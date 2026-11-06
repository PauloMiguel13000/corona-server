package br.servidor.vovo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * A casa da Vovo (versao 4, bem grande): dois andares com 12 comodos cada (grade de 4 x 3), porao com 7 salas,
 * garagem e um esgoto enorme embaixo do quintal, onde mora a aranha. Casa original feita para este minigame.
 *
 * Grade: colunas x 1..12 | 14..25 | 27..38 | 40..51 (paredes em x=0,13,26,39,52);
 *        linhas  z 1..11 | 13..23 | 25..35 (paredes em z=0,12,24,36).
 * Terreo: piso y=63 (anda em 64). Andar de cima: piso y=69 (anda em 70). Porao: piso y=57 (anda em 58).
 * Esgoto: piso y=51 (anda em 52). Garagem: x 53..65, z 13..27. Porta da frente dupla em x=32..33, z=36.
 */
final class Casa {

    static final String MUNDO = "vovo";
    static final int VERSAO = 5;
    static final int PISO_ESGOTO = 51, PISO_PORAO = 57, PISO_TERREO = 63, PISO_ANDAR = 69, TETO = 75;
    static final int COLS = 4, LINS = 3;
    /** Interior da casa (sem a garagem). */
    static final int IX1 = 1, IX2 = 51, IZ1 = 1, IZ2 = 35;
    static final int PAREDE_LESTE = 52, PAREDE_SUL = 36;

    static int x0(int c) {
        return 1 + 13 * c;
    }

    static int z0(int r) {
        return 1 + 12 * r;
    }

    enum Zona { CASA, PORAO, GARAGEM, ESGOTO }

    /** Lugar onde um item pode aparecer: em cima do bloco (x, y, z). */
    record Ponto(int x, int y, int z, Zona zona, String sala) {}

    /** Armario/esconderijo: o jogador fica no bloco (x, y, z). */
    record Esconderijo(int x, int y, int z, String sala) {}

    record Ronda(double x, double y, double z, Zona zona, String sala) {}

    // portas e trancas
    static final int[] PORTA_FRENTE = {32, 64, 36};
    static final int[] PORTA_FRENTE2 = {33, 64, 36};
    static final int[] PORTA_PORAO = {15, 64, 8};
    static final int[] PORTA_PORAO2 = {15, 64, 9};
    static final int[] PORTA_GARAGEM = {52, 64, 18};
    static final int[] PORTA_GARAGEM2 = {52, 64, 17};
    static final int[] CAIXA_FORCA = {33, 59, 1};
    static final int[] TAMPA = {6, 57, 18};
    /** Bloco da parede onde cada quadro de tranca da porta da frente fica preso (cadeado, corrente, tabuas, energia). */
    static final int[][] QUADROS_TRANCA = {{29, 65, 36}, {30, 65, 36}, {35, 65, 36}, {36, 65, 36}};

    // placas de texto
    static final double[] TXT_PORTA = {33.0, 68.0, 35.35};
    static final double[] TXT_PORAO = {14.3, 67.0, 9.0};
    static final double[] TXT_GARAGEM = {51.3, 67.0, 18.0};
    static final double[] TXT_CAIXA = {33.5, 61.0, 2.2};
    static final double[] TXT_CARRO = {59.5, 67.4, 23.6};
    static final double[] TXT_TAMPA = {6.5, 59.6, 18.5};
    static final double[] TXT_GRADE = {52.3, 55.0, 86.5};

    // garagem e carro
    static final int GX1 = 53, GX2 = 65, GZ1 = 13, GZ2 = 27;
    static final double[] FUMACA = {59.5, 64.5, 15.4};
    static final double[] CENTRO_GRADE = {51.5, 53.5, 86.5};

    final List<Ponto> pontos = new ArrayList<>();
    final List<Esconderijo> esconderijos = new ArrayList<>();
    final List<Ronda> ronda = new ArrayList<>();
    /** Lampadas que acendem quando a energia volta (redstone apagada -> luz). */
    final List<int[]> lampadas = new ArrayList<>();
    /** Blocos do portao da garagem (somem quando o carro liga). */
    final List<int[]> portao = new ArrayList<>();
    /** Farois do carro. */
    final List<int[]> farois = new ArrayList<>();
    /** Grade de ferro no fim do esgoto (some quando serrada). */
    final List<int[]> grade = new ArrayList<>();

    // ================================================================== os comodos

    /** Um comodo da grade (andar 0 = porao, 1 = terreo, 2 = andar de cima). */
    record Comodo(int c, int r, int andar, String id, String nome, String tipo) {
        int x0() {
            return Casa.x0(c);
        }

        int z0() {
            return Casa.z0(r);
        }

        int y() {
            return andar == 0 ? PISO_PORAO + 1 : andar == 1 ? PISO_TERREO + 1 : PISO_ANDAR + 1;
        }

        int topo() {
            return y() + 4;
        }

        Zona zona() {
            return andar == 0 ? Zona.PORAO : Zona.CASA;
        }
    }

    final List<Comodo> comodos = new ArrayList<>();

    private void comodo(int c, int r, int andar, String nome, String tipo) {
        String pre = andar == 0 ? "p" : andar == 1 ? "t" : "a";
        comodos.add(new Comodo(c, r, andar, pre + "_" + c + "_" + r, nome, tipo));
    }

    private void definirComodos() {
        // terreo
        comodo(0, 0, 1, "cozinha", "cozinha");
        comodo(1, 0, 1, "corredor", "corredor");
        comodo(2, 0, 1, "banheiro", "banheiro");
        comodo(3, 0, 1, "lavanderia", "lavanderia");
        comodo(0, 1, 1, "sala de estar", "sala");
        comodo(1, 1, 1, "biblioteca", "biblioteca");
        comodo(2, 1, 1, "hall", "hall");
        comodo(3, 1, 1, "sala de jantar", "jantar");
        comodo(0, 2, 1, "despensa", "despensa");
        comodo(1, 2, 1, "sala de visitas", "visitas");
        comodo(2, 2, 1, "entrada", "entrada");
        comodo(3, 2, 1, "escritório de baixo", "escritorio");
        // andar de cima
        comodo(0, 0, 2, "escritório", "escritorio");
        comodo(1, 0, 2, "corredor de cima", "corredorCima");
        comodo(2, 0, 2, "depósito", "deposito");
        comodo(3, 0, 2, "quarto de hóspedes", "quarto");
        comodo(0, 1, 2, "quarto", "quartoInicio");
        comodo(1, 1, 2, "sala da TV", "tv");
        comodo(2, 1, 2, "banheiro de cima", "banheiro");
        comodo(3, 1, 2, "quarto da vovó", "quartoVovo");
        comodo(0, 2, 2, "quarto das crianças", "criancas");
        comodo(1, 2, 2, "sala de leitura", "biblioteca");
        comodo(2, 2, 2, "sala de costura", "costura");
        comodo(3, 2, 2, "quartinho da bagunça", "deposito");
        // porao (so algumas salas sao escavadas)
        comodo(1, 0, 0, "escada do porão", "escadaPorao");
        comodo(2, 0, 0, "porão", "porao");
        comodo(3, 0, 0, "depósito do porão", "deposito");
        comodo(0, 1, 0, "caldeira", "caldeira");
        comodo(1, 1, 0, "oficina", "oficina");
        comodo(2, 1, 0, "adega", "adega");
        comodo(3, 1, 0, "câmara fria", "camaraFria");
    }

    Comodo comodo(int c, int r, int andar) {
        for (Comodo k : comodos) if (k.c() == c && k.r() == r && k.andar() == andar) return k;
        return null;
    }

    Casa() {
        definirComodos();
        montarMapa();
        for (Comodo k : comodos) {
            mobiliar(null, k);
            ronda.add(new Ronda(k.x0() + 9.5, k.y(), k.z0() + 6.5, k.zona(), k.nome()));
        }
        // garagem
        p(65, 64, 14, Zona.GARAGEM, "garagem");
        p(53, 64, 27, Zona.GARAGEM, "garagem");
        p(65, 64, 24, Zona.GARAGEM, "garagem");
        esconderijos.add(new Esconderijo(65, 64, 27, "garagem"));
        ronda.add(new Ronda(55.5, 64, 20.5, Zona.GARAGEM, "garagem"));
        ronda.add(new Ronda(63.5, 64, 20.5, Zona.GARAGEM, "garagem"));
        // esgoto
        p(9, 52, 22, Zona.ESGOTO, "entrada do esgoto");
        p(-15, 52, 38, Zona.ESGOTO, "esgoto (sala oeste)");
        p(54, 52, 60, Zona.ESGOTO, "esgoto (sala leste)");
        p(32, 52, 78, Zona.ESGOTO, "esgoto (sala sul)");
        p(0, 52, 62, Zona.ESGOTO, "esgoto (salinha)");
        p(34, 52, 38, Zona.ESGOTO, "esgoto (sala norte)");
        p(-8, 52, 80, Zona.ESGOTO, "esgoto (beco)");
        p(25, 52, 57, Zona.ESGOTO, "ninho da aranha");
        p(81, 52, 70, Zona.ESGOTO, "esgoto (sala do tunel leste)");

        // lampadas que acendem com a energia
        int[][] lamp = {{0, 1, 1}, {2, 1, 1}, {2, 2, 1}, {3, 0, 1}, {2, 0, 0}, {1, 1, 0}, {0, 1, 0}};
        for (int[] l : lamp) {
            Comodo k = comodo(l[0], l[1], l[2]);
            lampadas.add(new int[] {k.x0() + 2, k.topo(), k.z0() + 5});
        }
        lampadas.add(new int[] {55, 68, 15});

        for (int x = 57; x <= 61; x++)
            for (int y = 64; y <= 67; y++) portao.add(new int[] {x, y, 28});
        farois.add(new int[] {58, 64, 22});
        farois.add(new int[] {60, 64, 22});
        for (int z = 85; z <= 87; z++)
            for (int y = 52; y <= 55; y++) grade.add(new int[] {GRADE_X, y, z});
    }

