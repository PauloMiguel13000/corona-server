package br.servidor.masmorras;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * 17/11 (Paulo: "estao marcando sethome do lado das masmorras pra ficarem refazendo"): no mundo das masmorras o
 * /sethome (plugin SetHome) nao funciona perto de masmorra e cada um tem no maximo 2 casas la (VIP: 3). O /home para
 * uma casa antiga que ficou perto de masmorra tambem nao leva mais. Le as casas de plugins/SetHome/homes.yml.
 */
final class CasasMasmorra implements Listener {

    private final Masmorras pl;
    private final Map<UUID, Long> usandoHome = new HashMap<>();
    private static final NamespacedKey VIP_NIVEL = new NamespacedKey("extras", "vip_nivel");
    private static final NamespacedKey VIP_ATE = new NamespacedKey("extras", "vip_ate");

    CasasMasmorra(Masmorras pl) {
        this.pl = pl;
    }

    private static boolean mundoDasMasmorras(World w) {
        return w != null && w.getName().toLowerCase(Locale.ROOT).startsWith("masmorras");
    }

    private int distancia() {
        return pl.getConfig().getInt("casas.distancia-masmorra", 64);
    }

    /** Masmorra a menos de N blocos daqui (ou null). */
    Masmorra pertoDeMasmorra(Location l) {
        if (l == null || l.getWorld() == null) return null;
        int d = distancia();
        for (Masmorra m : pl.todas) {
            if (m.demolindo || m.tudo == null || !l.getWorld().getName().equals(m.mundo)) continue;
            if (m.tudo.crescer(d).contem(l.getX(), l.getY(), l.getZ())) return m;
        }
        return null;
    }

    static boolean vip(Player p) {
        Integer n = p.getPersistentDataContainer().get(VIP_NIVEL, PersistentDataType.INTEGER);
        Long ate = p.getPersistentDataContainer().get(VIP_ATE, PersistentDataType.LONG);
        return n != null && n >= 1 && ate != null && ate >= System.currentTimeMillis();
    }

    int limite(Player p) {
        return vip(p) ? pl.getConfig().getInt("casas.max-masmorras-vip", 3) : pl.getConfig().getInt("casas.max-masmorras", 2);
    }

    /** Casas do jogador que ficam no mundo das masmorras (sem contar a de nome 'menos', que vai ser trocada). */
    int casasNasMasmorras(Player p, String menos) {
        File f = new File(pl.getDataFolder().getParentFile(), "SetHome/homes.yml");
        if (!f.exists()) return 0;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection(p.getUniqueId().toString());
        if (s == null) return 0;
        int n = 0;
        for (String nome : s.getKeys(false)) {
            if (menos != null && nome.equalsIgnoreCase(menos)) continue;
            String w = s.getString(nome + ".world", "");
            if (w.toLowerCase(Locale.ROOT).startsWith("masmorras")) n++;
        }
        return n;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void comando(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String[] a = e.getMessage().trim().split("\\s+");
        if (a.length == 0) return;
        String cmd = a[0].toLowerCase(Locale.ROOT);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.startsWith("sethome:")) cmd = cmd.substring(8);
        if (cmd.equals("home")) {
            usandoHome.put(p.getUniqueId(), System.currentTimeMillis());
            return;
        }
        if (!cmd.equals("sethome") || a.length < 2) return;
        if (!pl.getConfig().getBoolean("casas.ativo", true) || p.hasPermission("masmorras.admin") || !mundoDasMasmorras(p.getWorld())) return;
        Masmorra m = pertoDeMasmorra(p.getLocation());
        if (m != null) {
            e.setCancelled(true);
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§cNão dá para marcar casa perto de masmorra §7(" + m.tipo.nome + ")§c. Afaste-se mais de §f"
                    + distancia() + " §cblocos dela."));
            return;
        }
        int lim = limite(p), tem = casasNasMasmorras(p, a[1]);
        if (tem >= lim) {
            e.setCancelled(true);
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§cNo Mundo das Masmorras cada um pode ter até §f" + lim + " §ccasa" + (lim == 1 ? "" : "s")
                    + (vip(p) ? "" : " §7(VIP: " + pl.getConfig().getInt("casas.max-masmorras-vip", 3) + ")") + "§c. §7Apague uma com §f/delhome nome§7."));
        }
    }

    /** /home para uma casa que ficou perto de masmorra nao leva mais. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleporte(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Long u = usandoHome.get(p.getUniqueId());
        if (u == null) return;
        if (System.currentTimeMillis() - u > 3000) {
            usandoHome.remove(p.getUniqueId());
            return;
        }
        usandoHome.remove(p.getUniqueId());
        if (!pl.getConfig().getBoolean("casas.ativo", true) || p.hasPermission("masmorras.admin") || !mundoDasMasmorras(e.getTo().getWorld())) return;
        Masmorra m = pertoDeMasmorra(e.getTo());
        if (m == null) return;
        e.setCancelled(true);
        p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§cEssa casa fica colada na masmorra §f" + m.tipo.nome + " §ce não funciona mais. §7Apague com §f/delhome nome §7e marque outra a mais de "
                + distancia() + " blocos."));
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        usandoHome.remove(e.getPlayer().getUniqueId());
    }
}
