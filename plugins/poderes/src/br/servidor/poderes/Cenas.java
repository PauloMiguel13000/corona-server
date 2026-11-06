package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

/**
 * A versao das animacoes PARA O CELULAR (Bedrock pelo Geyser). O Bedrock nao desenha os displays que fazem as
 * animacoes do PC (Efeitos/Visuais), entao aqui elas sao refeitas com o que o Bedrock mostra: blocos de verdade
 * (FallingBlock) que voam, crescem em aneis e formam o raio do Kamehameha, itens girando, fogos de artificio,
 * presas do Evocador, nuvens coloridas e particulas que o Geyser traduz.
 * TUDO aqui so aparece para quem joga no Bedrock: as entidades nascem invisiveis e sao mostradas so para eles, e
 * as particulas sao mandadas so para eles. Quem joga no PC ve exatamente a animacao do PC, sem nada a mais.
 * Sem jogador de celular por perto, nada disso e feito.
 */
final class Cenas {

    private final Poderes pl;
    /** Limite de entidades de enfeite por tick (para o celular nao travar). */
    private int orcamento;
    private static final int MAX_POR_TICK = 70;

    /**
     * A extensao GeyserDisplayEntity esta no Geyser? Entao o celular ja ve os ItemDisplay do PC (laminas, folhas,
     * estrela, tridente gigante) e os gemeos de ITEM nao sao criados (senao apareceriam em dobro).
     * Os gemeos de BLOCO continuam, porque a extensao nao mostra BlockDisplay.
     */
    final boolean itensNoCelular;

    Cenas(Poderes pl) {
        this.pl = pl;
        this.itensNoCelular = extensaoDeItens(pl);
        pl.getLogger().info(itensNoCelular
                ? "Celular: extensao GeyserDisplayEntity encontrada - itens do PC aparecem no celular, gemeos de item desligados."
                : "Celular: sem a extensao GeyserDisplayEntity - usando os gemeos de item.");
        Bukkit.getScheduler().runTaskTimer(pl, () -> orcamento = 0, 1L, 1L);
    }

