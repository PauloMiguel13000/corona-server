package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Barra de habilidades (pedido do Paulo, 08/11): em vez de agachar + soco, cada habilidade da classe vira um item nos
 * slots 1 a 5 da barra. Clique (direito ou esquerdo, no ar, num bloco ou num bicho) com o item na mao usa a habilidade.
 * O item "Habilidades" do kit (livro do Extras, comando /poderes) liga e desliga a barra. Ao escolher a classe a barra
 * liga sozinha. Os itens da barra nao saem do lugar: nao da para soltar, mover, guardar em bau, comer, colocar nem craftar.
 * Fora do Mundo das Masmorras (mundos sem poderes) a barra desliga e os itens que estavam nos slots voltam.
 */
final class Barra implements Listener {

    static final int[] SLOTS = {0, 1, 2, 3, 4};
    private static final NamespacedKey ITEM_MENU_EXTRAS = new NamespacedKey("extras", "item_menu");

    private final Poderes pl;
    private final NamespacedKey kItem, kLib, kLigada, kGuardado, kVoltar, kMovidos;
    private final Map<UUID, Long> ultimoUso = new HashMap<>();

    Barra(Poderes pl) {
        this.pl = pl;
        kItem = new NamespacedKey(pl, "barra_poder");
        kLib = new NamespacedKey(pl, "barra_lib");
        kLigada = new NamespacedKey(pl, "barra_ligada");
        kGuardado = new NamespacedKey(pl, "barra_guardado");
        kMovidos = new NamespacedKey(pl, "barra_movidos");
        kVoltar = new NamespacedKey(pl, "barra_voltar");
        Bukkit.getScheduler().runTaskTimer(pl, this::conferirTodos, 100L, 100L);
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("barra.ligado", true);
    }

