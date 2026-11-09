package br.servidor.extras;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Traje do Rei: armadura de diamante exclusiva do MVP e da LENDA (Protecao IV, Inquebravel, resistencia de netherite)
 * com visual proprio do pacote (Java: item_model + equipment corona:coroa; Bedrock: o Geyser troca pelo item
 * corona:coroa_<peca> com a armadura no corpo). Fica presa ao dono: so funciona com ele e enquanto o VIP estiver
 * ativo; quem pegar de outro perde; morrendo, o traje fica com ele (volta ao renascer).
 */
final class TrajeRei implements Listener {
    static final String TEMA = "coroa";
    static final String[] PECAS = {"elmo", "peitoral", "calca", "botas"};
    private static final Material[] MATERIAIS = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
    private static final String[] NOMES = {"Coroa-Elmo do Rei", "Peitoral do Rei", "Calças do Rei", "Botas do Rei"};
    private static final double[] ARMADURA = {3, 8, 6, 3};
    private static final EquipmentSlotGroup[] GRUPO = {EquipmentSlotGroup.HEAD, EquipmentSlotGroup.CHEST, EquipmentSlotGroup.LEGS, EquipmentSlotGroup.FEET};

    private final Extras pl;
    private final Vip vip;
    private final NamespacedKey kTraje, kDono, kPeca;
    private final Map<UUID, List<ItemStack>> guardados = new HashMap<>();

    TrajeRei(Extras pl, Vip vip) {
        this.pl = pl;
        this.vip = vip;
        this.kTraje = new NamespacedKey(pl, "traje");
        this.kDono = new NamespacedKey(pl, "traje_dono");
        this.kPeca = new NamespacedKey(pl, "traje_peca");
        Bukkit.getScheduler().runTaskTimer(pl, this::conferirTodos, 100L, 20L * 30);
        Bukkit.getScheduler().runTaskTimer(pl, this::aura, 60L, 4L);
    }

    /** MVP, LENDA ou traje de cortesia (admin). */
    boolean pode(Player p) {
        return vip.nivel(p) >= 3 || vip.cortesia(p.getName());
    }

    // ------------------------------------------------------------------ itens

