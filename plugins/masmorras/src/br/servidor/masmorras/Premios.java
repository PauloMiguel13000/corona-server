package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * O que o chefe deixa cair, por nivel (1 a 5). Fica em texto (material, quantidade, encantamentos) para dar para
 * testar sem servidor; vira ItemStack so na hora (Masmorras.item).
 */
final class Premios {

    /** Um premio: material, quantidade e encantamentos ("sharpness:3"). livro = livro encantado com um dos encantamentos. */
    record Item(String material, int qtd, List<String> encantos, boolean livro) {}

    private record Opcao(String material, int min, int max, double chance, String[] encantos, boolean livro) {}

    private static Opcao o(String m, int min, int max) {
        return new Opcao(m, min, max, 1, new String[0], false);
    }

    private static Opcao o(String m, int min, int max, double chance) {
        return new Opcao(m, min, max, chance, new String[0], false);
    }

    private static Opcao enc(String m, String... encantos) {
        return new Opcao(m, 1, 1, 1, encantos, false);
    }

    private static Opcao livro(String... encantos) {
        return new Opcao("ENCHANTED_BOOK", 1, 1, 1, encantos, true);
    }

    static int xp(int nivel) {
        return switch (nivel) {
            case 1 -> 150;
            case 2 -> 300;
            case 3 -> 600;
            case 4 -> 1000;
            default -> 1500;
        };
    }

