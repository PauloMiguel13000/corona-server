package br.servidor.defesa;

import org.bukkit.Material;

/** 13/11 (Paulo: "faca algo muito foda"): poderes que os defensores soltam no meio da onda, pagos com Essencia e com recarga. */
enum PoderDefensor {
    METEOROS("meteoro", "§6§l☄ CHUVA DE METEOROS", Material.FIRE_CHARGE, 60, 90,
            "Meteoros caem do céu em cima dos monstros", "e explodem em área (35% da vida; chefe 6%)."),
    NEVASCA("nevasca", "§b§l❄ NEVASCA CONGELANTE", Material.SNOWBALL, 40, 75,
            "Congela TODOS os monstros por 5 segundos", "(as torres continuam atirando)."),
    TROVAO("trovao", "§e§l⚡ FÚRIA DO TROVÃO", Material.LIGHTNING_ROD, 50, 60,
            "Raios nos 6 monstros mais perto do Núcleo", "(45% da vida; chefe 10%) e pulam para os vizinhos.");

    final String id, nome;
    final Material icone;
    final int custo, recargaSeg;
    final String desc1, desc2;

    PoderDefensor(String id, String nome, Material icone, int custo, int recargaSeg, String desc1, String desc2) {
        this.id = id; this.nome = nome; this.icone = icone; this.custo = custo; this.recargaSeg = recargaSeg; this.desc1 = desc1; this.desc2 = desc2;
    }

    static PoderDefensor porId(String id) {
        for (PoderDefensor p : values()) if (p.id.equalsIgnoreCase(id)) return p;
        return null;
    }
}