    private void p(int x, int y, int z, Zona zona, String sala) {
        pontos.add(new Ponto(x, y, z, zona, sala));
    }

    /** Pontos de ronda da aranha no esgoto (fora dos canais de agua). */
    static List<Location> caminhoAranha(World w) {
        List<Location> l = new ArrayList<>();
        double[][] c = {{-4, 34}, {40, 34}, {40, 66}, {-4, 66}, {14, 46}, {22, 54}, {16, 37}, {20, 63}, {-2, 48}, {38, 52}, {-6, 78},
                {58, 50}, {72, 50}, {72, 68}, {72, 86}, {62, 86}, {54, 86}};
        for (double[] p : c) l.add(new Location(w, p[0] + 0.5, 52, p[1] + 0.5));
        return l;
    }

    static Location ninhoAranha(World w) {
        return new Location(w, 18.5, 52, 50.5);
    }

    // ================================================================== moveis de cada tipo de comodo

    /**
     * Monta os moveis de um comodo. Com "o" nulo so registra os pontos de item e os armarios.
     * Os moveis ficam nos cantos (x local 0..3 / 8..11, z local 0..2 / 8..10): o meio e o caminho entre as portas.
     */
    private void mobiliar(Obra o, Comodo k) {
        M m = new M(o, k);
        switch (k.tipo()) {
            case "cozinha" -> {
                m.b(0, 0, 0, "minecraft:smoker[facing=south,lit=false]");
                m.barril(1, 0, true);
                m.b(2, 0, 0, "minecraft:cauldron");
                m.barril(3, 0, false);
                m.b(11, 0, 0, "minecraft:iron_block");
                m.b(11, 1, 0, "minecraft:iron_block");
                m.b(11, 0, 1, "minecraft:iron_block");
                m.b(11, 1, 1, "minecraft:iron_block");
                m.mesa(9, 9, true);
                m.mesa(10, 9, false);
                m.cadeira(9, 8, "north");
                m.cadeira(10, 8, "north");
                m.armario(0, 10, "north");
                m.tapete(4, 3, 7, 7, "white");
            }
            case "corredor" -> {
                m.barril(11, 9, true);
                m.barril(11, 10, false);
            }
            case "corredorCima" -> {
                m.barril(11, 10, true);
                m.barril(0, 10, true);
            }
            case "banheiro" -> {
                m.b(1, 0, 0, "minecraft:water_cauldron[level=2]");
                m.b(2, 0, 0, "minecraft:water_cauldron[level=2]");
                m.b(11, 0, 0, "minecraft:quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
                m.b(9, 0, 0, "minecraft:cauldron");
                m.barril(0, 9, true);
                m.barril(11, 10, true);
                m.armario(9, 10, "north");
            }
            case "lavanderia" -> {
                m.barril(0, 0, true);
                m.barril(1, 0, false);
                m.b(10, 0, 0, "minecraft:cauldron");
                m.b(11, 0, 0, "minecraft:blast_furnace[facing=south,lit=false]");
                m.barril(11, 10, true);
                m.armario(0, 10, "north");
            }
            case "sala" -> {
                // lareira na parede oeste (parede de fora)
                m.b(0, 0, 5, "minecraft:soul_campfire[lit=true,facing=east,signal_fire=false,waterlogged=false]");
                for (int dy = 0; dy <= 4; dy++) {
                    m.b(0, dy, 4, "minecraft:bricks");
                    m.b(0, dy, 6, "minecraft:bricks");
                }
                for (int dy = 2; dy <= 4; dy++) m.b(0, dy, 5, "minecraft:bricks");
                m.mesa(1, 1, true);
                m.cadeira(2, 1, "east");
                m.cadeira(1, 2, "south");
                for (int x = 8; x <= 11; x++)
                    for (int dy = 0; dy <= 2; dy++) m.b(x, dy, 10, "minecraft:bookshelf");
                m.barril(11, 0, true);
                m.armario(0, 10, "north");
                m.tapete(3, 3, 8, 7, "red");
            }
            case "biblioteca" -> {
                for (int dy = 0; dy <= 2; dy++) {
                    for (int x = 0; x <= 2; x++) {
                        m.b(x, dy, 0, "minecraft:bookshelf");
                        m.b(x, dy, 10, "minecraft:bookshelf");
                    }
                    for (int x = 9; x <= 11; x++) m.b(x, dy, 0, "minecraft:bookshelf");
                }
                m.mesa(3, 1, true);
                m.cadeira(3, 2, "south");
                m.mesa(9, 9, true);
                m.cadeira(9, 8, "north");
                m.velas(9, 1, 9, 3);
                m.armario(11, 10, "north");
            }
            case "hall" -> {
                m.barril(0, 0, true);
                m.barril(11, 10, true);
                m.b(0, 0, 10, "minecraft:decorated_pot");
                m.armario(11, 0, "south");
                m.tapete(3, 2, 8, 8, "gray");
            }
            case "jantar" -> {
                for (int x = 9; x <= 11; x++) {
                    m.mesa(x, 1, x == 10);
                    m.cadeira(x, 2, "south");
                }
                for (int x = 0; x <= 2; x++) {
                    m.mesa(x, 9, x == 1);
                    m.cadeira(x, 8, "north");
                }
                m.velas(9, 1, 1, 4);
                m.velas(0, 1, 9, 2);
                m.barril(11, 10, true);
                m.armario(0, 0, "south");
            }
            case "despensa" -> {
                m.barril(0, 0, true);
                m.barril(1, 0, false);
                m.barrilAlto(0, 1);
                m.barril(11, 0, true);
                m.barrilAlto(11, 1);
                m.barril(0, 10, true);
                m.barril(1, 10, false);
                m.barril(11, 10, true);
                m.armario(9, 10, "north");
            }
            case "visitas" -> {
                m.mesa(1, 9, true);
                m.cadeira(1, 8, "north");
                m.cadeira(2, 9, "west");
                m.barril(10, 0, true);
                m.b(11, 0, 0, "minecraft:decorated_pot");
                m.b(0, 0, 0, "minecraft:decorated_pot");
                m.b(11, 0, 10, "minecraft:decorated_pot");
                m.tapete(3, 2, 8, 8, "brown");
            }
            case "entrada" -> {
                m.b(1, 0, 10, "minecraft:decorated_pot");
                m.b(10, 0, 10, "minecraft:decorated_pot");
                m.mesa(11, 8, true);
                m.barril(0, 0, true);
                for (int dy = 0; dy <= 1; dy++) m.b(0, dy, 8, "minecraft:dark_oak_fence[east=false,west=false,north=false,south=false,waterlogged=false]");
                m.tapete(4, 6, 7, 10, "gray");
            }
            case "escritorio" -> {
                m.mesa(9, 0, true);
                m.mesa(10, 0, false);
                m.mesa(11, 0, true);
                m.cadeira(10, 1, "south");
                m.velas(10, 1, 0, 2);
                for (int z = 8; z <= 10; z++)
                    for (int dy = 0; dy <= 2; dy++) m.b(0, dy, z, "minecraft:bookshelf");
                m.armario(11, 10, "north");
                m.barril(1, 0, true);
            }
            case "deposito" -> {
                m.barril(0, 0, true);
                m.barril(1, 0, false);
                m.barrilAlto(0, 1);
                m.barril(11, 0, true);
                m.barril(10, 0, false);
                m.barril(11, 10, true);
                m.barrilAlto(10, 10);
                m.armario(0, 10, "north");
                m.b(0, 4, 0, "minecraft:cobweb");
                m.b(11, 4, 10, "minecraft:cobweb");
                m.b(11, 4, 0, "minecraft:cobweb");
            }
            case "quarto" -> {
                m.cama(1, 0, "west", "light_gray");
                m.barril(2, 0, true);
                m.barril(11, 0, true);
                m.mesa(11, 9, true);
                m.cadeira(10, 9, "east");
                m.armario(0, 10, "north");
                m.tapete(3, 3, 8, 7, "light_gray");
            }
            case "quartoInicio" -> {
                m.cama(1, 2, "west", "white");
                m.cama(1, 8, "west", "white");
                m.barril(11, 0, true);
                m.mesa(11, 10, true);
                m.cadeira(10, 10, "east");
                m.armario(0, 10, "north");
                m.tapete(3, 3, 8, 7, "brown");
            }
            case "tv" -> {
                m.b(10, 0, 0, "minecraft:black_concrete");
                m.b(11, 0, 0, "minecraft:black_concrete");
                m.b(10, 1, 0, "minecraft:black_concrete");
                m.b(11, 1, 0, "minecraft:black_concrete");
                for (int x = 9; x <= 11; x++) m.cadeira(x, 2, "south");
                m.barril(0, 0, true);
                m.barril(0, 10, true);
                m.armario(11, 10, "north");
                m.tapete(3, 3, 8, 7, "black");
            }
            case "quartoVovo" -> {
                m.cama(10, 4, "east", "red");
                m.cama(10, 5, "east", "red");
                m.barril(11, 3, true);
                m.barril(11, 6, false);
                m.velas(11, 1, 6, 4);
                m.armario(11, 10, "north");
                m.barril(0, 10, true);
                m.barril(1, 10, false);
                m.tapete(4, 2, 8, 8, "red");
            }
            case "criancas" -> {
                m.cama(1, 0, "west", "lime");
                m.cama(1, 2, "west", "light_blue");
                m.b(11, 0, 0, "minecraft:decorated_pot");
                m.barril(10, 0, true);
                m.barril(11, 10, true);
                m.armario(0, 10, "north");
                m.tapete(3, 3, 8, 7, "yellow");
            }
            case "costura" -> {
                m.mesa(0, 0, true);
                m.mesa(1, 0, false);
                m.cadeira(1, 1, "south");
                m.b(11, 0, 0, "minecraft:loom[facing=south]");
                m.barril(11, 10, true);
                m.barril(10, 10, false);
                m.armario(0, 10, "north");
            }
            case "escadaPorao" -> {
                m.barril(11, 0, true);
                m.barril(11, 10, true);
            }
            case "porao" -> {
                m.b(6, 0, 0, "minecraft:polished_deepslate");
                m.b(6, 1, 0, "minecraft:crafter[orientation=south_up,crafting=false,triggered=false]");
                m.barril(0, 0, true);
                m.barril(1, 0, false);
                m.b(11, 0, 10, "minecraft:crafting_table");
                m.ponto(11, 10);
                m.b(0, 2, 10, "minecraft:soul_wall_torch[facing=north]");
            }
            case "caldeira" -> {
                m.b(0, 0, 0, "minecraft:blast_furnace[facing=east,lit=false]");
                m.b(0, 1, 0, "minecraft:blast_furnace[facing=east,lit=false]");
                for (int dy = 2; dy <= 4; dy++) m.b(0, dy, 0, "minecraft:iron_block");
                for (int x = 1; x <= 8; x++) m.b(x, 4, 0, "minecraft:waxed_copper_block");
                m.barril(11, 10, true);
                m.barril(0, 10, true);
            }
            case "oficina" -> {
                m.mesa(0, 10, true);
                m.mesa(1, 10, false);
                m.b(11, 0, 10, "minecraft:anvil[facing=east]");
                m.barril(11, 0, true);
                m.b(0, 0, 0, "minecraft:grindstone[face=floor,facing=north]");
            }
            case "adega" -> {
                m.barril(0, 0, true);
                m.barrilAlto(1, 0);
                m.barril(11, 0, true);
                m.barrilAlto(10, 0);
                m.barril(0, 10, true);
                m.barrilAlto(1, 10);
                m.armario(11, 10, "north");
                m.b(0, 4, 0, "minecraft:cobweb");
            }
            case "camaraFria" -> {
                for (int x = 0; x <= 2; x++) {
                    m.b(x, 0, 0, "minecraft:packed_ice");
                    m.b(x, 1, 0, "minecraft:packed_ice");
                }
                m.barril(11, 10, true);
                m.barril(11, 0, true);
                m.b(0, 0, 10, "minecraft:blue_ice");
            }
            default -> { }
        }
    }

    /** Ajudante para montar moveis em coordenadas locais do comodo (x 0..11, z 0..10, dy 0 = chao). */
    private final class M {
        final Obra o;
        final Comodo k;

        M(Obra o, Comodo k) {
            this.o = o;
            this.k = k;
        }

        void b(int lx, int dy, int lz, String dados) {
            if (o != null) o.d(k.x0() + lx, k.y() + dy, k.z0() + lz, dados);
        }

        void ponto(int lx, int lz) {
            if (o == null) pontos.add(new Ponto(k.x0() + lx, k.y(), k.z0() + lz, k.zona(), k.nome()));
        }

        void barril(int lx, int lz, boolean item) {
            b(lx, 0, lz, "minecraft:barrel[facing=up,open=false]");
            if (item) ponto(lx, lz);
        }

        void barrilAlto(int lx, int lz) {
            b(lx, 0, lz, "minecraft:barrel[facing=up,open=false]");
            b(lx, 1, lz, "minecraft:barrel[facing=up,open=false]");
        }

        void mesa(int lx, int lz, boolean item) {
            b(lx, 0, lz, "minecraft:stripped_dark_oak_wood[axis=y]");
            if (item) ponto(lx, lz);
        }

        void cadeira(int lx, int lz, String encosto) {
            b(lx, 0, lz, "minecraft:spruce_stairs[facing=" + encosto + ",half=bottom,shape=straight,waterlogged=false]");
        }

        void velas(int lx, int dy, int lz, int n) {
            b(lx, dy, lz, "minecraft:candle[candles=" + n + ",lit=true,waterlogged=false]");
        }

        void cama(int lx, int lz, String facing, String cor) {
            if (o != null) o.cama(k.x0() + lx, k.y(), k.z0() + lz, facing, cor);
        }

        void armario(int lx, int lz, String frente) {
            if (o != null) o.armario(k.x0() + lx, k.y(), k.z0() + lz, frente, k.topo() + 1);
            else esconderijos.add(new Esconderijo(k.x0() + lx, k.y(), k.z0() + lz, k.nome()));
        }

        void tapete(int lx1, int lz1, int lx2, int lz2, String cor) {
            if (o == null) return;
            for (int x = lx1; x <= lx2; x++)
                for (int z = lz1; z <= lz2; z++)
                    if (o.w.getBlockAt(k.x0() + x, k.y(), k.z0() + z).getType().isAir()) b(x, 0, z, "minecraft:" + cor + "_carpet");
        }
    }

    // ================================================================== mapa de comodos (para a vovo andar entre andares)

    /** Comodo retangular num andar (0 = porao, 1 = terreo, 2 = andar de cima). */
    record Sala(String id, int andar, int x1, int z1, int x2, int z2) {
        boolean contem(double x, double z) {
            return x >= x1 && x < x2 + 1 && z >= z1 && z < z2 + 1;
        }

        double distancia(double x, double z) {
            double dx = x < x1 ? x1 - x : x > x2 + 1 ? x - x2 - 1 : 0;
            double dz = z < z1 ? z1 - z : z > z2 + 1 ? z - z2 - 1 : 0;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    record Passagem(String a, String b, double[] pa, double[] pb, String requer) {}

    final List<Sala> salas = new ArrayList<>();
    final List<Passagem> passagens = new ArrayList<>();

    private void montarMapa() {
        salas.clear();
        passagens.clear();
        for (Comodo k : comodos) salas.add(new Sala(k.id(), k.andar(), k.x0(), k.z0(), k.x0() + 11, k.z0() + 10));
        salas.add(new Sala("garagem", 1, GX1, GZ1, GX2, GZ2));
        for (Comodo a : comodos) {
            Comodo leste = comodo(a.c() + 1, a.r(), a.andar());
            if (leste != null) {
                double[] c = {x0(a.c()) + 12.5, a.y(), z0(a.r()) + 5.0};
                passagens.add(new Passagem(a.id(), leste.id(), c, c, null));
            }
            Comodo sul = comodo(a.c(), a.r() + 1, a.andar());
            if (sul != null) {
                double[] c = {x0(a.c()) + 6.0, a.y(), z0(a.r()) + 11.5};
                passagens.add(new Passagem(a.id(), sul.id(), c, c, null));
            }
        }
        passagens.add(new Passagem("t_1_0", "a_1_0", new double[] {24.5, 64, 3.5}, new double[] {17.5, 70, 3.5}, null));
        passagens.add(new Passagem("t_1_0", "p_1_0", new double[] {14.3, 64, 9.0}, new double[] {22.7, 58, 9.0}, "porao"));
        passagens.add(new Passagem("t_3_1", "garagem", new double[] {51.3, 64, 18.0}, new double[] {53.7, 64, 18.0}, "garagem"));
    }

    /** -1 = esgoto, 0 = porao, 1 = terreo, 2 = andar de cima. */
    static int andarDe(double y) {
        return y < PISO_PORAO ? -1 : y < PISO_TERREO ? 0 : y < PISO_ANDAR ? 1 : 2;
    }

    Sala salaDe(Location l) {
        int andar = andarDe(l.getY());
        Sala melhor = null;
        double md = 1e9;
        for (Sala s : salas) {
            if (s.andar() != andar) continue;
            if (s.contem(l.getX(), l.getZ())) return s;
            double d = s.distancia(l.getX(), l.getZ());
            if (d < md) {
                md = d;
                melhor = s;
            }
        }
        return melhor;
    }

    /** Pontos intermediarios (portas e escadas) para ir de "de" ate "para". Vazio = mesmo comodo (ou sem caminho). */
    Deque<Location> rota(World w, Location de, Location para, boolean poraoAberto, boolean garagemAberta) {
        Deque<Location> r = new ArrayDeque<>();
        Sala sa = salaDe(de), sb = salaDe(para);
        if (sa == null || sb == null || sa.id().equals(sb.id())) return r;
        Map<String, Passagem> veio = new HashMap<>();
        Map<String, Boolean> lado = new HashMap<>();
        LinkedList<String> fila = new LinkedList<>();
        fila.add(sa.id());
        veio.put(sa.id(), null);
        while (!fila.isEmpty()) {
            String s = fila.poll();
            if (s.equals(sb.id())) break;
            for (Passagem pg : passagens) {
                if (pg.requer() != null && (pg.requer().equals("porao") ? !poraoAberto : !garagemAberta)) continue;
                String outro = pg.a().equals(s) ? pg.b() : pg.b().equals(s) ? pg.a() : null;
                if (outro == null || veio.containsKey(outro)) continue;
                veio.put(outro, pg);
                lado.put(outro, pg.a().equals(s));
                fila.add(outro);
            }
        }
        if (!veio.containsKey(sb.id())) return r;
        String s = sb.id();
        while (veio.get(s) != null) {
            Passagem pg = veio.get(s);
            boolean deA = lado.get(s);
            double[] entra = deA ? pg.pa() : pg.pb(), sai = deA ? pg.pb() : pg.pa();
            if (sai != entra) r.addFirst(new Location(w, sai[0], sai[1], sai[2]));
            r.addFirst(new Location(w, entra[0], entra[1], entra[2]));
            s = deA ? pg.a() : pg.b();
        }
        return r;
    }

    /** No meio de uma escada (a vovo nao recalcula o caminho ai, senao sobe e desce sem parar). */
    static boolean naEscada(Location l) {
        double x = l.getX(), y = l.getY(), z = l.getZ();
        boolean subida = x >= 17.6 && x <= 24.9 && z >= 1.6 && z <= 5.4 && y > 64.2 && y < 69.95;
        boolean porao = x >= 14.9 && x <= 22.6 && z >= 7.6 && z <= 10.4 && y > 58.2 && y < 64.3;
        return subida || porao;
    }

    // ================================================================== lugares

    static Location lobby(World w) {
        return new Location(w, 33.5, 64, 56.5, 180, 0);
    }

    static Location placaLobby(World w) {
        return new Location(w, 33.5, 66.6, 52.5);
    }

    /** Onde os jogadores acordam: o quarto do andar de cima. */
    static Location inicio(World w, int i) {
        double[][] l = {{4.5, 15.5}, {4.5, 21.5}, {5.5, 18.5}, {9.5, 15.5}, {9.5, 20.5}, {7.5, 17.5}, {4.5, 18.5}, {10.5, 18.5}};
        double[] c = l[Math.floorMod(i, l.length)];
        return new Location(w, c[0], 70, c[1], -90, 0);
    }

    static boolean noQuartoInicio(int x, int y, int z) {
        return y == PISO_ANDAR && x <= 12 && z >= 13 && z <= 23;
    }

    static Location quartoVovo(World w) {
        return new Location(w, 45.5, 70, 18.5, 90, 0);
    }

    /** Para onde a vovo corre quando a porta da frente abre. */
    static Location frentePorta(World w) {
        return new Location(w, 33.0, 64, 33.5);
    }

    /** Para onde a vovo vai quando ouve barulho no esgoto (ela nao desce a escada de mao). */
    static Location sobreOBueiro(World w) {
        return new Location(w, 8.5, 58, 19.5);
    }

    static Location carroCentro(World w) {
        return new Location(w, 59.5, 65, 19);
    }

    static Location fogosCarro(World w) {
        return new Location(w, 59.5, 64, 34.5);
    }

    static Location fogosPorta(World w) {
        return new Location(w, 33.0, 64, 41.5);
    }

    static Location fogosEsgoto(World w) {
        return new Location(w, 44.5, 64, 89.5);
    }

    static Location centro(World w, int[] b) {
        return new Location(w, b[0] + 0.5, b[1] + 0.5, b[2] + 0.5);
    }

    static boolean ehPorta(Block b, int[] porta) {
        return b.getX() == porta[0] && b.getZ() == porta[2] && (b.getY() == porta[1] || b.getY() == porta[1] + 1);
    }

    static boolean ehPortaFrente(Block b) {
        return ehPorta(b, PORTA_FRENTE) || ehPorta(b, PORTA_FRENTE2);
    }

    static boolean ehPortaPorao(Block b) {
        return ehPorta(b, PORTA_PORAO) || ehPorta(b, PORTA_PORAO2);
    }

    static boolean ehPortaGaragem(Block b) {
        return ehPorta(b, PORTA_GARAGEM) || ehPorta(b, PORTA_GARAGEM2);
    }

    static boolean noCarro(Block b) {
        return b.getX() >= 57 && b.getX() <= 61 && b.getY() >= 64 && b.getY() <= 66 && b.getZ() >= 16 && b.getZ() <= 22;
    }

    static boolean noPortao(Block b) {
        return b.getX() >= 57 && b.getX() <= 61 && b.getY() >= 64 && b.getY() <= 67 && b.getZ() == 28;
    }

    static boolean ehCaixa(Block b) {
        return b.getX() == CAIXA_FORCA[0] && b.getZ() == CAIXA_FORCA[2] && (b.getY() == CAIXA_FORCA[1] || b.getY() == CAIXA_FORCA[1] - 1);
    }

    static boolean ehTampa(Block b) {
        return b.getX() == TAMPA[0] && b.getZ() == TAMPA[2] && b.getY() >= TAMPA[1] - 1 && b.getY() <= TAMPA[1];
    }

    static boolean naGrade(Block b) {
        return b.getX() == GRADE_X && b.getZ() >= 85 && b.getZ() <= 87 && b.getY() >= 51 && b.getY() <= 56;
    }

    private static boolean caixa(Location l, double x1, double x2, double y1, double y2, double z1, double z2) {
        return l.getX() >= x1 && l.getX() < x2 && l.getY() >= y1 && l.getY() < y2 && l.getZ() >= z1 && l.getZ() < z2;
    }

    /** Grade de ferro (a Serra corta) entre o tunel estreito e a sala da escada. */
    static final int GRADE_X = 51;
    /** Escada de mao da sala da saida ate o bueiro da rua. */
    static final int[] ESCADA_SAIDA = {44, 86};

    /** Subiu a escada do bueiro (depois da grade): fugiu. */
    static boolean naSaidaEsgoto(Location l) {
        return caixa(l, ESCADA_SAIDA[0] - 0.4, ESCADA_SAIDA[0] + 1.4, 58.5, 66, ESCADA_SAIDA[1] - 0.4, ESCADA_SAIDA[1] + 1.4);
    }

    /** Ja esta na escada (para abrir a tampa la de cima quando chega perto). */
    static boolean naEscadaSaida(Location l) {
        return caixa(l, ESCADA_SAIDA[0] - 0.4, ESCADA_SAIDA[0] + 1.4, 55.5, 66, ESCADA_SAIDA[1] - 0.4, ESCADA_SAIDA[1] + 1.4);
    }

    // ------------------------------------------------------------------ o esgoto

    /** Areas escavadas do esgoto: x1, z1, x2, z2 e quantos blocos de ar acima do piso. */
    static final int[][] ESGOTO = {
            {3, 18, 9, 22, 4},       // 0 entrada (a aranha nao chega: so tem a passagem estreita)
            {6, 23, 6, 29, 2},       // 1 passagem estreita ate o anel
            {-8, 30, 44, 34, 5},     // 2 anel norte
            {-8, 66, 44, 70, 5},     // 3 anel sul
            {-8, 30, -4, 70, 5},     // 4 anel oeste
            {40, 30, 44, 70, 5},     // 5 anel leste
            {16, 34, 20, 66, 5},     // 6 corredor norte-sul
            {-4, 48, 40, 52, 5},     // 7 corredor oeste-leste
            {11, 43, 25, 57, 6},     // 8 ninho da aranha
            {-15, 38, -11, 44, 3},   // 9 sala oeste
            {-10, 41, -9, 41, 2},    // 10   porta estreita
            {48, 54, 54, 60, 3},     // 11 sala leste
            {45, 57, 47, 57, 2},     // 12   porta estreita
            {26, 74, 32, 78, 3},     // 13 sala sul
            {29, 71, 29, 73, 2},     // 14   porta estreita
            {0, 56, 6, 62, 3},       // 15 salinha
            {3, 53, 3, 55, 2},       // 16   porta estreita
            {28, 38, 34, 42, 3},     // 17 sala norte
            {31, 35, 31, 37, 2},     // 18   porta estreita
            {-8, 71, -4, 80, 4},     // 19 beco sem saida
            {45, 48, 74, 52, 5},     // 20 tunel da saida, indo para o leste
            {44, 83, 50, 89, 5},     // 21 sala da escada (depois da grade)
            {70, 53, 74, 88, 5},     // 22 tunel da saida, descendo para o sul
            {57, 84, 69, 88, 5},     // 23 tunel da saida, voltando para o oeste
            {52, 85, 56, 87, 4},     // 24 trecho estreito antes da grade
            {77, 64, 81, 70, 3},     // 25 sala do tunel leste (esconderijo)
            {75, 67, 76, 67, 2},     // 26   porta estreita
    };

    static boolean noEsgoto(Location l) {
        // a escada da saida sobe ate a rua
        if (caixa(l, ESCADA_SAIDA[0] - 0.4, ESCADA_SAIDA[0] + 1.4, PISO_ESGOTO - 2, 66, ESCADA_SAIDA[1] - 0.4, ESCADA_SAIDA[1] + 1.4)) return true;
        if (l.getY() >= PISO_PORAO + 1 || l.getY() < PISO_ESGOTO - 2) return false;
        if (caixa(l, 6, 7, 49, 58, 18, 19)) return true;
        for (int[] a : ESGOTO)
            if (caixa(l, a[0], a[2] + 1, PISO_ESGOTO - 2, PISO_PORAO, a[1], a[3] + 1)) return true;
        return false;
    }

    /** Area larga do esgoto, onde a aranha pode andar (sem as salas e passagens estreitas e sem a saida). */
    static boolean areaDaAranha(Location l) {
        for (int i : new int[] {2, 3, 4, 5, 6, 7, 8, 19, 20, 22, 23, 24}) {
            int[] a = ESGOTO[i];
            if (caixa(l, a[0], a[2] + 1, PISO_ESGOTO - 1, PISO_PORAO + 1, a[1], a[3] + 1)) return true;
        }
        return false;
    }

    /** Ja passou da entrada do esgoto (a partir dai a aranha acorda). */
    static boolean alemDaEntrada(Location l) {
        return noEsgoto(l) && l.getZ() >= 30;
    }

    /** Dentro da casa, porao, garagem ou esgoto. */
    static boolean dentro(Location l) {
        if (noEsgoto(l)) return true;
        boolean casa = caixa(l, 0, PAREDE_LESTE + 1, PISO_PORAO - 1, TETO + 1, 0, PAREDE_SUL + 1);
        boolean garagem = caixa(l, PAREDE_LESTE, GX2 + 2, PISO_TERREO, PISO_ANDAR + 1, GZ1 - 1, GZ2 + 2);
        return casa || garagem;
    }

    static boolean naVaranda(Location l) {
        return caixa(l, 27, 39, 62, 70, PAREDE_SUL + 0.6, 44);
    }

    static Zona zonaDe(Location l) {
        if (l.getY() < PISO_PORAO) return Zona.ESGOTO;
        if (l.getY() < PISO_TERREO) return Zona.PORAO;
        if (l.getX() >= PAREDE_LESTE + 0.5 && l.getZ() >= GZ1 - 1 && l.getZ() <= GZ2 + 1) return Zona.GARAGEM;
        return Zona.CASA;
    }

    /** Pisos perto das portas e escadas, para nao por armadilha no meio do caminho. */
    static List<int[]> vaos() {
        List<int[]> l = new ArrayList<>();
        for (int y : new int[] {PISO_TERREO, PISO_ANDAR}) {
            for (int c = 0; c < COLS - 1; c++) {
                int wx = x0(c) + 12;
                for (int r = 0; r < LINS; r++)
                    for (int dz = 4; dz <= 5; dz++) {
                        l.add(new int[] {wx - 1, y, z0(r) + dz});
                        l.add(new int[] {wx + 1, y, z0(r) + dz});
                    }
            }
            for (int r = 0; r < LINS - 1; r++) {
                int wz = z0(r) + 11;
                for (int c = 0; c < COLS; c++)
                    for (int dx = 5; dx <= 6; dx++) {
                        l.add(new int[] {x0(c) + dx, y, wz - 1});
                        l.add(new int[] {x0(c) + dx, y, wz + 1});
                    }
            }
        }
        for (int x = 31; x <= 34; x++) l.add(new int[] {x, PISO_TERREO, 35});
        for (int z = 2; z <= 4; z++) {
            l.add(new int[] {24, PISO_TERREO, z});
            l.add(new int[] {17, PISO_ANDAR, z});
        }
        for (int z = 8; z <= 9; z++) l.add(new int[] {14, PISO_TERREO, z});
        for (int z = 17; z <= 18; z++) l.add(new int[] {51, PISO_TERREO, z});
        return l;
    }

    boolean construido(World w) {
        return w.getBlockAt(0, 40, 0).getType() == Material.LODESTONE && w.getBlockAt(2, 40, 0).getType() == Material.CHISELED_TUFF;
    }

    /** Pisos de madeira onde da para por tabua que range ou armadilha (dentro da casa, com espaco livre em cima). */
    List<int[]> pisosLivres(World w) {
        List<int[]> l = new ArrayList<>();
        for (int y : new int[] {PISO_TERREO, PISO_ANDAR}) {
            Material piso = y == PISO_TERREO ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
            for (int x = IX1; x <= IX2; x++)
                for (int z = IZ1; z <= IZ2; z++) {
                    if (w.getBlockAt(x, y, z).getType() != piso) continue;
                    if (!w.getBlockAt(x, y + 1, z).getType().isAir() || !w.getBlockAt(x, y + 2, z).getType().isAir()) continue;
                    l.add(new int[] {x, y, z});
                }
        }
        return l;
    }

    // ================================================================== construcao

    Deque<Runnable> etapas(World w, boolean limpar) {
        Deque<Runnable> f = new ArrayDeque<>();
        Obra o = new Obra(w);
        f.add(() -> {
            o.b(0, 40, 0, Material.AIR);
            o.b(2, 40, 0, Material.AIR);
        });
        if (limpar) {
            for (int y = 44; y <= 98; y++) {
                final int yy = y;
                f.add(() -> o.fill(-24, yy, -20, 90, yy, 100, Material.AIR));
            }
        }
        f.add(() -> terreno(o));
        for (int y = 46; y <= 62; y++) {
            final int yy = y;
            f.add(() -> subsolo(o, yy));
        }
        f.add(() -> paredesEPisos(o));
        f.add(() -> paredesInternas(o));
        f.add(() -> escadas(o));
        f.add(() -> porao(o));
        f.add(() -> esgoto(o));
        f.add(() -> telhado(o));
        f.add(() -> garagem(o));
        for (Comodo k : comodos) f.add(() -> mobiliar(o, k));
        f.add(() -> moveisEsgoto(o));
        f.add(() -> janelas(o));
        f.add(() -> exterior(o));
        f.add(() -> luzes(o));
        f.add(() -> {
            o.b(0, 40, 0, Material.LODESTONE);
            o.b(2, 40, 0, Material.CHISELED_TUFF);
        });
        return f;
    }

    static final class Obra {
        final World w;

        Obra(World w) {
            this.w = w;
        }

        void b(int x, int y, int z, Material m) {
            w.getBlockAt(x, y, z).setType(m, false);
        }

        void d(int x, int y, int z, String dados) {
            w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(dados), false);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, m);
        }

        void fillD(int x1, int y1, int z1, int x2, int y2, int z2, String dados) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) d(x, y, z, dados);
        }

