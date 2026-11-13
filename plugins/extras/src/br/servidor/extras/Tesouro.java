package br.servidor.extras;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/**
 * 13/11 (Paulo: "melhore o survival"): Caca ao Tesouro. De tempos em tempos um bau lendario aparece num lugar
 * aleatorio do survival, com um feixe de luz no ceu; o servidor avisa a regiao e quem abrir primeiro leva tudo.
 * /tesouro mostra a distancia e a direcao.
 */
final class Tesouro implements Listener {
    private final Extras pl;
    private Location bau;          // onde esta o bau atual (null = nenhum)
    private long some;             // quando o bau some se ninguem achar
    private boolean aberto;

    Tesouro(Extras pl) {
        this.pl = pl;
        long min = Math.max(10, pl.getConfig().getLong("tesouro.intervalo-minutos", 45));
        Bukkit.getScheduler().runTaskTimer(pl, this::talvezSurgir, 20L * 60 * 8, 20L * 60 * min);
        Bukkit.getScheduler().runTaskTimer(pl, this::brilhar, 40L, 20L);
    }

    private World mundo() { return Bukkit.getWorld(pl.getConfig().getString("tesouro.mundo", "world")); }

    void talvezSurgir() {
        World w = mundo();
        if (w == null || bau != null) return;
        if (w.getPlayers().size() < pl.getConfig().getInt("tesouro.minimo-jogadores", 1)) return;
        surgir(w);
    }

