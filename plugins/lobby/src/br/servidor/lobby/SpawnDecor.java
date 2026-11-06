package br.servidor.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Enfeita o spawn que o dono construiu (a plataforma de obsidiana chorona com a cupula de obsidiana),
 * sem desmanchar nada dele: so troca alguns blocos do piso por caminhos e coloca coisas onde e ar.
 * - caminhos de quartzo com faixa de purpura e luzes saindo das 4 aberturas da cupula ate a borda;
 * - um anel de quartzo em volta da cupula, bancos e postes com lanterna;
 * - 4 jardins com cerejeiras nos cantos;
 * - parapeito de vidro em volta (ninguem cai sem querer);
 * - 7 totens clicaveis: Casa da Vovo, Slender, Defesa da Torre, Backrooms, Classes, Explorar (rtp) e Kits;
 * - letreiros de boas-vindas.
 */
final class SpawnDecor implements Listener {

    static final int VERSAO = 5; // 3: totem do Hotel; 4: Hotel em cima do piso (o 3 ficou fora da plataforma)
    // a plataforma do dono
    static final int X1 = -22, X2 = 44, Z1 = -35, Z2 = 28, PISO = 122;
    static final int CX = 6, CZ = -6;

    record Totem(String id, double x, double z, Material icone, String titulo, String sub, String comando) {}

    static final List<Totem> TOTENS = List.of(
            new Totem("vovo", 15.5, 10.5, Material.IRON_DOOR, "§5§lCASA DA VOVÓ", "§7Fuja da vovó. §aNormal §7ou §cDifícil§7.\n§7Cuidado com a aranha do esgoto!", "vovo"),
            new Totem("backrooms", 15.5, 20.5, Material.YELLOW_GLAZED_TERRACOTTA, "§e§lBACKROOMS", "§7Saia da realidade e ache a saída.\n§aFácil §7· §eNormal §7· §cDifícil", "backrooms"),
            new Totem("hotel", -3.5, 20.5, Material.DARK_OAK_DOOR, "§4§lO HOTEL", "§7Abra 100 portas e não deixe ELES te acharem.\n§7Esconda-se nos armários!", "hotel"),
            new Totem("slender", -3.5, 10.5, Material.ENDER_EYE, "§8§lSLENDERMAN", "§7Ache as páginas no escuro.", "slender"),
            new Totem("defesa", 22.5, 3.5, Material.BOW, "§6§lDEFESA DA TORRE", "§7Segure as ondas de monstros.", "defesa"),
            new Totem("classes", -10.5, 3.5, Material.BLAZE_ROD, "§b§lCLASSES E PODERES", "§7Escolha sua classe.", "classe"),
            new Totem("explorar", 22.5, -15.5, Material.COMPASS, "§a§lEXPLORAR O MUNDO", "§7Vai para um lugar aleatório\n§7e seguro do mapa.", "rtp"),
            new Totem("kits", -10.5, -15.5, Material.CHEST, "§e§lKITS", "§7Pegue seus kits.", "kit"));

    private final LoginLobby pl;
    private final NamespacedKey kEnfeite, kTotem;
    private World w;
    private final Random r = new Random(13);
    private final List<ItemDisplay> icones = new ArrayList<>();
    private float giro;
    private org.bukkit.scheduler.BukkitTask tarefaGiro;

    SpawnDecor(LoginLobby pl) {
        this.pl = pl;
        this.kEnfeite = new NamespacedKey(pl, "enfeite_spawn");
        this.kTotem = new NamespacedKey(pl, "totem");
    }

    World mundo() {
        return Bukkit.getWorld("world") != null ? Bukkit.getWorld("world") : Bukkit.getWorlds().get(0);
    }

