package br.servidor.slender;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Visao escura o tempo todo: uma camada de vidro escuro no limite de altura do mundo, cobrindo a floresta, o lobby
 * e uma folga em volta. Ela segura a luz do ceu (a lua), entao no chao fica escuro como caverna sem tocha.
 * Sao blocos de verdade: vale no Java e no Bedrock e nao pisca (o efeito Escuridao do Minecraft pulsa sozinho
 * a cada 4 segundos, por isso ele fica so para as piscadas). Ninguem alcanca o teto (modo aventura, sem voo).
 * As lanternas do lobby, os arbustos de vagalume e as folhas (moldura que brilha) continuam aparecendo no escuro.
 */
final class Teto {

    /** Borda do mundo: centro (TAM/2, TAM/2 + 10), tamanho TAM + 90. Folga de 16 para a luz do ceu nao entrar pelos lados. */
    static final int MEIA = (Floresta.TAM + 90) / 2;
    static final int FOLGA = 16;
    static final int X1 = Floresta.TAM / 2 - MEIA - FOLGA;
    static final int X2 = Floresta.TAM / 2 + MEIA + FOLGA;
    static final int Z1 = Floresta.TAM / 2 + 10 - MEIA - FOLGA;
    static final int Z2 = Floresta.TAM / 2 + 10 + MEIA + FOLGA;
    static final Material BLOCO = Material.TINTED_GLASS;

    private final Plugin pl;
    private final World w;
    private final boolean colocar;
    private final int topo;
    private final Deque<int[]> fila = new ArrayDeque<>();
    private final Set<Long> pedidos = new HashSet<>();
    private int voltas;
    int blocos;

    Teto(Plugin pl, World w, boolean colocar) {
        this.pl = pl;
        this.w = w;
        this.colocar = colocar;
        this.topo = w.getMaxHeight() - 1;
        for (int cx = X1 >> 4; cx <= X2 >> 4; cx++)
            for (int cz = Z1 >> 4; cz <= Z2 >> 4; cz++) fila.add(new int[] {cx, cz});
    }

    /** O bloco do canto (X2, Z2) e colocado/tirado por ultimo: se ele esta certo, o trabalho terminou. */
    static boolean pronto(World w, boolean colocar) {
        Material m = w.getBlockAt(X2, w.getMaxHeight() - 1, Z2).getType();
        return colocar ? m == BLOCO : m != BLOCO;
    }

    int chunks() {
        return fila.size();
    }

    /** Faz um pedaco ate o limite de tempo. Devolve true quando terminou. */
    boolean passo(long limiteNs) {
        long t0 = System.nanoTime();
        voltas++;
        Iterator<int[]> it = fila.iterator();
        while (it.hasNext() && System.nanoTime() - t0 < limiteNs) {
            int[] c = it.next();
            long k = ((long) c[0] << 32) ^ (c[1] & 0xffffffffL);
            if (!w.isChunkLoaded(c[0], c[1])) {
                // pede para carregar sem travar o servidor; se demorar demais, carrega na hora
                if (pedidos.add(k)) w.addPluginChunkTicket(c[0], c[1], pl);
                if (voltas < 200) continue;
                w.getChunkAt(c[0], c[1]);
            }
            preencher(c[0], c[1]);
            it.remove();
            if (pedidos.remove(k)) w.removePluginChunkTicket(c[0], c[1], pl);
        }
        if (!fila.isEmpty()) return false;
        Block marca = w.getBlockAt(X2, topo, Z2);
        Material m = colocar ? BLOCO : Material.AIR;
        if (marca.getType() != m) {
            marca.setType(m, false);
            blocos++;
        }
        return true;
    }

    private void preencher(int cx, int cz) {
        int x1 = Math.max(cx << 4, X1), x2 = Math.min((cx << 4) + 15, X2);
        int z1 = Math.max(cz << 4, Z1), z2 = Math.min((cz << 4) + 15, Z2);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (x == X2 && z == Z2) continue;
                Block b = w.getBlockAt(x, topo, z);
                if (colocar) {
                    if (b.getType() != BLOCO) {
                        b.setType(BLOCO, false);
                        blocos++;
                    }
                } else if (b.getType() == BLOCO) {
                    b.setType(Material.AIR, false);
                    blocos++;
                }
            }
    }
}
