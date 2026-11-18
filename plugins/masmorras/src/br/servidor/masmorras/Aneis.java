package br.servidor.masmorras;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Aneis elementais (18/11, Paulo): vao na mao secundaria (onde vai o escudo), dao um buff e um efeito no golpe, e cada
 * chefe tem um elemento. Roda dos elementos: Agua vence Fogo, Fogo vence Vento, Vento vence Terra, Terra vence Raio,
 * Raio vence Agua. O Vazio e neutro: um pouco melhor contra tudo, sem vantagem nem fraqueza.
 * Com vantagem: +30% de dano no chefe e -30% do dano dele (monstros comuns da masmorra: metade disso). Com fraqueza: o
 * contrario, mais leve. Clique direito com o anel na mao poe ele na mao secundaria (o Bedrock nao deixa por la na mao).
 * So nos mundos das masmorras.
 */
final class Aneis implements Listener {

    enum Elem {
        FOGO("Fogo", "§c", Material.BLAZE_POWDER, "Resistência ao fogo; golpes queimam"),
        AGUA("Água", "§9", Material.HEART_OF_THE_SEA, "Respira e nada rápido; golpes deixam lento"),
        TERRA("Terra", "§2", Material.EMERALD, "Resistência I; golpes fazem o alvo tropeçar"),
        VENTO("Vento", "§f", Material.BREEZE_ROD, "Velocidade e pulo; golpes empurram longe"),
        RAIO("Raio", "§e", Material.GLOWSTONE_DUST, "Pressa II; golpes podem dar choque"),
        VAZIO("Vazio", "§5", Material.ECHO_SHARD, "Neutro: +10% de dano e -5% de dano contra tudo");

        final String nome, cor, efeito;
        final Material icone;

        Elem(String nome, String cor, Material icone, String efeito) {
            this.nome = nome;
            this.cor = cor;
            this.icone = icone;
            this.efeito = efeito;
        }

        String tag() {
            return cor + nome;
        }

        /** Quem este elemento vence. */
        Elem vence() {
            return switch (this) {
                case AGUA -> FOGO;
                case FOGO -> VENTO;
                case VENTO -> TERRA;
                case TERRA -> RAIO;
                case RAIO -> AGUA;
                default -> null;
            };
        }

        Elem perdePara() {
            for (Elem e : values()) if (e.vence() == this) return e;
            return null;
        }
    }

    /** Elemento de cada chefe (o resto: Nether = Fogo, End = Vazio, mundo normal = Terra). */
    private static final Map<String, Elem> CHEFES = new HashMap<>();

    static {
        String[][] l = {
                {"FOGO", "FORJA BASTIAO WITHER COVIL VULCAO DELTA CARMESIM OBSIDIANA CRATERA SANGUE"},
                {"AGUA", "AFOGADA FAROL NAUFRAGIO GELO URSO GELEIRA PANTANO ESGOTO"},
                {"TERRA", "CRIPTA NINHO MINA DESERTO FUNGOS COVIL_LOBOS BOSQUE_PALIDO SELVA FAZENDA CEMITERIO LABIRINTO MAUSOLEU TESOURO VIUVA CASTELO"},
                {"VENTO", "VENTOS TORRE OBSERVATORIO ILHAS JARDIM MOINHO ACAMPAMENTO QUARTEL"},
                {"RAIO", "TROVAO CAMARA_TESTES LABORATORIO RELOGIO COLISEU CATEDRAL"},
                {"VAZIO", "CIDADELA PRISAO NAVE TRONO CORO ABISMO CIDADE_ANTIGA CRIPTA_REAL SECRETA BIBLIOTECA ALMAS DISTORCIDO"}};
        for (String[] x : l) for (String id : x[1].split(" ")) CHEFES.put(id, Elem.valueOf(x[0]));
    }

    static Elem elementoDe(Tipo t) {
        Elem e = CHEFES.get(t.id);
        if (e != null) return e;
        return t.onde == Tipo.Onde.NETHER ? Elem.FOGO : t.onde == Tipo.Onde.END ? Elem.VAZIO : Elem.TERRA;
    }

