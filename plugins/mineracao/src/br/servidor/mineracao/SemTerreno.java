package br.servidor.mineracao;

import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Ninguem faz terreno (GriefPrevention) no mundo de mineracao: um terreno em cima de uma mina travaria a mina. */
final class SemTerreno implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void terreno(ClaimCreatedEvent e) {
        if (e.getClaim().getLesserBoundaryCorner().getWorld() != null
                && Mineracao.MUNDO.equals(e.getClaim().getLesserBoundaryCorner().getWorld().getName())) {
            e.setCancelled(true);
            if (e.getCreator() instanceof org.bukkit.entity.Player p) p.sendMessage(Mineracao.txt("§cNão dá para fazer terreno no mundo de mineração."));
        }
    }
}
