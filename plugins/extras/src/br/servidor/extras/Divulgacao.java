package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Divulgacao feita pelos proprios jogadores (sem conta em site nenhum):
 *  - /ip mostra o endereco do Java e do Bedrock para mandar aos amigos;
 *  - de tempos em tempos, uma dica no chat (votar, chamar amigos, /ip);
 *  - indicacao: quem e novo usa /indicar <nick de quem chamou>; os dois ganham premio quando o novo
 *    completa X minutos jogando (evita conta fake so para o premio).
 */
final class Divulgacao implements Listener {

    private final Extras pl;
    private final File arquivo;
    private final YamlConfiguration dados;
    private int proxima;

    Divulgacao(Extras pl) {
        this.pl = pl;
        this.arquivo = new File(pl.getDataFolder(), "indicacoes.yml");
        this.dados = YamlConfiguration.loadConfiguration(arquivo);
    }

    private void salvar() {
        try {
            dados.save(arquivo);
        } catch (IOException e) {
            pl.getLogger().warning("Nao consegui salvar indicacoes.yml: " + e.getMessage());
        }
    }

    void iniciar() {
        long min = pl.getConfig().getLong("divulgacao.dica-a-cada-minutos", 20);
        if (pl.getConfig().getBoolean("divulgacao.ativo", true) && min > 0)
            Bukkit.getScheduler().runTaskTimer(pl, this::dica, 20L * 60 * min, 20L * 60 * min);
        // indicacoes: confere o tempo de jogo de quem foi indicado
        Bukkit.getScheduler().runTaskTimer(pl, this::conferirIndicados, 20L * 60, 20L * 60);
    }

    private void dica() {
        List<String> l = pl.getConfig().getStringList("divulgacao.dicas");
        if (l.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        String m = l.get(proxima++ % l.size());
        Bukkit.broadcast(Extras.txt(Placas.cor(m)));
    }

    void ip(Player p) {
        p.sendMessage(Extras.txt("§6§l--- CHAME OS AMIGOS ---"));
        p.sendMessage(Extras.txt("§fJava: §e" + pl.getConfig().getString("divulgacao.ip-java", "")));
        p.sendMessage(Extras.txt("§fBedrock (celular, Xbox, Windows): §e" + pl.getConfig().getString("divulgacao.ip-bedrock", "")
                + " §fporta §e" + pl.getConfig().getString("divulgacao.porta-bedrock", "")));
        p.sendMessage(Extras.txt("§7Quando o amigo entrar, ele digita §e/indicar " + p.getName() + " §7e vocês dois ganham prêmio"
                + " §7(quando ele jogar " + pl.getConfig().getInt("divulgacao.indicacao.minutos-jogando", 30) + " min)."));
    }

    static int minutosJogados(Player p) {
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60);
    }

    void indicar(Player p, String nome) {
        if (!pl.getConfig().getBoolean("divulgacao.indicacao.ativo", true)) {
            p.sendMessage(Extras.txt("§cIndicação desligada."));
            return;
        }
        String eu = p.getUniqueId().toString();
        if (dados.contains("indicados." + eu)) {
            p.sendMessage(Extras.txt("§eVocê já disse quem te chamou (" + dados.getString("indicados." + eu + ".nome") + ")."));
            return;
        }
        int limite = pl.getConfig().getInt("divulgacao.indicacao.so-ate-minutos-jogados", 180);
        if (minutosJogados(p) > limite) {
            p.sendMessage(Extras.txt("§cA indicação é só para quem chegou agora (até " + limite / 60 + " horas de jogo)."));
            return;
        }
        Player online = Bukkit.getPlayerExact(nome);
        @SuppressWarnings("deprecation") OfflinePlayer quem = online != null ? online : Bukkit.getOfflinePlayerIfCached(nome);
        if (quem == null || (!quem.hasPlayedBefore() && !quem.isOnline())) {
            p.sendMessage(Extras.txt("§cNão achei o jogador " + nome + " (ele precisa já ter jogado aqui)."));
            return;
        }
        if (quem.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Extras.txt("§cVocê não pode indicar você mesmo."));
            return;
        }
        String q = quem.getUniqueId().toString();
        // limite por dia para quem indica
        long hoje = System.currentTimeMillis() / 86_400_000L;
        int hojeQtd = dados.getLong("quem-chamou." + q + ".dia", -1) == hoje ? dados.getInt("quem-chamou." + q + ".qtd", 0) : 0;
        int max = pl.getConfig().getInt("divulgacao.indicacao.maximo-por-dia", 5);
        if (hojeQtd >= max) {
            p.sendMessage(Extras.txt("§c" + quem.getName() + " já indicou " + max + " pessoas hoje. Tente amanhã."));
            return;
        }
        dados.set("quem-chamou." + q + ".dia", hoje);
        dados.set("quem-chamou." + q + ".qtd", hojeQtd + 1);
        dados.set("indicados." + eu + ".nome", quem.getName());
        dados.set("indicados." + eu + ".uuid", q);
        dados.set("indicados." + eu + ".pago", false);
        salvar();
        int min = pl.getConfig().getInt("divulgacao.indicacao.minutos-jogando", 30);
        p.sendMessage(Extras.txt("§aAnotado: §f" + quem.getName() + " §ate chamou! Jogue §f" + min + " min §ae vocês dois ganham prêmio."));
        if (online != null) online.sendMessage(Extras.txt("§a§l" + p.getName() + " §adisse que você chamou ele! Quando ele jogar " + min + " min, vocês ganham prêmio."));
    }

    private void conferirIndicados() {
        var sec = dados.getConfigurationSection("indicados");
        if (sec == null) return;
        int min = pl.getConfig().getInt("divulgacao.indicacao.minutos-jogando", 30);
        boolean mudou = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String eu = p.getUniqueId().toString();
            if (!sec.contains(eu) || sec.getBoolean(eu + ".pago")) continue;
            if (minutosJogados(p) < min) continue;
            sec.set(eu + ".pago", true);
            mudou = true;
            darPremio(p, "§aVocê completou " + min + " min! Prêmio de indicação entregue.");
            UUID quem = UUID.fromString(sec.getString(eu + ".uuid"));
            Player q = Bukkit.getPlayer(quem);
            if (q != null) darPremio(q, "§a" + p.getName() + " (que você chamou) jogou " + min + " min! Prêmio de indicação entregue.");
            else dados.set("pendentes." + quem, dados.getInt("pendentes." + quem, 0) + 1);
            Bukkit.broadcast(Extras.txt("§6§l>> §e" + sec.getString(eu + ".nome") + " §7trouxe §e" + p.getName() + " §7para o servidor! Chame os seus amigos também: §e/ip"));
        }
        if (mudou) salvar();
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        int n = dados.getInt("pendentes." + p.getUniqueId(), 0);
        if (n <= 0) return;
        dados.set("pendentes." + p.getUniqueId(), null);
        salvar();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (int i = 0; i < n; i++) darPremio(p, "§aPrêmio de indicação: alguém que você chamou já jogou bastante!");
        }, 100L);
    }

    private void darPremio(Player p, String msg) {
        for (String linha : pl.getConfig().getStringList("divulgacao.indicacao.premio")) {
            String[] a = linha.trim().split("\\s+");
            Material m = Material.matchMaterial(a[0]);
            if (m == null || !m.isItem()) continue;
            int q = a.length > 1 ? Integer.parseInt(a[1]) : 1;
            for (ItemStack sobra : p.getInventory().addItem(new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), q)))).values())
                p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        }
        p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.8f, 1.2f);
        p.sendMessage(Extras.txt(msg));
    }
}
