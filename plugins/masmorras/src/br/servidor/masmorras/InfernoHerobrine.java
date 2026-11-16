package br.servidor.masmorras;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 14/11 (Paulo: "faca a luta do Herobrine ser mais intensa, mas nao precisa deixar ele mais forte; ate se quiser,
 * ele teleportar para um lugar parecido com outra dimensao, como o mundo dele no inferno").
 *
 * O MUNDO DE HEROBRINE: quando ele chega a 2/3 da vida, escurece, ele fala "venham para o meu mundo" e puxa todo
 * mundo da arena (e ele junto) para uma ilha de pedra negra flutuando num inferno vermelho: lava caindo do ceu,
 * pilares com fogo azul e vermelho, raios, chuva de fogo, vultos dele aparecendo e sumindo e colunas de fogo que
 * nascem embaixo dos pes (avisadas antes por um circulo vermelho; jogam para o alto, sem dano). Dura 45 s ou ate
 * ele cair para 30% da vida, e ai todos voltam para a arena. Ele NAO fica mais forte: o perigo e so visual e de
 * empurrao; o dano continua o dele de sempre. Ele nao morre la (a vida trava em 30% e todos voltam), entao o premio
 * continua sendo dado na arena como antes.
 */
final class InfernoHerobrine implements Listener {
    static final String MUNDO = "herobrine_inferno";
    static final int Y = 100, R = 16;

    private final Masmorras pl;
    private final Random rnd = new Random();
    private World w;

    // fase em andamento
    private Chefe chefe;
    private long fim, proxPilar, proxChuva, proxRaio, proxVulto, proxFala;
    private double limite;
    private boolean saindo;
    private final Map<UUID, Location> volta = new HashMap<>();
    /** Onde cada um estava na ULTIMA fase que acabou: quem estava morto na hora da volta renasce la, nao no lobby. */
    private final Map<UUID, Location> voltaAntiga = new HashMap<>();
    /** O Herobrine que aparece no mundo dele: um corpo (invisivel) + o boneco com a cara dele. O chefe de verdade
     *  fica parado e escondido na arena; todo golpe no corpo vai para ele (mesma vida, mesmo premio, mesma forca). */
    private LivingEntity corpo;
    private org.bukkit.entity.Mannequin boneco;
    private org.bukkit.scheduler.BukkitTask tarefa, seguir;

    InfernoHerobrine(Masmorras pl) {
        this.pl = pl;
    }

    boolean ativo(Chefe c) {
        return chefe != null && chefe == c;
    }

    // ------------------------------------------------------------------ o mundo
    static final class Gerador extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
            return new BiomeProvider() {   // floresta carmesim: neblina vermelha e esporos no ar (inferno)
                @Override public Biome getBiome(WorldInfo i, int x, int y, int z) { return Biome.CRIMSON_FOREST; }
                @Override public List<Biome> getBiomes(WorldInfo i) { return List.of(Biome.CRIMSON_FOREST); }
            };
        }
    }

    @SuppressWarnings("removal")
    World mundo() {
        if (w != null) return w;
        w = Bukkit.getWorld(MUNDO);
        if (w == null) w = new WorldCreator(MUNDO).environment(World.Environment.NETHER).generator(new Gerador()).generateStructures(false).createWorld();
        if (w == null) return null;
        try {
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.DO_FIRE_TICK, false);
            w.setGameRule(GameRule.MOB_GRIEFING, false);
            w.setGameRule(GameRule.KEEP_INVENTORY, true);
        } catch (Throwable ignored) {
        }
        w.setSpawnLocation(0, Y + 1, 0);
        File flag = new File(pl.getDataFolder(), "inferno-herobrine-v1.txt");
        if (!flag.exists()) {
            construir(w);
            try { flag.createNewFile(); } catch (Exception ignored) { }
        }
        return w;
    }

    private void bloco(World w, int x, int y, int z, String b) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false);
    }

    private void construir(World w) {
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) w.getChunkAt(cx, cz).load(true);
        Random r = new Random(666);
        String[] piso = {"minecraft:netherrack", "minecraft:netherrack", "minecraft:blackstone", "minecraft:polished_blackstone",
                "minecraft:crimson_nylium", "minecraft:nether_wart_block", "minecraft:soul_soil", "minecraft:blackstone"};
        for (int x = -R - 2; x <= R + 2; x++)
            for (int z = -R - 2; z <= R + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= R) {
                    String b = d < 3.2 ? "minecraft:crying_obsidian" : d < 4.2 ? "minecraft:gilded_blackstone"
                            : d > R - 1 ? "minecraft:polished_blackstone_bricks" : r.nextInt(23) == 0 ? "minecraft:shroomlight" : piso[r.nextInt(piso.length)];
                    bloco(w, x, Y, z, b);
                    // ilha flutuando: a pedra por baixo vai afinando
                    int fundo = (int) Math.round((R - d) * 0.9 + r.nextInt(3));
                    for (int k = 1; k <= fundo; k++) bloco(w, x, Y - k, z, k > fundo - 2 && r.nextBoolean() ? "minecraft:magma_block" : "minecraft:netherrack");
                }
                if (d > R && d <= R + 1.2) for (int k = 1; k <= 9; k++) bloco(w, x, Y + k, z, "minecraft:barrier");   // ninguem cai
                if (d > R && d <= R + 1.2) bloco(w, x, Y + 1, z, r.nextInt(3) == 0 ? "minecraft:blackstone_wall" : "minecraft:air");
            }
        // pilares com fogo (vermelho e azul alternando)
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 13), z = (int) Math.round(Math.sin(a) * 13), h = 6 + r.nextInt(4);
            for (int k = 1; k <= h; k++) bloco(w, x, Y + k, z, k == h - 1 ? "minecraft:crying_obsidian" : "minecraft:obsidian");
            boolean azul = i % 2 == 0;
            bloco(w, x, Y + h + 1, z, azul ? "minecraft:soul_soil" : "minecraft:netherrack");
            bloco(w, x, Y + h + 2, z, azul ? "minecraft:soul_fire" : "minecraft:fire");
        }
        // trono de Herobrine (norte)
        for (int x = -2; x <= 2; x++) for (int k = 1; k <= 2; k++) bloco(w, x, Y + k - 1 + 1, -R + 2 - (k - 1), k == 1 ? "minecraft:polished_blackstone_stairs[facing=north]" : "minecraft:obsidian");
        for (int x = -2; x <= 2; x++) for (int k = 3; k <= 6; k++) bloco(w, x, Y + k, -R + 1, (x == 0 && k == 6) ? "minecraft:redstone_block" : "minecraft:crying_obsidian");
        // cachoeiras de lava caindo do ceu (fora da ilha)
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.3;
            int x = (int) Math.round(Math.cos(a) * (R + 6)), z = (int) Math.round(Math.sin(a) * (R + 6));
            bloco(w, x, Y + 18, z, "minecraft:obsidian");
            bloco(w, x, Y + 17, z, "minecraft:lava");
        }
        // pedacos de pedra flutuando em volta
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2, raio = R + 8 + r.nextInt(16);
            int x = (int) (Math.cos(a) * raio), z = (int) (Math.sin(a) * raio), y = Y - 10 + r.nextInt(30), t = 1 + r.nextInt(3);
            for (int dx = -t; dx <= t; dx++) for (int dy = -t; dy <= 0; dy++) for (int dz = -t; dz <= t; dz++)
                if (dx * dx + dy * dy * 2 + dz * dz <= t * t + 1) bloco(w, x + dx, y + dy, z + dz, r.nextInt(5) == 0 ? "minecraft:crying_obsidian" : "minecraft:netherrack");
        }
        pl.getLogger().info("Mundo de Herobrine construido (" + MUNDO + ").");
    }

    // ------------------------------------------------------------------ a fase
    /** Chamado pelo Chefe a cada meio segundo enquanto a luta e do Herobrine. Devolve true se a fase esta ativa. */
    boolean conferir(Chefe c, List<Player> ps) {
        if (chefe != null) return chefe == c;
        if (!c.vivo()) return false;
        if (Boolean.getBoolean("inferno.debug")) pl.getLogger().info("[debug inferno] feito=" + c.infernoFeito + " ps=" + ps.size() + " vida=" + c.e.getHealth() + "/" + c.vidaMax);
        if (c.infernoFeito || ps.isEmpty() || c.e.getHealth() > c.vidaMax * 0.66) return false;
        if (!pl.getConfig().getBoolean("herobrine.inferno", true)) return false;
        c.infernoFeito = true;
        comecar(c, ps);
        return true;
    }

    private static Title titulo(String a, String b, int seg) {
        return Title.title(Masmorras.txt(a), Masmorras.txt(b), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(seg), Duration.ofMillis(600)));
    }

    private void comecar(Chefe c, List<Player> ps) {
        World inf = mundo();
        if (inf == null) return;
        pl.getLogger().info("Mundo de Herobrine: abrindo para " + ps.size() + " jogador(es).");
        chefe = c;
        saindo = false;
        limite = c.vidaMax * 0.30;
        volta.clear();
        LivingEntity e = c.e;
        e.setInvulnerable(true);
        World aw = e.getWorld();
        // 15/11: a ARENA fica carregada enquanto todos estao no mundo dele. Sem isso o pedaco do mapa descarregava, o
        // Herobrine de verdade (que fica la, parado) sumia junto, a fase acabava sozinha e ele voltava sem tomar dano.
        soltarArena();
        Caixa ar = c.m.arena();
        arenaMundo = aw;
        for (int cx = (ar.x1() >> 4) - 1; cx <= (ar.x2() >> 4) + 1; cx++)
            for (int cz = (ar.z1() >> 4) - 1; cz <= (ar.z2() >> 4) + 1; cz++) {
                aw.getChunkAt(cx, cz).addPluginChunkTicket(pl);
                arenaChunks.add(new int[] {cx, cz});
            }
        for (Player p : ps) {
            p.showTitle(titulo("§4§lVENHAM PARA O MEU MUNDO", "§7§o...ninguém sai de lá do mesmo jeito", 3));
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 4, 0, false, false));
            p.playSound(p, "minecraft:entity.wither.spawn", 1f, 0.5f);
            p.playSound(p, "minecraft:block.portal.trigger", 0.8f, 0.6f);
        }
        // 3 s de "rasgando o mundo": raios em volta, portal subindo nos pes de todo mundo
        for (int k = 0; k < 6; k++) {
            int kk = k;
            pl.depois(k * 10L, () -> {
                if (!c.vivo()) return;
                Location l = e.getLocation();
                aw.strikeLightningEffect(l.clone().add(rnd.nextInt(13) - 6, 0, rnd.nextInt(13) - 6));
                for (Player p : ps) if (p.isOnline() && p.getWorld().equals(aw)) {
                    p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60 + kk * 20, 0.6, 1, 0.6, 0.6);
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.2, 0), 20, 0.8, 0.05, 0.8, 0, new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.5f));
                }
            });
        }
        pl.depois(62L, () -> {
            if (!c.vivo()) { encerrarSemVolta(); return; }
            List<Player> agora = c.naArena();
            pl.getLogger().info("Mundo de Herobrine: levando " + (agora.isEmpty() ? ps.size() : agora.size()) + " jogador(es).");
            if (agora.isEmpty()) agora = ps;
            int n = 0;
            for (Player p : agora) {
                if (!p.isOnline() || p.isDead()) continue;
                volta.put(p.getUniqueId(), p.getLocation().clone());
                double a = Math.PI / 2 + n++ * (Math.PI * 2 / Math.max(1, agora.size()));
                Location dest = new Location(inf, Math.cos(a) * 8 + 0.5, Y + 1, Math.sin(a) * 8 + 0.5);
                dest.setDirection(new Vector(-Math.cos(a), 0, -Math.sin(a)));
                p.teleport(dest);
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 4, 0, false, false));
                p.showTitle(titulo("§4§lO MUNDO DE HEROBRINE", "§cA luta termina aqui: derrote-o", 3));
                p.playSound(p, "minecraft:entity.ghast.scream", 1f, 0.5f);
                p.playSound(p, "minecraft:ambient.crimson_forest.mood", 1f, 0.8f);
            }
            criarCorpo(c, new Location(inf, 0.5, Y + 1, -R + 4.5, 0, 0));
            e.setInvulnerable(false);
            if (e instanceof Mob mob) { mob.setAI(false); mob.setTarget(null); }
            c.sumidoAte = Long.MAX_VALUE;   // o boneco da arena some (ele esta no mundo dele)
            if (tarefa != null) tarefa.cancel();
            tarefa = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 10L, 10L);
            long agoraMs = System.currentTimeMillis();
            fim = Long.MAX_VALUE;   // sem tempo: a luta termina aqui
            proxPilar = agoraMs + 3000; proxChuva = agoraMs + 6000; proxRaio = agoraMs + 1500; proxVulto = agoraMs + 5000; proxFala = agoraMs + 9000;
        });
    }

    private void criarCorpo(Chefe c, Location l) {
        World inf = l.getWorld();
        // a ilha inteira fica carregada durante a fase (senao o corpo dele some com o pedaco do mapa)
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) inf.getChunkAt(cx, cz).addPluginChunkTicket(pl);
        corpo = inf.spawn(l, org.bukkit.entity.Zombie.class, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, z -> {
            z.setAdult();
            z.setPersistent(false);
            z.setSilent(true);
            z.setCanPickupItems(false);
            z.setShouldBurnInDay(false);
            z.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(pl, "inferno_corpo"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
            if (z.getEquipment() != null) z.getEquipment().clear();
            copiar(c.e, z, org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            copiar(c.e, z, org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            copiar(c.e, z, org.bukkit.attribute.Attribute.ARMOR);
            var mh = z.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (mh != null) mh.setBaseValue(1000);
            z.setHealth(1000);
            z.customName(Masmorras.txt("§4§lHerobrine"));
        });
        try {
            boneco = pl.bonecoHerobrine(c.m, l);
        } catch (Throwable ex) {
            boneco = null;
            corpo.removePotionEffect(PotionEffectType.INVISIBILITY);   // sem boneco: o corpo aparece
        }
        if (seguir != null) seguir.cancel();
        seguir = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (boneco != null && boneco.isValid() && corpo != null && corpo.isValid()) boneco.teleport(corpo.getLocation());
        }, 1L, 1L);
    }

    private static void copiar(LivingEntity de, LivingEntity para, org.bukkit.attribute.Attribute a) {
        var x = de.getAttribute(a);
        var y = para.getAttribute(a);
        if (x != null && y != null) y.setBaseValue(x.getValue());
    }

    private boolean avisouSumico;
    private World arenaMundo;
    private final List<int[]> arenaChunks = new ArrayList<>();

    private void soltarArena() {
        if (arenaMundo != null) for (int[] ch : arenaChunks) arenaMundo.removePluginChunkTicket(ch[0], ch[1], pl);
        arenaChunks.clear();
        arenaMundo = null;
    }

    private void tirarCorpo() {
        if (seguir != null) { seguir.cancel(); seguir = null; }
        if (tarefa != null) { tarefa.cancel(); tarefa = null; }
        for (org.bukkit.entity.Entity x : new org.bukkit.entity.Entity[] {corpo, boneco}) if (x != null && x.isValid()) {
            x.getWorld().spawnParticle(Particle.LARGE_SMOKE, x.getLocation().add(0, 1, 0), 30, 0.3, 0.8, 0.3, 0.02);
            x.remove();
        }
        corpo = null;
        boneco = null;
        if (w != null) for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) w.getChunkAt(cx, cz).removePluginChunkTicket(pl);
    }

    /** Tique da fase (a cada meio segundo). */
    void tick() {
        Chefe c = chefe;
        if (c == null || saindo) return;
        // so "caiu" se a vida zerou de verdade (entidade descarregada com o pedaco do mapa nao e morte)
        if (!c.vivo() && c.e != null && c.e.getHealth() > 0) {
            if (!avisouSumico) pl.getLogger().warning("Mundo de Herobrine: o Herobrine da arena ficou invalido sem morrer (vida " + c.e.getHealth() + ").");
            avisouSumico = true;
            return;
        }
        avisouSumico = false;
        if (!c.vivo()) {
            if (w != null) for (Player p : w.getPlayers()) {
                p.showTitle(titulo("§a§lHEROBRINE CAIU", "§7O mundo dele se desfaz...", 3));
                p.playSound(p, "minecraft:entity.wither.death", 0.8f, 0.7f);
            }
            saindo = true;   // para o tique; em 3 s todos voltam para a arena (o premio esta la)
            tirarCorpo();
            pl.depois(60L, () -> { saindo = false; encerrarSemVolta(); });
            return;
        }
        World inf = w;
        if (inf == null || corpo == null) return;   // ainda na abertura
        if (!corpo.isValid()) {
            pl.getLogger().info("Mundo de Herobrine: o corpo sumiu, voltando.");
            voltar();
            return;
        }
        long agora = System.currentTimeMillis();
        List<Player> ps = new ArrayList<>();
        for (Player p : inf.getPlayers()) if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE) ps.add(p);
        // barra de vida para quem esta la
        c.barra.progress((float) Math.max(0, Math.min(1, c.e.getHealth() / c.vidaMax)));
        for (Player p : ps) if (!c.vendo.contains(p)) { p.showBossBar(c.barra); c.vendo.add(p); }
        // quem morreu e ainda nao renasceu continua contando: ele renasce na ilha e a luta segue (antes a fase acabava na hora)
        boolean alguemMorto = false;
        for (Player p : inf.getPlayers()) if (p.isDead() && volta.containsKey(p.getUniqueId())) alguemMorto = true;
        if ((ps.isEmpty() && !alguemMorto) || agora >= fim) {
            pl.getLogger().info("Mundo de Herobrine: fim da fase (" + (ps.isEmpty() ? "ninguem la" : "tempo") + ").");
            voltar();
            return;
        }
        if (ps.isEmpty()) return;
        // ele persegue o mais perto
        Player alvo = null;
        double melhor = Double.MAX_VALUE;
        for (Player p : ps) {
            double d = p.getLocation().distanceSquared(corpo.getLocation());
            if (d < melhor) { melhor = d; alvo = p; }
        }
        if (corpo instanceof Mob mob && (mob.getTarget() == null || !ps.contains(mob.getTarget()))) mob.setTarget(alvo);
        if (corpo.getLocation().getY() < Y - 3 || corpo.getLocation().toVector().setY(0).length() > R) corpo.teleport(new Location(inf, 0.5, Y + 1, 0.5));
        if (corpo.getHealth() < 900) corpo.setHealth(1000);
        // ambiente: esporos, cinzas e brasas no ar
        for (Player p : ps) {
            inf.spawnParticle(Particle.CRIMSON_SPORE, p.getLocation().add(0, 2, 0), 12, 6, 3, 6, 0);
            inf.spawnParticle(Particle.LAVA, p.getLocation().add(rnd.nextInt(17) - 8, 0.2, rnd.nextInt(17) - 8), 1, 0, 0, 0, 0);
        }
        if (agora >= proxRaio) {
            proxRaio = agora + 1800 + rnd.nextInt(2200);
            double a = rnd.nextDouble() * Math.PI * 2;
            inf.strikeLightningEffect(new Location(inf, Math.cos(a) * (R + 3), Y + 1, Math.sin(a) * (R + 3)));
        }
        if (agora >= proxPilar) { proxPilar = agora + 3500 + rnd.nextInt(1500); pilares(ps); }
        if (agora >= proxChuva) { proxChuva = agora + 8000; chuva(ps); }
        if (agora >= proxVulto) { proxVulto = agora + 7000 + rnd.nextInt(3000); vultos(ps); }
        if (agora >= proxFala) {
            proxFala = agora + 10_000;
            String[] falas = {"§4Herobrine: §7Aqui eu sou eterno.", "§4Herobrine: §7Vocês sentem o calor? É o meu mundo.",
                    "§4Herobrine: §7Ninguém nunca voltou daqui...", "§4Herobrine: §7Olhem para cima."};
            String f = falas[rnd.nextInt(falas.length)];
            for (Player p : ps) { p.sendActionBar(Masmorras.txt(f)); p.playSound(p, "minecraft:entity.warden.roar", 0.4f, 0.6f); }
        }
    }

    /** Colunas de fogo: circulo vermelho embaixo de cada um e, 1,5 s depois, a erupcao (joga para cima, sem dano). */
    private void pilares(List<Player> ps) {
        for (Player p : ps) {
            Location l = p.getLocation().clone();
            l.setY(Y + 1.05);
            for (int k = 0; k < 3; k++) {
                pl.depois(k * 10L, () -> {
                    for (int i = 0; i < 20; i++) {
                        double a = i * Math.PI / 10;
                        l.getWorld().spawnParticle(Particle.DUST, l.clone().add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(230, 20, 0), 1.2f));
                    }
                    l.getWorld().spawnParticle(Particle.SMALL_FLAME, l, 6, 0.6, 0.05, 0.6, 0.01);
                });
            }
            pl.depois(2L, () -> p.playSound(l, "minecraft:block.fire.ambient", 1.2f, 0.6f));
            pl.depois(30L, () -> {
                World x = l.getWorld();
                for (int k = 0; k < 9; k++) {
                    x.spawnParticle(Particle.FLAME, l.clone().add(0, k * 0.6, 0), 10, 0.35, 0.2, 0.35, 0.03);
                    if (k % 2 == 0) x.spawnParticle(Particle.LAVA, l.clone().add(0, k * 0.6, 0), 2, 0.3, 0.2, 0.3, 0);
                }
                x.spawnParticle(Particle.EXPLOSION, l.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                x.playSound(l, "minecraft:entity.blaze.shoot", 1.2f, 0.5f);
                x.playSound(l, "minecraft:block.lava.extinguish", 1f, 0.7f);
                for (Player o : x.getPlayers())
                    if (o.getLocation().distanceSquared(l) < 1.9 * 1.9) {
                        o.setVelocity(new Vector((rnd.nextDouble() - 0.5) * 0.4, 1.05, (rnd.nextDouble() - 0.5) * 0.4));
                        o.sendActionBar(Masmorras.txt("§6A coluna de fogo te jogou para o alto!"));
                    }
            });
        }
    }

    /** Chuva de fogo: brasas caindo do ceu por toda a ilha (so visual) e o ceu trovejando. */
    private void chuva(List<Player> ps) {
        World inf = w;
        for (Player p : ps) p.playSound(p, "minecraft:item.firecharge.use", 0.8f, 0.5f);
        for (int k = 0; k < 8; k++) {
            pl.depois(k * 5L, () -> {
                for (int i = 0; i < 20; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * R;
                    Location l = new Location(inf, Math.cos(a) * d, Y + 9 + rnd.nextInt(5), Math.sin(a) * d);
                    inf.spawnParticle(Particle.FALLING_LAVA, l, 1, 0.2, 0.2, 0.2, 0);
                    inf.spawnParticle(Particle.FLAME, l, 1, 0.1, 0.1, 0.1, 0.02);
                }
            });
        }
    }

    /** Vultos: 3 copias dele aparecem na borda olhando para os jogadores e somem em fumaca. */
    private void vultos(List<Player> ps) {
        if (chefe == null) return;
        Masmorra m = chefe.m;
        for (int i = 0; i < 3; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            Location l = new Location(w, Math.cos(a) * (R - 2) + 0.5, Y + 1, Math.sin(a) * (R - 2) + 0.5);
            Player olha = ps.get(rnd.nextInt(ps.size()));
            l.setDirection(olha.getLocation().toVector().subtract(l.toVector()));
            try {
                org.bukkit.entity.Mannequin v = pl.bonecoHerobrine(m, l);
                v.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
                pl.depois(50L + i * 6, () -> {
                    if (!v.isValid()) return;
                    v.getWorld().spawnParticle(Particle.LARGE_SMOKE, v.getLocation().add(0, 1, 0), 25, 0.3, 0.7, 0.3, 0.02);
                    v.getWorld().playSound(v.getLocation(), "minecraft:entity.enderman.teleport", 0.8f, 0.5f);
                    v.remove();
                });
            } catch (Throwable ignored) {
            }
        }
        for (Player p : ps) p.playSound(p, "minecraft:ambient.cave", 1f, 0.5f);
    }

    /** Fim da fase: todos voltam para a arena (onde estavam) e ele volta para o meio da arena. */
    private void voltar() {
        Chefe c = chefe;
        if (c == null || saindo) return;
        saindo = true;
        World aw = Bukkit.getWorld(c.m.mundo);
        Caixa a = c.m.arena();
        Location meio = aw == null ? null : new Location(aw, a.cx() + 0.5, a.y1(), a.cz() + 0.5);
        List<Player> la = w == null ? List.of() : new ArrayList<>(w.getPlayers());
        for (Player p : la) {
            p.showTitle(titulo("§c§lVOCÊS ESCAPARAM", "§7...mas ele voltou junto. §4Fúria final!", 3));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false));
            p.playSound(p, "minecraft:block.portal.travel", 0.5f, 1.4f);
        }
        tirarCorpo();
        if (c.vivo()) {
            c.e.setInvulnerable(true);
            if (meio != null) c.e.teleport(meio);
            if (c.e instanceof Mob mob) mob.setAI(true);
            c.sumidoAte = 0;
        }
        pl.depois(8L, () -> {
          try {
            for (Player p : la) {
                if (!p.isOnline() || !p.getWorld().equals(w)) continue;
                Location dest = volta.getOrDefault(p.getUniqueId(), meio);
                if (dest == null || dest.getWorld() == null) { pl.irParaSpawnMasmorras(p); continue; }
                p.teleport(dest);
            }
            if (c.vivo()) {
                c.e.setInvulnerable(false);
                if (aw != null && meio != null) {   // furia final: raios em volta da arena
                    for (int i = 0; i < 6; i++) {
                        int ii = i;
                        pl.depois(ii * 6L, () -> aw.strikeLightningEffect(meio.clone().add(Math.cos(ii) * 6, 0, Math.sin(ii) * 6)));
                    }
                }
            }
          } finally {
            if (c.vivo()) c.e.setInvulnerable(false);
            soltarArena();
            voltaAntiga.clear();
            voltaAntiga.putAll(volta);
            volta.clear();
            chefe = null;
            saindo = false;
          }
        });
    }

    private void encerrarSemVolta() {
        Chefe c = chefe;
        tirarCorpo();
        if (c != null && c.vivo()) { if (c.e instanceof Mob mob) mob.setAI(true); c.sumidoAte = 0; c.e.setInvulnerable(false); }
        if (c != null && w != null) for (Player p : w.getPlayers()) {
            Location dest = volta.get(p.getUniqueId());
            if (dest != null && dest.getWorld() != null) p.teleport(dest);
            else pl.irParaSpawnMasmorras(p);
        }
        soltarArena();
        voltaAntiga.clear();
        voltaAntiga.putAll(volta);
        volta.clear();
        chefe = null;
        saindo = false;
    }

    /** O servidor vai desligar / plugin recarregar: ninguem fica preso la. */
    void desligar() {
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            Location dest = volta.get(p.getUniqueId());
            if (dest != null && dest.getWorld() != null) p.teleport(dest);
            else pl.irParaSpawnMasmorras(p);
        }
        tirarCorpo();
        soltarArena();
        chefe = null;
    }

    // ------------------------------------------------------------------ seguranca
    /** La dentro ele nao morre: a vida trava em 30% e todos voltam para a arena (o premio e dado la). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void golpeNoCorpo(org.bukkit.event.entity.EntityDamageByEntityEvent ev) {
        Chefe c = chefe;
        if (c == null || corpo == null || ev.getEntity() != corpo) return;
        ev.setCancelled(true);
        Player a = ev.getDamager() instanceof Player p ? p : ev.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (a == null || !c.vivo()) return;
        c.e.damage(ev.getDamage(), a);
        corpo.playHurtAnimation(0);
        corpo.getWorld().playSound(corpo.getLocation(), "minecraft:entity.player.hurt", 1f, 0.6f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dano(EntityDamageEvent ev) {
        Chefe c = chefe;
        if (c != null && corpo != null && ev.getEntity() == corpo && !(ev instanceof org.bukkit.event.entity.EntityDamageByEntityEvent)) { ev.setCancelled(true); return; }
        if (c == null || ev.getEntity() != c.e) return;
        // 15/11 (Paulo: "e para permanecermos na fase final"): nao existe mais a trava de 30% nem a volta para a arena no
        // meio da luta. Ele morre aqui, no mundo dele; so depois todos voltam para a arena buscar o premio.
    }

    /** Quem saiu do jogo la dentro volta para o spawn das masmorras. */
    @EventHandler
    public void entrou(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        if (!p.getWorld().getName().equals(MUNDO)) return;
        if (chefe != null && volta.containsKey(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline() && p.getWorld().getName().equals(MUNDO)) pl.irParaSpawnMasmorras(p); }, 10L);
    }

    /** Quem morre la dentro renasce na arena (onde estava antes de ser puxado). */
    @EventHandler(priority = EventPriority.HIGH)
    public void renasceu(PlayerRespawnEvent ev) {
        Player p = ev.getPlayer();
        if (!p.getWorld().getName().equals(MUNDO) && (ev.getRespawnLocation().getWorld() == null || !ev.getRespawnLocation().getWorld().getName().equals(MUNDO))) return;
        // 15/11 (Paulo: voltou sozinho para a arena e nao conseguiu mais lutar): quem morre com a fase ainda rolando
        // renasce NA ILHA, junto da luta; so volta para a arena quando a fase acaba (ou se ela ja acabou).
        if (chefe != null && !saindo && w != null && corpo != null && corpo.isValid() && volta.containsKey(p.getUniqueId())) {
            ev.setRespawnLocation(new Location(w, 0.5, Y + 1, 8.5, 180, 0));
            return;
        }
        Location dest = volta.get(p.getUniqueId());
        if (dest == null) dest = voltaAntiga.get(p.getUniqueId());
        if (dest != null && dest.getWorld() != null) ev.setRespawnLocation(dest);
        else Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline() && p.getWorld().getName().equals(MUNDO)) pl.irParaSpawnMasmorras(p); }, 5L);
    }
}
