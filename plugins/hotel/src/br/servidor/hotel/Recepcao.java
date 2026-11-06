package br.servidor.hotel;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A recepcao do hotel (sala de espera antes da partida). Fica longe das faixas onde as partidas sao montadas. */
final class Recepcao {
    static final int X0 = -30, X1 = -14, Z0 = -8, Z1 = 8, Y = Planta.Y, ALT = 6;

    static Location spawn(World w) {
        return new Location(w, -17.5, Y, 0.5, 90f, 0f);
    }

    static Location placa(World w) {
        return new Location(w, -27.5, Y + 2.6, 0.5);
    }

    static void construir(World w) {
        for (int x = X0; x <= X1; x++)
            for (int z = Z0; z <= Z1; z++) {
                w.getChunkAt(x >> 4, z >> 4).load(true);
                boolean parede = x == X0 || x == X1 || z == Z0 || z == Z1;
                set(w, x, Y - 2, z, Material.STONE);
                set(w, x, Y - 1, z, (x + z) % 2 == 0 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                set(w, x, Y + ALT, z, Material.DARK_OAK_PLANKS);
                set(w, x, Y + ALT + 1, z, Material.STONE);
                for (int y = Y; y < Y + ALT; y++) {
                    Material m = !parede ? Material.AIR : y == Y ? Material.DARK_OAK_PLANKS : y == Y + 1 ? Material.STRIPPED_DARK_OAK_WOOD
                            : y == Y + ALT - 1 ? Material.DARK_OAK_PLANKS : Material.RED_TERRACOTTA;
                    set(w, x, y, z, m);
                }
            }
        // tapete vermelho, balcao, sinos e lustres
        for (int x = X0 + 2; x <= X1 - 2; x++) set(w, x, Y, 0, Material.RED_CARPET);
        for (int z = -3; z <= 3; z++) set(w, X0 + 3, Y, z, Material.DARK_OAK_PLANKS);
        set(w, X0 + 3, Y + 1, 0, Material.BELL);
        for (int x : new int[] {X0 + 6, X0 + 11})
            for (int z : new int[] {-4, 4}) {
                set(w, x, Y + ALT - 1, z, Material.IRON_CHAIN);
                set(w, x, Y + ALT - 2, z, Material.LANTERN);
            }
        // elevador (a porta de saida da recepcao e de ferro: o jogo comeca la dentro)
        set(w, X1, Y, 0, Material.IRON_BLOCK);
        set(w, X1, Y + 1, 0, Material.IRON_BLOCK);
        for (int z = -6; z <= 6; z += 3) {
            set(w, X0 + 1, Y, z, Material.POTTED_DEAD_BUSH);
        }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (m == Material.LANTERN) b.setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:lantern[hanging=true]"), false);
        else if (m == Material.BELL) b.setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:bell[attachment=floor,facing=east]"), false);
        else b.setType(m, false);
    }

    private Recepcao() {}
}
