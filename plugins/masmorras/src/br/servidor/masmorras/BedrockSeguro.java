package br.servidor.masmorras;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Protege quem joga no Bedrock (celular): o Geyser desta versao derruba a sessao ao responder a bussola com ima
 * (lodestone) - erro "PositionTrackingDBServerBroadcastPacket ... tag=null". Para jogador Bedrock, toda bussola com ima
 * vira bussola normal e a agulha passa a apontar para o mesmo lugar pelo alvo de bussola do jogador.
 * Tambem limita a distancia de chunks enviada ao Bedrock (menos dados pela internet do celular).
 */
final class BedrockSeguro implements Listener {
    private final Masmorras pl;

    BedrockSeguro(Masmorras pl) {
        this.pl = pl;
    }

    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0;
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) if (bedrock(p)) limpar(p);
        }, 20L, 10L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!bedrock(p)) return;
        limpar(p);
        int vd = pl.getConfig().getInt("bedrock.distancia-chunks", 5);
        if (vd >= 2) {
            try { p.setSendViewDistance(Math.min(vd, Bukkit.getViewDistance())); } catch (Throwable ignored) { }
        }
        Bukkit.getScheduler().runTask(pl, () -> limpar(p));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void trocouMundo(PlayerChangedWorldEvent e) {
        if (bedrock(e.getPlayer())) limpar(e.getPlayer());
    }

    /** Tira o ima de todas as bussolas do inventario (e do cursor) de um jogador Bedrock. */
    @SuppressWarnings("UnstableApiUsage")
    void limpar(Player p) {
        if (!p.isOnline()) return;
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            if (semIma(p, it)) inv.setItem(i, it);
        }
        ItemStack cur = p.getItemOnCursor();
        if (semIma(p, cur)) p.setItemOnCursor(cur);
    }

    @SuppressWarnings("UnstableApiUsage")
    private static boolean semIma(Player p, ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS || !it.hasData(DataComponentTypes.LODESTONE_TRACKER)) return false;
        LodestoneTracker lt = it.getData(DataComponentTypes.LODESTONE_TRACKER);
        Location alvo = lt == null ? null : lt.location();
        it.resetData(DataComponentTypes.LODESTONE_TRACKER);
        if (alvo != null && alvo.getWorld() != null && alvo.getWorld().equals(p.getWorld())) p.setCompassTarget(alvo);
        return true;
    }
}
