package br.servidor.masmorras;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Segunda fase do Rei do Gelo (16/11, ideia do Paulo): com 60% da vida o chefe foge pela encosta. Todo mundo desce
 * de barco uma pista de gelo azul no ceu (degraus, pilares de gelo para desviar e bonecos de neve atirando bolas de
 * neve dos lados) e chega numa arena de gelo escorregadio, onde comeca a fase 2: o chefe recupera vida, caem
 * estalactites (circulo azul no chao avisa antes) e ele solta leques de bolas de neve. Tudo o que e construido fica
 * guardado e some no fim (morte do chefe, limpeza, desligar). Os chunks da pista e da arena ficam presos carregados
 * durante a fase (o Herobrine sumia com o chunk). Quem morreu e voltou acha placas azuis na arena original.
 */
final class DescidaGelo implements Listener {

    static final int L = 72, SEG = 6, R2 = 11;
    static final int DEGRAUS = L / SEG;   // 12

    enum Etapa { CENA, DESCIDA, FASE2 }

    final Masmorras pl;
    final NamespacedKey kBola, kBarco, kBoneco;
    final Map<Chefe, Fase> fases = new HashMap<>();
    final Random rnd = new Random();

    static final class Fase {
        Chefe c;
        World w;
        Caixa arena, area, arena2;
        int x0, xFim, cz, ys, yFim, xa;
        Location centro2;
        Etapa etapa = Etapa.CENA;
        boolean acabou;
        BukkitTask cena, tarefa;
        long fimDescida, proximaEstalactite, proximaNevasca, comecouDescida;
        int passo;
        final List<Block> obra = new ArrayList<>();
        final List<Block> chao = new ArrayList<>();
        final List<BlockData> chaoDados = new ArrayList<>();
        final Set<Block> placas = new HashSet<>();
        final List<UUID> bonecos = new ArrayList<>();
        final Map<UUID, Long> bonecoPronto = new HashMap<>();
        final Map<UUID, UUID> barcos = new HashMap<>();   // jogador -> barco
        final Set<UUID> chegaram = new HashSet<>();
        final Map<UUID, double[]> parado = new HashMap<>();   // jogador -> {x, desde}
        final Set<UUID> soltando = new HashSet<>();
        final Map<UUID, Long> ultimoBarco = new HashMap<>();
        final List<int[]> tickets = new ArrayList<>();
    }

    DescidaGelo(Masmorras pl) {
        this.pl = pl;
        this.kBola = new NamespacedKey(pl, "bola_descida");
        this.kBarco = new NamespacedKey(pl, "barco_descida");
        this.kBoneco = new NamespacedKey(pl, "boneco_descida");
    }

    boolean ativa() {
        return pl.getConfig().getBoolean("descida.ativa", true);
    }

    boolean ocupado(Chefe c) {
        Fase f = fases.get(c);
        return f != null && f.etapa != Etapa.FASE2;
    }

    /** O golpe que passaria dos 60% nao pula a fase (mesma ideia da tempestade). */
    boolean seguraVida(Chefe c, double danoFinal) {
        if (c.descidaFeita || !ativa() || !("GELO".equals(c.t.id) || c.forcarDescida)) return false;
        return c.e.getHealth() - danoFinal <= c.vidaMax * pl.getConfig().getDouble("descida.vida", 0.6);
    }

