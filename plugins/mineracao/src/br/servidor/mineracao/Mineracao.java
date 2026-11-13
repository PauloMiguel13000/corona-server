package br.servidor.mineracao;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;

/**
 * Mundo de mineracao do Corona Server: 6 minas por rank (A a F) + 1 mina PvP. Sobe de rank com coronas (/rankup),
 * vende o minerio das minas (/vender) e as minas se refazem sozinhas. So o minerio tirado das minas vende
 * (fica marcado), entao ninguem transforma diamante do survival em coronas.
 */
public final class Mineracao extends JavaPlugin implements Listener {

    static final String MUNDO = "mineracao";
    static final int VERSAO_CONSTRUCAO = 1;

    record Rank(String letra, String nome, String cor, long custo, double mult) {}
    static final Rank[] RANKS = {
            new Rank("A", "Aprendiz", "§7", 0, 1.0),
            new Rank("B", "Mineiro", "§a", 1500, 1.1),
            new Rank("C", "Escavador", "§b", 5000, 1.25),
            new Rank("D", "Veterano", "§9", 15000, 1.4),
            new Rank("E", "Mestre", "§6", 40000, 1.6),
            new Rank("F", "Lenda da Mina", "§d", 100000, 2.0)};
    static final int[] BONUS_VIP = {0, 10, 25, 35, 50};

    final List<Mina> minas = new ArrayList<>();
    final Map<Material, Double> precos = new EnumMap<>(Material.class);
    World mundo;
    NamespacedKey kRank, kMinerado, kGanhou;
    static final NamespacedKey CORONAS = new NamespacedKey("masmorras", "coronas");
    static final NamespacedKey VIP_NIVEL = new NamespacedKey("extras", "vip_nivel");
    static final NamespacedKey VIP_ATE = new NamespacedKey("extras", "vip_ate");
    private final Set<String> mundosComAcesso = new HashSet<>();

    static Component txt(String s) { return LegacyComponentSerializer.legacySection().deserialize(s); }

