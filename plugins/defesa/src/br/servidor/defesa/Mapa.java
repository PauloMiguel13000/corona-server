package br.servidor.defesa;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;

/**
 * Um mapa da Defesa da Torre: ilha flutuante no mundo "defesa", com caminho, Nucleo, lobby e loja.
 * As coordenadas sao relativas a origem (ox, oz) do mapa. Para criar um mapa novo, adicione em TODOS.
 */
final class Mapa {

    enum Tema { CAMPOS, VULCAO, GELO, DRAGAO }

    static final String MUNDO = "defesa";
    /** Altura onde se pisa (o chao fica em Y-1). */
    static final int Y = 100;

    final String id;
    final String nome;
    final String cor;
    final Material icone;
    final String desc1;
    final String desc2;
    final Tema tema;
    final int ox;
    final int oz;
    final int minX;
    final int maxX;
    final int minZ;
    final int maxZ;
    final int[][] caminho;
    final double nucleoX;
    final double nucleoZ;
    final double lobbyX;
    final double lobbyZ;
    final double lojaX;
    final double lojaZ;
    /** Bloco onde pode colocar torre, bloco do caminho. */
    final Material chao;
    final Material trilha;
    /** Vida dos monstros neste mapa (mapa mais facil de defender = monstros mais fortes). */
    final double vidaMult;
    /** Troca de monstros pelo tema (ex.: zumbi vira piglin zumbi no vulcao). */
    final Map<EntityType, EntityType> trocas = new EnumMap<>(EntityType.class);
    final Map<EntityType, String> nomesTroca = new EnumMap<>(EntityType.class);

    private Mapa(String id, String nome, String cor, Material icone, String desc1, String desc2, Tema tema,
                 int ox, int oz, int minX, int maxX, int minZ, int maxZ, int[][] caminho,
                 double nucleoX, double nucleoZ, double lobbyX, double lobbyZ, double lojaX, double lojaZ,
                 Material chao, Material trilha, double vidaMult) {
        this.id = id;
        this.nome = nome;
        this.cor = cor;
        this.icone = icone;
        this.desc1 = desc1;
        this.desc2 = desc2;
        this.tema = tema;
        this.ox = ox;
        this.oz = oz;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.caminho = caminho;
        this.nucleoX = nucleoX;
        this.nucleoZ = nucleoZ;
        this.lobbyX = lobbyX;
        this.lobbyZ = lobbyZ;
        this.lojaX = lojaX;
        this.lojaZ = lojaZ;
        this.chao = chao;
        this.trilha = trilha;
        this.vidaMult = vidaMult;
    }

    // ------------------------------------------------------------------ os mapas

    static final Mapa CAMPOS = new Mapa("campos", "Campos Verdes", "§a", Material.GRASS_BLOCK,
            "O mapa original: caminho em zigue-zague", "numa ilha de grama.",
            Tema.CAMPOS, 0, 0, -22, 22, -22, 22,
            new int[][] {{-18, -18}, {18, -18}, {18, -8}, {-18, -8}, {-18, 2}, {18, 2}, {18, 12}, {0, 12}, {0, 17}},
            0.5, 19.5, 8.5, 18.5, -7.5, 18.5,
            Material.GRASS_BLOCK, Material.DIRT_PATH, 1.0);

    static final Mapa VULCAO = new Mapa("vulcao", "Vulcão", "§c", Material.MAGMA_BLOCK,
            "Caminho em espiral até o Núcleo no centro,", "cercado de lava. Monstros 25% mais fortes.",
            Tema.VULCAO, 300, 0, -22, 22, -22, 30,
            new int[][] {{-18, -18}, {18, -18}, {18, 18}, {-18, 18}, {-18, -10}, {10, -10}, {10, 10}, {-10, 10}, {-10, -2}, {-4, -2}},
            0.5, -1.5, 8.5, 26.5, -7.5, 26.5,
            Material.CRIMSON_NYLIUM, Material.POLISHED_BLACKSTONE, 1.25);

    static {
        VULCAO.trocas.put(EntityType.ZOMBIE, EntityType.ZOMBIFIED_PIGLIN);
        VULCAO.nomesTroca.put(EntityType.ZOMBIE, "Piglin Zumbi");
    }

