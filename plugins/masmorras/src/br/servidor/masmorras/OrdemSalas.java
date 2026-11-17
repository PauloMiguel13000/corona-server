package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 17/11 (Paulo: "o pessoal ta correndo as masmorras sem limpar os monstros anteriores, so correm reto e vao no boss"):
 * a proxima sala (e a arena do chefe) so abre quando nao sobra monstro vivo das salas anteriores que ja foram
 * ativadas. Quem tenta passar volta para onde estava (meio segundo antes), os monstros que faltam brilham e o aviso diz
 * quantos sao. Para nao travar ninguem: monstro que ficou preso longe vem ate a sala depois de 2,5 min e some depois
 * de 5 min. Sala que nunca foi ativada nao conta (nao tranca por causa de sala sem porta).
 */
final class OrdemSalas implements Listener {

    private final Masmorras pl;
    private final Map<UUID, Location> ultimoOk = new HashMap<>();
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();
    private final Map<String, Long> ultimoPuxao = new HashMap<>();

    OrdemSalas(Masmorras pl) {
        this.pl = pl;
    }

    boolean ativo() {
        return pl.getConfig().getBoolean("salas.limpar-antes", true);
    }

    /** Monstros vivos de cada sala (das que ja foram ativadas). */
    private Map<Integer, List<LivingEntity>> vivosPorSala(Masmorra m) {
        Map<Integer, List<LivingEntity>> r = new HashMap<>();
        for (Entity x : pl.lacaiosVivos(m)) {
            Integer s = m.daSala.get(x.getUniqueId());
            if (s == null || !(x instanceof LivingEntity le)) continue;
            r.computeIfAbsent(s, k -> new ArrayList<>()).add(le);
        }
        return r;
    }

    /** true = pode ficar onde esta. false = foi mandado de volta. */
    boolean conferir(Player p, Masmorra m, int s, long agora) {
        if (!ativo() || p.getGameMode() == GameMode.CREATIVE || p.hasPermission("masmorras.admin")) {
            ultimoOk.put(p.getUniqueId(), p.getLocation());
            return true;
        }
        if (s <= 0) {   // primeira sala ou corredor
            ultimoOk.put(p.getUniqueId(), p.getLocation());
            return true;
        }
        Map<Integer, List<LivingEntity>> vivos = vivosPorSala(m);
        int pendente = -1;
        for (int r = 0; r < s; r++) {
            List<LivingEntity> l = vivos.get(r);
            if (l != null && !l.isEmpty()) {
                pendente = r;
                break;
            }
        }
        if (pendente < 0) {
            ultimoOk.put(p.getUniqueId(), p.getLocation());
            return true;
        }
        List<LivingEntity> faltam = vivos.get(pendente);
        // nao trava para sempre: monstro preso longe vem ate a sala; muito tempo depois, some
        long desde = m.armadaEm != null && pendente < m.armadaEm.length ? m.armadaEm[pendente] : 0;
        if (desde > 0 && agora - desde > pl.getConfig().getLong("salas.sumir-depois-ms", 300_000L)) {
            for (LivingEntity le : faltam) le.remove();
            pl.getLogger().info("Ordem das salas: " + faltam.size() + " monstro(s) presos da sala " + (pendente + 1) + " da masmorra #" + m.id + " sumiram (5 min)");
            ultimoOk.put(p.getUniqueId(), p.getLocation());
            return true;
        }
        String chave = m.id + ":" + pendente;
        Long pux = ultimoPuxao.get(chave);
        if (desde > 0 && agora - desde > pl.getConfig().getLong("salas.puxar-depois-ms", 150_000L) && (pux == null || agora - pux > 30_000)) {
            ultimoPuxao.put(chave, agora);
            Caixa c = m.salas.get(pendente);
            Location meio = new Location(p.getWorld(), c.cx() + 0.5, c.y1(), c.cz() + 0.5);
            for (LivingEntity le : faltam) {
                le.teleport(meio);
                if (le instanceof Mob mob) mob.setTarget(p);
            }
        }
        // volta para onde estava
        Location volta = ultimoOk.get(p.getUniqueId());
        if (volta == null || !volta.getWorld().equals(p.getWorld()) || m.sala(volta.getX(), volta.getY(), volta.getZ()) >= s) {
            Caixa c = m.salas.get(pendente);
            volta = new Location(p.getWorld(), c.cx() + 0.5, c.y1(), c.cz() + 0.5);
        }
        volta = volta.clone();
        volta.setYaw(p.getLocation().getYaw());
        volta.setPitch(p.getLocation().getPitch());
        p.setVelocity(new org.bukkit.util.Vector());
        p.teleport(volta);
        for (LivingEntity le : faltam) {
            le.setGlowing(true);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (le.isValid()) le.setGlowing(false);
            }, 200L);
        }
        p.sendActionBar(Masmorras.txt("§c§lTRANCADO! §fMate os §c" + faltam.size() + " §fmonstro" + (faltam.size() == 1 ? "" : "s") + " da sala " + (pendente + 1) + " §7(estão brilhando)"));
        Long u = ultimoAviso.get(p.getUniqueId());
        if (u == null || agora - u > 6000) {
            ultimoAviso.put(p.getUniqueId(), agora);
            boolean arena = s == m.salas.size() - 1;
            p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§c" + (arena ? "A sala do chefe" : "A próxima sala") + " só abre quando a anterior estiver limpa. §7Faltam §f"
                    + faltam.size() + " §7monstro" + (faltam.size() == 1 ? "" : "s") + " na sala " + (pendente + 1) + "."));
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.8f);
        }
        return false;
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        ultimoOk.remove(e.getPlayer().getUniqueId());
        ultimoAviso.remove(e.getPlayer().getUniqueId());
    }
}
