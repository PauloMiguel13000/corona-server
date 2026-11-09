package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Poderes: agache + soco no ar com a mao vazia solta o poder escolhido em /poderes.
 * Tambem: pet do BabyPets ataca junto, duelo de pets, Pena de Voo e lobos guerreiros.
 */
public final class Poderes extends JavaPlugin implements Listener {

    private NamespacedKey chaveSelecionado;
    private NamespacedKey chaveFogo;
    private NamespacedKey chaveGelo;
    private NamespacedKey chaveMenu;
    private NamespacedKey chavePena;

    final PetsPonte pets = new PetsPonte();
    private DueloPets duelos;
    private Guardioes guardioes;
    private Torretas torretas;
    Habilidades hab;
    Lutador lut;
    Cipo cipo;
    VenenoCipo venenoCipo;
    Travas travas;
    Barra barraItens;
    Visuais vis;
    Efeitos fx;
    Cenas cena;
    Props3D props;
    PetPoderes petPoderes;
    private NamespacedKey chaveClasse;
    private NamespacedKey chaveTrocaClasse;
    private NamespacedKey chaveMenuClasse;
    private NamespacedKey chaveBotao;
    private final java.util.Set<UUID> carregando = new java.util.HashSet<>();
    /** Niveis de poder em que cada habilidade da classe libera (posicao 1 a 5). */
    static final int[] NIVEIS_PADRAO = {0, 15, 30, 45, 60};

    private final Map<String, Long> recargas = new HashMap<>();
    private final Map<UUID, Long> semDanoQueda = new HashMap<>();
    private final Map<UUID, Long> ultimaAjudaPet = new HashMap<>();
    private final Map<UUID, Voo> voando = new HashMap<>();
    /** Jogadores cujo voo foi liberado por estarem no proprio terreno (GriefPrevention). */
    private final java.util.Set<UUID> vooTerreno = new java.util.HashSet<>();
    private final Map<UUID, Long> gracaSaida = new HashMap<>();
    private NamespacedKey chaveVooTerreno;

    // Danos base (multiplicados pela forca do nivel: 1x a 2x). 2 de dano = 1 coracao.
    static final double DANO_FOGO = 4.0;
    static final double DANO_GELO = 3.0;
    static final double DANO_GOLEM_SOCO = 5.0;
    static final double DANO_GOLEM_EXPLOSAO = 6.0;
    static final double DANO_TORRETA = 1.5;
    static final double DANO_GUARDIAO = 5.0;
    static final double DANO_RAIO = 7.0;
    static final double DANO_KAMEHAMEHA = 1.8;
    static final int KAMEHAMEHA_ACERTOS = 6;

    /** Evita que o dano causado pelos poderes/pets dispare a ajuda do pet de novo. */
    boolean emDanoPoder = false;

    private static final class Voo {
        final boolean podiaVoarAntes;
        BukkitTask tarefa;
        int restante;

        Voo(boolean podiaVoarAntes, int restante) {
            this.podiaVoarAntes = podiaVoarAntes;
            this.restante = restante;
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final class MenuClassesHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chaveSelecionado = new NamespacedKey(this, "selecionado");
        chaveFogo = new NamespacedKey(this, "fogo");
        chaveGelo = new NamespacedKey(this, "gelo");
        chaveMenu = new NamespacedKey(this, "menu");
        chavePena = new NamespacedKey(this, "pena_voo");
        chaveVooTerreno = new NamespacedKey(this, "voo_terreno");
        chaveClasse = new NamespacedKey(this, "classe");
        chaveTrocaClasse = new NamespacedKey(this, "classe_troca");
        chaveMenuClasse = new NamespacedKey(this, "menu_classe");
        chaveBotao = new NamespacedKey(this, "menu_botao");
        duelos = new DueloPets(this);
        guardioes = new Guardioes(this);
        torretas = new Torretas(this);
        vis = new Visuais(this);
        fx = new Efeitos(this);
        cena = new Cenas(this);
        props = new Props3D(this);
        Bukkit.getPluginManager().registerEvents(vis, this);
        Bukkit.getPluginManager().registerEvents(props, this);
        hab = new Habilidades(this);
        hab.carregarCabecaGoku();
        lut = new Lutador(this);
        cipo = new Cipo(this);
        venenoCipo = new VenenoCipo(this);
        travas = new Travas(this);
        Bukkit.getPluginManager().registerEvents(venenoCipo, this);
        barraItens = new Barra(this);
        Bukkit.getPluginManager().registerEvents(barraItens, this);
        petPoderes = new PetPoderes(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(petPoderes, this);
        petPoderes.iniciar();
        Bukkit.getPluginManager().registerEvents(duelos, this);
        Bukkit.getPluginManager().registerEvents(guardioes, this);
        Bukkit.getPluginManager().registerEvents(torretas, this);
        Bukkit.getPluginManager().registerEvents(hab, this);
        Bukkit.getPluginManager().registerEvents(lut, this);
        Bukkit.getScheduler().runTaskTimer(this, this::verificarVooTerreno, 40L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) atualizarTab(p);
        // O antigo MaoDeFogo faz a mesma coisa que o poder Fogo: desliga para nao sair bola em dobro.
        org.bukkit.plugin.Plugin antigo = Bukkit.getPluginManager().getPlugin("MaoDeFogo");
        if (antigo != null && antigo.isEnabled()) {
            Bukkit.getPluginManager().disablePlugin(antigo);
            getLogger().info("MaoDeFogo desativado: o Poderes ja inclui a bola de fogo (pode apagar o MaoDeFogo-1.0.jar).");
        }
        Fx.ligar(this);
        getLogger().info("Poderes ativo: barra de habilidades (slots 1 a 5, liga no item Habilidades / /poderes). Menu: /poderes menu"
                + (Fx.emissoresLigados ? " | celular: efeitos por emissor (pacote v2)" : ""));
    }

    @Override
    public void onDisable() {
        Fx.desligar();
        for (UUID id : new ArrayList<>(voando.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) terminarVoo(p, false);
        }
        voando.clear();
        for (UUID id : new ArrayList<>(vooTerreno)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) desligarVooTerreno(p, false);
        }
        vooTerreno.clear();
        if (duelos != null) duelos.cancelarTodos();
        if (guardioes != null) guardioes.removerTodos();
        if (torretas != null) torretas.removerTodos();
        if (hab != null) hab.limparTodos();
        if (lut != null) lut.limparTodos();
        if (vis != null) vis.removerTodos();
    }

    // ------------------------------------------------------------------ utilidades

    static Component txt(String legado) {
        return LegacyComponentSerializer.legacySection().deserialize(legado).decoration(TextDecoration.ITALIC, false);
    }

    /** So para testes: -Dpoderes.debug=true escreve no console as mensagens da barra. */
    static final boolean DEBUG = Boolean.getBoolean("poderes.debug");

    static void barra(Player p, String msg) {
        p.sendActionBar(txt(msg));
        if (DEBUG) Bukkit.getLogger().info("[Poderes-debug] barra " + p.getName() + ": " + msg.replaceAll("§.", ""));
    }

    /** Nivel de poder para a habilidade na posicao i (0 a 4) da classe. */
    int nivelPosicao(int i) {
        List<Integer> l = getConfig().getIntegerList("classes.niveis");
        if (i < l.size()) return l.get(i);
        return i < NIVEIS_PADRAO.length ? NIVEIS_PADRAO[i] : NIVEIS_PADRAO[NIVEIS_PADRAO.length - 1];
    }

    int nivelNecessario(Poder po) {
        for (Classe c : Classe.values()) {
            int i = c.posicao(po);
            if (i >= 0) return nivelPosicao(i);
        }
        return 0;
    }

    // ------------------------------------------------------------------ classes

    Classe classe(Player p) {
        return Classe.porId(p.getPersistentDataContainer().get(chaveClasse, PersistentDataType.STRING));
    }

    /** Minutos que faltam para poder trocar de classe (0 = pode trocar). */
    long minutosParaTrocar(Player p) {
        if (classe(p) == null || p.hasPermission("poderes.admin")) return 0;
        Long ultima = p.getPersistentDataContainer().get(chaveTrocaClasse, PersistentDataType.LONG);
        if (ultima == null) return 0;
        long espera = getConfig().getLong("classes.troca-recarga-minutos", 60L) * 60_000L;
        long falta = ultima + espera - System.currentTimeMillis();
        return falta <= 0 ? 0 : (falta + 59_999L) / 60_000L;
    }

    /** Escolhe a classe. 'forcar' ignora a recarga (admin). Retorna true se mudou. */
    boolean escolherClasse(Player p, Classe c, boolean forcar) {
        Classe atual = classe(p);
        if (atual == c) {
            barra(p, "§eVoce ja e " + c.cor + c.nome + "§e.");
            return false;
        }
        if (!forcar && travas != null && !travas.liberada(p, c)) {
            p.sendMessage("§c" + c.cor + "§l" + c.nome + " §cé uma classe trancada. " + travas.porque(c));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1f);
            return false;
        }
        long falta = forcar ? 0 : minutosParaTrocar(p);
        if (falta > 0) {
            p.sendMessage("§cVoce so pode trocar de classe daqui a " + falta + " minuto(s).");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return false;
        }
        // desfaz o que estava ativo da classe antiga
        torretas.remover(p.getUniqueId(), false);
        guardioes.remover(p.getUniqueId(), false);
        hab.limpar(p);
        p.getPersistentDataContainer().set(chaveClasse, PersistentDataType.STRING, c.id);
        if (atual != null) p.getPersistentDataContainer().set(chaveTrocaClasse, PersistentDataType.LONG, System.currentTimeMillis());
        selecionar(p, c.poderes[0]);
        atualizarTab(p);
        p.showTitle(net.kyori.adventure.title.Title.title(txt(c.cor + "§l" + c.nome.toUpperCase(Locale.ROOT)),
                txt("§7" + c.desc1 + " " + c.desc2)));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        if (barraItens != null && barraItens.ligado()) {
            p.sendMessage("§aAgora voce e " + c.cor + "§l" + c.nome + "§a! §7Suas 5 habilidades sao os itens dos §fslots 1 a 5§7: segure e clique para usar.");
            p.sendMessage("§7O item §bHabilidades §7(livro) liga/desliga a barra. §f/poderes menu §7mostra os detalhes.");
            barraItens.aoTrocarClasse(p);
        } else {
            p.sendMessage("§aAgora voce e " + c.cor + "§l" + c.nome + "§a! §7Agache + soco no ar com a mao vazia para usar a habilidade.");
            p.sendMessage("§7Agache + §fF §7troca de habilidade. §f/poderes §7mostra as suas habilidades.");
        }
        if (c == Classe.LUTADOR) {
            double b = Lutador.bonusSoco(Math.max(0, nivelDePoder(p)), lut.nivelMaximo(), lut.bonusMin(), lut.bonusMax());
            p.sendMessage("§6Passiva Punhos de Aco: §7seu soco de mao vazia da §c+" + num(b) + " §7de dano (chega a §c+"
                    + num(lut.bonusMax()) + " §7no nivel " + lut.nivelMaximo() + ").");
        }
        if (c == Classe.CIPO && venenoCipo != null) p.sendMessage(venenoCipo.descricao(p));
        if (atual != null) {
            long min = getConfig().getLong("classes.troca-recarga-minutos", 60L);
            if (min > 0) p.sendMessage("§8Proxima troca de classe liberada em " + min + " minuto(s).");
        }
        return true;
    }

    /** Nome no TAB: [Classe] [TAG do cla] Nome. Publico: o plugin Clas chama quando alguem entra/sai de um cla. */
    private static final NamespacedKey VIP_NIVEL = new NamespacedKey("extras", "vip_nivel"), VIP_ATE = new NamespacedKey("extras", "vip_ate");
    private static final String[] TAGS_VIP = {"", "§a§lVIP ", "§b§lVIP+ ", "§6§lMVP ", "§d§lLENDA "};

    /** Tag do VIP (plugin Extras) para o TAB. */
    static String tagVip(Player p) {
        Integer n = p.getPersistentDataContainer().get(VIP_NIVEL, PersistentDataType.INTEGER);
        Long ate = p.getPersistentDataContainer().get(VIP_ATE, PersistentDataType.LONG);
        if (n == null || n < 1 || n > 4 || ate == null || ate < System.currentTimeMillis()) return "";
        return TAGS_VIP[n] + "§r";
    }

