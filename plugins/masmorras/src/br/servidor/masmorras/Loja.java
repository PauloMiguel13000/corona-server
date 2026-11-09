package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/**
 * Mercadores: villagers parados perto da entrada de cada masmorra (itens do nivel daquela parte do mapa) e o
 * Mercador Lendario no Spawn das Masmorras. Compra com coronas, num menu (nao e a troca normal do villager).
 */
final class Loja implements Listener {

    /** moedas > 0: nao e item, e Moeda de Reviver (vai para o saldo de moedas do jogador). */
    record Oferta(ItemStack item, long preco, int moedas) {}

    private final Masmorras pl;
    final NamespacedKey kMercador, kGuia;
    private final Map<Integer, List<Oferta>> catalogo = new HashMap<>();
    /** Masmorra -> villager dela (para nao procurar toda hora). */
    private final Map<Integer, UUID> mercadores = new HashMap<>();

    static final String[] NOMES = {"", "Mercador", "Mercador", "Mercador Veterano", "Mercador Veterano", "Mercador do Abismo", "Mercador Lendário"};

    Loja(Masmorras pl) {
        this.pl = pl;
        kMercador = new NamespacedKey(pl, "mercador");
        kGuia = new NamespacedKey(pl, "guia");
        montarCatalogo();
    }

    // ------------------------------------------------------------------ catalogo

