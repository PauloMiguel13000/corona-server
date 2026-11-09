package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * NPCs de missao do lobby do Mundo das Masmorras. Cada NPC tem 3 missoes em sequencia (matar monstros das masmorras,
 * derrotar chefes ou chegar a um nivel de poder) e da um item de recompensa: o Ferreiro da espadas, a Armeira da
 * armaduras, a Cacadora da arco e botas, a Alquimista da pocoes e consumiveis. O progresso fica em missoes.yml.
 */
final class Missoes implements Listener {

    enum Tipo { MONSTROS, CHEFES, NIVEL }

    /** nivelMin = nivel minimo da masmorra que conta; nivelPoder = nivel de poder do jogador para o NPC liberar a missao. */
    record Missao(String id, String titulo, Tipo tipo, int meta, int nivelMin, int nivelPoder, String descricao, List<ItemStack> premios) {}

    record Npc(String id, String nome, String cor, String profissao, int x, int z, float yaw, List<Missao> missoes) {}

    private final Masmorras pl;
    private final NamespacedKey kNpc;
    private final File arquivo;
    private final YamlConfiguration dados;
    private final List<Npc> npcs = new ArrayList<>();
    private boolean sujo;
    /** Ultimo clique (para o clique de confirmar). */
    private final Map<UUID, Long> clique = new ConcurrentHashMap<>();

