package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Portoes do Nether e do End das masmorras (Fase 4): dois portais no lobby (lado leste) que so abrem para quem ja tem o
 * nivel de poder da 4a habilidade (classes.niveis[3] do Poderes, 45). Do outro lado, uma camara de chegada protegida
 * com o portal de volta e um Guia que leva ate a entrada de uma masmorra daquele mundo (no Nether e no End nao da para
 * andar ate elas com seguranca).
 */
final class Portoes implements Listener {

    static final int PX = 29, PZ_NETHER = -10, PZ_END = 10;   // portais no lobby (lado leste, atravessa no x)
    static final int CHEGADA_X = 0, CHEGADA_Z = 0, CHEGADA_Y_NETHER = 100, CHEGADA_Y_END = 80;

    private final Masmorras pl;
    private final MundoMasmorras mm;
    private final NamespacedKey kGuia, kEnfeite;
    private final Map<UUID, Long> espera = new HashMap<>();

    Portoes(Masmorras pl, MundoMasmorras mm) {
        this.pl = pl;
        this.mm = mm;
        kGuia = new NamespacedKey(pl, "guia_portao");
        kEnfeite = new NamespacedKey(pl, "portao_enfeite");
    }

    void iniciar() {
        if (mm.normal == null) return;
        File feito = new File(pl.getDataFolder(), "portoes-v2.txt");
        if (!feito.exists()) {
            try {
                construirLobby();
                if (mm.nether != null) construirChegada(mm.nether, CHEGADA_Y_NETHER, true);
                if (mm.end != null) construirChegada(mm.end, CHEGADA_Y_END, false);
                Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Portoes do Nether/End nao terminaram: " + ex);
                ex.printStackTrace();
            }
        }
        Bukkit.getScheduler().runTaskTimer(pl, this::garantirGuias, 120L, 200L);
    }

    /** Nivel de poder exigido (a 4a habilidade das classes). */
    int nivelExigido() {
        int padrao = pl.getConfig().getInt("portoes.nivel", -1);
        if (padrao >= 0) return padrao;
        org.bukkit.plugin.Plugin po = Bukkit.getPluginManager().getPlugin("Poderes");
        if (po != null) {
            List<Integer> l = po.getConfig().getIntegerList("classes.niveis");
            if (l.size() >= 4) return l.get(3);
        }
        return 45;
    }

    // ------------------------------------------------------------------ construcao

