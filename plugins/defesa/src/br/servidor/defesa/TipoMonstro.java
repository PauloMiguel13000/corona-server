package br.servidor.defesa;

import org.bukkit.entity.EntityType;

/** Monstros normais das ondas: vida base, essencia que soltam, dano no Nucleo, velocidade, onda em que aparecem. */
enum TipoMonstro {
    ZUMBI(EntityType.ZOMBIE, "Zumbi", 20, 2, 5, 1.0, 1, 4),
    ARANHA(EntityType.SPIDER, "Aranha", 16, 2, 4, 1.2, 2, 3),
    ESQUELETO(EntityType.SKELETON, "Esqueleto", 20, 3, 5, 1.0, 3, 3),
    CREEPER(EntityType.CREEPER, "Creeper", 20, 4, 15, 1.0, 4, 2),
    VINDICADOR(EntityType.VINDICATOR, "Vindicador", 26, 5, 8, 1.05, 6, 2),
    ESQUELETO_WITHER(EntityType.WITHER_SKELETON, "Esqueleto Wither", 34, 6, 10, 1.0, 8, 2),
    // 13/11: a Bruxa cura os monstros perto dela (mate primeiro!); o Bruto e lento e muito resistente
    BRUXA(EntityType.WITCH, "Bruxa", 26, 6, 8, 0.95, 7, 2),
    BRUTO(EntityType.PIGLIN_BRUTE, "Piglin Bruto", 60, 9, 12, 0.85, 12, 1);

    final EntityType tipo;
    final String nome;
    final double vida;
    final int essencia;
    final int danoNucleo;
    final double velocidade;
    final int ondaMinima;
    final int peso;

    TipoMonstro(EntityType tipo, String nome, double vida, int essencia, int danoNucleo, double velocidade,
                int ondaMinima, int peso) {
        this.tipo = tipo;
        this.nome = nome;
        this.vida = vida;
        this.essencia = essencia;
        this.danoNucleo = danoNucleo;
        this.velocidade = velocidade;
        this.ondaMinima = ondaMinima;
        this.peso = peso;
    }
}
