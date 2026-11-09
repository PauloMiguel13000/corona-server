package br.servidor.masmorras;

import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Os tres mundos das masmorras (normal, Nether e End) e as regras deles: ninguem sente fome, o terreno pode ser
 * quebrado e construido (menos o lobby, as estruturas, a chegada do Nether/End e as masmorras), nenhum monstro nasce sozinho (so os que os plugins colocam), sem PvP,
 * sem portal do jogo, e quem morre volta para o lobby. O mundo normal usa o {@link GeradorMundo} (aneis por nivel);
 * o Nether e o End usam o terreno normal do jogo, com borda propria.
 */
final class MundoMasmorras implements Listener {

    private final Masmorras pl;
    World normal, nether, end;
    GeradorMundo gerador;

    MundoMasmorras(Masmorras pl) {
        this.pl = pl;
    }

    boolean ativo() {
        return pl.getConfig().getBoolean("mundo-novo.ativo", true);
    }

    String nomeNormal() {
        return pl.getConfig().getString("mundo-novo.nome", "masmorras_mundo");
    }

    int[] zonas() {
        List<Integer> l = pl.getConfig().getIntegerList("mundo-novo.zonas");
        if (l.size() < 3) return new int[] {900, 1900, 3000};
        return new int[] {l.get(0), l.get(1), l.get(2)};
    }

    /** Cria (ou carrega) os mundos. Chamado no onEnable antes de tudo que usa mundos.normal. */
    void criar() {
        if (!ativo()) return;
        long sem = pl.getConfig().getLong("mundo-novo.semente", 20261108L);
        gerador = new GeradorMundo(sem, zonas());
        normal = new WorldCreator(nomeNormal()).generator(gerador).biomeProvider(gerador.getDefaultBiomeProvider(null))
                .environment(World.Environment.NORMAL).generateStructures(false).seed(sem).createWorld();
        if (normal == null) {
            pl.getLogger().warning("Nao consegui criar o mundo " + nomeNormal() + ".");
            return;
        }
        regras(normal);
        normal.setSpawnLocation(new Location(normal, 0.5, GeradorMundo.LOBBY_Y + 1, 0.5, 180f, 0f));
        pl.getLogger().info("Mundo das Masmorras pronto: " + normal.getName() + " (zonas " + zonas()[0] + "/" + zonas()[1] + "/" + zonas()[2] + ").");
        if (pl.getConfig().getBoolean("mundo-novo.nether-end", true)) {
            nether = new WorldCreator(pl.getConfig().getString("mundos.nether", "masmorras_nether")).environment(World.Environment.NETHER)
                    .generateStructures(true).seed(sem + 1).createWorld();
            end = new WorldCreator(pl.getConfig().getString("mundos.end", "masmorras_end")).environment(World.Environment.THE_END)
                    .generateStructures(true).seed(sem + 2).createWorld();
            if (nether != null) regras(nether);
            if (end != null) regras(end);
        }
    }

    private void regras(World w) {
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setPVP(false);
        w.setDifficulty(org.bukkit.Difficulty.NORMAL);   // HARD deixava os monstros de nivel 1 pesados demais
        w.setGameRule(GameRule.KEEP_INVENTORY, true);       // ninguem perde os drops ao morrer (pedido do Paulo)
        w.setKeepSpawnInMemory(false);
        // clima do mundo das masmorras: fim de tarde eterno (ceu alaranjado, luz cheia) e sem chuva - algo diferente
        if (w.getEnvironment() == World.Environment.NORMAL) {
            long hora = pl.getConfig().getLong("mundo-novo.hora-fixa", 12200L);
            if (hora >= 0) {
                w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                w.setTime(hora);
            } else w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            if (pl.getConfig().getBoolean("mundo-novo.sem-chuva", true)) {
                w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                w.setStorm(false);
                w.setThundering(false);
            }
        }
    }

    /** O mundo e um dos mundos das masmorras? */
    boolean ehNosso(World w) {
        if (w == null || !ativo()) return false;
        return w.equals(normal) || w.equals(nether) || w.equals(end);
    }

