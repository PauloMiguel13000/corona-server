package br.servidor.tiro;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Texturas dos jogos de tiro (15/11): um pacote pequeno so deles (armas desenhadas e os infectados em 3D), separado
 * do pacote principal. Java recebe por link ao entrar (sem obrigar); Bedrock recebe pelo CoronaPack (pacotes-fora).
 * Quem tem o pacote ve o modelo 3D do infectado (ItemDisplay que anda junto) e a arma desenhada; quem nao tem
 * continua vendo o zumbi do jogo e a enxada. Para quem ve o 3D o zumbi de verdade fica escondido, entao o golpe
 * de perto (faca, soco) e o som de dor sao feitos aqui.
 */
final class Visual implements Listener {

    static final String URL_PADRAO = "https://corona-server.duckdns.org/pacotes/corona_tiro_java-de43a3d86d.zip";
    static final String SHA1_PADRAO = "de43a3d86d83d0a7664f69692d8d1d6affb34338";
    private static final UUID ID = UUID.nameUUIDFromBytes("corona-tiro-pack".getBytes(StandardCharsets.UTF_8));

    private final Tiro pl;
    private final Set<UUID> comPacote = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemDisplay> modelos = new HashMap<>();
    private final Map<UUID, Integer> quadros = new HashMap<>();
    private final Map<UUID, Long> ultimoGolpe = new HashMap<>();
    private int conta;

