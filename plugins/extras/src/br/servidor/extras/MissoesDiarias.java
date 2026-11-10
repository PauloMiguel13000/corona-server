package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Missoes diarias (10/11): todo dia cada jogador recebe 3 missoes simples (as mesmas para todo mundo naquele dia),
 * cada uma paga coronas; fechar as 3 da um bonus, que cresce com os dias seguidos (ate 7). /missoes abre o menu
 * (bau, funciona no Java e no Bedrock). O dia vira a meia-noite de Brasilia. Tudo fica em missoes-diarias.yml.
 */
final class MissoesDiarias implements Listener {

    enum Tipo { MONSTROS, MONSTROS_MASMORRA, MINERIOS, PESCAR, COLHER, COMER, ENCANTAR, ANDAR, TEMPO, MINIGAMES }

    /** id fixo (vai para o arquivo), tipo, meta, coronas, icone, texto (com %n = a meta). */
    record Missao(String id, Tipo tipo, int meta, int coronas, Material icone, String texto) {}

    static final Missao[] TODAS = {
            new Missao("monstros20", Tipo.MONSTROS, 20, 40, Material.IRON_SWORD, "Derrote %n monstros"),
            new Missao("monstros40", Tipo.MONSTROS, 40, 70, Material.DIAMOND_SWORD, "Derrote %n monstros"),
            new Missao("masmorra15", Tipo.MONSTROS_MASMORRA, 15, 60, Material.WITHER_SKELETON_SKULL, "Derrote %n monstros dentro das masmorras"),
            new Missao("minerios15", Tipo.MINERIOS, 15, 40, Material.IRON_PICKAXE, "Minere %n minérios"),
            new Missao("minerios30", Tipo.MINERIOS, 30, 70, Material.DIAMOND_PICKAXE, "Minere %n minérios"),
            new Missao("pescar5", Tipo.PESCAR, 5, 40, Material.FISHING_ROD, "Pesque %n vezes"),
            new Missao("colher25", Tipo.COLHER, 25, 40, Material.WHEAT, "Colha %n plantações maduras"),
            new Missao("comer5", Tipo.COMER, 5, 25, Material.COOKED_BEEF, "Coma ou beba %n vezes"),
            new Missao("encantar1", Tipo.ENCANTAR, 1, 50, Material.ENCHANTING_TABLE, "Encante %n item na mesa de encantamentos"),
            new Missao("andar1500", Tipo.ANDAR, 1500, 30, Material.LEATHER_BOOTS, "Ande %n blocos"),
            new Missao("tempo30", Tipo.TEMPO, 30, 40, Material.CLOCK, "Jogue por %n minutos"),
            new Missao("minigames2", Tipo.MINIGAMES, 2, 50, Material.ENDER_EYE, "Entre em %n minigames diferentes"),
    };

    /** Mundos que contam como minigame (entrar no mundo = entrou no jogo). */
    private static final Set<String> MINIGAMES = Set.of("turno_noite", "hotel", "fabrica", "backrooms", "naochore", "slender", "vovo", "defesa", "arena");

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final int[] SLOTS = {11, 13, 15};

    private final Extras pl;
    private final File arq;
    private YamlConfiguration dados;
    private boolean sujo;

    MissoesDiarias(Extras pl) {
        this.pl = pl;
        this.arq = new File(pl.getDataFolder(), "missoes-diarias.yml");
        this.dados = YamlConfiguration.loadConfiguration(arq);
    }

