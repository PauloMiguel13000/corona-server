package br.servidor.extras;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 13/11 (Paulo: "faca ele ficar fodao"): LUA DE SANGUE no Survival. Algumas noites (1 em 4) os monstros nascem
 * mais fortes, com nome vermelho, e pagam coronas + chance de loot raro. Avisa no comeco e no fim da noite.
 */
final class LuaDeSangue implements Listener {
    private final Extras pl;
    private final NamespacedKey kSangue;
    private boolean ativa;
    private long noiteAnunciada = -1;

    LuaDeSangue(Extras pl) {
        this.pl = pl;
        kSangue = new NamespacedKey(pl, "lua_sangue");
        Bukkit.getScheduler().runTaskTimer(pl, this::checar, 200L, 200L);
    }

    private World mundo() { return Bukkit.getWorld(pl.getConfig().getString("lua-de-sangue.mundo", "world")); }

    private void checar() {
        World w = mundo();
        if (w == null || !pl.getConfig().getBoolean("lua-de-sangue.ligada", true)) return;
        long t = w.getTime(), dia = w.getFullTime() / 24000L;
        boolean noite = t >= 13000 && t < 23000;
        if (noite && !ativa && noiteAnunciada != dia) {
            noiteAnunciada = dia;
            // sorteio por dia do mundo: 1 em 4 noites (e nunca duas seguidas)
            if (new java.util.Random(dia * 7919L + w.getSeed()).nextInt(4) == 0 && new java.util.Random((dia - 1) * 7919L + w.getSeed()).nextInt(4) != 0) {
                ativa = true;
                for (Player p : w.getPlayers()) {
                    p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§4§l☾ LUA DE SANGUE ☽"), Extras.txt("§cos monstros estão mais fortes... e pagam coronas!")));
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.6f);
                }
                Bukkit.broadcast(Extras.txt("§4§l☾ LUA DE SANGUE ☽ §cno Survival! §7Monstros mais fortes pagam §6coronas §7e podem soltar §dloot raro§7 até o amanhecer."));
            }
        } else if (!noite && ativa) {
            ativa = false;
            for (Player p : w.getPlayers()) p.sendMessage(Extras.txt("§6☀ §7O sol nasceu: a §4Lua de Sangue §7acabou."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void nasceu(CreatureSpawnEvent e) {
        if (!ativa || e.getEntity().getWorld() != mundo() || !(e.getEntity() instanceof Enemy)) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity m = e.getEntity();
        m.getPersistentDataContainer().set(kSangue, PersistentDataType.BYTE, (byte) 1);
        var hp = m.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(hp.getBaseValue() * 1.6); m.setHealth(hp.getBaseValue()); }
        m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
        m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, false));
        m.customName(Extras.txt("§4☾ §c" + nome(m)));
        m.setCustomNameVisible(false);
    }

    private static String nome(LivingEntity m) {
        return switch (m.getType()) {
            case ZOMBIE -> "Zumbi Sangrento";
            case SKELETON -> "Esqueleto Sangrento";
            case SPIDER -> "Aranha Sangrenta";
            case CREEPER -> "Creeper Sangrento";
            default -> "Monstro Sangrento";
        };
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kSangue, PersistentDataType.BYTE)) return;
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int v = 4 + r.nextInt(7);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + k.getName() + " " + v);
        k.sendActionBar(Extras.txt("§4☾ §6+" + v + " coronas §7(Lua de Sangue)"));
        e.getEntity().getWorld().spawnParticle(Particle.DUST, e.getEntity().getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(170, 0, 0), 1.4f));
        int sorte = r.nextInt(100);
        if (sorte < 2) e.getDrops().add(new ItemStack(Material.DIAMOND, 1 + r.nextInt(2)));
        else if (sorte < 5) e.getDrops().add(new ItemStack(Material.GOLDEN_APPLE));
        else if (sorte < 10) e.getDrops().add(new ItemStack(Material.EMERALD, 1 + r.nextInt(3)));
        e.setDroppedExp(e.getDroppedExp() * 2);
    }

    boolean ativa() { return ativa; }

    /** Admin: forca a lua de sangue agora (teste). */
    void forcar() { ativa = true; Bukkit.broadcast(Extras.txt("§4§l☾ LUA DE SANGUE ☽ §c(forçada pela staff)")); }
}
