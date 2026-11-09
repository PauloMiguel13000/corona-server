package br.servidor.masmorras;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

/**
 * Gerador do Mundo das Masmorras: um mapa gigante em aneis. Perto do lobby (centro) fica a zona de nivel 1 (campos e
 * bosques suaves, rios); cada anel para fora e um nivel a mais, com terreno mais bravo e biomas diferentes por setor
 * (pantano, neve, deserto, cerejeiras, savana e um lago no nivel 2; selva, floresta escura, badlands, jardim palido,
 * taiga velha e costa de pedra no nivel 3; picos, vulcao, escuridao profunda com ilhas no ceu e espinhos de gelo no
 * nivel 4). Nada e reto: tudo vem de ruido, com cavernas e a decoracao normal do jogo (arvores, flores, minerios) de
 * cada bioma. Nenhum monstro nasce sozinho. O que e estrutura (ruinas, torres, acampamentos, monolitos) e colocado pelo
 * {@link Estruturas}.
 */
final class GeradorMundo extends ChunkGenerator {

    static final int MAR = 62;
    /** Raio do planalto plano do lobby e ate onde ele se mistura com o terreno. */
    static final int LOBBY_RAIO = 95, LOBBY_MISTURA = 70, LOBBY_Y = 80;

    final long semente;
    /** Limites dos aneis (raio): nivel 1 ate zonas[0], nivel 2 ate zonas[1], nivel 3 ate zonas[2], nivel 4 dali ate a borda. */
    final int[] zonas;
    private final SimplexOctaveGenerator morros, detalhe, rios, lagos, cristas, ilhas, mancha, biomaJitter;

    GeradorMundo(long semente, int[] zonas) {
        this.semente = semente;
        this.zonas = zonas;
        morros = oct(semente + 1, 4, 1 / 190.0);
        detalhe = oct(semente + 2, 2, 1 / 38.0);
        rios = oct(semente + 3, 2, 1 / 420.0);
        lagos = oct(semente + 4, 3, 1 / 260.0);
        cristas = oct(semente + 5, 4, 1 / 150.0);
        ilhas = oct(semente + 6, 3, 1 / 120.0);
        mancha = oct(semente + 7, 2, 1 / 24.0);
        biomaJitter = oct(semente + 8, 2, 1 / 90.0);
    }

    private static SimplexOctaveGenerator oct(long s, int o, double escala) {
        SimplexOctaveGenerator g = new SimplexOctaveGenerator(new Random(s), o);
        g.setScale(escala);
        return g;
    }

    private double n(SimplexOctaveGenerator g, double x, double z) {
        return g.noise(x, z, 2.0, 0.5, true);   // -1..1
    }

    // ------------------------------------------------------------------ zonas e setores

    /** Nivel (1 a 4) do ponto, pela distancia do centro. */
    int nivel(double r) {
        if (r < zonas[0]) return 1;
        if (r < zonas[1]) return 2;
        if (r < zonas[2]) return 3;
        return 4;
    }

    /** 0..1: quanto do nivel seguinte ja entra (mistura de 160 blocos em volta de cada limite). */
    private double mistura(double r, int limite) {
        double d = (r - (limite - 80)) / 160.0;
        return d <= 0 ? 0 : d >= 1 ? 1 : d * d * (3 - 2 * d);
    }

    /** Setor (fatia de pizza) do ponto: n fatias, com a borda entre fatias ondulada pelo ruido para nao ficar reta. */
    private int setor(double x, double z, int n, double giro) {
        double ang = Math.atan2(z, x) + giro + n(biomaJitter, x, z) * 0.22;
        double f = (ang / (Math.PI * 2) + 1.0) % 1.0;
        return (int) Math.floor(f * n) % n;
    }

    // ------------------------------------------------------------------ bioma

