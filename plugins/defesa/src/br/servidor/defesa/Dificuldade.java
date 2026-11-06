package br.servidor.defesa;

import org.bukkit.Material;

/**
 * Dificuldades da partida. Facil/Normal/Dificil terminam com vitoria numa onda final;
 * Infinito continua ate o Nucleo cair. Cada uma limita quantas torres o time pode ter.
 */
enum Dificuldade {
    //       id          nome         cor   icone                     ondas vidaMonstro qtd   essInicial essDrop nucleo torres intervalo rampa
    FACIL("facil", "Fácil", "§a", Material.LIME_CONCRETE, 15, 0.75, 0.85, 60, 1.20, 150, 14, 20, 1.0),
    // Normal: o comeco era quase 3x mais pesado que o Facil (monstros com 67% mais vida e menos Essencia).
    // Agora comeca com mais Essencia e as ondas 1 a 4 vem mais fracas (75% da vida na 1a, 100% na 5a).
    NORMAL("normal", "Normal", "§e", Material.YELLOW_CONCRETE, 20, 1.25, 1.10, 55, 1.00, 100, 10, 15, 0.75),
    // Dificil (01/11): era 1.80 de vida, 1.35 de quantidade, 45 de Essencia, Nucleo 75 e 7 torres: ninguem passava da onda 5.
    // Agora da para vencer so com torres (pouco acima do Normal, com 5 ondas a mais).
    DIFICIL("dificil", "Difícil", "§c", Material.RED_CONCRETE, 25, 1.40, 1.15, 65, 1.05, 100, 11, 14, 0.8),
    INFINITO("infinito", "Infinito", "§5", Material.NETHER_STAR, 0, 1.00, 1.00, 45, 1.00, 100, 12, 15, 1.0);

    final String id;
    final String nome;
    final String cor;
    final Material icone;
    /** Onda final (vitoria ao completar). 0 = infinito. */
    final int ondas;
    final double vidaMonstros;
    final double quantidade;
    final int essenciaInicial;
    final double essenciaDrop;
    final int vidaNucleo;
    final int limiteTorres;
    final int intervalo;
    /** Vida dos monstros na onda 1 (fracao); sobe aos poucos ate 100% na onda 5. 1.0 = sem rampa. */
    final double rampa;

    Dificuldade(String id, String nome, String cor, Material icone, int ondas, double vidaMonstros, double quantidade,
                int essenciaInicial, double essenciaDrop, int vidaNucleo, int limiteTorres, int intervalo, double rampa) {
        this.id = id;
        this.nome = nome;
        this.cor = cor;
        this.icone = icone;
        this.ondas = ondas;
        this.vidaMonstros = vidaMonstros;
        this.quantidade = quantidade;
        this.essenciaInicial = essenciaInicial;
        this.essenciaDrop = essenciaDrop;
        this.vidaNucleo = vidaNucleo;
        this.limiteTorres = limiteTorres;
        this.intervalo = intervalo;
        this.rampa = rampa;
    }

    /** Quanto da vida normal os monstros tem nesta onda (rampa do comeco). */
    double rampaVida(int onda) {
        if (rampa >= 1.0 || onda >= 5) return 1.0;
        return rampa + (1.0 - rampa) * Math.max(0, onda - 1) / 4.0;
    }

    boolean infinito() {
        return ondas <= 0;
    }

    static Dificuldade porId(String s) {
        if (s == null) return null;
        String t = s.toLowerCase(java.util.Locale.ROOT).replace("í", "i").replace("á", "a");
        for (Dificuldade d : values()) {
            if (d.id.equals(t)) return d;
        }
        return null;
    }
}
