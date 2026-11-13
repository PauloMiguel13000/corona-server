package br.servidor.extras;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/** 13/11 (Paulo: "continue melhorando"): Pescaria premiada no Survival — peixe dourado (coronas) e tesouros raros. */
final class Pescaria implements Listener {
    private final Extras pl;

    Pescaria(Extras pl) { this.pl = pl; }

    @EventHandler(ignoreCancelled = true)
    public void pescou(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item it)) return;
        Player p = e.getPlayer();
        if (KitsContexto.lugar(p.getWorld()) != KitsContexto.Lugar.SURVIVAL) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int s = r.nextInt(1000);
        if (s < 30) {   // 3%: peixe dourado
            int v = 15 + r.nextInt(26);
            ItemStack peixe = new ItemStack(Material.TROPICAL_FISH);
            var m = peixe.getItemMeta();
            m.displayName(Extras.txt("§6§l✦ Peixe Dourado"));
            m.lore(List.of(Extras.txt("§7Pescado por " + p.getName())));
            peixe.setItemMeta(m);
            it.setItemStack(peixe);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
            p.sendMessage(Extras.txt("§6§l🎣 PEIXE DOURADO! §e+" + v + " coronas"));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
            p.getWorld().spawnParticle(Particle.WAX_ON, it.getLocation(), 20, 0.3, 0.3, 0.3, 0.1);
        } else if (s < 36) {   // 0,6%: livro encantado raro
            ItemStack livro = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta em = (EnchantmentStorageMeta) livro.getItemMeta();
            Enchantment[] bons = {Enchantment.MENDING, Enchantment.LUCK_OF_THE_SEA, Enchantment.LURE, Enchantment.UNBREAKING, Enchantment.FROST_WALKER, Enchantment.DEPTH_STRIDER};
            Enchantment en = bons[r.nextInt(bons.length)];
            em.addStoredEnchant(en, en.getMaxLevel(), true);
            livro.setItemMeta(em);
            it.setItemStack(livro);
            p.sendMessage(Extras.txt("§b§l🎣 TESOURO DO MAR! §fVocê pescou um livro encantado raro!"));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        } else if (s < 38) {   // 0,2%: tridente
            it.setItemStack(new ItemStack(Material.TRIDENT));
            Bukkit.broadcast(Extras.txt("§b🎣 §f" + p.getName() + " §7pescou um §b§lTRIDENTE§7 no Survival!"));
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.2f);
        }
    }
}
