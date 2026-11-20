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
 * ganhar XP no nivel 15 (skills.limite-por-rebirth deixa subir com o REBIRTH, hoje 0). O que ja passou fica; no jogo o nivel de poder vale no maximo 60 (Papi).
 * Config: skills.limite-base (15), skills.limite-por-rebirth (0), skills.limite-maximo (60).
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
        int base = pl.getConfig().getInt("skills.limite-base", 15), por = pl.getConfig().getInt("skills.limite-por-rebirth", 0);
        return Math.min(pl.getConfig().getInt("skills.limite-maximo", 60), base + por * (rb == null ? 0 : Math.max(0, rb)));
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
                + "§7Para ficar mais forte: §d/runa §7(runas da classe)."));
    }
}
