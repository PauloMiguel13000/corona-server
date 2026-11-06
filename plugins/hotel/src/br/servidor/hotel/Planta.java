package br.servidor.hotel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;

/**
 * A planta do Hotel: uma fila de comodos, um depois do outro no eixo X. A porta de saida de um comodo e a porta de
 * entrada do proximo (a parede e compartilhada). Tudo aqui e so calculo (sem mundo), para dar para testar:
 * quem coloca os blocos no mundo e a Partida.
 *
 * Coordenadas de um comodo: paredes em x0 e x1 (x1 = x0 do proximo), z0 e z1; chao em y = Y-1, ar de Y ate Y+alt-1,
 * teto em Y+alt. A porta de entrada fica em (x0, zE) e a de saida em (x1, zS), nas alturas Y e Y+1.
 */
final class Planta {

    static final String MUNDO = "hotel";
    /** Altura dos pes de quem anda no hotel. */
    static final int Y = 64;

    enum Tipo { INICIO, CORREDOR, QUARTO, SALAO, ESCRITORIO, BIBLIOTECA, CORRIDA, SAIDA }

    /** Um bloco da planta: material e o estado (o que vai entre colchetes no BlockData, ou vazio). */
    record Bloco(int x, int y, int z, Material m, String estado) {}

    /** Armario: onde a pessoa fica escondida, o bloco da porta e onde ela sai. */
    record Armario(int x, int z, int portaZ, int foraZ, boolean norte) {}

    static final class Comodo {
        final int idx;
        final Tipo tipo;
        final int x0, x1, z0, z1, alt;
        final int zE, zS;
        boolean escuro;
        /** O Retrato: moldura na parede norte, {x, y, z} do rosto (null = nao tem). */
        int[] retrato;
        boolean trancado;
        /** Gaveta (barril) onde esta a chave, se o comodo for trancado. */
        int[] chave;
        final List<Bloco> blocos = new ArrayList<>();
        final List<int[]> luzes = new ArrayList<>();
        final List<Armario> armarios = new ArrayList<>();
        final List<int[]> gavetas = new ArrayList<>();
        /** Lugares para os livros da biblioteca (chao livre ao lado das estantes). */
        final List<int[]> livros = new ArrayList<>();
        /** Obstaculos da corrida (x de cada mureta). */
        final List<Integer> muretas = new ArrayList<>();

        Comodo(int idx, Tipo tipo, int x0, int comp, int z0, int larg, int alt, int zE, int zS, boolean escuro) {
            this.idx = idx;
            this.tipo = tipo;
            this.x0 = x0;
            this.x1 = x0 + comp;
            this.z0 = z0;
            this.z1 = z0 + larg;
            this.alt = alt;
            this.zE = zE;
            this.zS = zS;
            this.escuro = escuro;
        }

        /** Numero da porta de saida (a primeira porta e a 0001). */
        int porta() {
            return idx + 1;
        }

        boolean contem(double x, double z) {
            return x >= x0 && x < x1 && z > z0 && z < z1;
        }

        double meioX() {
            return (x0 + x1) / 2.0;
        }

        double meioZ() {
            return (z0 + z1) / 2.0;
        }

        /** z do caminho (entre as duas portas) na altura x. */
        double zNoCaminho(double x) {
            double t = Math.max(0, Math.min(1, (x - x0) / (double) (x1 - x0)));
            return zE + 0.5 + (zS - zE) * t;
        }

        void por(int x, int y, int z, Material m) {
            blocos.add(new Bloco(x, y, z, m, ""));
        }

        void por(int x, int y, int z, Material m, String estado) {
            blocos.add(new Bloco(x, y, z, m, estado));
        }

        /** Livre na planta para mobilia: dentro, longe das portas e da faixa do caminho entre elas. */
        boolean livreParaMovel(int x, int z, Map<Long, Boolean> ocupado) {
            if (x <= x0 || x >= x1 || z <= z0 || z >= z1) return false;
            if (Math.abs(x - x0) <= 2 && Math.abs(z - zE) <= 1) return false;
            if (Math.abs(x - x1) <= 2 && Math.abs(z - zS) <= 1) return false;
            // a faixa do caminho entre as portas fica sempre livre
            if (Math.abs(z + 0.5 - zNoCaminho(x + 0.5)) < 1.5) return false;
            return !ocupado.containsKey(chave2(x, z));
        }
    }

