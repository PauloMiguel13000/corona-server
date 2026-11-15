package br.servidor.extras;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Pets (plugin BabyPets) so aparecem no survival, nas plots e nas masmorras (pedido do Paulo, 12/11): nos minigames, nos
 * jogos de terror e nos lobbies o pet some; quando o jogador volta para um mundo permitido, o pet escolhido reaparece.
 * Nao mexe no BabyPets: so chama o "guardar pet" e o "soltar pet" dele.
 */
final class SemPets implements Listener {
    private final Extras pl;
    private final Set<UUID> guardados = new HashSet<>();
    private Object gerente;
    private Method ativo, escolhido, guardar, soltar;
    private boolean avisou;

    SemPets(Extras pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::conferir, 100L, 40L);
    }

    private boolean permitido(String mundo) {
        // 13/11: a Prisao (Rank Up) e um mundo a parte, sem pets (mesmo que esteja na lista do survival)
        List<String> min = pl.getConfig().getStringList("mochilas.mineracao");
        if (mundo.equals("mineracao") || min.contains(mundo)) return false;
        List<String> l = pl.getConfig().getStringList("pets.mundos-permitidos");
        if (!l.isEmpty()) return l.contains(mundo);
        return mundo.equals("masmorras_spawn") || pl.getConfig().getStringList("mochilas.survival").contains(mundo)
                || pl.getConfig().getStringList("mochilas.masmorras").contains(mundo);
    }

    private boolean pronto() {
        if (gerente != null) return true;
        try {
            Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
            if (bp == null || !bp.isEnabled()) return false;
            Object g = bp.getClass().getMethod("getPetManager").invoke(bp);
            ativo = g.getClass().getMethod("getActivePet", UUID.class);
            escolhido = g.getClass().getMethod("getSelectedPet", UUID.class);
            guardar = g.getClass().getMethod("despawnPet", UUID.class, boolean.class);
            for (Method m : g.getClass().getMethods()) if (m.getName().equals("spawnPet") && m.getParameterCount() == 2) soltar = m;
            if (soltar == null) return false;
            gerente = g;
            pl.getLogger().info("Pets: so aparecem no survival, nas plots e nas masmorras.");
            return true;
        } catch (Throwable t) {
            if (!avisou) pl.getLogger().warning("Pets fora dos minigames: nao consegui falar com o BabyPets (" + t + ")");
            avisou = true;
            return false;
        }
    }

    private void conferir() {
        if (!pl.getConfig().getBoolean("pets.esconder-fora", true) || !pronto()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                UUID id = p.getUniqueId();
                boolean ok = permitido(p.getWorld().getName());
                if (!ok) {
                    if (ativo.invoke(gerente, id) != null) {
                        guardar.invoke(gerente, id, false);
                        guardados.add(id);
                    }
                } else if (guardados.remove(id) && !p.isDead()) {
                    Object pet = escolhido.invoke(gerente, id);
                    if (pet != null && ativo.invoke(gerente, id) == null) soltar.invoke(gerente, p, pet);
                }
            } catch (Throwable t) {
                if (!avisou) pl.getLogger().warning("Pets fora dos minigames: " + t);
                avisou = true;
            }
        }
    }

    /** Some com o pet logo na chegada (sem esperar a conferencia de 2 em 2 segundos). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void mudou(PlayerChangedWorldEvent e) {
        // 15/11: o BabyPets, ao trocar de mundo, guarda o pet e marca para solta-lo de novo em meio segundo (e avisa
        // "apareceu do seu lado"). Em mundo sem pets cancelamos essa volta marcada; o pet fica guardado ate o jogador
        // voltar para um mundo com pets.
        if (pl.getConfig().getBoolean("pets.esconder-fora", true) && pronto() && !permitido(e.getPlayer().getWorld().getName())) {
            try {
                UUID id = e.getPlayer().getUniqueId();
                for (org.bukkit.plugin.RegisteredListener rl : PlayerChangedWorldEvent.getHandlerList().getRegisteredListeners()) {
                    Object l = rl.getListener();
                    if (!l.getClass().getName().equals("com.petsplugin.listener.PlayerListener")) continue;
                    Method m = l.getClass().getDeclaredMethod("cancelPendingPetRespawn", UUID.class);
                    m.setAccessible(true);
                    m.invoke(l, id);
                    if (escolhido.invoke(gerente, id) != null) guardados.add(id);
                    break;
                }
            } catch (Throwable t) {
                if (!avisou) pl.getLogger().warning("Pets: nao consegui cancelar a volta do pet (" + t + ")");
                avisou = true;
            }
        }
        for (long t : new long[] {5L, 30L, 70L}) Bukkit.getScheduler().runTaskLater(pl, this::conferir, t);
    }

    /**
     * 15/11: indo para um mundo sem pets, guarda o pet ANTES do teleporte. Assim o BabyPets nao leva o pet junto
     * (ele reaparecia por um instante e mandava "apareceu do seu lado" em todo lobby e minigame).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void indo(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null || e.getTo().getWorld().equals(e.getFrom().getWorld())) return;
        if (!pl.getConfig().getBoolean("pets.esconder-fora", true) || !pronto() || permitido(e.getTo().getWorld().getName())) return;
        try {
            UUID id = e.getPlayer().getUniqueId();
            if (ativo.invoke(gerente, id) != null) {
                guardar.invoke(gerente, id, false);
                guardados.add(id);
            }
        } catch (Throwable t) {
            // a conferencia de 2 em 2 segundos cuida
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        guardados.remove(e.getPlayer().getUniqueId());
    }
}
