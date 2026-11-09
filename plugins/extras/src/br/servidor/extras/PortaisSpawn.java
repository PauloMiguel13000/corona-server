package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Praca dos Portais no spawn principal (roxo): uma ilha nova ao norte da plataforma, ligada pelo caminho do norte por
 * uma ponte. A oeste o Lobby dos Minigames; a leste o Spawn das Masmorras (as masmorras nao tem portal: procurar). Portal roxo do jogo (nao leva ao Nether).
 * Nao mexe na plataforma do dono (so abre o vidro na ponta do caminho do norte).
 */
final class PortaisSpawn implements Listener {

    static final int PISO = 122, X1 = -9, X2 = 21, Z1 = -64, Z2 = -40, CX = 6, CZ = -52;
    static final int VERSAO = 3;

    record Portal(String nome, String sub, int x, int z, boolean eixoX, String moldura, String detalhe, String acao) {}

    static final Portal[] PORTAIS = {
            new Portal("§6§lLOBBY DOS MINIGAMES", LobbyMinigames.SUB_NOVO, X1 + 1, CZ, false, "minecraft:waxed_cut_copper", "minecraft:orange_glazed_terracotta", "minigames"),
            new Portal("§4§lMUNDO DAS MASMORRAS", "§7Lobby • níveis 1 a 4 • NPCs e rankings", X2 - 1, CZ, false, "minecraft:deepslate_bricks", "minecraft:gold_block", "masmorras")};

    private final Extras pl;
    private final Map<UUID, Long> espera = new HashMap<>();

    PortaisSpawn(Extras pl) {
        this.pl = pl;
    }

    World mundo() {
        return Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world"));
    }

    static boolean naPraca(Location l) {
        return l.getX() >= X1 - 1 && l.getX() <= X2 + 2 && l.getZ() >= Z1 - 1 && l.getZ() <= Z2 + 1 && l.getY() > PISO - 15 && l.getY() < PISO + 20;
    }

