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
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Segunda fase do Arauto do Trovao (14/11, ideia do Paulo): com 60% da vida o chefe chama a tempestade, um trovao
 * quebra o chao da arena e todo mundo que cai no buraco e puxado para uma arena de nuvens no ceu, em cima do templo.
 * La a luta fica insana sem o chefe bater mais forte: ele fica mais rapido e recupera vida, e a propria tempestade
 * ataca (raios avisados com um circulo no chao, ventania que empurra todo mundo). Quando a luta acaba, o chao volta
 * e a arena do ceu some. Os blocos mexidos ficam guardados em memoria e voltam sempre (morte, limpeza, desligar).
 */
final class TempestadeTrovao implements Listener {

    static final int R = 13;

    final Masmorras pl;
    final NamespacedKey kCaco;
    final Map<Chefe, Fase> fases = new HashMap<>();
    final Random rnd = new Random();

    static final class Fase {
        Chefe c;
        World w;
        Caixa arena, ceu;
        Location centro;
        int ys;
        final List<Block> chao = new ArrayList<>();
        final List<BlockData> chaoDados = new ArrayList<>();
        final List<Block> obra = new ArrayList<>();
        final Set<UUID> noCeu = new HashSet<>();
        boolean emCena = true, acabou;
        BukkitTask cena, tarefa;
        long proximoRaio, proximaVentania, ventaniaEm = -1, proximoAmbiente;
        Vector ventoDir;
    }

    TempestadeTrovao(Masmorras pl) {
        this.pl = pl;
        this.kCaco = new NamespacedKey(pl, "caco_tempestade");
    }

    boolean ocupado(Chefe c) {
        Fase f = fases.get(c);
        return f != null && f.emCena;
    }

