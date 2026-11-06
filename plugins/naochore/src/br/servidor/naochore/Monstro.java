package br.servidor.naochore;

import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * O Faminto (dia 5): caca o jogador mais perto, sempre sabe onde ele esta. Por dentro e um zumbi invisivel (anda
 * pelos caminhos do Minecraft, sobe escada) e por fora um manequim com skin. Quando trava, quebra o que estiver na
 * frente: porta e vidro bem rapido, madeira e terra um pouco mais devagar, pedra mais devagar ainda (obsidiana nao).
 * Se o jogador subir num pilar, ele escala o pilar.
 */
final class Monstro {
    private int limpeza;

    private final NaoChore pl;
    private final World w;
    private final Random rnd = new Random();
    Zombie motor;
    Corpo corpo;
    private Location ultima;
    private int parado, cicloAtaque;
    private Block quebrando;
    private double progresso;
    private boolean escalando, gritou;
    private long tick;

    Monstro(NaoChore pl, World w) {
        this.pl = pl;
        this.w = w;
    }

    boolean vivo() {
        return motor != null && motor.isValid() && !motor.isDead() && corpo != null && corpo.valido();
    }

    void criar(Location l, double vida) {
        remover();
        NamespacedKey sn = NamespacedKey.fromString("levelledmobs:nolevel"), sm = NamespacedKey.fromString("levelledmobs:denylm_nametag");
        motor = w.spawn(l, Zombie.class, z -> {
            z.getPersistentDataContainer().set(pl.kMonstro, PersistentDataType.BYTE, (byte) 1);
            if (sn != null) z.getPersistentDataContainer().set(sn, PersistentDataType.STRING, "naochore");
            if (sm != null) z.getPersistentDataContainer().set(sm, PersistentDataType.STRING, "naochore");
            z.addScoreboardTag("clearlag_exclude");
            z.setAdult();
            z.setInvisible(true);
            z.setSilent(true);
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            z.setShouldBurnInDay(false);
            z.getEquipment().clear();
            z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            atributo(z, Attribute.MAX_HEALTH, vida);
            z.setHealth(vida);
            atributo(z, Attribute.FOLLOW_RANGE, 64);
            atributo(z, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            atributo(z, Attribute.MOVEMENT_SPEED, 0.27);
            atributo(z, Attribute.STEP_HEIGHT, 1.1);
            atributo(z, Attribute.ATTACK_DAMAGE, 0);
        });
        try {
            Bukkit.getMobGoals().removeAllGoals(motor);
        } catch (Throwable t) {
            pl.getLogger().warning("Nao tirei a IA do zumbi: " + t);
        }
        motor.getPathfinder().setCanOpenDoors(false);
        motor.getPathfinder().setCanPassDoors(false);
        motor.getPathfinder().setCanFloat(true);
        corpo = new Corpo(l, "faminto", 1.2, null);
        ultima = l.clone();
        gritou = false;
    }

    static void atributo(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    void remover() {
        pararQuebra();
        if (corpo != null) corpo.remove();
        if (motor != null) motor.remove();
        corpo = null;
        motor = null;
    }

    boolean eh(org.bukkit.entity.Entity e) {
        return e != null && (motor != null && e.getUniqueId().equals(motor.getUniqueId()) || corpo != null && corpo.eh(e));
    }

    /** Todo tick: o corpo segue o motor; escalando, sobe colado no pilar. */
    void sincronizar(Player alvo) {
        if (motor != null && motor.isValid() && (++limpeza % 10 == 1)) Corpo.semEquipamento(motor);
        if (!vivo()) return;
        tick++;
        if (escalando && alvo != null) {
            Vector para = alvo.getLocation().toVector().subtract(motor.getLocation().toVector());
            Vector hz = para.clone().setY(0);
            Vector v = hz.lengthSquared() > 0.01 ? hz.normalize().multiply(0.12) : new Vector();
            motor.setVelocity(v.setY(0.32));
            motor.setFallDistance(0);
            if (tick % 4 == 0) w.spawnParticle(Particle.BLOCK, motor.getLocation().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0,
                    motor.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData());
        }
        Location l = motor.getLocation();
        float yaw = motor.getBodyYaw();
        if (alvo != null) {
            Location t = l.clone();
            t.setDirection(alvo.getLocation().toVector().subtract(l.toVector()));
            yaw = t.getYaw();
        }
        l.setYaw(yaw);
        l.setPitch(0);
        boolean moveu = ultima == null || l.distanceSquared(ultima) > 0.0004;
        corpo.rotacao(yaw, 0);
        corpo.teleport(l);
        corpo.andar(moveu);
        corpo.respirar();
        corpo.visibilidade(false);
        if (ataque > 0 && --ataque == 0) corpo.quadro(moveu ? "walk1" : "idle");
    }

    private int ataque;

    /** Boca escancarada por um instante (ataque / grito). */
    void bocaAberta(int ticks) {
        corpo.quadro("grito");
        ataque = ticks;
    }

    /** 4 vezes por segundo. noite = mais rapido. */
    void ciclo(List<Player> vivos, boolean noite, double dano) {
        if (!vivo() || vivos.isEmpty()) return;
        Player alvo = null;
        double md = 1e18;
        Location v = motor.getLocation();
        for (Player p : vivos) {
            double d = p.getLocation().distanceSquared(v);
            if (d < md) {
                md = d;
                alvo = p;
            }
        }
        if (alvo == null) return;
        double dist = Math.sqrt(md);
        // longe demais (chunk descarregado, caiu num buraco): reaparece perto, fora de vista
        if (dist > 90) {
            Location l = pl.lugarLonge(alvo, 30, 45);
            if (l != null) {
                pararQuebra();
                motor.teleport(l);
            }
            return;
        }
        if (!gritou && dist < 30) {
            gritou = true;
            Sons.local(w, v, 60, "naochore.rosnado", 2f, 1f, Sound.ENTITY_ENDERMAN_SCREAM, 2f, 0.4f);
            alvo.playSound(alvo.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.5f);
            bocaAberta(20);
        }
        Location al = alvo.getLocation();
        double dy = al.getY() - v.getY(), hz = Math.hypot(al.getX() - v.getX(), al.getZ() - v.getZ());
        // ataque
        if (cicloAtaque > 0) cicloAtaque--;
        if (hz < 1.9 && Math.abs(dy) < 2.2 && cicloAtaque == 0 && motor.hasLineOfSight(alvo)) {
            cicloAtaque = 4;
            bocaAberta(8);
            alvo.damage(dano, motor);
            alvo.setVelocity(alvo.getLocation().toVector().subtract(v.toVector()).setY(0).normalize().multiply(0.5).setY(0.3));
            w.playSound(v, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.5f);
        }
        // escalar pilar: o alvo esta em cima e quase na mesma coluna
        escalando = dy > 1.6 && hz < 3.2;
        if (escalando) {
            pararQuebra();
            parado = 0;
            ultima = v.clone();
            return;
        }
        motor.getPathfinder().moveTo(alvo, noite ? 1.45 : 1.25);
        // travou? quebra o que estiver na frente
        boolean andou = ultima != null && ultima.getWorld().equals(v.getWorld()) && ultima.distanceSquared(v) > 0.04;
        ultima = v.clone();
        if (andou) {
            parado = 0;
            if (quebrando != null && quebrando.getLocation().distanceSquared(v) > 9) pararQuebra();
        } else parado++;
        if (tick % 2 == 0 && andou) w.playSound(v, Sound.ENTITY_WARDEN_STEP, 1.1f, 0.6f);
        if (parado >= 3 && dist < 40) quebrar(alvo);
        // muito tempo parado e longe (sem caminho): aparece mais perto, fora de vista
        if (parado >= 24 && dist > 28) {
            Location l = pl.lugarLonge(alvo, 18, 26);
            if (l != null) {
                pararQuebra();
                motor.teleport(l);
                parado = 0;
            }
        }
        if (rnd.nextInt(40) == 0) Sons.localComPausa(w, v, 40, "uivador_estalo", 1f, 0.6f, Sound.ENTITY_WARDEN_AMBIENT, 1.2f, 0.5f, 4000);
    }

    /** Escolhe o bloco que atrapalha (na direcao do alvo, na altura do pe ou da cabeca) e vai quebrando. */
    private void quebrar(Player alvo) {
        if (quebrando == null || quebrando.getType().isAir()) {
            quebrando = obstaculo(alvo);
            progresso = 0;
            if (quebrando == null) return;
        }
        double t = tempo(quebrando.getType());
        if (t < 0) {
            quebrando = null;
            return;
        }
        progresso += 0.25 / t;
        float pr = (float) Math.min(1, progresso);
        for (Player p : w.getPlayers())
            if (p.getLocation().distanceSquared(quebrando.getLocation()) < 40 * 40) p.sendBlockDamage(quebrando.getLocation(), pr, motor.getEntityId());
        bocaAberta(6);
        Material m = quebrando.getType();
        String n = m.name();
        if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("FENCE_GATE"))
            w.playSound(quebrando.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.4f, 0.7f);
        else if (rnd.nextBoolean()) w.playSound(quebrando.getLocation(), quebrando.getBlockData().getSoundGroup().getHitSound(), 1f, 0.6f);
        if (progresso < 1) return;
        Block b = quebrando;
        pararQuebra();
        w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 40, 0.3, 0.3, 0.3, 0, b.getBlockData());
        if (n.endsWith("_DOOR")) w.playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.6f, 0.7f);
        else if (n.contains("GLASS")) w.playSound(b.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.6f, 0.8f);
        else w.playSound(b.getLocation(), b.getBlockData().getSoundGroup().getBreakSound(), 1.4f, 0.7f);
        // porta: as duas metades
        if (b.getBlockData() instanceof Door d) {
            Block outra = d.getHalf() == Bisected.Half.BOTTOM ? b.getRelative(BlockFace.UP) : b.getRelative(BlockFace.DOWN);
            if (outra.getBlockData() instanceof Door) outra.setType(Material.AIR, false);
        }
        b.setType(Material.AIR, false);
        parado = 0;
    }

    private void pararQuebra() {
        if (quebrando != null && motor != null)
            for (Player p : w.getPlayers()) p.sendBlockDamage(quebrando.getLocation(), 0f, motor.getEntityId());
        quebrando = null;
        progresso = 0;
    }

    private Block obstaculo(Player alvo) {
        Location v = motor.getLocation();
        Vector dir = alvo.getLocation().toVector().subtract(v.toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) return null;
        dir.normalize();
        // primeiro o que esta bem na frente (pe e cabeca), depois o que o raio ate o alvo acertar
        for (double alt : new double[] {0.5, 1.5}) {
            Block b = v.clone().add(0, alt, 0).add(dir.clone().multiply(0.9)).getBlock();
            if (quebravel(b)) return b;
        }
        RayTraceResult r = w.rayTraceBlocks(motor.getEyeLocation(), alvo.getEyeLocation().toVector().subtract(motor.getEyeLocation().toVector()).normalize(),
                4, FluidCollisionMode.NEVER, true);
        if (r != null && r.getHitBlock() != null && quebravel(r.getHitBlock())) return r.getHitBlock();
        // cavando para baixo/cima quando o alvo esta num buraco ou em cima de um telhado
        double dy = alvo.getLocation().getY() - v.getY();
        if (dy < -1.5) {
            Block b = v.getBlock().getRelative(BlockFace.DOWN);
            if (quebravel(b)) return b;
        } else if (dy > 1.5) {
            Block b = v.getBlock().getRelative(0, 2, 0);
            if (quebravel(b)) return b;
        }
        return null;
    }

    private boolean quebravel(Block b) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid()) return false;
        String n = m.name();
        boolean atrapalha = m.isSolid() || n.contains("GLASS") || n.endsWith("_DOOR") || n.endsWith("_FENCE") || n.equals("IRON_BARS");
        return atrapalha && tempo(m) >= 0;
    }

    /** Segundos para quebrar (-1 = nao quebra). Porta e vidro bem rapido. */
    static double tempo(Material m) {
        String n = m.name();
        if (m == Material.BEDROCK || m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || m == Material.REINFORCED_DEEPSLATE
                || m == Material.BARRIER || m == Material.END_PORTAL_FRAME || m == Material.RESPAWN_ANCHOR || m == Material.ANCIENT_DEBRIS
                || n.contains("NETHERITE")) return -1;
        if (n.contains("GLASS")) return 0.35;
        if (n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("FENCE_GATE")) return n.startsWith("IRON") ? 3.5 : 1.5;
        if (n.endsWith("_LEAVES") || n.endsWith("_WOOL") || n.endsWith("_CARPET")) return 0.4;
        float h = m.getHardness();
        if (h < 0) return -1;
        return 0.6 + Math.min(6, h * 1.3);
    }
}
