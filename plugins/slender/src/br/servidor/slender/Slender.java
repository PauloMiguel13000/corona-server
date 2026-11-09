package br.servidor.slender;

import java.io.File;

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
import org.bukkit.Chunk;
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
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Minigame de terror: colete as 8 folhas na floresta escura antes que o Slenderman te pegue. */
public final class Slender extends JavaPlugin implements Listener {

    static final String PREFIXO = "§4§lPorcelana §8» §7";
    private static final Set<String> COMANDOS_LIVRES = Set.of("slender", "slenderman", "floresta", "porcelana", "terror", "msg", "tell", "w", "r", "reply",
            "login", "l", "logout", "register", "reg");

    NamespacedKey kFolha;
    NamespacedKey kSlender;
    NamespacedKey kPlaca;
    private World mundo;
    private Floresta mapa;
    private Partida partida;
    private Guarda guarda;
    private ResolvableProfile perfil;
    private final MapView[] mapas = new MapView[Folhas.TEXTOS.length];
    private final Set<UUID> movendo = new HashSet<>();
    private boolean pronto;
    private int progresso;
    private BukkitTask obra;
    private BukkitTask obraTeto;
    private Lanterna lanterna;

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ================================================================== ligar / desligar

    /** Atualiza no config.yml real valores que mudaram numa versao nova (so troca o que ainda estiver no valor antigo). */
    Lanterna lanterna() {
        return lanterna;
    }

    private void ajustarValores() {
        int v = getConfig().getInt("ajustes-versao", 0);
        if (v >= 3) return;
        if (v < 1) {
            // 1: a neblina comeca mais perto (6 -> 5 chunks)
            if (getConfig().isSet("distancia-visao") && getConfig().getInt("distancia-visao") == 6) getConfig().set("distancia-visao", 5);
            // piscadas (ondas do efeito Escuridao) a cada 10 s; ficam no config para ajustar sem reiniciar
            if (!getConfig().isSet("escuridao-a-cada")) getConfig().set("escuridao-a-cada", 10);
            if (!getConfig().isSet("escuridao-duracao")) getConfig().set("escuridao-duracao", 6);
        }
        if (v < 2) {
            // 2: visao escura o tempo todo vem do teto escuro (sem piscar); as piscadas ficam curtas (6 -> 3 s)
            if (!getConfig().isSet("teto-escuro")) getConfig().set("teto-escuro", true);
            if (getConfig().isSet("escuridao-duracao") && getConfig().getDouble("escuridao-duracao") == 6) getConfig().set("escuridao-duracao", 3);
            getConfig().setInlineComments("teto-escuro", List.of("true = floresta escura o tempo todo (vidro escuro no limite de altura segura a luz da lua); false = tira"));
            getConfig().setInlineComments("escuridao-a-cada", List.of("segundos entre as piscadas (efeito Escuridao); com o Porcelana perto vem mais vezes"));
            getConfig().setInlineComments("escuridao-duracao", List.of("segundos de cada piscada (de 2 a 15)"));
        }
        // 3: lanterna na mao de quem joga
        if (!getConfig().isSet("lanterna")) getConfig().set("lanterna", true);
        if (!getConfig().isSet("lanterna-alcance")) getConfig().set("lanterna-alcance", 16);
        if (!getConfig().isSet("lanterna-luz")) getConfig().set("lanterna-luz", 12);
        if (!getConfig().isSet("lanterna-luz-perto")) getConfig().set("lanterna-luz-perto", 6);
        getConfig().setInlineComments("lanterna", List.of("true = cada jogador recebe uma lanterna na mao esquerda"));
        getConfig().setInlineComments("lanterna-alcance", List.of("ate quantos blocos a lanterna ilumina para onde o jogador olha"));
        getConfig().setInlineComments("lanterna-luz", List.of("forca da luz no ponto iluminado (1 a 15)"));
        getConfig().setInlineComments("lanterna-luz-perto", List.of("luz fraca logo a frente do jogador (0 = sem)"));
        getConfig().set("ajustes-versao", 3);
        saveConfig();
        getLogger().info("Ajuste aplicado no config.yml (teto-escuro " + getConfig().getBoolean("teto-escuro") + ", piscadas a cada "
                + getConfig().getDouble("escuridao-a-cada") + " s com " + getConfig().getDouble("escuridao-duracao") + " s, lanterna "
                + getConfig().getInt("lanterna-alcance") + " blocos).");
    }

