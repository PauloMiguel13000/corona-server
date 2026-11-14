package br.servidor.mineracao;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 13/11 (Paulo: "na /mina nao tem como guardar bau... faca tambem um /bau, diferente do /ec"): bau pessoal da
 * Prisao. Fica guardado no proprio jogador (PDC), so abre dentro da Prisao e dos plots (que agora sao da Prisao),
 * entao nada de fora entra e nada da Prisao sai. Tamanho: 27 espacos; VIP ganha 54.
 */
final class BauPrisao implements Listener {
    private final Mineracao pl;
    private final NamespacedKey kBau;
    private final Map<UUID, Inventory> abertos = new HashMap<>();

    BauPrisao(Mineracao pl) {
        this.pl = pl;
        kBau = new NamespacedKey(pl, "bau_prisao");
    }

    static final class Tela implements InventoryHolder {
        final UUID dono;
        Inventory inv;
        Tela(UUID dono) { this.dono = dono; }
        @Override public Inventory getInventory() { return inv; }
    }

    boolean podeAbrir(Player p) {
        return p.getWorld() == pl.mundo || pl.ehPlots(p.getWorld());
    }

    void abrir(Player p) {
        if (!podeAbrir(p)) {
            p.sendMessage(Mineracao.txt("§c⛓ O §f/bau §cda Prisão só abre dentro da Prisão ou no seu terreno (plots)."));
            return;
        }
        Inventory velho = abertos.get(p.getUniqueId());
        if (velho != null) { salvar(p, velho); abertos.remove(p.getUniqueId()); }
        int tam = Mineracao.vip(p) > 0 ? 54 : 27;
        Tela h = new Tela(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(h, tam, Mineracao.txt("§8⛓ Baú da Prisão" + (tam == 54 ? " §6(VIP)" : "")));
        h.inv = inv;
        ItemStack[] guardado = ler(p);
        java.util.List<ItemStack> sobra = new java.util.ArrayList<>();
        for (int i = 0; i < guardado.length; i++) {
            if (guardado[i] == null) continue;
            if (i < tam) inv.setItem(i, guardado[i]);
            else sobra.add(guardado[i]);
        }
        // VIP acabou e o bau encolheu: o que nao coube vai para os espacos livres; o resto fica guardado
        for (ItemStack it : sobra) {
            var nao = inv.addItem(it);
            if (!nao.isEmpty()) for (ItemStack r : nao.values()) for (ItemStack x : p.getInventory().addItem(r).values()) p.getWorld().dropItem(p.getLocation(), x);
        }
        abertos.put(p.getUniqueId(), inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1f);
    }

    private ItemStack[] ler(Player p) {
        byte[] b = p.getPersistentDataContainer().get(kBau, PersistentDataType.BYTE_ARRAY);
        if (b == null) return new ItemStack[0];
        try { return ItemStack.deserializeItemsFromBytes(b); } catch (Exception e) { return new ItemStack[0]; }
    }

    private void salvar(Player p, Inventory inv) {
        ItemStack[] c = inv.getContents();
        ItemStack[] copia = new ItemStack[c.length];
        for (int i = 0; i < c.length; i++) copia[i] = c[i] == null || c[i].getType().isAir() ? ItemStack.empty() : c[i];
        p.getPersistentDataContainer().set(kBau, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(copia));
    }

    @EventHandler
    public void fechou(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Tela t) || !(e.getPlayer() instanceof Player p)) return;
        if (!p.getUniqueId().equals(t.dono)) return;
        salvar(p, e.getInventory());
        abertos.remove(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.7f, 1f);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Inventory inv = abertos.remove(e.getPlayer().getUniqueId());
        if (inv != null) salvar(e.getPlayer(), inv);
    }

    /** Desligando o servidor com bau aberto: salva. */
    void salvarTodos() {
        for (Map.Entry<UUID, Inventory> en : abertos.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) { salvar(p, en.getValue()); p.closeInventory(); }
        }
        abertos.clear();
    }

    /** Mudou de mundo com o bau aberto (teleporte): fecha e salva antes da troca de mochila. */
    @EventHandler
    public void mudou(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getFrom().getWorld() == e.getTo().getWorld()) return;
        Inventory inv = abertos.remove(e.getPlayer().getUniqueId());
        if (inv != null) { salvar(e.getPlayer(), inv); e.getPlayer().closeInventory(); }
    }
}
