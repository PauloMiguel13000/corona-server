package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Banco de XP e Loja de XP do lobby das masmorras (18/11, ideia do Sui, Paulo: "no lobby das masmorras... na loja a
 * pessoa consegue comprar alguns dias de VIP"). O Banqueiro guarda e devolve XP (1 nivel, 10 niveis ou tudo) e mostra o
 * saldo; a Loja troca o XP guardado por VIP (dias), coronas e itens. O Encantador (Encantos) tambem cobra deste saldo.
 * Saldo em pontos de XP (o mesmo que a barra verde), guardado em banco-xp.yml.
 */
final class LojaXp implements Listener {

    static final double[] BANCO = {26.5, -10.5}, ENCANTADOR = {-25.5, -10.5}, RUNAS = {-25.5, 9.5};

    private final Masmorras pl;
    private final File arquivo;
    private final YamlConfiguration dados;
    final NamespacedKey kBanco, kEncantador, kRunas;
    private static final NamespacedKey VIP_NIVEL = new NamespacedKey("extras", "vip_nivel");
    private World mundo;
    private int y;

    record Oferta(String nome, Material icone, long preco, String desc, String tipo, int valor) {}

    static final Oferta[] LOJA = {
            new Oferta("§a§lVIP por 1 dia", Material.EMERALD, 70000, "Tag, bônus de coronas e kit do VIP por 24 horas", "vip", 1),
            new Oferta("§a§lVIP por 3 dias", Material.EMERALD_BLOCK, 185000, "Tag, bônus de coronas e kit do VIP por 3 dias", "vip", 3),
            new Oferta("§a§lVIP por 7 dias", Material.BEACON, 375000, "Tag, bônus de coronas e kit do VIP por 7 dias", "vip", 7),
            new Oferta("§e§l250 coronas", Material.GOLD_NUGGET, 1500, "Coronas para gastar nas lojas do servidor", "coronas", 250),
            new Oferta("§e§l1.000 coronas", Material.GOLD_INGOT, 5500, "Coronas para gastar nas lojas do servidor", "coronas", 1000),
            new Oferta("§6§lTotem da Imortalidade", Material.TOTEM_OF_UNDYING, 6000, "Não morre uma vez", "item", 1),
            new Oferta("§d§lMaçã Dourada Encantada", Material.ENCHANTED_GOLDEN_APPLE, 5000, "Regeneração forte e absorção", "item", 1),
            new Oferta("§c§lAnel de Fogo", Material.BLAZE_POWDER, 4000, "Forte contra chefes de Vento", "anel", 0),
            new Oferta("§9§lAnel de Água", Material.HEART_OF_THE_SEA, 4000, "Forte contra chefes de Fogo", "anel", 1),
            new Oferta("§2§lAnel de Terra", Material.EMERALD, 4000, "Forte contra chefes de Raio", "anel", 2),
            new Oferta("§f§lAnel de Vento", Material.BREEZE_ROD, 4000, "Forte contra chefes de Terra", "anel", 3),
            new Oferta("§e§lAnel de Raio", Material.GLOWSTONE_DUST, 4000, "Forte contra chefes de Água", "anel", 4),
            new Oferta("§5§lAnel do Vazio", Material.ECHO_SHARD, 6000, "Neutro: um pouco melhor contra tudo", "anel", 5),
    };
    static final int[] SLOTS_LOJA = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};

    LojaXp(Masmorras pl) {
        this.pl = pl;
        arquivo = new File(pl.getDataFolder(), "banco-xp.yml");
        dados = YamlConfiguration.loadConfiguration(arquivo);
        kBanco = new NamespacedKey(pl, "lobby_banco");
        kEncantador = new NamespacedKey(pl, "lobby_encantador");
        kRunas = new NamespacedKey(pl, "lobby_runas");
    }

    void iniciar(World w, int yChao) {
        mundo = w;
        y = yChao;
        if (mundo == null) return;
        File feito = new File(pl.getDataFolder(), "lobby-mundo-v6.txt");
        if (!feito.exists()) {
            try {
                for (int cx = -2; cx <= 1; cx++) for (int cz = -1; cz <= 0; cz++) mundo.getChunkAt(cx, cz).load(true);
                construir();
                java.nio.file.Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Banco/Encantador do lobby: " + ex);
            }
        }
        // 20/11: Mestre das Runas (abre o /runa do Poderes), com estrado de ametista
        File fr = new File(pl.getDataFolder(), "lobby-runas-v1.txt");
        if (!fr.exists()) {
            try {
                mundo.getChunkAt(-2, 0).load(true);
                int rx = (int) Math.floor(RUNAS[0]), rz = (int) Math.floor(RUNAS[1]);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) bloco(rx + dx, 0, rz + dz, dx == 0 && dz == 0 ? "amethyst_block" : "purpur_block");
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int k = 1; k <= 3; k++)
                    if (!(dx == 0 && dz == 0)) mundo.getBlockAt(rx + dx, y + k, rz + dz).setType(org.bukkit.Material.AIR, false);
                texto(new Location(mundo, RUNAS[0], y + 3.6, RUNAS[1]), "§d§lMESTRE DAS RUNAS\n§7Suba o nível da sua classe (1 a 60)\n§8clique nele ou use /runa");
                java.nio.file.Files.writeString(fr.toPath(), "construido em " + new java.util.Date() + "\n");
            } catch (Throwable ex) {
                pl.getLogger().warning("Mestre das Runas: " + ex);
            }
        }
        Bukkit.getScheduler().runTaskTimer(pl, this::garantir, 140L, 200L);
    }

    // ------------------------------------------------------------------ saldo

    long saldo(Player p) {
        return dados.getLong(p.getUniqueId().toString(), 0);
    }

    private void por(Player p, long v) {
        dados.set(p.getUniqueId().toString(), Math.max(0, v));
        dados.set("nomes." + p.getUniqueId(), p.getName());
        try {
            dados.save(arquivo);
        } catch (IOException ex) {
            pl.getLogger().warning("banco-xp.yml: " + ex);
        }
    }

    boolean gastar(Player p, long v) {
        long s = saldo(p);
        if (s < v) return false;
        por(p, s - v);
        return true;
    }

    static String fmt(long v) {
        return String.format(Locale.ROOT, "%,d", v).replace(',', '.');
    }

    // ------------------------------------------------------------------ conta de XP (igual a do jogo)

    static long pontosAte(int nivel) {
        long n = nivel;
        if (n <= 16) return n * n + 6 * n;
        if (n <= 31) return Math.round(2.5 * n * n - 40.5 * n + 360);
        return Math.round(4.5 * n * n - 162.5 * n + 2220);
    }

    static int barra(int nivel) {
        if (nivel <= 15) return 2 * nivel + 7;
        if (nivel <= 30) return 5 * nivel - 38;
        return 9 * nivel - 158;
    }

    static int nivelDe(long pontos) {
        int n = 0;
        while (n < 21863 && pontosAte(n + 1) <= pontos) n++;
        return n;
    }

    static long total(Player p) {
        return pontosAte(p.getLevel()) + Math.round(p.getExp() * barra(p.getLevel()));
    }

    static void definir(Player p, long t) {
        t = Math.max(0, t);
        int n = nivelDe(t);
        p.setLevel(n);
        p.setExp(Math.max(0f, Math.min(0.999f, (float) (t - pontosAte(n)) / barra(n))));
        p.setTotalExperience((int) Math.min(Integer.MAX_VALUE, t));
    }

    private static String emNiveis(long pontos) {
        return "§7(≈ nível " + nivelDe(pontos) + " a partir do zero)";
    }

    // ------------------------------------------------------------------ menus

    static final class Menu implements InventoryHolder {
        Inventory inv;
        final boolean loja;

        Menu(boolean loja) {
            this.loja = loja;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static ItemStack botao(Material m, String nome, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Masmorras.txt(nome).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Masmorras.txt(s).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        im.lore(l);
        it.setItemMeta(im);
        return it;
    }

    void abrirBanco(Player p) {
        Menu m = new Menu(false);
        m.inv = Bukkit.createInventory(m, 27, Masmorras.txt("§2§lBanco de XP"));
        long s = saldo(p), meu = total(p);
        m.inv.setItem(4, botao(Material.EXPERIENCE_BOTTLE, "§a§lSeu saldo: " + fmt(s) + " XP", emNiveis(s), "§7Com você agora: §f" + fmt(meu) + " XP §7(nível " + p.getLevel() + ")",
                "§8O XP guardado não se perde quando você morre."));
        m.inv.setItem(10, botao(Material.LIME_DYE, "§a§lGuardar 1 nível", "§7Tira 1 nível seu e guarda no banco"));
        m.inv.setItem(11, botao(Material.LIME_CONCRETE, "§a§lGuardar 10 níveis", "§7Tira 10 níveis seus e guarda no banco"));
        m.inv.setItem(12, botao(Material.EMERALD_BLOCK, "§a§lGuardar tudo", "§7Guarda todo o XP que você tem"));
        m.inv.setItem(14, botao(Material.RED_DYE, "§c§lSacar 1 nível", "§7Tira do banco e te dá 1 nível"));
        m.inv.setItem(15, botao(Material.RED_CONCRETE, "§c§lSacar 10 níveis", "§7Tira do banco e te dá 10 níveis"));
        m.inv.setItem(16, botao(Material.REDSTONE_BLOCK, "§c§lSacar tudo", "§7Te devolve todo o XP guardado"));
        m.inv.setItem(22, botao(Material.CHEST, "§6§lLoja de XP", "§7VIP, coronas, anéis e itens pagos com o XP do banco", "§eclique para abrir"));
        p.openInventory(m.inv);
    }

    void abrirLoja(Player p) {
        Menu m = new Menu(true);
        m.inv = Bukkit.createInventory(m, 36, Masmorras.txt("§6§lLoja de XP"));
        long s = saldo(p);
        m.inv.setItem(4, botao(Material.EXPERIENCE_BOTTLE, "§a§lSeu saldo: " + fmt(s) + " XP", emNiveis(s)));
        for (int i = 0; i < LOJA.length; i++) {
            Oferta o = LOJA[i];
            ItemStack b = botao(o.icone(), o.nome(), "§7" + o.desc(), "§fPreço: " + (s >= o.preco() ? "§a" : "§c") + fmt(o.preco()) + " XP", emNiveis(o.preco()),
                    o.tipo().equals("vip") ? "§8só para quem não tem VIP maior (VIP+, MVP, LENDA)" : "", "§eclique para comprar");
            if (o.tipo().equals("anel") && pl.aneis != null) {
                // 19/11 (Paulo: "cade os aneis"): na loja aparece o proprio anel, com o desenho do pacote
                ItemStack anel = pl.aneis.anel(Aneis.Elem.values()[o.valor()]);
                ItemMeta am = anel.getItemMeta();
                List<net.kyori.adventure.text.Component> l = new ArrayList<>(am.lore());
                l.add(Masmorras.txt(""));
                l.addAll(b.getItemMeta().lore().subList(1, 3));
                l.add(Masmorras.txt("§eclique para comprar").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                am.lore(l);
                anel.setItemMeta(am);
                b = anel;
            }
            m.inv.setItem(SLOTS_LOJA[i], b);
        }
        m.inv.setItem(31, botao(Material.ARROW, "§7Voltar ao banco"));
        p.openInventory(m.inv);
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        int s = e.getRawSlot();
        if (!m.loja) {
            long meu = total(p), banco = saldo(p);
            long mover = 0;
            switch (s) {
                case 10, 11, 12 -> {
                    int alvo = s == 12 ? 0 : Math.max(0, p.getLevel() - (s == 10 ? 1 : 10));
                    mover = meu - (s == 12 ? 0 : pontosAte(alvo));
                    if (mover <= 0) {
                        p.sendActionBar(Masmorras.txt("§cVocê não tem XP para guardar."));
                        return;
                    }
                    definir(p, meu - mover);
                    por(p, banco + mover);
                    p.playSound(p.getLocation(), "minecraft:block.amethyst_block.chime", 1f, 1.2f);
                    p.sendActionBar(Masmorras.txt("§aGuardou " + fmt(mover) + " XP. §7Saldo: §a" + fmt(banco + mover)));
                }
                case 14, 15, 16 -> {
                    if (banco <= 0) {
                        p.sendActionBar(Masmorras.txt("§cSeu banco está vazio."));
                        return;
                    }
                    mover = s == 16 ? banco : Math.min(banco, pontosAte(p.getLevel() + (s == 14 ? 1 : 10)) - meu);
                    if (mover <= 0) return;
                    por(p, banco - mover);
                    definir(p, meu + mover);
                    p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.7f, 1.2f);
                    p.sendActionBar(Masmorras.txt("§eSacou " + fmt(mover) + " XP. §7Saldo: §a" + fmt(banco - mover)));
                }
                case 22 -> {
                    abrirLoja(p);
                    return;
                }
                default -> {
                    return;
                }
            }
            pl.getLogger().info("Banco de XP: " + p.getName() + (s < 13 ? " guardou " : " sacou ") + mover + " (saldo " + saldo(p) + ")");
            abrirBanco(p);
            return;
        }
        if (s == 31) {
            abrirBanco(p);
            return;
        }
        int i = -1;
        for (int k = 0; k < LOJA.length; k++) if (SLOTS_LOJA[k] == s) i = k;
        if (i < 0) return;
        comprar(p, LOJA[i]);
    }

    private void comprar(Player p, Oferta o) {
        if (o.tipo().equals("vip")) {
            Integer nv = p.getPersistentDataContainer().get(VIP_NIVEL, PersistentDataType.INTEGER);
            if (nv != null && nv > 1) {
                p.sendMessage(Masmorras.txt("§6§lLoja de XP: §fVocê já tem um VIP maior. O VIP da loja é só para quem não tem VIP ou tem o VIP comum."));
                return;
            }
            if (Bukkit.getPluginManager().getPlugin("Extras") == null) {
                p.sendMessage(Masmorras.txt("§cO VIP está fora do ar agora. Tente mais tarde."));
                return;
            }
        }
        if ((o.tipo().equals("item") || o.tipo().equals("anel")) && p.getInventory().firstEmpty() < 0) {
            p.sendActionBar(Masmorras.txt("§cSua mochila está cheia."));
            return;
        }
        if (!gastar(p, o.preco())) {
            p.sendMessage(Masmorras.txt("§6§lLoja de XP: §fFaltam §c" + fmt(o.preco() - saldo(p)) + " XP§f no banco."));
            p.playSound(p.getLocation(), "minecraft:entity.villager.no", 1f, 1f);
            return;
        }
        switch (o.tipo()) {
            case "vip" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vip dar " + p.getName() + " vip " + o.valor() + " semextra");
            case "coronas" -> pl.coronas.dar(p, o.valor(), true);
            case "anel" -> p.getInventory().addItem(pl.aneis.anel(Aneis.Elem.values()[o.valor()]));
            default -> p.getInventory().addItem(new ItemStack(o.icone(), o.valor()));
        }
        p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 1f, 1f);
        p.sendMessage(Masmorras.txt("§6§lLoja de XP: §fComprou " + o.nome() + "§f por §a" + fmt(o.preco()) + " XP§f. Saldo: §a" + fmt(saldo(p))));
        pl.getLogger().info("Loja de XP: " + p.getName() + " comprou " + o.tipo() + " " + o.valor() + " por " + o.preco() + " XP");
        abrirLoja(p);
    }

    // 18/11 (Paulo): o jogador ganha so 40% do XP normal, venha de onde vier (monstro, minerio, fornalha, pesca...)
    private final java.util.Map<java.util.UUID, Double> sobraXp = new java.util.HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void menosXp(org.bukkit.event.player.PlayerExpChangeEvent e) {
        int a = e.getAmount();
        if (a <= 0) return;
        double f = pl.getConfig().getDouble("xp.multiplicador", 0.40);
        double v = a * f + sobraXp.getOrDefault(e.getPlayer().getUniqueId(), 0.0);
        int inteiro = (int) Math.floor(v);
        sobraXp.put(e.getPlayer().getUniqueId(), v - inteiro);
        e.setAmount(inteiro);
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ NPCs e enfeites no lobby

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEntityEvent e) {
        var d = e.getRightClicked().getPersistentDataContainer();
        boolean banco = d.has(kBanco), enc = d.has(kEncantador), runas = d.has(kRunas);
        if (!banco && !enc && !runas) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (runas) e.getPlayer().performCommand("runa");
        else if (banco) abrirBanco(e.getPlayer());
        else pl.encantos.abrir(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void naoApanha(org.bukkit.event.entity.EntityDamageEvent e) {
        var d = e.getEntity().getPersistentDataContainer();
        if (d.has(kBanco) || d.has(kEncantador) || d.has(kRunas)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoVira(org.bukkit.event.entity.EntityTransformEvent e) {
        var d = e.getEntity().getPersistentDataContainer();
        if (d.has(kBanco) || d.has(kEncantador)) e.setCancelled(true);
    }

    private void garantir() {
        if (mundo == null) return;
        npc(BANCO, kBanco, "§2§lBanqueiro de XP", "librarian");
        npc(ENCANTADOR, kEncantador, "§5§lEncantador", "cleric");
        npc(RUNAS, kRunas, "§d§lMestre das Runas", "cartographer");
    }

    private void npc(double[] c, NamespacedKey k, String nome, String prof) {
        if (!LobbyMundo.pronto(mundo, (int) Math.floor(c[0]), (int) Math.floor(c[1]))) return;
        Location l = new Location(mundo, c[0], y + 1, c[1], (float) Math.toDegrees(Math.atan2(c[0], -c[1])), 0f);
        for (Entity e : mundo.getNearbyEntities(l, 4, 3, 4)) if (e.getPersistentDataContainer().has(k)) return;
        Villager v = pl.loja.npc(l, nome, null, Loja.profissao(0), k, 1);
        Villager.Profession pr = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(prof));
        if (pr != null) v.setProfession(pr);
    }

    private void bloco(int x, int dy, int z, String b) {
        mundo.getBlockAt(x, y + dy, z).setBlockData(Bukkit.createBlockData("minecraft:" + b), false);
    }

    private void construir() {
        // Banco de XP: estrado verde, balcao com baus e garrafas, postes de lanterna verde
        int bx = 26, bz = -11;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) bloco(bx + dx, 0, bz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "polished_blackstone_bricks" : "emerald_block");
        bloco(bx, 0, bz, "polished_blackstone_bricks");
        for (int dz = -1; dz <= 1; dz++) bloco(bx + 2, 1, bz + dz, "polished_blackstone_brick_wall");
        bloco(bx + 2, 2, bz, "lime_stained_glass");
        bloco(bx + 2, 1, bz - 2, "barrel[facing=up]");
        bloco(bx + 2, 1, bz + 2, "barrel[facing=up]");
        for (int[] q : new int[][] {{-2, -2}, {-2, 2}}) {
            bloco(bx + q[0], 1, bz + q[1], "polished_blackstone_brick_wall");
            bloco(bx + q[0], 2, bz + q[1], "polished_blackstone_brick_wall");
            bloco(bx + q[0], 3, bz + q[1], "sea_lantern");
        }
        // Encantador: estrado de ametista, estantes nos cantos, mesa de encantamento atras dele, velas
        int ex = -26, ez = -11;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) bloco(ex + dx, 0, ez + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "polished_deepslate" : "amethyst_block");
        bloco(ex, 0, ez, "polished_deepslate");
        for (int[] q : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            bloco(ex + q[0], 1, ez + q[1], "bookshelf");
            bloco(ex + q[0], 2, ez + q[1], "bookshelf");
            bloco(ex + q[0], 3, ez + q[1], q[0] < 0 ? "purple_candle[candles=3,lit=true]" : "amethyst_cluster");
        }
        bloco(ex - 2, 1, ez, "enchanting_table");
        bloco(ex - 2, 1, ez - 1, "bookshelf");
        bloco(ex - 2, 1, ez + 1, "bookshelf");
        texto(new Location(mundo, BANCO[0], y + 4.4, BANCO[1]), "§2§lBANCO DE XP\n§7Guarde e saque XP • veja o saldo\n§6Loja de XP: §fVIP, coronas e itens");
        texto(new Location(mundo, ENCANTADOR[0], y + 4.4, ENCANTADOR[1]), "§5§lENCANTADOR\n§7Livros de encantos especiais pagos com XP\n§8clique com o livro em cima do item para encantar");
        pl.getLogger().info("Lobby v6: Banco de XP e Encantador.");
    }

    private void texto(Location l, String t) {
        mundo.spawn(l, TextDisplay.class, x -> {
            x.text(Masmorras.txt(t));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
            x.setPersistent(true);
            x.setLineWidth(260);
            x.getPersistentDataContainer().set(new NamespacedKey(pl, "lobby_enfeite"), PersistentDataType.INTEGER, 2);
        });
    }
}
