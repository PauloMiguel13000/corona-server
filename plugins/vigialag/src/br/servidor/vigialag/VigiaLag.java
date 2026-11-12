package br.servidor.vigialag;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Vigia de travadas: uma thread a parte olha o relogio do tick; se ele para, anota a pilha da thread principal. */
public final class VigiaLag extends JavaPlugin {
    private volatile long ultimoTick;
    private long piorTick;
    private volatile boolean ligado;
    private volatile int jogadores;
    /** Perfil por amostragem (so com jogador online): onde a thread principal estava, 20 vezes por segundo. */
    private final java.util.HashMap<String, int[]> perfil = new java.util.HashMap<>(), porPlugin = new java.util.HashMap<>();
    private int amostras;
    private Thread principal, vigia;

    @Override public void onEnable() {
        saveDefaultConfig();
        final long limite = Math.max(100, getConfig().getLong("limite-ms", 400)) * 1_000_000L;
        final long intervalo = Math.max(1, getConfig().getLong("intervalo-segundos", 5)) * 1_000_000_000L;
        final long espera = Math.max(0, getConfig().getLong("espera-inicial-segundos", 240)) * 1_000_000_000L;
        final long resumoSeg = Math.max(10, getConfig().getLong("resumo-segundos", 300));
        principal = Thread.currentThread();
        ultimoTick = System.nanoTime();
        ligado = true;
        final long ligadoEm = System.nanoTime();
        final File perfilArq = new File(getDataFolder(), "perfil.log");
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long agora = System.nanoTime();
            long d = agora - ultimoTick;
            if (d > piorTick && agora - ligadoEm > 240_000_000_000L) piorTick = d;
            ultimoTick = agora;
            jogadores = Bukkit.getOnlinePlayers().size();
        }, 1L, 1L);
        // resumo a cada 5 minutos: jogadores, TPS, tempo medio e pior tick, entidades e chunks por mundo, memoria
        final File resumo = new File(getDataFolder(), "resumo.log");
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(new SimpleDateFormat("dd/MM HH:mm").format(new Date()));
                sb.append(" jogadores=").append(Bukkit.getOnlinePlayers().size());
                sb.append(String.format(java.util.Locale.ROOT, " tps=%.1f mspt=%.1f pior=%dms", Bukkit.getTPS()[0], Bukkit.getAverageTickTime(), piorTick / 1_000_000L));
                piorTick = 0;
                Runtime rt = Runtime.getRuntime();
                sb.append(" mem=").append((rt.totalMemory() - rt.freeMemory()) / 1048576L).append("/").append(rt.maxMemory() / 1048576L).append("MB");
                java.util.List<org.bukkit.World> ws = new java.util.ArrayList<>(Bukkit.getWorlds());
                ws.sort((x, y) -> Integer.compare(y.getEntityCount(), x.getEntityCount()));
                int tot = 0, ch = 0;
                for (org.bukkit.World w : ws) { tot += w.getEntityCount(); ch += w.getLoadedChunks().length; }
                sb.append(" entidades=").append(tot).append(" chunks=").append(ch).append(" |");
                for (int i = 0; i < Math.min(4, ws.size()); i++) {
                    org.bukkit.World w = ws.get(i);
                    if (w.getEntityCount() == 0) break;
                    sb.append(' ').append(w.getName()).append(':').append(w.getEntityCount()).append("e/").append(w.getLoadedChunks().length).append("c/").append(w.getPlayers().size()).append('j');
                }
                sb.append('\n');
                resumo.getParentFile().mkdirs();
                if (resumo.length() > 1_000_000L) resumo.renameTo(new File(resumo.getParentFile(), "resumo-antigo.log"));
                try (FileWriter w = new FileWriter(resumo, true)) { w.write(sb.toString()); }
                escreverPerfil(perfilArq, sb.toString());
            } catch (Throwable ignored) { }
        }, 20L * resumoSeg, 20L * resumoSeg);
        final long inicio = System.nanoTime();
        final File arq = new File(getDataFolder(), "travadas.log");
        vigia = new Thread(() -> {
            long ultimaNota = 0, tickAnotado = -1;
            StackTraceElement[] pilha = null;
            long tickDaPilha = -1;
            while (ligado) {
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                long agora = System.nanoTime(), t = ultimoTick;
                long parado = agora - t;
                if (jogadores > 0 && agora - inicio > espera) try { amostrar(principal.getStackTrace()); } catch (Throwable ignored) { }
                if (parado < limite) {
                    // acabou uma travada que foi anotada: registra quanto durou
                    if (pilha != null && tickDaPilha != t) {
                        escrever(arq, (t - tickDaPilha) / 1_000_000L, pilha);
                        pilha = null;
                    }
                    continue;
                }
                if (agora - inicio < espera || t == tickAnotado || agora - ultimaNota < intervalo) continue;
                tickAnotado = t;
                ultimaNota = agora;
                tickDaPilha = t;
                pilha = principal.getStackTrace();
            }
        }, "VigiaLag");
        vigia.setDaemon(true);
        vigia.start();
        getLogger().info("Vigiando ticks parados por mais de " + (limite / 1_000_000L) + " ms (anota em plugins/VigiaLag/travadas.log).");
    }

    private static final String[][] MARCAS = {
        {"waitForTasks", "(ocioso: esperando o proximo tick)"}, {"ServerChunkCache.getChunk", "servidor: esperando chunk carregar (carga sincrona)"}, {"LockSupport.park", "(ocioso: esperando o proximo tick)"}, {"NaturalSpawner", "servidor: nascimento natural de monstros"},
        {"tickBlockEntities", "servidor: blocos com funcao (funis, fornalhas, spawners)"}, {"ChunkFullTask", "servidor: carregar chunks"},
        {"ChunkTaskScheduler", "servidor: carregar chunks"}, {"ChunkMap.tick", "servidor: enviar entidades aos jogadores (tracker)"},
        {"EntityTickList", "servidor: entidades"}, {"tickNonPassenger", "servidor: entidades"}, {"ServerChunkCache.tick", "servidor: chunks (blocos, spawn)"},
        {"ServerConnectionListener", "servidor: rede (pacotes dos jogadores)"}, {"saveAll", "servidor: salvar"}, {"saveEverything", "servidor: salvar"},
        {"PacketUtils", "servidor: pacotes dos jogadores"}, {"CraftScheduler.mainThreadHeartbeat", "servidor: tarefas agendadas"}};

    private void amostrar(StackTraceElement[] pilha) {
        if (pilha.length == 0) return;
        String chave = null, jar = null, entidade = null;
        for (StackTraceElement e : pilha) {
            String cl = e.getClassLoaderName();
            if (cl != null && cl.endsWith(".jar")) {
                String c = e.getClassName();
                jar = cl;
                chave = cl + " " + c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName();
                break;
            }
        }
        if (chave == null) {
            for (StackTraceElement e : pilha) {
                String c = e.getClassName();
                if (entidade == null && c.startsWith("net.minecraft.world.entity.") && (e.getMethodName().equals("tick") || e.getMethodName().equals("aiStep") || e.getMethodName().equals("customServerAiStep")))
                    entidade = c.substring(c.lastIndexOf('.') + 1);
            }
            String tudo = null;
            for (String[] m : MARCAS) {
                for (StackTraceElement e : pilha) {
                    String s = e.getClassName() + "." + e.getMethodName();
                    if (s.contains(m[0])) { tudo = m[1]; break; }
                }
                if (tudo != null) break;
            }
            if (tudo == null) { StackTraceElement e = pilha[0]; String c = e.getClassName(); tudo = "servidor: " + c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName(); }
            chave = entidade != null && tudo.equals("servidor: entidades") ? "servidor: entidades (" + entidade + ")" : tudo;
        }
        synchronized (perfil) {
            amostras++;
            perfil.computeIfAbsent(chave, k -> new int[1])[0]++;
            if (jar != null) porPlugin.computeIfAbsent(jar, k -> new int[1])[0]++;
        }
    }

    private void escreverPerfil(File arq, String cabecalho) throws IOException {
        java.util.List<java.util.Map.Entry<String, int[]>> a, b;
        int n;
        synchronized (perfil) {
            n = amostras;
            if (n < 200) { perfil.clear(); porPlugin.clear(); amostras = 0; return; }
            a = new java.util.ArrayList<>(perfil.entrySet());
            b = new java.util.ArrayList<>(porPlugin.entrySet());
            perfil.clear(); porPlugin.clear(); amostras = 0;
        }
        a.sort((x, y) -> y.getValue()[0] - x.getValue()[0]);
        b.sort((x, y) -> y.getValue()[0] - x.getValue()[0]);
        StringBuilder sb = new StringBuilder("== ").append(cabecalho);
        sb.append("   amostras com jogador: ").append(n).append("\n   por plugin:");
        for (int i = 0; i < Math.min(8, b.size()); i++) sb.append(String.format(java.util.Locale.ROOT, " %s %.1f%%;", b.get(i).getKey(), 100.0 * b.get(i).getValue()[0] / n));
        sb.append('\n');
        for (int i = 0; i < Math.min(14, a.size()); i++) sb.append(String.format(java.util.Locale.ROOT, "   %5.1f%%  %s\n", 100.0 * a.get(i).getValue()[0] / n, a.get(i).getKey()));
        if (arq.length() > 1_000_000L) arq.renameTo(new File(arq.getParentFile(), "perfil-antigo.log"));
        try (FileWriter w = new FileWriter(arq, true)) { w.write(sb.toString()); }
    }

    private void escrever(File arq, long ms, StackTraceElement[] pilha) {
        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date())).append(" tick de ").append(ms).append(" ms | jogadores: ");
        try { sb.append(Bukkit.getOnlinePlayers().size()); } catch (Throwable t) { sb.append('?'); }
        sb.append('\n');
        int n = 0;
        String plugin = null;
        for (StackTraceElement e : pilha) {
            String s = e.toString();
            if (plugin == null && !s.startsWith("java.") && !s.startsWith("jdk.") && !s.startsWith("net.minecraft.") && !s.startsWith("org.bukkit.")
                    && !s.startsWith("io.papermc.") && !s.startsWith("com.mojang.") && !s.startsWith("ca.spottedleaf.") && !s.startsWith("it.unimi.")) plugin = s;
            if (n++ < 18) sb.append("    ").append(s).append('\n');
        }
        if (plugin != null) sb.append("    >> primeiro codigo de plugin: ").append(plugin).append('\n');
        try {
            arq.getParentFile().mkdirs();
            if (arq.length() > 2_000_000L) arq.renameTo(new File(arq.getParentFile(), "travadas-antigo.log"));
            try (FileWriter w = new FileWriter(arq, true)) { w.write(sb.toString()); }
        } catch (IOException ignored) { }
        getLogger().warning("tick de " + ms + " ms" + (plugin != null ? " em " + plugin : " (" + (pilha.length > 0 ? pilha[0] : "?") + ")"));
    }

    @Override public void onDisable() {
        ligado = false;
        if (vigia != null) vigia.interrupt();
    }
}