    private static Opcao[] garantidos(int nivel) {
        // 08/11 (pedido do Paulo): tudo um pouco mais fraco, equilibrado: da para passar com um pouco de dificuldade
        return switch (nivel) {
            case 1 -> new Opcao[] {o("IRON_INGOT", 4, 7), o("GOLD_INGOT", 1, 3), o("EXPERIENCE_BOTTLE", 2, 4)};
            case 2 -> new Opcao[] {o("DIAMOND", 1, 2), o("IRON_INGOT", 5, 9), o("GOLDEN_APPLE", 1, 1, 0.6), o("EXPERIENCE_BOTTLE", 4, 7)};
            case 3 -> new Opcao[] {o("DIAMOND", 2, 4), o("GOLDEN_APPLE", 1, 1), o("EXPERIENCE_BOTTLE", 8, 12)};
            case 4 -> new Opcao[] {o("DIAMOND", 3, 6), o("NETHERITE_SCRAP", 1, 1, 0.7), o("GOLDEN_APPLE", 1, 2), o("EXPERIENCE_BOTTLE", 10, 16)};
            default -> new Opcao[] {o("NETHERITE_INGOT", 1, 1, 0.5), o("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 1, 1, 0.7), o("DIAMOND", 5, 8),
                    o("TOTEM_OF_UNDYING", 1, 1, 0.5), o("EXPERIENCE_BOTTLE", 16, 24)};
        };
    }

    private static Opcao[] sorteio(int nivel) {
        return switch (nivel) {
            // niveis 1 a 3 mais fracos (pedido do Paulo, 04/11): menos encantamento e material pior; 05/11: todos um pouco mais fracos
            case 1 -> new Opcao[] {o("GOLDEN_APPLE", 1, 1, 0.3), o("DIAMOND", 1, 2), enc("STONE_SWORD", "sharpness:1"),
                    enc("CHAINMAIL_CHESTPLATE", "unbreaking:1"), enc("LEATHER_HELMET", "protection:1"), enc("BOW", "power:1"), o("ARROW", 12, 24),
                    o("BREAD", 6, 10), livro("sharpness:1", "protection:1", "efficiency:1", "unbreaking:1", "power:1")};
            case 2 -> new Opcao[] {enc("IRON_HELMET", "protection:1"), enc("IRON_CHESTPLATE", "protection:1"),
                    enc("IRON_LEGGINGS", "protection:1"), enc("IRON_BOOTS", "protection:1", "feather_falling:1"),
                    enc("IRON_SWORD", "sharpness:2"), enc("IRON_PICKAXE", "efficiency:2"), o("EMERALD", 4, 10), o("NAME_TAG", 1, 1),
                    o("SADDLE", 1, 1), o("GOLDEN_CARROT", 6, 12),
                    livro("sharpness:2", "protection:2", "efficiency:2", "unbreaking:2", "power:2", "feather_falling:2", "looting:1", "fortune:1")};
            case 3 -> new Opcao[] {enc("DIAMOND_HELMET", "protection:1"), enc("DIAMOND_CHESTPLATE", "protection:1"),
                    enc("IRON_LEGGINGS", "protection:3", "unbreaking:2"), enc("DIAMOND_BOOTS", "protection:1"),
                    enc("DIAMOND_PICKAXE", "efficiency:3"), enc("DIAMOND_SWORD", "sharpness:2"),
                    o("ENCHANTED_GOLDEN_APPLE", 1, 1, 0.05), o("NETHERITE_SCRAP", 1, 1, 0.15), o("ENDER_PEARL", 4, 8),
                    livro("looting:2", "fortune:2", "protection:3", "sharpness:3", "efficiency:3", "unbreaking:3")};
            case 4 -> new Opcao[] {o("TOTEM_OF_UNDYING", 1, 1, 0.25), o("NETHERITE_INGOT", 1, 1, 0.15),
                    enc("DIAMOND_HELMET", "protection:3", "unbreaking:2"), enc("DIAMOND_CHESTPLATE", "protection:3", "unbreaking:2"),
                    enc("DIAMOND_LEGGINGS", "protection:3", "unbreaking:2"), enc("DIAMOND_BOOTS", "protection:3", "unbreaking:2", "feather_falling:3"),
                    enc("DIAMOND_SWORD", "sharpness:4", "unbreaking:2"), o("ENCHANTED_GOLDEN_APPLE", 1, 1, 0.12), o("ANCIENT_DEBRIS", 1, 2),
                    livro("mending:1", "protection:4", "sharpness:5", "efficiency:5", "unbreaking:3", "fortune:3", "looting:3", "feather_falling:4", "silk_touch:1")};
            default -> new Opcao[] {o("ELYTRA", 1, 1, 0.15), o("ENCHANTED_GOLDEN_APPLE", 1, 1, 0.3), o("SHULKER_SHELL", 1, 3),
                    o("NETHERITE_INGOT", 1, 1, 0.25), o("NETHER_STAR", 1, 1, 0.1),
                    enc("DIAMOND_CHESTPLATE", "protection:3", "unbreaking:3"), enc("DIAMOND_PICKAXE", "efficiency:4", "unbreaking:3", "fortune:2"),
                    livro("mending:1", "protection:4", "sharpness:5", "efficiency:5", "unbreaking:3", "fortune:3", "looting:3", "power:5", "infinity:1")};
        };
    }

    /** Um equipamento encantado do nivel (para os monstros soltarem como reliquia, de vez em quando). */
    static Item equipamento(int nivel, Random r) {
        nivel = Math.max(1, Math.min(5, nivel));
        List<Opcao> l = new ArrayList<>();
        for (Opcao op : sorteio(nivel)) if (!op.livro() && op.encantos().length > 0) l.add(op);
        return fazer(l.get(r.nextInt(l.size())), r);
    }

    static int sorteios(int nivel) {
        return nivel >= 3 ? 3 : 2;
    }

    /** Sorteia o premio do chefe. */
    static List<Item> sortear(int nivel, Random r) {
        nivel = Math.max(1, Math.min(5, nivel));
        List<Item> l = new ArrayList<>();
        for (Opcao op : garantidos(nivel)) if (op.chance() >= 1 || r.nextDouble() < op.chance()) l.add(fazer(op, r));
        Opcao[] s = sorteio(nivel);
        for (int i = 0; i < sorteios(nivel); i++) {
            Opcao op = s[r.nextInt(s.length)];
            if (r.nextDouble() < op.chance()) l.add(fazer(op, r));
            else l.add(new Item("EXPERIENCE_BOTTLE", 3 + r.nextInt(4 * nivel), List.of(), false));
        }
        return l;
    }

    private static Item fazer(Opcao op, Random r) {
        int q = op.min() + (op.max() > op.min() ? r.nextInt(op.max() - op.min() + 1) : 0);
        if (op.livro()) return new Item(op.material(), 1, List.of(op.encantos()[r.nextInt(op.encantos().length)]), true);
        return new Item(op.material(), q, List.of(op.encantos()), false);
    }

    /** Todos os materiais e encantamentos usados (para conferir no inicio). */
    static List<String> materiais() {
        List<String> l = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            for (Opcao op : garantidos(n)) l.add(op.material());
            for (Opcao op : sorteio(n)) l.add(op.material());
        }
        return l;
    }

    static List<String> encantamentos() {
        List<String> l = new ArrayList<>();
        for (int n = 1; n <= 5; n++)
            for (Opcao op : sorteio(n)) for (String e : op.encantos()) l.add(e.split(":")[0]);
        return l;
    }
}
