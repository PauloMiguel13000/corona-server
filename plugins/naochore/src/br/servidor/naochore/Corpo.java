package br.servidor.naochore;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * O corpo de um monstro do Nao Chore. Quem esta no Java com o pacote de recursos ve o modelo 3D proprio
 * (dois item displays: o corpo e os olhos, que brilham no escuro). Quem esta no Bedrock, ou no Java sem o pacote,
 * ve um manequim com a skin de reserva. As tres entidades andam juntas e so aparecem para quem deve ver.
 * Quadros do modelo: idle, walk1, walk2, grito (trocados com o item do display).
 */
final class Corpo {
    static NaoChore pl;
    static final String NS = "corona";

    final String tipo;
    final double escala;
    /** null = todo mundo ve; senao so esse jogador. */
    final UUID so;
    ItemDisplay corpo, olhos;
    Mannequin reserva;
    /** Bedrock: entidade vanilla "emprestada" (zumbi-cavalo, mula, farejador) que o pacote Bedrock redesenha como o monstro. */
    org.bukkit.entity.Ageable bedrock;
    private String quadro = "idle", quadroOlhos = "idle";
    private int anda, respira;
    private float yaw, pitch;
    private long ultimaVisib = -1;

    Corpo(Location l, String tipo, double escala, UUID so) {
        this(l, tipo, escala, so, null);
    }

    /** perfil != null: manequim com essa skin e sem modelo 3D. */
    Corpo(Location l, String tipo, double escala, UUID so, io.papermc.paper.datacomponent.item.ResolvableProfile perfil) {
        this.tipo = tipo;
        this.escala = escala;
        this.so = so;
        World w = l.getWorld();
        yaw = l.getYaw();
        pitch = l.getPitch();
        boolean modelos = perfil == null && pl.getConfig().getBoolean("modelos-3d", true);
        boolean temOlhos = !tipo.equals("maos");
        reserva = w.spawn(l, Mannequin.class, m -> {
            m.getPersistentDataContainer().set(pl.kMonstro, PersistentDataType.BYTE, (byte) 3);
            m.setVisibleByDefault(false);
            m.setProfile(perfil != null ? perfil : Skins.perfil(tipo.equals("maos") ? "vigia" : tipo));
            if (tipo.equals("maos")) m.setInvisible(true);
            m.setDescription(null);
            m.setImmovable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setInvulnerable(true);
            m.setCollidable(false);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            AttributeInstance sc = m.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(escala * (tipo.equals("faminto") ? 1.0 : 1.05));
        });
        reserva.setRotation(yaw, pitch);
        reserva.setBodyYaw(yaw);
        if (modelos) {
            int brilhoCorpo = pl.getConfig().getInt("brilho-corpo", 7), brilhoOlhos = pl.getConfig().getInt("brilho-olhos", 15);
            corpo = display(l, item(tipo + "_idle"), brilhoCorpo);
            olhos = temOlhos ? display(l, item(tipo + "_olhos_idle"), brilhoOlhos) : null;
        }
        Class<? extends org.bukkit.entity.Ageable> emp = emprestada(tipo);
        if (emp != null && perfil == null && pl.getConfig().getBoolean("bedrock-3d", true)) {
            org.bukkit.NamespacedKey sn = org.bukkit.NamespacedKey.fromString("levelledmobs:nolevel");
            bedrock = w.spawn(l, emp, e -> {
                e.getPersistentDataContainer().set(pl.kMonstro, PersistentDataType.BYTE, (byte) 5);
                if (sn != null) e.getPersistentDataContainer().set(sn, PersistentDataType.STRING, "naochore");
                e.addScoreboardTag("clearlag_exclude");
                e.setVisibleByDefault(false);
                e.setAdult();
                e.setAI(false);
                e.setGravity(false);
                e.setSilent(true);
                e.setInvulnerable(true);
                e.setCollidable(false);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                if (e instanceof org.bukkit.entity.PiglinAbstract pa) pa.setImmuneToZombification(true);   // nao vira zumbi fora do Nether
                if (e.getEquipment() != null) e.getEquipment().clear();
                if (e instanceof org.bukkit.entity.AbstractHorse h) {
                    h.setTamed(false);
                    if (h instanceof org.bukkit.entity.ChestedHorse ch) ch.setCarryingChest(false);
                }
            });
            bedrock.setRotation(yaw, 0);
            bedrock.setBodyYaw(yaw);
        }
        visibilidade(true);
    }

