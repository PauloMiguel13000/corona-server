package br.servidor.masmorras;

import org.bukkit.Material;

/** Onde a masmorra e construida (o mundo de verdade, ou um mundo falso nos testes). */
interface Destino {

    /** Bloco que esta la agora. */
    Material tipo(int x, int y, int z);

    /** Altura do chao (bloco solido mais alto, sem contar folhas). */
    int chao(int x, int z);

    int minY();

    int maxY();

    /** Coloca um bloco ("minecraft:stone_bricks", "minecraft:lantern[hanging=true]"...). */
    void por(int x, int y, int z, String dado);

    /** Coloca um bau com tabela de saque do Minecraft (ex.: "chests/simple_dungeon"). */
    void bau(int x, int y, int z, String virado, String tabela);
}
