package br.servidor.extras;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 13/11 (Paulo: "atualize muita coisa"): no Survival
 *  - MONSTRO LENDARIO: 0,5% dos monstros nascem dourados, brilhando, e pagam 50 coronas + diamante;
 *  - XP EM DOBRO: de vez em quando (a cada ~3 h) 30 min de XP em dobro no Survival.
 */
final class EventosSurvival implements Listener {
    private final Extras pl;
    private final NamespacedKey kLendario;
    private long xpAte;

    EventosSurvival(Extras pl) {
        this.pl = pl;
        kLendario = new NamespacedKey(pl, "lendario");
        Bukkit.getScheduler().runTaskTimer(pl, this::sortearXp, 20L * 60 * 20, 20L * 60 * 60);
    }

    boolean xpDobro() { return System.currentTimeMillis() < xpAte; }

    private void sortearXp() {
        if (xpDobro() || ThreadLocalRandom.current().nextInt(3) != 0) return;
        xpAte = System.currentTimeMillis() + 30 * 60_000L;
        Bukkit.broadcast(Extras.txt("§a§l✦ XP EM DOBRO ✦ §fno §aSurvival §fpor §e30 minutos§f! Bora minerar e caçar!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);
    }

    void forcarXp() { xpAte = 0; xpAte = System.currentTimeMillis() + 30 * 60_000L; Bukkit.broadcast(Extras.txt("§a§l✦ XP EM DOBRO ✦ §f(30 min, Survival)")); }

    @EventHandler
    public void xp(PlayerExpChangeEvent e) {
        if (xpDobro() && e.getAmount() > 0 && KitsContexto.lugar(e.getPlayer().getWorld()) == KitsContexto.Lugar.SURVIVAL) e.setAmount(e.getAmount() * 2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void nasceu(CreatureSpawnEvent e) {
        LivingEntity m = e.getEntity();
        if (!(m instanceof Enemy) || e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (KitsContexto.lugar(m.getWorld()) != KitsContexto.Lugar.SURVIVAL) return;
        if (ThreadLocalRandom.current().nextInt(1000) >= pl.getConfig().getInt("monstro-lendario.por-mil", 5)) return;
        m.getPersistentDataContainer().set(kLendario, PersistentDataType.BYTE, (byte) 1);
        var hp = m.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(hp.getBaseValue() * 3); m.setHealth(hp.getBaseValue()); }
        m.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        m.setGlowing(true);
        m.customName(Extras.txt("§6§l★ LENDÁRIO ★"));
        m.setCustomNameVisible(true);
        m.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        m.getEquipment().setHelmetDropChance(0f);
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kLendario, PersistentDataType.BYTE)) return;
        Player k = e.getEntity().getKiller();
        e.setDroppedExp(e.getDroppedExp() * 5 + 30);
        e.getEntity().getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, e.getEntity().getLocation().add(0, 1, 0), 50, 0.5, 0.8, 0.5, 0.3);
        if (k == null) return;
        e.getDrops().add(new ItemStack(Material.DIAMOND, 1 + ThreadLocalRandom.current().nextInt(3)));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + k.getName() + " 50");
        Bukkit.broadcast(Extras.txt("§6★ §e" + k.getName() + " §7derrotou um §6§lMONSTRO LENDÁRIO §7no Survival! §6+50 coronas"));
        k.playSound(k.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }
}
