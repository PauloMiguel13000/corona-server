package br.servidor.naochore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minigame "Nao Chore" (04/11/2026): sobrevivencia de terror num mundo survival pequeno (~1500 chunks), gerado de novo
 * a cada partida. 5 dias. Os monstros ficam escondidos olhando e gritam quando a pessoa chega perto, cada dia pior;
 * no ultimo dia eles cacam todo mundo, quebram portas e vidros, escalam pilares e sobem escadas.
 * As coisas de cada jogador ficam guardadas num arquivo e voltam quando ele sai (mesmo se o servidor cair).
 */
public final class NaoChore extends JavaPlugin implements Listener {

    static final String MUNDO = "naochore";
    static final String PREFIXO = "§8[§4§lNão Chore§8] §7";
    private static final Set<String> COMANDOS_LIVRES = Set.of("naochore", "dontcry", "naochora", "msg", "tell", "w", "r", "login", "l", "logout", "g", "c", "cla", "clan");

    World mundo;
    boolean mundoUsado;
    private boolean preparando;
    Partida partida;
    Guarda guarda;
    NamespacedKey kMonstro;
    private final Set<UUID> movendo = new HashSet<>();

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public void onEnable() {
        Corpo.pl = this;
        saveDefaultConfig();
        kMonstro = new NamespacedKey(this, "monstro");
        guarda = new Guarda(getDataFolder());
        partida = new Partida(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(Sons.ouvinte(), this);
        // mundo de partida antiga (servidor caiu no meio): apaga e faz outro
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = Bukkit.getWorld(MUNDO);
            if (w != null) {
                mundo = w;
                configurar(w);
            }
            if (getConfig().getBoolean("mundo-usado", false) || w == null) renovarMundo();
        }, 60L);
        Bukkit.getScheduler().runTaskTimer(this, partida::ciclo, 100L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, partida::sincronizar, 101L, 1L);
        getLogger().info("Nao Chore pronto.");
    }

    @Override
    public void onDisable() {
        if (partida != null) {
            partida.limparMonstros();
            for (Player p : partida.online(true)) devolver(p);
        }
        getConfig().set("mundo-usado", mundoUsado || getConfig().getBoolean("mundo-usado", false));
        saveConfig();
    }

    // ================================================================== mundo (um novo a cada partida)

    boolean mundoPronto() {
        return mundo != null && !mundoUsado && !preparando;
    }

    void prepararMundo() {
        if (preparando) return;
        renovarMundo();
    }

    /** Apaga o mundo usado (sem ninguem dentro) e cria outro com semente nova. */
    void renovarMundo() {
        if (preparando) return;
        if (partida.estado == Partida.Estado.JOGANDO || partida.estado == Partida.Estado.FIM) return;
        preparando = true;
        try {
            World w = Bukkit.getWorld(MUNDO);
            if (w != null) {
                World principal = Bukkit.getWorlds().get(0);
                for (Player p : new ArrayList<>(w.getPlayers())) {
                    if (!devolver(p)) mover(p, principal.getSpawnLocation());
                }
                if (!Bukkit.unloadWorld(w, false)) {
                    getLogger().warning("Nao consegui descarregar o mundo do Nao Chore; tento de novo depois.");
                    preparando = false;
                    Bukkit.getScheduler().runTaskLater(this, this::renovarMundo, 600L);
                    return;
                }
            }
            mundo = null;
            apagarPastas();
            long semente = new java.util.Random().nextLong();
            long t0 = System.currentTimeMillis();
            w = new WorldCreator(MUNDO).environment(World.Environment.NORMAL).seed(semente).generateStructures(true).createWorld();
            if (w == null) {
                getLogger().severe("Nao consegui criar o mundo do Nao Chore!");
                preparando = false;
                return;
            }
            configurar(w);
            mundo = w;
            mundoUsado = false;
            getConfig().set("mundo-usado", false);
            saveConfig();
            getLogger().info("Mundo novo do Nao Chore pronto em " + (System.currentTimeMillis() - t0) + " ms (semente " + semente + ").");
        } catch (Throwable t) {
            getLogger().warning("Erro ao renovar o mundo do Nao Chore: " + t);
        } finally {
            preparando = false;
        }
    }

    private void apagarPastas() {
        List<File> pastas = new ArrayList<>();
        pastas.add(new File(Bukkit.getWorldContainer(), MUNDO));
        if (!Bukkit.getWorlds().isEmpty()) {
            World principal = Bukkit.getWorlds().get(0);
            pastas.add(new File(Bukkit.getWorldContainer(), principal.getName() + "/dimensions/minecraft/" + MUNDO));
            pastas.add(new File(principal.getWorldFolder(), "dimensions/minecraft/" + MUNDO));
        }
        for (File f : pastas) if (f.exists()) apagar(f);
    }

    private static void apagar(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) apagar(x);
        f.delete();
    }

    /** Monstros normais bem raros (o medo vem dos vigias e dos famintos): poucos por vez e tentativa a cada 10 s. */
    void limitarMonstros(World w) {
        w.setSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER, Math.max(0, getConfig().getInt("monstros-normais-limite", 6)));
        w.setTicksPerSpawns(org.bukkit.entity.SpawnCategory.MONSTER, 200);
    }

    private void configurar(World w) {
        w.setDifficulty(Difficulty.NORMAL);
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.LOCATOR_BAR, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        limitarMonstros(w);
        w.setTime(0);
        w.setStorm(false);
        Location s = spawnSeguro(w);
        w.setSpawnLocation(s);
        w.getWorldBorder().setCenter(s.getX(), s.getZ());
        w.getWorldBorder().setSize(Math.max(200, getConfig().getInt("borda", 620)));
        w.getWorldBorder().setWarningDistance(12);
        try {
            w.setViewDistance(8);
            w.setSimulationDistance(6);
        } catch (Throwable ignored) {
        }
    }

    /** Terra firme perto do spawn que o Minecraft escolheu (no maximo ~20 colunas olhadas: nao gera o mundo inteiro). */
    private Location spawnSeguro(World w) {
        Location base = w.getSpawnLocation();
        int bx = base.getBlockX(), bz = base.getBlockZ();
        int[][] off = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {32, 32}, {-32, 32}, {32, -32}, {-32, -32}, {48, 0}, {-48, 0}, {0, 48}, {0, -48},
                {64, 64}, {-64, -64}, {64, -64}, {-64, 64}, {96, 0}, {0, 96}, {-96, 0}};
        for (int[] o : off) {
            Block top = w.getHighestBlockAt(bx + o[0], bz + o[1]);
            Material m = top.getType();
            if (top.isLiquid() || m == Material.ICE || m.name().endsWith("_LEAVES") || !m.isSolid()) continue;
            return new Location(w, top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5);
        }
        Block top = w.getHighestBlockAt(bx, bz);
        return new Location(w, bx + 0.5, top.getY() + 1, bz + 0.5);
    }

    Location spawn() {
        return mundo.getSpawnLocation();
    }

    Location lugarLonge(Player p, double min, double max) {
        return partida.lugarLonge(p, min, max);
    }

    // ================================================================== entrar e sair

    void mover(Player p, Location l) {
        movendo.add(p.getUniqueId());
        try {
            p.teleport(l);
        } finally {
            movendo.remove(p.getUniqueId());
        }
    }

    /** Devolve tudo do jogador (local, itens, vida...). */
    boolean devolver(Player p) {
        movendo.add(p.getUniqueId());
        boolean ok;
        try {
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
            ok = guarda.devolver(p);
        } finally {
            movendo.remove(p.getUniqueId());
        }
        Bukkit.getScheduler().runTaskLater(this, () -> devolverPet(p), 40L);
        return ok;
    }

    void entrar(Player p) {
        if (partida.participa(p)) {
            p.sendMessage(PREFIXO + "Você já está na fila. Sair: §f/naochore sair");
            return;
        }
        String w = p.getWorld().getName().toLowerCase(Locale.ROOT);
        if (Set.of("vovo", "slender", "backrooms", "hotel", "defesa", "login", "arena").contains(w)) {
            p.sendMessage(PREFIXO + "§cSaia do outro minigame primeiro.");
            return;
        }
        if (guarda.tem(p)) {
            devolver(p);
            p.sendMessage(PREFIXO + "§7Suas coisas da última vez foram devolvidas. Use §f/naochore entrar §7de novo.");
            return;
        }
        partida.adicionar(p);
        if (partida.estado == Partida.Estado.JOGANDO || partida.estado == Partida.Estado.FIM) {
            partida.entrarNoMeio(p);
            return;
        }
        p.sendMessage(PREFIXO + "§fVocê entrou na fila do §4Não Chore§f. Quando começar, suas coisas ficam guardadas e voltam no fim.");
    }

    void sair(Player p) {
        Partida.Jogador j = partida.jogadores.remove(p.getUniqueId());
        if (j == null) {
            if (guarda.tem(p)) devolver(p);
            else p.sendMessage(PREFIXO + "Você não está no Não Chore.");
            return;
        }
        if (j.jogando) {
            devolver(p);
            if (j.vivo) partida.avisar("§7" + j.nome + " desistiu e fugiu.");
        }
        p.sendMessage(PREFIXO + "Você saiu do Não Chore.");
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String op = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "entrar";
        if (op.equals("admin")) return admin(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("So no jogo.");
            return true;
        }
        switch (op) {
            case "sair", "desistir" -> sair(p);
            case "reviver", "moeda" -> {
                if (!Reviver.usarPendente(p))
                    p.sendMessage(PREFIXO + "§7Você não tem uma volta pendente. Moedas de Reviver: §e" + Reviver.moedas(p) + "§7. Use quando morrer.");
            }
            case "status" -> p.sendMessage(PREFIXO + "§7Partida: §f" + partida.estado + (partida.estado == Partida.Estado.JOGANDO ? " (dia " + partida.dia() + ")" : "")
                    + " §8| §7na fila: §f" + partida.naFila().size());
            default -> entrar(p);
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("naochore.admin")) {
            s.sendMessage(PREFIXO + "§cSem permissão.");
            return true;
        }
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        Player p = s instanceof Player pp ? pp : (a.length > 2 ? Bukkit.getPlayerExact(a[a.length - 1]) : null);
        switch (op) {
            case "iniciar" -> {
                if (!mundoPronto()) renovarMundo();
                partida.iniciar();
                s.sendMessage(PREFIXO + partida.status());
            }
            case "parar" -> {
                partida.parar();
                s.sendMessage(PREFIXO + "Partida encerrada.");
            }
            case "status" -> s.sendMessage(PREFIXO + partida.status() + " mundoPronto=" + mundoPronto());
            case "dia" -> {
                try {
                    partida.pularDia(Integer.parseInt(a[2]));
                } catch (Exception ignored) {
                }
                s.sendMessage(PREFIXO + partida.status());
            }
            case "susto" -> {
                int t = 0;
                try {
                    t = Integer.parseInt(a[2]);
                } catch (Exception ignored) {
                }
                if (p != null) partida.sustoTeste(p, t);
                s.sendMessage(PREFIXO + "Susto " + t + (p == null ? " (sem jogador)" : " em " + p.getName()));
            }
            case "eliminar" -> {
                if (p == null || !partida.vivo(p)) {
                    s.sendMessage(PREFIXO + "Não deu (precisa estar vivo na partida).");
                    break;
                }
                partida.eliminarTeste(p);
                s.sendMessage(PREFIXO + "Eliminado.");
            }
            case "mundo" -> {
                renovarMundo();
                s.sendMessage(PREFIXO + "Mundo renovado: " + mundoPronto());
            }
            default -> s.sendMessage(PREFIXO + "/naochore admin <iniciar|parar|status|dia <n>|susto <0 vigia|1 noite|2 cara|3 maos|4 duplo|5 chorona em casa|6 morte>|mundo>");
        }
        return true;
    }

    // ================================================================== eventos

    boolean noMundo(Entity e) {
        return mundo != null && e.getWorld().equals(mundo);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!noMundo(p) || !partida.vivo(p)) return;
        e.deathMessage(null);
        partida.pego(p);
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (!partida.vivo(p)) {
                partida.virarEspectador(p);
                partida.oferecerReviver(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void renascer(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!noMundo(p) || !partida.participa(p)) return;
        e.setRespawnLocation(spawn());
    }

    /** Bater no corpo do Faminto machuca o motor dele. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bater(EntityDamageByEntityEvent e) {
        if (!noMundo(e.getEntity())) return;
        Byte b = e.getEntity().getPersistentDataContainer().get(kMonstro, PersistentDataType.BYTE);
        if (b == null) return;
        if (b == 3 || b == 4 || b == 5) {
            // corpo de um Vigia (ou o corpo/olhos 3D): bateu no Faminto? repassa para o motor; senao nao acontece nada
            e.setCancelled(true);
            for (Monstro m : partida.monstros)
                if (m.eh(e.getEntity()) && m.motor != null && m.motor.isValid()) {
                    m.motor.damage(e.getDamage(), e.getDamager());
                    e.getEntity().getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, e.getEntity().getLocation().add(0, 1.4, 0), 3, 0.3, 0.3, 0.3, 0.1);
                }
            return;
        }
        if (b == 2) {
            e.setCancelled(true);
            for (Monstro m : partida.monstros)
                if (m.eh(e.getEntity()) && m.motor != null && m.motor.isValid()) {
                    m.motor.damage(e.getDamage(), e.getDamager());
                    e.getEntity().getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, e.getEntity().getLocation().add(0, 1.4, 0), 3, 0.3, 0.3, 0.3, 0.1);
                }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void danoMonstro(EntityDamageEvent e) {
        if (!noMundo(e.getEntity())) return;
        Byte b = e.getEntity().getPersistentDataContainer().get(kMonstro, PersistentDataType.BYTE);
        if (b == null) return;
        // o motor nao morre de queda, sufocar, afogar ou fogo
        EntityDamageEvent.DamageCause c = e.getCause();
        if (b == 1 && (c == EntityDamageEvent.DamageCause.FALL || c == EntityDamageEvent.DamageCause.SUFFOCATION || c == EntityDamageEvent.DamageCause.DROWNING
                || c == EntityDamageEvent.DamageCause.FIRE || c == EntityDamageEvent.DamageCause.FIRE_TICK || c == EntityDamageEvent.DamageCause.LAVA
                || c == EntityDamageEvent.DamageCause.CRAMMING)) e.setCancelled(true);
        if (b >= 2 && !(e instanceof EntityDamageByEntityEvent)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alvo(EntityTargetEvent e) {
        // golens, lobos etc. nao brigam com os monstros (e o zumbi-motor nao escolhe alvo sozinho)
        if (noMundo(e.getEntity()) && (e.getEntity().getPersistentDataContainer().has(kMonstro, PersistentDataType.BYTE)
                || e.getTarget() != null && e.getTarget().getPersistentDataContainer().has(kMonstro, PersistentDataType.BYTE))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fogo(EntityCombustEvent e) {
        if (noMundo(e.getEntity()) && e.getEntity().getPersistentDataContainer().has(kMonstro, PersistentDataType.BYTE)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void cama(PlayerBedEnterEvent e) {
        if (!noMundo(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(txt("§8Você não consegue dormir... §7tem alguém olhando."));
        e.getPlayer().playSound(e.getPlayer().getLocation(), org.bukkit.Sound.ENTITY_GHAST_AMBIENT, 0.5f, 1.4f);
    }

    /** Ender chest levaria itens para fora da partida. */
    @EventHandler(ignoreCancelled = true)
    public void bau(InventoryOpenEvent e) {
        if (e.getInventory().getType() == InventoryType.ENDER_CHEST && noMundo(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(PREFIXO + "§7O baú do fim não funciona aqui.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void portal(PlayerPortalEvent e) {
        if (noMundo(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(txt("§8Não tem para onde fugir."));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void comando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!noMundo(p) || p.hasPermission("naochore.admin")) return;
        String c = e.getMessage().length() > 1 ? e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        if (c.contains(":")) c = c.substring(c.indexOf(':') + 1);
        if (COMANDOS_LIVRES.contains(c)) return;
        e.setCancelled(true);
        p.sendMessage(PREFIXO + "§cAqui só vale §f/naochore sair§c (e mensagens).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleporte(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (movendo.contains(p.getUniqueId()) || e.getTo() == null || mundo == null) return;
        boolean de = e.getFrom().getWorld().equals(mundo), para = e.getTo().getWorld().equals(mundo);
        if (de && !para && partida.participa(p) && !p.hasPermission("naochore.admin")) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "§cPara sair use §f/naochore sair§c.");
        } else if (!de && para && !partida.participa(p) && !p.hasPermission("naochore.admin")) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "Para jogar use §f/naochore entrar§7.");
        }
    }

    /**
     * Saiu do mundo do Nao Chore por qualquer outro caminho (admin com /spawn, /tpa, menu...): os itens de la ficam la
     * e as coisas de antes voltam na hora, sem levar a pessoa de volta.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void trocouMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!e.getFrom().getName().equals(MUNDO) || movendo.contains(p.getUniqueId()) || !guarda.tem(p)) return;
        Partida.Jogador j = partida.jogadores.remove(p.getUniqueId());
        if (j != null && j.jogando && j.vivo) partida.avisar("§7" + j.nome + " saiu da partida.");
        movendo.add(p.getUniqueId());
        try {
            p.setGameMode(org.bukkit.GameMode.SURVIVAL);
            guarda.devolver(p, false);
        } finally {
            movendo.remove(p.getUniqueId());
        }
        Bukkit.getScheduler().runTaskLater(this, () -> devolverPet(p), 40L);
        p.sendMessage(PREFIXO + "§7Você saiu do Não Chore: o que era de lá ficou lá e suas coisas voltaram.");
        getLogger().info("Nao Chore: " + p.getName() + " saiu do mundo por fora; itens de antes devolvidos.");
    }

    /** Corta a maioria dos monstros normais que nascem no mundo do Nao Chore (os do minigame nascem como CUSTOM e passam). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void monstroNormal(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (!e.getLocation().getWorld().getName().equals(MUNDO) || !(e.getEntity() instanceof org.bukkit.entity.Enemy)) return;
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM || r == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND
                || e.getEntity().getPersistentDataContainer().has(kMonstro, PersistentDataType.BYTE)) return;
        int jogadores = Math.max(1, e.getLocation().getWorld().getPlayers().size());
        long normais = e.getLocation().getWorld().getEntitiesByClass(org.bukkit.entity.Monster.class).stream()
                .filter(m -> !m.getPersistentDataContainer().has(kMonstro, PersistentDataType.BYTE)).count();
        if (normais >= 2L + jogadores || Math.random() < getConfig().getDouble("monstros-normais-cortar", 0.7)) e.setCancelled(true);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Partida.Jogador j = partida.jogadores.remove(p.getUniqueId());
        if (j != null && j.jogando && j.vivo) partida.avisar("§7" + j.nome + " sumiu...");
        // as coisas continuam no arquivo: voltam quando ele entrar de novo
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (guarda.tem(p) || noMundo(p) || getConfig().contains("pets-guardados." + p.getUniqueId()))
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline()) return;
                if (guarda.tem(p)) {
                    devolver(p);
                    p.sendMessage(PREFIXO + "§7Você saiu no meio do Não Chore: suas coisas foram devolvidas.");
                } else if (noMundo(p)) mover(p, Bukkit.getWorlds().get(0).getSpawnLocation());
                else devolverPet(p);
            }, 60L);
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

    void guardarPet(Player p) {
        Object pm = gerentePets();
        if (pm == null) return;
        try {
            UUID u = p.getUniqueId();
            Object pet = pm.getClass().getMethod("getActivePet", UUID.class).invoke(pm, u);
            if (pet == null) pet = pm.getClass().getMethod("getSelectedPet", UUID.class).invoke(pm, u);
            if (pet == null) return;
            int id = (Integer) pet.getClass().getMethod("getDatabaseId").invoke(pet);
            getConfig().set("pets-guardados." + u, id);
            saveConfig();
            pm.getClass().getMethod("deselectPet", UUID.class).invoke(pm, u);
        } catch (Throwable t) {
            getLogger().warning("Nao consegui guardar o pet de " + p.getName() + ": " + t);
        }
    }

    void devolverPet(Player p) {
        String k = "pets-guardados." + p.getUniqueId();
        if (!getConfig().contains(k) || !p.isOnline() || noMundo(p)) return;
        Object pm = gerentePets();
        if (pm == null) return;
        int id = getConfig().getInt(k);
        try {
            UUID u = p.getUniqueId();
            if (pm.getClass().getMethod("getSelectedPet", UUID.class).invoke(pm, u) == null) {
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
            }
            getConfig().set(k, null);
            saveConfig();
        } catch (Throwable t) {
            getLogger().warning("Nao consegui devolver o pet de " + p.getName() + ": " + t);
        }
    }
}
