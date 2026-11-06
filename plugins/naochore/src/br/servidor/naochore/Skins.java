package br.servidor.naochore;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skins dos monstros (galeria publica do MineSkin, texturas hospedadas pela Mojang: o Java baixa sozinho e o Geyser
 * manda para o Bedrock). O Vigia (preto, olhos brancos), a Chorona (menina palida chorando sangue) e o Faminto
 * (esqueleto do dia 5).
 */
final class Skins {
    private static final Map<String, String> SKINS = new HashMap<>();
    private static final Map<String, ResolvableProfile> CACHE = new HashMap<>();

    static {
        SKINS.put("vigia", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWJiMDQ1YzQwZGFlMzlkZGU5OWYyOTllZWFiMTBkNTgwZTQxMDdlN2I1OTEzNDNiNmY2YWZlYjU2YTNjZWI2NyJ9fX0=");
        SKINS.put("chorona", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzUzZWNkZDg3MTc0NmU4YWQ5ZjIxMWE1MWU4OGQ2MTMyYzExMTM5MTk0ZWRiYjkzNGY5Mjk5YWVlNmY4NjczYyJ9fX0=");
        SKINS.put("faminto", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTQwYjVjMTlhNzVhYzFmOTY0YzZlYWE1YjQyMGRjODMwNmY4Y2U5NTdjZmE4ZDcxYjgwYzA0NmExOWZiOTIyNCJ9fX0=");
    }

    static ResolvableProfile perfil(String k) {
        if (k.equals("duplo")) k = "vigia";   // o Duplo so existe em 3D; o boneco de reserva usa a skin do Vigia
        return CACHE.computeIfAbsent(k, x -> {
            String nome = ("nc_" + x).length() > 16 ? ("nc_" + x).substring(0, 16) : "nc_" + x;
            ResolvableProfile.Builder b = ResolvableProfile.resolvableProfile().name(nome)
                    .uuid(UUID.nameUUIDFromBytes(("corona-naochore-" + x).getBytes(StandardCharsets.UTF_8)));
            String t = SKINS.get(x);
            if (t != null) b.addProperty(new ProfileProperty("textures", t));
            return b.build();
        });
    }

    private Skins() {}
}
