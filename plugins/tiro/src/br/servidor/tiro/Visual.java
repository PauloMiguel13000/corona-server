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
 * Texturas dos jogos de tiro (15/11): um pacote pequeno so deles (armas desenhadas e os infectados ESCULPIDOS: modelo proprio por tipo, 3 poses), separado
 * do pacote principal. Java recebe por link ao entrar (sem obrigar); Bedrock recebe pelo CoronaPack (pacotes-fora).
 * Quem tem o pacote ve o modelo 3D do infectado (ItemDisplay que anda junto) e a arma desenhada; quem nao tem
 * continua vendo o zumbi do jogo e a enxada. Para quem ve o 3D o zumbi de verdade fica escondido, entao o golpe
 * de perto (faca, soco) e o som de dor sao feitos aqui.
 */
final class Visual implements Listener {

    static final String URL_PADRAO = "https://corona-server.duckdns.org/pacotes/corona_tiro_java-545f42d911.zip";
    static final String SHA1_PADRAO = "545f42d911428e66208cd5edfe2c3827937ffcc6";
    private static final UUID ID = UUID.nameUUIDFromBytes("corona-tiro-pack".getBytes(StandardCharsets.UTF_8));

    private final Tiro pl;
    private final Set<UUID> comPacote = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemDisplay> modelos = new HashMap<>();
    private final Map<UUID, Integer> quadros = new HashMap<>();
    private final Map<UUID, Location> ultimo = new HashMap<>();
    private final Map<UUID, Float> escalas = new HashMap<>();
    private final Map<UUID, Long> ultimoGolpe = new HashMap<>();
    private int conta;
    private static final int[] CICLO = {0, 3, 2, 3};
    /** Soldados da Missao: ate quando (tick do Visual) cada um esta com o quadro de tiro (clarao no cano). */
    private final Map<UUID, Integer> tiroAte = new HashMap<>();

    void atirando(Zombie z) {
        tiroAte.put(z.getUniqueId(), conta + 3);
    }

    /** 16/11: soldados da Missao agachados atras da cobertura (quadro 5 do modelo). */
    private final Set<UUID> agachados = ConcurrentHashMap.newKeySet();

    void agachado(Zombie z, boolean sim) {
        if (sim) agachados.add(z.getUniqueId());
        else agachados.remove(z.getUniqueId());
    }