    Missoes(Masmorras pl) {
        this.pl = pl;
        this.kNpc = new NamespacedKey(pl, "npc_missao");
        this.arquivo = new File(pl.getDataFolder(), "missoes.yml");
        this.dados = YamlConfiguration.loadConfiguration(arquivo);
        montar();
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::salvar, 20L * 60, 20L * 60);
    }

    List<Npc> npcs() {
        return npcs;
    }

    // ------------------------------------------------------------------ definicao

    private static ItemStack item(Material m, int qtd, String nome, String... encs) {
        ItemStack s = new ItemStack(m, qtd);
        for (String e : encs) {
            String[] p = e.split(":");
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(p[0]));
            if (en != null) s.addUnsafeEnchantment(en, Integer.parseInt(p[1]));
        }
        ItemMeta im = s.getItemMeta();
        if (nome != null) {
            im.customName(Masmorras.txt("§e" + nome));
            im.lore(List.of(Masmorras.txt("§7Recompensa de missão"), Masmorras.txt("§8Lobby do Mundo das Masmorras")));
        }
        s.setItemMeta(im);
        return s;
    }

    private static final int[] NIVEIS = {0, 15, 30, 45, 60};

    private static Missao m(String id, String titulo, Tipo tipo, int meta, int nivelMin, int etapa, String desc, ItemStack... premios) {
        return new Missao(id, titulo, tipo, meta, nivelMin, NIVEIS[etapa], desc, List.of(premios));
    }

    private void montar() {
        // 08/11 (pedido do Paulo): as missoes acompanham o nivel de poder - 5 por NPC, liberam nos niveis das habilidades (0/15/30/45/60)
        npcs.add(new Npc("ferreiro", "Ferreiro Brum", "§6", "weaponsmith", 5, -5, 45f, List.of(
                m("ferreiro1", "Primeiro Sangue", Tipo.MONSTROS, 15, 1, 0, "Derrote 15 monstros dentro de masmorras.",
                        item(Material.IRON_SWORD, 1, "Espada do Ferreiro", "sharpness:1", "unbreaking:1")),
                m("ferreiro2", "Aço Temperado", Tipo.CHEFES, 2, 1, 1, "Derrote 2 chefes de masmorra.",
                        item(Material.IRON_SWORD, 1, "Lâmina Temperada", "sharpness:3", "unbreaking:2")),
                m("ferreiro3", "Fio de Diamante", Tipo.MONSTROS, 40, 2, 2, "Derrote 40 monstros em masmorras de nível 2 ou mais.",
                        item(Material.DIAMOND_SWORD, 1, "Fio de Diamante", "sharpness:2", "unbreaking:2")),
                m("ferreiro4", "Lâmina do Abismo", Tipo.CHEFES, 5, 3, 3, "Derrote 5 chefes de nível 3 ou mais.",
                        item(Material.DIAMOND_SWORD, 1, "Lâmina do Abismo", "sharpness:4", "unbreaking:3", "looting:2")),
                m("ferreiro5", "Obra-Prima", Tipo.CHEFES, 8, 4, 4, "Derrote 8 chefes de nível 4 ou mais.",
                        item(Material.NETHERITE_SWORD, 1, "Obra-Prima do Ferreiro", "sharpness:5", "unbreaking:3", "looting:3", "mending:1")))));
        npcs.add(new Npc("armeira", "Armeira Tália", "§b", "armorer", -5, -5, -45f, List.of(
                m("armeira1", "Cabeça no Lugar", Tipo.MONSTROS, 10, 1, 0, "Derrote 10 monstros dentro de masmorras.",
                        item(Material.IRON_HELMET, 1, "Elmo da Armeira", "protection:1")),
                m("armeira2", "Peito Aberto", Tipo.CHEFES, 1, 2, 1, "Derrote 1 chefe de nível 2 ou mais.",
                        item(Material.IRON_CHESTPLATE, 1, "Peitoral Forjado", "protection:2", "unbreaking:1")),
                m("armeira3", "Pernas Firmes", Tipo.MONSTROS, 40, 2, 2, "Derrote 40 monstros em masmorras de nível 2 ou mais.",
                        item(Material.DIAMOND_LEGGINGS, 1, "Grevas da Armeira", "protection:2")),
                m("armeira4", "Couraça", Tipo.CHEFES, 4, 3, 3, "Derrote 4 chefes de nível 3 ou mais.",
                        item(Material.DIAMOND_CHESTPLATE, 1, "Couraça da Armeira", "protection:3", "unbreaking:2")),
                m("armeira5", "Inquebrável", Tipo.CHEFES, 8, 4, 4, "Derrote 8 chefes de nível 4 ou mais.",
                        item(Material.NETHERITE_CHESTPLATE, 1, "Couraça Inquebrável", "protection:4", "unbreaking:3", "mending:1")))));
        npcs.add(new Npc("cacadora", "Caçadora Ísis", "§2", "fletcher", -5, 5, -135f, List.of(
                m("cacadora1", "Olho de Águia", Tipo.MONSTROS, 20, 1, 0, "Derrote 20 monstros dentro de masmorras.",
                        item(Material.BOW, 1, "Arco da Caçadora", "power:1"), item(Material.ARROW, 32, null)),
                m("cacadora2", "Pés Ligeiros", Tipo.CHEFES, 2, 1, 1, "Derrote 2 chefes de masmorra.",
                        item(Material.IRON_BOOTS, 1, "Botas Ligeiras", "feather_falling:2", "protection:1")),
                m("cacadora3", "Tiro Certeiro", Tipo.MONSTROS, 50, 2, 2, "Derrote 50 monstros em masmorras de nível 2 ou mais.",
                        item(Material.BOW, 1, "Arco Certeiro", "power:3", "unbreaking:2"), item(Material.ARROW, 64, null)),
                m("cacadora4", "Caçadora de Chefes", Tipo.CHEFES, 6, 3, 3, "Derrote 6 chefes de nível 3 ou mais.",
                        item(Material.BOW, 1, "Arco Sem Fim", "power:4", "infinity:1", "unbreaking:3"), item(Material.ARROW, 1, null)),
                m("cacadora5", "Asas da Caçadora", Tipo.CHEFES, 8, 4, 4, "Derrote 8 chefes de nível 4 ou mais.",
                        item(Material.DIAMOND_BOOTS, 1, "Botas da Caçadora", "feather_falling:4", "protection:3", "unbreaking:3", "mending:1"), item(Material.ELYTRA, 1, "Asas da Caçadora", "unbreaking:3")))));
        npcs.add(new Npc("alquimista", "Alquimista Vera", "§d", "cleric", 5, 5, 135f, List.of(
                m("alquimista1", "Primeiros Socorros", Tipo.MONSTROS, 10, 1, 0, "Derrote 10 monstros dentro de masmorras.",
                        Loja.pocao(org.bukkit.potion.PotionType.HEALING), Loja.pocao(org.bukkit.potion.PotionType.HEALING), item(Material.GOLDEN_APPLE, 2, null)),
                m("alquimista2", "Elixir", Tipo.CHEFES, 1, 1, 1, "Derrote 1 chefe de masmorra.",
                        Loja.pocao(org.bukkit.potion.PotionType.STRENGTH), Loja.pocao(org.bukkit.potion.PotionType.STRENGTH), item(Material.EXPERIENCE_BOTTLE, 16, null)),
                m("alquimista3", "Sangue de Dragão", Tipo.NIVEL, 40, 0, 2, "Chegue ao nível de poder 40.",
                        item(Material.GOLDEN_APPLE, 4, null), Loja.pocao(org.bukkit.potion.PotionType.REGENERATION), item(Material.EXPERIENCE_BOTTLE, 32, null)),
                m("alquimista4", "Lenda Viva", Tipo.CHEFES, 4, 3, 3, "Derrote 4 chefes de nível 3 ou mais.",
                        item(Material.DIAMOND_HELMET, 1, "Elmo da Alquimista", "protection:3", "respiration:3", "unbreaking:2"), item(Material.TOTEM_OF_UNDYING, 1, null)),
                m("alquimista5", "Pedra Filosofal", Tipo.NIVEL, 70, 0, 4, "Chegue ao nível de poder 70.",
                        item(Material.ENCHANTED_GOLDEN_APPLE, 2, null), item(Material.TOTEM_OF_UNDYING, 2, null), item(Material.NETHERITE_INGOT, 1, null)))));
    }

    // ------------------------------------------------------------------ dados

    private static String chave(String nome) {
        return nome.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    private String k(Player p, String missao) {
        return "jogadores." + chave(p.getName()) + "." + missao;
    }

    /** Missao atual do NPC para o jogador (a primeira nao feita), ou null se fez todas. */
    private Missao atual(Player p, Npc n) {
        for (Missao m : n.missoes()) if (!dados.getBoolean(k(p, m.id()) + ".feita", false)) return m;
        return null;
    }

    private boolean aceita(Player p, Missao m) {
        return dados.getBoolean(k(p, m.id()) + ".aceita", false);
    }

    private int progresso(Player p, Missao m) {
        if (m.tipo() == Tipo.NIVEL) return Math.max(0, Aura.poder(p));
        return dados.getInt(k(p, m.id()) + ".progresso", 0);
    }

    private void salvar() {
        if (!sujo) return;
        sujo = false;
        try {
            dados.save(arquivo);
        } catch (IOException e) {
            pl.getLogger().warning("Nao consegui salvar missoes.yml: " + e.getMessage());
        }
    }

    void salvarAgora() {
        sujo = true;
        salvar();
    }

    // ------------------------------------------------------------------ eventos de progresso (chamados pelo Masmorras)

    void monstroMorto(Player p, int nivel) {
        avancar(p, Tipo.MONSTROS, nivel);
    }

    void chefeMorto(Player p, int nivel) {
        avancar(p, Tipo.CHEFES, nivel);
    }

    private void avancar(Player p, Tipo tipo, int nivel) {
        for (Npc n : npcs) {
            Missao m = atual(p, n);
            if (m == null || m.tipo() != tipo || !aceita(p, m) || nivel < m.nivelMin()) continue;
            int prog = progresso(p, m) + 1;
            dados.set(k(p, m.id()) + ".progresso", prog);
            sujo = true;
            if (prog == m.meta()) {
                p.sendMessage(Masmorras.txt("§a§l✔ Missão completa: §e" + m.titulo() + "§a! §7Fale com " + n.cor() + n.nome() + " §7no lobby para pegar a recompensa."));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            } else if (prog < m.meta() && (prog % 5 == 0 || m.meta() <= 5)) {
                p.sendActionBar(Masmorras.txt("§e" + m.titulo() + ": §f" + prog + "§7/" + m.meta()));
            }
        }
    }

    // ------------------------------------------------------------------ npcs no lobby

    void garantirNpcs(org.bukkit.World mundo, int y) {
        for (Npc n : npcs) {
            if (!mundo.isChunkLoaded(n.x() >> 4, n.z() >> 4)) continue;
            Location l = new Location(mundo, n.x() + 0.5, y + 1, n.z() + 0.5, n.yaw(), 0f);
            boolean tem = false;
            for (Entity e : mundo.getNearbyEntities(l, 2, 3, 2)) if (n.id().equals(e.getPersistentDataContainer().get(kNpc, PersistentDataType.STRING))) tem = true;
            if (tem) continue;
            Villager v = pl.loja.npc(l, n.cor() + "§l" + n.nome(), null, Loja.profissao(0), new NamespacedKey(pl, "npc_missao_marca"), 1);
            v.getPersistentDataContainer().set(kNpc, PersistentDataType.STRING, n.id());
            Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(n.profissao()));
            if (prof != null) v.setProfession(prof);
            v.customName(Masmorras.txt(n.cor() + "§l" + n.nome() + " §e§l[!]"));
        }
    }

    private Npc npcDe(Entity e) {
        String id = e.getPersistentDataContainer().get(kNpc, PersistentDataType.STRING);
        if (id == null) return null;
        for (Npc n : npcs) if (n.id().equals(id)) return n;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void falar(PlayerInteractEntityEvent e) {
        Npc n = npcDe(e.getRightClicked());
        if (n == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        Long ultimo = clique.put(p.getUniqueId(), agora);
        if (ultimo != null && agora - ultimo < 400) return;
        Missao m = atual(p, n);
        String quem = n.cor() + "§l" + n.nome() + "§7: §f";
        if (m == null) {
            p.sendMessage(Masmorras.txt(quem + "Você já fez todas as minhas missões, herói! Volte sempre."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            return;
        }
        int idx = n.missoes().indexOf(m) + 1;
        int poder = Aura.poder(p);
        if (!aceita(p, m) && m.nivelPoder() > 0 && poder >= 0 && poder < m.nivelPoder()) {
            p.sendMessage(Masmorras.txt(quem + "Você já fez " + (idx - 1) + " missão(ões) minha(s). A próxima (§e" + m.titulo() + "§f) é para quem tem §bnível de poder " + m.nivelPoder()
                    + "§f. Você está no §b" + poder + "§f: volte quando chegar lá!"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!aceita(p, m)) {
            dados.set(k(p, m.id()) + ".aceita", true);
            dados.set(k(p, m.id()) + ".progresso", 0);
            sujo = true;
            p.sendMessage(Masmorras.txt(quem + "Missão " + idx + "/" + n.missoes().size() + ": §e§l" + m.titulo()));
            p.sendMessage(Masmorras.txt("§7» " + m.descricao()));
            p.sendMessage(Masmorras.txt("§7» Recompensa: §e" + nomes(m.premios())));
            p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§e§lNOVA MISSÃO"), Masmorras.txt("§f" + m.titulo())));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
            return;
        }
        int prog = progresso(p, m);
        if (prog < m.meta()) {
            p.sendMessage(Masmorras.txt(quem + "§e" + m.titulo() + " §7— " + m.descricao() + " §fProgresso: §a" + prog + "§7/" + m.meta()));
            if (m.tipo() == Tipo.NIVEL) p.sendMessage(Masmorras.txt("§7» Suba de nível lutando nas masmorras (§f/poderes§7 mostra o seu nível)."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1f);
            return;
        }
        // completa: entrega
        dados.set(k(p, m.id()) + ".feita", true);
        sujo = true;
        for (ItemStack it : m.premios())
            for (ItemStack sobra : p.getInventory().addItem(it.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        p.sendMessage(Masmorras.txt(quem + "Muito bem! Aqui está: §e" + nomes(m.premios()) + "§f."));
        Missao prox = atual(p, n);
        if (prox != null) p.sendMessage(Masmorras.txt("§7» Fale comigo de novo para a próxima missão: §e" + prox.titulo()));
        p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§a§lMISSÃO CUMPRIDA"), Masmorras.txt("§f" + m.titulo())));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.2);
        salvarAgora();
    }

    private static String nomes(List<ItemStack> l) {
        List<String> s = new ArrayList<>();
        for (ItemStack it : l) {
            String nm = it.getItemMeta() != null && it.getItemMeta().hasCustomName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().customName())
                    : it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            s.add((it.getAmount() > 1 ? it.getAmount() + "x " : "") + nm);
        }
        return String.join("§7, §e", s);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoTransforma(EntityTransformEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kNpc)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoApanha(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(kNpc)) e.setCancelled(true);
    }
}
