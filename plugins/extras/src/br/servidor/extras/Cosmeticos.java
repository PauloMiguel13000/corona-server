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

    /**
     * 14/11 (teste no Bedrock): o Bedrock so desenha na cabeca CAPACETES e CABECAS de mob. Bloco (ouro, TNT, vidro,
     * bandeira...) fica invisivel la. Entao todo chapeu e cabeca de mob ou capacete (couro colorido, acabamento e
     * brilho), que aparece igual no Java e no Bedrock. O capacete cosmetico nao da armadura e nao gasta.
     * 14/11 (Paulo: "faça muitos cosmeticos fodas"): 4 categorias — Chapeus, Auras, Rastros (atras de quem anda) e
     * Especiais (asas, aureola, coroa, orbita, espiral, tornado...). Cada categoria tem a sua escolha.
     */
    record Chapeu(String id, String nome, Material item, int preco, String desc, Color cor, String trim, boolean brilho) {
        Chapeu(String id, String nome, Material item, int preco, String desc) { this(id, nome, item, preco, desc, null, null, false); }
    }
    record Efeito(String id, String nome, Material icone, int preco, String desc) {}

    static Color rgb(int c) { return Color.fromRGB(c); }

    static final List<Chapeu> CHAPEUS = List.of(
            new Chapeu("creeper", "Cabeça de Creeper", Material.CREEPER_HEAD, 600, "Tsssss..."),
            new Chapeu("zumbi", "Cabeça de Zumbi", Material.ZOMBIE_HEAD, 500, "Cérebros!"),
            new Chapeu("esqueleto", "Crânio de Esqueleto", Material.SKELETON_SKULL, 500, "Só osso."),
            new Chapeu("piglin", "Cabeça de Piglin", Material.PIGLIN_HEAD, 700, "Do Nether, com estilo."),
            new Chapeu("wither", "Crânio do Wither", Material.WITHER_SKELETON_SKULL, 1200, "Sombrio e raro."),
            new Chapeu("dragao", "Cabeça de Dragão", Material.DRAGON_HEAD, 3000, "A mais lendária de todas."),
            new Chapeu("capuz_vermelho", "Capuz Vermelho", Material.LEATHER_HELMET, 400, "Clássico e chamativo.", rgb(0xC62828), null, false),
            new Chapeu("capuz_azul", "Capuz Azul", Material.LEATHER_HELMET, 400, "Azul Corona.", rgb(0x1E63D6), null, false),
            new Chapeu("capuz_rosa", "Capuz Rosa", Material.LEATHER_HELMET, 400, "Fofo e perigoso.", rgb(0xFF6FB5), null, false),
            new Chapeu("capuz_laranja", "Capuz de Halloween", Material.LEATHER_HELMET, 500, "Doces ou travessuras?", rgb(0xF57C00), "netherite:snout", false),
            new Chapeu("capuz_obra", "Capacete de Obra", Material.LEATHER_HELMET, 500, "Segurança em primeiro lugar.", rgb(0xFFD400), "iron:host", false),
            new Chapeu("capuz_verde", "Capuz do Arqueiro", Material.LEATHER_HELMET, 500, "Estilo Robin Hood.", rgb(0x2E9E3E), "copper:wild", false),
            new Chapeu("capuz_oceano", "Capuz do Oceano", Material.LEATHER_HELMET, 600, "Direto do fundo do mar.", rgb(0x00A6B4), "diamond:tide", false),
            new Chapeu("ninja", "Capuz Ninja", Material.LEATHER_HELMET, 700, "Ninguém te vê chegando.", rgb(0x151515), "netherite:silence", false),
            new Chapeu("mago", "Capuz do Mago", Material.LEATHER_HELMET, 900, "Abracadabra!", rgb(0x6A1B9A), "amethyst:wayfinder", true),
            new Chapeu("fantasma", "Capuz Fantasma", Material.LEATHER_HELMET, 800, "Perfeito pro terror. Buuu!", rgb(0xF2F2F2), "quartz:vex", true),
            new Chapeu("arcoiris", "Capuz Arco-íris", Material.LEATHER_HELMET, 2500, "Muda de cor sem parar!", Color.RED, null, true),
            new Chapeu("tartaruga", "Casco de Tartaruga", Material.TURTLE_HELMET, 700, "Lento, mas estiloso."),
            new Chapeu("cavaleiro", "Elmo de Cavaleiro", Material.CHAINMAIL_HELMET, 900, "Pronto pra batalha.", null, "iron:sentry", false),
            new Chapeu("viking", "Elmo Viking", Material.IRON_HELMET, 1000, "Para o Valhalla!", null, "copper:dune", false),
            new Chapeu("guardiao", "Elmo do Guardião", Material.IRON_HELMET, 1200, "Protetor do servidor.", null, "lapis:coast", true),
            new Chapeu("coroa", "Coroa de Ouro", Material.GOLDEN_HELMET, 2000, "Para quem manda no servidor.", null, "diamond:eye", true),
            new Chapeu("coroa_rubi", "Coroa de Rubi", Material.GOLDEN_HELMET, 2200, "Vermelha como o Corona.", null, "redstone:wayfinder", true),
            new Chapeu("coroa_esmeralda", "Coroa de Esmeralda", Material.GOLDEN_HELMET, 2200, "Rica e verdinha.", null, "emerald:spire", true),
            new Chapeu("diamante", "Elmo de Diamante", Material.DIAMOND_HELMET, 2500, "Brilho máximo.", null, "gold:spire", true),
            new Chapeu("elmo_gelo", "Elmo de Gelo Eterno", Material.DIAMOND_HELMET, 2600, "Frio que dá medo.", null, "quartz:flow", true),
            new Chapeu("rei_sombrio", "Elmo do Rei Sombrio", Material.NETHERITE_HELMET, 3000, "O mais raro da loja.", null, "amethyst:silence", true),
            new Chapeu("infernal", "Elmo Infernal", Material.NETHERITE_HELMET, 3200, "Forjado no fogo do Nether.", null, "redstone:rib", true),
            new Chapeu("lenda", "Elmo da Lenda", Material.NETHERITE_HELMET, 5000, "Só os maiores do servidor.", null, "gold:bolt", true));

    /** Chapeus que existiram na 1a versao da loja (nao aparecem no Bedrock): quem comprou recebe as coronas de volta. */
    static final Map<String, Integer> REMOVIDOS = Map.of("abobora", 400, "astronauta", 800, "tnt", 900, "bolo", 700, "melancia", 400,
            "slime", 600, "farol", 2200, "bandeira", 1000, "gelo", 500, "mel", 600);

    /** AURAS: particulas em volta do jogador. */
    static final List<Efeito> EFEITOS = List.of(
            new Efeito("coracoes", "Corações", Material.RED_DYE, 600, "Amor espalhado por onde passa."),
            new Efeito("notas", "Notas Musicais", Material.NOTE_BLOCK, 600, "Seu próprio som."),
            new Efeito("fogo", "Chamas", Material.BLAZE_POWDER, 900, "Pegando fogo (sem queimar)."),
            new Efeito("almas", "Fogo das Almas", Material.SOUL_LANTERN, 1200, "Azul e assustador."),
            new Efeito("magia", "Magia", Material.ENCHANTED_BOOK, 900, "Runas de encantamento."),
            new Efeito("arcoiris", "Arco-íris", Material.PRISMARINE_CRYSTALS, 1800, "Todas as cores em volta de você."),
            new Efeito("estrelas", "Estrelas", Material.END_ROD, 1300, "Brilhinhos no ar."),
            new Efeito("nuvem", "Nuvem de Chuva", Material.WATER_BUCKET, 1000, "Sua nuvem particular."),
            new Efeito("neve", "Neve", Material.SNOWBALL, 700, "Inverno o ano todo."),
            new Efeito("raios", "Faíscas", Material.LIGHTNING_ROD, 1100, "Energia elétrica pura."),
            new Efeito("bolhas", "Bolhas", Material.HEART_OF_THE_SEA, 600, "Bolhinhas subindo."),
            new Efeito("cerejeira", "Pétalas de Cerejeira", Material.CHERRY_LEAVES, 1000, "Pétalas rosas caindo."),
            new Efeito("dinheiro", "Chuva de Ouro", Material.GOLD_INGOT, 2500, "Ostentação total."),
            new Efeito("totem", "Aura do Totem", Material.TOTEM_OF_UNDYING, 3000, "Raro: a aura mais bonita."),
            new Efeito("fumaca", "Fumaça", Material.CAMPFIRE, 500, "Misterioso."),
            new Efeito("ender", "Portal do Fim", Material.ENDER_EYE, 1400, "Partículas roxas do End."),
            new Efeito("vulcao", "Vulcão", Material.MAGMA_BLOCK, 1600, "Lava pulando em volta."),
            new Efeito("esporos", "Esporos do Nether", Material.CRIMSON_FUNGUS, 800, "Vermelho e azul flutuando."),
            new Efeito("brilho", "Brilho", Material.GLOW_INK_SAC, 900, "Brilha no escuro."),
            new Efeito("bruxa", "Feitiço da Bruxa", Material.CAULDRON, 1100, "Magia roxa e misteriosa."),
            new Efeito("sculk", "Almas do Sculk", Material.SCULK_CATALYST, 1500, "Do fundo das trevas."),
            new Efeito("esmeraldas", "Sorte", Material.EMERALD, 800, "Brilhos verdes da sorte."),
            new Efeito("fogos", "Fogos de Artifício", Material.FIREWORK_ROCKET, 2000, "Festa o tempo todo!"),
            new Efeito("raiva", "Nervosinho", Material.TNT, 500, "Bravo com todo mundo."));

    /** RASTROS: so aparecem quando o jogador anda, atras dele. */
    static final List<Efeito> RASTROS = List.of(
            new Efeito("fogo", "Rastro de Fogo", Material.FLINT_AND_STEEL, 800, "Deixa o chão pegando fogo."),
            new Efeito("almas", "Rastro das Almas", Material.SOUL_TORCH, 1000, "Chamas azuis nos seus passos."),
            new Efeito("coracoes", "Rastro de Amor", Material.PINK_DYE, 600, "Corações por onde passa."),
            new Efeito("notas", "Rastro Musical", Material.JUKEBOX, 600, "Cada passo, uma nota."),
            new Efeito("neve", "Pegadas de Neve", Material.POWDER_SNOW_BUCKET, 600, "Inverno nos pés."),
            new Efeito("petalas", "Caminho de Flores", Material.PINK_PETALS, 900, "Pétalas por onde anda."),
            new Efeito("arcoiris", "Rastro Arco-íris", Material.WHITE_DYE, 1800, "Um arco-íris atrás de você."),
            new Efeito("ouro", "Pó de Ouro", Material.GOLD_NUGGET, 1500, "Rico até no chão."),
            new Efeito("lava", "Pés de Lava", Material.LAVA_BUCKET, 1300, "Cuidado onde pisa."),
            new Efeito("estrelas", "Poeira de Estrelas", Material.NETHER_STAR, 2200, "Brilho de cometa."),
            new Efeito("esmeralda", "Rastro da Sorte", Material.EMERALD, 700, "Verdinho e brilhante."),
            new Efeito("sangue", "Rastro de Sangue", Material.REDSTONE, 1200, "Pro terror. Assustador!"),
            new Efeito("sculk", "Rastro do Sculk", Material.SCULK, 1400, "As almas te seguem."),
            new Efeito("gosma", "Gosma", Material.SLIME_BALL, 500, "Gosmento por onde passa."),
            new Efeito("magia", "Rastro Mágico", Material.AMETHYST_SHARD, 1000, "Faíscas roxas."),
            new Efeito("nuvem", "Andar nas Nuvens", Material.FEATHER, 900, "Fofinho e branco."));

    /** ESPECIAIS: formas desenhadas com particulas (asas, aureola, coroa, orbita...). */
    static final List<Efeito> ESPECIAIS = List.of(
            new Efeito("asas_anjo", "Asas de Anjo", Material.FEATHER, 3500, "Asas brancas com borda dourada."),
            new Efeito("asas_demonio", "Asas de Demônio", Material.CRIMSON_STEM, 3500, "Vermelhas e pretas. Pro terror!"),
            new Efeito("asas_fogo", "Asas de Fogo", Material.BLAZE_ROD, 4000, "Asas pegando fogo."),
            new Efeito("asas_borboleta", "Asas de Borboleta", Material.ALLIUM, 3000, "Rosa e roxo, batendo."),
            new Efeito("asas_gelo", "Asas de Gelo", Material.BLUE_ICE, 3500, "Cristais de gelo."),
            new Efeito("asas_ender", "Asas do Fim", Material.DRAGON_BREATH, 4500, "Asas do dragão do Fim."),
            new Efeito("asas_morcego", "Asas de Morcego", Material.BLACK_DYE, 3000, "Vampiro da noite."),
            new Efeito("aureola", "Auréola", Material.GOLD_INGOT, 1500, "Um anjinho... será?"),
            new Efeito("halo_fogo", "Coroa de Fogo", Material.FIRE_CHARGE, 1800, "Chamas em cima da cabeça."),
            new Efeito("coroa_gelo", "Coroa de Gelo", Material.PACKED_ICE, 1800, "Coroa de cristais."),
            new Efeito("orbita", "Órbita", Material.ENDER_PEARL, 2000, "Luzes girando em volta."),
            new Efeito("espiral", "Espiral Mágica", Material.AMETHYST_CLUSTER, 2500, "Dupla hélice subindo."),
            new Efeito("tornado", "Tornado", Material.WIND_CHARGE, 2800, "Um redemoinho em volta de você."),
            new Efeito("olhos", "Olhos na Escuridão", Material.SPIDER_EYE, 1500, "Algo te observa... (terror)"));

    /** Categorias da loja. */
    record Cat(String id, String nome, String cor, Material icone, String desc) {}
    static final List<Cat> CATS = List.of(
            new Cat("c", "Chapéus", "§d", Material.DRAGON_HEAD, "Cabeças, capuzes, coroas e elmos."),
            new Cat("e", "Auras", "§b", Material.BLAZE_POWDER, "Partículas em volta de você."),
            new Cat("r", "Rastros", "§a", Material.GOLDEN_BOOTS, "Aparecem atrás de você quando anda."),
            new Cat("s", "Especiais", "§6", Material.ELYTRA, "Asas, auréola, coroa, órbita, tornado..."));

    static final Set<String> MUNDOS_MINIGAMES = Set.of(LobbyMinigames.MUNDO, LobbyTerror.MUNDO, "hotel", "slender", "vovo", "backrooms",
            "fabrica", "naochore", "defesa", "arena", "turno_noite", "porteiro");

    private final Extras pl;
    private final NamespacedKey kChapeuItem, kDono, kNpc;
    private final Map<String, NamespacedKey> kEscolha = new HashMap<>();
    private final Map<UUID, Long> confirma = new HashMap<>();
    private final Map<UUID, String> confirmaId = new HashMap<>();
    private final Map<UUID, Location> ultimo = new HashMap<>();
    private Entity npc, rotulo, toque;
    private int giro;

    Cosmeticos(Extras pl) {
        this.pl = pl;
        kChapeuItem = new NamespacedKey(pl, "cos_chapeu_item");
        kDono = new NamespacedKey(pl, "cos_comprados");
        kEscolha.put("c", new NamespacedKey(pl, "cos_chapeu"));
        kEscolha.put("e", new NamespacedKey(pl, "cos_efeito"));
        kEscolha.put("r", new NamespacedKey(pl, "cos_rastro"));
        kEscolha.put("s", new NamespacedKey(pl, "cos_especial"));
        kNpc = new NamespacedKey(pl, "cos_npc");
        for (Cat c : CATS) prova.put(c.id(), new HashMap<>());
        Bukkit.getScheduler().runTaskTimer(pl, this::conferir, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(pl, this::particulas, 60L, 4L);
        Bukkit.getScheduler().runTaskTimer(pl, this::rastros, 61L, 2L);
        Bukkit.getScheduler().runTaskTimer(pl, this::mercador, 100L, 200L);
    }

    // ------------------------------------------------------------------ onde vale

    static boolean noSpawn(Location l) {
        if (l == null || l.getWorld() == null || !l.getWorld().getName().equals("world")) return false;
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

    /** Prova (admin ou "experimentar" de 10 s na loja): por categoria, nao salva nada. */
    private final Map<String, Map<UUID, String>> prova = new HashMap<>();

    private String escolha(Player p, String cat) {
        String t = prova.get(cat).get(p.getUniqueId());
        if (t != null) return t.isEmpty() ? null : t;
        return p.getPersistentDataContainer().get(kEscolha.get(cat), PersistentDataType.STRING);
    }

    String provar(Player p, String id) {
        if (id == null || id.equalsIgnoreCase("limpar")) {
            for (Map<UUID, String> m : prova.values()) m.remove(p.getUniqueId());
            tirarChapeu(p);
            return "prova limpa";
        }
        String cat = null;
        if (id.length() > 2 && id.charAt(1) == ':') { cat = id.substring(0, 1); id = id.substring(2); }
        for (Cat c : CATS) {
            if (cat != null && !cat.equals(c.id())) continue;
            if (existe(c.id(), id)) { prova.get(c.id()).put(p.getUniqueId(), id); return "provando " + c.nome().toLowerCase() + " " + id; }
        }
        return "id desconhecido";
    }

    private long saldo(Player p) {
        Long v = p.getPersistentDataContainer().get(new NamespacedKey("masmorras", "coronas"), PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    private static Chapeu chapeu(String id) { for (Chapeu c : CHAPEUS) if (c.id().equals(id)) return c; return null; }

    static List<Efeito> lista(String cat) { return cat.equals("e") ? EFEITOS : cat.equals("r") ? RASTROS : ESPECIAIS; }

    private static Efeito efeito(String cat, String id) {
        if (id == null) return null;
        for (Efeito e : lista(cat)) if (e.id().equals(id)) return e;
        return null;
    }

    private static boolean existe(String cat, String id) { return cat.equals("c") ? chapeu(id) != null : efeito(cat, id) != null; }

    private static int tamanho(String cat) { return cat.equals("c") ? CHAPEUS.size() : lista(cat).size(); }

    private static String nomeDe(String cat, String id) {
        if (cat.equals("c")) { Chapeu c = chapeu(id); return c == null ? id : c.nome(); }
        Efeito e = efeito(cat, id);
        return e == null ? id : e.nome();
    }

    // ------------------------------------------------------------------ chapeu na cabeca

    private boolean ehChapeu(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kChapeuItem, PersistentDataType.STRING);
    }

    private ItemStack itemChapeu(Chapeu c) {
        ItemStack it = icone(c);
        ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt("§d✦ " + c.nome() + " §7(cosmético)"));
        m.lore(List.of(Extras.txt("§7Só aparece nos lobbies e minigames."), Extras.txt("§7Troque em §e/cosmeticos")));
        m.getPersistentDataContainer().set(kChapeuItem, PersistentDataType.STRING, c.id());
        it.setItemMeta(m);
        return it;
    }

    /** O item do chapeu (cor, acabamento, brilho), sem armadura e sem gastar. Serve tambem de icone na loja. */
    static ItemStack icone(Chapeu c) {
        ItemStack it = new ItemStack(c.item());
        ItemMeta m = it.getItemMeta();
        if (m instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm && c.cor() != null) lm.setColor(c.cor());
        if (m instanceof org.bukkit.inventory.meta.ArmorMeta am && c.trim() != null) {
            String[] t = c.trim().split(":");
            var mat = org.bukkit.Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(t[0]));
            var pat = org.bukkit.Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(t[1]));
            if (mat != null && pat != null) am.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(mat, pat));
        }
        if (c.brilho()) m.setEnchantmentGlintOverride(true);
        if (c.item().name().endsWith("_HELMET")) {
            m.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());   // capacete cosmetico: zero armadura
            m.setUnbreakable(true);
        }
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
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
            Chapeu c = chapeu(escolha(p, "c"));
            ItemStack cab = p.getInventory().getHelmet();
            if (!ok || c == null) { if (ehChapeu(cab)) tirarChapeu(p); continue; }
            if (ehChapeu(cab)) {
                if (!c.id().equals(cab.getItemMeta().getPersistentDataContainer().get(kChapeuItem, PersistentDataType.STRING))) p.getInventory().setHelmet(itemChapeu(c));
                else if (c.id().equals("arcoiris") && cab.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm) {
                    java.awt.Color h = java.awt.Color.getHSBColor((giro % 90) / 90f, 0.85f, 1f);
                    lm.setColor(Color.fromRGB(h.getRed(), h.getGreen(), h.getBlue()));
                    cab.setItemMeta(lm);
                    p.getInventory().setHelmet(cab);
                }
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
    public void saiu(PlayerQuitEvent e) {
        tirarChapeu(e.getPlayer());
        for (Map<UUID, String> m : prova.values()) m.remove(e.getPlayer().getUniqueId());
        ultimo.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        tirarChapeu(p);
        Set<String> tem = comprados(p);
        int volta = 0;
        for (var r : REMOVIDOS.entrySet()) if (tem.remove("c:" + r.getKey())) volta += r.getValue();
        String esc = p.getPersistentDataContainer().get(kEscolha.get("c"), PersistentDataType.STRING);
        if (esc != null && chapeu(esc) == null) p.getPersistentDataContainer().remove(kEscolha.get("c"));
        if (volta > 0) {
            salvarComprados(p, tem);
            int v = volta;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
                p.sendMessage(Extras.txt("§d✦ A Loja de Cosméticos trocou os chapéus de bloco por capacetes e cabeças (agora aparecem no celular e no console também). Você recebeu §6" + v + " coronas §dde volta. §e/cosmeticos"));
            }, 60L);
        }
    }

    // ------------------------------------------------------------------ particulas

    private boolean podeParticula(Player p) { return permitido(p) && !p.isInvisible() && !p.isDead(); }

    private void particulas() {
        giro++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!podeParticula(p)) continue;
            String a = escolha(p, "e");
            if (a != null) tocar(p, a);
            String s = escolha(p, "s");
            if (s != null) especial(p, s);
        }
    }

    private static Particle.DustOptions po(int rgb, float t) { return new Particle.DustOptions(Color.fromRGB(rgb), t); }

    private void tocar(Player p, String id) {
        World w = p.getWorld();
        Location l = p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = giro * 0.45;
        Location roda = l.clone().add(Math.cos(ang) * 0.7, 0.2 + (giro % 20) * 0.09, Math.sin(ang) * 0.7);
        Location cabeca = l.clone().add(0, 2.3, 0);
        Location meio = l.clone().add(0, 1, 0);
        switch (id) {
            case "coracoes" -> { if (giro % 3 == 0) w.spawnParticle(Particle.HEART, cabeca, 1, 0.3, 0.1, 0.3, 0); }
            case "notas" -> { if (giro % 2 == 0) w.spawnParticle(Particle.NOTE, cabeca, 1, 0.4, 0.1, 0.4, r.nextDouble()); }
            case "fogo" -> w.spawnParticle(Particle.FLAME, roda, 2, 0.02, 0.02, 0.02, 0.005);
            case "almas" -> w.spawnParticle(Particle.SOUL_FIRE_FLAME, roda, 2, 0.02, 0.02, 0.02, 0.005);
            case "magia" -> w.spawnParticle(Particle.ENCHANT, l.clone().add(0, 1.2, 0), 8, 0.5, 0.6, 0.5, 0.6);
            case "arcoiris" -> {
                Color cor = Color.fromRGB(java.awt.Color.HSBtoRGB((giro % 40) / 40f, 0.9f, 1f) & 0xFFFFFF);
                w.spawnParticle(Particle.DUST, roda, 3, 0.1, 0.05, 0.1, 0, new Particle.DustOptions(cor, 1.3f));
            }
            case "estrelas" -> w.spawnParticle(Particle.END_ROD, meio, 1, 0.5, 0.6, 0.5, 0.01);
            case "nuvem" -> {
                w.spawnParticle(Particle.CLOUD, l.clone().add(0, 2.7, 0), 3, 0.35, 0.05, 0.35, 0);
                w.spawnParticle(Particle.FALLING_WATER, l.clone().add(0, 2.5, 0), 2, 0.3, 0, 0.3, 0);
            }
            case "neve" -> w.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 2.4, 0), 3, 0.5, 0.1, 0.5, 0.01);
            case "raios" -> w.spawnParticle(Particle.ELECTRIC_SPARK, meio, 4, 0.4, 0.6, 0.4, 0.05);
            case "bolhas" -> w.spawnParticle(Particle.BUBBLE_POP, l.clone().add(0, 0.5, 0), 3, 0.4, 0.4, 0.4, 0.02);
            case "cerejeira" -> w.spawnParticle(Particle.CHERRY_LEAVES, l.clone().add(0, 2.4, 0), 2, 0.6, 0.1, 0.6, 0);
            case "dinheiro" -> { if (giro % 2 == 0) w.spawnParticle(Particle.DUST, l.clone().add(0, 2.4, 0), 4, 0.5, 0.1, 0.5, 0, po(0xFFCD28, 1.1f)); }
            case "totem" -> { if (giro % 2 == 0) w.spawnParticle(Particle.TOTEM_OF_UNDYING, meio, 2, 0.4, 0.6, 0.4, 0.05); }
            case "fumaca" -> w.spawnParticle(Particle.SMOKE, l.clone().add(0, 0.2, 0), 3, 0.25, 0.05, 0.25, 0.01);
            case "ender" -> w.spawnParticle(Particle.PORTAL, meio, 6, 0.4, 0.6, 0.4, 0.3);
            case "vulcao" -> {
                if (giro % 3 == 0) w.spawnParticle(Particle.LAVA, l.clone().add(0, 0.3, 0), 1, 0.3, 0.1, 0.3, 0);
                w.spawnParticle(Particle.SMOKE, l.clone().add(0, 0.2, 0), 2, 0.3, 0.05, 0.3, 0.01);
            }
            case "esporos" -> {
                w.spawnParticle(Particle.CRIMSON_SPORE, meio, 3, 0.6, 0.7, 0.6, 0);
                w.spawnParticle(Particle.WARPED_SPORE, meio, 3, 0.6, 0.7, 0.6, 0);
            }
            case "brilho" -> w.spawnParticle(Particle.GLOW, meio, 2, 0.5, 0.6, 0.5, 0);
            case "bruxa" -> w.spawnParticle(Particle.WITCH, meio, 3, 0.4, 0.6, 0.4, 0);
            case "sculk" -> { if (giro % 2 == 0) w.spawnParticle(Particle.SCULK_SOUL, l.clone().add(0, 0.4, 0), 1, 0.4, 0.2, 0.4, 0.02); }
            case "esmeraldas" -> w.spawnParticle(Particle.HAPPY_VILLAGER, meio, 2, 0.5, 0.6, 0.5, 0);
            case "fogos" -> { if (giro % 2 == 0) w.spawnParticle(Particle.FIREWORK, l.clone().add(0, 2.2, 0), 3, 0.2, 0.1, 0.2, 0.08); }
            case "raiva" -> { if (giro % 5 == 0) w.spawnParticle(Particle.ANGRY_VILLAGER, cabeca, 1, 0.3, 0.1, 0.3, 0); }
            default -> {}
        }
    }

    /** Rastros: so quando o jogador realmente andou desde a ultima vez. */
    private void rastros() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String id = escolha(p, "r");
            Location l = p.getLocation();
            Location ant = ultimo.put(p.getUniqueId(), l);
            if (id == null || ant == null || ant.getWorld() != l.getWorld() || !podeParticula(p)) continue;
            double dx = l.getX() - ant.getX(), dz = l.getZ() - ant.getZ();
            if (dx * dx + dz * dz < 0.01) continue;
            World w = p.getWorld();
            Location pe = l.clone().add(-dx * 1.5, 0.12, -dz * 1.5);
            switch (id) {
                case "fogo" -> w.spawnParticle(Particle.FLAME, pe, 2, 0.1, 0.02, 0.1, 0.005);
                case "almas" -> w.spawnParticle(Particle.SOUL_FIRE_FLAME, pe, 2, 0.1, 0.02, 0.1, 0.005);
                case "coracoes" -> { if (giro % 2 == 0) w.spawnParticle(Particle.HEART, pe.clone().add(0, 0.3, 0), 1, 0.1, 0, 0.1, 0); }
                case "notas" -> { if (giro % 2 == 0) w.spawnParticle(Particle.NOTE, pe.clone().add(0, 0.3, 0), 1, 0.1, 0, 0.1, ThreadLocalRandom.current().nextDouble()); }
                case "neve" -> w.spawnParticle(Particle.SNOWFLAKE, pe, 2, 0.15, 0.02, 0.15, 0.005);
                case "petalas" -> w.spawnParticle(Particle.CHERRY_LEAVES, pe.clone().add(0, 0.4, 0), 2, 0.2, 0.05, 0.2, 0);
                case "arcoiris" -> {
                    Color cor = Color.fromRGB(java.awt.Color.HSBtoRGB(((giro * 2 + p.getTicksLived() / 2) % 60) / 60f, 0.9f, 1f) & 0xFFFFFF);
                    w.spawnParticle(Particle.DUST, pe, 4, 0.12, 0.02, 0.12, 0, new Particle.DustOptions(cor, 1.4f));
                }
                case "ouro" -> w.spawnParticle(Particle.DUST, pe, 3, 0.15, 0.02, 0.15, 0, po(0xFFCD28, 1.0f));
                case "lava" -> { if (p.getTicksLived() % 4 == 0) w.spawnParticle(Particle.LAVA, pe, 1, 0.1, 0, 0.1, 0); w.spawnParticle(Particle.DUST, pe, 2, 0.12, 0.01, 0.12, 0, po(0xFF5A00, 1.2f)); }
                case "estrelas" -> w.spawnParticle(Particle.FIREWORK, pe.clone().add(0, 0.2, 0), 2, 0.1, 0.05, 0.1, 0.01);
                case "esmeralda" -> w.spawnParticle(Particle.HAPPY_VILLAGER, pe.clone().add(0, 0.2, 0), 2, 0.15, 0.05, 0.15, 0);
                case "sangue" -> w.spawnParticle(Particle.DUST, pe, 4, 0.15, 0.01, 0.15, 0, po(0x8A0303, 1.5f));
                case "sculk" -> { if (p.getTicksLived() % 3 == 0) w.spawnParticle(Particle.SCULK_SOUL, pe, 1, 0.1, 0.02, 0.1, 0.01); }
                case "gosma" -> w.spawnParticle(Particle.ITEM_SLIME, pe, 3, 0.15, 0.02, 0.15, 0);
                case "magia" -> w.spawnParticle(Particle.WITCH, pe.clone().add(0, 0.2, 0), 2, 0.12, 0.05, 0.12, 0);
                case "nuvem" -> w.spawnParticle(Particle.CLOUD, pe, 2, 0.15, 0.02, 0.15, 0.005);
                default -> {}
            }
        }
    }

    // As formas (asas, aureola...) sao desenhadas ponto a ponto. No Bedrock cada ponto de poeira e bem maior e dura
    // mais, entao para quem joga no Bedrock vai uma versao com menos pontos (senao vira um borrao); no Java vai completa.
    private final List<Player> verJava = new ArrayList<>(), verBed = new ArrayList<>();
    private static Boolean temFloodgate;

    static boolean bedrock(Player v) {
        if (v.getName().startsWith(".")) return true;
        try {
            if (temFloodgate == null) temFloodgate = Bukkit.getPluginManager().getPlugin("floodgate") != null;
            return temFloodgate && org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(v.getUniqueId());
        } catch (Throwable t) {
            temFloodgate = false;
            return false;
        }
    }

    private void quemVe(Player dono) {
        verJava.clear();
        verBed.clear();
        Location l = dono.getLocation();
        for (Player v : dono.getWorld().getPlayers())
            if (v.getLocation().distanceSquared(l) < 48 * 48) (bedrock(v) ? verBed : verJava).add(v);
    }

    private void ponto(double x, double y, double z, Particle.DustOptions o, boolean tambemBedrock) {
        for (Player v : verJava) v.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, o);
        if (tambemBedrock) for (Player v : verBed) v.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, o);
    }

    /** Especiais: formas desenhadas em volta do jogador. */
    private void especial(Player p, String id) {
        quemVe(p);
        boolean par = giro % 2 == 0;
        switch (id) {
            case "asas_anjo" -> asas(p, 0xFFFFFF, 0xFFD24A, null);
            case "asas_demonio" -> asas(p, 0x7A0000, 0x111111, Particle.SMOKE);
            case "asas_fogo" -> asas(p, 0xFF7A00, 0xFFE040, Particle.FLAME);
            case "asas_borboleta" -> asas(p, 0xFF6EC7, 0x8C3CFF, null);
            case "asas_gelo" -> asas(p, 0xAEE4FF, 0xFFFFFF, Particle.SNOWFLAKE);
            case "asas_ender" -> asas(p, 0x2A0040, 0xC850FF, Particle.PORTAL);
            case "asas_morcego" -> asas(p, 0x1A1A1A, 0x6A0000, null);
            case "aureola" -> anel(p, 2.2, 0.32, 14, 0, po(0xFFD700, 0.75f), null);
            case "halo_fogo" -> anel(p, 2.15, 0.34, 10, giro * 0.25, po(0xFF8C00, 0.7f), Particle.SMALL_FLAME);
            case "coroa_gelo" -> {
                anel(p, 2.05, 0.3, 12, 0, po(0xBFE9FF, 0.8f), null);
                Location c = p.getLocation().add(0, 2.05, 0);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    for (int k = 1; k <= 2; k++)
                        ponto(c.getX() + Math.cos(a) * 0.3, c.getY() + k * 0.09, c.getZ() + Math.sin(a) * 0.3, po(0xFFFFFF, 0.6f), par && k == 2 && i % 2 == 0);
                }
            }
            case "orbita" -> {
                Location c = p.getLocation().add(0, 1.0, 0);
                for (int i = 0; i < 3; i++) {
                    double a = giro * 0.55 + i * (Math.PI * 2 / 3);
                    double y = Math.sin(giro * 0.2 + i) * 0.35;
                    ponto(c.getX() + Math.cos(a) * 0.85, c.getY() + y, c.getZ() + Math.sin(a) * 0.85,
                            po(i == 0 ? 0x40E0FF : i == 1 ? 0xFF40C0 : 0xFFE040, 1.0f), true);
                }
            }
            case "espiral" -> {
                Location c = p.getLocation();
                for (int k = 0; k < 6; k++) {
                    double h = ((giro * 3 + k) % 24) / 24.0 * 2.2;
                    for (int s = 0; s < 2; s++) {
                        double a = h * 4.5 + s * Math.PI;
                        ponto(c.getX() + Math.cos(a) * 0.6, c.getY() + h, c.getZ() + Math.sin(a) * 0.6,
                                po(s == 0 ? 0x9B30FF : 0xFF5EDB, 0.9f), k % 2 == 0);
                    }
                }
            }
            case "tornado" -> {
                Location c = p.getLocation();
                for (int k = 0; k < 14; k++) {
                    double h = k * 0.17, raio = 0.15 + h * 0.38, a = giro * 0.7 + k * 0.75;
                    ponto(c.getX() + Math.cos(a) * raio, c.getY() + h, c.getZ() + Math.sin(a) * raio,
                            po(k % 3 == 0 ? 0xFFFFFF : 0xB8C4CC, 1.0f), true);
                }
            }
            case "olhos" -> {
                if (giro % 25 < 2) return;   // pisca
                double yaw = Math.toRadians(p.getLocation().getYaw());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
                Location b = p.getLocation().add(-fx * 0.9, 1.75, -fz * 0.9);
                for (int s = -1; s <= 1; s += 2)
                    ponto(b.getX() + rx * s * 0.13, b.getY(), b.getZ() + rz * s * 0.13, po(0xFF0000, 0.9f), true);
            }
            default -> {}
        }
    }

    private void anel(Player p, double altura, double raio, int pontos, double gira, Particle.DustOptions cor, Particle extra) {
        Location c = p.getLocation().add(0, altura, 0);
        World w = p.getWorld();
        boolean par = giro % 2 == 0;
        for (int i = 0; i < pontos; i++) {
            double a = gira + i * Math.PI * 2 / pontos;
            double x = c.getX() + Math.cos(a) * raio, z = c.getZ() + Math.sin(a) * raio;
            if (extra != null && i % 2 == 0) w.spawnParticle(extra, x, c.getY(), z, 1, 0, 0, 0, 0);
            else ponto(x, c.getY(), z, cor, par && i % 3 == 0);
        }
    }

    /** Asas nas costas, batendo devagar (miolo + borda). */
    private void asas(Player p, int miolo, int borda, Particle extra) {
        World w = p.getWorld();
        Location base = p.getLocation();
        double yaw = Math.toRadians(p.getLocation().getYaw());   // (o corpo do jogador do Bedrock nao vem certo; a cabeca sim)
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // frente
        double rx = -fz, rz = fx;                          // lado
        double bate = 0.5 + 0.5 * Math.sin(giro * 0.35);
        double sobe = p.isSneaking() ? -0.3 : 0;
        Particle.DustOptions dm = po(miolo, 0.75f), db = po(borda, 0.85f);
        boolean par = giro % 2 == 0;
        for (int lado = -1; lado <= 1; lado += 2) {
            int iu = 0;
            for (double u = 0.2; u <= 1.32; u += 0.12, iu++) {
                double topo = 1.45 + 0.9 * u - 0.45 * u * u, fundo = 1.35 - 0.25 * u + 0.5 * u * u;
                double tras = 0.3 + u * (0.12 + 0.38 * bate);
                double x = base.getX() - fx * tras + rx * lado * u, z = base.getZ() - fz * tras + rz * lado * u;
                int jv = 0;
                for (double v = fundo; v <= topo + 0.001; v += 0.13, jv++) {
                    boolean ehBorda = v + 0.13 > topo || u > 1.25;
                    ponto(x, base.getY() + v + sobe, z, ehBorda ? db : dm, par && iu % 2 == 0 && (ehBorda || jv % 2 == 0));
                }
                if (extra != null && giro % 3 == 0 && u > 1.0)
                    w.spawnParticle(extra, x, base.getY() + topo + sobe, z, 1, 0.02, 0.02, 0.02, 0.005);
            }
        }
    }

    // ------------------------------------------------------------------ loja (bau)

    static final class Tela implements InventoryHolder {
        Inventory inv;
        String pag = "menu";
        @Override public Inventory getInventory() { return inv; }
    }

    private ItemStack vidro() {
        ItemStack v = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta vm = v.getItemMeta();
        vm.displayName(Extras.txt(" "));
        v.setItemMeta(vm);
        return v;
    }

    private int quantosTem(Set<String> tem, String cat) {
        int n = 0;
        for (String s : tem) if (s.startsWith(cat + ":")) n++;
        return n;
    }

    void abrir(Player p) {
        Tela h = new Tela();
        Inventory inv = Bukkit.createInventory(h, 54, Extras.txt("§5§l✦ Loja de Cosméticos §8(" + saldo(p) + " coronas)"));
        h.inv = inv;
        Set<String> tem = comprados(p);
        ItemStack vd = vidro();
        for (int i = 0; i < 54; i++) inv.setItem(i, vd);
        int[] slots = {19, 21, 23, 25};
        for (int i = 0; i < CATS.size(); i++) {
            Cat c = CATS.get(i);
            String usando = escolha(p, c.id());
            inv.setItem(slots[i], simples(c.icone(), c.cor() + "§l" + c.nome(), "§7" + c.desc(), " ",
                    "§fVocê tem §e" + quantosTem(tem, c.id()) + "§f de §e" + tamanho(c.id()),
                    "§fUsando: " + (usando == null ? "§8nada" : "§a" + nomeDe(c.id(), usando)), " ", "§e▶ Clique para ver"));
        }
        inv.setItem(4, simples(Material.NETHER_STAR, "§5§l✦ LOJA DE COSMÉTICOS ✦", "§7Chapéus, auras, rastros e asas.",
                "§7Aparecem nos §flobbies§7 e em §ftodos os minigames§7", "§7(até os de terror). No Survival, Masmorras", "§7e Prisão eles somem sozinhos."));
        inv.setItem(40, simples(Material.BARRIER, "§cTirar tudo", "§7Fica sem nenhum cosmético."));
        inv.setItem(49, simples(Material.BOOK, "§6§lComo funciona", "§fClique 1x: §7experimenta por 10 segundos.",
                "§fClique 2x: §7compra com §6coronas§7.", "§7O que é seu: clique para usar ou tirar.", "§7Use um de cada categoria ao mesmo tempo!"));
        inv.setItem(53, simples(Material.SUNFLOWER, "§6Seu saldo: §e" + saldo(p) + " coronas", "§7Ganhe mais nas masmorras, minigames,", "§7eventos, /roleta e /conquistas."));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.3f);
    }

    void abrirCat(Player p, String cat) {
        Cat c = null;
        for (Cat x : CATS) if (x.id().equals(cat)) c = x;
        if (c == null) return;
        Tela h = new Tela();
        h.pag = cat;
        Inventory inv = Bukkit.createInventory(h, 54, Extras.txt(c.cor() + "§l✦ " + c.nome() + " §8(" + saldo(p) + " coronas)"));
        h.inv = inv;
        Set<String> tem = comprados(p);
        String usando = escolha(p, cat);
        ItemStack vd = vidro();
        for (int i = 45; i < 54; i++) inv.setItem(i, vd);
        if (cat.equals("c")) {
            for (int i = 0; i < CHAPEUS.size() && i < 45; i++) {
                Chapeu ch = CHAPEUS.get(i);
                inv.setItem(i, botao(icone(ch), "§d" + ch.nome(), "chapéu", ch.desc(), ch.preco(), tem.contains("c:" + ch.id()), ch.id().equals(usando)));
            }
        } else {
            List<Efeito> l = lista(cat);
            for (int i = 0; i < l.size() && i < 45; i++) {
                Efeito e = l.get(i);
                inv.setItem(i, botao(new ItemStack(e.icone()), c.cor() + e.nome(), c.nome().toLowerCase(), e.desc(), e.preco(), tem.contains(cat + ":" + e.id()), e.id().equals(usando)));
            }
        }
        inv.setItem(45, simples(Material.ARROW, "§e◀ Voltar", "§7Volta para as categorias."));
        inv.setItem(49, simples(Material.BARRIER, "§cTirar " + c.nome().toLowerCase(), "§7Fica sem " + c.nome().toLowerCase() + "."));
        inv.setItem(53, simples(Material.SUNFLOWER, "§6Seu saldo: §e" + saldo(p) + " coronas", "§7Ganhe mais nas masmorras, minigames,", "§7eventos, /roleta e /conquistas."));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    private ItemStack botao(ItemStack it, String nome, String tipo, String desc, int preco, boolean tem, boolean usando) {
        ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt(nome + (usando ? " §a§l(USANDO)" : "")));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Extras.txt("§8" + tipo));
        lore.add(Extras.txt("§7" + desc));
        lore.add(Extras.txt(" "));
        if (usando) lore.add(Extras.txt("§aEm uso. §7Clique para tirar."));
        else if (tem) lore.add(Extras.txt("§aJá é seu! §eClique para usar."));
        else {
            lore.add(Extras.txt("§7Preço: §6" + preco + " coronas"));
            lore.add(Extras.txt("§eClique 1x: experimenta (10 s)"));
            lore.add(Extras.txt("§eClique 2x: compra"));
        }
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
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    private void limparProvaDoJogador(Player p, String cat) { prova.get(cat).remove(p.getUniqueId()); }

    @EventHandler
    public void cliqueLoja(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Tela t)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getRawSlot();
        if (t.pag.equals("menu")) {
            int[] slots = {19, 21, 23, 25};
            for (int i = 0; i < slots.length; i++) if (s == slots[i]) { String cat = CATS.get(i).id(); Bukkit.getScheduler().runTask(pl, () -> abrirCat(p, cat)); return; }
            if (s == 40) {
                for (NamespacedKey k : kEscolha.values()) p.getPersistentDataContainer().remove(k);
                for (Cat c : CATS) limparProvaDoJogador(p, c.id());
                tirarChapeu(p);
                p.sendMessage(Extras.txt("§d✦ Todos os cosméticos tirados."));
                reabrir(p, "menu");
            }
            return;
        }
        String cat = t.pag;
        NamespacedKey kEsc = kEscolha.get(cat);
        if (s == 45) { reabrir(p, "menu"); return; }
        if (s == 49) {
            p.getPersistentDataContainer().remove(kEsc);
            limparProvaDoJogador(p, cat);
            if (cat.equals("c")) tirarChapeu(p);
            p.sendMessage(Extras.txt("§d✦ Pronto, sem " + nomeCat(cat).toLowerCase() + "."));
            reabrir(p, cat);
            return;
        }
        if (s >= tamanho(cat) || s >= 45) return;
        String id, nome;
        int preco;
        if (cat.equals("c")) { Chapeu c = CHAPEUS.get(s); id = c.id(); nome = c.nome(); preco = c.preco(); }
        else { Efeito f = lista(cat).get(s); id = f.id(); nome = f.nome(); preco = f.preco(); }
        Set<String> tem = comprados(p);
        String chave = cat + ":" + id;
        if (tem.contains(chave)) {
            limparProvaDoJogador(p, cat);
            if (id.equals(p.getPersistentDataContainer().get(kEsc, PersistentDataType.STRING))) {
                p.getPersistentDataContainer().remove(kEsc);
                if (cat.equals("c")) tirarChapeu(p);
            } else {
                p.getPersistentDataContainer().set(kEsc, PersistentDataType.STRING, id);
                p.sendMessage(Extras.txt("§d✦ Usando §f" + nome + "§d. " + (permitido(p) ? "" : "§7(aparece nos lobbies e minigames)")));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
            }
            reabrir(p, cat);
            return;
        }
        long agora = System.currentTimeMillis();
        if (!chave.equals(confirmaId.get(p.getUniqueId())) || agora - confirma.getOrDefault(p.getUniqueId(), 0L) > 10_000) {
            // 1o clique: experimenta por 10 segundos (sem salvar) e avisa que o 2o clique compra
            confirma.put(p.getUniqueId(), agora);
            confirmaId.put(p.getUniqueId(), chave);
            prova.get(cat).put(p.getUniqueId(), id);
            String fid = id;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (fid.equals(prova.get(cat).get(p.getUniqueId()))) prova.get(cat).remove(p.getUniqueId());
            }, 200L);
            p.sendMessage(Extras.txt("§e✦ Experimentando §f" + nome + " §epor 10 s" + (permitido(p) ? "" : " §7(só aparece nos lobbies e minigames)")
                    + "§e. Clique de novo para comprar por §6" + preco + " coronas§e."));
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
        limparProvaDoJogador(p, cat);
        p.getPersistentDataContainer().set(kEsc, PersistentDataType.STRING, id);
        p.sendMessage(Extras.txt("§a§l✦ Comprado: §f" + nome + "§a! §7Já está usando (nos lobbies e minigames)."));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        if (preco >= 2000) Bukkit.broadcast(Extras.txt("§d✦ §f" + p.getName() + " §7comprou §d" + nome + " §7na §5Loja de Cosméticos§7! §e/cosmeticos"));
        reabrir(p, cat);
    }

    private static String nomeCat(String cat) { for (Cat c : CATS) if (c.id().equals(cat)) return c.nome(); return cat; }

    private void reabrir(Player p, String pag) {
        Bukkit.getScheduler().runTask(pl, () -> { if (pag.equals("menu")) abrir(p); else abrirCat(p, pag); });
    }

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
            t.text(Extras.txt("§5§l✦ LOJA DE COSMÉTICOS ✦\n§fChapéus, auras, rastros e ASAS\n§fpara os lobbies e minigames\n§eClique no mercador §7ou §e/cosmeticos"));
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
