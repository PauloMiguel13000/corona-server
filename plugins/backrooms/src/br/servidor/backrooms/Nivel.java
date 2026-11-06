package br.servidor.backrooms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Uma fase das Backrooms. Tudo e calculado a partir de uma semente fixa, entao o gerador de mundo
 * (que monta os blocos quando o chunk carrega) e o jogo (que sabe onde ficam itens, saida etc.) sempre concordam.
 */
abstract class Nivel {
    /** Bloco do chao. Os jogadores pisam em y = 64. */
    static final int PISO = 63;
    static final int Y = 64;

    final int id;
    final String nome;
    final String apelido;
    final int x0, z0, largX, largZ, altura;

    /** Centros das celulas onde da para andar: {x, z, distancia (em celulas) ate o inicio}. */
    final List<int[]> lugares = new ArrayList<>();
    /** Centros das celulas sem luz. */
    final List<int[]> escuros = new ArrayList<>();
    /** Lampadas que piscam: {x, y, z}. */
    final List<int[]> piscando = new ArrayList<>();
    /** Blocos da porta de saida: {x, y, z} (metade de baixo e de cima). */
    final List<int[]> porta = new ArrayList<>();
    final List<BlockData> portaFechada = new ArrayList<>();
    final List<BlockData> portaAberta = new ArrayList<>();
    double inicioX, inicioZ;
    double saidaX, saidaZ;
    Material lampada = Material.SEA_LANTERN, semLuz = Material.WHITE_TERRACOTTA;
    /** Bloco da lampada que pisca, aceso e apagado. */
    BlockData acesa, apagada;

    Nivel(int id, String nome, String apelido, int x0, int z0, int largX, int largZ, int altura) {
        this.id = id;
        this.nome = nome;
        this.apelido = apelido;
        this.x0 = x0;
        this.z0 = z0;
        this.largX = largX;
        this.largZ = largZ;
        this.altura = altura;
    }

    boolean contem(double x, double z) {
        return x >= x0 && x < x0 + largX && z >= z0 && z < z0 + largZ;
    }

    boolean dentro(Location l) {
        return contem(l.getX(), l.getZ()) && l.getY() > PISO - 2 && l.getY() < Y + altura + 1;
    }

    abstract void coluna(ChunkData d, int lx, int lz, int x, int z);

    Biome bioma() {
        return Biome.PALE_GARDEN;
    }

    Location inicio(World w, int i) {
        double[][] off = {{0, 0}, {1.2, 0}, {-1.2, 0}, {0, 1.2}, {0, -1.2}, {1.2, 1.2}, {-1.2, -1.2}, {1.2, -1.2}, {-1.2, 1.2}};
        double[] o = off[Math.floorMod(i, off.length)];
        return new Location(w, inicioX + o[0], Y, inicioZ + o[1], 0, 0);
    }

    Location saida(World w) {
        return new Location(w, saidaX, Y, saidaZ);
    }

    /** Altura dos blocos de luz que apagam e piscam. */
    int yLampada() {
        return Y + altura + 1;
    }

    // ---------------------------------------------------------------- utilidades

    /** Numero pseudo-aleatorio fixo para cada posicao (0..1). */
    static double ruido(long s, int x, int y, int z) {
        long h = s * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + z * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    static BlockData bd(String s) {
        return Bukkit.createBlockData(s);
    }

    static void por(ChunkData d, int lx, int y, int lz, Material m) {
        d.setBlock(lx, y, lz, m);
    }
}

/** Fases feitas de celulas (labirinto com salas abertas): Nivel 0, 1 e 2. */
final class Labirinto extends Nivel {

    enum Estilo { AMARELO, CONCRETO, CANOS, ELETRICA, FESTA, PISCINA, ESCURO }

    final Estilo estilo;
    final int S, n;
    final long semente;
    /** manter = chance de cada parede "extra" (fora do caminho do labirinto) ficar de pe. */
    /** Paredes verticais (linha x = S*i) entre as celulas (i-1, j) e (i, j): 0 aberto, 1 parede, 2 parede com vao. */
    final byte[][] v;
    /** Paredes horizontais (linha z = S*j) entre (i, j-1) e (i, j). */
    final byte[][] h;
    final boolean[][] poste;
    /** 0 sem luz, 1 luz, 2 luz que pisca. */
    final byte[][] luz;
    final boolean[][] sala;
    final boolean[][] caixas;
    final int[][] dist;
    int si, sj, ei, ej;
    boolean saidaEW;

    private final BlockData portaBaixoE, portaCimaE, portaBaixoD, portaCimaD, luzCanos, corrente;