    /** Chamado no tique do chefe. Devolve true quando a fase comecou agora. */
    boolean conferir(Chefe c, List<Player> ps) {
        if (c.descidaFeita || fases.containsKey(c) || !ativa()) return false;
        if (!"GELO".equals(c.t.id) && !c.forcarDescida) return false;
        if (c.e.getHealth() > c.vidaMax * pl.getConfig().getDouble("descida.vida", 0.6)) return false;
        c.descidaFeita = true;
        Fase f = new Fase();
        try {
            if (!planejar(f, c)) return false;
            fases.put(c, f);
            comecar(f);
        } catch (Throwable ex) {
            pl.getLogger().warning("Descida do Gelo: " + ex);
            ex.printStackTrace();
            fim(f);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ onde fica (no ceu, em cima da masmorra)

    private boolean planejar(Fase f, Chefe c) {
        f.c = c;
        f.w = c.e.getWorld();
        f.arena = c.m.arena();
        int cx = f.arena.cx();
        f.cz = f.arena.cz();
        f.x0 = cx - 40;
        f.xFim = f.x0 + L;
        f.xa = f.xFim + R2 + 1;
        int alto = f.arena.y2();
        for (int x = f.x0 - 9; x <= f.xa + R2 + 1; x += 3)
            for (int z = f.cz - R2 - 1; z <= f.cz + R2 + 1; z += 3) alto = Math.max(alto, f.w.getHighestBlockYAt(x, z));
        f.ys = Math.max(f.arena.y2() + 30, alto + 10 + DEGRAUS);
        if (f.ys > f.w.getMaxHeight() - 14) f.ys = f.w.getMaxHeight() - 14;
        f.yFim = f.ys - DEGRAUS;
        if (f.yFim < alto + 5) {
            pl.getLogger().warning("Descida do Gelo: sem espaco no ceu da masmorra #" + c.m.id + " (terreno em y=" + alto + "). A luta segue normal.");
            return false;
        }
        f.area = new Caixa(f.x0 - 9, f.yFim - 5, f.cz - R2 - 2, f.xa + R2 + 2, f.ys + 10, f.cz + R2 + 2);
        f.arena2 = new Caixa(f.xa - R2 - 1, f.yFim + 1, f.cz - R2 - 1, f.xa + R2 + 1, f.yFim + 12, f.cz + R2 + 1);
        f.centro2 = new Location(f.w, f.xa + 0.5, f.yFim + 1, f.cz + 0.5);
        return true;
    }

    // ------------------------------------------------------------------ comeco (cena de 3 s)

    private void comecar(Fase f) {
        Chefe c = f.c;
        // chunks presos carregados durante a fase
        for (int x = f.area.x1() >> 4; x <= f.area.x2() >> 4; x++)
            for (int z = f.area.z1() >> 4; z <= f.area.z2() >> 4; z++) {
                f.w.addPluginChunkTicket(x, z, pl);
                f.tickets.add(new int[] {x, z});
            }
        construir(f);
        c.e.setInvulnerable(true);
        if (c.e instanceof Mob mob) mob.setAI(false);
        pl.getLogger().info("Descida do Gelo: " + c.t.chefeNome + " (masmorra #" + c.m.id + ") fugiu pela encosta - pista em y=" + f.ys + ".." + f.yFim
                + ", " + f.obra.size() + " blocos");
        int[] k = {0};
        f.cena = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            int i = k[0]++;
            List<Player> naArena = jogadoresEm(f.w, f.arena.crescer(2));
            if (i == 0) for (Player p : naArena) {
                p.showTitle(Title.title(Masmorras.txt("§b§l❄ " + c.t.chefeNome.toUpperCase() + " FUGIU! ❄"), Masmorras.txt("§fDesça de barco e desvie das bolas de neve!"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(300))));
                p.playSound(p.getLocation(), "minecraft:entity.player.hurt_freeze", 1f, 0.6f);
            }
            if (i < 14) {
                if (c.vivo()) f.w.spawnParticle(Particle.SNOWFLAKE, c.e.getLocation().add(0, 1, 0), 30, 0.8, 1, 0.8, 0.05);
                for (Player p : naArena) p.playSound(p.getLocation(), "minecraft:block.glass.break", 0.6f, 1.6f);
            }
            if (i >= 15) {
                f.cena.cancel();
                f.cena = null;
                comecarDescida(f);
            }
        }, 0L, 4L);
    }

    private void comecarDescida(Fase f) {
        Chefe c = f.c;
        if (!c.vivo()) {
            fim(f);
            return;
        }
        // o chefe espera la embaixo, na arena de gelo (ainda sem apanhar)
        c.e.teleport(f.centro2.clone().add(4, 0.2, 0).setDirection(new Vector(-1, 0, 0)));
        c.e.setGlowing(true);
        c.m.extra = f.area;
        int n = 0;
        for (Player p : jogadoresEm(f.w, f.arena.crescer(2))) entrarNaPista(f, p, n++);
        placas(f);
        f.etapa = Etapa.DESCIDA;
        f.comecouDescida = System.currentTimeMillis();
        f.fimDescida = f.comecouDescida + pl.getConfig().getLong("descida.tempo-max-ms", 70_000L);
        f.tarefa = Bukkit.getScheduler().runTaskTimer(pl, () -> tick(f), 5L, 5L);
    }

    /** Poe o jogador no comeco da pista, dentro de um barco. */
    private void entrarNaPista(Fase f, Player p, int vaga) {
        double z = f.cz + 0.5 + (vaga % 3 - 1) * 1.6;
        double x = f.x0 - 3.5 - (vaga / 3) * 2.2;
        Location l = new Location(f.w, x, f.ys + 1, z, -90f, 10f);   // virado para +x (descida)
        colocarNoBarco(f, p, l);
        f.chegaram.remove(p.getUniqueId());
        f.parado.put(p.getUniqueId(), new double[] {p.getX(), System.currentTimeMillis()});
        p.sendMessage(Masmorras.txt("§b§l❄ Descida do Gelo: §7aperte §fW §7para ir, §fA/D §7para virar. Desvie dos §fpilares §7e das §fbolas de neve§7!"));
    }

    private void colocarNoBarco(Fase f, Player p, Location l) {
        tirarDoBarco(f, p);
        p.setFallDistance(0);
        p.teleport(l);
        f.ultimoBarco.put(p.getUniqueId(), System.currentTimeMillis());
        try {
            Boat b = f.w.spawn(l, org.bukkit.entity.boat.SpruceBoat.class, x -> {
                x.setInvulnerable(true);
                x.setPersistent(false);
                x.getPersistentDataContainer().set(kBarco, PersistentDataType.BYTE, (byte) 1);
                x.addScoreboardTag("clearlag_exclude");
            });
            b.addPassenger(p);
            f.barcos.put(p.getUniqueId(), b.getUniqueId());
        } catch (Throwable ex) {
            pl.getLogger().warning("Descida do Gelo: barco nao nasceu para " + p.getName() + ": " + ex);
        }
    }

