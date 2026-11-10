package br.servidor.fabrica;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Modelo 3D do pacote (Tutu e Dona Bolota, corona:props/anim_<id>_f<quadro>) que acompanha o boneco com skin.
 * Quem tem o pacote (ou esta no Bedrock) ve o modelo; quem nao tem ve o boneco. O modelo segue o boneco a cada tick
 * (posicao, lado e tamanho) e some junto com ele. Quadro 0 = parado, 1 = boca aberta (cacando).
 */
final class Boneco3d {

    /** Mesmo id que o CoronaPack usa ao mandar o pacote. */
    static final UUID PACOTE = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(StandardCharsets.UTF_8));
    private static final Set<UUID> COM_PACOTE = ConcurrentHashMap.newKeySet();

    static final class Ouvinte implements Listener {
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
        }
    }

    static boolean tem(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || COM_PACOTE.contains(p.getUniqueId());
    }

    private final String id;
    ItemDisplay modelo;
    private float esc = -1;
    private int quadro = -1;

    Boneco3d(String id) {
        this.id = id;
    }

    @SuppressWarnings("UnstableApiUsage")
    private ItemStack item(int q) {
        // leather_helmet: no Bedrock o Geyser (corona-fabrica.json) troca pelo item 3D
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/anim_" + id + "_f" + q));
        return it;
    }

    private static Transformation forma(float e) {
        return new Transformation(new Vector3f(0, 1.5f * e, 0), new Quaternionf(), new Vector3f(e, e, e), new Quaternionf());
    }

    /** Chamado a cada tick. b = o boneco (pode ser null/removido: o modelo some). repartir = reve quem enxerga o que. */
    void atualizar(Plugin pl, Mannequin b, int q, boolean repartir) {
        if (b == null || !b.isValid()) {
            sumir();
            return;
        }
        var a = b.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        float e = (float) ((a == null ? 1.0 : a.getBaseValue()) / 1.25);   // o modelo tem 2,25 blocos = boneco na escala 1,25
        Location l = b.getLocation();
        l.setPitch(0);
        boolean novo = modelo == null || !modelo.isValid();
        if (novo) {
            if (!l.getChunk().isLoaded()) return;
            final float fe = e;
            modelo = l.getWorld().spawn(l, ItemDisplay.class, d -> {
                d.setItemStack(item(q));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(forma(fe));
                d.setViewRange(1.6f);
                d.setShadowRadius(0f);
                d.setTeleportDuration(2);
                d.setPersistent(false);
                d.setVisibleByDefault(false);     // so quem tem o pacote ve o modelo; os outros veem o boneco com skin
            });
            esc = e;
            quadro = q;
        } else {
            Location m = modelo.getLocation();
            if (m.distanceSquared(l) > 0.0001 || Math.abs(m.getYaw() - l.getYaw()) > 0.5f) {
                // salto grande (troca de sala): sem deslizar pelo mapa
                modelo.setTeleportDuration(m.distanceSquared(l) > 9 ? 0 : 2);
                modelo.teleport(l);
            }
            if (Math.abs(e - esc) > 0.01f) {
                modelo.setInterpolationDelay(0);
                modelo.setInterpolationDuration(4);
                modelo.setTransformation(forma(e));
                esc = e;
            }
            if (q != quadro) {
                modelo.setItemStack(item(q));
                quadro = q;
            }
        }
        if (novo || repartir) repartir(pl, b, modelo);
    }

    void sumir() {
        if (modelo != null && modelo.isValid()) modelo.remove();
        modelo = null;
        esc = -1;
        quadro = -1;
    }

    private static void repartir(Plugin pl, Entity boneco, Entity modelo) {
        for (Player p : boneco.getWorld().getPlayers()) {
            boolean ve3d = tem(p);
            ver(pl, p, boneco, !ve3d);
            ver(pl, p, modelo, ve3d);
        }
    }

    private static void ver(Plugin pl, Player p, Entity e, boolean sim) {
        if (sim == p.canSee(e)) return;
        if (sim) p.showEntity(pl, e);
        else p.hideEntity(pl, e);
    }
}
