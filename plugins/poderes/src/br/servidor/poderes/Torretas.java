package br.servidor.poderes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Torres do Engenheiro: cada tipo tem um visual e um ataque diferente. Uma de cada tipo por vez. */
final class Torretas implements Listener {

    /** Tipos de torre: alcance (blocos), intervalo entre ataques (ticks), duracao (s). */
    enum Tipo {
        ARQUEIRA("Torre Arqueira", "§a", 16, 10, 20),
        GELO("Torre de Gelo", "§b", 12, 15, 20),
        FOGO("Torre Lanca-Chamas", "§6", 8, 10, 20),
        TESLA("Torre Tesla", "§e", 12, 30, 20),
        CANHAO("Torre Canhao", "§8", 22, 50, 20);

        final String nome;
        final String cor;
        final double alcance;
        final int intervalo;
        final int segundos;

        Tipo(String nome, String cor, double alcance, int intervalo, int segundos) {
            this.nome = nome;
            this.cor = cor;
            this.alcance = alcance;
            this.intervalo = intervalo;
            this.segundos = segundos;
        }
    }

    // Danos base (x forca do nivel). 2 de dano = 1 coracao.
    static final double DANO_ARQUEIRA = 1.5;
    static final double DANO_GELO = 1.0;
    static final double DANO_FOGO = 1.2;
    static final double DANO_TESLA = 3.0;
    static final double DANO_TESLA_PULO = 2.0;
    static final double DANO_CANHAO = 5.0;

    private final Poderes plugin;
    private final NamespacedKey chaveTorreta;
    private final NamespacedKey chaveTipo;
    private final NamespacedKey chaveBala;
    /** dono -> (tipo -> armor stand) */
    private final Map<UUID, EnumMap<Tipo, UUID>> torres = new HashMap<>();
    /** armor stand -> tarefa */
    private final Map<UUID, BukkitTask> tarefas = new HashMap<>();
    /** dono -> ultimo inimigo (quem ele bateu ou quem bateu nele) */
    private final Map<UUID, UUID> ultimoInimigo = new HashMap<>();
    private final Map<UUID, Long> ultimoInimigoQuando = new HashMap<>();

    Torretas(Poderes plugin) {
        this.plugin = plugin;
        this.chaveTorreta = new NamespacedKey(plugin, "torreta_dono");
        this.chaveTipo = new NamespacedKey(plugin, "torreta_tipo");
        this.chaveBala = new NamespacedKey(plugin, "torreta_bala");
    }

    // ------------------------------------------------------------------ colocar

