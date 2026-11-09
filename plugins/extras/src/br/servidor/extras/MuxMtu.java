package br.servidor.extras;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MTU das conexoes NetherNet que chegam pelo Warden (endereco prettyshore, celulares pelo playit).
 *
 * O canal de admissao do NetherNet (NativeAdmissionServerChannel) cria a configuracao de cada conexao a partir de
 * PeerConnectionConfiguration.DEFAULT (MTU automatico = 1280 no libdatachannel) e ignora a opcao do canal. O UDP mux
 * (IceUdpMuxListener) pergunta ao "handler" se aceita cada conexao nova e usa a configuracao da resposta (Acceptance)
 * para criar a conexao. Aqui o handler e embrulhado: a resposta sai igual, so com o MTU trocado.
 */
final class MuxMtu implements InvocationHandler {
    private final Object original;
    volatile int mtu;
    final AtomicLong trocadas = new AtomicLong();
    private static volatile boolean avisouSemMux;

    private MuxMtu(Object original, int mtu) {
        this.original = original;
        this.mtu = mtu;
    }

    /** Embrulha (ou atualiza) o handler do mux do canal. Devolve um texto para o log quando algo mudou, senao null. */
    static String aplicar(Object canal, int mtu) throws Exception {
        Object mux = acharPorTipo(canal, "tel.schich.libdatachannel.IceUdpMuxListener", 3);
        if (mux == null) {
            if (mtu > 0 && !avisouSemMux) {
                avisouSemMux = true;
                return "mux nao encontrado em " + canal.getClass().getName();
            }
            return null;
        }
        Field hf = campo(mux.getClass(), "handler");
        hf.setAccessible(true);
        Object h = hf.get(mux);
        if (h == null) return null;
        if (Proxy.isProxyClass(h.getClass()) && Proxy.getInvocationHandler(h) instanceof MuxMtu atual) {
            if (atual.mtu == mtu) return null;
            int antes = atual.mtu;
            atual.mtu = mtu;
            return "mux " + (antes == 0 ? "auto" : antes) + " -> " + (mtu == 0 ? "auto" : mtu);
        }
        if (mtu <= 0) return null;   // nada a fazer: segue o padrao
        ClassLoader cl = mux.getClass().getClassLoader();
        Class<?> hi = Class.forName("tel.schich.libdatachannel.IceUdpMuxListener$Handler", true, cl);
        MuxMtu mm = new MuxMtu(h, mtu);
        Object prox = Proxy.newProxyInstance(cl, new Class<?>[] {hi}, mm);
        hf.set(mux, prox);
        return "mux auto -> " + mtu;
    }

    /** Quantas conexoes ja sairam com o MTU trocado (para o /extras rede). */
    static long trocadas(Object canal) {
        try {
            Object mux = acharPorTipo(canal, "tel.schich.libdatachannel.IceUdpMuxListener", 3);
            if (mux == null) return -1;
            Field hf = campo(mux.getClass(), "handler");
            hf.setAccessible(true);
            Object h = hf.get(mux);
            if (h != null && Proxy.isProxyClass(h.getClass()) && Proxy.getInvocationHandler(h) instanceof MuxMtu mm) return mm.trocadas.get();
        } catch (Throwable ignored) {
        }
        return -1;
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        switch (m.getName()) {
            case "equals":
                return args != null && args.length == 1 && args[0] == proxy;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "MuxMtu[" + mtu + "] " + original;
            default:
                break;
        }
        Object r;
        try {
            m.setAccessible(true);
            r = m.invoke(original, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        if (!"onRequest".equals(m.getName()) || !(r instanceof CompletionStage<?> cs)) return r;
        return cs.thenApply(acc -> {
            ajustar(acc);
            return acc;
        });
    }

    private void ajustar(Object acc) {
        int alvo = mtu;
        if (acc == null || alvo <= 0) return;
        try {
            Field cf = campo(acc.getClass(), "configuration");
            cf.setAccessible(true);
            Object cfg = cf.get(acc);
            if (cfg == null) return;   // conexao reaproveitada (existingPeer): nao tem configuracao
            Field mf = campo(cfg.getClass(), "mtu");
            mf.setAccessible(true);
            if (mf.getInt(cfg) == alvo) return;
            Object novo = cfg.getClass().getMethod("withMtu", int.class).invoke(cfg, alvo);
            cf.set(acc, novo);
            trocadas.incrementAndGet();
        } catch (Throwable t) {
            // se der errado, a conexao segue com o padrao (nunca derruba a entrada)
        }
    }

    private static Object acharPorTipo(Object o, String tipo, int fundo) throws IllegalAccessException {
        if (o == null || fundo < 0) return null;
        if (o.getClass().getName().equals(tipo)) return o;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                if (f.getType().getName().equals(tipo)) {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    private static Field campo(Class<?> c0, String nome) throws NoSuchFieldException {
        for (Class<?> c = c0; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(nome);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(nome);
    }
}
