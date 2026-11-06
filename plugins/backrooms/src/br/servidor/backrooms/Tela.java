package br.servidor.backrooms;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Efeitos de camera so para quem joga no Bedrock (pelo Geyser): tremida e tela escura.
 * O rosto do monstro na tela (fonte do pacote) so funciona no Java, entao no celular o susto usa isto.
 * Se o Geyser nao estiver la (ou mudar a API), nada acontece e nada quebra.
 */
final class Tela {
    private static Boolean geyser;

    private static boolean temGeyser() {
        if (geyser == null) {
            boolean ok;
            try {
                Class.forName("org.geysermc.geyser.api.GeyserApi");
                ok = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
            } catch (Throwable t) {
                ok = false;
            }
            geyser = ok;
        }
        return geyser;
    }

    /** Tremida forte da camera (intensidade 0-4, em segundos). */
    static void tremer(Player p, float intensidade, float segundos) {
        if (p == null || !Sons.bedrock(p) || !temGeyser()) return;
        try {
            Interno.tremer(p.getUniqueId(), intensidade, segundos);
        } catch (Throwable ignored) {
        }
    }

    /** A tela escurece (entra, fica e sai, em segundos) com a cor dada (r, g, b de 0 a 255). */
    static void escurecer(Player p, int r, int g, int b, float entra, float fica, float sai) {
        if (p == null || !Sons.bedrock(p) || !temGeyser()) return;
        try {
            Interno.escurecer(p.getUniqueId(), new java.awt.Color(r, g, b), entra, fica, sai);
        } catch (Throwable ignored) {
        }
    }

    /** So e carregada quando o Geyser existe (as classes dele ficam aqui dentro). */
    private static final class Interno {
        static void tremer(UUID id, float i, float s) {
            org.geysermc.geyser.api.connection.GeyserConnection c = org.geysermc.geyser.api.GeyserApi.api().connectionByUuid(id);
            if (c == null) return;
            c.camera().shakeCamera(i, s, org.geysermc.geyser.api.bedrock.camera.CameraShake.POSITIONAL);
            c.camera().shakeCamera(i * 0.6f, s * 0.8f, org.geysermc.geyser.api.bedrock.camera.CameraShake.ROTATIONAL);
        }

        static void escurecer(UUID id, java.awt.Color cor, float a, float b, float d) {
            org.geysermc.geyser.api.connection.GeyserConnection c = org.geysermc.geyser.api.GeyserApi.api().connectionByUuid(id);
            if (c == null) return;
            c.camera().sendCameraFade(org.geysermc.geyser.api.bedrock.camera.CameraFade.builder()
                    .color(cor).fadeInSeconds(a).fadeHoldSeconds(b).fadeOutSeconds(d).build());
        }
    }

    private Tela() {}
}
