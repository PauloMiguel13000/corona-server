package br.servidor.tiro;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Os infectados dos jogos de tiro (15/11): comum, corredor (rapido e fraco), tanque (grande, lento, aguenta muito),
 * cuspidor (cospe acido de longe), explosivo (corre e estoura; nao quebra bloco) e o chefe de horda (enorme, chama
 * reforco). Cada um e um zumbi do jogo com atributos, roupa e comportamento proprios; o tipo fica marcado no bicho.
 */
final class Zumbis implements Listener {

    enum Tipo {
        COMUM("§2Infectado", 20, 0.23, 3, 1.0, 1),
        CORREDOR("§cCorredor", 14, 0.36, 3, 0.95, 2),
        TANQUE("§6§lTanque", 90, 0.19, 9, 1.5, 6),
        CUSPIDOR("§aCuspidor", 18, 0.22, 2, 1.0, 3),
        EXPLOSIVO("§eExplosivo", 16, 0.31, 0, 1.05, 3),
        CHEFE("§4§lCHEFE DE HORDA", 320, 0.25, 12, 1.9, 40);

        final String nome;
        final double vida, veloc, dano, escala;
        final int pontos;

        Tipo(String nome, double vida, double veloc, double dano, double escala, int pontos) {
            this.nome = nome;
            this.vida = vida;
            this.veloc = veloc;
            this.dano = dano;
            this.escala = escala;
            this.pontos = pontos;
        }
    }

    /** Aviso de morte para o jogo dono do bicho. */
    interface Dono {
        void morreu(Zombie z, Tipo t, Player matador);
    }

    final Tiro pl;
    final NamespacedKey kTipo, kCuspe;
    private final Map<UUID, Long> ultimoCuspe = new HashMap<>(), pavio = new HashMap<>(), ultimoReforco = new HashMap<>();
    private final Random rnd = new Random();
    Dono dono;