    boolean surgir(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location sp = w.getSpawnLocation();
        int min = pl.getConfig().getInt("tesouro.raio-min", 300), max = pl.getConfig().getInt("tesouro.raio-max", 1200);
        for (int tent = 0; tent < 30; tent++) {
            double ang = r.nextDouble(Math.PI * 2), d = r.nextDouble(min, max);
            int x = sp.getBlockX() + (int) (Math.cos(ang) * d), z = sp.getBlockZ() + (int) (Math.sin(ang) * d);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) continue;
            Block chao = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material m = chao.getType();
            if (chao.isLiquid() || m == Material.ICE || m.name().contains("LEAVES") || chao.getY() < w.getSeaLevel() - 2) continue;
            Location l = chao.getLocation().add(0, 1, 0);
            try { if (TerrenoGP.temTerreno(l)) continue; } catch (Throwable ignored) {}
            Block b = l.getBlock();
            b.setType(Material.CHEST);
            if (b.getState() instanceof Chest c) {
                c.customName(Extras.txt("§6§lBaú do Tesouro"));
                c.update();
                if (b.getState() instanceof Chest c2) {
                    for (ItemStack it : saque(r)) c2.getBlockInventory().addItem(it);
                }
            }
            bau = b.getLocation();
            aberto = false;
            some = System.currentTimeMillis() + pl.getConfig().getLong("tesouro.dura-minutos", 25) * 60_000L;
            int ax = Math.round(x / 50f) * 50, az = Math.round(z / 50f) * 50;
            Bukkit.broadcast(Extras.txt("§6§l✦ CAÇA AO TESOURO ✦ §eUm §6Baú do Tesouro §eapareceu no Survival perto de §fX " + ax + " Z " + az
                    + " §7(±50 blocos)§e! Procure o §ffeixe de luz §eno céu. §7/tesouro §8• §7some em " + pl.getConfig().getLong("tesouro.dura-minutos", 25) + " min"));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
            pl.getLogger().info("Tesouro em " + x + " " + l.getBlockY() + " " + z);
            return true;
        }
        return false;
    }

    private static ItemStack it(Material m, int q) { return new ItemStack(m, q); }

    static List<ItemStack> saque(ThreadLocalRandom r) {
        ItemStack livro = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta em = (EnchantmentStorageMeta) livro.getItemMeta();
        Enchantment[] bons = {Enchantment.MENDING, Enchantment.UNBREAKING, Enchantment.EFFICIENCY, Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.FORTUNE, Enchantment.LOOTING};
        Enchantment e = bons[r.nextInt(bons.length)];
        em.addStoredEnchant(e, e.getMaxLevel(), true);
        livro.setItemMeta(em);
        return List.of(it(Material.DIAMOND, 2 + r.nextInt(5)), it(Material.IRON_INGOT, 8 + r.nextInt(12)), it(Material.GOLD_INGOT, 6 + r.nextInt(10)),
                it(Material.EMERALD, 4 + r.nextInt(8)), it(Material.GOLDEN_APPLE, 1 + r.nextInt(3)), it(Material.EXPERIENCE_BOTTLE, 8 + r.nextInt(16)),
                livro, r.nextInt(6) == 0 ? it(Material.ENCHANTED_GOLDEN_APPLE, 1) : it(Material.NAME_TAG, 1),
                r.nextInt(8) == 0 ? it(Material.NETHERITE_SCRAP, 1 + r.nextInt(2)) : it(Material.LAPIS_LAZULI, 16));
    }

    private void brilhar() {
        if (bau == null) return;
        World w = bau.getWorld();
        if (System.currentTimeMillis() > some || bau.getBlock().getType() != Material.CHEST) {
            if (!aberto) Bukkit.broadcast(Extras.txt("§6✦ §7Ninguém achou o §6Baú do Tesouro§7 a tempo... ele sumiu! O próximo aparece mais tarde."));
            limpar();
            return;
        }
        if (!w.isChunkLoaded(bau.getBlockX() >> 4, bau.getBlockZ() >> 4)) return;
        Location c = bau.clone().add(0.5, 1, 0.5);
        for (int y = 0; y < 60; y += 3) w.spawnParticle(Particle.END_ROD, c.clone().add(0, y, 0), 1, 0.05, 0.4, 0.05, 0);
        w.spawnParticle(Particle.DUST, c, 6, 0.4, 0.3, 0.4, new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.6f));
    }

    private void limpar() {
        if (bau != null && bau.getBlock().getType() == Material.CHEST && bau.getBlock().getState() instanceof Chest c) {
            c.getBlockInventory().clear();
            bau.getBlock().setType(Material.AIR);
        }
        bau = null;
    }

    @EventHandler
    public void abriu(InventoryOpenEvent e) {
        if (bau == null || aberto || !(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getLocation() == null || !e.getInventory().getLocation().getBlock().getLocation().equals(bau)) return;
        aberto = true;
        some = System.currentTimeMillis() + 60_000L;   // depois de achado, o bau some em 1 minuto
        Bukkit.broadcast(Extras.txt("§6§l✦ §e" + p.getName() + " §6achou o Baú do Tesouro§e! §7Fique de olho: logo aparece outro."));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, bau.clone().add(0.5, 1, 0.5), 60, 0.6, 1, 0.6, 0.3);
    }

    @EventHandler(ignoreCancelled = true)
    public void quebrou(BlockBreakEvent e) {
        if (bau != null && e.getBlock().getLocation().equals(bau) && !aberto) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Extras.txt("§6✦ §7Abra o baú para pegar o tesouro!"));
        }
    }

    void comando(Player p, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("agora") && p.hasPermission("extras.admin")) {
            World w = mundo();
            p.sendMessage(w != null && bau == null && surgir(w) ? "§aTesouro criado." : "§cNão deu (já tem um ou sem lugar).");
            return;
        }
        if (bau == null) { p.sendMessage(Extras.txt("§6✦ §7Nenhum Baú do Tesouro agora. Ele aparece de tempos em tempos no Survival — fique ligado no chat!")); return; }
        if (p.getWorld() != bau.getWorld()) { p.sendMessage(Extras.txt("§6✦ §7O Baú do Tesouro está no §aSurvival§7 (mundo normal). Vá com §e/rtp §7ou pelo spawn.")); return; }
        double dx = bau.getX() - p.getX(), dz = bau.getZ() - p.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        String[] dirs = {"Sul", "Sudoeste", "Oeste", "Noroeste", "Norte", "Nordeste", "Leste", "Sudeste"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        String dir = dirs[(int) Math.round(((ang % 360) + 360) % 360 / 45.0) % 8];
        p.sendMessage(Extras.txt("§6✦ Baú do Tesouro: §f" + (dist > 60 ? (dist / 25 * 25) + " blocos" : "bem perto!") + " §7para o §e" + dir
                + " §8• §7some em " + Math.max(0, (some - System.currentTimeMillis()) / 60000) + " min"));
    }
}
