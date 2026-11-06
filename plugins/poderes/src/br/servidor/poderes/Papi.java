package br.servidor.poderes;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Le o nivel de poder do AuraSkills pelo PlaceholderAPI. -1 = nao deu para ler. */
final class Papi {
    private Papi() {}

    static int poder(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return -1;
        try {
            String s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((OfflinePlayer) p, "%auraskills_power%");
            String digitos = s == null ? "" : s.replaceAll("[^0-9]", "");
            if (digitos.isEmpty()) return -1;
            return Integer.parseInt(digitos);
        } catch (Throwable t) {
            return -1;
        }
    }
}
