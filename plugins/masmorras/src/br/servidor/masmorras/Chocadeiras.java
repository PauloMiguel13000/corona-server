package br.servidor.masmorras;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Area das Chocadeiras no lobby (pedido do Paulo, 08/11): um ninho redondo no canto sudoeste da praca com 8 chocadeiras
 * do BabyPets (publicas: qualquer um clica numa chocadeira livre com o seu ovo de pet e ela choca em 20 min para quem
 * botou o ovo). As chocadeiras sao as do proprio BabyPets (bloco alcapao de ferro + moveis de display criados pelo
 * IncubatorManager, chamado por reflexao), entao o plugin dos pets cuida do ovo, do tempo e do nascimento.
 */
final class Chocadeiras implements Listener {

    static final int CX = -16, CZ = 16, RAIO = 6;
    private static final int[][] LUGARES = new int[8][2];

    static {
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + k * 45);
            LUGARES[k][0] = (int) Math.round(CX + Math.cos(a) * 4);
            LUGARES[k][1] = (int) Math.round(CZ + Math.sin(a) * 4);
        }
    }

    private final Masmorras pl;
    private final NamespacedKey kLetreiro;
    private World mundo;
    private int y;

    Chocadeiras(Masmorras pl) {
        this.pl = pl;
        kLetreiro = new NamespacedKey(pl, "chocadeiras_letreiro");
    }

    private Object gerente() {
        Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
        if (bp == null || !bp.isEnabled()) return null;
        try {
            Method m = bp.getClass().getMethod("getIncubatorManager");
            return m.invoke(bp);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            pl.getLogger().warning("Chocadeiras: nao achei o IncubatorManager do BabyPets: " + ex);
            return null;
        }
    }

    private boolean ehChocadeira(Object ger, Block b) {
        try {
            return (Boolean) ger.getClass().getMethod("isIncubatorBlock", Block.class).invoke(ger, b);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private void criarMoveis(Object ger, Location l, float yaw) {
        try {
            ger.getClass().getMethod("createIncubatorFurniture", Location.class, float.class).invoke(ger, l, yaw);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            pl.getLogger().warning("Chocadeiras: nao consegui criar os moveis da chocadeira em " + l.getBlockX() + "," + l.getBlockZ() + ": " + ex);
        }
    }

    /** Constroi o ninho (uma vez) e garante as 8 chocadeiras (toda vez: se alguma sumiu, volta). */
    void iniciar(World w, int yChao, boolean construir) {
        mundo = w;
        y = yChao;
        if (construir) construir();
        Bukkit.getScheduler().runTaskLater(pl, this::garantir, 60L);
        Bukkit.getScheduler().runTaskTimer(pl, this::garantir, 20L * 120, 20L * 120);
    }

    private void por(int x, int yy, int z, String bloco) {
        mundo.getBlockAt(x, yy, z).setBlockData(Bukkit.createBlockData(bloco), false);
    }

    private void construir() {
        for (int dx = -RAIO - 1; dx <= RAIO + 1; dx++)
            for (int dz = -RAIO - 1; dz <= RAIO + 1; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = CX + dx, z = CZ + dz;
                if (r > RAIO + 0.6) continue;
                // limpa ate 6 de altura (lampioes, blocos de luz)
                for (int k = 1; k <= 7; k++) {
                    Block b = mundo.getBlockAt(x, y + k, z);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
                if (r > RAIO - 0.4) {
                    // borda: muro baixo com lanternas nos 4 pontos
                    por(x, y + 1, z, "minecraft:polished_deepslate");
                    boolean canto = (Math.abs(dx) <= 0 && Math.abs(dz) >= RAIO - 1) || (Math.abs(dz) <= 0 && Math.abs(dx) >= RAIO - 1);
                    if (canto) {
                        por(x, y + 2, z, "minecraft:polished_deepslate_wall");
                        por(x, y + 3, z, "minecraft:lantern");
                    } else if ((dx + dz) % 2 == 0) {
                        por(x, y + 2, z, "minecraft:polished_deepslate_slab");
                    }
                    continue;
                }
                por(x, y + 1, z, r < 1.6 ? "minecraft:copper_block" : (r > RAIO - 1.6 ? "minecraft:terracotta" : "minecraft:brown_terracotta"));
            }
        // "lampada do ninho" no centro: calor para os ovos
        por(CX, y + 2, CZ, "minecraft:waxed_cut_copper");
        por(CX, y + 3, CZ, "minecraft:waxed_cut_copper");
        por(CX, y + 4, CZ, "minecraft:shroomlight");
        por(CX, y + 5, CZ, "minecraft:tinted_glass");
        por(CX + 1, y + 4, CZ, "minecraft:lightning_rod[facing=east]");
        por(CX - 1, y + 4, CZ, "minecraft:lightning_rod[facing=west]");
        por(CX, y + 4, CZ + 1, "minecraft:lightning_rod[facing=south]");
        por(CX, y + 4, CZ - 1, "minecraft:lightning_rod[facing=north]");
        // ninhos de feno onde ficam as chocadeiras
        for (int[] p : LUGARES) por(p[0], y + 1, p[1], "minecraft:hay_block");
        // entrada (degrau) virada para o centro da praca (nordeste)
        por(CX + RAIO, y + 1, CZ - 1, "minecraft:polished_deepslate_stairs[facing=west]");
        por(CX + RAIO, y + 1, CZ, "minecraft:polished_deepslate_stairs[facing=west]");
        por(CX + RAIO, y + 1, CZ + 1, "minecraft:polished_deepslate_stairs[facing=west]");
        por(CX - 1, y + 1, CZ - RAIO, "minecraft:polished_deepslate_stairs[facing=south]");
        por(CX, y + 1, CZ - RAIO, "minecraft:polished_deepslate_stairs[facing=south]");
        por(CX + 1, y + 1, CZ - RAIO, "minecraft:polished_deepslate_stairs[facing=south]");
        // letreiro
        for (Entity e : mundo.getNearbyEntities(new Location(mundo, CX, y + 7, CZ), 3, 4, 3))
            if (e.getPersistentDataContainer().has(kLetreiro)) e.remove();
        mundo.spawn(new Location(mundo, CX + 0.5, y + 6.6, CZ + 0.5), TextDisplay.class, t -> {
            t.text(Masmorras.txt("§6§lCHOCADEIRAS\n§fClique numa chocadeira livre com o seu §eovo de pet§f.\n§7Ele choca em 20 minutos para quem botou o ovo.\n§8(ovos saem dos baús das masmorras e dos kits VIP)"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
            t.setPersistent(true);
            t.getPersistentDataContainer().set(kLetreiro, PersistentDataType.BYTE, (byte) 1);
            org.bukkit.util.Transformation tr = t.getTransformation();
            tr.getScale().set(1.1f);
            t.setTransformation(tr);
        });
        pl.getLogger().info("Lobby v3: area das Chocadeiras construida em " + CX + "," + CZ + ".");
    }

    /** Garante o bloco e os moveis de cada chocadeira (BabyPets). */
    void garantir() {
        if (mundo == null) return;
        Object ger = gerente();
        if (ger == null) return;
        int feitas = 0;
        for (int[] p : LUGARES) {
            Block b = mundo.getBlockAt(p[0], y + 2, p[1]);
            if (b.getType() != Material.IRON_TRAPDOOR) {
                BlockData d = Bukkit.createBlockData(Material.IRON_TRAPDOOR);
                if (d instanceof TrapDoor t) {
                    t.setHalf(Bisected.Half.BOTTOM);
                    t.setOpen(false);
                    t.setFacing(p[0] >= CX ? org.bukkit.block.BlockFace.WEST : org.bukkit.block.BlockFace.EAST);
                }
                b.setBlockData(d, false);
            }
            if (!ehChocadeira(ger, b)) {
                float yaw = (float) Math.toDegrees(Math.atan2(-(CX - p[0]), CZ - p[1]));
                criarMoveis(ger, b.getLocation(), yaw);
                feitas++;
            }
        }
        if (feitas > 0) pl.getLogger().info("Chocadeiras do lobby: " + feitas + " montada(s).");
    }

    boolean ehDoNinho(Block b) {
        if (mundo == null || !b.getWorld().equals(mundo)) return false;
        for (int[] p : LUGARES) if (b.getX() == p[0] && b.getZ() == p[1] && b.getY() == y + 2) return true;
        return false;
    }

    /** Ninguem quebra as chocadeiras publicas (nem o BabyPets devolve o item). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void quebrou(BlockBreakEvent e) {
        if (ehDoNinho(e.getBlock())) e.setCancelled(true);
    }

    /** Clique sem ovo numa chocadeira publica: explica como funciona (o BabyPets abre a colecao; deixamos passar). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void clicou(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || !ehDoNinho(e.getClickedBlock())) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var it = e.getPlayer().getInventory().getItemInMainHand();
        if (!it.getType().isAir()) return;
        e.getPlayer().sendActionBar(Masmorras.txt("§6Chocadeira pública: §fclique nela segurando um §eovo de pet §fpara chocar (20 min)."));
    }

    List<Location> lugares() {
        List<Location> l = new ArrayList<>();
        for (int[] p : LUGARES) l.add(new Location(mundo, p[0] + 0.5, y + 2, p[1] + 0.5));
        return l;
    }
}
