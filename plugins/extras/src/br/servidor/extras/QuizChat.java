package br.servidor.extras;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 14/11 (Paulo: "melhore muito"): PERGUNTA RELAMPAGO no chat — a cada ~25 min (2+ online) aparece uma conta ou uma
 * palavra embaralhada; o primeiro que responder certo no chat ganha coronas. 60 s para responder.
 */
final class QuizChat implements Listener {
    static final String[] PALAVRAS = {"diamante", "creeper", "esmeralda", "masmorra", "picareta", "obsidiana", "enderman", "redstone",
            "netherite", "aldeao", "esqueleto", "tridente", "fornalha", "encantamento", "dragao", "coronas", "prisao", "piñata", "lhama", "cogumelo"};
    private final Extras pl;
    private volatile String resposta;
    private volatile long ate;
    private int premio;

    QuizChat(Extras pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, () -> perguntar(false), 20L * 60 * 12, 20L * 60 * Math.max(10, pl.getConfig().getInt("quiz-chat.minutos", 25)));
    }

    void perguntar() { perguntar(false); }

    void perguntar(boolean forcar) {
        if (!forcar && (!pl.getConfig().getBoolean("quiz-chat.ativo", true) || Bukkit.getOnlinePlayers().size() < 2)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String pergunta;
        if (r.nextBoolean()) {
            int a = r.nextInt(12, 99), b = r.nextInt(3, 40);
            switch (r.nextInt(3)) {
                case 0 -> { pergunta = "Quanto é §f" + a + " + " + b + "§e?"; resposta = String.valueOf(a + b); }
                case 1 -> { pergunta = "Quanto é §f" + a + " - " + b + "§e?"; resposta = String.valueOf(a - b); }
                default -> { int c = r.nextInt(3, 13), d = r.nextInt(3, 13); pergunta = "Quanto é §f" + c + " x " + d + "§e?"; resposta = String.valueOf(c * d); }
            }
            premio = 30;
        } else {
            String w = PALAVRAS[r.nextInt(PALAVRAS.length)];
            List<Character> cs = new ArrayList<>();
            for (char ch : w.toCharArray()) cs.add(ch);
            String emb;
            do { Collections.shuffle(cs); StringBuilder sb = new StringBuilder(); for (char ch : cs) sb.append(ch); emb = sb.toString(); } while (emb.equals(w));
            pergunta = "Desembaralhe: §f§l" + emb.toUpperCase();
            resposta = w;
            premio = 50;
        }
        ate = System.currentTimeMillis() + 60_000L;
        Bukkit.broadcast(Extras.txt("§e§l⚡ PERGUNTA RELÂMPAGO! §e" + pergunta + " §7(o 1º a responder no chat ganha §6" + premio + " coronas§7)"));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (resposta != null && System.currentTimeMillis() >= ate) {
                Bukkit.broadcast(Extras.txt("§7⚡ Ninguém acertou! A resposta era §f" + resposta + "§7."));
                resposta = null;
            }
        }, 20L * 61);
    }

    private static String norm(String s) {
        return java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        String r = resposta;
        if (r == null || System.currentTimeMillis() > ate) return;
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (!norm(msg).equals(norm(r))) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (resposta == null || !norm(resposta).equals(norm(r))) return;   // alguem ja levou
            resposta = null;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + premio);
            Bukkit.broadcast(Extras.txt("§e⚡ §f" + p.getName() + " §eacertou primeiro (§f" + r + "§e) e ganhou §6" + premio + " coronas§e!"));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        });
    }
}
