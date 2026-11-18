package br.servidor.extras;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * 17/11 (Sui no Discord: "tem comandos que nao deveriam aparecer ao player, como os de configuracao do bukkit, fora
 * os redundantes como archery com auraskills:archery"): a lista de comandos que o jogo sugere ao digitar "/" nao
 * mostra mais os repetidos com prefixo de plugin (auraskills:archery, minecraft:tell...) nem os do servidor
 * (version, plugins, about...). Admin continua vendo tudo. Os comandos continuam funcionando, so nao aparecem.
 */
final class ComandosVisiveis implements Listener {

    private static final Set<String> ESCONDIDOS = Set.of("version", "ver", "about", "icanhasbukkit", "plugins", "pl", "trigger", "random", "teammsg", "tm");

    private final Extras pl;

    ComandosVisiveis(Extras pl) {
        this.pl = pl;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void lista(PlayerCommandSendEvent e) {
        if (e.getPlayer().hasPermission("extras.admin") || e.getPlayer().isOp()) return;
        Set<String> extra = new HashSet<>();
        List<String> l = pl.getConfig().getStringList("comandos-escondidos");
        for (String s : l) extra.add(s.toLowerCase(Locale.ROOT).replace("/", ""));
        e.getCommands().removeIf(c -> {
            String x = c.toLowerCase(Locale.ROOT);
            return x.indexOf(':') >= 0 || ESCONDIDOS.contains(x) || extra.contains(x);
        });
    }

    /** 18/11 (Sui: "o /plugins ta liberado, tem q bloquear pra evitar roubos"): quem nao e admin nao usa mais. */
    private static final Set<String> BLOQUEADOS = Set.of("plugins", "pl", "version", "ver", "about", "icanhasbukkit", "?");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void usar(PlayerCommandPreprocessEvent e) {
        if (e.getPlayer().hasPermission("extras.admin") || e.getPlayer().isOp()) return;
        String m = e.getMessage();
        if (m.length() < 2) return;
        String cmd = m.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int i = cmd.indexOf(':');
        String base = i >= 0 ? cmd.substring(i + 1) : cmd;
        if (BLOQUEADOS.contains(base)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Extras.txt("§cComando desconhecido. §7Use §f/ajuda§7."));
        }
    }
}
