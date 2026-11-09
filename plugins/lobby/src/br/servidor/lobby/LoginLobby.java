package br.servidor.lobby;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lobby de login leve. Quem entra e ainda nao fez /login vai para uma ilhazinha num mundo vazio
 * (poucos chunks, sem criaturas, sem dano, sem fome, sem PvP), faz o login sem lag e volta
 * exatamente para onde estava. O pet do BabyPets fica guardado enquanto a pessoa esta no lobby.
 */
public final class LoginLobby extends JavaPlugin implements Listener {

    static final int VERSAO_LOBBY = 1;
    static final int Y = 100;

    private World mundo;
    private NamespacedKey kTexto;
    private File arquivoVoltar;
    private YamlConfiguration voltar;
    /** Quem esta sendo levado de volta agora (para nao confundir com outras trocas de mundo). */
    private final Set<UUID> voltando = new HashSet<>();
    private SpawnDecor spawn;

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kTexto = new NamespacedKey(this, "texto");
        arquivoVoltar = new File(getDataFolder(), "voltar.yml");
        voltar = YamlConfiguration.loadConfiguration(arquivoVoltar);
        criarMundo();
        if (mundo == null) return;
        if (getConfig().getInt("versao-lobby", 0) != VERSAO_LOBBY || mundo.getBlockAt(0, Y, 0).getType().isAir()) construir();
        else textos();
        getServer().getPluginManager().registerEvents(this, this);
        spawn = new SpawnDecor(this);
        getServer().getPluginManager().registerEvents(spawn, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            int feito = getConfig().getInt("spawn-enfeite-versao", 0);
            if (getConfig().getBoolean("enfeitar-spawn", true) && feito != SpawnDecor.VERSAO) {
                if (feito >= 1) spawn.atualizar();
                else spawn.construir();
                getConfig().set("spawn-enfeite-versao", SpawnDecor.VERSAO);
                saveConfig();
            }
            spawn.iniciar();
        }, 60L);
        // quem ja estava logado no lobby (reload) e devolvido; quem acabou de fazer login tambem
        Bukkit.getScheduler().runTaskTimer(this, this::verificar, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::vigiarPets, 40L, 5L);
        getLogger().info("Lobby de login pronto no mundo '" + mundo.getName() + "'.");
    }

    @Override
    public void onDisable() {
        salvarVoltar();
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
                @Override
                public Biome getBiome(WorldInfo info, int x, int y, int z) {
                    return Biome.MEADOW;
                }

                @Override
                public List<Biome> getBiomes(WorldInfo info) {
                    return List.of(Biome.MEADOW);
                }
            };
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return pontoDoLobby(world);
        }
    }

    static Location pontoDoLobby(World w) {
        return new Location(w, 0.5, Y + 1, 6.5, 180, 0);
    }

    private void criarMundo() {
        String nome = getConfig().getString("mundo", "login");
        mundo = new WorldCreator(nome).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo do lobby!");
            return;
        }
        mundo.setDifficulty(Difficulty.PEACEFUL);
        mundo.setPVP(false);
        mundo.setSpawnLocation(pontoDoLobby(mundo));
        mundo.setGameRule(GameRules.SPAWN_MOBS, false);
        mundo.setGameRule(GameRules.SPAWN_MONSTERS, false);
        mundo.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        mundo.setGameRule(GameRules.SPAWN_PATROLS, false);
        mundo.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        mundo.setGameRule(GameRules.ADVANCE_TIME, false);
        mundo.setGameRule(GameRules.ADVANCE_WEATHER, false);
        mundo.setGameRule(GameRules.FALL_DAMAGE, false);
        mundo.setGameRule(GameRules.FIRE_DAMAGE, false);
        mundo.setGameRule(GameRules.DROWNING_DAMAGE, false);
        mundo.setGameRule(GameRules.FREEZE_DAMAGE, false);
        mundo.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        mundo.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        mundo.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        mundo.setGameRule(GameRules.LOCATOR_BAR, false);
        mundo.setTime(6000);
        mundo.setStorm(false);
        mundo.setThundering(false);
        try {
            mundo.setViewDistance(3);
            mundo.setSimulationDistance(2);
        } catch (Throwable t) {
            getLogger().info("Distancia de visao por mundo nao suportada: " + t.getMessage());
        }
        mundo.getWorldBorder().setCenter(0, 0);
        mundo.getWorldBorder().setSize(64);
    }

    private boolean noLobby(Entity e) {
        return mundo != null && e.getWorld().equals(mundo);
    }

    // ================================================================== a ilha

    private void b(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    private void d(int x, int y, int z, String dados) {
        mundo.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(dados), false);
    }

    void construir() {
        for (int cx = -2; cx <= 1; cx++)
            for (int cz = -2; cz <= 1; cz++) mundo.getChunkAt(cx, cz).load(true);
        Random r = new Random(7);
        // limpa
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++)
                for (int y = Y - 12; y <= Y + 14; y++) b(x, y, z, Material.AIR);
        // ilha flutuante: grama em cima, terra e pedra afinando para baixo
        for (int x = -11; x <= 11; x++)
            for (int z = -11; z <= 11; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > 10.5) continue;
                b(x, Y, z, Material.GRASS_BLOCK);
                int fundo = (int) Math.round((10.5 - dist) * 0.8) + 1;
                for (int k = 1; k <= fundo; k++) b(x, Y - k, z, k <= 2 ? Material.DIRT : (r.nextInt(5) == 0 ? Material.MOSSY_COBBLESTONE : Material.STONE));
            }
        // praca: anel de pedra e quatro caminhos
        for (int x = -11; x <= 11; x++)
            for (int z = -11; z <= 11; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > 10.5) continue;
                boolean anel = dist >= 3.0 && dist <= 4.6;
                boolean caminho = (Math.abs(x) <= 1 || Math.abs(z) <= 1) && dist > 4.6 && dist <= 10.5;
                if (anel || caminho) b(x, Y, z, r.nextInt(4) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
            }
        // fonte no meio
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > 2.9) continue;
                b(x, Y, z, Material.QUARTZ_BRICKS);
                if (dist > 1.9) b(x, Y + 1, z, Material.QUARTZ_BRICKS);
                else if (dist > 0.1) b(x, Y + 1, z, Material.WATER);
            }
        for (int y = Y + 1; y <= Y + 3; y++) d(0, y, 0, "minecraft:quartz_pillar[axis=y]");
        d(0, Y + 4, 0, "minecraft:lantern[hanging=false,waterlogged=false]");
        // flores na grama
        Material[] flores = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.ALLIUM, Material.OXEYE_DAISY, Material.AZURE_BLUET, Material.LILY_OF_THE_VALLEY};
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                if (mundo.getBlockAt(x, Y, z).getType() != Material.GRASS_BLOCK) continue;
                if (!mundo.getBlockAt(x, Y + 1, z).getType().isAir()) continue;
                int n = r.nextInt(10);
                if (n < 3) b(x, Y + 1, z, flores[r.nextInt(flores.length)]);
                else if (n < 5) b(x, Y + 1, z, Material.SHORT_GRASS);
            }
        // cerejeiras pequenas nos quatro cantos
        for (int[] c : new int[][] {{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) arvore(c[0], c[1], r);
        // bancos virados para a fonte e postes com lanterna
        banco(5, 2, "west");
        banco(5, -3, "west");
        banco(-5, 2, "east");
        banco(-5, -3, "east");
        banco(2, -5, "south");
        banco(-3, -5, "south");
        poste(4, 4);
        poste(-4, 4);
        poste(4, -4);
        poste(-4, -4);
        // parede invisivel em volta (ninguem cai) e um teto de barreira bem alto
        for (int x = -13; x <= 13; x++)
            for (int z = -13; z <= 13; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > 10.5 && dist <= 12.0)
                    for (int y = Y; y <= Y + 4; y++) b(x, y, z, Material.BARRIER);
            }
        getConfig().set("versao-lobby", VERSAO_LOBBY);
        saveConfig();
        textos();
        getLogger().info("Ilha do lobby construida.");
    }

    private void arvore(int x, int z, Random r) {
        for (int y = Y + 1; y <= Y + 4; y++) d(x, y, z, "minecraft:cherry_log[axis=y]");
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 3; dy <= 6; dy++) {
                    int raio = dy == 6 ? 1 : 2;
                    if (Math.abs(dx) > raio || Math.abs(dz) > raio) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && r.nextBoolean()) continue;
                    if (dx == 0 && dz == 0 && dy <= 4) continue;
                    d(x + dx, Y + dy, z + dz, "minecraft:cherry_leaves[persistent=true,distance=1,waterlogged=false]");
                }
        b(x, Y, z, Material.GRASS_BLOCK);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && mundo.getBlockAt(x + dx, Y + 1, z + dz).getType().isAir())
                    d(x + dx, Y + 1, z + dz, "minecraft:pink_petals[facing=north,flower_amount=" + (1 + r.nextInt(4)) + "]");
    }

    private void banco(int x, int z, String costas) {
        boolean emX = costas.equals("west") || costas.equals("east");
        for (int k = 0; k <= 1; k++) {
            int bx = emX ? x : x + k, bz = emX ? z + k : z;
            d(bx, Y + 1, bz, "minecraft:spruce_stairs[facing=" + costas + ",half=bottom,shape=straight,waterlogged=false]");
        }
    }

    private void poste(int x, int z) {
        for (int y = Y + 1; y <= Y + 2; y++) d(x, y, z, "minecraft:dark_oak_fence[east=false,west=false,north=false,south=false,waterlogged=false]");
        d(x, Y + 3, z, "minecraft:lantern[hanging=false,waterlogged=false]");
    }

    private void textos() {
        for (int cx = -1; cx <= 0; cx++)
            for (int cz = -1; cz <= 0; cz++) mundo.getChunkAt(cx, cz).load(true);
        for (Entity e : mundo.getEntities())
            if (e.getPersistentDataContainer().has(kTexto, PersistentDataType.BYTE)) e.remove();
        texto(new Location(mundo, 0.5, Y + 6.2, 0.5), "§6§lCORONA SERVER", 2.2f);
        texto(new Location(mundo, 0.5, Y + 5.0, 0.5),
                "§fBem-vindo!\n§eJá tem conta? §fdigite §a/login <sua senha>\n§eNovo por aqui? §fdigite §a/register <senha> <senha>\n§7Exemplo: §f/register corona123 corona123\n§7No celular (Bedrock) você entra direto, sem senha.", 1.0f);
    }

    private void texto(Location l, String conteudo, float escala) {
        mundo.spawn(l, TextDisplay.class, t -> {
            t.text(txt(conteudo));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setPersistent(true);
            t.setViewRange(1.0f);
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(escala, escala, escala), new org.joml.AxisAngle4f()));
            t.getPersistentDataContainer().set(kTexto, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ================================================================== login

    static boolean logado(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AuthMe")) return true;
        try {
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Object api = c.getMethod("getInstance").invoke(null);
            if (api == null) return true;
            return (Boolean) c.getMethod("isAuthenticated", Player.class).invoke(api, p);
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean ativo() {
        return mundo != null && getConfig().getBoolean("ativo", true);
    }

    /** Mundos de minigame: quem saiu de dentro deles volta para o spawn (o proprio minigame devolve as coisas). */
    private static boolean mundoDeMinigame(World w) {
        String n = w.getName().toLowerCase();
        return n.equals("vovo") || n.equals("slender") || n.equals("defesa") || n.equals("backrooms") || n.equals("hotel") || n.equals("naochore") || n.equals("arena")
                || n.equals("turno_noite") || n.equals("fabrica");
    }

    Location spawnNovatos() {
        String s = getConfig().getString("spawn-novatos", "");
        if (s != null && !s.isBlank()) {
            String[] p = s.trim().split("[ ,]+");
            try {
                World w = Bukkit.getWorld(p[0]);
                if (w != null) return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        p.length > 4 ? Float.parseFloat(p[4]) : 0, p.length > 5 ? Float.parseFloat(p[5]) : 0);
            } catch (Exception ignored) {
            }
        }
        File f = new File(getDataFolder().getParentFile(), "Spawn/location.yml");
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            World w = Bukkit.getWorld(y.getString("spawn.world", ""));
            if (w != null) return new Location(w, y.getDouble("spawn.x"), y.getDouble("spawn.y"), y.getDouble("spawn.z"),
                    (float) y.getDouble("spawn.yaw"), (float) y.getDouble("spawn.pitch"));
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void salvarVoltar() {
        try {
            voltar.save(arquivoVoltar);
        } catch (Exception e) {
            getLogger().warning("Nao consegui salvar voltar.yml: " + e);
        }
    }

    private void lembrar(Player p, Location l) {
        String k = p.getUniqueId().toString();
        voltar.set(k + ".nome", p.getName());
        voltar.set(k + ".mundo", l.getWorld().getName());
        voltar.set(k + ".x", l.getX());
        voltar.set(k + ".y", l.getY());
        voltar.set(k + ".z", l.getZ());
        voltar.set(k + ".yaw", l.getYaw());
        voltar.set(k + ".pitch", l.getPitch());
        salvarVoltar();
    }

    private Location lembrado(Player p) {
        String k = p.getUniqueId().toString();
        if (!voltar.contains(k + ".mundo")) return null;
        World w = Bukkit.getWorld(voltar.getString(k + ".mundo", ""));
        if (w == null || w.equals(mundo) || mundoDeMinigame(w)) return null;
        return new Location(w, voltar.getDouble(k + ".x"), voltar.getDouble(k + ".y"), voltar.getDouble(k + ".z"),
                (float) voltar.getDouble(k + ".yaw"), (float) voltar.getDouble(k + ".pitch"));
    }

    private void esquecer(Player p) {
        voltar.set(p.getUniqueId().toString(), null);
        salvarVoltar();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean novato = !p.hasPlayedBefore();
        // 2 ticks depois: da tempo do AuthMe reconhecer sessao salva (quem ja esta logado nem passa pelo lobby)
        Bukkit.getScheduler().runTaskLater(this, () -> mandarParaLobby(p, novato), 2L);
        // Bedrock (celular/console) ja entrou com a conta Xbox conferida pelo Floodgate: nao precisa de senha.
        // Espera o AuthMe terminar a entrada dele (2 s) para o login valer; o lobby devolve a pessoa sozinho.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && !logado(p) && bedrockSemSenha() && bedrock(p)) entrarSemSenha(p);
        }, 40L);
    }

    private boolean bedrockSemSenha() {
        return getConfig().getBoolean("bedrock-sem-senha", true);
    }

    /** Jogador do Bedrock que entrou pelo Geyser/Floodgate (a conta Xbox ja foi conferida na entrada). */
    static boolean bedrock(Player p) {
        String teste = System.getProperty("lobby.teste.bedrock", "");
        if (!teste.isEmpty() && java.util.Arrays.asList(teste.split(",")).contains(p.getName())) return true;
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try {
            Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = c.getMethod("getInstance").invoke(null);
            return api != null && (Boolean) c.getMethod("isFloodgatePlayer", java.util.UUID.class).invoke(api, p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Faz o login pelo AuthMe sem pedir senha: quem ja tem conta so entra; quem e novo ganha uma conta com senha
     * aleatoria (ninguem precisa saber: no Bedrock a identidade e a conta Xbox). false = nao deu (segue o login normal).
     */
    private boolean entrarSemSenha(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AuthMe")) return false;
        try {
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Object api = c.getMethod("getInstance").invoke(null);
            if (api == null) return false;
            boolean tem = (Boolean) c.getMethod("isRegistered", String.class).invoke(api, p.getName());
            if (tem) {
                c.getMethod("forceLogin", Player.class).invoke(api, p);
            } else {
                byte[] b = new byte[18];
                new java.security.SecureRandom().nextBytes(b);
                String senha = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
                c.getMethod("forceRegister", Player.class, String.class, boolean.class).invoke(api, p, senha, true);
            }
            getLogger().info(p.getName() + " (Bedrock) entrou sem senha: " + (tem ? "conta existente" : "conta nova criada"));
            p.sendMessage(txt("§a§l✔ §aConta Xbox reconhecida: você entrou sem precisar de senha."));
            return true;
        } catch (Throwable t) {
            getLogger().warning("Login sem senha do Bedrock falhou para " + p.getName() + ": " + t);
            return false;
        }
    }

    private void mandarParaLobby(Player p, boolean novato) {
        if (!ativo() || !p.isOnline()) return;
        if (logado(p)) {
            // logado direto (sessao): se por acaso ficou no lobby, volta
            if (noLobby(p)) devolver(p);
            else if (!voltarOndeEstava()) irParaSpawn(p);
            return;
        }
        if (!noLobby(p)) {
            Location l = p.getLocation();
            if (novato || !voltarOndeEstava()) lembrar(p, spawnNovatos());
            else if (!mundoDeMinigame(l.getWorld())) lembrar(p, l);
            else if (lembrado(p) == null) lembrar(p, spawnNovatos());
        }
        guardarPet(p);
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.teleport(pontoDoLobby(mundo));
    }

    /** A cada meio segundo: quem ja fez login e ainda esta no lobby volta para onde estava. */
    private void verificar() {
        if (mundo == null) return;
        for (Player p : mundo.getPlayers()) {
            if (logado(p)) devolver(p);
            else if (p.getLocation().getY() < Y - 6) p.teleport(pontoDoLobby(mundo));
        }
    }

    /** false = depois do login todo mundo vai para o spawn (lobby principal), em vez de voltar para onde estava. */
    private boolean voltarOndeEstava() {
        return getConfig().getBoolean("voltar-para-onde-estava", true);
    }

    private void irParaSpawn(Player p) {
        voltando.add(p.getUniqueId());
        try {
            p.setFallDistance(0);
            p.setFireTicks(0);
            p.teleport(spawnNovatos());
        } finally {
            voltando.remove(p.getUniqueId());
        }
    }

    void devolver(Player p) {
        Location l = voltarOndeEstava() ? lembrado(p) : null;
        if (l == null) l = spawnNovatos();
        voltando.add(p.getUniqueId());
        try {
            p.setFallDistance(0);
            p.teleport(l);
        } finally {
            voltando.remove(p.getUniqueId());
        }
        esquecer(p);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && !noLobby(p)) devolverPet(p);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void trocouDeMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (mundo == null || voltando.contains(p.getUniqueId())) return;
        if (e.getFrom().equals(mundo) && !noLobby(p) && logado(p)) {
            // outro plugin (por exemplo um minigame devolvendo as coisas) ja tirou a pessoa do lobby
            esquecer(p);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && !noLobby(p)) devolverPet(p);
            }, 10L);
        } else if (!noLobby(p) && !mundoSemPet(p.getWorld()) && logado(p) && getConfig().contains("pets-guardados." + p.getUniqueId())) {
            // o pet ficou guardado aqui e a pessoa foi para um minigame: volta quando ela chega num mundo normal
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && !noLobby(p)) devolverPet(p);
            }, 10L);
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        // quem sai do lobby sem logar continua com o lugar antigo guardado em voltar.yml
        salvarVoltar();
    }

    // ================================================================== pet do BabyPets

    private Object gerentePets() {
        org.bukkit.plugin.Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
        if (bp == null || !bp.isEnabled()) return null;
        try {
            return bp.getClass().getMethod("getPetManager").invoke(bp);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Guarda o pet ativo (ou o escolhido) e tira ele do mundo. Devolve true se tinha pet para guardar. */
    private boolean guardarPet(Player p) {
        Object pm = gerentePets();
        if (pm == null) return false;
        try {
            UUID u = p.getUniqueId();
            Object pet = pm.getClass().getMethod("getActivePet", UUID.class).invoke(pm, u);
            if (pet == null) pet = pm.getClass().getMethod("getSelectedPet", UUID.class).invoke(pm, u);
            if (pet == null) return false;
            int id = (Integer) pet.getClass().getMethod("getDatabaseId").invoke(pet);
            getConfig().set("pets-guardados." + u, id);
            saveConfig();
            pm.getClass().getMethod("deselectPet", UUID.class).invoke(pm, u);
            return true;
        } catch (Throwable t) {
            getLogger().warning("Nao consegui guardar o pet de " + p.getName() + ": " + t);
            return false;
        }
    }

    /** Mundos onde o pet nunca fica: os minigames e o lobby de login (cada plugin guarda e devolve o seu). */
    static boolean mundoSemPet(org.bukkit.World w) {
        if (w == null) return false;
        String n = w.getName().toLowerCase(java.util.Locale.ROOT);
        return n.equals("vovo") || n.equals("slender") || n.equals("backrooms") || n.equals("hotel") || n.equals("defesa") || n.equals("login")
                || n.equals("naochore") || n.equals("arena") || n.equals("turno_noite") || n.equals("fabrica");
    }

    private boolean vigiaMarcada;
    private final java.util.Map<UUID, Long> vigiaEspera = new java.util.HashMap<>();

    private final java.util.Map<UUID, Long> avisoPet = new java.util.HashMap<>();

    /**
     * Ninguem fica com pet ativo neste mundo. Se a pessoa escolhe um pet no /pets aqui dentro (ou outro plugin
     * devolve o pet dela), o BabyPets tenta trazer o pet a cada 5 ticks, este mundo nao deixa ele nascer e o chat
     * enche de "apareceu ao seu lado". Roda a cada 5 ticks e logo depois de um nascimento barrado.
     */
    void vigiarPets() {
        vigiaMarcada = false;
        if (mundo == null || mundo.getPlayers().isEmpty()) return;
        Object pm = gerentePets();
        if (pm == null) return;
        java.lang.reflect.Method ativo;
        try {
            ativo = pm.getClass().getMethod("getActivePet", UUID.class);
        } catch (Throwable t) {
            return;
        }
        long agora = System.currentTimeMillis();
        for (Player p : mundo.getPlayers()) {
            UUID u = p.getUniqueId();
            try {
                if (ativo.invoke(pm, u) == null) continue;
            } catch (Throwable t) {
                continue;
            }
            Long ate = vigiaEspera.get(u);
            if (ate != null && agora < ate) continue;
            if (guardarPet(p) && avisoPet.getOrDefault(u, 0L) < agora) {
                p.sendMessage("§8[§aLobby§8] " + "§7Seu pet fica guardado aqui e volta quando você sair.");
                avisoPet.put(u, agora + 600_000L); // o aviso sai no maximo a cada 10 min (nada de chat cheio)
            }
            else vigiaEspera.put(u, agora + 10000);
        }
    }

    /** Barrou o nascimento de uma criatura de fora: confere os pets ja no proximo tick. */
    void marcarVigia() {
        if (vigiaMarcada || !isEnabled()) return;
        vigiaMarcada = true;
        Bukkit.getScheduler().runTask(this, this::vigiarPets);
    }

    /** Quem esta saindo do servidor agora (do inicio ao fim do PlayerQuitEvent). */
    private final java.util.Set<UUID> saindo = new java.util.HashSet<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void saindoInicio(PlayerQuitEvent e) {
        saindo.add(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void saindoFim(PlayerQuitEvent e) {
        saindo.remove(e.getPlayer().getUniqueId());
    }

    private void devolverPet(Player p) {
        String k = "pets-guardados." + p.getUniqueId();
        if (!getConfig().contains(k)) return;
        // Saindo do servidor ou servidor desligando: o BabyPets ja limpou (ou vai limpar) os dados da pessoa e
        // chamar o pet agora pode deixar um pet solto no mundo. Ele continua guardado e volta no proximo login.
        if (!isEnabled() || saindo.contains(p.getUniqueId()) || !p.isOnline()) return;
        // num minigame ou no lobby de login o pet nao pode ficar: continua guardado e volta quando a pessoa sair de la
        if (mundoSemPet(p.getWorld())) return;
        Object pm = gerentePets();
        if (pm == null) return;
        int id = getConfig().getInt(k);
        try {
            UUID u = p.getUniqueId();
            if (pm.getClass().getMethod("getSelectedPet", UUID.class).invoke(pm, u) != null) {
                esquecerPet(k); // a pessoa ja escolheu outro pet: fica com o que escolheu
                return;
            }
            Object lista = pm.getClass().getMethod("getPlayerPets", UUID.class).invoke(pm, u);
            if (!(lista instanceof java.util.List<?> pets)) return;
            for (Object pet : pets) {
                if ((Integer) pet.getClass().getMethod("getDatabaseId").invoke(pet) != id) continue;
                for (java.lang.reflect.Method m : pm.getClass().getMethods()) {
                    if (m.getName().equals("selectPet") && m.getParameterCount() == 2) {
                        m.invoke(pm, u, pet);
                        esquecerPet(k);
                        return;
                    }
                }
                return;
            }
            esquecerPet(k); // esse pet nao existe mais
        } catch (Throwable t) {
            getLogger().warning("Nao consegui devolver o pet de " + p.getName() + " (tento de novo no proximo login): " + t);
        }
    }

    private void esquecerPet(String k) {
        getConfig().set(k, null);
        saveConfig();
    }

    // ================================================================== nada machuca no lobby

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (noLobby(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void fogo(EntityCombustEvent e) {
        if (noLobby(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void fome(FoodLevelChangeEvent e) {
        if (noLobby(e.getEntity()) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void criatura(EntitySpawnEvent e) {
        if (noLobby(e.getEntity()) && e.getEntity() instanceof LivingEntity && !(e.getEntity() instanceof Player)) {
            e.setCancelled(true);
            marcarVigia();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void quebrar(BlockBreakEvent e) {
        if (noLobby(e.getPlayer()) && !e.getPlayer().hasPermission("loginlobby.admin")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void colocar(BlockPlaceEvent e) {
        if (noLobby(e.getPlayer()) && !e.getPlayer().hasPermission("loginlobby.admin")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void usar(PlayerInteractEvent e) {
        if (noLobby(e.getPlayer()) && !e.getPlayer().hasPermission("loginlobby.admin")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void largar(PlayerDropItemEvent e) {
        if (noLobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void caiu(PlayerMoveEvent e) {
        if (noLobby(e.getPlayer()) && e.getTo() != null && e.getTo().getY() < Y - 6) e.getPlayer().teleport(pontoDoLobby(mundo));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void renascer(PlayerRespawnEvent e) {
        if (noLobby(e.getPlayer())) e.setRespawnLocation(pontoDoLobby(mundo));
    }

    // ================================================================== comando

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase() : "status";
        switch (sub) {
            case "reconstruir" -> {
                construir();
                s.sendMessage("§aLobby reconstruido.");
            }
            case "spawn" -> {
                spawn.construir();
                spawn.iniciar();
                s.sendMessage("§aSpawn enfeitado de novo.");
            }
            case "ir" -> {
                if (s instanceof Player p) p.teleport(pontoDoLobby(mundo));
            }
            case "devolver" -> {
                Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pp ? pp : null);
                if (p != null) devolver(p);
            }
            default -> s.sendMessage("§eLobby de login: mundo=" + (mundo == null ? "-" : mundo.getName()) + " ativo=" + ativo()
                    + " no lobby=" + (mundo == null ? 0 : mundo.getPlayers().size()) + " | /loginlobby <reconstruir|ir|devolver [jogador]>");
        }
        return true;
    }
}
