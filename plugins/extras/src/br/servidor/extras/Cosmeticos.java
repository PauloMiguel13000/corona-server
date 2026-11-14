package br.servidor.extras;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 14/11 (Paulo: "crie uma loja no spawn fodona com varios cosmeticos, efeitos, chapeus para usar nos lobbys e
 * minigames, menos nos outros, pro item nao atrapalhar em nada; que apareca em todos os minigames, ate os de terror").
 *
 * LOJA DE COSMETICOS: comprados com coronas, ficam guardados no jogador. So aparecem nos LOBBIES (praca do spawn,
 * lobby dos minigames, lobby do terror) e DENTRO dos minigames (todos, inclusive terror). No Survival, nas Masmorras
 * e na Prisao somem sozinhos. O chapeu nunca e um item "de verdade": nao sai da cabeca, nao cai ao morrer, nao vai
 * para bau/mochila, e se o minigame precisar do capacete o chapeu nao entra. Mercador no spawn e /cosmeticos.
 */
final class Cosmeticos implements Listener {

    record Chapeu(String id, String nome, Material item, int preco, String desc) {}
    record Efeito(String id, String nome, Material icone, int preco, String desc) {}

    static final List<Chapeu> CHAPEUS = List.of(
            new Chapeu("abobora", "Abóbora Assombrada", Material.JACK_O_LANTERN, 400, "Brilha no escuro. Perfeita pro terror."),
            new Chapeu("creeper", "Cabeça de Creeper", Material.CREEPER_HEAD, 600, "Tsssss..."),
            new Chapeu("zumbi", "Cabeça de Zumbi", Material.ZOMBIE_HEAD, 500, "Cérebros!"),
            new Chapeu("esqueleto", "Crânio de Esqueleto", Material.SKELETON_SKULL, 500, "Só osso."),
            new Chapeu("piglin", "Cabeça de Piglin", Material.PIGLIN_HEAD, 700, "Do Nether, com estilo."),
            new Chapeu("wither", "Crânio do Wither", Material.WITHER_SKELETON_SKULL, 1200, "Sombrio e raro."),
            new Chapeu("dragao", "Cabeça de Dragão", Material.DRAGON_HEAD, 3000, "A mais lendária de todas."),
            new Chapeu("astronauta", "Capacete de Astronauta", Material.LIGHT_BLUE_STAINED_GLASS, 800, "Pronto pro espaço."),
            new Chapeu("coroa", "Coroa de Ouro", Material.GOLD_BLOCK, 2000, "Para quem manda no servidor."),
            new Chapeu("diamante", "Cabeça de Diamante", Material.DIAMOND_BLOCK, 2500, "Brilho máximo."),
            new Chapeu("tnt", "Cabeça de TNT", Material.TNT, 900, "Não chegue perto!"),
            new Chapeu("bolo", "Chapéu de Bolo", Material.CAKE, 700, "Tem bolo pra todo mundo?"),
            new Chapeu("melancia", "Melancia", Material.MELON, 400, "Refrescante."),
            new Chapeu("slime", "Bloco de Slime", Material.SLIME_BLOCK, 600, "Gosmento e feliz."),
            new Chapeu("farol", "Farol", Material.BEACON, 2200, "Uma luz que todo mundo vê."),
            new Chapeu("bandeira", "Bandeira Corona", Material.RED_BANNER, 1000, "O símbolo do servidor."),
            new Chapeu("gelo", "Bloco de Gelo", Material.PACKED_ICE, 500, "Cabeça fria."),
            new Chapeu("mel", "Bloco de Mel", Material.HONEY_BLOCK, 600, "Doce demais."));

    static final List<Efeito> EFEITOS = List.of(
            new Efeito("coracoes", "Corações", Material.RED_DYE, 600, "Amor espalhado por onde passa."),
            new Efeito("notas", "Notas Musicais", Material.NOTE_BLOCK, 600, "Seu próprio som."),
            new Efeito("fogo", "Chamas", Material.BLAZE_POWDER, 900, "Pegando fogo (sem queimar)."),
            new Efeito("almas", "Fogo das Almas", Material.SOUL_LANTERN, 1200, "Azul e assustador."),
            new Efeito("magia", "Magia", Material.ENCHANTED_BOOK, 900, "Runas de encantamento."),
            new Efeito("arcoiris", "Arco-íris", Material.PRISMARINE_CRYSTALS, 1800, "Todas as cores atrás de você."),
            new Efeito("estrelas", "Estrelas", Material.END_ROD, 1300, "Brilhinhos no ar."),
            new Efeito("nuvem", "Nuvem de Chuva", Material.WATER_BUCKET, 1000, "Sua nuvem particular."),
            new Efeito("neve", "Neve", Material.SNOWBALL, 700, "Inverno o ano todo."),
            new Efeito("raios", "Faíscas", Material.LIGHTNING_ROD, 1100, "Energia elétrica pura."),
            new Efeito("bolhas", "Bolhas", Material.HEART_OF_THE_SEA, 600, "Bolhinhas subindo."),
            new Efeito("cerejeira", "Pétalas de Cerejeira", Material.CHERRY_LEAVES, 1000, "Pétalas rosas caindo."),
            new Efeito("dinheiro", "Chuva de Ouro", Material.GOLD_INGOT, 2500, "Ostentação total."),
            new Efeito("totem", "Aura do Totem", Material.TOTEM_OF_UNDYING, 3000, "Raro: a aura mais bonita."),
            new Efeito("fumaca", "Fumaça", Material.CAMPFIRE, 500, "Misterioso."),
            new Efeito("ender", "Portal do Fim", Material.ENDER_EYE, 1400, "Partículas roxas do End."));

    static final Set<String> MUNDOS_MINIGAMES = Set.of(LobbyMinigames.MUNDO, LobbyTerror.MUNDO, "hotel", "slender", "vovo", "backrooms",
            "fabrica", "naochore", "defesa", "arena", "turno_noite");

    private final Extras pl;
    private final NamespacedKey kChapeuItem, kDono, kEscolhaC, kEscolhaE, kNpc;
    private final Map<UUID, Long> confirma = new HashMap<>();
    private final Map<UUID, String> confirmaId = new HashMap<>();
    private Entity npc, rotulo, toque;
    private int giro;

    Cosmeticos(Extras pl) {
        this.pl = pl;
        kChapeuItem = new NamespacedKey(pl, "cos_chapeu_item");
        kDono = new NamespacedKey(pl, "cos_comprados");
        kEscolhaC = new NamespacedKey(pl, "cos_chapeu");
        kEscolhaE = new NamespacedKey(pl, "cos_efeito");
        kNpc = new NamespacedKey(pl, "cos_npc");
        Bukkit.getScheduler().runTaskTimer(pl, this::conferir, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(pl, this::particulas, 60L, 4L);
        Bukkit.getScheduler().runTaskTimer(pl, this::mercador, 100L, 200L);
    }

    // ------------------------------------------------------------------ onde vale

    static boolean noSpawn(Location l) {
        if (!l.getWorld().getName().equals("world")) return false;
        double dx = l.getX() - SpawnPrincipal.CX, dz = l.getZ() - SpawnPrincipal.CZ;
        return dx * dx + dz * dz <= (SpawnPrincipal.R + 4) * (SpawnPrincipal.R + 4) && l.getY() > SpawnPrincipal.PISO - 6;
    }

    static boolean permitido(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        String w = p.getWorld().getName().toLowerCase();
        for (String m : MUNDOS_MINIGAMES) if (w.equals(m) || w.startsWith(m + "_") || w.startsWith(m)) return true;
        return noSpawn(p.getLocation());
    }

    // ------------------------------------------------------------------ dados do jogador

    private Set<String> comprados(Player p) {
        String s = p.getPersistentDataContainer().get(kDono, PersistentDataType.STRING);
        return s == null || s.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(s.split(",")));
    }

    private void salvarComprados(Player p, Set<String> s) { p.getPersistentDataContainer().set(kDono, PersistentDataType.STRING, String.join(",", s)); }

    private String escolha(Player p, NamespacedKey k) {
        Map<UUID, String> prova = k == kEscolhaC ? provaC : provaE;
        String t = prova.get(p.getUniqueId());
        if (t != null) return t.isEmpty() ? null : t;
        return p.getPersistentDataContainer().get(k, PersistentDataType.STRING);
    }

    /** Admin: prova um cosmetico sem comprar e sem salvar nada (some ao sair do servidor). "" = limpa a prova. */
    private final Map<UUID, String> provaC = new HashMap<>(), provaE = new HashMap<>();

    String provar(Player p, String id) {
        if (id == null || id.equalsIgnoreCase("limpar")) { provaC.remove(p.getUniqueId()); provaE.remove(p.getUniqueId()); tirarChapeu(p); return "prova limpa"; }
        if (chapeu(id) != null) { provaC.put(p.getUniqueId(), id); return "provando chapéu " + id; }
        if (efeito(id) != null) { provaE.put(p.getUniqueId(), id); return "provando efeito " + id; }
        return "id desconhecido";
    }

    private long saldo(Player p) {
        Long v = p.getPersistentDataContainer().get(new NamespacedKey("masmorras", "coronas"), PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    private static Chapeu chapeu(String id) { for (Chapeu c : CHAPEUS) if (c.id().equals(id)) return c; return null; }

    private static Efeito efeito(String id) { for (Efeito e : EFEITOS) if (e.id().equals(id)) return e; return null; }

    // ------------------------------------------------------------------ chapeu na cabeca

    private boolean ehChapeu(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kChapeuItem, PersistentDataType.STRING);
    }

    private ItemStack itemChapeu(Chapeu c) {
        ItemStack it = new ItemStack(c.item());
        ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt("§d✦ " + c.nome() + " §7(cosmético)"));
        m.lore(List.of(Extras.txt("§7Só aparece nos lobbies e minigames."), Extras.txt("§7Troque em §e/cosmeticos")));
        m.getPersistentDataContainer().set(kChapeuItem, PersistentDataType.STRING, c.id());
        if (m instanceof BannerMeta b) {
            b.addPattern(new Pattern(org.bukkit.DyeColor.YELLOW, PatternType.CROSS));
            b.addPattern(new Pattern(org.bukkit.DyeColor.YELLOW, PatternType.BORDER));
        }
        it.setItemMeta(m);
        return it;
    }

    /** Tira qualquer chapeu cosmetico de qualquer lugar do inventario (nunca deixa vazar). */
    private void tirarChapeu(Player p) {
        var inv = p.getInventory();
        if (ehChapeu(inv.getHelmet())) inv.setHelmet(null);
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) if (ehChapeu(c[i])) inv.setItem(i, null);
        if (ehChapeu(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    private void conferir() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // na Arena PvP o capacete e de verdade (kit), entao chapeu cosmetico nao entra la; efeitos sim
            boolean ok = permitido(p) && !p.getWorld().getName().toLowerCase().startsWith("arena");
            Chapeu c = chapeu(escolha(p, kEscolhaC));
            ItemStack cab = p.getInventory().getHelmet();
            if (!ok || c == null) { if (ehChapeu(cab)) tirarChapeu(p); continue; }
            if (ehChapeu(cab)) {
                if (!c.id().equals(cab.getItemMeta().getPersistentDataContainer().get(kChapeuItem, PersistentDataType.STRING))) p.getInventory().setHelmet(itemChapeu(c));
            } else if (cab == null || cab.getType().isAir()) {
                p.getInventory().setHelmet(itemChapeu(c));   // so poe se a cabeca estiver livre (o minigame tem prioridade)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void clique(InventoryClickEvent e) {
        if (ehChapeu(e.getCurrentItem()) || ehChapeu(e.getCursor())) { e.setCancelled(true); return; }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p) {
            ItemStack h = p.getInventory().getItem(e.getHotbarButton());
            if (ehChapeu(h)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void arrastar(InventoryDragEvent e) { if (ehChapeu(e.getOldCursor())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void soltar(PlayerDropItemEvent e) {
        if (ehChapeu(e.getItemDrop().getItemStack())) { e.getItemDrop().remove(); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void morte(PlayerDeathEvent e) { e.getDrops().removeIf(this::ehChapeu); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void mundo(PlayerChangedWorldEvent e) { if (!permitido(e.getPlayer())) tirarChapeu(e.getPlayer()); }

    @EventHandler
    public void saiu(PlayerQuitEvent e) { tirarChapeu(e.getPlayer()); provaC.remove(e.getPlayer().getUniqueId()); provaE.remove(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void entrou(PlayerJoinEvent e) { tirarChapeu(e.getPlayer()); }

    // ------------------------------------------------------------------ efeitos

    private void particulas() {
        giro++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Efeito ef = efeito(escolha(p, kEscolhaE));
            if (ef == null || !permitido(p) || p.isInvisible()) continue;
            tocar(p, ef.id());
        }
    }

    private void tocar(Player p, String id) {
        World w = p.getWorld();
        Location l = p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = giro * 0.45;
        Location roda = l.clone().add(Math.cos(ang) * 0.7, 0.2 + (giro % 20) * 0.09, Math.sin(ang) * 0.7);
        Location cabeca = l.clone().add(0, 2.3, 0);
        boolean andando = p.getVelocity().lengthSquared() > 0.005 || p.isSprinting();
        switch (id) {
            case "coracoes" -> { if (giro % 3 == 0) w.spawnParticle(Particle.HEART, cabeca, 1, 0.3, 0.1, 0.3, 0); }
            case "notas" -> { if (giro % 2 == 0) w.spawnParticle(Particle.NOTE, cabeca, 1, 0.4, 0.1, 0.4, r.nextDouble()); }
            case "fogo" -> w.spawnParticle(Particle.FLAME, roda, 2, 0.02, 0.02, 0.02, 0.005);
            case "almas" -> w.spawnParticle(Particle.SOUL_FIRE_FLAME, roda, 2, 0.02, 0.02, 0.02, 0.005);
            case "magia" -> w.spawnParticle(Particle.ENCHANT, l.clone().add(0, 1.2, 0), 8, 0.5, 0.6, 0.5, 0.6);
            case "arcoiris" -> {
                Color cor = Color.fromRGB(java.awt.Color.HSBtoRGB((giro % 40) / 40f, 0.9f, 1f) & 0xFFFFFF);
                Location at = andando ? l.clone().add(0, 0.3, 0) : roda;
                w.spawnParticle(Particle.DUST, at, 3, 0.15, 0.05, 0.15, 0, new Particle.DustOptions(cor, 1.3f));
            }
            case "estrelas" -> w.spawnParticle(Particle.END_ROD, l.clone().add(0, 1, 0), 1, 0.5, 0.6, 0.5, 0.01);
            case "nuvem" -> {
                w.spawnParticle(Particle.CLOUD, l.clone().add(0, 2.7, 0), 3, 0.35, 0.05, 0.35, 0);
                w.spawnParticle(Particle.FALLING_WATER, l.clone().add(0, 2.5, 0), 2, 0.3, 0, 0.3, 0);
            }
            case "neve" -> w.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 2.4, 0), 3, 0.5, 0.1, 0.5, 0.01);
            case "raios" -> w.spawnParticle(Particle.ELECTRIC_SPARK, l.clone().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0.05);
            case "bolhas" -> w.spawnParticle(Particle.BUBBLE_POP, l.clone().add(0, 0.5, 0), 3, 0.4, 0.4, 0.4, 0.02);
            case "cerejeira" -> w.spawnParticle(Particle.CHERRY_LEAVES, l.clone().add(0, 2.4, 0), 2, 0.6, 0.1, 0.6, 0);
            case "dinheiro" -> { if (giro % 2 == 0) w.spawnParticle(Particle.DUST, l.clone().add(0, 2.4, 0), 4, 0.5, 0.1, 0.5, 0, new Particle.DustOptions(Color.fromRGB(255, 205, 40), 1.1f)); }
            case "totem" -> { if (giro % 2 == 0) w.spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 2, 0.4, 0.6, 0.4, 0.05); }
            case "fumaca" -> w.spawnParticle(Particle.SMOKE, l.clone().add(0, 0.2, 0), 3, 0.25, 0.05, 0.25, 0.01);
            case "ender" -> w.spawnParticle(Particle.PORTAL, l.clone().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.3);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ loja (bau)

    static final class Tela implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    void abrir(Player p) {
        Tela h = new Tela();
        Inventory inv = Bukkit.createInventory(h, 54, Extras.txt("§5§l✦ Loja de Cosméticos §8(" + saldo(p) + " coronas)"));
        h.inv = inv;
        Set<String> tem = comprados(p);
        String ec = escolha(p, kEscolhaC), ee = escolha(p, kEscolhaE);
        ItemStack vidro = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta vm = vidro.getItemMeta();
        vm.displayName(Extras.txt(" "));
        vidro.setItemMeta(vm);
        for (int i = 0; i < 54; i++) inv.setItem(i, vidro);
        for (int i = 0; i < CHAPEUS.size() && i < 18; i++) {
            Chapeu c = CHAPEUS.get(i);
            inv.setItem(i, botao(c.item() == Material.CAKE ? Material.CAKE : c.item(), "§d" + c.nome(), "chapéu", c.desc(), c.preco(), tem.contains("c:" + c.id()), c.id().equals(ec)));
        }
        for (int i = 0; i < EFEITOS.size() && i < 18; i++) {
            Efeito e = EFEITOS.get(i);
            inv.setItem(27 + i, botao(e.icone(), "§b" + e.nome(), "efeito", e.desc(), e.preco(), tem.contains("e:" + e.id()), e.id().equals(ee)));
        }
        inv.setItem(45, simples(Material.BARRIER, "§cTirar chapéu", "§7Fica sem chapéu."));
        inv.setItem(46, simples(Material.GLASS_BOTTLE, "§cTirar efeito", "§7Fica sem efeito."));
        inv.setItem(49, simples(Material.NETHER_STAR, "§6§lComo funciona", "§7Compre com §6coronas§7 (ganha jogando).",
                "§7Os cosméticos aparecem nos §flobbies§7 e em", "§ftodos os minigames§7 (até os de terror).", "§7No Survival, Masmorras e Prisão eles somem."));
        inv.setItem(53, simples(Material.SUNFLOWER, "§6Seu saldo: §e" + saldo(p) + " coronas", "§7Ganhe mais nas masmorras, minigames,", "§7eventos, /roleta e /conquistas."));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.3f);
    }

    private ItemStack botao(Material mat, String nome, String tipo, String desc, int preco, boolean tem, boolean usando) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt(nome + (usando ? " §a§l(USANDO)" : "")));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Extras.txt("§8" + tipo));
        lore.add(Extras.txt("§7" + desc));
        lore.add(Extras.txt(" "));
        if (usando) lore.add(Extras.txt("§aEm uso. §7Clique para tirar."));
        else if (tem) lore.add(Extras.txt("§aJá é seu! §eClique para usar."));
        else { lore.add(Extras.txt("§7Preço: §6" + preco + " coronas")); lore.add(Extras.txt("§eClique 2x para comprar")); }
        m.lore(lore);
        if (usando) m.setEnchantmentGlintOverride(true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack simples(Material mat, String nome, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt(nome));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Extras.txt(s));
        m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void cliqueLoja(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Tela)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getRawSlot();
        if (s == 45) { p.getPersistentDataContainer().remove(kEscolhaC); tirarChapeu(p); p.sendMessage(Extras.txt("§d✦ Chapéu tirado.")); reabrir(p); return; }
        if (s == 46) { p.getPersistentDataContainer().remove(kEscolhaE); p.sendMessage(Extras.txt("§d✦ Efeito tirado.")); reabrir(p); return; }
        String tipo = null, id = null, nome = null;
        int preco = 0;
        if (s < CHAPEUS.size()) { Chapeu c = CHAPEUS.get(s); tipo = "c"; id = c.id(); nome = c.nome(); preco = c.preco(); }
        else if (s >= 27 && s - 27 < EFEITOS.size()) { Efeito f = EFEITOS.get(s - 27); tipo = "e"; id = f.id(); nome = f.nome(); preco = f.preco(); }
        if (tipo == null) return;
        NamespacedKey kEsc = tipo.equals("c") ? kEscolhaC : kEscolhaE;
        Set<String> tem = comprados(p);
        if (tem.contains(tipo + ":" + id)) {
            if (id.equals(escolha(p, kEsc))) { p.getPersistentDataContainer().remove(kEsc); if (tipo.equals("c")) tirarChapeu(p); }
            else {
                p.getPersistentDataContainer().set(kEsc, PersistentDataType.STRING, id);
                p.sendMessage(Extras.txt("§d✦ Usando §f" + nome + "§d. " + (permitido(p) ? "" : "§7(aparece nos lobbies e minigames)")));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
            }
            reabrir(p);
            return;
        }
        long agora = System.currentTimeMillis();
        String chave = tipo + ":" + id;
        if (!chave.equals(confirmaId.get(p.getUniqueId())) || agora - confirma.getOrDefault(p.getUniqueId(), 0L) > 5000) {
            confirma.put(p.getUniqueId(), agora);
            confirmaId.put(p.getUniqueId(), chave);
            p.sendMessage(Extras.txt("§e✦ Clique de novo em §f" + nome + " §epara comprar por §6" + preco + " coronas§e."));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }
        confirmaId.remove(p.getUniqueId());
        if (saldo(p) < preco) {
            p.sendMessage(Extras.txt("§c✦ Faltam §6" + (preco - saldo(p)) + " coronas §cpara comprar " + nome + "."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas tirar " + p.getName() + " " + preco);
        tem.add(chave);
        salvarComprados(p, tem);
        p.getPersistentDataContainer().set(kEsc, PersistentDataType.STRING, id);
        p.sendMessage(Extras.txt("§a§l✦ Comprado: §f" + nome + "§a! §7Já está usando (nos lobbies e minigames)."));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        if (preco >= 2000) Bukkit.broadcast(Extras.txt("§d✦ §f" + p.getName() + " §7comprou §d" + nome + " §7na §5Loja de Cosméticos§7! §e/cosmeticos"));
        Bukkit.getScheduler().runTask(pl, () -> abrir(p));
    }

    private void reabrir(Player p) { Bukkit.getScheduler().runTask(pl, () -> abrir(p)); }

    // ------------------------------------------------------------------ mercador no spawn

    private Location lugarMercador(World w) {
        return new Location(w, SpawnPrincipal.CX + 0.5 + pl.getConfig().getDouble("cosmeticos.dx", 7), SpawnPrincipal.PISO + 1,
                SpawnPrincipal.CZ + 0.5 + pl.getConfig().getDouble("cosmeticos.dz", 0));
    }

    private void mercador() {
        World w = Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world"));
        if (w == null || !pl.getConfig().getBoolean("cosmeticos.mercador", true)) return;
        Location l = lugarMercador(w);
        if (!l.isChunkLoaded()) return;
        if (npc != null && npc.isValid() && rotulo != null && rotulo.isValid() && toque != null && toque.isValid()) return;
        for (Entity e : w.getNearbyEntities(l, 3, 4, 3)) if (e.getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE)) e.remove();
        l.setYaw(pl.getConfig().getInt("cosmeticos.yaw", 90));
        npc = w.spawn(l, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(false);
            v.setProfession(Villager.Profession.CLERIC);
            v.setVillagerType(Villager.Type.SWAMP);
            v.setCollidable(false);
            v.getPersistentDataContainer().set(kNpc, PersistentDataType.BYTE, (byte) 1);
        });
        rotulo = w.spawn(l.clone().add(0, 2.45, 0), TextDisplay.class, t -> {
            t.text(Extras.txt("§5§l✦ LOJA DE COSMÉTICOS ✦\n§fChapéus e efeitos para os\n§flobbies e minigames\n§eClique no mercador §7ou §e/cosmeticos"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBackgroundColor(Color.fromARGB(140, 30, 0, 50));
            t.setPersistent(false);
            t.getPersistentDataContainer().set(kNpc, PersistentDataType.BYTE, (byte) 1);
        });
        toque = w.spawn(l, Interaction.class, i -> {
            i.setInteractionWidth(1.2f);
            i.setInteractionHeight(2.2f);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(kNpc, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falarMercador(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        abrir(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void baterMercador(EntityDamageEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent d && d.getDamager() instanceof Player p) abrir(p);
    }

    @EventHandler
    public void carregou(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities())
            if (en.getPersistentDataContainer().has(kNpc, PersistentDataType.BYTE) && en != npc && en != rotulo && en != toque) en.remove();
    }

    void desligar() {
        for (Entity en : new Entity[] {npc, rotulo, toque}) if (en != null && en.isValid()) en.remove();
        for (Player p : Bukkit.getOnlinePlayers()) tirarChapeu(p);
    }

    static Set<String> mundos() { return new HashSet<>(MUNDOS_MINIGAMES); }
}
