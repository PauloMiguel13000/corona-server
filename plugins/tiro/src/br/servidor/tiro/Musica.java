package br.servidor.tiro;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Trilha dos jogos de tiro (15/11): faixas do pacote corona_tiro (tiro.horda, tiro.horda_luta, tiro.horda_chefe,
 * tiro.missao, tiro.missao_luta). Cada jogador tem a faixa do momento; ela recomeca sozinha quando acaba.
 */
final class Musica {
    private static final Map<String, Double> DURACAO = Map.of("horda", 45.71, "horda_luta", 45.71, "horda_chefe", 27.83, "missao", 35.56, "missao_luta", 35.56);
    private final Tiro pl;
    private final Map<UUID, String> faixa = new HashMap<>();
    private final Map<UUID, Long> desde = new HashMap<>();
    private final Map<UUID, Boolean> repete = new HashMap<>();

    Musica(Tiro pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 20L, 10L);
    }

    private boolean ligada() {
        return pl.getConfig().getBoolean("musica", true);
    }

    /** Troca a faixa do jogador (nada acontece se ja e a mesma). null = silencio. */
    void tocar(Player p, String f) {
        tocar(p, f, true);
    }

    void tocar(Player p, String f, boolean emLoop) {
        UUID u = p.getUniqueId();
        String atual = faixa.get(u);
        if (f != null && !ligada()) f = null;
        if (java.util.Objects.equals(atual, f)) return;
        if (atual != null) p.stopSound("tiro." + atual, SoundCategory.MUSIC);
        if (f == null) {
            faixa.remove(u);
            desde.remove(u);
            repete.remove(u);
            return;
        }
        faixa.put(u, f);
        repete.put(u, emLoop);
        desde.put(u, System.currentTimeMillis());
        p.playSound(p, "tiro." + f, SoundCategory.MUSIC, 0.8f, 1f);
    }

    String atual(Player p) {
        return faixa.get(p.getUniqueId());
    }

    private void tique() {
        long agora = System.currentTimeMillis();
        for (UUID u : new java.util.ArrayList<>(faixa.keySet())) {
            Player p = Bukkit.getPlayer(u);
            String f = faixa.get(u);
            if (p == null || !(pl.mundoDeTiro(p.getWorld()) || p.getWorld().getName().equals(Tiro.MISSAO))) {
                if (p != null) p.stopSound("tiro." + f, SoundCategory.MUSIC);
                faixa.remove(u);
                desde.remove(u);
                repete.remove(u);
                continue;
            }
            if (agora - desde.get(u) < DURACAO.getOrDefault(f, 40.0) * 1000 - 150) continue;
            if (!Boolean.TRUE.equals(repete.get(u))) {
                faixa.remove(u);
                desde.remove(u);
                repete.remove(u);
                continue;
            }
            desde.put(u, agora);
            p.playSound(p, "tiro." + f, SoundCategory.MUSIC, 0.8f, 1f);
        }
    }
}