    static final Mapa GELEIRA = new Mapa("geleira", "Geleira", "§b", Material.BLUE_ICE,
            "Caminho em serpente pela neve,", "lago congelado em volta. Monstros 15% mais fortes.",
            Tema.GELO, 600, 0, -22, 22, -22, 30,
            new int[][] {{-18, -18}, {-18, 14}, {-8, 14}, {-8, -14}, {2, -14}, {2, 14}, {12, 14}, {12, -14}, {18, -14}, {18, 20}},
            18.5, 23.5, 6.5, 26.5, -8.5, 26.5,
            Material.SNOW_BLOCK, Material.CALCITE, 1.15);

    /** 13/11 (Paulo): fase com um dragao que passa por cima a cada 5 minutos (e queima os monstros no caminho). */
    static final Mapa NINHO = new Mapa("ninho", "Ninho do Dragão", "§5", Material.DRAGON_HEAD,
            "Ilha do Fim em zigue-zague. A cada 5 minutos", "o Dragão passa por cima e queima os monstros!",
            Tema.DRAGAO, 900, 0, -22, 22, -22, 30,
            new int[][] {{-18, -20}, {18, -20}, {18, -10}, {-10, -10}, {-10, 0}, {18, 0}, {18, 10}, {-18, 10}, {-18, 18}, {-8, 18}},
            -5.5, 18.5, 10.5, 26.5, -10.5, 26.5,
            Material.END_STONE, Material.PURPUR_BLOCK, 1.35);

    static {
        GELEIRA.trocas.put(EntityType.SKELETON, EntityType.STRAY);
        GELEIRA.nomesTroca.put(EntityType.SKELETON, "Errante");
        NINHO.trocas.put(EntityType.SPIDER, EntityType.CAVE_SPIDER);
        NINHO.nomesTroca.put(EntityType.SPIDER, "Aranha do Fim");
    }

    static final List<Mapa> TODOS = List.of(CAMPOS, VULCAO, GELEIRA, NINHO);

    static Mapa porId(String s) {
        if (s == null) return null;
        for (Mapa m : TODOS) {
            if (m.id.equalsIgnoreCase(s)) return m;
        }
        return null;
    }

    // ------------------------------------------------------------------ geometria

    int absX(int x) {
        return ox + x;
    }

    int absZ(int z) {
        return oz + z;
    }

    Location ponto(World w, int i) {
        return new Location(w, ox + caminho[i][0] + 0.5, Y, oz + caminho[i][1] + 0.5);
    }

    Location lobby(World w) {
        Location l = new Location(w, ox + lobbyX, Y, oz + lobbyZ);
        l.setYaw(180f);
        return l;
    }

    Location loja(World w) {
        Location l = new Location(w, ox + lojaX, Y, oz + lojaZ);
        l.setYaw(180f);
        return l;
    }

    Location nucleo(World w) {
        return new Location(w, ox + nucleoX, Y, oz + nucleoZ);
    }

    Location centro(World w) {
        return new Location(w, ox + (minX + maxX) / 2.0, Y, oz + (minZ + maxZ) / 2.0);
    }

    /** x, z relativos ao mapa. */
    boolean noCaminho(int x, int z) {
        for (int i = 0; i + 1 < caminho.length; i++) {
            int x1 = Math.min(caminho[i][0], caminho[i + 1][0]) - 1;
            int x2 = Math.max(caminho[i][0], caminho[i + 1][0]) + 1;
            int z1 = Math.min(caminho[i][1], caminho[i + 1][1]) - 1;
            int z2 = Math.max(caminho[i][1], caminho[i + 1][1]) + 1;
            if (x >= x1 && x <= x2 && z >= z1 && z <= z2) return true;
        }
        return false;
    }

    /** x, z relativos ao mapa: dentro da cerca. */
    boolean dentro(int x, int z) {
        return x > minX && x < maxX && z > minZ && z < maxZ;
    }

