package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Classe Lutador.
 * Passiva "Punhos de Aco": soco de mao vazia com dano a mais, que cresce com o nivel de poder ate o nivel maximo das
 * classes (o da 5a habilidade). Segue a carga do golpe igual ao Minecraft (soco fraco = bonus fraco, critico = 1,5x).
 * As 5 habilidades: Rajada de Socos, Gancho do Dragao, Soco Sismico, Punhos em Chamas e Punho Meteoro.
 * PC: animacao com displays (Visuais/Efeitos). Celular: as cenas "lut*" do Cenas.
 */
final class Lutador implements Listener {

    // Danos base (x forca do nivel, como as outras classes). 2 de dano = 1 coracao.
    static final double DANO_RAJADA = 1.4;          // por soco
    static final int RAJADA_SOCOS = 6;
    static final double DANO_GANCHO = 6.0;
    static final double RAIO_GANCHO = 5.0;          // pega todos em volta, nao so na frente
    static final double DANO_SISMICO = 6.5;
    static final double FURIA_MULT = 1.5;           // bonus do soco durante os Punhos em Chamas
    static final double FURIA_ESPALHA = 0.6;        // parte do soco que explode em volta do alvo
    static final int FURIA_TICKS = 200;
    static final double DANO_METEORO = 11.0;
    static final double DANO_METEORO_EXPLOSAO = 6.0;

    static final double BONUS_MIN_PADRAO = 3.0, BONUS_MAX_PADRAO = 14.0;

    static final Color LARANJA = Color.fromRGB(255, 140, 30), AMARELO = Color.fromRGB(255, 220, 80),
            VERMELHO = Color.fromRGB(230, 50, 30), BRANCO = Color.WHITE, QUENTE = Color.fromRGB(255, 225, 170);

    private final Poderes pl;
    private final Map<UUID, Long> furiaAte = new HashMap<>();
    private final Map<UUID, BukkitTask> furiaTarefa = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> furiaMaos = new HashMap<>();
    /** Quem esta no meio de uma habilidade de varios passos. */
    private final Set<UUID> emSequencia = new HashSet<>();
    /** Nivel de poder lido ha pouco (o PlaceholderAPI nao precisa ser lido a cada soco). */
    private final Map<UUID, long[]> nivelCache = new HashMap<>();
    /** Carga do ultimo soco (do LOW para o MONITOR, onde ja se sabe que o golpe entrou). */
    private final Map<UUID, double[]> golpe = new HashMap<>();

    Lutador(Poderes pl) {
        this.pl = pl;
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    private static BlockData b(Material m) {
        return m.createBlockData();
    }

    private void depois(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(pl, r, Math.max(1L, ticks));
    }

    // =============================================================== passiva: Punhos de Aco

    /** Bonus do soco no 'nivel' de poder: de 'min' (nivel 0) ate 'max' (no 'nivelMax' ou mais). */
    static double bonusSoco(int nivel, int nivelMax, double min, double max) {
        if (nivel < 0) nivel = 0;
        double t = nivelMax <= 0 ? 1.0 : Math.min(1.0, nivel / (double) nivelMax);
        return min + (max - min) * t;
    }

    /**
     * Quanto do bonus entra pela carga do golpe, como o Minecraft faz com o dano do soco.
     * 'razao' = dano do golpe / dano base do jogador (0,2 a 1; acima de 1 so se for critico).
     */
    static double fatorCarga(double razao, boolean critico) {
        if (razao > 1.05) return critico ? 1.5 : 1.0;
        return Math.max(0.0, Math.min(1.0, razao));
    }

    /** Nivel da 5a habilidade da classe: e onde o soco chega no maximo. */
    int nivelMaximo() {
        return pl.nivelPosicao(Classe.LUTADOR.poderes.length - 1);
    }

    double bonusMin() {
        return pl.getConfig().getDouble("lutador.soco-bonus-min", BONUS_MIN_PADRAO);
    }

    double bonusMax() {
        return pl.getConfig().getDouble("lutador.soco-bonus-max", BONUS_MAX_PADRAO);
    }

    int nivel(Player p) {
        long agora = System.currentTimeMillis();
        long[] c = nivelCache.get(p.getUniqueId());
        if (c != null && c[1] > agora) return (int) c[0];
        int n = pl.nivelDePoder(p);
        nivelCache.put(p.getUniqueId(), new long[] {n, agora + 3000L});
        return n;
    }

    /** Bonus do soco agora (sem a carga do golpe). */
    double bonusAtual(Player p) {
        double b = bonusSoco(nivel(p), nivelMaximo(), bonusMin(), bonusMax());
        return furiosa(p) ? b * FURIA_MULT : b;
    }

    private static double danoBase(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_DAMAGE);
        double v = a == null ? 1.0 : a.getValue();
        return v <= 0.01 ? 1.0 : v;
    }

