package br.servidor.extras;

import java.io.File;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Aviso grande na tela (titulo + barra de acao) para quem entrou e ainda nao fez /login ou /register no AuthMe.
 * No celular a mensagem do chat passa batido e o jogador novo era expulso por tempo sem entender o que fazer.
 * Mostra o comando certo (register para novato, login para quem ja tem conta) e quanto tempo falta.
 */
final class AvisoLogin implements Listener {
    private final Extras pl;
    /** Quando cada jogador ainda nao logado entrou (sai do mapa quando loga ou sai). */
    private final Map<UUID, Long> entrouEm = new ConcurrentHashMap<>();
    /** Se o nick ja tem conta no AuthMe (consultado de vez em quando, nao a cada segundo). */
    private final Map<UUID, Boolean> temConta = new ConcurrentHashMap<>();
    private BukkitTask tarefa;
    private Object api;
    private Method autenticado, registrado;
    private int tempoRegistro = 90, tempoLogin = 90;
    private long passo;

    AvisoLogin(Extras pl) {
        this.pl = pl;
    }

    void iniciar() {
        if (!pl.getConfig().getBoolean("login-aviso.ligado", true)) return;
        Plugin authme = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authme == null || !authme.isEnabled()) return;
        try {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(new File(authme.getDataFolder(), "config.yml"));
            tempoRegistro = c.getInt("settings.restrictions.registerTimeout", 90);
            tempoLogin = c.getInt("settings.restrictions.loginTimeout", 90);
        } catch (Throwable ignored) {
        }
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) entrouEm.put(p.getUniqueId(), agora);
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, this::avisar, 40L, 20L);
        pl.getLogger().info("Aviso de login na tela ligado (tempo do AuthMe: registro " + tempoRegistro + " s, login " + tempoLogin + " s).");
    }

    void parar() {
        if (tarefa != null) tarefa.cancel();
        tarefa = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entrou(PlayerJoinEvent e) {
        entrouEm.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        temConta.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void saiu(PlayerQuitEvent e) {
        entrouEm.remove(e.getPlayer().getUniqueId());
        temConta.remove(e.getPlayer().getUniqueId());
    }

    private boolean api() {
        if (api != null) return true;
        try {
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, Bukkit.getPluginManager().getPlugin("AuthMe").getClass().getClassLoader());
            api = c.getMethod("getInstance").invoke(null);
            autenticado = c.getMethod("isAuthenticated", Player.class);
            registrado = c.getMethod("isRegistered", String.class);
            return api != null;
        } catch (Throwable t) {
            api = null;
            return false;
        }
    }

    private void avisar() {
        if (entrouEm.isEmpty() || !api()) return;
        passo++;
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Long t0 = entrouEm.get(id);
            if (t0 == null) continue;
            boolean logado;
            try {
                logado = (Boolean) autenticado.invoke(api, p);
            } catch (Throwable t) {
                continue;
            }
            if (logado) {
                entrouEm.remove(id);
                temConta.remove(id);
                p.clearTitle();
                p.sendActionBar(Extras.txt(""));
                continue;
            }
            Boolean conta = temConta.get(id);
            if (conta == null || passo % 5 == 0) {
                try {
                    conta = (Boolean) registrado.invoke(api, p.getName());
                } catch (Throwable t) {
                    conta = Boolean.FALSE;
                }
                temConta.put(id, conta);
            }
            long seg = (agora - t0) / 1000L;
            int limite = conta ? tempoLogin : tempoRegistro;
            long resta = limite > 0 ? Math.max(0, limite - seg) : -1;
            boolean cel = Extras.ehBedrock(p);
            // titulo a cada 4 s (fica 4,5 s na tela, entao nao pisca)
            if (seg % 4 == 0) {
                String titulo = conta ? "§e§lBEM-VINDO DE VOLTA!" : "§a§lBEM-VINDO!";
                String sub;
                if (conta) sub = "§fDigite: §e/login §fsua senha";
                else if ((seg / 4) % 2 == 0) sub = "§fDigite: §e/register §fsenha senha";
                else sub = "§7Crie uma senha e digite ela 2 vezes";
                p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt(titulo), Extras.txt(sub),
                    net.kyori.adventure.title.Title.Times.times(Duration.ZERO, Duration.ofMillis(4500), Duration.ofMillis(300))));
            }
            String como = cel ? "§7Toque no balao de chat e digite o comando" : "§7Aperte §fT §7e digite o comando";
            String tempo = resta >= 0 ? "§eTempo: §f" + (resta / 60) + ":" + String.format("%02d", resta % 60) + "  §8|  " : "";
            p.sendActionBar(Extras.txt(tempo + como));
        }
    }
}
