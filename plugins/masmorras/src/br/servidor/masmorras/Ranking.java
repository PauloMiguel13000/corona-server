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
        // 13/11: ranking da semana (zera toda segunda; top 3 ganha coronas)
        virarSemana();
        String w = "semana.lista." + chave(p.getName());
        dados.set(w + ".nome", p.getName());
        dados.set(w + ".valor", dados.getInt(w + ".valor", 0) + 1);
        sujo = true;
    }

    static final int[] PREMIO_SEMANA = {1500, 1000, 500};

    static int semanaAtual() {
        java.time.LocalDate d = java.time.LocalDate.now(java.time.ZoneId.of("America/Bahia"));
        return d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100 + d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** Semana nova: o top 3 da semana que passou ganha coronas (pagas quando estiver online) e a lista zera. */
    private void virarSemana() {
        int atual = semanaAtual(), id = dados.getInt("semana.id", 0);
        if (id == atual) return;
        if (id != 0) {
            List<Linha> top = top("semana.lista", 3);
            StringBuilder sb = new StringBuilder("§6§l🏆 Ranking semanal das Masmorras encerrado!");
            for (int i = 0; i < top.size(); i++) {
                Linha x = top.get(i);
                String k = "premio." + chave(x.nome());
                dados.set(k + ".nome", x.nome());
                dados.set(k + ".valor", dados.getInt(k + ".valor", 0) + PREMIO_SEMANA[i]);
                sb.append("\n§e").append(i + 1).append("º §f").append(x.nome()).append(" §7(").append(x.valor()).append(" chefes) §6+").append(PREMIO_SEMANA[i]).append(" coronas");
            }
            if (!top.isEmpty()) Bukkit.broadcast(Masmorras.txt(sb.toString()));
        }
        dados.set("semana.lista", null);
        dados.set("semana.id", atual);
        sujo = true;
    }

    /** Paga quem tem premio semanal guardado e esta online. */
    private void pagarPremios() {
        if (pl.coronas == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String k = "premio." + chave(p.getName());
            int v = dados.getInt(k + ".valor", 0);
            if (v <= 0) continue;
            dados.set(k, null);
            sujo = true;
            pl.coronas.definir(p, pl.coronas.saldo(p) + v);
            p.sendMessage(Masmorras.txt("§6§l🏆 Prêmio do ranking semanal das Masmorras: §e+" + v + " coronas§6!"));
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
    }

    int chefes(Player p) {
        return dados.getInt("chefes." + chave(p.getName()) + ".valor", 0);
    }

    private void lerNiveis() {
        virarSemana();
        pagarPremios();
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

    /** Top 3 da semana (vai embaixo do placar de chefes). */
    String placarSemana() {
        StringBuilder sb = new StringBuilder("\n\n§6§lRANKING DA SEMANA §7(zera segunda)");
        List<Linha> l = top("semana.lista", 3);
        if (l.isEmpty()) sb.append("\n§7ninguém ainda... derrote um chefe!");
        for (int i = 0; i < l.size(); i++)
            sb.append("\n§e").append(i + 1).append("º §f").append(l.get(i).nome()).append(" §c").append(l.get(i).valor()).append(" §8→ §6").append(PREMIO_SEMANA[i]).append(" coronas");
        return sb.toString();
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