    Visual(Tiro pl) {
        this.pl = pl;
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 2L, 1L);
        Bukkit.getScheduler().runTaskTimer(pl, this::rosnados, 40L, 20L);
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
    /** Icone de arma em menu (loja do Mercador): com o desenho, para quem tem o pacote. */
    ItemStack desenhar(Player p, ItemStack it) {
        Armas.Arma a = pl.armas.armaDe(it);
        if (a != null && tem(p)) it.setData(DataComponentTypes.ITEM_MODEL, Key.key("corona", "armas/" + a.id()));
        return it;
    }

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
        return forma(e, 0f, 0f, 0f);
    }

    private static Transformation forma(float e, float dy, float dz, float incl) {
        return new Transformation(new Vector3f(0, 1.5f * e + dy, dz), new Quaternionf().rotateX(incl), new Vector3f(e, e, e), new Quaternionf());
    }

    /*
     * 16/11 (Paulo: "anime pra ficar perfeito"): por cima dos quadros do modelo, o proprio ItemDisplay se mexe com
     * interpolacao suave: sobe e desce a cada passo, da o tranco do coice no tiro, respira parado e afunda/levanta
     * devagar ao agachar (em vez de pular de uma pose para a outra). Java; no Bedrock fica so a troca de quadro.
     */
    private final Map<UUID, float[]> pose = new HashMap<>();          // dy, dz, inclinacao aplicados agora
    private final Map<UUID, Integer> soltarEm = new HashMap<>();

    private void animar(UUID u, ItemDisplay d, int ant, int q, boolean andando, int fase) {
        float e = escalas.getOrDefault(u, 0.92f);
        float[] agora = pose.computeIfAbsent(u, k -> new float[3]);
        float dy, dz, incl;
        int dur;
        if (q == 1 && ant != 1) {                                       // coice do tiro: vai para tras na hora
            dy = 0.035f * e;                                            // (so vertical: o sentido do eixo Z do display varia)
            dz = 0f;
            incl = 0f;
            dur = 1;
            soltarEm.put(u, conta + 2);
        } else if (q == 5 && ant != 5) {                                // agachou: comeca alto e afunda
            aplicar(d, e, 0.3f * e, 0f, 0f, 0);
            agora[0] = Float.NaN;
            dy = 0f;
            dz = 0f;
            incl = 0f;
            dur = 4;
        } else if (ant == 5 && q != 5) {                                // levantou: comeca baixo e sobe
            aplicar(d, e, -0.3f * e, 0f, 0f, 0);
            agora[0] = Float.NaN;
            dy = 0f;
            dz = 0f;
            incl = 0f;
            dur = 4;
        } else if (conta < soltarEm.getOrDefault(u, 0)) {
            return;                                                     // ainda no coice
        } else if (andando) {
            boolean alto = q == 3;                                      // na passagem o corpo sobe, no passo desce
            dy = alto ? 0.045f * e : -0.01f * e;
            dz = 0f;
            incl = 0f;
            dur = 3;
        } else {
            dy = 0.012f * e * (float) Math.sin(fase * 0.12);            // respiracao parado
            dz = 0f;
            incl = 0f;
            dur = 10;
        }
        if (agora[0] == dy && agora[1] == dz && agora[2] == incl) return;
        if (!andando && q != 1 && q != 5 && ant == q && conta % 10 != 0 && !Float.isNaN(agora[0])) return;   // respiracao: so a cada 10 tiques
        if (Float.isNaN(agora[0])) {
            // a pose de partida acabou de ser aplicada sem interpolacao: a volta vem no proximo tique
            agora[0] = 1e9f;
            final float fy = dy, fz = dz, fi = incl;
            final int fdur = dur;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (d.isValid()) aplicar(d, e, fy, fz, fi, fdur);
            }, 1L);
            agora[0] = dy;
            agora[1] = dz;
            agora[2] = incl;
            return;
        }
        aplicar(d, e, dy, dz, incl, dur);
        agora[0] = dy;
        agora[1] = dz;
        agora[2] = incl;
    }

    private static void aplicar(ItemDisplay d, float e, float dy, float dz, float incl, int dur) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(dur);
        d.setTransformation(forma(e, dy, dz, incl));
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
                // o modelo fica um pouco para tras do zumbi: colado no jogador ele nao engole a camera
                double recuo = 0.4 * escalas.getOrDefault(z.getUniqueId(), 0.92f);
                double rad = Math.toRadians(l.getYaw());
                l.add(Math.sin(rad) * recuo, 0, -Math.cos(rad) * recuo);
                LivingEntity alvo = z.getTarget();
                // animacao: andando roda o ciclo passo A, passagem, passo B, passagem (mais rapido nos que correm);
                // colado no alvo alterna bracos erguidos e golpe; parado fica em pe
                Location antes = ultimo.put(u, l.clone());
                boolean andando = antes != null && antes.getWorld() == w && antes.distanceSquared(l) > 0.0004;
                boolean atacando = alvo != null && alvo.getWorld() == w && alvo.getLocation().distanceSquared(z.getLocation()) < 7.0;
                int fase = conta + (int) (u.getLeastSignificantBits() & 7);
                int q;
                if (t.soldado()) q = tiroAte.getOrDefault(u, 0) >= conta ? 1 : t != Zumbis.Tipo.JUGGERNAUT && agachados.contains(u) ? 5
                        : andando ? CICLO[(fase / 3) % 4] : 3;
                else if (atacando) q = (fase / 5) % 2 == 0 ? 1 : 4;
                else if (andando) q = CICLO[(fase / (t.veloc >= 0.3 ? 3 : 4)) % 4];
                else q = 3;
                ItemDisplay d = modelos.get(u);
                boolean novo = d == null || !d.isValid();
                if (novo) {
                    var esc = z.getAttribute(Attribute.SCALE);
                    double base = esc == null ? 1.0 : esc.getBaseValue();
                    if (agachados.contains(u)) base /= Missao.AGACHADO;          // agachado encolhe o zumbi, nao o modelo
                    float e = (float) (base * 0.92);
                    escalas.put(u, e);
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
                        if (t.soldado()) x.setGlowColorOverride(org.bukkit.Color.RED);   // marcado: contorno vermelho
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
                    int ant = quadros.getOrDefault(u, 0);
                    if (ant != q) {
                        d.setItemStack(item(t, q));
                        quadros.put(u, q);
                    }
                    if (t.soldado() && t != Zumbis.Tipo.JUGGERNAUT) animar(u, d, ant, q, andando, fase);
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
                agachados.remove(en.getKey());
                pose.remove(en.getKey());
                soltarEm.remove(en.getKey());
                ultimo.remove(en.getKey());
                escalas.remove(en.getKey());
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

    /** 15/11 (Paulo: "todos os zumbis tem o mesmo som, tem que ser diferente"; "soldado e humano e faz som de zumbi"):
     *  o zumbi de verdade e mudo e a voz de cada tipo sai daqui, para todo mundo. {dor, morte, rosnado}, tom. */
    private record Voz(Sound dor, Sound morte, Sound rosnado, float tom, float vol) {}

    private static Voz voz(Zumbis.Tipo t) {
        return switch (t) {
            case CORREDOR -> new Voz(Sound.ENTITY_HUSK_HURT, Sound.ENTITY_HUSK_DEATH, Sound.ENTITY_HUSK_AMBIENT, 1.45f, 0.9f);
            case TANQUE -> new Voz(Sound.ENTITY_RAVAGER_HURT, Sound.ENTITY_RAVAGER_DEATH, Sound.ENTITY_RAVAGER_AMBIENT, 0.75f, 1.2f);
            case CUSPIDOR -> new Voz(Sound.ENTITY_DROWNED_HURT, Sound.ENTITY_DROWNED_DEATH, Sound.ENTITY_DROWNED_AMBIENT, 1.25f, 1f);
            case EXPLOSIVO -> new Voz(Sound.ENTITY_ZOMBIE_VILLAGER_HURT, Sound.ENTITY_ZOMBIE_VILLAGER_DEATH, Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT, 0.6f, 1f);
            case CHEFE -> new Voz(Sound.ENTITY_WARDEN_HURT, Sound.ENTITY_WARDEN_DEATH, Sound.ENTITY_WARDEN_AMBIENT, 0.9f, 1.6f);
            case COMUM -> new Voz(Sound.ENTITY_ZOMBIE_HURT, Sound.ENTITY_ZOMBIE_DEATH, Sound.ENTITY_ZOMBIE_AMBIENT, 0.95f, 0.9f);
            default -> new Voz(Sound.ENTITY_PLAYER_HURT, Sound.ENTITY_PLAYER_DEATH, null, 0.8f, 0.9f);   // soldados: gente
        };
    }

    /** Rosnado de vez em quando (chamado uma vez por segundo). */
    void rosnados() {
        for (World w : Bukkit.getWorlds()) {
            if (!pl.mundoDeTiro(w) || w.getPlayers().isEmpty()) continue;
            for (Zombie z : w.getEntitiesByClass(Zombie.class)) {
                Zumbis.Tipo t = pl.zumbis.tipo(z);
                if (t == null || z.isDead() || Math.random() > 0.11) continue;
                Voz v = voz(t);
                if (v.rosnado() != null) w.playSound(z.getLocation(), v.rosnado(), v.vol(), v.tom() * (0.92f + (float) Math.random() * 0.16f));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dor(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Zombie z) || pl.zumbis.tipo(z) == null || e.getFinalDamage() <= 0) return;
        Location l = z.getLocation().add(0, z.getHeight() * 0.6, 0);
        Voz v = voz(pl.zumbis.tipo(z));
        z.getWorld().playSound(l, v.dor(), v.vol(), v.tom() * (0.92f + (float) Math.random() * 0.16f));
        for (Player p : z.getWorld().getPlayers())
            if (!p.canSee(z) && p.getLocation().distanceSquared(l) <= 900) p.spawnParticle(Particle.DAMAGE_INDICATOR, l, 3, 0.2, 0.3, 0.2, 0.05);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morte(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Zombie z) || pl.zumbis.tipo(z) == null) return;
        Voz v = voz(pl.zumbis.tipo(z));
        z.getWorld().playSound(z.getLocation(), v.morte(), v.vol(), v.tom());
        ItemDisplay d = modelos.remove(z.getUniqueId());
        quadros.remove(z.getUniqueId());
        ultimo.remove(z.getUniqueId());
        escalas.remove(z.getUniqueId());
        if (d == null) return;
        if (d.isValid()) d.remove();
        Location l = z.getLocation().add(0, z.getHeight() * 0.5, 0);
        for (Player p : z.getWorld().getPlayers()) {
            if (p.canSee(z) || p.getLocation().distanceSquared(l) > 1600) continue;
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
