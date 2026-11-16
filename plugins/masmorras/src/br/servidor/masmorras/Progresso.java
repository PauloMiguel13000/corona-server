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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    static final int MAX = 5;
    static final int NETHER = 3, END = 4;

    private final Masmorras pl;
    private final NamespacedKey kAviso;
    private final NamespacedKey kPortal, kPortao;
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();

    Progresso(Masmorras pl) {
        this.pl = pl;
        this.kAviso = new NamespacedKey(pl, "nivel_masmorra_aviso");
        this.kPortal = new NamespacedKey(pl, "portal_nivel_enfeite");
        this.kPortao = new NamespacedKey(pl, "portao_enfeite");
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::atualizarPlacas, 100L, 200L);
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

    /** Entrou numa masmorra acima do seu nivel: volta para a entrada dela. */
    void barrar(Player p, Masmorra m) {
        World w = Bukkit.getWorld(m.mundo);
        if (w == null) return;
        Location fora = new Location(w, m.saidaX + 0.5, m.saidaY, m.saidaZ + 0.5, p.getLocation().getYaw() + 180f, 0f);
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
                int z = td.getLocation().getBlockZ();
                int nivel = z < -14 ? 2 : z < 0 ? 3 : z < 14 ? 4 : 5;
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
