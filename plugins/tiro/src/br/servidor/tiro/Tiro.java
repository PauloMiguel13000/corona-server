package br.servidor.tiro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Jogos de tiro do Corona Server (15/11/2026, pedido do Paulo: "vai criar um lobby jogos de tiro, ai cada um vai
 * para um mundo diferente"). Este plugin tem o Lobby de Tiro (mundo "tiro"), o Apocalipse Zumbi (mundo "zumbi",
 * survival com mochila propria) e a Operacao Coroa (mundo "operacao", co-op). As armas e os infectados sao os
 * mesmos nos dois jogos (Armas e Zumbis).
 */
public final class Tiro extends JavaPlugin implements Listener {

    static final String PREFIXO = "§8[§2§lTiro§8] §7";
    static final String LOBBY = "tiro", ZUMBI = "zumbi", OPERACAO = "operacao", MISSAO = "missao";
    static final int Y = 100, VERSAO_LOBBY = 2;

    World lobby;
    Armas armas;
    Zumbis zumbis;
    Visual visual;
    Musica musica;
    Apocalipse apocalipse;
    Operacao operacao;
    Missao missao;
    File arqDados;
    YamlConfiguration dados;
    final Random rnd = new Random();
    private final Map<UUID, Long> esperaPortal = new HashMap<>();

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    boolean mundoDeTiro(World w) {
        String n = w.getName();
        return n.equals(ZUMBI) || n.equals(OPERACAO) || n.equals(MISSAO);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arqDados = new File(getDataFolder(), "dados.yml");
        dados = YamlConfiguration.loadConfiguration(arqDados);
        armas = new Armas(this);
        zumbis = new Zumbis(this);
        visual = new Visual(this);
        musica = new Musica(this);
        getServer().getPluginManager().registerEvents(visual, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(armas, this);
        getServer().getPluginManager().registerEvents(zumbis, this);
        criarLobby();
        apocalipse = new Apocalipse(this);
        getServer().getPluginManager().registerEvents(apocalipse, this);
        operacao = new Operacao(this);
        getServer().getPluginManager().registerEvents(operacao, this);
        try {
            missao = new Missao(this);
            getServer().getPluginManager().registerEvents(missao, this);
        } catch (Throwable t) {
            getLogger().warning("Missao nao subiu: " + t);
            missao = null;
        }
        armas.regras = new Armas.Regras() {
            @Override
            public boolean podeAtingir(Player a, org.bukkit.entity.LivingEntity alvo) {
                if (alvo instanceof Player) return a.getWorld().getName().equals(ZUMBI) && getConfig().getBoolean("zumbi.pvp", false);
                return !(alvo instanceof org.bukkit.entity.Villager);
            }

            @Override
            public void acertou(Player a, org.bukkit.entity.LivingEntity alvo, double dano, boolean cabeca) {
                if (a.getWorld().getName().equals(OPERACAO)) operacao.acertou(a, alvo, dano, cabeca);
                else if (missao != null && a.getWorld().getName().equals(MISSAO)) missao.acertou(a, alvo, cabeca);
            }
        };
        zumbis.dono = (z, t, matador) -> {
            if (z.getWorld().getName().equals(ZUMBI)) apocalipse.morreu(z, t, matador);
            else if (z.getWorld().getName().equals(OPERACAO)) operacao.morreu(z, t, matador);
            else if (missao != null && z.getWorld().getName().equals(MISSAO)) missao.morreu(z, t, matador);
        };
        Bukkit.getScheduler().runTaskTimer(this, this::salvar, 6000L, 6000L);
        getLogger().info("Jogos de tiro prontos (lobby, Apocalipse Zumbi e Operacao Coroa).");
    }

    @Override
    public void onDisable() {
        if (visual != null) visual.limpar();
        if (operacao != null) operacao.desligar();
        if (missao != null) missao.desligar();
        if (apocalipse != null) apocalipse.desligar();
        salvar();
    }

    void salvar() {
        try {
            dados.save(arqDados);
        } catch (IOException e) {
            getLogger().warning("Nao consegui salvar dados.yml: " + e.getMessage());
        }
    }

    void coronas(Player p, int v, String motivo) {
        if (v <= 0) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
        p.sendMessage(PREFIXO + "§6+" + v + " coronas §7" + motivo);
    }

    // ================================================================== lobby de tiro

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
            return new Location(world, 0.5, Y + 1, 0.5);
        }
    }

    static void regrasDeLobby(World w) {
        w.setDifficulty(Difficulty.PEACEFUL);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.SPAWN_WARDENS, false);
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
    }

    Location spawnLobby() {
        return new Location(lobby, 0.5, Y + 1, 6.5, 180f, 0f);
    }

    private void b(int x, int y, int z, Material m) {
        lobby.getBlockAt(x, y, z).setType(m, false);
    }

    // 16/11: placar dos recordes da Missao (um por dificuldade), do lado do portao norte
    private TextDisplay placarMissao;

    void atualizarPlacarMissao() {
        if (lobby == null) return;
        StringBuilder sb = new StringBuilder("§c§lRECORDES DA MISSÃO§r");
        for (int d = 0; d < Missao.DIF_NOME.length; d++) {
            int t = dados.getInt("missao.recorde." + d + ".tempo", 0);
            sb.append("\n").append(Missao.DIF_NOME[d]).append("§r§7: ")
                    .append(t <= 0 ? "§7ninguém ainda" : "§f" + String.format("%d:%02d", t / 60, t % 60) + " §7" + dados.getString("missao.recorde." + d + ".nomes", "?"));
        }
        sb.append("\n§7escolha a dificuldade na base");
        if (placarMissao != null && placarMissao.isValid()) placarMissao.text(txt(sb.toString()));
        else placarMissao = texto(7.5, Y + 2.9, -13.5, sb.toString(), 0.8f);
    }

    private TextDisplay texto(double x, double y, double z, String t, float escala) {
        return lobby.spawn(new Location(lobby, x, y, z), TextDisplay.class, d -> {
            d.text(txt(t));
            d.setBillboard(Display.Billboard.CENTER);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(escala, escala, escala), new org.joml.Quaternionf()));
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setPersistent(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(110, 0, 0, 0));
        });
    }

    private void criarLobby() {
        lobby = new WorldCreator(LOBBY).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (lobby == null) return;
        regrasDeLobby(lobby);
        lobby.setTime(12800);
        lobby.setSpawnLocation(spawnLobby());
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) {
            lobby.getChunkAt(cx, cz).load(true);
            lobby.addPluginChunkTicket(cx, cz, this);
        }
        if (getConfig().getInt("lobby-feito", 0) != VERSAO_LOBBY) {
            construirLobby();
            getConfig().set("lobby-feito", VERSAO_LOBBY);
            saveConfig();
        }
        for (Entity e : lobby.getEntities()) if (e instanceof TextDisplay) e.remove();
        texto(0.5, Y + 6.2, 0.5, "§2§lJOGOS DE TIRO\n§fEscolha um portão\n§7armas, munição e infectados", 1.6f);
        texto(-13.5, Y + 5.6, 0.5, "§2§lAPOCALIPSE ZUMBI\n§fSurvival: saqueie, construa, sobreviva\n§7mundo aberto • mochila própria\n§a▶ entre no portão", 1.1f);
        texto(14.5, Y + 5.6, 0.5, "§6§lMODO HORDA\n§fCo-op de 1 a 4: missões contra a horda\n§7classes • patentes • chefes\n§a▶ entre no portão", 1.1f);
        texto(0.5, Y + 5.6, -13.5, "§c§lMISSÃO\n§fCo-op de 1 a 4 contra soldados inimigos\n§75 setores até o Heliporto • eles atiram de volta\n§a▶ entre no portão", 1.1f);
        texto(0.5, Y + 3.2, 13.5, "§a§lVOLTAR AOS MINIGAMES\n§7pise na esmeralda", 0.9f);
        atualizarPlacarMissao();
    }

    private void construirLobby() {
        Random r = new Random(7);
        for (int x = -20; x <= 20; x++)
            for (int z = -20; z <= 20; z++) {
                double d = Math.sqrt(x * x + z * z);
                for (int y = Y - 2; y <= Y + 12; y++) b(x, y, z, Material.AIR);
                if (d > 18.5) continue;
                b(x, Y, z, d > 17 ? Material.POLISHED_DEEPSLATE : r.nextInt(6) == 0 ? Material.GRAVEL : r.nextInt(5) == 0 ? Material.COARSE_DIRT : Material.GRAY_CONCRETE_POWDER);
                b(x, Y - 1, z, Material.DEEPSLATE);
                if (d > 17.5) {                                         // mureta de sacos de areia + barreira
                    b(x, Y + 1, z, r.nextInt(4) == 0 ? Material.SMOOTH_SANDSTONE_SLAB : Material.SMOOTH_SANDSTONE);
                    for (int y = Y + 2; y <= Y + 6; y++) b(x, y, z, Material.BARRIER);
                }
            }
        // dois portoes (oeste = zumbi, leste = operacao): arco 5 de largura x 5 de altura
        for (int s = -1; s <= 1; s += 2) {
            int x = s * 15;
            Material m = s < 0 ? Material.MOSSY_STONE_BRICKS : Material.CUT_COPPER;
            for (int z = -3; z <= 3; z++)
                for (int y = 1; y <= 6; y++) if (Math.abs(z) == 3 || y == 6) b(x, Y + y, z, m);
            for (int z = -2; z <= 2; z++) b(x, Y, z, s < 0 ? Material.GREEN_CONCRETE : Material.ORANGE_CONCRETE);
            b(x, Y + 7, 0, Material.LANTERN);
            b(x - s, Y + 1, 4, Material.BARREL);
            b(x - s, Y + 1, -4, Material.BARREL);
            b(x - s, Y + 2, -4, s < 0 ? Material.ZOMBIE_HEAD : Material.TARGET);
        }
        // 15/11: terceiro portao, ao norte = Missao (soldados)
        for (int x = -3; x <= 3; x++)
            for (int y = 1; y <= 6; y++) if (Math.abs(x) == 3 || y == 6) b(x, Y + y, -15, Material.POLISHED_BLACKSTONE_BRICKS);
        for (int x = -2; x <= 2; x++) b(x, Y, -15, Material.RED_CONCRETE);
        b(0, Y + 7, -15, Material.LANTERN);
        b(4, Y + 1, -14, Material.BARREL);
        b(-4, Y + 1, -14, Material.BARREL);
        b(-4, Y + 2, -14, Material.TARGET);
        // centro: fogueira e caixotes
        b(0, Y + 1, 0, Material.CAMPFIRE);
        for (int[] c : new int[][] {{3, 3}, {-3, 3}, {4, -3}, {-4, -4}, {-3, -5}}) {
            b(c[0], Y + 1, c[1], Material.BARREL);
            if (r.nextBoolean()) b(c[0], Y + 2, c[1], Material.TNT);
        }
        for (int[] c : new int[][] {{8, 8}, {-8, 8}, {8, -8}, {-8, -8}}) {
            for (int y = 1; y <= 4; y++) b(c[0], Y + y, c[1], Material.SPRUCE_FENCE);
            b(c[0], Y + 5, c[1], Material.LANTERN);
        }
        // esmeralda de volta (sul)
        for (int x = -1; x <= 1; x++) for (int z = 12; z <= 14; z++) b(x, Y, z, Material.QUARTZ_BRICKS);
        b(0, Y, 13, Material.EMERALD_BLOCK);
        org.bukkit.block.data.type.Light luz = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(13);
        for (int x = -15; x <= 15; x += 5) for (int z = -15; z <= 15; z += 5)
            if (lobby.getBlockAt(x, Y + 3, z).getType().isAir()) lobby.getBlockAt(x, Y + 3, z).setBlockData(luz, false);
        getLogger().info("Lobby de Tiro construido.");
    }

    void irLobby(Player p) {
        if (lobby == null) return;
        p.teleport(spawnLobby());
        p.setFallDistance(0);
        p.showTitle(net.kyori.adventure.title.Title.title(txt("§2§lJOGOS DE TIRO"), txt("§7Zumbis a oeste • Horda a leste • Missão ao norte")));
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (lobby == null || to.getWorld() != lobby) return;
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        Player p = e.getPlayer();
        if (to.getY() < Y - 8) {
            p.teleport(spawnLobby());
            return;
        }
        long agora = System.currentTimeMillis();
        if (agora - esperaPortal.getOrDefault(p.getUniqueId(), 0L) < 2500) return;
        int x = to.getBlockX(), z = to.getBlockZ();
        String cmd = null;
        if (Math.abs(z) <= 2 && x <= -14 && x >= -16) cmd = "zumbi";
        else if (Math.abs(z) <= 2 && x >= 14 && x <= 16) cmd = "operacao";
        else if (Math.abs(x) <= 2 && z <= -14 && z >= -16) cmd = "missao";
        else if (x == 0 && z == 13) cmd = "minigames";
        if (cmd == null) return;
        esperaPortal.put(p.getUniqueId(), agora);
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        p.performCommand(cmd);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void dano(EntityDamageEvent e) {
        if (lobby != null && e.getEntity().getWorld() == lobby) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (lobby != null && e.getEntity().getWorld() == lobby) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quebrar(BlockBreakEvent e) {
        if (lobby != null && e.getBlock().getWorld() == lobby && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void colocar(BlockPlaceEvent e) {
        if (lobby != null && e.getBlock().getWorld() == lobby && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void usar(PlayerInteractEvent e) {
        if (lobby != null && e.getPlayer().getWorld() == lobby && e.getPlayer().getGameMode() != GameMode.CREATIVE && e.getClickedBlock() != null)
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler
    public void nascer(CreatureSpawnEvent e) {
        if (lobby != null && e.getLocation().getWorld() == lobby && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String c = cmd.getName().toLowerCase(java.util.Locale.ROOT);
        if (c.equals("zumbi")) return apocalipse.comando(s, a);
        if (c.equals("operacao")) return operacao.comando(s, a);
        if (c.equals("missao")) {
            if (missao == null) {
                s.sendMessage(PREFIXO + "§cA Missão está indisponível agora.");
                return true;
            }
            if (s instanceof Player pm && !missao.aqui(pm) && !pm.getWorld().getName().equals(LOBBY) && (a.length == 0 || !a[0].equalsIgnoreCase("admin"))) {
                irLobby(pm);
                pm.sendMessage(PREFIXO + "Entre pelo portão §cvermelho§7 (norte) ou digite §f/missao§7 de novo.");
                return true;
            }
            return missao.comando(s, a);
        }
        if (!(s instanceof Player p)) return true;
        String w = p.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        if (java.util.Set.of("vovo", "slender", "backrooms", "hotel", "defesa", "login", "naochore", "turno_noite", "fabrica", "arena", "esconde", "porteiro", OPERACAO, MISSAO).contains(w)) {
            p.sendMessage(PREFIXO + "§cSaia do outro jogo primeiro.");
            return true;
        }
        if (w.equals(ZUMBI)) {
            apocalipse.sair(p);
            return true;
        }
        irLobby(p);
        return true;
    }
}
