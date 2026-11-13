package br.servidor.masmorras;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/** 13/11 (Paulo: "melhore muitas coisas na masmorra"): uma masmorra em destaque por dia, com coronas, XP e reliquias em dobro. */
final class Destaque implements Listener {
    private final Masmorras pl;

    Destaque(Masmorras pl) {
        this.pl = pl;
        // lembrete a cada 30 min para todo o servidor
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            Tipo t = hoje();
            if (t != null) Bukkit.broadcast(Masmorras.txt("§6⭐ Masmorra em destaque hoje: §e" + t.nome + " §7(nível " + t.nivel
                    + ") §8» §acoronas, XP e relíquias em DOBRO§7! §e/masmorras"));
        }, 20L * 60 * 5, 20L * 60 * 30);
    }

    static Tipo hoje() {
        List<Tipo> l = new ArrayList<>();
        for (Tipo t : Tipo.TODOS.values()) if (!t.secreta) l.add(t);
        if (l.isEmpty()) return null;
        l.sort(Comparator.comparing(t -> t.id));
        return l.get(LocalDate.now(ZoneId.of("America/Sao_Paulo")).getDayOfYear() % l.size());
    }

    static boolean eh(Tipo t) { return t != null && t == hoje(); }

    static int mult(Tipo t) { return eh(t) ? 2 : 1; }

    @EventHandler
    public void entrou(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!p.getWorld().getName().startsWith("masmorras")) return;
        Tipo t = hoje();
        if (t == null) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline()) p.sendMessage(Masmorras.txt("§6⭐ Destaque do dia: §e" + t.nome + " §7(nível " + t.nivel + ", chefe §c" + t.chefeNome
                    + "§7) §8» §acoronas, XP e relíquias em DOBRO§7!"));
        }, 40L);
    }
}
