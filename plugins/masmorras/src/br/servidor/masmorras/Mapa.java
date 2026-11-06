package br.servidor.masmorras;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Mapa-mundi das masmorras: o mundo inteiro (ate a borda) num mapa so, pintado pela cor dos biomas, com uma
 * bandeira em cada masmorra (cor = nivel, nome embaixo; X vermelho = chefe ja derrotado, volta depois) e a seta do
 * jogador. Os biomas sao lidos sem carregar o mapa, aos poucos, uma vez por reinicio.
 */
final class Mapa extends MapRenderer {

    static final int L = 128;

    final Masmorras pl;
    final World w;
    final int meio;
    private Color[] fundo;
    private boolean gerando;
    private int versao = 1;
    private final Map<UUID, Integer> pintado = new HashMap<>();
    private final Map<UUID, Long> ultimo = new HashMap<>();

    Mapa(Masmorras pl, World w, int meio) {
        super(true);
        this.pl = pl;
        this.w = w;
        this.meio = meio;
    }

    /** Coluna/linha do mapa (0 a 127) para uma coordenada do mundo. */
    int px(double x) {
        return (int) Math.floor((x + meio) * L / (2.0 * meio));
    }

    static byte cursor(int p) {
        return (byte) Math.max(-128, Math.min(127, p * 2 - 127));
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player p) {
        if (fundo == null) {
            gerar();
            return;
        }
        UUID id = p.getUniqueId();
        Integer v = pintado.get(id);
        if (v == null || v != versao) {
            for (int y = 0; y < L; y++) for (int x = 0; x < L; x++) canvas.setPixelColor(x, y, fundo[y * L + x]);
            pintado.put(id, versao);
        }
        long agora = System.currentTimeMillis();
        Long u = ultimo.get(id);
        if (u != null && agora - u < 500) return;
        ultimo.put(id, agora);
        MapCursorCollection cs = new MapCursorCollection();
        for (Masmorra m : pl.todas) {
            if (!m.mundo.equals(w.getName()) || m.tipo.secreta) continue;
            int x = px(m.saidaX), z = px(m.saidaZ);
            if (x < 0 || x >= L || z < 0 || z >= L) continue;
            MapCursor.Type t = m.bossVolta > 0 ? MapCursor.Type.RED_X : bandeira(m.tipo.nivel);
            cs.addCursor(new MapCursor(cursor(x), cursor(z), (byte) 8, t, true, Masmorras.txt(curto(m.tipo) + " " + m.tipo.nivel)));
        }
        // spawn
        cs.addCursor(new MapCursor(cursor(px(0)), cursor(px(0)), (byte) 8, MapCursor.Type.TARGET_POINT, true, Masmorras.txt("Spawn")));
        if (p.getWorld().equals(w)) {
            int x = px(p.getX()), z = px(p.getZ());
            boolean dentro = x >= 0 && x < L && z >= 0 && z < L;
            float yaw = p.getLocation().getYaw();
            byte dir = (byte) (Math.floorMod(Math.round((yaw + 180f) / 22.5f), 16));
            cs.addCursor(new MapCursor(cursor(Math.max(0, Math.min(L - 1, x))), cursor(Math.max(0, Math.min(L - 1, z))), dir,
                    dentro ? MapCursor.Type.PLAYER : MapCursor.Type.PLAYER_OFF_MAP, true));
        }
        canvas.setCursors(cs);
    }

    static MapCursor.Type bandeira(int nivel) {
        return switch (nivel) {
            case 1 -> MapCursor.Type.BANNER_LIME;
            case 2 -> MapCursor.Type.BANNER_YELLOW;
            case 3 -> MapCursor.Type.BANNER_ORANGE;
            case 4 -> MapCursor.Type.BANNER_RED;
            default -> MapCursor.Type.BANNER_PURPLE;
        };
    }

    static String curto(Tipo t) {
        if (t.curto != null) return t.curto;
        return switch (t.id) {
            case "CRIPTA" -> "Cripta";
            case "NINHO" -> "Aranha";
            case "MINA" -> "Mina";
            case "PANTANO" -> "Bruxa";
            case "GELO" -> "Gelo";
            case "TORRE" -> "Torre";
            case "FORJA" -> "Forja";
            case "BASTIAO" -> "Bastião";
            case "WITHER" -> "Wither";
            case "CIDADELA" -> "Cidadela";
            case "DESERTO" -> "Faraó";
            case "QUARTEL" -> "Quartel";
            case "VENTOS" -> "Ventos";
            default -> "Prisão";
        };
    }

