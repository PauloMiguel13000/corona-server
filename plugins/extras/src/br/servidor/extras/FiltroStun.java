package br.servidor.extras;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;

/**
 * O NetherNet do Geyser escreve "STUN discovery through ..." no log a cada 15 segundos (duas linhas por vez).
 * Este filtro deixa passar so uma dessas mensagens a cada X minutos. Avisos e erros passam sempre.
 * O log4j pode ter mais de um "contexto" (um por carregador de plugin), entao o filtro entra em todos e
 * confere de tempos em tempos se apareceu contexto novo ou se a configuracao do log foi recarregada.
 * Fica numa classe separada: se o log4j mudar um dia, so o filtro deixa de funcionar, o resto do plugin segue.
 */
final class FiltroStun extends AbstractFilter {

    static final String ORIGEM = "org.cloudburstmc.netty.util.nethernet.StunObserver";

    private final long intervaloMs;
    private volatile long ultima;
    private volatile long escondidas;

    private FiltroStun(long intervaloMs) {
        super(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL);
        this.intervaloMs = intervaloMs;
    }

    /** Decide se a mensagem aparece (separado do log4j para dar para testar). */
    boolean mostrar(String origem, Level nivel, long agora) {
        if (!ORIGEM.equals(origem)) return true;
        if (nivel != null && nivel.isMoreSpecificThan(Level.WARN)) return true;
        if (intervaloMs > 0 && agora - ultima >= intervaloMs) {
            ultima = agora;
            return true;
        }
        escondidas++;
        return false;
    }

    long escondidas() {
        return escondidas;
    }

    @Override
    public Result filter(LogEvent e) {
        if (e == null) return Result.NEUTRAL;
        return mostrar(e.getLoggerName(), e.getLevel(), System.currentTimeMillis()) ? Result.NEUTRAL : Result.DENY;
    }

    static FiltroStun novo(long intervaloMs) {
        return new FiltroStun(intervaloMs);
    }

    /** Todos os contextos de log que o log4j conhece agora. */
    static List<LoggerContext> contextos() {
        Set<LoggerContext> s = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            if (LogManager.getFactory() instanceof Log4jContextFactory f) s.addAll(f.getSelector().getLoggerContexts());
        } catch (Throwable ignored) {
        }
        try {
            if (LogManager.getContext(false) instanceof LoggerContext c) s.add(c);
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader pai = FiltroStun.class.getClassLoader().getParent();
            if (pai != null && LogManager.getContext(pai, false) instanceof LoggerContext c) s.add(c);
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(s);
    }

    static boolean temFiltro(LoggerConfig lc, Filter f) {
        Filter atual = lc.getFilter();
        if (atual == f) return true;
        return atual instanceof CompositeFilter cf && Arrays.asList(cf.getFiltersArray()).contains(f);
    }

    /** Coloca o filtro onde ainda nao tem. Devolve quantos contextos ganharam o filtro agora. */
    int aplicar() {
        int n = 0;
        for (LoggerContext c : contextos()) {
            LoggerConfig raiz = c.getConfiguration().getRootLogger();
            if (temFiltro(raiz, this)) continue;
            raiz.addFilter(this);
            n++;
        }
        return n;
    }

    /** Texto curto para o log: em quantos contextos esta e se algum ja tem o logger do NetherNet. */
    String resumo() {
        int total = 0, com = 0;
        boolean achou = false;
        for (LoggerContext c : contextos()) {
            total++;
            if (temFiltro(c.getConfiguration().getRootLogger(), this)) com++;
            if (c.hasLogger(ORIGEM)) achou = true;
        }
        return "filtro em " + com + " de " + total + " contexto(s) de log" + (achou ? ", logger do NetherNet encontrado" : "");
    }

    /** intervaloMs = 0 esconde todas. Devolve o filtro instalado (para tirar depois). */
    static Object instalar(long intervaloMs) {
        FiltroStun f = new FiltroStun(intervaloMs);
        f.aplicar();
        return f;
    }

    static int reaplicar(Object f) {
        return f instanceof FiltroStun fs ? fs.aplicar() : 0;
    }

    static String resumo(Object f) {
        return f instanceof FiltroStun fs ? fs.resumo() + ", " + fs.escondidas() + " mensagem(ns) escondida(s)" : "";
    }

    static void remover(Object f) {
        if (!(f instanceof FiltroStun fs)) return;
        for (LoggerContext c : contextos()) c.getConfiguration().getRootLogger().removeFilter(fs);
    }
}
