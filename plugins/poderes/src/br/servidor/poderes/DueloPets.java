package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Duelo de pets: luta simulada entre os pets do BabyPets de dois jogadores. */
final class DueloPets implements Listener {

    private static final long CONVITE_MS = 60_000L;
    private static final double DISTANCIA_MAX = 30.0;
    private static final int RODADAS_MAX = 30;

    private final Poderes plugin;
    /** convidado -> desafiante */
    private final Map<UUID, UUID> convites = new HashMap<>();
    private final Map<UUID, Long> conviteQuando = new HashMap<>();
    private final Map<UUID, Duelo> emDuelo = new HashMap<>();
    private final List<Duelo> ativos = new ArrayList<>();

    private final class Lado {
        final Player dono;
        final PetsPonte.PetInfo pet;
        final double vidaMax;
        double vida;
        final BossBar barra;

        Lado(Player dono, PetsPonte.PetInfo pet) {
            this.dono = dono;
            this.pet = pet;
            this.vidaMax = (20.0 + pet.nivel * 4.0) * pet.multiplicador();
            this.vida = vidaMax;
            this.barra = BossBar.bossBar(Poderes.txt(titulo()), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        }

        String titulo() {
            return pet.corRaridade() + pet.nome + " §7de " + dono.getName() + " §f" + Math.max(0, (int) Math.ceil(vida))
                    + "/" + (int) Math.ceil(vidaMax) + " §c❤ §8(Nv " + pet.nivel + ")";
        }

        void atualizar() {
            float prog = (float) Math.max(0.0, Math.min(1.0, vida / vidaMax));
            barra.progress(prog);
            barra.name(Poderes.txt(titulo()));
            barra.color(prog > 0.5f ? BossBar.Color.GREEN : prog > 0.25f ? BossBar.Color.YELLOW : BossBar.Color.RED);
        }
    }

    private final class Duelo {
        final Lado a;
        final Lado b;
        int rodada = 0;
        BukkitTask tarefa;

        Duelo(Lado a, Lado b) {
            this.a = a;
            this.b = b;
        }
    }

