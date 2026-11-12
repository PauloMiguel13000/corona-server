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
    private volatile boolean ligado;
    private Thread principal, vigia;

    @Override public void onEnable() {
        saveDefaultConfig();
        final long limite = Math.max(100, getConfig().getLong("limite-ms", 400)) * 1_000_000L;
        final long intervalo = Math.max(1, getConfig().getLong("intervalo-segundos", 5)) * 1_000_000_000L;
        final long espera = Math.max(0, getConfig().getLong("espera-inicial-segundos", 240)) * 1_000_000_000L;
        principal = Thread.currentThread();
        ultimoTick = System.nanoTime();
        ligado = true;
        Bukkit.getScheduler().runTaskTimer(this, () -> ultimoTick = System.nanoTime(), 1L, 1L);
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
