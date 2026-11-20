package br.servidor.masmorras;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 20/11 (Paulo: "se ele tiver no lvl 5 so pode ate protecao 2, ai lvl 6 ele pode usar protecao 4, espada voce tambem ja
 * sabe... e assim voce segue"): dentro das masmorras o encantamento so vale ate o teto do NIVEL DE MASMORRA do jogador.
 * O item continua no corpo (nada sai do inventario, nada se perde): so o excesso nao conta.
 *  - Protecao (e Protecao contra Fogo/Explosao/Projeteis): o dano recebido e refeito como se o encantamento fosse o teto;
 *  - Afiacao/Julgamento/Ruina dos Artropodes: o dano do golpe e refeito com o teto;
 *  - Forca (arco): o dano da flecha e refeito com o teto.
 * Tetos por nivel em config (teto-equip.protecao / teto-equip.afiacao, nivel 1 a 6). Fora das masmorras nao muda nada.
 */
final class TetoEquip implements Listener {

    private static final int[] PROT_PADRAO = {1, 1, 1, 2, 2, 4};
    private static final int[] AFI_PADRAO = {1, 1, 2, 2, 3, 5};

    private final Masmorras pl;
    private final Map<UUID, long[]> cacheNivel = new HashMap<>();   // uuid -> {nivel, valido ate}
    private final Map<UUID, Long> avisado = new HashMap<>();

    TetoEquip(Masmorras pl) {
        this.pl = pl;
    }

    private boolean ligado() {
        return pl.getConfig().getBoolean("teto-equip.ligado", true);
    }

    static boolean naMasmorra(Entity e) {
        return e.getWorld().getName().startsWith("masmorras");
    }

    int nivel(Player p) {
        long agora = System.currentTimeMillis();
        long[] c = cacheNivel.get(p.getUniqueId());
        if (c != null && c[1] > agora) return (int) c[0];
        int n = pl.progresso == null ? Progresso.MAX : pl.progresso.nivel(p);
        cacheNivel.put(p.getUniqueId(), new long[] {n, agora + 10_000L});
        return n;
    }

    private int teto(String chave, int[] padrao, int nivel) {
        List<Integer> l = pl.getConfig().getIntegerList("teto-equip." + chave);
        int[] v = l.size() >= padrao.length ? l.stream().mapToInt(Integer::intValue).toArray() : padrao;
        return v[Math.max(0, Math.min(v.length - 1, nivel - 1))];
    }

    int tetoProtecao(Player p) {
        return teto("protecao", PROT_PADRAO, nivel(p));
    }

    int tetoAfiacao(Player p) {
        return teto("afiacao", AFI_PADRAO, nivel(p));
    }

    // ------------------------------------------------------------------ defesa

    /** Pontos de protecao do encantamento (EPF do jogo) para esse tipo de dano, com cada nivel limitado a 'teto'. */
    private static int epf(Player p, DamageCause causa, int teto) {
        int t = 0;
        for (ItemStack a : p.getInventory().getArmorContents()) {
            if (a == null || !a.hasItemMeta()) continue;
            t += Math.min(teto, a.getEnchantmentLevel(Enchantment.PROTECTION));
            int fogo = Math.min(teto, a.getEnchantmentLevel(Enchantment.FIRE_PROTECTION));
            int expl = Math.min(teto, a.getEnchantmentLevel(Enchantment.BLAST_PROTECTION));
            int proj = Math.min(teto, a.getEnchantmentLevel(Enchantment.PROJECTILE_PROTECTION));
            switch (causa) {
                case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> t += 2 * fogo;
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> t += 2 * expl;
                case PROJECTILE -> t += 2 * proj;
                default -> { }
            }
        }
        return Math.min(20, t);
    }

    private static boolean semProtecao(DamageCause c) {
        return switch (c) {
            case VOID, STARVATION, SUFFOCATION, DROWNING, CUSTOM, KILL, WORLD_BORDER, SUICIDE -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void defesa(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !ligado() || !naMasmorra(p) || semProtecao(e.getCause())) return;
        int teto = tetoProtecao(p);
        int real = epf(p, e.getCause(), 99), cap = epf(p, e.getCause(), teto);
        if (real <= cap) return;
        double fator = (1 - cap / 25.0) / (1 - real / 25.0);   // o jogo tira 4% por ponto (ate 80%)
        e.setDamage(e.getDamage() * fator);
        avisar(p);
    }

    // ------------------------------------------------------------------ ataque

    private static double bonusAfiacao(int nv) {
        return nv <= 0 ? 0 : 0.5 * nv + 0.5;
    }

    private static boolean sensivel(String tag, Entity alvo) {
        try {
            Tag<org.bukkit.entity.EntityType> t = Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, org.bukkit.NamespacedKey.minecraft(tag), org.bukkit.entity.EntityType.class);
            return t != null && t.isTagged(alvo.getType());
        } catch (Throwable ex) {
            return false;
        }
    }

    /** Dano da arma (atributo) + 1 da mao. */
    private static double baseArma(ItemStack it) {
        double b = 1.0;
        if (it == null || it.getType().isAir()) return b;
        try {
            java.util.Collection<AttributeModifier> ms = it.hasItemMeta() && it.getItemMeta().hasAttributeModifiers()
                    ? it.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE)
                    : it.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND).get(Attribute.ATTACK_DAMAGE);
            if (ms != null) for (AttributeModifier m : ms) if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) b += m.getAmount();
        } catch (Throwable ignorado) {
        }
        return Math.max(1.0, b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void ataque(EntityDamageByEntityEvent e) {
        if (!ligado() || !naMasmorra(e.getEntity())) return;
        if (e.getDamager() instanceof Player p && e.getCause() == DamageCause.ENTITY_ATTACK) {
            ItemStack arma = p.getInventory().getItemInMainHand();
            if (arma.getType().isAir() || !arma.hasItemMeta()) return;
            int teto = tetoAfiacao(p);
            double real = bonusAfiacao(arma.getEnchantmentLevel(Enchantment.SHARPNESS)), cap = bonusAfiacao(Math.min(teto, arma.getEnchantmentLevel(Enchantment.SHARPNESS)));
            if (sensivel("sensitive_to_smite", e.getEntity())) {
                real += 2.5 * arma.getEnchantmentLevel(Enchantment.SMITE);
                cap += 2.5 * Math.min(teto, arma.getEnchantmentLevel(Enchantment.SMITE));
            }
            if (sensivel("sensitive_to_bane_of_arthropods", e.getEntity())) {
                real += 2.5 * arma.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
                cap += 2.5 * Math.min(teto, arma.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS));
            }
            if (real <= cap) return;
            double base = baseArma(arma);
            e.setDamage(e.getDamage() * (base + cap) / (base + real));
            avisar(p);
            return;
        }
        if (e.getDamager() instanceof AbstractArrow f && f.getShooter() instanceof Player p) {
            Integer forca = f.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(pl, "teto_forca"), PersistentDataType.INTEGER);
            if (forca == null || forca <= 0) return;
            int cap = Math.min(tetoAfiacao(p), forca);
            if (cap >= forca) return;
            double fr = 1 + 0.25 * (forca + 1), fc = cap <= 0 ? 1 : 1 + 0.25 * (cap + 1);
            e.setDamage(e.getDamage() * fc / fr);
            avisar(p);
        }
    }

    /** Marca na flecha o nivel de Forca do arco (o dano dela e refeito ao acertar). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void atirou(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || e.getBow() == null || !(e.getProjectile() instanceof AbstractArrow f)) return;
        int forca = e.getBow().getEnchantmentLevel(Enchantment.POWER);
        if (forca > 0) f.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(pl, "teto_forca"), PersistentDataType.INTEGER, forca);
    }

    // ------------------------------------------------------------------ aviso

    private void avisar(Player p) {
        long agora = System.currentTimeMillis();
        Long ult = avisado.get(p.getUniqueId());
        if (ult != null && agora - ult < 60_000L) return;
        avisado.put(p.getUniqueId(), agora);
        p.sendMessage(Masmorras.txt(texto(p)));
    }

    String texto(Player p) {
        return "§6§lTETO §8» §7No §fNível de Masmorra " + nivel(p) + " §7vale até §bProteção " + Progresso.romano(Math.max(1, tetoProtecao(p)))
                + " §7e §cAfiação/Força " + Progresso.romano(Math.max(1, tetoAfiacao(p))) + "§7. O que passar disso não conta aqui. §8(suba de nível para liberar)";
    }

    @EventHandler
    public void entrou(PlayerChangedWorldEvent e) {
        cacheNivel.remove(e.getPlayer().getUniqueId());
    }

    void esquecer(Player p) {
        cacheNivel.remove(p.getUniqueId());
    }
}
