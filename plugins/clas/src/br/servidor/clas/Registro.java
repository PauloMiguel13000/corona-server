package br.servidor.clas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Os grupos e clas (so os dados e as regras, sem mensagens): da para testar sem servidor.
 * Cada operacao devolve null quando deu certo, ou o motivo (texto para o jogador) quando nao deu.
 */
final class Registro {

    static final class Grupo {
        UUID lider;
        final LinkedHashSet<UUID> membros = new LinkedHashSet<>();
    }

    static final class Cla {
        String tag, nome;
        UUID dono;
        final Set<UUID> oficiais = new HashSet<>();
        final LinkedHashSet<UUID> membros = new LinkedHashSet<>();
        /** Base: "mundo,x,y,z,yaw,pitch" (ou null). */
        String base;
        boolean fogoAmigo;
        long criado;

        boolean manda(UUID u) {
            return u.equals(dono) || oficiais.contains(u);
        }
    }

    int maxGrupo = 8, maxCla = 20, tagMin = 2, tagMax = 5, nomeMax = 24;
    long conviteGrupoMs = 120_000, conviteClaMs = 300_000;

    final Map<UUID, Grupo> grupoDe = new HashMap<>();
    final Map<String, Cla> clas = new LinkedHashMap<>();
    final Map<UUID, Cla> claDe = new HashMap<>();
    final Map<UUID, String> nomes = new HashMap<>();
    /** Convites: convidado -> (quem convidou -> expira). */
    final Map<UUID, Map<UUID, Long>> convitesGrupo = new HashMap<>();
    /** Convites de cla: convidado -> (TAG -> expira). */
    final Map<UUID, Map<String, Long>> convitesCla = new HashMap<>();

    String nome(UUID u) {
        return nomes.getOrDefault(u, u.toString().substring(0, 8));
    }

    // ================================================================== aliados

    /** Mesmo grupo, ou mesmo cla (com o fogo amigo desligado): um nao machuca o outro. */
    boolean aliados(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        Grupo g = grupoDe.get(a);
        if (g != null && g == grupoDe.get(b)) return true;
        Cla c = claDe.get(a);
        return c != null && c == claDe.get(b) && !c.fogoAmigo;
    }

    // ================================================================== grupo

    String convidarGrupo(UUID de, UUID para, long agora) {
        if (de.equals(para)) return "Você não pode se convidar.";
        Grupo g = grupoDe.get(de);
        if (g != null && !g.lider.equals(de)) return "Só o líder do grupo (" + nome(g.lider) + ") convida.";
        if (g != null && g.membros.size() >= maxGrupo) return "O grupo está cheio (" + maxGrupo + ").";
        Grupo dele = grupoDe.get(para);
        if (dele != null && dele == g) return nome(para) + " já está no seu grupo.";
        if (dele != null) return nome(para) + " já está em outro grupo.";
        convitesGrupo.computeIfAbsent(para, k -> new HashMap<>()).put(de, agora + conviteGrupoMs);
        return null;
    }

