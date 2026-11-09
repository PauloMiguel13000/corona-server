package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Lobby do Terror (mundo "terror", 09/11): uma ilha assombrada flutuando, sempre meia-noite de lua cheia. No meio,
 * um obelisco com fogo de alma; em volta, cemiterio, arvores mortas, postes com lanterna de alma e caminhos de terra
 * ate 10 portoes goticos (um por jogo de terror, 2 "Em breve" e, ao sul, a volta ao Lobby dos Minigames). Entrar no
 * portao entra no jogo. Esmeralda perto de quem chega volta ao spawn. Sons de vez em quando, relampagos ao longe e
 * morcegos. As protecoes (nada quebra, ninguem se machuca, sem fome, sem monstro, pet pode) sao as do LobbyMinigames.
 * /terror leva para ca. Trocar a VERSAO reconstroi (marca lobby-terror-vN.txt).
 */
final class LobbyTerror implements Listener {

    static final String MUNDO = "terror";
    static final int Y = 100, R = 44, PORTAL = 30, VAGAS = 10, VERSAO = 2;
    /** Quantas arvores sairam na ultima construcao (para a conferencia). */
    int arvoresFeitas;

    /** Volta ao lobby dos minigames normais (portao do sul, atras de quem chega). */
    static final LobbyMinigames.Portal SAIDA = new LobbyMinigames.Portal("minigames", "§f§lMINIGAMES", "§7Defesa da Torre • Arena PvP", "§f", "minigames", 0, 0,
            new String[] {"minecraft:quartz_bricks", "minecraft:polished_blackstone_bricks", "minecraft:red_nether_bricks"}, Material.COMPASS, Color.WHITE, false);

    /** Vaga 0 ao norte (de frente para quem chega), uma a cada 36 graus no sentido horario. */
    static final Map<String, Integer> VAGA_PREFERIDA = Map.of("hotel", 0, "fabrica", 1, "backrooms", 2, "slender", 3, "minigames", 5,
            "vovo", 7, "naochore", 8, "turno", 9);

    private final Extras pl;
    World mundo;
    private final List<ItemDisplay> icones = new ArrayList<>();
    private final Map<UUID, Long> espera = new HashMap<>();
    private final Random sorte = new Random();
    private float giro;
    private long proximoRaio;

