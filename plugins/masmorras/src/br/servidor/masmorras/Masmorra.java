package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Uma masmorra construida: onde fica, o que e protegido, salas, baus e o estado do chefe. */
final class Masmorra {

    final int id;
    final Tipo tipo;
    final String mundo;
    final long semente;
    final List<Caixa> protecao = new ArrayList<>();
    final List<Caixa> salas = new ArrayList<>();
    final List<int[]> baus = new ArrayList<>();
    int saidaX, saidaY, saidaZ;
    /** Quando o chefe volta (milissegundos); 0 = esta la esperando. */
    long bossVolta;
    /** Caixa que cobre tudo (para olhar rapido). */
    Caixa tudo;
    /** Versao do desenho (1 = primeira, pequena). */
    int versao = 1;
    /**
     * Blindagem: o terreno em volta das salas (e o alicerce/ilha embaixo) tambem nao quebra, para ninguem cavar ate
     * as salas por fora nem desmanchar a masmorra.
     */
    Caixa blindagem;
    /** Sendo desmanchada (versao antiga): nao protege nem enche de monstros. */
    boolean demolindo;

    // ---- so enquanto o servidor esta ligado
    boolean[] armada;
    Chefe chefe;
    /** Abertura do Herobrine (musica, vultos, apagao) antes de ele aparecer. */
    org.bukkit.scheduler.BukkitTask intro;
    final Set<UUID> lacaios = new HashSet<>();
    long semNinguemDesde = -1;

    Masmorra(int id, Tipo tipo, String mundo, long semente) {
        this.id = id;
        this.tipo = tipo;
        this.mundo = mundo;
        this.semente = semente;
    }

    Caixa arena() {
        return salas.get(salas.size() - 1);
    }

    void fechar() {
        Caixa t = null;
        for (Caixa c : protecao) t = t == null ? c : t.juntar(c);
        Caixa s = null;
        for (Caixa c : salas) s = s == null ? c : s.juntar(c);
        if (s != null) {
            if (tipo.estilo == Tipo.Estilo.TORRE) {
                // a torre inteira, as ameias e o alicerce ate 45 blocos para baixo (e a frente da porta)
                blindagem = new Caixa(s.x1() - 2, s.y1() - 45, s.z1() - 2, s.x2() + 2, s.y2() + 3, s.z2() + 6);
            } else if (tipo.onde == Tipo.Onde.END) {
                Caixa b = s.crescer(3);
                blindagem = new Caixa(b.x1(), b.y1() - 16, b.z1(), b.x2(), b.y2(), b.z2());
            } else blindagem = s.crescer(3);
            t = t == null ? blindagem : t.juntar(blindagem);
        }
        tudo = t;
        armada = new boolean[salas.size()];
        java.util.Arrays.fill(armada, true);
    }

    /** O bloco faz parte da masmorra (nao pode quebrar nem colocar)? */
    boolean protege(int x, int y, int z) {
        if (demolindo || tudo == null || !tudo.contem(x, y, z)) return false;
        if (blindagem != null && blindagem.contem(x, y, z)) return true;
        for (Caixa c : protecao) if (c.contem(x, y, z)) return true;
        return false;
    }

    /** Ponto dentro da masmorra ou da blindagem (para o "sem PvP"). */
    boolean naArea(double x, double y, double z) {
        if (demolindo || tudo == null || !tudo.contem(x, y, z)) return false;
        if (blindagem != null && blindagem.contem(x, y, z)) return true;
        for (Caixa c : protecao) if (c.contem(x, y, z)) return true;
        return false;
    }

    /** Em qual sala o ponto esta (-1 = nenhuma). */
    int sala(double x, double y, double z) {
        for (int i = 0; i < salas.size(); i++) if (salas.get(i).contem(x, y, z)) return i;
        return -1;
    }

    boolean dentro(double x, double y, double z) {
        if (demolindo || tudo == null || !tudo.contem(x, y, z)) return false;
        for (Caixa c : protecao) if (c.contem(x, y, z)) return true;
        return false;
    }

    static Masmorra criar(int id, Tipo tipo, String mundo, long semente, Planta p, Construtor.Obra o) {
        Masmorra m = new Masmorra(id, tipo, mundo, semente);
        m.protecao.addAll(o.protecao);
        m.salas.addAll(p.salas);
        m.baus.addAll(o.baus);
        m.saidaX = o.saidaX;
        m.saidaY = o.saidaY;
        m.saidaZ = o.saidaZ;
        m.versao = Planta.VERSAO;
        m.fechar();
        return m;
    }

    void salvar(ConfigurationSection s) {
        s.set("tipo", tipo.id);
        s.set("mundo", mundo);
        s.set("semente", semente);
        s.set("saida", saidaX + "," + saidaY + "," + saidaZ);
        s.set("chefe-volta", bossVolta);
        s.set("versao", versao);
        List<String> l = new ArrayList<>();
        for (Caixa c : salas) l.add(c.texto());
        s.set("salas", l);
        l = new ArrayList<>();
        for (Caixa c : protecao) l.add(c.texto());
        s.set("protecao", l);
        l = new ArrayList<>();
        for (int[] b : baus) l.add(b[0] + "," + b[1] + "," + b[2] + "," + b[3]);
        s.set("baus", l);
    }

    static Masmorra ler(int id, ConfigurationSection s) {
        Tipo t = Tipo.pegar(s.getString("tipo", ""));
        if (t == null) return null;
        Masmorra m = new Masmorra(id, t, s.getString("mundo", "world"), s.getLong("semente"));
        String[] sa = s.getString("saida", "0,0,0").split(",");
        m.saidaX = Integer.parseInt(sa[0].trim());
        m.saidaY = Integer.parseInt(sa[1].trim());
        m.saidaZ = Integer.parseInt(sa[2].trim());
        m.bossVolta = s.getLong("chefe-volta", 0);
        m.versao = s.getInt("versao", 1);
        for (String c : s.getStringList("salas")) m.salas.add(Caixa.ler(c));
        for (String c : s.getStringList("protecao")) m.protecao.add(Caixa.ler(c));
        for (String b : s.getStringList("baus")) {
            String[] p = b.split(",");
            m.baus.add(new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())});
        }
        if (m.salas.isEmpty() || m.protecao.isEmpty()) return null;
        m.fechar();
        return m;
    }
}
