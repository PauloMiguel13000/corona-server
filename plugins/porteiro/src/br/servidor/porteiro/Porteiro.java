package br.servidor.porteiro;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * O PORTEIRO (15/11/2026) - o jogo de terror "so nosso" do Corona Server, com os dois monstros 3D originais:
 * o Porteiro (cego, de uniforme vinho e saco na cabeca: so OUVE) e a Sombra de Cartola (so anda quando ninguem
 * esta olhando para ela). Os jogadores estao trancados no Edificio Coroa a meia-noite: precisam achar as chaves
 * espalhadas pelos apartamentos e sair pelo portao da portaria. Agachado ninguem faz barulho; dentro do armario e
 * agachado o Porteiro nao te acha; mas a Sombra ve tudo - e so para quando alguem olha para ela.
 * Co-op de 1 a 4 jogadores, uma partida por vez. Ninguem perde item (nada e mexido no inventario).
 */
public final class Porteiro extends JavaPlugin implements Listener {

    static final String PREFIXO = "§8[§4§lO Porteiro§8] §7";
    static final int Y = 64, M = 24, N = 2 * M + 1, YSALA = 100, MAPA = 1;
    static final UUID PACOTE = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(StandardCharsets.UTF_8));
    private static final Set<UUID> COM_PACOTE = ConcurrentHashMap.newKeySet();

    enum Fase { ESPERA, CONTAGEM, JOGO, FIM }

    private World mundo;
    private final Random rnd = new Random();
    private File arqDados;
    private YamlConfiguration dados;
    private Fase fase = Fase.ESPERA;
    private int t, pegas, totalChaves;
    private final Map<UUID, Integer> vidas = new HashMap<>();
    private final Set<UUID> fantasmas = new HashSet<>();
    private final Map<UUID, Long> imuneAte = new HashMap<>();
    private final Map<UUID, Location> antes = new HashMap<>();
    private final boolean[][] anda = new boolean[N][N];
    private final List<int[]> corredor = new ArrayList<>(), mesas = new ArrayList<>(), todasCelulas = new ArrayList<>();
    private final Set<Long> armarios = new HashSet<>();
    private final List<ItemDisplay> chaves = new ArrayList<>();
    private Bicho porteiro, sombra;
    private BossBar barra;
    private boolean desligando, portaoAberto;

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static boolean temPacote(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || COM_PACOTE.contains(p.getUniqueId());
    }

    // ================================================================== monstro (modelo 3D + boneco para quem nao tem o pacote)

    final class Bicho {
        final String id;
        double x, z;
        float yaw;
        ItemDisplay modelo;
        ArmorStand boneco;
        List<int[]> caminho = new ArrayList<>();
        int[] alvo;
        int quadro = 0, parado, olhada;
        boolean cacando, ativo = true;

        Bicho(String id, double x, double z, Color roupa, Material cabeca) {
            this.id = id;
            this.x = x;
            this.z = z;
            Location l = new Location(mundo, x, Y, z);
            boneco = mundo.spawn(l, ArmorStand.class, a -> {
                a.setGravity(false);
                a.setPersistent(false);
                a.setInvulnerable(true);
                a.setBasePlate(false);
                a.setArms(true);
                a.setSilent(true);
                a.setVisibleByDefault(false);
                a.getEquipment().setHelmet(new ItemStack(cabeca));
                for (var par : Map.of(EquipmentSlot.CHEST, Material.LEATHER_CHESTPLATE, EquipmentSlot.LEGS, Material.LEATHER_LEGGINGS, EquipmentSlot.FEET, Material.LEATHER_BOOTS).entrySet()) {
                    ItemStack it = new ItemStack(par.getValue());
                    LeatherArmorMeta m = (LeatherArmorMeta) it.getItemMeta();
                    m.setColor(roupa);
                    it.setItemMeta(m);
                    a.getEquipment().setItem(par.getKey(), it);
                }
                for (EquipmentSlot s : EquipmentSlot.values()) {
                    try {
                        a.addEquipmentLock(s, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    } catch (Throwable ignorado) {
                        // slot que a armadura nao tem
                    }
                }
            });
            modelo = mundo.spawn(l, ItemDisplay.class, d -> {
                d.setItemStack(item(0));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(new Transformation(new Vector3f(0, 1.5f, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
                d.setViewRange(1.6f);
                d.setShadowRadius(0f);
                d.setTeleportDuration(2);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
            });
            repartir();
        }

        @SuppressWarnings("UnstableApiUsage")
        ItemStack item(int q) {
            // leather_helmet: no Bedrock o Geyser (corona-hotel.json) troca pelo item 3D
            ItemStack it = new ItemStack(Material.LEATHER_HELMET);
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/anim_" + id + "_f" + q));
            return it;
        }

        void repartir() {
            for (Player p : mundo.getPlayers()) {
                boolean tres = temPacote(p);
                ver(p, modelo, tres && ativo);
                ver(p, boneco, !tres && ativo);
            }
        }

        void ver(Player p, Entity e, boolean sim) {
            if (e == null || !e.isValid() || sim == p.canSee(e)) return;
            if (sim) p.showEntity(Porteiro.this, e);
            else p.hideEntity(Porteiro.this, e);
        }

        Location loc() {
            return new Location(mundo, x, Y, z, yaw, 0f);
        }

        void quadro(int q) {
            if (q == quadro || modelo == null || !modelo.isValid()) return;
            quadro = q;
            modelo.setItemStack(item(q));
        }

        void pular(double nx, double nz) {
            x = nx;
            z = nz;
            caminho.clear();
            if (modelo != null && modelo.isValid()) modelo.setTeleportDuration(0);
            mostrar();
            if (modelo != null && modelo.isValid()) modelo.setTeleportDuration(2);
        }

        void mostrar() {
            Location l = loc();
            if (modelo != null && modelo.isValid()) modelo.teleport(l);
            if (boneco != null && boneco.isValid()) boneco.teleport(l);
        }

        /** Anda ate "passo" blocos pelo caminho. Devolve true se chegou no fim. */
        boolean andar(double passo) {
            while (passo > 0 && !caminho.isEmpty()) {
                int[] c = caminho.get(0);
                double dx = c[0] + 0.5 - x, dz = c[1] + 0.5 - z, d = Math.sqrt(dx * dx + dz * dz);
                if (d < 0.05) {
                    caminho.remove(0);
                    continue;
                }
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                double k = Math.min(passo, d);
                x += dx / d * k;
                z += dz / d * k;
                passo -= k;
            }
            mostrar();
            return caminho.isEmpty();
        }

        void ir(int cx, int cz) {
            alvo = new int[] {cx, cz};
            caminho = rota((int) Math.floor(x), (int) Math.floor(z), cx, cz);
        }

        void sumir() {
            if (modelo != null && modelo.isValid()) modelo.remove();
            if (boneco != null && boneco.isValid()) boneco.remove();
        }
    }

    // ================================================================== ligar e desligar

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arqDados = new File(getDataFolder(), "dados.yml");
        dados = YamlConfiguration.loadConfiguration(arqDados);
        getServer().getPluginManager().registerEvents(this, this);
        criarMundo();
        barra = BossBar.bossBar(txt("§4§lO PORTEIRO"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Bukkit.getScheduler().runTaskTimer(this, this::segundo, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::rapido, 41L, 2L);
        getLogger().info("O Porteiro pronto (mundo " + nome() + ", " + mesas.size() + " mesas, " + armarios.size() + " armarios).");
    }

    @Override
    public void onDisable() {
        desligando = true;
        limparPartida();
        for (Player p : mundo == null ? List.<Player>of() : new ArrayList<>(mundo.getPlayers())) {
            try {
                sair(p, false);
            } catch (Throwable ex) {
                getLogger().warning("Nao consegui tirar " + p.getName() + " do Porteiro ao desligar: " + ex);
            }
        }
        salvar();
    }

    private String nome() {
        return getConfig().getString("mundo", "porteiro");
    }

    void salvar() {
        try {
            dados.save(arqDados);
        } catch (IOException e) {
            getLogger().warning("Nao consegui salvar dados.yml: " + e.getMessage());
        }
    }

    static final class Vazio extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override public Biome getBiome(WorldInfo info, int x, int y, int z) { return Biome.PLAINS; }
                @Override public List<Biome> getBiomes(WorldInfo info) { return List.of(Biome.PLAINS); }
            };
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, YSALA + 1, 0.5);
        }
    }

    private void criarMundo() {
        mundo = new WorldCreator(nome()).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo do Porteiro!");
            return;
        }
        mundo.setDifficulty(Difficulty.PEACEFUL);
        mundo.setGameRule(GameRules.KEEP_INVENTORY, true);
        mundo.setGameRule(GameRules.PVP, false);
        mundo.setGameRule(GameRules.SPAWN_MOBS, false);
        mundo.setGameRule(GameRules.SPAWN_MONSTERS, false);
        mundo.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        mundo.setGameRule(GameRules.SPAWN_PATROLS, false);
        mundo.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        mundo.setGameRule(GameRules.SPAWN_WARDENS, false);
        mundo.setGameRule(GameRules.ADVANCE_TIME, false);
        mundo.setGameRule(GameRules.ADVANCE_WEATHER, false);
        mundo.setGameRule(GameRules.MOB_GRIEFING, false);
        mundo.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        mundo.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        mundo.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        mundo.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        mundo.setGameRule(GameRules.LOCATOR_BAR, false);
        mundo.setTime(18000);
        mundo.setStorm(false);
        mundo.setSpawnLocation(sala());
        for (int cx = (-M - 6) >> 4; cx <= (M + 6) >> 4; cx++)
            for (int cz = (-M - 6) >> 4; cz <= (M + 8) >> 4; cz++) {
                mundo.getChunkAt(cx, cz).load(true);
                mundo.addPluginChunkTicket(cx, cz, this);
            }
        if (getConfig().getInt("mapa-feito", 0) != MAPA) {
            construir();
            getConfig().set("mapa-feito", MAPA);
            saveConfig();
        }
        for (Entity e : mundo.getEntities()) if (e instanceof ItemDisplay || e instanceof ArmorStand) e.remove();   // sobras
        lerMapa();
    }

    Location sala() {
        return new Location(mundo, 0.5, YSALA + 1, 0.5, 0f, 0f);
    }

    // ------------------------------------------------------------------ o Edificio Coroa

    private void b(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    private void caixa(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, m);
    }

    /** Parede de y=64 a 67 numa linha (x1,z1)-(x2,z2), com rodape escuro. */
    private void parede(int x1, int z1, int x2, int z2, Material m) {
        caixa(x1, Y, z1, x2, Y, z2, Material.DARK_OAK_PLANKS);
        caixa(x1, Y + 1, z1, x2, Y + 3, z2, m);
    }

    private void vao(int x1, int z1, int x2, int z2) {
        caixa(x1, Y, z1, x2, Y + 2, z2, Material.AIR);
    }

    private void luz(int x, int y, int z, int nivel) {
        org.bukkit.block.Block bl = mundo.getBlockAt(x, y, z);
        if (!bl.getType().isAir()) return;
        org.bukkit.block.data.type.Light l = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        l.setLevel(nivel);
        bl.setBlockData(l, false);
    }

    void construir() {
        Random r = new Random(15112026);
        caixa(-M - 2, Y - 2, -M - 2, M + 2, Y + 8, M + 6, Material.AIR);
        caixa(-M - 1, Y - 2, -M - 1, M + 1, Y - 2, M + 5, Material.BEDROCK);
        // piso: madeira gasta nos apartamentos, tapete vinho nos corredores, xadrez na portaria
        for (int x = -M; x <= M; x++)
            for (int z = -M; z <= M + 4; z++) {
                int ax = Math.abs(x), az = Math.abs(z);
                Material m = r.nextInt(7) == 0 ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
                if (ax <= 9 && az <= 9) m = (x + z) % 2 == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_DIORITE;
                else if (Math.max(ax, az) >= 10 && Math.max(ax, az) <= 12) m = r.nextInt(9) == 0 ? Material.NETHER_WART_BLOCK : Material.RED_TERRACOTTA;
                else if (z > M) m = Material.COBBLESTONE;
                b(x, Y - 1, z, m);
            }
        caixa(-M, Y + 4, -M, M, Y + 4, M, Material.DARK_OAK_PLANKS);                 // teto
        Material par = Material.MANGROVE_PLANKS, ext = Material.DEEPSLATE_BRICKS;
        // paredes de fora
        parede(-M, -M, M, -M, ext);
        parede(-M, M, M, M, ext);
        parede(-M, -M, -M, M, ext);
        parede(M, -M, M, M, ext);
        // paredes entre o corredor em anel (10..12) e os apartamentos (14..23), e entre o anel e a portaria (|x|,|z| <= 9)
        for (int s = -1; s <= 1; s += 2) {
            parede(-13, s * 13, 13, s * 13, par);
            parede(s * 13, -13, s * 13, 13, par);
            parede(-9, s * 9, 9, s * 9, Material.POLISHED_BLACKSTONE_BRICKS);
            parede(s * 9, -9, s * 9, 9, Material.POLISHED_BLACKSTONE_BRICKS);
            vao(-1, s * 9, 1, s * 9);       // 4 entradas da portaria
            vao(s * 9, -1, s * 9, 1);
        }
        caixa(-8, Y, -8, 8, Y + 3, 8, Material.AIR);
        // divisorias dos apartamentos
        for (int s = -1; s <= 1; s += 2) {
            parede(-12, s * 14, -12, s * 23, par);   // cantos
            parede(12, s * 14, 12, s * 23, par);
            parede(s * 14, 0, s * 23, 0, par);       // leste / oeste: dois apartamentos por lado
            parede(s * 14, -12, s * 23, -12, par);
            parede(s * 14, 12, s * 23, 12, par);
        }
        parede(0, -14, 0, -23, par);                 // norte: divisoria do meio
        parede(-2, 14, -2, 23, par);                 // sul: corredor da saida no meio
        parede(2, 14, 2, 23, par);
        vao(-1, 13, 1, 13);                          // do anel para o corredor da saida
        // portas (vaos de 1) dos apartamentos
        int[][] portas = {{-6, -13}, {6, -13}, {-7, 13}, {7, 13}, {-13, -6}, {-13, 6}, {13, -6}, {13, 6},
                {-12, -18}, {12, -18}, {-12, 18}, {12, 18}};
        for (int[] p : portas) caixa(p[0], Y, p[1], p[0], Y + 1, p[1], Material.AIR);
        // portao da saida (grade) e a calcada la fora
        caixa(-1, Y, M, 1, Y + 2, M, Material.IRON_BARS);
        caixa(-3, Y, M + 1, -3, Y + 3, M + 4, Material.BARRIER);
        caixa(3, Y, M + 1, 3, Y + 3, M + 4, Material.BARRIER);
        caixa(-3, Y, M + 5, 3, Y + 3, M + 5, Material.BARRIER);
        caixa(-3, Y + 4, M + 1, 3, Y + 4, M + 5, Material.BARRIER);
        b(-2, Y, M + 2, Material.LANTERN);
        b(2, Y, M + 2, Material.LANTERN);
        // portaria: balcao, pilares, sofas
        caixa(-3, Y, -5, 3, Y, -5, Material.DARK_OAK_PLANKS);
        b(-3, Y, -6, Material.DARK_OAK_PLANKS);
        b(3, Y, -6, Material.DARK_OAK_PLANKS);
        b(0, Y + 1, -5, Material.BELL);
        b(-2, Y + 1, -5, Material.LANTERN);
        for (int[] p : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) caixa(p[0], Y, p[1], p[0], Y + 3, p[1], Material.POLISHED_BLACKSTONE_BRICKS);
        for (int[] p : new int[][] {{-7, 2}, {-7, 3}, {7, 2}, {7, 3}, {-7, -3}, {7, -3}}) b(p[0], Y, p[1], Material.RED_WOOL);
        b(0, Y + 3, 0, Material.SOUL_LANTERN);
        // lanternas fracas nos corredores
        for (int[] p : new int[][] {{-12, -12}, {12, -12}, {-12, 12}, {12, 12}, {0, -12}, {-12, 0}, {12, 0}, {-1, 20}})
            if (mundo.getBlockAt(p[0], Y, p[1]).getType().isAir()) b(p[0], Y, p[1], Material.SOUL_LANTERN);
        // apartamentos: mobilia, armario (esconderijo) e a mesa da chave
        int[][] aps = {{-23, -23, -13, -14}, {-11, -23, -1, -14}, {1, -23, 11, -14}, {13, -23, 23, -14},
                {-23, 14, -13, 23}, {-11, 14, -3, 23}, {3, 14, 11, 23}, {13, 14, 23, 23},
                {-23, -11, -14, -1}, {-23, 1, -14, 11}, {14, -11, 23, -1}, {14, 1, 23, 11}};
        Material[] mov = {Material.BARREL, Material.BOOKSHELF, Material.COBWEB, Material.CAULDRON, Material.CRAFTING_TABLE, Material.LOOM,
                Material.RED_WOOL, Material.CHISELED_BOOKSHELF, Material.FLOWER_POT, Material.COBWEB};
        for (int[] a : aps) {
            // armario num canto: 1 celula fechada por 3 lados (parede, parede, tabua) e aberta por um
            int ax = a[0] + (r.nextBoolean() ? 0 : a[2] - a[0]), az = a[1] + (r.nextBoolean() ? 0 : a[3] - a[1]);
            int dx = ax == a[0] ? 1 : -1, dz = az == a[1] ? 1 : -1;
            caixa(ax + dx, Y, az, ax + dx, Y + 1, az, Material.DARK_OAK_PLANKS);
            b(ax, Y + 2, az, Material.DARK_OAK_SLAB);
            b(ax, Y - 1, az, Material.BLACK_WOOL);          // marca do armario (lida em lerMapa)
            // mesa da chave do lado oposto
            int mx = ax == a[0] ? a[2] - 1 : a[0] + 1, mz = az == a[1] ? a[3] - 1 : a[1] + 1;
            b(mx, Y, mz, Material.DARK_OAK_FENCE);
            b(mx, Y + 1, mz, Material.RED_CARPET);
            b(mx, Y - 1, mz, Material.STRIPPED_DARK_OAK_WOOD);          // marca da mesa (lida em lerMapa)
            for (int i = 0; i < 9; i++) {
                int x = a[0] + r.nextInt(a[2] - a[0] + 1), z = a[1] + r.nextInt(a[3] - a[1] + 1);
                if (!mundo.getBlockAt(x, Y, z).getType().isAir()) continue;
                if (Math.abs(x - ax) + Math.abs(z - az) <= 2 || Math.abs(x - mx) + Math.abs(z - mz) <= 1) continue;
                boolean porta = false;
                for (int[] p : portas) if (Math.abs(p[0] - x) + Math.abs(p[1] - z) <= 2) porta = true;
                if (porta) continue;
                Material m = mov[r.nextInt(mov.length)];
                b(x, Y, z, m);
                if (m == Material.BOOKSHELF && r.nextBoolean()) b(x, Y + 1, z, m);
            }
            luz((a[0] + a[2]) / 2, Y + 2, (a[1] + a[3]) / 2, 4);
        }
        // luz invisivel bem fraca nos corredores e na portaria (escuro, mas da para andar)
        for (int x = -M + 1; x < M; x += 4)
            for (int z = -M + 1; z < M; z += 4) {
                int mx = Math.max(Math.abs(x), Math.abs(z));
                if (mx <= 12) luz(x, Y + 2, z, mx <= 8 ? 6 : 5);
            }
        // sala de espera (em cima, fechada)
        caixa(-5, YSALA, -5, 5, YSALA, 5, Material.POLISHED_BLACKSTONE);
        caixa(-5, YSALA + 1, -5, 5, YSALA + 4, 5, Material.AIR);
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                if (Math.abs(x) == 5 || Math.abs(z) == 5) caixa(x, YSALA + 1, z, x, YSALA + 4, z, Material.DEEPSLATE_BRICKS);
                b(x, YSALA + 5, z, Material.DEEPSLATE_TILES);
            }
        for (int[] p : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) b(p[0], YSALA + 1, p[1], Material.SOUL_LANTERN);
        b(0, YSALA, 0, Material.REDSTONE_BLOCK);
        getLogger().info("Edificio Coroa construido.");
    }

    /** Le do mundo onde da para andar, onde ficam as mesas das chaves e os armarios. */
    private void lerMapa() {
        corredor.clear();
        mesas.clear();
        armarios.clear();
        todasCelulas.clear();
        for (int x = -M; x <= M; x++)
            for (int z = -M; z <= M; z++) {
                Material c = mundo.getBlockAt(x, Y, z).getType(), c2 = mundo.getBlockAt(x, Y + 1, z).getType(), ch = mundo.getBlockAt(x, Y - 1, z).getType();
                boolean livre = (c.isAir() || c == Material.LIGHT) && (c2.isAir() || c2 == Material.LIGHT);
                anda[x + M][z + M] = livre;
                if (ch == Material.STRIPPED_DARK_OAK_WOOD) mesas.add(new int[] {x, z});
                if (ch == Material.BLACK_WOOL) {
                    armarios.add(chave(x, z));
                    anda[x + M][z + M] = false;      // monstro nao entra no armario
                    continue;
                }
                if (!livre) continue;
                todasCelulas.add(new int[] {x, z});
                int mx = Math.max(Math.abs(x), Math.abs(z));
                if (mx >= 10 && mx <= 12) corredor.add(new int[] {x, z});
            }
        int ok = 0;
        for (int[] m : mesas) if (!rota(0, 0, m[0], m[1]).isEmpty()) ok++;
        if (ok != mesas.size() || rota(0, 0, 0, M - 1).isEmpty()) getLogger().warning("MAPA: so " + ok + " de " + mesas.size() + " mesas alcancaveis; saida " + !rota(0, 0, 0, M - 1).isEmpty());
        else getLogger().info("Mapa conferido: " + ok + " mesas e a saida alcancaveis a partir da portaria.");
    }

    static long chave(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Caminho mais curto (BFS em 4 direcoes) entre duas celulas; vazio se nao tem. */
    List<int[]> rota(int x1, int z1, int x2, int z2) {
        List<int[]> saida = new ArrayList<>();
        int a = x1 + M, bb = z1 + M, c = x2 + M, d = z2 + M;
        if (a < 0 || bb < 0 || c < 0 || d < 0 || a >= N || bb >= N || c >= N || d >= N) return saida;
        int[][] de = new int[N][N];
        for (int[] l : de) java.util.Arrays.fill(l, -2);
        ArrayDeque<int[]> fila = new ArrayDeque<>();
        fila.add(new int[] {a, bb});
        de[a][bb] = -1;
        int[][] dir = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean achou = false;
        while (!fila.isEmpty()) {
            int[] q = fila.poll();
            if (q[0] == c && q[1] == d) {
                achou = true;
                break;
            }
            for (int[] v : dir) {
                int nx = q[0] + v[0], nz = q[1] + v[1];
                if (nx < 0 || nz < 0 || nx >= N || nz >= N || de[nx][nz] != -2) continue;
                if (!anda[nx][nz] && !(nx == c && nz == d)) continue;
                de[nx][nz] = q[0] * N + q[1];
                fila.add(new int[] {nx, nz});
            }
        }
        if (!achou) return saida;
        int cx = c, cz = d;
        while (de[cx][cz] >= 0) {
            saida.add(0, new int[] {cx - M, cz - M});
            int p = de[cx][cz];
            cx = p / N;
            cz = p % N;
        }
        return saida;
    }

    // ================================================================== entrar e sair

    boolean aqui(Player p) {
        return mundo != null && p.getWorld().equals(mundo);
    }

    List<Player> todos() {
        List<Player> l = new ArrayList<>();
        if (mundo != null) for (Player p : mundo.getPlayers()) if (p.getGameMode() != GameMode.SPECTATOR && !p.isDead()) l.add(p);
        return l;
    }

    List<Player> vivos() {
        List<Player> l = new ArrayList<>();
        for (Player p : todos()) if (vidas.containsKey(p.getUniqueId()) && !fantasmas.contains(p.getUniqueId())) l.add(p);
        return l;
    }

    void entrar(Player p) {
        if (mundo == null) {
            p.sendMessage(PREFIXO + "§cO Porteiro está indisponível agora.");
            return;
        }
        if (aqui(p)) {
            p.sendMessage(PREFIXO + "Você já está no Edifício Coroa. Para sair: §f/porteiro sair");
            return;
        }
        String w = p.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        if (Set.of("vovo", "slender", "backrooms", "hotel", "defesa", "login", "naochore", "turno_noite", "fabrica", "arena", "esconde").contains(w)) {
            p.sendMessage(PREFIXO + "§cSaia do outro minigame primeiro.");
            return;
        }
        Location de = p.getLocation();
        dados.set("voltar." + p.getUniqueId(), de.getWorld().getName() + "," + de.getX() + "," + de.getY() + "," + de.getZ() + "," + de.getYaw() + "," + de.getPitch());
        salvar();
        p.teleport(sala());
        p.setFallDistance(0);
        p.showTitle(Title.title(txt("§4§lO PORTEIRO"), txt("§7Edifício Coroa, meia-noite. Ele não vê... mas ouve.")));
        p.sendMessage(PREFIXO + "§fAche as §echaves§f nos apartamentos e saia pelo §eportão da portaria§f.");
        p.sendMessage(PREFIXO + "§cO Porteiro é cego§7: §fagachado§7 você não faz barulho, e §fagachado dentro de um armário§7 ele não te acha.");
        p.sendMessage(PREFIXO + "§8A Sombra de Cartola§7 só anda quando §fninguém está olhando§7 para ela. Não tire os olhos!");
        p.sendMessage(PREFIXO + "§7Sair: §f/porteiro sair §8| §7Recordes: §f/porteiro top");
        if (fase == Fase.JOGO) p.sendMessage(PREFIXO + "§eTem uma partida rolando: você entra na próxima.");
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 0.5f);
        for (Player o : mundo.getPlayers()) if (o != p) o.sendMessage(PREFIXO + "§f" + p.getName() + " §7chegou ao Edifício Coroa.");
    }

    private void limpar(Player p) {
        UUID u = p.getUniqueId();
        vidas.remove(u);
        fantasmas.remove(u);
        imuneAte.remove(u);
        p.hideBossBar(barra);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    void sair(Player p, boolean avisar) {
        limpar(p);
        String s = dados.getString("voltar." + p.getUniqueId());
        Location volta = null;
        if (s != null) {
            String[] a = s.split(",");
            World w = Bukkit.getWorld(a[0]);
            if (w != null && !w.equals(mundo)) {
                try {
                    volta = new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Float.parseFloat(a[4]), Float.parseFloat(a[5]));
                } catch (Exception ignored) {
                }
            }
        }
        if (volta == null) {
            World lobby = Bukkit.getWorld("terror");
            volta = (lobby != null ? lobby : Bukkit.getWorlds().get(0)).getSpawnLocation();
        }
        dados.set("voltar." + p.getUniqueId(), null);
        if (!desligando) salvar();
        if (aqui(p)) p.teleport(volta);
        p.setFallDistance(0);
        if (avisar) p.sendMessage(PREFIXO + "Você saiu do Edifício Coroa.");
    }

    // ================================================================== a partida

    private void titulo(Player p, String a, String b2) {
        p.showTitle(Title.title(txt(a), txt(b2), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2800), Duration.ofMillis(500))));
    }

    private static String tempo(int s) {
        return Math.max(0, s) / 60 + ":" + String.format("%02d", Math.max(0, s) % 60);
    }

    private void limparPartida() {
        if (porteiro != null) porteiro.sumir();
        if (sombra != null) sombra.sumir();
        porteiro = null;
        sombra = null;
        for (ItemDisplay d : chaves) if (d.isValid()) d.remove();
        chaves.clear();
        if (mundo != null) caixa(-1, Y, M, 1, Y + 2, M, Material.IRON_BARS);
        portaoAberto = false;
        pegas = 0;
    }

    private int[] longe(double minimo) {
        List<int[]> l = new ArrayList<>(todasCelulas);
        Collections.shuffle(l, rnd);
        for (int[] c : l) {
            boolean ok = true;
            for (Player p : vivos()) if (Math.hypot(p.getX() - c[0], p.getZ() - c[1]) < minimo) ok = false;
            if (porteiro != null && Math.hypot(porteiro.x - c[0], porteiro.z - c[1]) < 6) ok = false;
            if (sombra != null && sombra.ativo && Math.hypot(sombra.x - c[0], sombra.z - c[1]) < 6) ok = false;
            if (ok) return c;
        }
        return l.get(0);
    }

    private void comecar(List<Player> gente) {
        limparPartida();
        vidas.clear();
        fantasmas.clear();
        imuneAte.clear();
        int max = Math.max(1, getConfig().getInt("maximo-jogadores", 4));
        List<Player> l = new ArrayList<>(gente.subList(0, Math.min(max, gente.size())));
        int v = l.size() == 1 ? getConfig().getInt("vidas-sozinho", 3) : getConfig().getInt("vidas-em-grupo", 2);
        totalChaves = Math.min(mesas.size(), Math.max(1, getConfig().getInt("chaves", 5)));
        List<int[]> ms = new ArrayList<>(mesas);
        Collections.shuffle(ms, rnd);
        for (int i = 0; i < totalChaves; i++) {
            int[] m = ms.get(i);
            chaves.add(mundo.spawn(new Location(mundo, m[0] + 0.5, Y + 1.45, m[1] + 0.5), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.TRIPWIRE_HOOK));
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(0xFFD24A));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
            }));
        }
        porteiro = new Bicho("figura", 0.5, -11.5, Color.fromRGB(0x6B1220), Material.SKELETON_SKULL);
        sombra = new Bicho("perseguidor", 22.5, -22.5, Color.fromRGB(0x0A0A0C), Material.WITHER_SKELETON_SKULL);
        sombra.ativo = false;
        sombra.repartir();
        t = getConfig().getInt("tempo-segundos", 600);
        int k = 0;
        for (Player p : l) {
            limpar(p);
            vidas.put(p.getUniqueId(), v);
            p.teleport(new Location(mundo, (k % 2 == 0 ? 2.5 : -1.5), Y, 3.5 + k / 2, 180f, 0f));
            k++;
            p.showBossBar(barra);
            titulo(p, "§4§lMEIA-NOITE", "§7Ache " + totalChaves + " chaves. Agache para não fazer barulho.");
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        }
        for (Player p : gente) if (!vidas.containsKey(p.getUniqueId())) p.sendMessage(PREFIXO + "§eA partida lotou (" + max + "). Você entra na próxima.");
        fase = Fase.JOGO;
        getLogger().info("Partida: " + l.size() + " jogador(es), " + totalChaves + " chaves.");
    }

    private void coronas(Player p, int v, String motivo) {
        if (v <= 0) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
        p.sendMessage(PREFIXO + "§6+" + v + " coronas §7" + motivo);
    }

    private boolean noArmario(Player p) {
        return p.isSneaking() && armarios.contains(chave(p.getLocation().getBlockX(), p.getLocation().getBlockZ()));
    }

    private void segundo() {
        if (mundo == null) return;
        List<Player> todos = todos();
        for (Player p : todos) {
            if (p.getY() < Y - 6 || (fase != Fase.JOGO && p.getY() < YSALA - 3) || (fase == Fase.JOGO && !vidas.containsKey(p.getUniqueId()) && p.getY() < YSALA - 3)) p.teleport(sala());
            if (p.getFoodLevel() < 20) p.setFoodLevel(20);
        }
        switch (fase) {
            case ESPERA -> {
                if (!todos.isEmpty()) {
                    fase = Fase.CONTAGEM;
                    t = getConfig().getInt("contagem-segundos", 15);
                }
            }
            case CONTAGEM -> {
                if (todos.isEmpty()) {
                    fase = Fase.ESPERA;
                    return;
                }
                for (Player p : todos) {
                    p.sendActionBar(txt("§cAs portas se trancam em §f" + t + "s §7(" + todos.size() + " jogador" + (todos.size() > 1 ? "es" : "") + ") §8— chame os amigos: §f/porteiro"));
                    if (t <= 5 && t > 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                }
                if (t-- <= 0) comecar(todos);
            }
            case JOGO -> {
                vidas.keySet().removeIf(u -> {
                    Player p = Bukkit.getPlayer(u);
                    return p == null || !aqui(p);
                });
                List<Player> vivos = vivos();
                if (vivos.isEmpty()) {
                    acabar(false, vidas.isEmpty() ? "§7Todo mundo saiu." : "§7O Porteiro trancou todo mundo para sempre.");
                    return;
                }
                if (t-- <= 0) {
                    acabar(false, "§7O sino tocou. Ninguém saiu a tempo.");
                    return;
                }
                int total = Math.max(1, getConfig().getInt("tempo-segundos", 600));
                barra.progress(Math.max(0f, Math.min(1f, t / (float) total)));
                barra.name(txt("§e🗝 " + pegas + "/" + totalChaves + (portaoAberto ? " §a§lPORTÃO ABERTO (sul)" : "") + " §8| §f" + tempo(t)));
                for (Player p : todos) {
                    Integer v = vidas.get(p.getUniqueId());
                    if (v == null) continue;
                    boolean f = fantasmas.contains(p.getUniqueId());
                    String s = f ? "§7Você virou fantasma: torça pelos outros" : "§c" + "❤".repeat(v) + (noArmario(p) ? " §8| §aescondido no armário" : p.isSneaking() ? " §8| §asilencioso" : p.isSprinting() ? " §8| §c§lBARULHO!" : " §8| §epassos...");
                    p.sendActionBar(txt(s));
                    // coracao batendo quando um deles esta perto
                    double d = Math.hypot(p.getX() - porteiro.x, p.getZ() - porteiro.z);
                    if (sombra.ativo) d = Math.min(d, Math.hypot(p.getX() - sombra.x, p.getZ() - sombra.z));
                    if (!f && d < 11) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, (float) (1.2 - d / 14), d < 5 ? 1.3f : 0.9f);
                }
                porteiro.repartir();
                sombra.repartir();
                if (t % 25 == 0) for (Player p : vivos) p.playSound(porteiro.loc(), "minecraft:hotel.passos", 1.2f, 0.8f);
            }
            case FIM -> {
                if (t-- <= 0) {
                    limparPartida();
                    for (Player p : todos) {
                        limpar(p);
                        p.teleport(sala());
                    }
                    vidas.clear();
                    fantasmas.clear();
                    fase = Fase.ESPERA;
                }
            }
        }
    }

    /** A cada 2 ticks: monstros andando, ouvindo, pegando; chaves; saida. */
    private void rapido() {
        if (fase != Fase.JOGO || porteiro == null) return;
        List<Player> vivos = vivos();
        if (vivos.isEmpty()) return;
        long agora = System.currentTimeMillis();
        // ---- chaves e saida
        for (Player p : vivos) {
            for (ItemDisplay d : new ArrayList<>(chaves)) {
                if (!d.isValid() || Math.hypot(d.getX() - p.getX(), d.getZ() - p.getZ()) > 1.6) continue;
                d.remove();
                chaves.remove(d);
                pegouChave(p);
            }
            if (portaoAberto && p.getZ() > M + 1.2) {
                acabar(true, "§a" + p.getName() + " abriu caminho!");
                return;
            }
        }
        // ---- o Porteiro: ouve e vai atras do som
        var pc = getConfig();
        double ouveC = pc.getDouble("porteiro.ouve-correndo", 18), ouveA = pc.getDouble("porteiro.ouve-andando", 8);
        Player ouvido = null;
        double melhor = 1e9;
        for (Player p : vivos) {
            if (p.isSneaking()) continue;
            Location ant = antes.get(p.getUniqueId());
            double mexeu = ant == null || ant.getWorld() != mundo ? 0 : Math.hypot(ant.getX() - p.getX(), ant.getZ() - p.getZ());
            if (mexeu < 0.04) continue;
            double d = Math.hypot(p.getX() - porteiro.x, p.getZ() - porteiro.z);
            double raio = (p.isSprinting() ? ouveC : ouveA) * (porteiro.parado > 0 ? 1.4 : 1.0);
            if (d <= raio && d < melhor) {
                melhor = d;
                ouvido = p;
            }
        }
        for (Player p : vivos) antes.put(p.getUniqueId(), p.getLocation());
        if (ouvido != null) {
            boolean novo = !porteiro.cacando;
            porteiro.cacando = true;
            porteiro.parado = 0;
            int ox = ouvido.getLocation().getBlockX(), oz = ouvido.getLocation().getBlockZ();
            if (novo || porteiro.alvo == null || Math.abs(porteiro.alvo[0] - ox) + Math.abs(porteiro.alvo[1] - oz) > 1) porteiro.ir(ox, oz);
            if (novo) {
                ouvido.sendActionBar(txt("§4§lELE TE OUVIU. §cAgache e se esconda!"));
                mundo.playSound(porteiro.loc(), Sound.ENTITY_WARDEN_LISTENING_ANGRY, 1.6f, 0.7f);
            }
        }
        double vel = (porteiro.cacando ? pc.getDouble("porteiro.velocidade-cacando", 4.6) + pegas * 0.2 : pc.getDouble("porteiro.velocidade-ronda", 2.6)) * 0.1;
        porteiro.quadro(porteiro.cacando ? 1 : 0);
        if (porteiro.parado > 0) {
            porteiro.parado -= 2;
            if (porteiro.parado <= 0) porteiro.cacando = false;
        } else if (porteiro.caminho.isEmpty() || porteiro.andar(vel)) {
            if (porteiro.cacando) porteiro.parado = 70;          // chegou onde ouviu: fica 3,5 s escutando
            else if (!corredor.isEmpty()) {
                int[] c = corredor.get(rnd.nextInt(corredor.size()));
                porteiro.ir(c[0], c[1]);
            }
        }
        for (Player p : vivos) {
            if (imuneAte.getOrDefault(p.getUniqueId(), 0L) > agora || noArmario(p)) continue;
            if (Math.hypot(p.getX() - porteiro.x, p.getZ() - porteiro.z) < 1.35) pegar(p, porteiro);
        }
        // ---- a Sombra de Cartola: so anda quando ninguem olha
        if (!sombra.ativo) return;
        boolean olhada = false;
        Player perto = null;
        double dp = 1e9;
        for (Player p : vivos) {
            double d = Math.hypot(p.getX() - sombra.x, p.getZ() - sombra.z);
            if (d < dp) {
                dp = d;
                perto = p;
            }
            if (d > 30 || p.hasPotionEffect(PotionEffectType.BLINDNESS)) continue;
            Vector para = new Vector(sombra.x - p.getX(), (Y + 1.2) - p.getEyeLocation().getY(), sombra.z - p.getZ()).normalize();
            if (p.getEyeLocation().getDirection().dot(para) > 0.6 && p.hasLineOfSight(sombra.boneco)) olhada = true;
        }
        if (olhada) {
            sombra.quadro(0);
            sombra.olhada += 2;
            if (sombra.olhada >= pc.getInt("sombra.some-depois-de-olhada-segundos", 10) * 20) {
                int[] c = longe(16);
                mundo.spawnParticle(Particle.LARGE_SMOKE, sombra.loc().add(0, 1, 0), 30, 0.3, 0.8, 0.3, 0.02);
                mundo.playSound(sombra.loc(), "minecraft:hotel.psst", 1.2f, 1f);
                sombra.pular(c[0] + 0.5, c[1] + 0.5);
                sombra.olhada = 0;
            }
        } else if (perto != null) {
            sombra.olhada = Math.max(0, sombra.olhada - 1);
            sombra.quadro(dp < 8 ? 1 : 0);
            if (sombra.caminho.isEmpty() || sombra.parado++ % 10 == 0) sombra.ir(perto.getLocation().getBlockX(), perto.getLocation().getBlockZ());
            sombra.andar((pc.getDouble("sombra.velocidade", 3.9) + pegas * 0.1) * 0.1);
        }
        for (Player p : vivos) {
            if (imuneAte.getOrDefault(p.getUniqueId(), 0L) > agora) continue;
            if (Math.hypot(p.getX() - sombra.x, p.getZ() - sombra.z) < 1.25) pegar(p, sombra);
        }
    }

    private void pegouChave(Player p) {
        pegas++;
        mundo.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 2.0f, 1.4f);
        coronas(p, getConfig().getInt("coronas.por-chave", 10), "pela chave");
        for (Player o : todos()) o.sendMessage(PREFIXO + "§e" + p.getName() + " §7achou uma chave! §e" + pegas + "/" + totalChaves);
        // a chave tilinta: o Porteiro vem ver
        porteiro.cacando = true;
        porteiro.parado = 0;
        porteiro.ir(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        p.sendActionBar(txt("§cA chave fez barulho... §fsaia daí!"));
        if (pegas == getConfig().getInt("sombra-depois-de-chaves", 2) && !sombra.ativo) {
            int[] c = longe(18);
            sombra.pular(c[0] + 0.5, c[1] + 0.5);
            sombra.ativo = true;
            sombra.repartir();
            for (Player o : vivos()) {
                titulo(o, "§8§lALGUÉM ESTÁ TE OLHANDO", "§7A Sombra de Cartola só anda quando ninguém olha para ela");
                o.playSound(o.getLocation(), "minecraft:hotel.sussurro_ataque", 1f, 0.8f);
            }
        }
        if (pegas >= totalChaves && !portaoAberto) {
            portaoAberto = true;
            caixa(-1, Y, M, 1, Y + 2, M, Material.AIR);
            for (Player o : todos()) {
                titulo(o, "§a§lO PORTÃO ABRIU!", "§7Corra para a saída: corredor do §fSUL§7 da portaria");
                o.playSound(o.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.5f, 0.6f);
            }
        }
    }

    private void pegar(Player p, Bicho quem) {
        UUID u = p.getUniqueId();
        int v = vidas.getOrDefault(u, 1) - 1;
        vidas.put(u, v);
        imuneAte.put(u, System.currentTimeMillis() + 6000);
        boolean ehPorteiro = quem == porteiro;
        p.playSound(p.getLocation(), ehPorteiro ? "minecraft:hotel.figura" : "minecraft:hotel.perseguidor", 1.6f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 0.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, false, false));
        titulo(p, ehPorteiro ? "§4§lO PORTEIRO TE PEGOU" : "§8§lA SOMBRA TE ALCANÇOU", v > 0 ? "§7Restam §c" + "❤".repeat(v) : "§7Você virou um fantasma do edifício");
        for (Player o : todos()) if (o != p) o.sendMessage(PREFIXO + "§c" + p.getName() + " §7foi pego " + (ehPorteiro ? "pelo Porteiro" : "pela Sombra de Cartola") + (v > 0 ? "." : " e virou fantasma."));
        if (v <= 0) {
            fantasmas.add(u);
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            p.sendMessage(PREFIXO + "§7Você virou fantasma: pode andar e assistir, mas não pega chaves.");
        }
        int[] c = longe(14);
        p.teleport(new Location(mundo, c[0] + 0.5, Y, c[1] + 0.5, p.getLocation().getYaw(), 0f));
        if (quem == sombra) {
            int[] s = longe(18);
            sombra.pular(s[0] + 0.5, s[1] + 0.5);
            sombra.olhada = 0;
        } else {
            porteiro.cacando = false;
            porteiro.caminho.clear();
        }
    }

    private void acabar(boolean fugiram, String sub) {
        fase = Fase.FIM;
        int total = getConfig().getInt("tempo-segundos", 600), levou = total - t;
        t = 9;
        if (fugiram) {
            int rec = dados.getInt("recorde.segundos", 0);
            List<String> nomes = new ArrayList<>();
            for (Player p : todos()) {
                if (!vidas.containsKey(p.getUniqueId())) continue;
                boolean f = fantasmas.contains(p.getUniqueId());
                nomes.add(p.getName());
                coronas(p, getConfig().getInt(f ? "coronas.fugiu-fantasma" : "coronas.fugiu", f ? 30 : 80), "por escapar do Edifício Coroa!");
                dados.set("fugas." + p.getUniqueId(), dados.getInt("fugas." + p.getUniqueId(), 0) + 1);
                dados.set("nome." + p.getUniqueId(), p.getName());
            }
            if (rec == 0 || levou < rec) {
                dados.set("recorde.segundos", levou);
                dados.set("recorde.quem", String.join(", ", nomes));
                Bukkit.broadcast(txt(PREFIXO + "§e§lNOVO RECORDE! §f" + String.join(", ", nomes) + " §7" + (nomes.size() > 1 ? "escaparam" : "escapou") + " do Porteiro em §e" + tempo(levou) + "§7. Tente bater: §f/porteiro"));
            }
            salvar();
        }
        for (Player p : todos()) {
            p.hideBossBar(barra);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (!vidas.containsKey(p.getUniqueId())) continue;
            titulo(p, fugiram ? "§a§lVOCÊS ESCAPARAM!" : "§4§lO EDIFÍCIO VENCEU", fugiram ? "§7Tempo: §e" + tempo(levou) : sub);
            p.playSound(p.getLocation(), fugiram ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WARDEN_DEATH, 0.8f, fugiram ? 1f : 0.6f);
            p.sendMessage(PREFIXO + (fugiram ? "§aVocês escaparam em §e" + tempo(levou) + "§a! " : "§cNão foi dessa vez. ") + sub + " §7Nova partida já já.");
        }
        if (porteiro != null) porteiro.sumir();
        if (sombra != null) sombra.sumir();
        getLogger().info("Fim da partida: " + (fugiram ? "escaparam em " + tempo(levou) : "perderam") + " (" + pegas + "/" + totalChaves + " chaves)");
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "entrar" : a[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("admin")) {
            if (!s.hasPermission("porteiro.admin")) return true;
            String o = a.length > 1 ? a[1] : "";
            switch (o) {
                case "refazer" -> {
                    limparPartida();
                    construir();
                    lerMapa();
                    s.sendMessage(PREFIXO + "Edifício refeito (" + mesas.size() + " mesas, " + armarios.size() + " armários).");
                }
                case "iniciar" -> {
                    if (fase != Fase.JOGO && !todos().isEmpty()) comecar(todos());
                }
                case "tempo" -> t = Integer.parseInt(a[2]);
                case "chave" -> {   // teste: da uma chave para o primeiro vivo
                    if (fase == Fase.JOGO && !chaves.isEmpty() && !vivos().isEmpty()) {
                        ItemDisplay d = chaves.remove(0);
                        d.remove();
                        pegouChave(vivos().get(0));
                    }
                }
                case "pegar" -> {   // teste: /porteiro admin pegar <jogador> <porteiro|sombra>
                    Player v = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : null;
                    if (fase == Fase.JOGO && v != null && vidas.containsKey(v.getUniqueId())) pegar(v, a.length > 3 && a[3].startsWith("s") ? sombra : porteiro);
                }
                default -> s.sendMessage(PREFIXO + "fase=" + fase + " t=" + t + " chaves=" + pegas + "/" + totalChaves + " vivos=" + vivos().size() + " fantasmas=" + fantasmas.size()
                        + (porteiro != null ? " porteiro=" + (int) porteiro.x + "," + (int) porteiro.z + (porteiro.cacando ? "(cacando)" : "") : "")
                        + (sombra != null ? " sombra=" + (sombra.ativo ? (int) sombra.x + "," + (int) sombra.z : "dormindo") : "")
                        + " portao=" + portaoAberto + " | admin <iniciar|refazer|tempo N|chave|pegar>");
            }
            return true;
        }
        if (sub.equals("top")) {
            s.sendMessage(PREFIXO + "§e§lEdifício Coroa");
            int rec = dados.getInt("recorde.segundos", 0);
            s.sendMessage(rec > 0 ? "§7Fuga mais rápida: §e" + tempo(rec) + " §7por §f" + dados.getString("recorde.quem", "?") : "§7Ninguém escapou ainda. Seja o primeiro!");
            List<String[]> l = new ArrayList<>();
            var sec = dados.getConfigurationSection("nome");
            if (sec != null) for (String u : sec.getKeys(false)) l.add(new String[] {sec.getString(u), String.valueOf(dados.getInt("fugas." + u, 0))});
            l.sort((x, y) -> Integer.compare(Integer.parseInt(y[1]), Integer.parseInt(x[1])));
            for (int i = 0; i < Math.min(10, l.size()); i++) s.sendMessage("§e" + (i + 1) + ". §f" + l.get(i)[0] + " §7— §a" + l.get(i)[1] + " fuga(s)");
            return true;
        }
        if (!(s instanceof Player p)) return true;
        if (sub.equals("sair")) {
            if (aqui(p)) sair(p, true);
            else p.sendMessage(PREFIXO + "Você não está no Edifício Coroa.");
        } else entrar(p);
        return true;
    }

    // ================================================================== eventos

    @EventHandler(priority = EventPriority.MONITOR)
    public void pacote(PlayerResourcePackStatusEvent e) {
        if (!PACOTE.equals(e.getID())) return;
        UUID id = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> COM_PACOTE.add(id);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> COM_PACOTE.remove(id);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void dano(EntityDamageEvent e) {
        if (mundo != null && e.getEntity().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (mundo != null && e.getEntity().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quebrar(BlockBreakEvent e) {
        if (aqui(e.getPlayer()) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void colocar(BlockPlaceEvent e) {
        if (aqui(e.getPlayer()) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void usar(PlayerInteractEvent e) {
        if (!aqui(e.getPlayer()) || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (e.getAction() == Action.PHYSICAL) return;
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler
    public void boneco(PlayerArmorStandManipulateEvent e) {
        if (aqui(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void atirar(ProjectileLaunchEvent e) {
        if (mundo != null && e.getEntity().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler
    public void planar(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && aqui(p) && e.isGliding()) e.setCancelled(true);
    }

    @EventHandler
    public void largar(PlayerDropItemEvent e) {
        if (aqui(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void nascer(CreatureSpawnEvent e) {
        if (mundo != null && e.getLocation().getWorld().equals(mundo) && !(e.getEntity() instanceof ArmorStand)) e.setCancelled(true);
    }

    @EventHandler
    public void mudou(PlayerChangedWorldEvent e) {
        if (mundo == null || !e.getFrom().equals(mundo)) return;
        limpar(e.getPlayer());
        dados.set("voltar." + e.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        COM_PACOTE.remove(e.getPlayer().getUniqueId());
        antes.remove(e.getPlayer().getUniqueId());
        if (aqui(e.getPlayer())) sair(e.getPlayer(), false);
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && aqui(p)) sair(p, false);
        }, 20L);
    }
}