    private static Enchantment enc(String id) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id));
    }

    /** Ex.: it(Material.DIAMOND_SWORD, 1, "sharpness:3", "unbreaking:2") */
    static ItemStack it(Material m, int qtd, String... encs) {
        ItemStack s = new ItemStack(m, qtd);
        for (String e : encs) {
            String[] p = e.split(":");
            Enchantment en = enc(p[0]);
            if (en != null) s.addUnsafeEnchantment(en, Integer.parseInt(p[1]));
        }
        return s;
    }

    static ItemStack pocao(PotionType t) {
        ItemStack s = new ItemStack(Material.POTION);
        if (s.getItemMeta() instanceof PotionMeta pm) {
            pm.setBasePotionType(t);
            s.setItemMeta(pm);
        }
        return s;
    }

    /** Mercadores das masmorras (nivel 1 a 5): preco base x multiplicador da config (pedido do Paulo, 05/11: bem mais caro). */
    private void add(int nivel, ItemStack item, long preco) {
        if (nivel < 6) {
            double mult = Math.max(0.1, pl.getConfig().getDouble("loja.multiplicador", 3.0));
            preco = Math.max(10, Math.round(preco * mult / 10.0) * 10);
        }
        catalogo.computeIfAbsent(nivel, k -> new ArrayList<>()).add(new Oferta(item, preco, 0));
    }

    /**
     * Cosmetico do Mercador Lendario: o melhor equipamento com o visual de um tema do pacote de recursos (espada 3D,
     * armadura com textura propria). So o visual: nao tem o poder das reliquias.
     */
    private ItemStack cosmetico(Material m, String tema, String nome, String cor, String... encs) {
        ItemStack s = it(m, 1, encs);
        ItemMeta im = s.getItemMeta();
        im.customName(Masmorras.txt(cor + "§l" + nome));
        im.lore(tema.equals("herobrine")
                ? List.of(Masmorras.txt("§5✦ Cosmético Lendário"), Masmorras.txt("§7Visual exclusivo do Corona Server"), Masmorras.txt("§8Java e Bedrock"))
                : List.of(Masmorras.txt("§5✦ Cosmético Lendário"), Masmorras.txt("§7Visual exclusivo do pacote do servidor"),
                        Masmorras.txt("§8Java e Bedrock (pacote v2)")));
        im.getPersistentDataContainer().set(new NamespacedKey(pl, "cosmetico"), PersistentDataType.STRING, tema);
        s.setItemMeta(im);
        Reliquias.visualDoPacote(s, tema, pl.getLogger());
        return s;
    }

    /** As 5 pecas de um conjunto cosmetico (para dar de presente pelo admin). */
    List<ItemStack> pecasConjunto(String tema, String nomeTema, String cor) {
        String[] arm = {"protection:4", "unbreaking:3", "mending:1"};
        return List.of(cosmetico(Material.NETHERITE_HELMET, tema, "Elmo " + nomeTema, cor, arm),
                cosmetico(Material.NETHERITE_CHESTPLATE, tema, "Peitoral " + nomeTema, cor, arm),
                cosmetico(Material.NETHERITE_LEGGINGS, tema, "Calça " + nomeTema, cor, arm),
                cosmetico(Material.NETHERITE_BOOTS, tema, "Botas " + nomeTema, cor, "protection:4", "feather_falling:4", "unbreaking:3", "mending:1"),
                cosmetico(Material.NETHERITE_SWORD, tema, "Espada " + nomeTema, cor, "sharpness:5", "looting:3", "unbreaking:3", "mending:1"));
    }

    /** Conjunto completo (elmo, peitoral, calca, botas, espada) de um tema; precos x fator. */
    private void conjunto(String tema, String nomeTema, String cor, double fator) {
        String[] arm = {"protection:4", "unbreaking:3", "mending:1"};
        add(6, cosmetico(Material.NETHERITE_HELMET, tema, "Elmo " + nomeTema, cor, arm), Math.round(90000 * fator));
        add(6, cosmetico(Material.NETHERITE_CHESTPLATE, tema, "Peitoral " + nomeTema, cor, arm), Math.round(135000 * fator));
        add(6, cosmetico(Material.NETHERITE_LEGGINGS, tema, "Calça " + nomeTema, cor, arm), Math.round(120000 * fator));
        add(6, cosmetico(Material.NETHERITE_BOOTS, tema, "Botas " + nomeTema, cor, "protection:4", "feather_falling:4", "unbreaking:3", "mending:1"),
                Math.round(85000 * fator));
        add(6, cosmetico(Material.NETHERITE_SWORD, tema, "Espada " + nomeTema, cor, "sharpness:5", "looting:3", "unbreaking:3", "mending:1"),
                Math.round(135000 * fator));
    }

    private void montarCatalogo() {
        // nivel 1
        // 08/11 (pedido do Paulo): os Mercadores vendem itens INFERIORES aos que caem nas masmorras daquele nivel
        // nivel 1 (cai: espada de pedra afiada, cota de malha, elmo de couro protegido)
        add(1, it(Material.LEATHER_HELMET, 1), 120);
        add(1, it(Material.LEATHER_CHESTPLATE, 1), 200);
        add(1, it(Material.LEATHER_LEGGINGS, 1), 170);
        add(1, it(Material.LEATHER_BOOTS, 1), 100);
        add(1, it(Material.STONE_SWORD, 1), 120);
        add(1, it(Material.STONE_AXE, 1), 120);
        add(1, it(Material.BOW, 1), 220);
        add(1, it(Material.ARROW, 16), 80);
        add(1, it(Material.SHIELD, 1), 200);
        add(1, it(Material.COOKED_BEEF, 16), 120);
        add(1, pocao(PotionType.HEALING), 150);
        add(1, it(Material.TORCH, 32), 50);
        // nivel 2 (cai: armadura de ferro protegida, espada de ferro afiacao II)
        add(2, it(Material.CHAINMAIL_HELMET, 1), 450);
        add(2, it(Material.CHAINMAIL_CHESTPLATE, 1), 700);
        add(2, it(Material.CHAINMAIL_LEGGINGS, 1), 600);
        add(2, it(Material.CHAINMAIL_BOOTS, 1), 380);
        add(2, it(Material.IRON_SWORD, 1), 550);
        add(2, it(Material.IRON_PICKAXE, 1), 450);
        add(2, it(Material.CROSSBOW, 1), 500);
        add(2, it(Material.GOLDEN_CARROT, 8), 300);
        add(2, pocao(PotionType.STRENGTH), 400);
        add(2, pocao(PotionType.SWIFTNESS), 300);
        add(2, it(Material.ENDER_PEARL, 1), 250);
        add(2, it(Material.ARROW, 48), 180);
        // nivel 3 (cai: diamante protegido, espada de diamante afiacao II)
        add(3, it(Material.IRON_HELMET, 1), 1200);
        add(3, it(Material.IRON_CHESTPLATE, 1), 1900);
        add(3, it(Material.IRON_LEGGINGS, 1), 1700);
        add(3, it(Material.IRON_BOOTS, 1), 1000);
        add(3, it(Material.IRON_SWORD, 1, "sharpness:1"), 1500);
        add(3, it(Material.IRON_PICKAXE, 1, "efficiency:1"), 1400);
        add(3, it(Material.BOW, 1, "power:1"), 1100);
        add(3, it(Material.GOLDEN_APPLE, 1), 700);
        add(3, it(Material.ENDER_PEARL, 2), 600);
        add(3, pocao(PotionType.REGENERATION), 600);
        // nivel 4 (cai: diamante protecao III, espada afiacao IV, totem)
        add(4, it(Material.DIAMOND_HELMET, 1), 2600);
        add(4, it(Material.DIAMOND_CHESTPLATE, 1), 4000);
        add(4, it(Material.DIAMOND_LEGGINGS, 1), 3600);
        add(4, it(Material.DIAMOND_BOOTS, 1), 2200);
        add(4, it(Material.DIAMOND_SWORD, 1), 3500);
        add(4, it(Material.BOW, 1, "power:2"), 2200);
        add(4, pocao(PotionType.FIRE_RESISTANCE), 800);
        add(4, it(Material.EXPERIENCE_BOTTLE, 8), 1500);
        add(4, it(Material.GOLDEN_APPLE, 2), 1300);
        // nivel 5 (cai: netherite, elytra, maca dourada encantada)
        add(5, it(Material.DIAMOND_HELMET, 1, "protection:2", "unbreaking:1"), 6000);
        add(5, it(Material.DIAMOND_CHESTPLATE, 1, "protection:2", "unbreaking:1"), 9000);
        add(5, it(Material.DIAMOND_LEGGINGS, 1, "protection:2", "unbreaking:1"), 8000);
        add(5, it(Material.DIAMOND_BOOTS, 1, "protection:2", "unbreaking:1"), 5500);
        add(5, it(Material.DIAMOND_SWORD, 1, "sharpness:3", "unbreaking:1"), 7500);
        add(5, it(Material.NETHERITE_SCRAP, 1), 9000);
        add(5, it(Material.TOTEM_OF_UNDYING, 1), 8000);
        add(5, it(Material.SHULKER_BOX, 1), 6000);
        add(5, it(Material.EXPERIENCE_BOTTLE, 24), 2500);
        // Mercador Lendario (Spawn das Masmorras): o melhor que existe, bem mais caro (05/11), com o visual do pacote.
        // 4 conjuntos cosmeticos (fileiras 1 a 4 do menu) e, na ultima coluna, os itens uteis e a Moeda de Reviver.
        conjunto("herobrine", "de Herobrine", "§4", 1.5);
        conjunto("cidadela", "do Vazio", "§5", 1.0);
        conjunto("forja", "das Chamas", "§c", 1.0);
        conjunto("gelo", "do Gelo Eterno", "§b", 1.0);
        add(6, it(Material.NETHERITE_PICKAXE, 1, "efficiency:5", "fortune:3", "unbreaking:3", "mending:1"), 120000);
        add(6, it(Material.BOW, 1, "power:5", "infinity:1", "unbreaking:3", "flame:1"), 90000);
        add(6, it(Material.TRIDENT, 1, "loyalty:3", "channeling:1", "impaling:5", "unbreaking:3"), 105000);
        add(6, it(Material.ELYTRA, 1, "unbreaking:3", "mending:1"), 120000);
        add(6, it(Material.ENCHANTED_GOLDEN_APPLE, 1), 45000);
        add(6, it(Material.TOTEM_OF_UNDYING, 1), 40000);
        add(6, it(Material.BEACON, 1), 150000);
        long moeda = Math.max(1000, pl.getConfig().getLong("loja.moeda-reviver", 40000));
        catalogo.computeIfAbsent(6, k -> new ArrayList<>()).add(new Oferta(MoedaReviver.icone(1), moeda, 1));
    }

    // ------------------------------------------------------------------ villagers

    static Villager.Profession profissao(int nivel) {
        String id = switch (nivel) {
            case 1 -> "farmer";
            case 2 -> "fletcher";
            case 3 -> "armorer";
            case 4 -> "weaponsmith";
            case 5 -> "toolsmith";
            default -> "cleric";
        };
        Villager.Profession p = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(id));
        return p != null ? p : Villager.Profession.NITWIT;
    }

    /** Villager parado, invencivel e calado (Mercador ou Guia). */
    Villager npc(Location l, String nome, String sub, Villager.Profession prof, NamespacedKey chave, int valor) {
        return l.getWorld().spawn(l, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setProfession(prof);
            v.setVillagerLevel(5);
            v.customName(Masmorras.txt(nome));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(chave, PersistentDataType.INTEGER, valor);
            if (sub != null) v.getPersistentDataContainer().set(new NamespacedKey(pl, "sub"), PersistentDataType.STRING, sub);
        });
    }

    /** Chao livre perto da entrada (2 de ar em cima de bloco solido). */
    static Location lugarPerto(World w, int x0, int y0, int z0) {
        int[][] off = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {-3, -3}, {3, -3}, {-3, 3}, {4, 1}, {-4, 1}};
        for (int[] o : off)
            for (int dy = 3; dy >= -4; dy--) {
                Block b = w.getBlockAt(x0 + o[0], y0 + dy, z0 + o[1]);
                if (b.getType().isSolid() && !b.getType().name().contains("LEAVES") && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, 2, 0).isPassable()
                        && !b.getRelative(0, 1, 0).isLiquid()) {
                    Location l = new Location(w, x0 + o[0] + 0.5, b.getY() + 1, z0 + o[1] + 0.5);
                    l.setDirection(new Location(w, x0 + 0.5, l.getY(), z0 + 0.5).toVector().subtract(l.toVector()));
                    l.setPitch(0);
                    return l;
                }
            }
        return null;
    }

    /** A cada 5 s: masmorra com jogador por perto ganha o Mercador dela se ainda nao tiver. */
    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::conferir, 200L, 100L);
    }

    private void conferir() {
        if (!pl.getConfig().getBoolean("loja.mercadores", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location pl0 = p.getLocation();
            for (Masmorra m : pl.todas) {
                if (m.tipo.secreta || m.demolindo || !m.mundo.equals(p.getWorld().getName())) continue;
                double dx = m.saidaX - pl0.getX(), dz = m.saidaZ - pl0.getZ();
                if (dx * dx + dz * dz > 64 * 64) continue;
                garantirMercador(m, p.getWorld());
            }
        }
    }

    /** A masmorra foi desmanchada: o mercador dela vai embora. */
    void tirarMercador(Masmorra m, World w) {
        mercadores.remove(m.id);
        NamespacedKey k = new NamespacedKey(pl, "masmorra_loja");
        for (Entity e : w.getNearbyEntities(new Location(w, m.saidaX + 0.5, m.saidaY, m.saidaZ + 0.5), 32, 24, 32)) {
            Integer dono = e.getPersistentDataContainer().get(k, PersistentDataType.INTEGER);
            if (dono != null && dono == m.id) e.remove();
        }
    }

    /** Mercador de masmorra que nao existe mais (carregou depois): vai embora. */
    @org.bukkit.event.EventHandler
    public void mercadorSemMasmorra(org.bukkit.event.world.EntitiesLoadEvent e) {
        NamespacedKey k = new NamespacedKey(pl, "masmorra_loja");
        for (Entity x : e.getEntities()) {
            Integer dono = x.getPersistentDataContainer().get(k, PersistentDataType.INTEGER);
            if (dono != null && !pl.porId.containsKey(dono)) x.remove();
        }
    }

    private void garantirMercador(Masmorra m, World w) {
        UUID id = mercadores.get(m.id);
        if (id != null) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) return;
        }
        if (!w.isChunkLoaded(m.saidaX >> 4, m.saidaZ >> 4)) return;
        Location c = new Location(w, m.saidaX + 0.5, m.saidaY, m.saidaZ + 0.5);
        for (Entity e : w.getNearbyEntities(c, 8, 6, 8)) {
            Integer v = e.getPersistentDataContainer().get(kMercador, PersistentDataType.INTEGER);
            if (v != null && e.getPersistentDataContainer().has(new NamespacedKey(pl, "masmorra_loja"))) {
                Integer dono = e.getPersistentDataContainer().get(new NamespacedKey(pl, "masmorra_loja"), PersistentDataType.INTEGER);
                if (dono != null && dono == m.id) {
                    mercadores.put(m.id, e.getUniqueId());
                    return;
                }
            }
        }
        Location l = lugarPerto(w, m.saidaX, m.saidaY, m.saidaZ);
        if (l == null) return;
        int n = m.tipo.nivel;
        Villager v = npc(l, "§6" + NOMES[n] + " §7[Nível " + n + "]", null, profissao(n), kMercador, n);
        v.getPersistentDataContainer().set(new NamespacedKey(pl, "masmorra_loja"), PersistentDataType.INTEGER, m.id);
        mercadores.put(m.id, v.getUniqueId());
    }

    // ------------------------------------------------------------------ menu de compra

    static final class Menu implements InventoryHolder {
        Inventory inv;
        int nivel;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    /** Mercador Lendario (6 fileiras): um conjunto cosmetico por fileira (colunas 1-5) e os itens uteis nas colunas 7-8. */
    static final int[] SLOTS_LENDARIO = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23, 28, 29, 30, 31, 32, 37, 38, 39, 40, 41,
            16, 17, 25, 26, 34, 35, 43, 44};

    static int[] slots(int nivel) {
        return nivel >= 6 ? SLOTS_LENDARIO : SLOTS;
    }

    void abrir(Player p, int nivel) {
        List<Oferta> l = catalogo.getOrDefault(nivel, List.of());
        int[] slots = slots(nivel);
        int tam = nivel >= 6 ? 54 : 45;
        Menu h = new Menu();
        h.nivel = nivel;
        h.inv = Bukkit.createInventory(h, tam, Masmorras.txt((nivel >= 6 ? "§5§l" : "§6§l") + NOMES[nivel] + (nivel < 6 ? " §8[Nível " + nivel + "]" : "")));
        ItemStack vidro = new ItemStack(nivel >= 6 ? Material.PURPLE_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta vm = vidro.getItemMeta();
        vm.displayName(Masmorras.txt(" "));
        vidro.setItemMeta(vm);
        for (int i = 0; i < tam; i++) h.inv.setItem(i, vidro);
        long saldo = pl.coronas.saldo(p);
        for (int i = 0; i < l.size() && i < slots.length; i++) {
            Oferta o = l.get(i);
            ItemStack s = o.item().clone();
            ItemMeta m = s.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (m.lore() != null) lore.addAll(m.lore());
            lore.add(Masmorras.txt(""));
            lore.add(Masmorras.txt("§7Preço: §6" + Coronas.fmt(o.preco()) + " coronas"));
            lore.add(Masmorras.txt(saldo >= o.preco() ? "§aClique para comprar" : "§cVocê tem " + Coronas.fmt(saldo) + " coronas"));
            m.lore(lore);
            s.setItemMeta(m);
            h.inv.setItem(slots[i], s);
        }
        ItemStack info = new ItemStack(Material.SUNFLOWER);
        ItemMeta im = info.getItemMeta();
        im.displayName(Masmorras.txt("§6§lSeu saldo: §e" + Coronas.fmt(saldo) + " coronas"));
        im.lore(List.of(Masmorras.txt("§7Ganhe coronas nas masmorras:"), Masmorras.txt("§7monstros e, principalmente, chefes."),
                Masmorras.txt("§7Masmorra de nível maior = itens melhores."), Masmorras.txt("§7Itens lendários: §e/masmorras spawn")));
        im.addItemFlags(ItemFlag.values());
        info.setItemMeta(im);
        h.inv.setItem(nivel >= 6 ? 49 : 40, info);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1f);
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int idx = -1;
        int[] slots = slots(h.nivel);
        for (int i = 0; i < slots.length; i++) if (slots[i] == e.getRawSlot()) idx = i;
        List<Oferta> l = catalogo.getOrDefault(h.nivel, List.of());
        if (idx < 0 || idx >= l.size()) return;
        Oferta o = l.get(idx);
        if (!pl.coronas.tirar(p, o.preco())) {
            p.sendMessage(Masmorras.txt("§cFaltam §6" + Coronas.fmt(o.preco() - pl.coronas.saldo(p)) + " coronas §cpara comprar isso."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        if (o.moedas() > 0) {
            MoedaReviver.dar(p, o.moedas());
            pl.getLogger().info(p.getName() + " comprou " + o.moedas() + " moeda(s) de reviver por " + o.preco() + " coronas.");
        } else {
            for (ItemStack sobra : p.getInventory().addItem(o.item().clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
            pl.getLogger().info(p.getName() + " comprou " + o.item().getType() + " x" + o.item().getAmount() + " por " + o.preco() + " coronas (nivel " + h.nivel + ").");
        }
        p.sendMessage(Masmorras.txt("§aComprado por §6" + Coronas.fmt(o.preco()) + " coronas§a. Saldo: §6" + Coronas.fmt(pl.coronas.saldo(p))));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.1f);
        Bukkit.getScheduler().runTask(pl, () -> abrir(p, h.nivel));
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEntityEvent e) {
        Entity x = e.getRightClicked();
        Integer n = x.getPersistentDataContainer().get(kMercador, PersistentDataType.INTEGER);
        boolean guia = x.getPersistentDataContainer().has(kGuia);
        if (n == null && !guia) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (guia) pl.irParaSpawnMasmorras(e.getPlayer());
        else abrir(e.getPlayer(), Math.max(1, Math.min(6, n)));
    }

    /** Nossos villagers nao viram bruxa nem zumbi e nao apanham. */
    @EventHandler(ignoreCancelled = true)
    public void naoTransforma(EntityTransformEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kMercador) || e.getEntity().getPersistentDataContainer().has(kGuia)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoApanha(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kMercador) || e.getEntity().getPersistentDataContainer().has(kGuia)) e.setCancelled(true);
    }
}
