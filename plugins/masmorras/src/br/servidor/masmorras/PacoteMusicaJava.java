package br.servidor.masmorras;

import java.util.HexFormat;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 14/11: a trilha sonora nova das masmorras (trilha.*) vai para o Java num SEGUNDO pacote de recursos, separado do
 * pacote principal do CoronaPack (que continua igual, no link de sempre). O pacote fica no repositorio publico
 * corona-pacotes e chega pelo jsDelivr (rapido). Entra uns segundos depois do pacote principal, sem obrigar
 * (quem recusar so fica sem as musicas). Bedrock recebe a trilha pelos pacotes corona_trilhas/corona_chefes.
 */
final class PacoteMusicaJava implements Listener {
    static final String URL_PADRAO = "https://cdn.jsdelivr.net/gh/PauloMiguel13000/corona-pacotes@main/corona_trilha_java-8d06254cdc.zip";
    static final String SHA1_PADRAO = "8d06254cdc4305e721dab6b5408ea1ac92013113";
    private static final UUID ID = UUID.nameUUIDFromBytes("corona-trilha-masmorras".getBytes());
    private final Masmorras pl;

    PacoteMusicaJava(Masmorras pl) {
        this.pl = pl;
    }

    private static boolean bedrock(Player p) {
        if (p.getName().startsWith(".")) return true;
        try {
            return Bukkit.getPluginManager().getPlugin("floodgate") != null
                    && org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!pl.getConfig().getBoolean("trilha.pacote-java", true) || bedrock(p)) return;
        // depois do pacote principal (o CoronaPack manda o dele ~3 s depois de entrar e "set" troca a lista inteira)
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            try {
                String url = pl.getConfig().getString("trilha.pacote-java-url", URL_PADRAO);
                byte[] sha = HexFormat.of().parseHex(pl.getConfig().getString("trilha.pacote-java-sha1", SHA1_PADRAO));
                p.addResourcePack(ID, url, sha, "§e§lCorona Server§r\n§fTrilha sonora das masmorras §7(músicas novas)", false);
            } catch (Throwable t) {
                pl.getLogger().warning("Pacote de musica do Java para " + p.getName() + ": " + t);
            }
        }, 20L * 12);
    }
}
