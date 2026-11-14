package br.servidor.mineracao;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.*;

/**
 * Rank Up do Corona Server (13/11, pedido do Paulo): um mundo a parte com tema de PRISAO.
 * - Patio da prisao (spawn proprio) com um portal para cada lugar: 12 minas (Blocos A a L), Mina PvP, Sala de
 *   Encantamentos e a Saida para o spawn. Cada mina tem o portal de volta.
 * - Dinheiro proprio ($, nao e corona): ganha vendendo o minerio das minas; paga o rank up, os kits nao.
 * - Mochila propria (Extras: grupo "mineracao"): nada entra nem sai; so o Traje do Rei (VIP) acompanha.
 * - Sem poderes (Poderes), sem pets, sem fome; kits so daqui (jogador e VIPs), a cada 24 h.
 * - No spawn principal: um portal com cara de cadeia ao lado do portal das masmorras.
 */
public final class Mineracao extends JavaPlugin implements Listener {

    static final String MUNDO = "mineracao";
    static final int VERSAO_CONSTRUCAO = 9;   // 9: antessala segura da Mina PvP;   // 4: tapetes de vender; 5: loja de picaretas; 6: sem melhoria com $ (so encantando)
    /** 13/11 (Paulo): minas em quadrado (4 x 3), perto uma da outra para todos se verem, com um vao de 10 blocos
     *  de vazio entre as bordas (ninguem pula de uma para a outra). */
    static final int GRADE_X0 = 100, GRADE_Z0 = -53, GRADE_PASSO = 2 * (Mina.RAIO + 1 + Mina.BORDA) + 1 + 10;
    static final int H = 30;            // patio: x,z de -30 a 30
    static final int PISO = 99;         // piso do patio (anda em y=100)

    record Rank(String letra, String nome, String cor, long custo, double mult) {}
    static final Rank[] RANKS = {
            new Rank("A", "Novato", "§7", 0, 1.0),
            new Rank("B", "Detento", "§f", 1_000, 1.1),
            new Rank("C", "Ladrão", "§a", 3_000, 1.2),
            new Rank("D", "Fugitivo", "§2", 7_000, 1.3),
            new Rank("E", "Bandido", "§b", 15_000, 1.45),
            new Rank("F", "Contrabandista", "§3", 30_000, 1.6),
            new Rank("G", "Chefe de Cela", "§9", 55_000, 1.75),
            new Rank("H", "Carcereiro", "§e", 90_000, 1.9),
            new Rank("I", "Xerife", "§6", 140_000, 2.1),
            new Rank("J", "Diretor", "§c", 220_000, 2.3),
            new Rank("K", "Imperador", "§5", 350_000, 2.6),
            new Rank("L", "Lenda da Prisão", "§d", 600_000, 3.0)};
    static final int[] BONUS_VIP = {0, 10, 25, 35, 50};
    static final String[] NOME_VIP = {"", "VIP", "VIP+", "MVP", "LENDA"};

    final List<Mina> minas = new ArrayList<>();
    Missoes missoes;
    GolemMina golem;
    final Map<Material, Double> precos = new EnumMap<>(Material.class);
    World mundo;
    NamespacedKey kRank, kMinerado, kGanhou, kDinheiro, kEnfeite, kAcao, kPrisao, kAuto, kPrest, kDiaria, kVersao;
    static final NamespacedKey VIP_NIVEL = new NamespacedKey("extras", "vip_nivel");
    static final NamespacedKey VIP_ATE = new NamespacedKey("extras", "vip_ate");
    private final Set<String> mundosComAcesso = new HashSet<>();
    private final Map<UUID, Long> espera = new HashMap<>();

    static Component txt(String s) { return LegacyComponentSerializer.legacySection().deserialize(s); }

    // ------------------------------------------------------------------ portais
    /** Portal: centro (x,z), parede (n/s/l/o), acao (id da mina, "pvp", "enc", "saida", "patio"). */
    record Portal(int x, int z, char parede, String acao, String nome, String linha2, String topo) {}
    final List<Portal> portaisPatio = new ArrayList<>();

    // sala de encantamentos
    static final int ENC_Z = -120;

    @Override
    public void onEnable() {
        kRank = new NamespacedKey(this, "rank");
        kMinerado = new NamespacedKey(this, "minerado");
        kGanhou = new NamespacedKey(this, "picareta2");
        kDinheiro = new NamespacedKey(this, "dinheiro");
        kEnfeite = new NamespacedKey(this, "enfeite");
        kAcao = new NamespacedKey(this, "acao");
        kPrisao = new NamespacedKey(this, "item_prisao");
        kAuto = new NamespacedKey(this, "auto_venda");
        kPrest = new NamespacedKey(this, "prestigio");
        kDiaria = new NamespacedKey(this, "diaria");
        kVersao = new NamespacedKey(this, "versao");
        missoes = new Missoes(this);
        saveDefaultConfig();
        mundosComAcesso.addAll(getConfig().getStringList("mundos-com-acesso"));
        int reset = Math.max(1, getConfig().getInt("reset-minutos", 8));
        double pct = getConfig().getDouble("reset-quando-minerado", 0.7);

        M("a", "Bloco A • Pedreira", 0, Material.COAL_ORE, Material.STONE, 55, Material.COAL_ORE, 25, Material.COPPER_ORE, 20);
        M("b", "Bloco B • Galeria do Ferro", 1, Material.IRON_ORE, Material.STONE, 45, Material.COAL_ORE, 20, Material.COPPER_ORE, 15, Material.IRON_ORE, 20);
        M("c", "Bloco C • Poço Azul", 2, Material.LAPIS_ORE, Material.STONE, 40, Material.IRON_ORE, 25, Material.COAL_ORE, 15, Material.LAPIS_ORE, 20);
        M("d", "Bloco D • Veio Vermelho", 3, Material.REDSTONE_ORE, Material.ANDESITE, 35, Material.IRON_ORE, 25, Material.GOLD_ORE, 15, Material.REDSTONE_ORE, 25);
        M("e", "Bloco E • Gruta Dourada", 4, Material.GOLD_ORE, Material.STONE, 30, Material.GOLD_ORE, 25, Material.IRON_ORE, 20, Material.REDSTONE_ORE, 15, Material.LAPIS_ORE, 10);
        M("f", "Bloco F • Primeiro Brilho", 5, Material.DIAMOND_ORE, Material.DEEPSLATE, 40, Material.DEEPSLATE_IRON_ORE, 25, Material.DEEPSLATE_GOLD_ORE, 25, Material.DEEPSLATE_DIAMOND_ORE, 10);
        M("g", "Bloco G • Abismo", 6, Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE, 35, Material.DEEPSLATE_GOLD_ORE, 25, Material.DEEPSLATE_DIAMOND_ORE, 18, Material.DEEPSLATE_REDSTONE_ORE, 22);
        M("h", "Bloco H • Cofre Verde", 7, Material.EMERALD_ORE, Material.DEEPSLATE, 30, Material.DEEPSLATE_DIAMOND_ORE, 25, Material.DEEPSLATE_EMERALD_ORE, 15, Material.DEEPSLATE_GOLD_ORE, 30);
        M("i", "Bloco I • Forno do Nether", 8, Material.NETHER_QUARTZ_ORE, Material.NETHERRACK, 30, Material.NETHER_QUARTZ_ORE, 25, Material.NETHER_GOLD_ORE, 20, Material.DEEPSLATE_DIAMOND_ORE, 15, Material.DEEPSLATE_EMERALD_ORE, 10);
        M("j", "Bloco J • Bastião Negro", 9, Material.GILDED_BLACKSTONE, Material.BLACKSTONE, 30, Material.GILDED_BLACKSTONE, 20, Material.DEEPSLATE_DIAMOND_ORE, 25, Material.DEEPSLATE_EMERALD_ORE, 18, Material.ANCIENT_DEBRIS, 7);
        M("k", "Bloco K • Tesouro Imperial", 10, Material.ANCIENT_DEBRIS, Material.DEEPSLATE, 25, Material.DEEPSLATE_DIAMOND_ORE, 30, Material.DEEPSLATE_EMERALD_ORE, 25, Material.ANCIENT_DEBRIS, 12, Material.GOLD_BLOCK, 8);
        M("l", "Bloco L • Cela da Lenda", 11, Material.DIAMOND_BLOCK, Material.DEEPSLATE, 20, Material.DEEPSLATE_DIAMOND_ORE, 30, Material.DEEPSLATE_EMERALD_ORE, 25, Material.ANCIENT_DEBRIS, 15, Material.DIAMOND_BLOCK, 5, Material.EMERALD_BLOCK, 5);
        minas.add(new Mina("pvp", "⚔ Mina PvP • TEM PVP: quem morre perde tudo", 3, -80, 0, true, Material.NETHERITE_SWORD,
                Material.DEEPSLATE, 30, Material.DEEPSLATE_DIAMOND_ORE, 25, Material.DEEPSLATE_EMERALD_ORE, 20, Material.DEEPSLATE_GOLD_ORE, 15, Material.ANCIENT_DEBRIS, 10));

        preco(1, Material.COAL, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
        preco(0.3, Material.RAW_COPPER, Material.COPPER_INGOT);
        preco(1.5, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
        preco(2, Material.RAW_IRON, Material.IRON_INGOT, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
        preco(3, Material.RAW_GOLD, Material.GOLD_INGOT, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);
        preco(0.3, Material.REDSTONE, Material.GOLD_NUGGET);
        preco(1.5, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE);
        preco(0.25, Material.LAPIS_LAZULI);
        preco(1.5, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        preco(0.8, Material.QUARTZ);
        preco(2, Material.GILDED_BLACKSTONE);
        preco(10, Material.DIAMOND, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        preco(14, Material.EMERALD, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        preco(30, Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        preco(27, Material.GOLD_BLOCK);
        preco(90, Material.DIAMOND_BLOCK);
        preco(126, Material.EMERALD_BLOCK);

        // portais do patio: norte A-E, leste F-J, sul K, L, PvP, Encantamentos; oeste a saida
        String[] cores = {"minecraft:white_concrete", "minecraft:light_gray_concrete", "minecraft:lime_concrete", "minecraft:green_concrete", "minecraft:light_blue_concrete",
                "minecraft:cyan_concrete", "minecraft:blue_concrete", "minecraft:yellow_concrete", "minecraft:orange_concrete", "minecraft:red_concrete", "minecraft:purple_concrete", "minecraft:magenta_concrete"};
        int[] pos = {-20, -10, 0, 10, 20};
        for (int i = 0; i < 12; i++) {
            Mina m = minas.get(i);
            char parede = i < 5 ? 'n' : i < 10 ? 'l' : 's';
            int p = i < 5 ? pos[i] : i < 10 ? pos[i - 5] : pos[i - 10];
            int x = parede == 'l' ? H : p, z = parede == 'n' ? -H : parede == 's' ? H : p;
            String[] nm = m.nome.split(" • ");
            portaisPatio.add(new Portal(x, z, parede, m.id, RANKS[m.rank].cor() + "§l" + nm[0].toUpperCase(Locale.ROOT), "§f" + nm[1] + "\n§8rank " + RANKS[m.rank].letra(), cores[i]));
        }
        portaisPatio.add(new Portal(10, H, 's', "pvp", "§4§l⚔ MINA PVP ⚔\n§c§lAQUI TEM PVP!", "§cOutros jogadores podem te matar\n§cquem morre §4§lPERDE OS ITENS\n§8rank " + RANKS[minas.get(12).rank].letra(), "minecraft:redstone_block"));
        portaisPatio.add(new Portal(20, H, 's', "enc", "§d§lENCANTAMENTOS", "§7mesas, bigornas e rebolo", "minecraft:amethyst_block"));
        portaisPatio.add(new Portal(-H, 0, 'o', "saida", "§a§lSAÍDA", "§7volta para o spawn", "minecraft:emerald_block"));

        criarMundo();
        if (mundo == null) { getLogger().severe("Nao consegui criar o mundo da mineracao."); return; }
        if (getConfig().getInt("construido", 0) < VERSAO_CONSTRUCAO) {
            int v = getConfig().getInt("construido", 0);
            construirTudo(v);
            getConfig().set("construido", VERSAO_CONSTRUCAO);
            saveConfig();
        }
        for (int i = 0; i < minas.size(); i++) { Mina m = minas.get(i); Bukkit.getScheduler().runTaskLater(this, () -> refazer(m), 40L + 30L * i); }
        getServer().getPluginManager().registerEvents(this, this);
        golem = new GolemMina(this);
        getServer().getPluginManager().registerEvents(golem, this);
        if (getServer().getPluginManager().getPlugin("GriefPrevention") != null) {
            try { getServer().getPluginManager().registerEvents(new SemTerreno(), this); }
            catch (Throwable t) { getLogger().warning("GriefPrevention sem o evento de terreno: " + t); }
        }
        long ms = reset * 60_000L;
        resetMs = ms;
        Bukkit.getScheduler().runTaskTimer(this, this::barra, 40L, 30L);
        carregarTop();
        Bukkit.getScheduler().runTaskTimer(this, this::avisoDourada, 200L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, this::atualizarPainel, 100L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { consertarBigornas(); consertarPortais(); }, 600L, 6000L);
        Bukkit.getScheduler().runTaskTimer(this, this::varrerItens, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long agora = System.currentTimeMillis();
            for (Mina m : minas) {
                if (m.refazendo || m.quebrados == 0) continue;
                if (m.quebrados >= Mina.volume() * pct || agora - m.ultimoReset >= ms) { refazer(m); continue; }
                avisarReset(m, (ms - (agora - m.ultimoReset)) / 1000L, m.quebrados * 100.0 / (Mina.volume() * pct));
            }
        }, 400L, 200L);
        Bukkit.getScheduler().runTaskLater(this, this::portalDoSpawn, 200L);
        Bukkit.getScheduler().runTaskTimer(this, this::brilhoTapetes, 60L, 20L);
        Bukkit.getScheduler().runTaskLater(this, this::infosPortais, 160L);
        Bukkit.getScheduler().runTaskTimer(this, this::atualizarInfos, 200L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::sortearChuva, 12000L, 36000L);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) tirarPlacar(p);
    }

    private void M(String id, String nome, int rank, Material icone, Object... mistura) {
        int i = minas.size();
        minas.add(new Mina(id, nome, rank, GRADE_X0 + (i % 4) * GRADE_PASSO, GRADE_Z0 + (i / 4) * GRADE_PASSO, false, icone, mistura));
    }

    private void preco(double v, Material... ms) { for (Material m : ms) precos.put(m, v); }

    Mina mina(String id) { for (Mina m : minas) if (m.id.equals(id)) return m; return null; }

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
        mundo.setSpawnLocation(patio());
    }

    static final class Vazio extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    Location patio() { return new Location(mundo, 0.5, PISO + 1, 14.5, 180, 0); }
    Location salaEnc() { return new Location(mundo, 0.5, PISO + 1, ENC_Z + 8.5, 180, 0); }

    private void por(World w, int x, int y, int z, String b) { w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false); }
    private void por(int x, int y, int z, String b) { por(mundo, x, y, z, b); }

    // ------------------------------------------------------------------ construcao
    private void construirTudo(int versaoAntiga) {
        long t0 = System.currentTimeMillis();
        // tira o patio e os caminhos da versao 1
        if (versaoAntiga == 1) {
            for (int x = -70; x <= 47; x++) for (int z = -1; z <= 1; z++) por(x, 99, z, "minecraft:air");
            for (int i = 0; i + 1 < 6; i++) {
                int x0 = 60 + i * 60 + Mina.RAIO + 1 + Mina.BORDA + 1, x1 = 60 + (i + 1) * 60 - Mina.RAIO - 1 - Mina.BORDA - 1;
                for (int x = x0; x <= x1; x++) for (int z = -1; z <= 1; z++) por(x, 99, z, "minecraft:air");
            }
        }
        if (versaoAntiga == 1) {
            // molduras das minas pequenas (raio 8) da versao 1
            for (int cx : new int[]{60, 120, 180, 240, 300, 360, -80})
                for (int x = cx - 14; x <= cx + 14; x++) for (int z = -14; z <= 14; z++) for (int y = 77; y <= 102; y++) por(x, y, z, "minecraft:air");
        }
        if (versaoAntiga == 2) {
            // minas em fila da versao 2 (x = 60..720, z = 0): some tudo (poco, borda, grade e portal de volta)
            int lim = Mina.RAIO + 1 + Mina.BORDA;
            for (int k = 0; k < 12; k++) {
                int cx = 60 + 60 * k;
                for (int x = cx - lim; x <= cx + lim; x++) for (int z = -lim; z <= lim; z++) for (int y = Mina.TOPO - Mina.FUNDO - 1; y <= Mina.TOPO + 7; y++) por(x, y, z, "minecraft:air");
            }
        }
        tirarEnfeites(mundo);
        for (int x = -H - 3; x <= H + 3; x++) for (int z = -H - 3; z <= H + 3; z++) for (int y = 95; y <= 115; y++) por(x, y, z, "minecraft:air");
        construirPatio();
        for (Portal p : portaisPatio) construirPortal(p);
        construirCentro();
        construirSalaEnc();
        for (Mina m : minas) {
            m.construirMoldura(mundo);
            construirVolta(m);
        }
        getLogger().info("Prisao (Rank Up) construida: patio + " + portaisPatio.size() + " portais + sala de encantamentos + " + minas.size() + " minas (" + (System.currentTimeMillis() - t0) + " ms).");
    }

    private void construirPatio() {
        Random r = new Random(13);
        for (int x = -H; x <= H; x++)
            for (int z = -H; z <= H; z++) {
                for (int y = 95; y < PISO; y++) por(x, y, z, "minecraft:deepslate_bricks");
                double d = Math.sqrt(x * x + z * z);
                String b;
                if (d <= 5.5) b = (x + z) % 2 == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles";
                else if (d <= 6.5) b = "minecraft:chiseled_deepslate";
                else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) b = "minecraft:polished_blackstone_bricks";
                else {
                    int q = r.nextInt(100);
                    b = q < 8 ? "minecraft:cracked_stone_bricks" : q < 12 ? "minecraft:mossy_stone_bricks" : ((Math.floorDiv(x, 3) + Math.floorDiv(z, 3)) % 2 == 0 ? "minecraft:stone_bricks" : "minecraft:polished_andesite");
                }
                por(x, PISO, z, b);
                boolean parede = Math.abs(x) == H || Math.abs(z) == H;
                if (parede) {
                    for (int y = PISO + 1; y <= PISO + 7; y++) {
                        boolean janela = (y == PISO + 3 || y == PISO + 4) && Math.floorMod(x + z, 5) == 0;
                        por(x, y, z, janela ? "minecraft:iron_bars" : y == PISO + 7 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
                    }
                    if ((x + z) % 2 == 0) por(x, PISO + 8, z, "minecraft:stone_brick_wall");
                }
            }
        // torres nos 4 cantos (guardas)
        for (int sx : new int[]{-1, 1})
            for (int sz : new int[]{-1, 1}) {
                int cx = sx * H, cz = sz * H;
                for (int x = cx - 2; x <= cx + 2; x++)
                    for (int z = cz - 2; z <= cz + 2; z++) {
                        for (int y = 95; y <= PISO + 12; y++) por(x, y, z, y >= PISO + 11 && (Math.abs(x - cx) < 2 && Math.abs(z - cz) < 2) && y == PISO + 12 ? "minecraft:air" : "minecraft:deepslate_bricks");
                        if ((Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2) && (x + z) % 2 == 0) por(x, PISO + 13, z, "minecraft:deepslate_brick_wall");
                    }
                por(cx, PISO + 12, cz, "minecraft:soul_campfire[lit=true,signal_fire=false]");
                for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) por(cx + o[0], PISO + 14, cz + o[1], "minecraft:soul_lantern");
            }
        // postes de luz pelo patio (longe dos portais) e luz invisivel
        for (int x = -H + 5; x <= H - 5; x += 10)
            for (int z = -H + 5; z <= H - 5; z += 10) {
                if (Math.abs(x) < 8 && Math.abs(z) < 8) continue;
                por(x, PISO + 1, z, "minecraft:stone_brick_wall");
                por(x, PISO + 2, z, "minecraft:stone_brick_wall");
                por(x, PISO + 3, z, "minecraft:lantern");
            }
        for (int x = -H + 3; x <= H - 3; x += 6) for (int z = -H + 3; z <= H - 3; z += 6) por(x, PISO + 6, z, "minecraft:light[level=15]");
        // celas decorativas no oeste (dos lados da saida)
        for (int sz : new int[]{-1, 1})
            for (int k = 0; k < 2; k++) {
                int z0 = sz * (6 + k * 8);
                for (int x = -H + 1; x <= -H + 5; x++)
                    for (int z = z0 - 2; z <= z0 + 2; z++)
                        for (int y = PISO + 1; y <= PISO + 4; y++) {
                            boolean frente = x == -H + 5, lado = Math.abs(z - z0) == 2, teto = y == PISO + 4;
                            if (teto) por(x, y, z, "minecraft:stone_bricks");
                            else if (frente && !lado) por(x, y, z, "minecraft:iron_bars");
                            else if (lado) por(x, y, z, "minecraft:stone_bricks");
                        }
                por(-H + 2, PISO + 1, z0, "minecraft:red_bed[facing=east,part=foot]");
                por(-H + 1, PISO + 1, z0, "minecraft:red_bed[facing=east,part=head]");
                por(-H + 2, PISO + 3, z0 + 1, "minecraft:iron_chain[axis=y]");
            }
    }

    /** Portal na parede: moldura 5x8 de blackstone, miolo 3x5 de portal, cor do bloco no topo e letreiro dentro do patio. */
    private void construirPortal(Portal p) {
        boolean eixoX = p.parede() == 'n' || p.parede() == 's';
        for (int s = -2; s <= 2; s++)
            for (int y = PISO; y <= PISO + 7; y++) {
                int x = eixoX ? p.x() + s : p.x(), z = eixoX ? p.z() : p.z() + s;
                boolean miolo = Math.abs(s) <= 1 && y >= PISO + 1 && y <= PISO + 5;
                String b = miolo ? "minecraft:nether_portal[axis=" + (eixoX ? "x" : "z") + "]"
                        : y == PISO + 7 && s == 0 ? p.topo()
                        : y == PISO + 6 ? "minecraft:chiseled_polished_blackstone"
                        : "minecraft:polished_blackstone_bricks";
                por(x, y, z, b);
            }
        int dx = p.parede() == 'l' ? -1 : p.parede() == 'o' ? 1 : 0, dz = p.parede() == 'n' ? 1 : p.parede() == 's' ? -1 : 0;
        texto(new Location(mundo, p.x() + 0.5 + dx * 1.2, PISO + 9.2, p.z() + 0.5 + dz * 1.2), p.nome() + "\n" + p.linha2(), 1.0f);
        // tapete na frente (marca onde fica a entrada)
        for (int s = -1; s <= 1; s++) {
            int x = eixoX ? p.x() + s : p.x() + dx, z = eixoX ? p.z() + dz : p.z() + s;
            por(x, PISO, z, "minecraft:polished_blackstone");
        }
    }

    /** Totem do centro + 4 totens de servico (com tapete na frente: pisar tambem funciona, bom para o Bedrock). */
    private void construirCentro() {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) por(x, PISO + 1, z, "minecraft:chiseled_stone_bricks");
        por(0, PISO + 2, 0, "minecraft:polished_deepslate_wall");
        por(0, PISO + 3, 0, "minecraft:polished_deepslate_wall");
        por(0, PISO + 4, 0, "minecraft:gold_block");
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) por(o[0], PISO + 4, o[1], "minecraft:iron_chain[axis=" + (o[0] != 0 ? "x" : "z") + "]");
        icone(new Location(mundo, 0.5, PISO + 6.2, 0.5), Material.DIAMOND_PICKAXE, 1.4f);
        texto(new Location(mundo, 0.5, PISO + 8.6, 0.5), "§8§l⛓ §6§lPRISÃO CORONA §8§l⛓\n§7Minere, venda e suba de rank\n§e/minas §7• §e/rankup §7• §e/vender", 1.3f);
        interacao(new Location(mundo, 0.5, PISO + 1, 0.5), "menu", 2f, 4f);
        // 13/11 (Paulo): as estacoes do patio viraram bancas (como a loja) com um farol de cor atras de cada uma
        banca(-9, 0, 1, 0, "minecraft:yellow_wool", "minecraft:orange_wool", new Material[]{Material.EXPERIENCE_BOTTLE, Material.NETHER_STAR, Material.GOLD_INGOT},
                "§6§l▲ RANK UP ▲\n§fsuba de rank com $\n§7pise no tapete ou clique", "rankup", "minecraft:yellow_glazed_terracotta");
        farol(-12, 0, "minecraft:gold_block", "minecraft:yellow_stained_glass");
        banca(9, 0, -1, 0, "minecraft:lime_wool", "minecraft:white_wool", new Material[]{Material.EMERALD, Material.RAW_GOLD, Material.DIAMOND},
                "§2§l$ VENDER $\n§fvende todo o minério\n§7pise no tapete ou clique", "vender", "minecraft:lime_glazed_terracotta");
        farol(12, 0, "minecraft:emerald_block", "minecraft:lime_stained_glass");
        banca(0, 9, 0, -1, "minecraft:red_wool", "minecraft:white_wool", new Material[]{Material.CHEST, Material.IRON_CHESTPLATE, Material.GOLDEN_APPLE},
                "§c§l✦ KITS ✦\n§fkits da Prisão (24 h)\n§7pise no tapete ou clique", "kits", "minecraft:red_glazed_terracotta");
        farol(0, 12, "minecraft:diamond_block", "minecraft:red_stained_glass");
        // 13/11 (Paulo): picareta so fica forte encantando na mesa (nada de melhorar com $)
        banca(0, -9, 0, 1, "minecraft:purple_wool", "minecraft:magenta_wool", new Material[]{Material.ENCHANTED_BOOK, Material.ENCHANTING_TABLE, Material.LAPIS_LAZULI},
                "§d§l✧ ENCANTAMENTOS ✧\n§fleva para a sala das mesas\n§7pise no tapete ou clique", "enc", "minecraft:magenta_glazed_terracotta");
        farol(0, -12, "minecraft:iron_block", "minecraft:magenta_stained_glass");
        banca(-9, -10, 0, 1);   // loja do patio, de frente para o sul (tapete em -9,-8)
        for (int[] b : new int[][]{{-13, -10}, {-13, -8}}) por(b[0], PISO + 1, b[1], "minecraft:anvil[facing=north]");
        texto(new Location(mundo, -12.5, PISO + 2.6, -8.5), "§7§lBIGORNAS", 0.8f);
    }

