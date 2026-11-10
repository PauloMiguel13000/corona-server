package br.servidor.extras;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * O corpo animado do boss mundial: 8 pecas do pacote (tronco, cabeca, 2 bracos, 2 pernas, 2 asas), cada uma um
 * ItemDisplay que gira em volta do seu pivo. A cada 3 ticks calcula a pose (andar, respirar, bater as asas e os 4
 * ataques) e o cliente desliza de uma pose para a outra (interpolacao), no Java e no Bedrock.
 *
 * Contas no espaco da entidade: +z = frente, +y = cima. O item do ItemDisplay e desenhado virado 180 graus, entao o
 * lado +x do modelo fica no -x da entidade (a espada fica na mao direita do boss).
 */
final class ColossoCorpo {

    enum Golpe { NENHUM, ESMAGAR, INVESTIDA, INVOCAR, MAGMA, ESPADA }

    /** nome no pacote e pivo em pixels do modelo (igual ao colosso3d.py). */
    private record Peca(String nome, float px, float py, float pz, boolean deCima) {}

    private static final Peca[] PECAS = {
            new Peca("tronco", 8f, -3f, 8f, true), new Peca("cabeca", 8f, 12f, 8f, true),
            new Peca("braco_d", 15.4f, 9f, 8f, true), new Peca("braco_e", 0.6f, 9f, 8f, true),
            new Peca("perna_d", 10.55f, -3f, 8f, false), new Peca("perna_e", 5.45f, -3f, 8f, false),
            new Peca("asa_d", 11f, 9f, 12.6f, true), new Peca("asa_e", 5f, 9f, 12.6f, true)};
    private static final int TRONCO = 0, CABECA = 1, BRACO_D = 2, BRACO_E = 3, PERNA_D = 4, PERNA_E = 5, ASA_D = 6, ASA_E = 7;
    private static final int PASSO = 3;   // ticks entre uma pose e outra

    private final Plugin pl;
    private final float esc;
    private final ItemDisplay[] pecas = new ItemDisplay[PECAS.length];
    private Golpe golpe = Golpe.NENHUM;
    private int golpeDesde, golpeDura;
    private double fase, faseLenta;
    private boolean bocaAberta;
    private Location ultimo;

    ColossoCorpo(Plugin pl, float esc) {
        this.pl = pl;
        this.esc = esc;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static ItemStack item(String peca, int quadro) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/anim_colosso_" + peca + "_f" + quadro));
        return it;
    }

    /** Onde fica o pivo da peca, em blocos, a partir dos pes do boss. */
    private Vector3f lugar(Peca p) {
        return new Vector3f(-(p.px() - 8f) / 16f * esc, (p.py() + 16f) / 16f * esc, -(p.pz() - 8f) / 16f * esc);
    }

    void nascer(Location l) {
        sumir();
        for (int i = 0; i < PECAS.length; i++) {
            final Peca p = PECAS[i];
            pecas[i] = l.getWorld().spawn(l, ItemDisplay.class, d -> {
                d.setItemStack(item(p.nome(), 0));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(new Transformation(lugar(p), new Quaternionf(), new Vector3f(esc, esc, esc), new Quaternionf()));
                d.setBrightness(new Display.Brightness(12, 15));
                d.setViewRange(2.5f);
                d.setShadowRadius(0f);
                d.setTeleportDuration(2);
                d.setInterpolationDuration(PASSO);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
            });
        }
        ultimo = l.clone();
    }

    boolean existe() {
        for (ItemDisplay d : pecas) if (d == null || !d.isValid()) return false;
        return true;
    }

    void sumir() {
        for (int i = 0; i < pecas.length; i++) {
            if (pecas[i] != null && pecas[i].isValid()) pecas[i].remove();
            pecas[i] = null;
        }
    }

