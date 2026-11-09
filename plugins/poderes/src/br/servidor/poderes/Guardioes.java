package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Guardiao de Ferro: golem temporario que so ataca os inimigos do dono. */
final class Guardioes implements Listener {

    private final Poderes plugin;
    private final NamespacedKey chaveDono;
    /** dono -> golem */
    private final Map<UUID, UUID> guardiaoDoDono = new HashMap<>();
    private final Map<UUID, BukkitTask> tarefas = new HashMap<>();

    Guardioes(Poderes plugin) {
        this.plugin = plugin;
        this.chaveDono = new NamespacedKey(plugin, "guardiao_dono");
    }

    // ------------------------------------------------------------------ invocar / remover

    boolean invocar(Player p, int segundos) {
        if (guardiao(p) != null) {
            Poderes.barra(p, "§eSeu guardiao ja esta lutando!");
            return false;
        }
        Location l = localPerto(p);
        IronGolem g = p.getWorld().spawn(l, IronGolem.class, gol -> {
            gol.setPlayerCreated(true);
            gol.customName(Poderes.txt("§fGuardiao de " + p.getName()));
            gol.setCustomNameVisible(true);
            gol.setPersistent(false);
            gol.setRemoveWhenFarAway(true);
            gol.getPersistentDataContainer().set(chaveDono, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        UUID dono = p.getUniqueId();
        guardiaoDoDono.put(dono, g.getUniqueId());
        Fx.p(g.getWorld(), Particle.CLOUD, g.getLocation().add(0, 1, 0), 40, 0.6, 1.0, 0.6, 0.05);
        g.getWorld().playSound(g.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 0.8f);
        g.getWorld().playSound(g.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
        org.bukkit.Color ferro = org.bukkit.Color.fromRGB(230, 230, 240);
        plugin.fx.colunaDeLuz(g.getLocation(), Efeitos.b(org.bukkit.Material.WHITE_STAINED_GLASS), 5.0, 1.6, 10, ferro);
        plugin.cena.golemChega(g.getLocation());
        plugin.fx.estilhacos(g.getLocation().add(0, 0.3, 0), new org.bukkit.block.data.BlockData[] {Poderes.chaoDe(g.getLocation()), Efeitos.b(org.bukkit.Material.IRON_BLOCK), Efeitos.b(org.bukkit.Material.COBBLESTONE)}, 14, 3.2, 18, 0.4f);
        plugin.fx.anel(g.getLocation().add(0, 0.1, 0), Efeitos.b(org.bukkit.Material.IRON_BLOCK), 2.8, 6, 12, 0.35f, ferro);
        Poderes.barra(p, "§fGuardiao de Ferro invocado por §e" + segundos + "s");

        final int[] restante = {segundos};
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!g.isValid() || g.isDead() || !p.isOnline()) {
                remover(dono, false);
                return;
            }
            restante[0]--;
            if (restante[0] <= 0) {
                remover(dono, true);
                return;
            }
            if (restante[0] == 5) Poderes.barra(p, "§7Seu guardiao vai embora em 5s");
            boolean longe = !g.getWorld().equals(p.getWorld())
                    || g.getLocation().distanceSquared(p.getLocation()) > 24 * 24;
            if (longe) {
                g.teleport(localPerto(p));
                g.setTarget(null);
                return;
            }
            LivingEntity alvo = g.getTarget();
            if (alvo == null || !alvo.isValid() || alvo.isDead()) {
                LivingEntity perto = inimigoPerto(p, g);
                if (perto != null) {
                    g.setTarget(perto);
                } else if (g.getLocation().distanceSquared(p.getLocation()) > 5 * 5) {
                    g.getPathfinder().moveTo(p.getLocation());
                }
            }
        }, 20L, 20L);
        tarefas.put(dono, t);
        return true;
    }

    private LivingEntity inimigoPerto(Player p, IronGolem g) {
        LivingEntity melhor = null;
        double dist = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(12, 6, 12)) {
            if (!(e instanceof LivingEntity le) || !(e instanceof Enemy) || le.isDead()) continue;
            if (ehGuardiao(e) || plugin.pets.ehPet(e)) continue;
            double d = e.getLocation().distanceSquared(g.getLocation());
            if (d < dist) {
                dist = d;
                melhor = le;
            }
        }
        return melhor;
    }

    IronGolem guardiao(Player p) {
        UUID id = guardiaoDoDono.get(p.getUniqueId());
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        return e instanceof IronGolem g && g.isValid() && !g.isDead() ? g : null;
    }

    boolean ehGuardiao(Entity e) {
        return e instanceof IronGolem && e.getPersistentDataContainer().has(chaveDono, PersistentDataType.STRING);
    }

