package br.servidor.extras;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 13/11 (Paulo: "melhore os minigames"): Arena PvP paga coronas por abate e tem sequencia de abates
 * (3, 5, 10, 15... anunciada para a arena, com bonus). Matar o mesmo jogador de novo em menos de 2 minutos
 * nao paga (contra farm combinado).
 */
final class ArenaSequencia implements Listener {
    static final String MUNDO = "arena";
    private final Extras pl;
    private final Map<UUID, Integer> seq = new HashMap<>();
    private final Map<String, Long> ultimo = new HashMap<>();

    ArenaSequencia(Extras pl) { this.pl = pl; }

    private static boolean naArena(Player p) { return p.getWorld().getName().equalsIgnoreCase(MUNDO); }

    private void avisarArena(String msg) {
        var w = Bukkit.getWorld(MUNDO);
        if (w != null) for (Player p : w.getPlayers()) p.sendMessage(Extras.txt(msg));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morte(PlayerDeathEvent e) {
        Player morto = e.getPlayer();
        if (!naArena(morto)) return;
        int tinha = seq.getOrDefault(morto.getUniqueId(), 0);
        seq.remove(morto.getUniqueId());
        Player k = morto.getKiller();
        if (k == null || k.equals(morto) || !naArena(k)) {
            if (tinha >= 3) avisarArena("§c⚔ §7A sequência de §f" + morto.getName() + " §7(" + tinha + ") acabou.");
            return;
        }
        int n = seq.merge(k.getUniqueId(), 1, Integer::sum);
        if (tinha >= 3) {
            avisarArena("§c⚔ §e" + k.getName() + " §7acabou com a sequência de §f" + tinha + " §7de §c" + morto.getName() + "§7! §6+" + (tinha * 5) + " coronas");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + k.getName() + " " + (tinha * 5));
        }
        String par = k.getUniqueId() + ">" + morto.getUniqueId();
        long agora = System.currentTimeMillis();
        Long ant = ultimo.put(par, agora);
        if (ant != null && agora - ant < 120_000L) {
            k.sendActionBar(Extras.txt("§7Abate repetido no mesmo jogador: sem coronas por 2 min"));
            return;
        }
        int v = 10;
        if (n == 3 || n == 5 || n == 10 || (n > 10 && n % 5 == 0)) {
            int bonus = n * 10;
            v += bonus;
            String nome = n >= 10 ? "§4§lIMPARÁVEL" : n >= 5 ? "§6§lDOMINANDO" : "§e§lEM CHAMAS";
            avisarArena("§c⚔ " + nome + " §f" + k.getName() + " §7está com §c" + n + " abates §7seguidos! §6(+" + bonus + " coronas)");
            for (Player p : k.getWorld().getPlayers()) p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.6f);
            if (n >= 10) Bukkit.broadcast(Extras.txt("§c⚔ §f" + k.getName() + " §7está com §c" + n + " abates seguidos §7na §cArena PvP§7! Alguém para ele? §e/minigames"));
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + k.getName() + " " + v);
        k.sendActionBar(Extras.txt("§c⚔ Abate! §6+" + v + " coronas §8| §7sequência: §c" + n));
        k.playSound(k.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.7f);
        if (ultimo.size() > 2000) ultimo.entrySet().removeIf(x -> agora - x.getValue() > 120_000L);
    }

    @EventHandler
    public void saiu(PlayerChangedWorldEvent e) {
        if (e.getFrom().getName().equalsIgnoreCase(MUNDO)) seq.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { seq.remove(e.getPlayer().getUniqueId()); }
}
