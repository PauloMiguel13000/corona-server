package br.servidor.slender;

import java.awt.Color;
import java.util.Random;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

/** As 8 folhas: mapas desenhados a mao (papel rasgado, frases e rabiscos do Slenderman). */
final class Folhas {
    private Folhas() {}

    /** Frases das folhas (sem acento: a fonte do mapa nao tem todas as letras). */
    static final String[][] TEXTOS = {
            {"SEMPRE OBSERVA", "SEM OLHOS"},
            {"NAO DA PRA", "CORRER"},
            {"NAO OLHE", "OU ELE TE LEVA"},
            {"ELE SEGUE"},
            {"SOCORRO", "SOCORRO", "SOCORRO"},
            {"ME DEIXE", "EM PAZ"},
            {"NAO NAO NAO", "NAO NAO NAO", "NAO NAO"},
            {"IGNORADO", "MAS NAO", "ESQUECIDO"},
    };

    /** Frase da folha para o chat (com acentos). */
    static final String[] FRASES = {
            "Sempre observa. Sem olhos.",
            "Não dá pra correr.",
            "Não olhe... ou ele te leva.",
            "Ele segue.",
            "Socorro. Socorro. Socorro.",
            "Me deixe em paz.",
            "NÃO NÃO NÃO NÃO NÃO",
            "Ignorado, mas não esquecido.",
    };

    static final class Desenho extends MapRenderer {
        private final int idx;
        private boolean feito;

        Desenho(int idx) {
            super(false);
            this.idx = idx;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            if (feito) return;
            desenhar(canvas, idx);
            feito = true;
        }
    }

    static void preparar(MapView v, int idx) {
        for (MapRenderer r : v.getRenderers()) v.removeRenderer(r);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.setLocked(true);
        v.addRenderer(new Desenho(idx));
    }

    private static void desenhar(MapCanvas c, int idx) {
        Random r = new Random(1000 + idx);
        // papel com manchas
        for (int x = 0; x < 128; x++)
            for (int y = 0; y < 128; y++) {
                int n = r.nextInt(14) - 7;
                double mancha = Math.hypot(x - 100 + idx * 7, y - 30 - idx * 5);
                int esc = mancha < 18 ? 28 : mancha < 24 ? 12 : 0;
                c.setPixelColor(x, y, new Color(clamp(226 + n - esc), clamp(220 + n - esc - 4), clamp(198 + n - esc - 14)));
            }
        // bordas rasgadas (transparente mostra a parede)
        for (int x = 0; x < 128; x++) {
            int cima = r.nextInt(4), baixo = r.nextInt(4);
            for (int y = 0; y < cima; y++) c.setPixel(x, y, (byte) 0);
            for (int y = 127; y > 127 - baixo; y--) c.setPixel(x, y, (byte) 0);
        }
        for (int y = 0; y < 128; y++) {
            int esq = r.nextInt(3), dir = r.nextInt(3);
            for (int x = 0; x < esq; x++) c.setPixel(x, y, (byte) 0);
            for (int x = 127; x > 127 - dir; x--) c.setPixel(x, y, (byte) 0);
        }
        Color tinta = new Color(28, 26, 30);
        String cor = "§" + MapPalette.matchColor(tinta) + ";";
        String[] linhas = TEXTOS[idx];
        int y0 = idx == 4 || idx == 6 ? 10 : 14;
        for (int i = 0; i < linhas.length; i++) {
            String t = linhas[i];
            if (!MinecraftFont.Font.isValid(t)) continue;
            int w = MinecraftFont.Font.getWidth(t);
            int dx = idx == 4 ? (i - 1) * 9 : r.nextInt(5) - 2;
            c.drawText(Math.max(2, (128 - w) / 2 + dx), y0 + i * 12, MinecraftFont.Font, cor + t);
        }
        int tipo = idx % 4;
        if (tipo == 0 || tipo == 2) {
            // arvores e o vulto
            for (int k = 0; k < 4; k++) {
                int tx = 14 + k * 30 + r.nextInt(8);
                linha(c, tx, 120, tx + r.nextInt(5) - 2, 58, tinta, 2);
                linha(c, tx, 80, tx - 8, 70, tinta, 1);
                linha(c, tx, 72, tx + 9, 62, tinta, 1);
            }
            vulto(c, tipo == 0 ? 64 : 40 + r.nextInt(50), tinta);
        } else if (tipo == 1) {
            // rabisco furioso
            for (int k = 0; k < 140; k++) {
                double a = r.nextDouble() * Math.PI * 2, rr = 10 + r.nextDouble() * 28;
                int x1 = 64 + (int) (Math.cos(a) * rr), y1 = 86 + (int) (Math.sin(a) * rr * 0.8);
                linha(c, x1, y1, x1 + r.nextInt(17) - 8, y1 + r.nextInt(17) - 8, tinta, 1);
            }
        } else {
            // olho riscado
            for (int a = 0; a < 360; a += 3) {
                double rad = Math.toRadians(a);
                int x = 64 + (int) (Math.cos(rad) * 30), y = 86 + (int) (Math.sin(rad) * 14);
                c.setPixelColor(x, y, tinta);
                c.setPixelColor(x, y + 1, tinta);
            }
            disco(c, 64, 86, 7, tinta);
            linha(c, 30, 60, 98, 112, tinta, 3);
            linha(c, 98, 60, 30, 112, tinta, 3);
        }
    }

    private static void vulto(MapCanvas c, int x, Color tinta) {
        Color branco = new Color(240, 240, 238);
        linha(c, x, 72, x, 106, tinta, 3);
        linha(c, x, 78, x - 13, 100, tinta, 1);
        linha(c, x, 78, x + 13, 100, tinta, 1);
        linha(c, x - 13, 100, x - 16, 104, tinta, 1);
        linha(c, x + 13, 100, x + 17, 103, tinta, 1);
        linha(c, x, 106, x - 5, 122, tinta, 2);
        linha(c, x, 106, x + 5, 122, tinta, 2);
        disco(c, x, 66, 6, tinta);
        disco(c, x, 66, 4, branco);
    }

    private static void disco(MapCanvas c, int cx, int cy, int r, Color cor) {
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                if (x * x + y * y <= r * r) ponto(c, cx + x, cy + y, cor);
    }

    private static void linha(MapCanvas c, int x0, int y0, int x1, int y1, Color cor, int esp) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            for (int a = 0; a < esp; a++)
                for (int b = 0; b < esp; b++) ponto(c, x0 + a, y0 + b, cor);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static void ponto(MapCanvas c, int x, int y, Color cor) {
        if (x >= 0 && y >= 0 && x < 128 && y < 128 && c.getPixel(x, y) != 0) c.setPixelColor(x, y, cor);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