    boolean colocar(Player p, Tipo tipo, double fator) {
        if (torreta(p, tipo) != null) {
            Poderes.barra(p, "§eSua " + tipo.nome + " ja esta ativa!");
            return false;
        }
        Location l = local(p);
        if (l == null) {
            Poderes.barra(p, "§7Sem lugar para colocar a torre (olhe para o chao perto de voce).");
            return false;
        }
        World w = l.getWorld();
        ArmorStand t = w.spawn(l, ArmorStand.class, a -> montar(a, tipo, p));
        if (!t.isValid()) {
            plugin.getLogger().warning(tipo.nome + " de " + p.getName() + " nao conseguiu nascer em " + fmt(l) + " (spawn bloqueado por outro plugin?).");
            Poderes.barra(p, "§cNao deu para colocar a torre aqui.");
            return false;
        }
        UUID dono = p.getUniqueId();
        torres.computeIfAbsent(dono, k -> new EnumMap<>(Tipo.class)).put(tipo, t.getUniqueId());
        w.playSound(l, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.3f);
        Fx.p(w, Particle.CLOUD, l.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.03);
        Color cor = corDe(tipo);
        org.bukkit.block.data.BlockData vidro = vidroDe(tipo);
        plugin.fx.colunaDeLuz(l, vidro, 4.0, 1.0, 9, cor);
        plugin.cena.torreChega(l, cor, vidro);
        plugin.fx.anel(l.clone().add(0, 0.1, 0), vidro, 2.0, 6, 10, 0.28f, cor);
        plugin.fx.estilhacos(l.clone().add(0, 0.4, 0), new org.bukkit.block.data.BlockData[] {Efeitos.b(Material.IRON_BLOCK), Efeitos.b(Material.COPPER_BLOCK), Efeitos.b(Material.SMOOTH_STONE)}, 8, 1.8, 12, 0.28f);
        Poderes.barra(p, tipo.cor + tipo.nome + " §fcolocada por §e" + tipo.segundos + "s");

        final int[] ticks = {tipo.segundos * 20};
        final int[] carga = {tipo.intervalo};
        BukkitTask tarefa = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!t.isValid() || !p.isOnline()) {
                removerUma(dono, tipo, false);
                return;
            }
            ticks[0] -= 5;
            if (ticks[0] <= 0) {
                removerUma(dono, tipo, true);
                return;
            }
            carga[0] += 5;
            if (ticks[0] % 10 == 0) enfeitar(t, tipo);
            LivingEntity alvo = carga[0] >= tipo.intervalo ? escolherAlvo(p, t, tipo.alcance) : null;
            String tempo = ((ticks[0] + 19) / 20) + "s";
            if (alvo != null) {
                carga[0] = 0;
                virarPara(t, alvo.getEyeLocation());
                atacar(p, t, tipo, alvo, fator);
                t.customName(Poderes.txt(tipo.cor + tipo.nome + " §7(" + tempo + ") §4atacando"));
            } else if (ticks[0] % 10 == 0) {
                t.customName(Poderes.txt(tipo.cor + tipo.nome + " §7(" + tempo + ")"));
                if (carga[0] >= tipo.intervalo) t.setRotation(t.getLocation().getYaw() + 20f, 0f);
            }
        }, 5L, 5L);
        tarefas.put(t.getUniqueId(), tarefa);
        return true;
    }

    static Color corDe(Tipo t) {
        return switch (t) {
            case ARQUEIRA -> Color.fromRGB(90, 220, 90);
            case GELO -> Color.fromRGB(150, 220, 255);
            case FOGO -> Color.fromRGB(255, 140, 30);
            case TESLA -> Color.fromRGB(255, 235, 90);
            case CANHAO -> Color.fromRGB(120, 120, 130);
        };
    }

    static org.bukkit.block.data.BlockData vidroDe(Tipo t) {
        return Efeitos.b(switch (t) {
            case ARQUEIRA -> Material.LIME_STAINED_GLASS;
            case GELO -> Material.LIGHT_BLUE_STAINED_GLASS;
            case FOGO -> Material.ORANGE_STAINED_GLASS;
            case TESLA -> Material.YELLOW_STAINED_GLASS;
            case CANHAO -> Material.GRAY_STAINED_GLASS;
        });
    }

    /** Visual de cada torre. */
    private void montar(ArmorStand a, Tipo tipo, Player p) {
        a.setInvulnerable(true);
        a.setGravity(false);
        a.setBasePlate(false);
        a.setPersistent(false);
        EntityEquipment eq = a.getEquipment();
        switch (tipo) {
            case ARQUEIRA -> {
                a.setArms(false);
                eq.setHelmet(new ItemStack(Material.DISPENSER));
                eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            }
            case GELO -> {
                a.setSmall(true);
                a.setArms(true);
                eq.setHelmet(new ItemStack(Material.BLUE_ICE));
                eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, Color.fromRGB(140, 210, 255)));
                eq.setLeggings(couro(Material.LEATHER_LEGGINGS, Color.fromRGB(200, 235, 255)));
                eq.setItemInMainHand(new ItemStack(Material.PACKED_ICE));
            }
            case FOGO -> {
                a.setArms(true);
                eq.setHelmet(new ItemStack(Material.MAGMA_BLOCK));
                eq.setChestplate(couro(Material.LEATHER_CHESTPLATE, Color.fromRGB(200, 60, 20)));
                eq.setLeggings(couro(Material.LEATHER_LEGGINGS, Color.fromRGB(60, 30, 20)));
                eq.setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
                eq.setItemInOffHand(new ItemStack(Material.BLAZE_ROD));
            }
            case TESLA -> {
                a.setArms(true);
                eq.setHelmet(new ItemStack(Material.LIGHTNING_ROD));
                eq.setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
                eq.setItemInMainHand(new ItemStack(Material.END_ROD));
            }
            case CANHAO -> {
                a.setArms(false);
                eq.setHelmet(new ItemStack(Material.BLAST_FURNACE));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            }
        }
        for (EquipmentSlot s : EquipmentSlot.values()) {
            try {
                a.addEquipmentLock(s, ArmorStand.LockType.REMOVING_OR_CHANGING);
                a.addEquipmentLock(s, ArmorStand.LockType.ADDING_OR_CHANGING);
            } catch (IllegalArgumentException ignored) {
                // slot que o suporte nao tem
            }
        }
        a.customName(Poderes.txt(tipo.cor + tipo.nome + " de " + p.getName()));
        a.setCustomNameVisible(true);
        a.getPersistentDataContainer().set(chaveTorreta, PersistentDataType.STRING, p.getUniqueId().toString());
        a.getPersistentDataContainer().set(chaveTipo, PersistentDataType.STRING, tipo.name());
    }

    private static ItemStack couro(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(c);
            it.setItemMeta(lm);
        }
        return it;
    }

    /** Particulas paradas de cada torre (para dar vida). */
    private void enfeitar(ArmorStand t, Tipo tipo) {
        World w = t.getWorld();
        Location topo = t.getEyeLocation().add(0, 0.4, 0);
        switch (tipo) {
            case GELO -> Fx.p(w, Particle.SNOWFLAKE, topo, 3, 0.3, 0.3, 0.3, 0.01);
            case FOGO -> Fx.p(w, Particle.FLAME, topo, 3, 0.2, 0.2, 0.2, 0.01);
            case TESLA -> Fx.p(w, Particle.ELECTRIC_SPARK, topo.add(0, 0.4, 0), 4, 0.15, 0.3, 0.15, 0.05);
            case CANHAO -> Fx.p(w, Particle.SMOKE, topo, 3, 0.15, 0.1, 0.15, 0.01);
            default -> { }
        }
    }

    private Location local(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        List<Block> candidatos = new ArrayList<>();
        RayTraceResult r = w.rayTraceBlocks(olho, olho.getDirection(), 6.0, FluidCollisionMode.NEVER, true);
        if (r != null && r.getHitBlock() != null) {
            if (r.getHitBlockFace() == BlockFace.UP) candidatos.add(r.getHitBlock().getRelative(BlockFace.UP));
            else if (r.getHitBlockFace() != null) candidatos.add(r.getHitBlock().getRelative(r.getHitBlockFace()));
        }
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        dir.normalize();
        Vector lado = new Vector(-dir.getZ(), 0, dir.getX());
        Location pe = p.getLocation();
        for (double d : new double[] {1.5, 1.0, 2.0, 2.5}) {
            for (double s : new double[] {0, 1.2, -1.2}) {
                Block b = pe.clone().add(dir.clone().multiply(d)).add(lado.clone().multiply(s)).getBlock();
                candidatos.add(b);
                candidatos.add(b.getRelative(BlockFace.UP));
                candidatos.add(b.getRelative(BlockFace.DOWN));
            }
        }
        candidatos.add(pe.getBlock());
        candidatos.add(pe.getBlock().getRelative(BlockFace.UP));
        for (Block base : candidatos) {
            if (!base.isPassable() || !base.getRelative(BlockFace.UP).isPassable()) continue;
            if (base.getType() == Material.LAVA || base.getType() == Material.WATER) continue;
            // desce ate encostar no chao (no maximo 3 blocos)
            Block chao = base;
            for (int i = 0; i < 3 && chao.getRelative(BlockFace.DOWN).isPassable(); i++) chao = chao.getRelative(BlockFace.DOWN);
            Location l = chao.getLocation().add(0.5, 0, 0.5);
            if (temTorreAqui(l)) continue;
            l.setYaw(p.getLocation().getYaw());
            return l;
        }
        return null;
    }

    private boolean temTorreAqui(Location l) {
        for (Entity e : l.getWorld().getNearbyEntities(l, 0.7, 1.5, 0.7)) {
            if (ehTorreta(e)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ mira e ataque

    private LivingEntity escolherAlvo(Player dono, ArmorStand t, double alcance) {
        UUID inimigo = ultimoInimigo.get(dono.getUniqueId());
        Long quando = ultimoInimigoQuando.get(dono.getUniqueId());
        if (inimigo != null && quando != null && System.currentTimeMillis() - quando < 10_000L) {
            Entity e = Bukkit.getEntity(inimigo);
            if (e instanceof LivingEntity le && le.isValid() && !le.isDead() && e.getWorld().equals(t.getWorld())
                    && e.getLocation().distanceSquared(t.getLocation()) <= alcance * alcance && t.hasLineOfSight(e)) {
                return le;
            }
        }
        LivingEntity melhor = null;
        double dist = Double.MAX_VALUE;
        for (Entity e : t.getNearbyEntities(alcance, 8, alcance)) {
            if (!(e instanceof LivingEntity le) || !(e instanceof Enemy) || le.isDead()) continue;
            if (plugin.ehAliado(e, dono) || plugin.pets.ehPet(e) || !t.hasLineOfSight(e)) continue;
            double d = e.getLocation().distanceSquared(t.getLocation());
            if (d < dist) {
                dist = d;
                melhor = le;
            }
        }
        return melhor;
    }

    private static void virarPara(ArmorStand t, Location alvo) {
        Vector dir = alvo.toVector().subtract(t.getEyeLocation().toVector());
        if (dir.lengthSquared() < 0.01) return;
        Location vira = t.getLocation().setDirection(dir);
        t.setRotation(vira.getYaw(), 0f);
    }

    private void atacar(Player dono, ArmorStand t, Tipo tipo, LivingEntity alvo, double f) {
        switch (tipo) {
            case ARQUEIRA -> flecha(dono, t, alvo, f);
            case GELO -> gelo(dono, t, alvo, f);
            case FOGO -> fogo(dono, t, alvo, f);
            case TESLA -> tesla(dono, t, alvo, f);
            case CANHAO -> canhao(dono, t, alvo, f);
        }
    }

    private void flecha(Player dono, ArmorStand t, LivingEntity alvo, double f) {
        World w = t.getWorld();
        Location origem = t.getEyeLocation();
        Vector dir = alvo.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(origem.toVector());
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        Arrow flecha = w.spawnArrow(origem.clone().add(dir.clone().multiply(0.9)), dir, 2.8f, 0f);
        flecha.setShooter(dono);
        flecha.setGravity(false);
        flecha.setCritical(false);
        // dano da flecha = velocidade (2.8) x base, arredondado pra cima: fica ~DANO_ARQUEIRA x forca
        flecha.setDamage(DANO_ARQUEIRA * f / 2.8);
        flecha.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        flecha.getPersistentDataContainer().set(chaveBala, PersistentDataType.STRING, dono.getUniqueId().toString());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flecha.isValid()) flecha.remove();
        }, 40L);
        w.playSound(origem, Sound.BLOCK_DISPENSER_LAUNCH, 0.7f, 1.4f);
        Fx.p(w, Particle.SMOKE, origem, 4, 0.1, 0.1, 0.1, 0.01);
    }

    private void gelo(Player dono, ArmorStand t, LivingEntity alvo, double f) {
        World w = t.getWorld();
        Location a = t.getEyeLocation();
        Location b = alvo.getLocation().add(0, alvo.getHeight() * 0.6, 0);
        Particle.DustOptions azul = new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.1f);
        linha(a, b, 0.35, pt -> {
            Fx.p(w, Particle.DUST, pt, 1, 0.03, 0.03, 0.03, 0, azul);
            Fx.p(w, Particle.SNOWFLAKE, pt, 1, 0.05, 0.05, 0.05, 0.0);
        });
        Fx.p(w, Particle.SNOWFLAKE, b, 15, 0.4, 0.5, 0.4, 0.03);
        w.playSound(a, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.9f);
        plugin.fx.corte(a, b, Efeitos.b(Material.LIGHT_BLUE_STAINED_GLASS), 0.16, 6, Color.fromRGB(150, 220, 255));
        plugin.cena.raioDeGelo(a, b);
        plugin.fx.estilhacos(b, new org.bukkit.block.data.BlockData[] {Efeitos.b(Material.ICE), Efeitos.b(Material.PACKED_ICE)}, 4, 1.2, 9, 0.2f);
        if (plugin.causarDano(alvo, DANO_GELO * f, dono)) {
            alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2));
            alvo.setFreezeTicks(Math.max(alvo.getFreezeTicks(), 100));
        }
    }

    private void fogo(Player dono, ArmorStand t, LivingEntity alvo, double f) {
        World w = t.getWorld();
        Location a = t.getEyeLocation();
        Location b = alvo.getLocation().add(0, alvo.getHeight() * 0.5, 0);
        linha(a, b, 0.4, pt -> Fx.p(w, Particle.FLAME, pt, 2, 0.12, 0.12, 0.12, 0.02));
        Fx.p(w, Particle.FLAME, b, 12, 0.5, 0.5, 0.5, 0.03);
        w.playSound(a, Sound.ENTITY_BLAZE_SHOOT, 0.4f, 0.7f);
        plugin.fx.corte(a, b, Efeitos.b(Material.ORANGE_STAINED_GLASS), 0.28, 5, Color.fromRGB(255, 140, 30));
        plugin.cena.jatoDeFogo(a, b);
        plugin.fx.pulso(b, Efeitos.b(Material.ORANGE_STAINED_GLASS), Efeitos.b(Material.SHROOMLIGHT), 0.2f, 1.1f, 4, Color.fromRGB(255, 140, 30));
        for (Entity e : w.getNearbyEntities(b, 1.6, 1.6, 1.6)) {
            if (!(e instanceof LivingEntity le) || !(e.equals(alvo) || plugin.inimigoArea(e, dono))) continue;
            if (plugin.causarDano(le, DANO_FOGO * f, dono)) le.setFireTicks(Math.max(le.getFireTicks(), 60));
        }
    }

    private void tesla(Player dono, ArmorStand t, LivingEntity alvo, double f) {
        World w = t.getWorld();
        Set<UUID> atingidos = new HashSet<>();
        Location de = t.getEyeLocation().add(0, 0.6, 0);
        LivingEntity atual = alvo;
        double dano = DANO_TESLA * f;
        for (int pulo = 0; pulo < 3 && atual != null; pulo++) {
            Location ate = atual.getLocation().add(0, atual.getHeight() * 0.6, 0);
            raioZigZag(w, de, ate);
            plugin.fx.raio(de, ate, Efeitos.b(Material.WHITE_STAINED_GLASS), Math.max(3, (int) (de.distance(ate) / 1.3)), 0.35, 0.1, 4, Color.fromRGB(255, 240, 120));
            plugin.cena.tesla(de, ate);
            atingidos.add(atual.getUniqueId());
            if (plugin.causarDano(atual, dano, dono)) {
                atual.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 4));
            }
            Fx.p(w, Particle.ELECTRIC_SPARK, ate, 12, 0.3, 0.4, 0.3, 0.1);
            de = ate;
            dano = DANO_TESLA_PULO * f;
            LivingEntity prox = null;
            double melhor = 25.0;
            for (Entity e : w.getNearbyEntities(ate, 5, 3, 5)) {
                if (!(e instanceof LivingEntity le) || le.isDead() || atingidos.contains(e.getUniqueId())) continue;
                if (!plugin.inimigoArea(e, dono)) continue;
                double d = e.getLocation().distanceSquared(ate);
                if (d < melhor) {
                    melhor = d;
                    prox = le;
                }
            }
            atual = prox;
        }
        w.playSound(t.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
    }

    static void raioZigZag(World w, Location a, Location b) {
        Vector total = b.toVector().subtract(a.toVector());
        double dist = total.length();
        if (dist < 0.1) return;
        int partes = Math.max(2, (int) (dist / 1.2));
        Location ant = a.clone();
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 1; i <= partes; i++) {
            Location pt = a.clone().add(total.clone().multiply((double) i / partes));
            if (i < partes) pt.add(r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35), r.nextDouble(-0.35, 0.35));
            final Location de = ant;
            linha(de, pt, 0.25, x -> {
                Fx.p(w, Particle.ELECTRIC_SPARK, x, 1, 0, 0, 0, 0);
                Fx.p(w, Particle.END_ROD, x, 1, 0, 0, 0, 0);
            });
            ant = pt;
        }
    }

    private void canhao(Player dono, ArmorStand t, LivingEntity alvo, double f) {
        World w = t.getWorld();
        Location a = t.getEyeLocation().add(0, 0.3, 0);
        Location b = alvo.getLocation();
        // preve um pouco o movimento do alvo
        b.add(alvo.getVelocity().clone().setY(0).multiply(8));
        w.playSound(a, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        Fx.p(w, Particle.LARGE_SMOKE, a, 8, 0.2, 0.2, 0.2, 0.03);
        Vector delta = b.toVector().subtract(a.toVector());
        double altura = 2.0 + delta.length() * 0.25;
        final int duracao = 16;
        final int[] tk = {0};
        org.bukkit.entity.BlockDisplay balaPc = plugin.vis.bloco(a, Material.COAL_BLOCK.createBlockData(), duracao + 10, Color.fromRGB(255, 120, 40));
        Visuais.escala(balaPc, 0.5f, 0f, 0);
        org.bukkit.entity.FallingBlock bala = plugin.cena.bloco(a.clone().subtract(0, 0.5, 0), Material.COAL_BLOCK.createBlockData(), null, false, duracao + 10);
        plugin.fx.pulso(a, Efeitos.b(Material.GRAY_STAINED_GLASS), null, 0.3f, 1.4f, 4, null);
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            tk[0]++;
            double s = (double) tk[0] / duracao;
            Location pt = a.clone().add(delta.clone().multiply(s)).add(0, 4 * altura * s * (1 - s), 0);
            if (balaPc.isValid()) {
                balaPc.teleport(pt);
                Visuais.escala(balaPc, 0.5f, tk[0] * 0.6f, 1);
            }
            Cenas.levar(bala, pt.clone().subtract(0, 0.5, 0));
            Fx.p(w, Particle.LARGE_SMOKE, pt, 2, 0.05, 0.05, 0.05, 0.0);
            Fx.p(w, Particle.FLAME, pt, 2, 0.05, 0.05, 0.05, 0.0);
            if (tk[0] < duracao && pt.getBlock().isPassable()) return;
            tarefa.cancel();
            plugin.vis.remover(balaPc);
            plugin.vis.remover(bala);
            plugin.cena.canhaoImpacto(pt);
            plugin.fx.pulso(pt.clone().add(0, 0.5, 0), Efeitos.b(Material.ORANGE_STAINED_GLASS), Efeitos.b(Material.SHROOMLIGHT), 0.5f, 3.4f, 6, Color.fromRGB(255, 120, 40));
            plugin.fx.estilhacos(pt.clone().add(0, 0.3, 0), new org.bukkit.block.data.BlockData[] {Poderes.chaoDe(pt), Efeitos.b(Material.COBBLESTONE), Efeitos.b(Material.COAL_BLOCK)}, 10, 3.0, 14, 0.35f);
            plugin.fx.anel(pt.clone().add(0, 0.1, 0), Poderes.chaoDe(pt), 3.2, 6, 12, 0.35f, null);
            Fx.p(w, Particle.EXPLOSION, pt, 3, 0.6, 0.4, 0.6, 0);
            Fx.p(w, Particle.LAVA, pt, 10, 0.6, 0.3, 0.6, 0);
            w.playSound(pt, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
            if (!dono.isOnline()) return;
            for (Entity e : w.getNearbyEntities(pt, 3.5, 3.0, 3.5)) {
                if (!(e instanceof LivingEntity le) || !(e.equals(alvo) || plugin.inimigoArea(e, dono))) continue;
                if (plugin.causarDano(le, DANO_CANHAO * f, dono)) {
                    Vector v = le.getLocation().toVector().subtract(pt.toVector()).setY(0);
                    if (v.lengthSquared() > 0.01) v.normalize().multiply(0.6);
                    v.setY(0.45);
                    le.setVelocity(le.getVelocity().add(v));
                }
            }
        }, 1L, 1L);
    }

    /** Chama 'acao' em pontos ao longo do segmento a-b, a cada 'passo' blocos. */
    static void linha(Location a, Location b, double passo, java.util.function.Consumer<Location> acao) {
        Vector d = b.toVector().subtract(a.toVector());
        double dist = d.length();
        if (dist < 0.01) return;
        d.normalize();
        for (double x = 0; x <= dist; x += passo) acao.accept(a.clone().add(d.clone().multiply(x)));
    }

    private static String fmt(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    // ------------------------------------------------------------------ consulta / remocao

    ArmorStand torreta(Player p, Tipo tipo) {
        EnumMap<Tipo, UUID> m = torres.get(p.getUniqueId());
        if (m == null) return null;
        UUID id = m.get(tipo);
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        return e instanceof ArmorStand a && a.isValid() ? a : null;
    }

    boolean ehTorreta(Entity e) {
        return e instanceof ArmorStand && e.getPersistentDataContainer().has(chaveTorreta, PersistentDataType.STRING);
    }

    boolean ehTorretaDe(Entity e, Player p) {
        if (!ehTorreta(e)) return false;
        return p.getUniqueId().toString().equals(e.getPersistentDataContainer().get(chaveTorreta, PersistentDataType.STRING));
    }

    private boolean temTorre(UUID dono) {
        EnumMap<Tipo, UUID> m = torres.get(dono);
        return m != null && !m.isEmpty();
    }

    void removerUma(UUID dono, Tipo tipo, boolean avisar) {
        EnumMap<Tipo, UUID> m = torres.get(dono);
        if (m == null) return;
        UUID id = m.remove(tipo);
        if (m.isEmpty()) torres.remove(dono);
        if (id == null) return;
        BukkitTask t = tarefas.remove(id);
        if (t != null) t.cancel();
        Entity e = Bukkit.getEntity(id);
        if (e != null && e.isValid()) {
            Fx.p(e.getWorld(), Particle.CLOUD, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.03);
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.6f, 1.2f);
            e.remove();
        }
        Player p = Bukkit.getPlayer(dono);
        if (avisar && p != null) Poderes.barra(p, "§7Sua " + tipo.nome + " acabou.");
    }

    void remover(UUID dono, boolean avisar) {
        EnumMap<Tipo, UUID> m = torres.get(dono);
        if (m == null) return;
        for (Tipo t : new ArrayList<>(m.keySet())) removerUma(dono, t, avisar);
    }

    void removerTodos() {
        for (UUID d : new ArrayList<>(torres.keySet())) remover(d, false);
    }

    // ------------------------------------------------------------------ eventos

    private static Player jogadorDe(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    /** Flecha da torre nao machuca o dono nem os aliados. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protegerAliados(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow f)) return;
        String d = f.getPersistentDataContainer().get(chaveBala, PersistentDataType.STRING);
        if (d == null) return;
        Player dono = Bukkit.getPlayer(UUID.fromString(d));
        if (dono == null) return;
        Entity v = e.getEntity();
        if (plugin.ehAliado(v, dono) || plugin.pets.ehPet(v) || ehTorreta(v)) e.setCancelled(true);
    }

    @EventHandler
    public void balaAcertou(ProjectileHitEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(chaveBala, PersistentDataType.STRING)) return;
        Projectile pr = e.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pr.isValid()) pr.remove();
        });
    }

    /** Guarda quem o dono bateu e quem bateu no dono, para as torres mirarem nele. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void marcarInimigo(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Arrow f && f.getPersistentDataContainer().has(chaveBala, PersistentDataType.STRING)) return;
        Entity vitima = e.getEntity();
        Player dono = jogadorDe(e.getDamager());
        if (dono != null && vitima instanceof LivingEntity && !plugin.ehAliado(vitima, dono) && temTorre(dono.getUniqueId())) {
            ultimoInimigo.put(dono.getUniqueId(), vitima.getUniqueId());
            ultimoInimigoQuando.put(dono.getUniqueId(), System.currentTimeMillis());
            return;
        }
        if (vitima instanceof Player jogador && temTorre(jogador.getUniqueId())) {
            Entity atacante = e.getDamager();
            if (atacante instanceof Projectile pr && pr.getShooter() instanceof Entity atirador) atacante = atirador;
            if (atacante instanceof LivingEntity && !plugin.ehAliado(atacante, jogador)) {
                ultimoInimigo.put(jogador.getUniqueId(), atacante.getUniqueId());
                ultimoInimigoQuando.put(jogador.getUniqueId(), System.currentTimeMillis());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void naoMexer(PlayerArmorStandManipulateEvent e) {
        if (ehTorreta(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler
    public void donoSaiu(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        remover(id, false);
        ultimoInimigo.remove(id);
        ultimoInimigoQuando.remove(id);
    }

    @EventHandler
    public void donoMorreu(PlayerDeathEvent e) {
        remover(e.getEntity().getUniqueId(), false);
    }
}
