package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * Transforma a planta em blocos, olhando o terreno: tunel ate a superficie (mundo normal), ate uma caverna (Nether)
 * ou varanda (End); alicerce da torre; ilha de pedra do End por baixo. Tudo e decidido antes de colocar (le o
 * terreno original), e depois os blocos sao colocados aos poucos.
 */
final class Construtor {

    /** Resultado: blocos (posicao -> bloco), baus, caixas protegidas e onde o jogador entra. */
    static final class Obra {
        final Map<Long, String> blocos = new LinkedHashMap<>();
        final List<int[]> baus = new ArrayList<>();
        final List<Caixa> protecao = new ArrayList<>();
        /** Placa na entrada (colocada depois dos blocos, com o texto). */
        Placa placa;
        int saidaX, saidaY, saidaZ;
        int tunel;
    }

    /** Placa em pe: posicao, para onde o texto olha (north, south...) e as 4 linhas. */
    record Placa(int x, int y, int z, String virada, String[] linhas) {}

    /** Texto da placa da entrada: nome, nivel e chefe (linhas curtas, cabem no Java e no Bedrock). */
    static String[] linhasPlaca(Tipo t) {
        String nome = switch (t.id) {
            case "CRIPTA" -> "CRIPTA";
            case "NINHO" -> "NINHO ARANHA";
            case "MINA" -> "MINA";
            case "PANTANO" -> "COVIL BRUXA";
            case "GELO" -> "FORTE DE GELO";
            case "TORRE" -> "TORRE DO MAGO";
            case "FORJA" -> "FORJA";
            case "BASTIAO" -> "BASTIÃO";
            case "WITHER" -> "FORTE WITHER";
            case "CIDADELA" -> "CIDADELA";
            case "PRISAO" -> "PRISÃO";
            case "DESERTO" -> "TUMBA FARAÓ";
            case "QUARTEL" -> "QUARTEL";
            case "VENTOS" -> "SANTUÁRIO";
            case "AFOGADA" -> "CATACUMBA";
            case "SELVA" -> "TEMPLO SELVA";
            case "CASTELO" -> "CASTELO";
            case "COVIL" -> "COVIL";
            default -> t.placa != null ? t.placa : t.id;
        };
        if (t.secreta) return new String[] {"§4§l???", "§8Nível ?", "§7Não deveria", "§7estar aqui."};
        String chefe = t.chefeNome.replace("Senhor das ", "Sr. das ").replace("Senhor dos ", "Sr. dos ").replace("Senhor do ", "Sr. do ");
        return new String[] {"§4§l" + nome, "§eNível " + t.nivel + " §7de 5", "§fChefe:", "§c" + chefe};
    }

    static final String AR = "minecraft:air";
    static final String[] VIRADO = {"north", "east", "south", "west"};

    static long chave(int x, int y, int z) {
        return ((long) (x + (1 << 25)) << 38) | ((long) (z + (1 << 25)) << 12) | (long) (y + 2048);
    }

    static int cx(long k) {
        return (int) (k >>> 38) - (1 << 25);
    }

    static int cz(long k) {
        return (int) ((k >>> 12) & 0x3FFFFFFL) - (1 << 25);
    }

    static int cy(long k) {
        return (int) (k & 0xFFFL) - 2048;
    }

    private final Planta p;
    private final Tipo t;
    private final Destino d;
    private final Obra o = new Obra();

    private Construtor(Planta p, Destino d) {
        this.p = p;
        this.t = p.tipo;
        this.d = d;
    }

    static Obra montar(Planta p, Destino d) {
        Construtor c = new Construtor(p, d);
        c.fazer();
        return c.o;
    }

    private void por(int x, int y, int z, String b) {
        if (y < d.minY() || y > d.maxY()) return;
        o.blocos.put(chave(x, y, z), b);
    }

    private String parede(int x, int y, int z) {
        return Tipo.escolher(t.parede, x, y, z, p.semente);
    }

    private String piso(int x, int y, int z) {
        return Tipo.escolher(t.piso, x, y, z, p.semente + 1);
    }

    private String teto(int x, int y, int z) {
        return Tipo.escolher(t.teto, x, y, z, p.semente + 2);
    }

