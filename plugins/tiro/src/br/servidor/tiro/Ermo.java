package br.servidor.tiro;

import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Mapa do Apocalipse (v2, 15/11, pela foto que o Paulo mandou): campos dourados ondulados (capim seco e trigo), predios
 * gigantes em ruinas TOMBADOS e cobertos de musgo e folhas, com vergalhao no topo, duas estradas rachadas que se cruzam
 * no Abrigo com carros largados e postes, arvores esparsas. Longe do Abrigo o chao muda: Cidade Morta (mais ruinas),
 * Zona Toxica (pantano, pocas verdes) e Marco Zero (terra queimada, basalto). Tudo sai de contas com as coordenadas
 * (sem estado), entao um pedaco do mapa nunca depende do vizinho ja existir.
 */
final class Ermo extends ChunkGenerator {

    static final int BASE = 68, REGIAO = 150;
    private static final int[] ZONA_ATE = {250, 600, 1000};
    private static BlockData FOLHA, TRIGO, CAPIM_B, CAPIM_T, MUSGO_T;

    static void preparar() {
        FOLHA = Bukkit.createBlockData("minecraft:oak_leaves[persistent=true]");
        TRIGO = Bukkit.createBlockData("minecraft:wheat[age=7]");
        CAPIM_B = Bukkit.createBlockData("minecraft:tall_grass[half=lower]");
        CAPIM_T = Bukkit.createBlockData("minecraft:tall_grass[half=upper]");
        MUSGO_T = Bukkit.createBlockData("minecraft:moss_carpet");
    }

    // ------------------------------------------------------------------ contas (sem estado)

    static double h2(long x, long z, long s) {
        long n = x * 374761393L + z * 668265263L + s * 1442695040888963407L;
        n = (n ^ (n >>> 13)) * 1274126177L;
        n ^= n >>> 16;
        return ((n & 0xFFFFFF) / (double) 0x1000000);
    }

