package br.servidor.extras;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

/**
 * 14/11 (Paulo: "como deixo meu server bonito na lista igual aos outros?"): na LISTA DE SERVIDORES aparece o icone
 * do Corona (coroa) e um texto de 2 linhas colorido que muda sozinho: o que tem no servidor, o jogo do dia com
 * coronas x2, quantos estao jogando e as novidades. O Bedrock recebe o mesmo texto pelo Geyser.
 * O icone fica em plugins/Extras/server-icon.png (pode trocar o arquivo e dar /extras reload).
 */
final class ListaServidores implements Listener {
    private final Extras pl;
    private CachedServerIcon icone;

    ListaServidores(Extras pl) {
        this.pl = pl;
        carregarIcone();
    }

    void carregarIcone() {
        try {
            File f = new File(pl.getDataFolder(), "server-icon.png");
            if (!f.exists()) {
                pl.getDataFolder().mkdirs();
                try (InputStream in = pl.getResource("server-icon.png")) {
                    if (in != null) Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (f.exists()) icone = Bukkit.loadServerIcon(f);
        } catch (Exception e) {
            pl.getLogger().warning("Icone da lista de servidores: " + e);
        }
    }

    private static String jogoDoDia() {
        try {
            var m = Bukkit.getPluginManager().getPlugin("Masmorras");
            if (m == null || !m.isEnabled()) return null;
            Class<?> c = Class.forName("br.servidor.masmorras.Destaque", true, m.getClass().getClassLoader());
            var met = c.getDeclaredMethod("minigameHoje");
            met.setAccessible(true);
            Object o = met.invoke(null);
            return o instanceof String[] a && a.length > 1 ? a[1] : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void ping(ServerListPingEvent e) {
        if (!pl.getConfig().getBoolean("lista-servidores.ligado", true)) return;
        if (icone != null) {
            try { e.setServerIcon(icone); } catch (Exception ignored) { }
        }
        int on = Bukkit.getOnlinePlayers().size();
        String jogo = jogoDoDia();
        String[] linhas2 = {
                "     §c⚔ Masmorras §8• §6⛏ Prisão §8• §5☠ Terror §8• §aSurvival",
                jogo != null ? "        §a★ Jogo do dia: §f" + jogo + " §8- §6coronas x2!" : "     §c⚔ Masmorras §8• §6⛏ Prisão §8• §5☠ Terror §8• §aSurvival",
                "     §d✦ NOVO: §fLoja de Cosméticos com §dASAS§f, auras e rastros!",
                on > 0 ? "           §b● §f" + on + (on == 1 ? " jogando" : " jogando") + " agora §7- entra aí!" : "        §b● §fChama os amigos e §bjoga junto§f!",
                "      §e⚡ 8 minigames de terror §8• §cchefões §8• §6classes"};
        int i = (int) ((System.currentTimeMillis() / 8000L) % linhas2.length);
        String l1 = "§8§l»§6§l» §e§lCORONA SERVER §6§l«§8§l« §7Java §8& §7Bedrock";
        e.motd(Extras.txt("        " + l1 + "\n" + linhas2[i]));
    }
}
