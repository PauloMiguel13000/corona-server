package br.servidor.poderes;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Le o nivel de poder do AuraSkills pelo PlaceholderAPI. -1 = nao deu para ler. */
final class Papi {
    private Papi() {}

    /**
     * 20/11 (Paulo: "manter 60... deixar o limite maximo de 60, para nao farmarem mais que esse lvl no /skill"): o nivel
     * de poder que vale no jogo para no 60 (torres, runas, forca das habilidades); o /skill tambem para de dar XP no 60.
     */
    static final int MAXIMO = 60;

    static int poder(Player p) {
        int n = poderReal(p);
        return n < 0 ? n : Math.min(MAXIMO, n);
    }

    static int poderReal(Player p) {
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
