package br.servidor.extras;

import io.netty.buffer.Unpooled;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Matrix4f;

/**
 * Asas de hussardo do Traje do Rei (peitoral do MVP/LENDA) para quem joga no Java.
 *
 * Sao dois ItemDisplay (modelo corona:item/coroa/asa do pacote) que existem SO no cliente de quem esta vendo:
 * o servidor manda os pacotes de criar, montar no jogador e girar. Assim o jogador nao fica com "passageiros" de
 * verdade (com passageiro o teleporte entre mundos falha) e nada fica salvo no mapa.
 * O cliente encaixa as asas no jogador a cada quadro (montaria), e o plugin so gira junto com o corpo e troca a
 * pose (em pe / agachado / escondida). Quem joga no Bedrock ve as asas pelo proprio pacote (attachable do
 * peitoral), entao nao recebe estes pacotes. Medidas: fontes/modelos/asas_hussardo.py (matriz_display).
 */
final class AsasRei implements Listener {
    private static final int EM_PE = 0, AGACHADO = 1, ESCONDIDA = 2;
    // medidas em pixels do modelo do jogador (o jogador e desenhado em 0.9375)
    private static final float ESC = 0.9375f;
    private static final float PX = ESC / 16f;
    private static final float RAIZ_X = 5.0f, RAIZ_Y = 20.0f, RAIZ_Z = -3.3f;
    private static final float ABERTURA = 45f;

    private final Extras pl;
    private final TrajeRei traje;
    private final ItemStack item;
    private final Set<UUID> comPacote = new HashSet<>();
    private final Map<UUID, Estado> estados = new HashMap<>();
    private boolean falhou;
    /** id da entidade do dono -> ids das duas asas (lido na thread da rede pelo filtro de passageiros). */
    private final Map<Integer, int[]> montadas = new java.util.concurrent.ConcurrentHashMap<>();
    private int ticks;
    private static final String FILTRO = "corona_asas_passageiros";

    private static final class Estado {
        final int[] ids = new int[2];
        final UUID[] uuids = {UUID.randomUUID(), UUID.randomUUID()};
        final ItemDisplay[] modelo = new ItemDisplay[2];
        final Map<UUID, Object> vistos = new HashMap<>();   // quem ja tem as asas no cliente -> "handle" dele
        Object dono;
        int pose = -1;
        byte giro;
        int passageiros = -1;
    }

