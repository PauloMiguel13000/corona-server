package br.servidor.extras;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * 14/11 (Paulo: "melhore muito"): PIÑATA no spawn — uma lhama colorida e parada aparece no centro da praca a cada
 * ~2h (com 3+ jogadores online). Todo mundo bate nela; cada golpe tem chance de soltar coronas e, quando estoura,
 * as coronas sao divididas pelo dano de cada um (minimo 20). So jogador causa dano. Admin: "extras pinata".
 */
final class Pinata implements Listener {
    static final double VIDA = 300;
    private final Extras pl;
    private final NamespacedKey kPinata;
    private final Map<UUID, Double> dano = new HashMap<>();
    private Llama atual;
    private BossBar barra;
    private long proxima;

    Pinata(Extras pl) {
        this.pl = pl;
        kPinata = new NamespacedKey(pl, "pinata");
        proxima = System.currentTimeMillis() + 60 * 60_000L;
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 20L * 30, 20L * 10);
    }

    private void tique() {
        if (atual != null) {
            if (!atual.isValid()) { limpar(); return; }
            if (barra != null) {
                barra.setProgress(Math.max(0, Math.min(1, atual.getHealth() / VIDA)));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld() == atual.getWorld() && p.getLocation().distanceSquared(atual.getLocation()) < 60 * 60) barra.addPlayer(p);
                    else barra.removePlayer(p);
                }
            }
            return;
        }
        if (System.currentTimeMillis() < proxima) return;
        proxima = System.currentTimeMillis() + Math.max(45, pl.getConfig().getInt("pinata.minutos", 120)) * 60_000L;
        if (Bukkit.getOnlinePlayers().size() >= 3 && pl.getConfig().getBoolean("pinata.ativo", true)) aparecer();
    }

    private World mundo() { return Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world")); }

    boolean aparecer() {
        if (atual != null && atual.isValid()) return false;
        World w = mundo();
        if (w == null) return false;
        Location l = new Location(w, SpawnPrincipal.CX + 0.5, SpawnPrincipal.PISO + 1, SpawnPrincipal.CZ + 4.5);
        if (!l.isChunkLoaded()) l.getChunk().load();
        dano.clear();
        atual = w.spawn(l, Llama.class, x -> {
            x.setAI(false);
            x.setSilent(true);
            x.setPersistent(false);
            x.setRemoveWhenFarAway(false);
            x.setColor(Llama.Color.values()[ThreadLocalRandom.current().nextInt(Llama.Color.values().length)]);
            x.customName(Extras.txt("§d§l🎉 PIÑATA §7(bata nela!)"));
            x.setCustomNameVisible(true);
            x.setGlowing(true);
            var a = x.getAttribute(Attribute.MAX_HEALTH);
            if (a != null) a.setBaseValue(VIDA);
            x.setHealth(VIDA);
            x.getPersistentDataContainer().set(kPinata, PersistentDataType.BYTE, (byte) 1);
        });
        barra = Bukkit.createBossBar("§d§l🎉 PIÑATA NO SPAWN §f— bata para ganhar coronas!", BarColor.PINK, BarStyle.SEGMENTED_20);
        Bukkit.broadcast(Extras.txt("§d§l🎉 PIÑATA NO SPAWN! §fVenha bater nela: cada golpe pode soltar coronas e quando estourar todo mundo ganha! §e/spawn"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1f);
        // some depois de 15 minutos se ninguem estourar
        Llama esta = atual;
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (esta.isValid() && esta == atual) { esta.remove(); limpar(); Bukkit.broadcast(Extras.txt("§7A piñata foi embora... ninguém estourou!")); } }, 20L * 900);
        return true;
    }

    private boolean eh(Entity e) { return e.getPersistentDataContainer().has(kPinata, PersistentDataType.BYTE); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (!eh(e.getEntity())) return;
        if (!(e instanceof EntityDamageByEntityEvent d) || !(atacante(d.getDamager()) instanceof Player p)) { e.setCancelled(true); return; }
        e.setCancelled(false);
        double v = Math.min(e.getFinalDamage(), 20);
        e.setDamage(v);
        dano.merge(p.getUniqueId(), v, Double::sum);
        Location l = e.getEntity().getLocation().add(0, 1.2, 0);
        l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 6, 0.4, 0.4, 0.4, 0);
        if (ThreadLocalRandom.current().nextInt(100) < 8) {
            int c = ThreadLocalRandom.current().nextInt(2, 8);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + c);
            p.sendActionBar(Extras.txt("§d🎉 A piñata soltou §6" + c + " coronas§d!"));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.5f);
        }
    }

    private static Entity atacante(Entity d) {
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity s) return s;
        return d;
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        if (!eh(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Location l = e.getEntity().getLocation();
        l.getWorld().spawnParticle(Particle.FIREWORK, l.clone().add(0, 1, 0), 120, 1.5, 1.5, 1.5, 0.15);
        l.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 60, 1, 1, 1, 0.3);
        l.getWorld().playSound(l, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2f, 1f);
        double total = dano.values().stream().mapToDouble(Double::doubleValue).sum();
        StringBuilder sb = new StringBuilder("§d§l🎉 A PIÑATA ESTOUROU! ");
        int n = 0;
        for (var en : dano.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) continue;
            int c = (int) Math.max(20, Math.round(600 * en.getValue() / Math.max(1, total)));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + c);
            p.sendMessage(Extras.txt("§d🎉 Sua parte da piñata: §6+" + c + " coronas"));
            n++;
        }
        Bukkit.broadcast(Extras.txt(sb.append("§f" + n + " jogador" + (n == 1 ? "" : "es") + " dividiram §6600 coronas§f!").toString()));
        limpar();
    }

    private void limpar() {
        atual = null;
        dano.clear();
        if (barra != null) { barra.removeAll(); barra = null; }
    }

    @EventHandler
    public void carregou(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (eh(en) && en != atual) en.remove();
    }

    void desligar() {
        if (atual != null && atual.isValid()) atual.remove();
        limpar();
    }
}
