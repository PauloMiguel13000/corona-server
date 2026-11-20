package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 20/11 (Paulo: "ele ganhe um item exclusivo"): Estrela do Renascido, so de quem fez REBIRTH.
 * Clique: liga/desliga as Asas do Renascido (asas de particulas nas costas, batendo). Agachado + clique: menu de titulos.
 * Presa ao dono: nao da para jogar no chao, nao cai na morte (volta no respawn) e so funciona para quem renasceu.
 * Quem ja tinha rebirth recebe ao entrar; se perder: /rebirth estrela.
 */
final class EstrelaRenascido implements Listener {

    private static final Set<String> SEM_AURA = Set.of("backrooms", "fabrica", "galeria", "hotel", "naochore", "slender", "turno_noite", "vovo");

    private final Masmorras pl;
    private final NamespacedKey kDono, kDada, kAsas;
    private final Map<UUID, List<ItemStack>> guardadas = new HashMap<>();
    private final Map<UUID, Long> ultimoClique = new HashMap<>();
    private int quadro;

    EstrelaRenascido(Masmorras pl) {
        this.pl = pl;
        kDono = new NamespacedKey(pl, "estrela_dono");
        kDada = new NamespacedKey(pl, "estrela_dada");
        kAsas = new NamespacedKey(pl, "asas_renascido");
        Bukkit.getScheduler().runTaskTimer(pl, this::asas, 40L, 3L);
    }

    boolean ehEstrela(ItemStack i) {
        return i != null && i.getType() == Material.NETHER_STAR && i.hasItemMeta()
                && i.getItemMeta().getPersistentDataContainer().has(kDono, PersistentDataType.STRING);
    }

    ItemStack criar(Player p) {
        int n = Progresso.rebirths(p);
        ItemStack i = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = i.getItemMeta();
        m.displayName(Masmorras.txt("§5§l✦ §d§lEstrela do Renascido §5§l✦"));
        m.lore(List.of(Masmorras.txt("§7Item exclusivo de quem fez §5§lREBIRTH§7."),
                Masmorras.txt("§7Dono: §f" + p.getName() + " §8• §7Renascimentos: §d" + Progresso.romano(Math.max(1, n))),
                Masmorras.txt(""),
                Masmorras.txt("§e▶ Clique: §fliga/desliga as §dAsas do Renascido"),
                Masmorras.txt("§e▶ Agachado + clique: §fescolher o seu título"),
                Masmorras.txt(""),
                Masmorras.txt("§8Presa a você: não cai no chão nem na morte.")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(kDono, PersistentDataType.STRING, p.getUniqueId().toString());
        i.setItemMeta(m);
        return i;
    }

    private boolean temEstrela(Player p) {
        for (ItemStack i : p.getInventory().getContents()) if (ehEstrela(i)) return true;
        for (ItemStack i : p.getEnderChest().getContents()) if (ehEstrela(i)) return true;
        return false;
    }

    /** Entrega a estrela (ao renascer e para quem ja tinha rebirth). */
    void entregar(Player p, boolean avisar) {
        if (Progresso.rebirths(p) <= 0) return;
        p.getPersistentDataContainer().set(kDada, PersistentDataType.INTEGER, Progresso.rebirths(p));
        atualizarLore(p);
        if (temEstrela(p)) return;
        for (ItemStack sobra : p.getInventory().addItem(criar(p)).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        if (avisar) p.sendMessage(Masmorras.txt("§5§l✦ §fVocê ganhou a §d§lEstrela do Renascido§f! §7Clique com ela para ligar as §dAsas do Renascido§7."));
    }

    /** Lore com o numero de renascimentos atual. */
    private void atualizarLore(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        for (int s = 0; s < c.length; s++) {
            if (!ehEstrela(c[s])) continue;
            String dono = c[s].getItemMeta().getPersistentDataContainer().get(kDono, PersistentDataType.STRING);
            if (p.getUniqueId().toString().equals(dono)) p.getInventory().setItem(s, criar(p));
        }
    }

    void comando(Player p) {
        if (Progresso.rebirths(p) <= 0) {
            p.sendMessage(Masmorras.txt("§cA Estrela do Renascido é só de quem fez §5§lREBIRTH§c. §7(/rebirth)"));
            return;
        }
        if (temEstrela(p)) {
            p.sendMessage(Masmorras.txt("§7Você já tem a §dEstrela do Renascido §7(no inventário ou no baú do Ender)."));
            return;
        }
        entregar(p, true);
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || Progresso.rebirths(p) <= 0) return;
            Integer dada = p.getPersistentDataContainer().get(kDada, PersistentDataType.INTEGER);
            if (dada == null) entregar(p, true);
            else if (dada < Progresso.rebirths(p)) { p.getPersistentDataContainer().set(kDada, PersistentDataType.INTEGER, Progresso.rebirths(p)); atualizarLore(p); }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void usar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !ehEstrela(e.getItem())) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        Long ult = ultimoClique.get(p.getUniqueId());
        if (ult != null && agora - ult < 1000L) return;   // sem spam (o celular manda o toque duas vezes)
        ultimoClique.put(p.getUniqueId(), agora);
        String dono = e.getItem().getItemMeta().getPersistentDataContainer().get(kDono, PersistentDataType.STRING);
        if (!p.getUniqueId().toString().equals(dono) || Progresso.rebirths(p) <= 0) {
            p.sendMessage(Masmorras.txt("§cEssa Estrela do Renascido não é sua."));
            return;
        }
        if (p.isSneaking()) {
            p.performCommand("titulo");
            return;
        }
        boolean liga = !asasLigadas(p);
        p.getPersistentDataContainer().set(kAsas, PersistentDataType.BYTE, (byte) (liga ? 1 : 0));
        p.sendActionBar(Masmorras.txt(liga ? "§d§lAsas do Renascido §aLIGADAS" : "§d§lAsas do Renascido §cDESLIGADAS"));
        if (liga) p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.05);
    }

