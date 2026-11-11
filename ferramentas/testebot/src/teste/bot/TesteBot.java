package teste.bot;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/** SO PARA TESTE: cria jogadores falsos para disparar os poderes sem um cliente de verdade. */
public final class TesteBot extends JavaPlugin implements org.bukkit.event.Listener {

    private final Map<String, ServerPlayer> falsos = new HashMap<>();
    private final Map<String, EmbeddedChannel> canais = new HashMap<>();
    private final java.util.Set<String> ouvindo = new java.util.HashSet<>();

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void verDano(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Zombie z)) return;
        String quem = e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent b ? b.getDamager().getType().name() : "-";
        log("DANO zumbi causa=" + e.getCause() + " por=" + quem + " dano=" + String.format("%.2f", e.getDamage())
                + " final=" + String.format("%.2f", e.getFinalDamage()) + " cancelado=" + e.isCancelled()
                + " vida=" + String.format("%.1f", z.getHealth()) + " invul=" + z.getNoDamageTicks());
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void verInteracao(PlayerInteractEvent e) {
        log("INTERACT acao=" + e.getAction() + " mao=" + e.getHand() + " bloco=" + (e.getClickedBlock() == null ? "-" : e.getClickedBlock().getType() + "@" + e.getClickedBlock().getY()));
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void verProjetil(org.bukkit.event.entity.ProjectileHitEvent e) {
        log("PROJETIL " + e.getEntity().getType() + " acertou entidade=" + (e.getHitEntity() == null ? "-" : e.getHitEntity().getType())
                + " bloco=" + (e.getHitBlock() == null ? "-" : e.getHitBlock().getType()) + " em " + fmt(e.getEntity().getLocation()) + " cancelado=" + e.isCancelled());
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void verLancamento(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        log("LANCOU " + e.getEntity().getType() + " em " + fmt(e.getEntity().getLocation()) + " vel=" + e.getEntity().getVelocity() + " cancelado=" + e.isCancelled());
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Map.Entry<String, EmbeddedChannel> e : canais.entrySet()) {
                EmbeddedChannel c = e.getValue();
                try {
                    c.runPendingTasks();
                    if (ouvindo.contains(e.getKey())) {
                        Object m;
                        while ((m = c.readOutbound()) != null) {
                            String n = m.getClass().getSimpleName();
                            if (n.contains("SystemChat") || n.contains("DisguisedChat")) {
                                String txt = "?";
                                try {
                                    Object comp = m.getClass().getMethod("content").invoke(m);
                                    txt = String.valueOf(comp.getClass().getMethod("getString").invoke(comp));
                                } catch (Throwable t) {
                                    txt = String.valueOf(m);
                                }
                                log("CHAT " + e.getKey() + " | " + txt);
                            } else if (n.contains("Sound")) {
                                String som = "?";
                                try {
                                    Object h = m.getClass().getMethod("getSound").invoke(m);
                                    Object v = h.getClass().getMethod("value").invoke(h);
                                    som = String.valueOf(v);
                                } catch (Throwable t) {
                                    som = String.valueOf(m);
                                }
                                log("SOM " + e.getKey() + " " + n + " " + som);
                            }
                            io.netty.util.ReferenceCountUtil.release(m);
                        }
                    } else c.releaseOutbound();
                } catch (Throwable ignored) {
                    // so descartando pacotes
                }
                ServerPlayer sp = falsos.get(e.getKey());
                if (sp != null && sp.connection != null) manterVivo(sp.connection);
            }
        }, 1L, 1L);
    }

    private static void manterVivo(ServerCommonPacketListenerImpl l) {
        try {
            Field f = ServerCommonPacketListenerImpl.class.getDeclaredField("keepAlive");
            f.setAccessible(true);
            io.papermc.paper.util.KeepAlive k = (io.papermc.paper.util.KeepAlive) f.get(l);
            k.pendingKeepAlives.clear();
            k.lastKeepAliveTx = System.nanoTime();
        } catch (Throwable ignored) {
            // sem keepalive
        }
    }

    @Override
    public void onDisable() {
        for (String n : falsos.keySet().toArray(new String[0])) sair(n);
    }

    private Player entrar(String nome, Location onde) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) onde.getWorld()).getHandle();
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nome).getBytes());
        GameProfile perfil = new GameProfile(id, nome);
        ServerPlayer sp = new ServerPlayer(server, level, perfil, ClientInformation.createDefault());
        Connection con = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel canal = new EmbeddedChannel();
        con.channel = canal;
        con.address = new InetSocketAddress("127.0.0.1", 50000 + falsos.size());
        sp.setPos(onde.getX(), onde.getY(), onde.getZ());
        server.getPlayerList().placeNewPlayer(con, sp, CommonListenerCookie.createInitial(perfil, false));
        falsos.put(nome.toLowerCase(), sp);
        canais.put(nome.toLowerCase(), canal);
        Player p = sp.getBukkitEntity();
        p.teleport(onde);
        return p;
    }

    private void sair(String nome) {
        ServerPlayer sp = falsos.remove(nome.toLowerCase());
        canais.remove(nome.toLowerCase());
        if (sp == null) return;
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        server.getPlayerList().remove(sp);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length < 2) {
            s.sendMessage("fake entrar|sair|cmd|usar|trocar|olhar|clicar|info|vazio <nome> ...");
            return true;
        }
        String sub = a[0];
        String nome = a[1];
        if (sub.equals("entrar")) {
            World w = Bukkit.getWorlds().get(0);
            Location l = a.length >= 5
                    ? new Location(w, Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]))
                    : w.getSpawnLocation();
            Player p = entrar(nome, l);
            log("ENTROU " + p.getName() + " em " + fmt(p.getLocation()));
            return true;
        }
        if (sub.equals("sair")) {
            sair(nome);
            log("SAIU " + nome);
            return true;
        }
        Player p = Bukkit.getPlayerExact(nome);
        if (p == null) {
            log("NAO ACHEI " + nome);
            return true;
        }
        switch (sub) {
            case "cmd" -> {
                String c = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                boolean ok = p.performCommand(c);
                log("CMD /" + c + " -> " + ok);
            }
            case "usar" -> {
                p.getInventory().setItemInMainHand(null);
                p.setSneaking(true);
                PlayerInteractEvent ev = new PlayerInteractEvent(p, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF, EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(ev);
                p.setSneaking(false);
                log("USAR feito");
            }
            case "trocar" -> {
                p.getInventory().setItemInMainHand(null);
                p.setSneaking(true);
                PlayerSwapHandItemsEvent ev = new PlayerSwapHandItemsEvent(p, new ItemStack(org.bukkit.Material.AIR), new ItemStack(org.bukkit.Material.AIR));
                Bukkit.getPluginManager().callEvent(ev);
                p.setSneaking(false);
                log("TROCAR cancelado=" + ev.isCancelled());
            }
            case "olhar" -> {
                Location alvo = new Location(p.getWorld(), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]));
                Location l = p.getLocation();
                l.setDirection(alvo.toVector().subtract(p.getEyeLocation().toVector()));
                p.teleport(l);
                log("OLHANDO yaw=" + (int) l.getYaw() + " pitch=" + (int) l.getPitch());
            }
            case "clicar" -> {
                InventoryView v = p.getOpenInventory();
                int slot = Integer.parseInt(a[2]);
                InventoryClickEvent ev = new InventoryClickEvent(v, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                Bukkit.getPluginManager().callEvent(ev);
                log("CLIQUE slot " + slot + " em '" + titulo(v) + "' cancelado=" + ev.isCancelled());
            }
            case "info" -> {
                InventoryView v = p.getOpenInventory();
                StringBuilder ef = new StringBuilder();
                for (PotionEffect e : p.getActivePotionEffects()) ef.append(e.getType().getKey().getKey()).append(e.getAmplifier()).append(' ');
                String tab = p.playerListName() == null ? "-" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(p.playerListName());
                log("INFO " + p.getName() + " pos=" + fmt(p.getLocation()) + " vida=" + p.getHealth() + " janela='" + titulo(v)
                        + "' tab='" + tab + "' efeitos=[" + ef + "] voando=" + p.isFlying());
                if (v.getTopInventory().getSize() > 5) {
                    StringBuilder it = new StringBuilder();
                    for (int i = 0; i < v.getTopInventory().getSize(); i++) {
                        ItemStack is = v.getTopInventory().getItem(i);
                        if (is != null && !is.getType().name().contains("GLASS_PANE")) it.append(i).append('=').append(is.getType().name()).append(' ');
                    }
                    log("ITENS " + it);
                }
            }
            case "cliquebloco" -> {
                // simula o pacote real que o cliente manda ao clicar com o botao direito no TOPO de um bloco
                ServerPlayer sp = falsos.get(nome.toLowerCase());
                try {
                    // o cliente de verdade confirma o teleporte na hora; o falso nao, entao limpamos a espera
                    Field f = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
                    f.setAccessible(true);
                    log("esperando posicao antes: " + f.get(sp.connection));
                    f.set(sp.connection, null);
                } catch (Throwable t) {
                    log("sem campo awaitingPositionFromClient: " + t);
                }
                int x = Integer.parseInt(a[2]), y = Integer.parseInt(a[3]), z = Integer.parseInt(a[4]);
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                        new net.minecraft.world.phys.Vec3(x + 0.5, y + 1.0, z + 0.5), net.minecraft.core.Direction.UP, pos, false);
                sp.connection.handleUseItemOn(new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                        net.minecraft.world.InteractionHand.MAIN_HAND, hit, 1));
                log("CLIQUEBLOCO " + x + " " + y + " " + z + " mao=" + p.getInventory().getItemInMainHand().getType());
            }
            case "cliquear" -> {
                // clique no ar com o item que estiver na mao (direito ou esquerdo)
                Action ac = a.length > 2 && a[2].equals("esquerdo") ? Action.LEFT_CLICK_AIR : Action.RIGHT_CLICK_AIR;
                ItemStack mao = p.getInventory().getItemInMainHand();
                PlayerInteractEvent ev = new PlayerInteractEvent(p, ac, mao.getType().isAir() ? null : mao, null, BlockFace.SELF, EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(ev);
                log("CLIQUEAR " + ac + " com " + mao.getType() + " cancelado=" + ev.isCancelled() + " mao depois=" + p.getInventory().getItemInMainHand().getType());
            }
            case "mao" -> log("MAO slot=" + p.getInventory().getHeldItemSlot() + " item=" + p.getInventory().getItemInMainHand().getType()
                    + " nome=" + (p.getInventory().getItemInMainHand().hasItemMeta() ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(p.getInventory().getItemInMainHand().getItemMeta().displayName()) : "-"));
            case "inv" -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 36; i++) {
                    ItemStack is = p.getInventory().getItem(i);
                    if (is != null && !is.getType().isAir()) sb.append(i).append('=').append(is.getType().name()).append(' ');
                }
                log("INV " + sb);
            }
            case "tirar" -> {
                int slot = Integer.parseInt(a[2]);
                p.getInventory().setItem(slot, null);
                log("TIROU slot " + slot);
            }
            case "segurar" -> {
                p.getInventory().setHeldItemSlot(Integer.parseInt(a[2]));
                log("SEGURANDO slot " + a[2] + " = " + p.getInventory().getItemInMainHand().getType());
            }
            case "interagir", "bater" -> {
                // clique direito (interagir) ou soco (bater) na entidade mais perto (ate 6 blocos)
                org.bukkit.entity.Entity perto = null;
                double melhor = 37;
                for (org.bukkit.entity.Entity en : p.getNearbyEntities(6, 6, 6)) {
                    if (en instanceof Player) continue;
                    double d = en.getLocation().distanceSquared(p.getLocation());
                    if (d < melhor) {
                        melhor = d;
                        perto = en;
                    }
                }
                if (perto == null) {
                    log("NENHUMA ENTIDADE PERTO");
                } else if (sub.equals("interagir")) {
                    org.bukkit.event.player.PlayerInteractEntityEvent ev = new org.bukkit.event.player.PlayerInteractEntityEvent(p, perto, EquipmentSlot.HAND);
                    Bukkit.getPluginManager().callEvent(ev);
                    log("INTERAGIR " + perto.getType() + " cancelado=" + ev.isCancelled() + " existe=" + perto.isValid());
                } else {
                    p.attack(perto);
                    log("BATER " + perto.getType() + " existe=" + perto.isValid());
                }
            }
            case "ir" -> {
                Location l = new Location(p.getWorld(), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]));
                p.teleport(l);
                log("FOI para " + fmt(p.getLocation()));
            }
            case "andar" -> {
                Location de = p.getLocation();
                Location para = new Location(p.getWorld(), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]), de.getYaw(), de.getPitch());
                org.bukkit.event.player.PlayerMoveEvent ev = new org.bukkit.event.player.PlayerMoveEvent(p, de, para);
                Bukkit.getPluginManager().callEvent(ev);
                if (!ev.isCancelled() && p.getLocation().equals(de)) p.teleport(para);
                log("ANDOU para " + fmt(p.getLocation()) + " cancelado=" + ev.isCancelled());
            }
            case "dar" -> {
                p.getInventory().addItem(new ItemStack(org.bukkit.Material.valueOf(a[2]), Integer.parseInt(a.length > 3 ? a[3] : "1")));
                log("DEU " + a[2]);
            }
            case "sons" -> {
                if (a.length > 2 && a[2].equals("off")) ouvindo.remove(nome.toLowerCase());
                else ouvindo.add(nome.toLowerCase());
                log("SONS " + nome + " " + ouvindo.contains(nome.toLowerCase()));
            }
            case "pacote" -> {
                UUID id = UUID.nameUUIDFromBytes("corona-server-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                org.bukkit.event.player.PlayerResourcePackStatusEvent.Status st = a.length > 2
                        ? org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.valueOf(a[2])
                        : org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerResourcePackStatusEvent(p, id, st));
                log("PACOTE " + nome + " " + st);
            }
            case "vazio" -> {
                p.getInventory().clear();
                log("INVENTARIO limpo");
            }
            default -> log("?");
        }
        return true;
    }

    private static String titulo(InventoryView v) {
        try {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(v.title());
        } catch (Throwable t) {
            return v.getType().name();
        }
    }

    private void log(String m) {
        getLogger().info("[BOT] " + m);
    }

    private static String fmt(Location l) {
        return String.format("%s %.1f %.1f %.1f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ());
    }
}
