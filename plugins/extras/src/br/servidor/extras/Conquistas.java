package br.servidor.extras;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 13/11 (Paulo: "melhore tudo"): /conquistas — metas lidas das estatisticas do proprio Minecraft
 * (nao precisa guardar nada), cada uma paga coronas uma vez. Funciona em Java e Bedrock (bau).
 */
final class Conquistas implements Listener {
    interface Leitor { long ler(Player p); }

    record Meta(String id, String nome, String desc, Material icone, long alvo, int premio, Leitor leitor) {}

    private static long stat(Player p, Statistic s) { try { return p.getStatistic(s); } catch (Exception e) { return 0; } }
    private static long bloco(Player p, Material... ms) {
        long t = 0;
        for (Material m : ms) try { t += p.getStatistic(Statistic.MINE_BLOCK, m); } catch (Exception ignored) {}
        return t;
    }
    private static long matou(Player p, EntityType t) { try { return p.getStatistic(Statistic.KILL_ENTITY, t); } catch (Exception e) { return 0; } }

    static final List<Meta> METAS = new ArrayList<>();
    static {
        Leitor horas = p -> stat(p, Statistic.PLAY_ONE_MINUTE) / 72000L;
        METAS.add(new Meta("hora1", "Primeira hora", "Jogue 1 hora no servidor", Material.CLOCK, 1, 50, horas));
        METAS.add(new Meta("hora10", "Morador", "Jogue 10 horas no servidor", Material.CLOCK, 10, 250, horas));
        METAS.add(new Meta("hora50", "Veterano", "Jogue 50 horas no servidor", Material.CLOCK, 50, 1000, horas));
        Leitor mobs = p -> stat(p, Statistic.MOB_KILLS);
        METAS.add(new Meta("mob50", "Caçador", "Derrote 50 monstros", Material.STONE_SWORD, 50, 75, mobs));
        METAS.add(new Meta("mob500", "Exterminador", "Derrote 500 monstros", Material.IRON_SWORD, 500, 300, mobs));
        METAS.add(new Meta("mob3000", "Lenda das Sombras", "Derrote 3000 monstros", Material.NETHERITE_SWORD, 3000, 1200, mobs));
        Leitor km = p -> (stat(p, Statistic.WALK_ONE_CM) + stat(p, Statistic.SPRINT_ONE_CM)) / 100000L;
        METAS.add(new Meta("km10", "Andarilho", "Ande 10 km a pé", Material.LEATHER_BOOTS, 10, 100, km));
        METAS.add(new Meta("km100", "Desbravador", "Ande 100 km a pé", Material.DIAMOND_BOOTS, 100, 600, km));
        Leitor diam = p -> bloco(p, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        METAS.add(new Meta("diam1", "Brilho!", "Minere seu primeiro diamante", Material.DIAMOND_ORE, 1, 50, diam));
        METAS.add(new Meta("diam64", "Rico", "Minere 64 minérios de diamante", Material.DIAMOND_BLOCK, 64, 500, diam));
        Leitor pedra = p -> bloco(p, Material.STONE, Material.DEEPSLATE, Material.COBBLESTONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.TUFF);
        METAS.add(new Meta("pedra5k", "Picareta de Ouro", "Quebre 5000 pedras", Material.GOLDEN_PICKAXE, 5000, 300, pedra));
        Leitor peixe = p -> stat(p, Statistic.FISH_CAUGHT);
        METAS.add(new Meta("peixe25", "Pescador", "Pesque 25 peixes", Material.FISHING_ROD, 25, 100, peixe));
        METAS.add(new Meta("peixe250", "Mestre do Anzol", "Pesque 250 peixes", Material.TROPICAL_FISH, 250, 500, peixe));
        METAS.add(new Meta("cria20", "Fazendeiro", "Faça 20 animais cruzarem", Material.WHEAT, 20, 100, p -> stat(p, Statistic.ANIMALS_BRED)));
        METAS.add(new Meta("troca20", "Negociante", "Faça 20 trocas com aldeões", Material.EMERALD, 20, 150, p -> stat(p, Statistic.TRADED_WITH_VILLAGER)));
        METAS.add(new Meta("encanta10", "Encantador", "Encante 10 itens", Material.ENCHANTING_TABLE, 10, 150, p -> stat(p, Statistic.ITEM_ENCHANTED)));
        METAS.add(new Meta("dragao", "Mata-Dragão", "Derrote o Dragão do Fim", Material.DRAGON_HEAD, 1, 1500, p -> matou(p, EntityType.ENDER_DRAGON)));
        METAS.add(new Meta("wither", "Caça ao Wither", "Derrote o Wither", Material.WITHER_SKELETON_SKULL, 1, 1000, p -> matou(p, EntityType.WITHER)));
        METAS.add(new Meta("warden", "Silêncio Absoluto", "Derrote o Warden", Material.SCULK_SHRIEKER, 1, 1500, p -> matou(p, EntityType.WARDEN)));
        METAS.add(new Meta("raid", "Herói da Vila", "Vença uma invasão (raid)", Material.WHITE_BANNER, 1, 400, p -> stat(p, Statistic.RAID_WIN)));
        METAS.add(new Meta("pulo", "Canguru", "Pule 5000 vezes", Material.RABBIT_FOOT, 5000, 75, p -> stat(p, Statistic.JUMP)));
    }

    private final Extras pl;
    private final Set<String> avisados = new HashSet<>();

    Conquistas(Extras pl) {
        this.pl = pl;
        // a cada 2 minutos avisa quem completou alguma meta e ainda nao pegou o premio
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            // 15/11 (Paulo: "quando entro toca um som extremamente alto e aparecem essas mensagens"): cada conquista era avisada
            // de novo a cada reinicio, uma linha e um som por conquista. Agora: uma linha so, som baixo, e avisa cada uma UMA vez.
            for (Player p : Bukkit.getOnlinePlayers()) {
                int novas = 0, coronas = 0;
                String ultima = "";
                for (Meta m : METAS) {
                    if (pego(p, m) || m.leitor().ler(p) < m.alvo()) continue;
                    NamespacedKey av = new NamespacedKey(pl, "conq_av_" + m.id());
                    if (p.getPersistentDataContainer().has(av, PersistentDataType.BYTE)) continue;
                    p.getPersistentDataContainer().set(av, PersistentDataType.BYTE, (byte) 1);
                    novas++;
                    coronas += m.premio();
                    ultima = m.nome();
                }
                if (novas == 0) continue;
                p.sendMessage(Extras.txt(novas == 1 ? "§6🏆 Conquista completa: §e" + ultima + " §7— pegue §6" + coronas + " coronas §7em §e/conquistas"
                        : "§6🏆 Você tem §e" + novas + " conquistas §6prontas (§e" + coronas + " coronas§6): pegue em §e/conquistas"));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.1f);
            }
        }, 20L * 60, 20L * 120);
    }

    private NamespacedKey chave(Meta m) { return new NamespacedKey(pl, "conq_" + m.id()); }
    private boolean pego(Player p, Meta m) { return p.getPersistentDataContainer().has(chave(m), PersistentDataType.BYTE); }

    static final class Tela implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    void abrir(Player p) {
        Tela h = new Tela();
        int feitas = 0;
        for (Meta m : METAS) if (pego(p, m)) feitas++;
        Inventory inv = Bukkit.createInventory(h, 36, Extras.txt("§6§l🏆 Conquistas §8(" + feitas + "/" + METAS.size() + ")"));
        h.inv = inv;
        for (int i = 0; i < METAS.size() && i < 36; i++) {
            Meta m = METAS.get(i);
            long v = Math.min(m.leitor().ler(p), m.alvo());
            boolean ok = v >= m.alvo(), pegou = pego(p, m);
            ItemStack it = new ItemStack(pegou ? Material.LIME_STAINED_GLASS_PANE : m.icone());
            var meta = it.getItemMeta();
            meta.displayName(Extras.txt((pegou ? "§a✔ " : ok ? "§e§l! " : "§7") + m.nome()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Extras.txt("§7" + m.desc()));
            lore.add(Extras.txt("§8Progresso: §f" + v + "§8/§f" + m.alvo()));
            lore.add(Extras.txt("§8Prêmio: §6" + m.premio() + " coronas"));
            lore.add(Extras.txt(pegou ? "§aJá recebido" : ok ? "§e§lClique para pegar!" : "§cAinda não completou"));
            meta.lore(lore);
            if (ok && !pegou) meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
            inv.setItem(i, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Tela)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getRawSlot();
        if (s < 0 || s >= METAS.size()) return;
        Meta m = METAS.get(s);
        if (pego(p, m) || m.leitor().ler(p) < m.alvo()) return;
        p.getPersistentDataContainer().set(chave(m), PersistentDataType.BYTE, (byte) 1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + m.premio());
        p.sendMessage(Extras.txt("§6🏆 Você pegou a conquista §e" + m.nome() + "§6: §f+" + m.premio() + " coronas"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        if (m.premio() >= 1000) Bukkit.broadcast(Extras.txt("§6🏆 §e" + p.getName() + " §7conquistou §6" + m.nome() + "§7! §e/conquistas"));
        Bukkit.getScheduler().runTask(pl, () -> abrir(p));
    }
}
