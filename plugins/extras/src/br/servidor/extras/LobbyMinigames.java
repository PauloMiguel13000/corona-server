package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Lobby dos Minigames (mundo "minigames"): uma praca redonda flutuando no ceu, sempre de dia, com um farol laranja
 * no meio e 7 portais (Defesa da Torre ao norte, Slenderman a leste, Casa da Vovo a oeste, Backrooms ao sul,
 * Hotel a noroeste, Nao Chore a nordeste e Arena PvP a sudeste).
 * Entrar no portal entra no minigame. Esmeralda perto do centro volta ao spawn principal. Ninguem quebra, coloca,
 * se machuca, sente fome ou cai (caiu: volta ao centro). Sem PvP e sem habilidades (Poderes). Pet pode ficar.
 * /minigames leva para ca; /minigames menu abre o menu de antes.
 */
final class LobbyMinigames implements Listener {

    static final String MUNDO = "minigames";
    static final int Y = 100, R = 34, PORTAL = 22, VERSAO = 2, VAGAS = 12;

    record Portal(String id, String nome, String sub, String cor, String comando, int dx, int dz, String[] moldura, Material icone, Color brilho) {}

    static final Portal[] PORTAIS = {
            new Portal("defesa", "§6§lDEFESA DA TORRE", "§7Torres contra ondas de monstros", "§6", "defesa mapas", 0, -1,
                    new String[] {"minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks"}, Material.BOW, Color.ORANGE),
            new Portal("slender", "§8§lSLENDERMAN", "§7Ache as 8 folhas no escuro", "§8", "slender", 1, 0,
                    new String[] {"minecraft:stripped_dark_oak_wood", "minecraft:dark_oak_wood", "minecraft:blackstone"}, Material.ENDER_EYE, Color.fromRGB(60, 60, 60)),
            new Portal("vovo", "§5§lCASA DA VOVÓ", "§7Fuja da vovó sem fazer barulho", "§5", "vovo", -1, 0,
                    new String[] {"minecraft:bricks", "minecraft:spruce_planks", "minecraft:stripped_spruce_wood"}, Material.IRON_DOOR, Color.PURPLE),
            new Portal("backrooms", "§e§lBACKROOMS", "§7Saia da realidade e ache a saída", "§e", "backrooms", 0, 1,
                    new String[] {"minecraft:yellow_terracotta", "minecraft:smooth_sandstone", "minecraft:ochre_froglight"}, Material.YELLOW_GLAZED_TERRACOTTA, Color.YELLOW),
            // diagonal (noroeste), entre a arvore e o parapeito
            new Portal("hotel", "§4§lO HOTEL", "§7Abra 100 portas. Esconda-se nos armários!", "§4", "hotel entrar", -1, -1,
                    new String[] {"minecraft:dark_oak_planks", "minecraft:stripped_dark_oak_wood", "minecraft:red_nether_bricks"}, Material.DARK_OAK_DOOR, Color.RED),
            // diagonal nordeste
            new Portal("naochore", "§9§lNÃO CHORE", "§75 dias. Eles estão te observando...", "§9", "naochore entrar", 1, -1,
                    new String[] {"minecraft:polished_deepslate", "minecraft:deepslate_bricks", "minecraft:crying_obsidian"}, Material.GHAST_TEAR, Color.fromRGB(80, 120, 255)),
            // diagonal sudeste
            new Portal("arena", "§c§lARENA PvP", "§7Lute com seus poderes sem perder itens", "§c", "arena entrar", 1, 1,
                    new String[] {"minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone", "minecraft:gold_block"}, Material.NETHERITE_SWORD, Color.fromRGB(255, 190, 0))};

    /** Subtitulo antigo das placas do spawn (atualizado quando a placa carrega). */
    static final String SUB_ANTIGO = "§7Defesa • Slender • Vovó • Backrooms", SUB_NOVO = "§7Defesa • Slender • Vovó • Backrooms\n§7Hotel • Não Chore • Arena PvP";

    /**
     * v2: 12 vagas em circulo (a cada 30 graus, vaga 0 ao norte). Os jogos de hoje ficam perto de onde estavam;
     * jogo novo entra sozinho na primeira vaga livre. Vaga sem jogo vira um arco "Em breve".
     */
    static final Map<String, Integer> VAGA_PREFERIDA = Map.of("defesa", 0, "naochore", 1, "slender", 3, "arena", 4, "backrooms", 6, "vovo", 9, "hotel", 10);