    Labirinto(int id, String nome, String apelido, Estilo estilo, int x0, int z0, int S, int n, int altura, long semente,
              double manter, double pVao, int salas, double pilarSala, boolean postesSempre, double pLuz, double pPisca) {
        super(id, nome, apelido, x0, z0, S * n + 1, S * n + 1, altura);
        this.estilo = estilo;
        this.S = S;
        this.n = n;
        this.semente = semente;
        v = new byte[n + 1][n];
        h = new byte[n][n + 1];
        poste = new boolean[n + 1][n + 1];
        luz = new byte[n][n];
        sala = new boolean[n][n];
        caixas = new boolean[n][n];
        dist = new int[n][n];
        String face = estilo == Estilo.CANOS ? "south" : "south";
        portaBaixoE = bd("minecraft:iron_door[facing=" + face + ",half=lower,hinge=left,open=false]");
        portaCimaE = bd("minecraft:iron_door[facing=" + face + ",half=upper,hinge=left,open=false]");
        portaBaixoD = bd("minecraft:iron_door[facing=" + face + ",half=lower,hinge=right,open=false]");
        portaCimaD = bd("minecraft:iron_door[facing=" + face + ",half=upper,hinge=right,open=false]");
        luzCanos = bd("minecraft:light[level=11]");
        corrente = bd("minecraft:iron_chain[axis=y]");
        switch (estilo) {
            case AMARELO -> {
                lampada = Material.OCHRE_FROGLIGHT;
                semLuz = Material.WHITE_TERRACOTTA;
            }
            case CONCRETO -> {
                lampada = Material.SEA_LANTERN;
                semLuz = Material.GRAY_CONCRETE;
            }
            case CANOS -> {
                lampada = Material.RED_STAINED_GLASS;
                semLuz = Material.DEEPSLATE_TILES;
            }
            case ELETRICA -> {
                lampada = Material.SHROOMLIGHT;
                semLuz = Material.POLISHED_BLACKSTONE;
            }
            case FESTA -> {
                lampada = Material.PEARLESCENT_FROGLIGHT;
                semLuz = Material.WHITE_WOOL;
            }
            case PISCINA -> {
                lampada = Material.SEA_LANTERN;
                semLuz = Material.QUARTZ_BRICKS;
            }
            case ESCURO -> {
                // Nivel 6: quase tudo apagado. As poucas luzes sao fracas (catalisador de sculk, luz 6)
                lampada = Material.SCULK_CATALYST;
                semLuz = Material.BLACK_CONCRETE;
            }
        }
        acesa = estilo == Estilo.CANOS ? luzCanos : lampada.createBlockData();
        apagada = estilo == Estilo.CANOS ? Material.DEEPSLATE.createBlockData() : semLuz.createBlockData();
        gerar(new Random(semente), manter, pVao, salas, pilarSala, postesSempre, pLuz, pPisca);
    }

    // ---------------------------------------------------------------- planta

