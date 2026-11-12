package br.servidor.extras;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Mochilas separadas (pedido do Paulo, 08/11): o que e do survival fica no survival e o que e das masmorras fica nas
 * masmorras. Cada jogador tem duas mochilas (inventario + armadura + mao secundaria + bau do fim + XP): "survival"
 * (world, world_nether, world_the_end, plots) e "masmorras" (masmorras_mundo, masmorras_nether, masmorras_end).
 * Ao entrar num mundo de um grupo diferente do da mochila que esta na mao, a mochila atual e guardada no jogador
 * (PDC) e a do outro grupo e aberta. Mundos de minigame/lobby nao trocam nada (eles cuidam do proprio inventario).
 * Na primeira vez nas masmorras a mochila vem vazia, so com os itens de menu (bussola, estrela, osso, livro).
 */
final class Mochilas implements Listener {

    private final Extras pl;
    private final NamespacedKey kGrupo, kVersao, kReset;

    Mochilas(Extras pl) {
        this.pl = pl;
        kGrupo = new NamespacedKey(pl, "mochila_grupo");
        kVersao = new NamespacedKey(pl, "mochila_v");
        kReset = new NamespacedKey(pl, "mochila_reset");
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("mochilas.ligado", true);
    }

    /** Grupo do mundo: "survival", "masmorras" ou null (mundo neutro: nao troca). */
    String grupo(World w) {
        if (w == null) return null;
        String n = w.getName();
        List<String> surv = pl.getConfig().getStringList("mochilas.survival");
        if (surv.isEmpty()) surv = List.of("world", "world_nether", "world_the_end", "plots");
        List<String> mas = pl.getConfig().getStringList("mochilas.masmorras");
        if (mas.isEmpty()) mas = List.of("masmorras_mundo", "masmorras_nether", "masmorras_end");
        if (surv.contains(n)) return "survival";
        if (mas.contains(n)) return "masmorras";
        return null;
    }

