package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Lobby principal do Mundo das Masmorras (centro do mapa, no planalto): praca redonda com fonte, quatro portoes
 * para as estradas (norte, sul, leste, oeste), NPCs (Mercador Lendario, Guia das Masmorras, Mestre das Classes),
 * placares de ranking (chefes derrotados e maior nivel), campo de treino e a esmeralda de volta ao spawn principal.
 * Dentro do raio do lobby ninguem se machuca.
 */
final class LobbyMundo implements Listener {

    static final int R = 34, RAIO_SEGURO = 60;
    private final Masmorras pl;
    private final MundoMasmorras mm;
    private final NamespacedKey kGuia, kClasses, kBoneco, kEnfeite, kPlacar;
    private final List<ItemDisplay> girando = new ArrayList<>();
    private final List<TextDisplay> placares = new ArrayList<>();
    private float giro;
    private World mundo;
    private int y;

    LobbyMundo(Masmorras pl, MundoMasmorras mm) {
        this.pl = pl;
        this.mm = mm;
        kGuia = new NamespacedKey(pl, "lobby_guia");
        kClasses = new NamespacedKey(pl, "lobby_classes");
        kBoneco = new NamespacedKey(pl, "lobby_boneco");
        kEnfeite = new NamespacedKey(pl, "lobby_enfeite");
        kPlacar = new NamespacedKey(pl, "lobby_placar");
    }