    private boolean asasLigadas(Player p) {
        Byte b = p.getPersistentDataContainer().get(kAsas, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void jogar(PlayerDropItemEvent e) {
        if (!ehEstrela(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Masmorras.txt("§dA Estrela do Renascido é presa a você."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void morreu(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        List<ItemStack> minhas = new ArrayList<>();
        e.getDrops().removeIf(i -> {
            if (!ehEstrela(i)) return false;
            minhas.add(i);
            return true;
        });
        if (!minhas.isEmpty()) guardadas.computeIfAbsent(e.getEntity().getUniqueId(), k -> new ArrayList<>()).addAll(minhas);
    }

    @EventHandler
    public void voltou(PlayerRespawnEvent e) {
        List<ItemStack> l = guardadas.remove(e.getPlayer().getUniqueId());
        if (l == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) { guardadas.put(p.getUniqueId(), l); return; }
            for (ItemStack i : l) for (ItemStack sobra : p.getInventory().addItem(i).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        }, 2L);
    }

    @EventHandler
    public void entrouDeNovo(PlayerJoinEvent e) {
        // morreu e saiu antes do respawn: devolve quando entrar
        List<ItemStack> l = guardadas.remove(e.getPlayer().getUniqueId());
        if (l == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && !p.isDead()) for (ItemStack i : l) p.getInventory().addItem(i);
            else guardadas.put(p.getUniqueId(), l);
        }, 40L);
    }

    // ------------------------------------------------------------------ asas de particulas

    private static final Particle.DustOptions[] COR = {
            new Particle.DustOptions(Color.fromRGB(0x5A, 0x16, 0x9E), 0.75f),
            new Particle.DustOptions(Color.fromRGB(0x9B, 0x3C, 0xE6), 0.7f),
            new Particle.DustOptions(Color.fromRGB(0xE0, 0x6C, 0xFF), 0.65f),
            new Particle.DustOptions(Color.fromRGB(0xFF, 0xC8, 0xFF), 0.55f)};

    /** Desenho de uma asa (x = para o lado, y = para cima), em blocos. Ponta de pena por fileira. */
    private static final double[][] ASA = {
            {0.18, 0.10}, {0.30, 0.18}, {0.42, 0.28}, {0.55, 0.40}, {0.68, 0.52}, {0.80, 0.62}, {0.90, 0.68},
            {0.30, 0.02}, {0.45, 0.06}, {0.60, 0.14}, {0.74, 0.24}, {0.86, 0.36},
            {0.32, -0.12}, {0.48, -0.10}, {0.62, -0.04}, {0.76, 0.06},
            {0.30, -0.26}, {0.44, -0.28}, {0.58, -0.24}};

    private void asas() {
        quadro++;
        double bate = Math.sin(quadro * 0.35) * 0.35;   // abre e fecha
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!asasLigadas(p) || Progresso.rebirths(p) <= 0) continue;
            if (p.isInvisible() || p.getGameMode() == GameMode.SPECTATOR || p.isGliding() || SEM_AURA.contains(p.getWorld().getName())) continue;
            if (p.hasMetadata("vanished")) continue;
            Location b = p.getLocation();
            double yaw = Math.toRadians(b.getYaw());
            // frente do jogador = (-sin, cos); lado direito = (-cos, -sin)
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            double baseY = b.getY() + (p.isSneaking() ? 1.05 : 1.3);
            double atras = -0.28;
            for (int lado = -1; lado <= 1; lado += 2) {
                for (int k = 0; k < ASA.length; k++) {
                    double x = ASA[k][0], y = ASA[k][1];
                    double ang = 0.55 + bate;                          // quanto a asa vai para tras
                    double lat = x * Math.cos(ang), tras = x * Math.sin(ang);
                    double px = b.getX() + fx * (atras - tras) + rx * lat * lado;
                    double pz = b.getZ() + fz * (atras - tras) + rz * lat * lado;
                    Particle.DustOptions c = COR[Math.min(COR.length - 1, (int) (x / 0.25))];
                    p.getWorld().spawnParticle(Particle.DUST, px, baseY + y, pz, 1, 0, 0, 0, 0, c);
                }
            }
            if (quadro % 6 == 0) p.getWorld().spawnParticle(Particle.END_ROD, b.getX() - fx * 0.4, baseY + 0.3, b.getZ() - fz * 0.4, 1, 0.3, 0.2, 0.3, 0.005);
        }
    }
}