    private void por(World w, int x, int y, int z, String b) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false);
    }

    private void construirLobby() {
        World w = mm.normal;
        int y = GeradorMundo.LOBBY_Y;
        portal(w, PX, y, PZ_NETHER, "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:nether_portal[axis=z]", "minecraft:magma_block");
        portal(w, PX, y, PZ_END, "minecraft:obsidian", "minecraft:end_stone_bricks", "minecraft:nether_portal[axis=z]", "minecraft:purpur_block");
        texto(w, new Location(w, PX + 0.5, y + 6.8, PZ_NETHER + 0.5), "§c§lNETHER DAS MASMORRAS\n§7Masmorras de nível 3 e 4 no fogo\n§8precisa do nível de poder " + nivelExigido(), 1.1f);
        texto(w, new Location(w, PX + 0.5, y + 6.8, PZ_END + 0.5), "§d§lEND DAS MASMORRAS\n§7Masmorras de nível 4 e 5 no vazio\n§8precisa do nível de poder " + nivelExigido(), 1.1f);
        pl.getLogger().info("Portoes do Nether e do End construidos no lobby.");
    }

    /** Portal de 3 de vao x 4 de altura, eixo z (atravessa no x), com degrau na frente. */
    private void portal(World w, int x, int y, int z, String moldura, String detalhe, String dentro, String degrau) {
        for (int dz = -3; dz <= 3; dz++) for (int dy = 1; dy <= 8; dy++) por(w, x, y + dy, z + dz, "minecraft:air");
        for (Entity e : w.getNearbyEntities(new Location(w, x + 0.5, y + 7, z + 0.5), 1.5, 3, 1.5)) if (e.getPersistentDataContainer().has(kEnfeite)) e.remove();
        for (int dz = -2; dz <= 2; dz++)
            for (int dy = 0; dy <= 5; dy++) {
                boolean borda = Math.abs(dz) == 2 || dy == 0 || dy == 5;
                String b;
                if (!borda) b = dentro;
                else if ((dy == 0 || dy == 5) && Math.abs(dz) == 2) b = detalhe;
                else if (dy % 2 == 1 && Math.abs(dz) == 2) b = detalhe;
                else b = moldura;
                por(w, x, y + dy, z + dz, b);   // a base do arco fica no nivel do piso: e so andar para dentro
            }
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= -1; dx++) por(w, x + dx, y, z + dz, degrau);
        por(w, x, y + 6, z, "minecraft:soul_lantern");
    }

    private void construirChegada(World w, int y, boolean nether) {
        int cx = CHEGADA_X, cz = CHEGADA_Z;
        w.getChunkAt(cx >> 4, cz >> 4).load(true);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.getChunkAt((cx >> 4) + dx, (cz >> 4) + dz).load(true);
        // no End tudo e obsidiana/pedra do End: o dragao nao quebra
        String piso = nether ? "minecraft:polished_blackstone_bricks" : "minecraft:obsidian";
        String parede = nether ? "minecraft:blackstone" : "minecraft:end_stone";
        String luz = nether ? "minecraft:shroomlight" : "minecraft:sea_lantern";
        int r = 7, alt = 6;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -1; dy <= alt; dy++) {
                    boolean borda = Math.abs(dx) == r || Math.abs(dz) == r;
                    String b;
                    if (dy == -1) b = piso;
                    else if (dy == alt) b = (dx + dz) % 5 == 0 ? luz : parede;
                    else if (borda) b = (dy == 2 && (dx + dz) % 4 == 0) ? luz : parede;
                    else b = "minecraft:air";
                    por(w, cx + dx, y + dy, cz + dz, b);
                }
        // portal de volta (parede oeste), atravessa no x
        for (int dz = -2; dz <= 2; dz++)
            for (int dy = -1; dy <= 4; dy++) {
                boolean borda = Math.abs(dz) == 2 || dy == -1 || dy == 4;
                por(w, cx - r + 1, y + dy, cz + dz, borda ? "minecraft:obsidian" : "minecraft:nether_portal[axis=z]");
            }
        texto(w, new Location(w, cx - r + 1.5, y + 6.8, cz + 0.5), "§a§lVOLTAR AO LOBBY\n§7atravesse o portal", 1.0f);
        texto(w, new Location(w, cx + 0.5, y + 5.5, cz + 0.5), nether ? "§c§lNETHER DAS MASMORRAS\n§7Fale com o Guia: ele te leva até uma masmorra\n§8sem fome, sem quebrar, só os monstros das masmorras"
                : "§d§lEND DAS MASMORRAS\n§7Fale com o Guia: ele te leva até uma masmorra\n§8cuidado com o vazio", 1.1f);
        pl.getLogger().info("Camara de chegada construida em " + w.getName() + " (" + cx + ", " + y + ", " + cz + ").");
    }

    private void texto(World w, Location l, String t, float escala) {
        w.spawn(l, TextDisplay.class, x -> {
            x.text(Masmorras.txt(t));
            x.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
            x.setPersistent(true);
            x.setLineWidth(240);
            x.getPersistentDataContainer().set(kEnfeite, PersistentDataType.INTEGER, 1);
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(escala), new org.joml.AxisAngle4f()));
        });
    }

    Location chegada(World w) {
        int y = w.equals(mm.nether) ? CHEGADA_Y_NETHER : CHEGADA_Y_END;
        return new Location(w, CHEGADA_X + 0.5, y, CHEGADA_Z + 0.5, -90f, 0f);
    }

    // ------------------------------------------------------------------ guias

    private void garantirGuias() {
        for (World w : new World[] {mm.nether, mm.end}) {
            if (w == null || !w.isChunkLoaded(CHEGADA_X >> 4, CHEGADA_Z >> 4)) continue;
            Location l = chegada(w).add(3, 0, 0);
            l.setYaw(90f);
            boolean tem = false;
            for (Entity e : w.getNearbyEntities(l, 4, 4, 4)) if (e.getPersistentDataContainer().has(kGuia)) tem = true;
            if (tem) continue;
            boolean nether = w.equals(mm.nether);
            Villager v = pl.loja.npc(l, nether ? "§c§lGuia do Nether" : "§d§lGuia do Fim", null, Loja.profissao(0), kGuia, nether ? 1 : 2);
            Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft("cartographer"));
            if (prof != null) v.setProfession(prof);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEntityEvent e) {
        Integer k = e.getRightClicked().getPersistentDataContainer().get(kGuia, PersistentDataType.INTEGER);
        if (k == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        World w = k == 1 ? mm.nether : mm.end;
        if (w == null) return;
        Masmorra alvo = null;
        java.util.List<Masmorra> l = new java.util.ArrayList<>();
        for (Masmorra m : pl.todas) if (m.mundo.equals(w.getName()) && !m.demolindo && !m.tipo.secreta) l.add(m);
        if (l.isEmpty()) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "As masmorras daqui ainda estão sendo construídas. Volte daqui a pouco."));
            return;
        }
        alvo = l.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(l.size()));
        Location dest = Loja.lugarPerto(w, alvo.saidaX, alvo.saidaY, alvo.saidaZ);
        if (dest == null) dest = new Location(w, alvo.saidaX + 0.5, alvo.saidaY + 1, alvo.saidaZ + 0.5);
        final Masmorra m = alvo;
        p.sendMessage(Masmorras.txt((k == 1 ? "§c§lGuia do Nether: " : "§d§lGuia do Fim: ") + "§fSegure firme! Vou te levar até a §e" + m.tipo.nome + " §f(nível " + m.tipo.nivel + ", chefe §c" + m.tipo.chefeNome + "§f)."));
        p.setFallDistance(0);
        p.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.playSound(p.getLocation(), "minecraft:block.portal.travel", 0.4f, 1.4f);
            pl.darBussola(p, m);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void naoTransforma(EntityTransformEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kGuia)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoApanha(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kGuia)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ atravessar

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        World w = to.getWorld();
        if (!mm.ehNosso(w)) return;
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        Block b = to.getBlock();
        if (b.getType() != Material.NETHER_PORTAL) return;
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        espera.put(p.getUniqueId(), agora + 4000L);
        if (w.equals(mm.normal)) {
            // lobby: qual portal?
            if (b.getX() != PX || Math.abs(b.getZ() - PZ_NETHER) > 1 && Math.abs(b.getZ() - PZ_END) > 1) return;
            boolean nether = Math.abs(b.getZ() - PZ_NETHER) <= 1;
            World destino = nether ? mm.nether : mm.end;
            if (destino == null) return;
            int exige = nivelExigido();
            int poder = Aura.poder(p);
            if (!p.hasPermission("masmorras.admin") && poder >= 0 && poder < exige) {
                p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§cEste portal só abre para quem tem §bnível de poder " + exige + "§c (a 4ª habilidade). Você está no §b" + poder + "§c."));
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 0.8f);
                Location fora = new Location(w, PX - 1.5, to.getY(), b.getZ() + 0.5, to.getYaw(), to.getPitch());
                p.teleport(fora);
                return;
            }
            Location ch = chegada(destino);
            p.setFallDistance(0);
            p.teleportAsync(ch, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
                if (!ok) return;
                p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt(nether ? "§c§lNETHER DAS MASMORRAS" : "§d§lEND DAS MASMORRAS"), Masmorras.txt("§7Fale com o Guia para ir a uma masmorra")));
                p.playSound(p.getLocation(), "minecraft:block.portal.travel", 0.5f, nether ? 0.8f : 1.3f);
            });
        } else {
            // camara de chegada: volta ao lobby
            Location ch = chegada(w);
            if (Math.abs(b.getX() - (CHEGADA_X - 6)) > 1 || Math.abs(b.getZ() - CHEGADA_Z) > 2 || Math.abs(b.getY() - ch.getBlockY()) > 6) return;
            mm.irParaLobby(p);
        }
    }
}
