package br.servidor.masmorras;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Reliquias das masmorras: todo equipamento que sai do chefe, dos baus ou (as vezes) dos monstros de uma masmorra
 * vira uma reliquia dela. Ganha o nome do chefe (ex.: "Espada do Rei Esqueleto"), brilho, lore, particulas na mao
 * e um poder com o tema da masmorra: armas e arcos tem um golpe especial (chance a cada acerto) e armaduras um bonus
 * de atributo (do proprio Minecraft, aparece no item).
 */
final class Reliquias implements Listener {

    /** Golpe especial das armas (chance a cada acerto). */
    enum Golpe {
        OSSOS, VENENO, ESMAGAR, MALDICAO, CONGELAR, AREIA, CORRENTEZA, ARCANO, INVESTIDA, ILUSAO, VENTANIA,
        SANGRAR, CHAMAS, GANANCIA, MURCHAR, ESTOURO, VAZIO, SONICO, HEROBRINE
    }

    /** O tema de cada masmorra: nome ("do Rei Esqueleto"), cor, particula, golpe e bonus da armadura. */
    record Tema(String de, String cor, Color brilho, Particle aura, Golpe golpe, String golpeNome, String golpeDesc,
                String attr, double valor, AttributeModifier.Operation op, String passivoNome, String passivoDesc) {}

    static final Map<String, Tema> TEMAS = new HashMap<>();

    private static void t(String id, String de, String cor, int r, int g, int b, Particle aura, Golpe golpe, String gn, String gd,
                          String attr, double valor, AttributeModifier.Operation op, String pn, String pd) {
        TEMAS.put(id, new Tema(de, cor, Color.fromRGB(r, g, b), aura, golpe, gn, gd, attr, valor, op, pn, pd));
    }

