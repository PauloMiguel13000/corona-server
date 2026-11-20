package br.servidor.poderes;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * RUNAS DA CLASSE (20/11, pedido do Paulo): cada classe tem o seu proprio nivel, de 1 a 60, comprado com o XP do jogador
 * numa tela parecida com o Banco de XP, em abas de 10 em 10. Trocar de classe nao zera nada: a outra classe comeca no
 * nivel 1 e, voltando, o nivel antigo continua la.
 * <ul>
 * <li>Cada nivel: +0,1% de ataque e +0,1% de defesa, e +0,1% na especialidade da classe (dano das habilidades; no
 * Engenheiro, o dano das torres).</li>
 * <li>A cada 10 niveis (10, 20 ... 60): uma passiva da classe (ver {@link #MARCOS}).</li>
 * <li>O nivel de poder do jogador libera as runas: so compra a runa N quem tem nivel de poder N ou mais.</li>
 * <li>REBIRTH (Masmorras, /rebirth): cada renascimento da um slot de runa extra (+0,3% de dano e +0,3% de defesa).</li>
 * </ul>
 * Abre com /runa, pelo item do Kit Geral (/kits) e pelo NPC do lobby das masmorras. Tudo em menu de bau (Java e Bedrock).
 */
final class Runas implements Listener {

    static final int MAX = 60;
    static final NamespacedKey K_REBIRTH = new NamespacedKey("masmorras", "rebirth");

    enum Ef { DANO, DEF, RECARGA, VIDA, VELOC, ROUBO, VENENO, LENTO, FOGO, MURCHAR, RAIO, T_VENENO, T_RAPIDA, T_ARQUEIRA, T_DURACAO, T_RAIO, T_FOGO }

    record Marco(String nome, Ef ef, double valor, String desc) {}

    /** As 6 passivas de cada classe (nos niveis 10, 20, 30, 40, 50 e 60). */
    static final Map<Classe, Marco[]> MARCOS = new EnumMap<>(Classe.class);

    private static Marco mk(String n, Ef e, double v, String d) {
        return new Marco(n, e, v, d);
    }

    static {
        MARCOS.put(Classe.ENGENHEIRO, new Marco[] {
                mk("Torres Venenosas", Ef.T_VENENO, 0.005, "tiros das torres envenenam e +0,5% de dano das torres"),
                mk("Torre-Bomba", Ef.T_RAPIDA, 0.15, "as torres atiram 15% mais rápido"),
                mk("Mira de Arqueira", Ef.T_ARQUEIRA, 0.005, "+0,5% de dano das torres arqueiras"),
                mk("Engrenagens Longas", Ef.T_DURACAO, 0.02, "+2% de duração das torres"),
                mk("Torre Relâmpago", Ef.T_RAIO, 0.4, "a cada 4 tiros, um raio estoura em área"),
                mk("Chamas da Forja", Ef.T_FOGO, 3, "os tiros das torres põem fogo nos monstros")});
        MARCOS.put(Classe.MAGO, new Marco[] {
                mk("Foco Arcano", Ef.DANO, 0.03, "+3% de dano das magias"),
                mk("Mente Rápida", Ef.RECARGA, 0.05, "-5% de recarga"),
                mk("Gelo Arcano", Ef.LENTO, 0.20, "20% de chance de deixar o alvo lento"),
                mk("Escudo de Mana", Ef.VIDA, 2, "+1 coração"),
                mk("Sobrecarga", Ef.RAIO, 0.10, "10% de chance de raio no alvo"),
                mk("Arquimago", Ef.DANO, 0.06, "+6% de dano das magias")});
        MARCOS.put(Classe.SAIYAJIN, new Marco[] {
                mk("Ki Treinado", Ef.DANO, 0.04, "+4% de dano"),
                mk("Passo Rápido", Ef.VELOC, 0.05, "+5% de velocidade"),
                mk("Corpo Saiyajin", Ef.DEF, 0.03, "+3% de defesa"),
                mk("Instinto de Luta", Ef.ROUBO, 0.03, "rouba 3% do dano como vida"),
                mk("Ki Infinito", Ef.RECARGA, 0.08, "-8% de recarga"),
                mk("Ultra Instinto", Ef.DANO, 0.08, "+8% de dano")});
        MARCOS.put(Classe.GUERREIRO, new Marco[] {
                mk("Pele Dura", Ef.DEF, 0.03, "+3% de defesa"),
                mk("Vigor", Ef.VIDA, 2, "+1 coração"),
                mk("Golpe Pesado", Ef.DANO, 0.03, "+3% de dano"),
                mk("Sede de Batalha", Ef.ROUBO, 0.04, "rouba 4% do dano como vida"),
                mk("Muralha", Ef.DEF, 0.05, "+5% de defesa"),
                mk("Imortal", Ef.VIDA, 4, "+2 corações")});
        MARCOS.put(Classe.NINJA, new Marco[] {
                mk("Pés Leves", Ef.VELOC, 0.05, "+5% de velocidade"),
                mk("Lâmina Envenenada", Ef.VENENO, 0.25, "25% de chance de envenenar"),
                mk("Golpe Certeiro", Ef.DANO, 0.04, "+4% de dano"),
                mk("Sombra Rápida", Ef.RECARGA, 0.06, "-6% de recarga"),
                mk("Lâmina Sombria", Ef.MURCHAR, 0.20, "20% de chance de murchar o alvo"),
                mk("Mestre das Sombras", Ef.DANO, 0.08, "+8% de dano")});
        MARCOS.put(Classe.DRUIDA, new Marco[] {
                mk("Casca de Árvore", Ef.VIDA, 2, "+1 coração"),
                mk("Espinho Venenoso", Ef.VENENO, 0.25, "25% de chance de envenenar"),
                mk("Ciclo da Vida", Ef.RECARGA, 0.05, "-5% de recarga"),
                mk("Raízes Fortes", Ef.DEF, 0.03, "+3% de defesa"),
                mk("Seiva", Ef.ROUBO, 0.04, "rouba 4% do dano como vida"),
                mk("Fúria da Natureza", Ef.DANO, 0.06, "+6% de dano")});
        MARCOS.put(Classe.TRIDENTE, new Marco[] {
                mk("Correnteza", Ef.DANO, 0.03, "+3% de dano"),
                mk("Maré Fria", Ef.LENTO, 0.20, "20% de chance de deixar o alvo lento"),
                mk("Ira dos Mares", Ef.RAIO, 0.08, "8% de chance de raio no alvo"),
                mk("Escamas", Ef.VIDA, 2, "+1 coração"),
                mk("Maré Alta", Ef.RECARGA, 0.06, "-6% de recarga"),
                mk("Senhor dos Mares", Ef.DANO, 0.07, "+7% de dano")});
        MARCOS.put(Classe.ARQUEIRO, new Marco[] {
                mk("Mira Firme", Ef.DANO, 0.03, "+3% de dano"),
                mk("Passo do Caçador", Ef.VELOC, 0.04, "+4% de velocidade"),
                mk("Flecha Fria", Ef.LENTO, 0.20, "20% de chance de deixar o alvo lento"),
                mk("Aljava Rápida", Ef.RECARGA, 0.06, "-6% de recarga"),
                mk("Flecha Ardente", Ef.FOGO, 3, "põe fogo no alvo"),
                mk("Olho do Falcão", Ef.DANO, 0.06, "+6% de dano")});
        MARCOS.put(Classe.LUTADOR, new Marco[] {
                mk("Punho de Ferro", Ef.DANO, 0.04, "+4% de dano"),
                mk("Sangue Quente", Ef.ROUBO, 0.03, "rouba 3% do dano como vida"),
                mk("Guarda Alta", Ef.DEF, 0.03, "+3% de defesa"),
                mk("Gingado", Ef.VELOC, 0.04, "+4% de velocidade"),
                mk("Punhos em Chamas", Ef.FOGO, 3, "põe fogo no alvo"),
                mk("Campeão", Ef.DANO, 0.08, "+8% de dano")});
        MARCOS.put(Classe.CIPO, new Marco[] {
                mk("Seiva Ácida", Ef.VENENO, 0.25, "25% de chance de envenenar"),
                mk("Chicote Forte", Ef.DANO, 0.03, "+3% de dano"),
                mk("Enrosco", Ef.LENTO, 0.20, "20% de chance de deixar o alvo lento"),
                mk("Raiz Profunda", Ef.VIDA, 2, "+1 coração"),
                mk("Crescimento", Ef.RECARGA, 0.06, "-6% de recarga"),
                mk("Selva Viva", Ef.DANO, 0.07, "+7% de dano")});
    }

    private final Poderes pl;
    private final File arquivo;
    private final YamlConfiguration dados;
    private final NamespacedKey kVida, kVeloc;
    private final Map<UUID, Integer> tiroTorre = new HashMap<>();
    private boolean sujo;

    Runas(Poderes pl) {
        this.pl = pl;
        this.arquivo = new File(pl.getDataFolder(), "runas.yml");
        this.dados = YamlConfiguration.loadConfiguration(arquivo);
        this.kVida = new NamespacedKey(pl, "runa_vida");
        this.kVeloc = new NamespacedKey(pl, "runa_veloc");
    }

    void iniciar() {
        custoBase = pl.getConfig().getInt("runas.custo-base", 250);
        custoPorNivel = pl.getConfig().getInt("runas.custo-por-nivel", 90);
        Bukkit.getScheduler().runTaskTimer(pl, this::aCadaDoisSegundos, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(pl, this::salvar, 1200L, 1200L);
    }

    void salvar() {
        if (!sujo) return;
        sujo = false;
        try {
            dados.save(arquivo);
        } catch (Exception ex) {
            pl.getLogger().warning("Nao consegui salvar runas.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ dados

    int nivel(Player p, Classe c) {
        if (c == null) return 0;
        return Math.max(1, Math.min(MAX, dados.getInt(p.getUniqueId() + "." + c.id, 1)));
    }

    int nivel(Player p) {
        return nivel(p, pl.classe(p));
    }

    static int rebirths(Player p) {
        Integer n = p.getPersistentDataContainer().get(K_REBIRTH, PersistentDataType.INTEGER);
        return n == null ? 0 : Math.max(0, n);
    }

    static String tituloRebirth(Player p) {
        int n = rebirths(p);
        if (n <= 0) return "";
        String[] r = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
        return "§5§l✦§d§lREBIRTH" + (n < r.length ? r[n] : " " + n) + "§5§l✦ ";
    }

    /** Passivas (marcos) ja liberadas pela classe atual. */
    List<Marco> marcos(Player p) {
        Classe c = pl.classe(p);
        List<Marco> l = new ArrayList<>();
        Marco[] m = c == null ? null : MARCOS.get(c);
        if (m == null) return l;
        int n = nivel(p, c);
        for (int i = 0; i < m.length; i++) if (n >= (i + 1) * 10) l.add(m[i]);
        return l;
    }

    private double soma(Player p, Ef ef) {
        double s = 0;
        for (Marco m : marcos(p)) if (m.ef() == ef) s += m.valor();
        return s;
    }

    private boolean tem(Player p, Ef ef) {
        for (Marco m : marcos(p)) if (m.ef() == ef) return true;
        return false;
    }

    /** +0,1% por nivel e +0,3% por REBIRTH (ataque). */
    double bonusAtaque(Player p) {
        return nivel(p) * 0.001 + rebirths(p) * 0.003;
    }

    double bonusDefesa(Player p) {
        return Math.min(0.5, nivel(p) * 0.001 + rebirths(p) * 0.003 + soma(p, Ef.DEF));
    }

    /** Multiplicador do dano das habilidades (especialidade da classe). */
    double fatorHab(Player p) {
        Classe c = pl.classe(p);
        if (c == null || c == Classe.ENGENHEIRO) return 1.0;   // no Engenheiro a especialidade vai nas torres (fatorTorre)
        return 1.0 + nivel(p) * 0.001 + soma(p, Ef.DANO);
    }

    double fatorRecarga(Player p) {
        return Math.max(0.6, 1.0 - soma(p, Ef.RECARGA));
    }

    // ------------------------------------------------------------------ Engenheiro (torres)

    /** Dano das torres: +0,1% por nivel do Engenheiro e as passivas de torre (0,5% e 0,5% arqueira). */
    double fatorTorre(Player p, String tipo) {
        if (p == null || pl.classe(p) != Classe.ENGENHEIRO) return 1.0;
        double f = 1.0 + nivel(p) * 0.001 + soma(p, Ef.T_VENENO);
        if ("ARQUEIRA".equals(tipo) || "SUPREMA".equals(tipo)) f += soma(p, Ef.T_ARQUEIRA);
        return f;
    }

    /** Quanto mais rapido as torres carregam (0,15 = 15%). */
    double torreRapida(Player p) {
        return pl.classe(p) == Classe.ENGENHEIRO ? soma(p, Ef.T_RAPIDA) : 0;
    }

    double torreDuracao(Player p) {
        return pl.classe(p) == Classe.ENGENHEIRO ? 1.0 + soma(p, Ef.T_DURACAO) : 1.0;
    }

    /** Efeitos do tiro da torre no alvo (veneno, raio a cada 4 tiros, fogo). Chamado pelas Torretas depois do dano. */
    void tiroDaTorre(Player dono, LivingEntity alvo, double dano) {
        if (pl.classe(dono) != Classe.ENGENHEIRO || alvo instanceof Player) return;
        if (tem(dono, Ef.T_VENENO)) alvo.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true));
        if (tem(dono, Ef.T_FOGO)) alvo.setFireTicks(Math.max(alvo.getFireTicks(), 60));
        if (tem(dono, Ef.T_RAIO)) {
            int n = tiroTorre.merge(dono.getUniqueId(), 1, Integer::sum);
            if (n % 4 == 0) raio(dono, alvo, dano * 0.4, 3.0);
        }
    }

    private void raio(Player p, LivingEntity alvo, double dano, double raio) {
        alvo.getWorld().strikeLightningEffect(alvo.getLocation());
        for (Entity e : alvo.getNearbyEntities(raio, raio, raio)) {
            if (!(e instanceof LivingEntity le) || e instanceof Player || pl.ehAliado(e, p) || e instanceof ArmorStand) continue;
            if (!(e instanceof org.bukkit.entity.Enemy)) continue;
            pl.causarDano(le, Math.max(1, dano), p);
        }
        if (!alvo.isDead()) pl.causarDano(alvo, Math.max(1, dano), p);
    }

    // ------------------------------------------------------------------ efeitos no combate

    private static Player autor(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean emRaio = false;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void dano(EntityDamageByEntityEvent e) {
        if (emRaio) return;
        // defesa
        if (e.getEntity() instanceof Player v && pl.classe(v) != null && !pl.semPoderes(v.getWorld())) {
            double d = bonusDefesa(v);
            if (d > 0) e.setDamage(e.getDamage() * (1 - d));
        }
        // ataque
        Player p = autor(e.getDamager());
        if (p == null || !(e.getEntity() instanceof LivingEntity alvo) || alvo.equals(p) || pl.semPoderes(p.getWorld())) return;
        if (pl.classe(p) == null || alvo instanceof ArmorStand || pl.ehAliado(alvo, p)) return;
        double a = bonusAtaque(p);
        if (a > 0) e.setDamage(e.getDamage() * (1 + a));
        if (alvo instanceof Player) return;   // as passivas de efeito sao contra monstros
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Marco m : marcos(p)) {
            switch (m.ef()) {
                case VENENO -> { if (r.nextDouble() < m.valor()) alvo.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true)); }
                case LENTO -> { if (r.nextDouble() < m.valor()) alvo.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1, false, true)); }
                case MURCHAR -> { if (r.nextDouble() < m.valor()) alvo.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true)); }
                case FOGO -> alvo.setFireTicks(Math.max(alvo.getFireTicks(), (int) (m.valor() * 20)));
                case RAIO -> {
                    if (r.nextDouble() < m.valor()) {
                        emRaio = true;
                        try {
                            raio(p, alvo, Math.max(2, e.getDamage() * 0.35), 2.5);
                        } finally {
                            emRaio = false;
                        }
                    }
                }
                case ROUBO -> {
                    AttributeInstance mx = p.getAttribute(Attribute.MAX_HEALTH);
                    if (mx != null) p.setHealth(Math.min(mx.getValue(), p.getHealth() + e.getDamage() * m.valor()));
                }
                default -> { }
            }
        }
    }

    /** Defesa contra o resto (flecha de monstro sem autor, explosao...) — so o bonus da defesa. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void danoGeral(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (!(e.getEntity() instanceof Player v) || pl.classe(v) == null || pl.semPoderes(v.getWorld())) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || c == EntityDamageEvent.DamageCause.MAGIC || c == EntityDamageEvent.DamageCause.POISON || c == EntityDamageEvent.DamageCause.WITHER) {
            double d = bonusDefesa(v);
            if (d > 0) e.setDamage(e.getDamage() * (1 - d));
        }
    }

    // ------------------------------------------------------------------ passivas das classes (a cada 2 s)

    private void aCadaDoisSegundos() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Classe c = pl.classe(p);
            boolean ok = c != null && !pl.semPoderes(p.getWorld());
            atributo(p, Attribute.MAX_HEALTH, kVida, ok ? soma(p, Ef.VIDA) : 0, AttributeModifier.Operation.ADD_NUMBER);
            atributo(p, Attribute.MOVEMENT_SPEED, kVeloc, ok ? soma(p, Ef.VELOC) : 0, AttributeModifier.Operation.ADD_SCALAR);
            if (!ok) continue;
            // 20/11 (Sui): Guerreiro com Forca e Resistencia, Ninja com Velocidade II, Saiyajin com Forca e Pressa
            switch (c) {
                case GUERREIRO -> {
                    efeito(p, PotionEffectType.STRENGTH, 0);
                    efeito(p, PotionEffectType.RESISTANCE, 0);
                }
                case NINJA -> efeito(p, PotionEffectType.SPEED, 1);
                case SAIYAJIN -> {
                    efeito(p, PotionEffectType.STRENGTH, 0);
                    efeito(p, PotionEffectType.HASTE, 0);
                }
                default -> { }
            }
        }
    }

    private static void efeito(Player p, PotionEffectType t, int nivel) {
        PotionEffect atual = p.getPotionEffect(t);
        if (atual != null && (atual.getAmplifier() > nivel || atual.getDuration() > 100 || atual.isInfinite())) return;
        p.addPotionEffect(new PotionEffect(t, 70, nivel, true, false, true));
    }

    private static void atributo(Player p, Attribute at, NamespacedKey k, double valor, AttributeModifier.Operation op) {
        AttributeInstance a = p.getAttribute(at);
        if (a == null) return;
        AttributeModifier velho = null;
        for (AttributeModifier m : a.getModifiers()) if (k.equals(m.getKey())) velho = m;
        if (velho != null && Math.abs(velho.getAmount() - valor) < 1e-6) return;
        if (velho != null) a.removeModifier(velho);
        if (valor != 0) a.addModifier(new AttributeModifier(k, valor, op, EquipmentSlotGroup.ANY));
        if (at == Attribute.MAX_HEALTH && p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        tiroTorre.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ XP do jogador

    static int xpDoNivel(int nivel) {
        if (nivel <= 16) return nivel * nivel + 6 * nivel;
        if (nivel <= 31) return (int) (2.5 * nivel * nivel - 40.5 * nivel + 360);
        return (int) (4.5 * nivel * nivel - 162.5 * nivel + 2220);
    }

    static int paraProximo(int nivel) {
        if (nivel <= 15) return 2 * nivel + 7;
        if (nivel <= 30) return 5 * nivel - 38;
        return 9 * nivel - 158;
    }

    static int xpTotal(Player p) {
        return xpDoNivel(p.getLevel()) + Math.round(p.getExp() * paraProximo(p.getLevel()));
    }

    static void definirXp(Player p, int total) {
        total = Math.max(0, total);
        p.setExp(0);
        p.setLevel(0);
        p.setTotalExperience(0);
        int nivel = 0;
        while (xpDoNivel(nivel + 1) <= total) nivel++;
        p.setLevel(nivel);
        int resto = total - xpDoNivel(nivel);
        p.setExp(Math.min(0.9999f, resto / (float) paraProximo(nivel)));
        p.setTotalExperience(total);
    }

    /** Preco (pontos de XP) da runa do nivel n (2 a 60). */
    // 20/11 (Paulo: "aumente o custo de xp para upar a runa"): 3x mais caro (era 80 + 30 x nivel); config runas.custo-base/-por-nivel
    static int custoBase = 250, custoPorNivel = 90;

    static int custo(int n) {
        return custoBase + custoPorNivel * n;
    }

    // ------------------------------------------------------------------ menu

    static final class Menu implements InventoryHolder {
        Inventory inv;
        int pagina;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int[] SLOTS_RUNA = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    private static final int[] SLOTS_ABA = {2, 3, 4, 5, 6, 7};
    private static final int[] SLOTS_REBIRTH = {46, 47, 48, 49, 50, 51, 52};

    boolean comando(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("So no jogo.");
            return true;
        }
        if (pl.classe(p) == null) {
            p.sendMessage(Poderes.txt("§d§lRUNAS §8» §7Escolha uma classe primeiro (§f/classe§7): cada classe tem as suas runas."));
            return true;
        }
        abrir(p, (nivel(p) - 1) / 10);
        return true;
    }

    private static ItemStack item(Material m, String nome, List<String> lore, boolean brilho) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(Poderes.txt(nome));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Poderes.txt(s));
        im.lore(l);
        if (brilho) im.setEnchantmentGlintOverride(true);
        im.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(im);
        return it;
    }

    private static String pct(double v) {
        return Poderes.num(v * 100) + "%";
    }

    void abrir(Player p, int pagina) {
        Classe c = pl.classe(p);
        if (c == null) return;
        pagina = Math.max(0, Math.min(5, pagina));
        int n = nivel(p, c);
        Menu h = new Menu();
        h.pagina = pagina;
        Inventory inv = Bukkit.createInventory(h, 54, Poderes.txt("§5§lRunas " + c.cor + c.nome + " §8(nv " + n + "/" + MAX + ")"));
        h.inv = inv;
        // 20/11 (Paulo: "coloque vermelho no que nao esta habilitado, e nao preto em todos"): moldura roxa, o resto vazio
        ItemStack moldura = item(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, moldura);
        // resumo
        List<String> res = new ArrayList<>();
        res.add("§7Nível da classe: §f§l" + n + " §7de " + MAX);
        res.add("§7Ataque: §c+" + pct(bonusAtaque(p)) + " §7| Defesa: §a+" + pct(bonusDefesa(p)));
        res.add("§7Especialidade (" + (c == Classe.ENGENHEIRO ? "torres" : "habilidades") + "): §e+" + pct(n * 0.001));
        if (rebirths(p) > 0) res.add("§dREBIRTH: §f" + rebirths(p) + " §7(+" + pct(rebirths(p) * 0.003) + " de dano e defesa)");
        res.add("");
        res.add("§7Seu XP: §a" + xpTotal(p) + " pontos");
        res.add("§8Cada classe tem o seu nível: trocar de classe não zera.");
        inv.setItem(0, item(c.icone, c.cor + "§l" + c.nome + " §7— Runas", res, true));
        // abas
        for (int i = 0; i < SLOTS_ABA.length; i++) {
            int de = i * 10 + 1, ate = i * 10 + 10;
            boolean feita = n >= ate, atual = i == pagina;
            Material m = feita ? Material.LIME_STAINED_GLASS_PANE : n >= de ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            inv.setItem(SLOTS_ABA[i], item(m, (atual ? "§f§l▶ " : "§7") + "Níveis " + de + " a " + ate,
                    List.of(feita ? "§aCompleta" : n >= de ? "§eEm andamento" : "§8Ainda trancada", "§7Clique para ver"), atual));
        }
        // runas da aba
        Marco[] marcos = MARCOS.get(c);
        int poder = pl.nivelDePoder(p);
        for (int k = 0; k < 10; k++) {
            int nv = pagina * 10 + k + 1;
            boolean tem = n >= nv, proxima = nv == n + 1, marco = nv % 10 == 0;
            Marco mc = marco && marcos != null ? marcos[nv / 10 - 1] : null;
            List<String> l = new ArrayList<>();
            l.add("§7+0,1% de ataque e de defesa");
            l.add("§7+0,1% de " + (c == Classe.ENGENHEIRO ? "dano das torres" : "dano das habilidades"));
            if (mc != null) {
                l.add("");
                l.add("§6§lPASSIVA: §e" + mc.nome());
                l.add("§7" + mc.desc());
            }
            l.add("");
            Material mat;
            String nome;
            if (tem) {
                mat = marco ? Material.NETHER_STAR : Material.AMETHYST_SHARD;
                nome = "§a§lRuna " + nv + " §a✔";
                l.add("§aLiberada");
            } else if (proxima) {
                boolean semPoder = poder >= 0 && poder < nv && !p.hasPermission("poderes.todos");
                mat = semPoder ? Material.RED_DYE : marco ? Material.GOLD_NUGGET : Material.LIME_DYE;
                nome = "§e§lRuna " + nv;
                l.add("§7Preço: §a" + custo(nv) + " pontos de XP §7(você tem " + xpTotal(p) + ")");
                if (poder >= 0 && poder < nv) l.add("§cPrecisa do nível de poder " + nv + " §7(você: " + poder + ")");
                else l.add("§a§l▶ Clique para comprar");
            } else {
                mat = Material.RED_STAINED_GLASS_PANE;
                nome = "§cRuna " + nv + " §8(trancada)";
                l.add("§cCompre as anteriores primeiro");
            }
            inv.setItem(SLOTS_RUNA[k], item(mat, nome, l, tem && marco));
        }
        // slots do REBIRTH
        int rb = rebirths(p);
        for (int i = 0; i < SLOTS_REBIRTH.length; i++) {
            boolean ok = rb > i;
            inv.setItem(SLOTS_REBIRTH[i], item(ok ? Material.MAGENTA_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    ok ? "§d§lRuna de REBIRTH " + (i + 1) + " §a✔" : "§cRuna de REBIRTH " + (i + 1) + " §8(trancada)",
                    List.of("§7+0,3% de dano e +0,3% de defesa", ok ? "§aLiberada" : "§8Vem com o " + (i + 1) + "º REBIRTH"), ok));
        }
        inv.setItem(45, item(Material.END_CRYSTAL, "§5§l✦ §d§lREBIRTH §5§l✦",
                List.of("§7Venceu TODAS as masmorras de todos os níveis?", "§7Renasça: volta ao Nível de Masmorra 1,", "§7ganha uma runa de REBIRTH e o título.", "", "§a▶ Clique para ver"), true));
        inv.setItem(53, item(Material.BARRIER, "§cFechar", List.of(), false));
        p.openInventory(inv);
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int s = e.getSlot();
        if (s == 53) {
            p.closeInventory();
            return;
        }
        if (s == 45) {
            p.closeInventory();
            p.performCommand("rebirth");
            return;
        }
        for (int i = 0; i < SLOTS_ABA.length; i++)
            if (SLOTS_ABA[i] == s) {
                abrir(p, i);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                return;
            }
        for (int k = 0; k < SLOTS_RUNA.length; k++) {
            if (SLOTS_RUNA[k] != s) continue;
            int nv = h.pagina * 10 + k + 1;
            comprar(p, nv);
            abrir(p, h.pagina);
            return;
        }
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private void comprar(Player p, int nv) {
        Classe c = pl.classe(p);
        if (c == null) return;
        int n = nivel(p, c);
        if (nv <= n) return;
        if (nv != n + 1) {
            p.sendMessage(Poderes.txt("§d§lRUNAS §8» §7Compre a §fruna " + (n + 1) + " §7primeiro."));
            return;
        }
        int poder = pl.nivelDePoder(p);
        if (poder >= 0 && poder < nv && !p.hasPermission("poderes.todos")) {
            p.sendMessage(Poderes.txt("§d§lRUNAS §8» §cA runa " + nv + " precisa do nível de poder " + nv + "§7 (você: " + poder + "). Suba nas masmorras!"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        int preco = custo(nv), tem = xpTotal(p);
        if (tem < preco) {
            p.sendMessage(Poderes.txt("§d§lRUNAS §8» §cFaltam §f" + (preco - tem) + " §cpontos de XP §7(precisa de " + preco + ")."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }
        definirXp(p, tem - preco);
        dados.set(p.getUniqueId() + "." + c.id, nv);
        dados.set(p.getUniqueId() + ".nome", p.getName());
        sujo = true;
        salvar();
        p.playSound(p.getLocation(), nv % 10 == 0 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, nv % 10 == 0 ? 1f : 1.3f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.6);
        if (nv % 10 == 0) {
            Marco m = MARCOS.get(c) == null ? null : MARCOS.get(c)[nv / 10 - 1];
            p.showTitle(net.kyori.adventure.title.Title.title(Poderes.txt("§6§lRUNA " + nv), Poderes.txt(m == null ? "" : "§ePassiva: §f" + m.nome())));
            Bukkit.broadcast(Poderes.txt("§d§lRUNAS §8» §f" + p.getName() + " §7chegou ao §6nível " + nv + " §7de " + c.cor + c.nome + "§7!"));
        } else {
            p.sendActionBar(Poderes.txt("§aRuna " + nv + " liberada! §7(" + c.nome + " nv " + nv + ")"));
        }
    }
}
