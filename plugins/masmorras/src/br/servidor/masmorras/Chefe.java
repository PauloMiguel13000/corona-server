package br.servidor.masmorras;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Warden;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

/** O chefe vivo de uma masmorra: barra de vida, preso na arena, habilidades, fase furiosa. */
final class Chefe {

    final Masmorras pl;
    final Masmorra m;
    final Tipo t;
    final LivingEntity e;
    final BossBar barra;
    final List<Player> vendo = new ArrayList<>();
    final Random rnd = new Random();
    long proximaHab, semJogadorDesde = -1;
    boolean furioso, curou;
    // particularidades
    long inicioLuta = -1, proximaZona, escudoAte;
    int furiaNivel;
    boolean escudo70, escudo35, clonou;
    final List<java.util.UUID> guardas = new ArrayList<>();
    double vidaMax;
    /** Teias postas pela Rainha Aranha (para tirar depois). */
    final List<Block> teias = new ArrayList<>();
    /** Herobrine: o corpo que aparece (manequim com a cara do Steve), colado no chefe invisivel. */
    LivingEntity visual;
    org.bukkit.scheduler.BukkitTask visualTarefa;
    long sumidoAte;
    // particularidades novas (versao 3)
    boolean reviveu, cristais75, cristais40;
    final List<java.util.UUID> cristais = new ArrayList<>();
    long proximaOnda, proximaGravidade, proximoRelampago, proximoElem, reveladoAte;
    int elemento = -1;
    /** Blocos de gelo da prisao (para tirar depois). */
    final List<Block> gelos = new ArrayList<>();

    Chefe(Masmorras pl, Masmorra m, LivingEntity e, double vidaMax) {
        this.pl = pl;
        this.m = m;
        this.t = m.tipo;
        this.e = e;
        this.vidaMax = vidaMax;
        this.barra = BossBar.bossBar(Masmorras.txt("§c§l" + t.chefeNome + " §7— Nível " + t.nivel), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        this.proximaHab = System.currentTimeMillis() + 4000;
    }

    boolean vivo() {
        return e != null && e.isValid() && !e.isDead();
    }

    /** Jogadores que estao na arena (ou bem na porta). */
    List<Player> naArena() {
        List<Player> l = new ArrayList<>();
        Caixa a = m.arena().crescer(2);
        for (Player p : e.getWorld().getPlayers())
            if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && p.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && a.contem(p.getX(), p.getY(), p.getZ())) l.add(p);
        return l;
    }

    void anunciar(List<Player> l) {
        for (Player p : l) {
            p.showTitle(Title.title(Masmorras.txt("§4§l" + t.chefeNome), Masmorras.txt("§7" + t.nome + " — Nível " + t.nivel),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            p.playSound(e.getLocation(), "minecraft:entity.wither.spawn", 0.6f, 0.8f);
            if (t.esp != Tipo.Esp.NENHUMA) p.sendMessage(Masmorras.txt("§c§l⚠ " + t.chefeNome + ": §7" + t.esp.desc));
        }
    }

    /** A cada meio segundo. Devolve false quando o chefe deve ser tirado (ninguem por perto ha muito tempo). */
    boolean tick() {
        if (!vivo()) return false;
        long agora = System.currentTimeMillis();
        List<Player> ps = naArena();
        // barra de vida
        barra.progress((float) Math.max(0, Math.min(1, e.getHealth() / vidaMax)));
        List<Player> perto = new ArrayList<>();
        Caixa ver = m.arena().crescer(10);
        for (Player p : e.getWorld().getPlayers()) if (ver.contem(p.getX(), p.getY(), p.getZ())) perto.add(p);
        for (Player p : new ArrayList<>(vendo)) if (!perto.contains(p) || !p.isOnline()) {
            p.hideBossBar(barra);
            vendo.remove(p);
        }
        for (Player p : perto) if (!vendo.contains(p)) {
            p.showBossBar(barra);
            vendo.add(p);
        }
        efeitos();
        if (ps.isEmpty()) {
            if (semJogadorDesde < 0) semJogadorDesde = agora;
            return agora - semJogadorDesde < 60_000;
        }
        semJogadorDesde = -1;
        // preso na arena
        Caixa a = m.arena();
        if (!a.crescer(1).contem(e.getX(), e.getY(), e.getZ())) e.teleport(centro());
        // alvo
        Player alvo = maisPerto(ps);
        if (e instanceof Warden w) {
            for (Player p : ps) if (w.getAnger(p) < 90) w.increaseAnger(p, 90);
        } else if (e instanceof Mob mob) {
            if (mob.getTarget() == null || !(mob.getTarget() instanceof Player tp) || !ps.contains(tp)) mob.setTarget(alvo);
        }
        // furioso na metade da vida
        if (!furioso && e.getHealth() <= vidaMax / 2) {
            furioso = true;
            e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
            e.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, false));
            for (Player p : ps) {
                p.sendActionBar(Masmorras.txt("§c" + t.chefeNome + " ficou furioso!"));
                p.playSound(e.getLocation(), "minecraft:entity.ravager.roar", 1f, 0.7f);
            }
        }
        especial(ps, agora);
        if (agora >= proximaHab) {
            usarHabilidade(ps, alvo);
            long base = furioso ? 5000 : 8000;
            if (t.esp == Tipo.Esp.FRENESI) base = (long) (base * (0.35 + 0.65 * Math.max(0, e.getHealth() / vidaMax)));
            proximaHab = agora + base + rnd.nextInt(t.esp == Tipo.Esp.FRENESI ? 1500 : 3000);
        }
        // teias velhas somem
        if (!teias.isEmpty() && rnd.nextInt(4) == 0) tirarTeias(false);
        return true;
    }

    // ------------------------------------------------------------------ particularidades

    /** O escudo esta de pe (guardas vivos)? */
    boolean imune() {
        if (escudoAte <= 0) return false;
        boolean vivos = false;
        for (java.util.UUID id : guardas) {
            org.bukkit.entity.Entity g = org.bukkit.Bukkit.getEntity(id);
            if (g != null && g.isValid() && !g.isDead()) vivos = true;
        }
        if (!vivos || System.currentTimeMillis() > escudoAte) {
            escudoAte = 0;
            guardas.clear();
            for (Player p : naArena()) p.sendActionBar(Masmorras.txt("§b§lO escudo de " + t.chefeNome + " quebrou!"));
            e.getWorld().playSound(e.getLocation(), "minecraft:block.glass.break", 1.2f, 0.6f);
            return false;
        }
        return true;
    }

    private void escudo(List<Player> ps) {
        guardas.clear();
        String[] tipos = t.ajudantes != null && t.ajudantes.length > 0 ? t.ajudantes : t.lacaios;
        for (int i = 0; i < 3; i++) {
            Location ok = lugarLivre(e.getLocation().clone().add(rnd.nextInt(9) - 4, 0, rnd.nextInt(9) - 4));
            if (ok == null) continue;
            LivingEntity g = pl.lacaio(m, tipos[rnd.nextInt(tipos.length)], ok, true);
            if (g == null) continue;
            g.setGlowing(true);
            g.customName(Masmorras.txt("§bGuarda do Escudo"));
            guardas.add(g.getUniqueId());
        }
        if (guardas.isEmpty()) return;
        escudoAte = System.currentTimeMillis() + 25_000;
        for (Player p : ps) {
            p.sendMessage(Masmorras.txt("§b§l" + t.chefeNome + " ergueu um escudo! §7Mate os §bguardas brilhando§7 para quebrar."));
            p.playSound(e.getLocation(), "minecraft:block.beacon.activate", 1f, 0.7f);
        }
    }

