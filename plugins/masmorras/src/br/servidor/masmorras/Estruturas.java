package br.servidor.masmorras;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * Estruturas espalhadas pelo Mundo das Masmorras (uma a cada ~15 chunks): ruinas, torres de vigia, monolitos,
 * acampamentos abandonados, circulos de pedra, altares e, nos niveis altos, ossadas e torres quebradas. Tudo
 * procedural (nenhuma e igual a outra) e colocado junto com o chunk, sem travar o servidor.
 */
final class Estruturas extends BlockPopulator {

    private final GeradorMundo g;

    Estruturas(GeradorMundo g) {
        this.g = g;
    }

    /** Sorteio fixo do chunk: onde fica a estrutura e qual (0-99). null = chunk sem estrutura. Os Campos usam o mesmo sorteio. */
    record Escolha(int x, int z, int sorte, Random rnd) {}

    static Escolha escolher(long semente, int cx, int cz) {
        long h = (cx * 341873128712L + cz * 132897987541L) ^ semente;
        Random rnd = new Random(h);
        if (rnd.nextInt(15) != 0) return null;
        int x = (cx << 4) + 4 + rnd.nextInt(8), z = (cz << 4) + 4 + rnd.nextInt(8);
        double dist = Math.hypot(x, z);
        if (dist < GeradorMundo.LOBBY_RAIO + GeradorMundo.LOBBY_MISTURA + 30) return null;
        int sorte = rnd.nextInt(100);
        return new Escolha(x, z, sorte, rnd);
    }

    /** Que estrutura sai de um sorteio num nivel: ossada, torre_quebrada, ruina, torre, monolito, acampamento, circulo, altar. */
    static String tipo(int nivel, int sorte) {
        if (nivel >= 4 && sorte < 25) return "ossada";
        if (nivel >= 3 && sorte < 40) return "torre_quebrada";
        if (sorte < 30) return "ruina";
        if (sorte < 48) return "torre";
        if (sorte < 62) return "monolito";
        if (sorte < 78) return "acampamento";
        if (sorte < 90) return "circulo";
        return "altar";
    }

    @Override
    public void populate(WorldInfo info, Random random, int cx, int cz, LimitedRegion r) {
        Escolha esc = escolher(g.semente, cx, cz);
        if (esc == null) return;
        Random rnd = esc.rnd();
        int x = esc.x(), z = esc.z();
        double dist = Math.hypot(x, z);
        int y = r.getHighestBlockYAt(x, z);
        Material chao = r.getType(x, y - 1, z);
        if (chao == Material.WATER || chao == Material.LAVA || chao == Material.ICE || !chao.isSolid()) return;
        if (g.ehRio(x, z, dist)) return;
        // terreno muito inclinado nao serve
        int dy = 0;
        for (int[] o : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) dy = Math.max(dy, Math.abs(r.getHighestBlockYAt(x + o[0], z + o[1]) - y));
        if (dy > 4) return;
        int nivel = g.nivel(dist);
        switch (tipo(nivel, esc.sorte())) {
            case "ossada" -> ossada(r, rnd, x, y, z);
            case "torre_quebrada" -> torreQuebrada(r, rnd, x, y, z, nivel);
            case "ruina" -> ruina(r, rnd, x, y, z, nivel);
            case "torre" -> torreVigia(r, rnd, x, y, z, nivel);
            case "monolito" -> monolito(r, rnd, x, y, z);
            case "acampamento" -> acampamento(r, rnd, x, y, z);
            case "circulo" -> circuloDePedras(r, rnd, x, y, z);
            default -> altar(r, rnd, x, y, z);
        }
    }

    // ------------------------------------------------------------------ pecas

    private static Material tijolo(Random rnd) {
        int k = rnd.nextInt(10);
        return k < 5 ? Material.STONE_BRICKS : k < 8 ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS;
    }

    private static Material pedra(Random rnd) {
        int k = rnd.nextInt(10);
        return k < 5 ? Material.COBBLESTONE : k < 8 ? Material.MOSSY_COBBLESTONE : Material.STONE_BRICKS;
    }

    private void por(LimitedRegion r, int x, int y, int z, Material m) {
        if (r.isInRegion(x, y, z)) r.setType(x, y, z, m);
    }

    private void por(LimitedRegion r, int x, int y, int z, BlockData d) {
        if (r.isInRegion(x, y, z)) r.setBlockData(x, y, z, d);
    }

