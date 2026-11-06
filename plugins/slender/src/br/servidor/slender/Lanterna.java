package br.servidor.slender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Lanterna na mao de quem esta jogando: ilumina o ponto para onde o jogador olha (ate lanterna-alcance blocos)
 * e um pouco na frente dele. O Minecraft nao tem luz que anda junto com o jogador, entao a luz e um bloco de luz
 * invisivel mandado so para os jogadores (o mundo de verdade nao muda e nada fica para tras). Cada jogador do
 * mundo do Slender ve a lanterna dos outros. Com o Slenderman perto, a lanterna falha (pisca) as vezes.
 */
final class Lanterna {

    record Pos(int x, int y, int z) {}

    private final Slender pl;
    private final NamespacedKey chave;
    private final Random rnd = new Random();
    private final BlockData[] luzes = new BlockData[16];
    /** Quem esta com a lanterna na mao (trocavel nos testes). */
    Predicate<Player> segura = this::segurando;
    /** O que cada jogador do mundo esta vendo agora (posicao -> nivel de luz). */
    private Map<Pos, Integer> atual = new HashMap<>();
    private final Set<UUID> vendo = new HashSet<>();
    private long voltas;

    Lanterna(Slender pl, NamespacedKey chave) {
        this.pl = pl;
        this.chave = chave;
    }

    private BlockData luz(int nivel) {
        if (luzes[nivel] == null) {
            Light l = (Light) Material.LIGHT.createBlockData();
            l.setLevel(nivel);
            luzes[nivel] = l;
        }
        return luzes[nivel];
    }

    ItemStack item() {
        ItemStack it = new ItemStack(Material.LANTERN);
        ItemMeta m = it.getItemMeta();
        m.displayName(Slender.txt("§e§lLanterna").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        m.lore(List.of(Slender.txt("§7Ilumina para onde você olha.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(chave, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean eLanterna(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(chave, PersistentDataType.BYTE);
    }

    /** Da a lanterna na mao esquerda (a direita fica livre para pegar as folhas). */
    void dar(Player p) {
        if (!pl.getConfig().getBoolean("lanterna", true)) return;
        PlayerInventory inv = p.getInventory();
        if (eLanterna(inv.getItemInOffHand()) || eLanterna(inv.getItemInMainHand())) return;
        inv.setItemInOffHand(item());
        p.sendMessage(Slender.PREFIXO + "§fSua §elanterna§f está na mão esquerda: ela ilumina para onde você olha.");
    }

    /** Tira a lanterna do inventario (quem sai do mundo do Slender nao leva). */
    void recolher(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        boolean mudou = false;
        for (int i = 0; i < c.length; i++)
            if (eLanterna(c[i])) {
                c[i] = null;
                mudou = true;
            }
        if (mudou) inv.setContents(c);
    }

    private boolean segurando(Player p) {
        PlayerInventory inv = p.getInventory();
        return eLanterna(inv.getItemInMainHand()) || eLanterna(inv.getItemInOffHand());
    }

    /**
     * Roda a cada 2 ticks. vivo = quem esta jogando; estatica = medo (0 a 100, perto do Slenderman sobe);
     * proibidos = onde ficam as folhas (no Bedrock a moldura e um bloco, e a luz apagaria a folha).
     */
    void tick(World mundo, Predicate<Player> vivo, ToDoubleFunction<Player> estatica, Set<Pos> proibidos) {
        voltas++;
        Map<Pos, Integer> novo = new HashMap<>();
        boolean ligada = pl.getConfig().getBoolean("lanterna", true);
        if (ligada && mundo != null) {
            double alcance = Math.max(3, Math.min(48, pl.getConfig().getDouble("lanterna-alcance", 16)));
            int forte = Math.max(1, Math.min(15, pl.getConfig().getInt("lanterna-luz", 12)));
            int fraca = Math.max(0, Math.min(15, pl.getConfig().getInt("lanterna-luz-perto", 6)));
            for (Player p : mundo.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !vivo.test(p) || !segura.test(p)) continue;
                // perto do Slenderman a lanterna falha de vez em quando
                double est = estatica.applyAsDouble(p);
                if (est >= 35 && rnd.nextDouble() < est / 250) continue;
                Location olho = p.getEyeLocation();
                Vector dir = olho.getDirection();
                RayTraceResult r = mundo.rayTraceBlocks(olho, dir, alcance, FluidCollisionMode.NEVER, true);
                double dist = r == null ? alcance : r.getHitPosition().distance(olho.toVector());
                Block longe = livreAntes(olho, dir, dist - 0.5, proibidos);
                if (longe != null) juntar(novo, longe, forte);
                if (fraca > 0) {
                    Block perto = livreAntes(olho, dir, Math.min(2.0, dist - 0.5), proibidos);
                    if (perto != null) juntar(novo, perto, fraca);
                }
            }
        }
        enviar(mundo, novo);
    }

    /** O primeiro bloco de ar andando de volta pelo raio (luz nao pode ficar dentro de bloco). */
    static Block livreAntes(Location olho, Vector dir, double d, Set<Pos> proibidos) {
        for (double t = d; t >= 0.3; t -= 0.5) {
            Block b = olho.clone().add(dir.clone().multiply(t)).getBlock();
            if (livre(b, proibidos)) return b;
        }
        Block b = olho.getBlock();
        return livre(b, proibidos) ? b : null;
    }

    private static boolean livre(Block b, Set<Pos> proibidos) {
        Material m = b.getType();
        return (m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR) && !proibidos.contains(new Pos(b.getX(), b.getY(), b.getZ()));
    }

    private static void juntar(Map<Pos, Integer> m, Block b, int nivel) {
        m.merge(new Pos(b.getX(), b.getY(), b.getZ()), nivel, Math::max);
    }

    private void enviar(World mundo, Map<Pos, Integer> novo) {
        List<Player> quem = mundo == null ? List.of() : new ArrayList<>(mundo.getPlayers());
        boolean tudo = voltas % 20 == 0; // a cada 2 s manda tudo de novo (chunk recarregado, quem acabou de chegar)
        Set<UUID> agora = new HashSet<>();
        for (Player v : quem) {
            agora.add(v.getUniqueId());
            boolean chegou = !vendo.contains(v.getUniqueId());
            for (Map.Entry<Pos, Integer> e : atual.entrySet())
                if (!novo.containsKey(e.getKey())) devolver(v, mundo, e.getKey());
            for (Map.Entry<Pos, Integer> e : novo.entrySet()) {
                Integer antes = atual.get(e.getKey());
                if (tudo || chegou || antes == null || !antes.equals(e.getValue())) mostrar(v, mundo, e.getKey(), e.getValue());
            }
        }
        vendo.clear();
        vendo.addAll(agora);
        atual = novo;
    }

    private void mostrar(Player v, World w, Pos p, int nivel) {
        v.sendBlockChange(new Location(w, p.x(), p.y(), p.z()), luz(nivel));
    }

    private void devolver(Player v, World w, Pos p) {
        v.sendBlockChange(new Location(w, p.x(), p.y(), p.z()), w.getBlockAt(p.x(), p.y(), p.z()).getBlockData());
    }

    /** Apaga tudo (plugin desligando). */
    void apagar(World mundo) {
        if (mundo == null) return;
        for (Player v : mundo.getPlayers())
            for (Pos p : atual.keySet()) devolver(v, mundo, p);
        atual = new HashMap<>();
        vendo.clear();
    }

    Map<Pos, Integer> luzesAgora() {
        return atual;
    }
}
