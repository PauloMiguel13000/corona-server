package br.servidor.extras;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

/**
 * 13/11 (Paulo: "melhore o survival"): /reparar — o Ferreiro conserta o item da mao em troca de coronas.
 * Preco pelo material e pelo quanto esta gasto. Primeiro mostra o preco; "/reparar confirmar" paga e conserta.
 * So no Survival (mundo normal, Nether e Fim).
 */
final class Reparar {
    private final Extras pl;
    private final NamespacedKey kCoronas = new NamespacedKey("masmorras", "coronas");
    private final Map<UUID, Long> pedido = new HashMap<>();

    Reparar(Extras pl) { this.pl = pl; }

    private static boolean survival(Player p) {
        String w = p.getWorld().getName();
        return w.equals("world") || w.equals("world_nether") || w.equals("world_the_end");
    }

    private long saldo(Player p) {
        Long v = p.getPersistentDataContainer().get(kCoronas, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    static int base(ItemStack it) {
        String n = it.getType().name();
        if (n.startsWith("NETHERITE_")) return 300;
        if (n.startsWith("DIAMOND_")) return 180;
        if (n.equals("ELYTRA") || n.equals("TRIDENT") || n.equals("MACE")) return 250;
        if (n.startsWith("IRON_") || n.startsWith("CHAINMAIL_") || n.equals("CROSSBOW") || n.equals("SHIELD")) return 70;
        if (n.startsWith("GOLDEN_")) return 50;
        return 30;
    }

    static int preco(ItemStack it) {
        if (!(it.getItemMeta() instanceof Damageable d) || !d.hasDamage()) return 0;
        int max = it.getType().getMaxDurability();
        if (max <= 0) return 0;
        double gasto = Math.min(1.0, d.getDamage() / (double) max);
        int ench = it.getEnchantments().size();
        return Math.max(10, (int) Math.ceil(base(it) * gasto * (1 + ench * 0.1)));
    }

    boolean comando(Player p, String[] a) {
        if (!survival(p)) {
            p.sendMessage(Extras.txt("§c⚒ O Ferreiro só trabalha no Survival."));
            return true;
        }
        ItemStack it = p.getInventory().getItemInMainHand();
        int v = it.getType().isAir() ? 0 : preco(it);
        if (v <= 0) {
            p.sendMessage(Extras.txt("§7⚒ Segure na mão um item gasto (ferramenta, arma ou armadura) e use §e/reparar§7."));
            return true;
        }
        boolean conf = a.length > 0 && a[0].equalsIgnoreCase("confirmar");
        Long t = pedido.get(p.getUniqueId());
        if (!conf || t == null || System.currentTimeMillis() - t > 30_000L) {
            pedido.put(p.getUniqueId(), System.currentTimeMillis());
            p.sendMessage(Extras.txt("§6⚒ Ferreiro: §fconsertar este item custa §6" + v + " coronas §7(seu saldo: " + saldo(p) + ")"));
            p.sendMessage(Extras.txt("§7Para pagar, use §e/reparar confirmar §7em até 30 segundos."));
            return true;
        }
        pedido.remove(p.getUniqueId());
        if (saldo(p) < v) {
            p.sendMessage(Extras.txt("§c⚒ Você não tem §6" + v + " coronas§c. Saldo: §6" + saldo(p)));
            return true;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas tirar " + p.getName() + " " + v);
        if (it.getItemMeta() instanceof Damageable d) {
            d.setDamage(0);
            it.setItemMeta(d);
        }
        p.sendMessage(Extras.txt("§a⚒ Item consertado! §7(-" + v + " coronas)"));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
        return true;
    }
}
