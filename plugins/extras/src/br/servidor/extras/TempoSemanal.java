package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 13/11 (Paulo: "continue melhorando"): ranking SEMANAL de tempo jogado (sem contar AFK). Toda segunda (Bahia)
 * o top 3 ganha coronas (1000/600/300) — offline recebe ao logar (pelo /corona adm dar). Fica em tempo-semana.yml.
 * /tempo mostra o seu tempo e o top.
 */
final class TempoSemanal {
    static final int[] PREMIO = {1000, 600, 300};
    private final Extras pl;
    private final File arquivo;
    private final YamlConfiguration dados;
    private final Map<UUID, Location> ultimaPos = new HashMap<>();
    private final Map<UUID, Integer> parado = new HashMap<>();

    record Linha(String uuid, String nome, int min) {}

    TempoSemanal(Extras pl) {
        this.pl = pl;
        arquivo = new File(pl.getDataFolder(), "tempo-semana.yml");
        dados = YamlConfiguration.loadConfiguration(arquivo);
        Bukkit.getScheduler().runTaskTimer(pl, this::minuto, 20L * 60, 20L * 60);
    }

    static int semanaAtual() {
        java.time.LocalDate d = java.time.LocalDate.now(java.time.ZoneId.of("America/Bahia"));
        return d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100 + d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private void minuto() {
        virar();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation(), a = ultimaPos.put(p.getUniqueId(), l);
            boolean mexeu = a == null || a.getWorld() != l.getWorld() || a.distanceSquared(l) > 1 || Math.abs(a.getYaw() - l.getYaw()) > 1;
            int pr = mexeu ? 0 : parado.getOrDefault(p.getUniqueId(), 0) + 1;
            parado.put(p.getUniqueId(), pr);
            if (pr >= 3) continue;   // 3 minutos parado = AFK, nao conta
            String k = "lista." + p.getUniqueId();
            dados.set(k + ".nome", p.getName());
            dados.set(k + ".min", dados.getInt(k + ".min", 0) + 1);
        }
        try { dados.save(arquivo); } catch (IOException ignored) {}
    }

    List<Linha> top(int n) {
        List<Linha> l = new ArrayList<>();
        ConfigurationSection s = dados.getConfigurationSection("lista");
        if (s != null) for (String k : s.getKeys(false)) l.add(new Linha(k, s.getString(k + ".nome", "?"), s.getInt(k + ".min", 0)));
        l.sort((a, b) -> Integer.compare(b.min(), a.min()));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    static String horas(int min) { return (min / 60) + "h" + String.format("%02d", min % 60); }

    int meus(Player p) { return dados.getInt("lista." + p.getUniqueId() + ".min", 0); }

    String texto() {
        StringBuilder sb = new StringBuilder("§6§l⏱ Mais tempo jogando na SEMANA §7(zera segunda)");
        List<Linha> t = top(3);
        if (t.isEmpty()) sb.append("\n§7ninguém ainda!");
        for (int i = 0; i < t.size(); i++)
            sb.append("\n§e").append(i + 1).append("º §f").append(t.get(i).nome()).append(" §7").append(horas(t.get(i).min())).append(" §8→ §6").append(PREMIO[i]).append(" coronas");
        return sb.toString();
    }

    private void virar() {
        int atual = semanaAtual(), id = dados.getInt("id", 0);
        if (id == atual) return;
        if (id != 0) {
            List<Linha> t = top(3);
            StringBuilder sb = new StringBuilder("§6§l⏱ Ranking semanal de tempo jogado encerrado!");
            for (int i = 0; i < t.size(); i++) {
                Linha x = t.get(i);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas adm dar " + x.nome() + " " + PREMIO[i]);
                sb.append("\n§e").append(i + 1).append("º §f").append(x.nome()).append(" §7(").append(horas(x.min())).append(") §6+").append(PREMIO[i]).append(" coronas");
            }
            if (!t.isEmpty()) Bukkit.broadcast(Extras.txt(sb.toString()));
        }
        dados.set("lista", null);
        dados.set("id", atual);
    }

    void comando(Player p) {
        for (String l : texto().split("\n")) p.sendMessage(Extras.txt(l));
        p.sendMessage(Extras.txt("§7Você nesta semana: §f" + horas(meus(p)) + " §8(parado/AFK não conta)"));
    }
}
