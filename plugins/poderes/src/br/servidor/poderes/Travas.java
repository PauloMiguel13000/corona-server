package br.servidor.poderes;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Travas das classes (pedido do Paulo, 07/11): LUTADOR e SAIYAJIN so para VIP MVP para cima (MVP e LENDA); CIPO e TRIDENTE
 * so para VIPs (qualquer nivel). Quem nao tem o VIP pode COMPRAR a classe (bem caro) no Vendedor de Classes do lobby das
 * masmorras: a compra fica no jogador (PDC poderes:comprada_<classe>) e vale para sempre. Config classes.travas e
 * classes.preco.
 */
final class Travas {

    static final String[] NIVEL_VIP = {"", "VIP", "VIP+", "MVP", "LENDA"};
    private static final NamespacedKey VIP_NIVEL = new NamespacedKey("extras", "vip_nivel"), VIP_ATE = new NamespacedKey("extras", "vip_ate");
    private final Poderes pl;

    Travas(Poderes pl) {
        this.pl = pl;
    }

    /** Nivel de VIP exigido pela classe (0 = livre). */
    int vipExigido(Classe c) {
        int padrao = switch (c) {
            case LUTADOR, SAIYAJIN -> 3;
            case CIPO, TRIDENTE -> 1;
            default -> 0;
        };
        return pl.getConfig().getInt("classes.travas." + c.id, padrao);
    }

    /** Preco em coronas para quem nao tem o VIP (0 = nao vende). */
    long preco(Classe c) {
        long padrao = switch (c) {
            case LUTADOR -> 90_000;
            case SAIYAJIN -> 120_000;
            case CIPO, TRIDENTE -> 50_000;
            default -> 0;
        };
        return pl.getConfig().getLong("classes.preco." + c.id, padrao);
    }

    static int vip(Player p) {
        Integer n = p.getPersistentDataContainer().get(VIP_NIVEL, PersistentDataType.INTEGER);
        Long ate = p.getPersistentDataContainer().get(VIP_ATE, PersistentDataType.LONG);
        if (n == null || n < 1 || n > 4 || ate == null || ate < System.currentTimeMillis()) return 0;
        return n;
    }

    static NamespacedKey chaveCompra(Classe c) {
        return new NamespacedKey("poderes", "comprada_" + c.id);
    }

    static boolean comprou(Player p, Classe c) {
        return p.getPersistentDataContainer().has(chaveCompra(c), PersistentDataType.BYTE);
    }

    /** Marca a classe como comprada (quem vende e o Masmorras; aqui e so para o admin / testes). */
    static void marcarComprada(Player p, Classe c) {
        p.getPersistentDataContainer().set(chaveCompra(c), PersistentDataType.BYTE, (byte) 1);
    }

    boolean liberada(Player p, Classe c) {
        int exige = vipExigido(c);
        if (exige <= 0 || p.hasPermission("poderes.todas-classes")) return true;
        return vip(p) >= exige || comprou(p, c);
    }

    /** Texto curto do que falta (para o menu e para o aviso). */
    String porque(Classe c) {
        int exige = vipExigido(c);
        String vips = exige >= 3 ? "§6MVP §7ou §dLENDA" : exige == 2 ? "§bVIP+§7, §6MVP §7ou §dLENDA" : "§aVIP §7(qualquer nível)";
        long preco = preco(c);
        return "§7Só para " + vips + (preco > 0 ? " §7— ou compre por §6" + fmt(preco) + " coronas §7no Vendedor de Classes (lobby das masmorras)" : "");
    }

    static String fmt(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v).replace(',', '.');
    }
}