    private void gerar(Random r, double manter, double pVao, int salas, double pilarSala, boolean postesSempre, double pLuz, double pPisca) {
        for (byte[] a : v) java.util.Arrays.fill(a, (byte) 1);
        for (byte[] a : h) java.util.Arrays.fill(a, (byte) 1);
        // labirinto perfeito (busca em profundidade)
        boolean[][] vis = new boolean[n][n];
        Deque<int[]> pilha = new ArrayDeque<>();
        si = n / 2;
        sj = n / 2;
        pilha.push(new int[] {si, sj});
        vis[si][sj] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean[][] arvoreV = new boolean[n + 1][n], arvoreH = new boolean[n][n + 1];
        while (!pilha.isEmpty()) {
            int[] c = pilha.peek();
            List<int[]> viz = new ArrayList<>();
            for (int[] d : dirs) {
                int a = c[0] + d[0], b = c[1] + d[1];
                if (a >= 0 && b >= 0 && a < n && b < n && !vis[a][b]) viz.add(new int[] {a, b, d[0], d[1]});
            }
            if (viz.isEmpty()) {
                pilha.pop();
                continue;
            }
            int[] e = viz.get(r.nextInt(viz.size()));
            if (e[2] == 1) arvoreV[e[0]][e[1]] = true;
            else if (e[2] == -1) arvoreV[c[0]][c[1]] = true;
            else if (e[3] == 1) arvoreH[e[0]][e[1]] = true;
            else arvoreH[c[0]][c[1]] = true;
            vis[e[0]][e[1]] = true;
            pilha.push(new int[] {e[0], e[1]});
        }
        for (int i = 1; i < n; i++)
            for (int j = 0; j < n; j++) if (arvoreV[i][j] || r.nextDouble() > manter) v[i][j] = 0;
        for (int i = 0; i < n; i++)
            for (int j = 1; j < n; j++) if (arvoreH[i][j] || r.nextDouble() > manter) h[i][j] = 0;
        // saloes abertos
        for (int k = 0; k < salas; k++) {
            int w = 3 + r.nextInt(5), a = 3 + r.nextInt(5);
            int i0 = 1 + r.nextInt(Math.max(1, n - w - 2)), j0 = 1 + r.nextInt(Math.max(1, n - a - 2));
            for (int i = i0; i < i0 + w && i < n; i++)
                for (int j = j0; j < j0 + a && j < n; j++) {
                    sala[i][j] = true;
                    if (i > i0) v[i][j] = 0;
                    if (j > j0) h[i][j] = 0;
                }
        }
        // vaos (portas sem porta) em parte das paredes que sobraram
        for (int i = 1; i < n; i++)
            for (int j = 0; j < n; j++) if (v[i][j] == 1 && r.nextDouble() < pVao) v[i][j] = 2;
        for (int i = 0; i < n; i++)
            for (int j = 1; j < n; j++) if (h[i][j] == 1 && r.nextDouble() < pVao) h[i][j] = 2;
        // postes nos cantos
        for (int i = 0; i <= n; i++)
            for (int j = 0; j <= n; j++) {
                boolean p = postesSempre;
                if (j > 0 && v[i][j - 1] != 0) p = true;
                if (j < n && v[i][j] != 0) p = true;
                if (i > 0 && h[i - 1][j] != 0) p = true;
                if (i < n && h[i][j] != 0) p = true;
                if (!p && i > 0 && j > 0 && i < n && j < n && sala[i - 1][j - 1] && sala[i][j] && r.nextDouble() < pilarSala) p = true;
                poste[i][j] = p;
            }
        // luzes
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (r.nextDouble() < pLuz) luz[i][j] = (byte) (r.nextDouble() < pPisca ? 2 : 1);
                if (estilo == Estilo.CONCRETO && !sala[i][j] && r.nextDouble() < 0.28) caixas[i][j] = true;
                if (estilo == Estilo.ELETRICA && r.nextDouble() < 0.33) caixas[i][j] = true;
                if (estilo == Estilo.FESTA && r.nextDouble() < 0.35) caixas[i][j] = true;
                if (estilo == Estilo.PISCINA && (sala[i][j] || r.nextDouble() < 0.18)) caixas[i][j] = true;
                if (estilo == Estilo.ESCURO && r.nextDouble() < 0.3) caixas[i][j] = true;
            }
        // Nivel 0: algumas partes do mapa ficam no escuro (sorteio proprio: nao muda o resto do desenho)
        if (estilo == Estilo.AMARELO) {
            apagarZonas(new Random(semente * 31 + 7));
            // em volta do comeco fica aceso (para a pessoa se situar antes de entrar no escuro)
            for (int i = Math.max(0, si - 1); i <= Math.min(n - 1, si + 1); i++)
                for (int j = Math.max(0, sj - 1); j <= Math.min(n - 1, sj + 1); j++) if (luz[i][j] == 0) luz[i][j] = 1;
        }
        luz[si][sj] = 1;
        caixas[si][sj] = false;
        // distancias a partir do inicio
        for (int[] a : dist) java.util.Arrays.fill(a, -1);
        Deque<int[]> fila = new ArrayDeque<>();
        fila.add(new int[] {si, sj});
        dist[si][sj] = 0;
        int max = 0;
        while (!fila.isEmpty()) {
            int[] c = fila.poll();
            int i = c[0], j = c[1], d = dist[i][j];
            max = Math.max(max, d);
            if (i + 1 < n && v[i + 1][j] != 1 && dist[i + 1][j] < 0) { dist[i + 1][j] = d + 1; fila.add(new int[] {i + 1, j}); }
            if (i - 1 >= 0 && v[i][j] != 1 && dist[i - 1][j] < 0) { dist[i - 1][j] = d + 1; fila.add(new int[] {i - 1, j}); }
            if (j + 1 < n && h[i][j + 1] != 1 && dist[i][j + 1] < 0) { dist[i][j + 1] = d + 1; fila.add(new int[] {i, j + 1}); }
            if (j - 1 >= 0 && h[i][j] != 1 && dist[i][j - 1] < 0) { dist[i][j - 1] = d + 1; fila.add(new int[] {i, j - 1}); }
        }
        // saida: longe do inicio (para os canos, num beco sem saida)
        List<int[]> cand = new ArrayList<>();
        boolean beco = estilo == Estilo.CANOS || estilo == Estilo.ELETRICA;
        int minimo = (int) (max * (beco ? 0.45 : 0.7)), teto = (int) (max * (beco ? 0.8 : 1.0));
        for (int i = 1; i < n - 1; i++)
            for (int j = 1; j < n - 1; j++) {
                if (dist[i][j] < minimo || dist[i][j] > teto || sala[i][j]) continue;
                if (beco && abertos(i, j) != 1) continue;
                cand.add(new int[] {i, j});
            }
        if (cand.isEmpty())
            for (int i = 1; i < n - 1; i++)
                for (int j = 1; j < n - 1; j++) if (dist[i][j] >= max / 2) cand.add(new int[] {i, j});
        int[] s = cand.get(r.nextInt(cand.size()));
        ei = s[0];
        ej = s[1];
        luz[ei][ej] = 1;
        caixas[ei][ej] = false;
        if (beco) {
            // corredor anda em x (aberto a leste ou oeste): a porta fica atravessada em z
            saidaEW = v[ei][ej] != 1 || v[ei + 1][ej] != 1;
        }
        // dados para o jogo
        double c = (S + 1) / 2.0;
        inicioX = x0 + S * si + c;
        inicioZ = z0 + S * sj + c;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (dist[i][j] < 0 || i == ei && j == ej) continue;
                int cx = x0 + S * i + S / 2, cz = z0 + S * j + S / 2;
                lugares.add(new int[] {cx, cz, dist[i][j]});
                if (luz[i][j] == 0) escuros.add(new int[] {cx, cz, dist[i][j]});
                int yl = estilo == Estilo.CANOS ? Y + altura + 1 : Y + altura;
                if (luz[i][j] == 2) for (int[] o : lampadas()) piscando.add(new int[] {x0 + S * i + o[0], yl, z0 + S * j + o[1]});
            }
        montarPorta();
    }

    /**
     * Manchas sem luz no meio do saguao amarelo (da mais medo): 14 manchas de 2,5 a 4,5 celulas de raio, longe do
     * comeco. Na borda de cada mancha as lampadas ficam piscando. O comeco e a saida continuam acesos.
     */
    static int MANCHAS = 14;

    private void apagarZonas(Random rz) {
        int feitas = 0;
        for (int tentativa = 0; feitas < MANCHAS && tentativa < 300; tentativa++) {
            int ci = 2 + rz.nextInt(n - 4), cj = 2 + rz.nextInt(n - 4);
            double raio = 2.5 + rz.nextDouble() * 2.0;
            if (Math.hypot(ci - si, cj - sj) < raio + 6) continue;
            feitas++;
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++) {
                    double d = Math.hypot(i - ci, j - cj);
                    if (d <= raio) luz[i][j] = 0;
                    else if (d <= raio + 1.3 && luz[i][j] == 1) luz[i][j] = 2;
                }
        }
    }

    private int abertos(int i, int j) {
        int k = 0;
        if (v[i][j] != 1) k++;
        if (v[i + 1][j] != 1) k++;
        if (h[i][j] != 1) k++;
        if (h[i][j + 1] != 1) k++;
        return k;
    }

    /** Onde ficam as lampadas dentro da celula (deslocamentos gx, gz). */
    private int[][] lampadas() {
        return S >= 7 ? new int[][] {{3, 3}, {4, 3}} : new int[][] {{2, 2}};
    }

    private void montarPorta() {
        int bx = x0 + S * ei, bz = z0 + S * ej;
        if (S >= 7) {
            porta.add(new int[] {bx + 3, Y, bz + 3});
            portaFechada.add(portaBaixoE);
            porta.add(new int[] {bx + 3, Y + 1, bz + 3});
            portaFechada.add(portaCimaE);
            porta.add(new int[] {bx + 4, Y, bz + 3});
            portaFechada.add(portaBaixoD);
            porta.add(new int[] {bx + 4, Y + 1, bz + 3});
            portaFechada.add(portaCimaD);
            saidaX = bx + 4.0;
            saidaZ = bz + 3.5;
        } else {
            String f = saidaEW ? "east" : "south";
            porta.add(new int[] {bx + 2, Y, bz + 2});
            portaFechada.add(bd("minecraft:iron_door[facing=" + f + ",half=lower,hinge=left,open=false]"));
            porta.add(new int[] {bx + 2, Y + 1, bz + 2});
            portaFechada.add(bd("minecraft:iron_door[facing=" + f + ",half=upper,hinge=left,open=false]"));
            saidaX = bx + 2.5;
            saidaZ = bz + 2.5;
        }
        for (BlockData b : portaFechada) {
            BlockData a = b.clone();
            if (a instanceof org.bukkit.block.data.Openable o) o.setOpen(true);
            portaAberta.add(a);
        }
    }

    @Override
    Biome bioma() {
        return estilo == Estilo.CANOS ? Biome.DEEP_DARK : estilo == Estilo.ELETRICA ? Biome.BASALT_DELTAS
                : estilo == Estilo.PISCINA ? Biome.DEEP_COLD_OCEAN : estilo == Estilo.ESCURO ? Biome.DEEP_DARK : Biome.PALE_GARDEN;
    }

    // ---------------------------------------------------------------- blocos

    private boolean vao(int o) {
        return S >= 7 ? o == 3 || o == 4 : o == 2;
    }

    @Override
    void coluna(ChunkData d, int lx, int lz, int x, int z) {
        int rx = x - x0, rz = z - z0;
        if (rx < 0 || rz < 0 || rx > S * n || rz > S * n) return;
        int gi = rx / S, gx = rx % S, gj = rz / S, gz = rz % S;
        boolean naV = gx == 0, naH = gz == 0;
        if (naV && naH) {
            if (poste[gi][gj]) solida(d, lx, lz, x, z, true);
            else aberta(d, lx, lz, x, z, false);
            return;
        }
        if (naV || naH) {
            byte w = naV ? v[gi][gj] : h[gi][gj];
            int o = naV ? gz : gx;
            if (w == 0) aberta(d, lx, lz, x, z, false);
            else if (w == 2 && vao(o)) {
                aberta(d, lx, lz, x, z, false);
                for (int y = Y + 3; y < Y + altura; y++) por(d, lx, y, lz, parede(x, y, z));
            } else solida(d, lx, lz, x, z, false);
            return;
        }
        aberta(d, lx, lz, x, z, false);
        // lampadas no teto
        boolean ehSaida = gi == ei && gj == ej;
        if (luz[gi][gj] != 0) {
            for (int[] o : lampadas()) {
                int oz = ehSaida && S >= 7 ? o[1] + 2 : o[1];
                if (gx == o[0] && gz == oz) {
                    por(d, lx, Y + altura, lz, lampada);
                    if (estilo == Estilo.CANOS) d.setBlock(lx, Y + altura + 1, lz, luzCanos);
                }
            }
        }
        // a porta de saida (moldura no meio da celula)
        if (ehSaida) {
            portaColuna(d, lx, lz, x, z, gx, gz);
            return;
        }
        // enfeites
        if (estilo == Estilo.CONCRETO && caixas[gi][gj]) {
            boolean canto = gx == 1 && gz == 1 || gx == 1 && gz == 2 || gx == 2 && gz == 1 || gx == S - 1 && gz == S - 1;
            if (canto) {
                double q = ruido(semente, x, 7, z);
                por(d, lx, Y, lz, q < 0.5 ? Material.SPRUCE_PLANKS : Material.STRIPPED_SPRUCE_WOOD);
                if (q < 0.35) por(d, lx, Y + 1, lz, Material.SPRUCE_PLANKS);
            }
        }
        if (estilo == Estilo.CANOS && (gx == 2 && gz == 2) && luz[gi][gj] == 0 && ruido(semente, gi, 3, gj) < 0.3)
            d.setBlock(lx, Y + altura - 1, lz, corrente);
        if (estilo == Estilo.ELETRICA) maquinas(d, lx, lz, x, z, gi, gj, gx, gz);
        if (estilo == Estilo.FESTA && caixas[gi][gj]) festa(d, lx, lz, x, z, gi, gj, gx, gz);
        if (estilo == Estilo.ESCURO && caixas[gi][gj] && !sala[gi][gj]) escritorio(d, lx, lz, gi, gj, gx, gz);
        if (estilo == Estilo.PISCINA && caixas[gi][gj] && gx >= 2 && gz >= 2 && gx <= S - 2 && gz <= S - 2 && !(gi == si && gj == sj)) {
            // piscina rasa no meio da sala: agua no nivel do chao, fundo azul (barulhenta de atravessar)
            d.setBlock(lx, PISO, lz, Material.WATER.createBlockData());
            por(d, lx, PISO - 1, lz, (gx + gz) % 2 == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.CYAN_TERRACOTTA);
            por(d, lx, PISO - 2, lz, Material.WHITE_CONCRETE);
        }
    }

    /** Nivel 6: escritorio abandonado no escuro (mesas e arquivos encostados nos cantos da celula). */
    private void escritorio(ChunkData d, int lx, int lz, int gi, int gj, int gx, int gz) {
        double q = ruido(semente, gi, 13, gj);
        if (gx == 1 && gz == 1) d.setBlock(lx, Y, lz, bd("minecraft:dark_oak_slab[type=top]"));
        else if (gx == S - 1 && gz == 1 && q < 0.6) {
            por(d, lx, Y, lz, Material.BARREL);
            if (q < 0.3) por(d, lx, Y + 1, lz, Material.BARREL);
        } else if (gx == 1 && gz == S - 1 && q > 0.5) por(d, lx, Y, lz, Material.BOOKSHELF);
    }

    /** Nivel Fun: bolos com vela acesa, presentes e baloes (la em cima) encostados nas paredes da celula. */
    private void festa(ChunkData d, int lx, int lz, int x, int z, int gi, int gj, int gx, int gz) {
        double q = ruido(semente, gi, 11, gj);
        if (gx == 1 && gz == 1) {
            if (q < 0.5) d.setBlock(lx, Y, lz, bd("minecraft:candle_cake[lit=true]"));
            else por(d, lx, Y, lz, q < 0.75 ? Material.RED_SHULKER_BOX : Material.LIME_SHULKER_BOX);
        }
        if (gx == S - 1 && gz == 1 && q > 0.3) por(d, lx, Y, lz, q < 0.6 ? Material.MAGENTA_SHULKER_BOX : Material.YELLOW_SHULKER_BOX);
        if ((gx == 1 && gz == S - 1 || gx == S - 1 && gz == S - 1) && q > 0.2) {
            // balao: barbante (corrente) descendo do teto ate uma bola colorida
            Material[] cor = {Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIGHT_BLUE_WOOL, Material.LIME_WOOL, Material.MAGENTA_WOOL};
            por(d, lx, Y + altura - 2, lz, cor[(int) Math.floorMod(x * 7 + z * 13, cor.length)]);
            d.setBlock(lx, Y + altura - 1, lz, corrente);
        }
    }

    /**
     * Nivel 3 (Estacao Eletrica): quadros de energia, geradores e cabos. Fica tudo encostado nas paredes da celula
     * (nunca no meio do caminho) e no maximo 2 blocos de altura.
     */
    private void maquinas(ChunkData d, int lx, int lz, int x, int z, int gi, int gj, int gx, int gz) {
        // cabos pendurados no teto
        if (gx == 2 && gz == 1 && ruido(semente, gi, 5, gj) < 0.45) {
            d.setBlock(lx, Y + altura - 1, lz, corrente);
            if (ruido(semente, gi, 6, gj) < 0.5) d.setBlock(lx, Y + altura - 2, lz, corrente);
        }
        if (!caixas[gi][gj] || sala[gi][gj]) return;
        double q = ruido(semente, gi, 9, gj);
        boolean encostado = (gx == 1 || gx == S - 1) && (gz == 1 || gz == S - 1);
        if (!encostado) return;
        if (gx == 1 && gz == 1) {
            // gerador: alto-forno com para-raios em cima
            por(d, lx, Y, lz, q < 0.5 ? Material.BLAST_FURNACE : Material.SMOKER);
            por(d, lx, Y + 1, lz, Material.LIGHTNING_ROD);
        } else if (gx == S - 1 && gz == 1) {
            // quadro de energia
            por(d, lx, Y, lz, Material.WAXED_OXIDIZED_CUT_COPPER);
            por(d, lx, Y + 1, lz, q < 0.4 ? Material.WAXED_COPPER_BULB : Material.DROPPER);
        } else if (gx == 1 && gz == S - 1) {
            por(d, lx, Y, lz, Material.IRON_BLOCK);
            por(d, lx, Y + 1, lz, Material.IRON_BARS);
        } else if (q < 0.5) {
            por(d, lx, Y, lz, Material.DISPENSER);
        }
    }

    private void portaColuna(ChunkData d, int lx, int lz, int x, int z, int gx, int gz) {
        Material moldura = switch (estilo) {
            case AMARELO -> Material.STRIPPED_BIRCH_WOOD;
            case CONCRETO -> Material.IRON_BLOCK;
            case CANOS -> Material.WAXED_OXIDIZED_COPPER;
            case ELETRICA -> Material.WAXED_EXPOSED_CUT_COPPER;
            case FESTA -> Material.STRIPPED_CHERRY_WOOD;
            case PISCINA -> Material.SMOOTH_QUARTZ;
            case ESCURO -> Material.STRIPPED_DARK_OAK_LOG;
        };
        if (S >= 7) {
            if (gz != 3) return;
            if (gx == 2 || gx == 5) for (int y = Y; y < Y + altura; y++) por(d, lx, y, lz, moldura);
            else if (gx == 3 || gx == 4) {
                d.setBlock(lx, Y, lz, gx == 3 ? portaBaixoE : portaBaixoD);
                d.setBlock(lx, Y + 1, lz, gx == 3 ? portaCimaE : portaCimaD);
                for (int y = Y + 2; y < Y + altura; y++) por(d, lx, y, lz, moldura);
            }
        } else {
            int a = saidaEW ? gx : gz, b = saidaEW ? gz : gx;
            if (a != 2) return;
            if (b == 1 || b == 3) for (int y = Y; y < Y + altura; y++) por(d, lx, y, lz, moldura);
            else if (b == 2) {
                d.setBlock(lx, Y, lz, portaFechada.get(0));
                d.setBlock(lx, Y + 1, lz, portaFechada.get(1));
                for (int y = Y + 2; y < Y + altura; y++) por(d, lx, y, lz, moldura);
            }
        }
    }

    private void aberta(ChunkData d, int lx, int lz, int x, int z, boolean x_) {
        por(d, lx, PISO - 1, lz, subpiso());
        por(d, lx, PISO, lz, piso(x, z));
        por(d, lx, Y + altura, lz, teto(x, z));
        por(d, lx, Y + altura + 1, lz, subpiso());
        if (estilo == Estilo.CANOS) por(d, lx, Y + altura + 2, lz, subpiso());
    }

    private void solida(ChunkData d, int lx, int lz, int x, int z, boolean ehPoste) {
        por(d, lx, PISO - 1, lz, subpiso());
        por(d, lx, PISO, lz, piso(x, z));
        for (int y = Y; y < Y + altura; y++) por(d, lx, y, lz, ehPoste ? posteMat(x, y, z) : parede(x, y, z));
        por(d, lx, Y + altura, lz, teto(x, z));
        por(d, lx, Y + altura + 1, lz, subpiso());
        if (estilo == Estilo.CANOS) por(d, lx, Y + altura + 2, lz, subpiso());
    }

    private Material subpiso() {
        return switch (estilo) {
            case AMARELO -> Material.SMOOTH_SANDSTONE;
            case CONCRETO -> Material.STONE;
            case CANOS -> Material.DEEPSLATE;
            case ELETRICA -> Material.BLACKSTONE;
            case FESTA -> Material.WHITE_WOOL;
            case PISCINA -> Material.WHITE_CONCRETE;
            case ESCURO -> Material.BLACKSTONE;
        };
    }

    private Material piso(int x, int z) {
        double q = ruido(semente, x, 1, z);
        return switch (estilo) {
            case AMARELO -> q < 0.03 ? Material.BROWN_TERRACOTTA : q < 0.08 ? Material.ORANGE_TERRACOTTA : Material.YELLOW_TERRACOTTA;
            case CONCRETO -> q < 0.06 ? Material.LIGHT_GRAY_CONCRETE : q < 0.10 ? Material.POLISHED_ANDESITE : q < 0.12 ? Material.CYAN_TERRACOTTA : Material.GRAY_CONCRETE;
            case CANOS -> q < 0.08 ? Material.COBBLED_DEEPSLATE : q < 0.5 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE;
            case ELETRICA -> q < 0.12 ? Material.WAXED_OXIDIZED_COPPER_GRATE : q < 0.2 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS
                    : q < 0.6 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE;
            case FESTA -> q < 0.03 ? Material.PINK_WOOL : q < 0.05 ? Material.BROWN_WOOL : Material.RED_WOOL;
            case PISCINA -> ((x + z) & 1) == 0 ? Material.WHITE_CONCRETE : (q < 0.04 ? Material.LIGHT_GRAY_CONCRETE : Material.QUARTZ_BRICKS);
            // carpete velho de escritorio, com manchas
            case ESCURO -> q < 0.03 ? Material.BROWN_WOOL : q < 0.07 ? Material.RED_TERRACOTTA : q < 0.55 ? Material.GRAY_WOOL : Material.BLACK_WOOL;
        };
    }

    private Material teto(int x, int z) {
        double q = ruido(semente, x, 2, z);
        return switch (estilo) {
            case AMARELO -> q < 0.04 ? Material.LIGHT_GRAY_TERRACOTTA : Material.WHITE_TERRACOTTA;
            case CONCRETO -> q < 0.06 ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE;
            case CANOS -> q < 0.3 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            case ELETRICA -> q < 0.25 ? Material.IRON_BLOCK : Material.POLISHED_BLACKSTONE;
            case FESTA -> q < 0.05 ? Material.PINK_WOOL : Material.WHITE_WOOL;
            case PISCINA -> Material.QUARTZ_BRICKS;
            case ESCURO -> q < 0.05 ? Material.GRAY_CONCRETE : Material.BLACK_CONCRETE;
        };
    }

    private Material parede(int x, int y, int z) {
        double q = ruido(semente, x, y, z);
        return switch (estilo) {
            case AMARELO -> y == Y ? Material.CUT_SANDSTONE : q < 0.04 ? Material.SANDSTONE : Material.SMOOTH_SANDSTONE;
            case CONCRETO -> y == Y ? Material.GRAY_CONCRETE : q < 0.05 ? Material.SMOOTH_STONE : Material.LIGHT_GRAY_CONCRETE;
            case CANOS -> y == Y ? Material.POLISHED_DEEPSLATE : y == Y + 2 ? (q < 0.5 ? Material.WAXED_EXPOSED_COPPER : Material.WAXED_WEATHERED_COPPER)
                    : q < 0.1 ? Material.CRACKED_DEEPSLATE_BRICKS : Material.DEEPSLATE_BRICKS;
            case ELETRICA -> y == Y ? Material.POLISHED_BLACKSTONE_BRICKS
                    : y == Y + 2 ? (q < 0.3 ? Material.WAXED_OXIDIZED_CUT_COPPER : Material.GRAY_CONCRETE)
                    : q < 0.06 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : q < 0.12 ? Material.TUFF_BRICKS : Material.GRAY_CONCRETE;
            // Nivel Fun: parede branca com faixa de bandeirinhas coloridas e rodape vermelho
            case FESTA -> y == Y ? Material.RED_TERRACOTTA
                    : y == Y + 2 ? switch (Math.floorMod(x + z, 4)) {
                        case 0 -> Material.YELLOW_CONCRETE;
                        case 1 -> Material.LIGHT_BLUE_CONCRETE;
                        case 2 -> Material.PINK_CONCRETE;
                        default -> Material.LIME_CONCRETE;
                    }
                    : q < 0.03 ? Material.PINK_TERRACOTTA : Material.WHITE_TERRACOTTA;
            // Nivel 37: azulejo branco por todo lado
            case PISCINA -> y == Y ? Material.LIGHT_BLUE_TERRACOTTA : ((x + z + y) & 1) == 0 ? Material.WHITE_CONCRETE : Material.QUARTZ_BRICKS;
            // Nivel 6: parede escura com marcas (de unhas?) de vez em quando
            case ESCURO -> y == Y ? Material.POLISHED_BLACKSTONE : y == Y + 2 ? (q < 0.25 ? Material.GRAY_TERRACOTTA : Material.BLACK_TERRACOTTA)
                    : q < 0.04 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : q < 0.07 ? Material.RED_TERRACOTTA : Material.BLACK_TERRACOTTA;
        };
    }

    private Material posteMat(int x, int y, int z) {
        return switch (estilo) {
            case AMARELO -> parede(x, y, z);
            case CONCRETO -> Material.POLISHED_ANDESITE;
            case CANOS -> Material.POLISHED_BASALT;
            case ELETRICA -> y == Y + 1 ? Material.WAXED_EXPOSED_CUT_COPPER : Material.POLISHED_BASALT;
            case FESTA -> parede(x, y, z);
            case PISCINA -> Material.SMOOTH_QUARTZ;
            case ESCURO -> Material.POLISHED_BASALT;
        };
    }

    @Override
    int yLampada() {
        return estilo == Estilo.CANOS ? Y + altura + 1 : Y + altura;
    }
}