    DueloPets(Poderes plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ comando

    boolean comando(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Somente jogadores.");
            return true;
        }
        if (!p.hasPermission("poderes.duelo")) {
            p.sendMessage("§cSem permissao.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage("§6Duelo de pets: §f/duelopet <jogador> §7| §f/duelopet aceitar §7| §f/duelopet recusar");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("aceitar")) {
            aceitar(p);
            return true;
        }
        if (sub.equals("recusar")) {
            UUID de = convites.remove(p.getUniqueId());
            conviteQuando.remove(p.getUniqueId());
            if (de == null) {
                p.sendMessage("§7Voce nao tem desafio pendente.");
                return true;
            }
            Player desafiante = Bukkit.getPlayer(de);
            if (desafiante != null) desafiante.sendMessage("§c" + p.getName() + " recusou o duelo de pets.");
            p.sendMessage("§7Desafio recusado.");
            return true;
        }
        Player alvo = Bukkit.getPlayerExact(args[0]);
        if (alvo == null) {
            p.sendMessage("§cJogador nao encontrado: " + args[0]);
            return true;
        }
        if (alvo.equals(p)) {
            p.sendMessage("§cVoce nao pode duelar contra voce mesmo.");
            return true;
        }
        if (emDuelo.containsKey(p.getUniqueId()) || emDuelo.containsKey(alvo.getUniqueId())) {
            p.sendMessage("§cAlguem ja esta em um duelo.");
            return true;
        }
        String erro = validar(p, alvo);
        if (erro != null) {
            p.sendMessage(erro);
            return true;
        }
        convites.put(alvo.getUniqueId(), p.getUniqueId());
        conviteQuando.put(alvo.getUniqueId(), System.currentTimeMillis());
        p.sendMessage("§6Desafio enviado para " + alvo.getName() + ". §7Ele tem 60s para aceitar.");
        alvo.sendMessage("§6⚔ " + p.getName() + " desafiou o seu pet para um duelo!");
        alvo.sendMessage("§fDigite §a/duelopet aceitar §fou §c/duelopet recusar");
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        return true;
    }

    private String validar(Player a, Player b) {
        PetsPonte.PetInfo pa = plugin.pets.petAtivo(a);
        PetsPonte.PetInfo pb = plugin.pets.petAtivo(b);
        if (pa == null) return "§c" + a.getName() + " precisa estar com um pet ativo (/pets).";
        if (pb == null) return "§c" + b.getName() + " precisa estar com um pet ativo (/pets).";
        if (!a.getWorld().equals(b.getWorld()) || a.getLocation().distance(b.getLocation()) > DISTANCIA_MAX) {
            return "§cVoces precisam estar a ate " + (int) DISTANCIA_MAX + " blocos um do outro.";
        }
        return null;
    }

    private void aceitar(Player p) {
        UUID de = convites.remove(p.getUniqueId());
        Long quando = conviteQuando.remove(p.getUniqueId());
        if (de == null || quando == null || System.currentTimeMillis() - quando > CONVITE_MS) {
            p.sendMessage("§7Voce nao tem desafio pendente (ou ele expirou).");
            return;
        }
        Player desafiante = Bukkit.getPlayer(de);
        if (desafiante == null) {
            p.sendMessage("§7Quem te desafiou saiu do servidor.");
            return;
        }
        if (emDuelo.containsKey(p.getUniqueId()) || emDuelo.containsKey(desafiante.getUniqueId())) {
            p.sendMessage("§cAlguem ja esta em um duelo.");
            return;
        }
        String erro = validar(desafiante, p);
        if (erro != null) {
            p.sendMessage(erro);
            desafiante.sendMessage(erro);
            return;
        }
        iniciar(desafiante, p);
    }

    // ------------------------------------------------------------------ luta

    private void iniciar(Player pa, Player pb) {
        PetsPonte.PetInfo petA = plugin.pets.petAtivo(pa);
        PetsPonte.PetInfo petB = plugin.pets.petAtivo(pb);
        if (petA == null || petB == null) {
            pa.sendMessage("§cO duelo nao comecou: um dos pets nao esta ativo.");
            pb.sendMessage("§cO duelo nao comecou: um dos pets nao esta ativo.");
            return;
        }
        Lado a = new Lado(pa, petA);
        Lado b = new Lado(pb, petB);
        Duelo d = new Duelo(a, b);
        emDuelo.put(pa.getUniqueId(), d);
        emDuelo.put(pb.getUniqueId(), d);
        ativos.add(d);
        for (Player p : new Player[] {pa, pb}) {
            p.showBossBar(a.barra);
            p.showBossBar(b.barra);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        }
        avisarPerto(pa.getLocation(), "§6⚔ Duelo de pets: " + a.pet.corRaridade() + a.pet.nome + " §7(" + pa.getName()
                + ") §6vs " + b.pet.corRaridade() + b.pet.nome + " §7(" + pb.getName() + ")§6!");
        d.tarefa = Bukkit.getScheduler().runTaskTimer(plugin, () -> rodada(d), 40L, 20L);
    }

    private void rodada(Duelo d) {
        if (!valido(d.a) || !valido(d.b)) {
            terminar(d, null, "§7Duelo de pets cancelado (alguem saiu ou o pet sumiu).");
            return;
        }
        d.rodada++;
        atacar(d.a, d.b);
        if (d.b.vida <= 0) {
            terminar(d, d.a, null);
            return;
        }
        atacar(d.b, d.a);
        if (d.a.vida <= 0) {
            terminar(d, d.b, null);
            return;
        }
        if (d.rodada >= RODADAS_MAX) {
            Lado vencedor = d.a.vida / d.a.vidaMax >= d.b.vida / d.b.vidaMax ? d.a : d.b;
            terminar(d, vencedor, null);
        }
    }

    private boolean valido(Lado l) {
        return l.dono.isOnline() && l.pet.entidade.isValid() && !l.pet.entidade.isDead();
    }

    private void atacar(Lado atk, Lado def) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LivingEntity ea = atk.pet.entidade;
        LivingEntity ed = def.pet.entidade;
        World w = ed.getWorld();
        Location ld = ed.getLocation().add(0, ed.getHeight() / 2.0, 0);

        if (ea.getWorld().equals(w)) {
            Vector v = ed.getLocation().toVector().subtract(ea.getLocation().toVector());
            if (v.lengthSquared() > 0.01) {
                Vector impulso = v.normalize().multiply(0.5);
                impulso.setY(0.3);
                ea.setVelocity(impulso);
            }
        }

        if (rnd.nextDouble() < 0.10) {
            Fx.p(w, Particle.CLOUD, ld, 10, 0.3, 0.3, 0.3, 0.02);
            w.playSound(ld, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1.2f);
            Poderes.barra(atk.dono, "§7" + def.pet.nome + " desviou!");
            Poderes.barra(def.dono, "§a" + def.pet.nome + " desviou!");
            return;
        }
        double dano = (2.0 + atk.pet.nivel * 0.8) * atk.pet.multiplicador() * (0.8 + rnd.nextDouble() * 0.4);
        boolean critico = rnd.nextDouble() < 0.15;
        if (critico) dano *= 1.7;
        def.vida -= dano;
        def.atualizar();
        ed.playHurtAnimation(0f);
        Fx.p(w, Particle.CRIT, ld, critico ? 25 : 10, 0.3, 0.3, 0.3, 0.2);
        w.playSound(ld, critico ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.1f);
        if (critico) {
            Poderes.barra(atk.dono, "§6CRITICO! §f" + atk.pet.nome + " acertou " + String.format(Locale.ROOT, "%.1f", dano));
            Poderes.barra(def.dono, "§cCritico em " + def.pet.nome + "!");
        }
    }

