package br.servidor.defesa;

import org.bukkit.Color;
import org.bukkit.Material;

/** Os tipos de torre da loja. Dano por ataque, alcance em blocos, intervalo em ticks (20 = 1s). */
enum TipoTorre {
    ARQUEIRA("arqueira", "Arqueira", "§a", Material.DISPENSER, Color.GREEN, 15, 5.0, 12, 20,
            "Atira flechas no monstro mais adiantado."),
    CANHAO("canhao", "Canhão", "§6", Material.BLAST_FURNACE, Color.ORANGE, 30, 7.0, 13, 40,
            "Bola de fogo que explode e acerta em área."),
    GELO("gelo", "Torre de Gelo", "§b", Material.BLUE_ICE, Color.AQUA, 35, 1.5, 8, 20,
            "Congela todos por perto e deixa lentos."),
    VENENO("veneno", "Torre de Veneno", "§2", Material.SLIME_BLOCK, Color.LIME, 40, 2.0, 9, 30,
            "Envenena todos no alcance: dano por 5s."),
    MINA("mina", "Mina de Essência", "§d", Material.AMETHYST_BLOCK, Color.PURPLE, 50, 0.0, 0, 20,
            "Não ataca: gera Essência a cada onda."),
    MORTEIRO("morteiro", "Morteiro", "§8", Material.CAULDRON, Color.GRAY, 60, 14.0, 22, 60,
            "Bomba de longe no meio do grupo (área grande)."),
    TESLA("tesla", "Tesla", "§e", Material.COPPER_BLOCK, Color.YELLOW, 70, 10.0, 11, 30,
            "Choque elétrico que pula para outros monstros."),
    FAROL("farol", "Farol de Força", "§f", Material.SEA_LANTERN, Color.WHITE, 75, 0.0, 6, 20,
            "Não ataca: torres perto dão mais dano."),
    BALISTA("balista", "Balista", "§4", Material.CROSSBOW, Color.MAROON, 95, 32.0, 24, 50,
            "Tiro pesado no monstro com mais vida (boss)."),
    LASER("laser", "Canhão Laser", "§c", Material.BEACON, Color.RED, 140, 2.5, 16, 5,
            "Raio contínuo muito rápido num monstro só.");

    final String id;
    final String nome;
    final String cor;
    final Material icone;
    final Color corArmadura;
    final int custo;
    final double dano;
    final int alcance;
    final int intervalo;
    final String descricao;

    TipoTorre(String id, String nome, String cor, Material icone, Color corArmadura, int custo, double dano,
              int alcance, int intervalo, String descricao) {
        this.id = id;
        this.nome = nome;
        this.cor = cor;
        this.icone = icone;
        this.corArmadura = corArmadura;
        this.custo = custo;
        this.dano = dano;
        this.alcance = alcance;
        this.intervalo = intervalo;
        this.descricao = descricao;
    }

    /** Torres que nao atacam (suporte). */
    boolean suporte() {
        return this == MINA || this == FAROL;
    }

    double danoNivel(int nivel) {
        return dano * (1.0 + 0.5 * (nivel - 1));
    }

    double alcanceNivel(int nivel) {
        return alcance + (nivel - 1);
    }

    int intervaloNivel(int nivel) {
        return (int) Math.max(5, Math.round(intervalo * (1.0 - 0.15 * (nivel - 1))));
    }

    /** Mina: Essencia gerada no fim de cada onda. */
    static int essenciaMina(int nivel) {
        return nivel >= 3 ? 22 : nivel == 2 ? 14 : 8;
    }

    /** Farol: bonus de dano para torres perto (0.30 = +30%). */
    static double bonusFarol(int nivel) {
        return 0.30 + 0.15 * (nivel - 1);
    }

    /** Custo para subir do nivel atual para o proximo. */
    int custoEvolucao(int nivelAtual) {
        return nivelAtual == 1 ? (int) Math.round(custo * 0.8) : (int) Math.round(custo * 1.5);
    }

    static TipoTorre porId(String s) {
        if (s == null) return null;
        for (TipoTorre t : values()) {
            if (t.id.equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