    /** Quem convidou (o primeiro convite valido, ou o de 'de' se informado). */
    UUID conviteGrupo(UUID para, UUID de, long agora) {
        Map<UUID, Long> m = convitesGrupo.get(para);
        if (m == null) return null;
        m.values().removeIf(t -> t < agora);
        if (de != null) return m.containsKey(de) ? de : null;
        UUID melhor = null;
        long t = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> e : m.entrySet()) if (e.getValue() > t) {
            t = e.getValue();
            melhor = e.getKey();
        }
        return melhor;
    }

    String aceitarGrupo(UUID para, UUID de, long agora) {
        UUID quem = conviteGrupo(para, de, agora);
        if (quem == null) return de == null ? "Você não tem convite de grupo (ou já expirou)." : "Você não tem convite de " + nome(de) + " (ou já expirou).";
        if (grupoDe.containsKey(para)) return "Saia do seu grupo antes (/grupo sair).";
        Grupo g = grupoDe.get(quem);
        if (g == null) {
            g = new Grupo();
            g.lider = quem;
            g.membros.add(quem);
            grupoDe.put(quem, g);
        } else if (!g.lider.equals(quem)) return nome(quem) + " não é mais líder do grupo.";
        if (g.membros.size() >= maxGrupo) return "O grupo está cheio (" + maxGrupo + ").";
        g.membros.add(para);
        grupoDe.put(para, g);
        convitesGrupo.remove(para);
        return null;
    }

    /** Sai do grupo. Se era o lider, passa para o proximo; grupo com 1 so acaba. Devolve o grupo (ou null). */
    Grupo sairGrupo(UUID u) {
        Grupo g = grupoDe.remove(u);
        if (g == null) return null;
        g.membros.remove(u);
        if (g.lider.equals(u) && !g.membros.isEmpty()) g.lider = g.membros.iterator().next();
        if (g.membros.size() <= 1) {
            for (UUID x : g.membros) grupoDe.remove(x);
        }
        return g;
    }

    String expulsarGrupo(UUID lider, UUID alvo) {
        Grupo g = grupoDe.get(lider);
        if (g == null) return "Você não está em um grupo.";
        if (!g.lider.equals(lider)) return "Só o líder expulsa.";
        if (lider.equals(alvo)) return "Para sair, use /grupo sair.";
        if (grupoDe.get(alvo) != g) return nome(alvo) + " não está no seu grupo.";
        sairGrupo(alvo);
        return null;
    }

    String liderGrupo(UUID lider, UUID novo) {
        Grupo g = grupoDe.get(lider);
        if (g == null) return "Você não está em um grupo.";
        if (!g.lider.equals(lider)) return "Só o líder passa a liderança.";
        if (grupoDe.get(novo) != g) return nome(novo) + " não está no seu grupo.";
        g.lider = novo;
        return null;
    }

    // ================================================================== cla

    static String limparTag(String t) {
        return t == null ? "" : t.toUpperCase(Locale.ROOT);
    }

    String criarCla(UUID dono, String tag, String nome, long agora) {
        if (claDe.containsKey(dono)) return "Você já está no clã [" + claDe.get(dono).tag + "]. Saia antes (/clan sair).";
        tag = limparTag(tag);
        if (!tag.matches("[A-Z0-9]{" + tagMin + "," + tagMax + "}")) return "A tag precisa ter de " + tagMin + " a " + tagMax + " letras ou números (ex.: ABC).";
        if (clas.containsKey(tag)) return "Já existe um clã com a tag [" + tag + "].";
        nome = nome == null ? "" : nome.trim().replaceAll("[§&]", "");
        if (nome.isEmpty()) nome = tag;
        if (nome.length() > nomeMax) return "O nome pode ter no máximo " + nomeMax + " letras.";
        for (Cla c : clas.values()) if (c.nome.equalsIgnoreCase(nome)) return "Já existe um clã com esse nome.";
        Cla c = new Cla();
        c.tag = tag;
        c.nome = nome;
        c.dono = dono;
        c.membros.add(dono);
        c.criado = agora;
        clas.put(tag, c);
        claDe.put(dono, c);
        return null;
    }

    String convidarCla(UUID de, UUID para, long agora) {
        Cla c = claDe.get(de);
        if (c == null) return "Você não está em um clã.";
        if (!c.manda(de)) return "Só o dono e os oficiais convidam.";
        if (de.equals(para)) return "Você já está no clã.";
        if (claDe.get(para) == c) return nome(para) + " já está no clã.";
        if (claDe.containsKey(para)) return nome(para) + " já está em outro clã.";
        if (c.membros.size() >= maxCla) return "O clã está cheio (" + maxCla + ").";
        convitesCla.computeIfAbsent(para, k -> new HashMap<>()).put(c.tag, agora + conviteClaMs);
        return null;
    }

    String conviteCla(UUID para, String tag, long agora) {
        Map<String, Long> m = convitesCla.get(para);
        if (m == null) return null;
        m.values().removeIf(t -> t < agora);
        if (tag != null) {
            String t = limparTag(tag);
            return m.containsKey(t) ? t : null;
        }
        String melhor = null;
        long t = Long.MIN_VALUE;
        for (Map.Entry<String, Long> e : m.entrySet()) if (e.getValue() > t) {
            t = e.getValue();
            melhor = e.getKey();
        }
        return melhor;
    }

    String aceitarCla(UUID para, String tag, long agora) {
        String t = conviteCla(para, tag, agora);
        if (t == null) return "Você não tem convite " + (tag == null ? "de clã" : "do clã [" + limparTag(tag) + "]") + " (ou já expirou).";
        if (claDe.containsKey(para)) return "Saia do seu clã antes (/clan sair).";
        Cla c = clas.get(t);
        if (c == null) return "Esse clã não existe mais.";
        if (c.membros.size() >= maxCla) return "O clã está cheio (" + maxCla + ").";
        c.membros.add(para);
        claDe.put(para, c);
        convitesCla.remove(para);
        return null;
    }

    /** Sai do cla. O dono so sai passando o cla (ou desfazendo). */
    String sairCla(UUID u) {
        Cla c = claDe.get(u);
        if (c == null) return "Você não está em um clã.";
        if (u.equals(c.dono)) {
            if (c.membros.size() > 1) return "Você é o dono: passe o clã antes (/clan dono <nick>) ou desfaça (/clan desfazer).";
            desfazerCla(c);
            return null;
        }
        c.membros.remove(u);
        c.oficiais.remove(u);
        claDe.remove(u);
        return null;
    }

    String expulsarCla(UUID quem, UUID alvo) {
        Cla c = claDe.get(quem);
        if (c == null) return "Você não está em um clã.";
        if (!c.manda(quem)) return "Só o dono e os oficiais expulsam.";
        if (claDe.get(alvo) != c) return nome(alvo) + " não está no clã.";
        if (alvo.equals(c.dono)) return "O dono não pode ser expulso.";
        if (alvo.equals(quem)) return "Para sair, use /clan sair.";
        if (c.oficiais.contains(alvo) && !quem.equals(c.dono)) return "Só o dono expulsa um oficial.";
        c.membros.remove(alvo);
        c.oficiais.remove(alvo);
        claDe.remove(alvo);
        return null;
    }

    String promover(UUID dono, UUID alvo, boolean subir) {
        Cla c = claDe.get(dono);
        if (c == null) return "Você não está em um clã.";
        if (!dono.equals(c.dono)) return "Só o dono muda os oficiais.";
        if (claDe.get(alvo) != c || alvo.equals(dono)) return nome(alvo) + " não é membro do clã.";
        if (subir) {
            if (!c.oficiais.add(alvo)) return nome(alvo) + " já é oficial.";
        } else if (!c.oficiais.remove(alvo)) return nome(alvo) + " não é oficial.";
        return null;
    }

    String passarDono(UUID dono, UUID novo) {
        Cla c = claDe.get(dono);
        if (c == null) return "Você não está em um clã.";
        if (!dono.equals(c.dono)) return "Só o dono passa o clã.";
        if (claDe.get(novo) != c || novo.equals(dono)) return nome(novo) + " não é membro do clã.";
        c.dono = novo;
        c.oficiais.remove(novo);
        c.oficiais.add(dono);
        return null;
    }

    void desfazerCla(Cla c) {
        for (UUID u : c.membros) claDe.remove(u);
        clas.remove(c.tag);
        for (Map<String, Long> m : convitesCla.values()) m.remove(c.tag);
    }

    // ================================================================== salvar e ler (so os clas: grupos sao temporarios)

    void salvar(YamlConfiguration y) {
        y.set("clas", null);
        for (Cla c : clas.values()) {
            ConfigurationSection s = y.createSection("clas." + c.tag);
            s.set("nome", c.nome);
            s.set("dono", c.dono.toString());
            List<String> l = new ArrayList<>();
            for (UUID u : c.oficiais) l.add(u.toString());
            s.set("oficiais", l);
            l = new ArrayList<>();
            for (UUID u : c.membros) l.add(u.toString());
            s.set("membros", l);
            s.set("base", c.base);
            s.set("fogo-amigo", c.fogoAmigo);
            s.set("criado", c.criado);
        }
        y.set("nomes", null);
        for (Cla c : clas.values()) for (UUID u : c.membros) if (nomes.containsKey(u)) y.set("nomes." + u, nomes.get(u));
    }

    void ler(YamlConfiguration y) {
        clas.clear();
        claDe.clear();
        ConfigurationSection n = y.getConfigurationSection("nomes");
        if (n != null) for (String k : n.getKeys(false)) {
            try {
                nomes.put(UUID.fromString(k), n.getString(k));
            } catch (IllegalArgumentException ignored) {}
        }
        ConfigurationSection s = y.getConfigurationSection("clas");
        if (s == null) return;
        for (String tag : s.getKeys(false)) {
            ConfigurationSection cs = s.getConfigurationSection(tag);
            if (cs == null) continue;
            try {
                Cla c = new Cla();
                c.tag = limparTag(tag);
                c.nome = cs.getString("nome", c.tag);
                c.dono = UUID.fromString(cs.getString("dono", ""));
                for (String u : cs.getStringList("membros")) c.membros.add(UUID.fromString(u));
                for (String u : cs.getStringList("oficiais")) c.oficiais.add(UUID.fromString(u));
                c.membros.add(c.dono);
                c.base = cs.getString("base");
                c.fogoAmigo = cs.getBoolean("fogo-amigo", false);
                c.criado = cs.getLong("criado", 0);
                clas.put(c.tag, c);
                for (UUID u : c.membros) if (!claDe.containsKey(u)) claDe.put(u, c);
            } catch (IllegalArgumentException ignored) {
                // cla com dado estragado: ignora
            }
        }
    }
}
