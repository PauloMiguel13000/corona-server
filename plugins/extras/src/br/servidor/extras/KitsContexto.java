package br.servidor.extras;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/**
 * 13/11 (Paulo: "crie kits de acordo se for masmorra, survival ou mina"): /kits abre os kits do lugar onde o jogador esta.
 * Survival (mundo normal, nether, end, terrenos) e Masmorras tem kits proprios; a Prisao (mina) tem os dela no plugin Mineracao.
 */
final class KitsContexto implements Listener {

    enum Lugar { SURVIVAL, MASMORRA, OUTRO }

    record Kit(String id, Lugar lugar, String nome, Material icone, long horas, String desc) {}

    static final Kit[] KITS = {
            new Kit("sobrevivente", Lugar.SURVIVAL, "§a§lKit Sobrevivente", Material.STONE_SWORD, 24, "Ferramentas de pedra, comida, tochas e cama"),
            new Kit("construtor", Lugar.SURVIVAL, "§6§lKit Construtor", Material.OAK_PLANKS, 24, "Tábuas, pedra, vidro, escadas, baús, fornalha"),
            new Kit("fazendeiro", Lugar.SURVIVAL, "§2§lKit Fazendeiro", Material.IRON_HOE, 24, "Enxada, sementes, cenoura, batata, farinha de osso e balde d'água"),
            new Kit("minerador", Lugar.SURVIVAL, "§7§lKit Minerador", Material.IRON_PICKAXE, 48, "Picareta de ferro (Eficiência II), tochas e comida"),
            new Kit("aventureiro", Lugar.MASMORRA, "§c§lKit Aventureiro", Material.IRON_SWORD, 24, "Espada e arco de ferro/encantados, flechas, escudo e cota de malha"),
            new Kit("alquimista", Lugar.MASMORRA, "§d§lKit Alquimista", Material.SPLASH_POTION, 12, "Poções de cura, regeneração e força + maçãs douradas"),
            new Kit("explorador", Lugar.MASMORRA, "§e§lKit Explorador", Material.TORCH, 12, "Tochas, cenouras douradas, balde de leite e bússola"),
            // 20/11 (Paulo: "loja de XP e runas abrem por um item no kit geral"): vale no Survival e nas Masmorras
            new Kit("geral", Lugar.OUTRO, "§b§lKit Geral", Material.AMETHYST_SHARD, 1, "Itens que abrem a Loja de XP, as Runas da classe e os Títulos"),
    };

    /** Itens do Kit Geral: segurar e clicar (botao direito / tocar e segurar no celular) abre o menu. */
    static final NamespacedKey K_MENU = new NamespacedKey("extras", "item_menu");

