package br.servidor.poderes;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Pergunta aos outros plugins (sem depender deles): Clas (grupo/cla = aliados, tag do cla) e Masmorras (sem PvP
 * dentro das masmorras). Sem o plugin instalado, a resposta e "nao".
 */
final class Externo {

    private Externo() {}

    private static Method metodo(String plugin, String nome, Class<?>... tipos) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        if (p == null || !p.isEnabled()) return null;
        try {
            return p.getClass().getMethod(nome, tipos);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object chamar(String plugin, String nome, Class<?>[] tipos, Object... args) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        if (p == null || !p.isEnabled()) return null;
        try {
            Method m = metodo(plugin, nome, tipos);
            return m == null ? null : m.invoke(p, args);
        } catch (Exception e) {
            return null;
        }
    }

    /** Mesmo grupo ou cla (plugin Clas). */
    static boolean aliados(Player a, Player b) {
        return Boolean.TRUE.equals(chamar("Clas", "aliados", new Class<?>[] {Player.class, Player.class}, a, b));
    }

    /** Tag do cla (ex.: "ABC") ou null. */
    static String tag(Player p) {
        Object o = chamar("Clas", "tag", new Class<?>[] {Player.class}, p);
        return o instanceof String s ? s : null;
    }

    /** Lugar dentro de uma masmorra (la nao tem PvP). */
    static boolean semPvp(Location l) {
        return Boolean.TRUE.equals(chamar("Masmorras", "semPvp", new Class<?>[] {Location.class}, l));
    }
}