    /** Liga o giro dos icones e reconhece os totens que ja existem. */
    void iniciar() {
        w = mundo();
        carregar();
        icones.clear();
        for (Entity e : w.getEntities())
            if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(kEnfeite, PersistentDataType.BYTE)) icones.add(d);
        if (tarefaGiro == null) tarefaGiro = Bukkit.getScheduler().runTaskTimer(pl, this::girar, 40L, 10L);
    }

    private void carregar() {
        for (int cx = (X1 - 2) >> 4; cx <= (X2 + 2) >> 4; cx++)
            for (int cz = (Z1 - 2) >> 4; cz <= (Z2 + 2) >> 4; cz++) w.getChunkAt(cx, cz).load(true);
    }

    /** A cada 10 ticks (o jogo interpola o giro entre um e outro, fica liso) e so com alguem perto: menos pacotes. */
    private void girar() {
        if (w == null || !alguemPerto()) return;
        giro += 0.3f;
        for (ItemDisplay d : icones) {
            if (!d.isValid()) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(10);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(giro, 0, 1, 0), new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f()));
        }
    }

    private boolean alguemPerto() {
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - CX) < 96 && Math.abs(l.getZ() - CZ) < 96) return true;
        }
        return false;
    }

    // ================================================================== blocos

    private Material tipo(int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType();
    }

    private void b(int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void d(int x, int y, int z, String dados) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(dados), false);
    }

    /** Troca so o piso de obsidiana chorona (nunca mexe em outra coisa do dono). */
    private void piso(int x, int z, Material m) {
        if (tipo(x, PISO, z) == Material.CRYING_OBSIDIAN) b(x, PISO, z, m);
    }

    /** Coloca so onde e ar, em cima do piso da plataforma. */
    private boolean livre(int x, int y, int z) {
        return tipo(x, y, z).isAir() && x >= X1 && x <= X2 && z >= Z1 && z <= Z2;
    }

    private void poe(int x, int y, int z, String dados) {
        if (livre(x, y, z)) d(x, y, z, dados);
    }

    private static double dist(double x, double z) {
        return Math.sqrt((x - CX) * (x - CX) + (z - CZ) * (z - CZ));
    }

    void construir() {
        w = mundo();
        carregar();
        removerEntidades();
        caminhos();
        anel();
        jardins();
        parapeito();
        postesEBancos();
        cupula();
        lugares.clear();
        for (Totem t : TOTENS) totem(t);
        letreiros();
        salvarMapa();
        pl.getLogger().info("Spawn enfeitado (versao " + VERSAO + ").");
    }

    /** Versao nova so com totens/letreiros diferentes: nao refaz o resto (so poe os blocos do totem novo). */
    void atualizar() {
        w = mundo();
        carregar();
        removerEntidades();
        limparTotensNoAr();
        lugares.clear();
        for (Totem t : TOTENS) totem(t);
        letreiros();
        salvarMapa();
        pl.getLogger().info("Totens do spawn atualizados (versao " + VERSAO + ").");
    }

    private void removerEntidades() {
        for (Entity e : w.getEntities())
            if (e.getPersistentDataContainer().has(kEnfeite, PersistentDataType.BYTE)) e.remove();
        icones.clear();
    }

    private void caminhos() {
        // norte e sul (x = 5..7), oeste e leste (z = -7..-5), da cupula ate a borda
        for (int z = Z1; z <= Z2; z++) {
            if (dist(CX, z) < 12) continue;
            for (int x = CX - 1; x <= CX + 1; x++) piso(x, z, x == CX ? (Math.abs(z - CZ) % 6 == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.PURPUR_BLOCK) : Material.SMOOTH_QUARTZ);
            piso(CX - 2, z, Material.QUARTZ_BRICKS);
            piso(CX + 2, z, Material.QUARTZ_BRICKS);
        }
        for (int x = X1; x <= X2; x++) {
            if (dist(x, CZ) < 12) continue;
            for (int z = CZ - 1; z <= CZ + 1; z++) piso(x, z, z == CZ ? (Math.abs(x - CX) % 6 == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.PURPUR_BLOCK) : Material.SMOOTH_QUARTZ);
            piso(x, CZ - 2, Material.QUARTZ_BRICKS);
            piso(x, CZ + 2, Material.QUARTZ_BRICKS);
        }
    }

    private void anel() {
        for (int x = CX - 16; x <= CX + 16; x++)
            for (int z = CZ - 16; z <= CZ + 16; z++) {
                double dd = dist(x + 0.5, z + 0.5);
                if (dd >= 12.0 && dd < 14.0) piso(x, z, Material.SMOOTH_QUARTZ);
                else if (dd >= 14.0 && dd < 14.8) piso(x, z, Material.PURPUR_BLOCK);
                else if (dd >= 11.2 && dd < 12.0) piso(x, z, Material.QUARTZ_BRICKS);
            }
        // luzes no anel, entre os caminhos
        for (int a = 0; a < 360; a += 30) {
            if (a % 90 == 0) continue;
            int x = (int) Math.floor(CX + 0.5 + 13 * Math.cos(Math.toRadians(a)));
            int z = (int) Math.floor(CZ + 0.5 + 13 * Math.sin(Math.toRadians(a)));
            piso(x, z, Material.PEARLESCENT_FROGLIGHT);
        }
    }

    private void jardins() {
        int[][] cantos = {{X1 + 2, Z1 + 2}, {X2 - 12, Z1 + 2}, {X1 + 2, Z2 - 12}, {X2 - 12, Z2 - 12}};
        Material[] flores = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.ALLIUM, Material.OXEYE_DAISY,
                Material.AZURE_BLUET, Material.LILY_OF_THE_VALLEY, Material.PINK_TULIP, Material.WHITE_TULIP};
        for (int[] c : cantos) {
            int gx1 = c[0], gz1 = c[1], gx2 = c[0] + 10, gz2 = c[1] + 10;
            for (int x = gx1; x <= gx2; x++)
                for (int z = gz1; z <= gz2; z++) {
                    if (tipo(x, PISO, z) != Material.CRYING_OBSIDIAN || !tipo(x, PISO + 1, z).isAir()) continue;
                    boolean borda = x == gx1 || x == gx2 || z == gz1 || z == gz2;
                    if (borda) d(x, PISO + 1, z, "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]");
                    else b(x, PISO + 1, z, Material.GRASS_BLOCK);
                }
            for (int x = gx1 + 1; x < gx2; x++)
                for (int z = gz1 + 1; z < gz2; z++) {
                    if (tipo(x, PISO + 1, z) != Material.GRASS_BLOCK || !tipo(x, PISO + 2, z).isAir()) continue;
                    int n = r.nextInt(10);
                    if (n < 4) b(x, PISO + 2, z, flores[r.nextInt(flores.length)]);
                    else if (n < 6) b(x, PISO + 2, z, Material.SHORT_GRASS);
                }
            cerejeira(gx1 + 5, gz1 + 5);
            for (int[] p : new int[][] {{gx1 + 1, gz1 + 1}, {gx2 - 1, gz1 + 1}, {gx1 + 1, gz2 - 1}, {gx2 - 1, gz2 - 1}}) {
                if (tipo(p[0], PISO + 1, p[1]) != Material.GRASS_BLOCK) continue;
                b(p[0], PISO + 2, p[1], Material.AIR);
                d(p[0], PISO + 2, p[1], "minecraft:dark_oak_fence[east=false,west=false,north=false,south=false,waterlogged=false]");
                d(p[0], PISO + 3, p[1], "minecraft:lantern[hanging=false,waterlogged=false]");
            }
        }
    }

    private void cerejeira(int x, int z) {
        if (tipo(x, PISO + 1, z) != Material.GRASS_BLOCK) return;
        for (int y = PISO + 2; y <= PISO + 6; y++) d(x, y, z, "minecraft:cherry_log[axis=y]");
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 4; dy <= 8; dy++) {
                    int raio = dy == 8 ? 1 : dy == 4 ? 2 : 3;
                    if (Math.abs(dx) > raio || Math.abs(dz) > raio) continue;
                    if (Math.abs(dx) == raio && Math.abs(dz) == raio && r.nextInt(3) > 0) continue;
                    if (dx == 0 && dz == 0 && dy <= 6) continue;
                    int bx = x + dx, by = PISO + dy, bz = z + dz;
                    if (tipo(bx, by, bz).isAir() || tipo(bx, by, bz) == Material.SHORT_GRASS || tipo(bx, by, bz).name().endsWith("_TULIP"))
                        d(bx, by, bz, "minecraft:cherry_leaves[persistent=true,distance=1,waterlogged=false]");
                }
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && tipo(x + dx, PISO + 1, z + dz) == Material.GRASS_BLOCK) {
                    b(x + dx, PISO + 2, z + dz, Material.AIR);
                    d(x + dx, PISO + 2, z + dz, "minecraft:pink_petals[facing=north,flower_amount=" + (1 + r.nextInt(4)) + "]");
                }
    }

    private void parapeito() {
        List<int[]> borda = new ArrayList<>();
        for (int x = X1; x <= X2; x++) {
            borda.add(new int[] {x, Z1});
            borda.add(new int[] {x, Z2});
        }
        for (int z = Z1 + 1; z < Z2; z++) {
            borda.add(new int[] {X1, z});
            borda.add(new int[] {X2, z});
        }
        for (int[] c : borda) {
            int x = c[0], z = c[1];
            if (tipo(x, PISO, z).isAir()) continue;
            boolean poste = ((x - X1) % 8 == 0 && (z == Z1 || z == Z2)) || ((z - Z1) % 8 == 0 && (x == X1 || x == X2))
                    || ((x == X1 || x == X2) && (z == Z1 || z == Z2));
            if (poste) {
                if (livre(x, PISO + 1, z)) d(x, PISO + 1, z, "minecraft:purpur_pillar[axis=y]");
                if (livre(x, PISO + 2, z)) d(x, PISO + 2, z, "minecraft:purpur_pillar[axis=y]");
                if (livre(x, PISO + 3, z)) d(x, PISO + 3, z, "minecraft:lantern[hanging=false,waterlogged=false]");
            } else {
                for (int y = PISO + 1; y <= PISO + 2; y++)
                    if (livre(x, y, z)) d(x, y, z, "minecraft:purple_stained_glass_pane[north=false,south=false,east=false,west=false,waterlogged=false]");
            }
        }
        // liga os paineis de vidro entre si e aos postes (para ficar uma grade continua)
        for (int[] c : borda)
            for (int y = PISO + 1; y <= PISO + 2; y++) {
                if (tipo(c[0], y, c[1]) != Material.PURPLE_STAINED_GLASS_PANE) continue;
                boolean emX = c[1] == Z1 || c[1] == Z2;
                d(c[0], y, c[1], "minecraft:purple_stained_glass_pane[north=" + !emX + ",south=" + !emX + ",east=" + emX + ",west=" + emX + ",waterlogged=false]");
            }
    }

    private void postesEBancos() {
        // postes com lanterna dos dois lados dos caminhos
        for (int k = 16; k <= 40; k += 8) {
            int[][] lugares = {{CX - 3, CZ - k}, {CX + 3, CZ - k}, {CX - 3, CZ + k}, {CX + 3, CZ + k},
                    {CX - k, CZ - 3}, {CX - k, CZ + 3}, {CX + k, CZ - 3}, {CX + k, CZ + 3}};
            for (int[] p : lugares) poste(p[0], p[1]);
        }
        // bancos virados para a cupula, nas diagonais do anel
        banco(CX + 9, CZ + 11, "south", true);
        banco(CX - 10, CZ + 11, "south", true);
        banco(CX + 9, CZ - 12, "north", true);
        banco(CX - 10, CZ - 12, "north", true);
        banco(CX + 11, CZ + 8, "east", false);
        banco(CX - 12, CZ + 8, "west", false);
        banco(CX + 11, CZ - 10, "east", false);
        banco(CX - 12, CZ - 10, "west", false);
    }

    private void poste(int x, int z) {
        if (!livre(x, PISO + 1, z) || tipo(x, PISO, z).isAir()) return;
        d(x, PISO + 1, z, "minecraft:purpur_pillar[axis=y]");
        poe(x, PISO + 2, z, "minecraft:purpur_pillar[axis=y]");
        poe(x, PISO + 3, z, "minecraft:end_rod[facing=up]");
    }

    /** Banco de 2 lugares; "costas" e para onde fica o encosto (longe da cupula). */
    private void banco(int x, int z, String costas, boolean emX) {
        for (int k = 0; k <= 1; k++) {
            int bx = emX ? x + k : x, bz = emX ? z : z + k;
            if (tipo(bx, PISO, bz).isAir()) continue;
            poe(bx, PISO + 1, bz, "minecraft:dark_oak_stairs[facing=" + costas + ",half=bottom,shape=straight,waterlogged=false]");
        }
    }

    private void cupula() {
        // o buraquinho do meio vira uma luz; tapete roxo em volta do ponto de nascer
        if (tipo(CX, PISO, CZ).isAir()) b(CX, PISO, CZ, Material.PEARLESCENT_FROGLIGHT);
        for (int x = CX - 3; x <= CX + 3; x++)
            for (int z = CZ - 3; z <= CZ + 3; z++) {
                double dd = Math.sqrt((x - CX) * (x - CX) + (z - CZ) * (z - CZ));
                if (dd > 3.2 || dd < 0.5) continue;
                if (tipo(x, PISO + 1, z).isAir() && tipo(x, PISO, z) == Material.CRYING_OBSIDIAN)
                    b(x, PISO + 1, z, dd > 2.3 ? Material.MAGENTA_CARPET : Material.PURPLE_CARPET);
            }
    }

    private boolean pedacoDeTotem(Material m) {
        return m == Material.AMETHYST_BLOCK || m == Material.END_ROD || m == Material.POLISHED_BLACKSTONE_SLAB;
    }

    /** Onde cada totem ficou nesta montagem (para nao encostar um no outro). */
    private final java.util.Map<String, int[]> lugares = new java.util.HashMap<>();

    private boolean chao(int x, int z, int raio) {
        for (int a = -raio; a <= raio; a++)
            for (int b = -raio; b <= raio; b++) if (tipo(x + a, PISO, z + b).isAir()) return false;
        return true;
    }

    private boolean cabe(Totem t, int x, int z) {
        if (x - 2 < X1 || x + 2 > X2 || z - 2 < Z1 || z + 2 > Z2) return false;
        if (!chao(x, z, 2)) return false;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++)
                for (int y = PISO + 1; y <= PISO + 3; y++) {
                    Material m = tipo(x + a, y, z + b);
                    if (!m.isAir() && !pedacoDeTotem(m)) return false;
                }
        for (Totem o : TOTENS) {
            if (o.id().equals(t.id())) continue;
            int[] l = lugares.get(o.id());
            double ox = l != null ? l[0] + 0.5 : o.x(), oz = l != null ? l[1] + 0.5 : o.z();
            if (Math.hypot(ox - (x + 0.5), oz - (z + 0.5)) < 5) return false;
        }
        return Math.hypot(x + 0.5 - CX, z + 0.5 - CZ) > 9; // fora da cupula
    }

    /** O lugar pedido, se tiver chao; senao o lugar livre com chao mais perto dele. */
    private int[] lugar(Totem t) {
        int x0 = (int) Math.floor(t.x()), z0 = (int) Math.floor(t.z());
        if (chao(x0, z0, 1)) return new int[] {x0, z0};
        for (int r = 1; r <= 30; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && cabe(t, x0 + dx, z0 + dz)) return new int[] {x0 + dx, z0 + dz};
        pl.getLogger().warning("Totem " + t.id() + ": sem lugar livre com chao perto de " + x0 + "," + z0);
        return null;
    }

    /** Tira pedacos de totem que ficaram no ar (fora da plataforma). */
    private void limparTotensNoAr() {
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++)
                if (tipo(x, PISO, z).isAir() && pedacoDeTotem(tipo(x, PISO + 1, z))) w.getBlockAt(x, PISO + 1, z).setType(Material.AIR, false);
    }

    /** Mapa da plataforma (para conferir): ' ' sem chao, '.' livre, '#' ocupado, letra = totem. */
    private void salvarMapa() {
        StringBuilder sb = new StringBuilder("x de " + X1 + " a " + X2 + ", z de " + Z1 + " a " + Z2 + " (cada linha e um z)\n");
        for (int z = Z1; z <= Z2; z++) {
            sb.append(String.format("%4d ", z));
            for (int x = X1; x <= X2; x++) {
                char c = tipo(x, PISO, z).isAir() ? ' ' : '.';
                if (c == '.') for (int y = PISO + 1; y <= PISO + 2; y++) if (!tipo(x, y, z).isAir()) c = '#';
                for (java.util.Map.Entry<String, int[]> e : lugares.entrySet())
                    if (e.getValue()[0] == x && e.getValue()[1] == z) c = Character.toUpperCase(e.getKey().charAt(0));
                sb.append(c);
            }
            sb.append('\n');
        }
        try {
            java.nio.file.Files.writeString(new java.io.File(pl.getDataFolder(), "mapa-spawn.txt").toPath(), sb.toString());
        } catch (java.io.IOException ignored) {
        }
    }

    private void totem(Totem t) {
        int[] lg = lugar(t);
        if (lg == null) return;
        lugares.put(t.id(), lg);
        int x = lg[0], z = lg[1];
        if (x != (int) Math.floor(t.x()) || z != (int) Math.floor(t.z()))
            pl.getLogger().info("Totem " + t.id() + " estava sem chao: foi para " + x + "," + z);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                piso(x + dx, z + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                boolean canto = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                if (dx == 0 && dz == 0) poe(x, PISO + 1, z, "minecraft:amethyst_block");
                else if (canto) poe(x + dx, PISO + 1, z + dz, "minecraft:end_rod[facing=up]");
                else poe(x + dx, PISO + 1, z + dz, "minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]");
            }
        // anel de quartzo em volta do totem
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) piso(x + dx, z + dz, Material.SMOOTH_QUARTZ);
        Location base = new Location(w, x + 0.5, PISO + 2, z + 0.5);
        ItemDisplay icone = w.spawn(base.clone().add(0, 1.3, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(t.icone()));
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(true);
            d.setViewRange(1.5f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(kEnfeite, PersistentDataType.BYTE, (byte) 1);
        });
        icones.add(icone);
        w.spawn(base.clone().add(0, 2.9, 0), TextDisplay.class, d -> {
            d.text(LoginLobby.txt(t.titulo() + "\n" + t.sub() + "\n§e» Clique para entrar «"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setPersistent(true);
            d.setViewRange(1.0f);
            d.getPersistentDataContainer().set(kEnfeite, PersistentDataType.BYTE, (byte) 1);
        });
        w.spawn(new Location(w, x + 0.5, PISO + 1, z + 0.5), Interaction.class, i -> {
            i.setInteractionWidth(2.6f);
            i.setInteractionHeight(3.6f);
            i.setResponsive(true);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(kEnfeite, PersistentDataType.BYTE, (byte) 1);
            i.getPersistentDataContainer().set(kTotem, PersistentDataType.STRING, t.id());
        });
    }

    private void letreiros() {
        // dentro da cupula, logo na frente de quem nasce
        texto(new Location(w, CX + 0.5, PISO + 3.6, CZ + 3.5), "§d§lBEM-VINDO AO CORONA SERVER!\n§fLá fora tem §eminigames§f, §ekits§f e as §eclasses§f.\n§7Clique nos totens para entrar.  §8|  §7/rtp explora o mundo", 1.0f);
        // nome bem grande em cima da cupula, para ver de longe
        texto(new Location(w, CX + 0.5, PISO + 18.5, CZ + 0.5), "§d§lCORONA SERVER", 4.0f);
    }

    private void texto(Location l, String s, float escala) {
        w.spawn(l, TextDisplay.class, d -> {
            d.text(LoginLobby.txt(s));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setPersistent(true);
            d.setViewRange(escala > 2 ? 4f : 1f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(escala, escala, escala), new AxisAngle4f()));
            d.getPersistentDataContainer().set(kEnfeite, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ================================================================== clique nos totens

    private void usarTotem(Player p, Entity e) {
        String id = e.getPersistentDataContainer().get(kTotem, PersistentDataType.STRING);
        if (id == null) return;
        for (Totem t : TOTENS) {
            if (!t.id().equals(id)) continue;
            if (!LoginLobby.logado(p)) return;
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            p.performCommand(t.comando());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clique(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction i) || !i.getPersistentDataContainer().has(kTotem, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) usarTotem(e.getPlayer(), i);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void soco(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (!(e.getAttacked() instanceof Interaction i) || !i.getPersistentDataContainer().has(kTotem, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        usarTotem(e.getPlayer(), i);
    }
}
