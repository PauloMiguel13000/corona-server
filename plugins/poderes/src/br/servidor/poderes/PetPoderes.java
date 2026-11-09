package br.servidor.poderes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Pets que lutam sozinhos: batem nos monstros perto do dono, o dano sobe com o nivel e a raridade (3 a 15),
 * cada pet tem 5 poderes (liberados nos niveis 1, 3, 5, 7 e 9 do pet; o jogador escolhe um em /petpoder),
 * ganham XP nas masmorras e existe o "Pet Supremo" (so admin marca: /petpoder supremo).
 * Nunca acerta jogadores, pets, bichos de aliados nem nada nos mundos de minigame/login.
 */
final class PetPoderes implements Listener {

    static final String[] IDS = {"mordida", "investida", "uivo", "cura", "furia"};
    static final String[] NOMES = {"§c§lMordida Feroz", "§6§lInvestida", "§b§lUivo Gelado", "§a§lCura do Pet", "§e§lFúria Corona"};
    static final int[] NIVEL = {1, 3, 5, 7, 9};
    static final Material[] ICONES = {Material.BONE, Material.RABBIT_FOOT, Material.SNOWBALL, Material.GLISTERING_MELON_SLICE, Material.BLAZE_POWDER};
    static final String[][] DESCRICAO = {
            {"§7Morde forte e faz o monstro sangrar:", "§7golpe extra + 3 de sangramento."},
            {"§7Pula no monstro e acerta todos", "§7em volta, jogando eles longe."},
            {"§7Uiva e congela os monstros perto:", "§7ficam bem lentos e levam dano."},
            {"§7Cura o dono (3 corações) e dá", "§7regeneração. Vida cheia: escudo."},
            {"§7Fica furioso por 8 segundos:", "§7bate mais rápido, +50% de dano e fogo."}};
    static final List<String> MUNDOS_PADRAO = List.of("defesa", "backrooms", "hotel", "slender", "vovo", "login", "minigames", "terror", "galeria");

    private final Poderes pl;
    private final NamespacedKey chave;
    private final NamespacedKey kMasmorra = new NamespacedKey("masmorras", "masmorra");
    private final NamespacedKey kChefe = new NamespacedKey("masmorras", "chefe");
    private final Map<UUID, Long> recarga = new HashMap<>();
    private final Map<UUID, Long> furiaAte = new HashMap<>();

    PetPoderes(Poderes pl) {
        this.pl = pl;
        this.chave = new NamespacedKey(pl, "pet_poder");
    }

    // ------------------------------------------------------------------ regras

    boolean mundoLiberado(World w) {
        if (MUNDOS_PADRAO.contains(w.getName()) || pl.semPoderes(w)) return false;
        return !pl.getConfig().getStringList("pets.mundos-sem-ajuda").contains(w.getName());
    }

    boolean supremo(PetsPonte.PetInfo pet) {
        return pet != null && pet.id >= 0 && pl.getConfig().getIntegerList("pets.supremos").contains(pet.id);
    }

    private final NamespacedKey kChefeM = new NamespacedKey("masmorras", "chefe");

    /** Contra chefe de masmorra o pet bate bem menos (senao o pet mata o chefe sozinho). */
    double fatorAlvo(Entity e) {
        return e != null && e.getPersistentDataContainer().has(kChefeM) ? pl.getConfig().getDouble("pets.dano-em-chefe", 0.35) : 1.0;
    }

    private boolean bater(LivingEntity e, double d, Player dono) {
        return pl.causarDano(e, d * fatorAlvo(e), dono);
    }

    static double danoSupremo(int nivel) {
        return Math.min(16.0, 10.0 + nivel * 0.6);
    }

