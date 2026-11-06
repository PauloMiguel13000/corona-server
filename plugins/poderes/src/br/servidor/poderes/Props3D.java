package br.servidor.poderes;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Props 3D das habilidades (modelos do pacote de recursos: corona:props/<nome>_<quadro>). So quem joga no Java e
 * aceitou o pacote ve; para esses, os displays de vidro antigos ficam escondidos. Cada prop e um item display
 * centrado no ponto, apontando para +z no modelo; 'pose' gira para a direcao pedida e escala.
 */
final class Props3D implements Listener {
    private final Poderes pl;
    private static final Set<UUID> PACOTE = new HashSet<>();
    private static final Display.Brightness CLARO = new Display.Brightness(15, 15);
    static final double ALCANCE = 96;

    Props3D(Poderes pl) {
        this.pl = pl;
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("props-3d", true);
    }

    @EventHandler
    public void pacote(PlayerResourcePackStatusEvent e) {
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> PACOTE.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, DISCARDED, INVALID_URL -> PACOTE.remove(e.getPlayer().getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        PACOTE.remove(e.getPlayer().getUniqueId());
    }

    /** Java com o pacote carregado (o Bedrock nao entra: la os props nao existem ainda). */
    static boolean temPacote(Player p) {
        return !Cenas.ehBedrock(p) && PACOTE.contains(p.getUniqueId());
    }

    /** Quem vai ver o prop (Java com pacote, perto). */
    List<Player> plateia(Location l) {
        List<Player> v = new ArrayList<>();
        if (!ligado() || l == null || l.getWorld() == null) return v;
        for (Player p : l.getWorld().getPlayers())
            if (temPacote(p) && p.getLocation().distanceSquared(l) < ALCANCE * ALCANCE) v.add(p);
        return v;
    }

    @SuppressWarnings("UnstableApiUsage")
    static ItemStack item(String modelo) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("corona", "props/" + modelo));
        return it;
    }

    /**
     * Cria o prop em 'l' (centro), visivel so para a plateia. vida em ticks (some sozinho). Retorna null se ninguem ve.
     */
    ItemDisplay prop(Location l, String modelo, int vida, List<Player> plateia) {
        if (plateia == null || plateia.isEmpty()) return null;
        Location o = l.clone();
        o.setYaw(0);
        o.setPitch(0);
        ItemDisplay d = l.getWorld().spawn(o, ItemDisplay.class, x -> {
            x.setItemStack(item(modelo));
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            x.setBillboard(Display.Billboard.FIXED);
            x.setBrightness(CLARO);
            x.setTeleportDuration(2);
            x.setInterpolationDuration(2);
            x.setViewRange(1.5f);
            x.setShadowRadius(0f);
            x.setVisibleByDefault(false);
            x.setPersistent(false);
        });
        for (Player p : plateia) p.showEntity(pl, d);
        pl.vis.marcarVisual(d, vida);
        return d;
    }

    /** Mesmo prop para quem estiver perto agora (atalho). */
    ItemDisplay prop(Location l, String modelo, int vida) {
        return prop(l, modelo, vida, plateia(l));
    }

    /** Esconde da plateia dos props os enfeites antigos (vidro), para nao ficarem os dois ao mesmo tempo. */
    void esconder(List<Player> plateia, Entity... enfeites) {
        if (plateia == null || plateia.isEmpty()) return;
        for (Entity e : enfeites) {
            if (e == null) continue;
            for (Player p : plateia) p.hideEntity(pl, e);
        }
    }

    static void quadro(ItemDisplay d, String modelo) {
        if (d != null && d.isValid()) d.setItemStack(item(modelo));
    }

    /** Troca os quadros base_f0..base_f(n-1) a cada 'cada' ticks enquanto o display existir. */
    void animar(ItemDisplay d, String base, int quadros, int cada) {
        if (d == null || quadros <= 1) return;
        final int[] k = {0};
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            if (d == null || !d.isValid()) {
                t.cancel();
                return;
            }
            k[0] = (k[0] + 1) % quadros;
            d.setItemStack(item(base + "_f" + k[0]));
        }, cada, cada);
    }

    static Quaternionf para(Vector dir, float rolar) {
        Vector n = dir.clone();
        if (n.lengthSquared() < 1e-6) n = new Vector(0, 0, 1);
        n.normalize();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) n.getX(), (float) n.getY(), (float) n.getZ()));
        if (rolar != 0) q.mul(new Quaternionf().rotationZ(rolar));
        return q;
    }

    /** Aponta o prop para 'dir' (o +z do modelo), com escala por eixo e giro em volta da direcao. */
    static void pose(ItemDisplay d, Vector dir, float sx, float sy, float sz, float rolar, int interpolar) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interpolar);
        d.setTransformation(new Transformation(new Vector3f(), para(dir, rolar), new Vector3f(sx, sy, sz), new Quaternionf()));
    }

    static void pose(ItemDisplay d, Vector dir, float s, float rolar, int interpolar) {
        pose(d, dir, s, s, s, rolar, interpolar);
    }

    /** Prop "em pe" (sem girar), so escala. */
    static void escala(ItemDisplay d, float s, float giroY, int interpolar) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interpolar);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(giroY), new Vector3f(s), new Quaternionf()));
    }

    static void mover(ItemDisplay d, Location l) {
        if (d == null || !d.isValid()) return;
        Location o = l.clone();
        o.setYaw(0);
        o.setPitch(0);
        d.teleport(o);
    }

    /** O prop pega carona na entidade (projetil, jogador): acompanha sem atraso. Some quando ela some. */
    void montar(Entity veiculo, ItemDisplay d) {
        if (veiculo == null || d == null || !d.isValid()) return;
        veiculo.addPassenger(d);
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            if (!d.isValid()) {
                t.cancel();
                return;
            }
            if (!veiculo.isValid() || veiculo.isDead() || !d.isInsideVehicle()) {
                pl.vis.remover(d);
                t.cancel();
            }
        }, 1L, 1L);
    }

    void remover(ItemDisplay d) {
        if (d != null) pl.vis.remover(d);
    }
}
