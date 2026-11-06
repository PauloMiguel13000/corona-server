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

    enum Tema { CAMPOS, VULCAO }

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

    static final List<Mapa> TODOS = List.of(CAMPOS, VULCAO);

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
        return tema == Tema.VULCAO ? m == Material.GILDED_BLACKSTONE : m == Material.OBSIDIAN;
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