    public void atualizarTab(Player p) {
        if (!getConfig().getBoolean("classes.mostrar-no-tab", true)) return;
        Classe c = classe(p);
        String tag = Externo.tag(p);
        String t = tag == null ? "" : "§8[§d" + tag + "§8] ";
        String v = tagVip(p);
        if (c == null) p.playerListName(tag == null && v.isEmpty() ? null : txt(v + t + "§f" + p.getName()));
        else p.playerListName(txt(v + c.cor + "[" + c.nome + "] " + t + "§f" + p.getName()));
    }

    /** Inimigo para ataques em area: mob hostil, jogador, ou qualquer mob mirando em voce. Nunca aliados. */
    boolean inimigoArea(Entity e, Player p) {
        if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) return false;
        if (e instanceof org.bukkit.entity.ArmorStand) return false;
        if (ehAliado(e, p) || pets.ehPet(e)) return false;
        if (e instanceof Player outro) return outro.getGameMode() == GameMode.SURVIVAL || outro.getGameMode() == GameMode.ADVENTURE;
        if (e instanceof Enemy) return true;
        return e instanceof org.bukkit.entity.Mob m && p.equals(m.getTarget());
    }

    void semQueda(Player p, long ms) {
        semDanoQueda.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    /** Nivel de poder do AuraSkills (-1 se nao der para ler). */
    int nivelDePoder(Player p) {
        return Papi.poder(p);
    }

    boolean liberado(Player p, Poder poder, int nivel) {
        if (p.hasPermission("poderes.todos")) return true;
        if (nivel < 0) return true; // sem como ler o nivel: nao bloqueia ninguem
        return nivel >= nivelNecessario(poder);
    }

    /** Forca dos poderes cresce com o nivel: de 1x (nivel 0) ate 2x (nivel 200). */
    double fator(Player p) {
        int n = nivelDePoder(p);
        if (n < 0) n = 0;
        double base = (1.0 + Math.min(n, 200) / 200.0) * getConfig().getDouble("habilidades.dano-multiplicador", 1.3);
        return hab == null ? base : base * hab.multiplicador(p);
    }

    /** Habilidade escolhida (sempre uma da classe do jogador). */
    Poder selecionado(Player p) {
        Classe c = classe(p);
        String s = p.getPersistentDataContainer().get(chaveSelecionado, PersistentDataType.STRING);
        Poder po = Poder.porId(s);
        if (c == null) return po == null ? Poder.FOGO : po;
        return po != null && c.posicao(po) >= 0 ? po : c.poderes[0];
    }

    void selecionar(Player p, Poder po) {
        p.getPersistentDataContainer().set(chaveSelecionado, PersistentDataType.STRING, po.id);
    }

    /** Causa dano respeitando protecoes (WorldGuard etc.). Retorna true se o dano entrou. */
    boolean causarDano(LivingEntity alvo, double dano, Player autor) {
        if (alvo.isDead() || !alvo.isValid()) return false;
        double antes = alvo.getHealth() + alvo.getAbsorptionAmount();
        boolean antigo = emDanoPoder;
        emDanoPoder = true;
        try {
            alvo.damage(dano, autor);
        } finally {
            emDanoPoder = antigo;
        }
        boolean ok = alvo.isDead() || alvo.getHealth() + alvo.getAbsorptionAmount() < antes;
        if (DEBUG) Bukkit.getLogger().info("[Poderes-debug] causarDano " + alvo.getType() + " dano=" + dano + " antes=" + antes
                + " depois=" + (alvo.getHealth() + alvo.getAbsorptionAmount()) + " ok=" + ok + " invul=" + alvo.getNoDamageTicks());
        return ok;
    }

    boolean ehAliado(Entity e, Player p) {
        if (e.equals(p)) return true;
        if (pets.ehPetDe(e, p)) return true;
        if (e instanceof Tameable t && t.isTamed() && p.getUniqueId().equals(t.getOwnerUniqueId())) return true;
        if (guardioes != null && guardioes.ehGuardiaoDe(e, p)) return true;
        if (torretas != null && torretas.ehTorretaDe(e, p)) return true;
        // grupo/cla (plugin Clas) e masmorras (sem PvP): os poderes nao acertam esses jogadores nem os bichos deles
        if (e instanceof Player o) {
            // Arena PvP: todo mundo pode acertar todo mundo (nem cla, nem grupo, nem "sem PvP")
            if (pvpLivre(p.getWorld()) && pvpLivre(o.getWorld())) return false;
            return Externo.aliados(p, o) || Externo.semPvp(o.getLocation()) || Externo.semPvp(p.getLocation());
        }
        if (e instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player dono && !dono.equals(p)) return Externo.aliados(p, dono);
        return false;
    }

    // ------------------------------------------------------------------ gatilho

    /** Quem ainda nao fez /login no AuthMe nao pode usar nada. */
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

    @EventHandler
    public void aoInteragir(PlayerInteractEvent e) {
        Action a = e.getAction();
        Player p = e.getPlayer();

        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && ehPena(e.getItem())) {
            e.setCancelled(true);
            if (logado(p)) usarPena(p, e.getHand());
            return;
        }

        if (a != Action.LEFT_CLICK_AIR) return;
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        if (!p.isSneaking()) return;
        if (barraItens != null && barraItens.ligado() && !getConfig().getBoolean("barra.agachar-tambem", false)) return;
        if (!p.hasPermission("poderes.usar")) return;
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return;
        if (!logado(p)) return;
        lancar(p);
    }

    /** Minigames (e o login/lobby de minigames): sem habilidades. A lista do config soma com estes. */
    static final List<String> SEM_PODERES_SEMPRE = List.of("defesa", "slender", "vovo", "backrooms", "hotel", "login", "minigames", "naochore", "turno_noite", "fabrica", "terror", "galeria");

    /** Mundo onde os poderes acertam qualquer jogador (Arena PvP). */
    boolean pvpLivre(org.bukkit.World w) {
        java.util.List<String> l = getConfig().getStringList("mundos-pvp-livre");
        if (l.isEmpty()) l = java.util.List.of("arena");
        return w != null && l.contains(w.getName());
    }

    boolean semPoderes(org.bukkit.World w) {
        if (w == null) return false;
        String n = w.getName();
        return SEM_PODERES_SEMPRE.contains(n) || getConfig().getStringList("mundos-sem-poderes").contains(n);
    }

    /** Entrou num minigame: o que estava ativo das habilidades acaba (transformacao, lobos, golem, torretas, voo). */
    @EventHandler
    public void entrouEmMinigame(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Fx.esconderDe(p);
        if (!semPoderes(p.getWorld())) return;
        try {
            hab.limpar(p);
            if (guardioes != null) guardioes.remover(p.getUniqueId(), false);
            if (torretas != null) torretas.remover(p.getUniqueId(), false);
            terminarVoo(p, false);
            desligarVooTerreno(p, false);
        } catch (RuntimeException ex) {
            getLogger().warning("Nao consegui desligar as habilidades de " + p.getName() + " no minigame: " + ex);
        }
    }

    void lancar(Player p) {
        if (semPoderes(p.getWorld())) {
            barra(p, "§7As habilidades só funcionam no Mundo das Masmorras §8(portal no spawn)§7.");
            return;
        }
        if (classe(p) == null) {
            barra(p, "§eEscolha sua classe primeiro: §f/classe");
            abrirMenuClasses(p);
            return;
        }
        Poder poder = selecionado(p);
        int nivel = nivelDePoder(p);
        if (!liberado(p, poder, nivel)) {
            barra(p, "§c" + poder.nome + " libera no nivel de poder " + nivelNecessario(poder)
                    + " §7(voce tem " + nivel + ") §8- §f/poderes");
            return;
        }
        long agora = System.currentTimeMillis();
        String chave = p.getUniqueId() + ":" + poder.id;
        Long fim = recargas.get(chave);
        if (fim != null && fim > agora) {
            barra(p, "§7" + poder.nome + ": recarga de §f" + ((fim - agora + 999) / 1000) + "s");
            return;
        }
        boolean ok = switch (poder) {
            // Mago
            case FOGO -> fogo(p);
            case GELO -> gelo(p);
            case VENENO -> hab.veneno(p);
            case RAIO -> raio(p);
            case METEOROS -> hab.meteoros(p);
            // Engenheiro
            case TORRETA -> torretas.colocar(p, Torretas.Tipo.ARQUEIRA, fator(p));
            case TORRE_GELO -> torretas.colocar(p, Torretas.Tipo.GELO, fator(p));
            case TORRE_FOGO -> torretas.colocar(p, Torretas.Tipo.FOGO, fator(p));
            case TORRE_TESLA -> torretas.colocar(p, Torretas.Tipo.TESLA, fator(p));
            case TORRE_CANHAO -> torretas.colocar(p, Torretas.Tipo.CANHAO, fator(p));
            // Saiyajin
            case KI_BLAST -> hab.kiBlast(p);
            case TELEPORTE_INSTANTANEO -> hab.teleInstantaneo(p);
            case VOO -> voo(p);
            case KAMEHAMEHA -> kamehameha(p);
            case SUPER_SAIYAJIN -> hab.superSaiyajin(p);
            // Guerreiro
            case DASH -> dash(p);
            case GOLEM -> golem(p);
            case GRITO -> hab.grito(p);
            case GUARDIAO -> guardioes.invocar(p, 20 + Math.min(Math.max(0, nivelDePoder(p)), 200) / 10);
            case TERREMOTO -> hab.terremoto(p);
            // Ninja
            case SHURIKEN -> hab.shuriken(p);
            case FUMACA -> hab.fumaca(p);
            case TELEPORTE -> teleporte(p);
            case LAMINAS -> hab.laminas(p);
            case ATAQUE_RELAMPAGO -> hab.relampago(p);
            // Druida
            case CURA -> cura(p);
            case RAIZES -> hab.raizes(p);
            case MATILHA -> hab.matilha(p);
            case BENCAO -> hab.bencao(p);
            case ESPINHOS -> hab.espinhos(p);
            // Tridente
            case ARREMESSO -> hab.arremesso(p);
            case ONDA -> hab.onda(p);
            case TRIDENTE_RAIO -> hab.tridenteRaio(p);
            case REDEMOINHO -> hab.redemoinho(p);
            case POSEIDON -> hab.poseidon(p);
            // Arqueiro
            case TIRO_CERTEIRO -> hab.tiroCerteiro(p);
            case FLECHA_EXPLOSIVA -> hab.flechaExplosiva(p);
            case MULTIPLO -> hab.multiplo(p);
            case CHUVA_FLECHAS -> hab.chuvaFlechas(p);
            case TEMPESTADE -> hab.tempestade(p);
            // Lutador
            case RAJADA_SOCOS -> lut.rajada(p);
            case GANCHO -> lut.gancho(p);
            case SOCO_SISMICO -> lut.sismico(p);
            case FURIA -> lut.furia(p);
            case PUNHO_METEORO -> lut.meteoro(p);
            // Cipo
            case CHICOTE -> cipo.chicote(p);
            case BALANCO -> cipo.balanco(p);
            case LACO -> cipo.laco(p);
            case FLOR_CARNIVORA -> cipo.flor(p);
            case SELVA_VIVA -> cipo.selva(p);
        };
        if (ok) {
            recargas.put(chave, agora + poder.recargaSeg * 1000L);
            if (barraItens != null) barraItens.recarga(p, poder);
        }
    }

    // ------------------------------------------------------------------ poderes

    private boolean fogo(Player p) {
        SmallFireball bola = p.launchProjectile(SmallFireball.class);
        bola.setIsIncendiary(false); // queima mobs e jogadores, mas nao poe fogo em casas/florestas
        bola.getPersistentDataContainer().set(chaveFogo, PersistentDataType.DOUBLE, fator(p));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        org.bukkit.Color laranja = org.bukkit.Color.fromRGB(255, 140, 30);
        fx.pulso(bola.getLocation(), Efeitos.b(Material.ORANGE_STAINED_GLASS), Efeitos.b(Material.SHROOMLIGHT), 0.2f, 0.9f, 4, laranja);
        org.bukkit.entity.BlockDisplay nucleo = vis.bloco(bola.getLocation(), Material.SHROOMLIGHT.createBlockData(), 70, laranja);
        Visuais.escala(nucleo, 0.45f, 0f, 0);
        cena.fogoLancar(p, bola);
        {   // prop 3D: bola de fogo com cauda, pegando carona no projetil
            java.util.List<Player> plateia3d = props.plateia(p.getLocation());
            org.bukkit.entity.ItemDisplay fogo3d = props.prop(bola.getLocation(), "fogo_bola_f0", 80, plateia3d);
            if (fogo3d != null) {
                props.esconder(plateia3d, nucleo);
                Props3D.pose(fogo3d, bola.getVelocity(), 1.1f, 0f, 0);
                props.montar(bola, fogo3d);
                props.animar(fogo3d, "fogo_bola", 3, 2);
            }
        }
        fx.seguir(bola, nucleo, 60, 0.6f, 0.45f, fim -> {
            World w = fim.getWorld();
            fx.pulso(fim, Efeitos.b(Material.ORANGE_STAINED_GLASS), Efeitos.b(Material.SHROOMLIGHT), 0.3f, 1.8f, 5, laranja);
            fx.anel(fim, Efeitos.b(Material.MAGMA_BLOCK), 1.8, 6, 8, 0.28f, laranja);
            Fx.p(w, Particle.FLAME, fim, 18, 0.35, 0.35, 0.35, 0.06);
            Fx.p(w, Particle.LAVA, fim, 4, 0.3, 0.2, 0.3, 0);
        });
        return true;
    }

    private boolean gelo(Player p) {
        Vector dir = p.getEyeLocation().getDirection().normalize().multiply(2.0);
        Snowball bola = p.launchProjectile(Snowball.class, dir);
        bola.getPersistentDataContainer().set(chaveGelo, PersistentDataType.DOUBLE, fator(p));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 0.8f);
        Bukkit.getScheduler().runTaskTimer(this, tarefa -> {
            if (!bola.isValid() || bola.getTicksLived() > 60) {
                tarefa.cancel();
                return;
            }
            Fx.p(bola.getWorld(), Particle.SNOWFLAKE, bola.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
        }, 1L, 1L);
        org.bukkit.Color gelo = org.bukkit.Color.fromRGB(150, 220, 255);
        org.bukkit.entity.BlockDisplay cristal = vis.bloco(bola.getLocation(), Material.BLUE_ICE.createBlockData(), 70, gelo);
        Visuais.escala(cristal, 0.4f, 0f, 0);
        cena.geloLancar(p, bola);
        fx.seguir(bola, cristal, 60, 0.8f, 0.4f, fim -> {
            fx.estilhacos(fim, new org.bukkit.block.data.BlockData[] {Efeitos.b(Material.ICE), Efeitos.b(Material.PACKED_ICE), Efeitos.b(Material.BLUE_ICE)}, 9, 1.8, 12, 0.25f);
            fx.anel(fim, Efeitos.b(Material.PACKED_ICE), 1.6, 5, 8, 0.22f, gelo);
            Fx.p(fim.getWorld(), Particle.SNOWFLAKE, fim, 20, 0.4, 0.4, 0.4, 0.05);
            fim.getWorld().playSound(fim, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.6f);
        });
        return true;
    }

    private boolean dash(Player p) {
        Vector dir = p.getLocation().getDirection().normalize();
        Vector v = dir.multiply(1.8);
        v.setY(Math.max(v.getY() + 0.2, 0.45));
        p.setVelocity(v);
        semDanoQueda.put(p.getUniqueId(), System.currentTimeMillis() + 4000L);
        World w = p.getWorld();
        Fx.p(w, Particle.CLOUD, p.getLocation(), 25, 0.3, 0.1, 0.3, 0.05);
        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.4f);
        Location ini = p.getLocation();
        cena.investida(p, ini);
        org.bukkit.block.data.BlockData chao = chaoDe(ini);
        fx.estilhacos(ini.clone().add(0, 0.2, 0), new org.bukkit.block.data.BlockData[] {chao}, 7, 1.6, 12, 0.3f);
        fx.anel(ini.clone().add(0, 0.1, 0), Efeitos.b(Material.WHITE_STAINED_GLASS), 1.6, 5, 10, 0.22f, org.bukkit.Color.WHITE);
        for (int k = 1; k <= 3; k++) {
            final int kk = k;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || !p.getWorld().equals(ini.getWorld())) return;
                double alt = 0.5 + kk * 0.35;
                fx.corte(ini.clone().add(0, alt, 0), p.getLocation().add(0, alt, 0), Efeitos.b(Material.WHITE_STAINED_GLASS), 0.07, 7, org.bukkit.Color.fromRGB(230, 240, 255));
            }, 2L + k);
        }
        return true;
    }

    /** O bloco do chao embaixo (para os estilhacos parecerem do lugar). */
    static org.bukkit.block.data.BlockData chaoDe(Location l) {
        org.bukkit.block.Block b = l.clone().subtract(0, 0.2, 0).getBlock();
        for (int i = 0; i < 4 && b.isPassable(); i++) b = b.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (b.isPassable() || !b.getType().isSolid()) return Material.DIRT.createBlockData();
        return b.getBlockData();
    }

    private boolean cura(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double m = max != null ? max.getValue() : 20.0;
        p.setHealth(Math.min(m, p.getHealth() + 8.0));
        p.setFireTicks(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
        World w = p.getWorld();
        Fx.p(w, Particle.HEART, p.getLocation().add(0, 1.2, 0), 10, 0.5, 0.5, 0.5, 0.1);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        org.bukkit.Color verde = org.bukkit.Color.fromRGB(90, 230, 90);
        fx.orbitar(p, new org.bukkit.inventory.ItemStack(Material.FLOWERING_AZALEA_LEAVES), 4, 1.0, 0.9, 0.35, 0.45f, 24, null);
        cena.cura(p);
        fx.bolhas(p.getLocation(), Efeitos.b(Material.LIME_STAINED_GLASS), 10, 1.1, 2.4, 16, verde);
        fx.anel(p.getLocation().add(0, 0.1, 0), Efeitos.b(Material.MOSS_BLOCK), 1.8, 6, 12, 0.3f, verde);
        return true;
    }

    private boolean raio(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        RayTraceResult r = w.rayTrace(olho, dir, 40.0, FluidCollisionMode.NEVER, true, 0.6,
                ent -> ent instanceof LivingEntity vivo && !vivo.isDead() && !ehAliado(ent, p) && !pets.ehPet(ent));
        Location alvo = r != null ? r.getHitPosition().toLocation(w) : olho.clone().add(dir.multiply(40.0));
        w.strikeLightningEffect(alvo); // so o visual: nao poe fogo em blocos
        org.bukkit.Color azul = org.bukkit.Color.fromRGB(170, 215, 255);
        cena.raio(p, alvo);
        {   // prop 3D: raio em ziguezague (3 blocos) caindo no alvo, piscando entre dois desenhos
            java.util.List<Player> plateia3d = props.plateia(alvo);
            org.bukkit.entity.ItemDisplay r3d = props.prop(alvo.clone().add(0, 1.0, 0), "raio_f0", 12, plateia3d);
            if (r3d != null) {
                Props3D.escala(r3d, 1.25f, (float) (Math.random() * 6.28), 0);
                for (int k = 1; k <= 5; k++) {
                    final int kk = k;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!r3d.isValid()) return;
                        Props3D.quadro(r3d, "raio_f" + (kk % 2));
                        Props3D.escala(r3d, kk >= 4 ? 1.25f - (kk - 3) * 0.4f : 1.25f, (float) (kk * 1.1), 1);
                    }, k * 2L);
                }
            }
        }
        fx.raio(alvo.clone().add(0, 16, 0), alvo, Efeitos.b(Material.WHITE_STAINED_GLASS), 8, 1.1, 0.26, 7, azul);
        fx.raio(alvo.clone().add(1.5, 11, -1), alvo.clone().add(0.4, 2, 0.3), Efeitos.b(Material.LIGHT_BLUE_STAINED_GLASS), 5, 0.8, 0.12, 5, azul);
        fx.pulso(alvo.clone().add(0, 0.6, 0), Efeitos.b(Material.LIGHT_BLUE_STAINED_GLASS), Efeitos.b(Material.WHITE_STAINED_GLASS), 0.4f, 3.0f, 5, azul);
        fx.anel(alvo.clone().add(0, 0.15, 0), Efeitos.b(Material.LIGHT_BLUE_STAINED_GLASS), 3.5, 6, 14, 0.25f, azul);
        double dano = DANO_RAIO * fator(p);
        for (Entity ent : w.getNearbyEntities(alvo, 3.5, 4.0, 3.5)) {
            if (!(ent instanceof LivingEntity le) || ehAliado(ent, p) || pets.ehPet(ent)) continue;
            if (causarDano(le, dano, p)) le.setFireTicks(Math.max(le.getFireTicks(), 60));
        }
        return true;
    }

    /** Voo temporario: 4 segundos. */
    private boolean voo(Player p) {
        if (voando.containsKey(p.getUniqueId())) {
            barra(p, "§eVoce ja esta voando!");
            return false;
        }
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            barra(p, "§eNo criativo voce ja pode voar.");
            return false;
        }
        iniciarVoo(p, 4, "Voo");
        cena.voo(p);
        org.bukkit.Color branco = org.bukkit.Color.fromRGB(255, 250, 220);
        fx.anel(p.getLocation().add(0, 0.1, 0), Efeitos.b(Material.WHITE_STAINED_GLASS), 2.4, 6, 14, 0.3f, branco);
        fx.estilhacos(p.getLocation().add(0, 0.2, 0), new org.bukkit.block.data.BlockData[] {chaoDe(p.getLocation())}, 8, 2.0, 14, 0.3f);
        fx.pulso(p.getLocation().add(0, 1, 0), Efeitos.b(Material.YELLOW_STAINED_GLASS), Efeitos.b(Material.WHITE_STAINED_GLASS), 0.5f, 2.6f, 6, org.bukkit.Color.fromRGB(255, 230, 120));
        return true;
    }

    /** Teleporte do Enderman: aparece onde esta olhando (ate 30 blocos). Respeita terrenos protegidos. */
    boolean teleporte(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        double alcance = 30.0;
        RayTraceResult r = w.rayTraceBlocks(olho, dir, alcance, FluidCollisionMode.NEVER, true);
        org.bukkit.block.Block base = null;
        if (r != null && r.getHitBlock() != null && r.getHitBlockFace() == org.bukkit.block.BlockFace.UP) {
            org.bukkit.block.Block b = r.getHitBlock().getRelative(org.bukkit.block.BlockFace.UP);
            if (livre(b)) base = b;
        }
        if (base == null) {
            double dist = r != null ? olho.toVector().distance(r.getHitPosition()) - 0.7 : alcance;
            for (double d = dist; d >= 1.5; d -= 0.5) {
                org.bukkit.block.Block cabeca = olho.clone().add(dir.clone().multiply(d)).getBlock();
                org.bukkit.block.Block pe = cabeca.getRelative(org.bukkit.block.BlockFace.DOWN);
                if (livre(pe)) {
                    base = pe;
                    break;
                }
            }
        }
        if (base == null) {
            barra(p, "§7Sem espaco para teleportar ali.");
            return false;
        }
        Location origem = p.getLocation();
        Location destino = base.getLocation().add(0.5, 0, 0.5);
        destino.setYaw(origem.getYaw());
        destino.setPitch(origem.getPitch());
        boolean foi = p.teleport(destino, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        if (!foi) {
            barra(p, "§cNao da para teleportar para la (area protegida).");
            return false;
        }
        semDanoQueda.put(p.getUniqueId(), System.currentTimeMillis() + 3000L);
        Fx.p(w, Particle.PORTAL, origem.add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
        w.playSound(origem, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        Fx.p(w, Particle.PORTAL, destino.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
        w.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        org.bukkit.Color roxo = org.bukkit.Color.fromRGB(150, 60, 220);
        cena.sombra(origem, destino);
        fx.pulso(origem, Efeitos.b(Material.BLACK_STAINED_GLASS), Efeitos.b(Material.PURPLE_STAINED_GLASS), 0.5f, 1.9f, 6, roxo);
        fx.colunaDeLuz(destino, Efeitos.b(Material.PURPLE_STAINED_GLASS), 3.2, 1.0, 9, roxo);
        fx.anel(destino.clone().add(0, 0.1, 0), Efeitos.b(Material.OBSIDIAN), 1.7, 5, 10, 0.25f, roxo);
        fx.corte(origem, destino.clone().add(0, 1, 0), Efeitos.b(Material.PURPLE_STAINED_GLASS), 0.08, 8, roxo);
        return true;
    }

    /** Pe e cabeca cabem no bloco (sem lava). */
    private static boolean livre(org.bukkit.block.Block pe) {
        org.bukkit.block.Block cabeca = pe.getRelative(org.bukkit.block.BlockFace.UP);
        return pe.isPassable() && cabeca.isPassable()
                && pe.getType() != Material.LAVA && cabeca.getType() != Material.LAVA;
    }

    /** Forca do Golem: soco que joga o alvo pro alto, como o Iron Golem. */
    private boolean golem(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        RayTraceResult r = w.rayTraceEntities(olho, dir, 6.0, 0.8, ent -> ent instanceof LivingEntity vivo && !vivo.isDead()
                && !(ent instanceof org.bukkit.entity.ArmorStand) && !ehAliado(ent, p) && !pets.ehPet(ent));
        LivingEntity alvo = r != null && r.getHitEntity() instanceof LivingEntity le && p.hasLineOfSight(le) ? le : null;
        if (alvo == null) {
            double melhor = Double.MAX_VALUE;
            for (Entity e : p.getNearbyEntities(4.5, 3.0, 4.5)) {
                if (!(e instanceof LivingEntity le) || e instanceof org.bukkit.entity.ArmorStand || le.isDead()) continue;
                if (ehAliado(e, p) || pets.ehPet(e) || !p.hasLineOfSight(e)) continue;
                Vector ate = e.getLocation().toVector().subtract(p.getLocation().toVector());
                double dist = ate.length();
                if (dist < 0.01 || ate.normalize().dot(dir) < 0.5) continue;
                if (dist < melhor) {
                    melhor = dist;
                    alvo = le;
                }
            }
        }
        if (alvo == null) {
            barra(p, "§7Nenhum alvo na sua frente (chegue mais perto).");
            return false;
        }
        p.swingMainHand();
        if (!causarDano(alvo, DANO_GOLEM_SOCO * fator(p), p)) {
            barra(p, "§7Nao deu para acertar esse alvo aqui (area protegida).");
            return false;
        }
        Vector empurra = alvo.getLocation().toVector().subtract(p.getLocation().toVector());
        empurra.setY(0);
        if (empurra.lengthSquared() > 0.01) empurra.normalize().multiply(0.4);
        empurra.setY(1.25);
        Location l = alvo.getLocation();
        // explosao do tamanho da de um creeper: da dano e empurra, mas nao quebra blocos nem poe fogo
        boolean antigo = emDanoPoder;
        emDanoPoder = true;
        try {
            w.createExplosion(l, 3.0f, false, false, p);
        } finally {
            emDanoPoder = antigo;
        }
        final LivingEntity jogado = alvo;
        Bukkit.getScheduler().runTask(this, () -> {
            if (jogado.isValid() && !jogado.isDead()) jogado.setVelocity(empurra);
        });
        w.playSound(l, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1f);
        Fx.p(w, Particle.CLOUD, l, 20, 0.4, 0.1, 0.4, 0.05);
        cena.golem(p, l);
        Location mao = p.getEyeLocation().add(dir.clone().multiply(0.8)).subtract(0, 0.4, 0);
        org.bukkit.entity.BlockDisplay punho = vis.bloco(mao, Material.IRON_BLOCK.createBlockData(), 14, null);
        Visuais.escala(punho, 0.75f, 0f, 0);
        Location impacto = l.clone().add(0, 1, 0);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (punho.isValid()) punho.teleport(impacto);
        }, 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Visuais.escala(punho, 1.3f, 0.6f, 3);
            fx.pulso(impacto, Efeitos.b(Material.WHITE_STAINED_GLASS), null, 0.4f, 2.6f, 4, org.bukkit.Color.WHITE);
            fx.estilhacos(l.clone().add(0, 0.2, 0), new org.bukkit.block.data.BlockData[] {chaoDe(l), Efeitos.b(Material.COBBLESTONE), Efeitos.b(Material.IRON_BLOCK)}, 12, 3.0, 16, 0.35f);
            fx.anel(l.clone().add(0, 0.1, 0), chaoDe(l), 3.0, 6, 14, 0.4f, null);
        }, 3L);
        Bukkit.getScheduler().runTaskLater(this, () -> vis.remover(punho), 9L);
        Fx.p(w, Particle.CRIT, l.clone().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.2);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explosaoAmiga(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        if (!(e.getDamager() instanceof Player dono)) return;
        if (ehAliado(e.getEntity(), dono) || pets.ehPet(e.getEntity())) {
            e.setCancelled(true);
            return;
        }
        e.setDamage(Math.min(e.getDamage(), DANO_GOLEM_EXPLOSAO * fator(dono)));
    }

    /**
     * Kamehameha: junta a energia nas maos (uma esfera azul que cresce, energia sendo puxada, KA-ME-HA-ME letra por letra)
     * e solta um raio de verdade (barra de luz azul com miolo branco) em linha reta ate 32 blocos, com explosao na ponta.
     */
    private boolean kamehameha(Player p) {
        UUID id = p.getUniqueId();
        if (!carregando.add(id)) return false;
        double f = fator(p);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1.3f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2));
        Particle.DustOptions azul = new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 200, 255), 1.6f);
        org.bukkit.entity.BlockDisplay esfera = vis.bloco(maoKame(p), Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), 60, org.bukkit.Color.fromRGB(90, 210, 255));
        org.bukkit.entity.BlockDisplay miolo = vis.bloco(maoKame(p), Material.WHITE_STAINED_GLASS.createBlockData(), 60, null);
        // prop 3D (quem tem o pacote): a bola de energia nas maos, junto com a esfera de vidro da animacao antiga
        java.util.List<Player> plateia3d = props.plateia(p.getLocation());
        org.bukkit.entity.ItemDisplay carga3d = props.prop(maoKame(p), "kame_carga_f0", 60, plateia3d);
        String[] fala = {"§b§lKA...", "§b§lKA... ME...", "§b§lKA... ME... HA...", "§b§lKA... ME... HA... ME..."};
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(this, tarefa -> {
            if (!p.isOnline() || p.isDead()) {
                carregando.remove(id);
                vis.remover(esfera);
                vis.remover(miolo);
                props.remover(carga3d);
                cena.kameFim(p);
                tarefa.cancel();
                return;
            }
            t[0] += 2;
            Location mao = maoKame(p);
            float s = (float) Math.min(0.9, 0.12 + t[0] * 0.03);
            esfera.teleport(mao);
            miolo.teleport(mao);
            Visuais.escala(esfera, s, t[0] * 0.35f, 2);
            Visuais.escala(miolo, s * 0.55f, -t[0] * 0.5f, 2);
            if (carga3d != null) {
                Props3D.mover(carga3d, mao);
                Props3D.escala(carga3d, 0.4f + s * 1.6f, t[0] * 0.5f, 2);
                if (t[0] % 4 == 0) Props3D.quadro(carga3d, "kame_carga_f" + ((t[0] / 4) % 3));
            }
            Fx.p(p.getWorld(), Particle.DUST, mao, 6, 0.15, 0.15, 0.15, 0, azul);
            cena.kameCarga(p, mao, t[0]);
            // energia sendo puxada para as maos (particulas que andam para o centro)
            for (int i = 0; i < 4; i++) {
                double a = Math.random() * Math.PI * 2, b = Math.random() * Math.PI - Math.PI / 2, r = 1.6;
                Vector off = new Vector(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).multiply(r);
                Location de = mao.clone().add(off);
                Vector para = off.clone().multiply(-1);
                Fx.p(p.getWorld(), Particle.END_ROD, de, 0, para.getX(), para.getY(), para.getZ(), 0.09);
            }
            if (t[0] % 6 == 0) {
                int k = Math.min(3, t[0] / 6 - 1);
                if (k >= 0) {
                    barra(p, fala[k]);
                    w.playSound(mao, Sound.BLOCK_BEACON_AMBIENT, 0.9f, 1.0f + k * 0.25f);
                    w.playSound(mao, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f + k * 0.3f);
                }
            }
            if (t[0] < 28) return;
            tarefa.cancel();
            carregando.remove(id);
            vis.remover(esfera);
            vis.remover(miolo);
            props.remover(carga3d);
            dispararKamehameha(p, f);
        }, 0L, 2L);
        return true;
    }

    private static Location maoKame(Player p) {
        Vector d = p.getLocation().getDirection().setY(0);
        if (d.lengthSquared() < 0.01) d = new Vector(0, 0, 1);
        return p.getLocation().add(0, 1.0, 0).add(d.normalize().multiply(0.7));
    }

    private void dispararKamehameha(Player p, double f) {
        barra(p, "§b§lHAAAAAAAAAAAA!");
        World w0 = p.getWorld();
        w0.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.0f);
        w0.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.6f);
        Fx.p(w0, Particle.SONIC_BOOM, p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.2)), 1);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 62, 1));
        Particle.DustOptions azul = new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 190, 255), 3.0f);
        Particle.DustOptions branco = new Particle.DustOptions(org.bukkit.Color.fromRGB(235, 252, 255), 1.6f);
        org.bukkit.entity.BlockDisplay raio = vis.bloco(p.getEyeLocation(), Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), 80, org.bukkit.Color.fromRGB(70, 200, 255));
        org.bukkit.entity.BlockDisplay nucleo = vis.bloco(p.getEyeLocation(), Material.WHITE_STAINED_GLASS.createBlockData(), 80, null);
        org.bukkit.entity.BlockDisplay bola = vis.bloco(p.getEyeLocation(), Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), 80, org.bukkit.Color.fromRGB(150, 230, 255));
        // prop 3D: o raio em segmentos de 2 blocos (energia em espiral) + a bolha na ponta
        java.util.List<Player> plateia3d = props.plateia(p.getLocation());
        java.util.List<org.bukkit.entity.ItemDisplay> segmentos = new java.util.ArrayList<>();
        org.bukkit.entity.ItemDisplay ponta3d = props.prop(p.getEyeLocation(), "kame_ponta_f0", 80, plateia3d);
        final int duracao = 60; // 3 segundos
        final int[] tick = {0};
        Bukkit.getScheduler().runTaskTimer(this, tarefa -> {
            if (!p.isOnline() || p.isDead() || tick[0] > duracao) {
                tarefa.cancel();
                vis.remover(raio);
                vis.remover(nucleo);
                vis.remover(bola);
                props.remover(ponta3d);
                for (org.bukkit.entity.ItemDisplay sg : segmentos) props.remover(sg);
                cena.kameFim(p);
                return;
            }
            World w = p.getWorld();
            Location olho = p.getEyeLocation();
            Vector dir = olho.getDirection().normalize();
            RayTraceResult rb = w.rayTraceBlocks(olho, dir, 32.0, FluidCollisionMode.NEVER, true);
            double dist = rb != null ? olho.toVector().distance(rb.getHitPosition()) : 32.0;
            // o raio engrossa no comeco e afina no final
            double grossura = tick[0] < 10 ? 0.15 + tick[0] * 0.04 : (duracao - tick[0] < 10 ? 0.15 + (duracao - tick[0]) * 0.04 : 0.55);
            Location inicio = olho.clone().add(dir.clone().multiply(0.8)).subtract(0, 0.25, 0);
            // o vidro do raio (animacao antiga) fica junto do raio 3D: um pouco mais grosso, como um brilho em volta dele
            Visuais.barra(raio, inicio, dir, Math.max(0.1, dist - 0.8), grossura * (plateia3d.isEmpty() ? 2.2 : 2.9), 2);
            Visuais.barra(nucleo, inicio, dir, Math.max(0.1, dist - 0.8), grossura * 1.1, 2);
            Location ponta = olho.clone().add(dir.clone().multiply(dist));
            cena.kameRaio(p, inicio, dir, Math.max(0.1, dist - 0.8), grossura, tick[0]);
            bola.teleport(ponta);
            Visuais.escala(bola, (float) (1.2 + grossura * 2 + Math.sin(tick[0] * 0.6) * 0.3), tick[0] * 0.4f, 2);
            if (!plateia3d.isEmpty()) {
                // segmentos: cada um cobre 2 blocos (modelo de 1 bloco esticado em z); grossura do modelo = 0,44 bloco
                int precisa = (int) Math.ceil(Math.max(0.1, dist - 0.8) / 2.0);
                while (segmentos.size() < precisa) {
                    org.bukkit.entity.ItemDisplay sg = props.prop(inicio, "kame_raio_f0", 80, plateia3d);
                    if (sg == null) break;
                    segmentos.add(sg);
                }
                while (segmentos.size() > precisa) props.remover(segmentos.remove(segmentos.size() - 1));
                float g = (float) (grossura * 2.2 / 0.44);
                String q = "kame_raio_f" + ((tick[0] / 2) % 4);
                for (int i = 0; i < segmentos.size(); i++) {
                    org.bukkit.entity.ItemDisplay sg = segmentos.get(i);
                    double meio = Math.min(i * 2.0 + 1.0, Math.max(0.1, dist - 0.8) - 0.01);
                    Props3D.mover(sg, inicio.clone().add(dir.clone().multiply(meio)));
                    Props3D.pose(sg, dir, g, g, 2.0f, tick[0] * 0.35f + i * 0.4f, 2);
                    Props3D.quadro(sg, q);
                }
                if (ponta3d != null) {
                    Props3D.mover(ponta3d, ponta);
                    Props3D.pose(ponta3d, dir, (float) (0.9 + grossura * 2 + Math.sin(tick[0] * 0.6) * 0.25), tick[0] * 0.6f, 2);
                    if (tick[0] % 4 == 0) Props3D.quadro(ponta3d, "kame_ponta_f" + ((tick[0] / 4) % 2));
                }
            }
            for (double d = 0.8; d <= dist; d += 1.5) {
                Location pt = olho.clone().add(dir.clone().multiply(d)).subtract(0, 0.25, 0);
                Fx.p(w, Particle.DUST, pt, 1, grossura, grossura, grossura, 0, azul);
                if (tick[0] % 4 == 0) Fx.p(w, Particle.DUST, pt, 1, 0.05, 0.05, 0.05, 0, branco);
            }
            Fx.p(w, Particle.END_ROD, ponta, 6, 0.6, 0.6, 0.6, 0.15);
            Fx.p(w, Particle.ELECTRIC_SPARK, ponta, 6, 0.8, 0.8, 0.8, 0.2);
            if (tick[0] % 12 == 0) {
                cena.kameImpacto(p, ponta);
                Fx.p(w, Particle.EXPLOSION_EMITTER, ponta, 1, 0, 0, 0, 0);
                Fx.p(w, Particle.CLOUD, ponta, 12, 0.6, 0.6, 0.6, 0.1);
                w.playSound(ponta, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                w.playSound(olho, Sound.BLOCK_BEACON_AMBIENT, 1f, 2f);
                java.util.Set<UUID> atingidos = new java.util.HashSet<>();
                for (double d = 1.0; d <= dist + 0.5; d += 0.7) {
                    Location pt = olho.clone().add(dir.clone().multiply(d));
                    for (Entity e : w.getNearbyEntities(pt, 1.7, 1.7, 1.7)) {
                        if (!(e instanceof LivingEntity le) || e instanceof org.bukkit.entity.ArmorStand) continue;
                        if (ehAliado(e, p) || pets.ehPet(e) || !atingidos.add(e.getUniqueId())) continue;
                        if (causarDano(le, DANO_KAMEHAMEHA * f, p)) {
                            Vector v = dir.clone().multiply(0.55);
                            v.setY(0.12);
                            le.setVelocity(le.getVelocity().add(v));
                        }
                    }
                }
            }
            tick[0] += 2;
        }, 0L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void forcaDoFogo(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof SmallFireball bola)) return;
        Double f = bola.getPersistentDataContainer().get(chaveFogo, PersistentDataType.DOUBLE);
        if (f == null) return;
        if (bola.getShooter() instanceof Player dono && ehAliado(e.getEntity(), dono)) {
            e.setCancelled(true);
            return;
        }
        e.setDamage(DANO_FOGO * f);
    }

    @EventHandler
    public void geloAcertou(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        Double f = pr.getPersistentDataContainer().get(chaveGelo, PersistentDataType.DOUBLE);
        if (f == null) return;
        Location l = pr.getLocation();
        Fx.p(l.getWorld(), Particle.SNOWFLAKE, l, 25, 0.4, 0.4, 0.4, 0.05);
        l.getWorld().playSound(l, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
        {   // props 3D: cristais de gelo brotando no ponto do impacto (crescem e derretem)
            java.util.List<Player> plateia3d = props.plateia(l);
            Location chao = l.clone();
            for (int i = 0; i < 3 && !plateia3d.isEmpty(); i++) {
                double a = Math.PI * 2 * i / 3 + Math.random(), r = 0.25 + Math.random() * 0.4;
                Location c = chao.clone().add(Math.cos(a) * r, 0.9, Math.sin(a) * r);
                org.bukkit.entity.ItemDisplay cr = props.prop(c, "cristal_gelo_f0", 40, plateia3d);
                if (cr == null) continue;
                final float esc = (float) (0.5 + Math.random() * 0.5), giro = (float) (Math.random() * 6.28);
                Props3D.escala(cr, 0.05f, giro, 0);
                Bukkit.getScheduler().runTaskLater(this, () -> Props3D.escala(cr, esc, giro, 3), 1L);
                Bukkit.getScheduler().runTaskLater(this, () -> Props3D.escala(cr, 0.02f, giro, 8), 30L);
            }
        }
        if (!(e.getHitEntity() instanceof LivingEntity alvo)) return;
        if (!(pr.getShooter() instanceof Player atirador)) return;
        if (ehAliado(alvo, atirador) || pets.ehPet(alvo)) return;
        if (causarDano(alvo, DANO_GELO * f, atirador)) {
            alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void semQueda(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        Long ate = semDanoQueda.get(p.getUniqueId());
        if (ate != null && ate > System.currentTimeMillis()) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ pet ajuda no combate

    private static Player jogadorDe(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void petAjuda(EntityDamageByEntityEvent e) {
        if (emDanoPoder) return;
        if (!getConfig().getBoolean("pets.ajudar-em-combate", true)) return;
        Entity vitima = e.getEntity();
        Entity causador = e.getDamager();

        Player dono = jogadorDe(causador);
        if (dono != null && petPoderes.alvoValido(dono, vitima)) {
            ajudarPet(dono, (LivingEntity) vitima);
            return;
        }
        if (vitima instanceof Player jogador) {
            Entity atacante = causador;
            if (causador instanceof Projectile pr && pr.getShooter() instanceof Entity atirador) atacante = atirador;
            if (atacante instanceof LivingEntity la && atacante instanceof Enemy && !pets.ehPet(la) && !ehAliado(la, jogador)) {
                ajudarPet(jogador, la);
            }
        }
    }

    void ajudarPet(Player dono, LivingEntity alvo) {
        if (!petPoderes.mundoLiberado(dono.getWorld()) || !alvo.getWorld().equals(dono.getWorld())) return;
        long agora = System.currentTimeMillis();
        Long ultima = ultimaAjudaPet.get(dono.getUniqueId());
        long recarga = getConfig().getLong("pets.recarga-ms", 1500L);
        if (petPoderes.furioso(dono)) recarga /= 2;
        if (ultima != null && agora - ultima < recarga) return;
        PetsPonte.PetInfo pet = pets.petAtivo(dono);
        if (pet == null) return;
        LivingEntity pe = pet.entidade;
        if (!pe.getWorld().equals(alvo.getWorld())) return;
        if (pe.getLocation().distanceSquared(alvo.getLocation()) > 20 * 20) return;
        ultimaAjudaPet.put(dono.getUniqueId(), agora);
        double dano = petPoderes.dano(pet, dono);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!alvo.isValid() || alvo.isDead() || !pe.isValid() || !dono.isOnline()) return;
            Vector v = alvo.getLocation().toVector().subtract(pe.getLocation().toVector());
            if (v.lengthSquared() > 0.01) {
                Vector impulso = v.normalize().multiply(0.7);
                impulso.setY(0.35);
                pe.setVelocity(impulso);
            }
            Location l = alvo.getLocation().add(0, alvo.getHeight() / 2.0, 0);
            World w = alvo.getWorld();
            Fx.p(w, Particle.CRIT, l, 12, 0.3, 0.3, 0.3, 0.2);
            Fx.p(w, Particle.SWEEP_ATTACK, l, 1, 0, 0, 0, 0);
            w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.3f);
            if (causarDano(alvo, dano * petPoderes.fatorAlvo(alvo), dono)) petPoderes.talvezUsar(dono, pet, alvo);
        }, 4L);
    }

    // ------------------------------------------------------------------ pena de voo

    ItemStack criarPena(int qtd) {
        ItemStack it = new ItemStack(Material.FEATHER, Math.max(1, Math.min(64, qtd)));
        ItemMeta m = it.getItemMeta();
        m.displayName(txt("§b§lPena de Voo"));
        int s = getConfig().getInt("pena.duracao-segundos", 30);
        List<Component> lore = new ArrayList<>();
        lore.add(txt("§7Clique direito para voar"));
        lore.add(txt("§7por §f" + s + " segundos§7."));
        lore.add(txt(""));
        lore.add(txt("§8Gasta uma pena por uso."));
        m.lore(lore);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(chavePena, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean ehPena(ItemStack it) {
        if (it == null || it.getType() != Material.FEATHER || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(chavePena, PersistentDataType.BYTE);
    }

    private void usarPena(Player p, EquipmentSlot mao) {
        if (voando.containsKey(p.getUniqueId())) {
            barra(p, "§eVoce ja esta voando!");
            return;
        }
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            barra(p, "§eNo criativo voce ja pode voar.");
            return;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack it = mao == EquipmentSlot.OFF_HAND ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (!ehPena(it)) return;
        it.setAmount(it.getAmount() - 1);
        if (mao == EquipmentSlot.OFF_HAND) inv.setItemInOffHand(it.getAmount() > 0 ? it : null);
        else inv.setItemInMainHand(it.getAmount() > 0 ? it : null);

        int dur = Math.max(5, getConfig().getInt("pena.duracao-segundos", 30));
        iniciarVoo(p, dur, "Pena de Voo");
    }

    /** Liga o voo temporario (usado pela Pena de Voo e pelo poder Voo). */
    private void iniciarVoo(Player p, int dur, String nome) {
        Voo voo = new Voo(p.getAllowFlight() && !vooTerreno.contains(p.getUniqueId()), dur);
        voando.put(p.getUniqueId(), voo);
        p.setAllowFlight(true);
        if (!p.isFlying()) p.setVelocity(p.getVelocity().setY(0.6));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && voando.containsKey(p.getUniqueId())) p.setFlying(true);
        }, 3L);
        Fx.p(p.getWorld(), Particle.CLOUD, p.getLocation(), 30, 0.4, 0.2, 0.4, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.2f);
        barra(p, "§b" + nome + " ativado: §f" + dur + "s");
        voo.tarefa = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!p.isOnline()) return;
            voo.restante--;
            if (voo.restante <= 0) {
                terminarVoo(p, true);
                return;
            }
            barra(p, (voo.restante <= 5 ? "§c" : "§b") + nome + ": §f" + voo.restante + "s");
            if (voo.restante <= 5) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.5f);
        }, 20L, 20L);
    }

    private void terminarVoo(Player p, boolean avisar) {
        Voo voo = voando.remove(p.getUniqueId());
        if (voo == null) return;
        if (voo.tarefa != null) voo.tarefa.cancel();
        GameMode gm = p.getGameMode();
        if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && !voo.podiaVoarAntes) {
            if (avisar && podeVoarNoTerreno(p)) {
                vooTerreno.add(p.getUniqueId());
                barra(p, "§aO voo acabou, mas voce esta no seu terreno: continua voando.");
                return;
            }
            vooTerreno.remove(p.getUniqueId());
            p.setFlying(false);
            p.setAllowFlight(false);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0));
        }
        if (avisar) barra(p, "§eO voo acabou. Caindo devagar...");
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent e) {
        terminarVoo(e.getPlayer(), false);
        desligarVooTerreno(e.getPlayer(), false);
        gracaSaida.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ voo no terreno (GriefPrevention)

    boolean vooTerrenoLigado(Player p) {
        Byte b = p.getPersistentDataContainer().get(chaveVooTerreno, PersistentDataType.BYTE);
        return b == null || b != 0; // ligado por padrao
    }

    /** Ligado + permissao + dentro de terreno proprio (ou onde pode construir). */
    boolean podeVoarNoTerreno(Player p) {
        if (!p.hasPermission("poderes.voar") || !vooTerrenoLigado(p)) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) return false;
        try {
            return TerrenoGP.podeVoarAqui(p);
        } catch (Throwable t) {
            return false;
        }
    }

    private void desligarVooTerreno(Player p, boolean avisar) {
        if (!vooTerreno.remove(p.getUniqueId())) return;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;
        boolean estavaVoando = p.isFlying();
        p.setFlying(false);
        p.setAllowFlight(false);
        if (estavaVoando) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0));
            if (avisar) barra(p, "§eVoo do terreno desligado. Caindo devagar...");
        }
    }

    private void verificarVooTerreno() {
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            GameMode gm = p.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
                vooTerreno.remove(id);
                gracaSaida.remove(id);
                continue;
            }
            if (voando.containsKey(id)) continue; // a Pena de Voo cuida
            boolean pode = podeVoarNoTerreno(p) && logado(p);
            if (pode) {
                gracaSaida.remove(id);
                if (!p.getAllowFlight()) {
                    p.setAllowFlight(true);
                    vooTerreno.add(id);
                    barra(p, "§aVoo liberado no seu terreno §7(pule duas vezes para voar)");
                }
                continue;
            }
            if (!vooTerreno.contains(id)) continue;
            if (p.isFlying() && vooTerrenoLigado(p)) {
                Long fim = gracaSaida.get(id);
                if (fim == null) {
                    gracaSaida.put(id, agora + 3000L);
                    barra(p, "§eVoce saiu do terreno: o voo acaba em 3s");
                    continue;
                }
                if (agora < fim) continue;
            }
            gracaSaida.remove(id);
            desligarVooTerreno(p, true);
        }
    }

    @EventHandler
    public void aoMorrer(PlayerDeathEvent e) {
        terminarVoo(e.getEntity(), false);
    }

    // ------------------------------------------------------------------ lobo guerreiro

    boolean darLobo(Player p) {
        int max = getConfig().getInt("lobos.maximo-por-jogador", 4);
        long tem = p.getWorld().getEntitiesByClass(Wolf.class).stream()
                .filter(w -> p.getUniqueId().equals(w.getOwnerUniqueId()) && !hab.ehLoboEspiritual(w)).count();
        if (tem >= max) {
            p.sendMessage("§eVoce ja tem " + tem + " lobos neste mundo (maximo " + max + "). Nao veio outro.");
            return false;
        }
        Wolf lobo = p.getWorld().spawn(p.getLocation(), Wolf.class, w -> {
            w.setTamed(true);
            w.setOwner(p);
            w.setAdult();
            w.customName(txt("§6Lobo de " + p.getName()));
        });
        AttributeInstance vida = lobo.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) {
            vida.setBaseValue(40.0);
            lobo.setHealth(40.0);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 1f);
        Fx.p(p.getWorld(), Particle.HEART, lobo.getLocation().add(0, 1, 0), 6, 0.4, 0.3, 0.4, 0.1);
        p.sendMessage("§6Um lobo guerreiro agora te segue! §7Ele ataca quem voce bater e quem te atacar.");
        return true;
    }

    // ------------------------------------------------------------------ menu

    private static ItemStack vidro(Material m) {
        ItemStack vidro = new ItemStack(m);
        ItemMeta vm = vidro.getItemMeta();
        vm.displayName(txt(" "));
        vidro.setItemMeta(vm);
        return vidro;
    }

    /** Menu das habilidades da classe do jogador (abre o de classes se ainda nao escolheu). */
    void abrirMenu(Player p) {
        Classe c = classe(p);
        if (c == null) {
            abrirMenuClasses(p);
            return;
        }
        MenuHolder h = new MenuHolder();
        Inventory inv = Bukkit.createInventory(h, 36, txt("§8Habilidades - " + c.nome));
        h.inv = inv;
        ItemStack fundo = vidro(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 36; i++) inv.setItem(i, fundo);

        int nivel = nivelDePoder(p);
        Poder sel = selecionado(p);

        ItemStack ic = new ItemStack(c.icone);
        ItemMeta cm = ic.getItemMeta();
        cm.displayName(txt(c.cor + "§lClasse: " + c.nome));
        List<Component> cl = new ArrayList<>();
        cl.add(txt("§7" + c.desc1));
        cl.add(txt("§7" + c.desc2));
        cl.add(txt(""));
        cl.add(txt("§7Seu nivel de poder: §e" + (nivel < 0 ? "?" : String.valueOf(nivel))));
        cl.add(txt("§7(soma de todas as suas skills, /skills)"));
        cm.lore(cl);
        cm.setEnchantmentGlintOverride(true);
        ic.setItemMeta(cm);
        inv.setItem(4, ic);
        if (c == Classe.LUTADOR) inv.setItem(22, itemPassivaLutador(p, nivel));

        int[] slots = {11, 12, 13, 14, 15};
        for (int i = 0; i < c.poderes.length && i < slots.length; i++) {
            Poder po = c.poderes[i];
            boolean lib = liberado(p, po, nivel);
            ItemStack it = new ItemStack(lib ? po.icone : Material.GRAY_DYE);
            ItemMeta m = it.getItemMeta();
            m.displayName(txt((lib ? po.cor + "§l" : "§7") + (i + 1) + ". " + po.nome + (po == sel ? " §a(atual)" : "")));
            List<Component> lore = new ArrayList<>();
            lore.add(txt("§7" + po.desc1));
            lore.add(txt("§7" + po.desc2));
            lore.add(txt(""));
            lore.add(txt("§fLibera no nivel de poder: §e" + nivelNecessario(po)));
            String dano = danoTexto(po, fator(p));
            if (dano != null) lore.add(txt("§fDano: §c" + dano));
            lore.add(txt("§fRecarga: §e" + po.recargaSeg + "s"));
            lore.add(txt(""));
            if (!lib) lore.add(txt("§cBloqueado: suba suas skills (/skills)"));
            else if (barraItens != null && barraItens.ligado()) lore.add(txt("§eNa barra: slot " + (i + 1)));
            else if (po == sel) lore.add(txt("§aEsta e a sua habilidade atual"));
            else lore.add(txt("§eClique para usar esta habilidade"));
            m.lore(lore);
            if (po == sel) m.setEnchantmentGlintOverride(true);
            m.getPersistentDataContainer().set(chaveMenu, PersistentDataType.STRING, po.id);
            it.setItemMeta(m);
            inv.setItem(slots[i], it);
        }

        ItemStack troca = new ItemStack(Material.COMPASS);
        ItemMeta tm = troca.getItemMeta();
        tm.displayName(txt("§b§lTrocar de classe"));
        List<Component> tl = new ArrayList<>();
        long falta = minutosParaTrocar(p);
        tl.add(txt(falta > 0 ? "§cLibera em " + falta + " minuto(s)" : "§7Clique para ver as classes"));
        tm.lore(tl);
        tm.getPersistentDataContainer().set(chaveBotao, PersistentDataType.STRING, "classes");
        troca.setItemMeta(tm);
        inv.setItem(30, troca);

        ItemStack livro = new ItemStack(Material.BOOK);
        ItemMeta lm = livro.getItemMeta();
        lm.displayName(txt("§e§lComo usar"));
        List<Component> lore = new ArrayList<>();
        if (barraItens != null && barraItens.ligado()) {
            lore.add(txt("§fSlots 1 a 5 da barra §7= suas 5 habilidades."));
            lore.add(txt("§7Segure o item e clique para usar."));
            lore.add(txt("§fItem Habilidades §7(livro) liga/desliga a barra."));
        } else {
            lore.add(txt("§fAgache + soco no ar com a mao vazia"));
            lore.add(txt("§7= usa a habilidade escolhida."));
            lore.add(txt("§fAgache + F §7= troca para a proxima."));
        }
        lore.add(txt(""));
        lore.add(txt("§7Suba suas skills para liberar"));
        lore.add(txt("§7as outras habilidades e deixar"));
        lore.add(txt("§7todas mais fortes."));
        lore.add(txt(""));
        lore.add(txt("§bPena de Voo§7: clique direito nela"));
        lore.add(txt("§aVoo no terreno§7: pule 2x no seu terreno"));
        lore.add(txt("§7(liga/desliga com /voar)"));
        lore.add(txt("§6Duelo de pets§7: /duelopet <jogador>"));
        lm.lore(lore);
        livro.setItemMeta(lm);
        inv.setItem(32, livro);

        p.openInventory(inv);
    }

    /** Item da passiva do Lutador no menu /poderes (bonus de agora e o maximo). */
    private ItemStack itemPassivaLutador(Player p, int nivel) {
        ItemStack it = new ItemStack(Material.LEATHER);
        ItemMeta m = it.getItemMeta();
        m.displayName(txt("§6§lPassiva: Punhos de Aco"));
        double agora = Lutador.bonusSoco(Math.max(0, nivel), lut.nivelMaximo(), lut.bonusMin(), lut.bonusMax());
        List<Component> l = new ArrayList<>();
        l.add(txt("§7Sempre ativa, sem gastar nada."));
        l.add(txt("§7Soco de §fmao vazia§7 da dano a mais:"));
        l.add(txt(""));
        l.add(txt("§fAgora: §c+" + num(agora) + " §7de dano por soco"));
        l.add(txt("§fNo nivel " + lut.nivelMaximo() + ": §c+" + num(lut.bonusMax()) + " §7(o maximo)"));
        l.add(txt(""));
        l.add(txt("§7Espere o braco carregar: soco fraco"));
        l.add(txt("§7da bonus fraco. Critico (caindo) = §e1,5x§7."));
        l.add(txt("§7Punhos em Chamas: §c+50%§7 e explode."));
        m.lore(l);
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    /** Menu para escolher (ou trocar) a classe. */
    void abrirMenuClasses(Player p) {
        MenuClassesHolder h = new MenuClassesHolder();
        Classe atual = classe(p);
        Inventory inv = Bukkit.createInventory(h, 36, txt(atual == null ? "§8Escolha sua classe" : "§8Trocar de classe"));
        h.inv = inv;
        ItemStack fundo = vidro(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 36; i++) inv.setItem(i, fundo);
        long falta = minutosParaTrocar(p);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 21, 23, 22};
        Classe[] todas = Classe.values();
        for (int i = 0; i < todas.length && i < slots.length; i++) {
            Classe c = todas[i];
            ItemStack it = new ItemStack(c.icone);
            ItemMeta m = it.getItemMeta();
            m.displayName(txt(c.cor + "§l" + c.nome + (c == atual ? " §a(atual)" : "")));
            List<Component> lore = new ArrayList<>();
            lore.add(txt("§7" + c.desc1));
            lore.add(txt("§7" + c.desc2));
            lore.add(txt(""));
            lore.add(txt("§fHabilidades:"));
            for (int k = 0; k < c.poderes.length; k++) {
                Poder po = c.poderes[k];
                lore.add(txt("§8 " + (k + 1) + ". " + po.cor + po.nome + " §8(nivel " + nivelPosicao(k) + ")"));
            }
            if (c == Classe.LUTADOR) {
                lore.add(txt("§6Passiva: Punhos de Aco"));
                lore.add(txt("§7 Soco de mao vazia: §c+" + num(lut.bonusMin()) + " §7a §c+" + num(lut.bonusMax()) + " §7de dano"));
                lore.add(txt("§7 (cresce com o nivel ate o nivel " + lut.nivelMaximo() + ")"));
            }
            if (c == Classe.CIPO && venenoCipo != null) {
                lore.add(txt("§2Passiva: Seiva Venenosa"));
                lore.add(txt("§7 Todo ataque envenena: §a" + num(venenoCipo.danoMin()) + " §7a §a" + num(venenoCipo.danoMax()) + " §7de vida/s por " + venenoCipo.segundos() + " s"));
                lore.add(txt("§7 (cresce com o nivel ate o nivel " + venenoCipo.nivelMaximo() + ")"));
            }
            lore.add(txt(""));
            boolean trancada = travas != null && !travas.liberada(p, c);
            if (trancada) {
                int exige = travas.vipExigido(c);
                lore.add(txt("§c§l🔒 TRANCADA"));
                lore.add(txt("§7Só para " + (exige >= 3 ? "§6MVP §7e §dLENDA" : exige == 2 ? "§bVIP+ §7para cima" : "§aVIPs")));
                if (travas.preco(c) > 0) {
                    lore.add(txt("§7ou compre por §6" + Travas.fmt(travas.preco(c)) + " coronas"));
                    lore.add(txt("§7no §eVendedor de Classes §7(lobby das masmorras)"));
                }
            } else if (travas != null && travas.vipExigido(c) > 0) lore.add(txt("§a✔ Liberada para você" + (Travas.comprou(p, c) ? " §7(comprada)" : " §7(VIP)")));
            if (c == atual) lore.add(txt("§aEsta e a sua classe"));
            else if (trancada) lore.add(txt("§8Clique para ver como liberar"));
            else if (falta > 0) lore.add(txt("§cTroca liberada em " + falta + " minuto(s)"));
            else lore.add(txt("§eClique para escolher"));
            m.lore(lore);
            if (c == atual) m.setEnchantmentGlintOverride(true);
            m.getPersistentDataContainer().set(chaveMenuClasse, PersistentDataType.STRING, c.id);
            it.setItemMeta(m);
            inv.setItem(slots[i], it);
        }
        ItemStack livro = new ItemStack(Material.BOOK);
        ItemMeta lm = livro.getItemMeta();
        lm.displayName(txt("§e§lComo funciona"));
        List<Component> lore = new ArrayList<>();
        lore.add(txt("§7Cada classe tem 5 habilidades."));
        lore.add(txt("§7Elas liberam conforme seu"));
        lore.add(txt("§7nivel de poder sobe (/skills)."));
        lore.add(txt(""));
        long min = getConfig().getLong("classes.troca-recarga-minutos", 60L);
        lore.add(txt("§7A primeira escolha e livre."));
        if (min > 0) lore.add(txt("§7Depois, da para trocar a cada §f" + min + " min§7."));
        lm.lore(lore);
        livro.setItemMeta(lm);
        inv.setItem(31, livro);
        p.openInventory(inv);
    }

    static String num(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    /** Texto do dano no menu (ja com a forca do nivel do jogador). 2 de dano = 1 coracao. */
    static String danoTexto(Poder po, double f) {
        return switch (po) {
            case FOGO -> num(DANO_FOGO * f) + " §7+ queimadura";
            case GELO -> num(DANO_GELO * f) + " §7+ lentidao";
            case VENENO -> num(Habilidades.DANO_VENENO * f) + " §7por segundo (5s) + veneno";
            case RAIO -> num(DANO_RAIO * f) + " §7em area + fogo";
            case METEOROS -> num(Habilidades.DANO_METEORO * f) + " §7por meteoro (5 meteoros)";
            case TORRETA -> "~" + num(Torretas.DANO_ARQUEIRA * f) + " §7por flecha (2 por segundo)";
            case TORRE_GELO -> num(Torretas.DANO_GELO * f) + " §7+ lentidao forte";
            case TORRE_FOGO -> num(Torretas.DANO_FOGO * f) + " §7(2 por segundo) + fogo";
            case TORRE_TESLA -> num(Torretas.DANO_TESLA * f) + " §7+ " + num(Torretas.DANO_TESLA_PULO * f) + " em mais 2";
            case TORRE_CANHAO -> num(Torretas.DANO_CANHAO * f) + " §7em area";
            case KI_BLAST -> num(Habilidades.DANO_KI * f) + " §7por esfera (" + Habilidades.KI_RAJADA + ", ou " + Habilidades.KI_RAJADA_SSJ + " transformado)";
            case KAMEHAMEHA -> num(DANO_KAMEHAMEHA * f) + " §7x" + KAMEHAMEHA_ACERTOS + " em 3s (total §c" + num(DANO_KAMEHAMEHA * f * KAMEHAMEHA_ACERTOS) + "§7)";
            case GOLEM -> num(DANO_GOLEM_SOCO * f) + " §7+ explosao ate §c" + num(DANO_GOLEM_EXPLOSAO * f);
            case GRITO -> num(Habilidades.DANO_GRITO * f) + " §7em area + fraqueza";
            case GUARDIAO -> num(DANO_GUARDIAO * f) + " §7por golpe do golem";
            case TERREMOTO -> num(Habilidades.DANO_TERREMOTO * f) + " §7em area (7 blocos)";
            case SHURIKEN -> num(Habilidades.DANO_SHURIKEN * f) + " §7por estrela (3)";
            case LAMINAS -> num(Habilidades.DANO_LAMINAS * f) + " §7x6 em area";
            case ATAQUE_RELAMPAGO -> num(Habilidades.DANO_RELAMPAGO * f) + " §7em ate 5 inimigos";
            case RAIZES -> num(Habilidades.DANO_RAIZES * f) + " §7+ preso por 3s";
            case MATILHA -> num(Habilidades.DANO_LOBO * f) + " §7por mordida (3 lobos)";
            case ESPINHOS -> num(Habilidades.DANO_ESPINHOS * f) + " §7x3 ondas + veneno";
            case ARREMESSO -> num(Habilidades.DANO_ARREMESSO * f);
            case ONDA -> num(Habilidades.DANO_ONDA * f) + " §7em area + empurrao";
            case TRIDENTE_RAIO -> num(Habilidades.DANO_TRIDENTE_RAIO * f) + " §7em ate 3 inimigos";
            case REDEMOINHO -> num(Habilidades.DANO_REDEMOINHO * f) + " §7x8 (4s) + puxa";
            case POSEIDON -> num(Habilidades.DANO_POSEIDON * f) + " §7em area + forca do mar";
            case TIRO_CERTEIRO -> num(Habilidades.DANO_TIRO * f);
            case FLECHA_EXPLOSIVA -> num(Habilidades.DANO_EXPLOSIVA * f) + " §7em area";
            case MULTIPLO -> num(Habilidades.DANO_MULTIPLO * f) + " §7por flecha (5)";
            case CHUVA_FLECHAS -> num(Habilidades.DANO_CHUVA * f) + " §7x12 em area (3s)";
            case TEMPESTADE -> num(Habilidades.DANO_TEMPESTADE * f) + " §7x3 em ate 8 inimigos";
            case RAJADA_SOCOS -> num(Lutador.DANO_RAJADA * f) + " §7x" + Lutador.RAJADA_SOCOS + " socos na frente";
            case GANCHO -> num(Lutador.DANO_GANCHO * f) + " §7em volta (5 blocos) + fogo";
            case SOCO_SISMICO -> num(Lutador.DANO_SISMICO * f) + " §7em linha (14 blocos)";
            case FURIA -> "§7soco +50% e explode §c60% §7em volta";
            case PUNHO_METEORO -> num(Lutador.DANO_METEORO * f) + " §7+ explosao §c" + num(Lutador.DANO_METEORO_EXPLOSAO * f);
            case CHICOTE -> num(Cipo.DANO_CHICOTE * f) + " §7+ puxa o alvo";
            case LACO -> num(Cipo.DANO_LACO * f) + " §7+ sangra 3s + lentidao";
            case FLOR_CARNIVORA -> num(Cipo.DANO_FLOR * f) + " §7por mordida (10s) + veneno";
            case SELVA_VIVA -> num(Cipo.DANO_SELVA * f) + " §7x4 em area (9 blocos) + cura";
            default -> null;
        };
    }

    @EventHandler
    public void cliqueMenu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String botao = it.getItemMeta().getPersistentDataContainer().get(chaveBotao, PersistentDataType.STRING);
        if ("classes".equals(botao)) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            Bukkit.getScheduler().runTask(this, () -> abrirMenuClasses(p));
            return;
        }
        String id = it.getItemMeta().getPersistentDataContainer().get(chaveMenu, PersistentDataType.STRING);
        Poder po = Poder.porId(id);
        if (po == null) return;
        Classe c = classe(p);
        if (c == null || c.posicao(po) < 0) return;
        if (!liberado(p, po, nivelDePoder(p))) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            barra(p, "§c" + po.nome + " libera no nivel de poder " + nivelNecessario(po));
            return;
        }
        selecionar(p, po);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        barra(p, "§aHabilidade escolhida: " + po.cor + po.nome);
        Bukkit.getScheduler().runTask(this, () -> abrirMenu(p));
    }

    @EventHandler
    public void cliqueMenuClasses(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuClassesHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        Classe c = Classe.porId(it.getItemMeta().getPersistentDataContainer().get(chaveMenuClasse, PersistentDataType.STRING));
        if (c == null) return;
        if (classe(p) == c) {
            Bukkit.getScheduler().runTask(this, () -> abrirMenu(p));
            return;
        }
        if (escolherClasse(p, c, false)) Bukkit.getScheduler().runTask(this, () -> abrirMenu(p));
    }

    @EventHandler
    public void arrastarMenu(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof MenuHolder || h instanceof MenuClassesHolder) e.setCancelled(true);
    }

    /** Agachado + F (trocar de mao) com a mao vazia: passa para a proxima habilidade liberada. */
    @EventHandler(ignoreCancelled = true)
    public void trocarHabilidade(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking() || !p.getInventory().getItemInMainHand().getType().isAir()) return;
        Classe c = classe(p);
        if (c == null || !p.hasPermission("poderes.usar")) return;
        if (barraItens != null && barraItens.ligado()) return;
        e.setCancelled(true);
        int nivel = nivelDePoder(p);
        int atual = c.posicao(selecionado(p));
        for (int k = 1; k <= c.poderes.length; k++) {
            Poder po = c.poderes[(atual + k) % c.poderes.length];
            if (!liberado(p, po, nivel)) continue;
            if (po == selecionado(p)) break;
            selecionar(p, po);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.5f);
            barra(p, "§fHabilidade: " + po.cor + "§l" + po.nome + " §8(agache + F troca)");
            return;
        }
        barra(p, "§7So " + selecionado(p).nome + " esta liberada. Suba suas skills (/skills) para liberar mais.");
    }

    /** Quem ainda nao tem classe recebe o menu de escolha logo depois de logar (AuthMe). */
    @EventHandler
    public void aoEntrar(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        atualizarTab(p);
        Fx.esconderDe(p);
        // classe trancada (VIP acabou, ou regra nova): tira a classe e avisa; ele escolhe outra no menu
        Classe atualC = classe(p);
        if (atualC != null && travas != null && !travas.liberada(p, atualC) && !p.hasPermission("poderes.admin")) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || classe(p) != atualC || travas.liberada(p, atualC)) return;
                torretas.remover(p.getUniqueId(), false);
                guardioes.remover(p.getUniqueId(), false);
                hab.limpar(p);
                p.getPersistentDataContainer().remove(chaveClasse);
                p.getPersistentDataContainer().remove(chaveTrocaClasse);
                if (barraItens != null) barraItens.aoTrocarClasse(p);
                atualizarTab(p);
                p.sendMessage("§c§l>> §cA classe " + atualC.cor + "§l" + atualC.nome + " §cagora é trancada. " + travas.porque(atualC));
                p.sendMessage("§7Escolha outra classe de graça: §f/classe§7.");
                abrirMenuClasses(p);
            }, 100L);
        }
        if (classe(p) != null || !p.hasPermission("poderes.usar")) return;
        final int[] tentativas = {0};
        Bukkit.getScheduler().runTaskTimer(this, tarefa -> {
            tentativas[0]++;
            if (!p.isOnline() || classe(p) != null || tentativas[0] > 300) {
                tarefa.cancel();
                return;
            }
            if (!logado(p)) return;
            tarefa.cancel();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || classe(p) != null) return;
                p.sendMessage("§6§l>> §eEscolha a sua classe! §7Cada uma tem 5 habilidades que liberam com o seu nivel.");
                p.sendMessage("§7Fechou sem querer? Use §f/classe§7.");
                abrirMenuClasses(p);
            }, 40L);
        }, 20L, 20L);
    }

    // ------------------------------------------------------------------ comandos

    private static org.bukkit.Color corDaCategoria(String c) {
        return switch (c) {
            case "poeira_vermelho" -> org.bukkit.Color.fromRGB(240, 40, 40); case "poeira_laranja" -> org.bukkit.Color.fromRGB(255, 140, 25);
            case "poeira_amarelo" -> org.bukkit.Color.fromRGB(255, 230, 50); case "poeira_verde" -> org.bukkit.Color.fromRGB(75, 230, 75);
            case "poeira_azul" -> org.bukkit.Color.fromRGB(65, 115, 255); case "poeira_ciano" -> org.bukkit.Color.fromRGB(75, 230, 255);
            case "poeira_roxo" -> org.bukkit.Color.fromRGB(150, 65, 230); case "poeira_rosa" -> org.bukkit.Color.fromRGB(255, 130, 205);
            case "poeira_preto" -> org.bukkit.Color.fromRGB(30, 25, 35); case "poeira_marrom" -> org.bukkit.Color.fromRGB(140, 90, 40);
            case "poeira_dourado" -> org.bukkit.Color.fromRGB(255, 205, 75); default -> org.bukkit.Color.fromRGB(245, 245, 245);
        };
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String nome = cmd.getName().toLowerCase(Locale.ROOT);
        if (nome.equals("duelopet")) return duelos.comando(s, args);
        if (nome.equals("petpoder")) return petPoderes.comando(s, args);
        if (nome.equals("classe")) return comandoClasse(s, args);
        if (nome.equals("voar")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Somente jogadores.");
                return true;
            }
            if (!p.hasPermission("poderes.voar")) {
                p.sendMessage("§cSem permissao.");
                return true;
            }
            boolean ligar = !vooTerrenoLigado(p);
            p.getPersistentDataContainer().set(chaveVooTerreno, PersistentDataType.BYTE, (byte) (ligar ? 1 : 0));
            if (ligar) {
                p.sendMessage("§aVoo no terreno LIGADO. §7Dentro do seu terreno (ou onde voce pode construir), pule duas vezes para voar.");
            } else {
                desligarVooTerreno(p, true);
                p.sendMessage("§cVoo no terreno DESLIGADO. §7Use /voar para ligar de novo.");
            }
            return true;
        }

        if (args.length == 0) {
            if (s instanceof Player p) {
                if (barraItens != null && barraItens.ligado() && classe(p) != null) barraItens.alternar(p);
                else abrirMenu(p);
            } else s.sendMessage("Uso: poderes darpena <jogador> [qtd] | poderes darlobo <jogador>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                if (s instanceof Player p) abrirMenu(p);
                else s.sendMessage("Somente jogadores.");
                return true;
            }
            case "barra" -> {
                if (s instanceof Player p) {
                    if (barraItens != null && barraItens.ligado()) barraItens.alternar(p);
                    else s.sendMessage("§cA barra de habilidades esta desligada no config.");
                } else s.sendMessage("Somente jogadores.");
                return true;
            }
            case "darpena" -> {
                if (!s.hasPermission("poderes.admin")) {
                    s.sendMessage("§cSem permissao.");
                    return true;
                }
                if (args.length < 2) {
                    s.sendMessage("§eUso: /poderes darpena <jogador> [quantidade]");
                    return true;
                }
                Player alvo = Bukkit.getPlayerExact(args[1]);
                if (alvo == null) {
                    s.sendMessage("§cJogador nao encontrado: " + args[1]);
                    return true;
                }
                int qtd = 1;
                if (args.length >= 3) {
                    try {
                        qtd = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                        qtd = 1;
                    }
                }
                Map<Integer, ItemStack> sobra = alvo.getInventory().addItem(criarPena(qtd));
                for (ItemStack resto : sobra.values()) alvo.getWorld().dropItem(alvo.getLocation(), resto);
                alvo.sendMessage("§bVoce recebeu " + qtd + "x Pena de Voo! §7Clique direito para voar.");
                if (!s.equals(alvo)) s.sendMessage("§aDei " + qtd + " pena(s) para " + alvo.getName() + ".");
                return true;
            }
            case "darlobo" -> {
                if (!s.hasPermission("poderes.admin")) {
                    s.sendMessage("§cSem permissao.");
                    return true;
                }
                if (args.length < 2) {
                    s.sendMessage("§eUso: /poderes darlobo <jogador>");
                    return true;
                }
                Player alvo = Bukkit.getPlayerExact(args[1]);
                if (alvo == null) {
                    s.sendMessage("§cJogador nao encontrado: " + args[1]);
                    return true;
                }
                darLobo(alvo);
                return true;
            }
            case "fx" -> {
                // teste dos efeitos do celular: /poderes fx [categoria|tudo]
                if (!s.hasPermission("poderes.admin") || !(s instanceof Player jp)) {
                    s.sendMessage("§cSem permissao.");
                    return true;
                }
                java.util.List<String> cats = Fx.categoriasConhecidas();
                String pedido = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "tudo";
                org.bukkit.Location base = jp.getLocation().add(jp.getLocation().getDirection().setY(0).normalize().multiply(3));
                int i = 0;
                for (String c : cats) {
                    if (!pedido.equals("tudo") && !c.equals(pedido)) continue;
                    org.bukkit.Location l = pedido.equals("tudo") ? base.clone().add((i % 6) * 3 - 7.5, 1 + (i / 6) * 3, 0) : base.clone().add(0, 1, 0);
                    final org.bukkit.Location lf = l;
                    org.bukkit.Particle pt = switch (c) {
                        case "fogo" -> org.bukkit.Particle.FLAME; case "alma" -> org.bukkit.Particle.SOUL_FIRE_FLAME; case "gelo" -> org.bukkit.Particle.SNOWFLAKE;
                        case "raio" -> org.bukkit.Particle.ELECTRIC_SPARK; case "luz" -> org.bukkit.Particle.END_ROD; case "fumaca" -> org.bukkit.Particle.CLOUD;
                        case "sombra" -> org.bukkit.Particle.SQUID_INK; case "cura" -> org.bukkit.Particle.HEART; case "agua" -> org.bukkit.Particle.SPLASH;
                        case "golpe" -> org.bukkit.Particle.CRIT; case "explosao" -> org.bukkit.Particle.EXPLOSION; case "natureza" -> org.bukkit.Particle.CHERRY_LEAVES;
                        case "veneno" -> org.bukkit.Particle.ITEM_SLIME; case "terra" -> org.bukkit.Particle.DUST_PILLAR; default -> org.bukkit.Particle.DUST;
                    };
                    final Object dado = pt == org.bukkit.Particle.DUST ? new org.bukkit.Particle.DustOptions(corDaCategoria(c), 1.2f)
                            : pt == org.bukkit.Particle.DUST_PILLAR ? org.bukkit.Material.DIRT.createBlockData() : null;
                    for (int t = 0; t < 60; t += 2) {
                        Bukkit.getScheduler().runTaskLater(this, () -> Fx.p(lf.getWorld(), pt, lf, 12, 0.4, 0.4, 0.4, 0.02, dado), t);
                    }
                    i++;
                }
                s.sendMessage("§aEfeitos de teste (" + pedido + ") por 3 s. Emissores vivos: " + Fx.emissoresVivos() + ". No celular cada um e uma animacao do pacote v2.");
                return true;
            }
            case "recarregar" -> {
                if (!s.hasPermission("poderes.admin")) {
                    s.sendMessage("§cSem permissao.");
                    return true;
                }
                reloadConfig();
                s.sendMessage("§aConfig do Poderes recarregada (ex.: lutador.soco-bonus-max = " + getConfig().getDouble("lutador.soco-bonus-max") + ").");
                return true;
            }
            case "ajuda" -> {
                s.sendMessage("§6--- Poderes e Classes ---");
                s.sendMessage("§f/classe §7= escolhe ou troca a sua classe.");
                s.sendMessage("§f/poderes §7(ou o item Habilidades) = liga/desliga a barra de habilidades.");
                s.sendMessage("§fSlots 1 a 5 §7= as 5 habilidades da classe: segure o item e clique para usar.");
                s.sendMessage("§f/poderes menu §7= detalhes das habilidades (dano, recarga, nivel).");
                s.sendMessage("§f/duelopet <jogador> §7= duelo de pets.");
                s.sendMessage("§f/voar §7= liga/desliga o voo dentro do seu terreno.");
                return true;
            }
            default -> {
                Poder po = Poder.porId(sub);
                if (po == null || !(s instanceof Player p)) {
                    s.sendMessage("§eUse /poderes para abrir o menu, ou /poderes ajuda.");
                    return true;
                }
                Classe c = classe(p);
                if (c == null) {
                    s.sendMessage("§eEscolha sua classe primeiro: §f/classe");
                    return true;
                }
                if (c.posicao(po) < 0) {
                    s.sendMessage("§c" + po.nome + " nao e da classe " + c.nome + ".");
                    return true;
                }
                if (!liberado(p, po, nivelDePoder(p))) {
                    s.sendMessage("§c" + po.nome + " libera no nivel de poder " + nivelNecessario(po) + ".");
                    return true;
                }
                selecionar(p, po);
                s.sendMessage("§aHabilidade escolhida: " + po.cor + po.nome + (barraItens != null && barraItens.ligado()
                        ? "§a. Ela esta no slot " + (c.posicao(po) + 1) + " da barra." : "§a. Agache + soco no ar com a mao vazia."));
                return true;
            }
        }
    }

    private boolean comandoClasse(CommandSender s, String[] args) {
        if (args.length == 0) {
            if (s instanceof Player p) abrirMenuClasses(p);
            else s.sendMessage("Uso: classe definir <jogador> <classe> | classe resetar <jogador> | classe ver <jogador>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "definir", "resetar", "ver" -> {
                if (!s.hasPermission("poderes.admin")) {
                    s.sendMessage("§cSem permissao.");
                    return true;
                }
                if (args.length < 2) {
                    s.sendMessage("§eUso: /classe " + sub + " <jogador>" + (sub.equals("definir") ? " <classe>" : ""));
                    return true;
                }
                Player alvo = Bukkit.getPlayerExact(args[1]);
                if (alvo == null) {
                    s.sendMessage("§cJogador nao encontrado (precisa estar online): " + args[1]);
                    return true;
                }
                if (sub.equals("ver")) {
                    Classe c = classe(alvo);
                    s.sendMessage("§e" + alvo.getName() + ": " + (c == null ? "§7sem classe" : c.cor + c.nome)
                            + " §7| nivel de poder " + nivelDePoder(alvo) + " | habilidade " + selecionado(alvo).nome);
                    return true;
                }
                if (sub.equals("resetar")) {
                    torretas.remover(alvo.getUniqueId(), false);
                    guardioes.remover(alvo.getUniqueId(), false);
                    hab.limpar(alvo);
                    alvo.getPersistentDataContainer().remove(chaveClasse);
                    alvo.getPersistentDataContainer().remove(chaveTrocaClasse);
                    if (barraItens != null) barraItens.aoTrocarClasse(alvo);
                    atualizarTab(alvo);
                    alvo.sendMessage("§eSua classe foi resetada. Escolha uma nova com §f/classe§e.");
                    s.sendMessage("§aClasse de " + alvo.getName() + " resetada.");
                    return true;
                }
                Classe c = args.length >= 3 ? Classe.porId(args[2]) : null;
                if (c == null) {
                    s.sendMessage("§cClasse invalida. Opcoes: " + String.join(", ", idsClasses()));
                    return true;
                }
                escolherClasse(alvo, c, true);
                s.sendMessage("§aClasse de " + alvo.getName() + " agora e " + c.nome + ".");
                return true;
            }
            default -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage("Somente jogadores.");
                    return true;
                }
                Classe c = Classe.porId(sub);
                if (c == null) {
                    s.sendMessage("§cClasse invalida. Opcoes: " + String.join(", ", idsClasses()));
                    return true;
                }
                escolherClasse(p, c, false);
                return true;
            }
        }
    }

    private static List<String> idsClasses() {
        List<String> r = new ArrayList<>();
        for (Classe c : Classe.values()) r.add(c.id);
        return r;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> r = new ArrayList<>();
        String nome = cmd.getName().toLowerCase(Locale.ROOT);
        if (nome.equals("petpoder")) {
            if (args.length == 1 && s.hasPermission("poderes.admin")) r.add("supremo");
            if (args.length == 2 && s.hasPermission("poderes.admin")) for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
        } else if (nome.equals("duelopet")) {
            if (args.length == 1) {
                r.add("aceitar");
                r.add("recusar");
                for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
            }
        } else if (nome.equals("classe")) {
            if (args.length == 1) {
                r.addAll(idsClasses());
                if (s.hasPermission("poderes.admin")) {
                    r.add("definir");
                    r.add("resetar");
                    r.add("ver");
                }
            } else if (args.length == 2 && s.hasPermission("poderes.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
            } else if (args.length == 3 && s.hasPermission("poderes.admin") && args[0].equalsIgnoreCase("definir")) {
                r.addAll(idsClasses());
            }
        } else if (args.length == 1) {
            Classe c = s instanceof Player pl ? classe(pl) : null;
            for (Poder p : c != null ? c.poderes : Poder.values()) r.add(p.id);
            r.add("ajuda");
            r.add("menu");
            r.add("barra");
            if (s.hasPermission("poderes.admin")) {
                r.add("darpena");
                r.add("darlobo");
            }
        } else if (args.length == 2 && s.hasPermission("poderes.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
        }
        String pre = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(pre));
        return r;
    }
}
