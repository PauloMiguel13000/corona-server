package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Coroa do chefe: um adorno 3D proprio de cada masmorra (modelo corona:item/chefes/<tema> do pacote) que flutua girando
 * em cima da cabeca do chefe, como carona (ItemDisplay). No Java aparece o modelo; no Bedrock aparece o item base do
 * tema (cabeca ou bloco 3D do jogo), que o Geyser mostra.
 */
final class Coroas {

    private static final Map<String, String> BASE = Map.ofEntries(
            Map.entry("CRIPTA", "SKELETON_SKULL"), Map.entry("NINHO", "COBWEB"), Map.entry("MINA", "LANTERN"), Map.entry("PANTANO", "CAULDRON"),
            Map.entry("GELO", "BLUE_ICE"), Map.entry("DESERTO", "GOLD_BLOCK"), Map.entry("AFOGADA", "PRISMARINE"), Map.entry("TORRE", "ENCHANTING_TABLE"),
            Map.entry("QUARTEL", "ANVIL"), Map.entry("SELVA", "JUNGLE_LEAVES"), Map.entry("VENTOS", "WHITE_WOOL"), Map.entry("CASTELO", "ANVIL"),
            Map.entry("FORJA", "MAGMA_BLOCK"), Map.entry("BASTIAO", "GOLD_BLOCK"), Map.entry("WITHER", "WITHER_SKELETON_SKULL"), Map.entry("COVIL", "NETHERRACK"),
            Map.entry("CIDADELA", "END_ROD"), Map.entry("PRISAO", "IRON_BARS"), Map.entry("SECRETA", "SEA_LANTERN"), Map.entry("CEMITERIO", "SKELETON_SKULL"),
            Map.entry("ESGOTO", "SLIME_BLOCK"), Map.entry("ACAMPAMENTO", "CROSSBOW"), Map.entry("MOINHO", "HAY_BLOCK"), Map.entry("FUNGOS", "RED_MUSHROOM_BLOCK"),
            Map.entry("COVIL_LOBOS", "BONE_BLOCK"), Map.entry("URSO", "SNOW_BLOCK"), Map.entry("BOSQUE_PALIDO", "PALE_OAK_LEAVES"), Map.entry("FAROL", "SEA_LANTERN"),
            Map.entry("LABORATORIO", "BREWING_STAND"), Map.entry("CAMARA_TESTES", "COPPER_BLOCK"), Map.entry("FAZENDA", "HAY_BLOCK"), Map.entry("CRATERA", "MAGMA_BLOCK"),
            Map.entry("JARDIM", "CHERRY_LEAVES"), Map.entry("CATEDRAL", "GLOWSTONE"), Map.entry("TESOURO", "GOLD_BLOCK"), Map.entry("COLISEU", "IRON_BLOCK"),
            Map.entry("TROVAO", "LIGHTNING_ROD"), Map.entry("OBSERVATORIO", "END_ROD"), Map.entry("VIUVA", "COBWEB"), Map.entry("NAUFRAGIO", "SKELETON_SKULL"),
            Map.entry("MAUSOLEU", "GOLD_BLOCK"), Map.entry("BIBLIOTECA", "BOOKSHELF"), Map.entry("VULCAO", "MAGMA_BLOCK"), Map.entry("CIDADE_ANTIGA", "SCULK"),
            Map.entry("GELEIRA", "PACKED_ICE"), Map.entry("SANGUE", "REDSTONE_BLOCK"), Map.entry("RELOGIO", "CLOCK"), Map.entry("LABIRINTO", "BONE_BLOCK"),
            Map.entry("CRIPTA_REAL", "LAPIS_BLOCK"), Map.entry("DELTA", "IRON_BLOCK"), Map.entry("CARMESIM", "CRIMSON_NYLIUM"), Map.entry("DISTORCIDO", "WARPED_NYLIUM"),
            Map.entry("ALMAS", "SOUL_LANTERN"), Map.entry("OBSIDIANA", "CRYING_OBSIDIAN"), Map.entry("ILHAS", "PURPUR_BLOCK"), Map.entry("NAVE", "SHULKER_BOX"),
            Map.entry("TRONO", "OBSIDIAN"), Map.entry("CORO", "CHORUS_FLOWER"), Map.entry("ABISMO", "END_ROD"));