    void iniciar() {
        mundo = mm.normal;
        if (mundo == null) return;
        y = GeradorMundo.LOBBY_Y;
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) mundo.getChunkAt(cx, cz).load(true);
        File feito = new File(pl.getDataFolder(), "lobby-mundo-v1.txt");
        if (!feito.exists()) {
            try {
                construir();
                Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Lobby do Mundo das Masmorras nao terminou: " + ex);
                ex.printStackTrace();
            }
        }
        File v2 = new File(pl.getDataFolder(), "lobby-mundo-v2.txt");
        if (!v2.exists()) {
            try {
                construirV2();
                Files.writeString(v2.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Lobby v2 (missoes e vendedor) nao terminou: " + ex);
            }
        }
        File v4 = new File(pl.getDataFolder(), "lobby-mundo-v4.txt");
        if (!v4.exists()) {
            try {
                construirV4();
                Files.writeString(v4.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Lobby v4 (coroa e farol) nao terminou: " + ex);
            }
        }
        Bukkit.getScheduler().runTaskTimer(pl, this::ambiente, 80L, 8L);
        File v3 = new File(pl.getDataFolder(), "lobby-mundo-v3.txt");
        if (pl.chocadeiras != null) {
            try {
                pl.chocadeiras.iniciar(mundo, y, !v3.exists());
                if (!v3.exists()) Files.writeString(v3.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Lobby v3 (chocadeiras) nao terminou: " + ex);
            }
        }
        for (Entity e : mundo.getEntities()) {
            if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(kEnfeite)) girando.add(d);
            if (e instanceof TextDisplay t && t.getPersistentDataContainer().has(kPlacar)) placares.add(t);
        }
        Bukkit.getScheduler().runTaskTimer(pl, this::garantirNpcs, 100L, 200L);
        Bukkit.getScheduler().runTaskTimer(pl, this::girar, 60L, 10L);
        Bukkit.getScheduler().runTaskTimer(pl, this::atualizarPlacares, 120L, 20L * 60);
    }

    boolean noLobby(Location l) {
        return mundo != null && l.getWorld().equals(mundo) && l.getX() * l.getX() + l.getZ() * l.getZ() <= RAIO_SEGURO * RAIO_SEGURO;
    }

    // ------------------------------------------------------------------ construcao

    private void por(int x, int yy, int z, String bloco) {
        mundo.getBlockAt(x, yy, z).setBlockData(Bukkit.createBlockData(bloco), false);
    }

    private void por(int x, int yy, int z, Material m) {
        mundo.getBlockAt(x, yy, z).setType(m, false);
    }

    private void construir() {
        Random rnd = new Random(mm.gerador.semente);
        // limpa o que a geracao deixou em cima do planalto (grama, flores, arvores) e firma o chao embaixo
        for (int x = -R - 6; x <= R + 6; x++)
            for (int z = -R - 6; z <= R + 6; z++) {
                double d = Math.hypot(x, z);
                if (d > R + 6) continue;
                for (int k = y + 1; k <= y + 40; k++) {
                    Block b = mundo.getBlockAt(x, k, z);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
                for (int k = y - 4; k <= y - 1; k++) por(x, k, z, Material.STONE_BRICKS);
            }
        // piso
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.hypot(x, z);
                if (d > R) continue;
                boolean estrada = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                String piso;
                if (d <= 2.5) piso = "minecraft:chiseled_stone_bricks";
                else if (d <= 10.5) {
                    double ang = Math.toDegrees(Math.atan2(z, x));
                    boolean raio = Math.abs(((ang % 45) + 45) % 45 - 22.5) > 21.2;
                    piso = raio ? "minecraft:polished_blackstone" : ((x + z) & 1) == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles";
                } else if (d >= R - 2) piso = d >= R - 0.8 ? "minecraft:chiseled_deepslate" : "minecraft:deepslate_bricks";
                else if (estrada) piso = Math.abs(x) == 2 || Math.abs(z) == 2 ? "minecraft:polished_andesite" : rnd.nextInt(8) == 0 ? "minecraft:cracked_stone_bricks" : "minecraft:stone_bricks";
                else {
                    int k = rnd.nextInt(20);
                    piso = k < 12 ? "minecraft:stone_bricks" : k < 16 ? "minecraft:mossy_stone_bricks" : k < 18 ? "minecraft:cracked_stone_bricks" : "minecraft:cobblestone";
                }
                por(x, y, z, piso);
            }
        // canteiros (grama, flores e arvores) nos quatro quadrantes
        for (int[] c : new int[][] {{17, -17}, {-17, -17}, {-17, 17}}) canteiro(c[0], c[1], rnd);
        // muro baixo com lampioes na borda, deixando os portoes abertos
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.hypot(x, z);
                if (d < R - 0.8 || d > R) continue;
                if (Math.abs(x) <= 3 || Math.abs(z) <= 3) continue;   // portoes
                boolean poste = (Math.round(Math.toDegrees(Math.atan2(z, x))) + 360) % 15 < 2;
                if (poste) {
                    por(x, y + 1, z, "minecraft:deepslate_bricks");
                    por(x, y + 2, z, "minecraft:deepslate_bricks");
                    por(x, y + 3, z, "minecraft:lantern");
                } else por(x, y + 1, z, "minecraft:deepslate_brick_wall");
            }
        // portoes (arcos) e estradas saindo do lobby
        portao(0, -R, 0);
        portao(0, R, 0);
        portao(R, 0, 1);
        portao(-R, 0, 1);
        for (int k = R + 1; k <= GeradorMundo.LOBBY_RAIO + 10; k++) {
            for (int w = -2; w <= 2; w++) {
                String p = Math.abs(w) == 2 ? "minecraft:polished_andesite" : rnd.nextInt(7) == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks";
                estradaBloco(w, k, p);
                estradaBloco(w, -k, p);
                estradaBloco(k, w, p);
                estradaBloco(-k, w, p);
            }
            if (k % 9 == 0) for (int[] s : new int[][] {{3, k}, {-3, k}, {3, -k}, {-3, -k}, {k, 3}, {k, -3}, {-k, 3}, {-k, -3}}) lampiao(s[0], s[1]);
        }
        // fonte central
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                boolean borda = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (borda) por(x, y + 1, z, (Math.abs(x) == 2 && Math.abs(z) == 2) ? "minecraft:chiseled_deepslate" : "minecraft:polished_deepslate_wall");
                else if (x != 0 || z != 0) por(x, y + 1, z, "minecraft:water");
            }
        por(0, y + 1, 0, "minecraft:chiseled_deepslate");
        por(0, y + 2, 0, "minecraft:sea_lantern");
        por(0, y + 3, 0, "minecraft:polished_deepslate_wall");
        por(0, y + 4, 0, "minecraft:sea_lantern");
        for (int[] c : new int[][] {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) por(c[0], y + 2, c[1], "minecraft:soul_lantern");
        item(new Location(mundo, 0.5, y + 6.2, 0.5), new ItemStack(Material.NETHER_STAR), 1.6f);
        texto(new Location(mundo, 0.5, y + 8.3, 0.5), "§6§lMUNDO DAS MASMORRAS\n§7Nível 1 perto daqui • quanto mais longe, mais forte\n§eGuia §7a leste • §dMercador Lendário §7ao norte • §bClasses §7a oeste • §aVoltar §7ao sul", 1.4f, false);
        // palco do Mercador (norte)
        for (int x = -2; x <= 2; x++) for (int z = -28; z <= -25; z++) por(x, y + 1, z, "minecraft:polished_blackstone_bricks");
        por(-2, y + 2, -28, "minecraft:lantern");
        por(2, y + 2, -28, "minecraft:lantern");
        texto(new Location(mundo, 0.5, y + 5, -26.5), "§5§lMERCADOR LENDÁRIO\n§7Moeda de Reviver, relíquias e itens raros\n§8paga com coronas", 1.1f, false);
        // Guia (leste) e Mestre das Classes (oeste): pedestais
        pedestal(26, 0);
        texto(new Location(mundo, 26.5, y + 5, 0.5), "§e§lGUIA DAS MASMORRAS\n§7Mostra todas as masmorras e dá a bússola\n§8clique nele", 1.1f, false);
        pedestal(-26, 0);
        texto(new Location(mundo, -25.5, y + 5, 0.5), "§b§lMESTRE DAS CLASSES\n§7Escolha ou troque a sua classe\n§8clique nele", 1.1f, false);
        // volta ao spawn principal (sul): esmeralda com borda de ouro
        for (int x = -1; x <= 1; x++) for (int z = 25; z <= 27; z++) por(x, y, z, "minecraft:gold_block");
        por(0, y, 26, "minecraft:emerald_block");
        texto(new Location(mundo, 0.5, y + 3.2, 26.5), "§a§lVOLTAR AO SPAWN\n§7pise na esmeralda", 1.0f, false);
        // placares de ranking (nordeste e noroeste), com pedestal
        pedestal(16, -16);
        pedestal(-16, -16);
        TextDisplay p1 = texto(new Location(mundo, 16.5, y + 7.5, -15.5), "§c§lCHEFES DERROTADOS\n§7carregando...", 1.5f, true);
        TextDisplay p2 = texto(new Location(mundo, -15.5, y + 7.5, -15.5), "§b§lMAIORES NÍVEIS DE PODER\n§7carregando...", 1.5f, true);
        p1.getPersistentDataContainer().set(kPlacar, PersistentDataType.INTEGER, 1);
        p2.getPersistentDataContainer().set(kPlacar, PersistentDataType.INTEGER, 2);
        placares.add(p1);
        placares.add(p2);
        // campo de treino (sudeste): tatame de terra batida com 4 bonecos
        for (int x = 12; x <= 24; x++)
            for (int z = 12; z <= 22; z++) {
                boolean borda = x == 12 || x == 24 || z == 12 || z == 22;
                por(x, y, z, borda ? "minecraft:stripped_spruce_wood" : ((x + z) & 1) == 0 ? "minecraft:coarse_dirt" : "minecraft:packed_mud");
                boolean entrada = (x == 12 && z >= 16 && z <= 18) || (z == 12 && x >= 17 && x <= 19);
                if (borda && !entrada) por(x, y + 1, z, "minecraft:spruce_fence");
            }
        for (int[] c : BONECOS) por(c[0], y, c[1], "minecraft:hay_block");
        texto(new Location(mundo, 18.5, y + 5, 22.5), "§e§lCAMPO DE TREINO\n§7bata nos bonecos e veja o seu dano", 1.1f, false);
        // luz invisivel pela praca toda
        Light luz = (Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(15);
        for (int x = -R + 2; x <= R - 2; x += 6)
            for (int z = -R + 2; z <= R - 2; z += 6) {
                Block b = mundo.getBlockAt(x, y + 4, z);
                if (b.getType().isAir()) b.setBlockData(luz, false);
            }
        placa(2, y + 1, 4, BlockFace.NORTH, "§6§lLOBBY DAS", "§6§lMASMORRAS", "§fNível 1 em volta", "§7estradas N S L O");
        placa(-2, y + 1, 4, BlockFace.NORTH, "§e§lCORONAS", "§fmonstros e chefes", "§fdas masmorras", "§7saldo: /coronas");
        pl.getLogger().info("Lobby do Mundo das Masmorras construido (praca de " + (R * 2 + 1) + " blocos).");
    }

    static final int[][] BONECOS = {{15, 15}, {21, 15}, {15, 19}, {21, 19}};
    static final int VENDEDOR_X = -26, VENDEDOR_Z = 8;

    /** v2 (Fase 2): pedestal e letreiro do Vendedor de Classes e letreiros dos 4 NPCs de missao em volta da fonte. */
    private void construirV2() {
        pedestal(VENDEDOR_X, VENDEDOR_Z);
        texto(new Location(mundo, VENDEDOR_X + 0.5, y + 5, VENDEDOR_Z + 0.5), "§6§lVENDEDOR DE CLASSES\n§7Lutador, Saiyajin, Cipó e Tridente\n§7para quem não tem VIP (coronas)", 1.0f, false);
        if (pl.missoes != null)
            for (Missoes.Npc n : pl.missoes.npcs()) {
                int sx = Integer.signum(n.x()), sz = Integer.signum(n.z());
                // tapete de pedra do NPC e letreiro
                por(n.x(), y, n.z(), "minecraft:polished_blackstone_bricks");
                por(n.x() + sx, y, n.z(), "minecraft:polished_blackstone");
                por(n.x(), y, n.z() + sz, "minecraft:polished_blackstone");
                texto(new Location(mundo, n.x() + 0.5, y + 3.3, n.z() + 0.5), n.cor() + "§l" + n.nome().toUpperCase(java.util.Locale.ROOT) + "\n§7missões: " + descricaoCurta(n.id()), 0.9f, false);
            }
        pl.getLogger().info("Lobby v2: Vendedor de Classes e NPCs de missao.");
    }

    /**
     * v4 (08/11, "algo diferente de Minecraft"): a COROA do Corona Server flutuando sobre a praca (anel de ouro com 8
     * pontas, joias de vidro vermelho e lanternas do mar), um FAROL (beacon) escondido na fonte cujo feixe vermelho sobe
     * pelo meio da coroa ate o ceu (e da Velocidade a quem esta na praca), e bandeiras do servidor nos postes do muro.
     */
    private void construirV4() {
        // farol na fonte: piramide de ferro embaixo do espelho d'agua, farol no lugar do pilar, vidro colorido em cima
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) por(x, y, z, "minecraft:iron_block");
        por(0, y + 1, 0, "minecraft:beacon");
        por(0, y + 2, 0, "minecraft:red_stained_glass");
        por(0, y + 3, 0, "minecraft:polished_deepslate_wall");
        por(0, y + 4, 0, "minecraft:orange_stained_glass");
        try {
            if (mundo.getBlockAt(0, y + 1, 0).getState() instanceof org.bukkit.block.Beacon b) {
                b.setPrimaryEffect(org.bukkit.potion.PotionEffectType.SPEED);
                b.update(true, false);
            }
        } catch (Throwable t) {
            pl.getLogger().warning("Farol do lobby: " + t);
        }
        // coroa: anel de raio 6 a 24 blocos de altura, 8 pontas
        int cy = y + 24;
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                double d = Math.hypot(x, z);
                if (d < 5.3 || d > 6.7) continue;
                por(x, cy, z, "minecraft:gold_block");
                por(x, cy + 1, z, d > 6.2 || d < 5.8 ? "minecraft:gold_block" : "minecraft:red_stained_glass");
            }
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            int px = (int) Math.round(Math.cos(a) * 6), pz = (int) Math.round(Math.sin(a) * 6);
            int alt = k % 2 == 0 ? 5 : 3;
            for (int h = 2; h <= alt; h++) por(px, cy + h, pz, h == alt ? (k % 2 == 0 ? "minecraft:sea_lantern" : "minecraft:red_stained_glass") : "minecraft:gold_block");
            if (k % 2 == 0) por(px, cy + alt + 1, pz, "minecraft:end_rod");
            // pingentes de lanterna embaixo do anel
            por(px, cy - 1, pz, "minecraft:iron_chain[axis=y]");
            por(px, cy - 2, pz, k % 2 == 0 ? "minecraft:soul_lantern[hanging=true]" : "minecraft:lantern[hanging=true]");
        }
        // bandeiras do servidor (vermelho/preto/branco) nos postes do muro
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.hypot(x, z);
                if (d < R - 0.8 || d > R) continue;
                if (Math.abs(x) <= 3 || Math.abs(z) <= 3) continue;
                boolean poste = (Math.round(Math.toDegrees(Math.atan2(z, x))) + 360) % 15 < 2;
                if (!poste) continue;
                // bandeira na face voltada para o centro
                int fx = x > 0 ? -1 : x < 0 ? 1 : 0, fz = z > 0 ? -1 : z < 0 ? 1 : 0;
                if (Math.abs(x) > Math.abs(z)) fz = 0; else fx = 0;
                String face = fx == 1 ? "east" : fx == -1 ? "west" : fz == 1 ? "south" : "north";
                Block alvo = mundo.getBlockAt(x + fx, y + 2, z + fz);
                if (alvo.getType().isAir()) por(x + fx, y + 2, z + fz, "minecraft:red_wall_banner[facing=" + face + "]");
            }
        pl.getLogger().info("Lobby v4: coroa gigante, farol vermelho e bandeiras.");
    }

    private int ambTick;

    /** Particulas do farol (espiral de almas subindo pelo feixe) e um sino distante de vez em quando. */
    private void ambiente() {
        if (mundo == null) return;
        boolean alguem = false;
        for (Player p : mundo.getPlayers()) if (p.getLocation().distanceSquared(new Location(mundo, 0, y, 0)) < 70 * 70) { alguem = true; break; }
        if (!alguem) return;
        ambTick++;
        double base = ambTick * 0.35;
        for (int braco = 0; braco < 3; braco++) {
            double a = base + braco * (Math.PI * 2 / 3);
            double h = (ambTick * 0.6 + braco * 7) % 22;
            double r = 1.2 + 0.6 * Math.sin(h * 0.4);
            Location l = new Location(mundo, 0.5 + Math.cos(a) * r, y + 3 + h, 0.5 + Math.sin(a) * r);
            mundo.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, l, 1, 0, 0, 0, 0);
            if (ambTick % 3 == 0) mundo.spawnParticle(org.bukkit.Particle.END_ROD, l, 1, 0.1, 0.1, 0.1, 0.01);
        }
        if (ambTick % 450 == 0) mundo.playSound(new Location(mundo, 0.5, y + 24, 0.5), org.bukkit.Sound.BLOCK_BELL_RESONATE, 1.2f, 0.6f);
    }

    private static String descricaoCurta(String id) {
        return switch (id) {
            case "ferreiro" -> "espadas";
            case "armeira" -> "armaduras";
            case "cacadora" -> "arco e botas";
            default -> "poções e totem";
        };
    }

    private void estradaBloco(int x, int z, String piso) {
        int top = mundo.getHighestBlockYAt(x, z);
        for (int k = y + 1; k <= Math.max(top, y + 1); k++) {
            Block b = mundo.getBlockAt(x, k, z);
            if (!b.getType().isAir()) b.setType(Material.AIR, false);
        }
        por(x, y, z, piso);
    }

    private void lampiao(int x, int z) {
        int top = mundo.getHighestBlockYAt(x, z);
        if (top > y + 1) return;   // no morro nao poe
        por(x, y + 1, z, "minecraft:deepslate_brick_wall");
        por(x, y + 2, z, "minecraft:lantern");
    }

    private void portao(int x, int z, int eixo) {
        // arco de 5 de largura e 5 de altura; eixo 0 = atravessa no z (portao norte/sul), 1 = atravessa no x
        for (int w = -3; w <= 3; w++) {
            int px = eixo == 0 ? x + w : x, pz = eixo == 0 ? z : z + w;
            int alt = Math.abs(w) == 3 ? 5 : Math.abs(w) == 2 ? 5 : 0;
            for (int k = 1; k <= alt; k++) por(px, y + k, pz, Math.abs(w) == 3 ? "minecraft:deepslate_bricks" : "minecraft:polished_deepslate");
            if (Math.abs(w) <= 2) por(px, y + 5, pz, "minecraft:deepslate_bricks");
            if (Math.abs(w) == 3) {
                por(px, y + 6, pz, "minecraft:deepslate_brick_wall");
                por(px, y + 7, pz, "minecraft:lantern");
            }
        }
        int px = eixo == 0 ? x : x, pz = eixo == 0 ? z : z;
        por(px, y + 6, pz, "minecraft:chiseled_deepslate");
        por(px, y + 7, pz, "minecraft:soul_lantern");
    }

    private void pedestal(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) por(x + dx, y + 1, z + dz, dx == 0 && dz == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:deepslate_tiles");
        por(x - 1, y + 2, z - 1, "minecraft:lantern");
        por(x + 1, y + 2, z + 1, "minecraft:lantern");
    }

    private void canteiro(int cx, int cz, Random rnd) {
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 17) continue;
                int x = cx + dx, z = cz + dz;
                por(x, y, z, dx * dx + dz * dz > 14 ? "minecraft:moss_block" : "minecraft:grass_block");
                int k = rnd.nextInt(12);
                if (k == 0) por(x, y + 1, z, "minecraft:poppy");
                else if (k == 1) por(x, y + 1, z, "minecraft:cornflower");
                else if (k == 2) por(x, y + 1, z, "minecraft:oxeye_daisy");
                else if (k < 7) por(x, y + 1, z, "minecraft:short_grass");
            }
        arvore(cx, cz, rnd);
    }

    private void arvore(int x, int z, Random rnd) {
        int alt = 5 + rnd.nextInt(2);
        for (int k = 1; k <= alt; k++) por(x, y + k, z, "minecraft:dark_oak_log");
        for (int dy = alt - 2; dy <= alt + 1; dy++) {
            int r = dy == alt + 1 ? 1 : dy == alt ? 2 : 3;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r + 1) continue;
                    if (dx == 0 && dz == 0 && dy <= alt) continue;
                    Block b = mundo.getBlockAt(x + dx, y + dy, z + dz);
                    if (b.getType().isAir()) b.setBlockData(Bukkit.createBlockData("minecraft:dark_oak_leaves[persistent=true]"), false);
                }
        }
        por(x, y + alt + 2, z, "minecraft:lantern");
    }

    private void placa(int x, int yy, int z, BlockFace olha, String... linhas) {
        BlockData d = Bukkit.createBlockData("minecraft:dark_oak_sign[rotation=" + switch (olha) {
            case SOUTH -> 0;
            case WEST -> 4;
            case EAST -> 12;
            default -> 8;
        } + "]");
        Block b = mundo.getBlockAt(x, yy, z);
        b.setBlockData(d, false);
        if (b.getState() instanceof Sign s) {
            for (int i = 0; i < 4 && i < linhas.length; i++) s.getSide(Side.FRONT).line(i, Masmorras.txt(linhas[i]));
            s.getSide(Side.FRONT).setGlowingText(true);
            s.setWaxed(true);
            s.update(true, false);
        }
    }

    private TextDisplay texto(Location l, String t, float escala, boolean esquerda) {
        return mundo.spawn(l, TextDisplay.class, x -> {
            x.text(Masmorras.txt(t));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setAlignment(esquerda ? TextDisplay.TextAlignment.LEFT : TextDisplay.TextAlignment.CENTER);
            x.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
            x.setPersistent(true);
            x.setLineWidth(260);
            x.getPersistentDataContainer().set(kEnfeite, PersistentDataType.INTEGER, 2);
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(escala), new org.joml.AxisAngle4f()));
        });
    }

    private void item(Location l, ItemStack it, float escala) {
        ItemDisplay d = mundo.spawn(l, ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setPersistent(true);
            x.getPersistentDataContainer().set(kEnfeite, PersistentDataType.INTEGER, 1);
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(escala), new org.joml.AxisAngle4f()));
        });
        girando.add(d);
    }

    private void girar() {
        if (mundo == null || mundo.getPlayers().isEmpty()) return;
        giro += 0.3f;
        for (ItemDisplay d : girando) {
            if (!d.isValid()) continue;
            org.bukkit.util.Transformation t = d.getTransformation();
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(10);
            d.setTransformation(new org.bukkit.util.Transformation(t.getTranslation(), new org.joml.AxisAngle4f(giro, 0, 1, 0), t.getScale(), new org.joml.AxisAngle4f()));
        }
    }

    // ------------------------------------------------------------------ placares

    private void atualizarPlacares() {
        if (mundo == null || pl.ranking == null) return;
        for (TextDisplay t : placares) {
            if (!t.isValid()) continue;
            Integer q = t.getPersistentDataContainer().get(kPlacar, PersistentDataType.INTEGER);
            if (q == null) continue;
            String s = q == 1 ? pl.ranking.placar("§c§lCHEFES DERROTADOS", "chefes", "chefes", "§c") : pl.ranking.placar("§b§lMAIORES NÍVEIS DE PODER", "nivel", "de poder", "§b");
            t.text(Masmorras.txt(s));
        }
    }

    // ------------------------------------------------------------------ npcs

    private void garantirNpcs() {
        if (mundo == null || !mundo.isChunkLoaded(0, -2) || !mundo.isChunkLoaded(1, 0) || !mundo.isChunkLoaded(-2, 0)) return;
        try {
            Location merc = new Location(mundo, 0.5, y + 2, -26.5, 0f, 0f);
            if (!existe(merc, pl.loja.kMercador)) pl.loja.npc(merc, "§5§lMercador Lendário", null, Loja.profissao(6), pl.loja.kMercador, 6);
            Location guia = new Location(mundo, 26.5, y + 2, 0.5, 90f, 0f);
            if (!existe(guia, kGuia)) {
                Villager v = pl.loja.npc(guia, "§e§lGuia das Masmorras", null, Loja.profissao(0), kGuia, 1);
                v.setProfession(Hub.Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft("cartographer")));
            }
            Location cls = new Location(mundo, -25.5, y + 2, 0.5, -90f, 0f);
            if (!existe(cls, kClasses)) {
                Villager v = pl.loja.npc(cls, "§b§lMestre das Classes", null, Loja.profissao(0), kClasses, 1);
                v.setProfession(Hub.Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft("librarian")));
            }
            if (pl.missoes != null) pl.missoes.garantirNpcs(mundo, y);
            if (pl.vendaClasses != null) pl.vendaClasses.garantirNpc(mundo, y, VENDEDOR_X, VENDEDOR_Z);
            for (int[] c : BONECOS) {
                Location l = new Location(mundo, c[0] + 0.5, y + 1, c[1] + 0.5, 180f, 0f);
                boolean tem = false;
                for (Entity e : mundo.getNearbyEntities(l, 1, 2, 1)) if (e.getPersistentDataContainer().has(kBoneco)) tem = true;
                if (tem) continue;
                mundo.spawn(l, org.bukkit.entity.Mannequin.class, CreatureSpawnEvent.SpawnReason.CUSTOM, m -> {
                    m.getPersistentDataContainer().set(kBoneco, PersistentDataType.BYTE, (byte) 1);
                    m.setImmovable(true);
                    m.setGravity(false);
                    m.setSilent(true);
                    m.setPersistent(true);
                    m.customName(Masmorras.txt("§e§lBoneco de Treino"));
                    m.setCustomNameVisible(true);
                    m.setDescription(Masmorras.txt("§7bata em mim"));
                    if (m.getEquipment() != null) {
                        m.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                        m.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                    }
                });
            }
        } catch (Throwable t) {
            pl.getLogger().warning("NPCs do lobby: " + t);
        }
    }

    private static boolean existe(Location l, NamespacedKey k) {
        for (Entity e : l.getWorld().getNearbyEntities(l, 3, 3, 3)) if (e.getPersistentDataContainer().has(k)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEntityEvent e) {
        Entity x = e.getRightClicked();
        boolean guia = x.getPersistentDataContainer().has(kGuia), classes = x.getPersistentDataContainer().has(kClasses);
        if (!guia && !classes) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (guia) {
            p.sendMessage(Masmorras.txt("§e§lGuia: §fAs masmorras de §anível 1 §fficam em volta do lobby; as de §enível 2§f, §6nível 3 §fe §cnível 4 §fcada vez mais longe. Escolha uma e eu te dou a bússola."));
            pl.abrirMenu(p);
        } else {
            p.sendMessage(Masmorras.txt("§b§lMestre das Classes: §fCada classe tem 5 habilidades que liberam com o seu nível de poder."));
            if (!p.performCommand("classe")) p.sendMessage(Masmorras.txt("§7Use §e/classe§7."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void naoTransforma(EntityTransformEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kGuia) || e.getEntity().getPersistentDataContainer().has(kClasses)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoApanha(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kGuia) || e.getEntity().getPersistentDataContainer().has(kClasses)) e.setCancelled(true);
    }

    /** Boneco nao morre: mostra o dano que levaria (numero flutuando e na barra). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void bateuNoBoneco(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kBoneco)) return;
        double dano = e.getFinalDamage();
        e.setCancelled(true);
        Entity d = e.getDamager();
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        World w = e.getEntity().getWorld();
        Location l = e.getEntity().getLocation().add((Math.random() - 0.5) * 0.8, 2.2, (Math.random() - 0.5) * 0.8);
        String num = String.format(java.util.Locale.ROOT, "%.1f", dano);
        TextDisplay t = w.spawn(l, TextDisplay.class, x -> {
            x.text(Masmorras.txt((dano >= 10 ? "§c§l" : "§e") + "-" + num));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setPersistent(false);
            x.setTeleportDuration(10);
        });
        Bukkit.getScheduler().runTaskLater(pl, () -> t.teleport(t.getLocation().add(0, 0.8, 0)), 1L);
        Bukkit.getScheduler().runTaskLater(pl, t::remove, 16L);
        w.spawnParticle(org.bukkit.Particle.CRIT, e.getEntity().getLocation().add(0, 1.2, 0), 8, 0.3, 0.4, 0.3, 0.1);
        if (d instanceof Player p) p.sendActionBar(Masmorras.txt("§eDano: §f" + num + " §7(" + String.format(java.util.Locale.ROOT, "%.1f", dano / 2) + " corações)"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void bonecoNaoMorre(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kBoneco)) e.setCancelled(true);
    }

    /** Dentro do lobby ninguem se machuca (queda, fogo, monstro). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void dano(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && noLobby(e.getEntity().getLocation())) e.setCancelled(true);
    }

    /** Pisou na esmeralda: volta ao spawn principal. */
    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (mundo == null || !to.getWorld().equals(mundo)) return;
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        if (Math.abs(to.getBlockX()) <= 1 && to.getBlockZ() >= 25 && to.getBlockZ() <= 27 && to.getBlock().getRelative(BlockFace.DOWN).getType() == Material.EMERALD_BLOCK) {
            World w = Bukkit.getWorld(pl.mundoSpawnPrincipal());
            if (w == null) return;
            Player p = e.getPlayer();
            p.teleportAsync(w.getSpawnLocation().add(0.5, 0, 0.5), PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .thenAccept(ok -> p.playSound(p.getLocation(), "minecraft:entity.enderman.teleport", 1f, 1.2f));
        }
    }
}
