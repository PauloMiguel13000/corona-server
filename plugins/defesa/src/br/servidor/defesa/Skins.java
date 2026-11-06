package br.servidor.defesa;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skins dos guardioes das torres (skins publicas da galeria do MineSkin, texturas oficiais do Minecraft).
 * Funciona no Java e no Bedrock (o Geyser mostra a skin do Mannequin).
 */
final class Skins {
    private static final Map<String, String> SKINS = new HashMap<>();
    private static final Map<String, ResolvableProfile> CACHE = new HashMap<>();

    static {
        SKINS.put("arqueira", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWVhNGE5NmU1ZTMyMTllYzliY2Y4ZWQyOGRkMzUwMzJlMzM2YjhhZGQwNWNiMWIwYzY4ZDU4OTgxMTE3YTc3ZiJ9fX0=");
        SKINS.put("canhao", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMyMTkwYjczYzlmYTVkYzE5ZjA0ZWZkZWJlZWEzZTc3YzVjZDU4N2YwNDUwZDFkNGNhN2M5NjcwNDllYjQ0NCJ9fX0=");
        SKINS.put("gelo", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDA2MWNhMTNiZjk0NzQzZmZhMjEzODAwMzQwNDkxN2ZmNDY2OGQwYjAwMjc5YWJkNTQyOTEwMWViOWI5NWUyYiIsIm1ldGFkYXRhIjp7Im1vZGVsIjoic2xpbSJ9fX19");
        SKINS.put("veneno", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODZlN2RhYTAyNWExM2FjNTg2ZDFkZjEyMmYzNmZmODNiODA0Y2NlOWFiZDQzZjcwZWY3Yjc1OWMzNGFlYWIzNiJ9fX0=");
        SKINS.put("mina", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmE0YjUxYmY4NTNiZWY3ZTg4YzBkMTg4YThjYTFhNmRlMGZhYTIyMmY3YWJiNTJlN2I4Y2FmN2JjZjcwYWU2MiJ9fX0=");
        SKINS.put("morteiro", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzUwNTkwZjRiMDhiOTIxZTUxNWY3ZTJiZWE2NGUzYTM1ZGFjYjZiNTkzNzdhY2RlZWU0YTNkZmQ4YmY2ODQ3ZiJ9fX0=");
        SKINS.put("tesla", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWYzZTFlYjA4NTY1NzVkNjFkMmQwNGM0ZDUyODcxN2U1NzYxYjhlMDBmMzhkNDk2MWU2OTkwMTM2ZjlkMjFkNCJ9fX0=");
        SKINS.put("farol", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmI4YjJmMmEzZTBlNzgyZDY3ZGE1M2VkMDEzMWIxNDI4MDc3OTM5NmJiY2UwOWU1ZjcxMjY2ZDc0NTQ0ZGVlOCJ9fX0=");
        SKINS.put("balista", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDAyNjBmZjBmN2FjMzU3YjdlYzIyYmQyMjAyZGI3YjY3ZGRjODk5ZWM2NmM4Y2I3MTZjZjYwMWRiNDY5NWZlNiJ9fX0=");
        SKINS.put("laser", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2U0YjE3NGQ0ZWY0MTQwNGExMmVmMzA5ZTQ3Yzk1MWFmMDM3Y2I5ZmI2ODg5ZTIzZjJmNDgyODEwYWE2OTA5MiJ9fX0=");
    }

    static ResolvableProfile perfil(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            String nome = ("torre_" + k).length() > 16 ? ("torre_" + k).substring(0, 16) : "torre_" + k;
            ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(nome)
                    .uuid(UUID.nameUUIDFromBytes(("corona-torre-" + k).getBytes(StandardCharsets.UTF_8)));
            String v = SKINS.get(k);
            if (v != null) b.addProperty(new ProfileProperty("textures", v));
            return b.build();
        });
    }

    private Skins() {}
}
