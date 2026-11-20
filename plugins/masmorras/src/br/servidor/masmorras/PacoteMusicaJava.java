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
 * corona-pacotes e chega pelo raw.githubusercontent (o jsDelivr recusa arquivos novos: o repositorio passou de 50 MB). Entra uns segundos depois do pacote principal, sem obrigar
 * (quem recusar so fica sem as musicas). Bedrock recebe a trilha pelos pacotes corona_trilhas/corona_chefes.
 */
final class PacoteMusicaJava implements Listener {
    static final String URL_PADRAO = "https://raw.githubusercontent.com/PauloMiguel13000/corona-pacotes/main/corona_trilha_java-8d06254cdc.zip";
    static final String SHA1_PADRAO = "8d06254cdc4305e721dab6b5408ea1ac92013113";
    private static final UUID ID = UUID.nameUUIDFromBytes("corona-trilha-masmorras".getBytes());
    /** 14/11: pacote pequeno so com a musica do Templo do Trovao (a nova + a primeira versao em seguida). */
    static final String URL_TROVAO = "https://corona-server.duckdns.org/pacotes/corona_trovao_java-f26ea96da2.zip";   // servido pela propria loja (pacotes-site)
    static final String SHA1_TROVAO = "f26ea96da2163da210d7ea419ac715950aa14a79";
    private static final UUID ID_TROVAO = UUID.nameUUIDFromBytes("corona-trilha-trovao".getBytes());
    /** 20/11: musicas do nivel 6 (pacote separado, servido pelo site da loja: plugins/CoronaPack/pacotes-site). */
    static final String URL_N6 = "https://corona-server.duckdns.org/pacotes/corona_trilha6_java-0fd3bb7f6c.zip";
    static final String SHA1_N6 = "0fd3bb7f6ca5e30090a0b7efe6765440f1b03123";
    private static final UUID ID_N6 = UUID.nameUUIDFromBytes("corona-trilha-nivel6".getBytes());
    private final Masmorras pl;

    PacoteMusicaJava(Masmorras pl) {
        this.pl = pl;
        // 16/11: o CoronaPack junta estes pacotes com o principal e manda tudo de uma vez (uma tela de carregamento so)
        java.util.function.Function<Player, java.util.List<String>> pacotes = p -> {
            if (!pl.getConfig().getBoolean("trilha.pacote-java", true) || bedrock(p)) return java.util.List.of();
            java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of(ID.toString(), pl.getConfig().getString("trilha.pacote-java-url", URL_PADRAO),
                    pl.getConfig().getString("trilha.pacote-java-sha1", SHA1_PADRAO), "Trilha sonora das masmorras"));
            if (pl.getConfig().getBoolean("trilha.pacote-java-trovao", true))
                l.addAll(java.util.List.of(ID_TROVAO.toString(), pl.getConfig().getString("trilha.pacote-java-trovao-url", URL_TROVAO),
                        pl.getConfig().getString("trilha.pacote-java-trovao-sha1", SHA1_TROVAO), "Musica do Templo do Trovao"));
            if (pl.getConfig().getBoolean("trilha.pacote-java-n6", true))
                l.addAll(java.util.List.of(ID_N6.toString(), pl.getConfig().getString("trilha.pacote-java-n6-url", URL_N6),
                        pl.getConfig().getString("trilha.pacote-java-n6-sha1", SHA1_N6), "Musicas do nivel 6"));
            return l;
        };
        Bukkit.getServicesManager().register(java.util.function.Function.class, pacotes, pl, org.bukkit.plugin.ServicePriority.Normal);
    }

    private static boolean coronaPackJunta() {
        for (var r : Bukkit.getServicesManager().getRegistrations(java.util.function.Predicate.class))
            if (r.getPlugin().getName().equals("CoronaPack") && r.getPlugin().isEnabled()) return true;
        return false;
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
        if (!pl.getConfig().getBoolean("trilha.pacote-java", true) || bedrock(p) || coronaPackJunta()) return;   // o CoronaPack ja manda junto
        // depois do pacote principal (o CoronaPack manda o dele ~3 s depois de entrar e "set" troca a lista inteira)
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            try {
                String url = pl.getConfig().getString("trilha.pacote-java-url", URL_PADRAO);
                byte[] sha = HexFormat.of().parseHex(pl.getConfig().getString("trilha.pacote-java-sha1", SHA1_PADRAO));
                p.addResourcePack(ID, url, sha, "§e§lCorona Server§r\n§fTrilha sonora das masmorras §7(músicas novas)", false);
                if (pl.getConfig().getBoolean("trilha.pacote-java-trovao", true))
                    p.addResourcePack(ID_TROVAO, pl.getConfig().getString("trilha.pacote-java-trovao-url", URL_TROVAO),
                            HexFormat.of().parseHex(pl.getConfig().getString("trilha.pacote-java-trovao-sha1", SHA1_TROVAO)),
                            "§e§lCorona Server§r\n§fMúsica do Templo do Trovão", false);
                if (pl.getConfig().getBoolean("trilha.pacote-java-n6", true))
                    p.addResourcePack(ID_N6, pl.getConfig().getString("trilha.pacote-java-n6-url", URL_N6),
                            HexFormat.of().parseHex(pl.getConfig().getString("trilha.pacote-java-n6-sha1", SHA1_N6)),
                            "§e§lCorona Server§r\n§fMúsicas do nível 6", false);
            } catch (Throwable t) {
                pl.getLogger().warning("Pacote de musica do Java para " + p.getName() + ": " + t);
            }
        }, 20L * 12);
    }
}
