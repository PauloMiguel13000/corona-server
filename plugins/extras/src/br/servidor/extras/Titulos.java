package br.servidor.extras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * 20/11 (Paulo: "uma aba em que o player consiga escolher apenas 1 titulo para aparecer no servidor, mas que ele possa
 * trocar, sem bug"): /titulo abre o menu. So 1 titulo aparece (chat, TAB e em cima da cabeca): Cargo, VIP ou REBIRTH.
 *  - trocar e silencioso (sem som nenhum, nem do VIP) e tem espera de 3 s, para ninguem ficar trocando de proposito;
 *  - o REBIRTH em cima da cabeca brilha (letras com brilho correndo) e tem uma aureola de particulas;
 *  - a tag do cla continua junto (o Clas pede para este plugin montar a cabeca).
 * A escolha fica no jogador (extras:titulo). O texto do TAB vai em extras:titulo_tab (o Poderes le).
 */
final class Titulos implements Listener {

    enum Tipo { AUTO, NENHUM, CARGO, VIP, REBIRTH }

    private static final Set<String> SEM_AURA = Set.of("backrooms", "fabrica", "galeria", "hotel", "naochore", "slender", "turno_noite", "vovo");
    private static final long ESPERA_MS = 3000L;

    private final Extras pl;
    private final NamespacedKey kEscolha, kTab, kRebirth, kNivel, kAte, kCargo;
    private final Map<UUID, Long> ultimaTroca = new HashMap<>();
    /** Ultima "assinatura" aplicada (tipo + textos): so mexe no placar/TAB quando muda. */
    private final Map<UUID, String> aplicado = new HashMap<>();
    private final Map<UUID, String> claDe = new HashMap<>();
    private int quadro;

    Titulos(Extras pl) {
        this.pl = pl;
        kEscolha = new NamespacedKey(pl, "titulo");
        kTab = new NamespacedKey(pl, "titulo_tab");
        kRebirth = new NamespacedKey("masmorras", "rebirth");
        kNivel = new NamespacedKey(pl, "vip_nivel");
        kAte = new NamespacedKey(pl, "vip_ate");
        kCargo = new NamespacedKey(pl, "cargo");
    }

    void iniciar() {
        for (Player p : Bukkit.getOnlinePlayers()) atualizar(p, true);
        Bukkit.getScheduler().runTaskTimer(pl, () -> { for (Player p : Bukkit.getOnlinePlayers()) atualizar(p, false); }, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(pl, this::brilho, 20L, 5L);
    }

    void parar() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(sb.getTeams())) if (t.getName().startsWith("ttl")) t.unregister();
    }

    // ------------------------------------------------------------------ o que a pessoa tem

    int rebirths(Player p) {
        Integer n = p.getPersistentDataContainer().get(kRebirth, PersistentDataType.INTEGER);
        return n == null ? 0 : n;
    }

    int vip(Player p) {
        Integer n = p.getPersistentDataContainer().get(kNivel, PersistentDataType.INTEGER);
        Long ate = p.getPersistentDataContainer().get(kAte, PersistentDataType.LONG);
        return n == null || n < 1 || n > 4 || ate == null || ate < System.currentTimeMillis() ? 0 : n;
    }

    String cargo(Player p) {
        String c = p.getPersistentDataContainer().get(kCargo, PersistentDataType.STRING);
        return c == null || c.isEmpty() ? null : c;
    }

    boolean tem(Player p, Tipo t) {
        return switch (t) {
            case AUTO, NENHUM -> true;
            case CARGO -> cargo(p) != null;
            case VIP -> vip(p) > 0;
            case REBIRTH -> rebirths(p) > 0;
        };
    }

    Tipo escolhido(Player p) {
        String s = p.getPersistentDataContainer().get(kEscolha, PersistentDataType.STRING);
        try {
            return s == null ? Tipo.AUTO : Tipo.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Tipo.AUTO;
        }
    }

    /** O titulo que aparece agora (o escolhido; se perdeu ele, por ex. o VIP venceu, cai no automatico). */
    Tipo efetivo(Player p) {
        Tipo e = escolhido(p);
        if (e == Tipo.NENHUM) return Tipo.NENHUM;
        if (e != Tipo.AUTO && tem(p, e)) return e;
        if (tem(p, Tipo.CARGO)) return Tipo.CARGO;
        if (tem(p, Tipo.REBIRTH)) return Tipo.REBIRTH;
        if (tem(p, Tipo.VIP)) return Tipo.VIP;
        return Tipo.NENHUM;
    }

    static String romano(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private String nomeRebirth(int n) {
        return "REBIRTH" + (n > 1 ? " " + romano(n) : "");
    }

    /** Texto do titulo para o chat ("" = sem titulo). */
    String chat(Player p) {
        return switch (efetivo(p)) {
            case CARGO -> "§8[" + cargo(p) + "§8] ";
            case VIP -> "§8[" + Vip.NIVEIS[vip(p)].tag() + "§8] ";
            case REBIRTH -> "§5§l✦§d§l" + nomeRebirth(rebirths(p)) + "§5§l✦ ";
            default -> "";
        };
    }

    String tab(Player p) {
        return switch (efetivo(p)) {
            case CARGO -> cargo(p) + " §r";
            case VIP -> Vip.NIVEIS[vip(p)].tag() + " §r";
            case REBIRTH -> "§5§l✦§d§l" + nomeRebirth(rebirths(p)) + "§5§l✦ §r";
            default -> "";
        };
    }

    /** Em cima da cabeca. O REBIRTH muda a cada quadro (brilho correndo pelas letras). */
    String cabeca(Player p, int q) {
        Tipo t = efetivo(p);
        if (t == Tipo.CARGO) return cargo(p) + " §r";
        if (t == Tipo.VIP) return Vip.NIVEIS[vip(p)].tag() + " §r";
        if (t != Tipo.REBIRTH) return "";
        String nome = nomeRebirth(rebirths(p));
        int len = nome.length() + 2;
        int pos = q % (len + 6);   // passa o brilho e descansa um pouco
        StringBuilder sb = new StringBuilder();
        sb.append(cor(0, pos)).append("§l✦");
        for (int i = 0; i < nome.length(); i++) {
            char c = nome.charAt(i);
            if (c == ' ') { sb.append(' '); continue; }
            sb.append(cor(i + 1, pos)).append("§l").append(c);
        }
        sb.append(cor(len - 1, pos)).append("§l✦ §r");
        return sb.toString();
    }

    private static String cor(int i, int pos) {
        int d = Math.abs(i - pos);
        if (d == 0) return "§f";
        if (d == 1) return "§d";
        return "§5";
    }

    private String tagCla(Player p) {
        try {
            org.bukkit.plugin.Plugin c = Bukkit.getPluginManager().getPlugin("Clas");
            if (c == null || !c.isEnabled()) return "";
            Object o = c.getClass().getMethod("tag", Player.class).invoke(c, p);
            return o instanceof String s && !s.isEmpty() ? "§8[§d" + s + "§8] " : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ aplicar (TAB e cabeca)

    /** Refaz TAB e cabeca se algo mudou (ou sempre, com forcar). */
    void atualizar(Player p, boolean forcar) {
        if (!p.isOnline()) return;
        String tab = tab(p);
        String cla = tagCla(p);
        String assin = efetivo(p) + "|" + tab + "|" + cla;
        if (!forcar && assin.equals(aplicado.get(p.getUniqueId()))) return;
        aplicado.put(p.getUniqueId(), assin);
        p.getPersistentDataContainer().set(kTab, PersistentDataType.STRING, tab);
        claDe.put(p.getUniqueId(), cla);
        aplicarCabeca(p, cla);
        try {
            org.bukkit.plugin.Plugin pod = Bukkit.getPluginManager().getPlugin("Poderes");
            if (pod != null && pod.isEnabled()) pod.getClass().getMethod("atualizarTab", Player.class).invoke(pod, p);
        } catch (Exception ignorado) {
            // Poderes antigo
        }
    }

    private static String nomeTime(Player p) {
        return "ttl" + p.getUniqueId().toString().replace("-", "").substring(0, 13);
    }

    /** Time do placar so desta pessoa: prefixo = titulo + tag do cla. Nao mexe em quem esta num time de minigame. */
    private void aplicarCabeca(Player p, String cla) {
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team atual = sb.getEntryTeam(p.getName());
            if (atual != null && !atual.getName().startsWith("ttl") && !atual.getName().startsWith("cla_")) return;   // minigame
            String pre = cabeca(p, quadro) + cla;
            String nome = nomeTime(p);
            if (pre.isEmpty()) {
                if (atual != null) {
                    atual.removeEntry(p.getName());
                    if (atual.getName().startsWith("ttl") || atual.getEntries().isEmpty()) atual.unregister();
                }
                return;
            }
            Team t = sb.getTeam(nome);
            if (t == null) {
                t = sb.registerNewTeam(nome);
                t.setAllowFriendlyFire(true);
                t.setCanSeeFriendlyInvisibles(false);
            }
            if (atual != null && !atual.getName().equals(nome)) {
                atual.removeEntry(p.getName());
                if (atual.getEntries().isEmpty()) atual.unregister();
            }
            if (!t.hasEntry(p.getName())) t.addEntry(p.getName());
            t.prefix(Extras.txt(pre));
        } catch (Exception ex) {
            pl.getLogger().warning("Titulo na cabeca: " + ex.getMessage());
        }
    }

    /** Chamado pelo Clas quando a pessoa entra/sai de um cla. */
    void atualizarCabeca(Player p) {
        aplicado.remove(p.getUniqueId());
        atualizar(p, true);
    }

    /** Brilho do REBIRTH: letras e aureola de particulas em cima da cabeca. */
    private void brilho() {
        quadro++;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (efetivo(p) != Tipo.REBIRTH) continue;
            Team t = sb.getTeam(nomeTime(p));
            if (t != null && t.hasEntry(p.getName())) t.prefix(Extras.txt(cabeca(p, quadro) + claDe.getOrDefault(p.getUniqueId(), "")));
            if (p.isInvisible() || p.getGameMode() == GameMode.SPECTATOR || p.hasMetadata("vanished") || SEM_AURA.contains(p.getWorld().getName())) continue;
            Location l = p.getLocation();
            double y = l.getY() + (p.isSneaking() ? 2.05 : 2.35);
            for (int k = 0; k < 3; k++) {
                double a = quadro * 0.45 + k * (Math.PI * 2 / 3);
                p.getWorld().spawnParticle(Particle.DUST, l.getX() + Math.cos(a) * 0.42, y, l.getZ() + Math.sin(a) * 0.42, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(k == 0 ? Color.fromRGB(0xFF, 0xD0, 0xFF) : Color.fromRGB(0xB0, 0x40, 0xF0), 0.6f));
            }
            if (quadro % 4 == 0) p.getWorld().spawnParticle(Particle.WITCH, l.getX(), y + 0.1, l.getZ(), 1, 0.2, 0.05, 0.2, 0);
        }
    }

    // ------------------------------------------------------------------ chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void chat(io.papermc.paper.event.player.AsyncChatEvent e) {
        String t = chat(e.getPlayer());
        if (t.isEmpty()) return;
        net.kyori.adventure.text.Component tag = Extras.txt(t);
        io.papermc.paper.chat.ChatRenderer antes = e.renderer();
        e.renderer((src, nome, msg, quem) -> tag.append(antes.render(src, nome, msg, quem)));
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        aplicado.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(pl, () -> atualizar(p, true), 30L);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        aplicado.remove(p.getUniqueId());
        claDe.remove(p.getUniqueId());
        ultimaTroca.remove(p.getUniqueId());
        try {
            Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(nomeTime(p));
            if (t != null) t.unregister();
        } catch (Exception ignorado) {
        }
    }

    // ------------------------------------------------------------------ menu

    static final class Tela implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Tipo> slots = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    void abrir(Player p) {
        Tela h = new Tela();
        h.inv = Bukkit.createInventory(h, 27, Extras.txt("§5§lSeus Títulos §8(escolha 1)"));
        montar(p, h);
        p.openInventory(h.inv);
    }

    private void montar(Player p, Tela h) {
        Inventory inv = h.inv;
        h.slots.clear();
        ItemStack vidro = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta vm = vidro.getItemMeta();
        vm.displayName(Extras.txt(" "));
        vidro.setItemMeta(vm);
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        List<Tipo> op = new ArrayList<>(List.of(Tipo.NENHUM));
        if (tem(p, Tipo.CARGO)) op.add(Tipo.CARGO);
        op.add(Tipo.VIP);
        op.add(Tipo.REBIRTH);
        int[] pos = op.size() == 3 ? new int[] {11, 13, 15} : new int[] {10, 12, 14, 16};
        Tipo ef = efetivo(p);
        for (int i = 0; i < op.size(); i++) {
            inv.setItem(pos[i], icone(p, op.get(i), op.get(i) == ef));
            h.slots.put(pos[i], op.get(i));
        }
        ItemStack info = new ItemStack(Material.NAME_TAG);
        ItemMeta im = info.getItemMeta();
        im.displayName(Extras.txt("§e§lComo aparece agora"));
        String t = chat(p);
        im.lore(List.of(Extras.txt("§7Chat: " + (t.isEmpty() ? "" : t) + "§f" + p.getName()),
                Extras.txt("§7Só §f1 §7título aparece (chat, TAB e em cima da cabeça)."),
                Extras.txt("§7Troque quando quiser."),
                Extras.txt("§8A tag do clã continua junto.")));
        info.setItemMeta(im);
        inv.setItem(22, info);
    }

    private ItemStack icone(Player p, Tipo t, boolean usando) {
        boolean tem = tem(p, t);
        Material m;
        String nome;
        List<String> lore = new ArrayList<>();
        switch (t) {
            case NENHUM -> { m = Material.GLASS_BOTTLE; nome = "§f§lSem título"; lore.add("§7Aparece só o seu nome."); }
            case CARGO -> { m = Material.GOLDEN_HELMET; nome = cargo(p) == null ? "§6§lCargo" : cargo(p); lore.add("§7Seu cargo no servidor."); }
            case VIP -> {
                int n = vip(p);
                m = tem ? Vip.NIVEIS[n].icone() : Material.RED_STAINED_GLASS_PANE;
                nome = tem ? Vip.NIVEIS[n].tag() : "§c§lVIP §8(bloqueado)";
                lore.add(tem ? "§7O seu VIP, sem som e sem anúncio." : "§7Para ter: compre um VIP §e/vip§7.");
            }
            default -> {
                int n = rebirths(p);
                m = tem ? Material.NETHER_STAR : Material.RED_DYE;
                nome = tem ? "§5§l✦§d§l" + nomeRebirth(n) + "§5§l✦" : "§c§lREBIRTH §8(bloqueado)";
                lore.add(tem ? "§7Brilha e tem aura em cima da cabeça." : "§7Para ter: faça o §5§lREBIRTH §7(§e/rebirth§7).");
            }
        }
        if (!tem) m = t == Tipo.VIP ? Material.RED_STAINED_GLASS_PANE : Material.RED_DYE;
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.displayName(Extras.txt(nome));
        lore.add("");
        lore.add(usando ? "§a§l✔ EM USO" : tem ? "§e▶ Clique para usar" : "§c✖ Bloqueado");
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Extras.txt(s));
        im.lore(l);
        if (usando) im.setEnchantmentGlintOverride(true);
        im.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        i.setItemMeta(im);
        return i;
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Tela h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        Tipo t = h.slots.get(e.getSlot());
        if (t == null) return;
        if (!tem(p, t)) {
            p.sendActionBar(Extras.txt(t == Tipo.VIP ? "§cVocê não tem VIP. §e/vip" : "§cVocê ainda não fez REBIRTH. §e/rebirth"));
            return;
        }
        if (efetivo(p) == t && escolhido(p) == t) return;
        long agora = System.currentTimeMillis();
        Long ult = ultimaTroca.get(p.getUniqueId());
        if (ult != null && agora - ult < ESPERA_MS) {
            p.sendActionBar(Extras.txt("§7Espere §f" + Math.max(1, (ESPERA_MS - (agora - ult) + 999) / 1000) + "s §7para trocar de novo."));
            return;
        }
        ultimaTroca.put(p.getUniqueId(), agora);
        p.getPersistentDataContainer().set(kEscolha, PersistentDataType.STRING, t.name());
        atualizar(p, true);
        montar(p, h);   // sem som: so troca o item marcado
        p.sendActionBar(Extras.txt(t == Tipo.NENHUM ? "§7Agora sem título." : "§aTítulo trocado: " + chat(p)));
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Tela) e.setCancelled(true);
    }

    boolean comando(org.bukkit.command.CommandSender s, String[] a) {
        if (a.length >= 2 && a[0].equalsIgnoreCase("atualizar") && s.hasPermission("extras.admin")) {
            Player alvo = Bukkit.getPlayerExact(a[1]);
            if (alvo != null) atualizarCabeca(alvo);
            return true;
        }
        if (!(s instanceof Player p)) return true;
        if (a.length >= 1) {
            String q = a[0].toLowerCase(Locale.ROOT);
            Tipo t = switch (q) {
                case "nenhum", "sem", "off" -> Tipo.NENHUM;
                case "vip" -> Tipo.VIP;
                case "rebirth" -> Tipo.REBIRTH;
                case "cargo" -> Tipo.CARGO;
                default -> null;
            };
            if (t != null) {
                if (!tem(p, t)) { p.sendMessage(Extras.txt("§cVocê não tem esse título.")); return true; }
                long agora = System.currentTimeMillis();
                Long ult = ultimaTroca.get(p.getUniqueId());
                if (ult != null && agora - ult < ESPERA_MS) { p.sendMessage(Extras.txt("§7Espere um pouco para trocar de novo.")); return true; }
                ultimaTroca.put(p.getUniqueId(), agora);
                p.getPersistentDataContainer().set(kEscolha, PersistentDataType.STRING, t.name());
                atualizar(p, true);
                p.sendMessage(Extras.txt(t == Tipo.NENHUM ? "§7Agora sem título." : "§aTítulo: " + chat(p)));
                return true;
            }
        }
        abrir(p);
        return true;
    }
}
