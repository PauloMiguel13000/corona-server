package br.servidor.tiro;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Missao (15/11, pedido do Paulo: "o Warface era pra ser missoes PvE contra bots que sao pessoas; voce vai seguindo a
 * fase ate o final"). Co-op de 1 a 4 numa base inimiga em LINHA: 5 setores separados por portoes. Em cada setor os
 * soldados inimigos (gente, que ATIRA em voce e usa cobertura) seguram a passagem; limpou, o portao abre e vira ponto
 * de controle (cura, municao e quem caiu volta). No fim, o Juggernaut. 4 classes: fuzileiro, medico, engenheiro e
 * atirador. Inimigos: soldado, escopeteiro (avanca), atirador de elite (mira com laser), escudeiro (so toma tiro pelas
 * costas ou de lado) e o pesado da metralhadora (fraco nas costas).
 */
final class Missao implements Listener {

    static final String PREFIXO = "§8[§c§lMissão§8] §7";
    static final int Y = 64, SETORES = 5, COMP = 40, LARG = 12, MAPA = 4;
    static final String[] NOMES = {"Entrada da Favela", "Becos", "Campo de Terra", "Galpão da Milícia", "Topo do Morro"};
    static final String[] CLASSES = {"fuzileiro", "medico", "engenheiro", "atirador"};
    static final String[] CLASSE_NOME = {"§aFuzileiro", "§dMédico", "§eEngenheiro", "§bAtirador"};

    enum Fase { ESPERA, CONTAGEM, JOGO, FIM }

    /** O que cada inimigo faz: distancia que gosta, intervalo e dano do tiro, rajada, pontaria. */
    record Perfil(double perto, double longe, int intervalo, double dano, int rajada, double pontaria) {}

    static final Map<Zumbis.Tipo, Perfil> PERFIS = Map.of(
            Zumbis.Tipo.SOLDADO, new Perfil(8, 15, 50, 1.5, 3, 0.45),
            Zumbis.Tipo.ESCOPETA, new Perfil(3, 6, 40, 4.0, 1, 0.7),
            Zumbis.Tipo.ATIRADOR, new Perfil(14, 60, 100, 8.0, 1, 0.85),
            Zumbis.Tipo.ESCUDO, new Perfil(2.5, 5, 26, 2.0, 1, 0.6),
            Zumbis.Tipo.PESADO, new Perfil(6, 16, 80, 1.2, 20, 0.38),
            Zumbis.Tipo.JUGGERNAUT, new Perfil(5, 18, 70, 1.5, 24, 0.42));

    final Tiro pl;
    World mundo;
    private Fase fase = Fase.ESPERA;
    private int contagem, setor, tempo, reforcos;
    private boolean setorAtivo, reforcoVeio;
    private final Set<UUID> noJogo = new HashSet<>(), caidos = new HashSet<>();
    private final Map<UUID, Integer> classe = new HashMap<>(), usos = new HashMap<>();
    private final Map<UUID, Long> proximoTiro = new HashMap<>(), miraAte = new HashMap<>(), ultimoKit = new HashMap<>();
    private final Map<UUID, Integer> rajada = new HashMap<>();
    /**
     * 16/11 (Warface): quem toma o tiro final nao morre na hora, cai DERRUBADO (deitado, sem atirar, sangrando 30 s).
     * Um companheiro agachado do lado levanta ele em 3 s; o Medico em 1 s, ou na hora com o kit (desfibrilador).
     * Valor = quando sangra ate morrer; progresso = segundos de socorro acumulados.
     */
    private final Map<UUID, Long> derrubados = new HashMap<>();
    private final Map<UUID, Integer> socorro = new HashMap<>();
    static final int SANGRA_S = 30;

    /*
     * 16/11 (Warface: "torreta de defesa cujo gerador tem que ser destruido de perto"): no Armazem (setor 2) uma torreta
     * no fundo varre o corredor com rajadas. Bala nao a derruba: alguem tem que chegar no GERADOR (parede esquerda, meio
     * do setor) e ficar agachado do lado 3 s (engenheiro: 1 s) para sabotar. O portao so abre com a torreta morta.
     */
    static final int SETOR_TORRETA = 1;
    private boolean torretaViva;
    private int sabotagem, torretaRajada;
    private long torretaProximo, torretaMira;

    /*
     * 16/11 (Warface: o "Mech" do fim da fase, blindado, com metralhadoras e foguetes; o ponto fraco e o bloco de
     * energia nas costas). No Heliporto o chefe agora e o MECHA W-2300: de frente quase nao toma dano (x0,2), de lado
     * x0,6, pelas costas x2. Depois de 3 rajadas ele SUPERAQUECE 6 s: para, solta fumaca e o nucleo fica exposto
     * (x3 de qualquer lado). Foguete: marca um circulo vermelho no chao e explode 1,6 s depois. Chegou perto: pisao.
     */
    private IronGolem mecha;
    private int mechaRajada, mechaRajadas;
    private long mechaProximo, mechaFoguete, mechaPisao, mechaQuenteAte;
    private Location focoFoguete;
    private long fogueteEm;

    private boolean mechaVivo() {
        return mecha != null && mecha.isValid() && !mecha.isDead();
    }

    private void soltarMecha(int s) {
        int n = Math.max(1, time().size());
        Location l = new Location(mundo, 0.5, Y + 1, z1(s) - 7 + 0.5, 180f, 0f);
        mecha = mundo.spawn(l, IronGolem.class, CreatureSpawnEvent.SpawnReason.CUSTOM, g -> {
            g.customName(Tiro.txt("§4§lMECHA W-2300"));
            g.setCustomNameVisible(true);
            g.setPlayerCreated(false);
            g.setRemoveWhenFarAway(false);
            g.setPersistent(false);
            double vida = 900 * (0.6 + 0.4 * n);
            atributo(g, org.bukkit.attribute.Attribute.MAX_HEALTH, vida);
            g.setHealth(vida);
            atributo(g, org.bukkit.attribute.Attribute.SCALE, 1.7);
            atributo(g, org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, 1.0);
            atributo(g, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, 0.2);
        });
        try {
            Bukkit.getMobGoals().removeAllGoals(mecha);
        } catch (Throwable ignorado) {
            // sem metas: so anda pelo que mandamos
        }
        long agora = System.currentTimeMillis();
        mechaRajada = 0;
        mechaRajadas = 0;
        mechaProximo = agora + 2500;
        mechaFoguete = agora + 7000;
        mechaPisao = agora;
        mechaQuenteAte = 0;
        focoFoguete = null;
        aviso("§4§lMECHA W-2300", "§7Blindado na frente. §fAtaque pelas costas§7 ou quando ele §cSUPERAQUECER", Sound.ENTITY_WITHER_SPAWN);
        for (Player p : time()) p.sendMessage(PREFIXO + "§4Mecha W-2300! §fFrente blindada; o §enúcleo de energia§f fica nas costas. A cada 3 rajadas ele §csuperaquece§f e o núcleo fica exposto. §7Saia do círculo vermelho: é foguete.");
    }

    private static void atributo(LivingEntity e, org.bukkit.attribute.Attribute a, double v) {
        var at = e.getAttribute(a);
        if (at != null) at.setBaseValue(v);
    }

    /** Empurra para longe no plano (se estiver bem no centro, para um lado qualquer) e um pouco para cima. */
    private Vector empurrao(Vector de, double forca, double cima) {
        de.setY(0);
        if (de.lengthSquared() < 1e-4) de = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5);
        if (de.lengthSquared() < 1e-6) de = new Vector(1, 0, 0);
        return de.normalize().multiply(forca).setY(cima);
    }

    private boolean superaquecido() {
        return System.currentTimeMillis() < mechaQuenteAte;
    }

    private void mechaIa(List<Player> alvos, long agora) {
        if (!mechaVivo()) return;
        Location c = mecha.getLocation();
        // foguete marcado: explode no tempo
        if (focoFoguete != null) {
            Particle.DustOptions po = new Particle.DustOptions(Color.RED, 1.2f);
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                mundo.spawnParticle(Particle.DUST, focoFoguete.clone().add(Math.cos(a) * 3.5, 0.2, Math.sin(a) * 3.5), 1, 0, 0, 0, 0, po);
            }
            if (agora >= fogueteEm) {
                mundo.spawnParticle(Particle.EXPLOSION, focoFoguete, 4, 1.2, 0.4, 1.2, 0);
                mundo.playSound(focoFoguete, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.9f);
                for (Player p : alvos)
                    if (p.getLocation().distanceSquared(focoFoguete) <= 3.5 * 3.5) {
                        p.setNoDamageTicks(0);
                        feridoEm.put(p.getUniqueId(), agora);
                        p.damage(9 * (1 + 0.12 * setor));
                        p.setVelocity(empurrao(p.getLocation().toVector().subtract(focoFoguete.toVector()), 0.7, 0.5));
                    }
                focoFoguete = null;
            }
        }
        if (superaquecido()) {
            mecha.getPathfinder().stopPathfinding();
            Location costas = c.clone().add(c.getDirection().setY(0).normalize().multiply(-0.9)).add(0, 2.6, 0);
            mundo.spawnParticle(Particle.LARGE_SMOKE, costas, 4, 0.3, 0.3, 0.3, 0.03);
            mundo.spawnParticle(Particle.LAVA, costas, 1, 0.2, 0.2, 0.2, 0);
            if (tick % 4 == 0) for (Player p : alvos) p.sendActionBar(Tiro.txt("§c§lNÚCLEO EXPOSTO! §fATIREM AGORA §7(dano x3)"));
            return;
        }
        Player alvo = null;
        double dm = 1e9;
        for (Player p : alvos) {
            double d = p.getLocation().distanceSquared(c);
            if (d < dm) {
                dm = d;
                alvo = p;
            }
        }
        if (alvo == null) return;
        double d = Math.sqrt(dm);
        Vector dir = alvo.getLocation().toVector().subtract(c.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        mecha.setRotation(yaw, 0f);
        try {
            mecha.setBodyYaw(yaw);
        } catch (Throwable ignorado) {
            // versao sem setBodyYaw
        }
        boolean ve = mecha.hasLineOfSight(alvo);
        if (mechaRajada <= 0) {
            if (!ve || d > 18) mecha.getPathfinder().moveTo(alvo.getLocation(), 0.9);
            else if (d < 9) mecha.getPathfinder().stopPathfinding();
        } else mecha.getPathfinder().stopPathfinding();
        // pisao em quem chega perto
        if (d < 4.2 && agora >= mechaPisao) {
            mechaPisao = agora + 4000;
            mundo.playSound(c, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
            mundo.spawnParticle(Particle.EXPLOSION, c, 2, 1, 0.1, 1, 0);
            for (Player p : alvos)
                if (p.getLocation().distanceSquared(c) <= 4.5 * 4.5) {
                    p.setNoDamageTicks(0);
                    feridoEm.put(p.getUniqueId(), agora);
                    p.damage(4);
                    p.setVelocity(empurrao(p.getLocation().toVector().subtract(c.toVector()), 1.3, 0.6));
                }
        }
        // foguete
        if (focoFoguete == null && agora >= mechaFoguete && ve) {
            mechaFoguete = agora + 11000;
            focoFoguete = alvo.getLocation().clone();
            fogueteEm = agora + 1600;
            mundo.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);
            alvo.sendActionBar(Tiro.txt("§c§lFOGUETE! §fSaia do círculo vermelho!"));
        }
        // metralhadoras: rajada de 10
        if (mechaRajada > 0) {
            mechaRajada--;
            if (ve) {
                Location boca = c.clone().add(0, 2.2, 0).add(empurrao(new Vector(-dir.getZ(), 0, dir.getX()), mechaRajada % 2 == 0 ? 1.1 : -1.1, 0));
                boolean acertou = rnd.nextDouble() < (alvo.isSneaking() ? 0.33 : 0.55);
                Location fim = alvo.getEyeLocation().subtract(0, 0.4, 0);
                if (!acertou) fim.add(rnd.nextGaussian() * 1.1, rnd.nextGaussian() * 0.7, rnd.nextGaussian() * 1.1);
                Vector v = fim.toVector().subtract(boca.toVector());
                double len = v.length();
                v.normalize();
                for (double k = 1; k < len; k += 1.6) mundo.spawnParticle(Particle.ELECTRIC_SPARK, boca.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0);
                mundo.playSound(boca, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 0.8f);
                if (acertou) {
                    alvo.setNoDamageTicks(0);
                    feridoEm.put(alvo.getUniqueId(), agora);
                    alvo.damage(2.2 * (1 + 0.12 * setor));
                    alvo.playSound(alvo.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1.2f);
                } else alvo.playSound(fim, Sound.ENTITY_ARROW_SHOOT, 0.35f, 1.9f);
            }
            if (mechaRajada == 0) {
                mechaProximo = agora + 2600;
                if (++mechaRajadas >= 3) {
                    mechaRajadas = 0;
                    mechaQuenteAte = agora + 6000;
                    mundo.playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, 2f, 0.6f);
                    mundo.playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.6f);
                    aviso("§c§lSUPERAQUECEU!", "§fNúcleo exposto por 6 s: §eATIREM!", null);
                }
            }
            return;
        }
        if (ve && d <= 30 && agora >= mechaProximo) {
            mechaRajada = 10;
            mundo.playSound(c, Sound.BLOCK_PISTON_EXTEND, 1.5f, 0.5f);
        }
    }

    private Location bocaTorreta() {
        return new Location(mundo, 0.5, Y + 4.5, z1(SETOR_TORRETA) - 3 + 0.5);
    }

    private Location gerador() {
        return new Location(mundo, -LARG + 1 + 0.5, Y + 1, z0(SETOR_TORRETA) + 20 + 0.5);
    }

    private void montarTorreta() {
        int tz = z1(SETOR_TORRETA) - 3;
        caixa(-2, Y + 1, tz - 2, 2, Y + 6, tz + 2, Material.AIR);
        caixa(-1, Y + 1, tz - 1, 1, Y + 1, tz + 1, Material.POLISHED_BLACKSTONE_BRICKS);
        caixa(0, Y + 2, tz, 0, Y + 3, tz, Material.POLISHED_BASALT);
        b(0, Y + 4, tz, Material.DISPENSER);
        if (mundo.getBlockAt(0, Y + 4, tz).getBlockData() instanceof org.bukkit.block.data.Directional d) {
            d.setFacing(org.bukkit.block.BlockFace.NORTH);
            mundo.getBlockAt(0, Y + 4, tz).setBlockData(d, false);
        }
        b(0, Y + 4, tz - 1, Material.IRON_BARS);
        b(-1, Y + 4, tz, Material.IRON_TRAPDOOR);
        b(1, Y + 4, tz, Material.IRON_TRAPDOOR);
        b(0, Y + 5, tz, Material.LIGHTNING_ROD);
        // gerador: nucleo de redstone com para-raios, encostado na parede
        Location g = gerador();
        int gx = g.getBlockX(), gz = g.getBlockZ();
        caixa(gx, Y + 1, gz - 2, gx + 2, Y + 4, gz + 2, Material.AIR);
        caixa(gx, Y + 1, gz - 1, gx, Y + 1, gz + 1, Material.WAXED_OXIDIZED_CUT_COPPER);
        b(gx, Y + 2, gz, Material.REDSTONE_BLOCK);
        b(gx, Y + 2, gz - 1, Material.WAXED_COPPER_GRATE);
        b(gx, Y + 2, gz + 1, Material.WAXED_COPPER_GRATE);
        b(gx, Y + 3, gz, Material.LIGHTNING_ROD);
        torretaViva = true;
        sabotagem = 0;
        torretaRajada = 0;
        torretaMira = 0;
        torretaProximo = System.currentTimeMillis() + 3000;
    }

    private void destruirTorreta(Player quem) {
        if (!torretaViva) return;
        torretaViva = false;
        int tz = z1(SETOR_TORRETA) - 3;
        b(0, Y + 4, tz, Material.BLACKSTONE);
        b(0, Y + 4, tz - 1, Material.AIR);
        b(0, Y + 5, tz, Material.AIR);
        b(-1, Y + 4, tz, Material.AIR);
        b(1, Y + 4, tz, Material.AIR);
        Location g = gerador();
        b(g.getBlockX(), Y + 2, g.getBlockZ(), Material.COAL_BLOCK);
        b(g.getBlockX(), Y + 3, g.getBlockZ(), Material.AIR);
        for (Location l : new Location[] {bocaTorreta(), g.clone().add(0, 1, 0)}) {
            mundo.spawnParticle(Particle.EXPLOSION, l, 3, 0.5, 0.5, 0.5, 0);
            mundo.spawnParticle(Particle.LARGE_SMOKE, l, 25, 0.5, 0.8, 0.5, 0.02);
            mundo.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        }
        aviso("§a§lTORRETA DESTRUÍDA", "§7" + (quem != null ? quem.getName() + " sabotou o gerador" : "Caminho livre"), null);
        if (quem != null) pl.coronas(quem, 10, "por sabotar a torreta na Missão");
    }

    private void desmontarTorreta() {
        int tz = z1(SETOR_TORRETA) - 3;
        caixa(-2, Y + 1, tz - 2, 2, Y + 6, tz + 2, Material.AIR);
        Location g = gerador();
        caixa(g.getBlockX(), Y + 1, g.getBlockZ() - 2, g.getBlockX() + 2, Y + 4, g.getBlockZ() + 2, Material.AIR);
        torretaViva = false;
    }

    /** A cada segundo: sabotagem do gerador e a faisca que mostra onde ele esta. */
    private void geradorSegundo() {
        if (!torretaViva) return;
        Location g = gerador().add(0, 1.5, 0);
        mundo.spawnParticle(Particle.ELECTRIC_SPARK, g, 12, 0.4, 0.6, 0.4, 0.05);
        if (tempo % 2 == 0) mundo.playSound(g, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.6f);
        Player quem = null;
        for (Player p : vivos()) if (p.isSneaking() && p.getLocation().distanceSquared(gerador()) <= 6.5) {
            quem = p;
            if (classe.getOrDefault(p.getUniqueId(), 0) == 2) break;
        }
        if (quem == null) {
            sabotagem = 0;
            return;
        }
        int precisa = classe.getOrDefault(quem.getUniqueId(), 0) == 2 ? 1 : 3;
        sabotagem++;
        quem.sendActionBar(Tiro.txt("§eSabotando o gerador " + "█".repeat(sabotagem) + "§8" + "█".repeat(Math.max(0, precisa - sabotagem))));
        mundo.playSound(g, Sound.BLOCK_ANVIL_USE, 0.6f, 1.8f);
        if (sabotagem >= precisa) destruirTorreta(quem);
    }

    /** A torreta mira (laser vermelho) e solta uma rajada em quem ela ve. Chamado pela IA (a cada 4 ticks). */
    private void torretaIa(List<Player> alvos, long agora) {
        if (!torretaViva) return;
        Location boca = bocaTorreta();
        Player alvo = null;
        double dm = 34 * 34;
        for (Player p : alvos) {
            double d = p.getEyeLocation().distanceSquared(boca);
            if (d > dm) continue;
            Vector v = p.getEyeLocation().toVector().subtract(boca.toVector());
            var hit = mundo.rayTraceBlocks(boca.clone().add(v.clone().normalize().multiply(1.2)), v, Math.sqrt(d) - 1.2, org.bukkit.FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null) continue;   // atras de cobertura
            dm = d;
            alvo = p;
        }
        if (alvo == null) {
            torretaRajada = 0;
            torretaMira = 0;
            return;
        }
        if (torretaRajada > 0) {
            torretaRajada--;
            Vector v = alvo.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(boca.toVector());
            boolean acertou = rnd.nextDouble() < (alvo.isSneaking() ? 0.35 : 0.6);
            Location fim = alvo.getEyeLocation().subtract(0, 0.4, 0);
            if (!acertou) fim.add(rnd.nextGaussian() * 1.1, rnd.nextGaussian() * 0.7, rnd.nextGaussian() * 1.1);
            v = fim.toVector().subtract(boca.toVector());
            double len = v.length();
            v.normalize();
            for (double k = 1; k < len; k += 1.6) mundo.spawnParticle(Particle.ELECTRIC_SPARK, boca.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0);
            mundo.playSound(boca, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 0.7f);
            mundo.playSound(boca, Sound.BLOCK_DISPENSER_LAUNCH, 0.6f, 1.6f);
            if (acertou) {
                alvo.setNoDamageTicks(0);
                feridoEm.put(alvo.getUniqueId(), agora);
                alvo.damage(2.5 * (1 + 0.12 * setor));
                alvo.playSound(alvo.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1.2f);
            } else alvo.playSound(fim, Sound.ENTITY_ARROW_SHOOT, 0.35f, 1.9f);
            if (torretaRajada == 0) torretaProximo = agora + 2200;
            return;
        }
        if (agora < torretaProximo) return;
        if (torretaMira == 0) {
            torretaMira = agora + 1100;
            mundo.playSound(boca, Sound.BLOCK_NOTE_BLOCK_BIT, 1.2f, 0.5f);
        }
        // laser de mira
        Vector v = alvo.getEyeLocation().subtract(0, 0.3, 0).toVector().subtract(boca.toVector());
        double len = v.length();
        v.normalize();
        Particle.DustOptions po = new Particle.DustOptions(Color.RED, 0.6f);
        for (double k = 1; k < len; k += 0.9) mundo.spawnParticle(Particle.DUST, boca.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0, po);
        if (agora >= torretaMira) {
            torretaMira = 0;
            torretaRajada = 6;
        }
    }

    boolean derrubado(Player p) {
        return derrubados.containsKey(p.getUniqueId());
    }
    /** Quando cada jogador levou o ultimo tiro (a vida volta sozinha depois de 5 s em cobertura) e quando alguem
     *  comecou a atirar nele (os soldados se revezam: nao abrem fogo todos no mesmo instante). */
    private final Map<UUID, Long> feridoEm = new HashMap<>(), fogoEm = new HashMap<>();
    private final Random rnd = new Random();
    private final BossBar barra = BossBar.bossBar(Tiro.txt("§c§lMISSÃO"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private int tick;
    /** true enquanto o dano que esta saindo e de um TIRO de soldado (o soco deles e cancelado). */
    private boolean bala;

    Missao(Tiro pl) {
        this.pl = pl;
        mundo = new WorldCreator(Tiro.MISSAO).generator(new Tiro.Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        Tiro.regrasDeLobby(mundo);
        mundo.setDifficulty(org.bukkit.Difficulty.NORMAL);
        mundo.setTime(4500);   // 16/11: mapa Favela, de dia
        for (int cx = -4; cx <= 3; cx++) for (int cz = -3; cz <= (SETORES * COMP + 30) / 16; cz++) mundo.addPluginChunkTicket(cx, cz, pl);
        if (pl.getConfig().getInt("missao-mapa", 0) != MAPA) {
            construir();
            pl.getConfig().set("missao-mapa", MAPA);
            pl.saveConfig();
        }
        limpar();
        Bukkit.getScheduler().runTaskTimer(pl, this::segundo, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(pl, this::ia, 5L, 4L);
    }

    void desligar() {
        if (mundo == null) return;
        limpar();
        for (Player p : new ArrayList<>(mundo.getPlayers())) tirar(p, false);
    }

    // ================================================================== mapa

    private void b(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    private void caixa(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, m);
    }

    private int larg(int s) {
        return s == SETORES - 1 ? 18 : LARG;
    }

    private int z0(int s) {
        return s * COMP + 2;
    }

    private int z1(int s) {
        return (s + 1) * COMP - 1;
    }

    Location sala() {
        return new Location(mundo, 0.5, Y + 1, -30.5, 0f, 0f);
    }

    private Location partida(int s) {
        return new Location(mundo, 0.5 + rnd.nextInt(5) - 2, Y + 1, (s <= 0 ? -6 : z0(s) + 1) + 0.5, 0f, 0f);
    }

    private void portao(int s, boolean aberto) {
        int z = s * COMP;                                                        // portao de ENTRADA do setor s
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) for (int dz = 0; dz <= 1; dz++) b(x, Y + y, z + dz, aberto ? Material.AIR : Material.IRON_BARS);
    }

// ---------------------------------------------------------------- 16/11: mapa "Favela" (Paulo mandou fotos do mapa
    // Favela do Warface): ruas de asfalto rachado, casas de tijolo e reboco colorido empilhadas no morro, lajes com caixa
    // d'agua, varais, postes com fios, palmeiras, campo de terra com traves, galpao e o heliponto no alto, com serras no fundo.

    private static final Material[] PAREDES = {Material.BRICKS, Material.BRICKS, Material.BRICKS, Material.TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.CYAN_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.ORANGE_TERRACOTTA};

    private void bd(int x, int y, int z, org.bukkit.block.data.BlockData d) {
        mundo.getBlockAt(x, y, z).setBlockData(d, false);
    }

    /** Grade/cerca/barra de ferro ligada nos lados pedidos (sem fisica elas viram so um poste). */
    private void ligado(int x, int y, int z, Material m, boolean norteSul, boolean lesteOeste) {
        org.bukkit.block.data.BlockData d = Bukkit.createBlockData(m);
        if (d instanceof org.bukkit.block.data.MultipleFacing f) {
            if (norteSul) {
                f.setFace(org.bukkit.block.BlockFace.NORTH, true);
                f.setFace(org.bukkit.block.BlockFace.SOUTH, true);
            }
            if (lesteOeste) {
                f.setFace(org.bukkit.block.BlockFace.EAST, true);
                f.setFace(org.bukkit.block.BlockFace.WEST, true);
            }
        }
        bd(x, y, z, d);
    }

    private void fio(int x, int y, int z, org.bukkit.Axis eixo) {
        org.bukkit.block.data.BlockData d = Bukkit.createBlockData(Material.IRON_CHAIN);
        if (d instanceof org.bukkit.block.data.Orientable o) o.setAxis(eixo);
        bd(x, y, z, d);
    }

    private void folha(int x, int y, int z) {
        org.bukkit.block.data.BlockData d = Bukkit.createBlockData(Material.JUNGLE_LEAVES);
        if (d instanceof org.bukkit.block.data.type.Leaves l) l.setPersistent(true);
        if (mundo.getBlockAt(x, y, z).getType().isAir()) bd(x, y, z, d);
    }

    private void palmeira(int x, int y, int z, Random r) {
        int h = 6 + r.nextInt(4), dx = 0, dz = 0;
        for (int k = 0; k < h; k++) {
            if (k == h / 2) {
                dx = r.nextInt(3) - 1;
                dz = r.nextInt(3) - 1;
            }
            b(x + (k >= h / 2 ? dx : 0), y + k, z + (k >= h / 2 ? dz : 0), Material.JUNGLE_LOG);
        }
        int tx = x + dx, ty = y + h, tz = z + dz;
        folha(tx, ty, tz);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            folha(tx + d[0], ty, tz + d[1]);
            folha(tx + 2 * d[0], ty, tz + 2 * d[1]);
            folha(tx + 3 * d[0], ty - 1, tz + 3 * d[1]);
        }
        for (int[] d : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            folha(tx + d[0], ty, tz + d[1]);
            folha(tx + 2 * d[0], ty - 1, tz + 2 * d[1]);
        }
    }

    /**
     * Casa de favela: paredes de tijolo ou reboco, uma faixa de concreto em cada laje, janelas escuras, laje no alto
     * (as vezes com caixa d'agua azul, ferro de obra aparecendo ou telhado de zinco). oca = so a casca.
     */
    private void casa(int x1, int z1, int x2, int z2, int base, int andares, Random r, boolean oca) {
        Material par = PAREDES[r.nextInt(PAREDES.length)];
        boolean obra = par == Material.BRICKS && r.nextBoolean();
        int topo = base + andares * 4;
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++) {
                boolean borda = x == ax || x == bx || z == az || z == bz;
                if (!borda && oca) continue;
                for (int y = base + 1; y <= topo; y++) {
                    int dy = (y - base - 1) % 4;
                    Material m = par;
                    if (dy == 3) m = Material.SMOOTH_STONE;                              // viga/laje
                    else if (obra && borda && ((x - ax) % 4 == 0 || (z - az) % 4 == 0)) m = Material.LIGHT_GRAY_CONCRETE;   // pilar
                    else if (borda && dy == 1 && (x + z) % 3 == 0 && !(x == ax && z == az) && !(x == bx && z == bz)) m = r.nextInt(5) == 0 ? Material.GLASS : Material.BLACK_STAINED_GLASS;
                    b(x, y, z, m);
                }
            }
        // laje
        for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) b(x, topo + 1, z, x == ax || x == bx || z == az || z == bz ? Material.SMOOTH_STONE_SLAB : Material.AIR);
        int enfeite = r.nextInt(4);
        if (enfeite == 0 && bx - ax >= 3 && bz - az >= 3) caixa(ax + 1, topo + 1, az + 1, ax + 2, topo + 2, az + 2, r.nextBoolean() ? Material.LIGHT_BLUE_CONCRETE : Material.BLUE_CONCRETE);
        else if (enfeite == 1) for (int[] c : new int[][] {{ax, az}, {bx, bz}}) caixa(c[0], topo + 1, c[1], c[0], topo + 2, c[1], Material.IRON_BARS);
        else if (enfeite == 2) caixa(ax, topo + 1, az, bx, topo + 1, bz, r.nextBoolean() ? Material.WAXED_EXPOSED_CUT_COPPER_SLAB : Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        if (r.nextInt(6) == 0) caixa(bx, topo + 1, bz, bx, topo + 3, bz, Material.LIGHTNING_ROD);   // antena
    }

    /** Morro do fundo: so a "pele" (3 camadas) de grama, terra e pedra, com mato e palmeira de vez em quando. */
    private void morro(int x, int z, int altura, Random r) {
        // 16/11: o morro vai ate o chao (so a "pele" ficava flutuando no ceu, visto de baixo)
        for (int y = Y; y <= altura; y++) b(x, y, z, y == altura ? (r.nextInt(7) == 0 ? Material.STONE : Material.GRASS_BLOCK) : y >= altura - 2 ? Material.DIRT
                : (x * 3 + y * 5 + z) % 7 == 0 ? Material.ANDESITE : Material.STONE);
        int k = r.nextInt(40);
        if (k == 0) palmeira(x, altura + 1, z, r);
        else if (k < 3) folha(x, altura + 1, z);
        else if (k < 12) b(x, altura + 1, z, Material.SHORT_GRASS);
    }

    private void carro(int x, int z, boolean deLado, Material cor, boolean queimado) {
        int lx = deLado ? 3 : 1, lz = deLado ? 1 : 3;
        caixa(x, Y + 1, z, x + lx, Y + 1, z + lz, queimado ? Material.BLACKSTONE : cor);
        int cx = x + (deLado ? 1 : 0), cz = z + (deLado ? 0 : 1);
        caixa(cx, Y + 2, cz, cx + (deLado ? 1 : 1), Y + 2, cz + (deLado ? 1 : 1), queimado ? Material.COAL_BLOCK : Material.GRAY_STAINED_GLASS);
        if (queimado) b(cx, Y + 3, cz, Material.CAMPFIRE);
    }

    private void poste(int x, int z, int h) {
        caixa(x, Y + 1, z, x, Y + h, z, Material.STRIPPED_DARK_OAK_LOG);
        b(x, Y + h + 1, z, Material.LIGHTNING_ROD);
    }

    private void varal(int x1, int x2, int y, int z, Random r) {
        Material[] roupa = {Material.WHITE_WOOL, Material.LIGHT_BLUE_WOOL, Material.PINK_WOOL, Material.YELLOW_WOOL, Material.RED_WOOL};
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            fio(x, y, z, org.bukkit.Axis.X);
            if (r.nextInt(3) == 0 && mundo.getBlockAt(x, y - 1, z).getType().isAir()) b(x, y - 1, z, roupa[r.nextInt(roupa.length)]);
        }
    }

    private void sacos(int x, int z, boolean deLado) {
        if (deLado) {
            caixa(x, Y + 1, z - 1, x, Y + 1, z + 1, Material.MUD_BRICKS);
            b(x, Y + 2, z, Material.MUD_BRICK_SLAB);
        } else {
            caixa(x - 1, Y + 1, z, x + 1, Y + 1, z, Material.MUD_BRICKS);
            b(x, Y + 2, z, Material.MUD_BRICK_SLAB);
        }
    }

    private void construir() {
        Random r = new Random(16);
        // sala de espera (briefing)
        caixa(-6, Y - 1, -36, 6, Y + 8, -24, Material.AIR);
        caixa(-6, Y, -36, 6, Y, -24, Material.POLISHED_DEEPSLATE);
        for (int x = -6; x <= 6; x++) for (int z = -36; z <= -24; z++) if (Math.abs(x) == 6 || z == -36 || z == -24) caixa(x, Y + 1, z, x, Y + 5, z, Material.DEEPSLATE_BRICKS);
        caixa(-6, Y + 6, -36, 6, Y + 6, -24, Material.DEEPSLATE_TILES);
        for (int[] c : new int[][] {{-4, -34}, {4, -34}, {-4, -26}, {4, -26}, {0, -30}}) b(c[0], Y + 6, c[1], Material.SEA_LANTERN);
        int fim = SETORES * COMP + 4;
        // limpa o mapa antigo inteiro (o corredor e os muros iam ate x=+-19)
        for (int z = -14; z <= fim + 1; z++) for (int x = -21; x <= 21; x++) for (int y = Y + 1; y <= Y + 16; y++) b(x, y, z, Material.AIR);
        // chao de cada setor
        for (int z = -14; z <= fim; z++) {
            int s = Math.max(0, Math.min(SETORES - 1, Math.floorDiv(z, COMP))), L = z < 0 ? 6 : larg(s);
            for (int x = -L - 1; x <= L + 1; x++) {
                b(x, Y - 1, z, Material.DEEPSLATE);
                int ax = Math.abs(x);
                Material chao = switch (s) {
                    case 0 -> ax <= 4 ? (ax == 0 && Math.floorMod(z, 4) < 2 ? Material.YELLOW_CONCRETE : r.nextInt(6) == 0 ? Material.GRAY_CONCRETE : Material.BLACK_CONCRETE)
                            : ax == 5 ? Material.STONE_BRICKS : r.nextInt(5) == 0 ? Material.CRACKED_STONE_BRICKS : Material.SMOOTH_STONE;
                    case 1 -> r.nextInt(4) == 0 ? Material.MUD : r.nextInt(3) == 0 ? Material.COARSE_DIRT : Material.PACKED_MUD;
                    case 2 -> ax == L - 1 && z > z0(s) + 2 && z < z1(s) - 2 ? Material.WHITE_CONCRETE
                            : r.nextInt(5) == 0 ? Material.GRASS_BLOCK : r.nextInt(3) == 0 ? Material.COARSE_DIRT : Material.DIRT_PATH;
                    case 3 -> (x % 4 == 0 || z % 4 == 0) ? Material.SPRUCE_PLANKS : Material.STRIPPED_SPRUCE_WOOD;
                    default -> Math.hypot(x, z - (z0(s) + 20)) < 7 ? (Math.hypot(x, z - (z0(s) + 20)) > 5.6 ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE)
                            : Math.abs(x) <= 9 && Math.abs(z - (z0(s) + 20)) <= 9 ? Material.LIGHT_GRAY_CONCRETE
                            : r.nextInt(4) == 0 ? Material.GRASS_BLOCK : r.nextInt(3) == 0 ? Material.ROOTED_DIRT : Material.COARSE_DIRT;
                };
                if (z < 0) chao = r.nextInt(5) == 0 ? Material.GRAVEL : Material.BLACK_CONCRETE;
                b(x, Y, z, chao);
            }
            // poca d'agua na rua de terra (como na foto)
            if (s == 1 && z > z0(1) + 6 && z < z1(1) - 6 && Math.floorMod(z, 11) < 3) b(r.nextInt(3) - 1, Y, z, Material.WATER);
        }
        // letreiro "H" no heliponto
        int hz = z0(SETORES - 1) + 20;
        caixa(-2, Y, hz - 3, -2, Y, hz + 3, Material.WHITE_CONCRETE);
        caixa(2, Y, hz - 3, 2, Y, hz + 3, Material.WHITE_CONCRETE);
        caixa(-2, Y, hz, 2, Y, hz, Material.WHITE_CONCRETE);
        // ---- casas da frente (o "muro" de cada lado e uma fileira de casas de 2 a 3 andares) e a favela subindo o morro
        for (int lado = -1; lado <= 1; lado += 2) {
            int z = -14;
            while (z <= fim) {
                int s = Math.max(0, Math.min(SETORES - 1, Math.floorDiv(z, COMP))), L = z < 0 ? 6 : larg(s);
                int w = 4 + r.nextInt(4), ate = Math.min(fim, z + w);
                int sNo = Math.max(0, Math.min(SETORES - 1, Math.floorDiv(ate, COMP)));
                if (sNo != s) ate = Math.max(z, sNo * COMP - 1);   // nao atravessa a largura de outro setor
                if (z < 0) ate = Math.min(ate, -1);
                int x1 = lado * (L + 1), x2 = lado * (L + 6);
                casa(x1, z, x2, ate, Y, s == SETORES - 1 ? 1 + r.nextInt(2) : 2 + r.nextInt(2), r, false);
                // varanda virada para a rua
                if (r.nextInt(3) == 0 && ate - z >= 3) {
                    for (int zz = z + 1; zz <= ate - 1; zz++) {
                        b(lado * L, Y + 4, zz, Material.SMOOTH_STONE_SLAB);
                        ligado(lado * L, Y + 5, zz, Material.IRON_BARS, true, false);
                    }
                }
                // barreira invisivel por cima: ninguem sobe nos telhados para sair do mapa
                for (int zz = z; zz <= ate; zz++) for (int y = Y + 1; y <= Y + 24; y++)
                    if (mundo.getBlockAt(lado * (L + 1), y, zz).getType().isAir()) b(lado * (L + 1), y, zz, Material.BARRIER);
                z = ate + 1;
            }
            // morro com casinhas em degraus e as serras la atras
            for (int z2 = -20; z2 <= fim + 6; z2 += 5 + r.nextInt(3)) {
                int s = Math.max(0, Math.min(SETORES - 1, Math.floorDiv(Math.max(0, z2), COMP))), L = larg(s);
                for (int faixa = 0; faixa < 5; faixa++) {
                    int xa = lado * (L + 8 + faixa * 5), base = Y + 6 + faixa * 4 + r.nextInt(2);
                    for (int x = xa; x != xa + lado * 5; x += lado) for (int zz = z2; zz < z2 + 7; zz++) morro(x, zz, base, r);
                    if (r.nextInt(5) > 0) casa(xa + lado, z2, xa + lado * (2 + r.nextInt(2)), z2 + 3 + r.nextInt(2), base, 1 + r.nextInt(2), r, true);
                }
            }
            for (int zz = -24; zz <= fim + 12; zz++)
                for (int k = 0; k < 9; k++) {
                    int x = lado * (37 + k);
                    int alt = Y + 30 + (int) (7 * Math.sin(zz / 17.0 + lado) + 5 * Math.sin(zz / 7.3) + k * 1.5);
                    morro(x, zz, alt, r);
                }
        }
        // serra no fim do mapa (fecha a vista do heliponto) e um morro alto "pao de acucar" ao fundo
        for (int x = -45; x <= 45; x++)
            for (int k = 0; k < 22; k++) {
                int zz = fim + 3 + k;
                int alt = Y + 9 + (int) (5 * Math.sin(x / 9.0) + k * 1.4) + (int) Math.max(0, 16 - Math.abs(x - 12) * 1.2 - Math.max(0, 12 - k) * 1.3);
                morro(x, zz, alt, r);
            }
        // portoes entre setores: muro de tijolo grafitado
        Material[] grafite = {Material.LIME_CONCRETE, Material.MAGENTA_CONCRETE, Material.ORANGE_CONCRETE, Material.CYAN_CONCRETE, Material.YELLOW_CONCRETE};
        for (int s = 0; s < SETORES; s++) {
            int L = larg(s);
            for (int z = s * COMP; z <= s * COMP + 1; z++)
                for (int x = -L; x <= L; x++) {
                    for (int y = 1; y <= 8; y++) {
                        Material m = y == 5 && Math.abs(x) <= 3 ? Material.RED_CONCRETE : r.nextInt(9) == 0 ? Material.CRACKED_STONE_BRICKS : Material.BRICKS;
                        if (z == s * COMP && y >= 2 && y <= 4 && Math.abs(x) >= 3 && (x * 7 + y * 3) % 5 == 0) m = grafite[Math.floorMod(x + y + s, grafite.length)];
                        b(x, Y + y, z, m);
                    }
                    for (int y = 9; y <= 24; y++) b(x, Y + y, z, Material.BARRIER);
                }
        }
        // fundo do corredor e comeco
        for (int x = -6; x <= 6; x++) for (int y = 1; y <= 8; y++) b(x, Y + y, -14, Material.BRICKS);
        for (int x = -18; x <= 18; x++) for (int y = 1; y <= 24; y++) b(x, Y + y, fim, y <= 8 ? Material.BRICKS : Material.BARRIER);
        for (int s = 0; s < SETORES; s++) portao(s, false);
        // ---- cada setor
        for (int s = 0; s < SETORES; s++) {
            int L = larg(s), za = z0(s) + 3, zb = z1(s) - 3;
            switch (s) {
                case 0 -> {   // entrada da favela: rua com carros, postes, fios e barricadas
                    carro(-3, za + 6, false, Material.YELLOW_CONCRETE, false);
                    carro(2, za + 17, false, Material.WHITE_CONCRETE, true);
                    carro(-9, za + 26, true, Material.RED_CONCRETE, false);
                    for (int z = za + 2; z <= zb; z += 9) {
                        poste(-L + 1, z, 7);
                        poste(L - 1, z + 4, 7);
                        for (int x = -L + 1; x <= L - 1; x++) fio(x, Y + 7, z + (x > 0 ? 4 : 0) + 0, org.bukkit.Axis.X);
                    }
                    for (int z = za + 3; z <= zb; z += 8) sacos(r.nextBoolean() ? -7 : 7, z, false);
                    caixa(6, Y + 1, za + 12, 8, Y + 2, za + 13, Material.GREEN_CONCRETE);   // cacamba de lixo
                    caixa(-8, Y + 1, za + 20, -7, Y + 1, za + 21, Material.BARREL);
                    palmeira(-L + 2, Y + 1, za + 15, r);
                    palmeira(L - 2, Y + 1, za + 30, r);
                }
                case 1 -> {   // becos: casas no meio, beco central (a torreta olha por ele) e becos dos lados
                    for (int[] seg : new int[][] {{za + 1, za + 9}, {za + 21, za + 29}}) {
                        casa(-10, seg[0], -5, seg[1], Y, 1, r, false);
                        casa(5, seg[0] + 2, 10, seg[1] - 1, Y, 1 + r.nextInt(2), r, false);
                    }
                    for (int z = za + 2; z <= zb; z += 6) varal(-L, -6, Y + 7, z, r);
                    for (int z = za + 4; z <= zb; z += 7) varal(6, L, Y + 7, z, r);
                    for (int z = za; z <= zb; z += 3) {   // telhadinho de zinco por cima do beco da esquerda
                        b(-L, Y + 5, z, Material.WAXED_EXPOSED_CUT_COPPER_SLAB);
                        b(-L + 1, Y + 5, z, Material.WAXED_EXPOSED_CUT_COPPER_SLAB);
                    }
                    for (int z = za + 6; z <= zb; z += 10) sacos(r.nextBoolean() ? -2 : 2, z, false);
                    caixa(-3, Y + 1, za + 15, -2, Y + 1, za + 15, Material.BARREL);
                    palmeira(L - 1, Y + 1, za + 12, r);
                }
                case 2 -> {   // campo de futebol de terra: traves, alambrado e casas em volta
                    for (int zg : new int[] {za + 1, zb - 1}) {
                        caixa(-3, Y + 1, zg, -3, Y + 3, zg, Material.WHITE_CONCRETE);
                        caixa(3, Y + 1, zg, 3, Y + 3, zg, Material.WHITE_CONCRETE);
                        caixa(-3, Y + 3, zg, 3, Y + 3, zg, Material.WHITE_CONCRETE);
                    }
                    for (int z = za; z <= zb; z++)
                        if (Math.floorMod(z - za, 12) < 8)
                            for (int y = 1; y <= 3; y++) {
                                ligado(-L + 3, Y + y, z, Material.IRON_BARS, true, false);
                                ligado(L - 3, Y + y, z, Material.IRON_BARS, true, false);
                            }
                    carro(-L + 1, za + 4, false, Material.LIGHT_BLUE_CONCRETE, true);
                    palmeira(L - 1, Y + 1, za + 18, r);
                    palmeira(-L + 1, Y + 1, za + 22, r);
                    for (int z = za + 8; z <= zb - 4; z += 9) sacos(r.nextInt(7) - 3, z, false);
                    caixa(5, Y + 1, za + 14, 6, Y + 2, za + 15, Material.BARREL);
                    caixa(-6, Y + 1, za + 24, -5, Y + 1, za + 26, Material.SPRUCE_PLANKS);
                }
                case 3 -> {   // galpao da milicia: coberto, caixotes de armas, mesas e lampioes
                    caixa(-L, Y + 9, z0(s), L, Y + 9, z1(s), Material.WAXED_WEATHERED_CUT_COPPER);
                    for (int z = za; z <= zb; z += 7)
                        for (int x = -L + 3; x <= L - 3; x += 6) {
                            b(x, Y + 8, z, Material.IRON_CHAIN);
                            b(x, Y + 7, z, Material.LANTERN);
                        }
                    for (int k = 0; k < 12; k++) {
                        int x = r.nextInt(2 * L - 3) - L + 2, z = za + 2 + r.nextInt(Math.max(1, zb - za - 4));
                        if (Math.abs(x) < 2) x += 3;
                        switch (r.nextInt(3)) {
                            case 0 -> caixa(x, Y + 1, z, x + 1, Y + 2, z + 1, Material.GREEN_TERRACOTTA);   // caixote de armas
                            case 1 -> {
                                caixa(x, Y + 1, z, x + 2, Y + 1, z, Material.SPRUCE_FENCE);
                                caixa(x, Y + 2, z, x + 2, Y + 2, z, Material.SPRUCE_SLAB);
                            }
                            default -> caixa(x, Y + 1, z, x, Y + 2, z, Material.BARREL);
                        }
                    }
                }
                default -> {   // topo do morro: heliponto, helicoptero caido, bunkers de sacos e tambores
                    int bz = za + 6, bx = -L + 3;
                    caixa(bx, Y + 1, bz, bx + 2, Y + 3, bz + 8, Material.GRAY_CONCRETE);          // fuselagem
                    caixa(bx + 1, Y + 1, bz + 9, bx + 1, Y + 2, bz + 14, Material.GRAY_CONCRETE);  // cauda
                    caixa(bx, Y + 2, bz, bx + 2, Y + 2, bz, Material.BLACK_STAINED_GLASS);
                    caixa(bx - 3, Y + 4, bz + 2, bx + 5, Y + 4, bz + 2, Material.SMOOTH_STONE_SLAB);   // helice
                    caixa(bx + 1, Y + 4, bz - 2, bx + 1, Y + 4, bz + 6, Material.SMOOTH_STONE_SLAB);
                    b(bx + 1, Y + 4, bz + 4, Material.CAMPFIRE);
                    for (int[] c : new int[][] {{L - 4, za + 4}, {L - 6, za + 4}, {L - 4, za + 6}}) caixa(c[0], Y + 1, c[1], c[0], Y + 2, c[1], Material.RED_CONCRETE);
                    for (int z = za + 10; z <= zb - 8; z += 8) {
                        sacos(-8, z, false);
                        sacos(8, z + 4, false);
                    }
                    for (int z = za; z <= zb; z++)
                        if (Math.floorMod(z, 7) < 5) {
                            ligado(-L + 1, Y + 1, z, Material.IRON_BARS, true, false);
                            ligado(L - 1, Y + 1, z, Material.IRON_BARS, true, false);
                        }
                    palmeira(-L + 2, Y + 1, zb - 2, r);
                    palmeira(L - 2, Y + 1, za + 16, r);
                }
            }
            // corredor do meio livre junto do portao e as "lajes" de vigia no fundo (atiradores de elite)
            caixa(-1, Y + 1, z0(s), 1, Y + 3, z0(s) + 3, Material.AIR);
            if (s >= 2) for (int sx = -1; sx <= 1; sx += 2) {
                int tx = sx * (L - 2), tz = z1(s) - 5;
                caixa(tx - 1, Y + 1, tz - 1, tx + 1, Y + 4, tz + 1, Material.AIR);
                caixa(tx - 1, Y + 4, tz - 1, tx + 1, Y + 4, tz + 1, Material.SMOOTH_STONE);
                for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) caixa(tx + c[0], Y + 1, tz + c[1], tx + c[0], Y + 3, tz + c[1], Material.LIGHT_GRAY_CONCRETE);
                caixa(tx - 1, Y + 5, tz - 1, tx + 1, Y + 5, tz - 1, Material.MUD_BRICK_SLAB);
            }
        }
        // luz invisivel onde e coberto ou fechado (o resto e de dia)
        org.bukkit.block.data.type.Light luz = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(14);
        for (int z = -12; z <= fim - 2; z += 4) {
            int s = Math.max(0, Math.min(SETORES - 1, Math.floorDiv(z, COMP))), L = z < 0 ? 6 : larg(s);
            for (int x = -L + 1; x <= L - 1; x += 4)
                for (int y = Y + 5; y >= Y + 2; y--) {
                    var bl = mundo.getBlockAt(x, y, z);
                    if (bl.getType().isAir()) {
                        bl.setBlockData(luz, false);
                        break;
                    }
                }
        }
        for (int x = -4; x <= 4; x += 4) for (int z = -34; z <= -26; z += 4) mundo.getBlockAt(x, Y + 3, z).setBlockData(luz, false);
        pl.getLogger().info("Missao: mapa Favela construido (" + SETORES + " setores).");
    }

    // ================================================================== entrar e sair

    boolean aqui(Player p) {
        return mundo != null && p.getWorld() == mundo;
    }

    private List<Player> time() {
        List<Player> l = new ArrayList<>();
        for (UUID u : noJogo) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && aqui(p)) l.add(p);
        }
        return l;
    }

    private List<Player> vivos() {
        List<Player> l = time();
        l.removeIf(p -> caidos.contains(p.getUniqueId()) || p.isDead() || derrubados.containsKey(p.getUniqueId()));
        return l;
    }

    void entrar(Player p) {
        if (mundo == null) {
            p.sendMessage(PREFIXO + "§cA Missão está indisponível agora.");
            return;
        }
        if (aqui(p)) {
            p.sendMessage(PREFIXO + "§7Você já está na base. §f/missao sair §7para voltar ao lobby.");
            return;
        }
        p.teleport(sala());
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !aqui(p)) return;
            preparar(p);
            p.showTitle(Title.title(Tiro.txt("§c§lMISSÃO"), Tiro.txt("§7Invada a base e chegue ao Heliporto")));
            p.sendMessage(PREFIXO + "§fCo-op de 1 a 4§7 contra soldados inimigos. Avance §e5 setores§7; cada portão é um ponto de controle.");
            p.sendMessage(PREFIXO + "§7Escolha a classe com os itens da mão. §eUse cobertura§7: eles atiram de verdade, e a vida volta depois de 5 s sem levar tiro.");
            if (fase == Fase.JOGO) p.sendMessage(PREFIXO + "§eTem uma missão em andamento: você entra na próxima.");
        }, 10L);
    }

    private void preparar(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.setHealth(Math.min(20, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        p.setFoodLevel(20);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        Material[] ic = {Material.IRON_HOE, Material.RED_DYE, Material.IRON_INGOT, Material.SPYGLASS};
        String[] d = {"§7Fuzil de assalto. Bom em tudo.", "§7Escopeta + kit que cura o time.", "§7Submetralhadora + placas de blindagem para o time.", "§7Rifle de precisão. Fique atrás."};
        // 15/11 (Paulo: "os itens ainda aparecem como enxada"): quem tem o pacote ve a ARMA de cada classe no icone
        String[] armaDaClasse = {"fuzil", "escopeta", "smg", "sniper"};
        boolean desenho = pl.visual != null && pl.visual.tem(p);
        for (int i = 0; i < 4; i++) {
            ItemStack it = pl.armas.item("classe" + i, desenho ? Armas.ARMAS.get(armaDaClasse[i]).item() : ic[i], CLASSE_NOME[i] + " §7(clique)", 1, d[i]);
            if (desenho) {
                it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "armas/" + armaDaClasse[i]));
                var m = it.getItemMeta();
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                it.setItemMeta(m);
            }
            p.getInventory().setItem(i + 2, it);
        }
        p.showBossBar(barra);
    }

    void tirar(Player p, boolean avisar) {
        noJogo.remove(p.getUniqueId());
        caidos.remove(p.getUniqueId());
        levantar(p, 0);
        p.hideBossBar(barra);
        if (pl.musica != null) pl.musica.tocar(p, null);
        if (aqui(p)) {
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            pl.irLobby(p);
        }
        if (avisar) p.sendMessage(PREFIXO + "Você saiu da Missão.");
    }

    boolean comando(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(java.util.Locale.ROOT) : "";
        if (sub.equals("admin") && s.hasPermission("tiro.admin")) {
            String o = a.length > 1 ? a[1] : "";
            if (o.equals("iniciar") && fase != Fase.JOGO && !mundo.getPlayers().isEmpty()) comecar();
            else if (o.equals("refazer")) {
                construir();
                s.sendMessage(PREFIXO + "base refeita");
            } else if (o.equals("setor") && fase == Fase.JOGO) {
                limparSetor();
                destruirTorreta(null);
            }
            else if (o.equals("ver")) {
                for (Zombie z : inimigos()) for (Player q : vivos())
                    s.sendMessage(pl.zumbis.tipo(z) + " d=" + (int) z.getLocation().distance(q.getLocation()) + " ve=" + z.hasLineOfSight(q) + " z=" + (int) z.getLocation().getZ()
                            + " tiro=" + (proximoTiro.getOrDefault(z.getUniqueId(), 0L) - System.currentTimeMillis()) + " raj=" + rajada.get(z.getUniqueId()) + " tick=" + tick + " ai=" + z.hasAI());
            }
            else s.sendMessage(PREFIXO + "fase=" + fase + " setor=" + setor + " inimigos=" + inimigos().size() + " time=" + noJogo.size() + " | admin <iniciar|refazer|setor>");
            return true;
        }
        if (!(s instanceof Player p)) return true;
        if (sub.equals("sair")) {
            if (aqui(p)) tirar(p, true);
            else p.sendMessage(PREFIXO + "Você não está na Missão.");
        } else entrar(p);
        return true;
    }

    // ================================================================== partida

    private void equipar(Player p) {
        int c = classe.getOrDefault(p.getUniqueId(), 0);
        var inv = p.getInventory();
        inv.clear();
        String[] arma = {"fuzil", "escopeta", "smg", "sniper"};
        inv.setItem(0, pl.armas.arma(arma[c]));
        inv.setItem(1, pl.armas.arma("pistola"));
        municao(p);
        if (c == 1) inv.setItem(2, pl.armas.item("kitmed", Material.RED_DYE, "§dKit médico §7(botão direito)", 1, "§7Cura você e quem estiver a 6 blocos.", "§7Recarrega em 18 s."));
        if (c == 2) inv.setItem(2, pl.armas.item("placas", Material.IRON_INGOT, "§ePlacas de blindagem §7(botão direito)", 1, "§7Escudo extra para você e quem estiver a 6 blocos.", "§7Recarrega em 25 s."));
        pl.armas.vestir(p, c == 3 ? "tatico" : "exercito");   // atirador de elite vai de preto; o resto, farda camuflada
        inv.setHeldItemSlot(0);
    }

    private void municao(Player p) {
        int c = classe.getOrDefault(p.getUniqueId(), 0);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (pl.armas.municaoDe(inv.getItem(i)) != null) inv.setItem(i, null);
        String[] tipo = {"pesada", "cartucho", "leve", "pesada"};
        int[] qtd = {150, 36, 210, 30};
        for (int q = qtd[c]; q > 0; q -= 64) inv.addItem(pl.armas.municao(tipo[c], Math.min(64, q)));
        if (c != 2) inv.addItem(pl.armas.municao("leve", 48));
    }

    private void comecar() {
        noJogo.clear();
        caidos.clear();
        int max = pl.getConfig().getInt("missao.maximo-jogadores", 4);
        for (Player p : mundo.getPlayers()) {
            if (noJogo.size() >= max) {
                p.sendMessage(PREFIXO + "§eO esquadrão fechou com " + max + ". Você entra na próxima.");
                continue;
            }
            noJogo.add(p.getUniqueId());
            equipar(p);
            p.teleport(partida(0));
            p.sendMessage(PREFIXO + "§fClasse: " + CLASSE_NOME[classe.getOrDefault(p.getUniqueId(), 0)] + "§7. Botão direito atira; esquerdo no ar ou Q recarrega.");
        }
        limpar();
        desmontarTorreta();
        for (int s = 0; s < SETORES; s++) portao(s, false);
        fase = Fase.JOGO;
        setor = 0;
        tempo = 0;
        setorAtivo = false;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (fase != Fase.JOGO) return;
            portao(0, true);
            aviso("§c§lSETOR 1", "§f" + NOMES[0] + " §7— limpe a área para o portão abrir", Sound.BLOCK_IRON_DOOR_OPEN);
        }, 60L);
    }

    private void aviso(String a, String b2, Sound s) {
        for (Player p : time()) {
            p.showTitle(Title.title(Tiro.txt(a), Tiro.txt(b2), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2600), Duration.ofMillis(500))));
            if (s != null) p.playSound(p.getLocation(), s, 1f, 1f);
        }
    }

    private List<Zombie> inimigos() {
        List<Zombie> l = new ArrayList<>();
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) if (pl.zumbis.tipo(z) != null && !z.isDead()) l.add(z);
        return l;
    }

    private void limpar() {
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) z.remove();
        for (IronGolem g : mundo.getEntitiesByClass(IronGolem.class)) g.remove();
        mecha = null;
        focoFoguete = null;
        proximoTiro.clear();
        miraAte.clear();
        rajada.clear();
    }

    private void limparSetor() {
        for (Zombie z : inimigos()) z.remove();
        reforcoVeio = true;
    }

    private Zombie soltar(Zumbis.Tipo t, int s, boolean fundo) {
        int L = larg(s) - 2;
        Location l = null;
        for (int i = 0; i < 12 && l == null; i++) {
            int x = rnd.nextInt(2 * L + 1) - L, z = fundo ? z1(s) - 2 - rnd.nextInt(4) : z0(s) + COMP / 2 - 4 + rnd.nextInt(COMP / 2);
            if (t == Zumbis.Tipo.ATIRADOR && s >= 2) {
                l = new Location(mundo, (rnd.nextBoolean() ? 1 : -1) * (larg(s) - 2) + 0.5, Y + 5, z1(s) - 5 + 0.5);
                break;
            }
            if (mundo.getBlockAt(x, Y + 1, z).getType().isAir() && mundo.getBlockAt(x, Y + 2, z).getType().isAir()) l = new Location(mundo, x + 0.5, Y + 1, z + 0.5);
        }
        if (l == null) l = new Location(mundo, 0.5, Y + 1, z1(s) - 3.5);
        l.setYaw(180f);
        int n = Math.max(1, time().size());
        Zombie z = pl.zumbis.nascer(l, t, (t == Zumbis.Tipo.JUGGERNAUT ? 0.6 + 0.4 * n : 0.8 + 0.2 * n));
        z.setSilent(true);
        try {
            Bukkit.getMobGoals().removeAllGoals(z);
        } catch (Throwable ignorado) {
            z.setTarget(null);
        }
        proximoTiro.put(z.getUniqueId(), System.currentTimeMillis() + 1500 + rnd.nextInt(1500));
        return z;
    }

    /** Quem guarda cada setor (o numero cresce com o tamanho do esquadrao). */
    private void povoar(int s, boolean reforco) {
        int n = Math.max(1, time().size());
        int[][] grupos = {       // soldado, escopeta, atirador, escudo, pesado
                {4, 0, 0, 0, 0}, {4, 2, 0, 1, 0}, {3, 1, 2, 2, 0}, {3, 3, 1, 1, 1}, {4, 2, 0, 1, 0}};
        int[][] ref = {{3, 0, 0, 0, 0}, {2, 2, 0, 0, 0}, {2, 1, 0, 1, 0}, {2, 1, 0, 1, 1}, {3, 1, 0, 1, 0}};
        Zumbis.Tipo[] tipos = {Zumbis.Tipo.SOLDADO, Zumbis.Tipo.ESCOPETA, Zumbis.Tipo.ATIRADOR, Zumbis.Tipo.ESCUDO, Zumbis.Tipo.PESADO};
        int[] g = reforco ? ref[s] : grupos[s];
        for (int i = 0; i < g.length; i++) {
            int q = g[i] == 0 ? 0 : (i >= 2 ? g[i] + (n >= 3 ? 1 : 0) : (int) Math.ceil(g[i] * (0.7 + 0.3 * n)));
            for (int k = 0; k < q; k++) soltar(tipos[i], s, reforco);
        }
        if (s == SETORES - 1 && !reforco) soltarMecha(s);
    }

    private void segundo() {
        if (mundo == null) return;
        for (Player p : mundo.getPlayers()) {
            if (p.getY() < Y - 10) p.teleport(noJogo.contains(p.getUniqueId()) && fase == Fase.JOGO ? partida(setor) : sala());
            if (pl.musica != null) pl.musica.tocar(p, fase == Fase.JOGO ? (setorAtivo ? (setor == SETORES - 1 ? "chefe" : "missao_luta") : "missao") : fase == Fase.FIM ? null : "missao");
        }
        int na = mundo.getPlayers().size();
        if (fase == Fase.ESPERA) {
            barra.name(Tiro.txt("§c§lMISSÃO §7— escolha a classe" + (na > 0 ? " §8| §f" + na + " no esquadrão" : "")));
            barra.progress(1f);
            if (na > 0) {
                fase = Fase.CONTAGEM;
                contagem = pl.getConfig().getInt("missao.contagem-segundos", 20);
            }
            return;
        }
        if (fase == Fase.CONTAGEM) {
            if (na == 0) {
                fase = Fase.ESPERA;
                return;
            }
            barra.name(Tiro.txt("§c§lMISSÃO §7começa em §f" + contagem + "s §8| §f" + na + " no esquadrão"));
            barra.progress(Math.max(0f, Math.min(1f, contagem / 20f)));
            if (contagem <= 5 && contagem > 0) for (Player p : mundo.getPlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            if (contagem-- <= 0) comecar();
            return;
        }
        if (fase == Fase.FIM) {
            if (contagem-- <= 0) {
                limpar();
                for (Player p : new ArrayList<>(mundo.getPlayers())) {
                    noJogo.remove(p.getUniqueId());
                    caidos.remove(p.getUniqueId());
                    p.teleport(sala());
                    preparar(p);
                }
                fase = Fase.ESPERA;
            }
            return;
        }
        // ---- em jogo
        tempo++;
        for (Player p : vivos()) {
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            if (p.getHealth() < max && System.currentTimeMillis() - feridoEm.getOrDefault(p.getUniqueId(), 0L) > 5000) p.setHealth(Math.min(max, p.getHealth() + 2));
        }
        sangrar();
        geradorSegundo();
        List<Player> vivos = vivos();
        if (time().isEmpty()) {
            acabar(false, "");
            return;
        }
        if (vivos.isEmpty()) {
            for (Player p : time()) if (derrubados.containsKey(p.getUniqueId())) {
                levantar(p, 0);
                p.setHealth(0);
            }
            acabar(false, "§7O esquadrão inteiro caiu no setor " + (setor + 1) + ".");
            return;
        }
        List<Zombie> ini = inimigos();
        if (!setorAtivo) {
            boolean entrou = false;
            for (Player p : vivos) if (p.getLocation().getZ() > z0(setor) + 2) entrou = true;
            if (entrou || tempo % 30 == 29) {
                setorAtivo = true;
                reforcoVeio = false;
                povoar(setor, false);
                if (setor == SETOR_TORRETA) {
                    montarTorreta();
                    aviso("§c§lTORRETA!", "§7Bala não derruba. §eSabote o gerador §7(agachado do lado, beco da esquerda)", Sound.BLOCK_BEACON_ACTIVATE);
                    for (Player p : time()) p.sendMessage(PREFIXO + "§cTorreta no fundo dos Becos! §fAche o §egerador§f (faíscas, beco da esquerda) e fique §eagachado§f do lado 3 s. §7Engenheiro sabota em 1 s.");
                }
            }
        } else if (ini.size() <= 2 && !reforcoVeio) {
            reforcoVeio = true;
            povoar(setor, true);
            aviso("§e§lREFORÇOS!", "§7Mais soldados chegando pelo fundo", Sound.BLOCK_BELL_USE);
        } else if (ini.isEmpty() && mechaVivo()) {
            if (tempo % 8 == 0) for (Player p : time()) p.sendActionBar(Tiro.txt("§4Derrube o §lMECHA§4 para tomar o Heliporto!"));
        } else if (ini.isEmpty() && torretaViva) {
            if (tempo % 6 == 0) for (Player p : time()) p.sendActionBar(Tiro.txt("§eO portão só abre sem a torreta: §fsabote o gerador §7(parede esquerda)"));
        } else if (ini.isEmpty()) {
            setorAtivo = false;
            if (setor == SETORES - 1) {
                acabar(true, "");
                return;
            }
            setor++;
            portao(setor, true);
            // ponto de controle: cura, municao e quem caiu volta
            for (Player p : time()) {
                levantar(p, 0);
                if (caidos.remove(p.getUniqueId())) {
                    p.setGameMode(GameMode.ADVENTURE);
                    p.teleport(partida(setor));
                    equipar(p);
                }
                p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                municao(p);
                pl.coronas(p, 10, "pelo setor " + setor + " da Missão");
            }
            aviso("§a§lPONTO DE CONTROLE " + setor + "/" + (SETORES - 1), "§fPróximo: §e" + NOMES[setor] + " §7— vida e munição repostas", Sound.ENTITY_PLAYER_LEVELUP);
        }
        if (mechaVivo()) {
            double max = mecha.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            barra.name(Tiro.txt("§4§lMECHA W-2300 §8| " + (superaquecido() ? "§c§lSUPERAQUECIDO" : "§7blindado") + " §8| §7soldados §f" + ini.size()));
            barra.progress((float) Math.max(0.01, Math.min(1, mecha.getHealth() / max)));
            return;
        }
        barra.name(Tiro.txt("§c§lSETOR " + (setor + 1) + "/" + SETORES + " §f" + NOMES[setor] + " §8| §7inimigos §f" + ini.size() + " §8| §7" + String.format("%d:%02d", tempo / 60, tempo % 60)));
        barra.progress(setorAtivo ? Math.max(0.05f, Math.min(1f, ini.size() / 10f)) : 1f);
    }

    private void acabar(boolean vitoria, String sub) {
        fase = Fase.FIM;
        contagem = 12;
        for (Player p : time()) {
            if (vitoria) {
                int premio = 120 + Math.max(0, 600 - tempo) / 10;
                p.showTitle(Title.title(Tiro.txt("§a§lMISSÃO CUMPRIDA"), Tiro.txt("§7Tempo §f" + String.format("%d:%02d", tempo / 60, tempo % 60))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                pl.coronas(p, premio, "por completar a Missão");
                int n = pl.dados.getInt("missao.vitorias." + p.getUniqueId(), 0) + 1;
                pl.dados.set("missao.vitorias." + p.getUniqueId(), n);
                int melhor = pl.dados.getInt("missao.tempo." + p.getUniqueId(), 99999);
                if (tempo < melhor) pl.dados.set("missao.tempo." + p.getUniqueId(), tempo);
            } else {
                p.showTitle(Title.title(Tiro.txt("§c§lMISSÃO FRACASSADA"), Tiro.txt(sub)));
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f);
            }
            if (caidos.remove(p.getUniqueId())) p.setGameMode(GameMode.ADVENTURE);
            levantar(p, 0);
        }
        derrubados.clear();
        socorro.clear();
        desmontarTorreta();
        limpar();
    }

    // ================================================================== inimigos: andam, miram e atiram

    private void ia() {
        if (mundo == null || fase != Fase.JOGO) return;
        tick++;
        List<Player> alvos = vivos();
        if (alvos.isEmpty()) return;
        long agora = System.currentTimeMillis();
        torretaIa(alvos, agora);
        mechaIa(alvos, agora);
        for (Zombie z : inimigos()) {
            Zumbis.Tipo t = pl.zumbis.tipo(z);
            Perfil pf = PERFIS.get(t);
            if (pf == null) continue;
            Player alvo = null;
            double dm = 1e9;
            for (Player p : alvos) {
                double d = p.getLocation().distanceSquared(z.getLocation());
                if (d < dm) {
                    dm = d;
                    alvo = p;
                }
            }
            if (alvo == null) continue;
            double d = Math.sqrt(dm);
            boolean ve = z.hasLineOfSight(alvo);
            UUID u = z.getUniqueId();
            // olha para o alvo
            Vector dir = alvo.getEyeLocation().toVector().subtract(z.getEyeLocation().toVector());
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            z.setRotation(yaw, 0f);
            try {
                z.setBodyYaw(yaw);
            } catch (Throwable ignorado) {
                // versao sem setBodyYaw
            }
            // anda: chega ate a distancia que gosta; sem visao, procura; perto demais (menos o escudo e a escopeta), recua
            if (t != Zumbis.Tipo.ATIRADOR && rajada.getOrDefault(u, 0) <= 0) {
                if (!ve || d > pf.longe()) z.getPathfinder().moveTo(alvo.getLocation(), t == Zumbis.Tipo.ESCOPETA ? 1.25 : 1.0);
                else if (d < pf.perto() && t != Zumbis.Tipo.ESCUDO && t != Zumbis.Tipo.ESCOPETA) {
                    Location tras = z.getLocation().add(dir.clone().setY(0).normalize().multiply(-4));
                    z.getPathfinder().moveTo(tras, 1.0);
                } else if (tick % 12 == (u.getLeastSignificantBits() & 7)) {
                    // troca de posicao de lado, para nao ficar parado feito poste
                    Vector lado = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(rnd.nextBoolean() ? 3 : -3);
                    z.getPathfinder().moveTo(z.getLocation().add(lado), 1.0);
                } else if (tick % 12 == ((u.getLeastSignificantBits() & 7) + 5) % 12) z.getPathfinder().stopPathfinding();
            }
            // tiro
            int rj = rajada.getOrDefault(u, 0);
            if (rj > 0) {
                rajada.put(u, rj - 1);
                if (ve) disparo(z, t, pf, alvo, d);
                continue;
            }
            Long mira = miraAte.get(u);
            if (mira != null) {                                                  // atirador de elite e pesado: avisam antes
                if (!ve) {
                    miraAte.remove(u);
                    continue;
                }
                if (t == Zumbis.Tipo.ATIRADOR) laser(z, alvo);
                if (agora >= mira) {
                    miraAte.remove(u);
                    proximoTiro.put(u, agora + pf.intervalo() * 50L + rnd.nextInt(600));
                    if (pf.rajada() > 1) rajada.put(u, pf.rajada());
                    else disparo(z, t, pf, alvo, d);
                }
                continue;
            }
            if (!ve || d > pf.longe() + 6 || agora < proximoTiro.getOrDefault(u, 0L)) continue;
            if (agora - fogoEm.getOrDefault(alvo.getUniqueId(), 0L) < 650) continue;
            fogoEm.put(alvo.getUniqueId(), agora);
            if (t == Zumbis.Tipo.ATIRADOR || t == Zumbis.Tipo.PESADO || t == Zumbis.Tipo.JUGGERNAUT) {
                miraAte.put(u, agora + (t == Zumbis.Tipo.ATIRADOR ? 1500 : 900));
                mundo.playSound(z.getLocation(), t == Zumbis.Tipo.ATIRADOR ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_PISTON_EXTEND, 1f, t == Zumbis.Tipo.ATIRADOR ? 2f : 0.6f);
                z.getPathfinder().stopPathfinding();
                continue;
            }
            proximoTiro.put(u, agora + pf.intervalo() * 50L + rnd.nextInt(500));
            if (pf.rajada() > 1) rajada.put(u, pf.rajada());
            else disparo(z, t, pf, alvo, d);
        }
    }

    private void laser(Zombie z, Player alvo) {
        Location a = z.getEyeLocation(), b2 = alvo.getEyeLocation().subtract(0, 0.3, 0);
        Vector v = b2.toVector().subtract(a.toVector());
        double len = v.length();
        v.normalize();
        Particle.DustOptions po = new Particle.DustOptions(Color.RED, 0.5f);
        for (double k = 1; k < len; k += 0.8) mundo.spawnParticle(Particle.DUST, a.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0, po);
    }

    private void disparo(Zombie z, Zumbis.Tipo t, Perfil pf, Player alvo, double d) {
        if (pl.visual != null) pl.visual.atirando(z);
        Location boca = z.getEyeLocation().add(z.getLocation().getDirection().multiply(0.8)).subtract(0, 0.3, 0);
        boolean grosso = t == Zumbis.Tipo.ESCOPETA || t == Zumbis.Tipo.ATIRADOR;
        mundo.playSound(boca, grosso ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_FIREWORK_ROCKET_BLAST, grosso ? 0.7f : 0.45f, t == Zumbis.Tipo.ATIRADOR ? 2f : t == Zumbis.Tipo.ESCOPETA ? 1.7f : 1.5f + rnd.nextFloat() * 0.3f);
        // pontaria: cai com a distancia; agachado ou correndo voce e mais dificil de acertar
        double chance = pf.pontaria() * Math.max(0.25, 1 - d / (pf.longe() * 2.2));
        if (alvo.isSneaking()) chance *= 0.6;
        if (alvo.isSprinting()) chance *= 0.75;
        boolean acertou = rnd.nextDouble() < chance;
        Location fim = alvo.getEyeLocation().subtract(0, 0.4, 0);
        if (!acertou) fim.add(rnd.nextGaussian() * 0.9, rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.9);
        Vector v = fim.toVector().subtract(boca.toVector());
        double len = v.length();
        v.normalize();
        // rastro fino (no Bedrock a poeira colorida sai enorme e tapa a visao)
        for (double k = 0.8; k < len; k += 1.6) mundo.spawnParticle(Particle.ELECTRIC_SPARK, boca.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0);
        if (!acertou) {
            alvo.playSound(fim, Sound.ENTITY_ARROW_SHOOT, 0.35f, 1.9f);       // zunido da bala passando
            return;
        }
        double dano = pf.dano() * (t == Zumbis.Tipo.ESCOPETA ? Math.max(0.3, 1 - d / 9) : 1) * (1 + 0.12 * setor);
        alvo.setNoDamageTicks(0);
        feridoEm.put(alvo.getUniqueId(), System.currentTimeMillis());
        alvo.damage(dano);   // sem "quem bateu": dano de mob muda com a dificuldade do mundo (zero no pacifico)
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void semAlvoProprio(EntityTargetLivingEntityEvent e) {
        if (mundo == null || e.getEntity().getWorld() != mundo) return;
        Zumbis.Tipo t = pl.zumbis.tipo(e.getEntity());
        if (t != null && PERFIS.containsKey(t)) e.setCancelled(true);
    }

    /** Soldado nao da soco. Escudo e blindados so tomam dano de verdade de lado ou pelas costas. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void golpes(EntityDamageByEntityEvent e) {
        if (mundo == null || e.getEntity().getWorld() != mundo) return;
        Zumbis.Tipo tb = pl.zumbis.tipo(e.getDamager());
        if (tb != null && PERFIS.containsKey(tb) && e.getEntity() instanceof Player) {
            if (!bala) e.setCancelled(true);
            return;
        }
        Zumbis.Tipo t = pl.zumbis.tipo(e.getEntity());
        if (t == null || !(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity alvo)) return;
        if (t != Zumbis.Tipo.ESCUDO && t != Zumbis.Tipo.PESADO && t != Zumbis.Tipo.JUGGERNAUT) return;
        Vector olha = alvo.getLocation().getDirection().setY(0).normalize();
        Vector para = p.getLocation().toVector().subtract(alvo.getLocation().toVector()).setY(0).normalize();
        double frente = olha.dot(para);                                          // 1 = de frente, -1 = pelas costas
        if (t == Zumbis.Tipo.ESCUDO && frente > 0.35) {
            e.setDamage(e.getDamage() * 0.08);
            mundo.playSound(alvo.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.8f);
            p.sendActionBar(Tiro.txt("§7O escudo segurou! §eDê a volta§7 ou espere ele virar."));
        } else if (t != Zumbis.Tipo.ESCUDO) {
            if (frente < -0.3) {
                e.setDamage(e.getDamage() * 2.2);
                p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 0.8f);
            } else if (t == Zumbis.Tipo.JUGGERNAUT) e.setDamage(e.getDamage() * 0.45);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void golpeMecha(EntityDamageEvent e) {
        if (!mechaVivo() || e.getEntity() != mecha) return;
        if (!(e instanceof EntityDamageByEntityEvent ev) || !(ev.getDamager() instanceof Player p)) {
            if (e.getCause() != EntityDamageEvent.DamageCause.CUSTOM) e.setCancelled(true);   // queda, sufocar etc.
            return;
        }
        if (superaquecido()) {
            e.setDamage(e.getDamage() * 3);
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.5f, 0.6f);
            return;
        }
        Vector olha = mecha.getLocation().getDirection().setY(0).normalize();
        Vector para = p.getLocation().toVector().subtract(mecha.getLocation().toVector()).setY(0).normalize();
        double frente = olha.dot(para);
        if (frente < -0.3) {
            e.setDamage(e.getDamage() * 2);
            p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.5f, 0.8f);
            mundo.spawnParticle(Particle.ELECTRIC_SPARK, mecha.getLocation().add(0, 2.4, 0), 8, 0.3, 0.3, 0.3, 0.1);
        } else if (frente > 0.35) {
            e.setDamage(e.getDamage() * 0.2);
            if (rnd.nextInt(6) == 0) p.sendActionBar(Tiro.txt("§7Blindagem frontal! §eAtaque pelas costas§7 ou espere ele superaquecer."));
            mundo.playSound(mecha.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.25f, 2f);
        } else e.setDamage(e.getDamage() * 0.6);
    }

    @EventHandler
    public void mechaCaiu(EntityDeathEvent e) {
        if (mecha == null || e.getEntity() != mecha) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Location l = mecha.getLocation();
        mundo.spawnParticle(Particle.EXPLOSION_EMITTER, l.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
        mundo.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1.5, 0), 40, 1, 1.5, 1, 0.05);
        mundo.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
        aviso("§a§lMECHA DESTRUÍDO", "§7Limpem o resto do Heliporto!", null);
        focoFoguete = null;
    }

    void morreu(Zombie z, Zumbis.Tipo t, Player matador) {
        proximoTiro.remove(z.getUniqueId());
        miraAte.remove(z.getUniqueId());
        rajada.remove(z.getUniqueId());
        if (matador != null) matador.sendActionBar(Tiro.txt("§a+ " + t.nome + " §7abatido"));
    }

    // ================================================================== jogador: classe, habilidade, queda

    @EventHandler(priority = EventPriority.HIGH)
    public void usar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_AIR) return;
        String id = pl.armas.idItem(p.getInventory().getItemInMainHand());
        if (id == null) return;
        if (id.startsWith("classe") && fase != Fase.JOGO) {
            int c = id.charAt(6) - '0';
            classe.put(p.getUniqueId(), c);
            p.sendMessage(PREFIXO + "Classe: " + CLASSE_NOME[c] + "§7.");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            e.setCancelled(true);
            return;
        }
        if (!id.equals("kitmed") && !id.equals("placas")) return;
        e.setCancelled(true);
        if (derrubado(p)) return;
        long agora = System.currentTimeMillis(), espera = id.equals("kitmed") ? 18000 : 25000;
        long falta = espera - (agora - ultimoKit.getOrDefault(p.getUniqueId(), 0L));
        if (falta > 0) {
            p.sendActionBar(Tiro.txt("§7Recarregando: §f" + (falta / 1000 + 1) + "s"));
            return;
        }
        ultimoKit.put(p.getUniqueId(), agora);
        if (id.equals("kitmed")) for (Player o : time()) {
            if (derrubados.containsKey(o.getUniqueId()) && o.getLocation().distanceSquared(p.getLocation()) <= 9) {
                levantar(o, 14);
                reanimou(p, o, true);
            }
        }
        for (Player o : vivos()) {
            if (o.getLocation().distanceSquared(p.getLocation()) > 36) continue;
            if (id.equals("kitmed")) {
                o.setHealth(Math.min(o.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), o.getHealth() + 10));
                o.getWorld().spawnParticle(Particle.HEART, o.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3, 0);
            } else {
                o.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 40, 2, false, false, true));
                o.getWorld().spawnParticle(Particle.CRIT, o.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.1);
            }
            o.playSound(o.getLocation(), id.equals("kitmed") ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.4f);
        }
    }

    /** Tiro que mataria: cai derrubado (se ainda tem companheiro de pe para socorrer). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void quaseMorte(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !aqui(p) || fase != Fase.JOGO || !noJogo.contains(p.getUniqueId())) return;
        if (derrubados.containsKey(p.getUniqueId())) {
            e.setCancelled(true);   // derrubado nao toma mais tiro (os soldados procuram quem esta de pe)
            return;
        }
        if (e.getFinalDamage() < p.getHealth()) return;
        boolean temQuemSocorra = false;
        for (Player o : vivos()) if (o != p) temQuemSocorra = true;
        if (!temQuemSocorra) return;   // sozinho: cai de vez, como antes
        e.setCancelled(true);
        derrubar(p);
    }

    private void derrubar(Player p) {
        derrubados.put(p.getUniqueId(), System.currentTimeMillis() + SANGRA_S * 1000L);
        socorro.remove(p.getUniqueId());
        p.setHealth(Math.min(p.getHealth(), 4));
        p.setPose(org.bukkit.entity.Pose.SWIMMING, true);
        p.setGlowing(true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * (SANGRA_S + 2), 3, false, false, false));
        p.showTitle(Title.title(Tiro.txt("§c§lDERRUBADO"), Tiro.txt("§7Um companheiro precisa vir te levantar"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        for (Player o : time()) if (o != p) {
            o.sendMessage(PREFIXO + "§c" + p.getName() + " §7foi §cderrubado§7! §fAgache do lado dele§7 para levantar"
                    + (classe.getOrDefault(o.getUniqueId(), 0) == 1 ? " §d(ou use o kit médico)" : "") + "§7.");
            o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.7f);
        }
    }

    /** Tira o estado de derrubado. vida > 0: levantou com essa vida. */
    private void levantar(Player p, double vida) {
        if (derrubados.remove(p.getUniqueId()) == null) return;
        socorro.remove(p.getUniqueId());
        if (!p.isOnline()) return;
        p.setPose(org.bukkit.entity.Pose.STANDING, false);
        p.setGlowing(false);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        if (vida > 0 && !p.isDead()) {
            p.setHealth(Math.min(vida, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            feridoEm.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    private void reanimou(Player quem, Player caido, boolean kit) {
        caido.showTitle(Title.title(Tiro.txt("§a§lDE PÉ!"), Tiro.txt("§f" + quem.getName() + " §7te levantou")));
        caido.playSound(caido.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        mundo.spawnParticle(Particle.HEART, caido.getLocation().add(0, 1.2, 0), 6, 0.4, 0.3, 0.4, 0);
        if (kit) mundo.playSound(caido.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
        quem.sendActionBar(Tiro.txt("§aVocê levantou §f" + caido.getName() + "§a! §6+5 coronas"));
        pl.coronas(quem, 5, "por levantar um companheiro na Missão");
    }

    /** A cada segundo: quem esta sendo socorrido avanca; quem sangrou demais cai de vez. */
    private void sangrar() {
        if (derrubados.isEmpty()) return;
        long agora = System.currentTimeMillis();
        for (UUID u : new ArrayList<>(derrubados.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !aqui(p) || p.isDead()) {
                derrubados.remove(u);
                socorro.remove(u);
                continue;
            }
            Player ajudante = null;
            for (Player o : vivos())
                if (o.isSneaking() && o.getLocation().distanceSquared(p.getLocation()) <= 4) {
                    ajudante = o;
                    if (classe.getOrDefault(o.getUniqueId(), 0) == 1) break;   // medico tem preferencia
                }
            if (ajudante != null) {
                int precisa = classe.getOrDefault(ajudante.getUniqueId(), 0) == 1 ? 1 : 3;
                int feito = socorro.merge(u, 1, Integer::sum);
                ajudante.sendActionBar(Tiro.txt("§eLevantando §f" + p.getName() + " §e" + "█".repeat(feito) + "§8" + "█".repeat(Math.max(0, precisa - feito))));
                p.sendActionBar(Tiro.txt("§a" + ajudante.getName() + " está te levantando..."));
                mundo.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
                if (feito >= precisa) {
                    levantar(p, 8);
                    reanimou(ajudante, p, false);
                }
                continue;
            }
            socorro.remove(u);
            long falta = (derrubados.get(u) - agora) / 1000;
            if (falta <= 0) {
                levantar(p, 0);
                p.setHealth(0);   // sangrou: cai de vez e volta no proximo ponto de controle
                continue;
            }
            p.sendActionBar(Tiro.txt("§c§lDERRUBADO §7— sangrando: §f" + falta + "s §8| §7chame o time!"));
            mundo.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.3, 0), 4, 0.3, 0.1, 0.3, 0, new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1f));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!aqui(p)) return;
        e.getDrops().clear();
        e.setKeepInventory(true);
        e.setDroppedExp(0);
        e.deathMessage(null);
        if (noJogo.contains(p.getUniqueId()) && fase == Fase.JOGO) {
            caidos.add(p.getUniqueId());
            for (Player o : time()) o.sendMessage(PREFIXO + "§c" + p.getName() + " §7caiu. Volta no próximo ponto de controle.");
        }
    }

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!noJogo.contains(p.getUniqueId()) || mundo == null) return;
        boolean caido = caidos.contains(p.getUniqueId()) && fase == Fase.JOGO;
        e.setRespawnLocation(caido ? partida(setor) : sala());
        if (caido) Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && aqui(p) && caidos.contains(p.getUniqueId())) {
                p.setGameMode(GameMode.SPECTATOR);
                p.showTitle(Title.title(Tiro.txt("§c§lVOCÊ CAIU"), Tiro.txt("§7Você volta quando o time limpar o setor")));
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void dano(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !aqui(p)) return;
        if (fase != Fase.JOGO || !noJogo.contains(p.getUniqueId()) || e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && aqui(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quebrar(BlockBreakEvent e) {
        if (aqui(e.getPlayer()) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void colocar(BlockPlaceEvent e) {
        if (aqui(e.getPlayer()) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void largar(PlayerDropItemEvent e) {
        if (aqui(e.getPlayer()) && pl.armas.armaDe(e.getItemDrop().getItemStack()) == null) e.setCancelled(true);
    }

    @EventHandler
    public void nascer(CreatureSpawnEvent e) {
        if (mundo != null && e.getLocation().getWorld() == mundo && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    /** 15/11 (Paulo: "os itens sairam do modo de tiro e foram para o spawn"): nada da Missao sai da Missao. */
    private void varrer(Player p) {
        if (pl.mundoDeTiro(p.getWorld())) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            String id = pl.armas.idItem(it);
            boolean daMissao = id != null && (id.startsWith("classe") || id.startsWith("uniforme_") || id.equals("kitmed") || id.equals("placas"));
            if (daMissao || pl.armas.armaDe(it) != null || pl.armas.municaoDe(it) != null) inv.setItem(i, null);
        }
    }

    @EventHandler
    public void entrouNoServidor(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) varrer(p); }, 50L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void mudou(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        // depois das mochilas do Extras trocarem (elas guardam a do Apocalipse com as armas dentro)
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) varrer(p); }, 8L);
        if (e.getFrom() == mundo) {
            noJogo.remove(p.getUniqueId());
            caidos.remove(p.getUniqueId());
            p.hideBossBar(barra);
            p.getInventory().clear();
            for (PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p)) return;
        noJogo.remove(p.getUniqueId());
        caidos.remove(p.getUniqueId());
        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(pl.spawnLobby());
    }
}