    static int vaga(Portal p) {
        Integer v = VAGA_PREFERIDA.get(p.id());
        if (v != null) return v;
        java.util.Set<Integer> usadas = new java.util.HashSet<>(VAGA_PREFERIDA.values());
        int i = 0;
        for (Portal o : PORTAIS) {
            if (VAGA_PREFERIDA.containsKey(o.id())) continue;
            for (int k = 0; k < VAGAS; k++) {
                int cand = (2 + k * 5) % VAGAS;
                if (usadas.add(cand)) {
                    if (o == p) return cand;
                    break;
                }
            }
            i++;
        }
        return 2;
    }

    static double angulo(int vaga) {
        return -90 + vaga * (360.0 / VAGAS);
    }

    static int cxVaga(int vaga) {
        return (int) Math.round(Math.cos(Math.toRadians(angulo(vaga))) * PORTAL);
    }

    static int czVaga(int vaga) {
        return (int) Math.round(Math.sin(Math.toRadians(angulo(vaga))) * PORTAL);
    }

    /** O portal fica de frente para o centro: norte/sul = plano em X; leste/oeste = plano em Z. */
    static boolean eixoX(int vaga) {
        double a = Math.toRadians(angulo(vaga));
        return Math.abs(Math.sin(a)) >= Math.abs(Math.cos(a));
    }

    static int cx(Portal p) {
        return cxVaga(vaga(p));
    }

    static int cz(Portal p) {
        return czVaga(vaga(p));
    }

    private final Extras pl;
    World mundo;
    private final List<ItemDisplay> icones = new ArrayList<>();
    private final Map<UUID, Long> espera = new HashMap<>();
    private float giro;

