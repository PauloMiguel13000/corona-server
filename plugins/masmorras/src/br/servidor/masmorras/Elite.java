package br.servidor.masmorras;

import java.util.concurrent.ThreadLocalRandom;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 13/11 (Paulo: "muitas coisas na masmorra"): monstros ELITE raros no mundo das masmorras — brilham, sao mais fortes e pagam muito mais. */
final class Elite implements Listener {
    private final Masmorras pl;
    private final NamespacedKey kElite;

    Elite(Masmorras pl) {
        this.pl = pl;
        kElite = new NamespacedKey(pl, "elite");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void nasceu(CreatureSpawnEvent e) {
        LivingEntity m = e.getEntity();
        if (!(m instanceof Enemy) || !m.getWorld().getName().startsWith("masmorras")) return;
        if (m.getPersistentDataContainer().has(pl.kChefe, PersistentDataType.INTEGER)) return;
        if (ThreadLocalRandom.current().nextDouble() >= pl.getConfig().getDouble("elite.chance", 0.04)) return;
        org.bukkit.Bukkit.getScheduler().runTask(pl, () -> {
            if (!m.isValid()) return;
            m.getPersistentDataContainer().set(kElite, PersistentDataType.BYTE, (byte) 1);
            var hp = m.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(hp.getBaseValue() * 2.5); m.setHealth(hp.getBaseValue()); }
            m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, false));
            m.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
            m.setGlowing(true);
            String nome = m.customName() != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(m.customName()) : m.getType().name().toLowerCase().replace('_', ' ');
            m.customName(Masmorras.txt("§5§l✦ ELITE §d" + nome));
            m.setCustomNameVisible(true);
        });
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kElite, PersistentDataType.BYTE)) return;
        Player k = e.getEntity().getKiller();
        e.setDroppedExp(e.getDroppedExp() * 3 + 20);
        e.getEntity().getWorld().spawnParticle(Particle.WITCH, e.getEntity().getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
        if (k == null || pl.coronas == null) return;
        long v = pl.coronas.comVip(k, 30 + ThreadLocalRandom.current().nextInt(31));
        pl.coronas.dar(k, v, false);
        k.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§5✦ Você derrotou um §5§lELITE§7! §6+" + Coronas.fmt(v) + " coronas"));
        k.playSound(k.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.7f);
    }
}
