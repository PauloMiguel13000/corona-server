package br.servidor.loja;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loja do Corona Server. O site roda dentro do servidor (porta local, publicada pelo tunel do playit).
 * Pix automatico pelo Mercado Pago (token no config) ou manual (/loja aprovar). A entrega roda os comandos do produto
 * no console quando o pagamento e confirmado e o jogador esta online (se estiver offline, entrega quando ele entrar).
 */
public final class CoronaLoja extends JavaPlugin implements Listener {

    static final String PREFIXO = "§6§lLOJA §8» §r";
    private Pedidos pedidos;
    private MercadoPago mp;
    private Web web;
    private Https https;
    private CompraNoJogo jogo;
    private Map<String, Loja.Produto> produtos = new LinkedHashMap<>();
    private volatile int online, max;
    private File logArquivo;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        logArquivo = new File(getDataFolder(), "loja.log");
        pedidos = new Pedidos(new File(getDataFolder(), "pedidos.yml"));
        carregarConfig();
        getServer().getPluginManager().registerEvents(this, this);
        String host = getConfig().getString("host", "127.0.0.1");
        int porta = getConfig().getInt("porta", 8085);
        web = new Web(this);
        try {
            web.iniciar(host, porta);
            getLogger().info("Site da loja no ar em http://" + host + ":" + porta + " (" + (mp.ativo() ? "Pix automatico" : "Pix manual") + ")");
            log("site iniciado em " + host + ":" + porta + " - " + (mp.ativo() ? "Pix automatico (Mercado Pago)" : "Pix manual"));
        } catch (IOException e) {
            getLogger().severe("Nao consegui abrir o site na porta " + porta + ": " + e);
        }
        // compra dentro do jogo (totens de VIP do spawn): /loja comprar <produto>
        jogo = new CompraNoJogo(this);
        // https com certificado gratis do Let's Encrypt (https.yml): pede/renova sozinho, confere a cada 6 horas
        https = new Https(this, web, host);
        https.iniciar();
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> https.verificar(false), 20L * 30, 20L * 60 * 60 * 6);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> https.testarPublico(), 20L * 60 * 30, 20L * 60 * 30);
        // contagem de jogadores para o site (lida no servidor principal)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            online = Bukkit.getOnlinePlayers().size();
            max = Bukkit.getMaxPlayers();
        }, 20L, 100L);
        // confere os Pix no Mercado Pago e expira os antigos
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::conferirPagamentos, 200L, 200L);
        // entrega o que foi pago para quem esta online
        Bukkit.getScheduler().runTaskTimer(this, this::entregar, 100L, 100L);
    }

    @Override
    public void onDisable() {
        if (https != null) https.parar();
        if (web != null) web.parar();
        if (pedidos != null) pedidos.salvar();
    }

    private void carregarConfig() {
        reloadConfig();
        mp = new MercadoPago(getConfig().getString("mercadopago.access-token", ""));
        Map<String, Loja.Produto> m = new LinkedHashMap<>();
        lerProdutos(getConfig().getConfigurationSection("produtos"), m, false);
        // produtos a mais num arquivo separado (assim da para mudar sem mexer na config.yml, que tem o token)
        java.io.File extra = new java.io.File(getDataFolder(), "produtos-extra.yml");
        if (!extra.exists() && getResource("produtos-extra.yml") != null) saveResource("produtos-extra.yml", false);
        if (extra.exists()) {
            org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(extra);
            lerProdutos(y.getConfigurationSection("produtos"), m, true);
        }
        produtos = m;
    }

    /** extra = true: nao troca um produto que ja veio da config.yml (a config manda). */
    private static void lerProdutos(ConfigurationSection s, Map<String, Loja.Produto> m, boolean extra) {
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null || c.getDouble("preco") <= 0 || !c.getBoolean("ativo", true)) continue;
            if (extra && m.containsKey(id)) continue;
            m.put(id, new Loja.Produto(id, c.getString("nome", id), c.getString("categoria", "outros"), c.getDouble("preco"),
                    c.getString("cor", "#ffaa00"), c.getString("destaque", ""), c.getString("descricao", ""),
                    c.getStringList("beneficios"), c.getStringList("comandos")));
        }
    }

    // ------------------------------------------------------------------ acesso pelo site

    String cfg(String k, String padrao) {
        return getConfig().getString(k, padrao);
    }

    int expiraMinutos() {
        return Math.max(10, getConfig().getInt("expira-minutos", 30));
    }

    int expiraManualHoras() {
        return Math.max(1, getConfig().getInt("expira-manual-horas", 48));
    }

    /** Da para comprar: tem token do Mercado Pago ou chave Pix manual configurada. */
    boolean aberta() {
        String chave = getConfig().getString("pix-manual.chave", "");
        return mp.ativo() || (chave != null && !chave.isBlank());
    }

    MercadoPago mp() {
        return mp;
    }

    Https https() {
        return https;
    }

    /** Endereco do site para mostrar no jogo (passa a https quando o https estiver funcionando). */
    String enderecoPublico() {
        String url = getConfig().getString("endereco-publico", "");
        String dom = https == null ? "" : https.dominio();
        if ((url == null || url.isBlank()) && !dom.isEmpty()) url = "http://" + dom;
        if (url != null && https != null && https.funcionando() && url.toLowerCase(Locale.ROOT).startsWith("http://" + dom)) url = "https://" + url.substring(7);
        return url == null ? "" : url;
    }

    Pedidos pedidos() {
        return pedidos;
    }

    Map<String, Loja.Produto> produtos() {
        return produtos;
    }

    int online() {
        return online;
    }

    int maxJogadores() {
        return max;
    }

    synchronized void log(String msg) {
        try {
            String linha = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT).format(new Date()) + " " + msg + System.lineSeparator();
            Files.writeString(logArquivo.toPath(), linha, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ pagamentos

    private void conferirPagamentos() {
        long agora = System.currentTimeMillis();
        MercadoPago m = mp;
        for (Pedidos.Pedido p : pedidos.comStatus(Pedidos.AGUARDANDO)) {
            if (p.mpId != null && m.ativo()) {
                try {
                    MercadoPago.Consulta c = m.consultar(p.mpId);
                    String st = c.status() == null ? "" : c.status();
                    if (st.equals("approved")) {
                        if (c.valor() + 0.009 < p.valor || (c.referencia() != null && !c.referencia().equals(p.codigo))) {
                            log("pedido " + p.codigo + ": pagamento aprovado mas nao confere (valor " + c.valor() + ", ref " + c.referencia() + ") - NAO entregue");
                            pedidos.trocar(p, Pedidos.AGUARDANDO, Pedidos.CANCELADO);
                        } else if (pedidos.trocar(p, Pedidos.AGUARDANDO, Pedidos.PAGO)) {
                            log("pedido " + p.codigo + ": PAGO (R$ " + Loja.reais(c.valor()) + ")");
                        }
                        continue;
                    }
                    if (st.equals("cancelled") || st.equals("rejected") || st.equals("refunded") || st.equals("charged_back")) {
                        if (pedidos.trocar(p, Pedidos.AGUARDANDO, Pedidos.CANCELADO)) log("pedido " + p.codigo + ": cancelado no Mercado Pago (" + st + ")");
                        continue;
                    }
                } catch (Exception e) {
                    log("pedido " + p.codigo + ": erro ao consultar o Mercado Pago: " + e.getMessage());
                }
                if (agora - p.criado > (expiraMinutos() + 5) * 60_000L && pedidos.trocar(p, Pedidos.AGUARDANDO, Pedidos.EXPIRADO)) {
                    log("pedido " + p.codigo + ": expirou sem pagamento");
                }
            } else if (p.mpId == null) {
                // manual: espera o dono aprovar; sem token ativo um pedido "automatico" antigo tambem cai aqui
                if (agora - p.criado > expiraManualHoras() * 3_600_000L && pedidos.trocar(p, Pedidos.AGUARDANDO, Pedidos.EXPIRADO)) {
                    log("pedido " + p.codigo + ": expirou sem aprovacao");
                }
            }
        }
    }

    private Player encontrar(String nick) {
        String n = Loja.normalizar(nick);
        for (Player p : Bukkit.getOnlinePlayers()) if (Loja.normalizar(p.getName()).equals(n)) return p;
        return null;
    }

    private void entregar() {
        for (Pedidos.Pedido p : pedidos.comStatus(Pedidos.PAGO)) {
            Player j = encontrar(p.jogador);
            if (j == null) continue;
            if (!pedidos.trocar(p, Pedidos.PAGO, Pedidos.ENTREGUE)) continue;
            List<String> feitos = new ArrayList<>();
            for (String cmd : p.comandos) {
                String c = cmd.replace("{jogador}", j.getName()).replace("{uuid}", j.getUniqueId().toString());
                if (c.startsWith("/")) c = c.substring(1);
                try {
                    boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                    feitos.add(c + (ok ? "" : " (comando nao reconhecido)"));
                } catch (Exception e) {
                    feitos.add(c + " (ERRO: " + e + ")");
                }
            }
            log("pedido " + p.codigo + ": ENTREGUE para " + j.getName() + " -> " + String.join(" | ", feitos));
            j.sendMessage(txt(PREFIXO + "§aSua compra chegou: §f" + p.nomeProduto + "§a. Obrigado por apoiar o servidor! §8(" + p.codigo + ")"));
            j.showTitle(Title.title(txt("§6§lOBRIGADO!"), txt("§f" + p.nomeProduto + " §aentregue")));
            j.playSound(j.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        // avisa quem tem compra paga esperando (a entrega roda sozinha em ate 5 s)
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            for (Pedidos.Pedido x : pedidos.comStatus(Pedidos.PAGO)) {
                if (Loja.normalizar(x.jogador).equals(Loja.normalizar(p.getName()))) {
                    p.sendMessage(txt(PREFIXO + "§eSua compra §f" + x.nomeProduto + " §eestá sendo entregue..."));
                    return;
                }
            }
        }, 60L);
    }

    // ------------------------------------------------------------------ /loja

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        boolean admin = s.hasPermission("loja.admin");
        if (admin && (sub.equals("aprovar") || sub.equals("cancelar")) && a.length >= 2) {
            Pedidos.Pedido p = pedidos.pegar(a[1]);
            if (p == null) {
                s.sendMessage(txt(PREFIXO + "§cPedido não encontrado: " + a[1]));
                return true;
            }
            if (sub.equals("aprovar")) {
                boolean ok = pedidos.trocar(p, Pedidos.AGUARDANDO, Pedidos.PAGO) || pedidos.trocar(p, Pedidos.EXPIRADO, Pedidos.PAGO)
                        || pedidos.trocar(p, Pedidos.CANCELADO, Pedidos.PAGO);
                if (ok) log("pedido " + p.codigo + ": aprovado manualmente por " + s.getName());
                s.sendMessage(txt(PREFIXO + (ok ? "§aPedido §f" + p.codigo + " §aaprovado: §f" + p.nomeProduto + " §apara §f" + p.jogador
                        + "§a. Entrega quando ele estiver online." : "§ePedido " + p.codigo + " já está " + p.status + ".")));
            } else {
                boolean ok = pedidos.trocar(p, Pedidos.AGUARDANDO, Pedidos.CANCELADO) || pedidos.trocar(p, Pedidos.PAGO, Pedidos.CANCELADO);
                if (ok) log("pedido " + p.codigo + ": cancelado por " + s.getName());
                s.sendMessage(txt(PREFIXO + (ok ? "§ePedido " + p.codigo + " cancelado." : "§ePedido " + p.codigo + " está " + p.status + " e não foi mudado.")));
            }
            return true;
        }
        if (admin && sub.equals("pedidos")) {
            s.sendMessage(txt(PREFIXO + "§fÚltimos pedidos §7(recebido no total: §aR$ " + Loja.reais(pedidos.totalRecebido()) + "§7)"));
            for (Pedidos.Pedido p : pedidos.ultimos(12)) {
                String cor = switch (p.status) {
                    case Pedidos.ENTREGUE -> "§a";
                    case Pedidos.PAGO -> "§e";
                    case Pedidos.AGUARDANDO -> "§7";
                    default -> "§8";
                };
                s.sendMessage(txt("§8- " + cor + p.codigo + " §f" + p.jogador + " §7" + p.nomeProduto + " §fR$ " + Loja.reais(p.valor) + " " + cor + p.status));
            }
            return true;
        }
        if (admin && sub.equals("https")) {
            if (a.length >= 2 && a[1].equalsIgnoreCase("renovar")) {
                s.sendMessage(txt(PREFIXO + "§ePedindo o certificado ao Let's Encrypt... veja o resultado com §f/loja https§e daqui a 1 minuto."));
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    https.carregar();
                    https.verificar(true);
                });
            } else {
                s.sendMessage(txt(PREFIXO + "§fHTTPS (" + https.dominio() + "): §7" + https.situacao()));
            }
            return true;
        }
        if (admin && sub.equals("recarregar")) {
            carregarConfig();
            s.sendMessage(txt(PREFIXO + "§aConfig recarregada. " + produtos.size() + " produtos. Pagamento: " + (mp.ativo() ? "Pix automático (Mercado Pago)" : "Pix manual")
                    + ". §7(porta/host só mudam reiniciando)"));
            log("config recarregada por " + s.getName() + " - " + (mp.ativo() ? "Pix automatico" : "Pix manual"));
            return true;
        }
        if (sub.equals("comprar") && a.length >= 2 && s instanceof Player pj) {
            jogo.comprar(pj, a[1].toLowerCase(Locale.ROOT));
            return true;
        }
        String url = enderecoPublico();
        if (url.isBlank()) {
            s.sendMessage(txt(PREFIXO + "§eA loja está quase pronta. Volte em breve!"));
        } else {
            s.sendMessage(txt(PREFIXO + "§fVIPs e coronas no site: ").append(txt("§b§n" + url).clickEvent(ClickEvent.openUrl(url))));
            s.sendMessage(txt("§7A compra cai sozinha no seu personagem assim que o Pix for pago."));
        }
        if (admin) s.sendMessage(txt("§8Admin: /loja pedidos | aprovar <código> | cancelar <código> | recarregar | https [renovar]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1 && s.hasPermission("loja.admin")) {
            List<String> l = new ArrayList<>();
            for (String x : List.of("pedidos", "aprovar", "cancelar", "recarregar", "https")) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) l.add(x);
            return l;
        }
        return List.of();
    }
}
