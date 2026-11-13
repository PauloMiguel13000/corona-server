package br.servidor.extras;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** 13/11 (Paulo: "continue melhorando"): /roleta — uma girada gratis por dia, com animacao, valendo coronas e XP. */
final class Roleta implements Listener {
    record Premio(String nome, Material icone, int coronas, int niveis, int peso) {}

    static final List<Premio> PREMIOS = List.of(
            new Premio("§f25 coronas", Material.GOLD_NUGGET, 25, 0, 30),
            new Premio("§e50 coronas", Material.GOLD_INGOT, 50, 0, 25),
            new Premio("§a5 níveis de XP", Material.EXPERIENCE_BOTTLE, 0, 5, 15),
            new Premio("§6100 coronas", Material.GOLD_BLOCK, 100, 0, 15),
            new Premio("§b10 níveis de XP", Material.ENCHANTING_TABLE, 0, 10, 7),
            new Premio("§d250 coronas", Material.AMETHYST_CLUSTER, 250, 0, 6),
            new Premio("§c§lJACKPOT: 1000 coronas", Material.NETHER_STAR, 1000, 0, 2));

    private final Extras pl;
    private final NamespacedKey kDia;

    Roleta(Extras pl) {
        this.pl = pl;
        kDia = new NamespacedKey(pl, "roleta_dia");
    }

    static final class Tela implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static Premio sortear() {
        int tot = PREMIOS.stream().mapToInt(Premio::peso).sum(), r = ThreadLocalRandom.current().nextInt(tot);
        for (Premio p : PREMIOS) { r -= p.peso(); if (r < 0) return p; }
        return PREMIOS.get(0);
    }

    private static ItemStack icone(Premio p) {
        ItemStack i = new ItemStack(p.icone());
        var m = i.getItemMeta();
        m.displayName(Extras.txt(p.nome()));
        i.setItemMeta(m);
        return i;
    }

    void girar(Player p) {
        long hoje = LoginDiario.hojeEpoch();
        Long ult = p.getPersistentDataContainer().get(kDia, PersistentDataType.LONG);
        if (ult != null && ult == hoje) {
            p.sendMessage(Extras.txt("§6🎰 §7Você já girou a roleta hoje. Volte amanhã!"));
            return;
        }
        p.getPersistentDataContainer().set(kDia, PersistentDataType.LONG, hoje);
        Premio fim = sortear();
        Tela h = new Tela();
        Inventory inv = Bukkit.createInventory(h, 27, Extras.txt("§6§l🎰 Roleta do Dia"));
        h.inv = inv;
        ItemStack vidro = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        var vm = vidro.getItemMeta(); vm.displayName(Extras.txt(" ")); vidro.setItemMeta(vm);
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        ItemStack seta = new ItemStack(Material.HOPPER);
        var sm = seta.getItemMeta(); sm.displayName(Extras.txt("§e▼")); seta.setItemMeta(sm);
        inv.setItem(4, seta);
        p.openInventory(inv);
        final int[] passo = {0};
        int total = 28;
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            passo[0]++;
            for (int s = 9; s <= 17; s++) inv.setItem(s, icone(passo[0] >= total && s == 13 ? fim : PREMIOS.get(ThreadLocalRandom.current().nextInt(PREMIOS.size()))));
            if (p.isOnline()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.8f + passo[0] * 0.04f);
            if (passo[0] >= total) {
                t.cancel();
                if (fim.coronas() > 0) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + fim.coronas());
                if (fim.niveis() > 0) p.giveExpLevels(fim.niveis());
                p.sendMessage(Extras.txt("§6🎰 Roleta do Dia: §fvocê ganhou " + fim.nome() + "§f!"));
                p.playSound(p.getLocation(), fim.coronas() >= 250 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                if (fim.coronas() >= 1000) Bukkit.broadcast(Extras.txt("§6🎰 §e" + p.getName() + " §7tirou o §c§lJACKPOT §7na Roleta do Dia: §61000 coronas§7! §e/roleta"));
            }
        }, 2L, 3L);
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Tela) e.setCancelled(true);
    }
}
