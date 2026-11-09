package br.servidor.extras;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Medidor de rede dos jogadores (principalmente do Bedrock/celular): quantos pacotes e quantos KB o servidor manda para cada jogador e, no Bedrock, o ping de verdade
 *   do NetherNet (RTT do SCTP), reenvios por perda e fila. Escreve no log a cada N segundos e um resumo ao sair.
 */
final class RedeBedrock implements Listener {
    private static final String H_PACOTES = "corona_medidor_pacotes";
    private static final String H_BYTES = "corona_medidor_bytes";

    private final Extras pl;
    private final Map<UUID, Medida> medidas = new ConcurrentHashMap<>();
    private long ultimoLog;

    RedeBedrock(Extras pl) {
        this.pl = pl;
        for (Player p : Bukkit.getOnlinePlayers()) entrar(p);
        Bukkit.getScheduler().runTaskTimer(pl, this::amostrar, 100L, 100L);   // a cada 5 s
        Bukkit.getScheduler().runTaskTimer(pl, this::conferirMtu, 40L, 20L * 30);
    }

    // ------------------------------------------------------------------ SCTP do NetherNet (perda de pacote)

    private String sctpAplicado = "";

    /**
     * O NetherNet usa SCTP (dentro do WebRTC). Quando um pacote some no caminho (celular, tunel), o SCTP espera o
     * "tempo de reenvio" (RTO) e dobra a espera a cada perda seguida: o Geyser deixa o maximo em 10 s, entao uma
     * sequencia de perdas trava o jogo por varios segundos (mundo nao carrega, lag). Aqui o maximo fica em 1 s,
     * o minimo em 200 ms e o SCTP tenta mais vezes antes de desistir. Vale para as conexoes novas.
     */
    private void sctp() {
        if (!pl.getConfig().getBoolean("bedrock.sctp.ligado", true)) return;
        int min = pl.getConfig().getInt("bedrock.sctp.rto-min-ms", 200);
        int max = pl.getConfig().getInt("bedrock.sctp.rto-max-ms", 1000);
        int ini = pl.getConfig().getInt("bedrock.sctp.rto-inicial-ms", 500);
        int tent = pl.getConfig().getInt("bedrock.sctp.tentativas", 15);
        int sack = pl.getConfig().getInt("bedrock.sctp.sack-ms", 20);
        int cc = pl.getConfig().getInt("bedrock.sctp.controle", 0);
        int cwnd = pl.getConfig().getInt("bedrock.sctp.janela-inicial", 0);
        String chave = min + "/" + max + "/" + ini + "/" + tent + "/" + sack + "/" + cc + "/" + cwnd;
        if (chave.equals(sctpAplicado)) return;
        try {
            Object g = Class.forName("org.geysermc.geyser.GeyserImpl").getMethod("getInstance").invoke(null);
            if (g == null) return;
            ClassLoader cl = g.getClass().getClassLoader();
            Class<?> lib = Class.forName("tel.schich.libdatachannel.LibDataChannel", true, cl);
            Class<?> cfg = Class.forName("tel.schich.libdatachannel.SctpSettings", true, cl);
            Object b = cfg.getMethod("builder").invoke(null);
            Class<?> bc = b.getClass();
            bc.getMethod("minRetransmitTimeoutMs", int.class).invoke(b, min);
            bc.getMethod("maxRetransmitTimeoutMs", int.class).invoke(b, max);
            bc.getMethod("initialRetransmitTimeoutMs", int.class).invoke(b, ini);
            bc.getMethod("maxRetransmitAttempts", int.class).invoke(b, tent);
            bc.getMethod("delayedSackTimeMs", int.class).invoke(b, sack);
            bc.getMethod("congestionControlModule", int.class).invoke(b, cc);
            if (cwnd > 0) bc.getMethod("initialCongestionWindow", int.class).invoke(b, cwnd);
            Object s = bc.getMethod("build").invoke(b);
            lib.getMethod("setSctpSettings", cfg).invoke(null, s);
            sctpAplicado = chave;
            pl.getLogger().info("SCTP do NetherNet: reenvio entre " + min + " e " + max + " ms (inicial " + ini + "), " + tent
                    + " tentativas, SACK " + sack + " ms, controle " + cc + (cwnd > 0 ? ", janela inicial " + cwnd : "") + ".");
        } catch (Throwable t) {
            String m = "erro " + t;
            if (!m.equals(sctpAplicado)) pl.getLogger().warning("SCTP do NetherNet: " + t);
            sctpAplicado = m;
        }
    }