    /** Local absoluto esta na regiao deste mapa (com folga). */
    boolean contem(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(MUNDO)) return false;
        double x = l.getX() - ox;
        double z = l.getZ() - oz;
        return x >= minX - 12 && x <= maxX + 12 && z >= minZ - 12 && z <= maxZ + 12;
    }

    int chunkMinX() {
        return (ox + minX - 3) >> 4;
    }

    int chunkMaxX() {
        return (ox + maxX + 3) >> 4;
    }

    int chunkMinZ() {
        return (oz + minZ - 3) >> 4;
    }

    int chunkMaxZ() {
        return (oz + maxZ + 3) >> 4;
    }

    /** O mapa ja foi construido? (confere um bloco marcante do Nucleo) */
    boolean construido(World w) {
        int cx = (int) Math.floor(ox + nucleoX);
        int cz = (int) Math.floor(oz + nucleoZ);
        Material m = w.getBlockAt(cx, Y - 1, cz).getType();
        return switch (tema) {
            case VULCAO -> m == Material.GILDED_BLACKSTONE;
            case GELO -> m == Material.DIAMOND_BLOCK;
            case DRAGAO -> m == Material.CRYING_OBSIDIAN && w.getBlockAt(cx, Y - 2, cz).getType() == Material.OBSIDIAN;
            default -> m == Material.OBSIDIAN;
        };
    }

    // ------------------------------------------------------------------ construcao

    private void por(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(ox + x, y, oz + z);
        if (b.getType() != m) b.setType(m, false);
    }

    void construir(World w) {
        switch (tema) {
            case CAMPOS -> construirCampos(w);
            case VULCAO -> construirVulcao(w);
            case GELO -> construirGeleira(w);
            case DRAGAO -> construirNinho(w);
        }
    }

    private void construirCampos(World w) {
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                for (int y = Y; y <= Y + 8; y++) por(w, x, y, z, Material.AIR);
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                por(w, x, Y - 1, z, noCaminho(x, z) ? trilha : chao);
                por(w, x, Y - 2, z, Material.DIRT);
                por(w, x, Y - 3, z, Material.STONE);
                boolean borda = x == minX || x == maxX || z == minZ || z == maxZ;
                if (!borda && Math.abs(x) < maxX - 3 && Math.abs(z) < maxZ - 3) por(w, x, Y - 4, z, Material.STONE);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x == minX || x == maxX || z == minZ || z == maxZ) w.getBlockAt(ox + x, Y, oz + z).setType(Material.DARK_OAK_FENCE, true);
            }
        }
        portal(w, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.SOUL_LANTERN);
        baseNucleo(w, Material.OBSIDIAN, Material.POLISHED_BLACKSTONE_BRICKS, Material.LANTERN);
        pads(w, Material.SPRUCE_PLANKS, Material.SMOOTH_STONE);
    }

    private void construirVulcao(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // limpa o ar e monta a ilha (com fosso de lava em volta)
        for (int x = minX - 3; x <= maxX + 3; x++) {
            for (int z = minZ - 3; z <= maxZ + 3; z++) {
                for (int y = Y; y <= Y + 8; y++) por(w, x, y, z, Material.AIR);
                boolean ilha = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                boolean fosso = !ilha && x >= minX - 1 && x <= maxX + 1 && z >= minZ - 1 && z <= maxZ + 1;
                boolean borda = !ilha && !fosso && x >= minX - 2 && x <= maxX + 2 && z >= minZ - 2 && z <= maxZ + 2;
                if (ilha) {
                    por(w, x, Y - 1, z, noCaminho(x, z) ? trilha : chao);
                    por(w, x, Y - 2, z, Material.NETHERRACK);
                    por(w, x, Y - 3, z, Material.BLACKSTONE);
                    if (Math.abs(x) < maxX - 3 && z > minZ + 3 && z < maxZ - 3) por(w, x, Y - 4, z, Material.BASALT);
                    if (Math.abs(x) < maxX - 8 && z > minZ + 8 && z < maxZ - 8) por(w, x, Y - 5, z, Material.MAGMA_BLOCK);
                } else if (fosso) {
                    por(w, x, Y - 2, z, Material.BLACKSTONE);
                    por(w, x, Y - 1, z, Material.LAVA);
                } else if (borda) {
                    por(w, x, Y - 2, z, Material.BLACKSTONE);
                    por(w, x, Y - 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        // cerca de tijolo do nether com lanternas de alma
        int i = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
                w.getBlockAt(ox + x, Y, oz + z).setType(Material.NETHER_BRICK_FENCE, true);
                if (i++ % 6 == 0) por(w, x, Y + 1, z, Material.SOUL_LANTERN);
            }
        }
        // pilares de basalto nos cantos
        int[][] cantos = {{minX + 2, minZ + 2}, {maxX - 2, minZ + 2}, {minX + 2, maxZ - 2}, {maxX - 2, maxZ - 2}};
        for (int[] c : cantos) {
            if (noCaminho(c[0], c[1])) continue;
            if (Math.abs(c[0] - caminho[0][0]) <= 3 && Math.abs(c[1] - caminho[0][1]) <= 3) continue; // do lado do portal
            for (int y = Y; y < Y + 4; y++) por(w, c[0], y, c[1], Material.BASALT);
            por(w, c[0], Y + 4, c[1], Material.SHROOMLIGHT);
        }
        // raizes e fungos do nether espalhados (decoracao, nao atrapalha)
        for (int n = 0; n < 70; n++) {
            int x = r.nextInt(minX + 2, maxX - 1);
            int z = r.nextInt(minZ + 2, maxZ - 1);
            if (noCaminho(x, z) || perto(x, z, nucleoX, nucleoZ, 4) || perto(x, z, lobbyX, lobbyZ, 3) || perto(x, z, lojaX, lojaZ, 3)) continue;
            Block b = w.getBlockAt(ox + x, Y, oz + z);
            if (b.getType() != Material.AIR) continue;
            por(w, x, Y, z, n % 3 == 0 ? Material.CRIMSON_FUNGUS : Material.CRIMSON_ROOTS);
        }
        portal(w, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.SOUL_LANTERN);
        baseNucleo(w, Material.GILDED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.SOUL_LANTERN);
        pads(w, Material.CRIMSON_PLANKS, Material.POLISHED_BLACKSTONE);
    }

    /** Geleira: ilha de neve com lago congelado em volta, pinheiros nevados e blocos de gelo. */
    private void construirGeleira(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int x = minX - 4; x <= maxX + 4; x++) {
            for (int z = minZ - 4; z <= maxZ + 4; z++) {
                for (int y = Y; y <= Y + 10; y++) por(w, x, y, z, Material.AIR);
                boolean ilha = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                if (ilha) {
                    por(w, x, Y - 1, z, noCaminho(x, z) ? trilha : chao);
                    por(w, x, Y - 2, z, Material.SNOW_BLOCK);
                    por(w, x, Y - 3, z, Material.STONE);
                    if (Math.abs(x) < maxX - 3 && z > minZ + 3 && z < maxZ - 3) por(w, x, Y - 4, z, Material.PACKED_ICE);
                } else {
                    por(w, x, Y - 2, z, Material.PACKED_ICE);
                    por(w, x, Y - 1, z, (x + z) % 3 == 0 ? Material.BLUE_ICE : Material.ICE);
                }
            }
        }
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (x == minX || x == maxX || z == minZ || z == maxZ) w.getBlockAt(ox + x, Y, oz + z).setType(Material.SPRUCE_FENCE, true);
        // pinheiros nevados nos cantos e blocos de gelo espalhados
        int[][] cantos = {{minX + 2, minZ + 2}, {maxX - 2, minZ + 2}, {minX + 2, maxZ - 2}, {maxX - 2, maxZ - 2}};
        for (int[] c : cantos) {
            if (noCaminho(c[0], c[1]) || perto(c[0], c[1], caminho[0][0], caminho[0][1], 4) || perto(c[0], c[1], lobbyX, lobbyZ, 3) || perto(c[0], c[1], lojaX, lojaZ, 3) || perto(c[0], c[1], nucleoX, nucleoZ, 4)) continue;
            for (int y = Y; y < Y + 4; y++) por(w, c[0], y, c[1], Material.SPRUCE_LOG);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                por(w, c[0] + dx, Y + 3, c[1] + dz, Material.SPRUCE_LEAVES);
                por(w, c[0] + dx, Y + 4, c[1] + dz, dx == 0 || dz == 0 ? Material.SNOW_BLOCK : Material.AIR);
            }
            por(w, c[0], Y + 5, c[1], Material.SNOW_BLOCK);
        }
        for (int n = 0; n < 40; n++) {
            int x = r.nextInt(minX + 2, maxX - 1);
            int z = r.nextInt(minZ + 2, maxZ - 1);
            if (noCaminho(x, z) || perto(x, z, nucleoX, nucleoZ, 4) || perto(x, z, lobbyX, lobbyZ, 3) || perto(x, z, lojaX, lojaZ, 3)) continue;
            Block b = w.getBlockAt(ox + x, Y, oz + z);
            if (b.getType() != Material.AIR) continue;
            por(w, x, Y, z, n % 4 == 0 ? Material.POWDER_SNOW_CAULDRON : Material.SNOW);
        }
        portal(w, Material.PACKED_ICE, Material.BLUE_ICE, Material.SEA_LANTERN);
        baseNucleo(w, Material.DIAMOND_BLOCK, Material.PACKED_ICE, Material.SEA_LANTERN);
        pads(w, Material.SPRUCE_PLANKS, Material.POLISHED_DIORITE);
    }

    /** Ninho do Dragao: ilha de pedra do Fim com pilares de obsidiana e varas do Fim, borda de purpura. */
    private void construirNinho(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int x = minX - 3; x <= maxX + 3; x++) {
            for (int z = minZ - 3; z <= maxZ + 3; z++) {
                for (int y = Y; y <= Y + 12; y++) por(w, x, y, z, Material.AIR);
                boolean ilha = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                if (ilha) {
                    por(w, x, Y - 1, z, noCaminho(x, z) ? trilha : chao);
                    por(w, x, Y - 2, z, Material.END_STONE);
                    por(w, x, Y - 3, z, Material.END_STONE_BRICKS);
                    if (Math.abs(x) < maxX - 3 && z > minZ + 3 && z < maxZ - 3) por(w, x, Y - 4, z, Material.END_STONE);
                    if (Math.abs(x) < maxX - 8 && z > minZ + 8 && z < maxZ - 8) por(w, x, Y - 6, z, Material.END_STONE);
                }
            }
        }
        int i = 0;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
                por(w, x, Y, z, Material.PURPUR_PILLAR);
                if (i++ % 5 == 0) por(w, x, Y + 1, z, Material.END_ROD);
            }
        // pilares de obsidiana (como os do Fim), mais altos no fundo
        int[][] pil = {{minX + 2, minZ + 2}, {maxX - 2, minZ + 2}, {minX + 2, maxZ - 6}, {maxX - 2, maxZ - 6}};
        for (int[] c : pil) {
            if (noCaminho(c[0], c[1]) || perto(c[0], c[1], caminho[0][0], caminho[0][1], 4)) continue;
            int alt = 6 + r.nextInt(4);
            for (int y = Y; y < Y + alt; y++) por(w, c[0], y, c[1], Material.OBSIDIAN);
            por(w, c[0], Y + alt, c[1], Material.IRON_BARS);
            por(w, c[0], Y + alt + 1, c[1], Material.END_ROD);
        }
        for (int n = 0; n < 30; n++) {
            int x = r.nextInt(minX + 2, maxX - 1);
            int z = r.nextInt(minZ + 2, maxZ - 1);
            if (noCaminho(x, z) || perto(x, z, nucleoX, nucleoZ, 4) || perto(x, z, lobbyX, lobbyZ, 3) || perto(x, z, lojaX, lojaZ, 3)) continue;
            Block b = w.getBlockAt(ox + x, Y, oz + z);
            if (b.getType() != Material.AIR) continue;
            por(w, x, Y, z, n % 3 == 0 ? Material.END_ROD : Material.SMALL_AMETHYST_BUD);
        }
        portal(w, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.END_ROD);
        baseNucleo(w, Material.CRYING_OBSIDIAN, Material.PURPUR_BLOCK, Material.END_ROD);
        int cx = (int) Math.floor(nucleoX), cz = (int) Math.floor(nucleoZ);
        por(w, cx, Y - 2, cz, Material.OBSIDIAN);
        pads(w, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS);
    }

    /** Enfeites novos que entram tambem nos mapas ja construidos (so poe onde esta vazio). */
    /** Campos (13/11, v2 a pedido do Paulo): montanha ao norte com uma CACHOEIRA de verdade que cai num rio que corta o
     *  mapa de norte a sul (atravessa 3 trechos do caminho) e termina num lago. A correnteza arrasta os monstros rio
     *  abaixo e depois os joga de volta no chao (Partida.correnteza). Morrinhos enfeitam os lados de fora do mapa. */
    static final int RIO_X1 = 6, RIO_X2 = 8, RIO_Z1 = -23, RIO_Z2 = 4;      // canal do rio (agua no nivel do chao)
    static final int LAGO_X1 = 4, LAGO_X2 = 10, LAGO_Z1 = 4, LAGO_Z2 = 9;   // lago no fim do rio
    static final int QUEDA_TOPO = 10;                                       // a agua cai de Y+10
    // aqueduto antigo (v1), so para desmontar
    private static final int V1_X1 = -22, V1_X2 = -8, V1_Z1 = -20, V1_Z2 = -16;

    /** Esta dentro do rio ou do lago (x, z relativos). */
    static boolean noRio(int x, int z) {
        return (x >= RIO_X1 && x <= RIO_X2 && z >= RIO_Z1 && z <= RIO_Z2) || (x >= LAGO_X1 && x <= LAGO_X2 && z >= LAGO_Z1 && z <= LAGO_Z2);
    }

    void enfeites(World w) {
        if (tema == Tema.CAMPOS) { cachoeira(w); garantirQueda(w); return; }
        if (tema != Tema.VULCAO) return;
        vulcaoNoCaminho(w);
        // cratera ao norte, fora da ilha: anel de basalto com magma e fogueiras de sinal (fumaca alta)
        int cx = (minX + maxX) / 2, cz = minZ - 9;
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 4.3) continue;
                por(w, cx + dx, Y - 2, cz + dz, Material.BLACKSTONE);
                por(w, cx + dx, Y - 1, cz + dz, d < 2.2 ? Material.MAGMA_BLOCK : Material.BASALT);
                if (d >= 3.2) por(w, cx + dx, Y, cz + dz, Material.BASALT);
                if (d >= 3.8) por(w, cx + dx, Y + 1, cz + dz, Material.BLACKSTONE);
            }
        if (w.getBlockAt(ox + cx, Y, oz + cz).getType() == Material.AIR) {
            w.getBlockAt(ox + cx, Y, oz + cz).setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:campfire[signal_fire=true,lit=true]"), false);
            w.getBlockAt(ox + cx + 1, Y, oz + cz - 1).setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:campfire[signal_fire=true,lit=true]"), false);
        }
    }

    /** 13/11: agua posta sem fisica nao escorria - a cortina caindo e montada explicitamente (agua "caindo", nivel 8). */
    private void garantirQueda(World w) {
        org.bukkit.block.data.BlockData caindo = org.bukkit.Bukkit.createBlockData("minecraft:water[level=8]");
        for (int x = RIO_X1; x <= RIO_X2; x++) {
            por(w, x, Y + QUEDA_TOPO, -24, Material.WATER);
            for (int y = Y; y <= Y + QUEDA_TOPO; y++) {
                Block b = w.getBlockAt(ox + x, y, oz - 23);
                if (b.getType() == Material.AIR || b.getType() == Material.WATER) b.setBlockData(caindo, false);
            }
        }
    }

    private void cachoeira(World w) {
        if (w.getBlockAt(ox + 7, Y + QUEDA_TOPO, oz - 25).getType() == Material.WATER) return;   // ja feita
        // 1) desmonta o aqueduto antigo (v1)
        for (int x = V1_X1 - 1; x <= V1_X2 + 1; x++)
            for (int z = V1_Z1 - 1; z <= V1_Z2 + 1; z++)
                for (int y = Y + 1; y <= Y + 10; y++) por(w, x, y, z, Material.AIR);
        for (int x = V1_X1 + 1; x <= V1_X2 + 1; x++)
            for (int z = V1_Z1 - 1; z <= V1_Z2 + 1; z++) por(w, x, Y, z, Material.AIR);
        java.util.Random r = new java.util.Random(77);
        // 2) montanha do norte (fora da cerca) com a fenda da cachoeira
        for (int x = -16; x <= 30; x++)
            for (int z = -40; z <= -24; z++) {
                double h = 12 - Math.abs(z + 31) * 0.95 - Math.abs(x - 7) * 0.32 + Math.sin(x * 0.7) * 1.2 + Math.cos(z * 0.9 + x * 0.3) * 0.9;
                if (x >= 4 && x <= 10 && z >= -27) h = Math.max(h, QUEDA_TOPO + 1);   // o paredao atras da queda
                montanha(w, x, z, (int) Math.round(h), r);
            }
        // fenda da cachoeira: canal no topo com a nascente, agua corre para a beira e despenca
        for (int x = RIO_X1 - 1; x <= RIO_X2 + 1; x++) {
            boolean parede = x < RIO_X1 || x > RIO_X2;
            for (int z = -27; z <= -24; z++) {
                por(w, x, Y + QUEDA_TOPO - 1, z, Material.MOSSY_COBBLESTONE);
                por(w, x, Y + QUEDA_TOPO, z, parede || z == -27 ? Material.MOSSY_COBBLESTONE : Material.AIR);
                por(w, x, Y + QUEDA_TOPO + 1, z, parede || z == -27 ? Material.MOSSY_COBBLESTONE : Material.AIR);
                for (int y = Y + QUEDA_TOPO + 2; y <= Y + 16; y++) if (!parede) por(w, x, y, z, Material.AIR);
            }
            // a frente da queda (z = -23) fica livre do chao ate o topo
            for (int y = Y; y <= Y + 16; y++) por(w, x, y, -23, parede && y <= Y + QUEDA_TOPO + 1 ? Material.MOSSY_STONE_BRICKS : Material.AIR);
        }
        // poco embaixo da queda + rio + lago (agua parada no nivel do chao, fundo de pedra)
        for (int x = LAGO_X1 - 1; x <= LAGO_X2 + 1; x++)
            for (int z = RIO_Z1 - 1; z <= LAGO_Z2 + 1; z++) {
                boolean agua = noRio(x, z);
                if (agua) {
                    por(w, x, Y - 3, z, Material.STONE);
                    por(w, x, Y - 2, z, z % 4 == 0 ? Material.GRAVEL : Material.SAND);
                    por(w, x, Y - 1, z, Material.WATER);
                    if (z < minZ) for (int y = Y; y <= Y + 1; y++) if (x >= RIO_X1 && x <= RIO_X2) por(w, x, y, z, Material.AIR);
                } else if (z < minZ && (x == RIO_X1 - 1 || x == RIO_X2 + 1)) {
                    por(w, x, Y - 2, z, Material.STONE);
                    por(w, x, Y - 1, z, Material.MOSSY_COBBLESTONE);
                }
            }
        // nascente: agua parada no topo (ela escorre ate a beira e cai em cascata ate o poco)
        for (int x = RIO_X1; x <= RIO_X2; x++) por(w, x, Y + QUEDA_TOPO, -26, Material.WATER);
        for (int x = RIO_X1; x <= RIO_X2; x++) por(w, x, Y + QUEDA_TOPO, -25, Material.WATER);
        // vitorias-regias e pedras na beira do lago
        for (int n = 0; n < 6; n++) {
            int x = LAGO_X1 + r.nextInt(LAGO_X2 - LAGO_X1 + 1), z = LAGO_Z1 + 1 + r.nextInt(LAGO_Z2 - LAGO_Z1);
            if (w.getBlockAt(ox + x, Y, oz + z).getType() == Material.AIR) por(w, x, Y, z, Material.LILY_PAD);
        }
        // 3) morrinhos dos lados de fora (oeste, leste e sul)
        for (int x = -40; x <= 40; x++)
            for (int z = -24; z <= 34; z++) {
                boolean oeste = x <= -25, leste = x >= 25, sul = z >= 26 && Math.abs(x) <= 24;
                if (!oeste && !leste && !sul) continue;
                if (sul && (z < 27)) continue;
                double d = oeste ? -25 - x : leste ? x - 25 : z - 26;
                double h = 2 + d * 0.55 + Math.sin(z * 0.45 + x * 0.2) * 2.0 + Math.cos(x * 0.35) * 1.5;
                if (sul) h = Math.min(h, 4 + Math.sin(x * 0.4) * 2);
                montanha(w, x, z, (int) Math.round(Math.min(h, 9)), r);
            }
    }

    /** Vulcao (13/11, Paulo): um vulcaozinho rachado ao meio no caminho do leste; os monstros passam pela cratera,
     *  sao jogados para o alto pela erupcao e voltam a andar (Partida.erupcaoNoCaminho). */
    static final int CRATERA_X = 18, CRATERA_Z1 = -2, CRATERA_Z2 = 2;

    static boolean naCratera(int x, int z) { return Math.abs(x - CRATERA_X) <= 1 && z >= CRATERA_Z1 && z <= CRATERA_Z2; }

    private void vulcaoNoCaminho(World w) {
        for (int z = -5; z <= 5; z++)
            for (int lado : new int[]{-1, 1})
                for (int d = 2; d <= 4; d++) {   // distancia do meio do caminho
                    int x = CRATERA_X + lado * d;
                    if (x >= maxX) continue;
                    int h = (int) Math.round(5.5 - Math.abs(z) * 0.8 - (d - 2) * 1.3);
                    for (int y = Y; y < Y + h; y++)
                        por(w, x, y, z, y == Y + h - 1 && d == 2 ? Material.MAGMA_BLOCK : ((x + y + z) % 3 == 0 ? Material.BLACKSTONE : Material.BASALT));
                }
        for (int z = CRATERA_Z1; z <= CRATERA_Z2; z++)
            for (int dx = -1; dx <= 1; dx++) por(w, CRATERA_X + dx, Y - 1, z, (dx + z) % 2 == 0 ? Material.SHROOMLIGHT : Material.NETHERRACK);   // magma assustava os monstros (davam a volta)
    }

    /** Uma coluna de montanha: pedra, terra e grama (com flor ou arvore de vez em quando). */
    private void montanha(World w, int x, int z, int h, java.util.Random r) {
        if (h <= 0) return;
        int topo = Y - 1 + h;
        for (int y = Y - 4; y <= topo; y++) {
            Material m = y == topo ? (h > 9 ? Material.STONE : Material.GRASS_BLOCK) : y >= topo - 2 ? (h > 9 ? Material.STONE : Material.DIRT)
                    : (r.nextInt(9) == 0 ? Material.ANDESITE : Material.STONE);
            if (y < topo - 3 && r.nextInt(14) == 0) m = Material.MOSSY_COBBLESTONE;
            por(w, x, y, z, m);
        }
        if (h > 9) return;
        int s = r.nextInt(60);
        if (s < 3) {   // arvore
            for (int y = topo + 1; y <= topo + 4; y++) por(w, x, y, z, Material.OAK_LOG);
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int y = topo + 3; y <= topo + 5; y++) {
                        if (Math.abs(dx) + Math.abs(dz) + (y - topo - 3) > 3) continue;
                        if (w.getBlockAt(ox + x + dx, y, oz + z + dz).getType() == Material.AIR) por(w, x + dx, y, z + dz, Material.OAK_LEAVES);
                    }
        } else if (s < 9) por(w, x, topo + 1, z, s < 6 ? Material.SHORT_GRASS : (s == 6 ? Material.POPPY : s == 7 ? Material.DANDELION : Material.CORNFLOWER));
    }

    private static boolean perto(int x, int z, double cx, double cz, double raio) {
        double dx = x + 0.5 - cx;
        double dz = z + 0.5 - cz;
        return dx * dx + dz * dz <= raio * raio;
    }

    private void portal(World w, Material moldura, Material centro, Material luz) {
        int sx = caminho[0][0];
        int sz = caminho[0][1];
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                por(w, sx + dx, Y - 1, sz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? moldura : centro);
            }
        }
        por(w, sx - 2, Y, sz - 2, luz);
        por(w, sx + 2, Y, sz - 2, luz);
    }

    private void baseNucleo(World w, Material meio, Material volta, Material luz) {
        int cx = (int) Math.floor(nucleoX);
        int cz = (int) Math.floor(nucleoZ);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean centro = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                por(w, cx + dx, Y - 1, cz + dz, centro ? meio : volta);
            }
        }
        por(w, cx - 2, Y, cz + 2, luz);
        por(w, cx + 2, Y, cz + 2, luz);
    }

    private void pads(World w, Material loja, Material lobby) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                por(w, (int) Math.floor(lojaX) + dx, Y - 1, (int) Math.floor(lojaZ) + dz, loja);
                por(w, (int) Math.floor(lobbyX) + dx, Y - 1, (int) Math.floor(lobbyZ) + dz, lobby);
            }
        }
    }
}