    /** Banca da loja: balcao de barris, picaretas e lapis em cima, toldo listrado azul e branco, lanterna e letreiro.
     *  (fx, fz) = para onde a frente olha; o tapete fica 2 blocos na frente. */
    private void banca(int cx, int cz, int fx, int fz) {
        banca(cx, cz, fx, fz, "minecraft:blue_wool", "minecraft:white_wool", new Material[]{Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.LAPIS_LAZULI},
                "§b§l⛏ LOJA ⛏\n§fpicaretas e lápis-lazúli\n§7pise no tapete ou clique", "loja", "minecraft:light_blue_glazed_terracotta");
    }

    /** Banca generica: lona (duas cores listradas), tres mercadorias no balcao, letreiro, acao do clique e bloco do tapete. */
    private void banca(int cx, int cz, int fx, int fz, String lona1, String lona2, Material[] merc, String titulo, String acao, String tapete) {
        int px = -fz, pz = fx;   // lado
        for (int sd = -1; sd <= 1; sd++)
            for (int d = -1; d <= 0; d++)
                for (int y = PISO + 1; y <= PISO + 5; y++) por(cx + sd * px + d * fx, y, cz + sd * pz + d * fz, "minecraft:air");
        for (int sd = -1; sd <= 1; sd++) {
            int x = cx + sd * px, z = cz + sd * pz;
            por(x, PISO + 1, z, sd == 0 ? "minecraft:spruce_planks" : "minecraft:barrel[facing=up]");
            if (sd != 0) {
                por(x, PISO + 2, z, "minecraft:spruce_fence");
                por(x, PISO + 3, z, "minecraft:spruce_fence");
                por(x - fx, PISO + 1, z - fz, "minecraft:spruce_fence");
                por(x - fx, PISO + 2, z - fz, "minecraft:spruce_fence");
                por(x - fx, PISO + 3, z - fz, "minecraft:spruce_fence");
            }
            for (int d = -1; d <= 0; d++) por(x + d * fx, PISO + 4, z + d * fz, (sd + 1) % 2 == 0 ? lona1 : lona2);
        }
        por(cx - fx, PISO + 1, cz - fz, "minecraft:chest[facing=" + face(fx, fz) + "]");
        por(cx - fx, PISO + 3, cz - fz, "minecraft:lantern[hanging=true]");
        // mercadoria deitada no balcao
        for (int sd = -1; sd <= 1; sd++) {
            Material mm = merc[sd + 1];
            Location l = new Location(mundo, cx + sd * px + 0.5, PISO + 2.03, cz + sd * pz + 0.5);
            mundo.spawn(l, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(mm));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) (Math.PI / 2), 1, 0, 0), new Vector3f(0.7f), new AxisAngle4f()));
                d.setPersistent(true);
                d.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "icone");
                d.getPersistentDataContainer().set(kVersao, PersistentDataType.INTEGER, VERSAO_CONSTRUCAO);
            });
        }
        texto(new Location(mundo, cx + 0.5 + fx * 0.6, PISO + 5.4, cz + 0.5 + fz * 0.6), titulo, 1.0f);
        interacao(new Location(mundo, cx + 0.5, PISO + 1, cz + 0.5), acao, 3f, 2.5f);
        por(cx + 2 * fx, PISO, cz + 2 * fz, tapete);
        lojas.add(new int[]{cx + 2 * fx, cz + 2 * fz});
    }

    final List<int[]> lojas = new ArrayList<>();

    /** Farol (beacon) com vidro colorido: feixe de luz ate o ceu atras da banca. */
    private void farol(int x, int z, String base, String vidro) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) por(x + dx, PISO, z + dz, base);
        por(x, PISO + 1, z, "minecraft:beacon");
        por(x, PISO + 2, z, vidro);
        for (int y = PISO + 3; y <= PISO + 12; y++) if (mundo.getBlockAt(x, y, z).getType() == Material.LIGHT) por(x, y, z, "minecraft:air");
    }

    static String face(int fx, int fz) { return fx > 0 ? "east" : fx < 0 ? "west" : fz > 0 ? "south" : "north"; }

    private void totem(int x, int z, Material icone, String nome, String sub, String acao, String bloco, int tx, int tz) {
        por(x, PISO + 1, z, "minecraft:polished_blackstone_bricks");
        por(x, PISO + 2, z, bloco);
        por(x, PISO + 3, z, "minecraft:polished_blackstone_wall");
        icone(new Location(mundo, x + 0.5, PISO + 4.6, z + 0.5), icone, 0.9f);
        texto(new Location(mundo, x + 0.5, PISO + 5.8, z + 0.5), nome + "\n" + sub, 0.9f);
        interacao(new Location(mundo, x + 0.5, PISO + 1, z + 0.5), acao, 1.2f, 3.2f);
        por(tx, PISO, tz, "minecraft:red_glazed_terracotta");
    }

    /** Sala de encantamentos: biblioteca da prisao com 2 mesas completas (15 estantes), 2 bigornas e um rebolo. */
    private void construirSalaEnc() {
        int z0 = ENC_Z - 10, z1 = ENC_Z + 10;
        for (int x = -11; x <= 11; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++)
                for (int y = 95; y <= PISO + 8; y++) {
                    boolean borda = Math.abs(x) == 11 || z == z0 - 1 || z == z1 + 1;
                    String b;
                    if (y < PISO) b = "minecraft:deepslate_bricks";
                    else if (y == PISO) b = borda ? "minecraft:deepslate_bricks" : ((x + z) % 2 == 0 ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks");
                    else if (y == PISO + 8) b = "minecraft:dark_oak_planks";
                    else if (borda) b = (y == PISO + 3 || y == PISO + 4) && Math.abs(x) != 11 ? "minecraft:bookshelf" : (y == PISO + 3 || y == PISO + 4) ? "minecraft:bookshelf" : "minecraft:stone_bricks";
                    else b = "minecraft:air";
                    por(x, y, z, b);
                }
        for (int x = -9; x <= 9; x += 6) for (int z = z0 + 2; z <= z1 - 2; z += 6) por(x, PISO + 7, z, "minecraft:light[level=15]");
        for (int cx : new int[]{-5, 5}) {
            por(cx, PISO + 1, ENC_Z - 2, "minecraft:enchanting_table");
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                    if (dz == 2 && Math.abs(dx) <= 1) continue;   // entrada pela frente (sul)
                    por(cx + dx, PISO + 1, ENC_Z - 2 + dz, "minecraft:bookshelf");
                    if (Math.abs(dx) + Math.abs(dz) < 4) por(cx + dx, PISO + 2, ENC_Z - 2 + dz, "minecraft:bookshelf");
                }
            por(cx, PISO + 4, ENC_Z - 2, "minecraft:lantern[hanging=true]");
            por(cx, PISO + 5, ENC_Z - 2, "minecraft:iron_chain[axis=y]");
            por(cx, PISO + 6, ENC_Z - 2, "minecraft:iron_chain[axis=y]");
            por(cx, PISO + 7, ENC_Z - 2, "minecraft:iron_chain[axis=y]");
        }
        consertarBigornas();
        por(0, PISO + 1, z0 + 1, "minecraft:grindstone[face=floor,facing=south]");
        banca(8, ENC_Z - 7, -1, 0);   // loja da sala, de frente para o oeste (tapete em 6, ENC_Z-7)
        texto(new Location(mundo, -5.5, PISO + 2.8, ENC_Z - 7.5), "§7§lBIGORNAS §8(use à vontade)", 0.8f);
        texto(new Location(mundo, 0.5, PISO + 5.2, ENC_Z - 2 + 0.5), "§d§lSALA DE ENCANTAMENTOS\n§7Lápis + XP nas mesas • bigornas • rebolo", 1.0f);
        // portal de volta ao patio (parede sul)
        construirPortal(new Portal(0, z1 + 1, 's', "patio", "§a§l◀ VOLTAR AO PÁTIO", "§7Prisão Corona", "minecraft:emerald_block"));
    }

    /** Portais sem moldura de obsidiana podem sumir com uma atualizacao de bloco: recoloca o miolo (so em chunk carregado). */
    void consertarPortais() {
        if (mundo == null) return;
        int n = 0;
        List<Portal> todos = new ArrayList<>(portaisPatio);
        todos.add(new Portal(0, ENC_Z + 11, 's', "patio", "", "", ""));
        for (Portal pt : todos) {
            boolean eixoX = pt.parede() == 'n' || pt.parede() == 's';
            for (int s = -1; s <= 1; s++) for (int y = PISO + 1; y <= PISO + 5; y++) n += miolo(mundo, eixoX ? pt.x() + s : pt.x(), y, eixoX ? pt.z() : pt.z() + s, eixoX);
        }
        for (Mina m : minas) for (int s = -1; s <= 1; s++) for (int y = Mina.TOPO + 1; y <= Mina.TOPO + 4; y++) n += miolo(mundo, m.cx + s, y, m.zVolta(), true);
        if (getConfig().getBoolean("portal-spawn.feito", false)) {
            World w = Bukkit.getWorld(getConfig().getString("portal-spawn.mundo", "world"));
            int cx = getConfig().getInt("portal-spawn.x", 15), z = getConfig().getInt("portal-spawn.z", -29), y0 = getConfig().getInt("portal-spawn.y", 122);
            if (w != null) for (int s = -1; s <= 1; s++) for (int y = y0 + 1; y <= y0 + 5; y++) n += miolo(w, cx + s, y, z, true);
        }
        if (n > 0) getLogger().info("Portais consertados: " + n + " blocos.");
    }

    private int miolo(World w, int x, int y, int z, boolean eixoX) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() == Material.NETHER_PORTAL || !b.getType().isAir()) return 0;
        b.setBlockData(Bukkit.createBlockData("minecraft:nether_portal[axis=" + (eixoX ? "x" : "z") + "]"), false);
        return 1;
    }

    void consertarBigornas() {
        if (mundo == null) return;
        por(-8, PISO + 1, ENC_Z + 6, "minecraft:anvil[facing=east]");
        por(8, PISO + 1, ENC_Z + 6, "minecraft:anvil[facing=west]");
        for (int x : new int[]{-8, -6, -4}) por(x, PISO + 1, ENC_Z - 8, "minecraft:anvil[facing=south]");
        if (getConfig().getInt("construido", 0) >= 7)
            for (int[] b : new int[][]{{-13, -10}, {-13, -8}}) por(b[0], PISO + 1, b[1], "minecraft:anvil[facing=north]");
    }

    /** Portal de volta na borda norte de cada mina. */
    /** 13/11 (Paulo): em cada mina, 4 tapetes de VENDER na borda (3x3 de esmeralda): so pisar em cima vende o minerio. */
    static final int[][] TAPETES = {{5, -19}, {0, 19}, {19, 0}, {-19, 0}};   // centro (dx, dz) de cada tapete

    private void tapetesVender(Mina m) {
        for (int[] t : TAPETES) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    por(m.cx + t[0] + dx, Mina.TOPO, m.cz + t[1] + dz, dx == 0 && dz == 0 ? "minecraft:sea_lantern" : "minecraft:emerald_block");
                    por(m.cx + t[0] + dx, Mina.TOPO + 1, m.cz + t[1] + dz, "minecraft:air");
                }
            // 13/11 (Paulo): bem visivel - letreiro grande, moeda em cima e brilho verde (particulas) em volta
            texto(new Location(mundo, m.cx + t[0] + 0.5, Mina.TOPO + 3.2, m.cz + t[1] + 0.5), "§a§l$ VENDA AQUI $\n§fpise no tapete verde\n§7vende todo o minério", 1.5f);
            icone(new Location(mundo, m.cx + t[0] + 0.5, Mina.TOPO + 5.4, m.cz + t[1] + 0.5), Material.EMERALD, 1.2f);
            por(m.cx + t[0], Mina.TOPO + 3, m.cz + t[1], "minecraft:light[level=15]");
        }
    }

    // ------------------------------------------------------------------ chuva de minerio (evento) e placas dos portais
    String chuvaMina;
    long chuvaAte;

    boolean chuva(Mina m) { return m != null && m.id.equals(chuvaMina) && System.currentTimeMillis() < chuvaAte; }

    /** A cada 30 min (com gente na Prisao): uma mina sorteada da minerio em DOBRO por 10 min. */
    void sortearChuva() {
        if (mundo == null || mundo.getPlayers().isEmpty()) return;
        List<Mina> normais = new ArrayList<>();
        int maior = 0;
        for (Player p : mundo.getPlayers()) maior = Math.max(maior, rank(p));
        for (Mina m : minas) if (!m.pvp && m.rank <= maior) normais.add(m);
        if (normais.isEmpty()) return;
        Mina m = normais.get(new Random().nextInt(normais.size()));
        chuvaMina = m.id;
        chuvaAte = System.currentTimeMillis() + 10 * 60_000L;
        for (Player p : mundo.getPlayers()) {
            p.showTitle(Title.title(txt("§b§l⛏ CHUVA DE MINÉRIO!"), txt("§f" + m.nome.split(" • ")[0] + " §7dá minério em §bDOBRO §7por 10 min"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        }
        Bukkit.broadcast(txt("§b§l⛏ CHUVA DE MINÉRIO §bno §f" + m.nome + "§b: minério em dobro por 10 minutos! §e/minas " + m.id));
        getLogger().info("Chuva de minerio no " + m.id);
        atualizarInfos();
    }

    private final Map<String, TextDisplay> infos = new HashMap<>();

    /** Plaquinha embaixo de cada portal de mina no patio: quantos jogadores, quanto ja foi minerado, quando refaz. */
    void infosPortais() {
        if (mundo == null) return;
        for (Entity e : mundo.getEntities())
            if ("info".equals(e.getPersistentDataContainer().get(kEnfeite, PersistentDataType.STRING))) e.remove();
        infos.clear();
        for (Portal pt : portaisPatio) {
            Mina m = mina(pt.acao());
            if (m == null) continue;
            int dx = pt.parede() == 'l' ? -1 : pt.parede() == 'o' ? 1 : 0, dz = pt.parede() == 'n' ? 1 : pt.parede() == 's' ? -1 : 0;
            TextDisplay t = texto(new Location(mundo, pt.x() + 0.5 + dx * 1.8, PISO + 6.3, pt.z() + 0.5 + dz * 1.8), "§7...", 0.7f);
            t.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "info");
            infos.put(m.id, t);
        }
        atualizarInfos();
    }

    void atualizarInfos() {
        if (mundo == null || infos.isEmpty()) return;
        Map<String, Integer> gente = new HashMap<>();
        for (Player p : mundo.getPlayers()) { Mina m = minaDaArea(p.getLocation()); if (m != null) gente.merge(m.id, 1, Integer::sum); }
        long agora = System.currentTimeMillis();
        for (Map.Entry<String, TextDisplay> en : infos.entrySet()) {
            Mina m = mina(en.getKey());
            TextDisplay t = en.getValue();
            if (m == null || t == null || !t.isValid()) continue;
            int pct = (int) Math.min(100, m.quebrados * 100L / Mina.volume());
            long falta = m.quebrados == 0 ? -1 : Math.max(0, resetMs - (agora - m.ultimoReset));
            String l = "§f👤 " + gente.getOrDefault(m.id, 0) + " §8• §7" + pct + "% minerada" + (falta < 0 ? " §8• §acheia" : " §8• §7refaz em §f" + (falta / 60000 + 1) + " min");
            if (chuva(m)) l = "§b§l⛏ CHUVA: MINÉRIO EM DOBRO!\n" + l;
            t.text(txt(l));
        }
    }

    void brilhoTapetes() {
        if (mundo == null) return;
        Set<Mina> comGente = new HashSet<>();
        for (Player p : mundo.getPlayers()) { Mina m = minaDaArea(p.getLocation()); if (m != null) comGente.add(m); }
        for (Mina m : comGente)
            for (int[] t : TAPETES) {
                Location c = new Location(mundo, m.cx + t[0] + 0.5, Mina.TOPO + 1.2, m.cz + t[1] + 0.5);
                mundo.spawnParticle(Particle.HAPPY_VILLAGER, c, 8, 1.0, 0.3, 1.0, 0);
                mundo.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.5, 0), 2, 0.6, 0.6, 0.6, 0.01);
            }
    }

    boolean noTapete(Location l) {
        if (l.getWorld() != mundo || l.getBlockY() != Mina.TOPO + 1) return false;
        Mina m = minaDaArea(l);
        if (m == null) return false;
        int x = l.getBlockX() - m.cx, z = l.getBlockZ() - m.cz;
        for (int[] t : TAPETES) if (Math.abs(x - t[0]) <= 1 && Math.abs(z - t[1]) <= 1) return true;
        return false;
    }

    private void construirVolta(Mina m) {
        tapetesVender(m);
        int z = m.cz - (Mina.RAIO + 1 + Mina.BORDA);
        for (int s = -3; s <= 3; s++) for (int y = Mina.TOPO + 1; y <= Mina.TOPO + 9; y++) por(m.cx + s, y, z, "minecraft:air");
        if (m.pvp) { antessala(m); z = m.zVolta(); }
        for (int s = -2; s <= 2; s++)
            for (int y = Mina.TOPO; y <= Mina.TOPO + 6; y++) {
                boolean miolo = Math.abs(s) <= 1 && y >= Mina.TOPO + 1 && y <= Mina.TOPO + 4;
                por(m.cx + s, y, z, miolo ? "minecraft:nether_portal[axis=x]" : y == Mina.TOPO + 6 && s == 0 ? "minecraft:emerald_block" : "minecraft:polished_blackstone_bricks");
            }
        if (!m.pvp) {
            texto(new Location(mundo, m.cx + 0.5, Mina.TOPO + 8, z + 1.5), "§6§l" + m.nome.split(" • ")[0].toUpperCase(Locale.ROOT) + "\n§f" + m.nome.split(" • ")[1] + "\n§a◀ portal: volta ao pátio", 1.0f);
            return;
        }
        texto(new Location(mundo, m.cx + 0.5, Mina.TOPO + 8, z + 1.5), "§a§l◀ VOLTAR AO PÁTIO\n§7Antessala da Mina PvP • §aárea segura", 1.0f);
        // avisos vermelhos nos 4 lados da borda e postes de redstone nas quinas
        int b = Mina.RAIO + 3;
        for (int[] o : new int[][]{{0, b}, {b, 0}, {-b, 0}, {0, -b + 1}})
            if (!(o[0] == 0 && o[1] == -b + 1)) texto(new Location(mundo, m.cx + o[0] + 0.5, Mina.TOPO + 4, m.cz + o[1] + 0.5), "§4§l⚔ ZONA PVP ⚔\n§cjogadores podem te atacar aqui", 1.1f);
        int q = Mina.RAIO + 1 + Mina.BORDA;
        for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            por(m.cx + sx * q, Mina.TOPO + 1, m.cz + sz * q, "minecraft:redstone_block");
            por(m.cx + sx * q, Mina.TOPO + 2, m.cz + sz * q, "minecraft:red_banner");
        }
    }

    /** Antessala da Mina PvP (13/11, Paulo): sala segura ao norte, portao com aviso e ponte ate a borda da mina. */
    private void antessala(Mina m) {
        int cx = m.cx, cz = m.cz, T = Mina.TOPO, zf = -Mina.ANTESSALA_Z, zp = -23;   // zf = fundo (portal), zp = parede do portao
        for (int x = -6; x <= 6; x++)
            for (int z = zf; z <= -22; z++) {
                boolean ponte = z == -22, dentro = Math.abs(x) <= 6 && z <= zp;
                if (ponte && Math.abs(x) > 3) continue;
                for (int y = T - 3; y < T; y++) por(cx + x, y, cz + z, "minecraft:stone_bricks");
                por(cx + x, T, cz + z, x == 0 && z > zf + 1 ? "minecraft:red_nether_bricks" : (x + z) % 2 == 0 ? "minecraft:polished_blackstone" : "minecraft:polished_blackstone_bricks");
                for (int y = T + 1; y <= T + 7; y++) por(cx + x, y, cz + z, "minecraft:air");
                if (ponte && Math.abs(x) == 3) por(cx + x, T + 1, cz + z, "minecraft:nether_brick_fence");
                if (!dentro) continue;
                boolean parede = Math.abs(x) == 6 || z == zf || z == zp;
                boolean portao = z == zp && Math.abs(x) <= 1;
                if (parede && !portao) for (int y = T + 1; y <= T + 5; y++) por(cx + x, y, cz + z, y == T + 5 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks");
                if (portao) por(cx + x, T + 5, cz + z, "minecraft:chiseled_polished_blackstone");
                if (portao) por(cx + x, T + 4, cz + z, x == 0 ? "minecraft:redstone_block" : "minecraft:polished_blackstone_bricks");
            }
        // lanternas e estandartes vermelhos dos lados do portao, lanternas nas paredes
        for (int sx : new int[]{-2, 2}) {
            por(cx + sx, T + 1, cz + zp + 1, "minecraft:red_banner[rotation=8]");
            por(cx + sx, T + 3, cz + zp + 1, "minecraft:lantern[hanging=false]");
        }
        for (int z = zf + 3; z < zp; z += 4) for (int sx : new int[]{-5, 5}) por(cx + sx, T + 3, cz + z, "minecraft:soul_lantern");
        // bancos (degraus) para esperar
        for (int z = zf + 4; z <= zf + 8; z++) { por(cx - 5, T + 1, cz + z, "minecraft:polished_blackstone_stairs[facing=west]"); por(cx + 5, T + 1, cz + z, "minecraft:polished_blackstone_stairs[facing=east]"); }
        texto(new Location(mundo, cx + 0.5, T + 4.2, cz + zp + 1.6), "§4§l⚔ PORTÃO DA MINA PVP ⚔\n§cPassou daqui: §4§lTEM PVP\n§cquem morre perde os itens", 1.2f);
        texto(new Location(mundo, cx + 0.5, T + 3.2, cz - 29 + 0.5), "§a§l✔ ÁREA SEGURA\n§7Aqui ninguém te ataca.\n§7Prepare-se e entre pelo portão §c▼", 1.0f);
    }

    // ------------------------------------------------------------------ enfeites (textos, icones, cliques)
    TextDisplay texto(Location l, String t, float esc) {
        return l.getWorld().spawn(l, TextDisplay.class, d -> {
            d.text(txt(t));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(140, 0, 0, 0));
            d.setPersistent(true);
            d.setViewRange(0.8f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(esc), new AxisAngle4f()));
            d.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "texto");
            d.getPersistentDataContainer().set(kVersao, PersistentDataType.INTEGER, VERSAO_CONSTRUCAO);
        });
    }

    void icone(Location l, Material m, float esc) {
        l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setPersistent(true);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(esc), new AxisAngle4f()));
            d.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "icone");
            d.getPersistentDataContainer().set(kVersao, PersistentDataType.INTEGER, VERSAO_CONSTRUCAO);
        });
    }

    void interacao(Location l, String acao, float larg, float alt) {
        l.getWorld().spawn(l, Interaction.class, i -> {
            i.setInteractionWidth(larg);
            i.setInteractionHeight(alt);
            i.setResponsive(true);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "clique");
            i.getPersistentDataContainer().set(kVersao, PersistentDataType.INTEGER, VERSAO_CONSTRUCAO);
            i.getPersistentDataContainer().set(kAcao, PersistentDataType.STRING, acao);
        });
    }

    /** Enfeite de uma construcao antiga (ficou num chunk que nao estava carregado quando refiz tudo). */
    boolean enfeiteVelho(Entity e) {
        PersistentDataContainer c = e.getPersistentDataContainer();
        if (!c.has(kEnfeite, PersistentDataType.STRING)) return false;
        Integer v = c.get(kVersao, PersistentDataType.INTEGER);
        return v == null || v != VERSAO_CONSTRUCAO;
    }

    @EventHandler
    public void chunkCarregou(org.bukkit.event.world.EntitiesLoadEvent e) {
        if (e.getWorld() != mundo) return;
        int n = 0;
        for (Entity en : e.getEntities()) if (enfeiteVelho(en)) { en.remove(); n++; }
        if (n > 0) getLogger().info("Enfeites velhos tirados ao carregar o chunk: " + n);
    }

    void tirarEnfeites(World w) {
        int n = 0;
        for (Entity e : w.getEntities()) if (e.getPersistentDataContainer().has(kEnfeite, PersistentDataType.STRING)) { e.remove(); n++; }
        if (n > 0) getLogger().info("Enfeites antigos tirados: " + n);
    }

    private TextDisplay painelTop;

    void atualizarPainel() {
        if (mundo == null) return;
        if (painelTop == null || !painelTop.isValid()) {
            for (Entity e : mundo.getNearbyEntities(new Location(mundo, 0.5, PISO + 4, -15.5), 2, 3, 2))
                if (e instanceof TextDisplay t && "top".equals(t.getPersistentDataContainer().get(kEnfeite, PersistentDataType.STRING))) painelTop = t;
            if (painelTop == null || !painelTop.isValid()) {
                if (!mundo.isChunkLoaded(0, -1)) return;
                painelTop = texto(new Location(mundo, 0.5, PISO + 4.5, -15.5), "", 0.9f);
                painelTop.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "top");
            }
        }
        List<String> l = linhasTop(5);
        StringBuilder sb = new StringBuilder("§6§l⛏ TOP DA PRISÃO ⛏");
        if (l.isEmpty()) sb.append("\n§7ninguém ainda — seja o primeiro!");
        for (String s : l) sb.append("\n").append(s);
        int hd = getConfig().getInt("hora-dourada", 20);
        if (hd >= 0) sb.append(horaDourada() ? "\n§6§l✦ HORA DOURADA: venda x2!" : "\n§8hora dourada: todo dia às " + hd + "h (venda x2)");
        painelTop.text(txt(sb.toString()));
    }

    // ------------------------------------------------------------------ portal no spawn principal
    void portalDoSpawn() {
        if (getConfig().getBoolean("portal-spawn.feito", false) || !getConfig().getBoolean("portal-spawn.ativo", true)) return;
        World w = Bukkit.getWorld(getConfig().getString("portal-spawn.mundo", "world"));
        if (w == null) return;
        int y0 = getConfig().getInt("portal-spawn.y", 122);
        // lugares em ordem de preferencia (x,z do centro); o primeiro livre ganha o portal
        List<String> lugares = getConfig().getStringList("portal-spawn.lugares");
        if (lugares.isEmpty()) lugares = List.of("16,-23", "-4,-23", "16,-20", "-4,-20");
        int cx = 0, z = 0;
        boolean achou = false;
        StringBuilder motivos = new StringBuilder();
        for (String lg : lugares) {
            String[] xz = lg.split(",");
            int tx, tz;
            try { tx = Integer.parseInt(xz[0].trim()); tz = Integer.parseInt(xz[1].trim()); } catch (Exception ex) { continue; }
            String m = ocupado(w, tx, tz, y0);
            if (m == null) { cx = tx; z = tz; achou = true; break; }
            motivos.append(" [").append(lg).append(": ").append(m).append("]");
        }
        if (!achou) {
            getLogger().warning("Portal da Prisao no spawn NAO construido: nenhum lugar livre." + motivos);
            return;
        }
        for (int s = -2; s <= 2; s++)
            for (int y = 0; y <= 7; y++) {
                boolean miolo = Math.abs(s) <= 1 && y >= 1 && y <= 5;
                String b = miolo ? "minecraft:nether_portal[axis=x]" : y == 7 && s == 0 ? "minecraft:gold_block" : y == 6 ? "minecraft:chiseled_polished_blackstone"
                        : (Math.abs(s) == 2 && y % 2 == 0) ? "minecraft:deepslate_bricks" : "minecraft:polished_blackstone_bricks";
                por(w, cx + s, y0 + y, z, b);
            }
        for (int s : new int[]{-3, 3}) {
            por(w, cx + s, y0 + 1, z, "minecraft:iron_bars");
            por(w, cx + s, y0 + 2, z, "minecraft:iron_bars");
            por(w, cx + s, y0 + 3, z, "minecraft:stone_brick_wall");
            por(w, cx + s, y0 + 4, z, "minecraft:soul_lantern");
        }
        por(w, cx, y0 + 8, z, "minecraft:iron_chain[axis=y]");
        w.spawn(new Location(w, cx + 0.5, y0 + 9.4, z + 1.1), TextDisplay.class, d -> {
            d.text(txt("§8§l⛓ §6§lPRISÃO • RANK UP §8§l⛓\n§712 minas • Mina PvP • kits e encantamentos\n§fdinheiro próprio e mochila própria"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(140, 0, 0, 0));
            d.setPersistent(true);
            d.setViewRange(0.8f);
            d.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "spawn");
        });
        getConfig().set("portal-spawn.x", cx);
        getConfig().set("portal-spawn.z", z);
        getConfig().set("portal-spawn.feito", true);
        saveConfig();
        getLogger().info("Portal da Prisao construido no spawn principal em " + cx + "," + y0 + "," + z + ".");
    }

    /** null = livre; senao o motivo. Pede piso inteiro e nada em cima (7 x 5 em volta, 9 de altura). */
    private String ocupado(World w, int cx, int z, int y0) {
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int zz = z - 2; zz <= z + 2; zz++) {
                if (w.getBlockAt(x, y0, zz).getType().isAir()) return "sem piso em " + x + "," + y0 + "," + zz;
                for (int y = y0 + 1; y <= y0 + 9; y++) {
                    Material t = w.getBlockAt(x, y, zz).getType();
                    if (!t.isAir() && t != Material.LIGHT) return t + " em " + x + "," + y + "," + zz;
                }
            }
        for (Entity e : w.getNearbyEntities(new Location(w, cx + 0.5, y0 + 5, z + 0.5), 3.5, 5, 2.5))
            if (!(e instanceof Player)) return "entidade " + e.getType();
        return null;
    }

    boolean noPortalDoSpawn(Location l) {
        if (!getConfig().getBoolean("portal-spawn.feito", false)) return false;
        if (!l.getWorld().getName().equals(getConfig().getString("portal-spawn.mundo", "world"))) return false;
        int cx = getConfig().getInt("portal-spawn.x", 15), z = getConfig().getInt("portal-spawn.z", -29), y0 = getConfig().getInt("portal-spawn.y", 122);
        return Math.abs(l.getBlockX() - cx) <= 1 && l.getBlockZ() == z && l.getBlockY() >= y0 + 1 && l.getBlockY() <= y0 + 5;
    }

    // ------------------------------------------------------------------ reset das minas
    void refazer(Mina m) {
        if (mundo == null || m.refazendo) return;
        m.refazendo = true;
        for (Player p : mundo.getPlayers())
            if (m.dentroDoPoco(p.getLocation())) {
                // sobe para cima da mina no mesmo lugar (a camada de cima e a primeira a ser refeita), em vez de voltar ao portal
                Location l = p.getLocation();
                p.teleport(new Location(mundo, l.getX(), Mina.TOPO + 1.1, l.getZ(), l.getYaw(), l.getPitch()));
                p.sendActionBar(txt("§e⛏ O " + m.nome + " §eestá sendo refeito!"));
            }
        List<int[]> pos = new ArrayList<>(Mina.volume());
        for (int y = Mina.TOPO; y > Mina.TOPO - Mina.FUNDO; y--)
            for (int x = -Mina.RAIO; x <= Mina.RAIO; x++)
                for (int z = -Mina.RAIO; z <= Mina.RAIO; z++) pos.add(new int[]{m.cx + x, y, m.cz + z});
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
                m.avisado = -1;
                m.refazendo = false;
                for (Player p : mundo.getPlayers())
                    if (m.naArea(p.getLocation())) p.sendActionBar(txt("§a⛏ O " + m.nome + " §afoi refeito!"));
            }
        }, 1L, 1L);
    }

    /** 13/11 (Paulo): avisa quem esta na mina quantos minutos faltam para ela ser refeita (5 min, 1 min e 10 s antes). */
    void avisarReset(Mina m, long falta, double pctAteEsvaziar) {
        int[] marcas = {300, 60, 10};
        for (int marca : marcas) {
            if (falta > marca || falta <= marca - 10 || m.avisado == marca) continue;
            m.avisado = marca;
            String quando = marca >= 60 ? (marca / 60) + (marca == 60 ? " minuto" : " minutos") : marca + " segundos";
            for (Player p : mundo.getPlayers())
                if (m.naArea(p.getLocation())) {
                    p.sendMessage(txt("§e⏱ Reset da mina §f" + m.nome.split(" • ")[0] + " §eem §f§l" + quando + "§e!"
                            + (marca == 10 ? " §7(quem estiver no poço sobe para cima da mina)" : "")));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, marca == 10 ? 2f : 1.2f);
                }
        }
    }

    /** Texto do tempo ate o reset, para quem chega na mina. */
    String tempoReset(Mina m) {
        if (m.refazendo) return "§eestá sendo refeita agora";
        if (m.quebrados == 0) return "§acheia §7(o relógio de §f" + (resetMs / 60000) + " min §7começa no 1º bloco quebrado)";
        long falta = Math.max(0, resetMs - (System.currentTimeMillis() - m.ultimoReset)) / 1000L;
        return "§7refaz em §f§l" + (falta / 60) + " min " + String.format("%02d", falta % 60) + " s §7(ou antes, se esvaziar)";
    }

    Mina minaDoBloco(Block b) { for (Mina m : minas) if (m.noMiolo(b)) return m; return null; }

    Mina minaDaArea(Location l) { for (Mina m : minas) if (m.naArea(l)) return m; return null; }

    // ------------------------------------------------------------------ rank e dinheiro proprio
    int rank(Player p) {
        Integer r = p.getPersistentDataContainer().get(kRank, PersistentDataType.INTEGER);
        return r == null ? 0 : Math.max(0, Math.min(RANKS.length - 1, r));
    }

    int prestigio(Player p) {
        Integer v = p.getPersistentDataContainer().get(kPrest, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    /** Bonus de venda do prestigio: +20% por estrela. */
    static double multPrest(int pr) { return 1.0 + 0.2 * pr; }

    static String estrelas(int pr) { return pr <= 0 ? "" : "§e★" + (pr > 1 ? pr : "") + " "; }

    static final long CUSTO_PRESTIGIO = 1_000_000;
    private final Map<UUID, Long> confirmaPrest = new HashMap<>();

    void fazerPrestigio(Player p) {
        int r = rank(p);
        if (r + 1 < RANKS.length) { p.sendMessage(txt("§7O prestígio libera no rank máximo " + nomeRank(RANKS.length - 1) + "§7. Você está no " + nomeRank(r) + "§7.")); return; }
        long tem = dinheiro(p);
        if (tem < CUSTO_PRESTIGIO) { p.sendMessage(txt("§cO prestígio custa " + din(CUSTO_PRESTIGIO) + "§c. Faltam " + din(CUSTO_PRESTIGIO - tem) + "§c.")); return; }
        Long t = confirmaPrest.get(p.getUniqueId());
        long agora = System.currentTimeMillis();
        int pr = prestigio(p);
        if (t == null || agora - t > 15000) {
            confirmaPrest.put(p.getUniqueId(), agora);
            p.sendMessage(txt("§e§l★ PRESTÍGIO §8» §7Você volta para o rank §fA§7, o dinheiro zera e ganha §e★" + (pr + 1)
                    + " §7com §a+" + Math.round((multPrest(pr + 1) - 1) * 100) + "% §7na venda §lpara sempre§7. Digite §e/prestigio §7de novo em 15 s para confirmar."));
            return;
        }
        confirmaPrest.remove(p.getUniqueId());
        setDinheiro(p, 0);
        p.getPersistentDataContainer().set(kRank, PersistentDataType.INTEGER, 0);
        p.getPersistentDataContainer().set(kPrest, PersistentDataType.INTEGER, pr + 1);
        p.showTitle(Title.title(txt("§e§l★ PRESTÍGIO " + (pr + 1) + " ★"), txt("§7venda §a+" + Math.round((multPrest(pr + 1) - 1) * 100) + "% §7para sempre"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        Bukkit.broadcast(txt("§e§l★ §6" + p.getName() + " §efez o §lPRESTÍGIO " + (pr + 1) + " §ena Prisão! §7(voltou para o rank A com bônus para sempre)"));
        getLogger().info(p.getName() + " fez o prestigio " + (pr + 1));
        registrarTop(p, 0);
        irCentro(p);
    }

    // ---------- recompensa diaria
    long valorDiaria(Player p) {
        return Math.round(150L * (rank(p) + 1) * multPrest(prestigio(p)) * (100 + BONUS_VIP[vip(p)]) / 100.0);
    }

    long faltaDiaria(Player p) {
        Long ult = p.getPersistentDataContainer().get(kDiaria, PersistentDataType.LONG);
        if (ult == null) return 0;
        return Math.max(0, ult + 20 * 3600_000L - System.currentTimeMillis());
    }

    void diaria(Player p) {
        if (p.getWorld() != mundo) { p.sendMessage(txt("§cA recompensa diária sai dentro da Prisão (§e/minas§c).")); return; }
        long f = faltaDiaria(p);
        if (f > 0) { p.sendMessage(txt("§eSua recompensa diária volta em " + (f / 3600000) + "h " + ((f / 60000) % 60) + "min.")); return; }
        long v = valorDiaria(p);
        setDinheiro(p, dinheiro(p) + v);
        p.getPersistentDataContainer().set(kDiaria, PersistentDataType.LONG, System.currentTimeMillis());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        p.sendMessage(txt("§a§l$ Recompensa diária: §f+" + din(v) + " §7(sobe com o rank, prestígio e VIP) §8• saldo " + din(dinheiro(p))));
    }

    boolean autoVenda(Player p) {
        Byte b = p.getPersistentDataContainer().get(kAuto, PersistentDataType.BYTE);
        return b == null || b == 1;   // VIP comeca com ela ligada
    }

    long dinheiro(Player p) {
        Long v = p.getPersistentDataContainer().get(kDinheiro, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    void setDinheiro(Player p, long v) { p.getPersistentDataContainer().set(kDinheiro, PersistentDataType.LONG, Math.max(0, v)); }

    static int vip(Player p) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        Integer n = c.get(VIP_NIVEL, PersistentDataType.INTEGER);
        Long ate = c.get(VIP_ATE, PersistentDataType.LONG);
        if (n == null || n <= 0 || ate == null || ate < System.currentTimeMillis()) return 0;
        return Math.min(n, BONUS_VIP.length - 1);
    }

    static String fmt(long v) { return String.format(Locale.ROOT, "%,d", v).replace(',', '.'); }

    static String din(long v) { return "§2$" + fmt(v); }

    String nomeRank(int r) { return RANKS[r].cor() + "[" + RANKS[r].letra() + "] " + RANKS[r].nome(); }

    // ------------------------------------------------------------------ eventos do mundo
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
        if (t == Material.STONE || t == Material.DEEPSLATE || t == Material.COBBLESTONE || t == Material.COBBLED_DEEPSLATE || t == Material.ANDESITE
                || t == Material.NETHERRACK || t == Material.BLACKSTONE) e.setDropItems(false);
        // 13/11: XP de cada minerio (a picareta so fica forte encantando, entao precisa de XP para as mesas)
        e.setExpToDrop(xpDe(t));
        // 13/11 (Paulo: "comeca a minerar e teleporta pra tras do nada"): o relogio do reset so comeca no 1o bloco quebrado.
        // Antes, mina parada ha mais de 20 min resetava (e tirava o jogador do poco) logo no primeiro bloco.
        if (m.quebrados == 0) m.ultimoReset = System.currentTimeMillis();
        m.quebrados++;
        missoes.somar(p, Missoes.Tipo.QUEBRAR, 1);
        if (!(t == Material.STONE || t == Material.DEEPSLATE || t == Material.COBBLESTONE || t == Material.COBBLED_DEEPSLATE || t == Material.ANDESITE
                || t == Material.NETHERRACK || t == Material.BLACKSTONE)) missoes.somar(p, Missoes.Tipo.MINERIO, 1);
        if (!m.pvp && java.util.concurrent.ThreadLocalRandom.current().nextInt(700) == 0) {
            long v = 80L * (rank(p) + 1);
            setDinheiro(p, dinheiro(p) + v);
            p.sendMessage(txt("§6§l✦ Tesouro escondido! §eVocê achou " + din(v) + " §eno meio das pedras."));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0.2);
        }
    }

    static int xpDe(Material t) {
        return switch (t) {
            case COAL_ORE, DEEPSLATE_COAL_ORE, COPPER_ORE, DEEPSLATE_COPPER_ORE, NETHER_QUARTZ_ORE, NETHER_GOLD_ORE -> 1;
            case IRON_ORE, DEEPSLATE_IRON_ORE, GOLD_ORE, DEEPSLATE_GOLD_ORE, REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, LAPIS_ORE, DEEPSLATE_LAPIS_ORE, GILDED_BLACKSTONE -> 2;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 4;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 5;
            case ANCIENT_DEBRIS -> 6;
            case GOLD_BLOCK, DIAMOND_BLOCK, EMERALD_BLOCK -> 8;
            default -> 0;
        };
    }

    /** Tag do rank no chat de quem esta na Prisao: [B] Nome. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void chat(io.papermc.paper.event.player.AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (mundo == null || p.getWorld() != mundo) return;
        Integer r = p.getPersistentDataContainer().get(kRank, PersistentDataType.INTEGER);
        int ri = r == null ? 0 : Math.max(0, Math.min(RANKS.length - 1, r));
        Integer pr = p.getPersistentDataContainer().get(kPrest, PersistentDataType.INTEGER);
        Component tag = txt(estrelas(pr == null ? 0 : pr) + "§8[" + RANKS[ri].cor() + RANKS[ri].letra() + "§8] ");
        io.papermc.paper.chat.ChatRenderer antes = e.renderer();
        e.renderer((src, nome, msg, quem) -> tag.append(antes.render(src, nome, msg, quem)));
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
            if (chuva(minaDoBloco(e.getBlock()))) m.setAmount(Math.min(m.getMaxStackSize(), m.getAmount() * 2));
            Map<Integer, ItemStack> sobra = p.getInventory().addItem(m.clone());
            if (sobra.isEmpty()) { i.remove(); continue; }
            cheio = true;
            it.setItemStack(sobra.values().iterator().next());
        }
        if (cheio) {
            if (vip(p) > 0 && autoVenda(p)) {
                vender(p, true);
                // o que sobrou no chao volta para o inventario
                for (Iterator<Item> i = e.getItems().iterator(); i.hasNext(); ) {
                    Item it = i.next();
                    Map<Integer, ItemStack> sb = p.getInventory().addItem(it.getItemStack().clone());
                    if (sb.isEmpty()) i.remove();
                    else it.setItemStack(sb.values().iterator().next());
                }
            } else p.sendActionBar(txt("§c⛏ Inventário cheio! Use §e/vender" + (vip(p) > 0 ? " §8(ou ligue a venda automática no §e/minas§8)" : " §8(VIP tem venda automática)")));
        }
    }

    ItemStack marcar(ItemStack s) {
        ItemMeta im = s.getItemMeta();
        if (im == null) return s;
        im.getPersistentDataContainer().set(kMinerado, PersistentDataType.BYTE, (byte) 1);
        im.lore(List.of(txt("§8⛏ Minério da Prisão §7— §e/vender")));
        s.setItemMeta(im);
        return s;
    }

    boolean marcado(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return false;
        return s.getItemMeta().getPersistentDataContainer().has(kMinerado, PersistentDataType.BYTE);
    }

    /** Item que nasceu na Prisao (kits, picaretas): nao pode sair dela. */
    ItemStack daPrisao(ItemStack s) {
        ItemMeta im = s.getItemMeta();
        if (im == null) return s;
        im.getPersistentDataContainer().set(kPrisao, PersistentDataType.BYTE, (byte) 1);
        List<Component> l = im.hasLore() && im.lore() != null ? new ArrayList<>(im.lore()) : new ArrayList<>();
        l.add(txt("§8⛓ Item da Prisão (não sai daqui)"));
        im.lore(l);
        s.setItemMeta(im);
        return s;
    }

    boolean ehDaPrisao(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return false;
        PersistentDataContainer c = s.getItemMeta().getPersistentDataContainer();
        return c.has(kPrisao, PersistentDataType.BYTE) || c.has(kMinerado, PersistentDataType.BYTE);
    }

    private final Map<UUID, Long> trocouMundo = new HashMap<>();
    static final Set<String> MUNDOS_OUTRA_MOCHILA = Set.of("world", "world_nether", "world_the_end", "plots", "masmorras_mundo", "masmorras_nether", "masmorras_end");

    @EventHandler(priority = EventPriority.MONITOR)
    public void trocou(PlayerChangedWorldEvent e) { trocouMundo.put(e.getPlayer().getUniqueId(), System.currentTimeMillis()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void logou(PlayerJoinEvent e) { trocouMundo.put(e.getPlayer().getUniqueId(), System.currentTimeMillis()); }

    @EventHandler
    public void saiu(PlayerQuitEvent e) { placares.remove(e.getPlayer().getUniqueId()); trocouMundo.remove(e.getPlayer().getUniqueId()); espera.remove(e.getPlayer().getUniqueId()); naPvp.remove(e.getPlayer().getUniqueId()); }

    /** Pedido do Paulo (13/11): nenhum item do /minas entra em outro mundo. A mochila da Prisao (Extras) ja separa;
     *  isto e a rede de seguranca: fora da Prisao, item marcado some (inventario, cursor e bau do fim). */
    void varrerItens() {
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            // 13/11 (correcao): so onde a mochila e outra (survival/masmorras). No lobby do /login e nos minigames o
            // inventario da Prisao ainda esta na mao do jogador e NAO pode ser mexido.
            String wn = p.getWorld().getName();
            if (p.getWorld() == mundo || !MUNDOS_OUTRA_MOCHILA.contains(wn)) continue;
            Long t = trocouMundo.get(p.getUniqueId());
            if (t == null || agora - t < 10000) continue;   // a troca de mochila ainda pode estar acontecendo
            // nada e apagado: vai para um guarda-volumes e volta para o jogador quando ele entra na Prisao
            List<ItemStack> tirados = new ArrayList<>();
            ItemStack[] c = p.getInventory().getContents();
            for (int i = 0; i < c.length; i++) if (ehDaPrisao(c[i])) { tirados.add(c[i]); c[i] = null; }
            if (!tirados.isEmpty()) p.getInventory().setContents(c);
            if (ehDaPrisao(p.getItemOnCursor())) { tirados.add(p.getItemOnCursor()); p.setItemOnCursor(null); }
            ItemStack[] ec = p.getEnderChest().getContents();
            boolean mudouEc = false;
            for (int i = 0; i < ec.length; i++) if (ehDaPrisao(ec[i])) { tirados.add(ec[i]); ec[i] = null; mudouEc = true; }
            if (mudouEc) p.getEnderChest().setContents(ec);
            if (!tirados.isEmpty()) {
                guardar(p, tirados);
                int n = 0;
                for (ItemStack it : tirados) n += it.getAmount();
                getLogger().info("Itens da Prisao fora dela guardados de " + p.getName() + ": " + n + " (mundo " + p.getWorld().getName() + ")");
                p.sendMessage(txt("§8⛓ §7Itens da Prisão não saem dela: §f" + n + " §7foram guardados e voltam quando você entrar na Prisão."));
            }
        }
    }

    private NamespacedKey kGuardados() { return new NamespacedKey(this, "guardados"); }

    void guardar(Player p, List<ItemStack> novos) {
        List<ItemStack> todos = new ArrayList<>();
        byte[] b = p.getPersistentDataContainer().get(kGuardados(), PersistentDataType.BYTE_ARRAY);
        if (b != null) try { for (ItemStack it : ItemStack.deserializeItemsFromBytes(b)) if (it != null && !it.getType().isAir()) todos.add(it); } catch (Exception ignored) {}
        todos.addAll(novos);
        p.getPersistentDataContainer().set(kGuardados(), PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(todos));
    }

    /** Reembolso de quem perdeu itens no bug da varredura (13/11): libera de novo todos os kits e da um dinheiro.
     *  Lista no config (reembolso: [nick, ...]); cada nick sai da lista quando recebe. */
    void reembolso(Player p) {
        List<String> l = new ArrayList<>(getConfig().getStringList("reembolso"));
        if (!l.removeIf(n -> n.equalsIgnoreCase(p.getName()))) return;
        getConfig().set("reembolso", l);
        saveConfig();
        for (Kit k : KITS) p.getPersistentDataContainer().remove(new NamespacedKey(this, "kit_" + k.id()));
        long v = 1000;
        setDinheiro(p, dinheiro(p) + v);
        p.sendMessage(txt("§a§l⛓ Desculpe! §fUm erro sumiu com itens da sua mochila da Prisão ao entrar no servidor. §7Já foi corrigido: "
                + "seus §bkits §7foram liberados de novo §8(/kit) §7e você ganhou " + din(v) + "§7."));
        getLogger().info("Reembolso para " + p.getName() + ": kits liberados + $" + v);
    }

    /** Devolve o que foi guardado (ao entrar na Prisao). */
    void devolverGuardados(Player p) {
        byte[] b = p.getPersistentDataContainer().get(kGuardados(), PersistentDataType.BYTE_ARRAY);
        if (b == null) return;
        p.getPersistentDataContainer().remove(kGuardados());
        int n = 0;
        try {
            for (ItemStack it : ItemStack.deserializeItemsFromBytes(b)) {
                if (it == null || it.getType().isAir()) continue;
                n += it.getAmount();
                p.getInventory().addItem(it).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            }
        } catch (Exception ex) { getLogger().warning("Guardados de " + p.getName() + ": " + ex); }
        if (n > 0) {
            p.sendMessage(txt("§a⛓ Seus itens da Prisão voltaram: §f" + n));
            getLogger().info("Guardados devolvidos para " + p.getName() + ": " + n);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void semBauDoFim(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (e.getPlayer().getWorld() == mundo && e.getInventory().getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(txt("§cNa Prisão não tem baú do fim: aqui é um mundo à parte."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void semFabricar(org.bukkit.event.inventory.CraftItemEvent e) {
        if (e.getWhoClicked().getWorld() != mundo || admin((Player) e.getWhoClicked())) return;
        e.setCancelled(true);
        e.getWhoClicked().sendMessage(txt("§cNa Prisão não dá para fabricar itens. Venda o minério com §e/vender§c."));
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
        if (e.getEntity().getWorld() != mundo) return;
        if (e.getDamager() instanceof Firework) { e.setCancelled(true); return; }
        if (e.getEntity() instanceof Interaction i && e.getDamager() instanceof Player p) {   // clique esquerdo no totem
            e.setCancelled(true);
            String a = i.getPersistentDataContainer().get(kAcao, PersistentDataType.STRING);
            if (a != null && !esperando(p, 600)) acao(p, a);
            return;
        }
        if (!(e.getEntity() instanceof Player v)) return;
        Player a = atacante(e);
        if (a == null || a == v) return;
        Mina mv = minaDaArea(v.getLocation()), ma = minaDaArea(a.getLocation());
        if (mv != null && mv.pvp && ma == mv) return;
        e.setCancelled(true);
    }

    /** Na Mina PvP o PvP vale sempre: a protecao de "recem-nascido" do GriefPrevention nao segura quem esta la dentro. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pvpLiberado(EntityDamageByEntityEvent e) {
        if (!e.isCancelled() || e.getEntity().getWorld() != mundo || !(e.getEntity() instanceof Player v)) return;
        Player a = atacante(e);
        if (a == null || a == v) return;
        Mina mv = minaDaArea(v.getLocation()), ma = minaDaArea(a.getLocation());
        if (mv != null && mv.pvp && ma == mv) e.setCancelled(false);
    }

    @EventHandler
    public void cliqueTotem(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction i)) return;
        String a = i.getPersistentDataContainer().get(kAcao, PersistentDataType.STRING);
        if (a == null) return;
        e.setCancelled(true);
        if (!esperando(e.getPlayer(), 600)) acao(e.getPlayer(), a);
    }

    void acao(Player p, String a) {
        switch (a) {
            case "menu" -> abrirMenu(p);
            case "rankup" -> rankup(p);
            case "vender" -> vender(p);
            case "kits" -> abrirKits(p);
            case "enc" -> { p.teleport(salaEnc()); p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1f); }
            case "loja" -> abrirLoja(p);
            default -> {}
        }
    }

    boolean esperando(Player p, long ms) {
        long agora = System.currentTimeMillis();
        Long ult = espera.get(p.getUniqueId());
        if (ult != null && agora - ult < ms) return true;
        espera.put(p.getUniqueId(), agora);
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY() && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        Player p = e.getPlayer();
        if (to.getWorld() != mundo) {
            if (noPortalDoSpawn(to) && !esperando(p, 2500)) {
                if (mundo == null) return;
                p.teleport(patio());
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 0.8f);
            }
            return;
        }
        // tapetes de vender das minas
        if (noTapete(to)) {
            if (!noTapete(e.getFrom()) && !esperando(p, 1500)) {
                boolean tem = false;
                for (ItemStack it : p.getInventory().getStorageContents()) if (marcado(it)) { tem = true; break; }
                if (tem) vender(p);
                else p.sendActionBar(txt("§2⛏ Tapete de venda: §7pise aqui com minério no inventário para vender"));
            }
            return;
        }
        // tapetes dos totens (Bedrock: pisar funciona como clicar)
        if (to.getBlockY() == PISO + 1) {
            int x = to.getBlockX(), z = to.getBlockZ();
            String a = x == -7 && z == 0 ? "rankup" : x == 7 && z == 0 ? "vender" : x == 0 && z == 7 ? "kits" : x == 0 && z == -7 ? "enc"
                    : (x == -9 && z == -8) || (x == 6 && z == ENC_Z - 7) ? "loja" : null;
            if (a != null && !esperando(p, 3000)) { acao(p, a); return; }
        }
        if (to.getBlock().getType() != Material.NETHER_PORTAL) return;
        if (esperando(p, 1500)) return;
        // portal de volta (minas e sala de encantamentos)
        if (to.getBlockZ() == ENC_Z + 11 && Math.abs(to.getBlockX()) <= 1) { irPatio(p); return; }
        Mina m = minaDaArea(to);
        if (m != null) { irPatio(p); return; }
        for (Mina mp : minas) if (mp.pvp && to.getBlockZ() == mp.zVolta() && Math.abs(to.getBlockX() - mp.cx) <= 1) { irPatio(p); return; }
        for (Portal pt : portaisPatio) {
            boolean eixoX = pt.parede() == 'n' || pt.parede() == 's';
            boolean dentro = eixoX ? (to.getBlockZ() == pt.z() && Math.abs(to.getBlockX() - pt.x()) <= 1) : (to.getBlockX() == pt.x() && Math.abs(to.getBlockZ() - pt.z()) <= 1);
            if (!dentro) continue;
            switch (pt.acao()) {
                case "saida" -> sair(p);
                case "enc" -> { p.teleport(salaEnc()); p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1f); }
                default -> {
                    Mina mm = mina(pt.acao());
                    if (mm != null && !irPara(p, mm)) {
                        // empurra de volta para o patio
                        int dx = pt.parede() == 'l' ? -2 : pt.parede() == 'o' ? 2 : 0, dz = pt.parede() == 'n' ? 2 : pt.parede() == 's' ? -2 : 0;
                        p.teleport(new Location(mundo, pt.x() + 0.5 + dx, PISO + 1, pt.z() + 0.5 + dz, p.getLocation().getYaw(), p.getLocation().getPitch()));
                    }
                }
            }
            return;
        }
    }

    void irPatio(Player p) {
        p.teleport(patio());
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1f);
    }

    void sair(Player p) {
        if (!p.performCommand("spawn")) {
            World w = Bukkit.getWorld("world");
            if (w != null) p.teleport(w.getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether(PlayerPortalEvent e) {
        if (e.getFrom().getWorld() == mundo || noPortalDoSpawn(e.getFrom())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether2(EntityPortalEvent e) {
        if (e.getFrom().getWorld() == mundo) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void semFome(FoodLevelChangeEvent e) {
        if (e.getEntity().getWorld() == mundo && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    static final Set<String> BLOQUEADOS = Set.of("kit", "kits", "pet", "pets", "poderes", "habilidades", "classe", "classes", "ec", "enderchest", "echest", "bauender");

    @EventHandler(ignoreCancelled = true)
    public void comando(PlayerCommandPreprocessEvent e) {
        if (e.getPlayer().getWorld() != mundo) return;
        String c = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (c.contains(":")) c = c.substring(c.indexOf(':') + 1);
        // /missoes e do Extras tambem: dentro da Prisao abre as missoes da Prisao
        if (c.equals("missoes") || c.equals("missao") || c.equals("quests") || c.equals("missoesprisao")) { e.setCancelled(true); missoes.abrir(e.getPlayer()); return; }
        if (!BLOQUEADOS.contains(c)) return;
        e.setCancelled(true);
        if (c.startsWith("kit")) abrirKits(e.getPlayer());
        else e.getPlayer().sendMessage(txt("§cNa Prisão isso não funciona: aqui é um mundo à parte."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void encantou(org.bukkit.event.enchantment.EnchantItemEvent e) {
        if (e.getEnchanter().getWorld() == mundo) missoes.somar(e.getEnchanter(), Missoes.Tipo.ENCANTAR, 1);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (p.getWorld() != mundo) return;
        Mina m = minaDaArea(p.getLocation());
        if (m != null && m.pvp) {
            Player k = p.getKiller();
            e.deathMessage(txt("§c⚔ " + p.getName() + " §7caiu na §cMina PvP" + (k != null ? " §7para §c" + k.getName() : "") + "§7!"));
            int sv = sequencia.getOrDefault(p.getUniqueId(), 0);
            sequencia.remove(p.getUniqueId());
            if (k != null && k != p && k.getWorld() == mundo) {
                // recompensa: $ pelo rank de quem caiu; quem derruba alguem numa sequencia de 5+ ganha o dobro (cabeca a premio)
                long v = 150L * (rank(p) + 1) * (sv >= 5 ? 2 : 1);
                setDinheiro(k, dinheiro(k) + v);
                missoes.somar(k, Missoes.Tipo.PVP, 1);
                int sk = sequencia.merge(k.getUniqueId(), 1, Integer::sum);
                k.sendMessage(txt("§c⚔ §7Você derrubou §f" + p.getName() + " §7e ganhou " + din(v) + (sv >= 5 ? " §6(cabeça a prêmio!)" : "") + " §8• sequência: §c" + sk));
                k.playSound(k.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                if (sk == 3 || sk == 5 || sk == 10 || (sk > 10 && sk % 5 == 0))
                    Bukkit.broadcast(txt("§4§l⚔ §c" + k.getName() + " §7está numa sequência de §c§l" + sk + " §7na Mina PvP!" + (sk >= 5 ? " §6Cabeça a prêmio: vale o dobro!" : "")));
                if (sv >= 5) Bukkit.broadcast(txt("§6⚔ §e" + k.getName() + " §7acabou com a sequência de §c" + sv + " §7de §f" + p.getName() + "§7!"));
            }
            return;
        }
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    private final Map<UUID, Integer> sequencia = new HashMap<>();

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        if (e.getPlayer().getWorld() == mundo && mundo != null) e.setRespawnLocation(patio());
    }

    @EventHandler
    public void entrou(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (p.getWorld() != mundo) return;
        p.showTitle(Title.title(txt("§6⛓ Prisão Corona ⛓"), txt("§7Rank " + nomeRank(rank(p)) + " §8• " + din(dinheiro(p))),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
        p.sendMessage(txt("§6§l⛓ Prisão §8» §7Mundo à parte: mochila e dinheiro próprios. Entre nos portais dos blocos, venda com §e/vender §7e suba com §e/rankup§7. Kits: §e/kit§7."));
        // depois da troca de mochila (Extras), para a picareta ficar na mochila da prisao
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || p.getWorld() != mundo) return;
            devolverGuardados(p);
            reembolso(p);
            p.sendMessage(txt("§6📜 Missões do dia §8» §73 missões com prêmio em $ — veja com §e/missoes§7."));
            PersistentDataContainer c = p.getPersistentDataContainer();
            if (c.has(kGanhou, PersistentDataType.BYTE)) return;
            c.set(kGanhou, PersistentDataType.BYTE, (byte) 1);
            // 13/11 (Paulo): picareta de ferro padrao, sem encantamento (fica forte encantando na mesa)
            ItemStack pic = new ItemStack(Material.IRON_PICKAXE);
            ItemMeta im = pic.getItemMeta();
            im.displayName(txt("§fPicareta do Detento"));
            pic.setItemMeta(im);
            daPrisao(pic);
            p.getInventory().addItem(pic).values().forEach(s -> p.getWorld().dropItemNaturally(p.getLocation(), s));
            p.sendMessage(txt("§a+ Picareta de ferro §7(presente de quem chega na Prisão) §8— §dencante na Sala de Encantamentos para deixar forte!"));
        }, 30L);
    }

    // ------------------------------------------------------------------ barra, ranking e hora dourada
    long resetMs;
    private final Set<UUID> naPvp = new HashSet<>();

    // ------------------------------------------------------------------ placar lateral (so na prisao)
    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> placares = new HashMap<>();

    void placar(Player p, Mina m) {
        org.bukkit.scoreboard.Scoreboard sb = placares.get(p.getUniqueId());
        if (sb == null || p.getScoreboard() != sb) {
            if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard() && sb == null) return;   // outro plugin usando: nao mexe
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            org.bukkit.scoreboard.Objective o = sb.registerNewObjective("prisao", org.bukkit.scoreboard.Criteria.DUMMY, txt("§6§l⛓ PRISÃO CORONA ⛓"));
            o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            try { o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank()); } catch (Throwable ignored) {}
            placares.put(p.getUniqueId(), sb);
            p.setScoreboard(sb);
        }
        org.bukkit.scoreboard.Objective o = sb.getObjective("prisao");
        if (o == null) return;
        int r = rank(p), nv = vip(p);
        long d = dinheiro(p);
        List<String> l = new ArrayList<>();
        l.add("§8 ");
        l.add("§7Rank: " + estrelas(prestigio(p)) + RANKS[r].cor() + "[" + RANKS[r].letra() + "] " + RANKS[r].nome());
        l.add("§7Dinheiro: " + din(d));
        if (r + 1 < RANKS.length) {
            long c = RANKS[r + 1].custo();
            int pct = (int) Math.min(100, d * 100 / Math.max(1, c));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(i < pct / 10 ? "§a■" : "§8■");
            l.add("§7Próximo: " + RANKS[r + 1].cor() + "[" + RANKS[r + 1].letra() + "] §7" + din(c));
            l.add(b + " §f" + pct + "%" + (pct >= 100 ? " §a§l/rankup" : ""));
        } else {
            l.add("§d§lRANK MÁXIMO! §e/prestigio");
            int pct = (int) Math.min(100, d * 100 / CUSTO_PRESTIGIO);
            l.add("§7Prestígio: §f" + pct + "% §7de " + din(CUSTO_PRESTIGIO));
        }
        l.add("§0 ");
        if (m != null) {
            int pm = (int) Math.min(100, m.quebrados * 100L / Mina.volume());
            l.add((m.pvp ? "§4§l⚔ PVP §c" : "§6⛏ §f") + m.nome.split(" • ")[0]);
            l.add("§7Minerada: §f" + pm + "%");
        } else l.add("§7Local: §fPátio");
        l.add("§7Bônus venda: §a+" + Math.round((RANKS[r].mult() * multPrest(prestigio(p)) * (100 + BONUS_VIP[nv]) / 100.0 - 1) * 100) + "%" + (horaDourada() ? " §6x2" : ""));
        if (faltaDiaria(p) == 0) l.add("§a§l$ §aDiária: §e/recompensa");
        if (chuvaMina != null && System.currentTimeMillis() < chuvaAte) {
            Mina cm = mina(chuvaMina);
            long f = (chuvaAte - System.currentTimeMillis()) / 1000;
            if (cm != null) l.add("§b⛏ Chuva: §f" + cm.nome.split(" • ")[0] + " §7" + (f / 60) + ":" + String.format("%02d", f % 60));
        }
        if (nv > 0) l.add("§7Venda auto: " + (autoVenda(p) ? "§aligada" : "§7desligada"));
        l.add("§1 ");
        if (m != null) l.add("§a$ Vender: §fpise no tapete verde");
        l.add("§e/minas §8• §e/rankup §8• §e/vender");
        Set<String> agora = new HashSet<>();
        for (int i = 0; i < l.size(); i++) {
            String e = l.get(i);
            while (agora.contains(e)) e += "§r";
            agora.add(e);
            o.getScore(e).setScore(l.size() - i);
        }
        for (String e : new ArrayList<>(sb.getEntries())) if (!agora.contains(e)) sb.resetScores(e);
    }

    void tirarPlacar(Player p) {
        org.bukkit.scoreboard.Scoreboard sb = placares.remove(p.getUniqueId());
        if (sb != null && p.getScoreboard() == sb) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    @EventHandler
    public void saiuDoMundo(PlayerChangedWorldEvent e) { if (e.getFrom() == mundo) tirarPlacar(e.getPlayer()); }

    void barra() {
        if (mundo == null) return;
        long agora = System.currentTimeMillis();
        for (Player p : mundo.getPlayers()) {
            try { placar(p, minaDaArea(p.getLocation())); } catch (Throwable t) { getLogger().warning("placar: " + t); }
            Mina m = minaDaArea(p.getLocation());
            int r = rank(p);
            if (m == null) {
                naPvp.remove(p.getUniqueId());
                p.sendActionBar(txt((horaDourada() ? "§6§l✦ VENDA x2 §8• " : "") + "§6⛓ Rank " + RANKS[r].cor() + RANKS[r].letra() + " §8• " + din(dinheiro(p)) + " §8• §e/minas §7/rankup §7/vender"));
                continue;
            }
            if (m.pvp && naPvp.add(p.getUniqueId())) {
                p.showTitle(Title.title(txt("§4§l⚔ ZONA PVP ⚔"), txt("§cAqui outros jogadores podem te atacar — quem morre perde os itens!"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
            }
            if (!m.pvp) naPvp.remove(p.getUniqueId());
            int pct = (int) Math.min(100, m.quebrados * 100L / Mina.volume());
            long falta = m.quebrados == 0 ? -1 : Math.max(0, resetMs - (agora - m.ultimoReset));
            String quando = m.refazendo ? "§erefazendo..." : falta < 0 ? "§acheia" : "§7refaz em §f" + (falta / 60000) + ":" + String.format("%02d", (falta / 1000) % 60);
            p.sendActionBar(txt((m.pvp ? "§4§l⚔ PVP LIGADO ⚔ §c" : "§6⛏ ") + m.nome + " §8• §f" + pct + "% §7minerada §8• " + quando + " §8• " + din(dinheiro(p))));
        }
    }

    private java.io.File arqTop;
    private org.bukkit.configuration.file.YamlConfiguration top;
    private boolean topSujo;

    void carregarTop() {
        arqTop = new java.io.File(getDataFolder(), "ranking.yml");
        top = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(arqTop);
        Bukkit.getScheduler().runTaskTimer(this, () -> { if (topSujo) { topSujo = false; try { top.save(arqTop); } catch (Exception ignored) {} } }, 1200L, 1200L);
    }

    void registrarTop(Player p, long vendeu) {
        String k = p.getUniqueId().toString();
        top.set(k + ".nome", p.getName());
        top.set(k + ".rank", rank(p));
        top.set(k + ".prest", prestigio(p));
        top.set(k + ".vendido", top.getLong(k + ".vendido", 0) + Math.max(0, vendeu));
        topSujo = true;
    }

    List<String> linhasTop(int n) {
        List<String[]> l = new ArrayList<>();
        for (String k : top.getKeys(false))
            l.add(new String[]{top.getString(k + ".nome", "?"), String.valueOf(top.getInt(k + ".rank", 0)), String.valueOf(top.getLong(k + ".vendido", 0)), String.valueOf(top.getInt(k + ".prest", 0))});
        l.sort((a, b) -> {
            int c = Integer.compare(Integer.parseInt(b[3]), Integer.parseInt(a[3]));
            if (c != 0) return c;
            c = Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1]));
            return c != 0 ? c : Long.compare(Long.parseLong(b[2]), Long.parseLong(a[2]));
        });
        List<String> out = new ArrayList<>();
        String[] med = {"§6①", "§7②", "§c③"};
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            int r = Math.max(0, Math.min(RANKS.length - 1, Integer.parseInt(l.get(i)[1])));
            out.add((i < 3 ? med[i] : "§8" + (i + 1) + ".") + " §f" + l.get(i)[0] + " " + estrelas(Integer.parseInt(l.get(i)[3])) + RANKS[r].cor() + "[" + RANKS[r].letra() + "] §8• §7" + fmt(Long.parseLong(l.get(i)[2])) + " minérios");
        }
        return out;
    }

    boolean horaDourada() {
        int h = getConfig().getInt("hora-dourada", 20);
        if (h < 0) return false;
        return java.time.LocalTime.now(java.time.ZoneId.of("America/Sao_Paulo")).getHour() == h;
    }
    private boolean avisouDourada;

    void avisoDourada() {
        boolean d = horaDourada();
        if (d && !avisouDourada) {
            Bukkit.broadcast(txt("§6§l✦ HORA DOURADA NA PRISÃO! §eDurante 1 hora o minério vende em §6§lDOBRO§e. §f/minas"));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            atualizarPainel();
        }
        if (!d && avisouDourada) { Bukkit.broadcast(txt("§7A hora dourada da Prisão acabou. Volta amanhã!")); atualizarPainel(); }
        avisouDourada = d;
    }

    // ------------------------------------------------------------------ menus
    static class Tela implements InventoryHolder {
        final String tipo;
        Inventory inv;
        Tela(String tipo) { this.tipo = tipo; }
        @Override public Inventory getInventory() { return inv; }
    }

    static final int[] SLOTS_MINAS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};

    void abrirMenu(Player p) {
        Tela h = new Tela("menu");
        Inventory inv = Bukkit.createInventory(h, 54, txt("§8⛓ Prisão • rank " + RANKS[rank(p)].letra() + " • $" + fmt(dinheiro(p))));
        h.inv = inv;
        int r = rank(p);
        inv.setItem(0, item(Material.OAK_DOOR, "§a§lSaída", "§7Volta para o spawn do servidor."));
        inv.setItem(4, item(Material.COMPASS, "§e§lPátio da Prisão", "§7O centro: portais, kits, venda e rank up."));
        long fd = faltaDiaria(p);
        inv.setItem(2, item(fd == 0 ? Material.SUNFLOWER : Material.CLOCK, "§a§lRecompensa diária", "§7Ganhe " + din(valorDiaria(p)) + " §7a cada 20 h.", "§7(sobe com rank, prestígio e VIP)",
                fd == 0 ? "§a▶ Clique para pegar" : "§eVolta em " + (fd / 3600000) + "h " + ((fd / 60000) % 60) + "min"));
        if (r + 1 >= RANKS.length) inv.setItem(6, item(Material.NETHER_STAR, "§e§l★ Prestígio", "§7Volta para o rank A e o dinheiro zera,", "§7mas ganha §a+20% §7na venda para sempre.", "§7Custa " + din(CUSTO_PRESTIGIO) + "§7.", "§a▶ Clique (confirme duas vezes)"));
        List<String> tl = new ArrayList<>(linhasTop(5));
        if (tl.isEmpty()) tl.add("§7Ninguém ainda. Seja o primeiro!");
        int hd = getConfig().getInt("hora-dourada", 20);
        if (hd >= 0) tl.add(horaDourada() ? "§6§l✦ HORA DOURADA AGORA: venda x2!" : "§6✦ Hora dourada: todo dia às " + hd + "h (venda x2)");
        inv.setItem(8, item(Material.GOLDEN_HELMET, "§6§lTop da Prisão", tl.toArray(new String[0])));
        for (int i = 0; i < 12; i++) {
            Mina m = minas.get(i);
            boolean ok = r >= m.rank;
            inv.setItem(SLOTS_MINAS[i], item(ok ? m.icone : Material.IRON_BARS, (ok ? "§a" : "§c") + m.nome,
                    "§7Rank: " + nomeRank(m.rank), "§7Minérios: §f" + resumo(m), ok ? "§a▶ Clique para ir" : "§c✖ Precisa do rank " + RANKS[m.rank].letra() + " §8(/rankup)"));
        }
        Mina pv = minas.get(12);
        inv.setItem(24, item(r >= pv.rank ? Material.NETHERITE_SWORD : Material.IRON_BARS, "§c§l" + pv.nome,
                "§7Rank: " + nomeRank(pv.rank), "§7Minérios: §f" + resumo(pv), "§c⚠ PvP ligado: quem morre", "§c   perde os itens!", r >= pv.rank ? "§a▶ Clique para ir" : "§c✖ Precisa do rank " + RANKS[pv.rank].letra()));
        inv.setItem(49, item(Material.WRITABLE_BOOK, "§6§l📜 Missões do dia", "§73 missões com prêmio em $", "§7e bônus por completar todas.", "§a▶ Clique para ver"));
        inv.setItem(25, item(Material.ENCHANTING_TABLE, "§d§lSala de Encantamentos", "§7Mesas com 15 estantes, bigornas", "§7e rebolo. §a▶ Clique para ir"));
        inv.setItem(36, item(Material.DIAMOND_PICKAXE, "§b§lLoja de picaretas", "§7Picaretas com o dinheiro da Prisão", "§7(até a de diamante, sem encantamento).", "§7Encante na Sala de Encantamentos!", "§a▶ Clique para ver"));
        inv.setItem(38, item(Material.CHEST, "§b§lKits da Prisão", "§7Kit do Detento para todos e", "§7kits VIP (24 h cada).", "§a▶ Clique para ver"));
        long cust = r + 1 < RANKS.length ? RANKS[r + 1].custo() : 0;
        inv.setItem(40, item(Material.EXPERIENCE_BOTTLE, "§6§lRank Up",
                "§7Seu rank: " + nomeRank(r), r + 1 < RANKS.length ? "§7Próximo: " + nomeRank(r + 1) + " §7por " + din(cust) : "§dVocê está no rank máximo!",
                "§7Seu dinheiro: " + din(dinheiro(p)), "§7Bônus na venda: §a+" + Math.round((RANKS[r].mult() - 1) * 100) + "%", r + 1 < RANKS.length ? "§a▶ Clique para subir" : ""));
        inv.setItem(42, item(Material.GOLD_INGOT, "§2§lVender minério", "§7Vende todo minério das minas", "§7que estiver no inventário.", "§a▶ Clique para vender"));
        int nvv = vip(p);
        boolean av = autoVenda(p);
        inv.setItem(46, item(nvv > 0 ? (av ? Material.HOPPER : Material.CHEST_MINECART) : Material.BARRIER, "§e§lVenda automática: " + (nvv == 0 ? "§cSó VIP" : av ? "§aLIGADA" : "§7DESLIGADA"),
                "§7Quando o inventário enche, o minério", "§7é vendido sozinho (com seus bônus).", nvv > 0 ? "§a▶ Clique para " + (av ? "desligar" : "ligar") : "§7Veja os planos: §e/vip"));
        inv.setItem(44, item(Material.ENCHANTED_BOOK, "§d§lComo deixar a picareta forte?", "§7Encante nas mesas da §dSala de Encantamentos§7:", "§7lápis-lazúli (compre na §9Loja§7) + XP da mineração.", "§7Junte livros e picaretas na bigorna."));
        p.openInventory(inv);
    }

    String resumo(Mina m) {
        StringBuilder sb = new StringBuilder();
        for (Material mt : m.mistura.keySet()) {
            String n = switch (mt) {
                case COAL_ORE -> "carvão"; case COPPER_ORE -> "cobre"; case IRON_ORE, DEEPSLATE_IRON_ORE -> "ferro"; case GOLD_ORE, DEEPSLATE_GOLD_ORE -> "ouro";
                case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> "redstone"; case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> "lápis"; case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> "diamante";
                case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> "esmeralda"; case ANCIENT_DEBRIS -> "netherita"; case NETHER_QUARTZ_ORE -> "quartzo"; case NETHER_GOLD_ORE -> "ouro do Nether";
                case GILDED_BLACKSTONE -> "pedra dourada"; case GOLD_BLOCK -> "bloco de ouro"; case DIAMOND_BLOCK -> "bloco de diamante"; case EMERALD_BLOCK -> "bloco de esmeralda";
                default -> null;
            };
            if (n == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(n);
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
        if (!(e.getView().getTopInventory().getHolder() instanceof Tela t)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getSlot();
        switch (t.tipo) {
            case "menu" -> {
                for (int i = 0; i < SLOTS_MINAS.length; i++) if (SLOTS_MINAS[i] == s) { p.closeInventory(); irPara(p, minas.get(i)); return; }
                switch (s) {
                    case 0 -> { p.closeInventory(); sair(p); }
                    case 2 -> { p.closeInventory(); diaria(p); }
                    case 49 -> { p.closeInventory(); Bukkit.getScheduler().runTask(this, () -> missoes.abrir(p)); }
                    case 6 -> { p.closeInventory(); fazerPrestigio(p); }
                    case 4 -> { p.closeInventory(); irCentro(p); }
                    case 24 -> { p.closeInventory(); irPara(p, minas.get(12)); }
                    case 25 -> { p.closeInventory(); if (podeIr(p)) p.teleport(salaEnc()); }
                    case 36 -> abrirLoja(p);
                    case 38 -> abrirKits(p);
                    case 40 -> { p.closeInventory(); rankup(p); }
                    case 42 -> { p.closeInventory(); vender(p); }
                    case 44 -> { p.closeInventory(); if (podeIr(p)) p.teleport(salaEnc()); }
                    case 46 -> {
                        if (vip(p) == 0) { p.sendMessage(txt("§cA venda automática é dos VIPs. §7Veja: §e/vip")); return; }
                        p.getPersistentDataContainer().set(kAuto, PersistentDataType.BYTE, (byte) (autoVenda(p) ? 0 : 1));
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                        abrirMenu(p);
                    }
                    default -> {}
                }
            }
            case "kits" -> { if (s >= 0 && s < KITS.length * 2 && s % 2 == 0) pegarKit(p, KITS[s / 2]); }
            case "loja" -> { for (Oferta o : LOJA) if (o.slot() == s) { comprar(p, o); abrirLoja(p); } }
            default -> {}
        }
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Tela) e.setCancelled(true);
    }

    boolean podeIr(Player p) {
        String w = p.getWorld().getName();
        if (p.getWorld() == mundo || mundosComAcesso.contains(w) || p.hasPermission("mineracao.admin")) return true;
        p.sendMessage(txt("§cDaqui não dá para ir para a Prisão. Volte ao spawn primeiro."));
        return false;
    }

    void irCentro(Player p) { if (podeIr(p)) p.teleport(patio()); }

    boolean irPara(Player p, Mina m) {
        if (!podeIr(p)) return false;
        if (rank(p) < m.rank) {
            p.sendMessage(txt("§cO " + m.nome + " §cprecisa do rank " + nomeRank(m.rank) + "§c. Use §e/rankup§c."));
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 0.6f);
            return false;
        }
        p.teleport(m.chegada(mundo));
        p.sendMessage(txt("§2⛏ §aPara vender: §fpise num §a§ltapete verde §f(§a$ VENDA AQUI§f) na borda da mina."));
        p.sendMessage(txt("§e⏱ " + m.nome.split(" • ")[0] + "§7: " + tempoReset(m)));
        if (m.pvp) p.sendMessage(txt("§a✔ Antessala segura da Mina PvP. §7Passando pelo §cportão §7tem PvP e quem morre §cperde os itens§7."));
        return true;
    }

    // ------------------------------------------------------------------ rank up e venda
    void rankup(Player p) {
        int r = rank(p);
        if (r + 1 >= RANKS.length) { p.sendMessage(txt("§dVocê já está no rank máximo: " + nomeRank(r) + "§d. Próximo passo: §e/prestigio §7(" + din(CUSTO_PRESTIGIO) + "§7)")); return; }
        Rank prox = RANKS[r + 1];
        long tem = dinheiro(p);
        if (tem < prox.custo()) {
            p.sendMessage(txt("§cFaltam " + din(prox.custo() - tem) + " §cpara o rank " + nomeRank(r + 1) + "§c. Venda minério com §e/vender§c."));
            return;
        }
        setDinheiro(p, tem - prox.custo());
        p.getPersistentDataContainer().set(kRank, PersistentDataType.INTEGER, r + 1);
        p.showTitle(Title.title(txt("§6§lRANK UP!"), txt(nomeRank(r + 1)), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        // fogos na comemoracao do rank up
        p.getWorld().spawn(p.getLocation().add(0, 1, 0), Firework.class, fw -> {
            org.bukkit.inventory.meta.FireworkMeta fm = fw.getFireworkMeta();
            fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).withFlicker().build());
            fm.setPower(0);
            fw.setFireworkMeta(fm);
            fw.getPersistentDataContainer().set(kEnfeite, PersistentDataType.STRING, "fogos");
        }).detonate();
        Bukkit.broadcast(txt("§6⛓ §e" + p.getName() + " §7subiu para o rank " + nomeRank(r + 1) + " §7na Prisão!"));
        getLogger().info(p.getName() + " subiu para o rank " + prox.letra() + " (pagou $" + prox.custo() + ")");
        registrarTop(p, 0);
    }

    void vender(Player p) { vender(p, false); }

    /** auto = venda automatica (VIP, inventario cheio): sem chat, so a barra. */
    void vender(Player p, boolean auto) {
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
        if (itens == 0) { if (!auto) p.sendMessage(txt("§7Você não tem minério das minas para vender. §8(só o que sai das minas da Prisão vende)")); return; }
        p.getInventory().setStorageContents(cont);
        int r = rank(p);
        int nv = vip(p);
        boolean dour = horaDourada();
        // 13/11: bonus de grupo — +5% por jogador a mais na Prisao (ate +20%), para minerar junto valer a pena
        int outros = Math.min(4, Math.max(0, mundo.getPlayers().size() - 1));
        long v = Math.max(1, Math.round(total * RANKS[r].mult() * multPrest(prestigio(p)) * (100 + BONUS_VIP[nv] + 5 * outros) / 100.0 * (dour ? 2 : 1)));
        setDinheiro(p, dinheiro(p) + v);
        missoes.somar(p, Missoes.Tipo.VENDER, v);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        if (auto) {
            p.sendActionBar(txt("§a⛏ Venda automática: §f" + itens + " §aminérios por " + din(v) + " §8• saldo " + din(dinheiro(p))));
            registrarTop(p, itens);
            return;
        }
        p.sendMessage(txt("§a⛏ Vendeu §f" + itens + " §aminérios por " + din(v) + " §8(rank " + RANKS[r].letra()
                + (nv > 0 ? ", " + NOME_VIP[nv] + " +" + BONUS_VIP[nv] + "%" : "") + (outros > 0 ? ", §bgrupo +" + (5 * outros) + "%§8" : "") + (dour ? ", §6HORA DOURADA x2§8" : "") + ") §7— saldo: " + din(dinheiro(p))));
        registrarTop(p, itens);
    }

    // ------------------------------------------------------------------ kits (so na prisao)
    record Kit(String id, String nome, int vipMin, Material icone, String desc) {}
    static final Kit[] KITS = {
            new Kit("detento", "§7§lKit Detento", 0, Material.STONE_PICKAXE, "§7Picareta de ferro (Eficiência I)"),
            new Kit("vip", "§a§lKit VIP", 1, Material.IRON_PICKAXE, "§7Picareta de diamante (Efic. III, Fortuna I)"),
            new Kit("vipmais", "§b§lKit VIP+", 2, Material.DIAMOND_PICKAXE, "§7Picareta de diamante (Efic. IV, Fortuna II)\n§7+ armadura de ferro"),
            new Kit("mvp", "§6§lKit MVP", 3, Material.NETHERITE_PICKAXE, "§7Picareta de netherita (Efic. V, Fortuna II)\n§7+ armadura de diamante (Proteção II)"),
            new Kit("lenda", "§d§lKit LENDA", 4, Material.NETHER_STAR, "§7Picareta de netherita (Efic. V, Fortuna III, Remendo)\n§7+ armadura de diamante (Proteção III) + 4 maçãs douradas")};

    void abrirKits(Player p) {
        if (p.getWorld() != mundo) { p.sendMessage(txt("§cOs kits da Prisão só saem dentro da Prisão.")); return; }
        Tela h = new Tela("kits");
        Inventory inv = Bukkit.createInventory(h, 9, txt("§8⛓ Kits da Prisão (24 h)"));
        h.inv = inv;
        int nv = vip(p);
        for (int i = 0; i < KITS.length; i++) {
            Kit k = KITS[i];
            long falta = faltaKit(p, k);
            List<String> lore = new ArrayList<>(Arrays.asList(k.desc().split("\n")));
            if (nv < k.vipMin()) lore.add("§c✖ Só para " + NOME_VIP[k.vipMin()] + " ou acima §8(/vip)");
            else if (falta > 0) lore.add("§e⏳ Disponível em " + (falta / 3600000) + "h " + ((falta / 60000) % 60) + "min");
            else lore.add("§a▶ Clique para pegar");
            inv.setItem(i * 2, item(nv >= k.vipMin() ? k.icone() : Material.BARRIER, k.nome(), lore.toArray(new String[0])));
        }
        p.openInventory(inv);
    }

    long faltaKit(Player p, Kit k) {
        Long ult = p.getPersistentDataContainer().get(new NamespacedKey(this, "kit_" + k.id()), PersistentDataType.LONG);
        if (ult == null) return 0;
        return Math.max(0, ult + 24 * 3600_000L - System.currentTimeMillis());
    }

    void pegarKit(Player p, Kit k) {
        if (p.getWorld() != mundo) return;
        if (vip(p) < k.vipMin()) { p.sendMessage(txt("§cEsse kit é só para " + NOME_VIP[k.vipMin()] + " ou acima. §7Veja: §e/vip")); return; }
        long falta = faltaKit(p, k);
        if (falta > 0) { p.sendMessage(txt("§eEsse kit volta em " + (falta / 3600000) + "h " + ((falta / 60000) % 60) + "min.")); return; }
        List<ItemStack> l = new ArrayList<>();
        switch (k.id()) {
            case "detento" -> l.add(ench(Material.IRON_PICKAXE, "§7Picareta do Detento", Enchantment.EFFICIENCY, 1));
            case "vip" -> l.add(ench(Material.DIAMOND_PICKAXE, "§aPicareta VIP", Enchantment.EFFICIENCY, 3, Enchantment.FORTUNE, 1, Enchantment.UNBREAKING, 2));
            case "vipmais" -> {
                l.add(ench(Material.DIAMOND_PICKAXE, "§bPicareta VIP+", Enchantment.EFFICIENCY, 4, Enchantment.FORTUNE, 2, Enchantment.UNBREAKING, 3));
                for (Material m : new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS}) l.add(new ItemStack(m));
            }
            case "mvp" -> {
                l.add(ench(Material.NETHERITE_PICKAXE, "§6Picareta MVP", Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 2, Enchantment.UNBREAKING, 3));
                for (Material m : new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}) l.add(ench(m, null, Enchantment.PROTECTION, 2));
            }
            case "lenda" -> {
                l.add(ench(Material.NETHERITE_PICKAXE, "§dPicareta da Lenda", Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                for (Material m : new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}) l.add(ench(m, null, Enchantment.PROTECTION, 3));
                l.add(new ItemStack(Material.GOLDEN_APPLE, 4));
            }
            default -> {}
        }
        for (ItemStack s : l) p.getInventory().addItem(daPrisao(s)).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
        p.getPersistentDataContainer().set(new NamespacedKey(this, "kit_" + k.id()), PersistentDataType.LONG, System.currentTimeMillis());
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1f);
        p.sendMessage(txt("§a+ " + k.nome() + " §7(volta em 24 h)"));
        p.closeInventory();
    }

    static ItemStack ench(Material m, String nome, Object... encs) {
        ItemStack s = new ItemStack(m);
        ItemMeta im = s.getItemMeta();
        if (nome != null) im.displayName(txt(nome));
        for (int i = 0; i + 1 < encs.length; i += 2) im.addEnchant((Enchantment) encs[i], (Integer) encs[i + 1], true);
        s.setItemMeta(im);
        return s;
    }

    // ------------------------------------------------------------------ loja de picaretas ($)
    /** 13/11 (Paulo): picaretas compradas com o dinheiro das minas, no maximo a de diamante pura (sem encantamento):
     *  para ficarem fortes o jogador encanta (mesa, bigorna ou melhorias). Lapis para quem ainda nao chega no Bloco C. */
    record Oferta(int slot, Material m, int qtd, String nome, long preco) {}
    static final Oferta[] LOJA = {
            new Oferta(10, Material.STONE_PICKAXE, 1, "§7Picareta de Pedra", 50),
            new Oferta(11, Material.IRON_PICKAXE, 1, "§fPicareta de Ferro", 400),
            new Oferta(12, Material.DIAMOND_PICKAXE, 1, "§bPicareta de Diamante", 3000),
            new Oferta(14, Material.LAPIS_LAZULI, 8, "§9Lápis-lazúli x8", 60),
            new Oferta(15, Material.LAPIS_LAZULI, 32, "§9Lápis-lazúli x32", 220),
            new Oferta(16, Material.LAPIS_LAZULI, 64, "§9Lápis-lazúli x64", 400)};

    void abrirLoja(Player p) {
        if (p.getWorld() != mundo) { p.sendMessage(txt("§cA loja de picaretas fica dentro da Prisão.")); return; }
        Tela h = new Tela("loja");
        Inventory inv = Bukkit.createInventory(h, 27, txt("§8⛏ Loja • picaretas e lápis • $" + fmt(dinheiro(p))));
        h.inv = inv;
        long tem = dinheiro(p);
        for (Oferta o : LOJA) {
            ItemStack it = item(o.m(), o.nome(), "§7Preço: " + din(o.preco()), o.m() == Material.LAPIS_LAZULI ? "§7Use na mesa de encantamento." : "§7Sem encantamento: §fencante você mesmo!",
                    tem >= o.preco() ? "§a▶ Clique para comprar" : "§c✖ Faltam " + din(o.preco() - tem));
            it.setAmount(o.qtd());
            inv.setItem(o.slot(), it);
        }
        inv.setItem(22, item(Material.ENCHANTING_TABLE, "§d§lComo deixar forte?", "§7Encante na §dSala de Encantamentos §7(portal sul)", "§7com lápis + XP da mineração.", "§7Junte livros e picaretas na bigorna."));
        p.openInventory(inv);
    }

    void comprar(Player p, Oferta o) {
        long tem = dinheiro(p);
        if (tem < o.preco()) { p.sendMessage(txt("§cFaltam " + din(o.preco() - tem) + " §cpara comprar " + o.nome() + "§c.")); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f); return; }
        if (p.getInventory().firstEmpty() < 0) { p.sendMessage(txt("§cInventário cheio. Venda o minério primeiro.")); return; }
        setDinheiro(p, tem - o.preco());
        ItemStack it = daPrisao(new ItemStack(o.m(), o.qtd()));
        p.getInventory().addItem(it);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1.1f);
        p.sendMessage(txt("§a+ " + o.nome() + " §7(-" + din(o.preco()) + "§7) §8• saldo " + din(dinheiro(p))));
        getLogger().info(p.getName() + " comprou " + o.m() + " x" + o.qtd() + " por $" + o.preco());
    }

    // ------------------------------------------------------------------ comandos
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("mineracaoadm")) return adm(s, a);
        if (!(s instanceof Player p)) { s.sendMessage("So jogador."); return true; }
        if (mundo == null) { p.sendMessage(txt("§cA Prisão não está disponível agora.")); return true; }
        switch (n) {
            case "minas" -> {
                if (a.length > 0) {
                    String id = a[0].toLowerCase(Locale.ROOT);
                    if (id.equals("centro") || id.equals("patio")) { irCentro(p); return true; }
                    if (id.equals("enc") || id.startsWith("encant")) { if (podeIr(p)) p.teleport(salaEnc()); return true; }
                    Mina m = mina(id);
                    if (m != null) { irPara(p, m); return true; }
                    p.sendMessage(txt("§7Use: §e/minas §7(menu) ou §e/minas a..l | pvp | enc | centro"));
                    return true;
                }
                if (p.getWorld() != mundo) { irCentro(p); return true; }
                abrirMenu(p);
            }
            case "rankup" -> rankup(p);
            case "prestigio" -> fazerPrestigio(p);
            case "recompensa" -> diaria(p);
            case "missoesprisao" -> { if (p.getWorld() == mundo) missoes.abrir(p); else p.sendMessage(txt("§7As missões são da §6Prisão§7: entre com §e/minas§7.")); }
            case "vender" -> vender(p);
            case "picareta" -> {
                p.sendMessage(txt("§d⛏ A picareta fica forte encantando: §fvá na §dSala de Encantamentos §f(portal sul do pátio ou §e/minas enc§f) e compre lápis na §9Loja§f."));
                if (p.getWorld() == mundo) p.teleport(salaEnc());
            }
            case "topminas" -> {
                p.sendMessage(txt("§6§l⛓ Top da Prisão"));
                List<String> tl = linhasTop(10);
                if (tl.isEmpty()) p.sendMessage(txt("§7Ninguém ainda."));
                for (String x : tl) p.sendMessage(txt(x));
            }
            case "rank" -> {
                int r = rank(p);
                p.sendMessage(txt("§6⛓ Seu rank: " + nomeRank(r) + " §8| §7dinheiro: " + din(dinheiro(p))
                        + (r + 1 < RANKS.length ? " §8| §7próximo: " + nomeRank(r + 1) + " §7por " + din(RANKS[r + 1].custo()) : "")));
            }
            default -> {}
        }
        return true;
    }

    private boolean adm(CommandSender s, String[] a) {
        if (a.length == 0) { s.sendMessage("/mineracaoadm resetar <id|todas> | rank <nick> <A-L> | dinheiro <nick> <valor> | ver <nick> | info | reconstruir | portalspawn"); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "resetar" -> {
                String id = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "todas";
                for (Mina m : minas) if (id.equals("todas") || m.id.equals(id)) refazer(m);
                s.sendMessage("refazendo: " + id);
            }
            case "rank" -> {
                Player p = a.length > 2 ? Bukkit.getPlayerExact(a[1]) : null;
                if (p == null) { s.sendMessage("jogador offline"); return true; }
                int r = "ABCDEFGHIJKL".indexOf(a[2].toUpperCase(Locale.ROOT));
                if (r < 0) { s.sendMessage("rank A-L"); return true; }
                p.getPersistentDataContainer().set(kRank, PersistentDataType.INTEGER, r);
                s.sendMessage(p.getName() + " agora e rank " + RANKS[r].letra());
            }
            case "dinheiro" -> {
                Player p = a.length > 2 ? Bukkit.getPlayerExact(a[1]) : null;
                if (p == null) { s.sendMessage("jogador offline"); return true; }
                try { setDinheiro(p, dinheiro(p) + Long.parseLong(a[2])); } catch (NumberFormatException ex) { s.sendMessage("valor?"); return true; }
                s.sendMessage(p.getName() + " dinheiro=" + dinheiro(p));
            }
            case "ver" -> {
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (p == null) { s.sendMessage("jogador offline"); return true; }
                int marc = 0;
                for (ItemStack it : p.getInventory().getStorageContents()) if (marcado(it)) marc += it.getAmount();
                s.sendMessage(p.getName() + " rank=" + RANKS[rank(p)].letra() + " dinheiro=" + dinheiro(p) + " minerio-marcado=" + marc + " mundo=" + p.getWorld().getName());
            }
            case "reconstruir" -> { construirTudo(2); s.sendMessage("reconstruido"); }
            case "chuva" -> { sortearChuva(); s.sendMessage("chuva=" + chuvaMina); }
            case "golem" -> { if (s instanceof Player gp) golem.forcar(gp); else golem.talvezSurgir(); }
            case "portalspawn" -> { getConfig().set("portal-spawn.feito", false); portalDoSpawn(); s.sendMessage("portal-spawn.feito=" + getConfig().getBoolean("portal-spawn.feito")); }
            case "info" -> {
                for (Mina m : minas) {
                    Map<Material, Integer> cont = new EnumMap<>(Material.class);
                    for (int y = Mina.TOPO; y > Mina.TOPO - Mina.FUNDO; y -= 5)
                        for (int x = -Mina.RAIO; x <= Mina.RAIO; x += 3)
                            for (int z = -Mina.RAIO; z <= Mina.RAIO; z += 3) cont.merge(mundo.getBlockAt(m.cx + x, y, m.cz + z).getType(), 1, Integer::sum);
                    s.sendMessage("mina " + m.id + " cx=" + m.cx + " cz=" + m.cz + " rank=" + RANKS[m.rank].letra() + " quebrados=" + m.quebrados + " amostra=" + cont);
                }
            }
            default -> s.sendMessage("?");
        }
        return true;
    }
}
