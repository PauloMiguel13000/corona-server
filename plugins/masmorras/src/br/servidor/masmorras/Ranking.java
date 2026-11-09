package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Rankings do servidor para os placares do lobby: chefes derrotados (quem lutou contra o chefe ganha 1) e maior
 * nivel de poder (lido do AuraSkills de quem esta online, a cada minuto). Fica em ranking.yml.
 */
final class Ranking {

    record Linha(String nome, int valor) {}

    private final Masmorras pl;
    private final File arquivo;
    private final YamlConfiguration dados;
    private boolean sujo;

    Ranking(Masmorras pl) {
        this.pl = pl;
        this.arquivo = new File(pl.getDataFolder(), "ranking.yml");
        this.dados = YamlConfiguration.loadConfiguration(arquivo);
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::lerNiveis, 200L, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(pl, this::salvar, 20L * 90, 20L * 90);
    }

    private static String chave(String nome) {
        return nome.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    /** Mais um chefe derrotado para o jogador. */
    void chefeDerrotado(Player p) {
        String k = "chefes." + chave(p.getName());
        dados.set(k + ".nome", p.getName());
        dados.set(k + ".valor", dados.getInt(k + ".valor", 0) + 1);
        sujo = true;
    }

    int chefes(Player p) {
        return dados.getInt("chefes." + chave(p.getName()) + ".valor", 0);
    }

    private void lerNiveis() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int n = Aura.poder(p);
            if (n < 0) continue;
            String k = "nivel." + chave(p.getName());
            if (n != dados.getInt(k + ".valor", -1)) {
                dados.set(k + ".nome", p.getName());
                dados.set(k + ".valor", n);
                sujo = true;
            }
        }
    }

    /** Os n melhores (chefes ou nivel). */
    List<Linha> top(String qual, int n) {
        List<Linha> l = new ArrayList<>();
        ConfigurationSection s = dados.getConfigurationSection(qual);
        if (s != null)
            for (String k : s.getKeys(false)) l.add(new Linha(s.getString(k + ".nome", k), s.getInt(k + ".valor", 0)));
        l.sort(Comparator.comparingInt(Linha::valor).reversed().thenComparing(Linha::nome, String.CASE_INSENSITIVE_ORDER));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    /** Posicao (1 = primeiro) do jogador no ranking, ou 0 se nao esta. */
    int posicao(String qual, Player p) {
        List<Linha> l = top(qual, Integer.MAX_VALUE);
        for (int i = 0; i < l.size(); i++) if (l.get(i).nome().equalsIgnoreCase(p.getName())) return i + 1;
        return 0;
    }

    void salvar() {
        if (!sujo) return;
        sujo = false;
        try {
            dados.save(arquivo);
        } catch (IOException e) {
            pl.getLogger().warning("Nao consegui salvar ranking.yml: " + e.getMessage());
        }
    }

    /** Texto do placar (para o letreiro do lobby). */
    String placar(String titulo, String qual, String unidade, String cor) {
        StringBuilder sb = new StringBuilder(titulo);
        List<Linha> l = top(qual, 10);
        if (l.isEmpty()) sb.append("\n§7ninguém ainda... seja o primeiro!");
        for (int i = 0; i < l.size(); i++) {
            Linha x = l.get(i);
            String medalha = i == 0 ? "§6§l1º" : i == 1 ? "§f§l2º" : i == 2 ? "§c§l3º" : "§7" + (i + 1) + "º";
            sb.append('\n').append(medalha).append(" §f").append(x.nome()).append(" ").append(cor).append(x.valor()).append(" §7").append(unidade);
        }
        return sb.toString();
    }

    static Map<String, Object> vazio() {
        return Map.of();
    }
}