    private void terminar(Duelo d, Lado vencedor, String motivo) {
        if (d.tarefa != null) d.tarefa.cancel();
        ativos.remove(d);
        emDuelo.remove(d.a.dono.getUniqueId());
        emDuelo.remove(d.b.dono.getUniqueId());
        for (Lado l : new Lado[] {d.a, d.b}) {
            l.dono.hideBossBar(d.a.barra);
            l.dono.hideBossBar(d.b.barra);
        }
        if (vencedor == null) {
            if (motivo != null) {
                d.a.dono.sendMessage(motivo);
                d.b.dono.sendMessage(motivo);
            }
            return;
        }
        Lado perdedor = vencedor == d.a ? d.b : d.a;
        String msg = "§6⚔ " + vencedor.pet.corRaridade() + vencedor.pet.nome + " §6de §f" + vencedor.dono.getName()
                + " §6venceu o duelo contra " + perdedor.pet.corRaridade() + perdedor.pet.nome + " §6de §f"
                + perdedor.dono.getName() + "§6!";
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        plugin.getLogger().info(msg.replaceAll("§.", ""));
        LivingEntity ev = vencedor.pet.entidade;
        if (ev.isValid()) {
            Fx.p(ev.getWorld(), Particle.TOTEM_OF_UNDYING, ev.getLocation().add(0, 0.8, 0), 40, 0.4, 0.6, 0.4, 0.3);
        }
        vencedor.dono.playSound(vencedor.dono.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        perdedor.dono.playSound(perdedor.dono.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
    }

    private void avisarPerto(Location l, String msg) {
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) <= 60 * 60) p.sendMessage(msg);
        }
    }

    void cancelarTodos() {
        for (Duelo d : new ArrayList<>(ativos)) terminar(d, null, "§7Duelo de pets cancelado.");
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        convites.remove(id);
        conviteQuando.remove(id);
        Duelo d = emDuelo.get(id);
        if (d != null) terminar(d, null, "§7Duelo de pets cancelado: " + e.getPlayer().getName() + " saiu.");
    }
}
