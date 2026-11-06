package br.servidor.hotel;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Sons do Hotel. Quem carregou o pacote do servidor (plugin CoronaPack) ouve os sons proprios
 * ("hotel.*"); quem recusou o pacote ouve um som parecido do Minecraft, entao ninguem fica sem som.
 * Jogador do Bedrock (Geyser) recebe o pacote .mcpack na entrada, entao conta como "tem o pacote".
 */
final class Sons implements Listener {

    /** Mesmo id que o CoronaPack usa ao mandar o pacote. */
    static final UUID PACOTE = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(StandardCharsets.UTF_8));
    private static final Set<UUID> COM_PACOTE = ConcurrentHashMap.newKeySet();

    @EventHandler(priority = EventPriority.MONITOR)
    public void status(PlayerResourcePackStatusEvent e) {
        if (!PACOTE.equals(e.getID())) return;
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
        String pre = e.getPlayer().getUniqueId() + ":";
        ULTIMO.keySet().removeIf(k -> k.startsWith(pre));
    }

    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    static boolean temPacote(Player p) {
        return bedrock(p) || COM_PACOTE.contains(p.getUniqueId());
    }

    /**
     * Toca so para um jogador, no local dado.
     * @param nome som do pacote (sem o "hotel.")
     * @param reserva som do Minecraft para quem nao tem o pacote (null = nada)
     */
    static void para(Player p, Location l, String nome, float vol, float tom, Sound reserva, float volR, float tomR) {
        if (p == null || l == null || !p.getWorld().equals(l.getWorld())) return;
        if (temPacote(p)) p.playSound(l, "hotel." + nome, SoundCategory.HOSTILE, vol, tom);
        // Bedrock: o som proprio do pacote nem sempre toca pelo Geyser, entao toca tambem o som parecido do Minecraft (esse sempre chega)
        if ((!temPacote(p) || bedrock(p)) && reserva != null) p.playSound(l, reserva, SoundCategory.HOSTILE, volR, tomR);
    }

    /** Toca no local para todos do mundo que estao ate "raio" blocos. */
    static void local(World w, Location l, double raio, String nome, float vol, float tom, Sound reserva, float volR, float tomR) {
        if (w == null || l == null) return;
        double r2 = raio * raio;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= r2) para(p, l, nome, vol, tom, reserva, volR, tomR);
        }
    }

    private static final java.util.Map<String, Long> ULTIMO = new ConcurrentHashMap<>();

    /** Como local(), mas cada jogador so ouve esse som de novo depois de "pausaMs" (evita varios iguais ao mesmo tempo). */
    static void localComPausa(World w, Location l, double raio, String nome, float vol, float tom, Sound reserva, float volR, float tomR, long pausaMs) {
        if (w == null || l == null) return;
        double r2 = raio * raio;
        long agora = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(l) > r2) continue;
            String k = p.getUniqueId() + ":" + nome;
            Long antes = ULTIMO.get(k);
            if (antes != null && agora - antes < pausaMs) continue;
            ULTIMO.put(k, agora);
            para(p, l, nome, vol, tom, reserva, volR, tomR);
        }
    }

    private Sons() {}

    static Sons ouvinte() {
        return new Sons();
    }
}
