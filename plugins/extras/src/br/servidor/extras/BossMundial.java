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
    private final NamespacedKey kBoss, kAjudante;
    private final Random rnd = new Random();
    private final java.util.Set<UUID> comPacote = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private WitherSkeleton corpo;
    private ItemDisplay modelo;
    private BossBar barra;
    private BukkitTask tarefa;
    private double vida, vidaMax;
    private int ticks, proximoGolpe, quadro, quadroAte;
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
            World w = Bukkit.getWorld(pl.getConfig().getString("boss-mundial.mundo", "world"));
            if (w == null || w.getPlayers().size() < pl.getConfig().getInt("boss-mundial.minimo-de-jogadores", 1)) return;
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
        modelo = l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(item(0));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, 1.5f * ESC_MODELO, 0), new Quaternionf(), new Vector3f(ESC_MODELO, ESC_MODELO, ESC_MODELO), new Quaternionf()));
            d.setBrightness(new Display.Brightness(12, 15));
            d.setViewRange(2.5f);
            d.setShadowRadius(0f);
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
        });
        vidaMax = vida = pl.getConfig().getDouble("boss-mundial.vida-base", 600);
        dano.clear();
        nomes.clear();
        ticks = 0;
        quadro = 0;
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
        // o modelo segue o corpo
        if (modelo != null && modelo.isValid()) {
            Location m = l.clone();
            m.setPitch(0);
            m.setYaw(corpo.getBodyYaw());
            modelo.teleport(m);
            int q = ticks < quadroAte ? 1 : 0;
            if (q != quadro) {
                quadro = q;
                modelo.setItemStack(item(q));
            }
        }
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
                if (modelo != null && modelo.isValid() && v != p.canSee(modelo)) {
                    if (v) p.showEntity(pl, modelo);
                    else p.hideEntity(pl, modelo);
                }
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
        quadroAte = ticks + 40;
        switch (rnd.nextInt(4)) {
            case 0 -> {
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
                }, 30L);
            }
            case 1 -> {
                // investida: salta para cima do jogador mais longe
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
            default -> {
                // chuva de magma: marca o chao embaixo de ate 4 jogadores e explode 1,5 s depois
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
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        pl.getLogger().info("Boss mundial derrotado por " + ordem.size() + " jogador(es)");
        acabar(true);
    }

    private void acabar(boolean morto) {
        if (tarefa != null) tarefa.cancel();
        tarefa = null;
        if (barra != null) for (UUID id : naBarra) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(barra);
        }
        naBarra.clear();
        barra = null;
        if (modelo != null && modelo.isValid()) modelo.remove();
        modelo = null;
        if (corpo != null && corpo.isValid()) corpo.remove();
        corpo = null;
        for (Entity a : ajudantes) if (a.isValid()) a.remove();
        ajudantes.clear();
    }

    // ------------------------------------------------------------------ comando

    boolean comando(CommandSender s, String[] a) {
        if (a.length >= 1 && s.hasPermission("extras.admin")) {
            switch (a[0].toLowerCase()) {
                case "invocar", "nascer", "spawn" -> {
                    if (!(s instanceof Player p)) {
                        World w = Bukkit.getWorld(pl.getConfig().getString("boss-mundial.mundo", "world"));
                        Location l = w == null ? null : lugar(w);
                        if (l == null) s.sendMessage("Nao achei lugar.");
                        else nascer(l);
                    } else nascer(p.getLocation());
                    return true;
                }
                case "remover", "tirar" -> {
                    acabar(false);
                    s.sendMessage(Extras.txt("§7Boss mundial removido."));
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
