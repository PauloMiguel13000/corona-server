package br.servidor.extras;

import java.time.LocalDate;
import java.time.ZoneId;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 20/11 (Paulo: "dar blocos de claim extras quando entrar, quando votar e a cada 12h jogadas"): blocos de terreno do
 * GriefPrevention. Primeira vez no servidor: claim.primeira-vez (300); primeiro login de cada dia: claim.por-dia (100);
 * a cada 12 horas jogadas: claim.a-cada-12h (500). O do voto continua no VotoClaim (votos.premio.blocos-de-claim).
 */
final class ClaimBonus implements Listener {

    private final Extras pl;
    private final NamespacedKey kDia, kMin, kPrimeira;

    ClaimBonus(Extras pl) {
        this.pl = pl;
        kDia = new NamespacedKey(pl, "claim_ultimo_dia");
        kMin = new NamespacedKey(pl, "claim_minutos");
        kPrimeira = new NamespacedKey(pl, "claim_primeira");
    }

    void iniciar() {
        // a cada 5 minutos soma 5 minutos de jogo para quem esta online
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PersistentDataContainer d = p.getPersistentDataContainer();
                int m = d.getOrDefault(kMin, PersistentDataType.INTEGER, 0) + 5;
                if (m >= 720) {
                    m -= 720;
                    dar(p, pl.getConfig().getInt("claim.a-cada-12h", 500), "por 12 horas jogadas");
                }
                d.set(kMin, PersistentDataType.INTEGER, m);
            }
        }, 6000L, 6000L);
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PersistentDataContainer d = p.getPersistentDataContainer();
        String hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
        boolean primeira = !d.has(kPrimeira);
        String ultimo = d.get(kDia, PersistentDataType.STRING);
        d.set(kPrimeira, PersistentDataType.BYTE, (byte) 1);
        d.set(kDia, PersistentDataType.STRING, hoje);
        int n = 0;
        String porque = null;
        if (primeira) {
            n += pl.getConfig().getInt("claim.primeira-vez", 300);
            porque = "de boas-vindas";
        }
        if (!hoje.equals(ultimo)) {
            n += pl.getConfig().getInt("claim.por-dia", 100);
            if (porque == null) porque = "pelo login de hoje";
        }
        if (n > 0) {
            int total = n;
            String motivo = porque;
            Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) dar(p, total, motivo); }, 100L);
        }
    }

    private void dar(Player p, int n, String porque) {
        if (n <= 0 || !Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) return;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "adjustbonusclaimblocks " + p.getName() + " " + n);
            p.sendMessage(Extras.txt("§a+" + n + " blocos de terreno §7" + porque + "! §8(pá de ouro para marcar o terreno)"));
        } catch (Throwable t) {
            pl.getLogger().warning("Blocos de terreno para " + p.getName() + ": " + t);
        }
    }
}
