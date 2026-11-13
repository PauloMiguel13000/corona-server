package br.servidor.mineracao;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Uma mina: um poco quadrado (33x33, 30 de fundo) cercado de bedrock, com uma borda larga em volta para ficar em pe.
 * O miolo e preenchido com a mistura de blocos (pesos) e se refaz sozinho.
 */
final class Mina {
    static final int RAIO = 16;         // miolo: centro +- 16 = 33 blocos (13/11: o Paulo pediu minas maiores)
    static final int FUNDO = 30;        // altura do miolo
    static final int TOPO = 99;         // ultima camada do miolo
    static final int BORDA = 4;         // largura da borda para andar em volta

    final String id, nome;
    final int rank;            // rank minimo (0 = A)
    final int cx;              // centro x (z = 0)
    final boolean pvp;
    final Material icone;
    final LinkedHashMap<Material, Integer> mistura;
    private final int soma;

    int quebrados;             // blocos quebrados desde o ultimo reset
    long ultimoReset;
    boolean refazendo;

    Mina(String id, String nome, int rank, int cx, boolean pvp, Material icone, Object... pares) {
        this.id = id; this.nome = nome; this.rank = rank; this.cx = cx; this.pvp = pvp; this.icone = icone;
        mistura = new LinkedHashMap<>();
        int s = 0;
        for (int i = 0; i < pares.length; i += 2) { mistura.put((Material) pares[i], (Integer) pares[i + 1]); s += (Integer) pares[i + 1]; }
        soma = s;
    }

    static int volume() { return (2 * RAIO + 1) * (2 * RAIO + 1) * FUNDO; }

    Material sortear() {
        int r = ThreadLocalRandom.current().nextInt(soma);
        for (Map.Entry<Material, Integer> e : mistura.entrySet()) { r -= e.getValue(); if (r < 0) return e.getKey(); }
        return Material.STONE;
    }

    /** Dentro do miolo (onde se minera). */
    boolean noMiolo(Block b) {
        int x = b.getX() - cx, z = b.getZ(), y = b.getY();
        return Math.abs(x) <= RAIO && Math.abs(z) <= RAIO && y <= TOPO && y > TOPO - FUNDO;
    }

    /** Area toda da mina (miolo + paredes + borda), para PvP e para tirar gente na hora do reset. */
    boolean naArea(Location l) {
        int lim = RAIO + 1 + BORDA;
        return Math.abs(l.getBlockX() - cx) <= lim && Math.abs(l.getBlockZ()) <= lim && l.getY() > TOPO - FUNDO - 3 && l.getY() < TOPO + 30;
    }

    boolean dentroDoPoco(Location l) {
        return Math.abs(l.getBlockX() - cx) <= RAIO && Math.abs(l.getBlockZ()) <= RAIO && l.getY() < TOPO + 2 && l.getY() > TOPO - FUNDO - 2;
    }

    Location chegada(World w) {
        Location l = new Location(w, cx + 0.5, TOPO + 2, -(RAIO + 3) + 0.5);
        l.setYaw(0); l.setPitch(30);
        return l;
    }

    /** Paredes, chao, borda e placas: so precisa uma vez (e confere nas proximas). */
    void construirMoldura(World w) {
        int r = RAIO + 1, yChao = TOPO - FUNDO;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                boolean parede = Math.abs(x) == r || Math.abs(z) == r;
                if (parede) for (int y = yChao; y <= TOPO; y++) w.getBlockAt(cx + x, y, z).setType(Material.BEDROCK, false);
                w.getBlockAt(cx + x, yChao, z).setType(Material.BEDROCK, false);
            }
        Material piso = pvp ? Material.RED_NETHER_BRICKS : Material.POLISHED_ANDESITE;
        Material quina = pvp ? Material.CRYING_OBSIDIAN : Material.SMOOTH_STONE;
        int lim = r + BORDA;
        for (int x = -lim; x <= lim; x++)
            for (int z = -lim; z <= lim; z++) {
                if (Math.abs(x) < r && Math.abs(z) < r) continue;
                boolean beira = Math.abs(x) == lim || Math.abs(z) == lim;
                Block b = w.getBlockAt(cx + x, TOPO, z);
                if (Math.abs(x) == r || Math.abs(z) == r) b.setType(Material.BEDROCK, false);
                else b.setType(beira ? quina : piso, false);
                if (beira && (x + z) % 4 == 0) w.getBlockAt(cx + x, TOPO + 1, z).setType(Material.LANTERN, false);
                for (int y = TOPO - 3; y < TOPO; y++) if (Math.abs(x) > r || Math.abs(z) > r) w.getBlockAt(cx + x, y, z).setType(Material.STONE_BRICKS, false);
            }
    }
}
