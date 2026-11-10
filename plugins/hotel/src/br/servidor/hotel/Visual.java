package br.servidor.hotel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Os monstros usam a pintura HD do pacote. Quem esta no Java sem o pacote veria a textura faltando, entao cada
 * monstro ganha um gemeo com a skin de reserva (Mojang) que so essas pessoas veem. O gemeo copia tudo do original
 * a cada tick. O Bedrock ve o original (o Geyser troca a reserva pela pintura).
 */
final class Visual {
    private static Plugin pl;
    private static final Map<UUID, Par> PARES = new HashMap<>();

    private static final class Par {
        final Entity principal, gemeo;
        /** Modelo 3D do pacote (Figura e Perseguidor): quem tem o pacote ve o modelo em vez do boneco pintado. */
        ItemDisplay modelo;
        String modeloId;
        float modeloEsc = -1;
        int quadro, quadroPosto = -1;
        /** null = todos; senao so este jogador (ex.: o Sussurro, que so a vitima ve) */
        UUID so;
        Location ultima;

        Par(Entity principal, Entity gemeo) {
            this.principal = principal;
            this.gemeo = gemeo;
        }
    }

    static void iniciar(Plugin p) {
        pl = p;
        Bukkit.getScheduler().runTaskTimer(p, Visual::tick, 1L, 1L);
    }

    /** Liga o gemeo de reserva a um monstro recem-criado (Mannequin ou ItemDisplay de cabeca). */
    static void gemeo(Entity principal, String skin) {
        gemeo(principal, skin, null);
    }

    /** Igual, mas o monstro so aparece para este jogador (null = para todos). */
    static void gemeo(Entity principal, String skin, Player so) {
        if (pl == null || principal == null || !principal.isValid()) return;
        if (!Skins.temPintura(skin)) {
            if (so != null) {
                principal.setVisibleByDefault(false);
                so.showEntity(pl, principal);
            }
            return;
        }
        Entity g = null;
        Location l = principal.getLocation();
        if (principal instanceof Mannequin m) {
            g = l.getWorld().spawn(l, Mannequin.class, x -> {
                x.setProfile(Skins.reserva(skin));
                x.setDescription(null);
                x.setImmovable(true);
                x.setGravity(false);
                x.setSilent(true);
                x.setInvulnerable(true);
                x.setCollidable(false);
                x.setPersistent(false);
                x.setVisibleByDefault(false);
                AttributeInstance a = m.getAttribute(Attribute.SCALE), b = x.getAttribute(Attribute.SCALE);
                if (a != null && b != null) b.setBaseValue(a.getBaseValue());
                x.setPose(m.getPose(), true);
                principal.getPersistentDataContainer().copyTo(x.getPersistentDataContainer(), true);
            });
        } else if (principal instanceof ItemDisplay d) {
            g = l.getWorld().spawn(l, ItemDisplay.class, x -> {
                x.setItemStack(Skins.cabecaReserva(skin));
                copiar(d, x);
                x.setPersistent(false);
                x.setVisibleByDefault(false);
                principal.getPersistentDataContainer().copyTo(x.getPersistentDataContainer(), true);
            });
        }
        if (g == null) return;
        Par par = new Par(principal, g);
        par.ultima = l.clone();
        par.so = so == null ? null : so.getUniqueId();
        if (principal instanceof Mannequin && MODELOS.contains(skin) && pl.getConfig().getBoolean("modelo-3d", true)) par.modeloId = skin;
        PARES.put(principal.getUniqueId(), par);
        principal.setVisibleByDefault(false);
        visibilidade(par);
    }

    private static void copiar(Display d, Display x) {
        x.setTransformation(d.getTransformation());
        x.setInterpolationDelay(d.getInterpolationDelay());
        x.setInterpolationDuration(d.getInterpolationDuration());
        x.setTeleportDuration(d.getTeleportDuration());
        x.setBrightness(d.getBrightness());
        x.setBillboard(d.getBillboard());
        x.setGlowing(d.isGlowing());
        x.setGlowColorOverride(d.getGlowColorOverride());
        x.setViewRange(d.getViewRange());
    }

    /** Monstros que tem modelo 3D no pacote (corona:props/anim_<id>_f0 parado, _f1 atacando). */
    static final java.util.Set<String> MODELOS = java.util.Set.of("figura", "perseguidor");

