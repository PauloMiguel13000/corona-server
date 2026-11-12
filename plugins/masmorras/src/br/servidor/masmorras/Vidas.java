package br.servidor.masmorras;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 3 vidas por masmorra (pedido do Paulo, 12/11): quem morre dentro de uma masmorra renasce na primeira sala dela.
 * Na terceira morte, volta para o lobby das masmorras e as vidas recomecam. As vidas tambem recomecam depois de
 * 20 minutos sem morrer, ao trocar de masmorra e ao sair do servidor.
 */
final class Vidas implements Listener {
    static final int VIDAS = 3;
    private static final long VALIDADE_MS = 20L * 60L * 1000L;

    private static final class Estado {
        int masmorra, restam;
        long ultima;
    }

    private final Masmorras pl;
    private final Map<UUID, Estado> estados = new HashMap<>();
    private final Map<UUID, Masmorra> morreuEm = new HashMap<>();

    Vidas(Masmorras pl) {
        this.pl = pl;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morreu(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Location l = p.getLocation();
        Masmorra m = pl.masmorraEm(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (m == null || m.salas.isEmpty()) morreuEm.remove(p.getUniqueId());
        else morreuEm.put(p.getUniqueId(), m);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void renasceu(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Masmorra m = morreuEm.remove(p.getUniqueId());
        if (m == null || m.demolindo || m.salas.isEmpty()) return;
        World w = Bukkit.getWorld(m.mundo);
        if (w == null) return;
        long agora = System.currentTimeMillis();
        Estado s = estados.get(p.getUniqueId());
        if (s == null || s.masmorra != m.id || agora - s.ultima > VALIDADE_MS) {
            s = new Estado();
            s.masmorra = m.id;
            s.restam = VIDAS;
            estados.put(p.getUniqueId(), s);
        }
        s.ultima = agora;
        s.restam--;
        final int restam = s.restam;
        pl.getLogger().info("Vidas: " + p.getName() + " morreu na masmorra #" + m.id + " (" + m.tipo.nome + "), restam " + restam + "/" + VIDAS + (restam > 0 ? " - renasce na entrada" : " - volta para o lobby"));
        if (restam > 0) {
            Caixa sala = m.salas.get(0);
            e.setRespawnLocation(new Location(w, sala.cx() + 0.5, sala.y1(), sala.cz() + 0.5, 180f, 0f));
        } else {
            estados.remove(p.getUniqueId());
            if (pl.mundoNovo != null && pl.mundoNovo.normal != null) e.setRespawnLocation(pl.mundoNovo.spawnLobby());
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            if (restam > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 4, false, false, true));
                String cor = restam >= 2 ? "§e" : "§c";
                p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt(cor + "❤".repeat(restam) + "§8" + "❤".repeat(VIDAS - restam)),
                        Masmorras.txt("§f" + (restam == 1 ? "Última vida" : restam + " vidas") + " §7nesta masmorra — " + m.tipo.nome)));
                p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Você renasceu na entrada da §f" + m.tipo.nome + "§7. Vidas: " + cor + restam + "§7/" + VIDAS
                        + (restam == 1 ? " §c(se morrer de novo, volta para o lobby)" : "")));
                p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
            } else {
                p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§4§lSEM VIDAS"), Masmorras.txt("§7Você perdeu as " + VIDAS + " vidas na §f" + m.tipo.nome)));
                p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Suas §c" + VIDAS + " vidas §7na §f" + m.tipo.nome + " §7acabaram: você voltou para o lobby. Ao entrar de novo, começa com " + VIDAS + "."));
                p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 0.8f);
            }
        }, 5L);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        estados.remove(e.getPlayer().getUniqueId());
        morreuEm.remove(e.getPlayer().getUniqueId());
    }
}
