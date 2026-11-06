package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataType;

/**
 * Spawn das Masmorras: mundo vazio com uma praca protegida (ninguem quebra, coloca, se machuca ou cai),
 * o Mercador Lendario e uma esmeralda que leva de volta ao spawn principal. No spawn principal o Guia das
 * Masmorras (villager) leva para ca; tambem: /masmorras spawn e o item Masmorras.
 */
final class Hub implements Listener {

    static final String MUNDO = "masmorras_spawn";
    static final int Y = 100, R = 22;
    private final Masmorras pl;
    World mundo;

    Hub(Masmorras pl) {
        this.pl = pl;
    }

    static final class Vazio extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override public Biome getBiome(WorldInfo info, int x, int y, int z) { return Biome.THE_VOID; }
                @Override public List<Biome> getBiomes(WorldInfo info) { return List.of(Biome.THE_VOID); }
            };
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, Y + 1, 0.5, 180f, 0f);
        }
    }

    boolean ehHub(World w) {
        return w != null && w.getName().equals(MUNDO);
    }

    Location spawn() {
        return new Location(mundo, 0.5, Y + 1, 0.5, 180f, 0f);
    }

    void iniciar() {
        if (!pl.getConfig().getBoolean("hub.ativo", true)) return;
        mundo = new WorldCreator(MUNDO).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        mundo.setGameRule(GameRule.MOB_GRIEFING, false);
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
        mundo.setTime(18000);
        mundo.setSpawnLocation(spawn());
        File feito = new File(pl.getDataFolder(), "spawn-masmorras-v1.txt");
        if (!feito.exists()) {
            construir();
            try {
                Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (IOException ignored) {
            }
        }
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) mundo.getChunkAt(cx, cz).load(true);
        File v2 = new File(pl.getDataFolder(), "spawn-masmorras-v2.txt");
        if (!v2.exists()) {
            try {
                construirV2();
                Files.writeString(v2.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Spawn das Masmorras v2 nao terminou: " + ex);
            }
        }
        File v3 = new File(pl.getDataFolder(), "spawn-masmorras-v3.txt");
        if (!v3.exists()) {
            try {
                construirV3();
                Files.writeString(v3.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Spawn das Masmorras v3 nao terminou: " + ex);
            }
        }
        enfeites.clear();
        for (Entity e : mundo.getEntities())
            if (e instanceof org.bukkit.entity.ItemDisplay d && Integer.valueOf(1).equals(d.getPersistentDataContainer().get(kEnfeite(), PersistentDataType.INTEGER))) enfeites.add(d);
        Bukkit.getScheduler().runTaskTimer(pl, this::garantirNpcs, 100L, 200L);
        Bukkit.getScheduler().runTaskTimer(pl, this::girar, 60L, 10L);
    }

    // ------------------------------------------------------------------ versao 2: portais por nivel, salao dos chefes, bonecos de treino

    /** Portais de nivel (leste): levam para a entrada de uma masmorra daquele nivel no mundo normal. */
    static final int[][] PORTAIS = {{1, -12}, {2, -4}, {3, 4}, {4, 12}}; // nivel, z
    static final String[] COR_NIVEL = {"", "lime", "yellow", "orange", "red"};
    static final String[] COR_TXT = {"", "§a", "§e", "§6", "§c"};
    static final int PX = 19;
    private final List<org.bukkit.entity.ItemDisplay> enfeites = new java.util.ArrayList<>();
    private final java.util.Map<java.util.UUID, Long> esperaPortal = new java.util.HashMap<>();
    private float giro;

    private org.bukkit.NamespacedKey kEnfeite() {
        return new org.bukkit.NamespacedKey(pl, "hub_enfeite");
    }

    private void construirV2() {
        // se uma tentativa anterior parou no meio, tira os enfeites dela (nada fica repetido)
        for (Entity e : mundo.getEntities()) if (e.getPersistentDataContainer().has(kEnfeite(), PersistentDataType.INTEGER)) e.remove();
        enfeites.clear();
        // portais coloridos por nivel no lado leste (o portal roxo do jogo; nao leva ao Nether)
        for (int[] pt : PORTAIS) {
            int n = pt[0], cz = pt[1];
            for (int dz = -2; dz <= 2; dz++)
                for (int y = 1; y <= 6; y++) {
                    boolean borda = Math.abs(dz) == 2 || y == 6;
                    por(PX, Y + y, cz + dz, borda ? "minecraft:" + COR_NIVEL[n] + "_concrete" : "minecraft:nether_portal[axis=z]");
                }
            for (int dz = -2; dz <= 2; dz++) por(PX - 1, Y, cz + dz, "minecraft:" + COR_NIVEL[n] + "_glazed_terracotta");
            for (int dz = -2; dz <= 2; dz++) for (int y = 1; y <= 7; y++) {
                Block b = mundo.getBlockAt(PX + 1, Y + y, cz + dz);
                if (b.getType().isAir()) b.setBlockData(Bukkit.createBlockData("minecraft:deepslate_bricks"), false);
            }
            texto(new Location(mundo, PX - 0.6, Y + 8.4, cz + 0.5), COR_TXT[n] + "§lNÍVEL " + n + "\n§7" + nomesDoNivel(n) + "\n§fentre: vai para uma masmorra", 1.1f);
        }
        texto(new Location(mundo, PX - 3.5, Y + 11.5, 0.5), "§c§lPORTAIS DAS MASMORRAS\n§7escolha o nível e entre", 1.6f);
        // salao dos chefes (oeste): um pedestal para cada chefe, com o icone girando e a particularidade
        List<Tipo> tipos = new java.util.ArrayList<>();
        for (Tipo t : Tipo.TODOS.values()) if (!t.secreta) tipos.add(t);
        tipos.sort(java.util.Comparator.comparingInt(t -> t.nivel));
        int i = 0;
        for (Tipo t : tipos) {
            int x = i < 9 ? -19 : -14, z = -16 + (i % 9) * 4;
            i++;
            por(x, Y + 1, z, "minecraft:polished_blackstone_bricks");
            por(x, Y + 2, z, "minecraft:" + (t.nivel >= 5 ? "purpur_pillar" : COR_NIVEL[Math.min(4, t.nivel)] + "_concrete"));
            item(new Location(mundo, x + 0.5, Y + 3.4, z + 0.5), new org.bukkit.inventory.ItemStack(Masmorras.icone(t)), 0.9f);
            texto(new Location(mundo, x + 0.5, Y + 4.4, z + 0.5), "§c§l" + t.chefeNome + "\n§7" + t.nome + " §8• " + COR_TXT[Math.min(4, t.nivel)] + "nível " + t.nivel
                    + (t.esp != null && t.esp != Tipo.Esp.NENHUMA ? "\n§d" + t.esp.desc : ""), 0.55f);
        }
        texto(new Location(mundo, -16.5, Y + 9.5, 0.5), "§4§lSALÃO DOS CHEFES\n§7conheça quem te espera", 1.6f);
        // o pedestal misterioso do Herobrine
        por(-9, Y + 1, 0, "minecraft:obsidian");
        por(-9, Y + 2, 0, "minecraft:crying_obsidian");
        item(new Location(mundo, -8.5, Y + 3.4, 0.5), Masmorras.cabecaHerobrine(), 1.0f);
        texto(new Location(mundo, -8.5, Y + 4.6, 0.5), "§4§l???\n§8§o...ele nunca foi embora.", 0.8f);
        // campo de treino (sudeste): chao de terra batida, cerca e 3 bonecos que mostram o dano
        for (int x = 8; x <= 17; x++)
            for (int z = 9; z <= 17; z++) {
                boolean borda = x == 8 || x == 17 || z == 9 || z == 17;
                por(x, Y, z, borda ? "minecraft:stripped_spruce_wood" : (((x + z) & 1) == 0 ? "minecraft:coarse_dirt" : "minecraft:packed_mud"));
                if (borda && !(z == 9 && (x == 12 || x == 13))) por(x, Y + 1, z, "minecraft:spruce_fence");
            }
        mundo.getBlockAt(12, Y + 1, 9).setType(Material.AIR, false);
        mundo.getBlockAt(13, Y + 1, 9).setType(Material.AIR, false);
        texto(new Location(mundo, 12.5, Y + 4.2, 9.5), "§e§lCAMPO DE TREINO\n§7bata nos bonecos e veja o seu dano", 1.0f);
        // fogueiras de alma nos cantos e letreiro no meio
        for (int[] c : new int[][] {{-R + 2, -R + 2}, {R - 2, -R + 2}, {-R + 2, R - 2}, {R - 2, R - 2}}) {
            por(c[0], Y + 1, c[1], "minecraft:chiseled_polished_blackstone");
            por(c[0], Y + 2, c[1], "minecraft:soul_campfire[lit=true,signal_fire=false]");
        }
        texto(new Location(mundo, 0.5, Y + 6, 0.5), "§6§lSPAWN DAS MASMORRAS\n§eMercador Lendário §7ao norte • §cPortais §7a leste\n§4Salão dos Chefes §7a oeste • §eTreino §7a sudeste", 1.3f);
        pl.getLogger().info("Spawn das Masmorras: portais de nivel, salao dos chefes e campo de treino prontos.");
    }

    /** Raio novo da praca (v3): maior, para caber mais bonecos de treino. */
    static final int R2 = 34;

    private String pisoV1(int x, int z, int borda) {
        double d = Math.sqrt(x * x + z * z);
        if (Math.abs(x) == borda || Math.abs(z) == borda) return "minecraft:chiseled_deepslate";
        if (d <= 2.5) return "minecraft:gold_block";
        if (d <= 4.5) return "minecraft:polished_blackstone_bricks";
        if (x == 0 || z == 0) return "minecraft:polished_blackstone";
        return ((x + z) & 1) == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles";
    }

    /**
     * v3: tira os portais de nivel (eles foram para o spawn principal), aumenta a praca de 45x45 para 69x69 e faz um
     * campo de treino grande com 12 bonecos. O resto (Mercador Lendario, Salao dos Chefes, Herobrine, volta) fica.
     */
    private void construirV3() {
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) mundo.getChunkAt(cx, cz).load(true);
        // tira os portais (moldura, portal roxo, parede de tras e o degrau colorido)
        for (int[] pt : PORTAIS) {
            int cz = pt[1];
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 1; y <= 7; y++) {
                    Block b = mundo.getBlockAt(PX, Y + y, cz + dz);
                    if (b.getType() != Material.LIGHT) b.setType(Material.AIR, false);
                    Block c = mundo.getBlockAt(PX + 1, Y + y, cz + dz);
                    if (c.getType() == Material.DEEPSLATE_BRICKS) c.setType(Material.AIR, false);
                }
                por(PX - 1, Y, cz + dz, pisoV1(PX - 1, cz + dz, R2));
            }
        }
        // letreiros dos portais e o letreiro do meio (falava dos portais) saem
        for (Entity e : mundo.getEntities()) {
            if (!(e instanceof org.bukkit.entity.TextDisplay)) continue;
            Location l = e.getLocation();
            boolean portal = l.getX() > 14 && l.getY() > Y + 7;
            boolean meio = Math.abs(l.getX() - 0.5) < 0.6 && Math.abs(l.getZ() - 0.5) < 0.6 && l.getY() > Y + 5;
            boolean treino = Math.abs(l.getX() - 12.5) < 0.6 && Math.abs(l.getZ() - 9.5) < 0.6;
            if (portal || meio || treino) e.remove();
        }
        // muro velho da borda sai (a praca cresce)
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                if (Math.abs(x) != R && Math.abs(z) != R) continue;
                for (int y = 1; y <= 3; y++) {
                    Block b = mundo.getBlockAt(x, Y + y, z);
                    Material m = b.getType();
                    if (m == Material.DEEPSLATE_BRICKS || m == Material.DEEPSLATE_BRICK_WALL || m == Material.SOUL_LANTERN) b.setType(Material.AIR, false);
                }
            }
        // piso novo ate R2 (o que ja tinha dentro fica, menos a borda velha)
        for (int x = -R2; x <= R2; x++)
            for (int z = -R2; z <= R2; z++) {
                boolean dentroVelho = Math.abs(x) < R && Math.abs(z) < R;
                if (!dentroVelho) {
                    por(x, Y, z, pisoV1(x, z, R2));
                    por(x, Y - 1, z, "minecraft:deepslate_bricks");
                }
                if (Math.abs(x) == R2 || Math.abs(z) == R2) {
                    boolean poste = (Math.abs(x) == R2 && z % 6 == 0) || (Math.abs(z) == R2 && x % 6 == 0) || (Math.abs(x) == R2 && Math.abs(z) == R2);
                    if (poste) {
                        por(x, Y + 1, z, "minecraft:deepslate_bricks");
                        por(x, Y + 2, z, "minecraft:deepslate_bricks");
                        por(x, Y + 3, z, "minecraft:soul_lantern");
                    } else por(x, Y + 1, z, "minecraft:deepslate_brick_wall");
                }
            }
        // a borda velha vira piso normal (o muro saiu)
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++)
                if (Math.abs(x) == R || Math.abs(z) == R) {
                    Block b = mundo.getBlockAt(x, Y, z);
                    if (b.getType() == Material.CHISELED_DEEPSLATE) por(x, Y, z, pisoV1(x, z, R2));
                }
        // fogueiras velhas dos cantos vao para os cantos novos
        for (int[] c : new int[][] {{-R + 2, -R + 2}, {R - 2, -R + 2}, {-R + 2, R - 2}, {R - 2, R - 2}}) {
            mundo.getBlockAt(c[0], Y + 2, c[1]).setType(Material.AIR, false);
            mundo.getBlockAt(c[0], Y + 1, c[1]).setType(Material.AIR, false);
        }
        for (int[] c : new int[][] {{-R2 + 2, -R2 + 2}, {R2 - 2, -R2 + 2}, {-R2 + 2, R2 - 2}, {R2 - 2, R2 - 2}}) {
            por(c[0], Y + 1, c[1], "minecraft:chiseled_polished_blackstone");
            por(c[0], Y + 2, c[1], "minecraft:soul_campfire[lit=true,signal_fire=false]");
        }
        // luz invisivel na parte nova
        org.bukkit.block.data.type.Light luz = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(15);
        for (int x = -R2 + 2; x <= R2 - 2; x += 5)
            for (int z = -R2 + 2; z <= R2 - 2; z += 5) {
                Block b = mundo.getBlockAt(x, Y + 4, z);
                if (b.getType().isAir()) b.setBlockData(luz, false);
            }
        // campo de treino grande (sudeste): 24x23 com cerca e 12 bonecos
        for (Entity e : mundo.getEntities()) if (e.getPersistentDataContainer().has(kBoneco())) e.remove();
        for (int x = 8; x <= 17; x++)
            for (int z = 9; z <= 17; z++) mundo.getBlockAt(x, Y + 1, z).setType(Material.AIR, false);
        for (int x = TX1; x <= TX2; x++)
            for (int z = TZ1; z <= TZ2; z++) {
                boolean borda = x == TX1 || x == TX2 || z == TZ1 || z == TZ2;
                por(x, Y, z, borda ? "minecraft:stripped_spruce_wood" : (((x + z) & 1) == 0 ? "minecraft:coarse_dirt" : "minecraft:packed_mud"));
                Block cima = mundo.getBlockAt(x, Y + 1, z);
                if (cima.getType() != Material.LIGHT) cima.setType(Material.AIR, false);
                boolean entrada = (z == TZ1 && x >= 18 && x <= 21) || (x == TX1 && z >= 18 && z <= 21);
                if (borda && !entrada) por(x, Y + 1, z, "minecraft:spruce_fence");
            }
        for (int[] c : BONECOS) por(c[0], Y, c[1], "minecraft:hay_block");
        texto(new Location(mundo, 19.5, Y + 5, TZ1 + 0.5), "§e§lCAMPO DE TREINO\n§7bata nos bonecos e veja o seu dano\n§8(12 bonecos)", 1.2f);
        texto(new Location(mundo, 0.5, Y + 6, 0.5), "§6§lSPAWN DAS MASMORRAS\n§eMercador Lendário §7ao norte • §4Salão dos Chefes §7a oeste\n§eCampo de Treino §7a sudeste • §aVoltar §7ao sul", 1.3f);
        pl.getLogger().info("Spawn das Masmorras v3: praca maior (" + (R2 * 2 + 1) + "x" + (R2 * 2 + 1) + "), sem portais, 12 bonecos de treino.");
    }

    static final int TX1 = 8, TX2 = 32, TZ1 = 9, TZ2 = 32;
    static final int[][] BONECOS = {{12, 14}, {17, 14}, {22, 14}, {27, 14}, {12, 20}, {17, 20}, {22, 20}, {27, 20}, {12, 26}, {17, 26}, {22, 26}, {27, 26}};

    private static String nomesDoNivel(int n) {
        List<String> l = new java.util.ArrayList<>();
        for (Tipo t : Tipo.TODOS.values()) if (t.nivel == n && t.onde == Tipo.Onde.NORMAL && !t.secreta) l.add(t.nome);
        String s = String.join(", ", l);
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    private void texto(Location l, String t, float escala) {
        mundo.spawn(l, org.bukkit.entity.TextDisplay.class, x -> {
            x.text(Masmorras.txt(t));
            x.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(org.bukkit.Color.fromARGB(110, 0, 0, 0));
            x.setPersistent(true);
            x.getPersistentDataContainer().set(kEnfeite(), PersistentDataType.INTEGER, 2);
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(escala), new org.joml.AxisAngle4f()));
        });
    }

    private void item(Location l, org.bukkit.inventory.ItemStack it, float escala) {
        org.bukkit.entity.ItemDisplay d = mundo.spawn(l, org.bukkit.entity.ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            x.setPersistent(true);
            x.getPersistentDataContainer().set(kEnfeite(), PersistentDataType.INTEGER, 1);
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(escala), new org.joml.AxisAngle4f()));
        });
        enfeites.add(d);
    }

    private void girar() {
        if (mundo == null || mundo.getPlayers().isEmpty()) return;
        giro += 0.3f;
        for (org.bukkit.entity.ItemDisplay d : enfeites) {
            if (!d.isValid()) continue;
            org.bukkit.util.Transformation t = d.getTransformation();
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(10);
            d.setTransformation(new org.bukkit.util.Transformation(t.getTranslation(), new org.joml.AxisAngle4f(giro, 0, 1, 0), t.getScale(), new org.joml.AxisAngle4f()));
        }
    }

    /** Uma masmorra pronta daquele nivel no mundo normal (sorteada), ou null. */
    private Masmorra sortear(int nivel) {
        List<Masmorra> l = new java.util.ArrayList<>();
        String normal = pl.getConfig().getString("mundos.normal", "world");
        for (Masmorra m : pl.todas) if (m.tipo.nivel == nivel && !m.tipo.secreta && !m.demolindo && m.mundo.equals(normal)) l.add(m);
        return l.isEmpty() ? null : l.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(l.size()));
    }

    /** Leva para a entrada de uma masmorra sorteada daquele nivel (mundo normal) e da a bussola dela. */
    void irParaNivel(Player p, int nivel) {
        if (nivel < 1 || nivel > 4) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Nível de 1 a 4."));
            return;
        }
        long agora = System.currentTimeMillis();
        Long ate = esperaPortal.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        esperaPortal.put(p.getUniqueId(), agora + 5000L);
        Masmorra m = sortear(nivel);
        if (m == null) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Nenhuma masmorra de nível " + nivel + " pronta agora."));
            return;
        }
        World w = Bukkit.getWorld(m.mundo);
        if (w == null) return;
        Location dest = Loja.lugarPerto(w, m.saidaX, m.saidaY, m.saidaZ);
        if (dest == null) dest = new Location(w, m.saidaX + 0.5, m.saidaY + 1, m.saidaZ + 0.5);
        p.setFallDistance(0);
        final Location d = dest;
        p.teleportAsync(d, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt(COR_TXT[nivel] + "§l" + m.tipo.nome), Masmorras.txt("§7Chefe: §c" + m.tipo.chefeNome + " §8• §7nível " + nivel)));
            p.playSound(p.getLocation(), "minecraft:block.portal.travel", 0.4f, 1.4f);
            pl.darBussola(p, m);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether(org.bukkit.event.player.PlayerPortalEvent e) {
        if (ehHub(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether2(org.bukkit.event.entity.EntityPortalEvent e) {
        if (ehHub(e.getFrom().getWorld())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ bonecos de treino

    private org.bukkit.NamespacedKey kBoneco() {
        return new org.bukkit.NamespacedKey(pl, "boneco_treino");
    }

    private void garantirBonecos() {
        if (mundo == null) return;
        for (int[] c : BONECOS) {
            if (!mundo.isChunkLoaded(c[0] >> 4, c[1] >> 4)) continue;
            Location l = new Location(mundo, c[0] + 0.5, Y + 1, c[1] + 0.5, 180f, 0f);
            boolean tem = false;
            for (Entity e : mundo.getNearbyEntities(l, 1, 2, 1)) if (e.getPersistentDataContainer().has(kBoneco())) tem = true;
            if (tem) continue;
            mundo.spawn(l, org.bukkit.entity.Mannequin.class, CreatureSpawnEvent.SpawnReason.CUSTOM, y -> {
                y.getPersistentDataContainer().set(kBoneco(), PersistentDataType.BYTE, (byte) 1);
                y.setImmovable(true);
                y.setGravity(false);
                y.setSilent(true);
                y.setPersistent(true);
                y.customName(Masmorras.txt("§e§lBoneco de Treino"));
                y.setCustomNameVisible(true);
                y.setDescription(Masmorras.txt("§7bata em mim"));
                if (y.getEquipment() != null) {
                    y.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.CARVED_PUMPKIN));
                    y.getEquipment().setChestplate(new org.bukkit.inventory.ItemStack(Material.LEATHER_CHESTPLATE));
                }
            });
        }
    }

    /** Boneco nao morre: mostra o dano que levaria (numero flutuando e na barra). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void bateuNoBoneco(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(kBoneco())) return;
        double dano = e.getFinalDamage();
        e.setCancelled(true);
        Entity d = e.getDamager();
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        Location l = e.getEntity().getLocation().add((Math.random() - 0.5) * 0.8, 2.2, (Math.random() - 0.5) * 0.8);
        String num = String.format(java.util.Locale.ROOT, "%.1f", dano);
        org.bukkit.entity.TextDisplay t = mundo.spawn(l, org.bukkit.entity.TextDisplay.class, x -> {
            x.text(Masmorras.txt((dano >= 10 ? "§c§l" : "§e") + "-" + num));
            x.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setPersistent(false);
            x.setTeleportDuration(10);
        });
        Bukkit.getScheduler().runTaskLater(pl, () -> t.teleport(t.getLocation().add(0, 0.8, 0)), 1L);
        Bukkit.getScheduler().runTaskLater(pl, t::remove, 16L);
        e.getEntity().getWorld().spawnParticle(org.bukkit.Particle.CRIT, e.getEntity().getLocation().add(0, 1.2, 0), 8, 0.3, 0.4, 0.3, 0.1);
        if (d instanceof Player p) p.sendActionBar(Masmorras.txt("§eDano: §f" + num + " §7(" + String.format(java.util.Locale.ROOT, "%.1f", dano / 2) + " corações)"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void bonecoNaoMorre(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kBoneco())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ construcao (uma vez)

    private void por(int x, int y, int z, String bloco) {
        mundo.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(bloco), false);
    }

    private void construir() {
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) mundo.getChunkAt(cx, cz).load(true);
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                String piso;
                if (Math.abs(x) == R || Math.abs(z) == R) piso = "minecraft:chiseled_deepslate";
                else if (d <= 2.5) piso = "minecraft:gold_block";
                else if (d <= 4.5) piso = "minecraft:polished_blackstone_bricks";
                else if (x == 0 || z == 0) piso = "minecraft:polished_blackstone";
                else piso = ((x + z) & 1) == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles";
                por(x, Y, z, piso);
                por(x, Y - 1, z, "minecraft:deepslate_bricks");
                if (Math.abs(x) == R || Math.abs(z) == R) {
                    boolean poste = (x + R) % 6 == 0 && (z + R) % 6 == 0 || (Math.abs(x) == R && Math.abs(z) == R)
                            || (Math.abs(x) == R && z % 6 == 0) || (Math.abs(z) == R && x % 6 == 0);
                    if (poste) {
                        por(x, Y + 1, z, "minecraft:deepslate_bricks");
                        por(x, Y + 2, z, "minecraft:deepslate_bricks");
                        por(x, Y + 3, z, "minecraft:soul_lantern");
                    } else por(x, Y + 1, z, "minecraft:deepslate_brick_wall");
                }
            }
        // palco do Mercador Lendario (norte)
        for (int x = -2; x <= 2; x++) for (int z = -R + 2; z <= -R + 5; z++) por(x, Y + 1, z, "minecraft:polished_blackstone_bricks");
        por(-2, Y + 2, -R + 2, "minecraft:lantern");
        por(2, Y + 2, -R + 2, "minecraft:lantern");
        // volta ao spawn principal (sul): esmeralda com borda de ouro
        for (int x = -1; x <= 1; x++) for (int z = R - 5; z <= R - 3; z++) por(x, Y, z, "minecraft:gold_block");
        por(0, Y, R - 4, "minecraft:emerald_block");
        // luz invisivel por toda a praca
        Light luz = (Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(15);
        for (int x = -R + 2; x <= R - 2; x += 5)
            for (int z = -R + 2; z <= R - 2; z += 5) {
                Block b = mundo.getBlockAt(x, Y + 4, z);
                if (b.getType().isAir()) b.setBlockData(luz, false);
            }
        // placas
        placa(0, Y + 1, 3, BlockFace.NORTH, "§6§lSPAWN DAS", "§6§lMASMORRAS", "§fMercador Lendário", "§7(norte, frente)");
        placa(2, Y + 1, R - 5, BlockFace.NORTH, "§a§lVOLTAR", "§fpise na", "§aesmeralda", "§7spawn principal");
        placa(-2, Y + 1, 3, BlockFace.NORTH, "§e§lCORONAS", "§fmonstros e chefes", "§fdas masmorras", "§7saldo: /coronas");
        placa(2, Y + 1, 3, BlockFace.NORTH, "§c§lMASMORRAS", "§fbússola no item", "§fMasmorras ou", "§e/masmorras menu");
        pl.getLogger().info("Spawn das Masmorras construido (mundo " + MUNDO + ").");
    }

    private void placa(int x, int y, int z, BlockFace olha, String... linhas) {
        BlockData d = Bukkit.createBlockData("minecraft:dark_oak_sign[rotation=" + switch (olha) {
            case SOUTH -> 0;
            case WEST -> 4;
            case EAST -> 12;
            default -> 8;
        } + "]");
        Block b = mundo.getBlockAt(x, y, z);
        b.setBlockData(d, false);
        if (b.getState() instanceof Sign s) {
            for (int i = 0; i < 4 && i < linhas.length; i++) s.getSide(Side.FRONT).line(i, Masmorras.txt(linhas[i]));
            s.getSide(Side.FRONT).setGlowingText(true);
            s.setWaxed(true);
            s.update(true, false);
        }
    }

    // ------------------------------------------------------------------ villagers (Lendario aqui, Guia no spawn principal)

    Location localGuia() {
        String s = pl.getConfig().getString("hub.guia", "");
        if (!s.isBlank()) {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w != null && p.length >= 4)
                return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), p.length > 4 ? Float.parseFloat(p[4]) : 0f, 0f);
        }
        World w = Bukkit.getWorld(pl.getConfig().getString("mundos.normal", "world"));
        if (w == null) return null;
        Location sp = w.getSpawnLocation();
        return Loja.lugarPerto(w, sp.getBlockX(), sp.getBlockY(), sp.getBlockZ());
    }

    private void garantirNpcs() {
        try {
            garantirBonecos();
        } catch (Throwable ignored) {
        }
        if (mundo != null && mundo.isChunkLoaded(0, -2)) {
            Location l = new Location(mundo, 0.5, Y + 2, -R + 3.5, 0f, 0f);
            if (!existe(l, pl.loja.kMercador)) pl.loja.npc(l, "§5§lMercador Lendário", null, Loja.profissao(6), pl.loja.kMercador, 6);
        }
        // 01/11: o spawn principal novo tem o portal do Spawn das Masmorras; o Guia so volta se ligar hub.guia-no-spawn
        if (!pl.getConfig().getBoolean("hub.guia-no-spawn", false)) return;
        Location g = localGuia();
        if (g != null && g.getWorld().isChunkLoaded(g.getBlockX() >> 4, g.getBlockZ() >> 4) && !existe(g, pl.loja.kGuia)) {
            Villager v = pl.loja.npc(g, "§e§lGuia das Masmorras", null, Loja.profissao(0), pl.loja.kGuia, 1);
            v.setProfession(Registry.VILLAGER_PROFESSION.get(org.bukkit.NamespacedKey.minecraft("cartographer")));
            if (pl.getConfig().getString("hub.guia", "").isBlank()) {
                pl.getConfig().set("hub.guia", g.getWorld().getName() + "," + g.getX() + "," + g.getY() + "," + g.getZ() + "," + g.getYaw());
                pl.saveConfig();
            }
        }
    }

    private static boolean existe(Location l, org.bukkit.NamespacedKey k) {
        for (Entity e : l.getWorld().getNearbyEntities(l, 3, 3, 3)) if (e.getPersistentDataContainer().has(k)) return true;
        return false;
    }

    /** Admin: poe o Guia onde ele esta. */
    void moverGuia(Player p) {
        Location g = localGuia();
        if (g != null) for (Entity e : g.getWorld().getNearbyEntities(g, 3, 3, 3)) if (e.getPersistentDataContainer().has(pl.loja.kGuia)) e.remove();
        Location l = p.getLocation();
        pl.getConfig().set("hub.guia", l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw());
        pl.saveConfig();
        garantirNpcs();
        p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Guia das Masmorras colocado aqui."));
    }

    void teleportar(Player p) {
        if (mundo == null) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "O Spawn das Masmorras está desligado."));
            return;
        }
        p.setFallDistance(0);
        p.teleportAsync(spawn(), PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§6§lSpawn das Masmorras"), Masmorras.txt("§7Mercador Lendário ao norte")));
            p.playSound(p.getLocation(), "minecraft:entity.enderman.teleport", 1f, 1f);
        });
    }

    // ------------------------------------------------------------------ protecao

    private boolean livre(Player p) {
        return p != null && pl.podeEditar(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void quebrar(BlockBreakEvent e) {
        if (ehHub(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        if (ehHub(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void balde(PlayerBucketEmptyEvent e) {
        if (ehHub(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void balde2(PlayerBucketFillEvent e) {
        if (ehHub(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void quadro(HangingBreakByEntityEvent e) {
        if (ehHub(e.getEntity().getWorld()) && !(e.getRemover() instanceof Player p && livre(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mexer(PlayerInteractEntityEvent e) {
        if (ehHub(e.getRightClicked().getWorld()) && (e.getRightClicked() instanceof org.bukkit.entity.ItemFrame || e.getRightClicked() instanceof org.bukkit.entity.ArmorStand)
                && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void explodir(EntityExplodeEvent e) {
        if (ehHub(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fogo(BlockIgniteEvent e) {
        if (ehHub(e.getBlock().getWorld()) && !(e.getPlayer() != null && livre(e.getPlayer()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void queimar(BlockBurnEvent e) {
        if (ehHub(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mudarBloco(EntityChangeBlockEvent e) {
        if (ehHub(e.getBlock().getWorld()) && !(e.getEntity() instanceof Player p && livre(p))) e.setCancelled(true);
    }

    /** Ninguem se machuca aqui (nem queda, nem PvP, nem monstro). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void dano(EntityDamageEvent e) {
        if (ehHub(e.getEntity().getWorld()) && (e.getEntity() instanceof Player || e.getEntity() instanceof Villager)) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && e.getEntity() instanceof Player p) teleportar(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fome(FoodLevelChangeEvent e) {
        if (ehHub(e.getEntity().getWorld()) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void nascer(CreatureSpawnEvent e) {
        if (ehHub(e.getLocation().getWorld()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    /** Caiu da praca: volta. Pisou na esmeralda: vai para o spawn principal. */
    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (!ehHub(to.getWorld())) return;
        if (to.getY() < Y - 15) {
            teleportar(e.getPlayer());
            return;
        }
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;

        if (to.getBlock().getRelative(BlockFace.DOWN).getType() == Material.EMERALD_BLOCK) {
            World w = Bukkit.getWorld(pl.getConfig().getString("mundos.normal", "world"));
            if (w == null) return;
            Player p = e.getPlayer();
            p.teleportAsync(w.getSpawnLocation().add(0.5, 0, 0.5), PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .thenAccept(ok -> p.playSound(p.getLocation(), "minecraft:entity.enderman.teleport", 1f, 1.2f));
        }
    }

    static final class Registry {
        static final org.bukkit.Registry<Villager.Profession> VILLAGER_PROFESSION = org.bukkit.Registry.VILLAGER_PROFESSION;
    }
}
