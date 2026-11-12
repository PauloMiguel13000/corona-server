package br.servidor.extras;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Premio extra do voto: blocos de terreno (claim) do GriefPrevention. */
final class VotoClaim {
    private VotoClaim() {}

    static void dar(Player p, JavaPlugin pl) {
        try {
            if (p == null || pl == null) return;
            int n = pl.getConfig().getInt("votos.premio.blocos-de-claim", 150);
            if (n <= 0 || !Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) return;
            String nome = p.getName();
            Runnable r = () -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "adjustbonusclaimblocks " + nome + " " + n);
                    if (p.isOnline()) p.sendMessage("§a+" + n + " blocos de terreno §7pelo seu voto! Use a pá de ouro para marcar.");
                    pl.getLogger().info("Voto: +" + n + " blocos de terreno para " + nome);
                } catch (Throwable t) { pl.getLogger().warning("Voto: nao consegui dar blocos de terreno a " + nome + ": " + t); }
            };
            if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(pl, r);
        } catch (Throwable t) { /* o premio normal do voto nunca pode falhar por causa disto */ }
    }
}