    AsasRei(Extras pl, TrajeRei traje) {
        this.pl = pl;
        this.traje = traje;
        this.item = new ItemStack(Material.PAPER);
        ItemMeta m = item.getItemMeta();
        m.setItemModel(new NamespacedKey("corona", "item/coroa/asa"));
        item.setItemMeta(m);
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 40L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) filtrar(p);
    }

    // ------------------------------------------------------------------ filtro de passageiros

    /**
     * Outro plugin (pet que monta, etc.) ou o proprio servidor manda a lista de passageiros do jogador sem as asas:
     * no cliente as asas desmontavam e ficavam paradas no lugar (longe do jogador). O filtro poe as asas de volta
     * em toda lista de passageiros do dono que sai para o cliente.
     */
    private void filtrar(Player v) {
        if (bedrock(v)) return;
        try {
            io.netty.channel.Channel ch = ((CraftPlayer) v).getHandle().connection.connection.channel;
            ch.eventLoop().execute(() -> {
                try {
                    io.netty.channel.ChannelPipeline pp = ch.pipeline();
                    if (pp.get(FILTRO) != null) return;
                    if (pp.get("packet_handler") != null) pp.addBefore("packet_handler", FILTRO, new Filtro());
                    else pp.addFirst(FILTRO, new Filtro());   // jogador falso de teste (sem codificador)
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void tirarFiltro(Player v) {
        try {
            io.netty.channel.Channel ch = ((CraftPlayer) v).getHandle().connection.connection.channel;
            ch.eventLoop().execute(() -> {
                try {
                    if (ch.pipeline().get(FILTRO) != null) ch.pipeline().remove(FILTRO);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private final class Filtro extends io.netty.channel.ChannelOutboundHandlerAdapter {
        @Override
        public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) throws Exception {
            Object novo = msg;
            try {
                novo = ajustar(msg);
            } catch (Throwable ignored) {
            }
            super.write(ctx, novo, promise);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object ajustar(Object msg) {
        if (montadas.isEmpty()) return msg;
        if (msg instanceof ClientboundSetPassengersPacket sp) {
            int[] asas = montadas.get(sp.getVehicle());
            if (asas == null) return msg;
            int[] atuais = sp.getPassengers();
            for (int id : atuais) if (id == asas[0]) return msg;   // ja tem as asas (pacote nosso)
            int[] ids = java.util.Arrays.copyOf(atuais, atuais.length + 2);
            ids[atuais.length] = asas[0];
            ids[atuais.length + 1] = asas[1];
            return pacotePassageiros(sp.getVehicle(), ids);
        }
        if (msg instanceof ClientboundBundlePacket b) {
            List lista = new ArrayList<>();
            boolean mudou = false;
            for (Object sub : b.subPackets()) {
                Object n = ajustar(sub);
                if (n != sub) mudou = true;
                lista.add(n);
            }
            return mudou ? new ClientboundBundlePacket(lista) : msg;
        }
        return msg;
    }

    private static ClientboundSetPassengersPacket pacotePassageiros(int veiculo, int[] ids) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(veiculo);
            buf.writeVarIntArray(ids);
            return ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    // ------------------------------------------------------------------ quem ve

    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    /** Java com o pacote carregado (sem o pacote o modelo da asa nao existe). */
    private boolean podeVer(Player v, Player dono) {
        return v.isOnline() && !bedrock(v) && comPacote.contains(v.getUniqueId()) && v.getWorld() == dono.getWorld();
    }

    private int pose(Player p) {
        if (p.isDead() || p.isInvisible() || p.getGameMode() == GameMode.SPECTATOR || p.isGliding() || p.isSwimming() || p.isSleeping())
            return ESCONDIDA;
        Pose ps = p.getPose();
        if (ps == Pose.SNEAKING) return AGACHADO;
        if (ps != Pose.STANDING) return ESCONDIDA;
        return EM_PE;
    }

    // ------------------------------------------------------------------ medidas

    /**
     * Matriz do display (lado 0 = asa direita, 1 = esquerda). O ponto de montaria do jogador fica no alto da caixa
     * (1.8 em pe, 1.5 agachado) e o ItemDisplay ja gira o modelo 180 graus em y depois desta matriz.
     * Agachado: o tronco inclina 0.5 rad para a frente em volta do pescoco (que desce 3.2 px e o corpo todo 0.125).
     */
    static Matrix4f matriz(int lado, int pose) {
        Matrix4f m = new Matrix4f();
        if (pose == ESCONDIDA) return m.scale(0.0001f);
        float rx = lado == 0 ? -RAIZ_X : RAIZ_X;
        float beta = lado == 0 ? 180f - ABERTURA : ABERTURA;
        float giro = (float) Math.toRadians(beta - 180f);
        if (pose == EM_PE) {
            m.translate(rx * PX, RAIZ_Y * PX - 1.8f, RAIZ_Z * PX);
        } else {
            m.translate(0f, (24f - 3.2f) * PX - 0.125f - 1.5f, 0f);
            m.rotateX(0.5f);
            m.translate(rx * PX, (RAIZ_Y - 24f) * PX, RAIZ_Z * PX);
        }
        m.rotateY(giro);
        m.scale(ESC);
        return m;
    }

    private ItemDisplay criarModelo(Player p, int lado) {
        Location l = p.getLocation();
        ItemDisplay d = p.getWorld().createEntity(l, ItemDisplay.class);
        d.setItemStack(item);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        d.setBillboard(Display.Billboard.FIXED);
        d.setTeleportDuration(2);
        d.setInterpolationDuration(3);
        d.setShadowRadius(0f);
        d.setViewRange(1.0f);
        d.setTransformationMatrix(matriz(lado, EM_PE));
        return d;
    }

    // ------------------------------------------------------------------ pacotes

    private static void mandar(Player v, Packet<?> pk) {
        ((CraftPlayer) v).getHandle().connection.send(pk);
    }

    private static Object handle(Player p) {
        return ((CraftPlayer) p).getHandle();
    }

    private static SynchedEntityData dados(ItemDisplay d) {
        return ((CraftEntity) d).getHandle().getEntityData();
    }

    private static byte angulo(float graus) {
        return (byte) Math.floor(graus * 256.0f / 360.0f);
    }

    private ClientboundSetPassengersPacket montar(Player dono, Estado e) {
        net.minecraft.world.entity.Entity h = ((CraftPlayer) dono).getHandle();
        List<net.minecraft.world.entity.Entity> reais = h.getPassengers();
        int[] ids = new int[reais.size() + 2];
        for (int i = 0; i < reais.size(); i++) ids[i] = reais.get(i).getId();
        ids[reais.size()] = e.ids[0];
        ids[reais.size() + 1] = e.ids[1];
        return pacotePassageiros(h.getId(), ids);
    }

    private Packet<?> criarPacote(Player dono, Estado e) {
        Location l = dono.getLocation();
        float yaw = dono.getBodyYaw();
        double y = l.getY() + dono.getHeight();
        List<Packet<? super ClientGamePacketListener>> lista = new ArrayList<>();
        lista.add(new ClientboundRemoveEntitiesPacket(e.ids[0], e.ids[1]));
        for (int k = 0; k < 2; k++) {
            lista.add(new ClientboundAddEntityPacket(e.ids[k], e.uuids[k], l.getX(), y, l.getZ(), 0f, yaw, ((CraftEntity) e.modelo[k]).getHandle().getType(), 0, Vec3.ZERO, yaw));
            List<SynchedEntityData.DataValue<?>> v = dados(e.modelo[k]).getNonDefaultValues();
            if (v != null && !v.isEmpty()) lista.add(new ClientboundSetEntityDataPacket(e.ids[k], v));
        }
        lista.add(montar(dono, e));
        return new ClientboundBundlePacket(lista);
    }

    // ------------------------------------------------------------------ laco

    private void tick() {
        if (falhou) return;
        ticks++;
        try {
            for (Player dono : Bukkit.getOnlinePlayers()) {
                Estado e = estados.get(dono.getUniqueId());
                if (!traje.vestePeitoral(dono)) {
                    if (e != null) apagar(dono.getUniqueId());
                    continue;
                }
                if (e == null) {
                    e = new Estado();
                    for (int k = 0; k < 2; k++) {
                        e.ids[k] = Bukkit.getUnsafe().nextEntityId(dono.getWorld());
                        e.modelo[k] = criarModelo(dono, k);
                    }
                    estados.put(dono.getUniqueId(), e);
                }
                atualizar(dono, e);
            }
            estados.keySet().removeIf(id -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) return false;
                Estado e = estados.get(id);
                if (e != null) montadas.values().removeIf(v -> v[0] == e.ids[0]);
                tirarDeTodos(e);
                return true;
            });
        } catch (Throwable ex) {
            falhou = true;
            pl.getLogger().warning("Asas do Traje do Rei desligadas (erro): " + ex);
            ex.printStackTrace();
        }
    }

    private void atualizar(Player dono, Estado e) {
        Object h = handle(dono);
        if (e.dono != h) {          // renasceu: e outro jogador no servidor, manda tudo de novo
            tirarDeTodos(e);
            e.dono = h;
        }
        // pose (matriz) mudou?
        int pose = pose(dono);
        List<Packet<?>> dadosNovos = null;
        if (pose != e.pose) {
            e.pose = pose;
            dadosNovos = new ArrayList<>();
            for (int k = 0; k < 2; k++) {
                ItemDisplay d = e.modelo[k];
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(pose == ESCONDIDA ? 0 : 3);
                d.setTransformationMatrix(matriz(k, pose));
                List<SynchedEntityData.DataValue<?>> v = dados(d).packDirty();
                if (v != null && !v.isEmpty()) dadosNovos.add(new ClientboundSetEntityDataPacket(e.ids[k], v));
            }
        }
        byte giro = angulo(dono.getBodyYaw());
        boolean girou = giro != e.giro;
        e.giro = giro;
        int passageiros = dono.getPassengers().size();
        boolean remontar = (passageiros != e.passageiros && e.passageiros >= 0) || ticks % 40 == 0;
        e.passageiros = passageiros;
        montadas.put(((CraftPlayer) dono).getHandle().getId(), new int[] {e.ids[0], e.ids[1]});

        // quem deve ver
        Set<Player> querem = new HashSet<>();
        for (Player v : dono.getTrackedBy()) if (podeVer(v, dono)) querem.add(v);
        if (podeVer(dono, dono)) querem.add(dono);
        Iterator<Map.Entry<UUID, Object>> it = e.vistos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Object> en = it.next();
            Player v = Bukkit.getPlayer(en.getKey());
            if (v != null && querem.contains(v) && handle(v) == en.getValue()) continue;
            if (v != null) mandar(v, new ClientboundRemoveEntitiesPacket(e.ids[0], e.ids[1]));
            it.remove();
        }
        Packet<?> rot0 = null, rot1 = null, monta = null;
        for (Player v : querem) {
            if (!e.vistos.containsKey(v.getUniqueId())) {
                mandar(v, criarPacote(dono, e));
                e.vistos.put(v.getUniqueId(), handle(v));
                continue;
            }
            if (dadosNovos != null) for (Packet<?> pk : dadosNovos) mandar(v, pk);
            if (girou) {
                if (rot0 == null) {
                    rot0 = new ClientboundMoveEntityPacket.Rot(e.ids[0], giro, (byte) 0, false);
                    rot1 = new ClientboundMoveEntityPacket.Rot(e.ids[1], giro, (byte) 0, false);
                }
                mandar(v, rot0);
                mandar(v, rot1);
            }
            if (remontar) {
                if (monta == null) monta = montar(dono, e);
                mandar(v, monta);
            }
        }
    }

    private void tirarDeTodos(Estado e) {
        if (e == null) return;
        for (UUID id : e.vistos.keySet()) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && v.isOnline()) mandar(v, new ClientboundRemoveEntitiesPacket(e.ids[0], e.ids[1]));
        }
        e.vistos.clear();
    }

    private void apagar(UUID dono) {
        Estado e = estados.remove(dono);
        if (e != null) montadas.values().removeIf(v -> v[0] == e.ids[0]);
        tirarDeTodos(e);
    }

    void parar() {
        for (Estado e : estados.values()) tirarDeTodos(e);
        estados.clear();
        montadas.clear();
        for (Player p : Bukkit.getOnlinePlayers()) tirarFiltro(p);
    }

    /** Para o comando de admin: quantos estao com asas e para quantos jogadores elas aparecem. */
    String resumo() {
        int vistas = 0;
        for (Estado e : estados.values()) vistas += e.vistos.size();
        return estados.size() + " com asas, " + vistas + " visualizacao(oes), " + comPacote.size() + " no Java com pacote" + (falhou ? " (DESLIGADO por erro)" : "");
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler
    public void pacote(PlayerResourcePackStatusEvent e) {
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> comPacote.add(e.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> comPacote.remove(e.getPlayer().getUniqueId());
            default -> {
            }
        }
    }

    /** O cliente deixou de ver o dono das asas: as asas somem junto. */
    @EventHandler
    public void deixouDeVer(PlayerUntrackEntityEvent e) {
        Estado st = estados.get(e.getEntity().getUniqueId());
        if (st == null) return;
        if (st.vistos.remove(e.getPlayer().getUniqueId()) != null)
            mandar(e.getPlayer(), new ClientboundRemoveEntitiesPacket(st.ids[0], st.ids[1]));
    }

    /** Mundo novo no cliente: tudo que ele tinha sumiu; manda de novo no proximo tick. */
    @EventHandler
    public void mudouMundo(PlayerChangedWorldEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        for (Estado st : estados.values()) st.vistos.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void renasceu(PlayerRespawnEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        for (Estado st : estados.values()) st.vistos.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morreu(PlayerDeathEvent e) {
        apagar(e.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entrou(org.bukkit.event.player.PlayerJoinEvent e) {
        filtrar(e.getPlayer());
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        comPacote.remove(id);
        apagar(id);
        for (Estado st : estados.values()) st.vistos.remove(id);
    }
}