    Visual(Tiro pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 2L, 1L);
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("pacote.ligado", true);
    }

    static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || p.getName().startsWith(".");
    }

    /** Ve as texturas dos jogos de tiro? (Bedrock sempre: o pacote dele vem junto com os outros.) */
    boolean tem(Player p) {
        return ligado() && (bedrock(p) || comPacote.contains(p.getUniqueId()));
    }

    // ------------------------------------------------------------------ pacote do Java

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!ligado() || bedrock(p)) return;
        // depois do pacote principal (~3 s) e dos de musica (12 s)
        Bukkit.getScheduler().runTaskLater(pl, () -> mandar(p), 20L * 17);
    }

    void mandar(Player p) {
        if (!p.isOnline() || bedrock(p) || comPacote.contains(p.getUniqueId())) return;
        try {
            p.addResourcePack(ID, pl.getConfig().getString("pacote.java-url", URL_PADRAO),
                    HexFormat.of().parseHex(pl.getConfig().getString("pacote.java-sha1", SHA1_PADRAO)),
                    "§e§lCorona Server§r\n§fArmas e infectados 3D dos jogos de tiro", false);
        } catch (Throwable t) {
            pl.getLogger().warning("Pacote dos jogos de tiro para " + p.getName() + ": " + t);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void status(PlayerResourcePackStatusEvent e) {
        if (!ID.equals(e.getID())) return;
        UUID u = e.getPlayer().getUniqueId();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> comPacote.add(u);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> comPacote.remove(u);
            default -> { }
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        comPacote.remove(e.getPlayer().getUniqueId());
        ultimoGolpe.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ arma desenhada

    /** Deixa as armas da mochila com o desenho (quem tem o pacote) ou como enxada (quem nao tem). */
    @SuppressWarnings("UnstableApiUsage")
    void armas(Player p) {
        boolean quer = tem(p);
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            Armas.Arma a = pl.armas.armaDe(it);
            if (a == null) continue;
            Key certo = Key.key("corona", "armas/" + a.id());
            Key tem = it.getData(DataComponentTypes.ITEM_MODEL);
            boolean desenhada = certo.equals(tem);
            if (quer == desenhada) continue;
            if (quer) it.setData(DataComponentTypes.ITEM_MODEL, certo);
            else it.resetData(DataComponentTypes.ITEM_MODEL);
            p.getInventory().setItem(i, it);
        }
    }

    // ------------------------------------------------------------------ infectados em 3D

    @SuppressWarnings("UnstableApiUsage")
    private static ItemStack item(Zumbis.Tipo t, int q) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);   // no Bedrock o Geyser (corona-tiro.json) troca pelo item 3D
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("corona", "props/anim_z" + t.name().toLowerCase() + "_f" + q));
        return it;
    }

    private static Transformation forma(float e) {
        return new Transformation(new Vector3f(0, 1.5f * e, 0), new Quaternionf(), new Vector3f(e, e, e), new Quaternionf());
    }

    private void tique() {
        conta++;
        boolean rever = conta % 20 == 0;
        if (!ligado()) {
            if (!modelos.isEmpty()) limpar();
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            if (!pl.mundoDeTiro(w)) continue;
            if (w.getPlayers().isEmpty()) continue;
            if (rever) for (Player p : w.getPlayers()) armas(p);
            for (Zombie z : w.getEntitiesByClass(Zombie.class)) {
                Zumbis.Tipo t = pl.zumbis.tipo(z);
                if (t == null || z.isDead() || !z.isValid()) continue;
                UUID u = z.getUniqueId();
                Location l = z.getLocation();
                l.setYaw(z.getBodyYaw());
                l.setPitch(0);
                LivingEntity alvo = z.getTarget();
                int q = alvo != null && alvo.getWorld() == w && alvo.getLocation().distanceSquared(l) < 7.0 ? 1 : 0;
                ItemDisplay d = modelos.get(u);
                boolean novo = d == null || !d.isValid();
                if (novo) {
                    var esc = z.getAttribute(Attribute.SCALE);
                    float e = (float) ((esc == null ? 1.0 : esc.getBaseValue()) * 0.92);
                    final int fq = q;
                    d = w.spawn(l, ItemDisplay.class, x -> {
                        x.setItemStack(item(t, fq));
                        x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        x.setBillboard(Display.Billboard.FIXED);
                        x.setTransformation(forma(e));
                        x.setViewRange(1.6f);
                        x.setShadowRadius(0f);
                        x.setTeleportDuration(2);
                        x.setPersistent(false);
                        x.setVisibleByDefault(false);
                    });
                    modelos.put(u, d);
                    quadros.put(u, q);
                } else {
                    Location m = d.getLocation();
                    double dist = m.distanceSquared(l);
                    if (dist > 0.0001 || Math.abs(m.getYaw() - l.getYaw()) > 0.5f) {
                        d.setTeleportDuration(dist > 9 ? 0 : 2);
                        d.teleport(l);
                    }
                    if (quadros.getOrDefault(u, 0) != q) {
                        d.setItemStack(item(t, q));
                        quadros.put(u, q);
                    }
                }
                if (d.isGlowing() != z.isGlowing()) d.setGlowing(z.isGlowing());
                if (novo || rever) {
                    for (Player p : w.getPlayers()) {
                        boolean tres = tem(p);
                        ver(p, d, tres);
                        ver(p, z, !tres);
                    }
                }
            }
        }
        if (rever) {
            for (Iterator<Map.Entry<UUID, ItemDisplay>> i = modelos.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry<UUID, ItemDisplay> en = i.next();
                Entity z = Bukkit.getEntity(en.getKey());
                if (z != null && z.isValid() && !z.isDead() && en.getValue().isValid()) continue;
                if (en.getValue().isValid()) en.getValue().remove();
                quadros.remove(en.getKey());
                i.remove();
            }
        }
    }

    private void ver(Player p, Entity e, boolean sim) {
        if (e == null || !e.isValid() || sim == p.canSee(e)) return;
        if (sim) p.showEntity(pl, e);
        else p.hideEntity(pl, e);
    }

    void limpar() {
        for (ItemDisplay d : modelos.values()) if (d.isValid()) d.remove();
        modelos.clear();
        quadros.clear();
        for (World w : Bukkit.getWorlds()) {
            if (!pl.mundoDeTiro(w)) continue;
            for (Zombie z : w.getEntitiesByClass(Zombie.class)) for (Player p : w.getPlayers()) if (!p.canSee(z)) p.showEntity(pl, z);
        }
    }

    /** Quem ve o 3D nao recebe o som de dor do zumbi escondido: toca daqui. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dor(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Zombie z) || pl.zumbis.tipo(z) == null || e.getFinalDamage() <= 0) return;
        Location l = z.getLocation().add(0, z.getHeight() * 0.6, 0);
        for (Player p : z.getWorld().getPlayers()) {
            if (p.canSee(z) || p.getLocation().distanceSquared(l) > 900) continue;
            p.playSound(l, Sound.ENTITY_ZOMBIE_HURT, 0.9f, 0.8f + (float) Math.random() * 0.3f);
            p.spawnParticle(Particle.DAMAGE_INDICATOR, l, 3, 0.2, 0.3, 0.2, 0.05);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morte(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Zombie z) || pl.zumbis.tipo(z) == null) return;
        ItemDisplay d = modelos.remove(z.getUniqueId());
        quadros.remove(z.getUniqueId());
        if (d == null) return;
        if (d.isValid()) d.remove();
        Location l = z.getLocation().add(0, z.getHeight() * 0.5, 0);
        for (Player p : z.getWorld().getPlayers()) {
            if (p.canSee(z) || p.getLocation().distanceSquared(l) > 1600) continue;
            p.playSound(l, Sound.ENTITY_ZOMBIE_DEATH, 1f, 0.85f);
            p.spawnParticle(Particle.BLOCK, l, 24, 0.3, 0.5, 0.3, 0, Material.REDSTONE_BLOCK.createBlockData());
            p.spawnParticle(Particle.POOF, l, 8, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Golpe de perto de quem ve o 3D (o zumbi de verdade esta escondido para ele, o clique nao chega no bicho). */
    @EventHandler(ignoreCancelled = true)
    public void golpe(io.papermc.paper.event.player.PlayerArmSwingEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !pl.mundoDeTiro(p.getWorld()) || !tem(p)) return;
        if (pl.armas.armaDe(p.getInventory().getItemInMainHand()) != null) return;   // com arma de fogo, esquerdo = recarregar
        long agora = System.currentTimeMillis();
        if (agora - ultimoGolpe.getOrDefault(p.getUniqueId(), 0L) < 250) return;
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 3.2, org.bukkit.FluidCollisionMode.NEVER, true, 0.25,
                x -> x instanceof Zombie zz && pl.zumbis.tipo(zz) != null && !zz.isDead() && !p.canSee(zz));
        if (r == null || !(r.getHitEntity() instanceof Zombie z)) return;
        ultimoGolpe.put(p.getUniqueId(), agora);
        p.attack(z);
    }
}
