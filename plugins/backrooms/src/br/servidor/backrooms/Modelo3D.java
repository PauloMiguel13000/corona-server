package br.servidor.backrooms;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Set;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

/**
 * Modelo 3D de um monstro das Backrooms (pacote de recursos): dois item displays (corpo e olhos) que acompanham o
 * manequim original, mais uma entidade vanilla "emprestada" para o Bedrock (o pacote .mcpack a redesenha).
 * Quadros: idle, walk1, walk2, grito (trocados pelo item do display; no Bedrock o grito e a pose de filhote).
 * Quem ve o que: Java com o pacote = modelo; Bedrock = emprestada (se houver) ou o manequim; Java sem pacote = gemeo.
 */
final class Modelo3D {
    static final String NS = "corona";
    /** Monstros com modelo 3D no pacote (gerados por fontes/modelos/backrooms.py). */
    static final Set<String> COM_MODELO = Set.of("bacteria", "uivador", "cao", "festeiro", "sombra", "corredor", "afogado");

    final String tipo;
    final double escala;
    ItemDisplay corpo, olhos;
    Ageable bedrock;
    private String quadro = "idle", quadroOlhos = "idle";
    private int anda, respira, gritoAte;
    /** Passo: fase do ciclo de andar (0..1) que avanca com a distancia andada, e a ultima posicao. */
    private double fase, ultX = Double.NaN, ultZ = Double.NaN, andou;
    /** Quadros do ciclo de andar no pacote (fontes/modelos/andar_java.py). */
    static final int QUADROS_ANDAR = 16;
    private final boolean temOlhos;
    private float yaw;

    static boolean tem(String skin) {
        return COM_MODELO.contains(skin);
    }

    /** Entidade vanilla usada no Bedrock (bate com o pacote: entity/*.entity.json). null = o Bedrock ve o manequim. */
    static Class<? extends Ageable> emprestada(String tipo) {
        return switch (tipo) {
            case "bacteria" -> org.bukkit.entity.HappyGhast.class;
            case "uivador" -> org.bukkit.entity.Zoglin.class;
            default -> null;
        };
    }

    Modelo3D(Plugin pl, Location l, String tipo, double escala) {
        this.tipo = tipo;
        this.escala = escala;
        World w = l.getWorld();
        yaw = l.getYaw();
        temOlhos = !tipo.equals("festeiro") && !tipo.equals("afogado");
        int brilhoCorpo = pl.getConfig().getInt("modelo-brilho-corpo", 6), brilhoOlhos = pl.getConfig().getInt("modelo-brilho-olhos", 15);
        corpo = display(pl, l, item(tipo + "_idle"), brilhoCorpo);
        olhos = temOlhos ? display(pl, l, item(tipo + "_olhos_idle"), brilhoOlhos) : null;
        Class<? extends Ageable> emp = emprestada(tipo);
        if (emp != null && pl.getConfig().getBoolean("modelo-bedrock", true)) {
            org.bukkit.NamespacedKey sn = org.bukkit.NamespacedKey.fromString("levelledmobs:nolevel");
            org.bukkit.NamespacedKey ke = new org.bukkit.NamespacedKey(pl, "entidade");
            bedrock = w.spawn(l, emp, e -> {
                e.getPersistentDataContainer().set(ke, PersistentDataType.BYTE, (byte) 1);
                if (sn != null) e.getPersistentDataContainer().set(sn, PersistentDataType.STRING, "backrooms");
                e.addScoreboardTag("clearlag_exclude");
                e.setVisibleByDefault(false);
                e.setAdult();
                if (e instanceof org.bukkit.entity.Sittable s) s.setSitting(false);
                if (e instanceof org.bukkit.entity.Axolotl a) a.setPlayingDead(false);
                e.setAI(false);
                e.setGravity(false);
                e.setSilent(true);
                e.setInvulnerable(true);
                e.setCollidable(false);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
            });
            bedrock.setRotation(yaw, 0);
            bedrock.setBodyYaw(yaw);
        }
    }

    private ItemDisplay display(Plugin pl, Location l, ItemStack item, int brilho) {
        Location o = l.clone();
        o.setYaw(yaw + giro(pl));
        o.setPitch(0);
        org.bukkit.NamespacedKey ke = new org.bukkit.NamespacedKey(pl, "entidade");
        return l.getWorld().spawn(o, ItemDisplay.class, x -> {
            x.getPersistentDataContainer().set(ke, PersistentDataType.BYTE, (byte) 1);
            x.setVisibleByDefault(false);
            x.setItemStack(item);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            x.setBillboard(Display.Billboard.FIXED);
            x.setBrightness(new Display.Brightness(Math.max(0, Math.min(15, brilho)), 15));
            x.setViewRange(1.5f);
            x.setTeleportDuration(2);
            x.setInterpolationDuration(0);
            x.setPersistent(false);
            x.setShadowRadius(0f);
            x.setTransformation(transformacao(1f));
        });
    }

    private static float giro(Plugin pl) {
        return (float) pl.getConfig().getDouble("modelo-giro", 180.0);
    }

