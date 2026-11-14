package br.servidor.extras;

import java.time.LocalDate;
import java.time.ZoneId;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** 13/11 (Paulo: "faca ele ficar fodao"): premio por entrar todo dia, que cresce com a sequencia (7 dias e recomeca). */
final class LoginDiario implements Listener {
    static final long[] PREMIO = {50, 75, 100, 150, 200, 300, 600};
    private final Extras pl;
    private final NamespacedKey kDia, kSeq;

    LoginDiario(Extras pl) {
        this.pl = pl;
        kDia = new NamespacedKey(pl, "login_dia");
        kSeq = new NamespacedKey(pl, "login_seq");
    }

    static long hojeEpoch() { return LocalDate.now(ZoneId.of("America/Sao_Paulo")).toEpochDay(); }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        final int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(pl, t -> {
            if (!p.isOnline() || ++n[0] > 120) { t.cancel(); return; }
            if (!Extras.logado(p) || p.getWorld().getName().equals("login")) return;
            t.cancel();
            Bukkit.getScheduler().runTaskLater(pl, () -> premiar(p), 60L);
        }, 100L, 100L);
    }

    void premiar(Player p) {
        if (!p.isOnline() || !pl.getConfig().getBoolean("login-diario.ligado", true)) return;
        PersistentDataContainer c = p.getPersistentDataContainer();
        long hoje = hojeEpoch();
        Long ult = c.get(kDia, PersistentDataType.LONG);
        if (ult != null && ult == hoje) return;
        // primeiro login do dia: mostra o que tem de especial hoje
        if (pl.painelDia != null) Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) for (String l : pl.painelDia.texto().split("\n")) p.sendMessage(Extras.txt(l)); }, 80L);
        int seq = (ult != null && ult == hoje - 1) ? c.getOrDefault(kSeq, PersistentDataType.INTEGER, 0) + 1 : 1;
        c.set(kDia, PersistentDataType.LONG, hoje);
        c.set(kSeq, PersistentDataType.INTEGER, seq);
        int dia = (seq - 1) % 7;
        long v = PREMIO[dia];
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
        p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§6§l🔥 LOGIN DIÁRIO"), Extras.txt("§fdia §e" + (dia + 1) + "/7 §8• §6+" + v + " coronas")));
        StringBuilder barra = new StringBuilder();
        for (int i = 0; i < 7; i++) barra.append(i < dia ? "§a■" : i == dia ? "§6§l■" : "§8■");
        p.sendMessage(Extras.txt("§6§l🔥 Login diário §8» §fsequência de §e" + seq + (seq == 1 ? " dia" : " dias") + " §8» §6+" + v + " coronas §8 " + barra));
        p.sendMessage(Extras.txt(dia < 6 ? "§7Volte amanhã para ganhar §6" + PREMIO[dia + 1] + " coronas§7. No §6dia 7§7: §6§l" + PREMIO[6] + " coronas§7!"
                : "§6§l★ Você fechou os 7 dias! §7Amanhã a sequência recomeça — continue vindo!"));
        p.playSound(p.getLocation(), dia == 6 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        // 14/11: bonus de volta para quem sumiu 5+ dias
        if (ult != null && hoje - ult >= 5) {
            long volta = Math.min(600, 150 + (hoje - ult) * 15);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + volta);
                p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt("§a§lBEM-VINDO DE VOLTA!"), Extras.txt("§7sentimos sua falta §8• §6+" + volta + " coronas")));
                p.sendMessage(Extras.txt("§a§l✦ Bem-vindo de volta! §7Você ficou §f" + (hoje - ult) + " dias §7fora e ganhou §6" + volta + " coronas§7. Muita coisa nova: §e/ajuda"));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
            }, 120L);
        }
        if (dia == 6) {
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            Bukkit.broadcast(Extras.txt("§6🔥 §e" + p.getName() + " §7entrou §67 dias seguidos §7e ganhou §6" + PREMIO[6] + " coronas§7!"));
        }
    }
}
