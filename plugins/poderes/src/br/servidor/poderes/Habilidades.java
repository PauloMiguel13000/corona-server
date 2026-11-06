package br.servidor.poderes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** As habilidades novas das classes (as antigas continuam em Poderes, Torretas e Guardioes). */
final class Habilidades implements Listener {

    // Danos base (x forca do nivel: 1x a 2x). 2 de dano = 1 coracao.
    static final double DANO_VENENO = 1.5;      // por segundo, 5 segundos
    static final double DANO_METEORO = 3.5;     // por meteoro (5 meteoros)
    static final double DANO_KI = 2.2;
    static final int KI_RAJADA = 4;
    static final int KI_RAJADA_SSJ = 6;
    static final double DANO_GRITO = 3.0;
    static final double DANO_TERREMOTO = 6.0;
    static final double DANO_SHURIKEN = 2.5;    // por estrela (3 estrelas)
    static final double DANO_LAMINAS = 1.5;     // a cada meio segundo, 3 segundos
    static final double DANO_RELAMPAGO = 5.0;   // por inimigo (ate 5)
    static final double DANO_RAIZES = 2.0;
    static final double DANO_ESPINHOS = 3.0;    // por onda (3 ondas)
    static final double DANO_LOBO = 3.0;
    static final double SAIYAJIN_MULT = 1.3;
    // Tridente
    static final double DANO_ARREMESSO = 6.0;
    static final double DANO_ONDA = 4.5;
    static final double DANO_TRIDENTE_RAIO = 7.0;  // por inimigo (ate 3)
    static final double DANO_REDEMOINHO = 1.8;     // a cada meio segundo, 4 segundos
    static final double DANO_POSEIDON = 8.0;
    // Arqueiro
    static final double DANO_TIRO = 6.0;
    static final double DANO_EXPLOSIVA = 6.0;
    static final double DANO_MULTIPLO = 3.0;       // por flecha (5)
    static final double DANO_CHUVA = 1.6;          // a cada 1/4 s, 3 segundos
    static final double DANO_TEMPESTADE = 4.0;     // por flecha (3 rajadas)

    private final Poderes plugin;
    private final NamespacedKey chaveLobo;
    private final Map<UUID, Long> saiyajinAte = new HashMap<>();
    private final Map<UUID, BukkitTask> auraSaiyajin = new HashMap<>();
    private final Map<UUID, Long> fumacaAte = new HashMap<>();
    private final Map<UUID, List<UUID>> lobos = new HashMap<>();
    private final Map<UUID, BukkitTask> tarefaLobos = new HashMap<>();
    /** Quem esta no meio de uma habilidade de varios passos (terremoto, laminas, relampago). */
    private final Set<UUID> emSequencia = new HashSet<>();

    Habilidades(Poderes plugin) {
        this.plugin = plugin;
        this.chaveLobo = new NamespacedKey(plugin, "lobo_espiritual");
    }

    // ------------------------------------------------------------------ ajudantes

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    private static Particle.DustOptions cor(int r, int g, int b, float tamanho) {
        return new Particle.DustOptions(Color.fromRGB(r, g, b), tamanho);
    }