    private static double suave(double x, double z, long s) {
        long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = h2(ix, iz, s), b = h2(ix + 1, iz, s), c = h2(ix, iz + 1, s), d = h2(ix + 1, iz + 1, s);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    static double fbm(double x, double z, long s) {
        return (suave(x, z, s) + suave(x * 2, z * 2, s + 7) * 0.5 + suave(x * 4, z * 4, s + 13) * 0.25) / 1.75;
    }

    private static double n3(double x, double y, double z, long s) {
        long iy = (long) Math.floor(y);
        double fy = y - iy;
        fy = fy * fy * (3 - 2 * fy);
        return suave(x, z, s + iy * 31) * (1 - fy) + suave(x, z, s + (iy + 1) * 31) * fy;
    }

    static int zona(int x, int z) {
        double d = Math.hypot(x, z);
        for (int i = 0; i < ZONA_ATE.length; i++) if (d < ZONA_ATE[i]) return i;
        return 3;
    }

    /** Altura do chao (o bloco de cima) em x,z. Plano em volta do Abrigo (0,0). */
    static int altura(int x, int z) {
        double h = BASE + (fbm(x / 110.0, z / 110.0, 11) - 0.5) * 22 + (fbm(x / 28.0, z / 28.0, 23) - 0.5) * 5;
        double d = Math.hypot(x, z);
        double t = Math.max(0, Math.min(1, (d - 30) / 45.0));
        h = BASE + (h - BASE) * t;
        // as duas estradas ficam quase planas
        double e = Math.min(Math.abs(x), Math.abs(z));
        if (e < 14) {
            double hr = BASE + (fbm(x / 160.0, z / 160.0, 31) - 0.5) * 8 * t;
            double k = Math.max(0, Math.min(1, (e - 5) / 9.0));
            h = hr + (h - hr) * k;
        }
        return (int) Math.round(h);
    }

    /** Um predio em ruinas: centro, altura, meia largura/fundo e o quanto tomba por bloco de altura. */
    record Torre(int x, int z, int base, int alt, int a, int b, double sx, double sz, long seed) {}

    static Torre[] torres(int rx, int rz) {
        long s = rx * 73856093L ^ rz * 19349663L;
        int cx = rx * REGIAO + REGIAO / 2, cz = rz * REGIAO + REGIAO / 2;
        int zo = zona(cx, cz);
        int n = zo == 0 ? (h2(rx, rz, 101) < 0.75 ? 1 : 2) : zo == 1 ? 2 : zo == 2 ? (h2(rx, rz, 101) < 0.5 ? 2 : 3) : 3;
        Torre[] t = new Torre[n];
        for (int i = 0; i < n; i++) {
            int x = rx * REGIAO + 24 + (int) (h2(rx, rz, 200 + i) * (REGIAO - 48));
            int z = rz * REGIAO + 24 + (int) (h2(rx, rz, 300 + i) * (REGIAO - 48));
            // nada em cima do Abrigo nem das estradas
            if (Math.hypot(x, z) < 70) {
                x += x >= 0 ? 60 : -60;
                z += z >= 0 ? 60 : -60;
            }
            if (Math.abs(x) < 30) x = x >= 0 ? 34 : -34;
            if (Math.abs(z) < 30) z = z >= 0 ? 34 : -34;
            int alt = 34 + (int) (h2(rx, rz, 400 + i) * (zo == 3 ? 30 : 52));
            int a = 8 + (int) (h2(rx, rz, 500 + i) * 6), b = 8 + (int) (h2(rx, rz, 600 + i) * 6);
            double ang = h2(rx, rz, 700 + i) * Math.PI * 2, tomba = 0.08 + h2(rx, rz, 800 + i) * 0.26;
            t[i] = new Torre(x, z, altura(x, z) - 4, alt, a, b, Math.cos(ang) * tomba, Math.sin(ang) * tomba, s + i * 977L);
        }
        return t;
    }

    // ------------------------------------------------------------------ geracao

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData cd) {
        if (FOLHA == null) preparar();
        int minY = cd.getMinHeight(), maxY = cd.getMaxHeight();
        // predios que podem alcancar este pedaco
        java.util.ArrayList<Torre> perto = new java.util.ArrayList<>();
        int rx0 = Math.floorDiv(chunkX * 16 + 8, REGIAO), rz0 = Math.floorDiv(chunkZ * 16 + 8, REGIAO);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (Torre t : torres(rx0 + dx, rz0 + dz)) {
                    int alc = Math.max(t.a, t.b) + 3 + (int) (Math.max(Math.abs(t.sx), Math.abs(t.sz)) * t.alt) + 9;
                    if (Math.abs(t.x - (chunkX * 16 + 8)) <= alc + 8 && Math.abs(t.z - (chunkZ * 16 + 8)) <= alc + 8) perto.add(t);
                }
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkX * 16 + lx, z = chunkZ * 16 + lz;
                int h = altura(x, z), zo = zona(x, z);
                double r = h2(x, z, 5);
                // entulho em volta do pe dos predios
                boolean entulho = false;
                for (Torre t : perto) {
                    double d = Math.max(Math.abs(x - t.x) - t.a, Math.abs(z - t.z) - t.b);
                    if (d < 7 && fbm(x / 5.0, z / 5.0, t.seed) > 0.35 + d * 0.07) entulho = true;
                }
                // poca toxica (zonas 2 e 3)
                boolean poca = zo >= 2 && fbm(x / 22.0, z / 22.0, 77) > 0.68 && Math.min(Math.abs(x), Math.abs(z)) > 12;
                cd.setBlock(lx, minY, lz, Material.BEDROCK);
                cd.setRegion(lx, minY + 1, lz, lx + 1, 0, lz + 1, Material.DEEPSLATE);
                cd.setRegion(lx, 0, lz, lx + 1, h - 3, lz + 1, Material.STONE);
                cd.setRegion(lx, h - 3, lz, lx + 1, h, lz + 1, zo == 3 ? Material.BLACKSTONE : Material.DIRT);
                boolean estrada = Math.min(Math.abs(x), Math.abs(z)) <= 3 && Math.hypot(x, z) > 13;
                int e = Math.abs(x) <= 3 ? z : x;                                  // ao longo da estrada
                if (poca) {
                    cd.setBlock(lx, h - 1, lz, zo == 3 ? Material.BLACKSTONE : Material.MUD);
                    cd.setBlock(lx, h, lz, r < 0.12 ? Material.SLIME_BLOCK : Material.WATER);
                    if (r > 0.93) cd.setBlock(lx, h + 1, lz, Material.LILY_PAD);
                    continue;
                }
                if (estrada) {
                    boolean meio = (Math.abs(x) == 0 || Math.abs(z) == 0) && Math.floorMod(e, 8) < 4 && Math.min(Math.abs(x), Math.abs(z)) == 0 && Math.max(Math.abs(x), Math.abs(z)) > 4;
                    cd.setBlock(lx, h, lz, meio && r > 0.25 ? Material.YELLOW_TERRACOTTA : r < 0.10 ? Material.COBBLED_DEEPSLATE : r < 0.17 ? Material.COARSE_DIRT
                            : r < 0.22 ? Material.GRAVEL : r < 0.26 ? Material.MOSS_BLOCK : Material.GRAY_CONCRETE);
                    carro(cd, lx, lz, x, z, h, maxY);
                } else if (entulho) {
                    Material m = r < 0.3 ? Material.COBBLESTONE : r < 0.55 ? Material.MOSSY_COBBLESTONE : r < 0.75 ? Material.GRAVEL : r < 0.9 ? Material.STONE_BRICKS : Material.MOSS_BLOCK;
                    cd.setBlock(lx, h, lz, m);
                    if (r > 0.45 && r < 0.62) cd.setBlock(lx, h + 1, lz, r < 0.53 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_COBBLESTONE);
                    if (r > 0.57 && r < 0.62) cd.setBlock(lx, h + 2, lz, Material.STONE_BRICK_WALL);
                } else if (zo == 3) {
                    cd.setBlock(lx, h, lz, r < 0.25 ? Material.BASALT : r < 0.4 ? Material.GRAVEL : r < 0.5 ? Material.COAL_BLOCK : r < 0.56 ? Material.SMOOTH_BASALT : Material.BLACKSTONE);
                    if (r > 0.985) cd.setRegion(lx, h + 1, lz, lx + 1, h + 2 + (int) (h2(x, z, 9) * 4), lz + 1, Material.BASALT);
                } else {
                    boolean campo = zo <= 1 && fbm(x / 34.0, z / 34.0, 51) > 0.56 && Math.hypot(x, z) > 46;
                    boolean trilha = Math.abs(fbm(x / 40.0, z / 40.0, 61) - 0.5) < 0.012 || (Math.min(Math.abs(x), Math.abs(z)) <= 5);
                    if (trilha) {
                        cd.setBlock(lx, h, lz, r < 0.5 ? Material.COARSE_DIRT : r < 0.8 ? Material.DIRT_PATH : Material.GRAVEL);
                        if (r > 0.97) cd.setBlock(lx, h + 1, lz, Material.DEAD_BUSH);
                    } else if (campo) {
                        cd.setBlock(lx, h, lz, Material.FARMLAND);
                        cd.setBlock(lx, h + 1, lz, TRIGO);
                    } else {
                        cd.setBlock(lx, h, lz, zo == 2 ? (r < 0.3 ? Material.MUD : r < 0.45 ? Material.PODZOL : Material.GRASS_BLOCK) : (r < 0.04 ? Material.COARSE_DIRT : Material.GRASS_BLOCK));
                        if (cd.getType(lx, h, lz) == Material.GRASS_BLOCK) {
                            if (r > 0.55 && r < 0.9) cd.setBlock(lx, h + 1, lz, Material.SHORT_GRASS);
                            else if (r >= 0.9 && r < 0.97) {
                                cd.setBlock(lx, h + 1, lz, CAPIM_B);
                                cd.setBlock(lx, h + 2, lz, CAPIM_T);
                            } else if (r >= 0.97 && r < 0.973) cd.setBlock(lx, h + 1, lz, Material.POPPY);
                        }
                    }
                }
                poste(cd, lx, lz, x, z, h);
                arvore(cd, lx, lz, x, z, h, zo, maxY);
                for (Torre t : perto) predio(cd, lx, lz, x, z, t, maxY);
            }
    }

    private void predio(ChunkData cd, int lx, int lz, int x, int z, Torre t, int maxY) {
        int topo = Math.min(maxY - 1, t.base + t.alt + 4);
        for (int y = t.base; y <= topo; y++) {
            int dy = y - t.base;
            double ux = x - t.x - t.sx * dy, uz = z - t.z - t.sz * dy;
            double ax = Math.abs(ux), az = Math.abs(uz);
            if (ax > t.a + 1.5 || az > t.b + 1.5) continue;
            boolean dentro = ax <= t.a && az <= t.b;
            // onde o predio ja caiu: corte irregular no topo e mordidas grandes
            double corte = t.alt * (0.5 + 0.6 * fbm((ux + 40) / 9.0, (uz + 40) / 9.0, t.seed));
            double buraco = n3(x / 7.0, y / 7.0, z / 7.0, t.seed + 3);
            double lim = 0.70 - 0.12 * dy / (double) t.alt;
            boolean caiu = dy > corte || buraco > lim;
            if (!dentro || caiu) {
                // folhagem agarrada na casca e nas bordas quebradas
                if (dy > 2 && dy <= corte + 1.5 && buraco < lim + 0.045 && n3(x / 3.5, y / 3.5, z / 3.5, t.seed + 9) > 0.66 && (dentro || dy < corte)) {
                    if (cd.getType(lx, y, lz) == Material.AIR) cd.setBlock(lx, y, lz, FOLHA);
                }
                continue;
            }
            boolean casca = ax > t.a - 1 || az > t.b - 1;
            int andar = Math.floorMod(dy, 5);
            double r = h2(x * 31L + y, z * 17L - y, t.seed);
            Material m = null;
            if (casca) {
                int ao = (int) Math.floor(ax > t.a - 1 ? uz : ux);
                boolean janela = (andar == 2 || andar == 3) && Math.floorMod(ao, 4) >= 2;
                if (!janela) m = r < 0.5 ? Material.STONE_BRICKS : r < 0.72 ? Material.MOSSY_STONE_BRICKS : r < 0.86 ? Material.CRACKED_STONE_BRICKS : r < 0.94 ? Material.MOSS_BLOCK : Material.COBBLESTONE;
            } else if (andar == 0) {
                if (buraco < 0.6) m = r < 0.6 ? Material.SMOOTH_STONE : r < 0.85 ? Material.ANDESITE : Material.MOSSY_COBBLESTONE;
            } else if (Math.floorMod((int) Math.floor(ux), 6) == 0 && Math.floorMod((int) Math.floor(uz), 6) == 0) {
                m = Material.STONE_BRICKS;                                           // pilares
            } else if (andar == 1 && buraco < 0.6) {
                if (r < 0.07) {
                    cd.setBlock(lx, y, lz, MUSGO_T);
                    continue;
                }
                if (r > 0.985) m = Material.COBWEB;
            }
            if (m != null) cd.setBlock(lx, y, lz, m);
            else if (cd.getType(lx, y, lz) != Material.AIR && y > altura(x, z)) cd.setBlock(lx, y, lz, Material.AIR);
            // vergalhao saindo de onde quebrou
            if (casca && m != null && dy + 1 > corte && r > 0.82) {
                int n = 1 + (int) (r * 100) % 3;
                for (int k = 1; k <= n && y + k < maxY; k++) if (cd.getType(lx, y + k, lz) == Material.AIR) cd.setBlock(lx, y + k, lz, Material.IRON_BARS);
            }
        }
    }

    /** Carros largados nas duas estradas (2 x 4 blocos), de vez em quando queimados. */
    private void carro(ChunkData cd, int lx, int lz, int x, int z, int h, int maxY) {
        boolean ns = Math.abs(x) <= 3;                    // estrada que corre no eixo z
        int ao = ns ? z : x, lado = ns ? x : z;
        if (Math.abs(ao) < 24) return;
        int vaga = Math.floorDiv(ao, 13);
        double r = h2(vaga, ns ? 1 : 2, 909);
        if (r > 0.3) return;
        int faixa = r < 0.15 ? -3 : 1;                    // faixa de -3..-2 ou 1..2
        int k = Math.floorMod(ao, 13) - 4;
        if (lado < faixa || lado > faixa + 1 || k < 0 || k > 3) return;
        Material[] cores = {Material.RED_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.COAL_BLOCK, Material.GREEN_TERRACOTTA};
        Material cor = cores[(int) (h2(vaga, 3, 910) * cores.length)];
        cd.setBlock(lx, h + 1, lz, cor);
        if (k == 1 || k == 2) cd.setBlock(lx, h + 2, lz, cor == Material.COAL_BLOCK ? Material.IRON_BARS : Material.BLACK_STAINED_GLASS);
    }

    /** Postes de luz tortos ao longo das estradas, a cada 26 blocos. */
    private void poste(ChunkData cd, int lx, int lz, int x, int z, int h) {
        boolean ns = Math.abs(x) == 6, lo = Math.abs(z) == 6;
        if (ns == lo) return;
        int ao = ns ? z : x;
        if (Math.floorMod(ao, 26) != 0 || Math.abs(ao) < 20 || h2(ao, ns ? 1 : 2, 919) < 0.25) return;
        int alt = 6 + (int) (h2(ao, 5, 920) * 3);
        cd.setRegion(lx, h + 1, lz, lx + 1, h + 1 + alt, lz + 1, Material.STRIPPED_SPRUCE_LOG);
        cd.setBlock(lx, h + 1 + alt, lz, Material.SPRUCE_SLAB);
    }

    /** Arvores esparsas (uma por quadrado de 18, as vezes): tronco e copa que nao seca. */
    private void arvore(ChunkData cd, int lx, int lz, int x, int z, int h, int zo, int maxY) {
        for (int cx = Math.floorDiv(x - 3, 18); cx <= Math.floorDiv(x + 3, 18); cx++)
            for (int cz = Math.floorDiv(z - 3, 18); cz <= Math.floorDiv(z + 3, 18); cz++) {
                if (h2(cx, cz, 707) > (zo == 0 ? 0.22 : zo == 1 ? 0.3 : zo == 2 ? 0.4 : 0.25)) continue;
                int tx = cx * 18 + 3 + (int) (h2(cx, cz, 708) * 12), tz = cz * 18 + 3 + (int) (h2(cx, cz, 709) * 12);
                if (Math.hypot(tx, tz) < 40 || Math.min(Math.abs(tx), Math.abs(tz)) < 9) continue;
                int dx = x - tx, dz = z - tz;
                if (Math.abs(dx) > 3 || Math.abs(dz) > 3) continue;
                int th = altura(tx, tz), alt = 4 + (int) (h2(cx, cz, 710) * 4);
                boolean seca = zo == 3 || h2(cx, cz, 711) < (zo == 2 ? 0.6 : 0.25);
                if (dx == 0 && dz == 0) {
                    for (int y = th + 1; y <= th + alt && y < maxY; y++) cd.setBlock(lx, y, lz, seca ? Material.STRIPPED_OAK_LOG : Material.OAK_LOG);
                    continue;
                }
                if (seca) {
                    if (Math.abs(dx) + Math.abs(dz) == 1 && h2(x, z, 712) < 0.5) cd.setBlock(lx, th + alt - 1, lz, Material.OAK_FENCE);
                    continue;
                }
                for (int y = th + alt - 2; y <= th + alt + 2 && y < maxY; y++) {
                    double d = Math.sqrt(dx * dx + dz * dz + (y - th - alt) * (y - th - alt) * 1.6);
                    if (d < 2.3 + h2(x * 3L + y, z, 713) * 1.2 && cd.getType(lx, y, lz) == Material.AIR) cd.setBlock(lx, y, lz, FOLHA);
                }
            }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo w, int x, int y, int z) {
                return switch (zona(x, z)) {
                    case 0, 1 -> Biome.SAVANNA;
                    case 2 -> Biome.SWAMP;
                    default -> Biome.BASALT_DELTAS;
                };
            }

            @Override
            public List<Biome> getBiomes(WorldInfo w) {
                return List.of(Biome.SAVANNA, Biome.SWAMP, Biome.BASALT_DELTAS);
            }
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, altura(0, 0) + 1, 0.5);
    }
}