    static boolean extensaoDeItens(org.bukkit.plugin.Plugin pl) {
        String modo = pl.getConfig().getString("celular.extensao-itens", "auto").toLowerCase();
        if (modo.equals("sim")) return true;
        if (modo.equals("nao")) return false;
        java.io.File pasta = new java.io.File(pl.getDataFolder().getParentFile(), "Geyser-Spigot" + java.io.File.separator + "extensions");
        java.io.File[] fs = pasta.listFiles();
        if (fs == null) return false;
        for (java.io.File f : fs) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".jar") && n.contains("displayentity")) return true;
        }
        return false;
    }

    private Visuais vis() {
        return pl.vis;
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    private void depois(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(pl, r, Math.max(1L, ticks));
    }

    static Particle.DustOptions cor(int r, int g, int b, float t) {
        return new Particle.DustOptions(Color.fromRGB(r, g, b), t);
    }

    static final Color OURO = Color.fromRGB(255, 205, 40), AMARELO = Color.fromRGB(255, 240, 120), BRANCO = Color.WHITE,
            AZUL = Color.fromRGB(70, 190, 255), CIANO = Color.fromRGB(120, 235, 255), LARANJA = Color.fromRGB(255, 130, 20),
            VERMELHO = Color.fromRGB(230, 40, 30), VERDE = Color.fromRGB(70, 220, 60), LIMA = Color.fromRGB(160, 255, 90),
            ROXO = Color.fromRGB(150, 60, 220), ROSA = Color.fromRGB(255, 130, 200), PRETO = Color.fromRGB(25, 25, 30),
            CINZA = Color.fromRGB(130, 130, 140), AGUA = Color.fromRGB(40, 140, 255);

    static BlockData b(Material m) {
        return m.createBlockData();
    }

    // =============================================================== quem ve (so Bedrock)

    /** Jogador do Bedrock (Geyser/Floodgate): UUID com a parte alta zerada ou nome com o prefixo ".". */
    static boolean ehBedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || p.getName().startsWith(".");
    }

    /** Jogadores do celular perto do lugar (quem vai ver a cena). */
    List<Player> plateia(Location l) {
        List<Player> v = new ArrayList<>();
        if (l == null || l.getWorld() == null) return v;
        for (Player p : l.getWorld().getPlayers())
            if (ehBedrock(p) && p.getLocation().distanceSquared(l) < 80 * 80) v.add(p);
        return v;
    }

    private static void part(List<Player> v, Particle p, Location l, int n, double dx, double dy, double dz, double s, Object dado) {
        for (Player x : v) x.spawnParticle(p, l, n, dx, dy, dz, s, dado);
    }

    private void mostrar(Entity e, List<Player> v) {
        for (Player x : v) x.showEntity(pl, e);
    }

    /** Esconde de quem joga no PC uma entidade que ja existe (ex.: tridente extra so para o celular). */
    void soBedrock(Entity e) {
        for (Player x : e.getWorld().getPlayers()) if (!ehBedrock(x)) x.hideEntity(pl, e);
    }

    // =============================================================== pecas basicas (so para o celular)

    void fogos(List<Player> v, Location l, FireworkEffect.Type tipo, boolean brilho, Color... cores) {
        if (v.isEmpty() || orcamento++ > MAX_POR_TICK) return;
        Firework fw = l.getWorld().spawn(l, Firework.class, f -> {
            f.setVisibleByDefault(false);
            FireworkMeta m = f.getFireworkMeta();
            m.clearEffects();
            m.addEffect(FireworkEffect.builder().with(tipo).withColor(cores).withFade(BRANCO).flicker(brilho).trail(brilho).build());
            m.setPower(0);
            f.setFireworkMeta(m);
            f.setPersistent(false);
        });
        mostrar(fw, v);
        vis().marcarVisual(fw, 8);
        depois(2, () -> {
            if (fw.isValid() && !fw.isDetonated()) fw.detonate();
        });
    }

    /** Usado de fora (ex.: Tridente Trovejante). */
    void fogos(Location l, FireworkEffect.Type tipo, boolean brilho, Color... cores) {
        fogos(plateia(l), l, tipo, brilho, cores);
    }

    FallingBlock bloco(List<Player> v, Location l0, BlockData b, Vector vel, boolean gravidade, int vida) {
        if (v.isEmpty() || orcamento++ > MAX_POR_TICK) return null;
        // nunca nasce dentro de um bloco (senao "assenta" na hora e some)
        Location l = l0.clone();
        for (int i = 0; i < 3 && !l.getBlock().isPassable(); i++) l.setY(Math.floor(l.getY()) + 1);
        FallingBlock fb;
        try {
            fb = l.getWorld().spawn(l, FallingBlock.class, x -> {
                x.setVisibleByDefault(false);
                x.setBlockData(b);
                prepararBloco(x, gravidade);
            });
        } catch (Throwable t) {
            fb = l.getWorld().spawnFallingBlock(l, b);
            fb.setVisibleByDefault(false);
            prepararBloco(fb, gravidade);
        }
        if (vel != null) fb.setVelocity(vel);
        mostrar(fb, v);
        vis().marcarVisual(fb, vida);
        return fb;
    }

    private static void prepararBloco(FallingBlock x, boolean gravidade) {
        x.setDropItem(false);
        x.setCancelDrop(true);
        x.setHurtEntities(false);
        x.setGravity(gravidade);
        x.setInvulnerable(true);
        x.setPersistent(false);
    }

    /** Usado de fora (meteoro, bala do canhao). */
    FallingBlock bloco(Location l, BlockData b, Vector vel, boolean gravidade, int vida) {
        return bloco(plateia(l), l, b, vel, gravidade, vida);
    }

    Item item(List<Player> v, Location l, ItemStack it, Vector vel, boolean gravidade, int vida) {
        if (v.isEmpty() || orcamento++ > MAX_POR_TICK) return null;
        Item i = l.getWorld().dropItem(l, it, x -> {
            x.setVisibleByDefault(false);
            x.setPickupDelay(32767);
            x.setCanPlayerPickup(false);
            x.setCanMobPickup(false);
            x.setGravity(gravidade);
            x.setInvulnerable(true);
            x.setPersistent(false);
            x.setUnlimitedLifetime(true);
            x.setVelocity(vel != null ? vel : new Vector());
        });
        mostrar(i, v);
        vis().marcarVisual(i, vida);
        return i;
    }

    Item item(Location l, ItemStack it, Vector vel, boolean gravidade, int vida) {
        return item(plateia(l), l, it, vel, gravidade, vida);
    }

    void presa(List<Player> v, Location l, Player dono, int atraso) {
        if (v.isEmpty() || orcamento++ > MAX_POR_TICK) return;
        EvokerFangs f = l.getWorld().spawn(l, EvokerFangs.class, x -> {
            x.setVisibleByDefault(false);
            x.setOwner(dono);
            x.setAttackDelay(Math.max(0, atraso));
            x.setPersistent(false);
        });
        mostrar(f, v);
        vis().marcarVisual(f, atraso + 30);
    }

    void presasEmAnel(List<Player> v, Location c, Player dono, double raio, int qtd, int atraso) {
        for (int i = 0; i < qtd; i++) {
            double a = Math.PI * 2 * i / qtd;
            presa(v, chao(c.clone().add(Math.cos(a) * raio, 0, Math.sin(a) * raio)), dono, atraso);
        }
    }

    void nuvem(List<Player> v, Location c, Color cor, float raio, int ticks) {
        if (v.isEmpty() || orcamento++ > MAX_POR_TICK) return;
        AreaEffectCloud n = c.getWorld().spawn(c, AreaEffectCloud.class, x -> {
            x.setVisibleByDefault(false);
            x.setRadius(raio);
            x.setRadiusPerTick(0f);
            x.setDuration(ticks);
            x.setWaitTime(0);
            x.setDurationOnUse(0);
            x.setParticle(Particle.ENTITY_EFFECT, cor);
            x.setPersistent(false);
        });
        mostrar(n, v);
        vis().marcarVisual(n, ticks + 2);
    }

    /** Leva a entidade de enfeite ate 'para' neste tick (com velocidade: fica suave). */
    static void levar(Entity e, Location para) {
        levar(e, para, 1);
    }

    /** Leva a entidade ate 'para' em 'ticks' ticks (para quem e chamado de 2 em 2 ticks). */
    static void levar(Entity e, Location para, int ticks) {
        if (e == null || !e.isValid()) return;
        Vector v = para.toVector().subtract(e.getLocation().toVector());
        if (v.lengthSquared() > 36) {
            e.teleport(para);
            e.setVelocity(new Vector());
        } else e.setVelocity(v.multiply(1.0 / Math.max(1, ticks)));
    }

    static Location chao(Location l) {
        Location x = l.clone();
        for (int i = 0; i < 4 && !x.getBlock().isPassable(); i++) x.add(0, 1, 0);
        for (int i = 0; i < 6 && x.clone().subtract(0, 1, 0).getBlock().isPassable(); i++) x.subtract(0, 1, 0);
        x.setY(x.getBlockY());
        return x;
    }

    /** Blocos pulando do chao para os lados. */
    void erupcao(List<Player> v, Location c, BlockData[] tipos, int n, double forca, double subida, int vida) {
        ThreadLocalRandom r = rnd();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble(Math.PI * 2), f = forca * r.nextDouble(0.4, 1.0);
            Vector vel = new Vector(Math.cos(a) * f, subida * r.nextDouble(0.6, 1.1), Math.sin(a) * f);
            bloco(v, c.clone().add(Math.cos(a) * 0.4, 0.3, Math.sin(a) * 0.4), tipos[r.nextInt(tipos.length)], vel, true, vida);
        }
    }

    /** Blocos saindo do centro em todas as direcoes (esfera que explode) e sumindo. */
    void explosaoDeBlocos(List<Player> v, Location c, BlockData bd, int n, double raio, int ticks) {
        double ouro = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - (i / (double) Math.max(1, n - 1)) * 2, rr = Math.sqrt(Math.max(0, 1 - y * y)), th = ouro * i;
            Vector dir = new Vector(Math.cos(th) * rr, y, Math.sin(th) * rr).multiply(raio / Math.max(1, ticks));
            bloco(v, c.clone().subtract(0, 0.5, 0), bd, dir, false, ticks + 1);
        }
    }

    // --------------------------------------------------------------- formas de particula (so para o celular)

    static void circulo(List<Player> v, Location c, double r, int pontos, Particle p, Object dado) {
        for (int i = 0; i < pontos; i++) {
            double a = Math.PI * 2 * i / pontos;
            part(v, p, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, dado);
        }
    }

    void onda(List<Player> v, Location c, double rMax, int passos, Particle p, Object dado, double pontosPorBloco) {
        for (int k = 1; k <= passos; k++) {
            final double r = rMax * k / passos;
            depois(k, () -> circulo(v, c, r, Math.max(8, (int) (Math.PI * 2 * r * pontosPorBloco)), p, dado));
        }
    }

    static void esfera(List<Player> v, Location c, double r, int pontos, Particle p, Object dado) {
        double ouro = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < pontos; i++) {
            double y = 1 - (i / (double) Math.max(1, pontos - 1)) * 2, rr = Math.sqrt(Math.max(0, 1 - y * y)), th = ouro * i;
            part(v, p, c.clone().add(Math.cos(th) * rr * r, y * r, Math.sin(th) * rr * r), 1, 0, 0, 0, 0, dado);
        }
    }

    static void linha(List<Player> v, Location a, Location b, double passo, Particle p, Object dado, int n, double espalha) {
        Vector d = b.toVector().subtract(a.toVector());
        double dist = d.length();
        if (dist < 0.05) return;
        d.normalize();
        for (double x = 0; x <= dist; x += passo) part(v, p, a.clone().add(d.clone().multiply(x)), n, espalha, espalha, espalha, 0, dado);
    }

    void espiral(List<Player> v, Location base, double r, double altura, double voltas, int pontos, int ticks, Particle p, Object dado, int bracos) {
        int porTick = Math.max(1, pontos / Math.max(1, ticks));
        for (int t = 0; t < ticks; t++) {
            final int tt = t;
            depois(t + 1, () -> {
                for (int k = 0; k < porTick; k++) {
                    double s = (tt * porTick + k) / (double) pontos;
                    for (int b = 0; b < bracos; b++) {
                        double a = s * voltas * Math.PI * 2 + Math.PI * 2 * b / bracos;
                        part(v, p, base.clone().add(Math.cos(a) * r, s * altura, Math.sin(a) * r), 1, 0, 0, 0, 0, dado);
                    }
                }
            });
        }
    }

    static void raioParticulas(List<Player> v, Location a, Location b, double desvio) {
        Vector total = b.toVector().subtract(a.toVector());
        double dist = total.length();
        if (dist < 0.2) return;
        int partes = Math.max(3, (int) (dist / 1.1));
        Location ant = a.clone();
        ThreadLocalRandom r = rnd();
        for (int i = 1; i <= partes; i++) {
            Location pt = a.clone().add(total.clone().multiply((double) i / partes));
            if (i < partes) pt.add(r.nextDouble(-desvio, desvio), r.nextDouble(-desvio, desvio), r.nextDouble(-desvio, desvio));
            linha(v, ant, pt, 0.18, Particle.END_ROD, null, 1, 0.0);
            linha(v, ant, pt, 0.35, Particle.ELECTRIC_SPARK, null, 1, 0.02);
            ant = pt;
        }
    }

    /** Usado de fora (Tridente Trovejante). */
    void raioParticulas(Location a, Location b, double desvio) {
        raioParticulas(plateia(b), a, b, desvio);
    }

    /** Segue um projetil desenhando o rastro (so para o celular); no fim chama 'fim' (pode ser null). */
    void seguirRastro(Entity e, int max, Consumer<Location> rastro, Consumer<Location> fim) {
        final Location[] ultima = {e.getLocation()};
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!e.isValid() || e.isDead() || t[0]++ > max) {
                tarefa.cancel();
                if (fim != null) fim.accept(ultima[0]);
                return;
            }
            ultima[0] = e.getLocation();
            if (rastro != null) rastro.accept(ultima[0]);
        }, 1L, 1L);
    }

    // =============================================================== GEMEOS dos displays (Efeitos): a mesma
    // animacao do PC, feita com blocos de verdade, so para o celular

    void gemeoAnel(Location c, BlockData bd, double raio, int ticks, int pecas, Color brilho) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        int n = Math.min(14, Math.max(6, pecas));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vector vel = new Vector(Math.cos(a), 0, Math.sin(a)).multiply(raio / Math.max(1, ticks) * 1.08);
            bloco(v, c.clone().subtract(0, 0.5, 0), bd, vel, false, ticks + 1);
        }
        if (brilho != null) circulo(v, c.clone().add(0, 0.3, 0), raio * 0.6, 16, Particle.DUST, new Particle.DustOptions(brilho, 1.4f));
    }

    void gemeoPulso(Location c, BlockData casca, BlockData miolo, float s1, int ticks, Color brilho) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        if (miolo != null) bloco(v, c.clone().subtract(0, 0.5, 0), miolo, null, false, Math.max(3, ticks / 2));
        explosaoDeBlocos(v, c, casca, s1 >= 2.5f ? 10 : 6, s1 * 0.55, ticks);
        if (brilho != null) {
            esfera(v, c, s1 * 0.5, 24, Particle.DUST, new Particle.DustOptions(brilho, 1.6f));
            if (s1 >= 1.6f) fogos(v, c, FireworkEffect.Type.BALL, false, brilho, BRANCO);
        }
    }

    void gemeoEstilhacos(Location c, BlockData[] tipos, int n, double alcance, int ticks) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        erupcao(v, c, tipos, Math.min(10, n), alcance / Math.max(4, ticks) * 2.2, 0.45, ticks + 8);
    }

    void gemeoPilar(Location base, BlockData bd, double altura, int subir, int ficar) {
        List<Player> v = plateia(base);
        if (v.isEmpty()) return;
        Location pe = chao(base.clone().add(0, 0.5, 0)).add(0, 0.02, 0);
        FallingBlock fb = bloco(v, pe, bd, new Vector(0, Math.min(1.6, altura) / Math.max(1, subir), 0), false, subir + ficar + 8);
        if (fb == null) return;
        depois(subir + 1, () -> {
            if (fb.isValid()) fb.setVelocity(new Vector());
        });
        depois(subir + ficar + 1, () -> {
            if (fb.isValid()) fb.setVelocity(new Vector(0, -Math.min(1.6, altura) / 5.0, 0));
        });
    }

    void gemeoBolhas(Location c, Color cor, int n, double raio, double subida) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        Particle.DustOptions d = new Particle.DustOptions(cor != null ? cor : BRANCO, 1.5f);
        for (int i = 0; i < n; i++) {
            double a = rnd().nextDouble(Math.PI * 2), r = Math.sqrt(rnd().nextDouble()) * raio;
            Location pe = c.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            linha(v, pe, pe.clone().add(0, subida * rnd().nextDouble(0.6, 1.0), 0), 0.3, Particle.DUST, d, 1, 0.04);
        }
    }

    void gemeoRaio(Location de, Location ate, Color brilho) {
        List<Player> v = plateia(ate);
        if (v.isEmpty()) return;
        raioParticulas(v, de, ate, 0.7);
        if (brilho != null) linha(v, de, ate, 0.5, Particle.DUST, new Particle.DustOptions(brilho, 1.6f), 1, 0.06);
    }

    void gemeoCorte(Location a, Location b, Color brilho) {
        List<Player> v = plateia(b);
        if (v.isEmpty()) return;
        linha(v, a, b, 0.15, Particle.DUST, new Particle.DustOptions(brilho != null ? brilho : BRANCO, 1.1f), 1, 0.0);
    }

    void gemeoColuna(Location base, BlockData bd, double altura, int vida, Color brilho) {
        List<Player> v = plateia(base);
        if (v.isEmpty()) return;
        int n = (int) Math.min(4, Math.max(1, Math.round(altura)));
        for (int i = 0; i < n; i++) bloco(v, base.clone().add(0, i, 0), bd, null, false, Math.max(4, vida / 2));
        if (brilho != null) linha(v, base, base.clone().add(0, altura + 1, 0), 0.2, Particle.DUST, new Particle.DustOptions(brilho, 1.5f), 2, 0.25);
        part(v, Particle.END_ROD, base.clone().add(0, altura * 0.5, 0), 12, 0.3, altura * 0.3, 0.3, 0.05, null);
    }

    /** Bloco (ou item) do celular acompanhando um projetil, como o nucleo/cristal que o PC ve. */
    void gemeoSeguir(Entity alvo, BlockData bd, ItemStack it, int max) {
        List<Player> v = plateia(alvo.getLocation());
        if (v.isEmpty()) return;
        Entity e = bd != null ? bloco(v, alvo.getLocation().subtract(0, 0.5, 0), bd, null, false, max + 4)
                : item(v, alvo.getLocation(), it, null, false, max + 4);
        if (e == null) return;
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!alvo.isValid() || alvo.isDead() || t[0]++ > max || !e.isValid()) {
                tarefa.cancel();
                vis().remover(e);
                return;
            }
            levar(e, alvo.getLocation().subtract(0, bd != null ? 0.5 : 0.25, 0));
        }, 1L, 1L);
    }

    /** Itens girando em volta do jogador (laminas, folhas): so para o celular. */
    void gemeoOrbitar(Player p, ItemStack it, int n, double raio, double altura, double velocidade, int ticks) {
        if (itensNoCelular) return;
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        List<Item> itens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Item x = item(v, p.getLocation().add(Math.cos(a) * raio, altura - 0.25, Math.sin(a) * raio), it.clone(), null, false, ticks + 4);
            if (x != null) itens.add(x);
        }
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!p.isOnline() || p.isDead() || t[0] > ticks) {
                tarefa.cancel();
                for (Item i : itens) vis().remover(i);
                return;
            }
            Location c = p.getLocation().add(0, altura - 0.25, 0);
            double base = t[0] * velocidade;
            for (int i = 0; i < itens.size(); i++) {
                double a = base + Math.PI * 2 * i / itens.size();
                levar(itens.get(i), c.clone().add(Math.cos(a) * raio, Math.sin(t[0] * 0.25 + i) * 0.15, Math.sin(a) * raio));
            }
            if (t[0] % 3 == 0) circulo(v, c.clone().add(0, 0.3, 0), raio, 10, Particle.CRIT, null);
            t[0]++;
        }, 1L, 1L);
    }

    // =============================================================== MAGO

    void fogoLancar(Player p, Entity bola) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        Particle.DustOptions laranja = cor(255, 140, 30, 1.6f), amarelo = cor(255, 220, 60, 1.1f);
        seguirRastro(bola, 60, pos -> {
            part(v, Particle.FLAME, pos, 4, 0.12, 0.12, 0.12, 0.01, null);
            part(v, Particle.DUST, pos, 3, 0.18, 0.18, 0.18, 0, laranja);
            part(v, Particle.DUST, pos, 1, 0.08, 0.08, 0.08, 0, amarelo);
        }, fim -> {
            fogos(v, fim, FireworkEffect.Type.BURST, true, LARANJA, VERMELHO, AMARELO);
            part(v, Particle.FLAME, fim, 30, 0.4, 0.4, 0.4, 0.12, null);
            part(v, Particle.LAVA, fim, 5, 0.3, 0.2, 0.3, 0, null);
        });
    }

    void geloLancar(Player p, Entity bola) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        Particle.DustOptions azul = cor(150, 220, 255, 1.5f);
        seguirRastro(bola, 60, pos -> {
            part(v, Particle.SNOWFLAKE, pos, 3, 0.1, 0.1, 0.1, 0.01, null);
            part(v, Particle.DUST, pos, 3, 0.15, 0.15, 0.15, 0, azul);
        }, fim -> {
            fogos(v, fim, FireworkEffect.Type.STAR, true, CIANO, BRANCO, AZUL);
            part(v, Particle.SNOWFLAKE, fim, 35, 0.5, 0.5, 0.5, 0.08, null);
        });
    }

    void veneno(Player p, Location c) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        nuvem(v, c.clone().add(0, 0.1, 0), Color.fromRGB(80, 220, 60), 3.5f, 100);
        fogos(v, c.clone().add(0, 0.8, 0), FireworkEffect.Type.BALL_LARGE, false, VERDE, LIMA, Color.fromRGB(30, 120, 30));
        onda(v, c.clone().add(0, 0.2, 0), 3.5, 7, Particle.COPPER_FIRE_FLAME, null, 3.5);
    }

    void raio(Player p, Location alvo) {
        List<Player> v = plateia(alvo);
        if (v.isEmpty()) return;
        Location chao = alvo.clone().add(0, 0.1, 0);
        circulo(v, chao, 2.5, 40, Particle.ENCHANT, null);
        circulo(v, chao, 1.6, 26, Particle.END_ROD, null);
        fogos(v, alvo.clone().add(0, 1, 0), FireworkEffect.Type.BURST, true, CIANO, BRANCO, AZUL);
        part(v, Particle.ELECTRIC_SPARK, alvo.clone().add(0, 1, 0), 50, 1.0, 1.2, 1.0, 0.3, null);
    }

    void meteoroImpacto(Location alvo) {
        List<Player> v = plateia(alvo);
        if (v.isEmpty()) return;
        fogos(v, alvo.clone().add(0, 0.6, 0), FireworkEffect.Type.BALL_LARGE, true, LARANJA, VERMELHO, AMARELO);
        BlockData chao = Poderes.chaoDe(alvo);
        part(v, Particle.DUST_PILLAR, alvo.clone().add(0, 0.3, 0), 30, 1.5, 0.2, 1.5, 0, chao);
        onda(v, alvo.clone().add(0, 0.2, 0), 3.2, 6, Particle.FLAME, null, 3.0);
    }

    // =============================================================== ENGENHEIRO

    void torreChega(Location l, Color cor, BlockData vidro) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        fogos(v, l.clone().add(0, 1.2, 0), FireworkEffect.Type.BURST, false, cor, BRANCO);
        part(v, Particle.ELECTRIC_SPARK, l.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.1, null);
    }

    void raioDeGelo(Location a, Location b) {
        List<Player> v = plateia(b);
        if (v.isEmpty()) return;
        linha(v, a, b, 0.25, Particle.DUST, cor(150, 220, 255, 1.4f), 1, 0.04);
        linha(v, a, b, 0.6, Particle.SNOWFLAKE, null, 1, 0.05);
    }

    void jatoDeFogo(Location a, Location b) {
        List<Player> v = plateia(b);
        if (v.isEmpty()) return;
        linha(v, a, b, 0.3, Particle.FLAME, null, 2, 0.12);
        linha(v, a, b, 0.6, Particle.DUST, cor(255, 140, 30, 1.6f), 1, 0.1);
    }

    void tesla(Location a, Location b) {
        List<Player> v = plateia(b);
        if (v.isEmpty()) return;
        raioParticulas(v, a, b, 0.4);
    }

    void canhaoImpacto(Location pt) {
        List<Player> v = plateia(pt);
        if (v.isEmpty()) return;
        fogos(v, pt.clone().add(0, 0.5, 0), FireworkEffect.Type.BALL, false, LARANJA, CINZA, AMARELO);
        part(v, Particle.DUST_PILLAR, pt.clone().add(0, 0.3, 0), 20, 1.2, 0.2, 1.2, 0, Poderes.chaoDe(pt));
    }

    // =============================================================== SAIYAJIN

    void kiRastro(Location pos, boolean ssj) {
        List<Player> v = plateia(pos);
        if (v.isEmpty()) return;
        esfera(v, pos, 0.3, 10, Particle.DUST, ssj ? cor(255, 200, 30, 1.7f) : cor(255, 230, 90, 1.6f));
        part(v, Particle.END_ROD, pos, 1, 0.05, 0.05, 0.05, 0, null);
    }

    void kiImpacto(Location pos, boolean ssj, boolean grande) {
        List<Player> v = plateia(pos);
        if (v.isEmpty()) return;
        part(v, Particle.END_ROD, pos, 10, 0.3, 0.3, 0.3, 0.12, null);
        if (grande) fogos(v, pos, FireworkEffect.Type.BALL, true, OURO, AMARELO, BRANCO);
    }

    void teleKi(Location l) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        linha(v, l, l.clone().add(0, 2.6, 0), 0.15, Particle.END_ROD, null, 1, 0.08);
        part(v, Particle.ELECTRIC_SPARK, l.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.1, null);
    }

    void voo(Player p) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        Location l = p.getLocation();
        part(v, Particle.GUST_EMITTER_SMALL, l.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0, null);
        part(v, Particle.DUST_PILLAR, l.clone().add(0, 0.2, 0), 25, 1.2, 0.1, 1.2, 0, Poderes.chaoDe(l));
        Particle.DustOptions branco = cor(255, 250, 220, 1.3f);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!p.isOnline() || p.isDead() || t[0] > 80) {
                tarefa.cancel();
                return;
            }
            part(v, Particle.DUST, p.getLocation().add(0, 1, 0), 4, 0.35, 0.7, 0.35, 0, branco);
            part(v, Particle.END_ROD, p.getLocation(), 2, 0.2, 0.05, 0.2, 0.01, null);
            t[0] += 2;
        }, 2L, 2L);
    }

    // --------------------------------------------------------------- KAMEHAMEHA no celular: igual ao do PC,
    // com blocos de vidro azul de verdade (esfera nas maos, raio grosso de blocos e bola na ponta)

    private static final class Kame {
        FallingBlock esfera;
        final List<FallingBlock> raio = new ArrayList<>();
        final List<FallingBlock> bola = new ArrayList<>();
        List<Player> v;
    }

    private final Map<UUID, Kame> kames = new HashMap<>();

    /** Carga (a cada 2 ticks): bloco de vidro azul nas maos + energia sendo puxada. */
    void kameCarga(Player p, Location mao, int t) {
        Kame k = kames.get(p.getUniqueId());
        if (k == null) {
            List<Player> v = plateia(mao);
            if (v.isEmpty()) return;
            k = new Kame();
            k.v = v;
            kames.put(p.getUniqueId(), k);
        }
        List<Player> v = k.v;
        if (k.esfera == null || !k.esfera.isValid()) k.esfera = bloco(v, mao.clone().subtract(0, 0.5, 0), b(Material.LIGHT_BLUE_STAINED_GLASS), null, false, 80);
        levar(k.esfera, mao.clone().subtract(0, 0.5, 0), 2);
        double r = Math.min(0.9, 0.3 + t * 0.025);
        esfera(v, mao, r, 16 + t / 2, Particle.DUST, cor(80, 200, 255, 1.6f));
        esfera(v, mao, r * 0.5, 8, Particle.DUST, cor(230, 250, 255, 1.2f));
        part(v, Particle.SOUL_FIRE_FLAME, mao, 2, r * 0.4, r * 0.4, r * 0.4, 0.01, null);
        for (int i = 0; i < 3; i++) {
            double a = rnd().nextDouble(Math.PI * 2), bb = rnd().nextDouble(-1.2, 1.2), d = 1.9;
            Location de = mao.clone().add(Math.cos(a) * Math.cos(bb) * d, Math.sin(bb) * d, Math.sin(a) * Math.cos(bb) * d);
            linha(v, de, mao, 0.4, Particle.END_ROD, null, 1, 0.0);
        }
        if (t % 6 == 0) circulo(v, mao, r + 0.5, 16, Particle.ELECTRIC_SPARK, null);
    }

    /** O raio (a cada 2 ticks): fileira de blocos de vidro azul (e branco no meio) do jogador ate a ponta. */
    void kameRaio(Player p, Location inicio, Vector dir, double dist, double grossura, int tick) {
        Kame k = kames.get(p.getUniqueId());
        if (k == null) {
            List<Player> v = plateia(inicio);
            if (v.isEmpty()) return;
            k = new Kame();
            k.v = v;
            kames.put(p.getUniqueId(), k);
        }
        List<Player> v = k.v;
        if (k.esfera != null) {
            vis().remover(k.esfera);
            k.esfera = null;
        }
        Vector d = dir.clone().normalize();
        int n = (int) Math.min(32, Math.max(1, Math.round(dist)));
        while (k.raio.size() < n) {
            int i = k.raio.size();
            Location pt = inicio.clone().add(d.clone().multiply(i + 0.5)).subtract(0, 0.5, 0);
            FallingBlock fb = bloco(v, pt, b(i % 4 == 2 ? Material.WHITE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS), null, false, 100);
            if (fb == null) break;
            k.raio.add(fb);
        }
        for (int i = 0; i < k.raio.size(); i++) {
            FallingBlock fb = k.raio.get(i);
            Location alvo = inicio.clone().add(d.clone().multiply(Math.min(i, n - 1) + 0.5)).subtract(0, 0.5, 0);
            if (fb == null || !fb.isValid()) {
                FallingBlock novo = bloco(v, alvo, b(i % 4 == 2 ? Material.WHITE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS), null, false, 80);
                if (novo != null) k.raio.set(i, novo);
                continue;
            }
            levar(fb, alvo, 2);
        }
        // bola na ponta: 6 blocos em volta do fim do raio, girando
        Location ponta = inicio.clone().add(d.clone().multiply(dist));
        while (k.bola.size() < 6) {
            FallingBlock fb = bloco(v, ponta.clone().subtract(0, 0.5, 0), b(k.bola.size() % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS), null, false, 100);
            if (fb == null) break;
            k.bola.add(fb);
        }
        for (int i = 0; i < k.bola.size(); i++) {
            double a = tick * 0.35 + Math.PI * 2 * i / Math.max(1, k.bola.size());
            double rr = 0.9 + grossura;
            Vector lado = d.clone().crossProduct(new Vector(0, 1, 0));
            if (lado.lengthSquared() < 0.01) lado = new Vector(1, 0, 0);
            lado.normalize();
            Vector cima = lado.clone().crossProduct(d).normalize();
            Location pt = ponta.clone().add(lado.multiply(Math.cos(a) * rr)).add(cima.multiply(Math.sin(a) * rr)).subtract(0, 0.5, 0);
            levar(k.bola.get(i), pt, 2);
        }
        // brilho em volta do raio
        for (double x = 0.5; x <= dist; x += 1.2)
            part(v, Particle.SOUL_FIRE_FLAME, inicio.clone().add(d.clone().multiply(x)), 1, 0.45, 0.45, 0.45, 0.005, null);
        if (tick % 4 == 0) for (double x = 1; x <= dist; x += 3)
            part(v, Particle.END_ROD, inicio.clone().add(d.clone().multiply(x)), 1, 0.6, 0.6, 0.6, 0.02, null);
        part(v, Particle.SOUL_FIRE_FLAME, ponta, 6, 0.6, 0.6, 0.6, 0.05, null);
    }

    void kameImpacto(Player p, Location ponta) {
        Kame k = kames.get(p.getUniqueId());
        List<Player> v = k != null ? k.v : plateia(ponta);
        if (v.isEmpty()) return;
        fogos(v, ponta, FireworkEffect.Type.BALL_LARGE, true, AZUL, CIANO, BRANCO);
        if (!ponta.clone().subtract(0, 1.5, 0).getBlock().isPassable()) {
            BlockData chao = Poderes.chaoDe(ponta);
            part(v, Particle.DUST_PILLAR, ponta.clone().add(0, 0.2, 0), 25, 1.4, 0.2, 1.4, 0, chao);
            erupcao(v, ponta, new BlockData[] {chao, b(Material.COBBLESTONE)}, 5, 0.35, 0.5, 26);
        }
    }

    /** Fim do Kamehameha: tira os blocos do raio. */
    void kameFim(Player p) {
        Kame k = kames.remove(p.getUniqueId());
        if (k == null) return;
        if (k.esfera != null) vis().remover(k.esfera);
        for (FallingBlock fb : k.raio) vis().remover(fb);
        for (FallingBlock fb : k.bola) vis().remover(fb);
    }

    // --------------------------------------------------------------- Super Saiyajin

    void ssjTransformar(Player p, Location l) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        BlockData chao = Poderes.chaoDe(l);
        fogos(v, l.clone().add(0, 1.2, 0), FireworkEffect.Type.BALL_LARGE, true, OURO, AMARELO, BRANCO);
        depois(4, () -> fogos(v, l.clone().add(0, 2.2, 0), FireworkEffect.Type.STAR, true, OURO, AMARELO));
        explosaoDeBlocos(v, l.clone().add(0, 1, 0), b(Material.YELLOW_STAINED_GLASS), 12, 2.6, 10);
        part(v, Particle.DUST_PILLAR, l.clone().add(0, 0.2, 0), 40, 2.0, 0.2, 2.0, 0, chao);
        // pedras subindo devagar em volta
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + rnd().nextDouble(-0.2, 0.2), r = rnd().nextDouble(1.6, 3.4);
            Location pe = chao(l.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r)).add(0, 0.1, 0);
            FallingBlock pedra = bloco(v, pe, rnd().nextBoolean() ? chao : b(Material.COBBLESTONE), new Vector(0, 0.06, 0), false, 60);
            if (pedra == null) continue;
            final int[] t = {0};
            Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
                if (!pedra.isValid() || t[0]++ > 55) {
                    tarefa.cancel();
                    vis().remover(pedra);
                    return;
                }
                pedra.setVelocity(new Vector(0, t[0] < 40 ? 0.06 : 0.0, 0));
            }, 1L, 1L);
        }
        espiral(v, l, 1.2, 3.5, 3, 60, 15, Particle.END_ROD, null, 3);
    }

    void ssjAura(Player p, int t) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        Location pe = p.getLocation();
        Particle.DustOptions ouro = cor(255, 205, 40, 1.6f), claro = cor(255, 245, 150, 1.0f);
        for (int i = 0; i < 6; i++) {
            double a = rnd().nextDouble(Math.PI * 2), r = rnd().nextDouble(0.35, 0.6);
            part(v, Particle.DUST, pe.clone().add(Math.cos(a) * r, rnd().nextDouble(0.0, 2.1), Math.sin(a) * r), 1, 0, 0, 0, 0, i % 3 == 0 ? claro : ouro);
        }
        part(v, Particle.FLAME, pe.clone().add(0, 0.1, 0), 2, 0.3, 0.05, 0.3, 0.02, null);
        if (t % 12 == 0) circulo(v, pe.clone().add(0, 0.1, 0), 0.9, 14, Particle.DUST, ouro);
    }

    // =============================================================== GUERREIRO

    void investida(Player p, Location ini) {
        List<Player> v = plateia(ini);
        if (v.isEmpty()) return;
        part(v, Particle.GUST_EMITTER_SMALL, ini.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0, null);
        part(v, Particle.DUST_PILLAR, ini.clone().add(0, 0.2, 0), 15, 0.6, 0.1, 0.6, 0, Poderes.chaoDe(ini));
    }

    /** Forca do Golem: punho de ferro de verdade voando ate o alvo (o PC ve o punho de display). */
    void golem(Player p, Location alvo) {
        List<Player> v = plateia(alvo);
        if (v.isEmpty()) return;
        Location mao = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.9)).subtract(0, 0.7, 0);
        Location fim = alvo.clone().add(0, 0.6, 0);
        FallingBlock punho = bloco(v, mao, b(Material.IRON_BLOCK), fim.toVector().subtract(mao.toVector()).multiply(0.5), false, 8);
        depois(2, () -> {
            if (punho != null) vis().remover(punho);
            fogos(v, fim.clone().add(0, 0.4, 0), FireworkEffect.Type.BURST, false, BRANCO, CINZA);
            part(v, Particle.DUST_PILLAR, alvo.clone().add(0, 0.2, 0), 30, 1.5, 0.2, 1.5, 0, Poderes.chaoDe(alvo));
        });
    }

    void grito(Player p, Location l) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        part(v, Particle.SHRIEK, l.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0, 0);
        depois(4, () -> part(v, Particle.SHRIEK, l.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0, 0));
        part(v, Particle.GUST_EMITTER_LARGE, l.clone().add(0, 1, 0), 1, 0, 0, 0, 0, null);
        fogos(v, p.getEyeLocation().add(0, 0.6, 0), FireworkEffect.Type.BURST, false, VERMELHO, LARANJA, BRANCO);
    }

    void golemChega(Location l) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        fogos(v, l.clone().add(0, 1.5, 0), FireworkEffect.Type.BALL, false, BRANCO, CINZA);
        part(v, Particle.DUST_PILLAR, l.clone().add(0, 0.2, 0), 35, 1.8, 0.2, 1.8, 0, Poderes.chaoDe(l));
    }

    void terremotoSalto(Player p) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        part(v, Particle.GUST_EMITTER_SMALL, p.getLocation().add(0, 0.3, 0), 1, 0, 0, 0, 0, null);
    }

    void terremoto(Location c, BlockData terra) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        part(v, Particle.EXPLOSION_EMITTER, c.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0, null);
        for (int anel = 0; anel < 3; anel++) {
            final double r = 1.8 + anel * 2.2;
            depois(1L + anel * 3L, () -> part(v, Particle.DUST_PILLAR, c.clone().add(0, 0.2, 0), 24, r, 0.1, r, 0, terra));
        }
    }

    // =============================================================== NINJA

    void fumaca(Location l) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        fogos(v, l.clone().add(0, 1, 0), FireworkEffect.Type.BALL_LARGE, false, PRETO, CINZA);
        part(v, Particle.SQUID_INK, l.clone().add(0, 1, 0), 60, 1.4, 0.8, 1.4, 0.05, null);
        part(v, Particle.CAMPFIRE_SIGNAL_SMOKE, l.clone().add(0, 0.5, 0), 20, 1.6, 0.3, 1.6, 0.01, null);
        nuvem(v, l.clone().add(0, 0.1, 0), PRETO, 3.0f, 60);
    }

    void sombra(Location origem, Location destino) {
        List<Player> v = plateia(destino);
        if (v.isEmpty()) return;
        part(v, Particle.SQUID_INK, origem, 25, 0.3, 0.6, 0.3, 0.03, null);
        part(v, Particle.REVERSE_PORTAL, origem, 40, 0.3, 0.7, 0.3, 0.05, null);
    }

    void corteX(Location centro, Vector frente) {
        List<Player> v = plateia(centro);
        if (v.isEmpty()) return;
        part(v, Particle.CRIT, centro, 10, 0.2, 0.2, 0.2, 0.3, null);
    }

    void relampagoCorte(Location a, Location b) {
        List<Player> v = plateia(b);
        if (v.isEmpty()) return;
        linha(v, a, b, 0.6, Particle.END_ROD, null, 1, 0.0);
    }

    // =============================================================== DRUIDA

    void cura(Player p) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        Location l = p.getLocation();
        espiral(v, l, 0.9, 2.4, 2.5, 50, 12, Particle.HAPPY_VILLAGER, null, 2);
        part(v, Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.25, null);
        part(v, Particle.CHERRY_LEAVES, l.clone().add(0, 2.2, 0), 20, 0.8, 0.3, 0.8, 0, null);
    }

    void raizes(Player dono, Location c, List<LivingEntity> presos) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        presasEmAnel(v, c, dono, 1.5, 6, 0);
        presasEmAnel(v, c, dono, 3.0, 10, 4);
        for (LivingEntity le : presos) {
            Location pe = le.getLocation();
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i;
                presa(v, chao(pe.clone().add(Math.cos(a) * 0.8, 0, Math.sin(a) * 0.8)), dono, 2 + i);
            }
        }
    }

    void loboChega(Location l) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        linha(v, l, l.clone().add(0, 3, 0), 0.2, Particle.SOUL_FIRE_FLAME, null, 1, 0.1);
        fogos(v, l.clone().add(0, 1, 0), FireworkEffect.Type.STAR, false, CIANO, BRANCO);
    }

    void bencao(Location l) {
        List<Player> v = plateia(l);
        if (v.isEmpty()) return;
        fogos(v, l.clone().add(0, 2.5, 0), FireworkEffect.Type.BALL_LARGE, true, VERDE, LIMA, ROSA);
        nuvem(v, l.clone().add(0, 0.1, 0), Color.fromRGB(120, 255, 140), 8f, 60);
        part(v, Particle.CHERRY_LEAVES, l.clone().add(0, 3, 0), 60, 4, 0.5, 4, 0, null);
        espiral(v, l, 1.5, 5, 3, 80, 16, Particle.TOTEM_OF_UNDYING, null, 3);
    }

    void espinhos(Player dono, Location c, int onda) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        presasEmAnel(v, c, dono, 2.0 + onda * 1.6, 10 + onda * 5, 0);
        presasEmAnel(v, c, dono, 3.6 + onda * 1.6, 14 + onda * 5, 3);
    }

    // =============================================================== TRIDENTE

    void onda(Location base, Vector frente) {
        List<Player> v = plateia(base);
        if (v.isEmpty()) return;
        Vector f = frente.clone().setY(0);
        if (f.lengthSquared() < 0.01) f = new Vector(0, 0, 1);
        f.normalize();
        Vector lado = f.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        for (int dist = 1; dist <= 7; dist++) {
            final int d = dist;
            final Vector ff = f;
            depois(d, () -> {
                for (int s = -2; s <= 2; s++) {
                    Location pt = chao(base.clone().add(ff.clone().multiply(d)).add(lado.clone().multiply(s * 0.9)));
                    bloco(v, pt.clone().add(0, 0.05, 0), b((d + s) % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLUE_STAINED_GLASS),
                            new Vector(ff.getX() * 0.15, 0.42, ff.getZ() * 0.15), true, 22);
                }
            });
        }
    }

    void redemoinho(Location c, int ticks) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        // gotas (blocos de vidro azul de verdade) girando no redemoinho, como as do PC
        List<FallingBlock> gotas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            FallingBlock fb = bloco(v, c.clone(), b(i % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLUE_STAINED_GLASS), null, false, ticks + 4);
            if (fb != null) gotas.add(fb);
        }
        Particle.DustOptions azul = cor(40, 140, 255, 1.4f);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (t[0] > ticks) {
                tarefa.cancel();
                for (FallingBlock g : gotas) vis().remover(g);
                return;
            }
            for (int i = 0; i < gotas.size(); i++) {
                double a = t[0] * 0.45 + Math.PI * 2 * i / gotas.size();
                double h = ((t[0] * 0.08 + i * 0.5) % 3.0);
                double r = 2.2 - h * 0.5;
                levar(gotas.get(i), c.clone().add(Math.cos(a) * r, h - 0.3, Math.sin(a) * r));
            }
            if (t[0] % 2 == 0) {
                double a = t[0] * 0.5;
                part(v, Particle.DUST, c.clone().add(Math.cos(a) * 1.5, 0.5, Math.sin(a) * 1.5), 2, 0.05, 0.05, 0.05, 0, azul);
                part(v, Particle.SPLASH, c.clone().add(0, 1, 0), 4, 1.2, 0.6, 1.2, 0.05, null);
            }
            t[0]++;
        }, 1L, 1L);
    }

    void poseidon(Location onde) {
        List<Player> v = plateia(onde);
        if (v.isEmpty()) return;
        fogos(v, onde.clone().add(0, 2, 0), FireworkEffect.Type.BALL_LARGE, true, AGUA, CIANO, BRANCO);
        depois(6, () -> fogos(v, onde.clone().add(0, 4, 0), FireworkEffect.Type.STAR, true, AGUA, BRANCO));
        for (int anel = 0; anel < 2; anel++) {
            final double r = 2.5 + anel * 2.5;
            depois(2L + anel * 4L, () -> {
                int n = (int) (r * 4);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    Location pe = chao(onde.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                    bloco(v, pe.clone().add(0, 0.05, 0), b(i % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.PRISMARINE),
                            new Vector(Math.cos(a) * 0.12, 0.5, Math.sin(a) * 0.12), true, 26);
                }
            });
        }
    }

    // =============================================================== ARQUEIRO

    void explosaoFlecha(Location pos) {
        List<Player> v = plateia(pos);
        if (v.isEmpty()) return;
        fogos(v, pos, FireworkEffect.Type.BURST, false, LARANJA, AMARELO, VERMELHO);
    }

    // =============================================================== LUTADOR

    /** Um soco de energia: bloco que voa da mao ate a ponta e estoura (o ultimo da rajada solta fogos e um anel). */
    void lutSoco(Location mao, Location ponta, boolean grande) {
        List<Player> v = plateia(ponta);
        if (v.isEmpty()) return;
        Vector d = ponta.toVector().subtract(mao.toVector());
        FallingBlock fb = bloco(v, mao.clone().subtract(0, 0.5, 0), b(grande ? Material.SHROOMLIGHT : Material.ORANGE_STAINED_GLASS),
                d.clone().multiply(0.5), false, 5);
        linha(v, mao, ponta, 0.3, Particle.DUST, grande ? cor(255, 140, 30, 1.5f) : cor(255, 210, 80, 1.0f), 1, 0.03);
        depois(2, () -> {
            if (fb != null) vis().remover(fb);
            part(v, Particle.ELECTRIC_SPARK, ponta, grande ? 14 : 5, 0.15, 0.15, 0.15, 0.25, null);
            if (grande) {
                fogos(v, ponta, FireworkEffect.Type.BURST, false, LARANJA, AMARELO, BRANCO);
                anelEixo(v, ponta, d, 1.0, 14, LARANJA);
                depois(2, () -> anelEixo(v, ponta, d, 1.7, 20, AMARELO));
            }
        });
    }

    /** Onda de choque de pe (anel em volta do eixo 'dir'). */
    void lutAnel(Location c, Vector dir, double raio, Color cor) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        anelEixo(v, c, dir, raio * 0.6, 14, cor);
        depois(2, () -> anelEixo(v, c, dir, raio, 20, cor));
    }

    private static void anelEixo(List<Player> v, Location c, Vector dir, double r, int pontos, Color cor) {
        Vector u = Lutador.lado(dir), w = u.clone().crossProduct(dir).normalize();
        Particle.DustOptions d = new Particle.DustOptions(cor, 1.5f);
        for (int i = 0; i < pontos; i++) {
            double a = Math.PI * 2 * i / pontos;
            Location pt = c.clone().add(u.clone().multiply(Math.cos(a) * r)).add(w.clone().multiply(Math.sin(a) * r));
            part(v, Particle.DUST, pt, 1, 0, 0, 0, 0, d);
        }
    }

    /** Gancho do Dragao: blocos de fogo girando em volta do jogador enquanto ele sobe, e a cabeca do dragao estoura. */
    void lutGancho(Player p) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        part(v, Particle.GUST_EMITTER_SMALL, p.getLocation().add(0, 0.3, 0), 1, 0, 0, 0, 0, null);
        BlockData[] tipos = {b(Material.ORANGE_STAINED_GLASS), b(Material.SHROOMLIGHT), b(Material.RED_STAINED_GLASS)};
        List<FallingBlock> bs = new ArrayList<>();
        for (int i = 0; i < tipos.length; i++) {
            double a = Math.PI * 2 * i / tipos.length;
            FallingBlock fb = bloco(v, p.getLocation().add(Math.cos(a) * 1.1, i * 0.45, Math.sin(a) * 1.1), tipos[i], null, false, 20);
            if (fb != null) bs.add(fb);
        }
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            t[0]++;
            if (!p.isOnline() || t[0] > 14) {
                tarefa.cancel();
                for (FallingBlock fb : bs) vis().remover(fb);
                return;
            }
            Location c = p.getLocation();
            for (int i = 0; i < bs.size(); i++) {
                double a = t[0] * 0.85 + Math.PI * 2 * i / bs.size();
                levar(bs.get(i), c.clone().add(Math.cos(a) * 1.15, i * 0.45 - 0.2, Math.sin(a) * 1.15));
            }
            double a = t[0] * 0.85;
            for (int k = 0; k < 2; k++) {
                double ak = a + Math.PI * k;
                part(v, Particle.FLAME, c.clone().add(Math.cos(ak) * 0.9, 0.2 + (t[0] % 7) * 0.28, Math.sin(ak) * 0.9), 2, 0.05, 0.05, 0.05, 0.01, null);
            }
            if (t[0] == 9) {
                fogos(v, c.clone().add(0, 2.2, 0), FireworkEffect.Type.BALL, true, VERMELHO, LARANJA, AMARELO);
                part(v, Particle.LAVA, c.clone().add(0, 2, 0), 8, 0.5, 0.4, 0.5, 0, null);
            }
        }, 1L, 1L);
    }

    void lutGanchoAcerto(Location em) {
        List<Player> v = plateia(em);
        if (v.isEmpty()) return;
        fogos(v, em, FireworkEffect.Type.BURST, false, LARANJA, VERMELHO, AMARELO);
    }

    /** Soco Sismico: a terra pula em cada passo da rachadura. */
    void lutSismico(Location g, BlockData terra) {
        List<Player> v = plateia(g);
        if (v.isEmpty()) return;
        part(v, Particle.DUST_PILLAR, g.clone().add(0, 0.2, 0), 10, 0.6, 0.1, 0.6, 0, terra);
        erupcao(v, g.clone().add(0, 0.1, 0), new BlockData[] {terra}, 2, 0.12, 0.55, 16);
    }

    private final Map<UUID, List<Entity>> furiaCel = new HashMap<>();

    /** Punhos em Chamas: explosao de fogo em volta e uma bola de fogo seguindo cada mao. */
    void lutFuria(Player p, int dur) {
        List<Player> v = plateia(p.getLocation());
        if (v.isEmpty()) return;
        lutFuriaFim(p.getUniqueId());
        fogos(v, p.getLocation().add(0, 1.2, 0), FireworkEffect.Type.BALL_LARGE, true, VERMELHO, LARANJA, AMARELO);
        part(v, Particle.LAVA, p.getLocation().add(0, 1, 0), 12, 0.6, 0.6, 0.6, 0, null);
        List<Entity> maos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Item it = item(v, Lutador.mao(p, i == 0), new ItemStack(Material.FIRE_CHARGE), null, false, dur + 6);
            if (it != null) maos.add(it);
        }
        UUID id = p.getUniqueId();
        furiaCel.put(id, maos);
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            t[0]++;
            if (furiaCel.get(id) != maos || !p.isOnline() || t[0] > dur) {
                tarefa.cancel();
                for (Entity e : maos) vis().remover(e);
                if (furiaCel.get(id) == maos) furiaCel.remove(id);
                return;
            }
            for (int i = 0; i < maos.size(); i++) levar(maos.get(i), Lutador.mao(p, i == 0).subtract(0, 0.2, 0));
            if (t[0] % 20 == 0) circulo(v, p.getLocation().add(0, 0.15, 0), 0.9, 10, Particle.FLAME, null);
        }, 1L, 1L);
    }

    void lutFuriaFim(UUID id) {
        List<Entity> maos = furiaCel.remove(id);
        if (maos != null) for (Entity e : maos) vis().remover(e);
    }

    void lutFuriaSoco(Location c) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        fogos(v, c, FireworkEffect.Type.BURST, false, VERMELHO, LARANJA);
        part(v, Particle.FLAME, c, 10, 0.4, 0.4, 0.4, 0.06, null);
    }

    private final Map<UUID, FallingBlock> cargaCel = new HashMap<>();

    /** Punho Meteoro carregando: bloco brilhante na mao e energia sendo puxada (bolinha que encolhe). */
    void lutMeteoroCarga(Player p, Location mao, int t) {
        UUID id = p.getUniqueId();
        List<Player> v = plateia(mao);
        FallingBlock fb = cargaCel.get(id);
        if (t >= 30 || v.isEmpty()) {
            if (fb != null) vis().remover(fb);
            cargaCel.remove(id);
            if (v.isEmpty()) return;
        } else if (fb == null || !fb.isValid()) {
            fb = bloco(v, mao.clone().subtract(0, 0.5, 0), b(Material.SHROOMLIGHT), null, false, 40);
            if (fb != null) cargaCel.put(id, fb);
        } else levar(fb, mao.clone().subtract(0, 0.5, 0), 2);
        int g = Math.min(255, 140 + t * 3);
        esfera(v, mao, Math.max(0.45, 1.7 - t * 0.04), 14, Particle.DUST, cor(255, g, 40, 1.4f));
        if (t % 8 == 0) part(v, Particle.DUST_PILLAR, p.getLocation().add(0, 0.1, 0), 14, 1.4, 0.05, 1.4, 0, Poderes.chaoDe(p.getLocation()));
        if (t >= 28) fogos(v, mao, FireworkEffect.Type.BALL, true, LARANJA, AMARELO, BRANCO);
    }

    /** O punho gigante do celular: 8 blocos montados na forma de um punho, andando juntos. */
    static final class PunhoCel {
        final List<FallingBlock> blocos = new ArrayList<>();
        final List<Vector> offs = new ArrayList<>();
        List<Player> v;
        int passos;
    }

    private static final double[][] PUNHO_CEL = {
            {0.5, 0.5, 0.8}, {-0.5, 0.5, 0.8}, {0.5, -0.5, 0.8}, {-0.5, -0.5, 0.8},   // dedos (lado direito, cima, frente)
            {0.5, 0, -0.3}, {-0.5, 0, -0.3},                                         // mao
            {-1.0, -0.4, 0.2},                                                        // dedao
            {0, 0, -1.4},                                                             // pulso
    };

    Object lutMeteoroPunho(Location inicio, Vector dir) {
        List<Player> v = plateia(inicio);
        if (v.isEmpty()) return null;
        PunhoCel pc = new PunhoCel();
        pc.v = v;
        Vector f = dir.clone().normalize(), dirt = Lutador.lado(f), cima = dirt.clone().crossProduct(f).normalize();
        for (int i = 0; i < PUNHO_CEL.length; i++) {
            double[] o = PUNHO_CEL[i];
            Vector off = dirt.clone().multiply(o[0]).add(cima.clone().multiply(o[1])).add(f.clone().multiply(o[2]));
            Material m = i < 4 ? Material.ORANGE_STAINED_GLASS : i < 6 ? Material.SHROOMLIGHT : Material.YELLOW_STAINED_GLASS;
            FallingBlock fb = bloco(v, inicio.clone().add(off).subtract(0, 0.5, 0), b(m), null, false, 60);
            if (fb == null) continue;
            pc.blocos.add(fb);
            pc.offs.add(off);
        }
        fogos(v, inicio, FireworkEffect.Type.BALL, true, LARANJA, AMARELO);
        return pc;
    }

    void lutMeteoroMover(Object o, Location pos) {
        if (!(o instanceof PunhoCel pc)) return;
        for (int i = 0; i < pc.blocos.size(); i++) levar(pc.blocos.get(i), pos.clone().add(pc.offs.get(i)).subtract(0, 0.5, 0));
        pc.passos++;
        part(pc.v, Particle.END_ROD, pos, 3, 0.6, 0.6, 0.6, 0.02, null);
        if (pc.passos % 3 == 0) fogos(pc.v, pos, FireworkEffect.Type.BURST, false, LARANJA, AMARELO);
    }

    void lutMeteoroFim(Object o, Location pos) {
        if (!(o instanceof PunhoCel pc)) return;
        for (FallingBlock fb : pc.blocos) vis().remover(fb);
        pc.blocos.clear();
    }

    void lutMeteoroImpacto(Location c) {
        List<Player> v = plateia(c);
        if (v.isEmpty()) return;
        fogos(v, c, FireworkEffect.Type.BALL_LARGE, true, LARANJA, AMARELO, BRANCO);
        depois(3, () -> fogos(v, c.clone().add(0, 1, 0), FireworkEffect.Type.BURST, true, VERMELHO, LARANJA));
        explosaoDeBlocos(v, c, b(Material.ORANGE_STAINED_GLASS), 12, 4.0, 6);
        part(v, Particle.DUST_PILLAR, c.clone().add(0, 0.2, 0), 40, 3.0, 0.2, 3.0, 0, Poderes.chaoDe(c));
    }
}