    /** Bioma do ponto (a decoracao do jogo e o filtro das masmorras usam isso). */
    Biome bioma(int x, int z) {
        double r = Math.hypot(x, z);
        if (r < LOBBY_RAIO + 10) return Biome.MEADOW;
        int nv = nivel(r);
        double jit = n(biomaJitter, x + 777, z - 777);
        double hm = n(morros, x, z);
        if (ehRio(x, z, r) && nv <= 3) return Biome.RIVER;
        switch (nv) {
            case 1 -> {
                if (jit > 0.45) return Biome.FLOWER_FOREST;
                if (jit > 0.15) return Biome.FOREST;
                if (jit < -0.5) return Biome.BIRCH_FOREST;
                if (jit < -0.2) return Biome.TAIGA;
                return hm > 0.35 ? Biome.MEADOW : Biome.PLAINS;
            }
            case 2 -> {
                switch (setor(x, z, 6, 0.3)) {
                    case 0 -> { return jit > 0.3 ? Biome.MANGROVE_SWAMP : Biome.SWAMP; }
                    case 1 -> { return jit > 0.5 ? Biome.ICE_SPIKES : jit > 0 ? Biome.SNOWY_TAIGA : Biome.SNOWY_PLAINS; }
                    case 2 -> { return Biome.DESERT; }
                    case 3 -> { return jit > 0.2 ? Biome.MEADOW : Biome.CHERRY_GROVE; }
                    case 4 -> { return hm > 0.3 ? Biome.SAVANNA_PLATEAU : Biome.SAVANNA; }
                    default -> {
                        double lg = n(lagos, x, z);
                        return lg > 0.15 ? Biome.OCEAN : lg > -0.05 ? Biome.BEACH : Biome.PLAINS;
                    }
                }
            }
            case 3 -> {
                switch (setor(x, z, 6, 1.1)) {
                    case 0 -> { return jit > 0.35 ? Biome.BAMBOO_JUNGLE : Biome.JUNGLE; }
                    case 1 -> { return Biome.DARK_FOREST; }
                    case 2 -> { return jit > 0.4 ? Biome.ERODED_BADLANDS : jit < -0.3 ? Biome.WOODED_BADLANDS : Biome.BADLANDS; }
                    case 3 -> { return Biome.PALE_GARDEN; }
                    case 4 -> { return jit > 0.2 ? Biome.OLD_GROWTH_PINE_TAIGA : Biome.OLD_GROWTH_SPRUCE_TAIGA; }
                    default -> {
                        double lg = n(lagos, x + 5000, z + 5000);
                        return lg > 0.2 ? Biome.OCEAN : lg > -0.05 ? Biome.STONY_SHORE : Biome.WINDSWEPT_GRAVELLY_HILLS;
                    }
                }
            }
            default -> {
                switch (setor(x, z, 4, 2.0)) {
                    case 0 -> {
                        double h = alturaBase(x, z, r);
                        return h > 150 ? Biome.JAGGED_PEAKS : h > 115 ? Biome.FROZEN_PEAKS : h > 95 ? Biome.SNOWY_SLOPES : Biome.GROVE;
                    }
                    case 1 -> { return Biome.BADLANDS; }   // vulcao (a superficie e basalto/magma)
                    case 2 -> { return n(ilhas, x, z) > 0.15 ? Biome.WINDSWEPT_FOREST : Biome.DEEP_DARK; }
                    default -> { return jit > 0.1 ? Biome.ICE_SPIKES : Biome.SNOWY_TAIGA; }
                }
            }
        }
    }

    boolean ehRio(int x, int z, double r) {
        if (r < LOBBY_RAIO + LOBBY_MISTURA || r > zonas[2]) return false;
        double v = Math.abs(n(rios, x, z));
        return v < 0.028;
    }

    /** Vulcao: setor 1 do nivel 4. */
    boolean vulcao(int x, int z) {
        double r = Math.hypot(x, z);
        return nivel(r) == 4 && setor(x, z, 4, 2.0) == 1;
    }

    // ------------------------------------------------------------------ altura

    /** Altura do chao (bloco mais alto solido) no ponto, antes de cavernas. */
    int altura(int x, int z) {
        double r = Math.hypot(x, z);
        double h = alturaBase(x, z, r);
        // rios: calha funda com margens suaves
        if (r >= LOBBY_RAIO + LOBBY_MISTURA && r <= zonas[2] && h < 95) {
            double v = Math.abs(n(rios, x, z));
            if (v < 0.07) {
                double t = v / 0.07;                       // 0 no meio do rio, 1 na margem
                double calha = MAR - 5 + t * t * 9;        // 57 no meio ate ~66 na margem
                h = Math.min(h, Math.max(calha, h - (1 - t) * 14));
            }
        }
        // planalto do lobby: plano, e se mistura com o terreno em volta
        if (r < LOBBY_RAIO + LOBBY_MISTURA) {
            double t = r <= LOBBY_RAIO ? 0 : (r - LOBBY_RAIO) / LOBBY_MISTURA;
            t = t * t * (3 - 2 * t);
            h = LOBBY_Y * (1 - t) + h * t;
        }
        return (int) Math.round(h);
    }

