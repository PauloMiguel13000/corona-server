package br.servidor.backrooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Lanterna do Nivel 6 (Luzes Apagadas), a mesma ideia da do Slender: o Minecraft nao tem luz que anda com o
 * jogador, entao a luz e um bloco de luz invisivel mandado so para os jogadores (o mundo de verdade nao muda).
 * Todo mundo no mundo das Backrooms ve a lanterna dos outros.
 */
final class Lanterna {

    record Pos(int x, int y, int z) {}

    private final BlockData[] luzes = new BlockData[16];
    private Map<Pos, Integer> atual = new HashMap<>();
    private final Set<UUID> vendo = new HashSet<>();
    private long voltas;

    private BlockData luz(int nivel) {
        if (luzes[nivel] == null) {
            Light l = (Light) Material.LIGHT.createBlockData();
            l.setLevel(nivel);
            luzes[nivel] = l;
        }
        return luzes[nivel];
    }

    /** A cada 2 ticks. ligada = quem esta com a lanterna acesa agora. */
    void tick(World mundo, List<Player> jogadores, Predicate<Player> ligada, double alcance) {
        voltas++;
        Map<Pos, Integer> novo = new HashMap<>();
        for (Player p : jogadores) {
            if (!ligada.test(p)) continue;
            Location olho = p.getEyeLocation();
            Vector dir = olho.getDirection();
            RayTraceResult r = mundo.rayTraceBlocks(olho, dir, alcance, FluidCollisionMode.NEVER, true);
            double dist = r == null ? alcance : r.getHitPosition().distance(olho.toVector());
            Block longe = livreAntes(olho, dir, dist - 0.5);
            if (longe != null) novo.merge(pos(longe), 13, Math::max);
            Block meio = livreAntes(olho, dir, Math.min(dist - 0.5, dist * 0.5));
            if (meio != null) novo.merge(pos(meio), 10, Math::max);
            Block perto = livreAntes(olho, dir, Math.min(1.6, dist - 0.5));
            if (perto != null) novo.merge(pos(perto), 7, Math::max);
        }
        enviar(mundo, novo);
    }

    private static Pos pos(Block b) {
        return new Pos(b.getX(), b.getY(), b.getZ());
    }

    private static Block livreAntes(Location olho, Vector dir, double d) {
        for (double t = d; t >= 0.3; t -= 0.5) {
            Block b = olho.clone().add(dir.clone().multiply(t)).getBlock();
            if (b.getType().isAir()) return b;
        }
        Block b = olho.getBlock();
        return b.getType().isAir() ? b : null;
    }

    private void enviar(World mundo, Map<Pos, Integer> novo) {
        List<Player> quem = new ArrayList<>(mundo.getPlayers());
        boolean tudo = voltas % 20 == 0;
        Set<UUID> agora = new HashSet<>();
        for (Player v : quem) {
            agora.add(v.getUniqueId());
            boolean chegou = !vendo.contains(v.getUniqueId());
            for (Pos p : atual.keySet()) if (!novo.containsKey(p)) devolver(v, mundo, p);
            for (Map.Entry<Pos, Integer> e : novo.entrySet()) {
                Integer antes = atual.get(e.getKey());
                if (tudo || chegou || antes == null || !antes.equals(e.getValue()))
                    v.sendBlockChange(new Location(mundo, e.getKey().x(), e.getKey().y(), e.getKey().z()), luz(e.getValue()));
            }
        }
        vendo.clear();
        vendo.addAll(agora);
        atual = novo;
    }

    private static void devolver(Player v, World w, Pos p) {
        v.sendBlockChange(new Location(w, p.x(), p.y(), p.z()), w.getBlockAt(p.x(), p.y(), p.z()).getBlockData());
    }

    /** Apaga tudo (troca de fase, fim de partida). */
    void apagar(World mundo) {
        if (mundo == null || atual.isEmpty()) return;
        for (Player v : mundo.getPlayers())
            for (Pos p : atual.keySet()) devolver(v, mundo, p);
        atual = new HashMap<>();
        vendo.clear();
    }
}
