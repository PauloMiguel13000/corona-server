package br.servidor.coronapack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pacote de recursos do Corona Server (sons originais dos minigames).
 * Java: o proprio servidor entrega o pacote pela mesma porta do Minecraft (um pedido HTTP GET
 * e respondido com o arquivo; qualquer outra conexao segue normal), entao nao precisa de site.
 * Bedrock: o pacote .mcpack vai para a pasta de pacotes do Geyser, que manda sozinho para o jogador.
 */
public final class CoronaPack extends JavaPlugin implements Listener {

    /** Mesmo id sempre: o Minecraft reaproveita o download quando o arquivo nao muda. */
    public static final UUID ID = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(StandardCharsets.UTF_8));
    private static final Key CHAVE = Key.key("coronapack", "http");

    private byte[] zip;
    private byte[] sha1;
    private String sha1Hex;
    private LinkPublico publico;
    private volatile boolean avisouSemHttp;
    private volatile long ultimo404;

    @Override
    public void onLoad() {
        // Bedrock, antes do Geyser ligar (ele le a pasta de pacotes e a config quando liga).
        // Com link (bedrock-por-link): o celular baixa os pacotes direto de links https, sem passar pelo tunel do jogo
        // (pelos dados moveis o download pelo tunel travava em 0 MB). O Bedrock nao mistura: ou TODOS os pacotes vao por
        // link, ou todos pela conexao. Entao: corona + os outros pacotes da pasta do Geyser vao por link, e o pacote
        // integrado do Geyser fica desligado (ele nao tem como ir por link). Faltando algum link: tudo pela pasta, como antes.
        try (InputStream in = getResource("pack/corona.mcpack")) {
            if (in == null) return;
            mcpack = in.readAllBytes();
            File pasta = new File(getDataFolder().getParentFile(), "Geyser-Spigot/packs");
            if (!pasta.isDirectory()) return;
            List<PacoteBedrock> ps = pacotesBedrock();
            List<String> links = new ArrayList<>();
            boolean todos = getConfig().getBoolean("bedrock-por-link", true);
            for (PacoteBedrock pb : ps) {
                if (!todos) break;
                String l = linkSalvo(pb);
                String site = baseSite();
                if (l != null && !site.isEmpty()) {
                    // pelo site da loja: so vale link do site e com o arquivo aqui (a loja ainda nao ligou para testar)
                    if (!l.startsWith(site) || !new File(pastaSite(), l.substring(site.length())).isFile()) l = null;
                    if (l != null) {
                        links.add(l);
                        continue;
                    }
                }
                if (l == null || !linkNoAr(l)) {
                    if (l != null) getLogger().warning("Link do pacote " + pb.nome() + " nao respondeu agora (" + l + ").");
                    todos = false;
                    break;
                }
                links.add(l);
            }
            if (todos) modoLink(ps, links);
            else modoPasta(ps);
        } catch (Exception e) {
            getLogger().warning("Nao consegui preparar o pacote Bedrock: " + e);
        }
    }

    // ------------------------------------------------------------------ pacotes do Bedrock por link

    private record PacoteBedrock(String nome, byte[] dados, byte[] sha, File arquivo) {}

    private byte[] mcpack;
    private final List<LinkPublico> linksBedrock = new ArrayList<>();
    private volatile boolean emModoLink;

    private File pastaPacotes() {
        return new File(getDataFolder().getParentFile(), "Geyser-Spigot/packs");
    }

    private File pastaFora() {
        return new File(getDataFolder(), "pacotes-fora");
    }

    /** corona (do jar) + os outros pacotes da pasta do Geyser (ou ja tirados para pacotes-fora). */
    private List<PacoteBedrock> pacotesBedrock() throws Exception {
        List<PacoteBedrock> out = new ArrayList<>();
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        out.add(new PacoteBedrock("corona", mcpack, md.digest(mcpack), null));
        java.util.Map<String, File> extras = new java.util.TreeMap<>();
        for (File dir : new File[] {pastaFora(), pastaPacotes()}) {
            File[] fs = dir.listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (!f.isFile() || n.equals("corona.mcpack") || !(n.endsWith(".mcpack") || n.endsWith(".zip"))) continue;
                extras.put(f.getName(), f);   // o da pasta do Geyser ganha (vem depois)
            }
        }
        for (File f : extras.values()) {
            byte[] d = Files.readAllBytes(f.toPath());
            String nome = f.getName().replaceAll("\\.(mcpack|zip)$", "").replaceAll("[^A-Za-z0-9_-]", "_");
            out.add(new PacoteBedrock(nome, d, md.digest(d), f));
        }
        return out;
    }

    /** Base do link pelo site da loja (bedrock-link-site), terminando com "/"; vazio = servicos gratis (catbox...). */
    private String baseSite() {
        String b = getConfig().getString("bedrock-link-site");   // sem padrao aqui: assim vale o padrao do config.yml do jar
        if (b == null || b.isBlank()) return "";
        b = b.trim();
        return b.endsWith("/") ? b : b + "/";
    }

    private File pastaSite() {
        return new File(getDataFolder(), "pacotes-site");
    }

    /** Pelo site da loja: grava o pacote em pacotes-site/<nome>-<sha>.zip e confere pelo endereco publico (https). */
    private String linkPeloSite(PacoteBedrock pb) {
        try {
            String arq = pb.nome() + "-" + HexFormat.of().formatHex(pb.sha()).substring(0, 10) + ".zip";
            File f = new File(pastaSite(), arq);
            pastaSite().mkdirs();
            if (!f.isFile() || f.length() != pb.dados().length) Files.write(f.toPath(), pb.dados());
            String link = baseSite() + arq;
            if (!linkNoAr(link)) {
                getLogger().warning("Pacote Bedrock " + pb.nome() + ": o site nao respondeu em " + link + " (https e application/zip).");
                return null;
            }
            Files.writeString(new File(getDataFolder(), arquivoLink(pb)).toPath(), link + " " + HexFormat.of().formatHex(pb.sha()) + " site\n", StandardCharsets.UTF_8);
            return link;
        } catch (Exception e) {
            getLogger().warning("Pacote Bedrock " + pb.nome() + " pelo site: " + e);
            return null;
        }
    }

    private String arquivoLink(PacoteBedrock pb) {
        return pb.nome().equals("corona") ? "link-bedrock.txt" : "link-bedrock-" + pb.nome() + ".txt";
    }

    /** Link salvo que seja deste mesmo arquivo (a 2a coluna e o sha1). */
    private String linkSalvo(PacoteBedrock pb) {
        try {
            File f = new File(getDataFolder(), arquivoLink(pb));
            if (!f.isFile()) return null;
            String[] l = Files.readString(f.toPath(), StandardCharsets.UTF_8).trim().split("\\s+");
            if (l.length >= 2 && l[1].equalsIgnoreCase(HexFormat.of().formatHex(pb.sha())) && l[0].startsWith("https://")) return l[0];
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Pergunta rapida (HEAD) ao link: responde 200 com application/zip? */
    private static boolean linkNoAr(String u) {
        try {
            java.net.http.HttpClient c = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            java.net.http.HttpResponse<Void> r = c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(u))
                    .timeout(java.time.Duration.ofSeconds(40)).header("User-Agent", "CoronaServer/1.0")
                    .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.discarding());
            return r.statusCode() == 200 && r.headers().firstValue("content-type").orElse("").toLowerCase(java.util.Locale.ROOT).startsWith("application/zip");
        } catch (Exception e) {
            return false;
        }
    }

    private File arquivoAplicado() {
        return new File(getDataFolder(), "link-bedrock-aplicado.txt");
    }

    private File marcaIntegrado() {
        return new File(getDataFolder(), "integrado-desligado.txt");
    }

    /** Troca a lista "resource-pack-urls" da config do Geyser: tira os nossos links antigos, poe os novos, deixa os outros. */
    private void linksNaConfig(List<String> nossos, Boolean integrado) throws IOException {
        File cfg = new File(getDataFolder().getParentFile(), "Geyser-Spigot/config.yml");
        if (!cfg.isFile()) throw new IOException("config do Geyser nao existe");
        java.util.Set<String> antigos = new java.util.HashSet<>();
        if (arquivoAplicado().isFile()) for (String l : Files.readAllLines(arquivoAplicado().toPath(), StandardCharsets.UTF_8)) if (!l.isBlank()) antigos.add(l.trim());
        String txt = Files.readString(cfg.toPath(), StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^(\\s*)resource-pack-urls:[ \\t]*\\[(.*)\\][ \\t]*$").matcher(txt);
        if (!m.find()) throw new IOException("nao achei 'resource-pack-urls: [...]' na config do Geyser");
        List<String> itens = new ArrayList<>();
        for (String it : m.group(2).split(",")) {
            String v = it.trim().replaceAll("^[\"']|[\"']$", "");
            if (v.isEmpty() || antigos.contains(v) || nossos.contains(v)) continue;
            itens.add(v);
        }
        itens.addAll(nossos);
        StringBuilder sb = new StringBuilder();
        for (String v : itens) sb.append(sb.length() == 0 ? "" : ", ").append('"').append(v).append('"');
        String novo = txt.substring(0, m.start()) + m.group(1) + "resource-pack-urls: [" + sb + "]" + txt.substring(m.end());
        if (integrado != null) novo = novo.replaceAll("(?m)^(\\s*)enable-integrated-pack:[ \\t]*(true|false)[ \\t]*$", "$1enable-integrated-pack: " + integrado);
        if (!novo.equals(txt)) Files.writeString(cfg.toPath(), novo, StandardCharsets.UTF_8);
    }

    /** Todos os pacotes por link: config do Geyser com os links e sem o pacote integrado; pasta de pacotes vazia. */
    private void modoLink(List<PacoteBedrock> ps, List<String> links) throws IOException {
        boolean desligou = !marcaIntegrado().isFile();
        linksNaConfig(links, Boolean.FALSE);
        if (desligou) Files.writeString(marcaIntegrado().toPath(), "a config do Geyser estava com enable-integrated-pack: true\n", StandardCharsets.UTF_8);
        File corona = new File(pastaPacotes(), "corona.mcpack");
        if (corona.exists() && !corona.delete()) getLogger().warning("Nao consegui tirar o corona.mcpack da pasta do Geyser.");
        pastaFora().mkdirs();
        for (PacoteBedrock pb : ps) {
            if (pb.arquivo() == null || !pb.arquivo().getParentFile().equals(pastaPacotes())) continue;
            File destino = new File(pastaFora(), pb.arquivo().getName());
            Files.move(pb.arquivo().toPath(), destino.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(arquivoAplicado().toPath(), links, StandardCharsets.UTF_8);
        emModoLink = true;
        getLogger().info("Pacotes do Bedrock por link (" + links.size() + "): o celular baixa direto, sem o tunel. " + links);
    }

    /** Volta tudo para a pasta do Geyser (como antes do link). */
    private void modoPasta(List<PacoteBedrock> ps) throws IOException {
        try {
            linksNaConfig(List.of(), marcaIntegrado().isFile() ? Boolean.TRUE : null);
        } catch (IOException e) {
            getLogger().warning("Config do Geyser: " + e.getMessage());
        }
        marcaIntegrado().delete();
        arquivoAplicado().delete();
        for (PacoteBedrock pb : ps) {
            if (pb.arquivo() == null || !pb.arquivo().getParentFile().equals(pastaFora())) continue;
            File destino = new File(pastaPacotes(), pb.arquivo().getName());
            if (!destino.exists()) Files.move(pb.arquivo().toPath(), destino.toPath());
        }
        File f = new File(pastaPacotes(), "corona.mcpack");
        if (!f.exists() || !Arrays.equals(Files.readAllBytes(f.toPath()), mcpack)) {
            Files.write(f.toPath(), mcpack);
            getLogger().info("Pacote Bedrock copiado para o Geyser (vale quando o Geyser ligar).");
        }
        emModoLink = false;
    }

    /** Sobe os pacotes que ainda nao tem link; com todos prontos e ninguem do Bedrock online, passa para o modo link ja. */
    private void garantirLinksBedrock() {
        try {
            List<PacoteBedrock> ps = pacotesBedrock();
            List<String> links = new ArrayList<>();
            boolean site = !baseSite().isEmpty();
            for (PacoteBedrock pb : ps) {
                if (site) {
                    String l = linkPeloSite(pb);
                    if (l == null) return;
                    links.add(l);
                    continue;
                }
                LinkPublico lp = new LinkPublico(pb.dados(), pb.sha(), getDataFolder(), getLogger(), arquivoLink(pb), pb.nome() + ".zip", "Bedrock " + pb.nome());
                lp.exigirZip = true;
                lp.garantir();
                if (lp.link == null) return;
                links.add(lp.link);
            }
            List<String> aplicados = arquivoAplicado().isFile() ? Files.readAllLines(arquivoAplicado().toPath(), StandardCharsets.UTF_8) : List.of();
            if (emModoLink && aplicados.equals(links)) return;
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    int bedrock = 0;
                    for (Player p : Bukkit.getOnlinePlayers()) if (bedrock(p)) bedrock++;
                    if (bedrock > 0) {
                        getLogger().info("Links dos pacotes Bedrock prontos; valem no proximo reinicio (" + bedrock + " jogador(es) do Bedrock online agora).");
                        return;
                    }
                    modoLink(pacotesBedrock(), links);
                    if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
                        getLogger().info("Recarregando o Geyser para usar os links (ninguem do Bedrock online).");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "geyser reload");
                    }
                } catch (Exception e) {
                    getLogger().warning("Nao consegui passar os pacotes Bedrock para link: " + e);
                }
            });
        } catch (Exception e) {
            getLogger().warning("Links dos pacotes Bedrock: " + e);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            aplicar(lerPacote());
        } catch (Exception e) {
            getLogger().severe("Sem pacote de recursos: " + e);
            return;
        }
        try {
            ChannelInitializeListenerHolder.addListener(CHAVE, ch -> ch.pipeline().addFirst("corona_http", new Http()));
        } catch (Throwable t) {
            getLogger().severe("Nao consegui servir o pacote pela porta do servidor: " + t);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Pacote pronto: " + zip.length / 1024 + " KB, sha1 " + sha1Hex);
        if (mcpack != null && getConfig().getBoolean("bedrock-por-link", true)) {
            // confere/sobe e depois a cada 6 horas; se algum link mudar, aplica (recarrega o Geyser so sem ninguem do Bedrock)
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::garantirLinksBedrock, 140L, 20L * 60 * 60 * 6);
        }
        if (getConfig().getBoolean("link-automatico", true)) {
            publico = new LinkPublico(zip, sha1, getDataFolder(), getLogger());
            // confere/sobe agora e depois a cada 6 horas (o link pode expirar)
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                LinkPublico lp = publico;
                if (lp != null) lp.garantir();
            }, 100L, 20L * 60 * 60 * 6);
        }
    }

    /** Pacote em uso: plugins/CoronaPack/pacote-atual.zip quando for mais novo que o jar (veio por /coronapack atualizar); senao o do jar. */
    private byte[] lerPacote() throws IOException {
        File atual = new File(getDataFolder(), "pacote-atual.zip");
        if (atual.isFile()) {
            if (atual.lastModified() > getFile().lastModified()) {
                byte[] d = Files.readAllBytes(atual.toPath());
                if (zipValido(d)) {
                    getLogger().info("Usando o pacote de plugins/CoronaPack/pacote-atual.zip (mais novo que o jar).");
                    return d;
                }
            }
            atual.delete(); // o jar e mais novo (ou o arquivo esta estragado): volta ao pacote do jar
        }
        try (InputStream in = getResource("pack/corona-pack.zip")) {
            if (in == null) throw new IOException("pacote nao esta no jar");
            return in.readAllBytes();
        }
    }

    /** Zip de verdade e com pack.mcmeta na raiz. */
    private static boolean zipValido(byte[] d) {
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(d))) {
            java.util.zip.ZipEntry e;
            while ((e = z.getNextEntry()) != null) if (e.getName().equals("pack.mcmeta")) return true;
        } catch (IOException ignored) {
        }
        return false;
    }

    private void aplicar(byte[] dados) throws Exception {
        zip = dados;
        sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
        sha1Hex = HexFormat.of().formatHex(sha1);
    }

    /**
     * Troca o pacote do Java sem reiniciar: le plugins/CoronaPack/pacote-novo.zip, guarda como pacote-atual.zip,
     * sobe o link publico novo e manda o pacote de novo a todos do Java que estao online (o cliente baixa e aplica na hora).
     * O pacote do Bedrock (mcpack) so troca no reinicio (o Geyser le a pasta quando liga).
     */
    private void atualizar(CommandSender s) {
        File novo = new File(getDataFolder(), "pacote-novo.zip");
        if (!novo.isFile()) {
            s.sendMessage("§e[CoronaPack] §fPonha o pacote em §eplugins/CoronaPack/pacote-novo.zip§f e rode de novo.");
            return;
        }
        byte[] dados;
        try {
            dados = Files.readAllBytes(novo.toPath());
        } catch (IOException e) {
            s.sendMessage("§c[CoronaPack] Nao consegui ler pacote-novo.zip: " + e.getMessage());
            return;
        }
        if (!zipValido(dados)) {
            s.sendMessage("§c[CoronaPack] pacote-novo.zip nao e um pacote de recursos (sem pack.mcmeta).");
            return;
        }
        try {
            File atual = new File(getDataFolder(), "pacote-atual.zip");
            Files.move(novo.toPath(), atual.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            atual.setLastModified(System.currentTimeMillis());
            String antes = sha1Hex;
            aplicar(dados);
            if (sha1Hex.equals(antes)) {
                s.sendMessage("§e[CoronaPack] §fO pacote novo e igual ao que ja esta em uso.");
                return;
            }
        } catch (Exception e) {
            s.sendMessage("§c[CoronaPack] Falhou: " + e);
            return;
        }
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) if (!bedrock(p)) n++;
        s.sendMessage("§e[CoronaPack] §fPacote novo: " + zip.length / 1024 + " KB, sha1 " + sha1Hex + "§f. Subindo o link e reenviando para " + n + " do Java...");
        getLogger().info("Pacote trocado sem reiniciar: " + zip.length / 1024 + " KB, sha1 " + sha1Hex);
        final LinkPublico lp = getConfig().getBoolean("link-automatico", true) ? new LinkPublico(zip, sha1, getDataFolder(), getLogger()) : null;
        publico = lp;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (lp != null) lp.garantir();
            Bukkit.getScheduler().runTask(this, () -> {
                int c = 0;
                for (Player p : Bukkit.getOnlinePlayers()) if (oferecer(p)) c++;
                getLogger().info("Pacote novo reenviado para " + c + " jogador(es) do Java.");
                if (s instanceof Player sp && sp.isOnline()) sp.sendMessage("§e[CoronaPack] §fPacote novo enviado para " + c + " do Java.");
            });
        });
    }

    /** Manda o pacote para um jogador do Java (false = nao e do Java / sem link / desligado). */
    private boolean oferecer(Player p) {
        if (zip == null || !p.isOnline() || bedrock(p) || !getConfig().getBoolean("enviar-java", true)) return false;
        String u = url(p);
        if (u == null) return false;
        getLogger().info("Oferecendo o pacote para " + p.getName() + " (" + u + ")");
        Component msg = LegacyComponentSerializer.legacySection().deserialize(
                getConfig().getString("mensagem", "§e§lCorona Server§r\n§fSons dos minigames (Backrooms e outros). §aRecomendado!"));
        p.setResourcePack(ID, u, sha1, msg, getConfig().getBoolean("obrigatorio", false));
        return true;
    }

    @Override
    public void onDisable() {
        try {
            ChannelInitializeListenerHolder.removeListener(CHAVE);
        } catch (Throwable ignored) {
        }
    }

    /** Jogador do Bedrock (Floodgate): o Geyser cuida do pacote dele. */
    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0;
    }

    String url(Player p) {
        // link publico do pacote (ex.: catbox): o mesmo arquivo que esta no jar, entao vale para todo mundo
        String fixo = getConfig().getString("url-pacote", "");
        if (fixo != null && !fixo.isBlank()) return fixo.trim();
        if (publico != null && publico.link != null) return publico.link;
        String base = getConfig().getString("url-base", "");
        if (base == null || base.isBlank()) {
            InetSocketAddress v = p.getVirtualHost();
            if (v == null) return null;
            String host = v.getHostString();
            if (host == null || host.isBlank()) return null;
            // o Minecraft as vezes manda o nome com um ponto no fim (registro SRV)
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            // tunel "Minecraft Java" do playit: so deixa passar Minecraft, o download por ele sempre falha
            String h = host.toLowerCase(java.util.Locale.ROOT);
            for (String s : getConfig().getStringList("sem-http")) {
                if (s == null || s.isBlank()) continue;
                if (h.endsWith(s.toLowerCase(java.util.Locale.ROOT).trim())) {
                    if (!avisouSemHttp) {
                        avisouSemHttp = true;
                        getLogger().info("Nao ofereco o pacote a quem entra por " + host + " (tunel Minecraft Java do playit nao passa o download)."
                                + " Para liberar, preencha url-base com o endereco de um tunel TCP.");
                    }
                    return null;
                }
            }
            base = "http://" + host + ":" + v.getPort();
        }
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/corona-" + sha1Hex + ".zip";
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("atualizar")) {
            atualizar(s);
            return true;
        }
        if (a.length > 0 && (a[0].equalsIgnoreCase("recarregar") || a[0].equalsIgnoreCase("reload"))) {
            reloadConfig();
            avisouSemHttp = false;
        } else if (a.length == 0 || !a[0].equalsIgnoreCase("status")) {
            s.sendMessage("§e/coronapack status §7| §e/coronapack recarregar §7| §e/coronapack atualizar §7(troca o pacote do Java sem reiniciar: plugins/CoronaPack/pacote-novo.zip)");
            return true;
        }
        String base = getConfig().getString("url-base", "");
        String fixo = getConfig().getString("url-pacote", "");
        if (fixo != null && !fixo.isBlank()) base = fixo + " (url-pacote)";
        else if (publico != null && publico.link != null) base = publico.link + " (link automatico)";
        s.sendMessage("§e[CoronaPack] §fpacote " + (zip == null ? "§cnao carregado" : zip.length / 1024 + " KB, sha1 " + sha1Hex)
                + "§f | url-base: " + (base == null || base.isBlank() ? "§7(vazio: usa o endereco do jogador)" : "§a" + base)
                + "§f | Java: " + (getConfig().getBoolean("enviar-java", true) ? "§aligado" : "§cdesligado"));
        return true;
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (zip == null || bedrock(p) || !getConfig().getBoolean("enviar-java", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> oferecer(p), getConfig().getLong("atraso-ticks", 60));
    }

    @EventHandler
    public void status(PlayerResourcePackStatusEvent e) {
        if (!ID.equals(e.getID())) return;
        getLogger().info("Pacote de " + e.getPlayer().getName() + ": " + e.getStatus());
    }

    private static final byte[] GET = {'G', 'E', 'T', ' '};
    private static final int LIMITE_PEDIDO = 8192;

    /** O comeco do que ja chegou bate com "GET " (com menos de 4 bytes, confere so o que tem). */
    static boolean comecaComGet(ByteBuf b) {
        int n = Math.min(GET.length, b.readableBytes());
        for (int i = 0; i < n; i++) if (b.getByte(b.readerIndex() + i) != GET[i]) return false;
        return true;
    }

    /** O pedido HTTP terminou (linha em branco depois do cabecalho). */
    static boolean pedidoCompleto(ByteBuf b) {
        int ini = b.readerIndex(), fim = b.writerIndex();
        for (int i = ini; i < fim - 1; i++) {
            if (b.getByte(i) != '\n') continue;
            if (b.getByte(i + 1) == '\n') return true;
            if (i + 2 < fim && b.getByte(i + 1) == '\r' && b.getByte(i + 2) == '\n') return true;
        }
        return false;
    }

    /**
     * Responde "GET /corona-....zip" na porta do servidor. Qualquer outra coisa segue para o Minecraft.
     * O pedido pode chegar em pedacos (tunel, rede lenta): junta tudo ate a linha em branco antes de responder.
     */
    final class Http extends ChannelInboundHandlerAdapter {
        private ByteBuf junto;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf b)) {
                seguir(ctx, msg);
                return;
            }
            if (junto == null) {
                if (b.readableBytes() == 0 || !comecaComGet(b)) {
                    seguir(ctx, b);
                    return;
                }
                junto = ctx.alloc().buffer(Math.max(256, b.readableBytes()));
            }
            junto.writeBytes(b);
            b.release();
            if (!comecaComGet(junto)) { // nao era HTTP: devolve tudo ao Minecraft
                ByteBuf j = junto;
                junto = null;
                seguir(ctx, j);
                return;
            }
            if (junto.readableBytes() < GET.length) return;
            if (!pedidoCompleto(junto) && junto.readableBytes() < LIMITE_PEDIDO) return; // espera o resto
            String pedido = junto.toString(StandardCharsets.ISO_8859_1);
            junto.release();
            junto = null;
            responder(ctx, pedido);
        }

        private void seguir(ChannelHandlerContext ctx, Object msg) {
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(msg);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (junto != null) {
                junto.release();
                junto = null;
            }
        }

        private void responder(ChannelHandlerContext ctx, String pedido) {
            String linha = pedido.split("\r?\n", 2)[0];
            String[] partes = linha.split(" ");
            String caminho = partes.length > 1 ? partes[1] : "/";
            int q = caminho.indexOf('?');
            if (q >= 0) caminho = caminho.substring(0, q);
            boolean ok = zip != null && (caminho.equals("/corona-" + sha1Hex + ".zip") || caminho.equals("/corona.zip"));
            byte[] corpo = ok ? zip : "nada aqui".getBytes(StandardCharsets.UTF_8);
            String cab = (ok ? "HTTP/1.1 200 OK" : "HTTP/1.1 404 Not Found") + "\r\n"
                    + "Content-Type: " + (ok ? "application/zip" : "text/plain") + "\r\n"
                    + "Content-Length: " + corpo.length + "\r\n"
                    + "Cache-Control: max-age=86400\r\n"
                    + "Connection: close\r\n\r\n";
            ByteBuf r = Unpooled.wrappedBuffer(cab.getBytes(StandardCharsets.ISO_8859_1), corpo);
            ctx.writeAndFlush(r).addListener(ChannelFutureListener.CLOSE);
            if (ok) {
                getLogger().info("Pacote baixado por " + ctx.channel().remoteAddress());
            } else if (System.currentTimeMillis() - ultimo404 > 60_000) { // no maximo 1 aviso por minuto
                ultimo404 = System.currentTimeMillis();
                getLogger().info("Pedido HTTP sem pacote (" + caminho + ") de " + ctx.channel().remoteAddress());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.fireExceptionCaught(cause);
        }
    }
}