    /** Troca o quadro do modelo 3D do monstro (0 parado/andando, 1 atacando). */
    static void quadro(Entity principal, int q) {
        Par par = principal == null ? null : PARES.get(principal.getUniqueId());
        if (par != null) par.quadro = q;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static org.bukkit.inventory.ItemStack item(String id, int q) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_HELMET);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/anim_" + id + "_f" + q));
        return it;
    }

    private static org.bukkit.util.Transformation forma(float e) {
        return new org.bukkit.util.Transformation(new org.joml.Vector3f(0, 1.5f * e, 0), new org.joml.Quaternionf(), new org.joml.Vector3f(e, e, e), new org.joml.Quaternionf());
    }

    /** Mantem o modelo 3D em cima do boneco: posicao, lado, tamanho e quadro. */
    private static void seguir(Par par) {
        if (par.modeloId == null || !(par.principal instanceof Mannequin m)) return;
        AttributeInstance a = m.getAttribute(Attribute.SCALE);
        float e = (float) ((a == null ? 1.0 : a.getBaseValue()) / 1.25);     // o modelo tem 2,25 blocos = boneco na escala 1,25
        Location l = m.getLocation();
        l.setPitch(0);
        if (par.modelo == null || !par.modelo.isValid()) {
            if (!l.getChunk().isLoaded()) return;
            final float fe = e;
            final int q = par.quadro;
            par.modelo = l.getWorld().spawn(l, ItemDisplay.class, d -> {
                d.setItemStack(item(par.modeloId, q));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(forma(fe));
                d.setViewRange(1.6f);
                d.setShadowRadius(0f);
                d.setTeleportDuration(2);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                par.principal.getPersistentDataContainer().copyTo(d.getPersistentDataContainer(), true);
            });
            par.modeloEsc = e;
            par.quadroPosto = q;
            visibilidade(par);
            return;
        }
        Location at = par.modelo.getLocation();
        if (at.distanceSquared(l) > 0.0001 || Math.abs(at.getYaw() - l.getYaw()) > 0.5f) {
            par.modelo.setTeleportDuration(at.distanceSquared(l) > 9 ? 0 : 2);
            par.modelo.teleport(l);
        }
        if (Math.abs(e - par.modeloEsc) > 0.01f) {
            par.modelo.setTransformation(forma(e));
            par.modeloEsc = e;
        }
        if (par.quadro != par.quadroPosto) {
            par.modelo.setItemStack(item(par.modeloId, par.quadro));
            par.quadroPosto = par.quadro;
        }
    }

    private static void visibilidade(Par par) {
        boolean tem3d = par.modelo != null && par.modelo.isValid();
        for (Player p : par.principal.getWorld().getPlayers()) {
            boolean pode = par.so == null || par.so.equals(p.getUniqueId());
            boolean pacote = Sons.temPacote(p);
            if (tem3d) ver(p, par.modelo, pode && pacote);
            ver(p, par.principal, pode && pacote && !tem3d);
            ver(p, par.gemeo, pode && !pacote);
        }
    }

    private static void ver(Player p, Entity e, boolean sim) {
        if (sim == p.canSee(e)) return;
        if (sim) p.showEntity(pl, e);
        else p.hideEntity(pl, e);
    }

    private static void tick() {
        if (PARES.isEmpty()) return;
        boolean revisar = Bukkit.getCurrentTick() % 10 == 0;
        for (Iterator<Par> it = PARES.values().iterator(); it.hasNext(); ) {
            Par par = it.next();
            Entity a = par.principal, g = par.gemeo;
            if (!a.isValid() || !g.isValid()) {
                g.remove();
                if (par.modelo != null && par.modelo.isValid()) par.modelo.remove();
                it.remove();
                continue;
            }
            seguir(par);
            Location l = a.getLocation();
            if (!l.equals(par.ultima)) {
                g.teleport(l);
                par.ultima = l.clone();
            }
            if (a instanceof Mannequin m && g instanceof Mannequin x) {
                if (m.getPose() != x.getPose()) x.setPose(m.getPose(), true);
                if (m.getBodyYaw() != x.getBodyYaw()) x.setBodyYaw(m.getBodyYaw());
            } else if (a instanceof Display d && g instanceof Display x) {
                if (!Objects.equals(d.getTransformation(), x.getTransformation())) {
                    x.setInterpolationDelay(d.getInterpolationDelay());
                    x.setInterpolationDuration(d.getInterpolationDuration());
                    x.setTransformation(d.getTransformation());
                }
                if (d.isGlowing() != x.isGlowing()) x.setGlowing(d.isGlowing());
            }
            if (revisar) visibilidade(par);
        }
    }

    private Visual() {}
}
