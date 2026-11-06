package br.servidor.backrooms;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Sorridente (Nivel 1): um sorriso branco que brilha no escuro. Fica parado. Se voce olhar para ele
 * ou chegar perto demais, ele grita e vem voando na sua direcao. Quebre a linha de visao (vire uma esquina) para ele sumir.
 */
final class Sorridente {

    enum Estado { ESPREITA, AVISO, ATAQUE, SUMIDO }

    private static final String ROSTO = "§f§l●    ●\n§f§l╰━━━━╯";

    private final Backrooms pl;
    private final Partida jogo;
    private final World mundo;
    private final Nivel nivel;
    private final Random rnd = new Random();

    private TextDisplay ent;
    Estado estado = Estado.SUMIDO;
    private UUID alvo;
    private long tick, ateQuando, semVer;
    private Location pos;

    Sorridente(Backrooms pl, Partida jogo, World mundo, Nivel nivel) {
        this.pl = pl;
        this.jogo = jogo;
        this.mundo = mundo;
        this.nivel = nivel;
    }

    private static long seg(double s) {
        return Math.round(s * 4);
    }

    boolean eh(Entity e) {
        return e != null && ent != null && e.getUniqueId().equals(ent.getUniqueId());
    }

    Location local() {
        return ent != null && ent.isValid() ? ent.getLocation() : null;
    }

    void remover() {
        if (ent != null) ent.remove();
        ent = null;
        estado = Estado.SUMIDO;
        alvo = null;
    }

    private void aparecer(Location l) {
        remover();
        pos = l.clone();
        ent = mundo.spawn(l, TextDisplay.class, t -> {
            t.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
            t.text(Backrooms.txt(ROSTO));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setPersistent(false);
            t.setViewRange(0.6f);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f()));
        });
        estado = Estado.ESPREITA;
    }

    /** 4x por segundo. */
    void tick(long t, List<Player> vivos) {
        tick = t;
        switch (estado) {
            case SUMIDO -> {
                if (t < ateQuando) return;
                Location l = jogo.escuroLonge(nivel, vivos, 18, 45);
                if (l != null) aparecer(l.add(0, 1.3, 0));
                else ateQuando = t + seg(5);
            }
            case ESPREITA -> {
                if (ent == null || !ent.isValid()) {
                    sumir(5);
                    return;
                }
                // longe de todo mundo por muito tempo: muda de lugar
                double perto = 1e9;
                for (Player p : vivos) perto = Math.min(perto, p.getLocation().distanceSquared(pos));
                if (perto > 70 * 70) {
                    sumir(2);
                    return;
                }
                double gat = jogo.dif("sorridente-gatilho", 9);
                for (Player p : vivos) {
                    if (!jogo.alvoValido(p)) continue;
                    Location olho = p.getEyeLocation();
                    Vector para = pos.toVector().subtract(olho.toVector());
                    double d = para.length();
                    if (d > gat) continue;
                    double ang = Math.toDegrees(olho.getDirection().angle(para));
                    boolean olhando = ang < 24 && d <= gat, colado = d < 3.5;
                    if ((olhando || colado) && p.hasLineOfSight(ent)) {
                        estado = Estado.AVISO;
                        alvo = p.getUniqueId();
                        ateQuando = t + 2;
                        Sons.local(mundo, pos, 48, "sorridente_grito", 1f, 0.95f + rnd.nextFloat() * 0.1f, Sound.ENTITY_GHAST_SCREAM, 1.6f, 0.5f);
                        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 2f);
                        jogo.titulo(p, "", "§f§lNÃO OLHE PARA ELE... CORRA!", 1.2);
                        ent.text(Backrooms.txt("§f§l◉    ◉\n§f§l╰┻┻┻┻╯"));
                        return;
                    }
                }
                if (t % 14 == 0 && rnd.nextDouble() < 0.25)
                    Sons.localComPausa(mundo, pos, 22, "sorridente_risada", 0.75f, 0.9f + rnd.nextFloat() * 0.2f, Sound.ENTITY_VEX_AMBIENT, 0.5f, 0.4f, 7000);
            }
            case AVISO -> {
                if (t >= ateQuando) {
                    estado = Estado.ATAQUE;
                    semVer = 0;
                    ateQuando = t + seg(jogo.dif("sorridente-tempo", 7));
                }
            }
            case ATAQUE -> {
                Player p = alvo == null ? null : Bukkit.getPlayer(alvo);
                if (p == null || !jogo.alvoValido(p) || ent == null || !ent.isValid() || t >= ateQuando) {
                    sumir(15 + rnd.nextInt(15));
                    return;
                }
                if (!p.hasLineOfSight(ent)) semVer++;
                else semVer = 0;
                if (semVer >= seg(1.5)) {
                    mundo.playSound(pos, Sound.ENTITY_VEX_DEATH, 1.2f, 0.5f);
                    sumir(15 + rnd.nextInt(15));
                    return;
                }
                if (t % 3 == 0) mundo.playSound(pos, Sound.ENTITY_VEX_CHARGE, 1.4f, 0.5f);
            }
        }
    }

    /** Todo tick: o sorriso voa na direcao do alvo (sem atravessar paredes). */
    void mover() {
        if (estado != Estado.ATAQUE || ent == null || !ent.isValid()) return;
        Player p = alvo == null ? null : Bukkit.getPlayer(alvo);
        if (p == null || !p.getWorld().equals(mundo)) return;
        Vector para = p.getEyeLocation().add(0, -0.3, 0).toVector().subtract(pos.toVector());
        double d = para.length();
        if (d < 1.1) {
            if (jogo.alvoValido(p)) jogo.pego(p, "um Sorridente");
            sumir(20 + rnd.nextInt(15));
            return;
        }
        double passo = jogo.dif("sorridente-velocidade", 4.8) / 20.0;
        Vector dir = para.normalize().multiply(Math.min(passo, d));
        Location novo = pos.clone().add(dir);
        if (!livre(novo)) {
            Location sx = pos.clone().add(dir.getX(), dir.getY(), 0), sz = pos.clone().add(0, dir.getY(), dir.getZ());
            if (livre(sx)) novo = sx;
            else if (livre(sz)) novo = sz;
            else return;
        }
        pos = novo;
        ent.teleport(pos);
    }

    private boolean livre(Location l) {
        Block b = l.getBlock();
        return b.isPassable() && nivel.dentro(l);
    }

    private void sumir(double segundos) {
        if (ent != null) ent.remove();
        ent = null;
        alvo = null;
        estado = Estado.SUMIDO;
        ateQuando = tick + seg(segundos);
    }

    void comecar(long t, double atraso) {
        tick = t;
        estado = Estado.SUMIDO;
        ateQuando = t + seg(atraso);
    }

    String status() {
        Location l = local();
        return "sorr=" + estado + (l == null ? "" : String.format("(%.0f %.0f)", l.getX(), l.getZ()));
    }
}