    @Override
    public void onEnable() {
        kRank = new NamespacedKey(this, "rank");
        kMinerado = new NamespacedKey(this, "minerado");
        kGanhou = new NamespacedKey(this, "picareta");
        saveDefaultConfig();
        mundosComAcesso.addAll(getConfig().getStringList("mundos-com-acesso"));
        int reset = Math.max(1, getConfig().getInt("reset-minutos", 8));
        double pct = getConfig().getDouble("reset-quando-minerado", 0.7);

        minas.add(new Mina("a", "Mina A • Pedreira", 0, 60, false, Material.COAL_ORE,
                Material.STONE, 55, Material.COAL_ORE, 25, Material.COPPER_ORE, 20));
        minas.add(new Mina("b", "Mina B • Veio de Ferro", 1, 120, false, Material.IRON_ORE,
                Material.STONE, 40, Material.COAL_ORE, 20, Material.COPPER_ORE, 15, Material.IRON_ORE, 25));
        minas.add(new Mina("c", "Mina C • Gruta Dourada", 2, 180, false, Material.GOLD_ORE,
                Material.STONE, 35, Material.IRON_ORE, 25, Material.GOLD_ORE, 15, Material.REDSTONE_ORE, 15, Material.LAPIS_ORE, 10));
        minas.add(new Mina("d", "Mina D • Abismo", 3, 240, false, Material.DEEPSLATE_DIAMOND_ORE,
                Material.DEEPSLATE, 35, Material.DEEPSLATE_IRON_ORE, 20, Material.DEEPSLATE_GOLD_ORE, 15,
                Material.DEEPSLATE_REDSTONE_ORE, 10, Material.DEEPSLATE_LAPIS_ORE, 10, Material.DEEPSLATE_DIAMOND_ORE, 10));
        minas.add(new Mina("e", "Mina E • Coração da Terra", 4, 300, false, Material.EMERALD_ORE,
                Material.DEEPSLATE, 30, Material.DEEPSLATE_GOLD_ORE, 20, Material.DEEPSLATE_DIAMOND_ORE, 22,
                Material.DEEPSLATE_EMERALD_ORE, 13, Material.DEEPSLATE_REDSTONE_ORE, 15));
        minas.add(new Mina("f", "Mina F • Tesouro Lendário", 5, 360, false, Material.ANCIENT_DEBRIS,
                Material.DEEPSLATE, 25, Material.DEEPSLATE_DIAMOND_ORE, 35, Material.DEEPSLATE_EMERALD_ORE, 25,
                Material.ANCIENT_DEBRIS, 5, Material.DEEPSLATE_GOLD_ORE, 10));
        minas.add(new Mina("pvp", "Mina PvP • Arena do Minério", 2, -80, true, Material.NETHERITE_SWORD,
                Material.DEEPSLATE, 30, Material.DEEPSLATE_DIAMOND_ORE, 25, Material.DEEPSLATE_EMERALD_ORE, 20,
                Material.DEEPSLATE_GOLD_ORE, 15, Material.ANCIENT_DEBRIS, 10));

        preco(1, Material.COAL, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
        preco(0.3, Material.RAW_COPPER, Material.COPPER_INGOT);
        preco(1.5, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
        preco(2, Material.RAW_IRON, Material.IRON_INGOT, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
        preco(3, Material.RAW_GOLD, Material.GOLD_INGOT, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);
        preco(0.3, Material.REDSTONE);
        preco(1.5, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
        preco(0.25, Material.LAPIS_LAZULI);
        preco(1.5, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        preco(10, Material.DIAMOND, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        preco(14, Material.EMERALD, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        preco(30, Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);

        criarMundo();
        if (mundo == null) { getLogger().severe("Nao consegui criar o mundo da mineracao."); return; }
        if (getConfig().getInt("construido", 0) < VERSAO_CONSTRUCAO) {
            construirCentro();
            for (Mina m : minas) m.construirMoldura(mundo);
            getConfig().set("construido", VERSAO_CONSTRUCAO);
            saveConfig();
            getLogger().info("Mundo de mineracao construido (centro + " + minas.size() + " minas).");
        }
        for (Mina m : minas) refazer(m);
        getServer().getPluginManager().registerEvents(this, this);
        if (getServer().getPluginManager().getPlugin("GriefPrevention") != null) {
            try { getServer().getPluginManager().registerEvents(new SemTerreno(), this); }
            catch (Throwable t) { getLogger().warning("GriefPrevention sem o evento de terreno: " + t); }
        }
        long ms = reset * 60_000L;
        resetMs = ms;
        Bukkit.getScheduler().runTaskTimer(this, this::barra, 40L, 30L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long agora = System.currentTimeMillis();
            for (Mina m : minas) {
                if (m.refazendo || m.quebrados == 0) continue;
                if (m.quebrados >= Mina.volume() * pct || agora - m.ultimoReset >= ms) refazer(m);
            }
        }, 400L, 200L);
    }

    long resetMs;

    /** Barra de status (13/11): mostra a mina, quanto ja foi minerado e quando ela se refaz. */
    void barra() {
        if (mundo == null) return;
        long agora = System.currentTimeMillis();
        for (Player p : mundo.getPlayers()) {
            Mina m = minaDaArea(p.getLocation());
            int r = rank(p);
            if (m == null) {
                p.sendActionBar(txt("§6⛏ Rank " + RANKS[r].cor() + RANKS[r].letra() + " §8• §6" + fmt(coronas(p)) + " coronas §8• §e/minas §7/rankup §7/vender"));
                continue;
            }
            int pct = (int) Math.min(100, m.quebrados * 100L / Mina.volume());
            long falta = m.quebrados == 0 ? -1 : Math.max(0, resetMs - (agora - m.ultimoReset));
            String quando = m.refazendo ? "§erefazendo..." : falta < 0 ? "§acheia" : "§7refaz em §f" + (falta / 60000) + ":" + String.format("%02d", (falta / 1000) % 60);
            p.sendActionBar(txt((m.pvp ? "§c⚔ " : "§6⛏ ") + m.nome + " §8• §f" + pct + "% §7minerada §8• " + quando + " §8• §7rank " + RANKS[r].cor() + RANKS[r].letra()));
        }
    }

    private void preco(double v, Material... ms) { for (Material m : ms) precos.put(m, v); }

    // ------------------------------------------------------------------ mundo
    private void criarMundo() {
        mundo = Bukkit.getWorld(MUNDO);
        if (mundo == null) mundo = new WorldCreator(MUNDO).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        mundo.setDifficulty(Difficulty.EASY);
        mundo.setGameRule(GameRules.SPAWN_MOBS, false);
        mundo.setGameRule(GameRules.SPAWN_MONSTERS, false);
        mundo.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        mundo.setGameRule(GameRules.SPAWN_PATROLS, false);
        mundo.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        mundo.setGameRule(GameRules.ADVANCE_TIME, false);
        mundo.setGameRule(GameRules.ADVANCE_WEATHER, false);
        mundo.setGameRule(GameRules.KEEP_INVENTORY, false);
        mundo.setGameRule(GameRules.MOB_GRIEFING, false);
        mundo.setGameRule(GameRules.PVP, true);
        mundo.setGameRule(GameRules.FALL_DAMAGE, false);
        mundo.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        mundo.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        mundo.setTime(6000);
        mundo.setStorm(false);
        mundo.setSpawnLocation(new Location(mundo, 0.5, 100, 0.5));
    }

    static final class Vazio extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    Location centro() { Location l = new Location(mundo, 0.5, 100, 0.5); l.setYaw(-90); return l; }

    private void construirCentro() {
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                boolean beira = Math.abs(x) == 8 || Math.abs(z) == 8;
                boolean cruz = x == 0 || z == 0;
                mundo.getBlockAt(x, 99, z).setType(beira ? Material.POLISHED_BLACKSTONE_BRICKS : cruz ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE, false);
                for (int y = 96; y < 99; y++) mundo.getBlockAt(x, y, z).setType(Material.DEEPSLATE_BRICKS, false);
                if (beira && Math.abs(x) == Math.abs(z)) {
                    mundo.getBlockAt(x, 100, z).setType(Material.POLISHED_BLACKSTONE_WALL, false);
                    mundo.getBlockAt(x, 101, z).setType(Material.LANTERN, false);
                }
            }
        // caminho para a mina PvP (vermelho) e para as minas normais (pedra)
        for (int x = 9; x <= 47; x++) for (int z = -1; z <= 1; z++) mundo.getBlockAt(x, 99, z).setType(z == 0 ? Material.SMOOTH_STONE : Material.STONE_BRICKS, false);
        for (int x = -66; x <= -9; x++) for (int z = -1; z <= 1; z++) mundo.getBlockAt(x, 99, z).setType(z == 0 ? Material.RED_NETHER_BRICKS : Material.NETHER_BRICKS, false);
        for (int i = 0; i + 1 < 6; i++) {
            int x0 = 60 + i * 60 + Mina.RAIO + 1 + Mina.BORDA + 1, x1 = 60 + (i + 1) * 60 - Mina.RAIO - 1 - Mina.BORDA - 1;
            for (int x = x0; x <= x1; x++) for (int z = -1; z <= 1; z++) mundo.getBlockAt(x, 99, z).setType(z == 0 ? Material.SMOOTH_STONE : Material.STONE_BRICKS, false);
        }
    }

    // ------------------------------------------------------------------ reset das minas
    void refazer(Mina m) {
        if (mundo == null || m.refazendo) return;
        m.refazendo = true;
        for (Player p : mundo.getPlayers())
            if (m.dentroDoPoco(p.getLocation())) {
                p.teleport(m.chegada(mundo));
                p.sendActionBar(txt("§e⛏ A " + m.nome + " §eestá sendo refeita!"));
            }
        List<int[]> pos = new ArrayList<>(Mina.volume());
        for (int y = Mina.TOPO; y > Mina.TOPO - Mina.FUNDO; y--)
            for (int x = -Mina.RAIO; x <= Mina.RAIO; x++)
                for (int z = -Mina.RAIO; z <= Mina.RAIO; z++) pos.add(new int[]{m.cx + x, y, z});
        Iterator<int[]> it = pos.iterator();
        Bukkit.getScheduler().runTaskTimer(this, t -> {
            for (int n = 0; n < 2500 && it.hasNext(); n++) {
                int[] b = it.next();
                mundo.getBlockAt(b[0], b[1], b[2]).setType(m.sortear(), false);
            }
            if (!it.hasNext()) {
                t.cancel();
                m.quebrados = 0;
                m.ultimoReset = System.currentTimeMillis();
                m.refazendo = false;
                for (Player p : mundo.getPlayers())
                    if (m.naArea(p.getLocation())) p.sendActionBar(txt("§a⛏ A " + m.nome + " §afoi refeita!"));
            }
        }, 1L, 1L);
    }

    Mina minaDoBloco(Block b) {
        for (Mina m : minas) if (m.noMiolo(b)) return m;
        return null;
    }

    Mina minaDaArea(Location l) {
        for (Mina m : minas) if (m.naArea(l)) return m;
        return null;
    }

    // ------------------------------------------------------------------ rank, coronas
    int rank(Player p) {
        Integer r = p.getPersistentDataContainer().get(kRank, PersistentDataType.INTEGER);
        return r == null ? 0 : Math.max(0, Math.min(RANKS.length - 1, r));
    }

    static long coronas(Player p) {
        Long v = p.getPersistentDataContainer().get(CORONAS, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    static void setCoronas(Player p, long v) { p.getPersistentDataContainer().set(CORONAS, PersistentDataType.LONG, Math.max(0, v)); }

    static int vip(Player p) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        Integer n = c.get(VIP_NIVEL, PersistentDataType.INTEGER);
        Long ate = c.get(VIP_ATE, PersistentDataType.LONG);
        if (n == null || n <= 0 || ate == null || ate < System.currentTimeMillis()) return 0;
        return Math.min(n, BONUS_VIP.length - 1);
    }

    static String fmt(long v) { return String.format(Locale.ROOT, "%,d", v).replace(',', '.'); }

    String nomeRank(int r) { return RANKS[r].cor() + "[" + RANKS[r].letra() + "] " + RANKS[r].nome(); }

    // ------------------------------------------------------------------ eventos
    boolean admin(Player p) { return p.hasPermission("mineracao.admin") && p.getGameMode() == GameMode.CREATIVE; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void quebrar(BlockBreakEvent e) {
        if (e.getBlock().getWorld() != mundo) return;
        Player p = e.getPlayer();
        if (admin(p)) return;
        Mina m = minaDoBloco(e.getBlock());
        if (m == null || m.refazendo) { e.setCancelled(true); return; }
        if (rank(p) < m.rank) {
            e.setCancelled(true);
            p.sendActionBar(txt("§cPrecisa do rank " + nomeRank(m.rank) + " §cpara minerar aqui. §e/rankup"));
            return;
        }
        Material t = e.getBlock().getType();
        if (t == Material.STONE || t == Material.DEEPSLATE || t == Material.COBBLESTONE || t == Material.COBBLED_DEEPSLATE) e.setDropItems(false);
        e.setExpToDrop(Math.min(e.getExpToDrop(), 3));
        m.quebrados++;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void soltou(BlockDropItemEvent e) {
        if (e.getBlock().getWorld() != mundo || minaDoBloco(e.getBlock()) == null) return;
        Player p = e.getPlayer();
        boolean cheio = false;
        for (Iterator<Item> i = e.getItems().iterator(); i.hasNext(); ) {
            Item it = i.next();
            ItemStack s = it.getItemStack();
            if (!precos.containsKey(s.getType())) continue;
            ItemStack m = marcar(s);
            // coleta automatica (13/11): vai direto pro inventario; o que nao couber cai no chao
            Map<Integer, ItemStack> sobra = p.getInventory().addItem(m.clone());
            if (sobra.isEmpty()) { i.remove(); continue; }
            cheio = true;
            it.setItemStack(sobra.values().iterator().next());
        }
        if (cheio) p.sendActionBar(txt("§c⛏ Inventário cheio! Use §e/vender"));
    }

    ItemStack marcar(ItemStack s) {
        ItemMeta im = s.getItemMeta();
        if (im == null) return s;
        im.getPersistentDataContainer().set(kMinerado, PersistentDataType.BYTE, (byte) 1);
        im.lore(List.of(txt("§8⛏ Minério da Mina §7— §e/vender")));
        s.setItemMeta(im);
        return s;
    }

    boolean marcado(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return false;
        return s.getItemMeta().getPersistentDataContainer().has(kMinerado, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        if (e.getBlock().getWorld() == mundo && !admin(e.getPlayer())) e.setCancelled(true);
    }

    static Player atacante(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void pvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v) || v.getWorld() != mundo) return;
        Player a = atacante(e);
        if (a == null || a == v) return;
        Mina mv = minaDaArea(v.getLocation()), ma = minaDaArea(a.getLocation());
        if (mv != null && mv.pvp && ma == mv) return;   // PvP liberado so dentro da mina PvP
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (p.getWorld() != mundo) return;
        Mina m = minaDaArea(p.getLocation());
        if (m != null && m.pvp) {
            e.deathMessage(txt("§c⚔ " + p.getName() + " §7caiu na §cMina PvP" + (p.getKiller() != null ? " §7para §c" + p.getKiller().getName() : "") + "§7!"));
            return;   // na mina PvP os itens caem
        }
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        if (e.getPlayer().getWorld() == mundo && mundo != null) e.setRespawnLocation(centro());
    }

    @EventHandler
    public void entrou(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld() != mundo) return;
        p.showTitle(Title.title(txt("§6⛏ Mundo de Mineração"), txt("§e/minas §7escolher mina §8• §e/rankup §7subir de rank"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
        p.sendMessage(txt("§6§l⛏ Mineração §8» §7Seu rank: " + nomeRank(rank(p)) + "§7. Minere, use §e/vender §7e suba com §e/rankup§7. A §cMina PvP §7(rank C) tem o melhor minério, mas lá os itens caem."));
        PersistentDataContainer c = p.getPersistentDataContainer();
        if (!c.has(kGanhou, PersistentDataType.BYTE)) {
            c.set(kGanhou, PersistentDataType.BYTE, (byte) 1);
            ItemStack pic = new ItemStack(Material.IRON_PICKAXE);
            ItemMeta im = pic.getItemMeta();
            im.displayName(txt("§6Picareta do Mineiro"));
            im.addEnchant(Enchantment.EFFICIENCY, 2, true);
            im.addEnchant(Enchantment.UNBREAKING, 2, true);
            pic.setItemMeta(im);
            p.getInventory().addItem(pic).values().forEach(s -> p.getWorld().dropItemNaturally(p.getLocation(), s));
            p.sendMessage(txt("§a+ Picareta do Mineiro §7(presente da primeira visita)"));
        }
    }

    // ------------------------------------------------------------------ menu
    static final class Menu implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    void abrirMenu(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, txt("§8⛏ Minas • seu rank: " + RANKS[rank(p)].letra()));
        h.inv = inv;
        inv.setItem(4, item(Material.COMPASS, "§e§lCentro da Mineração", "§7Ir para o centro do mundo de mineração."));
        int[] slots = {10, 11, 12, 13, 14, 15};
        int r = rank(p);
        for (int i = 0; i < 6; i++) {
            Mina m = minas.get(i);
            boolean ok = r >= m.rank;
            List<String> lore = new ArrayList<>();
            lore.add("§7Rank: " + nomeRank(m.rank));
            lore.add("§7Minérios: §f" + resumo(m));
            lore.add(ok ? "§a▶ Clique para ir" : "§c✖ Precisa do rank " + RANKS[m.rank].letra() + " §8(/rankup)");
            inv.setItem(slots[i], item(ok ? m.icone : Material.BARRIER, (ok ? "§a" : "§c") + m.nome, lore.toArray(new String[0])));
        }
        Mina pv = minas.get(6);
        inv.setItem(16, item(r >= pv.rank ? Material.NETHERITE_SWORD : Material.BARRIER, "§c§l" + pv.nome,
                "§7Rank: " + nomeRank(pv.rank), "§7Minérios: §f" + resumo(pv), "§c⚠ PvP ligado: quem morre lá", "§c   perde os itens!", r >= pv.rank ? "§a▶ Clique para ir" : "§c✖ Precisa do rank C"));
        long cust = r + 1 < RANKS.length ? RANKS[r + 1].custo() : 0;
        inv.setItem(22, item(Material.EXPERIENCE_BOTTLE, "§6§lRank Up",
                "§7Seu rank: " + nomeRank(r), r + 1 < RANKS.length ? "§7Próximo: " + nomeRank(r + 1) + " §7por §6" + fmt(cust) + " coronas" : "§dVocê está no rank máximo!",
                "§7Suas coronas: §6" + fmt(coronas(p)), "§7Bônus na venda: §a+" + Math.round((RANKS[r].mult() - 1) * 100) + "%", r + 1 < RANKS.length ? "§a▶ Clique para subir" : ""));
        inv.setItem(18, item(Material.ANVIL, "§b§lMelhorar picareta", "§7Segure a picareta na mão e clique.",
                "§7Eficiência até V, Fortuna até III,", "§7Inquebrável até III (custa coronas).", "§a▶ Clique para ver os preços"));
        inv.setItem(26, item(Material.GOLD_INGOT, "§e§lVender minério", "§7Vende todo minério das minas", "§7que estiver no inventário.", "§a▶ Clique para vender"));
        p.openInventory(inv);
    }

    String resumo(Mina m) {
        StringBuilder sb = new StringBuilder();
        for (Material mt : m.mistura.keySet()) {
            String n = mt.name().replace("DEEPSLATE_", "").replace("_ORE", "").replace("ANCIENT_DEBRIS", "NETHERITA");
            if (n.equals("STONE") || n.equals("DEEPSLATE")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(switch (n) { case "COAL" -> "carvão"; case "COPPER" -> "cobre"; case "IRON" -> "ferro"; case "GOLD" -> "ouro";
                case "REDSTONE" -> "redstone"; case "LAPIS" -> "lápis"; case "DIAMOND" -> "diamante"; case "EMERALD" -> "esmeralda"; default -> "netherita"; });
        }
        return sb.toString();
    }

    static ItemStack item(Material m, String nome, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta im = s.getItemMeta();
        im.displayName(txt(nome));
        List<Component> l = new ArrayList<>();
        for (String x : lore) if (!x.isEmpty()) l.add(txt(x));
        im.lore(l);
        s.setItemMeta(im);
        return s;
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getSlot();
        if (s == 4) { p.closeInventory(); irPara(p, null); }
        else if (s >= 10 && s <= 15) { p.closeInventory(); irPara(p, minas.get(s - 10)); }
        else if (s == 16) { p.closeInventory(); irPara(p, minas.get(6)); }
        else if (s == 22) { p.closeInventory(); rankup(p); }
        else if (s == 26) { p.closeInventory(); vender(p); }
        else if (s == 18) { p.closeInventory(); abrirMelhorias(p); }
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu || e.getView().getTopInventory().getHolder() instanceof Bigorna) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ melhorias da picareta (13/11)
    record Melhoria(Enchantment enc, String nome, Material icone, long[] custo) {}
    static final Melhoria[] MELHORIAS = {
            new Melhoria(Enchantment.EFFICIENCY, "Eficiência", Material.GOLDEN_PICKAXE, new long[]{300, 600, 1200, 2500, 5000}),
            new Melhoria(Enchantment.FORTUNE, "Fortuna", Material.EMERALD, new long[]{2000, 6000, 15000}),
            new Melhoria(Enchantment.UNBREAKING, "Inquebrável", Material.OBSIDIAN, new long[]{400, 900, 2000})};

    static final class Bigorna implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    static boolean picareta(ItemStack s) { return s != null && s.getType().name().endsWith("_PICKAXE"); }

    void abrirMelhorias(Player p) {
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (!picareta(mao)) { p.sendMessage(txt("§cSegure uma picareta na mão para melhorar.")); return; }
        Bigorna h = new Bigorna();
        Inventory inv = Bukkit.createInventory(h, 9, txt("§8⚒ Melhorar picareta"));
        h.inv = inv;
        for (int i = 0; i < MELHORIAS.length; i++) {
            Melhoria m = MELHORIAS[i];
            int nv = mao.getEnchantmentLevel(m.enc());
            if (nv >= m.custo().length) inv.setItem(2 + i * 2, item(m.icone(), "§a" + m.nome() + " " + romano(nv), "§7Já está no máximo."));
            else inv.setItem(2 + i * 2, item(m.icone(), "§b" + m.nome() + " " + romano(nv) + " §7→ §b" + romano(nv + 1),
                    "§7Custa §6" + fmt(m.custo()[nv]) + " coronas", "§7Você tem §6" + fmt(coronas(p)), "§a▶ Clique para melhorar"));
        }
        p.openInventory(inv);
    }

    static String romano(int n) { return n <= 0 ? "0" : new String[]{"I", "II", "III", "IV", "V"}[Math.min(5, n) - 1]; }

    @EventHandler
    public void cliqueBigorna(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Bigorna)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int i = (e.getSlot() - 2) / 2;
        if (e.getSlot() < 2 || (e.getSlot() - 2) % 2 != 0 || i >= MELHORIAS.length) return;
        melhorar(p, MELHORIAS[i]);
        abrirMelhorias(p);
    }

    void melhorar(Player p, Melhoria m) {
        ItemStack mao = p.getInventory().getItemInMainHand();
        if (!picareta(mao)) { p.closeInventory(); p.sendMessage(txt("§cSegure a picareta na mão.")); return; }
        int nv = mao.getEnchantmentLevel(m.enc());
        if (nv >= m.custo().length) { p.sendMessage(txt("§a" + m.nome() + " já está no máximo.")); return; }
        long c = m.custo()[nv], tem = coronas(p);
        if (tem < c) { p.sendMessage(txt("§cFaltam §6" + fmt(c - tem) + " coronas §cpara " + m.nome() + " " + romano(nv + 1) + ".")); return; }
        setCoronas(p, tem - c);
        ItemMeta im = mao.getItemMeta();
        im.addEnchant(m.enc(), nv + 1, true);
        mao.setItemMeta(im);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        p.sendMessage(txt("§b⚒ Picareta melhorada: §f" + m.nome() + " " + romano(nv + 1) + " §7(-" + fmt(c) + " coronas)"));
    }

    boolean podeSair(Player p) {
        String w = p.getWorld().getName();
        return p.getWorld() == mundo || mundosComAcesso.contains(w) || p.hasPermission("mineracao.admin");
    }

    void irPara(Player p, Mina m) {
        if (!podeSair(p)) { p.sendMessage(txt("§cDaqui não dá para ir para a mineração. Volte ao spawn primeiro.")); return; }
        if (m == null) { p.teleport(centro()); return; }
        if (rank(p) < m.rank) { p.sendMessage(txt("§cA " + m.nome + " §cprecisa do rank " + nomeRank(m.rank) + "§c. Use §e/rankup§c.")); return; }
        p.teleport(m.chegada(mundo));
        if (m.pvp) p.sendMessage(txt("§c§l⚠ MINA PVP §8» §7Aqui outros jogadores podem te atacar e quem morre §cperde os itens§7."));
    }

    void rankup(Player p) {
        int r = rank(p);
        if (r + 1 >= RANKS.length) { p.sendMessage(txt("§dVocê já está no rank máximo: " + nomeRank(r))); return; }
        Rank prox = RANKS[r + 1];
        long tem = coronas(p);
        if (tem < prox.custo()) {
            p.sendMessage(txt("§cFaltam §6" + fmt(prox.custo() - tem) + " coronas §cpara o rank " + nomeRank(r + 1) + "§c. Venda minério com §e/vender§c."));
            return;
        }
        setCoronas(p, tem - prox.custo());
        p.getPersistentDataContainer().set(kRank, PersistentDataType.INTEGER, r + 1);
        p.showTitle(Title.title(txt("§6§lRANK UP!"), txt(nomeRank(r + 1)), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.broadcast(txt("§6⛏ §e" + p.getName() + " §7subiu para o rank " + nomeRank(r + 1) + " §7na mineração!"));
        getLogger().info(p.getName() + " subiu para o rank " + prox.letra() + " (pagou " + prox.custo() + ")");
    }

    void vender(Player p) {
        double total = 0;
        int itens = 0;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length; i++) {
            ItemStack s = cont[i];
            if (s == null || !marcado(s)) continue;
            Double pr = precos.get(s.getType());
            if (pr == null) continue;
            total += pr * s.getAmount();
            itens += s.getAmount();
            cont[i] = null;
        }
        if (itens == 0) { p.sendMessage(txt("§7Você não tem minério das minas para vender. §8(só o que sai das minas vende)")); return; }
        p.getInventory().setStorageContents(cont);
        int r = rank(p);
        double comRank = total * RANKS[r].mult();
        int nv = vip(p);
        long v = Math.max(1, Math.round(comRank * (100 + BONUS_VIP[nv]) / 100.0));
        setCoronas(p, coronas(p) + v);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        p.sendMessage(txt("§a⛏ Vendeu §f" + itens + " §aminérios por §6" + fmt(v) + " coronas §8(rank " + RANKS[r].letra()
                + (nv > 0 ? ", VIP +" + BONUS_VIP[nv] + "%" : "") + ") §7— saldo: §6" + fmt(coronas(p))));
    }

    // ------------------------------------------------------------------ comandos
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("mineracaoadm")) return adm(s, a);
        if (!(s instanceof Player p)) { s.sendMessage("So jogador."); return true; }
        if (mundo == null) { p.sendMessage(txt("§cA mineração não está disponível agora.")); return true; }
        switch (n) {
            case "minas" -> {
                if (a.length > 0) {
                    String id = a[0].toLowerCase(Locale.ROOT);
                    if (id.equals("centro")) { irPara(p, null); return true; }
                    for (Mina m : minas) if (m.id.equals(id)) { irPara(p, m); return true; }
                    p.sendMessage(txt("§7Minas: §ea b c d e f pvp centro"));
                    return true;
                }
                abrirMenu(p);
            }
            case "rankup" -> rankup(p);
            case "vender" -> vender(p);
            case "picareta" -> abrirMelhorias(p);
            case "rank" -> {
                int r = rank(p);
                p.sendMessage(txt("§6⛏ Seu rank: " + nomeRank(r) + " §8| §7coronas: §6" + fmt(coronas(p))
                        + (r + 1 < RANKS.length ? " §8| §7próximo: " + nomeRank(r + 1) + " §7por §6" + fmt(RANKS[r + 1].custo()) : "")));
            }
        }
        return true;
    }

    private boolean adm(CommandSender s, String[] a) {
        if (a.length == 0) { s.sendMessage("/mineracaoadm resetar <a..f|pvp|todas> | rank <nick> <A-F> | ver <nick> | info"); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "resetar" -> {
                String id = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "todas";
                for (Mina m : minas) if (id.equals("todas") || m.id.equals(id)) refazer(m);
                s.sendMessage("refazendo: " + id);
            }
            case "rank" -> {
                Player p = a.length > 2 ? Bukkit.getPlayerExact(a[1]) : null;
                if (p == null) { s.sendMessage("jogador offline"); return true; }
                int r = "ABCDEF".indexOf(a[2].toUpperCase(Locale.ROOT));
                if (r < 0) { s.sendMessage("rank A-F"); return true; }
                p.getPersistentDataContainer().set(kRank, PersistentDataType.INTEGER, r);
                s.sendMessage(p.getName() + " agora e rank " + RANKS[r].letra());
            }
            case "ver" -> {
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (p == null) { s.sendMessage("jogador offline"); return true; }
                int marc = 0;
                for (ItemStack it : p.getInventory().getStorageContents()) if (marcado(it)) marc += it.getAmount();
                s.sendMessage(p.getName() + " rank=" + RANKS[rank(p)].letra() + " coronas=" + coronas(p) + " minerio-marcado=" + marc);
            }
            case "info" -> {
                for (Mina m : minas) {
                    Map<Material, Integer> cont = new EnumMap<>(Material.class);
                    for (int y = Mina.TOPO; y > Mina.TOPO - Mina.FUNDO; y -= 4)
                        for (int x = -Mina.RAIO; x <= Mina.RAIO; x += 2)
                            for (int z = -Mina.RAIO; z <= Mina.RAIO; z += 2) cont.merge(mundo.getBlockAt(m.cx + x, y, z).getType(), 1, Integer::sum);
                    s.sendMessage("mina " + m.id + " rank=" + RANKS[m.rank].letra() + " quebrados=" + m.quebrados + " amostra=" + cont);
                }
            }
            default -> s.sendMessage("?");
        }
        return true;
    }
}
