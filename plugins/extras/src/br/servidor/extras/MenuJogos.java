package br.servidor.extras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 16/11 (Paulo: "nao ta facil de achar os portais, assim perco gente, arrume esse spawn"; jogador novo: "consegui
 * entrar so que nao to entendendo como que joga"). Um menu simples "ESCOLHA UM JOGO" com os modos principais:
 * - abre sozinho na primeira entrada (depois do login);
 * - NPC "JOGAR" bem na frente de quem nasce no spawn (clicar ou bater abre);
 * - comando /jogar (ou /jogos, /menu, /modos).
 * No Bedrock abre o menu nativo de botoes (com icones); no Java, um bau pequeno.
 */
final class MenuJogos implements Listener {

    record Modo(int slot, Material icone, String nome, String bedrockIcone, String[] texto) {}

    static final Modo[] MODOS = {
            new Modo(10, Material.NETHERITE_SWORD, "§4§lMASMORRAS §7(RPG)", "textures/items/netherite_sword",
                    new String[] {"§fO modo principal: chefes, armaduras", "§fe masmorras do nível 1 ao 4.", "§7Classes com poderes e pets."}),
            new Modo(11, Material.CROSSBOW, "§c§lMISSÃO §7(tiro co-op)", "textures/items/crossbow_standby",
                    new String[] {"§fWarface na favela, de 1 a 4 jogadores.", "§7Também: Apocalipse Zumbi e Modo Horda."}),
            new Modo(12, Material.ENDER_EYE, "§6§lMINIGAMES", "textures/items/ender_eye",
                    new String[] {"§fArena PvP, Defesa da Torre,", "§fEsconde-esconde e outros."}),
            new Modo(13, Material.SOUL_LANTERN, "§5§lTERROR", "textures/items/spider_eye",
                    new String[] {"§fHotel (estilo Doors), Backrooms,", "§fCasa da Vovó, Turno da Noite e mais."}),
            new Modo(14, Material.IRON_PICKAXE, "§e§lPRISÃO §7(Rank Up)", "textures/items/iron_pickaxe",
                    new String[] {"§fMinas de A a L, Mina PvP", "§fe dinheiro próprio."}),
            new Modo(15, Material.GRASS_BLOCK, "§a§lSURVIVAL", "textures/items/map_filled",
                    new String[] {"§fMundo aberto: construa e explore", "§fnum lugar aleatório e seguro."}),
            new Modo(16, Material.BOOK, "§f§lGUIA DO SERVIDOR", "textures/items/book_normal",
                    new String[] {"§fTudo o que dá para fazer,", "§fcom atalhos: kits, classes, pets..."}),
    };

    private final Extras pl;
    private final NamespacedKey kNpc;
    private Villager npc;
    private TextDisplay rotulo;
    private Interaction toque;
    private final Map<UUID, Long> ultimaVez = new HashMap<>();

    MenuJogos(Extras pl) {
        this.pl = pl;
        this.kNpc = new NamespacedKey(pl, "npc_jogar");
        Bukkit.getScheduler().runTaskTimer(pl, this::garantirNpc, 120L, 200L);
    }

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void abrir(Player p) {
        long agora = System.currentTimeMillis();
        if (agora - ultimaVez.getOrDefault(p.getUniqueId(), 0L) < 700) return;   // clique duplo no NPC
        ultimaVez.put(p.getUniqueId(), agora);
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate") && FormBedrock.ehBedrock(p) && formBedrock(p)) return;
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, Extras.txt("§8Escolha um jogo"));
        h.inv = inv;
        for (Modo m : MODOS) {
            ItemStack it = new ItemStack(m.icone());
            ItemMeta im = it.getItemMeta();
            im.displayName(Extras.txt(m.nome()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String t : m.texto()) lore.add(Extras.txt(t));
            lore.add(Extras.txt(""));
            lore.add(Extras.txt("§e▶ Clique para ir"));
            im.lore(lore);
            im.addItemFlags(ItemFlag.values());
            it.setItemMeta(im);
            inv.setItem(m.slot(), it);
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
    }

    private boolean formBedrock(Player p) {
        return FormBedrock.jogos(pl, p, MODOS, id -> ir(p, id));   // o formulario fica no FormBedrock (classes do Geyser so existem com ele)
    }

    private void ir(Player p, int i) {
        if (!p.isOnline()) return;
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.4f);
        switch (i) {
            case 0 -> p.performCommand("masmorras spawn");
            case 1 -> p.performCommand("tiro");
            case 2 -> {
                if (pl.lobby != null) pl.lobby.teleportar(p);
                else p.performCommand("minigames");
            }
            case 3 -> {
                if (pl.terror != null) pl.terror.teleportar(p);
                else p.performCommand("terror");
            }
            case 4 -> p.performCommand("minas");
            case 5 -> p.performCommand("rtp");
            default -> p.performCommand("ajuda");
        }
    }

    @EventHandler
    public void clicou(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        for (int i = 0; i < MODOS.length; i++) {
            if (MODOS[i].slot() != e.getSlot()) continue;
            int id = i;
            p.closeInventory();
            Bukkit.getScheduler().runTask(pl, () -> ir(p, id));   // um tique depois (Bedrock nao gosta de teleporte dentro do clique)
            return;
        }
    }

    // ------------------------------------------------------------------ primeira entrada: abre sozinho depois do login
    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean novo = !p.hasPlayedBefore();
        int[] tentativas = {0};
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            if (!p.isOnline() || ++tentativas[0] > 180) {
                t.cancel();
                return;
            }
            if (!Extras.logado(p)) return;
            t.cancel();
            if (!perto(p.getLocation())) return;
            if (novo) {
                p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§6§lBEM-VINDO!"), Extras.txt("§fEscolha um jogo para começar")));
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (p.isOnline()) abrir(p);
                }, 50L);
            } else {
                p.sendActionBar(Extras.txt("§eClique no NPC §6§lJOGAR §eou digite §f/jogar §epara escolher um jogo"));
            }
        }, 40L, 20L);
    }

    // ------------------------------------------------------------------ NPC "JOGAR" na frente de quem nasce
    private World mundo() {
        return Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world"));
    }

    private Location lugar(World w) {
        // quem nasce fica em (6.5, 123, -8.5) olhando para o sul: o NPC fica no caminho, logo depois do farol
        Location l = new Location(w, SpawnPrincipal.CX + 0.5 + pl.getConfig().getDouble("jogar.dx", 0), SpawnPrincipal.PISO + 1,
                SpawnPrincipal.CZ + 0.5 + pl.getConfig().getDouble("jogar.dz", 2.5));
        l.setYaw(180);
        return l;
    }

    private boolean perto(Location l) {
        World w = mundo();
        return w != null && l.getWorld() == w && l.distanceSquared(lugar(w)) < 45 * 45;
    }

    private void garantirNpc() {
        World w = mundo();
        if (w == null || !pl.getConfig().getBoolean("jogar.npc", true)) return;
        Location l = lugar(w);
        if (!l.isChunkLoaded()) return;
        if (npc != null && npc.isValid() && rotulo != null && rotulo.isValid() && toque != null && toque.isValid()) return;
        for (Entity e : w.getNearbyEntities(l, 3, 4, 3)) if (e.getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE)) e.remove();
        npc = w.spawn(l, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(false);
            v.setProfession(Villager.Profession.CARTOGRAPHER);
            v.setVillagerType(Villager.Type.PLAINS);
            v.setCollidable(false);
            v.setGlowing(true);
            v.getPersistentDataContainer().set(kNpc, PersistentDataType.BYTE, (byte) 1);
        });
        rotulo = w.spawn(l.clone().add(0, 2.5, 0), TextDisplay.class, t -> {
            t.text(Extras.txt("§6§l▶ JOGAR ◀\n§fEscolha um modo de jogo\n§eClique aqui §7ou §e/jogar"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBackgroundColor(Color.fromARGB(150, 40, 20, 0));
            t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(1.3f, 1.3f, 1.3f), new org.joml.AxisAngle4f()));
            t.setPersistent(false);
            t.getPersistentDataContainer().set(kNpc, PersistentDataType.BYTE, (byte) 1);
        });
        toque = w.spawn(l, Interaction.class, i -> {
            i.setInteractionWidth(1.3f);
            i.setInteractionHeight(2.3f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(kNpc, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        abrir(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void bater(EntityDamageEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p) abrir(p);
    }

    @EventHandler
    public void carregou(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities())
            if (en.getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE) && en != npc && en != rotulo && en != toque) en.remove();
    }

    void desligar() {
        for (Entity en : new Entity[] {npc, rotulo, toque}) if (en != null && en.isValid()) en.remove();
    }
}
