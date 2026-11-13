package br.servidor.masmorras;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 13/11 (Paulo: "muitas coisas na masmorra"): MIMICO. Alguns baus das masmorras sao monstros disfarcados:
 * ao abrir, o bau "acorda" e ataca. Derrotando o Mimico, o bau abre normal e ainda paga coronas.
 */
final class Mimico implements Listener {
    private final Masmorras pl;
    private final NamespacedKey kMimico;
    private final Set<Location> liberados = new HashSet<>();   // baus ja testados nesta vida do servidor

    Mimico(Masmorras pl) {
        this.pl = pl;
        kMimico = new NamespacedKey(pl, "mimico");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void abriu(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !(e.getInventory().getHolder() instanceof Chest c)) return;
        Block b = c.getBlock();
        Location l = b.getLocation();
        if (liberados.contains(l)) return;
        Masmorra m = pl.masmorraEm(b.getWorld(), b.getX(), b.getY(), b.getZ());
        if (m == null) return;
        liberados.add(l);
        double chance = pl.getConfig().getDouble("mimico.chance", 0.06);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        e.setCancelled(true);
        int nv = m.tipo.nivel;
        Location onde = l.clone().add(0.5, 1, 0.5);
        Zombie z = b.getWorld().spawn(onde, Zombie.class, zz -> {
            zz.setAdult();
            zz.customName(Masmorras.txt("§6§lMímico §7(nível " + nv + ")"));
            zz.setCustomNameVisible(true);
            zz.getEquipment().setHelmet(new ItemStack(Material.CHEST));
            zz.getEquipment().setHelmetDropChance(0f);
            var hp = zz.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(30 + 25 * nv); zz.setHealth(hp.getBaseValue()); }
            zz.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
            zz.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, Math.max(0, nv / 2 - 1), false, false));
            zz.setShouldBurnInDay(false);
            zz.getPersistentDataContainer().set(kMimico, PersistentDataType.INTEGER, nv);
            zz.setTarget(p);
        });
        b.getWorld().playSound(l, Sound.BLOCK_CHEST_CLOSE, 1.5f, 0.5f);
        b.getWorld().playSound(l, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.6f);
        b.getWorld().spawnParticle(Particle.BLOCK, onde, 30, 0.4, 0.4, 0.4, Material.OAK_PLANKS.createBlockData());
        p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§6§lMÍMICO!"), Masmorras.txt("§eo baú era um monstro! derrote-o para abrir")));
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        Integer nv = e.getEntity().getPersistentDataContainer().get(kMimico, PersistentDataType.INTEGER);
        if (nv == null) return;
        e.getDrops().clear();
        Player k = e.getEntity().getKiller();
        if (k == null || pl.coronas == null) return;
        long v = pl.coronas.comVip(k, 40L * nv);
        pl.coronas.dar(k, v, false);
        k.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§6Você derrotou o Mímico! §6+" + Coronas.fmt(v) + " coronas §7— agora o baú abre normal."));
        k.playSound(k.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }
}