    private void casca(Caixa interior) {
        Caixa c = interior.crescer(1);
        for (int x = c.x1(); x <= c.x2(); x++)
            for (int z = c.z1(); z <= c.z2(); z++)
                for (int y = c.y1(); y <= c.y2(); y++) {
                    if (y == c.y1()) por(x, y, z, piso(x, y, z));
                    else if (y == c.y2()) por(x, y, z, teto(x, y, z));
                    else por(x, y, z, parede(x, y, z));
                }
        o.protecao.add(c);
    }

    private void vazio(Caixa c) {
        for (int x = c.x1(); x <= c.x2(); x++)
            for (int z = c.z1(); z <= c.z2(); z++)
                for (int y = c.y1(); y <= c.y2(); y++) por(x, y, z, AR);
    }

    private void fazer() {
        if (t.estilo == Tipo.Estilo.TORRE) torre();
        else subterranea();
        for (int[] b : p.baus) o.baus.add(b);
    }

    // ------------------------------------------------------------------ subterranea / embutida / flutuante

    private void subterranea() {
        for (Caixa s : p.salas) casca(s);
        for (Caixa c : p.corredores) casca(c);
        for (Caixa s : p.salas) vazio(s);
        for (Caixa c : p.corredores) vazio(c);
        for (int k = 0; k < p.salas.size(); k++) decorar(p.salas.get(k), k == p.salas.size() - 1);
        for (Caixa c : p.corredores) luz(c.cx(), c.y2(), c.cz());
        if (t.onde == Tipo.Onde.END) {
            varanda();
            ilha(p.area());
        } else tunel();
    }

    /** Pilares na arena, decoracao pelas bordas e no teto, luzes. */
    private void decorar(Caixa s, boolean arena) {
        int cx = s.cx(), cz = s.cz();
        int hw = (s.x2() - s.x1()) / 2;
        if (arena) {
            int q1 = hw / 2 + 1;
            List<int[]> pil = new ArrayList<>(List.of(new int[] {-q1, -q1}, new int[] {q1, -q1}, new int[] {-q1, q1}, new int[] {q1, q1}));
            if (hw >= 12) {
                int q2 = hw - 3;
                pil.addAll(List.of(new int[] {-q2, 0}, new int[] {q2, 0}, new int[] {0, -q2}, new int[] {0, q2}));
            }
            for (int[] q : pil)
                for (int y = s.y1(); y <= s.y2(); y++) por(cx + q[0], y, cz + q[1], t.pilar);
        }
        int w = t.corredor;
        for (int x = s.x1(); x <= s.x2(); x++)
            for (int z = s.z1(); z <= s.z2(); z++) {
                boolean borda = x == s.x1() || x == s.x2() || z == s.z1() || z == s.z2();
                boolean porta = (Math.abs(x - cx) <= w && (z == s.z1() || z == s.z2())) || (Math.abs(z - cz) <= w && (x == s.x1() || x == s.x2()));
                if (borda && !porta && !ehBau(x, s.y1(), z) && Tipo.sorte(x, s.y1(), z, p.semente) < t.chancePiso)
                    por(x, s.y1(), z, Tipo.escolher(t.decoPiso, x, s.y1(), z, p.semente + 3));
                if (Tipo.sorte(x, s.y2(), z, p.semente + 5) < t.chanceTeto && !(Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1))
                    por(x, s.y2(), z, Tipo.escolher(t.decoTeto, x, s.y2(), z, p.semente + 4));
            }
        if (arena) {
            int l = Math.max(4, hw / 2 - 1);
            for (int[] q : new int[][] {{-l, 0}, {l, 0}, {0, -l}, {0, l}, {-l, -l}, {l, -l}, {-l, l}, {l, l}}) luz(cx + q[0], s.y2(), cz + q[1]);
        } else {
            luz(cx, s.y2(), cz);
            if (s.x2() - s.x1() >= 10) {
                luz(cx - 3, s.y2(), cz);
                luz(cx + 3, s.y2(), cz);
            }
            if (s.z2() - s.z1() >= 14) {
                luz(cx, s.y2(), cz - 4);
                luz(cx, s.y2(), cz + 4);
            }
        }
    }

    private boolean ehBau(int x, int y, int z) {
        for (int[] b : p.baus) if (b[0] == x && b[1] == y && b[2] == z) return true;
        return false;
    }