    Zumbis(Tiro pl) {
        this.pl = pl;
        kTipo = new NamespacedKey(pl, "zumbi_tipo");
        kCuspe = new NamespacedKey(pl, "cuspe");
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 10L, 10L);
    }

    Tipo tipo(Entity e) {
        if (!(e instanceof Zombie)) return null;
        String s = e.getPersistentDataContainer().get(kTipo, PersistentDataType.STRING);
        try {
            return s == null ? null : Tipo.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ItemStack couro(Material m, int cor) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(Color.fromRGB(cor));
        it.setItemMeta(lm);
        return it;
    }

    /** forca = multiplicador de vida (dificuldade). */
    Zombie nascer(Location l, Tipo t, double forca) {
        return l.getWorld().spawn(l, Zombie.class, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, z -> {
            z.getPersistentDataContainer().set(kTipo, PersistentDataType.STRING, t.name());
            z.setAdult();
            z.setShouldBurnInDay(false);
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(false);
            z.customName(Tiro.txt(t.nome));
            z.setCustomNameVisible(t == Tipo.TANQUE || t == Tipo.CHEFE);
            atributo(z, Attribute.MAX_HEALTH, t.vida * forca);
            z.setHealth(t.vida * forca);
            atributo(z, Attribute.MOVEMENT_SPEED, t.veloc);
            atributo(z, Attribute.ATTACK_DAMAGE, Math.max(0.5, t.dano));
            atributo(z, Attribute.SCALE, t.escala);
            atributo(z, Attribute.FOLLOW_RANGE, 34);
            atributo(z, Attribute.SPAWN_REINFORCEMENTS, 0);
            if (t == Tipo.TANQUE || t == Tipo.CHEFE) atributo(z, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            var eq = z.getEquipment();
            eq.clear();
            switch (t) {
                case CORREDOR -> {
                    eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0xB02020));
                    eq.setLeggings(couro(Material.LEATHER_LEGGINGS, 0x30303A));
                }
                case TANQUE -> {
                    eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                    eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                    eq.setLeggings(couro(Material.LEATHER_LEGGINGS, 0x3A2A1A));
                }
                case CUSPIDOR -> {
                    eq.setHelmet(new ItemStack(Material.SLIME_BLOCK));
                    eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0x58C030));
                }
                case EXPLOSIVO -> {
                    eq.setHelmet(new ItemStack(Material.TNT));
                    eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0xE0C020));
                }
                case CHEFE -> {
                    eq.setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
                    eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                    eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                    eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
                    z.setGlowing(true);
                }
                default -> {
                    if (rnd.nextInt(3) == 0) eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, 0x405060 + rnd.nextInt(0x202020)));
                }
            }
            for (org.bukkit.inventory.EquipmentSlot s : org.bukkit.inventory.EquipmentSlot.values()) {
                try {
                    eq.setDropChance(s, 0f);
                } catch (Throwable ignorado) {
                    // slot que zumbi nao tem
                }
            }
        });
    }

    private static void atributo(LivingEntity e, Attribute a, double v) {
        var at = e.getAttribute(a);
        if (at != null) at.setBaseValue(v);
    }

    // ------------------------------------------------------------------ comportamento

    private void tique() {
        long agora = System.currentTimeMillis();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            if (!pl.mundoDeTiro(w) || w.getPlayers().isEmpty()) continue;
            for (Zombie z : w.getEntitiesByClass(Zombie.class)) {
                Tipo t = tipo(z);
                if (t == null || z.isDead()) continue;
                LivingEntity alvo = z.getTarget();
                if (!(alvo instanceof Player p) || p.isDead() || p.getWorld() != w) continue;
                double d = z.getLocation().distance(p.getLocation());
                UUID u = z.getUniqueId();
                if (t == Tipo.CUSPIDOR && d < 15 && d > 3 && z.hasLineOfSight(p) && agora - ultimoCuspe.getOrDefault(u, 0L) > 2800) {
                    ultimoCuspe.put(u, agora);
                    Location de = z.getEyeLocation();
                    Vector v = p.getEyeLocation().toVector().subtract(de.toVector()).normalize().multiply(1.25).add(new Vector(0, 0.08 + d * 0.006, 0));
                    Snowball s = w.spawn(de.add(v.clone().multiply(0.6)), Snowball.class, x -> {
                        x.setItem(new ItemStack(Material.SLIME_BALL));
                        x.setShooter(z);
                        x.getPersistentDataContainer().set(kCuspe, PersistentDataType.BYTE, (byte) 1);
                    });
                    s.setVelocity(v);
                    w.playSound(z.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.2f, 0.7f);
                }
                if (t == Tipo.EXPLOSIVO) {
                    Long p0 = pavio.get(u);
                    if (p0 == null && d < 2.6) {
                        pavio.put(u, agora);
                        z.setGlowing(true);
                        w.playSound(z.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.4f, 1.2f);
                        z.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, false));
                    } else if (p0 != null && agora - p0 > 1100) {
                        pavio.remove(u);
                        Location l = z.getLocation();
                        z.remove();
                        w.createExplosion(l, 2.6f, false, false, z);
                        w.spawnParticle(Particle.EXPLOSION_EMITTER, l.add(0, 1, 0), 1);
                    }
                }
                if (t == Tipo.CHEFE && agora - ultimoReforco.getOrDefault(u, agora - 6000) > 12000 && d < 30) {
                    ultimoReforco.put(u, agora);
                    w.playSound(z.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
                    for (int i = 0; i < 3; i++) {
                        Location l = z.getLocation().add(rnd.nextInt(7) - 3, 0, rnd.nextInt(7) - 3);
                        l.setY(l.getWorld().getHighestBlockYAt(l) + 1);
                        if (Math.abs(l.getY() - z.getLocation().getY()) > 6) l = z.getLocation();
                        Zombie r = nascer(l, rnd.nextBoolean() ? Tipo.CORREDOR : Tipo.COMUM, 1.0);
                        r.setTarget(p);
                    }
                }
            }
        }
    }

    @EventHandler
    public void cuspe(ProjectileHitEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kCuspe)) return;
        e.getEntity().getWorld().spawnParticle(Particle.ITEM_SLIME, e.getEntity().getLocation(), 12, 0.2, 0.2, 0.2, 0.05);
        if (e.getHitEntity() instanceof Player p) {
            p.damage(3.0, e.getEntity().getShooter() instanceof Entity en ? en : null);
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false));
        }
    }

    /** Os infectados so vao atras de jogador. */
    @EventHandler(ignoreCancelled = true)
    public void alvo(EntityTargetLivingEntityEvent e) {
        if (tipo(e.getEntity()) != null && e.getTarget() != null && !(e.getTarget() instanceof Player)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fogo(EntityCombustEvent e) {
        if (tipo(e.getEntity()) != null && !(e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent) && !(e instanceof org.bukkit.event.entity.EntityCombustByBlockEvent)) e.setCancelled(true);
    }

    /** A explosao do Explosivo nao machuca os outros infectados. */
    @EventHandler(ignoreCancelled = true)
    public void amigo(EntityDamageByEntityEvent e) {
        if (tipo(e.getEntity()) != null && tipo(e.getDamager()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void morte(EntityDeathEvent e) {
        Tipo t = tipo(e.getEntity());
        if (t == null) return;
        e.getDrops().clear();
        e.setDroppedExp(t.pontos);
        UUID u = e.getEntity().getUniqueId();
        ultimoCuspe.remove(u);
        pavio.remove(u);
        ultimoReforco.remove(u);
        if (dono != null) dono.morreu((Zombie) e.getEntity(), t, e.getEntity().getKiller());
    }
}
