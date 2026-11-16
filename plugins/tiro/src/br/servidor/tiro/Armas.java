package br.servidor.tiro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Armas de fogo dos jogos de tiro (15/11). Cada arma e uma enxada com nome e marca (o pacote de texturas troca o
 * desenho depois): botao direito atira (segurando, as automaticas continuam atirando), botao esquerdo no ar ou
 * soltar o item (Q) recarrega. Tiro e instantaneo (raio) com rastro de particulas; tiro na cabeca da mais dano.
 * A municao do pente fica na propria arma; a reserva sao itens de municao na mochila. O numero do pente aparece no
 * lugar do nivel de XP enquanto a arma esta na mao.
 */
final class Armas implements Listener {

    record Arma(String id, String nome, Material item, double dano, int cadencia, int pente, int recarga, double alcance,
                double espalha, int projeteis, String municao, boolean automatica, Sound som, float tom) {}

    record Municao(String id, String nome, Material item) {}

    static final Map<String, Arma> ARMAS = new LinkedHashMap<>();
    static final Map<String, Municao> MUNICOES = new LinkedHashMap<>();

    static {
        for (Arma a : new Arma[] {
                new Arma("pistola", "§fPistola 9mm", Material.WOODEN_HOE, 5.0, 6, 12, 30, 40, 1.5, 1, "leve", false, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.6f),
                new Arma("smg", "§eSubmetralhadora", Material.GOLDEN_HOE, 3.6, 2, 30, 40, 34, 3.2, 1, "leve", true, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.9f),
                new Arma("escopeta", "§6Escopeta", Material.STONE_HOE, 3.2, 16, 6, 50, 15, 8.0, 7, "cartucho", false, Sound.ENTITY_GENERIC_EXPLODE, 1.7f),
                new Arma("fuzil", "§aFuzil de Assalto", Material.IRON_HOE, 6.5, 3, 30, 45, 60, 1.3, 1, "pesada", true, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f),
                new Arma("sniper", "§bRifle de Precisão", Material.DIAMOND_HOE, 24.0, 30, 5, 60, 110, 0.1, 1, "pesada", false, Sound.ENTITY_GENERIC_EXPLODE, 2.0f),
                new Arma("metralhadora", "§cMetralhadora Pesada", Material.NETHERITE_HOE, 5.2, 2, 80, 90, 50, 3.6, 1, "pesada", true, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.1f)})
            ARMAS.put(a.id(), a);
        for (Municao m : new Municao[] {
                new Municao("leve", "§fMunição leve §7(pistola, submetralhadora)", Material.IRON_NUGGET),
                new Municao("pesada", "§eMunição pesada §7(fuzil, rifle, metralhadora)", Material.GOLD_NUGGET),
                new Municao("cartucho", "§6Cartuchos §7(escopeta)", Material.FLINT)})
            MUNICOES.put(m.id(), m);
    }

    /** Quem recebe o aviso de cada acerto (os jogos usam para pontos e para decidir quem pode ser atingido). */
    interface Regras {
        boolean podeAtingir(Player atirador, LivingEntity alvo);

        default void acertou(Player atirador, LivingEntity alvo, double dano, boolean cabeca) {}
    }

    final Tiro pl;
    final NamespacedKey kArma, kPente, kMunicao, kItem;
    private final Map<UUID, Long> proximoTiro = new HashMap<>(), segurandoAte = new HashMap<>(), recarregandoAte = new HashMap<>();
    private final Map<UUID, Boolean> xpFalso = new HashMap<>();
    private final Random rnd = new Random();
    Regras regras = (a, b) -> !(b instanceof Player);