    /** Entidade vanilla usada no Bedrock para cada monstro (tem que bater com o pacote Bedrock: entity/*.entity.json). */
    static Class<? extends org.bukkit.entity.Ageable> emprestada(String tipo) {
        return switch (tipo) {
            case "vigia" -> org.bukkit.entity.ZombieHorse.class;
            // era a mula, mas a mula e um dos pets (BabyPets): no celular o pet aparecia com a cara da Chorona
            case "chorona" -> org.bukkit.entity.PiglinBrute.class;
            case "faminto" -> org.bukkit.entity.Sniffer.class;
            case "duplo" -> org.bukkit.entity.SkeletonHorse.class;
            default -> null;
        };
    }

    private ItemDisplay display(Location l, ItemStack item, int brilho) {
        Location o = l.clone();
        o.setYaw(yaw + giro());
        o.setPitch(0);
        ItemDisplay d = l.getWorld().spawn(o, ItemDisplay.class, x -> {
            x.getPersistentDataContainer().set(pl.kMonstro, PersistentDataType.BYTE, (byte) 4);
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
        return d;
    }

    private static float giro() {
        return (float) pl.getConfig().getDouble("modelo-giro", 180.0);
    }

    /** O modelo tem os pes em y=-16 (1,5 bloco abaixo do centro): sobe para os pes ficarem no chao. */
    private Transformation transformacao(float respiro) {
        float s = (float) escala;
        // o modelo e desenhado com o centro em x=0, z=0 e o Java posiciona o item pelo ponto (8, 8, 8): sem corrigir, o monstro
        // aparecia meio bloco fora do lugar de verdade. O item display vira o item 180 graus DEPOIS desta transformacao,
        // por isso a correcao aqui e -0,5 (e nao +0,5) em x e z.
        return new Transformation(new Vector3f(-0.5f * s, 1.5f * s, -0.5f * s), new AxisAngle4f(), new Vector3f(s, s * respiro, s), new AxisAngle4f());
    }

    @SuppressWarnings("UnstableApiUsage")
    static ItemStack item(String modelo) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key(NS, "naochore/" + modelo));
        return it;
    }

    boolean valido() {
        return reserva != null && reserva.isValid();
    }

    Location getLocation() {
        return reserva.getLocation();
    }

    World getWorld() {
        return reserva.getWorld();
    }

    /** A entidade "fisica" (para linha de visao, distancia...). */
    Entity entidade() {
        return reserva;
    }

    boolean eh(Entity e) {
        if (e == null) return false;
        UUID u = e.getUniqueId();
        return (reserva != null && u.equals(reserva.getUniqueId())) || (corpo != null && u.equals(corpo.getUniqueId())) || (olhos != null && u.equals(olhos.getUniqueId()))
                || (bedrock != null && u.equals(bedrock.getUniqueId()));
    }

    void teleport(Location l) {
        Location o = l.clone();
        o.setYaw(yaw);
        o.setPitch(pitch);
        reserva.teleport(o);
        reserva.setBodyYaw(yaw);
        if (bedrock != null) {
            Location b = l.clone();
            b.setYaw(yaw);
            b.setPitch(0);
            bedrock.teleport(b);
            bedrock.setBodyYaw(yaw);
        }
        if (corpo != null) {
            Location d = l.clone();
            d.setYaw(yaw + giro());
            d.setPitch(0);
            corpo.teleport(d);
            if (olhos != null) olhos.teleport(d);
        }
    }

    void rotacao(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Math.max(-30, Math.min(30, pitch));
        reserva.setRotation(yaw, this.pitch);
        reserva.setBodyYaw(yaw);
        if (bedrock != null) {
            bedrock.setRotation(yaw, 0);
            bedrock.setBodyYaw(yaw);
        }
        if (corpo != null) {
            corpo.setRotation(yaw + giro(), 0);
            if (olhos != null) olhos.setRotation(yaw + giro(), 0);
        }
    }

