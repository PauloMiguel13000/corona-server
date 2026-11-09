package br.servidor.masmorras;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import org.bukkit.block.Biome;

/** Desenha o mapa do Mundo das Masmorras (altura + bioma) direto do gerador, sem carregar chunks. */
final class MapaMundo {
    private MapaMundo() {}

    static void desenhar(GeradorMundo g, int raio, int passo, File saida, List<Masmorra> masmorras) throws Exception {
        int n = raio * 2 / passo;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                int x = -raio + i * passo, z = -raio + j * passo;
                int h = g.altura(x, z);
                Biome b = g.bioma(x, z);
                Color c = cor(b.getKey().getKey(), h, g.vulcao(x, z));
                double sombra = 0.75 + Math.max(-0.35, Math.min(0.45, (h - g.altura(x - passo, z - passo)) * 0.04));
                int[] ilha = g.ilhaNoCeu(x, z);
                if (ilha != null) c = new Color(120, 200, 120);
                img.setRGB(i, j, escurecer(c, sombra).getRGB());
            }
        // aneis dos niveis
        for (int z0 : g.zonas) anel(img, n, raio, passo, z0, Color.WHITE);
        // masmorras
        for (Masmorra m : masmorras) {
            if (m.tudo == null || !m.mundo.equals("masmorras_mundo")) continue;
            int i = (m.tudo.cx() + raio) / passo, j = (m.tudo.cz() + raio) / passo;
            for (int di = -2; di <= 2; di++) for (int dj = -2; dj <= 2; dj++) if (i + di >= 0 && j + dj >= 0 && i + di < n && j + dj < n) img.setRGB(i + di, j + dj, Color.MAGENTA.getRGB());
        }
        ImageIO.write(img, "png", saida);
    }

    /** Foto de cima (cor do mapa de cada bloco + sombra pela altura) de uma area carregada. */
    static void foto(org.bukkit.World w, int cx, int cz, int raio, int esc, File saida) throws Exception {
        int n = raio * 2 + 1;
        BufferedImage img = new BufferedImage(n * esc, n * esc, BufferedImage.TYPE_INT_RGB);
        int[][] alt = new int[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                int x = cx - raio + i, z = cz - raio + j;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                org.bukkit.block.Block b = w.getHighestBlockAt(x, z);
                while (b.getY() > w.getMinHeight() && (b.getType() == org.bukkit.Material.LIGHT || b.getType().isAir())) b = b.getRelative(0, -1, 0);
                alt[i][j] = b.getY();
                org.bukkit.Color mc = b.getBlockData().getMapColor();
                Color c = new Color(mc.getRed(), mc.getGreen(), mc.getBlue());
                if (b.getType() == org.bukkit.Material.WATER) c = new Color(40, 80, 220);
                double sombra = 1.0;
                if (i > 0 && j > 0) sombra = 0.8 + Math.max(-0.4, Math.min(0.4, (alt[i][j] - alt[i - 1][j - 1]) * 0.12));
                int rgb = escurecer(c, sombra).getRGB();
                for (int a = 0; a < esc; a++) for (int bb = 0; bb < esc; bb++) img.setRGB(i * esc + a, j * esc + bb, rgb);
            }
        ImageIO.write(img, "png", saida);
    }

    private static void anel(BufferedImage img, int n, int raio, int passo, int r, Color c) {
        for (int k = 0; k < 2000; k++) {
            double a = k * Math.PI * 2 / 2000;
            int i = (int) ((Math.cos(a) * r + raio) / passo), j = (int) ((Math.sin(a) * r + raio) / passo);
            if (i >= 0 && j >= 0 && i < n && j < n) img.setRGB(i, j, c.getRGB());
        }
    }

    private static Color escurecer(Color c, double f) {
        return new Color((int) Math.min(255, c.getRed() * f), (int) Math.min(255, c.getGreen() * f), (int) Math.min(255, c.getBlue() * f));
    }

    private static Color cor(String b, int h, boolean vulcao) {
        if (vulcao) return h < GeradorMundo.MAR ? new Color(255, 120, 0) : new Color(60, 50, 55);
        if (h < GeradorMundo.MAR) return b.equals("river") ? new Color(60, 110, 220) : new Color(30, 70, 180);
        return switch (b) {
            case "plains" -> new Color(120, 180, 70);
            case "meadow" -> new Color(140, 200, 90);
            case "forest" -> new Color(60, 130, 50);
            case "flower_forest" -> new Color(150, 170, 90);
            case "birch_forest" -> new Color(120, 160, 90);
            case "taiga" -> new Color(40, 100, 70);
            case "swamp" -> new Color(70, 90, 50);
            case "mangrove_swamp" -> new Color(90, 80, 50);
            case "snowy_plains", "snowy_taiga", "snowy_slopes", "grove" -> new Color(230, 235, 245);
            case "ice_spikes", "frozen_peaks" -> new Color(180, 220, 255);
            case "desert" -> new Color(230, 210, 140);
            case "beach" -> new Color(240, 225, 170);
            case "cherry_grove" -> new Color(240, 150, 200);
            case "savanna", "savanna_plateau" -> new Color(180, 170, 80);
            case "jungle", "bamboo_jungle" -> new Color(30, 120, 30);
            case "dark_forest" -> new Color(30, 60, 20);
            case "badlands", "eroded_badlands", "wooded_badlands" -> new Color(200, 110, 60);
            case "pale_garden" -> new Color(170, 180, 170);
            case "old_growth_pine_taiga", "old_growth_spruce_taiga" -> new Color(50, 90, 60);
            case "stony_shore", "windswept_gravelly_hills" -> new Color(130, 130, 130);
            case "jagged_peaks" -> h > 165 ? new Color(250, 250, 255) : new Color(150, 150, 160);
            case "deep_dark" -> new Color(20, 40, 50);
            case "windswept_forest" -> new Color(80, 120, 80);
            default -> new Color(200, 0, 200);
        };
    }
}