    private double alturaBase(double x, double z, double r) {
        int nv = nivel(r);
        double h1 = z1(x, z), h;
        if (nv == 1) {
            h = h1;
            double m = mistura(r, zonas[0]);
            if (m > 0) h = h1 * (1 - m) + z2(x, z) * m;
        } else if (nv == 2) {
            h = z2(x, z);
            double m0 = 1 - mistura(r, zonas[0]), m1 = mistura(r, zonas[1]);
            if (m0 > 0) h = h * (1 - m0) + h1 * m0;
            if (m1 > 0) h = h * (1 - m1) + z3(x, z) * m1;
        } else if (nv == 3) {
            h = z3(x, z);
            double m0 = 1 - mistura(r, zonas[1]), m1 = mistura(r, zonas[2]);
            if (m0 > 0) h = h * (1 - m0) + z2(x, z) * m0;
            if (m1 > 0) h = h * (1 - m1) + z4(x, z) * m1;
        } else {
            h = z4(x, z);
            double m0 = 1 - mistura(r, zonas[2]);
            if (m0 > 0) h = h * (1 - m0) + z3(x, z) * m0;
        }
        return h + n(detalhe, x, z) * 1.6;
    }

    /** Nivel 1: campos e bosques suaves com morros baixos. */
    private double z1(double x, double z) {
        return 68 + n(morros, x, z) * 9 + Math.max(0, n(cristas, x, z)) * 7;
    }

    /** Nivel 2: por setor. */
    private double z2(double x, double z) {
        double m = n(morros, x, z), c = n(cristas, x, z);
        switch (setor(x, z, 6, 0.3)) {
            case 0 -> { return 63.5 + m * 3 + (m < -0.15 ? -4 : 0); }                     // pantano: baixo, com pocas
            case 1 -> { return 72 + m * 16 + Math.max(0, c) * 10; }                       // neve: morros
            case 2 -> { return 68 + Math.abs(c) * 11 + m * 4; }                            // deserto: dunas (ruido em crista)
            case 3 -> { return 74 + m * 20 + Math.max(0, c) * 12; }                       // cerejeiras: colinas altas
            case 4 -> { return 70 + Math.floor((m + 1) * 2.2) * 5 + c * 2; }               // savana: platos em degraus
            default -> {                                                                   // lago: ilhas e praias
                double lg = n(lagos, x, z);
                return lg > 0.15 ? 50 - lg * 10 : 64 + (0.15 - lg) * 30 + Math.max(0, m) * 5;
            }
        }
    }

    /** Nivel 3: por setor, mais bravo. */
    private double z3(double x, double z) {
        double m = n(morros, x, z), c = n(cristas, x, z);
        switch (setor(x, z, 6, 1.1)) {
            case 0 -> { return 72 + m * 24 + Math.max(0, c) * 16; }                        // selva: morros altos
            case 1 -> { return 70 + m * 11 + c * 4; }                                      // floresta escura
            case 2 -> { return 74 + Math.floor((Math.abs(c) * 1.6 + (m + 1) * 1.2) * 2) * 6; } // badlands: mesas em degraus
            case 3 -> { return 69 + m * 9 + Math.max(0, c) * 6; }                          // jardim palido: ondulado
            case 4 -> { return 76 + m * 20 + Math.max(0, c) * 14; }                        // taiga velha
            default -> {                                                                   // costa de pedra e lago
                double lg = n(lagos, x + 5000, z + 5000);
                return lg > 0.2 ? 48 - lg * 12 : 66 + (0.2 - lg) * 40 + Math.abs(c) * 10;
            }
        }
    }

    /** Nivel 4: picos, vulcao, escuridao profunda com ilhas no ceu, espinhos de gelo. */
    private double z4(double x, double z) {
        double m = n(morros, x, z), c = n(cristas, x, z);
        double crista = 1 - Math.abs(c);   // 1 nas cristas
        switch (setor(x, z, 4, 2.0)) {
            case 0 -> { return 92 + m * 25 + crista * crista * 75; }                       // picos
            case 1 -> { return 74 + m * 10 + crista * crista * 38 - (m < -0.35 ? 12 : 0); } // vulcao: cones e lagos de lava
            case 2 -> { return 67 + m * 14 + Math.max(0, c) * 8; }                         // vales escuros (as ilhas ficam no ceu)
            default -> { return 72 + m * 14 + Math.max(0, c) * 8; }                        // gelo
        }
    }

