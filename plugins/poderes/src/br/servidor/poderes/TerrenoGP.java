package br.servidor.poderes;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;

/** Consulta ao GriefPrevention: o jogador pode voar onde esta? (so carregada se o GP estiver ligado) */
final class TerrenoGP {
    private TerrenoGP() {}

    static boolean podeVoarAqui(Player p) {
        GriefPrevention gp = GriefPrevention.instance;
        if (gp == null || gp.dataStore == null) return false;
        Claim c = gp.dataStore.getClaimAt(p.getLocation(), true, null);
        if (c == null) return false;
        if (c.isAdminClaim()) return p.hasPermission("poderes.admin");
        if (p.getUniqueId().equals(c.getOwnerID())) return true;
        return c.checkPermission(p, ClaimPermission.Build, null) == null;
    }
}
