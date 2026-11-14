package br.servidor.extras;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 14/11: /semana — os 3 rankings semanais num lugar so (tempo jogado, chefes das masmorras, blocos da Prisao),
 * com o top 3 de cada e a posicao do jogador. Le os arquivos dos outros plugins (so leitura).
 */
final class Semana {
    private final Extras pl;

    Semana(Extras pl) { this.pl = pl; }

    record L(String uuid, String nome, int v) {}

    private static List<L> ler(File f, String secao, String campo) {
        List<L> l = new ArrayList<>();
        if (!f.exists()) return l;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection(secao);
        if (s == null) return l;
        for (String k : s.getKeys(false)) l.add(new L(k, s.getString(k + ".nome", k), s.getInt(k + "." + campo, 0)));
        l.sort((a, b) -> Integer.compare(b.v(), a.v()));
        return l;
    }

    private static File arq(String plugin, String nome) {
        Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
        return p == null ? new File("plugins/" + plugin + "/" + nome) : new File(p.getDataFolder(), nome);
    }

    private void bloco(Player p, String titulo, List<L> l, boolean porUuid, java.util.function.IntFunction<String> fmt, String premio) {
        p.sendMessage(Extras.txt(titulo + " §8(" + premio + "§8)"));
        if (l.isEmpty()) p.sendMessage(Extras.txt("  §7ninguém ainda — seja o primeiro!"));
        for (int i = 0; i < Math.min(3, l.size()); i++)
            p.sendMessage(Extras.txt("  §e" + (i + 1) + "º §f" + l.get(i).nome() + " §7" + fmt.apply(l.get(i).v())));
        int pos = -1;
        for (int i = 0; i < l.size(); i++) {
            L x = l.get(i);
            if (porUuid ? x.uuid().equalsIgnoreCase(p.getUniqueId().toString()) : x.nome().equalsIgnoreCase(p.getName())) { pos = i; break; }
        }
        p.sendMessage(Extras.txt(pos < 0 ? "  §8você ainda não pontuou" : "  §aVocê: §f" + (pos + 1) + "º §7(" + fmt.apply(l.get(pos).v()) + ")"));
    }

    void abrir(Player p) {
        p.sendMessage(Extras.txt("§6§l━━━ RANKINGS DA SEMANA §7(zeram segunda) §6§l━━━"));
        bloco(p, "§e⏱ Tempo jogado", ler(new File(pl.getDataFolder(), "tempo-semana.yml"), "lista", "min"), true,
                TempoSemanal::horas, "§6até 1000 coronas");
        bloco(p, "§c⚔ Chefes das Masmorras", ler(arq("Masmorras", "ranking.yml"), "semana.lista", "valor"), false,
                v -> v + " chefes", "§6até 1500 coronas");
        bloco(p, "§7⛏ Blocos na Prisão", ler(arq("Mineracao", "semana.yml"), "lista", "valor"), true,
                v -> String.format("%,d", v).replace(',', '.') + " blocos", "§2até $60.000");
    }
}
