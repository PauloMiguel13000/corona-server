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
}