    static long chave2(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // ================================================================== fila de comodos

    final int laneZ;
    final long semente;
    final int biblioteca;
    final int corrida;
    final int totalPortas;
    private final Random r;
    final List<Comodo> comodos = new ArrayList<>();

    /**
     * @param laneZ    z do meio da faixa (cada partida usa uma faixa nova)
     * @param biblioteca numero da porta da biblioteca (a ultima antes da saida)
     * @param corrida  numero da porta onde comeca a perseguicao (0 = sem)
     */
    Planta(int laneZ, long semente, int biblioteca, int corrida) {
        this.laneZ = laneZ;
        this.semente = semente;
        this.biblioteca = biblioteca;
        this.corrida = corrida;
        this.totalPortas = biblioteca;
        this.r = new Random(semente);
    }

    /** Comodos da perseguicao: as portas corrida .. corrida+CORRIDA_SALAS-1 ja ficam abertas. */
    static final int CORRIDA_SALAS = 7;

    boolean naCorrida(int idx) {
        return corrida > 0 && idx >= corrida && idx < corrida + CORRIDA_SALAS;
    }

    /** Gera os comodos ate o indice 'ate' (inclusive). */
    void gerarAte(int ate) {
        ate = Math.min(ate, totalPortas);
        while (comodos.size() <= ate) gerar(comodos.size());
    }

    Comodo comodo(int idx) {
        return idx >= 0 && idx < comodos.size() ? comodos.get(idx) : null;
    }

    Comodo onde(double x, double z) {
        for (Comodo c : comodos) if (c.contem(x, z)) return c;
        return null;
    }

    private Tipo sortearTipo(int idx) {
        if (idx == 0) return Tipo.INICIO;
        if (idx == biblioteca - 1) return Tipo.BIBLIOTECA;
        if (idx >= biblioteca) return Tipo.SAIDA;
        if (naCorrida(idx)) return Tipo.CORRIDA;
        double q = r.nextDouble();
        Tipo anterior = comodos.isEmpty() ? null : comodos.get(comodos.size() - 1).tipo;
        Tipo t = q < 0.34 ? Tipo.CORREDOR : q < 0.62 ? Tipo.QUARTO : q < 0.82 ? Tipo.SALAO : Tipo.ESCRITORIO;
        if (t == anterior && t != Tipo.CORREDOR && r.nextBoolean()) t = Tipo.CORREDOR;
        return t;
    }

    private void gerar(int idx) {
        Tipo t = sortearTipo(idx);
        int comp, larg, alt;
        switch (t) {
            case INICIO -> { comp = 10; larg = 10; alt = 5; }
            case CORREDOR -> { comp = 14 + r.nextInt(7); larg = 6; alt = 5; }
            case QUARTO -> { comp = 11 + r.nextInt(4); larg = 11 + r.nextInt(3); alt = 5; }
            case SALAO -> { comp = 17 + r.nextInt(5); larg = 13 + r.nextInt(3); alt = 7; }
            case ESCRITORIO -> { comp = 11 + r.nextInt(3); larg = 9 + r.nextInt(3); alt = 5; }
            case BIBLIOTECA -> { comp = 44; larg = 31; alt = 9; }
            case CORRIDA -> { comp = 18 + r.nextInt(5); larg = 7; alt = 6; }
            default -> { comp = 10; larg = 9; alt = 6; }
        }
        Comodo ant = comodos.isEmpty() ? null : comodos.get(comodos.size() - 1);
        int x0 = ant == null ? 0 : ant.x1;
        int zE = ant == null ? laneZ : ant.zS;
        // a faixa de z do comodo contem a porta de entrada com folga e nao se afasta muito do meio da faixa
        int minZ0 = zE - larg + 2, maxZ0 = zE - 2;
        int alvo = laneZ - larg / 2;
        int z0 = Math.max(minZ0, Math.min(maxZ0, alvo + r.nextInt(5) - 2));
        int zS;
        if (t == Tipo.CORRIDA || t == Tipo.CORREDOR) zS = zE;
        else {
            int zMin = z0 + 2, zMax = z0 + larg - 2;
            zS = zMin + r.nextInt(Math.max(1, zMax - zMin + 1));
            // puxa de volta para perto do meio da faixa
            if (Math.abs(zS - laneZ) > 6) zS = Math.max(zMin, Math.min(zMax, laneZ + (zS > laneZ ? 4 : -4)));
        }
        boolean escuro = idx >= 8 && (t == Tipo.QUARTO || t == Tipo.CORREDOR || t == Tipo.ESCRITORIO) && !naCorrida(idx)
                && idx < biblioteca - 2 && r.nextDouble() < 0.22;
        Comodo c = new Comodo(idx, t, x0, comp, z0, larg, alt, zE, zS, escuro);
        construir(c);
        comodos.add(c);
    }

    // ================================================================== paredes, chao e teto

    private Material papel(Comodo c) {
        return switch (c.tipo) {
            case CORREDOR, CORRIDA -> Material.BROWN_TERRACOTTA;
            case QUARTO -> c.idx % 2 == 0 ? Material.RED_TERRACOTTA : Material.GREEN_TERRACOTTA;
            case SALAO -> Material.TERRACOTTA;
            case ESCRITORIO -> Material.LIGHT_GRAY_TERRACOTTA;
            case BIBLIOTECA -> Material.BROWN_TERRACOTTA;
            case SAIDA -> Material.WHITE_CONCRETE;
            default -> Material.BROWN_TERRACOTTA;
        };
    }

    private void construir(Comodo c) {
        Material papel = papel(c);
        boolean saida = c.tipo == Tipo.SAIDA;
        for (int x = c.x0; x <= c.x1; x++)
            for (int z = c.z0; z <= c.z1; z++) {
                boolean parede = x == c.x0 || x == c.x1 || z == c.z0 || z == c.z1;
                // chao
                Material chao = saida ? Material.QUARTZ_BLOCK : c.tipo == Tipo.BIBLIOTECA ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
                c.por(x, Y - 1, z, chao);
                c.por(x, Y - 2, z, Material.STONE);
                // teto
                c.por(x, Y + c.alt, z, saida ? Material.SEA_LANTERN : c.tipo == Tipo.SALAO ? Material.STRIPPED_DARK_OAK_WOOD : Material.SPRUCE_PLANKS);
                c.por(x, Y + c.alt + 1, z, Material.STONE);
                if (!parede) {
                    for (int y = Y; y < Y + c.alt; y++) c.por(x, y, z, Material.AIR);
                    continue;
                }
                boolean portaE = x == c.x0 && z == c.zE, portaS = x == c.x1 && z == c.zS;
                // a moldura da porta de entrada e do comodo anterior: nao cobre
                boolean molduraE = c.idx > 0 && x == c.x0 && Math.abs(z - c.zE) <= 1;
                for (int y = Y; y < Y + c.alt; y++) {
                    if ((portaE || portaS) && (y == Y || y == Y + 1)) continue;
                    if (molduraE && y <= Y + 2) continue;
                    Material m;
                    if (saida) m = Material.WHITE_CONCRETE;
                    else if (y == Y) m = Material.DARK_OAK_PLANKS;
                    else if (y == Y + 1) m = Material.STRIPPED_DARK_OAK_WOOD;
                    else if (y == Y + c.alt - 1) m = Material.DARK_OAK_PLANKS;
                    else m = papel;
                    c.por(x, y, z, m);
                }
            }
        if (saida) {
            // o fim do hotel: sem porta de saida
            c.por(c.x1, Y, c.zS, Material.WHITE_CONCRETE);
            c.por(c.x1, Y + 1, c.zS, Material.WHITE_CONCRETE);
        } else {
            // porta de saida (a de entrada e a de saida do comodo anterior) com moldura
            c.por(c.x1, Y, c.zS, Material.DARK_OAK_DOOR, "facing=west,half=lower,hinge=left,open=false");
            c.por(c.x1, Y + 1, c.zS, Material.DARK_OAK_DOOR, "facing=west,half=upper,hinge=left,open=false");
            for (int y = Y; y <= Y + 2; y++) {
                c.por(c.x1, y, c.zS - 1, Material.STRIPPED_SPRUCE_LOG, "axis=y");
                c.por(c.x1, y, c.zS + 1, Material.STRIPPED_SPRUCE_LOG, "axis=y");
            }
            c.por(c.x1, Y + 2, c.zS, Material.STRIPPED_SPRUCE_LOG, "axis=z");
        }
        if (c.idx == 0) elevador(c);
        Map<Long, Boolean> ocupado = new HashMap<>();
        switch (c.tipo) {
            case INICIO -> inicio(c, ocupado);
            case CORREDOR -> corredor(c, ocupado);
            case QUARTO -> quarto(c, ocupado);
            case SALAO -> salao(c, ocupado);
            case ESCRITORIO -> escritorio(c, ocupado);
            case BIBLIOTECA -> biblioteca(c, ocupado);
            case CORRIDA -> corrida(c, ocupado);
            case SAIDA -> saidaFinal(c);
        }
        if (c.tipo != Tipo.SAIDA && c.tipo != Tipo.BIBLIOTECA) luzes(c, ocupado);
        if (c.idx >= 10 && (c.tipo == Tipo.QUARTO || c.tipo == Tipo.SALAO || c.tipo == Tipo.ESCRITORIO) && c.idx < biblioteca - 2
                && !naCorrida(c.idx) && r.nextDouble() < 0.16) retrato(c, ocupado);
    }

    /** O Retrato: um quadro na parede norte com um rosto. Encarar ele muito tempo e uma ma ideia. */
    private void retrato(Comodo c, Map<Long, Boolean> o) {
        if (c.x1 - c.x0 < 8) return;
        int x = c.x0 + 3 + r.nextInt(c.x1 - c.x0 - 6);
        if (o.containsKey(chave2(x, c.z0 + 1)) || o.containsKey(chave2(x - 1, c.z0 + 1)) || o.containsKey(chave2(x + 1, c.z0 + 1))) return;
        for (int dx = -1; dx <= 1; dx++)
            for (int y = Y + 1; y <= Y + 3; y++) c.por(x + dx, y, c.z0, dx == 0 && y == Y + 2 ? Material.BLACK_CONCRETE : Material.STRIPPED_DARK_OAK_WOOD);
        c.retrato = new int[] {x, Y + 2, c.z0};
    }

    // ================================================================== mobilia

    private void ocupar(Map<Long, Boolean> o, int x, int z) {
        o.put(chave2(x, z), true);
    }

    /** Luzes: lanternas penduradas no teto pelo caminho (ou velas, nos comodos escuros). */
    private void luzes(Comodo c, Map<Long, Boolean> o) {
        int passo = c.tipo == Tipo.SALAO ? 6 : 5;
        for (int x = c.x0 + 3; x < c.x1 - 1; x += passo) {
            int z = (int) Math.floor(c.zNoCaminho(x + 0.5));
            if (c.escuro) {
                int zz = r.nextBoolean() ? c.z0 + 1 : c.z1 - 1;
                if (r.nextDouble() < 0.35 && c.livreParaMovel(x, zz, o)) {
                    c.por(x, Y, zz, Material.CANDLE, "candles=" + (1 + r.nextInt(2)) + ",lit=true");
                    ocupar(o, x, zz);
                }
                continue;
            }
            c.por(x, Y + c.alt - 1, z, Material.IRON_CHAIN, "axis=y");
            if (c.alt >= 6) c.por(x, Y + c.alt - 2, z, Material.IRON_CHAIN, "axis=y");
            int ly = Y + c.alt - (c.alt >= 6 ? 3 : 2);
            c.por(x, ly, z, Material.LANTERN, "hanging=true");
            c.luzes.add(new int[] {x, ly, z});
        }
    }

    /** Armario encostado na parede norte (z0) ou sul (z1): 1x1x2 com porta de abeto. */
    private boolean armario(Comodo c, Map<Long, Boolean> o, int x, boolean norte) {
        int zIn = norte ? c.z0 + 1 : c.z1 - 1, zPorta = norte ? c.z0 + 2 : c.z1 - 2, zFora = norte ? c.z0 + 3 : c.z1 - 3;
        for (int dx = -1; dx <= 1; dx++) if (!c.livreParaMovel(x + dx, zIn, o)) return false;
        if (!c.livreParaMovel(x, zPorta, o) || zFora <= c.z0 || zFora >= c.z1 || o.containsKey(chave2(x, zFora))) return false;
        if (Math.abs(zFora - (norte ? c.z0 : c.z1)) < 3) return false;
        for (int dx = -1; dx <= 1; dx++) {
            ocupar(o, x + dx, zIn);
            if (dx != 0) {
                c.por(x + dx, Y, zIn, Material.SPRUCE_PLANKS);
                c.por(x + dx, Y + 1, zIn, Material.SPRUCE_PLANKS);
            }
            c.por(x + dx, Y + 2, zIn, Material.SPRUCE_SLAB, "type=bottom");
        }
        c.por(x, Y, zIn, Material.AIR);
        c.por(x, Y + 1, zIn, Material.AIR);
        String f = norte ? "south" : "north";
        c.por(x, Y, zPorta, Material.SPRUCE_DOOR, "facing=" + f + ",half=lower,hinge=left,open=false");
        c.por(x, Y + 1, zPorta, Material.SPRUCE_DOOR, "facing=" + f + ",half=upper,hinge=left,open=false");
        ocupar(o, x, zPorta);
        ocupar(o, x, zFora);
        c.armarios.add(new Armario(x, zIn, zPorta, zFora, norte));
        return true;
    }

    private void armarios(Comodo c, Map<Long, Boolean> o, int quantos) {
        int feitos = 0;
        for (int t = 0; t < 30 && feitos < quantos; t++) {
            int x = c.x0 + 3 + r.nextInt(Math.max(1, c.x1 - c.x0 - 5));
            if (armario(c, o, x, r.nextBoolean())) feitos++;
        }
    }

    private boolean gaveta(Comodo c, Map<Long, Boolean> o, int x, int z) {
        if (!c.livreParaMovel(x, z, o)) return false;
        c.por(x, Y, z, Material.BARREL, "facing=up,open=false");
        ocupar(o, x, z);
        c.gavetas.add(new int[] {x, Y, z});
        return true;
    }

    private void gavetas(Comodo c, Map<Long, Boolean> o, int quantas) {
        int feitas = 0;
        for (int t = 0; t < 40 && feitas < quantas; t++) {
            int x = c.x0 + 2 + r.nextInt(Math.max(1, c.x1 - c.x0 - 3));
            int z = r.nextBoolean() ? c.z0 + 1 : c.z1 - 1;
            if (gaveta(c, o, x, z)) feitas++;
        }
    }

    /** Tapete pelo caminho entre as portas. */
    private void tapete(Comodo c, Material m) {
        for (int x = c.x0 + 1; x < c.x1; x++) {
            int z = (int) Math.floor(c.zNoCaminho(x + 0.5));
            c.por(x, Y, z, m);
        }
    }

    /** Quadro na parede (um bloco "pintado" com moldura) no alto. */
    private void quadros(Comodo c) {
        for (int x = c.x0 + 3; x < c.x1 - 2; x += 4 + r.nextInt(3)) {
            Material q = switch (r.nextInt(4)) {
                case 0 -> Material.BLACK_GLAZED_TERRACOTTA;
                case 1 -> Material.RED_GLAZED_TERRACOTTA;
                case 2 -> Material.GRAY_GLAZED_TERRACOTTA;
                default -> Material.BROWN_GLAZED_TERRACOTTA;
            };
            c.por(x, Y + 2, r.nextBoolean() ? c.z0 : c.z1, q);
        }
    }

    /**
     * O elevador (atras da parede de entrada do inicio): cabine 3x3 com grade no fundo (por ela se ve o poco passando)
     * e porta de ferro que abre para a recepcao quando ele chega la embaixo.
     */
    private void elevador(Comodo c) {
        int x0 = c.x0, z = c.zE;
        for (int x = x0 - 4; x <= x0 - 1; x++)
            for (int dz = -2; dz <= 2; dz++) {
                int zz = z + dz;
                boolean lado = dz == -2 || dz == 2, fundo = x == x0 - 4;
                c.por(x, Y - 1, zz, lado || fundo ? Material.IRON_BLOCK : Material.POLISHED_ANDESITE);
                c.por(x, Y + 3, zz, x == x0 - 2 && dz == 0 ? Material.SEA_LANTERN : Material.IRON_BLOCK);
                for (int y = Y; y <= Y + 2; y++) {
                    if (lado) c.por(x, y, zz, y == Y + 1 ? Material.STRIPPED_DARK_OAK_WOOD : Material.IRON_BLOCK);
                    else if (fundo) c.por(x, y, zz, Material.IRON_BARS, "east=false,west=false,north=" + (dz > -1) + ",south=" + (dz < 1));
                    else c.por(x, y, zz, Material.AIR);
                }
            }
        c.por(x0, Y, z, Material.IRON_DOOR, "facing=east,half=lower,hinge=left,open=false");
        c.por(x0, Y + 1, z, Material.IRON_DOOR, "facing=east,half=upper,hinge=left,open=false");
    }

    private void inicio(Comodo c, Map<Long, Boolean> o) {
        tapete(c, Material.RED_CARPET);
        // balcao da recepcao
        for (int z = c.z0 + 2; z <= c.z0 + 4; z++) {
            c.por(c.x0 + 5, Y, z, Material.DARK_OAK_PLANKS);
            ocupar(o, c.x0 + 5, z);
        }
        c.por(c.x0 + 5, Y + 1, c.z0 + 3, Material.BELL, "attachment=floor,facing=west");
        quadros(c);
    }

    private void corredor(Comodo c, Map<Long, Boolean> o) {
        tapete(c, Material.RED_CARPET);
        // portas falsas dos quartos nas paredes do lado
        for (int x = c.x0 + 4; x < c.x1 - 3; x += 5 + r.nextInt(2)) {
            boolean norte = r.nextBoolean();
            int z = norte ? c.z0 : c.z1;
            String f = norte ? "south" : "north";
            c.por(x, Y, z, Material.DARK_OAK_DOOR, "facing=" + f + ",half=lower,hinge=left,open=false");
            c.por(x, Y + 1, z, Material.DARK_OAK_DOOR, "facing=" + f + ",half=upper,hinge=left,open=false");
        }
        quadros(c);
        armarios(c, o, 1 + r.nextInt(2));
        gavetas(c, o, 1 + r.nextInt(2));
    }

    private void quarto(Comodo c, Map<Long, Boolean> o) {
        armarios(c, o, 2);
        // cama(s) encostadas na parede
        int camas = 1 + r.nextInt(2);
        for (int k = 0; k < camas; k++) {
            for (int t = 0; t < 15; t++) {
                int x = c.x0 + 2 + r.nextInt(Math.max(1, c.x1 - c.x0 - 4));
                boolean norte = r.nextBoolean();
                int zp = norte ? c.z0 + 1 : c.z1 - 1, zf = norte ? c.z0 + 2 : c.z1 - 2;
                if (!c.livreParaMovel(x, zp, o) || !c.livreParaMovel(x, zf, o)) continue;
                String f = norte ? "north" : "south";
                Material cama = r.nextBoolean() ? Material.RED_BED : Material.WHITE_BED;
                c.por(x, Y, zp, cama, "facing=" + f + ",part=head");
                c.por(x, Y, zf, cama, "facing=" + f + ",part=foot");
                ocupar(o, x, zp);
                ocupar(o, x, zf);
                gaveta(c, o, x + 1, zp);
                break;
            }
        }
        // tapete no meio
        int mx = (int) c.meioX(), mz = (int) c.meioZ();
        for (int x = mx - 1; x <= mx + 1; x++)
            for (int z = mz - 1; z <= mz + 1; z++) if (c.livreParaMovel(x, z, o)) c.por(x, Y, z, Material.BROWN_CARPET);
        quadros(c);
        gavetas(c, o, 2);
    }

    private void salao(Comodo c, Map<Long, Boolean> o) {
        tapete(c, Material.RED_CARPET);
        armarios(c, o, 2 + r.nextInt(2));
        // pilares
        for (int x = c.x0 + 4; x < c.x1 - 3; x += 5)
            for (int z : new int[] {c.z0 + 3, c.z1 - 3}) {
                if (!c.livreParaMovel(x, z, o) || Math.abs(z - c.zNoCaminho(x + 0.5)) < 2) continue;
                for (int y = Y; y < Y + c.alt; y++) c.por(x, y, z, Material.STRIPPED_DARK_OAK_LOG, "axis=y");
                ocupar(o, x, z);
            }
        // sofas (escadas) e plantas
        for (int t = 0; t < 6; t++) {
            int x = c.x0 + 2 + r.nextInt(Math.max(1, c.x1 - c.x0 - 4));
            boolean norte = r.nextBoolean();
            int z = norte ? c.z0 + 1 : c.z1 - 1;
            if (!c.livreParaMovel(x, z, o) || !c.livreParaMovel(x + 1, z, o)) continue;
            String f = norte ? "south" : "north";
            c.por(x, Y, z, Material.DARK_OAK_STAIRS, "facing=" + (norte ? "north" : "south") + ",half=bottom,shape=straight");
            c.por(x + 1, Y, z, Material.DARK_OAK_STAIRS, "facing=" + (norte ? "north" : "south") + ",half=bottom,shape=straight");
            ocupar(o, x, z);
            ocupar(o, x + 1, z);
            if (c.livreParaMovel(x + 2, z, o)) {
                c.por(x + 2, Y, z, Material.POTTED_DEAD_BUSH);
                ocupar(o, x + 2, z);
            }
        }
        quadros(c);
        gavetas(c, o, 2);
    }

    private void escritorio(Comodo c, Map<Long, Boolean> o) {
        armarios(c, o, 1);
        // estantes nas paredes do lado
        for (int x = c.x0 + 2; x < c.x1 - 1; x++)
            for (int z : new int[] {c.z0 + 1, c.z1 - 1}) {
                if (!c.livreParaMovel(x, z, o) || r.nextDouble() < 0.35) continue;
                c.por(x, Y, z, Material.BOOKSHELF);
                c.por(x, Y + 1, z, Material.BOOKSHELF);
                ocupar(o, x, z);
            }
        // mesas no meio (fora do caminho)
        for (int t = 0; t < 4; t++) {
            int x = c.x0 + 3 + r.nextInt(Math.max(1, c.x1 - c.x0 - 5)), z = c.z0 + 2 + r.nextInt(Math.max(1, c.z1 - c.z0 - 3));
            if (Math.abs(z - c.zNoCaminho(x + 0.5)) < 2 || !c.livreParaMovel(x, z, o) || !c.livreParaMovel(x + 1, z, o)) continue;
            c.por(x, Y, z, Material.SPRUCE_SLAB, "type=top");
            c.por(x + 1, Y, z, Material.SPRUCE_SLAB, "type=top");
            ocupar(o, x, z);
            ocupar(o, x + 1, z);
            if (r.nextBoolean()) c.por(x, Y + 1, z, Material.CANDLE, "candles=2,lit=" + !c.escuro);
        }
        gavetas(c, o, 3);
    }

    /** Biblioteca (a ultima sala): fileiras de estantes, mesas com livros e a Figura andando. */
    private void biblioteca(Comodo c, Map<Long, Boolean> o) {
        // fileiras de estantes em z, com corredores de 2 blocos entre elas e um corredor largo no meio (x)
        int meioZ = (c.z0 + c.z1) / 2;
        for (int x = c.x0 + 4; x <= c.x1 - 4; x += 4) {
            for (int z = c.z0 + 2; z <= c.z1 - 2; z++) {
                if (Math.abs(z - meioZ) <= 1 || Math.abs(z - c.zE) <= 1 && x < c.x0 + 6 || Math.abs(z - c.zS) <= 1 && x > c.x1 - 6) continue;
                if ((z - c.z0) % 7 == 0) continue; // passagens entre os blocos de estantes
                for (int y = Y; y < Y + 4; y++) c.por(x, y, z, Material.BOOKSHELF);
                ocupar(o, x, z);
            }
        }
        // estantes nas paredes
        for (int x = c.x0 + 1; x < c.x1; x++)
            for (int z : new int[] {c.z0 + 1, c.z1 - 1}) {
                if (!c.livreParaMovel(x, z, o) || x % 5 == 0) continue;
                for (int y = Y; y < Y + 3; y++) c.por(x, y, z, Material.BOOKSHELF);
                ocupar(o, x, z);
            }
        // velas no chao, no meio das fileiras (sem atrapalhar o caminho: vela nao bloqueia)
        for (int x = c.x0 + 6; x < c.x1 - 5; x += 8)
            for (int z = c.z0 + 4; z < c.z1 - 3; z += 7) {
                if (Math.abs(z - meioZ) <= 2 || o.containsKey(chave2(x, z))) continue;
                c.por(x, Y, z, Material.CANDLE, "candles=" + (2 + r.nextInt(3)) + ",lit=true");
            }
        // lustres
        for (int x = c.x0 + 6; x < c.x1 - 3; x += 8) {
            for (int y = Y + 5; y < Y + c.alt; y++) c.por(x, y, meioZ, Material.IRON_CHAIN, "axis=y");
            c.por(x, Y + 4, meioZ, Material.LANTERN, "hanging=true");
            c.luzes.add(new int[] {x, Y + 4, meioZ});
        }
        // lugares dos livros: no chao livre ao lado das estantes, espalhados
        for (int x = c.x0 + 3; x < c.x1 - 2; x++)
            for (int z = c.z0 + 2; z < c.z1 - 1; z++) {
                if (o.containsKey(chave2(x, z)) || Math.abs(z - meioZ) <= 1) continue;
                boolean ladoEstante = o.containsKey(chave2(x + 1, z)) || o.containsKey(chave2(x - 1, z));
                if (ladoEstante && (x + z) % 3 == 0) c.livros.add(new int[] {x, z});
            }
        armarios(c, o, 2);
    }

    /** Corredor da perseguicao: muretas para pular e as portas ja abertas. */
    private void corrida(Comodo c, Map<Long, Boolean> o) {
        tapete(c, Material.RED_CARPET);
        int x = c.x0 + 6 + r.nextInt(3);
        while (x < c.x1 - 4) {
            for (int z = c.z0 + 1; z < c.z1; z++) {
                if (r.nextDouble() < 0.25 && Math.abs(z - c.zS) > 0) continue;
                c.por(x, Y, z, Material.DARK_OAK_PLANKS);
            }
            c.muretas.add(x);
            x += 6 + r.nextInt(4);
        }
        quadros(c);
        // a porta de saida da perseguicao fica aberta (ninguem para no meio)
        int i = c.blocos.size();
        while (--i >= 0) {
            Bloco b = c.blocos.get(i);
            if (b.x() == c.x1 && b.z() == c.zS && b.m() == Material.DARK_OAK_DOOR) c.blocos.set(i, new Bloco(b.x(), b.y(), b.z(), b.m(), b.estado().replace("open=false", "open=true")));
        }
    }

    private void saidaFinal(Comodo c) {
        for (int x = c.x0 + 1; x < c.x1; x++)
            for (int z = c.z0 + 1; z < c.z1; z++) if ((x + z) % 3 == 0) c.por(x, Y, z, Material.WHITE_CARPET);
    }

    // ================================================================== consultas

    /** Bloco que fica no lugar (o ultimo colocado) dentro do comodo; null se nenhum. */
    static Bloco blocoEm(Comodo c, int x, int y, int z) {
        Bloco achado = null;
        for (Bloco b : c.blocos) if (b.x() == x && b.y() == y && b.z() == z) achado = b;
        return achado;
    }

    /** O bloco deixa passar (para o teste de caminho). */
    static boolean passa(Material m) {
        if (m == null || m == Material.AIR) return true;
        String n = m.name();
        return n.endsWith("_CARPET") || n.equals("CANDLE") || n.equals("LANTERN") || n.equals("IRON_CHAIN");
    }
}