    private final Masmorras pl;
    private final NamespacedKey kAnel;

    Aneis(Masmorras pl) {
        this.pl = pl;
        kAnel = new NamespacedKey(pl, "anel");
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::buffs, 80L, 40L);
    }

    private boolean vale(org.bukkit.World w) {
        return w != null && (w.getName().toLowerCase(Locale.ROOT).startsWith("masmorras") || w.getName().equals(InfernoHerobrine.MUNDO));
    }

    // ------------------------------------------------------------------ item

    private static net.kyori.adventure.text.Component t(String s) {
        return Masmorras.txt(s).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    ItemStack anel(Elem e) {
        ItemStack it = new ItemStack(e.icone);
        ItemMeta m = it.getItemMeta();
        m.displayName(t(e.cor + "§lAnel de " + e.nome));
        Elem v = e.vence(), p = e.perdePara();
        m.lore(List.of(t("§7" + e.efeito),
                v != null ? t("§aForte contra chefes de " + v.tag() + " §7• §cfraco contra " + p.tag()) : t("§7Sem vantagem e sem fraqueza"),
                t(""), t("§eUse na mão secundária §7(onde vai o escudo)"), t("§8clique direito com ele na mão para colocar lá"), t("§8vale nas masmorras")));
        m.setEnchantmentGlintOverride(true);
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(kAnel, PersistentDataType.STRING, e.name());
        it.setItemMeta(m);
        return it;
    }

    Elem anelDe(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(kAnel, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return Elem.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    Elem usando(Player p) {
        return anelDe(p.getInventory().getItemInOffHand());
    }

    /** Clique direito com o anel na mao: vai para a mao secundaria (troca com o que estava la). */
    @EventHandler(priority = EventPriority.HIGH)
    public void colocar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack mao = p.getInventory().getItemInMainHand();
        Elem el = anelDe(mao);
        if (el == null) return;
        e.setCancelled(true);
        ItemStack antes = p.getInventory().getItemInOffHand();
        p.getInventory().setItemInOffHand(mao);
        p.getInventory().setItemInMainHand(antes);
        p.playSound(p.getLocation(), "minecraft:item.armor.equip_gold", 1f, 1.3f);
        p.sendActionBar(Masmorras.txt(el.cor + "Anel de " + el.nome + " §fna mão secundária."));
    }

    // ------------------------------------------------------------------ buffs (a cada 2 s)

    private void buffs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!vale(p.getWorld())) continue;
            Elem e = usando(p);
            if (e == null) continue;
            switch (e) {
                case FOGO -> p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, true));
                case AGUA -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, true, false, true));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0, true, false, true));
                }
                case TERRA -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, true));
                case VENTO -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, true));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 0, true, false, true));
                }
                case RAIO -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 1, true, false, true));
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ dano

    /** Elemento do monstro da masmorra (null se nao for de masmorra) e se e o chefe. */
    private Elem elementoDe(Entity x, boolean[] chefe) {
        Masmorra m = pl.de(x);
        if (m == null) return null;
        chefe[0] = x.getPersistentDataContainer().has(pl.kChefe);
        return elementoDe(m.tipo);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void dano(EntityDamageByEntityEvent e) {
        if (!vale(e.getEntity().getWorld())) return;
        Entity d = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        boolean[] chefe = {false};
        if (d instanceof Player p && !(e.getEntity() instanceof Player) && e.getEntity() instanceof LivingEntity alvo) {
            Elem meu = usando(p);
            if (meu == null) return;
            Elem dele = elementoDe(alvo, chefe);
            double f = 1.0;
            if (dele != null) {
                if (meu == Elem.VAZIO) f = 1.10;
                else if (meu.vence() == dele) f = chefe[0] ? 1.30 : 1.15;
                else if (dele.vence() == meu) f = chefe[0] ? 0.85 : 0.93;
                if (f > 1.05 && ThreadLocalRandom.current().nextInt(4) == 0)
                    alvo.getWorld().spawnParticle(Particle.DUST, alvo.getLocation().add(0, alvo.getHeight() * 0.7, 0), 6, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(cor(meu), 1.3f));
            }
            e.setDamage(e.getDamage() * f);
            if (e.getDamager() == p) golpe(p, alvo, meu);
            return;
        }
        if (e.getEntity() instanceof Player v && !(d instanceof Player)) {
            Elem meu = usando(v);
            if (meu == null) return;
            Elem dele = elementoDe(d, chefe);
            if (dele == null) return;
            double f = 1.0;
            if (meu == Elem.VAZIO) f = 0.95;
            else if (meu.vence() == dele) f = chefe[0] ? 0.70 : 0.85;
            else if (dele.vence() == meu) f = chefe[0] ? 1.20 : 1.10;
            e.setDamage(e.getDamage() * f);
        }
    }

    private static Color cor(Elem e) {
        return switch (e) {
            case FOGO -> Color.fromRGB(255, 90, 20);
            case AGUA -> Color.fromRGB(40, 120, 255);
            case TERRA -> Color.fromRGB(70, 160, 60);
            case VENTO -> Color.fromRGB(230, 240, 255);
            case RAIO -> Color.fromRGB(255, 230, 60);
            default -> Color.fromRGB(150, 60, 220);
        };
    }

    private void golpe(Player p, LivingEntity alvo, Elem e) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() >= 0.22) return;
        var l = alvo.getLocation().add(0, alvo.getHeight() * 0.5, 0);
        switch (e) {
            case FOGO -> {
                alvo.setFireTicks(Math.max(alvo.getFireTicks(), 60));
                alvo.getWorld().spawnParticle(Particle.FLAME, l, 10, 0.3, 0.4, 0.3, 0.02);
            }
            case AGUA -> {
                alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                alvo.getWorld().spawnParticle(Particle.SPLASH, l, 20, 0.4, 0.4, 0.4, 0.1);
            }
            case TERRA -> {
                alvo.setVelocity(alvo.getVelocity().add(new Vector(0, 0.45, 0)));
                alvo.getWorld().spawnParticle(Particle.BLOCK, alvo.getLocation(), 16, 0.4, 0.1, 0.4, 0, Material.DIRT.createBlockData());
            }
            case VENTO -> {
                Vector v = alvo.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (v.lengthSquared() > 0.01) alvo.setVelocity(v.normalize().multiply(0.9).setY(0.3));
                alvo.getWorld().spawnParticle(Particle.GUST, l, 1);
            }
            case RAIO -> {
                if (r.nextInt(2) == 0) {
                    alvo.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, l, 20, 0.4, 0.5, 0.4, 0.2);
                    alvo.getWorld().playSound(l, "minecraft:entity.lightning_bolt.thunder", 0.25f, 2f);
                    alvo.setNoDamageTicks(0);
                    alvo.damage(2.5);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ aviso e premio

    /** Quando o chefe chega: mostra o elemento dele e qual anel leva vantagem. */
    void avisar(List<Player> ps, Tipo t) {
        Elem e = elementoDe(t);
        Elem forte = e.perdePara();
        String msg = "§7Elemento do chefe: " + e.tag() + (forte != null ? " §8• §7leva vantagem quem usa §lAnel de " + forte.tag() : " §8• §7neutro (qualquer anel serve)");
        for (Player p : ps) p.sendMessage(Masmorras.txt(msg));
    }

    /** Chefe derrotado: 8% de chance de cair o anel do elemento dele para quem matou. */
    void premio(Player k, Tipo t) {
        if (k == null || ThreadLocalRandom.current().nextDouble() >= 0.08) return;
        Elem e = elementoDe(t);
        var sobra = k.getInventory().addItem(anel(e));
        if (!sobra.isEmpty()) k.getWorld().dropItemNaturally(k.getLocation(), anel(e));
        k.sendMessage(Masmorras.txt("§d✦ §fO chefe deixou cair um " + e.cor + "§lAnel de " + e.nome + "§f!"));
        k.playSound(k.getLocation(), "minecraft:entity.player.levelup", 1f, 1.5f);
    }
}