    static {
        AttributeModifier.Operation soma = AttributeModifier.Operation.ADD_NUMBER, pct = AttributeModifier.Operation.ADD_SCALAR;
        t("CRIPTA", "do Rei Esqueleto", "§f", 225, 225, 235, Particle.SOUL, Golpe.OSSOS, "Toque da Cripta",
                "deixa o alvo lento e fraco (3s)", "armor", 0.5, soma, "Ossos Duros", "+0,5 de armadura");
        t("NINHO", "da Rainha Aranha", "§2", 70, 170, 60, Particle.ITEM_SLIME, Golpe.VENENO, "Presa Venenosa",
                "envenena o alvo (4s)", "step_height", 0.25, soma, "Patas de Aranha", "sobe degraus mais altos andando");
        t("MINA", "do Mineiro Gigante", "§6", 255, 170, 50, Particle.WAX_ON, Golpe.ESMAGAR, "Golpe de Picareta",
                "dano extra e empurra o alvo", "block_break_speed", 0.08, pct, "Força de Mineiro", "quebra blocos 8% mais rápido");
        t("PANTANO", "da Bruxa Anciã", "§5", 160, 70, 210, Particle.WITCH, Golpe.MALDICAO, "Maldição da Bruxa",
                "um efeito ruim aleatório no alvo", "max_health", 1.0, soma, "Poção da Vida", "+½ coração de vida");
        t("GELO", "do Rei do Gelo", "§b", 150, 220, 255, Particle.SNOWFLAKE, Golpe.CONGELAR, "Toque Congelante",
                "congela e deixa o alvo muito lento", "movement_efficiency", 0.25, soma, "Passo Firme", "anda melhor na neve e em areia de almas");
        t("DESERTO", "do Faraó", "§e", 255, 215, 90, Particle.DUST, Golpe.AREIA, "Areia do Faraó",
                "cega o alvo e dá fome", "luck", 1.0, soma, "Tesouro do Faraó", "+1 de sorte");
        t("AFOGADA", "do Rei Afogado", "§3", 40, 170, 200, Particle.DRIPPING_WATER, Golpe.CORRENTEZA, "Correnteza",
                "puxa o alvo para você e deixa lento", "oxygen_bonus", 1.0, soma, "Fôlego do Afogado", "fica mais tempo debaixo d'água");
        t("TORRE", "do Arquimago", "§d", 220, 120, 255, Particle.ENCHANT, Golpe.ARCANO, "Raio Arcano",
                "descarga mágica com dano extra", "entity_interaction_range", 0.25, soma, "Alcance Arcano", "acerta um pouco mais longe");
        t("QUARTEL", "do Devastador", "§7", 170, 170, 170, Particle.CRIT, Golpe.INVESTIDA, "Investida",
                "joga o alvo longe com dano extra", "armor_toughness", 0.5, soma, "Couraça", "+0,5 de resistência da armadura");
        t("SELVA", "do Xamã da Selva", "§a", 100, 220, 80, Particle.COMPOSTER, Golpe.ILUSAO, "Ilusão",
                "cega o alvo e deixa ele brilhando", "movement_speed", 0.025, pct, "Agilidade da Selva", "anda 2,5% mais rápido");
        t("VENTOS", "do Senhor dos Ventos", "§f", 230, 240, 255, Particle.SMALL_GUST, Golpe.VENTANIA, "Ventania",
                "joga o alvo para o alto", "safe_fall_distance", 1.0, soma, "Pés de Vento", "cai 1 bloco a mais sem dano");
        t("CASTELO", "do Carrasco", "§4", 190, 25, 25, Particle.DUST, Golpe.SANGRAR, "Sangramento",
                "o alvo sangra por 3 segundos", "attack_damage", 0.5, soma, "Fúria do Carrasco", "+0,5 de dano");
        t("FORJA", "do Senhor das Chamas", "§c", 255, 110, 30, Particle.FLAME, Golpe.CHAMAS, "Chamas Infernais",
                "põe fogo no alvo com dano extra", "burning_time", -0.15, soma, "Pele de Brasa", "queima por menos tempo");
        t("BASTIAO", "do Chefe Piglin", "§6", 255, 185, 20, Particle.DUST, Golpe.GANANCIA, "Fúria Piglin",
                "você ganha Força (4s) e dano extra", "knockback_resistance", 0.1, soma, "Peso do Ouro", "é menos empurrado");
        t("WITHER", "do General Wither", "§8", 70, 70, 70, Particle.LARGE_SMOKE, Golpe.MURCHAR, "Toque Wither",
                "aplica Wither no alvo (4s)", "explosion_knockback_resistance", 0.25, soma, "Couraça Negra", "explosões te empurram menos");
        t("COVIL", "da Besta Zoglin", "§c", 220, 90, 90, Particle.ANGRY_VILLAGER, Golpe.ESTOURO, "Estouro da Besta",
                "acerta os monstros em volta e empurra", "jump_strength", 0.03, soma, "Salto da Besta", "pula mais alto");
        t("CIDADELA", "do Senhor do Vazio", "§5", 150, 60, 220, Particle.REVERSE_PORTAL, Golpe.VAZIO, "Toque do Vazio",
                "o alvo flutua e leva dano extra", "gravity", -0.004, soma, "Leveza do Vazio", "cai mais devagar");
        t("PRISAO", "do Carcereiro", "§3", 30, 110, 120, Particle.SCULK_SOUL, Golpe.SONICO, "Grito Sônico",
                "dano alto e escuridão no alvo", "max_health", 2.0, soma, "Corpo do Carcereiro", "+1 coração de vida");
        t("SECRETA", "de Herobrine", "§4", 255, 255, 255, Particle.END_ROD, Golpe.HEROBRINE, "Fúria de Herobrine",
                "raio, cegueira e dano alto", "attack_damage", 1.0, soma, "Presença de Herobrine", "+1 de dano");
        // masmorras novas (versao 3): o tema vem do proprio tipo
        for (Tipo x : Tipo.TODOS.values()) {
            if (TEMAS.containsKey(x.id) || x.rel == null) continue;
            Tipo.Rel r = x.rel;
            Particle p;
            try {
                p = Particle.valueOf(r.particula());
                if (p.getDataType() != Void.class) p = Particle.ENCHANT;
            } catch (Throwable ex) {
                p = Particle.ENCHANT;
            }
            Golpe g;
            try {
                g = Golpe.valueOf(r.golpe());
            } catch (Throwable ex) {
                g = Golpe.ARCANO;
            }
            t(x.id, r.de(), r.cor(), (r.rgb() >> 16) & 255, (r.rgb() >> 8) & 255, r.rgb() & 255, p, g, r.golpeNome(), r.golpeDesc(),
                    r.attr(), r.valor(), r.pct() ? pct : soma, r.passivoNome(), r.passivoDesc());
        }
    }

