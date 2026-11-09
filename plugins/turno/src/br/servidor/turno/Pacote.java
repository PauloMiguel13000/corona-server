package br.servidor.turno;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

/**
 * Quem carregou o pacote de recursos do servidor (o mesmo controle do Backrooms/Hotel). Quem esta no Java e recusou o
 * pacote veria um cubo roxo no lugar do modelo 3D: essa pessoa continua vendo o boneco com skin. O Bedrock sempre tem
 * o pacote (o Geyser manda o .mcpack na entrada).
 */
final class Pacote implements Listener {

    /** Mesmo id que o CoronaPack usa ao mandar o pacote. */
    static final UUID ID = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(StandardCharsets.UTF_8));
    private static final Set<UUID> COM_PACOTE = ConcurrentHashMap.newKeySet();

    @EventHandler(priority = EventPriority.MONITOR)
    public void status(PlayerResourcePackStatusEvent e) {
        if (!ID.equals(e.getID())) return;
        UUID id = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> COM_PACOTE.add(id);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> COM_PACOTE.remove(id);
            default -> { }
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        COM_PACOTE.remove(e.getPlayer().getUniqueId());
    }

    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    static boolean tem(Player p) {
        return bedrock(p) || COM_PACOTE.contains(p.getUniqueId());
    }

    /** Mostra o modelo 3D para quem tem o pacote e o boneco com skin para quem nao tem (cada jogador ve so um). */
    static void repartir(Plugin pl, Entity boneco, Entity modelo) {
        if (boneco == null || !boneco.isValid()) return;
        boolean temModelo = modelo != null && modelo.isValid();
        for (Player p : boneco.getWorld().getPlayers()) {
            boolean ve3d = temModelo && tem(p);
            ver(pl, p, boneco, !ve3d);
            if (temModelo) ver(pl, p, modelo, ve3d);
        }
    }

    private static void ver(Plugin pl, Player p, Entity e, boolean sim) {
        if (sim == p.canSee(e)) return;
        if (sim) p.showEntity(pl, e);
        else p.hideEntity(pl, e);
    }
}
