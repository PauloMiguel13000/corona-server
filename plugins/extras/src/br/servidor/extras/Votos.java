package br.servidor.extras;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Votos nos sites de lista de servidores, com premio em itens. Sem Votifier (o playit gratis nao abre porta TCP):
 * o servidor pergunta ao site, pela API dele, se o jogador votou nas ultimas 24 h e "resgata" o voto (cada voto
 * so da premio uma vez). Precisa da chave da API de cada site (o dono do servidor ve no painel do site) no config.
 * Formatos: "numero" (Minecraft-MP e parecidos: 0 nao votou, 1 votou e nao pegou, 2 ja pegou) e "json"
 * (minecraftlist.org: {"voted":true,"claimed":false}).
 */
final class Votos implements Listener {

    record Site(String id, String nome, String link, String chave, String checar, String resgatar, String formato) {}

    private final Extras pl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    /** Para nao perguntar duas vezes ao mesmo tempo: "uuid site". */
    private final Set<String> perguntando = new HashSet<>();

    Votos(Extras pl) {
        this.pl = pl;
    }

    List<Site> sites() {
        List<Site> l = new ArrayList<>();
        ConfigurationSection s = pl.getConfig().getConfigurationSection("votos.sites");
        if (s == null) return l;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            l.add(new Site(id, c.getString("nome", id), c.getString("link", ""), c.getString("chave", ""), c.getString("checar", ""),
                    c.getString("resgatar", ""), c.getString("formato", "numero").toLowerCase(Locale.ROOT)));
        }
        return l;
    }

    boolean ativo() {
        return pl.getConfig().getBoolean("votos.ativo", true);
    }

    static String url(String modelo, String chave, String nick) {
        return modelo.replace("{chave}", URLEncoder.encode(chave, StandardCharsets.UTF_8)).replace("{nick}", URLEncoder.encode(nick, StandardCharsets.UTF_8));
    }

    /** Resposta da checagem: votou e ainda nao pegou o premio? */
    static boolean podePegar(String formato, String corpo) {
        if (corpo == null) return false;
        String c = corpo.trim();
        if (formato.equals("json")) {
            String s = c.replace(" ", "").toLowerCase(Locale.ROOT);
            return s.contains("\"voted\":true") && s.contains("\"claimed\":false");
        }
        return c.equals("1");
    }

    /** Resposta do resgate: deu certo? */
    static boolean resgatou(String formato, String corpo) {
        if (corpo == null) return false;
        String c = corpo.trim();
        if (formato.equals("json")) return c.replace(" ", "").toLowerCase(Locale.ROOT).contains("\"claimed\":true");
        return c.equals("1");
    }

    /** Os nomes para perguntar: no Bedrock o nome tem um "." na frente (o jogador pode ter votado sem ele). */
    static List<String> nomes(String nome) {
        List<String> l = new ArrayList<>();
        l.add(nome);
        if (nome.startsWith(".") && nome.length() > 1) l.add(nome.substring(1));
        return l;
    }

    private String get(String url) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", "CoronaServer-Votos/1.0").GET().build();
            HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Pergunta a todos os sites (fora da linha principal) e da o premio de cada voto novo. */
    void conferir(Player p, boolean avisarSeNada) {
        if (!ativo()) return;
        List<Site> comChave = new ArrayList<>();
        for (Site s : sites()) if (!s.chave().isBlank() && !s.checar().isBlank()) comChave.add(s);
        if (comChave.isEmpty()) {
            if (avisarSeNada) p.sendMessage(Extras.txt("§eOs prêmios por voto ainda não foram ligados pelo dono. Vote mesmo assim: ajuda o servidor a crescer!"));
            return;
        }
        UUID id = p.getUniqueId();
        String nome = p.getName();
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            int achou = 0;
            for (Site s : comChave) {
                String chaveTrava = id + " " + s.id();
                synchronized (perguntando) {
                    if (!perguntando.add(chaveTrava)) continue;
                }
                try {
                    for (String n : nomes(nome)) {
                        String r = get(url(s.checar(), s.chave(), n));
                        if (!podePegar(s.formato(), r)) continue;
                        String rr = s.resgatar().isBlank() ? "1" : get(url(s.resgatar(), s.chave(), n));
                        if (!resgatou(s.formato(), rr) && !s.resgatar().isBlank()) continue;
                        achou++;
                        Bukkit.getScheduler().runTask(pl, () -> premiar(id, s));
                        break;
                    }
                } finally {
                    synchronized (perguntando) {
                        perguntando.remove(chaveTrava);
                    }
                }
            }
            if (achou == 0 && avisarSeNada)
                Bukkit.getScheduler().runTask(pl, () -> {
                    Player q = Bukkit.getPlayer(id);
                    if (q != null) q.sendMessage(Extras.txt("§7Nenhum voto novo encontrado. Votou agora? O site pode levar uns minutos. §e/votar §7mostra os links."));
                });
        });
    }

    void premiar(UUID id, Site s) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        int dados = 0;
        for (String linha : pl.getConfig().getStringList("votos.premio.itens")) {
            String[] a = linha.trim().split("\\s+");
            Material m = Material.matchMaterial(a[0]);
            if (m == null || !m.isItem()) continue;
            int q = a.length > 1 ? Integer.parseInt(a[1]) : 1;
            for (ItemStack sobra : p.getInventory().addItem(new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), q)))).values())
                p.getWorld().dropItemNaturally(p.getLocation(), sobra);
            dados++;
        }
        int xp = pl.getConfig().getInt("votos.premio.xp", 0);
        if (xp > 0) p.giveExp(xp);
        p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.8f, 1.2f);
        p.sendMessage(Extras.txt("§a§lObrigado por votar no " + s.nome() + "! §7Você ganhou: §f" + pl.getConfig().getString("votos.premio.descricao", "itens")));
        if (pl.getConfig().getBoolean("votos.anunciar", true))
            Bukkit.broadcast(Extras.txt("§6§l>> §e" + p.getName() + " §7votou no servidor (" + s.nome() + ") e ganhou prêmios! §e/votar"));
        pl.getLogger().info("Voto de " + p.getName() + " no " + s.nome() + ": premio entregue (" + dados + " itens).");
    }

    void mostrar(Player p) {
        List<Site> l = sites();
        p.sendMessage(Extras.txt("§6§l--- VOTE NO SERVIDOR §7(1 vez por dia em cada site) §6§l---"));
        int n = 0;
        for (Site s : l) {
            if (s.link().isBlank()) continue;
            n++;
            p.sendMessage(Extras.txt("§e" + s.nome() + "§7: ").append(Extras.txt("§b§n" + s.link()).clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(s.link()))));
        }
        if (n == 0) p.sendMessage(Extras.txt("§7Os links de voto ainda vão ser colocados."));
        p.sendMessage(Extras.txt("§7Prêmio por voto: §f" + pl.getConfig().getString("votos.premio.descricao", "itens") + "§7. Depois de votar: §e/votar pegar"));
        p.sendMessage(Extras.txt("§8(No Bedrock, vote com o seu nome com ou sem o ponto.)"));
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && Extras.logado(p)) conferir(p, false);
        }, 20L * 40);
    }

    /** A cada X minutos, confere quem esta online (votou pelo celular sem digitar nada). */
    void iniciar() {
        long min = Math.max(2, pl.getConfig().getLong("votos.conferir-a-cada-minutos", 5));
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) if (Extras.logado(p)) conferir(p, false);
        }, 20L * 60 * min, 20L * 60 * min);
    }
}
