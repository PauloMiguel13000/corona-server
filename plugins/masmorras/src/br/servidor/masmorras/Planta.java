package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Desenho de uma masmorra (so o que nao depende do terreno): salas, corredores e baus.
 * A ultima sala e a arena do chefe. Versao 2: grade, salas e arena pelo tamanho do tipo (bem maiores) e salas
 * extras do lado do caminho (com tesouro).
 */
final class Planta {

    /** Versao do desenho (masmorras de versao menor sao desmanchadas e feitas de novo). */
    static final int VERSAO = 2;

    final Tipo tipo;
    final long semente;
    final int ox, oy, oz;
    final List<Caixa> salas = new ArrayList<>();
    final List<Caixa> corredores = new ArrayList<>();
    /** Baus: {x, y, z, virado (0 norte, 1 leste, 2 sul, 3 oeste)}. */
    final List<int[]> baus = new ArrayList<>();
    /** Quantas salas sao do caminho (o resto, antes da arena, sao salas do lado). */
    int noCaminho;
    /** Onde comeca a saida: no meio da parede norte da primeira sala (subterranea) ou na porta (torre). */
    int entradaX, entradaY, entradaZ;
    /** Torre: centro e altura de cada andar (piso). */
    int centroX, centroZ;
    final List<Integer> andares = new ArrayList<>();

