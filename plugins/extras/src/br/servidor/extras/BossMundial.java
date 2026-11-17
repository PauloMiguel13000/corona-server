package br.servidor.extras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Boss mundial (10/11): o Colosso da Coroa Partida nasce de tempos em tempos no mundo aberto; o servidor avisa todo
 * mundo e /boss leva ate perto dele. O corpo e um esqueleto wither gigante invisivel; quem tem o pacote (ou esta no
 * Bedrock) ve o modelo 3D corona:props/anim_colosso_f0|f1, quem nao tem ve a armadura gigante. A vida e contada aqui
 * (cresce com quem entra na luta). Poucos efeitos por golpe, para nao pesar no celular. Premio em coronas para todos
 * que bateram, com bonus para os 3 que deram mais dano.
 */
final class BossMundial implements Listener {

    private static final UUID PACOTE = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final double ESCALA = 3.0;                 // esqueleto wither: 2,4 blocos -> 7,2
    private static final float ESC_MODELO = (float) (2.4 * ESCALA / 2.25);

    private final Extras pl;
    private final NamespacedKey kBoss, kAjudante, kDrop, kBussola;
    private final Random rnd = new Random();
    private final java.util.Set<UUID> comPacote = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private WitherSkeleton corpo;
    private ColossoCorpo corpo3d;
    private BossBar barra;
    private BukkitTask tarefa;
    /** 17/11 (Paulo: um jogador sozinho matava facil): bastante mais vida e um pouco mais de dano que a config. */
    static final double MULT_VIDA = 2.0;
    static final double MULT_DANO = 1.2;
    private double vida, vidaMax;
    private int ticks, proximoGolpe;
    private long nasceu;
    private final Map<UUID, Double> dano = new HashMap<>();
    private final Map<UUID, String> nomes = new HashMap<>();
    private final List<Entity> ajudantes = new ArrayList<>();
    private final java.util.Set<UUID> naBarra = new java.util.HashSet<>();
    private long proximoNascimento;

    // ---- 17/11 (sugestao do Sui, Paulo: "criar uma arena pra ele em que a galera pudesse entrar pelo comando ou a bussola")
    /** Arena no ceu do mundo das masmorras: paredes em +-RAIO do centro, pes em ay, teto de barreira em ay+18. */
    /** 17/11 (Sui: "tem que ser espacosa ... tipo os templos das masmorras, em escala bem maior"): versao 2, templo grande. */
    static final int RAIO = 40, ALTURA = 24, VERSAO_ARENA = 2;
    private int ax, ay, az;
    private boolean arenaPronta;
    /** De onde cada um veio quando entrou na arena (volta para la quando a luta acaba). */
    private final Map<UUID, Location> volta = new HashMap<>();
    private final Map<UUID, Long> esperaBussola = new HashMap<>();
    /** Chefe de masmorra chamado pelo Colosso (2/3 da vida: nivel 3; 1/3: nivel 4). Enquanto ele vive o Colosso nao sente dano. */
    private LivingEntity invocado;
    private String nomeInvocado = "";
    private int fase;
    private final List<long[]> tickets = new ArrayList<>();

    BossMundial(Extras pl) {
        this.pl = pl;
        this.kBoss = new NamespacedKey(pl, "boss_mundial");
        this.kAjudante = new NamespacedKey(pl, "boss_ajudante");
        this.kDrop = new NamespacedKey(pl, "boss_drop");
        this.kBussola = new NamespacedKey(pl, "boss_bussola");
    }

    /** O boss so existe no mundo das masmorras, nunca no survival puro (pedido do Paulo, 10/11). */
    private World mundoDoBoss() {
        String nome = pl.getConfig().getString("boss-mundial.mundo", "masmorras_mundo");
        if (!nome.startsWith("masmorras")) nome = "masmorras_mundo";
        return Bukkit.getWorld(nome);
    }

    private boolean ativo() {
        return pl.getConfig().getBoolean("boss-mundial.ativo", true);
    }

    private String nome() {
        return pl.getConfig().getString("boss-mundial.nome", "Colosso da Coroa Partida");
    }

    /**
     * 16/11 (Paulo: "o boss mundial nunca spawna"): a contagem de 120 min recomecava a cada reinicio do servidor (foram
     * 27 reinicios no dia) e nunca chegava ao fim. Agora a hora do proximo fica salva em boss-mundial.txt: depois de um
     * reinicio ele nasce no maximo em 10 min se ja tinha passado da hora. Sem jogador online ou sem lugar, tenta de novo
     * no minuto seguinte (antes perdia a vez e esperava mais 2 horas). Aviso 5 min antes.
     */
    private boolean avisou5;

    private java.io.File arqProximo() {
        return new java.io.File(pl.getDataFolder(), "boss-mundial.txt");
    }

    private void salvarProximo() {
        try {
            java.nio.file.Files.writeString(arqProximo().toPath(), String.valueOf(proximoNascimento));
        } catch (Exception ex) {
            pl.getLogger().warning("Boss mundial: nao salvei a hora do proximo: " + ex.getMessage());
        }
    }

