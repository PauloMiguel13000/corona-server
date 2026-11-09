package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Particulas por espectador (08/11). Java recebe as particulas como sempre. Bedrock (celular) NAO recebe a chuva de
 * particulas (cada particula Java vira um pacote no Geyser; era a maior causa do travamento): em vez disso o servidor poe
 * um "emissor" no lugar — um ItemDisplay invisivel com o item corona:fx_<categoria> — e o pacote v2 do Bedrock desenha
 * sozinho, no celular, uma animacao rica daquela categoria (fogo, gelo, raio, luz, fumaca, sombra, cura, agua, golpe,
 * explosao, natureza, veneno, magia, morte, terra, vento, sangue e poeira colorida). Emissores proximos da mesma
 * categoria sao juntados por celula de 2 blocos e duram enquanto chegarem particulas (+8 ticks). Quem joga no Java nao
 * ve o emissor (entidade escondida + modelo vazio).
 */
final class Fx {
    static final double ALCANCE = 64;
    static final int MAX_EMISSORES = 64;
    static final int VIDA_TICKS = 8;
    static Plugin plugin;
    static boolean emissoresLigados = true;
    static int divisorBedrock = 3;

    private static final Map<Long, Emissor> EMISSORES = new HashMap<>();
    private static final Map<String, ItemStack> ITENS = new HashMap<>();
    private static int tarefa = -1;

    private static final class Emissor {
        ItemDisplay e;
        int ate;
    }

    private Fx() {
    }

    static void ligar(Plugin pl) {
        plugin = pl;
        emissoresLigados = pl.getConfig().getBoolean("celular.emissores", true);
        if (tarefa == -1) tarefa = Bukkit.getScheduler().scheduleSyncRepeatingTask(pl, Fx::varrer, 2L, 2L);
    }

    static void desligar() {
        if (tarefa != -1) Bukkit.getScheduler().cancelTask(tarefa);
        tarefa = -1;
        for (Emissor em : EMISSORES.values()) if (em.e != null && em.e.isValid()) em.e.remove();
        EMISSORES.clear();
    }

