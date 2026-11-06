package br.servidor.poderes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Animacoes de enfeite das habilidades, feitas com displays (blocos e itens que crescem, giram e voam com
 * interpolacao no proprio jogo, sem custo por tick): aneis que se abrem, estilhacos, espinhos que brotam,
 * pulsos de energia, raios em zigue-zague, cortes, itens orbitando e textos. Tudo passa pelo Visuais:
 * nada fica salvo no mundo, nada causa dano e tudo some sozinho.
 */
final class Efeitos {

    private final Poderes pl;

    Efeitos(Poderes pl) {
        this.pl = pl;
    }

    private Visuais vis() {
        return pl.vis;
    }

    static BlockData b(Material m) {
        return m.createBlockData();
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    private void depois(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(pl, r, Math.max(1L, ticks));
    }

    /** Cubo de lado 's' centrado em 'centro' (relativo ao display), girado por 'q'. */
    static Transformation cubo(Vector3f centro, float s, Quaternionf q) {
        Vector3f t = new Vector3f(-s / 2f, -s / 2f, -s / 2f).rotate(new Quaternionf(q)).add(centro);
        return new Transformation(t, new Quaternionf(q), new Vector3f(s), new Quaternionf());
    }

    /** Caixa (largura x altura x largura) com a base em 'base' (relativo), girada por 'q'. */
    static Transformation coluna(Vector3f base, float largura, float altura, Quaternionf q) {
        Vector3f t = new Vector3f(-largura / 2f, 0, -largura / 2f).rotate(new Quaternionf(q)).add(base);
        return new Transformation(t, new Quaternionf(q), new Vector3f(largura, Math.max(0.001f, altura), largura), new Quaternionf());
    }

    static void mover(Display d, Transformation t, int ticks) {
        if (d == null || !d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(Math.max(0, ticks));
        d.setTransformation(t);
    }

    static Color corDe(BlockData b) {
        String n = b.getMaterial().name();
        if (n.startsWith("LIME")) return Color.fromRGB(120, 255, 90);
        if (n.startsWith("GREEN")) return Color.fromRGB(60, 160, 40);
        if (n.startsWith("LIGHT_GRAY")) return Color.fromRGB(175, 175, 175);
        if (n.startsWith("GRAY")) return Color.fromRGB(110, 110, 115);
        if (n.startsWith("BLACK")) return Color.fromRGB(30, 30, 35);
        if (n.startsWith("LIGHT_BLUE")) return Color.fromRGB(120, 200, 255);
        if (n.startsWith("BLUE")) return Color.fromRGB(50, 90, 230);
        if (n.startsWith("YELLOW")) return Color.fromRGB(255, 225, 60);
        if (n.startsWith("ORANGE")) return Color.fromRGB(255, 140, 30);
        if (n.startsWith("PURPLE")) return Color.fromRGB(150, 60, 220);
        return Color.WHITE;
    }

    private static Quaternionf giroAleatorio() {
        ThreadLocalRandom r = rnd();
        return new Quaternionf().rotationXYZ((float) r.nextDouble(Math.PI * 2), (float) r.nextDouble(Math.PI * 2), (float) r.nextDouble(Math.PI * 2));
    }

    // ------------------------------------------------------------------ aneis, pulsos e estilhacos

    /** Anel de 'pecas' blocos que se abre do centro ate 'raio' em 'ticks' e some. */
    void anel(Location c, BlockData b, double raio, int ticks, int pecas, float tam, Color brilho) {
        pl.cena.gemeoAnel(c, b, raio, ticks, pecas, brilho);
        for (int i = 0; i < pecas; i++) {
            double a = Math.PI * 2 * i / pecas;
            BlockDisplay d = vis().bloco(c, b, ticks + 8, brilho);
            Quaternionf q = new Quaternionf().rotationY((float) -a);
            d.setTransformation(cubo(new Vector3f(), tam * 0.25f, q));
            Vector3f fim = new Vector3f((float) (Math.cos(a) * raio), 0, (float) (Math.sin(a) * raio));
            Quaternionf q2 = new Quaternionf(q).rotateZ(1.2f);
            depois(1, () -> mover(d, cubo(fim, tam, q2), ticks));
            depois(ticks + 1, () -> mover(d, cubo(fim, 0.001f, q2), 5));
        }
    }

    /** Pulso de energia: dois cubos girando em sentidos contrarios que crescem de 's0' ate 's1' e somem. */
    void pulso(Location c, BlockData casca, BlockData miolo, float s0, float s1, int ticks, Color brilho) {
        pl.cena.gemeoPulso(c, casca, miolo, s1, ticks, brilho);
        BlockDisplay a = vis().bloco(c, casca, ticks + 8, brilho);
        BlockDisplay m = miolo != null ? vis().bloco(c, miolo, ticks + 8, null) : null;
        Quaternionf qa0 = new Quaternionf().rotationXYZ(0.6f, 0.3f, 0.2f), qa1 = new Quaternionf().rotationXYZ(2.2f, 1.9f, 1.0f);
        Quaternionf qm0 = new Quaternionf().rotationXYZ(-0.4f, 0.8f, -0.3f), qm1 = new Quaternionf().rotationXYZ(-2.0f, -1.3f, -1.6f);
        a.setTransformation(cubo(new Vector3f(), s0, qa0));
        if (m != null) m.setTransformation(cubo(new Vector3f(), s0 * 0.55f, qm0));
        depois(1, () -> {
            mover(a, cubo(new Vector3f(), s1, qa1), ticks);
            if (m != null) mover(m, cubo(new Vector3f(), s1 * 0.55f, qm1), ticks);
        });
        depois(ticks + 1, () -> {
            mover(a, cubo(new Vector3f(), 0.001f, qa1), 5);
            if (m != null) mover(m, cubo(new Vector3f(), 0.001f, qm1), 4);
        });
    }

    /** Pedacos de bloco que voam do centro para os lados, sobem, caem e somem. */
    void estilhacos(Location c, BlockData[] tipos, int n, double alcance, int ticks, float tam) {
        pl.cena.gemeoEstilhacos(c, tipos, n, alcance, ticks);
        ThreadLocalRandom r = rnd();
        for (int i = 0; i < n; i++) {
            BlockData b = tipos[r.nextInt(tipos.length)];
            BlockDisplay d = vis().bloco(c, b, ticks + 6, null);
            double a = r.nextDouble(Math.PI * 2), dist = alcance * r.nextDouble(0.45, 1.0);
            float s = tam * (float) r.nextDouble(0.6, 1.25);
            Quaternionf q0 = giroAleatorio(), q1 = giroAleatorio(), q2 = giroAleatorio();
            d.setTransformation(cubo(new Vector3f(), s, q0));
            Vector3f meio = new Vector3f((float) (Math.cos(a) * dist * 0.6), (float) r.nextDouble(0.7, 1.7), (float) (Math.sin(a) * dist * 0.6));
            Vector3f fim = new Vector3f((float) (Math.cos(a) * dist), -0.2f, (float) (Math.sin(a) * dist));
            int h = Math.max(2, ticks / 2);
            depois(1, () -> mover(d, cubo(meio, s, q1), h));
            depois(1 + h, () -> mover(d, cubo(fim, s * 0.4f, q2), Math.max(2, ticks - h)));
        }
    }

    /** Espinho/coluna que brota do chao (pode sair inclinado), fica 'ficar' ticks e volta para dentro. */
    BlockDisplay pilar(Location base, BlockData b, double altura, double largura, double inclinacao, int subir, int ficar, Color brilho) {
        pl.cena.gemeoPilar(base, b, altura, subir, ficar);
        BlockDisplay d = vis().bloco(base, b, subir + ficar + 10, brilho);
        double dirInc = rnd().nextDouble(Math.PI * 2);
        Quaternionf q = new Quaternionf().rotationY((float) dirInc).rotateX((float) inclinacao);
        float l = (float) largura;
        d.setTransformation(coluna(new Vector3f(), l, 0.001f, q));
        depois(1, () -> mover(d, coluna(new Vector3f(), l, (float) altura, q), subir));
        depois(1 + subir + ficar, () -> mover(d, coluna(new Vector3f(), l, 0.001f, q), 6));
        return d;
    }

    /** Bolhas que sobem do chao e estouram (veneno, cura). */
    void bolhas(Location c, BlockData b, int n, double raio, double subida, int ticks, Color brilho) {
        pl.cena.gemeoBolhas(c, brilho != null ? brilho : corDe(b), n, raio, subida);
        ThreadLocalRandom r = rnd();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * raio;
            Location pt = c.clone().add(Math.cos(a) * d, r.nextDouble(0.05, 0.4), Math.sin(a) * d);
            float s = (float) r.nextDouble(0.18, 0.38);
            BlockDisplay x = vis().bloco(pt, b, ticks + 6, brilho);
            Quaternionf q = giroAleatorio();
            x.setTransformation(cubo(new Vector3f(), s * 0.3f, q));
            int atraso = r.nextInt(6);
            depois(1 + atraso, () -> mover(x, cubo(new Vector3f(0, (float) (subida * r.nextDouble(0.6, 1.0)), 0), s, giroAleatorio()), ticks - atraso));
            depois(1 + ticks, () -> mover(x, cubo(new Vector3f(0, (float) subida, 0), 0.001f, q), 3));
        }
    }

    // ------------------------------------------------------------------ linhas: raios e cortes

    /** Raio de energia em zigue-zague de 'de' ate 'ate' (barras brilhantes que somem em 'vida' ticks). */
    void raio(Location de, Location ate, BlockData b, int segs, double desvio, double grossura, int vida, Color brilho) {
        pl.cena.gemeoRaio(de, ate, brilho);
        List<Location> pts = new ArrayList<>();
        Vector total = ate.toVector().subtract(de.toVector());
        Vector lado1 = total.clone().crossProduct(new Vector(0, 1, 0));
        if (lado1.lengthSquared() < 0.001) lado1 = new Vector(1, 0, 0);
        lado1.normalize();
        Vector lado2 = total.clone().crossProduct(lado1).normalize();
        pts.add(de.clone());
        for (int i = 1; i < segs; i++) {
            Location p = de.clone().add(total.clone().multiply((double) i / segs));
            p.add(lado1.clone().multiply(rnd().nextDouble(-desvio, desvio))).add(lado2.clone().multiply(rnd().nextDouble(-desvio, desvio)));
            pts.add(p);
        }
        pts.add(ate.clone());
        for (int i = 0; i + 1 < pts.size(); i++) {
            Location a = pts.get(i), c = pts.get(i + 1);
            Vector dir = c.toVector().subtract(a.toVector());
            double len = dir.length();
            if (len < 0.01) continue;
            BlockDisplay d = vis().bloco(a, b, vida + 4, brilho);
            Visuais.barra(d, a, dir, len, grossura, 0);
            depois(vida, () -> Visuais.barra(d, a, dir, len, 0.001, 3));
        }
    }

    /** Corte reto (barra fina e brilhante) que aparece e afina ate sumir. */
    void corte(Location a, Location c, BlockData b, double grossura, int vida, Color brilho) {
        pl.cena.gemeoCorte(a, c, brilho);
        Vector dir = c.toVector().subtract(a.toVector());
        double len = dir.length();
        if (len < 0.01) return;
        BlockDisplay d = vis().bloco(a, b, vida + 4, brilho);
        Visuais.barra(d, a, dir, len, grossura, 0);
        depois(2, () -> Visuais.barra(d, a, dir, len, 0.001, Math.max(1, vida - 2)));
    }

    /** Dois cortes cruzados (X) na frente do alvo. */
    void cortesX(Location centro, Vector frente, double tam, Color brilho) {
        Vector f = frente.clone().setY(0);
        if (f.lengthSquared() < 0.01) f = new Vector(0, 0, 1);
        Vector lado = f.normalize().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = new Vector(0, 1, 0);
        Vector d1 = lado.clone().add(up).normalize().multiply(tam), d2 = lado.clone().subtract(up).normalize().multiply(tam);
        BlockData branco = b(Material.WHITE_STAINED_GLASS);
        corte(centro.clone().subtract(d1.clone().multiply(0.5)), centro.clone().add(d1.clone().multiply(0.5)), branco, 0.09, 7, brilho);
        corte(centro.clone().subtract(d2.clone().multiply(0.5)), centro.clone().add(d2.clone().multiply(0.5)), branco, 0.09, 7, brilho);
    }

    // ------------------------------------------------------------------ coisas que seguem e orbitam

    /** O display acompanha a entidade (projetil) ate ela sumir; no fim chama 'fim' com o ultimo lugar. */
    void seguir(Entity alvo, Display d, int maxTicks, float giroPorTick, float tam, Consumer<Location> fim) {
        if (!(d instanceof ItemDisplay) || !pl.cena.itensNoCelular)
            pl.cena.gemeoSeguir(alvo, d instanceof BlockDisplay bd ? bd.getBlock() : null, d instanceof ItemDisplay id ? id.getItemStack() : new ItemStack(Material.SNOWBALL), maxTicks);
        final Location[] ultima = {alvo.getLocation()};
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!alvo.isValid() || alvo.isDead() || t[0] > maxTicks || !d.isValid()) {
                tarefa.cancel();
                vis().remover(d);
                if (fim != null) fim.accept(ultima[0]);
                return;
            }
            ultima[0] = alvo.getLocation();
            d.teleport(ultima[0]);
            if (giroPorTick != 0) Visuais.escala(d, tam, t[0] * giroPorTick, 1);
            t[0]++;
        }, 1L, 1L);
    }

    /** Itens girando em volta do jogador ('n' copias, raio, altura) por 'ticks'. */
    void orbitar(Player p, ItemStack it, int n, double raio, double altura, double velocidade, float tam, int ticks, Color brilho) {
        pl.cena.gemeoOrbitar(p, it, n, raio, altura, velocidade, ticks);
        List<ItemDisplay> ds = new ArrayList<>();
        for (int i = 0; i < n; i++) ds.add(vis().item(p.getLocation().add(0, altura, 0), it, ticks + 4, brilho));
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!p.isOnline() || p.isDead() || t[0] > ticks) {
                tarefa.cancel();
                for (ItemDisplay d : ds) vis().remover(d);
                return;
            }
            Location c = p.getLocation().add(0, altura, 0);
            double base = t[0] * velocidade;
            for (int i = 0; i < ds.size(); i++) {
                ItemDisplay d = ds.get(i);
                if (!d.isValid()) continue;
                double a = base + Math.PI * 2 * i / ds.size();
                d.teleport(c.clone().add(Math.cos(a) * raio, Math.sin(t[0] * 0.3 + i) * 0.15, Math.sin(a) * raio));
                Quaternionf q = new Quaternionf().rotationY((float) (-a - Math.PI / 2)).rotateZ((float) (t[0] * 0.9));
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(tam), new Quaternionf()));
            }
            t[0]++;
        }, 1L, 1L);
    }

    /** Texto grande que aparece, sobe um pouco e some (ex.: "GRITO!"). */
    void texto(Location l, String txt, float escala, int vida) {
        World w = l.getWorld();
        TextDisplay d = w.spawn(l, TextDisplay.class, x -> {
            x.text(Poderes.txt(txt));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setSeeThrough(false);
            x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(1.0f);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(escala * 0.3f), new Quaternionf()));
        });
        vis().marcarVisual(d, vida + 4);
        depois(1, () -> mover(d, new Transformation(new Vector3f(0, 0.6f, 0), new Quaternionf(), new Vector3f(escala), new Quaternionf()), 6));
        depois(vida - 4, () -> mover(d, new Transformation(new Vector3f(0, 1.1f, 0), new Quaternionf(), new Vector3f(0.001f), new Quaternionf()), 4));
    }

    /** Coluna de luz que desce do ceu e se abre (chegada/teleporte). */
    void colunaDeLuz(Location base, BlockData b, double altura, double largura, int vida, Color brilho) {
        pl.cena.gemeoColuna(base, b, altura, vida, brilho);
        BlockDisplay d = vis().bloco(base, b, vida + 6, brilho);
        float l = (float) largura;
        d.setTransformation(new Transformation(new Vector3f(-l / 2f, (float) altura, -l / 2f), new Quaternionf(), new Vector3f(l, 0.001f, l), new Quaternionf()));
        depois(1, () -> mover(d, coluna(new Vector3f(), l, (float) altura, new Quaternionf()), 3));
        depois(4, () -> mover(d, coluna(new Vector3f(), 0.001f, (float) altura, new Quaternionf()), Math.max(2, vida - 4)));
    }
}
