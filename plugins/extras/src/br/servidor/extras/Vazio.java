package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * O spawn e uma ilha flutuante: tira o terreno que fica embaixo e em volta dela (um buraco redondo ate o vazio,
 * com a borda irregular para parecer natural). Faz uma vez so, aos poucos, e so se existir a copia de seguranca
 * das regioes. Quem pula da ilha cai no vazio e e levado para o chao fora do buraco, na direcao em que pulou
 * (sem dano de queda).
 */
final class Vazio implements Listener {

    private final Extras pl;
    final File feito, selado;
    private BukkitTask tarefa;
    String situacao = "parado";

    Vazio(Extras pl) {
        this.pl = pl;
        this.feito = new File(pl.getDataFolder(), "vazio-feito.txt");
        this.selado = new File(pl.getDataFolder(), "vazio-selado.txt");
        this.arquivoRaio = new File(pl.getDataFolder(), "vazio-raio.txt");
    }

    double cx() { return pl.getConfig().getDouble("vazio.centro-x", 6.5); }
    double cz() { return pl.getConfig().getDouble("vazio.centro-z", -5.5); }
    /** Raio do buraco que ja foi feito (o arquivo vazio-raio.txt guarda o raio depois de ampliar). */
    int raio() {
        if (raioFeito < 0) {
            raioFeito = 0;
            try {
                if (arquivoRaio.exists()) raioFeito = Integer.parseInt(Files.readString(arquivoRaio.toPath()).trim());
            } catch (Exception ignored) {
            }
        }
        return raioFeito > 0 ? raioFeito : pl.getConfig().getInt("vazio.raio", 72);
    }

    /** Raio que o buraco deve ter (01/11: tira o terreno em volta do spawn principal ate aqui). */
    int raioAlvo() { return pl.getConfig().getInt("vazio.raio-ampliado", 160); }

    private int raioFeito = -1;
    private int raioAmpliando = 0;
    private File arquivoRaio;
    int ateY() { return pl.getConfig().getInt("vazio.ate-y", 118); }

    World mundo() {
        return Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world"));
    }

    /** Raio do buraco nesse angulo (borda irregular, entre raio-6 e raio+6). */
    static double raioEm(double r, double ang) {
        return r + 3.0 * Math.sin(3 * ang + 1.0) + 2.0 * Math.sin(7 * ang + 2.0) + 1.0 * Math.sin(13 * ang);
    }

    /** O ponto (x, z) esta dentro do buraco? */
    static boolean noBuraco(double cx, double cz, double r, double x, double z) {
        double dx = x - cx, dz = z - cz, d = Math.hypot(dx, dz);
        if (d > r + 6.5) return false;
        if (d < r - 6.5) return true;
        return d < raioEm(r, Math.atan2(dz, dx));
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("vazio.ativo", true);
    }

    boolean feito() {
        return feito.exists();
    }

    // ================================================================== limpar (uma vez)

    void conferir() {
        if (!ligado()) return;
        if (feito()) {
            if (raio() < raioAlvo()) ampliar(raioAlvo());
            else if (!selado.exists() && pl.getConfig().getBoolean("vazio.selar-paredes", true)) selar();
            return;
        }
        String copia = pl.getConfig().getString("vazio.copia-necessaria", "");
        if (!copia.isEmpty()) {
            File c = new File(copia); // a pasta de trabalho e a do servidor
            if (!c.exists()) {
                situacao = "esperando a copia de seguranca (" + copia + ")";
                pl.getLogger().warning("Limpeza embaixo do spawn: nao fiz porque a copia de seguranca nao existe (" + copia + ").");
                return;
            }
        }
        limpar();
    }

