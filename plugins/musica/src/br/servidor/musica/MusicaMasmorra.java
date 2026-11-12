package br.servidor.musica;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MusicaMasmorra extends JavaPlugin implements Listener {
    private record Faixa(String som, int segundos) {}
    private static final class Tocando { int faixa; long ate; }
    private static final class Luta { long ate; long semChefe; }

    private final List<Faixa> faixas = new ArrayList<>();
    private final Set<String> mundos = new HashSet<>();
    private final Map<UUID, Tocando> tocando = new HashMap<>();
    private final Map<UUID, Luta> lutas = new HashMap<>();
    private final List<String> chefes = new ArrayList<>();
    private String chefeSom;
    private int chefeSegundos, chefeRaio;
    private float chefeVolume;
    private NamespacedKey desligada;
    private float volume;
    private int pausa;

    @Override public void onEnable() {
        saveDefaultConfig();
        desligada = new NamespacedKey(this, "desligada");
        for (String m : getConfig().getStringList("mundos")) mundos.add(m.toLowerCase());
        volume = (float) getConfig().getDouble("volume", 1.0);
        pausa = Math.max(0, getConfig().getInt("pausa", 4));
        for (Map<?, ?> f : getConfig().getMapList("faixas")) {
            Object s = f.get("som"), d = f.get("segundos");
            if (s != null && d instanceof Number n && n.intValue() > 0) faixas.add(new Faixa(s.toString(), n.intValue()));
        }
        for (String c : getConfig().getStringList("chefe.nomes")) if (!c.isBlank()) chefes.add(c.toLowerCase());
        chefeSom = getConfig().getString("chefe.som", "minecraft:music.dragon");
        chefeSegundos = Math.max(10, getConfig().getInt("chefe.segundos", 345));
        chefeRaio = Math.max(8, getConfig().getInt("chefe.raio", 40));
        chefeVolume = (float) getConfig().getDouble("chefe.volume", 1.0);
        if (faixas.isEmpty()) faixas.add(new Faixa("minecraft:music_disc.pigstep", 149));
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::passo, 40L, 20L);
        getLogger().info("Musica do spawn da masmorra: " + faixas.size() + " faixa(s) em " + mundos + ".");
    }

    @Override public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) { parar(p); if (lutas.remove(p.getUniqueId()) != null) p.stopSound(chefeSom, SoundCategory.RECORDS); }
    }

    private boolean quer(Player p) {
        return mundos.contains(p.getWorld().getName().toLowerCase()) && !p.getPersistentDataContainer().has(desligada, PersistentDataType.BYTE);
    }

    private void passo() {
        long agora = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (lutando(p, agora)) { if (tocando.containsKey(p.getUniqueId())) parar(p); continue; }
            Tocando t = tocando.get(p.getUniqueId());
            if (!quer(p)) { if (t != null) parar(p); continue; }
            if (t == null) { t = new Tocando(); t.faixa = ThreadLocalRandom.current().nextInt(faixas.size()) - 1; t.ate = 0; tocando.put(p.getUniqueId(), t); }
            if (agora < t.ate) continue;
            t.faixa = (t.faixa + 1) % faixas.size();
            Faixa f = faixas.get(t.faixa);
            p.playSound(p, f.som(), SoundCategory.RECORDS, volume, 1f);
            t.ate = agora + (f.segundos() + pausa) * 1000L;
        }
    }

    /** Musica de chefe: toca enquanto houver um chefe da lista por perto; para 6 s depois que ele some. */
    private boolean lutando(Player p, long agora) {
        Luta l = lutas.get(p.getUniqueId());
        boolean perto = false;
        if (!chefes.isEmpty() && !p.getPersistentDataContainer().has(desligada, PersistentDataType.BYTE)) {
            for (org.bukkit.entity.Entity e : p.getNearbyEntities(chefeRaio, chefeRaio / 2.0, chefeRaio)) {
                if (!(e instanceof org.bukkit.entity.LivingEntity) || e instanceof Player || e.isDead()) continue;
                net.kyori.adventure.text.Component n = e.customName();
                if (n == null) continue;
                String nome = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(n).toLowerCase();
                for (String c : chefes) if (nome.contains(c)) { perto = true; break; }
                if (perto) break;
            }
        }
        if (perto) {
            if (l == null) { l = new Luta(); lutas.put(p.getUniqueId(), l); }
            l.semChefe = 0;
            if (agora >= l.ate) {
                p.stopSound(chefeSom, SoundCategory.RECORDS);
                p.playSound(p, chefeSom, SoundCategory.RECORDS, chefeVolume, 1f);
                l.ate = agora + chefeSegundos * 1000L;
            }
            return true;
        }
        if (l == null) return false;
        if (l.semChefe == 0) l.semChefe = agora;
        if (agora - l.semChefe < 6000) return true;
        lutas.remove(p.getUniqueId());
        p.stopSound(chefeSom, SoundCategory.RECORDS);
        return false;
    }

    private void parar(Player p) {
        Tocando t = tocando.remove(p.getUniqueId());
        if (t == null || t.faixa < 0) return;
        if (p.isOnline()) p.stopSound(faixas.get(t.faixa).som(), SoundCategory.RECORDS);
    }

    @EventHandler public void saiu(PlayerQuitEvent e) { tocando.remove(e.getPlayer().getUniqueId()); lutas.remove(e.getPlayer().getUniqueId()); }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("So para jogadores."); return true; }
        if (p.getPersistentDataContainer().has(desligada, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().remove(desligada);
            p.sendMessage("§aMusica do spawn da masmorra ligada.");
        } else {
            p.getPersistentDataContainer().set(desligada, PersistentDataType.BYTE, (byte) 1);
            parar(p);
            if (lutas.remove(p.getUniqueId()) != null) p.stopSound(chefeSom, SoundCategory.RECORDS);
            p.sendMessage("§eMusica do spawn da masmorra desligada. Use /musicaspawn para ligar de novo.");
        }
        return true;
    }
}
