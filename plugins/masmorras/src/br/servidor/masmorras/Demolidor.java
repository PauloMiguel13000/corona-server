package br.servidor.masmorras;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Desmancha uma masmorra antiga devolvendo o terreno: cada bloco dela vira o bloco que esta na mesma altura na coluna
 * mais perto de fora da masmorra (pedra, terra, grama, ar...). Se essa coluna tiver algo que nao e terreno (agua,
 * construcao), usa pedra/netherrack abaixo do chao e ar acima. No End, o que nao e terreno vira ar.
 */
final class Demolidor {

    private Demolidor() {}

    static final int ALCANCE = 72;

    /** Blocos de terreno que podem ser copiados. */
    static final Set<String> TERRENO = new HashSet<>();

    static {
        for (String n : ("air cave_air stone deepslate tuff calcite granite diorite andesite dirt coarse_dirt rooted_dirt grass_block podzol mycelium "
                + "mud clay gravel sand red_sand sandstone red_sandstone terracotta white_terracotta orange_terracotta yellow_terracotta brown_terracotta "
                + "red_terracotta light_gray_terracotta snow_block packed_ice powder_snow moss_block dripstone_block smooth_basalt "
                + "netherrack basalt blackstone soul_sand soul_soil magma_block crimson_nylium warped_nylium nether_wart_block warped_wart_block "
                + "end_stone obsidian").split(" "))
            TERRENO.add(n);
    }

    static boolean terreno(Material m) {
        if (m == null) return false;
        String n = m.name().toLowerCase(Locale.ROOT);
        return TERRENO.contains(n) || n.endsWith("_ore");
    }

    static String nome(Material m) {
        return "minecraft:" + m.name().toLowerCase(Locale.ROOT);
    }

    static long coluna(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /**
     * O que colocar em cada bloco da masmorra antiga. Inclui as caixas protegidas e, na torre, o alicerce (blocos da
     * parede do tipo embaixo da torre).
     */
    static Map<Long, String> plano(Masmorra m, Destino d) {
        Set<Long> pos = new HashSet<>();
        Set<Long> pegada = new HashSet<>();
        for (Caixa c : m.protecao) {
            for (int x = c.x1(); x <= c.x2(); x++)
                for (int z = c.z1(); z <= c.z2(); z++) {
                    pegada.add(coluna(x, z));
                    for (int y = Math.max(c.y1(), d.minY()); y <= Math.min(c.y2(), d.maxY()); y++) pos.add(Construtor.chave(x, y, z));
                }
        }
        if (m.tipo.estilo == Tipo.Estilo.TORRE && m.tipo.onde != Tipo.Onde.END) {
            Set<Material> parede = new HashSet<>();
            for (String b : m.tipo.parede) {
                Material mt = Material.matchMaterial(base(b));
                if (mt != null) parede.add(mt);
            }
            for (Caixa c : m.protecao)
                for (int x = c.x1(); x <= c.x2(); x++)
                    for (int z = c.z1(); z <= c.z2(); z++)
                        for (int y = c.y1() - 1; y >= c.y1() - 41 && y >= d.minY(); y--) {
                            if (!parede.contains(d.tipo(x, y, z))) break;
                            pos.add(Construtor.chave(x, y, z));
                        }
        }
        Map<Long, String> r = new LinkedHashMap<>();
        Map<Long, Integer> chaoCache = new java.util.HashMap<>();
        for (long k : pos) {
            int x = Construtor.cx(k), y = Construtor.cy(k), z = Construtor.cz(k);
            java.util.List<int[]> refs = referencias(x, z, pegada, 12);
            String b = null;
            for (int[] ref : refs) {
                Material mt = d.tipo(ref[0], y, ref[1]);
                if (terreno(mt)) {
                    b = nome(mt);
                    break;
                }
            }
            if (b == null) {
                int chao = Integer.MAX_VALUE;
                if (!refs.isEmpty()) {
                    int[] ref = refs.get(0);
                    long ck = coluna(ref[0], ref[1]);
                    Integer c = chaoCache.get(ck);
                    if (c == null) {
                        c = d.chao(ref[0], ref[1]);
                        chaoCache.put(ck, c);
                    }
                    chao = c;
                }
                b = enchimento(m.tipo.onde, y, chao);
            }
            r.put(k, b);
        }
        return r;
    }

    /** As colunas fora da masmorra mais perto (andando nas 4 direcoes), da mais perto para a mais longe. */
    static java.util.List<int[]> referencias(int x, int z, Set<Long> pegada, int quantas) {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        for (int k = 1; k <= ALCANCE && l.size() < quantas; k++) {
            int[][] c = {{x + k, z}, {x - k, z}, {x, z + k}, {x, z - k}};
            for (int[] q : c) if (!pegada.contains(coluna(q[0], q[1])) && l.size() < quantas) l.add(q);
        }
        return l;
    }

    /** Coluna mais perto fora da masmorra (andando nas 4 direcoes). */
    static int[] referencia(int x, int z, Set<Long> pegada) {
        for (int k = 1; k <= ALCANCE; k++) {
            if (!pegada.contains(coluna(x + k, z))) return new int[] {x + k, z};
            if (!pegada.contains(coluna(x - k, z))) return new int[] {x - k, z};
            if (!pegada.contains(coluna(x, z + k))) return new int[] {x, z + k};
            if (!pegada.contains(coluna(x, z - k))) return new int[] {x, z - k};
        }
        return null;
    }

    static String enchimento(Tipo.Onde onde, int y, int chao) {
        return switch (onde) {
            case NORMAL -> y > chao ? Construtor.AR : y < 0 ? "minecraft:deepslate" : "minecraft:stone";
            case NETHER -> y > chao ? Construtor.AR : "minecraft:netherrack";
            case END -> Construtor.AR;
        };
    }

    static String base(String b) {
        int i = b.indexOf('[');
        String n = i >= 0 ? b.substring(0, i) : b;
        return n.replace("minecraft:", "");
    }
}