        void portaFerro(int x, int y, int z, String facing, String dobradica) {
            d(x, y, z, "minecraft:iron_door[facing=" + facing + ",half=lower,hinge=" + dobradica + ",open=false,powered=false]");
            d(x, y + 1, z, "minecraft:iron_door[facing=" + facing + ",half=upper,hinge=" + dobradica + ",open=false,powered=false]");
        }

        void cama(int x, int y, int z, String facing, String cor) {
            int dx = facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0;
            int dz = facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0;
            d(x, y, z, "minecraft:" + cor + "_bed[facing=" + facing + ",part=foot,occupied=false]");
            d(x + dx, y, z + dz, "minecraft:" + cor + "_bed[facing=" + facing + ",part=head,occupied=false]");
        }

        /** Armario aberto para "frente". Laterais (onde houver ar) e topo ate o teto, de madeira escura. */
        void armario(int x, int y, int z, String frente, int teto) {
            boolean frenteEmZ = frente.equals("north") || frente.equals("south");
            int[][] lados = frenteEmZ ? new int[][] {{-1, 0}, {1, 0}} : new int[][] {{0, -1}, {0, 1}};
            for (int[] l : lados) {
                if (w.getBlockAt(x + l[0], y, z + l[1]).getType().isAir()) {
                    for (int yy = y; yy < teto; yy++) b(x + l[0], yy, z + l[1], Material.DARK_OAK_PLANKS);
                }
            }
            for (int yy = y + 2; yy < teto; yy++) b(x, yy, z, Material.DARK_OAK_PLANKS);
        }