    /** Preenche o chao embaixo da peca (para nao ficar flutuando no morro). */
    private void alicerce(LimitedRegion r, int x, int z, int y, Material m) {
        for (int k = y - 1; k >= y - 6; k--) {
            if (!r.isInRegion(x, k, z)) return;
            Material t = r.getType(x, k, z);
            if (t.isSolid() && t != Material.SNOW) return;
            r.setType(x, k, z, m);
        }
    }

    private void ruina(LimitedRegion r, Random rnd, int x, int y, int z, int nivel) {
        int meia = 3 + rnd.nextInt(2);
        for (int dx = -meia; dx <= meia; dx++)
            for (int dz = -meia; dz <= meia; dz++) {
                boolean parede = Math.abs(dx) == meia || Math.abs(dz) == meia;
                int py = r.getHighestBlockYAt(x + dx, z + dz);
                if (!parede) {
                    if (rnd.nextInt(4) != 0) por(r, x + dx, py - 1, z + dz, rnd.nextInt(3) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                    if (rnd.nextInt(12) == 0) por(r, x + dx, py, z + dz, Material.COBWEB);
                    continue;
                }
                alicerce(r, x + dx, z + dz, py, Material.STONE_BRICKS);
                int alt = rnd.nextInt(6) == 0 ? 0 : 1 + rnd.nextInt(3 + nivel);
                for (int k = 0; k < alt; k++) por(r, x + dx, py + k, z + dz, tijolo(rnd));
                if (alt > 0 && rnd.nextInt(3) == 0) por(r, x + dx, py + alt, z + dz, Material.STONE_BRICK_SLAB);
            }
        // porta (um vao) e uma lanterna caida
        int lado = rnd.nextInt(4);
        int px = lado == 0 ? -meia : lado == 1 ? meia : rnd.nextInt(meia * 2 - 1) - meia + 1;
        int pz = lado == 2 ? -meia : lado == 3 ? meia : rnd.nextInt(meia * 2 - 1) - meia + 1;
        int py = r.getHighestBlockYAt(x + px, z + pz);
        por(r, x + px, py, z + pz, Material.AIR);
        por(r, x + px, py + 1, z + pz, Material.AIR);
        por(r, x, y, z, Material.LANTERN);
    }

    private void torreVigia(LimitedRegion r, Random rnd, int x, int y, int z, int nivel) {
        int alt = 7 + rnd.nextInt(4) + nivel;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 6) continue;
                boolean borda = dx * dx + dz * dz >= 4;
                int py = r.getHighestBlockYAt(x + dx, z + dz);
                alicerce(r, x + dx, z + dz, py, Material.COBBLESTONE);
                por(r, x + dx, py - 1, z + dz, Material.STONE_BRICKS);
                for (int k = 0; k < alt; k++) {
                    if (borda) {
                        boolean janela = k > 2 && k % 3 == 0 && (dx == 0 || dz == 0);
                        por(r, x + dx, y + k, z + dz, janela ? Material.AIR : pedra(rnd));
                    } else por(r, x + dx, y + k, z + dz, Material.AIR);
                }
                // piso do topo e ameias
                por(r, x + dx, y + alt, z + dz, borda ? Material.STONE_BRICK_SLAB : Material.STONE_BRICKS);
                if (borda && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) por(r, x + dx, y + alt, z + dz, Material.STONE_BRICK_WALL);
            }
        // porta ao sul, lanterna dentro e no topo
        por(r, x, y, z + 2, Material.AIR);
        por(r, x, y + 1, z + 2, Material.AIR);
        por(r, x, y, z, Material.LANTERN);
        por(r, x, y + alt + 1, z, Material.LANTERN);
        // escada de mao na parede norte
        BlockData esc = Material.LADDER.createBlockData();
        if (esc instanceof org.bukkit.block.data.Directional d) d.setFacing(BlockFace.SOUTH);
        for (int k = 0; k < alt; k++) por(r, x, y + k, z - 1, esc);
        por(r, x, y + alt, z - 1, Material.AIR);
    }

    private void monolito(LimitedRegion r, Random rnd, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int py = r.getHighestBlockYAt(x + dx, z + dz);
                alicerce(r, x + dx, z + dz, py, Material.DEEPSLATE_BRICKS);
                por(r, x + dx, py - 1, z + dz, (dx == 0 && dz == 0) ? Material.CHISELED_DEEPSLATE : Material.DEEPSLATE_TILES);
            }
        int alt = 5 + rnd.nextInt(4);
        for (int k = 0; k < alt; k++) por(r, x, y + k, z, k == alt / 2 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
        por(r, x, y + alt, z, Material.SOUL_SOIL);
        por(r, x, y + alt + 1, z, Material.SOUL_FIRE);
        for (int[] o : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            int py = r.getHighestBlockYAt(x + o[0], z + o[1]);
            por(r, x + o[0], py, z + o[1], Material.DEEPSLATE_BRICK_WALL);
            por(r, x + o[0], py + 1, z + o[1], Material.SOUL_LANTERN);
        }
    }

    private void acampamento(LimitedRegion r, Random rnd, int x, int y, int z) {
        por(r, x, y - 1, z, Material.COBBLESTONE);
        por(r, x, y, z, rnd.nextBoolean() ? Material.CAMPFIRE : Material.SOUL_CAMPFIRE);
        // troncos deitados em volta da fogueira
        BlockData lx = Material.OAK_LOG.createBlockData(), lz = Material.OAK_LOG.createBlockData();
        if (lx instanceof org.bukkit.block.data.Orientable o) o.setAxis(org.bukkit.Axis.X);
        if (lz instanceof org.bukkit.block.data.Orientable o) o.setAxis(org.bukkit.Axis.Z);
        for (int k = -1; k <= 1; k++) {
            por(r, x + k, r.getHighestBlockYAt(x + k, z - 2), z - 2, lx);
            por(r, x - 2, r.getHighestBlockYAt(x - 2, z + k), z + k, lz);
        }
        // tenda: la com telhado de escadas
        int tx = x + 3, tz = z + 1;
        Material la = switch (rnd.nextInt(4)) {
            case 0 -> Material.WHITE_WOOL;
            case 1 -> Material.BROWN_WOOL;
            case 2 -> Material.GREEN_WOOL;
            default -> Material.RED_WOOL;
        };
        int ty = r.getHighestBlockYAt(tx, tz);
        for (int dz = -1; dz <= 1; dz++) {
            alicerce(r, tx - 1, tz + dz, ty, Material.DIRT);
            alicerce(r, tx + 1, tz + dz, ty, Material.DIRT);
            por(r, tx - 1, ty, tz + dz, la);
            por(r, tx + 1, ty, tz + dz, la);
            por(r, tx, ty, tz + dz, Material.AIR);
            por(r, tx, ty + 1, tz + dz, la);
            por(r, tx - 1, ty + 1, tz + dz, escada(Material.SPRUCE_STAIRS, BlockFace.EAST));
            por(r, tx + 1, ty + 1, tz + dz, escada(Material.SPRUCE_STAIRS, BlockFace.WEST));
        }
        por(r, tx, ty, tz - 2, Material.BARREL);
        por(r, x + 2, r.getHighestBlockYAt(x + 2, z - 2), z - 2, Material.LANTERN);
    }

    private static BlockData escada(Material m, BlockFace face) {
        BlockData d = m.createBlockData();
        if (d instanceof Stairs s) {
            s.setFacing(face);
            s.setHalf(Bisected.Half.BOTTOM);
        }
        return d;
    }

    private void circuloDePedras(LimitedRegion r, Random rnd, int x, int y, int z) {
        int n = 6 + rnd.nextInt(3), raio = 4 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int px = x + (int) Math.round(Math.cos(a) * raio), pz = z + (int) Math.round(Math.sin(a) * raio);
            int py = r.getHighestBlockYAt(px, pz);
            alicerce(r, px, pz, py, Material.STONE);
            int alt = 2 + rnd.nextInt(2);
            for (int k = 0; k < alt; k++) por(r, px, py + k, pz, rnd.nextInt(4) == 0 ? Material.MOSSY_COBBLESTONE : rnd.nextBoolean() ? Material.ANDESITE : Material.STONE);
            if (rnd.nextInt(3) == 0) por(r, px, py + alt, pz, Material.STONE_SLAB);
        }
        por(r, x, y - 1, z, Material.MOSSY_STONE_BRICKS);
        por(r, x, y, z, Material.MOSSY_STONE_BRICK_SLAB);
    }

    private void altar(LimitedRegion r, Random rnd, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int py = r.getHighestBlockYAt(x + dx, z + dz);
                alicerce(r, x + dx, z + dz, py, Material.CALCITE);
                por(r, x + dx, py - 1, z + dz, Material.POLISHED_DIORITE);
                por(r, x + dx, py, z + dz, (dx == 0 && dz == 0) ? Material.AMETHYST_BLOCK : Material.CALCITE);
            }
        por(r, x, y + 1, z, Material.AMETHYST_CLUSTER);
        for (int[] o : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) por(r, x + o[0], y + 1, z + o[1], Material.CANDLE);
    }

    /** Nivel 4: ossada gigante (costelas de ossos). */
    private void ossada(LimitedRegion r, Random rnd, int x, int y, int z) {
        boolean eixoX = rnd.nextBoolean();
        int comp = 7 + rnd.nextInt(5);
        BlockData osso = Material.BONE_BLOCK.createBlockData();
        if (osso instanceof org.bukkit.block.data.Orientable o) o.setAxis(eixoX ? org.bukkit.Axis.X : org.bukkit.Axis.Z);
        BlockData ossoY = Material.BONE_BLOCK.createBlockData();
        // espinha
        for (int k = -comp / 2; k <= comp / 2; k++) {
            int px = eixoX ? x + k : x, pz = eixoX ? z : z + k;
            por(r, px, r.getHighestBlockYAt(px, pz), pz, osso);
        }
        // costelas (arcos)
        for (int k = -comp / 2 + 1; k <= comp / 2 - 1; k += 2) {
            int alt = 3 + rnd.nextInt(3);
            for (int lado = -1; lado <= 1; lado += 2) {
                for (int h = 1; h <= alt; h++) {
                    int afasta = Math.min(h, alt - 1) + 1;
                    int px = eixoX ? x + k : x + lado * afasta, pz = eixoX ? z + lado * afasta : z + k;
                    por(r, px, y + h, pz, ossoY);
                }
            }
        }
        // cranio: bloco de ossos com olhos de carvao
        int cx = eixoX ? x + comp / 2 + 2 : x, cz = eixoX ? z : z + comp / 2 + 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int h = 0; h < 2; h++) por(r, cx + dx, y + h, cz + dz, ossoY);
        por(r, cx + (eixoX ? 2 : -1), y + 1, cz + (eixoX ? -1 : 2), Material.COAL_BLOCK);
        por(r, cx + (eixoX ? 2 : 1), y + 1, cz + (eixoX ? 1 : 2), Material.COAL_BLOCK);
    }

    /** Nivel 3 e 4: torre alta quebrada (parte caiu). */
    private void torreQuebrada(LimitedRegion r, Random rnd, int x, int y, int z, int nivel) {
        int alt = 12 + rnd.nextInt(8);
        int quebra = alt / 2 + rnd.nextInt(alt / 2);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.4) continue;
                boolean borda = d > 2.4;
                int py = r.getHighestBlockYAt(x + dx, z + dz);
                alicerce(r, x + dx, z + dz, py, Material.DEEPSLATE_BRICKS);
                for (int k = 0; k < alt; k++) {
                    if (!borda) {
                        por(r, x + dx, y + k, z + dz, k % 5 == 0 && k > 0 ? Material.DARK_OAK_PLANKS : Material.AIR);
                        continue;
                    }
                    // acima da quebra, so sobram pedacos (lado que nao caiu)
                    if (k > quebra && (dx + dz > 0 || rnd.nextInt(3) == 0)) continue;
                    Material m = rnd.nextInt(6) == 0 ? Material.CRACKED_DEEPSLATE_BRICKS : rnd.nextInt(5) == 0 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE_BRICKS;
                    boolean janela = k > 2 && k % 4 == 0 && (dx == 0 || dz == 0);
                    por(r, x + dx, y + k, z + dz, janela ? Material.AIR : m);
                }
            }
        por(r, x, y, z + 3, Material.AIR);
        por(r, x, y + 1, z + 3, Material.AIR);
        por(r, x, y, z, Material.SOUL_LANTERN);
        // escombros em volta
        for (int i = 0; i < 10 + nivel * 3; i++) {
            int px = x + rnd.nextInt(13) - 6, pz = z + rnd.nextInt(13) - 6;
            if (Math.hypot(px - x, pz - z) < 4) continue;
            int py = r.getHighestBlockYAt(px, pz);
            por(r, px, py, pz, rnd.nextBoolean() ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE_BRICK_SLAB);
        }
    }
}
