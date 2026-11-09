package br.servidor.turno;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * O restaurante "Zoo Mecânico do Seu Zé" (mapa original do Turno da Noite). Tudo construido por codigo numa instancia
 * (deslocamento em x). Coordenadas relativas ao centro da cadeira do vigia (0,0): escritorio no sul, dois corredores
 * (oeste/leste) subindo para o salao, palco ao norte, cozinha e banheiros a leste, bastidores e a Cortina a oeste,
 * deposito no corredor oeste. Piso y=64 (pes em 65), paredes ate y=68, teto y=69.
 */
final class Mapa {

    static final int Y = 64;
    static final int ESPACO = 160;         // distancia entre instancias (em x)

    /** Sala: nome, caixa (x1,z1,x2,z2) e posicao da camera (x, y, z, yaw, pitch) ou null (so audio). */
    record Sala(String id, String nome, int x1, int z1, int x2, int z2, double[] cam) {}

    static final Map<String, Sala> SALAS = new LinkedHashMap<>();

    static {
        SALAS.put("escritorio", new Sala("escritorio", "Escritório", -4, -3, 4, 3, null));
        SALAS.put("corr_oeste", new Sala("corr_oeste", "Corredor Oeste", -9, -18, -6, 3, new double[]{-7.5, 67, -17.5, 0, 25}));
        SALAS.put("corr_leste", new Sala("corr_leste", "Corredor Leste", 6, -18, 9, 3, new double[]{7.5, 67, -17.5, 0, 25}));
        SALAS.put("salao", new Sala("salao", "Salão", -14, -36, 14, -19, new double[]{0.5, 67, -19.5, 180, 25}));
        SALAS.put("palco", new Sala("palco", "Palco", -6, -42, 6, -37, new double[]{0.5, 67, -31.5, 180, 15}));
        SALAS.put("cozinha", new Sala("cozinha", "Cozinha", 15, -36, 24, -27, null));
        SALAS.put("banheiros", new Sala("banheiros", "Banheiros", 15, -25, 24, -19, new double[]{23.5, 67, -19.5, 135, 25}));
        SALAS.put("bastidores", new Sala("bastidores", "Bastidores", -24, -36, -15, -27, new double[]{-23.5, 67, -27.5, -135, 25}));
        SALAS.put("cortina", new Sala("cortina", "A Cortina", -24, -25, -15, -19, new double[]{-14.5, 67, -19.5, 135, 20}));
        SALAS.put("deposito", new Sala("deposito", "Depósito", -16, -12, -10, -6, new double[]{-10.5, 67, -6.5, 135, 25}));
    }

    /** Cameras na ordem do tablet: id da sala. Cozinha = so audio. */
    static final String[] CAMERAS = {"palco", "salao", "cortina", "corr_oeste", "canto_oeste", "deposito", "corr_leste", "canto_leste", "bastidores", "cozinha", "banheiros"};
    static final String[] CAMERAS_NOME = {"1A Palco", "1B Salão", "1C Cortina", "2A Corredor Oeste", "2B Canto Oeste", "3 Depósito",
            "4A Corredor Leste", "4B Canto Leste", "5 Bastidores", "6 Cozinha", "7 Banheiros"};
    /** Cameras dos cantos (junto das portas), sem sala propria. */
    static final double[] CAM_CANTO_OESTE = {-7.5, 67, -3.5, 0, 35};
    static final double[] CAM_CANTO_LESTE = {7.5, 67, -3.5, 0, 35};

    /** Onde os animatronicos param: lugar -> (x, y, z, yaw). */
    static final Map<String, double[]> LUGARES = new LinkedHashMap<>();

    static {
        LUGARES.put("palco_capi", new double[]{0.5, 65, -39.5, 0});
        LUGARES.put("palco_tuca", new double[]{-3.5, 65, -39.5, 0});
        LUGARES.put("palco_jaca", new double[]{3.5, 65, -39.5, 0});
        LUGARES.put("salao_a", new double[]{-5.5, 65, -27.5, 20});
        LUGARES.put("salao_b", new double[]{6.5, 65, -25.5, -20});
        LUGARES.put("banheiros", new double[]{20.5, 65, -22.5, 90});
        LUGARES.put("cozinha", new double[]{20.5, 65, -31.5, 90});
        LUGARES.put("bastidores", new double[]{-20.5, 65, -31.5, -90});
        LUGARES.put("deposito", new double[]{-13.5, 65, -9.5, -90});
        LUGARES.put("corr_oeste", new double[]{-7.5, 65, -12.5, 0});
        LUGARES.put("canto_oeste", new double[]{-7.5, 65, 0.5, -90});
        LUGARES.put("corr_leste", new double[]{7.5, 65, -12.5, 0});
        LUGARES.put("canto_leste", new double[]{7.5, 65, 0.5, 90});
        LUGARES.put("cortina_0", new double[]{-21.5, 65, -22.5, -90});   // escondido
        LUGARES.put("cortina_1", new double[]{-17.5, 65, -22.5, -90});   // espiando
        LUGARES.put("cortina_2", new double[]{-15.5, 65, -22.5, -90});   // fora da cortina
        LUGARES.put("cortina_3", new double[]{-14.5, 65, -22.5, -90});   // pronto para correr
    }

