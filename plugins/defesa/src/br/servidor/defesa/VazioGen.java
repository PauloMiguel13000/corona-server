package br.servidor.defesa;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

/** Gerador de mundo vazio (so a arena que o plugin constroi). */
final class VazioGen extends ChunkGenerator {
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return spawnLonge(world);
    }

    /** Spawn do mundo longe dos mapas (a protecao de spawn bloqueia cliques de quem nao e OP). */
    static Location spawnLonge(World world) {
        return new Location(world, 150.5, Mapa.Y, 150.5);
    }
}
