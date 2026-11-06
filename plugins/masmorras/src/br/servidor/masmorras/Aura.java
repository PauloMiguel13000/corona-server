package br.servidor.masmorras;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Da XP de habilidade do AuraSkills (sobe o "nivel de poder", que libera as habilidades das classes do Poderes).
 * Usa reflexao: sem o AuraSkills instalado, nao faz nada.
 */
final class Aura {

    private Aura() {}

    private static boolean tentou;
    private static Object api;
    private static Method getUser, addXp, getPower;
    private static Class<?> skills;

    /** So para os testes: troca o jeito de dar XP. */
    interface Dar {
        boolean dar(Player p, String skill, double xp);
    }

    static Dar teste;

    private static synchronized boolean preparar() {
        if (api != null) return true;
        if (tentou) return false;
        tentou = true;
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("AuraSkills");
            if (pl == null || !pl.isEnabled()) return false;
            ClassLoader cl = pl.getClass().getClassLoader();
            Class<?> c = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi", true, cl);
            Object a = c.getMethod("get").invoke(null);
            Class<?> user = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser", true, cl);
            Class<?> skill = Class.forName("dev.aurelium.auraskills.api.skill.Skill", true, cl);
            skills = Class.forName("dev.aurelium.auraskills.api.skill.Skills", true, cl);
            getUser = c.getMethod("getUser", UUID.class);
            addXp = user.getMethod("addSkillXp", skill, double.class);
            getPower = user.getMethod("getPowerLevel");
            api = a;
            return true;
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Masmorras] Nao consegui ligar no AuraSkills: " + t);
            return false;
        }
    }

    /** Nivel de poder do AuraSkills (soma das skills), ou -1. */
    static int poder(Player p) {
        if (teste != null || !preparar()) return -1;
        try {
            Object u = getUser.invoke(api, p.getUniqueId());
            return u == null ? -1 : ((Number) getPower.invoke(u)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Ex.: dar(p, "FIGHTING", 500). Devolve false se nao deu. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean dar(Player p, String skill, double xp) {
        if (teste != null) return teste.dar(p, skill, xp);
        if (xp <= 0 || !preparar()) return false;
        try {
            Object u = getUser.invoke(api, p.getUniqueId());
            if (u == null) return false;
            Object s = Enum.valueOf((Class) skills, skill);
            addXp.invoke(u, s, xp);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