    private final Masmorras pl;
    private final NamespacedKey kCoroa;
    private final List<ItemDisplay> coroas = new ArrayList<>();
    private float giro;

    Coroas(Masmorras pl) {
        this.pl = pl;
        kCoroa = new NamespacedKey(pl, "coroa_chefe");
        Bukkit.getScheduler().runTaskTimer(pl, this::girar, 20L, 2L);
        // coroas que sobraram de chefes antigos (reinicio): somem
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (org.bukkit.World w : Bukkit.getWorlds())
                for (Entity e : w.getEntitiesByClass(ItemDisplay.class))
                    if (e.getPersistentDataContainer().has(kCoroa) && (e.getVehicle() == null || e.getVehicle().isDead())) e.remove();
        }, 100L);
    }

    static ItemStack item(Tipo t) {
        // pacote v2 (08/11): no Bedrock a coroa 3D e um item custom do Geyser em cima de leather_helmet (mapeamento por
        // item_model); quem nao tem pacote nenhum ve um capacete de couro. BASE fica so para 'coroas.bedrock-vanilla: true'.
        Material base = Material.LEATHER_HELMET;
        if (Boolean.getBoolean("corona.coroas.bedrock-vanilla")) {
            base = Material.matchMaterial(BASE.getOrDefault(t.id, "GOLD_BLOCK"));
            if (base == null) base = Material.GOLD_BLOCK;
        }
        ItemStack it = new ItemStack(base);
        try {
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "item/chefes/" + t.id.toLowerCase(Locale.ROOT)));
        } catch (Throwable ignored) {
        }
        return it;
    }

    /** Poe a coroa flutuando em cima do chefe. */
    void vestir(LivingEntity chefe, Tipo t) {
        if (!pl.getConfig().getBoolean("coroas.ativo", true)) return;
        try {
            double altura = chefe.getHeight();
            float escala = (float) Math.max(0.7, Math.min(2.2, 0.8 + altura * 0.25));
            ItemDisplay d = chefe.getWorld().spawn(chefe.getLocation(), ItemDisplay.class, x -> {
                x.setItemStack(item(t));
                x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                x.setBillboard(Display.Billboard.FIXED);
                x.setBrightness(new Display.Brightness(15, 15));
                x.setPersistent(false);
                x.setShadowStrength(0f);
                x.setViewRange(1.5f);
                x.getPersistentDataContainer().set(kCoroa, PersistentDataType.INTEGER, 1);
                // a carona fica no ombro: sobe a coroa para cima da cabeca
                x.setTransformation(new Transformation(new Vector3f(0f, (float) (0.55 + altura * 0.12), 0f), new AxisAngle4f(0f, 0f, 1f, 0f), new Vector3f(escala), new AxisAngle4f()));
            });
            if (!chefe.addPassenger(d)) {
                d.remove();
                return;
            }
            coroas.add(d);
        } catch (Throwable ex) {
            pl.getLogger().warning("Coroa do chefe " + t.chefeNome + ": " + ex);
        }
    }

    private void girar() {
        if (coroas.isEmpty()) return;
        giro += 0.12f;
        Iterator<ItemDisplay> it = coroas.iterator();
        while (it.hasNext()) {
            ItemDisplay d = it.next();
            Entity v = d.getVehicle();
            if (!d.isValid() || v == null || v.isDead() || !v.isValid()) {
                d.remove();
                it.remove();
                continue;
            }
            Transformation t = d.getTransformation();
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
            d.setTransformation(new Transformation(t.getTranslation(), new AxisAngle4f(giro, 0f, 1f, 0f), t.getScale(), new AxisAngle4f()));
        }
    }

    void parar() {
        for (ItemDisplay d : coroas) if (d.isValid()) d.remove();
        coroas.clear();
    }
}
