package br.servidor.extras;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;

/**
 * "Ceu" dos lobbies (09/11): da altura a uma praca que era so um disco chato. Tudo e bloco parado (nenhuma entidade,
 * nenhuma agua, nada que faca conta), so entra onde ha AR e nunca troca bloco que ja existe:
 *  - 8 torres na borda, entre os portais, com braseiro aceso no topo e estandarte virado para o centro;
 *  - uma coroa flutuando bem acima do farol do meio (o feixe passa por dentro);
 *  - ilhotas flutuando em volta, em alturas diferentes, com arvore, ruina ou lanterna pendurada.
 * Roda uma vez por lugar (marca nome-ceu-vN.txt). Tirar a marca refaz (so completa o que faltar).
 */
final class Ceu {

    static final int VERSAO = 1;

    /** Cores do lugar: bloco de destaque, estandarte, folha das arvores, tronco, fogo do braseiro, joia da coroa. */
    record Tema(String destaque, String estandarte, String folha, String tronco, String fogo, String joia, String flor) {}

    static final Tema VERMELHO = new Tema("minecraft:red_concrete", "red", "minecraft:cherry_leaves", "minecraft:cherry_log",
            "minecraft:campfire", "minecraft:red_stained_glass", "minecraft:poppy");
    static final Tema JOGOS = new Tema("minecraft:red_concrete", "red", "minecraft:flowering_azalea_leaves", "minecraft:oak_log",
            "minecraft:campfire", "minecraft:red_stained_glass", "minecraft:orange_tulip");

    private final World w;
    private final int cx, cz, piso, raio;
    private final Tema t;
    private final Random rnd;
    private int postos;

    private Ceu(World w, int cx, int cz, int piso, int raio, Tema t, long semente) {
        this.w = w;
        this.cx = cx;
        this.cz = cz;
        this.piso = piso;
        this.raio = raio;
        this.t = t;
        this.rnd = new Random(semente);
    }

    /** Faz o ceu do lugar se ainda nao foi feito. limite = ate onde (em blocos do centro) pode ir uma ilhota. */
    static void fazer(Extras pl, String nome, World w, int cx, int cz, int piso, int raio, int limite, Tema t, boolean refazer) {
        if (w == null || !pl.getConfig().getBoolean("ceu-dos-lobbies", true)) return;
        File feito = new File(pl.getDataFolder(), nome + "-ceu-v" + VERSAO + ".txt");
        if (feito.exists() && !refazer) return;
        try {
            for (int x = (cx - limite) >> 4; x <= (cx + limite) >> 4; x++)
                for (int z = (cz - limite) >> 4; z <= (cz + limite) >> 4; z++) w.getChunkAt(x, z).load(true);
            Ceu c = new Ceu(w, cx, cz, piso, raio, t, nome.hashCode() * 31L + VERSAO);
            int torres = c.torres();
            c.coroa();
            int ilhas = c.ilhotas(limite);
            Files.writeString(feito.toPath(), "feito em " + new java.util.Date() + ": " + torres + " torres, " + ilhas + " ilhotas, " + c.postos + " blocos\n");
            pl.getLogger().info("Ceu do " + nome + ": " + torres + " torres, coroa e " + ilhas + " ilhotas (" + c.postos + " blocos).");
        } catch (Throwable e) {
            pl.getLogger().warning("Ceu do " + nome + " nao terminou: " + e);
        }
    }

    // ------------------------------------------------------------------ blocos

    /** Ar de verdade ou a luz invisivel que os lobbies usam (pode construir em cima dela). */
    private static boolean vazio(Block b) {
        return b.getType().isAir() || b.getType() == org.bukkit.Material.LIGHT;
    }

    private boolean ar(int x, int y, int z) {
        return vazio(w.getBlockAt(x, y, z));
    }

    /** So coloca onde ha ar (nunca troca o que ja existe). */
    private void por(int x, int y, int z, String b) {
        Block bl = w.getBlockAt(x, y, z);
        if (!vazio(bl)) return;
        bl.setBlockData(Bukkit.createBlockData(b), false);
        postos++;
    }

    // ------------------------------------------------------------------ torres da borda

