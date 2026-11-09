package br.servidor.fabrica;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/**
 * Confere se a fabrica e jogavel A PE (nao por teleporte): busca em largura pelas celulas onde um jogador cabe (corpo de
 * 2 blocos livre, chao firme), andando, subindo 1 bloco com pulo e descendo ate 3. Abre as portas, ve se tudo e
 * alcancavel a partir do spawn; depois fecha cada porta e ve se ela bloqueia o que deve.
 */
final class Verificador {

    private final Planta pt;
    private final World w;
    private final List<String> rel = new ArrayList<>();
    private static final int X1 = -82, X2 = 37, Z1 = -2, Z2 = 97, Y1 = Planta.Y - 12, Y2 = Planta.Y + 14;

    Verificador(Planta pt) {
        this.pt = pt;
        this.w = pt.mundo();
    }

    /** Altura (0..1+) do topo da colisao do bloco; 0 = nao colide. */
    private double topo(int x, int y, int z) {
        Block b = pt.bloco(x, y, z);
        if (b.isPassable()) return 0;
        double max = 0;
        for (BoundingBox bb : b.getCollisionShape().getBoundingBoxes()) max = Math.max(max, bb.getMaxY());
        return max;
    }

    private boolean livre(int x, int y, int z) {
        return topo(x, y, z) == 0;
    }

    /** O jogador fica em pe com os pes em y: corpo (y, y+1) livre, ou pes sobre bloco baixo (tapete, laje). */
    private boolean cabe(int x, int y, int z) {
        double pe = topo(x, y, z);
        if (pe > 0.5) return false;
        if (!livre(x, y + 1, z)) return false;
        if (pe > 0) return livre(x, y + 2, z) || pe < 0.1;
        return topo(x, y - 1, z) > 0;     // chao firme logo abaixo
    }

    private static long k(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    Set<Long> alcance(int sx, int sy, int sz) {
        Set<Long> vis = new HashSet<>();
        ArrayDeque<int[]> fila = new ArrayDeque<>();
        if (!cabe(sx, sy, sz)) return vis;
        vis.add(k(sx, sy, sz));
        fila.add(new int[]{sx, sy, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!fila.isEmpty() && vis.size() < 400000) {
            int[] c = fila.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                if (nx < X1 || nx > X2 || nz < Z1 || nz > Z2) continue;
                // mesmo nivel, pulo (+1, precisa de espaco em cima), queda (ate 3)
                for (int dy = 1; dy >= -3; dy--) {
                    int ny = c[1] + dy;
                    if (ny < Y1 || ny > Y2) continue;
                    if (dy == 1 && !livre(c[0], c[1] + 2, c[2])) continue;
                    if (dy < 0) {
                        boolean caminho = true;
                        for (int yy = ny + 1; yy <= c[1] + 1; yy++) if (!livre(nx, yy, nz)) caminho = false;
                        if (!caminho) continue;
                    }
                    if (cabe(nx, ny, nz)) {
                        long kk = k(nx, ny, nz);
                        if (vis.add(kk)) fila.add(new int[]{nx, ny, nz});
                        break;
                    }
                }
            }
        }
        return vis;
    }

    /** Pontos que o jogador precisa alcancar (pes em Y+1, ou perto o bastante para clicar). */
    static Map<String, int[]> pontos() {
        int y = Planta.Y + 1;
        Map<String, int[]> m = new LinkedHashMap<>();
        m.put("spawn da portaria", new int[]{0, y, 2});
        m.put("frente do teclado", new int[]{4, y, 10});
        m.put("fita 1 (seguranca)", new int[]{10, y, 5});
        m.put("saguao", new int[]{0, y, 20});
        m.put("pedestal da luva azul", new int[]{-26, y, 21});
        m.put("perto do plugue do saguao", new int[]{10, y, 18});
        m.put("perto da tomada do portao", new int[]{4, y, 40});
        m.put("linha de producao", new int[]{0, y, 50});
        m.put("frente das prensas", new int[]{0, y, 58});
        m.put("vestiario", new int[]{25, y, 70});
        m.put("pedestal da mao vermelha", new int[]{27, y, 72});
        m.put("fita 2", new int[]{22, y, 73});
        m.put("sala de controle", new int[]{24, y, 80});
        m.put("meio da ponte", new int[]{0, y, 83});
        m.put("sala dos brinquedos gigantes", new int[]{-16, y, 83});
        m.put("duto (meio)", new int[]{-41, y, 92});
        m.put("sala final", new int[]{-72, y, 84});
        return m;
    }

    private int alcancados(Set<Long> vis, List<String> faltam, Map<String, int[]> pts) {
        int ok = 0;
        for (Map.Entry<String, int[]> e : pts.entrySet()) {
            int[] p = e.getValue();
            boolean achou = false;
            for (int dy = -1; dy <= 1 && !achou; dy++) if (vis.contains(k(p[0], p[1] + dy, p[2]))) achou = true;
            if (achou) ok++;
            else faltam.add(e.getKey());
        }
        return ok;
    }

    /** Roda tudo; devolve o relatorio (linhas). Deixa a fabrica com as portas no estado inicial no fim. */
    List<String> rodar() {
        Map<String, int[]> pts = pontos();
        int[] s = pts.get("spawn da portaria");
        // 1) tudo aberto
        abrirTudo(true);
        Set<Long> vis = alcance(s[0], s[1], s[2]);
        List<String> faltam = new ArrayList<>();
        int ok = alcancados(vis, faltam, pts);
        rel.add((faltam.isEmpty() ? "OK" : "FALHA") + " a pe com tudo aberto: " + ok + "/" + pts.size() + " pontos"
                + (faltam.isEmpty() ? "" : " | NAO alcancados: " + String.join(", ", faltam)));
        // 2) cada porta fechada bloqueia o que vem depois
        bloqueia("porta da portaria", () -> pt.portaPortaria(false), () -> pt.portaPortaria(true), s, pts, "saguao");
        bloqueia("porta da sala da luva (fechada)", () -> pt.portaLuva(false), () -> pt.portaLuva(true), new int[]{-26, Planta.Y + 1, 21}, pts, "saguao");
        bloqueia("portao da fabrica", () -> pt.portao(false), () -> pt.portao(true), s, pts, "linha de producao");
        bloqueia("porta do vestiario", () -> pt.portaVestiario(false), () -> pt.portaVestiario(true), s, pts, "vestiario");
        bloqueia("ponte recolhida", () -> pt.ponte(0), () -> pt.ponte(Planta.tamanhoPonte()), s, pts, "sala dos brinquedos gigantes");
        abrirTudo(false);
        return rel;
    }

    private void bloqueia(String nome, Runnable fechar, Runnable abrir, int[] de, Map<String, int[]> pts, String alvo) {
        fechar.run();
        Set<Long> v = alcance(de[0], de[1], de[2]);
        List<String> faltam = new ArrayList<>();
        Map<String, int[]> so = new LinkedHashMap<>();
        so.put(alvo, pts.get(alvo));
        alcancados(v, faltam, so);
        rel.add((faltam.isEmpty() ? "FALHA" : "OK") + " " + nome + (faltam.isEmpty() ? " NAO bloqueia: da para chegar em " + alvo : " bloqueia " + alvo));
        abrir.run();
    }

    private void abrirTudo(boolean abrir) {
        pt.portaPortaria(abrir);
        pt.portaLuva(true);
        pt.portao(abrir);
        pt.portaVestiario(abrir);
        pt.ponte(abrir ? Planta.tamanhoPonte() : 0);
    }
}