    private Planta(Tipo tipo, long semente, int ox, int oy, int oz) {
        this.tipo = tipo;
        this.semente = semente;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    Caixa arena() {
        return salas.get(salas.size() - 1);
    }

    /** (ox, oz) = canto noroeste da grade (subterranea) ou da area da torre; o centro fica em ox + tipo.meia(). */
    static Planta de(Tipo t, long semente, int ox, int oy, int oz) {
        Planta p = new Planta(t, semente, ox, oy, oz);
        if (t.estilo == Tipo.Estilo.TORRE) p.torre();
        else p.subterranea();
        return p;
    }

    /** Caminho pela grade NxN: comeca na fileira de cima (norte) e anda sem repetir celula, de preferencia para o sul. */
    static List<int[]> caminho(Random r, int quer, int n) {
        List<int[]> melhor = null;
        for (int tentativa = 0; tentativa < 80; tentativa++) {
            List<int[]> cam = new ArrayList<>();
            boolean[][] usado = new boolean[n][n];
            int i = r.nextInt(n), j = 0;
            cam.add(new int[] {i, j});
            usado[i][j] = true;
            while (cam.size() < quer) {
                List<int[]> op = new ArrayList<>();
                if (i > 0 && !usado[i - 1][j]) op.add(new int[] {i - 1, j});
                if (i < n - 1 && !usado[i + 1][j]) op.add(new int[] {i + 1, j});
                if (j < n - 1 && !usado[i][j + 1]) {
                    op.add(new int[] {i, j + 1});
                    if (cam.size() * 2 < quer) op.add(new int[] {i, j + 1});
                }
                if (j > 0 && !usado[i][j - 1] && j - 1 > 0) op.add(new int[] {i, j - 1});
                if (op.isEmpty()) break;
                int[] e = op.get(r.nextInt(op.size()));
                i = e[0];
                j = e[1];
                usado[i][j] = true;
                cam.add(e);
            }
            if (cam.size() >= quer) return cam;
            if (melhor == null || cam.size() > melhor.size()) melhor = cam;
        }
        if (melhor != null && melhor.size() >= Math.min(quer, 3)) return melhor;
        List<int[]> reserva = new ArrayList<>();
        for (int j = 0; j < Math.max(3, quer) && j < n; j++) reserva.add(new int[] {0, j});
        return reserva;
    }

    int centroCelulaX(int i) {
        return ox + tipo.celula * i + tipo.celula / 2;
    }

    int centroCelulaZ(int j) {
        return oz + tipo.celula * j + tipo.celula / 2;
    }

    private void subterranea() {
        Random r = new Random(semente);
        int n = tipo.grade;
        List<int[]> cam = caminho(r, tipo.salas, n);
        boolean[][] usado = new boolean[n][n];
        for (int[] c : cam) usado[c[0]][c[1]] = true;
        // salas do lado: celula livre vizinha de uma sala do meio do caminho
        List<int[]> ramos = new ArrayList<>();
        List<Integer> pai = new ArrayList<>();
        for (int tent = 0; tent < 60 && ramos.size() < tipo.ramos && cam.size() >= 3; tent++) {
            int k = 1 + r.nextInt(cam.size() - 2);
            int[] c = cam.get(k);
            int[][] viz = {{c[0] - 1, c[1]}, {c[0] + 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
            int[] v = viz[r.nextInt(4)];
            if (v[0] < 0 || v[0] >= n || v[1] < 1 || v[1] >= n || usado[v[0]][v[1]]) continue;
            usado[v[0]][v[1]] = true;
            ramos.add(v);
            pai.add(k);
        }
        int w = tipo.corredor, hc = w >= 2 ? 4 : 3;
        int[][] centro = new int[cam.size()][];
        Caixa arena = null;
        for (int k = 0; k < cam.size(); k++) {
            int cx = centroCelulaX(cam.get(k)[0]), cz = centroCelulaZ(cam.get(k)[1]);
            centro[k] = new int[] {cx, cz};
            boolean ehArena = k == cam.size() - 1;
            int hw = ehArena ? tipo.arenaMeia : tipo.meiaMin + r.nextInt(tipo.meiaMax - tipo.meiaMin + 1);
            int hd = ehArena ? tipo.arenaMeia : tipo.meiaMin + r.nextInt(tipo.meiaMax - tipo.meiaMin + 1);
            int h = ehArena ? tipo.alturaArena : tipo.alturaSala;
            Caixa s = new Caixa(cx - hw, oy, cz - hd, cx + hw, oy + h - 1, cz + hd);
            if (ehArena) arena = s;
            else salas.add(s);
        }
        noCaminho = salas.size();
        for (int k = 1; k < cam.size(); k++) corredores.add(corredor(centro[k - 1], centro[k], w, hc));
        for (int q = 0; q < ramos.size(); q++) {
            int cx = centroCelulaX(ramos.get(q)[0]), cz = centroCelulaZ(ramos.get(q)[1]);
            int hw = tipo.meiaMin + r.nextInt(Math.max(1, tipo.meiaMax - tipo.meiaMin - 1)), hd = tipo.meiaMin + r.nextInt(Math.max(1, tipo.meiaMax - tipo.meiaMin - 1));
            salas.add(new Caixa(cx - hw, oy, cz - hd, cx + hw, oy + tipo.alturaSala - 1, cz + hd));
            corredores.add(corredor(centro[pai.get(q)], new int[] {cx, cz}, w, hc));
        }
        salas.add(arena);
        // baus nas salas do caminho (perto de um canto, longe das portas, que ficam no meio das paredes)
        for (int k = 1; k < noCaminho; k++) {
            if (r.nextDouble() > 0.7) continue;
            bauNoCanto(salas.get(k), r.nextInt(4));
        }
        // salas do lado: sempre um tesouro (dois nas grandes)
        for (int k = noCaminho; k < salas.size() - 1; k++) {
            int c0 = r.nextInt(4);
            bauNoCanto(salas.get(k), c0);
            if (tipo.nivel >= 3) bauNoCanto(salas.get(k), (c0 + 2) % 4);
        }
        Caixa ar = arena();
        baus.add(new int[] {ar.x1(), oy, ar.z1(), 2});
        baus.add(new int[] {ar.x2(), oy, ar.z2(), 0});
        if (tipo.nivel >= 3) {
            baus.add(new int[] {ar.x2(), oy, ar.z1(), 2});
            baus.add(new int[] {ar.x1(), oy, ar.z2(), 0});
        }
        Caixa s0 = salas.get(0);
        entradaX = s0.cx();
        entradaY = oy;
        entradaZ = s0.z1() - 1;
    }

    private Caixa corredor(int[] a, int[] b, int w, int hc) {
        if (a[1] == b[1]) return Caixa.de(a[0], oy, a[1] - w, b[0], oy + hc, a[1] + w);
        return Caixa.de(a[0] - w, oy, a[1], a[0] + w, oy + hc, b[1]);
    }

    private void bauNoCanto(Caixa s, int canto) {
        switch (canto) {
            case 0 -> baus.add(new int[] {s.x1(), oy, s.z1() + 1, 1});
            case 1 -> baus.add(new int[] {s.x2(), oy, s.z1() + 1, 3});
            case 2 -> baus.add(new int[] {s.x1(), oy, s.z2() - 1, 1});
            default -> baus.add(new int[] {s.x2(), oy, s.z2() - 1, 3});
        }
    }

    private void torre() {
        int hm = tipo.torreMeia;
        centroX = ox + tipo.meia();
        centroZ = oz + tipo.meia();
        int y = oy;
        for (int k = 0; k < tipo.salas; k++) {
            boolean arena = k == tipo.salas - 1;
            int h = arena ? tipo.alturaArena : tipo.alturaSala;
            salas.add(new Caixa(centroX - hm, y, centroZ - hm, centroX + hm, y + h - 1, centroZ + hm));
            andares.add(y);
            if (k > 0 && !arena) baus.add(new int[] {centroX - hm, y, centroZ - hm + 1, 1});
            y += h + 1;
        }
        noCaminho = salas.size() - 1;
        Caixa ar = arena();
        baus.add(new int[] {ar.x1(), ar.y1(), ar.z1(), 2});
        baus.add(new int[] {ar.x2(), ar.y1(), ar.z1(), 2});
        entradaX = centroX;
        entradaY = oy;
        entradaZ = centroZ + hm + 1;
    }

    /** Escada de mao da torre (andar k para k+1): coluna perto do canto sudeste, presa na parede leste. */
    int escadaX() {
        return centroX + tipo.torreMeia;
    }

    int escadaZ() {
        return centroZ + tipo.torreMeia - 1;
    }

    /** Area que a masmorra ocupa (sem o tunel), com as paredes. */
    Caixa area() {
        Caixa a = null;
        for (Caixa s : salas) a = a == null ? s.crescer(1) : a.juntar(s.crescer(1));
        for (Caixa c : corredores) a = a.juntar(c.crescer(1));
        return a;
    }
}
