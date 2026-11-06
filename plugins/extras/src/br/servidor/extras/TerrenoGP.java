package br.servidor.extras;

import org.bukkit.Location;

/** Separado para so carregar as classes do GriefPrevention se ele estiver instalado. */
final class TerrenoGP {
    private TerrenoGP() {}

    static boolean temTerreno(Location l) {
        return me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(l, true, null) != null;
    }
}