    ItemStack peca(int i, Player dono) {
        ItemStack it = new ItemStack(MATERIAIS[i]);
        ItemMeta m = it.getItemMeta();
        m.displayName(Extras.txt("§6§l" + NOMES[i]));
        m.lore(List.of(
                Extras.txt("§7Traje exclusivo do §6§lMVP §7e da §d§lLENDA"),
                Extras.txt(""),
                Extras.txt("§bProteção IV §8| §bInquebrável"),
                Extras.txt("§7Resistência de netherite e menos empurrão"),
                Extras.txt("§7Conjunto completo: §6aura de ouro"),
                Extras.txt(""),
                Extras.txt("§8Preso a §7" + dono.getName() + "§8: só funciona com ele")));
        m.addEnchant(Enchantment.PROTECTION, 4, true);
        m.addEnchant(Enchantment.UNBREAKING, 3, true);
        m.addEnchant(Enchantment.MENDING, 1, true);
        m.setUnbreakable(true);
        m.setEnchantmentGlintOverride(false);   // o brilho do encantamento apagava o desenho
        m.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(new NamespacedKey(pl, "traje_armadura_" + i), ARMADURA[i], AttributeModifier.Operation.ADD_NUMBER, GRUPO[i]));
        m.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(new NamespacedKey(pl, "traje_dureza_" + i), 3.0, AttributeModifier.Operation.ADD_NUMBER, GRUPO[i]));
        m.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(new NamespacedKey(pl, "traje_empurrao_" + i), 0.1, AttributeModifier.Operation.ADD_NUMBER, GRUPO[i]));
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(kTraje, PersistentDataType.STRING, TEMA);
        m.getPersistentDataContainer().set(kDono, PersistentDataType.STRING, dono.getUniqueId().toString());
        m.getPersistentDataContainer().set(kPeca, PersistentDataType.STRING, PECAS[i]);
        it.setItemMeta(m);
        visual(it, i);
        return it;
    }

    /** item_model (o Geyser tambem usa) + armadura do pacote no corpo. */
    @SuppressWarnings("UnstableApiUsage")
    private void visual(ItemStack it, int i) {
        try {
            it.setData(DataComponentTypes.ITEM_MODEL, Key.key("corona", "item/coroa/" + PECAS[i]));
            Equippable atual = it.getData(DataComponentTypes.EQUIPPABLE);
            EquipmentSlot slot = it.getType().getEquipmentSlot();
            Equippable.Builder b = Equippable.equippable(slot);
            // coroa 3D (arcos, pontas, asas de ouro): sem asset_id o Java desenha o modelo do item na cabeca;
            // o resto usa a armadura do pacote (e o manto pela camada "wings" do peitoral)
            if (slot != EquipmentSlot.HEAD) b.assetId(Key.key("corona", TEMA));
            if (atual != null) b.equipSound(atual.equipSound()).dispensable(atual.dispensable()).swappable(atual.swappable()).damageOnHurt(atual.damageOnHurt());
            it.setData(DataComponentTypes.EQUIPPABLE, b.build());
        } catch (Throwable ex) {
            pl.getLogger().warning("Visual do Traje do Rei: " + ex);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private boolean precisaVisual(ItemStack it) {
        try {
            if (indice(it) != 0) return false;
            Equippable e = it.getData(DataComponentTypes.EQUIPPABLE);
            return e == null || e.assetId() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Esta com o Peitoral do Rei vestido (as asas de hussardo vem com ele). */
    boolean vestePeitoral(Player p) {
        ItemStack it = p.getInventory().getChestplate();
        return eh(it) && indice(it) == 1;
    }

    boolean eh(ItemStack it) {
        return it != null && it.hasItemMeta() && TEMA.equals(it.getItemMeta().getPersistentDataContainer().get(kTraje, PersistentDataType.STRING));
    }

    private String dono(ItemStack it) {
        return it.getItemMeta().getPersistentDataContainer().get(kDono, PersistentDataType.STRING);
    }

    private int indice(ItemStack it) {
        String p = it.getItemMeta().getPersistentDataContainer().get(kPeca, PersistentDataType.STRING);
        for (int i = 0; i < PECAS.length; i++) if (PECAS[i].equals(p)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ entregar

    /** Da as pecas que faltam (procura no inventario, na armadura e no bau do fim). Devolve quantas deu. */
    int entregar(Player p, boolean avisar) {
        if (!pode(p)) {
            if (avisar) p.sendMessage(Extras.txt("§cO Traje do Rei é só do " + Vip.NIVEIS[3].tag() + " §ce da " + Vip.NIVEIS[4].tag() + "§c. §7Veja: §e/vip"));
            return 0;
        }
        boolean[] tem = new boolean[PECAS.length];
        for (Inventory inv : List.of(p.getInventory(), p.getEnderChest()))
            for (ItemStack it : inv.getContents())
                if (eh(it) && p.getUniqueId().toString().equals(dono(it)) && indice(it) >= 0) tem[indice(it)] = true;
        int dadas = 0;
        for (int i = 0; i < PECAS.length; i++) {
            if (tem[i]) continue;
            ItemStack it = peca(i, p);
            ItemStack[] arm = p.getInventory().getArmorContents();   // botas, calca, peito, elmo
            int slot = 3 - i;
            if (arm[slot] == null || arm[slot].getType().isAir()) {
                arm[slot] = it;
                p.getInventory().setArmorContents(arm);
            } else {
                for (ItemStack sobra : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
            }
            dadas++;
        }
        if (dadas > 0) {
            p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§6§lTRAJE DO REI"), Extras.txt("§fExclusivo do MVP e da LENDA")));
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 0.8f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
            p.sendMessage(Extras.txt("§6§l✦ §fVocê recebeu o §6§lTraje do Rei§f! §7(" + dadas + " peça" + (dadas > 1 ? "s" : "") + ")"));
            pl.getLogger().info("Traje do Rei: " + dadas + " peca(s) para " + p.getName());
        } else if (avisar) {
            p.sendMessage(Extras.txt("§7Você já tem todas as peças do Traje do Rei §8(inventário, corpo ou baú do fim)§7."));
        }
        return dadas;
    }

    // ------------------------------------------------------------------ regras

    /** Tira o traje de quem nao e o dono ou nao tem mais o VIP. */
    void conferir(Player p) {
        boolean pode = pode(p);
        String eu = p.getUniqueId().toString();
        int tirou = 0;
        for (Inventory inv : List.of(p.getInventory(), p.getEnderChest())) {
            ItemStack[] c = inv.getContents();
            boolean mudou = false;
            for (int k = 0; k < c.length; k++) {
                if (!eh(c[k])) continue;
                if (pode && eu.equals(dono(c[k]))) {
                    if (precisaVisual(c[k])) {        // traje antigo (coroa plana): passa para a coroa 3D
                        visual(c[k], indice(c[k]));
                        mudou = true;
                    }
                    continue;
                }
                c[k] = null;
                mudou = true;
                tirou++;
            }
            if (mudou) inv.setContents(c);
        }
        if (tirou > 0) {
            p.sendMessage(Extras.txt(pode ? "§cEsse Traje do Rei não é seu: ele voltou para o dono."
                    : "§7Seu VIP acabou: o §6Traje do Rei §7foi guardado. §eRenove o MVP ou a LENDA §7e use §e/vip traje§7."));
            pl.getLogger().info("Traje do Rei: " + tirou + " peca(s) tirada(s) de " + p.getName() + (pode ? " (nao era o dono)" : " (sem VIP)"));
        }
    }

    private void conferirTodos() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                conferir(p);
            } catch (Throwable ignored) {
            }
        }
    }

    private static final Particle.DustOptions OURO = new Particle.DustOptions(Color.fromRGB(255, 205, 50), 0.75f);
    private static final Particle.DustOptions OURO_CLARO = new Particle.DustOptions(Color.fromRGB(255, 240, 160), 0.55f);
    private static final Particle.DustOptions RUBI = new Particle.DustOptions(Color.fromRGB(220, 20, 40), 0.7f);
    private static final Particle.DustOptions MANTO = new Particle.DustOptions(Color.fromRGB(150, 8, 22), 1.0f);
    private static final Particle.DustOptions MANTO2 = new Particle.DustOptions(Color.fromRGB(95, 4, 14), 1.0f);
    private final Map<UUID, Location> ultimoLugar = new HashMap<>();
    private final Map<UUID, Long> ultimoGolpe = new HashMap<>();
    private long giro;

    private boolean completo(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) if (eh(it)) n++;
        return n == 4;
    }

    /** Conjunto completo vestido: coroa de luz girando sobre a cabeca, manto real de particulas nas costas e rastro de
     * ouro ao andar. So particulas do jogo (Java e Bedrock), poucas por vez. */
    private void aura() {
        giro++;
        boolean leve = pl.getConfig().getBoolean("bedrock.aura-leve", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (p.isInvisible() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || !completo(p)) {
                    ultimoLugar.remove(p.getUniqueId());
                    continue;
                }
                org.bukkit.World w = p.getWorld();
                Location pe = p.getLocation();
                // quem ve: Java recebe a aura inteira; Bedrock (celular) so uma coroa leve (cada particula vira um pacote
                // no celular: a aura inteira eram ~120 pacotes por segundo para cada um perto, pelo 4G/5G)
                List<Player> java = new ArrayList<>(), cel = new ArrayList<>();
                for (Player o : w.getPlayers()) {
                    if (o.getLocation().distanceSquared(pe) > 32 * 32) continue;
                    if (leve && RedeBedrock.bedrock(o)) cel.add(o);
                    else java.add(o);
                }
                // a coroa, as asas e o manto agora sao 3D de verdade (Java e Bedrock): aqui so um brilho leve de ouro
                // de vez em quando em volta da coroa (Bedrock/celular recebe metade)
                if (giro % 3 == 0) {
                    double a = giro * 0.35;
                    double yc = pe.getY() + (p.isSneaking() ? 1.95 : 2.3);
                    Location l = new Location(w, pe.getX() + Math.cos(a) * 0.42, yc, pe.getZ() + Math.sin(a) * 0.42);
                    mostrar(java, Particle.DUST, l, 1, 0, 0, 0, 0, giro % 6 == 0 ? OURO_CLARO : OURO);
                    if (giro % 6 == 0) mostrar(cel, Particle.DUST, l, 1, 0, 0, 0, 0, OURO);
                }
                if (java.isEmpty()) {
                    ultimoLugar.put(p.getUniqueId(), pe.clone());
                    continue;
                }
                // rastro de ouro quando anda
                Location antes = ultimoLugar.put(p.getUniqueId(), pe.clone());
                if (antes != null && antes.getWorld() == w && antes.distanceSquared(pe) > 0.04 && p.isOnGround()) {
                    mostrar(java, Particle.DUST, pe.clone().add(0, 0.05, 0), 3, 0.18, 0.0, 0.18, 0, OURO_CLARO);
                    if (Math.random() < 0.5) mostrar(java, Particle.WAX_ON, pe.clone().add(0, 0.1, 0), 1, 0.2, 0.0, 0.2, 0, null);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static <T> void mostrar(List<Player> quem, Particle tipo, Location l, int n, double ox, double oy, double oz, double vel, T dados) {
        for (Player o : quem) {
            if (dados == null) o.spawnParticle(tipo, l, n, ox, oy, oz, vel);
            else o.spawnParticle(tipo, l, n, ox, oy, oz, vel, dados);
        }
    }

    /** Golpe com o traje completo: explosao de faiscas de ouro no alvo (no maximo 1 a cada 0,4 s). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void golpe(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        try {
            if (!(e.getDamager() instanceof Player p) || !completo(p)) return;
            long agora = System.currentTimeMillis();
            Long u = ultimoGolpe.get(p.getUniqueId());
            if (u != null && agora - u < 400) return;
            ultimoGolpe.put(p.getUniqueId(), agora);
            Location l = e.getEntity().getLocation().add(0, e.getEntity().getHeight() * 0.6, 0);
            l.getWorld().spawnParticle(Particle.DUST, l, 14, 0.3, 0.35, 0.3, 0, OURO);
            l.getWorld().spawnParticle(Particle.CRIT, l, 10, 0.3, 0.3, 0.3, 0.25);
            l.getWorld().spawnParticle(Particle.DUST, l, 5, 0.25, 0.3, 0.25, 0, RUBI);
            l.getWorld().playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, org.bukkit.SoundCategory.PLAYERS, 0.7f, 1.6f);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void saiu(org.bukkit.event.player.PlayerQuitEvent e) {
        ultimoLugar.remove(e.getPlayer().getUniqueId());
        ultimoGolpe.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void soltar(PlayerDropItemEvent e) {
        if (!eh(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Extras.txt("§7O §6Traje do Rei §7não pode ser jogado no chão."));
    }

    /** Morreu: o traje nao cai, volta quando renascer. */
    @EventHandler(priority = EventPriority.HIGH)
    public void morreu(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        List<ItemStack> meus = new ArrayList<>();
        e.getDrops().removeIf(it -> {
            if (!eh(it)) return false;
            if (e.getEntity().getUniqueId().toString().equals(dono(it))) meus.add(it);
            return true;
        });
        if (!meus.isEmpty()) guardados.put(e.getEntity().getUniqueId(), meus);
    }

    @EventHandler
    public void renasceu(PlayerRespawnEvent e) {
        List<ItemStack> l = guardados.remove(e.getPlayer().getUniqueId());
        if (l == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            ItemStack[] arm = p.getInventory().getArmorContents();
            for (ItemStack it : l) {
                int i = indice(it);
                int slot = i < 0 ? -1 : 3 - i;
                if (slot >= 0 && (arm[slot] == null || arm[slot].getType().isAir())) arm[slot] = it;
                else for (ItemStack sobra : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
            }
            p.getInventory().setArmorContents(arm);
            p.sendMessage(Extras.txt("§6Traje do Rei §7de volta com você."));
        }, 2L);
    }
}
