package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Portais dos niveis no lobby do Mundo das Masmorras (10/11, pedido do Paulo): quatro portais no lado oeste que levam
 * direto para a area de cada nivel do mapa (2, 3, 4 e 5). O nivel 1 fica em volta do lobby, entao nao precisa de portal.
 * Cada portal so deixa passar quem esta com equipamento a altura — a conta esta em {@link #nivelDosItens(Player)}:
 * media do que a pessoa veste (4 pecas) mais a arma da mao, de couro/madeira (1) ate netherite (5).
 */
final class PortaisNivel implements Listener {

    /** Portais no lado oeste do lobby (atravessa no x), um por nivel. */
    static final int PX = -29;
    private static final int[] NIVEIS = {2, 3, 4, 5};
    private static final int[] PZ = {-21, -7, 7, 21};

    private final Masmorras pl;
    private final MundoMasmorras mm;
    private final NamespacedKey kEnfeite;
    private final Map<UUID, Long> espera = new HashMap<>();

    PortaisNivel(Masmorras pl, MundoMasmorras mm) {
        this.pl = pl;
        this.mm = mm;
        this.kEnfeite = new NamespacedKey(pl, "portal_nivel_enfeite");
    }

    boolean ativo() {
        return pl.getConfig().getBoolean("portais-nivel.ativo", true);
    }

    void iniciar() {
        if (mm.normal == null || !ativo()) return;
        File feito = new File(pl.getDataFolder(), "portais-nivel-v2.txt");
        if (feito.exists()) return;
        try {
            construir();
            Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
        } catch (Throwable ex) {
            pl.getLogger().warning("Portais dos niveis nao terminaram: " + ex);
        }
    }

    // ------------------------------------------------------------------ construcao

    private void por(World w, int x, int y, int z, String b) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false);
    }

    /** Moldura e cor de cada nivel (quanto mais alto, mais "perigoso"). */
    private static String[] visual(int nivel) {
        return switch (nivel) {
            case 2 -> new String[] {"minecraft:mossy_stone_bricks", "minecraft:oxidized_copper", "minecraft:moss_block", "§a"};
            case 3 -> new String[] {"minecraft:deepslate_bricks", "minecraft:gold_block", "minecraft:polished_deepslate", "§e"};
            case 4 -> new String[] {"minecraft:blackstone", "minecraft:diamond_block", "minecraft:polished_blackstone", "§b"};
            default -> new String[] {"minecraft:obsidian", "minecraft:netherite_block", "minecraft:crying_obsidian", "§d"};
        };
    }

    private void construir() {
        World w = mm.normal;
        int y = GeradorMundo.LOBBY_Y;
        w.getChunkAt(PX >> 4, 0).load(true);
        for (int i = 0; i < NIVEIS.length; i++) {
            int nivel = NIVEIS[i], z = PZ[i];
            String[] v = visual(nivel);
            portal(w, PX, y, z, v[0], v[1], v[2]);
            texto(w, new Location(w, PX + 0.5, y + 6.8, z + 0.5),
                    v[3] + "§lÁREA DE NÍVEL " + nivel + "\n§7Masmorras de nível " + nivel + " do mapa\n§8equipamento nível " + nivel + " ou melhor", 1.1f);
        }
        pl.getLogger().info("Portais dos niveis 2 a 5 construidos no lobby (oeste).");
    }

    /** Portal de 3 de vao x 4 de altura, eixo z (atravessa no x), com degrau na frente (lado leste, virado para o lobby). */
    private void portal(World w, int x, int y, int z, String moldura, String detalhe, String degrau) {
        for (int dz = -3; dz <= 3; dz++) for (int dy = 1; dy <= 8; dy++) por(w, x, y + dy, z + dz, "minecraft:air");
        for (Entity e : w.getNearbyEntities(new Location(w, x + 0.5, y + 7, z + 0.5), 1.5, 3, 1.5))
            if (e.getPersistentDataContainer().has(kEnfeite)) e.remove();
        for (int dz = -2; dz <= 2; dz++)
            for (int dy = 0; dy <= 5; dy++) {
                boolean borda = Math.abs(dz) == 2 || dy == 0 || dy == 5;
                String b;
                if (!borda) b = "minecraft:nether_portal[axis=z]";
                else if ((dy == 0 || dy == 5) && Math.abs(dz) == 2) b = detalhe;
                else if (dy % 2 == 1 && Math.abs(dz) == 2) b = detalhe;
                else b = moldura;
                por(w, x, y + dy, z + dz, b);
            }
        // abre o caminho na frente (lado do lobby): sem isso o portal pode nascer atras de uma parede
        for (int dx = 1; dx <= 6; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 5; dy++) por(w, x + dx, y + dy, z + dz, "minecraft:air");
                por(w, x + dx, y, z + dz, degrau);
            }
        por(w, x, y + 6, z, "minecraft:soul_lantern");
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

    // ------------------------------------------------------------------ nivel do equipamento

    /** Quanto vale a peca: couro/madeira 1 ... netherite 5. Nada na mao/no corpo vale 0. */
    private static int pontos(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0;
        String m = it.getType().name();
        if (m.startsWith("NETHERITE")) return 5;
        if (m.startsWith("DIAMOND")) return 4;
        if (m.startsWith("IRON") || m.startsWith("CHAINMAIL")) return 3;
        if (m.startsWith("GOLDEN") || m.startsWith("STONE")) return 2;
        if (m.startsWith("LEATHER") || m.startsWith("WOODEN") || m.startsWith("TURTLE")) return 1;
        if (m.equals("BOW") || m.equals("CROSSBOW") || m.equals("TRIDENT")) return 3;
        return 0;
    }

    /**
     * Nivel do equipamento: media das 4 pecas de armadura com a arma da mao (arredondando para baixo).
     * Quem esta de netherite inteiro da 5; quem esta de ferro da 3; quem esta pelado da 0.
     */
    static int nivelDosItens(Player p) {
        int soma = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) soma += pontos(it);
        soma += pontos(p.getInventory().getItemInMainHand());
        return soma / 5;
    }

    // ------------------------------------------------------------------ atravessar

    /** Para onde o portal leva: perto de uma masmorra daquele nivel; se nao houver, no anel daquele nivel. */
    private Location destino(int nivel) {
        World w = mm.normal;
        List<Masmorra> l = new ArrayList<>();
        for (Masmorra m : pl.todas)
            if (m.mundo.equals(w.getName()) && m.tipo.nivel == nivel && !m.demolindo && !m.tipo.secreta) l.add(m);
        if (!l.isEmpty()) {
            Masmorra m = l.get(ThreadLocalRandom.current().nextInt(l.size()));
            Location d = Loja.lugarPerto(w, m.saidaX, m.saidaY, m.saidaZ);
            return d != null ? d : new Location(w, m.saidaX + 0.5, m.saidaY + 1, m.saidaZ + 0.5);
        }
        return noAnel(nivel);
    }

    /** Um lugar seguro no anel daquele nivel (quando ainda nao ha masmorra construida la). */
    private Location noAnel(int nivel) {
        World w = mm.normal;
        int[] z = mm.zonas();
        int borda = pl.borda(Tipo.Onde.NORMAL);
        double r = switch (nivel) {
            case 2 -> (z[0] + z[1]) / 2.0;
            case 3 -> (z[1] + z[2]) / 2.0;
            case 4 -> z[2] + (borda - z[2]) * 0.3;
            default -> z[2] + (borda - z[2]) * 0.7;
        };
        for (int t = 0; t < 12; t++) {
            double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            int x = (int) (Math.cos(a) * r), zz = (int) (Math.sin(a) * r);
            Location d = Loja.lugarPerto(w, x, w.getHighestBlockYAt(x, zz) + 1, zz);
            if (d != null) return d;
        }
        double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        int x = (int) (Math.cos(a) * r), zz = (int) (Math.sin(a) * r);
        return new Location(w, x + 0.5, w.getHighestBlockYAt(x, zz) + 1, zz + 0.5);
    }

    /** Qual portal e este bloco (ou -1). */
    private int qual(Block b) {
        if (b.getX() != PX) return -1;
        for (int i = 0; i < PZ.length; i++) if (Math.abs(b.getZ() - PZ[i]) <= 1) return i;
        return -1;
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        if (!ativo()) return;
        Location to = e.getTo();
        World w = to.getWorld();
        if (mm.normal == null || !mm.normal.equals(w)) return;
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        Block b = to.getBlock();
        if (b.getType() != Material.NETHER_PORTAL) return;
        int i = qual(b);
        if (i < 0) return;
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        espera.put(p.getUniqueId(), agora + 4000L);
        int nivel = NIVEIS[i];
        int tem = nivelDosItens(p);
        if (tem < nivel && !p.hasPermission("masmorras.admin")) {
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§cEste portal é da §fárea de nível " + nivel + "§c. Seu equipamento está no nível §f" + tem
                    + "§c — volte com armadura e arma melhores (couro 1, pedra/ouro 2, ferro 3, diamante 4, netherite 5)."));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 0.8f);
            Location fora = new Location(w, PX + 1.5, to.getY(), b.getZ() + 0.5, to.getYaw(), to.getPitch());
            p.teleport(fora);
            return;
        }
        Location d = destino(nivel);
        p.setFallDistance(0);
        p.teleportAsync(d, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt(visual(nivel)[3] + "§lÁREA DE NÍVEL " + nivel),
                    Masmorras.txt("§7Use §f/masmorras menu §7para a bússola de uma masmorra daqui")));
            p.playSound(p.getLocation(), "minecraft:block.portal.travel", 0.5f, 1.1f - nivel * 0.1f);
            Masmorra perto = null;
            double md = Double.MAX_VALUE;
            for (Masmorra m : pl.todas) {
                if (!m.mundo.equals(p.getWorld().getName()) || m.tipo.nivel != nivel || m.demolindo || m.tipo.secreta) continue;
                double dd = pl.dist2(m, p.getLocation());
                if (dd < md) {
                    md = dd;
                    perto = m;
                }
            }
            if (perto != null) pl.darBussola(p, perto);
        });
    }
}
