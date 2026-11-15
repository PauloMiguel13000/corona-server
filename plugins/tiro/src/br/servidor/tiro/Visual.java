package br.servidor.tiro;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Texturas dos jogos de tiro (15/11): um pacote pequeno so deles (armas desenhadas e a pele de cada infectado),
 * separado do pacote principal. Java recebe por link ao entrar (sem obrigar); Bedrock recebe pelo CoronaPack.
 * O infectado e o zumbi de verdade do jogo (anda e ataca animado) vestindo 4 pecas cuja textura e a pele do tipo.
 * Quem nao tem o pacote ve couro tingido no lugar (mandado so para ele) e a arma como enxada.
 */
final class Visual implements Listener {

    static final String URL_PADRAO = "https://corona-server.duckdns.org/pacotes/corona_tiro_java-a9a3ae25f4.zip";
    static final String SHA1_PADRAO = "a9a3ae25f4fedc4aace0467895ee863ee6c714f7";
    private static final UUID ID = UUID.nameUUIDFromBytes("corona-tiro-pack".getBytes(StandardCharsets.UTF_8));

    private final Tiro pl;
    private final Set<UUID> comPacote = ConcurrentHashMap.newKeySet();
    private int conta;

    Visual(Tiro pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 2L, 1L);
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("pacote.ligado", true);
    }

    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || p.getName().startsWith(".");
    }

    /** Ve as texturas dos jogos de tiro? (Bedrock sempre: o pacote dele vem junto com os outros.) */
    boolean tem(Player p) {
        return ligado() && (bedrock(p) || comPacote.contains(p.getUniqueId()));
    }

    // ------------------------------------------------------------------ pacote do Java

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!ligado() || bedrock(p)) return;
        // depois do pacote principal (~3 s) e dos de musica (12 s)
        Bukkit.getScheduler().runTaskLater(pl, () -> mandar(p), 20L * 17);
    }

    void mandar(Player p) {
        if (!p.isOnline() || bedrock(p) || comPacote.contains(p.getUniqueId())) return;
        try {
            p.addResourcePack(ID, pl.getConfig().getString("pacote.java-url", URL_PADRAO),
                    HexFormat.of().parseHex(pl.getConfig().getString("pacote.java-sha1", SHA1_PADRAO)),
                    "§e§lCorona Server§r\n§fArmas e infectados 3D dos jogos de tiro", false);
        } catch (Throwable t) {
            pl.getLogger().warning("Pacote dos jogos de tiro para " + p.getName() + ": " + t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void status(PlayerResourcePackStatusEvent e) {
        if (!ID.equals(e.getID())) return;
        UUID u = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> comPacote.add(u);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> comPacote.remove(u);
            default -> { }
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        comPacote.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ arma desenhada

    /** Deixa as armas da mochila com o desenho (quem tem o pacote) ou como enxada (quem nao tem). */
    @SuppressWarnings("UnstableApiUsage")
    void armas(Player p) {
        boolean quer = tem(p);
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            Armas.Arma a = pl.armas.armaDe(it);
            if (a == null) continue;
            Key certo = Key.key("corona", "armas/" + a.id());
            Key tem = it.getData(DataComponentTypes.ITEM_MODEL);
            boolean desenhada = certo.equals(tem);
            if (quer == desenhada) continue;
            if (quer) it.setData(DataComponentTypes.ITEM_MODEL, certo);
            else it.resetData(DataComponentTypes.ITEM_MODEL);
            p.getInventory().setItem(i, it);
        }
    }

    // ------------------------------------------------------------------ pele dos infectados

    private static final Map<EquipmentSlot, String> PECAS = Map.of(EquipmentSlot.HEAD, "elmo", EquipmentSlot.CHEST, "peitoral",
            EquipmentSlot.LEGS, "calca", EquipmentSlot.FEET, "botas");
    private static final Map<EquipmentSlot, Material> COURO = Map.of(EquipmentSlot.HEAD, Material.LEATHER_HELMET, EquipmentSlot.CHEST, Material.LEATHER_CHESTPLATE,
            EquipmentSlot.LEGS, Material.LEATHER_LEGGINGS, EquipmentSlot.FEET, Material.LEATHER_BOOTS);

    /** Veste o infectado com a pele do tipo dele: 4 pecas de couro cuja "armadura" e a textura corona:z<tipo> do pacote. */
    @SuppressWarnings("UnstableApiUsage")
    void vestir(Zombie z, Zumbis.Tipo t) {
        if (!ligado()) return;
        String tipo = t.name().toLowerCase();
        var eq = z.getEquipment();
        for (var en : PECAS.entrySet()) {
            ItemStack it = new ItemStack(COURO.get(en.getKey()));
            it.setData(DataComponentTypes.ITEM_MODEL, Key.key("corona", "zumbi/" + tipo + "_" + en.getValue()));
            it.setData(DataComponentTypes.EQUIPPABLE, io.papermc.paper.datacomponent.item.Equippable.equippable(en.getKey())
                    .assetId(Key.key("corona", "z" + tipo)).build());
            eq.setItem(en.getKey(), it);
            eq.setDropChance(en.getKey(), 0f);
        }
    }

    /** Roupa de couro tingida que quem NAO tem o pacote ve no lugar da pele (so para ele). */
    private ItemStack simples(EquipmentSlot s, Zumbis.Tipo t) {
        ItemStack it = new ItemStack(COURO.get(s));
        org.bukkit.inventory.meta.LeatherArmorMeta m = (org.bukkit.inventory.meta.LeatherArmorMeta) it.getItemMeta();
        m.setColor(org.bukkit.Color.fromRGB(switch (t) {
            case CORREDOR -> 0xB02020;
            case TANQUE -> 0x70747C;
            case CUSPIDOR -> 0x58C030;
            case EXPLOSIVO -> 0xE0C020;
            case CHEFE -> 0x1A1820;
            default -> 0x3A6090;
        }));
        it.setItemMeta(m);
        return it;
    }

    private void tique() {
        conta++;
        if (conta % 40 != 0 || !ligado()) return;
        for (World w : Bukkit.getWorlds()) {
            if (!pl.mundoDeTiro(w)) continue;
            for (Player p : w.getPlayers()) {
                armas(p);
                if (tem(p)) continue;
                // sem o pacote a pele apareceria como textura que falta: este jogador ve couro tingido
                for (Entity e : p.getNearbyEntities(48, 32, 48)) {
                    if (!(e instanceof Zombie z)) continue;
                    Zumbis.Tipo t = pl.zumbis.tipo(z);
                    if (t == null) continue;
                    for (EquipmentSlot s : PECAS.keySet()) p.sendEquipmentChange(z, s, s == EquipmentSlot.HEAD ? new ItemStack(Material.AIR) : simples(s, t));
                }
            }
        }
    }

    void limpar() {
    }
}
