package br.servidor.defesa;

import java.time.LocalDate;
import java.time.ZoneId;

/** 13/11 (Paulo: "melhore tudo"): um desafio diferente por dia na Defesa da Torre (vale para todos os mapas). */
enum DesafioDia {
    FURIA("§c§l🔥 FÚRIA", "monstros 30% mais rápidos • Essência +40%", 1.0, 1.3, 1.4, 1.0),
    BLINDADOS("§7§l🛡 BLINDADOS", "monstros com +35% de vida • Essência +50%", 1.35, 1.0, 1.5, 1.0),
    FRAGIL("§d§l💔 NÚCLEO FRÁGIL", "Núcleo com metade da vida • Essência em DOBRO", 1.0, 1.0, 2.0, 0.5),
    MARE("§a§l🐢 MARÉ PESADA", "monstros 20% mais lentos, mas +60% de vida • Essência +25%", 1.6, 0.8, 1.25, 1.0),
    SORTE("§6§l💰 DIA DE SORTE", "Essência +75% (dia de bater recorde!)", 1.0, 1.0, 1.75, 1.0);

    final String nome, desc;
    final double vida, velocidade, essencia, nucleo;

    DesafioDia(String nome, String desc, double vida, double velocidade, double essencia, double nucleo) {
        this.nome = nome; this.desc = desc; this.vida = vida; this.velocidade = velocidade; this.essencia = essencia; this.nucleo = nucleo;
    }

    static DesafioDia hoje() {
        return values()[LocalDate.now(ZoneId.of("America/Sao_Paulo")).getDayOfYear() % values().length];
    }
}