    static final int CADEIRA_Z = 0;
    static final double[] CADEIRA = {0.5, 65, 0.5, 180, 10};   // vigia olhando para o norte (para a mesa)
    // portas: abertura 2 larga x 3 alta nas paredes oeste (x=-5) e leste (x=5), z -1..0
    static final int PORTA_X_OESTE = -5, PORTA_X_LESTE = 5, PORTA_Z1 = -1, PORTA_Z2 = 0;
    // botoes em cima da mesa (z=-2, y+2): porta oeste, luz oeste, luz leste, porta leste
    static final int BOTAO_Z = -2, BOTAO_PORTA_OESTE_X = -2, BOTAO_LUZ_OESTE_X = -1, BOTAO_LUZ_LESTE_X = 1, BOTAO_PORTA_LESTE_X = 2;

    private final World w;
    private final int ox;

    Mapa(World w, int ox) {
        this.w = w;
        this.ox = ox;
    }

    Location loc(double x, double y, double z, float yaw, float pitch) {
        return new Location(w, x + ox, y, z, yaw, pitch);
    }

    Location loc(double[] p) {
        return new Location(w, p[0] + ox, p[1], p[2], (float) p[3], p.length > 4 ? (float) p[4] : 0f);
    }

    Location camera(int i) {
        String id = CAMERAS[i];
        if (id.equals("canto_oeste")) return loc(CAM_CANTO_OESTE);
        if (id.equals("canto_leste")) return loc(CAM_CANTO_LESTE);
        Sala s = SALAS.get(id);
        return s == null || s.cam == null ? null : loc(s.cam);
    }

    Block bloco(int x, int y, int z) {
        return w.getBlockAt(x + ox, y, z);
    }

    private void por(int x, int y, int z, String d) {
        BlockData bd;
        try {
            bd = Bukkit.createBlockData(d.contains(":") || d.contains("[") ? d : "minecraft:" + d);
        } catch (IllegalArgumentException e) {
            return;
        }
        bloco(x, y, z).setBlockData(bd, false);
    }