    Location spawnLobby() {
        return new Location(normal, 0.5, GeradorMundo.LOBBY_Y + 1, 0.5, 180f, 0f);
    }

    private boolean livre(Player p) {
        return p != null && pl.podeEditar(p);
    }

    // ------------------------------------------------------------------ blocos

    // 08/11 (pedido do Paulo): o terreno do mundo das masmorras pode ser quebrado e construido. Continuam protegidos:
    // as masmorras (Masmorras.protegido), o lobby do mundo, as estruturas do mapa e a chegada do Nether/End.

    /** O bloco fica numa area protegida do mundo das masmorras? */
    boolean protegidoMundo(Block b) {
        World w = b.getWorld();
        if (!ehNosso(w)) return false;
        int x = b.getX(), z = b.getZ();
        if (w.equals(normal)) {
            if (Math.hypot(x + 0.5, z + 0.5) <= GeradorMundo.LOBBY_RAIO + 12) return true;
            return pertoDeEstrutura(w, x, b.getY(), z);
        }
        return Math.abs(x - Portoes.CHEGADA_X) <= 16 && Math.abs(z - Portoes.CHEGADA_Z) <= 16;
    }

    private boolean pertoDeEstrutura(World w, int x, int y, int z) {
        if (gerador == null) return false;
        int cx = x >> 4, cz = z >> 4;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                Estruturas.Escolha e = Estruturas.escolher(gerador.semente, cx + dx, cz + dz);
                if (e == null || Math.abs(x - e.x()) > 11 || Math.abs(z - e.z()) > 11) continue;
                // so a estrutura (na superficie) e um pouco abaixo dela; minerar fundo embaixo pode
                if (y >= w.getHighestBlockYAt(e.x(), e.z()) - 12) return true;
            }
        return false;
    }

    private void avisar(Player p, String o) {
        if (p != null) p.sendActionBar(Masmorras.txt("§7" + o + " §8(lobby, estruturas e masmorras são protegidos)"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void quebrar(BlockBreakEvent e) {
        if (!livre(e.getPlayer()) && protegidoMundo(e.getBlock())) {
            e.setCancelled(true);
            avisar(e.getPlayer(), "Aqui não dá para quebrar.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        if (!livre(e.getPlayer()) && protegidoMundo(e.getBlock())) {
            e.setCancelled(true);
            avisar(e.getPlayer(), "Aqui não dá para colocar blocos.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void balde(PlayerBucketEmptyEvent e) {
        if (!livre(e.getPlayer()) && protegidoMundo(e.getBlockClicked().getRelative(e.getBlockFace()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void balde2(PlayerBucketFillEvent e) {
        if (!livre(e.getPlayer()) && protegidoMundo(e.getBlockClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void escorreu(org.bukkit.event.block.BlockFromToEvent e) {
        if (protegidoMundo(e.getToBlock()) && !protegidoMundo(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pistao(org.bukkit.event.block.BlockPistonExtendEvent e) {
        if (!ehNosso(e.getBlock().getWorld())) return;
        for (Block b : e.getBlocks()) if (protegidoMundo(b) || protegidoMundo(b.getRelative(e.getDirection()))) {
            e.setCancelled(true);
            return;
        }
        if (protegidoMundo(e.getBlock().getRelative(e.getDirection()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pistaoVolta(org.bukkit.event.block.BlockPistonRetractEvent e) {
        if (!ehNosso(e.getBlock().getWorld())) return;
        for (Block b : e.getBlocks()) if (protegidoMundo(b)) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pisar(PlayerInteractEvent e) {
        // plantacao pisada, tocha de redstone etc.: nada muda o mapa
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && ehNosso(e.getClickedBlock().getWorld())
                && e.getClickedBlock().getType() == Material.FARMLAND) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void quadro(HangingBreakByEntityEvent e) {
        if (ehNosso(e.getEntity().getWorld()) && !(e.getRemover() instanceof Player p && livre(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mexer(PlayerInteractEntityEvent e) {
        if (ehNosso(e.getRightClicked().getWorld()) && (e.getRightClicked() instanceof org.bukkit.entity.ItemFrame || e.getRightClicked() instanceof org.bukkit.entity.ArmorStand)
                && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void explodir(EntityExplodeEvent e) {
        if (ehNosso(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fogo(BlockIgniteEvent e) {
        if (ehNosso(e.getBlock().getWorld()) && !(e.getPlayer() != null && livre(e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void queimar(BlockBurnEvent e) {
        if (ehNosso(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mudarBloco(EntityChangeBlockEvent e) {
        if (ehNosso(e.getBlock().getWorld()) && !(e.getEntity() instanceof Player p && livre(p))) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ fome, monstros, portais

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fome(FoodLevelChangeEvent e) {
        if (!ehNosso(e.getEntity().getWorld())) return;
        if (e.getFoodLevel() < e.getEntity().getFoodLevel()) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player p && p.getFoodLevel() < 20) p.setFoodLevel(20);
        }
    }

    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAIS = Set.of(CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE, CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION, CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
            CreatureSpawnEvent.SpawnReason.JOCKEY, CreatureSpawnEvent.SpawnReason.MOUNT, CreatureSpawnEvent.SpawnReason.TRAP,
            CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK, CreatureSpawnEvent.SpawnReason.LIGHTNING, CreatureSpawnEvent.SpawnReason.DROWNED,
            CreatureSpawnEvent.SpawnReason.FROZEN, CreatureSpawnEvent.SpawnReason.INFECTION, CreatureSpawnEvent.SpawnReason.CURED,
            CreatureSpawnEvent.SpawnReason.PIGLIN_ZOMBIFIED, CreatureSpawnEvent.SpawnReason.ENDER_PEARL, CreatureSpawnEvent.SpawnReason.BREEDING,
            CreatureSpawnEvent.SpawnReason.EGG, CreatureSpawnEvent.SpawnReason.OCELOT_BABY, CreatureSpawnEvent.SpawnReason.SPELL,
            CreatureSpawnEvent.SpawnReason.SLIME_SPLIT, CreatureSpawnEvent.SpawnReason.DUPLICATION);

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void nascer(CreatureSpawnEvent e) {
        if (!ehNosso(e.getLocation().getWorld())) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (NATURAIS.contains(r)) {
            // o chefe divide-se / invoca (SPELL, SLIME_SPLIT) so dentro das masmorras
            if ((r == CreatureSpawnEvent.SpawnReason.SPELL || r == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT)
                    && pl.masmorraEm(e.getLocation().getWorld(), e.getLocation().getBlockX(), e.getLocation().getBlockY(), e.getLocation().getBlockZ()) != null) return;
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void portal(PlayerPortalEvent e) {
        if (ehNosso(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void portal2(EntityPortalEvent e) {
        if (ehNosso(e.getFrom().getWorld())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ morte e queda

    @EventHandler(priority = EventPriority.HIGH)
    public void renascer(PlayerRespawnEvent e) {
        if (!ehNosso(e.getPlayer().getWorld()) || normal == null) return;
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        e.setRespawnLocation(spawnLobby());
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (!ehNosso(to.getWorld()) || normal == null) return;
        if (to.getY() < to.getWorld().getMinHeight() - 10) {
            Player p = e.getPlayer();
            p.setFallDistance(0);
            p.teleportAsync(spawnLobby(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    /** Quem desconectou dentro do Nether/End das masmorras sem ter nivel volta ao lobby (as regras do nivel 4 ficam no Portoes). */
    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (ehNosso(p.getWorld()) && p.getFoodLevel() < 20) p.setFoodLevel(20);
    }

    /** Leva ao lobby do mundo novo. */
    void irParaLobby(Player p) {
        if (normal == null) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "O Mundo das Masmorras está desligado."));
            return;
        }
        p.setFallDistance(0);
        p.teleportAsync(spawnLobby(), PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§6§lMUNDO DAS MASMORRAS"), Masmorras.txt("§7Lobby • escolha o seu caminho")));
            p.playSound(p.getLocation(), "minecraft:block.portal.travel", 0.4f, 1.3f);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline() && p.getWorld().equals(normal)) p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Nível 1 fica perto do lobby; quanto mais longe, mais forte. §eGuia §7(leste) mostra as masmorras."));
            }, 40L);
        });
    }
}
