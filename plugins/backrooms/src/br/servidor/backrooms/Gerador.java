package br.servidor.backrooms;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** Gera as fases direto no carregamento do chunk (nao precisa construir nada, e as fases podem ser enormes). */
final class Gerador extends ChunkGenerator {
    private final Mapa mapa;

    Gerador(Mapa mapa) {
        this.mapa = mapa;
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public void generateNoise(WorldInfo info, Random r, int cx, int cz, ChunkData d) {
        int bx = cx << 4, bz = cz << 4;
        for (Nivel n : mapa.niveis) {
            if (bx + 15 < n.x0 || bx > n.x0 + n.largX || bz + 15 < n.z0 || bz > n.z0 + n.largZ) continue;
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++) {
                    int x = bx + lx, z = bz + lz;
                    if (n.contem(x, z)) n.coluna(d, lx, lz, x, z);
                }
        }
        if (bx + 15 >= Espera.X1 && bx <= Espera.X2 && bz + 15 >= Espera.Z1 && bz <= Espera.Z2)
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++) {
                    int x = bx + lx, z = bz + lz;
                    if (Espera.contem(x, z)) Espera.coluna(d, lx, lz, x, z);
                }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                Nivel n = mapa.em(x, z);
                return n == null ? Biome.PALE_GARDEN : n.bioma();
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.PALE_GARDEN, Biome.DEEP_DARK);
            }
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return Espera.spawn(world);
    }
}
