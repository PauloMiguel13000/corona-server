package br.servidor.masmorras;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * 14/11 (Paulo: "melhore muito"): CONTRATO DO DIA nas masmorras — todo dia uma masmorra diferente pede
 * "derrote 30 monstros dela"; quem cumprir ganha coronas (60 x nivel, minimo 150) uma vez por dia. /contrato.
 */
final class ContratoDia {
    static final int ALVO = 30;
    private final Masmorras pl;
    private final NamespacedKey kDia, kQtd;

    ContratoDia(Masmorras pl) {
        this.pl = pl;
        kDia = new NamespacedKey(pl, "contrato_dia");
        kQtd = new NamespacedKey(pl, "contrato_qtd");
    }

    static long hoje() { return LocalDate.now(ZoneId.of("America/Sao_Paulo")).toEpochDay(); }

    static Tipo alvo() {
        List<Tipo> l = new ArrayList<>();
        for (Tipo t : Tipo.TODOS.values()) if (!t.secreta && t.nivel <= 6) l.add(t);
        if (l.isEmpty()) return null;
        l.sort(Comparator.comparing(t -> t.id));
        Tipo t = l.get((int) ((hoje() * 7 + 3) % l.size()));
        if (t == Destaque.hoje() && l.size() > 1) t = l.get((int) ((hoje() * 7 + 4) % l.size()));
        return t;
    }

    static long premio(Tipo t) { return Math.max(150, 60L * t.nivel); }

    private int qtd(Player p) {
        Long d = p.getPersistentDataContainer().get(kDia, PersistentDataType.LONG);
        if (d == null || d != hoje()) return 0;
        return p.getPersistentDataContainer().getOrDefault(kQtd, PersistentDataType.INTEGER, 0);
    }

    void monstro(Player p, Tipo t) {
        Tipo a = alvo();
        if (a == null || t != a) return;
        int q = qtd(p);
        if (q >= ALVO) return;
        q++;
        p.getPersistentDataContainer().set(kDia, PersistentDataType.LONG, hoje());
        p.getPersistentDataContainer().set(kQtd, PersistentDataType.INTEGER, q);
        if (q >= ALVO) {
            long v = premio(a);
            pl.coronas.dar(p, pl.coronas.comVip(p, v), false);
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§a§l📜 CONTRATO DO DIA CUMPRIDO! §6+" + Coronas.fmt(v) + " coronas §7(amanhã tem outro)"));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        } else if (q % 10 == 0 || q == 1) {
            p.sendActionBar(Masmorras.txt("§e📜 Contrato do dia: §f" + q + "/" + ALVO + " §7monstros em §f" + a.nome));
        }
    }

    void mostrar(Player p) {
        Tipo a = alvo();
        if (a == null) { p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§7Sem contrato hoje.")); return; }
        int q = qtd(p);
        p.sendMessage(Masmorras.txt("§e§l📜 CONTRATO DO DIA §7(muda à meia-noite)"));
        p.sendMessage(Masmorras.txt("§fDerrote §e" + ALVO + " monstros §fna masmorra §6" + a.nome + " §7(nível " + a.nivel + ")"));
        p.sendMessage(Masmorras.txt("§7Progresso: " + (q >= ALVO ? "§a§lCUMPRIDO ✔" : "§f" + q + "/" + ALVO) + " §8• §7prêmio: §6" + Coronas.fmt(premio(a)) + " coronas"));
    }
}
