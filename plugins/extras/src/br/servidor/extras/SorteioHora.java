package br.servidor.extras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 14/11 (Paulo: "melhore muito"): SORTEIO DA HORA — de hora em hora (com 2+ jogadores) um jogador que nao esta
 * AFK ganha 100 coronas. Quem ficou parado os ultimos 5 minutos nao entra.
 */
final class SorteioHora {
    private final Extras pl;
    private final Map<UUID, Location> pos = new HashMap<>();
    private final Map<UUID, Long> mexeu = new HashMap<>();

    SorteioHora(Extras pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::vigiar, 20L * 30, 20L * 30);
        Bukkit.getScheduler().runTaskTimer(pl, this::sortear, 20L * 60 * 60, 20L * 60 * 60);
    }

    private void vigiar() {
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation(), a = pos.put(p.getUniqueId(), l);
            if (a == null || a.getWorld() != l.getWorld() || a.distanceSquared(l) > 1 || Math.abs(a.getYaw() - l.getYaw()) > 2) mexeu.put(p.getUniqueId(), agora);
        }
    }

    private void sortear() {
        if (!pl.getConfig().getBoolean("sorteio-hora.ativo", true) || Bukkit.getOnlinePlayers().size() < 2) return;
        long agora = System.currentTimeMillis();
        List<Player> ok = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (agora - mexeu.getOrDefault(p.getUniqueId(), 0L) < 5 * 60_000L) ok.add(p);
        if (ok.isEmpty()) return;
        Player g = ok.get(ThreadLocalRandom.current().nextInt(ok.size()));
        int v = pl.getConfig().getInt("sorteio-hora.coronas", 100);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + g.getName() + " " + v);
        Bukkit.broadcast(Extras.txt("§e§l🎟 SORTEIO DA HORA: §f" + g.getName() + " §eganhou §6" + v + " coronas§e! §7(" + ok.size() + " concorrendo — fique ativo para participar)"));
        g.playSound(g.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }
}
