package br.servidor.defesa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

/**
 * Minigame Defesa da Torre: varios mapas, cada um com a sua partida. Compre e posicione torres,
 * defenda o Nucleo das ondas de monstros. Dificuldades Facil/Normal/Dificil (com vitoria) e Infinito.
 */
public final class DefesaTorre extends JavaPlugin implements Listener {

    // ---------------------------------------------------------------- menus

    private static final class LojaHolder implements InventoryHolder {
        final Partida partida;
        Inventory inv;

        LojaHolder(Partida partida) {
            this.partida = partida;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class TorreHolder implements InventoryHolder {
        final Partida partida;
        final UUID stand;
        Inventory inv;

        TorreHolder(Partida partida, UUID stand) {
            this.partida = partida;
            this.stand = stand;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class MapasHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class DificuldadeHolder implements InventoryHolder {
        final Partida partida;
        Inventory inv;

        DificuldadeHolder(Partida partida) {
            this.partida = partida;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    World mundo;
    NamespacedKey kMob;
    NamespacedKey kTorre;
    NamespacedKey kEssencia;
    NamespacedKey kLoja;
    NamespacedKey kNucleo;
    private NamespacedKey kVolta;
    private NamespacedKey kItemTorre;
    private NamespacedKey kItemMapa;
    private NamespacedKey kItemDif;
    private NamespacedKey kItemColocar;
    /** Alcance para colocar torre mirando (o clique normal do Minecraft so alcanca ~4,5 blocos). */
    static final double ALCANCE_COLOCAR = 10.0;

    private final Map<String, Partida> partidas = new LinkedHashMap<>();
    /** Em que partida (mapa) cada jogador estava no ultimo tique, para trocar placar e barras. */
    private final Map<UUID, Partida> ondeEsta = new HashMap<>();
    private final Map<UUID, Long> ultimoMenu = new HashMap<>();

    // ================================================================== ciclo de vida

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kMob = new NamespacedKey(this, "mob");
        kTorre = new NamespacedKey(this, "torre");
        kEssencia = new NamespacedKey(this, "essencia");
        kLoja = new NamespacedKey(this, "loja");
        kNucleo = new NamespacedKey(this, "nucleo");
        kVolta = new NamespacedKey(this, "volta");
        kItemTorre = new NamespacedKey(this, "item_torre");
        kItemMapa = new NamespacedKey(this, "item_mapa");
        kItemDif = new NamespacedKey(this, "item_dificuldade");
        kItemColocar = new NamespacedKey(this, "item_colocar");
        migrarConfig();

        criarMundo();
        if (mundo == null) return;
        for (Mapa m : Mapa.TODOS) partidas.put(m.id, new Partida(this, m, mundo));

        Bukkit.getPluginManager().registerEvents(this, this);
        // espera as entidades carregarem para limpar e montar Nucleo e loja de cada mapa
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Partida pa : partidas.values()) pa.prepararArena();
        }, 60L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Partida pa : partidas.values()) {
                pa.tique();
                if (pa.rapido && pa.estado != Partida.Estado.ESPERANDO) pa.tique();
            }
        }, 80L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, this::conferirOndeEstao, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::vigiarPets, 40L, 5L);
        getLogger().info("Defesa da Torre pronta no mundo '" + Mapa.MUNDO + "' com " + partidas.size() + " mapas. Use /defesa para jogar.");
    }

    @Override
    public void onDisable() {
        if (mundo == null) return;
        for (Partida pa : partidas.values()) {
            pa.resetar(false);
            for (Player p : pa.jogadores()) pa.saiu(p);
        }
    }

    /** Versao 1 guardava um recorde so (mapa original, modo infinito). */
    private void migrarConfig() {
        int antigo = getConfig().getInt("recorde", 0);
        if (antigo > 0 && !getConfig().contains("recordes.campos.infinito")) {
            getConfig().set("recordes.campos.infinito", antigo);
            getConfig().set("recordes-jogadores.campos.infinito", getConfig().getString("recorde-jogadores", ""));
            saveConfig();
            getLogger().info("Recorde antigo (onda " + antigo + ") movido para o mapa Campos Verdes, modo Infinito.");
        }
    }