    /** Coloca ou tira o teto escuro (visao escura o tempo todo), aos poucos, sem travar o servidor. */
    private void ajustarTeto() {
        if (mundo == null) return;
        if (obraTeto != null) {
            obraTeto.cancel();
            obraTeto = null;
        }
        boolean quer = getConfig().getBoolean("teto-escuro", true);
        if (Teto.pronto(mundo, quer)) return;
        Teto t = new Teto(this, mundo, quer);
        long inicio = System.currentTimeMillis();
        getLogger().info((quer ? "Colocando" : "Tirando") + " o teto escuro (" + t.chunks() + " chunks)...");
        obraTeto = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!t.passo(15_000_000L)) return;
            if (obraTeto != null) obraTeto.cancel();
            obraTeto = null;
            getLogger().info("Teto escuro " + (quer ? "colocado" : "tirado") + " em " + (System.currentTimeMillis() - inicio) + " ms (" + t.blocos + " blocos).");
        }, 1L, 1L);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ajustarValores();
        kFolha = new NamespacedKey(this, "folha");
        kSlender = new NamespacedKey(this, "slender");
        kPlaca = new NamespacedKey(this, "placa");
        guarda = new Guarda(getDataFolder());
        mapa = new Floresta();
        criarMundo();
        if (mundo == null) return;
        perfil = montarPerfil();
        prepararMapas();
        partida = new Partida(this, mundo, mapa);
        lanterna = new Lanterna(this, new NamespacedKey(this, "lanterna"));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Pacote(), this);
        limparSobras();
        boolean precisa = !mapa.construido(mundo) || getConfig().getInt("mapa-versao", 0) != Floresta.VERSAO;
        if (precisa) construir(getConfig().getInt("mapa-versao", 0) != 0);
        else {
            pronto = true;
            placa();
        }
        ajustarTeto();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (pronto) partida.ciclo();
        }, 20L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, this::vigiarPets, 40L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (pronto) lanterna.tick(mundo, partida::vivo, partida::estaticaDe, partida.posicoesFolhas());
        }, 20L, 2L);
        for (Player p : Bukkit.getOnlinePlayers()) if (guarda.tem(p)) devolverDepoisDoLogin(p);
        Bukkit.getScheduler().runTaskLater(this, this::renomearTodosLetreiros, 200L);
        getLogger().info("Porcelana pronto (" + mapa.pontos.size() + " lugares com folhas).");
    }

    @Override
    public void onDisable() {
        if (lanterna != null) lanterna.apagar(mundo);
        if (partida == null) return;
        for (Player p : partida.online(false)) {
            p.setWalkSpeed(0.2f);
            devolver(p);
        }
        partida.jogadores.clear();
        Mannequin m = partida.slenderAtual();
        if (m != null) m.remove();
        try {
            partida.limparFolhas();
        } catch (Throwable ignored) {
        }
    }

    private void criarMundo() {
        mundo = new WorldCreator(Floresta.MUNDO).generator(new VazioGen()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) {
            getLogger().severe("Nao consegui criar o mundo do Slenderman!");
            return;
        }
        mundo.setDifficulty(Difficulty.PEACEFUL);
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
        // chuva fina sem trovao: deixa a noite mais escura sem precisar da escuridao direto
        mundo.setStorm(getConfig().getBoolean("chuva", true));
        mundo.setThundering(false);
        mundo.setSpawnLocation(Floresta.lobby(mundo));
        mundo.getWorldBorder().setCenter(Floresta.TAM / 2.0, Floresta.TAM / 2.0 + 10);
        mundo.getWorldBorder().setSize(Floresta.TAM + 90);
        try {
            mundo.setViewDistance(Math.max(3, getConfig().getInt("distancia-visao", 6)));
            mundo.setSimulationDistance(4);
        } catch (Throwable t) {
            getLogger().info("Distancia de visao por mundo nao suportada: " + t.getMessage());
        }
    }

    private ResolvableProfile montarPerfil() {
        // a criatura ORIGINAL (o Porcelana): skin desenhada por codigo (fontes/skins/porcelana.py), assinada pelo MineSkin
        // pelo plugin Masmorras (plugins/Masmorras/skins.yml, chave "porcelana"). Sem ela ainda, usa a do config.
        String valor = "", ass = "";
        try {
            File f = new File(getDataFolder().getParentFile(), "Masmorras/skins.yml");
            if (f.exists()) {
                org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
                valor = y.getString("porcelana.value", "");
                ass = y.getString("porcelana.signature", "");
            }
        } catch (Throwable t) {
            valor = "";
        }
        porcelanaPronta = !valor.isEmpty();
        if (!porcelanaPronta) {
            valor = getConfig().getString("skin.valor", "");
            ass = getConfig().getString("skin.assinatura", "");
        }
        UUID id = UUID.nameUUIDFromBytes("Porcelana".getBytes());
        ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name("Porcelana").uuid(id);
        if (!valor.isEmpty()) b.addProperty(new ProfileProperty("textures", valor, ass.isEmpty() ? null : ass));
        return b.build();
    }

    private boolean porcelanaPronta;
    private long ultimaConferencia;

    ResolvableProfile perfilSlender() {
        // a skin pode ficar pronta depois (MineSkin): confere de novo no maximo 1 vez por minuto
        if (!porcelanaPronta && System.currentTimeMillis() - ultimaConferencia > 60_000L) {
            ultimaConferencia = System.currentTimeMillis();
            perfil = montarPerfil();
        }
        return perfil;
    }

    // ------------------------------------------------------------------ nome novo nos letreiros de todos os lobbies
    /** Letreiros (TextDisplay) de qualquer plugin que ainda falam do personagem antigo passam a dizer "Porcelana". */
    @EventHandler
    public void letreirosCarregados(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (org.bukkit.entity.Entity en : e.getEntities())
            if (en instanceof org.bukkit.entity.TextDisplay td) renomearLetreiro(td);
    }

    private void renomearTodosLetreiros() {
        int n = 0;
        for (World w : Bukkit.getWorlds())
            for (org.bukkit.entity.TextDisplay td : w.getEntitiesByClass(org.bukkit.entity.TextDisplay.class))
                if (renomearLetreiro(td)) n++;
        if (n > 0) getLogger().info("Letreiros renomeados para o Porcelana: " + n);
    }

    private boolean renomearLetreiro(org.bukkit.entity.TextDisplay td) {
        try {
            net.kyori.adventure.text.Component c = td.text();
            String plano = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            if (!plano.contains("SLENDER") && !plano.contains("Slender")) return false;
            net.kyori.adventure.text.Component novo = c
                    .replaceText(b -> b.matchLiteral("SLENDERMAN").replacement("O PORCELANA"))
                    .replaceText(b -> b.matchLiteral("Slenderman").replacement("Porcelana"))
                    .replaceText(b -> b.matchLiteral("SLENDER").replacement("PORCELANA"))
                    .replaceText(b -> b.matchLiteral("Slender").replacement("Porcelana"));
            td.text(novo);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** true = o monstro e o modelo 3D do pacote (ItemDisplay) e o boneco fica invisivel; false = boneco com skin. */
    boolean modelo3d() {
        return getConfig().getBoolean("modelo-3d", true);
    }

    /** Giro extra (graus) do modelo 3D, se a frente aparecer virada. */
    float modeloGiro() {
        return (float) getConfig().getDouble("modelo-giro", 0);
    }

    double escala() {
        return Math.max(1.0, Math.min(1.8, getConfig().getDouble("escala", 1.3)));
    }

    private void prepararMapas() {
        List<Integer> ids = getConfig().getIntegerList("folhas-mapas");
        List<Integer> novos = new ArrayList<>();
        boolean mudou = false;
        for (int i = 0; i < mapas.length; i++) {
            MapView v = i < ids.size() ? Bukkit.getMap(ids.get(i)) : null;
            if (v == null) {
                v = Bukkit.createMap(mundo);
                mudou = true;
            }
            Folhas.preparar(v, i);
            mapas[i] = v;
            novos.add(v.getId());
        }
        if (mudou) {
            getConfig().set("folhas-mapas", novos);
            saveConfig();
        }
    }

    ItemStack itemFolha(int idx) {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        if (it.getItemMeta() instanceof MapMeta mm) {
            mm.setMapView(mapas[idx]);
            mm.displayName(txt("§fFolha"));
            it.setItemMeta(mm);
        }
        return it;
    }

    ItemFrame colocarFolha(Floresta.Ancora a, int idx) {
        Location l = a.frente(mundo);
        mundo.getChunkAt(l).load();
        try {
            return mundo.spawn(l, org.bukkit.entity.GlowItemFrame.class, f -> {
                f.setFacingDirection(a.face(), true);
                f.setItem(itemFolha(idx), false);
                f.setVisible(false);
                f.setFixed(true);
                f.setInvulnerable(true);
                f.setPersistent(true);
                f.getPersistentDataContainer().set(kFolha, PersistentDataType.INTEGER, idx);
            });
        } catch (Throwable t) {
            getLogger().warning("Nao consegui colocar a folha em " + a + ": " + t.getMessage());
            return null;
        }
    }

    /** Remove Slenderman e folhas que ficaram de uma partida interrompida. */
    private void limparSobras() {
        for (Chunk c : mundo.getLoadedChunks())
            for (Entity e : c.getEntities())
                if (e.getPersistentDataContainer().has(kSlender, PersistentDataType.BYTE)) e.remove();
        try {
            partida.limparFolhas();
        } catch (Throwable ignored) {
        }
    }

    // ================================================================== construcao do mapa

    private void construir(boolean limpar) {
        pronto = false;
        if (obra != null) obra.cancel();
        List<long[]> chunks = new ArrayList<>();
        for (int cx = -1; cx <= (Floresta.TAM + 4) >> 4; cx++)
            for (int cz = -1; cz <= (Floresta.TAM + 26) >> 4; cz++) {
                mundo.addPluginChunkTicket(cx, cz, this);
                chunks.add(new long[] {cx, cz});
            }
        Deque<Runnable> fila = mapa.etapas(mundo, limpar);
        int total = fila.size();
        progresso = 0;
        getLogger().info("Construindo o mapa do Slenderman (" + total + " etapas)...");
        long inicio = System.currentTimeMillis();
        obra = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long t0 = System.nanoTime();
            while (!fila.isEmpty() && System.nanoTime() - t0 < 25_000_000L) fila.poll().run();
            int p = (int) (100L * (total - fila.size()) / total);
            if (p / 10 != progresso / 10) getLogger().info("Mapa do Slenderman: " + p + "%");
            progresso = p;
            if (fila.isEmpty()) {
                obra.cancel();
                obra = null;
                for (long[] c : chunks) mundo.removePluginChunkTicket((int) c[0], (int) c[1], this);
                getConfig().set("mapa-versao", Floresta.VERSAO);
                saveConfig();
                placa();
                pronto = true;
                getLogger().info("Mapa do Slenderman pronto em " + (System.currentTimeMillis() - inicio) / 1000 + "s.");
            }
        }, 1L, 1L);
    }

    private void placa() {
        Location l = new Location(mundo, Floresta.TAM / 2 + 0.5, Floresta.BASE + 3.2, Floresta.TAM + 2.5);
        mundo.getChunkAt(l).load();
        for (Entity e : mundo.getNearbyEntities(l, 6, 6, 6))
            if (e.getPersistentDataContainer().has(kPlaca, PersistentDataType.BYTE)) e.remove();
        mundo.spawn(l, TextDisplay.class, t -> {
            t.text(txt("§4§lO PORCELANA\n§fColete as §e" + getConfig().getInt("folhas-para-vencer", 8) + " folhas§f presas nas paredes\n"
                    + "§7Não fique encarando a máscara...\n§8/slender sair para desistir"));
            t.setBillboard(Display.Billboard.CENTER);
            // a placa continua legivel debaixo do teto escuro
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
        return p.hasPermission("slender.admin") && p.getGameMode() == GameMode.CREATIVE && !partida.participa(p);
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

    // ================================================================== pet do BabyPets

    /** O pet nao entra na floresta: guarda o pet escolhido (sem isso o BabyPets fica tentando trazer e manda mensagem sem parar). */
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

    /** Quem cai no mundo do Slender (admin, teleporte de outro plugin) fica sem pet; chegando num mundo normal, o pet volta. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void trocouDeMundo(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        if (mundo == null) return;
        Player p = e.getPlayer();
        if (e.getFrom().equals(mundo) && lanterna != null) lanterna.recolher(p);
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
                p.sendMessage(PREFIXO + "§7Seus itens da última partida do Porcelana foram devolvidos.");
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
            Bukkit.broadcast(txt("§4§lPorcelana §8» §f" + String.join(", ", nomes) + " §7escapou" + (nomes.size() > 1 ? "aram" : "")
                    + " do Porcelana em §f" + tempo + "§7!" + (novo ? " §6§lNOVO RECORDE!" : "")));
        }
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: /slender admin <iniciar|parar|construir|status>");
            return true;
        }
        switch (sub) {
            case "", "menu" -> menu(p);
            case "entrar", "jogar" -> entrar(p);
            case "sair" -> {
                if (!partida.remover(p)) p.sendMessage(PREFIXO + "Você não está na partida do Porcelana.");
                else p.sendMessage(PREFIXO + "Você saiu da partida do Porcelana.");
            }
            case "status", "info" -> info(p);
            case "reviver", "moeda" -> {
                if (!Reviver.usarPendente(p))
                    p.sendMessage(PREFIXO + "§7Você não tem uma volta pendente. Moedas de Reviver: §e" + Reviver.moedas(p) + "§7. Use quando for pego.");
            }
            default -> p.sendMessage(PREFIXO + "§f/slender §7(menu), §f/slender entrar§7, §f/slender sair");
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("slender.admin")) {
            s.sendMessage(PREFIXO + "§cSem permissão.");
            return true;
        }
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        Player p = s instanceof Player pp ? pp : (a.length > 2 ? Bukkit.getPlayerExact(a[2]) : null);
        switch (op) {
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
                s.sendMessage(PREFIXO + "Reconstruindo o mapa...");
            }
            case "status" -> s.sendMessage(PREFIXO + (pronto ? partida.status() : "construindo " + progresso + "%"));
            case "pegar" -> s.sendMessage(PREFIXO + (p != null && partida.pegarTeste(p) ? "Pego." : "Não deu (precisa estar vivo na partida)."));
            case "folha" -> s.sendMessage(PREFIXO + (p != null && partida.folhaTeste(p) ? "Folha pega." : "Nada a pegar."));
            case "aparecer" -> {
                double d = 0;
                try {
                    if (a.length > 2) d = Double.parseDouble(a[2]);
                } catch (NumberFormatException ignored) {
                }
                partida.aparecerTeste(d);
                s.sendMessage(PREFIXO + partida.status());
            }
            case "tp" -> {
                if (p != null) {
                    mover(p, mapa.inicio(mundo, 0));
                    s.sendMessage(PREFIXO + "Teleportado para o início do mapa.");
                }
            }
            case "pontos" -> {
                for (Floresta.Ponto pt : mapa.pontos)
                    s.sendMessage("§7" + pt.nome + " §f" + pt.cx + " " + pt.base + " " + pt.cz + " §8(" + pt.ancoras.size() + " paredes)");
            }
            case "recarregar" -> {
                reloadConfig();
                perfil = montarPerfil();
                try {
                    mundo.setViewDistance(Math.max(3, getConfig().getInt("distancia-visao", 5)));
                } catch (Throwable ignored) {
                }
                ajustarTeto();
                s.sendMessage(PREFIXO + "Config recarregada.");
            }
            default -> s.sendMessage(PREFIXO + "/slender admin <iniciar|parar|construir [limpar]|status|folha|aparecer|tp|pontos|recarregar>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> op = new ArrayList<>();
        if (a.length == 1) {
            op.addAll(List.of("entrar", "sair", "status"));
            if (s.hasPermission("slender.admin")) op.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("slender.admin")) {
            op.addAll(List.of("iniciar", "parar", "construir", "status", "folha", "aparecer", "tp", "pontos", "recarregar"));
        }
        String ult = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        op.removeIf(x -> !x.startsWith(ult));
        return op;
    }

    private void entrar(Player p) {
        if (!pronto) {
            p.sendMessage(PREFIXO + "§eO mapa ainda está sendo construído (" + progresso + "%). Tente daqui a pouco.");
            return;
        }
        if (partida.participa(p)) {
            p.sendMessage(PREFIXO + "Você já está na partida do Porcelana. §e/slender sair §7para sair.");
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
        mover(p, Floresta.lobby(mundo));
        partida.adicionar(p);
        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.7f);
        p.sendMessage("§4§l--- O PORCELANA ---");
        p.sendMessage("§7Seus itens ficaram guardados e voltam quando você sair.");
        p.sendMessage("§fEncontre as §e" + partida.totalFolhas() + " folhas §fpresas nas paredes pela floresta e clique nelas.");
        p.sendMessage("§fQuanto mais folhas, mais perto ele chega. §cOlhar para ele por muito tempo te pega.");
        p.sendMessage("§fSair: §e/slender sair");
        if (partida.estado == Partida.Estado.JOGANDO) partida.entrarNoMeio(p);
        else if (partida.estado == Partida.Estado.FIM) p.sendMessage(PREFIXO + "§eA partida anterior está terminando. A próxima começa já já.");
    }

    private void info(Player p) {
        p.sendMessage(PREFIXO + "§fEstado: §e" + estadoTexto() + " §8| §fna partida: §e" + partida.jogadores.size());
        int rec = getConfig().getInt("recorde.segundos", 0);
        if (rec > 0) p.sendMessage(PREFIXO + "§fRecorde: §e" + String.format("%d:%02d", rec / 60, rec % 60) + " §7(" + getConfig().getString("recorde.jogadores", "") + ")");
        p.sendMessage(PREFIXO + "§fSuas vitórias: §e" + getConfig().getInt("vitorias." + p.getUniqueId(), 0));
    }

    private String estadoTexto() {
        return switch (partida.estado) {
            case ESPERANDO -> "esperando jogadores";
            case CONTAGEM -> "começando";
            case JOGANDO -> "em andamento (" + partida.folhas + "/" + partida.totalFolhas() + " folhas, " + partida.vivos().size() + " vivos)";
            case FIM -> "terminando";
        };
    }

    // ================================================================== menu

    private void menu(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, txt("§4§lO Porcelana"));
        h.inv = inv;
        ItemStack vidro = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        ItemStack entrar = cabeca();
        ItemMeta m = entrar.getItemMeta();
        m.displayName(txt(partida.participa(p) ? "§c§lVocê já está na partida" : "§c§lEntrar na partida"));
        m.lore(List.of(txt("§7Estado: §f" + estadoTexto()), txt("§7Jogadores: §f" + partida.jogadores.size()),
                txt(""), txt("§eClique para entrar")));
        entrar.setItemMeta(m);
        inv.setItem(11, entrar);
        inv.setItem(13, item(Material.FILLED_MAP, "§e§lComo jogar",
                "§7Ande pela floresta escura e encontre", "§7as §f" + partida.totalFolhas() + " folhas §7presas nas paredes.",
                "§7Clique na folha para pegar.", "", "§7O Porcelana aparece e some.", "§7Quanto mais folhas, mais perto ele chega.",
                "§cNão fique olhando para ele!", "§7Se ele encostar em você... acabou."));
        int rec = getConfig().getInt("recorde.segundos", 0);
        inv.setItem(15, item(Material.CLOCK, "§6§lRecorde",
                rec > 0 ? "§7Mais rápido: §f" + String.format("%d:%02d", rec / 60, rec % 60) : "§7Ninguém escapou ainda.",
                rec > 0 ? "§7Por: §f" + getConfig().getString("recorde.jogadores", "") : "",
                "§7Suas vitórias: §f" + getConfig().getInt("vitorias." + p.getUniqueId(), 0)));
        if (partida.participa(p)) inv.setItem(22, item(Material.BARRIER, "§c§lSair da partida", "§7Seus itens voltam para você."));
        p.openInventory(inv);
    }

    private static ItemStack item(Material mat, String nome, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(txt(nome));
        List<Component> l = new ArrayList<>();
        for (String s : lore) if (!s.isEmpty() || l.size() > 0) l.add(txt(s));
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
        } else if (e.getSlot() == 22 && partida.participa(p)) {
            p.closeInventory();
            partida.remover(p);
            p.sendMessage(PREFIXO + "Você saiu da partida do Porcelana.");
        }
    }

    @EventHandler
    public void arrastarMenu(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ================================================================== protecao do mundo

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidade(PlayerInteractEntityEvent e) {
        if (!noMundo(e.getRightClicked())) return;
        Entity en = e.getRightClicked();
        if (en instanceof ItemFrame f && f.getPersistentDataContainer().has(kFolha, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) partida.coletar(e.getPlayer(), f);
            return;
        }
        if (!admin(e.getPlayer())) e.setCancelled(true);
    }

    /** Soco na folha tambem pega (o quadro e fixo, entao nem chega a levar dano). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void socoNaFolha(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (!noMundo(e.getAttacked())) return;
        if (e.getAttacked() instanceof ItemFrame f && f.getPersistentDataContainer().has(kFolha, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            partida.coletar(e.getPlayer(), f);
        } else if (!admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagirEntidadeAt(PlayerInteractAtEntityEvent e) {
        if (noMundo(e.getRightClicked()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (!noMundo(e.getEntity())) return;
        Entity en = e.getEntity();
        if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p
                && en instanceof ItemFrame f && f.getPersistentDataContainer().has(kFolha, PersistentDataType.INTEGER)) {
            e.setCancelled(true);
            partida.coletar(p, f);
            return;
        }
        if (en instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            mover(p, partida.vivo(p) ? mapa.inicio(mundo, 0) : Floresta.lobby(mundo));
            return;
        }
        if (en instanceof Player || en instanceof ItemFrame || en instanceof Mannequin || en instanceof TextDisplay) {
            if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p && admin(p)) return;
            e.setCancelled(true);
        }
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
    public void interagir(PlayerInteractEvent e) {
        if (!noMundo(e.getPlayer()) || admin(e.getPlayer())) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
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
        if (p.hasPermission("slender.admin")) return;
        String c = e.getMessage().length() > 1 ? e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        if (c.contains(":")) c = c.substring(c.indexOf(':') + 1);
        if (COMANDOS_LIVRES.contains(c)) return;
        e.setCancelled(true);
        p.sendMessage(PREFIXO + "§cNa partida do Porcelana só vale §f/slender sair§c (e mensagens).");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleporte(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (movendo.contains(p.getUniqueId()) || e.getTo() == null) return;
        boolean deDentro = e.getFrom().getWorld().equals(mundo), praDentro = e.getTo().getWorld().equals(mundo);
        if (partida.participa(p) && !praDentro) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "§cPara sair da partida use §f/slender sair§c.");
            return;
        }
        if (!deDentro && praDentro && !partida.participa(p) && !p.hasPermission("slender.admin")) {
            e.setCancelled(true);
            p.sendMessage(PREFIXO + "Para jogar use §f/slender entrar§7.");
        }
    }

    /** Pets e criaturas de outros plugins nao entram na floresta (so o Slenderman). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void criaturaNasce(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (!noMundo(e.getEntity())) return;
        Entity en = e.getEntity();
        if (en instanceof org.bukkit.entity.LivingEntity && !(en instanceof Player)
                && !en.getPersistentDataContainer().has(kSlender, PersistentDataType.BYTE)) {
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
        if (partida.participa(e.getPlayer())) e.setRespawnLocation(Floresta.lobby(mundo));
    }
}