    /** Luz pendurada logo abaixo do teto, ou no lugar do bloco do teto. */
    private void luz(int x, int yTopo, int z) {
        if (t.luzNoTeto) por(x, yTopo + 1, z, t.luz);
        else por(x, yTopo, z, t.luz);
    }

    /** Tunel de escada que sobe da primeira sala ate o lado de fora (superficie ou caverna do Nether). */
    private void tunel() {
        int x0 = p.entradaX, z0 = p.entradaZ, oy = p.oy;
        String degrau = Tipo.b(t.escada + "[facing=north,half=bottom]");
        int s = 0, fim = -1;
        for (; s < 56; s++) {
            int z = z0 - s, yb = oy - 1 + s;
            if (s >= 2 && fora(x0, yb, z)) {
                fim = yb;
                break;
            }
            for (int x = x0 - 2; x <= x0 + 2; x++) {
                boolean lado = Math.abs(x - x0) == 2;
                por(x, yb - 1, z, lado ? parede(x, yb - 1, z) : piso(x, yb - 1, z));
                if (lado) {
                    for (int y = yb; y <= yb + 5; y++) por(x, y, z, parede(x, y, z));
                } else {
                    por(x, yb, z, s == 0 ? piso(x, yb, z) : degrau);
                    for (int y = yb + 1; y <= yb + 4; y++) por(x, y, z, AR);
                    por(x, yb + 5, z, teto(x, yb + 5, z));
                }
            }
            if (s % 6 == 3) {
                if (t.luzNoTeto) por(x0, yb + 5, z, t.luz);
                else por(x0, yb + 4, z, t.luz);
            }
            o.protecao.add(new Caixa(x0 - 2, yb - 1, z, x0 + 2, yb + 5, z));
        }
        if (fim < 0) fim = oy - 1 + s;
        // patamar do lado de fora: chao reto e ar em cima (tira terra, plantas e folhas)
        for (int k = 0; k < 3; k++) {
            int z = z0 - s - k;
            for (int x = x0 - 1; x <= x0 + 1; x++) {
                por(x, fim, z, piso(x, fim, z));
                for (int y = fim + 1; y <= fim + 4; y++) por(x, y, z, AR);
            }
            // portal de entrada (dois pilares e uma viga) no primeiro passo para fora
            if (k == 0) {
                for (int y = fim + 1; y <= fim + 4; y++) {
                    por(x0 - 2, y, z, t.pilar);
                    por(x0 + 2, y, z, t.pilar);
                }
                for (int x = x0 - 2; x <= x0 + 2; x++) por(x, fim + 5, z, t.pilar);
                if (!t.luzNoTeto) por(x0, fim + 4, z, t.luz);
            }
            o.protecao.add(new Caixa(x0 - 2, fim, z, x0 + 2, fim + 5, z));
        }
        o.tunel = s;
        o.saidaX = x0;
        o.saidaY = fim + 1;
        o.saidaZ = z0 - s - 2;
        o.placa = new Placa(x0 + 1, fim + 1, z0 - s - 2, "north", linhasPlaca(t));
    }