    // ------------------------------------------------------------------ MTU do NetherNet

    private String mtuAplicado = "";

    /**
     * Pacote do NetherNet (WebRTC) de tamanho seguro. Sem MTU fixo, o SCTP manda pacotes grandes que, somados ao
     * cabecalho do tunel do playit, passam do limite do caminho e somem sem aviso: o celular recebe o login e os
     * pacotes pequenos, mas os chunks (pacotes grandes) ficam sendo reenviados (espera de ate 10 s) -> mundo nao
     * carrega, cai no vazio, lag. Com MTU 1200 (o valor seguro da RFC 8261) tudo cabe. Vale para as conexoes novas;
     * confere a cada 30 s porque um "geyser reload" recria os canais.
     */
    private void conferirMtu() {
        sctp();
        int alvo = Math.max(0, pl.getConfig().getInt("bedrock.nethernet-mtu", 0));   // 0 = automatico (o normal do Geyser)
        StringBuilder sb = new StringBuilder();
        try {
            Object g = Class.forName("org.geysermc.geyser.GeyserImpl").getMethod("getInstance").invoke(null);
            if (g == null) return;
            Object nns = g.getClass().getMethod("getNetherNetServer").invoke(g);
            if (nns == null) return;
            @SuppressWarnings("rawtypes")
            io.netty.channel.ChannelOption opt = (io.netty.channel.ChannelOption) Class
                    .forName("org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption", true, nns.getClass().getClassLoader())
                    .getField("NETHER_PEER_CONNECTION_CONFIG").get(null);
            for (String nome : new String[] {"netherNetChannel", "inbuiltChannel"}) {
                Object o;
                try {
                    o = Medida.campo(nns, nome);
                } catch (NoSuchFieldException e) {
                    continue;
                }
                if (!(o instanceof Channel ch) || !ch.isOpen()) continue;
                @SuppressWarnings("unchecked")
                Object cfg = ch.config().getOption(opt);
                if (cfg == null) continue;
                int atual = ((Number) cfg.getClass().getMethod("mtu").invoke(cfg)).intValue();
                if (atual != alvo) {
                    Object novo = cfg.getClass().getMethod("withMtu", int.class).invoke(cfg, alvo);
                    @SuppressWarnings("unchecked")
                    boolean ok = ch.config().setOption(opt, novo);
                    sb.append(sb.length() > 0 ? ", " : "").append(nome).append(' ').append(atual == 0 ? "auto" : atual).append(" -> ").append(alvo).append(ok ? "" : " (recusado)");
                }
            }
            // O caminho do Warden (prettyshore, celulares pelo playit) NAO usa a opcao acima: o canal de admissao do
            // NetherNet monta a configuracao de cada conexao a partir do padrao (MTU 1280). Aqui o "porteiro" do UDP mux
            // e embrulhado para trocar o MTU de cada conexao nova.
            Object prov;
            try {
                prov = Medida.campo(nns, "netherNetChannel");
            } catch (NoSuchFieldException e) {
                prov = null;
            }
            if (prov != null) {
                String r = MuxMtu.aplicar(prov, alvo);
                if (r != null) sb.append(sb.length() > 0 ? ", " : "").append(r);
            }
        } catch (Throwable t) {
            String m = "erro " + t;
            if (!m.equals(mtuAplicado)) pl.getLogger().warning("MTU do NetherNet: " + t);
            mtuAplicado = m;
            return;
        }
        if (sb.length() > 0) {
            pl.getLogger().info("MTU do NetherNet (pacote seguro pelo tunel): " + sb);
            mtuAplicado = sb.toString();
        }
    }

