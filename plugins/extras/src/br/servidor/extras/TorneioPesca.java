package br.servidor.extras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * 14/11 (Paulo: "melhore muito"): TORNEIO DE PESCA no Survival — 10 minutos, quem pescar mais ganha coronas
 * (500/300/150) e todo mundo que pescou ao menos 3 ganha 30. Acontece sozinho a cada ~3h com 2+ jogadores no
 * Survival. Admin: "extras pesca".
 */
final class TorneioPesca implements Listener {
    static final int[] PREMIO = {500, 300, 150};
    private final Extras pl;
    private final Map<UUID, Integer> pontos = new HashMap<>();
    private final Map<UUID, String> nomes = new HashMap<>();
    private long fim, proximo;
    private BossBar barra;

    TorneioPesca(Extras pl) {
        this.pl = pl;
        proximo = System.currentTimeMillis() + 90 * 60_000L;
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 20L * 20, 20L * 20);
    }

    boolean ativo() { return fim > System.currentTimeMillis(); }

    private static boolean survival(Player p) {
        String w = p.getWorld().getName();
        return w.equals("world") || w.equals("world_nether") || w.equals("world_the_end");
    }

    private void tique() {
        if (ativo()) {
            long falta = fim - System.currentTimeMillis();
            if (barra != null) {
                barra.setProgress(Math.max(0, Math.min(1, falta / 600_000.0)));
                barra.setTitle("§b🎣 TORNEIO DE PESCA §f— " + (falta / 60000 + 1) + " min §7| líder: " + lider());
                for (Player p : Bukkit.getOnlinePlayers()) { if (survival(p)) barra.addPlayer(p); else barra.removePlayer(p); }
            }
            return;
        }
        if (fim > 0) { encerrar(); return; }
        if (System.currentTimeMillis() < proximo) return;
        proximo = System.currentTimeMillis() + Math.max(60, pl.getConfig().getInt("torneio-pesca.minutos", 180)) * 60_000L;
        long noSurvival = Bukkit.getOnlinePlayers().stream().filter(TorneioPesca::survival).count();
        if (noSurvival >= 2 && pl.getConfig().getBoolean("torneio-pesca.ativo", true)) iniciar();
    }

    private String lider() {
        UUID best = null;
        for (var e : pontos.entrySet()) if (best == null || e.getValue() > pontos.get(best)) best = e.getKey();
        return best == null ? "ninguém" : nomes.get(best) + " (" + pontos.get(best) + ")";
    }

    void iniciar() {
        pontos.clear();
        nomes.clear();
        fim = System.currentTimeMillis() + 600_000L;
        if (barra != null) barra.removeAll();
        barra = Bukkit.createBossBar("§b🎣 TORNEIO DE PESCA", BarColor.BLUE, BarStyle.SEGMENTED_10);
        Bukkit.broadcast(Extras.txt("§b§l🎣 TORNEIO DE PESCA COMEÇOU! §7(10 min, no Survival) §fQuem pescar mais ganha §6500 coronas§f! Pegue a vara!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.8f, 1f);
    }

    private void encerrar() {
        fim = 0;
        if (barra != null) { barra.removeAll(); barra = null; }
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(pontos.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        if (l.isEmpty()) { Bukkit.broadcast(Extras.txt("§b🎣 O torneio de pesca acabou... ninguém pescou nada!")); return; }
        StringBuilder sb = new StringBuilder("§b§l🎣 FIM DO TORNEIO DE PESCA!");
        for (int i = 0; i < l.size(); i++) {
            var e = l.get(i);
            String n = nomes.get(e.getKey());
            int v = i < 3 ? PREMIO[i] : e.getValue() >= 3 ? 30 : 0;
            if (i < 3) sb.append("\n§e").append(i + 1).append("º §f").append(n).append(" §7(").append(e.getValue()).append(" peixes) §6+").append(v).append(" coronas");
            if (v > 0) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas adm dar " + n + " " + v);
        }
        if (l.size() > 3) sb.append("\n§7Quem pescou 3+ também ganhou §630 coronas§7.");
        Bukkit.broadcast(Extras.txt(sb.toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pescou(PlayerFishEvent e) {
        if (!ativo() || e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item)) return;
        Player p = e.getPlayer();
        if (!survival(p)) return;
        int n = pontos.merge(p.getUniqueId(), 1, Integer::sum);
        nomes.put(p.getUniqueId(), p.getName());
        p.sendActionBar(Extras.txt("§b🎣 Torneio: §f" + n + " peixe" + (n == 1 ? "" : "s") + " §7| líder: " + lider()));
    }

    void desligar() { if (barra != null) barra.removeAll(); }
}
