package br.servidor.masmorras;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;

/**
 * Atalhos do dono para testar os visuais (10/11): /dararmadura <nick> <nome> e /darespada <nick> <nome>.
 * O nome e o do conjunto: "herobrine", "rei esqueleto", "farao"... (sem acento, com espaco ou _; tambem aceita o id
 * da masmorra, tipo CRIPTA). Da 1 de cada peca. "todas" entrega tudo dentro de caixas de shulker.
 * "rei" (Traje do Rei do VIP) vai pelo /vip traje dar, que e o caminho que ja cuida do dono do traje.
 */
final class DarItens {

    private static final Material[] ARMADURA = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};

    /** chave (sem acento, sem espaco) -> id do conjunto: id do Tipo, "HEROBRINE" (cosmetico da loja) ou "REI". */
    private final Map<String, String> chaves = new LinkedHashMap<>();
    /** id do conjunto -> nome que aparece na lista e nas mensagens. */
    private final Map<String, String> nomes = new LinkedHashMap<>();
    private final Masmorras pl;

    DarItens(Masmorras pl) {
        this.pl = pl;
        nomes.put("HEROBRINE", "Herobrine");
        chaves.put("herobrine", "HEROBRINE");
        for (Tipo t : Tipo.TODOS.values()) {
            Reliquias.Tema tm = Reliquias.TEMAS.get(t.id);
            if (tm == null) continue;
            // "do Rei Esqueleto" -> "Rei Esqueleto"; a masmorra secreta tambem e "de Herobrine": fica como "Secreta"
            String nome = t.secreta ? "Secreta" : semArtigo(tm.de());
            nomes.put(t.id, nome);
            chaves.putIfAbsent(limpo(nome), t.id);
            chaves.putIfAbsent(limpo(t.id), t.id);
            if (!t.secreta && t.nome != null && !t.nome.contains("?")) chaves.putIfAbsent(limpo(t.nome), t.id);
        }
        nomes.put("REI", "Rei (Traje do Rei do VIP)");
        for (String k : new String[] {"rei", "trajedorei", "traje", "vip", "trajerei"}) chaves.put(k, "REI");
    }

    private static String semArtigo(String de) {
        String s = de.trim();
        for (String art : new String[] {"do ", "da ", "de ", "dos ", "das "})
            if (s.toLowerCase(Locale.ROOT).startsWith(art)) return s.substring(art.length());
        return s;
    }

    /** minusculo, sem acento, sem espaco/_/-, e sem "armadura/espada/do/da/de" na frente. */
    static String limpo(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replace('_', ' ').replace('-', ' ').trim();
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (String p : new String[] {"armadura ", "espada ", "conjunto ", "set ", "do ", "da ", "de ", "dos ", "das ", "o ", "a "})
                if (n.startsWith(p)) {
                    n = n.substring(p.length()).trim();
                    mudou = true;
                }
        }
        return n.replaceAll("[^a-z0-9]", "");
    }

    /** Acha o conjunto pelo que foi digitado; devolve 1 id, varios (ambiguo) ou nenhum. */
    private List<String> achar(String digitado, boolean espada) {
        String k = limpo(digitado);
        List<String> r = new ArrayList<>();
        if (k.isEmpty()) return r;
        String exato = chaves.get(k);
        if (exato != null && !(espada && exato.equals("REI"))) {
            r.add(exato);
            return r;
        }
        for (Map.Entry<String, String> e : chaves.entrySet())
            if (e.getKey().startsWith(k) && !r.contains(e.getValue())) r.add(e.getValue());
        if (!r.isEmpty()) return r;
        for (Map.Entry<String, String> e : chaves.entrySet())
            if (e.getKey().contains(k) && !r.contains(e.getValue())) r.add(e.getValue());
        return r;
    }

    private static Player jogador(String nick) {
        Player p = Bukkit.getPlayerExact(nick);
        if (p == null && !nick.startsWith(".")) p = Bukkit.getPlayerExact("." + nick); // Bedrock sem o ponto
        if (p == null)
            for (Player x : Bukkit.getOnlinePlayers())
                if (x.getName().equalsIgnoreCase(nick) || x.getName().equalsIgnoreCase("." + nick)) p = x;
        return p;
    }

    /** As pecas do conjunto: armadura (4) ou espada (1). Lista vazia se o conjunto nao tem aquilo. */
    private List<ItemStack> pecas(String id, boolean espada) {
        List<ItemStack> l = new ArrayList<>();
        if (id.equals("HEROBRINE")) {
            List<ItemStack> c = pl.loja.pecasConjunto("herobrine", "de Herobrine", "§4"); // elmo, peitoral, calca, botas, espada
            if (espada) l.add(c.get(4));
            else l.addAll(c.subList(0, 4));
            return l;
        }
        Tipo t = Tipo.TODOS.get(id);
        if (t == null) return l;
        if (espada) l.add(pl.reliquias.aplicar(new ItemStack(Material.DIAMOND_SWORD), t));
        else for (Material m : ARMADURA) l.add(pl.reliquias.aplicar(new ItemStack(m), t));
        return l;
    }

    private static void entregar(Player alvo, List<ItemStack> itens) {
        for (ItemStack it : itens)
            for (ItemStack sobra : alvo.getInventory().addItem(it).values()) alvo.getWorld().dropItem(alvo.getLocation(), sobra);
    }

    private void lista(CommandSender s, boolean espada) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : nomes.entrySet()) {
            if (espada && e.getKey().equals("REI")) continue;
            if (b.length() > 0) b.append("§8, ");
            b.append("§f").append(e.getValue());
        }
        s.sendMessage(Masmorras.txt("§eUse: §f/" + (espada ? "darespada" : "dararmadura") + " <nick> <nome> §7(ou §ftodas§7)"));
        s.sendMessage(Masmorras.txt("§7Nomes: " + b));
    }

    boolean comando(CommandSender s, String[] a, boolean espada) {
        if (!s.hasPermission("masmorras.admin")) {
            s.sendMessage(Masmorras.txt("§cSó o dono pode dar " + (espada ? "espadas" : "armaduras") + "."));
            return true;
        }
        if (a.length < 2) {
            lista(s, espada);
            return true;
        }
        Player alvo = jogador(a[0]);
        if (alvo == null) {
            s.sendMessage(Masmorras.txt("§c" + a[0] + " não está online."));
            return true;
        }
        String digitado = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
        String oque = espada ? "Espada" : "Armadura";
        if (limpo(digitado).equals("todas") || limpo(digitado).equals("todos") || limpo(digitado).equals("tudo")) {
            List<ItemStack> tudo = new ArrayList<>();
            for (String id : nomes.keySet()) if (!id.equals("REI")) tudo.addAll(pecas(id, espada));
            List<ItemStack> caixas = caixas(tudo, espada ? "Espadas" : "Armaduras");
            entregar(alvo, caixas);
            s.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§f" + tudo.size() + " §7" + (espada ? "espadas" : "peças de armadura") + " em §f" + caixas.size()
                    + " §7caixa" + (caixas.size() > 1 ? "s" : "") + " de shulker para §f" + alvo.getName() + "§7."));
            if (alvo != s) alvo.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Você recebeu " + caixas.size() + " caixa" + (caixas.size() > 1 ? "s" : "") + " com "
                    + (espada ? "todas as espadas" : "todas as armaduras") + "."));
            pl.getLogger().info(s.getName() + " deu todas as " + (espada ? "espadas" : "armaduras") + " para " + alvo.getName());
            return true;
        }
        List<String> ids = achar(digitado, espada);
        if (espada) ids.remove("REI");
        if (ids.isEmpty()) {
            s.sendMessage(Masmorras.txt("§cNão achei \"" + digitado + "\"."));
            lista(s, espada);
            return true;
        }
        if (ids.size() > 1) {
            StringBuilder b = new StringBuilder();
            for (String id : ids) b.append(b.length() > 0 ? "§8, " : "").append("§f").append(nomes.get(id));
            s.sendMessage(Masmorras.txt("§eTem mais de um com esse nome: " + b + "§e. Escreva o nome inteiro."));
            return true;
        }
        String id = ids.get(0);
        if (id.equals("REI")) {
            // o Traje do Rei e do Extras (tem dono e volta para ele): o /vip traje dar entrega do jeito certo
            Bukkit.dispatchCommand(s, "vip traje dar " + alvo.getName());
            return true;
        }
        List<ItemStack> itens = pecas(id, espada);
        if (itens.isEmpty()) {
            s.sendMessage(Masmorras.txt("§cEsse conjunto não tem " + (espada ? "espada" : "armadura") + "."));
            return true;
        }
        entregar(alvo, itens);
        String nome = nomes.get(id);
        s.sendMessage(Masmorras.txt(Masmorras.PREFIXO + oque + " §f" + nome + " §7dada a §f" + alvo.getName() + "§7."
                + (espada ? "" : " §8(elmo, peitoral, calças e botas)")));
        if (alvo != s) alvo.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "Você recebeu a " + oque.toLowerCase(Locale.ROOT) + " §f" + nome + "§7."));
        pl.getLogger().info(s.getName() + " deu " + oque.toLowerCase(Locale.ROOT) + " " + id + " para " + alvo.getName());
        return true;
    }

    /** Guarda os itens em caixas de shulker (27 por caixa). */
    private static List<ItemStack> caixas(List<ItemStack> itens, String titulo) {
        List<ItemStack> r = new ArrayList<>();
        int total = (itens.size() + 26) / 27;
        for (int c = 0; c < total; c++) {
            ItemStack caixa = new ItemStack(Material.RED_SHULKER_BOX);
            BlockStateMeta m = (BlockStateMeta) caixa.getItemMeta();
            ShulkerBox sb = (ShulkerBox) m.getBlockState();
            for (int i = 0; i < 27 && c * 27 + i < itens.size(); i++) sb.getInventory().setItem(i, itens.get(c * 27 + i));
            m.setBlockState(sb);
            m.customName(Masmorras.txt("§6§l" + titulo + " §7(" + (c + 1) + "/" + total + ")"));
            caixa.setItemMeta(m);
            r.add(caixa);
        }
        return r;
    }

    List<String> completar(String[] a, boolean espada) {
        List<String> l = new ArrayList<>();
        if (a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
        } else if (a.length == 2) {
            l.add("todas");
            for (Map.Entry<String, String> e : nomes.entrySet()) {
                if (e.getKey().equals("REI")) {
                    if (!espada) l.add("rei");
                    continue;
                }
                l.add(Normalizer.normalize(e.getValue(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace(' ', '_'));
            }
        }
        String u = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        l.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(u));
        return l;
    }
}