    /** Mundos de minigame/lobby: sem poder de reliquia (so o visual). */
    private static final Set<String> SEM_PODER = Set.of("defesa", "slender", "vovo", "backrooms", "hotel", "login", "minigames");

    private final Masmorras pl;
    final NamespacedKey kRel;
    private final NamespacedKey kProj, kBoneco;
    private final Map<UUID, Long> recarga = new HashMap<>();
    private boolean emGolpe;
    private int voltas;

    Reliquias(Masmorras pl) {
        this.pl = pl;
        this.kRel = new NamespacedKey(pl, "reliquia");
        this.kProj = new NamespacedKey(pl, "reliquia_tiro");
        this.kBoneco = new NamespacedKey(pl, "boneco_treino");
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::auras, 40L, 10L);
        for (Map.Entry<String, Tema> en : TEMAS.entrySet())
            if (atributo(en.getValue().attr()) == null) pl.getLogger().warning("Reliquia " + en.getKey() + ": atributo desconhecido " + en.getValue().attr());
    }

    static Attribute atributo(String chave) {
        return org.bukkit.Registry.ATTRIBUTE.get(NamespacedKey.minecraft(chave));
    }

    private static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }

    // ------------------------------------------------------------------ o item

    enum Uso { ARMA, ARCO, ARMADURA }

    /** Nome da peca em portugues ou null se nao e equipamento. */
    static String peca(Material m) {
        String n = m.name();
        if (n.endsWith("_SWORD")) return "Espada";
        if (n.endsWith("_PICKAXE")) return "Picareta";
        if (n.endsWith("_AXE")) return "Machado";
        if (n.endsWith("_SHOVEL")) return "Pá";
        if (n.endsWith("_HOE")) return "Enxada";
        if (n.endsWith("_HELMET")) return "Elmo";
        if (n.endsWith("_CHESTPLATE")) return "Peitoral";
        if (n.endsWith("_LEGGINGS")) return "Calças";
        if (n.endsWith("_BOOTS")) return "Botas";
        return switch (n) {
            case "BOW" -> "Arco";
            case "CROSSBOW" -> "Besta";
            case "TRIDENT" -> "Tridente";
            case "MACE" -> "Maça";
            case "SHIELD" -> "Escudo";
            case "ELYTRA" -> "Asas";
            default -> null;
        };
    }

    static Uso uso(Material m) {
        String n = m.name();
        if (n.equals("BOW") || n.equals("CROSSBOW")) return Uso.ARCO;
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.equals("SHIELD") || n.equals("ELYTRA")) return Uso.ARMADURA;
        return Uso.ARMA;
    }

    private static EquipmentSlotGroup grupo(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> EquipmentSlotGroup.HEAD;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case FEET -> EquipmentSlotGroup.FEET;
            case OFF_HAND -> EquipmentSlotGroup.OFFHAND;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }

    String de(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(kRel, PersistentDataType.STRING);
    }

    /** Transforma o equipamento numa reliquia da masmorra 't'. Itens que nao sao equipamento ficam como estao. */
    ItemStack aplicar(ItemStack it, Tipo t) {
        if (it == null || t == null) return it;
        String peca = peca(it.getType());
        Tema tm = TEMAS.get(t.id);
        if (peca == null || tm == null || de(it) != null) return it;
        try {
            ItemMeta m = it.getItemMeta();
            if (m == null) return it;
            Uso tipo = uso(it.getType());
            m.customName(Masmorras.txt(tm.cor() + "§l" + peca + " " + tm.de()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Masmorras.txt("§8Relíquia: " + (t.secreta ? "???" : t.nome) + " §8• Nível " + (t.secreta ? "?" : String.valueOf(t.nivel))));
            lore.add(Masmorras.txt(""));
            if (tipo == Uso.ARMADURA) {
                lore.add(Masmorras.txt(tm.cor() + "» " + tm.passivoNome() + ": §7" + tm.passivoDesc()));
                EquipmentSlot slot = it.getType().getEquipmentSlot();
                if (!m.hasAttributeModifiers()) {
                    Multimap<Attribute, AttributeModifier> padrao = LinkedHashMultimap.create(it.getType().getDefaultAttributeModifiers(slot));
                    m.setAttributeModifiers(padrao);
                }
                Attribute at = atributo(tm.attr());
                if (at != null) m.addAttributeModifier(at, new AttributeModifier(new NamespacedKey(pl, "reliquia_" + slot.name().toLowerCase()),
                        tm.valor(), tm.op(), grupo(slot)));
            } else {
                lore.add(Masmorras.txt(tm.cor() + "» " + tm.golpeNome() + ": §7" + tm.golpeDesc()));
                lore.add(Masmorras.txt("§8(chance a cada " + (tipo == Uso.ARCO ? "flecha que acerta" : "golpe") + ")"));
            }
            m.lore(lore);
            m.setEnchantmentGlintOverride(true);
            m.getPersistentDataContainer().set(kRel, PersistentDataType.STRING, t.id);
            it.setItemMeta(m);
            visual(it, t.id);
        } catch (Throwable ex) {
            pl.getLogger().warning("Reliquia nao aplicada em " + it.getType() + ": " + ex);
        }
        return it;
    }

    /**
     * Visual proprio do pacote de recursos (quem nao tem o pacote ve o item normal): espadas com modelo 3D por tema
     * (corona:item/reliquias/espada_<tema>) e armaduras com o equipment asset corona:<tema> (textura por tema).
     */
    static final boolean SO_ESPADA = true;

    /** Nome do modelo da arma no pacote (corona:item/reliquias/<arma>_<tema>) ou null se nao tem modelo proprio. */
    static String modelo(String material) {
        if (material.endsWith("_SWORD")) return "espada";
        if (SO_ESPADA) return null; // as outras armas sairam do pacote por enquanto (pacote pesado travava o cliente)
        if (material.endsWith("_PICKAXE")) return "picareta";
        if (material.endsWith("_AXE")) return "machado";
        return switch (material) {
            case "BOW" -> "arco";
            case "CROSSBOW" -> "besta";
            case "TRIDENT" -> "tridente";
            case "MACE" -> "maca";
            default -> null;
        };
    }

    private void visual(ItemStack it, String tema) {
        if (!pl.getConfig().getBoolean("reliquias.visual-3d", true)) return;
        visualDoPacote(it, tema, pl.getLogger());
    }

    /** Aplica o visual do pacote (espada 3D / armadura com textura do tema). Usado tambem nos cosmeticos do Mercador Lendario. */
    @SuppressWarnings("UnstableApiUsage")
    /** Peca do conjunto Herobrine pelo material (para o icone proprio no inventario, Java e Bedrock). */
    static String pecaHerobrine(String n) {
        if (n.endsWith("_HELMET")) return "elmo";
        if (n.endsWith("_CHESTPLATE")) return "peitoral";
        if (n.endsWith("_LEGGINGS")) return "calca";
        if (n.endsWith("_BOOTS")) return "botas";
        if (n.endsWith("_SWORD")) return "espada";
        return null;
    }

    static void visualDoPacote(ItemStack it, String tema, java.util.logging.Logger log) {
        String id = tema.toLowerCase(java.util.Locale.ROOT);
        String n = it.getType().name();
        if (id.equals("herobrine")) {
            // conjunto cosmetico proprio: icone (item_model, que o Geyser tambem usa no Bedrock) e armadura no corpo
            try {
                String peca = pecaHerobrine(n);
                if (peca != null) it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "item/herobrine/" + peca));
                if (peca != null && !peca.equals("espada")) {
                    io.papermc.paper.datacomponent.item.Equippable atual = it.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);
                    io.papermc.paper.datacomponent.item.Equippable.Builder b = io.papermc.paper.datacomponent.item.Equippable.equippable(it.getType().getEquipmentSlot())
                            .assetId(net.kyori.adventure.key.Key.key("corona", "herobrine"));
                    if (atual != null) b.equipSound(atual.equipSound()).dispensable(atual.dispensable()).swappable(atual.swappable()).damageOnHurt(atual.damageOnHurt());
                    it.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE, b.build());
                }
            } catch (Throwable ex) {
                log.warning("Visual Herobrine em " + n + ": " + ex);
            }
            return;
        }
        try {
            String arma = modelo(n);
            if (arma != null) {
                it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "item/reliquias/" + arma + "_" + id));
            } else if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) {
                io.papermc.paper.datacomponent.item.Equippable atual = it.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);
                EquipmentSlot slot = it.getType().getEquipmentSlot();
                io.papermc.paper.datacomponent.item.Equippable.Builder b = io.papermc.paper.datacomponent.item.Equippable.equippable(slot)
                        .assetId(net.kyori.adventure.key.Key.key("corona", id));
                if (atual != null) {
                    b.equipSound(atual.equipSound()).dispensable(atual.dispensable()).swappable(atual.swappable()).damageOnHurt(atual.damageOnHurt())
                            .equipOnInteract(atual.equipOnInteract());
                    if (atual.allowedEntities() != null) b.allowedEntities(atual.allowedEntities());
                    if (atual.cameraOverlay() != null) b.cameraOverlay(atual.cameraOverlay());
                }
                it.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE, b.build());
            }
        } catch (Throwable ex) {
            log.warning("Visual da reliquia " + tema + " em " + n + ": " + ex);
        }
    }

    // ------------------------------------------------------------------ de onde vem

    /** Baus de dentro das masmorras: o equipamento vira reliquia daquela masmorra. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bau(LootGenerateEvent e) {
        if (!(e.getInventoryHolder() instanceof Container c)) return;
        Block b = c.getBlock();
        Masmorra m = pl.masmorraEm(b.getWorld(), b.getX(), b.getY(), b.getZ());
        if (m == null) return;
        List<ItemStack> novo = new ArrayList<>();
        for (ItemStack it : e.getLoot()) novo.add(it == null ? null : aplicar(it.clone(), m.tipo));
        novo.removeIf(x -> x == null);
        e.setLoot(novo);
    }

    /** Reliquia no chao: brilha e mostra o nome. */
    @EventHandler(ignoreCancelled = true)
    public void noChao(ItemSpawnEvent e) {
        Item item = e.getEntity();
        ItemStack it = item.getItemStack();
        if (de(it) == null) return;
        item.setGlowing(true);
        if (it.getItemMeta().hasCustomName()) {
            item.customName(it.getItemMeta().customName());
            item.setCustomNameVisible(true);
        }
    }

    // ------------------------------------------------------------------ poderes

    private boolean semPoder(World w) {
        return w == null || SEM_PODER.contains(w.getName());
    }

    /** Flecha/besta/tridente de reliquia: marca o projetil e deixa um rastro. */
    @EventHandler(ignoreCancelled = true)
    public void atirou(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || semPoder(p.getWorld())) return;
        String id = de(e.getBow());
        if (id != null && e.getProjectile() instanceof Projectile pr) marcarTiro(pr, id);
    }

    @EventHandler(ignoreCancelled = true)
    public void lancou(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Trident tr) || !(tr.getShooter() instanceof Player p) || semPoder(p.getWorld())) return;
        String id = de(tr.getItemStack());
        if (id != null) marcarTiro(tr, id);
    }

    private void marcarTiro(Projectile pr, String id) {
        pr.getPersistentDataContainer().set(kProj, PersistentDataType.STRING, id);
        Tema tm = TEMAS.get(id);
        if (tm == null) return;
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            if (!pr.isValid() || pr.isOnGround() || t[0]++ > 60) {
                tarefa.cancel();
                return;
            }
            particula(pr.getLocation(), tm, 2, 0.05);
        }, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void acertou(EntityDamageByEntityEvent e) {
        if (emGolpe || !(e.getEntity() instanceof LivingEntity alvo)) return;
        Player p;
        String id;
        double chance;
        if (e.getDamager() instanceof Player x) {
            p = x;
            id = de(x.getInventory().getItemInMainHand());
            if (id != null && uso(x.getInventory().getItemInMainHand().getType()) != Uso.ARMA) id = null;
            chance = 0.25;
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player x) {
            p = x;
            id = pr.getPersistentDataContainer().get(kProj, PersistentDataType.STRING);
            chance = 0.35;
        } else return;
        if (id == null || semPoder(p.getWorld())) return;
        Tema tm = TEMAS.get(id);
        if (tm == null) return;
        boolean boneco = alvo.getPersistentDataContainer().has(kBoneco);
        if (e.isCancelled() && !boneco) return;
        long agora = System.currentTimeMillis();
        Long ate = recarga.get(p.getUniqueId());
        if (ate != null && ate > agora) return;
        if (rnd().nextDouble() >= chance) return;
        recarga.put(p.getUniqueId(), agora + 1200L);
        int n = Math.max(1, nivelDoTema(id));
        p.sendActionBar(Masmorras.txt(tm.cor() + "§l✦ " + tm.golpeNome() + "!"));
        golpe(tm, p, alvo, n, boneco);
    }

    private static int nivelDoTema(String id) {
        Tipo t = Tipo.pegar(id);
        return t != null ? t.nivel : 1;
    }

    private void dano(LivingEntity alvo, double d, Player p, boolean boneco) {
        if (boneco || alvo.isDead()) return;
        emGolpe = true;
        try {
            alvo.damage(d, p);
        } finally {
            emGolpe = false;
        }
    }

    /** Pode empurrar/levantar: nem boneco, nem chefe de masmorra. */
    private boolean podeMexer(LivingEntity alvo, boolean boneco) {
        return !boneco && !alvo.getPersistentDataContainer().has(pl.kChefe, PersistentDataType.INTEGER);
    }

    private void efeito(LivingEntity alvo, PotionEffectType tipo, int ticks, int forca, boolean boneco) {
        if (!boneco) alvo.addPotionEffect(new PotionEffect(tipo, ticks, forca));
    }

    private void golpe(Tema tm, Player p, LivingEntity alvo, int n, boolean boneco) {
        World w = alvo.getWorld();
        Location c = alvo.getLocation().add(0, alvo.getHeight() * 0.55, 0);
        particula(c, tm, 18, 0.35);
        switch (tm.golpe()) {
            case OSSOS -> {
                efeito(alvo, PotionEffectType.SLOWNESS, 60, 1, boneco);
                efeito(alvo, PotionEffectType.WEAKNESS, 60, 0, boneco);
                w.playSound(c, "minecraft:entity.skeleton.hurt", 1f, 0.6f);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, c, 10, 0.3, 0.4, 0.3, 0.02);
            }
            case VENENO -> {
                efeito(alvo, PotionEffectType.POISON, 80, 1, boneco);
                w.playSound(c, "minecraft:entity.spider.hurt", 1f, 0.7f);
            }
            case ESMAGAR -> {
                dano(alvo, 3 + n * 0.5, p, boneco);
                if (podeMexer(alvo, boneco)) empurrar(alvo, p, 0.8, 0.3);
                w.playSound(c, "minecraft:block.anvil.land", 0.6f, 1.4f);
                w.spawnParticle(Particle.CRIT, c, 16, 0.3, 0.3, 0.3, 0.3);
            }
            case MALDICAO -> {
                PotionEffectType[] ruins = {PotionEffectType.POISON, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.NAUSEA, PotionEffectType.MINING_FATIGUE};
                efeito(alvo, ruins[rnd().nextInt(ruins.length)], 80, 0, boneco);
                w.playSound(c, "minecraft:entity.witch.celebrate", 1f, 1.2f);
            }
            case CONGELAR -> {
                if (!boneco) alvo.setFreezeTicks(Math.max(alvo.getFreezeTicks(), 160));
                efeito(alvo, PotionEffectType.SLOWNESS, 40, 2, boneco);
                w.playSound(c, "minecraft:block.glass.break", 0.8f, 1.6f);
                w.spawnParticle(Particle.ITEM_SNOWBALL, c, 12, 0.3, 0.4, 0.3, 0.05);
            }
            case AREIA -> {
                efeito(alvo, PotionEffectType.BLINDNESS, 50, 0, boneco);
                efeito(alvo, PotionEffectType.HUNGER, 80, 1, boneco);
                w.playSound(c, "minecraft:block.sand.break", 1f, 0.7f);
            }
            case CORRENTEZA -> {
                if (podeMexer(alvo, boneco)) {
                    org.bukkit.util.Vector v = p.getLocation().toVector().subtract(alvo.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() > 0.01) alvo.setVelocity(v.normalize().multiply(0.7).setY(0.25));
                }
                efeito(alvo, PotionEffectType.SLOWNESS, 40, 1, boneco);
                w.playSound(c, "minecraft:entity.generic.splash", 1f, 1.0f);
                w.spawnParticle(Particle.SPLASH, c, 30, 0.4, 0.4, 0.4, 0.1);
            }
            case ARCANO -> {
                dano(alvo, 3 + n, p, boneco);
                w.playSound(c, "minecraft:block.amethyst_block.resonate", 1f, 1.4f);
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, 20, 0.3, 0.5, 0.3, 0.2);
                linha(p.getEyeLocation().subtract(0, 0.3, 0), c, Particle.END_ROD);
            }
            case INVESTIDA -> {
                dano(alvo, 2 + n * 0.5, p, boneco);
                if (podeMexer(alvo, boneco)) empurrar(alvo, p, 1.3, 0.45);
                w.playSound(c, "minecraft:entity.ravager.attack", 0.8f, 1.1f);
                w.spawnParticle(Particle.EXPLOSION, c, 1, 0, 0, 0, 0);
            }
            case ILUSAO -> {
                efeito(alvo, PotionEffectType.BLINDNESS, 40, 0, boneco);
                efeito(alvo, PotionEffectType.GLOWING, 100, 0, boneco);
                w.playSound(c, "minecraft:entity.illusioner.mirror_move", 1f, 1.2f);
                w.spawnParticle(Particle.HAPPY_VILLAGER, c, 12, 0.4, 0.5, 0.4, 0);
            }
            case VENTANIA -> {
                if (podeMexer(alvo, boneco)) alvo.setVelocity(alvo.getVelocity().setY(0.95));
                w.playSound(c, "minecraft:entity.breeze.wind_burst", 1f, 1.0f);
                w.spawnParticle(Particle.GUST, alvo.getLocation(), 1, 0, 0, 0, 0);
            }
            case SANGRAR -> {
                for (int i = 1; i <= 3; i++) {
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        if (!alvo.isValid() || alvo.isDead()) return;
                        Location l = alvo.getLocation().add(0, alvo.getHeight() * 0.6, 0);
                        particula(l, tm, 10, 0.25);
                        dano(alvo, 1.0 + n * 0.2, p, boneco);
                    }, i * 20L);
                }
                w.playSound(c, "minecraft:entity.player.attack.crit", 1f, 0.7f);
            }
            case CHAMAS -> {
                if (!boneco) alvo.setFireTicks(Math.max(alvo.getFireTicks(), 80));
                dano(alvo, 2, p, boneco);
                w.playSound(c, "minecraft:item.firecharge.use", 0.8f, 1.0f);
                w.spawnParticle(Particle.LAVA, c, 5, 0.3, 0.3, 0.3, 0);
            }
            case GANANCIA -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 0));
                dano(alvo, 2, p, boneco);
                w.playSound(c, "minecraft:entity.piglin_brute.angry", 0.8f, 1.2f);
                w.spawnParticle(Particle.DUST, c, 14, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(255, 210, 40), 1.4f));
            }
            case MURCHAR -> {
                efeito(alvo, PotionEffectType.WITHER, 80, 1, boneco);
                w.playSound(c, "minecraft:entity.wither.shoot", 0.6f, 1.2f);
            }
            case ESTOURO -> {
                for (Entity x : w.getNearbyEntities(alvo.getLocation(), 3, 2, 3)) {
                    if (!(x instanceof LivingEntity le) || !(x instanceof Enemy) || x.equals(p)) continue;
                    dano(le, 3, p, false);
                    if (podeMexer(le, false)) empurrar(le, p, 0.9, 0.35);
                }
                w.playSound(c, "minecraft:entity.zoglin.attack", 1f, 0.8f);
                w.spawnParticle(Particle.EXPLOSION, alvo.getLocation(), 2, 0.5, 0.2, 0.5, 0);
            }
            case VAZIO -> {
                efeito(alvo, PotionEffectType.LEVITATION, 30, 1, boneco || !podeMexer(alvo, boneco));
                dano(alvo, 3, p, boneco);
                w.playSound(c, "minecraft:entity.enderman.teleport", 1f, 0.8f);
                w.spawnParticle(Particle.PORTAL, c, 40, 0.4, 0.6, 0.4, 0.4);
            }
            case SONICO -> {
                dano(alvo, 4 + n, p, boneco);
                efeito(alvo, PotionEffectType.DARKNESS, 60, 0, boneco);
                w.playSound(c, "minecraft:entity.warden.sonic_boom", 0.5f, 1.4f);
                w.spawnParticle(Particle.SONIC_BOOM, c, 1, 0, 0, 0, 0);
            }
            case HEROBRINE -> {
                w.strikeLightningEffect(alvo.getLocation());
                dano(alvo, 5, p, boneco);
                efeito(alvo, PotionEffectType.BLINDNESS, 40, 0, boneco);
                w.playSound(c, "minecraft:ambient.cave", 1f, 0.6f);
            }
        }
    }

    private static void empurrar(LivingEntity alvo, Player p, double forca, double y) {
        org.bukkit.util.Vector v = alvo.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (v.lengthSquared() < 0.01) v = p.getLocation().getDirection().setY(0);
        if (v.lengthSquared() < 0.01) return;
        alvo.setVelocity(alvo.getVelocity().add(v.normalize().multiply(forca).setY(y)));
    }

    private static void linha(Location a, Location b, Particle part) {
        org.bukkit.util.Vector d = b.toVector().subtract(a.toVector());
        double dist = d.length();
        if (dist < 0.1 || dist > 30) return;
        d.normalize();
        for (double x = 0; x <= dist; x += 0.4) a.getWorld().spawnParticle(part, a.clone().add(d.clone().multiply(x)), 1, 0, 0, 0, 0);
    }

    private static void particula(Location l, Tema tm, int n, double espalha) {
        if (tm.aura() == Particle.DUST)
            l.getWorld().spawnParticle(Particle.DUST, l, n, espalha, espalha, espalha, 0, new Particle.DustOptions(tm.brilho(), 1.2f));
        else l.getWorld().spawnParticle(tm.aura(), l, n, espalha, espalha, espalha, 0.01);
    }

    // ------------------------------------------------------------------ brilho na mao

    /** A cada meio segundo: particulas da masmorra saindo da reliquia na mao; armadura completa da mesma masmorra = anel no chao. */
    private void auras() {
        voltas++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isInvisible() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            String mao = de(p.getInventory().getItemInMainHand());
            if (mao != null) {
                Tema tm = TEMAS.get(mao);
                if (tm != null) {
                    org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0);
                    if (dir.lengthSquared() < 0.01) dir = new org.bukkit.util.Vector(0, 0, 1);
                    org.bukkit.util.Vector lado = dir.normalize().clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).multiply(-0.4);
                    particula(p.getLocation().add(0, 0.95, 0).add(dir.multiply(0.3)).add(lado), tm, 2, 0.12);
                }
            }
            if (voltas % 4 != 0) continue;
            ItemStack[] arm = p.getInventory().getArmorContents();
            String igual = null;
            boolean completo = arm.length == 4;
            for (ItemStack a : arm) {
                String id = de(a);
                if (id == null || (igual != null && !igual.equals(id))) {
                    completo = false;
                    break;
                }
                igual = id;
            }
            if (!completo || igual == null) continue;
            Tema tm = TEMAS.get(igual);
            if (tm == null) continue;
            Location pe = p.getLocation().add(0, 0.1, 0);
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10 + voltas * 0.2;
                particula(pe.clone().add(Math.cos(a) * 0.8, 0, Math.sin(a) * 0.8), tm, 1, 0.02);
            }
        }
    }
}
