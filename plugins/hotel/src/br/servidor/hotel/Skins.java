package br.servidor.hotel;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Skins das criaturas do Hotel (galeria publica do MineSkin, texturas hospedadas pela Mojang: aparecem no Java e,
 * pelo Geyser, no Bedrock). Viram corpo (manequim) ou cabeca gigante (ItemDisplay).
 */
final class Skins {
    private static final Map<String, String> SKINS = new HashMap<>();
    private static final Map<String, ResolvableProfile> CACHE = new HashMap<>(), CACHE_R = new HashMap<>();

    static {
        SKINS.put("estrondo", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGY5MGE0Nzg2ZWQ4OWRkNGJkZTQ4YTZiNjE5MzcwMDFmMGMwMjAzYzczMjc4YzJmY2IzYjRkOTI5OGZhYTA1NCJ9fX0="); // O Estrondo: so um sorriso enorme no escuro
        SKINS.put("retorno", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmUzYjA1MTI2NmZhMmQxY2IzODExMjYyN2ZiYzFlZDA3N2Y4NTQ3MzU3M2M0NjQzYjYxZmNiMjE5NzEyMDU3MiJ9fX0="); // O Retorno: carne viva e dentes
        SKINS.put("olhos", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJiMTIzZTAzYmM5OWRjOTUzYjM0ZmY1YzljMzU0YzhmOTkwNDdlYTllMTY0YTZiMGIzNjJiZWM5OGE2N2EwYiJ9fX0="); // Os Olhos: carne cheia de olhos
        SKINS.put("grito", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWExMDIxNDVkY2I2ZmEyMWQ4YWVlN2ZhODc3N2EyMTdjNjM0YWM3OGFiNGFhNDZlYTZkOGE3MDM2NmM3MjJlNiJ9fX0="); // O Sussurro: rosto preto sorrindo
        SKINS.put("figura", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmE3ZjExNjQxYTcxOWVkY2U0MWRjNmEwNDhjOGU3ZjczZjY0YjVlZjgzMzlkZTE3ZjU4MTY1Zjg4MWRiZjg3ZSJ9fX0="); // A Figura: corpo sem pele, so dentes
        SKINS.put("perseguidor", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTg4OWUwMTdkYjBjNDIzMjgzY2YzZmJkNmQ1NGEzYWI1ZWYwMTk1OTc1NzY4ZGYxOTQzN2Y1MDVmMmZiMTNkZiJ9fX0="); // O Perseguidor: preto com olhos brancos
        // O Retrato e o Gerente (nossos): so um rosto preto com sorriso vermelho (sem pintura HD no pacote)
        SKINS.put("retrato", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTI1Yjc3MTU3ZjUwODNhYzVkMjJlMTJiNWE3OTdlYzc2YzdjZDMyYmFlZmMzNzdlMTk1M2ExY2JiZGU5YzYyMyJ9fX0=");
    }

    static final java.util.Set<String> PINTURA = java.util.Set.of("estrondo", "retorno", "olhos", "grito", "figura", "perseguidor");

    static boolean temPintura(String k) {
        return PINTURA.contains(k);
    }

    private static UUID id(String k) {
        return UUID.nameUUIDFromBytes(("corona-hotel-" + k).getBytes(StandardCharsets.UTF_8));
    }

    /** Perfil com a pintura HD do pacote (Java com pacote) e a reserva da Mojang por baixo (o Bedrock troca pela pintura). */
    static ResolvableProfile perfil(String k) {
        return CACHE.computeIfAbsent(k, x -> base(x, true));
    }

    /** So a skin de reserva (Java sem o pacote, menus). */
    static ResolvableProfile reserva(String k) {
        return CACHE_R.computeIfAbsent(k, x -> base(x, false));
    }

    private static ResolvableProfile base(String x, boolean pintura) {
        {
            ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(("hotel_" + x).length() > 16 ? ("hotel_" + x).substring(0, 16) : "hotel_" + x).uuid(id(x));
            String v = SKINS.get(x);
            if (v != null) b.addProperty(new ProfileProperty("textures", v));
            if (pintura && temPintura(x)) b.skinPatch(sp -> sp.body(net.kyori.adventure.key.Key.key("corona", "entity/monstro/" + x))
                    .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC));
            return b.build();
        }
    }

    /** Cabeca com a pintura HD (para mostrar gigante num ItemDisplay). */
    @SuppressWarnings("UnstableApiUsage")
    static ItemStack cabeca(String k) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.PROFILE, perfil(k));
        return it;
    }

    /** Cabeca so com a skin de reserva (menus e Java sem pacote). */
    static ItemStack cabecaReserva(String k) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        if (it.getItemMeta() instanceof SkullMeta sm) {
            try {
                PlayerProfile pp = Bukkit.createProfile(id(k), ("hotel_" + k).length() > 16 ? ("hotel_" + k).substring(0, 16) : "hotel_" + k);
                String v = SKINS.get(k);
                if (v != null) pp.setProperty(new ProfileProperty("textures", v));
                sm.setPlayerProfile(pp);
            } catch (Throwable ignored) {
            }
            it.setItemMeta(sm);
        }
        return it;
    }

    private Skins() {}
}
