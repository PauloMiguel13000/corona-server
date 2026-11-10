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
    private double vida, vidaMax;
    private int ticks, proximoGolpe;
    private long nasceu;
    private final Map<UUID, Double> dano = new HashMap<>();
    private final Map<UUID, String> nomes = new HashMap<>();
    private final List<Entity> ajudantes = new ArrayList<>();
    private final java.util.Set<UUID> naBarra = new java.util.HashSet<>();
    private long proximoNascimento;

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

    void iniciar() {
        Bukkit.getPluginManager().registerEvents(this, pl);
        if (!ativo()) return;
        long min = Math.max(10, pl.getConfig().getLong("boss-mundial.a-cada-minutos", 120));
        proximoNascimento = System.currentTimeMillis() + min * 60_000L;
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (vivo() || System.currentTimeMillis() < proximoNascimento) return;
            proximoNascimento = System.currentTimeMillis() + min * 60_000L;
            World w = mundoDoBoss();
            if (w == null || Bukkit.getOnlinePlayers().size() < pl.getConfig().getInt("boss-mundial.minimo-de-jogadores", 1)) return;
            Location l = lugar(w);
            if (l != null) nascer(l);
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
            set(c, Attribute.ATTACK_DAMAGE, pl.getConfig().getDouble("boss-mundial.dano", 9));
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
        vidaMax = vida = pl.getConfig().getDouble("boss-mundial.vida-base", 600);
        dano.clear();
        nomes.clear();
        ticks = 0;
        proximoGolpe = 100;
        nasceu = System.currentTimeMillis();
        barra = BossBar.bossBar(Extras.txt("§6§l" + nome()), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
        l.getWorld().strikeLightningEffect(l);
        Bukkit.broadcast(Extras.txt("§6§l⚔ BOSS MUNDIAL §8» §eO §6" + nome() + " §edespertou em §f" + l.getBlockX() + ", " + l.getBlockZ() + "§e! Quem bater ganha coronas."));
        Bukkit.broadcast(Extras.txt("§a§l[IR ATÉ O BOSS]").clickEvent(ClickEvent.runCommand("/boss")).append(Extras.txt(" §7ou digite §f/boss")));
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
            if (pe.isLiquid() || l.getY() < corpo.getWorld().getMinHeight() + 2) {
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
            barra.name(Extras.txt("§6§l" + nome() + " §7— §c" + (int) Math.ceil(vida) + " §7/ " + (int) vidaMax));
            // 20 minutos sem morrer: vai embora
            if (System.currentTimeMillis() - nasceu > pl.getConfig().getLong("boss-mundial.some-em-minutos", 20) * 60_000L) {
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
                        p.damage(pl.getConfig().getDouble("boss-mundial.dano-do-esmagar", 10), corpo);
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
                        p.damage(pl.getConfig().getDouble("boss-mundial.dano-da-espada", 12), corpo);
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
                            if (q.getLocation().distanceSquared(c) <= 2.5 * 2.5) q.damage(pl.getConfig().getDouble("boss-mundial.dano-do-magma", 7), corpo);
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
            cm.lore(List.of(Extras.txt("§7Aponta para o §6" + nome() + "§7."),
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
            double mais = pl.getConfig().getDouble("boss-mundial.vida-por-jogador", 350);
            if (dano.size() > 1) {
                vidaMax += mais;
                vida += mais;
            }
        }
        nomes.put(p.getUniqueId(), p.getName());
        vida -= d;
        corpo.getWorld().playSound(corpo.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1f, 0.6f);
        if (vida <= 0) morrer();
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
        }
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

    // ------------------------------------------------------------------ comando

    boolean comando(CommandSender s, String[] a) {
        if (a.length >= 1 && s.hasPermission("extras.admin")) {
            switch (a[0].toLowerCase()) {
                case "invocar", "nascer", "spawn" -> {
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
                case "status" -> {
                    s.sendMessage(vivo() ? "vivo em " + corpo.getWorld().getName() + " " + corpo.getLocation().getBlockX() + " " + corpo.getLocation().getBlockY() + " "
                            + corpo.getLocation().getBlockZ() + " vida " + (int) vida + "/" + (int) vidaMax + " lutando " + dano.size() : "dormindo");
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
