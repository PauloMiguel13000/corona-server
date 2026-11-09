package br.servidor.masmorras;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Galeria das Reliquias (/galeria): um salao num mundo proprio com o conjunto completo de CADA masmorra (elmo 3D,
 * peitoral, calca, botas e a arma do tema na mao) em suportes trancados, com o nome da masmorra em cima.
 * Serve para os jogadores (e o dono) verem tudo junto, no Java e no Bedrock. Ninguem consegue pegar nada.
 * Trocar a VERSAO reconstroi (marca galeria-vN.txt).
 */
final class Galeria implements Listener {
    static final String MUNDO = "galeria";
    static final int Y = 100, VERSAO = 2;
    static final int[] LINHAS_Z = {-16, -10, -3, 3, 10, 16};

    private final Masmorras pl;
    World mundo;

    Galeria(Masmorras pl) {
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
            return new Location(world, -19.5, Y + 1, 0.5, -90f, 0f);
        }
    }

    Location chegada() {
        return new Location(mundo, -19.5, Y + 1, 0.5, -90f, 0f);
    }

    static boolean eh(World w) {
        return w != null && w.getName().equals(MUNDO);
    }

    private NamespacedKey chave() {
        return new NamespacedKey(pl, "galeria");
    }

    void iniciar() {
        if (!pl.getConfig().getBoolean("galeria.ativa", true)) return;
        mundo = new WorldCreator(MUNDO).generator(new Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        mundo.setGameRule(GameRule.MOB_GRIEFING, false);
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
        mundo.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        mundo.setGameRule(GameRule.FALL_DAMAGE, false);
        mundo.setFullTime(6000);
        mundo.setStorm(false);
        mundo.setThundering(false);
        mundo.setViewDistance(4);
        mundo.setSpawnLocation(chegada());
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) mundo.getChunkAt(cx, cz).load(true);
        File feito = new File(pl.getDataFolder(), "galeria-v" + VERSAO + ".txt");
        if (!feito.exists()) {
            try {
                construir();
                Files.writeString(feito.toPath(), "construida em " + new java.util.Date() + "\n");
            } catch (Throwable t) {
                pl.getLogger().warning("Galeria nao terminou de construir: " + t);
            }
        }
    }

    /** /galeria */
    boolean comando(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Use no jogo: /galeria");
            return true;
        }
        if (mundo == null) {
            p.sendMessage(Masmorras.txt("§cA Galeria das Relíquias está fechada agora."));
            return true;
        }
        p.teleport(chegada());
        p.sendMessage(Masmorras.txt("§6§lGaleria das Relíquias §7- os conjuntos de todas as masmorras. §8(/spawn para voltar)"));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        return true;
    }

    /** Admin: /masmorras admin galeria (refaz os suportes e o salao). */
    void refazer(CommandSender quem) {
        if (mundo == null) {
            quem.sendMessage("§cA galeria nao esta ligada.");
            return;
        }
        construir();
        quem.sendMessage("§aGaleria refeita.");
    }

    // ------------------------------------------------------------------ construcao

    private void por(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    private void construir() {
        for (Entity e : mundo.getEntities())
            if (e.getPersistentDataContainer().has(chave(), PersistentDataType.INTEGER) || e.getScoreboardTags().contains("lixo_galeria")) e.remove();
        try {   // suportes de teste guardados longe (1000, 150, 1000)
            org.bukkit.Chunk ch = mundo.getChunkAt(62, 62);
            ch.load(true);
            for (Entity e : ch.getEntities()) if (e.getScoreboardTags().contains("lixo_galeria")) e.remove();
        } catch (Throwable ignored) {
        }
        // piso, paredes e corredores
        for (int x = -23; x <= 23; x++)
            for (int z = -21; z <= 21; z++) {
                boolean borda = Math.abs(x) == 23 || Math.abs(z) == 21;
                por(x, Y - 1, z, Material.POLISHED_DEEPSLATE);
                por(x, Y, z, borda ? Material.DEEPSLATE_TILES : ((x + z) % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE));
                for (int y = Y + 1; y <= Y + 8; y++) por(x, y, z, borda ? (y == Y + 7 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE) : Material.AIR);
                if (borda) por(x, Y + 8, z, Material.DEEPSLATE_TILE_SLAB);
            }
        // corredores com tapete
        for (int x = -21; x <= 21; x++)
            for (int z0 : new int[]{-13, 0, 13})
                for (int dz = -1; dz <= 1; dz++) por(x, Y + 1, z0 + dz, dz == 0 ? Material.RED_CARPET : Material.AIR);
        for (int z = -20; z <= 20; z++) {
            por(-21, Y + 1, z, Material.RED_CARPET);
            por(21, Y + 1, z, Material.RED_CARPET);
        }
        // luzes no piso (lanternas do mar entre os suportes) e pilares nas paredes
        for (int x = -20; x <= 20; x += 4)
            for (int z : new int[]{-13, 0, 13}) por(x, Y, z + 2, Material.SEA_LANTERN);
        for (int x = -20; x <= 20; x += 4) {
            for (int z : new int[]{-21, 21})
                for (int y = Y + 1; y <= Y + 6; y++) por(x, y, z, Material.QUARTZ_PILLAR);
            por(x, Y + 5, -20, Material.LANTERN);
            por(x, Y + 5, 20, Material.LANTERN);
        }
        // canteiros entre as fileiras de costas uma para a outra
        for (int x = -19; x <= 19; x++)
            for (int z : new int[]{-7, 7}) {
                por(x, Y + 1, z, Material.MOSS_BLOCK);
                if (x % 3 == 0) por(x, Y + 2, z, Material.AZALEA);
            }
        // suportes
        List<String> temas = new ArrayList<>();
        for (Tipo t : Tipo.TODOS.values()) temas.add(t.id);
        temas.add("HEROBRINE");
        int i = 0;
        for (int linha = 0; linha < LINHAS_Z.length; linha++) {
            int z = LINHAS_Z[linha];
            boolean paraSul = linha % 2 == 0;
            for (int col = 0; col < 10 && i < temas.size(); col++, i++) {
                int x = -18 + col * 4;
                suporte(temas.get(i), x, z, paraSul);
            }
        }
        // vitrine no fim do tapete: Traje do Rei (MVP e LENDA)
        try {
            traje(20, 0);
        } catch (Throwable t) {
            pl.getLogger().warning("Galeria: vitrine do Traje do Rei: " + t);
        }
        // placa de boas-vindas
        TextDisplay tit = mundo.spawn(new Location(mundo, -21.0, Y + 4.2, 0.5, -90f, 0f), TextDisplay.class, d -> {
            d.text(Masmorras.txt("§6§lGaleria das Relíquias\n§7O conjunto de cada masmorra: elmo, armadura e arma\n§8Use §f/spawn §8para voltar"));
            d.setBillboard(Display.Billboard.FIXED);
            d.setRotation(-90f, 0f);
            marcar(d);
        });
        tit.setPersistent(true);
        pl.getLogger().info("Galeria das Relíquias construída: " + i + " conjuntos.");
    }

    /** Traje do Rei de vitrine (so visual: diamante com o visual do pacote; nao e o item de verdade do VIP). */
    private void traje(int x, int z) {
        String[] pecas = {"elmo", "peitoral", "calca", "botas"};
        Material[] mats = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
        ItemStack[] its = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            ItemStack it = new ItemStack(mats[i]);
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "item/coroa/" + pecas[i]));
            io.papermc.paper.datacomponent.item.Equippable.Builder b = io.papermc.paper.datacomponent.item.Equippable.equippable(mats[i].getEquipmentSlot());
            if (i != 0) b.assetId(net.kyori.adventure.key.Key.key("corona", "coroa"));
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE, b.build());
            its[i] = it;
        }
        por(x, Y + 1, z, Material.GOLD_BLOCK);
        por(x - 1, Y + 1, z, Material.CHISELED_QUARTZ_BLOCK);
        por(x, Y + 1, z - 1, Material.QUARTZ_BLOCK);
        por(x, Y + 1, z + 1, Material.QUARTZ_BLOCK);
        Location l = new Location(mundo, x + 0.5, Y + 2, z + 0.5, 90f, 0f);
        ArmorStand st = mundo.spawn(l, ArmorStand.class, a -> {
            a.setInvulnerable(true);
            a.setGravity(false);
            a.setArms(true);
            a.setBasePlate(false);
            a.setPersistent(true);
            a.setCanPickupItems(false);
            for (EquipmentSlot s : EquipmentSlot.values()) {
                try {
                    a.addEquipmentLock(s, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    a.addEquipmentLock(s, ArmorStand.LockType.ADDING_OR_CHANGING);
                } catch (Throwable ignored) {
                }
            }
            a.getEquipment().setHelmet(its[0]);
            a.getEquipment().setChestplate(its[1]);
            a.getEquipment().setLeggings(its[2]);
            a.getEquipment().setBoots(its[3]);
            a.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-20), 0, Math.toRadians(-8)));
            marcar(a);
        });
        st.setPersistent(true);
        TextDisplay d = mundo.spawn(new Location(mundo, x + 0.5, Y + 4.75, z + 0.5), TextDisplay.class, td -> {
            td.text(Masmorras.txt("§6§l✦ Traje do Rei ✦\n§7Exclusivo do §6§lMVP §7e da §d§lLENDA\n§eVeja: §f/vip"));
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(false);
            td.setViewRange(0.5f);
            marcar(td);
        });
        d.setPersistent(true);
    }

    private void marcar(Entity e) {
        e.getPersistentDataContainer().set(chave(), PersistentDataType.INTEGER, VERSAO);
    }

    private ItemStack peca(Material m, String tema) {
        ItemStack it = new ItemStack(m);
        Reliquias.visualDoPacote(it, tema, pl.getLogger());
        return it;
    }

    private void suporte(String tema, int x, int z, boolean paraSul) {
        por(x, Y + 1, z, Material.CHISELED_QUARTZ_BLOCK);
        por(x, Y + 1, z + (paraSul ? -1 : 1), Material.QUARTZ_BLOCK);
        float yaw = paraSul ? 0f : 180f;
        Location l = new Location(mundo, x + 0.5, Y + 2, z + 0.5, yaw, 0f);
        boolean hb = tema.equals("HEROBRINE");
        ArmorStand st = mundo.spawn(l, ArmorStand.class, a -> {
            a.setInvulnerable(true);
            a.setGravity(false);
            a.setArms(true);
            a.setBasePlate(false);
            a.setPersistent(true);
            a.setCanPickupItems(false);
            for (EquipmentSlot s : EquipmentSlot.values()) {
                try {
                    a.addEquipmentLock(s, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    a.addEquipmentLock(s, ArmorStand.LockType.ADDING_OR_CHANGING);
                } catch (Throwable ignored) {
                }
            }
            a.getEquipment().setHelmet(peca(hb ? Material.NETHERITE_HELMET : Material.IRON_HELMET, tema));
            a.getEquipment().setChestplate(peca(hb ? Material.NETHERITE_CHESTPLATE : Material.IRON_CHESTPLATE, tema));
            a.getEquipment().setLeggings(peca(hb ? Material.NETHERITE_LEGGINGS : Material.IRON_LEGGINGS, tema));
            a.getEquipment().setBoots(peca(hb ? Material.NETHERITE_BOOTS : Material.IRON_BOOTS, tema));
            a.getEquipment().setItemInMainHand(peca(hb ? Material.NETHERITE_SWORD : Material.IRON_SWORD, tema));
            a.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(-20), 0, Math.toRadians(-8)));
            marcar(a);
        });
        st.setPersistent(true);
        Tipo t = Tipo.TODOS.get(tema);
        String nome = hb ? "§4§lHerobrine §7(conjunto especial)" : "§6§l" + (t == null ? tema : ("???".equals(t.nome) ? "Masmorra Secreta" : t.nome));
        String onde = hb ? "§8loja do servidor" : t == null ? "" : switch (t.onde) {
            case NETHER -> "§cNether §8• nível " + t.nivel;
            case END -> "§5End §8• nível " + t.nivel;
            default -> "§aMundo normal §8• nível " + t.nivel;
        };
        TextDisplay d = mundo.spawn(new Location(mundo, x + 0.5, Y + 4.35, z + 0.5), TextDisplay.class, td -> {
            td.text(Masmorras.txt(nome + "\n" + onde));
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(false);
            td.setViewRange(0.35f);
            marcar(td);
        });
        d.setPersistent(true);
    }

    // ------------------------------------------------------------------ protecao

    @EventHandler(priority = EventPriority.HIGHEST)
    public void mexer(PlayerArmorStandManipulateEvent e) {
        if (eh(e.getRightClicked().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clicar(PlayerInteractAtEntityEvent e) {
        if (eh(e.getRightClicked().getWorld()) && e.getRightClicked() instanceof ArmorStand) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void dano(EntityDamageEvent e) {
        if (!eh(e.getEntity().getWorld())) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.KILL && !(e.getEntity() instanceof Player)) return;   // /kill do console
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void quebrar(BlockBreakEvent e) {
        if (eh(e.getBlock().getWorld()) && !(e.getPlayer().isOp() && e.getPlayer().getGameMode() == GameMode.CREATIVE)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void colocar(BlockPlaceEvent e) {
        if (eh(e.getBlock().getWorld()) && !(e.getPlayer().isOp() && e.getPlayer().getGameMode() == GameMode.CREATIVE)) e.setCancelled(true);
    }

    @EventHandler
    public void cair(PlayerMoveEvent e) {
        if (!eh(e.getTo().getWorld()) || e.getTo().getY() > Y - 6) return;
        e.getPlayer().teleport(chegada());
    }
}
