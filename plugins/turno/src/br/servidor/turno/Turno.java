package br.servidor.turno;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Turno da Noite: minigame original de vigia noturno (um jogador por instancia). Voce e o vigia do "Zoo Mecanico do Seu
 * Ze"; os bichos de pelucia mecanicos (Capi, Tuca, Jaca e Boto) andam de noite. Sobreviva das 12 as 6 com portas, luzes
 * e cameras, gastando energia. Noites 1 a 5 e a noite personalizada (IA 0-20 por bicho). Java e Bedrock.
 */
public final class Turno extends JavaPlugin implements Listener {

    static final String MUNDO = "turno_noite";
    static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    static Component txt(String s) {
        return LEGADO.deserialize(s);
    }

    World mundo;
    Skins skins;
    Guarda guarda;
    NamespacedKey kTablet, kMenu;
    private final Map<Integer, Mapa> mapas = new HashMap<>();
    private final Map<UUID, Noite> noites = new HashMap<>();
    private File progressoArq;
    private YamlConfiguration progresso;
    private final Random rnd = new Random();

    // ------------------------------------------------------------------ vida do plugin

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kTablet = new NamespacedKey(this, "tablet");
        kMenu = new NamespacedKey(this, "menu");
        progressoArq = new File(getDataFolder(), "progresso.yml");
        progresso = YamlConfiguration.loadConfiguration(progressoArq);
        guarda = new Guarda(new File(getDataFolder(), "guardados"));
        skins = new Skins(this);
        mundo = mundo();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Pacote(), this);
        getLogger().info("Turno da Noite ligado: /turno (mundo " + MUNDO + ", " + getConfig().getInt("instancias", 4) + " instancias).");
    }

    @Override
    public void onDisable() {
        for (Noite n : new ArrayList<>(noites.values())) n.terminar(false, null);
        noites.clear();
    }

    private World mundo() {
        World w = Bukkit.getWorld(MUNDO);
        if (w == null) {
            w = new WorldCreator(MUNDO).generator(new ChunkGenerator() {
            }).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        }
        if (w != null) {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.KEEP_INVENTORY, true);
            w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            w.setGameRule(GameRule.DO_FIRE_TICK, false);
            w.setGameRule(GameRule.MOB_GRIEFING, false);
            w.setTime(18000);
            w.setStorm(false);
            w.setSpawnLocation(0, Mapa.Y + 1, 0);
        }
        return w;
    }

    Mapa mapa(int i) {
        return mapas.computeIfAbsent(i, k -> {
            Mapa m = new Mapa(mundo, k * Mapa.ESPACO);
            File flag = new File(getDataFolder(), "mapa-" + k + ".txt");
            if (!flag.exists()) {
                m.construir();
                try {
                    getDataFolder().mkdirs();
                    java.nio.file.Files.writeString(flag.toPath(), "construido");
                } catch (IOException ignored) {
                }
            }
            return m;
        });
    }

    // ------------------------------------------------------------------ progresso e recompensa

    int noiteMax(Player p) {
        return progresso.getInt(p.getUniqueId() + ".noite", 1);
    }

    void venceu(Player p, int noite) {
        int atual = noiteMax(p);
        if (noite < 6 && noite + 1 > atual) {
            progresso.set(p.getUniqueId() + ".noite", noite + 1);
            progresso.set(p.getUniqueId() + ".nome", p.getName());
            salvarProgresso();
        }
        int moedas = getConfig().getInt("recompensa.noite-" + Math.min(noite, 6), 30 * noite);
        if (moedas > 0) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), getConfig().getString("recompensa.comando", "coronas dar %jogador% %valor%")
                    .replace("%jogador%", p.getName()).replace("%valor%", String.valueOf(moedas)));
            p.sendMessage(txt("§6+" + moedas + " coronas §7por sobreviver à noite " + (noite == 6 ? "personalizada" : noite) + "."));
        }
        if (noite == 5) Bukkit.broadcast(txt("§d✦ §f" + p.getName() + " §7sobreviveu às 5 noites do §eTurno da Noite§7! A noite personalizada está liberada."));
    }

    private void salvarProgresso() {
        try {
            progresso.save(progressoArq);
        } catch (IOException e) {
            getLogger().warning("Progresso: " + e);
        }
    }

    // ------------------------------------------------------------------ entrar e sair

    private int instanciaLivre() {
        int n = Math.max(1, getConfig().getInt("instancias", 4));
        for (int i = 0; i < n; i++) {
            final int fi = i;
            if (noites.values().stream().noneMatch(x -> x.instancia == fi)) return i;
        }
        return -1;
    }

    void comecar(Player p, int noite, int[] ia) {
        if (noites.containsKey(p.getUniqueId())) {
            p.sendMessage(txt("§cVocê já está num turno."));
            return;
        }
        int inst = instanciaLivre();
        if (inst < 0) {
            p.sendMessage(txt("§cTodos os escritórios estão ocupados. Tente daqui a pouco."));
            return;
        }
        if (noite < 6 && noite > noiteMax(p)) {
            p.sendMessage(txt("§cVocê ainda não chegou nessa noite."));
            return;
        }
        if (mundo == null) {
            p.sendMessage(txt("§cO mundo do minigame não carregou."));
            return;
        }
        try {
            if (!guarda.tem(p)) guarda.guardar(p);
        } catch (IOException e) {
            p.sendMessage(txt("§cNão deu para guardar seu inventário. Tente de novo."));
            getLogger().warning("Guardar " + p.getName() + ": " + e);
            return;
        }
        Mapa m = mapa(inst);
        Noite n = new Noite(this, m, inst, p, noite, ia);
        noites.put(p.getUniqueId(), n);
        p.sendMessage(txt("§e§lTURNO DA NOITE §7— noite " + (noite == 6 ? "personalizada" : noite) + ". Para sair das câmeras: agache (Shift), pule (Espaço) ou aperte Q; no celular, toque em SAIR na barra. /turno sair para desistir."));
        n.comecar();
    }

    /** Tira o jogador do minigame (devolve inventario, volta ao lobby). */
    void sair(Player p, boolean desistiu) {
        Noite n = noites.remove(p.getUniqueId());
        if (n != null && !n.acabou) n.terminar(false, null);
        if (guarda.tem(p)) {
            p.setWalkSpeed(0.2f);
            Guarda.limpar(p);
            guarda.devolver(p, true);
            if (desistiu) p.sendMessage(txt("§7Você saiu do turno."));
        } else if (n != null || p.getWorld().equals(mundo)) {
            // ja devolvido por outro caminho (ex.: o fim da noite chamou antes): so garante que ele saiu do mundo
            p.setWalkSpeed(0.2f);
            if (p.getWorld().equals(mundo)) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    void noiteAcabou(Noite n) {
        noites.remove(n.jogador, n);
    }

    Noite noiteDe(Player p) {
        return noites.get(p.getUniqueId());
    }

    void skinPronta(String id) {
        for (Noite n : noites.values()) for (Animatronico a : n.bichos) if (a.id.equals(id) && a.boneco != null && a.boneco.isValid()) {
            var perfil = skins.perfil(id);
            if (perfil != null) a.boneco.setProfile(perfil);
        }
    }

    // ------------------------------------------------------------------ menu

    private static final int[] SLOTS_NOITES = {10, 11, 12, 13, 14};

    void menu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, txt("§8Turno da Noite"));
        int max = noiteMax(p);
        for (int i = 0; i < 5; i++) {
            int noite = i + 1;
            boolean aberta = noite <= max;
            ItemStack it = new ItemStack(aberta ? Material.CLOCK : Material.BARRIER);
            ItemMeta m = it.getItemMeta();
            m.displayName(txt((aberta ? "§e" : "§8") + "Noite " + noite));
            List<Component> lore = new ArrayList<>();
            lore.add(txt(aberta ? "§7Clique para começar" : "§7Vença a noite " + (noite - 1) + " primeiro"));
            lore.add(txt("§8IA: Tuca " + IA[i][0] + " · Jaca " + IA[i][1] + " · Capi " + IA[i][2] + " · Boto " + IA[i][3]));
            m.lore(lore);
            m.getPersistentDataContainer().set(kMenu, PersistentDataType.INTEGER, noite);
            it.setItemMeta(m);
            inv.setItem(SLOTS_NOITES[i], it);
        }
        ItemStack pers = new ItemStack(max >= 6 ? Material.NETHER_STAR : Material.GRAY_DYE);
        ItemMeta pm = pers.getItemMeta();
        pm.displayName(txt(max >= 6 ? "§dNoite personalizada" : "§8Noite personalizada"));
        pm.lore(List.of(txt(max >= 6 ? "§7Todos os bichos no máximo (IA 20)" : "§7Vença a noite 5 para liberar"),
                txt("§8Também: /turno custom <tuca> <jaca> <capi> <boto>")));
        pm.getPersistentDataContainer().set(kMenu, PersistentDataType.INTEGER, 6);
        pers.setItemMeta(pm);
        inv.setItem(16, pers);
        ItemStack como = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta cm = como.getItemMeta();
        cm.displayName(txt("§fComo jogar"));
        cm.lore(List.of(txt("§7Você é o vigia. Fique vivo das 12 às 6."), txt("§7Botões na mesa: §cPORTA §7e §fLUZ §7de cada lado."),
                txt("§7Tablet: clique direito = câmeras; agache = sai."), txt("§7Tudo gasta §eenergia§7. Acabou, apagou."),
                txt("§7Capi só anda quando ninguém olha as câmeras."), txt("§7Boto some se você não olhar a Cortina... ou se olhar demais.")));
        como.setItemMeta(cm);
        inv.setItem(22, como);
        p.openInventory(inv);
    }

    static final int[][] IA = {{1, 1, 0, 0}, {3, 2, 0, 1}, {5, 4, 1, 2}, {7, 6, 2, 5}, {9, 8, 4, 7}};

    private int[] iaDaNoite(int noite) {
        if (noite >= 1 && noite <= 5) {
            List<Integer> l = getConfig().getIntegerList("ia.noite-" + noite);
            if (l.size() == 4) return new int[]{l.get(0), l.get(1), l.get(2), l.get(3)};
            return IA[noite - 1].clone();
        }
        return new int[]{20, 20, 20, 20};
    }

    @EventHandler
    public void clicouMenu(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Noite n = noiteDe(p);
        if (n != null) {
            e.setCancelled(true);     // nada de mexer no inventario durante o turno
            return;
        }
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        Integer noite = it.getItemMeta().getPersistentDataContainer().get(kMenu, PersistentDataType.INTEGER);
        if (noite == null) return;
        e.setCancelled(true);
        p.closeInventory();
        if (noite == 6 && noiteMax(p) < 6) {
            p.sendMessage(txt("§cVença a noite 5 para liberar a noite personalizada."));
            return;
        }
        comecar(p, noite, iaDaNoite(noite));
    }

    // ------------------------------------------------------------------ comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("So no jogo.");
            return true;
        }
        if (a.length == 0) {
            if (noiteDe(p) != null) {
                p.sendMessage(txt("§7Você está no turno. §f/turno sair §7para desistir."));
                return true;
            }
            menu(p);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "sair" -> {
                if (noiteDe(p) == null) {
                    p.sendMessage(txt("§7Você não está num turno."));
                    return true;
                }
                sair(p, true);
            }
            case "custom", "personalizada" -> {
                if (noiteMax(p) < 6 && !p.hasPermission("turno.admin")) {
                    p.sendMessage(txt("§cVença a noite 5 para liberar."));
                    return true;
                }
                int[] ia = {20, 20, 20, 20};
                for (int i = 0; i < 4 && i + 1 < a.length; i++) {
                    try {
                        ia[i] = Math.max(0, Math.min(20, Integer.parseInt(a[i + 1])));
                    } catch (NumberFormatException ex) {
                        p.sendMessage(txt("§cUse números de 0 a 20: /turno custom <tuca> <jaca> <capi> <boto>"));
                        return true;
                    }
                }
                comecar(p, 6, ia);
            }
            case "noite" -> {
                if (a.length < 2) {
                    menu(p);
                    return true;
                }
                int noite;
                try {
                    noite = Integer.parseInt(a[1]);
                } catch (NumberFormatException ex) {
                    menu(p);
                    return true;
                }
                if (noite < 1 || noite > 6) {
                    p.sendMessage(txt("§cNoites 1 a 6."));
                    return true;
                }
                if (noite > noiteMax(p) && !p.hasPermission("turno.admin")) {
                    p.sendMessage(txt("§cVocê ainda não chegou nessa noite."));
                    return true;
                }
                if (p.hasPermission("turno.admin") && noite > noiteMax(p)) {
                    // admin testando: vai direto
                    Noite n = noites.get(p.getUniqueId());
                    if (n == null) {
                        progressoAdmin(p, noite);
                    }
                }
                comecar(p, noite, iaDaNoite(noite));
            }
            case "admin" -> {
                if (!p.hasPermission("turno.admin")) {
                    p.sendMessage(txt("§cSem permissão."));
                    return true;
                }
                if (a.length >= 2 && a[1].equalsIgnoreCase("skins")) {
                    p.sendMessage(txt(skins.resumo()));
                    skins.gerarFaltando();
                } else if (a.length >= 3 && a[1].equalsIgnoreCase("liberar")) {
                    Player alvo = Bukkit.getPlayerExact(a[2]);
                    int ate = a.length >= 4 ? Integer.parseInt(a[3]) : 6;
                    if (alvo == null) {
                        p.sendMessage(txt("§cJogador offline."));
                        return true;
                    }
                    progresso.set(alvo.getUniqueId() + ".noite", ate);
                    salvarProgresso();
                    p.sendMessage(txt("§a" + alvo.getName() + " liberado até a noite " + ate + "."));
                } else if (a.length >= 2 && a[1].equalsIgnoreCase("reconstruir")) {
                    for (int i = 0; i < Math.max(1, getConfig().getInt("instancias", 4)); i++) {
                        new File(getDataFolder(), "mapa-" + i + ".txt").delete();
                        mapas.remove(i);
                        mapa(i);
                    }
                    p.sendMessage(txt("§aMapas reconstruídos."));
                } else if (a.length >= 2 && a[1].equalsIgnoreCase("ir")) {
                    p.teleport(mapa(0).loc(Mapa.CADEIRA));
                    p.setGameMode(GameMode.CREATIVE);
                } else {
                    p.sendMessage(txt("§e/turno admin skins | liberar <jogador> [noite] | reconstruir | ir"));
                }
            }
            default -> menu(p);
        }
        return true;
    }

    private void progressoAdmin(Player p, int noite) {
        // so o admin pula noites para testar; o progresso dele nao muda
    }

    // ------------------------------------------------------------------ controles do vigia

    private boolean tablet(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kTablet, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void interagiu(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Noite n = noiteDe(p);
        if (n == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action ac = e.getAction();
        Block b = e.getClickedBlock();
        // botoes da mesa
        if (b != null && (ac == Action.RIGHT_CLICK_BLOCK || ac == Action.LEFT_CLICK_BLOCK)) {
            int bx = b.getX() - n.instancia * Mapa.ESPACO, bz = b.getZ();
            String tipo = b.getType().name();
            if (bz == Mapa.BOTAO_Z && tipo.endsWith("_BUTTON") && (bx == Mapa.BOTAO_PORTA_OESTE_X || bx == Mapa.BOTAO_LUZ_OESTE_X
                    || bx == Mapa.BOTAO_LUZ_LESTE_X || bx == Mapa.BOTAO_PORTA_LESTE_X)) {
                n.botao(p, bx);
                return;
            }
        }
        // tablet
        if (tablet(e.getItem()) || tablet(p.getInventory().getItemInMainHand())) {
            if (n.camera < 0) {
                if (ac == Action.RIGHT_CLICK_AIR || ac == Action.RIGHT_CLICK_BLOCK) n.abrirCameras(p);
            } else {
                if (ac == Action.RIGHT_CLICK_AIR || ac == Action.RIGHT_CLICK_BLOCK) n.proximaCamera(p, 1);
                else n.proximaCamera(p, -1);
            }
        }
    }

    @EventHandler
    public void agachou(PlayerToggleSneakEvent e) {
        Noite n = noiteDe(e.getPlayer());
        if (n == null || !e.isSneaking()) return;
        if (n.camera >= 0) n.fecharCameras(e.getPlayer());
    }

    /** Durante a noite ninguem sai do lugar (cadeira ou camera): segura qualquer subida ou passo (pulo no celular, empurrao). */
    @EventHandler(ignoreCancelled = true)
    public void parado(org.bukkit.event.player.PlayerMoveEvent e) {
        Noite n = noiteDe(e.getPlayer());
        if (n == null || n.acabou) return;
        org.bukkit.Location de = e.getFrom(), para = e.getTo();
        boolean andou = Math.abs(para.getX() - de.getX()) > 0.001 || Math.abs(para.getZ() - de.getZ()) > 0.001;
        if (!andou && para.getY() <= de.getY() + 0.001) return;     // so virou a cabeca (ou esta descendo de volta)
        org.bukkit.Location fica = de.clone();
        fica.setYaw(para.getYaw());
        fica.setPitch(para.getPitch());
        e.setTo(fica);
    }

    /** Sair das cameras tambem com pulo (espaco) ou andar para tras (S), alem de agachar: qualquer tecla "de sair". */
    @EventHandler
    public void teclas(org.bukkit.event.player.PlayerInputEvent e) {
        Noite n = noiteDe(e.getPlayer());
        if (n == null || n.camera < 0 || n.ticks - n.cameraDesde < 6) return;
        org.bukkit.Input i = e.getInput();
        if (i.isSneak() || i.isJump() || i.isBackward()) n.fecharCameras(e.getPlayer());
    }

    /** O tablet fica sempre na mao; escolher outro espaco da barra (no celular: tocar no botao SAIR) larga as cameras. */
    @EventHandler
    public void trocouEspaco(org.bukkit.event.player.PlayerItemHeldEvent e) {
        Noite n = noiteDe(e.getPlayer());
        if (n == null || e.getNewSlot() == 0) return;
        e.setCancelled(true);
        if (n.camera >= 0) n.fecharCameras(e.getPlayer());
    }

    @EventHandler
    public void trocouMao(PlayerSwapHandItemsEvent e) {
        Noite n = noiteDe(e.getPlayer());
        if (n == null) return;
        e.setCancelled(true);
        // F = troca de camera para tras (atalho)
        if (n.camera >= 0) n.proximaCamera(e.getPlayer(), -1);
    }

    @EventHandler
    public void soltou(PlayerDropItemEvent e) {
        Noite n = noiteDe(e.getPlayer());
        if (n == null) return;
        e.setCancelled(true);
        // Q nas cameras = largar o tablet (sair)
        if (n.camera >= 0 && n.ticks - n.cameraDesde >= 6) n.fecharCameras(e.getPlayer());
    }

    @EventHandler
    public void quebrou(BlockBreakEvent e) {
        if (e.getPlayer().getWorld().equals(mundo) && !e.getPlayer().hasPermission("turno.admin")) e.setCancelled(true);
    }

    @EventHandler
    public void colocou(BlockPlaceEvent e) {
        if (e.getPlayer().getWorld().equals(mundo) && !e.getPlayer().hasPermission("turno.admin")) e.setCancelled(true);
    }

    @EventHandler
    public void dano(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (noiteDe(p) != null || p.getWorld().equals(mundo))) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && noiteDe(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Noite n = noites.remove(p.getUniqueId());
        if (n != null) {
            n.terminar(false, null);
            p.setWalkSpeed(0.2f);
            Guarda.limpar(p);
            guarda.devolver(p, true);
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (guarda.tem(p)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline()) return;
                p.setWalkSpeed(0.2f);
                Guarda.limpar(p);
                guarda.devolver(p, true);
                p.sendMessage(txt("§7Seu inventário do Turno da Noite foi devolvido."));
            }, 10L);
        } else if (p.getWorld().equals(mundo) && noiteDe(p) == null) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && noiteDe(p) == null && p.getWorld().equals(mundo)) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }, 10L);
        }
    }

    @EventHandler
    public void clicouInventario(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && noiteDe(p) != null && e.getInventory().getType() != InventoryType.CRAFTING) e.setCancelled(true);
    }

    @EventHandler
    public void mudouMundo(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Noite n = noiteDe(p);
        if (n != null && !p.getWorld().equals(mundo)) sair(p, false);
    }

    Location lobby() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    static Sound som(String nome, Sound padrao) {
        try {
            return Sound.valueOf(nome);
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }
}
