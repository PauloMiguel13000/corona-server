package br.servidor.slender;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.noise.SimplexOctaveGenerator;

/**
 * O mapa do Slenderman: floresta escura de 128x128 blocos cercada, com 12 pontos de referencia
 * (cada um com paredes onde as folhas podem aparecer), trilhas ligando tudo e um lobby do lado de fora do portao.
 */
final class Floresta {

    static final String MUNDO = "slender";
    static final int TAM = 128;
    static final int BASE = 64;
    /** Mude quando o mapa mudar: o plugin reconstroi sozinho. */
    static final int VERSAO = 2;
    private static final int BORDA = 8;

    /** Bloco da parede onde a folha fica presa e o lado da parede para onde ela olha. */
    record Ancora(int x, int y, int z, BlockFace face) {
        Location frente(World w) {
            return new Location(w, x + face.getModX() + 0.5, y + 0.5, z + face.getModZ() + 0.5);
        }
    }

    static final class Ponto {
        final String nome;
        final int cx;
        final int cz;
        final int raio;
        final BiConsumer<Ponto, Obra> obra;
        /** O desenho foi feito em outra posicao; dx/dz levam para o lugar certo. */
        final int dx;
        final int dz;
        int base;
        final List<Ancora> ancoras = new ArrayList<>();

        Ponto(String nome, int cx, int cz, int raio, int desenhoX, int desenhoZ, BiConsumer<Ponto, Obra> obra) {
            this.nome = nome;
            this.cx = cx;
            this.cz = cz;
            this.raio = raio;
            this.dx = cx - desenhoX;
            this.dz = cz - desenhoZ;
            this.obra = obra;
        }

        void construir(Obra o) {
            obra.accept(this, deslocada(o, dx, dz));
        }
    }

    static Obra deslocada(Obra o, int dx, int dz) {
        return new Obra() {
            @Override public void bloco(int x, int y, int z, Material m) { o.bloco(x + dx, y, z + dz, m); }
            @Override public void dado(int x, int y, int z, BlockData d) { o.dado(x + dx, y, z + dz, d); }
            @Override public void ancora(Ponto p, int x, int y, int z, BlockFace face) { o.ancora(p, x + dx, y, z + dz, face); }
        };
    }

    /** Quem constroi: no planejamento so anota as ancoras; na construcao coloca os blocos. */
    interface Obra {
        void bloco(int x, int y, int z, Material m);

        void dado(int x, int y, int z, BlockData d);

        void ancora(Ponto p, int x, int y, int z, BlockFace face);

        default void caixa(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) bloco(x, y, z, m);
        }