    void iniciar() {
        World w = mundo();
        if (w == null || !pl.getConfig().getBoolean("portais-spawn.ativo", true)) return;
        File feito = new File(pl.getDataFolder(), "portais-spawn-v" + VERSAO + ".txt");
        if (feito.exists()) return;
        boolean v1 = new File(pl.getDataFolder(), "portais-spawn-v1.txt").exists();
        boolean v2 = new File(pl.getDataFolder(), "portais-spawn-v2.txt").exists();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            try {
                if (v2) atualizarTextos(w);
                else if (v1) {
                    tirarPortaisDeMasmorra(w);
                    atualizarTextos(w);
                } else construir(w);
                Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable t) {
                pl.getLogger().warning("Praca dos Portais nao terminou: " + t);
            }
        }, 60L);
    }

    // ------------------------------------------------------------------ construcao (uma vez)

    private static void por(World w, int x, int y, int z, String b) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false);
    }

    private static boolean dentro(int x, int z) {
        // retangulo com cantos arredondados (raio 4)
        int ax = x < X1 + 4 ? X1 + 4 - x : (x > X2 - 4 ? x - (X2 - 4) : 0);
        int az = z < Z1 + 4 ? Z1 + 4 - z : (z > Z2 - 4 ? z - (Z2 - 4) : 0);
        return x >= X1 && x <= X2 && z >= Z1 && z <= Z2 && ax * ax + az * az <= 16;
    }

    private static boolean borda(int x, int z) {
        return dentro(x, z) && (!dentro(x + 1, z) || !dentro(x - 1, z) || !dentro(x, z + 1) || !dentro(x, z - 1));
    }

    private void construir(World w) {
        for (int x = (X1 - 2) >> 4; x <= (X2 + 2) >> 4; x++)
            for (int z = (Z1 - 2) >> 4; z <= (-34) >> 4; z++) w.getChunkAt(x, z).load(true);
        for (Entity e : w.getEntities())
            if (naPraca(e.getLocation()) && e.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) e.remove();
        // piso: obsidiana chorona com borda de purpura, losango de ametista no meio e linha de luz para o fundo
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                if (!dentro(x, z)) continue;
                String b;
                int dx = Math.abs(x - CX), dz = Math.abs(z - CZ);
                if (borda(x, z)) b = "minecraft:purpur_block";
                else if (dx + dz <= 2) b = "minecraft:amethyst_block";
                else if (dx + dz == 3) b = "minecraft:purpur_pillar";
                else if (x == CX && (z % 3 == 0)) b = "minecraft:pearlescent_froglight";
                else if (dx == 0 || dz == 0) b = "minecraft:polished_blackstone_bricks";
                else b = ((x + z) & 1) == 0 ? "minecraft:crying_obsidian" : "minecraft:obsidian";
                por(w, x, PISO, z, b);
                por(w, x, PISO - 1, z, "minecraft:obsidian");
            }
        // fundo da ilha afinando para baixo
        for (int k = 2; k <= 8; k++)
            for (int x = X1 + k; x <= X2 - k; x++)
                for (int z = Z1 + k; z <= Z2 - k; z++)
                    if (dentro(x, z) && (x + z + k) % 7 != 0) por(w, x, PISO - k, z, k > 5 ? "minecraft:crying_obsidian" : "minecraft:obsidian");
        // parapeito de vidro roxo com postes de purpura e lanterna (com fisica: o vidro se liga)
        for (int x = X1; x <= X2; x++)
            for (int z = Z1; z <= Z2; z++) {
                if (!borda(x, z)) continue;
                boolean ponte = z == Z2 && x >= 5 && x <= 7;
                if (ponte) continue;
                boolean poste = (x - X1) % 6 == 0 && (z == Z1 || z == Z2) || (z - Z1) % 6 == 0 && (x == X1 || x == X2);
                if (poste) {
                    por(w, x, PISO + 1, z, "minecraft:purpur_pillar");
                    por(w, x, PISO + 2, z, "minecraft:purpur_pillar");
                    por(w, x, PISO + 3, z, "minecraft:lantern[hanging=false]");
                } else w.getBlockAt(x, PISO + 1, z).setBlockData(Bukkit.createBlockData("minecraft:purple_stained_glass_pane"), true);
            }
        // ponte do caminho do norte da plataforma ate a praca
        for (int z = Z2 + 1; z <= -36; z++)
            for (int x = 4; x <= 8; x++) {
                boolean lado = x == 4 || x == 8;
                por(w, x, PISO, z, lado ? "minecraft:purpur_block" : (x == 6 ? "minecraft:pearlescent_froglight" : "minecraft:crying_obsidian"));
                por(w, x, PISO - 1, z, "minecraft:obsidian");
                if (lado) w.getBlockAt(x, PISO + 1, z).setBlockData(Bukkit.createBlockData("minecraft:purple_stained_glass_pane"), true);
            }
        // abre o vidro da plataforma na ponta do caminho do norte (z -35)
        for (int x = 5; x <= 7; x++)
            for (int y = PISO + 1; y <= PISO + 2; y++) {
                Block b = w.getBlockAt(x, y, -35);
                if (b.getType() == Material.PURPLE_STAINED_GLASS_PANE) b.setType(Material.AIR, true);
            }
        // portais
        for (Portal p : PORTAIS) portal(w, p);
        // luz invisivel (nada de monstro nascendo no escuro) e letreiro grande
        Light luz = (Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(15);
        for (int x = X1 + 2; x <= X2 - 2; x += 4)
            for (int z = Z1 + 2; z <= Z2 - 2; z += 4) {
                Block b = w.getBlockAt(x, PISO + 3, z);
                if (b.getType().isAir()) b.setBlockData(luz, false);
            }
        texto(w, new Location(w, CX + 0.5, PISO + 4.5, CZ + 0.5), "§d§lPRAÇA DOS PORTAIS\n§6Minigames §7a oeste • §4Spawn das Masmorras §7a leste\n§8As masmorras? Procure pelo mundo!", 1.5f);
        pl.getLogger().info("Praca dos Portais construida no spawn principal (ao norte da plataforma).");
    }

    /**
     * v2: as masmorras nao tem mais portal (o Paulo quer que as pessoas procurem). Tira os 4 arcos do fundo,
     * devolve o piso e troca o letreiro grande.
     */
    private void tirarPortaisDeMasmorra(World w) {
        for (int x = (X1 - 2) >> 4; x <= (X2 + 2) >> 4; x++)
            for (int z = (Z1 - 2) >> 4; z <= (-34) >> 4; z++) w.getChunkAt(x, z).load(true);
        int zp = Z1 + 1;
        for (int cx : new int[] {-3, 3, 9, 15})
            for (int s = -2; s <= 2; s++) {
                int x = cx + s;
                for (int y = 1; y <= 6; y++) w.getBlockAt(x, PISO + y, zp).setType(Material.AIR, false);
                por(w, x, PISO, zp, ((x + zp) & 1) == 0 ? "minecraft:crying_obsidian" : "minecraft:obsidian");
            }
        for (Entity e : w.getEntities()) {
            if (!(e instanceof TextDisplay) || !naPraca(e.getLocation())) continue;
            Location l = e.getLocation();
            boolean deMasmorra = l.getZ() < Z1 + 4 && l.getY() > PISO + 6;
            boolean grande = Math.abs(l.getX() - (CX + 0.5)) < 0.6 && Math.abs(l.getZ() - (CZ + 0.5)) < 0.6;
            if (deMasmorra || grande) e.remove();
        }
        texto(w, new Location(w, CX + 0.5, PISO + 4.5, CZ + 0.5), "§d§lPRAÇA DOS PORTAIS\n§6Minigames §7a oeste • §4Spawn das Masmorras §7a leste\n§8As masmorras? Procure pelo mundo!", 1.5f);
        pl.getLogger().info("Praca dos Portais: portais das masmorras tirados (ficam Minigames e Spawn das Masmorras).");
    }

    private void portal(World w, Portal p) {
        for (int s = -2; s <= 2; s++)
            for (int y = 0; y <= 6; y++) {
                int x = p.eixoX() ? p.x() + s : p.x(), z = p.eixoX() ? p.z() : p.z() + s;
                boolean bordaArco = Math.abs(s) == 2 || y == 0 || y == 6;
                String b;
                if (!bordaArco) b = "minecraft:nether_portal[axis=" + (p.eixoX() ? "x" : "z") + "]";
                else if (y == 6 && s == 0) b = "minecraft:amethyst_block";
                else if (y == 0) b = p.detalhe();
                else b = p.moldura();
                por(w, x, PISO + y, z, b);
            }
        double tx = p.eixoX() ? p.x() + 0.5 : p.x() + (p.x() < CX ? 1.6 : -0.6);
        double tz = p.eixoX() ? p.z() + 1.6 : p.z() + 0.5;
        texto(w, new Location(w, tx, PISO + 7.6, tz), p.nome() + "\n" + p.sub(), 0.9f);
    }

    private org.bukkit.NamespacedKey chave() {
        return new org.bukkit.NamespacedKey(pl, "portais_spawn");
    }

    /** v3: o portal leste agora leva ao Mundo das Masmorras (lobby novo); troca os letreiros que falavam do Spawn das Masmorras. */
    private void atualizarTextos(World w) {
        int n = 0;
        for (org.bukkit.entity.Entity e : w.getEntities()) {
            if (!(e instanceof TextDisplay t) || !naPraca(e.getLocation()) || !t.getPersistentDataContainer().has(chave())) continue;
            String plano = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(t.text());
            if (plano.contains("SPAWN DAS MASMORRAS")) {
                t.text(Extras.txt(PORTAIS[1].nome() + "\n" + PORTAIS[1].sub()));
                n++;
            } else if (plano.contains("PRAÇA DOS PORTAIS")) {
                t.text(Extras.txt("§d§lPRAÇA DOS PORTAIS\n§6Minigames §7a oeste • §4Mundo das Masmorras §7a leste\n§8Nível 1 perto do lobby; mais longe = mais forte"));
                n++;
            }
        }
        pl.getLogger().info("Praca dos Portais: " + n + " letreiro(s) atualizado(s) para o Mundo das Masmorras.");
    }

    private void texto(World w, Location l, String t, float escala) {
        w.spawn(l, TextDisplay.class, x -> {
            x.text(Extras.txt(t));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(Color.fromARGB(110, 20, 0, 30));
            x.setPersistent(true);
            x.getPersistentDataContainer().set(chave(), PersistentDataType.BYTE, (byte) 1);
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(escala), new AxisAngle4f()));
        });
    }

    // ------------------------------------------------------------------ entrar no portal

    private Portal portalEm(Location l) {
        int y = l.getBlockY() - PISO;
        if (y < 1 || y > 5) return null;
        for (Portal p : PORTAIS) {
            int ox = l.getBlockX() - p.x(), oz = l.getBlockZ() - p.z();
            if (p.eixoX() ? (Math.abs(ox) <= 1 && oz >= -1 && oz <= 1) : (Math.abs(oz) <= 1 && Math.abs(ox) <= 1)) return p;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        if (!naPraca(to) || to.getWorld() != mundo()) return;
        Portal po = portalEm(to);
        if (po == null) return;
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        espera.put(p.getUniqueId(), agora + 4000L);
        p.teleport(new Location(to.getWorld(), CX + 0.5, PISO + 1, CZ + 0.5, to.getYaw(), to.getPitch()));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        switch (po.acao()) {
            case "minigames" -> {
                if (pl.lobby != null) pl.lobby.teleportar(p);
            }
            case "masmorras" -> p.performCommand("masmorras spawn");
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether(PlayerPortalEvent e) {
        if (e.getFrom().getWorld() == mundo() && naPraca(e.getFrom())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether2(EntityPortalEvent e) {
        if (e.getFrom().getWorld() == mundo() && naPraca(e.getFrom())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ protecao da praca

    private static boolean livre(Player p) {
        return p != null && p.hasPermission("extras.admin") && p.getGameMode() == org.bukkit.GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void quebrar(BlockBreakEvent e) {
        if (e.getBlock().getWorld() == mundo() && naPraca(e.getBlock().getLocation()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        if (e.getBlock().getWorld() == mundo() && naPraca(e.getBlock().getLocation()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void explodir(EntityExplodeEvent e) {
        if (e.getLocation().getWorld() == mundo()) e.blockList().removeIf(b -> naPraca(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void nascer(CreatureSpawnEvent e) {
        if (e.getLocation().getWorld() != mundo() || !naPraca(e.getLocation())) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.NATURAL || r == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS || r == CreatureSpawnEvent.SpawnReason.PATROL)
            e.setCancelled(true);
    }
}
