package br.servidor.extras;

import java.time.LocalDate;
import java.time.ZoneId;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/** 13/11 (Paulo: "melhore o spawn"): painel flutuante no centro do spawn com o que tem de especial HOJE no servidor. */
final class PainelDia {
    private final Extras pl;

    PainelDia(Extras pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::atualizar, 200L, 1200L);
    }

    /** Chama um metodo estatico de outro plugin (por reflexao) e le um campo do resultado. */
    private static String deOutro(String plugin, String classe, String metodo, String campo) {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
            if (p == null || !p.isEnabled()) return null;
            Class<?> c = Class.forName(classe, true, p.getClass().getClassLoader());
            var m = c.getDeclaredMethod(metodo);
            m.setAccessible(true);
            Object o = m.invoke(null);
            if (o == null) return null;
            if (campo == null) return o.toString();
            var f = o.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            return String.valueOf(f.get(o));
        } catch (Throwable t) {
            return null;
        }
    }

    String texto() {
        StringBuilder t = new StringBuilder("§6§l☀ HOJE NO CORONA ☀ §7" + LocalDate.now(ZoneId.of("America/Sao_Paulo")).getDayOfMonth() + "/"
                + LocalDate.now(ZoneId.of("America/Sao_Paulo")).getMonthValue());
        String mas = deOutro("Masmorras", "br.servidor.masmorras.Destaque", "hoje", "nome");
        if (mas != null) t.append("\n§c⚔ Masmorra em destaque: §f").append(mas).append(" §a(coronas e XP x2)");
        String des = deOutro("DefesaTorre", "br.servidor.defesa.DesafioDia", "hoje", "nome");
        if (des != null) t.append("\n§5🏰 Defesa da Torre: ").append(des);
        t.append("\n§6⛏ Prisão: §f3 missões novas §7(/minas → /missoes)");
        t.append("\n§e✦ Tesouro: ").append(pl.tesouro != null && pl.tesouro.ativo() ? "§a§lATIVO! §7use §e/tesouro" : "§7aparece de tempos em tempos no Survival");
        t.append("\n§b🎁 §f/kits §7no Survival e nas Masmorras");
        return t.toString();
    }

    void atualizar() {
        World w = Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world"));
        if (w == null) return;
        Location l = new Location(w, SpawnPrincipal.CX + 0.5, SpawnPrincipal.PISO + 9.5, SpawnPrincipal.CZ + 0.5);
        if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return;
        TextDisplay achado = null;
        for (Entity e : w.getNearbyEntities(l, 2, 3, 2)) {
            if (!(e instanceof TextDisplay td) || !e.getScoreboardTags().contains("painel_dia")) continue;
            if (achado == null) achado = td; else e.remove();
        }
        String t = texto();
        if (achado == null) {
            achado = w.spawn(l, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
                d.setPersistent(true);
                d.addScoreboardTag("painel_dia");
                d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(1.3f), new org.joml.AxisAngle4f()));
            });
        }
        achado.text(Extras.txt(t));
    }
}
