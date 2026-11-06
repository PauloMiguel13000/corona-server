package br.servidor.masmorras;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Coronas: o dinheiro do servidor. Ganha nas masmorras (monstros e chefes) e gasta nos Mercadores.
 * Fica guardado no proprio jogador (dados do jogador no mundo), entao nao se perde ao reiniciar.
 */
final class Coronas {

    private final Masmorras pl;
    private final NamespacedKey chave;

    Coronas(Masmorras pl) {
        this.pl = pl;
        this.chave = new NamespacedKey(pl, "coronas");
    }

    static String fmt(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v).replace(',', '.');
    }

    private static final NamespacedKey VIP_NIVEL = new NamespacedKey("extras", "vip_nivel");
    private static final NamespacedKey VIP_ATE = new NamespacedKey("extras", "vip_ate");
    /** Bonus de coronas por nivel de VIP (plugin Extras): VIP +10%, VIP+ +25%, MVP +35%, LENDA +50%. */
    static final int[] BONUS_VIP = {0, 10, 25, 35, 50};

    static int nivelVip(Player p) {
        Integer n = p.getPersistentDataContainer().get(VIP_NIVEL, PersistentDataType.INTEGER);
        Long ate = p.getPersistentDataContainer().get(VIP_ATE, PersistentDataType.LONG);
        if (n == null || n <= 0 || ate == null || ate < System.currentTimeMillis()) return 0;
        return Math.min(n, BONUS_VIP.length - 1);
    }

    /** Valor com o bonus do VIP (masmorras: monstros e chefes). */
    long comVip(Player p, long v) {
        int n = nivelVip(p);
        return n == 0 ? v : v + Math.max(1, v * BONUS_VIP[n] / 100);
    }

    long saldo(Player p) {
        Long v = p.getPersistentDataContainer().get(chave, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    void definir(Player p, long v) {
        p.getPersistentDataContainer().set(chave, PersistentDataType.LONG, Math.max(0, v));
    }

    void dar(Player p, long v, boolean avisar) {
        if (v <= 0) return;
        definir(p, saldo(p) + v);
        if (avisar) p.sendActionBar(Masmorras.txt("§6+" + fmt(v) + " coronas §7(saldo: §6" + fmt(saldo(p)) + "§7)"));
    }

    /** Tira se tiver; devolve false se nao tiver o bastante. */
    boolean tirar(Player p, long v) {
        long s = saldo(p);
        if (v < 0 || s < v) return false;
        definir(p, s - v);
        return true;
    }

    boolean comando(CommandSender s, String[] a) {
        if (a.length >= 3 && (a[0].equalsIgnoreCase("dar") || a[0].equalsIgnoreCase("tirar")) && s.hasPermission("masmorras.admin")) {
            Player alvo = Bukkit.getPlayerExact(a[1]);
            long v;
            try {
                v = Long.parseLong(a[2]);
            } catch (NumberFormatException e) {
                s.sendMessage(Masmorras.txt("§cValor inválido."));
                return true;
            }
            if (alvo == null || v <= 0) {
                s.sendMessage(Masmorras.txt("§cUse: /coronas " + a[0] + " <jogador online> <valor>"));
                return true;
            }
            if (a[0].equalsIgnoreCase("dar")) dar(alvo, v, true);
            else definir(alvo, saldo(alvo) - v);
            s.sendMessage(Masmorras.txt("§aSaldo de " + alvo.getName() + ": §6" + fmt(saldo(alvo)) + " coronas"));
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Use: coronas dar|tirar <jogador> <valor>");
            return true;
        }
        if (a.length >= 3 && a[0].equalsIgnoreCase("pagar")) {
            Player alvo = Bukkit.getPlayerExact(a[1]);
            long v;
            try {
                v = Long.parseLong(a[2]);
            } catch (NumberFormatException e) {
                v = -1;
            }
            if (alvo == null || alvo.equals(p) || v <= 0) {
                p.sendMessage(Masmorras.txt("§cUse: /coronas pagar <jogador online> <valor>"));
                return true;
            }
            if (!tirar(p, v)) {
                p.sendMessage(Masmorras.txt("§cVocê não tem " + fmt(v) + " coronas. Saldo: §6" + fmt(saldo(p))));
                return true;
            }
            dar(alvo, v, false);
            p.sendMessage(Masmorras.txt("§aVocê pagou §6" + fmt(v) + " coronas §aa " + alvo.getName() + "."));
            alvo.sendMessage(Masmorras.txt("§a" + p.getName() + " te pagou §6" + fmt(v) + " coronas§a. Saldo: §6" + fmt(saldo(alvo))));
            return true;
        }
        p.sendMessage(Masmorras.txt("§6§lCoronas: §e" + fmt(saldo(p))));
        p.sendMessage(Masmorras.txt("§7Ganhe matando monstros e chefes das masmorras. Gaste com os §6Mercadores §7(perto das masmorras e no §e/masmorras spawn§7)."));
        p.sendMessage(Masmorras.txt("§7Pagar alguém: §f/coronas pagar <jogador> <valor>"));
        return true;
    }
}