    Armas(Tiro pl) {
        this.pl = pl;
        kArma = new NamespacedKey(pl, "arma");
        kPente = new NamespacedKey(pl, "pente");
        kMunicao = new NamespacedKey(pl, "municao");
        kItem = new NamespacedKey(pl, "item");
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 1L, 1L);
    }

    // ------------------------------------------------------------------ itens

    ItemStack arma(String id) {
        Arma a = ARMAS.get(id);
        ItemStack it = new ItemStack(a.item());
        ItemMeta m = it.getItemMeta();
        m.displayName(Tiro.txt(a.nome()));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        l.add(Tiro.txt("§7Dano §f" + (a.projeteis() > 1 ? a.projeteis() + "x" : "") + a.dano() + " §8| §7Pente §f" + a.pente() + " §8| §7Alcance §f" + (int) a.alcance()));
        l.add(Tiro.txt("§7Usa: " + MUNICOES.get(a.municao()).nome()));
        l.add(Tiro.txt("§eBotão direito§7 atira" + (a.automatica() ? " (segure)" : "") + " §8| §eEsquerdo no ar ou Q§7 recarrega"));
        l.add(Tiro.txt("§8Tiro na cabeça dá mais dano."));
        m.lore(l);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(kArma, PersistentDataType.STRING, id);
        m.getPersistentDataContainer().set(kPente, PersistentDataType.INTEGER, a.pente());
        m.setCustomModelData(7100 + new ArrayList<>(ARMAS.keySet()).indexOf(id));
        it.setItemMeta(m);
        return it;
    }

    ItemStack municao(String id, int qtd) {
        Municao mu = MUNICOES.get(id);
        ItemStack it = new ItemStack(mu.item(), Math.max(1, Math.min(64, qtd)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Tiro.txt(mu.nome()));
        m.getPersistentDataContainer().set(kMunicao, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    /** Item simples marcado (bandagem, tala, sucata, faca...). */
    ItemStack item(String id, Material mat, String nome, int qtd, String... lore) {
        ItemStack it = new ItemStack(mat, qtd);
        ItemMeta m = it.getItemMeta();
        m.displayName(Tiro.txt(nome));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Tiro.txt(s));
        if (!l.isEmpty()) m.lore(l);
        m.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    /** 15/11 (Paulo: "falta fazer roupas de exercito e de sobrevivencia"): peca de uniforme. conj = exercito | tatico |
     *  sobrevivente; peca = helmet | chestplate | leggings | boots. Couro tingido para quem nao tem o pacote. */
    @SuppressWarnings("UnstableApiUsage")
    ItemStack uniforme(String conj, String peca) {
        String[] pecas = {"helmet", "chestplate", "leggings", "boots"}, pt = {"elmo", "peitoral", "calca", "botas"};
        String[] nomes = switch (conj) {
            case "tatico" -> new String[] {"§8Capacete Tático", "§8Colete Tático", "§8Calça Tática", "§8Coturno Tático"};
            case "sobrevivente" -> new String[] {"§6Gorro de Sobrevivente", "§6Jaqueta com Mochila", "§6Jeans Remendado", "§6Bota de Trilha"};
            default -> new String[] {"§2Capacete do Exército", "§2Farda Camuflada com Colete", "§2Calça Camuflada", "§2Coturno"};
        };
        int i = java.util.Arrays.asList(pecas).indexOf(peca);
        if (i < 0) i = 1;
        Material mat = Material.valueOf("LEATHER_" + pecas[i].toUpperCase(java.util.Locale.ROOT));
        int cor = conj.equals("tatico") ? 0x26282C : conj.equals("sobrevivente") ? 0x603E24 : 0x4E5F38;
        double[] def = conj.equals("sobrevivente") ? new double[] {2, 4, 3, 2} : new double[] {2, 6, 5, 2};
        ItemStack it = item("uniforme_" + conj, mat, nomes[i], 1, conj.equals("sobrevivente") ? "§7Roupa de quem sobreviveu até aqui." : "§7Proteção de soldado.");
        org.bukkit.inventory.meta.LeatherArmorMeta m = (org.bukkit.inventory.meta.LeatherArmorMeta) it.getItemMeta();
        m.setColor(org.bukkit.Color.fromRGB(cor));
        m.setUnbreakable(true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_DYE, org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        org.bukkit.inventory.EquipmentSlot slot = mat.getEquipmentSlot();
        m.addAttributeModifier(org.bukkit.attribute.Attribute.ARMOR, new org.bukkit.attribute.AttributeModifier(
                new NamespacedKey(pl, "uniforme_" + pecas[i]), def[i], org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, slot.getGroup()));
        it.setItemMeta(m);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "uniforme/" + conj + "_" + pt[i]));
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE, io.papermc.paper.datacomponent.item.Equippable.equippable(slot)
                .assetId(net.kyori.adventure.key.Key.key("corona", "u" + conj)).build());
        return it;
    }

    /** Veste o conjunto inteiro. */
    void vestir(org.bukkit.entity.Player p, String conj) {
        var inv = p.getInventory();
        inv.setHelmet(uniforme(conj, "helmet"));
        inv.setChestplate(uniforme(conj, "chestplate"));
        inv.setLeggings(uniforme(conj, "leggings"));
        inv.setBoots(uniforme(conj, "boots"));
    }

    String idItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kItem, PersistentDataType.STRING);
    }

    Arma armaDe(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(kArma, PersistentDataType.STRING);
        return id == null ? null : ARMAS.get(id);
    }

    String municaoDe(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kMunicao, PersistentDataType.STRING);
    }

    int pente(ItemStack it) {
        Integer n = it.getItemMeta().getPersistentDataContainer().get(kPente, PersistentDataType.INTEGER);
        return n == null ? 0 : n;
    }

    private void pente(Player p, ItemStack it, int n) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(kPente, PersistentDataType.INTEGER, n);
        it.setItemMeta(m);
        p.getInventory().setItemInMainHand(it);
    }

    int reserva(Player p, String tipo) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (tipo.equals(municaoDe(it))) n += it.getAmount();
        return n;
    }

    private int tirarReserva(Player p, String tipo, int quer) {
        int falta = quer;
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length && falta > 0; i++) {
            if (!tipo.equals(municaoDe(c[i]))) continue;
            int k = Math.min(falta, c[i].getAmount());
            falta -= k;
            if (k == c[i].getAmount()) p.getInventory().setItem(i, null);
            else c[i].setAmount(c[i].getAmount() - k);
        }
        return quer - falta;
    }

    /** Tira uma fracao da municao de reserva (ao morrer no Apocalipse). */
    void perderMunicao(Player p, double fracao) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            if (municaoDe(c[i]) == null) continue;
            int fica = (int) Math.floor(c[i].getAmount() * (1 - fracao));
            if (fica <= 0) p.getInventory().setItem(i, null);
            else c[i].setAmount(fica);
        }
    }

    // ------------------------------------------------------------------ atirar e recarregar

    private void mostrar(Player p, Arma a, ItemStack it) {
        int n = pente(it);
        p.sendExperienceChange(Math.max(0f, Math.min(1f, n / (float) a.pente())), n);
        xpFalso.put(p.getUniqueId(), true);
        p.sendActionBar(Tiro.txt(a.nome() + " §f" + n + "§7/" + a.pente() + " §8| §7reserva §f" + reserva(p, a.municao())));
    }

    private void tique() {
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            if (!pl.mundoDeTiro(p.getWorld())) {
                if (xpFalso.remove(u) != null) p.sendExperienceChange(p.getExp(), p.getLevel());
                continue;
            }
            ItemStack it = p.getInventory().getItemInMainHand();
            Arma a = armaDe(it);
            if (a == null) {
                if (xpFalso.remove(u) != null) p.sendExperienceChange(p.getExp(), p.getLevel());
                continue;
            }
            Long rec = recarregandoAte.get(u);
            if (rec != null) {
                if (agora >= rec) {
                    recarregandoAte.remove(u);
                    int falta = a.pente() - pente(it);
                    int veio = tirarReserva(p, a.municao(), falta);
                    pente(p, it, pente(it) + veio);
                    p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.7f, 1.8f);
                    mostrar(p, a, p.getInventory().getItemInMainHand());
                }
                continue;
            }
            if (a.automatica() && segurandoAte.getOrDefault(u, 0L) > agora && proximoTiro.getOrDefault(u, 0L) <= agora) atirar(p, a, it);
            else if (Bukkit.getCurrentTick() % 10 == 0 && !Boolean.TRUE.equals(xpFalso.get(u))) mostrar(p, a, it);
        }
    }

    /** 16/11: gatilho por codigo (piloto de gravacao do admin, /missao admin piloto): como segurar o botao direito. */
    void gatilho(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        Arma a = armaDe(it);
        if (a == null || !pl.mundoDeTiro(p.getWorld())) return;
        segurandoAte.put(p.getUniqueId(), System.currentTimeMillis() + 260);
        atirar(p, a, it);
    }

    void recarregar(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        Arma a = armaDe(it);
        if (a == null || recarregandoAte.containsKey(p.getUniqueId())) return;
        if (pente(it) >= a.pente()) return;
        if (reserva(p, a.municao()) <= 0) {
            p.sendActionBar(Tiro.txt("§cSem munição de reserva! §7(" + MUNICOES.get(a.municao()).nome() + "§7)"));
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f);
            return;
        }
        recarregandoAte.put(p.getUniqueId(), System.currentTimeMillis() + a.recarga() * 50L);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.7f, 1.8f);
        p.sendActionBar(Tiro.txt("§eRecarregando..."));
    }

    private void atirar(Player p, Arma a, ItemStack it) {
        UUID u = p.getUniqueId();
        long agora = System.currentTimeMillis();
        if (recarregandoAte.containsKey(u) || proximoTiro.getOrDefault(u, 0L) > agora) return;
        int n = pente(it);
        if (n <= 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.9f);
            recarregar(p);
            return;
        }
        proximoTiro.put(u, agora + a.cadencia() * 50L - 10);
        pente(p, it, n - 1);
        if (pl.apocalipse != null) pl.apocalipse.barulho(p, a);
        World w = p.getWorld();
        Location olho = p.getEyeLocation();
        w.playSound(olho, a.som(), a.projeteis() > 1 || a.dano() > 15 ? 1.0f : 0.55f, a.tom() + (rnd.nextFloat() - 0.5f) * 0.1f);
        for (int i = 0; i < a.projeteis(); i++) {
            Vector dir = olho.getDirection();
            double e = Math.toRadians(a.espalha()) * (p.isSneaking() ? 0.5 : 1.0);
            dir.add(new Vector(rnd.nextGaussian() * e, rnd.nextGaussian() * e, rnd.nextGaussian() * e)).normalize();
            RayTraceResult r = w.rayTrace(olho, dir, a.alcance(), FluidCollisionMode.NEVER, true, 0.12,
                    x -> x != p && x instanceof LivingEntity le && !le.isDead() && !(x instanceof org.bukkit.entity.ArmorStand) && regras.podeAtingir(p, le));
            double dist = a.alcance();
            if (r != null) {
                dist = r.getHitPosition().distance(olho.toVector());
                Entity alvo = r.getHitEntity();
                if (alvo instanceof LivingEntity le) {
                    boolean cabeca = r.getHitPosition().getY() >= le.getLocation().getY() + le.getHeight() * 0.78;
                    double dano = a.dano() * (cabeca ? 1.8 : 1.0);
                    le.setNoDamageTicks(0);
                    le.damage(dano, p);
                    le.setNoDamageTicks(0);
                    // 16/11 (Paulo: "as balas sao balas normais"): respingo pequeno e escuro (o bloco de redstone virava quadradao vermelho no Bedrock)
                    w.spawnParticle(Particle.DUST, r.getHitPosition().toLocation(w), cabeca ? 5 : 3, 0.06, 0.06, 0.06, 0, new Particle.DustOptions(Color.fromRGB(96, 8, 10), 0.55f));
                    if (cabeca) p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 1.7f);
                    regras.acertou(p, le, dano, cabeca);
                } else if (r.getHitBlock() != null) {
                    w.spawnParticle(Particle.BLOCK, r.getHitPosition().toLocation(w), 6, 0.05, 0.05, 0.05, 0, r.getHitBlock().getBlockData());
                }
            }
            // 16/11: sem rastro desenhado (bala de verdade nao se ve; no Bedrock a poeira amarela saia vermelha)
        }
        // coice leve
        if (a.dano() > 15 || a.projeteis() > 1) p.setVelocity(p.getVelocity().add(olho.getDirection().multiply(-0.12).setY(0)));
        mostrar(p, a, p.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void usar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        Arma a = armaDe(it);
        if (a == null) return;
        e.setUseItemInHand(Event.Result.DENY);              // a enxada nao ara a terra
        if (!pl.mundoDeTiro(p.getWorld())) {
            if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) p.sendActionBar(Tiro.txt("§7As armas só funcionam nos jogos de tiro."));
            return;
        }
        if (pl.missao != null && pl.missao.derrubado(p)) {
            e.setCancelled(true);
            p.sendActionBar(Tiro.txt("§cDerrubado: §7espere um companheiro te levantar"));
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return;
            e.setUseInteractedBlock(Event.Result.DENY);
            segurandoAte.put(p.getUniqueId(), System.currentTimeMillis() + 260);
            atirar(p, a, it);
        } else if (e.getAction() == Action.LEFT_CLICK_AIR) recarregar(p);
    }

    /** Q com a arma na mao = recarregar (nao larga a arma). */
    @EventHandler(priority = EventPriority.LOW)
    public void largar(PlayerDropItemEvent e) {
        if (armaDe(e.getItemDrop().getItemStack()) == null || !pl.mundoDeTiro(e.getPlayer().getWorld())) return;
        if (e.getPlayer().getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING || e.getPlayer().isSneaking()) return;   // agachado + Q larga a arma de verdade
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(pl, () -> recarregar(e.getPlayer()));
    }

    @EventHandler
    public void trocou(PlayerItemHeldEvent e) {
        recarregandoAte.remove(e.getPlayer().getUniqueId());
        xpFalso.put(e.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        proximoTiro.remove(u);
        segurandoAte.remove(u);
        recarregandoAte.remove(u);
        xpFalso.remove(u);
    }
}
