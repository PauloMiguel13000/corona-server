package br.servidor.extras;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Estatuas dos monstros 3D ao lado dos portoes do Lobby do Terror (09/11): a Criatura da Floresta, a Vovo e dois
 * animatronicos do Turno da Noite, do mesmo modelo que aparece dentro do jogo. Sao ItemDisplay que nao ficam salvos:
 * nascem quando chega alguem e somem quando o mundo esvazia. So aparecem para quem tem o pacote de recursos (ou esta
 * no Bedrock); quem recusou o pacote nao ve nada (em vez de ver um capacete de couro flutuando).
 */
final class Estatuas implements Listener {

    /** Mesmo id que o CoronaPack usa ao mandar o pacote. */
    private static final UUID PACOTE = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(StandardCharsets.UTF_8));

    /** jogo = id do portao; modelo = item corona:props/...; lado = -1 a esquerda de quem olha o portao, +1 a direita. */
    record Estatua(String jogo, String modelo, int lado, float escala) {}

    static final Estatua[] ESTATUAS = {
            new Estatua("slender", "porcelana_f0", -1, 1.5f),
            new Estatua("vovo", "vovo_idle", 1, 1.4f),
            new Estatua("turno", "anim_capi_f0", -1, 1.3f),
            new Estatua("turno", "anim_jaca_f0", 1, 1.3f)};

    private final Extras pl;
    private final LobbyTerror lobby;
    private final NamespacedKey marca;
    private final Set<UUID> comPacote = ConcurrentHashMap.newKeySet();
    private final List<ItemDisplay> vivas = new ArrayList<>();

    Estatuas(Extras pl, LobbyTerror lobby) {
        this.pl = pl;
        this.lobby = lobby;
        this.marca = new NamespacedKey(pl, "terror_estatua");
    }

    void iniciar() {
        if (!pl.getConfig().getBoolean("lobby-terror.estatuas", true)) return;
        Bukkit.getPluginManager().registerEvents(this, pl);
        Bukkit.getScheduler().runTaskTimer(pl, this::cuidar, 100L, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void pacote(PlayerResourcePackStatusEvent e) {
        if (!PACOTE.equals(e.getID())) return;
        UUID id = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> comPacote.add(id);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> comPacote.remove(id);
            default -> { }
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        comPacote.remove(e.getPlayer().getUniqueId());
    }

    private boolean ve(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || comPacote.contains(p.getUniqueId());
    }

    /** A cada 2 s: com gente no lobby, garante as estatuas e mostra so para quem tem o pacote; vazio, tira tudo. */
    private void cuidar() {
        World w = lobby.mundo;
        if (w == null) return;
        List<Player> gente = w.getPlayers();
        if (gente.isEmpty()) {
            for (ItemDisplay d : vivas) if (d.isValid()) d.remove();
            vivas.clear();
            return;
        }
        vivas.removeIf(d -> !d.isValid());
        if (vivas.size() < ESTATUAS.length) {
            for (ItemDisplay d : vivas) d.remove();
            vivas.clear();
            for (Estatua e : ESTATUAS) {
                ItemDisplay d = nascer(w, e);
                if (d != null) vivas.add(d);
            }
        }
        for (Player p : gente) {
            boolean sim = ve(p);
            for (ItemDisplay d : vivas) {
                if (sim == p.canSee(d)) continue;
                if (sim) p.showEntity(pl, d);
                else p.hideEntity(pl, d);
            }
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private ItemDisplay nascer(World w, Estatua e) {
        LobbyMinigames.Portal portao = null;
        for (LobbyMinigames.Portal p : LobbyTerror.portoes()) if (p.id().equals(e.jogo())) portao = p;
        if (portao == null) return null;
        int vg = LobbyTerror.vaga(portao);
        int cx = LobbyTerror.cxVaga(vg), cz = LobbyTerror.czVaga(vg);
        double a = Math.atan2(cz, cx);
        // ao lado do portao (uns 4,5 blocos na tangente) e 2,5 para dentro, de frente para o centro; se ali tiver
        // arvore, lapide ou poste, tenta um pouco mais para o lado ou para dentro ate achar chao livre
        Location l = null;
        busca:
        for (double dentro : new double[] {2.5, 3.5, 1.5, 4.5})
            for (double lado : new double[] {4.5, 5.5, 3.5, 6.5}) {
                double x = cx + 0.5 - Math.sin(a) * lado * e.lado() - Math.cos(a) * dentro;
                double z = cz + 0.5 + Math.cos(a) * lado * e.lado() - Math.sin(a) * dentro;
                int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
                for (int y = LobbyTerror.Y + 1; y >= LobbyTerror.Y - 1; y--) {
                    if (!w.getBlockAt(bx, y, bz).getType().isSolid()) continue;
                    boolean livre = true;
                    for (int dy = 1; dy <= 4 && livre; dy++)
                        for (int dx = -1; dx <= 1 && livre; dx++)
                            for (int dz = -1; dz <= 1 && livre; dz++)
                                if (w.getBlockAt(bx + dx, y + dy, bz + dz).getType().isSolid()) livre = false;
                    if (livre) {
                        l = new Location(w, bx + 0.5, y + 1, bz + 0.5);
                        break busca;
                    }
                    break;
                }
            }
        if (l == null) return null; // sem lugar livre perto do portao
        l.setYaw((float) Math.toDegrees(Math.atan2(-(0.5 - l.getX()), 0.5 - l.getZ())));
        float esc = e.escala();
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/" + e.modelo()));
        Location lugar = l;
        return w.spawn(lugar, ItemDisplay.class, d -> {
            d.setItemStack(it);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, 1.5f * esc, 0), new Quaternionf(), new Vector3f(esc, esc, esc), new Quaternionf()));
            d.setBrightness(new Display.Brightness(10, 10));
            d.setViewRange(1.6f);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        });
    }
}
