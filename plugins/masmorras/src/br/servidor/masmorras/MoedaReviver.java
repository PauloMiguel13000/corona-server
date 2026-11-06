package br.servidor.masmorras;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Moedas de Reviver: quem e eliminado num minigame (Backrooms...) pode gastar uma e voltar para a partida.
 * Compra no site (/loja) ou com muitas coronas no Mercador Lendario. Fica guardada no proprio jogador, na chave
 * "masmorras:moedas_reviver" (os minigames leem e gastam direto dali, sem depender deste plugin).
 */
final class MoedaReviver {
    static final NamespacedKey CHAVE = NamespacedKey.fromString("masmorras:moedas_reviver");

    static int moedas(Player p) {
        Integer v = p.getPersistentDataContainer().get(CHAVE, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    static void definir(Player p, int v) {
        p.getPersistentDataContainer().set(CHAVE, PersistentDataType.INTEGER, Math.max(0, v));
        try {
            p.saveData();   // comprada com dinheiro: grava ja no disco
        } catch (Throwable ignored) {
        }
    }

    static void dar(Player p, int n) {
        if (n <= 0) return;
        definir(p, moedas(p) + n);
        p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§e§l+" + n + " MOEDA" + (n > 1 ? "S" : "") + " DE REVIVER"),
                Masmorras.txt("§7Você tem §e" + moedas(p) + "§7. Use quando for eliminado num minigame.")));
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
    }

    /** Item que aparece no menu do Mercador Lendario. */
    static ItemStack icone(int n) {
        ItemStack s = new ItemStack(Material.GOLD_NUGGET, Math.max(1, Math.min(64, n)));
        ItemMeta m = s.getItemMeta();
        m.displayName(Masmorras.txt("§e§lMoeda de Reviver" + (n > 1 ? " §7x" + n : "")));
        m.lore(java.util.List.of(
                Masmorras.txt("§7Foi eliminado num minigame (Backrooms...)?"),
                Masmorras.txt("§7Gaste uma moeda e volte para a partida."),
                Masmorras.txt("§8Fica guardada com você: §7/reviver")));
        m.setEnchantmentGlintOverride(true);
        m.addItemFlags(ItemFlag.values());
        s.setItemMeta(m);
        return s;
    }

    static boolean comando(CommandSender s, String[] a) {
        if (a.length >= 3 && (a[0].equalsIgnoreCase("dar") || a[0].equalsIgnoreCase("tirar")) && s.hasPermission("masmorras.admin")) {
            Player alvo = Bukkit.getPlayerExact(a[1]);
            int n;
            try {
                n = Integer.parseInt(a[2]);
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (alvo == null && n > 0 && a[0].equalsIgnoreCase("dar")) {
                // offline: fica guardado e entra quando a pessoa entrar
                org.bukkit.plugin.java.JavaPlugin pl = org.bukkit.plugin.java.JavaPlugin.getPlugin(Masmorras.class);
                String k = "reviver-pendentes." + a[1].toLowerCase(java.util.Locale.ROOT).replace('.', '_');
                pl.getConfig().set(k, pl.getConfig().getInt(k, 0) + n);
                pl.saveConfig();
                s.sendMessage(Masmorras.txt("§a" + a[1] + " está offline: §e" + n + " moeda(s) §aentram quando entrar."));
                Bukkit.getLogger().info("[Masmorras] moedas de reviver: dar " + n + " para " + a[1] + " (offline, fica guardado) por " + s.getName());
                return true;
            }
            if (alvo == null || n <= 0) {
                s.sendMessage(Masmorras.txt("§cUse: /reviver " + a[0] + " <jogador> <quantidade>"));
                return true;
            }
            if (a[0].equalsIgnoreCase("dar")) dar(alvo, n);
            else definir(alvo, moedas(alvo) - n);
            s.sendMessage(Masmorras.txt("§a" + alvo.getName() + " tem §e" + moedas(alvo) + " moeda(s) de reviver§a."));
            Bukkit.getLogger().info("[Masmorras] moedas de reviver: " + a[0] + " " + n + " para " + alvo.getName() + " por " + s.getName() + " (agora " + moedas(alvo) + ")");
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: reviver dar|tirar <jogador> <quantidade>");
            return true;
        }
        // eliminado num minigame: /reviver usa a moeda direto (manda para o comando do proprio jogo)
        String mundo = p.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        if (a.length == 0 && MINIGAMES.contains(mundo)) {
            p.performCommand(mundo + " reviver");
            return true;
        }
        p.sendMessage(Masmorras.txt("§e§lMoedas de Reviver: §f" + moedas(p)));
        p.sendMessage(Masmorras.txt("§7Eliminado num minigame? Aparece a opção de gastar uma e voltar pra partida."));
        p.sendMessage(Masmorras.txt("§7Consiga mais no site (§f/loja§7) ou no §5Mercador Lendário §7(§f/masmorras spawn§7)."));
        return true;
    }

    /** Moedas dadas com a pessoa offline: entram agora. */
    static void entregarPendentes(org.bukkit.plugin.java.JavaPlugin pl, Player p) {
        String k = "reviver-pendentes." + p.getName().toLowerCase(java.util.Locale.ROOT).replace('.', '_');
        int n = pl.getConfig().getInt(k, 0);
        if (n <= 0) return;
        pl.getConfig().set(k, null);
        pl.saveConfig();
        dar(p, n);
        Bukkit.getLogger().info("[Masmorras] moedas de reviver guardadas entregues: " + n + " para " + p.getName() + " (agora " + moedas(p) + ")");
    }

    /** Mundos dos minigames que aceitam "/<mundo> reviver". */
    static final java.util.Set<String> MINIGAMES = java.util.Set.of("backrooms", "naochore", "slender", "vovo", "hotel");

    private MoedaReviver() {}
}