    /** As mesmas condicoes do Minecraft para o golpe critico (caindo, fora da agua, sem correr...). */
    @SuppressWarnings("deprecation")
    private static boolean podeCritico(Player p) {
        return p.getFallDistance() > 0 && !p.isOnGround() && !p.isClimbing() && !p.isInWater() && !p.isInsideVehicle()
                && !p.isSprinting() && !p.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    private boolean socoValido(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return false;
        if (!(e.getDamager() instanceof Player p)) return false;
        if (pl.emDanoPoder) return false;
        if (!(e.getEntity() instanceof LivingEntity alvo) || alvo instanceof ArmorStand) return false;
        if (pl.classe(p) != Classe.LUTADOR) return false;
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return false;
        if (pl.semPoderes(p.getWorld())) return false;
        return !pl.ehAliado(alvo, p) && !pl.pets.ehPet(alvo) && !pl.vis.ehVisual(alvo);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void soco(EntityDamageByEntityEvent e) {
        if (!socoValido(e)) return;
        Player p = (Player) e.getDamager();
        double razao = e.getDamage() / danoBase(p);
        boolean critico = razao > 1.05 && podeCritico(p);
        double carga = fatorCarga(razao, critico);
        double bonus = bonusAtual(p) * carga;
        golpe.put(p.getUniqueId(), new double[] {carga, critico ? 1 : 0, e.getEntity().getEntityId()});
        if (bonus > 0) e.setDamage(e.getDamage() + bonus);
    }

    /** O golpe entrou de verdade (ninguem cancelou): mostra o impacto e, na Furia, a explosao de fogo. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void socoEntrou(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        double[] g = golpe.remove(p.getUniqueId());
        if (g == null || (int) g[2] != e.getEntity().getEntityId() || !socoValido(e)) return;
        LivingEntity alvo = (LivingEntity) e.getEntity();
        boolean forte = g[0] >= 0.85, critico = g[1] > 0;
        efeitoSoco(p, alvo, forte, critico);
        if (forte && furiosa(p)) {
            double espalha = e.getDamage() * FURIA_ESPALHA;
            Bukkit.getScheduler().runTask(pl, () -> explosaoFuria(p, alvo, espalha));
        }
    }

    /** Impacto do soco: para todo mundo (particulas que o celular tambem mostra). */
    private void efeitoSoco(Player p, LivingEntity alvo, boolean forte, boolean critico) {
        World w = alvo.getWorld();
        Location c = alvo.getLocation().add(0, alvo.getHeight() * 0.55, 0);
        Vector d = c.toVector().subtract(p.getEyeLocation().toVector());
        if (d.lengthSquared() < 0.01) d = p.getLocation().getDirection();
        d.normalize();
        Location impacto = c.clone().subtract(d.clone().multiply(Math.min(0.6, alvo.getWidth() * 0.5)));
        Fx.p(w, Particle.CRIT, impacto, forte ? 7 : 2, 0.12, 0.12, 0.12, 0.25);
        if (!forte) return;
        double t = Math.min(1.0, Math.max(0, nivel(p)) / (double) Math.max(1, nivelMaximo()));
        Color cor = furiosa(p) ? VERMELHO : (t >= 0.66 ? AMARELO : LARANJA);
        Fx.p(w, Particle.DUST, impacto, 5, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(cor, 1.1f));
        anelParticulas(w, impacto, d, 0.45 + t * 0.35, 10, new Particle.DustOptions(BRANCO, 0.8f));
        w.playSound(impacto, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.55f, 0.75f);
        if (critico) {
            Fx.p(w, Particle.ELECTRIC_SPARK, impacto, 10, 0.2, 0.2, 0.2, 0.35);
            w.playSound(impacto, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.3f, 1.8f);
            pulsoPc(impacto, b(Material.WHITE_STAINED_GLASS), 0.12f, 0.8f + (float) t * 0.5f, 3, cor);
        }
    }

    /** Anel de particulas em volta do eixo 'dir' (onda de choque do soco). */
    private static void anelParticulas(World w, Location c, Vector dir, double raio, int pontos, Object dado) {
        Vector u = lado(dir), v = u.clone().crossProduct(dir).normalize();
        for (int i = 0; i < pontos; i++) {
            double a = Math.PI * 2 * i / pontos;
            Location pt = c.clone().add(u.clone().multiply(Math.cos(a) * raio)).add(v.clone().multiply(Math.sin(a) * raio));
            Fx.p(w, Particle.DUST, pt, 1, 0, 0, 0, 0, dado);
        }
    }

    // =============================================================== ajudantes de geometria

    /** Vetor para o lado (horizontal) de 'dir'. */
    static Vector lado(Vector dir) {
        Vector l = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (l.lengthSquared() < 1e-4) l = new Vector(1, 0, 0);
        return l.normalize();
    }

    /** Onde fica a mao do jogador (direita ou esquerda), um pouco na frente do corpo. */
    static Location mao(Player p, boolean direita) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        Vector frente = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector paraDireita = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        double h = p.isSneaking() ? 0.95 : 1.15;
        Location m = l.clone().add(0, h, 0).add(frente.multiply(0.38)).add(paraDireita.multiply(direita ? 0.36 : -0.36));
        m.setYaw(0);
        m.setPitch(0);
        return m;
    }

    private static Vector frenteHorizontal(Location l) {
        Vector f = l.getDirection().setY(0);
        if (f.lengthSquared() < 0.01) f = new Vector(0, 0, 1);
        return f.normalize();
    }

    /** Distancia livre na mira (para na parede). */
    private static double alcance(Location olho, Vector dir, double max) {
        RayTraceResult r = olho.getWorld().rayTraceBlocks(olho, dir, max, FluidCollisionMode.NEVER, true);
        if (r == null) return max;
        return Math.max(0.5, olho.toVector().distance(r.getHitPosition()) - 0.2);
    }

    /** Inimigos na frente do jogador ('cosMin' = abertura do cone; 0,5 = 60 graus para cada lado). */
    List<LivingEntity> naFrente(Player p, double alcance, double cosMin) {
        List<LivingEntity> r = new ArrayList<>();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        for (Entity e : p.getNearbyEntities(alcance + 1, alcance + 1, alcance + 1)) {
            if (!pl.inimigoArea(e, p)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector ate = le.getLocation().add(0, le.getHeight() * 0.5, 0).toVector().subtract(olho.toVector());
            double dist = ate.length();
            if (dist > alcance + le.getWidth() * 0.5) continue;
            if (dist > 0.8 && ate.normalize().dot(dir) < cosMin) continue;
            if (!p.hasLineOfSight(le)) continue;
            r.add(le);
        }
        return r;
    }

    /** Orientacao que leva o eixo +Z para 'dir' sem virar de lado (o "em cima" continua em cima). */
    static Quaternionf olhando(Vector dir) {
        Vector n = dir.clone().normalize();
        double yaw = Math.atan2(-n.getX(), n.getZ());
        double pitch = Math.asin(Math.max(-1, Math.min(1, -n.getY())));
        return new Quaternionf().rotationY((float) -yaw).rotateX((float) pitch);
    }

    /** Caixa de tamanho 's' centrada em 'centro' no espaco local girado por 'q'. */
    static Transformation caixa(Vector3f centro, Vector3f s, Quaternionf q) {
        Vector3f t = new Vector3f(centro).sub(s.x / 2f, s.y / 2f, s.z / 2f).rotate(new Quaternionf(q));
        return new Transformation(t, new Quaternionf(q), new Vector3f(s), new Quaternionf());
    }

    // =============================================================== pecas do PC (displays)

    /** Pulso de energia so do PC (o celular ganha a cena dele em cada habilidade). */
    private void pulsoPc(Location c, BlockData casca, float s0, float s1, int ticks, Color brilho) {
        BlockDisplay a = pl.vis.bloco(c, casca, ticks + 8, brilho);
        ThreadLocalRandom r = rnd();
        Quaternionf q0 = new Quaternionf().rotationXYZ((float) r.nextDouble(3), (float) r.nextDouble(3), (float) r.nextDouble(3));
        Quaternionf q1 = new Quaternionf(q0).rotateXYZ(1.4f, 1.1f, 0.7f);
        a.setTransformation(Efeitos.cubo(new Vector3f(), s0, q0));
        depois(1, () -> Efeitos.mover(a, Efeitos.cubo(new Vector3f(), s1, q1), ticks));
        depois(ticks + 1, () -> Efeitos.mover(a, Efeitos.cubo(new Vector3f(), 0.001f, q1), 4));
    }

    /** Anel de pedacos que se abre em volta do eixo 'dir' (onda de choque de pe, como um "O" na frente do soco). */
    private void anelVerticalPc(Location c, Vector dir, double raio, int pecas, float tam, int ticks, Color brilho, BlockData bd) {
        Vector u = lado(dir), v = u.clone().crossProduct(dir).normalize();
        Quaternionf base = olhando(dir);
        for (int i = 0; i < pecas; i++) {
            double a = Math.PI * 2 * i / pecas;
            Vector off = u.clone().multiply(Math.cos(a) * raio).add(v.clone().multiply(Math.sin(a) * raio));
            BlockDisplay d = pl.vis.bloco(c, bd, ticks + 8, brilho);
            Quaternionf q = new Quaternionf(base).rotateZ((float) a);
            d.setTransformation(Efeitos.cubo(new Vector3f(), tam * 0.3f, q));
            Vector3f fim = new Vector3f((float) off.getX(), (float) off.getY(), (float) off.getZ());
            depois(1, () -> Efeitos.mover(d, Efeitos.cubo(fim, tam, q), ticks));
            depois(ticks + 1, () -> Efeitos.mover(d, Efeitos.cubo(fim, 0.001f, q), 4));
        }
    }

    /** Um soco de energia: borrao da mao ate a ponta, punho que voa e estoura. */
    private void socoPc(Location mao, Location ponta, boolean grande, Color cor) {
        Vector d = ponta.toVector().subtract(mao.toVector());
        double len = d.length();
        if (len < 0.1) return;
        BlockDisplay rastro = pl.vis.bloco(mao, b(Material.ORANGE_STAINED_GLASS), 10, cor);
        Visuais.barra(rastro, mao, d, len, grande ? 0.34 : 0.2, 0);
        depois(2, () -> Visuais.barra(rastro, mao, d, len, 0.001, 4));
        BlockDisplay punho = pl.vis.bloco(mao, b(grande ? Material.SHROOMLIGHT : Material.YELLOW_STAINED_GLASS), 12, AMARELO);
        float s = grande ? 0.6f : 0.34f;
        Visuais.escala(punho, s, 0f, 0);
        depois(1, () -> {
            if (punho.isValid()) punho.teleport(ponta);
        });
        depois(3, () -> Visuais.escala(punho, s * 2.2f, 0.8f, 2));
        depois(5, () -> Visuais.escala(punho, 0.001f, 1.6f, 3));
        depois(3, () -> pulsoPc(ponta, b(Material.WHITE_STAINED_GLASS), 0.12f, grande ? 1.6f : 0.8f, 3, grande ? cor : BRANCO));
        if (grande) depois(3, () -> anelVerticalPc(ponta, d, 1.7, 12, 0.32f, 5, cor, b(Material.ORANGE_STAINED_GLASS)));
    }

    // =============================================================== 1. Rajada de Socos

    boolean rajada(Player p) {
        UUID id = p.getUniqueId();
        if (!emSequencia.add(id)) return false;
        double f = pl.fator(p);
        World w = p.getWorld();
        Poderes.barra(p, "§6§lRAJADA DE SOCOS!");
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.7f, 1.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, RAJADA_SOCOS * 4 + 2, 1, false, false));
        final int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!p.isOnline() || p.isDead() || pl.semPoderes(p.getWorld())) {
                emSequencia.remove(id);
                tarefa.cancel();
                return;
            }
            boolean ultimo = n[0] >= RAJADA_SOCOS - 1;
            boolean direita = n[0] % 2 == 0;
            if (direita) p.swingMainHand();
            else p.swingOffHand();
            Location olho = p.getEyeLocation();
            Vector dir = olho.getDirection().normalize();
            Location m = mao(p, direita);
            double al = alcance(olho, dir, 3.2);
            Location ponta = olho.clone().add(dir.clone().multiply(al)).subtract(0, 0.2, 0);
            if (!ultimo) ponta.add(lado(dir).multiply(rnd().nextDouble(-0.45, 0.45))).add(0, rnd().nextDouble(-0.3, 0.3), 0);
            socoPc(m, ponta, ultimo, ultimo ? LARANJA : AMARELO);
            pl.cena.lutSoco(m, ponta, ultimo);
            Fx.p(w, Particle.CRIT, ponta, ultimo ? 14 : 4, 0.15, 0.15, 0.15, 0.3);
            w.playSound(ponta, ultimo ? Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK : Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f,
                    ultimo ? 0.7f : 1.2f + n[0] * 0.08f);
            if (ultimo) {
                w.playSound(ponta, Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 1.8f);
                Fx.p(w, Particle.EXPLOSION, ponta, 1, 0, 0, 0, 0);
            }
            Vector empurra = dir.clone().setY(0);
            if (empurra.lengthSquared() > 0.01) empurra.normalize();
            for (LivingEntity le : naFrente(p, 3.6, 0.55)) {
                le.setNoDamageTicks(0);
                if (!pl.causarDano(le, DANO_RAJADA * f, p)) continue;
                if (ultimo) le.setVelocity(empurra.clone().multiply(1.4).setY(0.45));
                else le.setVelocity(le.getVelocity().multiply(0.3).add(empurra.clone().multiply(0.1)));
            }
            if (ultimo) {
                emSequencia.remove(id);
                tarefa.cancel();
                return;
            }
            n[0]++;
        }, 0L, 4L);
        return true;
    }

    // =============================================================== 2. Gancho do Dragao

    boolean gancho(Player p) {
        double f = pl.fator(p);
        World w = p.getWorld();
        Location ini = p.getLocation();
        Vector frente = frenteHorizontal(ini);
        p.swingMainHand();
        pl.semQueda(p, 4500L);
        p.setVelocity(frente.clone().multiply(0.35).setY(1.05));
        w.playSound(ini, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        w.playSound(ini, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.6f);
        w.playSound(ini, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 1.7f);
        Poderes.barra(p, "§c§lGANCHO DO DRAGAO!");
        pl.fx.texto(p.getEyeLocation().add(frente.clone().multiply(1.3)).add(0, 0.5, 0), "§c§lGANCHO DO DRAGAO!", 1.2f, 22);
        pl.fx.anel(ini.clone().add(0, 0.15, 0), b(Material.ORANGE_STAINED_GLASS), RAIO_GANCHO, 6, 16, 0.36f, LARANJA);
        Fx.p(w, Particle.FLAME, ini.clone().add(0, 0.3, 0), 50, RAIO_GANCHO * 0.5, 0.2, RAIO_GANCHO * 0.5, 0.02);
        pl.fx.colunaDeLuz(ini, b(Material.ORANGE_STAINED_GLASS), 3.2, 0.9, 10, LARANJA);
        pl.cena.lutGancho(p);
        Set<UUID> atingidos = new HashSet<>();
        // o gancho pega tudo em volta (5 blocos) na hora de subir, e quem estiver no caminho enquanto sobe
        ganchoAcerta(p, ini, f, frente, atingidos);
        BlockData[] fogo = {b(Material.ORANGE_STAINED_GLASS), b(Material.RED_STAINED_GLASS), b(Material.SHROOMLIGHT)};
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!p.isOnline() || p.isDead() || t[0] > 14) {
                tarefa.cancel();
                return;
            }
            Location c = p.getLocation();
            // o dragao de fogo: duas espirais de pedacos de fogo subindo em volta do jogador
            for (int k = 0; k < 2; k++) {
                double a = t[0] * 0.85 + Math.PI * k;
                double r = 0.95 - t[0] * 0.02;
                Location pt = c.clone().add(Math.cos(a) * r, 0.2 + (t[0] % 7) * 0.28, Math.sin(a) * r);
                BlockDisplay d = pl.vis.bloco(pt, fogo[(t[0] + k) % fogo.length], 12, k == 0 ? LARANJA : VERMELHO);
                float s = (float) (0.42 - t[0] * 0.012);
                Visuais.escala(d, s, (float) a, 0);
                depois(4, () -> Visuais.escala(d, 0.001f, (float) a + 1.2f, 5));
                Fx.p(w, Particle.FLAME, pt, 2, 0.08, 0.08, 0.08, 0.02);
            }
            if (t[0] <= 9) ganchoAcerta(p, p.getLocation(), f, frente, atingidos);
            if (t[0] == 9) {
                // no alto: a cabeca do dragao estoura em chamas
                Location topo = p.getLocation().add(0, 1.2, 0).add(frente.clone().multiply(0.6));
                pulsoPc(topo, b(Material.ORANGE_STAINED_GLASS), 0.3f, 2.4f, 5, VERMELHO);
                pulsoPc(topo, b(Material.SHROOMLIGHT), 0.15f, 1.1f, 4, null);
                pl.fx.estilhacos(topo, fogo, 10, 2.6, 14, 0.3f);
                Fx.p(w, Particle.FLAME, topo, 30, 0.6, 0.6, 0.6, 0.08);
                w.playSound(topo, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
            }
            t[0]++;
        }, 1L, 1L);
        return true;
    }

    /** Acerto do Gancho: todos os inimigos num raio de 5 blocos em volta (e ate 5 abaixo, enquanto sobe). */
    private void ganchoAcerta(Player p, Location c, double f, Vector frente, Set<UUID> atingidos) {
        World w = c.getWorld();
        for (Entity e : w.getNearbyEntities(c.clone().subtract(0, 1.5, 0), RAIO_GANCHO, 4.0, RAIO_GANCHO)) {
            if (!pl.inimigoArea(e, p)) continue;
            LivingEntity le = (LivingEntity) e;
            Location el = le.getLocation();
            double dx = el.getX() - c.getX(), dz = el.getZ() - c.getZ();
            if (dx * dx + dz * dz > RAIO_GANCHO * RAIO_GANCHO || !atingidos.add(le.getUniqueId())) continue;
            if (!pl.causarDano(le, DANO_GANCHO * f, p)) continue;
            le.setFireTicks(Math.max(le.getFireTicks(), 60));
            Vector fora = new Vector(dx, 0, dz);
            fora = fora.lengthSquared() > 0.01 ? fora.normalize().multiply(0.25) : frente.clone().multiply(0.25);
            le.setVelocity(fora.setY(1.15));
            Location em = el.clone().add(0, le.getHeight() * 0.6, 0);
            pulsoPc(em, b(Material.ORANGE_STAINED_GLASS), 0.2f, 1.4f, 4, LARANJA);
            Fx.p(w, Particle.LAVA, em, 4, 0.2, 0.2, 0.2, 0);
            w.playSound(em, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
            pl.cena.lutGanchoAcerto(em);
        }
    }

    // =============================================================== 3. Soco Sismico

    boolean sismico(Player p) {
        UUID id = p.getUniqueId();
        if (!emSequencia.add(id)) return false;
        double f = pl.fator(p);
        World w = p.getWorld();
        Location ini = p.getLocation();
        Vector dir = frenteHorizontal(ini);
        p.swingMainHand();
        w.playSound(ini, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);
        w.playSound(ini, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.7f);
        w.playSound(ini, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);
        Poderes.barra(p, "§6§lSOCO SISMICO!");
        BlockData chao0 = Poderes.chaoDe(ini);
        Location golpe = ini.clone().add(dir.clone().multiply(0.9)).add(0, 0.3, 0);
        pl.fx.pulso(golpe, b(Material.WHITE_STAINED_GLASS), null, 0.3f, 2.2f, 4, QUENTE);
        pl.fx.anel(ini.clone().add(0, 0.1, 0), chao0, 2.6, 5, 12, 0.4f, null);
        pl.fx.estilhacos(golpe, new BlockData[] {chao0, b(Material.COBBLESTONE), b(Material.GRAVEL)}, 10, 2.5, 14, 0.35f);
        Fx.p(w, Particle.EXPLOSION, golpe, 1, 0, 0, 0, 0);
        Set<UUID> atingidos = new HashSet<>();
        final int passos = 14;
        final int[] k = {0};
        final Location[] antes = {Cenas.chao(golpe)};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            k[0]++;
            if (!p.isOnline() || k[0] > passos) {
                tarefa.cancel();
                emSequencia.remove(id);
                if (p.isOnline()) fimSismico(antes[0]);
                return;
            }
            Location pt = ini.clone().add(dir.clone().multiply(0.9 + k[0]));
            pt.setY(antes[0].getY() + 0.5);
            Location g = Cenas.chao(pt);
            if (Math.abs(g.getY() - antes[0].getY()) > 2.5 || !g.clone().add(0, 0.5, 0).getBlock().isPassable()) {
                tarefa.cancel();
                emSequencia.remove(id);
                fimSismico(antes[0]);
                return;
            }
            BlockData terra = Poderes.chaoDe(g);
            // a rachadura no chao (PC): risco escuro de um passo ao outro
            Location de = antes[0].clone().add(0, 0.03, 0);
            Vector seg = g.toVector().subtract(antes[0].toVector());
            if (seg.lengthSquared() > 0.01) {
                BlockDisplay rach = pl.vis.bloco(de, b(Material.BLACK_CONCRETE), 34, null);
                double len = seg.length();
                Visuais.barra(rach, de, seg, len, 0.17, 0);
                depois(24, () -> Visuais.barra(rach, de, seg, len, 0.001, 8));
            }
            Vector l = lado(dir);
            for (int i = 0; i < 3; i++) {
                Location base = g.clone().add(l.clone().multiply(rnd().nextDouble(-0.8, 0.8))).add(dir.clone().multiply(rnd().nextDouble(-0.3, 0.3)));
                pl.fx.pilar(base.add(0, -0.3, 0), terra, rnd().nextDouble(0.9, 1.6), rnd().nextDouble(0.5, 0.8), rnd().nextDouble(0.15, 0.5), 3, 6, null);
            }
            Fx.p(w, Particle.BLOCK, g.clone().add(0, 0.3, 0), 12, 0.4, 0.2, 0.4, 0, terra);
            if (k[0] % 3 == 0) w.playSound(g, Sound.BLOCK_DEEPSLATE_BREAK, 0.9f, 0.6f);
            pl.cena.lutSismico(g, terra);
            for (Entity e : w.getNearbyEntities(g.clone().add(0, 1, 0), 1.8, 2.0, 1.8)) {
                if (!pl.inimigoArea(e, p) || !atingidos.add(e.getUniqueId())) continue;
                LivingEntity le = (LivingEntity) e;
                if (!pl.causarDano(le, DANO_SISMICO * f, p)) continue;
                le.setVelocity(dir.clone().multiply(0.3).setY(1.0));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            antes[0] = g;
        }, 1L, 1L);
        return true;
    }

    private void fimSismico(Location g) {
        World w = g.getWorld();
        BlockData terra = Poderes.chaoDe(g);
        Location c = g.clone().add(0, 0.4, 0);
        pl.fx.pulso(c, b(Material.WHITE_STAINED_GLASS), null, 0.4f, 3.0f, 4, QUENTE);
        pl.fx.estilhacos(c, new BlockData[] {terra, terra, b(Material.COBBLESTONE)}, 12, 3.5, 16, 0.4f);
        Fx.p(w, Particle.EXPLOSION, c, 2, 0.3, 0.1, 0.3, 0);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
    }

    // =============================================================== 4. Punhos em Chamas

    boolean furiosa(Player p) {
        Long ate = furiaAte.get(p.getUniqueId());
        return ate != null && ate > System.currentTimeMillis();
    }

    boolean furia(Player p) {
        UUID id = p.getUniqueId();
        if (furiosa(p)) {
            Poderes.barra(p, "§cSeus punhos ja estao em chamas!");
            return false;
        }
        terminarFuria(id, false);
        World w = p.getWorld();
        furiaAte.put(id, System.currentTimeMillis() + FURIA_TICKS * 50L);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, FURIA_TICKS, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, FURIA_TICKS, 1));
        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
        Poderes.barra(p, "§4§lPUNHOS EM CHAMAS! §7(10s - soque com a mao vazia)");
        pl.fx.texto(p.getEyeLocation().add(0, 0.7, 0), "§4§lFURIA!", 1.7f, 24);
        pl.fx.pulso(p.getLocation().add(0, 1, 0), b(Material.ORANGE_STAINED_GLASS), b(Material.SHROOMLIGHT), 0.4f, 3.2f, 5, VERMELHO);
        pl.fx.anel(p.getLocation().add(0, 0.15, 0), b(Material.MAGMA_BLOCK), 3.2, 6, 14, 0.38f, LARANJA);
        Fx.p(w, Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.08);
        pl.cena.lutFuria(p, FURIA_TICKS);
        // os punhos pegando fogo (PC): miolo brilhante + casca de fogo em cada mao
        List<BlockDisplay> maos = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            boolean direita = i % 2 == 0, miolo = i < 2;
            BlockDisplay d = pl.vis.bloco(mao(p, direita), b(miolo ? Material.SHROOMLIGHT : Material.ORANGE_STAINED_GLASS),
                    FURIA_TICKS + 10, miolo ? AMARELO : VERMELHO);
            Visuais.escala(d, miolo ? 0.22f : 0.4f, 0f, 0);
            maos.add(d);
        }
        furiaMaos.put(id, maos);
        final int[] t = {0};
        BukkitTask tarefa = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            t[0]++;
            if (!p.isOnline() || p.isDead() || t[0] > FURIA_TICKS || pl.semPoderes(p.getWorld()) || pl.classe(p) != Classe.LUTADOR) {
                terminarFuria(id, p.isOnline());
                return;
            }
            Location md = mao(p, true), me = mao(p, false);
            for (int i = 0; i < maos.size(); i++) {
                BlockDisplay d = maos.get(i);
                if (!d.isValid()) continue;
                d.teleport(i % 2 == 0 ? md : me);
                if (i >= 2 && t[0] % 2 == 0) Visuais.escala(d, (float) (0.38 + Math.sin(t[0] * 0.5) * 0.05), t[0] * 0.35f, 2);
            }
            World mw = p.getWorld();
            if (t[0] % 2 == 0) {
                Fx.p(mw, Particle.FLAME, md, 1, 0.06, 0.06, 0.06, 0.01);
                Fx.p(mw, Particle.FLAME, me, 1, 0.06, 0.06, 0.06, 0.01);
            }
            if (t[0] % 6 == 0) Fx.p(mw, Particle.SMALL_FLAME, p.getLocation().add(0, 0.1, 0), 6, 0.4, 0.02, 0.4, 0.01);
            if (t[0] % 30 == 0) mw.playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.6f, 1.2f);
            if (t[0] % 20 == 0 && t[0] < FURIA_TICKS) Poderes.barra(p, "§4§lPUNHOS EM CHAMAS §7" + (FURIA_TICKS - t[0]) / 20 + "s");
        }, 1L, 1L);
        furiaTarefa.put(id, tarefa);
        return true;
    }

    private void terminarFuria(UUID id, boolean avisar) {
        furiaAte.remove(id);
        BukkitTask t = furiaTarefa.remove(id);
        if (t != null) t.cancel();
        List<BlockDisplay> maos = furiaMaos.remove(id);
        if (maos != null) for (BlockDisplay d : maos) pl.vis.remover(d);
        pl.cena.lutFuriaFim(id);
        if (avisar) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Poderes.barra(p, "§7Os punhos em chamas apagaram.");
                Fx.p(p.getWorld(), Particle.SMOKE, p.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** Cada soco forte na Furia explode em fogo em volta do alvo. */
    private void explosaoFuria(Player p, LivingEntity alvo, double dano) {
        if (!p.isOnline() || !alvo.isValid() || !alvo.getWorld().equals(p.getWorld())) return;
        Location c = alvo.getLocation().add(0, alvo.getHeight() * 0.5, 0);
        World w = c.getWorld();
        Fx.p(w, Particle.FLAME, c, 18, 0.4, 0.4, 0.4, 0.08);
        Fx.p(w, Particle.LAVA, c, 3, 0.2, 0.2, 0.2, 0);
        w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.4f);
        pulsoPc(c, b(Material.ORANGE_STAINED_GLASS), 0.3f, 2.0f, 4, LARANJA);
        pulsoPc(c, b(Material.SHROOMLIGHT), 0.15f, 1.0f, 3, null);
        pl.cena.lutFuriaSoco(c);
        if (!(alvo instanceof Player)) alvo.setFireTicks(Math.max(alvo.getFireTicks(), 40));
        for (LivingEntity le : pl.hab.inimigosPerto(c, 2.6, 2.0, p)) {
            if (le.equals(alvo)) continue;
            if (!pl.causarDano(le, dano, p)) continue;
            le.setFireTicks(Math.max(le.getFireTicks(), 40));
            Vector v = le.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (v.lengthSquared() > 0.01) le.setVelocity(v.normalize().multiply(0.5).setY(0.3));
        }
    }

    // =============================================================== 5. Punho Meteoro

    boolean meteoro(Player p) {
        UUID id = p.getUniqueId();
        if (!emSequencia.add(id)) return false;
        double f = pl.fator(p);
        World w = p.getWorld();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 34, 3, false, false));
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 0.6f);
        w.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
        Poderes.barra(p, "§e§lPUNHO...");
        BlockDisplay esfera = pl.vis.bloco(mao(p, true), b(Material.ORANGE_STAINED_GLASS), 50, LARANJA);
        BlockDisplay nucleo = pl.vis.bloco(mao(p, true), b(Material.SHROOMLIGHT), 50, AMARELO);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!p.isOnline() || p.isDead() || pl.semPoderes(p.getWorld())) {
                tarefa.cancel();
                emSequencia.remove(id);
                pl.vis.remover(esfera);
                pl.vis.remover(nucleo);
                return;
            }
            t[0] += 2;
            Location m = mao(p, true);
            float s = (float) Math.min(0.9, 0.15 + t[0] * 0.025);
            esfera.teleport(m);
            nucleo.teleport(m);
            Visuais.escala(esfera, s, t[0] * 0.4f, 2);
            Visuais.escala(nucleo, s * 0.55f, -t[0] * 0.5f, 2);
            World mw = p.getWorld();
            // a energia sendo puxada para o punho
            for (int i = 0; i < 4; i++) {
                double a = rnd().nextDouble(Math.PI * 2), bb = rnd().nextDouble(-0.8, 1.2), r = 1.8;
                Vector off = new Vector(Math.cos(a) * Math.cos(bb), Math.sin(bb), Math.sin(a) * Math.cos(bb)).multiply(r);
                Location de = m.clone().add(off);
                Vector para = off.clone().multiply(-1);
                Fx.p(mw, i % 2 == 0 ? Particle.FLAME : Particle.END_ROD, de, 0, para.getX(), para.getY(), para.getZ(), 0.09);
            }
            BlockData terra = Poderes.chaoDe(p.getLocation());
            Fx.p(mw, Particle.DUST_PILLAR, p.getLocation().add(0, 0.1, 0), 4, 1.2, 0.05, 1.2, 0, terra);
            if (t[0] % 8 == 0) {
                pl.fx.anel(p.getLocation().add(0, 0.1, 0), terra, 1.6 + t[0] * 0.05, 5, 10, 0.3f, null);
                mw.playSound(m, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 0.6f + t[0] * 0.03f);
            }
            pl.cena.lutMeteoroCarga(p, m, t[0]);
            if (t[0] < 30) return;
            tarefa.cancel();
            pl.vis.remover(esfera);
            pl.vis.remover(nucleo);
            soltarMeteoro(p, f, id);
        }, 0L, 2L);
        return true;
    }

    /** Pecas do punho gigante (no espaco do punho: +Z = para frente, +Y = para cima). */
    private static final float[][] PUNHO = {
            // centro x, y, z, tamanho x, y, z, tipo (0 = mao, 1 = miolo, 2 = casca)
            {0f, 0f, -0.15f, 1.6f, 1.3f, 1.3f, 0},           // costas da mao
            {-0.6f, 0.28f, 0.62f, 0.38f, 0.5f, 0.55f, 0},    // dedos (fileira de cima: os nos)
            {-0.2f, 0.3f, 0.66f, 0.38f, 0.52f, 0.55f, 0},
            {0.2f, 0.3f, 0.66f, 0.38f, 0.52f, 0.55f, 0},
            {0.6f, 0.28f, 0.62f, 0.38f, 0.5f, 0.55f, 0},
            {-0.4f, -0.25f, 0.55f, 0.75f, 0.45f, 0.5f, 0},   // dedos dobrados (embaixo)
            {0.4f, -0.25f, 0.55f, 0.75f, 0.45f, 0.5f, 0},
            {0.9f, -0.3f, 0.2f, 0.36f, 0.36f, 0.75f, 0},     // dedao
            {0f, 0f, -1.2f, 1.0f, 0.95f, 1.1f, 0},           // pulso
            {0f, 0f, 0f, 1.0f, 0.8f, 1.0f, 1},              // miolo brilhante
            {0f, 0.02f, -0.1f, 2.1f, 1.75f, 2.5f, 2},        // casca de energia em volta
    };

    /** Tamanho do punho gigante (as medidas da tabela sao para um punho de 1x). */
    static final float PUNHO_ESCALA = 1.5f;

    private List<BlockDisplay> punhoGigante(Location c, Vector dir) {
        Quaternionf q = olhando(dir);
        List<BlockDisplay> ds = new ArrayList<>();
        final float e = PUNHO_ESCALA;
        for (float[] pc : PUNHO) {
            int tipo = (int) pc[6];
            Material m = tipo == 1 ? Material.SHROOMLIGHT : tipo == 2 ? Material.YELLOW_STAINED_GLASS : Material.ORANGE_STAINED_GLASS;
            BlockDisplay d = pl.vis.bloco(c, b(m), 60, tipo == 2 ? null : (tipo == 1 ? AMARELO : LARANJA));
            d.setTeleportDuration(1);
            Vector3f centro = new Vector3f(pc[0] * e, pc[1] * e, pc[2] * e);
            d.setTransformation(caixa(centro, new Vector3f(0.05f), q));
            Transformation fim = caixa(centro, new Vector3f(pc[3] * e, pc[4] * e, pc[5] * e), q);
            depois(1, () -> Efeitos.mover(d, fim, 2));
            ds.add(d);
        }
        return ds;
    }

    private void soltarMeteoro(Player p, double f, UUID id) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        Poderes.barra(p, "§6§lMETEORO!!!");
        pl.fx.texto(olho.clone().add(dir.clone().multiply(1.6)).add(0, 0.9, 0), "§6§lMETEORO!", 2.0f, 26);
        w.playSound(olho, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.2f);
        w.playSound(olho, Sound.ITEM_MACE_SMASH_AIR, 1f, 0.6f);
        w.playSound(olho, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
        Fx.p(w, Particle.SONIC_BOOM, olho.clone().add(dir.clone().multiply(1.3)), 1);
        p.swingMainHand();
        p.setVelocity(dir.clone().multiply(-0.35).setY(0.1));
        double dist = alcance(olho, dir, 18.0);
        Location inicio = olho.clone().add(dir.clone().multiply(1.2)).subtract(0, 0.3, 0);
        List<BlockDisplay> punho = punhoGigante(inicio, dir);
        Object cel = pl.cena.lutMeteoroPunho(inicio, dir);
        // prop 3D: o punho de energia (modelo de ~1,2 bloco; aqui 3x) girando devagar em volta da direcao
        java.util.List<Player> plateia3d = pl.props.plateia(inicio);
        final org.bukkit.entity.ItemDisplay punho3d = pl.props.prop(inicio, "punho_f0", 60, plateia3d);
        if (punho3d != null) {
            punho3d.setTeleportDuration(1);
            for (BlockDisplay d : punho) pl.props.esconder(plateia3d, d);
            Props3D.pose(punho3d, dir, 0.3f, 0f, 0);
            depois(1, () -> Props3D.pose(punho3d, dir, 3.0f, 0f, 2));
        }
        Set<UUID> atingidos = new HashSet<>();
        final double passo = 1.8;
        final int[] k = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            k[0]++;
            double andado = Math.min(dist, k[0] * passo);
            Location pos = inicio.clone().add(dir.clone().multiply(andado));
            if (!p.isOnline()) {
                tarefa.cancel();
                emSequencia.remove(id);
                for (BlockDisplay d : punho) pl.vis.remover(d);
                pl.props.remover(punho3d);
                pl.cena.lutMeteoroFim(cel, null);
                return;
            }
            for (BlockDisplay d : punho) if (d.isValid()) d.teleport(pos);
            if (punho3d != null && punho3d.isValid()) {
                Props3D.mover(punho3d, pos);
                Props3D.pose(punho3d, dir, 3.0f, k[0] * 0.25f, 1);
                if (k[0] % 2 == 0) Props3D.quadro(punho3d, "punho_f" + ((k[0] / 2) % 2));
            }
            pl.cena.lutMeteoroMover(cel, pos);
            Fx.p(w, Particle.FLAME, pos, 10, 0.6, 0.6, 0.6, 0.04);
            Fx.p(w, Particle.DUST, pos, 6, 0.8, 0.8, 0.8, 0, new Particle.DustOptions(LARANJA, 2.0f));
            Fx.p(w, Particle.CLOUD, pos.clone().subtract(dir.clone().multiply(1.4)), 4, 0.4, 0.4, 0.4, 0.02);
            if (k[0] % 2 == 0) {
                anelVerticalPc(pos.clone().subtract(dir.clone().multiply(0.8)), dir, 2.0, 14, 0.34f, 4, AMARELO, b(Material.YELLOW_STAINED_GLASS));
                pl.cena.lutAnel(pos.clone().subtract(dir.clone().multiply(0.8)), dir, 2.0, AMARELO);
                w.playSound(pos, Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 0.6f);
            }
            for (Entity e : w.getNearbyEntities(pos, 2.6, 2.6, 2.6)) {
                if (!pl.inimigoArea(e, p) || !atingidos.add(e.getUniqueId())) continue;
                LivingEntity le = (LivingEntity) e;
                if (!pl.causarDano(le, DANO_METEORO * f, p)) continue;
                le.setVelocity(dir.clone().multiply(1.6).setY(0.55));
                Location em = le.getLocation().add(0, le.getHeight() * 0.5, 0);
                pulsoPc(em, b(Material.WHITE_STAINED_GLASS), 0.2f, 1.6f, 3, AMARELO);
                w.playSound(em, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.5f);
            }
            if (andado < dist) return;
            tarefa.cancel();
            emSequencia.remove(id);
            for (BlockDisplay d : punho) {
                Transformation tr = d.getTransformation();
                Efeitos.mover(d, new Transformation(tr.getTranslation(), tr.getLeftRotation(), new Vector3f(0.001f), tr.getRightRotation()), 3);
            }
            if (punho3d != null && punho3d.isValid()) {
                Props3D.pose(punho3d, dir, 4.2f, k[0] * 0.25f, 3);   // estoura: cresce e some
                depois(4, () -> pl.props.remover(punho3d));
            }
            depois(4, () -> {
                for (BlockDisplay d : punho) pl.vis.remover(d);
            });
            pl.cena.lutMeteoroFim(cel, pos);
            impactoMeteoro(p, pos, f, atingidos);
        }, 1L, 1L);
    }

    private void impactoMeteoro(Player p, Location c, double f, Set<UUID> jaAtingidos) {
        World w = c.getWorld();
        BlockData terra = Poderes.chaoDe(c);
        pl.fx.pulso(c, b(Material.ORANGE_STAINED_GLASS), b(Material.SHROOMLIGHT), 0.6f, 5.0f, 6, LARANJA);
        pl.fx.estilhacos(c, new BlockData[] {terra, b(Material.MAGMA_BLOCK), b(Material.ORANGE_STAINED_GLASS), b(Material.COBBLESTONE)}, 18, 5.0, 18, 0.45f);
        Location ch = Cenas.chao(c.clone().add(0, 0.5, 0));
        if (Math.abs(ch.getY() - c.getY()) < 3) pl.fx.anel(ch.add(0, 0.15, 0), terra, 5.5, 7, 18, 0.45f, LARANJA);
        Fx.p(w, Particle.EXPLOSION_EMITTER, c, 1, 0, 0, 0, 0);
        Fx.p(w, Particle.FLAME, c, 60, 1.5, 1.2, 1.5, 0.12);
        Fx.p(w, Particle.LAVA, c, 10, 1.0, 0.6, 1.0, 0);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        w.playSound(c, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 0.8f);
        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
        pl.cena.lutMeteoroImpacto(c);
        if (!p.isOnline()) return;
        for (LivingEntity le : pl.hab.inimigosPerto(c, 4.5, 3.5, p)) {
            le.setNoDamageTicks(0);
            if (!pl.causarDano(le, DANO_METEORO_EXPLOSAO * f, p)) continue;
            le.setFireTicks(Math.max(le.getFireTicks(), 60));
            Vector v = le.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(rnd().nextDouble(-1, 1), 0, rnd().nextDouble(-1, 1));
            le.setVelocity(v.normalize().multiply(jaAtingidos.contains(le.getUniqueId()) ? 0.4 : 1.0).setY(0.6));
        }
    }

    // =============================================================== limpeza

    /** Desfaz tudo que esta ativo do jogador (troca de classe, minigame, saida, morte). */
    void limpar(Player p) {
        UUID id = p.getUniqueId();
        terminarFuria(id, false);
        emSequencia.remove(id);
        golpe.remove(id);
        nivelCache.remove(id);
    }

    void limparTodos() {
        for (UUID id : new ArrayList<>(furiaTarefa.keySet())) terminarFuria(id, false);
        for (UUID id : new ArrayList<>(furiaMaos.keySet())) terminarFuria(id, false);
        furiaAte.clear();
        emSequencia.clear();
        golpe.clear();
        nivelCache.clear();
    }
}