    /** Chegou do lado de fora? Mundo normal: degrau acima do chao. Nether: ar livre (caverna) em cima do degrau. */
    private boolean fora(int x0, int yb, int z) {
        if (t.onde == Tipo.Onde.NETHER) {
            for (int x = x0 - 1; x <= x0 + 1; x++)
                for (int y = yb + 1; y <= yb + 3; y++) {
                    Material m = d.tipo(x, y, z);
                    if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR) return false;
                }
            return true;
        }
        int alto = Integer.MIN_VALUE;
        for (int x = x0 - 1; x <= x0 + 1; x++) alto = Math.max(alto, d.chao(x, z));
        return yb >= alto;
    }

    /** End: varanda saindo da primeira sala (chega-se voando ou fazendo ponte). */
    private void varanda() {
        int x0 = p.entradaX, z0 = p.entradaZ, oy = p.oy;
        for (int k = 0; k < 6; k++) {
            int z = z0 - k;
            for (int x = x0 - 2; x <= x0 + 2; x++) {
                por(x, oy - 1, z, piso(x, oy - 1, z));
                boolean lado = Math.abs(x - x0) == 2;
                if (lado && k > 0) por(x, oy, z, parede(x, oy, z));
                else for (int y = oy; y <= oy + 3; y++) por(x, y, z, AR);
            }
            o.protecao.add(new Caixa(x0 - 2, oy - 1, z, x0 + 2, oy + 4, z));
        }
        por(x0 - 2, oy + 1, z0 - 5, t.pilar);
        por(x0 + 2, oy + 1, z0 - 5, t.pilar);
        por(x0 - 2, oy + 2, z0 - 5, t.luz.contains("end_rod") ? Tipo.b("end_rod[facing=up]") : t.pilar);
        por(x0 + 2, oy + 2, z0 - 5, t.luz.contains("end_rod") ? Tipo.b("end_rod[facing=up]") : t.pilar);
        // a ilha vai ate debaixo da varanda
        ilha(new Caixa(x0 - 3, oy - 1, z0 - 7, x0 + 3, oy, z0));
        o.saidaX = x0;
        o.saidaY = oy;
        o.saidaZ = z0 - 4;
        o.placa = new Placa(x0 + 1, oy, z0 - 5, "north", linhasPlaca(t));
    }

    /** Ilha de pedra do End por baixo da construcao (so onde e ar). */
    private void ilha(Caixa a) {
        int topo = a.y1() - 1;
        double rx = (a.x2() - a.x1()) / 2.0 + 3, rz = (a.z2() - a.z1()) / 2.0 + 3;
        double mx = (a.x1() + a.x2()) / 2.0, mz = (a.z1() + a.z2()) / 2.0;
        for (int x = (int) Math.floor(mx - rx); x <= (int) Math.ceil(mx + rx); x++)
            for (int z = (int) Math.floor(mz - rz); z <= (int) Math.ceil(mz + rz); z++) {
                double dx = (x - mx) / rx, dz = (z - mz) / rz, dd = dx * dx + dz * dz;
                if (dd > 1) continue;
                int fundo = (int) Math.round((1 - dd) * 14) + 2;
                for (int y = topo; y > topo - fundo; y--) {
                    if (o.blocos.containsKey(chave(x, y, z))) continue;
                    Material m = d.tipo(x, y, z);
                    if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR) por(x, y, z, "minecraft:end_stone");
                }
            }
    }

    // ------------------------------------------------------------------ torre

    private void torre() {
        int c = p.centroX, cz = p.centroZ, hm = t.torreMeia, pw = hm + 1;
        for (Caixa s : p.salas) casca(s);
        for (Caixa s : p.salas) vazio(s);
        Caixa topo = p.arena();
        int ultimo = p.salas.size() - 1;
        for (int k = 0; k < p.salas.size(); k++) {
            Caixa s = p.salas.get(k);
            // janelas
            for (int i = -hm + 2; i <= hm - 2; i += 4) {
                for (int y = s.y1() + 2; y <= s.y1() + 3; y++) {
                    por(c + i, y, cz - pw, t.janela);
                    por(c - pw, y, cz + i, t.janela);
                    por(c + pw, y, cz + i, t.janela);
                    if (k > 0) por(c + i, y, cz + pw, t.janela);
                }
            }
            decorarTorre(s, k == ultimo);
            // escada de mao ate o andar de cima (passa pelo buraco no piso de cima)
            if (k < ultimo) for (int y = s.y1(); y <= s.y2() + 1; y++) por(p.escadaX(), y, p.escadaZ(), Tipo.b("ladder[facing=west]"));
        }
        // ameias no telhado
        for (int i = -pw; i <= pw; i += 2) {
            por(c + i, topo.y2() + 2, cz - pw, parede(c + i, topo.y2() + 2, cz - pw));
            por(c + i, topo.y2() + 2, cz + pw, parede(c + i, topo.y2() + 2, cz + pw));
            por(c - pw, topo.y2() + 2, cz + i, parede(c - pw, topo.y2() + 2, cz + i));
            por(c + pw, topo.y2() + 2, cz + i, parede(c + pw, topo.y2() + 2, cz + i));
        }
        int oy = p.oy;
        // porta (3 de largura, 3 de altura) na parede sul do terreo
        for (int x = c - 1; x <= c + 1; x++) for (int y = oy; y <= oy + 2; y++) por(x, y, cz + pw, AR);
        o.protecao.add(new Caixa(c - pw, oy - 1, cz - pw, c + pw, topo.y2() + 2, cz + pw));
        if (t.onde == Tipo.Onde.END) {
            // patamar na frente da porta e ilha embaixo
            for (int z = cz + pw + 1; z <= cz + pw + 4; z++)
                for (int x = c - 2; x <= c + 2; x++) {
                    por(x, oy - 1, z, piso(x, oy - 1, z));
                    for (int y = oy; y <= oy + 3; y++) por(x, y, z, AR);
                }
            o.protecao.add(new Caixa(c - 2, oy - 1, cz + pw + 1, c + 2, oy + 3, cz + pw + 4));
            ilha(new Caixa(c - pw, oy - 1, cz - pw, c + pw, oy, cz + pw + 4));
            o.saidaX = c;
            o.saidaY = oy;
            o.saidaZ = cz + pw + 3;
            o.placa = new Placa(c + 2, oy, cz + pw + 4, "south", linhasPlaca(t));
        } else {
            // alicerce ate o chao e caminho limpo na frente da porta
            for (int x = c - pw; x <= c + pw; x++)
                for (int z = cz - pw; z <= cz + pw; z++) {
                    int chao = d.chao(x, z);
                    for (int y = oy - 2; y > chao && y > oy - 40; y--) por(x, y, z, parede(x, y, z));
                }
            for (int z = cz + pw + 1; z <= cz + pw + 3; z++)
                for (int x = c - 1; x <= c + 1; x++) {
                    por(x, oy - 1, z, piso(x, oy - 1, z));
                    for (int y = oy; y <= oy + 3; y++) por(x, y, z, AR);
                    int chao = d.chao(x, z);
                    for (int y = oy - 2; y > chao && y > oy - 40; y--) por(x, y, z, parede(x, y, z));
                }
            o.protecao.add(new Caixa(c - 1, oy - 1, cz + pw + 1, c + 1, oy + 3, cz + pw + 3));
            o.saidaX = c;
            o.saidaY = oy;
            o.saidaZ = cz + pw + 2;
            o.placa = new Placa(c + 1, oy, cz + pw + 3, "south", linhasPlaca(t));
        }
    }

    private void decorarTorre(Caixa s, boolean arena) {
        int c = s.cx(), cz = s.cz();
        int hm = (s.x2() - s.x1()) / 2, q1 = hm / 2 + 1, l = Math.max(4, hm / 2 - 1);
        if (arena) {
            for (int[] q : new int[][] {{-q1, -q1}, {q1, -q1}, {-q1, q1}, {q1, q1}})
                for (int y = s.y1(); y <= s.y2(); y++) por(c + q[0], y, cz + q[1], t.pilar);
            for (int[] q : new int[][] {{-l, 0}, {l, 0}, {0, -l}, {0, l}}) luz(c + q[0], s.y2(), cz + q[1]);
        } else {
            luz(c, s.y2(), cz);
            luz(c - l, s.y2(), cz - l);
            luz(c + l, s.y2(), cz + l);
        }
        for (int x = s.x1(); x <= s.x2(); x++)
            for (int z = s.z1(); z <= s.z2(); z++) {
                boolean borda = x == s.x1() || x == s.x2() || z == s.z1() || z == s.z2();
                boolean livre = (Math.abs(x - c) <= 1 && z == s.z2()) || (x >= p.escadaX() - 1 && Math.abs(z - p.escadaZ()) <= 1);
                if (borda && !livre && !ehBau(x, s.y1(), z) && Tipo.sorte(x, s.y1(), z, p.semente) < t.chancePiso)
                    por(x, s.y1(), z, Tipo.escolher(t.decoPiso, x, s.y1(), z, p.semente + 3));
                if (!livre && Tipo.sorte(x, s.y2(), z, p.semente + 5) < t.chanceTeto && !(Math.abs(x - c) <= 1 && Math.abs(z - cz) <= 1))
                    por(x, s.y2(), z, Tipo.escolher(t.decoTeto, x, s.y2(), z, p.semente + 4));
            }
    }

    static String virado(int i) {
        return VIRADO[Math.floorMod(i, 4)];
    }
}
