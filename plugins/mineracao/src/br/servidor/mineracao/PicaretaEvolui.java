package br.servidor.mineracao;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 14/11 (Paulo: "melhore muito"): na Prisao a PICARETA EVOLUI — conta os blocos quebrados com ela e sobe de nivel
 * (300, 1000, 2500, 5000, 10000, 20000 blocos). Cada nivel da um encantamento de presente (Eficiencia, Inquebravel,
 * Fortuna) e o nivel aparece na descricao do item.
 */
final class PicaretaEvolui {
    static final int[] NIVEIS = {300, 1000, 2500, 5000, 10000, 20000};
    private final NamespacedKey kBlocos;

    PicaretaEvolui(Mineracao pl) { kBlocos = new NamespacedKey(pl, "pic_blocos"); }

    static int nivel(int blocos) {
        int n = 0;
        for (int x : NIVEIS) if (blocos >= x) n++;
        return n;
    }

    void quebrou(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!it.getType().name().endsWith("_PICKAXE")) return;
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        int b = m.getPersistentDataContainer().getOrDefault(kBlocos, PersistentDataType.INTEGER, 0) + 1;
        m.getPersistentDataContainer().set(kBlocos, PersistentDataType.INTEGER, b);
        int antes = nivel(b - 1), agora = nivel(b);
        if (agora > antes) {
            Enchantment en = switch (agora) {
                case 2, 6 -> Enchantment.UNBREAKING;
                case 4 -> Enchantment.FORTUNE;
                default -> Enchantment.EFFICIENCY;
            };
            int lv = Math.min(10, m.getEnchantLevel(en) + 1);
            m.addEnchant(en, lv, true);
            p.sendMessage(Mineracao.txt("§6§l⛏ SUA PICARETA EVOLUIU! §fNível §e" + agora + " §7→ ganhou §b" + nomeEnc(en) + " " + lv));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
            p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 80, 0.6, 0.6, 0.6, 1);
        }
        if (agora > antes || b % 50 == 0 || b == 1) lore(m, b, agora);
        it.setItemMeta(m);
    }

    private static String nomeEnc(Enchantment e) {
        if (e.equals(Enchantment.UNBREAKING)) return "Inquebrável";
        if (e.equals(Enchantment.FORTUNE)) return "Fortuna";
        return "Eficiência";
    }

    private static void lore(ItemMeta m, int b, int nivel) {
        List<Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        l.removeIf(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c).startsWith("⛏ Picareta nível"));
        String prox = nivel < NIVEIS.length ? " §8(próximo: " + NIVEIS[nivel] + ")" : " §6§lMÁXIMO";
        l.add(Mineracao.txt("§6⛏ Picareta nível " + nivel + " §7— " + b + " blocos" + prox));
        m.lore(l);
    }
}
