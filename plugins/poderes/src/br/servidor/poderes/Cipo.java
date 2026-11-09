package br.servidor.poderes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Classe do Cipo (04/11/2026): mestre das trepadeiras. Chicoteia e puxa, se balanca como o Tarzan, laca varios
 * inimigos de uma vez, planta uma flor carnivora gigante e transforma o lugar numa selva viva que estrangula.
 * Tudo com particulas (Java e Bedrock) e blocos de enfeite (Java).
 */
final class Cipo {

    static final double DANO_CHICOTE = 5.0, DANO_LACO = 3.0, DANO_FLOR = 2.5, DANO_SELVA = 3.0;

    private final Poderes pl;

    Cipo(Poderes pl) {
        this.pl = pl;
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    private static final Particle.DustOptions VERDE = new Particle.DustOptions(Color.fromRGB(60, 140, 40), 1.2f);
    private static final Color BRILHO = Color.fromRGB(90, 200, 60);

    private static BlockData b(Material m) {
        return m.createBlockData();
    }

    /** Linha de cipo entre dois pontos: particulas (todo mundo ve) + uma barra de musgo (Java). */
    private void cipo(Location a, Location c, int vida) {
        Vector d = c.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.05) return;
        Vector u = d.clone().normalize();
        World w = a.getWorld();
        BlockData vinha = b(Material.VINE);
        for (double k = 0; k < len; k += 0.45) {
            Location p = a.clone().add(u.clone().multiply(k));
            Fx.p(w, Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0, VERDE);
            if (rnd().nextInt(3) == 0) Fx.p(w, Particle.BLOCK, p, 1, 0.05, 0.05, 0.05, 0, vinha);
        }
        pl.fx.corte(a, c, b(Material.MOSS_BLOCK), 0.09, vida, BRILHO);
    }

    private Location mao(Player p) {
        Location o = p.getEyeLocation();
        Vector lado = new Vector(-o.getDirection().getZ(), 0, o.getDirection().getX()).normalize().multiply(0.35);
        return o.add(0, -0.35, 0).add(lado);
    }

    // ------------------------------------------------------------------ 1. Chicote de Cipo