    private void tirarDoBarco(Fase f, Player p) {
        UUID b = f.barcos.remove(p.getUniqueId());
        f.soltando.add(p.getUniqueId());
        try {
            if (p.isInsideVehicle()) p.leaveVehicle();
        } finally {
            f.soltando.remove(p.getUniqueId());
        }
        if (b != null) {
            Entity e = Bukkit.getEntity(b);
            if (e != null) e.remove();
        }
    }

    // ------------------------------------------------------------------ a fase (tique de 1/4 s)

    private void tick(Fase f) {
        if (f.acabou) return;
        Chefe c = f.c;
        if (!c.vivo()) return;   // a morte chama terminar()
        long agora = System.currentTimeMillis();
        f.passo++;
        // placas azuis na arena original: quem chega depois volta para a luta
        for (Player p : jogadoresEm(f.w, f.arena.crescer(2))) {
            Block pe = p.getLocation().subtract(0, 0.2, 0).getBlock();
            if (f.placas.contains(pe)) {
                if (f.etapa == Etapa.DESCIDA) entrarNaPista(f, p, rnd.nextInt(3));
                else mandarParaArena(f, p);
                continue;
            }
            if (f.passo % 8 == 0) p.sendActionBar(Masmorras.txt("§b§lPise numa placa azul §7(nas paredes) para voltar à luta!"));
        }
        if (f.passo % 4 == 0) for (Block b : f.placas) f.w.spawnParticle(Particle.SNOWFLAKE, b.getLocation().add(0.5, 1.2, 0.5), 3, 0.2, 0.5, 0.2, 0.01);
        if (f.etapa == Etapa.DESCIDA) tickDescida(f, agora);
        else if (f.etapa == Etapa.FASE2) tickFase2(f, agora);
    }

    private void tickDescida(Fase f, long agora) {
        List<Player> naPista = new ArrayList<>();
        for (Player p : jogadoresEm(f.w, f.area)) {
            if (f.chegaram.contains(p.getUniqueId())) continue;
            // chegou la embaixo
            if (p.getX() >= f.xFim + 0.5 && p.getY() < f.yFim + 4) {
                chegou(f, p);
                continue;
            }
            naPista.add(p);
            // caiu da pista (nao devia, mas): volta para o degrau
            if (p.getY() < f.yFim - 2) {
                colocarNoBarco(f, p, comecoDoDegrau(f, p.getX()));
                continue;
            }
            // sem barco (renasceu, relogou...): volta para o barco no comeco do degrau em que esta
            if (!(p.getVehicle() instanceof Boat)) {
                Long ub = f.ultimoBarco.get(p.getUniqueId());
                if (ub == null || agora - ub > 2500) colocarNoBarco(f, p, comecoDoDegrau(f, p.getX()));
                continue;
            }
            // parado ha muito tempo (preso num pilar): empurra para o proximo degrau
            double[] pa = f.parado.computeIfAbsent(p.getUniqueId(), k -> new double[] {p.getX(), agora});
            if (p.getX() > pa[0] + 2) {
                pa[0] = p.getX();
                pa[1] = agora;
            } else if (agora - pa[1] > 14_000) {
                colocarNoBarco(f, p, comecoDoDegrau(f, Math.max(p.getX(), f.x0 + 0.5) + SEG));
                pa[0] = p.getX();
                pa[1] = agora;
                p.sendActionBar(Masmorras.txt("§7Empurrei você um pouco para frente. §fSegure W§7!"));
            } else if (agora - pa[1] > 6_000 && f.passo % 8 == 0)
                p.sendActionBar(Masmorras.txt("§bSegure §fW §bpara descer! §7Use A/D para desviar."));
        }
        // bonecos de neve atiram em quem passa
        if (f.passo % 2 == 0) for (Player p : naPista) if (p.getVehicle() instanceof Boat) atirar(f, p, agora);
        // fim da descida: todo mundo chegou, acabou o tempo, ou nao sobrou ninguem na pista ha 15 s
        boolean alguem = !naPista.isEmpty();
        if (!alguem && !f.chegaram.isEmpty() || agora >= f.fimDescida || !alguem && agora - f.comecouDescida > 15_000) {
            for (Player p : naPista) chegou(f, p);
            comecarFase2(f);
        }
    }

    private Location comecoDoDegrau(Fase f, double x) {
        int k = (int) Math.floor((x - f.x0) / SEG);
        k = Math.max(0, Math.min(DEGRAUS - 1, k));
        double xx = k == 0 ? f.x0 - 2.5 : f.x0 + k * SEG + 0.5;
        return new Location(f.w, xx, f.ys - k + 1, f.cz + 0.5, -90f, 10f);
    }