    /** Vira o corpo para um ponto (a cabeca do manequim acompanha, o modelo gira inteiro). */
    void olharPara(Location alvo) {
        Location o = getLocation();
        o.setDirection(alvo.toVector().subtract(o.toVector().add(new Vector(0, 1.6 * escala, 0))));
        rotacao(o.getYaw(), o.getPitch());
    }

    void quadro(String q) {
        if (q.equals(quadro)) return;
        quadro = q;
        // Bedrock: sempre adulto. Filhote (usado antes para o "grito") faz o Geyser encolher o bicho no celular.
        if (bedrock != null && !bedrock.isAdult()) bedrock.setAdult();
        if (corpo == null) return;
        corpo.setItemStack(item(tipo + "_" + q));
        String qo = q.equals("grito") ? "grito" : "idle";
        if (olhos != null && !qo.equals(quadroOlhos)) {
            quadroOlhos = qo;
            olhos.setItemStack(item(tipo + "_olhos_" + qo));
        }
    }

    /** Chamar todo tick quando o monstro se move: alterna os quadros de andar. Parado volta ao idle. */
    void andar(boolean movendo) {
        if (corpo == null && bedrock == null) return;
        if (!movendo) {
            if (!quadro.equals("grito")) quadro("idle");
            return;
        }
        anda++;
        if (quadro.equals("grito")) return;
        // passo em 4 tempos (perna na frente, passando, a outra na frente, passando): no Java fica bem menos duro
        int fase = (anda / 3) % 4;
        quadro(fase == 0 ? "walk1" : fase == 2 ? "walk2" : "idle");
    }

    /** Respiracao: o modelo cresce e encolhe um pouquinho (interpolado). Chamar a cada tick. */
    void respirar() {
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

    /** Mostra o modelo 3D para quem tem o pacote (Java) e o manequim para o resto. Rapido: so quem esta no mundo. */
    void visibilidade(boolean forcar) {
        long agora = pl.getServer().getCurrentTick();
        if (!forcar && ultimaVisib >= 0 && agora - ultimaVisib < 20) return;
        ultimaVisib = agora;
        for (Player p : getWorld().getPlayers()) {
            if (so != null && !p.getUniqueId().equals(so)) {
                p.hideEntity(pl, reserva);
                if (bedrock != null) p.hideEntity(pl, bedrock);
                if (corpo != null) {
                    p.hideEntity(pl, corpo);
                    if (olhos != null) p.hideEntity(pl, olhos);
                }
                continue;
            }
            boolean ehBedrock = Sons.bedrock(p);
            boolean modelo = corpo != null && Sons.temPacote(p) && !ehBedrock;
            boolean emprestada = bedrock != null && ehBedrock;
            ver(p, reserva, !modelo && !emprestada);
            if (bedrock != null) ver(p, bedrock, emprestada);
            if (corpo != null) {
                ver(p, corpo, modelo);
                if (olhos != null) ver(p, olhos, modelo);
            }
        }
    }

    /** Tira equipamento que outro plugin (LevelledMobs, EliteMobs) tenha posto numa entidade invisivel nossa:
     * armadura flutuando no meio do monstro. */
    static void semEquipamento(org.bukkit.entity.LivingEntity z) {
        org.bukkit.inventory.EntityEquipment eq = z.getEquipment();
        if (eq == null) return;
        for (org.bukkit.inventory.EquipmentSlot s : org.bukkit.inventory.EquipmentSlot.values()) {
            try {
                ItemStack it = eq.getItem(s);
                if (it != null && !it.getType().isAir()) eq.setItem(s, null);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void ver(Player p, Entity e, boolean sim) {
        if (sim == p.canSee(e)) return;
        if (sim) p.showEntity(pl, e);
        else p.hideEntity(pl, e);
    }

    void remove() {
        if (bedrock != null) bedrock.remove();
        if (reserva != null) reserva.remove();
        if (corpo != null) corpo.remove();
        if (olhos != null) olhos.remove();
    }
}