    boolean furioso(Player p) {
        Long t = furiaAte.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    double dano(PetsPonte.PetInfo pet, Player dono) {
        double d = (supremo(pet) ? danoSupremo(pet.nivel) : pet.dano()) * pl.getConfig().getDouble("pets.dano-multiplicador", 0.75);
        return furioso(dono) ? d * 1.5 : d;
    }

    boolean liberado(PetsPonte.PetInfo pet, int i) {
        return supremo(pet) || pet.nivel >= NIVEL[i];
    }

    /** Poder escolhido (0 a 4). Guarda o numero; os nomes antigos (mordida...furia) viram o mesmo numero. */
    int escolhido(Player p) {
        String s = p.getPersistentDataContainer().get(chave, PersistentDataType.STRING);
        if (s == null) return 0;
        if (s.length() == 1 && Character.isDigit(s.charAt(0))) return Math.min(4, s.charAt(0) - '0');
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(s)) return i;
        return 0;
    }

    /** Um monstro que o pet pode atacar (nunca jogador, pet, bicho de aliado, ou bicho com nome fora das masmorras). */
    boolean alvoValido(Player dono, Entity e) {
        if (!(e instanceof LivingEntity le) || !(e instanceof Enemy) || le.isDead() || !le.isValid()) return false;
        if (e instanceof Player || pl.pets.ehPet(e) || pl.ehAliado(e, dono)) return false;
        if (e.getPersistentDataContainer().has(kMasmorra)) return true;
        return e.customName() == null;
    }

