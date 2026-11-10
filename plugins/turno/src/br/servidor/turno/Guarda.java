package br.servidor.turno;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Guarda tudo do jogador antes de entrar no minigame (itens, local, vida, XP, efeitos) num arquivo,
 * e devolve na saida. O arquivo garante a devolucao mesmo se o servidor cair no meio da partida.
 */
final class Guarda {
    private final File pasta;

    Guarda(File pastaPlugin) {
        this.pasta = new File(pastaPlugin, "guardados");
        if (!pasta.exists()) pasta.mkdirs();
    }

    private File arquivo(Player p) {
        return new File(pasta, p.getUniqueId() + ".yml");
    }

    boolean tem(Player p) {
        return arquivo(p).exists();
    }

    void guardar(Player p) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        Location l = p.getLocation();
        y.set("nome", p.getName());
        y.set("local.mundo", l.getWorld().getName());
        y.set("local.x", l.getX());
        y.set("local.y", l.getY());
        y.set("local.z", l.getZ());
        y.set("local.yaw", l.getYaw());
        y.set("local.pitch", l.getPitch());
        y.set("modo", p.getGameMode().name());
        y.set("vida", p.getHealth());
        y.set("fome", p.getFoodLevel());
        y.set("saturacao", p.getSaturation());
        y.set("nivel", p.getLevel());
        y.set("xp", p.getExp());
        y.set("pode-voar", p.getAllowFlight());
        y.set("efeitos", new ArrayList<>(p.getActivePotionEffects()));
        ItemStack[] inv = p.getInventory().getContents();
        y.set("inv-tamanho", inv.length);
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it == null || it.getType().isAir()) continue;
            y.set("inv." + i, Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        y.save(arquivo(p));
    }

    /** Devolve o que foi guardado. Retorna false se nao havia nada guardado. */
    boolean devolver(Player p) {
        return devolver(p, true);
    }

    /** voltar=false: devolve itens/vida/XP mas deixa a pessoa onde ela esta (ja saiu do mundo do minigame por outro caminho). */
    boolean devolver(Player p, boolean voltar) {
        File f = arquivo(p);
        if (!f.exists()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        limpar(p);
        int tam = y.getInt("inv-tamanho", p.getInventory().getContents().length);
        ItemStack[] inv = new ItemStack[Math.max(tam, p.getInventory().getContents().length)];
        if (y.isConfigurationSection("inv")) {
            for (String k : y.getConfigurationSection("inv").getKeys(false)) {
                try {
                    int i = Integer.parseInt(k);
                    if (i >= 0 && i < inv.length) inv[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(y.getString("inv." + k)));
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Turno] Item " + k + " de " + p.getName() + " nao pode ser lido: " + e);
                }
            }
        }
        ItemStack[] certo = new ItemStack[p.getInventory().getContents().length];
        System.arraycopy(inv, 0, certo, 0, certo.length);
        p.getInventory().setContents(certo);
        try {
            p.setGameMode(GameMode.valueOf(y.getString("modo", "SURVIVAL")));
        } catch (IllegalArgumentException e) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double teto = max == null ? 20 : max.getValue();
        p.setHealth(Math.max(1, Math.min(teto, y.getDouble("vida", teto))));
        p.setFoodLevel(y.getInt("fome", 20));
        p.setSaturation((float) y.getDouble("saturacao", 5));
        p.setLevel(y.getInt("nivel", 0));
        p.setExp((float) Math.max(0, Math.min(0.999, y.getDouble("xp", 0))));
        p.setAllowFlight(y.getBoolean("pode-voar", false) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
        List<?> efs = y.getList("efeitos");
        if (efs != null) for (Object o : efs) if (o instanceof PotionEffect pe) p.addPotionEffect(pe);
        World w = Bukkit.getWorld(y.getString("local.mundo", ""));
        Location volta = w == null || w.getName().equals(Turno.MUNDO) ? Bukkit.getWorlds().get(0).getSpawnLocation()
                : new Location(w, y.getDouble("local.x"), y.getDouble("local.y"), y.getDouble("local.z"),
                        (float) y.getDouble("local.yaw"), (float) y.getDouble("local.pitch"));
        if (voltar) p.teleport(volta);
        f.delete();
        return true;
    }

    /** Deixa o jogador "zerado" para o minigame. */
    static void limpar(Player p) {
        p.getInventory().clear();
        p.setItemOnCursor(null);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setWalkSpeed(0.2f);
        AttributeInstance pulo = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (pulo != null) pulo.setBaseValue(pulo.getDefaultValue());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
    }
}
