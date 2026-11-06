package br.servidor.defesa;

import org.bukkit.entity.EntityType;

/** Bosses que aparecem a cada 5 ondas (em rodizio). */
enum TipoChefe {
    ZUMBI_GIGANTE(EntityType.ZOMBIE, "Zumbi Gigante", 150, 2.5),
    DEVASTADOR(EntityType.RAVAGER, "Devastador", 250, 1.3),
    COLOSSO(EntityType.IRON_GOLEM, "Colosso de Ferro", 400, 1.5);

    final EntityType tipo;
    final String nome;
    final double vida;
    final double escala;

    TipoChefe(EntityType tipo, String nome, double vida, double escala) {
        this.tipo = tipo;
        this.nome = nome;
        this.vida = vida;
        this.escala = escala;
    }
}