        void barril(int x, int y, int z) {
            d(x, y, z, "minecraft:barrel[facing=up,open=false]");
        }

        void mesa(int x, int y, int z) {
            d(x, y, z, "minecraft:stripped_dark_oak_wood[axis=y]");
        }

        void lanternaPendurada(int x, int y, int z, boolean alma) {
            d(x, y, z, "minecraft:" + (alma ? "soul_lantern" : "lantern") + "[hanging=true,waterlogged=false]");
        }

        void janela(int x, int y, int z, boolean paredeEmX) {
            d(x, y, z, paredeEmX ? "minecraft:glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]"
                    : "minecraft:glass_pane[north=true,south=true,east=false,west=false,waterlogged=false]");
        }

        void cerca(int x1, int z1, int x2, int z2, int y, String tipo) {
            if (z1 == z2) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    d(x, y, z1, "minecraft:" + tipo + "[east=" + (x < Math.max(x1, x2)) + ",west=" + (x > Math.min(x1, x2)) + ",north=false,south=false,waterlogged=false]");
            } else {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    d(x1, y, z, "minecraft:" + tipo + "[north=" + (z > Math.min(z1, z2)) + ",south=" + (z < Math.max(z1, z2)) + ",east=false,west=false,waterlogged=false]");
            }
        }

        void poste(int x, int z) {
            for (int y = 64; y <= 66; y++) d(x, y, z, "minecraft:dark_oak_fence[east=false,west=false,north=false,south=false,waterlogged=false]");
            d(x, 67, z, "minecraft:lantern[hanging=false,waterlogged=false]");
        }
    }

    // ------------------------------------------------------------------ terreno, subsolo, paredes

    private void terreno(Obra o) {
        o.fill(-22, 60, -18, 86, 62, 96, Material.DIRT);
        o.fill(-22, 63, -18, 86, 63, 96, Material.GRASS_BLOCK);
        int[][] manchas = {{-10, -8}, {64, -6}, {-12, 16}, {72, 6}, {8, 42}, {46, 40}, {26, -10}, {-14, 32}, {70, 36}, {20, 44}, {0, 70}, {40, 74}};
        for (int[] m : manchas)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.abs(dx) + Math.abs(dz) <= 3) o.b(m[0] + dx, 63, m[1] + dz, (dx + dz) % 2 == 0 ? Material.COARSE_DIRT : Material.PODZOL);
    }

    /** Uma camada do subsolo: rocha macica embaixo da casa e do quintal, onde porao e esgoto sao escavados. */
    private void subsolo(Obra o, int y) {
        if (y >= 60) return;
        Material m = y >= 57 ? Material.COBBLED_DEEPSLATE : Material.STONE;
        o.fill(-18, y, -2, 84, y, 92, m);
        if (y >= 57) {
            for (int x = 0; x <= PAREDE_LESTE; x++) {
                o.b(x, y, 0, Material.DEEPSLATE_BRICKS);
                o.b(x, y, PAREDE_SUL, Material.DEEPSLATE_BRICKS);
            }
            for (int z = 0; z <= PAREDE_SUL; z++) {
                o.b(0, y, z, Material.DEEPSLATE_BRICKS);
                o.b(PAREDE_LESTE, y, z, Material.DEEPSLATE_BRICKS);
            }
        }
    }

    private boolean pisoXadrez(int c, int r, int andar) {
        Comodo k = comodo(c, r, andar);
        return k != null && (k.tipo().equals("cozinha") || k.tipo().equals("banheiro") || k.tipo().equals("lavanderia"));
    }

    private void paredesEPisos(Obra o) {
        // embaixo da casa a rocha vai ate o piso do terreo (o porao e escavado dentro dela)
        o.fill(0, 60, 0, PAREDE_LESTE, 62, PAREDE_SUL, Material.COBBLED_DEEPSLATE);
        for (int y = 60; y <= 62; y++) {
            for (int x = 0; x <= PAREDE_LESTE; x++) {
                o.b(x, y, 0, Material.DEEPSLATE_BRICKS);
                o.b(x, y, PAREDE_SUL, Material.DEEPSLATE_BRICKS);
            }
            for (int z = 0; z <= PAREDE_SUL; z++) {
                o.b(0, y, z, Material.DEEPSLATE_BRICKS);
                o.b(PAREDE_LESTE, y, z, Material.DEEPSLATE_BRICKS);
            }
        }
        o.fill(IX1, PISO_TERREO, IZ1, IX2, PISO_TERREO, IZ2, Material.SPRUCE_PLANKS);
        o.fill(IX1, PISO_ANDAR, IZ1, IX2, PISO_ANDAR, IZ2, Material.DARK_OAK_PLANKS);
        o.fill(IX1, TETO, IZ1, IX2, TETO, IZ2, Material.SPRUCE_PLANKS);
        // cozinha e banheiros: ladrilho xadrez
        for (int andar = 1; andar <= 2; andar++)
            for (int c = 0; c < COLS; c++)
                for (int r = 0; r < LINS; r++) {
                    if (!pisoXadrez(c, r, andar)) continue;
                    int y = andar == 1 ? PISO_TERREO : PISO_ANDAR;
                    for (int x = x0(c); x <= x0(c) + 11; x++)
                        for (int z = z0(r); z <= z0(r) + 10; z++)
                            o.b(x, y, z, (x + z) % 2 == 0 ? Material.WHITE_TERRACOTTA : Material.LIGHT_GRAY_TERRACOTTA);
                }
        for (int y = 63; y <= TETO; y++) {
            Material m = y == 63 ? Material.MOSSY_COBBLESTONE : (y == PISO_ANDAR || y == TETO) ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
            for (int x = 0; x <= PAREDE_LESTE; x++) {
                o.b(x, y, 0, m);
                o.b(x, y, PAREDE_SUL, m);
            }
            for (int z = 0; z <= PAREDE_SUL; z++) {
                o.b(0, y, z, m);
                o.b(PAREDE_LESTE, y, z, m);
            }
            if (y > 63) {
                for (int c = 0; c <= COLS; c++) {
                    int x = c == COLS ? PAREDE_LESTE : x0(c) - 1;
                    o.d(x, y, 0, "minecraft:dark_oak_log[axis=y]");
                    o.d(x, y, PAREDE_SUL, "minecraft:dark_oak_log[axis=y]");
                }
                for (int r = 1; r < LINS; r++) {
                    o.d(0, y, z0(r) - 1, "minecraft:dark_oak_log[axis=y]");
                    o.d(PAREDE_LESTE, y, z0(r) - 1, "minecraft:dark_oak_log[axis=y]");
                }
            }
        }
        o.fill(IX1, 64, IZ1, IX2, 68, IZ2, Material.AIR);
        o.fill(IX1, 70, IZ1, IX2, 74, IZ2, Material.AIR);
        o.portaFerro(PORTA_FRENTE[0], PORTA_FRENTE[1], PORTA_FRENTE[2], "north", "left");
        o.portaFerro(PORTA_FRENTE2[0], PORTA_FRENTE2[1], PORTA_FRENTE2[2], "north", "right");
        o.fill(32, 66, PAREDE_SUL, 33, 66, PAREDE_SUL, Material.STRIPPED_DARK_OAK_WOOD);
    }

    private void paredesInternas(Obra o) {
        for (int[] faixa : new int[][] {{64, 68}, {70, 74}}) {
            for (int y = faixa[0]; y <= faixa[1]; y++) {
                for (int c = 0; c < COLS - 1; c++) {
                    int wx = x0(c) + 12;
                    for (int z = IZ1; z <= IZ2; z++) o.b(wx, y, z, Material.PALE_OAK_PLANKS);
                }
                for (int r = 0; r < LINS - 1; r++) {
                    int wz = z0(r) + 11;
                    for (int x = IX1; x <= IX2; x++) o.b(x, y, wz, Material.PALE_OAK_PLANKS);
                }
            }
        }
        // vaos de 2 de largura e 3 de altura entre todos os comodos vizinhos, com batente em cima
        for (int y : new int[] {64, 70}) {
            for (int c = 0; c < COLS - 1; c++) {
                int wx = x0(c) + 12;
                for (int r = 0; r < LINS; r++)
                    for (int dz = 4; dz <= 5; dz++) {
                        o.fill(wx, y, z0(r) + dz, wx, y + 2, z0(r) + dz, Material.AIR);
                        o.b(wx, y + 3, z0(r) + dz, Material.STRIPPED_DARK_OAK_WOOD);
                    }
            }
            for (int r = 0; r < LINS - 1; r++) {
                int wz = z0(r) + 11;
                for (int c = 0; c < COLS; c++)
                    for (int dx = 5; dx <= 6; dx++) {
                        o.fill(x0(c) + dx, y, wz, x0(c) + dx, y + 2, wz, Material.AIR);
                        o.b(x0(c) + dx, y + 3, wz, Material.STRIPPED_DARK_OAK_WOOD);
                    }
            }
        }
        // porta dupla da garagem (quando abertas, as folhas ficam encostadas nos batentes)
        o.portaFerro(PORTA_GARAGEM[0], PORTA_GARAGEM[1], PORTA_GARAGEM[2], "east", "right");
        o.portaFerro(PORTA_GARAGEM2[0], PORTA_GARAGEM2[1], PORTA_GARAGEM2[2], "east", "left");
        o.fill(PAREDE_LESTE, 66, 17, PAREDE_LESTE, 66, 18, Material.STRIPPED_DARK_OAK_WOOD);
    }

    private void escadas(Obra o) {
        // escada larga (3) subindo para o oeste em z=2..4: degraus de x=23 (y64) ate x=18 (y69, no nivel do piso de cima)
        for (int z = 2; z <= 4; z++) {
            for (int k = 0; k <= 5; k++) {
                int x = 23 - k, y = 64 + k;
                for (int yy = 64; yy < y; yy++) o.b(x, yy, z, Material.SPRUCE_PLANKS);
                o.d(x, y, z, "minecraft:dark_oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]");
            }
            for (int x = 19; x <= 22; x++) o.b(x, PISO_ANDAR, z, Material.AIR);
        }
        for (int x = 19; x <= 23; x++) {
            o.d(x, 70, 1, "minecraft:dark_oak_fence[east=" + (x < 23) + ",west=" + (x > 19) + ",north=false,south=false,waterlogged=false]");
            o.d(x, 70, 5, "minecraft:dark_oak_fence[east=" + (x < 23) + ",west=" + (x > 19) + ",north=false,south=false,waterlogged=false]");
        }
        for (int z = 2; z <= 4; z++) o.d(23, 70, z, "minecraft:dark_oak_fence[north=true,south=true,east=false,west=false,waterlogged=false]");
        // poco da escada do porao (2 de largura): paredes em z=7 e z=10 (x=15..22), porta dupla em x=15
        for (int y = 64; y <= 68; y++)
            for (int x = 15; x <= 22; x++) {
                o.b(x, y, 7, Material.PALE_OAK_PLANKS);
                o.b(x, y, 10, Material.PALE_OAK_PLANKS);
            }
        o.fill(22, 64, 8, 22, 68, 9, Material.PALE_OAK_PLANKS);
        o.fill(16, 64, 8, 21, 68, 9, Material.AIR);
        o.portaFerro(PORTA_PORAO[0], PORTA_PORAO[1], PORTA_PORAO[2], "east", "left");
        o.portaFerro(PORTA_PORAO2[0], PORTA_PORAO2[1], PORTA_PORAO2[2], "east", "right");
        o.fill(15, 66, 8, 15, 66, 9, Material.STRIPPED_DARK_OAK_WOOD);
        o.fill(15, 67, 8, 15, 68, 9, Material.PALE_OAK_PLANKS);
    }

    private void porao(Obra o) {
        for (Comodo k : comodos) {
            if (k.andar() != 0) continue;
            o.fill(k.x0(), 58, k.z0(), k.x0() + 11, 62, k.z0() + 10, Material.AIR);
            o.fill(k.x0(), PISO_PORAO, k.z0(), k.x0() + 11, PISO_PORAO, k.z0() + 10, Material.POLISHED_DEEPSLATE);
        }
        // vaos entre salas vizinhas do porao
        for (Comodo a : comodos) {
            if (a.andar() != 0) continue;
            if (comodo(a.c() + 1, a.r(), 0) != null) {
                int wx = a.x0() + 12;
                for (int dz = 4; dz <= 5; dz++) {
                    o.fill(wx, 58, a.z0() + dz, wx, 60, a.z0() + dz, Material.AIR);
                    o.b(wx, PISO_PORAO, a.z0() + dz, Material.POLISHED_DEEPSLATE);
                }
            }
            if (comodo(a.c(), a.r() + 1, 0) != null) {
                int wz = a.z0() + 11;
                for (int dx = 5; dx <= 6; dx++) {
                    o.fill(a.x0() + dx, 58, wz, a.x0() + dx, 60, wz, Material.AIR);
                    o.b(a.x0() + dx, PISO_PORAO, wz, Material.POLISHED_DEEPSLATE);
                }
            }
        }
        // escada do porao: um corredor fechado (z=8..9) dentro da sala de cima do porao, descendo para o leste
        for (int y = 58; y <= 62; y++)
            for (int x = 15; x <= 21; x++) {
                o.b(x, y, 7, Material.PALE_OAK_PLANKS);
                o.b(x, y, 10, Material.PALE_OAK_PLANKS);
            }
        o.fill(14, 58, 8, 15, 62, 9, Material.PALE_OAK_PLANKS);
        for (int z = 8; z <= 9; z++)
            for (int k = 0; k <= 5; k++) {
                int x = 16 + k, y = 63 - k;
                for (int yy = PISO_PORAO + 1; yy < y; yy++) o.b(x, yy, z, Material.SPRUCE_PLANKS);
                o.d(x, y, z, "minecraft:spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]");
                for (int yy = y + 1; yy <= 63; yy++) o.b(x, yy, z, Material.AIR);
            }
        tampaFechada(o);
    }

    void tampaFechada(Obra o) {
        o.d(TAMPA[0], TAMPA[1], TAMPA[2], "minecraft:iron_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]");
    }

    void tampaAberta(Obra o) {
        o.d(TAMPA[0], TAMPA[1], TAMPA[2], "minecraft:iron_trapdoor[facing=south,half=top,open=true,powered=false,waterlogged=false]");
    }

    private void esgoto(Obra o) {
        for (int i = 0; i < ESGOTO.length; i++) {
            int[] a = ESGOTO[i];
            o.fill(a[0], PISO_ESGOTO + 1, a[1], a[2], PISO_ESGOTO + a[4], a[3], Material.AIR);
            Material piso = i == 8 ? Material.MUD_BRICKS : (i >= 9 && i <= 19) ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
            o.fill(a[0], PISO_ESGOTO, a[1], a[2], PISO_ESGOTO, a[3], piso);
        }
        // canais de agua no meio dos tuneis largos (um bloco de fundo, lama embaixo)
        canal(o, -6, 32, 42, 32);
        canal(o, -6, 68, 42, 68);
        canal(o, -6, 32, -6, 68);
        canal(o, 42, 32, 42, 68);
        canal(o, 18, 35, 18, 42);
        canal(o, 18, 58, 18, 65);
        canal(o, -3, 50, 10, 50);
        canal(o, 26, 50, 39, 50);
        // musgo nas paredes (enfeite)
        int[][] musgo = {{-9, 36}, {-9, 60}, {45, 40}, {45, 64}, {10, 29}, {30, 29}, {12, 71}, {36, 71}, {15, 40}, {21, 60}, {2, 20}, {10, 20}};
        for (int[] m : musgo) {
            o.b(m[0], 53, m[1], Material.MOSSY_STONE_BRICKS);
            o.b(m[0], 52, m[1], Material.MOSSY_COBBLESTONE);
        }
        // grade no fim do tunel da saida (a Serra corta) e a escada ate o bueiro da rua
        gradeFechada(o);
        o.fill(44, PISO_ESGOTO, 83, 50, PISO_ESGOTO, 89, Material.SMOOTH_STONE);
        int ex = ESCADA_SAIDA[0], ez = ESCADA_SAIDA[1];
        o.fill(ex - 1, 52, ez, ex - 1, 62, ez, Material.STONE_BRICKS);
        for (int y = 52; y <= 62; y++) o.d(ex, y, ez, "minecraft:ladder[facing=east,waterlogged=false]");
        tampaRuaFechada(o);
        // escada de mao do bueiro da caldeira ate a entrada do esgoto
        for (int y = PISO_ESGOTO + 1; y <= PISO_PORAO - 1; y++) o.d(TAMPA[0], y, TAMPA[2], "minecraft:ladder[facing=south,waterlogged=false]");
    }

    private void canal(Obra o, int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                o.b(x, PISO_ESGOTO, z, Material.MUD);
                o.b(x, PISO_ESGOTO - 1, z, Material.PACKED_MUD);
            }
    }

    void tampaRuaFechada(Obra o) {
        o.d(ESCADA_SAIDA[0], 63, ESCADA_SAIDA[1], "minecraft:iron_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]");
    }

    void tampaRuaAberta(Obra o) {
        o.d(ESCADA_SAIDA[0], 63, ESCADA_SAIDA[1], "minecraft:iron_trapdoor[facing=east,half=top,open=true,powered=false,waterlogged=false]");
    }

    void gradeFechada(Obra o) {
        for (int[] g : grade) o.d(g[0], g[1], g[2], "minecraft:iron_bars[north=true,south=true,east=false,west=false,waterlogged=false]");
    }

    void gradeAberta(Obra o) {
        for (int[] g : grade) o.b(g[0], g[1], g[2], Material.AIR);
    }

    private void telhado(Obra o) {
        int meio = PAREDE_SUL / 2;
        for (int k = 0; k <= meio; k++) {
            int y = TETO + 1 + k;
            for (int x = -1; x <= PAREDE_LESTE + 1; x++) {
                o.d(x, y, -1 + k, "minecraft:deepslate_tile_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
                o.d(x, y, PAREDE_SUL + 1 - k, "minecraft:deepslate_tile_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
            }
            for (int z = k; z <= PAREDE_SUL - k; z++) {
                o.b(0, y, z, Material.DARK_OAK_PLANKS);
                o.b(PAREDE_LESTE, y, z, Material.DARK_OAK_PLANKS);
            }
        }
        for (int x = -1; x <= PAREDE_LESTE + 1; x++) o.d(x, TETO + 1 + meio, meio, "minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]");
        o.janela(0, TETO + 6, meio, false);
        o.janela(PAREDE_LESTE, TETO + 6, meio, false);
    }

    private void garagem(Obra o) {
        o.fill(GX1, PISO_TERREO, GZ1, GX2, PISO_TERREO, GZ2, Material.SMOOTH_STONE);
        for (int y = 63; y <= 68; y++) {
            Material m = y == 63 ? Material.MOSSY_COBBLESTONE : Material.STONE_BRICKS;
            for (int x = GX1; x <= GX2 + 1; x++) {
                o.b(x, y, GZ1 - 1, m);
                o.b(x, y, GZ2 + 1, m);
            }
            for (int z = GZ1 - 1; z <= GZ2 + 1; z++) o.b(GX2 + 1, y, z, m);
        }
        o.fill(GX1, 69, GZ1 - 1, GX2 + 1, 69, GZ2 + 1, Material.DEEPSLATE_TILES);
        o.fill(GX1, 64, GZ1, GX2, 68, GZ2, Material.AIR);
        for (int y : new int[] {65, 66}) {
            o.janela(GX2 + 1, y, 16, false);
            o.janela(GX2 + 1, y, 17, false);
            o.janela(GX2 + 1, y, 23, false);
            o.janela(GX2 + 1, y, 24, false);
        }
        portaoFechado(o);
        carro(o, false);
        o.armario(65, 64, 27, "north", 69);
        o.barril(65, 64, 13);
        o.barril(65, 64, 14);
        o.barril(65, 64, 24);
        o.mesa(53, 64, 27);
        o.d(54, 64, 27, "minecraft:anvil[facing=east]");
        o.d(53, 64, 13, "minecraft:grindstone[face=floor,facing=north]");
    }

    void portaoFechado(Obra o) {
        for (int[] b : portao) o.b(b[0], b[1], b[2], b[1] == 65 || b[1] == 67 ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE);
    }

    void portaoAberto(Obra o) {
        for (int[] b : portao) o.b(b[0], b[1], b[2], Material.AIR);
    }

    /** Carro 3x7, com 2 blocos de altura em volta (a vovo nao consegue subir em cima). */
    void carro(Obra o, boolean ligado) {
        o.fill(58, 64, 16, 60, 65, 22, Material.RED_CONCRETE);
        for (int[] r : new int[][] {{58, 17}, {60, 17}, {58, 21}, {60, 21}}) o.b(r[0], 64, r[1], Material.BLACK_CONCRETE);
        o.fill(58, 65, 17, 60, 65, 20, Material.BLACK_STAINED_GLASS);
        o.fill(58, 66, 17, 60, 66, 20, Material.RED_CONCRETE);
        for (int[] f : farois) o.b(f[0], f[1], f[2], ligado ? Material.OCHRE_FROGLIGHT : Material.WHITE_CONCRETE);
        o.b(58, 64, 16, Material.REDSTONE_BLOCK);
        o.b(60, 64, 16, Material.REDSTONE_BLOCK);
    }

    private void moveisEsgoto(Obra o) {
        o.barril(9, 52, 22);
        o.barril(-15, 52, 38);
        o.barril(54, 52, 60);
        o.barril(32, 52, 78);
        o.barril(0, 52, 62);
        o.barril(34, 52, 38);
        o.barril(-8, 52, 80);
        o.barril(25, 52, 57);
        // ninho da aranha: teias nos cantos e ossos
        int[][] teias = {{11, 43}, {25, 43}, {11, 57}, {12, 44}, {24, 56}, {13, 43}, {11, 45}};
        for (int[] t : teias) {
            o.b(t[0], 57, t[1], Material.COBWEB);
            o.b(t[0], 56, t[1], Material.COBWEB);
        }
        o.b(11, 52, 43, Material.COBWEB);
        o.b(25, 52, 43, Material.COBWEB);
        o.b(11, 52, 57, Material.COBWEB);
        o.d(13, 52, 55, "minecraft:bone_block[axis=x]");
        o.d(23, 52, 45, "minecraft:bone_block[axis=z]");
        o.b(-8, 54, 79, Material.COBWEB);
        o.b(-15, 54, 44, Material.COBWEB);
        o.b(54, 54, 54, Material.COBWEB);
        // lanternas fracas pelos tuneis
        int[][] lant = {{4, 21}, {-6, 40}, {-6, 60}, {42, 40}, {42, 60}, {6, 32}, {30, 32}, {6, 68}, {30, 68}, {18, 43}, {-13, 41}, {51, 57}, {29, 76}, {3, 59}, {31, 40},
                {60, 50}, {72, 62}, {72, 80}, {63, 86}, {79, 67}};
        for (int[] l : lant) {
            int topo = PISO_ESGOTO + 1;
            for (int[] a : ESGOTO)
                if (l[0] >= a[0] && l[0] <= a[2] && l[1] >= a[1] && l[1] <= a[3]) topo = Math.max(topo, PISO_ESGOTO + a[4]);
            o.lanternaPendurada(l[0], topo, l[1], true);
        }
        o.lanternaPendurada(47, 56, 86, false);
        o.barril(81, 52, 70);
        o.b(77, 54, 64, Material.COBWEB);
        o.b(74, 55, 88, Material.COBWEB);
        o.b(57, 55, 84, Material.COBWEB);
        int[][] ossos = {{73, 60}, {61, 88}};
        for (int[] b : ossos) o.d(b[0], 52, b[1], "minecraft:bone_block[axis=x]");
    }

    private void janelas(Obra o) {
        for (int y : new int[] {65, 66, 67, 71, 72, 73}) {
            for (int c = 0; c < COLS; c++) {
                for (int dx : new int[] {2, 3, 8, 9}) {
                    int x = x0(c) + dx;
                    o.janela(x, y, 0, true);
                    if (!(c == 2 && y < 69)) o.janela(x, y, PAREDE_SUL, true);
                }
            }
            for (int r = 0; r < LINS; r++) {
                for (int dz : new int[] {1, 2, 8, 9}) {
                    int z = z0(r) + dz;
                    o.janela(0, y, z, false);
                    boolean garagem = y < 69 && z >= GZ1 - 1 && z <= GZ2 + 1;
                    if (!garagem) o.janela(PAREDE_LESTE, y, z, false);
                }
            }
        }
    }

    private void exterior(Obra o) {
        // varanda
        o.fill(28, 63, 37, 37, 63, 40, Material.SPRUCE_PLANKS);
        for (int y = 64; y <= 68; y++) {
            o.d(28, y, 40, "minecraft:dark_oak_fence[east=false,west=false,north=false,south=false,waterlogged=false]");
            o.d(37, y, 40, "minecraft:dark_oak_fence[east=false,west=false,north=false,south=false,waterlogged=false]");
        }
        o.fillD(28, 69, 37, 37, 69, 40, "minecraft:dark_oak_slab[type=bottom,waterlogged=false]");
        o.lanternaPendurada(33, 68, 38, false);
        // caminhos e rua
        o.fill(32, 63, 41, 34, 63, 52, Material.GRAVEL);
        o.fill(57, 63, 29, 61, 63, 52, Material.GRAVEL);
        o.fill(-16, 63, 50, 74, 63, 52, Material.GRAVEL);
        o.fill(40, 63, 83, 49, 63, 92, Material.GRAVEL);
        tampaRuaFechada(o);
        o.poste(48, 90);
        // cerca do quintal (fechada)
        for (int y = 64; y <= 65; y++) {
            o.cerca(-18, -14, 74, -14, y, "dark_oak_fence");
            o.cerca(-18, 48, 74, 48, y, "dark_oak_fence");
            o.cerca(-18, -14, -18, 48, y, "dark_oak_fence");
            o.cerca(74, -14, 74, 48, y, "dark_oak_fence");
        }
        // arvores secas
        int[][] arvores = {{-10, -6}, {62, -8}, {-12, 20}, {70, -2}, {-12, 40}, {10, 42}, {48, 42}, {70, 38}, {24, -9}, {44, -9}, {70, 14}};
        for (int[] a : arvores) {
            for (int y = 64; y <= 70; y++) o.d(a[0], y, a[1], "minecraft:dark_oak_log[axis=y]");
            o.d(a[0] + 1, 69, a[1], "minecraft:dark_oak_log[axis=x]");
            o.d(a[0] + 2, 70, a[1], "minecraft:dark_oak_log[axis=x]");
            o.d(a[0], 68, a[1] - 1, "minecraft:dark_oak_log[axis=z]");
            o.d(a[0] - 1, 71, a[1], "minecraft:dark_oak_log[axis=x]");
        }
        int[][] arbustos = {{-4, -3}, {4, 40}, {40, 42}, {56, -4}, {68, 26}, {-14, 8}, {12, -8}, {36, -10}, {-6, 44}, {72, -10}, {22, 40}};
        for (int[] a : arbustos) o.b(a[0], 64, a[1], Material.DEAD_BUSH);
        // rua do lobby
        o.poste(16, 54);
        o.poste(50, 54);
        o.poste(38, 58);
    }

    private void luzes(Obra o) {
        for (Comodo k : comodos) {
            int x = k.x0() + 6, z = k.z0() + 5, y = k.topo();
            if (o.w.getBlockAt(x, y, z).getType().isAir()) o.lanternaPendurada(x, y, z, true);
        }
        o.lanternaPendurada(55, 68, 20, true);
        apagarLampadas(o);
    }

    /**
     * Claridade extra: blocos de luz invisiveis perto do teto, numa grade de 4 em 4 blocos (0 = sem luz extra).
     * So ocupa lugares vazios, entao nao atrapalha moveis, escadas nem a passagem.
     */
    void luzesInvisiveis(Obra o, int nivel, int nivelEsgoto) {
        for (Comodo k : comodos)
            for (int dx : new int[] {2, 6, 10})
                for (int dz : new int[] {2, 6, 10}) luzInvisivel(o, k.x0() + dx, k.topo() - 1, k.z0() + dz, nivel);
        for (int x = GX1 + 2; x <= GX2; x += 4)
            for (int z = GZ1 + 2; z <= GZ2; z += 4) luzInvisivel(o, x, 67, z, nivel);
        for (int x = -15; x <= 81; x += 4)
            for (int z = 18; z <= 92; z += 4) luzInvisivel(o, x, 55, z, nivelEsgoto);
    }

    private void luzInvisivel(Obra o, int x, int y, int z, int nivel) {
        Material m = o.w.getBlockAt(x, y, z).getType();
        if (m != Material.AIR && m != Material.LIGHT) return;
        if (nivel <= 0) {
            if (m == Material.LIGHT) o.b(x, y, z, Material.AIR);
            return;
        }
        o.d(x, y, z, "minecraft:light[level=" + Math.min(15, nivel) + ",waterlogged=false]");
    }

    void apagarLampadas(Obra o) {
        for (int[] l : lampadas) o.d(l[0], l[1], l[2], "minecraft:redstone_lamp[lit=false]");
    }

    void acenderLampadas(Obra o) {
        for (int[] l : lampadas) o.b(l[0], l[1], l[2], Material.OCHRE_FROGLIGHT);
    }
}
