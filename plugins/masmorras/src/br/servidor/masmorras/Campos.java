package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Campos de monstros do Mundo das Masmorras (v2, 08/11): grupos de monstros parados espalhados pelo mapa, do nivel do
 * anel onde estao. Dois tipos de lugar: (1) campos no mato (sorteados pelo chunk), que ganham enfeites de acampamento
 * (fogueira, barraca, estacas com lanterna) na primeira vez; (2) as ESTRUTURAS do mapa (ruina, torre de vigia, monolito,
 * acampamento abandonado, circulo de pedras, altar, ossada, torre quebrada), que agora tem os guardioes delas: cada uma
 * com monstros do tema. Em todo campo pode nascer um ELITE (maior, brilhando, 3x de vida) que da mais coronas. Limpar o
 * campo inteiro da um premio para quem matou o ultimo. Os monstros nao saem de perto e voltam alguns minutos depois.
 */
final class Campos implements Listener {

    // raio menor (08/11, celular travando): so nasce a menos de 72 blocos e some a mais de 150 - menos bichos por jogador
    private static final int RAIO_CHUNKS = 4, RAIO_CAMPO = 14, RESPAWN_MS = 4 * 60_000, LONGE = 150;

    enum Tipo { CAMPO, RUINA, TORRE, MONOLITO, ACAMPAMENTO, CIRCULO, ALTAR, OSSADA, TORRE_QUEBRADA }

    private final Masmorras pl;
    private final MundoMasmorras mm;
    private final NamespacedKey kCampo, kElite, kProps;
    private final Map<Long, Campo> campos = new HashMap<>();
    /** Quando cada campo ficou vazio (para o respawn), mesmo quando ele sai da memoria. */
    private final Map<Long, Long> vazioEm = new HashMap<>();
    private int tick;

    private static final class Campo {
        final long chave;
        final int x, z, nivel;
        final Tipo tipo;
        int y = -1;
        final List<UUID> mobs = new ArrayList<>();
        int vivos;

        Campo(long chave, int x, int z, int nivel, Tipo tipo) {
            this.chave = chave;
            this.x = x;
            this.z = z;
            this.nivel = nivel;
            this.tipo = tipo;
        }
    }

    Campos(Masmorras pl, MundoMasmorras mm) {
        this.pl = pl;
        this.mm = mm;
        kCampo = new NamespacedKey(pl, "campo");
        kElite = new NamespacedKey(pl, "campo_elite");
        kProps = new NamespacedKey(pl, "campo_props");
    }

    void iniciar() {
        if (mm.normal == null || !pl.getConfig().getBoolean("campos.ativo", true)) return;
        Bukkit.getScheduler().runTaskTimer(pl, this::ciclo, 100L, 40L);
    }

