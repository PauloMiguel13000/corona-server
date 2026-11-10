package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.map.MapView;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Masmorras (dungeons) espalhadas pelo mundo normal, Nether e End, com um chefe no fim de cada uma.
 * Constroi sozinho em lugares que ninguem usou (aos poucos, sem travar), protege os blocos, enche as salas de
 * monstros quando alguem entra, solta o chefe na arena e da premio pelo nivel. Tambem coloca a borda do mundo.
 */
public final class Masmorras extends JavaPlugin implements Listener {

    static final String PREFIXO = "§6§lMasmorras §8» §7";

    final List<Masmorra> todas = new ArrayList<>();
    Coronas coronas;
    Loja loja;
    Skins skins;
    Chocadeiras chocadeiras;
    Hub hub;
    final Map<Integer, Masmorra> porId = new HashMap<>();
    private final Map<String, BlockData> dados = new HashMap<>();
    private File arquivo;
    private YamlConfiguration banco;
    NamespacedKey kDe, kChefe;
    Reliquias reliquias;
    Galeria galeria;
    AlaMonstros ala;
    MundoMasmorras mundoNovo;
    LobbyMundo lobby;
    Ranking ranking;
    Missoes missoes;
    VendaClasses vendaClasses;
    Campos campos;
    Coroas coroas;
    Portoes portoes;

    /** A masmorra que tem esse bloco dentro (ou null). */
    Masmorra masmorraEm(World w, int x, int y, int z) {
        for (Masmorra m : todas) if (m.mundo.equals(w.getName()) && m.dentro(x, y, z)) return m;
        return null;
    }
    private NamespacedKey kSemNivel, kSemNomeLm;
    private long tick;
    private final Random rnd = new Random();

    // construcao
    private final Deque<Pedido> fila = new ArrayDeque<>();
    private Pedido atual;
    private BukkitTask obraTarefa;
    private final Map<String, Locais> locais = new HashMap<>();
    String situacao = "parado";

    // desmanchar as antigas (versao menor que Planta.VERSAO)
    private final Deque<Masmorra> demolir = new ArrayDeque<>();
    private final Map<Integer, Integer> adiadas = new HashMap<>();
    private Masmorra demolindoAgora;
    /** Admins que ligaram o modo de edicao (so assim quebram/colocam blocos nas masmorras). */
    private final Set<UUID> editando = new HashSet<>();

    private static final class Pedido {
        final Tipo tipo;
        final World w;
        int tentativas;
        int[] centro;
        final List<long[]> chunks = new ArrayList<>();
        Pedido(Tipo tipo, World w) {
            this.tipo = tipo;
            this.w = w;
        }
    }