    /** Le os biomas aos poucos (1024 por tick) e monta o desenho. */
    private void gerar() {
        if (gerando) return;
        gerando = true;
        Color[] f = new Color[L * L];
        int[] i = {0};
        int[] tarefa = new int[1];
        tarefa[0] = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (int k = 0; k < 1024 && i[0] < L * L; k++, i[0]++) {
                int x = i[0] % L, z = i[0] / L;
                double bx = -meio + (x + 0.5) * (2.0 * meio / L), bz = -meio + (z + 0.5) * (2.0 * meio / L);
                String b;
                try {
                    b = w.getBiome((int) bx, 64, (int) bz).getKey().getKey().toUpperCase(Locale.ROOT);
                } catch (Throwable t) {
                    b = "";
                }
                f[i[0]] = cor(b, x, z);
            }
            if (i[0] < L * L) return;
            // moldura (a borda do mundo) e cruz fraca no spawn
            Color moldura = new Color(70, 50, 30);
            for (int k = 0; k < L; k++) {
                f[k] = moldura;
                f[(L - 1) * L + k] = moldura;
                f[k * L] = moldura;
                f[k * L + L - 1] = moldura;
            }
            fundo = f;
            versao++;
            Bukkit.getScheduler().cancelTask(tarefa[0]);
        }, 1L, 1L).getTaskId();
    }

    /** Cor de cada bioma (perto da cor do Minecraft nos mapas). */
    static Color cor(String b, int x, int z) {
        Color c;
        if (b.isEmpty()) c = new Color(120, 120, 120);
        else if (b.contains("DEEP") && b.contains("OCEAN")) c = new Color(40, 60, 150);
        else if (b.contains("OCEAN")) c = new Color(60, 90, 190);
        else if (b.contains("RIVER")) c = new Color(80, 120, 220);
        else if (b.contains("BEACH") || b.contains("DESERT")) c = new Color(220, 205, 150);
        else if (b.contains("BADLANDS")) c = new Color(190, 110, 50);
        else if (b.contains("SNOW") || b.contains("FROZEN") || b.contains("ICE") || b.contains("GROVE") || b.contains("JAGGED")) c = new Color(240, 245, 250);
        else if (b.contains("PEAK") || b.contains("STONY") || b.contains("WINDSWEPT") || b.contains("SLOPES")) c = new Color(140, 140, 140);
        else if (b.contains("SWAMP") || b.contains("MANGROVE")) c = new Color(80, 100, 60);
        else if (b.contains("JUNGLE") || b.contains("BAMBOO")) c = new Color(50, 150, 40);
        else if (b.contains("SAVANNA")) c = new Color(170, 165, 90);
        else if (b.contains("TAIGA") || b.contains("DARK_FOREST")) c = new Color(50, 100, 60);
        else if (b.contains("PALE")) c = new Color(170, 175, 170);
        else if (b.contains("CHERRY")) c = new Color(230, 170, 200);
        else if (b.contains("MUSHROOM")) c = new Color(150, 110, 150);
        else if (b.contains("FOREST") || b.contains("BIRCH")) c = new Color(60, 130, 50);
        else if (b.contains("PLAINS") || b.contains("MEADOW") || b.contains("SUNFLOWER")) c = new Color(110, 175, 70);
        // Nether
        else if (b.contains("CRIMSON")) c = new Color(150, 30, 40);
        else if (b.contains("WARPED")) c = new Color(40, 120, 120);
        else if (b.contains("SOUL_SAND")) c = new Color(90, 70, 55);
        else if (b.contains("BASALT")) c = new Color(60, 60, 65);
        else if (b.contains("NETHER")) c = new Color(110, 40, 35);
        // End
        else if (b.contains("HIGHLANDS") || b.contains("MIDLANDS")) c = new Color(220, 220, 160);
        else if (b.contains("BARRENS") || b.contains("SMALL_END")) c = new Color(40, 30, 50);
        else if (b.contains("THE_END")) c = new Color(200, 200, 150);
        else c = new Color(110, 160, 80);
        // leve textura para nao ficar chapado
        int r = (int) (((x * 7919L + z * 104729L) >>> 3) & 7) - 3;
        return new Color(lim(c.getRed() + r * 3), lim(c.getGreen() + r * 3), lim(c.getBlue() + r * 3));
    }

    private static int lim(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