    void parar() {
        for (Campo c : campos.values()) for (UUID id : c.mobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        campos.clear();
    }

    private static long chave(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** O chunk tem campo no mato? (sorteio fixo pela semente) */
    private boolean temCampo(int cx, int cz) {
        long h = (cx * 341873128712L + cz * 132897987541L) ^ (mm.gerador.semente * 31 + 7);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        int freq = Math.max(2, pl.getConfig().getInt("campos.um-a-cada-chunks", 8));
        return Math.floorMod(h, freq) == 0;
    }

    private static Tipo tipoDaEstrutura(String s) {
        return switch (s) {
            case "ruina" -> Tipo.RUINA;
            case "torre" -> Tipo.TORRE;
            case "monolito" -> Tipo.MONOLITO;
            case "acampamento" -> Tipo.ACAMPAMENTO;
            case "circulo" -> Tipo.CIRCULO;
            case "altar" -> Tipo.ALTAR;
            case "ossada" -> Tipo.OSSADA;
            default -> Tipo.TORRE_QUEBRADA;
        };
    }

    private void ciclo() {
        World w = mm.normal;
        if (w == null) return;
        tick++;
        List<Player> jogadores = new ArrayList<>();
        for (Player p : w.getPlayers()) if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR) jogadores.add(p);
        if (jogadores.isEmpty()) {
            if (!campos.isEmpty() && tick % 15 == 0) limparLonge(jogadores);
            return;
        }
        boolean guardioes = pl.getConfig().getBoolean("campos.guardioes-das-estruturas", true);
        // descobre campos perto dos jogadores
        for (Player p : jogadores) {
            int pcx = p.getLocation().getBlockX() >> 4, pcz = p.getLocation().getBlockZ() >> 4;
            for (int cx = pcx - RAIO_CHUNKS; cx <= pcx + RAIO_CHUNKS; cx++)
                for (int cz = pcz - RAIO_CHUNKS; cz <= pcz + RAIO_CHUNKS; cz++) {
                    long k = chave(cx, cz);
                    if (campos.containsKey(k)) continue;
                    Estruturas.Escolha esc = guardioes ? Estruturas.escolher(mm.gerador.semente, cx, cz) : null;
                    if (esc != null) {
                        double dist = Math.hypot(esc.x(), esc.z());
                        int nivel = mm.gerador.nivel(dist);
                        campos.put(k, new Campo(k, esc.x(), esc.z(), nivel, tipoDaEstrutura(Estruturas.tipo(nivel, esc.sorte()))));
                        continue;
                    }
                    if (!temCampo(cx, cz)) continue;
                    int x = (cx << 4) + 8, z = (cz << 4) + 8;
                    double dist = Math.hypot(x, z);
                    if (dist < GeradorMundo.LOBBY_RAIO + GeradorMundo.LOBBY_MISTURA + 40) continue;
                    campos.put(k, new Campo(k, x, z, mm.gerador.nivel(dist), Tipo.CAMPO));
                }
        }
        // cuida de cada campo
        for (Campo c : campos.values()) {
            if (!w.isChunkLoaded(c.x >> 4, c.z >> 4)) continue;
            c.mobs.removeIf(id -> {
                Entity e = Bukkit.getEntity(id);
                return e == null || !e.isValid();
            });
            if (!c.mobs.isEmpty()) {
                segurar(c);
                continue;
            }
            Long vz = vazioEm.get(c.chave);
            if (vz != null && System.currentTimeMillis() - vz < RESPAWN_MS) continue;
            if (c.vivos > 0) c.vivos = 0;
            double pertoMin = Double.MAX_VALUE;
            for (Player p : jogadores) pertoMin = Math.min(pertoMin, p.getLocation().distanceSquared(new Location(w, c.x, p.getLocation().getY(), c.z)));
            if (pertoMin < 12 * 12 || pertoMin > 72 * 72) continue;
            nascer(w, c);
        }
        if (tick % 15 == 0) limparLonge(jogadores);
    }

    private void limparLonge(List<Player> jogadores) {
        Iterator<Campo> it = campos.values().iterator();
        while (it.hasNext()) {
            Campo c = it.next();
            boolean perto = false;
            for (Player p : jogadores) if (Math.hypot(p.getLocation().getX() - c.x, p.getLocation().getZ() - c.z) < LONGE) perto = true;
            if (perto) continue;
            for (UUID id : c.mobs) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
            it.remove();
        }
    }

    /** Monstro que saiu do campo volta para o centro (ficam "parados" na area deles). */
    private void segurar(Campo c) {
        for (UUID id : c.mobs) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Mob m) || !m.isValid()) continue;
            Location l = m.getLocation();
            double dx = l.getX() - c.x, dz = l.getZ() - c.z;
            if (dx * dx + dz * dz > (RAIO_CAMPO + 6) * (RAIO_CAMPO + 6)) {
                Location volta = chao(m.getWorld(), c.x + (int) (Math.random() * 6 - 3), c.z + (int) (Math.random() * 6 - 3));
                if (volta != null) {
                    m.setTarget(null);
                    m.teleport(volta);
                    m.getWorld().spawnParticle(org.bukkit.Particle.POOF, volta.clone().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02);
                }
            }
        }
    }

    private static Location chao(World w, int x, int z) {
        Block b = w.getHighestBlockAt(x, z);
        int tent = 0;
        while (tent++ < 8 && (b.getType() == Material.LIGHT || !b.getType().isSolid()) && b.getY() > w.getMinHeight()) b = b.getRelative(0, -1, 0);
        if (!b.getType().isSolid() || b.isLiquid() || b.getType() == Material.CACTUS || b.getType() == Material.MAGMA_BLOCK || b.getType() == Material.CAMPFIRE
                || b.getType() == Material.SOUL_CAMPFIRE) return null;
        if (!b.getRelative(0, 1, 0).isPassable() || !b.getRelative(0, 2, 0).isPassable()) return null;
        return new Location(w, x + 0.5, b.getY() + 1, z + 0.5, (float) (Math.random() * 360), 0f);
    }

    private void nascer(World w, Campo c) {
        if (pl.protegendo(w, c.x, w.getHighestBlockYAt(c.x, c.z), c.z) != null) {
            vazioEm.put(c.chave, System.currentTimeMillis() + 60 * 60_000L);   // dentro de uma masmorra: nao usa
            return;
        }
        Location centro = chao(w, c.x, c.z);
        if (centro == null) {
            // em cima de estrutura (fogueira, monolito...) o centro pode nao servir: procura em volta
            for (int t = 0; t < 8 && centro == null; t++) centro = chao(w, c.x + (t % 2 == 0 ? 3 : -3) * (1 + t / 4), c.z + (t % 4 < 2 ? 3 : -3));
        }
        if (centro == null || centro.getY() < GeradorMundo.MAR + 1) {
            vazioEm.put(c.chave, System.currentTimeMillis() + 30 * 60_000L);   // agua / sem chao: tenta bem depois
            return;
        }
        if (mm.gerador.ehRio(c.x, c.z, Math.hypot(c.x, c.z))) {
            vazioEm.put(c.chave, System.currentTimeMillis() + 60 * 60_000L);
            return;
        }
        c.y = centro.getBlockY();
        Random r = new Random(c.chave ^ System.nanoTime());
        if (c.tipo == Tipo.CAMPO) enfeitar(w, c, r);
        String bioma = w.getBiome(c.x, c.y, c.z).getKey().getKey();
        List<EntityType> tipos = tipos(c, bioma, r);
        int n = 0;
        int chanceElite = switch (c.nivel) {
            case 1 -> 18;
            case 2 -> 30;
            case 3 -> 45;
            default -> 60;
        };
        int eliteEm = r.nextInt(100) < pl.getConfig().getInt("campos.chance-elite", chanceElite) ? r.nextInt(tipos.size()) : -1;
        for (int i = 0; i < tipos.size(); i++) {
            EntityType t = tipos.get(i);
            Location l;
            if (c.tipo == Tipo.TORRE && i < 2) {
                // os dois primeiros ficam em cima da torre
                l = chao(w, c.x + (i == 0 ? 0 : 1), c.z + (i == 0 ? 0 : 1));
                if (l == null) l = chao(w, c.x, c.z);
            } else {
                int dx = r.nextInt(RAIO_CAMPO) - RAIO_CAMPO / 2, dz = r.nextInt(RAIO_CAMPO) - RAIO_CAMPO / 2;
                l = chao(w, c.x + dx, c.z + dz);
            }
            if (l == null || Math.abs(l.getBlockY() - c.y) > 9) continue;
            final boolean elite = i == eliteEm;
            try {
                Entity e = w.spawnEntity(l, t, CreatureSpawnEvent.SpawnReason.CUSTOM, x -> preparar(x, c, r, elite));
                if (e.isValid()) {
                    c.mobs.add(e.getUniqueId());
                    n++;
                }
            } catch (Throwable ignored) {
            }
        }
        c.vivos = n;
        if (n == 0) vazioEm.put(c.chave, System.currentTimeMillis());
        else vazioEm.remove(c.chave);
    }

    // ------------------------------------------------------------------ enfeites dos campos do mato

    private void por(World w, int x, int y, int z, String dado) {
        try {
            w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(dado), false);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private int topo(World w, int x, int z) {
        Block b = w.getHighestBlockAt(x, z);
        while (b.getY() > w.getMinHeight() && (b.getType() == Material.LIGHT || !b.getType().isSolid())) b = b.getRelative(0, -1, 0);
        return b.getY();
    }

    /** Fogueira, barraca, estacas com lanterna (e caveiras/ossos nos niveis altos). So uma vez por chunk. */
    private void enfeitar(World w, Campo c, Random r) {
        if (!pl.getConfig().getBoolean("campos.enfeites", true)) return;
        var chunk = w.getChunkAt(c.x >> 4, c.z >> 4);
        if (chunk.getPersistentDataContainer().has(kProps, PersistentDataType.BYTE)) return;
        chunk.getPersistentDataContainer().set(kProps, PersistentDataType.BYTE, (byte) 1);
        int y0 = topo(w, c.x, c.z);
        Material solo = w.getBlockAt(c.x, y0, c.z).getType();
        if (solo == Material.WATER || solo == Material.ICE || solo == Material.SAND && c.nivel == 1 && r.nextBoolean()) return;
        // fogueira no centro, cercada de pedra
        por(w, c.x, y0 + 1, c.z, c.nivel >= 4 ? "minecraft:soul_campfire[lit=true]" : "minecraft:campfire[lit=true]");
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int yy = topo(w, c.x + o[0], c.z + o[1]);
            if (Math.abs(yy - y0) <= 1) por(w, c.x + o[0], yy, c.z + o[1], r.nextBoolean() ? "minecraft:cobblestone" : "minecraft:mossy_cobblestone");
        }
        // barraca (3 de largura) de um lado
        int bx = c.x + (r.nextBoolean() ? 4 : -4), bz = c.z + (r.nextBoolean() ? 3 : -3);
        int by = topo(w, bx, bz);
        if (Math.abs(by - y0) <= 2) {
            String la = c.nivel >= 3 ? "minecraft:black_wool" : c.nivel == 2 ? "minecraft:brown_wool" : "minecraft:white_wool";
            String esc = c.nivel >= 3 ? "minecraft:dark_oak_stairs" : "minecraft:spruce_stairs";
            for (int dx = -1; dx <= 1; dx++) {
                por(w, bx + dx, by + 1, bz - 1, esc + "[facing=south]");
                por(w, bx + dx, by + 1, bz + 1, esc + "[facing=north]");
                por(w, bx + dx, by + 2, bz, la);
            }
            por(w, bx + 2, by + 1, bz, la);
            por(w, bx - 2, by + 1, bz, "minecraft:air");
            por(w, bx, by + 1, bz, "minecraft:air");
            por(w, bx + (r.nextBoolean() ? 1 : -1), by + 1, bz, c.nivel >= 2 ? "minecraft:barrel[facing=up]" : "minecraft:hay_block");
        }
        // estacas com lanterna nas diagonais; caveiras e ossos a partir do nivel 3
        for (int[] o : new int[][]{{5, 5}, {-5, -5}, {5, -5}, {-5, 5}}) {
            if (r.nextInt(3) == 0) continue;
            int x = c.x + o[0], z = c.z + o[1];
            int yy = topo(w, x, z);
            if (Math.abs(yy - y0) > 3) continue;
            por(w, x, yy + 1, z, "minecraft:spruce_fence");
            if (c.nivel >= 3 && r.nextBoolean()) {
                por(w, x, yy + 2, z, "minecraft:spruce_fence");
                por(w, x, yy + 3, z, c.nivel >= 4 ? "minecraft:wither_skeleton_skull" : "minecraft:skeleton_skull");
            } else por(w, x, yy + 2, z, c.nivel >= 4 ? "minecraft:soul_lantern" : "minecraft:lantern");
        }
        if (c.nivel >= 3) {
            int x = c.x + r.nextInt(7) - 3, z = c.z + r.nextInt(7) - 3;
            if (Math.abs(x - c.x) + Math.abs(z - c.z) > 1) por(w, x, topo(w, x, z) + 1, z, "minecraft:bone_block[axis=" + (r.nextBoolean() ? "x" : "z") + "]");
        }
    }

    // ------------------------------------------------------------------ composicao

    private static EntityType sorteio(Random r, EntityType... op) {
        return op[r.nextInt(op.length)];
    }

    private List<EntityType> tipos(Campo c, String bioma, Random r) {
        List<EntityType> l = new ArrayList<>();
        int nivel = c.nivel;
        int n = switch (nivel) {
            case 1 -> 3 + r.nextInt(2);
            case 2 -> 4 + r.nextInt(2);
            case 3 -> 4 + r.nextInt(3);
            default -> 5 + r.nextInt(3);
        };
        switch (c.tipo) {
            case RUINA -> {
                for (int i = 0; i < n; i++) l.add(nivel == 1 ? sorteio(r, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON)
                        : nivel == 2 ? sorteio(r, EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.ZOMBIE_VILLAGER)
                        : nivel == 3 ? sorteio(r, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIE_VILLAGER, EntityType.CAVE_SPIDER)
                        : sorteio(r, EntityType.WITHER_SKELETON, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.BOGGED, EntityType.ZOGLIN));
            }
            case TORRE -> {
                l.add(nivel >= 4 ? EntityType.SKELETON : nivel >= 2 ? EntityType.STRAY : EntityType.SKELETON);
                l.add(nivel >= 3 ? EntityType.PILLAGER : EntityType.SKELETON);
                for (int i = 2; i < n; i++) l.add(nivel == 1 ? sorteio(r, EntityType.ZOMBIE, EntityType.SKELETON)
                        : nivel == 2 ? sorteio(r, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER)
                        : nivel == 3 ? sorteio(r, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.SKELETON)
                        : sorteio(r, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.WITHER_SKELETON, EntityType.BOGGED));
            }
            case MONOLITO -> {
                for (int i = 0; i < n; i++) l.add(nivel == 1 ? sorteio(r, EntityType.SLIME, EntityType.SPIDER, EntityType.ZOMBIE)
                        : nivel == 2 ? sorteio(r, EntityType.SLIME, EntityType.SPIDER, EntityType.SKELETON, EntityType.ENDERMAN)
                        : nivel == 3 ? sorteio(r, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.SPIDER, EntityType.PHANTOM)
                        : sorteio(r, EntityType.ENDERMAN, EntityType.ENDERMAN, EntityType.PHANTOM, EntityType.SHULKER));
            }
            case ACAMPAMENTO -> {
                for (int i = 0; i < n; i++) l.add(nivel == 1 ? sorteio(r, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.PILLAGER)
                        : nivel == 2 ? sorteio(r, EntityType.PILLAGER, EntityType.ZOMBIE, EntityType.SKELETON)
                        : nivel == 3 ? sorteio(r, EntityType.PILLAGER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.WITCH)
                        : sorteio(r, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.EVOKER));
                if (nivel >= 4 && r.nextInt(3) == 0) l.add(EntityType.RAVAGER);
            }
            case CIRCULO -> {
                l.add(EntityType.WITCH);
                for (int i = 1; i < n; i++) l.add(nivel == 1 ? sorteio(r, EntityType.ZOMBIE, EntityType.SPIDER)
                        : nivel == 2 ? sorteio(r, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SLIME)
                        : nivel == 3 ? sorteio(r, EntityType.WITCH, EntityType.CAVE_SPIDER, EntityType.ZOMBIE, EntityType.VEX)
                        : sorteio(r, EntityType.WITCH, EntityType.EVOKER, EntityType.VEX, EntityType.BOGGED));
            }
            case ALTAR -> {
                for (int i = 0; i < n; i++) l.add(nivel == 1 ? sorteio(r, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIE)
                        : nivel == 2 ? sorteio(r, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.WITCH)
                        : nivel == 3 ? sorteio(r, EntityType.WITHER_SKELETON, EntityType.SKELETON, EntityType.WITCH, EntityType.ZOMBIE)
                        : sorteio(r, EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.EVOKER, EntityType.MAGMA_CUBE));
            }
            case OSSADA -> {
                for (int i = 0; i < n + 1; i++) l.add(sorteio(r, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.CAVE_SPIDER, EntityType.BOGGED, EntityType.SKELETON));
            }
            case TORRE_QUEBRADA -> {
                for (int i = 0; i < n; i++) l.add(nivel == 3 ? sorteio(r, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.SKELETON, EntityType.ZOMBIE)
                        : sorteio(r, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.WITHER_SKELETON, EntityType.BREEZE));
                if (r.nextBoolean()) l.add(EntityType.RAVAGER);
            }
            default -> {
                switch (nivel) {
                    case 1 -> {
                        for (int i = 0; i < n; i++) l.add(r.nextInt(5) < 2 ? EntityType.ZOMBIE : r.nextBoolean() ? EntityType.SKELETON : EntityType.SPIDER);
                    }
                    case 2 -> {
                        for (int i = 0; i < n; i++) {
                            if (bioma.contains("desert") || bioma.contains("badlands") || bioma.contains("savanna")) l.add(r.nextBoolean() ? EntityType.HUSK : EntityType.SKELETON);
                            else if (bioma.contains("snow") || bioma.contains("ice") || bioma.contains("frozen")) l.add(r.nextInt(3) == 0 ? EntityType.ZOMBIE : EntityType.STRAY);
                            else if (bioma.contains("swamp") || bioma.contains("mangrove")) l.add(r.nextInt(3) == 0 ? EntityType.WITCH : r.nextBoolean() ? EntityType.SLIME : EntityType.BOGGED);
                            else if (bioma.contains("beach") || bioma.contains("ocean")) l.add(EntityType.DROWNED);
                            else l.add(r.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON);
                        }
                    }
                    case 3 -> {
                        for (int i = 0; i < n; i++) {
                            int k = r.nextInt(10);
                            l.add(k < 3 ? EntityType.PILLAGER : k < 5 ? EntityType.VINDICATOR : k < 6 ? EntityType.WITCH : k < 8 ? EntityType.CAVE_SPIDER : EntityType.ZOMBIE);
                        }
                    }
                    default -> {
                        boolean vulcao = bioma.contains("basalt") || bioma.contains("nether") || bioma.contains("badlands");
                        for (int i = 0; i < n; i++) {
                            int k = r.nextInt(12);
                            l.add(vulcao && k < 5 ? (k < 3 ? EntityType.BLAZE : EntityType.MAGMA_CUBE)
                                    : k < 4 ? EntityType.WITHER_SKELETON : k < 6 ? EntityType.BLAZE : k < 8 ? EntityType.ENDERMAN : k < 10 ? EntityType.BOGGED : EntityType.BREEZE);
                        }
                        if (r.nextInt(3) == 0) l.add(EntityType.RAVAGER);
                    }
                }
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ preparo dos monstros

    private void preparar(Entity e, Campo c, Random r, boolean elite) {
        e.getPersistentDataContainer().set(kCampo, PersistentDataType.LONG, c.chave);
        // o LevelledMobs nao poe nivel neles (pela distancia do centro eles ficavam muito fortes no nivel 1)
        e.getPersistentDataContainer().set(NamespacedKey.fromString("levelledmobs:nolevel"), PersistentDataType.INTEGER, 1);
        e.setPersistent(false);
        if (e instanceof LivingEntity le) {
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);
            String nome = nome(e.getType(), c);
            String cor = switch (c.nivel) {
                case 1 -> "§a";
                case 2 -> "§e";
                case 3 -> "§6";
                default -> "§c";
            };
            // vida e dano sobem com o nivel do anel
            double fatorVida = switch (c.nivel) {
                case 1 -> 0.85;
                case 2 -> 1.25;
                case 3 -> 1.75;
                default -> 2.3;
            };
            double fatorDano = switch (c.nivel) {
                case 1 -> 1.0;
                case 2 -> 1.25;
                case 3 -> 1.6;
                default -> 2.0;
            };
            if (elite) {
                fatorVida *= 3.0;
                fatorDano *= 1.5;
                e.getPersistentDataContainer().set(kElite, PersistentDataType.BYTE, (byte) 1);
                le.setGlowing(true);
                var escala = le.getAttribute(Attribute.SCALE);
                if (escala != null) escala.setBaseValue(escala.getBaseValue() * (e.getType() == EntityType.RAVAGER ? 1.15 : 1.3));
                var kb = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                if (kb != null) kb.setBaseValue(Math.min(1.0, kb.getBaseValue() + 0.5));
                le.customName(Masmorras.txt("§6§l★ " + tituloElite(c) + " §7[" + c.nivel + "]"));
            } else le.customName(Masmorras.txt(cor + nome + " §7[" + c.nivel + "]"));
            le.setCustomNameVisible(true);
            var vida = le.getAttribute(Attribute.MAX_HEALTH);
            if (vida != null) {
                vida.setBaseValue(vida.getBaseValue() * fatorVida);
                le.setHealth(vida.getValue());
            }
            var dano = le.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dano != null && fatorDano > 1.0) dano.setBaseValue(dano.getBaseValue() * fatorDano);
            equipar(le, c, r, elite);
        }
        if (e instanceof Slime s) s.setSize(c.nivel >= 3 ? 3 : 2);
        if (e instanceof org.bukkit.entity.Ageable a) a.setAdult();
        if (e instanceof org.bukkit.entity.Phantom ph) ph.setSize(c.nivel >= 4 ? 3 : 1);
    }

    /** Equipamento por nivel: 1 nada, 2 couro/corrente, 3 ferro, 4 diamante encantado. Arqueiros sempre com arco. */
    private void equipar(LivingEntity le, Campo c, Random r, boolean elite) {
        if (le.getEquipment() == null) return;
        var eq = le.getEquipment();
        EntityType t = le.getType();
        boolean humanoide = t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.ZOMBIE_VILLAGER || t == EntityType.SKELETON
                || t == EntityType.STRAY || t == EntityType.BOGGED || t == EntityType.WITHER_SKELETON || t == EntityType.DROWNED;
        if (t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.BOGGED) {
            ItemStack arco = new ItemStack(Material.BOW);
            if (c.nivel >= 3) arco.addUnsafeEnchantment(Enchantment.POWER, c.nivel - 2);
            if (elite) arco.addUnsafeEnchantment(Enchantment.PUNCH, 1);
            eq.setItemInMainHand(arco);
            eq.setItemInMainHandDropChance(0f);
        }
        if (t == EntityType.WITHER_SKELETON) {
            eq.setItemInMainHand(new ItemStack(c.nivel >= 4 ? Material.IRON_SWORD : Material.STONE_SWORD));
            eq.setItemInMainHandDropChance(0f);
        }
        if (!humanoide) return;
        int nivelEq = c.nivel + (elite ? 1 : 0);
        Material elmo = null, peito = null;
        if (nivelEq == 2) {
            elmo = r.nextBoolean() ? Material.LEATHER_HELMET : Material.CHAINMAIL_HELMET;
        } else if (nivelEq == 3) {
            elmo = Material.IRON_HELMET;
            peito = r.nextBoolean() ? Material.IRON_CHESTPLATE : Material.CHAINMAIL_CHESTPLATE;
        } else if (nivelEq >= 4) {
            elmo = Material.DIAMOND_HELMET;
            peito = Material.DIAMOND_CHESTPLATE;
        }
        if (elmo != null) {
            ItemStack it = new ItemStack(elmo);
            if (nivelEq >= 4) it.addUnsafeEnchantment(Enchantment.PROTECTION, nivelEq >= 5 ? 3 : 2);
            eq.setHelmet(it);
            eq.setHelmetDropChance(0f);
        }
        if (peito != null) {
            ItemStack it = new ItemStack(peito);
            if (nivelEq >= 4) it.addUnsafeEnchantment(Enchantment.PROTECTION, nivelEq >= 5 ? 3 : 2);
            eq.setChestplate(it);
            eq.setChestplateDropChance(0f);
        }
        if ((t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.ZOMBIE_VILLAGER) && c.nivel >= 3 && eq.getItemInMainHand().getType().isAir()) {
            eq.setItemInMainHand(new ItemStack(c.nivel >= 4 ? Material.DIAMOND_SWORD : Material.IRON_SWORD));
            eq.setItemInMainHandDropChance(0f);
        }
    }

    private static String tituloElite(Campo c) {
        return switch (c.tipo) {
            case TORRE -> "Sentinela";
            case RUINA -> "Senhor da Ruína";
            case MONOLITO -> "Guardião";
            case ACAMPAMENTO -> "Capitão";
            case CIRCULO -> "Xamã";
            case ALTAR -> "Sumo Sacerdote";
            case OSSADA -> "Matriarca";
            case TORRE_QUEBRADA -> "Comandante";
            default -> "Chefe";
        };
    }

    private static String nome(EntityType t, Campo c) {
        int nivel = c.nivel;
        return switch (t) {
            case ZOMBIE -> c.tipo == Tipo.ALTAR ? "Cultista" : c.tipo == Tipo.RUINA ? "Morador da Ruína" : nivel >= 3 ? "Zumbi Blindado" : "Zumbi Errante";
            case ZOMBIE_VILLAGER -> "Aldeão Amaldiçoado";
            case SKELETON -> c.tipo == Tipo.TORRE ? "Vigia da Torre" : nivel >= 2 ? "Arqueiro Veterano" : "Arqueiro Perdido";
            case SPIDER -> c.tipo == Tipo.OSSADA ? "Aranha da Ossada" : "Aranha do Mato";
            case HUSK -> "Zumbi do Deserto";
            case STRAY -> "Arqueiro Gélido";
            case SLIME -> "Gosma Brejeira";
            case BOGGED -> "Arqueiro Atolado";
            case DROWNED -> "Zumbi Afogado";
            case WITCH -> c.tipo == Tipo.CIRCULO ? "Bruxa do Círculo" : nivel >= 3 ? "Bruxa das Sombras" : "Bruxo do Pântano";
            case PILLAGER -> c.tipo == Tipo.TORRE ? "Besteiro da Torre" : nivel >= 3 ? "Saqueador Veterano" : "Saqueador";
            case VINDICATOR -> "Vingador";
            case EVOKER -> "Invocador";
            case VEX -> "Espírito Irritado";
            case CAVE_SPIDER -> "Aranha Venenosa";
            case WITHER_SKELETON -> c.tipo == Tipo.ALTAR ? "Sacerdote Wither" : "Esqueleto Wither";
            case BLAZE -> "Blaze Errante";
            case MAGMA_CUBE -> "Cubo de Magma";
            case ENDERMAN -> c.tipo == Tipo.MONOLITO ? "Vigia do Monólito" : "Caçador do Vazio";
            case PHANTOM -> "Fantasma do Monólito";
            case SHULKER -> "Casca do Vazio";
            case BREEZE -> "Brisa Furiosa";
            case RAVAGER -> "Devastador";
            case ZOGLIN -> "Zoglin Faminto";
            default -> "Monstro";
        };
    }

    // ------------------------------------------------------------------ eventos

    /** Monstros do campo nao pegam fogo no sol (senao os zumbis e esqueletos morrem sozinhos de dia). */
    @EventHandler(ignoreCancelled = true)
    public void sol(org.bukkit.event.entity.EntityCombustEvent e) {
        if (e.getClass() == org.bukkit.event.entity.EntityCombustEvent.class && e.getEntity().getPersistentDataContainer().has(kCampo)) e.setCancelled(true);
    }

    @EventHandler
    public void morreu(EntityDeathEvent e) {
        Long k = e.getEntity().getPersistentDataContainer().get(kCampo, PersistentDataType.LONG);
        if (k == null) return;
        boolean elite = e.getEntity().getPersistentDataContainer().has(kElite, PersistentDataType.BYTE);
        Campo c = campos.get(k);
        Player killer = e.getEntity().getKiller();
        if (c == null) {
            vazioEm.put(k, System.currentTimeMillis());
            return;
        }
        c.mobs.remove(e.getEntity().getUniqueId());
        c.vivos--;
        boolean limpou = c.mobs.isEmpty();
        if (limpou) vazioEm.put(k, System.currentTimeMillis());
        if (killer == null) return;
        int mult = elite ? pl.getConfig().getInt("campos.elite-multiplicador", 6) : 1;
        e.setDroppedExp(e.getDroppedExp() + 3 * c.nivel * mult);
        long cor = pl.getConfig().getLong("campos.coronas-por-monstro", 1) * c.nivel * mult;
        if (cor > 0) pl.coronas.dar(killer, pl.coronas.comVip(killer, cor), true);
        pl.darXpClasses(killer, pl.getConfig().getDouble("campos.xp-classes", 6) * c.nivel * mult, false);
        if (elite) {
            killer.sendMessage(Masmorras.txt("§6§l★ Elite derrotado! §e+" + cor + " coronas"));
            killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        }
        if (limpou && pl.getConfig().getBoolean("campos.premio-limpar", true)) {
            long bonus = pl.getConfig().getLong("campos.coronas-limpar", 4) * c.nivel;
            pl.coronas.dar(killer, pl.coronas.comVip(killer, bonus), true);
            pl.darXpClasses(killer, pl.getConfig().getDouble("campos.xp-classes", 6) * c.nivel * 2, false);
            String lugar = switch (c.tipo) {
                case RUINA -> "Ruína";
                case TORRE -> "Torre de Vigia";
                case MONOLITO -> "Monólito";
                case ACAMPAMENTO -> "Acampamento abandonado";
                case CIRCULO -> "Círculo de Pedras";
                case ALTAR -> "Altar";
                case OSSADA -> "Ossada";
                case TORRE_QUEBRADA -> "Torre Quebrada";
                default -> "Acampamento";
            };
            Location l = new Location(e.getEntity().getWorld(), c.x, c.y, c.z);
            for (Player p : e.getEntity().getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(l) > 50 * 50) continue;
                p.sendMessage(Masmorras.txt("§a§l✔ " + lugar + " limpa! §7" + killer.getName() + " ganhou §e+" + bonus + " coronas§7. Volta em 4 min."));
            }
            killer.playSound(killer.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        }
    }
}
