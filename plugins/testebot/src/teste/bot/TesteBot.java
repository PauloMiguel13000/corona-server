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

    /** Diagnostico: em que prioridade o dano entre jogadores falsos foi cancelado. */
    public static final class DiagDano implements org.bukkit.event.Listener {
        private static void ver(String pri, org.bukkit.event.entity.EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof Player v)) return;
            org.bukkit.entity.Entity a = e.getDamager();
            if (a instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity sh) a = sh;
            if (!(a instanceof Player at)) return;
            Bukkit.getLogger().info("[BOT] DANO " + pri + " " + at.getName() + "->" + v.getName() + " dano=" + e.getDamage() + " final=" + e.getFinalDamage() + " cancelado=" + e.isCancelled());
        }
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST) public void a(org.bukkit.event.entity.EntityDamageByEntityEvent e) { ver("LOWEST", e); }
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOW) public void b(org.bukkit.event.entity.EntityDamageByEntityEvent e) { ver("LOW", e); }
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.NORMAL) public void c(org.bukkit.event.entity.EntityDamageByEntityEvent e) { ver("NORMAL", e); }
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH) public void d(org.bukkit.event.entity.EntityDamageByEntityEvent e) { ver("HIGH", e); }
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST) public void e(org.bukkit.event.entity.EntityDamageByEntityEvent e) { ver("HIGHEST", e); }
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR) public void f(org.bukkit.event.entity.EntityDamageByEntityEvent e) { ver("MONITOR", e); }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new DiagDano(), this);
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
                            logEntidade(e.getKey(), m);
                            if (n.contains("Sound")) {
                                String som = "?";
                                try {
                                    Object h = m.getClass().getMethod("getSound").invoke(m);
                                    Object v = h.getClass().getMethod("value").invoke(h);
                                    som = String.valueOf(v);
                                } catch (Throwable t) {
                                    som = String.valueOf(m);
                                }
                                log("SOM " + e.getKey() + " " + n + " " + som);
                            } else if (n.contains("SystemChat") || n.contains("SetActionBarText") || n.contains("SetTitleText") || n.contains("SetSubtitleText")) {
                                String txt = "?";
                                try {
                                    Object comp = null;
                                    for (String g : new String[] {"content", "text", "getText"}) {
                                        try { comp = m.getClass().getMethod(g).invoke(m); break; } catch (NoSuchMethodException ignored) { }
                                    }
                                    if (comp instanceof net.minecraft.network.chat.Component c2) txt = c2.getString();
                                    else txt = String.valueOf(comp);
                                } catch (Throwable t) {
                                    txt = String.valueOf(m);
                                }
                                log("MSG " + e.getKey() + " " + n + " " + txt);
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

    /** Pacotes de entidades (criar, montar, apagar, girar, dados) que chegaram ao bot; abre os pacotes em grupo. */
    private void logEntidade(String bot, Object m) {
        try {
            if (m instanceof net.minecraft.network.protocol.BundlePacket<?> b) {
                for (Object sub : b.subPackets()) logEntidade(bot, sub);
                return;
            }
            if (m instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket a) {
                log("PKT " + bot + " ADD id=" + a.getId() + " tipo=" + a.getType().toShortString() + " pos=" + String.format("%.2f %.2f %.2f", a.getX(), a.getY(), a.getZ()) + " yaw=" + a.getYRot());
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundSetPassengersPacket sp) {
                log("PKT " + bot + " MONTAR veiculo=" + sp.getVehicle() + " passageiros=" + java.util.Arrays.toString(sp.getPassengers()));
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket tt) {
                log("PKT " + bot + " TITULO " + tt.text().getString());
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket st) {
                log("PKT " + bot + " SUBTITULO " + st.text().getString());
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket ab) {
                log("PKT " + bot + " BARRA " + ab.text().getString());
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundClearTitlesPacket) {
                log("PKT " + bot + " LIMPAR-TITULO");
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket r) {
                log("PKT " + bot + " APAGAR " + r.entityIds());
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot r) {
                log("PKT " + bot + " GIRO yaw=" + r.getYRot());
            } else if (m instanceof net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket d && d.packedItems().size() >= 3) {
                StringBuilder sb = new StringBuilder();
                for (var v : d.packedItems()) sb.append(v.id()).append('=').append(String.valueOf(v.value()).replace('\n', ' ')).append("; ");
                String t = sb.toString();
                if (t.length() > 900) t = t.substring(0, 900) + "...";
                log("PKT " + bot + " DADOS id=" + d.id() + " " + t);
            }
        } catch (Throwable t) {
            log("PKT erro " + t);
        }
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
        return entrar(nome, onde, false);
    }

    /** bedrock = UUID com a parte alta zerada, como o Floodgate (para testar o que e so do celular). */
    private Player entrar(String nome, Location onde, boolean bedrock) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) onde.getWorld()).getHandle();
        UUID id = bedrock ? new UUID(0L, 0x9000000L + Math.abs(nome.hashCode())) : UUID.nameUUIDFromBytes(("OfflinePlayer:" + nome).getBytes());
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
        if (sub.equals("entrar") || sub.equals("entrarb")) {
            World w = a.length >= 6 ? Bukkit.getWorld(a[5]) : Bukkit.getWorlds().get(0);
            Location l = a.length >= 5
                    ? new Location(w, Double.parseDouble(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]))
                    : w.getSpawnLocation();
            Player p = entrar(nome, l, sub.equals("entrarb"));
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
            case "ver" -> {
                StringBuilder sb = new StringBuilder();
                for (org.bukkit.entity.Entity e : p.getNearbyEntities(90, 40, 90)) {
                    if (e instanceof Player || !p.canSee(e)) continue;
                    String extra = "";
                    if (e instanceof org.bukkit.entity.Mannequin m) {
                        org.bukkit.attribute.AttributeInstance sc = m.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                        extra = " perfil=" + (m.getProfile() == null ? "-" : m.getProfile().name()) + " escala=" + (sc == null ? "?" : sc.getBaseValue());
                    } else if (e instanceof org.bukkit.entity.ItemDisplay d) {
                        Object mod = d.getItemStack() == null ? null : d.getItemStack().getData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL);
                        extra = " modelo=" + mod;
                    } else if (e instanceof org.bukkit.entity.LivingEntity le) {
                        extra = " invisivel=" + le.isInvisible() + " vida=" + (int) le.getHealth();
                    }
                    if (e.customName() != null) extra += " nome='" + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.customName()) + "'";
                    sb.append("\n   ").append(e.getType()).append(" ").append(fmt(e.getLocation())).append(" d=").append(String.format("%.1f", e.getLocation().distance(p.getLocation()))).append(extra);
                }
                log("VER " + p.getName() + ":" + sb);
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
            case "agachar" -> {
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerToggleSneakEvent(p, true));
                log("AGACHOU em " + fmt(p.getLocation()));
            }
            case "slot" -> {
                var ev = new org.bukkit.event.player.PlayerItemHeldEvent(p, p.getInventory().getHeldItemSlot(), Integer.parseInt(a[2]));
                Bukkit.getPluginManager().callEvent(ev);
                if (!ev.isCancelled()) p.getInventory().setHeldItemSlot(Integer.parseInt(a[2]));
                log("SLOT " + a[2] + " cancelado=" + ev.isCancelled() + " mao=" + p.getInventory().getHeldItemSlot() + " em " + fmt(p.getLocation()));
            }
            case "tecla" -> {
                // tecla <nome> pular|tras|frente : PlayerInputEvent como o cliente manda
                String t = a[2];
                org.bukkit.Input in = new org.bukkit.Input() {
                    public boolean isForward() { return t.equals("frente"); }
                    public boolean isBackward() { return t.equals("tras"); }
                    public boolean isLeft() { return false; }
                    public boolean isRight() { return false; }
                    public boolean isJump() { return t.equals("pular"); }
                    public boolean isSneak() { return t.equals("agachar"); }
                    public boolean isSprint() { return false; }
                };
                Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInputEvent(p, in));
                log("TECLA " + t + " em " + fmt(p.getLocation()));
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
                // clique direito (interagir) ou soco (bater) na entidade mais perto (ate 6 blocos); "bater <nome> <alvo>" bate no jogador alvo
                org.bukkit.entity.Entity perto = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : null;
                double melhor = perto != null ? 0 : 37;
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
            case "vulneravel" -> {
                // o jogador falso nao recebe tick da conexao, entao o spawnInvulnerableTime (60) nunca cai: zera na mao
                try {
                    Object h = p.getClass().getMethod("getHandle").invoke(p);
                    java.lang.reflect.Field fl = null;
                    for (String fn : new String[] {"spawnInvulnerableTime", "invulnerableTime"})
                        for (Class<?> c = h.getClass(); c != null && fl == null; c = c.getSuperclass())
                            try { fl = c.getDeclaredField(fn); } catch (NoSuchFieldException ignored) { }
                    if (fl == null) { log("VULNERAVEL campo nao achado"); break; }
                    fl.setAccessible(true);
                    fl.setInt(h, 0);
                    // depois de trocar de mundo o servidor espera o cliente confirmar (isChangingDimension / hasClientLoaded): o falso nunca confirma
                    java.lang.reflect.Field cd = h.getClass().getDeclaredField("isChangingDimension");
                    cd.setAccessible(true);
                    cd.setBoolean(h, false);
                    java.lang.reflect.Field cf = h.getClass().getDeclaredField("connection");
                    cf.setAccessible(true);
                    Object con = cf.get(h);
                    java.lang.reflect.Method mk = con.getClass().getDeclaredMethod("markClientLoaded");
                    mk.setAccessible(true);
                    mk.invoke(con);
                    log("VULNERAVEL " + nome + " ok");
                } catch (Throwable t) {
                    log("VULNERAVEL erro " + t);
                }
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
            case "mundo" -> {
                // cria/carrega um mundo com um gerador de plugin: fake mundo <jogador> <nome> [gerador]
                org.bukkit.WorldCreator wc = org.bukkit.WorldCreator.name(a[2]);
                if (a.length > 3) wc.generator(a[3]);
                org.bukkit.World w = Bukkit.createWorld(wc);
                log("MUNDO " + a[2] + " -> " + (w == null ? "falhou" : w.getName() + " spawn=" + fmt(w.getSpawnLocation())));
            }
            case "fatia" -> {
                // fatia vertical: fake fatia <jogador> <mundo> <x1> <x2> <y1> <y2> <z|z=...> ; mostra uma letra por bloco
                org.bukkit.World w = Bukkit.getWorld(a[2]);
                int x1 = Integer.parseInt(a[3]), x2 = Integer.parseInt(a[4]), y1 = Integer.parseInt(a[5]), y2 = Integer.parseInt(a[6]), z = Integer.parseInt(a[7]);
                StringBuilder sb = new StringBuilder("FATIA z=" + z + "\n");
                for (int yy = y2; yy >= y1; yy--) {
                    sb.append(String.format("%4d ", yy));
                    for (int x = x1; x <= x2; x++) {
                        String n = w.getBlockAt(x, yy, z).getType().name();
                        char c = n.equals("AIR") ? '.' : n.contains("STAIRS") ? '/' : n.contains("SLAB") ? '_' : n.contains("GLASS") || n.contains("BARS") ? '#'
                                : n.contains("DOOR") ? 'D' : n.contains("BED") ? 'B' : n.contains("CHEST") ? 'C' : n.contains("LANTERN") || n.contains("TORCH") || n.contains("ROD") ? '*'
                                : n.contains("LOG") || n.contains("PILLAR") ? 'I' : n.contains("PLANKS") || n.contains("WOOD") ? 'W' : n.contains("LEAVES") ? '%' : n.contains("FENCE") || n.contains("WALL") || n.contains("CHAIN") ? '|'
                                : n.contains("GRASS") ? 'g' : n.contains("DIRT") ? 'd' : n.contains("WATER") ? '~' : n.contains("PLATE") ? '=' : n.contains("GOLD") ? '$' : n.contains("QUARTZ") ? 'Q' : n.contains("BOOKSHELF") ? 'b' : 'S';
                        sb.append(c);
                    }
                    sb.append('\n');
                }
                log(sb.toString());
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