/** Nivel ! : um corredor comprido. Algo vem atras de voce. Corra. */
final class Corredor extends Nivel {
    static final int L = 640, W = 7;
    final long semente;
    /**
     * Obstaculo em cada x (relativo): 0 nenhum, 1 parede esquerda, 2 parede direita, 3 mureta (pular), 4 dois pilares,
     * 6 fenda na esquerda, 7 fenda na direita, 8 fenda no meio (parede inteira com um buraco de 1 bloco),
     * 9 grade de ferro (passagem de 2 blocos no meio), 10 mesa (pula ou contorna pelos lados),
     * 11 entulho (barris e estante: passa pelos vaos de 1 bloco ou pula os barris).
     */
    final byte[] obst = new byte[L + 1];
    final List<Integer> pontos = new ArrayList<>();
    private final BlockData grade, gradeN, gradeS, mesa;

    Corredor(int id, String nome, String apelido, int x0, int z0, long semente) {
        super(id, nome, apelido, x0, z0, L + 1, W, 5);
        this.semente = semente;
        Random r = new Random(semente);
        grade = bd("minecraft:iron_bars[north=true,south=true]");
        gradeN = bd("minecraft:iron_bars[north=true,south=false]");
        gradeS = bd("minecraft:iron_bars[north=false,south=true]");
        mesa = bd("minecraft:dark_oak_slab[type=top]");
        // 5 = zigue-zague (parede de um lado e, 4 blocos depois, do outro)
        int[] tipos = {1, 3, 9, 5, 10, 8, 4, 11, 3, 5, 9, 6, 1, 10, 7, 11};
        for (int x = 34, k = 0; x < L - 30; x += 11 + r.nextInt(8), k++) {
            int t = tipos[(k + r.nextInt(3)) % tipos.length];
            if (t == 5) {
                boolean esq = r.nextBoolean();
                obst[x] = (byte) (esq ? 1 : 2);
                obst[x + 4] = (byte) (esq ? 2 : 1);
                x += 4;
            } else obst[x] = (byte) t;
        }
        for (int x = 160; x < L - 40; x += 160) {
            pontos.add(x);
            obst[x] = 0;
            obst[x + 1] = 0;
        }
        inicioX = x0 + 20.5;
        inicioZ = z0 + 3.5;
        saidaX = x0 + L - 5.5;
        saidaZ = z0 + 3.5;
        lampada = Material.SHROOMLIGHT;
        acesa = Material.SHROOMLIGHT.createBlockData();
        apagada = Material.STONE.createBlockData();
        for (int x = 4; x < L; x += 8) piscando.add(new int[] {x0 + x, Y + altura + 1, z0 + 3});
    }

