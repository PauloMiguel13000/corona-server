package br.servidor.extras;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Guia do servidor (10/11): /ajuda abre um bau com tudo o que da para fazer; clicar num item roda o comando dele.
 * Funciona igual no Java e no Bedrock. Quem entra pela primeira vez recebe o lembrete do /ajuda.
 */
final class Ajuda implements Listener {

    /** slot, icone, nome, comando (sem a barra; null = so explica), linhas de texto. */
    record Item(int slot, Material icone, String nome, String comando, String... texto) {}

    static final Item[] ITENS = {
            new Item(10, Material.RECOVERY_COMPASS, "§4§lMasmorras", "masmorras menu", "§7Entre nas masmorras, derrote os chefes", "§7e ganhe armaduras e armas de cada tema."),
            new Item(11, Material.NETHER_STAR, "§d§lClasses e poderes", "classe", "§7Escolha a sua classe. Cada uma tem", "§75 habilidades (slots 1 a 5): §f/poderes"),
            new Item(12, Material.COMPASS, "§6§lMinigames", "minigames", "§7Defesa da Torre, Arena PvP e outros."),
            new Item(13, Material.SOUL_LANTERN, "§5§lJogos de terror", "terror", "§7Lobby do Terror: Hotel, Backrooms, Casa da Vovó,", "§7Não Chore, Turno da Noite, Fábrica e a Floresta.", "§7Turno em grupo: §f/turno coop <nick>"),
            new Item(14, Material.BONE, "§a§lPets", "pets", "§7Veja e chame os seus pets.", "§7Poder do pet: §f/petpoder §8| §7Duelo: §f/duelopet"),
            new Item(15, Material.GRASS_BLOCK, "§2§lMundo aberto", "rtp", "§7Teleporta para um lugar aleatório e seguro", "§7do survival para construir e explorar."),
            new Item(16, Material.ENDER_CHEST, "§3§lBaú do Ender", "ec", "§7Abre o seu baú do Ender de qualquer lugar."),
            new Item(19, Material.WRITABLE_BOOK, "§e§lMissões do dia", "missoes", "§73 missões novas todo dia valendo coronas,", "§7com bônus por fechar as 3."),
            new Item(20, Material.GOLD_NUGGET, "§6§lCoronas", "coronas", "§7A moeda do servidor. Ganha jogando,", "§7nas missões, votando e nos minigames.", "§7Pagar alguém: §f/coronas pagar <nick> <valor>"),
            new Item(21, Material.PAPER, "§b§lVotar", "votar", "§7Vote no servidor e pegue o prêmio: §f/votar pegar"),
            new Item(22, Material.DIAMOND, "§b§lVIP", "vip", "§7Benefícios, kit do dia, partículas, chapéu,", "§7voo e o Traje do Rei."),
            new Item(23, Material.EMERALD, "§a§lLoja", "loja", "§7Link da loja do servidor."),
            new Item(24, Material.ARMOR_STAND, "§f§lGaleria das Relíquias", "galeria", "§7Veja todos os conjuntos de armadura", "§7e arma das masmorras."),
            new Item(25, Material.TOTEM_OF_UNDYING, "§c§lMoeda de Reviver", "reviver", "§7Volta para a partida quando você", "§7é eliminado num minigame."),
            new Item(29, Material.PLAYER_HEAD, "§d§lGrupo e clã", "grupo", "§7Jogue junto: §f/grupo convidar <nick>", "§7Clã: §f/clan criar <nome> §8| §7Chat: §f/gc §7e §f/cc"),
            new Item(30, Material.CAKE, "§e§lChame um amigo", "indicar", "§7Diga quem te chamou: os dois ganham prêmio.", "§7Endereço do servidor: §f/ip"),
            new Item(32, Material.BELL, "§9§lDiscord", "discord", "§7Novidades, avisos e a galera do servidor."),
            new Item(33, Material.OAK_SIGN, "§f§lEndereço do servidor", "ip", "§7Java e Bedrock (celular), para passar aos amigos."),
    };

    private final Extras pl;

    Ajuda(Extras pl) {
        this.pl = pl;
    }

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void abrir(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 45, Extras.txt("§8Guia do Corona Server"));
        h.inv = inv;
        ItemStack topo = new ItemStack(Material.BOOK);
        ItemMeta tm = topo.getItemMeta();
        tm.displayName(Extras.txt("§6§lBem-vindo ao Corona Server"));
        tm.lore(List.of(Extras.txt("§7Clique em qualquer item para ir direto."), Extras.txt("§7Abra este guia quando quiser: §f/ajuda")));
        topo.setItemMeta(tm);
        inv.setItem(4, topo);
        for (Item i : ITENS) {
            ItemStack it = new ItemStack(i.icone());
            ItemMeta im = it.getItemMeta();
            im.displayName(Extras.txt(i.nome()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String t : i.texto()) lore.add(Extras.txt(t));
            if (i.comando() != null) {
                lore.add(Extras.txt(""));
                lore.add(Extras.txt("§eClique §7ou digite §f/" + i.comando()));
            }
            im.lore(lore);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(im);
            inv.setItem(i.slot(), it);
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    @EventHandler
    public void clicou(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        for (Item i : ITENS) {
            if (i.slot() != e.getSlot() || i.comando() == null) continue;
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            // um tick depois: abrir outro menu dentro do clique do bau da problema no Bedrock
            Bukkit.getScheduler().runTask(pl, () -> {
                if (p.isOnline()) p.performCommand(i.comando());
            });
            return;
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPlayedBefore()) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline()) p.sendMessage(Extras.txt("§6§l✦ §fNovo por aqui? Digite §e/ajuda §fpara ver tudo o que dá para fazer no servidor."));
        }, 400L);
    }
}