    boolean ligada(Player p) {
        Byte b = p.getPersistentDataContainer().get(kLigada, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    private boolean voltar(Player p) {
        Byte b = p.getPersistentDataContainer().get(kVoltar, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    private void marcarVoltar(Player p, boolean v) {
        if (v) p.getPersistentDataContainer().set(kVoltar, PersistentDataType.BYTE, (byte) 1);
        else p.getPersistentDataContainer().remove(kVoltar);
    }

    Poder poderDe(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return Poder.porId(it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING));
    }

    boolean ehItem(ItemStack it) {
        return poderDe(it) != null;
    }

    private static boolean vazio(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    private static boolean ehItemMenuExtras(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(ITEM_MENU_EXTRAS, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ ligar / desligar

    /** O item Habilidades do kit e o /poderes chamam isto. */
    void alternar(Player p) {
        if (ligada(p)) {
            desligar(p, true);
            marcarVoltar(p, false);
            return;
        }
        ligar(p, true);
    }

    /** Liga a barra. Sem classe: abre o menu de classes. Fora do mundo das masmorras: avisa e fica marcada para ligar la. */
    boolean ligar(Player p, boolean avisar) {
        if (!ligado()) return false;
        Classe c = pl.classe(p);
        if (c == null) {
            if (avisar) pl.abrirMenuClasses(p);
            return false;
        }
        if (pl.semPoderes(p.getWorld())) {
            marcarVoltar(p, true);
            if (avisar) {
                p.sendMessage("§eA barra de habilidades liga sozinha quando voce entrar no §d§lMundo das Masmorras §7(portal no spawn)§e.");
                pl.abrirMenu(p);
            }
            return false;
        }
        PlayerInventory inv = p.getInventory();
        List<ItemStack> sobra = new ArrayList<>();
        List<Integer> origem = new ArrayList<>();      // de que slot da barra cada item saiu (para voltar ao desligar)
        for (int i = 0; i < inv.getSize(); i++) {
            if (ehItem(inv.getItem(i))) inv.setItem(i, null);
        }
        for (int s : SLOTS) {
            ItemStack it = inv.getItem(s);
            inv.setItem(s, null);
            if (!vazio(it)) {
                sobra.add(it);
                origem.add(s);
            }
        }
        // deixa pelo menos 2 slots da barra livres para arma / mao vazia: os itens de menu Minigames e Classes
        // (slots 6 e 7) vao para a mochila se nao houver slot vazio entre 6 e 9
        boolean temVazio = false;
        for (int s = 5; s < 9; s++) if (vazio(inv.getItem(s))) temVazio = true;
        boolean moveuMenu = false;
        if (!temVazio) {
            for (int s = 5; s <= 6; s++) {
                ItemStack it = inv.getItem(s);
                if (ehItemMenuExtras(it)) {
                    inv.setItem(s, null);
                    sobra.add(it);
                    origem.add(s);
                    moveuMenu = true;
                }
            }
        }
        List<ItemStack> guardar = new ArrayList<>();
        StringBuilder movidos = new StringBuilder();
        for (int k = 0; k < sobra.size(); k++) {
            ItemStack it = sobra.get(k);
            int livre = livreNaMochila(inv);
            if (livre >= 0) {
                inv.setItem(livre, it);
                movidos.append(livre).append(':').append(origem.get(k)).append(':').append(it.getType().name()).append(';');
            } else guardar.add(it);
        }
        if (movidos.length() > 0) p.getPersistentDataContainer().set(kMovidos, PersistentDataType.STRING, movidos.toString());
        else p.getPersistentDataContainer().remove(kMovidos);
        if (!guardar.isEmpty()) guardarNoJogador(p, guardar);
        colocar(p, c);
        p.getPersistentDataContainer().set(kLigada, PersistentDataType.BYTE, (byte) 1);
        marcarVoltar(p, false);
        p.updateInventory();
        if (avisar) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
            p.sendMessage("§a§l>> §aBarra de habilidades LIGADA. §7Slots §f1 a 5 §7= as 5 habilidades de " + c.cor + c.nome
                    + "§7. Segure o item e §fclique §7(no ar, num bloco ou num monstro) para usar.");
            if (!sobra.isEmpty()) p.sendMessage("§7O que estava nos slots 1 a 5" + (moveuMenu ? " (e os itens Minigames e Classes)" : "")
                    + " foi para a sua mochila. §8Desligue no item §bHabilidades §8para voltar.");
            if (!guardar.isEmpty()) p.sendMessage("§7Sua mochila estava cheia: " + guardar.size() + " item(ns) ficaram guardados e voltam quando desligar.");
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
            Poderes.barra(p, "§aBarra de habilidades ligada §7(slots 1 a 5)");
        }
        return true;
    }

    /** Desliga: some com os itens de habilidade e devolve o que estava guardado. */
    void desligar(Player p, boolean avisar) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (ehItem(inv.getItem(i))) inv.setItem(i, null);
        }
        if (ehItem(inv.getItemInOffHand())) inv.setItemInOffHand(null);
        p.getPersistentDataContainer().remove(kLigada);
        voltarParaBarra(p);
        devolverGuardado(p);
        p.updateInventory();
        if (avisar) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
            p.sendMessage("§c§l>> §cBarra de habilidades DESLIGADA. §7Clique de novo no item §bHabilidades §7para ligar.");
        }
    }

    /** Ao desligar, o que tinha ido da barra para a mochila volta para o MESMO slot da barra (se o jogador nao mexeu). */
    private void voltarParaBarra(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String movidos = pdc.get(kMovidos, PersistentDataType.STRING);
        pdc.remove(kMovidos);
        if (movidos == null || movidos.isEmpty()) return;
        PlayerInventory inv = p.getInventory();
        for (String parte : movidos.split(";")) {
            String[] q = parte.split(":");
            if (q.length != 3) continue;
            try {
                int mochila = Integer.parseInt(q[0]), barra = Integer.parseInt(q[1]);
                if (mochila < 9 || mochila > 35 || barra < 0 || barra > 8) continue;
                ItemStack it = inv.getItem(mochila);
                if (vazio(it) || !it.getType().name().equals(q[2]) || !vazio(inv.getItem(barra))) continue;
                inv.setItem(mochila, null);
                inv.setItem(barra, it);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private int livreNaMochila(PlayerInventory inv) {
        for (int i = 9; i < 36; i++) if (vazio(inv.getItem(i))) return i;
        return -1;
    }

    private void guardarNoJogador(Player p, List<ItemStack> itens) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        List<ItemStack> todos = new ArrayList<>();
        byte[] antes = pdc.get(kGuardado, PersistentDataType.BYTE_ARRAY);
        if (antes != null) {
            try {
                for (ItemStack it : ItemStack.deserializeItemsFromBytes(antes)) if (!vazio(it)) todos.add(it);
            } catch (RuntimeException ignored) {
            }
        }
        todos.addAll(itens);
        pdc.set(kGuardado, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(todos.toArray(new ItemStack[0])));
    }

    private List<ItemStack> tirarGuardado(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        byte[] b = pdc.get(kGuardado, PersistentDataType.BYTE_ARRAY);
        pdc.remove(kGuardado);
        List<ItemStack> r = new ArrayList<>();
        if (b == null) return r;
        try {
            for (ItemStack it : ItemStack.deserializeItemsFromBytes(b)) if (!vazio(it)) r.add(it);
        } catch (RuntimeException ex) {
            pl.getLogger().warning("Itens guardados da barra de " + p.getName() + " nao puderam ser lidos: " + ex);
        }
        return r;
    }

    private void devolverGuardado(Player p) {
        List<ItemStack> itens = tirarGuardado(p);
        if (itens.isEmpty()) return;
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : itens) {
            int livre = -1;
            for (int s : SLOTS) if (vazio(inv.getItem(s))) { livre = s; break; }
            if (livre < 0) livre = inv.firstEmpty();
            if (livre >= 0) inv.setItem(livre, it);
            else p.getWorld().dropItemNaturally(p.getLocation(), it);
        }
    }

    // ------------------------------------------------------------------ itens

    private void colocar(Player p, Classe c) {
        int nivel = pl.nivelDePoder(p);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < SLOTS.length && i < c.poderes.length; i++) inv.setItem(SLOTS[i], item(p, c.poderes[i], i, nivel));
    }

    private ItemStack item(Player p, Poder po, int i, int nivel) {
        boolean lib = pl.liberado(p, po, nivel);
        ItemStack it = new ItemStack(lib ? po.icone : Material.GRAY_DYE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Poderes.txt((lib ? po.cor + "§l" : "§8") + (i + 1) + ". " + po.nome + (lib ? "" : " §7(trancada)")));
        List<Component> lore = new ArrayList<>();
        lore.add(Poderes.txt("§7" + po.desc1));
        lore.add(Poderes.txt("§7" + po.desc2));
        lore.add(Poderes.txt(""));
        if (lib) {
            String dano = pl.danoTexto(po, pl.fator(p));
            if (dano != null) lore.add(Poderes.txt("§fDano: §c" + dano));
            lore.add(Poderes.txt("§fRecarga: §e" + po.recargaSeg + "s"));
            lore.add(Poderes.txt("§eClique para usar §8(no ar, bloco ou monstro)"));
        } else {
            lore.add(Poderes.txt("§cLibera no nivel de poder §f" + pl.nivelNecessario(po)));
            lore.add(Poderes.txt("§7Voce tem §f" + Math.max(0, nivel) + "§7. Suba suas skills: §f/skills"));
        }
        m.lore(lore);
        m.addItemFlags(ItemFlag.values());
        m.setEnchantmentGlintOverride(lib);
        m.setMaxStackSize(1);
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, po.id);
        m.getPersistentDataContainer().set(kLib, PersistentDataType.BYTE, (byte) (lib ? 1 : 0));
        it.setItemMeta(m);
        return it;
    }

    /** Chamado pelo lancar(): mostra a recarga no item (funciona no PC e no celular). */
    void recarga(Player p, Poder po) {
        if (!ligada(p)) return;
        p.setCooldown(po.icone, Math.max(1, po.recargaSeg * 20));
    }

    /** Trocou de classe: refaz os itens; se a barra estava desligada, liga (ou marca para ligar nas masmorras). */
    void aoTrocarClasse(Player p) {
        if (!ligado()) return;
        Classe c = pl.classe(p);
        if (c == null) {
            if (ligada(p)) desligar(p, false);
            marcarVoltar(p, false);
            return;
        }
        if (ligada(p)) {
            for (int i = 0; i < p.getInventory().getSize(); i++) if (ehItem(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
            colocar(p, c);
            p.updateInventory();
            return;
        }
        ligar(p, false);
        if (pl.semPoderes(p.getWorld())) {
            p.sendMessage("§7Suas 5 habilidades viram itens na barra §8(slots 1 a 5)§7 assim que voce entrar no §d§lMundo das Masmorras§7.");
        }
    }

    /** A cada 5s: tranca/destranca itens conforme o nivel, repoe item que sumiu, desliga em mundo sem poderes. */
    private void conferirTodos() {
        if (!ligado()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!ligada(p)) continue;
            try {
                conferir(p);
            } catch (RuntimeException ex) {
                pl.getLogger().warning("Barra de " + p.getName() + ": " + ex);
            }
        }
    }

    private void conferir(Player p) {
        Classe c = pl.classe(p);
        if (c == null) {
            desligar(p, false);
            return;
        }
        if (pl.semPoderes(p.getWorld())) {
            desligar(p, false);
            marcarVoltar(p, true);
            return;
        }
        int nivel = pl.nivelDePoder(p);
        PlayerInventory inv = p.getInventory();
        boolean mudou = false;
        for (int i = 0; i < SLOTS.length && i < c.poderes.length; i++) {
            Poder po = c.poderes[i];
            ItemStack it = inv.getItem(SLOTS[i]);
            Poder atual = poderDe(it);
            boolean lib = pl.liberado(p, po, nivel);
            if (atual == po) {
                Byte b = it.getItemMeta().getPersistentDataContainer().get(kLib, PersistentDataType.BYTE);
                if (b != null && (b == 1) == lib) continue;
                inv.setItem(SLOTS[i], item(p, po, i, nivel));
                if (lib) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                    p.sendMessage("§a§l>> §aNova habilidade liberada: " + po.cor + "§l" + po.nome + " §7(slot " + (i + 1) + ")");
                }
                mudou = true;
                continue;
            }
            // slot com outra coisa (item que caiu, bug, item antigo): guarda o que estava e repoe
            if (!vazio(it) && atual == null) {
                int livre = livreNaMochila(inv);
                if (livre >= 0) inv.setItem(livre, it);
                else guardarNoJogador(p, List.of(it));
            }
            inv.setItem(SLOTS[i], item(p, po, i, nivel));
            mudou = true;
        }
        // item de habilidade fora dos 5 slots: some
        for (int i = 0; i < inv.getSize(); i++) {
            boolean dosSlots = false;
            for (int s : SLOTS) if (s == i) dosSlots = true;
            if (!dosSlots && ehItem(inv.getItem(i))) {
                inv.setItem(i, null);
                mudou = true;
            }
        }
        if (ehItem(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            mudou = true;
        }
        if (mudou) p.updateInventory();
    }

    // ------------------------------------------------------------------ usar

    private void usar(Player p, Poder po) {
        long agora = System.currentTimeMillis();
        Long antes = ultimoUso.get(p.getUniqueId());
        if (antes != null && agora - antes < 250) return;
        ultimoUso.put(p.getUniqueId(), agora);
        if (!Poderes.logado(p)) return;
        if (!p.hasPermission("poderes.usar")) return;
        Classe c = pl.classe(p);
        if (c == null || c.posicao(po) < 0) {
            desligar(p, false);
            return;
        }
        pl.selecionar(p, po);
        pl.lancar(p);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void clicou(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        ItemStack it = e.getHand() == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : e.getItem();
        Poder po = poderDe(it);
        if (po == null) return;
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        usar(p, po);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void clicouEntidade(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Poder po = poderDe(p.getInventory().getItem(e.getHand()));
        if (po == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) usar(p, po);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void clicouEmEntidade(PlayerInteractAtEntityEvent e) {
        if (ehItem(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    /** Soco num bicho segurando o item: nao bate, usa a habilidade. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void bateu(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (pl.emDanoPoder) return;
        Poder po = poderDe(p.getInventory().getItemInMainHand());
        if (po == null) return;
        e.setCancelled(true);
        usar(p, po);
    }

    // ------------------------------------------------------------------ travas dos itens

    @EventHandler(priority = EventPriority.LOWEST)
    public void clicouInventario(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (ehItem(e.getCurrentItem()) || ehItem(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0 && ehItem(p.getInventory().getItem(e.getHotbarButton()))) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND && ehItem(p.getInventory().getItemInOffHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void arrastou(InventoryDragEvent e) {
        if (ehItem(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void soltou(PlayerDropItemEvent e) {
        if (!ehItem(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        Poderes.barra(e.getPlayer(), "§7Os itens da barra nao caem. Desligue no item §bHabilidades§7.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void trocouMao(PlayerSwapHandItemsEvent e) {
        if (ehItem(e.getMainHandItem()) || ehItem(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void colocouBloco(BlockPlaceEvent e) {
        if (ehItem(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void esvaziouBalde(PlayerBucketEmptyEvent e) {
        if (ehItem(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void encheuBalde(PlayerBucketFillEvent e) {
        if (ehItem(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void comeu(PlayerItemConsumeEvent e) {
        if (ehItem(e.getItem())) e.setCancelled(true);
    }

    @EventHandler
    public void naoCraftar(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (ehItem(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ morte, mundo, entrada

    @EventHandler(priority = EventPriority.HIGH)
    public void morreu(PlayerDeathEvent e) {
        Player p = e.getEntity();
        e.getDrops().removeIf(this::ehItem);
        if (!ligada(p)) return;
        if (e.getKeepInventory()) return; // mundo das masmorras: fica tudo, inclusive a barra
        // sem keepInventory: a barra some e o que estava guardado cai junto; volta a ligar ao renascer
        for (ItemStack it : tirarGuardado(p)) e.getDrops().add(it);
        p.getPersistentDataContainer().remove(kLigada);
        marcarVoltar(p, true);
    }

    @EventHandler
    public void renasceu(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            if (ligada(p)) conferir(p);
            else if (voltar(p) && !pl.semPoderes(p.getWorld())) ligar(p, false);
        }, 5L);
    }

    /** Antes do Mochilas (MONITOR) trocar o inventario: desliga a barra ao sair para mundo sem poderes. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void mudouDeMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!ligado()) return;
        if (pl.semPoderes(p.getWorld())) {
            if (ligada(p)) {
                desligar(p, false);
                marcarVoltar(p, true);
                Poderes.barra(p, "§7Barra de habilidades guardada: aqui os poderes nao funcionam.");
            }
            return;
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || pl.semPoderes(p.getWorld())) return;
            if (ligada(p)) conferir(p);
            else if (voltar(p)) ligar(p, true);
        }, 12L); // depois do Mochilas (Extras) abrir a mochila das masmorras e dar os itens de menu
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !ligado()) return;
            if (pl.semPoderes(p.getWorld())) {
                if (ligada(p)) {
                    desligar(p, false);
                    marcarVoltar(p, true);
                }
                return;
            }
            if (ligada(p)) conferir(p);
            else if (voltar(p)) ligar(p, false);
        }, 60L);
    }
}