    private void caixa(int x1, int y1, int z1, int x2, int y2, int z2, String d) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) por(x, y, z, d);
    }

    /** Constroi a instancia inteira (uma vez). */
    void construir() {
        int X1 = -26, Z1 = -44, X2 = 26, Z2 = 5;
        // chao e teto gerais, tudo cheio de parede primeiro (as salas sao escavadas depois)
        caixa(X1, Y - 1, Z1, X2, Y - 1, Z2, "deepslate_bricks");
        caixa(X1, Y, Z1, X2, Y, Z2, "stripped_dark_oak_wood");
        caixa(X1, Y + 1, Z1, X2, Y + 4, Z2, "stripped_spruce_wood");   // miolo (vira parede onde nao for sala)
        caixa(X1, Y + 5, Z1, X2, Y + 5, Z2, "dark_oak_planks");        // teto
        // escava as salas (ar de y+1 a y+4) e piso proprio
        for (Sala s : SALAS.values()) {
            caixa(s.x1, Y + 1, s.z1, s.x2, Y + 4, s.z2, "air");
            String piso = switch (s.id) {
                case "escritorio" -> "polished_andesite";
                case "salao" -> "black_terracotta";
                case "palco" -> "red_concrete";
                case "cozinha", "banheiros" -> "white_glazed_terracotta";
                case "cortina" -> "purple_terracotta";
                default -> "cobbled_deepslate";
            };
            caixa(s.x1, Y, s.z1, s.x2, Y, s.z2, piso);
        }
        // salao xadrez
        Sala sl = SALAS.get("salao");
        for (int x = sl.x1; x <= sl.x2; x++) for (int z = sl.z1; z <= sl.z2; z++) if (((x + z) & 1) == 0) por(x, Y, z, "white_terracotta");
        // palco elevado com cortina atras
        Sala pc = SALAS.get("palco");
        caixa(pc.x1, Y + 1, pc.z1, pc.x2, Y + 1, pc.z2, "dark_oak_planks");
        caixa(pc.x1, Y + 2, pc.z1, pc.x2, Y + 4, pc.z1, "red_wool");
        for (int x = pc.x1 + 1; x <= pc.x2 - 1; x += 3) por(x, Y + 4, pc.z2 + 1, "redstone_lamp[lit=true]");
        caixa(pc.x1, Y + 1, pc.z2 + 1, pc.x2, Y + 1, pc.z2 + 1, "dark_oak_stairs[facing=south]");
        // passagens entre salas
        caixa(-9, Y + 1, -19, -6, Y + 3, -19, "air");   // corredor oeste -> salao
        caixa(6, Y + 1, -19, 9, Y + 3, -19, "air");      // corredor leste -> salao
        caixa(15, Y + 1, -31, 15, Y + 3, -30, "air");    // salao -> cozinha
        caixa(15, Y + 1, -23, 15, Y + 3, -22, "air");    // salao -> banheiros
        caixa(-15, Y + 1, -31, -15, Y + 3, -30, "air");  // salao -> bastidores
        caixa(-15, Y + 1, -23, -15, Y + 3, -21, "purple_wool");  // cortina (fechada)
        por(-15, Y + 3, -22, "purple_wool");
        caixa(-10, Y + 1, -10, -10, Y + 3, -9, "air");   // corredor oeste -> deposito
        caixa(PORTA_X_OESTE, Y + 1, PORTA_Z1, PORTA_X_OESTE, Y + 3, PORTA_Z2, "air");  // portas do escritorio (abertas)
        caixa(PORTA_X_LESTE, Y + 1, PORTA_Z1, PORTA_X_LESTE, Y + 3, PORTA_Z2, "air");
        // janelas ao lado das portas (z 1..2, y+2..y+3)
        caixa(PORTA_X_OESTE, Y + 2, 1, PORTA_X_OESTE, Y + 3, 2, "glass");
        caixa(PORTA_X_LESTE, Y + 2, 1, PORTA_X_LESTE, Y + 3, 2, "glass");
        // batentes de metal das portas
        for (int x : new int[]{PORTA_X_OESTE, PORTA_X_LESTE}) {
            por(x, Y + 4, PORTA_Z1, "iron_block");
            por(x, Y + 4, PORTA_Z2, "iron_block");
            por(x, Y + 1, PORTA_Z1 - 1, "iron_block");
            por(x, Y + 2, PORTA_Z1 - 1, "iron_block");
            por(x, Y + 3, PORTA_Z1 - 1, "iron_block");
            por(x, Y + 1, PORTA_Z2 + 1, "iron_block");
            por(x, Y + 2, PORTA_Z2 + 1, "iron_block");
            por(x, Y + 3, PORTA_Z2 + 1, "iron_block");
        }
        // escritorio: mesa com os 4 botoes (porta/luz de cada lado), monitores na parede, cadeira, posteres, luz fraca
        caixa(-2, Y + 1, -2, 2, Y + 1, -2, "dark_oak_slab[type=top]");
        por(BOTAO_PORTA_OESTE_X, Y + 2, BOTAO_Z, "polished_blackstone_button[face=floor,facing=south]");
        por(BOTAO_LUZ_OESTE_X, Y + 2, BOTAO_Z, "stone_button[face=floor,facing=south]");
        por(BOTAO_LUZ_LESTE_X, Y + 2, BOTAO_Z, "stone_button[face=floor,facing=south]");
        por(BOTAO_PORTA_LESTE_X, Y + 2, BOTAO_Z, "polished_blackstone_button[face=floor,facing=south]");
        por(0, Y + 2, -2, "lever[face=floor]");
        por(-1, Y + 2, -3, "observer[facing=south]");
        por(1, Y + 2, -3, "observer[facing=south]");
        por(0, Y + 3, -3, "observer[facing=south]");
        por(0, Y + 1, CADEIRA_Z, "dark_oak_stairs[facing=south]");    // cadeira (virada para o norte)
        por(-3, Y + 3, -3, "painting[facing=south]");
        por(3, Y + 3, -3, "painting[facing=south]");
        por(0, Y + 4, 0, "light[level=7]");
        por(0, Y + 5, 0, "sea_lantern");
        por(0, Y + 4, -2, "light[level=5]");
        // luzes fracas nas salas (escuro, mas da para ver um pouco); corredores sem luz
        for (Sala s : SALAS.values()) {
            if (s.id.startsWith("corr") || s.id.equals("escritorio")) continue;
            int cx = (s.x1 + s.x2) / 2, cz = (s.z1 + s.z2) / 2;
            por(cx, Y + 4, cz, s.id.equals("palco") ? "light[level=10]" : "light[level=4]");
            if (s.id.equals("salao")) {
                por(cx - 8, Y + 4, cz, "light[level=4]");
                por(cx + 8, Y + 4, cz, "light[level=4]");
            }
        }
        // mesas do salao
        for (int x = -10; x <= 10; x += 5) for (int z = -33; z <= -23; z += 5) {
            por(x, Y + 1, z, "dark_oak_fence");
            por(x, Y + 2, z, "dark_oak_pressure_plate");
            por(x + 1, Y + 1, z, "dark_oak_stairs[facing=west]");
            por(x - 1, Y + 1, z, "dark_oak_stairs[facing=east]");
        }
        // cozinha e banheiros
        caixa(16, Y + 1, -36, 24, Y + 1, -36, "furnace[facing=south]");
        caixa(16, Y + 1, -27, 24, Y + 1, -27, "smooth_stone");
        por(18, Y + 2, -27, "iron_trapdoor[half=bottom]");
        caixa(16, Y + 1, -19, 24, Y + 1, -19, "cauldron");
        caixa(16, Y + 1, -25, 24, Y + 1, -25, "white_concrete");
        // bastidores: cabecas e pecas
        por(-23, Y + 1, -35, "chest[facing=south]");
        por(-21, Y + 1, -35, "armor_stand_dummy");
        caixa(-24, Y + 2, -36, -15, Y + 2, -36, "dark_oak_slab[type=bottom]");
        for (int x = -23; x <= -16; x += 2) por(x, Y + 3, -36, "skeleton_skull[rotation=8]");
        // cortina: lugar do Boto com um palquinho
        caixa(-24, Y + 1, -25, -16, Y + 1, -19, "purple_terracotta");
        por(-23, Y + 2, -25, "soul_lantern");
        // deposito: prateleiras
        caixa(-16, Y + 1, -12, -16, Y + 3, -6, "bookshelf");
        caixa(-16, Y + 1, -12, -16, Y + 3, -12, "barrel");
        // saida de emergencia (decoracao) no corredor leste
        por(9, Y + 2, -17, "iron_door[half=lower,facing=west]");
        por(9, Y + 3, -17, "iron_door[half=upper,facing=west]");
        // cabines das cameras (barreira invisivel, 1x2, onde o vigia fica quando olha a camera)
        for (int i = 0; i < CAMERAS.length; i++) {
            Location c = camera(i);
            if (c == null) continue;
            int cx = c.getBlockX() - ox, cz = c.getBlockZ();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 1; dy++) {
                if (dx == 0 && dz == 0) {
                    por(cx + dx, Y + 3 + dy, cz + dz, "air");
                    continue;
                }
                por(cx + dx, Y + 3 + dy, cz + dz, "barrier");
            }
            por(cx, Y + 2, cz, "barrier");   // chao da cabine
        }
        // cabine escura (camera da cozinha: so audio)
        caixa(-1, Y + 7, -4, 1, Y + 10, -2, "black_concrete");
        caixa(0, Y + 8, -3, 0, Y + 9, -3, "air");
    }

    /** Onde o vigia fica na camera sem imagem (cozinha). */
    Location cameraEscura() {
        return loc(0.5, Y + 8, -2.5, 0, 0);
    }

    /** Fecha (true) ou abre a porta oeste/leste: paineis de metal na abertura. */
    void porta(boolean oeste, boolean fechar) {
        int x = oeste ? PORTA_X_OESTE : PORTA_X_LESTE;
        caixa(x, Y + 1, PORTA_Z1, x, Y + 3, PORTA_Z2, fechar ? "iron_block" : "air");
    }

    /** Luz do canto (fora da porta): acende/apaga blocos de luz no corredor junto da porta. */
    void luz(boolean oeste, boolean acesa) {
        int x = oeste ? -7 : 7;
        for (int z = -2; z <= 2; z += 2) por(x, Y + 4, z, acesa ? "light[level=15]" : "air");
    }

    /** Cortina do Boto: aberta (vazia) ou fechada (la roxa). */
    void cortina(boolean fechada) {
        caixa(-15, Y + 1, -23, -15, Y + 3, -21, fechada ? "purple_wool" : "air");
    }

    /** Luz do escritorio (acaba a energia). */
    void luzEscritorio(boolean acesa) {
        por(0, Y + 4, 0, acesa ? "light[level=7]" : "air");
        por(0, Y + 4, -2, acesa ? "light[level=5]" : "air");
        por(0, Y + 5, 0, acesa ? "sea_lantern" : "dark_oak_planks");
        Sala pc = SALAS.get("palco");
        for (int x = pc.x1 + 1; x <= pc.x2 - 1; x += 3) por(x, Y + 4, pc.z2 + 1, acesa ? "redstone_lamp[lit=true]" : "redstone_lamp[lit=false]");
    }
}