    /** Ponto de volta (checkpoint) mais adiantado que esteja pelo menos em "minimo". */
    double pontoDepois(double minimo) {
        double melhor = -1;
        for (int p : pontos) if (x0 + p + 0.5 >= minimo) return x0 + p + 0.5;
        return melhor;
    }

    @Override
    Location inicio(World w, int i) {
        double[] zs = {3.5, 2.5, 4.5, 1.5, 5.5};
        return new Location(w, inicioX + (i / 5) * 1.5, Y, z0 + zs[i % 5], -90, 0);
    }

    @Override
    void coluna(ChunkData d, int lx, int lz, int x, int z) {
        int rx = x - x0, rz = z - z0;
        if (rx < 0 || rz < 0 || rx > L || rz >= W) return;
        por(d, lx, PISO - 1, lz, Material.STONE);
        por(d, lx, PISO, lz, rx > L - 12 ? Material.YELLOW_TERRACOTTA : ruido(semente, x, 1, z) < 0.07 ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE);
        int topo = Y + altura;
        boolean paredeLado = rz == 0 || rz == W - 1, fundo = rx == 0 || rx == L;
        if (paredeLado || fundo) {
            for (int y = Y; y < topo; y++) por(d, lx, y, lz, y == Y + 2 ? Material.RED_CONCRETE : rx > L - 12 ? Material.SMOOTH_SANDSTONE : Material.WHITE_CONCRETE);
            por(d, lx, topo, lz, Material.LIGHT_GRAY_CONCRETE);
            por(d, lx, topo + 1, lz, Material.STONE);
            return;
        }
        // teto: lampadas vermelhas (vidro vermelho com luz em cima) e, no fim, a sala amarela iluminada
        boolean lamp = rx % 8 == 4 && rz == 3;
        if (rx > L - 12) {
            por(d, lx, topo, lz, rz == 3 && rx % 3 == 0 ? Material.OCHRE_FROGLIGHT : Material.WHITE_TERRACOTTA);
            por(d, lx, topo + 1, lz, Material.STONE);
        } else if (lamp) {
            por(d, lx, topo, lz, Material.RED_STAINED_GLASS);
            por(d, lx, topo + 1, lz, Material.SHROOMLIGHT);
            por(d, lx, topo + 2, lz, Material.STONE);
        } else {
            por(d, lx, topo, lz, Material.LIGHT_GRAY_CONCRETE);
            por(d, lx, topo + 1, lz, Material.STONE);
        }
        // obstaculos
        switch (obst[rx]) {
            case 1 -> { if (rz <= 3) for (int y = Y; y < topo; y++) por(d, lx, y, lz, Material.WHITE_CONCRETE); }
            case 2 -> { if (rz >= 3) for (int y = Y; y < topo; y++) por(d, lx, y, lz, Material.WHITE_CONCRETE); }
            case 3 -> por(d, lx, Y, lz, Material.RED_TERRACOTTA);
            case 4 -> { if (rz == 2 || rz == 4) for (int y = Y; y < topo; y++) por(d, lx, y, lz, Material.QUARTZ_PILLAR); }
            case 6 -> { if (rz != 1) for (int y = Y; y < topo; y++) por(d, lx, y, lz, Material.WHITE_CONCRETE); }
            case 7 -> { if (rz != 5) for (int y = Y; y < topo; y++) por(d, lx, y, lz, Material.WHITE_CONCRETE); }
            case 8 -> { if (rz != 3) for (int y = Y; y < topo; y++) por(d, lx, y, lz, Material.WHITE_CONCRETE); }
            case 9 -> {
                // grade de ferro de cima a baixo nos lados; passagem de 2 blocos no meio (rz 3 e 4)
                BlockData g = rz == 1 ? grade : rz == 2 ? gradeN : rz == 5 ? gradeS : null;
                if (g != null) for (int y = Y; y < topo; y++) d.setBlock(lx, y, lz, g);
            }
            case 10 -> { if (rz >= 2 && rz <= 4) d.setBlock(lx, Y, lz, mesa); }
            case 11 -> {
                if (rz == 1 || rz == 5) por(d, lx, Y, lz, Material.BARREL);
                else if (rz == 3) { por(d, lx, Y, lz, Material.BOOKSHELF); por(d, lx, Y + 1, lz, Material.BOOKSHELF); }
            }
            default -> { }
        }
        // marcas no chao nos pontos de volta
        for (int p : pontos) if (rx == p) por(d, lx, PISO, lz, Material.LIME_TERRACOTTA);
    }
}