    LobbyTerror(Extras pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------------ vagas

    /** Portoes deste lobby: os jogos de terror e a saida. */
    static List<LobbyMinigames.Portal> portoes() {
        List<LobbyMinigames.Portal> l = new ArrayList<>(List.of(LobbyMinigames.deTerror()));
        l.add(SAIDA);
        return l;
    }

    static int vaga(LobbyMinigames.Portal p) {
        Integer v = VAGA_PREFERIDA.get(p.id());
        if (v != null) return v;
        Set<Integer> usadas = new HashSet<>(VAGA_PREFERIDA.values());
        // jogo de terror novo: primeiro as vagas "Em breve" (4 e 6), depois o que sobrar
        int[] ordem = {4, 6, 0, 1, 2, 3, 5, 7, 8, 9};
        for (LobbyMinigames.Portal o : portoes()) {
            if (VAGA_PREFERIDA.containsKey(o.id())) continue;
            for (int c : ordem)
                if (usadas.add(c)) {
                    if (o == p) return c;
                    break;
                }
        }
        return 4;
    }

    static double angulo(int vaga) {
        return -90 + vaga * (360.0 / VAGAS);
    }

    static int cxVaga(int vaga) {
        return (int) Math.round(Math.cos(Math.toRadians(angulo(vaga))) * PORTAL);
    }

    static int czVaga(int vaga) {
        return (int) Math.round(Math.sin(Math.toRadians(angulo(vaga))) * PORTAL);
    }

    /** O portao fica de frente para o centro: perto de norte/sul = plano em X; perto de leste/oeste = plano em Z. */
    static boolean eixoX(int vaga) {
        double a = Math.toRadians(angulo(vaga));
        return Math.abs(Math.sin(a)) >= Math.abs(Math.cos(a));
    }

    /** Borda irregular da ilha (raio no angulo dado). */
    static double borda(double ang) {
        return R + 2.5 * Math.sin(3 * ang + 0.7) + 1.8 * Math.cos(5 * ang) + 1.2 * Math.sin(7 * ang + 2);
    }

    // ------------------------------------------------------------------ mundo

    static final class Vazio extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override public Biome getBiome(WorldInfo info, int x, int y, int z) { return Biome.PALE_GARDEN; }
                @Override public List<Biome> getBiomes(WorldInfo info) { return List.of(Biome.PALE_GARDEN); }
            };
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, Y + 1, 14.5, 180f, 0f);
        }
    }

    Location chegada() {
        return new Location(mundo, 0.5, Y + 1, 14.5, 180f, 0f);
    }

    boolean ehTerror(World w) {
        return w != null && w.getName().equals(MUNDO);
    }

    private org.bukkit.NamespacedKey chave() {
        return new org.bukkit.NamespacedKey(pl, "terror_enfeite");
    }

    private org.bukkit.NamespacedKey chaveVersao() {
        return new org.bukkit.NamespacedKey(pl, "terror_v");
    }

    private org.bukkit.NamespacedKey chaveMorcego() {
        return new org.bukkit.NamespacedKey(pl, "terror_morcego");
    }

    void iniciar() {
        if (!pl.getConfig().getBoolean("lobby-terror.ativo", true)) return;
        mundo = new WorldCreator(MUNDO).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        mundo.setGameRule(GameRule.MOB_GRIEFING, false);
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
        mundo.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        mundo.setFullTime(18000);   // meia-noite do dia 0: lua cheia
        mundo.setStorm(false);
        mundo.setThundering(false);
        mundo.setViewDistance(5);
        mundo.setSpawnLocation(chegada());
        for (int cx = -4; cx <= 3; cx++) for (int cz = -4; cz <= 3; cz++) mundo.getChunkAt(cx, cz).load(true);
        File feito = new File(pl.getDataFolder(), "lobby-terror-v" + VERSAO + ".txt");
        if (!feito.exists()) {
            try {
                construir();
                Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable t) {
                pl.getLogger().warning("Lobby do Terror nao terminou de construir: " + t);
            }
        }
        icones.clear();
        for (Entity e : mundo.getEntities())
            if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) icones.add(d);
        // coroa sombria em cima do obelisco e ilhotas mortas em volta (uma vez; marca lobby-terror-ceu-vN.txt)
        Ceu.fazer(pl, "lobby-terror", mundo, 0, 0, Y, 51, 74, Ceu.TERROR, false, false);
        Bukkit.getScheduler().runTaskTimer(pl, this::girar, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(pl, this::ambiente, 100L, 40L);
    }

    /** Admin: /extras refazerterror. */
    void refazer(CommandSender quem) {
        if (mundo == null) {
            quem.sendMessage("§cO Lobby do Terror nao esta ligado.");
            return;
        }
        construir();
        icones.clear();
        for (Entity e : mundo.getEntities())
            if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) icones.add(d);
        quem.sendMessage("§aLobby do Terror refeito.");
    }

    @EventHandler
    public void enfeitesCarregados(org.bukkit.event.world.EntitiesLoadEvent e) {
        if (!ehTerror(e.getWorld())) return;
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(chaveMorcego(), PersistentDataType.BYTE)) {
                en.remove();   // os morcegos nascem de novo quando alguem chega
                continue;
            }
            if (!en.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) continue;
            Integer v = en.getPersistentDataContainer().get(chaveVersao(), PersistentDataType.INTEGER);
            if (v == null || v != VERSAO) en.remove();
        }
    }

    // ------------------------------------------------------------------ construcao

    private final Set<String> blocosRuins = new HashSet<>();

    private void por(int x, int y, int z, String bloco) {
        try {
            mundo.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(bloco), false);
        } catch (IllegalArgumentException ex) {
            if (blocosRuins.add(bloco)) pl.getLogger().warning("Lobby do Terror: bloco invalido " + bloco);
        }
    }

    private boolean livre(int x, int y, int z) {
        return mundo.getBlockAt(x, y, z).getType().isAir();
    }

    /** Numero "aleatorio" fixo por posicao (a ilha sai sempre igual). */
    private static int h(int x, int z, int sal) {
        int n = x * 73856093 ^ z * 19349663 ^ sal * 83492791;
        n ^= (n >>> 13);
        n *= 0x5bd1e995;
        n ^= (n >>> 15);
        return n & 0x7fffffff;
    }

    private static double dist(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /** Distancia do ponto ate o caminho (raio) da vaga, so no trecho entre a praca e o portao; senao infinito. */
    private static double aoCaminho(double x, double z, int vaga) {
        double a = Math.toRadians(angulo(vaga));
        double ux = Math.cos(a), uz = Math.sin(a);
        double t = x * ux + z * uz;
        if (t < 8 || t > PORTAL + 1) return 999;
        return Math.abs(-x * uz + z * ux);
    }

    private static double aoCaminhoMaisPerto(double x, double z) {
        double m = 999;
        for (int k = 0; k < VAGAS; k++) m = Math.min(m, aoCaminho(x, z, k));
        return m;
    }

    /** Lugar de quem chega (sem tumulo nem arvore). */
    private static boolean areaChegada(double x, double z) {
        return Math.abs(x) <= 7 && z >= 7 && z <= 22;
    }

    private void construir() {
        // tira o que houver (blocos e enfeites) antes
        for (Entity e : mundo.getEntities())
            if (e.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE) || e instanceof TextDisplay || e instanceof ItemDisplay
                    || e.getPersistentDataContainer().has(chaveMorcego(), PersistentDataType.BYTE)) e.remove();
        icones.clear();
        int L = R + 8;
        for (int x = -L; x <= L; x++)
            for (int z = -L; z <= L; z++)
                for (int y = Y - 22; y <= Y + 18; y++) {
                    Block b = mundo.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
        ilha();
        caminhos();
        praca();
        List<int[]> ocupado = new ArrayList<>();
        Set<Integer> usadas = new HashSet<>();
        for (LobbyMinigames.Portal p : portoes()) {
            int vg = vaga(p);
            usadas.add(vg);
            portao(p, vg);
        }
        for (int k = 0; k < VAGAS; k++) if (!usadas.contains(k)) emBreve(k);
        for (int k = 0; k < VAGAS; k++) ocupado.add(new int[] {cxVaga(k), czVaga(k), 5});
        ocupado.add(new int[] {0, 0, 11});
        postes(ocupado);
        for (double ang : new double[] {-72, 36, 180}) {
            double a = Math.toRadians(ang);
            int x = (int) Math.round(Math.cos(a) * 19), z = (int) Math.round(Math.sin(a) * 19);
            cripta(x, z);
            ocupado.add(new int[] {x, z, 6});
        }
        arvores(ocupado);
        cemiterio(ocupado);
        mureta();
        // volta ao spawn: esmeralda com borda de pedra negra, a direita de quem chega
        for (int x = 5; x <= 7; x++) for (int z = 13; z <= 15; z++) por(x, Y, z, "minecraft:polished_blackstone_bricks");
        por(6, Y, 14, "minecraft:emerald_block");
        texto(new Location(mundo, 6.5, Y + 2.3, 14.5), "§a§lVOLTAR AO SPAWN\n§7pise na esmeralda", 0.9f);
        texto(new Location(mundo, 0.5, Y + 13.2, 0.5), "§4§lLOBBY DO TERROR\n§fEntre num portão... se tiver coragem.\n§7" + LobbyMinigames.deTerror().length
                + " jogos  §8|  §7/minigames menu  §8|  §a/spawn", 1.6f);
        luz();
        pl.getLogger().info("Lobby do Terror construido (" + LobbyMinigames.deTerror().length + " jogos de terror + saida para os minigames).");
    }

    /** Ilha: terra escura por cima, pedra funda por baixo, borda irregular. */
    private void ilha() {
        String[] chao = {"minecraft:podzol", "minecraft:podzol", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
                "minecraft:packed_mud", "minecraft:soul_soil", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:mud_bricks", "minecraft:podzol"};
        String[] rocha = {"minecraft:tuff", "minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:deepslate", "minecraft:blackstone", "minecraft:tuff"};
        int L = R + 7;
        for (int x = -L; x <= L; x++)
            for (int z = -L; z <= L; z++) {
                double d = dist(x, z), rb = borda(Math.atan2(z, x));
                if (d > rb) continue;
                por(x, Y, z, chao[h(x, z, 1) % chao.length]);
                por(x, Y - 1, z, h(x, z, 2) % 3 == 0 ? "minecraft:rooted_dirt" : "minecraft:dirt");
                int fundo = 2 + (int) Math.min(18, (rb - d) * 0.55 + (h(x, z, 3) % 3));
                for (int k = 2; k <= fundo; k++) por(x, Y - k, z, rocha[h(x, z, 10 + k) % rocha.length]);
                // raizes penduradas embaixo, de vez em quando
                if (h(x, z, 4) % 23 == 0) por(x, Y - fundo - 1, z, "minecraft:hanging_roots");
            }
    }

    /** Caminhos de terra do centro ate cada portao e um anel na frente dos portoes. */
    private void caminhos() {
        int L = PORTAL + 3;
        for (int x = -L; x <= L; x++)
            for (int z = -L; z <= L; z++) {
                double d = dist(x, z);
                double c = aoCaminhoMaisPerto(x + 0.5, z + 0.5);
                boolean anel = d >= PORTAL - 5.5 && d < PORTAL - 3.5;
                if (c <= 0.9) por(x, Y, z, "minecraft:dirt_path");
                else if (c <= 1.9 || anel) por(x, Y, z, h(x, z, 5) % 3 == 0 ? "minecraft:gravel" : "minecraft:coarse_dirt");
            }
    }

    /** Praca do meio: piso de ardosia rachada, obelisco com fogo de alma e quatro braseiros. */
    private void praca() {
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                double d = dist(x, z);
                if (d > 9.5) continue;
                String b;
                if (d > 8.5) b = "minecraft:polished_blackstone_bricks";
                else if (d < 2.5) b = "minecraft:polished_deepslate";
                else if (d >= 5.5 && d < 6.5) b = "minecraft:cracked_polished_blackstone_bricks";
                else b = h(x, z, 6) % 4 == 0 ? "minecraft:cracked_deepslate_tiles" : "minecraft:deepslate_tiles";
                por(x, Y, z, b);
            }
        // obelisco
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) por(x, Y + 1, z, "minecraft:polished_blackstone_bricks");
        por(0, Y + 2, 0, "minecraft:chiseled_polished_blackstone");
        for (int[] l : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) por(l[0], Y + 2, l[1], "minecraft:polished_blackstone_brick_wall");
        for (int y = 3; y <= 8; y++) por(0, Y + y, 0, y == 5 ? "minecraft:chiseled_deepslate" : "minecraft:deepslate_tiles");
        por(0, Y + 9, 0, "minecraft:crying_obsidian");
        por(0, Y + 10, 0, "minecraft:soul_lantern");
        // correntes com lanterna de alma penduradas nos lados do obelisco
        for (int[] l : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            por(l[0], Y + 8, l[1], "minecraft:iron_chain[axis=y]");
            por(l[0], Y + 7, l[1], "minecraft:soul_lantern[hanging=true]");
        }
        // braseiros de fogo de alma nas diagonais
        for (int[] l : new int[][] {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
            por(l[0], Y + 1, l[1], "minecraft:polished_blackstone");
            por(l[0], Y + 2, l[1], "minecraft:soul_campfire[lit=true,signal_fire=false]");
        }
    }

    /** Portao gotico de um jogo: arco alto com o portal no meio, caveira no topo, lanternas e aboboras na frente. */
    private void portao(LobbyMinigames.Portal p, int vg) {
        String[] m = p.moldura();
        arco(vg, m, true);
        int cx = cxVaga(vg), cz = czVaga(vg);
        int dirX = (int) Math.signum(Math.round(Math.cos(Math.toRadians(angulo(vg))) * 10)), dirZ = (int) Math.signum(Math.round(Math.sin(Math.toRadians(angulo(vg))) * 10));
        Location topo = new Location(mundo, cx + 0.5 - dirX * 0.7, Y + 12.4, cz + 0.5 - dirZ * 0.7);
        texto(topo, p.nome() + "\n" + p.sub(), 1.6f);
        ItemDisplay d = mundo.spawn(topo.clone().add(0, 1.9, 0), ItemDisplay.class, x -> {
            x.setItemStack(new ItemStack(p.icone()));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setGlowing(true);
            x.setGlowColorOverride(p.brilho());
            x.setPersistent(true);
            x.getPersistentDataContainer().set(chave(), PersistentDataType.BYTE, (byte) 1);
            x.getPersistentDataContainer().set(chaveVersao(), PersistentDataType.INTEGER, VERSAO);
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f), new AxisAngle4f()));
        });
        icones.add(d);
    }

    /** Vaga sem jogo: o mesmo arco, mas fechado com tabuas. */
    private void emBreve(int vg) {
        arco(vg, new String[] {"minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:cobbled_deepslate"}, false);
        int cx = cxVaga(vg), cz = czVaga(vg);
        int dirX = (int) Math.signum(Math.round(Math.cos(Math.toRadians(angulo(vg))) * 10)), dirZ = (int) Math.signum(Math.round(Math.sin(Math.toRadians(angulo(vg))) * 10));
        texto(new Location(mundo, cx + 0.5 - dirX * 0.7, Y + 12.4, cz + 0.5 - dirZ * 0.7), "§8§lEM BREVE\n§7um novo pesadelo...", 1.3f);
    }

    private void arco(int vg, String[] m, boolean aberto) {
        int cx = cxVaga(vg), cz = czVaga(vg);
        boolean ex = eixoX(vg);
        double a = Math.toRadians(angulo(vg));
        int dirX = (int) Math.signum(Math.round(Math.cos(a) * 10)), dirZ = (int) Math.signum(Math.round(Math.sin(a) * 10));
        // de frente para o centro: so um dos dois conta (o plano do portao e X ou Z)
        int fx = ex ? 0 : -dirX, fz = ex ? -dirZ : 0;
        if (fx == 0 && fz == 0) fz = ex ? 1 : 0;
        String eixo = ex ? "x" : "z";
        for (int s = -3; s <= 3; s++)
            for (int y = 0; y <= 10; y++) {
                int x = ex ? cx + s : cx, z = ex ? cz : cz + s;
                int as = Math.abs(s);
                String b = null;
                if (as <= 1 && y >= 1 && y <= 5) {
                    if (aberto) b = "minecraft:nether_portal[axis=" + eixo + "]";
                    else b = y % 2 == 1 ? "minecraft:stripped_dark_oak_log[axis=" + eixo + "]" : null;
                } else if (y == 0) b = m[2];
                else if (as == 2 && y <= 7) b = y == 7 ? m[1] : m[0];
                else if (as == 3 && y <= 7) b = y % 3 == 0 ? m[0] : m[1];
                else if (y == 6 && as <= 1) b = m[0];
                else if (y == 7 && as <= 1) b = m[1];
                else if (y == 8 && as <= 2) b = as == 2 ? m[0] : m[1];
                else if (y == 9 && as <= 1) b = m[0];
                else if (y == 10 && s == 0) b = m[2];
                if (b != null) por(x, Y + y, z, b);
            }
        // caveira no topo, olhando para o centro
        int rot = rotacaoPara(-dirX, -dirZ);
        por(ex ? cx : cx, Y + 11, cz, "minecraft:wither_skeleton_skull[rotation=" + rot + "]");
        // lanternas de alma em cima dos pilares de fora e teia nos cantos de tras
        for (int s : new int[] {-3, 3}) {
            int x = ex ? cx + s : cx, z = ex ? cz : cz + s;
            por(x, Y + 8, z, "minecraft:soul_lantern");
            int tx = x + (ex ? 0 : dirX), tz = z + (ex ? dirZ : 0);
            if (livre(tx, Y + 6, tz)) por(tx, Y + 6, tz, "minecraft:cobweb");
        }
        // aboboras acesas dos dois lados da entrada, viradas para o centro
        String face = face(fx, fz);
        for (int s : new int[] {-3, 3}) {
            int x = (ex ? cx + s : cx) + fx * 2, z = (ex ? cz : cz + s) + fz * 2;
            por(x, Y + 1, z, aberto ? "minecraft:jack_o_lantern[facing=" + face + "]" : "minecraft:carved_pumpkin[facing=" + face + "]");
        }
        // soleira na frente
        for (int s = -1; s <= 1; s++) {
            int x = (ex ? cx + s : cx) + fx, z = (ex ? cz : cz + s) + fz;
            por(x, Y, z, m[2]);
        }
    }

    private static String face(int fx, int fz) {
        if (fz < 0) return "north";
        if (fz > 0) return "south";
        if (fx < 0) return "west";
        return "east";
    }

    /** Rotacao (0-15) de caveira/placa olhando para a direcao (dx, dz). 0 = sul, 4 = oeste, 8 = norte, 12 = leste. */
    private static int rotacaoPara(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(-dx, dz));   // 0 = sul (+z), 90 = oeste (-x)
        return Math.floorMod((int) Math.round(ang / 22.5), 16);
    }

    /** Postes com lanterna de alma ao lado dos caminhos. */
    private void postes(List<int[]> ocupado) {
        for (int k = 0; k < VAGAS; k++) {
            double a = Math.toRadians(angulo(k));
            double ux = Math.cos(a), uz = Math.sin(a), px = -uz, pz = ux;
            for (double t : new double[] {13, 21}) {
                double lado = (t == 13) == (k % 2 == 0) ? 2.6 : -2.6;
                int x = (int) Math.floor(ux * t + px * lado + 0.5), z = (int) Math.floor(uz * t + pz * lado + 0.5);
                if (areaChegada(x, z)) continue;
                por(x, Y + 1, z, "minecraft:dark_oak_fence");
                por(x, Y + 2, z, "minecraft:dark_oak_fence");
                por(x, Y + 3, z, "minecraft:soul_lantern");
                ocupado.add(new int[] {x, z, 2});
            }
        }
    }

    private boolean longeDe(List<int[]> ocupado, int x, int z, int folga) {
        for (int[] o : ocupado) {
            int dx = o[0] - x, dz = o[1] - z, r = o[2] + folga;
            if (dx * dx + dz * dz < r * r) return false;
        }
        return true;
    }

    /** Cemiterio: lapides, covas, velas e caveiras entre os caminhos. */
    private void cemiterio(List<int[]> ocupado) {
        Random r = new Random(4242);
        String[] lapide = {"minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall", "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall",
                "minecraft:chiseled_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:andesite_wall", "minecraft:polished_blackstone_brick_wall",
                "minecraft:tuff_brick_wall", "minecraft:chiseled_tuff"};
        String[] vela = {"minecraft:white_candle", "minecraft:black_candle", "minecraft:red_candle", "minecraft:candle"};
        int feitas = 0;
        for (int tent = 0; tent < 900 && feitas < 70; tent++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 11.5 + r.nextDouble() * 13.5;
            if (r.nextInt(4) == 0) d = PORTAL + 3 + r.nextDouble() * 7;   // alguns atras dos portoes
            int x = (int) Math.floor(Math.cos(ang) * d + 0.5), z = (int) Math.floor(Math.sin(ang) * d + 0.5);
            if (d > borda(ang) - 3) continue;
            if (aoCaminhoMaisPerto(x + 0.5, z + 0.5) < 3.2 || areaChegada(x, z)) continue;
            double dd = dist(x, z);
            if (dd >= PORTAL - 6.5 && dd < PORTAL - 2.5) continue;   // anel na frente dos portoes
            if (!longeDe(ocupado, x, z, 2)) continue;
            // lapide do lado de fora, cova (2 blocos) em direcao ao centro
            double ux = -Math.cos(ang), uz = -Math.sin(ang);
            int cx1 = x + (int) Math.round(ux), cz1 = z + (int) Math.round(uz);
            int cx2 = x + (int) Math.round(ux * 2), cz2 = z + (int) Math.round(uz * 2);
            String l = lapide[r.nextInt(lapide.length)];
            por(x, Y + 1, z, l);
            if (!l.endsWith("_wall") && r.nextInt(3) == 0) por(x, Y + 2, z, "minecraft:skeleton_skull[rotation=" + rotacaoPara(ux, uz) + "]");
            else if (l.endsWith("_wall") && r.nextInt(4) == 0) por(x, Y + 2, z, vela[r.nextInt(vela.length)] + "[lit=true,candles=" + (1 + r.nextInt(3)) + "]");
            por(cx1, Y, cz1, r.nextBoolean() ? "minecraft:rooted_dirt" : "minecraft:coarse_dirt");
            por(cx2, Y, cz2, r.nextBoolean() ? "minecraft:rooted_dirt" : "minecraft:coarse_dirt");
            int sorteio = r.nextInt(10);
            if (sorteio < 3) por(cx2, Y + 1, cz2, vela[r.nextInt(vela.length)] + "[lit=true,candles=" + (1 + r.nextInt(4)) + "]");
            else if (sorteio == 3) por(cx2, Y + 1, cz2, "minecraft:wither_rose");
            else if (sorteio == 4) por(cx1, Y + 1, cz1, "minecraft:dead_bush");
            else if (sorteio == 5 && livre(x + (int) Math.round(-ux), Y + 1, z + (int) Math.round(-uz))) por(x + (int) Math.round(-ux), Y + 1, z + (int) Math.round(-uz), "minecraft:cobweb");
            ocupado.add(new int[] {x, z, 1});
            ocupado.add(new int[] {cx2, cz2, 1});
            feitas++;
        }
        // grama seca e flores murchas soltas
        for (int tent = 0; tent < 260; tent++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 11 + r.nextDouble() * (R - 12);
            int x = (int) Math.floor(Math.cos(ang) * d + 0.5), z = (int) Math.floor(Math.sin(ang) * d + 0.5);
            if (d > borda(ang) - 1.5 || aoCaminhoMaisPerto(x + 0.5, z + 0.5) < 2.2 || areaChegada(x, z) || !livre(x, Y + 1, z) || !longeDe(ocupado, x, z, 0)) continue;
            Material base = mundo.getBlockAt(x, Y, z).getType();
            if (base != Material.PODZOL && base != Material.COARSE_DIRT && base != Material.ROOTED_DIRT) continue;
            int s = r.nextInt(6);
            por(x, Y + 1, z, s == 0 ? "minecraft:wither_rose" : s <= 2 ? "minecraft:dead_bush" : "minecraft:short_dry_grass");
        }
    }

    /** Cripta pequena (5 x 6) de ardosia, de frente para o centro: porta de ferro fechada, cruz e lanternas de alma. */
    private void cripta(int x, int z) {
        // direcao (eixo) de quem esta na frente olhando para o centro
        int fx, fz;
        if (Math.abs(x) >= Math.abs(z)) {
            fx = x > 0 ? -1 : 1;
            fz = 0;
        } else {
            fx = 0;
            fz = z > 0 ? -1 : 1;
        }
        int ox = -fx, oz = -fz, px = -oz, pz = ox;   // para fora e de lado
        String porta = face(fx, fz);
        for (int p = -2; p <= 2; p++)
            for (int d = 0; d <= 5; d++) {
                int bx = x + px * p + ox * d, bz = z + pz * p + oz * d;
                por(bx, Y, bz, "minecraft:polished_deepslate");
                boolean canto = Math.abs(p) == 2 && (d == 0 || d == 5);
                boolean parede = Math.abs(p) == 2 || d == 0 || d == 5;
                for (int y = 1; y <= 4; y++) {
                    String b = null;
                    if (canto) b = "minecraft:deepslate_tiles";
                    else if (parede) {
                        if (d == 0 && p == 0 && y <= 2) b = "minecraft:iron_door[half=" + (y == 1 ? "lower" : "upper") + ",facing=" + porta + ",hinge=left,open=false]";
                        else if (d == 0 && p == 0 && y == 4) b = "minecraft:chiseled_deepslate";
                        else if (Math.abs(p) == 2 && (d == 2 || d == 3) && y == 2) b = "minecraft:iron_bars";
                        else b = (h(bx, bz, 20 + y) % 5 == 0) ? "minecraft:cracked_deepslate_bricks" : "minecraft:deepslate_bricks";
                    }
                    if (b != null) por(bx, Y + y, bz, b);
                }
                por(bx, Y + 5, bz, canto ? "minecraft:deepslate_tiles" : "minecraft:cobbled_deepslate_slab[type=bottom]");
                if (p == 0 && d >= 1 && d <= 4) por(bx, Y + 6, bz, "minecraft:deepslate_tile_wall");
                if (p == 0 && (d == 2 || d == 3)) por(bx, Y + 1, bz, "minecraft:dark_oak_slab[type=bottom]");
            }
        // cruz na frente e lanternas nos cantos da frente
        int cx = x, cz = z;
        por(cx, Y + 6, cz, "minecraft:polished_blackstone_wall");
        por(cx, Y + 7, cz, "minecraft:polished_blackstone_wall");
        por(cx, Y + 8, cz, "minecraft:polished_blackstone_wall");
        por(cx + px, Y + 7, cz + pz, "minecraft:polished_blackstone_wall");
        por(cx - px, Y + 7, cz - pz, "minecraft:polished_blackstone_wall");
        for (int p : new int[] {-2, 2}) por(x + px * p, Y + 6, z + pz * p, "minecraft:soul_lantern");
        // degrau e velas na frente
        for (int p = -1; p <= 1; p++) por(x + px * p + fx, Y, z + pz * p + fz, "minecraft:polished_blackstone_bricks");
        por(x + px * 2 + fx, Y + 1, z + pz * 2 + fz, "minecraft:black_candle[lit=true,candles=3]");
        por(x - px * 2 + fx, Y + 1, z - pz * 2 + fz, "minecraft:red_candle[lit=true,candles=2]");
    }

    /** Arvores mortas: tronco escuro, galhos tortos, teias e uma lanterna de alma pendurada de vez em quando. */
    private void arvores(List<int[]> ocupado) {
        Random r = new Random(777);
        List<double[]> lugares = new ArrayList<>();
        for (int k = 0; k < VAGAS; k++) lugares.add(new double[] {Math.toRadians(angulo(k) + 18 + (r.nextDouble() - 0.5) * 8), PORTAL + 4 + r.nextDouble() * 5});
        for (int k = 0; k < 14; k++) lugares.add(new double[] {r.nextDouble() * Math.PI * 2, 13 + r.nextDouble() * 12});
        for (int k = 0; k < 8; k++) lugares.add(new double[] {r.nextDouble() * Math.PI * 2, PORTAL + 3 + r.nextDouble() * 8});
        int feitas = 0;
        for (double[] lg : lugares) {
            int x = (int) Math.floor(Math.cos(lg[0]) * lg[1] + 0.5), z = (int) Math.floor(Math.sin(lg[0]) * lg[1] + 0.5);
            if (lg[1] > borda(lg[0]) - 3 || aoCaminhoMaisPerto(x + 0.5, z + 0.5) < 3.6 || areaChegada(x, z)) continue;
            double dd = dist(x, z);
            if (dd >= PORTAL - 6.5 && dd < PORTAL - 2.5) continue;
            if (!longeDe(ocupado, x, z, 2)) continue;
            arvore(x, z, r);
            ocupado.add(new int[] {x, z, 3});
            feitas++;
        }
        arvoresFeitas = feitas;
        pl.getLogger().info("Lobby do Terror: " + feitas + " arvores mortas.");
    }

    private void arvore(int x, int z, Random r) {
        String tronco = r.nextInt(3) == 0 ? "minecraft:spruce_log" : "minecraft:dark_oak_log";
        int alt = 5 + r.nextInt(4);
        por(x, Y, z, "minecraft:rooted_dirt");
        for (int y = 1; y <= alt; y++) por(x, Y + y, z, tronco + "[axis=y]");
        // raizes em volta do pe
        for (int[] l : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            if (r.nextInt(3) == 0 && livre(x + l[0], Y + 1, z + l[1])) por(x + l[0], Y + 1, z + l[1], "minecraft:mangrove_roots");
        int galhos = 2 + r.nextInt(3);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int g = 0; g < galhos; g++) {
            int[] dv = dirs[r.nextInt(4)];
            int gy = alt - r.nextInt(3);
            int len = 1 + r.nextInt(3);
            int bx = x, bz = z, by = Y + gy;
            for (int i = 1; i <= len; i++) {
                bx += dv[0];
                bz += dv[1];
                if (!livre(bx, by, bz)) break;
                por(bx, by, bz, tronco + "[axis=" + (dv[0] != 0 ? "x" : "z") + "]");
            }
            // ponta virando para cima
            if (r.nextBoolean() && livre(bx, by + 1, bz)) por(bx, by + 1, bz, tronco + "[axis=y]");
            int s = r.nextInt(5);
            if (s == 0 && livre(bx, by - 1, bz)) {
                por(bx, by - 1, bz, "minecraft:iron_chain[axis=y]");
                if (livre(bx, by - 2, bz)) por(bx, by - 2, bz, "minecraft:soul_lantern[hanging=true]");
            } else if (s <= 2 && livre(bx + dv[0], by, bz + dv[1])) por(bx + dv[0], by, bz + dv[1], "minecraft:cobweb");
        }
    }

    /** Mureta quebrada na borda (com buracos). */
    private void mureta() {
        int L = R + 7;
        List<int[]> pos = new ArrayList<>();
        for (int x = -L; x <= L; x++)
            for (int z = -L; z <= L; z++) {
                double rb = borda(Math.atan2(z, x)), d = dist(x, z);
                if (d > rb || d < rb - 1.0) continue;
                if (h(x, z, 8) % 6 == 0) continue;   // buracos
                if (!livre(x, Y + 1, z) || mundo.getBlockAt(x, Y, z).getType().isAir()) continue;
                pos.add(new int[] {x, z});
            }
        for (int[] p : pos)
            mundo.getBlockAt(p[0], Y + 1, p[1]).setBlockData(Bukkit.createBlockData(h(p[0], p[1], 9) % 3 == 0 ? "minecraft:mossy_cobblestone_wall" : "minecraft:cobbled_deepslate_wall"), true);
    }

    /** Luz invisivel fraca: da para enxergar o caminho, mas continua escuro. */
    private void luz() {
        org.bukkit.block.data.type.Light fraca = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        fraca.setLevel(8);
        org.bukkit.block.data.type.Light media = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        media.setLevel(11);
        for (int x = -R; x <= R; x += 6)
            for (int z = -R; z <= R; z += 6) {
                double d = dist(x, z);
                if (d > borda(Math.atan2(z, x)) - 2) continue;
                Block b = mundo.getBlockAt(x, Y + 4, z);
                if (b.getType().isAir()) b.setBlockData(d < 11 || areaChegada(x, z) ? media : fraca, false);
            }
    }

    private void texto(Location l, String t, float escala) {
        mundo.spawn(l, TextDisplay.class, x -> {
            x.text(Extras.txt(t));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setPersistent(true);
            x.getPersistentDataContainer().set(chave(), PersistentDataType.BYTE, (byte) 2);
            x.getPersistentDataContainer().set(chaveVersao(), PersistentDataType.INTEGER, VERSAO);
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(escala), new AxisAngle4f()));
        });
    }

    // ------------------------------------------------------------------ vida do lobby

    private void girar() {
        if (mundo == null || mundo.getPlayers().isEmpty()) return;
        giro += 0.3f;
        for (ItemDisplay d : icones) {
            if (!d.isValid()) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(10);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(giro, 0, 1, 0), new Vector3f(1.6f), new AxisAngle4f()));
        }
    }

    private static final Sound[] SONS = {Sound.AMBIENT_CAVE, Sound.AMBIENT_CAVE, Sound.ENTITY_GHAST_AMBIENT, Sound.BLOCK_BELL_RESONATE, Sound.ENTITY_PHANTOM_AMBIENT,
            Sound.ENTITY_ZOMBIE_AMBIENT, Sound.BLOCK_WOODEN_DOOR_CLOSE, Sound.ENTITY_WARDEN_HEARTBEAT, Sound.ENTITY_ENDERMAN_AMBIENT, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD};

    /** A cada 2 s: sons ao longe, um relampago de vez em quando, morcegos voando e sempre meia-noite. */
    private void ambiente() {
        if (mundo == null) return;
        List<Player> aqui = mundo.getPlayers();
        if (aqui.isEmpty()) return;
        if (mundo.getTime() != 18000) mundo.setTime(18000);
        for (Player p : aqui) {
            if (sorte.nextInt(12) != 0) continue;
            double a = sorte.nextDouble() * Math.PI * 2;
            Location l = p.getLocation().add(Math.cos(a) * 9, 1, Math.sin(a) * 9);
            Sound s = SONS[sorte.nextInt(SONS.length)];
            float vol = s == Sound.AMBIENT_CAVE ? 0.7f : s == Sound.ENTITY_WARDEN_HEARTBEAT ? 0.9f : 0.45f;
            p.playSound(l, s, vol, 0.6f + sorte.nextFloat() * 0.4f);
        }
        long agora = System.currentTimeMillis();
        if (agora >= proximoRaio) {
            if (proximoRaio != 0) {
                double a = sorte.nextDouble() * Math.PI * 2;
                mundo.strikeLightningEffect(new Location(mundo, Math.cos(a) * (R + 14), Y - 6, Math.sin(a) * (R + 14)));
            }
            proximoRaio = agora + 70_000L + sorte.nextInt(60_000);
        }
        morcegos();
    }

    private void morcegos() {
        List<Bat> bats = new ArrayList<>();
        for (Entity e : mundo.getEntities()) if (e instanceof Bat b && b.getPersistentDataContainer().has(chaveMorcego(), PersistentDataType.BYTE)) bats.add(b);
        for (Bat b : bats) {
            Location l = b.getLocation();
            if (dist(l.getX(), l.getZ()) > R - 4 || l.getY() < Y + 2 || l.getY() > Y + 26) b.teleport(new Location(mundo, sorte.nextInt(17) - 8, Y + 8, sorte.nextInt(17) - 8));
        }
        for (int i = bats.size(); i < 5; i++) {
            Location l = new Location(mundo, sorte.nextInt(31) - 15 + 0.5, Y + 7 + sorte.nextInt(5), sorte.nextInt(31) - 15 + 0.5);
            mundo.spawn(l, Bat.class, b -> {
                b.setPersistent(false);
                b.setRemoveWhenFarAway(false);
                b.setSilent(false);
                b.getPersistentDataContainer().set(chaveMorcego(), PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    // ------------------------------------------------------------------ entrar e andar

    void teleportar(Player p) {
        if (mundo == null) {
            p.sendMessage(Extras.txt("§cO Lobby do Terror não está ligado."));
            return;
        }
        p.setFallDistance(0);
        p.teleportAsync(chegada(), PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§4§lLOBBY DO TERROR"), Extras.txt("§7Escolha o seu pesadelo...")));
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.7f);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.6f, 0.6f);
        });
    }

    /** Portao em que o bloco esta (ou null). */
    private LobbyMinigames.Portal portaoEm(Location l) {
        int y = l.getBlockY() - Y;
        if (y < 0 || y > 6) return null;
        for (LobbyMinigames.Portal p : portoes()) {
            int vg = vaga(p);
            int ox = l.getBlockX() - cxVaga(vg), oz = l.getBlockZ() - czVaga(vg);
            if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) return p;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (!ehTerror(to.getWorld())) return;
        Player p = e.getPlayer();
        if (to.getY() < Y - 20) {
            teleportar(p);
            return;
        }
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        if (to.getBlockY() == Y + 1 && to.getBlockX() == 6 && to.getBlockZ() == 14) {
            p.performCommand("spawn");
            return;
        }
        LobbyMinigames.Portal po = portaoEm(to);
        if (po == null) return;
        // ja estava dentro do portao (voltou de um jogo e caiu ali): so empurra para fora, sem entrar de novo
        if (portaoEm(e.getFrom()) != null) {
            p.teleport(fora(po, to));
            return;
        }
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        espera.put(p.getUniqueId(), agora + 4000L);
        p.teleport(fora(po, to));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        if (!p.performCommand(po.comando())) p.sendMessage(Extras.txt("§cEsse minigame não está disponível agora."));
    }

    /** Lugar na frente do portao (4 blocos para o centro). */
    private Location fora(LobbyMinigames.Portal po, Location olhar) {
        int vg = vaga(po);
        double ang = Math.toRadians(angulo(vg));
        return new Location(mundo, cxVaga(vg) - Math.round(Math.cos(ang) * 4) + 0.5, Y + 1, czVaga(vg) - Math.round(Math.sin(ang) * 4) + 0.5, olhar.getYaw(), olhar.getPitch());
    }

    /** Quem volta de um jogo para dentro de um portao (lugar guardado) aparece na frente dele. */
    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGH)
    public void chegou(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null || !ehTerror(to.getWorld())) return;
        LobbyMinigames.Portal po = portaoEm(to);
        if (po != null) e.setTo(fora(po, to));
    }

    // ------------------------------------------------------------------ conferencia (admin)

    /**
     * Confere a pe (corpo de 2 blocos, chao firme) se da para ir de quem chega ate a frente de cada portao e da
     * esmeralda, e se o portal de cada portao esta inteiro. /extras conferirterror
     */
    void conferir(CommandSender quem) {
        if (mundo == null) {
            quem.sendMessage("§cLobby do Terror desligado.");
            return;
        }
        int L = R + 8;
        boolean[][] visto = new boolean[2 * L + 1][2 * L + 1];
        ArrayDeque<int[]> fila = new ArrayDeque<>();
        fila.add(new int[] {0, 14});
        visto[L][L + 14] = true;
        while (!fila.isEmpty()) {
            int[] c = fila.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + d[0], z = c[1] + d[1];
                if (Math.abs(x) > L || Math.abs(z) > L || visto[x + L][z + L]) continue;
                if (!andavel(x, z)) continue;
                visto[x + L][z + L] = true;
                fila.add(new int[] {x, z});
            }
        }
        int ok = 0, falhas = 0;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < VAGAS; k++) {
            double a = Math.toRadians(angulo(k));
            int fx = (int) Math.round(Math.cos(a) * (PORTAL - 3)), fz = (int) Math.round(Math.sin(a) * (PORTAL - 3));
            boolean chega = visto[fx + L][fz + L];
            String nome = "vaga " + k;
            boolean temPortal = false;
            for (LobbyMinigames.Portal p : portoes()) if (vaga(p) == k) {
                nome = p.id();
                temPortal = true;
            }
            boolean inteiro = true;
            if (temPortal) {
                boolean ex = eixoX(k);
                for (int s = -1; s <= 1; s++)
                    for (int y = 1; y <= 5; y++) {
                        int x = ex ? cxVaga(k) + s : cxVaga(k), z = ex ? czVaga(k) : czVaga(k) + s;
                        if (mundo.getBlockAt(x, Y + y, z).getType() != Material.NETHER_PORTAL) inteiro = false;
                    }
            }
            boolean bom = chega && inteiro;
            if (bom) ok++;
            else falhas++;
            sb.append(bom ? "§a" : "§c").append(nome).append(chega ? "" : " (sem caminho)").append(inteiro ? "" : " (portal quebrado)").append("§7, ");
        }
        boolean esmeralda = visto[6 + L][14 + L];
        quem.sendMessage("§eLobby do Terror: §f" + ok + " ok, " + falhas + " com problema; esmeralda " + (esmeralda ? "§aok" : "§cSEM CAMINHO"));
        quem.sendMessage(sb.toString());
        pl.getLogger().info("Conferencia do Lobby do Terror: " + ok + " ok, " + falhas + " problema(s), esmeralda " + esmeralda + " -> " + sb.toString().replaceAll("§.", ""));
    }

    private boolean andavel(int x, int z) {
        Block chao = mundo.getBlockAt(x, Y, z);
        if (!chao.getType().isSolid() && chao.getType() != Material.DIRT_PATH) return false;
        return passa(mundo.getBlockAt(x, Y + 1, z)) && passa(mundo.getBlockAt(x, Y + 2, z));
    }

    private static boolean passa(Block b) {
        Material m = b.getType();
        if (m == Material.COBWEB) return false;
        return !m.isSolid() || m == Material.NETHER_PORTAL;
    }
}
