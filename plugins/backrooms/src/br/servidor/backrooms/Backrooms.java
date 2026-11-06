package br.servidor.backrooms;

import java.io.File;
import java.io.IOException;
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
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Minigame de terror: caia nas Backrooms e atravesse 4 fases enormes ate achar a saida. */
public final class Backrooms extends JavaPlugin implements Listener {

    static final String PREFIXO = "§e§lBackrooms §8» §7";
    private static final Set<String> COMANDOS_LIVRES = Set.of("backrooms", "backroom", "br", "msg", "tell", "w", "r", "reply",
            "login", "l", "logout", "register", "reg");

    NamespacedKey kEntidade, kItem, kColetavel, kPlaca;
    private World mundo;
    private Mapa mapa;
    private Partida partida;
    private Guarda guarda;
    private final Set<UUID> movendo = new HashSet<>();

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ================================================================== ligar / desligar

    /**
     * Atualiza no config.yml real os valores que mudaram numa versao nova. So troca o que ainda estiver no
     * valor antigo: se alguem mudou a mao, fica como esta.
     */
    private void ajustarValores() {
        if (getConfig().getInt("ajustes-versao", 0) >= 1) return;
        int n = trocarValores(getConfig(), AJUSTES_1);
        getConfig().set("ajustes-versao", 1);
        saveConfig();
        getLogger().info("Ajuste de dificuldade aplicado no config.yml (" + n + " valor(es) trocado(s)).");
    }

    // 1: Nivel ! do dificil estava mais rapido que o jogador correndo (6,1 b/s e bote de 8,8 b/s)
    static final Object[][] AJUSTES_1 = {
                {"modos.normal.corrida-velocidade", 5.6, 5.3},
                {"modos.dificil.corrida-velocidade", 6.1, 5.3},
                {"modos.dificil.corrida-distancia", 16, 18},
                {"modos.dificil.corrida-investida-segundos", 9, 11},
                {"modos.dificil.corrida-investida-forca", 1.45, 1.35},
                {"modos.dificil.corrida-escuridao", 14, 16},
    };