    /** Chamado no tique do chefe. Devolve true quando a fase comecou agora. */
    boolean conferir(Chefe c, List<Player> ps) {
        if (c.tempestadeFeita || fases.containsKey(c)) return false;
        if (!"TROVAO".equals(c.t.id) && !c.forcarTempestade) return false;
        if (!pl.getConfig().getBoolean("tempestade.ativa", true)) return false;
        if (c.e.getHealth() > c.vidaMax * pl.getConfig().getDouble("tempestade.vida", 0.6)) return false;
        c.tempestadeFeita = true;
        try {
            comecar(c, ps);
        } catch (Throwable ex) {
            pl.getLogger().warning("Tempestade do Trovao: " + ex);
            Fase f = fases.get(c);
            if (f != null) fim(f);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ comeco (cena de 4 s)

    private void comecar(Chefe c, List<Player> ps) {
        Fase f = new Fase();
        f.c = c;
        f.w = c.e.getWorld();
        f.arena = c.m.arena();
        int cx = f.arena.cx(), cz = f.arena.cz();
        int alto = f.arena.y2();
        for (int dx = -R - 2; dx <= R + 2; dx += 3)
            for (int dz = -R - 2; dz <= R + 2; dz += 3) alto = Math.max(alto, f.w.getHighestBlockYAt(cx + dx, cz + dz));
        f.ys = Math.min(f.w.getMaxHeight() - 14, Math.max(f.arena.y2() + 25, alto + 18));
        f.ceu = new Caixa(cx - R - 2, f.ys - 6, cz - R - 2, cx + R + 2, f.ys + 14, cz + R + 2);
        f.centro = new Location(f.w, cx + 0.5, f.ys + 1, cz + 0.5);
        fases.put(c, f);
        construirCeu(f);
        c.e.setInvulnerable(true);
        if (c.e instanceof Mob mob) mob.setAI(false);
        pl.getLogger().info("Tempestade: " + c.t.chefeNome + " (masmorra #" + c.m.id + ") chamou a tempestade - arena no ceu em y=" + f.ys);
        int[] k = {0};
        f.cena = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            int i = k[0]++;
            List<Player> naArena = jogadoresEm(f.w, f.arena.crescer(2));
            if (i == 0) {
                for (Player p : naArena) {
                    p.showTitle(Title.title(Masmorras.txt("§e§l⚡ O CÉU SE ABRIU ⚡"), Masmorras.txt("§7" + c.t.chefeNome + " chamou a tempestade..."),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(300))));
                    tempo(p, true);
                    p.playSound(p.getLocation(), "minecraft:entity.lightning_bolt.thunder", 1f, 0.6f);
                }
            }
            if (i < 25 && i % 3 == 0) {   // raios caindo em volta da arena
                Location l = new Location(f.w, f.arena.x1() + rnd.nextInt(f.arena.x2() - f.arena.x1() + 1) + 0.5, f.arena.y1(),
                        f.arena.z1() + rnd.nextInt(f.arena.z2() - f.arena.z1() + 1) + 0.5);
                f.w.strikeLightningEffect(l);
                f.w.spawnParticle(Particle.ELECTRIC_SPARK, c.e.getLocation().add(0, 1, 0), 30, 0.6, 1, 0.6, 0.2);
                for (Player p : naArena) p.playSound(p.getLocation(), "minecraft:block.deepslate.break", 1.2f, 0.5f);
            }
            if (i == 18) for (Player p : naArena)
                p.showTitle(Title.title(Masmorras.txt("§c§lO CHÃO VAI CEDER!"), Masmorras.txt("§7Segure-se..."),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(200))));
            if (i == 25) {   // o grande trovao: o chao quebra
                Location meio = new Location(f.w, f.arena.cx() + 0.5, f.arena.y1(), f.arena.cz() + 0.5);
                f.w.strikeLightningEffect(meio);
                f.w.strikeLightningEffect(meio.clone().add(2, 0, 1));
                f.w.spawnParticle(Particle.EXPLOSION_EMITTER, meio, 2, 1, 0, 1, 0);
                for (Player p : naArena) {
                    p.playSound(p.getLocation(), "minecraft:entity.generic.explode", 1.2f, 0.6f);
                    p.playSound(p.getLocation(), "minecraft:entity.lightning_bolt.thunder", 1.5f, 0.5f);
                }
                quebrarChao(f);
                c.m.extra = f.ceu;
            }
            if (i >= 25) for (Player p : jogadoresEm(f.w, f.arena.crescer(2))) if (caiu(f, p)) mandarProCeu(f, p);
            if (i >= 40) {   // 4 s: quem ficou em cima de alguma coisa vai junto; o chefe aparece la em cima
                for (Player p : jogadoresEm(f.w, f.arena.crescer(2))) mandarProCeu(f, p);
                f.cena.cancel();
                f.cena = null;
                comecarFase2(f);
            }
        }, 0L, 2L);
    }

    private void comecarFase2(Fase f) {
        Chefe c = f.c;
        if (!c.vivo()) {
            fim(f);
            return;
        }
        c.e.teleport(f.centro.clone().add(0, 0.2, -6));
        c.e.setInvulnerable(false);
        if (c.e instanceof Mob mob) mob.setAI(true);
        // mais rapido e com mais vida, sem bater mais forte
        c.e.removePotionEffect(PotionEffectType.SPEED);
        c.e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION,
                pl.getConfig().getInt("tempestade.velocidade", 1), false, false));
        double cura = pl.getConfig().getDouble("tempestade.recupera-vida", 0.85);
        c.e.setHealth(Math.max(c.e.getHealth(), Math.min(c.vidaMax, c.vidaMax * cura)));
        c.e.setGlowing(true);
        f.w.strikeLightningEffect(c.e.getLocation());
        for (Player p : jogadoresEm(f.w, f.ceu)) {
            p.showTitle(Title.title(Masmorras.txt("§e§l⚡ FASE 2 ⚡"), Masmorras.txt("§7" + c.t.chefeNome + " absorveu a tempestade!"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(400))));
            p.sendMessage(Masmorras.txt("§e§l⚡ " + c.t.chefeNome + ": §7cuidado com os §ecírculos de raio§7 no chão e com a §bventania§7!"));
            p.playSound(p.getLocation(), "minecraft:entity.wither.spawn", 0.7f, 1.3f);
        }
        long agora = System.currentTimeMillis();
        f.proximoRaio = agora + 2500;
        f.proximaVentania = agora + 9000;
        f.emCena = false;
        f.tarefa = Bukkit.getScheduler().runTaskTimer(pl, () -> tick(f), 10L, 10L);
    }

    // ------------------------------------------------------------------ a fase no ceu

    private void tick(Fase f) {
        if (f.acabou) return;
        Chefe c = f.c;
        if (!c.vivo()) return;   // a morte chama terminar()
        long agora = System.currentTimeMillis();
        // quem chega depois (renasceu e subiu a torre) cai pelo buraco e vem tambem
        for (Player p : jogadoresEm(f.w, f.arena.crescer(2))) if (caiu(f, p)) mandarProCeu(f, p);
        List<Player> ps = jogadoresEm(f.w, f.ceu);
        for (Player p : ps) if (p.getY() < f.ys - 3) {   // caiu da nuvem (nao deveria): volta para o meio
            p.teleport(f.centro.clone().add(0, 1, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, true));
        }
        if (!f.ceu.crescer(1).contem(c.e.getX(), c.e.getY(), c.e.getZ())) c.e.teleport(f.centro);
        // chuva, nuvens e raios ao longe
        f.w.spawnParticle(Particle.FALLING_WATER, f.centro.clone().add(0, 7, 0), 60, R * 0.7, 2, R * 0.7, 0);
        f.w.spawnParticle(Particle.CLOUD, f.centro.clone().add(0, -1, 0), 14, R * 0.8, 0.3, R * 0.8, 0.01);
        if (agora >= f.proximoAmbiente) {
            double a = rnd.nextDouble() * Math.PI * 2, d = R + 5 + rnd.nextInt(8);
            f.w.strikeLightningEffect(f.centro.clone().add(Math.cos(a) * d, -2, Math.sin(a) * d));
            f.proximoAmbiente = agora + 1200 + rnd.nextInt(1800);
        }
        if (ps.isEmpty()) return;
        boolean furia = c.e.getHealth() <= c.vidaMax * 0.3;
        // raios avisados: circulo no chao por 1,2 s e depois o raio
        if (agora >= f.proximoRaio) {
            List<Player> alvos = new ArrayList<>(ps);
            java.util.Collections.shuffle(alvos, rnd);
            int n = Math.min(alvos.size(), furia ? 3 : 2);
            for (int i = 0; i < n; i++) raioAvisado(f, alvos.get(i).getLocation().clone());
            if (furia) raioAvisado(f, f.centro.clone().add(rnd.nextInt(R * 2 - 4) - R + 2, 0, rnd.nextInt(R * 2 - 4) - R + 2));
            f.proximoRaio = agora + (furia ? 2600 : 3800) + rnd.nextInt(800);
        }
        // ventania: aviso, depois empurra todo mundo para um lado (a parede invisivel segura)
        if (f.ventaniaEm < 0 && agora >= f.proximaVentania) {
            double a = rnd.nextDouble() * Math.PI * 2;
            f.ventoDir = new Vector(Math.cos(a), 0, Math.sin(a));
            f.ventaniaEm = agora + 1500;
            for (Player p : ps) {
                p.sendActionBar(Masmorras.txt("§b§l≋ VENTANIA! §7Segure-se ou mude de lado!"));
                p.playSound(p.getLocation(), "minecraft:item.elytra.flying", 0.8f, 1.4f);
            }
        }
        if (f.ventaniaEm > 0) {
            Location de = f.centro.clone().subtract(f.ventoDir.clone().multiply(R));
            for (int i = 0; i < 6; i++)
                f.w.spawnParticle(Particle.GUST, de.clone().add(f.ventoDir.clone().multiply(i * 4.5)).add(rnd.nextGaussian() * 3, 1, rnd.nextGaussian() * 3), 1);
            if (agora >= f.ventaniaEm) {
                for (Player p : ps) {
                    p.setVelocity(f.ventoDir.clone().multiply(furia ? 1.6 : 1.25).setY(0.35));
                    p.playSound(p.getLocation(), "minecraft:entity.breeze.wind_burst", 1f, 0.8f);
                }
                f.ventaniaEm = -1;
                f.proximaVentania = agora + (furia ? 8000 : 11000) + rnd.nextInt(3000);
            }
        }
    }

    private void raioAvisado(Fase f, Location alvo) {
        alvo.setY(f.ys + 1);
        Particle.DustOptions amarelo = new Particle.DustOptions(Color.fromRGB(0xF5F05A), 1.3f);
        double raio = 2.4;
        int[] k = {0};
        BukkitTask[] t = {null};
        t[0] = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (f.acabou) {
                t[0].cancel();
                return;
            }
            int i = k[0]++;
            if (i < 6) {
                for (int j = 0; j < 20; j++) {
                    double a = j * Math.PI / 10;
                    f.w.spawnParticle(Particle.DUST, alvo.clone().add(Math.cos(a) * raio, 0.15, Math.sin(a) * raio), 1, amarelo);
                }
                f.w.spawnParticle(Particle.ELECTRIC_SPARK, alvo.clone().add(0, 0.3, 0), 6, raio * 0.4, 0.1, raio * 0.4, 0.05);
                if (i == 0) f.w.playSound(alvo, "minecraft:block.beacon.power_select", 0.8f, 1.8f);
                return;
            }
            t[0].cancel();
            f.w.strikeLightningEffect(alvo);
            double dano = pl.getConfig().getDouble("tempestade.dano-raio", 4.0);
            for (Player p : jogadoresEm(f.w, f.ceu)) {
                if (p.getLocation().distanceSquared(alvo) > raio * raio + 0.5) continue;
                p.damage(dano, f.c.e);
                Vector v = p.getLocation().toVector().subtract(alvo.toVector()).setY(0);
                if (v.lengthSquared() < 0.01) v = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5);
                p.setVelocity(v.normalize().multiply(0.7).setY(0.45));
            }
        }, 0L, 4L);
    }

    /** O jogador caiu pelo buraco do chao? */
    private boolean caiu(Fase f, Player p) {
        Caixa a = f.arena;
        return p.getX() >= a.x1() && p.getX() < a.x2() + 1 && p.getZ() >= a.z1() && p.getZ() < a.z2() + 1
                && p.getY() < a.y1() - 0.8 && p.getY() > a.y1() - 20;
    }

    private void mandarProCeu(Fase f, Player p) {
        double a = rnd.nextDouble() * Math.PI * 2, d = 3 + rnd.nextDouble() * 5;
        Location l = f.centro.clone().add(Math.cos(a) * d, 9, Math.sin(a) * d);
        l.setYaw(p.getLocation().getYaw());
        l.setPitch(30);
        p.teleport(l);
        p.setFallDistance(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 70, 0, false, false, true));
        tempo(p, true);
        if (f.noCeu.add(p.getUniqueId()) || !f.emCena) {
            p.showTitle(Title.title(Masmorras.txt("§b§lA TEMPESTADE TE PUXOU"), Masmorras.txt("§7para o céu do Templo do Trovão!"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), "minecraft:entity.breeze.wind_burst", 1f, 0.6f);
        }
    }

    private static void tempo(Player p, boolean tempestade) {
        if (tempestade) {
            p.setPlayerWeather(WeatherType.DOWNFALL);
            p.setPlayerTime(18000, false);
        } else {
            p.resetPlayerWeather();
            p.resetPlayerTime();
        }
    }

    // ------------------------------------------------------------------ blocos (chao quebrado e arena no ceu)

    private void quebrarChao(Fase f) {
        Caixa a = f.arena;
        int y = a.y1() - 1;
        // o miolo do chao (fica uma beirada de 1 bloco junto da parede, onde estao os baus) e o que estiver em cima dele
        for (int x = a.x1() + 1; x <= a.x2() - 1; x++)
            for (int z = a.z1() + 1; z <= a.z2() - 1; z++) {
                for (int yy : new int[] {a.y1(), y}) {
                    Block b = f.w.getBlockAt(x, yy, z);
                    if (b.getType().isAir()) continue;
                    if (b.getState() instanceof TileState) continue;
                    if (yy == a.y1() && b.getType().isSolid() && b.getType() != Material.LODESTONE) continue;   // pilar: fica
                    f.chao.add(b);
                    f.chaoDados.add(b.getBlockData());
                    if (yy == y && rnd.nextInt(3) == 0) caco(f, b);
                    b.setType(Material.AIR, false);
                }
            }
        f.w.spawnParticle(Particle.BLOCK, new Location(f.w, a.cx() + 0.5, y + 0.5, a.cz() + 0.5), 200,
                (a.x2() - a.x1()) / 3.0, 0.3, (a.z2() - a.z1()) / 3.0, 0, f.chaoDados.isEmpty() ? Material.STONE.createBlockData() : f.chaoDados.get(0));
    }

    /** Pedaco do chao caindo (so visual: nao vira bloco nem item). */
    private void caco(Fase f, Block b) {
        try {
            FallingBlock fb = f.w.spawn(b.getLocation().add(0.5, 0, 0.5), FallingBlock.class, x -> {
                x.setBlockData(b.getBlockData());
                x.setDropItem(false);
                x.setCancelDrop(true);
                x.setHurtEntities(false);
                x.getPersistentDataContainer().set(kCaco, PersistentDataType.BYTE, (byte) 1);
                x.setVelocity(new Vector((rnd.nextDouble() - 0.5) * 0.2, -0.2, (rnd.nextDouble() - 0.5) * 0.2));
            });
            Bukkit.getScheduler().runTaskLater(pl, () -> { if (fb.isValid()) fb.remove(); }, 30L);
        } catch (Throwable ignorado) {
            // sem o visual do pedaco caindo
        }
    }

    private void por(Fase f, int x, int y, int z, Material m) {
        Block b = f.w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) return;   // so mexe onde era ar (assim sempre da para desfazer)
        b.setType(m, false);
        f.obra.add(b);
    }

    private void construirCeu(Fase f) {
        int cx = f.arena.cx(), cz = f.arena.cz(), y = f.ys;
        Material[] nuvem = {Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, Material.WHITE_WOOL, Material.LIGHT_GRAY_CONCRETE, Material.SNOW_BLOCK};
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 0.4) continue;
                Material m;
                if (d < 2.5) m = Material.WAXED_CHISELED_COPPER;
                else if (Math.abs(d - 6) < 0.5) m = Material.WAXED_OXIDIZED_CUT_COPPER;
                else if (d < 6) m = Material.SMOOTH_QUARTZ;
                else m = nuvem[rnd.nextInt(nuvem.length)];
                por(f, cx + dx, y, cz + dz, m);
                if (d < R - 1.5) por(f, cx + dx, y - 1, cz + dz, Material.WHITE_WOOL);   // nuvem por baixo
                if (d < R - 5 && rnd.nextInt(3) > 0) por(f, cx + dx, y - 2, cz + dz, Material.WHITE_WOOL);
                // parede invisivel na borda (ninguem cai, nem com a ventania)
                if (d > R - 0.6) for (int h = 1; h <= 5; h++) por(f, cx + dx, y + h, cz + dz, Material.BARRIER);
            }
        // 4 colunas de cobre com para-raios
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            int px = cx + (int) Math.round(Math.cos(a) * 9), pz = cz + (int) Math.round(Math.sin(a) * 9);
            for (int h = 1; h <= 4; h++) por(f, px, y + h, pz, h == 4 ? Material.WAXED_CHISELED_COPPER : Material.WAXED_OXIDIZED_COPPER_GRATE);
            por(f, px, y + 5, pz, Material.LIGHTNING_ROD);
        }
        // luz para nao ficar um breu na tempestade
        for (int dx = -R + 3; dx <= R - 3; dx += 6)
            for (int dz = -R + 3; dz <= R - 3; dz += 6) por(f, cx + dx, y + 1, cz + dz, Material.LIGHT);
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
            for (Player p : jogadoresEm(f.w, f.ceu)) {
                p.showTitle(Title.title(Masmorras.txt("§a§lA TEMPESTADE PASSOU"), Masmorras.txt("§7Pegue os prêmios: em 6 s vocês voltam ao templo"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(500))));
                tempo(p, false);
            }
            Bukkit.getScheduler().runTaskLater(pl, () -> fim(f), 120L);
        } else fim(f);
    }

    private void fim(Fase f) {
        f.acabou = true;
        if (f.cena != null) f.cena.cancel();
        if (f.tarefa != null) f.tarefa.cancel();
        fases.remove(f.c);
        // 1) o chao volta
        for (int i = 0; i < f.chao.size(); i++) f.chao.get(i).setBlockData(f.chaoDados.get(i), false);
        Location volta = new Location(f.w, f.arena.cx() + 0.5, f.arena.y1() + 0.1, f.arena.cz() + 1.5);
        // 2) quem esta no ceu (e os premios que ficaram la) desce para o templo
        if (f.ceu != null) {
            for (Player p : jogadoresEm(f.w, f.ceu.crescer(4))) {
                p.teleport(volta.clone().add(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1));
                p.setFallDistance(0);
                tempo(p, false);
            }
            for (Entity x : f.w.getNearbyEntities(f.centro, R + 3, 12, R + 3))
                if (x instanceof Item || x instanceof ExperienceOrb) x.teleport(volta);
        }
        for (Player p : jogadoresEm(f.w, f.arena.crescer(4))) tempo(p, false);
        if (f.c.m.extra == f.ceu) f.c.m.extra = null;
        // 3) a arena do ceu some
        for (int i = f.obra.size() - 1; i >= 0; i--) f.obra.get(i).setType(Material.AIR, false);
        f.obra.clear();
        pl.getLogger().info("Tempestade: fim (masmorra #" + f.c.m.id + "), chao de volta (" + f.chao.size() + " blocos)");
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

    // ------------------------------------------------------------------ eventos

    @EventHandler(ignoreCancelled = true)
    public void cacoNaoVira(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock fb && fb.getPersistentDataContainer().has(kCaco)) {
            e.setCancelled(true);
            fb.remove();
        }
    }

    /** Saiu do jogo la em cima: volta na entrada da masmorra (a arena do ceu e temporaria). */
    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        for (Fase f : fases.values()) {
            if (f.ceu == null || !f.ceu.crescer(4).contem(p.getX(), p.getY(), p.getZ())) continue;
            Caixa s = f.c.m.salas.get(0);
            p.teleport(new Location(f.w, s.cx() + 0.5, s.y1(), s.cz() + 0.5));
            tempo(p, false);
        }
    }

    @EventHandler
    public void renasceu(PlayerRespawnEvent e) {
        if (!fases.isEmpty()) tempo(e.getPlayer(), false);
    }
}