    boolean chicote(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        double alcance = 10;
        RayTraceResult r = w.rayTrace(olho, dir, alcance, FluidCollisionMode.NEVER, true, 0.6,
                e -> e instanceof LivingEntity && !e.equals(p) && pl.inimigoArea(e, p));
        Location fim = r == null ? olho.clone().add(dir.clone().multiply(alcance)) : r.getHitPosition().toLocation(w);
        cipo(mao(p), fim, 8);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        w.playSound(fim, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        if (r != null && r.getHitEntity() instanceof LivingEntity alvo) {
            double f = pl.fator(p);
            if (pl.causarDano(alvo, DANO_CHICOTE * f, p)) {
                Vector puxa = p.getLocation().toVector().subtract(alvo.getLocation().toVector());
                if (puxa.lengthSquared() > 0.01) alvo.setVelocity(puxa.normalize().multiply(1.15).setY(0.38));
                Fx.p(w, Particle.BLOCK, alvo.getLocation().add(0, alvo.getHeight() / 2, 0), 20, 0.3, 0.4, 0.3, 0, b(Material.VINE));
                w.playSound(alvo.getLocation(), Sound.BLOCK_VINE_BREAK, 1.2f, 0.6f);
                Poderes.barra(p, "§a§lCHICOTE! §7Puxou o alvo para você.");
            }
        } else Poderes.barra(p, "§a§lCHICOTE! §7(errou)");
        return true;
    }

    // ------------------------------------------------------------------ 2. Balanco do Tarzan

    boolean balanco(Player p) {
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        RayTraceResult r = w.rayTraceBlocks(olho, olho.getDirection(), 32, FluidCollisionMode.NEVER, true);
        if (r == null || r.getHitBlock() == null) {
            Poderes.barra(p, "§7Nada para agarrar o cipó (mire num bloco até 32 blocos).");
            return false;
        }
        Location ancora = r.getHitPosition().toLocation(w);
        pl.semQueda(p, 6000L);
        w.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 0.6f);
        w.playSound(ancora, Sound.BLOCK_VINE_PLACE, 1.2f, 0.7f);
        Fx.p(w, Particle.BLOCK, ancora, 25, 0.3, 0.3, 0.3, 0, b(Material.VINE));
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!p.isOnline() || p.isDead() || !p.getWorld().equals(w) || t[0] > 18) {
                tarefa.cancel();
                return;
            }
            Vector para = ancora.toVector().subtract(p.getLocation().add(0, 1, 0).toVector());
            double d = para.length();
            if (d < 2.2) {
                // chegou: um impulso para cima/frente para sair pendurado
                p.setVelocity(p.getVelocity().multiply(0.6).setY(0.45));
                tarefa.cancel();
                return;
            }
            Vector v = para.normalize().multiply(Math.min(1.9, 0.75 + d * 0.06));
            v.setY(v.getY() + 0.12);
            p.setVelocity(v);
            if (t[0] % 2 == 0) cipo(mao(p), ancora, 3);
            if (t[0] % 4 == 0) Fx.p(w, Particle.FALLING_SPORE_BLOSSOM, p.getLocation().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0);
            t[0]++;
        }, 0L, 1L);
        Poderes.barra(p, "§2§lBALANÇO! §7Lá vai você no cipó.");
        return true;
    }

    // ------------------------------------------------------------------ 3. Laco de Espinhos

    boolean laco(Player p) {
        Location c = pl.hab.mira(p, 22);
        World w = c.getWorld();
        double f = pl.fator(p);
        cipo(mao(p), c.clone().add(0, 1, 0), 10);
        pl.fx.anel(c.clone().add(0, 0.2, 0), b(Material.MOSS_BLOCK), 3.6, 6, 16, 0.3f, BRILHO);
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28;
            Fx.p(w, Particle.BLOCK, c.clone().add(Math.cos(a) * 3.6, 0.4, Math.sin(a) * 3.6), 3, 0.1, 0.2, 0.1, 0, b(Material.VINE));
        }
        w.playSound(c, Sound.BLOCK_VINE_STEP, 1.4f, 0.5f);
        w.playSound(c, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 0.8f);
        List<LivingEntity> presos = pl.hab.inimigosPerto(c, 4.0, 3, p);
        for (LivingEntity le : presos) {
            Vector v = c.toVector().subtract(le.getLocation().toVector()).setY(0);
            if (v.lengthSquared() > 0.25) le.setVelocity(v.normalize().multiply(0.8).setY(0.2));
            if (pl.causarDano(le, DANO_LACO * f, p)) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 3));
        }
        presos.removeIf(le -> !le.isValid() || le.isDead());
        // os amarrados ficam presos uns aos outros pelos espinhos (3 s, sangrando)
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (t[0] > 60) {
                tarefa.cancel();
                return;
            }
            LivingEntity ant = null;
            for (LivingEntity le : presos) {
                if (!le.isValid() || le.isDead()) continue;
                if (t[0] % 4 == 0 && ant != null) cipo(ant.getLocation().add(0, 0.9, 0), le.getLocation().add(0, 0.9, 0), 4);
                if (t[0] % 20 == 10) pl.causarDano(le, DANO_LACO * f * 0.35, p);
                ant = le;
            }
            t[0]++;
        }, 0L, 1L);
        Poderes.barra(p, "§2§lLAÇO DE ESPINHOS! §7" + (presos.isEmpty() ? "Ninguém laçado." : presos.size() + " inimigo(s) amarrado(s)."));
        return true;
    }

    // ------------------------------------------------------------------ 4. Flor Carnivora

    boolean flor(Player p) {
        Location c = pl.hab.mira(p, 18);
        World w = c.getWorld();
        double f = pl.fator(p);
        // a flor: planta de jarro gigante com folhas em volta (Java) + esporos (todos)
        BlockDisplay boca = pl.vis.bloco(c.clone().add(0, 1.2, 0), b(Material.PITCHER_PLANT), 210, BRILHO);
        Visuais.escala(boca, 2.4f, 0f, 0);
        List<BlockDisplay> folhas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + 0.4;
            BlockDisplay fo = pl.vis.bloco(c.clone().add(Math.cos(a) * 1.1, 0.35, Math.sin(a) * 1.1), b(Material.BIG_DRIPLEAF), 210, BRILHO);
            Visuais.escala(fo, 1.3f, (float) a, 0);
            folhas.add(fo);
        }
        w.playSound(c, Sound.BLOCK_BIG_DRIPLEAF_PLACE, 1.4f, 0.5f);
        w.playSound(c, Sound.BLOCK_ROOTED_DIRT_PLACE, 1.2f, 0.6f);
        Fx.p(w, Particle.BLOCK, c.clone().add(0, 0.3, 0), 40, 0.8, 0.2, 0.8, 0, b(Material.ROOTED_DIRT));
        // prop 3D: a flor com mandibula (f0 fechada, f1 aberta); quem a ve nao ve a planta de vidro
        java.util.List<Player> plateia3d = pl.props.plateia(c);
        final org.bukkit.entity.ItemDisplay flor3d = pl.props.prop(c.clone().add(0, 1.0, 0), "flor_f1", 210, plateia3d);
        if (flor3d != null) {
            pl.props.esconder(plateia3d, boca);
            for (BlockDisplay fo : folhas) pl.props.esconder(plateia3d, fo);
            Props3D.escala(flor3d, 0.3f, 0f, 0);
            Bukkit.getScheduler().runTaskLater(pl, () -> Props3D.escala(flor3d, 2.2f, 0f, 6), 1L);
        }
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (t[0] > 200 || !p.isOnline()) {
                tarefa.cancel();
                pl.props.remover(flor3d);
                Fx.p(w, Particle.COMPOSTER, c.clone().add(0, 1, 0), 30, 0.8, 0.8, 0.8, 0);
                return;
            }
            if (flor3d != null && flor3d.isValid()) {
                // vira devagar procurando; a boca fica aberta (f1) e so fecha na mordida
                Props3D.escala(flor3d, 2.2f + 0.05f * (float) Math.sin(t[0] * 0.3), t[0] * 0.03f, 1);
            }
            if (t[0] % 5 == 0) Fx.p(w, Particle.SPORE_BLOSSOM_AIR, c.clone().add(0, 1.5, 0), 6, 1.2, 0.8, 1.2, 0);
            if (t[0] % 20 == 0) {
                boolean mordeu = false;
                for (LivingEntity le : pl.hab.inimigosPerto(c, 6, 3, p)) {
                    Vector v = c.toVector().subtract(le.getLocation().toVector()).setY(0);
                    double d = v.length();
                    if (d > 2.2 && d > 0.1) le.setVelocity(le.getVelocity().add(v.normalize().multiply(0.45)));
                    else if (pl.causarDano(le, DANO_FLOR * f, p)) {
                        mordeu = true;
                        le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                        Fx.p(w, Particle.BLOCK, le.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0, b(Material.MOSS_BLOCK));
                    }
                }
                if (mordeu) {
                    w.playSound(c, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
                    w.playSound(c, Sound.ENTITY_GENERIC_EAT, 1.2f, 0.5f);
                    if (flor3d != null && flor3d.isValid()) {
                        Props3D.quadro(flor3d, "flor_f0");   // fecha a boca na mordida
                        Bukkit.getScheduler().runTaskLater(pl, () -> Props3D.quadro(flor3d, "flor_f1"), 6L);
                    }
                    if (boca.isValid()) {
                        Visuais.escala(boca, 2.9f, 0f, 2);
                        Bukkit.getScheduler().runTaskLater(pl, () -> {
                            if (boca.isValid()) Visuais.escala(boca, 2.4f, 0f, 3);
                        }, 3L);
                    }
                }
            }
            t[0]++;
        }, 0L, 1L);
        Poderes.barra(p, "§c§lFLOR CARNÍVORA! §7Ela puxa e morde quem chegar perto (10s).");
        return true;
    }

    // ------------------------------------------------------------------ 5. Selva Viva

    boolean selva(Player p) {
        Location c = p.getLocation();
        World w = c.getWorld();
        double f = pl.fator(p);
        double raio = 9;
        BlockData[] tipos = {b(Material.MOSS_BLOCK), b(Material.JUNGLE_LEAVES), b(Material.MANGROVE_ROOTS), b(Material.AZALEA_LEAVES)};
        w.playSound(c, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.6f, 0.5f);
        w.playSound(c, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.4f, 0.5f);
        w.playSound(c, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.6f, 0.5f);
        for (int i = 0; i < 18; i++) {
            double a = rnd().nextDouble(Math.PI * 2), r = rnd().nextDouble(1.5, raio);
            pl.fx.pilar(c.clone().add(Math.cos(a) * r, -0.3, Math.sin(a) * r), tipos[i % tipos.length], rnd().nextDouble(1.8, 3.6), 0.35,
                    rnd().nextDouble(-0.4, 0.4), 5, 70, BRILHO);
        }
        pl.fx.anel(c.clone().add(0, 0.2, 0), b(Material.MOSS_BLOCK), raio, 8, 28, 0.4f, BRILHO);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));
        List<LivingEntity> pegos = pl.hab.inimigosPerto(c, raio, 4, p);
        for (LivingEntity le : pegos) le.setVelocity(new Vector(0, 0.75, 0));
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (t[0] > 80) {
                tarefa.cancel();
                return;
            }
            if (t[0] % 6 == 0) Fx.p(w, Particle.FALLING_SPORE_BLOSSOM, c.clone().add(0, 3, 0), 20, raio * 0.6, 1, raio * 0.6, 0);
            for (LivingEntity le : pegos) {
                if (!le.isValid() || le.isDead()) continue;
                // pendurado pelos cipos, estrangulado no ar
                if (t[0] > 10) le.setVelocity(new Vector(0, 0.02, 0));
                if (t[0] % 3 == 0) cipo(le.getLocation().add(0, le.getHeight() * 0.6, 0), le.getLocation().add(0, 4.5, 0), 4);
                if (t[0] % 20 == 15) {
                    if (pl.causarDano(le, DANO_SELVA * f, p)) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
                    Fx.p(w, Particle.BLOCK, le.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0, b(Material.VINE));
                }
            }
            t[0]++;
        }, 0L, 1L);
        Poderes.barra(p, "§a§lSELVA VIVA! §7" + (pegos.isEmpty() ? "A selva cresceu em volta de você." : pegos.size() + " inimigo(s) pendurado(s) pelos cipós."));
        return true;
    }
}