    private static void varrer() {
        int agora = Bukkit.getCurrentTick();
        Iterator<Emissor> it = EMISSORES.values().iterator();
        while (it.hasNext()) {
            Emissor em = it.next();
            if (em.ate <= agora || em.e == null || !em.e.isValid()) {
                if (em.e != null && em.e.isValid()) em.e.remove();
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ chamadas (mesma assinatura do spawnParticle)

    static void p(World w, Particle pt, Location l, int n) {
        p(w, pt, l, n, 0, 0, 0, 0, null);
    }

    static void p(World w, Particle pt, Location l, int n, double ox, double oy, double oz) {
        p(w, pt, l, n, ox, oy, oz, 0, null);
    }

    static void p(World w, Particle pt, Location l, int n, double ox, double oy, double oz, double extra) {
        p(w, pt, l, n, ox, oy, oz, extra, null);
    }

    static <T> void p(World w, Particle pt, Location l, int n, double ox, double oy, double oz, T data) {
        p(w, pt, l, n, ox, oy, oz, 0, data);
    }

    static <T> void p(World w, Particle pt, Location l, int n, T data) {
        p(w, pt, l, n, 0, 0, 0, 0, data);
    }

    static void p(World w, Particle pt, double x, double y, double z, int n) {
        p(w, pt, new Location(w, x, y, z), n, 0, 0, 0, 0, null);
    }

    static void p(World w, Particle pt, double x, double y, double z, int n, double ox, double oy, double oz) {
        p(w, pt, new Location(w, x, y, z), n, ox, oy, oz, 0, null);
    }

    static void p(World w, Particle pt, double x, double y, double z, int n, double ox, double oy, double oz, double extra) {
        p(w, pt, new Location(w, x, y, z), n, ox, oy, oz, extra, null);
    }

    static <T> void p(World w, Particle pt, double x, double y, double z, int n, double ox, double oy, double oz, double extra, T data) {
        p(w, pt, new Location(w, x, y, z), n, ox, oy, oz, extra, data);
    }

    static <T> void p(World w, Particle pt, Location l, int n, double ox, double oy, double oz, double extra, T data) {
        if (w == null || l == null || pt == null) return;
        if (l.getWorld() != null && !l.getWorld().equals(w)) w = l.getWorld();
        boolean tickPar = (Bukkit.getCurrentTick() & 1) == 0;
        double a2 = ALCANCE * ALCANCE;
        boolean bedrockPerto = false;
        boolean usarEmissor = emissoresLigados && plugin != null;
        for (Player j : w.getPlayers()) {
            Location pl = j.getLocation();
            double dx = pl.getX() - l.getX(), dz = pl.getZ() - l.getZ(), dy = pl.getY() - l.getY();
            if (dx * dx + dz * dz + dy * dy > a2) continue;
            int q = n;
            if (Cenas.ehBedrock(j)) {
                bedrockPerto = true;
                if (usarEmissor) {
                    if (!passaDireto(pt)) continue;        // o emissor cuida; so as "grandes" unicas passam direto
                    q = Math.min(n, 1);
                } else {
                    if (n <= 2 && !tickPar) continue;
                    q = Math.max(1, n / Math.max(1, divisorBedrock));
                }
            }
            try {
                if (data != null) j.spawnParticle(pt, l, q, ox, oy, oz, extra, data);
                else j.spawnParticle(pt, l, q, ox, oy, oz, extra);
            } catch (IllegalArgumentException ignored) {
                // particula com dado errado: ignora em vez de derrubar a habilidade
            }
        }
        if (bedrockPerto && usarEmissor) emissor(w, l, pt, n, Math.max(ox, Math.max(oy, oz)), data);
    }

    /** Particulas unicas e grandes que o Geyser ja mostra bem: passam direto (1 pacote) alem do emissor. */
    private static boolean passaDireto(Particle pt) {
        return switch (pt) {
            case EXPLOSION, EXPLOSION_EMITTER, GUST_EMITTER_LARGE, GUST_EMITTER_SMALL, SONIC_BOOM, FLASH -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ emissores (Bedrock)

    static String categoria(Particle pt, Object data) {
        return switch (pt) {
            case FLAME, SMALL_FLAME, LAVA, COPPER_FIRE_FLAME, DRIPPING_LAVA, FALLING_LAVA, LANDING_LAVA -> "fogo";
            case SOUL_FIRE_FLAME, SOUL, SCULK_SOUL -> "alma";
            case SNOWFLAKE, ITEM_SNOWBALL -> "gelo";
            case ELECTRIC_SPARK, WAX_ON, WAX_OFF, SCRAPE -> "raio";
            case END_ROD, FLASH, TOTEM_OF_UNDYING, NOTE, FIREWORK, GLOW, ENCHANT, INSTANT_EFFECT -> "luz";
            case CLOUD, POOF, SMOKE, LARGE_SMOKE, WHITE_SMOKE, CAMPFIRE_COSY_SMOKE, CAMPFIRE_SIGNAL_SMOKE, SNEEZE, ASH, WHITE_ASH, SPIT -> "fumaca";
            case SQUID_INK, PORTAL, REVERSE_PORTAL, WITCH, SHRIEK, DRAGON_BREATH, MYCELIUM, SCULK_CHARGE, SCULK_CHARGE_POP, VIBRATION -> "sombra";
            case HEART, HAPPY_VILLAGER, COMPOSTER -> "cura";
            case SPLASH, BUBBLE_POP, BUBBLE_COLUMN_UP, BUBBLE, FALLING_WATER, DRIPPING_WATER, RAIN, FISHING, UNDERWATER, DOLPHIN, NAUTILUS -> "agua";
            case CRIT, ENCHANTED_HIT, SWEEP_ATTACK, DAMAGE_INDICATOR, ANGRY_VILLAGER -> "golpe";
            case EXPLOSION, EXPLOSION_EMITTER, GUST_EMITTER_SMALL, GUST_EMITTER_LARGE, SONIC_BOOM, GUST, SMALL_GUST -> "explosao";
            case DUST_PILLAR, BLOCK, FALLING_DUST, BLOCK_CRUMBLE, DUST_PLUME, ITEM -> "terra";
            case CHERRY_LEAVES, FALLING_SPORE_BLOSSOM, SPORE_BLOSSOM_AIR, PALE_OAK_LEAVES, TINTED_LEAVES -> "natureza";
            case ITEM_SLIME, DRIPPING_HONEY, FALLING_HONEY, LANDING_HONEY, CRIMSON_SPORE, WARPED_SPORE -> "veneno";
            case DUST, DUST_COLOR_TRANSITION, ENTITY_EFFECT, TRAIL -> {
                Color c = corDe(data);
                yield c == null ? "magia" : "poeira_" + corNome(c);
            }
            default -> "magia";
        };
    }

    private static Color corDe(Object data) {
        if (data instanceof Particle.DustOptions d) return d.getColor();
        if (data instanceof Particle.DustTransition d) return d.getColor();
        if (data instanceof Color c) return c;
        if (data instanceof Particle.Trail t) return t.getColor();
        return null;
    }

    private static final Object[][] PALETA = {
            {"vermelho", 240, 40, 40}, {"laranja", 255, 140, 25}, {"amarelo", 255, 230, 50}, {"verde", 75, 230, 75},
            {"azul", 65, 115, 255}, {"ciano", 75, 230, 255}, {"roxo", 150, 65, 230}, {"rosa", 255, 130, 205},
            {"branco", 245, 245, 245}, {"preto", 30, 25, 35}, {"marrom", 140, 90, 40}, {"dourado", 255, 205, 75},
    };

    static String corNome(Color c) {
        String melhor = "branco";
        double md = Double.MAX_VALUE;
        for (Object[] p : PALETA) {
            double dr = c.getRed() - (int) p[1], dg = c.getGreen() - (int) p[2], db = c.getBlue() - (int) p[3];
            double d = dr * dr + dg * dg + db * db;
            if (d < md) {
                md = d;
                melhor = (String) p[0];
            }
        }
        return melhor;
    }

    private static ItemStack item(String cat) {
        return ITENS.computeIfAbsent(cat, k -> {
            ItemStack it = new ItemStack(Material.LEATHER_HELMET);
            try {
                it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "fx/" + k));
            } catch (Throwable ignored) {
            }
            return it;
        });
    }

    private static void emissor(World w, Location l, Particle pt, int n, double espalha, Object data) {
        String cat = categoria(pt, data);
        boolean grande = espalha >= 0.9 || n >= 40;
        if (grande) cat = cat + "_g";
        int cx = (int) Math.floor(l.getX() / 2), cy = (int) Math.floor(l.getY() / 2), cz = (int) Math.floor(l.getZ() / 2);
        long chave = (((long) cat.hashCode()) << 40) ^ (((long) (cx & 0xFFFF)) << 24) ^ (((long) (cy & 0xFF)) << 16) ^ (cz & 0xFFFF)
                ^ (((long) w.getUID().hashCode()) << 56);
        int agora = Bukkit.getCurrentTick();
        Emissor em = EMISSORES.get(chave);
        if (em != null && em.e != null && em.e.isValid()) {
            em.ate = agora + VIDA_TICKS;
            return;
        }
        if (EMISSORES.size() >= MAX_EMISSORES) return;
        Location onde = l.clone();
        onde.setYaw(0);
        onde.setPitch(0);
        final String catF = cat;
        ItemDisplay d;
        try {
            d = w.spawn(onde, ItemDisplay.class, x -> {
                x.setItemStack(item(catF));
                x.setPersistent(false);
                x.setViewRange(1.2f);
                x.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            });
        } catch (Throwable ex) {
            return;
        }
        for (Player j : w.getPlayers()) if (!Cenas.ehBedrock(j)) j.hideEntity(plugin, d);
        em = new Emissor();
        em.e = d;
        em.ate = agora + VIDA_TICKS;
        EMISSORES.put(chave, em);
    }

    /** Jogador do Java que entrou/mudou de mundo: esconde os emissores vivos dele. */
    static void esconderDe(Player j) {
        if (Cenas.ehBedrock(j) || plugin == null) return;
        for (Emissor em : EMISSORES.values()) if (em.e != null && em.e.isValid() && em.e.getWorld().equals(j.getWorld())) j.hideEntity(plugin, em.e);
    }

    static int emissoresVivos() {
        return EMISSORES.size();
    }

    static List<String> categoriasConhecidas() {
        List<String> v = new ArrayList<>();
        for (String s : new String[]{"fogo", "alma", "gelo", "raio", "luz", "fumaca", "sombra", "cura", "agua", "golpe", "explosao", "natureza",
                "veneno", "magia", "morte", "terra", "vento", "sangue"}) v.add(s);
        for (Object[] p : PALETA) v.add("poeira_" + p[0]);
        return v;
    }
}