    // ------------------------------------------------------------------ pet ataca sozinho

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::procurar, 60L, 20L);
    }

    private void procurar() {
        if (!pl.getConfig().getBoolean("pets.atacar-sozinho", true)) return;
        double raio = pl.getConfig().getDouble("pets.raio-ataque", 10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead() || !mundoLiberado(p.getWorld())) continue;
            PetsPonte.PetInfo pet = pl.pets.petAtivo(p);
            if (pet == null || !pet.entidade.isValid() || !pet.entidade.getWorld().equals(p.getWorld())) continue;
            if (supremo(pet)) Fx.p(pet.entidade.getWorld(), Particle.END_ROD, pet.entidade.getLocation().add(0, pet.entidade.getHeight() + 0.3, 0), 3, 0.25, 0.15, 0.25, 0.01);
            else if (pet.lendario()) auraLendaria(pet.entidade);
            if (!Poderes.logado(p)) continue;
            LivingEntity melhor = null;
            double md = Double.MAX_VALUE;
            for (Entity e : p.getNearbyEntities(raio, 6, raio)) {
                if (!alvoValido(p, e) || calmo(e, p)) continue;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < md && p.hasLineOfSight(e)) {
                    md = d;
                    melhor = (LivingEntity) e;
                }
            }
            if (melhor != null) pl.ajudarPet(p, melhor);
        }
    }

    /** Bichos neutros (piglin, enderman, warden...) so apanham sozinhos se ja estao atacando o dono ou sao da masmorra. */
    private boolean calmo(Entity e, Player dono) {
        if (e.getPersistentDataContainer().has(kMasmorra)) return false;
        if (e instanceof org.bukkit.entity.PigZombie || e instanceof org.bukkit.entity.Enderman || e instanceof org.bukkit.entity.Warden
                || e instanceof org.bukkit.entity.Piglin || e instanceof org.bukkit.entity.Spider)
            return !(e instanceof org.bukkit.entity.Mob m && dono.equals(m.getTarget()));
        return false;
    }

    // ------------------------------------------------------------------ os 5 poderes

    /** Chamado quando o golpe do pet entra: usa o poder escolhido (cada bicho tem os seus, ver PetCatalogo). */
    void talvezUsar(Player dono, PetsPonte.PetInfo pet, LivingEntity alvo) {
        long agora = System.currentTimeMillis();
        boolean sup = supremo(pet);
        if (pet.lendario() && !sup) furiaLendaria(dono, pet, alvo, agora);
        long cd = sup ? 6000L : pet.lendario() ? pl.getConfig().getLong("pets.poder-recarga-lendario-ms", 5000L) : pl.getConfig().getLong("pets.poder-recarga-ms", 8000L);
        Long u = recarga.get(dono.getUniqueId());
        if (u != null && agora - u < cd) return;
        int i = escolhido(dono);
        if (!liberado(pet, i)) i = 0;
        recarga.put(dono.getUniqueId(), agora);
        PetCatalogo.Poder pd = PetCatalogo.de(pet.entidade).poderes().get(i);
        double d = dano(pet, dono);
        LivingEntity pe = pet.entidade;
        World w = alvo.getWorld();
        Location la = alvo.getLocation().add(0, alvo.getHeight() / 2, 0);
        Poderes.barra(dono, pd.nome() + " §7do seu pet!");
        Particle part = pd.part() != null && pd.part().getDataType() == Void.class ? pd.part() : Particle.CRIT;
        switch (pd.acao()) {
            case GOLPE -> {
                acertar(dono, alvo, d * pd.mult(), pd, pe.getLocation());
                Fx.p(w, part, la, 14, 0.35, 0.35, 0.35, 0.08);
                w.playSound(la, pd.som(), 1f, pd.tom());
                for (int k = 1; k <= pd.sangra(); k++)
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        if (!alvo.isValid() || alvo.isDead() || !dono.isOnline()) return;
                        Fx.p(alvo.getWorld(), Particle.DUST, alvo.getLocation().add(0, alvo.getHeight() / 2, 0), 8, 0.25, 0.3, 0.25,
                                new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
                        bater(alvo, Math.max(1.0, d * 0.3), dono);
                    }, 20L * k);
            }
            case AREA -> {
                Fx.p(w, part, la, 50, pd.raio() * 0.6, 0.8, pd.raio() * 0.6, 0.05);
                w.playSound(la, pd.som(), 1f, pd.tom());
                for (LivingEntity e : alvosPerto(dono, alvo, alvo.getLocation(), pd.raio())) acertar(dono, e, d * pd.mult(), pd, alvo.getLocation());
            }
            case INVESTIDA -> {
                Vector v = alvo.getLocation().toVector().subtract(pe.getLocation().toVector());
                if (v.lengthSquared() > 0.01) pe.setVelocity(v.normalize().multiply(1.1).setY(0.45));
                Fx.p(w, part, la, 30, 1.2, 0.5, 1.2, 0.05);
                w.playSound(la, pd.som(), 1f, pd.tom());
                for (LivingEntity e : alvosPerto(dono, alvo, alvo.getLocation(), pd.raio())) acertar(dono, e, d * pd.mult(), pd, e == alvo ? pe.getLocation() : alvo.getLocation());
            }
            case ONDA -> {
                Location c = pe.getLocation();
                w.playSound(c, pd.som(), 1f, pd.tom());
                for (int k = 0; k < 3; k++) {
                    final double r = pd.raio() * (k + 1) / 3.0;
                    Bukkit.getScheduler().runTaskLater(pl, () -> anel(w, c.clone().add(0, 0.4, 0), r, part), 3L * k);
                }
                for (LivingEntity e : alvosPerto(dono, alvo, c, pd.raio())) acertar(dono, e, d * pd.mult(), pd, c);
            }
            case CURA -> {
                AttributeInstance a = dono.getAttribute(Attribute.MAX_HEALTH);
                double max = a == null ? 20 : a.getValue();
                double cura = sup ? 10 : 6;
                if (dono.getHealth() >= max - 0.5) dono.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, sup ? 1 : 0, false, true));
                else dono.setHealth(Math.min(max, dono.getHealth() + cura));
                dono.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, true));
                if (pd.efeito() != null) dono.addPotionEffect(new PotionEffect(pd.efeito(), Math.max(20, pd.segundos() * 20), pd.forca(), false, true));
                if (pd.icone() == Material.MILK_BUCKET)
                    for (PotionEffectType ruim : List.of(PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                            PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE))
                        dono.removePotionEffect(ruim);
                Fx.p(dono.getWorld(), part, dono.getLocation().add(0, 1.6, 0), 12, 0.5, 0.5, 0.5, 0.02);
                Fx.p(dono.getWorld(), Particle.HEART, dono.getLocation().add(0, 2, 0), 5, 0.4, 0.3, 0.4, 0);
                w.playSound(dono.getLocation(), pd.som(), 0.8f, pd.tom());
                bater(alvo, d * pd.mult(), dono);
            }
            case BUFF -> {
                if (pd.efeito() != null) dono.addPotionEffect(new PotionEffect(pd.efeito(), Math.max(20, pd.segundos() * 20), pd.forca(), false, true));
                dono.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Math.max(20, pd.segundos() * 20), 0, false, true));
                Fx.p(dono.getWorld(), part, dono.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
                w.playSound(dono.getLocation(), pd.som(), 0.8f, pd.tom());
                acertar(dono, alvo, d * pd.mult(), pd, pe.getLocation());
            }
            case FURIA -> {
                furiaAte.put(dono.getUniqueId(), agora + 8000L);
                if (pd.efeito() != null && pd.efeito() != PotionEffectType.POISON)
                    dono.addPotionEffect(new PotionEffect(pd.efeito(), Math.max(20, pd.segundos() * 20), pd.forca(), false, true));
                Fx.p(w, part, la, 40, 1.5, 0.8, 1.5, 0.05);
                Fx.p(w, Particle.ANGRY_VILLAGER, pe.getLocation().add(0, pe.getHeight() + 0.3, 0), 4, 0.3, 0.2, 0.3, 0);
                w.playSound(la, pd.som(), 1f, pd.tom());
                for (LivingEntity e : alvosPerto(dono, alvo, alvo.getLocation(), pd.raio())) acertar(dono, e, d * pd.mult(), pd, alvo.getLocation());
            }
        }
    }

    // ------------------------------------------------------------------ so dos lendarios

    private final Map<UUID, Long> lendariaCd = new HashMap<>();
    private static final Particle.DustOptions OURO = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 40), 1.1f);
    private static final Particle.DustOptions OURO_CLARO = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 240, 150), 0.8f);

    /** Aura dourada em volta do pet lendario (todo segundo): um anel girando e brilhinhos. So particulas (Java e Bedrock). */
    private void auraLendaria(LivingEntity pe) {
        try {
            World w = pe.getWorld();
            Location c = pe.getLocation();
            double h = pe.getHeight();
            double a0 = (System.currentTimeMillis() % 3600) / 3600.0 * Math.PI * 2;
            for (int k = 0; k < 6; k++) {
                double a = a0 + k * Math.PI / 3;
                Fx.p(w, Particle.DUST, c.getX() + Math.cos(a) * 0.6, c.getY() + h * 0.5, c.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0, k % 2 == 0 ? OURO : OURO_CLARO);
            }
            Fx.p(w, Particle.WAX_ON, c.clone().add(0, h + 0.25, 0), 2, 0.2, 0.1, 0.2, 0);
        } catch (Throwable ignored) {
        }
    }

    /** Poder exclusivo dos lendarios (a cada 12 s, sozinho): raio dourado no alvo, explosao de luz que acerta em volta,
     * cura 2 coracoes do dono e da Resistencia I por 4 s. */
    private void furiaLendaria(Player dono, PetsPonte.PetInfo pet, LivingEntity alvo, long agora) {
        Long u = lendariaCd.get(dono.getUniqueId());
        if (u != null && agora - u < pl.getConfig().getLong("pets.lendario-poder-ms", 12000L)) return;
        lendariaCd.put(dono.getUniqueId(), agora);
        try {
            World w = alvo.getWorld();
            Location la = alvo.getLocation();
            w.strikeLightningEffect(la);
            Fx.p(w, Particle.DUST, la.clone().add(0, 1, 0), 60, 1.6, 0.9, 1.6, 0, OURO);
            Fx.p(w, Particle.TOTEM_OF_UNDYING, la.clone().add(0, 1, 0), 40, 0.8, 0.8, 0.8, 0.35);
            Fx.p(w, Particle.FLASH, la.clone().add(0, 1, 0), 1, 0, 0, 0, 0, org.bukkit.Color.fromRGB(255, 220, 90));
            w.playSound(la, Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.4f);
            w.playSound(la, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.6f);
            double d = dano(pet, dono) * pl.getConfig().getDouble("pets.lendario-poder-mult", 1.5);
            for (LivingEntity e : alvosPerto(dono, alvo, la, 4.0)) {
                bater(e, d, dono);
                Vector v = e.getLocation().toVector().subtract(la.toVector());
                if (v.lengthSquared() > 0.01) e.setVelocity(v.normalize().multiply(0.6).setY(0.35));
            }
            AttributeInstance a = dono.getAttribute(Attribute.MAX_HEALTH);
            double max = a == null ? 20 : a.getValue();
            dono.setHealth(Math.min(max, dono.getHealth() + 4.0));
            dono.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0, false, true));
            Poderes.barra(dono, "§6§l✦ Fúria Lendária §7do seu pet!");
        } catch (Throwable ignored) {
        }
    }

    /** Monstros validos em volta (o alvo sempre entra; os calmos de fora nao). */
    private List<LivingEntity> alvosPerto(Player dono, LivingEntity alvo, Location c, double raio) {
        List<LivingEntity> l = new ArrayList<>();
        if (alvoValido(dono, alvo)) l.add(alvo);
        if (raio <= 0) return l;
        for (Entity e : c.getWorld().getNearbyEntities(c, raio, 2.5, raio)) {
            if (e == alvo || !alvoValido(dono, e) || calmo(e, dono)) continue;
            l.add((LivingEntity) e);
        }
        return l;
    }

    /** Dano + efeitos do poder num monstro. */
    private void acertar(Player dono, LivingEntity e, double dano, PetCatalogo.Poder pd, Location de) {
        bater(e, dano, dono);
        if (pd.efeito() != null && pd.acao() != PetCatalogo.Acao.CURA && pd.acao() != PetCatalogo.Acao.BUFF
                && (pd.acao() != PetCatalogo.Acao.FURIA || pd.efeito() == PotionEffectType.POISON))
            e.addPotionEffect(new PotionEffect(pd.efeito(), Math.max(20, pd.segundos() * 20), pd.forca(), false, true));
        if (pd.fogo()) e.setFireTicks(Math.max(e.getFireTicks(), 80));
        if (pd.gelo()) e.setFreezeTicks(Math.max(e.getFreezeTicks(), 100));
        if (pd.empurrao() > 0 && de != null) {
            Vector v = e.getLocation().toVector().subtract(de.toVector());
            if (v.lengthSquared() > 0.01) e.setVelocity(v.normalize().multiply(pd.empurrao()).setY(0.35 + pd.empurrao() * 0.1));
        }
    }

    private static void anel(World w, Location c, double r, Particle part) {
        int n = Math.max(12, (int) (r * 8));
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * k / n;
            Fx.p(w, part, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private static void empurrar(Entity e, Location de) {
        Vector v = e.getLocation().toVector().subtract(de.toVector());
        if (v.lengthSquared() < 0.01) return;
        e.setVelocity(v.normalize().multiply(0.9).setY(0.4));
    }

    // ------------------------------------------------------------------ XP nas masmorras

    @EventHandler
    public void morte(EntityDeathEvent e) {
        LivingEntity m = e.getEntity();
        if (!m.getPersistentDataContainer().has(kMasmorra) && !m.getPersistentDataContainer().has(kChefe)) return;
        if (!pl.getConfig().getBoolean("pets.xp-masmorras", true)) return;
        boolean chefe = m.getPersistentDataContainer().has(kChefe);
        AttributeInstance a = m.getAttribute(Attribute.MAX_HEALTH);
        double vida = a == null ? 20 : a.getValue();
        double xp = chefe ? Math.max(40, vida * pl.getConfig().getDouble("pets.xp-chefe-por-vida", 0.8))
                : Math.max(3, vida * pl.getConfig().getDouble("pets.xp-monstro-por-vida", 0.3));
        List<Player> quem = new ArrayList<>();
        Player k = m.getKiller();
        if (k != null) quem.add(k);
        if (chefe)
            for (Player p : m.getWorld().getPlayers())
                if (!quem.contains(p) && p.getLocation().distanceSquared(m.getLocation()) < 40 * 40) quem.add(p);
        int x = (int) Math.round(xp);
        for (Player p : quem) {
            PetsPonte.PetInfo pet = pl.pets.petAtivo(p);
            if (pet == null) continue;
            int antes = pet.nivel;
            int novo = pl.pets.darXp(p, x);
            if (novo < 0) continue;
            if (novo > antes) {
                p.sendMessage(Poderes.txt("§d§l✦ §dSeu pet subiu para o nível §f" + novo + "§d! §7Poderes do pet: §e/petpoder"));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                List<PetCatalogo.Poder> lp = PetCatalogo.de(pet.entidade).poderes();
                for (int i = 0; i < NIVEL.length; i++)
                    if (antes < NIVEL[i] && novo >= NIVEL[i]) p.sendMessage(Poderes.txt("§d§l✦ §7Novo poder do pet liberado: " + lp.get(i).nome()));
            } else if (!chefe) Poderes.barra(p, "§d+" + x + " XP do pet");
            else p.sendMessage(Poderes.txt("§d+" + x + " XP para o seu pet (chefe da masmorra)."));
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        recarga.remove(e.getPlayer().getUniqueId());
        furiaAte.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ /petpoder

    static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    boolean comando(CommandSender s, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("supremo")) {
            if (!s.hasPermission("poderes.admin")) {
                s.sendMessage(Poderes.txt("§cSó admin."));
                return true;
            }
            Player alvo = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (s instanceof Player p ? p : null);
            if (alvo == null) {
                s.sendMessage(Poderes.txt("§cUse: /petpoder supremo [jogador] (com o pet chamado)"));
                return true;
            }
            PetsPonte.PetInfo pet = pl.pets.petAtivo(alvo);
            if (pet == null || pet.id < 0) {
                s.sendMessage(Poderes.txt("§c" + alvo.getName() + " precisa estar com um pet chamado (/pets)."));
                return true;
            }
            List<Integer> l = new ArrayList<>(pl.getConfig().getIntegerList("pets.supremos"));
            boolean virou = !l.contains(pet.id);
            if (virou) l.add(pet.id);
            else l.remove(Integer.valueOf(pet.id));
            pl.getConfig().set("pets.supremos", l);
            pl.saveConfig();
            s.sendMessage(Poderes.txt(virou ? "§6§l✦ " + pet.nome + " §6de " + alvo.getName() + " agora é um PET SUPREMO! §7(dano " + String.format(java.util.Locale.ROOT, "%.1f", danoSupremo(pet.nivel) * pl.getConfig().getDouble("pets.dano-multiplicador", 0.75))
                    + ", todos os poderes, recarga 6 s)" : "§7" + pet.nome + " de " + alvo.getName() + " voltou a ser um pet normal."));
            if (virou && alvo != s) alvo.sendMessage(Poderes.txt("§6§l✦ Seu pet " + pet.nome + " virou um PET SUPREMO!"));
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage(Poderes.txt("§cSó jogador. Admin: petpoder supremo <jogador>"));
            return true;
        }
        abrir(p);
        return true;
    }

    void abrir(Player p) {
        PetsPonte.PetInfo pet = pl.pets.petAtivo(p);
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, Poderes.txt("§5§lPoderes do Pet"));
        h.inv = inv;
        ItemStack vidro = item(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        if (pet == null) {
            inv.setItem(13, item(Material.BARRIER, "§cChame um pet primeiro", List.of("§7Use §e/pets §7e chame o seu pet.", "§7Depois volte aqui: §e/petpoder")));
        } else {
            boolean sup = supremo(pet);
            List<String> info = new ArrayList<>(List.of("§7Nível: §f" + pet.nivel + "  §7Raridade: §f" + pet.raridade,
                    "§7Dano por golpe: §c" + String.format(java.util.Locale.ROOT, "%.1f", dano(pet, p)),
                    "§7Ele ataca sozinho os monstros perto de você.", "§7Masmorras dão XP para o pet!"));
            if (pet.lendario()) {
                info.add("");
                info.add("§6§l✦ LENDÁRIO");
                info.add("§eFúria Lendária §7a cada 12 s: raio dourado,");
                info.add("§7cura 2 corações e dá Resistência.");
                info.add("§7Dano quase em dobro e aura dourada.");
            }
            inv.setItem(4, item(sup ? Material.NETHER_STAR : Material.NAME_TAG, (sup ? "§6§l✦ PET SUPREMO: " : pet.lendario() ? "§6§l" : "§d§l") + pet.nome, info));
            int esc = escolhido(p);
            PetCatalogo.Familia fam = PetCatalogo.de(pet.entidade);
            for (int i = 0; i < 5; i++) {
                PetCatalogo.Poder pd = fam.poderes().get(i);
                boolean ok = liberado(pet, i);
                List<String> lore = new ArrayList<>(List.of(pd.d1(), pd.d2()));
                lore.add("");
                if (!ok) lore.add("§cLibera no nível " + NIVEL[i] + " do pet.");
                else if (i == esc) lore.add("§a§l✔ ESCOLHIDO");
                else lore.add("§eClique para escolher.");
                ItemStack it = item(ok ? pd.icone() : Material.GRAY_DYE, (ok ? "" : "§8") + pd.nome() + " §7(nível " + NIVEL[i] + ")", lore);
                if (ok && i == esc) {
                    ItemMeta m = it.getItemMeta();
                    m.setEnchantmentGlintOverride(true);
                    it.setItemMeta(m);
                }
                inv.setItem(11 + i, it);
            }
            inv.setItem(22, item(Material.BOOK, "§eComo funciona", List.of("§7Cada bicho tem os seus poderes", "§7(este é: §f" + fam.nome() + "§7).", "§7O pet usa o poder escolhido sozinho,", "§7a cada " + (sup ? 6 : pl.getConfig().getLong("pets.poder-recarga-ms", 8000L) / 1000) + " segundos de luta.")));
        }
        p.openInventory(inv);
    }

    private static ItemStack item(Material m, String nome, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Poderes.txt(nome));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Poderes.txt(s));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void clique(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int i = e.getRawSlot() - 11;
        if (i < 0 || i > 4) return;
        PetsPonte.PetInfo pet = pl.pets.petAtivo(p);
        if (pet == null) return;
        if (!liberado(pet, i)) {
            p.sendMessage(Poderes.txt("§cEsse poder libera no nível " + NIVEL[i] + " do pet. §7(seu pet: nível " + pet.nivel + ")"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        p.getPersistentDataContainer().set(chave, PersistentDataType.STRING, String.valueOf(i));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        p.sendMessage(Poderes.txt("§aPoder do pet: " + PetCatalogo.de(pet.entidade).poderes().get(i).nome()));
        Bukkit.getScheduler().runTask(pl, () -> abrir(p));
    }

    @EventHandler
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