    boolean ehGuardiaoDe(Entity e, Player p) {
        if (!ehGuardiao(e)) return false;
        return p.getUniqueId().equals(dono(e));
    }

    private UUID dono(Entity e) {
        String s = e.getPersistentDataContainer().get(chaveDono, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    void remover(UUID dono, boolean avisar) {
        BukkitTask t = tarefas.remove(dono);
        if (t != null) t.cancel();
        UUID id = guardiaoDoDono.remove(dono);
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null && e.isValid()) {
            Fx.p(e.getWorld(), Particle.CLOUD, e.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.6f, 1.4f);
            e.remove();
        }
        Player p = Bukkit.getPlayer(dono);
        if (avisar && p != null) Poderes.barra(p, "§7Seu guardiao foi embora.");
    }

    void removerTodos() {
        for (UUID d : new ArrayList<>(guardiaoDoDono.keySet())) remover(d, false);
    }

    private Location localPerto(Player p) {
        Location base = p.getLocation();
        Vector dir = base.getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        dir.normalize().multiply(2.0);
        Location l = base.clone().add(dir);
        Block b = l.getBlock();
        for (int i = 0; i < 3 && !b.isPassable(); i++) b = b.getRelative(BlockFace.UP);
        boolean cabe = b.isPassable() && b.getRelative(BlockFace.UP).isPassable()
                && b.getRelative(BlockFace.UP, 2).isPassable();
        if (!cabe) return base;
        Location r = b.getLocation().add(0.5, 0, 0.5);
        r.setYaw(base.getYaw());
        return r;
    }

    // ------------------------------------------------------------------ comportamento

    private static Player jogadorDe(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    /** Quem o dono bate, e quem bate no dono, vira alvo do guardiao. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void marcarAlvo(EntityDamageByEntityEvent e) {
        Entity vitima = e.getEntity();
        Entity causador = e.getDamager();
        Player dono = jogadorDe(causador);
        if (dono != null && vitima instanceof LivingEntity alvo && !plugin.ehAliado(alvo, dono) && !ehGuardiao(alvo)) {
            IronGolem g = guardiao(dono);
            if (g != null) g.setTarget(alvo);
            return;
        }
        if (vitima instanceof Player jogador) {
            Entity atacante = causador;
            if (causador instanceof Projectile pr && pr.getShooter() instanceof Entity atirador) atacante = atirador;
            if (atacante instanceof LivingEntity la && !ehGuardiao(la) && !plugin.ehAliado(la, jogador)) {
                IronGolem g = guardiao(jogador);
                if (g != null) g.setTarget(la);
            }
        }
    }

    /** O guardiao nunca machuca o dono nem os aliados, e o dono nao machuca o guardiao sem querer. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protegerAliados(EntityDamageByEntityEvent e) {
        if (ehGuardiao(e.getDamager())) {
            UUID d = dono(e.getDamager());
            Player p = d == null ? null : Bukkit.getPlayer(d);
            if (p != null && (e.getEntity().equals(p) || plugin.ehAliado(e.getEntity(), p) || plugin.pets.ehPet(e.getEntity()))) {
                e.setCancelled(true);
                return;
            }
            double forca = p != null ? plugin.fator(p) : 1.0;
            e.setDamage(Math.min(e.getDamage(), Poderes.DANO_GUARDIAO * forca));
            return;
        }
        if (ehGuardiao(e.getEntity())) {
            Player atk = jogadorDe(e.getDamager());
            UUID d = dono(e.getEntity());
            if (atk != null && d != null && atk.getUniqueId().equals(d)) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void escolherAlvo(EntityTargetLivingEntityEvent e) {
        if (!ehGuardiao(e.getEntity())) return;
        LivingEntity t = e.getTarget();
        if (t == null) return;
        UUID d = dono(e.getEntity());
        Player p = d == null ? null : Bukkit.getPlayer(d);
        if (p == null) return;
        if (t.equals(p) || plugin.ehAliado(t, p) || plugin.pets.ehPet(t) || ehGuardiao(t)) e.setCancelled(true);
    }

    /** Guardiao nao deixa ferro nem XP (nada de fazenda de ferro com o poder). */
    @EventHandler
    public void guardiaoMorreu(EntityDeathEvent e) {
        if (!ehGuardiao(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        UUID d = dono(e.getEntity());
        if (d != null) {
            BukkitTask t = tarefas.remove(d);
            if (t != null) t.cancel();
            guardiaoDoDono.remove(d);
        }
    }

    @EventHandler
    public void donoSaiu(PlayerQuitEvent e) {
        remover(e.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void donoMorreu(PlayerDeathEvent e) {
        remover(e.getEntity().getUniqueId(), false);
    }
}
