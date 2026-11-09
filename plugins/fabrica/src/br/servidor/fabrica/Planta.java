package br.servidor.fabrica;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Planta da Fabrica Alegria (uma instancia; x relativo a 'ox', z absoluto, piso em Y). O caminho anda para +z e depois
 * para -x: Portaria -> Saguao (Tutu no pedestal) -> Sala da Luva (oeste) -> Linha de Producao -> Vestiario (leste) ->
 * Sala de Controle -> Galeria com a ponte -> Sala dos Brinquedos Gigantes -> dutos -> Sala Final.
 */
final class Planta {

    static final int Y = 64;
    static final int ESPACO = 320;

    // ------------------------------------------------------------------ alvos (o que a luva / a mao alcanca)
    enum Tipo { TECLA, FITA, PEDESTAL_AZUL, PEDESTAL_VERMELHO, BOTAO, PLUGUE, TOMADA, CAIXA, RECEPTOR, PUXADOR }

    record Alvo(String id, Tipo tipo, int x, int y, int z, String cor) {}

    static final List<Alvo> ALVOS = new ArrayList<>();
    static final Map<Long, Alvo> POR_BLOCO = new HashMap<>();

    static long chave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFF) << 30) | (z & 0x3FFFFFFFL);
    }

    private static void alvo(String id, Tipo t, int x, int y, int z, String cor) {
        Alvo a = new Alvo(id, t, x, y, z, cor);
        ALVOS.add(a);
        POR_BLOCO.put(chave(x, y, z), a);
    }

    static Alvo alvo(String id) {
        for (Alvo a : ALVOS) if (a.id.equals(id)) return a;
        return null;
    }

    // teclado da portaria: 4 linhas x 3 colunas na parede z=12, botoes em z=11 (x 3..5)
    static final String[][] TECLAS = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"C", "0", "OK"}};
    static final int TECLADO_X = 3, TECLADO_Z = 11;

    static {
        for (int l = 0; l < 4; l++)
            // quem olha o teclado (da portaria) esta virado para o sul: o leste (+x) fica a ESQUERDA, por isso a coluna
            // da esquerda ("1", "4", "7", "C") e a de x maior
            for (int c = 0; c < 3; c++) alvo("tecla_" + TECLAS[l][c], Tipo.TECLA, TECLADO_X + 2 - c, Y + 4 - l, TECLADO_Z, TECLAS[l][c]);
        alvo("fita1", Tipo.FITA, 11, Y + 1, 7, null);
        alvo("fita2", Tipo.FITA, 21, Y + 1, 73, null);
        alvo("pedestal_azul", Tipo.PEDESTAL_AZUL, -28, Y + 1, 21, null);
        alvo("pedestal_vermelho", Tipo.PEDESTAL_VERMELHO, 28, Y + 1, 72, null);
        alvo("botao_luva", Tipo.BOTAO, -22, Y + 5, 15, null);
        alvo("plugue_saguao", Tipo.PLUGUE, 15, Y + 2, 18, null);
        alvo("tomada_saguao", Tipo.TOMADA, 4, Y + 2, 44, "plugue_saguao");
        alvo("caixa_vermelha", Tipo.CAIXA, -17, Y + 6, 50, "vermelha");
        alvo("caixa_amarela", Tipo.CAIXA, 17, Y + 6, 50, "amarela");
        alvo("caixa_azul", Tipo.CAIXA, 0, Y + 7, 73, "azul");
        alvo("receptor_vermelha", Tipo.RECEPTOR, -4, Y + 1, 60, "vermelha");
        alvo("receptor_amarela", Tipo.RECEPTOR, 0, Y + 1, 60, "amarela");
        alvo("receptor_azul", Tipo.RECEPTOR, 4, Y + 1, 60, "azul");
        alvo("plugue_azul", Tipo.PLUGUE, 14, Y + 2, 77, "azul");
        alvo("plugue_vermelho", Tipo.PLUGUE, 34, Y + 2, 77, "vermelha");
        alvo("tomada_azul", Tipo.TOMADA, 34, Y + 2, 89, "plugue_azul");
        alvo("tomada_vermelha", Tipo.TOMADA, 14, Y + 2, 89, "plugue_vermelho");
        alvo("puxador", Tipo.PUXADOR, -79, Y + 8, 84, null);
    }

    // ------------------------------------------------------------------ pontos
    static final double[] SPAWN = {0.5, Y + 1, 2.5, 0, 0};
    static final double[][] CHECKPOINT = {
            {0.5, Y + 1, 2.5, 0, 0},          // 0 portaria
            {-20.5, Y + 1, 21.5, 90, 0},      // 1 sala da luva
            {0.5, Y + 1, 47.5, 0, 0},         // 2 linha de producao
            {24.5, Y + 1, 78.5, 0, 0},        // 3 sala de controle
            {-7.5, Y + 1, 83.5, 90, 0},       // 4 fim da ponte (antes da perseguicao)
    };
    static final double[] TUTU_PEDESTAL = {0.5, Y + 3, 32.5, 180, 0};
    static final double[] TUTU_ESPIA = {-12.5, Y + 1, 72.5, 160, 0};    // aparece no fundo da linha de producao (canto livre)
    static final double[] TUTU_CACADA = {-16.5, Y + 1, 77.5, 180, 0};
    static final double[] BOLOTA_FIM = {-71.5, Y - 9, 84.5, 90, -60};
    /** Caminho do Tutu na perseguicao (centros de bloco em Y+1): sala -> boca do duto -> dutos -> sala final. */
    static final double[][] CAMINHO = {
            {-16.5, 77.5}, {-16.5, 83.5}, {-22.5, 83.5},
            {-34.5, 83.5}, {-34.5, 92.5}, {-46.5, 92.5}, {-46.5, 76.5}, {-56.5, 76.5}, {-56.5, 84.5}, {-64.5, 84.5},
            {-69.5, 84.5}};
    static final int DUTO_INICIO = 2, DUTO_FIM = 9;   // indices do caminho que sao duto (o Tutu encolhe)

    static final int PONTE_Z1 = 82, PONTE_Z2 = 84, PONTE_X1 = -9, PONTE_X2 = 12;

    private final World w;
    final int ox;

    Planta(World w, int ox) {
        this.w = w;
        this.ox = ox;
    }

    World mundo() {
        return w;
    }

    Location loc(double x, double y, double z, float yaw, float pitch) {
        return new Location(w, x + ox, y, z, yaw, pitch);
    }

    Location loc(double[] p) {
        return new Location(w, p[0] + ox, p[1], p[2], p.length > 3 ? (float) p[3] : 0f, p.length > 4 ? (float) p[4] : 0f);
    }

    Location centro(Alvo a) {
        return new Location(w, a.x + ox + 0.5, a.y + 0.5, a.z + 0.5);
    }

    Block bloco(int x, int y, int z) {
        return w.getBlockAt(x + ox, y, z);
    }

    /** Alvo do bloco (coordenadas do mundo) ou null. */
    Alvo alvoEm(Block b) {
        if (b == null) return null;
        return POR_BLOCO.get(chave(b.getX() - ox, b.getY(), b.getZ()));
    }

    void por(int x, int y, int z, String d) {
        BlockData bd;
        try {
            bd = Bukkit.createBlockData(d.contains(":") ? d : "minecraft:" + d);
        } catch (IllegalArgumentException e) {
            return;
        }
        bloco(x, y, z).setBlockData(bd, false);
    }

    void caixa(int x1, int y1, int z1, int x2, int y2, int z2, String d) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) por(x, y, z, d);
    }

    /** Sala: paredes/teto/piso cheios e o miolo vazio. */
    private void sala(int x1, int z1, int x2, int z2, int alt, String parede, String piso, String teto) {
        caixa(x1, Y, z1, x2, Y, z2, piso);
        caixa(x1, Y + 1, z1, x2, Y + alt, z1, parede);
        caixa(x1, Y + 1, z2, x2, Y + alt, z2, parede);
        caixa(x1, Y + 1, z1, x1, Y + alt, z2, parede);
        caixa(x2, Y + 1, z1, x2, Y + alt, z2, parede);
        caixa(x1, Y + alt + 1, z1, x2, Y + alt + 1, z2, teto);
        caixa(x1 + 1, Y + 1, z1 + 1, x2 - 1, Y + alt, z2 - 1, "air");
    }

    private void luzes(int x1, int z1, int x2, int z2, int y, int passo, int nivel) {
        for (int x = x1; x <= x2; x += passo) for (int z = z1; z <= z2; z += passo) por(x, y, z, "light[level=" + nivel + "]");
    }

    // ------------------------------------------------------------------ construcao (uma vez por instancia)

    void construir() {
        // ---------- portaria e seguranca
        sala(-6, 0, 6, 12, 5, "white_terracotta", "polished_andesite", "smooth_stone");
        caixa(-5, Y + 1, 1, 5, Y + 1, 1, "yellow_terracotta");                 // rodape
        caixa(-5, Y, 3, 5, Y, 9, "red_terracotta");
        por(-4, Y + 1, 6, "spruce_stairs[facing=east]");
        por(-4, Y + 1, 7, "spruce_stairs[facing=east]");
        por(-5, Y + 1, 4, "potted_dead_bush");
        luzes(-3, 3, 3, 9, Y + 5, 6, 9);
        sala(6, 1, 13, 9, 4, "light_gray_terracotta", "polished_andesite", "smooth_stone");
        caixa(6, Y + 1, 4, 6, Y + 2, 5, "air");                                  // passagem para a seguranca
        caixa(10, Y + 1, 6, 12, Y + 1, 8, "spruce_planks");                     // mesa
        caixa(10, Y + 1, 6, 12, Y + 1, 6, "spruce_slab[type=top]");
        por(11, Y + 1, 7, "jukebox");                                            // fita 1 (aparelho de video)
        por(12, Y + 2, 8, "black_stained_glass_pane");
        por(10, Y + 2, 8, "black_stained_glass_pane");
        por(7, Y + 1, 2, "chest[facing=south]");
        luzes(9, 4, 9, 4, Y + 4, 1, 7);
        // porta da portaria + teclado
        caixa(-1, Y + 1, 12, 1, Y + 3, 12, "iron_block");

        // ---------- saguao
        sala(-16, 12, 16, 45, 13, "orange_terracotta", "white_concrete", "smooth_quartz");
        for (int x = -15; x <= 15; x++) for (int z = 13; z <= 44; z++) por(x, Y, z, ((x + z) & 1) == 0 ? "light_blue_concrete" : "white_concrete");
        caixa(-15, Y + 1, 13, 15, Y + 2, 13, "yellow_terracotta");
        caixa(-15, Y + 1, 44, 15, Y + 2, 44, "yellow_terracotta");
        // o rodape nao pode tampar as passagens (portaria ao norte, portao da fabrica ao sul)
        caixa(-1, Y + 1, 13, 1, Y + 3, 13, "air");
        caixa(-2, Y + 1, 44, 2, Y + 4, 44, "air");
        caixa(-1, Y + 1, 12, 1, Y + 3, 12, "iron_block");                       // (porta da portaria de novo, lado de la)
        // faixas coloridas nas paredes
        for (int z = 14; z <= 43; z += 4) {
            por(-16, Y + 6, z, "red_concrete");
            por(16, Y + 6, z, "blue_concrete");
            por(-16, Y + 7, z + 1, "yellow_concrete");
            por(16, Y + 7, z + 1, "lime_concrete");
        }
        // pedestal do Tutu
        caixa(-3, Y + 1, 29, 3, Y + 1, 35, "smooth_quartz");
        caixa(-2, Y + 2, 30, 2, Y + 2, 34, "quartz_bricks");
        caixa(-3, Y + 1, 29, 3, Y + 1, 29, "quartz_stairs[facing=south]");
        // brinquedos jogados
        por(-10, Y + 1, 20, "red_shulker_box");
        por(-11, Y + 1, 21, "yellow_shulker_box");
        por(9, Y + 1, 38, "blue_shulker_box");
        por(-12, Y + 1, 40, "skeleton_skull[rotation=4]");
        por(11, Y + 1, 24, "creeper_head[rotation=12]");
        // porta da sala da luva (aberta), portao da fabrica (fechado)
        caixa(-16, Y + 1, 19, -16, Y + 3, 21, "air");
        caixa(-2, Y + 1, 45, 2, Y + 4, 45, "yellow_concrete");
        // plugue (carretel na parede leste) e tomada (ao lado do portao)
        caixa(15, Y + 1, 17, 15, Y + 3, 19, "yellow_terracotta");
        por(15, Y + 2, 18, "yellow_glazed_terracotta");
        por(4, Y + 2, 44, "polished_blackstone_bricks");
        por(4, Y + 1, 44, "polished_blackstone");
        por(4, Y + 3, 44, "redstone_lamp[lit=false]");

        // teclado (depois do saguao: a parede dele e a mesma)
        caixa(TECLADO_X - 1, Y + 1, 12, TECLADO_X + 3, Y + 5, 12, "gray_concrete");
        for (Alvo a : ALVOS) if (a.tipo == Tipo.TECLA) por(a.x, a.y, a.z, "polished_blackstone_button[face=wall,facing=north]");

        // ---------- sala da luva
        sala(-31, 14, -16, 28, 6, "cyan_terracotta", "polished_andesite", "smooth_stone");
        caixa(-16, Y + 1, 19, -16, Y + 3, 21, "air");
        por(-28, Y + 1, 21, "smooth_quartz");
        caixa(-29, Y + 1, 20, -29, Y + 3, 22, "glass");
        caixa(-29, Y + 1, 20, -27, Y + 3, 20, "glass");
        caixa(-29, Y + 1, 22, -27, Y + 3, 22, "glass");
        caixa(-29, Y + 4, 20, -27, Y + 4, 22, "glass");
        por(-22, Y + 5, 15, "stone_button[face=wall,facing=south]");
        por(-22, Y + 6, 15, "redstone_lamp[lit=false]");
        luzes(-27, 17, -19, 25, Y + 6, 4, 10);

        // ---------- linha de producao
        sala(-19, 45, 19, 75, 9, "gray_terracotta", "polished_deepslate", "smooth_stone");
        caixa(-2, Y + 1, 45, 2, Y + 4, 45, "yellow_concrete");
        caixa(-3, Y + 1, 45, -3, Y + 5, 45, "black_concrete");
        caixa(3, Y + 1, 45, 3, Y + 5, 45, "black_concrete");
        caixa(-3, Y + 5, 45, 3, Y + 5, 45, "black_concrete");
        // esteira
        for (int x = -16; x <= 16; x++) {
            por(x, Y + 1, 56, "black_concrete");
            por(x, Y + 2, 56, "black_carpet");
        }
        for (int x = -15; x <= 15; x += 6) por(x, Y + 3, 56, "player_head[rotation=0]");
        // prateleiras altas
        caixa(-18, Y + 1, 49, -16, Y + 5, 51, "iron_block");
        caixa(16, Y + 1, 49, 18, Y + 5, 51, "iron_block");
        caixa(-1, Y + 1, 73, 1, Y + 6, 74, "iron_block");
        // prensas (receptores)
        for (Alvo a : ALVOS) {
            if (a.tipo != Tipo.RECEPTOR) continue;
            String vidrado = switch (a.cor) {
                case "vermelha" -> "red_glazed_terracotta";
                case "amarela" -> "yellow_glazed_terracotta";
                default -> "blue_glazed_terracotta";
            };
            por(a.x, a.y, a.z, vidrado);
            por(a.x, Y + 4, a.z, "redstone_lamp[lit=false]");
            por(a.x - 1, Y + 1, a.z, "iron_block");
            por(a.x + 1, Y + 1, a.z, "iron_block");
            caixa(a.x - 1, Y + 2, a.z, a.x - 1, Y + 3, a.z, "iron_bars");
            caixa(a.x + 1, Y + 2, a.z, a.x + 1, Y + 3, a.z, "iron_bars");
        }
        luzes(-15, 48, 15, 72, Y + 9, 6, 8);
        // porta do vestiario (fechada)
        caixa(19, Y + 1, 66, 19, Y + 3, 68, "iron_block");

        // ---------- vestiario
        sala(19, 61, 30, 75, 5, "light_blue_terracotta", "polished_andesite", "smooth_stone");
        caixa(19, Y + 1, 66, 19, Y + 3, 68, "iron_block");
        for (int z = 63; z <= 69; z += 2) {
            por(29, Y + 1, z, "iron_trapdoor[facing=west,half=bottom,open=true]");
            por(29, Y + 2, z, "iron_trapdoor[facing=west,half=bottom,open=true]");
        }
        caixa(22, Y + 1, 64, 26, Y + 1, 64, "spruce_slab[type=bottom]");
        por(28, Y + 1, 72, "smooth_quartz");
        por(21, Y + 1, 73, "jukebox");
        luzes(22, 64, 28, 72, Y + 5, 3, 8);
        caixa(23, Y + 1, 75, 25, Y + 3, 75, "air");                               // passagem para a sala de controle

        // ---------- sala de controle
        sala(13, 75, 35, 91, 7, "black_terracotta", "polished_blackstone", "smooth_stone");
        caixa(23, Y + 1, 75, 25, Y + 3, 75, "air");
        caixa(15, Y + 1, 85, 33, Y + 1, 85, "light_gray_concrete");               // painel
        for (int x = 15; x <= 33; x += 2) por(x, Y + 2, 85, (x / 2 % 2 == 0) ? "redstone_lamp[lit=false]" : "lever[face=floor,facing=south]");
        por(14, Y + 2, 77, "blue_concrete");
        por(34, Y + 2, 77, "red_concrete");
        por(34, Y + 2, 89, "blue_glazed_terracotta");
        por(14, Y + 2, 89, "red_glazed_terracotta");
        por(34, Y + 3, 89, "redstone_lamp[lit=false]");
        por(14, Y + 3, 89, "redstone_lamp[lit=false]");
        luzes(16, 78, 32, 88, Y + 7, 4, 9);
        caixa(13, Y + 1, 82, 13, Y + 3, 84, "air");                               // saida para a galeria

        // ---------- galeria (fosso) e ponte
        caixa(-10, Y - 12, 78, 12, Y + 9, 88, "gray_concrete");
        caixa(-9, Y - 11, 79, 12, Y + 8, 87, "air");
        caixa(-9, Y - 12, 79, 12, Y - 12, 87, "black_concrete");
        caixa(PONTE_X2 - 1, Y, PONTE_Z1, PONTE_X2, Y, PONTE_Z2, "yellow_concrete");
        caixa(PONTE_X1, Y, PONTE_Z1, PONTE_X1 + 1, Y, PONTE_Z2, "yellow_concrete");
        caixa(13, Y + 1, 82, 13, Y + 3, 84, "air");
        for (int x = -8; x <= 10; x += 6) por(x, Y + 8, 83, "light[level=12]");

        // ---------- sala dos brinquedos gigantes
        sala(-23, 75, -10, 91, 9, "purple_terracotta", "spruce_planks", "dark_oak_planks");
        caixa(-10, Y + 1, 82, -10, Y + 3, 84, "air");
        por(-20, Y + 1, 78, "red_shulker_box");
        por(-20, Y + 2, 78, "blue_shulker_box");
        por(-13, Y + 1, 89, "yellow_shulker_box");
        caixa(-21, Y + 1, 86, -19, Y + 3, 88, "pink_wool");                        // "urso" gigante desmontado
        por(-20, Y + 4, 87, "pink_wool");
        luzes(-20, 78, -13, 88, Y + 9, 5, 6);

        // ---------- dutos (1 x 2)
        caixa(-65, Y, 74, -24, Y + 3, 94, "light_gray_concrete");
        for (int i = DUTO_INICIO; i < DUTO_FIM; i++) {
            double[] a = CAMINHO[i], b = CAMINHO[i + 1];
            int x1 = (int) Math.floor(a[0]), z1 = (int) Math.floor(a[1]), x2 = (int) Math.floor(b[0]), z2 = (int) Math.floor(b[1]);
            caixa(x1, Y + 1, z1, x2, Y + 2, z2, "air");
            caixa(x1, Y, z1, x2, Y, z2, "iron_block");
        }
        caixa(-23, Y + 1, 83, -23, Y + 2, 83, "air");                              // boca do duto (parede da sala)
        caixa(-23, Y + 3, 82, -23, Y + 3, 84, "iron_bars");
        for (int i = DUTO_INICIO; i <= DUTO_FIM; i++) {
            double[] a = CAMINHO[i];
            por((int) Math.floor(a[0]), Y + 2, (int) Math.floor(a[1]), "light[level=4]");
        }

        // ---------- sala final
        sala(-80, 74, -64, 94, 11, "brown_terracotta", "spruce_planks", "dark_oak_planks");
        caixa(-64, Y + 1, 84, -64, Y + 2, 84, "air");                              // saida do duto
        caixa(-79, Y - 10, 75, -65, Y - 1, 93, "air");                             // vao embaixo do piso de tabuas
        caixa(-79, Y - 11, 75, -65, Y - 11, 93, "black_concrete");
        caixa(-80, Y - 11, 74, -80, Y - 1, 94, "brown_terracotta");
        caixa(-64, Y - 11, 74, -64, Y - 1, 94, "brown_terracotta");
        caixa(-80, Y - 11, 74, -64, Y - 1, 74, "brown_terracotta");
        caixa(-80, Y - 11, 94, -64, Y - 1, 94, "brown_terracotta");
        por(-79, Y + 8, 84, "lever[face=wall,facing=east,powered=false]");
        caixa(-80, Y + 7, 83, -80, Y + 9, 85, "yellow_terracotta");
        por(-79, Y + 9, 84, "redstone_lamp[lit=false]");
        luzes(-76, 77, -67, 91, Y + 11, 4, 7);
    }

    // ------------------------------------------------------------------ partes que mudam (zeradas a cada partida)

    void portaPortaria(boolean aberta) {
        caixa(-1, Y + 1, 12, 1, Y + 3, 12, aberta ? "air" : "iron_block");
    }

    void portaLuva(boolean aberta) {
        caixa(-16, Y + 1, 19, -16, Y + 3, 21, aberta ? "air" : "iron_block");
    }

    void portao(boolean aberto) {
        caixa(-2, Y + 1, 45, 2, Y + 4, 45, aberto ? "air" : "yellow_concrete");
    }

    void portaVestiario(boolean aberta) {
        caixa(19, Y + 1, 66, 19, Y + 3, 68, aberta ? "air" : "iron_block");
    }

    void lampada(int x, int y, int z, boolean acesa) {
        por(x, y, z, "redstone_lamp[lit=" + acesa + "]");
    }

    /** Saguao escuro (sem energia) ou claro. */
    void luzSaguao(boolean acesa) {
        for (int x = -12; x <= 12; x += 6)
            for (int z = 16; z <= 42; z += 6) por(x, Y + 13, z, acesa ? "light[level=14]" : "light[level=3]");
    }

    void caixaNaPrateleira(String cor, boolean esta) {
        Alvo a = alvo("caixa_" + cor);
        if (a != null) por(a.x, a.y, a.z, esta ? cor(cor) + "_shulker_box" : "air");
    }

    void caixaNaPrensa(String cor, boolean esta) {
        Alvo a = alvo("receptor_" + cor);
        if (a == null) return;
        por(a.x, a.y + 1, a.z, esta ? cor(cor) + "_shulker_box" : "air");
        lampada(a.x, Y + 4, a.z, esta);
    }

    static String cor(String c) {
        return switch (c) {
            case "vermelha" -> "red";
            case "amarela" -> "yellow";
            default -> "blue";
        };
    }

    void ponte(int colunas) {
        // colunas = quantos blocos (de leste para oeste) ja estao estendidos alem dos tocos
        for (int x = PONTE_X2 - 2; x >= PONTE_X1 + 2; x--) {
            boolean tem = (PONTE_X2 - 2 - x) < colunas;
            caixa(x, Y, PONTE_Z1, x, Y, PONTE_Z2, tem ? "yellow_concrete" : "air");
        }
    }

    static int tamanhoPonte() {
        return (PONTE_X2 - 2) - (PONTE_X1 + 2) + 1;
    }

    void piso(boolean inteiro) {
        if (inteiro) caixa(-79, Y, 75, -65, Y, 93, "spruce_planks");
    }

    void buraco(int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                if (x >= -79 && x <= -65 && z >= 75 && z <= 93) por(x, Y, z, "air");
    }

    void puxador(boolean pronto) {
        lampada(-79, Y + 9, 84, pronto);
    }
}
