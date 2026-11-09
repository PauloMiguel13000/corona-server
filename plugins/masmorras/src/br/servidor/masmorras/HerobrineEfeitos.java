package br.servidor.masmorras;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Efeitos do conjunto cosmetico de Herobrine (so visual, nao muda dano nem nada do jogo):
 * - segurando a espada: faiscas brancas, chama de alma azul e centelhas saindo da lamina;
 * - acertando com a espada: explosao de luz branca e um som sombrio (no maximo 1 a cada 0,4 s);
 * - com as 4 pecas vestidas: aura de pontinhos azul-claros girando e brilho saindo dos olhos.
 * Particulas do proprio jogo (aparecem no Java e no Bedrock), poucas e so para quem esta perto. Qualquer erro e ignorado.
 */
final class HerobrineEfeitos implements Listener {
    private static final Particle.DustOptions AZUL = new Particle.DustOptions(Color.fromRGB(190, 246, 255), 0.8f);
    private static final Particle.DustOptions BRANCO = new Particle.DustOptions(Color.fromRGB(250, 255, 255), 0.6f);
    private final Masmorras pl;
    private final NamespacedKey kc;
    private final Map<UUID, Long> ultimoGolpe = new HashMap<>();
    private long tick;
    private boolean avisou;

    HerobrineEfeitos(Masmorras pl) {
        this.pl = pl;
        this.kc = new NamespacedKey(pl, "cosmetico");
        Bukkit.getScheduler().runTaskTimer(pl, this::ciclo, 40L, 4L);
    }

    private boolean eh(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        return "herobrine".equals(it.getItemMeta().getPersistentDataContainer().get(kc, PersistentDataType.STRING));
    }

    private boolean espada(ItemStack it) {
        return it != null && it.getType().name().endsWith("_SWORD") && eh(it);
    }

    private void ciclo() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()) continue;
                boolean comEspada = espada(p.getInventory().getItemInMainHand());
                int pecas = 0;
                for (ItemStack a : p.getInventory().getArmorContents()) if (eh(a)) pecas++;
                if (!comEspada && pecas < 4) continue;
                World w = p.getWorld();
                Location pe = p.getLocation();
                Vector dir = pe.getDirection().setY(0);
                if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
                dir.normalize();
                Vector direita = new Vector(-dir.getZ(), 0, dir.getX());
                if (comEspada) {
                    // ao longo da lamina (mao direita para a frente e um pouco para cima)
                    double t = 0.25 + Math.random() * 0.6;
                    Location l = pe.clone().add(0, 1.05 + t * 0.35, 0).add(direita.clone().multiply(0.4)).add(dir.clone().multiply(0.3 + t * 0.5));
                    w.spawnParticle(Particle.END_ROD, l, 1, 0.04, 0.06, 0.04, 0.004);
                    if (tick % 2 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 1, 0.05, 0.08, 0.05, 0.004);
                    if (tick % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.08, 0.1, 0.08, 0.02);
                }
                if (pecas >= 4 && tick % 2 == 0) {
                    // aura girando em volta do corpo
                    double a = tick * 0.55;
                    for (int k = 0; k < 2; k++) {
                        double ang = a + k * Math.PI;
                        Location l = pe.clone().add(Math.cos(ang) * 0.65, 0.3 + (tick % 10) * 0.15, Math.sin(ang) * 0.65);
                        w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, AZUL);
                    }
                    // (os olhos agora brilham de verdade no elmo 3D: sem particula nos olhos = menos pacotes no celular)
                }
            } catch (Throwable ex) {
                if (!avisou) {
                    avisou = true;
                    pl.getLogger().warning("Efeito do Herobrine: " + ex);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void acertou(EntityDamageByEntityEvent e) {
        try {
            if (!(e.getDamager() instanceof Player p) || !espada(p.getInventory().getItemInMainHand())) return;
            long agora = System.currentTimeMillis();
            Long antes = ultimoGolpe.get(p.getUniqueId());
            if (antes != null && agora - antes < 400) return;
            ultimoGolpe.put(p.getUniqueId(), agora);
            Entity alvo = e.getEntity();
            Location l = alvo.getLocation().add(0, alvo.getHeight() * 0.6, 0);
            World w = l.getWorld();
            w.spawnParticle(Particle.END_ROD, l, 10, 0.25, 0.3, 0.25, 0.07);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 6, 0.2, 0.25, 0.2, 0.03);
            w.spawnParticle(Particle.ELECTRIC_SPARK, l, 6, 0.3, 0.3, 0.3, 0.1);
            w.playSound(l, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.PLAYERS, 0.5f, 1.7f);
            w.playSound(l, Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.4f, 1.5f);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        ultimoGolpe.remove(e.getPlayer().getUniqueId());
    }
}
