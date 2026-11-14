package br.servidor.mineracao;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 13/11 (Paulo: "continue melhorando"): ranking SEMANAL da Prisao — quem mais quebrar blocos nas minas na semana.
 * Toda segunda (horario da Bahia) o top 3 ganha dinheiro da Prisao ($) e a contagem zera. Quem estiver offline
 * recebe quando logar (dinheiro-pendente do /dinheiro). Fica em semana.yml.
 */
final class SemanaPrisao {
    static final long[] PREMIO = {60_000, 35_000, 15_000};
    private final Mineracao pl;
    private final File arquivo;
    private final YamlConfiguration dados;
    private boolean sujo;

    record Linha(String uuid, String nome, int valor) {}

    SemanaPrisao(Mineracao pl) {
        this.pl = pl;
        arquivo = new File(pl.getDataFolder(), "semana.yml");
        dados = YamlConfiguration.loadConfiguration(arquivo);
        Bukkit.getScheduler().runTaskTimer(pl, () -> { virar(); salvar(); }, 20L * 60, 20L * 120);
    }

    static int semanaAtual() {
        java.time.LocalDate d = java.time.LocalDate.now(java.time.ZoneId.of("America/Bahia"));
        return d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100 + d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    void somar(Player p) {
        String k = "lista." + p.getUniqueId();
        dados.set(k + ".nome", p.getName());
        dados.set(k + ".valor", dados.getInt(k + ".valor", 0) + 1);
        sujo = true;
    }

    int meus(Player p) { return dados.getInt("lista." + p.getUniqueId() + ".valor", 0); }

    List<Linha> top(int n) {
        List<Linha> l = new ArrayList<>();
        ConfigurationSection s = dados.getConfigurationSection("lista");
        if (s != null) for (String k : s.getKeys(false)) l.add(new Linha(k, s.getString(k + ".nome", "?"), s.getInt(k + ".valor", 0)));
        l.sort((a, b) -> Integer.compare(b.valor(), a.valor()));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    List<String> linhas() {
        List<String> r = new ArrayList<>();
        List<Linha> t = top(3);
        r.add("§6§lTop da SEMANA §7(blocos • zera segunda)");
        if (t.isEmpty()) r.add("§7ninguém ainda nesta semana!");
        for (int i = 0; i < t.size(); i++)
            r.add("§e" + (i + 1) + "º §f" + t.get(i).nome() + " §7" + Mineracao.fmt(t.get(i).valor()) + " blocos §8→ " + Mineracao.din(PREMIO[i]));
        return r;
    }

    private void virar() {
        int atual = semanaAtual(), id = dados.getInt("id", 0);
        if (id == atual) return;
        if (id != 0) {
            List<Linha> t = top(3);
            StringBuilder sb = new StringBuilder("§6§l⛓ Ranking semanal da Prisão encerrado!");
            for (int i = 0; i < t.size(); i++) {
                Linha x = t.get(i);
                sb.append("\n§e").append(i + 1).append("º §f").append(x.nome()).append(" §7(").append(Mineracao.fmt(x.valor())).append(" blocos) ").append(Mineracao.din(PREMIO[i]));
                Player on = Bukkit.getPlayer(UUID.fromString(x.uuid()));
                if (on != null) {
                    pl.setDinheiro(on, pl.dinheiro(on) + PREMIO[i]);
                    on.sendMessage(Mineracao.txt("§6§l⛓ Prêmio do ranking semanal da Prisão: " + Mineracao.din(PREMIO[i]) + "§6!"));
                    on.playSound(on.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                } else {
                    String k = "dinheiro-pendente." + x.uuid();
                    pl.getConfig().set(k, pl.getConfig().getLong(k, 0) + PREMIO[i]);
                    pl.saveConfig();
                }
            }
            if (!t.isEmpty()) Bukkit.broadcast(Mineracao.txt(sb.toString()));
        }
        dados.set("lista", null);
        dados.set("id", atual);
        sujo = true;
    }

    void salvar() {
        if (!sujo) return;
        sujo = false;
        try { dados.save(arquivo); } catch (IOException e) { pl.getLogger().warning("semana.yml: " + e.getMessage()); }
    }
}
