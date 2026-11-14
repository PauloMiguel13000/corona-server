package br.servidor.extras;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 14/11 (Paulo: "continue melhorando"): de tempos em tempos um METEORO cai no Survival perto de um jogador
 * (60–160 blocos), nunca em terreno protegido. Vira uma rocha de magma com minerios dentro (ferro, ouro,
 * diamante e as vezes esmeralda). As coordenadas sao anunciadas. Admin: "extras meteoro".
 */
final class Meteoro {
    private final Extras pl;
    private long proximo;

    Meteoro(Extras pl) {
        this.pl = pl;
        proximo = System.currentTimeMillis() + 40 * 60_000L;
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (!pl.getConfig().getBoolean("meteoro.ativo", true) || System.currentTimeMillis() < proximo) return;
            proximo = System.currentTimeMillis() + Math.max(30, pl.getConfig().getInt("meteoro.minutos", 120)) * 60_000L;
            cair();
        }, 20L * 60, 20L * 60);
    }

    boolean cair() {
        World w = Bukkit.getWorld("world");
        if (w == null) return false;
        List<Player> ps = new ArrayList<>(w.getPlayers());
        ps.removeIf(p -> p.getGameMode() == org.bukkit.GameMode.SPECTATOR);
        if (ps.isEmpty()) return false;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Player alvo = ps.get(r.nextInt(ps.size()));
        for (int t = 0; t < 25; t++) {
            double ang = r.nextDouble(Math.PI * 2), dist = r.nextDouble(60, 160);
            int x = (int) (alvo.getX() + Math.cos(ang) * dist), z = (int) (alvo.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkGenerated(x >> 4, z >> 4)) continue;
            Block topo = w.getHighestBlockAt(x, z);
            if (topo.isLiquid() || topo.getType() == Material.WATER || topo.getY() < 50) continue;
            Location l = topo.getLocation().add(0.5, 1, 0.5);
            try { if (TerrenoGP.temTerreno(l)) continue; } catch (Throwable ignored) {}
            if (perigoso(l)) continue;
            animar(w, l);
            return true;
        }
        return false;
    }

    /** Longe do spawn principal e de estruturas de jogadores sem terreno (baus, camas, placas por perto). */
    private boolean perigoso(Location l) {
        if (Math.abs(l.getBlockX() - SpawnPrincipal.CX) < 120 && Math.abs(l.getBlockZ() - SpawnPrincipal.CZ) < 120) return true;
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = -3; dy <= 4; dy++)
                for (int dz = -6; dz <= 6; dz++) {
                    Material m = l.getWorld().getBlockAt(l.getBlockX() + dx, l.getBlockY() + dy, l.getBlockZ() + dz).getType();
                    String n = m.name();
                    if (n.endsWith("_BED") || n.endsWith("CHEST") || n.endsWith("_SIGN") || n.endsWith("BARREL") || n.endsWith("_PLANKS") || n.endsWith("_DOOR") || m == Material.FURNACE || m == Material.CRAFTING_TABLE || n.endsWith("GLASS") || n.endsWith("_WOOL")) return true;
                }
        return false;
    }

    private void animar(World w, Location l) {
        Bukkit.broadcast(Extras.txt("§c§l☄ METEORO! §eUm meteoro está caindo no Survival perto de §fX " + l.getBlockX() + " Z " + l.getBlockZ() + "§e! Tem minério raro dentro."));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.6f);
        final int passos = 40;
        for (int i = 0; i <= passos; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                double f = 1.0 - k / (double) passos;
                Location c = l.clone().add(f * 25, f * 60, f * 10);
                if (!c.isChunkLoaded()) return;
                w.spawnParticle(Particle.FLAME, c, 25, 0.6, 0.6, 0.6, 0.05);
                w.spawnParticle(Particle.LARGE_SMOKE, c, 10, 0.5, 0.5, 0.5, 0.02);
                w.spawnParticle(Particle.LAVA, c, 3, 0.3, 0.3, 0.3, 0);
                if (k == passos) impacto(w, l);
            }, i * 2L);
        }
    }

    private void impacto(World w, Location l) {
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 2);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int bx = l.getBlockX(), by = l.getBlockY() - 1, bz = l.getBlockZ();
        // rocha 3x3x3 enterrada pela metade: casca de magma/basalto, minerios no meio
        List<Block> miolo = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
                    if (b.getType() == Material.BEDROCK) continue;
                    boolean borda = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) >= 2;
                    if (borda) b.setType(r.nextInt(3) == 0 ? Material.BLACKSTONE : Material.MAGMA_BLOCK, false);
                    else miolo.add(b);
                }
        for (int[] d : new int[][]{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}}) miolo.add(w.getBlockAt(bx + d[0], by + d[1], bz + d[2]));
        Material[] ores = {Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_IRON_ORE, r.nextInt(4) == 0 ? Material.DEEPSLATE_EMERALD_ORE : Material.DEEPSLATE_IRON_ORE};
        for (int i = 0; i < miolo.size() && i < ores.length; i++) if (miolo.get(i).getType() != Material.BEDROCK) miolo.get(i).setType(ores[i], false);
        w.spawnParticle(Particle.LAVA, l, 40, 2, 1, 2, 0);
        pl.getLogger().info("Meteoro caiu em " + bx + " " + by + " " + bz);
    }
}
