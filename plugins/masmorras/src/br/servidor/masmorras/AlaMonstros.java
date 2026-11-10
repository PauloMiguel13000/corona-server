package br.servidor.masmorras;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ala dos Monstros (10/11): no mundo da Galeria, longe do salao das armaduras, uma passarela com TODOS os monstros 3D
 * parados em pedestais, com nome, para o dono conferir os modelos e as texturas de perto. So admin chega la
 * (/warp monstros). Os modelos sao os mesmos itens do pacote que os jogos usam; os do Nao Chore e do Backrooms sao do
 * pacote do Java (no Bedrock esses dois jogos usam outro tipo de modelo e aqui nao aparecem).
 * Os blocos sao feitos uma vez (marca galeria-monstros-vN.txt); as estatuas renascem a cada ligada do servidor.
 */
final class AlaMonstros {
    static final int VERSAO = 1;
    /** Passarela: x de X0 a X1, z de -7 a 7; fileiras em z = -5 (olhando para o sul) e z = 5 (olhando para o norte). */
    static final int X0 = 60, PASSO = 5, POR_FILA = 11, X1 = X0 + 6 + PASSO * (POR_FILA - 1) + 5;

    /** tipo: 'p' = prop (centro no meio do bloco); 'm' = modelo de monstro do gerar.py (centro no canto). */
    record Monstro(String nome, String jogo, char tipo, String modelo, String olhos, float escala) {}

    static final Monstro[] MONSTROS = {
            new Monstro("§fA Criatura", "§7Floresta (/slender)", 'p', "props/porcelana_f0", null, 1.3f),
            new Monstro("§fA Criatura §c(susto)", "§7Floresta (/slender)", 'p', "props/porcelana_f1", null, 1.3f),
            new Monstro("§6Capi, a capivara", "§7Turno da Noite", 'p', "props/anim_capi_f0", null, 1.2f),
            new Monstro("§eTuca, o tucano", "§7Turno da Noite", 'p', "props/anim_tuca_f0", null, 1.2f),
            new Monstro("§6Jaca, a onça", "§7Turno da Noite", 'p', "props/anim_jaca_f0", null, 1.2f),
            new Monstro("§dBoto", "§7Turno da Noite", 'p', "props/anim_boto_f0", null, 1.2f),
            new Monstro("§6Capi §c(susto)", "§7Turno da Noite", 'p', "props/anim_capi_f1", null, 1.2f),
            new Monstro("§5A Vovó", "§7Casa da Vovó (/vovo)", 'p', "props/vovo_idle", null, 1.2f),
            new Monstro("§5A Vovó §c(grito)", "§7Casa da Vovó (/vovo)", 'p', "props/vovo_grito", null, 1.2f),
            new Monstro("§9A Chorona", "§7Não Chore §8(só no Java)", 'm', "naochore/chorona_idle", "naochore/chorona_olhos_idle", 1.0f),
            new Monstro("§9O Vigia", "§7Não Chore §8(só no Java)", 'm', "naochore/vigia_idle", "naochore/vigia_olhos_idle", 1.1f),
            new Monstro("§9O Faminto", "§7Não Chore §8(só no Java)", 'm', "naochore/faminto_idle", "naochore/faminto_olhos_idle", 1.2f),
            new Monstro("§9O Duplo", "§7Não Chore §8(só no Java)", 'm', "naochore/duplo_idle", "naochore/duplo_olhos_idle", 1.08f),
            new Monstro("§9As Mãos", "§7Não Chore §8(só no Java)", 'm', "naochore/maos_idle", null, 1.3f),
            new Monstro("§eBactéria", "§7Backrooms §8(só no Java)", 'm', "backrooms/bacteria_idle", "backrooms/bacteria_olhos_idle", 1.0f),
            new Monstro("§eSombra", "§7Backrooms §8(só no Java)", 'm', "backrooms/sombra_idle", "backrooms/sombra_olhos_idle", 1.0f),
            new Monstro("§eUivador", "§7Backrooms §8(só no Java)", 'm', "backrooms/uivador_idle", "backrooms/uivador_olhos_idle", 1.0f),
            new Monstro("§eCorredor", "§7Backrooms §8(só no Java)", 'm', "backrooms/corredor_idle", "backrooms/corredor_olhos_idle", 1.0f),
            new Monstro("§eCão", "§7Backrooms §8(só no Java)", 'm', "backrooms/cao_idle", "backrooms/cao_olhos_idle", 1.0f),
            new Monstro("§eAfogado", "§7Backrooms §8(só no Java)", 'm', "backrooms/afogado_idle", null, 1.0f),
            new Monstro("§eFesteiro", "§7Backrooms §8(só no Java)", 'm', "backrooms/festeiro_idle", null, 1.0f)};

    private final Masmorras pl;
    private final Galeria galeria;
    private final NamespacedKey marca;
    private final List<Entity> vivos = new ArrayList<>();

    AlaMonstros(Masmorras pl, Galeria galeria) {
        this.pl = pl;
        this.galeria = galeria;
        this.marca = new NamespacedKey(pl, "ala_monstros");
    }

    Location chegada() {
        return new Location(galeria.mundo, X0 + 1.5, Galeria.Y + 1, 0.5, -90f, 0f);
    }

