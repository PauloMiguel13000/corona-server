package br.servidor.masmorras;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Trilha sonora das masmorras (10/11): cada familia de masmorra tem a sua musica, e a faixa troca sozinha
 * conforme o que esta acontecendo — andando (a trilha do tipo), com monstro por perto (combate) e na arena
 * do chefe (chefe). Tudo tocado no canal MUSIC, no ouvido de cada jogador, entao nao atrapalha os sons do jogo.
 * Quem nao quiser desliga com /musica.
 */
final class Trilha implements Listener {

    /** Quanto dura cada faixa, em segundos (para reemendar o loop antes de acabar). */
    private static final Map<String, Double> DURACAO = new HashMap<>();
    /** Qual faixa toca em cada tipo de masmorra. */
    private static final Map<String, String> FAMILIA = new HashMap<>();

    static {
        DURACAO.put("explorar", 37.5);
        DURACAO.put("combate", 40.3);
        DURACAO.put("chefe", 44.2);
        DURACAO.put("cripta", 42.5);
        DURACAO.put("mina", 35.1);
        DURACAO.put("floresta", 33.9);
        DURACAO.put("gelo", 37.7);
        DURACAO.put("mar", 39.2);
        DURACAO.put("fogo", 32.8);
        DURACAO.put("vazio", 31.8);
        DURACAO.put("arcano", 34.5);

        fam("cripta", "CEMITERIO", "CRIPTA_REAL", "MAUSOLEU", "CATEDRAL", "ALMAS", "SANGUE", "VIUVA", "TUMBA", "OSSOS");
        fam("mina", "LABIRINTO", "CAMARA_TESTES", "LABORATORIO", "RELOGIO", "CIDADE_ANTIGA", "OBSERVATORIO",
                "COLISEU", "TESOURO", "TRONO", "MINA", "FORJA");
        fam("floresta", "ESGOTO", "FUNGOS", "COVIL_LOBOS", "BOSQUE_PALIDO", "JARDIM", "FAZENDA", "MOINHO",
                "ACAMPAMENTO", "PANTANO", "SELVA");
        fam("gelo", "URSO", "GELEIRA", "GELO", "NEVE");
        fam("mar", "NAUFRAGIO", "FAROL", "ILHAS", "ABISMO", "AFOGADA", "CORAL");
        fam("fogo", "VULCAO", "CARMESIM", "DISTORCIDO", "DELTA", "OBSIDIANA", "CRATERA", "FORTALEZA");
        fam("vazio", "NAVE", "CORO", "VAZIO", "CIDADE_END");
        fam("arcano", "BIBLIOTECA", "TROVAO", "TORRE", "ARCANO");
    }

    private static void fam(String faixa, String... ids) {
        for (String id : ids) FAMILIA.put(id, faixa);
    }

    private final Masmorras pl;
    private final Map<UUID, String> tocando = new HashMap<>();
    private final Map<UUID, Long> desde = new HashMap<>();
    private final Set<UUID> mudos = new HashSet<>();

    Trilha(Masmorras pl) {
        this.pl = pl;
    }

    boolean ligada() {
        return pl.getConfig().getBoolean("trilha.ativa", true);
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::passo, 60L, 20L);
    }

    /** Faixa da masmorra, pelo id do tipo; se nao conhecer, vai pelo mundo. */
    private String faixaDe(Masmorra m) {
        String f = FAMILIA.get(m.tipo.id);
        if (f != null) return f;
        return switch (m.tipo.onde) {
            case NETHER -> "fogo";
            case END -> "vazio";
            default -> m.tipo.nivel >= 4 ? "arcano" : "cripta";
        };
    }

    /** O que esta pessoa deveria estar ouvindo agora (ou null para silencio). */
    private String oQue(Player p) {
        if (!ligada() || mudos.contains(p.getUniqueId())) return null;
        Location l = p.getLocation();
        World w = l.getWorld();
        if (w == null) return null;
        Masmorra m = pl.masmorraEm(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (m == null || m.demolindo) return null;
        if (m.chefe != null && m.chefe.vivo() && m.arena().contem(l.getBlockX(), l.getBlockY(), l.getBlockZ())) return "chefe";
        int perto = 0;
        for (Entity e : w.getNearbyEntities(l, 11, 7, 11)) {
            if (e instanceof Enemy && e.isValid() && !e.isDead()) perto++;
            if (perto >= 2) return "combate";
        }
        return faixaDe(m);
    }

    private void passo() {
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            String quer = oQue(p);
            String atual = tocando.get(u);
            if (quer == null) {
                if (atual != null) parar(p, atual);
                continue;
            }
            if (!quer.equals(atual)) {
                if (atual != null) p.stopSound("masmorras." + atual, SoundCategory.MUSIC);
                tocar(p, quer, agora);
                continue;
            }
            double dur = DURACAO.getOrDefault(quer, 40.0);
            Long ini = desde.get(u);
            if (ini == null || agora - ini >= (long) ((dur - 0.35) * 1000)) tocar(p, quer, agora);
        }
    }

    private void tocar(Player p, String faixa, long agora) {
        p.playSound(p, "masmorras." + faixa, SoundCategory.MUSIC, 0.8f, 1f);
        tocando.put(p.getUniqueId(), faixa);
        desde.put(p.getUniqueId(), agora);
    }

    private void parar(Player p, String faixa) {
        p.stopSound("masmorras." + faixa, SoundCategory.MUSIC);
        tocando.remove(p.getUniqueId());
        desde.remove(p.getUniqueId());
    }

    /** Para tudo o que esta tocando para essa pessoa (usado no /musica e ao sair). */
    void calar(Player p) {
        String a = tocando.get(p.getUniqueId());
        if (a != null) parar(p, a);
        for (String f : DURACAO.keySet()) p.stopSound("masmorras." + f, SoundCategory.MUSIC);
    }

    /** /musica — liga e desliga a trilha so para quem pediu. Devolve true se ficou ligada. */
    boolean alternar(Player p) {
        UUID u = p.getUniqueId();
        if (mudos.remove(u)) return true;
        mudos.add(u);
        calar(p);
        return false;
    }

    boolean mudo(Player p) {
        return mudos.contains(p.getUniqueId());
    }

    @EventHandler
    public void trocouDeMundo(PlayerChangedWorldEvent e) {
        calar(e.getPlayer());
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        tocando.remove(e.getPlayer().getUniqueId());
        desde.remove(e.getPlayer().getUniqueId());
    }
}
