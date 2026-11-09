package teste.vista;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

/** So para teste local: /vista <mundo> <cx> <cz> <raio> <ymin> <ymax> <arquivo.png> [lado] -> foto de cima (ou de lado). */
public final class Vista extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a[0].equals("iso")) return iso(s, a);
        World w = Bukkit.getWorld(a[0]);
        int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]), r = Integer.parseInt(a[3]), y0 = Integer.parseInt(a[4]), y1 = Integer.parseInt(a[5]);
        boolean lado = a.length > 7 && a[7].equals("lado");
        int esc = 6, n = 2 * r + 1;
        BufferedImage img = new BufferedImage(n * esc, (lado ? (y1 - y0 + 1) : n) * esc, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < (lado ? y1 - y0 + 1 : n); j++) {
                int rgb = 0x1a2a3a;
                if (!lado) {
                    for (int y = y1; y >= y0; y--) {
                        Block b = w.getBlockAt(cx - r + i, y, cz - r + j);
                        if (b.getType().isAir() || b.getType().name().equals("LIGHT")) continue;
                        java.awt.Color col = new java.awt.Color(b.getBlockData().getMapColor().asRGB());
                        float f = 0.65f + 0.35f * (y - y0) / Math.max(1f, y1 - y0);
                        rgb = new java.awt.Color(Math.min(255, (int) (col.getRed() * f)), Math.min(255, (int) (col.getGreen() * f)), Math.min(255, (int) (col.getBlue() * f))).getRGB();
                        if (b.getType().name().contains("PORTAL")) rgb = 0x8a2be2;
                        break;
                    }
                } else {
                    int y = y1 - j;
                    for (int z = cz + r; z >= cz - r; z--) {
                        Block b = w.getBlockAt(cx - r + i, y, z);
                        if (b.getType().isAir() || b.getType().name().equals("LIGHT")) continue;
                        rgb = b.getBlockData().getMapColor().asRGB();
                        if (b.getType().name().contains("PORTAL")) rgb = 0x8a2be2;
                        break;
                    }
                }
                for (int dx = 0; dx < esc; dx++) for (int dy = 0; dy < esc; dy++) img.setRGB(i * esc + dx, j * esc + dy, rgb);
            }
        int ents = 0;
        if (!lado) for (Entity e : w.getEntities()) {
            int i = (int) Math.floor(e.getLocation().getX()) - (cx - r), j = (int) Math.floor(e.getLocation().getZ()) - (cz - r);
            if (i < 0 || j < 0 || i >= n || j >= n) continue;
            int col = e instanceof TextDisplay ? 0xffff00 : e instanceof ItemDisplay ? 0x00ffff : e instanceof Interaction ? 0xff00ff : -1;
            if (col == -1) continue;
            ents++;
            for (int dx = 1; dx < esc - 1; dx++) for (int dy = 1; dy < esc - 1; dy++) if (dx == 1 || dy == 1 || dx == esc - 2 || dy == esc - 2) img.setRGB(i * esc + dx, j * esc + dy, col);
        }
        try {
            ImageIO.write(img, "png", new File(a[6]));
        } catch (Exception e) {
            s.sendMessage("erro " + e);
        }
        s.sendMessage("vista ok " + a[6] + " enfeites=" + ents);
        return true;
    }

    /** vista iso <mundo> <cx> <cz> <raio> <y0> <y1> <arquivo> [giro 0-3]: desenho em perspectiva (isometrico). */
    private boolean iso(CommandSender s, String[] a) {
        World w = Bukkit.getWorld(a[1]);
        int cx = Integer.parseInt(a[2]), cz = Integer.parseInt(a[3]), r = Integer.parseInt(a[4]), y0 = Integer.parseInt(a[5]), y1 = Integer.parseInt(a[6]);
        int giro = a.length > 8 ? Integer.parseInt(a[8]) : 0;
        int n = 2 * r + 1, h = y1 - y0 + 1, e = 6; // e = meia largura do losango
        int larg = (2 * n + 2) * e, alt = (n + 2) * e + h * e + 40;
        BufferedImage img = new BufferedImage(larg, alt, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x6fa8dc), 0, alt, new java.awt.Color(0xcfe2f3)));
        g.fillRect(0, 0, larg, alt);
        int blocos = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                for (int y = y0; y <= y1; y++) {
                    // (i, j) = coordenadas na tela (fundo -> frente); gira para o mundo
                    int dx = i - r, dz = j - r, wx, wz;
                    switch (giro) {
                        case 1 -> { wx = -dz; wz = dx; }
                        case 2 -> { wx = -dx; wz = -dz; }
                        case 3 -> { wx = dz; wz = -dx; }
                        default -> { wx = dx; wz = dz; }
                    }
                    Block b = w.getBlockAt(cx + wx, y, cz + wz);
                    if (b.getType().isAir() || b.getType().name().equals("LIGHT")) continue;
                    int rgb = b.getBlockData().getMapColor().asRGB();
                    String nome = b.getType().name();
                    if (nome.contains("PORTAL")) rgb = 0x8a2be2;
                    if (nome.contains("LANTERN") || nome.contains("CAMPFIRE")) rgb = 0xffc040;
                    if (nome.contains("BANNER")) rgb = nome.startsWith("WHITE") ? 0xf4f4f4 : 0xd01028;
                    if (nome.contains("GLASS")) rgb = nome.startsWith("RED") ? 0xff5060 : rgb == 0 ? 0xc0e0ff : rgb;
                    if (rgb == 0) rgb = 0x707070;
                    boolean fino = nome.contains("CHAIN") || nome.contains("LANTERN") || nome.contains("BANNER") || nome.contains("FENCE") || nome.contains("ROD")
                            || nome.contains("SHORT_GRASS") || nome.contains("TULIP") || nome.contains("POPPY") || nome.contains("PANE") || nome.contains("WALL");
                    int px = (i - j) * e + larg / 2, py = (i + j) * e / 2 + (y1 - y) * e + 20;
                    java.awt.Color c = new java.awt.Color(rgb);
                    if (fino) {
                        g.setColor(c);
                        g.fillRect(px - 1, py, 3, e + e / 2);
                    } else {
                        g.setColor(c);
                        g.fillPolygon(new int[] {px, px + e, px, px - e}, new int[] {py, py + e / 2, py + e, py + e / 2}, 4);
                        g.setColor(c.darker());
                        g.fillPolygon(new int[] {px - e, px, px, px - e}, new int[] {py + e / 2, py + e, py + 2 * e, py + e + e / 2}, 4);
                        g.setColor(c.darker().darker());
                        g.fillPolygon(new int[] {px, px + e, px + e, px}, new int[] {py + e, py + e / 2, py + e + e / 2, py + 2 * e}, 4);
                    }
                    blocos++;
                }
        g.dispose();
        try {
            ImageIO.write(img, "png", new File(a[7]));
        } catch (Exception ex) {
            s.sendMessage("erro " + ex);
        }
        s.sendMessage("vista iso ok " + a[7] + " blocos=" + blocos);
        return true;
    }
}