/** Sala de espera (antes da partida). */
final class Espera {
    static final int X1 = -60, X2 = -40, Z1 = 0, Z2 = 20;

    static boolean contem(int x, int z) {
        return x >= X1 && x <= X2 && z >= Z1 && z <= Z2;
    }

    static Location spawn(World w) {
        return new Location(w, -50.0, Nivel.Y, 13.0, 180, 0);
    }

    static Location placa(World w) {
        return new Location(w, -50.0, Nivel.Y + 2.2, 4.5);
    }

    static void coluna(ChunkData d, int lx, int lz, int x, int z) {
        Nivel.por(d, lx, Nivel.PISO - 1, lz, Material.SMOOTH_SANDSTONE);
        Nivel.por(d, lx, Nivel.PISO, lz, Material.YELLOW_TERRACOTTA);
        boolean borda = x == X1 || x == X2 || z == Z1 || z == Z2;
        if (borda) for (int y = Nivel.Y; y < Nivel.Y + 4; y++) Nivel.por(d, lx, y, lz, y == Nivel.Y ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE);
        boolean lamp = !borda && Math.floorMod(x - X1, 5) == 2 && Math.floorMod(z - Z1, 5) == 2;
        Nivel.por(d, lx, Nivel.Y + 4, lz, lamp ? Material.OCHRE_FROGLIGHT : Material.WHITE_TERRACOTTA);
        Nivel.por(d, lx, Nivel.Y + 5, lz, Material.SMOOTH_SANDSTONE);
    }

