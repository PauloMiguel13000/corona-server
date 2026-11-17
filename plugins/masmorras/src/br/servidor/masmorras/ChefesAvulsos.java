package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * 17/11 (sugestao do Sui, aprovada pelo Paulo): o boss mundial (Extras) chama chefes de masmorra na arena dele. Com
 * 2/3 da vida chama um chefe aleatorio de nivel 3; com 1/3, um de nivel 4. O chefe chamado e o mesmo das masmorras
 * (vida, habilidades, sudito e particularidade), rodando numa "masmorra" de mentira que so tem a arena do Colosso.
 * Ficam de fora as cenas que dependem da sala de verdade (tempestade, descida de barco, inferno, mecanicas da sala).
 *
 * O Extras chama por reflexao (sem dependencia entre os plugins): chefeAvulso(...) e tirarChefeAvulso(...) no Masmorras.
 */
final class ChefesAvulsos {

    /** Chefes que nao combinam com uma arena aberta no ceu (somem, ficam parados ou nao dao dano). */
    private static final java.util.Set<String> FORA = java.util.Set.of("ENDERMAN", "SHULKER", "BREEZE");

    private final Masmorras pl;
    private final Random rnd = new Random();
    private final Map<UUID, Masmorra> porCorpo = new HashMap<>();
    private final Map<UUID, BukkitTask> tarefas = new HashMap<>();
    private final NamespacedKey kBossMundial = new NamespacedKey("extras", "boss_mundial");
    private int proximoId = -1000;

    ChefesAvulsos(Masmorras pl) {
        this.pl = pl;
    }

    Masmorra masmorraDe(Entity e) {
        return e == null ? null : porCorpo.get(e.getUniqueId());
    }

    /** Sorteia um tipo de masmorra do nivel (sem a secreta e sem os chefes que nao servem na arena aberta). */
    Tipo sortear(int nivel) {
        List<Tipo> l = new ArrayList<>();
        for (Tipo t : Tipo.TODOS.values())
            if (t.nivel == nivel && !t.secreta && t.chefe != null && !FORA.contains(t.chefe)) l.add(t);
        return l.isEmpty() ? null : l.get(rnd.nextInt(l.size()));
    }

    /**
     * Chama um chefe na arena (caixa por dentro das paredes: centro x/z, chao em y, meia-largura raio, altura). Devolve o
     * corpo ou null.
     */
    LivingEntity chamar(Location onde, int cx, int y, int cz, int raio, int altura, int nivel, int jogadores) {
        Tipo t = sortear(nivel);
        if (t == null) return null;
        Caixa arena = new Caixa(cx - raio, y, cz - raio, cx + raio, y + altura, cz + raio);
        Masmorra m = new Masmorra(proximoId--, t, onde.getWorld().getName(), rnd.nextLong());
        m.salas.add(arena);
        m.protecao.add(arena);
        m.tudo = arena;
        LivingEntity e = pl.corpoDoChefe(m, onde, jogadores, false);
        if (e == null) return null;
        // o Extras (boss mundial) e as torres do Engenheiro tratam como chefe: sem drop, sem lentidao das torres
        e.getPersistentDataContainer().set(kBossMundial, PersistentDataType.BYTE, (byte) 2);
        double vida = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        Chefe c = new Chefe(pl, m, e, vida);
        c.avulso = true;
        m.chefe = c;
        porCorpo.put(e.getUniqueId(), m);
        if (pl.coroas != null) pl.coroas.vestir(e, t);
        e.getWorld().strikeLightningEffect(onde);
        e.getWorld().spawnParticle(Particle.EXPLOSION, onde.clone().add(0, 1, 0), 6, 1, 0.6, 1);
        List<Player> ps = c.naArena();
        if (e instanceof org.bukkit.entity.Warden wd) for (Player p : ps) wd.increaseAnger(p, 90);
        c.anunciar(ps);
        // meio em meio segundo, igual ao chefe da masmorra
        tarefas.put(e.getUniqueId(), Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (!c.vivo()) {
                fim(m, true);
                return;
            }
            c.tick();   // sem ninguem na arena ele so espera: quem tira e o boss mundial (quando morre ou vai embora)
        }, 10L, 10L));
        pl.getLogger().info("Boss mundial chamou " + t.chefeNome + " (nivel " + nivel + ", " + t.id + ") com " + (int) vida + " de vida");
        return e;
    }

    /** O boss mundial acabou (ou o chefe morreu): tira a barra, os suditos e o corpo. */
    void tirar(LivingEntity e) {
        if (e == null) return;
        Masmorra m = porCorpo.get(e.getUniqueId());
        if (m != null) fim(m, false);
        else if (e.isValid()) e.remove();
    }

    private void fim(Masmorra m, boolean morreu) {
        Chefe c = m.chefe;
        if (c == null) return;
        BukkitTask k = tarefas.remove(c.e.getUniqueId());
        if (k != null) k.cancel();
        porCorpo.remove(c.e.getUniqueId());
        if (morreu) {
            c.esconderBarra();
            for (Player p : c.naArena()) p.sendMessage(Masmorras.txt("§a§l✔ §f" + c.t.chefeNome + " §acaiu! §7O Colosso perdeu a proteção."));
        } else c.remover();
        for (Entity x : pl.lacaiosVivos(m)) x.remove();
        for (UUID id : c.guardas) {
            Entity g = Bukkit.getEntity(id);
            if (g != null) g.remove();
        }
        m.chefe = null;
    }

    void desligar() {
        for (Masmorra m : new ArrayList<>(porCorpo.values())) fim(m, false);
    }
}
