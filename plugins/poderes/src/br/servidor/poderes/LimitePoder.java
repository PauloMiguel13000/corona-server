package br.servidor.poderes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 20/11 (Paulo: "deixar o limite maximo de 60, para nao farmarem mais que esse lvl no /skill"): quem chegou no nivel de
 * poder 60 (soma das skills do AuraSkills) para de ganhar XP de skill. O que ja passou fica, mas no jogo vale 60 (Papi).
 */
final class LimitePoder implements Listener {

    private final Map<UUID, Long> avisado = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void xp(dev.aurelium.auraskills.api.event.skill.XpGainEvent e) {
        Player p = e.getPlayer();
        if (p == null || Papi.poderReal(p) < Papi.MAXIMO) return;
        e.setCancelled(true);
        long agora = System.currentTimeMillis();
        Long u = avisado.get(p.getUniqueId());
        if (u != null && agora - u < 600_000L) return;
        avisado.put(p.getUniqueId(), agora);
        p.sendMessage(Poderes.txt("§6§lNÍVEL DE PODER MÁXIMO §8» §7Você chegou no §f" + Papi.MAXIMO + "§7 (o máximo): as skills param de ganhar XP. §dSuba as Runas da classe: §f/runa"));
    }
}