    private void criarMundo() {
        WorldCreator wc = new WorldCreator(Mapa.MUNDO)
                .generator(new VazioGen())
                .environment(World.Environment.NORMAL)
                .generateStructures(false);
        mundo = wc.createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo da arena!");
            return;
        }
        mundo.setDifficulty(Difficulty.NORMAL);
        mundo.setGameRule(GameRules.SPAWN_MOBS, false);
        mundo.setGameRule(GameRules.SPAWN_MONSTERS, false);
        mundo.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        mundo.setGameRule(GameRules.SPAWN_PATROLS, false);
        mundo.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        mundo.setGameRule(GameRules.ADVANCE_TIME, false);
        mundo.setGameRule(GameRules.ADVANCE_WEATHER, false);
        mundo.setGameRule(GameRules.KEEP_INVENTORY, true);
        mundo.setGameRule(GameRules.MOB_GRIEFING, false);
        mundo.setGameRule(GameRules.MOB_DROPS, false);
        mundo.setGameRule(GameRules.PVP, false);
        mundo.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        mundo.setTime(6000);
        mundo.setStorm(false);
        mundo.setThundering(false);
        // spawn do mundo longe dos mapas: a protecao de spawn do Minecraft impede quem nao e OP de clicar perto dele
        mundo.setSpawnLocation(VazioGen.spawnLonge(mundo));
        for (Mapa m : Mapa.TODOS) {
            // mantem o mapa sempre carregado para os monstros andarem e as torres atirarem
            for (int cx = m.chunkMinX(); cx <= m.chunkMaxX(); cx++) {
                for (int cz = m.chunkMinZ(); cz <= m.chunkMaxZ(); cz++) {
                    mundo.getChunkAt(cx, cz).load(true);
                    mundo.setChunkForceLoaded(cx, cz, true);
                }
            }
            if (!m.construido(mundo)) {
                getLogger().info("Construindo o mapa " + m.nome + "...");
                m.construir(mundo);
                getLogger().info("Mapa " + m.nome + " construido.");
            }
        }
    }

    // ================================================================== utilidades

    static Component txt(String legado) {
        return LegacyComponentSerializer.legacySection().deserialize(legado).decoration(TextDecoration.ITALIC, false);
    }

    static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    private boolean naArena(Entity e) {
        return mundo != null && e.getWorld().equals(mundo);
    }

    /** Partida do mapa onde fica este local (ou null). */
    Partida partidaEm(Location l) {
        for (Partida pa : partidas.values()) {
            if (pa.mapa.contem(l)) return pa;
        }
        return null;
    }

    private Partida partidaPorId(String id) {
        return id == null ? null : partidas.get(id.toLowerCase(Locale.ROOT));
    }

    private Partida partidaDe(Player p) {
        Partida pa = partidaEm(p.getLocation());
        return pa != null ? pa : ondeEsta.get(p.getUniqueId());
    }

    private void conferirOndeEstao() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Partida agora = naArena(p) ? partidaEm(p.getLocation()) : null;
            Partida antes = ondeEsta.get(p.getUniqueId());
            if (agora == antes) continue;
            if (antes != null) antes.saiu(p);
            if (agora != null) {
                agora.entrou(p);
                ondeEsta.put(p.getUniqueId(), agora);
            } else {
                ondeEsta.remove(p.getUniqueId());
            }
        }
    }

    private ItemStack item(Material mat, String nome, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(txt(nome));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(txt(s));
        m.lore(l);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack marcado(ItemStack it, NamespacedKey chave, String valor) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(chave, PersistentDataType.STRING, valor);
        it.setItemMeta(m);
        return it;
    }

    private static int[] slotsCentro(int n) {
        if (n <= 1) return new int[] {13};
        if (n == 2) return new int[] {11, 15};
        if (n == 3) return new int[] {11, 13, 15};
        if (n == 4) return new int[] {10, 12, 14, 16};
        int[] r = new int[n];
        int[] todos = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < n && i < todos.length; i++) r[i] = todos[i];
        return r;
    }

    // ================================================================== menus

    void abrirMenuMapas(Player p) {
        MapasHolder h = new MapasHolder();
        Inventory inv = Bukkit.createInventory(h, 27, txt("§8Defesa da Torre - escolha o mapa"));
        h.inv = inv;
        ItemStack vidro = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        List<Partida> lista = new ArrayList<>(partidas.values());
        int[] slots = slotsCentro(lista.size());
        for (int i = 0; i < lista.size() && i < slots.length; i++) {
            Partida pa = lista.get(i);
            Mapa m = pa.mapa;
            List<String> lore = new ArrayList<>();
            lore.add("§7" + m.desc1);
            lore.add("§7" + m.desc2);
            lore.add("");
            int js = pa.jogadores().size();
            lore.add("§fJogadores: §e" + js);
            if (pa.estado == Partida.Estado.ESPERANDO) lore.add("§fPartida: §7esperando §8(" + pa.dif.cor + pa.dif.nome + "§8)");
            else lore.add("§fPartida: §conda " + pa.ondaTexto() + " §8(" + pa.dif.cor + pa.dif.nome + "§8)");
            lore.add("");
            lore.add("§fRecordes:");
            for (Dificuldade d : Dificuldade.values()) {
                int rec = pa.recorde(d);
                int vit = pa.vitorias(d);
                lore.add("§8 • " + d.cor + d.nome + "§7: onda " + rec + (d.infinito() ? "" : "/" + d.ondas) + (vit > 0 ? " §6★ " + vit + " vitória(s)" : ""));
            }
            lore.add("");
            lore.add("§eClique para ir");
            inv.setItem(slots[i], marcado(item(m.icone, m.cor + "§l" + m.nome, lore), kItemMapa, m.id));
        }
        inv.setItem(22, item(Material.BOOK, "§e§lComo funciona", List.of(
                "§7Cada mapa tem a sua própria partida:",
                "§7dá para jogar os dois ao mesmo tempo.",
                "§7Ao iniciar, escolha a dificuldade:",
                "§aFácil§7, §eNormal§7, §cDifícil §7(com vitória)",
                "§7ou §5Infinito §7(até o Núcleo cair).")));
        p.openInventory(inv);
    }

    void abrirMenuDificuldade(Player p, Partida pa) {
        DificuldadeHolder h = new DificuldadeHolder(pa);
        Inventory inv = Bukkit.createInventory(h, 27, txt("§8Escolha a dificuldade - " + pa.mapa.nome));
        h.inv = inv;
        ItemStack vidro = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        Dificuldade[] ds = Dificuldade.values();
        int[] slots = slotsCentro(ds.length);
        for (int i = 0; i < ds.length; i++) {
            Dificuldade d = ds[i];
            List<String> lore = new ArrayList<>();
            lore.add(d.infinito() ? "§5Sem fim: até o Núcleo cair." : "§fVença §e" + d.ondas + " ondas §fpara ganhar.");
            lore.add("");
            lore.add("§fLimite de torres: §a" + d.limiteTorres);
            lore.add("§fVida dos monstros: §c" + Math.round(d.vidaMonstros * pa.mapa.vidaMult * 100) + "%");
            if (d.rampa < 1.0) lore.add("§7(começo mais leve: as ondas 1 a 4 vêm mais fracas)");
            lore.add("§fQuantidade de monstros: §c" + Math.round(d.quantidade * 100) + "%");
            lore.add("§fNúcleo: §c" + d.vidaNucleo + " de vida");
            lore.add("§fEssência inicial: §d" + d.essenciaInicial + (d.essenciaDrop != 1.0 ? " §7(drops " + Math.round(d.essenciaDrop * 100) + "%)" : ""));
            lore.add("§fTempo entre ondas: §e" + d.intervalo + "s");
            lore.add("");
            lore.add("§7Recorde: §6onda " + pa.recorde(d) + (pa.vitorias(d) > 0 ? " §6★ " + pa.vitorias(d) + " vitória(s)" : ""));
            lore.add("");
            lore.add("§aClique para começar");
            ItemStack it = marcado(item(d.icone, d.cor + "§l" + d.nome.toUpperCase(Locale.ROOT), lore), kItemDif, d.id);
            if (d == pa.dif) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            inv.setItem(slots[i], it);
        }
        p.openInventory(inv);
    }

    private void abrirLoja(Player p, Partida pa) {
        LojaHolder h = new LojaHolder(pa);
        Inventory inv = Bukkit.createInventory(h, 36, txt("§8Loja de Torres §7- §d" + pa.essencia + " Essência"));
        h.inv = inv;
        ItemStack vidro = item(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 36; i++) inv.setItem(i, vidro);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        TipoTorre[] tipos = TipoTorre.values();
        boolean cheio = pa.torres.size() >= pa.dif.limiteTorres;
        for (int i = 0; i < tipos.length && i < slots.length; i++) {
            TipoTorre t = tipos[i];
            List<String> lore = new ArrayList<>();
            lore.add("§7" + t.descricao);
            lore.add("");
            lore.add("§fCusto: §d" + t.custo + " Essência");
            if (t == TipoTorre.MINA) {
                lore.add("§fGera: §d" + TipoTorre.essenciaMina(1) + "/" + TipoTorre.essenciaMina(2) + "/" + TipoTorre.essenciaMina(3) + " §7por onda (nível 1/2/3)");
            } else if (t == TipoTorre.FAROL) {
                lore.add("§fBônus: §c+" + Math.round(TipoTorre.bonusFarol(1) * 100) + "% §7de dano (até +" + Math.round(TipoTorre.bonusFarol(3) * 100) + "% no nível 3)");
                lore.add("§fAlcance: §e" + t.alcance + " blocos");
            } else {
                String extra = switch (t) {
                    case CANHAO -> " §7em área";
                    case GELO -> " §7+ lentidão em área";
                    case VENENO -> " §7por segundo, em área";
                    case MORTEIRO -> " §7em área grande";
                    case TESLA -> " §7+ pula para outros";
                    default -> "";
                };
                lore.add("§fDano: §c" + num(t.dano) + extra);
                lore.add("§fAlcance: §e" + t.alcance + " blocos" + (t == TipoTorre.MORTEIRO ? " §7(não acerta de perto)" : ""));
                lore.add("§fAtaques por segundo: §e" + num(20.0 / t.intervalo));
            }
            lore.add("§7Evolui até o nível 3 (clique na torre).");
            lore.add("");
            if (cheio) lore.add("§cLimite de torres atingido (" + pa.dif.limiteTorres + ")");
            else lore.add(pa.essencia >= t.custo ? "§aClique para comprar e depois clique no chão" : "§cFalta " + (t.custo - pa.essencia) + " Essência");
            inv.setItem(slots[i], marcado(item(t.icone, t.cor + "§l" + t.nome, lore), kItemTorre, t.id));
        }
        if (pa.estado == Partida.Estado.ESPERANDO) {
            inv.setItem(23, item(Material.LIME_CONCRETE, "§a§lINICIAR PARTIDA", List.of(
                    "§7Escolha a dificuldade e começa",
                    "§7a contagem da onda 1.",
                    "§7Atual: " + pa.dif.cor + pa.dif.nome)));
        } else {
            inv.setItem(23, item(Material.CLOCK, "§eOnda " + pa.ondaTexto(), List.of("§7" + pa.estadoTexto().replaceAll("§.", ""), "§7Dificuldade: " + pa.dif.cor + pa.dif.nome)));
        }
        inv.setItem(24, item(cheio ? Material.RED_STAINED_GLASS : Material.LIME_STAINED_GLASS,
                "§fTorres: " + (cheio ? "§c" : "§a") + pa.torres.size() + "§7/" + pa.dif.limiteTorres,
                List.of("§7Limite do " + pa.dif.cor + pa.dif.nome + "§7.", "§7Vender uma torre libera espaço.")));
        inv.setItem(25, item(pa.mapa.icone, pa.mapa.cor + "§l" + pa.mapa.nome, List.of("§7" + pa.mapa.desc1, "§7" + pa.mapa.desc2)));
        inv.setItem(22, item(pa.rapido ? Material.LIGHTNING_ROD : Material.FEATHER, pa.rapido ? "§b§lVELOCIDADE 2x §a(ligada)" : "§f§lVELOCIDADE 1x",
                List.of("§7Acelera a partida: ondas, contagens,", "§7monstros e torres andam 2x.", "", pa.rapido ? "§eClique para voltar ao normal" : "§eClique para ligar o 2x", "§8(ou /defesa 2x)")));
        inv.setItem(31, item(Material.BOOK, "§e§lComo jogar", List.of(
                "§7Monstros saem do portal e andam",
                "§7pelo caminho até o §cNúcleo§7.",
                "§7Coloque torres no chão para matar",
                "§7eles antes que cheguem.",
                "",
                "§7Cada monstro solta §dEssência§7:",
                "§7use para comprar e evoluir torres.",
                "§7A cada §55 ondas §7vem um §5BOSS§7.",
                "§7Cada dificuldade tem um limite de torres.",
                "",
                "§fClique numa torre: evoluir ou vender",
                "§fOutros mapas: §e/defesa mapas",
                "§fAcelerar: §e/defesa 2x",
                "§fSair: §e/defesa sair")));
        p.openInventory(inv);
    }

    private void abrirMenuTorre(Player p, Partida pa, Partida.Torre t) {
        TorreHolder h = new TorreHolder(pa, t.stand);
        Inventory inv = Bukkit.createInventory(h, 9, txt("§8" + t.tipo.nome + " §7- nível " + t.nivel));
        h.inv = inv;
        ItemStack vidro = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, vidro);
        inv.setItem(2, item(t.tipo.icone, Partida.nomeTorre(t), descNivel(t.tipo, t.nivel, t.investido)));
        if (t.nivel < 3) {
            int n = t.nivel + 1;
            int custo = t.tipo.custoEvolucao(t.nivel);
            List<String> lore = new ArrayList<>();
            lore.add("§fCusto: §d" + custo + " Essência");
            if (t.tipo == TipoTorre.MINA) {
                lore.add("§fGera: §d" + TipoTorre.essenciaMina(t.nivel) + " §7→ §d" + TipoTorre.essenciaMina(n) + " §7por onda");
            } else if (t.tipo == TipoTorre.FAROL) {
                lore.add("§fBônus: §c+" + Math.round(TipoTorre.bonusFarol(t.nivel) * 100) + "% §7→ §c+" + Math.round(TipoTorre.bonusFarol(n) * 100) + "%");
                lore.add("§fAlcance: §e" + num(t.tipo.alcanceNivel(t.nivel)) + " §7→ §e" + num(t.tipo.alcanceNivel(n)));
            } else {
                lore.add("§fDano: §c" + num(t.tipo.danoNivel(t.nivel)) + " §7→ §c" + num(t.tipo.danoNivel(n)));
                lore.add("§fAlcance: §e" + num(t.tipo.alcanceNivel(t.nivel)) + " §7→ §e" + num(t.tipo.alcanceNivel(n)));
            }
            lore.add("");
            lore.add(pa.essencia >= custo ? "§aClique para evoluir" : "§cFalta " + (custo - pa.essencia) + " Essência");
            inv.setItem(4, item(Material.EXPERIENCE_BOTTLE, "§a§lEVOLUIR para nível " + n, lore));
        } else {
            inv.setItem(4, item(Material.NETHER_STAR, "§6§lNÍVEL MÁXIMO", List.of("§7Essa torre já está no máximo.")));
        }
        inv.setItem(6, item(Material.GOLD_NUGGET, "§e§lVENDER", List.of("§fDevolve §d" + (t.investido / 2) + " Essência", "§7(metade do que foi gasto)",
                "§7Libera 1 espaço no limite de torres.")));
        p.openInventory(inv);
    }

    private List<String> descNivel(TipoTorre t, int nivel, int investido) {
        List<String> l = new ArrayList<>();
        if (t == TipoTorre.MINA) {
            l.add("§fGera: §d" + TipoTorre.essenciaMina(nivel) + " Essência §7por onda");
        } else if (t == TipoTorre.FAROL) {
            l.add("§fBônus: §c+" + Math.round(TipoTorre.bonusFarol(nivel) * 100) + "% §7de dano nas torres perto");
            l.add("§fAlcance: §e" + num(t.alcanceNivel(nivel)) + " blocos");
        } else {
            l.add("§fDano: §c" + num(t.danoNivel(nivel)));
            l.add("§fAlcance: §e" + num(t.alcanceNivel(nivel)) + " blocos");
            l.add("§fAtaques por segundo: §e" + num(20.0 / t.intervaloNivel(nivel)));
        }
        l.add("§7Essência investida: §d" + investido);
        return l;
    }

    @EventHandler
    public void cliqueMenu(InventoryClickEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (!(h instanceof LojaHolder) && !(h instanceof TorreHolder) && !(h instanceof MapasHolder) && !(h instanceof DificuldadeHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;
        ItemStack it = e.getCurrentItem();
        if (h instanceof MapasHolder) {
            if (it == null || !it.hasItemMeta()) return;
            Partida pa = partidaPorId(it.getItemMeta().getPersistentDataContainer().get(kItemMapa, PersistentDataType.STRING));
            if (pa == null) return;
            p.closeInventory();
            entrar(p, pa);
            return;
        }
        if (h instanceof DificuldadeHolder dh) {
            if (it == null || !it.hasItemMeta()) return;
            Dificuldade d = Dificuldade.porId(it.getItemMeta().getPersistentDataContainer().get(kItemDif, PersistentDataType.STRING));
            if (d == null) return;
            p.closeInventory();
            if (dh.partida.iniciar(p, d)) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            return;
        }
        if (h instanceof LojaHolder lh) {
            Partida pa = lh.partida;
            if (e.getSlot() == 22) {
                alternarVelocidade(p, pa);
                Bukkit.getScheduler().runTask(this, () -> abrirLoja(p, pa));
                return;
            }
            if (e.getSlot() == 23 && pa.estado == Partida.Estado.ESPERANDO) {
                Bukkit.getScheduler().runTask(this, () -> abrirMenuDificuldade(p, pa));
                return;
            }
            if (it == null || !it.hasItemMeta()) return;
            TipoTorre t = TipoTorre.porId(it.getItemMeta().getPersistentDataContainer().get(kItemTorre, PersistentDataType.STRING));
            if (t == null) return;
            if (pa.torres.size() >= pa.dif.limiteTorres) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                p.sendMessage("§cLimite de torres do " + pa.dif.cor + pa.dif.nome + " §catingido (§f" + pa.dif.limiteTorres + "§c). Evolua ou venda uma torre.");
                return;
            }
            if (pa.essencia < t.custo) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                p.sendMessage("§cFalta Essência: precisa de §d" + t.custo + "§c, vocês têm §d" + pa.essencia + "§c.");
                return;
            }
            pa.colocando.put(p.getUniqueId(), t);
            pa.colocandoAte.put(p.getUniqueId(), System.currentTimeMillis() + 90_000L);
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            boolean naMao = darItemColocar(p, t);
            p.sendMessage("§aA " + t.cor + t.nome + " §aestá " + (naMao ? "na sua mão" : "pronta")
                    + "§a: olhe para o chão (até " + (int) ALCANCE_COLOCAR + " blocos) e clique para colocar.");
            p.sendMessage("§7(Ou olhe para o chão e digite §e/defesa colocar§7. Você tem 90 segundos.)");
            return;
        }
        TorreHolder th = (TorreHolder) h;
        Partida pa = th.partida;
        Partida.Torre t = pa.torres.get(th.stand);
        if (t == null) {
            p.closeInventory();
            return;
        }
        if (e.getSlot() == 4) {
            pa.evoluir(p, t);
            Bukkit.getScheduler().runTask(this, () -> {
                if (pa.torres.containsKey(t.stand)) abrirMenuTorre(p, pa, t);
            });
        } else if (e.getSlot() == 6) {
            p.closeInventory();
            pa.vender(p, t);
        }
    }

    @EventHandler
    public void arrastarMenu(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof LojaHolder || h instanceof TorreHolder || h instanceof MapasHolder || h instanceof DificuldadeHolder) e.setCancelled(true);
    }

    // ================================================================== interacoes

    // ---------------------------------------------------------------- colocar torre

    /** Poe na mao do jogador um item que representa a torre comprada (clicar com ele coloca a torre). */
    boolean darItemColocar(Player p, TipoTorre t) {
        tirarItemColocar(p);
        ItemStack it = item(t.icone, "§a§lColocar: " + t.cor + "§l" + t.nome, List.of(
                "§7Olhe para o chão e clique",
                "§7(botão direito ou esquerdo).",
                "§7Alcance: até " + (int) ALCANCE_COLOCAR + " blocos."));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(kItemColocar, PersistentDataType.STRING, t.id);
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack x = inv.getItem(i);
            if (x == null || x.getType().isAir()) {
                inv.setItem(i, it);
                inv.setHeldItemSlot(i);
                return true;
            }
        }
        int livre = inv.firstEmpty();
        if (livre < 0) return false;
        int mao = inv.getHeldItemSlot();
        inv.setItem(livre, inv.getItem(mao));
        inv.setItem(mao, it);
        return true;
    }

    boolean ehItemColocar(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kItemColocar, PersistentDataType.STRING);
    }

    void tirarItemColocar(Player p) {
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (ehItemColocar(inv.getItem(i))) inv.setItem(i, null);
        }
        if (ehItemColocar(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void cliqueNoChao(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!naArena(p)) return;
        Action a = e.getAction();
        boolean comItem = ehItemColocar(e.getItem());
        if (a == Action.PHYSICAL || (a != Action.RIGHT_CLICK_BLOCK && !comItem)) return;
        Partida pa = partidaDe(p);
        TipoTorre t = pa == null ? null : pa.colocando.get(p.getUniqueId());
        if (t == null) {
            if (comItem) {
                e.setCancelled(true);
                tirarItemColocar(p);
            }
            return;
        }
        e.setCancelled(true);
        Block alvo = e.getClickedBlock() != null ? e.getClickedBlock() : blocoOlhando(p);
        if (alvo == null) {
            p.sendActionBar(txt("§cOlhe para o chão (até " + (int) ALCANCE_COLOCAR + " blocos) para colocar a torre."));
            return;
        }
        if (pa.colocarTorre(p, alvo, t, false)) {
            pa.colocando.remove(p.getUniqueId());
            pa.colocandoAte.remove(p.getUniqueId());
            tirarItemColocar(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void naoLargarItemColocar(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (ehItemColocar(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoTrocarMaoItemColocar(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (ehItemColocar(e.getMainHandItem()) || ehItemColocar(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoMexerItemColocar(InventoryClickEvent e) {
        if (ehItemColocar(e.getCurrentItem()) || ehItemColocar(e.getCursor())) e.setCancelled(true);
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p
                && ehItemColocar(p.getInventory().getItem(e.getHotbarButton()))) e.setCancelled(true);
    }

    @EventHandler
    public void limparItemAoEntrar(org.bukkit.event.player.PlayerJoinEvent e) {
        tirarItemColocar(e.getPlayer());
    }

    private boolean menuRecente(Player p) {
        long agora = System.currentTimeMillis();
        Long antes = ultimoMenu.put(p.getUniqueId(), agora);
        return antes != null && agora - antes < 400;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void cliqueEntidade(PlayerInteractEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (!naArena(ent)) return;
        String loja = ent.getPersistentDataContainer().get(kLoja, PersistentDataType.STRING);
        if (loja != null) {
            e.setCancelled(true);
            Partida pa = partidaPorId(loja);
            if (pa != null && e.getHand() == EquipmentSlot.HAND && !menuRecente(e.getPlayer())) abrirLoja(e.getPlayer(), pa);
            return;
        }
        if (ent.getPersistentDataContainer().has(kNucleo, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            return;
        }
        if (ent.getPersistentDataContainer().has(kTorre, PersistentDataType.STRING)) {
            e.setCancelled(true);
            abrirMenuTorreDe(e.getPlayer(), ent, e.getHand());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void cliqueEntidadeAt(PlayerInteractAtEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (!naArena(ent)) return;
        if (ent.getPersistentDataContainer().has(kTorre, PersistentDataType.STRING)) {
            e.setCancelled(true);
            abrirMenuTorreDe(e.getPlayer(), ent, e.getHand());
        }
    }

    private void abrirMenuTorreDe(Player p, Entity ent, EquipmentSlot mao) {
        Partida pa = partidaEm(ent.getLocation());
        if (pa == null || mao != EquipmentSlot.HAND || menuRecente(p)) return;
        Partida.Torre t = pa.torreDoStand(ent);
        if (t != null) abrirMenuTorre(p, pa, t);
    }

    @EventHandler(ignoreCancelled = true)
    public void mexerNaTorre(PlayerArmorStandManipulateEvent e) {
        if (naArena(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pegarEssencia(EntityPickupItemEvent e) {
        if (!naArena(e.getEntity())) return;
        ItemStack it = e.getItem().getItemStack();
        if (it.getType() != Material.AMETHYST_SHARD || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(kEssencia, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        if (!(e.getEntity() instanceof Player p)) return;
        Partida pa = partidaPorId(id);
        if (pa == null) return;
        int v = it.getAmount();
        pa.pegouEssencia(e.getItem().getUniqueId(), v);
        e.getItem().remove();
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
        p.sendActionBar(txt("§d+" + v + " Essência §7(time: §d" + pa.essencia + "§7)"));
    }

    // ================================================================== protecoes da arena

    private boolean podeEditar(Player p) {
        return p.hasPermission("defesa.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(ignoreCancelled = true)
    public void quebrar(BlockBreakEvent e) {
        if (naArena(e.getPlayer()) && !podeEditar(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void colocarBloco(BlockPlaceEvent e) {
        if (naArena(e.getPlayer()) && !podeEditar(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void balde(PlayerBucketEmptyEvent e) {
        if (naArena(e.getPlayer()) && !podeEditar(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void explosao(EntityExplodeEvent e) {
        if (naArena(e.getEntity())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void danoEmProtegidos(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        if (!naArena(ent)) return;
        var pdc = ent.getPersistentDataContainer();
        if (pdc.has(kTorre, PersistentDataType.STRING) || pdc.has(kLoja, PersistentDataType.STRING) || pdc.has(kNucleo, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    /** Tira o nome do monstro no golpe final: o Minecraft escreve no log toda morte de mob com nome. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void golpeFinal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || !naArena(le)) return;
        if (!le.getPersistentDataContainer().has(kMob, PersistentDataType.STRING)) return;
        if (e.getFinalDamage() >= le.getHealth() + le.getAbsorptionAmount()) le.customName(null);
    }

    void alternarVelocidade(Player p, Partida pa) {
        pa.rapido = !pa.rapido;
        for (Player j : pa.jogadores()) {
            j.sendMessage(pa.rapido ? "§b§l2x! §f" + p.getName() + " §7acelerou a partida. §8(/defesa 2x volta ao normal)"
                    : "§f" + p.getName() + " §7voltou a partida para a velocidade normal.");
            j.playSound(j.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, pa.rapido ? 1.6f : 0.9f);
        }
    }

    /** Ninguem sente fome no mundo da Defesa. */
    @EventHandler(ignoreCancelled = true)
    public void semFome(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (!naArena(e.getEntity())) return;
        e.setCancelled(true);
        if (e.getEntity() instanceof Player p && p.getFoodLevel() < 20) {
            p.setFoodLevel(20);
            p.setSaturation(10f);
        }
    }

    /** So as torres matam os monstros: golpe, flecha, poder ou pet do jogador nao faz nada. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void jogadorNaoBate(EntityDamageByEntityEvent e) {
        if (!naArena(e.getEntity()) || e.getEntity() instanceof Player) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        boolean doJogador = d instanceof Player
                || (d instanceof org.bukkit.entity.Tameable t && t.getOwner() != null)
                || (d instanceof org.bukkit.entity.AreaEffectCloud c && c.getSource() instanceof Player)
                || (d instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player);
        if (doJogador) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void semPvp(EntityDamageByEntityEvent e) {
        if (!naArena(e.getEntity()) || !(e.getEntity() instanceof Player)) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        if (d instanceof Player) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void semAlvo(EntityTargetEvent e) {
        if (naArena(e.getEntity()) && e.getEntity().getPersistentDataContainer().has(kMob, PersistentDataType.STRING)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void semQueimarNoSol(EntityCombustEvent e) {
        if (e instanceof EntityCombustByEntityEvent || e instanceof EntityCombustByBlockEvent) return;
        if (naArena(e.getEntity()) && e.getEntity().getPersistentDataContainer().has(kMob, PersistentDataType.STRING)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void spawnNatural(CreatureSpawnEvent e) {
        if (!naArena(e.getEntity())) return;
        switch (e.getSpawnReason()) {
            case CUSTOM, DEFAULT, COMMAND, SPAWNER_EGG -> { }
            default -> e.setCancelled(true);
        }
    }

    @EventHandler
    public void monstroMorreu(EntityDeathEvent e) {
        if (!naArena(e.getEntity())) return;
        String id = e.getEntity().getPersistentDataContainer().get(kMob, PersistentDataType.STRING);
        Partida pa = partidaPorId(id);
        if (pa == null) return;
        Partida.Monstro mo = pa.monstros.remove(e.getEntity().getUniqueId());
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (mo != null) pa.monstroMorreu(e.getEntity(), mo);
    }

    @EventHandler(ignoreCancelled = true)
    public void caiuNoVazio(PlayerMoveEvent e) {
        if (e.getTo().getY() > Mapa.Y - 20 || !naArena(e.getPlayer())) return;
        Partida pa = partidaDe(e.getPlayer());
        Mapa m = pa != null ? pa.mapa : Mapa.CAMPOS;
        e.getPlayer().teleport(m.lobby(mundo));
        e.getPlayer().setFallDistance(0);
        e.getPlayer().sendMessage("§7Você caiu da ilha e voltou para o lobby.");
    }

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        if (!naArena(e.getPlayer())) return;
        Partida pa = ondeEsta.get(e.getPlayer().getUniqueId());
        e.setRespawnLocation((pa != null ? pa.mapa : Mapa.CAMPOS).lobby(mundo));
    }

    // ================================================================== pet do BabyPets

    /** O pet nao entra na arena: fica guardado enquanto o jogador esta no mundo da Defesa e volta quando ele sai. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void trocouDeMundo(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        if (mundo == null) return;
        Player p = e.getPlayer();
        if (p.getWorld().equals(mundo)) guardarPet(p);
        else if (!mundoSemPet(p.getWorld())) devolverPet(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void petAoEntrar(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || mundo == null) return;
            if (p.getWorld().equals(mundo)) guardarPet(p);
            else devolverPet(p);
        }, 60L);
    }

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
                p.sendMessage("§6Defesa da Torre §8» " + "§7Seu pet fica guardado aqui e volta quando você sair.");
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

    @EventHandler
    public void saiuDoServidor(PlayerQuitEvent e) {
        Partida pa = ondeEsta.remove(e.getPlayer().getUniqueId());
        if (pa != null) pa.saiu(e.getPlayer());
    }

    // ================================================================== comandos

    private void entrar(Player p, Partida pa) {
        if (pa.mapa.contem(p.getLocation())) {
            p.sendMessage("§eVocê já está no " + pa.mapa.nome + ". §7Use §e/defesa sair §7para voltar.");
            return;
        }
        if (!naArena(p)) {
            Location l = p.getLocation();
            p.getPersistentDataContainer().set(kVolta, PersistentDataType.STRING,
                    l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch());
        }
        p.teleport(pa.mapa.lobby(mundo));
        p.sendMessage("§6§l--- DEFESA DA TORRE: " + pa.mapa.cor + "§l" + pa.mapa.nome.toUpperCase(Locale.ROOT) + " §6§l---");
        p.sendMessage("§fDefenda o §cNúcleo §fdos monstros que saem do portal!");
        p.sendMessage("§fCompre torres no §6aldeão da loja §fou com §e/defesa loja§f.");
        p.sendMessage("§fComeçar: §a/defesa iniciar §7(escolhe a dificuldade) §7| §fSair: §e/defesa sair");
        if (pa.estado != Partida.Estado.ESPERANDO) {
            p.sendMessage("§7Partida em andamento: onda " + pa.ondaTexto() + " no " + pa.dif.cor + pa.dif.nome + "§7. Ajude o time!");
        }
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f);
    }

    private void sair(Player p) {
        if (!naArena(p)) {
            p.sendMessage("§7Você não está na arena.");
            return;
        }
        Location volta = null;
        String s = p.getPersistentDataContainer().get(kVolta, PersistentDataType.STRING);
        if (s != null) {
            String[] a = s.split(";");
            World w = a.length == 6 ? Bukkit.getWorld(a[0]) : null;
            if (w != null && !w.equals(mundo)) {
                try {
                    volta = new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]),
                            Float.parseFloat(a[4]), Float.parseFloat(a[5]));
                } catch (NumberFormatException ignored) {
                    volta = null;
                }
            }
        }
        if (volta == null) volta = Bukkit.getWorlds().get(0).getSpawnLocation();
        p.teleport(volta);
        p.sendMessage("§7Você saiu da arena.");
    }

    private Block blocoOlhando(Player p) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), ALCANCE_COLOCAR, FluidCollisionMode.NEVER, true);
        return r == null ? null : r.getHitBlock();
    }

    private Partida.Torre torreOlhando(Player p, Partida pa) {
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 6.0, 0.6,
                en -> en instanceof Mannequin && pa.torres.containsKey(en.getUniqueId()));
        if (r != null && r.getHitEntity() != null) return pa.torres.get(r.getHitEntity().getUniqueId());
        Partida.Torre perto = null;
        double melhor = 9.0;
        for (Partida.Torre t : pa.torres.values()) {
            double d = t.local.distanceSquared(p.getLocation());
            if (d < melhor) {
                melhor = d;
                perto = t;
            }
        }
        return perto;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (mundo == null) {
            s.sendMessage("§cA arena não carregou. Veja o log do servidor.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(s, args);
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: defesa admin [mapa] <iniciar [dif]|parar|status|essencia N|onda N|torre tipo x z|nucleo N|reconstruir>");
            return true;
        }
        if (!p.hasPermission("defesa.jogar")) {
            p.sendMessage("§cSem permissão.");
            return true;
        }
        Partida aqui = naArena(p) ? partidaDe(p) : null;
        switch (sub) {
            case "" -> {
                if (aqui == null) abrirMenuMapas(p);
                else ajuda(p);
            }
            case "entrar", "ir", "mapa", "mapas" -> {
                Partida pa = args.length > 1 ? partidaPorId(args[1]) : null;
                if (pa != null) entrar(p, pa);
                else abrirMenuMapas(p);
            }
            case "sair", "voltar" -> sair(p);
            case "2x", "rapido", "velocidade", "acelerar" -> {
                Partida pa = partidaDe(p);
                if (pa == null) p.sendMessage("§cEntre numa partida primeiro (§e/defesa§c).");
                else alternarVelocidade(p, pa);
            }
            case "loja" -> {
                if (aqui == null) {
                    p.sendMessage("§eEntre num mapa primeiro: §a/defesa entrar");
                    return true;
                }
                abrirLoja(p, aqui);
            }
            case "iniciar", "comecar" -> {
                if (aqui == null) {
                    p.sendMessage("§eEntre num mapa primeiro: §a/defesa entrar");
                    return true;
                }
                Dificuldade d = args.length > 1 ? Dificuldade.porId(args[1]) : null;
                if (d != null) aqui.iniciar(p, d);
                else if (aqui.estado == Partida.Estado.ESPERANDO) abrirMenuDificuldade(p, aqui);
                else aqui.iniciar(p, null);
            }
            case "dificuldade" -> {
                if (aqui == null) {
                    p.sendMessage("§eEntre num mapa primeiro: §a/defesa entrar");
                    return true;
                }
                if (aqui.estado != Partida.Estado.ESPERANDO) {
                    p.sendMessage("§eSó dá para escolher a dificuldade antes de começar.");
                    return true;
                }
                abrirMenuDificuldade(p, aqui);
            }
            case "colocar" -> {
                if (aqui == null) return true;
                TipoTorre t = aqui.colocando.get(p.getUniqueId());
                if (t == null) {
                    p.sendMessage("§eCompre uma torre na loja primeiro (§a/defesa loja§e).");
                    return true;
                }
                Block b = blocoOlhando(p);
                if (b == null) {
                    p.sendMessage("§cOlhe para o chão onde quer a torre.");
                    return true;
                }
                if (aqui.colocarTorre(p, b, t, false)) {
                    aqui.colocando.remove(p.getUniqueId());
                    aqui.colocandoAte.remove(p.getUniqueId());
                    tirarItemColocar(p);
                }
            }
            case "evoluir", "vender" -> {
                Partida.Torre t = aqui != null ? torreOlhando(p, aqui) : null;
                if (t == null) {
                    p.sendMessage("§eOlhe para uma torre (bem perto) e use o comando de novo.");
                    return true;
                }
                if (sub.equals("evoluir")) aqui.evoluir(p, t);
                else aqui.vender(p, t);
            }
            default -> ajuda(p);
        }
        return true;
    }

    private void ajuda(CommandSender s) {
        s.sendMessage("§6§l--- DEFESA DA TORRE ---");
        s.sendMessage("§e/defesa mapas §7- escolher o mapa");
        s.sendMessage("§e/defesa sair §7- volta para onde você estava");
        s.sendMessage("§e/defesa iniciar §7- escolhe a dificuldade e começa");
        s.sendMessage("§e/defesa loja §7- comprar torres (ou clique no aldeão)");
        s.sendMessage("§e/defesa colocar §7- coloca a torre comprada onde você olha");
        s.sendMessage("§e/defesa evoluir §7/ §e/defesa vender §7- na torre que você olha");
        s.sendMessage("§7Clicar numa torre também abre o menu de evoluir/vender.");
    }

    private boolean admin(CommandSender s, String[] args) {
        if (!s.hasPermission("defesa.admin")) {
            s.sendMessage("§cSem permissão.");
            return true;
        }
        int i = 1;
        Partida pa = args.length > 1 ? partidaPorId(args[1]) : null;
        if (pa != null) i = 2;
        else pa = s instanceof Player p && naArena(p) && partidaDe(p) != null ? partidaDe(p) : partidas.get(Mapa.CAMPOS.id);
        String op = args.length > i ? args[i].toLowerCase(Locale.ROOT) : "status";
        String a1 = args.length > i + 1 ? args[i + 1] : null;
        try {
            switch (op) {
                case "iniciar" -> {
                    pa.forcado = true;
                    pa.iniciar(s, Dificuldade.porId(a1));
                }
                case "parar" -> {
                    pa.resetar(true);
                    s.sendMessage("Partida do " + pa.mapa.nome + " resetada.");
                }
                case "essencia" -> {
                    if (a1 != null) pa.essencia += Integer.parseInt(a1);
                    s.sendMessage("Essencia: " + pa.essencia);
                }
                case "onda" -> s.sendMessage(a1 != null && pa.pularParaOnda(Integer.parseInt(a1))
                        ? "A proxima partida comeca na onda " + a1 : "So da para mudar a onda antes de iniciar.");
                case "nucleo" -> {
                    if (a1 != null) pa.vidaNucleo = Integer.parseInt(a1);
                    s.sendMessage("Nucleo: " + pa.vidaNucleo);
                }
                case "dificuldade" -> {
                    Dificuldade d = Dificuldade.porId(a1);
                    if (d != null && pa.estado == Partida.Estado.ESPERANDO) pa.definirDificuldade(d);
                    s.sendMessage("Dificuldade: " + pa.dif.id);
                }
                case "torre" -> {
                    if (args.length < i + 4) {
                        s.sendMessage("Uso: defesa admin [mapa] torre <tipo> <x> <z>  (x z relativos ao mapa)");
                        return true;
                    }
                    TipoTorre t = TipoTorre.porId(args[i + 1]);
                    if (t == null) {
                        List<String> ids = new ArrayList<>();
                        for (TipoTorre tt : TipoTorre.values()) ids.add(tt.id);
                        s.sendMessage("Tipos: " + String.join(", ", ids));
                        return true;
                    }
                    Block b = mundo.getBlockAt(pa.mapa.ox + Integer.parseInt(args[i + 2]), Mapa.Y - 1, pa.mapa.oz + Integer.parseInt(args[i + 3]));
                    s.sendMessage(pa.colocarTorre(null, b, t, true) ? "Torre colocada." : "Nao deu para colocar ali (" + b.getType() + ").");
                }
                case "evoluirtodas" -> {
                    int n = a1 != null ? Integer.parseInt(a1) : 3;
                    for (Partida.Torre t : pa.torres.values()) {
                        t.nivel = Math.max(1, Math.min(3, n));
                        pa.atualizarGuardiao(t);
                    }
                    s.sendMessage("Torres no nivel " + n);
                }
                case "simular" -> {
                    java.io.File f = new java.io.File(getDataFolder(), (a1 == null ? "plano" : a1) + ".txt");
                    List<String> linhas = java.nio.file.Files.readAllLines(f.toPath());
                    Dificuldade d = Dificuldade.porId(linhas.get(0).trim());
                    pa.resetar(false);
                    pa.simular(linhas.subList(1, linhas.size()));
                    pa.iniciar(s, d);
                    s.sendMessage("Simulando " + pa.mapa.id + " no " + pa.dif.id + " com " + f.getName());
                }
                case "reconstruir" -> {
                    pa.resetar(true);
                    pa.mapa.construir(mundo);
                    pa.prepararArena();
                    s.sendMessage("Mapa " + pa.mapa.nome + " reconstruido.");
                }
                default -> {
                    for (Partida x : partidas.values()) s.sendMessage(x.status());
                }
            }
        } catch (NumberFormatException ex) {
            s.sendMessage("Numero invalido.");
        } catch (java.io.IOException ex) {
            s.sendMessage("Nao consegui ler o arquivo: " + ex.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> r = new ArrayList<>();
        if (args.length == 1) {
            r.addAll(List.of("mapas", "entrar", "sair", "iniciar", "dificuldade", "loja", "colocar", "evoluir", "vender"));
            if (s.hasPermission("defesa.admin")) r.add("admin");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("entrar") || args[0].equalsIgnoreCase("mapas"))) {
            r.addAll(partidas.keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("iniciar")) {
            for (Dificuldade d : Dificuldade.values()) r.add(d.id);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && s.hasPermission("defesa.admin")) {
            r.addAll(partidas.keySet());
            r.addAll(List.of("iniciar", "parar", "status", "essencia", "onda", "nucleo", "dificuldade", "torre", "evoluirtodas", "reconstruir"));
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("admin") && s.hasPermission("defesa.admin")) {
            String ant = args[args.length - 2].toLowerCase(Locale.ROOT);
            if (partidas.containsKey(ant)) {
                r.addAll(List.of("iniciar", "parar", "status", "essencia", "onda", "nucleo", "dificuldade", "torre", "evoluirtodas", "reconstruir"));
            } else if (ant.equals("torre")) {
                for (TipoTorre t : TipoTorre.values()) r.add(t.id);
            } else if (ant.equals("iniciar") || ant.equals("dificuldade")) {
                for (Dificuldade d : Dificuldade.values()) r.add(d.id);
            }
        }
        String pre = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(pre));
        return r;
    }
}