    void iniciar() {
        if (!pl.getConfig().getBoolean("missoes-diarias.ativo", true)) return;
        Bukkit.getPluginManager().registerEvents(this, pl);
        // a cada minuto: missao de tempo, missao de andar, e grava o arquivo se mudou
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                somar(p, Tipo.TEMPO, 1);
                andar(p);
            }
            salvar();
        }, 1200L, 1200L);
    }

    void parar() {
        salvar();
    }

    private void salvar() {
        if (!sujo) return;
        sujo = false;
        try {
            dados.save(arq);
        } catch (IOException e) {
            pl.getLogger().warning("Missoes diarias: " + e);
        }
    }

    // ------------------------------------------------------------------ o dia e as 3 missoes

    static String hoje() {
        return LocalDate.now(ZONA).toString();
    }

    /** As 3 missoes do dia (iguais para todos): sorteio fixo pela data, sem repetir o tipo. */
    static List<Missao> doDia(String dia) {
        Random r = new Random(dia.hashCode() * 31L + 7);
        List<Missao> pool = new ArrayList<>(List.of(TODAS));
        List<Missao> l = new ArrayList<>();
        while (l.size() < 3 && !pool.isEmpty()) {
            Missao m = pool.remove(r.nextInt(pool.size()));
            boolean repetido = false;
            for (Missao x : l) if (x.tipo() == m.tipo() || (x.tipo().name().startsWith("MONSTROS") && m.tipo().name().startsWith("MONSTROS"))) repetido = true;
            if (!repetido) l.add(m);
        }
        return l;
    }

    /** A ficha do jogador para hoje (zera quando o dia muda; a sequencia cai se ele pulou um dia). */
    private ConfigurationSection ficha(Player p) {
        String k = p.getUniqueId().toString();
        ConfigurationSection c = dados.getConfigurationSection(k);
        if (c == null) c = dados.createSection(k);
        String dia = hoje();
        if (!dia.equals(c.getString("dia"))) {
            String ultimoCompleto = c.getString("ultimo-completo", "");
            if (!ultimoCompleto.equals(LocalDate.now(ZONA).minusDays(1).toString())) c.set("sequencia", 0);
            c.set("dia", dia);
            c.set("nome", p.getName());
            c.set("progresso", null);
            c.set("pago", null);
            c.set("bonus-pago", false);
            c.set("mundos", null);
            c.set("andou-base", andado(p));
            sujo = true;
        }
        return c;
    }

    private static int andado(Player p) {
        try {
            return (p.getStatistic(Statistic.WALK_ONE_CM) + p.getStatistic(Statistic.SPRINT_ONE_CM)) / 100;
        } catch (Throwable e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ progresso

    private void somar(Player p, Tipo tipo, int quanto) {
        if (quanto <= 0) return;
        List<Missao> dia = doDia(hoje());
        Missao m = null;
        for (Missao x : dia) if (x.tipo() == tipo) m = x;
        if (m == null) return;
        ConfigurationSection c = ficha(p);
        int antes = c.getInt("progresso." + m.id(), 0);
        if (antes >= m.meta()) return;
        definir(p, c, m, antes + quanto);
    }

    private void definir(Player p, ConfigurationSection c, Missao m, int valor) {
        int antes = c.getInt("progresso." + m.id(), 0);
        int agora = Math.min(m.meta(), valor);
        if (agora <= antes) return;
        c.set("progresso." + m.id(), agora);
        sujo = true;
        if (agora >= m.meta()) {
            p.sendMessage(Extras.txt("§a§l✔ Missão do dia concluída: §f" + m.texto().replace("%n", String.valueOf(m.meta())) + "§a. §7Pegue o prêmio: §e/missoes"));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        } else if (m.meta() >= 4 && antes * 2 < m.meta() && agora * 2 >= m.meta()) {
            p.sendActionBar(Extras.txt("§eMissão do dia: §f" + m.texto().replace("%n", String.valueOf(m.meta())) + " §7(" + agora + "/" + m.meta() + ")"));
        }
    }

    private void andar(Player p) {
        Missao m = null;
        for (Missao x : doDia(hoje())) if (x.tipo() == Tipo.ANDAR) m = x;
        if (m == null) return;
        ConfigurationSection c = ficha(p);
        definir(p, c, m, andado(p) - c.getInt("andou-base", andado(p)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void matou(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || !(e.getEntity() instanceof Monster)) return;
        somar(p, Tipo.MONSTROS, 1);
        if (p.getWorld().getName().startsWith("masmorras")) somar(p, Tipo.MONSTROS_MASMORRA, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void quebrou(BlockBreakEvent e) {
        Block b = e.getBlock();
        String n = b.getType().name();
        if (n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS")) {
            // minerio que o proprio jogador colocou nao conta (toque de seda e recolocar)
            if (!e.getPlayer().getInventory().getItemInMainHand().containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) somar(e.getPlayer(), Tipo.MINERIOS, 1);
        } else if (b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()
                && (n.equals("WHEAT") || n.equals("CARROTS") || n.equals("POTATOES") || n.equals("BEETROOTS") || n.equals("NETHER_WART") || n.equals("COCOA"))) {
            somar(e.getPlayer(), Tipo.COLHER, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pescou(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) somar(e.getPlayer(), Tipo.PESCAR, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void comeu(PlayerItemConsumeEvent e) {
        somar(e.getPlayer(), Tipo.COMER, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void encantou(EnchantItemEvent e) {
        somar(e.getEnchanter(), Tipo.ENCANTAR, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void mudouMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        String w = p.getWorld().getName();
        if (!MINIGAMES.contains(w)) return;
        Missao m = null;
        for (Missao x : doDia(hoje())) if (x.tipo() == Tipo.MINIGAMES) m = x;
        if (m == null) return;
        ConfigurationSection c = ficha(p);
        List<String> mundos = new ArrayList<>(c.getStringList("mundos"));
        if (mundos.contains(w)) return;
        mundos.add(w);
        c.set("mundos", mundos);
        sujo = true;
        definir(p, c, m, mundos.size());
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            ConfigurationSection c = ficha(p);
            int falta = 0;
            for (Missao m : doDia(hoje())) if (!c.getBoolean("pago." + m.id(), false)) falta++;
            if (falta > 0) p.sendMessage(Extras.txt("§6§l✦ §fVocê tem §e" + falta + " missões do dia §fvalendo coronas. Veja: §e/missoes"));
        }, 160L);
    }

    // ------------------------------------------------------------------ menu

    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private int bonus(int sequencia) {
        int base = pl.getConfig().getInt("missoes-diarias.bonus-das-3", 60);
        int porDia = pl.getConfig().getInt("missoes-diarias.bonus-por-dia-seguido", 15);
        return base + porDia * Math.min(6, Math.max(0, sequencia));
    }

    void abrir(Player p) {
        if (!pl.getConfig().getBoolean("missoes-diarias.ativo", true)) {
            p.sendMessage(Extras.txt("§7As missões do dia estão desligadas."));
            return;
        }
        andar(p);
        ConfigurationSection c = ficha(p);
        List<Missao> dia = doDia(hoje());
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, Extras.txt("§8Missões do dia"));
        h.inv = inv;
        int feitas = 0, pagas = 0;
        for (int i = 0; i < dia.size(); i++) {
            Missao m = dia.get(i);
            int prog = Math.min(m.meta(), c.getInt("progresso." + m.id(), 0));
            boolean feita = prog >= m.meta(), paga = c.getBoolean("pago." + m.id(), false);
            if (feita) feitas++;
            if (paga) pagas++;
            ItemStack it = new ItemStack(paga ? Material.LIME_DYE : m.icone());
            ItemMeta im = it.getItemMeta();
            im.displayName(Extras.txt((paga ? "§a✔ " : feita ? "§e§l" : "§f") + m.texto().replace("%n", String.valueOf(m.meta()))));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Extras.txt("§7Progresso: " + barra(prog, m.meta()) + " §f" + prog + "§7/§f" + m.meta()));
            lore.add(Extras.txt("§7Prêmio: §6" + m.coronas() + " coronas"));
            lore.add(Extras.txt(""));
            lore.add(Extras.txt(paga ? "§aPrêmio já recebido" : feita ? "§e§lClique para pegar o prêmio!" : "§8Ainda não terminou"));
            im.lore(lore);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (feita && !paga) im.setEnchantmentGlintOverride(true);
            it.setItemMeta(im);
            inv.setItem(SLOTS[i], it);
        }
        int seq = c.getInt("sequencia", 0);
        boolean bonusPago = c.getBoolean("bonus-pago", false);
        boolean podeBonus = pagas == dia.size() && !bonusPago;
        ItemStack b = new ItemStack(bonusPago ? Material.LIME_DYE : podeBonus ? Material.CHEST : Material.ENDER_CHEST);
        ItemMeta bm = b.getItemMeta();
        bm.displayName(Extras.txt(bonusPago ? "§a✔ Bônus do dia recebido" : "§6§lBônus por fechar as 3"));
        List<net.kyori.adventure.text.Component> bl = new ArrayList<>();
        bl.add(Extras.txt("§7Prêmio: §6" + bonus(bonusPago ? Math.max(0, seq - 1) : seq) + " coronas"));
        bl.add(Extras.txt("§7Dias seguidos: §f" + seq + " §8(cada dia seguido aumenta o bônus, até 7)"));
        bl.add(Extras.txt(""));
        bl.add(Extras.txt(bonusPago ? "§7Volte amanhã para manter a sequência!" : podeBonus ? "§e§lClique para pegar o bônus!" : "§8Pegue o prêmio das 3 missões primeiro"));
        bm.lore(bl);
        if (podeBonus) bm.setEnchantmentGlintOverride(true);
        b.setItemMeta(bm);
        inv.setItem(22, b);
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta fm = info.getItemMeta();
        fm.displayName(Extras.txt("§fComo funciona"));
        fm.lore(List.of(Extras.txt("§7Todo dia tem 3 missões novas."), Extras.txt("§7Cada uma paga coronas na hora."), Extras.txt("§7Fechou as 3? Tem bônus."),
                Extras.txt("§7As missões trocam à meia-noite.")));
        info.setItemMeta(fm);
        inv.setItem(4, info);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    /** Admin (teste): termina as 3 missoes de hoje do jogador, sem pagar (ele pega o premio no menu). */
    void completar(Player p) {
        ConfigurationSection c = ficha(p);
        for (Missao m : doDia(hoje())) definir(p, c, m, m.meta());
        salvar();
    }

    private static String barra(int v, int max) {
        int cheio = max <= 0 ? 10 : Math.min(10, v * 10 / max);
        return "§a" + "▮".repeat(cheio) + "§8" + "▮".repeat(10 - cheio);
    }

    @EventHandler
    public void clicou(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        ConfigurationSection c = ficha(p);
        List<Missao> dia = doDia(hoje());
        int slot = e.getSlot();
        for (int i = 0; i < dia.size(); i++) {
            if (slot != SLOTS[i]) continue;
            Missao m = dia.get(i);
            if (c.getBoolean("pago." + m.id(), false) || c.getInt("progresso." + m.id(), 0) < m.meta()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                return;
            }
            c.set("pago." + m.id(), true);
            sujo = true;
            salvar();
            pagar(p, m.coronas());
            p.sendMessage(Extras.txt("§6+" + m.coronas() + " coronas §7pela missão do dia."));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            abrir(p);
            return;
        }
        if (slot == 22) {
            if (c.getBoolean("bonus-pago", false)) return;
            for (Missao m : dia) if (!c.getBoolean("pago." + m.id(), false)) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                return;
            }
            int seq = c.getInt("sequencia", 0);
            int v = bonus(seq);
            c.set("bonus-pago", true);
            c.set("sequencia", seq + 1);
            c.set("ultimo-completo", hoje());
            sujo = true;
            salvar();
            pagar(p, v);
            p.sendMessage(Extras.txt("§6§l✦ §fVocê fechou as 3 missões do dia: §6+" + v + " coronas§f! §7Dias seguidos: §f" + (seq + 1)));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            pl.getLogger().info("Missoes diarias: " + p.getName() + " fechou as 3 (sequencia " + (seq + 1) + ", bonus " + v + ")");
            abrir(p);
        }
    }

    private void pagar(Player p, int v) {
        if (v <= 0) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pl.getConfig().getString("missoes-diarias.comando", "coronas dar %jogador% %valor%")
                .replace("%jogador%", p.getName()).replace("%valor%", String.valueOf(v)));
    }
}
