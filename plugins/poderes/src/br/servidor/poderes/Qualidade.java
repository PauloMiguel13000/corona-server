package br.servidor.poderes;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Estrelas de qualidade dos pets (16/11, ideia do Discord: "pets vem com level 1 a 5 tipo IV de Pokemon").
 * Cada pet do BabyPets nasce com 1 a 5 estrelas (sorteio). Mais estrelas = mais dano, poder do pet volta mais rapido
 * e o bonus do pet no jogador (vida, velocidade...) fica maior. Com 1 estrela o pet fica igual era antes (ninguem perde).
 * Pet repetido serve: funde no pet escolhido e ele ganha +1 estrela (ou fica com as do repetido, se forem mais)
 * e o maior nivel dos dois. Guarda em plugins/Poderes/qualidade.yml (id do pet no banco do BabyPets -> estrelas).
 */
final class Qualidade implements Listener {

    static final int[] PESOS = {30, 30, 22, 13, 5};
    static final double[] DANO = {1.0, 1.08, 1.16, 1.27, 1.4};
    static final double[] PASSIVO = {0, 0.10, 0.20, 0.35, 0.50};
    static final double[] RECARGA = {1.0, 1.0, 0.95, 0.9, 0.8};

    private final Poderes pl;
    private final File arq;
    private YamlConfiguration y;
    private long inicio;
    private boolean mudou;
    private final Random rnd = new Random();
    private final NamespacedKey chaveMod;
    private final NamespacedKey chaveAviso;
    private final Map<UUID, Integer> ultimoAtivo = new HashMap<>();

    Qualidade(Poderes pl) {
        this.pl = pl;
        this.arq = new File(pl.getDataFolder(), "qualidade.yml");
        this.chaveMod = new NamespacedKey(pl, "pet_qualidade");
        this.chaveAviso = new NamespacedKey(pl, "pet_estrelas_aviso");
        y = YamlConfiguration.loadConfiguration(arq);
        inicio = y.getLong("inicio", 0L);
        if (inicio <= 0) {
            inicio = System.currentTimeMillis();
            y.set("inicio", inicio);
            mudou = true;
            salvar();
        }
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::sortearNovos, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(pl, this::sincronizar, 40L, 20L);
    }

    void desligar() {
        for (Player p : Bukkit.getOnlinePlayers()) tirarBonus(p);
        salvar();
    }

    // ------------------------------------------------------------------ dados

    /** Estrelas do pet (0 = ainda nao sorteou). */
    int de(int id) {
        return id <= 0 ? 0 : Math.max(0, Math.min(5, y.getInt("pets." + id, 0)));
    }

    private void definir(int id, int q) {
        y.set("pets." + id, q <= 0 ? null : Math.min(5, q));
        mudou = true;
    }

    private void salvar() {
        if (!mudou) return;
        try {
            y.save(arq);
            mudou = false;
        } catch (Exception e) {
            pl.getLogger().warning("Nao consegui salvar qualidade.yml: " + e.getMessage());
        }
    }

    double fatorDano(int id) {
        int q = de(id);
        return q <= 0 ? 1.0 : DANO[q - 1];
    }

    double fatorRecarga(int id) {
        int q = de(id);
        return q <= 0 ? 1.0 : RECARGA[q - 1];
    }

    int sortear() {
        int t = 0;
        for (int w : PESOS) t += w;
        int r = rnd.nextInt(t);
        for (int i = 0; i < PESOS.length; i++) {
            r -= PESOS[i];
            if (r < 0) return i + 1;
        }
        return 1;
    }

    static String estrelas(int q) {
        q = Math.max(0, Math.min(5, q));
        return "§6" + "★".repeat(q) + "§8" + "★".repeat(5 - q);
    }

