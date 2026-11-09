package br.servidor.vovo;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
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
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Minigame de terror: acorde trancado na casa da Vovo e fuja em 5 dias, sem que ela te pegue. */
public final class Vovo extends JavaPlugin implements Listener {

    static final String PREFIXO = "§5§lVovó §8» §7";
    private static final Set<String> COMANDOS_LIVRES = Set.of("vovo", "casadavovo", "avo", "msg", "tell", "w", "r", "reply",
            "login", "l", "logout", "register", "reg");

    NamespacedKey kItem, kTranca, kTexto, kVovo, kPlaca, kMissao;
    private World mundo;
    private Casa casa;
    private Partida partida;
    private Guarda guarda;
    private ResolvableProfile perfil;
    private final Set<UUID> movendo = new HashSet<>();
    private boolean pronto;
    private int progresso;
    private BukkitTask obra;

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ================================================================== ligar / desligar

    private void trocarSeAntigo(String chave, double antigo, double novo) {
        if (!getConfig().isSet(chave) || Math.abs(getConfig().getDouble(chave) - antigo) < 1e-6) getConfig().set(chave, novo);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // config antiga (de antes das dificuldades): acrescenta as chaves novas sem mexer nas que ja existem
        if (!getConfig().contains("modos", true)) {
            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
        }
        // aranha do esgoto mais ligeira (o Paulo achou muito devagar): so troca os valores que ainda sao os antigos
        if (getConfig().getInt("ajustes-aranha", 0) < 2) {
            trocarSeAntigo("modos.normal.aranha-ritmo-ronda", 0.95, 1.12);      // ~2,5 -> ~3,4 b/s
            trocarSeAntigo("modos.normal.aranha-ritmo-procura", 1.15, 1.25);
            trocarSeAntigo("modos.normal.aranha-ritmo-caca", 1.3, 1.34);        // ~4,6 -> ~4,9 b/s
            trocarSeAntigo("modos.dificil.aranha-ritmo-ronda", 1.05, 1.2);      // ~3 -> ~3,9 b/s
            getConfig().set("ajustes-aranha", 2);
            saveConfig();
        }
        // aranha mais dificil (a fuga pelo esgoto so pede 2 itens): enxerga mais para os lados, ouve quem corre e corre mais
        if (getConfig().getInt("ajustes-aranha", 0) < 3) {
            trocarSeAntigo("modos.normal.aranha-ritmo-ronda", 1.12, 1.2);       // ~3,9 b/s
            trocarSeAntigo("modos.normal.aranha-ritmo-procura", 1.25, 1.34);    // ~4,9 b/s
            trocarSeAntigo("modos.normal.aranha-ritmo-caca", 1.34, 1.44);       // ~5,6 b/s: igual a voce correndo
            trocarSeAntigo("modos.normal.aranha-visao", 22, 26);
            trocarSeAntigo("modos.normal.aranha-visao-caca", 28, 32);
            trocarSeAntigo("modos.normal.aranha-angulo", 45, 70);
            getConfig().set("modos.normal.aranha-ouve-correndo", 20);
            getConfig().set("modos.normal.aranha-ouve-andando", 7);
            trocarSeAntigo("modos.dificil.aranha-ritmo-ronda", 1.2, 1.28);      // ~4,4 b/s
            trocarSeAntigo("modos.dificil.aranha-ritmo-procura", 1.3, 1.42);    // ~5,4 b/s
            trocarSeAntigo("modos.dificil.aranha-ritmo-caca", 1.42, 1.5);       // ~6,1 b/s: mais rapida que voce correndo
            trocarSeAntigo("modos.dificil.aranha-visao", 26, 30);
            trocarSeAntigo("modos.dificil.aranha-visao-caca", 32, 36);
            trocarSeAntigo("modos.dificil.aranha-angulo", 55, 85);
            getConfig().set("modos.dificil.aranha-ouve-correndo", 26);
            getConfig().set("modos.dificil.aranha-ouve-andando", 10);
            getConfig().set("ajustes-aranha", 3);
            saveConfig();
        }
        kItem = new NamespacedKey(this, "item");
        kTranca = new NamespacedKey(this, "tranca");
        kTexto = new NamespacedKey(this, "texto");
        kVovo = new NamespacedKey(this, "vovo");
        kPlaca = new NamespacedKey(this, "placa");
        kMissao = new NamespacedKey(this, "missao");
        guarda = new Guarda(getDataFolder());
        casa = new Casa();
        criarMundo();
        if (mundo == null) return;
        perfil = montarPerfil();
        partida = new Partida(this, mundo, casa);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Pacote(), this);
        boolean precisa = !casa.construido(mundo) || getConfig().getInt("mapa-versao", 0) != Casa.VERSAO;
        if (precisa) construir(getConfig().getInt("mapa-versao", 0) != 0);
        else terminarObra();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (pronto) partida.ciclo();
        }, 20L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (pronto) partida.sincronizar();
        }, 21L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::vigiarPets, 40L, 5L);
        for (Player p : Bukkit.getOnlinePlayers()) if (guarda.tem(p) || noMundo(p)) devolverDepoisDoLogin(p);
        getLogger().info("Casa da Vovo pronta (" + casa.pontos.size() + " lugares de itens, " + casa.esconderijos.size() + " armarios).");
    }

    @Override
    public void onDisable() {
        if (partida == null) return;
        for (Player p : partida.online(false)) {
            p.setWalkSpeed(0.2f);
            devolver(p);
        }
        partida.jogadores.clear();
        try {
            partida.removerVovo();
            partida.limparEntidades();
            partida.restaurarCasa();
        } catch (Throwable ignored) {
        }
    }

    private void criarMundo() {
        mundo = new WorldCreator(Casa.MUNDO).generator(new VazioGen()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo da Vovo!");
            return;
        }
        mundo.setDifficulty(Difficulty.EASY);
        mundo.setGameRule(GameRules.SPAWN_MOBS, false);
        mundo.setGameRule(GameRules.SPAWN_MONSTERS, false);
        mundo.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        mundo.setGameRule(GameRules.SPAWN_PATROLS, false);
        mundo.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        mundo.setGameRule(GameRules.SPAWN_WARDENS, false);
        mundo.setGameRule(GameRules.ADVANCE_TIME, false);
        mundo.setGameRule(GameRules.ADVANCE_WEATHER, false);
        mundo.setGameRule(GameRules.KEEP_INVENTORY, true);
        mundo.setGameRule(GameRules.MOB_GRIEFING, false);
        mundo.setGameRule(GameRules.PVP, false);
        mundo.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        mundo.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        mundo.setGameRule(GameRules.FALL_DAMAGE, false);
        mundo.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        mundo.setGameRule(GameRules.LOCATOR_BAR, false);
        mundo.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        mundo.setGameRule(GameRules.REDUCED_DEBUG_INFO, true);
        mundo.setGameRule(GameRules.SPREAD_VINES, false);
        mundo.setTime(18000);
        mundo.setStorm(false);
        mundo.setThundering(false);
        mundo.setSpawnLocation(Casa.lobby(mundo));
        mundo.getWorldBorder().setCenter(27, 33);
        mundo.getWorldBorder().setSize(200);
        try {
            mundo.setViewDistance(5);
            mundo.setSimulationDistance(4);
        } catch (Throwable t) {
            getLogger().info("Distancia de visao por mundo nao suportada: " + t.getMessage());
        }
    }

    private ResolvableProfile montarPerfil() {
        String nome = getConfig().getString("skin.nome", "Vovo");
        UUID id;
        try {
            id = UUID.fromString(getConfig().getString("skin.uuid", ""));
        } catch (IllegalArgumentException e) {
            id = UUID.nameUUIDFromBytes("Vovo".getBytes());
        }
        ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(nome).uuid(id);
        String valor = getConfig().getString("skin.valor", "");
        if (!valor.isEmpty()) {
            String ass = getConfig().getString("skin.assinatura", "");
            b.addProperty(new ProfileProperty("textures", valor, ass.isEmpty() ? null : ass));
        }
        return b.build();
    }

    ResolvableProfile perfilVovo() {
        return perfil;
    }

    // ================================================================== construcao

    private void construir(boolean limpar) {
        pronto = false;
        if (obra != null) obra.cancel();
        List<long[]> chunks = new ArrayList<>();
        for (int cx = -2; cx <= 6; cx++)
            for (int cz = -2; cz <= 6; cz++) {
                mundo.addPluginChunkTicket(cx, cz, this);
                chunks.add(new long[] {cx, cz});
            }
        Deque<Runnable> fila = casa.etapas(mundo, limpar);
        int total = fila.size();
        progresso = 0;
        getLogger().info("Construindo a casa da Vovo (" + total + " etapas)...");
        long inicio = System.currentTimeMillis();
        obra = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long t0 = System.nanoTime();
            while (!fila.isEmpty() && System.nanoTime() - t0 < 25_000_000L) fila.poll().run();
            int p = (int) (100L * (total - fila.size()) / total);
            if (p / 20 != progresso / 20) getLogger().info("Casa da Vovo: " + p + "%");
            progresso = p;
            if (fila.isEmpty()) {
                obra.cancel();
                obra = null;
                for (long[] c : chunks) mundo.removePluginChunkTicket((int) c[0], (int) c[1], this);
                getConfig().set("mapa-versao", Casa.VERSAO);
                saveConfig();
                getLogger().info("Casa da Vovo pronta em " + (System.currentTimeMillis() - inicio) / 1000 + "s.");
                terminarObra();
            }
        }, 1L, 1L);
    }

    private void terminarObra() {
        mundo.getChunkAt(0, 0).load();
        partida.removerVovo();
        partida.limparEntidades();
        partida.restaurarCasa();
        placa();
        pronto = true;
    }

    private void placa() {
        Location l = Casa.placaLobby(mundo);
        mundo.getChunkAt(l).load();
        mundo.getChunkAt(0, 1).load();
        for (Entity e : mundo.getEntities())
            if (e.getPersistentDataContainer().has(kPlaca, PersistentDataType.BYTE)) e.remove();
        mundo.spawn(l, TextDisplay.class, t -> {
            t.text(txt("§5§lCASA DA VOVÓ\n§fVocê vai acordar trancado lá dentro.\n§7Ache chaves e ferramentas e fuja antes que ela te pegue."
                    + "\n§fModos: §aNormal §7e §cDifícil §8(/vovo)\n§7Ela ouve tudo... §8/vovo sair para desistir"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(true);
            t.setShadowed(true);
            t.getPersistentDataContainer().set(kPlaca, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ================================================================== utilidades

    static Component txt(String legado) {
        return LegacyComponentSerializer.legacySection().deserialize(legado).decoration(TextDecoration.ITALIC, false);
    }

    boolean noMundo(Entity e) {
        return mundo != null && e.getWorld().equals(mundo);
    }

    private boolean admin(Player p) {
        return p.hasPermission("vovo.admin") && p.getGameMode() == GameMode.CREATIVE && !partida.participa(p);
    }

    void mover(Player p, Location l) {
        movendo.add(p.getUniqueId());
        try {
            p.teleport(l);
        } finally {
            movendo.remove(p.getUniqueId());
        }
    }

    void devolver(Player p) {
        movendo.add(p.getUniqueId());
        try {
            if (!guarda.devolver(p) && noMundo(p)) {
                Guarda.limpar(p);
                p.setGameMode(GameMode.SURVIVAL);
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        } finally {
            movendo.remove(p.getUniqueId());
        }
        devolverPet(p);
    }

    // ================================================================== pet do BabyPets

    /** O pet nao entra na casa: fica guardado (sem isso o BabyPets fica tentando trazer o pet e mandando mensagem). */
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
        return n.equals("vovo") || n.equals("slender") || n.equals("backrooms") || n.equals("hotel") || n.equals("defesa") || n.equals("login");
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
                p.sendMessage(PREFIXO + "§7Seu pet fica guardado aqui e volta quando você sair.");
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

    /** Seguranca extra: qualquer um que cair no mundo da Vovo (admin, teleporte de outro plugin) fica sem pet la dentro. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void trocouDeMundo(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        if (mundo == null) return;
        Player p = e.getPlayer();
        if (p.getWorld().equals(mundo)) guardarPet(p);
        else if (!mundoSemPet(p.getWorld()) && !guarda.tem(p)) devolverPet(p);
    }

    private void devolverDepoisDoLogin(Player p) {
        final int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(this, t -> {
            n[0]++;
            if (!p.isOnline() || n[0] > 300) {
                t.cancel();
                return;
            }
            if (!logado(p)) return;
            t.cancel();
            if (partida.participa(p)) return;
            if (guarda.tem(p)) {
                devolver(p);
                p.sendMessage(PREFIXO + "§7Seus itens da última partida na casa da vovó foram devolvidos.");
            } else if (noMundo(p)) devolver(p);
            else devolverPet(p);
        }, 20L, 20L);
    }

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

    ItemStack cabeca() {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        if (it.getItemMeta() instanceof SkullMeta sm) {
            try {
                PlayerProfile pp = Bukkit.createProfile(perfil.uuid(), perfil.name());
                pp.setProperties(perfil.properties());
                sm.setPlayerProfile(pp);
            } catch (Throwable ignored) {
            }
            it.setItemMeta(sm);
        }
        return it;
    }

    void registrarVitoria(List<String> nomes, long segundos, String como, String quem, List<Player> jogaram, String modo) {
        String tempo = String.format("%d:%02d", segundos / 60, segundos % 60);
        String base = "recordes." + modo + ".";
        int rec = getConfig().getInt(base + "segundos", 0);
        boolean novo = !nomes.isEmpty() && (rec <= 0 || segundos < rec);
        if (novo) {
            getConfig().set(base + "segundos", segundos);
            getConfig().set(base + "jogadores", String.join(", ", nomes));
            getConfig().set(base + "como", como);
        }
        for (Player p : jogaram) {
            if (!nomes.contains(p.getName())) continue;
            String k = "vitorias." + p.getUniqueId();
            getConfig().set(k, getConfig().getInt(k, 0) + 1);
            if (modo.equals("dificil")) {
                String kd = "vitorias-dificil." + p.getUniqueId();
                getConfig().set(kd, getConfig().getInt(kd, 0) + 1);
            }
            for (String cmd : getConfig().getStringList("recompensa-comandos"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%jogador%", p.getName()));
        }
        saveConfig();
        if (!nomes.isEmpty()) {
            Bukkit.broadcast(txt("§5§lVovó §8» §f" + String.join(", ", nomes) + " §7fugi" + (nomes.size() > 1 ? "ram" : "u") + " da casa da vovó ("
                    + como.toLowerCase(Locale.ROOT) + ") no modo " + Partida.nomeModo(modo) + " §7em §f" + tempo + "§7!" + (novo ? " §6§lNOVO RECORDE!" : "")));
        }
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: /vovo admin <iniciar|parar|construir|status|itens>");
            return true;
        }
        switch (sub) {
            case "", "menu" -> menu(p);
            case "entrar", "jogar" -> entrar(p);
            case "sair" -> {
                if (!partida.remover(p)) p.sendMessage(PREFIXO + "Você não está na casa da vovó.");
                else p.sendMessage(PREFIXO + "Você saiu da casa da vovó.");
            }
            case "status", "info" -> info(p);
            case "reviver", "moeda" -> {
                if (!Reviver.usarPendente(p))
                    p.sendMessage(PREFIXO + "§7Você não tem uma volta pendente. Moedas de Reviver: §e" + Reviver.moedas(p) + "§7. Use quando seus dias acabarem.");
            }
            case "modo", "dificuldade" -> {
                if (a.length < 2) {
                    p.sendMessage(PREFIXO + "§fDificuldade: " + partida.nomeModo() + " §8| §f/vovo modo <normal|dificil>");
                    break;
                }
                String erro = partida.trocarModo(p, a[1]);
                if (erro != null) p.sendMessage(PREFIXO + erro);
                else p.sendMessage(PREFIXO + "§fDificuldade: " + partida.nomeModo());
            }
            default -> p.sendMessage(PREFIXO + "§f/vovo §7(menu), §f/vovo entrar§7, §f/vovo sair§7, §f/vovo modo <normal|dificil>");
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("vovo.admin")) {
            s.sendMessage(PREFIXO + "§cSem permissão.");
            return true;
        }
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        Player p = s instanceof Player pp ? pp : (a.length > 2 ? Bukkit.getPlayerExact(a[a.length - 1]) : null);
        switch (op) {
            case "eliminar" -> s.sendMessage(PREFIXO + (p != null && partida.eliminarTeste(p) ? "Eliminado." : "Não deu (precisa estar vivo na partida)."));
            case "iniciar" -> {
                partida.iniciar();
                s.sendMessage(PREFIXO + partida.status());
            }
            case "parar" -> {
                partida.parar();
                s.sendMessage(PREFIXO + "Partida encerrada.");
            }
            case "construir" -> {
                partida.parar();
                construir(a.length > 2 && a[2].equalsIgnoreCase("limpar"));
                s.sendMessage(PREFIXO + "Reconstruindo a casa...");
            }
            case "status" -> s.sendMessage(PREFIXO + (pronto ? partida.status() : "construindo " + progresso + "%"));
            case "itens" -> s.sendMessage(PREFIXO + partida.itensNoMapa());
            case "item" -> {
                Missao m = a.length > 2 ? Missao.porId(a[2].toLowerCase(Locale.ROOT)) : null;
                if (p == null || m == null) s.sendMessage(PREFIXO + "/vovo admin item <" + String.join("|", java.util.Arrays.stream(Missao.values()).map(Missao::id).toList()) + "> [jogador]");
                else {
                    String r = partida.darTeste(p, m);
                    s.sendMessage(PREFIXO + "Item " + m.nomeLimpo() + ": " + r);
                    getLogger().info("[teste] item " + m.id() + " para " + p.getName() + ": " + r);
                }
            }
            case "abrir" -> {
                partida.abrirTudo();
                s.sendMessage(PREFIXO + "Tudo destrancado.");
            }
            case "vovo" -> {
                if (p != null) partida.vovoAqui(p);
                s.sendMessage(PREFIXO + partida.status());
            }
            case "largar" -> {
                if (p != null) partida.largar(p);
                s.sendMessage(PREFIXO + partida.itensNoMapa());
            }
            case "passo" -> {
                // teste: anda o jogador ate x y z (dispara tabua que range / armadilha)
                if (p != null && a.length >= 5) {
                    Location l = new Location(mundo, Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]));
                    mover(p, l);
                    partida.passo(p, l);
                }
                s.sendMessage(PREFIXO + partida.status());
            }
            case "pisos" -> s.sendMessage(PREFIXO + partida.pisosTeste());
            case "aranha" -> s.sendMessage(PREFIXO + partida.aranhaTeste(p, a.length > 2 ? a[2] : "status"));
            case "cacar" -> {
                if (p != null) partida.cacarTeste(p);
                s.sendMessage(PREFIXO + partida.status());
            }
            case "dormir" -> {
                int sgs = 60;
                try {
                    if (a.length > 2) sgs = Integer.parseInt(a[2]);
                } catch (NumberFormatException ignored) {
                }
                partida.dormirTeste(sgs);
                s.sendMessage(PREFIXO + "A vovó vai dormir por " + sgs + "s.");
            }
            case "tp" -> {
                if (p != null) mover(p, Casa.inicio(mundo, 0));
            }
            case "lobby" -> {
                if (p != null) mover(p, Casa.lobby(mundo));
            }
            case "modo" -> {
                String erro = a.length > 2 ? partida.trocarModo(null, a[2]) : null;
                s.sendMessage(PREFIXO + (erro != null ? erro : "Dificuldade: " + partida.nomeModo()));
            }
            case "recarregar" -> {
                reloadConfig();
                perfil = montarPerfil();
                placa();
                s.sendMessage(PREFIXO + "Config recarregada.");
            }
            default -> s.sendMessage(PREFIXO + "/vovo admin <iniciar|parar|construir [limpar]|status|itens|item <id>|abrir|vovo|dormir [s]|tp|lobby|recarregar>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> op = new ArrayList<>();
        if (a.length == 1) {
            op.addAll(List.of("entrar", "sair", "status", "modo"));
            if (s.hasPermission("vovo.admin")) op.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("vovo.admin")) {
            op.addAll(List.of("iniciar", "parar", "construir", "status", "itens", "item", "abrir", "vovo", "dormir", "tp", "lobby", "recarregar", "modo", "aranha"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("modo")) {
            op.addAll(List.of("normal", "dificil"));
        } else if (a.length == 3 && a[1].equalsIgnoreCase("modo") && s.hasPermission("vovo.admin")) {
            op.addAll(List.of("normal", "dificil"));
        } else if (a.length == 3 && a[1].equalsIgnoreCase("item") && s.hasPermission("vovo.admin")) {
            for (Missao m : Missao.values()) op.add(m.id());
        }
        String ult = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        op.removeIf(x -> !x.startsWith(ult));
        return op;
    }

    private void entrar(Player p) {
        if (!pronto) {
            p.sendMessage(PREFIXO + "§eA casa ainda está sendo construída (" + progresso + "%). Tente daqui a pouco.");
            return;
        }
        if (partida.participa(p)) {
            p.sendMessage(PREFIXO + "Você já está na casa da vovó. §e/vovo sair §7para sair.");
            return;
        }
        if (p.isInsideVehicle()) p.leaveVehicle();
        try {
            guarda.guardar(p);
        } catch (IOException e) {
            p.sendMessage(PREFIXO + "§cNão consegui guardar seus itens, então não vou te levar. Avise um admin.");
            getLogger().warning("Erro ao guardar " + p.getName() + ": " + e);
            return;
        }
        guardarPet(p);
        Guarda.limpar(p);
        p.setGameMode(GameMode.ADVENTURE);
        mover(p, Casa.lobby(mundo));
        partida.adicionar(p);
        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.7f);
        p.sendMessage("§5§l--- CASA DA VOVÓ ---");
        p.sendMessage("§7Seus itens ficaram guardados e voltam quando você sair.");
        p.sendMessage("§fVocê vai acordar trancado na casa. Ache as chaves e ferramentas e fuja pela §eporta da frente§f, de §ecarro §fou pelo §eesgoto§f.");
        p.sendMessage("§fDificuldade: " + partida.nomeModo() + " §7(troque no menu §f/vovo §7ou com §f/vovo modo <normal|dificil>§7)");
        p.sendMessage("§fVocê tem §e" + partida.diasTotal() + " dias§f: cada vez que ela te pega, passa um dia.");
        p.sendMessage("§fSair: §e/vovo sair");
        if (partida.estado == Partida.Estado.JOGANDO) partida.entrarNoMeio(p);
        else if (partida.estado == Partida.Estado.FIM) p.sendMessage(PREFIXO + "§eA partida anterior está terminando. A próxima começa já já.");
    }

    private void info(Player p) {
        p.sendMessage(PREFIXO + "§fEstado: §e" + estadoTexto() + " §8| §fna partida: §e" + partida.jogadores.size() + " §8| §fmodo: " + partida.nomeModo());
        for (String m : List.of("normal", "dificil")) {
            int rec = getConfig().getInt("recordes." + m + ".segundos", 0);
            if (rec > 0) p.sendMessage(PREFIXO + "§fRecorde " + Partida.nomeModo(m) + "§f: §e" + String.format("%d:%02d", rec / 60, rec % 60) + " §7(" + getConfig().getString("recordes." + m + ".jogadores", "") + ")");
        }
        p.sendMessage(PREFIXO + "§fSuas fugas: §e" + getConfig().getInt("vitorias." + p.getUniqueId(), 0) + " §7(no difícil: §e" + getConfig().getInt("vitorias-dificil." + p.getUniqueId(), 0) + "§7)");
    }

    private String estadoTexto() {
        return switch (partida.estado) {
            case ESPERANDO -> "esperando jogadores";
            case CONTAGEM -> "começando";
            case JOGANDO -> "em andamento (porta " + partida.trancasAbertas() + "/4, carro " + partida.pecasCarro() + "/3, esgoto " + partida.etapasEsgoto() + "/2, " + partida.vivos().size() + " na casa)";
            case FIM -> "terminando";
        };
    }

    // ================================================================== menu

    private void menu(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, txt("§5§lCasa da Vovó"));
        h.inv = inv;
        ItemStack vidro = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        ItemStack entrar = cabeca();
        ItemMeta m = entrar.getItemMeta();
        m.displayName(txt(partida.participa(p) ? "§d§lVocê já está na partida" : "§d§lEntrar na casa"));
        m.lore(List.of(txt("§7Estado: §f" + estadoTexto()), txt("§7Jogadores: §f" + partida.jogadores.size()), txt(""), txt("§eClique para entrar")));
        entrar.setItemMeta(m);
        inv.setItem(11, entrar);
        inv.setItem(13, item(Material.IRON_DOOR, "§e§lComo jogar",
                "§7Você acorda trancado no quarto.", "§7Ache §fchaves e ferramentas§7 pela casa",
                "§7(clique para pegar, uma por vez).", "", "§fPorta da frente: §7cadeado, corrente,", "§7tábuas e fechadura elétrica.",
                "§fCarro: §7bateria, gasolina e chave.", "§fEsgoto: §7chave inglesa na tampa do", "§7bueiro (porão) e serra na grade.", "", "§7Ela §fouve tudo§7: tábuas soltas, correr,", "§7largar coisas. §fAgache§7 para andar quieto.",
                "§7Esconda-se nos §farmários§7.", "§7Pego = acorda no dia seguinte (§f" + partida.diasTotal() + " dias§7)."));
        int rn = getConfig().getInt("recordes.normal.segundos", 0), rd = getConfig().getInt("recordes.dificil.segundos", 0);
        inv.setItem(15, item(Material.CLOCK, "§6§lRecordes",
                "§aNormal: " + (rn > 0 ? "§f" + String.format("%d:%02d", rn / 60, rn % 60) + " §7(" + getConfig().getString("recordes.normal.jogadores", "") + ")" : "§7ninguém fugiu ainda"),
                "§cDifícil: " + (rd > 0 ? "§f" + String.format("%d:%02d", rd / 60, rd % 60) + " §7(" + getConfig().getString("recordes.dificil.jogadores", "") + ")" : "§7ninguém fugiu ainda"),
                "",
                "§7Suas fugas: §f" + getConfig().getInt("vitorias." + p.getUniqueId(), 0) + " §7(no difícil: §f" + getConfig().getInt("vitorias-dificil." + p.getUniqueId(), 0) + "§7)"));
        boolean dif = partida.modo.equals("dificil");
        boolean podeTrocar = partida.estado == Partida.Estado.ESPERANDO || partida.estado == Partida.Estado.CONTAGEM;
        String rodape = podeTrocar ? "§eClique para escolher" : "§8(muda só antes da partida)";
        inv.setItem(20, brilho(item(dif ? Material.LIME_DYE : Material.LIME_CONCRETE, "§a§lModo Normal" + (dif ? "" : " §f✔"),
                "§7Ela anda mais rápido que antes,", "§7ouve mais longe e às vezes", "§7abre os armários.", "", "§f" + getConfig().getInt("modos.normal.dias", 5) + " dias", "", rodape), !dif));
        inv.setItem(24, brilho(item(dif ? Material.RED_CONCRETE : Material.RED_DYE, "§c§lModo Difícil" + (dif ? " §f✔" : ""),
                "§7Quase tão rápida quanto você andando,", "§7ouve e enxerga longe, abre os armários,", "§7muitas tábuas que rangem e armadilhas.",
                "§7A aranha do esgoto também é mais rápida.", "", "§f" + getConfig().getInt("modos.dificil.dias", 3) + " dias §7| dardo dura pouco", "", rodape), dif));
        if (partida.participa(p)) inv.setItem(22, item(Material.BARRIER, "§c§lSair da casa", "§7Seus itens voltam para você."));
        p.openInventory(inv);
    }

    private static ItemStack brilho(ItemStack it, boolean sim) {
        if (!sim) return it;
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack item(Material mat, String nome, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(txt(nome));
        List<Component> l = new ArrayList<>();
        for (String s : lore) if (!s.isEmpty() || !l.isEmpty()) l.add(txt(s));
        if (!l.isEmpty()) m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void cliqueMenu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;
        if (e.getSlot() == 11) {
            p.closeInventory();
            entrar(p);
        } else if (e.getSlot() == 20 || e.getSlot() == 24) {
            String erro = partida.trocarModo(p, e.getSlot() == 20 ? "normal" : "dificil");
            if (erro != null) {
                p.sendMessage(PREFIXO + erro);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            } else p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1f);
            Bukkit.getScheduler().runTask(this, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) menu(p);
            });
        } else if (e.getSlot() == 22 && partida.participa(p)) {
            p.closeInventory();
            partida.remover(p);
            p.sendMessage(PREFIXO + "Você saiu da casa da vovó.");
        }
    }

    @EventHandler
    public void arrastarMenu(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ================================================================== jogo: cliques, itens, passos

    @EventHandler(priority = EventPriority.HIGH)
    public void interagir(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!noMundo(p) || admin(p)) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND || !partida.vivo(p)) return;
        Action a = e.getAction();
        Block b = e.getClickedBlock();
        if ((a == Action.RIGHT_CLICK_BLOCK || a == Action.LEFT_CLICK_BLOCK) && b != null && partida.ehTranca(b)) {
            partida.usar(p, b);
            return;
        }
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && partida.carregado(p) == Missao.BESTA) partida.atirar(p);
    }

    private void cliqueQuadro(Player p, ItemFrame f) {
        if (f.getPersistentDataContainer().has(kItem, PersistentDataType.STRING)) partida.pegarItem(p, f);
        else if (f.getPersistentDataContainer().has(kTranca, PersistentDataType.STRING)) partida.portaFrente(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidade(PlayerInteractEntityEvent e) {
        if (!noMundo(e.getRightClicked())) return;
        if (e.getRightClicked() instanceof ItemFrame f && !admin(e.getPlayer())) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) cliqueQuadro(e.getPlayer(), f);
            return;
        }
        if (!admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void soco(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (!noMundo(e.getAttacked()) || admin(e.getPlayer())) return;
        e.setCancelled(true);
        if (e.getAttacked() instanceof ItemFrame f) cliqueQuadro(e.getPlayer(), f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidadeAt(PlayerInteractAtEntityEvent e) {
        if (noMundo(e.getRightClicked()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void largarItem(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!noMundo(p) || admin(p)) return;
        e.setCancelled(true);
        if (Missao.de(e.getItemDrop().getItemStack(), kMissao) != null && partida.vivo(p)) {
            Bukkit.getScheduler().runTask(this, () -> partida.largar(p));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void inventario(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !noMundo(p) || admin(p)) return;
        if (e.getInventory().getHolder() instanceof Menu) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void trocarMao(PlayerSwapHandItemsEvent e) {
        if (noMundo(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        Player p = e.getPlayer();
        if (noMundo(p) && partida.participa(p)) partida.passo(p, e.getTo());
    }

    // ================================================================== protecao do mundo

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (!noMundo(e.getEntity())) return;
        Entity en = e.getEntity();
        if (en instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            mover(p, partida.vivo(p) ? Casa.inicio(mundo, 0) : Casa.lobby(mundo));
            return;
        }
        if (en instanceof ItemFrame && e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p && !admin(p)) {
            e.setCancelled(true);
            cliqueQuadro(p, (ItemFrame) en);
            return;
        }
        if (en instanceof Player || en instanceof ItemFrame || en instanceof Mannequin || en instanceof TextDisplay || partida.ehVovo(en)) {
            if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p && admin(p) && !partida.ehVovo(en)) return;
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alvo(EntityTargetEvent e) {
        if (partida != null && partida.ehVovo(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void fogo(EntityCombustEvent e) {
        if (partida != null && partida.ehVovo(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void quebrarQuadro(HangingBreakEvent e) {
        if (!noMundo(e.getEntity())) return;
        if (e instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent b && b.getRemover() instanceof Player p && admin(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void quebrar(BlockBreakEvent e) {
        if (noMundo(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void colocar(BlockPlaceEvent e) {
        if (noMundo(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void abrirBau(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !noMundo(p) || admin(p)) return;
        if (e.getInventory().getHolder() instanceof Menu) return;
        if (e.getInventory().getHolder() instanceof org.bukkit.block.BlockState || e.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest
                || e.getInventory().getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (noMundo(e.getEntity())) {
            e.setCancelled(true);
            e.getEntity().setFoodLevel(20);
        }
    }

    @EventHandler
    public void pegarDoChao(EntityPickupItemEvent e) {
        if (noMundo(e.getEntity()) && !(e.getEntity() instanceof Player p && admin(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void comando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!partida.participa(p) && !noMundo(p)) return;
        if (p.hasPermission("vovo.admin")) return;
        String c = e.getMessage().length() > 1 ? e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        if (c.contains(":")) c = c.substring(c.indexOf(':') + 1);
        if (COMANDOS_LIVRES.contains(c)) return;
        e.setCancelled(true);
        p.sendMessage(PREFIXO + "§cNa casa da vovó só vale §f/vovo sair§c (e mensagens).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleporte(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (movendo.contains(p.getUniqueId()) || e.getTo() == null) return;
        boolean deDentro = e.getFrom().getWorld().equals(mundo), praDentro = e.getTo().getWorld().equals(mundo);
        if (partida.participa(p) && !praDentro) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "§cPara sair da casa use §f/vovo sair§c.");
            return;
        }
        if (!deDentro && praDentro && !partida.participa(p) && !p.hasPermission("vovo.admin")) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "Para jogar use §f/vovo entrar§7.");
        }
    }

    /** Pets e criaturas de outros plugins nao entram na casa (so a vovo). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void criaturaNasce(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (!noMundo(e.getEntity())) return;
        Entity en = e.getEntity();
        if (en instanceof org.bukkit.entity.LivingEntity && !(en instanceof Player) && !partida.ehVovo(en)) {
            e.setCancelled(true);
            marcarVigia();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void criaturaTeleporta(org.bukkit.event.entity.EntityTeleportEvent e) {
        if (e.getEntity() instanceof Player || e.getTo() == null || mundo == null) return;
        if (e.getTo().getWorld().equals(mundo) && !e.getFrom().getWorld().equals(mundo)) e.setCancelled(true);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        if (partida != null && partida.participa(e.getPlayer())) partida.remover(e.getPlayer());
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (guarda.tem(p) || noMundo(p) || getConfig().contains("pets-guardados." + p.getUniqueId())) devolverDepoisDoLogin(p);
    }

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        if (partida.participa(e.getPlayer())) e.setRespawnLocation(Casa.lobby(mundo));
    }

    boolean ehZumbiDaVovo(Entity e) {
        return e instanceof Zombie && partida.ehVovo(e);
    }
}