    /** {caminho, valor antigo, valor novo}: troca so onde o valor gravado e igual ao antigo. */
    static int trocarValores(org.bukkit.configuration.ConfigurationSection c, Object[][] t) {
        int n = 0;
        for (Object[] x : t) {
            String k = (String) x[0];
            if (c.isSet(k) && (c.isDouble(k) || c.isInt(k))) {
                if (Math.abs(c.getDouble(k) - ((Number) x[1]).doubleValue()) < 1e-9) {
                    c.set(k, x[2]);
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // 04/11: 8 fases embaralhadas -> mais tempo de partida (so troca o valor antigo padrao)
        if (getConfig().getInt("config-versao", 1) < 2) {
            if (getConfig().getInt("tempo-limite-minutos", 60) == 60) getConfig().set("tempo-limite-minutos", 90);
            if (!getConfig().isSet("embaralhar-niveis")) getConfig().set("embaralhar-niveis", true);
            getConfig().set("config-versao", 2);
            saveConfig();
        }
        if (!getConfig().contains("modos", true)) {
            getConfig().options().copyDefaults(true);
            saveConfig();
            reloadConfig();
        }
        ajustarValores();
        kEntidade = new NamespacedKey(this, "entidade");
        kItem = new NamespacedKey(this, "item");
        kColetavel = new NamespacedKey(this, "coletavel");
        kPlaca = new NamespacedKey(this, "placa");
        guarda = new Guarda(getDataFolder());
        long t0 = System.currentTimeMillis();
        mapa = new Mapa();
        criarMundo();
        if (mundo == null) return;
        partida = new Partida(this, mundo, mapa);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(Sons.ouvinte(), this);
        Visual.iniciar(this);
        partida.limparEntidades();
        placa();
        Bukkit.getScheduler().runTaskTimer(this, partida::ciclo, 20L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, partida::sincronizar, 21L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::vigiarPets, 40L, 5L);
        for (Player p : Bukkit.getOnlinePlayers()) if (guarda.tem(p) || noMundo(p)) devolverDepoisDoLogin(p);
        StringBuilder s = new StringBuilder();
        for (Nivel n : mapa.niveis) s.append(n.nome).append(' ').append(n.largX).append('x').append(n.largZ).append(", ");
        getLogger().info("Backrooms prontas em " + (System.currentTimeMillis() - t0) + " ms: " + s);
    }

    @Override
    public void onDisable() {
        if (partida == null) return;
        for (Player p : partida.online(false)) devolver(p);
        partida.jogadores.clear();
        try {
            partida.limparNivel();
            partida.limparEntidades();
        } catch (Throwable ignored) {
        }
    }

    private static void apagar(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) apagar(x);
        f.delete();
    }

    private void criarMundo() {
        int ver = getConfig().getInt("mapa-versao", 0);
        if (ver != Mapa.VERSAO && Bukkit.getWorld(Mapa.MUNDO) == null) {
            // o desenho das fases mudou: apaga o mundo velho para gerar de novo
            List<File> pastas = new ArrayList<>();
            pastas.add(new File(Bukkit.getWorldContainer(), Mapa.MUNDO));
            if (!Bukkit.getWorlds().isEmpty()) {
                World principal = Bukkit.getWorlds().get(0);
                pastas.add(new File(Bukkit.getWorldContainer(), principal.getName() + "/dimensions/minecraft/" + Mapa.MUNDO));
                pastas.add(new File(principal.getWorldFolder(), "dimensions/minecraft/" + Mapa.MUNDO));
                String caminho = principal.getWorldFolder().getAbsolutePath();
                int i = caminho.indexOf(File.separator + "dimensions" + File.separator);
                if (i > 0) pastas.add(new File(caminho.substring(0, i), "dimensions/minecraft/" + Mapa.MUNDO));
            }
            for (File f : pastas)
                if (f.exists()) {
                    getLogger().info("Apagando o mundo antigo das Backrooms: " + f);
                    apagar(f);
                }
        }
        mundo = new WorldCreator(Mapa.MUNDO).generator(new Gerador(mapa)).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo das Backrooms!");
            return;
        }
        if (ver != Mapa.VERSAO) {
            getConfig().set("mapa-versao", Mapa.VERSAO);
            saveConfig();
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
        mundo.setSpawnLocation(Espera.spawn(mundo));
        // a borda tem que cobrir TODAS as fases (antes os Niveis 3, Fun e 37 ficavam fora dela e as criaturas
        // nao conseguiam andar: o buscador de caminho do Minecraft nao passa da borda)
        double minX = Espera.X1, maxX = Espera.X2, minZ = Espera.Z1, maxZ = Espera.Z2;
        for (Nivel n : mapa.niveis) {
            minX = Math.min(minX, n.x0);
            maxX = Math.max(maxX, n.x0 + n.largX);
            minZ = Math.min(minZ, n.z0);
            maxZ = Math.max(maxZ, n.z0 + n.largZ);
        }
        mundo.getWorldBorder().setCenter((minX + maxX) / 2, (minZ + maxZ) / 2);
        mundo.getWorldBorder().setSize(Math.max(maxX - minX, maxZ - minZ) + 128);
        try {
            mundo.setViewDistance(6);
            mundo.setSimulationDistance(6);
        } catch (Throwable t) {
            getLogger().info("Distancia de visao por mundo nao suportada: " + t.getMessage());
        }
    }

    private void placa() {
        Location l = Espera.placa(mundo);
        mundo.getChunkAt(l).load();
        for (Entity e : mundo.getEntities())
            if (e.getPersistentDataContainer().has(kPlaca, PersistentDataType.BYTE)) e.remove();
        mundo.spawn(l, TextDisplay.class, t -> {
            t.text(txt("§e§lBACKROOMS\n§fVocê saiu da realidade.\n§7Atravesse §f4 fases enormes§7 e ache a saída."
                    + "\n§aFácil §7· §eNormal §7· §cDifícil §8(/backrooms)\n§8/backrooms sair para desistir"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(true);
            t.setShadowed(true);
            t.setBrightness(new Display.Brightness(15, 15));
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
        return p.hasPermission("backrooms.admin") && p.getGameMode() == GameMode.CREATIVE && !partida.participa(p);
    }

    boolean ehNossa(Entity e) {
        return e != null && e.getPersistentDataContainer().has(kEntidade, PersistentDataType.BYTE);
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
        p.setCompassTarget(Bukkit.getWorlds().get(0).getSpawnLocation());
        // desligando (onDisable) o Bukkit nao aceita tarefa nova: o pet continua guardado e volta no proximo login
        if (isEnabled()) Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && !noMundo(p)) devolverPet(p);
        }, 10L);
    }

    // ================================================================== pet do BabyPets

    /** O pet nao entra nas Backrooms: fica guardado e volta quando o jogador sai. */
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

    private void devolverPet(Player p) {
        String k = "pets-guardados." + p.getUniqueId();
        if (!getConfig().contains(k)) return;
        // servidor desligando ou pessoa fora: o pet continua guardado e volta no proximo login
        if (!isEnabled() || !p.isOnline()) return;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void trocouDeMundo(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        if (mundo == null) return;
        Player p = e.getPlayer();
        if (p.getWorld().equals(mundo)) guardarPet(p);
        else if (!mundoSemPet(p.getWorld()) && !guarda.tem(p) && getConfig().contains("pets-guardados." + p.getUniqueId()))
            Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && !noMundo(p)) devolverPet(p);
        }, 10L);
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
                p.sendMessage(PREFIXO + "§7Seus itens da última partida nas Backrooms foram devolvidos.");
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

    void registrarVitoria(List<String> nomes, long segundos, String modo, List<Player> jogaram) {
        String tempo = String.format("%d:%02d", segundos / 60, segundos % 60);
        String base = "recordes." + modo + ".";
        int rec = getConfig().getInt(base + "segundos", 0);
        boolean novo = !nomes.isEmpty() && (rec <= 0 || segundos < rec);
        if (novo) {
            getConfig().set(base + "segundos", segundos);
            getConfig().set(base + "jogadores", String.join(", ", nomes));
        }
        for (Player p : jogaram) {
            if (!nomes.contains(p.getName())) continue;
            String k = "vitorias." + modo + "." + p.getUniqueId();
            getConfig().set(k, getConfig().getInt(k, 0) + 1);
            for (String cmd : getConfig().getStringList("recompensa-comandos"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%jogador%", p.getName()).replace("%modo%", modo));
        }
        saveConfig();
        if (!nomes.isEmpty())
            Bukkit.broadcast(txt(PREFIXO + "§f" + String.join(", ", nomes) + " §7" + (nomes.size() > 1 ? "escaparam" : "escapou") + " das Backrooms no modo "
                    + Partida.nomeModo(modo) + " §7em §f" + tempo + "§7!" + (novo ? " §6§lNOVO RECORDE!" : "")));
    }

    private int vitorias(Player p) {
        int n = 0;
        for (String m : List.of("facil", "normal", "dificil")) n += getConfig().getInt("vitorias." + m + "." + p.getUniqueId(), 0);
        return n;
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: /backrooms admin <iniciar|parar|status|nivel <0-4>|pular|abrir|modo <m>>");
            return true;
        }
        switch (sub) {
            case "", "menu" -> menu(p);
            case "entrar", "jogar" -> entrar(p);
            case "sair" -> {
                if (!partida.remover(p)) p.sendMessage(PREFIXO + "Você não está nas Backrooms.");
                else p.sendMessage(PREFIXO + "Você saiu das Backrooms.");
            }
            case "modo", "dificuldade" -> {
                if (a.length < 2) {
                    p.sendMessage(PREFIXO + "§fDificuldade: " + partida.nomeModo() + " §8| §f/backrooms modo <facil|normal|dificil>");
                    break;
                }
                String erro = partida.trocarModo(p, a[1]);
                p.sendMessage(PREFIXO + (erro != null ? erro : "§fDificuldade: " + partida.nomeModo()));
            }
            case "status", "info" -> info(p);
            case "reviver", "moeda" -> {
                if (!Reviver.usarPendente(p)) p.sendMessage(PREFIXO + "§7Nada para reviver agora. §8(Moedas de Reviver: §e" + Reviver.moedas(p) + "§8)");
            }
            default -> p.sendMessage(PREFIXO + "§f/backrooms §7(menu), §f/backrooms entrar§7, §f/backrooms sair§7, §f/backrooms modo <facil|normal|dificil>");
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("backrooms.admin")) {
            s.sendMessage(PREFIXO + "§cSem permissão.");
            return true;
        }
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        Player p = s instanceof Player pp ? pp : (a.length > 2 ? Bukkit.getPlayerExact(a[a.length - 1]) : null);
        switch (op) {
            case "iniciar" -> {
                partida.iniciar();
                s.sendMessage(PREFIXO + partida.status());
            }
            case "parar" -> {
                partida.parar();
                s.sendMessage(PREFIXO + "Partida encerrada.");
            }
            case "status" -> s.sendMessage(PREFIXO + partida.status());
            case "nivel" -> {
                try {
                    int n = Integer.parseInt(a[2]);
                    if (partida.estado == Partida.Estado.JOGANDO && n >= 0 && n <= Partida.CORRIDA) partida.irParaNivel(n);
                } catch (Exception ignored) {
                }
                s.sendMessage(PREFIXO + partida.status());
            }
            case "susto" -> {
                // teste: /backrooms admin susto <0-3> [jogador]  (0 na cara, 1 atras, 2 vulto, 3 olhos no escuro)
                if (p != null && partida.vivo(p)) {
                    int t = 0;
                    try {
                        t = Integer.parseInt(a[2]);
                    } catch (Exception ignored) {
                    }
                    partida.assustar(p, Math.max(0, Math.min(3, t)));
                    s.sendMessage(PREFIXO + "Susto " + t + " em " + p.getName());
                } else s.sendMessage(PREFIXO + "§cPrecisa de um jogador na partida.");
            }
            case "entrar" -> {
                // teste: /backrooms admin entrar <jogador>  (coloca o jogador na fila, como se ele usasse /backrooms entrar)
                if (p != null) {
                    entrar(p);
                    s.sendMessage(PREFIXO + p.getName() + " entrou na fila. " + partida.status());
                } else s.sendMessage(PREFIXO + "§cJogador não encontrado.");
            }
            case "pego" -> {
                // teste: /backrooms admin pego [jogador]  (o monstro da fase pega o jogador, com o susto inteiro; tira 1 vida)
                if (p != null && partida.vivo(p) && partida.alvoValido(p)) {
                    partida.pego(p, "o teste", partida.skinNivel());
                    s.sendMessage(PREFIXO + "Pego (teste) em " + p.getName());
                } else s.sendMessage(PREFIXO + "§cPrecisa de um jogador na partida (fora da proteção do começo).");
            }
            case "pular" -> {
                partida.pularNivel();
                s.sendMessage(PREFIXO + partida.status());
            }
            case "abrir" -> {
                partida.abrirTeste();
                s.sendMessage(PREFIXO + partida.status());
            }
            case "itens" -> s.sendMessage(PREFIXO + partida.itensTeste(p));
            case "item" -> {
                // teste: leva o jogador ate o item mais perto
                if (p != null) {
                    Location l = partida.itemMaisPerto(p);
                    if (l != null) mover(p, l.clone().add(0.6, -0.25, 0));
                }
                s.sendMessage(PREFIXO + partida.status());
            }
            case "saida" -> {
                if (p != null) mover(p, partida.atual().saida(mundo).add(0, 0, 1.2));
                s.sendMessage(PREFIXO + partida.status());
            }
            case "sanidade" -> {
                if (p != null && a.length > 2) partida.sanidadeTeste(p, Double.parseDouble(a[2]));
                s.sendMessage(PREFIXO + partida.status());
            }
            case "tp" -> {
                int n = 0;
                try {
                    n = Integer.parseInt(a[2]);
                } catch (Exception ignored) {
                }
                if (p != null) {
                    movendo.add(p.getUniqueId());
                    try {
                        p.teleport(mapa.niveis.get(Math.max(0, Math.min(mapa.niveis.size() - 1, n))).inicio(mundo, 0));
                    } finally {
                        movendo.remove(p.getUniqueId());
                    }
                }
            }
            case "espera" -> {
                if (p != null) mover(p, Espera.spawn(mundo));
            }
            case "modo" -> {
                String erro = a.length > 2 ? partida.trocarModo(null, a[2]) : null;
                s.sendMessage(PREFIXO + (erro != null ? erro : "Dificuldade: " + partida.nomeModo()));
            }
            case "recarregar" -> {
                reloadConfig();
                placa();
                s.sendMessage(PREFIXO + "Config recarregada.");
            }
            default -> s.sendMessage(PREFIXO + "/backrooms admin <iniciar|parar|status|nivel <0-4>|pular|abrir|itens|item|saida|sanidade <n>|tp <0-4>|espera|modo <m>|recarregar>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> op = new ArrayList<>();
        if (a.length == 1) {
            op.addAll(List.of("entrar", "sair", "modo", "status"));
            if (s.hasPermission("backrooms.admin")) op.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("modo")) op.addAll(List.of("facil", "normal", "dificil"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("backrooms.admin"))
            op.addAll(List.of("iniciar", "parar", "status", "nivel", "pular", "abrir", "itens", "item", "saida", "sanidade", "tp", "espera", "modo", "recarregar"));
        else if (a.length == 3 && a[1].equalsIgnoreCase("modo")) op.addAll(List.of("facil", "normal", "dificil"));
        String ult = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        op.removeIf(x -> !x.startsWith(ult));
        return op;
    }

    private void entrar(Player p) {
        if (partida.participa(p)) {
            p.sendMessage(PREFIXO + "Você já está nas Backrooms. §e/backrooms sair §7para sair.");
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
        mover(p, Espera.spawn(mundo));
        partida.adicionar(p);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f);
        p.sendMessage("§e§l--- BACKROOMS ---");
        p.sendMessage("§7Seus itens (e seu pet) ficaram guardados e voltam quando você sair.");
        p.sendMessage("§fSão §e4 fases§f: ache os itens de cada uma para abrir a saída. Fuja das criaturas e cuide da sua §bsanidade§f.");
        p.sendMessage("§fDificuldade: " + partida.nomeModo() + " §7(troque no menu §f/backrooms §7ou com §f/backrooms modo <facil|normal|dificil>§7)");
        p.sendMessage("§fSair: §e/backrooms sair");
        if (partida.estado == Partida.Estado.JOGANDO) partida.entrarNoMeio(p);
        else if (partida.estado == Partida.Estado.FIM) p.sendMessage(PREFIXO + "§eA partida anterior está terminando. A próxima começa já já.");
    }

    private void info(Player p) {
        p.sendMessage(PREFIXO + "§fEstado: §e" + estadoTexto() + " §8| §fmodo: " + partida.nomeModo());
        for (String m : List.of("facil", "normal", "dificil")) {
            int rec = getConfig().getInt("recordes." + m + ".segundos", 0);
            if (rec > 0) p.sendMessage(PREFIXO + "§fRecorde " + Partida.nomeModo(m) + "§f: §e" + String.format("%d:%02d", rec / 60, rec % 60) + " §7(" + getConfig().getString("recordes." + m + ".jogadores", "") + ")");
        }
        p.sendMessage(PREFIXO + "§fSuas fugas: §e" + vitorias(p));
    }

    private String estadoTexto() {
        return switch (partida.estado) {
            case ESPERANDO -> "esperando jogadores";
            case CONTAGEM -> "começando";
            case JOGANDO -> "em andamento (" + partida.atual().nome + ", " + partida.vivos().size() + " lá dentro)";
            case FIM -> "terminando";
        };
    }

    // ================================================================== menu

    private void menu(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, txt("§e§lBackrooms"));
        h.inv = inv;
        ItemStack vidro = item(Material.YELLOW_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        inv.setItem(11, item(Material.YELLOW_GLAZED_TERRACOTTA, partida.participa(p) ? "§e§lVocê já está na partida" : "§e§lEntrar nas Backrooms",
                "§7Estado: §f" + estadoTexto(), "§7Jogadores: §f" + partida.jogadores.size(), "", "§eClique para entrar"));
        inv.setItem(13, item(Material.BOOK, "§f§lComo jogar",
                "§eNível 0 §7— o saguão amarelo: ache as §echaves§7.", "§7  Fuja da §fBactéria§7 (alta e preta).",
                "§bNível 1 §7— a garagem: ache os §bfusíveis§7.", "§7  §cNão olhe§7 para os Sorridentes no escuro.",
                "§6Nível 2 §7— os canos: ache as §6válvulas§7.", "§7  O §fUivador§7 ouve tudo: ande agachado.",
                "§cNível ! §7— CORRA até o fim do corredor.", "",
                "§bSanidade§7: cai com o tempo (mais no escuro).", "§7Beba §fÁgua de Amêndoa§7 para recuperar.",
                "§7A §6bússola§7 ajuda a achar os itens e a saída."));
        int rf = getConfig().getInt("recordes.facil.segundos", 0), rn = getConfig().getInt("recordes.normal.segundos", 0), rd = getConfig().getInt("recordes.dificil.segundos", 0);
        inv.setItem(15, item(Material.CLOCK, "§6§lRecordes",
                "§aFácil: " + rec(rf, "facil"), "§eNormal: " + rec(rn, "normal"), "§cDifícil: " + rec(rd, "dificil"), "", "§7Suas fugas: §f" + vitorias(p)));
        boolean pode = partida.estado == Partida.Estado.ESPERANDO || partida.estado == Partida.Estado.CONTAGEM;
        String rodape = pode ? "§eClique para escolher" : "§8(muda só antes da partida)";
        String m = partida.modo;
        inv.setItem(19, brilho(item(Material.LIME_CONCRETE, "§a§lFácil" + (m.equals("facil") ? " §f✔" : ""),
                "§7" + getConfig().getInt("modos.facil.vidas", 4) + " vidas, poucas criaturas,", "§7bússola sempre aponta o item mais perto.", "", rodape), m.equals("facil")));
        inv.setItem(21, brilho(item(Material.YELLOW_CONCRETE, "§e§lNormal" + (m.equals("normal") ? " §f✔" : ""),
                "§7" + getConfig().getInt("modos.normal.vidas", 3) + " vidas, criaturas mais rápidas,", "§7bússola funciona só às vezes.", "", rodape), m.equals("normal")));
        inv.setItem(23, brilho(item(Material.RED_CONCRETE, "§c§lDifícil" + (m.equals("dificil") ? " §f✔" : ""),
                "§7" + getConfig().getInt("modos.dificil.vidas", 2) + " vidas, muitas criaturas rápidas,", "§7sanidade cai rápido, pouca água,", "§7bússola só se mexe de 15 em 15 s.", "", rodape), m.equals("dificil")));
        if (partida.participa(p)) inv.setItem(25, item(Material.BARRIER, "§c§lSair das Backrooms", "§7Seus itens voltam para você."));
        p.openInventory(inv);
    }

    private String rec(int s, String m) {
        return s > 0 ? "§f" + String.format("%d:%02d", s / 60, s % 60) + " §7(" + getConfig().getString("recordes." + m + ".jogadores", "") + ")" : "§7ninguém escapou ainda";
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
        int s = e.getSlot();
        if (s == 11) {
            p.closeInventory();
            entrar(p);
        } else if (s == 19 || s == 21 || s == 23) {
            String erro = partida.trocarModo(p, s == 19 ? "facil" : s == 21 ? "normal" : "dificil");
            if (erro != null) {
                p.sendMessage(PREFIXO + erro);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            } else p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1f);
            Bukkit.getScheduler().runTask(this, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) menu(p);
            });
        } else if (s == 25 && partida.participa(p)) {
            p.closeInventory();
            partida.remover(p);
            p.sendMessage(PREFIXO + "Você saiu das Backrooms.");
        }
    }

    @EventHandler
    public void arrastarMenu(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ================================================================== jogo

    @EventHandler(priority = EventPriority.HIGH)
    public void interagir(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!noMundo(p) || admin(p)) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        boolean agua = partida.ehAgua(e.getItem());
        if (!agua) e.setUseItemInHand(Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND || !partida.vivo(p)) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.IRON_DOOR && !partida.saidaAberta
                && partida.nivel < Partida.CORRIDA) {
            int falta = partida.necessarios - partida.pegos;
            p.sendActionBar(txt("§cTrancada. §7Faltam §f" + falta + " §7item(ns) para abrir."));
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 1.4f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consumir(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!noMundo(p) || !partida.ehAgua(e.getItem())) return;
        partida.bebeu(p);
        Bukkit.getScheduler().runTask(this, () -> p.getInventory().remove(Material.GLASS_BOTTLE));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidade(PlayerInteractEntityEvent e) {
        if (noMundo(e.getRightClicked()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidadeAt(PlayerInteractAtEntityEvent e) {
        if (noMundo(e.getRightClicked()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void soco(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (noMundo(e.getAttacked()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void largarItem(PlayerDropItemEvent e) {
        if (noMundo(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true);
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

    // ================================================================== protecao do mundo

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (!noMundo(e.getEntity())) return;
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            mover(p, partida.vivo(p) ? partida.atual().inicio(mundo, 0) : Espera.spawn(mundo));
            return;
        }
        // nada morre nas Backrooms (quem "pega" o jogador e o proprio jogo)
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void alvo(EntityTargetEvent e) {
        if (noMundo(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void fogo(EntityCombustEvent e) {
        if (noMundo(e.getEntity())) e.setCancelled(true);
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

    @EventHandler
    public void juntarItens(ItemMergeEvent e) {
        if (noMundo(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void sumirItem(ItemDespawnEvent e) {
        if (noMundo(e.getEntity()) && ehNossa(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void renascer(PlayerRespawnEvent e) {
        if (partida != null && partida.participa(e.getPlayer())) e.setRespawnLocation(Espera.spawn(mundo));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void comando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!partida.participa(p) && !noMundo(p)) return;
        if (p.hasPermission("backrooms.admin")) return;
        String c = e.getMessage().length() > 1 ? e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        if (c.contains(":")) c = c.substring(c.indexOf(':') + 1);
        if (COMANDOS_LIVRES.contains(c)) return;
        e.setCancelled(true);
        p.sendMessage(PREFIXO + "§cNas Backrooms só vale §f/backrooms sair§c (e mensagens).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleporte(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (movendo.contains(p.getUniqueId()) || e.getTo() == null) return;
        boolean deDentro = e.getFrom().getWorld().equals(mundo), praDentro = e.getTo().getWorld().equals(mundo);
        if (partida.participa(p) && !praDentro) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "§cPara sair use §f/backrooms sair§c.");
            return;
        }
        if (!deDentro && praDentro && !partida.participa(p) && !p.hasPermission("backrooms.admin")) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "Para jogar use §f/backrooms entrar§7.");
        }
    }

    /** Pets e criaturas de outros plugins nao entram (so as nossas). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void criaturaNasce(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (!noMundo(e.getEntity())) return;
        Entity en = e.getEntity();
        if (en instanceof org.bukkit.entity.LivingEntity && !(en instanceof Player) && !ehNossa(en)) {
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
}
