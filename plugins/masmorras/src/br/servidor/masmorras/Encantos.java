package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Encantamentos especiais do Encantador do lobby (18/11, ideia do Sui, Paulo: "encantamentos diferentes... veneno,
 * lancar bola de neve... seja criativo"). Pagos com o XP guardado no Banco de XP. Ficam no item (dados + lore + brilho),
 * entao funcionam no Java e no Bedrock. So valem nos mundos das masmorras (nao mexem no equilibrio dos minigames).
 */
final class Encantos implements Listener {

    enum Alvo { ARMA, ARCO, CAPACETE, PEITO, CALCA, BOTAS, FERRAMENTA }

    record Enc(String id, String nome, String cor, int max, int preco, Alvo[] alvos, String desc) {
        String nomeNivel(int n) {
            return cor + nome + " " + romano(n);
        }
    }

    static final Map<String, Enc> TODOS = new LinkedHashMap<>();

    private static void add(String id, String nome, String cor, int max, int preco, String desc, Alvo... alvos) {
        TODOS.put(id, new Enc(id, nome, cor, max, preco, alvos, desc));
    }

    static {
        add("veneno", "Veneno", "§2", 3, 400, "Chance de envenenar o alvo a cada golpe", Alvo.ARMA);
        add("vampiro", "Vampirismo", "§4", 3, 600, "Cura você com parte do dano que causa", Alvo.ARMA);
        add("raio", "Fúria do Trovão", "§e", 2, 900, "Chance de cair um raio no alvo (+5 de dano)", Alvo.ARMA);
        add("gelo", "Toque Gélido", "§b", 2, 500, "Chance de congelar o alvo (lentidão forte)", Alvo.ARMA);
        add("explosao", "Impacto Explosivo", "§c", 2, 1000, "Chance de explodir e ferir quem está em volta", Alvo.ARMA);
        add("neve", "Bola de Neve Mágica", "§f", 3, 500, "Clique direito: lança uma bola de neve que fere e deixa lento", Alvo.ARMA);
        add("sangria", "Sangria", "§4", 2, 700, "O alvo sangra por alguns segundos", Alvo.ARMA);
        add("multi", "Chuva de Flechas", "§6", 2, 900, "Atira flechas extras em leque", Alvo.ARCO);
        add("teia", "Flecha Teia", "§7", 2, 600, "A flecha prende o alvo numa teia", Alvo.ARCO);
        add("tele", "Flecha do Andarilho", "§d", 1, 1500, "Você é levado para onde a flecha cair (8 s de espera)", Alvo.ARCO);
        add("veneno_arco", "Flecha Venenosa", "§2", 2, 500, "A flecha envenena", Alvo.ARCO);
        add("visao", "Olhos de Coruja", "§9", 1, 700, "Visão noturna enquanto usa", Alvo.CAPACETE);
        add("respirar", "Guelras", "§3", 1, 500, "Respira debaixo d'água", Alvo.CAPACETE);
        add("espinho_gelo", "Armadura de Gelo", "§b", 2, 700, "Quem bate em você fica lento", Alvo.PEITO);
        add("fenix", "Fênix", "§6", 1, 3000, "Uma vez a cada 10 min, não morre: volta com fogo", Alvo.PEITO);
        add("regen", "Coração Vivo", "§c", 2, 1200, "Regeneração de tempos em tempos", Alvo.PEITO);
        add("velocista", "Velocista", "§a", 2, 800, "Corre mais rápido", Alvo.CALCA, Alvo.BOTAS);
        add("pulo", "Saltitante", "§a", 2, 600, "Pula mais alto e não toma dano de queda pequena", Alvo.BOTAS);
        add("ima", "Ímã", "§5", 1, 800, "O que você quebra vai direto para a mochila", Alvo.FERRAMENTA);
        add("fundir", "Toque de Forja", "§6", 1, 1200, "Minério de ferro, ouro e cobre já sai em barra", Alvo.FERRAMENTA);
    }

    static final int MAX_POR_ITEM = 3;

    private final Masmorras pl;
    private final NamespacedKey kEnc, kProj, kDono, kLivro;
    private final Map<UUID, Long> esperaNeve = new HashMap<>(), esperaTele = new HashMap<>(), esperaFenix = new HashMap<>();
    private boolean aplicando;

    Encantos(Masmorras pl) {
        this.pl = pl;
        kEnc = new NamespacedKey(pl, "encantos");
        kProj = new NamespacedKey(pl, "enc_proj");
        kDono = new NamespacedKey(pl, "enc_dono");
        kLivro = new NamespacedKey(pl, "livro_encanto");
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::armaduras, 60L, 40L);
    }

    static String romano(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> String.valueOf(n); };
    }

    boolean vale(World w) {
        return w != null && w.getName().toLowerCase(Locale.ROOT).startsWith("masmorras");
    }

    // ------------------------------------------------------------------ dados no item

    static Alvo alvoDe(Material m) {
        String n = m.name();
        if (n.endsWith("_SWORD") || n.equals("MACE") || n.equals("TRIDENT") || n.endsWith("_SPEAR")) return Alvo.ARMA;
        if (n.equals("BOW") || n.equals("CROSSBOW")) return Alvo.ARCO;
        if (n.endsWith("_HELMET") || n.equals("TURTLE_HELMET")) return Alvo.CAPACETE;
        if (n.endsWith("_CHESTPLATE")) return Alvo.PEITO;
        if (n.endsWith("_LEGGINGS")) return Alvo.CALCA;
        if (n.endsWith("_BOOTS")) return Alvo.BOTAS;
        if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) return Alvo.FERRAMENTA;
        if (n.endsWith("_AXE")) return Alvo.ARMA;   // machado: arma (e tambem ferramenta, ver serve())
        return null;
    }

    static boolean serve(Enc e, Material m) {
        Alvo a = alvoDe(m);
        if (a == null) return false;
        for (Alvo x : e.alvos()) if (x == a || (x == Alvo.FERRAMENTA && m.name().endsWith("_AXE"))) return true;
        return false;
    }

    Map<String, Integer> ler(ItemStack it) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (it == null || !it.hasItemMeta()) return m;
        String s = it.getItemMeta().getPersistentDataContainer().get(kEnc, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return m;
        for (String p : s.split(",")) {
            String[] kv = p.split(":");
            if (kv.length == 2 && TODOS.containsKey(kv[0])) {
                try {
                    m.put(kv[0], Integer.parseInt(kv[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return m;
    }

    int nivel(ItemStack it, String id) {
        return ler(it).getOrDefault(id, 0);
    }

    /** Grava os encantos no item, com a lore (linhas com ✦ sao nossas) e o brilho. */
    void gravar(ItemStack it, Map<String, Integer> m) {
        ItemMeta meta = it.getItemMeta();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) sb.append(sb.length() == 0 ? "" : ",").append(e.getKey()).append(':').append(e.getValue());
        if (m.isEmpty()) meta.getPersistentDataContainer().remove(kEnc);
        else meta.getPersistentDataContainer().set(kEnc, PersistentDataType.STRING, sb.toString());
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (meta.lore() != null)
            for (net.kyori.adventure.text.Component c : meta.lore()) {
                String t = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
                if (!t.startsWith("✦ ")) lore.add(c);
            }
        List<net.kyori.adventure.text.Component> nossas = new ArrayList<>();
        for (Map.Entry<String, Integer> e : m.entrySet())
            nossas.add(Masmorras.txt("§5✦ " + TODOS.get(e.getKey()).nomeNivel(e.getValue())).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        nossas.addAll(lore);
        meta.lore(nossas.isEmpty() ? null : nossas);
        meta.setEnchantmentGlintOverride(m.isEmpty() ? null : true);
        it.setItemMeta(meta);
    }

    // ------------------------------------------------------------------ Encantador (menu) e livros de encanto
    //
    // 18/11 (Paulo: "um item que voce consegue clicar ou apertar em cima do item que voce quer encantar"): o Encantador
    // vende LIVROS. Para usar: pegar o livro e clicar/tocar em cima do item na mochila (PC e celular), ou segurar o livro
    // e clicar com o botao direito (no celular: tocar e segurar) para abrir a lista dos itens que servem e tocar num deles.

    private static net.kyori.adventure.text.Component t(String s) {
        return Masmorras.txt(s).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    private static String ondeServe(Enc e) {
        List<String> l = new ArrayList<>();
        for (Alvo a : e.alvos())
            l.add(switch (a) {
                case ARMA -> "espadas, machados, tridentes e maças";
                case ARCO -> "arcos e bestas";
                case CAPACETE -> "capacetes";
                case PEITO -> "peitorais";
                case CALCA -> "calças";
                case BOTAS -> "botas";
                case FERRAMENTA -> "picaretas, pás, enxadas e machados";
            });
        return String.join(", ", l);
    }

    ItemStack livro(Enc e, int n) {
        ItemStack b = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta m = b.getItemMeta();
        m.displayName(t("§5§lLivro: " + e.nomeNivel(n)));
        m.lore(List.of(t("§7" + e.desc()), t("§8serve em: " + ondeServe(e)), t(""),
                t("§eComo usar:"), t("§f• pegue o livro e clique (ou toque) em cima do item"), t("§f• ou segure o livro e clique com o botão direito"),
                t("§8  (no celular: toque e segure) para escolher o item")));
        m.getPersistentDataContainer().set(kLivro, PersistentDataType.STRING, e.id() + ":" + n);
        b.setItemMeta(m);
        return b;
    }

    private String[] livroDe(ItemStack it) {
        if (it == null || it.getType() != Material.ENCHANTED_BOOK || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(kLivro, PersistentDataType.STRING);
        if (s == null) return null;
        String[] kv = s.split(":");
        return kv.length == 2 && TODOS.containsKey(kv[0]) ? kv : null;
    }

    /** Poe o encanto do livro no item. Devolve null se deu certo, ou o motivo. */
    String aplicar(ItemStack alvo, Enc e, int n) {
        if (alvo == null || alvo.getType().isAir()) return "Escolha um item.";
        if (!serve(e, alvo.getType())) return "Este livro serve em " + ondeServe(e) + ".";
        if (alvo.getAmount() != 1) return "Só dá para encantar um item por vez.";
        Map<String, Integer> tem = ler(alvo);
        int atual = tem.getOrDefault(e.id(), 0);
        if (atual == 0 && tem.size() >= MAX_POR_ITEM) return "Este item já tem " + MAX_POR_ITEM + " encantos especiais.";
        int novo = n > atual ? n : (n == atual && atual < e.max() ? atual + 1 : -1);
        if (novo < 0) return "Este item já tem " + e.nomeNivel(atual) + "§c.";
        tem.put(e.id(), novo);
        gravar(alvo, tem);
        return null;
    }

    private void efeitoAplicou(Player p, Enc e, ItemStack alvo) {
        p.playSound(p.getLocation(), "minecraft:block.enchantment_table.use", 1f, 1.4f);
        p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.6f, 1.6f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 50, 0.5, 0.8, 0.5, 0.8);
        p.sendMessage(Masmorras.txt("§d✦ §fEncantou: " + TODOS.get(e.id()).nomeNivel(nivel(alvo, e.id())) + " §fem §e" + alvo.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
    }

    static final class Menu implements InventoryHolder {
        Inventory inv;
        final List<String[]> itens = new ArrayList<>();   // {id, nivel} por slot (a partir do 9)

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Lista para escolher em qual item da mochila vai o livro (bom para o celular). */
    static final class Escolha implements InventoryHolder {
        Inventory inv;
        final List<Integer> slots = new ArrayList<>();
        int livroSlot;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void abrir(Player p) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, 54, Masmorras.txt("§5§lEncantador §8(livros pagos com XP)"));
        long saldo = pl.lojaXp.saldo(p);
        ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta im = info.getItemMeta();
        im.displayName(t("§a§lSeu XP no banco: " + LojaXp.fmt(saldo)));
        im.lore(List.of(t("§7Clique num livro para comprar."), t("§7Depois clique com ele em cima do item que quer encantar."),
                t("§8Até " + MAX_POR_ITEM + " encantos especiais por item. Dois livros iguais sobem o nível.")));
        info.setItemMeta(im);
        m.inv.setItem(4, info);
        int slot = 9;
        for (Enc e : TODOS.values())
            for (int n = 1; n <= e.max() && slot < 54; n++, slot++) {
                ItemStack b = livro(e, n);
                ItemMeta bm = b.getItemMeta();
                List<net.kyori.adventure.text.Component> l = new ArrayList<>(bm.lore());
                int preco = e.preco() * n;
                l.add(0, t("§fPreço: " + (saldo >= preco ? "§a" : "§c") + LojaXp.fmt(preco) + " XP"));
                bm.lore(l.subList(0, 3));
                b.setItemMeta(bm);
                m.inv.setItem(slot, b);
                m.itens.add(new String[] {e.id(), String.valueOf(n)});
            }
        p.openInventory(m.inv);
        p.playSound(p.getLocation(), "minecraft:block.enchantment_table.use", 0.8f, 1.1f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clique(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof Menu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory()) return;
            int i = e.getRawSlot() - 9;
            if (i < 0 || i >= m.itens.size()) return;
            Enc enc = TODOS.get(m.itens.get(i)[0]);
            int n = Integer.parseInt(m.itens.get(i)[1]);
            int preco = enc.preco() * n;
            if (p.getInventory().firstEmpty() < 0) {
                p.sendActionBar(Masmorras.txt("§cSua mochila está cheia."));
                return;
            }
            if (!pl.lojaXp.gastar(p, preco)) {
                p.sendMessage(Masmorras.txt("§d§lEncantador: §fFaltam §c" + LojaXp.fmt(preco - pl.lojaXp.saldo(p)) + " XP§f no banco. Guarde XP com o §aBanqueiro de XP§f."));
                p.playSound(p.getLocation(), "minecraft:entity.villager.no", 1f, 1f);
                return;
            }
            p.getInventory().addItem(livro(enc, n));
            p.playSound(p.getLocation(), "minecraft:item.book.page_turn", 1f, 1f);
            p.playSound(p.getLocation(), "minecraft:entity.experience_orb.pickup", 0.8f, 0.8f);
            p.sendMessage(Masmorras.txt("§d§lEncantador: §fAqui está o livro de " + enc.nomeNivel(n) + "§f. Clique com ele em cima do item para encantar."));
            pl.getLogger().info("Encantador: " + p.getName() + " comprou livro " + enc.id() + " " + n + " por " + preco + " XP");
            abrir(p);
            return;
        }
        if (h instanceof Escolha es) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory()) return;
            int i = e.getRawSlot();
            if (i < 0 || i >= es.slots.size()) return;
            ItemStack lv = p.getInventory().getItem(es.livroSlot);
            String[] kv = livroDe(lv);
            if (kv == null) {
                p.closeInventory();
                return;
            }
            ItemStack alvo = p.getInventory().getItem(es.slots.get(i));
            Enc enc = TODOS.get(kv[0]);
            String erro = aplicar(alvo, enc, Integer.parseInt(kv[1]));
            if (erro != null) {
                p.sendActionBar(Masmorras.txt("§c" + erro));
                return;
            }
            lv.setAmount(lv.getAmount() - 1);
            p.closeInventory();
            efeitoAplicou(p, enc, alvo);
            return;
        }
        // livro na mao do mouse (ou no dedo) clicado em cima de um item da mochila
        ItemStack cursor = e.getCursor();
        String[] kv = livroDe(cursor);
        if (kv == null || e.getClickedInventory() == null || e.getClickedInventory().getType() != org.bukkit.event.inventory.InventoryType.PLAYER) return;
        ItemStack alvo = e.getCurrentItem();
        if (alvo == null || alvo.getType().isAir() || livroDe(alvo) != null) return;
        if (alvoDe(alvo.getType()) == null) return;   // deixa trocar de lugar normalmente
        e.setCancelled(true);
        Enc enc = TODOS.get(kv[0]);
        String erro = aplicar(alvo, enc, Integer.parseInt(kv[1]));
        if (erro != null) {
            p.sendActionBar(Masmorras.txt("§c" + erro));
            return;
        }
        e.setCurrentItem(alvo);
        cursor.setAmount(cursor.getAmount() - 1);
        p.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
        efeitoAplicou(p, enc, alvo);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void usarLivro(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack lv = p.getInventory().getItemInMainHand();
        String[] kv = livroDe(lv);
        if (kv == null) return;
        e.setCancelled(true);
        Enc enc = TODOS.get(kv[0]);
        Escolha es = new Escolha();
        es.livroSlot = p.getInventory().getHeldItemSlot();
        es.inv = Bukkit.createInventory(es, 54, Masmorras.txt("§5Toque no item para pôr " + enc.nome()));
        var inv = p.getInventory();
        for (int s = 0; s < 41 && es.slots.size() < 54; s++) {
            ItemStack it = inv.getItem(s);
            if (it == null || !serve(enc, it.getType())) continue;
            es.inv.setItem(es.slots.size(), it.clone());
            es.slots.add(s);
        }
        if (es.slots.isEmpty()) {
            p.sendMessage(Masmorras.txt("§d✦ §fVocê não tem nenhum item onde serve este livro §7(" + ondeServe(enc) + ")§f."));
            return;
        }
        p.openInventory(es.inv);
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu || e.getInventory().getHolder() instanceof Escolha) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ efeitos: golpes

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void golpe(EntityDamageByEntityEvent e) {
        if (aplicando || !(e.getEntity() instanceof LivingEntity alvo) || !vale(alvo.getWorld())) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // quem apanha com Armadura de Gelo deixa o agressor lento
        if (alvo instanceof Player v) {
            int g = nivel(v.getInventory().getChestplate(), "espinho_gelo");
            Entity d = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
            if (g > 0 && d instanceof LivingEntity ag && ag != v) {
                ag.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 + 20 * g, g));
                ag.getWorld().spawnParticle(Particle.SNOWFLAKE, ag.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
            }
        }
        if (e.getDamager() instanceof Arrow fl && fl.getShooter() instanceof Player p) {
            String marca = fl.getPersistentDataContainer().get(kProj, PersistentDataType.STRING);
            if (marca == null) return;
            for (String m : marca.split(",")) {
                String[] kv = m.split(":");
                int n = kv.length > 1 ? Integer.parseInt(kv[1]) : 1;
                if (kv[0].equals("teia")) teia(alvo, n);
                if (kv[0].equals("veneno_arco")) alvo.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60 + 30 * n, n - 1));
            }
            return;
        }
        if (!(e.getDamager() instanceof Player p) || alvo == p) return;
        ItemStack arma = p.getInventory().getItemInMainHand();
        Map<String, Integer> m = ler(arma);
        if (m.isEmpty() || alvoDe(arma.getType()) != Alvo.ARMA) return;
        Location l = alvo.getLocation().add(0, alvo.getHeight() * 0.6, 0);
        Integer n;
        if ((n = m.get("veneno")) != null && r.nextDouble() < 0.20 + 0.10 * n) {
            alvo.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40 + 20 * n, n - 1));
            alvo.getWorld().spawnParticle(Particle.ITEM_SLIME, l, 10, 0.3, 0.4, 0.3, 0.05);
        }
        if ((n = m.get("vampiro")) != null) {
            double cura = e.getFinalDamage() * (0.06 + 0.04 * n);
            var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (max != null && cura > 0) p.setHealth(Math.min(max.getValue(), p.getHealth() + cura));
            if (r.nextInt(3) == 0) p.getWorld().spawnParticle(Particle.DUST, l, 8, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.2f));
        }
        if ((n = m.get("raio")) != null && r.nextDouble() < 0.05 + 0.05 * n) {
            alvo.getWorld().strikeLightningEffect(alvo.getLocation());
            ferir(alvo, 5.0, p);
        }
        if ((n = m.get("gelo")) != null && r.nextDouble() < 0.18 + 0.08 * n) {
            alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 20 * n, 2 + n));
            alvo.setFreezeTicks(Math.max(alvo.getFreezeTicks(), 100 + 40 * n));
            alvo.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 20, 0.4, 0.5, 0.4, 0.03);
            alvo.getWorld().playSound(alvo.getLocation(), "minecraft:block.glass.break", 0.6f, 1.6f);
        }
        if ((n = m.get("explosao")) != null && r.nextDouble() < 0.06 + 0.05 * n) {
            Location c = alvo.getLocation();
            c.getWorld().spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.8, 0), 1);
            c.getWorld().playSound(c, "minecraft:entity.generic.explode", 0.8f, 1.2f);
            for (Entity x : c.getWorld().getNearbyEntities(c, 3, 2, 3))
                if (x instanceof LivingEntity le && x != p && !(x instanceof Player) && !(x instanceof org.bukkit.entity.Villager) && !(x instanceof org.bukkit.entity.Mannequin))
                    ferir(le, 3.0 + 2.0 * n, p);
        }
        if ((n = m.get("sangria")) != null && r.nextDouble() < 0.25) sangrar(alvo, p, n);
    }

    private void ferir(LivingEntity alvo, double dano, Player p) {
        aplicando = true;
        try {
            alvo.damage(dano, p);
        } finally {
            aplicando = false;
        }
    }

    private void sangrar(LivingEntity alvo, Player p, int n) {
        int[] c = {0};
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            if (!alvo.isValid() || alvo.isDead() || c[0]++ >= 3 + n) {
                t.cancel();
                return;
            }
            ferir(alvo, 1.0 + 0.5 * n, p);
            alvo.getWorld().spawnParticle(Particle.DUST, alvo.getLocation().add(0, 1, 0), 8, 0.25, 0.4, 0.25, 0, new Particle.DustOptions(Color.fromRGB(170, 0, 0), 1.0f));
        }, 20L, 20L);
    }

    private void teia(LivingEntity alvo, int n) {
        alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 + 20 * n, 5));
        alvo.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30 + 20 * n, 200));   // nao pula
        alvo.getWorld().spawnParticle(Particle.BLOCK, alvo.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0, Material.COBWEB.createBlockData());
        alvo.getWorld().playSound(alvo.getLocation(), "minecraft:block.wool.place", 1f, 0.8f);
    }

    // ------------------------------------------------------------------ bola de neve magica

    @EventHandler(priority = EventPriority.HIGH)
    public void clicar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!vale(p.getWorld())) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        int n = nivel(it, "neve");
        if (n <= 0 || alvoDe(it.getType()) != Alvo.ARMA) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) return;
        long agora = System.currentTimeMillis();
        long espera = 3200 - 500L * n;
        Long ult = esperaNeve.get(p.getUniqueId());
        if (ult != null && agora - ult < espera) return;
        esperaNeve.put(p.getUniqueId(), agora);
        Snowball s = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.8));
        s.getPersistentDataContainer().set(kProj, PersistentDataType.STRING, "neve:" + n);
        s.getPersistentDataContainer().set(kDono, PersistentDataType.STRING, p.getUniqueId().toString());
        p.getWorld().playSound(p.getLocation(), "minecraft:entity.snowball.throw", 1f, 0.7f);
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            if (!s.isValid()) {
                t.cancel();
                return;
            }
            s.getWorld().spawnParticle(Particle.SNOWFLAKE, s.getLocation(), 2, 0.05, 0.05, 0.05, 0.0);
        }, 1L, 2L);
    }

    @EventHandler
    public void acertou(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        String marca = pr.getPersistentDataContainer().get(kProj, PersistentDataType.STRING);
        if (marca == null) return;
        if (marca.startsWith("neve:") && pr.getShooter() instanceof Player p) {
            int n = Integer.parseInt(marca.substring(5));
            Location l = pr.getLocation();
            l.getWorld().spawnParticle(Particle.SNOWFLAKE, l, 30, 0.5, 0.5, 0.5, 0.08);
            l.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, l, 12, 0.3, 0.3, 0.3, 0.1);
            l.getWorld().playSound(l, "minecraft:block.powder_snow.break", 1f, 1f);
            if (e.getHitEntity() instanceof LivingEntity alvo && alvo != p) {
                ferir(alvo, 2.0 + 1.5 * n, p);
                alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 20 * n, 1 + n / 2));
                alvo.setFreezeTicks(Math.max(alvo.getFreezeTicks(), 80));
            }
            return;
        }
        if (marca.contains("tele") && pr.getShooter() instanceof Player p && vale(p.getWorld()) && p.getWorld().equals(pr.getWorld())) {
            Location l = pr.getLocation();
            if (e.getHitBlock() != null && e.getHitBlockFace() != null)
                l = e.getHitBlock().getRelative(e.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
            Block b = l.getBlock();
            if (!b.isPassable() || !b.getRelative(0, 1, 0).isPassable() || pl.lobby != null && pl.lobby.noLobby(l)) return;
            l.setYaw(p.getLocation().getYaw());
            l.setPitch(p.getLocation().getPitch());
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.5);
            p.teleport(l);
            p.setFallDistance(0);
            p.getWorld().playSound(l, "minecraft:entity.enderman.teleport", 1f, 1.3f);
            pr.remove();
        }
    }

    // ------------------------------------------------------------------ arco

    @EventHandler(ignoreCancelled = true)
    public void atirou(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !vale(p.getWorld()) || e.getBow() == null) return;
        Map<String, Integer> m = ler(e.getBow());
        if (m.isEmpty() || !(e.getProjectile() instanceof AbstractArrow fl)) return;
        StringBuilder marca = new StringBuilder();
        for (String id : new String[] {"teia", "veneno_arco"}) if (m.containsKey(id)) marca.append(marca.length() == 0 ? "" : ",").append(id).append(':').append(m.get(id));
        if (m.containsKey("tele")) {
            long agora = System.currentTimeMillis();
            Long ult = esperaTele.get(p.getUniqueId());
            if (ult == null || agora - ult >= 8000) {
                esperaTele.put(p.getUniqueId(), agora);
                marca.append(marca.length() == 0 ? "" : ",").append("tele:1");
                fl.setGlowing(true);
            }
        }
        if (marca.length() > 0) fl.getPersistentDataContainer().set(kProj, PersistentDataType.STRING, marca.toString());
        Integer multi = m.get("multi");
        if (multi != null) {
            Vector v = fl.getVelocity();
            int extra = 2 * multi;
            for (int k = 1; k <= extra; k++) {
                double ang = Math.toRadians((k % 2 == 0 ? 1 : -1) * 6.0 * ((k + 1) / 2));
                Vector nv = v.clone().rotateAroundY(ang);
                Arrow a = p.getWorld().spawnArrow(fl.getLocation(), nv, (float) v.length(), 0f);
                a.setShooter(p);
                a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                a.setDamage(fl.getDamage() * 0.6);
                if (marca.length() > 0 && !marca.toString().contains("tele")) a.getPersistentDataContainer().set(kProj, PersistentDataType.STRING, marca.toString());
                Bukkit.getScheduler().runTaskLater(pl, a::remove, 100L);
            }
        }
    }

    // ------------------------------------------------------------------ armaduras (a cada 2 s)

    private void armaduras() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!vale(p.getWorld())) continue;
            var inv = p.getInventory();
            int vis = nivel(inv.getHelmet(), "visao"), resp = nivel(inv.getHelmet(), "respirar");
            int vel = Math.max(nivel(inv.getLeggings(), "velocista"), nivel(inv.getBoots(), "velocista"));
            int pulo = nivel(inv.getBoots(), "pulo"), reg = nivel(inv.getChestplate(), "regen");
            if (vis > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 320, 0, true, false, true));
            if (resp > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 80, 0, true, false, true));
            if (vel > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, vel - 1, true, false, true));
            if (pulo > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, pulo - 1, true, false, true));
            if (reg > 0 && (Bukkit.getCurrentTick() / 40) % (reg == 1 ? 6 : 4) == 0)
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void quase(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !vale(p.getWorld())) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && nivel(p.getInventory().getBoots(), "pulo") > 0 && e.getDamage() <= 4) {
            e.setCancelled(true);
            return;
        }
        if (e.getFinalDamage() < p.getHealth() || nivel(p.getInventory().getChestplate(), "fenix") <= 0) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        long agora = System.currentTimeMillis();
        Long ult = esperaFenix.get(p.getUniqueId());
        if (ult != null && agora - ult < 600_000L) return;
        esperaFenix.put(p.getUniqueId(), agora);
        e.setCancelled(true);
        p.setHealth(Math.min(8.0, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        Location l = p.getLocation();
        l.getWorld().spawnParticle(Particle.FLAME, l.clone().add(0, 1, 0), 80, 0.6, 1.0, 0.6, 0.08);
        l.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
        l.getWorld().playSound(l, "minecraft:item.totem.use", 0.8f, 1.2f);
        for (Entity x : l.getWorld().getNearbyEntities(l, 4, 2, 4))
            if (x instanceof LivingEntity le && !(x instanceof Player) && !(x instanceof org.bukkit.entity.Villager) && !(x instanceof org.bukkit.entity.Mannequin)) le.setFireTicks(100);
        p.sendMessage(Masmorras.txt("§6§lFÊNIX! §fVocê renasceu das cinzas. §7(de novo em 10 minutos)"));
    }

    // ------------------------------------------------------------------ ferramentas

    private static final Set<Material> MINERIOS = EnumSet.of(Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void quebrou(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (!vale(p.getWorld())) return;
        ItemStack t = p.getInventory().getItemInMainHand();
        Map<String, Integer> m = ler(t);
        if (m.isEmpty()) return;
        if (m.containsKey("fundir"))
            for (var it : e.getItems()) {
                ItemStack s = it.getItemStack();
                if (!MINERIOS.contains(s.getType())) continue;
                Material barra = switch (s.getType()) { case RAW_IRON -> Material.IRON_INGOT; case RAW_GOLD -> Material.GOLD_INGOT; default -> Material.COPPER_INGOT; };
                it.setItemStack(new ItemStack(barra, s.getAmount()));
                p.getWorld().spawnParticle(Particle.FLAME, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.01);
            }
        if (m.containsKey("ima")) {
            var itr = e.getItems().iterator();
            while (itr.hasNext()) {
                var it = itr.next();
                var sobra = p.getInventory().addItem(it.getItemStack());
                if (sobra.isEmpty()) itr.remove();
                else it.setItemStack(sobra.values().iterator().next());
            }
        }
    }
}
