package br.servidor.turno;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Um animatronico: boneco (Mannequin com skin) que "anda" de lugar em lugar por teleporte, no ritmo do nivel de IA
 * (0 a 20): a cada 'intervaloTicks' tira um numero de 1 a 20; se sair <= IA, avanca uma casa no caminho.
 */
final class Animatronico {

    final String id, nome, cor;
    final List<String> caminho;      // lugares (Mapa.LUGARES) ate o canto da porta; o ultimo e o canto
    final boolean portaOeste;        // por qual porta entra
    final int intervaloTicks;
    int ia;
    int posicao;                     // indice no caminho
    boolean noEscritorio;            // entrou: proximo passo e o susto
    int ticks;
    Mannequin boneco;
    /** Modelo 3D do pacote (ItemDisplay) que acompanha o boneco invisivel; null = so o boneco com skin. */
    ItemDisplay modelo;
    private boolean usaModelo;
    private Turno pl;

    Animatronico(String id, String nome, String cor, List<String> caminho, boolean portaOeste, int intervaloTicks) {
        this.id = id;
        this.nome = nome;
        this.cor = cor;
        this.caminho = caminho;
        this.portaOeste = portaOeste;
        this.intervaloTicks = intervaloTicks;
    }

    String lugarAtual() {
        return caminho.get(Math.min(posicao, caminho.size() - 1));
    }

    boolean noCanto() {
        return !noEscritorio && posicao == caminho.size() - 1;
    }

    void nascer(Turno pl, Mapa mapa) {
        Location l = mapa.loc(Mapa.LUGARES.get(caminho.get(0)));
        boneco = l.getWorld().spawn(l, Mannequin.class, CreatureSpawnEvent.SpawnReason.CUSTOM, m -> {
            var perfil = pl.skins.perfil(id);
            if (perfil != null) m.setProfile(perfil);
            m.setImmovable(true);
            m.setGravity(false);
            m.setCollidable(false);
            m.setSilent(true);
            m.setInvulnerable(true);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCustomNameVisible(false);
            m.setDescription(net.kyori.adventure.text.Component.empty());
            var escala = m.getAttribute(Attribute.SCALE);
            if (escala != null) escala.setBaseValue(1.25);
        });
        usaModelo = pl.getConfig().getBoolean("modelo-3d", true);
        this.pl = pl;
        criarModelo(0, 1f, 0f);
    }

    @SuppressWarnings("UnstableApiUsage")
    private ItemStack item(int quadro) {
        // leather_helmet: no Bedrock o Geyser (corona-animatronicos.json) troca pelo item 3D da extensao GeyserDisplayEntity
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                net.kyori.adventure.key.Key.key("corona", "props/anim_" + id + "_f" + quadro));
        return it;
    }

    /** Recria o modelo 3D em cima do boneco (quadro 0 parado, 1 susto). esc = tamanho em relacao ao normal. */
    private void criarModelo(int quadro, float esc, float dy) {
        boolean brilhando = modelo != null && modelo.isValid() && modelo.isGlowing();
        if (modelo != null) modelo.remove();
        modelo = null;
        if (!usaModelo || boneco == null || !boneco.isValid()) return;
        Location l = boneco.getLocation();
        l.setPitch(0);
        modelo = l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(item(quadro));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, 1.5f * esc + dy, 0), new Quaternionf(), new Vector3f(esc, esc, esc), new Quaternionf()));
            d.setViewRange(1.0f);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.setGlowing(brilhando);
            d.setVisibleByDefault(false);     // so quem tem o pacote ve o modelo; os outros veem o boneco com skin
        });
        if (pl != null) Pacote.repartir(pl, boneco, modelo);
    }

    /** Cada jogador ve o modelo 3D (com o pacote) ou o boneco com skin (sem o pacote). */
    void rever() {
        if (pl == null || boneco == null || !boneco.isValid()) return;
        // o boneco nasce antes do pedaco do mapa "ligar": nessa hora o modelo nao pode ser criado; cria na primeira chance
        if (usaModelo && (modelo == null || !modelo.isValid())) {
            criarModelo(0, 1f, 0f);
            return;
        }
        Pacote.repartir(pl, boneco, modelo);
    }

    /** Leva o modelo para onde o boneco esta (sem deslizar: anda de sala em sala). */
    void sincronizar() {
        if (!usaModelo || boneco == null || !boneco.isValid()) return;
        if (modelo == null || !modelo.isValid()) {
            criarModelo(0, 1f, 0f);
            return;
        }
        Location l = boneco.getLocation();
        l.setPitch(0);
        modelo.teleport(l);
    }

    /** Contorno brilhando (no modelo 3D; o boneco invisivel nao brilha para nao aparecer a silhueta errada). */
    void brilhar(boolean sim) {
        if (modelo != null && modelo.isValid()) modelo.setGlowing(sim);
        if (boneco != null && boneco.isValid()) boneco.setGlowing(sim);
    }

    /** Susto: modelo no quadro do grito, maior (escalaBoneco e o tamanho do boneco, o normal e 1,25). */
    void susto(double escalaBoneco) {
        if (!usaModelo) return;
        // no susto a cara fica na altura dos olhos de quem esta na cadeira (o corpo afunda no chao, nao aparece)
        criarModelo(1, (float) (escalaBoneco / 1.25), -0.8f);
        if (modelo != null) modelo.setGlowing(true);
    }

    void mover(Mapa mapa, String lugar) {
        if (boneco == null || !boneco.isValid()) return;
        double[] p = Mapa.LUGARES.get(lugar);
        if (p == null) return;
        boneco.teleport(mapa.loc(p));
        sincronizar();
    }

    void olharPara(Location alvo) {
        if (boneco == null || !boneco.isValid()) return;
        Location l = boneco.getLocation();
        double dx = alvo.getX() - l.getX(), dz = alvo.getZ() - l.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        boneco.setRotation(yaw, 0);
        if (modelo != null && modelo.isValid()) modelo.setRotation(yaw, 0);
    }

    void sumir() {
        if (modelo != null && modelo.isValid()) modelo.remove();
        modelo = null;
        if (boneco != null && boneco.isValid()) boneco.remove();
        boneco = null;
    }
}
