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

    private static void visibilidade(Par par) {
        for (Player p : par.principal.getWorld().getPlayers()) {
            boolean pode = par.so == null || par.so.equals(p.getUniqueId());
            boolean pacote = Sons.temPacote(p);
            ver(p, par.principal, pode && pacote);
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
                it.remove();
                continue;
            }
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
