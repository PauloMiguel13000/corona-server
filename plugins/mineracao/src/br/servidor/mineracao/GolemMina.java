package br.servidor.mineracao;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** 13/11 (Paulo: "faca ficar fodao"): de tempos em tempos um Golem de Minerio aparece na mina com mais gente; quem ajudar a derrubar ganha $. */
final class GolemMina implements Listener {
    private final Mineracao pl;
    private IronGolem golem;
    private Mina onde;
    private BossBar barra;
    private final Map<UUID, Double> dano = new HashMap<>();
    private double vidaMax;

    GolemMina(Mineracao pl) {
        this.pl = pl;
        long min = Math.max(5, pl.getConfig().getLong("golem.intervalo-minutos", 30));
        Bukkit.getScheduler().runTaskTimer(pl, this::talvezSurgir, 20L * 60 * 6, 20L * 60 * min);
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 20L, 20L);
    }

    void talvezSurgir() {
        if (golem != null && golem.isValid()) return;
        Mina melhor = null;
        int mais = 0;
        for (Mina m : pl.minas) {
            if (m.pvp) continue;
            int n = 0;
            for (Player p : pl.mundo.getPlayers()) if (m.naArea(p.getLocation())) n++;
            if (n > mais) { mais = n; melhor = m; }
        }
        if (melhor != null) surgir(melhor, mais);
    }

    boolean surgir(Mina m, int jogadores) {
        Location l = m.chegada(pl.mundo);
        onde = m;
        vidaMax = Math.min(1024, 200 + 120 * jogadores + 40 * m.rank);
        dano.clear();
        golem = pl.mundo.spawn(l, IronGolem.class, g -> {
            g.setPlayerCreated(false);
            var a = g.getAttribute(Attribute.MAX_HEALTH);
            if (a != null) a.setBaseValue(vidaMax);
            g.setHealth(vidaMax);
            g.customName(Mineracao.txt("§6§l⛏ Golem de Minério"));
            g.setCustomNameVisible(true);
            g.setGlowing(true);
            g.setRemoveWhenFarAway(false);
            g.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(pl, "golem"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        });
        barra = Bukkit.createBossBar("§6§l⛏ GOLEM DE MINÉRIO §8» §f" + m.nome.split(" • ")[0], BarColor.YELLOW, BarStyle.SEGMENTED_10);
        for (Player p : pl.mundo.getPlayers()) {
            p.sendMessage(Mineracao.txt("§6§l⛏ GOLEM DE MINÉRIO! §eApareceu no §f" + m.nome.split(" • ")[0] + "§e. Quem ajudar a derrubar ganha §2$§e! §7(/minas)"));
            if (m.naArea(p.getLocation())) p.showTitle(net.kyori.adventure.title.Title.title(Mineracao.txt("§6§l⛏ GOLEM DE MINÉRIO"), Mineracao.txt("§fderrube ele e ganhe $!")));
        }
        pl.mundo.playSound(l, Sound.ENTITY_IRON_GOLEM_REPAIR, 2f, 0.5f);
        return true;
    }

    private void tick() {
        if (golem == null) return;
        if (!golem.isValid() || golem.isDead()) { acabar(false); return; }
        Player alvo = null;
        double perto = 400;
        for (Player p : pl.mundo.getPlayers()) {
            if (!onde.naArea(p.getLocation()) || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(golem.getLocation());
            if (d < perto) { perto = d; alvo = p; }
            if (barra != null && !barra.getPlayers().contains(p)) barra.addPlayer(p);
        }
        if (barra != null) {
            for (Player p : new java.util.ArrayList<>(barra.getPlayers())) if (!p.isOnline() || p.getWorld() != pl.mundo || !onde.naArea(p.getLocation())) barra.removePlayer(p);
            barra.setProgress(Math.max(0, Math.min(1, golem.getHealth() / vidaMax)));
        }
        if (alvo != null) golem.setTarget(alvo);
        golem.getWorld().spawnParticle(Particle.BLOCK, golem.getLocation().add(0, 1.5, 0), 6, 0.5, 0.8, 0.5, org.bukkit.Material.GOLD_ORE.createBlockData());
        if (!onde.naArea(golem.getLocation())) golem.teleport(onde.chegada(pl.mundo));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void apanhou(EntityDamageByEntityEvent e) {
        if (golem == null || e.getEntity() != golem) return;
        Player a = Mineracao.atacante(e);
        if (a == null) { e.setCancelled(true); return; }
        e.setCancelled(false);
        dano.merge(a.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        if (golem == null || e.getEntity() != golem) return;
        e.getDrops().clear();
        e.setDroppedExp(80);
        acabar(true);
    }

    private void acabar(boolean derrubado) {
        if (barra != null) barra.removeAll();
        barra = null;
        if (derrubado) {
            double total = dano.values().stream().mapToDouble(Double::doubleValue).sum();
            StringBuilder quem = new StringBuilder();
            for (var en : dano.entrySet()) {
                Player p = Bukkit.getPlayer(en.getKey());
                if (p == null) continue;
                double parte = total <= 0 ? 0 : en.getValue() / total;
                long v = Math.round((1500 + 3000 * parte) * (pl.rank(p) + 1) * Mineracao.multPrest(pl.prestigio(p)));
                pl.setDinheiro(p, pl.dinheiro(p) + v);
                p.sendMessage(Mineracao.txt("§6⛏ §eVocê ajudou a derrubar o §6Golem de Minério§e: " + Mineracao.din(v) + " §7(" + Math.round(parte * 100) + "% do dano)"));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
                quem.append(quem.length() > 0 ? ", " : "").append(p.getName());
            }
            for (Player p : pl.mundo.getPlayers()) p.sendMessage(Mineracao.txt("§6⛏ §eO §6Golem de Minério §efoi derrubado por §f" + (quem.length() > 0 ? quem : "alguém") + "§e!"));
        }
        golem = null;
        onde = null;
        dano.clear();
    }

    void forcar(Player p) {
        Mina m = pl.minaDaArea(p.getLocation());
        if (m == null || m.pvp) { p.sendMessage("§cFique dentro de uma mina normal."); return; }
        if (golem != null && golem.isValid()) golem.remove();
        acabar(false);
        surgir(m, 1);
    }
}
