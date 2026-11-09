package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sorteia onde ficam as masmorras: dentro da borda do mundo, longe do spawn, longe umas das outras, e quanto
 * mais longe do spawn, maior o nivel. O bioma e conferido sem carregar o mapa (filtro passado de fora).
 */
final class Locais {

    interface Bioma {
        /** Nome do bioma no ponto (ex.: "PLAINS"), sem carregar chunk. */
        String em(int x, int z);
    }

    final Tipo.Onde onde;
    final int borda;
    final Random r;
    /** Aneis do mundo novo (raio de cada nivel), ou null no mundo antigo. */
    final int[] zonas;
    final List<int[]> aceitos = new ArrayList<>();
    /** Lugares recusados na conferencia (agua, morro, area muito usada): bloqueiam so um pedaco pequeno, nao o espaco todo. */
    final List<int[]> recusados = new ArrayList<>();
    static final int RAIO_RECUSADO = 40;

    Locais(Tipo.Onde onde, int borda, long semente) {
        this(onde, borda, semente, null);
    }

    Locais(Tipo.Onde onde, int borda, long semente, int[] zonas) {
        this.onde = onde;
        this.borda = borda;
        this.r = new Random(semente);
        this.zonas = zonas;
    }

    /** Distancia do spawn (minima, maxima) para o nivel. */
    int[] faixa(Tipo t) {
        if (zonas != null && onde == Tipo.Onde.NORMAL) {
            // mundo novo: cada nivel e um anel (com folga de 120 nas divisas); o nivel 1 comeca depois do planalto do lobby
            int n = Math.max(1, Math.min(4, t.nivel));
            int de = n == 1 ? 250 : zonas[n - 2] + 120;
            int ate = n == 4 ? borda - 260 : zonas[n - 1] - 120;
            return new int[] {de, Math.max(de + 100, ate)};
        }
        return switch (onde) {
            // mais perto do spawn que antes (antes: 250-1500, 1000-2600, 2200-4740; End 1100-4740); 01/11: 2 a 4 um pouco mais largas (faltou lugar)
            case NORMAL -> switch (t.nivel) {
                case 1 -> new int[] {250, 1100};
                case 2 -> new int[] {600, 2000};
                case 3 -> new int[] {1000, 2600};
                case 4 -> new int[] {1500, Math.min(3200, borda - 260)};
                default -> new int[] {2200, borda - 260};
            };
            case NETHER -> new int[] {110, borda - 110};
            case END -> new int[] {1100, Math.min(3000, borda - 260)};
        };
    }

    int espaco() {
        return switch (onde) {
            case NORMAL -> 260;
            case NETHER -> 175;
            case END -> 500;
        };
    }

    /** Toda a masmorra (grade em volta do centro, tunel ate ~70 para o norte) cabe dentro da borda com folga. */
    boolean cabe(Tipo t, int cx, int cz) {
        int m = borda - 16, r = t.meia() + 8;
        int norte = t.estilo == Tipo.Estilo.TORRE ? r : r + 72;
        return cx - r >= -m && cx + r <= m && cz - norte >= -m && cz + r + 8 <= m;
    }

    static boolean agua(String bioma) {
        return bioma.contains("OCEAN") || bioma.contains("RIVER");
    }

    static boolean preferido(Tipo t, String bioma) {
        if (t.biomas.length == 0) return true;
        for (String b : t.biomas) if (bioma.contains(b)) return true;
        return false;
    }

    /** Proximo lugar para o tipo (centro x, z), ou null. */
    int[] candidato(Tipo t, Bioma bioma) {
        int[] f = faixa(t);
        for (int tentativa = 0; tentativa < 700; tentativa++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double raio = Math.sqrt(f[0] * (double) f[0] + r.nextDouble() * (f[1] * (double) f[1] - f[0] * (double) f[0]));
            int cx = (int) Math.round(Math.cos(ang) * raio), cz = (int) Math.round(Math.sin(ang) * raio);
            if (!cabe(t, cx, cz) || perto(cx, cz)) continue;
            if (onde == Tipo.Onde.NORMAL && bioma != null) {
                String b = bioma.em(cx, cz);
                if (agua(b)) continue;
                // nas primeiras tentativas so aceita o bioma preferido (gelo na neve, bruxa no pantano)
                if (tentativa < 250 && !preferido(t, b)) continue;
            }
            return new int[] {cx, cz};
        }
        return varrer(t, bioma, f);
    }

    /** O sorteio nao achou: varre a faixa inteira em grade e escolhe um ponto livre ao acaso (bioma preferido primeiro). */
    private int[] varrer(Tipo t, Bioma bioma, int[] f) {
        List<int[]> bons = new ArrayList<>(), outros = new ArrayList<>();
        int passo = onde == Tipo.Onde.NORMAL ? 24 : 12, max = f[1];
        long min2 = (long) f[0] * f[0], max2 = (long) f[1] * f[1];
        int ox = r.nextInt(passo), oz = r.nextInt(passo);
        for (int cx = -max + ox; cx <= max; cx += passo)
            for (int cz = -max + oz; cz <= max; cz += passo) {
                long d2 = (long) cx * cx + (long) cz * cz;
                if (d2 < min2 || d2 > max2 || !cabe(t, cx, cz) || perto(cx, cz)) continue;
                if (onde == Tipo.Onde.NORMAL && bioma != null) {
                    String b = bioma.em(cx, cz);
                    if (agua(b)) continue;
                    (preferido(t, b) ? bons : outros).add(new int[] {cx, cz});
                } else bons.add(new int[] {cx, cz});
            }
        List<int[]> lista = bons.isEmpty() ? outros : bons;
        return lista.isEmpty() ? null : lista.get(r.nextInt(lista.size()));
    }

    boolean perto(int cx, int cz) {
        long e2 = (long) espaco() * espaco();
        for (int[] a : aceitos) {
            long dx = a[0] - cx, dz = a[1] - cz;
            if (dx * dx + dz * dz < e2) return true;
        }
        long r2 = (long) RAIO_RECUSADO * RAIO_RECUSADO;
        for (int[] a : recusados) {
            long dx = a[0] - cx, dz = a[1] - cz;
            if (dx * dx + dz * dz < r2) return true;
        }
        return false;
    }

    void aceitar(int[] c) {
        aceitos.add(c);
    }

    void recusar(int[] c) {
        recusados.add(c);
    }
}