    /** Ilhas flutuantes do setor escuro do nivel 4: devolve [base, topo] ou null. */
    int[] ilhaNoCeu(int x, int z) {
        double r = Math.hypot(x, z);
        if (nivel(r) != 4 || setor(x, z, 4, 2.0) != 2) return null;
        double v = n(ilhas, x, z);
        if (v < 0.42) return null;
        double t = (v - 0.42) / 0.58;   // 0 na borda da ilha, 1 no meio
        int topo = 150 + (int) Math.round(n(detalhe, x, z) * 3 + t * 10);
        int base = topo - 3 - (int) Math.round(t * t * 24);
        return new int[] {base, topo};
    }

    // ------------------------------------------------------------------ blocos

    private static final BlockData STONE = Material.STONE.createBlockData(), DEEPSLATE = Material.DEEPSLATE.createBlockData(),
            WATER = Material.WATER.createBlockData(), LAVA = Material.LAVA.createBlockData(), BEDROCK = Material.BEDROCK.createBlockData();

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData chunk) {
        int min = info.getMinHeight();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                int h = altura(x, z);
                boolean vulc = vulcao(x, z);
                for (int y = min; y <= h; y++) chunk.setBlock(lx, y, lz, y < 0 ? DEEPSLATE : STONE);
                if (h < MAR) {
                    BlockData liq = vulc ? LAVA : WATER;
                    for (int y = h + 1; y <= MAR; y++) chunk.setBlock(lx, y, lz, liq);
                }
                int[] ilha = ilhaNoCeu(x, z);
                if (ilha != null) for (int y = ilha[0]; y <= ilha[1]; y++) chunk.setBlock(lx, y, lz, STONE);
            }
    }

    @Override
    public void generateSurface(WorldInfo info, Random random, int cx, int cz, ChunkData chunk) {
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                int h = altura(x, z);
                Biome b = bioma(x, z);
                superficie(chunk, lx, h, lz, x, z, b, h, random);
                int[] ilha = ilhaNoCeu(x, z);
                if (ilha != null) {
                    chunk.setBlock(lx, ilha[1], lz, Material.GRASS_BLOCK);
                    for (int y = ilha[1] - 1; y >= ilha[1] - 3 && y > ilha[0]; y--) chunk.setBlock(lx, y, lz, Material.DIRT);
                    if (n(mancha, x, z) > 0.5 && ilha[1] - ilha[0] > 6) chunk.setBlock(lx, ilha[0], lz, Material.AMETHYST_BLOCK);
                }
            }
    }

    private void superficie(ChunkData c, int lx, int h, int lz, int x, int z, Biome b, int topo, Random rnd) {
        double mc = n(mancha, x, z);
        String nome = b.getKey().getKey();
        boolean fundo = h < MAR;   // embaixo da agua ou lava
        if (vulcao(x, z)) {
            Material t = mc > 0.55 ? Material.MAGMA_BLOCK : mc < -0.6 ? Material.BLACKSTONE : Material.BASALT;
            c.setBlock(lx, h, lz, t);
            for (int y = h - 1; y >= h - 3; y--) c.setBlock(lx, y, lz, mc > 0.2 ? Material.BLACKSTONE : Material.BASALT);
            return;
        }
        if (fundo) {
            Material t = mc > 0.4 ? Material.GRAVEL : mc < -0.5 ? Material.CLAY : Material.SAND;
            if (nome.contains("swamp")) t = Material.MUD;
            c.setBlock(lx, h, lz, t);
            c.setBlock(lx, h - 1, lz, t == Material.CLAY ? Material.CLAY : Material.SAND);
            return;
        }
        Material topoB, baixo = Material.DIRT;
        int camadas = 3;
        switch (nome) {
            case "desert" -> { topoB = Material.SAND; baixo = Material.SANDSTONE; camadas = 4; }
            case "beach" -> { topoB = Material.SAND; baixo = Material.SAND; }
            case "river" -> { topoB = h <= MAR + 1 ? Material.SAND : Material.GRASS_BLOCK; }
            case "stony_shore", "windswept_gravelly_hills" -> { topoB = mc > 0 ? Material.STONE : Material.GRAVEL; baixo = Material.STONE; }
            case "badlands", "eroded_badlands", "wooded_badlands" -> {
                topoB = h > 90 || nome.equals("wooded_badlands") && mc > 0 ? Material.RED_SAND : terracota(h);
                for (int y = h - 1; y >= h - 10; y--) c.setBlock(lx, y, lz, terracota(y));
                c.setBlock(lx, h, lz, topoB);
                return;
            }
            case "mangrove_swamp" -> { topoB = Material.MUD; baixo = Material.MUD; }
            case "swamp" -> { topoB = mc > 0.35 ? Material.MUD : Material.GRASS_BLOCK; }
            case "snowy_plains", "snowy_taiga", "ice_spikes", "snowy_slopes", "grove" -> { topoB = Material.GRASS_BLOCK; }
            case "frozen_peaks" -> { topoB = mc > 0.2 ? Material.PACKED_ICE : Material.SNOW_BLOCK; baixo = Material.STONE; camadas = 2; }
            case "jagged_peaks" -> { topoB = h > 165 ? Material.SNOW_BLOCK : Material.STONE; baixo = Material.STONE; camadas = 1; }
            case "deep_dark" -> { topoB = mc > 0.3 ? Material.SCULK : Material.DEEPSLATE; baixo = Material.DEEPSLATE; camadas = 4; }
            case "savanna", "savanna_plateau" -> { topoB = mc > 0.6 ? Material.COARSE_DIRT : Material.GRASS_BLOCK; }
            case "old_growth_pine_taiga", "old_growth_spruce_taiga" -> { topoB = mc > 0.25 ? Material.PODZOL : mc < -0.6 ? Material.COARSE_DIRT : Material.GRASS_BLOCK; }
            case "pale_garden" -> { topoB = mc > 0.45 ? Material.PALE_MOSS_BLOCK : Material.GRASS_BLOCK; }
            case "meadow" -> { topoB = Material.GRASS_BLOCK; }
            default -> topoB = Material.GRASS_BLOCK;
        }
        c.setBlock(lx, h, lz, topoB);
        for (int y = h - 1; y >= h - camadas; y--) c.setBlock(lx, y, lz, baixo);
        if ((nome.startsWith("snowy") || nome.equals("ice_spikes") || nome.equals("grove") || nome.equals("frozen_peaks") || nome.equals("jagged_peaks") && h > 165) && h >= MAR)
            c.setBlock(lx, h + 1, lz, Material.SNOW);
        // picos: pedra nua com manchas de calcita/neve
        if (nome.equals("jagged_peaks") && h <= 165 && mc > 0.5) c.setBlock(lx, h, lz, Material.CALCITE);
    }

    private static Material terracota(int y) {
        return switch (Math.floorMod(y, 11)) {
            case 0, 6 -> Material.ORANGE_TERRACOTTA;
            case 1 -> Material.RED_TERRACOTTA;
            case 2, 7 -> Material.TERRACOTTA;
            case 3 -> Material.YELLOW_TERRACOTTA;
            case 4, 9 -> Material.WHITE_TERRACOTTA;
            case 5 -> Material.BROWN_TERRACOTTA;
            case 8 -> Material.LIGHT_GRAY_TERRACOTTA;
            default -> Material.ORANGE_TERRACOTTA;
        };
    }

    @Override
    public void generateBedrock(WorldInfo info, Random random, int cx, int cz, ChunkData chunk) {
        int min = info.getMinHeight();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                chunk.setBlock(lx, min, lz, BEDROCK);
                for (int y = min + 1; y < min + 4; y++) if (random.nextInt(y - min + 1) == 0) chunk.setBlock(lx, y, lz, BEDROCK);
            }
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return true; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return bioma(x, z);
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.MEADOW, Biome.PLAINS, Biome.FOREST, Biome.FLOWER_FOREST, Biome.BIRCH_FOREST, Biome.TAIGA, Biome.RIVER,
                        Biome.SWAMP, Biome.MANGROVE_SWAMP, Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA, Biome.ICE_SPIKES, Biome.DESERT, Biome.CHERRY_GROVE,
                        Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.OCEAN, Biome.BEACH, Biome.JUNGLE, Biome.BAMBOO_JUNGLE, Biome.DARK_FOREST,
                        Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS, Biome.PALE_GARDEN, Biome.OLD_GROWTH_PINE_TAIGA,
                        Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.STONY_SHORE, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS,
                        Biome.SNOWY_SLOPES, Biome.GROVE, Biome.WINDSWEPT_FOREST, Biome.DEEP_DARK);
            }
        };
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new Estruturas(this));
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, LOBBY_Y + 1, 0.5, 180f, 0f);
    }
}
