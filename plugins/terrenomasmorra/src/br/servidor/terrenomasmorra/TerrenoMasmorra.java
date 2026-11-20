package br.servidor.terrenomasmorra;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.events.ClaimChangeEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerrenoMasmorra extends JavaPlugin implements Listener {
    private final Set<String> mundos = new HashSet<>();
    private int lobbyRaio, margemMasmorra, margemMonstros;
    private final Set<String> avisado = new HashSet<>();

    // caixas protegidas das masmorras (x1,z1,x2,z2), por mundo; relidas quando o masmorras.yml muda
    private long caixasDe = -1;
    private final List<Object[]> caixas = new ArrayList<>();

    // ganchos no plugin Masmorras (so leitura)
    private Plugin masmorras;
    private long semente;
    private Method escolher, escX, escZ, temCampo;
    private Object campos;

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String m : getConfig().getStringList("mundos")) mundos.add(m.toLowerCase());
        lobbyRaio = getConfig().getInt("lobby-raio", 160);
        margemMasmorra = getConfig().getInt("margem-masmorra", 40);
        margemMonstros = getConfig().getInt("margem-monstros", 16);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::liberarMundos, 100L, 200L);
        // 15/11 (Paulo: "meu terreno no mundo masmorra esta ficando desclaimado"): o GriefPrevention liga antes dos mundos
        // das masmorras e do Apocalipse existirem e PULA os terrenos de la a cada reinicio. Depois que tudo ligou, carrega os que faltaram.
        Bukkit.getScheduler().runTaskLater(this, this::carregarTerrenosPulados, 140L);
        Bukkit.getScheduler().runTaskLater(this, this::carregarTerrenosPulados, 600L);
    }

    /** O GriefPrevention so conhece os mundos que existiam quando ele ligou; o das masmorras nasce depois. */
    private void liberarMundos() {
        try {
            GriefPrevention gp = GriefPrevention.instance;
            if (gp == null || gp.config_claims_worldModes == null) return;
            for (World w : Bukkit.getWorlds()) {
                if (!mundos.contains(w.getName().toLowerCase())) continue;
                ClaimsMode m = gp.config_claims_worldModes.get(w);
                if (m == null || m == ClaimsMode.Disabled) {
                    gp.config_claims_worldModes.put(w, ClaimsMode.Survival);
                    getLogger().info("Terrenos liberados no mundo " + w.getName() + ".");
                }
            }
        } catch (Throwable t) { avisar("gp", "Nao consegui liberar terrenos no GriefPrevention: " + t); }
    }

    private void carregarTerrenosPulados() {
        try {
            GriefPrevention gp = GriefPrevention.instance;
            if (gp == null || gp.dataStore == null) return;
            File pasta = new File(gp.getDataFolder().getParentFile(), "GriefPreventionData" + File.separator + "ClaimData");
            File[] todos = pasta.listFiles((d, n) -> n.endsWith(".yml"));
            if (todos == null) return;
            List<File> faltam = new ArrayList<>();
            for (File f : todos) {
                long id;
                try { id = Long.parseLong(f.getName().substring(0, f.getName().length() - 4)); } catch (NumberFormatException e) { continue; }
                if (gp.dataStore.getClaim(id) != null) continue;
                // so os de mundo que agora existe (os outros continuam esperando)
                String mundo = YamlConfiguration.loadConfiguration(f).getString("Lesser Boundary Corner", "").split(";")[0];
                if (!mundo.isEmpty() && Bukkit.getWorld(mundo) != null) faltam.add(f);
            }
            if (faltam.isEmpty()) return;
            Method m = gp.dataStore.getClass().getDeclaredMethod("loadClaimData", File[].class);
            m.setAccessible(true);
            m.invoke(gp.dataStore, (Object) faltam.toArray(new File[0]));
            int ok = 0;
            for (File f : faltam) if (gp.dataStore.getClaim(Long.parseLong(f.getName().substring(0, f.getName().length() - 4))) != null) ok++;
            getLogger().info("Terrenos que o GriefPrevention tinha pulado (mundo criado depois dele): " + ok + " de " + faltam.size() + " carregados.");
        } catch (Throwable t) { avisar("pulados", "Nao consegui carregar os terrenos pulados: " + t); }
    }

    private void avisar(String chave, String msg) { if (avisado.add(chave)) getLogger().warning(msg); }

    private boolean ligar() {
        if (temCampo != null) return true;
        try {
            Plugin m = Bukkit.getPluginManager().getPlugin("Masmorras");
            if (m == null || !m.isEnabled()) return false;
            Object mm = campo(m, "mundoNovo");
            Object ger = campo(mm, "gerador");
            Object cps = campo(m, "campos");
            if (ger == null || cps == null) return false;
            long s = ((Number) campo(ger, "semente")).longValue();
            Class<?> est = Class.forName("br.servidor.masmorras.Estruturas", true, m.getClass().getClassLoader());
            Method e = est.getDeclaredMethod("escolher", long.class, int.class, int.class); e.setAccessible(true);
            Class<?> esc = Class.forName("br.servidor.masmorras.Estruturas$Escolha", true, m.getClass().getClassLoader());
            Method x = esc.getDeclaredMethod("x"), z = esc.getDeclaredMethod("z"); x.setAccessible(true); z.setAccessible(true);
            Method tc = cps.getClass().getDeclaredMethod("temCampo", int.class, int.class); tc.setAccessible(true);
            masmorras = m; semente = s; escolher = e; escX = x; escZ = z; campos = cps; temCampo = tc;
            return true;
        } catch (Throwable t) { avisar("ligar", "Nao consegui ler os pontos de monstros do plugin Masmorras: " + t); return false; }
    }

    private static Object campo(Object o, String nome) throws Exception {
        if (o == null) return null;
        Field f = o.getClass().getDeclaredField(nome); f.setAccessible(true); return f.get(o);
    }

    private void lerCaixas() {
        Plugin m = Bukkit.getPluginManager().getPlugin("Masmorras");
        if (m == null) return;
        File f = new File(m.getDataFolder(), "masmorras.yml");
        long mod = f.lastModified();
        if (mod == caixasDe) return;
        caixasDe = mod; caixas.clear();
        if (!f.isFile()) return;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection("masmorras");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            String mundo = s.getString(id + ".mundo", "");
            int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
            List<String> linhas = new ArrayList<>(s.getStringList(id + ".protecao"));
            linhas.addAll(s.getStringList(id + ".salas"));
            String saida = s.getString(id + ".saida");
            if (saida != null) { String[] p = saida.split(","); if (p.length == 3) linhas.add(p[0] + "," + p[1] + "," + p[2] + "," + p[0] + "," + p[1] + "," + p[2]); }
            for (String l : linhas) {
                String[] p = l.split(",");
                if (p.length != 6) continue;
                try {
                    int a = Integer.parseInt(p[0].trim()), b = Integer.parseInt(p[2].trim()), c = Integer.parseInt(p[3].trim()), d = Integer.parseInt(p[5].trim());
                    x1 = Math.min(x1, Math.min(a, c)); x2 = Math.max(x2, Math.max(a, c));
                    z1 = Math.min(z1, Math.min(b, d)); z2 = Math.max(z2, Math.max(b, d));
                } catch (NumberFormatException ignored) { }
            }
            if (x1 <= x2) caixas.add(new Object[] { mundo.toLowerCase(), x1, z1, x2, z2 });
        }
    }

    /** null = pode; senao o motivo (para mostrar ao jogador). */
    String motivo(World w, int ax, int az, int bx, int bz) {
        if (w == null || !mundos.contains(w.getName().toLowerCase())) return null;
        int x1 = Math.min(ax, bx), x2 = Math.max(ax, bx), z1 = Math.min(az, bz), z2 = Math.max(az, bz);
        if (dist(0, 0, x1, z1, x2, z2) < lobbyRaio) return "perto demais do lobby das masmorras (afaste-se mais de " + lobbyRaio + " blocos do centro)";
        try { lerCaixas(); } catch (Throwable t) { avisar("caixas", "Nao consegui ler o masmorras.yml: " + t); return "protecao das masmorras indisponivel agora, tente mais tarde"; }
        String nome = w.getName().toLowerCase();
        for (Object[] c : caixas) {
            if (!nome.equals(c[0])) continue;
            int cx1 = (int) c[1] - margemMasmorra, cz1 = (int) c[2] - margemMasmorra, cx2 = (int) c[3] + margemMasmorra, cz2 = (int) c[4] + margemMasmorra;
            if (x1 <= cx2 && x2 >= cx1 && z1 <= cz2 && z2 >= cz1) return "perto demais de uma masmorra (fique a mais de " + margemMasmorra + " blocos dela)";
        }
        // 20/11 (Paulo: "ficaram lugares que nao podem ser claimados, mesmo sem mobs"): os pontos de monstros nao travam
        // mais o terreno; quem manda e o terreno: o Masmorras nao faz nascer monstro de campo dentro de terreno marcado
        if (!getConfig().getBoolean("bloquear-pontos-de-monstros", false)) return null;
        if (!ligar()) return "protecao dos pontos de monstros indisponivel agora, tente mais tarde";
        if ((long) (x2 - x1) * (z2 - z1) > 4_000_000L) return "terreno grande demais para conferir de uma vez; marque em partes menores";
        try {
            int m = margemMonstros + 16;
            for (int cx = (x1 - m) >> 4; cx <= (x2 + m) >> 4; cx++) for (int cz = (z1 - m) >> 4; cz <= (z2 + m) >> 4; cz++) {
                Object e = escolher.invoke(null, semente, cx, cz);
                if (e != null && dist((int) escX.invoke(e), (int) escZ.invoke(e), x1, z1, x2, z2) <= margemMonstros)
                    return "perto demais de um ponto de monstros (fique a mais de " + margemMonstros + " blocos dele)";
                if ((boolean) temCampo.invoke(campos, cx, cz) && dist((cx << 4) + 8, (cz << 4) + 8, x1, z1, x2, z2) <= margemMonstros)
                    return "perto demais de um ponto de monstros (fique a mais de " + margemMonstros + " blocos dele)";
            }
        } catch (Throwable t) { avisar("pontos", "Erro conferindo pontos de monstros: " + t); return "protecao dos pontos de monstros indisponivel agora, tente mais tarde"; }
        return null;
    }

    /** distancia de um ponto ate o retangulo (0 se estiver dentro). */
    private static double dist(int px, int pz, int x1, int z1, int x2, int z2) {
        int dx = px < x1 ? x1 - px : (px > x2 ? px - x2 : 0), dz = pz < z1 ? z1 - pz : (pz > z2 ? pz - z2 : 0);
        return Math.hypot(dx, dz);
    }

    private String motivo(Claim c) {
        if (c == null) return null;
        Claim topo = c; while (topo.parent != null) topo = topo.parent;
        if (c != topo) return null; // subdivisao dentro de um terreno ja aceito
        Location a = c.getLesserBoundaryCorner(), b = c.getGreaterBoundaryCorner();
        if (a == null || b == null) return null;
        return motivo(a.getWorld(), a.getBlockX(), a.getBlockZ(), b.getBlockX(), b.getBlockZ());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void criou(ClaimCreatedEvent e) {
        String m = motivo(e.getClaim());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getCreator() != null) e.getCreator().sendMessage("§cAqui nao da para marcar terreno: " + m + ".");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void mudou(ClaimChangeEvent e) {
        String m = motivo(e.getTo());
        if (m == null) return;
        e.setCancelled(true);
        CommandSender s = null;
        try { Object o = e.getClass().getMethod("getModifier").invoke(e); if (o instanceof CommandSender cs) s = cs; } catch (Throwable ignored) { }
        if (s != null) s.sendMessage("§cNao da para levar o terreno ate ai: " + m + ".");
    }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 5 && a[0].equalsIgnoreCase("testar")) {
            try {
                World w = s instanceof org.bukkit.entity.Player p ? p.getWorld() : null;
                if (w == null || !mundos.contains(w.getName().toLowerCase())) for (String m : mundos) { World x = Bukkit.getWorld(m); if (x != null) { w = x; break; } }
                if (w == null) { s.sendMessage("Mundo das masmorras nao esta carregado."); return true; }
                String m = motivo(w, Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]));
                GriefPrevention gp = GriefPrevention.instance;
                s.sendMessage("[TerrenoMasmorra] " + w.getName() + " " + a[1] + "," + a[2] + " ate " + a[3] + "," + a[4] + ": " + (m == null ? "PODE" : "NAO PODE - " + m)
                        + " | GP ligado no mundo: " + (gp != null && gp.claimsEnabledForWorld(w)) + " | masmorras lidas: " + caixas.size());
            } catch (NumberFormatException ex) { s.sendMessage("Use numeros inteiros."); }
            return true;
        }
        s.sendMessage("Use: /terrenomasmorra testar <x1> <z1> <x2> <z2>");
        return true;
    }
}