    /** O modelo tem os pes em y=-16 (1,5 bloco abaixo do centro): sobe para os pes ficarem no chao. */
    private Transformation transformacao(float respiro) {
        float s = (float) escala;
        return new Transformation(new Vector3f(0f, 1.5f * s, 0f), new AxisAngle4f(), new Vector3f(s, s * respiro, s), new AxisAngle4f());
    }

    @SuppressWarnings("UnstableApiUsage")
    static ItemStack item(String modelo) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key(NS, "backrooms/" + modelo));
        return it;
    }

    boolean valido() {
        return corpo != null && corpo.isValid();
    }

    boolean eh(Entity e) {
        if (e == null) return false;
        return (corpo != null && e.getUniqueId().equals(corpo.getUniqueId())) || (olhos != null && e.getUniqueId().equals(olhos.getUniqueId()))
                || (bedrock != null && e.getUniqueId().equals(bedrock.getUniqueId()));
    }

    /** Todo tick: acompanha o manequim (posicao e giro) e anima: andar quando se move, respirar parado. */
    void seguir(Plugin pl, Location l, float yaw, boolean movendo, long tick) {
        this.yaw = yaw;
        double dx = Double.isNaN(ultX) ? 0 : l.getX() - ultX, dz = Double.isNaN(ultZ) ? 0 : l.getZ() - ultZ;
        ultX = l.getX();
        ultZ = l.getZ();
        andou = Math.min(1.5, Math.sqrt(dx * dx + dz * dz));   // teleporte grande nao conta como passo
        Location d = l.clone();
        d.setYaw(yaw + giro(pl));
        d.setPitch(0);
        if (corpo != null) {
            corpo.teleport(d);
            if (olhos != null) olhos.teleport(d);
        }
        if (bedrock != null) {
            Location b = l.clone();
            b.setYaw(yaw);
            b.setPitch(0);
            bedrock.teleport(b);
            bedrock.setBodyYaw(yaw);
        }
        if (gritoAte > 0 && tick >= gritoAte) {
            gritoAte = 0;
            quadro("idle");
        }
        andar(movendo);
        respirar();
    }

    void quadro(String q) {
        if (q.equals(quadro)) return;
        quadro = q;
        // Bedrock: sempre adulto. Filhote (usado antes para o "grito") faz o Geyser encolher o bicho no celular.
        if (bedrock != null && !bedrock.isAdult()) bedrock.setAdult();
        if (corpo == null) return;
        corpo.setItemStack(item(tipo + "_" + q));
        String qo = q.equals("grito") ? "grito" : q.startsWith("andar_") ? q : "idle";
        if (olhos != null && !qo.equals(quadroOlhos)) {
            quadroOlhos = qo;
            olhos.setItemStack(item(tipo + "_olhos_" + qo));
        }
    }

    /** Grito por alguns ticks (depois volta sozinho ao idle). */
    void gritar(long tick, int ticks) {
        gritoAte = (int) (tick + ticks);
        quadro("grito");
    }

    private void andar(boolean movendo) {
        if (!movendo) {
            if (!quadro.equals("grito")) quadro("idle");
            return;
        }
        anda++;
        // o ciclo anda junto com o chao: quanto mais rapido o bicho, mais rapido as pernas (nada de "patinar")
        fase = (fase + Math.max(andou, 0.02) / passada()) % 1.0;
        if (quadro.equals("grito")) return;
        int k = (int) (fase * QUADROS_ANDAR) % QUADROS_ANDAR;
        quadro(String.format("andar_%02d", k));
    }

    /** Blocos andados por ciclo completo (dois passos), do tamanho das pernas de cada monstro. */
    private double passada() {
        double base = switch (tipo) {
            case "corredor" -> 3.2;
            case "cao" -> 2.0;
            case "festeiro", "afogado" -> 1.8;
            default -> 1.9;
        };
        return Math.max(0.8, base * escala);
    }

    private void respirar() {
        if (corpo == null) return;
        respira++;
        if (respira % 30 != 0) return;
        float r = (respira / 30) % 2 == 0 ? 1f : 1.03f;
        corpo.setInterpolationDelay(0);
        corpo.setInterpolationDuration(30);
        corpo.setTransformation(transformacao(r));
        if (olhos != null) {
            olhos.setInterpolationDelay(0);
            olhos.setInterpolationDuration(30);
            olhos.setTransformation(transformacao(r));
        }
    }

    /** Quem deve ver o modelo (Java com pacote) e quem ve a emprestada (Bedrock). */
    boolean mostraModelo(Player p) {
        return corpo != null && Sons.temPacote(p) && !Sons.bedrock(p);
    }

    boolean mostraEmprestada(Player p) {
        return bedrock != null && Sons.bedrock(p);
    }

    void remover() {
        if (corpo != null) corpo.remove();
        if (olhos != null) olhos.remove();
        if (bedrock != null) bedrock.remove();
        corpo = null;
        olhos = null;
        bedrock = null;
    }
}