    private Espera() {}
}

/** Todas as fases, sempre iguais (semente fixa). */
final class Mapa {
    /** Mude quando o desenho das fases mudar: o mundo e apagado e gerado de novo. */
    static final int VERSAO = 9; // 9: Nivel 6 (Luzes Apagadas); 8: Nivel Fun e Nivel 37 (Piscinas)
    static final String MUNDO = "backrooms";

    final List<Nivel> niveis = new ArrayList<>();

    Mapa() {
        niveis.add(new Labirinto(0, "Nível 0", "O Saguão Amarelo", Labirinto.Estilo.AMARELO, 0, 0, 7, 46, 4, 1101L,
                0.80, 0.35, 14, 0.5, false, 0.62, 0.25));
        niveis.add(new Labirinto(1, "Nível 1", "A Garagem Sem Fim", Labirinto.Estilo.CONCRETO, 500, 0, 7, 44, 5, 2202L,
                0.45, 0.35, 10, 1.0, true, 0.38, 0.30));
        niveis.add(new Labirinto(2, "Nível 2", "Os Canos", Labirinto.Estilo.CANOS, 1000, 0, 4, 48, 4, 3303L,
                0.86, 0.0, 0, 0, false, 0.20, 0.25));
        niveis.add(new Labirinto(3, "Nível 3", "A Estação Elétrica", Labirinto.Estilo.ELETRICA, 2200, 0, 4, 48, 4, 5505L,
                0.82, 0.25, 4, 0.0, false, 0.30, 0.35));
        niveis.add(new Labirinto(4, "Nível Fun", "=) Não Pare de Sorrir", Labirinto.Estilo.FESTA, 2700, 0, 5, 40, 4, 6606L,
                0.78, 0.3, 8, 0.0, false, 0.75, 0.35));
        niveis.add(new Labirinto(5, "Nível 37", "As Piscinas", Labirinto.Estilo.PISCINA, 3200, 0, 7, 32, 5, 7707L,
                0.55, 0.35, 12, 0.0, true, 0.85, 0.15));
        niveis.add(new Labirinto(6, "Nível 6", "Luzes Apagadas", Labirinto.Estilo.ESCURO, 3800, 0, 7, 32, 4, 8808L,
                0.72, 0.30, 6, 0.0, false, 0.07, 0.6));
        // a corrida e sempre a ultima (o indice tem que ser igual ao id)
        niveis.add(new Corredor(7, "Nível !", "Corra!", 1400, 0, 4404L));
    }

    Nivel em(double x, double z) {
        for (Nivel n : niveis) if (n.contem(x, z)) return n;
        return null;
    }

    static List<int[]> embaralhado(List<int[]> l, Random r) {
        List<int[]> c = new ArrayList<>(l);
        Collections.shuffle(c, r);
        return c;
    }
}
