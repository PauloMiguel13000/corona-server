package br.servidor.vovo;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Modelo 3D da Vovo (pacote de recursos, fontes/modelos/vovo3d.py): um item display que acompanha o boneco. Quadros:
 * vovo_idle, vovo_grito e o ciclo de andar vovo_andar_00..15, que avanca com a distancia andada (as pernas nao
 * "patinam"). No Bedrock o Geyser (corona-vovo.json) troca o item pelo item 3D da extensao GeyserDisplayEntity.
 * Quem esta no Java sem o pacote continua vendo o boneco com skin (classe Pacote).
 */
final class Modelo {
    static final int QUADROS_ANDAR = 16;
    /** Blocos andados por ciclo completo (dois passos curtos de velhinha). */
    private static final double PASSADA = 1.5;

    private ItemDisplay display;
    private String quadro = "idle";
    private double fase, ultX = Double.NaN, ultZ = Double.NaN;
    private int parada;
    private long gritoAte;

    Modelo(Plugin pl, Location l, NamespacedKey marca) {
        Location o = l.clone();
        o.setPitch(0);
        display = l.getWorld().spawn(o, ItemDisplay.class, d -> {
            d.setItemStack(item("idle"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            // o modelo tem os pes em y=-16 (1,5 bloco abaixo do centro do item)
            d.setTransformation(new Transformation(new Vector3f(0, 1.5f, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
            d.setViewRange(1.2f);
            d.setShadowRadius(0f);
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setVisibleByDefault(false);     // so quem tem o pacote ve (Pacote.repartir)
            d.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private static ItemStack item(String quadro) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("corona", "props/vovo_" + quadro));
        return it;
    }

    Entity entidade() {
        return display;
    }

    boolean valido() {
        return display != null && display.isValid();
    }

    /** Todo tick: vai para onde o boneco esta e escolhe o quadro (andando, parada ou gritando). */
    void seguir(Location l, float yaw) {
        if (!valido()) return;
        double dx = Double.isNaN(ultX) ? 0 : l.getX() - ultX, dz = Double.isNaN(ultZ) ? 0 : l.getZ() - ultZ;
        ultX = l.getX();
        ultZ = l.getZ();
        double andou = Math.sqrt(dx * dx + dz * dz);
        Location d = l.clone();
        d.setYaw(yaw);
        d.setPitch(0);
        display.teleport(d);
        long agora = Bukkit.getCurrentTick();
        if (gritoAte > 0) {
            if (agora < gritoAte) return;
            gritoAte = 0;
        }
        if (andou > 1.5) andou = 0;                 // teleporte nao conta como passo
        if (andou < 0.004) {
            if (++parada >= 4) quadro("idle");
            return;
        }
        parada = 0;
        fase = (fase + andou / PASSADA) % 1.0;
        quadro(String.format("andar_%02d", (int) (fase * QUADROS_ANDAR) % QUADROS_ANDAR));
    }

    private void quadro(String q) {
        if (q.equals(quadro) || !valido()) return;
        quadro = q;
        display.setItemStack(item(q));
    }

    /** Frigideira para a frente e boca aberta por alguns ticks. */
    void gritar(int ticks) {
        gritoAte = Bukkit.getCurrentTick() + ticks;
        quadro("grito");
    }

    void remover() {
        if (display != null) display.remove();
        display = null;
    }
}