    private int torres() {
        int n = 0;
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int tx = cx + (int) Math.round(Math.sin(a) * (raio - 3.5)), tz = cz - (int) Math.round(Math.cos(a) * (raio - 3.5));
            if (torre(tx, tz)) n++;
        }
        return n;
    }

    private boolean torre(int tx, int tz) {
        // precisa de chao embaixo e de espaco livre em cima (nada de totem, portal ou letreiro ali)
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (ar(tx + dx, piso, tz + dz)) return false;
                for (int y = piso + 1; y <= piso + 14; y++) if (!ar(tx + dx, y, tz + dz)) return false;
            }
        for (Entity e : w.getNearbyEntities(new org.bukkit.Location(w, tx + 0.5, piso + 3, tz + 0.5), 3.5, 6, 3.5))
            if (e instanceof Display || e instanceof Interaction) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                boolean canto = dx != 0 && dz != 0;
                por(tx + dx, piso + 1, tz + dz, "minecraft:polished_blackstone_bricks");
                if (!canto) por(tx + dx, piso + 2, tz + dz, dx == 0 && dz == 0 ? "minecraft:polished_blackstone_bricks"
                        : "minecraft:polished_blackstone_brick_stairs[facing=" + virado(-dx, -dz) + ",half=bottom,shape=straight,waterlogged=false]");
            }
        for (int y = piso + 3; y <= piso + 10; y++) por(tx, y, tz, y == piso + 6 ? t.destaque() : "minecraft:quartz_pillar[axis=y]");
        // capitel: laje em volta e braseiro no meio
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                por(tx + dx, piso + 10, tz + dz, "minecraft:polished_blackstone_brick_slab[type=top,waterlogged=false]");
            }
        por(tx, piso + 11, tz, "minecraft:polished_blackstone_bricks");
        por(tx, piso + 12, tz, t.fogo() + "[lit=true,signal_fire=false,facing=north,waterlogged=false]");
        // estandarte virado para o centro (dois, um em cima do outro, para ficar comprido)
        int ddx = cx - tx, ddz = cz - tz;
        int bx = Math.abs(ddx) >= Math.abs(ddz) ? Integer.signum(ddx) : 0, bz = bx == 0 ? Integer.signum(ddz) : 0;
        por(tx + bx, piso + 9, tz + bz, "minecraft:" + t.estandarte() + "_wall_banner[facing=" + virado(bx, bz) + "]");
        por(tx + bx, piso + 5, tz + bz, "minecraft:white_wall_banner[facing=" + virado(bx, bz) + "]");
        return true;
    }

    private static String virado(int dx, int dz) {
        return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
    }

    // ------------------------------------------------------------------ coroa em cima do farol

    private void coroa() {
        int y = piso + 22;
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < 6.5 || d >= 7.5) continue;
                por(cx + x, y - 1, cz + z, t.destaque());
                por(cx + x, y, cz + z, "minecraft:gold_block");
            }
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(45 * k);
            int x = cx + (int) Math.round(Math.sin(a) * 7), z = cz - (int) Math.round(Math.cos(a) * 7);
            int alto = k % 2 == 0 ? 4 : 2;
            for (int i = 1; i <= alto; i++) por(x, y + i, z, "minecraft:gold_block");
            por(x, y + alto + 1, z, t.joia());
            if (k % 2 == 0) {
                // lanterna pendurada embaixo das pontas grandes
                por(x, y - 2, z, "minecraft:iron_chain[axis=y,waterlogged=false]");
                por(x, y - 3, z, "minecraft:iron_chain[axis=y,waterlogged=false]");
                por(x, y - 4, z, "minecraft:lantern[hanging=true,waterlogged=false]");
            }
        }
    }

    // ------------------------------------------------------------------ ilhotas em volta

    private int ilhotas(int limite) {
        int n = 0;
        int quantas = 7;
        for (int k = 0; k < quantas; k++) {
            double a = Math.toRadians(360.0 / quantas * k + 12 + rnd.nextInt(14));
            int r = 4 + rnd.nextInt(3);
            double dist = Math.min(limite - r - 1, raio + 9 + r + rnd.nextInt(5));
            if (dist < raio + r + 3) continue; // nao cabe sem encostar na praca
            int ix = cx + (int) Math.round(Math.sin(a) * dist), iz = cz - (int) Math.round(Math.cos(a) * dist);
            int iy = piso - 5 + rnd.nextInt(16);
            if (!livre(ix, iy, iz, r)) continue;
            ilhota(ix, iy, iz, r, k);
            n++;
        }
        return n;
    }

    private boolean livre(int ix, int iy, int iz, int r) {
        for (int x = -r - 1; x <= r + 1; x++)
            for (int z = -r - 1; z <= r + 1; z++)
                for (int y = iy - r * 2; y <= iy + 9; y++) if (!ar(ix + x, y, iz + z)) return false;
        return true;
    }

    private void ilhota(int ix, int iy, int iz, int r, int tipo) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z) + (rnd.nextDouble() - 0.5) * 0.9;
                if (d > r) continue;
                int fundo = (int) Math.round((1 - d / r) * r * 1.7 + rnd.nextInt(2));
                por(ix + x, iy, iz + z, "minecraft:grass_block[snowy=false]");
                for (int i = 1; i <= fundo; i++) {
                    double f = (double) i / Math.max(1, fundo);
                    String b = i <= 1 ? "minecraft:dirt" : f > 0.8 ? "minecraft:deepslate[axis=y]" : rnd.nextInt(4) == 0 ? "minecraft:andesite"
                            : rnd.nextInt(5) == 0 ? "minecraft:cobblestone" : "minecraft:stone";
                    por(ix + x, iy - i, iz + z, b);
                }
                // matinho e flor
                if (d < r - 1 && rnd.nextInt(5) == 0) por(ix + x, iy + 1, iz + z, rnd.nextInt(3) == 0 ? t.flor() : "minecraft:short_grass");
            }
        switch (tipo % 3) {
            case 0 -> arvore(ix, iy + 1, iz, 4 + rnd.nextInt(2));
            case 1 -> ruina(ix, iy + 1, iz);
            default -> {
                arvore(ix + 1, iy + 1, iz, 3 + rnd.nextInt(2));
                por(ix - 2, iy + 1, iz - 1, "minecraft:lantern[hanging=false,waterlogged=false]");
            }
        }
        // lanterna pendurada embaixo da ponta
        int ponta = (int) Math.round(r * 1.7) + 1;
        for (int y = iy - ponta - 3; y <= iy - 1; y++) {
            if (!ar(ix, y + 1, iz) && ar(ix, y, iz)) {
                por(ix, y, iz, "minecraft:iron_chain[axis=y,waterlogged=false]");
                por(ix, y - 1, iz, "minecraft:lantern[hanging=true,waterlogged=false]");
                break;
            }
        }
    }

    private void arvore(int x, int y, int z, int alto) {
        for (int i = 0; i < alto; i++) {
            Block b = w.getBlockAt(x, y + i, z);
            if (!b.getType().isAir() && !b.getType().name().contains("GRASS") && !b.getType().name().contains("TULIP") && !b.getType().name().contains("POPPY")) continue;
            b.setBlockData(Bukkit.createBlockData(t.tronco() + "[axis=y]"), false);
            postos++;
        }
        int topo = y + alto;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -2; dy <= 1; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6);
                    if (d > 2.4 || (d > 1.9 && rnd.nextInt(3) == 0)) continue;
                    por(x + dx, topo + dy, z + dz, t.folha() + "[persistent=true,distance=1,waterlogged=false]");
                }
    }

    private void ruina(int x, int y, int z) {
        int[][] p = {{-2, -1, 4}, {2, -1, 2}, {-1, 2, 3}, {2, 2, 1}};
        for (int[] c : p) {
            for (int i = 0; i < c[2]; i++) por(x + c[0], y + i, z + c[1], i == c[2] - 1 && c[2] > 2 ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_pillar[axis=y]");
        }
        por(x, y, z, "minecraft:polished_blackstone_bricks");
        por(x, y + 1, z, t.destaque());
        por(x, y + 2, z, "minecraft:lantern[hanging=false,waterlogged=false]");
        por(x - 1, y, z + 1, "minecraft:polished_blackstone_brick_slab[type=bottom,waterlogged=false]");
        por(x + 1, y, z, "minecraft:quartz_slab[type=bottom,waterlogged=false]");
    }
}