    void iniciar() {
        Bukkit.getPluginManager().registerEvents(this, pl);
        if (!ativo()) return;
        Bukkit.getScheduler().runTaskLater(pl, this::prepararArena, 200L);
        long min = Math.max(10, pl.getConfig().getLong("boss-mundial.a-cada-minutos", 120));
        long agora = System.currentTimeMillis(), salvo = 0;
        try {
            salvo = Long.parseLong(java.nio.file.Files.readString(arqProximo().toPath()).trim());
        } catch (Exception ignored) {
        }
        if (salvo <= 0) proximoNascimento = agora + 30 * 60_000L;
        else proximoNascimento = Math.max(salvo, agora + 10 * 60_000L);
        if (salvo > 0 && salvo > agora + min * 60_000L) proximoNascimento = agora + min * 60_000L;   // hora absurda no arquivo
        salvarProximo();
        pl.getLogger().info("Boss mundial: proximo em " + (proximoNascimento - agora) / 60_000L + " min");
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (vivo()) return;
            long t = System.currentTimeMillis();
            World w = mundoDoBoss();
            boolean gente = Bukkit.getOnlinePlayers().size() >= pl.getConfig().getInt("boss-mundial.minimo-de-jogadores", 1);
            long falta = proximoNascimento - t;
            if (!avisou5 && gente && falta > 0 && falta <= 5 * 60_000L) {
                avisou5 = true;
                Bukkit.broadcast(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §eO §6" + nome() + " §edesperta em §f" + Math.max(1, (falta + 59_999) / 60_000L)
                        + " min §eno Mundo das Masmorras! §7(/boss)"));
            }
            if (falta > 0) return;
            if (w == null || !gente) return;   // tenta de novo no proximo minuto
            Location l = arenaPronta ? centroArena(w) : lugar(w);
            if (l == null) return;
            nascer(l);
            if (vivo()) {
                proximoNascimento = System.currentTimeMillis() + min * 60_000L;
                avisou5 = false;
                salvarProximo();
            }
        }, 20L * 60, 20L * 60);
    }

    void parar() {
        acabar(false);
    }

    boolean vivo() {
        return corpo != null && corpo.isValid() && !corpo.isDead();
    }

    // ------------------------------------------------------------------ pacote (quem ve o modelo)

    @EventHandler(priority = EventPriority.MONITOR)
    public void pacote(PlayerResourcePackStatusEvent e) {
        if (!PACOTE.equals(e.getID())) return;
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> comPacote.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> comPacote.remove(e.getPlayer().getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        comPacote.remove(e.getPlayer().getUniqueId());
        naBarra.remove(e.getPlayer().getUniqueId());
    }

    private boolean ve3d(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || comPacote.contains(p.getUniqueId());
    }

    // ------------------------------------------------------------------ nascer

    /** Um lugar de chao firme a 150-350 blocos do spawn do mundo, fora da agua. */
    private Location lugar(World w) {
        Location s = w.getSpawnLocation();
        for (int t = 0; t < 25; t++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 150 + rnd.nextInt(200);
            int x = s.getBlockX() + (int) (Math.cos(a) * d), z = s.getBlockZ() + (int) (Math.sin(a) * d);
            Block b = w.getHighestBlockAt(x, z);
            if (b.isLiquid() || !b.getType().isSolid() || b.getY() < 55 || b.getType().name().contains("LEAVES")) continue;
            return new Location(w, x + 0.5, b.getY() + 1, z + 0.5);
        }
        return null;
    }

    @SuppressWarnings("UnstableApiUsage")
    private ItemStack item(int q) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/anim_colosso_f" + q));
        return it;
    }

    void nascer(Location l) {
        if (vivo()) return;
        acabar(false);
        l.getChunk().load();
        corpo = l.getWorld().spawn(l, WitherSkeleton.class, c -> {
            c.getPersistentDataContainer().set(kBoss, PersistentDataType.BYTE, (byte) 1);
            c.setPersistent(false);
            c.setRemoveWhenFarAway(false);
            c.setSilent(true);
            c.setCanPickupItems(false);
            c.customName(Extras.txt("§6§l" + nome()));
            c.setCustomNameVisible(false);
            c.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            c.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            set(c, Attribute.SCALE, ESCALA);
            set(c, Attribute.MAX_HEALTH, 1000);
            set(c, Attribute.ATTACK_DAMAGE, pl.getConfig().getDouble("boss-mundial.dano", 9) * MULT_DANO);
            set(c, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            set(c, Attribute.MOVEMENT_SPEED, 0.27);
            set(c, Attribute.FOLLOW_RANGE, 48);
            set(c, Attribute.STEP_HEIGHT, 1.6);
            c.setHealth(1000);
            // quem nao tem o pacote ve a armadura gigante (a armadura aparece mesmo no corpo invisivel)
            var eq = c.getEquipment();
            eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            for (EquipmentSlot s : EquipmentSlot.values()) {
                try {
                    eq.setDropChance(s, 0f);
                } catch (IllegalArgumentException ignorado) {
                    // slot que este mob nao tem
                }
            }
        });
        corpo3d = new ColossoCorpo(pl, ESC_MODELO);
        corpo3d.nascer(l);
        vidaMax = vida = pl.getConfig().getDouble("boss-mundial.vida-base", 600) * MULT_VIDA;
        fase = 0;
        invocado = null;
        if (naArena(l)) segurarChunks(l.getWorld());
        dano.clear();
        nomes.clear();
        ticks = 0;
        proximoGolpe = 100;
        nasceu = System.currentTimeMillis();
        barra = BossBar.bossBar(Extras.txt("§6§l" + nome()), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
        l.getWorld().strikeLightningEffect(l);
        if (naArena(l)) {
            Bukkit.broadcast(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §eO §6" + nome() + " §edespertou na §6Arena do Colosso§e! Quem bater ganha coronas."));
            Bukkit.broadcast(Extras.txt("§7Com 2/3 e 1/3 da vida ele chama um §cchefe de masmorra§7: enquanto o chefe viver, o Colosso não sente dano. Dividam a equipe!"));
            Bukkit.broadcast(Extras.txt("§a§l[ENTRAR NA ARENA]").clickEvent(ClickEvent.runCommand("/boss")).append(Extras.txt(" §7ou digite §f/boss §7ou clique com a §6Bússola do Colosso")));
        } else {
            Bukkit.broadcast(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §eO §6" + nome() + " §edespertou em §f" + l.getBlockX() + ", " + l.getBlockZ() + "§e! Quem bater ganha coronas."));
            Bukkit.broadcast(Extras.txt("§a§l[IR ATÉ O BOSS]").clickEvent(ClickEvent.runCommand("/boss")).append(Extras.txt(" §7ou digite §f/boss")));
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.7f);
        pl.getLogger().info("Boss mundial nasceu em " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        var i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    // ------------------------------------------------------------------ a luta

    private List<Player> perto(double raio) {
        List<Player> l = new ArrayList<>();
        if (!vivo()) return l;
        for (Player p : corpo.getWorld().getPlayers())
            if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && p.getLocation().distanceSquared(corpo.getLocation()) <= raio * raio) l.add(p);
        return l;
    }

    private void tick() {
        if (!vivo()) {
            acabar(false);
            return;
        }
        ticks++;
        Location l = corpo.getLocation();
        // o corpo 3D (8 pecas animadas) segue o esqueleto invisivel
        if (corpo3d != null) corpo3d.tick(l, corpo.getBodyYaw(), ticks);
        if (ticks % 20 == 0) {
            if (corpo.getFireTicks() > 0) corpo.setFireTicks(0);
            // caiu na agua/lava/vazio ou ficou preso: volta para o chao firme mais perto
            Block pe = l.getBlock();
            if (arenaPronta && corpo.getWorld().equals(mundoDoBoss()) && Math.abs(ax - l.getBlockX()) < 200 && Math.abs(az - l.getBlockZ()) < 200) {
                // na arena: nao sai dela (escada de bloco, empurrao, teleporte)
                if (!naArena(l)) corpo.teleport(centroArena(corpo.getWorld()));
            } else if (pe.isLiquid() || l.getY() < corpo.getWorld().getMinHeight() + 2) {
                Block b = corpo.getWorld().getHighestBlockAt(l);
                corpo.teleport(new Location(corpo.getWorld(), l.getX(), b.getY() + 1, l.getZ(), l.getYaw(), 0));
            }
            List<Player> gente = perto(48);
            // quem tem o pacote ve o modelo e nao ve a armadura; quem nao tem ve a armadura
            for (Player p : corpo.getWorld().getPlayers()) {
                boolean v = ve3d(p);
                if (corpo3d != null) corpo3d.ver(p, v);
                if (v && gente.contains(p)) for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND})
                    p.sendEquipmentChange(corpo, s, new ItemStack(Material.AIR));
            }
            // barra de vida so para quem esta perto
            for (Player p : gente) if (naBarra.add(p.getUniqueId())) p.showBossBar(barra);
            for (UUID id : new ArrayList<>(naBarra)) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !gente.contains(p)) {
                    naBarra.remove(id);
                    if (p != null) p.hideBossBar(barra);
                }
            }
            bussolas();
            barra.progress((float) Math.max(0, Math.min(1, vida / vidaMax)));
            if (invocado != null && (!invocado.isValid() || invocado.isDead())) {
                invocado = null;
                for (Player p : perto(60)) {
                    p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§a§lO COLOSSO ESTÁ VULNERÁVEL"), Extras.txt("§7Voltem a bater nele!")));
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
                }
            }
            if (invocado != null) {
                barra.color(BossBar.Color.PURPLE);
                barra.name(Extras.txt("§5§lPROTEGIDO §7— derrote §c" + nomeInvocado + " §7primeiro"));
                double r = 2.4;
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + ticks * 0.05;
                    corpo.getWorld().spawnParticle(Particle.END_ROD, l.getX() + Math.cos(a) * r, l.getY() + 1 + (i % 4) * 1.6, l.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            } else {
                barra.color(BossBar.Color.YELLOW);
                barra.name(Extras.txt("§6§l" + nome() + " §7— §c" + (int) Math.ceil(vida) + " §7/ " + (int) vidaMax));
            }
            // 20 minutos sem morrer (30 na arena, que tem as fases): vai embora
            long limite = pl.getConfig().getLong("boss-mundial.some-em-minutos", 20);
            if (arenaPronta) limite = Math.max(30, limite);
            if (System.currentTimeMillis() - nasceu > limite * 60_000L) {
                Bukkit.broadcast(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §7O " + nome() + " voltou a dormir. Ninguém o derrotou desta vez."));
                acabar(false);
                return;
            }
        }
        if (ticks >= proximoGolpe) {
            proximoGolpe = ticks + 120 + rnd.nextInt(60);
            List<Player> gente = perto(24);
            if (!gente.isEmpty()) golpe(gente);
        }
    }

    private void golpe(List<Player> gente) {
        World w = corpo.getWorld();
        Location l = corpo.getLocation();
        // alguem colado no boss: metade das vezes ele usa o montante
        boolean colado = false;
        for (Player p : gente) if (p.getLocation().distanceSquared(l) <= 11 * 11) colado = true;
        switch (colado && rnd.nextBoolean() ? 4 : rnd.nextInt(4)) {
            case 0 -> {
                corpo3d.golpe(ColossoCorpo.Golpe.ESMAGAR, ticks, 44);
                // esmagar: avisa com um anel no chao e, 1,5 s depois, acerta todo mundo dentro
                double raio = 7;
                anel(l, raio, Color.fromRGB(255, 120, 20));
                w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (!vivo()) return;
                    Location c = corpo.getLocation();
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
                    w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 4, 2.5, 0.2, 2.5, 0);
                    for (Player p : perto(raio)) {
                        p.damage(pl.getConfig().getDouble("boss-mundial.dano-do-esmagar", 10) * MULT_DANO, corpo);
                        Vector v = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                        if (v.lengthSquared() > 0.01) p.setVelocity(v.normalize().multiply(1.1).setY(0.55));
                    }
                }, 27L);
            }
            case 1 -> {
                // investida: salta para cima do jogador mais longe
                corpo3d.golpe(ColossoCorpo.Golpe.INVESTIDA, ticks, 30);
                Player alvo = gente.get(0);
                for (Player p : gente) if (p.getLocation().distanceSquared(l) > alvo.getLocation().distanceSquared(l)) alvo = p;
                Vector v = alvo.getLocation().toVector().subtract(l.toVector());
                double d = Math.max(1, v.length());
                corpo.setVelocity(v.multiply(Math.min(1.6, d * 0.11) / d).setY(0.7));
                corpo.setTarget(alvo);
                w.playSound(l, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.5f);
            }
            case 2 -> {
                // chama ajudantes (no maximo 6 vivos)
                corpo3d.golpe(ColossoCorpo.Golpe.INVOCAR, ticks, 44);
                ajudantes.removeIf(a -> !a.isValid());
                int falta = Math.min(3, 6 - ajudantes.size());
                for (int i = 0; i < falta; i++) {
                    Location s = l.clone().add(rnd.nextInt(7) - 3, 0, rnd.nextInt(7) - 3);
                    s.setY(w.getHighestBlockYAt(s) + 1);
                    if (Math.abs(s.getY() - l.getY()) > 6) continue;
                    ajudantes.add(w.spawn(s, WitherSkeleton.class, a -> {
                        a.getPersistentDataContainer().set(kAjudante, PersistentDataType.BYTE, (byte) 1);
                        a.setPersistent(false);
                        a.customName(Extras.txt("§7Guarda da Coroa"));
                    }));
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, s.clone().add(0, 1, 0), 8, 0.3, 0.6, 0.3, 0.01);
                }
                w.playSound(l, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.6f);
            }
            case 4 -> {
                // montante do Colosso: arma o golpe e varre tudo que estiver na frente dele
                corpo3d.golpe(ColossoCorpo.Golpe.ESPADA, ticks, 34);
                Player alvo = gente.get(0);
                for (Player p : gente) if (p.getLocation().distanceSquared(l) < alvo.getLocation().distanceSquared(l)) alvo = p;
                corpo.setTarget(alvo);
                corpo.lookAt(alvo);
                w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, 1.6f, 1.4f);
                w.playSound(l, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.6f, 0.6f);
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (!vivo()) return;
                    Location c = corpo.getLocation();
                    double rad = Math.toRadians(corpo.getBodyYaw());
                    Vector frente = new Vector(-Math.sin(rad), 0, Math.cos(rad));
                    double alc = pl.getConfig().getDouble("boss-mundial.alcance-da-espada", 8);
                    w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 3f, 0.5f);
                    w.playSound(c, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.4f, 1.5f);
                    arco(c, frente, alc);
                    for (Player p : perto(alc + 2)) {
                        Vector d = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                        double dist = d.length();
                        if (dist < 0.1 || dist > alc) continue;
                        if (frente.dot(d.clone().multiply(1 / dist)) < 0.2) continue;     // so quem esta na frente
                        p.damage(pl.getConfig().getDouble("boss-mundial.dano-da-espada", 12) * MULT_DANO, corpo);
                        p.setVelocity(d.multiply(1 / dist).multiply(1.0).setY(0.5));
                    }
                }, 16L);
            }
            default -> {
                // chuva de magma: marca o chao embaixo de ate 4 jogadores e explode 1,5 s depois
                corpo3d.golpe(ColossoCorpo.Golpe.MAGMA, ticks, 44);
                java.util.Collections.shuffle(gente, rnd);
                w.playSound(l, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
                for (Player p : gente.subList(0, Math.min(4, gente.size()))) {
                    Location c = p.getLocation().clone();
                    anel(c, 2.5, Color.fromRGB(255, 60, 20));
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        if (!vivo()) return;
                        w.spawnParticle(Particle.LAVA, c.clone().add(0, 0.3, 0), 10, 1.0, 0.2, 1.0, 0);
                        w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
                        for (Player q : c.getWorld().getPlayers())
                            if (q.getLocation().distanceSquared(c) <= 2.5 * 2.5) q.damage(pl.getConfig().getDouble("boss-mundial.dano-do-magma", 7) * MULT_DANO, corpo);
                    }, 30L);
                }
            }
        }
    }

    // ------------------------------------------------------------------ bussola do boss

    /**
     * Enquanto o boss esta vivo, quem esta no mundo das masmorras ganha uma bussola que aponta para ele e vai
     * acompanhando ele andar. Quando ele morre ou vai embora, a bussola some da mao de todo mundo.
     */
    private void bussolas() {
        if (!vivo()) return;
        Location alvo = corpo.getLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(alvo.getWorld())) atualizarBussola(p, alvo);
            else tirarBussola(p);
        }
    }

    /** Poe a bussola (se ainda nao tiver) e refaz a agulha/distancia. */
    private void atualizarBussola(Player p, Location alvo) {
        int slot = -1;
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) if (ehBussola(inv[i])) { slot = i; break; }
        ItemStack b = bussolaItem(p, alvo);
        if (slot >= 0) {
            p.getInventory().setItem(slot, b);
            return;
        }
        for (ItemStack sobra : p.getInventory().addItem(b).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        p.sendMessage(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §7Você recebeu a §6Bússola do Colosso§7: siga a agulha."));
        p.playSound(p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1f);
    }

    /** Tira a bussola do inventario (e do cursor) de um jogador. */
    private void tirarBussola(Player p) {
        boolean tinha = false;
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) if (ehBussola(inv[i])) {
            p.getInventory().setItem(i, null);
            tinha = true;
        }
        if (ehBussola(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
            tinha = true;
        }
        if (tinha) p.updateInventory();
    }

    private boolean ehBussola(ItemStack it) {
        return it != null && it.getType() == Material.COMPASS && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(kBussola, PersistentDataType.BYTE);
    }

    /** No Bedrock a bussola com ima derruba a sessao (Geyser): la vira bussola normal com alvo de bussola. */
    private ItemStack bussolaItem(Player p, Location alvo) {
        ItemStack b = new ItemStack(Material.COMPASS);
        boolean celular = Extras.ehBedrock(p);
        if (celular) p.setCompassTarget(alvo);
        if (b.getItemMeta() instanceof org.bukkit.inventory.meta.CompassMeta cm) {
            if (!celular) {
                cm.setLodestone(alvo);
                cm.setLodestoneTracked(false);
            }
            int d = (int) p.getLocation().distance(alvo);
            cm.displayName(Extras.txt("§6§lBússola do Colosso"));
            cm.lore(arenaPronta ? List.of(Extras.txt("§7O §6" + nome() + " §7está na arena."),
                    Extras.txt("§eClique com ela na mão para entrar."),
                    Extras.txt("§8Some quando o boss morrer ou ir embora."))
                    : List.of(Extras.txt("§7Aponta para o §6" + nome() + "§7."),
                    Extras.txt("§7Distância: §f" + d + " blocos"),
                    Extras.txt("§8Some quando o boss morrer ou ir embora.")));
            cm.getPersistentDataContainer().set(kBussola, PersistentDataType.BYTE, (byte) 1);
            b.setItemMeta(cm);
        }
        return b;
    }

    /** Segurando a bussola: mostra a distancia na tela. */
    @EventHandler
    public void usarBussola(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !ehBussola(e.getItem())) return;
        if (!vivo()) {
            tirarBussola(e.getPlayer());
            return;
        }
        Location alvo = corpo.getLocation();
        Player p = e.getPlayer();
        if (arenaPronta && naArena(alvo)) {
            e.setCancelled(true);
            if (naArena(p.getLocation())) {
                p.sendActionBar(Extras.txt("§7Você já está na arena."));
                return;
            }
            long agora = System.currentTimeMillis();
            if (esperaBussola.getOrDefault(p.getUniqueId(), 0L) > agora) return;
            esperaBussola.put(p.getUniqueId(), agora + 3000);
            entrarNaArena(p);
            return;
        }
        if (!p.getWorld().equals(alvo.getWorld())) return;
        p.sendActionBar(Extras.txt("§6" + nome() + " §8| §e" + (int) p.getLocation().distance(alvo) + " blocos"));
        p.playSound(p.getLocation(), "minecraft:ui.button.click", 0.4f, 1.6f);
    }

    /** Anel de aviso no chao: poucos pontos, para nao pesar no celular. */
    private void anel(Location c, double raio, Color cor) {
        Particle.DustOptions po = new Particle.DustOptions(cor, 2.0f);
        int pontos = (int) Math.max(10, raio * 4);
        for (int k = 0; k < 3; k++) {
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                for (int i = 0; i < pontos; i++) {
                    double a = Math.PI * 2 * i / pontos;
                    c.getWorld().spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * raio, c.getY() + 0.25, c.getZ() + Math.sin(a) * raio, 1, 0, 0, 0, 0, po);
                }
            }, k * 10L);
        }
    }

    /** Rastro do montante: um arco de faiscas na frente do boss, na altura do peito. */
    private void arco(Location c, Vector frente, double alc) {
        Particle.DustOptions po = new Particle.DustOptions(Color.fromRGB(255, 140, 40), 2.2f);
        double base = Math.atan2(frente.getZ(), frente.getX());
        for (int i = 0; i <= 14; i++) {
            double a = base + Math.toRadians(-70 + 140.0 * i / 14);
            double r = alc * 0.85;
            c.getWorld().spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + 2.6, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, po);
            c.getWorld().spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r * 0.6, c.getY() + 2.2, c.getZ() + Math.sin(a) * r * 0.6, 1, 0, 0, 0, 0, po);
        }
    }

    // ------------------------------------------------------------------ dano e morte

    private boolean eBoss(Entity e) {
        return e != null && e.getPersistentDataContainer().has(kBoss, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void dano(EntityDamageEvent e) {
        if (!eBoss(e.getEntity()) || e.getEntity() != corpo) return;
        Player p = null;
        if (e instanceof EntityDamageByEntityEvent ee) {
            if (ee.getDamager() instanceof Player x) p = x;
            else if (ee.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player x) p = x;
        }
        if (invocado != null && invocado.isValid() && !invocado.isDead()) {
            e.setCancelled(true);
            if (p != null) {
                p.sendActionBar(Extras.txt("§5§lPROTEGIDO! §7Derrote §c" + nomeInvocado + " §7primeiro."));
                p.playSound(corpo.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 0.6f);
            }
            return;
        }
        double d = e.getFinalDamage();
        e.setDamage(0);                       // a vida de verdade e a nossa (o corpo fica sempre cheio)
        if (p == null) {
            // queda, sufoco, fogo, outro mob: nao conta
            e.setCancelled(true);
            return;
        }
        d = Math.min(d, pl.getConfig().getDouble("boss-mundial.dano-maximo-por-golpe", 60));
        if (dano.merge(p.getUniqueId(), d, Double::sum) == d) {
            // jogador novo na luta: o boss ganha vida
            double mais = pl.getConfig().getDouble("boss-mundial.vida-por-jogador", 350) * MULT_VIDA;
            if (dano.size() > 1) {
                vidaMax += mais;
                vida += mais;
            }
        }
        nomes.put(p.getUniqueId(), p.getName());
        vida -= d;
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1f, 0.6f);
        if (arenaPronta && naArena(corpo.getLocation())) {
            if (fase == 0 && vida <= vidaMax * 2 / 3.0) {
                fase = 1;
                vida = Math.max(vida, 1);
                chamarReforco(3);
                return;
            }
            if (fase == 1 && vida <= vidaMax / 3.0) {
                fase = 2;
                vida = Math.max(vida, 1);
                chamarReforco(4);
                return;
            }
        }
        if (vida <= 0) morrer();
    }

    /** Quem bate no chefe chamado tambem esta lutando: conta metade do dano para o premio do Colosso. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void danoNoInvocado(EntityDamageByEntityEvent e) {
        if (invocado == null || e.getEntity() != invocado || !vivo()) return;
        Player p = e.getDamager() instanceof Player x ? x : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player x ? x : null;
        if (p == null) return;
        double d = Math.min(e.getFinalDamage(), pl.getConfig().getDouble("boss-mundial.dano-maximo-por-golpe", 60)) * 0.5;
        if (dano.merge(p.getUniqueId(), d, Double::sum) == d && dano.size() > 1) {
            double mais = pl.getConfig().getDouble("boss-mundial.vida-por-jogador", 350) * MULT_VIDA;
            vidaMax += mais;
            vida += mais;
        }
        nomes.put(p.getUniqueId(), p.getName());
    }

    /** Chama um chefe de masmorra (nivel 3 ou 4) na arena pelo plugin das masmorras (reflexao: sem dependencia). */
    private void chamarReforco(int nivel) {
        World w = corpo.getWorld();
        for (Player p : perto(60)) {
            p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§5§lO COLOSSO CHAMOU REFORÇO"), Extras.txt("§7Um chefe de masmorra nível " + nivel + " entrou na arena!")));
            p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.6f);
        }
        w.strikeLightningEffect(corpo.getLocation());
        try {
            org.bukkit.plugin.Plugin m = Bukkit.getPluginManager().getPlugin("Masmorras");
            if (m == null || !m.isEnabled()) return;
            java.lang.reflect.Method mt = m.getClass().getMethod("chefeAvulso", Location.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
            // longe do Colosso: do outro lado da arena
            Location c = corpo.getLocation();
            double dx = ax + 0.5 - c.getX(), dz = az + 0.5 - c.getZ(), len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            Location onde = new Location(w, ax + 0.5 + dx / len * 12, ay, az + 0.5 + dz / len * 12);
            if (!naArena(onde) || len < 4) onde = new Location(w, ax + 0.5, ay, az + 12.5);   // Colosso no meio: nasce ao sul dele
            Object r = mt.invoke(m, onde, ax, ay, az, RAIO - 1, ALTURA, nivel, Math.max(1, perto(60).size()));
            if (r instanceof LivingEntity le) {
                invocado = le;
                net.kyori.adventure.text.Component nm = le.customName();
                nomeInvocado = nm == null ? "o chefe" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(nm).replaceAll("\\s*\\[N[ií]vel \\d\\]", "");
                Bukkit.broadcast(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §7O " + nome() + " chamou §c" + nomeInvocado + "§7! Ele fica protegido até o chefe cair."));
            } else pl.getLogger().warning("Boss mundial: as masmorras nao devolveram o chefe de nivel " + nivel);
        } catch (Throwable ex) {
            pl.getLogger().warning("Boss mundial: nao chamei o chefe de nivel " + nivel + ": " + ex);
        }
    }

    private void tirarInvocado() {
        LivingEntity i = invocado;
        invocado = null;
        if (i == null) return;
        try {
            org.bukkit.plugin.Plugin m = Bukkit.getPluginManager().getPlugin("Masmorras");
            if (m != null && m.isEnabled()) m.getClass().getMethod("tirarChefeAvulso", LivingEntity.class).invoke(m, i);
            else if (i.isValid()) i.remove();
        } catch (Throwable ex) {
            if (i.isValid()) i.remove();
        }
    }

    @EventHandler
    public void fogo(EntityCombustEvent e) {
        if (eBoss(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        if (eBoss(e.getEntity()) || e.getEntity().getPersistentDataContainer().has(kAjudante, PersistentDataType.BYTE)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        } else if (!(e.getEntity() instanceof Player) && naArena(e.getEntity().getLocation())) e.getDrops().clear();   // suditos do chefe chamado
    }

    private void morrer() {
        Location l = corpo.getLocation();
        World w = l.getWorld();
        w.playSound(l, Sound.ENTITY_WITHER_DEATH, 2f, 0.7f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l.clone().add(0, 3, 0), 2, 1, 1, 1, 0);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 4, 0), 60, 2, 3, 2, 0.3);
        List<Map.Entry<UUID, Double>> ordem = new ArrayList<>(dano.entrySet());
        ordem.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int base = pl.getConfig().getInt("boss-mundial.premio", 150);
        int[] bonus = {pl.getConfig().getInt("boss-mundial.bonus-1", 200), pl.getConfig().getInt("boss-mundial.bonus-2", 120), pl.getConfig().getInt("boss-mundial.bonus-3", 60)};
        double minimo = pl.getConfig().getDouble("boss-mundial.dano-minimo-para-premio", 10);
        Bukkit.broadcast(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §aO §6" + nome() + " §afoi derrotado por §f" + ordem.size() + " §ajogador" + (ordem.size() == 1 ? "" : "es") + "!"));
        for (int i = 0; i < ordem.size(); i++) {
            UUID id = ordem.get(i).getKey();
            if (ordem.get(i).getValue() < minimo) continue;
            int v = base + (i < 3 ? bonus[i] : 0);
            String nome = nomes.getOrDefault(id, "?");
            if (i < 3) Bukkit.broadcast(Extras.txt("§7  " + (i + 1) + "º §f" + nome + " §7— §c" + (int) Math.round(ordem.get(i).getValue()) + " de dano §7(§6+" + v + " coronas§7)"));
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pl.getConfig().getString("boss-mundial.comando", "coronas dar %jogador% %valor%")
                    .replace("%jogador%", p.getName()).replace("%valor%", String.valueOf(v)));
            p.sendMessage(Extras.txt("§6+" + v + " coronas §7pelo boss mundial."));
            List<String> caiu = drops(p, i);
            if (!caiu.isEmpty()) {
                String o = String.join("§7 e §6", caiu);
                p.sendMessage(Extras.txt("§6§l✦ DROP §8» §fVocê ganhou §6" + o + "§f!"));
                Bukkit.broadcast(Extras.txt("§6§l✦ §f" + p.getName() + " §7ganhou §6" + o + " §7do boss mundial!"));
                pl.getLogger().info("Boss mundial: " + p.getName() + " ganhou " + String.join(" e ", caiu));
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        pl.getLogger().info("Boss mundial derrotado por " + ordem.size() + " jogador(es)");
        acabar(true);
    }

    private void acabar(boolean morto) {
        tirarInvocado();
        fase = 0;
        soltarChunks();
        if (arenaPronta && (corpo != null || morto)) devolverDaArena(morto ? 200L : 60L);
        for (Player p : Bukkit.getOnlinePlayers()) tirarBussola(p);
        if (tarefa != null) tarefa.cancel();
        tarefa = null;
        if (barra != null) for (UUID id : naBarra) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(barra);
        }
        naBarra.clear();
        barra = null;
        if (corpo3d != null) corpo3d.sumir();
        corpo3d = null;
        if (corpo != null && corpo.isValid()) corpo.remove();
        corpo = null;
        for (Entity a : ajudantes) if (a.isValid()) a.remove();
        ajudantes.clear();
    }

    // ------------------------------------------------------------------ drops: a armadura e o montante do Colosso

    private static final String[] PECAS = {"elmo", "peitoral", "calca", "botas"};
    private static final Material[] MAT_PECAS = {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
    private static final String[] NOME_PECAS = {"Elmo do Colosso", "Peitoral do Colosso", "Calças do Colosso", "Botas do Colosso"};

    /** Peca 0..3 da armadura do Colosso (netherite, com o visual corona:colosso do pacote). */
    @SuppressWarnings("UnstableApiUsage")
    ItemStack pecaDoColosso(int i) {
        ItemStack it = new ItemStack(MAT_PECAS[i]);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt("§6§l" + NOME_PECAS[i]));
        m.lore(List.of(Extras.txt("§7Arrancada do §6Colosso da Coroa Partida§7,"), Extras.txt("§7o boss mundial das masmorras."), Extras.txt(""),
                Extras.txt("§bProteção IV §8| §bInquebrável III")));
        m.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
        m.setEnchantmentGlintOverride(false);          // o brilho do encantamento apagava o desenho
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(kDrop, PersistentDataType.STRING, PECAS[i]);
        it.setItemMeta(m);
        try {
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "item/armaduras/colosso_" + PECAS[i]));
            io.papermc.paper.datacomponent.item.Equippable atual = it.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);
            EquipmentSlot slot = it.getType().getEquipmentSlot();
            io.papermc.paper.datacomponent.item.Equippable.Builder b = io.papermc.paper.datacomponent.item.Equippable.equippable(slot);
            // elmo 3D: sem asset_id o Java desenha o modelo do item na cabeca; as outras pecas usam a textura corona:colosso
            if (slot != EquipmentSlot.HEAD) b.assetId(net.kyori.adventure.key.Key.key("corona", "colosso"));
            if (atual != null) {
                b.equipSound(atual.equipSound()).dispensable(atual.dispensable()).swappable(atual.swappable()).damageOnHurt(atual.damageOnHurt())
                        .equipOnInteract(atual.equipOnInteract());
                if (atual.allowedEntities() != null) b.allowedEntities(atual.allowedEntities());
                if (atual.cameraOverlay() != null) b.cameraOverlay(atual.cameraOverlay());
            }
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE, b.build());
        } catch (Throwable ex) {
            pl.getLogger().warning("Visual da armadura do Colosso: " + ex);
        }
        return it;
    }

    /** O Montante do Colosso: espada de netherite grande, com o modelo do pacote. */
    @SuppressWarnings("UnstableApiUsage")
    ItemStack espadaDoColosso() {
        ItemStack it = new ItemStack(Material.NETHERITE_SWORD);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt("§6§lMontante do Colosso"));
        m.lore(List.of(Extras.txt("§7A espada colossal do §6Colosso da Coroa Partida§7."), Extras.txt("§7Raríssima: só cai do boss mundial."), Extras.txt(""),
                Extras.txt("§bAfiação V §8| §bAspecto Flamejante II §8| §bInquebrável III")));
        m.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
        m.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2, true);
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
        m.setEnchantmentGlintOverride(false);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(kDrop, PersistentDataType.STRING, "espada");
        it.setItemMeta(m);
        try {
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "item/reliquias/espada_colosso"));
        } catch (Throwable ex) {
            pl.getLogger().warning("Visual do Montante do Colosso: " + ex);
        }
        return it;
    }

    private void entregar(Player p, ItemStack it) {
        for (ItemStack sobra : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), sobra);
    }

    /** Sorteia os drops de um jogador (posicao 0 = quem deu mais dano) e devolve o que caiu, para o aviso. */
    private List<String> drops(Player p, int posicao) {
        List<String> caiu = new ArrayList<>();
        if (!pl.getConfig().getBoolean("boss-mundial.drops.ativo", true)) return caiu;
        double chancePeca = posicao == 0 ? pl.getConfig().getDouble("boss-mundial.drops.peca-do-1", 1.0)
                : posicao < 3 ? pl.getConfig().getDouble("boss-mundial.drops.peca-do-top3", 0.6) : pl.getConfig().getDouble("boss-mundial.drops.peca-dos-outros", 0.3);
        double chanceEspada = posicao == 0 ? pl.getConfig().getDouble("boss-mundial.drops.espada-do-1", 0.25)
                : pl.getConfig().getDouble("boss-mundial.drops.espada-dos-outros", 0.06);
        if (rnd.nextDouble() < chancePeca) {
            // prefere uma peca que o jogador ainda nao tem
            List<Integer> falta = new ArrayList<>();
            for (int i = 0; i < PECAS.length; i++) if (!tem(p, PECAS[i])) falta.add(i);
            int i = falta.isEmpty() ? rnd.nextInt(PECAS.length) : falta.get(rnd.nextInt(falta.size()));
            entregar(p, pecaDoColosso(i));
            caiu.add(NOME_PECAS[i]);
        }
        if (rnd.nextDouble() < chanceEspada) {
            entregar(p, espadaDoColosso());
            caiu.add("Montante do Colosso");
        }
        return caiu;
    }

    private boolean tem(Player p, String peca) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            if (peca.equals(it.getItemMeta().getPersistentDataContainer().get(kDrop, PersistentDataType.STRING))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ arena (17/11)

    private java.io.File arqArena() {
        return new java.io.File(pl.getDataFolder(), "arena-boss.txt");
    }

    /** Le a arena salva; se ainda nao tem, escolhe o lugar e constroi (uma vez so). */
    private void prepararArena() {
        if (!pl.getConfig().getBoolean("boss-mundial.arena.ativa", true)) return;
        World w = mundoDoBoss();
        if (w == null) return;
        try {
            String[] v = java.nio.file.Files.readString(arqArena().toPath()).trim().split(" ");
            ax = Integer.parseInt(v[0]);
            ay = Integer.parseInt(v[1]);
            az = Integer.parseInt(v[2]);
            int ver = v.length > 3 ? Integer.parseInt(v[3]) : 1;
            if (ver >= VERSAO_ARENA) {
                arenaPronta = true;
                pl.getLogger().info("Arena do Colosso em " + ax + " " + ay + " " + az);
            } else construirArena(w);   // arena antiga (menor): refaz no mesmo lugar
            return;
        } catch (Exception ignored) {
            // ainda nao foi construida
        }
        escolherLugarArena(w);
        construirArena(w);
    }

    private void escolherLugarArena(World w) {
        Location s = w.getSpawnLocation();
        ax = pl.getConfig().getInt("boss-mundial.arena.x", s.getBlockX() + 900);
        az = pl.getConfig().getInt("boss-mundial.arena.z", s.getBlockZ() + 900);
        ay = Math.min(w.getMaxHeight() - 24, pl.getConfig().getInt("boss-mundial.arena.y", 262));
    }

    /**
     * Templo no ceu (versao 2): chao de mosaico com aneis dourados, 12 colunas em volta do centro, paredes de pedra negra
     * com luz e cornija, e barreira por cima (ninguem cai nem sai voando). Constroi aos poucos (6 fileiras por tique)
     * para nao travar o servidor; a arena so vale (arenaPronta) quando termina.
     */
    void construirArena(World w) {
        long t0 = System.currentTimeMillis();
        arenaPronta = false;
        for (int x = ax - RAIO - 16; x <= ax + RAIO + 16; x += 16)
            for (int z = az - RAIO - 16; z <= az + RAIO + 16; z += 16) w.getChunkAt(x >> 4, z >> 4).load(true);
        java.util.Set<Long> colunas = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30 + 15);
            int px = (int) Math.round(Math.cos(a) * 30), pz = (int) Math.round(Math.sin(a) * 30);
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) colunas.add(((long) (px + ox) << 32) ^ ((pz + oz) & 0xffffffffL));
        }
        int[] fila = {-RAIO};
        BukkitTask[] tarefaArena = new BukkitTask[1];
        tarefaArena[0] = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (int k = 0; k < 6 && fila[0] <= RAIO; k++, fila[0]++) {
                int dx = fila[0];
                for (int dz = -RAIO; dz <= RAIO; dz++) colunaDaArena(w, dx, dz, colunas.contains(((long) dx << 32) ^ (dz & 0xffffffffL)));
            }
            if (fila[0] <= RAIO) return;
            tarefaArena[0].cancel();
            arenaPronta = true;
            try {
                java.nio.file.Files.writeString(arqArena().toPath(), ax + " " + ay + " " + az + " " + VERSAO_ARENA);
            } catch (Exception ex) {
                pl.getLogger().warning("Arena do Colosso: nao salvei o lugar: " + ex.getMessage());
            }
            pl.getLogger().info("Arena do Colosso (templo v" + VERSAO_ARENA + ") construida em " + ax + " " + ay + " " + az + " em " + (System.currentTimeMillis() - t0) + " ms");
        }, 1L, 1L);
    }

    private void colunaDaArena(World w, int dx, int dz, boolean coluna) {
        int x = ax + dx, z = az + dz;
        boolean borda = Math.abs(dx) == RAIO || Math.abs(dz) == RAIO;
        w.getBlockAt(x, ay - 3, z).setType(Material.DEEPSLATE_TILES, false);
        w.getBlockAt(x, ay - 2, z).setType(Material.POLISHED_BLACKSTONE, false);
        double dist = Math.sqrt(dx * dx + dz * dz);
        Material chao;
        if (borda) chao = Material.POLISHED_BLACKSTONE_BRICKS;
        else if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) chao = (Math.abs(dx) == 2 || Math.abs(dz) == 2) ? Material.GILDED_BLACKSTONE : Material.CHISELED_POLISHED_BLACKSTONE;
        else if (dx % 8 == 0 && dz % 8 == 0) chao = Material.SHROOMLIGHT;
        else if (Math.abs(dist - 10) < 0.5 || Math.abs(dist - 22) < 0.5 || Math.abs(dist - 36) < 0.5) chao = Material.GILDED_BLACKSTONE;
        else if (dist > 22 && dist < 36 && (Math.abs(dx) == Math.abs(dz) || dx == 0 || dz == 0)) chao = Material.POLISHED_BLACKSTONE;   // raios do mosaico
        else if (Math.abs(dx) >= RAIO - 2 || Math.abs(dz) >= RAIO - 2) chao = Material.POLISHED_DEEPSLATE;
        else chao = ((dx + dz) & 1) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_DEEPSLATE;
        w.getBlockAt(x, ay - 1, z).setType(chao, false);
        int ao = Math.abs(dx) == RAIO ? dz : dx;   // posicao ao longo da parede
        for (int h = 0; h <= ALTURA + 1; h++) {
            Block b = w.getBlockAt(x, ay + h, z);
            Material m;
            if (borda) {
                if (h == 0) m = Material.POLISHED_BLACKSTONE_BRICKS;
                else if (h <= 11) m = (Math.floorMod(ao, 8) == 0) ? (h == 5 ? Material.SHROOMLIGHT : Material.POLISHED_BLACKSTONE)   // pilastras com luz
                        : (h >= 3 && h <= 8 && Math.floorMod(ao, 8) >= 3 && Math.floorMod(ao, 8) <= 5) ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.BLACKSTONE;
                else if (h == 12) m = Material.CHISELED_POLISHED_BLACKSTONE;
                else if (h == 13) m = Material.GILDED_BLACKSTONE;
                else m = Material.BARRIER;
            } else if (coluna) {
                if (h == 0 || h == 15) m = Material.CHISELED_POLISHED_BLACKSTONE;
                else if (h == 16) m = Material.SHROOMLIGHT;
                else if (h < 16) m = (h % 5 == 0) ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
                else m = Material.AIR;
            } else m = Material.AIR;
            if (b.getType() != m) b.setType(m, false);
        }
        Block teto = w.getBlockAt(x, ay + ALTURA + 2, z);
        if (teto.getType() != Material.BARRIER) teto.setType(Material.BARRIER, false);
    }

    private Location centroArena(World w) {
        return new Location(w, ax + 0.5, ay, az + 0.5);
    }

    /** Dentro da arena (por dentro das paredes, do chao ate o teto). */
    private boolean naArena(Location l) {
        if (!arenaPronta || l == null || l.getWorld() == null || !l.getWorld().equals(mundoDoBoss())) return false;
        return Math.abs(l.getX() - (ax + 0.5)) < RAIO && Math.abs(l.getZ() - (az + 0.5)) < RAIO && l.getY() >= ay - 1 && l.getY() <= ay + ALTURA + 2;
    }

    /** A arena com as paredes, o chao e o teto (para proteger os blocos). */
    private boolean blocoDaArena(Block b) {
        if (!arenaPronta || !b.getWorld().equals(mundoDoBoss())) return false;
        return Math.abs(b.getX() - ax) <= RAIO && Math.abs(b.getZ() - az) <= RAIO && b.getY() >= ay - 3 && b.getY() <= ay + ALTURA + 2;
    }

    private void entrarNaArena(Player p) {
        World w = mundoDoBoss();
        if (w == null) return;
        if (!naArena(p.getLocation())) volta.put(p.getUniqueId(), p.getLocation());
        Location d = new Location(w, ax + 0.5, ay, az - RAIO + 4.5);
        d.setDirection(new Vector(0, 0, 1));
        p.teleport(d);
        p.sendMessage(Extras.txt("§6§l⚔ §eVocê entrou na §6Arena do Colosso§e. Boa luta! §7(no fim da luta você volta para onde estava)"));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.8f);
    }

    /** No fim da luta todo mundo que esta na arena volta para onde estava (ou para o spawn das masmorras). */
    private void devolverDaArena(long espera) {
        if (!pl.isEnabled()) return;   // desligando: quem ficou la volta pelo entrou() quando entrar de novo
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (vivo()) return;
            World w = mundoDoBoss();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!naArena(p.getLocation())) continue;
                Location v = volta.remove(p.getUniqueId());
                if (v == null || v.getWorld() == null) v = w != null ? w.getSpawnLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
                p.teleport(v);
                p.sendMessage(Extras.txt("§7A luta acabou: você voltou para onde estava."));
            }
            volta.clear();
        }, espera);
    }

    private void segurarChunks(World w) {
        soltarChunks();
        for (int x = (ax - RAIO) >> 4; x <= (ax + RAIO) >> 4; x++)
            for (int z = (az - RAIO) >> 4; z <= (az + RAIO) >> 4; z++) {
                w.addPluginChunkTicket(x, z, pl);
                tickets.add(new long[] {x, z});
            }
    }

    private void soltarChunks() {
        World w = mundoDoBoss();
        if (w != null) for (long[] c : tickets) w.removePluginChunkTicket((int) c[0], (int) c[1], pl);
        tickets.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void quebrar(org.bukkit.event.block.BlockBreakEvent e) {
        if (blocoDaArena(e.getBlock()) && e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void colocar(org.bukkit.event.block.BlockPlaceEvent e) {
        if (blocoDaArena(e.getBlock()) && e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void balde(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        if (blocoDaArena(e.getBlock()) && e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void explodiu(org.bukkit.event.entity.EntityExplodeEvent e) {
        e.blockList().removeIf(this::blocoDaArena);
    }

    @EventHandler(ignoreCancelled = true)
    public void explodiuBloco(org.bukkit.event.block.BlockExplodeEvent e) {
        e.blockList().removeIf(this::blocoDaArena);
    }

    @EventHandler(ignoreCancelled = true)
    public void fogoNaArena(org.bukkit.event.block.BlockIgniteEvent e) {
        if (blocoDaArena(e.getBlock())) e.setCancelled(true);
    }

    /** Quem saiu do jogo dentro da arena e voltou depois da luta nao fica preso no ceu. */
    @EventHandler
    public void entrou(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || vivo() || !naArena(p.getLocation())) return;
            World w = mundoDoBoss();
            if (w != null) p.teleport(w.getSpawnLocation());
        }, 40L);
    }

    // ------------------------------------------------------------------ comando

    boolean comando(CommandSender s, String[] a) {
        if (a.length >= 1 && s.hasPermission("extras.admin")) {
            switch (a[0].toLowerCase()) {
                case "invocar", "nascer", "spawn" -> {
                    if (arenaPronta && mundoDoBoss() != null) {
                        nascer(centroArena(mundoDoBoss()));
                        s.sendMessage(Extras.txt("§7O Colosso nasceu na arena. §f/boss §7leva até lá."));
                        return true;
                    }
                    if (!(s instanceof Player p)) {
                        World w = mundoDoBoss();
                        Location l = w == null ? null : lugar(w);
                        if (l == null) s.sendMessage("Nao achei lugar.");
                        else nascer(l);
                    } else if (p.getWorld().equals(mundoDoBoss())) nascer(p.getLocation());
                    else {
                        // fora do mundo das masmorras: nasce la, num lugar sorteado, e o /boss leva ate ele
                        World w = mundoDoBoss();
                        Location l = w == null ? null : lugar(w);
                        if (l == null) p.sendMessage(Extras.txt("§cNão achei lugar no mundo das masmorras."));
                        else {
                            nascer(l);
                            p.sendMessage(Extras.txt("§7O boss só nasce no mundo das masmorras. Use §f/boss §7para ir até ele."));
                        }
                    }
                    return true;
                }
                case "remover", "tirar" -> {
                    acabar(false);
                    s.sendMessage(Extras.txt("§7Boss mundial removido."));
                    return true;
                }
                case "dar" -> {
                    // dono testando: /boss dar <nick> [elmo|peitoral|calca|botas|espada|tudo]
                    Player alvo = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : null;
                    if (alvo == null && a.length >= 2) alvo = Bukkit.getPlayerExact("." + a[1]);
                    if (alvo == null) {
                        s.sendMessage(Extras.txt("§eUse: §f/boss dar <nick> [elmo|peitoral|calca|botas|espada|tudo]"));
                        return true;
                    }
                    String o = a.length >= 3 ? a[2].toLowerCase() : "tudo";
                    int n = 0;
                    for (int i = 0; i < PECAS.length; i++) if (o.equals("tudo") || o.equals(PECAS[i]) || (o.equals("calcas") && i == 2)) {
                        entregar(alvo, pecaDoColosso(i));
                        n++;
                    }
                    if (o.equals("tudo") || o.equals("espada") || o.equals("montante")) {
                        entregar(alvo, espadaDoColosso());
                        n++;
                    }
                    s.sendMessage(Extras.txt("§7" + n + " item(ns) do Colosso dado(s) a §f" + alvo.getName() + "§7."));
                    return true;
                }
                case "arena" -> {
                    // dono: refaz a arena e vai ate ela
                    World w = mundoDoBoss();
                    if (w == null) {
                        s.sendMessage("Mundo das masmorras nao carregado.");
                        return true;
                    }
                    if (!arenaPronta) escolherLugarArena(w);
                    construirArena(w);
                    s.sendMessage(Extras.txt("§7Arena do Colosso em §f" + ax + " " + ay + " " + az + "§7."));
                    if (s instanceof Player p) entrarNaArena(p);
                    return true;
                }
                case "fase" -> {
                    // dono testando: pula para a proxima fase (chama o chefe de nivel 3, depois o de nivel 4)
                    if (!vivo() || fase >= 2) {
                        s.sendMessage("sem boss vivo ou ja na ultima fase");
                        return true;
                    }
                    fase++;
                    tirarInvocado();
                    vida = Math.min(vida, vidaMax * (fase == 1 ? 2 / 3.0 : 1 / 3.0));
                    chamarReforco(fase == 1 ? 3 : 4);
                    s.sendMessage("fase " + fase + ": " + (invocado != null ? nomeInvocado + " vida " + (int) invocado.getHealth() : "nao chamou"));
                    return true;
                }
                case "status" -> {
                    s.sendMessage(vivo() ? "vivo em " + corpo.getWorld().getName() + " " + corpo.getLocation().getBlockX() + " " + corpo.getLocation().getBlockY() + " "
                            + corpo.getLocation().getBlockZ() + " vida " + (int) vida + "/" + (int) vidaMax + " lutando " + dano.size() + " fase " + fase
                            + (invocado != null && invocado.isValid() ? " invocado " + nomeInvocado + " " + (int) invocado.getHealth() + " em " + invocado.getLocation().getBlockX() + " " + invocado.getLocation().getBlockY() + " " + invocado.getLocation().getBlockZ() : "")
                            + " arena " + (arenaPronta ? ax + " " + ay + " " + az : "nao") : "dormindo; arena " + (arenaPronta ? ax + " " + ay + " " + az : "nao"));
                    return true;
                }
                default -> { }
            }
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Somente jogadores.");
            return true;
        }
        if (!vivo()) {
            long falta = Math.max(0, (proximoNascimento - System.currentTimeMillis()) / 60_000L);
            p.sendMessage(Extras.txt(ativo() ? "§7O boss mundial está dormindo. Ele desperta em cerca de §f" + (falta + 1) + " min§7; o servidor avisa no chat."
                    : "§7O boss mundial está desligado."));
            return true;
        }
        if (arenaPronta && naArena(corpo.getLocation())) {
            if (naArena(p.getLocation())) p.sendMessage(Extras.txt("§7Você já está na arena."));
            else entrarNaArena(p);
            return true;
        }
        // chega a uns 20 blocos dele, em chao firme
        Location b = corpo.getLocation();
        World w = b.getWorld();
        for (int t = 0; t < 12; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            int x = b.getBlockX() + (int) (Math.cos(ang) * 20), z = b.getBlockZ() + (int) (Math.sin(ang) * 20);
            Block chao = w.getHighestBlockAt(x, z);
            if (chao.isLiquid() || !chao.getType().isSolid()) continue;
            Location d = new Location(w, x + 0.5, chao.getY() + 1, z + 0.5);
            d.setDirection(b.toVector().subtract(d.toVector()));
            p.teleport(d);
            p.sendMessage(Extras.txt("§6§l⚔ §eVocê chegou perto do " + nome() + ". Boa luta!"));
            return true;
        }
        p.sendMessage(Extras.txt("§cNão achei um lugar seguro perto do boss agora. Tente de novo."));
        return true;
    }
}