    /** Mostra ou esconde o corpo 3D para o jogador. */
    void ver(Player p, boolean sim) {
        for (ItemDisplay d : pecas) {
            if (d == null || !d.isValid() || sim == p.canSee(d)) continue;
            if (sim) p.showEntity(pl, d);
            else p.hideEntity(pl, d);
        }
    }

    void golpe(Golpe g, int ticksAgora, int dura) {
        golpe = g;
        golpeDesde = ticksAgora;
        golpeDura = dura;
    }

    private static Quaternionf frente(double graus) {
        // membro pendurado vai para a frente / cabeca olha para cima
        return new Quaternionf().rotateX((float) Math.toRadians(-graus));
    }

    private static float suave(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Chamado a cada tick: leva as pecas junto com o corpo e, a cada 3 ticks, calcula a pose nova. */
    void tick(Location corpo, float yawCorpo, int ticks) {
        if (!existe()) return;
        Location l = corpo.clone();
        l.setYaw(yawCorpo);
        l.setPitch(0);
        for (ItemDisplay d : pecas) d.teleport(l);
        if (ticks % PASSO != 0) return;
        double andou = ultimo == null || ultimo.getWorld() != l.getWorld() ? 0 : Math.hypot(l.getX() - ultimo.getX(), l.getZ() - ultimo.getZ());
        ultimo = l.clone();
        boolean andando = andou > 0.04;
        fase += andando ? 0.62 : 0;
        faseLenta += 0.16;
        float s = (float) Math.sin(fase), lento = (float) Math.sin(faseLenta);

        // pose de base: respirando parado ou andando
        float pernaD = andando ? 26 * s : 0, pernaE = -pernaD;
        float bracoD = andando ? -9 * s : 2 * lento, bracoE = andando ? 16 * s : -2 * lento;
        float bracoDFora = 0, bracoEFora = 0;
        float bracoDGira = 0, torcao = 0;   // giro do braco da espada e torcao do tronco
        float asa = 7 + 7 * lento, asaFrente = 0;
        float inclina = andando ? 4 : 0;
        float cabecaCima = 0, cabecaLado = andando ? 0 : 9 * (float) Math.sin(faseLenta * 0.5);
        float sobe = andando ? 0.05f * esc * Math.abs(s) : 0.015f * esc * lento;
        boolean boca = false;

        int t = ticks - golpeDesde;
        if (golpe != Golpe.NENHUM && t >= golpeDura) golpe = Golpe.NENHUM;
        switch (golpe) {
            case ESMAGAR -> {
                // ergue a espada e o escudo por 1,3 s e desce tudo de uma vez
                if (t < 26) {
                    float k = suave(t / 22f);
                    bracoD = 165 * k;
                    bracoE = 150 * k;
                    inclina = -10 * k;
                    cabecaCima = 18 * k;
                    asa = 12 + 30 * k;
                    boca = true;
                } else {
                    bracoD = 55;
                    bracoE = 50;
                    inclina = 24;
                    cabecaCima = -14;
                    asa = 46;
                }
                pernaD = 10;
                pernaE = -10;
            }
            case INVESTIDA -> {
                float k = suave(t / 6f);
                inclina = 30 * k;
                asa = 20 + 38 * k;
                asaFrente = -18 * k;
                bracoD = -35 * k;
                bracoE = 40 * k;
                pernaD = 34 * k;
                pernaE = -30 * k;
                cabecaCima = 22 * k;
                boca = true;
            }
            case INVOCAR -> {
                float k = suave(t / 10f), treme = (float) Math.sin(t * 0.9) * 4;
                bracoE = 125 * k + treme;
                bracoEFora = -18 * k;
                bracoD = 18 * k;
                inclina = -7 * k;
                cabecaCima = 26 * k;
                asa = 14 + 26 * k + treme;
                boca = true;
            }
            case MAGMA -> {
                float k = suave(t / 10f), treme = (float) Math.sin(t * 1.1) * 5;
                bracoD = 172 * k;
                bracoDFora = 10 * k;
                bracoE = -12 * k;
                cabecaCima = 20 * k;
                asa = 16 + 22 * k + treme;
                boca = true;
            }
            case ESPADA -> {
                // montante: arma o golpe por 0,6 s e varre a frente de uma vez so
                if (t < 12) {
                    float k = suave(t / 12f);
                    bracoD = 95 * k;
                    bracoDFora = -42 * k;
                    bracoDGira = -78 * k;
                    torcao = -17 * k;
                    inclina = -6 * k;
                    cabecaLado = -14 * k;
                    asa = 12 + 24 * k;
                    boca = true;
                } else if (t < 21) {
                    float k = suave((t - 12) / 7f);
                    bracoD = 95 - 28 * k;
                    bracoDFora = -42 + 14 * k;
                    bracoDGira = -78 + 156 * k;
                    torcao = -17 + 36 * k;
                    inclina = -6 + 20 * k;
                    cabecaLado = -14 + 30 * k;
                    asa = 36 + 18 * k;
                    boca = true;
                } else {
                    float k = suave((t - 21) / 12f);
                    bracoD = 67 - 40 * k;
                    bracoDFora = -28 + 24 * k;
                    bracoDGira = 78 - 62 * k;
                    torcao = 19 - 15 * k;
                    inclina = 14 - 12 * k;
                    cabecaLado = 16 - 13 * k;
                    asa = 54 - 34 * k;
                }
                pernaD = 15;
                pernaE = -9;
            }
            default -> { }
        }

        // tronco: inclina para a frente e torce no golpe de espada (a parte de cima toda acompanha)
        Quaternionf inc = new Quaternionf().rotateY((float) Math.toRadians(torcao)).mul(frente(-inclina));
        Vector3f quadril = lugar(PECAS[TRONCO]).add(0, sobe, 0);
        Quaternionf[] giro = new Quaternionf[PECAS.length];
        giro[TRONCO] = new Quaternionf();
        giro[CABECA] = new Quaternionf().rotateY((float) Math.toRadians(cabecaLado)).mul(frente(cabecaCima));
        giro[BRACO_D] = new Quaternionf().rotateY((float) Math.toRadians(bracoDGira)).mul(new Quaternionf().rotateZ((float) Math.toRadians(-bracoDFora))).mul(frente(bracoD));
        giro[BRACO_E] = new Quaternionf().rotateZ((float) Math.toRadians(-bracoEFora)).mul(frente(bracoE));
        giro[PERNA_D] = frente(pernaD);
        giro[PERNA_E] = frente(pernaE);
        giro[ASA_D] = new Quaternionf().rotateZ((float) Math.toRadians(asa)).mul(frente(asaFrente));
        giro[ASA_E] = new Quaternionf().rotateZ((float) Math.toRadians(-asa)).mul(frente(asaFrente));
        for (int i = 0; i < PECAS.length; i++) {
            Peca p = PECAS[i];
            Vector3f pos;
            Quaternionf q;
            if (p.deCima()) {
                // a parte de cima acompanha a inclinacao do tronco (gira em volta do quadril)
                pos = inc.transform(lugar(p).sub(lugar(PECAS[TRONCO]))).add(quadril);
                q = new Quaternionf(inc).mul(giro[i]);
            } else {
                pos = lugar(p);
                q = giro[i];
            }
            ItemDisplay d = pecas[i];
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(PASSO);
            d.setTransformation(new Transformation(pos, q, new Vector3f(esc, esc, esc), new Quaternionf()));
        }
        if (boca != bocaAberta) {
            bocaAberta = boca;
            pecas[CABECA].setItemStack(item("cabeca", boca ? 1 : 0));
        }
    }

    List<ItemDisplay> todas() {
        List<ItemDisplay> l = new ArrayList<>();
        for (ItemDisplay d : pecas) if (d != null) l.add(d);
        return l;
    }
}