    void iniciar() {
        World w = galeria.mundo;
        if (w == null || !pl.getConfig().getBoolean("galeria.monstros", true)) return;
        // os pedacos ficam sempre carregados: as estatuas nao sao salvas e nao podem sumir quando o lugar esvazia
        for (int cx = (X0 - 2) >> 4; cx <= (X1 + 2) >> 4; cx++)
            for (int cz = -1; cz <= 0; cz++) {
                w.getChunkAt(cx, cz).load(true);
                w.getChunkAt(cx, cz).setForceLoaded(true);
            }
        File feito = new File(pl.getDataFolder(), "galeria-monstros-v" + VERSAO + ".txt");
        if (!feito.exists()) {
            try {
                construir(w);
                Files.writeString(feito.toPath(), "construida em " + new java.util.Date() + "\n");
            } catch (Throwable t) {
                pl.getLogger().warning("Ala dos Monstros nao terminou de construir: " + t);
            }
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> estatuas(w), 60L);
    }

    private static void por(World w, int x, int y, int z, String b) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false);
    }

    private int xDe(int i) {
        return X0 + 6 + PASSO * (i % POR_FILA);
    }

    private int zDe(int i) {
        return (i / POR_FILA) % 2 == 0 ? -5 : 5;
    }

    private void construir(World w) {
        int y = Galeria.Y;
        int zMax = 7;
        for (int x = X0; x <= X1; x++)
            for (int z = -7; z <= zMax; z++) {
                boolean borda = x == X0 || x == X1 || z == -7 || z == zMax;
                por(w, x, y, z, Math.abs(z) <= 1 ? "minecraft:smooth_quartz" : (x + z) % 2 == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:polished_blackstone");
                por(w, x, y - 1, z, "minecraft:blackstone");
                if ((x - X0) % 5 == 3 && Math.abs(z) == 2) por(w, x, y, z, "minecraft:sea_lantern");
                for (int dy = 1; dy <= 6; dy++) por(w, x, y + dy, z, borda && dy <= 3 ? "minecraft:barrier" : "minecraft:air");
            }
        for (int i = 0; i < MONSTROS.length; i++) por(w, xDe(i), y + 1, zDe(i), "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]");
    }

    @SuppressWarnings("UnstableApiUsage")
    private ItemDisplay modelo(World w, Location l, char tipo, String chave, float s) {
        ItemStack it = new ItemStack(tipo == 'p' ? Material.LEATHER_HELMET : Material.PAPER);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", chave));
        Vector3f t = tipo == 'p' ? new Vector3f(0, 1.5f * s, 0) : new Vector3f(-0.5f * s, 1.5f * s, -0.5f * s);
        return w.spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(it);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(t, new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void estatuas(World w) {
        for (Entity e : vivos) if (e.isValid()) e.remove();
        vivos.clear();
        for (Entity e : w.getEntities()) if (e.getPersistentDataContainer().has(marca, PersistentDataType.BYTE)) e.remove();
        for (int i = 0; i < MONSTROS.length; i++) {
            Monstro m = MONSTROS[i];
            int x = xDe(i), z = zDe(i);
            float yaw = z < 0 ? 0f : 180f;
            Location l = new Location(w, x + 0.5, Galeria.Y + 1.5, z + 0.5, yaw, 0f);
            vivos.add(modelo(w, l, m.tipo(), m.modelo(), m.escala()));
            if (m.olhos() != null) vivos.add(modelo(w, l, m.tipo(), m.olhos(), m.escala()));
            Location placa = new Location(w, x + 0.5, Galeria.Y + 1.1, z + 0.5 + (z < 0 ? 1.4 : -1.4), yaw, 0f);
            vivos.add(w.spawn(placa, TextDisplay.class, d -> {
                d.text(Masmorras.txt(m.nome() + "\n" + m.jogo()));
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
                d.setSeeThrough(false);
                d.setShadowed(true);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
                d.setPersistent(false);
                d.getPersistentDataContainer().set(marca, PersistentDataType.BYTE, (byte) 1);
            }));
        }
    }

    /** /warp [armaduras|monstros]: atalho do dono para conferir as armaduras e os monstros 3D. */
    boolean warp(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Use no jogo: /warp armaduras | /warp monstros");
            return true;
        }
        if (!p.hasPermission("masmorras.admin")) {
            p.sendMessage(Masmorras.txt("§cO /warp é só para a equipe. Para ver as armaduras use §f/galeria§c."));
            return true;
        }
        if (galeria.mundo == null) {
            p.sendMessage(Masmorras.txt("§cA Galeria está fechada agora."));
            return true;
        }
        String para = a.length == 0 ? "" : a[0].toLowerCase();
        switch (para) {
            case "armaduras", "armadura", "galeria" -> {
                p.teleport(galeria.chegada());
                p.sendMessage(Masmorras.txt("§6§lArmaduras §7- todos os conjuntos, no Java e no Bedrock. §8Monstros: /warp monstros"));
            }
            case "monstros", "monstro" -> {
                p.teleport(chegada());
                p.sendMessage(Masmorras.txt("§5§lAla dos Monstros §7- " + MONSTROS.length + " modelos parados para conferir a textura. §8Armaduras: /warp armaduras"));
            }
            default -> p.sendMessage(Masmorras.txt("§eUse: §f/warp armaduras §7ou §f/warp monstros"));
        }
        return true;
    }
}
