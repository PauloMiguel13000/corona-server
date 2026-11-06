package br.servidor.poderes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * So aparencia das habilidades: tridentes e flechas de verdade (sem dano, sem pegar do chao), displays de bloco
 * e de item (raio do Kamehameha, onda de agua, tridente gigante). Nada disso fica salvo no mundo, nada causa dano
 * (o dano continua so na logica da habilidade) e tudo some sozinho.
 */
final class Visuais implements Listener {

    private final Poderes pl;
    final NamespacedKey chave;
    private final Set<UUID> vivos = new HashSet<>();

    Visuais(Poderes pl) {
        this.pl = pl;
        this.chave = new NamespacedKey(pl, "visual");
    }

    private void marcar(Entity e, int vidaTicks) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(chave, PersistentDataType.BYTE, (byte) 1);
        vivos.add(e.getUniqueId());
        Bukkit.getScheduler().runTaskLater(pl, () -> remover(e), Math.max(2, vidaTicks));
    }

    /** Para enfeites criados fora daqui (ex.: textos): some sozinho e nao fica salvo. */
    void marcarVisual(Entity e, int vidaTicks) {
        marcar(e, vidaTicks);
    }

    void remover(Entity e) {
        if (e == null) return;
        vivos.remove(e.getUniqueId());
        if (e.isValid()) e.remove();
    }

    void removerTodos() {
        for (UUID id : new HashSet<>(vivos)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        vivos.clear();
    }

    boolean ehVisual(Entity e) {
        return e != null && e.getPersistentDataContainer().has(chave, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ tridente e flechas que voam (so aparencia)

    /** Tridente de verdade voando reto (sem gravidade), sem dano. Some ao bater ou depois de 'vidaTicks'. */
    Trident tridente(Location de, Vector dir, double velocidade, int vidaTicks, boolean brilho) {
        World w = de.getWorld();
        Location l = de.clone();
        l.setDirection(dir);
        Trident t = w.spawn(l, Trident.class, x -> {
            x.setGravity(false);
            x.setDamage(0);
            x.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            x.setGlint(brilho);
            x.setSilent(true);
            x.setVelocity(dir.clone().normalize().multiply(velocidade));
        });
        marcar(t, vidaTicks);
        return t;
    }

    /** Flecha de verdade voando (com ou sem gravidade), sem dano. Fogo = flecha em chamas. */
    Arrow flecha(Location de, Vector dir, double velocidade, int vidaTicks, boolean gravidade, boolean fogo, Color cor) {
        World w = de.getWorld();
        Location l = de.clone();
        l.setDirection(dir);
        Arrow a = w.spawn(l, Arrow.class, x -> {
            x.setGravity(gravidade);
            x.setDamage(0);
            x.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            x.setCritical(true);
            x.setSilent(true);
            if (cor != null) x.setColor(cor);
            if (fogo) x.setFireTicks(200);
            x.setVelocity(dir.clone().normalize().multiply(velocidade));
        });
        marcar(a, vidaTicks);
        return a;
    }

    /** Os projeteis de enfeite nunca acertam nada: somem ao encostar. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void bateu(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!ehVisual(pr)) return;
        e.setCancelled(true);
        remover(pr);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semDano(EntityDamageByEntityEvent e) {
        if (ehVisual(e.getDamager())) e.setCancelled(true);
    }

    /** Bloco de enfeite (FallingBlock) nunca vira bloco nem solta item: some ao encostar no chao. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void blocoNaoAssenta(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (!ehVisual(e.getEntity())) return;
        e.setCancelled(true);
        remover(e.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void naoSoltaItem(org.bukkit.event.entity.EntityDropItemEvent e) {
        if (ehVisual(e.getEntity())) e.setCancelled(true);
    }

    /** Itens de enfeite: ninguem pega, funil nao puxa, nao juntam com outros itens. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void ninguemPega(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (ehVisual(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void funilNaoPuxa(org.bukkit.event.inventory.InventoryPickupItemEvent e) {
        if (ehVisual(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void naoJunta(org.bukkit.event.entity.ItemMergeEvent e) {
        if (ehVisual(e.getEntity()) || ehVisual(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void nuvemSemEfeito(org.bukkit.event.entity.AreaEffectCloudApplyEvent e) {
        if (ehVisual(e.getEntity())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ displays

    private static final Display.Brightness CLARO = new Display.Brightness(15, 15);

    BlockDisplay bloco(Location l, BlockData dado, int vidaTicks, Color brilho) {
        Location p = l.clone();
        p.setYaw(0);
        p.setPitch(0);
        BlockDisplay d = l.getWorld().spawn(p, BlockDisplay.class, x -> {
            x.setBlock(dado);
            x.setBrightness(CLARO);
            x.setTeleportDuration(2);
            x.setInterpolationDuration(2);
            x.setViewRange(1.5f);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.001f), new Quaternionf()));
            if (brilho != null) {
                x.setGlowing(true);
                x.setGlowColorOverride(brilho);
            }
        });
        marcar(d, vidaTicks);
        return d;
    }

    ItemDisplay item(Location l, ItemStack it, int vidaTicks, Color brilho) {
        Location p = l.clone();
        p.setYaw(0);
        p.setPitch(0);
        ItemDisplay d = l.getWorld().spawn(p, ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setBrightness(CLARO);
            x.setTeleportDuration(2);
            x.setInterpolationDuration(2);
            x.setViewRange(1.5f);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            if (brilho != null) {
                x.setGlowing(true);
                x.setGlowColorOverride(brilho);
            }
        });
        marcar(d, vidaTicks);
        return d;
    }

    /**
     * Estica um display de bloco como uma barra: comeca em 'de', vai na direcao 'dir' por 'comprimento',
     * com 'grossura' de lado (centrada na linha).
     */
    static void barra(BlockDisplay d, Location de, Vector dir, double comprimento, double grossura, int interpolar) {
        if (!d.isValid()) return;
        Location l = de.clone();
        l.setYaw(0);
        l.setPitch(0);
        d.teleport(l);
        Vector n = dir.clone().normalize();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) n.getX(), (float) n.getY(), (float) n.getZ()));
        float g = (float) grossura;
        Vector3f centro = new Vector3f(-g / 2f, -g / 2f, 0).rotate(q);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interpolar);
        d.setTransformation(new Transformation(centro, q, new Vector3f(g, g, (float) comprimento), new Quaternionf()));
    }

    /** Escala um display (centrado no ponto do display), com giro em Y. */
    static void escala(Display d, float s, float giroY, int interpolar) {
        if (!d.isValid()) return;
        Quaternionf q = new Quaternionf().rotationY(giroY);
        Vector3f t = d instanceof BlockDisplay ? new Vector3f(-s / 2f, -s / 2f, -s / 2f).rotate(q) : new Vector3f();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interpolar);
        d.setTransformation(new Transformation(t, q, new Vector3f(s), new Quaternionf()));
    }

    /** Item (ex.: tridente) apontando para 'dir' com tamanho 's'. */
    static void apontar(ItemDisplay d, Vector dir, float s, int interpolar) {
        if (!d.isValid()) return;
        Vector n = dir.clone().normalize();
        // o modelo do tridente fica "em pe" (ponta para cima): gira +Y para a direcao
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) n.getX(), (float) n.getY(), (float) n.getZ()));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interpolar);
        d.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(s), new Quaternionf()));
    }

    /** Particula segura: a que pede dado (cor, bloco) vira CRIT. */
    static Particle seguro(Particle p) {
        return p != null && p.getDataType() == Void.class ? p : Particle.CRIT;
    }

    static ItemStack tridenteItem(boolean brilho) {
        ItemStack it = new ItemStack(Material.TRIDENT);
        if (brilho) {
            var m = it.getItemMeta();
            m.setEnchantmentGlintOverride(true);
            it.setItemMeta(m);
        }
        return it;
    }
}
