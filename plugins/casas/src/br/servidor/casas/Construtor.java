package br.servidor.casas;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Constroi as 7 casas (nivel 0 a 6) no centro do plot, de frente para o norte (porta em z menor).
 * Tudo com blocos comuns (Java e Bedrock veem igual). A placa de pressao de evoluir fica logo depois da porta.
 */
final class Construtor {

    record Casa(int nivel, String nome, int lado, int altura, String descricao) {}

    static final Casa[] CASAS = {
            new Casa(0, "Cabana", 7, 8, "madeira de carvalho, uma cama e um baú"),
            new Casa(1, "Casa de Madeira", 9, 9, "varanda, fornalha e dois baús"),
            new Casa(2, "Sobrado", 11, 12, "dois andares, sacada e quatro baús"),
            new Casa(3, "Casa de Pedra", 13, 14, "torre, jardim, mesa de encantamento e seis baús"),
            new Casa(4, "Mansão", 15, 14, "dois andares, telhado de cobre, fonte e oito baús"),
            new Casa(5, "Castelo", 19, 18, "quatro torres, salão, trono e dez baús"),
            new Casa(6, "Palácio", 23, 20, "quartzo e ouro, cúpula, jardins, sala de encantar e doze baús"),
    };

    /** O que a construcao devolve: onde ficou a placa e os baus (para guardar os itens). */
    record Resultado(Location placa, List<Block> baus, int x1, int z1, int x2, int z2, int yTopo) {}

    private final World w;
    private final int y; // y do chao (grama): a casa comeca em y+1
    private final List<Block> baus = new ArrayList<>();
    private Location placa;

    Construtor(World w, int yChao) {
        this.w = w;
        this.y = yChao;
    }

    static Casa casa(int nivel) {
        return CASAS[Math.max(0, Math.min(CASAS.length - 1, nivel))];
    }

    // ------------------------------------------------------------------ primitivas

    /** Nomes que mudaram de versao: tenta o novo se o antigo nao existir. */
    private static final java.util.Map<String, String> ALTERNATIVOS = java.util.Map.of("chain", "iron_chain", "iron_chain", "chain");
    private static final java.util.Set<String> AVISADOS = new java.util.HashSet<>();

    private void b(int x, int yy, int z, String dado) {
        Block bl = w.getBlockAt(x, yy, z);
        String nome = dado.contains("[") ? dado.substring(0, dado.indexOf('[')) : dado;
        String resto = dado.contains("[") ? dado.substring(dado.indexOf('[')) : "";
        BlockData d;
        try {
            d = Bukkit.createBlockData((nome.contains(":") ? nome : "minecraft:" + nome) + resto);
        } catch (IllegalArgumentException ex) {
            String alt = ALTERNATIVOS.get(nome.replace("minecraft:", ""));
            try {
                if (alt == null) throw ex;
                d = Bukkit.createBlockData("minecraft:" + alt + resto);
            } catch (IllegalArgumentException ex2) {
                if (AVISADOS.add(dado)) Bukkit.getLogger().warning("[Casas] bloco desconhecido nesta versao: " + dado + " (pulei)");
                return;
            }
        }
        bl.setBlockData(d, false);
    }