    /** Ponto no chao para onde o jogador esta mirando (ate 'alcance' blocos). */
    Location mira(Player p, double alcance) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        RayTraceResult r = w.rayTrace(olho, dir, alcance, FluidCollisionMode.NEVER, true, 0.5,
                e -> e instanceof LivingEntity vivo && !vivo.isDead() && !(e instanceof ArmorStand) && !plugin.ehAliado(e, p) && !plugin.pets.ehPet(e));
        Location l;
        if (r == null) {
            l = olho.clone().add(dir.clone().multiply(alcance));
        } else if (r.getHitEntity() != null) {
            l = r.getHitEntity().getLocation();
        } else {
            l = r.getHitPosition().toLocation(w);
            if (r.getHitBlockFace() != null) l.add(r.getHitBlockFace().getDirection().multiply(0.4));
        }
        Block b = l.getBlock();
        for (int i = 0; i < 3 && !b.isPassable(); i++) b = b.getRelative(BlockFace.UP);
        for (int i = 0; i < 16 && b.getY() > w.getMinHeight() && b.getRelative(BlockFace.DOWN).isPassable(); i++) {
            b = b.getRelative(BlockFace.DOWN);
        }
        return new Location(w, l.getX(), b.getY(), l.getZ());
    }

    /** Inimigos (mobs hostis, jogadores, ou quem esta mirando no jogador) num raio. */
    List<LivingEntity> inimigosPerto(Location c, double raio, double altura, Player p) {
        List<LivingEntity> r = new ArrayList<>();
        for (Entity e : c.getWorld().getNearbyEntities(c, raio, altura, raio)) {
            if (!plugin.inimigoArea(e, p)) continue;
            Location el = e.getLocation();
            double dx = el.getX() - c.getX();
            double dz = el.getZ() - c.getZ();
            if (dx * dx + dz * dz > raio * raio) continue;
            r.add((LivingEntity) e);
        }
        return r;
    }

    /** Lugar livre nas costas do alvo, virado para ele (ou null). */
    static Location atras(LivingEntity alvo) {
        Location l = alvo.getLocation();
        Vector costas = l.getDirection().setY(0);
        if (costas.lengthSquared() < 0.01) costas = new Vector(0, 0, 1);
        costas.normalize().multiply(-1);
        Vector lado = new Vector(-costas.getZ(), 0, costas.getX());
        Vector[] opcoes = {
                costas.clone().multiply(1.3), costas.clone().multiply(0.9), costas.clone().multiply(1.8),
                lado.clone().multiply(1.3), lado.clone().multiply(-1.3), costas.clone().multiply(-1.3)
        };
        for (Vector v : opcoes) {
            Location d = l.clone().add(v);
            Block pe = d.getBlock();
            for (int i = 0; i < 2 && !pe.isPassable(); i++) pe = pe.getRelative(BlockFace.UP);
            if (!pe.isPassable() || !pe.getRelative(BlockFace.UP).isPassable()) continue;
            if (pe.getType() == Material.LAVA || pe.getRelative(BlockFace.UP).getType() == Material.LAVA) continue;
            Block chao = pe;
            int queda = 0;
            while (queda < 3 && chao.getRelative(BlockFace.DOWN).isPassable()) {
                chao = chao.getRelative(BlockFace.DOWN);
                queda++;
            }
            Location r = new Location(l.getWorld(), d.getX(), chao.getY(), d.getZ());
            r.setDirection(l.toVector().subtract(r.toVector()));
            r.setPitch(0f);
            return r;
        }
        return null;
    }

    /**
     * Projetil feito de particulas: anda 'velocidade' blocos por tick ate 'alcance'.
     * Para no primeiro inimigo (chama 'acerto') ou bloco solido (chama 'fim').
     */
    private void projetil(Player p, Location origem, Vector dir, double alcance, double velocidade, double raio,
                          Consumer<Location> desenho, BiConsumer<LivingEntity, Location> acerto, Consumer<Location> fim) {
        World w = origem.getWorld();
        Location pos = origem.clone();
        Vector passo = dir.clone().normalize().multiply(0.5);
        int subpassos = Math.max(1, (int) Math.round(velocidade / 0.5));
        final double[] andado = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (!p.isOnline()) {
                tarefa.cancel();
                return;
            }
            for (int i = 0; i < subpassos; i++) {
                pos.add(passo);
                andado[0] += 0.5;
                if (andado[0] > alcance || !pos.getBlock().isPassable()) {
                    tarefa.cancel();
                    if (fim != null) fim.accept(pos);
                    return;
                }
                for (Entity e : w.getNearbyEntities(pos, raio, raio, raio)) {
                    if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || le.isDead()) continue;
                    if (plugin.ehAliado(e, p) || plugin.pets.ehPet(e)) continue;
                    tarefa.cancel();
                    acerto.accept(le, pos);
                    return;
                }
            }
            desenho.accept(pos);
        }, 0L, 1L);
    }

    // ------------------------------------------------------------------ MAGO

    boolean veneno(Player p) {
        Location c = mira(p, 20);
        double f = plugin.fator(p);
        World w = c.getWorld();
        Particle.DustOptions verde = cor(70, 200, 60, 1.6f);
        Particle.DustOptions escuro = cor(40, 110, 30, 1.2f);
        w.playSound(p.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 1f, 0.8f);
        Torretas.linha(p.getEyeLocation().subtract(0, 0.3, 0), c.clone().add(0, 0.8, 0), 0.6,
                pt -> w.spawnParticle(Particle.DUST, pt, 1, 0.02, 0.02, 0.02, 0, verde));
        w.playSound(c, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.6f);
        org.bukkit.Color corVeneno = org.bukkit.Color.fromRGB(80, 220, 60);
        plugin.cena.veneno(p, c);
        plugin.fx.estilhacos(c.clone().add(0, 0.3, 0), new BlockData[] {Efeitos.b(Material.LIME_STAINED_GLASS), Efeitos.b(Material.GREEN_STAINED_GLASS), Efeitos.b(Material.SLIME_BLOCK)}, 12, 3.0, 14, 0.3f);
        plugin.fx.anel(c.clone().add(0, 0.15, 0), Efeitos.b(Material.LIME_STAINED_GLASS), 3.5, 7, 16, 0.3f, corVeneno);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (t[0] > 100 || !p.isOnline()) {
                tarefa.cancel();
                return;
            }
            for (int i = 0; i < 14; i++) {
                double ang = rnd().nextDouble(Math.PI * 2);
                double r = Math.sqrt(rnd().nextDouble()) * 3.5;
                Location pt = c.clone().add(Math.cos(ang) * r, rnd().nextDouble(0.1, 1.6), Math.sin(ang) * r);
                w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, i % 3 == 0 ? escuro : verde);
            }
            w.spawnParticle(Particle.ITEM_SLIME, c.clone().add(0, 0.5, 0), 3, 2.0, 0.4, 2.0, 0);
            if (t[0] % 15 == 0 && t[0] <= 85) plugin.fx.bolhas(c, Efeitos.b(Material.LIME_STAINED_GLASS), 5, 3.2, 1.9, 14, corVeneno);
            if (t[0] % 20 == 0) {
                if (t[0] % 40 == 0) w.playSound(c, Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 0.6f);
                for (LivingEntity le : inimigosPerto(c, 3.5, 2.5, p)) {
                    if (plugin.causarDano(le, DANO_VENENO * f, p)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                    }
                }
            }
            t[0] += 5;
        }, 0L, 5L);
        return true;
    }

    boolean meteoros(Player p) {
        Location c = mira(p, 30);
        double f = plugin.fator(p);
        World w = c.getWorld();
        Poderes.barra(p, "§c§lCHUVA DE METEOROS!");
        w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
        Particle.DustOptions laranja = cor(255, 120, 20, 2.6f);
        for (int i = 0; i < 5; i++) {
            final int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                Location alvo = c.clone();
                if (n > 0) {
                    double ang = rnd().nextDouble(Math.PI * 2);
                    double r = rnd().nextDouble(1.0, 3.8);
                    alvo.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                    Block b = alvo.getBlock();
                    for (int k = 0; k < 3 && !b.isPassable(); k++) b = b.getRelative(BlockFace.UP);
                    for (int k = 0; k < 6 && b.getRelative(BlockFace.DOWN).isPassable(); k++) b = b.getRelative(BlockFace.DOWN);
                    alvo.setY(b.getY());
                }
                for (int k = 0; k < 16; k++) {
                    double a = Math.PI * 2 * k / 16;
                    w.spawnParticle(Particle.FLAME, alvo.clone().add(Math.cos(a) * 1.5, 0.1, Math.sin(a) * 1.5), 1, 0, 0, 0, 0);
                }
                Location topo = alvo.clone().add(rnd().nextDouble(-4, 4), 18, rnd().nextDouble(-4, 4));
                Vector caminho = alvo.toVector().subtract(topo.toVector());
                w.playSound(alvo, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
                final int[] tk = {0};
                org.bukkit.Color corFogo = org.bukkit.Color.fromRGB(255, 120, 20);
                org.bukkit.entity.BlockDisplay rochaPc = plugin.vis.bloco(topo, Material.MAGMA_BLOCK.createBlockData(), 30, corFogo);
                Visuais.escala(rochaPc, 1.2f, 0f, 0);
                org.bukkit.entity.FallingBlock rocha = plugin.cena.bloco(topo.clone().subtract(0, 0.5, 0), Material.MAGMA_BLOCK.createBlockData(), caminho.clone().multiply(1.0 / 12), false, 30);
                // prop 3D: meteoro (rocha em chamas com cauda) apontando para onde cai
                java.util.List<Player> plateia3d = plugin.props.plateia(alvo);
                org.bukkit.entity.ItemDisplay met3d = plugin.props.prop(topo, "meteoro_f0", 30, plateia3d);
                if (met3d != null) {
                    plugin.props.esconder(plateia3d, rochaPc);
                    met3d.setTeleportDuration(1);
                    Props3D.pose(met3d, caminho, 1.6f, 0f, 0);
                }
                Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
                    tk[0]++;
                    double s = tk[0] / 12.0;
                    Location pt = topo.clone().add(caminho.clone().multiply(Math.min(1.0, s)));
                    if (rochaPc.isValid()) {
                        rochaPc.teleport(pt);
                        Visuais.escala(rochaPc, 1.2f, tk[0] * 0.5f, 1);
                    }
                    if (met3d != null && met3d.isValid()) {
                        Props3D.mover(met3d, pt);
                        Props3D.pose(met3d, caminho, 1.6f, tk[0] * 0.4f, 1);
                        if (tk[0] % 2 == 0) Props3D.quadro(met3d, "meteoro_f" + ((tk[0] / 2) % 2));
                    }
                    if (rocha != null && rocha.isValid()) rocha.setVelocity(caminho.clone().multiply(1.0 / 12));
                    w.spawnParticle(Particle.DUST, pt, 6, 0.25, 0.25, 0.25, 0, laranja);
                    w.spawnParticle(Particle.FLAME, pt, 6, 0.3, 0.3, 0.3, 0.02);
                    w.spawnParticle(Particle.LARGE_SMOKE, pt, 2, 0.2, 0.2, 0.2, 0.01);
                    if (tk[0] < 12) return;
                    tarefa.cancel();
                    plugin.vis.remover(rochaPc);
                    plugin.vis.remover(rocha);
                    plugin.props.remover(met3d);
                    plugin.cena.meteoroImpacto(alvo);
                    plugin.fx.estilhacos(alvo.clone().add(0, 0.3, 0), new BlockData[] {Efeitos.b(Material.MAGMA_BLOCK), Efeitos.b(Material.NETHERRACK), Efeitos.b(Material.BLACKSTONE), Poderes.chaoDe(alvo)}, 10, 3.2, 16, 0.4f);
                    plugin.fx.pulso(alvo.clone().add(0, 0.6, 0), Efeitos.b(Material.ORANGE_STAINED_GLASS), Efeitos.b(Material.SHROOMLIGHT), 0.6f, 3.4f, 6, corFogo);
                    plugin.fx.anel(alvo.clone().add(0, 0.1, 0), Efeitos.b(Material.MAGMA_BLOCK), 3.0, 6, 12, 0.35f, corFogo);
                    w.spawnParticle(Particle.EXPLOSION, alvo.clone().add(0, 0.5, 0), 3, 0.8, 0.4, 0.8, 0);
                    w.spawnParticle(Particle.FLAME, alvo.clone().add(0, 0.3, 0), 30, 1.2, 0.3, 1.2, 0.08);
                    w.spawnParticle(Particle.LAVA, alvo, 8, 1.0, 0.2, 1.0, 0);
                    w.playSound(alvo, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                    if (!p.isOnline()) return;
                    for (LivingEntity le : inimigosPerto(alvo, 3.0, 3.0, p)) {
                        if (plugin.causarDano(le, DANO_METEORO * f, p)) {
                            le.setFireTicks(Math.max(le.getFireTicks(), 60));
                            le.setVelocity(le.getVelocity().add(new Vector(0, 0.4, 0)));
                        }
                    }
                }, 1L, 1L);
            }, i * 10L);
        }
        return true;
    }

    // ------------------------------------------------------------------ SAIYAJIN

    /** Rajada de Ki: varias esferas seguidas que acompanham a mira (mais esferas transformado). */
    boolean kiBlast(Player p) {
        double f = plugin.fator(p);
        World w = p.getWorld();
        boolean ssj = ssjAtivo(p);
        int n = ssj ? KI_RAJADA_SSJ : KI_RAJADA;
        Particle.DustOptions amarelo = ssj ? cor(255, 200, 30, 2.2f) : cor(255, 225, 80, 2.0f);
        Particle.DustOptions branco = cor(255, 255, 225, 1.2f);
        for (int i = 0; i < n; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.isDead() || !p.getWorld().equals(w)) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Vector dir = p.getEyeLocation().getDirection().normalize();
                if (k > 0) dir.add(new Vector(r.nextDouble(-0.05, 0.05), r.nextDouble(-0.03, 0.03), r.nextDouble(-0.05, 0.05))).normalize();
                Vector lado = dir.clone().crossProduct(new Vector(0, 1, 0));
                if (lado.lengthSquared() < 0.01) lado = new Vector(1, 0, 0);
                lado.normalize().multiply(k % 2 == 0 ? 0.35 : -0.35);
                Location origem = p.getEyeLocation().subtract(0, 0.35, 0).add(dir.clone().multiply(0.8)).add(lado);
                w.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.6f + k * 0.08f);
                w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.4f, 1.8f + k * 0.05f);
                if (k % 2 == 0) p.swingMainHand();
                else p.swingOffHand();
                final Vector d = dir;
                org.bukkit.Color corKi = ssj ? org.bukkit.Color.fromRGB(255, 200, 30) : org.bukkit.Color.fromRGB(255, 225, 80);
                org.bukkit.entity.BlockDisplay esfera = plugin.vis.bloco(origem, Material.YELLOW_STAINED_GLASS.createBlockData(), 40, corKi);
                org.bukkit.entity.BlockDisplay luz = plugin.vis.bloco(origem, Material.WHITE_STAINED_GLASS.createBlockData(), 40, null);
                Visuais.escala(esfera, 0.42f, 0f, 0);
                Visuais.escala(luz, 0.22f, 0f, 0);
                org.bukkit.entity.FallingBlock kiCel = plugin.cena.bloco(origem.clone().subtract(0, 0.5, 0), Material.YELLOW_STAINED_GLASS.createBlockData(), null, false, 40);
                java.util.List<Player> plateia3d = plugin.props.plateia(origem);
                org.bukkit.entity.ItemDisplay ki3d = plugin.props.prop(origem, "ki_orbe_f0", 40, plateia3d);
                if (ki3d != null) {
                    plugin.props.esconder(plateia3d, esfera, luz);
                    Props3D.pose(ki3d, d, ssj ? 1.5f : 1.15f, 0f, 0);
                }
                final int[] giro = {0};
                projetil(p, origem, d, 28, 1.6, 0.7,
                        pos -> {
                            giro[0]++;
                            if (esfera.isValid()) {
                                esfera.teleport(pos);
                                Visuais.escala(esfera, 0.42f, giro[0] * 0.7f, 1);
                            }
                            if (luz.isValid()) luz.teleport(pos);
                            if (ki3d != null && ki3d.isValid()) {
                                Props3D.mover(ki3d, pos);
                                Props3D.pose(ki3d, d, ssj ? 1.5f : 1.15f, giro[0] * 0.8f, 1);
                                if (giro[0] % 3 == 0) Props3D.quadro(ki3d, "ki_orbe_f" + ((giro[0] / 3) % 2));
                            }
                            Cenas.levar(kiCel, pos.clone().subtract(0, 0.5, 0));
                            plugin.cena.kiRastro(pos, ssj);
                            w.spawnParticle(Particle.DUST, pos, 3, 0.1, 0.1, 0.1, 0, amarelo);
                            w.spawnParticle(Particle.DUST, pos, 1, 0.04, 0.04, 0.04, 0, branco);
                            w.spawnParticle(Particle.END_ROD, pos, 1, 0.04, 0.04, 0.04, 0.0);
                        },
                        (alvo, pos) -> {
                            plugin.vis.remover(esfera);
                            plugin.vis.remover(luz);
                            plugin.vis.remover(kiCel);
                            plugin.props.remover(ki3d);
                            plugin.fx.pulso(pos, Efeitos.b(Material.YELLOW_STAINED_GLASS), Efeitos.b(Material.WHITE_STAINED_GLASS), 0.3f, 1.7f, 4, corKi);
                            plugin.cena.kiImpacto(pos, ssj, k == n - 1);
                            estouroKi(w, pos);
                            if (plugin.causarDano(alvo, DANO_KI * f, p)) {
                                Vector v = d.clone().multiply(0.25);
                                v.setY(0.12);
                                alvo.setVelocity(alvo.getVelocity().add(v));
                            }
                        },
                        pos -> {
                            plugin.vis.remover(esfera);
                            plugin.vis.remover(luz);
                            plugin.vis.remover(kiCel);
                            plugin.props.remover(ki3d);
                            plugin.fx.pulso(pos, Efeitos.b(Material.YELLOW_STAINED_GLASS), Efeitos.b(Material.WHITE_STAINED_GLASS), 0.3f, 1.4f, 4, corKi);
                            plugin.cena.kiImpacto(pos, ssj, k == n - 1);
                            estouroKi(w, pos);
                        });
            }, i * 3L);
        }
        return true;
    }

    private static void estouroKi(World w, Location pos) {
        w.spawnParticle(Particle.DUST, pos, 20, 0.4, 0.4, 0.4, 0, cor(255, 230, 90, 1.8f));
        w.spawnParticle(Particle.EXPLOSION, pos, 1, 0, 0, 0, 0);
        w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
    }

    boolean teleInstantaneo(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        RayTraceResult r = w.rayTraceEntities(olho, dir, 30.0, 1.0, e -> e instanceof LivingEntity vivo && !vivo.isDead()
                && !(e instanceof ArmorStand) && !plugin.ehAliado(e, p) && !plugin.pets.ehPet(e));
        LivingEntity alvo = r != null && r.getHitEntity() instanceof LivingEntity le && p.hasLineOfSight(le) ? le : null;
        if (alvo == null) {
            boolean ok = plugin.teleporte(p);
            if (ok) Poderes.barra(p, "§fTeletransporte Instantaneo §7(sem alvo: foi para onde voce olhava)");
            return ok;
        }
        Location destino = atras(alvo);
        if (destino == null) {
            Poderes.barra(p, "§7Sem espaco nas costas desse alvo.");
            return false;
        }
        Location origem = p.getLocation();
        efeitoTeleInstantaneo(w, origem);
        if (!p.teleport(destino, PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
            Poderes.barra(p, "§cNao da para teleportar para la (area protegida).");
            return false;
        }
        plugin.semQueda(p, 3000L);
        efeitoTeleInstantaneo(w, destino);
        Poderes.barra(p, "§f§lTeletransporte Instantaneo!");
        return true;
    }

    private void efeitoTeleInstantaneo(World w, Location l) {
        Location c = l.clone().add(0, 1, 0);
        org.bukkit.Color branco = org.bukkit.Color.fromRGB(255, 255, 235);
        plugin.fx.colunaDeLuz(l, Efeitos.b(Material.WHITE_STAINED_GLASS), 3.0, 0.9, 7, branco);
        plugin.cena.teleKi(l);
        plugin.fx.anel(l.clone().add(0, 0.1, 0), Efeitos.b(Material.WHITE_STAINED_GLASS), 1.7, 5, 10, 0.22f, branco);
        w.spawnParticle(Particle.DUST, c, 25, 0.3, 0.7, 0.3, 0, cor(255, 255, 255, 1.4f));
        w.spawnParticle(Particle.END_ROD, c, 12, 0.3, 0.6, 0.3, 0.05);
        w.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 2.0f);
        w.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
    }

    boolean ssjAtivo(Player p) {
        Long ate = saiyajinAte.get(p.getUniqueId());
        return ate != null && ate > System.currentTimeMillis();
    }

    /** Multiplicador de dano (Super Saiyajin deixa os poderes 30% mais fortes). */
    double multiplicador(Player p) {
        return ssjAtivo(p) ? SAIYAJIN_MULT : 1.0;
    }

    boolean superSaiyajin(Player p) {
        if (ssjAtivo(p)) {
            Poderes.barra(p, "§eVoce ja esta transformado!");
            return false;
        }
        UUID id = p.getUniqueId();
        World w = p.getWorld();
        Location l = p.getLocation();
        final int duracao = 15;
        saiyajinAte.put(id, System.currentTimeMillis() + duracao * 1000L);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duracao * 20, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duracao * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duracao * 20, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duracao * 20, 1));
        Particle.DustOptions ouro = cor(255, 210, 40, 1.5f);
        Particle.DustOptions claro = cor(255, 245, 150, 1.0f);
        w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.5f);
        w.playSound(l, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 0.8f);
        w.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40;
            w.spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * 1.6, 0.2 + i * 0.05, Math.sin(a) * 1.6), 2, 0.05, 0.05, 0.05, 0, ouro);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, l.clone().add(0, 1, 0), 40, 0.6, 1.0, 0.6, 0.2);
        // raio que nao queima nem machuca, coluna dourada e onda de choque no chao
        w.strikeLightningEffect(l);
        for (int i = 0; i < 30; i++) w.spawnParticle(Particle.DUST, l.clone().add(0, i * 0.15, 0), 3, 0.25, 0.05, 0.25, 0, claro);
        for (int anel = 1; anel <= 4; anel++) {
            final double raio = anel * 1.2;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 36; i++) {
                    double a = Math.PI * 2 * i / 36;
                    w.spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * raio, 0.15, Math.sin(a) * raio), 1, 0, 0, 0, 0, ouro);
                }
                w.spawnParticle(Particle.CLOUD, l.clone().add(0, 0.2, 0), 6, raio * 0.4, 0.05, raio * 0.4, 0.02);
            }, anel * 2L);
        }
        // explosao de ki: esfera dourada que cresce, chao rachando e espiral de energia subindo
        org.bukkit.entity.BlockDisplay esfera = plugin.vis.bloco(l.clone().add(0, 1, 0), Material.YELLOW_STAINED_GLASS.createBlockData(), 16, Color.fromRGB(255, 220, 60));
        Bukkit.getScheduler().runTaskLater(plugin, () -> Visuais.escala(esfera, 4.5f, 1.2f, 10), 1L);
        try {
            BlockData chao = l.clone().subtract(0, 0.5, 0).getBlock().getBlockData();
            if (!chao.getMaterial().isAir()) w.spawnParticle(Particle.BLOCK, l.clone().add(0, 0.1, 0), 80, 1.6, 0.1, 1.6, 0.3, chao);
        } catch (Throwable ignored) {
        }
        for (int k = 0; k < 10; k++) {
            final int kk = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int j = 0; j < 3; j++) {
                    double a = kk * 0.7 + j * Math.PI * 2 / 3;
                    w.spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * 1.1, kk * 0.3, Math.sin(a) * 1.1), 2, 0.03, 0.03, 0.03, 0, ouro);
                    w.spawnParticle(Particle.END_ROD, l.clone().add(Math.cos(a) * 1.1, kk * 0.3, Math.sin(a) * 1.1), 1, 0, 0, 0, 0.01);
                }
            }, k);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            w.strikeLightningEffect(l.clone().add(1.5, 0, 0));
            w.strikeLightningEffect(l.clone().add(-1.5, 0, 0.8));
        }, 6L);
        plugin.cena.ssjTransformar(p, l);
        mostrarVisual(p, true);
        p.showTitle(net.kyori.adventure.title.Title.title(Poderes.txt("§6§lSUPER SAIYAJIN"), Poderes.txt("§eSeus poderes estao 30% mais fortes")));
        BukkitTask antiga = auraSaiyajin.remove(id);
        if (antiga != null) antiga.cancel();
        // prop 3D: casca de fogo dourado em volta do jogador (quem tem o pacote)
        final org.bukkit.entity.ItemDisplay aura3d = plugin.props.prop(l.clone().add(0, 1.0, 0), "aura_ssj_f0", duracao * 20 + 20, plugin.props.plateia(l));
        if (aura3d != null) {
            aura3d.setTeleportDuration(1);
            Props3D.escala(aura3d, 1.0f, 0f, 0);
        }
        final int[] t = {0};
        BukkitTask aura = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            t[0] += 3;
            if (!p.isOnline() || p.isDead() || !ssjAtivo(p)) {
                plugin.props.remover(aura3d);
                terminarSaiyajin(p, p.isOnline() && !p.isDead());
                return;
            }
            Location c = p.getLocation().add(0, 1.0, 0);
            if (aura3d != null && aura3d.isValid()) {
                Props3D.mover(aura3d, c);
                Props3D.escala(aura3d, 1.0f + 0.04f * (float) Math.sin(t[0] * 0.5), t[0] * 0.15f, 3);
                Props3D.quadro(aura3d, "aura_ssj_f" + ((t[0] / 3) % 3));
            }
            cabeloDeKi(p);
            plugin.cena.ssjAura(p, t[0]);
            if (t[0] % 12 == 0) mostrarVisual(p, true);
            if (t[0] % 30 == 0) w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.8f);
            w.spawnParticle(Particle.DUST, c, 6, 0.35, 0.8, 0.35, 0, ouro);
            w.spawnParticle(Particle.DUST, c.clone().add(0, 0.6, 0), 2, 0.2, 0.3, 0.2, 0, claro);
            if (t[0] % 9 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, c, 3, 0.4, 0.7, 0.4, 0.1);
            if (t[0] % 21 == 0) {
                long resta = (saiyajinAte.getOrDefault(id, 0L) - System.currentTimeMillis() + 999) / 1000;
                Poderes.barra(p, "§6§lSUPER SAIYAJIN §e" + resta + "s");
            }
        }, 3L, 3L);
        auraSaiyajin.put(id, aura);
        return true;
    }

    private void terminarSaiyajin(Player p, boolean avisar) {
        UUID id = p.getUniqueId();
        boolean estava = saiyajinAte.remove(id) != null;
        BukkitTask t = auraSaiyajin.remove(id);
        if (t != null) t.cancel();
        if (estava && p.isOnline()) {
            mostrarVisual(p, false);
            Location c = p.getLocation().add(0, 1, 0);
            p.getWorld().spawnParticle(Particle.DUST, c, 30, 0.4, 0.8, 0.4, 0, cor(255, 230, 120, 1.2f));
            p.getWorld().spawnParticle(Particle.CLOUD, c, 10, 0.3, 0.6, 0.3, 0.02);
            p.getWorld().playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        }
        if (avisar) Poderes.barra(p, "§7A transformacao acabou. Voce voltou ao normal.");
    }

    // ------------------------------------------------------------------ visual do Super Saiyajin
    // So aparencia (pacotes para quem esta vendo): a armadura de verdade continua no inventario,
    // entao nada some, nada duplica e nada vai parar dentro dos minigames.

    private Map<EquipmentSlot, ItemStack> roupaSsj;

    private static ItemStack pecaSsj(Material m, Color tinta, boolean brilho) {
        ItemStack it = new ItemStack(m);
        var meta = it.getItemMeta();
        if (meta instanceof LeatherArmorMeta lm && tinta != null) lm.setColor(tinta);
        if (meta instanceof ArmorMeta am) {
            try {
                am.setTrim(new ArmorTrim(TrimMaterial.GOLD, TrimPattern.BOLT));
            } catch (Throwable ignored) {
            }
        }
        if (brilho) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    /** Cabeca do Goku Super Saiyajin (skin publica "SuperSayain_Goku" da Mojang). Null enquanto nao carregou. */
    private volatile ItemStack cabecaGoku;
    static final java.util.UUID GOKU_ID = java.util.UUID.fromString("5c3a9f2e-6b1d-4e8a-9f0c-55aa17c0ffee");
    static final String GOKU_NOME = "GokuSSJ";
    /** Cabeca do Goku Super Saiyajin (cabelo dourado e olhos verdes), textura conferida no textures.minecraft.net. */
    static final String GOKU_SSJ_TEXTURA = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmZhNDJiMTQxOWU2NmIzM2UzNjMzODE4ZTc5NWE0ZWVmZmIxODA1NTQ0OThlNGEyYzdhNmRkMjQxNWM0NDQifX19";

    /** Pega a textura da cabeca (do config ou da Mojang, sem travar o servidor) e guarda no config. */
    void carregarCabecaGoku() {
        String tex = plugin.getConfig().getString("saiyajin.textura-ssj", "");
        montarCabeca(tex.isBlank() ? GOKU_SSJ_TEXTURA : tex);
    }

    private void montarCabeca(String textura) {
        try {
            com.destroystokyo.paper.profile.PlayerProfile pr = Bukkit.createProfile(GOKU_ID, GOKU_NOME);
            pr.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", textura));
            ItemStack h = new ItemStack(Material.PLAYER_HEAD);
            if (h.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
                sm.setPlayerProfile(pr);
                h.setItemMeta(sm);
            }
            cabecaGoku = h;
            roupaSsj = null;
        } catch (Throwable t) {
            plugin.getLogger().warning("Cabeca do Goku com erro: " + t);
        }
    }

    private Map<EquipmentSlot, ItemStack> roupaSsj() {
        if (roupaSsj == null) {
            roupaSsj = new EnumMap<>(EquipmentSlot.class);
            // cabelo dourado do Goku + roupa de treino laranja com faixa e botas azuis
            roupaSsj.put(EquipmentSlot.HEAD, cabecaGoku != null ? cabecaGoku.clone() : pecaSsj(Material.GOLDEN_HELMET, null, true));
            roupaSsj.put(EquipmentSlot.CHEST, pecaSsj(Material.LEATHER_CHESTPLATE, Color.fromRGB(240, 115, 20), false));
            roupaSsj.put(EquipmentSlot.LEGS, pecaSsj(Material.LEATHER_LEGGINGS, Color.fromRGB(240, 115, 20), false));
            roupaSsj.put(EquipmentSlot.FEET, pecaSsj(Material.LEATHER_BOOTS, Color.fromRGB(30, 60, 190), false));
        }
        return roupaSsj;
    }

    private static ItemStack ouAr(ItemStack it) {
        return it == null ? new ItemStack(Material.AIR) : it;
    }

    /** Mostra (ou desfaz) a armadura de ki dourada para todo mundo que esta perto, inclusive o proprio jogador. */
    void mostrarVisual(Player p, boolean transformado) {
        Map<EquipmentSlot, ItemStack> itens = new EnumMap<>(EquipmentSlot.class);
        if (transformado) itens.putAll(roupaSsj());
        else {
            EntityEquipment eq = p.getEquipment();
            itens.put(EquipmentSlot.HEAD, ouAr(eq.getHelmet()));
            itens.put(EquipmentSlot.CHEST, ouAr(eq.getChestplate()));
            itens.put(EquipmentSlot.LEGS, ouAr(eq.getLeggings()));
            itens.put(EquipmentSlot.FEET, ouAr(eq.getBoots()));
        }
        Location l = p.getLocation();
        for (Player o : p.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(l) > 96 * 96) continue;
            try {
                o.sendEquipmentChange(p, itens);
            } catch (Throwable ignored) {
            }
        }
        if (!transformado) p.updateInventory();
    }

    /** "Cabelo" espetado de energia dourada saindo da cabeca, virado para tras. */
    private void cabeloDeKi(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector frente = olho.getDirection().setY(0);
        if (frente.lengthSquared() < 0.01) frente = new Vector(0, 0, 1);
        frente.normalize();
        Vector direita = frente.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location base = olho.clone().add(0, 0.32, 0).subtract(frente.clone().multiply(0.05));
        double[][] pontas = {{0, 1, -0.55}, {0.45, 0.85, -0.45}, {-0.45, 0.85, -0.45}, {0.22, 1, -0.05}, {-0.22, 1, -0.05}, {0, 0.75, -0.95}};
        for (double[] pt : pontas) {
            Vector d = direita.clone().multiply(pt[0]).add(new Vector(0, pt[1], 0)).add(frente.clone().multiply(pt[2])).normalize();
            for (int i = 1; i <= 4; i++) {
                double dist = i * 0.13;
                float tam = 1.4f - i * 0.22f;
                int g = 200 + i * 12;
                w.spawnParticle(Particle.DUST, base.clone().add(d.clone().multiply(dist)), 1, 0, 0, 0, 0, cor(255, Math.min(255, g), 40 + i * 30, tam));
            }
        }
    }

    // ------------------------------------------------------------------ GUERREIRO

    boolean grito(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        double f = plugin.fator(p);
        w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.1f);
        w.playSound(l, Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1.2f);
        org.bukkit.Color vermelho = org.bukkit.Color.fromRGB(255, 80, 60);
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.fx.anel(l.clone().add(0, 0.4 + kk * 0.5, 0), Efeitos.b(Material.WHITE_STAINED_GLASS), 6.2, 8, 22, 0.28f, kk == 1 ? vermelho : org.bukkit.Color.WHITE), 1L + k * 3L);
        }
        plugin.fx.texto(p.getEyeLocation().add(0, 0.7, 0), "§c§lAAARGH!", 1.8f, 24);
        plugin.cena.grito(p, l);
        plugin.fx.estilhacos(l.clone().add(0, 0.2, 0), new BlockData[] {Poderes.chaoDe(l)}, 8, 2.5, 14, 0.3f);
        for (int anel = 1; anel <= 6; anel++) {
            final int raio = anel;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int pontos = raio * 10;
                for (int i = 0; i < pontos; i++) {
                    double a = Math.PI * 2 * i / pontos;
                    Location pt = l.clone().add(Math.cos(a) * raio, 0.3, Math.sin(a) * raio);
                    w.spawnParticle(Particle.CLOUD, pt, 1, 0, 0.05, 0, 0.0);
                    if (i % 4 == 0) w.spawnParticle(Particle.CRIT, pt.add(0, 0.4, 0), 1, 0, 0, 0, 0.0);
                }
            }, anel);
        }
        int n = 0;
        for (LivingEntity le : inimigosPerto(l, 6.0, 3.0, p)) {
            if (!plugin.causarDano(le, DANO_GRITO * f, p)) continue;
            n++;
            Vector v = le.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(rnd().nextDouble(-1, 1), 0, rnd().nextDouble(-1, 1));
            v.normalize().multiply(1.3);
            v.setY(0.5);
            le.setVelocity(v);
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 0));
        Poderes.barra(p, "§c§lGRITO DE GUERRA! §7" + (n == 0 ? "Voce esta mais resistente." : n + " inimigo(s) atingido(s)"));
        return true;
    }

    boolean terremoto(Player p) {
        UUID id = p.getUniqueId();
        if (!emSequencia.add(id)) return false;
        double f = plugin.fator(p);
        plugin.semQueda(p, 5000L);
        p.setVelocity(new Vector(0, 0.95, 0));
        plugin.cena.terremotoSalto(p);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1f, 0.6f);
        Poderes.barra(p, "§6§lTERREMOTO!");
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            t[0]++;
            if (!p.isOnline() || p.isDead()) {
                emSequencia.remove(id);
                tarefa.cancel();
                return;
            }
            if (t[0] == 9) p.setVelocity(new Vector(0, -1.6, 0));
            boolean noChao = !p.getLocation().subtract(0, 0.15, 0).getBlock().isPassable();
            if (!((t[0] > 9 && noChao) || t[0] >= 30)) return;
            tarefa.cancel();
            emSequencia.remove(id);
            esmagar(p, f);
        }, 1L, 1L);
        return true;
    }

    private void esmagar(Player p, double f) {
        World w = p.getWorld();
        Location c = p.getLocation();
        Block chao = c.clone().subtract(0, 0.15, 0).getBlock();
        for (int i = 0; i < 6 && chao.isPassable(); i++) chao = chao.getRelative(BlockFace.DOWN);
        BlockData dado = chao.isPassable() ? Material.DIRT.createBlockData() : chao.getBlockData();
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        w.playSound(c, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        w.spawnParticle(Particle.EXPLOSION, c, 2, 0.3, 0.1, 0.3, 0);
        final BlockData terra = dado;
        plugin.cena.terremoto(c, terra);
        plugin.fx.estilhacos(c.clone().add(0, 0.3, 0), new BlockData[] {terra, terra, Efeitos.b(Material.COBBLESTONE), Efeitos.b(Material.GRAVEL)}, 16, 5.0, 18, 0.45f);
        plugin.fx.pulso(c.clone().add(0, 0.5, 0), Efeitos.b(Material.WHITE_STAINED_GLASS), null, 0.5f, 3.0f, 4, org.bukkit.Color.fromRGB(255, 220, 160));
        for (int onda = 0; onda < 3; onda++) {
            final double raioOnda = 2.0 + onda * 2.2;
            final int pecas = 10 + onda * 6;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < pecas; i++) {
                    double a = Math.PI * 2 * i / pecas + rnd().nextDouble(-0.1, 0.1);
                    Location base = c.clone().add(Math.cos(a) * raioOnda, -0.4, Math.sin(a) * raioOnda);
                    plugin.fx.pilar(base, terra, rnd().nextDouble(0.8, 1.3), rnd().nextDouble(0.7, 1.0), rnd().nextDouble(0.15, 0.45), 3, 5, null);
                }
            }, 1L + onda * 3L);
        }
        for (int anel = 1; anel <= 7; anel++) {
            final int raio = anel;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int pontos = raio * 8;
                for (int i = 0; i < pontos; i++) {
                    double a = Math.PI * 2 * i / pontos;
                    Location pt = c.clone().add(Math.cos(a) * raio, 0.2, Math.sin(a) * raio);
                    w.spawnParticle(Particle.BLOCK, pt, 4, 0.2, 0.3, 0.2, 0, dado);
                }
                w.playSound(c, Sound.BLOCK_GRAVEL_BREAK, 0.6f, 0.6f);
            }, anel * 2L);
        }
        for (LivingEntity le : inimigosPerto(c, 7.0, 3.0, p)) {
            if (!plugin.causarDano(le, DANO_TERREMOTO * f, p)) continue;
            Vector v = le.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (v.lengthSquared() > 0.01) v.normalize().multiply(0.35);
            v.setY(0.9);
            le.setVelocity(v);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
    }

    // ------------------------------------------------------------------ NINJA

    boolean shuriken(Player p) {
        double f = plugin.fator(p);
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location origem = p.getEyeLocation().subtract(0, 0.2, 0).add(dir.clone().multiply(0.6));
        Particle.DustOptions aco = cor(190, 190, 200, 1.0f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.8f);
        w.playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 1.6f);
        p.swingMainHand();
        for (double ang : new double[] {-0.17, 0.0, 0.17}) {
            Vector d = dir.clone().rotateAroundY(ang);
            org.bukkit.entity.ItemDisplay estrelaPc = plugin.vis.item(origem, new org.bukkit.inventory.ItemStack(Material.NETHER_STAR), 30, null);
            org.bukkit.entity.Item estrela = plugin.cena.itensNoCelular ? null
                    : plugin.cena.item(origem, new org.bukkit.inventory.ItemStack(Material.NETHER_STAR), d.clone().multiply(1.5), false, 30);
            java.util.List<Player> plateia3d = plugin.props.plateia(origem);
            org.bukkit.entity.ItemDisplay estrela3d = plugin.props.prop(origem, "shuriken_f0", 30, plateia3d);
            if (estrela3d != null) {
                plugin.props.esconder(plateia3d, estrelaPc);
                estrela3d.setTeleportDuration(1);
            }
            final int[] giro = {0};
            projetil(p, origem.clone(), d, 22, 1.5, 0.6,
                    pos -> {
                        giro[0]++;
                        if (estrela3d != null && estrela3d.isValid()) {
                            Props3D.mover(estrela3d, pos);
                            Props3D.pose(estrela3d, new Vector(0, 1, 0), 0.9f, giro[0] * 1.3f, 1);   // deitada, girando
                        }
                        if (estrelaPc.isValid()) {
                            estrelaPc.teleport(pos);
                            estrelaPc.setInterpolationDelay(0);
                            estrelaPc.setInterpolationDuration(1);
                            estrelaPc.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                                    new org.joml.Quaternionf().rotationY(giro[0] * 1.3f).rotateX((float) (Math.PI / 2)),
                                    new org.joml.Vector3f(0.55f), new org.joml.Quaternionf()));
                        }
                        Cenas.levar(estrela, pos.clone().subtract(0, 0.25, 0));
                        w.spawnParticle(Particle.DUST, pos, 2, 0.08, 0.02, 0.08, 0, aco);
                        w.spawnParticle(Particle.CRIT, pos, 1, 0, 0, 0, 0.0);
                    },
                    (alvo, pos) -> {
                        plugin.vis.remover(estrela);
                        plugin.vis.remover(estrelaPc);
                        plugin.props.remover(estrela3d);
                        plugin.fx.cortesX(pos, d, 1.1, org.bukkit.Color.fromRGB(220, 220, 235));
                        plugin.cena.corteX(pos, d);
                        w.spawnParticle(Particle.CRIT, pos, 10, 0.2, 0.2, 0.2, 0.2);
                        w.playSound(pos, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.4f);
                        plugin.causarDano(alvo, DANO_SHURIKEN * f, p);
                    },
                    pos -> {
                        plugin.vis.remover(estrela);
                        plugin.vis.remover(estrelaPc);
                        plugin.props.remover(estrela3d);
                        w.spawnParticle(Particle.CRIT, pos, 4, 0.1, 0.1, 0.1, 0.05);
                    });
        }
        return true;
    }

    boolean fumaca(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        UUID id = p.getUniqueId();
        w.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 2.0f);
        plugin.fx.pulso(l.clone().add(0, 1, 0), Efeitos.b(Material.GRAY_STAINED_GLASS), Efeitos.b(Material.BLACK_STAINED_GLASS), 0.6f, 4.2f, 9, null);
        plugin.cena.fumaca(l);
        plugin.fx.bolhas(l, Efeitos.b(Material.GRAY_WOOL), 12, 2.4, 2.6, 22, null);
        plugin.fx.bolhas(l, Efeitos.b(Material.LIGHT_GRAY_WOOL), 8, 2.0, 2.0, 18, null);
        plugin.fx.estilhacos(l.clone().add(0, 0.4, 0), new BlockData[] {Efeitos.b(Material.GRAY_WOOL), Efeitos.b(Material.BLACK_WOOL)}, 8, 3.0, 16, 0.5f);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (t[0] > 40) {
                tarefa.cancel();
                return;
            }
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l.clone().add(0, 0.8, 0), t[0] == 0 ? 40 : 8, 1.6, 0.8, 1.6, 0.02);
            w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), t[0] == 0 ? 60 : 10, 1.8, 1.0, 1.8, 0.03);
            t[0] += 5;
        }, 0L, 5L);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
        fumacaAte.put(id, System.currentTimeMillis() + 5000L);
        for (LivingEntity le : inimigosPerto(l, 5.0, 3.0, p)) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
        }
        for (Entity e : p.getNearbyEntities(24, 12, 24)) {
            if (e instanceof Mob m && p.equals(m.getTarget())) m.setTarget(null);
        }
        Poderes.barra(p, "§8§lBOMBA DE FUMACA! §7Invisivel por 5s");
        return true;
    }

    /** Durante a fumaca os mobs nao conseguem mirar no ninja. */
    @EventHandler(ignoreCancelled = true)
    public void naoVeNinja(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        Long ate = fumacaAte.get(p.getUniqueId());
        if (ate == null) return;
        if (ate < System.currentTimeMillis()) {
            fumacaAte.remove(p.getUniqueId());
            return;
        }
        e.setCancelled(true);
    }

    boolean laminas(Player p) {
        UUID id = p.getUniqueId();
        if (!emSequencia.add(id)) return false;
        double f = plugin.fator(p);
        World w = p.getWorld();
        Poderes.barra(p, "§f§lLAMINAS GIRATORIAS!");
        plugin.fx.orbitar(p, new org.bukkit.inventory.ItemStack(Material.NETHERITE_SWORD), 3, 2.0, 1.0, 0.7, 1.1f, 60, org.bukkit.Color.fromRGB(200, 200, 215));
        // props 3D: 4 arcos de lamina girando em volta (quem tem o pacote)
        java.util.List<Player> plateia3d = plugin.props.plateia(p.getLocation());
        java.util.List<org.bukkit.entity.ItemDisplay> arcos = new java.util.ArrayList<>();
        for (int i = 0; i < 4 && !plateia3d.isEmpty(); i++) {
            org.bukkit.entity.ItemDisplay a = plugin.props.prop(p.getLocation().add(0, 1.0, 0), "lamina_arco_f" + (i % 2), 64, plateia3d);
            if (a != null) {
                a.setTeleportDuration(1);
                arcos.add(a);
            }
        }
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (t[0] > 60 || !p.isOnline() || p.isDead()) {
                emSequencia.remove(id);
                for (org.bukkit.entity.ItemDisplay a : arcos) plugin.props.remover(a);
                tarefa.cancel();
                return;
            }
            Location c = p.getLocation().add(0, 1.0, 0);
            double base = t[0] * 0.35;
            for (int i = 0; i < arcos.size(); i++) {
                double a = base + Math.PI / 2 * i;
                Vector dir = new Vector(Math.cos(a), 0, Math.sin(a));   // o arco (modelo em z=+10) fica a 2 blocos, tangente ao giro
                Props3D.mover(arcos.get(i), c.clone().add(0, (i % 2 == 0 ? 0.1 : -0.3), 0));
                Props3D.pose(arcos.get(i), dir, 3.2f, 1.0f, 3.2f, 0f, 1);
                if (t[0] % 4 == 0) Props3D.quadro(arcos.get(i), "lamina_arco_f" + ((t[0] / 4 + i) % 2));
            }
            for (int i = 0; i < 4; i++) {
                double a = base + Math.PI / 2 * i;
                Location pt = c.clone().add(Math.cos(a) * 2.0, 0, Math.sin(a) * 2.0);
                w.spawnParticle(Particle.SWEEP_ATTACK, pt, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.CRIT, pt, 2, 0.1, 0.1, 0.1, 0.05);
            }
            if (t[0] % 10 == 0) {
                p.swingMainHand();
                w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.3f);
                for (LivingEntity le : inimigosPerto(c, 3.5, 2.0, p)) {
                    if (plugin.causarDano(le, DANO_LAMINAS * f, p)) {
                        Vector v = le.getLocation().toVector().subtract(c.toVector()).setY(0);
                        if (v.lengthSquared() > 0.01) le.setVelocity(le.getVelocity().add(v.normalize().multiply(0.25)));
                    }
                }
            }
            t[0] += 2;
        }, 0L, 2L);
        return true;
    }

    boolean relampago(Player p) {
        UUID id = p.getUniqueId();
        if (emSequencia.contains(id)) return false;
        List<LivingEntity> alvos = inimigosPerto(p.getLocation(), 12.0, 5.0, p);
        alvos.removeIf(le -> !p.hasLineOfSight(le));
        if (alvos.isEmpty()) {
            Poderes.barra(p, "§7Nenhum inimigo perto (ate 12 blocos).");
            return false;
        }
        Location ref = p.getLocation();
        alvos.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(ref)));
        if (alvos.size() > 5) alvos = new ArrayList<>(alvos.subList(0, 5));
        final List<LivingEntity> fila = alvos;
        emSequencia.add(id);
        double f = plugin.fator(p);
        World w = p.getWorld();
        Location origem = p.getLocation().clone();
        plugin.semQueda(p, 6000L);
        Particle.DustOptions roxo = cor(190, 70, 255, 1.3f);
        Poderes.barra(p, "§d§lATAQUE RELAMPAGO!");
        w.playSound(origem, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.4f);
        final int[] i = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (!p.isOnline() || p.isDead()) {
                emSequencia.remove(id);
                tarefa.cancel();
                return;
            }
            if (i[0] >= fila.size()) {
                Torretas.linha(p.getLocation().add(0, 1, 0), origem.clone().add(0, 1, 0), 0.5,
                        pt -> w.spawnParticle(Particle.DUST, pt, 1, 0.05, 0.05, 0.05, 0, roxo));
                if (p.getWorld().equals(origem.getWorld()))
                    plugin.fx.corte(p.getLocation().add(0, 1, 0), origem.clone().add(0, 1, 0), Efeitos.b(Material.PURPLE_STAINED_GLASS), 0.1, 8, org.bukkit.Color.fromRGB(190, 70, 255));
                if (origem.getWorld().equals(p.getWorld())) p.teleport(origem, PlayerTeleportEvent.TeleportCause.PLUGIN);
                w.playSound(origem, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
                emSequencia.remove(id);
                tarefa.cancel();
                return;
            }
            LivingEntity a = fila.get(i[0]++);
            if (!a.isValid() || a.isDead() || !a.getWorld().equals(p.getWorld())) return;
            Location d = atras(a);
            if (d == null) d = a.getLocation();
            Torretas.linha(p.getLocation().add(0, 1, 0), d.clone().add(0, 1, 0), 0.5,
                    pt -> w.spawnParticle(Particle.DUST, pt, 1, 0.05, 0.05, 0.05, 0, roxo));
            Location saida = p.getLocation().add(0, 1, 0);
            if (!p.teleport(d, PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) return;
            plugin.fx.corte(saida, d.clone().add(0, 1, 0), Efeitos.b(Material.WHITE_STAINED_GLASS), 0.1, 8, org.bukkit.Color.fromRGB(190, 70, 255));
            plugin.cena.relampagoCorte(saida, d.clone().add(0, 1, 0));
            plugin.cena.corteX(a.getLocation().add(0, a.getHeight() * 0.6, 0), d.toVector().subtract(saida.toVector()));
            plugin.fx.cortesX(a.getLocation().add(0, a.getHeight() * 0.6, 0), d.toVector().subtract(saida.toVector()), 1.8, org.bukkit.Color.fromRGB(230, 200, 255));
            p.swingMainHand();
            Location meio = a.getLocation().add(0, a.getHeight() * 0.6, 0);
            w.spawnParticle(Particle.SWEEP_ATTACK, meio, 2, 0.3, 0.2, 0.3, 0);
            w.spawnParticle(Particle.CRIT, meio, 12, 0.3, 0.4, 0.3, 0.2);
            w.playSound(meio, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.3f);
            plugin.causarDano(a, DANO_RELAMPAGO * f, p);
        }, 2L, 5L);
        return true;
    }

    // ------------------------------------------------------------------ DRUIDA

    boolean raizes(Player p) {
        Location c = mira(p, 20);
        double f = plugin.fator(p);
        World w = c.getWorld();
        BlockData raiz = Material.MANGROVE_ROOTS.createBlockData();
        Particle.DustOptions marrom = cor(110, 75, 40, 1.4f);
        w.playSound(c, Sound.BLOCK_ROOTED_DIRT_PLACE, 1f, 0.6f);
        w.playSound(c, Sound.BLOCK_GRASS_BREAK, 1f, 0.6f);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            w.spawnParticle(Particle.BLOCK, c.clone().add(Math.cos(a) * 3.5, 0.2, Math.sin(a) * 3.5), 3, 0.1, 0.2, 0.1, 0, raiz);
        }
        plugin.fx.anel(c.clone().add(0, 0.1, 0), raiz, 3.5, 6, 16, 0.45f, null);
        for (int i = 0; i < 8; i++) {
            double a = rnd().nextDouble(Math.PI * 2), r = rnd().nextDouble(0.5, 3.2);
            plugin.fx.pilar(c.clone().add(Math.cos(a) * r, -0.3, Math.sin(a) * r), raiz, rnd().nextDouble(0.9, 1.6), 0.3, rnd().nextDouble(0.2, 0.5), 4, 50, null);
        }
        List<LivingEntity> presos = inimigosPerto(c, 3.5, 2.5, p);
        // props 3D: raizes (tentaculos) brotando do chao em volta e em cada preso, balancando
        java.util.List<Player> plateia3d = plugin.props.plateia(c);
        if (!plateia3d.isEmpty()) {
            java.util.List<org.bukkit.entity.ItemDisplay> raizes3d = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + rnd().nextDouble(-0.3, 0.3), r = rnd().nextDouble(1.2, 3.2);
                Location base = c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                org.bukkit.entity.ItemDisplay rz = plugin.props.prop(base.clone().add(0, 1.0, 0), "raiz_f" + (i % 2), 60, plateia3d);
                if (rz == null) continue;
                // o modelo "aponta" para +z: vira a ponta para o centro (onde estao os presos); nasce pequeno e cresce
                Vector paraCentro = c.toVector().subtract(base.toVector()).setY(0);
                Props3D.pose(rz, paraCentro, 0.2f, 0.05f, 0.2f, 0f, 0);
                final Vector pc = paraCentro;
                Bukkit.getScheduler().runTaskLater(plugin, () -> Props3D.pose(rz, pc, (float) rnd().nextDouble(0.9, 1.3), (float) rnd().nextDouble(1.0, 1.5), (float) rnd().nextDouble(0.9, 1.3), 0f, 5), 1L);
                raizes3d.add(rz);
            }
            for (LivingEntity le : presos) {
                Location pe = le.getLocation();
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI * 2 / 3 * i + 0.4;
                    Location base = pe.clone().add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5);
                    org.bukkit.entity.ItemDisplay rz = plugin.props.prop(base.clone().add(0, 1.0, 0), "raiz_f0", 60, plateia3d);
                    if (rz == null) continue;
                    Vector paraCentro = pe.toVector().subtract(base.toVector()).setY(0);
                    Props3D.pose(rz, paraCentro, 0.2f, 0.05f, 0.2f, 0f, 0);
                    final Vector pc = paraCentro;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> Props3D.pose(rz, pc, 0.8f, (float) (le.getHeight() * 0.55 + 0.3), 0.8f, 0f, 4), 1L);
                    raizes3d.add(rz);
                }
            }
            final int[] tq = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
                tq[0]++;
                boolean viva = false;
                for (int i = 0; i < raizes3d.size(); i++) {
                    org.bukkit.entity.ItemDisplay rz = raizes3d.get(i);
                    if (rz == null || !rz.isValid()) continue;
                    viva = true;
                    Props3D.quadro(rz, "raiz_f" + ((tq[0] + i) % 2));
                }
                if (!viva || tq[0] > 12) tarefa.cancel();
            }, 5L, 5L);
        }
        for (LivingEntity le : presos) {
            Location pe = le.getLocation();
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i + 0.4;
                plugin.fx.pilar(pe.clone().add(Math.cos(a) * 0.55, -0.2, Math.sin(a) * 0.55), Efeitos.b(Material.MANGROVE_ROOTS), le.getHeight() * 0.8 + 0.4, 0.28, -0.35, 4, 54, null);
            }
        }
        for (LivingEntity le : presos) {
            if (plugin.causarDano(le, DANO_RAIZES * f, p)) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 6));
            }
        }
        presos.removeIf(le -> !le.isValid() || le.isDead());
        plugin.cena.raizes(p, c, presos);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (t[0] > 60) {
                tarefa.cancel();
                return;
            }
            for (LivingEntity le : presos) {
                if (!le.isValid() || le.isDead()) continue;
                Vector v = le.getVelocity();
                le.setVelocity(new Vector(0, Math.min(0, v.getY()), 0));
                if (t[0] % 4 == 0) {
                    Location pe = le.getLocation().add(0, 0.3, 0);
                    w.spawnParticle(Particle.BLOCK, pe, 6, 0.3, 0.3, 0.3, 0, raiz);
                    w.spawnParticle(Particle.DUST, pe, 3, 0.3, 0.4, 0.3, 0, marrom);
                }
            }
            if (t[0] % 8 == 0) w.spawnParticle(Particle.HAPPY_VILLAGER, c.clone().add(0, 0.3, 0), 6, 2.5, 0.2, 2.5, 0);
            t[0]++;
        }, 0L, 1L);
        Poderes.barra(p, "§2§lRAIZES! §7" + (presos.isEmpty() ? "Ninguem foi preso." : presos.size() + " inimigo(s) preso(s) por 3s"));
        return true;
    }

    boolean ehLoboEspiritual(Entity e) {
        return e instanceof Wolf && e.getPersistentDataContainer().has(chaveLobo, PersistentDataType.STRING);
    }

    private int lobosVivos(Player p) {
        List<UUID> l = lobos.get(p.getUniqueId());
        if (l == null) return 0;
        l.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid() || e.isDead();
        });
        return l.size();
    }

    boolean matilha(Player p) {
        if (lobosVivos(p) > 0) {
            Poderes.barra(p, "§eSua matilha ainda esta lutando!");
            return false;
        }
        double f = plugin.fator(p);
        World w = p.getWorld();
        Location base = p.getLocation();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3 + base.getYaw();
            Location l = base.clone().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5);
            Block b = l.getBlock();
            if (!b.isPassable() || !b.getRelative(BlockFace.UP).isPassable()) l = base.clone();
            Wolf lobo = w.spawn(l, Wolf.class, wf -> {
                wf.setTamed(true);
                wf.setOwner(p);
                wf.setAdult();
                wf.setCollarColor(DyeColor.LIME);
                wf.setPersistent(false);
                wf.customName(Poderes.txt("§6Lobo Espiritual"));
                wf.getPersistentDataContainer().set(chaveLobo, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            if (!lobo.isValid()) continue;
            AttributeInstance vida = lobo.getAttribute(Attribute.MAX_HEALTH);
            if (vida != null) {
                vida.setBaseValue(24.0);
                lobo.setHealth(24.0);
            }
            AttributeInstance ataque = lobo.getAttribute(Attribute.ATTACK_DAMAGE);
            if (ataque != null) ataque.setBaseValue(DANO_LOBO * f);
            w.spawnParticle(Particle.SOUL, lobo.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.02);
            plugin.fx.colunaDeLuz(lobo.getLocation(), Efeitos.b(Material.LIGHT_BLUE_STAINED_GLASS), 3.0, 0.9, 10, org.bukkit.Color.fromRGB(120, 220, 255));
            plugin.cena.loboChega(lobo.getLocation());
            ids.add(lobo.getUniqueId());
        }
        if (ids.isEmpty()) {
            Poderes.barra(p, "§cNao deu para invocar os lobos aqui.");
            return false;
        }
        UUID dono = p.getUniqueId();
        lobos.put(dono, ids);
        w.playSound(base, Sound.ENTITY_WOLF_ANGRY_GROWL, 1f, 0.8f);
        plugin.fx.anel(base.clone().add(0, 0.1, 0), Efeitos.b(Material.MOSS_BLOCK), 2.6, 7, 14, 0.35f, org.bukkit.Color.fromRGB(120, 220, 255));
        Poderes.barra(p, "§6§lMATILHA! §7" + ids.size() + " lobos lutam com voce por 20s");
        BukkitTask antiga = tarefaLobos.remove(dono);
        if (antiga != null) antiga.cancel();
        final int[] seg = {20};
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            seg[0]--;
            if (seg[0] <= 0 || !p.isOnline() || p.isDead() || lobosVivos(p) == 0) {
                removerLobos(dono, seg[0] <= 0 && p.isOnline());
                return;
            }
            for (UUID id : lobos.getOrDefault(dono, List.of())) {
                Entity e = Bukkit.getEntity(id);
                if (e == null) continue;
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, e.getLocation().add(0, 0.6, 0), 2, 0.2, 0.2, 0.2, 0.0);
                if (!e.getWorld().equals(p.getWorld()) || e.getLocation().distanceSquared(p.getLocation()) > 30 * 30) {
                    e.teleport(p.getLocation());
                }
            }
        }, 20L, 20L);
        tarefaLobos.put(dono, t);
        return true;
    }

    private void removerLobos(UUID dono, boolean avisar) {
        BukkitTask t = tarefaLobos.remove(dono);
        if (t != null) t.cancel();
        List<UUID> l = lobos.remove(dono);
        if (l == null) return;
        for (UUID id : l) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) {
                e.getWorld().spawnParticle(Particle.SOUL, e.getLocation().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.03);
                e.remove();
            }
        }
        Player p = Bukkit.getPlayer(dono);
        if (avisar && p != null) Poderes.barra(p, "§7Sua matilha voltou para a floresta.");
    }

    boolean bencao(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        w.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        w.playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
        org.bukkit.Color verdeClaro = org.bukkit.Color.fromRGB(120, 255, 120);
        plugin.fx.colunaDeLuz(l, Efeitos.b(Material.LIME_STAINED_GLASS), 7.0, 1.6, 14, verdeClaro);
        plugin.cena.bencao(l);
        plugin.fx.anel(l.clone().add(0, 0.1, 0), Efeitos.b(Material.FLOWERING_AZALEA_LEAVES), 8.0, 12, 30, 0.6f, null);
        plugin.fx.bolhas(l, Efeitos.b(Material.LIME_STAINED_GLASS), 16, 6.0, 2.6, 20, verdeClaro);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            w.spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(Math.cos(a) * 8, 0.3, Math.sin(a) * 8), 1, 0, 0, 0, 0);
        }
        int curados = 0;
        for (Entity e : w.getNearbyEntities(l, 8, 4, 8)) {
            boolean jogador = e instanceof Player outro && (outro.getGameMode() == GameMode.SURVIVAL || outro.getGameMode() == GameMode.ADVENTURE);
            boolean bicho = e instanceof Tameable tm && tm.isTamed() && p.getUniqueId().equals(tm.getOwnerUniqueId());
            if (!jogador && !bicho && !e.equals(p)) continue;
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
            double m = max != null ? max.getValue() : 20.0;
            le.setHealth(Math.min(m, le.getHealth() + 6.0));
            le.setFireTicks(0);
            le.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            w.spawnParticle(Particle.HEART, le.getLocation().add(0, le.getHeight() + 0.3, 0), 5, 0.4, 0.3, 0.4, 0.1);
            if (e instanceof Player outro && !outro.equals(p)) {
                Poderes.barra(outro, "§a" + p.getName() + " te curou com a Bencao da Natureza!");
                curados++;
            }
        }
        Poderes.barra(p, "§a§lBENCAO DA NATUREZA! §7" + (curados == 0 ? "Voce foi curado." : "Voce e mais " + curados + " jogador(es) foram curados."));
        return true;
    }

    boolean espinhos(Player p) {
        double f = plugin.fator(p);
        World w = p.getWorld();
        BlockData espinho = Material.SWEET_BERRY_BUSH.createBlockData();
        Particle.DustOptions verde = cor(40, 150, 40, 1.3f);
        Poderes.barra(p, "§2§lFURIA DOS ESPINHOS!");
        for (int onda = 0; onda < 3; onda++) {
            final int ondaN = onda;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.isDead()) return;
                Location c = p.getLocation();
                plugin.cena.espinhos(p, c, ondaN);
                w.playSound(c, Sound.ENCHANT_THORNS_HIT, 1f, 0.7f);
                w.playSound(c, Sound.BLOCK_SWEET_BERRY_BUSH_BREAK, 1f, 0.6f);
                for (int i = 0; i < 18; i++) {
                    double a = rnd().nextDouble(Math.PI * 2);
                    double r = 1.5 + rnd().nextDouble() * 5.5;
                    Location pe = c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    if (i % 2 == 0) plugin.fx.pilar(pe.clone().subtract(0, 0.3, 0), Efeitos.b(Material.CACTUS), rnd().nextDouble(1.0, 1.9), 0.32, rnd().nextDouble(0.1, 0.45), 3, 10, null);
                    for (double y = 0; y < 1.6; y += 0.25) {
                        w.spawnParticle(Particle.DUST, pe.clone().add(0, y, 0), 1, 0.02, 0, 0.02, 0, verde);
                    }
                    w.spawnParticle(Particle.BLOCK, pe.clone().add(0, 0.3, 0), 4, 0.2, 0.2, 0.2, 0, espinho);
                }
                for (LivingEntity le : inimigosPerto(c, 7.0, 3.0, p)) {
                    if (plugin.causarDano(le, DANO_ESPINHOS * f, p)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                        le.setVelocity(le.getVelocity().add(new Vector(0, 0.35, 0)));
                    }
                }
            }, onda * 20L);
        }
        return true;
    }

    // ------------------------------------------------------------------ limpeza

    /** Desfaz tudo que esta ativo do jogador (troca de classe, saida, morte). */
    void limpar(Player p) {
        UUID id = p.getUniqueId();
        terminarSaiyajin(p, false);
        removerLobos(id, false);
        fumacaAte.remove(id);
        emSequencia.remove(id);
        if (plugin.lut != null) plugin.lut.limpar(p);
    }

    void limparTodos() {
        for (UUID id : new ArrayList<>(saiyajinAte.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) mostrarVisual(p, false);
        }
        for (UUID id : new ArrayList<>(lobos.keySet())) removerLobos(id, false);
        for (BukkitTask t : auraSaiyajin.values()) t.cancel();
        auraSaiyajin.clear();
        saiyajinAte.clear();
        fumacaAte.clear();
        emSequencia.clear();
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        limpar(e.getPlayer());
    }

    @EventHandler
    public void morreu(PlayerDeathEvent e) {
        limpar(e.getEntity());
    }

    /** Lobo espiritual nao deixa drop nem XP. */
    @EventHandler
    public void loboMorreu(EntityDeathEvent e) {
        if (!ehLoboEspiritual(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }
    // ------------------------------------------------------------------ TRIDENTE

    boolean arremesso(Player p) {
        double f = plugin.fator(p);
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location origem = p.getEyeLocation().subtract(0, 0.2, 0).add(dir.clone().multiply(0.6));
        Particle.DustOptions agua = cor(40, 170, 220, 1.2f);
        p.swingMainHand();
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1f);
        // tridente de verdade voando junto (so aparencia), com rastro de agua
        org.bukkit.entity.Trident visual = plugin.vis.tridente(origem, dir, 2.0, 20, true);
        projetil(p, origem, dir, 30, 2.0, 0.7,
                pos -> {
                    w.spawnParticle(Particle.DUST, pos, 3, 0.05, 0.05, 0.05, 0, agua);
                    w.spawnParticle(Particle.SPLASH, pos, 3, 0.05, 0.05, 0.05, 0);
                    w.spawnParticle(Particle.BUBBLE_POP, pos, 2, 0.1, 0.1, 0.1, 0.02);
                },
                (alvo, pos) -> {
                    plugin.vis.remover(visual);
                    w.spawnParticle(Particle.SPLASH, pos, 40, 0.4, 0.4, 0.4, 0.3);
                    w.spawnParticle(Particle.FALLING_WATER, pos.clone().add(0, 0.8, 0), 25, 0.5, 0.3, 0.5, 0);
                    w.spawnParticle(Particle.BUBBLE_POP, pos, 20, 0.4, 0.4, 0.4, 0.1);
                    w.playSound(pos, Sound.ITEM_TRIDENT_HIT, 1f, 1f);
                    w.playSound(pos, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.2f);
                    plugin.causarDano(alvo, DANO_ARREMESSO * f, p);
                },
                pos -> {
                    plugin.vis.remover(visual);
                    w.spawnParticle(Particle.SPLASH, pos, 20, 0.3, 0.3, 0.3, 0.15);
                    w.playSound(pos, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
                });
        return true;
    }

    boolean onda(Player p) {
        double f = plugin.fator(p);
        World w = p.getWorld();
        Location l = p.getLocation();
        Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        dir.normalize();
        final Vector d = dir;
        Particle.DustOptions azul = cor(60, 140, 230, 1.6f);
        w.playSound(l, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.8f);
        w.playSound(l, Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 1f);
        // o jogador gira como no Riptide e avanca um pouco (so se tiver espaco na frente)
        try {
            Location frente = l.clone().add(d.clone().multiply(2));
            if (frente.getBlock().isPassable() && frente.clone().add(0, 1, 0).getBlock().isPassable()) {
                p.startRiptideAttack(14, 0f, Visuais.tridenteItem(true));
                p.setVelocity(d.clone().multiply(1.1).setY(0.25));
                plugin.semQueda(p, 3000L);
            }
        } catch (Throwable ignored) {
        }
        org.bukkit.block.data.BlockData vidroA = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), vidroB = Material.BLUE_STAINED_GLASS.createBlockData();
        plugin.cena.onda(l, d);
        // prop 3D: a crista da onda (3 larguras lado a lado) avancando e crescendo; quem a ve nao ve o vidro
        java.util.List<Player> plateia3d = plugin.props.plateia(l);
        java.util.List<org.bukkit.entity.ItemDisplay> cristas = new java.util.ArrayList<>();
        if (!plateia3d.isEmpty()) {
            Vector lado = new Vector(-d.getZ(), 0, d.getX());
            for (int i = -1; i <= 1; i++) {
                org.bukkit.entity.ItemDisplay cr = plugin.props.prop(l.clone().add(d.clone().multiply(1.0)).add(lado.clone().multiply(i * 1.0)).add(0, 0.6, 0), "onda_f0", 20, plateia3d);
                if (cr == null) continue;
                cr.setTeleportDuration(2);
                Props3D.pose(cr, d, 1.0f, 0.4f, 1.0f, 0f, 0);
                cristas.add(cr);
            }
            final int[] tk = {0};
            Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
                tk[0] += 2;
                if (tk[0] > 16) {
                    for (org.bukkit.entity.ItemDisplay cr : cristas) plugin.props.remover(cr);
                    tarefa.cancel();
                    return;
                }
                double dist = 1.0 + tk[0] * 0.45;
                float alt = tk[0] < 8 ? 0.4f + tk[0] * 0.12f : Math.max(0.1f, 1.4f - (tk[0] - 8) * 0.16f);
                float larg = (float) Math.min(1.6, 0.8 + dist * 0.15);
                for (int i = 0; i < cristas.size(); i++) {
                    org.bukkit.entity.ItemDisplay cr = cristas.get(i);
                    Props3D.mover(cr, l.clone().add(d.clone().multiply(dist)).add(lado.clone().multiply((i - 1) * larg)).add(0, 0.6, 0));
                    Props3D.pose(cr, d, larg, alt, 1.0f, 0f, 2);
                    Props3D.quadro(cr, "onda_f" + ((tk[0] / 2 + i) % 3));
                }
            }, 0L, 2L);
        }
        for (int k = 1; k <= 7; k++) {
            final int dist = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location c = l.clone().add(d.clone().multiply(dist)).add(0, 0.1, 0);
                Vector lado = new Vector(-d.getZ(), 0, d.getX());
                // parede de agua (blocos de vidro azul que sobem e caem)
                for (double sx = -Math.min(3, dist * 0.6); sx <= Math.min(3, dist * 0.6) + 0.01; sx += 1.0) {
                    Location pt = c.clone().add(lado.clone().multiply(sx));
                    org.bukkit.entity.BlockDisplay b = plugin.vis.bloco(pt, (dist + (int) sx) % 2 == 0 ? vidroA : vidroB, 12, null);
                    plugin.props.esconder(plateia3d, b);
                    Visuais.escala(b, 0.9f, 0f, 1);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> Visuais.escala(b, 1.3f, 0.4f, 4), 1L);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> Visuais.escala(b, 0.05f, 0.8f, 6), 6L);
                    w.spawnParticle(Particle.SPLASH, pt.clone().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.15);
                    w.spawnParticle(Particle.FALLING_WATER, pt.clone().add(0, 1.5, 0), 3, 0.3, 0.1, 0.3, 0);
                    w.spawnParticle(Particle.DUST, pt.clone().add(0, 0.8, 0), 1, 0.1, 0.4, 0.1, 0, azul);
                }
                if (dist % 2 == 0) w.playSound(c, Sound.ENTITY_GENERIC_SPLASH, 0.7f, 1.2f);
            }, k * 2L);
        }
        for (LivingEntity le : inimigosPerto(l.clone().add(d.clone().multiply(3.5)), 4.0, 2.5, p)) {
            Vector para = le.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (para.lengthSquared() > 0.01 && para.clone().normalize().dot(d) < 0.2) continue;
            if (!plugin.causarDano(le, DANO_ONDA * f, p)) continue;
            le.setVelocity(d.clone().multiply(1.2).setY(0.45));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1));
        }
        Poderes.barra(p, "§b§lONDA!");
        return true;
    }

    boolean tridenteRaio(Player p) {
        double f = plugin.fator(p);
        List<LivingEntity> alvos = inimigosPerto(p.getLocation(), 15.0, 6.0, p);
        alvos.removeIf(le -> !p.hasLineOfSight(le));
        if (alvos.isEmpty()) {
            Poderes.barra(p, "§7Nenhum inimigo perto (ate 15 blocos).");
            return false;
        }
        Location ref = p.getLocation();
        alvos.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(ref)));
        World w = p.getWorld();
        p.swingMainHand();
        w.playSound(ref, Sound.ITEM_TRIDENT_THUNDER, 1f, 1f);
        int n = Math.min(3, alvos.size());
        for (int i = 0; i < n; i++) {
            LivingEntity le = alvos.get(i);
            // um tridente cai do ceu em cada alvo e chama o raio (Canalizacao)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!le.isValid() || le.isDead() || !p.isOnline()) return;
                plugin.vis.tridente(le.getLocation().add(0, 12, 0), new Vector(0, -1, 0), 3.0, 6, true);
                w.spawnParticle(Particle.ELECTRIC_SPARK, le.getLocation().add(0, 6, 0), 20, 0.3, 4, 0.3, 0.05);
            }, 4L * i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!le.isValid() || le.isDead() || !p.isOnline()) return;
                w.strikeLightningEffect(le.getLocation());
                w.spawnParticle(Particle.FLASH, le.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.WHITE);
                plugin.cena.fogos(le.getLocation().add(0, 1, 0), org.bukkit.FireworkEffect.Type.BURST, true, Cenas.CIANO, Cenas.BRANCO, Cenas.AZUL);
                plugin.cena.raioParticulas(le.getLocation().add(0, 10, 0), le.getLocation().add(0, 1, 0), 0.6);
                w.spawnParticle(Particle.SPLASH, le.getLocation().add(0, 0.5, 0), 30, 0.6, 0.3, 0.6, 0.3);
                plugin.causarDano(le, DANO_TRIDENTE_RAIO * f, p);
            }, 4L * i + 4L);
        }
        Poderes.barra(p, "§e§lTRIDENTE TROVEJANTE! §7" + n + " alvo(s)");
        return true;
    }

    boolean redemoinho(Player p) {
        UUID id = p.getUniqueId();
        if (!emSequencia.add(id)) return false;
        double f = plugin.fator(p);
        Location c = mira(p, 25);
        World w = c.getWorld();
        Particle.DustOptions azul = cor(30, 110, 220, 1.4f);
        w.playSound(c, Sound.AMBIENT_UNDERWATER_ENTER, 1f, 0.8f);
        Poderes.barra(p, "§9§lREDEMOINHO!");
        plugin.cena.redemoinho(c, 80);
        List<org.bukkit.entity.BlockDisplay> gotas = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            gotas.add(plugin.vis.bloco(c, (i % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLUE_STAINED_GLASS).createBlockData(), 90, null));
        // prop 3D: o funil do redemoinho (modelo de 3 blocos de altura, base em y=-16) girando; quem o ve nao ve as gotas de vidro
        java.util.List<Player> plateia3d = plugin.props.plateia(c);
        org.bukkit.entity.ItemDisplay funil = plugin.props.prop(c.clone().add(0, 1.0, 0), "redemoinho_f0", 90, plateia3d);
        if (funil != null) {
            funil.setTeleportDuration(1);
            Props3D.escala(funil, 0.2f, 0f, 0);
            for (org.bukkit.entity.BlockDisplay g : gotas) plugin.props.esconder(plateia3d, g);
        }
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (t[0] > 80 || !p.isOnline() || p.isDead()) {
                emSequencia.remove(id);
                for (org.bukkit.entity.BlockDisplay g : gotas) plugin.vis.remover(g);
                plugin.props.remover(funil);
                tarefa.cancel();
                return;
            }
            if (funil != null && funil.isValid()) {
                float esc = t[0] < 10 ? 0.2f + t[0] * 0.1f : (t[0] > 72 ? Math.max(0.1f, 1.2f - (t[0] - 72) * 0.14f) : 1.2f);
                Props3D.escala(funil, esc, t[0] * 0.5f, 2);
                if (t[0] % 4 == 0) Props3D.quadro(funil, "redemoinho_f" + ((t[0] / 4) % 3));
            }
            for (int i = 0; i < gotas.size(); i++) {
                double a = t[0] * 0.25 + Math.PI * 2 / gotas.size() * i;
                double r = 1.2 + (i % 3) * 0.8, y = 0.3 + (i % 4) * 0.6 + Math.sin(t[0] * 0.2 + i) * 0.2;
                org.bukkit.entity.BlockDisplay g = gotas.get(i);
                g.teleport(c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r));
                Visuais.escala(g, 0.45f, (float) a, 2);
            }
            w.spawnParticle(Particle.BUBBLE_COLUMN_UP, c.clone().add(0, 0.2, 0), 6, 0.4, 0.1, 0.4, 0.05);
            if (t[0] % 6 == 0) w.spawnParticle(Particle.FALLING_WATER, c.clone().add(0, 3, 0), 8, 1.5, 0.2, 1.5, 0);
            double base = t[0] * 0.4;
            for (int i = 0; i < 6; i++) {
                double a = base + Math.PI / 3 * i;
                double r = 0.6 + (i % 3) * 0.9;
                double y = (i % 3) * 0.7;
                Location pt = c.clone().add(Math.cos(a) * r, 0.2 + y, Math.sin(a) * r);
                w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, azul);
                w.spawnParticle(Particle.SPLASH, pt, 2, 0.05, 0.05, 0.05, 0);
            }
            if (t[0] % 10 == 0) {
                w.playSound(c, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.8f, 1f);
                for (LivingEntity le : inimigosPerto(c, 5.0, 3.0, p)) {
                    Vector v = c.toVector().subtract(le.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() > 0.25) le.setVelocity(v.normalize().multiply(0.45).setY(0.1));
                    plugin.causarDano(le, DANO_REDEMOINHO * f, p);
                }
            }
            t[0] += 2;
        }, 0L, 2L);
        return true;
    }

    boolean poseidon(Player p) {
        double f = plugin.fator(p);
        World w = p.getWorld();
        Location c = p.getLocation();
        Particle.DustOptions azul = cor(20, 70, 200, 2.0f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 200, 0, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 0, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0, false, true));
        w.playSound(c, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 0.8f);
        w.playSound(c, Sound.ENTITY_GENERIC_SPLASH, 1f, 0.6f);
        try {
            p.startRiptideAttack(16, 0f, Visuais.tridenteItem(true));
        } catch (Throwable ignored) {
        }
        // tridente gigante de Poseidon descendo do ceu na frente do jogador e cupula de agua
        Vector fr = c.getDirection().setY(0);
        if (fr.lengthSquared() < 0.01) fr = new Vector(0, 0, 1);
        Location onde = c.clone().add(fr.normalize().multiply(3));
        org.bukkit.entity.ItemDisplay gigante = plugin.vis.item(onde.clone().add(0, 9, 0), Visuais.tridenteItem(true), 50, Color.fromRGB(60, 200, 255));
        plugin.cena.poseidon(onde);
        if (!plugin.cena.itensNoCelular && !plugin.cena.plateia(onde).isEmpty())
            for (int k = 0; k < 5; k++) {
                double a = Math.PI * 2 * k / 5;
                plugin.cena.soBedrock(plugin.vis.tridente(onde.clone().add(Math.cos(a) * 2.5, 14, Math.sin(a) * 2.5), new Vector(0, -1, 0), 2.2, 10, true));
            }
        Visuais.apontar(gigante, new Vector(0, -1, 0), 4.5f, 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gigante.setTeleportDuration(8);
            gigante.teleport(onde.clone().add(0, 2.2, 0));
        }, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            w.strikeLightningEffect(onde);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, onde, 1);
            w.spawnParticle(Particle.SPLASH, onde.clone().add(0, 1, 0), 120, 2.5, 1.2, 2.5, 0.4);
            w.playSound(onde, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.8f);
        }, 10L);
        org.bukkit.entity.BlockDisplay cupula = plugin.vis.bloco(c.clone().add(0, 1, 0), Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), 24, Color.fromRGB(40, 120, 255));
        Bukkit.getScheduler().runTaskLater(plugin, () -> Visuais.escala(cupula, 9f, 0.6f, 14), 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Visuais.escala(cupula, 0.1f, 1.2f, 6), 16L);
        for (int r = 1; r <= 8; r++) {
            final int raio = r;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 / 24 * i;
                    Location pt = c.clone().add(Math.cos(a) * raio, 0.5, Math.sin(a) * raio);
                    w.spawnParticle(Particle.SPLASH, pt, 3, 0.1, 0.6, 0.1, 0.1);
                    w.spawnParticle(Particle.DUST, pt, 1, 0.1, 0.4, 0.1, 0, azul);
                }
            }, r * 2L);
        }
        for (LivingEntity le : inimigosPerto(c, 8.0, 3.5, p)) {
            if (!plugin.causarDano(le, DANO_POSEIDON * f, p)) continue;
            Vector v = le.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (v.lengthSquared() > 0.01) v.normalize().multiply(0.8);
            le.setVelocity(v.setY(0.7));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
        }
        Poderes.barra(p, "§1§lFURIA DE POSEIDON! §7Forca do mar por 10s");
        return true;
    }

    // ------------------------------------------------------------------ ARQUEIRO

    private void flecha(Player p, Vector dir, double alcance, double velocidade, double dano, boolean explode) {
        World w = p.getWorld();
        Location origem = p.getEyeLocation().subtract(0, 0.15, 0).add(dir.clone().multiply(0.6));
        // flecha de verdade voando junto (so aparencia): em chamas na explosiva, dourada nas outras
        org.bukkit.entity.Arrow visual = plugin.vis.flecha(origem, dir, velocidade, (int) (alcance / velocidade) + 4, false, explode,
                explode ? Color.fromRGB(255, 90, 20) : Color.fromRGB(255, 220, 90));
        projetil(p, origem, dir, alcance, velocidade, 0.6,
                pos -> {
                    w.spawnParticle(explode ? Particle.SMALL_FLAME : Particle.CRIT, pos, 2, 0.02, 0.02, 0.02, 0);
                    if (!explode) w.spawnParticle(Particle.END_ROD, pos, 1, 0.01, 0.01, 0.01, 0);
                },
                (alvo, pos) -> {
                    plugin.vis.remover(visual);
                    w.playSound(pos, Sound.ENTITY_ARROW_HIT, 1f, 1.1f);
                    w.spawnParticle(Particle.CRIT, pos, 12, 0.2, 0.2, 0.2, 0.3);
                    if (explode) explodir(p, pos, dano);
                    else plugin.causarDano(alvo, dano, p);
                },
                pos -> {
                    plugin.vis.remover(visual);
                    if (explode) explodir(p, pos, dano);
                    else w.spawnParticle(Particle.CRIT, pos, 6, 0.1, 0.1, 0.1, 0.05);
                });
    }

    private void explodir(Player p, Location pos, double dano) {
        World w = pos.getWorld();
        w.spawnParticle(Particle.EXPLOSION, pos, 2, 0.3, 0.3, 0.3, 0);
        plugin.cena.explosaoFlecha(pos);
        w.spawnParticle(Particle.FLAME, pos, 35, 0.8, 0.5, 0.8, 0.08);
        w.spawnParticle(Particle.LAVA, pos, 8, 0.6, 0.3, 0.6, 0);
        w.spawnParticle(Particle.LARGE_SMOKE, pos, 10, 0.6, 0.4, 0.6, 0.03);
        w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        for (LivingEntity le : inimigosPerto(pos, 3.5, 2.5, p)) {
            if (!plugin.causarDano(le, dano, p)) continue;
            Vector v = le.getLocation().toVector().subtract(pos.toVector()).setY(0);
            if (v.lengthSquared() > 0.01) v.normalize().multiply(0.5);
            le.setVelocity(v.setY(0.4));
        }
    }

    boolean tiroCerteiro(Player p) {
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.4f);
        p.swingMainHand();
        flecha(p, p.getEyeLocation().getDirection().normalize(), 45, 3.0, DANO_TIRO * plugin.fator(p), false);
        return true;
    }

    boolean flechaExplosiva(Player p) {
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
        p.swingMainHand();
        flecha(p, p.getEyeLocation().getDirection().normalize(), 35, 2.0, DANO_EXPLOSIVA * plugin.fator(p), true);
        Poderes.barra(p, "§c§lFLECHA EXPLOSIVA!");
        return true;
    }

    boolean multiplo(Player p) {
        double dano = DANO_MULTIPLO * plugin.fator(p);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.0f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.2f);
        p.swingMainHand();
        for (double ang : new double[] {-0.3, -0.15, 0.0, 0.15, 0.3}) flecha(p, dir.clone().rotateAroundY(ang), 30, 2.5, dano, false);
        return true;
    }

    boolean chuvaFlechas(Player p) {
        UUID id = p.getUniqueId();
        if (!emSequencia.add(id)) return false;
        double f = plugin.fator(p);
        Location c = mira(p, 30);
        World w = c.getWorld();
        Poderes.barra(p, "§7§lCHUVA DE FLECHAS!");
        w.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.7f);
        // flechas sobem do arqueiro antes de cair
        for (int i = 0; i < 6; i++) plugin.vis.flecha(p.getEyeLocation(), new Vector(rnd().nextDouble(-0.15, 0.15), 1, rnd().nextDouble(-0.15, 0.15)), 2.2, 8, false, false, null);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (t[0] > 60 || !p.isOnline() || p.isDead()) {
                emSequencia.remove(id);
                tarefa.cancel();
                return;
            }
            for (int i = 0; i < 5; i++) {
                double a = rnd().nextDouble(Math.PI * 2), r = rnd().nextDouble(0, 4.0);
                Location chao = c.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                plugin.vis.flecha(chao.clone().add(rnd().nextDouble(-1, 1), 11, rnd().nextDouble(-1, 1)), new Vector(0, -1, 0), 1.6, 14, true, false, Color.fromRGB(230, 230, 255));
                w.spawnParticle(Particle.CRIT, chao.clone().add(0, 0.2, 0), 3, 0.1, 0.1, 0.1, 0.1);
            }
            w.playSound(c, Sound.ENTITY_ARROW_HIT, 0.6f, 1.3f);
            for (LivingEntity le : inimigosPerto(c, 4.0, 3.0, p)) plugin.causarDano(le, DANO_CHUVA * f, p);
            t[0] += 5;
        }, 10L, 5L);
        return true;
    }

    boolean tempestade(Player p) {
        UUID id = p.getUniqueId();
        if (emSequencia.contains(id)) return false;
        List<LivingEntity> alvos = inimigosPerto(p.getLocation(), 20.0, 8.0, p);
        alvos.removeIf(le -> !p.hasLineOfSight(le));
        if (alvos.isEmpty()) {
            Poderes.barra(p, "§7Nenhum inimigo perto (ate 20 blocos).");
            return false;
        }
        Location ref = p.getLocation();
        alvos.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(ref)));
        List<LivingEntity> lista = alvos.size() > 8 ? new ArrayList<>(alvos.subList(0, 8)) : alvos;
        emSequencia.add(id);
        double dano = DANO_TEMPESTADE * plugin.fator(p);
        Poderes.barra(p, "§6§lTEMPESTADE DE FLECHAS! §7" + lista.size() + " alvo(s)");
        for (int rajada = 0; rajada < 3; rajada++) {
            final int r = rajada;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (r == 2) emSequencia.remove(id);
                if (!p.isOnline() || p.isDead()) return;
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f + r * 0.1f);
                p.swingMainHand();
                for (LivingEntity le : lista) {
                    if (!le.isValid() || le.isDead() || !le.getWorld().equals(p.getWorld())) continue;
                    Vector d = le.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(p.getEyeLocation().toVector());
                    if (d.lengthSquared() < 0.01) continue;
                    flecha(p, d.normalize(), 24, 3.0, dano, false);
                }
            }, 10L * rajada);
        }
        return true;
    }
}