    static ItemStack itemMenu(Material m, String nome, String cmd, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.displayName(Extras.txt(nome));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Extras.txt(s));
        l.add(Extras.txt("§a▶ Segure e clique para abrir"));
        im.lore(l);
        im.setEnchantmentGlintOverride(true);
        im.getPersistentDataContainer().set(K_MENU, PersistentDataType.STRING, cmd);
        i.setItemMeta(im);
        return i;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void usarItemMenu(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || e.getItem() == null || !e.getItem().hasItemMeta()) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        String cmd = e.getItem().getItemMeta().getPersistentDataContainer().get(K_MENU, PersistentDataType.STRING);
        if (cmd == null) return;
        e.setCancelled(true);
        e.getPlayer().performCommand(cmd);
    }

    static final Set<String> SURVIVAL = Set.of("world", "world_nether", "world_the_end");   // plots agora sao da Prisao

    private final Extras pl;

    KitsContexto(Extras pl) { this.pl = pl; }

    static Lugar lugar(World w) {
        String n = w.getName();
        if (SURVIVAL.contains(n)) return Lugar.SURVIVAL;
        if (n.startsWith("masmorras")) return Lugar.MASMORRA;
        return Lugar.OUTRO;
    }

    static final class Tela implements InventoryHolder {
        final List<Kit> kits = new ArrayList<>();
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private NamespacedKey chave(Kit k) { return new NamespacedKey(pl, "kitctx_" + k.id); }

    private long falta(Player p, Kit k) {
        Long ult = p.getPersistentDataContainer().get(chave(k), PersistentDataType.LONG);
        return ult == null ? 0 : Math.max(0, ult + k.horas * 3_600_000L - System.currentTimeMillis());
    }

    private static String tempo(long ms) {
        long m = ms / 60_000L;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "min" : Math.max(1, m) + " min";
    }

    void abrir(Player p) {
        Lugar l = Cosmeticos.noSpawn(p.getLocation()) ? Lugar.OUTRO : lugar(p.getWorld());   // 14/11: a praca do spawn e lobby
        if (l == Lugar.OUTRO) {
            p.sendMessage(Extras.txt("§6§lKITS §8» §7Os kits são do §aSurvival §7e das §cMasmorras§7 (a §6Prisão §7tem os dela: §e/minas§7). Entre em um deles e use §e/kits§7."));
            return;
        }
        Tela h = new Tela();
        Inventory inv = Bukkit.createInventory(h, 27, Extras.txt(l == Lugar.SURVIVAL ? "§2§lKits do Survival" : "§4§lKits das Masmorras"));
        h.inv = inv;
        ItemStack vidro = new ItemStack(l == Lugar.SURVIVAL ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        ItemMeta vm = vidro.getItemMeta(); vm.displayName(Extras.txt(" ")); vidro.setItemMeta(vm);
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        int slot = 10;
        for (Kit k : KITS) {
            if (k.lugar != l && !k.id.equals("geral")) continue;
            long f = falta(p, k);
            ItemStack it = new ItemStack(f > 0 ? Material.GRAY_DYE : k.icone);
            ItemMeta m = it.getItemMeta();
            m.displayName(Extras.txt(k.nome));
            m.lore(List.of(Extras.txt("§7" + k.desc), Extras.txt("§7Recarga: §f" + k.horas + "h"), Extras.txt(""),
                    Extras.txt(f > 0 ? "§cDisponível em " + tempo(f) : "§a§l▶ Clique para pegar")));
            it.setItemMeta(m);
            inv.setItem(slot, it);
            h.kits.add(k);
            slot += 2;
        }
        ItemStack vip = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = vip.getItemMeta();
        m.displayName(Extras.txt("§b§lKit VIP do dia"));
        m.lore(List.of(Extras.txt("§7Para VIPs: kit diário + coronas."), Extras.txt("§a▶ Clique (ou §e/vip kit§a)")));
        vip.setItemMeta(m);
        inv.setItem(22, vip);
        ItemStack sem = new ItemStack(Material.ENDER_CHEST);
        ItemMeta sm = sem.getItemMeta();
        sm.displayName(Extras.txt("§d§lKit VIP da semana"));
        sm.lore(List.of(Extras.txt("§7Para VIPs: kit semanal do seu nível."), Extras.txt("§a▶ Clique (ou §e/vip semanal§a)")));
        sem.setItemMeta(sm);
        inv.setItem(24, sem);
        ItemStack mais = new ItemStack(Material.CHEST);
        ItemMeta mm = mais.getItemMeta();
        mm.displayName(Extras.txt("§e§lOutros kits"));
        mm.lore(List.of(Extras.txt("§7Kits do servidor (§e/kit§7)."), Extras.txt("§a▶ Clique para abrir")));
        mais.setItemMeta(mm);
        inv.setItem(26, mais);
        // 20/11: atalhos direto para a Loja de XP e as Runas
        inv.setItem(0, itemMenu(Material.EMERALD, "§a§lLoja de XP", "xp loja", "§7Troque o seu XP por VIP, coronas e itens."));
        inv.setItem(8, itemMenu(Material.AMETHYST_SHARD, "§d§lRunas da Classe", "runa", "§7Suba o nível da sua classe (1 a 60)."));
        inv.setItem(4, itemMenu(Material.NAME_TAG, "§e§lTítulos", "titulo", "§7Escolha o título que aparece no seu nome."));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void comando(PlayerCommandPreprocessEvent e) {
        if (!pl.getConfig().getBoolean("kits-contexto.ligado", true)) return;
        String c = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (c.contains(":")) c = c.substring(c.indexOf(':') + 1);
        if (!c.equals("kits") && !c.equals("kitlugar")) return;   // /kit continua sendo do PlayerKits2
        if (true) return;   // agora /kits e comando de verdade (plugin.yml)
        Lugar l = lugar(e.getPlayer().getWorld());
        if (l == Lugar.OUTRO) return;   // fora do survival e das masmorras: deixa o /kit de sempre
        e.setCancelled(true);
        abrir(e.getPlayer());
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Tela t)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getSlot();
        if (s == 22) { p.closeInventory(); p.performCommand("vip kit"); return; }
        if (s == 24) { p.closeInventory(); p.performCommand("vip semanal"); return; }
        if (s == 26) { p.closeInventory(); p.performCommand("kit"); return; }
        if (s == 0) { p.closeInventory(); p.performCommand("xp loja"); return; }
        if (s == 8) { p.closeInventory(); p.performCommand("runa"); return; }
        if (s == 4) { p.closeInventory(); p.performCommand("titulo"); return; }
        int i = (s - 10) / 2;
        if (s < 10 || (s - 10) % 2 != 0 || i >= t.kits.size()) return;
        Kit k = t.kits.get(i);
        p.closeInventory();
        if (!k.id.equals("geral") && (lugar(p.getWorld()) != k.lugar || Cosmeticos.noSpawn(p.getLocation()))) return;
        long f = falta(p, k);
        if (f > 0) { p.sendMessage(Extras.txt("§c" + k.nome + " §cdisponível em " + tempo(f) + ".")); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f); return; }
        p.getPersistentDataContainer().set(chave(k), PersistentDataType.LONG, System.currentTimeMillis());
        for (ItemStack it : itens(k.id))
            for (ItemStack sobra : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        p.sendMessage(Extras.txt("§a✔ Você pegou o " + k.nome + "§a!"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Tela) e.setCancelled(true);
    }

    private static ItemStack it(Material m, int q) { return new ItemStack(m, q); }

    private static ItemStack nomeado(Material m, String nome, Object... enc) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.displayName(Extras.txt(nome));
        i.setItemMeta(im);
        for (int k = 0; k + 1 < enc.length; k += 2) i.addUnsafeEnchantment((Enchantment) enc[k], (Integer) enc[k + 1]);
        return i;
    }

    private static ItemStack pocao(Material m, PotionType t, int q) {
        ItemStack i = new ItemStack(m, q);
        PotionMeta pm = (PotionMeta) i.getItemMeta();
        pm.setBasePotionType(t);
        i.setItemMeta(pm);
        return i;
    }

    static List<ItemStack> itens(String id) {
        return switch (id) {
            case "sobrevivente" -> List.of(nomeado(Material.STONE_SWORD, "§aEspada do Sobrevivente"), it(Material.STONE_PICKAXE, 1), it(Material.STONE_AXE, 1),
                    it(Material.STONE_SHOVEL, 1), it(Material.BREAD, 16), it(Material.COOKED_BEEF, 8), it(Material.TORCH, 32), it(Material.RED_BED, 1), it(Material.OAK_LOG, 16));
            case "construtor" -> List.of(it(Material.OAK_PLANKS, 64), it(Material.COBBLESTONE, 64), it(Material.STONE_BRICKS, 64), it(Material.GLASS, 32),
                    it(Material.OAK_STAIRS, 16), it(Material.CHEST, 2), it(Material.CRAFTING_TABLE, 1), it(Material.FURNACE, 1), it(Material.LANTERN, 8));
            case "fazendeiro" -> List.of(it(Material.IRON_HOE, 1), it(Material.WHEAT_SEEDS, 32), it(Material.CARROT, 8), it(Material.POTATO, 8),
                    it(Material.BONE_MEAL, 32), it(Material.WATER_BUCKET, 1), it(Material.COMPOSTER, 1));
            case "minerador" -> List.of(nomeado(Material.IRON_PICKAXE, "§7Picareta do Minerador", Enchantment.EFFICIENCY, 2, Enchantment.UNBREAKING, 1),
                    it(Material.TORCH, 64), it(Material.COOKED_PORKCHOP, 16), it(Material.WATER_BUCKET, 1));
            case "aventureiro" -> List.of(nomeado(Material.IRON_SWORD, "§cLâmina do Aventureiro", Enchantment.SHARPNESS, 1),
                    nomeado(Material.BOW, "§cArco do Aventureiro", Enchantment.POWER, 1), it(Material.ARROW, 48), it(Material.SHIELD, 1),
                    it(Material.CHAINMAIL_HELMET, 1), it(Material.CHAINMAIL_CHESTPLATE, 1), it(Material.CHAINMAIL_LEGGINGS, 1), it(Material.CHAINMAIL_BOOTS, 1),
                    it(Material.COOKED_BEEF, 12));
            case "alquimista" -> List.of(pocao(Material.SPLASH_POTION, PotionType.HEALING, 3), pocao(Material.POTION, PotionType.REGENERATION, 1),
                    pocao(Material.POTION, PotionType.STRENGTH, 1), it(Material.GOLDEN_APPLE, 2));
            case "geral" -> List.of(itemMenu(Material.EMERALD, "§a§lLoja de XP", "xp loja", "§7Troque o seu XP por VIP, coronas e itens."),
                    itemMenu(Material.AMETHYST_SHARD, "§d§lRunas da Classe", "runa", "§7Suba o nível da sua classe (1 a 60)."),
                    itemMenu(Material.NAME_TAG, "§e§lTítulos", "titulo", "§7Escolha o título que aparece no seu nome."));
            case "explorador" -> List.of(it(Material.TORCH, 48), it(Material.GOLDEN_CARROT, 16), it(Material.MILK_BUCKET, 1), it(Material.COMPASS, 1));
            default -> List.of();
        };
    }
}
