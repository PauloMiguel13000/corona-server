package br.servidor.masmorras;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * 13/11 (Paulo: "melhore as masmorras"): em cima da entrada de cada masmorra aparece o nome, o nivel e se o chefe
 * esta esperando, em luta ou quanto falta para voltar. So existe com jogador por perto (nao salva no mundo).
 */
final class StatusEntrada {
    private final Masmorras pl;
    private final Map<Integer, TextDisplay> textos = new HashMap<>();

    StatusEntrada(Masmorras pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::atualizar, 100L, 100L);
    }

    private void atualizar() {
        for (Masmorra m : pl.todas) {
            World w = Bukkit.getWorld(m.mundo);
            TextDisplay t = textos.get(m.id);
            if (w == null) continue;
            Location l = new Location(w, m.saidaX + 0.5, m.saidaY + 3.6, m.saidaZ + 0.5);
            boolean perto = false;
            if (w.isChunkLoaded(m.saidaX >> 4, m.saidaZ >> 4))
                for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(l) < 28 * 28) { perto = true; break; }
            if (!perto) {
                if (t != null) { if (t.isValid()) t.remove(); textos.remove(m.id); }
                continue;
            }
            String st = m.chefe != null ? "§4⚔ chefe em luta agora" : m.bossVolta == 0 ? "§a✔ chefe esperando"
                    : "§c✖ chefe volta em " + Math.max(1, (m.bossVolta - System.currentTimeMillis()) / 60000) + " min";
            String txt = "§6§l" + m.tipo.nome + "\n§7Nível " + m.tipo.nivel + (Destaque.eh(m.tipo) ? " §d§l★ DESTAQUE x2" : "") + (Destaque.relampago(m.tipo) ? "\n§e§l⚡ RELÂMPAGO x3 §7(" + Math.max(1, (Destaque.relampagoAte - System.currentTimeMillis()) / 60000) + " min)" : "") + "\n" + st;
            if (t == null || !t.isValid()) {
                t = w.spawn(l, TextDisplay.class, x -> {
                    x.setPersistent(false);
                    x.setBillboard(Display.Billboard.CENTER);
                    x.setShadowed(true);
                    x.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                    x.setAlignment(TextDisplay.TextAlignment.CENTER);
                });
                textos.put(m.id, t);
            }
            t.text(Masmorras.txt(txt));
        }
    }

    void limpar() {
        for (TextDisplay t : textos.values()) if (t != null && t.isValid()) t.remove();
        textos.clear();
    }
}
