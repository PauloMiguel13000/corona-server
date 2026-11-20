package br.servidor.poderes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

/**
 * 20/11 (Paulo: "cada skill no maximo 15... caso no futuro eu peca, aumentamos o limite"): cada skill do /skill para de
 * ganhar XP no nivel 15 e +5 por REBIRTH do jogador ("a cada rebirth do player e mais 5 levels"). O que ja passou fica; no jogo o nivel de poder vale no maximo 60 (Papi).
 * Config: skills.limite-base (15), skills.limite-por-rebirth (5), skills.limite-maximo (60).
 */
final class LimitePoder implements Listener {

    private static final NamespacedKey K_REBIRTH = new NamespacedKey("masmorras", "rebirth");
    private final Poderes pl;
    private final Map<String, Long> avisado = new HashMap<>();

    LimitePoder(Poderes pl) {
        this.pl = pl;
    }

    int limite(Player p) {
        Integer rb = p.getPersistentDataContainer().get(K_REBIRTH, PersistentDataType.INTEGER);
        int base = pl.getConfig().getInt("skills.limite-base", 15), por = pl.getConfig().getInt("skills.limite-por-rebirth", 5);
        return Math.min(pl.getConfig().getInt("skills.limite-maximo", 60), base + por * (rb == null ? 0 : Math.max(0, rb)));
    }

    /**
     * 20/11 (Paulo: "o meu nao ficou limitado lvl 15 que nem os outros"): quem ja tinha passado do limite volta para ele
     * (ao entrar e a cada 5 min). Config skills.cortar-acima (padrao true).
     */
    void cortar(Player p) {
        if (!pl.getConfig().getBoolean("skills.cortar-acima", true)) return;
        int lim = limite(p);
        try {
            var api = dev.aurelium.auraskills.api.AuraSkillsApi.get();
            var u = api.getUser(p.getUniqueId());
            if (u == null || !u.isLoaded()) return;
            StringBuilder cortadas = new StringBuilder();
            for (var sk : api.getGlobalRegistry().getSkills()) {
                if (!sk.isEnabled()) continue;
                int n = u.getSkillLevel(sk);
                if (n > lim) {
                    u.setSkillLevel(sk, lim);
                    cortadas.append(cortadas.length() == 0 ? "" : ", ").append(sk.getDisplayName(java.util.Locale.ROOT)).append(" ").append(n).append("→").append(lim);
                }
            }
            if (cortadas.length() > 0) {
                p.sendMessage(Poderes.txt("§6§lLIMITE DAS SKILLS §8» §7As skills agora vão até o §f" + lim + "§7 (+5 por REBIRTH): " + cortadas));
                pl.getLogger().info("Skills de " + p.getName() + " cortadas para " + lim + ": " + cortadas);
            }
        } catch (Throwable t) {
            pl.getLogger().warning("Cortar skills de " + p.getName() + ": " + t);
        }
    }

    void iniciar() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) cortar(p);
        }, 20L * 30, 20L * 300);
    }

    @EventHandler
    public void entrou(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) cortar(p); }, 20L * 8);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void xp(dev.aurelium.auraskills.api.event.skill.XpGainEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;
        int lim = limite(p);
        int nivel;
        try {
            nivel = dev.aurelium.auraskills.api.AuraSkillsApi.get().getUser(p.getUniqueId()).getSkillLevel(e.getSkill());
        } catch (Throwable t) {
            return;
        }
        if (nivel < lim) return;
        e.setCancelled(true);
        String k = p.getUniqueId() + ":" + e.getSkill().getId();
        long agora = System.currentTimeMillis();
        Long u = avisado.get(k);
        if (u != null && agora - u < 900_000L) return;
        avisado.put(k, agora);
        p.sendMessage(Poderes.txt("§6§lLIMITE DA SKILL §8» §7Essa skill chegou no nível §f" + lim + "§7 (o máximo agora). "
                + "§dCada REBIRTH sobe o limite em +5 §7(§f/rebirth§7). Para ficar mais forte agora: §d/runa§7."));
    }
}
