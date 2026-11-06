package br.servidor.naochore;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * O medo de cada jogador (0 a 1). Sobe de noite, no escuro, com um Vigia por perto (muito mais se ele estiver olhando
 * para voce) e com os dias; desce de dia e na claridade. O medo vira: batimento cardiaco (mais rapido e mais alto),
 * zumbido grave a noite, a visao escurecendo em pulsos, estatica e um sussurro com o seu nome.
 */
final class Medo {
    private final NaoChore pl;
    private final Random rnd = new Random();
    double nivel;
    private long proxBatimento, proxZumbido, proxEstatica, proxSussurro, proxEscuro;

    Medo(NaoChore pl) {
        this.pl = pl;
    }

    /** 4 vezes por segundo. distVigia = distancia do vigia mais perto (ou 999); olhado = tem vigia olhando para ele. */
    void atualizar(Player p, int dia, boolean noite, double distVigia, boolean olhado, long tick) {
        Location l = p.getLocation();
        Block b = l.getBlock();
        int luz = Math.max(b.getLightFromBlocks(), noite ? 0 : b.getLightFromSky());
        double alvo = 0.05 * dia;
        if (noite) alvo += 0.25;
        if (luz < 5) alvo += 0.15;
        if (distVigia < 40) alvo += 0.25 * (1 - distVigia / 40);
        if (olhado) alvo += 0.2;
        if (distVigia < 10) alvo += 0.25;
        alvo = Math.min(1, alvo);
        // sobe rapido, desce devagar
        nivel += (alvo > nivel ? 0.06 : -0.012) * Math.abs(alvo - nivel) * 4 + (alvo > nivel ? 0.004 : -0.002);
        nivel = Math.max(0, Math.min(1, nivel));
        boolean pacote = Sons.temPacote(p);
        // batimento: a partir de 0.25; intervalo de 1,1 s (calmo) a 0,42 s (apavorado)
        if (nivel > 0.25 && tick >= proxBatimento) {
            double intervalo = 1.1 - 0.68 * nivel;
            proxBatimento = tick + Math.max(6, (long) (intervalo * 20));
            float vol = (float) (0.25 + 0.75 * nivel), tom = (float) (0.85 + 0.45 * nivel);
            if (pacote) Sons.para(p, p.getLocation(), "naochore.batimento", vol, tom, null, 0f, 0f);
            else p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, vol * 0.7f, 0.5f);
        }
        // zumbido grave a noite (10 s, emendado)
        if (noite && nivel > 0.2 && tick >= proxZumbido) {
            proxZumbido = tick + 190;
            if (pacote) Sons.para(p, p.getLocation(), "naochore.zumbido_noite", (float) (0.3 + 0.5 * nivel), 1f, null, 0f, 0f);
            else p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, (float) (0.3 + 0.3 * nivel), 0.4f);
        }
        // visao escurecendo em pulsos (medo alto)
        if (nivel > 0.7 && tick >= proxEscuro) {
            proxEscuro = tick + 40 + rnd.nextInt(80);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false, false));
        }
        // estatica + tranco na camera (medo muito alto)
        if (nivel > 0.85 && tick >= proxEstatica && rnd.nextInt(6) == 0) {
            proxEstatica = tick + 60 + rnd.nextInt(100);
            Sons.para(p, p.getLocation(), "naochore.estatica", 0.8f, 1f, Sound.BLOCK_CONDUIT_AMBIENT, 0.6f, 0.4f);
            Location v = p.getLocation();
            v.setPitch(Math.max(-85, Math.min(85, v.getPitch() + (rnd.nextFloat() - 0.5f) * 14)));
            v.setYaw(v.getYaw() + (rnd.nextFloat() - 0.5f) * 10);
            p.teleport(v);
        }
        // sussurra o nome dele (de noite, medo medio para cima)
        if (noite && nivel > 0.5 && tick >= proxSussurro && rnd.nextInt(8) == 0) {
            proxSussurro = tick + 20 * (60 + rnd.nextInt(90));
            Location atras = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.2)).add(0, 1.5, 0);
            Sons.para(p, atras, "naochore.sussurro_nome", 1f, 1f, Sound.ENTITY_ENDERMAN_AMBIENT, 0.6f, 0.4f);
            p.sendActionBar(NaoChore.txt("§8§o" + p.getName().toLowerCase() + "... não chore..."));
        }
    }

    /** Pico de medo (susto): vai ao maximo na hora. */
    void susto() {
        nivel = 1;
    }
}
