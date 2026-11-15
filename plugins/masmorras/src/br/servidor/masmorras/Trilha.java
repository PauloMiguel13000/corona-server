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

    /** 14/11: as musicas novas ficam em "trilha.*" (pacotes corona_trilhas e corona_chefes no Bedrock; no Java dentro do
     *  pacote do CoronaPack), separadas das antigas "masmorras.*" do pacote principal, que continuam la sem uso. */
    static final String SOM = "trilha.";
    /** Quanto dura cada faixa, em segundos (para reemendar o loop antes de acabar). */
    private static final Map<String, Double> DURACAO = new HashMap<>();
    /** Qual faixa toca em cada tipo de masmorra. */
    private static final Map<String, String> FAMILIA = new HashMap<>();

    static {
        // 14/11 (Paulo: "diferenciar a musica de todas, esta muito igual"): 23 temas, cada um com musica propria
        // (sons/gerar_trilhas_v3.py) e a versao de luta da mesma musica (<tema>_luta); Herobrine tem a dele.
        // Cada tema e a musica caracteristica das masmorras (a original) com arranjo proprio. O lobby tem a versao
        // de lobby da musica caracteristica (sons/gerar_lobby.py, estereo e com mais qualidade).
        DURACAO.put("cripta", 41.74);
        DURACAO.put("cripta_luta", 41.74);
        DURACAO.put("catedral", 53.33);
        DURACAO.put("catedral_luta", 53.33);
        DURACAO.put("vampiro", 36.92);
        DURACAO.put("vampiro_luta", 36.92);
        DURACAO.put("aranha", 34.29);
        DURACAO.put("aranha_luta", 34.29);
        DURACAO.put("mina", 34.29);
        DURACAO.put("mina_luta", 34.29);
        DURACAO.put("deserto", 38.40);
        DURACAO.put("deserto_luta", 38.40);
        DURACAO.put("pantano", 41.74);
        DURACAO.put("pantano_luta", 41.74);
        DURACAO.put("floresta", 35.56);
        DURACAO.put("floresta_luta", 35.56);
        DURACAO.put("jardim", 44.65);
        DURACAO.put("jardim_luta", 44.65);
        DURACAO.put("selva", 30.97);
        DURACAO.put("selva_luta", 30.97);
        DURACAO.put("quartel", 33.10);
        DURACAO.put("quartel_luta", 33.10);
        DURACAO.put("gelo", 40.00);
        DURACAO.put("gelo_luta", 40.00);
        DURACAO.put("mar", 32.00);
        DURACAO.put("mar_luta", 32.00);
        DURACAO.put("afogada", 50.53);
        DURACAO.put("afogada_luta", 50.53);
        DURACAO.put("relogio", 30.48);
        DURACAO.put("relogio_luta", 30.48);
        DURACAO.put("arcano", 40.00);
        DURACAO.put("arcano_luta", 40.00);
        DURACAO.put("coliseu", 38.40);
        DURACAO.put("coliseu_luta", 38.40);
        DURACAO.put("trovao", 25.60);
        DURACAO.put("trovao2", 49.90);   // 14/11 (Paulo): a nova e, quando acaba, a primeira versao em seguida (pacote corona_trovao)
        DURACAO.put("trovao_luta", 25.60);
        DURACAO.put("fogo", 26.30);
        DURACAO.put("fogo_luta", 26.30);
        DURACAO.put("nether", 30.00);
        DURACAO.put("nether_luta", 30.00);
        DURACAO.put("vazio", 32.54);
        DURACAO.put("vazio_luta", 32.54);
        DURACAO.put("antiga", 54.86);
        DURACAO.put("antiga_luta", 54.86);
        DURACAO.put("explorar", 36.23);
        DURACAO.put("explorar_luta", 36.23);
        DURACAO.put("herobrine", 51.2);
        DURACAO.put("lobby", 81.82);
        DURACAO.put("chefe", 48.0);
        // 14/11: cada tema tem a sua musica de chefe (sons/gerar_chefes.py)
        DURACAO.put("chefe_cripta", 54.92);
        DURACAO.put("chefe_catedral", 66.12);
        DURACAO.put("chefe_vampiro", 49.85);
        DURACAO.put("chefe_aranha", 46.96);
        DURACAO.put("chefe_mina", 46.96);
        DURACAO.put("chefe_deserto", 51.43);
        DURACAO.put("chefe_pantano", 54.92);
        DURACAO.put("chefe_floresta", 48.36);
        DURACAO.put("chefe_jardim", 57.86);
        DURACAO.put("chefe_selva", 43.20);
        DURACAO.put("chefe_quartel", 45.63);
        DURACAO.put("chefe_gelo", 53.11);
        DURACAO.put("chefe_mar", 44.38);
        DURACAO.put("chefe_afogada", 63.53);
        DURACAO.put("chefe_relogio", 42.63);
        DURACAO.put("chefe_arcano", 53.11);
        DURACAO.put("chefe_coliseu", 51.43);
        DURACAO.put("chefe_trovao", 38.57);
        DURACAO.put("chefe_fogo", 38.57);
        DURACAO.put("chefe_nether", 42.08);
        DURACAO.put("chefe_vazio", 45.00);
        DURACAO.put("chefe_antiga", 67.50);
        DURACAO.put("chefe_explorar", 49.09);

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
        if (w.getName().equals(InfernoHerobrine.MUNDO)) return "herobrine";   // no mundo dele
        if (pl.lobby != null && pl.lobby.noLobby(l)) return "lobby";
        Masmorra m = pl.masmorraEm(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (m == null || m.demolindo) return null;
        String tema = faixaDe(m);
        // chefe vivo: na arena ou perto dele (os chefes ja aparecem logo que a gente entra) -> musica do chefe do tema
        if (m.chefe != null && m.chefe.vivo() && (m.arenaLuta().contem(l.getBlockX(), l.getBlockY(), l.getBlockZ())
                || (m.chefe.e.getWorld().equals(w) && m.chefe.e.getLocation().distanceSquared(l) < 28 * 28))) {
            if (m.tipo.secreta) return "herobrine";
            return DURACAO.containsKey("chefe_" + tema) ? "chefe_" + tema : "chefe";
        }
        int perto = 0;
        long agora = System.currentTimeMillis();
        for (Entity e : w.getNearbyEntities(l, 11, 7, 11)) {
            if (e instanceof Enemy && e.isValid() && !e.isDead()) perto++;
            if (perto >= 2) { luta.put(p.getUniqueId(), agora); break; }
        }
        // a versao de luta da MESMA musica; so volta para a calma depois de 8 s sem monstro (nao fica trocando)
        Long ul = luta.get(p.getUniqueId());
        if (ul != null && agora - ul < 8000) return tema + "_luta";
        if (tema.equals("trovao") && pl.getConfig().getBoolean("trilha.trovao-com-antiga", false)) return "trovao2";
        return tema;
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
                if (atual != null) p.stopSound(SOM + atual, SoundCategory.MUSIC);
                tocar(p, quer, agora);
                continue;
            }
            double dur = DURACAO.getOrDefault(quer, 40.0);
            Long ini = desde.get(u);
            if (ini == null || agora - ini >= (long) ((dur - 0.35) * 1000)) tocar(p, quer, agora);
        }
    }

    private void tocar(Player p, String faixa, long agora) {
        p.playSound(p, SOM + faixa, SoundCategory.MUSIC, 0.8f, 1f);
        tocando.put(p.getUniqueId(), faixa);
        desde.put(p.getUniqueId(), agora);
    }

    private void parar(Player p, String faixa) {
        p.stopSound(SOM + faixa, SoundCategory.MUSIC);
        tocando.remove(p.getUniqueId());
        desde.remove(p.getUniqueId());
    }

    /** Para tudo o que esta tocando para essa pessoa (usado no /musica e ao sair). */
    void calar(Player p) {
        String a = tocando.get(p.getUniqueId());
        if (a != null) parar(p, a);
        for (String f : DURACAO.keySet()) { p.stopSound(SOM + f, SoundCategory.MUSIC); p.stopSound("masmorras." + f, SoundCategory.MUSIC); }
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