    private void especial(List<Player> ps, long agora) {
        if (inicioLuta < 0) {
            inicioLuta = agora;
            proximaZona = agora + 6000;
        }
        double vida = e.getHealth();
        World w = e.getWorld();
        switch (t.esp) {
            case ESCUDO_LACAIOS -> {
                if (!escudo70 && vida <= vidaMax * 0.7) {
                    escudo70 = true;
                    escudo(ps);
                } else if (!escudo35 && vida <= vidaMax * 0.35) {
                    escudo35 = true;
                    escudo(ps);
                }
                if (imune()) {
                    Location l = e.getLocation();
                    double h = e.getHeight(), r = Math.max(1.2, e.getWidth());
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8, y = (i % 4) * h / 3;
                        w.spawnParticle(Particle.END_ROD, l.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, 0, 0, 0, 0);
                    }
                }
            }
            case CLONES -> {
                if (!clonou && vida <= vidaMax * 0.5) {
                    clonou = true;
                    for (int i = 0; i < 2; i++) {
                        Location ok = lugarLivre(e.getLocation().clone().add(rnd.nextInt(7) - 3, 0, rnd.nextInt(7) - 3));
                        if (ok == null) continue;
                        LivingEntity c = pl.lacaio(m, t.chefe, ok, true);
                        if (c == null) continue;
                        atributo(c, org.bukkit.attribute.Attribute.MAX_HEALTH, Math.max(20, vidaMax * 0.15));
                        c.setHealth(Math.max(20, vidaMax * 0.15));
                        if (t.escala != 1) atributo(c, org.bukkit.attribute.Attribute.SCALE, t.escala * 0.75);
                        if (t.dano > 0) atributo(c, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, t.dano * 0.6);
                        c.customName(Masmorras.txt("§7Clone de " + t.chefeNome));
                        c.setCustomNameVisible(true);
                        org.bukkit.inventory.EntityEquipment de = e.getEquipment(), para = c.getEquipment();
                        if (de != null && para != null) {
                            para.setItemInMainHand(de.getItemInMainHand());
                            para.setHelmet(de.getHelmet());
                            para.setItemInMainHandDropChance(0f);
                            para.setHelmetDropChance(0f);
                        }
                        w.spawnParticle(Particle.PORTAL, ok.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                    }
                    for (Player p : ps) p.sendMessage(Masmorras.txt("§5§l" + t.chefeNome + " se dividiu! §7Os clones são mais fracos."));
                    w.playSound(e.getLocation(), "minecraft:entity.illusioner.mirror_move", 1.2f, 0.8f);
                }
            }
            case ZONAS -> {
                if (agora >= proximaZona) {
                    proximaZona = agora + (furioso ? 7000 : 9500);
                    List<Player> alvos = new ArrayList<>(ps);
                    java.util.Collections.shuffle(alvos, rnd);
                    for (int i = 0; i < Math.min(3, alvos.size()); i++) zona(alvos.get(i).getLocation().clone());
                }
            }
            case FURIA_TEMPO -> {
                int quer = (int) Math.min(3, (agora - inicioLuta) / 40_000);
                if (quer > furiaNivel) {
                    furiaNivel = quer;
                    if (t.dano > 0) atributo(e, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, t.dano * (1 + 0.2 * furiaNivel));
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, Math.min(2, furiaNivel), false, false));
                    for (Player p : ps) {
                        p.sendMessage(Masmorras.txt("§4§l" + t.chefeNome + " está ficando mais forte! §7(" + furiaNivel + "/3)"));
                        p.playSound(e.getLocation(), "minecraft:entity.ravager.roar", 1f, 0.6f);
                    }
                }
            }
            case CRISTAIS -> {
                if (!cristais75 && vida <= vidaMax * 0.75) {
                    cristais75 = true;
                    cristais(ps);
                } else if (!cristais40 && vida <= vidaMax * 0.4) {
                    cristais40 = true;
                    cristais(ps);
                }
                curaCristais();
            }
            case ELEMENTOS -> elementos(ps, agora);
            case ESCURIDAO -> {
                for (Player p : ps) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
                if (proximoRelampago == 0) proximoRelampago = agora + 5000;
                if (agora >= proximoRelampago) {
                    proximoRelampago = agora + 5000 + rnd.nextInt(3000);
                    reveladoAte = agora + 2200;
                    w.strikeLightningEffect(e.getLocation());
                    e.removePotionEffect(PotionEffectType.INVISIBILITY);
                    e.setGlowing(true);
                } else if (agora >= reveladoAte) {
                    e.setGlowing(false);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
                }
            }
            case GRAVIDADE -> {
                if (proximaGravidade == 0) proximaGravidade = agora + 12000;
                if (agora >= proximaGravidade) {
                    proximaGravidade = agora + 18000 + rnd.nextInt(5000);
                    for (Player p : ps) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 45, 0, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 140, 0, false, false));
                        p.sendActionBar(Masmorras.txt("§d§lA gravidade sumiu!"));
                    }
                    w.playSound(e.getLocation(), "minecraft:block.beacon.power_select", 1.2f, 0.6f);
                }
            }
            case ONDAS -> {
                if (proximaOnda == 0) proximaOnda = agora + 20000;
                if (agora >= proximaOnda) {
                    proximaOnda = agora + 20000;
                    invocar(3);
                    for (Player p : ps) p.sendActionBar(Masmorras.txt("§c" + t.chefeNome + " chamou mais monstros!"));
                }
            }
            default -> {
            }
        }
    }

    /** Cristais de cura (shulkers parados e abertos): enquanto algum estiver vivo, o chefe se cura. */
    private void cristais(List<Player> ps) {
        for (int i = 0; i < 3; i++) {
            Caixa a = m.arena();
            Location ok = lugarLivre(new Location(e.getWorld(), a.x1() + 2 + rnd.nextInt(Math.max(1, a.x2() - a.x1() - 3)), a.y1(),
                    a.z1() + 2 + rnd.nextInt(Math.max(1, a.z2() - a.z1() - 3))));
            if (ok == null) continue;
            LivingEntity c = pl.lacaio(m, "SHULKER", ok, true);
            if (c == null) continue;
            if (c instanceof org.bukkit.entity.Shulker sh) {
                sh.setAI(false);
                sh.setPeek(1f);
                try {
                    sh.setColor(org.bukkit.DyeColor.MAGENTA);
                } catch (Throwable ignored) {
                }
            }
            atributo(c, Attribute.MAX_HEALTH, 18 + 6 * t.nivel);
            c.setHealth(18 + 6 * t.nivel);
            c.setGlowing(true);
            c.customName(Masmorras.txt("§d§lCristal de Cura"));
            c.setCustomNameVisible(true);
            cristais.add(c.getUniqueId());
        }
        for (Player p : ps) {
            p.sendMessage(Masmorras.txt("§d§l" + t.chefeNome + " ergueu cristais de cura! §7Quebre os §dcristais brilhando§7."));
            p.playSound(e.getLocation(), "minecraft:block.amethyst_block.resonate", 1.4f, 0.6f);
        }
    }

    private void curaCristais() {
        if (cristais.isEmpty()) return;
        int vivos = 0;
        for (java.util.Iterator<java.util.UUID> it = cristais.iterator(); it.hasNext(); ) {
            Entity c = org.bukkit.Bukkit.getEntity(it.next());
            if (c == null || !c.isValid() || c.isDead()) {
                it.remove();
                continue;
            }
            vivos++;
            // raio de cura do cristal ate o chefe
            Location de = c.getLocation().add(0, 0.6, 0), para = e.getLocation().add(0, e.getHeight() / 2, 0);
            Vector v = para.toVector().subtract(de.toVector());
            double d = v.length();
            v.normalize();
            for (double k = 0; k < d; k += 0.8)
                e.getWorld().spawnParticle(Particle.DUST, de.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(230, 90, 255), 1f));
        }
        if (vivos > 0 && e.getHealth() < vidaMax) e.setHealth(Math.min(vidaMax, e.getHealth() + vidaMax * 0.006 * vivos));
    }

    /** Fogo, gelo e raio: troca de forma a cada quarto da vida; cada forma tem um ataque proprio a cada 3 s. */
    private void elementos(List<Player> ps, long agora) {
        int quer = (int) Math.min(2, Math.floor((1 - e.getHealth() / vidaMax) * 4)) % 3;
        if (quer != elemento) {
            elemento = quer;
            String[] nomes = {"§6§lFOGO", "§b§lGELO", "§e§lRAIO"};
            for (Player p : ps) p.sendActionBar(Masmorras.txt("§7" + t.chefeNome + " mudou para a forma de " + nomes[elemento]));
            e.getWorld().playSound(e.getLocation(), "minecraft:entity.illusioner.cast_spell", 1.2f, 0.7f);
        }
        Particle part = elemento == 0 ? Particle.FLAME : elemento == 1 ? Particle.SNOWFLAKE : Particle.ELECTRIC_SPARK;
        e.getWorld().spawnParticle(part, e.getLocation().add(0, e.getHeight() / 2, 0), 12, 0.5, e.getHeight() / 3, 0.5, 0.02);
        if (agora < proximoElem) return;
        proximoElem = agora + 3000;
        Location l = e.getLocation();
        int n = t.nivel;
        for (Player p : ps) {
            if (p.getLocation().distanceSquared(l) > 9 * 9) continue;
            switch (elemento) {
                case 0 -> p.setFireTicks(Math.max(p.getFireTicks(), 60));
                case 1 -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 60, p.getFreezeTicks() + 80));
                }
                default -> {
                    if (rnd.nextBoolean()) {
                        p.getWorld().strikeLightningEffect(p.getLocation());
                        p.damage(2 + n, e);
                    }
                }
            }
        }
    }

    /** Circulo vermelho no chao por 1,6 s; depois explode quem ficou dentro. */
    private void zona(Location c) {
        World w = c.getWorld();
        Particle.DustOptions vermelho = new Particle.DustOptions(org.bukkit.Color.fromRGB(230, 30, 30), 1.5f);
        double r = 2.6;
        for (int k = 0; k < 4; k++)
            pl.depois(k * 8L, () -> {
                for (int i = 0; i < 28; i++) {
                    double a = i * Math.PI * 2 / 28;
                    w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, vermelho);
                }
                w.spawnParticle(Particle.DUST, c.clone().add(0, 0.15, 0), 10, r / 2, 0, r / 2, 0, vermelho);
            });
        w.playSound(c, "minecraft:block.note_block.bass", 1f, 0.5f);
        pl.depois(32L, () -> {
            if (!vivo()) return;
            w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 3, 1, 0.3, 1);
            w.spawnParticle(Particle.LAVA, c.clone().add(0, 0.3, 0), 12, 1, 0.2, 1);
            w.playSound(c, "minecraft:entity.generic.explode", 1f, 0.9f);
            for (Player p : naArena())
                if (p.getLocation().distanceSquared(c) <= r * r && Math.abs(p.getY() - c.getY()) < 3) {
                    p.damage(4 + 2 * t.nivel, e);
                    p.setVelocity(p.getVelocity().setY(0.6));
                }
        });
    }

    Location centro() {
        Caixa a = m.arena();
        return new Location(e.getWorld(), a.cx() + 0.5, a.y1(), a.cz() + 0.5);
    }

    static Particle particula(String nome) {
        try {
            Particle p = Particle.valueOf(nome);
            return p.getDataType() == Void.class ? p : Particle.CRIT;
        } catch (Throwable t) {
            return Particle.CRIT;
        }
    }

    /** Particulas em volta do chefe (cada tipo a sua) e um anel no chao; furioso: chamas e raiva. */
    void efeitos() {
        if (visual != null && System.currentTimeMillis() < sumidoAte) return;
        World w = e.getWorld();
        Location l = e.getLocation();
        String nome = switch (t.id) {
            case "CRIPTA" -> "SOUL";
            case "NINHO" -> "SMOKE";
            case "MINA" -> "LARGE_SMOKE";
            case "PANTANO" -> "WITCH";
            case "GELO" -> "SNOWFLAKE";
            case "TORRE" -> "ENCHANT";
            case "FORJA" -> "FLAME";
            case "BASTIAO" -> "CRIMSON_SPORE";
            case "WITHER" -> "SOUL_FIRE_FLAME";
            case "CIDADELA" -> "PORTAL";
            case "PRISAO" -> "SCULK_SOUL";
            case "DESERTO" -> "WHITE_ASH";
            case "QUARTEL" -> "CRIT";
            case "VENTOS" -> "CLOUD";
            case "AFOGADA" -> "DRIPPING_WATER";
            case "SELVA" -> "FALLING_SPORE_BLOSSOM";
            case "CASTELO" -> "SMOKE";
            case "COVIL" -> "CRIMSON_SPORE";
            default -> t.aura != null ? t.aura : "SMOKE";
        };
        double h = e.getHeight();
        w.spawnParticle(particula(nome), l.clone().add(0, h * 0.6, 0), 14, 0.5, h * 0.4, 0.5, 0.02);
        Particle anel = furioso ? Particle.FLAME : particula("ELECTRIC_SPARK");
        double r = Math.max(1.2, e.getWidth() * 1.2), giro = (System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2;
        for (int i = 0; i < 10; i++) {
            double a = giro + i * Math.PI / 5;
            w.spawnParticle(anel, l.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0);
        }
        if (furioso) w.spawnParticle(Particle.ANGRY_VILLAGER, l.clone().add(0, h + 0.3, 0), 1, 0.3, 0.1, 0.3, 0);
    }

    /** Herobrine: o manequim (com a cabeca do Herobrine) segue o corpo invisivel (a cada tick). */
    void seguirVisual() {
        if (visual == null || !visual.isValid()) return;
        if (!vivo()) {
            visual.remove();
            return;
        }
        Location l = e.getLocation();
        if (System.currentTimeMillis() < sumidoAte) {
            if (!visual.isInvisible()) {
                visual.setInvisible(true);
                if (visual.getEquipment() != null) visual.getEquipment().setHelmet(null);
            }
            return;
        }
        if (visual.isInvisible()) {
            visual.setInvisible(false);
            if (visual.getEquipment() != null) visual.getEquipment().setHelmet(Masmorras.cabecaHerobrine());
        }
        visual.teleport(l);
    }

    private Player maisPerto(List<Player> ps) {
        Player melhor = null;
        double d = Double.MAX_VALUE;
        for (Player p : ps) {
            double x = p.getLocation().distanceSquared(e.getLocation());
            if (x < d) {
                d = x;
                melhor = p;
            }
        }
        return melhor;
    }

    private Tipo.Hab forcada;

    /** Teste do admin: usa esta habilidade agora. */
    void testarHabilidade(Tipo.Hab h) {
        List<Player> ps = naArena();
        if (ps.isEmpty()) return;
        forcada = h;
        usarHabilidade(ps, maisPerto(ps));
        forcada = null;
    }

    private void usarHabilidade(List<Player> ps, Player alvo) {
        if (t.habs == null || t.habs.length == 0 || alvo == null) return;
        Tipo.Hab h = forcada != null ? forcada : t.habs[rnd.nextInt(t.habs.length)];
        if (h == Tipo.Hab.CURA && forcada == null) {
            if (curou || e.getHealth() > vidaMax * 0.35) h = t.habs[0];
        }
        int n = t.nivel;
        World w = e.getWorld();
        Location l = e.getLocation();
        switch (h) {
            case FLECHAS -> {
                for (Player p : primeiros(ps, 4))
                    for (int i = 0; i < 3; i++) {
                        Vector dir = p.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize()
                                .add(new Vector(rnd.nextGaussian() * 0.06, rnd.nextGaussian() * 0.04, rnd.nextGaussian() * 0.06)).multiply(1.8);
                        Arrow a = e.launchProjectile(Arrow.class, dir);
                        a.setDamage(2 + n);
                        a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                        if (t.id.equals("GELO")) a.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1), true);
                        pl.marcar(a, m);
                    }
                w.playSound(l, "minecraft:entity.skeleton.shoot", 1.2f, 0.7f);
            }
            case INVOCAR -> invocar(2 + n / 2);
            case PISAO -> {
                w.spawnParticle(Particle.EXPLOSION, l, 3, 1, 0.2, 1);
                w.playSound(l, "minecraft:entity.generic.explode", 1f, 0.6f);
                for (Player p : ps) {
                    if (p.getLocation().distanceSquared(l) > 7 * 7) continue;
                    p.damage(4 + n * 2, e);
                    Vector v = p.getLocation().toVector().subtract(l.toVector()).setY(0);
                    if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
                    p.setVelocity(v.normalize().multiply(1.2).setY(0.5));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                }
            }
            case INVESTIDA -> {
                Vector v = alvo.getLocation().toVector().subtract(l.toVector()).setY(0);
                if (v.lengthSquared() > 0.01) e.setVelocity(v.normalize().multiply(1.6).setY(0.35));
                w.playSound(l, "minecraft:entity.ravager.attack", 1f, 0.8f);
                pl.depois(10, () -> {
                    if (!vivo()) return;
                    for (Player p : naArena())
                        if (p.getLocation().distanceSquared(e.getLocation()) < 3 * 3) {
                            p.damage(5 + n * 2, e);
                            p.setVelocity(p.getLocation().toVector().subtract(e.getLocation().toVector()).setY(0).normalize().multiply(0.8).setY(0.4));
                        }
                });
            }
            case TELEPORTE -> {
                Location atras = alvo.getLocation().clone().add(alvo.getLocation().getDirection().setY(0).normalize().multiply(-2.5));
                Location ok = lugarLivre(atras);
                if (ok != null) {
                    w.spawnParticle(Particle.PORTAL, l, 40, 0.5, 1, 0.5);
                    e.teleport(ok);
                    w.playSound(ok, "minecraft:entity.enderman.teleport", 1f, 0.8f);
                }
            }
            case TEIA -> {
                for (Player p : ps) {
                    Block b = p.getLocation().getBlock();
                    if (b.getType() == Material.AIR && m.dentro(b.getX(), b.getY(), b.getZ())) {
                        b.setType(Material.COBWEB, false);
                        teias.add(b);
                    }
                }
                w.playSound(l, "minecraft:entity.spider.ambient", 1.4f, 0.5f);
                pl.depois(120, () -> tirarTeias(true));
            }
            case BOLAS_FOGO -> {
                for (Player p : primeiros(ps, 4))
                    for (int i = 0; i < 3; i++) {
                        final int k = i;
                        pl.depois(k * 6L, () -> {
                            if (!vivo() || !p.isOnline()) return;
                            Vector dir = p.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize();
                            SmallFireball f = e.launchProjectile(SmallFireball.class, dir.multiply(0.9));
                            pl.marcar(f, m);
                        });
                    }
                w.playSound(l, "minecraft:entity.blaze.shoot", 1.2f, 0.7f);
            }
            case ANEL_FOGO -> {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    w.spawnParticle(Particle.FLAME, l.clone().add(Math.cos(a) * 4, 0.3, Math.sin(a) * 4), 3, 0.2, 0.2, 0.2, 0.01);
                }
                w.playSound(l, "minecraft:item.firecharge.use", 1f, 0.6f);
                for (Player p : ps)
                    if (p.getLocation().distanceSquared(l) < 8 * 8) {
                        p.setFireTicks(Math.max(p.getFireTicks(), 100));
                        p.damage(3 + n, e);
                    }
            }
            case CRANIOS -> {
                for (Player p : primeiros(ps, 4))
                    for (int i = 0; i < 2; i++) {
                        final int k = i;
                        pl.depois(k * 8L, () -> {
                            if (!vivo() || !p.isOnline()) return;
                            Vector dir = p.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize();
                            WitherSkull s = e.launchProjectile(WitherSkull.class, dir.multiply(0.8));
                            pl.marcar(s, m);
                        });
                    }
                w.playSound(l, "minecraft:entity.wither.shoot", 1f, 0.8f);
            }
            case POCOES -> {
                PotionType[] tipos = {PotionType.HARMING, PotionType.POISON, PotionType.SLOWNESS, PotionType.WEAKNESS};
                for (Player p : primeiros(ps, 4)) {
                    ItemStack it = new ItemStack(Material.SPLASH_POTION);
                    if (it.getItemMeta() instanceof PotionMeta pm) {
                        pm.setBasePotionType(tipos[rnd.nextInt(tipos.length)]);
                        it.setItemMeta(pm);
                    }
                    Vector dir = p.getLocation().toVector().subtract(l.toVector());
                    double dist = dir.length();
                    dir.normalize().multiply(Math.min(1.2, 0.35 + dist * 0.06)).setY(0.35);
                    ThrownPotion tp = e.launchProjectile(ThrownPotion.class, dir);
                    tp.setItem(it);
                    pl.marcar(tp, m);
                }
                w.playSound(l, "minecraft:entity.witch.throw", 1.2f, 0.7f);
            }
            case LEVITAR -> {
                for (Player p : ps)
                    if (p.getLocation().distanceSquared(l) < 14 * 14) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1));
                        w.spawnParticle(Particle.END_ROD, p.getLocation(), 15, 0.3, 0.5, 0.3, 0.02);
                    }
                w.playSound(l, "minecraft:entity.shulker.shoot", 1.2f, 0.6f);
            }
            case PUXAR -> {
                for (Player p : ps) {
                    Vector v = l.toVector().subtract(p.getLocation().toVector());
                    if (v.lengthSquared() < 4 || v.lengthSquared() > 16 * 16) continue;
                    p.setVelocity(v.setY(0).normalize().multiply(1.3).setY(0.3));
                    w.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation(), 25, 0.3, 0.8, 0.3, 0.05);
                }
                w.playSound(l, "minecraft:entity.evoker.prepare_summon", 1f, 0.6f);
            }
            case CONGELAR -> {
                for (Player p : ps)
                    if (p.getLocation().distanceSquared(l) < 11 * 11) {
                        p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 100, p.getFreezeTicks() + 200));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        w.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
                    }
                w.playSound(l, "minecraft:block.powder_snow.step", 1.5f, 0.5f);
            }
            case RAIO -> {
                for (Player p : primeiros(ps, 3)) {
                    w.strikeLightningEffect(p.getLocation());
                    p.damage(4 + n, e);
                }
            }
            case TNT -> {
                for (Player p : primeiros(ps, 3)) {
                    TNTPrimed tnt = w.spawn(p.getLocation().add(0, 2, 0), TNTPrimed.class, x -> {
                        x.setFuseTicks(40);
                        x.setYield(2.5f);
                        x.setSource(e);
                    });
                    pl.marcar(tnt, m);
                }
                w.playSound(l, "minecraft:entity.tnt.primed", 1.2f, 0.8f);
            }
            case ESCUDO -> {
                e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                w.spawnParticle(Particle.ENCHANTED_HIT, l.clone().add(0, 1, 0), 40, 0.6, 1, 0.6);
                w.playSound(l, "minecraft:item.shield.block", 1.2f, 0.6f);
            }
            case GRITO -> {
                e.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0));
                for (Entity x : pl.lacaiosVivos(m))
                    if (x instanceof LivingEntity le) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0));
                    }
                w.playSound(l, "minecraft:entity.piglin_brute.angry", 1.6f, 0.6f);
                invocar(2);
            }
            case ARREMESSO -> {
                for (Player p : ps)
                    if (p.getLocation().distanceSquared(l) < 6 * 6) {
                        Vector v = p.getLocation().toVector().subtract(l.toVector()).setY(0);
                        if (v.lengthSquared() < 0.01) v = new Vector(0, 0, 1);
                        p.setVelocity(v.normalize().multiply(0.6).setY(1.3));
                        p.damage(3 + n, e);
                    }
                w.playSound(l, "minecraft:entity.iron_golem.attack", 1.4f, 0.6f);
            }
            case RUGIDO -> {
                for (Player p : ps) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 160, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
                }
                w.playSound(l, "minecraft:entity.warden.roar", 1.6f, 1f);
            }
            case AURA_WITHER -> {
                w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 60, 3, 1, 3, 0.01);
                for (Player p : ps)
                    if (p.getLocation().distanceSquared(l) < 9 * 9) p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1));
                w.playSound(l, "minecraft:entity.wither.ambient", 1f, 0.6f);
            }
            case SUMIR -> {
                sumidoAte = System.currentTimeMillis() + 2500;
                w.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.02);
                w.playSound(l, "minecraft:ambient.cave", 1.6f, 0.6f);
                pl.depois(50, () -> {
                    if (!vivo()) return;
                    List<Player> agoraNa = naArena();
                    if (agoraNa.isEmpty()) return;
                    Player p = agoraNa.get(rnd.nextInt(agoraNa.size()));
                    Location atras = p.getLocation().clone().add(p.getLocation().getDirection().setY(0).normalize().multiply(-2));
                    Location ok = lugarLivre(atras);
                    if (ok != null) e.teleport(ok);
                    sumidoAte = 0;
                    p.getWorld().strikeLightningEffect(e.getLocation());
                    p.sendActionBar(Masmorras.txt("§fEle está atrás de você."));
                    p.playSound(p.getLocation(), "minecraft:entity.enderman.stare", 1f, 0.5f);
                });
            }
            case CEGAR -> {
                String[] frases = {"Você não deveria estar aqui.", "Eu sempre estive aqui.", "Ninguém sai.", "Olhe para trás."};
                String f = frases[rnd.nextInt(frases.length)];
                for (Player p : ps) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
                    p.sendMessage(Masmorras.txt("§8§o" + t.chefeNome + " sussurra: §7§o" + f));
                }
                w.playSound(l, "minecraft:ambient.cave", 1.8f, 0.5f);
            }
            case CURA -> {
                curou = true;
                e.setHealth(Math.min(vidaMax, e.getHealth() + vidaMax * 0.25));
                w.spawnParticle(Particle.HEART, l.clone().add(0, 2, 0), 12, 0.6, 0.6, 0.6);
                w.playSound(l, "minecraft:entity.witch.drink", 1.4f, 0.7f);
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§d" + t.chefeNome + " bebeu uma poção de cura!"));
            }
            default -> novaHabilidade(h, ps, alvo);
        }
    }

    // ------------------------------------------------------------------ habilidades novas (versao 3)

    private boolean naArenaAinda(Player p) {
        return p.isOnline() && !p.isDead() && naArena().contains(p);
    }

    private void circulo(Location c, double r, Particle.DustOptions cor) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, cor);
        }
    }

    private void novaHabilidade(Tipo.Hab h, List<Player> ps, Player alvo) {
        int n = t.nivel;
        World w = e.getWorld();
        Location l = e.getLocation();
        switch (h) {
            case METEORO -> {
                Particle.DustOptions laranja = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 120, 20), 1.6f);
                for (Player p : primeiros(ps, 3)) {
                    Location c = p.getLocation().clone();
                    for (int k = 0; k < 4; k++) pl.depois(k * 7L, () -> circulo(c, 2.2, laranja));
                    for (int k = 0; k < 6; k++) {
                        final int kk = k;
                        pl.depois(16 + k * 2L, () -> w.spawnParticle(Particle.FLAME, c.clone().add(0, 12 - kk * 2, 0), 20, 0.4, 0.4, 0.4, 0.02));
                    }
                    pl.depois(30L, () -> {
                        if (!vivo()) return;
                        w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 3, 1, 0.3, 1);
                        w.spawnParticle(Particle.LAVA, c.clone().add(0, 0.3, 0), 16, 1.2, 0.2, 1.2);
                        w.playSound(c, "minecraft:entity.generic.explode", 1.2f, 0.7f);
                        for (Player q : naArena())
                            if (q.getLocation().distanceSquared(c) <= 2.6 * 2.6) {
                                q.damage(5 + 2 * n, e);
                                q.setFireTicks(Math.max(q.getFireTicks(), 60));
                            }
                    });
                }
                w.playSound(l, "minecraft:entity.blaze.ambient", 1.4f, 0.5f);
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§6Meteoros! §7Saia dos círculos laranja."));
            }
            case LAMINAS -> {
                for (int k = 0; k < 6; k++) {
                    final int kk = k;
                    pl.depois(k * 5L, () -> {
                        if (!vivo()) return;
                        Location c = e.getLocation();
                        for (int i = 0; i < 12; i++) {
                            double a = i * Math.PI / 6 + kk * 0.5;
                            w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(Math.cos(a) * 3, 1, Math.sin(a) * 3), 1, 0, 0, 0, 0);
                        }
                        w.playSound(c, "minecraft:entity.player.attack.sweep", 1.2f, 0.6f + kk * 0.05f);
                        for (Player q : naArena()) if (q.getLocation().distanceSquared(c) < 4.2 * 4.2) q.damage(2 + n * 0.6, e);
                    });
                }
            }
            case GELO_PRISAO -> {
                Location c = alvo.getLocation();
                int bx = c.getBlockX(), by = c.getBlockY(), bz = c.getBlockZ();
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        for (int dy = 0; dy <= 2; dy++) {
                            boolean borda = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 2;
                            if (!borda) continue;
                            Block b = w.getBlockAt(bx + dx, by + dy, bz + dz);
                            if (!b.getType().isAir() || !m.dentro(b.getX(), b.getY(), b.getZ())) continue;
                            b.setType(Material.PACKED_ICE, false);
                            gelos.add(b);
                        }
                alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                alvo.damage(2 + n, e);
                alvo.sendActionBar(Masmorras.txt("§b§lVocê foi preso no gelo!"));
                w.playSound(c, "minecraft:block.glass.place", 1.4f, 0.5f);
                pl.depois(60L, this::tirarGelos);
            }
            case ESPINHOS -> {
                Vector dir = alvo.getLocation().toVector().subtract(l.toVector()).setY(0);
                if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                Vector d = dir.normalize();
                org.bukkit.block.data.BlockData ponta = Material.POINTED_DRIPSTONE.createBlockData();
                for (int k = 1; k <= 14; k++) {
                    final int kk = k;
                    pl.depois(k * 2L, () -> {
                        if (!vivo()) return;
                        Location p0 = l.clone().add(d.clone().multiply(kk));
                        w.spawnParticle(Particle.BLOCK, p0.clone().add(0, 0.4, 0), 14, 0.3, 0.4, 0.3, 0, ponta);
                        w.playSound(p0, "minecraft:block.pointed_dripstone.land", 0.8f, 0.6f);
                        for (Player q : naArena())
                            if (q.getLocation().distanceSquared(p0) < 1.4 * 1.4) {
                                q.damage(4 + 2 * n, e);
                                q.setVelocity(q.getVelocity().setY(0.7));
                            }
                    });
                }
            }
            case CORRENTE_RAIO -> {
                List<Player> feitos = new ArrayList<>();
                Player atual = alvo;
                for (int k = 0; k < 4 && atual != null; k++) {
                    final Player q = atual;
                    final int kk = k;
                    feitos.add(q);
                    pl.depois(k * 5L, () -> {
                        if (!naArenaAinda(q)) return;
                        w.strikeLightningEffect(q.getLocation());
                        q.damage(Math.max(2, 4 + n - kk), e);
                    });
                    Player prox = null;
                    double md = 8 * 8;
                    for (Player o : ps) {
                        if (feitos.contains(o)) continue;
                        double dd = o.getLocation().distanceSquared(q.getLocation());
                        if (dd < md) {
                            md = dd;
                            prox = o;
                        }
                    }
                    atual = prox;
                }
            }
            case BURACO_NEGRO -> {
                Location c = centro().add(0, 1, 0);
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§5§lUm buraco negro está te puxando! §7Corra para longe!"));
                for (int k = 0; k < 12; k++) {
                    final int kk = k;
                    pl.depois(k * 5L, () -> {
                        if (!vivo()) return;
                        for (int i = 0; i < 16; i++) {
                            double a = i * Math.PI / 8 + kk * 0.4, r = 3 - kk * 0.2;
                            w.spawnParticle(Particle.REVERSE_PORTAL, c.clone().add(Math.cos(a) * r, Math.sin(kk + i) * 0.5, Math.sin(a) * r), 1, 0, 0, 0, 0);
                        }
                        for (Player q : naArena()) {
                            Vector v = c.toVector().subtract(q.getLocation().toVector());
                            double dd = v.length();
                            if (dd > 14 || dd < 0.5) continue;
                            q.setVelocity(q.getVelocity().add(v.setY(0).normalize().multiply(0.22)));
                            if (kk == 11 && dd < 3) q.damage(5 + 2 * n, e);
                        }
                    });
                }
                w.playSound(c, "minecraft:block.portal.trigger", 0.8f, 0.6f);
            }
            case NUVEM_VENENO -> {
                for (Player p : primeiros(ps, 4)) {
                    org.bukkit.entity.AreaEffectCloud nuvem = w.spawn(p.getLocation(), org.bukkit.entity.AreaEffectCloud.class, c -> {
                        c.setRadius(2.6f);
                        c.setDuration(100);
                        c.setRadiusPerTick(-0.01f);
                        c.setColor(org.bukkit.Color.fromRGB(90, 170, 40));
                        c.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, 1), true);
                        c.setSource(e);
                    });
                    pl.marcar(nuvem, m);
                }
                w.playSound(l, "minecraft:entity.witch.throw", 1.2f, 0.5f);
            }
            case CORRENTES -> {
                alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 9));
                alvo.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 50, 1));
                alvo.sendActionBar(Masmorras.txt("§7§lCorrentes te prenderam!"));
                for (int k = 0; k < 10; k++) {
                    pl.depois(k * 5L, () -> {
                        if (!vivo() || !alvo.isOnline()) return;
                        Location de = e.getLocation().add(0, e.getHeight() / 2, 0), para = alvo.getLocation().add(0, 1, 0);
                        Vector v = para.toVector().subtract(de.toVector());
                        double d = v.length();
                        if (d > 30) return;
                        v.normalize();
                        for (double kk = 0; kk < d; kk += 0.6)
                            w.spawnParticle(Particle.DUST, de.clone().add(v.clone().multiply(kk)), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 120, 130), 0.9f));
                    });
                }
                w.playSound(alvo.getLocation(), "minecraft:block.chain.place", 1.4f, 0.5f);
                alvo.damage(2 + n, e);
            }
            case TROCA -> {
                Player longe = null;
                double md = -1;
                for (Player p : ps) {
                    double dd = p.getLocation().distanceSquared(l);
                    if (dd > md) {
                        md = dd;
                        longe = p;
                    }
                }
                if (longe == null) return;
                Location a = l.clone(), b = longe.getLocation().clone();
                w.spawnParticle(Particle.PORTAL, a.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                w.spawnParticle(Particle.PORTAL, b.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
                e.teleport(b);
                longe.teleport(a.setDirection(b.toVector().subtract(a.toVector())));
                w.playSound(a, "minecraft:entity.enderman.teleport", 1.2f, 0.6f);
                longe.sendActionBar(Masmorras.txt("§5" + t.chefeNome + " trocou de lugar com você!"));
            }
            case ONDA_CHOQUE -> {
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§e§lPULE! §7a onda de choque vem aí"));
                w.playSound(l, "minecraft:entity.warden.sonic_charge", 1f, 0.8f);
                Location c = l.clone();
                java.util.Set<java.util.UUID> pegos = new java.util.HashSet<>();
                for (int k = 0; k < 12; k++) {
                    final double r = 1.5 + k * 1.1;
                    pl.depois(14 + k * 2L, () -> {
                        if (!vivo()) return;
                        for (int i = 0; i < 32; i++) {
                            double a = i * Math.PI / 16;
                            w.spawnParticle(Particle.CLOUD, c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0, 0, 0);
                        }
                        for (Player q : naArena()) {
                            double dd = Math.hypot(q.getX() - c.getX(), q.getZ() - c.getZ());
                            if (Math.abs(dd - r) < 0.9 && Math.abs(q.getY() - c.getY()) < 1.2 && pegos.add(q.getUniqueId())) {
                                q.damage(4 + 2 * n, e);
                                q.setVelocity(q.getVelocity().setY(0.6));
                            }
                        }
                    });
                }
            }
            case DRENAR -> {
                if (alvo.getLocation().distanceSquared(l) > 18 * 18 || !e.hasLineOfSight(alvo)) return;
                alvo.sendActionBar(Masmorras.txt("§4" + t.chefeNome + " está drenando sua vida! §7Saia da vista dele!"));
                for (int k = 0; k < 8; k++) {
                    pl.depois(k * 5L, () -> {
                        if (!vivo() || !naArenaAinda(alvo) || !e.hasLineOfSight(alvo)) return;
                        Location de = alvo.getLocation().add(0, 1, 0), para = e.getLocation().add(0, e.getHeight() / 2, 0);
                        Vector v = para.toVector().subtract(de.toVector());
                        double d = v.length();
                        v.normalize();
                        for (double kk = 0; kk < d; kk += 0.5)
                            w.spawnParticle(Particle.DUST, de.clone().add(v.clone().multiply(kk)), 1, 0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 0), 1f));
                        double dano = 1 + n * 0.5;
                        alvo.damage(dano, e);
                        e.setHealth(Math.min(vidaMax, e.getHealth() + dano * 2));
                    });
                }
                w.playSound(l, "minecraft:entity.evoker.prepare_wololo", 1.2f, 0.6f);
            }
            case ENXAME -> {
                int vivos = 0;
                for (Entity x : pl.lacaiosVivos(m)) if (m.arena().crescer(1).contem(x.getX(), x.getY(), x.getZ())) vivos++;
                for (int i = 0; i < Math.min(3, 7 - vivos); i++) {
                    Location ok = lugarLivre(l.clone().add(rnd.nextInt(5) - 2, 0, rnd.nextInt(5) - 2));
                    if (ok == null) continue;
                    LivingEntity v = pl.lacaio(m, "VEX", ok.add(0, 1, 0), true);
                    if (v instanceof org.bukkit.entity.Vex vx) {
                        vx.setLimitedLifetime(true);
                        vx.setLimitedLifetimeTicks(300);
                        vx.setTarget(primeiros(ps, 1).get(0));
                    }
                }
                w.playSound(l, "minecraft:entity.evoker.prepare_attack", 1.2f, 0.8f);
            }
            case MARTELO -> {
                Location c = alvo.getLocation().clone();
                Particle.DustOptions verm = new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 30, 30), 1.6f);
                for (int k = 0; k < 4; k++) pl.depois(k * 5L, () -> circulo(c, 4, verm));
                Vector v = c.toVector().subtract(l.toVector());
                e.setVelocity(v.multiply(0.09).setY(1.0));
                w.playSound(l, "minecraft:entity.ravager.roar", 1.2f, 0.8f);
                pl.depois(20L, () -> {
                    if (!vivo()) return;
                    Location aqui = e.getLocation();
                    w.spawnParticle(Particle.EXPLOSION, aqui, 4, 1.5, 0.2, 1.5);
                    w.spawnParticle(Particle.BLOCK, aqui, 40, 2.5, 0.2, 2.5, 0, Material.STONE.createBlockData());
                    w.playSound(aqui, "minecraft:entity.generic.explode", 1.4f, 0.6f);
                    for (Player q : naArena())
                        if (q.getLocation().distanceSquared(aqui) < 5 * 5) {
                            q.damage(6 + 2 * n, e);
                            Vector kb = q.getLocation().toVector().subtract(aqui.toVector()).setY(0);
                            if (kb.lengthSquared() < 0.01) kb = new Vector(1, 0, 0);
                            q.setVelocity(kb.normalize().multiply(1.1).setY(0.5));
                        }
                });
            }
            case EXPLOSAO_ARCANA -> {
                e.setGlowing(true);
                for (Player p : ps) {
                    p.sendMessage(Masmorras.txt("§d§l" + t.chefeNome + " está carregando uma explosão! §fEsconda-se atrás de um pilar!"));
                    p.playSound(l, "minecraft:block.beacon.activate", 1.2f, 1.6f);
                }
                for (int k = 0; k < 8; k++) {
                    final int kk = k;
                    pl.depois(k * 5L, () -> {
                        if (vivo()) w.spawnParticle(Particle.ENCHANT, e.getLocation().add(0, e.getHeight() / 2, 0), 40 + kk * 10, 2, 2, 2, 1);
                    });
                }
                pl.depois(42L, () -> {
                    if (!vivo()) return;
                    if (t.esp != Tipo.Esp.ESCURIDAO) e.setGlowing(false);
                    Location aqui = e.getLocation().add(0, e.getHeight() / 2, 0);
                    w.spawnParticle(Particle.END_ROD, aqui, 120, 4, 2, 4, 0.3);
                    w.playSound(aqui, "minecraft:entity.generic.explode", 1.6f, 1.4f);
                    for (Player q : naArena())
                        if (q.getLocation().distanceSquared(aqui) < 22 * 22 && e.hasLineOfSight(q)) q.damage(6 + 3 * n, e);
                });
            }
            case ROUBAR -> {
                PotionEffectType[] bons = {PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE,
                        PotionEffectType.ABSORPTION, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.HASTE, PotionEffectType.JUMP_BOOST, PotionEffectType.INVISIBILITY};
                int roubados = 0;
                for (Player p : ps) {
                    if (p.getLocation().distanceSquared(l) > 14 * 14) continue;
                    for (PotionEffectType pe : bons)
                        if (p.hasPotionEffect(pe)) {
                            p.removePotionEffect(pe);
                            roubados++;
                        }
                    w.spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.1);
                    p.sendActionBar(Masmorras.txt("§e" + t.chefeNome + " roubou seus efeitos!"));
                }
                e.setHealth(Math.min(vidaMax, e.getHealth() + vidaMax * 0.03 * roubados));
                if (roubados > 0) e.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 0));
                w.playSound(l, "minecraft:entity.piglin.admiring_item", 1.4f, 0.6f);
            }
            case TRIDENTES -> {
                for (Player p : primeiros(ps, 4))
                    for (int i = 0; i < 2; i++) {
                        final int k = i;
                        pl.depois(k * 6L, () -> {
                            if (!vivo() || !p.isOnline()) return;
                            Vector dir = p.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize().multiply(2.0);
                            org.bukkit.entity.Trident tr = e.launchProjectile(org.bukkit.entity.Trident.class, dir);
                            tr.setDamage(4 + n);
                            tr.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                            pl.marcar(tr, m);
                        });
                    }
                w.playSound(l, "minecraft:item.trident.throw", 1.4f, 0.7f);
            }
            case SANGRAR -> {
                if (alvo.getLocation().distanceSquared(l) > 7 * 7) {
                    Vector v = alvo.getLocation().toVector().subtract(l.toVector()).setY(0);
                    if (v.lengthSquared() > 0.01) e.setVelocity(v.normalize().multiply(1.4).setY(0.3));
                }
                pl.depois(8L, () -> {
                    if (!vivo() || !naArenaAinda(alvo) || alvo.getLocation().distanceSquared(e.getLocation()) > 4.5 * 4.5) return;
                    alvo.sendActionBar(Masmorras.txt("§4Você está sangrando!"));
                    for (int k = 0; k < 5; k++)
                        pl.depois(k * 10L, () -> {
                            if (!alvo.isOnline() || alvo.isDead()) return;
                            alvo.damage(1 + n * 0.5, e);
                            alvo.getWorld().spawnParticle(Particle.DUST, alvo.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0,
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(160, 0, 0), 1.2f));
                        });
                });
                w.playSound(l, "minecraft:entity.player.attack.crit", 1.2f, 0.6f);
            }
            case VENTANIA -> {
                for (Player p : primeiros(ps, 3)) {
                    Vector dir = p.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize().multiply(1.2);
                    org.bukkit.entity.BreezeWindCharge v = e.launchProjectile(org.bukkit.entity.BreezeWindCharge.class, dir);
                    pl.marcar(v, m);
                }
                w.playSound(l, "minecraft:entity.breeze.shoot", 1.4f, 0.8f);
            }
            case GOSMA -> {
                for (Player p : primeiros(ps, 3)) {
                    Vector dir = p.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector());
                    double d = dir.length();
                    org.bukkit.entity.Snowball sb = e.launchProjectile(org.bukkit.entity.Snowball.class, dir.normalize().multiply(1.3));
                    sb.setItem(new ItemStack(Material.SLIME_BALL));
                    pl.marcar(sb, m);
                    pl.depois(Math.max(4L, (long) (d / 1.3)), () -> {
                        if (!naArenaAinda(p)) return;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 1));
                        p.damage(2 + n, e);
                        p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
                        p.sendActionBar(Masmorras.txt("§aVocê está grudado na gosma!"));
                    });
                }
                w.playSound(l, "minecraft:entity.slime.jump", 1.4f, 0.5f);
            }
            default -> {
            }
        }
    }

    void tirarGelos() {
        for (Block b : gelos) if (b.getType() == Material.PACKED_ICE) b.setType(Material.AIR, false);
        gelos.clear();
    }

    private List<Player> primeiros(List<Player> ps, int n) {
        List<Player> l = new ArrayList<>(ps);
        java.util.Collections.shuffle(l, rnd);
        return l.subList(0, Math.min(n, l.size()));
    }

    private void invocar(int quantos) {
        if (t.ajudantes == null || t.ajudantes.length == 0) return;
        int vivos = 0;
        for (Entity x : pl.lacaiosVivos(m)) if (m.arena().crescer(1).contem(x.getX(), x.getY(), x.getZ())) vivos++;
        quantos = Math.min(quantos, 6 - vivos);
        Location l = e.getLocation();
        for (int i = 0; i < quantos; i++) {
            Location ok = lugarLivre(l.clone().add(rnd.nextInt(7) - 3, 0, rnd.nextInt(7) - 3));
            if (ok == null) continue;
            pl.lacaio(m, t.ajudantes[rnd.nextInt(t.ajudantes.length)], ok, true);
            e.getWorld().spawnParticle(Particle.SOUL, ok.clone().add(0, 0.5, 0), 12, 0.3, 0.4, 0.3, 0.02);
        }
        e.getWorld().playSound(l, "minecraft:entity.evoker.prepare_summon", 1.2f, 0.7f);
    }

    /** Ponto dentro da arena com ar para os pes e a cabeca. */
    Location lugarLivre(Location quer) {
        Caixa a = m.arena();
        int x = Math.max(a.x1(), Math.min(a.x2(), quer.getBlockX())), z = Math.max(a.z1(), Math.min(a.z2(), quer.getBlockZ()));
        World w = e.getWorld();
        for (int tent = 0; tent < 12; tent++) {
            int xx = tent == 0 ? x : Math.max(a.x1(), Math.min(a.x2(), x + rnd.nextInt(5) - 2));
            int zz = tent == 0 ? z : Math.max(a.z1(), Math.min(a.z2(), z + rnd.nextInt(5) - 2));
            Block b = w.getBlockAt(xx, a.y1(), zz);
            if (b.isPassable() && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, 2, 0).isPassable())
                return new Location(w, xx + 0.5, a.y1(), zz + 0.5, quer.getYaw(), quer.getPitch());
        }
        return null;
    }

    void tirarTeias(boolean todas) {
        for (Block b : new ArrayList<>(teias)) {
            if (!todas && rnd.nextBoolean()) continue;
            if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
            teias.remove(b);
        }
    }

    /** Tira o chefe (sem premio) e a barra. */
    void tirarVisual() {
        if (visualTarefa != null) visualTarefa.cancel();
        visualTarefa = null;
        if (visual != null && visual.isValid()) visual.remove();
        visual = null;
    }

    void remover() {
        tirarGelos();
        for (java.util.UUID id : cristais) {
            Entity c = org.bukkit.Bukkit.getEntity(id);
            if (c != null) c.remove();
        }
        cristais.clear();
        tirarVisual();
        for (Player p : vendo) p.hideBossBar(barra);
        vendo.clear();
        tirarTeias(true);
        if (e != null && e.isValid()) e.remove();
    }

    void esconderBarra() {
        tirarGelos();
        tirarVisual();
        for (Player p : vendo) p.hideBossBar(barra);
        vendo.clear();
        tirarTeias(true);
    }

    static void atributo(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    static Component nome(Tipo t) {
        return Masmorras.txt("§c§l" + t.chefeNome + " §7[Nível " + t.nivel + "]");
    }
}
