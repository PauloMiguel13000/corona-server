package br.servidor.poderes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Passiva do Cipo: "Seiva Venenosa". Todo ataque do jogador Cipo num monstro (soco, espada, machado, flecha, tridente...)
 * envenena o alvo. O veneno tira vida a cada segundo por alguns segundos; o dano por segundo cresce com o nivel de poder,
 * do mesmo jeito que o soco do Lutador (Lutador.bonusSoco), so que mais fraco, porque o Cipo pode usar espada.
 * Bater de novo renova o veneno (nao acumula). O veneno aqui pega tambem mortos-vivos (o do jogo nao pega).
 */
final class VenenoCipo implements Listener {

    static final double DANO_MIN_PADRAO = 0.6, DANO_MAX_PADRAO = 3.0;
    static final int SEGUNDOS_PADRAO = 4;

    private final Poderes pl;
    /** alvo -> {quando acaba (ms), dano por segundo, dono} */
    private final Map<UUID, Envenenado> envenenados = new HashMap<>();
    private final Map<UUID, long[]> nivelCache = new HashMap<>();

    private record Envenenado(LivingEntity alvo, long ate, double danoSegundo, UUID dono) {}

    VenenoCipo(Poderes pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 20L, 20L);
    }

    double danoMin() {
        return pl.getConfig().getDouble("cipo.veneno-min", DANO_MIN_PADRAO);
    }

    double danoMax() {
        return pl.getConfig().getDouble("cipo.veneno-max", DANO_MAX_PADRAO);
    }

    int segundos() {
        return Math.max(1, pl.getConfig().getInt("cipo.veneno-segundos", SEGUNDOS_PADRAO));
    }

    int nivelMaximo() {
        return pl.nivelPosicao(Classe.CIPO.poderes.length - 1);
    }

    private int nivel(Player p) {
        long agora = System.currentTimeMillis();
        long[] c = nivelCache.get(p.getUniqueId());
        if (c != null && c[1] > agora) return (int) c[0];
        int n = pl.nivelDePoder(p);
        nivelCache.put(p.getUniqueId(), new long[] {n, agora + 3000L});
        return n;
    }

    /** Dano por segundo do veneno do jogador agora. */
    double danoAtual(Player p) {
        return Lutador.bonusSoco(Math.max(0, nivel(p)), nivelMaximo(), danoMin(), danoMax());
    }

    private Player autor(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void ataque(EntityDamageByEntityEvent e) {
        if (pl.emDanoPoder) return;   // dano do proprio veneno ou de um poder: nao renova
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c != EntityDamageEvent.DamageCause.ENTITY_ATTACK && c != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && c != EntityDamageEvent.DamageCause.PROJECTILE) return;
        Player p = autor(e.getDamager());
        if (p == null || pl.classe(p) != Classe.CIPO || pl.semPoderes(p.getWorld())) return;
        if (!(e.getEntity() instanceof LivingEntity alvo) || alvo instanceof Player || alvo instanceof ArmorStand) return;
        if (pl.ehAliado(alvo, p) || pl.pets.ehPet(alvo) || pl.vis.ehVisual(alvo)) return;
        if (e.getFinalDamage() <= 0) return;
        double dps = danoAtual(p);
        Envenenado antes = envenenados.get(alvo.getUniqueId());
        boolean novo = antes == null || antes.ate() < System.currentTimeMillis();
        envenenados.put(alvo.getUniqueId(), new Envenenado(alvo, System.currentTimeMillis() + segundos() * 1000L, Math.max(dps, antes == null ? 0 : antes.danoSegundo()), p.getUniqueId()));
        Location l = alvo.getLocation().add(0, alvo.getHeight() * 0.6, 0);
        Fx.p(alvo.getWorld(), Particle.ENTITY_EFFECT, l, 8, 0.3, 0.4, 0.3, 0, Color.fromRGB(70, 200, 40));
        if (novo) alvo.getWorld().playSound(l, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.6f, 0.7f);
    }

    private void tique() {
        if (envenenados.isEmpty()) return;
        long agora = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Envenenado>> it = envenenados.entrySet().iterator();
        while (it.hasNext()) {
            Envenenado v = it.next().getValue();
            LivingEntity alvo = v.alvo();
            if (alvo.isDead() || !alvo.isValid() || v.ate() < agora) {
                it.remove();
                continue;
            }
            Player dono = Bukkit.getPlayer(v.dono());
            Location l = alvo.getLocation().add(0, alvo.getHeight() * 0.6, 0);
            Fx.p(alvo.getWorld(), Particle.ENTITY_EFFECT, l, 6, 0.3, 0.4, 0.3, 0, Color.fromRGB(70, 200, 40));
            Fx.p(alvo.getWorld(), Particle.ITEM_SLIME, l, 2, 0.2, 0.3, 0.2, 0);
            // dano do veneno ignora os ticks de invulnerabilidade (senao nao entra junto com os golpes)
            int inv = alvo.getNoDamageTicks();
            alvo.setNoDamageTicks(0);
            try {
                if (dono != null) pl.causarDano(alvo, v.danoSegundo(), dono);
                else {
                    boolean antigo = pl.emDanoPoder;
                    pl.emDanoPoder = true;
                    try {
                        alvo.damage(v.danoSegundo());
                    } finally {
                        pl.emDanoPoder = antigo;
                    }
                }
            } finally {
                if (alvo.isValid() && !alvo.isDead()) alvo.setNoDamageTicks(Math.min(inv, alvo.getNoDamageTicks()));
            }
        }
    }

    /** Texto para o /poderes e para a escolha da classe. */
    String descricao(Player p) {
        return "§2Passiva Seiva Venenosa: §7cada ataque seu envenena o monstro por §a" + segundos() + " s§7, tirando §a" + Poderes.num(danoAtual(p))
                + " §7de vida por segundo (chega a §a" + Poderes.num(danoMax()) + "§7/s no nível " + nivelMaximo() + ").";
    }
}