    static String efeito(int q) {
        if (q <= 1) return "§7igual ao normal";
        return "§a+" + Math.round((DANO[q - 1] - 1) * 100) + "% dano§7, §a+" + Math.round(PASSIVO[q - 1] * 100) + "% bônus"
                + (RECARGA[q - 1] < 1 ? "§7, §apoder " + Math.round((1 - RECARGA[q - 1]) * 100) + "% mais rápido" : "");
    }

    // ------------------------------------------------------------------ sorteio dos pets novos

    private void sortearNovos() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<PetsPonte.PetDado> l = pl.pets.colecao(p);
            int antigos = 0;
            for (PetsPonte.PetDado d : l) {
                if (de(d.id()) > 0) continue;
                int q = sortear();
                definir(d.id(), q);
                if (d.obtido() > 0 && d.obtido() < inicio) {
                    antigos++;
                    continue;
                }
                p.sendMessage(Poderes.txt("§d§l✦ §fSeu pet novo §e" + d.nome() + " §fnasceu com " + estrelas(q) + " §7(" + q + "/5)"));
                p.sendMessage(Poderes.txt("§7  " + efeito(q) + ". §7Pet repetido? Funda e ganhe estrela: §e/petpoder estrelas"));
                p.playSound(p.getLocation(), q >= 4 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.7f, q >= 4 ? 1f : 1.6f);
                if (q == 5) Bukkit.broadcast(Poderes.txt("§6§l★ " + p.getName() + " §echocou um pet §6§lPERFEITO §6(5 estrelas)§e: §f" + d.nome() + "§e!"));
            }
            if (antigos > 0 && !p.getPersistentDataContainer().has(chaveAviso)) {
                p.getPersistentDataContainer().set(chaveAviso, PersistentDataType.BYTE, (byte) 1);
                p.sendMessage(Poderes.txt("§d§l✦ NOVO: §fos pets agora têm §6estrelas de qualidade §f(1 a 5)!"));
                p.sendMessage(Poderes.txt("§7Mais estrelas = mais dano, poder mais rápido e bônus maior. Veja os seus: §e/petpoder estrelas"));
                p.sendMessage(Poderes.txt("§7Pet repetido agora serve: §efunda §7no seu pet e ele ganha §6+1 estrela§7."));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            }
        }
        salvar();
    }

    // ------------------------------------------------------------------ bonus no jogador (vida, velocidade... do pet)

    private void sincronizar() {
        NamespacedKey bpChave = pl.pets.chaveAtributo();
        if (bpChave == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            int id = pl.pets.idAtivo(p);
            int q = de(id);
            for (Attribute a : pl.pets.atributosDosPets()) {
                AttributeInstance ai = p.getAttribute(a);
                if (ai == null) continue;
                AttributeModifier base = null, nosso = null;
                for (AttributeModifier m : ai.getModifiers()) {
                    if (bpChave.equals(m.getKey())) base = m;
                    else if (chaveMod.equals(m.getKey())) nosso = m;
                }
                double alvo = base != null && q >= 2 ? base.getAmount() * PASSIVO[q - 1] : 0;
                if (alvo == 0) {
                    if (nosso != null) ai.removeModifier(nosso);
                } else if (nosso == null || Math.abs(nosso.getAmount() - alvo) > 1e-9 || nosso.getOperation() != base.getOperation()) {
                    if (nosso != null) ai.removeModifier(nosso);
                    ai.addModifier(new AttributeModifier(chaveMod, alvo, base.getOperation()));
                }
            }
            Integer antes = ultimoAtivo.put(p.getUniqueId(), id);
            if (id > 0 && q > 0 && (antes == null || antes != id) && antes != null)
                Poderes.barra(p, "§dQualidade do pet: " + estrelas(q) + " §7(" + q + "/5)");
        }
    }

    private void tirarBonus(Player p) {
        for (Attribute a : pl.pets.atributosDosPets()) {
            AttributeInstance ai = p.getAttribute(a);
            if (ai == null) continue;
            for (AttributeModifier m : new ArrayList<>(ai.getModifiers()))
                if (chaveMod.equals(m.getKey())) ai.removeModifier(m);
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        ultimoAtivo.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ menus

    static final class MenuPets implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Integer> slotId = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class MenuFundir implements InventoryHolder {
        Inventory inv;
        int alvo, repetido;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static PetsPonte.PetDado escolhido(List<PetsPonte.PetDado> l) {
        for (PetsPonte.PetDado d : l) if (d.selecionado()) return d;
        return null;
    }

    /** Estrelas depois da fusao: +1 no escolhido, ou as do repetido se forem mais. */
    static int fundida(int qAlvo, int qRep) {
        return Math.min(5, Math.max(qAlvo + 1, qRep));
    }

    void abrir(Player p) {
        List<PetsPonte.PetDado> l = new ArrayList<>(pl.pets.colecao(p));
        PetsPonte.PetDado esc = escolhido(l);
        String tipoEsc = esc == null ? null : esc.tipo();
        l.sort(Comparator.<PetsPonte.PetDado>comparingInt(d -> d.selecionado() ? 0 : d.tipo().equals(tipoEsc) ? 1 : 2)
                .thenComparingInt(d -> -de(d.id())).thenComparingInt(d -> -d.nivel()));
        MenuPets h = new MenuPets();
        Inventory inv = Bukkit.createInventory(h, 54, Poderes.txt("§5§lSeus pets §8- estrelas"));
        h.inv = inv;
        ItemStack vidro = item(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 9; i++) inv.setItem(i, vidro);
        List<String> como = new ArrayList<>(List.of(
                "§7Todo pet nasce com §61 a 5 estrelas§7.",
                "§7Mais estrelas = mais dano, poder mais",
                "§7rápido e bônus maior (vida, velocidade...).",
                "",
                "§f" + estrelas(1) + " §7igual ao normal",
                "§f" + estrelas(2) + " §a+8% dano, +10% bônus",
                "§f" + estrelas(3) + " §a+16% dano, +20% bônus",
                "§f" + estrelas(4) + " §a+27% dano, +35% bônus",
                "§f" + estrelas(5) + " §a+40% dano, +50% bônus",
                "",
                "§eFUSÃO: §7pet repetido (mesmo bicho) funde",
                "§7no seu pet escolhido: ele ganha §6+1 estrela",
                "§7(ou as do repetido, se forem mais) e fica",
                "§7com o maior nível dos dois."));
        if (esc == null) {
            como.add("");
            como.add("§cEscolha um pet em §e/pets §cpara receber as fusões.");
        }
        inv.setItem(4, item(Material.NETHER_STAR, "§e§lEstrelas dos pets", como, false));
        int slot = 9;
        for (PetsPonte.PetDado d : l) {
            if (slot >= 54) break;
            int q = de(d.id());
            List<String> lore = new ArrayList<>();
            lore.add("§7Qualidade: " + estrelas(q) + " §7(" + q + "/5)");
            lore.add("§7" + efeito(q));
            lore.add("§7Nível: §f" + d.nivel() + "  §7Raridade: §f" + d.raridade());
            lore.add("");
            if (d.selecionado()) lore.add("§a§l✔ ESCOLHIDO §7(recebe as fusões)");
            else if (esc != null && d.tipo().equals(esc.tipo())) {
                int nq = fundida(de(esc.id()), q);
                lore.add("§eClique para FUNDIR no escolhido.");
                lore.add("§7O escolhido fica: " + estrelas(nq) + " §7nível §f" + Math.max(esc.nivel(), d.nivel()));
                lore.add("§cEste aqui some para sempre.");
            } else if (esc != null) lore.add("§8Só funde com outro pet igual a este.");
            ItemStack it = item(d.icone(), (d.selecionado() ? "§a§l" : "§f") + d.nome() + " " + estrelas(q), lore, d.selecionado());
            inv.setItem(slot, it);
            h.slotId.put(slot, d.id());
            slot++;
        }
        if (l.isEmpty()) inv.setItem(31, item(Material.BARRIER, "§cVocê ainda não tem pets", List.of("§7Ache ovos de pet em baús e masmorras", "§7e choque numa incubadora (§e/pets§7)."), false));
        p.openInventory(inv);
    }

    private void abrirFundir(Player p, PetsPonte.PetDado esc, PetsPonte.PetDado rep) {
        MenuFundir h = new MenuFundir();
        h.alvo = esc.id();
        h.repetido = rep.id();
        Inventory inv = Bukkit.createInventory(h, 27, Poderes.txt("§5§lFundir pets?"));
        h.inv = inv;
        ItemStack vidro = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        int qa = de(esc.id()), qr = de(rep.id()), nq = fundida(qa, qr);
        int nv = Math.max(esc.nivel(), rep.nivel());
        inv.setItem(10, item(esc.icone(), "§a" + esc.nome() + " " + estrelas(qa), List.of("§7Seu pet escolhido (fica).", "§7Nível §f" + esc.nivel()), true));
        inv.setItem(12, item(rep.icone(), "§c" + rep.nome() + " " + estrelas(qr), List.of("§7O repetido (some para sempre).", "§7Nível §f" + rep.nivel()), false));
        inv.setItem(14, item(Material.LIME_CONCRETE, "§a§lFUNDIR", List.of("§7Resultado: " + estrelas(nq) + " §7(" + nq + "/5)", "§7Nível: §f" + nv, "§7" + efeito(nq), "", "§cO repetido some para sempre!"), false));
        inv.setItem(16, item(Material.RED_CONCRETE, "§c§lCancelar", List.of("§7Voltar sem fundir."), false));
        p.openInventory(inv);
    }

    private void fundir(Player p, int alvoId, int repId) {
        List<PetsPonte.PetDado> l = pl.pets.colecao(p);
        PetsPonte.PetDado esc = null, rep = null;
        for (PetsPonte.PetDado d : l) {
            if (d.id() == alvoId) esc = d;
            if (d.id() == repId) rep = d;
        }
        if (esc == null || rep == null || !esc.selecionado() || rep.selecionado() || !esc.tipo().equals(rep.tipo()) || alvoId == repId) {
            p.sendMessage(Poderes.txt("§cNão deu para fundir: a sua coleção mudou. Abra de novo: §e/petpoder estrelas"));
            p.closeInventory();
            return;
        }
        int qa = de(esc.id()), qr = de(rep.id()), nq = fundida(qa, qr);
        int nv = Math.max(esc.nivel(), rep.nivel());
        if (nq == qa && nv <= esc.nivel()) {
            p.sendMessage(Poderes.txt("§eSeu pet já tem 5 estrelas e nível igual ou maior: a fusão não daria nada."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        if (!pl.pets.apagar(p, rep.inst())) {
            p.sendMessage(Poderes.txt("§cO plugin de pets não respondeu. Nada foi apagado."));
            return;
        }
        definir(rep.id(), 0);
        definir(esc.id(), nq);
        salvar();
        if (nv > esc.nivel()) pl.pets.mudarNivel(p, esc.inst(), nv);
        pl.getLogger().info("Fusao de pets: " + p.getName() + " fundiu #" + rep.id() + " (" + rep.tipo() + " " + qr + "*, nv " + rep.nivel() + ") em #"
                + esc.id() + " (" + qa + "* -> " + nq + "*, nv " + esc.nivel() + " -> " + nv + ")");
        p.sendMessage(Poderes.txt("§d§l✦ FUSÃO! §f" + esc.nome() + " §fagora tem " + estrelas(nq) + " §7(" + nq + "/5)" + (nv > esc.nivel() ? " §fe nível §e" + nv : "")));
        p.sendMessage(Poderes.txt("§7  " + efeito(nq)));
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        p.playSound(p.getLocation(), nq == 5 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        Fx.p(p.getWorld(), Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.3);
        PetsPonte.PetInfo ativo = pl.pets.petAtivo(p);
        if (ativo != null) Fx.p(ativo.entidade.getWorld(), Particle.HAPPY_VILLAGER, ativo.entidade.getLocation().add(0, ativo.entidade.getHeight(), 0), 20, 0.4, 0.4, 0.4, 0);
        if (nq == 5 && qa < 5) Bukkit.broadcast(Poderes.txt("§6§l★ " + p.getName() + " §efundiu pets e fez um §6§lPET PERFEITO §6(5 estrelas)§e: §f" + esc.nome() + "§e!"));
        Bukkit.getScheduler().runTask(pl, () -> abrir(p));
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        InventoryHolder hd = e.getView().getTopInventory().getHolder();
        if (!(hd instanceof MenuPets) && !(hd instanceof MenuFundir)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (hd instanceof MenuPets mp) {
            Integer id = mp.slotId.get(slot);
            if (id == null) return;
            List<PetsPonte.PetDado> l = pl.pets.colecao(p);
            PetsPonte.PetDado esc = escolhido(l), rep = null;
            for (PetsPonte.PetDado d : l) if (d.id() == id) rep = d;
            if (rep == null) return;
            if (rep.selecionado()) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
                return;
            }
            if (esc == null) {
                p.sendMessage(Poderes.txt("§eEscolha primeiro (em §f/pets§e) o pet que vai receber a fusão."));
                return;
            }
            if (!esc.tipo().equals(rep.tipo())) {
                p.sendMessage(Poderes.txt("§cSó dá para fundir dois pets iguais. §7Para fundir este, escolha outro igual a ele em §e/pets§7."));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
                return;
            }
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            final PetsPonte.PetDado a = esc, b = rep;
            Bukkit.getScheduler().runTask(pl, () -> abrirFundir(p, a, b));
        } else if (hd instanceof MenuFundir mf) {
            if (slot == 14) {
                final int a = mf.alvo, b = mf.repetido;
                Bukkit.getScheduler().runTask(pl, () -> fundir(p, a, b));
            } else if (slot == 16) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
                Bukkit.getScheduler().runTask(pl, () -> abrir(p));
            }
        }
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        InventoryHolder hd = e.getView().getTopInventory().getHolder();
        if (hd instanceof MenuPets || hd instanceof MenuFundir) e.setCancelled(true);
    }

    /** /petpoder estrela <jogador> <1-5> (admin): muda as estrelas do pet escolhido. */
    boolean comandoAdmin(org.bukkit.command.CommandSender s, String[] args) {
        if (!s.hasPermission("poderes.admin")) {
            s.sendMessage(Poderes.txt("§cSó admin."));
            return true;
        }
        Player alvo = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
        int q;
        try {
            q = Integer.parseInt(args.length >= 3 ? args[2] : "");
        } catch (NumberFormatException ex) {
            q = -1;
        }
        if (alvo == null || q < 1 || q > 5) {
            s.sendMessage(Poderes.txt("§cUse: /petpoder estrela <jogador> <1-5> (muda o pet escolhido dele)"));
            return true;
        }
        PetsPonte.PetDado esc = escolhido(pl.pets.colecao(alvo));
        if (esc == null) {
            s.sendMessage(Poderes.txt("§c" + alvo.getName() + " não tem pet escolhido."));
            return true;
        }
        definir(esc.id(), q);
        salvar();
        s.sendMessage(Poderes.txt("§a" + esc.nome() + " (#" + esc.id() + ") de " + alvo.getName() + " agora tem " + estrelas(q)));
        return true;
    }

    private static ItemStack item(Material m, String nome, List<String> lore, boolean brilho) {
        ItemStack it = new ItemStack(m == null || !m.isItem() ? Material.EGG : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Poderes.txt(nome));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Poderes.txt(s));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        if (brilho) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100);
    }
}