    private NamespacedKey chave(String grupo, String parte) {
        return new NamespacedKey(pl, "mochila_" + grupo + "_" + parte);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void mudouDeMundo(PlayerChangedWorldEvent e) {
        conferir(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entrou(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (e.getPlayer().isOnline()) conferir(e.getPlayer());
        }, 20L);
    }

    /** Confere se a mochila na mao e a do grupo do mundo em que o jogador esta; se nao for, troca. */
    void conferir(Player p) {
        if (!ligado()) return;
        String novo = grupo(p.getWorld());
        if (novo == null) return;   // minigame/lobby: o inventario na mao nao e a mochila
        resetar(p);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String atual = pdc.get(kGrupo, PersistentDataType.STRING);
        if (atual == null) {
            // primeira vez com o sistema: o que ele tem na mao e a mochila do survival (tudo veio de la)
            atual = "survival";
            pdc.set(kGrupo, PersistentDataType.STRING, atual);
        }
        if (atual.equals(novo)) return;
        try {
            guardar(p, atual);
            boolean tinha = abrir(p, novo);
            pdc.set(kGrupo, PersistentDataType.STRING, novo);
            if (!tinha) {
                // mochila nova das masmorras: so os itens de menu
                if (novo.equals("masmorras")) Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (p.isOnline()) pl.darItens(p);
                }, 5L);
            }
            p.sendActionBar(Extras.txt(novo.equals("masmorras") ? "§dMochila das masmorras aberta §7(a do survival ficou guardada)" : "§aMochila do survival aberta §7(a das masmorras ficou guardada)"));
            p.playSound(p.getLocation(), "minecraft:item.bundle.insert", 0.8f, 1f);
        } catch (Throwable t) {
            pl.getLogger().warning("Mochila de " + p.getName() + " (" + atual + " -> " + novo + "): " + t);
        }
    }

    /**
     * Reset da mochila das masmorras (pedido do Paulo, 12/11, uma vez so): cada jogador perde os itens e o bau do fim
     * da mochila das masmorras na proxima vez que entrar. A do survival, o XP, as coronas e as classes nao mudam.
     * Antes de apagar, o conteudo vai para plugins/Extras/reset-masmorras-<n>/<nome>-<uuid>.yml (da para devolver).
     * O numero vem de mochilas.reset-masmorras no config (0 = nunca; aumentar o numero faz outro reset).
     */
    void resetar(Player p) {
        int quer = pl.getConfig().getInt("mochilas.reset-masmorras", 0);
        if (quer <= 0) return;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Integer feito = pdc.get(kReset, PersistentDataType.INTEGER);
        if (feito != null && feito >= quer) return;
        try {
            String atual = pdc.get(kGrupo, PersistentDataType.STRING);
            boolean naMao = "masmorras".equals(atual);
            boolean guardada = pdc.has(chave("masmorras", "tem"), PersistentDataType.BYTE);
            if (naMao || guardada) {
                byte[] inv = naMao ? ItemStack.serializeItemsAsBytes(p.getInventory().getContents()) : pdc.get(chave("masmorras", "inv"), PersistentDataType.BYTE_ARRAY);
                byte[] bau = naMao ? ItemStack.serializeItemsAsBytes(p.getEnderChest().getContents()) : pdc.get(chave("masmorras", "bau"), PersistentDataType.BYTE_ARRAY);
                java.io.File pasta = new java.io.File(pl.getDataFolder(), "reset-masmorras-" + quer);
                pasta.mkdirs();
                org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
                y.set("jogador", p.getName());
                y.set("uuid", p.getUniqueId().toString());
                y.set("quando", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
                y.set("estava-na-mao", naMao);
                if (inv != null) y.set("inv", java.util.Base64.getEncoder().encodeToString(inv));
                if (bau != null) y.set("bau", java.util.Base64.getEncoder().encodeToString(bau));
                y.save(new java.io.File(pasta, p.getName().replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + p.getUniqueId() + ".yml"));   // se nao gravar, nao apaga
                if (naMao) {
                    p.getInventory().clear();
                    p.getEnderChest().clear();
                    p.updateInventory();
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        if (p.isOnline() && "masmorras".equals(grupo(p.getWorld())) && "masmorras".equals(p.getPersistentDataContainer().get(kGrupo, PersistentDataType.STRING))) pl.darItens(p);
                    }, 5L);
                } else {
                    for (String parte : new String[] {"inv", "bau", "tem"}) pdc.remove(chave("masmorras", parte));
                }
                p.sendMessage(Extras.txt("§6§lMasmorras §8» §7Temporada nova: os §fitens das masmorras §7de todos foram reiniciados. Seu survival, suas coronas e suas classes continuam iguais. Use §f/trilha §7para conquistar os conjuntos."));
                pl.getLogger().info("Reset das masmorras: mochila de " + p.getName() + " reiniciada (copia em reset-masmorras-" + quer + ").");
            }
            pdc.set(kReset, PersistentDataType.INTEGER, quer);
        } catch (Throwable t) {
            pl.getLogger().warning("Reset das masmorras de " + p.getName() + " NAO feito (nada foi apagado): " + t);
        }
    }

    private void guardar(Player p, String grupo) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(chave(grupo, "inv"), PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(p.getInventory().getContents()));
        pdc.set(chave(grupo, "bau"), PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(p.getEnderChest().getContents()));
        pdc.set(chave(grupo, "xp"), PersistentDataType.INTEGER, p.getLevel());
        pdc.set(chave(grupo, "xpf"), PersistentDataType.FLOAT, p.getExp());
        pdc.set(chave(grupo, "tem"), PersistentDataType.BYTE, (byte) 1);
    }

    /** Abre a mochila do grupo; devolve false se ela nao existia (ficou vazia). */
    private boolean abrir(Player p, String grupo) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        boolean tinha = pdc.has(chave(grupo, "tem"), PersistentDataType.BYTE);
        p.getInventory().clear();
        p.getEnderChest().clear();
        p.setLevel(0);
        p.setExp(0f);
        if (!tinha) return false;
        byte[] inv = pdc.get(chave(grupo, "inv"), PersistentDataType.BYTE_ARRAY);
        byte[] bau = pdc.get(chave(grupo, "bau"), PersistentDataType.BYTE_ARRAY);
        if (inv != null) {
            ItemStack[] itens = ItemStack.deserializeItemsFromBytes(inv);
            ItemStack[] destino = new ItemStack[p.getInventory().getSize()];
            System.arraycopy(itens, 0, destino, 0, Math.min(itens.length, destino.length));
            p.getInventory().setContents(destino);
        }
        if (bau != null) {
            ItemStack[] itens = ItemStack.deserializeItemsFromBytes(bau);
            ItemStack[] destino = new ItemStack[p.getEnderChest().getSize()];
            System.arraycopy(itens, 0, destino, 0, Math.min(itens.length, destino.length));
            p.getEnderChest().setContents(destino);
        }
        Integer xp = pdc.get(chave(grupo, "xp"), PersistentDataType.INTEGER);
        Float xpf = pdc.get(chave(grupo, "xpf"), PersistentDataType.FLOAT);
        if (xp != null) p.setLevel(xp);
        if (xpf != null) p.setExp(Math.max(0f, Math.min(0.999f, xpf)));
        p.updateInventory();
        return true;
    }
}
