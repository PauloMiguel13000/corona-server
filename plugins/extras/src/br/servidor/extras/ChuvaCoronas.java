package br.servidor.extras;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 13/11 (Paulo: "melhore o spawn"): de tempos em tempos cai uma CHUVA DE CORONAS na praca do spawn — moedas
 * douradas espalhadas pelo chao; quem pegar ganha coronas na hora. Aviso 1 minuto antes. So acontece com
 * pelo menos 2 jogadores online. Admin: "extras chuva" forca uma agora.
 */
final class ChuvaCoronas implements Listener {
    private final Extras pl;
    private final NamespacedKey kMoeda;
    private long proxima;

    ChuvaCoronas(Extras pl) {
        this.pl = pl;
        kMoeda = new NamespacedKey(pl, "moeda_chuva");
        proxima = System.currentTimeMillis() + minutos() * 60_000L / 2;
        Bukkit.getScheduler().runTaskTimer(pl, this::relogio, 20L * 30, 20L * 30);
    }

    private int minutos() { return Math.max(20, pl.getConfig().getInt("chuva-coronas.minutos", 75)); }

    private void relogio() {
        if (!pl.getConfig().getBoolean("chuva-coronas.ativo", true)) return;
        if (System.currentTimeMillis() < proxima) return;
        proxima = System.currentTimeMillis() + minutos() * 60_000L;
        if (Bukkit.getOnlinePlayers().size() < 2) return;
        anunciar();
    }

    void anunciar() {
        Bukkit.broadcast(Extras.txt("§6§l⛃ CHUVA DE CORONAS §eno §fSPAWN §eem 1 minuto! §7Corra pra praça e pegue as moedas douradas. §f/spawn"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
        Bukkit.getScheduler().runTaskLater(pl, this::chover, 20L * 60);
    }

    private World mundo() { return Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world")); }

    private void chover() {
        World w = mundo();
        if (w == null) return;
        Location c = new Location(w, SpawnPrincipal.CX + 0.5, SpawnPrincipal.PISO + 1, SpawnPrincipal.CZ + 0.5);
        if (!w.isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) w.getChunkAt(c).load();
        int n = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < 120 && n < 30; t++) {
            int x = SpawnPrincipal.CX + r.nextInt(-20, 21), z = SpawnPrincipal.CZ + r.nextInt(-20, 21);
            if ((x - SpawnPrincipal.CX) * (x - SpawnPrincipal.CX) + (z - SpawnPrincipal.CZ) * (z - SpawnPrincipal.CZ) > 400) continue;
            Location l = chao(w, x, z);
            if (l == null) continue;
            ItemStack s = new ItemStack(Material.GOLD_NUGGET);
            var m = s.getItemMeta();
            m.displayName(Extras.txt("§6Corona"));
            m.getPersistentDataContainer().set(kMoeda, PersistentDataType.BYTE, (byte) 1);
            m.setEnchantmentGlintOverride(true);
            s.setItemMeta(m);
            Item it = w.dropItem(l.clone().add(0, 4, 0), s);
            it.getPersistentDataContainer().set(kMoeda, PersistentDataType.BYTE, (byte) 1);
            it.setGlowing(true);
            it.setUnlimitedLifetime(false);
            it.setTicksLived(1);
            it.setCanMobPickup(false);
            it.setCustomNameVisible(false);
            n++;
        }
        w.spawnParticle(Particle.FIREWORK, c.clone().add(0, 6, 0), 80, 8, 2, 8, 0.05);
        for (Player p : w.getPlayers())
            if (p.getLocation().distanceSquared(c) < 60 * 60) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);
        Bukkit.broadcast(Extras.txt("§6§l⛃ CHUVA DE CORONAS §eno spawn AGORA! §7(" + n + " moedas) §f/spawn"));
        // moedas que ninguem pegou somem em 3 minutos
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (Entity e : w.getEntitiesByClass(Item.class))
                if (e.getPersistentDataContainer().has(kMoeda, PersistentDataType.BYTE)) e.remove();
        }, 20L * 180);
    }

    private static Location chao(World w, int x, int z) {
        for (int y = SpawnPrincipal.PISO + 6; y >= SpawnPrincipal.PISO - 3; y--) {
            Block b = w.getBlockAt(x, y, z);
            if (b.isPassable() && !b.isLiquid() && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, -1, 0).getType().isSolid())
                return b.getLocation().add(0.5, 0.1, 0.5);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void pegar(EntityPickupItemEvent e) {
        if (!e.getItem().getPersistentDataContainer().has(kMoeda, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        if (!(e.getEntity() instanceof Player p)) return;
        e.getItem().remove();
        int v = ThreadLocalRandom.current().nextInt(100) < 5 ? 100 : ThreadLocalRandom.current().nextInt(5, 21);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
        p.sendActionBar(Extras.txt(v >= 100 ? "§6§l⛃ MOEDA RARA! §e+" + v + " coronas" : "§6⛃ +" + v + " coronas"));
        p.playSound(p.getLocation(), v >= 100 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void funil(InventoryPickupItemEvent e) {
        if (e.getItem().getPersistentDataContainer().has(kMoeda, PersistentDataType.BYTE)) { e.setCancelled(true); e.getItem().remove(); }
    }

    /** Moeda que ficou salva no mundo (servidor desligou no meio) some quando carregar. */
    @EventHandler
    public void carregou(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities())
            if (en instanceof Item && en.getPersistentDataContainer().has(kMoeda, PersistentDataType.BYTE)) en.remove();
    }
}