    // ================================================================== ligar

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mundoNovo = new MundoMasmorras(this);
        try {
            mundoNovo.criar();
        } catch (Throwable ex) {
            getLogger().warning("Mundo das Masmorras nao foi criado: " + ex);
            ex.printStackTrace();
        }
        getServer().getPluginManager().registerEvents(mundoNovo, this);
        kDe = new NamespacedKey(this, "masmorra");
        kChefe = new NamespacedKey(this, "chefe");
        kSemNivel = NamespacedKey.fromString("levelledmobs:nolevel");
        kSemNomeLm = NamespacedKey.fromString("levelledmobs:denylm_nametag");
        arquivo = new File(getDataFolder(), "masmorras.yml");
        banco = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection s = banco.getConfigurationSection("masmorras");
        if (s != null)
            for (String k : s.getKeys(false)) {
                try {
                    Masmorra m = Masmorra.ler(Integer.parseInt(k), s.getConfigurationSection(k));
                    if (m != null) {
                        todas.add(m);
                        porId.put(m.id, m);
                    }
                } catch (Exception ex) {
                    getLogger().warning("Masmorra " + k + " com erro no masmorras.yml: " + ex.getMessage());
                }
            }
        conferirBlocos();
        getServer().getPluginManager().registerEvents(this, this);
        coronas = new Coronas(this);
        loja = new Loja(this);
        skins = new Skins(this);
        chocadeiras = new Chocadeiras(this);
        Bukkit.getPluginManager().registerEvents(chocadeiras, this);
        // efeitos do conjunto cosmetico de Herobrine (so visual)
        getServer().getPluginManager().registerEvents(new HerobrineEfeitos(this), this);
        hub = new Hub(this);
        getServer().getPluginManager().registerEvents(loja, this);
        getServer().getPluginManager().registerEvents(hub, this);
        reliquias = new Reliquias(this);
        getServer().getPluginManager().registerEvents(reliquias, this);
        galeria = new Galeria(this);
        getServer().getPluginManager().registerEvents(galeria, this);
        BedrockSeguro bedrockSeguro = new BedrockSeguro(this);
        getServer().getPluginManager().registerEvents(bedrockSeguro, this);
        bedrockSeguro.iniciar();
        reliquias.iniciar();
        try {
            galeria.iniciar();
            ala = new AlaMonstros(this, galeria);
            ala.iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Galeria das Reliquias nao iniciou: " + ex);
        }
        try {
            hub.iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Spawn das Masmorras nao iniciou: " + ex);
        }
        loja.iniciar();
        ranking = new Ranking(this);
        ranking.iniciar();
        missoes = new Missoes(this);
        getServer().getPluginManager().registerEvents(missoes, this);
        missoes.iniciar();
        vendaClasses = new VendaClasses(this);
        getServer().getPluginManager().registerEvents(vendaClasses, this);
        coroas = new Coroas(this);
        campos = new Campos(this, mundoNovo);
        getServer().getPluginManager().registerEvents(campos, this);
        campos.iniciar();
        lobby = new LobbyMundo(this, mundoNovo);
        getServer().getPluginManager().registerEvents(lobby, this);
        try {
            lobby.iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Lobby do Mundo das Masmorras nao iniciou: " + ex);
        }
        portoes = new Portoes(this, mundoNovo);
        getServer().getPluginManager().registerEvents(portoes, this);
        try {
            portoes.iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Portoes do Nether/End nao iniciaram: " + ex);
        }
        ConfigurationSection ms = banco.getConfigurationSection("mapas");
        if (ms != null)
            for (String k : ms.getKeys(false)) {
                MapView v = Bukkit.getMap(ms.getInt(k));
                World w = Bukkit.getWorld(k);
                if (v != null && w != null) configurarMapa(v, w);
            }
        Bukkit.getScheduler().runTaskTimer(this, this::ciclo, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, this::bussolasNaMao, 60L, 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            aplicarBordas();
            if (getConfig().getBoolean("construir", true) && !atualizarAntigas()) planejar();
        }, 200L);
        getLogger().info(todas.size() + " masmorras carregadas.");
    }

    @Override
    public void onDisable() {
        if (ranking != null) ranking.salvar();
        if (missoes != null) missoes.salvarAgora();
        if (campos != null) campos.parar();
        if (coroas != null) coroas.parar();
        for (Masmorra m : todas) limpar(m);
        if (atual != null) soltarChunks(atual);
        if (demolindoAgora != null) {
            World w = Bukkit.getWorld(demolindoAgora.mundo);
            if (w != null) for (long[] ch : chunksDemolicao) w.removePluginChunkTicket((int) ch[0], (int) ch[1], this);
        }
    }

    static Component txt(String legado) {
        return LegacyComponentSerializer.legacySection().deserialize(legado).decoration(TextDecoration.ITALIC, false);
    }

    /** Confere se o Minecraft conhece todos os blocos, itens e encantamentos (avisa no log). */
    private void conferirBlocos() {
        int ruins = 0;
        for (Tipo t : Tipo.TODOS.values()) {
            for (String b : t.blocos()) {
                try {
                    Bukkit.createBlockData(b);
                } catch (IllegalArgumentException ex) {
                    getLogger().warning("Bloco desconhecido em " + t.id + ": " + b);
                    ruins++;
                }
            }
            for (String[] l : new String[][] {t.lacaios, t.ajudantes, {t.chefe}})
                for (String e : l) {
                    try {
                        EntityType.valueOf(e);
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("Monstro desconhecido em " + t.id + ": " + e);
                        ruins++;
                    }
                }
        }
        for (String m : Premios.materiais())
            if (Material.matchMaterial(m) == null) {
                getLogger().warning("Item de premio desconhecido: " + m);
                ruins++;
            }
        for (String e : Premios.encantamentos())
            if (Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e)) == null) {
                getLogger().warning("Encantamento desconhecido: " + e);
                ruins++;
            }
        if (ruins == 0) getLogger().info("Blocos, monstros e premios conferidos.");
    }

    BlockData dado(String s) {
        return dados.computeIfAbsent(s, k -> {
            try {
                return Bukkit.createBlockData(k);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Bloco invalido: " + k);
                return Bukkit.createBlockData(Material.STONE_BRICKS);
            }
        });
    }

    void depois(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(this, r, Math.max(1, ticks));
    }

    // ================================================================== borda do mundo

    String nomeMundo(Tipo.Onde o) {
        return switch (o) {
            case NORMAL -> getConfig().getString("mundos.normal", "world");
            case NETHER -> getConfig().getString("mundos.nether", "world_nether");
            case END -> getConfig().getString("mundos.end", "world_the_end");
        };
    }

    /** Spawn das Masmorras antigo (masmorras_spawn): quem aparece la vai para o lobby novo (pedido do Paulo, 08/11 09:45). */
    @EventHandler
    public void caiuNoHubAntigo(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (hub != null && hub.ehHub(p.getWorld()) && mundoNovo != null && mundoNovo.normal != null) depois(1, () -> {
            if (p.isOnline() && hub.ehHub(p.getWorld())) mundoNovo.irParaLobby(p);
        });
    }

    @EventHandler
    public void entrouNoHubAntigo(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (hub != null && hub.ehHub(p.getWorld()) && mundoNovo != null && mundoNovo.normal != null) depois(30, () -> {
            if (p.isOnline() && hub.ehHub(p.getWorld())) mundoNovo.irParaLobby(p);
        });
    }

    /** Mundo do spawn principal / survival (para "voltar ao spawn"). */
    String mundoSpawnPrincipal() {
        return getConfig().getString("mundos.survival", "world");
    }

    /** Spawn das masmorras: o lobby do mundo novo, ou o Spawn das Masmorras antigo se o mundo novo estiver desligado. */
    void irParaSpawnMasmorras(Player p) {
        if (mundoNovo != null && mundoNovo.normal != null) mundoNovo.irParaLobby(p);
        else hub.teleportar(p);
    }

    /** Limites dos aneis de nivel do mundo novo (ou null quando o mundo e o antigo). */
    int[] zonasDe(World w) {
        if (mundoNovo == null || mundoNovo.normal == null || !w.equals(mundoNovo.normal)) return null;
        return mundoNovo.zonas();
    }

    int borda(Tipo.Onde o) {
        return switch (o) {
            case NORMAL -> getConfig().getInt("borda.normal", 10000);
            case NETHER -> getConfig().getInt("borda.nether", 1250);
            case END -> getConfig().getInt("borda.end", 10000);
        };
    }

    void aplicarBordas() {
        if (!getConfig().getBoolean("borda.aplicar", true)) return;
        for (Tipo.Onde o : Tipo.Onde.values()) {
            World w = Bukkit.getWorld(nomeMundo(o));
            if (w == null) continue;
            WorldBorder b = w.getWorldBorder();
            int tam = borda(o);
            if (Math.abs(b.getSize() - tam) > 0.5 || Math.abs(b.getCenter().getX()) > 0.5 || Math.abs(b.getCenter().getZ()) > 0.5) {
                b.setCenter(0, 0);
                b.setSize(tam);
                b.setWarningDistance(16);
                getLogger().info("Borda de " + w.getName() + ": " + tam + " x " + tam + " blocos (centro 0, 0).");
            }
        }
    }

    // ================================================================== planejar e construir

    private long semente(World w, int salt) {
        return w.getSeed() ^ (0x5DEECE66DL * (salt + 1)) ^ getConfig().getLong("semente", 1234567L);
    }

    void planejar() {
        if (atual != null || !fila.isEmpty() || demolindoAgora != null || !demolir.isEmpty()) return;
        for (Tipo.Onde o : Tipo.Onde.values()) {
            World w = Bukkit.getWorld(nomeMundo(o));
            if (w == null) continue;
            Locais loc = new Locais(o, borda(o) / 2, semente(w, o.ordinal()), zonasDe(w));
            for (Masmorra m : todas) if (m.mundo.equals(w.getName())) loc.aceitar(new int[] {m.tudo.cx(), m.tudo.cz()});
            locais.put(w.getName(), loc);
            for (Tipo t : Tipo.de(o)) {
                int quer = getConfig().getInt("quantidade." + t.id, 0), tem = 0;
                for (Masmorra m : todas) if (m.tipo == t && m.mundo.equals(w.getName())) tem++;
                for (int i = tem; i < quer; i++) fila.add(new Pedido(t, w));
            }
        }
        if (fila.isEmpty()) {
            situacao = "todas construidas";
            return;
        }
        getLogger().info("Vou construir " + fila.size() + " masmorras, uma de cada vez (sem travar o servidor).");
        proxima();
    }

    private void proxima() {
        atual = fila.poll();
        if (atual == null) {
            situacao = "todas construidas";
            getLogger().info("Masmorras prontas: " + todas.size() + ".");
            return;
        }
        tentar(atual);
    }

    private void tentar(Pedido p) {
        Locais loc = locais.get(p.w.getName());
        p.tentativas++;
        if (p.tentativas > 40) {
            getLogger().warning("Nao achei lugar para " + p.tipo.nome + " em " + p.w.getName() + ".");
            depois(20, this::proxima);
            return;
        }
        World w = p.w;
        int[] c = loc.candidato(p.tipo, (x, z) -> w.getBiome(x, 64, z).getKey().getKey().toUpperCase(Locale.ROOT));
        if (c == null) {
            getLogger().warning("Sem lugar livre para " + p.tipo.nome + " em " + w.getName() + ".");
            depois(20, this::proxima);
            return;
        }
        p.centro = c;
        situacao = "carregando o lugar de " + p.tipo.nome + " (" + c[0] + ", " + c[1] + ")";
        int x1, x2, z1, z2, meia = p.tipo.meia();
        if (p.tipo.estilo == Tipo.Estilo.TORRE) {
            x1 = c[0] - meia - 3;
            x2 = c[0] + meia + 3;
            z1 = c[1] - meia - 3;
            z2 = c[1] + meia + 8;
        } else {
            x1 = c[0] - meia - 6;
            x2 = c[0] + meia + 6;
            z1 = c[1] - meia - 76;
            z2 = c[1] + meia + 6;
        }
        p.chunks.clear();
        for (int cx = Math.floorDiv(x1, 16); cx <= Math.floorDiv(x2, 16); cx++)
            for (int cz = Math.floorDiv(z1, 16); cz <= Math.floorDiv(z2, 16); cz++) p.chunks.add(new long[] {cx, cz});
        int[] faltam = {p.chunks.size()};
        for (long[] ch : p.chunks) {
            w.addPluginChunkTicket((int) ch[0], (int) ch[1], this);
            w.getChunkAtAsync((int) ch[0], (int) ch[1], true, chunk -> {
                if (--faltam[0] == 0) depois(2, () -> validar(p));
            });
        }
    }

    private void soltarChunks(Pedido p) {
        for (long[] ch : p.chunks) p.w.removePluginChunkTicket((int) ch[0], (int) ch[1], this);
    }

    private void validar(Pedido p) {
        World w = p.w;
        long limite = getConfig().getLong("habitado-maximo-minutos", 5) * 60L * 20L;
        boolean ok = true;
        for (long[] ch : p.chunks) {
            Chunk c = w.getChunkAt((int) ch[0], (int) ch[1]);
            if (c.getInhabitedTime() > limite) {
                ok = false;
                break;
            }
        }
        int cx = p.centro[0], cz = p.centro[1];
        int oy;
        Tipo t = p.tipo;
        if (t.onde == Tipo.Onde.NORMAL) {
            int agua = 0, menor = Integer.MAX_VALUE, maior = Integer.MIN_VALUE;
            int r = t.estilo == Tipo.Estilo.TORRE ? t.torreMeia + 1 : t.meia() - 4;
            for (int dx = -r; dx <= r; dx += r)
                for (int dz = -r; dz <= r; dz += r) {
                    Block b = w.getHighestBlockAt(cx + dx, cz + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    if (b.getType() == Material.WATER || b.getType() == Material.LAVA || b.getType() == Material.ICE) agua++;
                    menor = Math.min(menor, b.getY());
                    maior = Math.max(maior, b.getY());
                }
            if (agua >= 2) ok = false;
            if (t.estilo == Tipo.Estilo.TORRE) {
                oy = w.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                if (maior - oy > 8) ok = false;
            } else oy = Math.max(w.getMinHeight() + 14, menor - Math.max(18, t.alturaArena + 6));
        } else if (t.onde == Tipo.Onde.NETHER) oy = 64;
        else oy = 70;
        if (!ok) {
            soltarChunks(p);
            locais.get(w.getName()).recusar(p.centro);
            depois(5, () -> tentar(p));
            return;
        }
        int id = 1;
        for (Masmorra m : todas) id = Math.max(id, m.id + 1);
        long sem = new Random(semente(w, 7) ^ (id * 0x9E3779B97F4A7C15L)).nextLong();
        int ox = cx - t.meia(), oz = cz - t.meia();
        Planta planta = Planta.de(t, sem, ox, oy, oz);
        Construtor.Obra obra = Construtor.montar(planta, new DestinoMundo(w));
        locais.get(w.getName()).aceitar(p.centro);
        colocar(p, id, sem, planta, obra);
    }

    /** Coloca os blocos aos poucos (no maximo ~8 ms por tick) e depois os baus. */
    private void colocar(Pedido p, int id, long sem, Planta planta, Construtor.Obra obra) {
        World w = p.w;
        Iterator<Map.Entry<Long, String>> it = obra.blocos.entrySet().iterator();
        int total = obra.blocos.size();
        int[] feitos = {0};
        long inicio = System.currentTimeMillis();
        situacao = "construindo " + p.tipo.nome + " em " + p.centro[0] + ", " + p.centro[1];
        obraTarefa = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long t0 = System.nanoTime();
            while (it.hasNext() && System.nanoTime() - t0 < 8_000_000L) {
                Map.Entry<Long, String> e = it.next();
                w.getBlockAt(Construtor.cx(e.getKey()), Construtor.cy(e.getKey()), Construtor.cz(e.getKey())).setBlockData(dado(e.getValue()), false);
                feitos[0]++;
            }
            if (it.hasNext()) return;
            obraTarefa.cancel();
            obraTarefa = null;
            Masmorra m = Masmorra.criar(id, p.tipo, w.getName(), sem, planta, obra);
            for (int[] b : m.baus) porBau(w, b, m.tipo.tabela, true);
            if (obra.placa != null) porPlaca(w, obra.placa);
            todas.add(m);
            porId.put(m.id, m);
            salvar();
            soltarChunks(p);
            getLogger().info("Masmorra #" + id + " " + p.tipo.nome + " (nivel " + p.tipo.nivel + ") pronta em " + w.getName() + " "
                    + m.saidaX + " " + m.saidaY + " " + m.saidaZ + " (" + total + " blocos, " + (System.currentTimeMillis() - inicio) / 1000 + " s).");
            atual = null;
            depois(40, this::proxima);
        }, 1L, 1L);
    }

    /** Placa em pe (madeira escura, texto brilhando, encerada: ninguem edita). */
    void porPlaca(World w, Construtor.Placa pl) {
        try {
            Block bl = w.getBlockAt(pl.x(), pl.y(), pl.z());
            BlockData bd = Bukkit.createBlockData(Material.DARK_OAK_SIGN);
            if (bd instanceof org.bukkit.block.data.type.Sign sd) sd.setRotation(org.bukkit.block.BlockFace.valueOf(pl.virada().toUpperCase(Locale.ROOT)));
            bl.setBlockData(bd, false);
            if (bl.getState() instanceof org.bukkit.block.Sign sg) {
                org.bukkit.block.sign.SignSide lado = sg.getSide(org.bukkit.block.sign.Side.FRONT);
                for (int i = 0; i < 4 && i < pl.linhas().length; i++) lado.line(i, txt(pl.linhas()[i]));
                lado.setGlowingText(true);
                lado.setColor(org.bukkit.DyeColor.WHITE);
                sg.setWaxed(true);
                sg.update(true, false);
            }
        } catch (Exception ex) {
            getLogger().warning("Nao consegui colocar a placa da masmorra: " + ex.getMessage());
        }
    }

    void porBau(World w, int[] b, String tabela, boolean colocarBloco) {
        Block bl = w.getBlockAt(b[0], b[1], b[2]);
        if (colocarBloco || bl.getType() != Material.CHEST) bl.setBlockData(dado("minecraft:chest[facing=" + Construtor.virado(b[3]) + "]"), false);
        BlockState st = bl.getState();
        if (st instanceof Chest c) {
            c.getBlockInventory().clear();
            LootTable lt = Bukkit.getLootTable(NamespacedKey.minecraft(tabela));
            if (lt == null) lt = Bukkit.getLootTable(NamespacedKey.minecraft("chests/simple_dungeon"));
            if (lt != null) c.setLootTable(lt, rnd.nextLong());
            c.update(true, false);
        }
    }

    void salvar() {
        ConfigurationSection mapas = banco.getConfigurationSection("mapas");
        Map<String, Object> guardar = mapas == null ? Map.of() : mapas.getValues(false);
        banco.set("masmorras", null);
        banco.set("mapas", null);
        for (Map.Entry<String, Object> e : guardar.entrySet()) banco.set("mapas." + e.getKey(), e.getValue());
        for (Masmorra m : todas) m.salvar(banco.createSection("masmorras." + m.id));
        try {
            banco.save(arquivo);
        } catch (IOException e) {
            getLogger().warning("Nao consegui salvar masmorras.yml: " + e.getMessage());
        }
    }

    // ================================================================== desmanchar as antigas

    private final List<long[]> chunksDemolicao = new ArrayList<>();

    /**
     * Masmorras de versao antiga (pequenas) sao desmanchadas (o terreno volta) uma de cada vez; depois o planejar()
     * constroi as novas. Devolve true se tem alguma para desmanchar.
     */
    boolean atualizarAntigas() {
        if (!getConfig().getBoolean("refazer-antigas", true)) return false;
        if (demolindoAgora != null || atual != null) return true;
        for (Masmorra m : todas) if ((m.versao < Planta.VERSAO || longeDemais(m)) && !demolir.contains(m)) demolir.add(m);
        // FASE 7 (08/11): mundos que deixaram de ser de masmorras (o survival): tudo que sobrou la e desmanchado, o terreno volta
        List<String> velhos = getConfig().getStringList("desmanchar-mundos");
        int antigos = 0;
        for (Masmorra m : todas) if (velhos.contains(m.mundo) && !demolir.contains(m)) {
            demolir.add(m);
            antigos++;
        }
        if (antigos > 0) getLogger().info(antigos + " masmorras do survival antigo vao ser desmanchadas (desmanchar-mundos).");
        // tipos repetidos demais (ex.: 3 do mesmo e o limite e 2): desmancha as que sobram (as mais longe do spawn)
        Map<String, List<Masmorra>> porTipo = new HashMap<>();
        for (Masmorra m : todas) if (!m.tipo.secreta && !demolir.contains(m)) porTipo.computeIfAbsent(m.mundo + "|" + m.tipo.id, k -> new ArrayList<>()).add(m);
        int sobrando = 0;
        for (List<Masmorra> l : porTipo.values()) {
            int quer = getConfig().getInt("quantidade." + l.get(0).tipo.id, 0);
            if (l.size() <= quer) continue;
            l.sort(Comparator.comparingDouble(x -> x.tudo == null ? 1e9 : Math.hypot(x.tudo.cx(), x.tudo.cz())));
            for (int i = quer; i < l.size(); i++) {
                demolir.add(l.get(i));
                sobrando++;
            }
        }
        if (sobrando > 0) getLogger().info(sobrando + " masmorras repetidas demais vao ser desmanchadas (no maximo " + "o que esta em quantidade de cada tipo).");
        if (demolir.isEmpty()) return false;
        getLogger().info("Vou desmanchar " + demolir.size() + " masmorras antigas ou longe demais (o terreno volta) e depois construir as novas, mais perto.");
        proximaDemolicao();
        return true;
    }

    /** Ficou longe demais depois que as distancias diminuiram: vai ser refeita mais perto. A secreta e as do Nether ficam. */
    boolean longeDemais(Masmorra m) {
        if (!getConfig().getBoolean("aproximar", true) || m.tipo.secreta || m.tudo == null || m.tipo.onde == Tipo.Onde.NETHER) return false;
        World wm = Bukkit.getWorld(m.mundo);
        int[] f = new Locais(m.tipo.onde, borda(m.tipo.onde) / 2, 0, wm == null ? null : zonasDe(wm)).faixa(m.tipo);
        return Math.hypot(m.tudo.cx(), m.tudo.cz()) > f[1] + 200;
    }

    private void proximaDemolicao() {
        Masmorra m = demolir.poll();
        if (m == null) {
            demolindoAgora = null;
            depois(40, this::planejar);
            return;
        }
        World w = Bukkit.getWorld(m.mundo);
        if (w == null || m.tudo == null) {
            todas.remove(m);
            porId.remove(m.id);
            salvar();
            depois(2, this::proximaDemolicao);
            return;
        }
        // ninguem por perto (nao enterra ninguem); se tiver gente, tenta de novo depois (3 vezes; depois fica a antiga)
        Caixa perto = m.tudo.crescer(32);
        for (Player p : w.getPlayers())
            if (perto.contem(p.getX(), p.getY(), p.getZ())) {
                int n = adiadas.merge(m.id, 1, Integer::sum);
                if (n <= 3) demolir.add(m);
                else getLogger().info("Masmorra antiga #" + m.id + " fica como esta (sempre tinha gente perto).");
                situacao = "esperando ninguem perto da masmorra antiga #" + m.id;
                depois(demolir.size() > 1 ? 20 : 1200, this::proximaDemolicao);
                return;
            }
        demolindoAgora = m;
        situacao = "desmanchando a masmorra antiga #" + m.id + " (" + m.tipo.nome + ")";
        Caixa a = m.tudo.crescer(40);
        chunksDemolicao.clear();
        for (int cx = Math.floorDiv(a.x1(), 16); cx <= Math.floorDiv(a.x2(), 16); cx++)
            for (int cz = Math.floorDiv(a.z1(), 16); cz <= Math.floorDiv(a.z2(), 16); cz++) chunksDemolicao.add(new long[] {cx, cz});
        int[] faltam = {chunksDemolicao.size()};
        for (long[] ch : chunksDemolicao) {
            w.addPluginChunkTicket((int) ch[0], (int) ch[1], this);
            w.getChunkAtAsync((int) ch[0], (int) ch[1], true, chunk -> {
                if (--faltam[0] == 0) depois(2, () -> desmanchar(m, w));
            });
        }
    }

    private void soltarDemolicao(World w) {
        for (long[] ch : chunksDemolicao) w.removePluginChunkTicket((int) ch[0], (int) ch[1], this);
        chunksDemolicao.clear();
    }

    private void desmanchar(Masmorra m, World w) {
        limpar(m);
        loja.tirarMercador(m, w);
        m.demolindo = true;
        long t0 = System.currentTimeMillis();
        Map<Long, String> plano;
        try {
            plano = Demolidor.plano(m, new DestinoMundo(w));
        } catch (Exception ex) {
            getLogger().warning("Nao consegui desmanchar a masmorra #" + m.id + ": " + ex);
            m.demolindo = false;
            soltarDemolicao(w);
            demolindoAgora = null;
            depois(20, this::proximaDemolicao);
            return;
        }
        Iterator<Map.Entry<Long, String>> it = plano.entrySet().iterator();
        int total = plano.size();
        BukkitTask[] tarefa = new BukkitTask[1];
        tarefa[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long n0 = System.nanoTime();
            while (it.hasNext() && System.nanoTime() - n0 < 8_000_000L) {
                Map.Entry<Long, String> e = it.next();
                w.getBlockAt(Construtor.cx(e.getKey()), Construtor.cy(e.getKey()), Construtor.cz(e.getKey())).setBlockData(dado(e.getValue()), false);
            }
            if (it.hasNext()) return;
            tarefa[0].cancel();
            todas.remove(m);
            porId.remove(m.id);
            salvar();
            soltarDemolicao(w);
            getLogger().info("Masmorra antiga #" + m.id + " " + m.tipo.nome + " desmanchada (" + total + " blocos, "
                    + (System.currentTimeMillis() - t0) / 1000 + " s).");
            demolindoAgora = null;
            depois(20, this::proximaDemolicao);
        }, 1L, 1L);
    }

    /** O mundo de verdade para o Construtor (so leitura; os blocos sao colocados depois). */
    static final class DestinoMundo implements Destino {
        final World w;
        DestinoMundo(World w) {
            this.w = w;
        }
        public Material tipo(int x, int y, int z) {
            return w.getBlockAt(x, y, z).getType();
        }
        public int chao(int x, int z) {
            return w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        }
        public int minY() {
            return w.getMinHeight();
        }
        public int maxY() {
            return w.getMaxHeight() - 1;
        }
        public void por(int x, int y, int z, String d) {
            throw new UnsupportedOperationException();
        }
        public void bau(int x, int y, int z, String v, String t) {
            throw new UnsupportedOperationException();
        }
    }

    // ================================================================== monstros

    private void marcarBase(Entity x, Masmorra m) {
        PersistentDataContainer pdc = x.getPersistentDataContainer();
        pdc.set(kDe, PersistentDataType.INTEGER, m.id);
        if (kSemNivel != null) pdc.set(kSemNivel, PersistentDataType.STRING, "masmorras");
        x.setPersistent(false);
        x.addScoreboardTag("clearlag_exclude");
        if (x instanceof LivingEntity le) le.setRemoveWhenFarAway(false);
    }

    /** Projeteis e TNT do chefe: sao da masmorra (nao ferem os proprios monstros) e nao ficam salvos. */
    void marcar(Entity x, Masmorra m) {
        x.getPersistentDataContainer().set(kDe, PersistentDataType.INTEGER, m.id);
        x.setPersistent(false);
    }

    Masmorra de(Entity x) {
        if (x == null) return null;
        Integer id = x.getPersistentDataContainer().get(kDe, PersistentDataType.INTEGER);
        return id == null ? null : porId.get(id);
    }

    List<Entity> lacaiosVivos(Masmorra m) {
        List<Entity> l = new ArrayList<>();
        Iterator<UUID> it = m.lacaios.iterator();
        while (it.hasNext()) {
            Entity x = Bukkit.getEntity(it.next());
            if (x == null || !x.isValid() || x.isDead()) it.remove();
            else l.add(x);
        }
        return l;
    }

    /** Cria um monstro da masmorra (mais forte conforme o nivel). */
    LivingEntity lacaio(Masmorra m, String tipo, Location l, boolean doChefe) {
        EntityType et;
        try {
            et = EntityType.valueOf(tipo);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Class<? extends Entity> cls = et.getEntityClass();
        if (cls == null) return null;
        Consumer<Entity> antes = x -> {
            marcarBase(x, m);
            if (x instanceof Slime s && !(x instanceof MagmaCube)) s.setSize(2);
            if (x instanceof MagmaCube mc) mc.setSize(2);
        };
        Entity x = l.getWorld().spawn(l, cls, CreatureSpawnEvent.SpawnReason.CUSTOM, antes);
        if (!(x instanceof LivingEntity le)) {
            x.remove();
            return null;
        }
        int n = m.tipo.nivel;
        AttributeInstance vida = le.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) {
            vida.setBaseValue(Math.min(1024, vida.getBaseValue() * (1 + 0.3 * (n - 1))));
            le.setHealth(vida.getValue());
        }
        AttributeInstance dano = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dano != null) dano.setBaseValue(dano.getBaseValue() + (n - 1) * 1.5);
        if (le instanceof Ageable a) a.setAdult();
        if (le instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);
        if (le instanceof Hoglin h) h.setImmuneToZombification(true);
        if (m.tipo.id.equals("MINA") && le.getType() == EntityType.ZOMBIE) {
            EntityEquipment eq = le.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(rnd.nextBoolean() ? Material.IRON_PICKAXE : Material.STONE_PICKAXE));
                eq.setItemInMainHandDropChance(0f);
                eq.setHelmet(new ItemStack(Material.LEATHER_HELMET));
                eq.setHelmetDropChance(0f);
            }
        }
        m.lacaios.add(le.getUniqueId());
        return le;
    }

    /** Enche a sala de monstros quando alguem entra. */
    private void povoar(Masmorra m, int s, List<Player> ps) {
        Caixa c = m.salas.get(s);
        World w = ps.get(0).getWorld();
        int area = (c.x2() - c.x1() + 1) * (c.z2() - c.z1() + 1);
        int quantos = Math.min(10, 2 + m.tipo.nivel / 2 + ps.size() + (area > 200 ? 2 : 0));
        int feitos = 0;
        for (int tent = 0; tent < 40 && feitos < quantos; tent++) {
            int x = c.x1() + 1 + rnd.nextInt(Math.max(1, c.x2() - c.x1() - 1)), z = c.z1() + 1 + rnd.nextInt(Math.max(1, c.z2() - c.z1() - 1));
            Block b = w.getBlockAt(x, c.y1(), z);
            if (!b.isPassable() || !b.getRelative(0, 1, 0).isPassable() || b.getType() == Material.COBWEB) continue;
            boolean longe = true;
            for (Player p : ps) if (p.getLocation().distanceSquared(b.getLocation()) < 9) longe = false;
            if (!longe) continue;
            LivingEntity le = lacaio(m, m.tipo.lacaios[rnd.nextInt(m.tipo.lacaios.length)], b.getLocation().add(0.5, 0, 0.5), false);
            if (le instanceof Mob mob) mob.setTarget(ps.get(rnd.nextInt(ps.size())));
            feitos++;
        }
        if (feitos > 0) for (Player p : ps) p.playSound(p.getLocation(), "minecraft:entity.zombie.ambient", 0.6f, 0.6f);
    }

    /** Solta o chefe na arena. */
    private void chamarChefe(Masmorra m, List<Player> ps) {
        Tipo t = m.tipo;
        World w = ps.get(0).getWorld();
        Caixa a = m.arena();
        Location l = new Location(w, a.cx() + 0.5, a.y1(), a.cz() + 0.5);
        EntityType et;
        try {
            et = EntityType.valueOf(t.chefe);
        } catch (IllegalArgumentException ex) {
            return;
        }
        Entity x = w.spawn(l, et.getEntityClass(), CreatureSpawnEvent.SpawnReason.CUSTOM, y -> {
            marcarBase(y, m);
            y.getPersistentDataContainer().set(kChefe, PersistentDataType.INTEGER, m.id);
            if (kSemNomeLm != null) y.getPersistentDataContainer().set(kSemNomeLm, PersistentDataType.STRING, "masmorras");
            y.customName(Chefe.nome(t));
            y.setCustomNameVisible(true);
            if (y instanceof MagmaCube mc) mc.setSize(3);
            else if (y instanceof Slime sl) sl.setSize(3);
            if (y instanceof Hoglin hg) hg.setImmuneToZombification(true);
            if (y instanceof org.bukkit.entity.Wolf wf) wf.setAngry(true);
        });
        if (!(x instanceof LivingEntity e)) {
            x.remove();
            return;
        }
        List<Double> fv = getConfig().getDoubleList("dificuldade.vida-chefe"), fd = getConfig().getDoubleList("dificuldade.dano-chefe");
        double multVida = fv.size() >= t.nivel ? fv.get(t.nivel - 1) : 1.0, multDano = fd.size() >= t.nivel ? fd.get(t.nivel - 1) : 1.0;
        double vida = Math.min(1024, t.vida * multVida * (1 + 0.4 * (ps.size() - 1)));
        Chefe.atributo(e, Attribute.MAX_HEALTH, vida);
        e.setHealth(vida);
        if (t.escala != 1) Chefe.atributo(e, Attribute.SCALE, t.escala);
        if (t.dano > 0) Chefe.atributo(e, Attribute.ATTACK_DAMAGE, t.dano * multDano);
        Chefe.atributo(e, Attribute.KNOCKBACK_RESISTANCE, 0.7);
        Chefe.atributo(e, Attribute.FOLLOW_RANGE, 48);
        AttributeInstance arm = e.getAttribute(Attribute.ARMOR);
        if (arm != null) arm.setBaseValue(arm.getBaseValue() + 2 * t.nivel);
        if (e instanceof Ageable ag) ag.setAdult();
        if (e instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);
        EntityEquipment eq = e.getEquipment();
        if (eq != null)
            for (String s : t.equipamento) {
                String[] p = s.split(":");
                Material mat = Material.matchMaterial(p[1]);
                if (mat == null) continue;
                ItemStack it = new ItemStack(mat);
                if (mat == Material.BOW) encantar(it, "power:" + Math.min(5, t.nivel + 1));
                EquipmentSlot slot = EquipmentSlot.valueOf(p[0]);
                eq.setItem(slot, it);
                eq.setDropChance(slot, 0f);
            }
        if (e instanceof Warden wd) for (Player p : ps) wd.increaseAnger(p, 90);
        // chefe nao queima nem no magma do chao, nem no sol da entrada
        e.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        if (e instanceof org.bukkit.entity.Zombie zb) zb.setShouldBurnInDay(false);
        if (e instanceof org.bukkit.entity.AbstractSkeleton sk) sk.setShouldBurnInDay(false);
        m.chefe = new Chefe(this, m, e, vida);
        if (coroas != null && !t.secreta) coroas.vestir(e, t);
        // chegada do chefe: raio (so efeito) e explosao de particulas
        w.strikeLightningEffect(l);
        w.spawnParticle(org.bukkit.Particle.EXPLOSION, l.clone().add(0, 1, 0), 6, 1, 0.6, 1);
        if (t.secreta) herobrine(m, e);
        m.chefe.anunciar(ps);
    }

    private NamespacedKey kVisual;

    /** O chefe da secreta fica invisivel e um manequim com a cara do Steve (e olhos brancos) segue ele. */
    private void herobrine(Masmorra m, LivingEntity e) {
        if (kVisual == null) kVisual = new NamespacedKey(this, "visual");
        e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        e.setSilent(true);
        e.setCustomNameVisible(true);
        if (e instanceof org.bukkit.entity.Zombie z) {
            z.setShouldBurnInDay(false);
            z.setCanPickupItems(false);
        }
        EntityEquipment eq = e.getEquipment();
        if (eq != null) eq.clear();
        try {
            org.bukkit.entity.Mannequin mq = bonecoHerobrine(m, e.getLocation());
            m.chefe.visual = mq;
            m.chefe.visualTarefa = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (m.chefe == null) return;
                if ((m.chefe.visual == null || !m.chefe.visual.isValid()) && m.chefe.vivo() && m.chefe.e.isValid()) {
                    try {
                        m.chefe.visual = bonecoHerobrine(m, m.chefe.e.getLocation()); // o chunk descarregou: volta o boneco
                    } catch (Throwable ignored) {
                    }
                }
                m.chefe.seguirVisual();
            }, 1L, 1L);
        } catch (Throwable ex) {
            // sem manequim (versao antiga): o chefe aparece como zumbi
            e.removePotionEffect(PotionEffectType.INVISIBILITY);
            getLogger().warning("Herobrine sem manequim: " + ex);
        }
    }

    // ------------------------------------------------------------------ saida rapida (pedra-ima no centro da arena)

    private final java.util.Set<Integer> saidaPronta = new java.util.HashSet<>();
    private final Map<UUID, Long> saiuEm = new HashMap<>();

    /** Poe a pedra-ima no centro da arena (uma vez por masmorra) e, sem chefe vivo, mostra que ela leva para a entrada. */
    private final java.util.Set<Integer> iluminada = new java.util.HashSet<>();

    /**
     * Um pouco mais de luz nas salas e na arena: blocos de luz invisiveis (minecraft:light) no ar, em grade,
     * sem mudar a aparencia. So poe onde e ar; uma vez por masmorra a cada vez que o servidor liga.
     */
    private void iluminar(Masmorra m, World w) {
        if (!iluminada.add(m.id)) return;
        int nivelSala = getConfig().getInt("luz-extra.sala", 12), nivelArena = getConfig().getInt("luz-extra.arena", 15);
        if (nivelSala <= 0 && nivelArena <= 0) return;
        int postos = 0;
        for (int i = 0; i < m.salas.size(); i++) {
            Caixa c = m.salas.get(i);
            boolean arena = i == m.salas.size() - 1;
            int nivel = Math.min(15, arena ? nivelArena : nivelSala);
            if (nivel <= 0) continue;
            int altura = c.y2() - c.y1();
            List<Integer> ys = new ArrayList<>();
            if (arena) for (int y = c.y1() + 3; y < c.y2() - 1; y += 4) ys.add(y);
            if (ys.isEmpty()) ys.add(c.y1() + Math.max(2, Math.min(4, altura - 2)));
            int passo = arena ? 4 : 4;
            for (int x = c.x1() + 2; x <= c.x2() - 2; x += passo)
                for (int z = c.z1() + 2; z <= c.z2() - 2; z += passo)
                    for (int y : ys) {
                        if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                        Block b = w.getBlockAt(x, y, z);
                        Material tipo = b.getType();
                        if (tipo != Material.AIR && tipo != Material.CAVE_AIR && tipo != Material.LIGHT) continue;
                        if (tipo == Material.LIGHT && ((org.bukkit.block.data.type.Light) b.getBlockData()).getLevel() >= nivel) continue;
                        org.bukkit.block.data.type.Light luz = (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
                        luz.setLevel(nivel);
                        b.setBlockData(luz, false);
                        postos++;
                    }
        }
        if (postos > 0) getLogger().info("Masmorra #" + m.id + ": " + postos + " pontos de luz a mais.");
    }

    private void saidaRapida(Masmorra m, World w, List<Player> dentro, long tick) {
        iluminar(m, w);
        Caixa a = m.arena();
        if (!saidaPronta.contains(m.id) && w.isChunkLoaded(a.cx() >> 4, a.cz() >> 4)) {
            Block b = w.getBlockAt(a.cx(), a.y1(), a.cz());
            if (b.getType() != Material.LODESTONE) b.setType(Material.LODESTONE, false);
            Block cima = b.getRelative(org.bukkit.block.BlockFace.UP);
            if (!cima.getType().isSolid() && !cima.getType().isAir()) cima.setType(Material.AIR, false);
            saidaPronta.add(m.id);
        }
        if (m.chefe != null || m.intro != null || tick % 2 != 0) return;
        Location c = new Location(w, a.cx() + 0.5, a.y1() + 1.1, a.cz() + 0.5);
        w.spawnParticle(Particle.PORTAL, c, 25, 0.3, 0.6, 0.3, 0.4);
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.8, 0), 2, 0.1, 0.3, 0.1, 0.01);
        if (m.bossVolta != 0 && tick % 6 == 0)
            for (Player p : dentro)
                if (a.contem(p.getX(), p.getY(), p.getZ())) p.sendActionBar(txt("§a✦ Pise na §fpedra-ímã §ano centro para voltar à entrada"));
    }

    @EventHandler(ignoreCancelled = true)
    public void pisouNaSaida(org.bukkit.event.player.PlayerMoveEvent e) {
        Location to = e.getTo(), de = e.getFrom();
        if (to.getBlockX() == de.getBlockX() && to.getBlockY() == de.getBlockY() && to.getBlockZ() == de.getBlockZ()) return;
        Block b = to.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
        if (b.getType() != Material.LODESTONE) return;
        Player p = e.getPlayer();
        String mundo = b.getWorld().getName();
        for (Masmorra m : porId.values()) {
            if (!m.mundo.equals(mundo)) continue;
            Caixa a = m.arena();
            if (b.getX() != a.cx() || b.getY() != a.y1() || b.getZ() != a.cz()) continue;
            if (m.chefe != null) {
                p.sendActionBar(txt("§cA saída só funciona sem o chefe na arena!"));
                return;
            }
            long agora = System.currentTimeMillis();
            Long antes = saiuEm.get(p.getUniqueId());
            if (antes != null && agora - antes < 3000) return;
            saiuEm.put(p.getUniqueId(), agora);
            World w = Bukkit.getWorld(m.mundo);
            if (w == null) return;
            Location destino = new Location(w, m.saidaX + 0.5, m.saidaY, m.saidaZ + 0.5, p.getLocation().getYaw(), 0f);
            w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
            w.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.setFallDistance(0);
            p.teleportAsync(destino, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
                if (!ok) return;
                p.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                p.sendActionBar(txt("§aVocê voltou para a entrada da masmorra."));
            });
            return;
        }
    }

    /** O boneco com a cara do Herobrine (corpo do Steve + cabeca do Herobrine). Nao fica salvo no mundo. */
    org.bukkit.entity.Mannequin bonecoHerobrine(Masmorra m, Location l) {
        if (kVisual == null) kVisual = new NamespacedKey(this, "visual");
        return l.getWorld().spawn(l, org.bukkit.entity.Mannequin.class, CreatureSpawnEvent.SpawnReason.CUSTOM, y -> {
            marcar(y, m);
            y.getPersistentDataContainer().set(kVisual, PersistentDataType.INTEGER, m.id);
            y.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile()
                    .name("Herobrine").uuid(UUID.nameUUIDFromBytes("corona-herobrine".getBytes()))
                    .skinPatch(sp -> sp.body(net.kyori.adventure.key.Key.key("minecraft", "entity/player/wide/steve"))
                            .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC))
                    .build());
            y.setImmovable(true);
            y.setGravity(false);
            y.setCollidable(false);
            y.setSilent(true);
            y.setPersistent(false);
            y.setCustomNameVisible(false);
            y.setDescription(Component.empty());
            if (y.getEquipment() != null) y.getEquipment().setHelmet(cabecaHerobrine());
        });
    }

    static final String[] SUSSURROS = {"§8§o...eu estou aqui há muito tempo.", "§8§o...vocês não deviam ter vindo.", "§8§o...eu vejo vocês.",
            "§8§o...eu nunca fui embora.", "§4§o...atrás de você."};

    /** Jogadores (fora do criativo/espectador) dentro da arena agora. */
    private List<Player> naArena(Masmorra m) {
        List<Player> l = new ArrayList<>();
        World w = Bukkit.getWorld(m.mundo);
        if (w == null) return l;
        for (Player p : w.getPlayers())
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR && !p.isDead() && m.arena().contem(p.getX(), p.getY(), p.getZ())) l.add(p);
        return l;
    }

    /**
     * Antes do Herobrine: 24 s com o disco 11 tocando (a musica mais sinistra do jogo), escuridao, coracao batendo,
     * sussurros, vultos dele aparecendo e sumindo pela arena, relampagos e apagao. Depois ele chega com tudo.
     * Se todo mundo sair da arena no meio, para e recomeca quando alguem entrar de novo.
     */
    void introHerobrine(Masmorra m) {
        if (m.intro != null || m.chefe != null) return;
        World w = Bukkit.getWorld(m.mundo);
        if (w == null) return;
        Caixa a = m.arena();
        Location centro = new Location(w, a.cx() + 0.5, a.y1(), a.cz() + 0.5);
        java.util.Random r = new java.util.Random();
        int[] k = {0};
        List<org.bukkit.entity.Mannequin> vultos = new ArrayList<>();
        java.util.Set<Player> ouvindo = new java.util.HashSet<>();
        Runnable pararMusica = () -> {
            for (Player p : ouvindo) if (p.isOnline()) p.stopSound(Sound.MUSIC_DISC_11);
        };
        m.intro = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int i = k[0]++;
            List<Player> ps = naArena(m);
            if (ps.isEmpty() || m.chefe != null) {
                pararMusica.run();
                for (org.bukkit.entity.Mannequin v : vultos) v.remove();
                for (Player p : ouvindo) if (p.isOnline()) p.removePotionEffect(PotionEffectType.DARKNESS);
                if (m.intro != null) m.intro.cancel();
                m.intro = null;
                return;
            }
            for (Player p : ps) {
                if (ouvindo.add(p)) {
                    p.playSound(p, Sound.MUSIC_DISC_11, org.bukkit.SoundCategory.MASTER, 1f, 0.9f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 30, 0, false, false));
                    p.showTitle(net.kyori.adventure.title.Title.title(txt("§4§k||| §4§l??? §4§k|||"), txt("§7Você não deveria estar aqui...")));
                }
            }
            // coracao: mais rapido no fim
            if ((i < 24 && i % 4 == 0) || (i >= 24 && i < 44 && i % 2 == 0))
                for (Player p : ps) p.playSound(p, Sound.ENTITY_WARDEN_HEARTBEAT, org.bukkit.SoundCategory.MASTER, 0.6f + i / 60f, 1f);
            if (i == 8 || i == 18 || i == 26 || i == 33 || i == 40) {
                String s = SUSSURROS[i == 8 ? 0 : i == 18 ? 1 : i == 26 ? 2 : i == 33 ? 3 : 4];
                for (Player p : ps) {
                    p.sendMessage(txt(s));
                    p.playSound(p, Sound.AMBIENT_CAVE, org.bukkit.SoundCategory.MASTER, 1f, 0.6f);
                }
            }
            // vultos: aparece num canto da arena olhando para alguem e some
            if (i == 12 || i == 22 || i == 30 || i == 37 || i == 42) {
                Player alvo = ps.get(r.nextInt(ps.size()));
                double ang = r.nextDouble() * Math.PI * 2, raio = Math.max(3, Math.min(a.x2() - a.x1(), a.z2() - a.z1()) / 2.0 - 2);
                Location v = centro.clone().add(Math.cos(ang) * raio, 0, Math.sin(ang) * raio);
                v.setDirection(alvo.getLocation().toVector().subtract(v.toVector()));
                try {
                    org.bukkit.entity.Mannequin mq = bonecoHerobrine(m, v);
                    vultos.add(mq);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        w.spawnParticle(Particle.LARGE_SMOKE, mq.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                        mq.remove();
                    }, i >= 37 ? 8L : 14L);
                } catch (Throwable ignored) {
                }
                for (Player p : ps) p.playSound(v, Sound.ENTITY_ENDERMAN_STARE, org.bukkit.SoundCategory.MASTER, 0.5f, 0.5f);
            }
            if (i == 34 || i == 38 || i == 41) {
                Location rl = centro.clone().add(r.nextInt(9) - 4, 0, r.nextInt(9) - 4);
                w.strikeLightningEffect(rl);
                for (Player p : ps) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 12, 0, false, false));
            }
            if (i == 44) { // silencio total
                pararMusica.run();
                for (Player p : ps) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));
                    p.playSound(p, Sound.ENTITY_ELDER_GUARDIAN_CURSE, org.bukkit.SoundCategory.MASTER, 1f, 0.5f);
                    p.showTitle(net.kyori.adventure.title.Title.title(txt(" "), txt("§8§o...shh.")));
                }
            }
            if (i >= 49) { // ele chega
                m.intro.cancel();
                m.intro = null;
                for (int j = 0; j < 8; j++) {
                    double an = j * Math.PI / 4;
                    w.strikeLightningEffect(centro.clone().add(Math.cos(an) * 5, 0, Math.sin(an) * 5));
                }
                w.strikeLightningEffect(centro);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, centro.clone().add(0, 1, 0), 2);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, centro.clone().add(0, 1, 0), 300, 0.4, 0.4, 0.4, 0.35);
                w.spawnParticle(Particle.LARGE_SMOKE, centro.clone().add(0, 1, 0), 120, 3, 1.5, 3, 0.05);
                for (Player p : ps) {
                    p.removePotionEffect(PotionEffectType.DARKNESS);
                    p.removePotionEffect(PotionEffectType.BLINDNESS);
                    p.playSound(p, Sound.ENTITY_WITHER_SPAWN, org.bukkit.SoundCategory.MASTER, 1f, 0.6f);
                    p.playSound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, org.bukkit.SoundCategory.MASTER, 1f, 0.5f);
                    p.showTitle(net.kyori.adventure.title.Title.title(txt("§f§lHEROBRINE"), txt("§4§lele viu você.")));
                    org.bukkit.util.Vector v = p.getLocation().toVector().subtract(centro.toVector()).setY(0);
                    if (v.lengthSquared() > 0.01) p.setVelocity(v.normalize().multiply(0.6).setY(0.35));
                }
                chamarChefe(m, ps);
            }
        }, 20L, 10L);
    }

    /** Textura da cabeca do Herobrine (servidor de skins da Mojang). */
    static final String TEXTURA_HEROBRINE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjQ4ZDU1YjMzZWQ2ZmViNjE0ZTJjYTVkNGY1MGJiMzdmMTYxYWRhMzU4MmZjZmM2ZTQwMjg4YzZmYjA2ZjFmIn19fQ==";

    static ItemStack cabecaHerobrine() {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            com.destroystokyo.paper.profile.PlayerProfile pp = Bukkit.createProfile(UUID.nameUUIDFromBytes("corona-herobrine-cabeca".getBytes()), "Herobrine");
            pp.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", TEXTURA_HEROBRINE));
            sm.setPlayerProfile(pp);
            it.setItemMeta(sm);
        }
        return it;
    }

    /** Bater no manequim do Herobrine vale como bater no chefe. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void danoVisual(org.bukkit.event.entity.EntityDamageEvent e) {
        if (kVisual == null || !e.getEntity().getPersistentDataContainer().has(kVisual, PersistentDataType.INTEGER)) return;
        e.setCancelled(true);
        Integer id = e.getEntity().getPersistentDataContainer().get(kVisual, PersistentDataType.INTEGER);
        Masmorra m = id == null ? null : porId.get(id);
        if (m == null || m.chefe == null || !m.chefe.vivo()) return;
        if (e instanceof EntityDamageByEntityEvent d) {
            Player a = jogadorDe(d.getDamager());
            if (a != null) m.chefe.e.damage(e.getDamage(), a);
        }
    }

    void limpar(Masmorra m) {
        if (m.intro != null) {
            m.intro.cancel();
            m.intro = null;
        }
        if (m.chefe != null) {
            m.chefe.remover();
            m.chefe = null;
        }
        for (Entity x : lacaiosVivos(m)) x.remove();
        m.lacaios.clear();
        m.semNinguemDesde = -1;
    }

    /** Volta tudo: salas com monstros, baus cheios e chefe esperando. */
    void resetar(Masmorra m) {
        java.util.Arrays.fill(m.armada, true);
        World w = Bukkit.getWorld(m.mundo);
        if (w != null) {
            for (int[] b : m.baus) {
                if (w.isChunkLoaded(b[0] >> 4, b[2] >> 4)) porBau(w, b, m.tipo.tabela, false);
                else bausPendentes.put(m.id, true);
            }
        }
        m.bossVolta = 0;
        salvar();
    }

    /** Baus que nao estavam carregados na hora do reset (enche quando alguem chegar perto). */
    private final Map<Integer, Boolean> bausPendentes = new HashMap<>();

    // ================================================================== ciclo (a cada meio segundo)

    private void ciclo() {
        tick++;
        long agora = System.currentTimeMillis();
        for (Masmorra m : todas) {
            World w = Bukkit.getWorld(m.mundo);
            if (w == null || m.tudo == null || m.demolindo) continue;
            Caixa perto = m.tudo.crescer(24);
            List<Player> ps = new ArrayList<>();
            for (Player p : w.getPlayers())
                if (p.getGameMode() != GameMode.SPECTATOR && perto.contem(p.getX(), p.getY(), p.getZ())) ps.add(p);
            if (ps.isEmpty()) {
                if (m.chefe != null || !m.lacaios.isEmpty()) {
                    if (m.semNinguemDesde < 0) m.semNinguemDesde = agora;
                    else if (agora - m.semNinguemDesde > 90_000) limpar(m);
                }
                if (m.bossVolta > 0 && agora >= m.bossVolta) resetar(m);
                continue;
            }
            m.semNinguemDesde = -1;
            if (bausPendentes.remove(m.id) != null) for (int[] b : m.baus) porBau(w, b, m.tipo.tabela, false);
            List<Player> dentro = new ArrayList<>();
            for (Player p : ps) if (p.getGameMode() != GameMode.CREATIVE && m.dentro(p.getX(), p.getY(), p.getZ())) dentro.add(p);
            for (Player p : dentro) {
                int s = m.sala(p.getX(), p.getY(), p.getZ());
                if (s >= 0 && s < m.salas.size() - 1 && m.armada[s]) {
                    m.armada[s] = false;
                    povoar(m, s, dentro);
                }
            }
            if (m.chefe != null) {
                if (!m.chefe.tick()) {
                    m.chefe.remover();
                    m.chefe = null;
                }
            } else if (m.bossVolta == 0) {
                List<Player> naArena = new ArrayList<>();
                for (Player p : dentro) if (m.arena().contem(p.getX(), p.getY(), p.getZ())) naArena.add(p);
                if (!naArena.isEmpty()) {
                    if (m.tipo.secreta) introHerobrine(m);
                    else chamarChefe(m, naArena);
                }
            } else if (agora >= m.bossVolta && dentro.isEmpty()) resetar(m);
            if (!dentro.isEmpty()) saidaRapida(m, w, dentro, tick);
            if (tick % 4 == 0) vigiar(m, w);
        }
    }

    /** Monstros da masmorra nao saem dela (voltam para a primeira sala). */
    private void vigiar(Masmorra m, World w) {
        for (Entity x : lacaiosVivos(m)) {
            if (m.dentro(x.getX(), x.getY() + 0.5, x.getZ())) continue;
            Caixa s = m.salas.get(0);
            x.teleport(new Location(w, s.cx() + 0.5, s.y1(), s.cz() + 0.5));
        }
    }

    // ================================================================== eventos: chefe morto, dano, alvo

    /** Quem comprou o Herobrine antigo (visual "secreta") ganha o visual novo ao entrar. */
    @EventHandler
    public void atualizarHerobrine(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            MoedaReviver.entregarPendentes(this, p);
            NamespacedKey kc = new NamespacedKey(this, "cosmetico");
            ItemStack[] c = p.getInventory().getContents();
            int n = 0;
            for (ItemStack it : c) {
                if (it == null || !it.hasItemMeta()) continue;
                String tema = it.getItemMeta().getPersistentDataContainer().get(kc, PersistentDataType.STRING);
                if (!"secreta".equals(tema)) continue;
                org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
                im.getPersistentDataContainer().set(kc, PersistentDataType.STRING, "herobrine");
                it.setItemMeta(im);
                Reliquias.visualDoPacote(it, "herobrine", getLogger());
                n++;
            }
            if (n > 0) {
                p.getInventory().setContents(c);
                getLogger().info(p.getName() + ": " + n + " peca(s) do Herobrine atualizada(s) para o visual novo.");
            }
        }, 60L);
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        Integer id = e.getEntity().getPersistentDataContainer().get(kChefe, PersistentDataType.INTEGER);
        if (id == null) {
            Masmorra de = de(e.getEntity());
            Player k = e.getEntity().getKiller();
            // dentro das masmorras os monstros nao deixam os drops normais (carne podre, ossos, flechas, fios...):
            // so o que a masmorra da (reliquias). Fora delas fica tudo igual.
            if (getConfig().getBoolean("sem-drops-normais", true) && !(e.getEntity() instanceof Player)) {
                Location ml = e.getEntity().getLocation();
                boolean dentroDe = de != null || e.getEntity() instanceof org.bukkit.entity.Enemy && masmorraEm(ml.getWorld(), ml.getBlockX(), ml.getBlockY(), ml.getBlockZ()) != null;
                if (Boolean.getBoolean("poderes.debug"))
                    getLogger().info("[debug] morreu " + e.getEntity().getType() + " de=" + (de != null) + " dentro=" + dentroDe + " drops=" + e.getDrops().size());
                if (dentroDe) e.getDrops().clear();
            }
            if (de != null && k != null) {
                if (rnd.nextDouble() < getConfig().getDouble("reliquias.chance-monstro", 0.025)) {
                    ItemStack rel = item(Premios.equipamento(de.tipo.nivel, rnd));
                    if (rel != null) {
                        e.getDrops().add(reliquias.aplicar(rel, de.tipo));
                        k.sendMessage(txt(PREFIXO + "§d✦ O monstro deixou cair uma §lrelíquia§d! §7(" + (de.tipo.secreta ? "???" : de.tipo.nome) + ")"));
                        k.playSound(k.getLocation(), "minecraft:block.amethyst_block.chime", 1f, 1.2f);
                    }
                }
                darXpClasses(k, getConfig().getDouble("xp-classes.monstro", 12) * de.tipo.nivel, false);
                if (missoes != null) missoes.monstroMorto(k, de.tipo.nivel);
                coronas.dar(k, coronas.comVip(k, getConfig().getLong("coronas.monstro", 3) * de.tipo.nivel + rnd.nextInt(de.tipo.nivel + 1)), true);
            }
            return;
        }
        Masmorra m = porId.get(id);
        if (m == null) return;
        Tipo t = m.tipo;
        e.getDrops().clear();
        for (Premios.Item it : Premios.sortear(t.nivel, rnd)) {
            ItemStack s = item(it);
            if (s != null) e.getDrops().add(reliquias.aplicar(s, t));
        }
        e.setDroppedExp(Premios.xp(t.nivel));
        if (m.chefe != null) m.chefe.esconderBarra();
        m.chefe = null;
        m.bossVolta = System.currentTimeMillis() + getConfig().getLong("chefe-volta-minutos", 60) * 60_000L;
        salvar();
        Player k = e.getEntity().getKiller();
        String quem = k != null ? k.getName() : "Alguém";
        Bukkit.broadcast(txt(PREFIXO + "§e" + quem + " §7derrotou §c" + t.chefeNome + " §7(Nível " + t.nivel + ") na §f" + t.nome + "§7!"));
        // XP de habilidade (nivel de poder das classes) para quem lutou: quem esta na arena e quem matou
        Set<Player> lutaram = new HashSet<>();
        Caixa ar = m.arena().crescer(3);
        for (Player p : e.getEntity().getWorld().getPlayers())
            if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && ar.contem(p.getX(), p.getY(), p.getZ())) lutaram.add(p);
        if (k != null) lutaram.add(k);
        double xp = xpChefe(t.nivel) * (t.secreta ? 2 : 1);
        for (Player p : lutaram) darXpClasses(p, xp, true);
        if (ranking != null) for (Player p : lutaram) ranking.chefeDerrotado(p);
        if (missoes != null) for (Player p : lutaram) missoes.chefeMorto(p, t.nivel);
        long cor = t.secreta ? getConfig().getLong("coronas.secreta", 5000) : getConfig().getLong("coronas.chefe", 250) * t.nivel;
        for (Player p : lutaram) {
            coronas.dar(p, coronas.comVip(p, cor), false);
            p.sendMessage(txt(PREFIXO + "§6+" + Coronas.fmt(cor) + " coronas §7pelo chefe! Saldo: §6" + Coronas.fmt(coronas.saldo(p)) + " §7(gaste com os §6Mercadores§7)"));
        }
        Location l = e.getEntity().getLocation();
        l.getWorld().playSound(l, "minecraft:ui.toast.challenge_complete", 1f, 1f);
        getLogger().info("Chefe da masmorra #" + m.id + " derrotado por " + quem + ".");
    }

    private Masmorra dono(Entity x) {
        if (x instanceof Projectile pr && pr.getShooter() instanceof Entity s) {
            Masmorra m = de(pr);
            return m != null ? m : de(s);
        }
        if (x instanceof TNTPrimed tnt) {
            Masmorra m = de(tnt);
            return m != null ? m : de(tnt.getSource());
        }
        return de(x);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void dano(EntityDamageByEntityEvent e) {
        Masmorra vitima = de(e.getEntity()), atacante = dono(e.getDamager());
        if (vitima != null && vitima == atacante) {
            e.setCancelled(true);
            return;
        }
        if (atacante != null && atacante.tipo.veneno && e.getEntity() instanceof Player p && !(e.getDamager() instanceof Projectile))
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
    }

    /** Escudo (chefe imune), reflexo de projeteis e roubo de vida. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void especiais(EntityDamageEvent e) {
        Integer id = e.getEntity().getPersistentDataContainer().get(kChefe, PersistentDataType.INTEGER);
        Masmorra m = id == null ? null : porId.get(id);
        if (m != null && m.chefe != null && m.chefe.e.equals(e.getEntity())) {
            Chefe c = m.chefe;
            if (c.imune()) {
                e.setCancelled(true);
                if (e instanceof EntityDamageByEntityEvent d && jogadorDe(d.getDamager()) instanceof Player pp)
                    pp.sendActionBar(txt("§b§lEscudo! §7Mate os guardas brilhando primeiro."));
                return;
            }
            // volta uma vez com 40% da vida
            if (m.tipo.esp == Tipo.Esp.REVIVER && !c.reviveu && e.getFinalDamage() >= c.e.getHealth()) {
                e.setCancelled(true);
                c.reviveu = true;
                c.e.setHealth(Math.min(c.vidaMax, c.vidaMax * 0.4));
                c.e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 4));
                c.e.getWorld().strikeLightningEffect(c.e.getLocation());
                c.e.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, c.e.getLocation().add(0, c.e.getHeight() / 2, 0), 80, 0.6, 1, 0.6, 0.4);
                c.e.getWorld().playSound(c.e.getLocation(), "minecraft:item.totem.use", 1.2f, 0.7f);
                for (Player p : c.naArena()) p.sendMessage(txt("§4§l" + m.tipo.chefeNome + " voltou à vida! §7Termine o serviço!"));
                return;
            }
            // pele de espinhos: quem bate de perto leva parte do dano
            if (m.tipo.esp == Tipo.Esp.ESPINHOS_PELE && e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player bat) {
                double volta = Math.min(8, e.getDamage() * 0.25);
                Bukkit.getScheduler().runTask(this, () -> {
                    if (bat.isOnline() && !bat.isDead()) bat.damage(volta, c.e);
                });
                c.e.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, bat.getLocation().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0.1);
            }
            if (m.tipo.esp == Tipo.Esp.REFLEXO && e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Projectile pr
                    && pr.getShooter() instanceof Player atirador && rnd.nextDouble() < 0.35) {
                e.setCancelled(true);
                pr.remove();
                atirador.damage(Math.min(10, e.getDamage() * 0.5), c.e);
                c.e.getWorld().spawnParticle(Particle.ENCHANTED_HIT, c.e.getLocation().add(0, c.e.getHeight() / 2, 0), 20, 0.5, 0.5, 0.5, 0.3);
                c.e.getWorld().playSound(c.e.getLocation(), "minecraft:item.shield.block", 1f, 0.8f);
                atirador.sendActionBar(txt("§e" + m.tipo.chefeNome + " rebateu seu ataque!"));
            }
            return;
        }
        if (e instanceof EntityDamageByEntityEvent d && e.getEntity() instanceof Player) {
            Integer id2 = d.getDamager().getPersistentDataContainer().get(kChefe, PersistentDataType.INTEGER);
            Masmorra m2 = id2 == null ? null : porId.get(id2);
            if (m2 != null && m2.chefe != null && m2.tipo.esp == Tipo.Esp.VAMPIRO && m2.chefe.vivo()) {
                LivingEntity b = m2.chefe.e;
                double nova = Math.min(m2.chefe.vidaMax, b.getHealth() + e.getFinalDamage() * 0.3);
                b.setHealth(Math.max(0.5, Math.min(nova, b.getAttribute(Attribute.MAX_HEALTH).getValue())));
                b.getWorld().spawnParticle(Particle.HEART, b.getLocation().add(0, b.getHeight(), 0), 2, 0.3, 0.2, 0.3, 0);
            }
        }
    }

    /** Gosmas e cubos de magma das masmorras (e o chefe) nao se dividem ao morrer. */
    @EventHandler(ignoreCancelled = true)
    public void dividir(org.bukkit.event.entity.SlimeSplitEvent e) {
        if (de(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alvo(EntityTargetLivingEntityEvent e) {
        Masmorra a = de(e.getEntity()), b = e.getTarget() == null ? null : de(e.getTarget());
        if (a != null && a == b) e.setCancelled(true);
    }

    /** XP de habilidade que o chefe da (por nivel, do config). */
    double xpChefe(int nivel) {
        List<Integer> l = getConfig().getIntegerList("xp-classes.chefe");
        int[] padrao = {3000, 6000, 10000, 15000, 22000};
        int i = Math.max(0, Math.min(4, nivel - 1));
        return i < l.size() ? l.get(i) : padrao[i];
    }

    /** Divide a XP entre as skills do config (padrao: 60% Luta, 40% Defesa). */
    void darXpClasses(Player p, double xp, boolean avisar) {
        if (!getConfig().getBoolean("xp-classes.ativo", true) || xp <= 0) return;
        ConfigurationSection sk = getConfig().getConfigurationSection("xp-classes.skills");
        Map<String, Double> partes = new LinkedHashMap<>();
        if (sk != null) for (String k : sk.getKeys(false)) partes.put(k.toUpperCase(Locale.ROOT), sk.getDouble(k));
        if (partes.isEmpty()) {
            partes.put("FIGHTING", 0.3);
            partes.put("DEFENSE", 0.25);
            partes.put("ENDURANCE", 0.15);
            partes.put("AGILITY", 0.15);
            partes.put("ARCHERY", 0.15);
        }
        int antes = avisar ? Aura.poder(p) : -1;
        boolean deu = false;
        for (Map.Entry<String, Double> e : partes.entrySet()) if (e.getValue() > 0) deu |= Aura.dar(p, e.getKey(), xp * e.getValue());
        if (deu && avisar)
            depois(5, () -> {
                if (!p.isOnline()) return;
                int agora = Aura.poder(p);
                String sobe = antes >= 0 && agora >= 0 ? "§fNível de poder: §e" + antes + " §f→ §a§l" + agora + (agora > antes ? " §a(+" + (agora - antes) + ")" : "") : "§fseu nível de poder subiu";
                p.sendMessage(txt(PREFIXO + "§b+" + (int) Math.round(xp) + " XP de habilidade! " + sobe + " §8(/poderes)"));
                p.showTitle(net.kyori.adventure.title.Title.title(txt("§b§lPODER " + (agora >= 0 ? agora : "")), txt(agora > antes && antes >= 0 ? "§a+" + (agora - antes) + " de poder" : "§7XP de habilidade recebida")));
            });
    }

    // ================================================================== sem PvP nas masmorras

    /** O lugar e dentro de alguma masmorra (ou da blindagem dela)? La nao tem PvP. */
    public boolean semPvp(Location l) {
        if (l == null || l.getWorld() == null || !getConfig().getBoolean("sem-pvp", true)) return false;
        String w = l.getWorld().getName();
        if (w.equals(Hub.MUNDO)) return true;
        for (Masmorra m : todas) if (m.mundo.equals(w) && m.naArea(l.getX(), l.getY(), l.getZ())) return true;
        return false;
    }

    /** O jogador por tras do ataque (ele mesmo, a flecha dele, a TNT dele, o lobo dele...). */
    static Player jogadorDe(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (d instanceof AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        if (d instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;
        return null;
    }

    private boolean pvpBloqueado(Player a, Player v) {
        return a != null && v != null && !a.equals(v) && (semPvp(v.getLocation()) || semPvp(a.getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void pvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Player a = jogadorDe(e.getDamager());
        if (!pvpBloqueado(a, v)) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Projectile pr) pr.remove();
        a.sendActionBar(txt("§7Nas masmorras não tem PvP."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void pvpFogo(EntityCombustByEntityEvent e) {
        if (e.getEntity() instanceof Player v && pvpBloqueado(jogadorDe(e.getCombuster()), v)) e.setCancelled(true);
    }

    static boolean nocivo(java.util.Collection<PotionEffect> efeitos) {
        for (PotionEffect pe : efeitos) {
            try {
                if (pe.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) return true;
            } catch (Throwable t) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void pvpPocao(PotionSplashEvent e) {
        ThrownPotion tp = e.getPotion();
        if (!(tp.getShooter() instanceof Player a) || !nocivo(tp.getEffects())) return;
        for (LivingEntity le : e.getAffectedEntities()) if (le instanceof Player v && pvpBloqueado(a, v)) e.setIntensity(le, 0);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void pvpNuvem(AreaEffectCloudApplyEvent e) {
        AreaEffectCloud c = e.getEntity();
        if (!(c.getSource() instanceof Player a)) return;
        List<PotionEffect> ef = new ArrayList<>(c.getCustomEffects());
        if (c.getBasePotionType() != null) ef.addAll(c.getBasePotionType().getPotionEffects());
        if (!nocivo(ef)) return;
        e.getAffectedEntities().removeIf(le -> le instanceof Player v && pvpBloqueado(a, v));
    }

    ItemStack item(Premios.Item it) {
        Material mat = Material.matchMaterial(it.material());
        if (mat == null) return null;
        ItemStack s = new ItemStack(mat, Math.max(1, Math.min(mat.getMaxStackSize(), it.qtd())));
        for (String en : it.encantos()) {
            if (it.livro()) {
                String[] p = en.split(":");
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(p[0]));
                if (ench != null && s.getItemMeta() instanceof EnchantmentStorageMeta esm) {
                    esm.addStoredEnchant(ench, Integer.parseInt(p[1]), true);
                    s.setItemMeta(esm);
                }
            } else encantar(s, en);
        }
        return s;
    }

    static void encantar(ItemStack s, String en) {
        String[] p = en.split(":");
        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(p[0]));
        if (ench == null) return;
        ItemMeta m = s.getItemMeta();
        if (m == null) return;
        m.addEnchant(ench, Integer.parseInt(p[1]), true);
        s.setItemMeta(m);
    }

    // ================================================================== protecao dos blocos

    Masmorra protegendo(World w, int x, int y, int z) {
        for (Masmorra m : todas) if (m.mundo.equals(w.getName()) && m.protege(x, y, z)) return m;
        return null;
    }

    private boolean protegido(Block b) {
        return protegendo(b.getWorld(), b.getX(), b.getY(), b.getZ()) != null;
    }

    /** So quem tem masmorras.admin E ligou o modo de edicao (/masmorras admin editar) mexe nos blocos. */
    private boolean admin(Player p) {
        return editando.contains(p.getUniqueId()) && p.hasPermission("masmorras.admin");
    }

    boolean podeEditar(Player p) {
        return admin(p);
    }

    private static boolean tocha(Material m) {
        return m == Material.TORCH || m == Material.WALL_TORCH || m == Material.SOUL_TORCH || m == Material.SOUL_WALL_TORCH;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void quebrar(BlockBreakEvent e) {
        if (admin(e.getPlayer()) || tocha(e.getBlock().getType()) || !protegido(e.getBlock())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(txt("§7As paredes da masmorra não quebram."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void colocar(BlockPlaceEvent e) {
        if (admin(e.getPlayer()) || tocha(e.getBlock().getType()) || !protegido(e.getBlock())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(txt("§7Na masmorra só dá para colocar tochas."));
    }

    @EventHandler(ignoreCancelled = true)
    public void explodir(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protegido);
    }

    @EventHandler(ignoreCancelled = true)
    public void explodirBloco(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protegido);
    }

    @EventHandler(ignoreCancelled = true)
    public void queimar(BlockBurnEvent e) {
        if (protegido(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fogo(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && e.getPlayer() != null && admin(e.getPlayer())) return;
        if (protegido(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void liquido(BlockFromToEvent e) {
        if (protegido(e.getToBlock()) && !protegido(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistao(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (protegido(b) || protegido(b.getRelative(e.getDirection()))) {
            e.setCancelled(true);
            return;
        }
        if (protegido(e.getBlock().getRelative(e.getDirection()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistaoVolta(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (protegido(b)) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void mudarBloco(EntityChangeBlockEvent e) {
        if (protegido(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void derreter(BlockFadeEvent e) {
        if (protegido(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void espalhar(BlockSpreadEvent e) {
        if (protegido(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void balde(PlayerBucketEmptyEvent e) {
        if (!admin(e.getPlayer()) && protegido(e.getBlockClicked().getRelative(e.getBlockFace()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void baldeEncher(PlayerBucketFillEvent e) {
        if (!admin(e.getPlayer()) && protegido(e.getBlockClicked())) e.setCancelled(true);
    }

    // ================================================================== mapa-mundi

    Tipo.Onde ondeDe(World w) {
        for (Tipo.Onde o : Tipo.Onde.values()) if (nomeMundo(o).equals(w.getName())) return o;
        return null;
    }

    void configurarMapa(MapView v, World w) {
        Tipo.Onde o = ondeDe(w);
        if (o == null) return;
        for (org.bukkit.map.MapRenderer r : v.getRenderers()) if (r instanceof Mapa) return;
        for (org.bukkit.map.MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setScale(MapView.Scale.FARTHEST);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.addRenderer(new Mapa(this, w, borda(o) / 2));
    }

    @EventHandler
    public void mapaCarregado(MapInitializeEvent e) {
        MapView v = e.getMap();
        ConfigurationSection ms = banco.getConfigurationSection("mapas");
        if (ms == null) return;
        for (String k : ms.getKeys(false))
            if (ms.getInt(k) == v.getId()) {
                World w = Bukkit.getWorld(k);
                if (w != null) configurarMapa(v, w);
            }
    }

    /** O mapa do mundo (um so para todos; cada um ve a propria seta). */
    MapView mapaDe(World w) {
        int id = banco.getInt("mapas." + w.getName(), -1);
        MapView v = id >= 0 ? Bukkit.getMap(id) : null;
        if (v == null) {
            v = Bukkit.createMap(w);
            banco.set("mapas." + w.getName(), v.getId());
            salvar();
        }
        configurarMapa(v, w);
        return v;
    }

    private void darMapa(Player p) {
        World w = p.getWorld();
        if (ondeDe(w) == null) {
            p.sendMessage(txt(PREFIXO + "O mapa das masmorras é do mundo normal, do Nether e do End."));
            return;
        }
        MapView v = mapaDe(w);
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.getItemMeta() instanceof MapMeta mm && mm.hasMapView() && mm.getMapView() != null && mm.getMapView().getId() == v.getId()) {
                p.sendMessage(txt(PREFIXO + "Você já tem o mapa das masmorras deste mundo no inventário."));
                return;
            }
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        if (it.getItemMeta() instanceof MapMeta mm) {
            mm.setMapView(v);
            String onde = switch (ondeDe(w)) {
                case NORMAL -> "mundo normal";
                case NETHER -> "Nether";
                case END -> "End";
            };
            mm.displayName(txt("§6§lMapa das Masmorras §7(" + onde + ")"));
            mm.lore(List.of(txt("§7O mundo inteiro, até a borda."), txt("§7Bandeira = masmorra (cor = nível):"),
                    txt("§a1 §e2 §63 §c4 §55 §7| §cX§7 = chefe derrotado (volta depois)"), txt("§7Seta branca = você.")));
            it.setItemMeta(mm);
        }
        for (ItemStack sobra : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        p.sendMessage(txt(PREFIXO + "Mapa das masmorras entregue! Segure na mão para ver. §8(cor da bandeira = nível: §a1 §e2 §63 §c4 §55§8)"));
    }

    // ================================================================== menu e bussola

    static final class Menu implements org.bukkit.inventory.InventoryHolder {
        final Map<Integer, Integer> idPorSlot = new HashMap<>();
        org.bukkit.inventory.Inventory inv;

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inv;
        }
    }

    static Material icone(Tipo t) {
        String m = switch (t.id) {
            case "CRIPTA" -> "SKELETON_SKULL";
            case "NINHO" -> "SPIDER_EYE";
            case "MINA" -> "IRON_PICKAXE";
            case "PANTANO" -> "BREWING_STAND";
            case "GELO" -> "BLUE_ICE";
            case "TORRE" -> "ENCHANTING_TABLE";
            case "FORJA" -> "BLAZE_ROD";
            case "BASTIAO" -> "GILDED_BLACKSTONE";
            case "WITHER" -> "WITHER_SKELETON_SKULL";
            case "CIDADELA" -> "PURPUR_BLOCK";
            case "PRISAO" -> "SCULK_SHRIEKER";
            case "DESERTO" -> "CHISELED_SANDSTONE";
            case "QUARTEL" -> "CROSSBOW";
            case "VENTOS" -> "BREEZE_ROD";
            default -> "COMPASS";
        };
        Material x = Material.matchMaterial(m);
        return x == null ? Material.COMPASS : x;
    }

    private String status(Masmorra m) {
        if (m.chefe != null) return "§4lutando agora";
        return m.bossVolta == 0 ? "§achefe esperando" : "§cchefe volta em " + Math.max(1, (m.bossVolta - System.currentTimeMillis()) / 60000) + " min";
    }

    /** Menu com as masmorras deste mundo (da mais perto para a mais longe). Clicar da a bussola. */
    void abrirMenu(Player p) {
        List<Masmorra> l = new ArrayList<>();
        for (Masmorra m : todas) if (m.mundo.equals(p.getWorld().getName()) && !m.demolindo && !m.tipo.secreta) l.add(m);
        if (l.isEmpty()) {
            p.sendMessage(txt(PREFIXO + "Não há masmorras neste mundo. Elas ficam no mundo normal, no Nether e no End."));
            return;
        }
        Location pl = p.getLocation();
        l.sort(Comparator.comparingDouble(m -> dist2(m, pl)));
        Menu h = new Menu();
        int linhas = Math.min(6, (Math.min(l.size(), 45) + 8) / 9 + 1);
        h.inv = Bukkit.createInventory(h, linhas * 9, txt("§4§lMasmorras §8(mais perto primeiro)"));
        for (int i = 0; i < l.size() && i < (linhas - 1) * 9; i++) {
            Masmorra m = l.get(i);
            ItemStack it = new ItemStack(icone(m.tipo), Math.max(1, m.tipo.nivel));
            ItemMeta im = it.getItemMeta();
            im.displayName(txt("§6§l" + m.tipo.nome + " §7(Nível " + m.tipo.nivel + ")"));
            im.lore(List.of(txt("§7Chefe: §c" + m.tipo.chefeNome), txt("§7Distância: §f" + (int) Math.sqrt(dist2(m, pl)) + " blocos " + direcao(pl, m)),
                    txt("§7Entrada: §f" + m.saidaX + " " + m.saidaY + " " + m.saidaZ), txt(status(m)), txt(""), txt("§eClique: ganha uma bússola até ela")));
            it.setItemMeta(im);
            h.inv.setItem(i, it);
            h.idPorSlot.put(i, m.id);
        }
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(txt("§e§lComo funciona"));
        im.lore(List.of(txt("§7Número no ícone = nível (1 a 5)."), txt("§7Mais longe do spawn = mais forte."), txt("§7A bússola aponta para a entrada"),
                txt("§7(só funciona neste mundo)."), txt("§7Mapa com todas: §f/masmorras mapa")));
        info.setItemMeta(im);
        h.inv.setItem(linhas * 9 - 5, info);
        ItemStack hs = new ItemStack(Material.LODESTONE);
        ItemMeta hm = hs.getItemMeta();
        hm.displayName(txt("§6§lSpawn das Masmorras"));
        hm.lore(List.of(txt("§7Praça segura com o §5Mercador Lendário§7."), txt("§eClique para ir"), txt("§8(ou /masmorras spawn)")));
        hs.setItemMeta(hm);
        h.inv.setItem(linhas * 9 - 1, hs);
        h.idPorSlot.put(linhas * 9 - 1, -1);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), "minecraft:ui.button.click", 0.6f, 1.2f);
    }

    @EventHandler
    public void cliqueMenu(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;
        Integer id = h.idPorSlot.get(e.getSlot());
        if (id != null && id == -1) {
            p.closeInventory();
            hub.teleportar(p);
            return;
        }
        Masmorra m = id == null ? null : porId.get(id);
        if (m == null) return;
        p.closeInventory();
        if (!darBussola(p, m)) return;
        // o grupo (plugin Clas) ganha a mesma bussola
        List<String> foram = new ArrayList<>(), longe = new ArrayList<>();
        for (Player o : grupo(p)) {
            if (!o.getWorld().equals(p.getWorld())) {
                longe.add(o.getName());
                continue;
            }
            o.sendMessage(txt(PREFIXO + "§b" + p.getName() + " §7escolheu §e" + m.tipo.nome + " §7para o grupo."));
            if (darBussola(o, m)) foram.add(o.getName());
        }
        if (!foram.isEmpty()) p.sendMessage(txt(PREFIXO + "Seu grupo também ganhou a bússola: §b" + String.join("§7, §b", foram)));
        if (!longe.isEmpty()) p.sendMessage(txt(PREFIXO + "§8Em outro mundo (não ganhou): " + String.join(", ", longe)));
    }

    /** Membros online do grupo do jogador (plugin Clas), sem ele. Vazio se nao tem grupo ou o Clas nao esta ligado. */
    @SuppressWarnings("unchecked")
    List<Player> grupo(Player p) {
        org.bukkit.plugin.Plugin c = Bukkit.getPluginManager().getPlugin("Clas");
        if (c == null || !c.isEnabled()) return List.of();
        try {
            Object r = c.getClass().getMethod("membrosDoGrupo", Player.class).invoke(c, p);
            return r instanceof List<?> l ? (List<Player>) l : List.of();
        } catch (Throwable t) {
            return List.of();
        }
    }

    @EventHandler
    public void arrastarMenu(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private NamespacedKey kBussola;

    /** Masmorra da bussola na mao (ou null). */
    private Masmorra daBussola(ItemStack it) {
        if (kBussola == null) kBussola = new NamespacedKey(this, "bussola");
        if (it == null || it.getType() != Material.COMPASS || !it.hasItemMeta()) return null;
        Integer id = it.getItemMeta().getPersistentDataContainer().get(kBussola, PersistentDataType.INTEGER);
        return id == null ? null : porId.get(id);
    }

    private void mostrarDistancia(Player p, Masmorra m) {
        if (!m.mundo.equals(p.getWorld().getName())) {
            p.sendActionBar(txt("§7Essa masmorra fica em outro mundo."));
            return;
        }
        int d = (int) Math.sqrt(dist2(m, p.getLocation()));
        String cor = d < 50 ? "§a" : d < 300 ? "§e" : "§6";
        String txt = d < 8 ? "§a§lVocê chegou! §7Entrada da " + m.tipo.nome
                : "§6" + m.tipo.nome + " §8| " + cor + d + " blocos §7" + direcao(p.getLocation(), m) + " §8| §7altura da entrada: §f" + m.saidaY;
        p.sendActionBar(txt(txt));
    }

    /** Apertou a bussola: mostra quanto falta. */
    @EventHandler
    public void usarBussola(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || e.getAction() == org.bukkit.event.block.Action.PHYSICAL) return;
        Masmorra m = daBussola(e.getItem());
        if (m == null) return;
        mostrarDistancia(e.getPlayer(), m);
        e.getPlayer().playSound(e.getPlayer().getLocation(), "minecraft:ui.button.click", 0.4f, 1.6f);
    }

    /** Segurando a bussola: a distancia fica atualizando na tela. */
    private void bussolasNaMao() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Masmorra m = daBussola(p.getInventory().getItemInMainHand());
            if (m != null) mostrarDistancia(p, m);
        }
    }

    boolean darBussola(Player p, Masmorra m) {
        World w = Bukkit.getWorld(m.mundo);
        if (w == null || !p.getWorld().equals(w)) {
            p.sendMessage(txt(PREFIXO + "Essa masmorra é de outro mundo."));
            return false;
        }
        if (kBussola == null) kBussola = new NamespacedKey(this, "bussola");
        // so uma bussola de masmorra por vez
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kBussola, PersistentDataType.INTEGER)) p.getInventory().remove(it);
        ItemStack b = new ItemStack(Material.COMPASS);
        Location alvoBussola = new Location(w, m.saidaX + 0.5, m.saidaY, m.saidaZ + 0.5);
        boolean celular = BedrockSeguro.bedrock(p);
        if (celular) p.setCompassTarget(alvoBussola); // no Bedrock, bussola com ima derruba a sessao (Geyser)
        if (b.getItemMeta() instanceof org.bukkit.inventory.meta.CompassMeta cm) {
            if (!celular) {
                cm.setLodestone(alvoBussola);
                cm.setLodestoneTracked(false);
            }
            cm.displayName(txt("§6§lBússola: §e" + m.tipo.nome + " §7(Nível " + m.tipo.nivel + ")"));
            cm.lore(List.of(txt("§7Aponta para a entrada da masmorra."), txt("§7Entrada: §f" + m.saidaX + " " + m.saidaY + " " + m.saidaZ),
                    txt("§7Chefe: §c" + m.tipo.chefeNome), txt("§8Outra masmorra: /masmorras menu")));
            cm.getPersistentDataContainer().set(kBussola, PersistentDataType.INTEGER, m.id);
            b.setItemMeta(cm);
        }
        for (ItemStack sobra : p.getInventory().addItem(b).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        p.sendMessage(txt(PREFIXO + "Bússola até §e" + m.tipo.nome + " §7(Nível " + m.tipo.nivel + ", " + (int) Math.sqrt(dist2(m, p.getLocation())) + " blocos "
                + direcao(p.getLocation(), m) + "). Segure na mão e siga a agulha."));
        p.playSound(p.getLocation(), "minecraft:item.lodestone_compass.lock", 1f, 1f);
        return true;
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("coronas")) return coronas.comando(s, a);
        if (cmd.getName().equalsIgnoreCase("reviver")) return MoedaReviver.comando(s, a);
        if (cmd.getName().equalsIgnoreCase("galeria")) return galeria.comando(s, a);
        if (cmd.getName().equalsIgnoreCase("warp")) {
            if (ala == null) {
                s.sendMessage("§cA Galeria nao esta ligada.");
                return true;
            }
            return ala.warp(s, a);
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("admin")) return admin(s, a);
        if (a.length > 0 && a[0].equalsIgnoreCase("spawn") && s instanceof Player pp) {
            irParaSpawnMasmorras(pp);
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: masmorras admin <lista|status|construir|reset <id>|bordas>");
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("mapa")) {
            darMapa(p);
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("menu")) {
            abrirMenu(p);
            return true;
        }
        boolean todasDoMundo = a.length > 0 && a[0].equalsIgnoreCase("todas");
        List<Masmorra> l = new ArrayList<>();
        for (Masmorra m : todas) if (m.mundo.equals(p.getWorld().getName()) && !m.tipo.secreta) l.add(m);
        if (l.isEmpty()) {
            p.sendMessage(txt(PREFIXO + "Não há masmorras neste mundo. Elas ficam no mundo normal, no Nether e no End."));
            return true;
        }
        Location pl = p.getLocation();
        l.sort(Comparator.comparingDouble(m -> dist2(m, pl)));
        p.sendMessage(txt("§6§l--- MASMORRAS " + (todasDoMundo ? "(todas deste mundo)" : "mais perto") + " ---"));
        int n = 0;
        for (Masmorra m : l) {
            if (!todasDoMundo && n++ >= 5) break;
            int dist = (int) Math.sqrt(dist2(m, pl));
            String status = m.bossVolta == 0 ? "§achefe esperando" : "§cchefe volta em " + Math.max(1, (m.bossVolta - System.currentTimeMillis()) / 60000) + " min";
            if (m.chefe != null) status = "§4lutando agora";
            p.sendMessage(txt("§e" + m.tipo.nome + " §7(Nível " + m.tipo.nivel + ") §f" + m.saidaX + " " + m.saidaY + " " + m.saidaZ
                    + " §8| §7" + dist + " blocos " + direcao(pl, m) + " §8| " + status));
        }
        p.sendMessage(txt("§7Quanto mais longe do spawn, mais forte. §e/masmorras mapa §7(mapa com todas) §8| /masmorras todas"));
        return true;
    }

    private static double dist2(Masmorra m, Location l) {
        double dx = m.saidaX - l.getX(), dz = m.saidaZ - l.getZ();
        return dx * dx + dz * dz;
    }

    static String direcao(Location de, Masmorra m) {
        double dx = m.saidaX - de.getX(), dz = m.saidaZ - de.getZ();
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        String[] n = {"ao norte", "a nordeste", "a leste", "a sudeste", "ao sul", "a sudoeste", "a oeste", "a noroeste"};
        return n[(int) Math.floorMod(Math.round(ang / 45.0), 8L)];
    }

    /** Acha pelo numero ("83" ou "#83") ou pelo nome da secreta ("???" ou "secreta"). */
    Masmorra porTexto(String t) {
        if (t.equals("???") || t.equalsIgnoreCase("secreta")) {
            for (Masmorra m : porId.values()) if (m.tipo.secreta) return m;
            return null;
        }
        try {
            return porId.get(Integer.parseInt(t.startsWith("#") ? t.substring(1) : t));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("masmorras.admin")) {
            s.sendMessage(txt(PREFIXO + "§cSem permissão."));
            return true;
        }
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (op) {
            case "mundo-mapa" -> {
                // desenha o mapa do mundo novo (altura e bioma, sem carregar chunks) em mundo-mapa.png
                if (mundoNovo == null || mundoNovo.gerador == null) {
                    s.sendMessage("mundo novo desligado");
                    return true;
                }
                int raio = a.length > 2 ? Integer.parseInt(a[2]) : borda(Tipo.Onde.NORMAL) / 2, passo = a.length > 3 ? Integer.parseInt(a[3]) : 8;
                s.sendMessage("desenhando " + (raio * 2 / passo) + "x" + (raio * 2 / passo) + "...");
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        File f = new File(getDataFolder(), "mundo-mapa.png");
                        MapaMundo.desenhar(mundoNovo.gerador, raio, passo, f, todas);
                        s.sendMessage("mapa em " + f.getPath());
                    } catch (Throwable t) {
                        s.sendMessage("falhou: " + t);
                    }
                });
            }
            case "foto" -> {
                // foto de cima dos blocos carregados: masmorras admin foto <mundo> <x> <z> <raio> [escala]
                World fw = Bukkit.getWorld(a[2]);
                int fx = Integer.parseInt(a[3]), fz = Integer.parseInt(a[4]), fr = Integer.parseInt(a[5]), esc = a.length > 6 ? Integer.parseInt(a[6]) : 4;
                if (fw == null) {
                    s.sendMessage("mundo?");
                    return true;
                }
                try {
                    File f = new File(getDataFolder(), "foto.png");
                    MapaMundo.foto(fw, fx, fz, fr, esc, f);
                    s.sendMessage("foto em " + f.getPath());
                } catch (Throwable t) {
                    s.sendMessage("falhou: " + t);
                }
            }
            case "testequebra" -> {
                // /masmorras admin testequebra <mundo> <x> <y> <z>: dispara uma quebra de verdade (todos os plugins) e diz se deixou
                if (!(s instanceof Player tp) || a.length < 6) {
                    s.sendMessage("uso: /masmorras admin testequebra <mundo> <x> <y> <z>");
                    return true;
                }
                World tw = Bukkit.getWorld(a[2]);
                if (tw == null) {
                    s.sendMessage("mundo nao existe");
                    return true;
                }
                org.bukkit.block.Block tb = tw.getBlockAt(Integer.parseInt(a[3]), Integer.parseInt(a[4]), Integer.parseInt(a[5]));
                BlockBreakEvent ev = new BlockBreakEvent(tb, tp);
                Bukkit.getPluginManager().callEvent(ev);
                String r = "[testequebra] " + a[2] + " " + a[3] + " " + a[4] + " " + a[5] + " (" + tb.getType() + "): " + (ev.isCancelled() ? "PROTEGIDO" : "QUEBRA")
                        + " | mundo=" + mundoNovo.protegidoMundo(tb) + " masmorra=" + protegido(tb);
                getLogger().info(r);
                s.sendMessage(r);
            }
            case "skins" -> {
                s.sendMessage(skins.resumo());
                skins.gerarFaltando();
                return true;
            }
            case "coroa" -> {
                // teste visual: masmorras admin coroa <tema> -> um zumbi com a coroa do chefe daquele tema, na sua frente
                if (!(s instanceof Player pj)) return true;
                Tipo tt = Tipo.TODOS.get(a.length > 2 ? a[2].toUpperCase(Locale.ROOT) : "CRIPTA");
                if (tt == null) {
                    s.sendMessage("tema? ex.: CRIPTA, GELO, FORJA");
                    return true;
                }
                Location lc = pj.getLocation().add(pj.getLocation().getDirection().setY(0).normalize().multiply(3));
                LivingEntity zb = (LivingEntity) pj.getWorld().spawnEntity(lc, EntityType.valueOf(tt.chefe.equals("WARDEN") ? "ZOMBIE" : tt.chefe), CreatureSpawnEvent.SpawnReason.CUSTOM);
                if (zb instanceof Mob mb) mb.setAI(false);
                zb.customName(Chefe.nome(tt));
                zb.setCustomNameVisible(true);
                if (tt.escala != 1) Chefe.atributo(zb, Attribute.SCALE, tt.escala);
                coroas.vestir(zb, tt);
                s.sendMessage("coroa de " + tt.chefeNome + " (" + tt.chefe + ")");
            }
            case "lista" -> {
                for (Masmorra m : todas)
                    s.sendMessage("#" + m.id + " " + m.tipo.nome + " (" + m.tipo.nivel + ", v" + m.versao + ") " + m.mundo + " " + m.saidaX + " " + m.saidaY + " " + m.saidaZ
                            + (m.bossVolta == 0 ? " chefe esperando" : " chefe volta " + (m.bossVolta - System.currentTimeMillis()) / 60000 + " min"));
                s.sendMessage(todas.size() + " masmorras.");
            }
            case "status" -> s.sendMessage(PREFIXO + "Construção: " + situacao + " (na fila: " + fila.size() + ", antigas para desmanchar: "
                    + demolir.size() + (demolindoAgora != null ? " + 1 agora" : "") + ")");
            case "guia" -> {
                if (s instanceof Player pg) hub.moverGuia(pg);
            }
            case "herobrine" -> {
                // masmorras admin herobrine <jogador> - da o conjunto cosmetico de Herobrine
                Player alvo = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : null;
                if (alvo == null) {
                    s.sendMessage(PREFIXO + "Uso: /masmorras admin herobrine <jogador>");
                    return true;
                }
                for (ItemStack it : loja.pecasConjunto("herobrine", "de Herobrine", "§4"))
                    for (ItemStack sobra : alvo.getInventory().addItem(it).values()) alvo.getWorld().dropItem(alvo.getLocation(), sobra);
                s.sendMessage(PREFIXO + "Conjunto de Herobrine dado a " + alvo.getName() + ".");
            }
            case "galeria" -> galeria.refazer(s);
            case "reliquia" -> {
                // masmorras admin reliquia <jogador> <tema> [item] - da uma reliquia pronta (para testar o visual)
                if (a.length < 4) {
                    s.sendMessage(PREFIXO + "Uso: /masmorras admin reliquia <jogador> <tema> [item]  (ex.: CRIPTA diamond_sword)");
                    return true;
                }
                Player alvo = Bukkit.getPlayerExact(a[2]);
                Tipo tp = Tipo.TODOS.get(a[3].toUpperCase(java.util.Locale.ROOT));
                if (alvo == null || tp == null) {
                    s.sendMessage(PREFIXO + (alvo == null ? "Jogador nao encontrado." : "Tema desconhecido. Temas: " + String.join(", ", Tipo.TODOS.keySet())));
                    return true;
                }
                org.bukkit.Material mat = a.length >= 5 ? org.bukkit.Material.matchMaterial(a[4]) : org.bukkit.Material.DIAMOND_SWORD;
                if (mat == null) {
                    s.sendMessage(PREFIXO + "Item desconhecido.");
                    return true;
                }
                ItemStack it = reliquias.aplicar(new ItemStack(mat), tp);
                alvo.getInventory().addItem(it);
                s.sendMessage(PREFIXO + "Reliquia " + tp.id + " (" + mat.name().toLowerCase(java.util.Locale.ROOT) + ") dada a " + alvo.getName() + ".");
            }
            case "editar" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(PREFIXO + "Use no jogo.");
                    return true;
                }
                if (editando.remove(p.getUniqueId())) p.sendMessage(txt(PREFIXO + "Modo de edição §cdesligado§7: as masmorras voltam a ser inquebráveis para você."));
                else {
                    editando.add(p.getUniqueId());
                    p.sendMessage(txt(PREFIXO + "Modo de edição §aligado§7: você pode quebrar e colocar blocos nas masmorras. §8(/masmorras admin editar desliga)"));
                }
            }
            case "construir" -> {
                planejar();
                s.sendMessage(PREFIXO + "Construção: " + situacao + " (na fila: " + fila.size() + ")");
            }
            case "hab" -> {
                // teste: /masmorras admin hab <HABILIDADE>  (usa no chefe vivo mais perto de voce / do 1o jogador)
                Player perto = s instanceof Player pp ? pp : (Bukkit.getOnlinePlayers().isEmpty() ? null : Bukkit.getOnlinePlayers().iterator().next());
                Masmorra alvoM = null;
                double md = 1e18;
                for (Masmorra m : todas)
                    if (m.chefe != null && m.chefe.vivo() && perto != null && m.chefe.e.getWorld().equals(perto.getWorld())) {
                        double d = m.chefe.e.getLocation().distanceSquared(perto.getLocation());
                        if (d < md) {
                            md = d;
                            alvoM = m;
                        }
                    }
                if (alvoM == null || a.length < 3) {
                    s.sendMessage(PREFIXO + "Sem chefe vivo por perto (ou falta a habilidade).");
                    return true;
                }
                try {
                    alvoM.chefe.testarHabilidade(Tipo.Hab.valueOf(a[2].toUpperCase(Locale.ROOT)));
                    s.sendMessage(PREFIXO + "Habilidade " + a[2] + " usada por " + alvoM.tipo.chefeNome);
                } catch (IllegalArgumentException ex) {
                    s.sendMessage(PREFIXO + "Habilidade desconhecida.");
                }
            }
            case "bordas" -> {
                aplicarBordas();
                s.sendMessage(PREFIXO + "Bordas conferidas.");
            }
            case "reset" -> {
                Masmorra m = a.length > 2 ? porTexto(a[2]) : null;
                if (m == null) {
                    s.sendMessage(PREFIXO + "Masmorra não encontrada.");
                    return true;
                }
                limpar(m);
                resetar(m);
                s.sendMessage(PREFIXO + "Masmorra #" + m.id + " voltou ao começo.");
            }
            case "tp" -> {
                Masmorra m = a.length > 2 ? porTexto(a[2]) : null;
                World w = m == null ? null : Bukkit.getWorld(m.mundo);
                if (m == null || w == null || !(s instanceof Player p)) {
                    s.sendMessage(PREFIXO + "Use: /masmorras admin tp <id> (no jogo)");
                    return true;
                }
                p.teleport(new Location(w, m.saidaX + 0.5, m.saidaY, m.saidaZ + 0.5));
            }
            default -> s.sendMessage(PREFIXO + "/masmorras admin <lista|status|construir|reset <id>|tp <id>|bordas|editar>  (id = número ou ???)");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> l = new ArrayList<>();
        if (a.length == 1) {
            l.add("todas");
            l.add("mapa");
            l.add("menu");
            if (s.hasPermission("masmorras.admin")) l.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("masmorras.admin"))
            l.addAll(List.of("lista", "status", "construir", "reset", "tp", "bordas", "editar", "reliquia"));
        String u = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        l.removeIf(x -> !x.startsWith(u));
        return l;
    }

    Map<String, Object> resumo() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("masmorras", todas.size());
        r.put("fila", fila.size());
        r.put("situacao", situacao);
        return r;
    }
}
