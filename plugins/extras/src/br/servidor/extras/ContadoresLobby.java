package br.servidor.extras;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 13/11 (Paulo: "melhore os lobbies"): na frente de cada portal dos lobbies de minigames aparece quantos jogadores
 * estao jogando aquele jogo agora ("● 3 jogando" / "○ ninguém jogando"). Os textos nao sao salvos no mundo
 * (persistente = false), entao nunca duplicam: sao refeitos quando somem.
 */
final class ContadoresLobby {
    private final Extras pl;
    private final Map<String, TextDisplay> textos = new HashMap<>();

    ContadoresLobby(Extras pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::atualizar, 100L, 100L);
    }

    private static int jogando(String id) {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            String nome = w.getName().toLowerCase();
            if (nome.equals(id) || nome.startsWith(id + "_") || nome.startsWith(id)) n += w.getPlayers().size();
        }
        return n;
    }

    private void atualizar() {
        lobby(Bukkit.getWorld(LobbyMinigames.MUNDO), LobbyMinigames.doLobby(), false);
        lobby(Bukkit.getWorld(LobbyTerror.MUNDO), LobbyMinigames.deTerror(), true);
    }

    private void lobby(World w, LobbyMinigames.Portal[] portais, boolean terror) {
        if (w == null) return;
        boolean alguem = !w.getPlayers().isEmpty();
        for (LobbyMinigames.Portal p : portais) {
            String chave = w.getName() + ":" + p.id();
            TextDisplay t = textos.get(chave);
            if (!alguem) {
                if (t != null && t.isValid()) t.remove();
                textos.remove(chave);
                continue;
            }
            int n;
            if (p.id().equals("terror")) {
                n = 0;
                World tw = Bukkit.getWorld(LobbyTerror.MUNDO);
                if (tw != null) n += tw.getPlayers().size();
                for (LobbyMinigames.Portal o : LobbyMinigames.deTerror()) n += jogando(o.id());
            } else n = jogando(p.id());
            String mg = minigameDoDia();
            boolean doDia = mg != null && !p.id().equals("terror") && (mg.startsWith(p.id()) || p.id().startsWith(mg));
            String txt = (doDia ? "§d§l★ JOGO DO DIA §7— §acoronas x2\n" : "") + (n > 0 ? "§a● §f" + n + (n == 1 ? " jogando agora" : " jogando agora") : "§8○ §7ninguém jogando — §fseja o primeiro!");
            if (t == null || !t.isValid()) {
                Location l = local(w, p, terror);
                if (l == null || !l.isChunkLoaded()) continue;
                t = w.spawn(l, TextDisplay.class, x -> {
                    x.setPersistent(false);
                    x.setBillboard(Display.Billboard.CENTER);
                    x.setShadowed(true);
                    x.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                    x.setBrightness(new Display.Brightness(15, 15));
                    x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.1f), new AxisAngle4f()));
                });
                textos.put(chave, t);
            }
            t.text(Extras.txt(txt));
        }
    }

    private static String minigameDoDia() {
        try {
            var pl = Bukkit.getPluginManager().getPlugin("Masmorras");
            if (pl == null || !pl.isEnabled()) return null;
            Class<?> c = Class.forName("br.servidor.masmorras.Destaque", true, pl.getClass().getClassLoader());
            var m = c.getDeclaredMethod("minigameHoje");
            m.setAccessible(true);
            Object o = m.invoke(null);
            return o instanceof String[] a && a.length > 0 ? a[0].toLowerCase() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Na frente do portal, na altura dos olhos, um pouco para o lado de quem chega. */
    private static Location local(World w, LobbyMinigames.Portal p, boolean terror) {
        int vg, cx, cz, y;
        double ang;
        if (terror) {
            vg = LobbyTerror.vaga(p);
            cx = LobbyTerror.cxVaga(vg);
            cz = LobbyTerror.czVaga(vg);
            ang = LobbyTerror.angulo(vg);
            y = LobbyTerror.Y;
        } else {
            vg = LobbyMinigames.vaga(p);
            cx = LobbyMinigames.cxVaga(vg);
            cz = LobbyMinigames.czVaga(vg);
            ang = LobbyMinigames.angulo(vg);
            y = LobbyMinigames.Y;
        }
        double dx = Math.cos(Math.toRadians(ang)), dz = Math.sin(Math.toRadians(ang));
        return new Location(w, cx + 0.5 - dx * 3.0, y + 2.9, cz + 0.5 - dz * 3.0);
    }

    void limpar() {
        for (Entity e : textos.values()) if (e != null && e.isValid()) e.remove();
        textos.clear();
    }
}
