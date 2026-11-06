package br.servidor.extras;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/** Kit Corona Basico: armadura de couro personalizada, ferramentas de pedra, pa de terreno e comida. */
final class KitBasico {

    private KitBasico() {}

    static final Color COR = Color.fromRGB(0xE8A33C);

    private static ItemStack item(Material m, int qtd, String nome, String... lore) {
        ItemStack it = new ItemStack(m, qtd);
        ItemMeta meta = it.getItemMeta();
        if (nome != null) meta.displayName(Extras.txt(nome));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Extras.txt(s));
        if (!l.isEmpty()) meta.lore(l);
        if (meta instanceof LeatherArmorMeta lm) lm.setColor(COR);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack armadura(Material m, String nome) {
        ItemStack it = item(m, 1, "§6§l" + nome + " Corona", "§7Kit Corona Básico");
        it.addUnsafeEnchantment(Enchantment.UNBREAKING, 2);
        return it;
    }

    static List<ItemStack> itens() {
        List<ItemStack> l = new ArrayList<>();
        l.add(armadura(Material.LEATHER_HELMET, "Capacete"));
        l.add(armadura(Material.LEATHER_CHESTPLATE, "Peitoral"));
        l.add(armadura(Material.LEATHER_LEGGINGS, "Calça"));
        l.add(armadura(Material.LEATHER_BOOTS, "Botas"));
        l.add(item(Material.STONE_SWORD, 1, "§fEspada Corona", "§7Kit Corona Básico"));
        l.add(item(Material.STONE_PICKAXE, 1, "§fPicareta Corona", "§7Kit Corona Básico"));
        l.add(item(Material.STONE_AXE, 1, "§fMachado Corona", "§7Kit Corona Básico"));
        l.add(item(Material.STONE_SHOVEL, 1, "§fPá Corona", "§7Kit Corona Básico"));
        l.add(item(Material.GOLDEN_SHOVEL, 1, "§e§lPá de Terreno", "§7Botão direito em 2 cantos opostos", "§7para proteger o seu terreno.", "§7Seus terrenos: §e/claimslist"));
        l.add(new ItemStack(Material.BREAD, 16));
        l.add(new ItemStack(Material.COOKED_BEEF, 8));
        l.add(new ItemStack(Material.TORCH, 16));
        return l;
    }

    /** Veste a armadura (se o lugar estiver vazio) e poe o resto no inventario; o que nao couber cai no chao. */
    static void dar(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : itens()) {
            String t = it.getType().name();
            if (t.endsWith("_HELMET") && inv.getHelmet() == null) inv.setHelmet(it);
            else if (t.endsWith("_CHESTPLATE") && inv.getChestplate() == null) inv.setChestplate(it);
            else if (t.endsWith("_LEGGINGS") && inv.getLeggings() == null) inv.setLeggings(it);
            else if (t.endsWith("_BOOTS") && inv.getBoots() == null) inv.setBoots(it);
            else for (ItemStack sobra : inv.addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        }
    }
}
