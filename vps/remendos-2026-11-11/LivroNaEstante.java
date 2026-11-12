package br.servidor.hotel;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/** Os livros da biblioteca ficam na estante, meio puxados para fora, em vez de soltos no chao. */
final class LivroNaEstante {
    private LivroNaEstante() {}

    /** distancia do centro do livro ate a face da estante (a caixa do item tem 0,25: mais perto que isso o jogo empurra o item) */
    private static final double FORA = 0.14;
    private static final double ALTURA = 1.3;

    static Item soltar(World w, Location chao, ItemStack item, Consumer<? super Item> ajuste) {
        Location l = chao;
        try { l = naEstante(w, chao); } catch (Throwable t) { l = chao; }
        return w.dropItem(l, item, ajuste);
    }

    static Location naEstante(World w, Location chao) {
        int x = chao.getBlockX(), y = chao.getBlockY(), z = chao.getBlockZ();
        double h = y + ALTURA;
        if (estante(w, x + 1, y + 1, z)) return new Location(w, x + 1 - FORA, h, z + 0.5);
        if (estante(w, x - 1, y + 1, z)) return new Location(w, x + FORA, h, z + 0.5);
        if (estante(w, x, y + 1, z + 1)) return new Location(w, x + 0.5, h, z + 1 - FORA);
        if (estante(w, x, y + 1, z - 1)) return new Location(w, x + 0.5, h, z + FORA);
        return chao;
    }

    private static boolean estante(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType() == Material.BOOKSHELF;
    }
}