    static boolean bedrock(Player p) {
        return Extras.ehBedrock(p);
    }

    private int intervaloLog() {
        return Math.max(10, pl.getConfig().getInt("bedrock.medidor-segundos", 30));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entrou(PlayerJoinEvent e) {
        entrar(e.getPlayer());
    }

    private boolean ligado() {
        return pl.getConfig().getBoolean("bedrock.medidor", true);
    }

    private void entrar(Player p) {
        if (!ligado()) return;
        try {
            Medida m = new Medida(p.getName(), bedrock(p));
            Medida velha = medidas.put(p.getUniqueId(), m);
            if (velha != null) velha.desligar();
            m.ligar(canal(p));
        } catch (Throwable t) {
            pl.getLogger().warning("Medidor de rede (" + p.getName() + "): " + t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void saiu(PlayerQuitEvent e) {
        Medida m = medidas.remove(e.getPlayer().getUniqueId());
        if (m == null) return;
        m.amostrar(e.getPlayer());
        m.desligar();
        if (m.bedrock || pl.getConfig().getBoolean("bedrock.medidor-java", false)) pl.getLogger().info(m.resumo());
    }

    void parar() {
        for (Medida m : medidas.values()) m.desligar();
        medidas.clear();
    }

    private static Channel canal(Player p) {
        return ((CraftPlayer) p).getHandle().connection.connection.channel;
    }

    // ------------------------------------------------------------------ amostras e log

    private void amostrar() {
        long agora = System.currentTimeMillis();
        boolean logar = agora - ultimoLog >= intervaloLog() * 1000L;
        if (logar) ultimoLog = agora;
        if (!ligado()) return;
        boolean java = pl.getConfig().getBoolean("bedrock.medidor-java", false);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Medida m = medidas.get(p.getUniqueId());
            if (m == null) continue;
            try {
                m.amostrar(p);
                if (logar && (m.bedrock || java)) pl.getLogger().info(m.linha(p));
            } catch (Throwable t) {
                if (logar) pl.getLogger().warning("Medidor de rede (" + p.getName() + "): " + t);
            }
        }
    }

    void comando(CommandSender s) {
        if (medidas.isEmpty()) {
            s.sendMessage("§7Ninguém online para medir.");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Medida m = medidas.get(p.getUniqueId());
            if (m == null) continue;
            m.amostrar(p);
            s.sendMessage("§6" + m.linha(p));
        }
    }

    // ------------------------------------------------------------------ medida de um jogador

    private static final class Contador extends ChannelOutboundHandlerAdapter {
        final LongAdder pacotes = new LongAdder();
        final LongAdder bytes = new LongAdder();
        final Map<String, LongAdder> tipos = new ConcurrentHashMap<>();
        final boolean contaBytes;

        Contador(boolean contaBytes) {
            this.contaBytes = contaBytes;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            try {
                if (contaBytes) {
                    if (msg instanceof ByteBuf b) bytes.add(b.readableBytes());
                } else {
                    contar(msg);
                }
            } catch (Throwable ignored) {
            }
            super.write(ctx, msg, promise);
        }

        private void contar(Object msg) {
            if (msg instanceof net.minecraft.network.protocol.BundlePacket<?> b) {
                for (Object sub : b.subPackets()) contar(sub);
                return;
            }
            if (!(msg instanceof net.minecraft.network.protocol.Packet<?>)) return;
            pacotes.increment();
            tipos.computeIfAbsent(msg.getClass().getSimpleName(), k -> new LongAdder()).increment();
        }
    }

    private static final class Medida {
        final String nome;
        final boolean bedrock;
        final long inicio = System.currentTimeMillis();
        Contador pac, byt;
        Channel canal;
        // amostra anterior
        long tAnt = System.currentTimeMillis(), pacAnt, bytAnt, perdasAnt = -1;
        // valores da ultima janela
        double pacSeg, kbSeg;
        long ping = -1, rtt = -1, rto = -1, fila = -1, janela = -1, rwnd = -1, pend = -1, unack = -1, perdasJanela;
        String caminho = "";
        // resumo da sessao
        long somaPing, nPing, maxPing, perdasTotal;
        final Map<String, Long> tiposAnt = new ConcurrentHashMap<>();
        String topo = "";

        Medida(String nome, boolean bedrock) {
            this.nome = nome;
            this.bedrock = bedrock;
        }

        void ligar(Channel c) {
            if (c == null) return;
            canal = c;
            pac = new Contador(false);
            byt = new Contador(true);
            c.eventLoop().execute(() -> {
                try {
                    ChannelPipeline pp = c.pipeline();
                    if (pp.get(H_PACOTES) != null) pp.remove(H_PACOTES);
                    if (pp.get(H_BYTES) != null) pp.remove(H_BYTES);
                    if (pp.get("packet_handler") != null) pp.addBefore("packet_handler", H_PACOTES, pac);
                    else pp.addFirst(H_PACOTES, pac);
                    if (pp.get("encoder") != null) pp.addFirst(H_BYTES, byt);
                } catch (Throwable t) {
                    Bukkit.getLogger().warning("[Extras] Medidor de rede: nao entrou no canal de " + nome + ": " + t);
                }
            });
        }

        void desligar() {
            Channel c = canal;
            if (c == null) return;
            canal = null;
            try {
                c.eventLoop().execute(() -> {
                    try {
                        ChannelPipeline pp = c.pipeline();
                        if (pp.get(H_PACOTES) != null) pp.remove(H_PACOTES);
                        if (pp.get(H_BYTES) != null) pp.remove(H_BYTES);
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable ignored) {
            }
        }

        void amostrar(Player p) {
            long agora = System.currentTimeMillis();
            double seg = Math.max(0.5, (agora - tAnt) / 1000.0);
            long pc = pac == null ? 0 : pac.pacotes.sum();
            long bt = byt == null ? 0 : byt.bytes.sum();
            pacSeg = (pc - pacAnt) / seg;
            kbSeg = (bt - bytAnt) / 1024.0 / seg;
            pacAnt = pc;
            bytAnt = bt;
            tAnt = agora;
            // tipos mais enviados nesta janela
            if (pac != null) {
                List<Map.Entry<String, Long>> l = new ArrayList<>();
                long tot = 0;
                for (Map.Entry<String, LongAdder> e : pac.tipos.entrySet()) {
                    long v = e.getValue().sum();
                    long d = v - tiposAnt.getOrDefault(e.getKey(), 0L);
                    tiposAnt.put(e.getKey(), v);
                    if (d > 0) {
                        l.add(Map.entry(e.getKey(), d));
                        tot += d;
                    }
                }
                l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(4, l.size()); i++) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(curto(l.get(i).getKey())).append(' ').append(Math.round(100.0 * l.get(i).getValue() / Math.max(1, tot))).append('%');
                }
                if (sb.length() > 0) topo = sb.toString();
            }
            if (bedrock) netherNet(p);
            else ping = p.getPing();
            if (ping > 0) {
                somaPing += ping;
                nPing++;
                maxPing = Math.max(maxPing, ping);
            }
        }

        /** Ping, SCTP e caminho do NetherNet pelo Geyser (reflexao: nao prende o Extras a versao do Geyser). */
        private void netherNet(Player p) {
            try {
                Object api = Class.forName("org.geysermc.geyser.api.GeyserApi").getMethod("api").invoke(null);
                Object sessao = api.getClass().getMethod("connectionByUuid", UUID.class).invoke(api, p.getUniqueId());
                if (sessao == null) return;
                Object up = sessao.getClass().getMethod("getUpstream").invoke(sessao);
                Object bs = campo(up, "session");
                Object peer = bs.getClass().getMethod("getPeer").invoke(bs);
                Object ch = peer.getClass().getMethod("getChannel").invoke(peer);
                Object nn = ch;
                for (int i = 0; i < 3 && nn != null && !nn.getClass().getName().contains("NetherNet"); i++) nn = ((Channel) nn).parent();
                if (nn == null || !nn.getClass().getName().contains("NetherNet")) {
                    caminho = "RakNet";
                    ping = ((Number) sessao.getClass().getMethod("ping").invoke(sessao)).longValue();
                    return;
                }
                ping = ((Number) metodo(nn, "getPing").invoke(nn)).longValue();
                Object st = metodo(nn, "sctpStats").invoke(nn);
                if (st != null) {
                    rtt = ms(st, "rtt");
                    rto = ms(st, "rto");
                    pend = ((Number) metodo(st, "pendingChunks").invoke(st)).longValue();
                    unack = ((Number) metodo(st, "unackedChunks").invoke(st)).longValue();
                    fila = pend + unack;
                    janela = ((Number) metodo(st, "congestionWindow").invoke(st)).longValue();
                    rwnd = ((Number) metodo(st, "peerReceiveWindow").invoke(st)).longValue();
                    long perdas = ((Number) metodo(st, "dataTimeouts").invoke(st)).longValue();
                    perdasJanela = perdasAnt < 0 ? 0 : Math.max(0, perdas - perdasAnt);
                    perdasTotal += perdasJanela;
                    perdasAnt = perdas;
                }
                Object path = metodo(nn, "selectedPath").invoke(nn);
                if (path != null) {
                    Object r = metodo(path, "remote").invoke(path);
                    Object t = metodo(path, "remoteType").invoke(path);
                    caminho = "NetherNet " + t + " " + r;
                }
            } catch (Throwable t) {
                caminho = "erro " + t.getClass().getSimpleName();
            }
        }

        private static long ms(Object st, String nome) throws Exception {
            Object d = metodo(st, nome).invoke(st);
            return d instanceof java.time.Duration du ? du.toMillis() : -1;
        }

        private static Method metodo(Object o, String nome) throws NoSuchMethodException {
            Method m = o.getClass().getMethod(nome);
            m.setAccessible(true);
            return m;
        }

        private static Object campo(Object o, String nome) throws Exception {
            for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(nome);
                    f.setAccessible(true);
                    return f.get(o);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException(nome);
        }

        private static String curto(String n) {
            return n.replace("Clientbound", "").replace("Packet", "").replace("$", ".");
        }

        String linha(Player p) {
            StringBuilder sb = new StringBuilder("Rede ").append(nome).append(bedrock ? " (Bedrock): " : " (Java): ");
            sb.append("ping ").append(ping < 0 ? "?" : ping + " ms");
            if (bedrock && rtt >= 0) sb.append(", rto ").append(rto).append(" ms, perdas +").append(perdasJanela).append(", fila ").append(pend).append('+').append(unack)
                    .append(", cwnd ").append(janela).append(", rwnd ").append(rwnd);
            sb.append(String.format(Locale.ROOT, ", %.0f pac/s, %.1f KB/s", pacSeg, kbSeg));
            if (bedrock) sb.append(", visao ").append(p.getSendViewDistance());
            if (!topo.isEmpty()) sb.append(" [").append(topo).append(']');
            if (bedrock && !caminho.isEmpty()) sb.append(" {").append(caminho).append('}');
            return sb.toString();
        }

        String resumo() {
            long min = Math.max(1, (System.currentTimeMillis() - inicio) / 60000);
            double seg = Math.max(1, (System.currentTimeMillis() - inicio) / 1000.0);
            long pc = pac == null ? 0 : pac.pacotes.sum();
            long bt = byt == null ? 0 : byt.bytes.sum();
            return String.format(Locale.ROOT, "Rede resumo %s (%s): %d min, ping medio %s (max %d), perdas %d, media %.0f pac/s e %.1f KB/s",
                    nome, bedrock ? "Bedrock" : "Java", min, nPing == 0 ? "?" : (somaPing / nPing) + " ms", maxPing, perdasTotal, pc / seg, bt / 1024.0 / seg);
        }
    }
}