    LobbyMinigames(Extras pl) {
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
                @Override public Biome getBiome(WorldInfo info, int x, int y, int z) { return Biome.PLAINS; }
                @Override public List<Biome> getBiomes(WorldInfo info) { return List.of(Biome.PLAINS); }
            };
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, Y + 1, 8.5, 180f, 0f);
        }
    }

    boolean ehLobby(World w) {
        return w != null && w.getName().equals(MUNDO);
    }

    Location spawn() {
        return new Location(mundo, 0.5, Y + 1, 8.5, 180f, 0f);
    }

    void iniciar() {
        if (!pl.getConfig().getBoolean("lobby-minigames.ativo", true)) return;
        mundo = new WorldCreator(MUNDO).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        mundo.setGameRule(GameRule.MOB_GRIEFING, false);
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
        mundo.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        mundo.setTime(6000);
        mundo.setStorm(false);
        mundo.setViewDistance(4);
        mundo.setSpawnLocation(spawn());
        File feito = new File(pl.getDataFolder(), "lobby-minigames-v" + VERSAO + ".txt");
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) mundo.getChunkAt(cx, cz).load(true);
        File feitoHotel = new File(pl.getDataFolder(), "lobby-minigames-hotel.txt");
        File feitoNovos = new File(pl.getDataFolder(), "lobby-minigames-naochore-arena.txt");
        if (!feito.exists()) {
            construir();
            try {
                Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
                Files.writeString(feitoHotel.toPath(), "junto com o lobby\n");
                Files.writeString(feitoNovos.toPath(), "junto com o lobby\n");
            } catch (IOException ignored) {
            }
        } else if (false && !feitoHotel.exists()) {
            // lobby ja existia: so acrescenta o portal do Hotel
            for (Portal p : PORTAIS) if (p.id().equals("hotel")) portal(p);
            pl.getLogger().info("Portal do Hotel colocado no lobby dos minigames.");
            try {
                Files.writeString(feitoHotel.toPath(), "colocado em " + new java.util.Date() + "\n");
            } catch (IOException ignored) {
            }
        }
        if (false && !feitoNovos.exists()) {
            // lobby ja existia: acrescenta os portais do Nao Chore e da Arena PvP
            for (Portal p : PORTAIS) if (p.id().equals("naochore") || p.id().equals("arena")) portal(p);
            pl.getLogger().info("Portais do Nao Chore e da Arena PvP colocados no lobby dos minigames.");
            try {
                Files.writeString(feitoNovos.toPath(), "colocado em " + new java.util.Date() + "\n");
            } catch (IOException ignored) {
            }
        }
        for (World w : Bukkit.getWorlds()) for (Entity en : w.getEntities()) atualizarPlaca(en);
        icones.clear();
        for (Entity e : mundo.getEntities())
            if (e instanceof ItemDisplay d && d.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) icones.add(d);
        Bukkit.getScheduler().runTaskTimer(pl, this::girar, 40L, 10L);
    }

    private org.bukkit.NamespacedKey chave() {
        return new org.bukkit.NamespacedKey(pl, "lobby_enfeite");
    }

    private org.bukkit.NamespacedKey chaveVersao() {
        return new org.bukkit.NamespacedKey(pl, "lobby_v");
    }

    /** Enfeite do lobby antigo (sem a versao atual) que carregar depois e apagado. */
    @EventHandler
    public void enfeitesCarregados(org.bukkit.event.world.EntitiesLoadEvent e) {
        if (!ehLobby(e.getWorld())) return;
        for (Entity en : e.getEntities()) {
            if (!en.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) continue;
            Integer v = en.getPersistentDataContainer().get(chaveVersao(), PersistentDataType.INTEGER);
            if (v == null || v != VERSAO) en.remove();
        }
    }

    // ------------------------------------------------------------------ construcao (uma vez)

    private void por(int x, int y, int z, String bloco) {
        mundo.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(bloco), false);
    }

    private void construir() {
        // tira o lobby antigo (blocos e enfeites) antes de fazer o novo
        for (Entity e : mundo.getEntities()) if (e.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) e.remove();
        for (Entity e : mundo.getEntities())
            if (e instanceof TextDisplay || e instanceof ItemDisplay || e instanceof org.bukkit.entity.Interaction) {
                Location l = e.getLocation();
                if (Math.abs(l.getX()) < R + 4 && Math.abs(l.getZ()) < R + 4) e.remove();
            }
        icones.clear();
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) mundo.getChunkAt(cx, cz).load(true);
        for (int x = -R - 3; x <= R + 3; x++)
            for (int z = -R - 3; z <= R + 3; z++)
                for (int y = Y - 14; y <= Y + 14; y++) {
                    org.bukkit.block.Block bl = mundo.getBlockAt(x, y, z);
                    if (!bl.getType().isAir()) bl.setType(Material.AIR, false);
                }
        // piso: quartzo com rejunte preto, centro com coroa e farol vermelho, anel vermelho na frente dos portais
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 0.5) continue;
                String piso;
                if (d > R - 0.5) piso = "minecraft:polished_blackstone_bricks";
                else if (d > R - 1.5) piso = "minecraft:red_nether_bricks";
                else if (d < 0.5) piso = "minecraft:red_stained_glass";
                else if (d < 2.5) piso = "minecraft:quartz_bricks";
                else if (d < 4.5) piso = "minecraft:polished_blackstone_bricks";
                else if (d >= PORTAL - 5.5 && d < PORTAL - 3.5) piso = "minecraft:red_concrete";
                else if (d >= 9.5 && d < 10.5) piso = "minecraft:red_nether_bricks";
                else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) piso = "minecraft:red_concrete";
                else if (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) piso = "minecraft:polished_blackstone";
                else piso = "minecraft:smooth_quartz";
                por(x, Y, z, piso);
                por(x, Y - 1, z, "minecraft:obsidian");
            }
        // fundo afinando
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                for (int k = 2; k <= 10; k++) {
                    if (d >= R - k * 3.2) continue;
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1 && k == 2) continue;
                    por(x, Y - k, z, k % 3 == 0 ? "minecraft:red_nether_bricks" : k >= 7 ? "minecraft:blackstone" : "minecraft:polished_blackstone");
                }
            }
        // farol: o vidro vermelho deixa o feixe vermelho
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) por(x, Y - 2, z, "minecraft:iron_block");
        por(0, Y - 1, 0, "minecraft:beacon");
        // coroa vermelha em volta do farol
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            int x = (int) Math.round(Math.cos(a) * 3.6), z = (int) Math.round(Math.sin(a) * 3.6);
            int alt = k % 2 == 0 ? 4 : 2;
            por(x, Y + 1, z, "minecraft:polished_blackstone_bricks");
            for (int y = 2; y <= alt; y++) por(x, Y + y, z, "minecraft:red_concrete");
            por(x, Y + alt + 1, z, "minecraft:quartz_block");
        }
        // mureta preta e pilares brancos com topo vermelho e lanterna (entre os portais)
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R - 0.5 && d <= R + 0.5) mundo.getBlockAt(x, Y + 1, z).setBlockData(Bukkit.createBlockData("minecraft:polished_blackstone_wall"), true);
            }
        for (int k = 0; k < VAGAS; k++) {
            double a = Math.toRadians(angulo(k) + 180.0 / VAGAS);
            int x = (int) Math.round(Math.cos(a) * (R - 0.4)), z = (int) Math.round(Math.sin(a) * (R - 0.4));
            por(x, Y + 1, z, "minecraft:quartz_pillar");
            por(x, Y + 2, z, "minecraft:quartz_pillar");
            por(x, Y + 3, z, "minecraft:red_concrete");
            por(x, Y + 4, z, "minecraft:lantern[hanging=false]");
        }
        // portais dos jogos e arcos "Em breve" nas vagas livres
        boolean[] ocupada = new boolean[VAGAS];
        for (Portal p : PORTAIS) {
            portal(p);
            ocupada[vaga(p)] = true;
        }
        for (int k = 0; k < VAGAS; k++) if (!ocupada[k]) emBreve(k);
        // volta ao spawn principal: esmeralda com borda de quartzo, logo atras de quem chega
        for (int x = -6; x <= -4; x++) for (int z = 5; z <= 7; z++) por(x, Y, z, "minecraft:quartz_bricks");
        por(-5, Y, 6, "minecraft:emerald_block");
        texto(new Location(mundo, -4.5, Y + 2.2, 6.5), "§a§lVOLTAR AO SPAWN\n§7pise na esmeralda", 1.0f);
        texto(new Location(mundo, 0.5, Y + 7.0, 0.5), "§c§lLOBBY DOS MINIGAMES\n§fEntre num portal para jogar!\n§7" + PORTAIS.length + " jogos  §8|  §7mais chegando  §8|  §7/minigames menu", 1.5f);
        // luz invisivel
        org.bukkit.block.data.type.Light luz = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(15);
        for (int x = -R + 2; x <= R - 2; x += 5)
            for (int z = -R + 2; z <= R - 2; z += 5) {
                if (Math.sqrt(x * x + z * z) > R - 2) continue;
                org.bukkit.block.Block bl = mundo.getBlockAt(x, Y + 3, z);
                if (bl.getType().isAir()) bl.setBlockData(luz, false);
            }
        for (Entity en : mundo.getEntities())
            if (en.getPersistentDataContainer().has(chave(), PersistentDataType.BYTE)) en.getPersistentDataContainer().set(chaveVersao(), PersistentDataType.INTEGER, VERSAO);
        pl.getLogger().info("Lobby dos Minigames v2 construido (" + PORTAIS.length + " jogos, " + (VAGAS - PORTAIS.length) + " vagas livres).");
    }

    /** Arco vazio numa vaga livre: lugar guardado para o proximo minigame. */
    private void emBreve(int vaga) {
        int cx = cxVaga(vaga), cz = czVaga(vaga);
        boolean ex = eixoX(vaga);
        for (int s = -3; s <= 3; s++)
            for (int y = 0; y <= 7; y++) {
                boolean borda = Math.abs(s) >= 2 || y == 0 || y >= 6;
                if (!borda || (Math.abs(s) == 3 && y >= 6)) continue;
                por(ex ? cx + s : cx, Y + y, ex ? cz : cz + s, y == 0 || y == 7 ? "minecraft:red_nether_bricks" : "minecraft:polished_blackstone_bricks");
            }
        int ix = (int) Math.round(Math.cos(Math.toRadians(angulo(vaga))) * 2.0), iz = (int) Math.round(Math.sin(Math.toRadians(angulo(vaga))) * 2.0);
        texto(new Location(mundo, cx - ix * 0.3 + 0.5, Y + 3.5, cz - iz * 0.3 + 0.5), "§8§lEM BREVE\n§7novo minigame", 1.0f);
    }

    private void arvore(int x, int z) {
        por(x, Y, z, "minecraft:moss_block");
        for (int y = 1; y <= 3; y++) por(x, Y + y, z, "minecraft:oak_log");
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = 3; dy <= 5; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + (dy - 4) * (dy - 4) * 2 + dz * dz > 6) continue;
                    if (dx == 0 && dz == 0 && dy == 3) continue;
                    por(x + dx, Y + dy, z + dz, ((dx + dy + dz) & 1) == 0 ? "minecraft:flowering_azalea_leaves[persistent=true]" : "minecraft:azalea_leaves[persistent=true]");
                }
    }

    /** Arco com o portal roxo do jogo (nao leva ao Nether: o lobby cancela) e o nome em cima. */
    private void portal(Portal p) {
        int vg = vaga(p);
        int cx = cxVaga(vg), cz = czVaga(vg);
        boolean eixoX = eixoX(vg);
        int dirX = (int) Math.signum(Math.round(Math.cos(Math.toRadians(angulo(vg))) * 10)), dirZ = (int) Math.signum(Math.round(Math.sin(Math.toRadians(angulo(vg))) * 10));
        String[] m = p.moldura();
        for (int s = -3; s <= 3; s++)
            for (int y = 0; y <= 7; y++) {
                int x = eixoX ? cx + s : cx, z = eixoX ? cz : cz + s;
                boolean borda = Math.abs(s) >= 2 || y == 0 || y >= 6;
                if (!borda) {
                    por(x, Y + y, z, "minecraft:nether_portal[axis=" + (eixoX ? "x" : "z") + "]");
                    continue;
                }
                if (Math.abs(s) == 3 && y >= 6) continue;
                String b = y == 0 ? m[2] : (Math.abs(s) == 3 ? m[1] : m[0]);
                if (y == 7 && Math.abs(s) == 1) b = m[2];
                por(x, Y + y, z, b);
            }
        // degrau na frente e luz
        int fx = eixoX ? cx : cx - dirX * 2, fz = eixoX ? cz - dirZ * 2 : cz;
        for (int s = -2; s <= 2; s++) por(eixoX ? fx + s : fx, Y, eixoX ? fz : fz + s, m[2]);
        Location topo = new Location(mundo, cx + 0.5 - dirX * 0.6, Y + 9.3, cz + 0.5 - dirZ * 0.6);
        texto(topo, p.nome() + "\n" + p.sub(), 1.6f);
        ItemDisplay d = mundo.spawn(topo.clone().add(0, 1.6, 0), ItemDisplay.class, x -> {
            x.setItemStack(new ItemStack(p.icone()));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setGlowing(true);
            x.setGlowColorOverride(p.brilho());
            x.setPersistent(true);
            x.getPersistentDataContainer().set(chave(), PersistentDataType.BYTE, (byte) 1);
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f), new AxisAngle4f()));
        });
        icones.add(d);
    }

    private void texto(Location l, String t, float escala) {
        mundo.spawn(l, TextDisplay.class, x -> {
            x.text(Extras.txt(t));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            x.setPersistent(true);
            x.getPersistentDataContainer().set(chave(), PersistentDataType.BYTE, (byte) 2);
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(escala), new AxisAngle4f()));
        });
    }

    private void girar() {
        if (mundo == null || mundo.getPlayers().isEmpty()) return;
        giro += 0.3f;
        for (ItemDisplay d : icones) {
            if (!d.isValid()) continue;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(10);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(giro, 0, 1, 0), new Vector3f(1.6f), new AxisAngle4f()));
        }
    }

    // ------------------------------------------------------------------ entrar

    void teleportar(Player p) {
        if (mundo == null) {
            p.sendMessage(Extras.txt("§cO lobby dos minigames não está ligado."));
            return;
        }
        p.setFallDistance(0);
        p.teleportAsync(spawn(), PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§6§lMINIGAMES"), Extras.txt("§7Entre num portal para jogar")));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        });
    }

    /** Placas antigas do spawn ("Defesa • Slender • Vovó • Backrooms") ganham a linha dos minigames novos. */
    @EventHandler
    public void placasAntigas(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) atualizarPlaca(en);
    }

    private static void atualizarPlaca(Entity en) {
        if (!(en instanceof TextDisplay t)) return;
        String s = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(t.text());
        if (!s.contains("Vovó • Backrooms") || s.contains("Não Chore")) return;
        String limpo = s.replace("§r", "");
        int i = limpo.indexOf("Defesa • Slender • Vovó • Backrooms");
        if (i < 0) return;
        String antes = limpo.substring(0, i).replaceAll("(§7)+$", "");
        t.text(Extras.txt(antes + SUB_NOVO + limpo.substring(i + "Defesa • Slender • Vovó • Backrooms".length())));
    }

    /** Portal em que o bloco esta (ou null). */
    private Portal portalEm(Location l) {
        int y = l.getBlockY() - Y;
        if (y < 0 || y > 6) return null;
        for (Portal p : PORTAIS) {
            int cx = cx(p), cz = cz(p);
            boolean eixoX = eixoX(vaga(p));
            int ox = l.getBlockX() - cx, oz = l.getBlockZ() - cz;
            if (eixoX ? (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) : (Math.abs(oz) <= 1 && Math.abs(ox) <= 1)) return p;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (!ehLobby(to.getWorld())) return;
        Player p = e.getPlayer();
        if (to.getY() < Y - 20) {
            teleportar(p);
            return;
        }
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        if (to.getBlockY() == Y + 1 && to.getBlockX() == -5 && to.getBlockZ() == 6) {
            p.performCommand("spawn");
            return;
        }
        Portal po = portalEm(to);
        if (po == null) return;
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        espera.put(p.getUniqueId(), agora + 4000L);
        // empurra de volta para a praca (nao fica preso dentro do portal)
        double ang = Math.toRadians(angulo(vaga(po)));
        Location volta = new Location(mundo, cx(po) - Math.round(Math.cos(ang) * 3) + 0.5, Y + 1, cz(po) - Math.round(Math.sin(ang) * 3) + 0.5, to.getYaw(), to.getPitch());
        p.teleport(volta);
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        if (!p.performCommand(po.comando())) p.sendMessage(Extras.txt("§cEsse minigame não está disponível agora."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether(PlayerPortalEvent e) {
        if (ehLobby(e.getFrom().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether2(EntityPortalEvent e) {
        if (ehLobby(e.getFrom().getWorld())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ protecao

    private static boolean livre(Player p) {
        return p != null && p.hasPermission("extras.admin") && p.getGameMode() == org.bukkit.GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void quebrar(BlockBreakEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void balde(PlayerBucketEmptyEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void balde2(PlayerBucketFillEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !livre(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void usar(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || !ehLobby(e.getClickedBlock().getWorld()) || livre(e.getPlayer())) return;
        Material m = e.getClickedBlock().getType();
        if (m == Material.BEACON || m.name().endsWith("_TRAPDOOR") || m.name().endsWith("_DOOR") || m == Material.FLOWER_POT || m.name().startsWith("POTTED_"))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void quadro(HangingBreakByEntityEvent e) {
        if (ehLobby(e.getEntity().getWorld()) && !(e.getRemover() instanceof Player p && livre(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void explodir(EntityExplodeEvent e) {
        if (ehLobby(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fogo(BlockIgniteEvent e) {
        if (ehLobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void queimar(BlockBurnEvent e) {
        if (ehLobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void folhas(LeavesDecayEvent e) {
        if (ehLobby(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mudarBloco(EntityChangeBlockEvent e) {
        if (ehLobby(e.getBlock().getWorld()) && !(e.getEntity() instanceof Player p && livre(p))) e.setCancelled(true);
    }

    /** Ninguem se machuca aqui (nem queda, nem PvP, nem nada). Pet tambem nao. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void dano(EntityDamageEvent e) {
        if (!ehLobby(e.getEntity().getWorld())) return;
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID && e.getEntity() instanceof Player p) teleportar(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void fome(FoodLevelChangeEvent e) {
        if (ehLobby(e.getEntity().getWorld()) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    /** So nasce o que um plugin pediu (pet do BabyPets, enfeites). Nada de monstro. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void nascer(CreatureSpawnEvent e) {
        if (!ehLobby(e.getLocation().getWorld())) return;
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM || Extras.vemDoBabyPets()) {
            if (e.isCancelled() && Extras.vemDoBabyPets()) e.setCancelled(false);
            return;
        }
        e.setCancelled(true);
    }
}
