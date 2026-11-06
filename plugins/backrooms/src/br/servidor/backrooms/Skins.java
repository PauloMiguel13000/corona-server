package br.servidor.backrooms;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skins das criaturas (skins publicas da galeria do MineSkin, texturas oficiais do Minecraft). Trocadas em 02/11 por
 * skins de monstro de verdade (antes pareciam jogadores fantasiados).
 * O corpo e um manequim: o Java baixa a textura sozinho e o Geyser manda a mesma skin para o Bedrock.
 */
final class Skins {
    private static final Map<String, String> SKINS = new HashMap<>();
    private static final Map<String, ResolvableProfile> CACHE = new HashMap<>(), CACHE_R = new HashMap<>();

    static {
        // escolhidas a dedo na galeria publica do MineSkin (texturas hospedadas pela Mojang, funcionam no Java e no Bedrock)
        SKINS.put("bacteria", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjNkNDg0YmQ0ZTY5ZDFhYzQzMmM3M2E1ZDg4ZWY0MjUwMDlkMDg1YTUxMzliNjAyN2M3ODY2OTA3MTMwMWVhOSJ9fX0=");   // "scary face": preta, olhos vermelhos e dentes
        SKINS.put("uivador", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGI1MTMyMGE5YmFkMjBiZmJiMmQ3ODZjZDBjMmE4MGYwMTYyMDI1NzI2NDkxYmZjODk4YzUwYzMxZDVmOWVmNSJ9fX0=");    // palido de boca escancarada e sangue (estilo SCP-096)
        SKINS.put("corredor", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTYyMmQ5ZmUxYzcyODI2M2VkZDg3ODdiYTc4NGFkZWU3NDYxMjY1NDY2ZjVlN2E2ZGQ2NzNlN2ViOTM5ZjMifX19");   // Wendigo: costelas a mostra e cranio com dentes
        SKINS.put("cao", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmU5OTg1ZjMwZDM5MTk1MGVmYjE3YmY4YTc3OWNmYjYwNmFiNzRmYjlhMjUxNjAwMWJkODIwNWZmODcyZWI3NSJ9fX0=");        // Cao do Nivel 3: cranio palido e corpo esfarrapado (anda de quatro)
        SKINS.put("festeiro", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODljNzZjMmZkNDM1MDYwZjY0ZTBjODNjZDA5NWU4NThkMTQ5MTkzYmZmM2FkZmUwNGViMDMyMGM3MTJkMjNhOSJ9fX0=");   // Festeiro: amarelo, sorriso e manchas de sangue
        // Nivel 6 (Luzes Apagadas): a Sombra, toda preta com olhos vermelhos (sem pintura HD no pacote: usa so a da Mojang)
        SKINS.put("sombra", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDU2ZDkyYTZkZTkyN2U3NmEyMjhmOTYyOWM2YWZiNzhjMmRmMjc0ZjI1Nzk0Zjk1NGViOWQwNTg5NjAzYTA1ZiJ9fX0=");
    }

    static {
        // o Afogado (Nivel 37) e o Corredor (Nivel !) existem em 3D; o boneco de reserva deles usa estas skins
        SKINS.put("afogado", SKINS.get("uivador"));
    }

    /** Monstros que tem pintura HD no pacote de recursos (corona:entity/monstro/<id>). */
    static final java.util.Set<String> PINTURA = java.util.Set.of("bacteria", "uivador", "corredor", "cao", "festeiro");

    static boolean temPintura(String id) {
        return PINTURA.contains(id);
    }

    /** Perfil com a pintura HD do pacote (Java com pacote) e a reserva da Mojang por baixo (o Bedrock troca pela pintura). */
    static ResolvableProfile perfil(String id) {
        return CACHE.computeIfAbsent(id, k -> base(k, true));
    }

    /** So a skin de reserva (Java sem o pacote). */
    static ResolvableProfile reserva(String id) {
        return CACHE_R.computeIfAbsent(id, k -> base(k, false));
    }

    @SuppressWarnings("UnstableApiUsage")
    static org.bukkit.inventory.ItemStack cabeca(String k) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE, perfil(k));
        return it;
    }

    @SuppressWarnings("UnstableApiUsage")
    static org.bukkit.inventory.ItemStack cabecaReserva(String k) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE, reserva(k));
        return it;
    }

    private static ResolvableProfile base(String k, boolean pintura) {
        {
            String nome = ("br_" + k).length() > 16 ? ("br_" + k).substring(0, 16) : "br_" + k;
            ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(nome)
                    .uuid(UUID.nameUUIDFromBytes(("corona-backrooms-v2-" + k).getBytes(StandardCharsets.UTF_8)));
            String v = SKINS.get(k);
            if (v != null) b.addProperty(new ProfileProperty("textures", v));
            if (pintura && temPintura(k)) b.skinPatch(sp -> sp.body(net.kyori.adventure.key.Key.key("corona", "entity/monstro/" + k))
                    .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC));
            return b.build();
        }
    }

    private Skins() {}
}