    void limpar() {
        World w = mundo();
        if (w == null || tarefa != null) return;
        double cx = cx(), cz = cz();
        int r = raio(), topo = Math.min(ateY(), w.getMaxHeight() - 1), base = w.getMinHeight();
        List<int[]> chunks = new ArrayList<>();
        int r2 = r + 7;
        for (int x = (int) Math.floor((cx - r2) / 16); x <= (int) Math.floor((cx + r2) / 16); x++)
            for (int z = (int) Math.floor((cz - r2) / 16); z <= (int) Math.floor((cz + r2) / 16); z++) {
                // algum canto do chunk dentro do buraco?
                double px = Math.max(x * 16, Math.min(cx, x * 16 + 15)), pz = Math.max(z * 16, Math.min(cz, z * 16 + 15));
                if (Math.hypot(px - cx, pz - cz) <= r + 6.5) chunks.add(new int[] {x, z});
            }
        chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - cx, c[1] * 16 + 8 - cz)));
        pl.getLogger().info("Limpeza embaixo do spawn: " + chunks.size() + " chunks, raio ~" + r + " em volta de " + cx + " " + cz + ", do fundo ate y " + topo + ".");
        BlockData ar = Bukkit.createBlockData(Material.AIR);
        long inicio = System.currentTimeMillis();
        int[] i = {0};
        long[] tirados = {0};
        // estado do chunk atual: coluna e altura onde parou
        int[] col = {0};
        boolean[] carregando = {false};
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            long t0 = System.nanoTime();
            while (System.nanoTime() - t0 < 10_000_000L) {
                if (i[0] >= chunks.size()) {
                    tarefa.cancel();
                    tarefa = null;
                    for (int[] c : chunks) w.removePluginChunkTicket(c[0], c[1], pl);
                    try {
                        Files.writeString(feito.toPath(), "feito em " + new java.util.Date() + " - " + tirados[0] + " blocos tirados\n");
                    } catch (IOException e) {
                        pl.getLogger().warning("Nao consegui marcar a limpeza como feita: " + e.getMessage());
                    }
                    situacao = "feito (" + tirados[0] + " blocos)";
                    pl.getLogger().info("Limpeza embaixo do spawn pronta: " + tirados[0] + " blocos tirados em " + (System.currentTimeMillis() - inicio) / 1000 + " s.");
                    return;
                }
                int[] c = chunks.get(i[0]);
                if (!w.isChunkLoaded(c[0], c[1])) {
                    if (!carregando[0]) {
                        carregando[0] = true;
                        w.addPluginChunkTicket(c[0], c[1], pl);
                        w.getChunkAtAsync(c[0], c[1], true).thenAccept(ch -> carregando[0] = false);
                    }
                    return;
                }
                w.addPluginChunkTicket(c[0], c[1], pl);
                ChunkSnapshot snap = w.getChunkAt(c[0], c[1]).getChunkSnapshot(false, false, false);
                // 16x16 colunas; col[0] = proxima coluna
                while (col[0] < 256 && System.nanoTime() - t0 < 10_000_000L) {
                    int lx = col[0] & 15, lz = col[0] >> 4;
                    int x = c[0] * 16 + lx, z = c[1] * 16 + lz;
                    col[0]++;
                    if (!noBuraco(cx, cz, r, x + 0.5, z + 0.5)) continue;
                    for (int y = base; y <= topo; y++) {
                        Material m = snap.getBlockType(lx, y, lz);
                        if (m == Material.AIR || m == Material.VOID_AIR) continue;
                        Block b = w.getBlockAt(x, y, z);
                        b.setBlockData(ar, false);
                        tirados[0]++;
                    }
                }
                if (col[0] >= 256) {
                    col[0] = 0;
                    w.removePluginChunkTicket(c[0], c[1], pl);
                    i[0]++;
                    situacao = "limpando embaixo do spawn: chunk " + i[0] + " de " + chunks.size() + " (" + tirados[0] + " blocos)";
                }
            }
        }, 1L, 1L);
    }

    // ================================================================== ampliar o buraco (tirar o terreno em volta do spawn)

    /** A plataforma do spawn (com folga) e a praca dos portais: nunca mexe nelas. */
    static boolean protegido(int x, int z) {
        boolean ilha = x >= -25 && x <= 47 && z >= -38 && z <= 31;
        boolean portais = x >= PortaisSpawn.X1 - 3 && x <= PortaisSpawn.X2 + 3 && z >= PortaisSpawn.Z1 - 3 && z <= PortaisSpawn.Z2 + 1;
        return ilha || portais;
    }

    /**
     * Tira todo o terreno entre o buraco atual e o raio novo, do fundo do mundo ate o ceu (montanhas, arvores, tudo),
     * menos terrenos do GriefPrevention. Dentro do buraco antigo tira o que sobrou acima de y 96 fora da plataforma
     * (as ilhinhas). Aos poucos (10 ms por tick). No fim fecha as cavernas da parede nova.
     */
    void ampliar(int novo) {
        World w = mundo();
        if (w == null || tarefa != null) return;
        int velho = raio();
        raioAmpliando = novo;
        double cx = cx(), cz = cz();
        int base = w.getMinHeight(), teto = w.getMaxHeight() - 1;
        List<int[]> chunks = new ArrayList<>();
        int r2 = novo + 7;
        for (int x = (int) Math.floor((cx - r2) / 16); x <= (int) Math.floor((cx + r2) / 16); x++)
            for (int z = (int) Math.floor((cz - r2) / 16); z <= (int) Math.floor((cz + r2) / 16); z++) {
                double px = Math.max(x * 16, Math.min(cx, x * 16 + 15)), pz = Math.max(z * 16, Math.min(cz, z * 16 + 15));
                if (Math.hypot(px - cx, pz - cz) <= novo + 6.5) chunks.add(new int[] {x, z});
            }
        chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - cx, c[1] * 16 + 8 - cz)));
        pl.getLogger().info("Ampliando o vazio em volta do spawn: raio " + velho + " -> " + novo + ", " + chunks.size() + " chunks.");
        BlockData ar = Bukkit.createBlockData(Material.AIR);
        long inicio = System.currentTimeMillis();
        int[] i = {0}, col = {0};
        long[] tirados = {0};
        boolean[] carregando = {false};
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            long t0 = System.nanoTime();
            while (System.nanoTime() - t0 < 10_000_000L) {
                if (i[0] >= chunks.size()) {
                    tarefa.cancel();
                    tarefa = null;
                    for (int[] c : chunks) w.removePluginChunkTicket(c[0], c[1], pl);
                    try {
                        Files.writeString(arquivoRaio.toPath(), String.valueOf(novo));
                    } catch (IOException e) {
                        pl.getLogger().warning("Nao consegui guardar o raio novo: " + e.getMessage());
                    }
                    raioFeito = novo;
                    raioAmpliando = 0;
                    selado.delete();
                    situacao = "ampliado (" + tirados[0] + " blocos)";
                    pl.getLogger().info("Vazio em volta do spawn ampliado: raio " + novo + ", " + tirados[0] + " blocos tirados em "
                            + (System.currentTimeMillis() - inicio) / 1000 + " s. Fechando a parede nova...");
                    Bukkit.getScheduler().runTaskLater(pl, this::conferir, 40L);
                    return;
                }
                int[] c = chunks.get(i[0]);
                if (!w.isChunkLoaded(c[0], c[1])) {
                    if (!carregando[0]) {
                        carregando[0] = true;
                        w.addPluginChunkTicket(c[0], c[1], pl);
                        w.getChunkAtAsync(c[0], c[1], true).thenAccept(ch -> carregando[0] = false);
                    }
                    return;
                }
                w.addPluginChunkTicket(c[0], c[1], pl);
                ChunkSnapshot snap = w.getChunkAt(c[0], c[1]).getChunkSnapshot(false, false, false);
                while (col[0] < 256 && System.nanoTime() - t0 < 10_000_000L) {
                    int lx = col[0] & 15, lz = col[0] >> 4;
                    int x = c[0] * 16 + lx, z = c[1] * 16 + lz;
                    col[0]++;
                    if (protegido(x, z)) continue;
                    boolean noVelho = noBuraco(cx, cz, velho, x + 0.5, z + 0.5);
                    if (!noVelho && !noBuraco(cx, cz, novo, x + 0.5, z + 0.5)) continue;
                    if (!noVelho && pl.temTerreno(new Location(w, x + 0.5, 64, z + 0.5))) continue;
                    int y0 = noVelho ? 96 : base;
                    for (int y = y0; y <= teto; y++) {
                        Material m = snap.getBlockType(lx, y, lz);
                        if (m.isAir()) continue;
                        w.getBlockAt(x, y, z).setBlockData(ar, false);
                        tirados[0]++;
                    }
                }
                if (col[0] >= 256) {
                    col[0] = 0;
                    w.removePluginChunkTicket(c[0], c[1], pl);
                    i[0]++;
                    situacao = "ampliando o vazio: chunk " + i[0] + " de " + chunks.size() + " (" + tirados[0] + " blocos)";
                }
            }
        }, 1L, 1L);
    }

    // ================================================================== fechar as cavernas da parede (uma vez)

    /**
     * O buraco cortou muitas cavernas: metade da parede, de y -48 a 16, era ar aberto, com agua e lava escorrendo.
     * Quem esta na ilha enxerga (e o jogo desenha) as cavernas de baixo do spawn inteiro, e isso pesa.
     * Aqui enche so ar, agua e lava numa faixa atras da parede, abaixo da superficie (nao mexe no chao de cima,
     * em arvores, terrenos do GriefPrevention, perto de baus/fornalhas/spawners nem perto de jogadores).
     * A parede vira pedra lisa e o vazio continua igual. Faz uma vez so, aos poucos.
     */
    void selar() {
        World w = mundo();
        if (w == null || tarefa != null) return;
        double cx = cx(), cz = cz();
        int r = raio(), base = w.getMinHeight();
        int faixa = Math.max(2, Math.min(12, pl.getConfig().getInt("vazio.parede-espessura", 6)));
        java.util.Map<Long, List<int[]>> porChunk = new java.util.LinkedHashMap<>();
        int lim = r + 8 + faixa;
        for (int x = (int) Math.floor(cx - lim); x <= (int) Math.ceil(cx + lim); x++)
            for (int z = (int) Math.floor(cz - lim); z <= (int) Math.ceil(cz + lim); z++) {
                double px = x + 0.5, pz = z + 0.5;
                if (noBuraco(cx, cz, r, px, pz)) continue;
                double dx = px - cx, dz = pz - cz;
                if (Math.hypot(dx, dz) - raioEm(r, Math.atan2(dz, dx)) > faixa) continue;
                long k = (((long) (x >> 4)) << 32) ^ ((z >> 4) & 0xffffffffL);
                porChunk.computeIfAbsent(k, kk -> new ArrayList<>()).add(new int[] {x, z});
            }
        List<List<int[]>> fila = new ArrayList<>(porChunk.values());
        pl.getLogger().info("Fechando as cavernas da parede do buraco do spawn: " + fila.size() + " chunks, faixa de " + faixa + " blocos.");
        long inicio = System.currentTimeMillis();
        int[] i = {0}, adiados = {0};
        long[] postos = {0}, pulados = {0};
        boolean[] carregando = {false};
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            long t0 = System.nanoTime();
            while (System.nanoTime() - t0 < 8_000_000L) {
                if (i[0] >= fila.size()) {
                    tarefa.cancel();
                    tarefa = null;
                    try {
                        Files.writeString(selado.toPath(), "feito em " + new java.util.Date() + " - " + postos[0] + " blocos postos, "
                                + pulados[0] + " colunas puladas\n");
                    } catch (IOException e) {
                        pl.getLogger().warning("Nao consegui marcar a parede como fechada: " + e.getMessage());
                    }
                    situacao = "parede fechada (" + postos[0] + " blocos)";
                    pl.getLogger().info("Parede do buraco do spawn fechada: " + postos[0] + " blocos postos, " + pulados[0]
                            + " colunas puladas, em " + (System.currentTimeMillis() - inicio) / 1000 + " s.");
                    return;
                }
                List<int[]> cols = fila.get(i[0]);
                int chx = cols.get(0)[0] >> 4, chz = cols.get(0)[1] >> 4;
                if (!w.isChunkLoaded(chx, chz)) {
                    if (!carregando[0]) {
                        carregando[0] = true;
                        w.addPluginChunkTicket(chx, chz, pl);
                        w.getChunkAtAsync(chx, chz, true).thenAccept(ch -> carregando[0] = false);
                    }
                    return;
                }
                // alguem embaixo da terra por perto: deixa este chunk para o fim (no maximo algumas vezes)
                if (jogadorPerto(w, chx, chz)) {
                    w.removePluginChunkTicket(chx, chz, pl);
                    if (adiados[0]++ < fila.size() * 4) {
                        fila.add(fila.remove(i[0]));
                        return;
                    }
                    pulados[0] += cols.size();
                    i[0]++;
                    continue;
                }
                w.addPluginChunkTicket(chx, chz, pl);
                List<int[]> guardar = blocosGuardados(w, chx, chz);
                for (int[] c : cols) {
                    int x = c[0], z = c[1];
                    if (pl.temTerreno(new Location(w, x + 0.5, 64, z + 0.5))) {
                        pulados[0]++;
                        continue;
                    }
                    int topo = superficie(w, x, z);
                    for (int y = base; y <= topo - 4; y++) {
                        Block b = w.getBlockAt(x, y, z);
                        Material m = b.getType();
                        if (!(m.isAir() || m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN)) continue;
                        if (perto(guardar, x, y, z)) continue;
                        b.setType(y < 0 ? Material.DEEPSLATE : Material.STONE, false);
                        postos[0]++;
                    }
                }
                w.removePluginChunkTicket(chx, chz, pl);
                i[0]++;
                situacao = "fechando a parede: chunk " + i[0] + " de " + fila.size() + " (" + postos[0] + " blocos)";
            }
        }, 1L, 1L);
    }

    /** Altura do chao natural (ignora folhas, troncos, plantas e agua por cima). */
    static int superficie(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
        while (y > w.getMinHeight()) {
            Material m = w.getBlockAt(x, y, z).getType();
            if (m.isSolid() && !Tag.LEAVES.isTagged(m) && !Tag.LOGS.isTagged(m)) break;
            y--;
        }
        return y;
    }

    private static boolean jogadorPerto(World w, int chx, int chz) {
        double mx = chx * 16 + 8, mz = chz * 16 + 8;
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - mx) < 40 && Math.abs(l.getZ() - mz) < 40 && l.getY() < superficie(w, l.getBlockX(), l.getBlockZ()) + 2) return true;
        }
        return false;
    }

    /** Baus, fornalhas, spawners, placas etc. deste chunk e dos vizinhos (nao enche nada perto deles). */
    private static List<int[]> blocosGuardados(World w, int chx, int chz) {
        List<int[]> l = new ArrayList<>();
        for (int a = chx - 1; a <= chx + 1; a++)
            for (int b = chz - 1; b <= chz + 1; b++) {
                if (!w.isChunkLoaded(a, b)) continue;
                for (org.bukkit.block.BlockState s : w.getChunkAt(a, b).getTileEntities(false)) l.add(new int[] {s.getX(), s.getY(), s.getZ()});
            }
        return l;
    }

    private static boolean perto(List<int[]> l, int x, int y, int z) {
        for (int[] p : l) if (Math.abs(p[0] - x) <= 4 && Math.abs(p[1] - y) <= 4 && Math.abs(p[2] - z) <= 4) return true;
        return false;
    }

    void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    // ================================================================== quem cai da ilha vai para o chao

    private boolean caindoNoBuraco(Location l) {
        World w = mundo();
        return w != null && l.getWorld() == w && noBuraco(cx(), cz(), Math.max(raio(), raioAmpliando), l.getX(), l.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void cair(PlayerMoveEvent e) {
        if (!ligado() || !feito()) return;
        Location to = e.getTo();
        if (to.getY() > pl.getConfig().getInt("vazio.resgate-abaixo-de-y", 40) || e.getFrom().getBlockY() == to.getBlockY()) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE || !caindoNoBuraco(to)) return;
        resgatar(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void vazio(EntityDamageEvent e) {
        if (!ligado() || e.getCause() != EntityDamageEvent.DamageCause.VOID || !(e.getEntity() instanceof Player p)) return;
        if (!caindoNoBuraco(p.getLocation())) return;
        e.setCancelled(true);
        resgatar(p);
    }

    private final java.util.Map<java.util.UUID, Long> resgatando = new java.util.HashMap<>();

    /** Leva para o chao logo fora do buraco, na direcao em que a pessoa estava (ou a mais perto que for segura). */
    void resgatar(Player p) {
        long agora = System.currentTimeMillis();
        Long antes = resgatando.get(p.getUniqueId());
        if (antes != null && agora - antes < 3000) return;
        resgatando.put(p.getUniqueId(), agora);
        World w = mundo();
        double cx = cx(), cz = cz();
        Location l = p.getLocation();
        double ang = Math.atan2(l.getZ() - cz, l.getX() - cx);
        double dist = raio() + 12;
        for (int k = 0; k < 24; k++) {
            double a = ang + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * (Math.PI / 12);
            int x = (int) Math.floor(cx + Math.cos(a) * dist), z = (int) Math.floor(cz + Math.sin(a) * dist);
            Block topo = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block pe = topo.getRelative(BlockFace.UP);
            if (!topo.getType().isSolid() || topo.isLiquid() || pe.isLiquid() || !pe.isPassable() || !pe.getRelative(BlockFace.UP).isPassable()) continue;
            Location destino = new Location(w, x + 0.5, pe.getY(), z + 0.5, l.getYaw(), 0f);
            p.setFallDistance(0);
            p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            pl.semQuedaPor(p, 8000L);
            p.teleportAsync(destino, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
                if (ok) p.sendActionBar(Extras.txt("§aVocê desceu da ilha do spawn. §7(§e/spawn §7volta)"));
            });
            return;
        }
        // nada seguro em volta: volta para o spawn
        p.setFallDistance(0);
        p.teleportAsync(w.getSpawnLocation().add(0.5, 0, 0.5), PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
}