    private void chegou(Fase f, Player p) {
        tirarDoBarco(f, p);
        f.chegaram.add(p.getUniqueId());
        f.parado.remove(p.getUniqueId());
        Location l = f.centro2.clone().add(-R2 + 3.5, 0.1, (rnd.nextInt(5) - 2) * 1.2);
        l.setYaw(-90f);
        p.teleport(l);
        p.setFallDistance(0);
        p.showTitle(Title.title(Masmorras.txt("§b§lCHEGOU!"), Masmorras.txt(f.etapa == Etapa.DESCIDA ? "§7Esperando os outros..." : "§7Lute!"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300))));
        p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", 1f, 1.2f);
    }

    private void atirar(Fase f, Player p, long agora) {
        if (rnd.nextDouble() < 0.65) return;   // nem sempre alguem atira (da para desviar)
        Entity v = p.getVehicle();
        Vector vel = v != null ? v.getVelocity() : p.getVelocity();
        for (UUID u : f.bonecos) {
            Entity b = Bukkit.getEntity(u);
            if (!(b instanceof Snowman s) || !s.isValid()) continue;
            double dx = s.getX() - p.getX();
            if (dx < 3 || dx > 16) continue;   // so os que estao na frente
            Long pronto = f.bonecoPronto.get(u);
            if (pronto != null && agora < pronto) continue;
            Location de = s.getEyeLocation();
            Location alvo = p.getLocation().add(vel.clone().multiply(5)).add(0, 1.0, 0);
            Vector dir = alvo.toVector().subtract(de.toVector());
            double dist = dir.length();
            if (dist < 1) continue;
            dir.normalize().multiply(1.3);
            dir.setY(dir.getY() + dist * 0.02);
            Snowball sb = s.launchProjectile(Snowball.class, dir);
            sb.getPersistentDataContainer().set(kBola, PersistentDataType.BYTE, (byte) 1);
            f.bonecoPronto.put(u, agora + 2500 + rnd.nextInt(1000));
            return;   // um tiro por vez em cada jogador
        }
    }

    // ------------------------------------------------------------------ fase 2 na arena de gelo

    private void comecarFase2(Fase f) {
        if (f.etapa == Etapa.FASE2) return;
        Chefe c = f.c;
        f.etapa = Etapa.FASE2;
        if (!c.vivo()) {
            fim(f);
            return;
        }
        // fecha a entrada da pista e tira os bonecos
        for (int z = f.cz - 3; z <= f.cz + 3; z++)
            for (int y = f.yFim + 1; y <= f.yFim + 8; y++) por(f, f.xFim, y, z, Material.BARRIER);
        for (UUID u : f.bonecos) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        f.bonecos.clear();
        c.m.extra = f.arena2;
        c.e.teleport(f.centro2.clone().add(3, 0.2, 0));
        c.e.setInvulnerable(false);
        if (c.e instanceof Mob mob) mob.setAI(true);
        c.e.removePotionEffect(PotionEffectType.SPEED);
        c.e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, pl.getConfig().getInt("descida.velocidade", 0), false, false));
        c.e.setHealth(Math.max(c.e.getHealth(), Math.min(c.vidaMax, c.vidaMax * pl.getConfig().getDouble("descida.recupera-vida", 0.8))));
        f.w.spawnParticle(Particle.SNOWFLAKE, c.e.getLocation().add(0, 1, 0), 80, 1, 1.2, 1, 0.1);
        for (Player p : jogadoresEm(f.w, f.arena2)) {
            p.showTitle(Title.title(Masmorras.txt("§b§l❄ FASE 2 ❄"), Masmorras.txt("§7O chão é de gelo: cuidado para não escorregar!"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(400))));
            p.sendMessage(Masmorras.txt("§b§l❄ " + c.t.chefeNome + ": §7fuja dos §bcírculos azuis§7: caem estalactites!"));
            p.playSound(p.getLocation(), "minecraft:entity.wither.spawn", 0.6f, 1.6f);
        }
        long agora = System.currentTimeMillis();
        f.proximaEstalactite = agora + 3000;
        f.proximaNevasca = agora + 8000;
        pl.getLogger().info("Descida do Gelo: fase 2 (masmorra #" + c.m.id + "), " + f.chegaram.size() + " jogador(es) na arena");
    }

    private void tickFase2(Fase f, long agora) {
        Chefe c = f.c;
        for (Player p : jogadoresEm(f.w, f.area)) {
            boolean naArena = f.arena2.contem(p.getX(), p.getY(), p.getZ());
            if (p.getY() < f.yFim - 1.5) mandarParaArena(f, p);   // escorregou e caiu no piso invisivel
            else if (!naArena && p.getX() < f.xFim) mandarParaArena(f, p);   // ficou na pista
        }
        if (!f.arena2.crescer(1).contem(c.e.getX(), c.e.getY(), c.e.getZ()) || c.e.getY() < f.yFim - 1) c.e.teleport(f.centro2);
        if (f.passo % 2 == 0) f.w.spawnParticle(Particle.SNOWFLAKE, f.centro2.clone().add(0, 6, 0), 25, R2 * 0.7, 2, R2 * 0.7, 0.01);
        List<Player> ps = jogadoresEm(f.w, f.arena2);
        if (ps.isEmpty()) return;
        boolean furia = c.e.getHealth() <= c.vidaMax * 0.3;
        if (agora >= f.proximaEstalactite) {
            List<Player> alvos = new ArrayList<>(ps);
            java.util.Collections.shuffle(alvos, rnd);
            int n = Math.min(alvos.size(), furia ? 3 : 2);
            for (int i = 0; i < n; i++) estalactite(f, alvos.get(i).getLocation().clone());
            if (furia) estalactite(f, f.centro2.clone().add(rnd.nextInt(R2 * 2 - 6) - R2 + 3, 0, rnd.nextInt(R2 * 2 - 6) - R2 + 3));
            f.proximaEstalactite = agora + (furia ? 2800 : 4000) + rnd.nextInt(800);
        }
        if (agora >= f.proximaNevasca) {   // leque de bolas de neve do chefe
            Player alvo = ps.get(rnd.nextInt(ps.size()));
            Vector base = alvo.getEyeLocation().toVector().subtract(c.e.getEyeLocation().toVector());
            if (base.lengthSquared() > 0.5) {
                base.normalize();
                for (int i = -2; i <= 2; i++) {
                    Vector d = base.clone().rotateAroundY(i * 0.18).multiply(1.4);
                    Snowball sb = c.e.launchProjectile(Snowball.class, d);
                    sb.getPersistentDataContainer().set(kBola, PersistentDataType.BYTE, (byte) 1);
                }
                f.w.playSound(c.e.getLocation(), "minecraft:entity.snow_golem.shoot", 1f, 0.7f);
            }
            f.proximaNevasca = agora + (furia ? 5000 : 7500) + rnd.nextInt(1500);
        }
    }

    private void mandarParaArena(Fase f, Player p) {
        tirarDoBarco(f, p);
        f.chegaram.add(p.getUniqueId());
        double a = rnd.nextDouble() * Math.PI * 2, d = 2 + rnd.nextDouble() * (R2 - 5);
        Location l = f.centro2.clone().add(Math.cos(a) * d, 0.2, Math.sin(a) * d);
        l.setYaw(p.getLocation().getYaw());
        p.teleport(l);
        p.setFallDistance(0);
    }

    private void estalactite(Fase f, Location alvo) {
        alvo.setY(f.yFim + 1);
        Particle.DustOptions azul = new Particle.DustOptions(Color.fromRGB(0x7FD6FF), 1.3f);
        double raio = 2.2;
        int[] k = {0};
        BukkitTask[] t = {null};
        t[0] = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (f.acabou || f.etapa != Etapa.FASE2) {
                t[0].cancel();
                return;
            }
            int i = k[0]++;
            if (i < 6) {
                for (int j = 0; j < 20; j++) {
                    double a = j * Math.PI / 10;
                    f.w.spawnParticle(Particle.DUST, alvo.clone().add(Math.cos(a) * raio, 0.15, Math.sin(a) * raio), 1, azul);
                }
                f.w.spawnParticle(Particle.SNOWFLAKE, alvo.clone().add(0, 4, 0), 6, 0.4, 0.5, 0.4, 0.01);
                if (i == 0) f.w.playSound(alvo, "minecraft:block.pointed_dripstone.drip_water", 1f, 0.6f);
                return;
            }
            t[0].cancel();
            f.w.spawnParticle(Particle.BLOCK, alvo.clone().add(0, 0.5, 0), 60, 1, 0.3, 1, 0, Material.PACKED_ICE.createBlockData());
            f.w.spawnParticle(Particle.SNOWFLAKE, alvo.clone().add(0, 0.5, 0), 40, 1.2, 0.4, 1.2, 0.05);
            f.w.playSound(alvo, "minecraft:block.glass.break", 1.2f, 0.6f);
            double dano = pl.getConfig().getDouble("descida.dano-estalactite", 4.0);
            for (Player p : jogadoresEm(f.w, f.arena2)) {
                if (p.getLocation().distanceSquared(alvo) > raio * raio + 0.5) continue;
                p.damage(dano, f.c.e);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1, false, true, true));
                p.setFreezeTicks(Math.max(p.getFreezeTicks(), 80));
            }
        }, 0L, 4L);
    }

    // ------------------------------------------------------------------ blocos

    private void por(Fase f, int x, int y, int z, Material m) {
        Block b = f.w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) return;   // so mexe onde era ar (assim sempre da para desfazer)
        b.setType(m, false);
        f.obra.add(b);
    }

    private void construir(Fase f) {
        int cz = f.cz;
        // plataforma de largada (gelo azul) com mureta
        for (int x = f.x0 - 8; x <= f.x0 - 1; x++)
            for (int z = cz - 4; z <= cz + 4; z++) {
                boolean borda = x == f.x0 - 8 || Math.abs(z - cz) == 4;
                por(f, x, f.ys, z, borda ? Material.PACKED_ICE : Material.BLUE_ICE);
                if (borda) {
                    por(f, x, f.ys + 1, z, Material.PACKED_ICE);
                    por(f, x, f.ys + 2, z, Material.PACKED_ICE);
                    por(f, x, f.ys + 3, z, Material.BARRIER);
                }
            }
        for (int z = cz - 3; z <= cz + 3; z += 3) por(f, f.x0 - 8, f.ys + 3, z, Material.SEA_LANTERN);
        // a pista: 12 degraus de 6 blocos, 5 de largura
        for (int k = 0; k < DEGRAUS; k++) {
            int y = f.ys - k;
            for (int x = f.x0 + k * SEG; x < f.x0 + (k + 1) * SEG; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) por(f, x, y, z, Material.BLUE_ICE);
                for (int s : new int[] {-1, 1}) {
                    int zw = cz + 3 * s;
                    // sem barreira em cima da mureta: ela segurava as bolas de neve (quem esta no barco nao sai mesmo)
                    for (int h = -1; h <= 2; h++) por(f, x, y + h, zw, Material.PACKED_ICE);
                }
                if ((x - f.x0) % 8 == 4) {
                    por(f, x, y + 1, cz + 3, Material.SEA_LANTERN);
                    por(f, x, y + 1, cz - 3, Material.SEA_LANTERN);
                }
            }
            // pilares de gelo no caminho (sempre sobra passagem)
            if (k >= 2 && k <= DEGRAUS - 2) {
                int x = f.x0 + k * SEG + 3;
                int z1 = cz - 2 + rnd.nextInt(5);
                por(f, x, y + 1, z1, Material.PACKED_ICE);
                por(f, x, y + 2, z1, Material.ICE);
                if (k % 3 == 0) {
                    int z2 = z1 <= cz ? z1 + 3 : z1 - 3;
                    if (z2 >= cz - 2 && z2 <= cz + 2) {
                        por(f, x + 1, y + 1, z2, Material.PACKED_ICE);
                        por(f, x + 1, y + 2, z2, Material.ICE);
                    }
                }
            }
            // bonecos de neve dos dois lados, num pilar de neve de fora da mureta
            if (k % 2 == 1) for (int s : new int[] {-1, 1}) {
                int x = f.x0 + k * SEG + 2, z = cz + 5 * s;
                for (int h = 0; h <= 4; h++) por(f, x, y + h, z, Material.SNOW_BLOCK);
                boneco(f, new Location(f.w, x + 0.5, y + 5, z + 0.5, s > 0 ? 180f : 0f, 0f));
            }
        }
        // emenda entre o fim da pista e a arena (sem fresta nos cantos)
        for (int x = f.xFim; x <= f.xFim + 2; x++)
            for (int z = cz - 4; z <= cz + 4; z++) {
                if (Math.abs(z - cz) <= 3) por(f, x, f.yFim, z, Material.BLUE_ICE);
                if (Math.abs(z - cz) >= 3) {
                    for (int h = 1; h <= 3; h++) por(f, x, f.yFim + h, z, Material.PACKED_ICE);
                    for (int h = 4; h <= 8; h++) por(f, x, f.yFim + h, z, Material.BARRIER);
                }
            }
        // piso de seguranca invisivel embaixo da arena e da emenda (quem escorregar cai nele e volta para o meio)
        for (int x = f.xFim - 2; x <= f.xa + R2 + 1; x++)
            for (int z = cz - R2 - 1; z <= cz + R2 + 1; z++) por(f, x, f.yFim - 3, z, Material.BARRIER);
        // arena de gelo da fase 2
        int y = f.yFim;
        Material[] gelo = {Material.PACKED_ICE, Material.PACKED_ICE, Material.PACKED_ICE, Material.ICE, Material.SNOW_BLOCK};
        for (int dx = -R2; dx <= R2; dx++)
            for (int dz = -R2; dz <= R2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R2 + 0.4) continue;
                int x = f.xa + dx, z = cz + dz;
                por(f, x, y, z, d < 2.5 ? Material.BLUE_ICE : gelo[rnd.nextInt(gelo.length)]);
                por(f, x, y - 1, z, Material.SNOW_BLOCK);
                boolean entrada = dx < 0 && Math.abs(dz) <= 3;
                if (d > R2 - 0.6 && !entrada) {
                    por(f, x, y + 1, z, Material.SNOW_BLOCK);
                    for (int h = 2; h <= 10; h++) por(f, x, y + h, z, Material.BARRIER);
                }
            }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            int px = f.xa + (int) Math.round(Math.cos(a) * 7), pz = cz + (int) Math.round(Math.sin(a) * 7);
            for (int h = 1; h <= 4; h++) por(f, px, y + h, pz, h == 4 ? Material.SEA_LANTERN : Material.PACKED_ICE);
        }
        for (int dx = -R2 + 3; dx <= R2 - 3; dx += 6)
            for (int dz = -R2 + 3; dz <= R2 - 3; dz += 6) por(f, f.xa + dx, y + 1, cz + dz, Material.LIGHT);
    }

    private void boneco(Fase f, Location l) {
        try {
            Snowman s = f.w.spawn(l, Snowman.class, x -> {
                x.setAI(false);
                x.setInvulnerable(true);
                x.setSilent(true);
                x.setPersistent(false);
                x.setDerp(false);
                x.setRemoveWhenFarAway(false);
                x.getPersistentDataContainer().set(kBoneco, PersistentDataType.BYTE, (byte) 1);
                NamespacedKey sem = NamespacedKey.fromString("levelledmobs:nolevel");
                if (sem != null) x.getPersistentDataContainer().set(sem, PersistentDataType.STRING, "masmorras");
                x.addScoreboardTag("clearlag_exclude");
            });
            f.bonecos.add(s.getUniqueId());
        } catch (Throwable ex) {
            pl.getLogger().warning("Descida do Gelo: boneco de neve nao nasceu: " + ex);
        }
    }

    /** Placas azuis na beirada da arena original (quem morreu e voltou pisa e volta para a luta). */
    private void placas(Fase f) {
        Caixa a = f.arena;
        int y = a.y1() - 1, cx = a.cx(), cz = a.cz();
        int[][] lugares = {{cx - 1, a.z1() + 1}, {cx, a.z1() + 1}, {cx + 1, a.z1() + 1}, {cx - 1, a.z2() - 1}, {cx, a.z2() - 1}, {cx + 1, a.z2() - 1},
                {a.x1() + 1, cz - 1}, {a.x1() + 1, cz}, {a.x1() + 1, cz + 1}, {a.x2() - 1, cz - 1}, {a.x2() - 1, cz}, {a.x2() - 1, cz + 1}};
        for (int[] l : lugares) {
            Block b = f.w.getBlockAt(l[0], y, l[1]);
            if (b.getState() instanceof TileState || !b.getType().isSolid() || !f.w.getBlockAt(l[0], y + 1, l[1]).isPassable()) continue;
            f.chao.add(b);
            f.chaoDados.add(b.getBlockData());
            b.setType(Material.LIGHT_BLUE_GLAZED_TERRACOTTA, false);
            f.placas.add(b);
        }
    }

    // ------------------------------------------------------------------ fim

    /** Fim da luta: morreu = o chefe foi derrotado (da tempo de pegar os premios la em cima). */
    void terminar(Chefe c, boolean morreu) {
        Fase f = fases.get(c);
        if (f == null || f.acabou) return;
        f.acabou = true;
        if (f.cena != null) f.cena.cancel();
        if (f.tarefa != null) f.tarefa.cancel();
        if (morreu && pl.isEnabled()) {
            for (Player p : jogadoresEm(f.w, f.area))
                p.showTitle(Title.title(Masmorras.txt("§b§lO GELO DERRETEU"), Masmorras.txt("§7Pegue os prêmios: em 6 s vocês voltam à fortaleza"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(500))));
            Bukkit.getScheduler().runTaskLater(pl, () -> fim(f), 120L);
        } else fim(f);
    }

    void fim(Fase f) {
        f.acabou = true;
        if (f.cena != null) f.cena.cancel();
        if (f.tarefa != null) f.tarefa.cancel();
        if (f.c != null) fases.remove(f.c);
        if (f.w == null) return;
        Location volta = f.arena == null ? null : new Location(f.w, f.arena.cx() + 0.5, f.arena.y1() + 0.1, f.arena.cz() + 1.5);
        // 1) jogadores (e premios) que estao la em cima descem para a arena original
        if (f.area != null && volta != null) {
            for (Player p : jogadoresEm(f.w, f.area.crescer(6))) {
                tirarDoBarco(f, p);
                p.teleport(volta.clone().add(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1));
                p.setFallDistance(0);
            }
            for (Entity x : f.w.getNearbyEntities(f.centro2, R2 + 4, 14, R2 + 4))
                if (x instanceof Item || x instanceof ExperienceOrb) x.teleport(volta);
        }
        // 2) barcos, bonecos e bolas de neve
        for (UUID u : new ArrayList<>(f.barcos.values())) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) {
                e.eject();
                e.remove();
            }
        }
        f.barcos.clear();
        for (UUID u : f.bonecos) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        f.bonecos.clear();
        if (f.area != null)
            for (Entity x : f.w.getNearbyEntities(new Location(f.w, (f.area.x1() + f.area.x2()) / 2.0, (f.area.y1() + f.area.y2()) / 2.0, f.cz + 0.5),
                    (f.area.x2() - f.area.x1()) / 2.0 + 4, 20, R2 + 6)) {
                if (x instanceof Snowball && x.getPersistentDataContainer().has(kBola)) x.remove();
                if (x instanceof Boat && x.getPersistentDataContainer().has(kBarco)) {
                    x.eject();
                    x.remove();
                }
                if (x instanceof Snowman && x.getPersistentDataContainer().has(kBoneco)) x.remove();
            }
        // 3) placas da arena original voltam e a obra no ceu some
        for (int i = 0; i < f.chao.size(); i++) f.chao.get(i).setBlockData(f.chaoDados.get(i), false);
        f.chao.clear();
        int n = f.obra.size();
        for (int i = n - 1; i >= 0; i--) f.obra.get(i).setType(Material.AIR, false);
        f.obra.clear();
        if (f.c != null && f.c.m.extra != null && (f.c.m.extra == f.area || f.c.m.extra == f.arena2)) f.c.m.extra = null;
        if (f.c != null && f.c.e != null && f.c.e.isValid()) {
            f.c.e.setInvulnerable(false);
            if (f.c.e instanceof Mob mob) mob.setAI(true);
        }
        for (int[] t : f.tickets) f.w.removePluginChunkTicket(t[0], t[1], pl);
        f.tickets.clear();
        pl.getLogger().info("Descida do Gelo: fim (masmorra #" + (f.c == null ? "?" : f.c.m.id) + "), " + n + " blocos desfeitos");
    }

    void desligar() {
        for (Fase f : new ArrayList<>(fases.values())) fim(f);
    }

    private static List<Player> jogadoresEm(World w, Caixa a) {
        List<Player> l = new ArrayList<>();
        for (Player p : w.getPlayers())
            if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && a.contem(p.getX(), p.getY(), p.getZ())) l.add(p);
        return l;
    }

    private Fase faseDe(Entity e) {
        for (Fase f : fases.values())
            if (f.w != null && f.w.equals(e.getWorld()) && f.area != null && f.area.crescer(4).contem(e.getX(), e.getY(), e.getZ())) return f;
        return null;
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(ignoreCancelled = true)
    public void bolaAcertou(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball sb) || !sb.getPersistentDataContainer().has(kBola)) return;
        Entity hit = e.getHitEntity();
        Player p = hit instanceof Player pp ? pp
                : hit instanceof Boat b && !b.getPassengers().isEmpty() && b.getPassengers().get(0) instanceof Player pb ? pb : null;
        sb.getWorld().spawnParticle(Particle.SNOWFLAKE, sb.getLocation(), 12, 0.3, 0.3, 0.3, 0.05);
        if (p == null) return;
        Fase f = faseDe(p);
        if (f == null) return;
        p.damage(pl.getConfig().getDouble("descida.dano-bola", 1.0));
        p.setFreezeTicks(Math.max(p.getFreezeTicks(), 60));
        p.playSound(p.getLocation(), "minecraft:entity.player.hurt_freeze", 1f, 1.2f);
        Entity v = p.getVehicle();
        if (v instanceof Boat) {
            // empurra o barco para o lado e tira velocidade
            double lado = sb.getVelocity().getZ() >= 0 ? 1 : -1;
            Vector vel = v.getVelocity();
            v.setVelocity(new Vector(vel.getX() * 0.4, 0.12, lado * 0.55));
        } else if (f.etapa == Etapa.FASE2) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true, true));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void naoSai(VehicleExitEvent e) {
        if (!e.getVehicle().getPersistentDataContainer().has(kBarco) || !(e.getExited() instanceof Player p)) return;
        for (Fase f : fases.values()) {
            if (f.acabou || f.soltando.contains(p.getUniqueId())) continue;
            if (f.etapa == Etapa.DESCIDA && f.barcos.containsKey(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendActionBar(Masmorras.txt("§bSegure firme no barco até lá embaixo!"));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void barcoNaoQuebra(VehicleDamageEvent e) {
        if (e.getVehicle().getPersistentDataContainer().has(kBarco)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void barcoNaoQuebra2(VehicleDestroyEvent e) {
        if (e.getVehicle().getPersistentDataContainer().has(kBarco)) e.setCancelled(true);
    }

    /** Boneco de neve da pista nao toma dano (nem de flecha). */
    @EventHandler(ignoreCancelled = true)
    public void bonecoNaoApanha(EntityDamageEvent e) {
        if (e.getEntity() instanceof Snowman s && s.getPersistentDataContainer().has(kBoneco)) e.setCancelled(true);
    }

    /** Saiu do jogo la em cima: volta na entrada da masmorra (a pista e temporaria). */
    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        for (Fase f : fases.values()) {
            if (f.area == null || !f.area.crescer(6).contem(p.getX(), p.getY(), p.getZ())) continue;
            tirarDoBarco(f, p);
            Caixa s = f.c.m.salas.get(0);
            p.teleport(new Location(f.w, s.cx() + 0.5, s.y1(), s.cz() + 0.5));
            f.chegaram.remove(p.getUniqueId());
            f.parado.remove(p.getUniqueId());
        }
    }
}
