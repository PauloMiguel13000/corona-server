package br.servidor.hotel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Biblioteca (porta 99): dois armarios para se esconder, e a Figura ouve o coracao de quem esta escondido.
 * Ela vem conferir o armario; enquanto ela esta perto, quem mexer a camera e ouvido e arrancado de la.
 */
final class Coracao {
    private Coracao() {}

    private static final class Estado { float yaw, pitch; long desde, batida, isca, janela; double mexeu; boolean avisou; }
    private static final Map<UUID, Estado> ESTADOS = new HashMap<>();

    private static final double PERTO = 5.5;      // a Figura esta "escutando" o armario
    private static final double ALCANCE = 18.0;   // ate onde ela ouve o coracao
    private static final double LIMITE_GRAUS = 14.0; // quanto pode mexer a camera em meio segundo

    /** Chamado no comeco de Figura.ouvir, para cada jogador vivo. */
    static void escondido(Partida.Figura f, Player p, Partida.Jogador j) {
        try {
            if (p == null || j == null) return;
            if (j.armario == null || f == null || f.motor == null || !f.motor.isValid()) { ESTADOS.remove(p.getUniqueId()); return; }
            long agora = System.currentTimeMillis();
            Location lp = p.getLocation();
            Estado e = ESTADOS.get(p.getUniqueId());
            if (e == null) {
                e = new Estado(); e.yaw = lp.getYaw(); e.pitch = lp.getPitch(); e.desde = agora; e.janela = agora; e.isca = agora + 2500;
                ESTADOS.put(p.getUniqueId(), e);
            }
            double d = f.motor.getLocation().distance(lp);

            // o coracao bate mais rapido quanto mais perto ela esta
            long intervalo = d < PERTO ? 430 : d < 10 ? 680 : 1050;
            if (agora >= e.batida) {
                e.batida = agora + intervalo;
                p.playSound(lp, Sound.ENTITY_WARDEN_HEARTBEAT, d < PERTO ? 1.6f : 1.0f, d < PERTO ? 1.25f : 1.0f);
            }

            // ela ouve o coracao e vem conferir o armario
            if (agora >= e.isca && d <= ALCANCE && f.modo != 2) {
                e.isca = Long.MAX_VALUE; // uma visita por esconderijo: ela vem, escuta uns segundos e vai embora
                Planta.Armario a = j.armario;
                f.barulho(new Location(lp.getWorld(), a.x() + 0.5, lp.getY(), a.foraZ() + 0.5), ALCANCE);
            }

            // ficar quieto: sem mexer a camera enquanto ela esta perto
            float dy = Math.abs(lp.getYaw() - e.yaw) % 360f; if (dy > 180f) dy = 360f - dy;
            e.mexeu += dy + Math.abs(lp.getPitch() - e.pitch);
            e.yaw = lp.getYaw(); e.pitch = lp.getPitch();
            boolean barulho = false;
            if (agora - e.janela >= 500) { barulho = e.mexeu > LIMITE_GRAUS; e.mexeu = 0; e.janela = agora; }

            if (d < PERTO) {
                barra(p, "§4❤ §c§lFIQUE QUIETO §7- não mexa a câmera, ela está ouvindo");
                if (barulho && agora - e.desde > 900) { pegar(f, p, j); return; }
            } else if (!e.avisou || agora - e.desde < 4000) {
                e.avisou = true;
                barra(p, "§c❤ §7Seu coração está batendo... quando ela chegar perto, §fnão se mexa");
            }
        } catch (Throwable t) { /* nunca derruba a partida */ }
    }

    private static void barra(Player p, String s) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s));
    }

    private static void pegar(Partida.Figura f, Player p, Partida.Jogador j) throws Exception {
        ESTADOS.remove(p.getUniqueId());
        Field dono = f.getClass().getDeclaredField("this$0"); dono.setAccessible(true);
        Partida pt = (Partida) dono.get(f);
        p.getWorld().playSound(f.motor.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.5f, 0.7f);
        p.sendTitle("§4§lELA TE OUVIU", "§7você se mexeu dentro do armário", 0, 40, 10);
        Method sair = Partida.class.getDeclaredMethod("sairDoArmario", Player.class, Partida.Jogador.class, boolean.class);
        sair.setAccessible(true);
        sair.invoke(pt, p, j, true);
        Field tick = Partida.class.getDeclaredField("tick"); tick.setAccessible(true);
        f.modo = 2;
        f.alvo = p.getUniqueId();
        f.ateModo = tick.getLong(pt) + 80L;
        f.motor.getPathfinder().moveTo(p, 1.55);
    }

    /** Chamado no comeco de Planta.biblioteca: poe os dois armarios antes das estantes, entre as fileiras. */
    static void armarios(Planta pl, Planta.Comodo c, Map<Long, Boolean> mapa) {
        try {
            Method m = Planta.class.getDeclaredMethod("armario", Planta.Comodo.class, Map.class, int.class, boolean.class);
            m.setAccessible(true);
            int larg = c.x1 - c.x0;
            int[] alvos = { c.x0 + larg / 3, c.x0 + (2 * larg) / 3 };
            boolean[] lado = { true, false };
            for (int i = 0; i < 2; i++) {
                boolean feito = false;
                // posicoes entre as fileiras de estantes: (x - x0) % 4 == 2
                for (int passo = 0; passo < 8 && !feito; passo++) {
                    for (int s = -1; s <= 1 && !feito; s += 2) {
                        int x = alvos[i] + s * passo * 4;
                        x = c.x0 + 2 + Math.floorDiv(x - c.x0 - 2 + 2, 4) * 4;
                        if (x < c.x0 + 6 || x > c.x1 - 6) continue;
                        feito = (boolean) m.invoke(pl, c, mapa, x, lado[i]);
                        if (!feito) feito = (boolean) m.invoke(pl, c, mapa, x, !lado[i]);
                        if (passo == 0) break;
                    }
                }
            }
        } catch (Throwable t) { /* sem armarios extras, a biblioteca continua como antes */ }
    }
}
