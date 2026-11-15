package br.servidor.esconde;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Esconde-esconde (14/11/2026, pedido no Discord: "faz um minigame de esconde esconde"; o Paulo quer rodando ja com
 * 3 jogadores). Um mundo so dele com a Vila (casas de dois andares, celeiro com feno, labirinto de sebe, tunel do
 * poco ate um porao, torre, arvores ocas, lago). Um jogador procura e os outros se escondem; quem e pego vira
 * pegador tambem (assim ninguem fica parado olhando). Ninguem perde item, nao tem dano, e os poderes ficam de fora.
 */
public final class Esconde extends JavaPlugin implements Listener {

    static final String PREFIXO = "§8[§e§lEsconde§8] §7";
    static final int Y = 64, M = 30, YSALA = 110, MAPA = 1;

    enum Fase { ESPERA, CONTAGEM, ESCONDER, CACA, FIM }

    private World mundo;
    private final Random rnd = new Random();
    private File arqDados;
    private YamlConfiguration dados;
    private Fase fase = Fase.ESPERA;
    private int t;
    private final Set<UUID> pegadores = new HashSet<>(), escondidos = new HashSet<>(), primeiros = new HashSet<>();
    private final Map<UUID, Integer> pegou = new HashMap<>();
    private UUID ultimoPegador;
    private Scoreboard placar;
    private Team timeEsc, timePeg;
    private boolean desligando;

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arqDados = new File(getDataFolder(), "dados.yml");
        dados = YamlConfiguration.loadConfiguration(arqDados);
        getServer().getPluginManager().registerEvents(this, this);
        criarMundo();
        placar = Bukkit.getScoreboardManager().getNewScoreboard();
        timeEsc = placar.registerNewTeam("escondidos");
        timeEsc.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        timeEsc.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        timePeg = placar.registerNewTeam("pegadores");
        timePeg.color(NamedTextColor.RED);
        timePeg.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        Bukkit.getScheduler().runTaskTimer(this, this::segundo, 40L, 20L);
        getLogger().info("Esconde-esconde pronto (mundo " + nome() + ").");
    }

    @Override
    public void onDisable() {
        desligando = true;
        for (Player p : mundo == null ? List.<Player>of() : new ArrayList<>(mundo.getPlayers())) {
            try {
                sair(p, false);
            } catch (Throwable ex) {
                getLogger().warning("Nao consegui tirar " + p.getName() + " do esconde-esconde ao desligar: " + ex);
            }
        }
        salvar();
    }

    private String nome() {
        return getConfig().getString("mundo", "esconde");
    }

    void salvar() {
        try {
            dados.save(arqDados);
        } catch (IOException e) {
            getLogger().warning("Nao consegui salvar dados.yml: " + e.getMessage());
        }
    }

    // ================================================================== mundo

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
            getLogger().severe("Nao consegui criar o mundo do esconde-esconde!");
            return;
        }
        mundo.setDifficulty(Difficulty.PEACEFUL);
        mundo.setGameRule(GameRules.KEEP_INVENTORY, true);
        mundo.setGameRule(GameRules.PVP, true);   // o toque do pegador e um "ataque" (sem dano)
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
        mundo.setTime(6000);
        mundo.setStorm(false);
        mundo.setThundering(false);
        mundo.setSpawnLocation(sala());
        mundo.getWorldBorder().setCenter(0.5, 0.5);
        mundo.getWorldBorder().setSize(2 * (M + 6));
        if (getConfig().getInt("mapa-feito", 0) != MAPA) {
            construir();
            getConfig().set("mapa-feito", MAPA);
            saveConfig();
        }
    }

    Location sala() {
        return new Location(mundo, 0.5, YSALA + 1, 0.5, 180f, 0f);
    }

    Location praca() {
        double a = rnd.nextDouble() * Math.PI * 2, d = 2.5 + rnd.nextDouble() * 2;
        return new Location(mundo, Math.cos(a) * d + 0.5, Y, Math.sin(a) * d + 0.5, rnd.nextInt(360) - 180, 0f);
    }

    // ------------------------------------------------------------------ construcao da Vila

    private void b(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    private void caixa(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, m);
    }

    private void paredes(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) if (x == x1 || x == x2 || z == z1 || z == z2) b(x, y, z, m);
    }

    private void folha(int x, int y, int z, Material m) {
        org.bukkit.block.Block bl = mundo.getBlockAt(x, y, z);
        bl.setType(m, false);
        if (bl.getBlockData() instanceof org.bukkit.block.data.type.Leaves lv) {
            lv.setPersistent(true);
            bl.setBlockData(lv, false);
        }
    }

    void construir() {
        Random r = new Random(20261114);
        for (int cx = (-M - 6) >> 4; cx <= (M + 6) >> 4; cx++)
            for (int cz = (-M - 6) >> 4; cz <= (M + 6) >> 4; cz++) mundo.getChunkAt(cx, cz).load(true);
        // limpa e faz o chao
        caixa(-M - 2, 57, -M - 2, M + 2, 90, M + 2, Material.AIR);
        caixa(-M - 2, 56, -M - 2, M + 2, 56, M + 2, Material.BEDROCK);
        caixa(-M - 2, 57, -M - 2, M + 2, 62, M + 2, Material.STONE);
        caixa(-M - 2, 63, -M - 2, M + 2, 63, M + 2, Material.GRASS_BLOCK);
        // muro da vila (e barreira em cima, para ninguem pular)
        paredes(-M - 1, 64, -M - 1, M + 1, 68, M + 1, Material.STONE_BRICKS);
        paredes(-M - 1, 69, -M - 1, M + 1, 69, M + 1, Material.MOSSY_STONE_BRICKS);
        paredes(-M - 1, 70, -M - 1, M + 1, 92, M + 1, Material.BARRIER);
        // ruas em cruz e a praca
        for (int i = -M; i <= M; i++)
            for (int k = -1; k <= 1; k++) {
                b(i, 63, k, r.nextInt(4) == 0 ? Material.COARSE_DIRT : Material.GRAVEL);
                b(k, 63, i, r.nextInt(4) == 0 ? Material.COARSE_DIRT : Material.GRAVEL);
            }
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) if (x * x + z * z <= 30) b(x, 63, z, r.nextInt(3) == 0 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
        poco();
        casa(14, -5, 22, 3, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, 2, 'O', r);
        casa(-24, -6, -14, 2, Material.BRICKS, Material.OAK_PLANKS, 2, 'L', r);
        casa(-5, -26, 5, -18, Material.COBBLESTONE, Material.BIRCH_PLANKS, 1, 'S', r);
        porao();
        celeiro(r);
        labirinto(r);
        torre();
        lago();
        for (int[] s : new int[][] {{8, -8}, {-8, -8}, {8, 8}, {-8, 8}}) barraca(s[0], s[1], r);
        for (int[] a : new int[][] {{-12, -14}, {9, -13}, {26, 7}, {-28, -20}, {-28, 5}, {3, 28}, {28, 28}, {5, 11}, {-6, 12}, {27, -12}}) arvore(a[0], a[1]);
        // moitas espalhadas na grama
        Material[] moita = {Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.OAK_LEAVES};
        for (int i = 0; i < 70; i++) {
            int x = r.nextInt(2 * M - 3) - M + 2, z = r.nextInt(2 * M - 3) - M + 2;
            Material m = moita[r.nextInt(moita.length)];
            for (int k = 0; k < 3; k++) {
                int xx = x + (k == 1 ? 1 : 0), zz = z + (k == 2 ? 1 : 0);
                if (mundo.getBlockAt(xx, 63, zz).getType() != Material.GRASS_BLOCK || !mundo.getBlockAt(xx, 64, zz).getType().isAir()) continue;
                folha(xx, 64, zz, m);
                if (r.nextBoolean()) folha(xx, 65, zz, m);
            }
        }
        // caixotes perto do celeiro e da torre
        for (int[] c : new int[][] {{8, 14}, {8, 15}, {7, 15}, {20, -20}, {21, -20}, {20, -21}, {-12, 5}, {-12, 6}}) {
            if (!mundo.getBlockAt(c[0], 64, c[1]).getType().isAir()) continue;
            b(c[0], 64, c[1], Material.BARREL);
            if (r.nextBoolean()) b(c[0], 65, c[1], Material.BARREL);
        }
        sala(true);
        getLogger().info("Vila do esconde-esconde construida.");
    }

    /** Sala de espera la em cima (chao fechado, para ninguem espiar o mapa). */
    private void sala(boolean fazer) {
        caixa(-8, YSALA, -8, 8, YSALA, 8, Material.SMOOTH_QUARTZ);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) if ((x + z) % 4 == 0) b(x, YSALA, z, Material.YELLOW_CONCRETE);
        caixa(-8, YSALA + 1, -8, 8, YSALA + 5, 8, Material.AIR);
        paredes(-8, YSALA + 1, -8, 8, YSALA + 5, 8, Material.BARRIER);
        paredes(-8, YSALA + 1, -8, 8, YSALA + 1, 8, Material.YELLOW_STAINED_GLASS);
        caixa(-8, YSALA + 6, -8, 8, YSALA + 6, 8, Material.BARRIER);
        for (int[] c : new int[][] {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) b(c[0], YSALA, c[1], Material.SEA_LANTERN);
    }

    private void poco() {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                b(dx, 64, dz, Material.MOSSY_COBBLESTONE);
                if (dx != 0 && dz != 0) {
                    b(dx, 65, dz, Material.OAK_FENCE);
                    b(dx, 66, dz, Material.OAK_FENCE);
                }
                b(dx, 67, dz, Material.SPRUCE_SLAB);
            }
        b(0, 67, 0, Material.SPRUCE_SLAB);
        for (int y = 58; y <= 63; y++) b(0, y, 0, Material.LADDER);   // encostada no bloco do sul
    }

    /** Casa: paredes de 4 de altura por andar, porta (vao) no lado dado, janelas, escada de mao no canto e moveis. */
    private void casa(int x1, int z1, int x2, int z2, Material parede, Material piso, int andares, char porta, Random r) {
        int topo = 64 + andares * 5 - 1;
        caixa(x1, 63, z1, x2, 63, z2, piso);
        paredes(x1, 64, z1, x2, topo, z2, parede);
        for (int a = 0; a < andares; a++) {
            int yb = 64 + a * 5;
            caixa(x1, yb + 4, z1, x2, yb + 4, z2, a == andares - 1 ? Material.SPRUCE_PLANKS : piso);   // teto / piso de cima
            b((x1 + x2) / 2, yb + 4, (z1 + z2) / 2, Material.SHROOMLIGHT);
            // janelas
            for (int k = -1; k <= 1; k += 2) {
                b((x1 + x2) / 2 + k * 2, yb + 1, z1, Material.GLASS);
                b((x1 + x2) / 2 + k * 2, yb + 1, z2, Material.GLASS);
                b(x1, yb + 1, (z1 + z2) / 2 + k * 2, Material.GLASS);
                b(x2, yb + 1, (z1 + z2) / 2 + k * 2, Material.GLASS);
            }
            // escada de mao no canto (encostada na parede do sul) e o buraco no teto
            for (int y = yb; y <= yb + 4; y++) b(x1 + 1, y, z2 - 1, Material.LADDER);
            // parede no meio com passagem
            int zm = (z1 + z2) / 2;
            for (int x = x1 + 1; x <= x2 - 1; x++) if (x != x1 + 2 && x != x2 - 2) caixa(x, yb, zm, x, yb + 3, zm, piso);
            // moveis
            Material[] mov = {Material.BARREL, Material.BOOKSHELF, Material.HAY_BLOCK, Material.CRAFTING_TABLE, Material.COMPOSTER, Material.LOOM, Material.BOOKSHELF, Material.BARREL};
            for (int i = 0; i < 9; i++) {
                int x = x1 + 1 + r.nextInt(x2 - x1 - 1), z = z1 + 1 + r.nextInt(z2 - z1 - 1);
                if (z == zm || (x == x1 + 1 && z >= z2 - 2) || !mundo.getBlockAt(x, yb, z).getType().isAir()) continue;
                if (x == x1 + 2 || x == x2 - 2) continue;   // nao tampa as passagens
                Material m = mov[r.nextInt(mov.length)];
                b(x, yb, z, m);
                if ((m == Material.BOOKSHELF || m == Material.BARREL) && r.nextBoolean()) b(x, yb + 1, z, m);
            }
            // "guarda-roupa": um cantinho fechado por estantes com entrada pelo lado
            b(x2 - 2, yb, z1 + 1, Material.BOOKSHELF);
            b(x2 - 2, yb + 1, z1 + 1, Material.BOOKSHELF);
            b(x2 - 1, yb + 1, z1 + 2, Material.BOOKSHELF);
            b(x2 - 1, yb, z1 + 1, Material.AIR);
            b(x2 - 1, yb + 1, z1 + 1, Material.AIR);
            b(x2 - 1, yb, z1 + 2, Material.AIR);
        }
        // parapeito no telhado (da para subir e se esconder la)
        paredes(x1, topo + 1, z1, x2, topo + 1, z2, parede);
        // porta (vao de 1 x 2) e o caminho ate ela
        int px = (x1 + x2) / 2, pz = (z1 + z2) / 2;
        switch (porta) {
            case 'O' -> caixa(x1, 64, pz - 1, x1, 65, pz - 1, Material.AIR);
            case 'L' -> caixa(x2, 64, pz - 1, x2, 65, pz - 1, Material.AIR);
            case 'S' -> caixa(px, 64, z2, px, 65, z2, Material.AIR);
            default -> caixa(px, 64, z1, px, 65, z1, Material.AIR);
        }
    }

    /** Porao embaixo da casa do leste, ligado ao poco da praca por um tunel. */
    private void porao() {
        caixa(1, 58, 0, 14, 59, 0, Material.AIR);          // tunel
        for (int x = 4; x <= 13; x += 5) b(x, 60, 0, Material.SHROOMLIGHT);
        caixa(15, 58, -4, 21, 60, 2, Material.AIR);        // porao
        caixa(15, 57, -4, 21, 57, 2, Material.COBBLESTONE);
        b(18, 61, -1, Material.SHROOMLIGHT);
        for (int[] c : new int[][] {{16, -4}, {17, -4}, {16, -3}, {20, -4}, {21, -3}, {19, 1}, {18, 2}, {16, 2}}) {
            b(c[0], 58, c[1], Material.BARREL);
            if ((c[0] + c[1]) % 2 == 0) b(c[0], 59, c[1], Material.BARREL);
        }
        for (int y = 58; y <= 63; y++) b(21, y, 2, Material.LADDER);   // sobe para dentro da casa
    }

    private void celeiro(Random r) {
        int x1 = 10, z1 = 12, x2 = 24, z2 = 24;
        caixa(x1, 63, z1, x2, 63, z2, Material.COARSE_DIRT);
        paredes(x1, 64, z1, x2, 69, z2, Material.SPRUCE_PLANKS);
        for (int x = x1; x <= x2; x += 7) for (int z = z1; z <= z2; z += 6) caixa(x, 64, z, x, 69, z, Material.SPRUCE_LOG);
        caixa(x1, 70, z1, x2, 70, z2, Material.DARK_OAK_PLANKS);
        caixa(16, 64, z1, 18, 66, z1, Material.AIR);                       // portao
        caixa(x1 + 1, 67, 18, x2 - 1, 67, z2 - 1, Material.SPRUCE_PLANKS);  // mezanino
        for (int y = 64; y <= 67; y++) b(x1 + 1, y, z2 - 1, Material.LADDER);
        b(17, 70, 15, Material.SHROOMLIGHT);
        b(17, 70, 21, Material.SHROOMLIGHT);
        for (int x = x1 + 2; x <= x2 - 1; x += 2)
            for (int z = z1 + 3; z <= z2 - 1; z += 2) {
                int h = r.nextInt(3);
                for (int k = 0; k < h; k++) if (mundo.getBlockAt(x, 64 + k, z).getType().isAir()) b(x, 64 + k, z, Material.HAY_BLOCK);
            }
        for (int x = x1 + 3; x <= x2 - 1; x += 3) if (r.nextBoolean()) b(x, 68, z2 - 2, Material.HAY_BLOCK);
    }

    /** Labirinto de sebe 17 x 17 (caminhos de 1 bloco, sebe de 3 de altura). */
    private void labirinto(Random r) {
        int ox = -26, oz = 10, n = 8, g = 2 * n + 1;
        boolean[][] livre = new boolean[g][g];
        boolean[][] visto = new boolean[n][n];
        java.util.ArrayDeque<int[]> pilha = new java.util.ArrayDeque<>();
        pilha.push(new int[] {0, 0});
        visto[0][0] = true;
        livre[1][1] = true;
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!pilha.isEmpty()) {
            int[] c = pilha.peek();
            List<int[]> op = new ArrayList<>();
            for (int[] v : d) {
                int nx = c[0] + v[0], nz = c[1] + v[1];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !visto[nx][nz]) op.add(v);
            }
            if (op.isEmpty()) {
                pilha.pop();
                continue;
            }
            int[] v = op.get(r.nextInt(op.size()));
            livre[2 * c[0] + 1 + v[0]][2 * c[1] + 1 + v[1]] = true;
            visto[c[0] + v[0]][c[1] + v[1]] = true;
            livre[2 * (c[0] + v[0]) + 1][2 * (c[1] + v[1]) + 1] = true;
            pilha.push(new int[] {c[0] + v[0], c[1] + v[1]});
        }
        for (int i = 0; i < 10; i++) livre[1 + r.nextInt(g - 2)][1 + r.nextInt(g - 2)] = true;   // alguns atalhos
        livre[g - 1][1] = true;      // entrada do leste
        livre[7][0] = true;          // entrada do norte
        livre[g - 1][g - 4] = true;
        for (int i = 0; i < g; i++)
            for (int k = 0; k < g; k++) {
                b(ox + i, 63, oz + k, Material.PODZOL);
                if (!livre[i][k]) for (int y = 64; y <= 66; y++) folha(ox + i, y, oz + k, Material.OAK_LEAVES);
            }
    }

    private void torre() {
        int x1 = 22, z1 = -27, x2 = 26, z2 = -23;
        caixa(x1, 63, z1, x2, 63, z2, Material.STONE_BRICKS);
        paredes(x1, 64, z1, x2, 75, z2, Material.STONE_BRICKS);
        caixa(x1, 76, z1, x2, 76, z2, Material.STONE_BRICKS);
        paredes(x1, 77, z1, x2, 77, z2, Material.STONE_BRICK_WALL);
        for (int y = 64; y <= 76; y++) b(x1 + 1, y, z2 - 1, Material.LADDER);
        caixa(24, 64, z2, 24, 65, z2, Material.AIR);
        caixa(x1 + 1, 70, z1 + 1, x2 - 1, 70, z2 - 2, Material.OAK_PLANKS);   // andar do meio
        b(24, 76, -25, Material.SHROOMLIGHT);
        b(24, 68, z1, Material.GLASS);
        b(x2, 73, -25, Material.GLASS);
    }

    private void lago() {
        for (int x = -5; x <= 5; x++)
            for (int z = 15; z <= 25; z++) {
                double dd = x * x + (z - 20) * (z - 20);
                if (dd > 22) continue;
                b(x, 63, z, Material.WATER);
                b(x, 62, z, dd < 8 ? Material.WATER : Material.SAND);
                if (dd < 8) b(x, 61, z, Material.SAND);
            }
        caixa(0, 64, 15, 0, 64, 19, Material.SPRUCE_SLAB);   // pier
        for (int[] c : new int[][] {{-3, 17}, {3, 22}, {-2, 23}, {4, 18}}) if (mundo.getBlockAt(c[0], 63, c[1]).getType() == Material.WATER) b(c[0], 64, c[1], Material.LILY_PAD);
    }

    private void barraca(int x, int z, Random r) {
        Material[] la = {Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIGHT_BLUE_WOOL, Material.LIME_WOOL};
        Material cor = la[r.nextInt(la.length)];
        for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) caixa(x + dx, 64, z + dz, x + dx, 66, z + dz, Material.OAK_FENCE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b(x + dx, 67, z + dz, (dx + dz) % 2 == 0 ? cor : Material.WHITE_WOOL);
        b(x, 64, z - 1, Material.BARREL);
        b(x + 1, 64, z, Material.COMPOSTER);
        b(x - 1, 64, z, Material.BARREL);
    }

    /** Arvore com a copa oca: sobe pela escada de mao no tronco e fica escondido la dentro. */
    private void arvore(int x, int z) {
        if (!mundo.getBlockAt(x, 64, z).getType().isAir() || !mundo.getBlockAt(x, 64, z - 1).getType().isAir()) return;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = 68; y <= 71; y++) {
                    boolean dentro = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && y >= 69 && y <= 70;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && y == 71) continue;
                    if (dentro) b(x + dx, y, z + dz, Material.AIR);
                    else folha(x + dx, y, z + dz, Material.OAK_LEAVES);
                }
        caixa(x, 64, z, x, 70, z, Material.OAK_LOG);
        for (int y = 64; y <= 69; y++) b(x, y, z - 1, Material.LADDER);
        b(x, 68, z - 1, Material.LADDER);
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

    void entrar(Player p) {
        if (mundo == null) {
            p.sendMessage(PREFIXO + "§cO esconde-esconde está indisponível agora.");
            return;
        }
        if (aqui(p)) {
            p.sendMessage(PREFIXO + "Você já está no esconde-esconde. Para sair: §f/esconde sair");
            return;
        }
        String w = p.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        if (Set.of("vovo", "slender", "backrooms", "hotel", "defesa", "login", "naochore", "turno_noite", "fabrica", "arena").contains(w)) {
            p.sendMessage(PREFIXO + "§cSaia do outro minigame primeiro.");
            return;
        }
        Location de = p.getLocation();
        dados.set("voltar." + p.getUniqueId(), de.getWorld().getName() + "," + de.getX() + "," + de.getY() + "," + de.getZ() + "," + de.getYaw() + "," + de.getPitch());
        salvar();
        p.teleport(sala());
        p.setScoreboard(placar);
        p.setFallDistance(0);
        int min = getConfig().getInt("minimo-jogadores", 3);
        p.showTitle(Title.title(txt("§e§lESCONDE-ESCONDE"), txt("§7Um procura, os outros se escondem na Vila")));
        p.sendMessage(PREFIXO + "§fBem-vindo! §7Um jogador procura e os outros se escondem. §eQuem é pego vira pegador também.");
        p.sendMessage(PREFIXO + "§7A rodada começa com §f" + min + " jogadores§7. Sair: §f/esconde sair §8| §7Ranking: §f/esconde top");
        if (fase == Fase.ESCONDER || fase == Fase.CACA) p.sendMessage(PREFIXO + "§eTem uma rodada rolando: você entra na próxima.");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        for (Player o : mundo.getPlayers()) if (o != p) o.sendMessage(PREFIXO + "§f" + p.getName() + " §7entrou no esconde-esconde.");
        int n = todos().size();
        if (n < min && fase == Fase.ESPERA)
            Bukkit.broadcast(txt(PREFIXO + "§f" + p.getName() + " §7está esperando no §eEsconde-esconde §7(" + n + "/" + min + "). Entre também: §f/esconde"));
    }

    private void limpar(Player p) {
        UUID u = p.getUniqueId();
        pegadores.remove(u);
        escondidos.remove(u);
        timeEsc.removeEntry(p.getName());
        timePeg.removeEntry(p.getName());
        p.setGlowing(false);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    void sair(Player p, boolean avisar) {
        limpar(p);
        if (p.getScoreboard() == placar) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
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
            World lobby = Bukkit.getWorld("minigames");
            volta = (lobby != null ? lobby : Bukkit.getWorlds().get(0)).getSpawnLocation();
        }
        dados.set("voltar." + p.getUniqueId(), null);
        if (!desligando) salvar();
        if (aqui(p)) p.teleport(volta);
        p.setFallDistance(0);
        if (avisar) p.sendMessage(PREFIXO + "Você saiu do esconde-esconde.");
    }

    // ================================================================== a rodada

    private void barra(Player p, String s) {
        p.sendActionBar(txt(s));
    }

    private void titulo(Player p, String a, String b2) {
        p.showTitle(Title.title(txt(a), txt(b2), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(400))));
    }

    private Player jog(UUID u) {
        Player p = Bukkit.getPlayer(u);
        return p != null && aqui(p) ? p : null;
    }

    private void segundo() {
        if (mundo == null) return;
        List<Player> todos = todos();
        int min = Math.max(2, getConfig().getInt("minimo-jogadores", 3));
        for (Player p : todos) {   // caiu no vazio / saiu do mapa
            if (p.getY() < 50) p.teleport(fase == Fase.CACA || (fase == Fase.ESCONDER && escondidos.contains(p.getUniqueId())) ? praca() : sala());
            if (p.getFoodLevel() < 20) p.setFoodLevel(20);
        }
        switch (fase) {
            case ESPERA -> {
                if (todos.size() >= min) {
                    fase = Fase.CONTAGEM;
                    t = getConfig().getInt("contagem-segundos", 20);
                } else for (Player p : todos) barra(p, "§eEsperando jogadores §f" + todos.size() + "/" + min + " §7— chame os amigos: §f/esconde");
            }
            case CONTAGEM -> {
                if (todos.size() < min) {
                    fase = Fase.ESPERA;
                    return;
                }
                for (Player p : todos) {
                    barra(p, "§aA rodada começa em §f" + t + "s §7(" + todos.size() + " jogadores)");
                    if (t <= 5 && t > 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                if (t-- <= 0) comecar(todos);
            }
            case ESCONDER -> {
                if (conferirGente()) return;
                for (UUID u : escondidos) {
                    Player p = jog(u);
                    if (p != null) barra(p, "§a§lESCONDA-SE! §fO pegador sai em " + t + "s");
                }
                for (UUID u : pegadores) {
                    Player p = jog(u);
                    if (p != null) barra(p, "§c§lVOCÊ PROCURA! §fVocê desce em " + t + "s");
                }
                if (t-- <= 0) soltar();
            }
            case CACA -> {
                if (conferirGente()) return;
                int dica = Math.max(10, getConfig().getInt("dica-a-cada-segundos", 40));
                int total = getConfig().getInt("rodada-segundos", 240);
                int brilho = getConfig().getInt("brilho-nos-ultimos-segundos", 25);
                boolean miar = t < total && t > brilho && (total - t) % dica == 0;
                for (UUID u : escondidos) {
                    Player p = jog(u);
                    if (p == null) continue;
                    barra(p, "§aEscondido §8| §fRestam " + escondidos.size() + " §8| §e" + tempo(t));
                    if (miar) {
                        mundo.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1.4f, 1f);
                        mundo.spawnParticle(Particle.NOTE, p.getLocation().add(0, 2.3, 0), 1);
                        p.sendMessage(PREFIXO + "§eVocê miou! §7O pegador pode ter ouvido...");
                    }
                    if (t == brilho) {
                        p.setGlowing(true);
                        titulo(p, "§e§lVOCÊ ESTÁ BRILHANDO", "§7Corra! Faltam " + brilho + "s");
                    }
                }
                for (UUID u : pegadores) {
                    Player p = jog(u);
                    if (p == null) continue;
                    barra(p, "§c§lPEGADOR §8| §fFaltam " + escondidos.size() + " §8| §e" + tempo(t));
                    if (miar) p.sendMessage(PREFIXO + "§eOs escondidos miaram: §7siga o som!");
                    if (t == brilho) titulo(p, "§e§lELES ESTÃO BRILHANDO", "§7Dá para ver pelas paredes!");
                }
                if (escondidos.isEmpty()) acabar(false);
                else if (t-- <= 0) acabar(true);
            }
            case FIM -> {
                if (t-- <= 0) {
                    for (Player p : todos) {
                        limpar(p);
                        p.teleport(sala());
                    }
                    pegadores.clear();
                    escondidos.clear();
                    fase = Fase.ESPERA;
                }
            }
        }
    }

    private static String tempo(int s) {
        return Math.max(0, s) / 60 + ":" + String.format("%02d", Math.max(0, s) % 60);
    }

    /** Alguem saiu no meio: troca o pegador ou encerra. Devolve true se a rodada acabou. */
    private boolean conferirGente() {
        pegadores.removeIf(u -> jog(u) == null);
        escondidos.removeIf(u -> jog(u) == null);
        if (pegadores.size() + escondidos.size() < 2) {
            for (Player p : todos()) p.sendMessage(PREFIXO + "§cA rodada parou: faltou gente.");
            t = 0;
            fase = Fase.FIM;
            return true;
        }
        if (pegadores.isEmpty()) {
            List<UUID> l = new ArrayList<>(escondidos);
            UUID novo = l.get(rnd.nextInt(l.size()));
            Player p = jog(novo);
            virarPegador(p, null);
            if (fase == Fase.ESCONDER) {
                p.teleport(sala());
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * (t + 2), 0, false, false, false));
            }
            for (Player o : todos()) o.sendMessage(PREFIXO + "§eO pegador saiu. Agora quem procura é §c" + p.getName() + "§e!");
        }
        return false;
    }

    private void comecar(List<Player> todos) {
        pegadores.clear();
        escondidos.clear();
        primeiros.clear();
        pegou.clear();
        List<Player> l = new ArrayList<>(todos);
        Collections.shuffle(l, rnd);
        if (l.size() > 1 && l.get(0).getUniqueId().equals(ultimoPegador)) Collections.swap(l, 0, 1);   // nao repete o pegador
        int np = l.size() >= getConfig().getInt("dois-pegadores-a-partir-de", 7) ? 2 : 1;
        t = getConfig().getInt("esconder-segundos", 35);
        for (int i = 0; i < l.size(); i++) {
            Player p = l.get(i);
            limpar(p);
            p.setScoreboard(placar);
            if (i < np) {
                pegadores.add(p.getUniqueId());
                primeiros.add(p.getUniqueId());
                timePeg.addEntry(p.getName());
                p.teleport(sala());
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * (t + 2), 0, false, false, false));
                titulo(p, "§c§lVOCÊ PROCURA!", "§7Espere os outros se esconderem (" + t + "s)");
                p.sendMessage(PREFIXO + "§cVocê é o pegador. §7Quando descer, §fbata §7em quem achar para pegar.");
            } else {
                escondidos.add(p.getUniqueId());
                timeEsc.addEntry(p.getName());
                p.teleport(praca());
                titulo(p, "§a§lESCONDA-SE!", "§7Você tem " + t + "s. Casas, celeiro, labirinto, poço...");
                p.sendMessage(PREFIXO + "§aEsconda-se! §7Dá para subir em árvores, entrar no poço da praça e nos telhados. §eDe tempos em tempos você mia.");
            }
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.5f, 1.4f);
        }
        ultimoPegador = l.get(0).getUniqueId();
        fase = Fase.ESCONDER;
        getLogger().info("Rodada: " + l.size() + " jogadores, pegador " + l.get(0).getName());
    }

    private void soltar() {
        t = getConfig().getInt("rodada-segundos", 240);
        fase = Fase.CACA;
        for (UUID u : pegadores) {
            Player p = jog(u);
            if (p == null) continue;
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.teleport(new Location(mundo, 0.5, Y, 3.5, 180f, 0f));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            p.setGlowing(true);
            titulo(p, "§c§lVALENDO!", "§7Ache todo mundo em " + tempo(t));
        }
        for (UUID u : escondidos) {
            Player p = jog(u);
            if (p == null) continue;
            titulo(p, "§c§lO PEGADOR SAIU!", "§7Fique quieto... ou fuja");
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.5f);
        }
    }

    private void virarPegador(Player p, Player quem) {
        escondidos.remove(p.getUniqueId());
        pegadores.add(p.getUniqueId());
        timeEsc.removeEntry(p.getName());
        timePeg.addEntry(p.getName());
        p.setGlowing(true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        if (quem != null) titulo(p, "§c§lPEGO!", "§7Agora você ajuda a procurar");
    }

    private void coronas(Player p, int v, String motivo) {
        if (v <= 0) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
        p.sendMessage(PREFIXO + "§6+" + v + " coronas §7" + motivo);
    }

    private void pegar(Player quem, Player p) {
        int total = getConfig().getInt("rodada-segundos", 240);
        int ficou = Math.max(0, total - t);
        virarPegador(p, quem);
        pegou.merge(quem.getUniqueId(), 1, Integer::sum);
        dados.set("pegou." + quem.getUniqueId(), dados.getInt("pegou." + quem.getUniqueId(), 0) + 1);
        dados.set("nome." + quem.getUniqueId(), quem.getName());
        mundo.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        mundo.spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.2);
        for (Player o : todos()) o.sendMessage(PREFIXO + "§c" + quem.getName() + " §7pegou §f" + p.getName() + "§7! Restam §a" + escondidos.size() + "§7.");
        coronas(quem, getConfig().getInt("coronas.por-pegar", 15), "por pegar " + p.getName());
        coronas(p, ficou / 30 * getConfig().getInt("coronas.a-cada-30s-escondido", 5), "por ficar " + tempo(ficou) + " escondido");
    }

    private void acabar(boolean escondidosVenceram) {
        fase = Fase.FIM;
        t = 8;
        List<String> vivos = new ArrayList<>();
        for (UUID u : escondidos) {
            Player p = jog(u);
            if (p == null) continue;
            vivos.add(p.getName());
            coronas(p, getConfig().getInt("coronas.sobreviveu", 60), "por não ser pego!");
            dados.set("sobreviveu." + u, dados.getInt("sobreviveu." + u, 0) + 1);
            dados.set("nome." + u, p.getName());
        }
        if (!escondidosVenceram) for (UUID u : primeiros) {
            Player p = jog(u);
            if (p != null) coronas(p, getConfig().getInt("coronas.pegou-todos", 30), "por achar todo mundo!");
        }
        salvar();
        String sub = escondidosVenceram ? "§7Ninguém achou: §a" + String.join(", ", vivos) : "§7Todo mundo foi pego!";
        for (Player p : todos()) {
            p.setGlowing(false);
            titulo(p, escondidosVenceram ? "§a§lOS ESCONDIDOS VENCERAM" : "§c§lOS PEGADORES VENCERAM", sub);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
            p.sendMessage(PREFIXO + (escondidosVenceram ? "§aOs escondidos venceram! " : "§cOs pegadores venceram! ") + sub + " §7Nova rodada já já.");
        }
        getLogger().info("Fim da rodada: " + (escondidosVenceram ? "escondidos venceram (" + vivos + ")" : "pegadores venceram"));
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "entrar" : a[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("admin")) {
            if (!s.hasPermission("esconde.admin")) return true;
            String o = a.length > 1 ? a[1] : "";
            if (o.equals("refazer")) {
                construir();
                s.sendMessage(PREFIXO + "Vila refeita.");
            } else if (o.equals("iniciar")) {
                List<Player> l = todos();
                if (l.size() < 2) s.sendMessage(PREFIXO + "Precisa de 2 jogadores no mundo.");
                else if (fase == Fase.ESPERA || fase == Fase.CONTAGEM) comecar(l);
            } else if (o.equals("pegar") && a.length > 3) {   // teste: /esconde admin pegar <pegador> <escondido>
                Player q = Bukkit.getPlayerExact(a[2]), v = Bukkit.getPlayerExact(a[3]);
                if (q != null && v != null && fase == Fase.CACA && pegadores.contains(q.getUniqueId()) && escondidos.contains(v.getUniqueId())) pegar(q, v);
                else s.sendMessage(PREFIXO + "nao deu (fase=" + fase + ")");
            } else if (o.equals("tempo") && a.length > 2) {
                t = Integer.parseInt(a[2]);
            } else s.sendMessage(PREFIXO + "fase=" + fase + " t=" + t + " pegadores=" + pegadores.size() + " escondidos=" + escondidos.size() + " | admin <iniciar|refazer|tempo N>");
            return true;
        }
        if (sub.equals("top")) {
            List<String[]> l = new ArrayList<>();
            var sec = dados.getConfigurationSection("nome");
            if (sec != null) for (String u : sec.getKeys(false))
                l.add(new String[] {sec.getString(u), String.valueOf(dados.getInt("pegou." + u, 0)), String.valueOf(dados.getInt("sobreviveu." + u, 0))});
            l.sort((x, y) -> Integer.compare(Integer.parseInt(y[1]) + 3 * Integer.parseInt(y[2]), Integer.parseInt(x[1]) + 3 * Integer.parseInt(x[2])));
            s.sendMessage(PREFIXO + "§e§lMelhores do Esconde-esconde");
            if (l.isEmpty()) s.sendMessage("§7Ninguém jogou ainda. Seja o primeiro!");
            for (int i = 0; i < Math.min(10, l.size()); i++)
                s.sendMessage("§e" + (i + 1) + ". §f" + l.get(i)[0] + " §7— pegou §c" + l.get(i)[1] + " §7| sobreviveu §a" + l.get(i)[2] + "x");
            return true;
        }
        if (!(s instanceof Player p)) return true;
        if (sub.equals("sair")) {
            if (aqui(p)) sair(p, true);
            else p.sendMessage(PREFIXO + "Você não está no esconde-esconde.");
        } else entrar(p);
        return true;
    }

    // ================================================================== eventos

    /** O toque do pegador: qualquer golpe corpo a corpo em quem esta escondido (sem dano). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void toque(EntityDamageByEntityEvent e) {
        if (mundo == null || !e.getEntity().getWorld().equals(mundo)) return;
        e.setCancelled(true);
        if (fase != Fase.CACA || !(e.getDamager() instanceof Player quem) || !(e.getEntity() instanceof Player p)) return;
        if (pegadores.contains(quem.getUniqueId()) && escondidos.contains(p.getUniqueId())) pegar(quem, p);
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

    /** Nada de usar item nem mexer em bloco (perola, balde, bau...). So andar, pular e subir escada. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void usar(PlayerInteractEvent e) {
        if (!aqui(e.getPlayer()) || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (e.getAction() == Action.PHYSICAL) return;
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
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
        if (mundo != null && e.getLocation().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler
    public void explodir(EntityExplodeEvent e) {
        if (mundo != null && e.getLocation().getWorld().equals(mundo)) e.blockList().clear();
    }

    @EventHandler
    public void fogo(BlockIgniteEvent e) {
        if (mundo != null && e.getBlock().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler
    public void queimar(BlockBurnEvent e) {
        if (mundo != null && e.getBlock().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler
    public void folhas(LeavesDecayEvent e) {
        if (mundo != null && e.getBlock().getWorld().equals(mundo)) e.setCancelled(true);
    }

    /** Saiu do mundo por outro caminho (/spawn, /home...): tira da rodada. */
    @EventHandler
    public void mudou(PlayerChangedWorldEvent e) {
        if (mundo == null || !e.getFrom().equals(mundo)) return;
        Player p = e.getPlayer();
        limpar(p);
        if (p.getScoreboard() == placar) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        dados.set("voltar." + p.getUniqueId(), null);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        if (aqui(e.getPlayer())) sair(e.getPlayer(), false);
    }

    /** Entrou no servidor ja dentro do mundo (caiu no meio da rodada): volta para a sala de espera. */
    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && aqui(p)) sair(p, false);
        }, 20L);
    }
}