        /** Paredes de um retangulo (sem chao nem teto). */
        default void oco(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    bloco(x, y, z1, m);
                    bloco(x, y, z2, m);
                }
                for (int z = z1; z <= z2; z++) {
                    bloco(x1, y, z, m);
                    bloco(x2, y, z, m);
                }
            }
        }
    }

    final int[][] altura = new int[TAM][TAM];
    final boolean[][] caminho = new boolean[TAM][TAM];
    final boolean[][] semArvore = new boolean[TAM][TAM];
    final boolean[][] ocupado = new boolean[TAM][TAM];
    final List<Ponto> pontos = new ArrayList<>();
    private final SimplexOctaveGenerator solo = new SimplexOctaveGenerator(new Random(11), 2);
    private final SimplexOctaveGenerator mata = new SimplexOctaveGenerator(new Random(23), 2);

    Floresta() {
        SimplexOctaveGenerator relevo = new SimplexOctaveGenerator(new Random(7), 3);
        relevo.setScale(1 / 48.0);
        solo.setScale(1 / 18.0);
        mata.setScale(1 / 34.0);
        for (int x = 0; x < TAM; x++)
            for (int z = 0; z < TAM; z++)
                altura[x][z] = BASE + (int) Math.round(relevo.noise(x, z, 0.5, 0.5, true) * 2.4);
        definirPontos();
        for (Ponto p : pontos) p.base = altura[p.cx][p.cz];
        for (Ponto p : pontos) aplainar(p.cx, p.cz, p.raio, p.base);
        aplainar(63, 120, 8, BASE);
        for (int x = 54; x <= 73; x++)
            for (int z = 122; z < TAM; z++) altura[x][z] = BASE;
        tracarCaminhos();
        for (Ponto p : pontos) marcar(ocupado, p.cx, p.cz, p.raio);
        for (Ponto p : pontos) marcar(semArvore, p.cx, p.cz, p.raio + 2);
        marcar(semArvore, 63, 120, 9);
        for (int x = 0; x < TAM; x++)
            for (int z = 0; z < TAM; z++) {
                if (!caminho[x][z]) continue;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        int a = x + dx, b = z + dz;
                        if (a >= 0 && b >= 0 && a < TAM && b < TAM) semArvore[a][b] = true;
                    }
            }
        Obra planta = new Obra() {
            @Override public void bloco(int x, int y, int z, Material m) {}
            @Override public void dado(int x, int y, int z, BlockData d) {}
            @Override public void ancora(Ponto p, int x, int y, int z, BlockFace face) {
                p.ancoras.add(new Ancora(x, y, z, face));
            }
        };
        for (Ponto p : pontos) p.construir(planta);
    }

    static Location lobby(World w) {
        return new Location(w, 64.5, BASE + 1, 142.5, 180f, 0f);
    }

    Location inicio(World w, int i) {
        double x = 62.5 + (i % 4), z = 119.5 + (i / 4) % 3;
        return new Location(w, x, altura[(int) x][(int) z] + 1, z, 180f, 0f);
    }

    int alturaEm(int x, int z) {
        if (x < 0 || z < 0 || x >= TAM || z >= TAM) return BASE;
        return altura[x][z];
    }

    static boolean dentro(Location l) {
        return l.getX() >= 0 && l.getZ() >= 0 && l.getX() < TAM && l.getZ() < TAM;
    }

    static boolean noLobby(Location l) {
        return l.getX() >= 51 && l.getX() <= 78 && l.getZ() >= TAM - 0.5 && l.getZ() <= TAM + 23;
    }

    // ================================================================== planejamento

    private void aplainar(int cx, int cz, int raio, int alvo) {
        int r = raio + 5;
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++) {
                if (x < 0 || z < 0 || x >= TAM || z >= TAM) continue;
                double d = Math.hypot(x - cx, z - cz);
                if (d <= raio) altura[x][z] = alvo;
                else if (d <= r) altura[x][z] = (int) Math.round(alvo + (altura[x][z] - alvo) * (d - raio) / 5.0);
            }
    }

    private static void marcar(boolean[][] g, int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                if (x >= 0 && z >= 0 && x < TAM && z < TAM && Math.hypot(x - cx, z - cz) <= r) g[x][z] = true;
    }

    private void tracarCaminhos() {
        List<int[]> nos = new ArrayList<>();
        nos.add(new int[] {63, 120});
        for (Ponto p : pontos) nos.add(new int[] {p.cx, p.cz});
        Random r = new Random(99);
        int n = nos.size();
        boolean[] dentro = new boolean[n];
        boolean[][] ligado = new boolean[n][n];
        dentro[0] = true;
        for (int k = 1; k < n; k++) {
            int bi = -1, bj = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!dentro[i]) continue;
                for (int j = 0; j < n; j++) {
                    if (dentro[j]) continue;
                    double d = Math.hypot(nos.get(i)[0] - nos.get(j)[0], nos.get(i)[1] - nos.get(j)[1]);
                    if (d < best) {
                        best = d;
                        bi = i;
                        bj = j;
                    }
                }
            }
            dentro[bj] = true;
            ligado[bi][bj] = ligado[bj][bi] = true;
            ligar(nos.get(bi), nos.get(bj), r);
        }
        // alguns caminhos extras para ter rotas de fuga (nao so uma arvore de trilhas)
        int extras = 0;
        for (int t = 0; t < 200 && extras < 5; t++) {
            int i = r.nextInt(n), j = r.nextInt(n);
            if (i == j || ligado[i][j]) continue;
            double d = Math.hypot(nos.get(i)[0] - nos.get(j)[0], nos.get(i)[1] - nos.get(j)[1]);
            if (d > 50) continue;
            ligado[i][j] = ligado[j][i] = true;
            ligar(nos.get(i), nos.get(j), r);
            extras++;
        }
    }

    private void ligar(int[] a, int[] b, Random r) {
        double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.hypot(dx, dz);
        if (len < 1) return;
        double px = -dz / len, pz = dx / len;
        double amp = 2 + r.nextDouble() * 3, fase = r.nextDouble() * Math.PI * 2, ondas = 1 + r.nextDouble() * 2;
        for (double s = 0; s <= len; s += 0.5) {
            double t = s / len;
            double off = Math.sin(t * Math.PI * ondas + fase) * amp * Math.sin(t * Math.PI);
            double x = a[0] + dx * t + px * off, z = a[1] + dz * t + pz * off;
            for (int ix = (int) Math.floor(x - 1.3); ix <= (int) Math.ceil(x + 1.3); ix++)
                for (int iz = (int) Math.floor(z - 1.3); iz <= (int) Math.ceil(z + 1.3); iz++) {
                    if (ix < 2 || iz < 2 || ix >= TAM - 2 || iz >= TAM - 2) continue;
                    if (Math.hypot(ix + 0.5 - x, iz + 0.5 - z) <= 1.3) caminho[ix][iz] = true;
                }
        }
    }

    // ================================================================== construcao em etapas

    /** Lista de etapas pequenas para construir sem travar o servidor. */
    Deque<Runnable> etapas(World w, boolean limpar) {
        Deque<Runnable> fila = new ArrayDeque<>();
        Obra real = new Obra() {
            @Override public void bloco(int x, int y, int z, Material m) {
                w.getBlockAt(x, y, z).setType(m, false);
            }
            @Override public void dado(int x, int y, int z, BlockData d) {
                w.getBlockAt(x, y, z).setBlockData(d, false);
            }
            @Override public void ancora(Ponto p, int x, int y, int z, BlockFace face) {}
        };
        if (limpar) {
            for (int x = -2; x < 200; x++) {
                final int fx = x;
                fila.add(() -> {
                    for (int z = -2; z < 220; z++)
                        for (int y = BASE - 10; y <= BASE + 50; y++) {
                            Block b = w.getBlockAt(fx, y, z);
                            if (!b.getType().isAir()) b.setType(Material.AIR, false);
                        }
                });
            }
        }
        for (int x = 0; x < TAM; x++) {
            final int fx = x;
            fila.add(() -> terreno(w, fx));
        }
        for (Ponto p : pontos) fila.add(() -> p.construir(real));
        fila.add(() -> cerca(w));
        fila.add(() -> lobbyObra(deslocada(real, -32, TAM - 192)));
        List<int[]> arvores = posicoesArvores();
        Random ra = new Random(4242);
        for (int i = 0; i < arvores.size(); i += 12) {
            final int ini = i;
            fila.add(() -> {
                for (int k = ini; k < Math.min(ini + 12, arvores.size()); k++) arvore(w, arvores.get(k)[0], arvores.get(k)[1], ra);
            });
        }
        Random rd = new Random(777);
        for (int x = 1; x < TAM - 1; x++) {
            final int fx = x;
            fila.add(() -> decorar(w, fx, rd));
        }
        fila.add(() -> {
            for (Ponto p : pontos) for (Ancora a : p.ancoras) prepararAncora(w, a);
            w.getBlockAt(0, BASE - 14, 0).setType(Material.LODESTONE, false);
        });
        return fila;
    }

    boolean construido(World w) {
        return w.getBlockAt(0, BASE - 14, 0).getType() == Material.LODESTONE;
    }

    private void terreno(World w, int x) {
        for (int z = 0; z < TAM; z++) {
            int h = altura[x][z];
            for (int y = BASE - 8; y < h - 3; y++) w.getBlockAt(x, y, z).setType(Material.STONE, false);
            for (int y = Math.max(BASE - 8, h - 3); y < h; y++) w.getBlockAt(x, y, z).setType(Material.DIRT, false);
            w.getBlockAt(x, h, z).setType(superficie(x, z), false);
        }
    }

    private Material superficie(int x, int z) {
        if (caminho[x][z]) return ((x * 31 + z * 17) % 7 == 0) ? Material.COARSE_DIRT : Material.DIRT_PATH;
        double n = solo.noise(x, z, 0.5, 0.5, true);
        int h = (x * 73856093) ^ (z * 19349663);
        if ((h & 63) == 0) return Material.ROOTED_DIRT;
        if (n < -0.45) return Material.COARSE_DIRT;
        if (n < -0.2) return Material.PODZOL;
        if (n > 0.5) return Material.PALE_MOSS_BLOCK;
        if (n > 0.32) return Material.MOSS_BLOCK;
        return Material.GRASS_BLOCK;
    }

    private void cerca(World w) {
        for (int i = 0; i < TAM; i++) {
            poste(w, 0, i, i % 6 == 0);
            poste(w, TAM - 1, i, i % 6 == 0);
            poste(w, i, 0, i % 6 == 0);
            poste(w, i, TAM - 1, i % 6 == 0);
        }
    }

    private void poste(World w, int x, int z, boolean grosso) {
        int h = altura[x][z];
        int topo = grosso ? h + 4 : h + 3;
        for (int y = h + 1; y <= topo; y++) w.getBlockAt(x, y, z).setType(grosso ? Material.DARK_OAK_LOG : Material.IRON_BARS, false);
        for (int y = topo + 1; y <= BASE + 46; y++) w.getBlockAt(x, y, z).setType(Material.BARRIER, false);
    }

    private List<int[]> posicoesArvores() {
        List<int[]> l = new ArrayList<>();
        Random r = new Random(31337);
        for (int gx = 2; gx < TAM - 2; gx += 4)
            for (int gz = 2; gz < TAM - 2; gz += 4) {
                int x = gx + r.nextInt(3), z = gz + r.nextInt(3);
                if (x < 2 || z < 2 || x >= TAM - 2 || z >= TAM - 2 || semArvore[x][z]) continue;
                boolean borda = x < BORDA || z < BORDA || x >= TAM - BORDA || z >= TAM - BORDA;
                double d = mata.noise(x, z, 0.5, 0.5, true);
                double prob = borda ? 0.95 : d < -0.45 ? 0.08 : d < -0.2 ? 0.35 : 0.66;
                if (r.nextDouble() <= prob) l.add(new int[] {x, z});
            }
        return l;
    }

    private void arvore(World w, int x, int z, Random r) {
        double t = r.nextDouble();
        TreeType tipo = t < 0.32 ? TreeType.PALE_OAK : t < 0.55 ? TreeType.DARK_OAK : t < 0.72 ? TreeType.TALL_REDWOOD
                : t < 0.80 ? TreeType.REDWOOD : t < 0.86 ? TreeType.TALL_BIRCH : null;
        Location l = new Location(w, x, altura[x][z] + 1, z);
        boolean ok = false;
        if (tipo != null) {
            try {
                ok = w.generateTree(l, tipo);
            } catch (Throwable ignored) {
                ok = false;
            }
        }
        if (!ok) arvoreMorta(w, x, z, r);
    }

    private void arvoreMorta(World w, int x, int z, Random r) {
        Material log = switch (r.nextInt(3)) {
            case 0 -> Material.DARK_OAK_LOG;
            case 1 -> Material.PALE_OAK_LOG;
            default -> Material.SPRUCE_LOG;
        };
        int b = altura[x][z], h = 5 + r.nextInt(5);
        for (int y = 1; y <= h; y++) w.getBlockAt(x, b + y, z).setType(log, false);
        int galhos = 1 + r.nextInt(3);
        for (int g = 0; g < galhos; g++) {
            int y = b + 3 + r.nextInt(Math.max(1, h - 3));
            BlockFace f = new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}[r.nextInt(4)];
            BlockData d = log.createBlockData("[axis=" + (f.getModX() != 0 ? "x" : "z") + "]");
            int len = 1 + r.nextInt(3);
            for (int i = 1; i <= len; i++) {
                Block bl = w.getBlockAt(x + f.getModX() * i, y + (i == len && r.nextBoolean() ? 1 : 0), z + f.getModZ() * i);
                if (bl.getType().isAir()) bl.setBlockData(d, false);
            }
        }
    }

    private void decorar(World w, int x, Random r) {
        for (int z = 1; z < TAM - 1; z++) {
            if (caminho[x][z] || ocupado[x][z]) continue;
            int h = altura[x][z];
            Block chao = w.getBlockAt(x, h, z), cima = w.getBlockAt(x, h + 1, z);
            if (!cima.getType().isAir()) continue;
            Material m = chao.getType();
            double v = r.nextDouble();
            if (m == Material.PALE_MOSS_BLOCK && v < 0.4) {
                cima.setType(Material.PALE_MOSS_CARPET, false);
                continue;
            }
            if (m != Material.GRASS_BLOCK && m != Material.MOSS_BLOCK && m != Material.PODZOL && m != Material.COARSE_DIRT
                    && m != Material.ROOTED_DIRT && m != Material.PALE_MOSS_BLOCK) continue;
            Material p = null;
            if (v < 0.13) p = Material.SHORT_GRASS;
            else if (v < 0.17) p = Material.FERN;
            else if (v < 0.20) p = Material.LEAF_LITTER;
            else if (v < 0.215) p = Material.DEAD_BUSH;
            else if (v < 0.225) p = Material.BUSH;
            else if (v < 0.229) p = Material.OPEN_EYEBLOSSOM;
            else if (v < 0.2305) p = Material.FIREFLY_BUSH;
            if (p != null) cima.setType(p, false);
        }
    }

    /** Garante a parede firme e o espaco da folha livre (arvores podem ter crescido na frente). */
    private static void prepararAncora(World w, Ancora a) {
        Block parede = w.getBlockAt(a.x, a.y, a.z);
        if (!parede.getType().isSolid()) parede.setType(Material.STONE_BRICKS, false);
        Block frente = parede.getRelative(a.face);
        if (!frente.getType().isAir()) frente.setType(Material.AIR, false);
    }

    // ================================================================== pontos de referencia

    private void definirPontos() {
        // nome, centro no mapa, raio livre, centro onde o desenho foi feito, obra
        pontos.add(new Ponto("Ônibus velho", 20, 18, 9, 24, 24, this::onibus));
        pontos.add(new Ponto("Cabana abandonada", 58, 20, 7, 36, 40, this::cabana));
        pontos.add(new Ponto("Banheiros", 102, 18, 7, 150, 40, this::banheiros));
        pontos.add(new Ponto("Rochas", 16, 50, 7, 24, 100, this::rochas));
        pontos.add(new Ponto("Árvore morta gigante", 46, 48, 7, 96, 62, this::arvoreGigante));
        pontos.add(new Ponto("Torre de água", 80, 46, 6, 166, 112, this::torre));
        pontos.add(new Ponto("Capela em ruínas", 108, 54, 8, 62, 96, this::capela));
        pontos.add(new Ponto("Caminhão", 28, 80, 9, 100, 122, this::caminhao));
        pontos.add(new Ponto("Acampamento", 62, 78, 8, 128, 92, this::acampamento));
        pontos.add(new Ponto("Muros", 98, 84, 9, 128, 166, this::muros));
        pontos.add(new Ponto("Túnel", 24, 106, 11, 55, 150, this::tunel));
        pontos.add(new Ponto("Cemitério", 100, 108, 9, 30, 168, this::cemiterio));
    }

    private static Material pedra(Random r) {
        int v = r.nextInt(10);
        return v < 4 ? Material.MOSSY_STONE_BRICKS : v < 7 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
    }

    private static BlockData eixo(Material m, char e) {
        return m.createBlockData("[axis=" + e + "]");
    }

    private void cabana(Ponto p, Obra o) {
        int b = p.base;
        Random r = new Random(1);
        o.caixa(32, b, 37, 40, b, 43, Material.SPRUCE_PLANKS);
        o.oco(32, b + 1, 37, 40, b + 4, 43, Material.SPRUCE_PLANKS);
        for (int[] c : new int[][] {{32, 37}, {40, 37}, {32, 43}, {40, 43}}) o.caixa(c[0], b + 1, c[1], c[0], b + 4, c[1], Material.SPRUCE_LOG);
        o.caixa(36, b + 1, 43, 36, b + 2, 43, Material.AIR);
        for (int[] j : new int[][] {{32, 40}, {34, 43}, {38, 43}, {40, 39}}) o.bloco(j[0], b + 2, j[1], Material.AIR);
        for (int x = 31; x <= 41; x++)
            for (int z = 36; z <= 44; z++) o.bloco(x, b + 5, z, r.nextInt(100) < 15 ? Material.AIR : Material.DARK_OAK_SLAB);
        o.bloco(33, b + 1, 38, Material.BARREL);
        o.bloco(39, b + 1, 42, Material.CRAFTING_TABLE);
        o.bloco(33, b + 4, 38, Material.COBWEB);
        o.bloco(39, b + 4, 42, Material.COBWEB);
        o.bloco(36, b + 1, 40, Material.SPRUCE_FENCE);
        o.bloco(36, b + 2, 40, Material.SPRUCE_PRESSURE_PLATE);
        o.ancora(p, 36, b + 2, 37, BlockFace.SOUTH);
        o.ancora(p, 40, b + 2, 41, BlockFace.WEST);
        o.ancora(p, 32, b + 2, 38, BlockFace.EAST);
    }

    private void banheiros(Ponto p, Obra o) {
        int b = p.base;
        o.caixa(146, b, 37, 154, b, 42, Material.SMOOTH_STONE);
        o.oco(146, b + 1, 37, 154, b + 3, 42, Material.WHITE_CONCRETE);
        o.oco(146, b + 1, 37, 154, b + 1, 42, Material.STONE_BRICKS);
        o.caixa(150, b + 1, 37, 150, b + 3, 40, Material.WHITE_CONCRETE);
        o.caixa(148, b + 1, 42, 148, b + 2, 42, Material.AIR);
        o.caixa(152, b + 1, 42, 152, b + 2, 42, Material.AIR);
        o.caixa(145, b + 4, 36, 155, b + 4, 43, Material.SMOOTH_STONE_SLAB);
        o.bloco(147, b + 1, 38, Material.CAULDRON);
        o.bloco(153, b + 1, 38, Material.CAULDRON);
        o.bloco(149, b + 3, 41, Material.COBWEB);
        o.ancora(p, 148, b + 2, 37, BlockFace.SOUTH);
        o.ancora(p, 152, b + 2, 37, BlockFace.SOUTH);
        o.ancora(p, 146, b + 2, 39, BlockFace.WEST);
        o.ancora(p, 154, b + 2, 40, BlockFace.EAST);
    }

    private void caminhao(Ponto p, Obra o) {
        int b = p.base;
        for (int x : new int[] {94, 95, 101, 102}) {
            o.bloco(x, b + 1, 121, Material.COAL_BLOCK);
            o.bloco(x, b + 1, 123, Material.COAL_BLOCK);
        }
        o.caixa(93, b + 2, 121, 104, b + 4, 123, Material.WHITE_CONCRETE);
        o.caixa(93, b + 2, 121, 104, b + 2, 121, Material.LIGHT_GRAY_CONCRETE);
        o.caixa(93, b + 2, 123, 104, b + 2, 123, Material.LIGHT_GRAY_CONCRETE);
        o.caixa(105, b + 1, 121, 107, b + 3, 123, Material.RED_CONCRETE);
        o.bloco(106, b + 1, 121, Material.COAL_BLOCK);
        o.bloco(106, b + 1, 123, Material.COAL_BLOCK);
        o.caixa(107, b + 3, 121, 107, b + 3, 123, Material.BLACK_STAINED_GLASS);
        o.ancora(p, 98, b + 3, 123, BlockFace.SOUTH);
        o.ancora(p, 99, b + 3, 121, BlockFace.NORTH);
        o.ancora(p, 93, b + 3, 122, BlockFace.WEST);
    }

    private void torre(Ponto p, Obra o) {
        int b = p.base;
        int[][] pernas = {{163, 109}, {169, 109}, {163, 115}, {169, 115}};
        for (int[] l : pernas) {
            o.caixa(l[0], b + 1, l[1], l[0], b + 2, l[1], Material.STONE_BRICKS);
            o.caixa(l[0], b + 3, l[1], l[0], b + 11, l[1], Material.DARK_OAK_LOG);
        }
        for (int x = 164; x <= 168; x++) {
            o.bloco(x, b + 6, 109, Material.DARK_OAK_FENCE);
            o.bloco(x, b + 6, 115, Material.DARK_OAK_FENCE);
        }
        for (int z = 110; z <= 114; z++) {
            o.bloco(163, b + 6, z, Material.DARK_OAK_FENCE);
            o.bloco(169, b + 6, z, Material.DARK_OAK_FENCE);
        }
        for (int x = 161; x <= 171; x++)
            for (int z = 107; z <= 117; z++) {
                double d = Math.hypot(x - 166, z - 112);
                if (d <= 4.5) {
                    o.bloco(x, b + 12, z, Material.SPRUCE_PLANKS);
                    if (d > 3.5) o.caixa(x, b + 13, z, x, b + 16, z, Material.SPRUCE_PLANKS);
                    o.bloco(x, b + 17, z, Material.SPRUCE_SLAB);
                }
                if (d <= 2.5) o.bloco(x, b + 18, z, Material.SPRUCE_SLAB);
            }
        BlockData escada = Material.LADDER.createBlockData("[facing=west]");
        for (int y = b + 1; y <= b + 11; y++) o.dado(162, y, 112, escada);
        o.caixa(163, b + 1, 112, 163, b + 11, 112, Material.DARK_OAK_PLANKS);
        o.ancora(p, 163, b + 2, 109, BlockFace.NORTH);
        o.ancora(p, 169, b + 2, 115, BlockFace.SOUTH);
        o.ancora(p, 169, b + 2, 109, BlockFace.EAST);
    }

    private void tunel(Ponto p, Obra o) {
        int b = p.base;
        Random r = new Random(5);
        for (int x = 45; x <= 65; x++)
            for (int z = 142; z <= 158; z++) {
                double e = 1 - Math.pow((x - 55) / 10.0, 2) - Math.pow((z - 150) / 7.5, 2);
                int hm = (int) Math.round(7 * e);
                for (int y = 1; y <= hm; y++) o.bloco(x, b + y, z, y == hm ? Material.GRASS_BLOCK : Material.DIRT);
            }
        o.caixa(44, b + 1, 149, 66, b + 4, 151, Material.AIR);
        for (int x = 47; x <= 63; x++) {
            for (int y = b + 1; y <= b + 4; y++) {
                o.bloco(x, y, 148, pedra(r));
                o.bloco(x, y, 152, pedra(r));
            }
            for (int z = 148; z <= 152; z++) o.bloco(x, b + 5, z, pedra(r));
            for (int z = 149; z <= 151; z++) o.bloco(x, b, z, Material.GRAVEL);
        }
        o.bloco(50, b + 4, 150, Material.COBWEB);
        o.bloco(58, b + 4, 149, Material.COBWEB);
        o.ancora(p, 55, b + 2, 148, BlockFace.SOUTH);
        o.ancora(p, 51, b + 2, 152, BlockFace.NORTH);
        o.ancora(p, 60, b + 2, 152, BlockFace.NORTH);
    }

    private void esfera(Obra o, double cx, double cy, double cz, double r, Random rnd) {
        Material[] m = {Material.STONE, Material.ANDESITE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.TUFF};
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++)
            for (int y = -ri; y <= ri; y++)
                for (int z = -ri; z <= ri; z++)
                    if (x * x + y * y * 1.4 + z * z <= r * r) o.bloco((int) cx + x, (int) cy + y, (int) cz + z, m[rnd.nextInt(m.length)]);
    }

    private void rochas(Ponto p, Obra o) {
        int b = p.base;
        Random r = new Random(6);
        esfera(o, 24, b + 1, 100, 3.6, r);
        esfera(o, 29, b, 104, 2.8, r);
        esfera(o, 20, b, 105, 2.4, r);
        esfera(o, 27, b, 96, 2.0, r);
        o.ancora(p, 27, b + 2, 100, BlockFace.EAST);
        o.ancora(p, 24, b + 2, 97, BlockFace.NORTH);
        o.ancora(p, 21, b + 2, 100, BlockFace.WEST);
    }

    private void arvoreGigante(Ponto p, Obra o) {
        int b = p.base;
        BlockData y = eixo(Material.DARK_OAK_LOG, 'y'), x = eixo(Material.DARK_OAK_LOG, 'x'), z = eixo(Material.DARK_OAK_LOG, 'z');
        for (int h = b + 1; h <= b + 12; h++)
            for (int i = 95; i <= 97; i++)
                for (int k = 61; k <= 63; k++) {
                    boolean canto = (i != 96 && k != 62);
                    if (canto && h > b + 9) continue;
                    o.dado(i, h, k, y);
                }
        for (int h = b + 13; h <= b + 17; h++) o.dado(96, h, 62, y);
        for (int i = 92; i <= 94; i++) o.dado(i, b + 1, 62, x);
        for (int i = 98; i <= 99; i++) o.dado(i, b + 1, 62, x);
        for (int k = 59; k <= 60; k++) o.dado(96, b + 1, k, z);
        for (int i = 98; i <= 101; i++) o.dado(i, b + 9, 62, x);
        for (int i = 91; i <= 94; i++) o.dado(i, b + 11, 62, x);
        for (int k = 64; k <= 67; k++) o.dado(96, b + 10, k, z);
        for (int k = 57; k <= 60; k++) o.dado(96, b + 12, k, z);
        for (int i = 97; i <= 99; i++) o.dado(i, b + 14, 62, x);
        o.bloco(101, b + 8, 62, Material.PALE_HANGING_MOSS);
        o.bloco(91, b + 10, 62, Material.PALE_HANGING_MOSS);
        o.bloco(96, b + 9, 67, Material.PALE_HANGING_MOSS);
        o.bloco(96, b + 11, 57, Material.PALE_HANGING_MOSS);
        o.ancora(p, 96, b + 2, 63, BlockFace.SOUTH);
        o.ancora(p, 96, b + 2, 61, BlockFace.NORTH);
        o.ancora(p, 97, b + 3, 62, BlockFace.EAST);
        o.ancora(p, 95, b + 3, 62, BlockFace.WEST);
    }

    private void muros(Ponto p, Obra o) {
        int b = p.base;
        Random r = new Random(8);
        int[][] muros = {{121, 161, 128, 161}, {134, 159, 134, 166}, {126, 171, 133, 171}, {122, 166, 122, 173}};
        for (int[] m : muros)
            for (int x = m[0]; x <= m[2]; x++)
                for (int z = m[1]; z <= m[3]; z++)
                    for (int y = b + 1; y <= b + 4; y++) {
                        boolean buraco = (y == b + 4 && r.nextInt(100) < 30) || r.nextInt(100) < 5;
                        o.bloco(x, y, z, buraco ? Material.AIR : (r.nextInt(6) == 0 ? Material.CRACKED_STONE_BRICKS : Material.GRAY_CONCRETE));
                    }
        o.ancora(p, 124, b + 2, 161, BlockFace.SOUTH);
        o.ancora(p, 134, b + 2, 162, BlockFace.WEST);
        o.ancora(p, 130, b + 2, 171, BlockFace.NORTH);
        o.ancora(p, 122, b + 2, 169, BlockFace.EAST);
    }

    private void cemiterio(Ponto p, Obra o) {
        int b = p.base;
        Random r = new Random(10);
        for (int x = 23; x <= 37; x++) {
            o.bloco(x, b + 1, 162, Material.DARK_OAK_FENCE);
            if (x != 30 && x != 31) o.bloco(x, b + 1, 174, Material.DARK_OAK_FENCE);
        }
        for (int z = 162; z <= 174; z++) {
            o.bloco(23, b + 1, z, Material.DARK_OAK_FENCE);
            o.bloco(37, b + 1, z, Material.DARK_OAK_FENCE);
        }
        for (int gx : new int[] {25, 28, 31, 34})
            for (int gz : new int[] {169, 172}) {
                o.bloco(gx, b + 1, gz, r.nextBoolean() ? Material.STONE_BRICKS : Material.MOSSY_COBBLESTONE);
                o.bloco(gx, b + 2, gz, Material.STONE_BRICK_SLAB);
                o.bloco(gx, b, gz + 1, Material.COARSE_DIRT);
            }
        for (int x = 26; x <= 33; x++)
            for (int z = 163; z <= 166; z++) {
                for (int y = b + 1; y <= b + 4; y++) {
                    boolean parede = x == 26 || x == 33 || z == 163 || z == 166;
                    o.bloco(x, y, z, parede ? pedra(r) : Material.AIR);
                }
                o.bloco(x, b, z, Material.STONE_BRICKS);
            }
        o.caixa(25, b + 5, 162, 34, b + 5, 167, Material.STONE_BRICK_SLAB);
        o.caixa(29, b + 1, 166, 30, b + 2, 166, Material.AIR);
        o.bloco(27, b + 4, 164, Material.COBWEB);
        o.bloco(32, b + 3, 165, Material.COBWEB);
        o.ancora(p, 28, b + 2, 166, BlockFace.SOUTH);
        o.ancora(p, 33, b + 2, 164, BlockFace.EAST);
        o.ancora(p, 31, b + 2, 163, BlockFace.SOUTH);
    }

    private void acampamento(Ponto p, Obra o) {
        int b = p.base;
        o.dado(128, b + 1, 92, Material.CAMPFIRE.createBlockData("[lit=false]"));
        for (int z = 91; z <= 93; z++) {
            o.dado(125, b + 1, z, eixo(Material.OAK_LOG, 'z'));
            o.dado(131, b + 1, z, eixo(Material.OAK_LOG, 'z'));
        }
        for (int z = 96; z <= 100; z++) {
            o.bloco(121, b + 1, z, Material.WHITE_WOOL);
            o.bloco(123, b + 1, z, Material.WHITE_WOOL);
            o.bloco(122, b + 2, z, Material.WHITE_WOOL);
            o.bloco(133, b + 1, z, Material.GREEN_WOOL);
            o.bloco(135, b + 1, z, Material.GREEN_WOOL);
            o.bloco(134, b + 2, z, Material.GREEN_WOOL);
        }
        o.caixa(126, b + 1, 86, 126, b + 2, 86, Material.DARK_OAK_FENCE);
        o.caixa(130, b + 1, 86, 130, b + 2, 86, Material.DARK_OAK_FENCE);
        o.caixa(126, b + 3, 86, 130, b + 4, 86, Material.SPRUCE_PLANKS);
        o.caixa(133, b + 1, 88, 134, b + 2, 89, Material.BARREL);
        o.ancora(p, 128, b + 3, 86, BlockFace.SOUTH);
        o.ancora(p, 128, b + 4, 86, BlockFace.NORTH);
        o.ancora(p, 133, b + 2, 88, BlockFace.NORTH);
    }

    private void capela(Ponto p, Obra o) {
        int b = p.base;
        Random r = new Random(12);
        for (int x = 57; x <= 67; x++)
            for (int z = 93; z <= 99; z++) o.bloco(x, b, z, r.nextInt(3) == 0 ? Material.COBBLESTONE : Material.STONE_BRICKS);
        for (int x = 57; x <= 67; x++)
            for (int z = 93; z <= 99; z++) {
                boolean parede = x == 57 || x == 67 || z == 93 || z == 99;
                if (!parede) continue;
                int hc = 3 + r.nextInt(4);
                for (int y = 1; y <= hc; y++) o.bloco(x, b + y, z, pedra(r));
            }
        o.caixa(62, b + 1, 99, 62, b + 3, 99, Material.AIR);
        o.caixa(59, b + 3, 93, 59, b + 4, 93, Material.AIR);
        o.caixa(65, b + 3, 93, 65, b + 4, 93, Material.AIR);
        o.caixa(57, b + 1, 93, 58, b + 9, 94, Material.STONE_BRICKS);
        o.bloco(58, b + 9, 94, Material.AIR);
        o.bloco(62, b + 1, 94, Material.CHISELED_STONE_BRICKS);
        o.bloco(62, b + 2, 94, Material.CANDLE);
        for (int z = 96; z <= 97; z++) {
            o.caixa(59, b + 1, z, 60, b + 1, z, Material.SPRUCE_SLAB);
            o.caixa(64, b + 1, z, 65, b + 1, z, Material.SPRUCE_SLAB);
        }
        o.bloco(66, b + 4, 98, Material.COBWEB);
        o.ancora(p, 64, b + 2, 93, BlockFace.SOUTH);
        o.ancora(p, 67, b + 2, 96, BlockFace.EAST);
        o.ancora(p, 57, b + 2, 97, BlockFace.EAST);
    }

    private void onibus(Ponto p, Obra o) {
        int b = p.base;
        Random r = new Random(15);
        for (int x : new int[] {19, 20, 27, 28}) {
            o.bloco(x, b + 1, 23, Material.COAL_BLOCK);
            o.bloco(x, b + 1, 25, Material.COAL_BLOCK);
        }
        for (int x = 17; x <= 30; x++)
            for (int y = b + 2; y <= b + 5; y++)
                for (int z = 23; z <= 25; z++) {
                    Material m = r.nextInt(10) == 0 ? Material.ORANGE_TERRACOTTA : Material.YELLOW_CONCRETE;
                    if (y == b + 4 && (z == 23 || z == 25) && x > 17 && x < 30) m = Material.BLACK_STAINED_GLASS;
                    o.bloco(x, y, z, m);
                }
        o.ancora(p, 21, b + 3, 25, BlockFace.SOUTH);
        o.ancora(p, 26, b + 3, 23, BlockFace.NORTH);
        o.ancora(p, 17, b + 3, 24, BlockFace.WEST);
    }

    private void lobbyObra(Obra o) {
        Random r = new Random(17);
        for (int x = 84; x <= 108; x++)
            for (int z = 192; z <= 213; z++) {
                o.caixa(x, BASE - 3, z, x, BASE - 1, z, Material.DIRT);
                Material m = (x >= 94 && x <= 98) ? Material.DIRT_PATH : (r.nextInt(4) == 0 ? Material.GRAVEL : Material.COARSE_DIRT);
                o.bloco(x, BASE, z, m);
            }
        for (int z = 192; z <= 214; z++) {
            o.caixa(83, BASE + 1, z, 83, BASE + 20, z, Material.BARRIER);
            o.caixa(109, BASE + 1, z, 109, BASE + 20, z, Material.BARRIER);
        }
        o.caixa(83, BASE + 1, 214, 109, BASE + 20, 214, Material.BARRIER);
        o.caixa(88, BASE + 1, 199, 90, BASE + 2, 203, Material.BLUE_CONCRETE);
        for (int[] c : new int[][] {{88, 199}, {90, 199}, {88, 203}, {90, 203}}) o.bloco(c[0], BASE + 1, c[1], Material.COAL_BLOCK);
        o.caixa(88, BASE + 3, 200, 90, BASE + 3, 202, Material.BLACK_STAINED_GLASS);
        for (int[] l : new int[][] {{101, 197}, {92, 209}, {104, 210}}) {
            o.caixa(l[0], BASE + 1, l[1], l[0], BASE + 4, l[1], Material.DARK_OAK_FENCE);
            o.bloco(l[0], BASE + 5, l[1], Material.LANTERN);
        }
        o.caixa(102, BASE + 1, 205, 105, BASE + 1, 205, Material.SPRUCE_SLAB);
    }
}