    private void caixa(int x1, int y1, int z1, int x2, int y2, int z2, String dado) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int yy = Math.min(y1, y2); yy <= Math.max(y1, y2); yy++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, yy, z, dado);
    }

    /** Paredes ocas (so o contorno em x/z) entre y1 e y2. */
    private void paredes(int x1, int z1, int x2, int z2, int y1, int y2, String dado) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (x != x1 && x != x2 && z != z1 && z != z2) continue;
                for (int yy = y1; yy <= y2; yy++) b(x, yy, z, dado);
            }
    }

    private void cantos(int x1, int z1, int x2, int z2, int y1, int y2, String dado) {
        for (int yy = y1; yy <= y2; yy++) {
            b(x1, yy, z1, dado);
            b(x2, yy, z1, dado);
            b(x1, yy, z2, dado);
            b(x2, yy, z2, dado);
        }
    }

    /** Telhado de duas aguas com cumeeira no eixo X (escadas viradas para fora), cobrindo x1..x2 e z1..z2 a partir de yBase. */
    private void telhado(int x1, int z1, int x2, int z2, int yBase, String escada, String laje, String cheio) {
        int prof = z2 - z1 + 1;
        int camadas = (prof + 1) / 2;
        for (int k = 0; k < camadas; k++) {
            int zn = z1 + k, zs = z2 - k;
            int yy = yBase + k;
            for (int x = x1; x <= x2; x++) {
                if (zn == zs) {
                    b(x, yy, zn, laje + "[type=bottom]");
                } else if (zs - zn == 1) {
                    b(x, yy, zn, escada + "[facing=south]");
                    b(x, yy, zs, escada + "[facing=north]");
                } else {
                    b(x, yy, zn, escada + "[facing=south]");
                    b(x, yy, zs, escada + "[facing=north]");
                    // empenas (triangulo da parede) rentes a parede, nao no beiral
                    if (x == x1 + 1 || x == x2 - 1) for (int z = zn + 1; z < zs; z++) b(x, yy, z, cheio);
                }
            }
        }
    }

    /** Telhado em piramide (4 aguas) com escadas. */
    private void piramide(int x1, int z1, int x2, int z2, int yBase, String escada, String topo) {
        int k = 0;
        while (x1 + k <= x2 - k && z1 + k <= z2 - k) {
            int yy = yBase + k;
            int ax = x1 + k, bx = x2 - k, az = z1 + k, bz = z2 - k;
            if (ax == bx && az == bz) {
                b(ax, yy, az, topo);
                break;
            }
            for (int x = ax; x <= bx; x++) {
                b(x, yy, az, escada + "[facing=south]");
                b(x, yy, bz, escada + "[facing=north]");
            }
            for (int z = az + 1; z < bz; z++) {
                b(ax, yy, z, escada + "[facing=east]");
                b(bx, yy, z, escada + "[facing=west]");
            }
            if (bx - ax <= 1 || bz - az <= 1) {
                caixa(ax, yy, az, bx, yy, bz, topo);
                break;
            }
            k++;
        }
    }

    private void janela(int x, int yy, int z, String vidro) {
        b(x, yy, z, vidro);
    }

    private void porta(int x, int yy, int z, String madeira, String facing) {
        b(x, yy, z, madeira + "_door[half=lower,facing=" + facing + ",hinge=left]");
        b(x, yy + 1, z, madeira + "_door[half=upper,facing=" + facing + ",hinge=left]");
    }

    private void portaDupla(int x, int yy, int z, String madeira, String facing) {
        b(x, yy, z, madeira + "_door[half=lower,facing=" + facing + ",hinge=right]");
        b(x, yy + 1, z, madeira + "_door[half=upper,facing=" + facing + ",hinge=right]");
        b(x + 1, yy, z, madeira + "_door[half=lower,facing=" + facing + ",hinge=left]");
        b(x + 1, yy + 1, z, madeira + "_door[half=upper,facing=" + facing + ",hinge=left]");
    }

    private void cama(int x, int yy, int z, String cor, String facing) {
        // pe em (x,z), cabeceira no sentido facing
        int dx = facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0;
        int dz = facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0;
        b(x, yy, z, cor + "_bed[part=foot,facing=" + facing + "]");
        b(x + dx, yy, z + dz, cor + "_bed[part=head,facing=" + facing + "]");
    }

    private void bau(int x, int yy, int z, String facing) {
        b(x, yy, z, "chest[facing=" + facing + "]");
        baus.add(w.getBlockAt(x, yy, z));
    }

    private void placa(int x, int yy, int z) {
        b(x, yy, z, "polished_blackstone_pressure_plate");
        placa = new Location(w, x, yy, z);
    }

    private void lanternaPendurada(int x, int yy, int z) {
        b(x, yy, z, "lantern[hanging=true]");
    }

    private void poste(int x, int z, int alt, String haste) {
        for (int k = 1; k <= alt; k++) b(x, y + k, z, haste);
        b(x, y + alt + 1, z, "lantern");
    }

    private void limpar(int x1, int z1, int x2, int z2, int alt) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                for (int k = 1; k <= alt; k++) {
                    Block bl = w.getBlockAt(x, y + k, z);
                    if (!bl.getType().isAir()) bl.setType(Material.AIR, false);
                }
                w.getBlockAt(x, y, z).setType(Material.GRASS_BLOCK, false);
            }
    }

    // ------------------------------------------------------------------ casas

    /** Limpa a area e constroi a casa do nivel no centro (cx, cz). */
    Resultado construir(int cx, int cz, int nivel) {
        Casa c = casa(nivel);
        int r = c.lado() / 2;
        limpar(cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, c.altura() + 4);
        switch (c.nivel()) {
            case 0 -> cabana(cx, cz);
            case 1 -> casaMadeira(cx, cz);
            case 2 -> sobrado(cx, cz);
            case 3 -> casaPedra(cx, cz);
            case 4 -> mansao(cx, cz);
            case 5 -> castelo(cx, cz);
            default -> palacio(cx, cz);
        }
        return new Resultado(placa, new ArrayList<>(baus), cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, y + c.altura() + 4);
    }

    private void cabana(int cx, int cz) {
        int x1 = cx - 3, x2 = cx + 3, z1 = cz - 3, z2 = cz + 3;
        caixa(x1, y, z1, x2, y, z2, "spruce_planks");
        paredes(x1, z1, x2, z2, y + 1, y + 3, "oak_planks");
        cantos(x1, z1, x2, z2, y + 1, y + 3, "spruce_log");
        telhado(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 4, "oak_stairs", "oak_slab", "oak_planks");
        porta(cx, y + 1, z1, "oak", "north");
        janela(x1, y + 2, cz, "glass_pane");
        janela(x2, y + 2, cz, "glass_pane");
        janela(cx - 2, y + 2, z1, "glass_pane");
        janela(cx + 2, y + 2, z1, "glass_pane");
        cama(x2 - 1, y + 1, z2 - 2, "white", "south");
        b(x1 + 1, y + 1, z2 - 1, "crafting_table");
        bau(x1 + 1, y + 1, z2 - 2, "east");
        lanternaPendurada(cx, y + 3, cz);
        b(x1 + 1, y + 1, z1 + 1, "potted_oak_sapling");
        placa(cx, y + 1, z1 + 2);
    }

    private void casaMadeira(int cx, int cz) {
        // casa 9x7 (fundo) + varanda de 2 na frente (norte)
        int x1 = cx - 4, x2 = cx + 4, z1 = cz - 2, z2 = cz + 4;
        caixa(x1, y, z1 - 2, x2, y, z2, "oak_planks");
        paredes(x1, z1, x2, z2, y + 1, y + 3, "spruce_planks");
        cantos(x1, z1, x2, z2, y + 1, y + 3, "stripped_spruce_log");
        for (int x = x1; x <= x2; x++) b(x, y + 3, z1, "stripped_spruce_log[axis=x]");
        telhado(x1 - 1, z1 - 3, x2 + 1, z2 + 1, y + 4, "dark_oak_stairs", "dark_oak_slab", "dark_oak_planks");
        // varanda
        b(x1, y + 1, z1 - 2, "spruce_fence");
        b(x2, y + 1, z1 - 2, "spruce_fence");
        for (int x = x1 + 1; x <= x2 - 1; x++) if (x != cx) b(x, y + 1, z1 - 2, "spruce_fence");
        b(x1, y + 2, z1 - 2, "spruce_fence");
        b(x2, y + 2, z1 - 2, "spruce_fence");
        b(x1, y + 3, z1 - 2, "spruce_fence");
        b(x2, y + 3, z1 - 2, "spruce_fence");
        b(x1 + 1, y + 2, z1 - 2, "potted_red_tulip");
        b(x2 - 1, y + 2, z1 - 2, "potted_blue_orchid");
        porta(cx, y + 1, z1, "spruce", "north");
        janela(cx - 2, y + 2, z1, "glass_pane");
        janela(cx + 2, y + 2, z1, "glass_pane");
        janela(x1, y + 2, cz, "glass_pane");
        janela(x1, y + 2, cz + 1, "glass_pane");
        janela(x2, y + 2, cz, "glass_pane");
        janela(x2, y + 2, cz + 1, "glass_pane");
        janela(cx - 1, y + 2, z2, "glass_pane");
        janela(cx + 1, y + 2, z2, "glass_pane");
        cama(x2 - 1, y + 1, z2 - 2, "red", "south");
        b(x1 + 1, y + 1, z2 - 1, "furnace[facing=east]");
        b(x1 + 1, y + 1, z2 - 2, "crafting_table");
        bau(x1 + 1, y + 1, z1 + 1, "east");
        bau(x1 + 1, y + 1, z1 + 2, "east");
        b(x1 + 1, y + 1, z2 - 3, "barrel[facing=up]");
        lanternaPendurada(cx, y + 3, cz);
        lanternaPendurada(cx, y + 3, z1 - 1);
        placa(cx, y + 1, z1 + 2);
    }

    private void sobrado(int cx, int cz) {
        int x1 = cx - 5, x2 = cx + 5, z1 = cz - 4, z2 = cz + 5;
        caixa(x1, y, z1, x2, y, z2, "stone_bricks");
        caixa(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, "oak_planks");
        // andar 1: pedra
        paredes(x1, z1, x2, z2, y + 1, y + 4, "cobblestone");
        cantos(x1, z1, x2, z2, y + 1, y + 4, "stone_bricks");
        // laje do 2o andar
        caixa(x1, y + 5, z1, x2, y + 5, z2, "spruce_planks");
        // andar 2: madeira com vigas
        paredes(x1, z1, x2, z2, y + 6, y + 8, "oak_planks");
        cantos(x1, z1, x2, z2, y + 5, y + 8, "dark_oak_log");
        for (int x = x1; x <= x2; x++) {
            b(x, y + 5, z1, "dark_oak_log[axis=x]");
            b(x, y + 5, z2, "dark_oak_log[axis=x]");
        }
        for (int z = z1; z <= z2; z++) {
            b(x1, y + 5, z, "dark_oak_log[axis=z]");
            b(x2, y + 5, z, "dark_oak_log[axis=z]");
        }
        telhado(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 9, "dark_oak_stairs", "dark_oak_slab", "dark_oak_planks");
        // sacada na frente
        for (int x = cx - 2; x <= cx + 2; x++) {
            b(x, y + 5, z1 - 1, "spruce_slab[type=top]");
            b(x, y + 6, z1 - 1, "spruce_fence");
        }
        b(cx, y + 6, z1 - 1, "spruce_fence");
        // portas e janelas
        porta(cx, y + 1, z1, "dark_oak", "north");
        b(cx, y + 6, z1, "dark_oak_door[half=lower,facing=north,hinge=left]");
        b(cx, y + 7, z1, "dark_oak_door[half=upper,facing=north,hinge=left]");
        for (int x : new int[]{cx - 3, cx + 3}) {
            janela(x, y + 2, z1, "glass_pane");
            janela(x, y + 3, z1, "glass_pane");
            janela(x, y + 7, z1, "glass_pane");
            janela(x, y + 7, z2, "glass_pane");
            janela(x, y + 2, z2, "glass_pane");
            janela(x, y + 3, z2, "glass_pane");
        }
        for (int z : new int[]{cz - 2, cz, cz + 2}) {
            janela(x1, y + 2, z, "glass_pane");
            janela(x1, y + 7, z, "glass_pane");
            janela(x2, y + 2, z, "glass_pane");
            janela(x2, y + 7, z, "glass_pane");
        }
        // escada interna (buraco na laje) e ladder
        caixa(x2 - 1, y + 5, z2 - 2, x2 - 1, y + 5, z2 - 1, "air");
        for (int k = 1; k <= 5; k++) b(x2 - 1, y + k, z2, "ladder[facing=north]");
        // interior 1: cozinha e baus
        b(x1 + 1, y + 1, z2 - 1, "furnace[facing=east]");
        b(x1 + 1, y + 1, z2 - 2, "smoker[facing=east]");
        b(x1 + 1, y + 1, z2 - 3, "crafting_table");
        bau(x1 + 1, y + 1, z1 + 1, "east");
        bau(x1 + 1, y + 1, z1 + 2, "east");
        b(cx + 2, y + 1, z2 - 1, "cartography_table");
        b(x1 + 2, y + 1, z1 + 1, "bookshelf");
        b(x1 + 3, y + 1, z1 + 1, "bookshelf");
        lanternaPendurada(cx, y + 4, cz);
        // interior 2: quarto
        cama(x1 + 2, y + 6, z2 - 2, "light_blue", "south");
        bau(x1 + 1, y + 6, z1 + 1, "east");
        bau(x1 + 1, y + 6, z1 + 2, "east");
        b(x2 - 2, y + 6, z1 + 1, "bookshelf");
        b(x2 - 3, y + 6, z1 + 1, "lectern[facing=south]");
        lanternaPendurada(cx, y + 8, cz);
        placa(cx, y + 1, z1 + 2);
    }

    private void casaPedra(int cx, int cz) {
        int x1 = cx - 4, x2 = cx + 6, z1 = cz - 3, z2 = cz + 5; // casa deslocada: torre fica no canto noroeste
        // jardim: caminho de cascalho da frente do plot ate a porta e lanternas
        int jx1 = cx - 6, jx2 = cx + 6, jz1 = cz - 6, jz2 = cz + 6;
        for (int z = jz1; z < z1; z++) b(cx + 1, y, z, "gravel");
        for (int z = jz1; z < z1; z++) {
            b(cx, y, z, "coarse_dirt");
            b(cx + 2, y, z, "coarse_dirt");
        }
        poste(jx1, jz1, 2, "spruce_fence");
        poste(jx2, jz1, 2, "spruce_fence");
        poste(jx1, jz2, 2, "spruce_fence");
        poste(jx2, jz2, 2, "spruce_fence");
        for (int x = jx1 + 1; x <= jx2 - 1; x += 2) {
            if (x != cx + 1) b(x, y + 1, jz1, "spruce_fence");
            b(x, y + 1, jz2, "spruce_fence");
        }
        for (int z = jz1 + 1; z <= jz2 - 1; z += 2) {
            b(jx1, y + 1, z, "spruce_fence");
            b(jx2, y + 1, z, "spruce_fence");
        }
        b(x1 - 1, y + 1, z2, "azalea");
        b(x2 + 1, y + 1, z2, "flowering_azalea");
        b(x2 + 1, y + 1, z1 - 1, "sweet_berry_bush[age=3]");
        // casa
        caixa(x1, y, z1, x2, y, z2, "polished_andesite");
        paredes(x1, z1, x2, z2, y + 1, y + 4, "stone_bricks");
        cantos(x1, z1, x2, z2, y + 1, y + 4, "spruce_log");
        for (int x = x1; x <= x2; x++) b(x, y + 4, z1, "spruce_log[axis=x]");
        for (int x = x1; x <= x2; x++) b(x, y + 4, z2, "spruce_log[axis=x]");
        telhado(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 5, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
        // torre 5x5 no canto noroeste, 3 andares
        int tx1 = x1 - 2, tx2 = x1 + 2, tz1 = z1 - 2, tz2 = z1 + 2;
        for (int x = tx1; x <= tx2; x++) for (int z = tz1; z <= tz2; z++) for (int k = 5; k <= 12; k++) b(x, y + k, z, "air");
        caixa(tx1, y, tz1, tx2, y, tz2, "polished_andesite");
        paredes(tx1, tz1, tx2, tz2, y + 1, y + 10, "stone_bricks");
        cantos(tx1, tz1, tx2, tz2, y + 1, y + 10, "polished_deepslate");
        caixa(tx1 + 1, y + 5, tz1 + 1, tx2 - 1, y + 5, tz2 - 1, "spruce_planks");
        caixa(tx1 + 1, y + 1, tz1 + 1, tx2 - 1, y + 4, tz2 - 1, "air");
        caixa(tx1 + 1, y + 6, tz1 + 1, tx2 - 1, y + 10, tz2 - 1, "air");
        for (int k = 1; k <= 10; k++) b(tx1 + 1, y + k, tz1 + 1, "ladder[facing=east]");
        b(tx1 + 1, y + 5, tz1 + 1, "air");
        for (int k : new int[]{3, 8}) {
            janela(tx1, y + k, tz1 + 2, "glass_pane");
            janela(tx2, y + k, tz1 + 2, "glass_pane");
            janela(tx1 + 2, y + k, tz1, "glass_pane");
            janela(tx1 + 2, y + k, tz2, "glass_pane");
        }
        piramide(tx1 - 1, tz1 - 1, tx2 + 1, tz2 + 1, y + 11, "deepslate_tile_stairs", "deepslate_tiles");
        b(tx1 + 2, y + 15, tz1 + 2, "lightning_rod");
        // porta da casa na frente (entre a torre e o canto leste) e passagem casa->torre
        porta(cx + 1, y + 1, z1, "spruce", "north");
        b(x1, y + 1, z1 + 1, "air");
        b(x1, y + 2, z1 + 1, "air");
        // janelas
        for (int x : new int[]{cx + 3, cx + 5}) {
            janela(x, y + 2, z1, "glass_pane");
            janela(x, y + 3, z1, "glass_pane");
        }
        for (int x = x1 + 1; x <= x2 - 1; x += 2) {
            janela(x, y + 2, z2, "glass_pane");
            janela(x, y + 3, z2, "glass_pane");
        }
        for (int z = z1 + 1; z <= z2 - 1; z += 2) {
            janela(x2, y + 2, z, "glass_pane");
            janela(x2, y + 3, z, "glass_pane");
        }
        // interior
        cama(x2 - 1, y + 1, z2 - 2, "blue", "south");
        b(cx + 1, y + 1, z2 - 1, "enchanting_table");
        b(cx, y + 1, z2 - 1, "bookshelf");
        b(cx + 2, y + 1, z2 - 1, "bookshelf");
        b(cx, y + 2, z2 - 1, "bookshelf");
        b(cx + 2, y + 2, z2 - 1, "bookshelf");
        b(x1 + 1, y + 1, z2 - 1, "anvil[facing=east]");
        b(x1 + 1, y + 1, z2 - 2, "blast_furnace[facing=east]");
        b(x1 + 1, y + 1, z2 - 3, "crafting_table");
        for (int k = 0; k < 3; k++) bau(x2 - 1, y + 1, z1 + 1 + k, "west");
        bau(x1 + 1, y + 1, z1 + 2, "east");
        bau(x1 + 1, y + 1, z1 + 3, "east");
        bau(tx1 + 2, y + 6, tz2 - 1, "north");
        lanternaPendurada(cx + 1, y + 4, cz + 1);
        lanternaPendurada(tx1 + 2, y + 4, tz1 + 2);
        lanternaPendurada(tx1 + 2, y + 9, tz1 + 2);
        placa(cx + 1, y + 1, z1 + 2);
    }

    private void mansao(int cx, int cz) {
        int x1 = cx - 7, x2 = cx + 7, z1 = cz - 4, z2 = cz + 7;
        // base e terraco
        caixa(x1 - 1, y, z1 - 3, x2 + 1, y, z2 + 1, "polished_andesite");
        caixa(x1, y, z1, x2, y, z2, "polished_diorite");
        // fonte na frente
        int fz = z1 - 3 - 2;
        caixa(cx - 2, y, fz - 2, cx + 2, y, fz + 2, "stone_bricks");
        caixa(cx - 1, y, fz - 1, cx + 1, y, fz + 1, "water");
        caixa(cx - 2, y + 1, fz - 2, cx + 2, y + 1, fz + 2, "stone_brick_wall");
        caixa(cx - 1, y + 1, fz - 1, cx + 1, y + 1, fz + 1, "air");
        b(cx, y + 1, fz, "polished_diorite");
        b(cx, y + 2, fz, "sea_lantern");
        for (int z = fz + 3; z < z1; z++) {
            b(cx - 1, y, z, "polished_andesite");
            b(cx, y, z, "polished_andesite");
            b(cx + 1, y, z, "polished_andesite");
        }
        // andar 1 e 2
        paredes(x1, z1, x2, z2, y + 1, y + 4, "white_terracotta");
        caixa(x1, y + 5, z1, x2, y + 5, z2, "polished_andesite");
        paredes(x1, z1, x2, z2, y + 6, y + 9, "white_terracotta");
        // pilastras
        for (int x = x1; x <= x2; x += 7) {
            for (int k = 1; k <= 9; k++) {
                b(x, y + k, z1, "stone_bricks");
                b(x, y + k, z2, "stone_bricks");
            }
        }
        for (int z = z1; z <= z2; z += 11) {
            for (int k = 1; k <= 9; k++) {
                b(x1, y + k, z, "stone_bricks");
                b(x2, y + k, z, "stone_bricks");
            }
        }
        for (int x = x1; x <= x2; x++) {
            b(x, y + 5, z1, "chiseled_stone_bricks");
            b(x, y + 5, z2, "chiseled_stone_bricks");
            b(x, y + 10, z1, "stone_brick_slab[type=bottom]");
            b(x, y + 10, z2, "stone_brick_slab[type=bottom]");
        }
        for (int z = z1; z <= z2; z++) {
            b(x1, y + 5, z, "chiseled_stone_bricks");
            b(x2, y + 5, z, "chiseled_stone_bricks");
        }
        telhado(x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 10, "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab", "waxed_oxidized_copper");
        // entrada com colunas e porta dupla
        portaDupla(cx - 1, y + 1, z1, "dark_oak", "north");
        for (int k = 1; k <= 4; k++) {
            b(cx - 3, y + k, z1 - 2, "quartz_pillar");
            b(cx + 3, y + k, z1 - 2, "quartz_pillar");
        }
        caixa(cx - 3, y + 5, z1 - 2, cx + 3, y + 5, z1 - 1, "polished_andesite");
        for (int x = cx - 3; x <= cx + 3; x++) b(x, y + 6, z1 - 2, "stone_brick_wall");
        // janelas altas
        for (int x = x1 + 2; x <= x2 - 2; x += 2) {
            if (Math.abs(x - cx) <= 1) continue;
            for (int k : new int[]{2, 3, 7, 8}) {
                janela(x, y + k, z1, "glass_pane");
                janela(x, y + k, z2, "glass_pane");
            }
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 2) {
            for (int k : new int[]{2, 3, 7, 8}) {
                janela(x1, y + k, z, "glass_pane");
                janela(x2, y + k, z, "glass_pane");
            }
        }
        // escada interna
        for (int k = 0; k < 5; k++) {
            b(x2 - 1, y + 1 + k, z2 - 1 - k, "stone_brick_stairs[facing=north]");
            if (k > 0) caixa(x2 - 1, y + 1, z2 - 1 - k, x2 - 1, y + k, z2 - 1 - k, "stone_bricks");
        }
        caixa(x2 - 1, y + 5, z2 - 6, x2 - 1, y + 5, z2 - 1, "air");
        caixa(x2 - 2, y + 5, z2 - 6, x2 - 2, y + 5, z2 - 1, "air");
        for (int z = z2 - 6; z <= z2 - 1; z++) b(x2 - 2, y + 6, z, "stone_brick_wall");
        // interior 1: salao
        for (int k = 0; k < 4; k++) bau(x1 + 1, y + 1, z1 + 2 + k, "east");
        b(x1 + 1, y + 1, z2 - 1, "brewing_stand");
        b(x1 + 2, y + 1, z2 - 1, "cauldron");
        b(x1 + 3, y + 1, z2 - 1, "smithing_table");
        b(x1 + 4, y + 1, z2 - 1, "crafting_table");
        b(cx, y + 1, z2 - 1, "enchanting_table");
        for (int x = cx - 2; x <= cx + 2; x++) if (x != cx) for (int k = 1; k <= 2; k++) b(x, y + k, z2 - 1, "bookshelf");
        for (int x = cx - 3; x <= cx + 3; x += 3) {
            b(x, y + 4, cz, "chain");
            b(x, y + 3, cz, "lantern[hanging=true]");
        }
        // interior 2: quartos
        cama(x1 + 1, y + 6, z2 - 2, "purple", "south");
        cama(x2 - 3, y + 6, z1 + 1, "cyan", "south");
        for (int k = 0; k < 4; k++) bau(x1 + 1, y + 6, z1 + 1 + k, "east");
        b(cx, y + 6, z2 - 1, "lectern[facing=north]");
        b(cx - 1, y + 6, z2 - 1, "bookshelf");
        b(cx + 1, y + 6, z2 - 1, "bookshelf");
        for (int x = cx - 3; x <= cx + 3; x += 3) {
            b(x, y + 9, cz, "chain");
            b(x, y + 8, cz, "lantern[hanging=true]");
        }
        placa(cx, y + 1, z1 + 2);
    }

    private void castelo(int cx, int cz) {
        int x1 = cx - 8, x2 = cx + 8, z1 = cz - 8, z2 = cz + 8;
        caixa(x1 - 1, y, z1 - 1, x2 + 1, y, z2 + 1, "cobbled_deepslate");
        caixa(x1, y, z1, x2, y, z2, "polished_deepslate");
        // muralha principal
        paredes(x1, z1, x2, z2, y + 1, y + 8, "deepslate_bricks");
        for (int x = x1; x <= x2; x++) {
            b(x, y + 9, z1, (x % 2 == 0) ? "deepslate_brick_wall" : "deepslate_brick_slab[type=bottom]");
            b(x, y + 9, z2, (x % 2 == 0) ? "deepslate_brick_wall" : "deepslate_brick_slab[type=bottom]");
        }
        for (int z = z1; z <= z2; z++) {
            b(x1, y + 9, z, (z % 2 == 0) ? "deepslate_brick_wall" : "deepslate_brick_slab[type=bottom]");
            b(x2, y + 9, z, (z % 2 == 0) ? "deepslate_brick_wall" : "deepslate_brick_slab[type=bottom]");
        }
        caixa(x1, y + 8, z1, x2, y + 8, z2, "polished_deepslate"); // teto/terraco
        caixa(x1 + 1, y + 1, z1 + 1, x2 - 1, y + 7, z2 - 1, "air");
        // torres nos 4 cantos (5x5, ate y+14)
        int[][] cantosT = {{x1, z1}, {x2 - 4, z1}, {x1, z2 - 4}, {x2 - 4, z2 - 4}};
        for (int[] t : cantosT) {
            int tx1 = t[0], tz1 = t[1], tx2 = t[0] + 4, tz2 = t[1] + 4;
            paredes(tx1, tz1, tx2, tz2, y + 1, y + 13, "polished_blackstone_bricks");
            cantos(tx1, tz1, tx2, tz2, y + 1, y + 13, "deepslate_bricks");
            caixa(tx1 + 1, y + 1, tz1 + 1, tx2 - 1, y + 7, tz2 - 1, "air");
            caixa(tx1 + 1, y + 8, tz1 + 1, tx2 - 1, y + 8, tz2 - 1, "polished_deepslate");
            caixa(tx1 + 1, y + 9, tz1 + 1, tx2 - 1, y + 12, tz2 - 1, "air");
            caixa(tx1 + 1, y + 13, tz1 + 1, tx2 - 1, y + 13, tz2 - 1, "polished_deepslate");
            for (int x = tx1; x <= tx2; x++) {
                b(x, y + 14, tz1, (x - tx1) % 2 == 0 ? "polished_blackstone_brick_wall" : "polished_blackstone_brick_slab[type=bottom]");
                b(x, y + 14, tz2, (x - tx1) % 2 == 0 ? "polished_blackstone_brick_wall" : "polished_blackstone_brick_slab[type=bottom]");
            }
            for (int z = tz1 + 1; z < tz2; z++) {
                b(tx1, y + 14, z, (z - tz1) % 2 == 0 ? "polished_blackstone_brick_wall" : "polished_blackstone_brick_slab[type=bottom]");
                b(tx2, y + 14, z, (z - tz1) % 2 == 0 ? "polished_blackstone_brick_wall" : "polished_blackstone_brick_slab[type=bottom]");
            }
            for (int k : new int[]{3, 6, 11}) {
                janela(tx1 + 2, y + k, tz1, "iron_bars");
                janela(tx1 + 2, y + k, tz2, "iron_bars");
                janela(tx1, y + k, tz1 + 2, "iron_bars");
                janela(tx2, y + k, tz1 + 2, "iron_bars");
            }
            // escada em caracol simples (ladder) dentro da torre ate o terraco
            for (int k = 1; k <= 13; k++) b(tx1 + 1, y + k, tz1 + 1, "ladder[facing=east]");
            b(tx1 + 1, y + 8, tz1 + 1, "air");
            b(tx1 + 1, y + 13, tz1 + 1, "air");
            b(tx1 + 2, y + 9, tz1 + 2, "soul_lantern");
        }
        // portao: porta dupla de ferro grande (arco) com grades em cima
        caixa(cx - 1, y + 1, z1, cx + 1, y + 4, z1, "air");
        b(cx - 2, y + 1, z1, "polished_blackstone_bricks");
        b(cx + 2, y + 1, z1, "polished_blackstone_bricks");
        caixa(cx - 1, y + 4, z1, cx + 1, y + 4, z1, "iron_bars");
        caixa(cx - 1, y + 5, z1, cx + 1, y + 5, z1, "chiseled_polished_blackstone");
        portaDupla(cx - 1, y + 1, z1, "dark_oak", "north");
        b(cx + 1, y + 1, z1, "dark_oak_door[half=lower,facing=north,hinge=left]");
        b(cx + 1, y + 2, z1, "dark_oak_door[half=upper,facing=north,hinge=left]");
        b(cx, y + 1, z1, "air");
        b(cx, y + 2, z1, "air");
        b(cx, y + 3, z1, "air");
        caixa(cx - 1, y + 3, z1, cx + 1, y + 3, z1, "air");
        // bandeiras e tochas na frente
        b(cx - 3, y + 6, z1 - 1, "red_wall_banner[facing=north]");
        b(cx + 3, y + 6, z1 - 1, "red_wall_banner[facing=north]");
        b(cx - 3, y + 4, z1 - 1, "soul_wall_torch[facing=north]");
        b(cx + 3, y + 4, z1 - 1, "soul_wall_torch[facing=north]");
        // janelas das paredes
        for (int x = x1 + 6; x <= x2 - 6; x += 2) {
            if (Math.abs(x - cx) <= 2) continue;
            for (int k : new int[]{3, 4, 6}) janela(x, y + k, z1, "iron_bars");
            for (int k : new int[]{3, 4, 6}) janela(x, y + k, z2, "iron_bars");
        }
        for (int z = z1 + 6; z <= z2 - 6; z += 2) {
            for (int k : new int[]{3, 4, 6}) janela(x1, y + k, z, "iron_bars");
            for (int k : new int[]{3, 4, 6}) janela(x2, y + k, z, "iron_bars");
        }
        // salao: tapete, trono ao fundo, mesas, baus
        caixa(cx - 1, y, z1 + 3, cx + 1, y, z2 - 4, "red_wool");
        caixa(cx - 2, y + 1, z2 - 3, cx + 2, y + 1, z2 - 2, "polished_blackstone_bricks");
        caixa(cx - 1, y + 2, z2 - 2, cx + 1, y + 2, z2 - 2, "polished_blackstone_bricks");
        b(cx, y + 2, z2 - 3, "quartz_stairs[facing=south]");
        b(cx - 1, y + 2, z2 - 3, "gold_block");
        b(cx + 1, y + 2, z2 - 3, "gold_block");
        b(cx, y + 3, z2 - 2, "gold_block");
        b(cx, y + 4, z2 - 2, "red_wall_banner[facing=north]");
        for (int k = 0; k < 5; k++) {
            bau(x1 + 1, y + 1, cz - 2 + k, "east");
            bau(x2 - 1, y + 1, cz - 2 + k, "west");
        }
        b(x1 + 2, y + 1, z1 + 5, "anvil[facing=east]");
        b(x1 + 3, y + 1, z1 + 5, "smithing_table");
        b(x1 + 4, y + 1, z1 + 5, "blast_furnace[facing=south]");
        b(x1 + 5, y + 1, z1 + 5, "grindstone[face=floor,facing=north]");
        b(x2 - 2, y + 1, z1 + 5, "enchanting_table");
        b(x2 - 3, y + 1, z1 + 5, "bookshelf");
        b(x2 - 4, y + 1, z1 + 5, "bookshelf");
        b(x2 - 3, y + 2, z1 + 5, "bookshelf");
        b(x2 - 2, y + 1, z1 + 6, "brewing_stand");
        cama(x1 + 5, y + 1, z2 - 2, "black", "south");
        cama(x2 - 5, y + 1, z2 - 2, "gray", "south");
        for (int x = cx - 4; x <= cx + 4; x += 4) for (int z = cz - 4; z <= cz + 4; z += 4) {
            b(x, y + 7, z, "chain");
            b(x, y + 6, z, "soul_lantern[hanging=true]");
        }
        placa(cx, y + 1, z1 + 2);
    }

    private void palacio(int cx, int cz) {
        int x1 = cx - 10, x2 = cx + 10, z1 = cz - 8, z2 = cz + 10;
        // base de quartzo e jardim em volta
        caixa(cx - 11, y, cz - 11, cx + 11, y, cz + 11, "grass_block");
        caixa(x1 - 1, y, z1 - 1, x2 + 1, y, z2 + 1, "smooth_quartz");
        // jardim da frente: caminho, cerejeiras, lanternas
        for (int z = cz - 11; z < z1 - 1; z++) {
            b(cx - 1, y, z, "polished_diorite");
            b(cx, y, z, "quartz_block");
            b(cx + 1, y, z, "polished_diorite");
        }
        for (int sx : new int[]{-1, 1}) {
            int ax = cx + sx * 6, az = cz - 10;
            b(ax, y + 1, az, "cherry_log");
            b(ax, y + 2, az, "cherry_log");
            caixa(ax - 1, y + 3, az - 1, ax + 1, y + 4, az + 1, "cherry_leaves");
            b(ax, y + 5, az, "cherry_leaves");
            b(ax + sx * 3, y + 1, az, "sea_lantern");
            b(cx + sx * 3, y + 1, cz - 10, "flowering_azalea");
        }
        // corpo central 2 andares + alas laterais 1 andar
        paredes(x1, z1, x2, z2, y + 1, y + 5, "quartz_block");
        caixa(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, "quartz_bricks");
        int mx1 = cx - 5, mx2 = cx + 5;
        paredes(mx1, z1, mx2, z2, y + 6, y + 10, "quartz_block");
        caixa(x1, y + 6, z1, x2, y + 6, z2, "smooth_quartz");       // teto das alas / laje do 2o andar
        caixa(mx1 + 1, y + 6, z1 + 1, mx2 - 1, y + 6, z2 - 1, "quartz_bricks");
        caixa(mx1, y + 11, z1, mx2, y + 11, z2, "smooth_quartz");   // teto do corpo central
        caixa(mx1 + 1, y + 1, z1 + 1, mx2 - 1, y + 5, z2 - 1, "air");
        caixa(x1 + 1, y + 1, z1 + 1, mx1 - 1, y + 5, z2 - 1, "air");
        caixa(mx2 + 1, y + 1, z1 + 1, x2 - 1, y + 5, z2 - 1, "air");
        caixa(mx1 + 1, y + 7, z1 + 1, mx2 - 1, y + 10, z2 - 1, "air");
        // ouro: frisos
        for (int x = x1; x <= x2; x++) {
            b(x, y + 5, z1, "gold_block");
            b(x, y + 5, z2, "gold_block");
            b(x, y + 7, z1, x >= mx1 && x <= mx2 ? "gold_block" : "quartz_slab[type=bottom]");
            b(x, y + 7, z2, x >= mx1 && x <= mx2 ? "gold_block" : "quartz_slab[type=bottom]");
        }
        for (int z = z1; z <= z2; z++) {
            b(x1, y + 5, z, "gold_block");
            b(x2, y + 5, z, "gold_block");
            b(x1, y + 7, z, "quartz_slab[type=bottom]");
            b(x2, y + 7, z, "quartz_slab[type=bottom]");
        }
        cantos(x1, z1, x2, z2, y + 1, y + 7, "quartz_pillar");
        cantos(mx1, z1, mx2, z2, y + 1, y + 12, "quartz_pillar");
        // cupula central
        for (int k = 0; k < 5; k++) {
            int rr = 5 - k;
            for (int x = -rr; x <= rr; x++) for (int z = -rr; z <= rr; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > rr + 0.4 || d < rr - 0.8) continue;
                b(cx + x, y + 12 + k, cz + 1 + z, k == 4 ? "gold_block" : (k % 2 == 0 ? "smooth_quartz" : "purple_stained_glass"));
            }
        }
        b(cx, y + 17, cz + 1, "gold_block");
        b(cx, y + 18, cz + 1, "end_rod");
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (x * x + z * z <= 5) b(cx + x, y + 12, cz + 1 + z, "purple_stained_glass");
        // janelas roxas altas
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            if (Math.abs(x - cx) <= 1) continue;
            for (int k : new int[]{2, 3, 4}) {
                janela(x, y + k, z1, "purple_stained_glass_pane");
                janela(x, y + k, z2, "purple_stained_glass_pane");
            }
            if (x >= mx1 && x <= mx2) for (int k : new int[]{8, 9}) {
                janela(x, y + k, z1, "purple_stained_glass_pane");
                janela(x, y + k, z2, "purple_stained_glass_pane");
            }
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 3) for (int k : new int[]{2, 3, 4}) {
            janela(x1, y + k, z, "purple_stained_glass_pane");
            janela(x2, y + k, z, "purple_stained_glass_pane");
        }
        // entrada: porta dupla, colunas de ouro, luz
        portaDupla(cx - 1, y + 1, z1, "dark_oak", "north");
        b(cx + 1, y + 1, z1, "dark_oak_door[half=lower,facing=north,hinge=left]");
        b(cx + 1, y + 2, z1, "dark_oak_door[half=upper,facing=north,hinge=left]");
        caixa(cx - 1, y + 1, z1, cx + 1, y + 2, z1, "air");
        portaDupla(cx - 1, y + 1, z1, "dark_oak", "north");
        b(cx, y + 3, z1, "sea_lantern");
        for (int k = 1; k <= 4; k++) {
            b(cx - 3, y + k, z1 - 2, "gold_block");
            b(cx + 3, y + k, z1 - 2, "gold_block");
        }
        caixa(cx - 3, y + 5, z1 - 2, cx + 3, y + 5, z1 - 1, "smooth_quartz");
        b(cx, y + 6, z1 - 2, "end_rod");
        // escada para o 2o andar (ala leste do salao)
        for (int k = 0; k < 6; k++) {
            b(mx2 - 1, y + 1 + k, z2 - 1 - k, "quartz_stairs[facing=north]");
            if (k > 0) caixa(mx2 - 1, y + 1, z2 - 1 - k, mx2 - 1, y + k, z2 - 1 - k, "quartz_block");
        }
        caixa(mx2 - 1, y + 6, z2 - 7, mx2 - 1, y + 6, z2 - 1, "air");
        caixa(mx2 - 2, y + 6, z2 - 7, mx2 - 2, y + 6, z2 - 1, "air");
        for (int z = z2 - 7; z <= z2 - 1; z++) b(mx2 - 2, y + 7, z, "gold_block");
        // salao: trono, tapete roxo, lustres
        caixa(cx - 1, y, z1 + 3, cx + 1, y, z2 - 4, "purple_wool");
        caixa(cx - 2, y + 1, z2 - 3, cx + 2, y + 1, z2 - 2, "gold_block");
        b(cx, y + 2, z2 - 3, "quartz_stairs[facing=south]");
        b(cx - 1, y + 2, z2 - 2, "purple_stained_glass");
        b(cx + 1, y + 2, z2 - 2, "purple_stained_glass");
        b(cx, y + 2, z2 - 2, "gold_block");
        b(cx, y + 3, z2 - 2, "amethyst_cluster[facing=up]");
        for (int x = cx - 3; x <= cx + 3; x += 3) for (int z = cz - 3; z <= cz + 4; z += 7) {
            b(x, y + 5, z, "chain");
            b(x, y + 4, z, "lantern[hanging=true]");
        }
        // ala oeste: sala de encantar (15 estantes) + bigorna
        int ox1 = x1 + 1, ox2 = mx1 - 1;
        int ex = (ox1 + ox2) / 2, ez = cz + 1;
        b(ex, y + 1, ez, "enchanting_table");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            if (dx == 0 && dz == -2) continue; // entrada
            b(ex + dx, y + 1, ez + dz, "bookshelf");
            if (Math.abs(dx) == 2 && Math.abs(dz) == 2) b(ex + dx, y + 2, ez + dz, "bookshelf");
        }
        b(ox1, y + 1, z1 + 1, "anvil[facing=east]");
        b(ox1, y + 1, z1 + 2, "smithing_table");
        b(ox1, y + 1, z1 + 3, "grindstone[face=floor,facing=east]");
        b(ox1, y + 1, z2 - 1, "brewing_stand");
        b(ox1 + 1, y + 1, z2 - 1, "cauldron");
        // ala leste: baus (12) e cozinha
        int lx1 = mx2 + 1, lx2 = x2 - 1;
        for (int k = 0; k < 6; k++) {
            bau(lx2, y + 1, z1 + 2 + k, "west");
            bau(lx2, y + 2, z1 + 2 + k, "west");
        }
        b(lx1, y + 1, z2 - 1, "blast_furnace[facing=east]");
        b(lx1, y + 1, z2 - 2, "smoker[facing=east]");
        b(lx1, y + 1, z2 - 3, "crafting_table");
        b(lx1, y + 1, z2 - 4, "barrel[facing=up]");
        // 2o andar: quartos reais
        cama(mx1 + 1, y + 7, z2 - 2, "purple", "south");
        cama(mx2 - 3, y + 7, z1 + 1, "magenta", "south");
        b(cx, y + 7, z2 - 1, "lectern[facing=north]");
        b(cx - 1, y + 7, z2 - 1, "bookshelf");
        b(cx + 1, y + 7, z2 - 1, "bookshelf");
        b(mx1 + 1, y + 7, z1 + 1, "jukebox");
        b(mx1 + 1, y + 7, z1 + 2, "note_block");
        for (int x = cx - 3; x <= cx + 3; x += 3) {
            b(x, y + 10, cz + 1, "chain");
            b(x, y + 9, cz + 1, "sea_lantern");
        }
        // luzes externas nos cantos
        for (int[] c : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}, {x2 + 1, z2 + 1}}) {
            b(c[0], y + 1, c[1], "quartz_pillar");
            b(c[0], y + 2, c[1], "quartz_pillar");
            b(c[0], y + 3, c[1], "sea_lantern");
        }
        placa(cx, y + 1, z1 + 2);
    }
}
