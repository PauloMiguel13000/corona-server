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
        // 14/11 (Paulo: "diferenciar a musica de todas, esta muito igual"): 23 temas, cada um com musica propria
        // (sons/gerar_trilhas_v3.py) e a versao de luta da mesma musica (<tema>_luta); Herobrine tem a dele.
        DURACAO.put("cripta", 30.00);
        DURACAO.put("cripta_luta", 30.00);
        DURACAO.put("catedral", 58.18);
        DURACAO.put("catedral_luta", 58.18);
        DURACAO.put("vampiro", 48.00);
        DURACAO.put("vampiro_luta", 48.00);
        DURACAO.put("aranha", 44.80);
        DURACAO.put("aranha_luta", 44.80);
        DURACAO.put("mina", 34.29);
        DURACAO.put("mina_luta", 34.29);
        DURACAO.put("deserto", 36.92);
        DURACAO.put("deserto_luta", 36.92);
        DURACAO.put("pantano", 41.74);
        DURACAO.put("pantano_luta", 41.74);
        DURACAO.put("floresta", 53.33);
        DURACAO.put("floresta_luta", 53.33);
        DURACAO.put("jardim", 45.71);
        DURACAO.put("jardim_luta", 45.71);
        DURACAO.put("selva", 30.48);
        DURACAO.put("selva_luta", 30.48);
        DURACAO.put("quartel", 33.10);
        DURACAO.put("quartel_luta", 33.10);
        DURACAO.put("gelo", 36.92);
        DURACAO.put("gelo_luta", 36.92);
        DURACAO.put("mar", 45.71);
        DURACAO.put("mar_luta", 45.71);
        DURACAO.put("afogada", 54.86);
        DURACAO.put("afogada_luta", 54.86);
        DURACAO.put("relogio", 50.91);
        DURACAO.put("relogio_luta", 50.91);
        DURACAO.put("arcano", 31.30);
        DURACAO.put("arcano_luta", 31.30);
        DURACAO.put("coliseu", 38.40);
        DURACAO.put("coliseu_luta", 38.40);
        DURACAO.put("trovao", 24.30);
        DURACAO.put("trovao_luta", 24.30);
        DURACAO.put("fogo", 26.30);
        DURACAO.put("fogo_luta", 26.30);
        DURACAO.put("nether", 36.36);
        DURACAO.put("nether_luta", 36.36);
        DURACAO.put("vazio", 32.54);
        DURACAO.put("vazio_luta", 32.54);
        DURACAO.put("antiga", 60.00);
        DURACAO.put("antiga_luta", 60.00);
        DURACAO.put("explorar", 36.92);
        DURACAO.put("explorar_luta", 36.92);
        DURACAO.put("herobrine", 44.80);
        DURACAO.put("chefe", 42.3);

        fam("cripta", "CRIPTA", "CEMITERIO", "CRIPTA_REAL", "TUMBA", "OSSOS");
        fam("catedral", "CATEDRAL", "MAUSOLEU");
        fam("vampiro", "SANGUE", "CASTELO");
        fam("aranha", "NINHO", "VIUVA");
        fam("mina", "MINA", "TESOURO");
        fam("deserto", "DESERTO");
        fam("pantano", "PANTANO", "ESGOTO", "FUNGOS");
        fam("floresta", "BOSQUE_PALIDO", "MOINHO", "FAZENDA");
        fam("jardim", "JARDIM");
        fam("selva", "SELVA", "COVIL_LOBOS", "COVIL");
        fam("quartel", "QUARTEL", "ACAMPAMENTO");
        fam("gelo", "GELO", "URSO", "GELEIRA", "NEVE");
        fam("mar", "FAROL", "NAUFRAGIO", "CORAL");
        fam("afogada", "AFOGADA");
        fam("relogio", "LABORATORIO", "CAMARA_TESTES", "RELOGIO");
        fam("arcano", "TORRE", "BIBLIOTECA", "OBSERVATORIO", "ARCANO");
        fam("coliseu", "COLISEU", "LABIRINTO", "TRONO");
        fam("trovao", "TROVAO", "VENTOS");
        fam("fogo", "VULCAO", "CRATERA", "FORJA", "DELTA");
        fam("nether", "BASTIAO", "WITHER", "CARMESIM", "DISTORCIDO", "ALMAS", "OBSIDIANA", "FORTALEZA");
        fam("vazio", "CIDADELA", "PRISAO", "ILHAS", "NAVE", "CORO", "VAZIO", "ABISMO", "CIDADE_END");
        fam("antiga", "CIDADE_ANTIGA", "SECRETA");
    }

    private static void fam(String faixa, String... ids) {
        for (String id : ids) FAMILIA.put(id, faixa);
    }

    private final Masmorras pl;
    private final Map<UUID, String> tocando = new HashMap<>();
    private final Map<UUID, Long> desde = new HashMap<>();
    private final Set<UUID> mudos = new HashSet<>();
    private final Map<UUID, Long> luta = new HashMap<>();

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
            case NETHER -> "nether";
            case END -> "vazio";
            default -> "explorar";
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
        if (m.chefe != null && m.chefe.vivo() && m.arena().contem(l.getBlockX(), l.getBlockY(), l.getBlockZ()))
            return m.tipo.secreta ? "herobrine" : "chefe";
        String tema = faixaDe(m);
        int perto = 0;
        long agora = System.currentTimeMillis();
        for (Entity e : w.getNearbyEntities(l, 11, 7, 11)) {
            if (e instanceof Enemy && e.isValid() && !e.isDead()) perto++;
            if (perto >= 2) { luta.put(p.getUniqueId(), agora); break; }
        }
        // a versao de luta da MESMA musica; so volta para a calma depois de 8 s sem monstro (nao fica trocando)
        Long ul = luta.get(p.getUniqueId());
        return ul != null && agora - ul < 8000 ? tema + "_luta" : tema;
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
        p.stopSound("masmorras.combate", SoundCategory.MUSIC);
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
        luta.remove(e.getPlayer().getUniqueId());
    }
}
