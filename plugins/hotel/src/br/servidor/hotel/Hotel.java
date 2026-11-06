package br.servidor.hotel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minigame "O Hotel": 50 portas num hotel que se monta enquanto voces andam. Cada porta aberta tem um numero; atras
 * delas vivem coisas. O Estrondo passa varrendo os corredores (esconda-se num armario), o Retorno volta varias vezes,
 * os Olhos machucam quem olha para eles, o Sussurro ataca no escuro quem nao olhar para ele, o Perseguidor corre atras
 * de voces num corredor sem fim e, na porta 50, a Figura cega vigia a biblioteca. Ache 5 livros e saia do hotel.
 */
public final class Hotel extends JavaPlugin implements Listener {

    static final String PREFIXO = "§4§lHotel §8» §7";
    private static final Set<String> COMANDOS_LIVRES = Set.of("hotel", "portas", "doors", "msg", "tell", "w", "r", "reply",
            "login", "l", "logout", "register", "reg");

    NamespacedKey kHotel, kItem, kPlaca;
    World mundo;
    Partida partida;
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

    /** O mundo do hotel e montado do zero a cada vez que o servidor liga (cada partida usa uma faixa nova). */
    @Override
    public void onLoad() {
        Path p = new File(Bukkit.getWorldContainer(), Planta.MUNDO).toPath();
        if (!Files.isDirectory(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                }
            });
            getLogger().info("Mundo antigo do hotel apagado (sera montado de novo).");
        } catch (IOException e) {
            getLogger().warning("Nao consegui apagar o mundo antigo do hotel: " + e);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // 04/11: 100 portas, biblioteca maior (8 livros) e a Campainha. Muda so os valores antigos padrao,
        // sem mexer no resto do config (recordes, pets guardados...).
        if (getConfig().getInt("config-versao", 1) < 2) {
            if (getConfig().getInt("portas", 50) == 50) getConfig().set("portas", 100);
            if (getConfig().getInt("livros", 5) == 5) getConfig().set("livros", 8);
            if (!getConfig().isSet("chance-campainha")) getConfig().set("chance-campainha", 0.07);
            if (!getConfig().isSet("campainha-dano")) getConfig().set("campainha-dano", 6.0);
            getConfig().set("config-versao", 2);
            saveConfig();
        }
        kHotel = new NamespacedKey(this, "hotel");
        kItem = new NamespacedKey(this, "item");
        kPlaca = new NamespacedKey(this, "placa");
        guarda = new Guarda(getDataFolder());
        criarMundo();
        if (mundo == null) return;
        Recepcao.construir(mundo);
        placa();
        partida = new Partida(this, mundo);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(Sons.ouvinte(), this);
        Visual.iniciar(this);
        Bukkit.getScheduler().runTaskTimer(this, partida::ciclo, 20L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, partida::rapido, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::vigiarPets, 40L, 5L);
        for (Player p : Bukkit.getOnlinePlayers()) if (guarda.tem(p)) devolverDepoisDoLogin(p);
        getLogger().info("O Hotel esta pronto (" + getConfig().getInt("portas", 50) + " portas).");
    }

    @Override
    public void onDisable() {
        if (partida == null) return;
        for (Player p : partida.online(false)) {
            p.setWalkSpeed(0.2f);
            devolver(p);
        }
        partida.jogadores.clear();
        partida.limparEntidades();
    }

    private void criarMundo() {
        mundo = new WorldCreator(Planta.MUNDO).generator(new VazioGen()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo do hotel!");
            return;
        }
        // NAO pode ser Pacifico: no Pacifico o jogo apaga o zumbi invisivel que faz a Figura andar (ela ficava parada)
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
        mundo.setSpawnLocation(Recepcao.spawn(mundo));
        try {
            mundo.setViewDistance(Math.max(3, getConfig().getInt("distancia-visao", 5)));
            mundo.setSimulationDistance(4);
        } catch (Throwable t) {
            getLogger().info("Distancia de visao por mundo nao suportada: " + t.getMessage());
        }
    }

    private void placa() {
        Location l = Recepcao.placa(mundo);
        mundo.getChunkAt(l).load();
        for (Entity e : mundo.getNearbyEntities(l, 6, 6, 6))
            if (e.getPersistentDataContainer().has(kPlaca, PersistentDataType.BYTE)) e.remove();
        mundo.spawn(l, TextDisplay.class, t -> {
            t.text(txt("§4§lO HOTEL\n§fAbra §e" + getConfig().getInt("portas", 50) + " portas§f e saia vivo.\n"
                    + "§7Luzes piscando? §fESCONDA-SE NUM ARMÁRIO.\n§7No escuro, se ouvir \"psst\", §folhe para ele§7.\n"
                    + "§7Os olhos roxos: §fNÃO olhe§7.\n§7Quadros com rosto: §fnão encare§7.\n§7A campainha tocou? §fNÃO SE MEXA§7.\n§8/hotel sair para desistir"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBrightness(new Display.Brightness(15, 15));
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
        return p.hasPermission("hotel.admin") && p.getGameMode() == GameMode.CREATIVE && !partida.participa(p);
    }

    /** Teleporte feito pelo proprio plugin (os bloqueios de teleporte deixam passar). */
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

    /** Item especial do hotel (chave, vitamina...) com o tipo guardado no item. */
    ItemStack item(Material m, String tipo, String nome, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(txt(nome));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(txt(s));
        if (!l.isEmpty()) meta.lore(l);
        meta.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, tipo);
        it.setItemMeta(meta);
        return it;
    }

    String tipoItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING);
    }

    // ================================================================== pet do BabyPets (o pet nao entra no hotel)

    private Object gerentePets() {
        org.bukkit.plugin.Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
        if (bp == null || !bp.isEnabled()) return null;
        try {
            return bp.getClass().getMethod("getPetManager").invoke(bp);
        } catch (Throwable t) {
            return null;
        }
    }

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
    static boolean mundoSemPet(World w) {
        if (w == null) return false;
        String n = w.getName().toLowerCase(Locale.ROOT);
        return n.equals("vovo") || n.equals("slender") || n.equals("backrooms") || n.equals("defesa") || n.equals("login") || n.equals(Planta.MUNDO);
    }

    private final java.util.Map<UUID, Long> vigiaEspera = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> avisoPet = new java.util.HashMap<>();
    private boolean vigiaMarcada;

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
                avisoPet.put(u, agora + 600_000L);
            } else vigiaEspera.put(u, agora + 10000);
        }
    }

    void marcarVigia() {
        if (vigiaMarcada || !isEnabled()) return;
        vigiaMarcada = true;
        Bukkit.getScheduler().runTask(this, this::vigiarPets);
    }

    private final Set<UUID> saindo = new HashSet<>();

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
        if (!isEnabled() || saindo.contains(p.getUniqueId()) || !p.isOnline()) return;
        if (mundoSemPet(p.getWorld())) return;
        Object pm = gerentePets();
        if (pm == null) return;
        int id = getConfig().getInt(k);
        try {
            UUID u = p.getUniqueId();
            if (pm.getClass().getMethod("getSelectedPet", UUID.class).invoke(pm, u) != null) {
                esquecerPet(k);
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
            esquecerPet(k);
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
                p.sendMessage(PREFIXO + "§7Seus itens do último Hotel foram devolvidos.");
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

    void registrarVitoria(List<String> nomes, long segundos, List<Player> jogaram) {
        String tempo = String.format("%d:%02d", segundos / 60, segundos % 60);
        int rec = getConfig().getInt("recorde.segundos", 0);
        boolean novo = !nomes.isEmpty() && (rec <= 0 || segundos < rec);
        if (novo) {
            getConfig().set("recorde.segundos", segundos);
            getConfig().set("recorde.jogadores", String.join(", ", nomes));
        }
        for (Player p : jogaram) {
            if (!nomes.contains(p.getName())) continue;
            String k = "vitorias." + p.getUniqueId();
            getConfig().set(k, getConfig().getInt(k, 0) + 1);
            for (String cmd : getConfig().getStringList("recompensa-comandos"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%jogador%", p.getName()));
        }
        saveConfig();
        if (!nomes.isEmpty()) {
            Bukkit.broadcast(txt(PREFIXO + "§f" + String.join(", ", nomes) + " §7saí" + (nomes.size() > 1 ? "ram" : "u")
                    + " vivo" + (nomes.size() > 1 ? "s" : "") + " do Hotel em §f" + tempo + "§7!" + (novo ? " §6§lNOVO RECORDE!" : "")));
        }
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: /hotel admin <iniciar|parar|status>");
            return true;
        }
        switch (sub) {
            case "", "menu" -> menu(p);
            case "entrar", "jogar" -> entrar(p);
            case "sair" -> {
                if (!partida.remover(p)) p.sendMessage(PREFIXO + "Você não está no Hotel.");
                else p.sendMessage(PREFIXO + "Você saiu do Hotel.");
            }
            case "status", "info" -> info(p);
            case "reviver", "moeda" -> {
                if (!Reviver.usarPendente(p))
                    p.sendMessage(PREFIXO + "§7Você não tem uma volta pendente. Moedas de Reviver: §e" + Reviver.moedas(p) + "§7. Use quando morrer.");
            }
            default -> p.sendMessage(PREFIXO + "§f/hotel §7(menu), §f/hotel entrar§7, §f/hotel sair");
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("hotel.admin")) {
            s.sendMessage(PREFIXO + "§cSem permissão.");
            return true;
        }
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        Player p = s instanceof Player pp ? pp : null;
        switch (op) {
            case "eliminar" -> {
                Player alvo = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : p;
                s.sendMessage(PREFIXO + (alvo != null && partida.eliminarTeste(alvo) ? "Eliminado." : "Não deu (precisa estar vivo na partida)."));
            }
            case "iniciar" -> {
                partida.iniciar();
                s.sendMessage(PREFIXO + partida.status());
            }
            case "parar" -> {
                partida.parar();
                s.sendMessage(PREFIXO + "Partida encerrada.");
            }
            case "status" -> s.sendMessage(PREFIXO + partida.status());
            case "porta" -> {
                int n = 1;
                try {
                    n = Integer.parseInt(a[2]);
                } catch (Exception ignored) {
                }
                s.sendMessage(PREFIXO + partida.pularPara(n));
            }
            case "estrondo" -> s.sendMessage(PREFIXO + partida.testeEvento("estrondo"));
            case "retorno" -> s.sendMessage(PREFIXO + partida.testeEvento("retorno"));
            case "olhos" -> s.sendMessage(PREFIXO + partida.testeEvento("olhos"));
            case "sussurro" -> s.sendMessage(PREFIXO + partida.testeEvento("sussurro"));
            case "campainha" -> s.sendMessage(PREFIXO + partida.testeEvento("campainha"));
            case "recarregar" -> {
                reloadConfig();
                s.sendMessage(PREFIXO + "Config recarregada.");
            }
            case "tp" -> {
                if (p != null) mover(p, Recepcao.spawn(mundo));
            }
            default -> s.sendMessage(PREFIXO + "/hotel admin <iniciar|parar|status|porta <n>|estrondo|retorno|olhos|sussurro|tp|recarregar>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> op = new ArrayList<>();
        if (a.length == 1) {
            op.addAll(List.of("entrar", "sair", "status"));
            if (s.hasPermission("hotel.admin")) op.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("hotel.admin")) {
            op.addAll(List.of("iniciar", "parar", "status", "porta", "estrondo", "retorno", "olhos", "sussurro", "tp", "recarregar"));
        }
        String ult = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        op.removeIf(x -> !x.startsWith(ult));
        return op;
    }

    private void entrar(Player p) {
        if (partida.participa(p)) {
            p.sendMessage(PREFIXO + "Você já está no Hotel. §e/hotel sair §7para sair.");
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
        mover(p, Recepcao.spawn(mundo));
        partida.adicionar(p);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.5f);
        p.sendMessage("§4§l--- O HOTEL ---");
        p.sendMessage("§7Seus itens ficaram guardados e voltam quando você sair.");
        p.sendMessage("§fAbra as portas (clique nelas) até a §e" + getConfig().getInt("portas", 50) + "§f. Algumas estão trancadas: a chave está numa §egaveta§f (barril).");
        p.sendMessage("§fLuzes piscando = §cALGO VEM AÍ§f: clique num §earmário§f para se esconder.");
        p.sendMessage("§fSair: §e/hotel sair");
        if (partida.estado == Partida.Estado.JOGANDO) partida.entrarNoMeio(p);
        else if (partida.estado == Partida.Estado.FIM) p.sendMessage(PREFIXO + "§eA partida anterior está terminando. A próxima começa já já.");
    }

    private void info(Player p) {
        p.sendMessage(PREFIXO + "§fEstado: §e" + partida.estadoTexto() + " §8| §fna partida: §e" + partida.jogadores.size());
        int rec = getConfig().getInt("recorde.segundos", 0);
        if (rec > 0) p.sendMessage(PREFIXO + "§fRecorde: §e" + String.format("%d:%02d", rec / 60, rec % 60) + " §7(" + getConfig().getString("recorde.jogadores", "") + ")");
        p.sendMessage(PREFIXO + "§fSuas vitórias: §e" + getConfig().getInt("vitorias." + p.getUniqueId(), 0));
    }

    // ================================================================== menu

    private void menu(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, txt("§4§lO Hotel"));
        h.inv = inv;
        ItemStack vidro = item(Material.BLACK_STAINED_GLASS_PANE, "", " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        ItemStack entrar = Skins.cabecaReserva("estrondo");
        ItemMeta m = entrar.getItemMeta();
        m.displayName(txt(partida.participa(p) ? "§c§lVocê já está na partida" : "§c§lEntrar no Hotel"));
        m.lore(List.of(txt("§7Estado: §f" + partida.estadoTexto()), txt("§7Jogadores: §f" + partida.jogadores.size()),
                txt(""), txt("§eClique para entrar")));
        entrar.setItemMeta(m);
        inv.setItem(11, entrar);
        inv.setItem(13, item(Material.DARK_OAK_DOOR, "", "§e§lComo jogar",
                "§7Abra as portas, uma depois da outra,", "§7até a porta §f" + getConfig().getInt("portas", 50) + "§7.",
                "", "§cLuzes piscando: §fesconda-se num armário!", "§5Olhos roxos: §fnão olhe para eles.",
                "§8Escuro + \"psst\": §folhe para o que apareceu.", "§7Porta trancada: chave numa gaveta (barril).",
                "§7Na biblioteca: §fande agachado§7 e ache 5 livros."));
        int rec = getConfig().getInt("recorde.segundos", 0);
        inv.setItem(15, item(Material.CLOCK, "", "§6§lRecorde",
                rec > 0 ? "§7Mais rápido: §f" + String.format("%d:%02d", rec / 60, rec % 60) : "§7Ninguém saiu vivo ainda.",
                rec > 0 ? "§7Por: §f" + getConfig().getString("recorde.jogadores", "") : "§7Seja o primeiro.",
                "§7Suas vitórias: §f" + getConfig().getInt("vitorias." + p.getUniqueId(), 0)));
        if (partida.participa(p)) inv.setItem(22, item(Material.BARRIER, "", "§c§lSair do Hotel", "§7Seus itens voltam para você."));
        p.openInventory(inv);
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
        } else if (e.getSlot() == 22 && partida.participa(p)) {
            p.closeInventory();
            partida.remover(p);
            p.sendMessage(PREFIXO + "Você saiu do Hotel.");
        }
    }

    @EventHandler
    public void arrastarMenu(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ================================================================== jogo: cliques

    @EventHandler(priority = EventPriority.HIGH)
    public void interagir(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!noMundo(p) || admin(p)) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL || e.getAction() == Action.LEFT_CLICK_BLOCK)
            e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!partida.vivo(p)) return;
        Block b = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b != null
                && (b.getType() == Material.DARK_OAK_DOOR || b.getType() == Material.SPRUCE_DOOR || b.getType() == Material.BARREL)) {
            e.setCancelled(true);
            partida.clicouBloco(p, b);
            return;
        }
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && e.getItem() != null) {
            partida.usarItem(p, e.getItem());
        }
    }

    /** Agachar dentro do armario: sai dele. */
    @EventHandler
    public void agachar(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && noMundo(e.getPlayer())) partida.agachou(e.getPlayer());
    }

    // ================================================================== protecao do mundo

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidade(PlayerInteractEntityEvent e) {
        if (noMundo(e.getRightClicked()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidadeAt(PlayerInteractAtEntityEvent e) {
        if (noMundo(e.getRightClicked()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (!noMundo(e.getEntity())) return;
        Entity en = e.getEntity();
        if (en instanceof Player p) {
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                e.setCancelled(true);
                mover(p, partida.vivo(p) ? partida.lugarSeguro(p) : Recepcao.spawn(mundo));
                return;
            }
            // so o proprio jogo machuca (ele usa dano "custom"); nada de dano de fora
            if (e.getCause() != EntityDamageEvent.DamageCause.CUSTOM) e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p && admin(p)) return;
        e.setCancelled(true);
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
        if (e.getInventory().getHolder() instanceof org.bukkit.block.BlockState
                || e.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest
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
    public void largar(PlayerDropItemEvent e) {
        if (noMundo(e.getPlayer()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void pegarItem(EntityPickupItemEvent e) {
        if (noMundo(e.getEntity()) && !(e.getEntity() instanceof Player p && admin(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void comando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!partida.participa(p) && !noMundo(p)) return;
        if (p.hasPermission("hotel.admin")) return;
        String c = e.getMessage().length() > 1 ? e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        if (c.contains(":")) c = c.substring(c.indexOf(':') + 1);
        if (COMANDOS_LIVRES.contains(c)) return;
        e.setCancelled(true);
        p.sendMessage(PREFIXO + "§cNo Hotel só vale §f/hotel sair§c (e mensagens).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleporte(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (movendo.contains(p.getUniqueId()) || e.getTo() == null) return;
        boolean deDentro = e.getFrom().getWorld().equals(mundo), praDentro = e.getTo().getWorld().equals(mundo);
        if (partida.participa(p) && !praDentro) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "§cPara sair do Hotel use §f/hotel sair§c.");
            return;
        }
        if (!deDentro && praDentro && !partida.participa(p) && !p.hasPermission("hotel.admin")) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "Para jogar use §f/hotel entrar§7.");
        }
    }

    /** Pets e criaturas de outros plugins nao entram no hotel (so as do jogo). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void criaturaNasce(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (!noMundo(e.getEntity())) return;
        Entity en = e.getEntity();
        if (en instanceof org.bukkit.entity.LivingEntity && !(en instanceof Player)
                && !en.getPersistentDataContainer().has(kHotel, PersistentDataType.BYTE)) {
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
        if (partida.participa(e.getPlayer())) e.setRespawnLocation(Recepcao.spawn(mundo));
    }
}
