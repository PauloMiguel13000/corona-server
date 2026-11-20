package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Nivel de Masmorra (16/11, Paulo no Discord: "um lvl a parte" e "ter que fazer todas as de lvl 1, depois todas as de
 * lvl 2, depois todas as de lvl 3... pra irem liberando"). Todo mundo comeca no 1. Vencer o chefe de TODAS as masmorras
 * de um nivel (as que existem no mapa) libera o proximo. Nao usa o XP do Minecraft nem o nivel de poder.
 * Conta os chefes da Trilha (missoes.yml, "trilha.<nome>.<tipo>.chefe"); o nivel alcancado fica em "progresso.<nome>"
 * e nunca desce. Tranca: os portais das areas (2 a 5), os portoes do Nether (3) e do End (4) e as proprias masmorras
 * (quem entra numa sala de nivel acima do seu volta para a entrada).
 */
final class Progresso implements Listener {

    static final int MAX = 6;   // 20/11 (Paulo: "o limite agora vai ser lvl 6")
    static final int NETHER = 3, END = 4;

    private final Masmorras pl;
    private final NamespacedKey kAviso;
    /** 18/11: quem escondeu o painel com /nivel painel. */
    private final NamespacedKey kSemPlacar;
    private final NamespacedKey kPortal, kPortao;
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();

    Progresso(Masmorras pl) {
        this.pl = pl;
        this.kAviso = new NamespacedKey(pl, "nivel_masmorra_aviso");
        this.kSemPlacar = new NamespacedKey(pl, "sem_placar");
        this.kPortal = new NamespacedKey(pl, "portal_nivel_enfeite");
        this.kPortao = new NamespacedKey(pl, "portao_enfeite");
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::atualizarPlacas, 100L, 200L);
        Bukkit.getScheduler().runTaskTimer(pl, this::aCadaDoisSegundos, 60L, 40L);
    }

    boolean ativo() {
        return pl.getConfig().getBoolean("progresso.ativo", true) && pl.missoes != null;
    }

    // ------------------------------------------------------------------ regras

    /** Masmorras (tipos) daquele nivel que existem no mapa: e o que precisa vencer para liberar o proximo. */
    List<Tipo> exigidas(int nivel) {
        Set<Tipo> s = new LinkedHashSet<>();
        for (Masmorra m : pl.todas)
            if (!m.demolindo && m.tipo != null && !m.tipo.secreta && m.tipo.nivel == nivel) s.add(m.tipo);
        List<Tipo> l = new ArrayList<>(s);
        l.sort(Comparator.comparing(t -> t.nome));
        return l;
    }

    List<Tipo> faltam(Player p, int nivel) {
        List<Tipo> l = new ArrayList<>();
        for (Tipo t : exigidas(nivel)) if (!pl.missoes.trilhaChefeFeito(p, t)) l.add(t);
        return l;
    }

    /** Nivel de Masmorra do jogador (1 a 5). */
    int nivel(Player p) {
        if (!ativo()) return MAX;
        int n = pl.missoes.nivelMasmorra(p);
        if (n <= 0) n = recalcular(p, false);
        return Math.max(1, Math.min(MAX, n));
    }

    /** Sobe o nivel se todas as do nivel atual ja foram vencidas (pode subir varios de uma vez). Nunca desce. */
    int recalcular(Player p, boolean festa) {
        if (!ativo()) return MAX;
        int salvo = pl.missoes.nivelMasmorra(p);
        int antes = Math.max(1, salvo), n = antes;
        while (n < MAX && faltam(p, n).isEmpty()) n++;
        if (n != salvo) pl.missoes.definirNivelMasmorra(p, n);
        if (festa && n > antes) comemorar(p, n);
        if (festa && n == MAX && !pl.missoes.mestreMasmorras(p) && faltam(p, MAX).isEmpty()) {
            pl.missoes.marcarMestreMasmorras(p);
            p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§6§l★ MESTRE DAS MASMORRAS ★"), Masmorras.txt("§fVocê venceu todos os chefes!")));
            Bukkit.broadcast(Masmorras.txt(Masmorras.PREFIXO + "§6§l★ " + p.getName() + " §evenceu os chefes de TODAS as masmorras e virou §6§lMESTRE DAS MASMORRAS§e!"));
        }
        return n;
    }

    // ================================================================== 20/11 REBIRTH
    /** Quantos REBIRTH a pessoa ja fez (fica no jogador, o Poderes le para os slots de runa extras). */
    static final NamespacedKey K_REBIRTH = new NamespacedKey("masmorras", "rebirth");

    static int rebirths(Player p) {
        Integer n = p.getPersistentDataContainer().get(K_REBIRTH, org.bukkit.persistence.PersistentDataType.INTEGER);
        return n == null ? 0 : n;
    }

    static String romano(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    /** Titulo do chat e do TAB: ✦REBIRTH✦ (II, III... a partir do segundo). */
    static String titulo(int n) {
        if (n <= 0) return "";
        return "§5§l✦§d§lREBIRTH" + (n > 1 ? " " + romano(n) : "") + "§5§l✦ ";
    }

    /** Terminou TODAS as masmorras de TODOS os niveis (1 a MAX). */
    /** 20/11 (Paulo: "o rebirth 1, so precise a pessoa fazer as dungeons ate as lvl 5"): ate que nivel precisa vencer tudo. */
    int nivelDoRebirth(Player p) {
        return rebirths(p) == 0 ? Math.min(MAX, pl.getConfig().getInt("rebirth.primeiro-ate-nivel", 5)) : MAX;
    }

    boolean podeRenascer(Player p) {
        if (!ativo()) return false;
        recalcular(p, false);
        int ate = nivelDoRebirth(p);
        if (nivel(p) < ate) return false;
        for (int n = 1; n <= ate; n++) if (!faltam(p, n).isEmpty()) return false;
        return true;
    }

    void comandoRebirth(Player p, String[] a) {
        int ja = rebirths(p);
        if (!podeRenascer(p)) {
            p.sendMessage(Masmorras.txt("§5§l✦ REBIRTH §8» §7Para renascer, vença o chefe de §fTODAS §7as masmorras dos níveis §f1 a " + nivelDoRebirth(p) + "§7."));
            p.sendMessage(Masmorras.txt("§7Você volta ao §fNível de Masmorra 1 §7e refaz tudo, mas ganha §d+1 slot de runa §7(§c+0,3% de dano §7e §a+0,3% de defesa§7) e o título §5§l✦§d§lREBIRTH§5§l✦§7."));
            if (ja > 0) p.sendMessage(Masmorras.txt("§7Seus renascimentos: §d" + ja));
            String f = falta(p);
            if (!f.isEmpty()) p.sendMessage(Masmorras.txt("§7» " + f));
            return;
        }
        if (a.length == 0 || !a[0].equalsIgnoreCase("confirmar")) {
            p.sendMessage(Masmorras.txt("§5§l✦ REBIRTH §8» §fVocê pode renascer! §7Volta ao §fNível de Masmorra 1 §7(a trilha zera; itens e coronas ficam)."));
            p.sendMessage(Masmorras.txt("§7Ganha §d+1 slot de runa §7(+0,3% de dano e defesa), o título e a §dEstrela do Renascido§7. Para confirmar: §d§l/rebirth confirmar"));
            return;
        }
        pl.missoes.zerarParaRebirth(p);
        int n = ja + 1;
        p.getPersistentDataContainer().set(K_REBIRTH, org.bukkit.persistence.PersistentDataType.INTEGER, n);
        p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§5§l✦ §d§lR E B I R T H §5§l✦"),
                Masmorras.txt("§fRenascimento " + romano(n) + " §7— de volta ao nível 1, mais forte")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.6f);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 120, 0.6, 1.2, 0.6, 0.15);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.6, 0.9, 0.6, 0.4);
        Bukkit.broadcast(Masmorras.txt(Masmorras.PREFIXO + titulo(n) + "§f" + p.getName() + " §dRENASCEU! §7Venceu todas as masmorras e voltou ao nível 1 para ficar ainda mais forte."));
        if (pl.estrela != null) pl.estrela.entregar(p, true);   // item exclusivo
        // TAB do Poderes (le a marca)
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "poderes atualizartab " + p.getName()); }, 5L);
    }

    /** O titulo REBIRTH aparece antes do nome no chat (por cima da tag de VIP). */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void chatRebirth(io.papermc.paper.event.player.AsyncChatEvent e) {
        int n = rebirths(e.getPlayer());
        if (n <= 0) return;
        // 20/11: com o Extras ligado, o titulo (1 so, escolhido no /titulo) e colocado por ele
        org.bukkit.plugin.Plugin ex = Bukkit.getPluginManager().getPlugin("Extras");
        if (ex != null && ex.isEnabled()) return;
        net.kyori.adventure.text.Component tag = Masmorras.txt(titulo(n));
        io.papermc.paper.chat.ChatRenderer antes = e.renderer();
        e.renderer((src, nome, msg, quem) -> tag.append(antes.render(src, nome, msg, quem)));
    }

    /** Quem entra com rebirth salvo e sem a marca no jogador (perdeu o dado): devolve a marca. */
    @org.bukkit.event.EventHandler
    public void entrouRebirth(org.bukkit.event.player.PlayerJoinEvent e) {
        if (pl.missoes == null) return;
        int salvo = pl.missoes.rebirthsSalvos(e.getPlayer());
        if (salvo > rebirths(e.getPlayer())) e.getPlayer().getPersistentDataContainer().set(K_REBIRTH, org.bukkit.persistence.PersistentDataType.INTEGER, salvo);
    }

    boolean pode(Player p, int nivelMasmorra) {
        if (!ativo() || p.hasPermission("masmorras.admin")) return true;
        if (nivel(p) >= nivelMasmorra) return true;
        // confere de novo (uma masmorra do nivel pode ter sido desmanchada/trocada desde a ultima conta)
        return recalcular(p, true) >= nivelMasmorra;
    }

    private void comemorar(Player p, int n) {
        p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§6§lNÍVEL DE MASMORRA " + n),
                Masmorras.txt("§fAs masmorras de nível " + n + " foram liberadas!")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.6, 0.9, 0.6, 0.3);
        Bukkit.broadcast(Masmorras.txt(Masmorras.PREFIXO + "§e" + p.getName() + " §7chegou ao §6§lNível de Masmorra " + n + "§7!"));
        List<Tipo> prox = exigidas(n);
        p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§aLiberado: §fmasmorras de nível " + n
                + (n == NETHER ? " §7(e o portão do §cNether§7)" : n == END ? " §7(e o portão do §dEnd§7)" : "") + "§a."));
        if (n < MAX || !prox.isEmpty())
            p.sendMessage(Masmorras.txt("§7» Para o próximo: vença o chefe das §f" + prox.size() + " §7masmorras de nível " + n + ". Veja em §e/nivel§7."));
    }

    /** Texto curto do que falta (para as mensagens de bloqueio). */
    String falta(Player p) {
        int n = nivel(p);
        List<Tipo> f = faltam(p, n);
        if (f.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < f.size() && i < 4; i++) sb.append(i == 0 ? "" : "§7, ").append("§f").append(f.get(i).nome);
        if (f.size() > 4) sb.append(" §7e mais ").append(f.size() - 4);
        return "§7Faltam §f" + f.size() + " §7chefe" + (f.size() == 1 ? "" : "s") + " de nível " + n + ": " + sb + "§7.";
    }

    /** Mensagem de "ainda trancado" (com intervalo, para nao encher o chat). */
    void avisarTrancado(Player p, String oQue, int precisa) {
        long agora = System.currentTimeMillis();
        Long u = ultimoAviso.get(p.getUniqueId());
        p.sendActionBar(Masmorras.txt("§c" + oQue + " precisa do Nível de Masmorra " + precisa + " §7(você: " + nivel(p) + ")"));
        if (u != null && agora - u < 6000) return;
        ultimoAviso.put(p.getUniqueId(), agora);
        p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§c" + oQue + " precisa do §6Nível de Masmorra " + precisa + "§c. §7Você está no §6" + nivel(p) + "§7."));
        String f = falta(p);
        if (!f.isEmpty()) p.sendMessage(Masmorras.txt("§7» " + f + " §7Lista completa: §e/nivel"));
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 0.8f);
    }

    /** Entrou numa masmorra acima do seu nivel: volta para a entrada dela (ou para a borda da sua area). */
    void barrar(Player p, Masmorra m) {
        World w = Bukkit.getWorld(m.mundo);
        if (w == null) return;
        Location fora = new Location(w, m.saidaX + 0.5, m.saidaY, m.saidaZ + 0.5, p.getLocation().getYaw() + 180f, 0f);
        if (anel(fora) > nivel(p)) fora = lugarSeguro(p, fora);
        p.setFallDistance(0);
        p.teleportAsync(fora, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        avisarTrancado(p, "A " + m.tipo.nome + " (nível " + m.tipo.nivel + ")", m.tipo.nivel);
    }

    // ------------------------------------------------------------------ /nivel

    void comando(Player p) {
        recalcular(p, true);
        int n = nivel(p);
        p.sendMessage(Masmorras.txt("§8+--------------------------------+"));
        p.sendMessage(Masmorras.txt(" §6§lNÍVEL DE MASMORRA: §f§l" + n + " §7de " + MAX + (pl.missoes.mestreMasmorras(p) ? " §6★ Mestre" : "")));
        p.sendMessage(Masmorras.txt("§8| §7Libera vencendo o chefe de §fTODAS §7as masmorras do seu nível."));
        StringBuilder ab = new StringBuilder();
        for (int i = 1; i <= MAX; i++) ab.append(i == 1 ? "" : " ").append(i <= n ? "§a" + i : "§8" + i);
        p.sendMessage(Masmorras.txt("§8| §7Níveis liberados: " + ab + "  §7Nether: " + (n >= NETHER ? "§aaberto" : "§cnível " + NETHER)
                + " §7End: " + (n >= END ? "§aaberto" : "§cnível " + END)));
        List<Tipo> ex = exigidas(n);
        int feitas = 0;
        for (Tipo t : ex) if (pl.missoes.trilhaChefeFeito(p, t)) feitas++;
        p.sendMessage(Masmorras.txt("§8| §fChefes de nível " + n + ": §e" + feitas + "§7/§e" + ex.size()));
        for (Tipo t : ex) {
            boolean ok = pl.missoes.trilhaChefeFeito(p, t);
            String onde = t.onde == Tipo.Onde.NETHER ? " §c(Nether)" : t.onde == Tipo.Onde.END ? " §d(End)" : "";
            p.sendMessage(Masmorras.txt("§8|  " + (ok ? "§a✔ §7" : "§c✘ §f") + t.nome + " §8- " + t.chefeNome + onde));
        }
        if (n < MAX) p.sendMessage(Masmorras.txt("§8| §7Vença todos para liberar o §6nível " + (n + 1) + "§7."));
        else if (feitas >= ex.size()) p.sendMessage(Masmorras.txt("§8| §6Você venceu todas! Mestre das Masmorras."));
        p.sendMessage(Masmorras.txt("§8| §7Bússola até a próxima: §e/masmorras menu§7. Equipamento: §e/nivel equipamento"));
        p.sendMessage(Masmorras.txt("§8+--------------------------------+"));
    }

    // ------------------------------------------------------------------ areas (aneis do mapa) trancadas

    /** Nivel do anel do mapa nesse lugar (so no mundo normal das masmorras; 0 = fora/sem anel). */
    int anel(Location l) {
        if (l == null || pl.mundoNovo == null || pl.mundoNovo.normal == null || pl.mundoNovo.gerador == null || !pl.mundoNovo.normal.equals(l.getWorld())) return 0;
        double r = Math.sqrt(l.getX() * l.getX() + l.getZ() * l.getZ());
        if (r < 250) return 0;   // lobby e planalto em volta
        return pl.mundoNovo.gerador.nivel(r);
    }

    private boolean areaLiberada(Player p, Location l) {
        int a = anel(l);
        return a <= 1 || pode(p, a);
    }

    /** Ultimo lugar em area liberada por onde o jogador passou (no mundo das masmorras). */
    private final Map<UUID, Location> ultimoLivre = new HashMap<>();

    /**
     * Para onde mandar quem esta numa area trancada: o ultimo lugar liberado por onde passou; sem isso, o lobby.
     * 17/11: antes procurava o chao na borda da area (getHighestBlockYAt longe), o que carregava/gerava chunk na hora e
     * travou o servidor (VigiaLag apontou Progresso.lugarSeguro).
     */
    Location lugarSeguro(Player p, Location de) {
        World w = pl.mundoNovo.normal;
        Location u = ultimoLivre.get(p.getUniqueId());
        if (u != null && w.equals(u.getWorld()) && areaLiberada(p, u)) {
            Location l = u.clone();
            l.setYaw(de.getYaw());
            l.setPitch(de.getPitch());
            return l;
        }
        return w.getSpawnLocation();
    }

    private void lembrarLivre(Player p, Location l) {
        if (l.getWorld() != null && l.getWorld().equals(pl.mundoNovo.normal) && p.isOnGround()) ultimoLivre.put(p.getUniqueId(), l.clone());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo(), de = e.getFrom();
        if (to.getBlockX() == de.getBlockX() && to.getBlockZ() == de.getBlockZ()) return;
        if (!ativo() || pl.mundoNovo == null || pl.mundoNovo.normal == null || !pl.mundoNovo.normal.equals(to.getWorld())) return;
        Player p = e.getPlayer();
        if (anel(to) <= 1 || p.hasPermission("masmorras.admin") || areaLiberada(p, to)) {
            if (to.getBlockX() % 4 == 0 || to.getBlockZ() % 4 == 0) lembrarLivre(p, to);
            return;
        }
        int a = anel(to);
        if (areaLiberada(p, de)) {
            Location volta = de.clone();
            volta.setYaw(to.getYaw());
            volta.setPitch(to.getPitch());
            e.setTo(volta);
            double r = Math.max(1, Math.sqrt(de.getX() * de.getX() + de.getZ() * de.getZ()));
            p.setVelocity(new org.bukkit.util.Vector(-de.getX() / r * 0.6, 0.25, -de.getZ() / r * 0.6));
        } else {
            p.teleportAsync(lugarSeguro(p, to), PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        avisarTrancado(p, "A área de nível " + a, a);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void teleportar(PlayerTeleportEvent e) {
        if (!ativo()) return;
        Player p = e.getPlayer();
        Location to = e.getTo();
        int a = anel(to);
        if (a <= 1 || p.hasPermission("masmorras.admin") || areaLiberada(p, to)) return;
        // morreu e renasce na entrada da masmorra em que estava: deixa
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        e.setCancelled(true);
        avisarTrancado(p, "A área de nível " + a, a);
    }

    // ------------------------------------------------------------------ placar lateral (o que falta)

    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> placares = new HashMap<>();

    private boolean mundoDasMasmorras(World w) {
        MundoMasmorras mm = pl.mundoNovo;
        return mm != null && w != null && (w.equals(mm.normal) || w.equals(mm.nether) || w.equals(mm.end));
    }

    private void aCadaDoisSegundos() {
        if (!ativo()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!mundoDasMasmorras(p.getWorld())) {
                tirarPlacar(p);
                continue;
            }
            // de barco, cavalo ou elytra o andar() nao pega: confere aqui
            if (anel(p.getLocation()) <= 1 || areaLiberada(p, p.getLocation())) lembrarLivre(p, p.getLocation());
            if (!p.hasPermission("masmorras.admin") && anel(p.getLocation()) > 1 && !areaLiberada(p, p.getLocation())) {
                int a = anel(p.getLocation());
                if (p.isInsideVehicle()) p.leaveVehicle();
                p.teleportAsync(lugarSeguro(p, p.getLocation()), PlayerTeleportEvent.TeleportCause.PLUGIN);
                avisarTrancado(p, "A área de nível " + a, a);
            }
            if (pl.getConfig().getBoolean("progresso.placar", true) && !p.getPersistentDataContainer().has(kSemPlacar)) placar(p);
            else tirarPlacar(p);
        }
    }

    void placar(Player p) {
        org.bukkit.scoreboard.Scoreboard sb = placares.get(p.getUniqueId());
        if (sb == null || p.getScoreboard() != sb) {
            if (p.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) return;   // outro plugin usando: nao mexe
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            org.bukkit.scoreboard.Objective o = sb.registerNewObjective("masmorras", org.bukkit.scoreboard.Criteria.DUMMY, Masmorras.txt("§6§l⚔ MASMORRAS ⚔"));
            o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            try {
                o.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
            } catch (Throwable ignored) {
            }
            placares.put(p.getUniqueId(), sb);
            p.setScoreboard(sb);
        }
        org.bukkit.scoreboard.Objective o = sb.getObjective("masmorras");
        if (o == null) return;
        int n = nivel(p);
        List<Tipo> f = faltam(p, n);
        List<String> l = new ArrayList<>();
        l.add("§7Seu nível: §6§l" + n + " §7de " + MAX);
        l.add("§8 ");
        if (f.isEmpty()) l.add(n >= MAX ? "§6§l★ Todas vencidas!" : "§aNível " + n + " completo!");
        else {
            l.add("§fFaltam §c§l" + f.size() + " §fdo nível " + n + ":");
            int max = f.size() > 7 ? 6 : 7;
            for (int i = 0; i < f.size() && i < max; i++) {
                Tipo t = f.get(i);
                l.add("§c✘ §f" + t.nome + (t.onde == Tipo.Onde.NETHER ? " §c(Nether)" : t.onde == Tipo.Onde.END ? " §d(End)" : ""));
            }
            if (f.size() > max) l.add("§7+ " + (f.size() - max) + " outras §8(/nivel)");
        }
        l.add("§1 ");
        if (n < MAX) l.add("§7Vença todas: libera o §e" + (n + 1) + (n + 1 == NETHER ? " §7+ §cNether" : n + 1 == END ? " §7+ §dEnd" : ""));
        l.add("§e/nivel §8• §e/masmorras menu");
        l.add("§8/nivel painel: esconder");
        java.util.Set<String> agora = new java.util.HashSet<>();
        for (int i = 0; i < l.size(); i++) {
            String e = l.get(i);
            while (agora.contains(e)) e += "§r";
            agora.add(e);
            o.getScore(e).setScore(l.size() - i);
        }
        for (String e : new ArrayList<>(sb.getEntries())) if (!agora.contains(e)) sb.resetScores(e);
    }

    /** 18/11: /nivel painel. Devolve true se escondeu. */
    boolean alternarPlacar(Player p) {
        org.bukkit.persistence.PersistentDataContainer d = p.getPersistentDataContainer();
        if (d.has(kSemPlacar)) {
            d.remove(kSemPlacar);
            if (mundoDasMasmorras(p.getWorld())) placar(p);
            return false;
        }
        d.set(kSemPlacar, PersistentDataType.BYTE, (byte) 1);
        tirarPlacar(p);
        return true;
    }

    void tirarPlacar(Player p) {
        org.bukkit.scoreboard.Scoreboard sb = placares.remove(p.getUniqueId());
        if (sb != null && p.getScoreboard() == sb) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // ------------------------------------------------------------------ avisos e placas

    @EventHandler
    public void mudouDeMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!ativo() || pl.mundoNovo == null || pl.mundoNovo.normal == null || !p.getWorld().equals(pl.mundoNovo.normal)) return;
        if (p.getPersistentDataContainer().has(kAviso)) return;
        p.getPersistentDataContainer().set(kAviso, PersistentDataType.BYTE, (byte) 1);
        int n = recalcular(p, false);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§6§lNOVO: Nível de Masmorra! §7Você está no §6" + n + "§7."));
            p.sendMessage(Masmorras.txt("§7Vença o chefe de §fTODAS §7as masmorras do seu nível para liberar o próximo (1 → 5)."));
            p.sendMessage(Masmorras.txt("§7Veja o que falta com §e/nivel§7."));
        }, 60L);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        ultimoAviso.remove(e.getPlayer().getUniqueId());
        ultimoLivre.remove(e.getPlayer().getUniqueId());
        tirarPlacar(e.getPlayer());
    }

    /** Troca o texto antigo das placas dos portais ("equipamento"/"nivel de poder") pelo Nivel de Masmorra. */
    private void atualizarPlacas() {
        if (!ativo() || pl.mundoNovo == null || pl.mundoNovo.normal == null) return;
        World w = pl.mundoNovo.normal;
        Location c = new Location(w, 0, GeradorMundo.LOBBY_Y, 0);
        if (!c.isChunkLoaded()) return;
        for (Entity x : w.getNearbyEntities(c, 45, 25, 45)) {
            if (!(x instanceof TextDisplay td)) continue;
            String novo = null;
            if (td.getPersistentDataContainer().has(kPortal, PersistentDataType.INTEGER)) {
                Integer marca = td.getPersistentDataContainer().get(kPortal, PersistentDataType.INTEGER);
                int z = td.getLocation().getBlockZ();
                // lobby v5: a marca guarda o nivel; os letreiros antigos (marca 1) iam pelo z
                int nivel = marca != null && marca >= 2 ? marca : z < -14 ? 2 : z < 0 ? 3 : z < 14 ? 4 : 5;
                novo = PortaisNivel.visual(nivel)[3] + "§lÁREA DE NÍVEL " + nivel + "\n§7Masmorras de nível " + nivel + " do mapa\n§6precisa do Nível de Masmorra " + nivel;
            } else if (td.getPersistentDataContainer().has(kPortao, PersistentDataType.INTEGER) && td.getLocation().getX() > 20) {
                boolean nether = td.getLocation().getZ() < 0;
                novo = nether ? "§c§lNETHER DAS MASMORRAS\n§7Masmorras de nível 3 e 4 no fogo\n§6precisa do Nível de Masmorra " + NETHER
                        : "§d§lEND DAS MASMORRAS\n§7Masmorras de nível 4 e 5 no vazio\n§6precisa do Nível de Masmorra " + END;
            }
            if (novo == null) continue;
            String atual = PlainTextComponentSerializer.plainText().serialize(td.text());
            String alvo = PlainTextComponentSerializer.plainText().serialize(Masmorras.txt(novo));
            if (!atual.equals(alvo)) td.text(Masmorras.txt(novo));
        }
    }
}
