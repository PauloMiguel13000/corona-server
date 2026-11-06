package br.servidor.backrooms;

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
        final Entity principal, gemeo;   // gemeo pode ser null (monstro sem pintura HD, so com modelo 3D)
        final Modelo3D modelo;           // null = sem modelo 3D
        /** null = todos; senao so este jogador (ex.: o Sussurro, que so a vitima ve) */
        UUID so;
        Location ultima;
        final double[] ultimaXZ = {Double.NaN, Double.NaN};

        Par(Entity principal, Entity gemeo, Modelo3D modelo) {
            this.principal = principal;
            this.gemeo = gemeo;
            this.modelo = modelo;
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
        gemeo(principal, skin, so, 0);
    }

    private static void gemeo(Entity principal, String skin, Player so, int tentativa) {
        if (pl == null || principal == null) return;
        if (!principal.isValid()) {
            // recem-nascido num pedaco do mundo ainda carregando: o servidor so o "liga" no tick seguinte. Espera.
            if (tentativa < 40 && !principal.isDead()) Bukkit.getScheduler().runTaskLater(pl, () -> gemeo(principal, skin, so, tentativa + 1), 1L);
            return;
        }
        if (pl.getConfig().getBoolean("modelos-3d-log", false))
            pl.getLogger().info("Modelo/gemeo de " + skin + " (tentativa " + tentativa + ")");
        Location l = principal.getLocation();
        Modelo3D modelo = null;
        if (principal instanceof Mannequin m && Modelo3D.tem(skin) && pl.getConfig().getBoolean("modelos-3d", true)) {
            AttributeInstance a = m.getAttribute(Attribute.SCALE);
            double esc = a != null ? a.getBaseValue() : 1.0;
            try {
                modelo = new Modelo3D(pl, l, skin, esc * escalaModelo(skin));
                if (pl.getConfig().getBoolean("modelos-3d-log", false)) pl.getLogger().info("Modelo 3D criado: " + skin + " em " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
            } catch (Throwable t) {
                pl.getLogger().warning("Modelo 3D de " + skin + " falhou: " + t);
            }
        }
        if (!Skins.temPintura(skin) && modelo == null) {
            // sem pintura HD nem modelo: todo mundo ve a mesma skin, nao precisa de gemeo
            if (so != null) {
                principal.setVisibleByDefault(false);
                so.showEntity(pl, principal);
            }
            return;
        }
        Entity g = null;
        if (!Skins.temPintura(skin)) {
            // so modelo 3D: nada de gemeo (quem nao ve o modelo ve o proprio manequim)
        } else if (principal instanceof Mannequin m) {
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
        if (g == null && modelo == null) return;
        Par par = new Par(principal, g, modelo);
        par.ultima = l.clone();
        par.so = so == null ? null : so.getUniqueId();
        PARES.put(principal.getUniqueId(), par);
        principal.setVisibleByDefault(false);
        visibilidade(par);
    }

    /** O modelo 3D do manequim e um pouco menor que o manequim (o manequim ja vem com SCALE; o modelo tem a altura certa). */
    private static double escalaModelo(String skin) {
        return switch (skin) {
            case "bacteria" -> 1.0;   // modelo: 2,9 m com escala 1,6 (a mesma do manequim)
            default -> 1.0;
        };
    }

    /** O modelo 3D ligado a esta entidade (null se nao tem). */
    static Modelo3D modelo(Entity principal) {
        if (principal == null) return null;
        Par par = PARES.get(principal.getUniqueId());
        return par == null ? null : par.modelo;
    }

    /** Pose de grito no modelo 3D por alguns ticks (nada acontece se nao tem modelo). */
    static void gritar(Entity principal, int ticks) {
        Modelo3D m = modelo(principal);
        if (m != null) m.gritar(Bukkit.getCurrentTick(), ticks);
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
            Modelo3D m = par.modelo;
            boolean modelo = m != null && pode && m.mostraModelo(p);
            boolean emprestada = m != null && pode && !modelo && m.mostraEmprestada(p);
            if (m != null) {
                if (m.corpo != null) ver(p, m.corpo, modelo);
                if (m.olhos != null) ver(p, m.olhos, modelo);
                if (m.bedrock != null) ver(p, m.bedrock, emprestada);
            }
            boolean resto = pode && !modelo && !emprestada;
            ver(p, par.principal, resto && (pacote || par.gemeo == null));
            if (par.gemeo != null) ver(p, par.gemeo, resto && !pacote);
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
            if (!a.isValid() || (g != null && !g.isValid())) {
                if (g != null) g.remove();
                if (par.modelo != null) par.modelo.remover();
                it.remove();
                continue;
            }
            Location l = a.getLocation();
            boolean moveu = !l.equals(par.ultima);
            if (moveu) {
                if (g != null) g.teleport(l);
                par.ultima = l.clone();
            }
            if (par.modelo != null) {
                float yaw = a instanceof Mannequin m ? m.getBodyYaw() : l.getYaw();
                boolean andou = moveu && (l.getX() != par.ultimaXZ[0] || l.getZ() != par.ultimaXZ[1]);
                par.ultimaXZ[0] = l.getX();
                par.ultimaXZ[1] = l.getZ();
                par.modelo.seguir(pl, l, yaw, andou, Bukkit.getCurrentTick());
            }
            if (g == null) {
                if (revisar) visibilidade(par);
                continue;
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
