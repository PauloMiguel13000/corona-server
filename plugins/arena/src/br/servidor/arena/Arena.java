package br.servidor.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Arena PvP (04/11/2026): um coliseu redondo num mundo so dele. Os poderes das classes funcionam, o PvP e liberado
 * e ninguem perde itens nem XP ao morrer (renasce na hora num ponto da arena, com 3 s de protecao).
 * Os pets (BabyPets) ficam guardados enquanto a pessoa esta na arena e voltam quando ela sai.
 */
public final class Arena extends JavaPlugin implements Listener {

    static final String PREFIXO = "§8[§c§lArena§8] §7";
    static final int VERSAO = 1;
    static final int Y = 64, RAIO = 32;

    private World mundo;
    private final Random rnd = new Random();
    private File arqDados;
    private YamlConfiguration dados;
    private final Map<UUID, Integer> sequencia = new HashMap<>();
    private final Map<UUID, Long> protegidoAte = new HashMap<>();
    private final Set<UUID> movendo = new HashSet<>();

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
        Bukkit.getScheduler().runTaskTimer(this, this::ciclo, 40L, 10L);
        getLogger().info("Arena PvP pronta (mundo " + nome() + ").");
    }

    @Override
    public void onDisable() {
        desligando = true;
        for (Player p : mundo == null ? List.<Player>of() : new ArrayList<>(mundo.getPlayers())) {
            try {
                sair(p, false);
            } catch (Throwable t) {
                getLogger().warning("Nao consegui tirar " + p.getName() + " da arena ao desligar: " + t);
            }
        }
        salvar();
    }

    private boolean desligando;

    private String nome() {
        return getConfig().getString("mundo", "arena");
    }

    void salvar() {
        try {
            dados.save(arqDados);
        } catch (IOException e) {
            getLogger().warning("Nao consegui salvar dados.yml: " + e.getMessage());
        }
    }

    // ================================================================== mundo e construcao

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
            return new Location(world, 0.5, Y, 0.5);
        }
    }

    private void criarMundo() {
        mundo = new WorldCreator(nome()).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo da arena!");
            return;
        }
        mundo.setDifficulty(Difficulty.NORMAL);
        mundo.setGameRule(GameRules.KEEP_INVENTORY, true);
        mundo.setGameRule(GameRules.PVP, true);
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
        mundo.setSpawnLocation(new Location(mundo, 0.5, Y, 0.5));
        mundo.getWorldBorder().setCenter(0.5, 0.5);
        mundo.getWorldBorder().setSize(2 * (RAIO + 12));
        if (getConfig().getInt("construida", 0) != VERSAO) {
            construir();
            getConfig().set("construida", VERSAO);
            saveConfig();
        }
    }

    private void por(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    /** O coliseu: chao com desenho, muro com luzes, arquibancada, pilares para se esconder e um palco no meio. */
    void construir() {
        long t0 = System.currentTimeMillis();
        int R = RAIO;
        for (int cx = (-R - 12) >> 4; cx <= (R + 12) >> 4; cx++)
            for (int cz = (-R - 12) >> 4; cz <= (R + 12) >> 4; cz++) mundo.getChunkAt(cx, cz).load(true);
        for (int x = -R - 10; x <= R + 10; x++)
            for (int z = -R - 10; z <= R + 10; z++) {
                double d = Math.sqrt(x * x + z * z);
                for (int y = Y - 3; y <= Y + 14; y++) por(x, y, z, Material.AIR);
                if (d <= R) {
                    // chao: aneis de blackstone polida, ouro, quartzo e o simbolo no meio
                    Material m;
                    int anel = (int) d;
                    if (d < 2.5) m = Material.GOLD_BLOCK;
                    else if (d < 5) m = Material.QUARTZ_BRICKS;
                    else if (anel % 8 == 0) m = Material.CHISELED_POLISHED_BLACKSTONE;
                    else if (anel == 15 || anel == 25) m = Material.GILDED_BLACKSTONE;
                    else m = ((x + z) & 1) == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
                    if (anel % 6 == 3 && (Math.abs(x) <= 0 || Math.abs(z) <= 0)) m = Material.SEA_LANTERN;
                    por(x, Y - 1, z, m);
                    por(x, Y - 2, z, Material.BLACKSTONE);
                } else if (d <= R + 2) {
                    // muro
                    por(x, Y - 1, z, Material.POLISHED_BLACKSTONE);
                    for (int y = Y; y <= Y + 5; y++) {
                        boolean luz = y == Y + 4 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z, x))), 15) == 0;
                        por(x, y, z, luz ? Material.SHROOMLIGHT : y == Y + 5 ? Material.POLISHED_BLACKSTONE_BRICK_WALL : y == Y ? Material.GILDED_BLACKSTONE
                                : Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                } else if (d <= R + 9) {
                    // arquibancada (degraus subindo para fora)
                    int h = (int) ((d - R - 2) * 1.2);
                    for (int y = Y - 1; y <= Y + 4 + h; y++) por(x, y, z, y == Y + 4 + h ? (h % 2 == 0 ? Material.RED_TERRACOTTA : Material.SMOOTH_STONE) : Material.STONE_BRICKS);
                }
            }
        // pilares para se esconder (8, raio 14) e muretas (4, raio 23)
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(a) * 14), pz = (int) Math.round(Math.sin(a) * 14);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int y = Y; y <= Y + 4; y++)
                        por(px + dx, y, pz + dz, y == Y + 4 ? Material.CHISELED_QUARTZ_BLOCK : dx == 0 && dz == 0 ? Material.QUARTZ_PILLAR : Material.QUARTZ_BRICKS);
            por(px, Y + 5, pz, Material.LANTERN);
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i;
            double cx = Math.cos(a) * 23, cz = Math.sin(a) * 23;
            double tx = -Math.sin(a), tz = Math.cos(a);
            for (int k = -3; k <= 3; k++) {
                int x = (int) Math.round(cx + tx * k), z = (int) Math.round(cz + tz * k);
                por(x, Y, z, Material.POLISHED_BLACKSTONE_BRICKS);
                por(x, Y + 1, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
        }
        // palco no meio (2 de altura, com degraus)
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 4.4) continue;
                por(x, Y, z, d > 3.4 ? Material.QUARTZ_SLAB : Material.QUARTZ_BRICKS);
                if (d <= 3.4) por(x, Y + 1, z, d > 2.4 ? Material.QUARTZ_SLAB : Material.GOLD_BLOCK);
            }
        por(0, Y + 2, 0, Material.LIGHTNING_ROD);
        getLogger().info("Arena construida em " + (System.currentTimeMillis() - t0) + " ms.");
    }

    /** Pontos de renascer (raio 27, olhando para o meio). */
    Location ponto() {
        double a = rnd.nextDouble() * Math.PI * 2;
        Location l = new Location(mundo, Math.cos(a) * 27 + 0.5, Y, Math.sin(a) * 27 + 0.5);
        l.setDirection(new org.bukkit.util.Vector(-Math.cos(a), 0, -Math.sin(a)));
        return l;
    }

    boolean naArena(Entity e) {
        return mundo != null && e.getWorld().equals(mundo);
    }

    // ================================================================== entrar e sair

    void entrar(Player p) {
        if (mundo == null) {
            p.sendMessage(PREFIXO + "§cA arena está indisponível agora.");
            return;
        }
        if (naArena(p)) {
            p.sendMessage(PREFIXO + "Você já está na arena. Para sair: §f/arena sair");
            return;
        }
        String w = p.getWorld().getName().toLowerCase(Locale.ROOT);
        if (Set.of("vovo", "slender", "backrooms", "hotel", "defesa", "login", "naochore", "turno_noite", "fabrica").contains(w)) {
            p.sendMessage(PREFIXO + "§cSaia do minigame primeiro.");
            return;
        }
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE && !p.hasPermission("arena.admin")) {
            p.sendMessage(PREFIXO + "§cSó dá para entrar no modo sobrevivência.");
            return;
        }
        Location de = p.getLocation();
        dados.set("voltar." + p.getUniqueId(), de.getWorld().getName() + "," + de.getX() + "," + de.getY() + "," + de.getZ() + "," + de.getYaw() + "," + de.getPitch());
        salvar();
        guardarPet(p);
        mover(p, ponto());
        proteger(p);
        liberarPvpGP(p);
        sequencia.put(p.getUniqueId(), 0);
        p.showTitle(net.kyori.adventure.title.Title.title(txt("§c§lARENA PvP"), txt("§7Poderes liberados. Ninguém perde itens.")));
        p.sendMessage(PREFIXO + "§fBem-vindo à Arena! §7Os poderes das classes funcionam e §aninguém perde itens nem XP§7 ao morrer.");
        p.sendMessage(PREFIXO + "§7Sair: §f/arena sair §8| §7Ranking: §f/arena top");
        p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        for (Player o : mundo.getPlayers()) if (o != p) o.sendMessage(PREFIXO + "§f" + p.getName() + " §7entrou na arena.");
    }

    void sair(Player p, boolean avisar) {
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
        if (volta == null) volta = Bukkit.getWorlds().get(0).getSpawnLocation();
        dados.set("voltar." + p.getUniqueId(), null);
        salvar();
        sequencia.remove(p.getUniqueId());
        if (naArena(p)) mover(p, volta);
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.setGlowing(false);
        limparCombateGP(p);
        if (desligando) devolverPet(p);
        else Bukkit.getScheduler().runTaskLater(this, () -> devolverPet(p), 30L);
        if (avisar) p.sendMessage(PREFIXO + "Você saiu da arena.");
    }

    private void mover(Player p, Location l) {
        movendo.add(p.getUniqueId());
        try {
            p.teleport(l);
        } finally {
            movendo.remove(p.getUniqueId());
        }
    }

    private void proteger(Player p) {
        liberarPvpGP(p);
        long ms = getConfig().getLong("protecao-segundos", 3) * 1000L;
        protegidoAte.put(p.getUniqueId(), System.currentTimeMillis() + ms);
        p.setGlowing(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) p.setGlowing(false);
        }, ms / 50);
    }

    private boolean protegido(Player p) {
        Long t = protegidoAte.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() < t;
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String op = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "entrar";
        if (op.equals("admin")) return admin(s, a);
        if (op.equals("top") || op.equals("ranking")) {
            top(s);
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("So no jogo.");
            return true;
        }
        switch (op) {
            case "sair", "leave" -> {
                if (!naArena(p)) p.sendMessage(PREFIXO + "Você não está na arena.");
                else sair(p, true);
            }
            case "stats", "eu" -> {
                String k = "stats." + p.getUniqueId();
                p.sendMessage(PREFIXO + "§fAbates: §a" + dados.getInt(k + ".abates") + " §8| §fMortes: §c" + dados.getInt(k + ".mortes")
                        + " §8| §fMelhor sequência: §e" + dados.getInt(k + ".melhor"));
            }
            default -> entrar(p);
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("arena.admin")) {
            s.sendMessage(PREFIXO + "§cSem permissão.");
            return true;
        }
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (op) {
            case "reconstruir" -> {
                construir();
                s.sendMessage(PREFIXO + "Arena reconstruída.");
            }
            case "tp" -> {
                if (s instanceof Player p) mover(p, ponto());
            }
            default -> s.sendMessage(PREFIXO + "/arena admin <reconstruir|tp>");
        }
        return true;
    }

    private void top(CommandSender s) {
        var sec = dados.getConfigurationSection("stats");
        List<String[]> l = new ArrayList<>();
        if (sec != null) for (String k : sec.getKeys(false))
            l.add(new String[] {dados.getString("stats." + k + ".nome", "?"), String.valueOf(dados.getInt("stats." + k + ".abates")), String.valueOf(dados.getInt("stats." + k + ".mortes"))});
        l.sort((x, y) -> Integer.parseInt(y[1]) - Integer.parseInt(x[1]));
        s.sendMessage(PREFIXO + "§e§lTop abates da Arena:");
        for (int i = 0; i < Math.min(10, l.size()); i++)
            s.sendMessage("§7" + (i + 1) + ". §f" + l.get(i)[0] + " §8- §a" + l.get(i)[1] + " abates §8/ §c" + l.get(i)[2] + " mortes");
        if (l.isEmpty()) s.sendMessage("§7Ninguém lutou ainda.");
    }

    // ================================================================== morte sem perder nada

    @EventHandler(priority = EventPriority.HIGHEST)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!naArena(p)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        e.deathMessage(null);
        Player k = p.getKiller();
        String kp = "stats." + p.getUniqueId();
        dados.set(kp + ".nome", p.getName());
        dados.set(kp + ".mortes", dados.getInt(kp + ".mortes") + 1);
        int perdeu = sequencia.getOrDefault(p.getUniqueId(), 0);
        sequencia.put(p.getUniqueId(), 0);
        String msg;
        if (k != null && !k.equals(p)) {
            String kk = "stats." + k.getUniqueId();
            dados.set(kk + ".nome", k.getName());
            dados.set(kk + ".abates", dados.getInt(kk + ".abates") + 1);
            int seq = sequencia.merge(k.getUniqueId(), 1, Integer::sum);
            if (seq > dados.getInt(kk + ".melhor")) dados.set(kk + ".melhor", seq);
            msg = "§c" + k.getName() + " §7derrotou §f" + p.getName() + (seq >= 2 ? " §6(" + seq + " seguidos)" : "");
            k.playSound(k.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            k.sendActionBar(txt("§a+1 abate §8| §7sequência §e" + seq));
            if (getConfig().getIntegerList("anunciar-sequencia").contains(seq))
                Bukkit.broadcast(txt(PREFIXO + "§6§l" + k.getName() + " §eestá imparável na Arena: §f" + seq + " abates seguidos!"));
            if (perdeu >= 5) Bukkit.broadcast(txt(PREFIXO + "§f" + k.getName() + " §7acabou com a sequência de §c" + perdeu + " §7de §f" + p.getName() + "§7!"));
        } else msg = "§f" + p.getName() + " §7caiu na arena.";
        for (Player o : mundo.getPlayers()) o.sendMessage(PREFIXO + msg);
        salvar();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void renascer(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!naArena(p)) return; // ainda esta no mundo onde morreu
        e.setRespawnLocation(ponto());
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline() && naArena(p)) proteger(p);
        });
    }

    /** Quem bateu (jogador, ou o dono do projetil), ou null. */
    private static Player atacante(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent d)) return null;
        Entity atk = d.getDamager();
        if (atk instanceof Projectile pr && pr.getShooter() instanceof Entity sh) atk = sh;
        if (atk instanceof org.bukkit.entity.AreaEffectCloud n && n.getSource() instanceof Entity sh) atk = sh;
        if (atk instanceof org.bukkit.entity.TNTPrimed t && t.getSource() instanceof Entity sh) atk = sh;
        return atk instanceof Player a ? a : null;
    }

    private long ultimoAvisoCancel;

    /**
     * Roda por ultimo (HIGHEST, inclusive em eventos ja cancelados): na arena o PvP SEMPRE vale. Outros plugins cancelam o dano
     * entre jogadores por motivos que nao fazem sentido aqui (imunidade "sem itens" do GriefPrevention, cla/grupo do Clas...),
     * entao o dano e liberado de novo. Quem acabou de entrar/renascer continua protegido por alguns segundos.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !naArena(p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            mover(p, ponto());
            return;
        }
        if (protegido(p)) {
            e.setCancelled(true);
            return;
        }
        Player a = atacante(e);
        if (a == null || a.equals(p) || !naArena(a)) return;
        // quem acabou de renascer tambem nao bate (nada de abuso da protecao)
        if (protegido(a)) {
            protegidoAte.remove(a.getUniqueId());
            a.setGlowing(false);
        }
        if (e.isCancelled()) {
            e.setCancelled(false);
            long agora = System.currentTimeMillis();
            if (agora - ultimoAvisoCancel > 60_000L) {
                ultimoAvisoCancel = agora;
                getLogger().info("Outro plugin tinha cancelado o PvP de " + a.getName() + " em " + p.getName() + " na arena: liberado (plugins que escutam dano: " + quemEscutaDano() + ").");
            }
            liberarPvpGP(a);
            liberarPvpGP(p);
        }
    }

    /** Nomes dos plugins com ouvintes de dano antes do nosso (para o log). */
    private String quemEscutaDano() {
        java.util.Set<String> nomes = new java.util.LinkedHashSet<>();
        for (org.bukkit.plugin.RegisteredListener r : EntityDamageByEntityEvent.getHandlerList().getRegisteredListeners())
            if (r.getPriority().ordinal() <= EventPriority.HIGHEST.ordinal() && r.getPlugin() != this) nomes.add(r.getPlugin().getName());
        return String.join(", ", nomes);
    }

    // ================================================================== GriefPrevention (imunidade "sem itens" e castigo de logout)

    private boolean avisouGP;

    private Object dadosGP(Player p) throws Exception {
        org.bukkit.plugin.Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (gp == null || !gp.isEnabled()) return null;
        Object ds = gp.getClass().getField("dataStore").get(gp);
        return ds.getClass().getMethod("getPlayerData", UUID.class).invoke(ds, p.getUniqueId());
    }

    /** O GriefPrevention deixa "imune a PvP" quem entrou sem itens: na arena isso nao vale. */
    private void liberarPvpGP(Player p) {
        try {
            Object pd = dadosGP(p);
            if (pd == null) return;
            java.lang.reflect.Field f = pd.getClass().getField("pvpImmune");
            if (f.getBoolean(pd)) {
                f.setBoolean(pd, false);
                getLogger().info(p.getName() + " estava imune a PvP pelo GriefPrevention: liberado na arena.");
            }
        } catch (Throwable t) {
            if (!avisouGP) {
                avisouGP = true;
                getLogger().warning("Nao consegui falar com o GriefPrevention (imunidade PvP): " + t);
            }
        }
    }

    /** Saindo da arena: zera o "em combate" do GriefPrevention (senao ele mata quem sair do jogo logo depois de lutar). */
    private void limparCombateGP(Player p) {
        try {
            Object pd = dadosGP(p);
            if (pd == null) return;
            java.lang.reflect.Field f = pd.getClass().getField("lastPvpTimestamp");
            f.setLong(pd, 0L);
        } catch (Throwable ignored) {
        }
    }

    // ================================================================== protecao do mundo

    @EventHandler(ignoreCancelled = true)
    public void quebrar(BlockBreakEvent e) {
        if (naArena(e.getPlayer()) && !e.getPlayer().hasPermission("arena.admin")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        if (naArena(e.getPlayer()) && !e.getPlayer().hasPermission("arena.admin")) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void largar(PlayerDropItemEvent e) {
        if (naArena(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(txt("§7Na arena não dá para largar itens (para você não perder nada)."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void explodir(EntityExplodeEvent e) {
        if (naArena(e.getEntity())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void fogo(BlockIgniteEvent e) {
        if (e.getBlock().getWorld().equals(mundo) && e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void queimar(BlockBurnEvent e) {
        if (e.getBlock().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void nascer(CreatureSpawnEvent e) {
        if (!naArena(e.getEntity())) return;
        // so o que os poderes/jogadores colocam (torres, golens, lobos...); nada de monstro natural
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleporte(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (movendo.contains(p.getUniqueId()) || e.getTo() == null) return;
        boolean de = e.getFrom().getWorld().equals(mundo), para = e.getTo().getWorld().equals(mundo);
        // saiu da arena por outro jeito (comando, tpa, morte em outro lugar): limpa e devolve o pet
        if (de && !para) {
            dados.set("voltar." + p.getUniqueId(), null);
            salvar();
            sequencia.remove(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> devolverPet(p), 30L);
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // quem saiu do jogo dentro da arena volta para onde estava antes
        if (naArena(p) || dados.contains("voltar." + p.getUniqueId()))
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline()) sair(p, false);
            }, 40L);
        else if (dados.contains("pets." + p.getUniqueId())) Bukkit.getScheduler().runTaskLater(this, () -> devolverPet(p), 60L);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        sequencia.remove(e.getPlayer().getUniqueId());
        protegidoAte.remove(e.getPlayer().getUniqueId());
    }

    /** A cada meio segundo: quem passou da borda (voando) volta; barra com a sequencia. */
    private void ciclo() {
        if (mundo == null) return;
        for (Player p : mundo.getPlayers()) {
            Location l = p.getLocation();
            if (l.getY() < Y - 10 || Math.sqrt(l.getX() * l.getX() + l.getZ() * l.getZ()) > RAIO + 10 || l.getY() > Y + 60) {
                mover(p, ponto());
                continue;
            }
            if (Bukkit.getCurrentTick() % 40 < 10) {
                int seq = sequencia.getOrDefault(p.getUniqueId(), 0);
                p.sendActionBar(txt("§c§lARENA §8| §7jogadores: §f" + mundo.getPlayers().size() + " §8| §7sequência: §e" + seq + " §8| §f/arena sair"));
            }
        }
    }

    // ================================================================== pets do BabyPets

    private Object gerentePets() {
        org.bukkit.plugin.Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
        if (bp == null || !bp.isEnabled()) return null;
        try {
            return bp.getClass().getMethod("getPetManager").invoke(bp);
        } catch (Throwable t) {
            return null;
        }
    }

    private void guardarPet(Player p) {
        Object pm = gerentePets();
        if (pm == null) return;
        try {
            UUID u = p.getUniqueId();
            Object pet = pm.getClass().getMethod("getActivePet", UUID.class).invoke(pm, u);
            if (pet == null) pet = pm.getClass().getMethod("getSelectedPet", UUID.class).invoke(pm, u);
            if (pet == null) return;
            int id = (Integer) pet.getClass().getMethod("getDatabaseId").invoke(pet);
            dados.set("pets." + u, id);
            salvar();
            pm.getClass().getMethod("deselectPet", UUID.class).invoke(pm, u);
            p.sendMessage(PREFIXO + "§7Seu pet fica guardado e volta quando você sair da arena.");
        } catch (Throwable t) {
            getLogger().warning("Nao consegui guardar o pet de " + p.getName() + ": " + t);
        }
    }

    private void devolverPet(Player p) {
        String k = "pets." + p.getUniqueId();
        if (!dados.contains(k) || !p.isOnline() || naArena(p)) return;
        Object pm = gerentePets();
        if (pm == null) return;
        int id = dados.getInt(k);
        try {
            UUID u = p.getUniqueId();
            if (pm.getClass().getMethod("getSelectedPet", UUID.class).invoke(pm, u) != null) {
                dados.set(k, null);
                salvar();
                return;
            }
            Object lista = pm.getClass().getMethod("getPlayerPets", UUID.class).invoke(pm, u);
            if (lista instanceof List<?> pets)
                for (Object pet : pets) {
                    if ((Integer) pet.getClass().getMethod("getDatabaseId").invoke(pet) != id) continue;
                    for (java.lang.reflect.Method m : pm.getClass().getMethods())
                        if (m.getName().equals("selectPet") && m.getParameterCount() == 2) {
                            m.invoke(pm, u, pet);
                            break;
                        }
                    break;
                }
            dados.set(k, null);
            salvar();
        } catch (Throwable t) {
            getLogger().warning("Nao consegui devolver o pet de " + p.getName() + " (tento no proximo login): " + t);
        }
    }

    /** Usado pelo teste: o pet ainda esta guardado? */
    boolean petGuardado(Player p) {
        return dados.contains("pets." + p.getUniqueId());
    }
}
