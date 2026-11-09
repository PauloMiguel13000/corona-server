package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Vendedor de Classes (lobby do Mundo das Masmorras): quem nao tem o VIP compra a classe trancada por coronas, bem caro.
 * As classes, o VIP exigido e o preco vem do config do Poderes (classes.travas / classes.preco); a compra fica no
 * jogador (PDC poderes:comprada_<classe>), que o Poderes le.
 */
final class VendaClasses implements Listener {

    /** id, nome, cor, icone, vip exigido padrao, preco padrao */
    private static final Object[][] CLASSES = {
            {"lutador", "Lutador", "§4", Material.LEATHER, 3, 90_000L},
            {"saiyajin", "Saiyajin", "§e", Material.BLAZE_POWDER, 3, 120_000L},
            {"cipo", "Cipó", "§2", Material.VINE, 1, 50_000L},
            {"tridente", "Tridente", "§3", Material.TRIDENT, 1, 50_000L}};
    private static final String[] NIVEL_VIP = {"", "VIP", "VIP+", "MVP", "LENDA"};

    private final Masmorras pl;
    private final NamespacedKey kNpc, kItem;

    VendaClasses(Masmorras pl) {
        this.pl = pl;
        kNpc = new NamespacedKey(pl, "vendedor_classes");
        kItem = new NamespacedKey(pl, "venda_classe");
    }

    private org.bukkit.configuration.file.FileConfiguration poderes() {
        org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Poderes");
        return p == null ? null : p.getConfig();
    }

    private int vipExigido(Object[] c) {
        var cfg = poderes();
        return cfg == null ? (int) c[4] : cfg.getInt("classes.travas." + c[0], (int) c[4]);
    }

    private long preco(Object[] c) {
        var cfg = poderes();
        return cfg == null ? (long) c[5] : cfg.getLong("classes.preco." + c[0], (long) c[5]);
    }

    private static boolean comprou(Player p, String id) {
        return p.getPersistentDataContainer().has(new NamespacedKey("poderes", "comprada_" + id), PersistentDataType.BYTE);
    }

    void garantirNpc(org.bukkit.World mundo, int y, int x, int z) {
        if (!mundo.isChunkLoaded(x >> 4, z >> 4)) return;
        Location l = new Location(mundo, x + 0.5, y + 2, z + 0.5, -90f, 0f);
        for (Entity e : mundo.getNearbyEntities(l, 3, 3, 3)) if (e.getPersistentDataContainer().has(kNpc)) return;
        Villager v = pl.loja.npc(l, "§6§lVendedor de Classes", null, Loja.profissao(0), kNpc, 1);
        Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft("mason"));
        if (prof != null) v.setProfession(prof);
    }

    static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void abrir(Player p) {
        Menu h = new Menu();
        h.inv = Bukkit.createInventory(h, 27, Masmorras.txt("§6§lVendedor de Classes"));
        ItemStack fundo = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = fundo.getItemMeta();
        fm.customName(Masmorras.txt(" "));
        fundo.setItemMeta(fm);
        for (int i = 0; i < 27; i++) h.inv.setItem(i, fundo);
        int[] slots = {10, 12, 14, 16};
        long saldo = pl.coronas.saldo(p);
        int vip = Coronas.nivelVip(p);
        for (int i = 0; i < CLASSES.length; i++) {
            Object[] c = CLASSES[i];
            ItemStack it = new ItemStack((Material) c[3]);
            ItemMeta m = it.getItemMeta();
            m.displayName(Masmorras.txt(c[2] + "§l" + c[1]));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            int exige = vipExigido(c);
            long preco = preco(c);
            lore.add(Masmorras.txt("§7Liberada para §f" + (exige >= 3 ? "MVP e LENDA" : exige == 2 ? "VIP+, MVP e LENDA" : "qualquer VIP")));
            lore.add(Masmorras.txt(""));
            if (comprou(p, (String) c[0])) {
                lore.add(Masmorras.txt("§a✔ Você já comprou esta classe"));
                m.setEnchantmentGlintOverride(true);
            } else if (vip >= exige) {
                lore.add(Masmorras.txt("§a✔ Seu " + NIVEL_VIP[Math.min(vip, 4)] + " já libera esta classe"));
                lore.add(Masmorras.txt("§7(não precisa comprar)"));
            } else if (preco <= 0) lore.add(Masmorras.txt("§cNão está à venda"));
            else {
                lore.add(Masmorras.txt("§7Preço: §6" + Coronas.fmt(preco) + " coronas"));
                lore.add(Masmorras.txt("§7Seu saldo: " + (saldo >= preco ? "§a" : "§c") + Coronas.fmt(saldo)));
                lore.add(Masmorras.txt(""));
                lore.add(Masmorras.txt(saldo >= preco ? "§eClique para comprar (para sempre)" : "§cFaltam " + Coronas.fmt(preco - saldo) + " coronas"));
            }
            m.lore(lore);
            m.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, (String) c[0]);
            it.setItemMeta(m);
            h.inv.setItem(slots[i], it);
        }
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Masmorras.txt("§e§lComo funciona"));
        im.lore(List.of(Masmorras.txt("§7Essas classes são dos VIPs."), Masmorras.txt("§7Quem não tem VIP compra aqui com"), Masmorras.txt("§7coronas das masmorras (vale pra sempre)."),
                Masmorras.txt(""), Masmorras.txt("§7Virar VIP: §e/vip")));
        info.setItemMeta(im);
        h.inv.setItem(22, info);
        p.openInventory(h.inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(kNpc)) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        abrir(e.getPlayer());
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof Menu)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING);
        if (id == null) return;
        Object[] c = null;
        for (Object[] x : CLASSES) if (x[0].equals(id)) c = x;
        if (c == null) return;
        if (comprou(p, id) || Coronas.nivelVip(p) >= vipExigido(c)) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Essa classe já está liberada para você. Use §e/classe§7."));
            p.closeInventory();
            return;
        }
        long preco = preco(c);
        if (preco <= 0) return;
        if (!pl.coronas.tirar(p, preco)) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§cCoronas insuficientes. §7Preço: §6" + Coronas.fmt(preco) + "§7, saldo: §6" + Coronas.fmt(pl.coronas.saldo(p))));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        p.getPersistentDataContainer().set(new NamespacedKey("poderes", "comprada_" + id), PersistentDataType.BYTE, (byte) 1);
        p.closeInventory();
        p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§aVocê comprou a classe " + c[2] + "§l" + c[1] + "§a para sempre! §7Escolha em §e/classe§7. Saldo: §6" + Coronas.fmt(pl.coronas.saldo(p))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.broadcast(Masmorras.txt("§6§l✦ §e" + p.getName() + " §7comprou a classe " + c[2] + "§l" + c[1] + "§7!"));
        pl.getLogger().info(p.getName() + " comprou a classe " + id + " por " + preco + " coronas.");
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoTransforma(EntityTransformEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kNpc)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoApanha(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kNpc)) e.setCancelled(true);
    }

    static String minusculo(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
