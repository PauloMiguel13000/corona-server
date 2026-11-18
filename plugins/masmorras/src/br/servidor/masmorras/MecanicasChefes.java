package br.servidor.masmorras;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * 17/11 (Paulo: "faça coisas diferentes em todos os bosses"): cada chefe ganhou mecanicas proprias, alem das
 * habilidades de sempre. Nivel 1 e 2: uma mecanica; nivel 3 em diante: duas, que se revezam. Cada mecanica e uma
 * "cena" curta (5 a 25 s) que comeca a cada ~25 s de luta; durante a cena o chefe nao usa as habilidades normais.
 * Tudo que a cena poe no mundo (blocos, sombras, pedras caindo) e tirado no fim, quando o chefe morre ou some e quando
 * o plugin desliga. Teste: /masmorras admin mecanica &lt;nome&gt; [masmorra].
 * Gelo (descida de barco), Trovao (tempestade) e Herobrine (inferno) ja tem a fase propria e ficam de fora.
 */
final class MecanicasChefes implements Listener {

    enum Mec {
        SOMBRAS("Sombras", "Cria cópias iguais a ele: ache o verdadeiro! Bater numa sombra deixa você cego."),
        CASULO("Prisão", "Prende alguém: clique na prisão para soltar antes que o tempo acabe."),
        CHAO("Chão marcado", "Marca partes do chão de vermelho: saia das marcas antes de explodirem."),
        SILENCIO("Silêncio", "Quando ele mandar NÃO SE MEXER, fique parado: quem andar leva dano."),
        OLHAR("Olhar", "Manda OLHAR ou NÃO OLHAR para ele: obedeça antes do tempo acabar."),
        CHUVA("Chuva", "Coisas caem do teto: fuja dos círculos vermelhos no chão."),
        INVESTIDA("Investida", "Mira e dispara em linha reta: desvie e faça ele bater na parede (fica tonto e leva mais dano)."),
        ANEIS("Ondas", "Solta ondas em anel pelo chão: pule quando a onda passar por você."),
        LAMINA("Lâmina", "Gira uma lâmina pela arena: PULE a lâmina baixa e AGACHE na alta."),
        ALVOS("Escudo", "Fica imune até alguém quebrar os alvos brilhando na arena (clique neles)."),
        MINAS("Armadilhas", "Espalha armadilhas pela arena: clique nelas antes que estourem."),
        BOMBA("Bomba", "Transforma alguém em bomba: leve a bomba até o chefe e longe dos amigos."),
        REDEMOINHO("Redemoinho", "Puxa todo mundo para o meio da arena: corra para fora."),
        ELO("Elo", "Prende todos num elo: fique a mais de 7 blocos dele quando o elo fechar."),
        RASTRO("Rastro", "Corre deixando um rastro que machuca: não pise nele."),
        // 17/11 (ideias do Paulo e as aprovadas "amei suas ideias")
        NINHADA("Ninhada", "Sobe no teto e joga ovos que viram aranhas; depois desce e luta junto com elas."),
        ENGOLIR("Engolir", "Engole alguém: quem foi engolido tem que bater nela sem parar para sair."),
        BURACO("Buraco negro", "Abre um buraco negro que puxa todo mundo: corra para longe! Quem cair dentro faz ele explodir."),
        MARE("Naufrágio", "O navio afunda e a água sobe: suba nos mastros (escadas) para não se afogar."),
        LIVRO("Livro", "Puxa todos para dentro de um livro: pule pelas páginas. Quem chega ao fim ganha força; quem cai volta fraco."),
        VITRAIS("Luz do sol", "Fora do sol ele quase não se machuca: quebre os vitrais (clique neles) para a luz entrar."),
        MURALHAS("Labirinto", "A arena vira um labirinto que muda a cada 10 s: ele só fica vulnerável quando bate numa parede."),
        GRAVIDADE("Gravidade invertida", "A gravidade inverte: todo mundo flutua e a luta continua lá no teto."),
        ILHAS("Ilhas", "O chão some: fique nas ilhas e pule entre elas. As rajadas de vento empurram você."),
        TEMPO("Tempo parado", "Para o tempo: só um jogador se mexe e tem 5 s para quebrar a engrenagem que solta os outros.");

        final String nome, desc;

        Mec(String nome, String desc) {
            this.nome = nome;
            this.desc = desc;
        }
    }

    /**
     * Como as mecanicas ficam num chefe: particula, bloco (prisao, alvos, armadilhas, chuva), cor das marcas, nome das
     * coisas ("corações", "covas") e estilo (lamina: 0 baixa, 1 alta, 2 as duas; olhar: 0 sorteia, 1 olhe, 2 nao olhe).
     */
    record Tema(Mec[] mecs, String part, Material bloco, int cor, String coisa, int estilo) {}

    static final Map<String, Tema> TEMAS = new HashMap<>();

    /**
     * 18/11 (Paulo): "o de passar a lamina, do chao gerar uma marca, explodir TNT e ficar parada nao encaixou; quando for
     * para adicionar isso eu aviso". Ficam desligadas em todos os chefes (o resto continua). O admin ainda testa pelo nome.
     */
    static final java.util.Set<Mec> DESLIGADAS = java.util.EnumSet.of(Mec.LAMINA, Mec.CHAO, Mec.BOMBA, Mec.SILENCIO);

    private static void t(String id, String part, Material bloco, int cor, String coisa, int estilo, Mec... mecs) {
        Mec[] ok = java.util.Arrays.stream(mecs).filter(m -> !DESLIGADAS.contains(m)).toArray(Mec[]::new);
        TEMAS.put(id, new Tema(ok, part, bloco, cor, coisa, estilo));
    }

    static {
        // ---------------- nivel 1
        t("CRIPTA", "SOUL_FIRE_FLAME", Material.BONE_BLOCK, 0x4FD8FF, "ossos", 0, Mec.CHAO);
        t("NINHO", "CRIT", Material.COBWEB, 0xDDDDDD, "teias", 0, Mec.NINHADA);   // 17/11: a prisao de teia foi para a Viuva Negra
        t("CEMITERIO", "SOUL", Material.ROOTED_DIRT, 0x7A5A3A, "covas", 0, Mec.MINAS);
        t("ESGOTO", "ITEM_SLIME", Material.SLIME_BLOCK, 0x7CCB5B, "gosmas", 0, Mec.ENGOLIR);
        t("ACAMPAMENTO", "FLAME", Material.TNT, 0xFF5522, "barris", 0, Mec.BOMBA);
        t("MOINHO", "WHITE_ASH", Material.HAY_BLOCK, 0xE0A030, "palhas", 0, Mec.OLHAR);
        t("FUNGOS", "CRIMSON_SPORE", Material.RED_MUSHROOM_BLOCK, 0xD03030, "cogumelos", 0, Mec.MINAS);
        t("COVIL_LOBOS", "SMOKE", Material.BONE_BLOCK, 0x888888, "uivos", 0, Mec.SOMBRAS);
        // ---------------- nivel 2
        t("MINA", "LARGE_SMOKE", Material.COBBLESTONE, 0x8A8A8A, "pedras", 0, Mec.CHUVA);
        t("PANTANO", "WITCH", Material.CAULDRON, 0x9B3BD0, "caldeirões", 0, Mec.REDEMOINHO);
        t("DESERTO", "WHITE_ASH", Material.SANDSTONE, 0xE0C080, "miragens", 0, Mec.SOMBRAS);
        t("AFOGADA", "SPLASH", Material.PRISMARINE, 0x3A7BD5, "ondas", 0, Mec.ANEIS);
        t("URSO", "SNOWFLAKE", Material.PACKED_ICE, 0xBFE8FF, "patadas", 0, Mec.INVESTIDA);
        t("BOSQUE_PALIDO", "WHITE_ASH", Material.CREAKING_HEART, 0xE8E4D8, "corações", 0, Mec.ALVOS);
        t("FAROL", "END_ROD", Material.SEA_LANTERN, 0xFFE070, "luzes", 1, Mec.LAMINA);
        t("LABORATORIO", "WITCH", Material.BREWING_STAND, 0x50E050, "poções", 0, Mec.BOMBA);
        t("CAMARA_TESTES", "ELECTRIC_SPARK", Material.COPPER_BULB, 0xE07A40, "lâmpadas", 0, Mec.ANEIS);
        t("FAZENDA", "COMPOSTER", Material.CARVED_PUMPKIN, 0xF09020, "abóboras", 0, Mec.MINAS);
        t("CRATERA", "FLAME", Material.MAGMA_BLOCK, 0xFF6020, "meteoros", 0, Mec.CHUVA);
        t("JARDIM", "SWEEP_ATTACK", Material.CHERRY_LEAVES, 0xFF9BC8, "pétalas", 0, Mec.LAMINA);
        // ---------------- nivel 3 (duas mecanicas)
        t("TORRE", "ENCHANT", Material.AMETHYST_BLOCK, 0xA060FF, "runas", 0, Mec.GRAVIDADE, Mec.ELO);
        t("FORJA", "FLAME", Material.MAGMA_BLOCK, 0xFF7A00, "brasas", 0, Mec.ANEIS, Mec.RASTRO);
        t("QUARTEL", "CRIT", Material.TNT, 0xB04030, "barris", 0, Mec.INVESTIDA, Mec.BOMBA);
        t("SELVA", "SPORE_BLOSSOM_AIR", Material.MOSSY_COBBLESTONE, 0x40B040, "totens", 0, Mec.MINAS, Mec.ELO);
        t("CATEDRAL", "END_ROD", Material.GOLD_BLOCK, 0xFFF0B0, "vitrais", 0);   // 18/11 (Paulo): o Bispo so revive, sem chao marcado nem "nao se mexa"
        t("TESOURO", "WAX_ON", Material.GOLD_BLOCK, 0xFFC020, "baús", 0, Mec.CHUVA, Mec.ALVOS);
        t("COLISEU", "CRIT", Material.CHISELED_STONE_BRICKS, 0xD04040, "ondas", 0, Mec.ANEIS, Mec.INVESTIDA);
        t("OBSERVATORIO", "FIREWORK", Material.GLOWSTONE, 0x7090FF, "estrelas", 2, Mec.CHUVA, Mec.LAMINA);
        t("VIUVA", "CRIT", Material.COBWEB, 0x303030, "teias", 0, Mec.CASULO, Mec.SOMBRAS);
        t("NAUFRAGIO", "SPLASH", Material.COAL_BLOCK, 0x2050A0, "balas de canhão", 0, Mec.MARE, Mec.CHUVA);
        t("MAUSOLEU", "WHITE_ASH", Material.CHISELED_SANDSTONE, 0xE8C878, "maldições", 2, Mec.OLHAR, Mec.CHAO);
        t("BIBLIOTECA", "ENCHANT", Material.CHISELED_BOOKSHELF, 0xC09060, "livros", 0, Mec.LIVRO, Mec.SILENCIO);
        t("DELTA", "LARGE_SMOKE", Material.BASALT, 0x505060, "pedras", 0, Mec.INVESTIDA, Mec.CHAO);
        // ---------------- nivel 4
        t("BASTIAO", "CRIMSON_SPORE", Material.GOLD_BLOCK, 0xFFB000, "ouros", 0, Mec.BOMBA, Mec.ALVOS);
        t("WITHER", "SOUL_FIRE_FLAME", Material.SOUL_SAND, 0x303030, "almas", 0, Mec.ELO, Mec.CHAO);
        t("VENTOS", "CLOUD", Material.WHITE_WOOL, 0xE0F0FF, "ventos", 0, Mec.ILHAS, Mec.REDEMOINHO);
        t("CASTELO", "SMOKE", Material.IRON_BARS, 0x902020, "machados", 0, Mec.LAMINA, Mec.CHAO);
        t("COVIL", "CRIMSON_SPORE", Material.NETHERRACK, 0xC03030, "pegadas", 0, Mec.INVESTIDA, Mec.RASTRO);
        t("VULCAO", "LAVA", Material.MAGMA_BLOCK, 0xFF4000, "rochas", 0, Mec.CHUVA, Mec.ANEIS);
        t("CIDADE_ANTIGA", "SCULK_SOUL", Material.SCULK_SHRIEKER, 0x0A8090, "ecos", 0, Mec.SILENCIO, Mec.ELO);
        t("GELEIRA", "SNOWFLAKE", Material.PACKED_ICE, 0xA0E0FF, "blocos de gelo", 0, Mec.CHUVA, Mec.INVESTIDA);
        t("SANGUE", "DAMAGE_INDICATOR", Material.RED_STAINED_GLASS, 0xA00010, "vitrais", 0, Mec.VITRAIS, Mec.ELO);
        t("RELOGIO", "WAX_OFF", Material.GOLD_BLOCK, 0xFFD040, "ponteiros", 2, Mec.TEMPO, Mec.LAMINA);
        t("LABIRINTO", "CRIT", Material.MOSSY_STONE_BRICKS, 0x806040, "chifradas", 0, Mec.MURALHAS, Mec.CHAO);
        t("CRIPTA_REAL", "SOUL", Material.SOUL_LANTERN, 0x60E0FF, "almas", 0, Mec.CHAO, Mec.SOMBRAS);
        t("CARMESIM", "CRIMSON_SPORE", Material.NETHER_WART_BLOCK, 0xB02020, "fungos", 0, Mec.INVESTIDA, Mec.MINAS);
        t("DISTORCIDO", "WARPED_SPORE", Material.WARPED_WART_BLOCK, 0x20B0A0, "esporos", 0, Mec.RASTRO, Mec.MINAS);
        t("ALMAS", "SOUL", Material.SOUL_SAND, 0x60D0E0, "almas", 0, Mec.ELO, Mec.LAMINA);
        t("OBSIDIANA", "FLAME", Material.CRYING_OBSIDIAN, 0x8020C0, "obsidianas", 0, Mec.CHUVA, Mec.BOMBA);
        t("CORO", "PORTAL", Material.MAGENTA_GLAZED_TERRACOTTA, 0xB080D0, "sementes do coro", 0, Mec.MINAS, Mec.REDEMOINHO);
        t("ABISMO", "REVERSE_PORTAL", Material.OBSIDIAN, 0x6020A0, "flechas", 0, Mec.LAMINA, Mec.SOMBRAS);
        // ---------------- nivel 5
        t("CIDADELA", "PORTAL", Material.PURPUR_BLOCK, 0x9040D0, "ondas", 0, Mec.BURACO, Mec.ANEIS);
        t("PRISAO", "SCULK_SOUL", Material.IRON_BARS, 0x404050, "celas", 0, Mec.CASULO, Mec.SILENCIO);
        t("ILHAS", "END_ROD", Material.END_STONE, 0xE0E0A0, "pedras do fim", 0, Mec.CHUVA, Mec.ANEIS);
        t("NAVE", "END_ROD", Material.PURPUR_PILLAR, 0xC080E0, "cascos", 0, Mec.ALVOS, Mec.BOMBA);
        t("TRONO", "PORTAL", Material.OBSIDIAN, 0x401060, "pedaços do trono", 0, Mec.INVESTIDA, Mec.CHUVA);
    }

    static final NamespacedKey K_SOMBRA = new NamespacedKey("masmorras", "sombra");
    static final NamespacedKey K_PEDRA = new NamespacedKey("masmorras", "pedra_caindo");
    @SuppressWarnings("deprecation")
    private static final NamespacedKey K_BALA_TORRE = new NamespacedKey("poderes", "torreta_bala");
    private static final NamespacedKey K_SEM_NOME_LM = NamespacedKey.fromString("levelledmobs:denylm_nametag");

    private final Masmorras pl;
    private final Random rnd = new Random();
    private final Map<Chefe, Luta> lutas = new IdentityHashMap<>();

    MecanicasChefes(Masmorras pl) {
        this.pl = pl;
    }

    // ================================================================== estado de cada luta

    private final class Luta {
        final Chefe c;
        final Tema tema;
        int vez, vazio;
        long proxima;
        BukkitTask tarefa;
        Cena cena;
        long imuneAte, tontoAte;
        boolean congelou;
        /** Blocos postos pela cena (voltam ao que eram no fim). */
        final Map<Block, BlockData> blocos = new HashMap<>();
        /** Blocos que somem com um clique (prisao, alvos, armadilhas). */
        final Set<Block> clicaveis = new HashSet<>();
        /** Entidades da cena (sombras, pedras caindo). */
        final List<Entity> coisas = new ArrayList<>();
        // ---- passivas (a luta toda): Bispo (sacerdotisa e copias), Lorde Vampiro (sol), Ceifador (almas)
        LivingEntity sacerdotisa;
        boolean sacerdotisaFeita, sacerdotisaMorta;
        long proximoSino;
        boolean bispoVoltou;
        final List<LivingEntity> copias = new ArrayList<>();
        long solAte;
        final Map<UUID, Alma> almas = new HashMap<>();

        Luta(Chefe c, Tema tema) {
            this.c = c;
            this.tema = tema;
        }
    }

    private abstract class Cena {
        final Luta l;
        final Chefe c;
        final World w;
        final int n;
        int t;

        Cena(Luta l) {
            this.l = l;
            this.c = l.c;
            this.w = l.c.e.getWorld();
            this.n = Math.max(1, l.c.t.nivel);
        }

        /** Um passo (a cada 0,1 s). false = acabou. */
        abstract boolean passo(List<Player> ps);

        void fim() {}

        void clicou(Player p, Block b) {}

        Particle part() {
            return Chefe.particula(l.tema.part());
        }

        Color cor() {
            return Color.fromRGB(l.tema.cor() & 0xFFFFFF);
        }

        double dano(double base) {
            return base + 2 * n;
        }

        Caixa arena() {
            return c.m.arenaLuta();
        }

        int piso() {
            return arena().y1();
        }
    }

    boolean ativo() {
        return pl.getConfig().getBoolean("mecanicas.ativo", true);
    }

    // ================================================================== ligacao com o Chefe

    /** A cada meio segundo (Chefe.tick). true = tem cena rodando (o chefe nao usa as habilidades normais agora). */
    boolean tick(Chefe c, List<Player> ps, long agora) {
        Luta l = lutas.get(c);
        if (l == null) {
            if (!ativo()) return false;
            Tema t = TEMAS.get(c.t.id);
            if (t == null) return false;
            l = new Luta(c, t);
            l.proxima = agora + pl.getConfig().getLong("mecanicas.primeira-s", 14) * 1000L;
            lutas.put(c, l);
        }
        try {
            passivas(l, ps, agora);
        } catch (Throwable ex) {
            pl.getLogger().warning("Passiva do chefe (masmorra #" + c.m.id + "): " + ex);
        }
        if (l.cena != null) return true;
        if (l.tema.mecs().length == 0 || agora < l.proxima || ps.isEmpty()) return false;
        if (c.escudoAte > 0) {   // escudo dos guardas de pe: espera ele cair
            l.proxima = agora + 3000;
            return false;
        }
        Mec mec = l.tema.mecs()[l.vez++ % l.tema.mecs().length];
        comecar(l, mec, ps);
        return l.cena != null;
    }

    /** O chefe esta escondido entre as sombras (nao mostrar as particulas dele). */
    boolean escondendo(Chefe c) {
        Luta l = lutas.get(c);
        return l != null && l.cena instanceof Sombras;
    }

    /** Mecanicas do chefe, para a mensagem de entrada na arena. */
    void anunciar(Chefe c, Player p) {
        Tema t = TEMAS.get(c.t.id);
        if (t == null || !ativo()) return;
        for (Mec m : t.mecs()) p.sendMessage(Masmorras.txt("§6§l✦ " + m.nome + ": §7" + m.desc));
        if (c.t.id.equals("CATEDRAL")) p.sendMessage(Masmorras.txt("§6§l✦ Ressurreição: §7enquanto a sacerdotisa viver, ele volta uma vez com a vida cheia e cópias."));
    }

    /** Dano no chefe: escudo (alvos), tonto (+50%), achou o verdadeiro entre as sombras. true = cancelado. */
    boolean danoNoChefe(Chefe c, EntityDamageEvent e) {
        Luta l = lutas.get(c);
        if (l == null) return false;
        long agora = System.currentTimeMillis();
        Player p = e instanceof EntityDamageByEntityEvent d ? Masmorras.jogadorDe(d.getDamager()) : null;
        EntityDamageEvent.DamageCause causa = e.getCause();
        if (l.imuneAte > agora && causa != EntityDamageEvent.DamageCause.KILL && causa != EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            if (p != null) {
                int falta = 0;
                if (l.cena instanceof Alvos a) falta = a.faltam();
                p.sendActionBar(Masmorras.txt("§b§lEscudo! §7Quebre os §f" + falta + " " + l.tema.coisa() + " §7brilhando (clique neles)."));
            }
            return true;
        }
        // Ceifador: a alma nao machuca, atrapalha (deixa o chefe lento e fraco)
        if (p != null && l.almas.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            c.e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false));
            c.e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1, false, false));
            fx(c.e.getWorld(), Particle.SOUL, c.e.getLocation().add(0, c.e.getHeight() / 2, 0), 8, 0.4, 0.5, 0.4, 0.02);
            p.sendActionBar(Masmorras.txt("§b☠ Sua alma atrapalha o chefe §7(ele fica lento e fraco)"));
            return true;
        }
        // Minotauro: no labirinto so leva dano quando esta tonto (bateu numa parede)
        if (l.cena instanceof Muralhas && l.tontoAte <= agora && causa != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
            if (p != null) p.sendActionBar(Masmorras.txt("§6§lNão adianta! §7Faça ele bater numa parede (desvie da investida)."));
            return true;
        }
        if (l.tontoAte > agora) e.setDamage(e.getDamage() * 1.5);
        // Lorde Vampiro: fora do sol leva so metade
        if (c.t.id.equals("SANGUE") && l.solAte <= agora) {
            e.setDamage(e.getDamage() * 0.5);
            if (p != null && rnd.nextInt(4) == 0) p.sendActionBar(Masmorras.txt("§4Fora do sol ele quase não sente! §7Espere a luz dos vitrais."));
        }
        if (p != null && l.cena instanceof Sombras s) s.achou(p);
        if (p != null && l.cena instanceof Engolir g) g.bateu(p);
        // Bispo (18/11, Paulo): com a sacerdotisa viva, no primeiro golpe que mataria ele volta com a vida cheia e cria
        // copias, UMA vez so. Sem chao marcado e sem "nao se mexa".
        if (c.t.id.equals("CATEDRAL") && !l.bispoVoltou && l.sacerdotisa != null && l.sacerdotisa.isValid() && !l.sacerdotisa.isDead()
                && e.getFinalDamage() >= c.e.getHealth()) {
            e.setCancelled(true);
            l.bispoVoltou = true;
            bispoVolta(l);
            return true;
        }
        return false;
    }

    /** Chefe morreu ou foi tirado. */
    void fim(Chefe c) {
        Luta l = lutas.remove(c);
        if (l != null) {
            parar(l, false);
            fimPassivas(l, c.vivo() ? false : true);
        }
    }

    void desligar() {
        for (Luta l : new ArrayList<>(lutas.values())) {
            parar(l, false);
            fimPassivas(l, true);
        }
        lutas.clear();
    }

    /** /masmorras admin mecanica <nome> [masmorra]. */
    String forcar(Chefe c, String nome) {
        // nome[:estilo|:BLOCO] (teste: lamina:1 = alta, lamina:2 = as duas, olhar:2 = nao olhe, casulo:iron_bars = cela)
        String[] pd = nome.split(":", 2);
        Mec mec;
        try {
            mec = Mec.valueOf(pd[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return "Mecanica desconhecida. Use: " + nomes();
        }
        Luta l = lutas.get(c);
        if (l != null && l.cena != null) parar(l, true);
        if (l == null || pd.length > 1) {
            Tema t = l != null ? l.tema : TEMAS.get(c.t.id);
            if (t == null) t = new Tema(new Mec[0], "CRIT", Material.AMETHYST_BLOCK, 0xFF4040, "alvos", 0);
            if (pd.length > 1) {
                Material bl = Material.matchMaterial(pd[1]);
                if (bl != null) t = new Tema(t.mecs(), t.part(), bl, t.cor(), t.coisa(), t.estilo());
                else {
                    int est;
                    try {
                        est = Integer.parseInt(pd[1]);
                    } catch (NumberFormatException ex) {
                        return "Depois do ':' vai um numero (estilo) ou um bloco.";
                    }
                    t = new Tema(t.mecs(), t.part(), t.bloco(), t.cor(), t.coisa(), est);
                }
            }
            Luta nova = new Luta(c, t);
            if (l != null) {
                nova.vez = l.vez;
                nova.proxima = l.proxima;
            }
            l = nova;
            lutas.put(c, l);
        }
        List<Player> ps = c.naArena();
        if (ps.isEmpty()) return "Ninguem na arena do chefe #" + c.m.id + ".";
        comecar(l, mec, ps);
        return l.cena != null ? "Mecanica " + mec.name() + " comecou no chefe da masmorra #" + c.m.id + "." : "A mecanica nao conseguiu comecar (sem lugar livre?).";
    }

    static String nomes() {
        StringBuilder sb = new StringBuilder();
        for (Mec m : Mec.values()) sb.append(sb.length() == 0 ? "" : ", ").append(m.name().toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    String descrever(Chefe c) {
        Luta l = lutas.get(c);
        Tema t = l != null ? l.tema : TEMAS.get(c.t.id);
        if (t == null) return "sem mecanica";
        StringBuilder sb = new StringBuilder();
        for (Mec m : t.mecs()) sb.append(sb.length() == 0 ? "" : " + ").append(m.name());
        if (l != null && l.cena != null) sb.append(" | rodando: ").append(l.cena.getClass().getSimpleName()).append(" t=").append(l.cena.t);
        if (l != null) sb.append(" | proxima em ").append(Math.max(0, (l.proxima - System.currentTimeMillis()) / 1000)).append("s");
        return sb.toString();
    }

    // ================================================================== motor das cenas

    private void comecar(Luta l, Mec mec, List<Player> ps) {
        Cena cn;
        try {
            cn = switch (mec) {
                case SOMBRAS -> new Sombras(l, ps);
                case CASULO -> new Casulo(l, ps);
                case CHAO -> new Chao(l);
                case SILENCIO -> new Silencio(l);
                case OLHAR -> new Olhar(l);
                case CHUVA -> new Chuva(l, ps);
                case INVESTIDA -> new Investida(l);
                case ANEIS -> new Aneis(l);
                case LAMINA -> new Lamina(l);
                case ALVOS -> new Alvos(l, ps);
                case MINAS -> new Minas(l, ps);
                case BOMBA -> new Bomba(l, ps);
                case REDEMOINHO -> new Redemoinho(l);
                case ELO -> new Elo(l);
                case RASTRO -> new Rastro(l);
                case NINHADA -> new Ninhada(l, ps);
                case ENGOLIR -> new Engolir(l, ps);
                case BURACO -> new Buraco(l);
                case MARE -> new Mare(l);
                case LIVRO -> new Livro(l, ps);
                case VITRAIS -> new Vitrais(l, ps);
                case MURALHAS -> new Muralhas(l);
                case GRAVIDADE -> new Gravidade(l);
                case ILHAS -> new Ilhas(l);
                case TEMPO -> new Tempo(l, ps);
            };
        } catch (Throwable ex) {
            pl.getLogger().warning("Mecanica " + mec + " (masmorra #" + l.c.m.id + ") nao comecou: " + ex);
            limpar(l);
            l.proxima = System.currentTimeMillis() + 20_000;
            return;
        }
        l.cena = cn;
        l.vazio = 0;
        l.tarefa = Bukkit.getScheduler().runTaskTimer(pl, () -> passo(l), 1L, 2L);
    }

    private void passo(Luta l) {
        Cena cn = l.cena;
        if (cn == null) {
            parar(l, true);
            return;
        }
        boolean seguir;
        try {
            List<Player> ps = l.c.vivo() ? l.c.naArena() : List.of();
            if (ps.isEmpty()) seguir = l.c.vivo() && ++l.vazio < 30;
            else {
                l.vazio = 0;
                seguir = cn.passo(ps);
            }
        } catch (Throwable ex) {
            pl.getLogger().warning("Mecanica " + cn.getClass().getSimpleName() + " (masmorra #" + l.c.m.id + ") deu erro: " + ex);
            ex.printStackTrace();
            seguir = false;
        }
        cn.t++;
        if (!seguir) parar(l, true);
    }

    /** Termina a cena e arruma tudo. depois = marca a proxima. */
    private void parar(Luta l, boolean depois) {
        if (l.tarefa != null) l.tarefa.cancel();
        l.tarefa = null;
        Cena cn = l.cena;
        l.cena = null;
        if (cn != null) {
            try {
                cn.fim();
            } catch (Throwable ex) {
                pl.getLogger().warning("Mecanica " + cn.getClass().getSimpleName() + " fim: " + ex);
            }
        }
        limpar(l);
        l.imuneAte = 0;
        if (depois) {
            long base = pl.getConfig().getLong("mecanicas.intervalo-s", 24) * 1000L;
            if (l.c.furioso) base = base * 3 / 4;
            l.proxima = System.currentTimeMillis() + base + rnd.nextInt(5000);
        }
    }

    private void limpar(Luta l) {
        for (Map.Entry<Block, BlockData> b : new ArrayList<>(l.blocos.entrySet())) {
            try {
                b.getKey().setBlockData(b.getValue(), false);
            } catch (Throwable ignored) {
            }
        }
        l.blocos.clear();
        l.clicaveis.clear();
        for (Entity x : l.coisas) if (x != null && x.isValid()) x.remove();
        l.coisas.clear();
        if (l.congelou) {
            l.congelou = false;
            if (l.c.e instanceof Mob mob && mob.isValid()) mob.setAware(true);
        }
    }

    // ================================================================== ajudantes

    private void congelar(Luta l) {
        if (l.c.e instanceof Mob mob) {
            mob.setAware(false);
            mob.setTarget(null);
            l.congelou = true;
        }
        l.c.e.setVelocity(new Vector());
    }

    private void colocar(Luta l, Block b, Material m, boolean clicavel) {
        if (!l.blocos.containsKey(b)) l.blocos.put(b, b.getBlockData());
        b.setType(m, false);
        if (clicavel) l.clicaveis.add(b);
    }

    private void tirar(Luta l, Block b) {
        BlockData o = l.blocos.remove(b);
        l.clicaveis.remove(b);
        if (o != null) b.setBlockData(o, false);
    }

    private static void fx(World w, Particle p, Location l, int q, double dx, double dy, double dz, double v) {
        if (p.getDataType() != Void.class) p = Particle.CRIT;
        w.spawnParticle(p, l, q, dx, dy, dz, v);
    }

    private static void po(World w, Location l, Color cor, float tam, int q, double esp) {
        w.spawnParticle(Particle.DUST, l, q, esp, 0.02, esp, 0, new Particle.DustOptions(cor, tam));
    }

    private static void som(World w, Location l, String s, float vol, float tom) {
        w.playSound(l, s, vol, tom);
    }

    private static void titulo(Player p, String a, String b, int ms) {
        p.showTitle(Title.title(Masmorras.txt(a), Masmorras.txt(b), Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(ms), Duration.ofMillis(300))));
    }

    private static double horiz(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** No ar (pulando) ou em cima de alguma coisa. */
    @SuppressWarnings("deprecation")
    private static boolean noAr(Player p, int piso) {
        return !p.isOnGround() || p.getY() - piso > 0.45;
    }

    private static boolean vazio(Block b) {
        return b.getType().isAir();
    }

    /** Bloco de chao livre na arena (ar nos pes e na cabeca, chao firme embaixo, ninguem em cima). */
    private Block chaoLivre(Luta l, Location perto, int raio, double longeDoChefe) {
        Caixa a = l.c.m.arenaLuta();
        World w = l.c.e.getWorld();
        Location ch = l.c.e.getLocation();
        for (int tent = 0; tent < 30; tent++) {
            int x, z;
            if (perto != null && tent < 20) {
                x = perto.getBlockX() + rnd.nextInt(raio * 2 + 1) - raio;
                z = perto.getBlockZ() + rnd.nextInt(raio * 2 + 1) - raio;
            } else {
                x = a.x1() + 1 + rnd.nextInt(Math.max(1, a.x2() - a.x1() - 1));
                z = a.z1() + 1 + rnd.nextInt(Math.max(1, a.z2() - a.z1() - 1));
            }
            if (x <= a.x1() || x >= a.x2() || z <= a.z1() || z >= a.z2()) continue;
            Block b = w.getBlockAt(x, a.y1(), z);
            if (!vazio(b) || !vazio(b.getRelative(BlockFace.UP)) || !b.getRelative(BlockFace.DOWN).getType().isSolid() || l.blocos.containsKey(b)) continue;
            Location bl = b.getLocation().add(0.5, 0, 0.5);
            if (horiz(bl, ch) < longeDoChefe) continue;
            boolean gente = false;
            for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(bl) < 2.2) gente = true;
            if (gente) continue;
            return b;
        }
        return null;
    }

    private Luta lutaDoBloco(Block b) {
        for (Luta l : lutas.values()) if (l.clicaveis.contains(b)) return l;
        return null;
    }

    // ================================================================== eventos

    @EventHandler(priority = EventPriority.HIGH)
    public void clique(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Luta l = lutaDoBloco(e.getClickedBlock());
        if (l == null) return;
        e.setCancelled(true);
        if (l.cena != null) l.cena.clicou(e.getPlayer(), e.getClickedBlock());
        else tirar(l, e.getClickedBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void quebrar(BlockBreakEvent e) {
        Luta l = lutaDoBloco(e.getBlock());
        if (l == null) {
            for (Luta x : lutas.values()) if (x.blocos.containsKey(e.getBlock())) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (l.cena != null) l.cena.clicou(e.getPlayer(), e.getBlock());
        else tirar(l, e.getBlock());
    }

    @EventHandler
    public void flecha(ProjectileHitEvent e) {
        if (e.getHitBlock() == null || !(e.getEntity().getShooter() instanceof Player p)) return;
        Luta l = lutaDoBloco(e.getHitBlock());
        if (l == null) return;
        if (l.cena != null) l.cena.clicou(p, e.getHitBlock());
        e.getEntity().remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void pedraCaiu(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock fb && fb.getPersistentDataContainer().has(K_PEDRA)) {
            e.setCancelled(true);
            fb.remove();
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void danoSombra(EntityDamageEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(K_SOMBRA)) return;
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent d)) return;
        Player p = Masmorras.jogadorDe(d.getDamager());
        if (p == null) return;
        boolean castigo = d.getDamager() instanceof Player
                || (d.getDamager() instanceof Projectile pr && pr.getShooter() == p && !pr.getPersistentDataContainer().has(K_BALA_TORRE));
        for (Luta l : lutas.values()) {
            if (l.cena instanceof Sombras s && s.sombras.contains(e.getEntity())) {
                s.estourar((LivingEntity) e.getEntity(), p, castigo);
                return;
            }
        }
        e.getEntity().remove();   // sombra perdida (a cena ja acabou)
    }

    // ================================================================== SOMBRAS: copias iguais, ache o verdadeiro

    private final class Sombras extends Cena {
        final List<LivingEntity> sombras = new ArrayList<>();
        boolean achou;

        Sombras(Luta l, List<Player> ps) {
            super(l);
            int q = c.furioso ? 4 : 3;
            Location base = c.e.getLocation();
            for (int i = 0; i < q; i++) {
                Location ok = c.lugarLivre(base.clone().add(rnd.nextInt(13) - 6, 0, rnd.nextInt(13) - 6));
                if (ok == null) continue;
                LivingEntity s = pl.lacaio(c.m, c.t.chefe, ok, true);
                if (s == null) continue;
                vestir(s);
                sombras.add(s);
                l.coisas.add(s);
            }
            if (sombras.isEmpty()) throw new IllegalStateException("sem lugar para as sombras");
            // o chefe troca de lugar com uma das sombras: ninguem sabe qual e qual
            LivingEntity troca = sombras.get(rnd.nextInt(sombras.size()));
            Location a = c.e.getLocation().clone(), b = troca.getLocation().clone();
            c.e.teleport(b);
            troca.teleport(a);
            fx(w, Particle.LARGE_SMOKE, a.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);
            for (LivingEntity s : sombras) fx(w, Particle.LARGE_SMOKE, s.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);
            fx(w, Particle.LARGE_SMOKE, c.e.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);
            som(w, a, "minecraft:entity.illusioner.mirror_move", 1.4f, 0.7f);
            for (Player p : ps) titulo(p, "§8§lSOMBRAS!", "§7Ache o §fverdadeiro§7 — bater numa sombra cega você", 2200);
        }

        void vestir(LivingEntity s) {
            s.getPersistentDataContainer().set(K_SOMBRA, PersistentDataType.BYTE, (byte) 1);
            if (K_SEM_NOME_LM != null) s.getPersistentDataContainer().set(K_SEM_NOME_LM, PersistentDataType.STRING, "masmorras");
            s.customName(c.e.customName());
            s.setCustomNameVisible(c.e.isCustomNameVisible());
            s.setGlowing(c.e.isGlowing());
            for (Attribute at : new Attribute[] {Attribute.SCALE, Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED}) {
                AttributeInstance de = c.e.getAttribute(at), para = s.getAttribute(at);
                if (de != null && para != null) para.setBaseValue(de.getBaseValue());
            }
            AttributeInstance d = s.getAttribute(Attribute.ATTACK_DAMAGE);
            if (d != null) d.setBaseValue(1);
            try {
                s.setHealth(Math.min(s.getAttribute(Attribute.MAX_HEALTH).getValue(), c.e.getHealth()));
            } catch (Throwable ignored) {
            }
            EntityEquipment de = c.e.getEquipment(), para = s.getEquipment();
            if (de != null && para != null) {
                para.setItemInMainHand(de.getItemInMainHand());
                para.setItemInOffHand(de.getItemInOffHand());
                para.setHelmet(de.getHelmet());
                para.setChestplate(de.getChestplate());
                para.setLeggings(de.getLeggings());
                para.setBoots(de.getBoots());
                para.setItemInMainHandDropChance(0f);
                para.setItemInOffHandDropChance(0f);
                para.setHelmetDropChance(0f);
                para.setChestplateDropChance(0f);
                para.setLeggingsDropChance(0f);
                para.setBootsDropChance(0f);
            }
            for (PotionEffect ef : c.e.getActivePotionEffects()) s.addPotionEffect(ef);
            if (c.e instanceof org.bukkit.entity.Wolf a && s instanceof org.bukkit.entity.Wolf b) b.setAngry(a.isAngry());
            if (c.e instanceof org.bukkit.entity.Slime a && s instanceof org.bukkit.entity.Slime b) b.setSize(a.getSize());
            s.setRemoveWhenFarAway(false);
        }

        @Override
        boolean passo(List<Player> ps) {
            sombras.removeIf(s -> !s.isValid());
            if (achou || sombras.isEmpty()) return false;
            if (t % 10 == 0) {
                fx(w, Particle.SMOKE, c.e.getLocation().add(0, c.e.getHeight() * 0.6, 0), 6, 0.3, 0.4, 0.3, 0.01);
                for (LivingEntity s : sombras) fx(w, Particle.SMOKE, s.getLocation().add(0, s.getHeight() * 0.6, 0), 6, 0.3, 0.4, 0.3, 0.01);
            }
            if (t >= 200) {
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§7As sombras sumiram."));
                return false;
            }
            return true;
        }

        void estourar(LivingEntity s, Player p, boolean castigo) {
            if (!sombras.remove(s)) return;
            fx(w, Particle.LARGE_SMOKE, s.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.03);
            som(w, s.getLocation(), "minecraft:entity.illusioner.cast_spell", 1f, 1.4f);
            s.remove();
            if (castigo) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
                p.damage(dano(1), c.e);
                p.sendActionBar(Masmorras.txt("§8§lEra uma sombra! §7Faltam §f" + sombras.size()));
            }
        }

        void achou(Player p) {
            if (achou) return;
            achou = true;
            for (LivingEntity s : sombras) {
                fx(w, Particle.LARGE_SMOKE, s.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.03);
                s.remove();
            }
            sombras.clear();
            som(w, c.e.getLocation(), "minecraft:entity.player.levelup", 1f, 1.6f);
            for (Player x : c.naArena()) x.sendActionBar(Masmorras.txt("§a§l" + p.getName() + " achou o verdadeiro! §7As sombras sumiram."));
        }
    }

    // ================================================================== CASULO: teia (pes e cabeca) ou cela de grades

    private final class Casulo extends Cena {
        final List<Player> presos = new ArrayList<>();
        final List<List<Block>> prisoes = new ArrayList<>();
        final List<Location> lugares = new ArrayList<>();
        final List<Boolean> soltos = new ArrayList<>();
        final boolean cela;
        final int prazo;

        Casulo(Luta l, List<Player> ps) {
            super(l);
            // teia (nos pes e na cabeca) ou cela de grades em volta; nunca bloco cheio (sufocaria o preso)
            cela = l.tema.bloco() == Material.IRON_BARS;
            Material bl = cela ? Material.IRON_BARS : Material.COBWEB;
            prazo = cela ? 60 : 50;
            List<Player> alvos = new ArrayList<>(ps);
            java.util.Collections.shuffle(alvos, rnd);
            int q = Math.min(alvos.size(), c.furioso && alvos.size() > 1 ? 2 : 1);
            for (Player p : alvos) {
                if (presos.size() >= q) break;
                Block pe = p.getLocation().getBlock(), cab = pe.getRelative(BlockFace.UP);
                List<Block> bs = new ArrayList<>();
                if (cela) {
                    if (!vazio(cab)) continue;
                    for (BlockFace f : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                        for (int y = 0; y < 2; y++) {
                            Block b = pe.getRelative(f).getRelative(0, y, 0);
                            if (vazio(b)) bs.add(b);
                        }
                    }
                    Block topo = cab.getRelative(BlockFace.UP);
                    if (vazio(topo)) bs.add(topo);
                } else {
                    if (vazio(pe)) bs.add(pe);
                    if (vazio(cab)) bs.add(cab);
                }
                if (bs.isEmpty()) continue;
                // centraliza o preso no bloco (a cela fecha em volta dele)
                Location centro = pe.getLocation().add(0.5, 0, 0.5);
                centro.setYaw(p.getLocation().getYaw());
                centro.setPitch(p.getLocation().getPitch());
                p.teleport(centro);
                p.setVelocity(new Vector());
                for (Block b : bs) colocar(l, b, bl, true);
                presos.add(p);
                prisoes.add(bs);
                lugares.add(centro);
                soltos.add(false);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, prazo + 10, cela ? 1 : 3));
                titulo(p, cela ? "§7§lNA CELA!" : "§f§lPRESO NA TEIA!", "§7Clique " + (cela ? "nas grades" : "na teia") + " para se soltar!", 2000);
                som(w, centro, cela ? "minecraft:block.iron_door.close" : "minecraft:entity.spider.ambient", 1.2f, 0.6f);
            }
            if (presos.isEmpty()) throw new IllegalStateException("ninguem para prender");
            for (Player p : ps) {
                if (presos.contains(p)) continue;
                p.sendActionBar(Masmorras.txt("§e§lSolte " + presos.get(0).getName() + "! §7Clique " + (cela ? "nas grades" : "na teia") + "."));
            }
        }

        @Override
        void clicou(Player p, Block b) {
            for (int i = 0; i < prisoes.size(); i++) {
                List<Block> bs = prisoes.get(i);
                if (!bs.contains(b)) continue;
                BlockData d = b.getBlockData();
                tirar(l, b);
                w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0.1, d);
                som(w, b.getLocation(), cela ? "minecraft:block.chain.break" : "minecraft:block.wool.break", 1f, 1.2f);
                boolean solto;
                if (cela) solto = true;   // uma grade aberta = porta da cela
                else {
                    solto = true;
                    for (Block x : bs) if (l.clicaveis.contains(x)) solto = false;
                }
                if (solto && !soltos.get(i)) {
                    soltos.set(i, true);
                    Player preso = presos.get(i);
                    preso.removePotionEffect(PotionEffectType.SLOWNESS);
                    preso.sendActionBar(Masmorras.txt("§a§lLivre!"));
                    if (!preso.equals(p)) p.sendActionBar(Masmorras.txt("§a§lVocê soltou " + preso.getName() + "!"));
                    // a cela inteira cai
                    if (cela) for (Block x : new ArrayList<>(bs)) tirar(l, x);
                }
                return;
            }
        }

        @Override
        boolean passo(List<Player> ps) {
            boolean algum = false;
            for (int i = 0; i < presos.size(); i++) {
                if (soltos.get(i)) continue;
                algum = true;
                Location lc = lugares.get(i);
                if (t % 4 == 0) fx(w, part(), lc.clone().add(0, 1, 0), 3, 0.4, 0.6, 0.4, 0.01);
                if (t % 10 == 0) {
                    int falta = (prazo - t) / 10;
                    presos.get(i).sendActionBar(Masmorras.txt("§c§l" + Math.max(0, falta) + "s §7— clique " + (cela ? "nas grades" : "na teia") + " para se soltar!"));
                }
            }
            if (!algum) return false;
            if (t < prazo) return true;
            for (int i = 0; i < presos.size(); i++) {
                if (soltos.get(i)) continue;
                Player p = presos.get(i);
                Location lc = lugares.get(i);
                for (Block b : new ArrayList<>(prisoes.get(i))) tirar(l, b);
                if (cela) {
                    if (p.isOnline() && p.getLocation().distanceSquared(lc) < 9) {
                        p.damage(dano(4), c.e);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                    }
                    fx(w, Particle.SCULK_SOUL, lc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.03);
                    som(w, lc, "minecraft:entity.warden.attack_impact", 1.3f, 0.6f);
                    for (Player x : ps) x.sendActionBar(Masmorras.txt("§4§lA cela fechou em " + p.getName() + "!"));
                } else {
                    for (int k = 0; k < 2; k++) {
                        Location ok = c.lugarLivre(lc.clone().add(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1));
                        if (ok != null) pl.lacaio(c.m, "CAVE_SPIDER", ok, true);
                    }
                    if (p.isOnline() && p.getLocation().distanceSquared(lc) < 9) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                        p.damage(dano(1), c.e);
                    }
                    fx(w, Particle.ITEM_SLIME, lc.clone().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.05);
                    som(w, lc, "minecraft:entity.spider.death", 1.2f, 1.6f);
                    for (Player x : ps) x.sendActionBar(Masmorras.txt("§c§lO casulo chocou! §7Aranhas!"));
                }
            }
            return false;
        }

        @Override
        void fim() {
            for (Player p : presos) if (p.isOnline()) p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    // ================================================================== CHAO: marcas vermelhas em padroes, depois explode

    private final class Chao extends Cena {
        static final int LADO = 3, AVISO = 22, ONDA = 28;
        final int ondas, x0, z0, nx, nz;
        boolean[][] marca;
        int onda = -1, ultimoPadrao = -1;

        Chao(Luta l) {
            super(l);
            Caixa a = arena();
            x0 = a.x1();
            z0 = a.z1();
            nx = (a.x2() - a.x1()) / LADO + 1;
            nz = (a.z2() - a.z1()) / LADO + 1;
            ondas = c.furioso ? 4 : 3;
            for (Player p : c.naArena()) titulo(p, "§c§lCHÃO MARCADO!", "§7Saia das marcas §cvermelhas§7 antes de explodirem", 1800);
        }

        boolean[][] padrao() {
            int tipo;
            do tipo = rnd.nextInt(5); while (tipo == ultimoPadrao);
            ultimoPadrao = tipo;
            int k = rnd.nextInt(2);
            boolean[][] m = new boolean[nx][nz];
            double cx = (nx - 1) / 2.0, cz = (nz - 1) / 2.0, r = Math.min(nx, nz) / 3.0;
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < nz; j++) {
                    m[i][j] = switch (tipo) {
                        case 0 -> (i + j) % 2 == k;
                        case 1 -> i % 2 == k;
                        case 2 -> j % 2 == k;
                        case 3 -> (Math.hypot(i - cx, j - cz) > r) == (k == 0);
                        default -> (Math.abs(i - cx) <= 0.6 || Math.abs(j - cz) <= 0.6) == (k == 0);
                    };
                }
            }
            return m;
        }

        boolean marcado(double x, double z) {
            int i = (int) Math.floor((x - x0) / LADO), j = (int) Math.floor((z - z0) / LADO);
            return i >= 0 && j >= 0 && i < nx && j < nz && marca[i][j];
        }

        @Override
        boolean passo(List<Player> ps) {
            int f = t % ONDA;
            if (f == 0) {
                onda++;
                if (onda >= ondas) return false;
                marca = padrao();
                som(w, c.e.getLocation(), "minecraft:block.respawn_anchor.charge", 1.2f, 0.7f + onda * 0.15f);
            }
            int y = piso();
            Caixa a = arena();
            if (f < AVISO && f % 2 == 0) {
                Color cor = f >= AVISO - 8 ? Color.fromRGB(255, 120, 0) : Color.RED;
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < nz; j++) {
                        if (!marca[i][j]) continue;
                        double bx = x0 + i * LADO, bz = z0 + j * LADO;
                        for (int k = 0; k < 2; k++) {
                            double px = bx + rnd.nextDouble() * LADO, pz = bz + rnd.nextDouble() * LADO;
                            if (px > a.x2() + 1 || pz > a.z2() + 1) continue;
                            po(w, new Location(w, px, y + 0.12, pz), cor, 1.4f, 1, 0);
                        }
                        if (f % 6 == 0) fx(w, part(), new Location(w, bx + 1.5, y + 0.3, bz + 1.5), 1, 0.6, 0.1, 0.6, 0.01);
                    }
                }
            }
            if (f == AVISO) {
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < nz; j++) {
                        if (!marca[i][j]) continue;
                        Location lc = new Location(w, x0 + i * LADO + 1.5, y + 0.4, z0 + j * LADO + 1.5);
                        fx(w, part(), lc, 5, 1.0, 0.3, 1.0, 0.05);
                        if (rnd.nextInt(3) == 0) fx(w, Particle.EXPLOSION, lc, 1, 0.5, 0.1, 0.5, 0);
                    }
                }
                som(w, c.e.getLocation(), "minecraft:entity.generic.explode", 1.2f, 0.8f);
                for (Player p : ps) {
                    if (p.getY() - y > 2.5 || !marcado(p.getX(), p.getZ())) continue;
                    p.damage(dano(3), c.e);
                    p.setVelocity(p.getVelocity().setY(0.45));
                }
            }
            return true;
        }
    }

    // ================================================================== SILENCIO: nao se mexa

    private final class Silencio extends Cena {
        static final int AVISO = 25, FIM = 55;
        final Map<UUID, Location> parado = new HashMap<>();
        final Set<UUID> pegos = new HashSet<>();

        Silencio(Luta l) {
            super(l);
            congelar(l);
            for (Player p : c.naArena()) titulo(p, "§5§lSILÊNCIO...", "§7Quando ele mandar, §fNÃO SE MEXA§7!", 2000);
            som(w, c.e.getLocation(), "minecraft:entity.warden.sniff", 1.5f, 0.8f);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t < AVISO) {
                if (t == 8 || t == 15 || t == 22) for (Player p : ps) {
                    p.playSound(p.getLocation(), "minecraft:block.note_block.hat", 1f, 0.8f + t / 30f);
                    p.sendActionBar(Masmorras.txt("§5§l" + (t == 8 ? "3" : t == 15 ? "2" : "1") + "..."));
                }
                return true;
            }
            if (t == AVISO) {
                for (Player p : ps) {
                    titulo(p, "§4§lNÃO SE MEXA!", "", 2600);
                    parado.put(p.getUniqueId(), p.getLocation());
                }
                som(w, c.e.getLocation(), "minecraft:entity.warden.listening_angry", 1.6f, 0.8f);
                return true;
            }
            if (t < FIM) {
                if (t % 3 == 0) fx(w, Particle.SCULK_SOUL, c.e.getLocation().add(0, c.e.getHeight(), 0), 3, 0.4, 0.3, 0.4, 0.02);
                for (Player p : ps) {
                    Location a = parado.computeIfAbsent(p.getUniqueId(), k -> p.getLocation());
                    if (pegos.contains(p.getUniqueId()) || t < AVISO + 3) continue;
                    if (horiz(a, p.getLocation()) > 0.75) {
                        pegos.add(p.getUniqueId());
                        p.damage(dano(4), c.e);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
                        Location de = c.e.getEyeLocation(), para = p.getEyeLocation();
                        Vector v = para.toVector().subtract(de.toVector());
                        double d = v.length();
                        v.normalize();
                        for (double s = 1; s < d; s += 1.2) w.spawnParticle(Particle.SONIC_BOOM, de.clone().add(v.clone().multiply(s)), 1, 0, 0, 0, 0);
                        som(w, p.getLocation(), "minecraft:entity.warden.sonic_boom", 1.4f, 1f);
                        p.sendActionBar(Masmorras.txt("§4§lVocê se mexeu!"));
                    }
                }
                return true;
            }
            for (Player p : ps) {
                if (!pegos.contains(p.getUniqueId())) titulo(p, "§a§lPode se mexer", "", 900);
            }
            return false;
        }
    }

    // ================================================================== OLHAR: olhe / nao olhe para ele

    private final class Olhar extends Cena {
        static final int PRAZO = 32;
        final boolean olhe;

        Olhar(Luta l) {
            super(l);
            int e = l.tema.estilo();
            olhe = e == 1 || (e == 0 && rnd.nextBoolean());
            congelar(l);
            for (Player p : c.naArena()) titulo(p, olhe ? "§e§lOLHE PARA MIM!" : "§c§lNÃO OLHE PARA MIM!", "§7em 3 segundos...", 2600);
            som(w, c.e.getLocation(), olhe ? "minecraft:entity.elder_guardian.curse" : "minecraft:entity.enderman.stare", 1.2f, 1f);
        }

        boolean olhando(Player p) {
            Vector para = c.e.getLocation().add(0, c.e.getHeight() * 0.6, 0).toVector().subtract(p.getEyeLocation().toVector());
            if (para.lengthSquared() < 0.01) return true;
            return p.getEyeLocation().getDirection().dot(para.normalize()) > 0.72;
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t % 3 == 0) fx(w, olhe ? Particle.END_ROD : Particle.SMOKE, c.e.getEyeLocation(), 4, 0.3, 0.2, 0.3, 0.02);
            if (t == 10 || t == 20) for (Player p : ps) {
                p.playSound(p.getLocation(), "minecraft:block.note_block.bell", 1f, t == 10 ? 1f : 1.3f);
                p.sendActionBar(Masmorras.txt((olhe ? "§e§lOLHE" : "§c§lNÃO OLHE") + " §7— " + (t == 10 ? "2" : "1") + "..."));
            }
            if (t < PRAZO) return true;
            for (Player p : ps) {
                boolean v = olhando(p);
                if (v == olhe) {
                    p.sendActionBar(Masmorras.txt("§a§lBoa!"));
                    continue;
                }
                p.damage(dano(3), c.e);
                if (olhe) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                }
                fx(w, part(), p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                p.sendActionBar(Masmorras.txt(olhe ? "§c§lVocê não olhou!" : "§c§lVocê olhou!"));
            }
            som(w, c.e.getLocation(), "minecraft:entity.evoker.cast_spell", 1.2f, 0.7f);
            return false;
        }
    }

    // ================================================================== CHUVA: coisas caem do teto

    private final class Chuva extends Cena {
        final class Gota {
            final Location chao;
            final int nasce;
            FallingBlock fb;
            Location ultimo;
            boolean caiu;

            Gota(Location chao, int nasce) {
                this.chao = chao;
                this.nasce = nasce;
            }
        }

        static final int AVISO = 13;
        final List<Gota> gotas = new ArrayList<>();
        final int total;
        final BlockData dado;

        Chuva(Luta l, List<Player> ps) {
            super(l);
            total = Math.min(16, 6 + 2 * ps.size() + (c.furioso ? 3 : 0));
            Material m = l.tema.bloco() != null && l.tema.bloco().isBlock() && l.tema.bloco().isSolid() ? l.tema.bloco() : Material.COBBLESTONE;
            dado = m.createBlockData();
            for (Player p : ps) titulo(p, "§6§lCUIDADO COM O TETO!", "§7Fuja dos círculos §cvermelhos", 1600);
            som(w, c.e.getLocation(), "minecraft:block.pointed_dripstone.drip_lava_into_cauldron", 1.5f, 0.5f);
        }

        Location alvo(List<Player> ps) {
            Caixa a = arena();
            double x, z;
            if (!ps.isEmpty() && rnd.nextInt(10) < 6) {
                Player p = ps.get(rnd.nextInt(ps.size()));
                x = p.getX() + rnd.nextGaussian() * 1.2;
                z = p.getZ() + rnd.nextGaussian() * 1.2;
            } else {
                x = a.x1() + 1 + rnd.nextDouble() * Math.max(1, a.x2() - a.x1() - 1);
                z = a.z1() + 1 + rnd.nextDouble() * Math.max(1, a.z2() - a.z1() - 1);
            }
            x = Math.max(a.x1() + 0.6, Math.min(a.x2() + 0.4, x));
            z = Math.max(a.z1() + 0.6, Math.min(a.z2() + 0.4, z));
            return new Location(w, Math.floor(x) + 0.5, piso(), Math.floor(z) + 0.5);
        }

        void impacto(Gota g, Location onde, List<Player> ps) {
            g.caiu = true;
            if (g.fb != null && g.fb.isValid()) g.fb.remove();
            Location lc = g.chao.clone().add(0, 0.3, 0);
            w.spawnParticle(Particle.BLOCK, lc, 30, 0.5, 0.3, 0.5, 0.1, dado);
            fx(w, part(), lc, 8, 0.6, 0.3, 0.6, 0.05);
            som(w, lc, "minecraft:block.anvil.land", 0.7f, 0.6f);
            for (Player p : ps) {
                if (horiz(p.getLocation(), g.chao) > 1.6 || Math.abs(p.getY() - g.chao.getY()) > 2.5) continue;
                p.damage(dano(3), c.e);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
            }
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t % 3 == 0 && gotas.size() < total && t > 5) gotas.add(new Gota(alvo(ps), t));
            boolean faltando = gotas.size() < total;
            Caixa a = arena();
            for (Gota g : gotas) {
                if (g.caiu) continue;
                faltando = true;
                int idade = t - g.nasce;
                if (t % 2 == 0) {
                    for (int i = 0; i < 12; i++) {
                        double an = i * Math.PI / 6;
                        po(w, g.chao.clone().add(Math.cos(an) * 1.2, 0.1, Math.sin(an) * 1.2), Color.RED, 1.2f, 1, 0);
                    }
                    po(w, g.chao.clone().add(0, 0.1, 0), Color.fromRGB(255, 80, 0), 1.6f, 2, 0.3);
                }
                if (idade == AVISO) {
                    int topo = Math.min(a.y2() - 1, piso() + 8);
                    int y = topo;
                    while (y > piso() + 2 && !w.getBlockAt(g.chao.getBlockX(), y, g.chao.getBlockZ()).isPassable()) y--;
                    Location de = new Location(w, g.chao.getX(), y, g.chao.getZ());
                    @SuppressWarnings("deprecation")
                    FallingBlock x = w.spawnFallingBlock(de, dado);
                    x.getPersistentDataContainer().set(K_PEDRA, PersistentDataType.BYTE, (byte) 1);
                    x.setDropItem(false);
                    x.setHurtEntities(false);
                    x.setCancelDrop(true);
                    x.setPersistent(false);
                    g.fb = x;
                    g.ultimo = de;
                    l.coisas.add(g.fb);
                } else if (idade > AVISO) {
                    if (g.fb == null || !g.fb.isValid() || g.fb.isOnGround() || g.fb.getY() <= g.chao.getY() + 0.6 || idade > AVISO + 40) {
                        impacto(g, g.fb != null && g.fb.isValid() ? g.fb.getLocation() : g.ultimo, ps);
                    } else g.ultimo = g.fb.getLocation();
                }
            }
            return faltando;
        }
    }

    // ================================================================== INVESTIDA: mira, dispara, bate na parede e fica tonto

    private final class Investida extends Cena {
        static final int MIRA = 15;
        final int vezes;
        int feitas, fase, inicio, pausaAte;
        Vector dir;
        Location pos;
        double andou;
        final Set<UUID> atingidos = new HashSet<>();

        Investida(Luta l) {
            super(l);
            vezes = c.furioso ? 3 : 2;
            congelar(l);
        }

        /** O corpo do chefe cabe aqui (confere o meio e a frente, pela largura dele). */
        boolean livre(Location p) {
            double meia = Math.max(0.3, c.e.getWidth() / 2);
            Location frente = dir == null ? p : p.clone().add(dir.clone().multiply(meia));
            return cabe(p) && cabe(frente);
        }

        boolean cabe(Location p) {
            Caixa a = arena();
            if (!a.contem(p.getX(), p.getY() + 0.1, p.getZ())) return false;
            Block b = w.getBlockAt(p.getBlockX(), (int) Math.floor(p.getY() + 0.1), p.getBlockZ());
            return b.isPassable() && b.getRelative(BlockFace.UP).isPassable();
        }

        @Override
        boolean passo(List<Player> ps) {
            if (fase == 0) {
                if (t == inicio) {
                    Player alvo = ps.get(rnd.nextInt(ps.size()));
                    pos = c.e.getLocation().clone();
                    dir = alvo.getLocation().toVector().subtract(pos.toVector()).setY(0);
                    if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                    dir.normalize();
                    pos.setDirection(dir);
                    som(w, pos, "minecraft:entity.ravager.roar", 1.5f, 0.8f);
                    for (Player p : ps) p.sendActionBar(Masmorras.txt("§c§lINVESTIDA! §7Saia da linha vermelha!"));
                }
                c.e.teleport(pos);
                if (t % 2 == 0) {
                    Location q = pos.clone();
                    for (int i = 0; i < 40; i++) {
                        q.add(dir.clone().multiply(0.7));
                        if (!livre(q)) break;
                        po(w, q.clone().add(0, 0.15, 0), Color.RED, 1.3f, 1, 0.05);
                    }
                }
                if (t - inicio >= MIRA) {
                    fase = 1;
                    andou = 0;
                    atingidos.clear();
                    som(w, pos, "minecraft:entity.ravager.attack", 1.4f, 0.7f);
                }
                return true;
            }
            if (fase == 1) {
                Location ultimo = pos.clone();
                boolean bateu = false;
                for (int k = 0; k < 3; k++) {
                    Location prox = ultimo.clone().add(dir.clone().multiply(0.55));
                    if (!livre(prox)) {
                        bateu = true;
                        break;
                    }
                    ultimo = prox;
                }
                andou += ultimo.distance(pos);
                pos = ultimo;
                c.e.teleport(pos);
                c.e.setVelocity(new Vector());
                fx(w, Particle.CLOUD, pos.clone().add(0, 0.2, 0), 3, 0.3, 0.1, 0.3, 0.02);
                for (Player p : ps) {
                    if (atingidos.contains(p.getUniqueId())) continue;
                    if (horiz(p.getLocation(), pos) > 1.2 + c.e.getWidth() / 2 || Math.abs(p.getY() - pos.getY()) > 2.2) continue;
                    atingidos.add(p.getUniqueId());
                    p.damage(dano(5), c.e);
                    Vector lado = new Vector(-dir.getZ(), 0, dir.getX());
                    if (lado.dot(p.getLocation().toVector().subtract(pos.toVector())) < 0) lado.multiply(-1);
                    p.setVelocity(lado.multiply(0.9).add(dir.clone().multiply(0.4)).setY(0.5));
                }
                if (bateu) {
                    if (pl.getConfig().getBoolean("mecanicas.debug", false))
                        pl.getLogger().info("Investida bateu: pos=" + pos.getX() + "," + pos.getZ() + " chefe=" + c.e.getX() + "," + c.e.getZ());
                    l.tontoAte = System.currentTimeMillis() + 3500;
                    c.e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 6, false, false));
                    c.e.setGlowing(true);
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        if (c.e.isValid()) c.e.setGlowing(false);
                    }, 70L);
                    fx(w, Particle.EXPLOSION, pos.clone().add(dir.clone().multiply(0.6)).add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                    som(w, pos, "minecraft:entity.zombie.attack_iron_door", 1.6f, 0.6f);
                    som(w, pos, "minecraft:block.anvil.land", 1f, 0.5f);
                    for (Player p : ps) titulo(p, "", "§e§lTONTO! §7Bata nele agora (+50% de dano)", 1800);
                    fase = 2;
                    pausaAte = t + 35;
                } else if (andou > 32) {
                    fase = 2;
                    pausaAte = t + 8;
                }
                return true;
            }
            // fase 2: tonto ou parado um pouco (segura no ultimo lugar valido)
            if (pos != null && c.e.getLocation().distanceSquared(pos) > 0.04) c.e.teleport(pos);
            c.e.setVelocity(new Vector());
            if (l.tontoAte > System.currentTimeMillis() && t % 3 == 0)
                fx(w, Particle.CRIT, c.e.getLocation().add(0, c.e.getHeight() + 0.2, 0), 4, 0.3, 0.1, 0.3, 0.02);
            if (t < pausaAte) return true;
            feitas++;
            if (feitas >= vezes) return false;
            fase = 0;
            inicio = t + 1;
            return true;
        }
    }

    // ================================================================== ANEIS: ondas no chao, pule

    private final class Aneis extends Cena {
        final class Anel {
            final Location centro;
            final double vel;
            double r = 0.5, antes = 0.5;
            final Set<UUID> atingidos = new HashSet<>();

            Anel(Location centro, double vel) {
                this.centro = centro;
                this.vel = vel;
            }
        }

        final List<Anel> aneis = new ArrayList<>();
        final int ondas;
        final double rMax;
        int criados;

        Aneis(Luta l) {
            super(l);
            congelar(l);
            ondas = c.furioso ? 5 : 4;
            Caixa a = arena();
            rMax = Math.hypot(a.x2() - a.x1() + 1, a.z2() - a.z1() + 1) / 2 + 3;
            for (Player p : c.naArena()) titulo(p, "§6§lPULE AS ONDAS!", "§7Pule quando a onda chegar em você", 1600);
            som(w, c.e.getLocation(), "minecraft:entity.warden.roar", 1.2f, 1.2f);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t >= 12 && (t - 12) % 14 == 0 && criados < ondas) {
                double vel = 0.42 + 0.06 * criados + (c.furioso ? 0.05 : 0);
                Location ct = c.e.getLocation().clone();
                ct.setY(piso());
                aneis.add(new Anel(ct, vel));
                criados++;
                som(w, ct, "minecraft:entity.warden.attack_impact", 1.4f, 0.6f);
                fx(w, Particle.EXPLOSION, ct.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
            }
            Iterator<Anel> it = aneis.iterator();
            while (it.hasNext()) {
                Anel an = it.next();
                an.antes = an.r;
                an.r += an.vel;
                if (an.r > rMax) {
                    it.remove();
                    continue;
                }
                int pontos = (int) Math.min(110, Math.max(16, an.r * 2 * Math.PI * 1.6));
                for (int i = 0; i < pontos; i++) {
                    double a = i * 2 * Math.PI / pontos;
                    Location q = an.centro.clone().add(Math.cos(a) * an.r, 0.25, Math.sin(a) * an.r);
                    if (i % 2 == 0) po(w, q, cor(), 1.3f, 1, 0);
                    else fx(w, part(), q, 1, 0, 0, 0, 0);
                }
                for (Player p : ps) {
                    if (an.atingidos.contains(p.getUniqueId())) continue;
                    double d = horiz(p.getLocation(), an.centro);
                    if (d < an.antes - 0.4 || d > an.r + 0.4 || Math.abs(p.getY() - an.centro.getY()) > 1.5) continue;
                    if (noAr(p, piso())) continue;
                    an.atingidos.add(p.getUniqueId());
                    p.damage(dano(3), c.e);
                    Vector fora = p.getLocation().toVector().subtract(an.centro.toVector()).setY(0);
                    if (fora.lengthSquared() < 0.01) fora = new Vector(1, 0, 0);
                    p.setVelocity(fora.normalize().multiply(0.4).setY(0.45));
                }
            }
            return criados < ondas || !aneis.isEmpty();
        }
    }

    // ================================================================== LAMINA: gira pela arena (baixa: pule / alta: agache)

    private final class Lamina extends Cena {
        final class Braco {
            double ang, antes;
            final double vel;
            final boolean alta;
            final Map<UUID, Integer> cd = new HashMap<>();

            Braco(double ang, double vel, boolean alta) {
                this.ang = ang;
                this.antes = ang;
                this.vel = vel;
                this.alta = alta;
            }
        }

        static final int AVISO = 16;
        final List<Braco> bracos = new ArrayList<>();
        final Location centro;
        final double raio;
        final int dur;

        Lamina(Luta l) {
            super(l);
            congelar(l);
            centro = c.e.getLocation().clone();
            centro.setY(piso());
            Caixa a = arena();
            raio = Math.hypot(a.x2() - a.x1() + 1, a.z2() - a.z1() + 1) / 2 + 1;
            double f = c.furioso ? 1.25 : 1.0;
            int e = l.tema.estilo();
            double a0 = rnd.nextDouble() * Math.PI * 2;
            if (e == 2) {
                bracos.add(new Braco(a0, 0.085 * f, false));
                bracos.add(new Braco(a0 + Math.PI, -0.13 * f, true));
            } else bracos.add(new Braco(a0, (rnd.nextBoolean() ? 1 : -1) * (e == 1 ? 0.14 : 0.11) * f, e == 1));
            dur = c.furioso ? 95 : 75;
            String tt = e == 2 ? "§c§lPULE a baixa, AGACHE na alta!" : e == 1 ? "§c§lAGACHE!" : "§c§lPULE A LÂMINA!";
            String st = e == 2 ? "§7Duas lâminas girando" : e == 1 ? "§7A lâmina passa na altura da cabeça" : "§7A lâmina passa rente ao chão";
            for (Player p : c.naArena()) titulo(p, tt, st, 1800);
            som(w, centro, "minecraft:item.trident.riptide_3", 1.4f, 0.7f);
        }

        void desenhar(Braco b) {
            double y = centro.getY() + (b.alta ? 1.65 : 0.35);
            double cs = Math.cos(b.ang), sn = Math.sin(b.ang);
            int i = 0;
            for (double r = 0.9; r <= raio; r += 0.55, i++) {
                Location q = new Location(w, centro.getX() + cs * r, y, centro.getZ() + sn * r);
                if (i % 2 == 0) po(w, q, b.alta ? Color.fromRGB(255, 60, 60) : cor(), 1.3f, 1, 0);
                else fx(w, part(), q, 1, 0, 0, 0, 0);
            }
        }

        boolean passou(Braco b, double phi) {
            double tau = Math.PI * 2;
            if (b.vel >= 0) {
                double d = ((phi - b.antes) % tau + tau) % tau;
                return d <= b.vel + 0.04;
            }
            double d = ((b.antes - phi) % tau + tau) % tau;
            return d <= -b.vel + 0.04;
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t < AVISO) {
                if (t % 4 < 2) for (Braco b : bracos) desenhar(b);
                return true;
            }
            for (Braco b : bracos) {
                b.antes = b.ang;
                b.ang += b.vel;
                desenhar(b);
                if (t % 8 == 0) som(w, centro, "minecraft:entity.player.attack.sweep", 0.9f, b.alta ? 1.2f : 0.7f);
                for (Player p : ps) {
                    double dx = p.getX() - centro.getX(), dz = p.getZ() - centro.getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < 0.7 || d > raio + 0.5 || p.getY() - piso() > 2.5) continue;
                    if (!passou(b, Math.atan2(dz, dx))) continue;
                    Integer ult = b.cd.get(p.getUniqueId());
                    if (ult != null && t - ult < 8) continue;
                    boolean escapou = b.alta ? (p.isSneaking() || p.isSwimming() || p.isGliding()) : noAr(p, piso());
                    if (escapou) continue;
                    b.cd.put(p.getUniqueId(), t);
                    p.damage(dano(3), c.e);
                    Vector tang = new Vector(-dz, 0, dx);
                    if (b.vel < 0) tang.multiply(-1);
                    if (tang.lengthSquared() > 0.01) p.setVelocity(tang.normalize().multiply(0.6).setY(0.35));
                    p.sendActionBar(Masmorras.txt(b.alta ? "§c§lAgache na lâmina alta!" : "§c§lPule a lâmina baixa!"));
                }
            }
            return t < AVISO + dur;
        }
    }

    // ================================================================== ALVOS: escudo ate quebrar os alvos

    private final class Alvos extends Cena {
        final List<Block> alvos = new ArrayList<>();

        Alvos(Luta l, List<Player> ps) {
            super(l);
            int q = ps.size() >= 3 ? 4 : 3;
            Material m = l.tema.bloco() != null && l.tema.bloco().isBlock() ? l.tema.bloco() : Material.AMETHYST_BLOCK;
            for (int i = 0; i < q; i++) {
                Block b = chaoLivre(l, null, 0, 5);
                if (b == null) continue;
                colocar(l, b, m, true);
                alvos.add(b);
            }
            if (alvos.isEmpty()) throw new IllegalStateException("sem lugar para os alvos");
            l.imuneAte = System.currentTimeMillis() + 30_000;
            for (Player p : ps) titulo(p, "§b§lESCUDO!", "§7Quebre os §f" + alvos.size() + " " + l.tema.coisa() + " §7brilhando (clique neles)", 2400);
            som(w, c.e.getLocation(), "minecraft:block.beacon.activate", 1.5f, 0.8f);
        }

        int faltam() {
            int f = 0;
            for (Block b : alvos) if (l.clicaveis.contains(b)) f++;
            return f;
        }

        @Override
        void clicou(Player p, Block b) {
            if (!alvos.contains(b) || !l.clicaveis.contains(b)) return;
            BlockData d = b.getBlockData();
            tirar(l, b);
            w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0.1, d);
            som(w, b.getLocation(), "minecraft:block.amethyst_block.break", 1.4f, 0.8f);
            int f = faltam();
            for (Player x : c.naArena()) x.sendActionBar(Masmorras.txt(f > 0 ? "§b" + p.getName() + " quebrou um! §7Faltam §f" + f : "§a§lESCUDO QUEBRADO!"));
        }

        @Override
        boolean passo(List<Player> ps) {
            if (faltam() == 0) {
                l.imuneAte = 0;
                som(w, c.e.getLocation(), "minecraft:block.glass.break", 1.6f, 0.6f);
                fx(w, Particle.END_ROD, c.e.getLocation().add(0, c.e.getHeight() / 2, 0), 30, 0.6, 0.8, 0.6, 0.15);
                return false;
            }
            if (t % 3 == 0) {
                for (Block b : alvos) {
                    if (!l.clicaveis.contains(b)) continue;
                    Location lc = b.getLocation().add(0.5, 1.1, 0.5);
                    for (int i = 0; i < 4; i++) fx(w, Particle.END_ROD, lc.clone().add(0, i * 0.7, 0), 1, 0.05, 0.05, 0.05, 0);
                    fx(w, part(), lc, 2, 0.4, 0.3, 0.4, 0.01);
                }
            }
            if (t % 2 == 0) {
                Location lc = c.e.getLocation();
                double r = Math.max(1.2, c.e.getWidth()), h = c.e.getHeight();
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + t * 0.1;
                    fx(w, Particle.END_ROD, lc.clone().add(Math.cos(a) * r, (i % 3) * h / 2.5, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
            }
            if (t >= 260) {
                double cura = c.vidaMax * 0.08;
                c.e.setHealth(Math.min(c.e.getHealth() + cura, c.vidaMax));
                fx(w, Particle.HEART, c.e.getLocation().add(0, c.e.getHeight(), 0), 8, 0.5, 0.3, 0.5, 0);
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§c§l" + c.t.chefeNome + " se curou! §7Quebre os alvos mais rápido."));
                return false;
            }
            return true;
        }
    }

    // ================================================================== MINAS: armadilhas que estouram

    private final class Minas extends Cena {
        static final int PAVIO = 60;
        final List<Block> minas = new ArrayList<>();
        int desarmadas;

        Minas(Luta l, List<Player> ps) {
            super(l);
            int q = Math.min(8, 3 + ps.size() + (c.furioso ? 1 : 0));
            Material m = l.tema.bloco() != null && l.tema.bloco().isBlock() ? l.tema.bloco() : Material.TNT;
            for (int i = 0; i < q; i++) {
                Player perto = ps.get(rnd.nextInt(ps.size()));
                Block b = chaoLivre(l, rnd.nextBoolean() ? perto.getLocation() : null, 4, 3);
                if (b == null) continue;
                colocar(l, b, m, true);
                minas.add(b);
            }
            if (minas.isEmpty()) throw new IllegalStateException("sem lugar para as armadilhas");
            String coisa = l.tema.coisa();
            for (Player p : ps) titulo(p, "§6§l" + coisa.substring(0, 1).toUpperCase(Locale.ROOT) + coisa.substring(1) + "!",
                    "§7Clique nelas antes que estourem (6 s)", 2000);
            som(w, c.e.getLocation(), "minecraft:block.grass.place", 1.5f, 0.6f);
        }

        @Override
        void clicou(Player p, Block b) {
            if (!minas.contains(b) || !l.clicaveis.contains(b)) return;
            BlockData d = b.getBlockData();
            tirar(l, b);
            desarmadas++;
            w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.1, d);
            som(w, b.getLocation(), "minecraft:entity.item.break", 1.2f, 1f);
        }

        @Override
        boolean passo(List<Player> ps) {
            int vivas = 0;
            for (Block b : minas) if (l.clicaveis.contains(b)) vivas++;
            if (vivas == 0) {
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§a§lTodas desarmadas!"));
                return false;
            }
            if (t % 4 == 0) for (Block b : minas) if (l.clicaveis.contains(b)) fx(w, part(), b.getLocation().add(0.5, 1.1, 0.5), 2, 0.3, 0.2, 0.3, 0.01);
            if (t >= PAVIO - 20 && t % 5 == 0) {
                for (Block b : minas) if (l.clicaveis.contains(b)) fx(w, Particle.SMOKE, b.getLocation().add(0.5, 1.1, 0.5), 4, 0.2, 0.2, 0.2, 0.02);
                som(w, c.e.getLocation(), "minecraft:block.note_block.hat", 1.2f, 0.8f + (t - PAVIO + 20) / 20f);
            }
            if (t % 10 == 0 && t < PAVIO) for (Player p : ps) p.sendActionBar(Masmorras.txt("§6§l" + (PAVIO - t) / 10 + "s §7— faltam §f" + vivas));
            if (t < PAVIO) return true;
            int monstros = 0;
            String[] tipos = c.t.ajudantes != null && c.t.ajudantes.length > 0 ? c.t.ajudantes : c.t.lacaios;
            for (Block b : new ArrayList<>(minas)) {
                if (!l.clicaveis.contains(b)) continue;
                tirar(l, b);
                Location lc = b.getLocation().add(0.5, 0.2, 0.5);
                fx(w, Particle.EXPLOSION, lc, 1, 0, 0, 0, 0);
                fx(w, part(), lc, 10, 0.5, 0.4, 0.5, 0.05);
                for (Player p : ps) {
                    if (p.getLocation().distanceSquared(lc) > 9) continue;
                    p.damage(dano(3), c.e);
                }
                if (monstros < 3 && tipos != null && tipos.length > 0) {
                    pl.lacaio(c.m, tipos[rnd.nextInt(tipos.length)], lc, true);
                    monstros++;
                }
            }
            som(w, c.e.getLocation(), "minecraft:entity.generic.explode", 1.2f, 1f);
            return false;
        }
    }

    // ================================================================== BOMBA: alguem vira bomba

    private final class Bomba extends Cena {
        static final int PAVIO = 60;
        final Player alvo;

        Bomba(Luta l, List<Player> ps) {
            super(l);
            alvo = ps.get(rnd.nextInt(ps.size()));
            titulo(alvo, "§c§lVOCÊ É A BOMBA!", "§7Corra até o chefe e fique longe dos amigos!", 2400);
            for (Player p : ps) if (!p.equals(alvo)) p.sendActionBar(Masmorras.txt("§c§l" + alvo.getName() + " vai explodir! §7Fique longe dele."));
            som(w, alvo.getLocation(), "minecraft:entity.tnt.primed", 1.5f, 1f);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (!alvo.isOnline() || alvo.isDead() || !ps.contains(alvo)) return false;
            Location lc = alvo.getLocation();
            if (t % 2 == 0) {
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5 + t * 0.2;
                    po(w, lc.clone().add(Math.cos(a) * 1.3, 0.2, Math.sin(a) * 1.3), Color.RED, 1.2f, 1, 0);
                }
                fx(w, Particle.SMOKE, lc.clone().add(0, 2.2, 0), 2, 0.1, 0.1, 0.1, 0.01);
                fx(w, part(), lc.clone().add(0, 1.2, 0), 1, 0.3, 0.4, 0.3, 0.01);
            }
            if (t % 10 == 0 && t < PAVIO) {
                som(w, lc, "minecraft:block.note_block.pling", 1.3f, 0.6f + t / 60f);
                double dc = horiz(lc, c.e.getLocation());
                alvo.sendActionBar(Masmorras.txt("§c§lBOMBA: " + (PAVIO - t) / 10 + "s §7— chefe a §f" + (int) dc + " §7blocos" + (dc <= 5 ? " §a(perto!)" : "")));
            }
            if (t < PAVIO) return true;
            fx(w, Particle.EXPLOSION_EMITTER, lc.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
            fx(w, part(), lc.clone().add(0, 0.5, 0), 25, 1.2, 0.6, 1.2, 0.08);
            som(w, lc, "minecraft:entity.generic.explode", 1.6f, 0.9f);
            for (Player p : ps) {
                if (p.equals(alvo) || p.getLocation().distanceSquared(lc) > 4.5 * 4.5) continue;
                p.damage(dano(5), c.e);
                Vector v = p.getLocation().toVector().subtract(lc.toVector()).setY(0);
                if (v.lengthSquared() > 0.01) p.setVelocity(v.normalize().multiply(0.8).setY(0.5));
            }
            alvo.damage(dano(0), c.e);
            if (c.e.getLocation().distanceSquared(lc) <= 5.5 * 5.5) {
                c.e.damage(c.vidaMax * 0.07, alvo);
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§a§lA bomba acertou " + c.t.chefeNome + "! §7(-7% da vida)"));
            } else alvo.sendActionBar(Masmorras.txt("§7A bomba explodiu longe do chefe."));
            return false;
        }
    }

    // ================================================================== REDEMOINHO: puxa para o meio

    private final class Redemoinho extends Cena {
        static final int AVISO = 15, DUR = 80;
        final Location centro;
        final double forca;

        Redemoinho(Luta l) {
            super(l);
            Caixa a = arena();
            centro = new Location(w, a.cx() + 0.5, a.y1(), a.cz() + 0.5);
            forca = c.furioso ? 0.24 : 0.2;
            for (Player p : c.naArena()) titulo(p, "§9§lREDEMOINHO!", "§7Corra para longe do meio!", 1800);
            som(w, centro, "minecraft:item.trident.riptide_3", 1.6f, 0.5f);
        }

        @Override
        boolean passo(List<Player> ps) {
            double giro = t * 0.25;
            for (int braco = 0; braco < 3; braco++) {
                for (double r = 0.5; r < 9; r += 0.9) {
                    double a = giro + braco * 2 * Math.PI / 3 + r * 0.45;
                    Location q = centro.clone().add(Math.cos(a) * r, 0.25, Math.sin(a) * r);
                    if (t % 2 == 0) po(w, q, cor(), 1.2f, 1, 0);
                    else fx(w, part(), q, 1, 0, 0, 0, 0);
                }
            }
            if (t < AVISO) return true;
            if (t % 6 == 0) som(w, centro, "minecraft:block.bubble_column.whirlpool_inside", 1.4f, 0.8f);
            for (Player p : ps) {
                Vector v = centro.toVector().subtract(p.getLocation().toVector()).setY(0);
                double d = v.length();
                // empurraozinho fixo a cada 0,2 s, so no chao (no ar nao mexe: nao estraga o pulo); andando para fora da para sair
                if (d > 0.6 && t % 2 == 0 && !noAr(p, piso())) p.setVelocity(v.normalize().multiply(forca));
                if (d < 2.5 && t % 8 == 0) {
                    p.damage(dano(0), c.e);
                    p.sendActionBar(Masmorras.txt("§9§lSaia do meio!"));
                }
            }
            return t < AVISO + DUR;
        }
    }

    // ================================================================== ELO: afaste-se antes que ele drene

    private final class Elo extends Cena {
        static final int DUR = 50;
        static final double DIST = 7;

        Elo(Luta l) {
            super(l);
            congelar(l);
            for (Player p : c.naArena()) titulo(p, "§4§lELO!", "§7Fique a mais de §f7 §7blocos dele!", 2000);
            som(w, c.e.getLocation(), "minecraft:entity.evoker.prepare_attack", 1.5f, 0.6f);
        }

        @Override
        boolean passo(List<Player> ps) {
            Location de = c.e.getLocation().add(0, c.e.getHeight() * 0.6, 0);
            for (Player p : ps) {
                double d = horiz(p.getLocation(), c.e.getLocation());
                if (t % 2 == 0) {
                    Location para = p.getLocation().add(0, 1, 0);
                    Vector v = para.toVector().subtract(de.toVector());
                    double len = v.length();
                    if (len > 0.1) {
                        v.normalize();
                        Color cor = d >= DIST ? Color.fromRGB(80, 220, 80) : cor();
                        for (double s = 0.6; s < len; s += 0.8) po(w, de.clone().add(v.clone().multiply(s)), cor, 1.0f, 1, 0);
                    }
                }
                if (t % 5 == 0 && t < DUR)
                    p.sendActionBar(Masmorras.txt((d >= DIST ? "§a§lLonge! " : "§c§lPerto demais! ") + "§7" + (int) d + "/" + (int) DIST + " blocos — §f" + (DUR - t) / 10 + "s"));
            }
            if (t < DUR) return true;
            double curou = 0;
            for (Player p : ps) {
                double d = horiz(p.getLocation(), c.e.getLocation());
                if (d >= DIST) {
                    p.sendActionBar(Masmorras.txt("§a§lO elo arrebentou!"));
                    continue;
                }
                double dn = dano(3);
                p.damage(dn, c.e);
                curou += dn;
                fx(w, Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.1);
                p.sendActionBar(Masmorras.txt("§4§lO elo drenou sua vida!"));
            }
            if (curou > 0) {
                c.e.setHealth(Math.min(c.vidaMax, c.e.getHealth() + Math.min(curou * 2, c.vidaMax * 0.05)));
                fx(w, Particle.HEART, c.e.getLocation().add(0, c.e.getHeight(), 0), 5, 0.4, 0.3, 0.4, 0);
            }
            som(w, c.e.getLocation(), "minecraft:entity.evoker.cast_spell", 1.4f, 0.6f);
            return false;
        }
    }

    // ================================================================== RASTRO: corre deixando um rastro que machuca

    private final class Rastro extends Cena {
        static final int DUR = 100, VIDA = 60;
        final List<Location> pontos = new ArrayList<>();
        final List<Integer> nasceu = new ArrayList<>();
        final Map<UUID, Integer> cd = new HashMap<>();
        final boolean fogo;

        Rastro(Luta l) {
            super(l);
            fogo = l.tema.bloco() == Material.MAGMA_BLOCK || l.tema.bloco() == Material.NETHERRACK;
            c.e.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, DUR * 2, 1, false, false));
            for (Player p : c.naArena()) titulo(p, "§2§lRASTRO!", "§7Não pise no rastro que ele deixa", 1800);
            som(w, c.e.getLocation(), "minecraft:entity.slime.squish", 1.5f, 0.6f);
        }

        @Override
        boolean passo(List<Player> ps) {
            Location ch = c.e.getLocation().clone();
            ch.setY(Math.max(piso(), Math.floor(ch.getY())));
            if (t < DUR && (pontos.isEmpty() || horiz(pontos.get(pontos.size() - 1), ch) > 0.8)) {
                pontos.add(ch);
                nasceu.add(t);
            }
            for (int i = pontos.size() - 1; i >= 0; i--) {
                if (t - nasceu.get(i) > VIDA) {
                    pontos.remove(i);
                    nasceu.remove(i);
                }
            }
            if (t % 2 == 0) for (Location q : pontos) {
                po(w, q.clone().add(0, 0.1, 0), cor(), 1.5f, 1, 0.3);
                if (rnd.nextInt(3) == 0) fx(w, part(), q.clone().add(0, 0.2, 0), 1, 0.2, 0.05, 0.2, 0.01);
            }
            for (Player p : ps) {
                Integer u = cd.get(p.getUniqueId());
                if (u != null && t - u < 10) continue;
                for (Location q : pontos) {
                    if (horiz(p.getLocation(), q) < 0.9 && Math.abs(p.getY() - q.getY()) < 1.2) {
                        cd.put(p.getUniqueId(), t);
                        p.damage(dano(1), c.e);
                        if (fogo) p.setFireTicks(Math.max(p.getFireTicks(), 40));
                        else p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                        p.sendActionBar(Masmorras.txt("§2§lSaia do rastro!"));
                        break;
                    }
                }
            }
            return t < DUR || !pontos.isEmpty();
        }
    }

    // ================================================================== 17/11 (Paulo): quem entrou na sala do chefe nao foge

    private static boolean naSala(Caixa a, Location x) {
        return x.getX() >= a.x1() - 0.3 && x.getX() <= a.x2() + 1.3 && x.getZ() >= a.z1() - 0.3 && x.getZ() <= a.z2() + 1.3
                && x.getY() >= a.y1() - 6 && x.getY() <= a.y2() + 4;
    }

    private Masmorra salaTrancada(Player p, Location de, Location para) {
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return null;
        if (!pl.getConfig().getBoolean("chefe.sala-trancada", true)) return null;
        for (Masmorra m : pl.todas) {
            Chefe c = m.chefe;
            if (c == null || !c.vivo() || !m.mundo.equals(de.getWorld().getName())) continue;
            Caixa a = m.arenaLuta();
            if (naSala(a, de) && (!para.getWorld().equals(de.getWorld()) || !naSala(a, para))) return m;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void fugir(org.bukkit.event.player.PlayerMoveEvent e) {
        Location de = e.getFrom(), para = e.getTo();
        if (para == null || (de.getBlockX() == para.getBlockX() && de.getBlockZ() == para.getBlockZ() && de.getBlockY() == para.getBlockY())) return;
        if (salaTrancada(e.getPlayer(), de, para) == null) return;
        Location volta = de.clone();
        volta.setYaw(para.getYaw());
        volta.setPitch(para.getPitch());
        e.setTo(volta);
        e.getPlayer().sendActionBar(Masmorras.txt("§c§l🔒 A sala está trancada! §7Só dá para sair quando o chefe morrer."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void fugirTeleporte(org.bukkit.event.player.PlayerTeleportEvent e) {
        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cz = e.getCause();
        if (cz != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && cz != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) return;
        if (e.getTo() == null || salaTrancada(e.getPlayer(), e.getFrom(), e.getTo()) == null) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Masmorras.txt("§c§l🔒 A sala está trancada! §7Só dá para sair quando o chefe morrer."));
    }

    // ================================================================== PASSIVAS (17/11): Bispo, Lorde Vampiro, Ceifador

    private void passivas(Luta l, List<Player> ps, long agora) {
        Chefe c = l.c;
        World w = c.e.getWorld();
        // ---- Bispo Corrompido: a sacerdotisa escondida numa sala reza por ele
        if (c.t.id.equals("CATEDRAL") && !ps.isEmpty()) {
            if (!l.sacerdotisaFeita) {
                l.sacerdotisaFeita = true;
                criarSacerdotisa(l);
            }
            LivingEntity s = l.sacerdotisa;
            if (s != null && (!s.isValid() || s.isDead())) {
                l.sacerdotisa = null;
                l.sacerdotisaMorta = true;
                som(w, c.e.getLocation(), "minecraft:block.bell.resonate", 2f, 0.5f);
                for (LivingEntity x : l.copias) if (x.isValid()) {
                    fx(w, Particle.LARGE_SMOKE, x.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
                    x.remove();
                }
                l.copias.clear();
                for (Player p : c.naArena()) titulo(p, "§e§lA SACERDOTISA CAIU!", "§7Agora o Bispo morre de verdade", 2500);
            } else if (s != null && agora >= l.proximoSino) {
                l.proximoSino = agora + 3500;
                // o sino toca onde ela esta (da para seguir o som) e um rastro dourado sai de cada jogador na direcao dela
                w.playSound(s.getLocation(), "minecraft:block.bell.use", 3f, 0.8f);
                fx(w, Particle.END_ROD, s.getLocation().add(0, 2.3, 0), 6, 0.2, 0.3, 0.2, 0.01);
                for (Player p : w.getPlayers()) {
                    if (p.getLocation().distanceSquared(s.getLocation()) > 120 * 120) continue;
                    Vector d = s.getLocation().toVector().subtract(p.getLocation().toVector());
                    if (d.lengthSquared() < 4) continue;
                    d.normalize();
                    Location q = p.getLocation().add(0, 0.3, 0);
                    for (int i = 1; i <= 6; i++) po(w, q.clone().add(d.clone().multiply(i * 0.8)), Color.fromRGB(255, 215, 80), 1.1f, 1, 0.02);
                }
            }
        }
        // ---- Lorde Vampiro: no sol pega fogo e brilha
        if (l.solAte > agora) {
            c.e.setFireTicks(Math.max(c.e.getFireTicks(), 30));
            Location lc = c.e.getLocation();
            for (int i = 0; i < 10; i++) fx(w, Particle.END_ROD, lc.clone().add(0, i * 0.8, 0), 1, 0.15, 0.1, 0.15, 0);
        }
        // ---- Ceifador de Almas: almas esperando um amigo
        if (!l.almas.isEmpty()) {
            List<Player> vivos = new ArrayList<>();
            for (Player p : ps) if (!l.almas.containsKey(p.getUniqueId())) vivos.add(p);
            for (Iterator<Map.Entry<UUID, Alma>> it = l.almas.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Alma> en = it.next();
                Alma a = en.getValue();
                Player dono = Bukkit.getPlayer(en.getKey());
                if (dono == null || !dono.isOnline()) {
                    a.limpar();
                    it.remove();
                    continue;
                }
                if (c.e instanceof Mob mob && dono.equals(mob.getTarget())) mob.setTarget(null);
                fx(w, Particle.SOUL, a.corpo.clone().add(0, 0.6, 0), 3, 0.25, 0.3, 0.25, 0.01);
                Player salvador = null;
                for (Player v : vivos) if (v.getLocation().distanceSquared(a.corpo) < 1.8 * 1.8) salvador = v;
                if (salvador != null) {
                    a.reviver(dono, 0.5);
                    it.remove();
                    for (Player x : c.naArena()) x.sendActionBar(Masmorras.txt("§b§l" + salvador.getName() + " pegou a alma de " + dono.getName() + "! §7Voltou à vida."));
                    som(w, a.corpo, "minecraft:item.totem.use", 1f, 1.4f);
                    continue;
                }
                long falta = a.ate - agora;
                if (falta <= 0 || vivos.isEmpty()) {
                    a.limpar();
                    it.remove();
                    dono.removePotionEffect(PotionEffectType.INVISIBILITY);
                    dono.sendMessage(Masmorras.txt("§8☠ Ninguém pegou a sua alma a tempo."));
                    dono.setHealth(0);
                    continue;
                }
                dono.sendActionBar(Masmorras.txt("§b☠ Você é uma alma! §7Um amigo precisa passar no seu corpo §8(" + (falta / 1000 + 1) + "s) §7— bata no chefe para atrapalhar"));
            }
        }
    }

    private void criarSacerdotisa(Luta l) {
        Chefe c = l.c;
        Masmorra m = c.m;
        World w = c.e.getWorld();
        List<Caixa> salas = new ArrayList<>(m.salas);
        if (salas.size() > 1) salas.remove(salas.size() - 1);   // a ultima e a arena
        Caixa arena = m.arenaLuta();
        java.util.Collections.shuffle(salas, rnd);
        for (Caixa s : salas) {
            if (s.equals(arena)) continue;
            for (int tent = 0; tent < 25; tent++) {
                int x = s.x1() + 1 + rnd.nextInt(Math.max(1, s.x2() - s.x1() - 1));
                int z = s.z1() + 1 + rnd.nextInt(Math.max(1, s.z2() - s.z1() - 1));
                Block b = w.getBlockAt(x, s.y1(), z);
                if (!b.isPassable() || !b.getRelative(BlockFace.UP).isPassable() || !b.getRelative(BlockFace.DOWN).getType().isSolid()) continue;
                LivingEntity e = pl.lacaio(m, "WITCH", b.getLocation().add(0.5, 0, 0.5), true);
                if (e == null) return;
                e.customName(Masmorras.txt("§d§l✝ Sacerdotisa do Bispo"));
                e.setCustomNameVisible(true);
                e.setRemoveWhenFarAway(false);
                AttributeInstance v = e.getAttribute(Attribute.MAX_HEALTH);
                if (v != null) {
                    v.setBaseValue(30 + 10 * c.t.nivel);
                    e.setHealth(v.getValue());
                }
                l.sacerdotisa = e;
                l.proximoSino = System.currentTimeMillis() + 4000;
                for (Player p : c.naArena()) {
                    p.sendMessage(Masmorras.txt("§d§l✝ " + c.t.chefeNome + ": §7\"Enquanto ela rezar por mim, eu não morro.\""));
                    p.sendMessage(Masmorras.txt("§7» Uma §dsacerdotisa §7está escondida em outra sala. §eSiga o sino e o rastro dourado §7e mate-a."));
                }
                return;
            }
        }
    }

    private void bispoVolta(Luta l) {
        Chefe c = l.c;
        World w = c.e.getWorld();
        AttributeInstance v = c.e.getAttribute(Attribute.MAX_HEALTH);
        c.e.setHealth(Math.min(v != null ? v.getValue() : c.vidaMax, c.vidaMax));
        c.e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4, false, false));
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, c.e.getLocation().add(0, c.e.getHeight() / 2, 0), 80, 0.6, 1, 0.6, 0.4);
        som(w, c.e.getLocation(), "minecraft:block.bell.use", 2f, 0.6f);
        l.copias.removeIf(x -> !x.isValid() || x.isDead());
        int q = Math.min(2, 6 - l.copias.size());
        for (int i = 0; i < q; i++) {
            Location ok = c.lugarLivre(c.e.getLocation().clone().add(rnd.nextInt(9) - 4, 0, rnd.nextInt(9) - 4));
            if (ok == null) continue;
            LivingEntity x = pl.lacaio(c.m, c.t.chefe, ok, true);
            if (x == null) continue;
            x.customName(c.e.customName());
            x.setCustomNameVisible(c.e.isCustomNameVisible());
            for (Attribute at : new Attribute[] {Attribute.SCALE, Attribute.MOVEMENT_SPEED}) {
                AttributeInstance de = c.e.getAttribute(at), para = x.getAttribute(at);
                if (de != null && para != null) para.setBaseValue(de.getBaseValue());
            }
            AttributeInstance vida = x.getAttribute(Attribute.MAX_HEALTH);
            if (vida != null) {
                vida.setBaseValue(Math.max(20, c.vidaMax * 0.12));
                x.setHealth(vida.getValue());
            }
            EntityEquipment de = c.e.getEquipment(), para = x.getEquipment();
            if (de != null && para != null) {
                para.setItemInMainHand(de.getItemInMainHand());
                para.setHelmet(de.getHelmet());
                para.setChestplate(de.getChestplate());
                para.setItemInMainHandDropChance(0f);
                para.setHelmetDropChance(0f);
                para.setChestplateDropChance(0f);
            }
            fx(w, Particle.LARGE_SMOKE, ok.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
            l.copias.add(x);
        }
        for (Player p : c.naArena()) titulo(p, "§d§lO BISPO VOLTOU!", "§7A sacerdotisa rezou por ele (só desta vez). Derrube de novo!", 2500);
    }

    private void fimPassivas(Luta l, boolean ignorado) {
        if (l.sacerdotisa != null && l.sacerdotisa.isValid()) l.sacerdotisa.remove();
        l.sacerdotisa = null;
        for (LivingEntity x : l.copias) if (x.isValid()) x.remove();
        l.copias.clear();
        l.solAte = 0;
        for (Map.Entry<UUID, Alma> en : l.almas.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null && p.isOnline()) en.getValue().reviver(p, 0.3);
            else en.getValue().limpar();
        }
        l.almas.clear();
    }

    /** Ceifador: o jogador virou alma (o corpo fica no chao esperando um amigo). */
    private final class Alma {
        final Location corpo;
        final long ate;
        final org.bukkit.entity.ArmorStand marca;

        Alma(Player p) {
            corpo = p.getLocation().clone();
            ate = System.currentTimeMillis() + 30_000;
            marca = corpo.getWorld().spawn(corpo.clone().add(0, -0.4, 0), org.bukkit.entity.ArmorStand.class, a -> {
                a.setInvisible(true);
                a.setMarker(true);
                a.setGravity(false);
                a.setSmall(true);
                a.setPersistent(false);
                a.customName(Masmorras.txt("§b☠ Alma de " + p.getName() + " §7— passe aqui!"));
                a.setCustomNameVisible(true);
            });
        }

        void limpar() {
            if (marca != null && marca.isValid()) marca.remove();
        }

        void reviver(Player p, double vida) {
            limpar();
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.WEAKNESS);
            AttributeInstance v = p.getAttribute(Attribute.MAX_HEALTH);
            double max = v != null ? v.getValue() : 20;
            p.setHealth(Math.max(1, Math.min(max, max * vida)));
            p.setFireTicks(0);
            titulo(p, "§a§lVOCÊ VOLTOU!", "", 1500);
        }
    }

    /** Ceifador de Almas: quem morreria na arena vira alma. A alma nao leva dano. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void quaseMorreu(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        for (Luta l : lutas.values()) {
            if (!l.c.t.id.equals("ALMAS") || !l.c.vivo()) continue;
            if (l.almas.containsKey(p.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            if (!l.c.m.arenaLuta().crescer(2).contem(p.getX(), p.getY(), p.getZ())) continue;
            if (e.getFinalDamage() < p.getHealth() + p.getAbsorptionAmount()) return;
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.KILL) return;
            if (p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                    || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) return;
            e.setCancelled(true);
            p.setHealth(1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 31, 0, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 31, 4, false, false));
            Alma a = new Alma(p);
            l.almas.put(p.getUniqueId(), a);
            World w = p.getWorld();
            fx(w, Particle.SOUL, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
            som(w, p.getLocation(), "minecraft:particle.soul_escape", 2f, 0.6f);
            titulo(p, "§b§l☠ VOCÊ VIROU ALMA", "§7Um amigo tem 30 s para passar no seu corpo", 2500);
            for (Player x : l.c.naArena()) if (!x.equals(p)) x.sendMessage(Masmorras.txt("§b☠ " + p.getName() + " virou alma! §7Passe no corpo dele (nome azul no chão) para trazer de volta."));
            return;
        }
    }

    // ================================================================== NINHADA (Rainha Aranha): sobe no teto e joga ovos

    private final class Ninhada extends Cena {
        Location teto;
        final List<org.bukkit.entity.Item> ovos = new ArrayList<>();
        final Map<org.bukkit.entity.Item, Integer> nasceu = new HashMap<>();
        int jogados;

        Ninhada(Luta l, List<Player> ps) {
            super(l);
            Caixa a = arena();
            double alto = Math.max(a.y1() + 3, a.y2() - c.e.getHeight() - 0.6);
            if (alto - a.y1() < 2.5) throw new IllegalStateException("arena baixa demais para o teto");
            Location base = c.e.getLocation();
            teto = new Location(w, base.getX(), alto, base.getZ(), base.getYaw(), 0);
            congelar(l);
            c.e.setGravity(false);
            l.imuneAte = System.currentTimeMillis() + 6000;
            som(w, base, "minecraft:entity.spider.ambient", 2f, 0.5f);
            for (Player p : ps) titulo(p, "§2§lNINHADA!", "§7Ela subiu no teto — mate as aranhas que nascerem", 2000);
        }

        @Override
        boolean passo(List<Player> ps) {
            Location ag = c.e.getLocation();
            if (t < 8) {
                Location q = ag.clone().add(0, (teto.getY() - ag.getY()) * 0.35, 0);
                c.e.teleport(q);
                po(w, ag.clone().add(0, 0.3, 0), Color.WHITE, 1f, 4, 0.2);
                for (double y = ag.getY(); y < teto.getY() + 1; y += 0.6) po(w, new Location(w, ag.getX(), y, ag.getZ()), Color.fromRGB(230, 230, 230), 0.7f, 1, 0);
                return true;
            }
            if (t < 40) c.e.teleport(teto);
            c.e.setVelocity(new Vector());
            if ((t == 14 || t == 24) && !ps.isEmpty()) {
                Player alvo = ps.get(rnd.nextInt(ps.size()));
                Location de = c.e.getLocation().add(0, 0.2, 0);
                org.bukkit.entity.Item ovo = w.dropItem(de, new org.bukkit.inventory.ItemStack(Material.TURTLE_EGG));
                ovo.setPickupDelay(Integer.MAX_VALUE);
                ovo.setCanMobPickup(false);
                Vector v = alvo.getLocation().toVector().subtract(de.toVector()).setY(0).multiply(0.08);
                if (v.length() > 0.7) v.normalize().multiply(0.7);
                ovo.setVelocity(v.setY(0.1));
                ovos.add(ovo);
                l.coisas.add(ovo);
                som(w, de, "minecraft:entity.turtle.lay_egg", 1.5f, 1.2f);
                jogados++;
            }
            for (org.bukkit.entity.Item ovo : new ArrayList<>(ovos)) {
                if (!ovo.isValid()) {
                    ovos.remove(ovo);
                    continue;
                }
                if (ovo.isOnGround() || ovo.getTicksLived() > 40) {
                    nasceu.merge(ovo, 1, Integer::sum);
                    if (nasceu.get(ovo) == 1) som(w, ovo.getLocation(), "minecraft:block.sniffer_egg.crack", 1.5f, 1.2f);
                    if (nasceu.get(ovo) >= 6) {
                        Location ok = c.lugarLivre(ovo.getLocation());
                        if (ok == null) ok = ovo.getLocation();
                        LivingEntity ar = pl.lacaio(c.m, "CAVE_SPIDER", ok, true);
                        if (ar != null) {
                            ar.customName(Masmorras.txt("§2Filhote da Rainha"));
                            fx(w, Particle.ITEM_SLIME, ok.clone().add(0, 0.3, 0), 15, 0.3, 0.2, 0.3, 0.05);
                        }
                        ovos.remove(ovo);
                        ovo.remove();
                    }
                }
            }
            if (t == 40) {
                Location chao = c.lugarLivre(c.e.getLocation());
                if (chao == null) chao = new Location(w, c.e.getX(), piso(), c.e.getZ());
                c.e.teleport(chao);
                c.e.setGravity(true);
                l.imuneAte = 0;
                fx(w, Particle.CLOUD, chao.clone().add(0, 0.2, 0), 20, 0.6, 0.1, 0.6, 0.05);
                som(w, chao, "minecraft:entity.spider.step", 2f, 0.6f);
            }
            return t < 46 || !ovos.isEmpty();
        }

        @Override
        void fim() {
            c.e.setGravity(true);
            if (c.e.getLocation().getY() > piso() + 1.5) {
                Location chao = c.lugarLivre(c.e.getLocation());
                if (chao != null) c.e.teleport(chao);
            }
        }
    }

    // ================================================================== ENGOLIR (Gosma): quem foi engolido bate para sair

    private final class Engolir extends Cena {
        final Player preso;
        final int precisa;
        int bateu;
        boolean saiu;

        Engolir(Luta l, List<Player> ps) {
            super(l);
            preso = ps.get(rnd.nextInt(ps.size()));
            precisa = 3 + n;
            som(w, c.e.getLocation(), "minecraft:entity.slime.squish", 2f, 0.4f);
            titulo(preso, "§a§lVOCÊ FOI ENGOLIDO!", "§7Bata nela §f" + precisa + "x §7para sair", 2000);
            for (Player p : ps) if (!p.equals(preso)) p.sendActionBar(Masmorras.txt("§a" + preso.getName() + " foi engolido! §7Bata na gosma para ajudar."));
        }

        void bateu(Player p) {
            if (saiu) return;
            if (p.equals(preso)) bateu += 1;
            else bateu += 0;   // so quem esta dentro conta (os outros so dao dano)
            fx(w, Particle.ITEM_SLIME, c.e.getLocation().add(0, c.e.getHeight() / 2, 0), 10, 0.4, 0.4, 0.4, 0.05);
            if (bateu >= precisa) cuspir(false);
            else preso.sendActionBar(Masmorras.txt("§a§lBata! §f" + bateu + "§7/" + precisa));
        }

        void cuspir(boolean tarde) {
            if (saiu) return;
            saiu = true;
            preso.removePotionEffect(PotionEffectType.SLOWNESS);
            Location s = c.e.getLocation().add(0, 0.5, 0);
            Vector v = preso.getLocation().getDirection().setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
            Location fora = c.lugarLivre(s.clone().add(v.normalize().multiply(Math.max(2, c.e.getWidth()))));
            if (fora != null) preso.teleport(fora);
            preso.setVelocity(v.normalize().multiply(0.6).setY(0.45));
            fx(w, Particle.ITEM_SLIME, s, 40, 0.6, 0.6, 0.6, 0.15);
            som(w, s, "minecraft:entity.slime.jump", 2f, 0.6f);
            if (tarde) {
                preso.damage(dano(2), c.e);
                titulo(preso, "§c§lCUSPIDO!", "§7Demorou demais lá dentro", 1500);
            } else titulo(preso, "§a§lVOCÊ SAIU!", "", 1200);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (saiu) return false;
            if (!preso.isOnline() || preso.isDead()) return false;
            Location dentro = c.e.getLocation().add(0, Math.max(0.1, c.e.getHeight() / 2 - 0.9), 0);
            dentro.setYaw(preso.getLocation().getYaw());
            dentro.setPitch(preso.getLocation().getPitch());
            if (preso.getLocation().distanceSquared(dentro) > 0.25) preso.teleport(dentro);
            preso.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 3, false, false));
            if (t % 4 == 0) fx(w, Particle.ITEM_SLIME, dentro.clone().add(0, 1, 0), 6, 0.4, 0.5, 0.4, 0.02);
            if (t % 10 == 9) preso.damage(1 + n * 0.5, c.e);
            if (t >= 80) {
                cuspir(true);
                return false;
            }
            return true;
        }

        @Override
        void fim() {
            if (!saiu && preso.isOnline()) cuspir(false);
        }
    }

    // ================================================================== BURACO (Senhor do Vazio): buraco negro que puxa

    private final class Buraco extends Cena {
        static final int AVISO = 15, DUR = 90;
        final Location centro;
        boolean explodiu;

        Buraco(Luta l) {
            super(l);
            Block b = chaoLivre(l, null, 0, 5);
            if (b == null) throw new IllegalStateException("sem lugar para o buraco");
            centro = b.getLocation().add(0.5, 0.6, 0.5);
            for (Player p : c.naArena()) titulo(p, "§5§lBURACO NEGRO!", "§7Corra para longe — não deixe ele te puxar", 1800);
            som(w, centro, "minecraft:block.portal.trigger", 1.6f, 0.5f);
        }

        @Override
        boolean passo(List<Player> ps) {
            double r = t < AVISO ? 0.4 + t * 0.04 : 1.1 + Math.sin(t * 0.3) * 0.15;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI / 7 + t * 0.35;
                po(w, centro.clone().add(Math.cos(a) * r, Math.sin(a * 2) * 0.3, Math.sin(a) * r), Color.fromRGB(20, 0, 30), 1.6f, 1, 0);
            }
            fx(w, Particle.REVERSE_PORTAL, centro, 8, 0.2, 0.2, 0.2, 0.6);
            fx(w, Particle.SQUID_INK, centro, 2, 0.1, 0.1, 0.1, 0.01);
            if (t < AVISO) return true;
            if (t % 8 == 0) som(w, centro, "minecraft:block.beacon.ambient", 1.6f, 0.5f);
            for (Player p : ps) {
                Vector v = centro.toVector().subtract(p.getLocation().toVector());
                double d = v.length();
                if (d < 1.4) {
                    explodir(ps);
                    return false;
                }
                if (d > 16 || t % 2 != 0) continue;
                Vector puxa = v.normalize().multiply(Math.min(0.2, 0.06 + 0.9 / (d + 2)));
                Vector atual = p.getVelocity();
                p.setVelocity(new Vector(atual.getX() * 0.6 + puxa.getX(), atual.getY(), atual.getZ() * 0.6 + puxa.getZ()));
            }
            if (t >= AVISO + DUR) {
                fx(w, Particle.REVERSE_PORTAL, centro, 80, 0.3, 0.3, 0.3, 1.2);
                som(w, centro, "minecraft:block.beacon.deactivate", 1.6f, 0.6f);
                return false;
            }
            return true;
        }

        void explodir(List<Player> ps) {
            if (explodiu) return;
            explodiu = true;
            w.spawnParticle(Particle.EXPLOSION_EMITTER, centro, 1);
            fx(w, Particle.REVERSE_PORTAL, centro, 120, 1, 1, 1, 1.5);
            som(w, centro, "minecraft:entity.generic.explode", 2f, 0.6f);
            for (Player p : ps) {
                double d = p.getLocation().distance(centro);
                if (d > 5) continue;
                p.damage(dano(6) * (d < 2 ? 1 : 0.5), c.e);
                Vector fora = p.getLocation().toVector().subtract(centro.toVector()).setY(0);
                if (fora.lengthSquared() < 0.01) fora = new Vector(1, 0, 0);
                p.setVelocity(fora.normalize().multiply(1.3).setY(0.6));
            }
        }
    }

    // ================================================================== MARE (Capitao Caveira): o navio afunda

    private final class Mare extends Cena {
        final List<Block> mastros = new ArrayList<>();
        int nivelAgua = -1;

        Mare(Luta l) {
            super(l);
            Caixa a = arena();
            if (a.y2() - a.y1() < 5) throw new IllegalStateException("arena baixa para os mastros");
            int q = Math.max(3, Math.min(5, c.naArena().size() + 2));
            Material tronco = Material.SPRUCE_LOG;
            for (int i = 0; i < q * 3 && mastros.size() < q; i++) {
                Block b = chaoLivre(l, null, 0, 3);
                if (b == null) continue;
                boolean ok = true;
                for (Block m : mastros) if (m.getLocation().distanceSquared(b.getLocation()) < 16) ok = false;
                for (int y = 0; y < 4 && ok; y++) if (!vazio(b.getRelative(0, y, 0))) ok = false;
                if (!ok) continue;
                BlockFace lado = null;
                for (BlockFace f : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    boolean livre = true;
                    for (int y = 0; y < 4; y++) if (!vazio(b.getRelative(f).getRelative(0, y, 0))) livre = false;
                    if (livre) {
                        lado = f;
                        break;
                    }
                }
                if (lado == null) continue;
                for (int y = 0; y < 4; y++) colocar(l, b.getRelative(0, y, 0), tronco, false);
                org.bukkit.block.data.type.Ladder esc = (org.bukkit.block.data.type.Ladder) Material.LADDER.createBlockData();
                esc.setFacing(lado);
                for (int y = 0; y < 4; y++) {
                    Block e = b.getRelative(lado).getRelative(0, y, 0);
                    if (!l.blocos.containsKey(e)) l.blocos.put(e, e.getBlockData());
                    e.setBlockData(esc, false);
                }
                mastros.add(b);
            }
            if (mastros.isEmpty()) throw new IllegalStateException("sem lugar para os mastros");
            for (Player p : c.naArena()) titulo(p, "§9§lO NAVIO ESTÁ AFUNDANDO!", "§7Suba nos mastros pelas escadas", 2200);
            som(w, c.e.getLocation(), "minecraft:block.wood.break", 2f, 0.5f);
            som(w, c.e.getLocation(), "minecraft:ambient.underwater.enter", 2f, 0.6f);
        }

        void subir(int nivel) {
            Caixa a = arena();
            int y = a.y1() + nivel;
            for (int x = a.x1(); x <= a.x2(); x++)
                for (int z = a.z1(); z <= a.z2(); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!vazio(b) || l.blocos.containsKey(b)) continue;
                    l.blocos.put(b, b.getBlockData());
                    b.setType(Material.WATER, false);
                }
            som(w, c.e.getLocation(), "minecraft:block.water.ambient", 2f, 0.6f);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t == 20 || t == 60 || t == 100) subir(++nivelAgua);
            if (t % 10 == 0) {
                for (Player p : ps) {
                    Block cab = p.getEyeLocation().getBlock(), pe = p.getLocation().getBlock();
                    if (cab.getType() == Material.WATER) {
                        p.damage(dano(0), c.e);
                        p.sendActionBar(Masmorras.txt("§9§lAfogando! §7Suba num mastro"));
                    } else if (pe.getType() == Material.WATER) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 1, false, false));
                    }
                }
                for (Block m : mastros) fx(w, Particle.SPLASH, m.getLocation().add(0.5, 4.2, 0.5), 3, 0.3, 0.1, 0.3, 0);
            }
            if (t == 190) for (Player p : ps) p.sendActionBar(Masmorras.txt("§7A água está baixando..."));
            return t < 200;
        }

        @Override
        void fim() {
            // agua que tenha escorrido para fora do que a cena guardou some tambem
            Caixa a = arena().crescer(3);
            Bukkit.getScheduler().runTask(pl, () -> {
                for (int x = a.x1(); x <= a.x2(); x++)
                    for (int y = a.y1(); y <= Math.min(a.y2(), a.y1() + 6); y++)
                        for (int z = a.z1(); z <= a.z2(); z++) {
                            Block b = w.getBlockAt(x, y, z);
                            if (b.getType() == Material.WATER && b.getBlockData() instanceof org.bukkit.block.data.Levelled lv && lv.getLevel() > 0)
                                b.setType(Material.AIR, false);
                        }
            });
        }
    }

    // ================================================================== LIVRO (Bibliotecario): parkour pelas paginas

    private final class Livro extends Cena {
        final List<Player> dentro = new ArrayList<>();
        final Set<UUID> acabou = new HashSet<>();
        final Set<Block> fim = new HashSet<>();
        Location inicio;
        int yPagina;

        Livro(Luta l, List<Player> ps) {
            super(l);
            Caixa a = arena();
            if (a.y2() - a.y1() < 7 || a.x2() - a.x1() < 8 || a.z2() - a.z1() < 8) throw new IllegalStateException("arena pequena para o livro");
            yPagina = a.y1() + 3;
            double x0 = a.x1() + 2, z0 = a.z1() + 2, x1 = a.x2() - 2, z1 = a.z2() - 2;
            double dx = x1 - x0, dz = z1 - z0, len = Math.sqrt(dx * dx + dz * dz);
            int passos = (int) (len / 3.2);
            if (passos < 3) throw new IllegalStateException("arena pequena para o livro");
            for (int i = 0; i <= passos; i++) {
                double f = i / (double) passos;
                int cx = (int) Math.round(x0 + dx * f), cz = (int) Math.round(z0 + dz * f);
                int raio = (i == 0 || i == passos) ? 1 : 0;
                Material mat = i == passos ? Material.GOLD_BLOCK : (i % 2 == 0 ? Material.WHITE_WOOL : Material.BIRCH_PLANKS);
                for (int ox = -raio; ox <= raio + (raio == 0 ? 1 : 0); ox++)
                    for (int oz = -raio; oz <= raio + (raio == 0 ? 1 : 0); oz++) {
                        Block b = w.getBlockAt(cx + ox, yPagina, cz + oz);
                        if (!vazio(b)) continue;
                        colocar(l, b, mat, false);
                        if (i == passos) fim.add(b);
                    }
                if (i == 0) inicio = new Location(w, cx + 0.5, yPagina + 1, cz + 0.5);
            }
            if (fim.isEmpty() || inicio == null) throw new IllegalStateException("sem lugar para as paginas");
            congelar(l);
            l.imuneAte = System.currentTimeMillis() + 28_000;
            for (Player p : ps) {
                dentro.add(p);
                p.teleport(inicio.clone().add(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5));
                titulo(p, "§6§lDENTRO DO LIVRO!", "§7Pule pelas páginas até o §eouro§7. Quem cair volta fraco.", 2600);
            }
            som(w, inicio, "minecraft:item.book.page_turn", 2f, 0.8f);
            som(w, inicio, "minecraft:block.enchantment_table.use", 2f, 0.6f);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t % 5 == 0) for (Block b : fim) fx(w, Particle.ENCHANT, b.getLocation().add(0.5, 1.3, 0.5), 4, 0.3, 0.3, 0.3, 0.5);
            if (t % 15 == 0) som(w, inicio, "minecraft:item.book.page_turn", 1.2f, 1f);
            for (Player p : dentro) {
                if (acabou.contains(p.getUniqueId()) || !p.isOnline() || p.isDead()) continue;
                Block pe = p.getLocation().clone().subtract(0, 0.3, 0).getBlock();
                if (fim.contains(pe)) {
                    acabou.add(p.getUniqueId());
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 20, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 1));
                    titulo(p, "§e§lCHEGOU AO FIM!", "§7Força por 20 s", 1600);
                    som(w, p.getLocation(), "minecraft:entity.player.levelup", 1f, 1.3f);
                } else if (p.getY() < yPagina - 1.5) {
                    acabou.add(p.getUniqueId());
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 15, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 4, 0));
                    titulo(p, "§c§lCAIU DO LIVRO!", "§7Volta fraco por 15 s", 1600);
                }
            }
            boolean todos = true;
            for (Player p : dentro) if (!acabou.contains(p.getUniqueId()) && p.isOnline() && !p.isDead()) todos = false;
            return !todos && t < 260;
        }

        @Override
        void fim() {
            for (Player p : dentro) if (p.isOnline() && p.getY() > yPagina - 0.5) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
            l.imuneAte = 0;
        }
    }

    // ================================================================== VITRAIS (Lorde Vampiro): quebre os vitrais, o sol entra

    private final class Vitrais extends Cena {
        final List<Block> vitrais = new ArrayList<>();
        boolean sol;
        int solDesde;

        Vitrais(Luta l, List<Player> ps) {
            super(l);
            int q = ps.size() >= 3 ? 4 : 3;
            Material m = l.tema.bloco() != null && l.tema.bloco().isBlock() ? l.tema.bloco() : Material.RED_STAINED_GLASS;
            for (int i = 0; i < q * 3 && vitrais.size() < q; i++) {
                Block b = chaoLivre(l, null, 0, 5);
                if (b == null) continue;
                if (!vazio(b.getRelative(BlockFace.UP))) continue;
                colocar(l, b, m, true);
                colocar(l, b.getRelative(BlockFace.UP), m, true);
                vitrais.add(b);
            }
            if (vitrais.isEmpty()) throw new IllegalStateException("sem lugar para os vitrais");
            l.imuneAte = System.currentTimeMillis() + 25_000;
            for (Player p : ps) titulo(p, "§4§lESCURIDÃO!", "§7Quebre os §f" + vitrais.size() + " vitrais §7(clique nos dois blocos) para o sol entrar", 2400);
            som(w, c.e.getLocation(), "minecraft:entity.bat.takeoff", 2f, 0.5f);
        }

        int faltam() {
            int f = 0;
            for (Block b : vitrais) if (l.clicaveis.contains(b) || l.clicaveis.contains(b.getRelative(BlockFace.UP))) f++;
            return f;
        }

        @Override
        void clicou(Player p, Block b) {
            if (!l.clicaveis.contains(b)) return;
            BlockData d = b.getBlockData();
            tirar(l, b);
            w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.1, d);
            som(w, b.getLocation(), "minecraft:block.glass.break", 1.4f, 0.9f);
            int f = faltam();
            for (Player x : c.naArena()) x.sendActionBar(Masmorras.txt(f > 0 ? "§c" + p.getName() + " quebrou um vitral! §7Faltam §f" + f : "§e§lO SOL ENTROU!"));
        }

        @Override
        boolean passo(List<Player> ps) {
            if (!sol && faltam() == 0) {
                sol = true;
                solDesde = t;
                l.imuneAte = 0;
                l.solAte = System.currentTimeMillis() + 10_000;
                l.tontoAte = l.solAte;
                som(w, c.e.getLocation(), "minecraft:block.beacon.power_select", 2f, 1.4f);
                for (Player p : ps) titulo(p, "§e§l☀ SOL!", "§7Agora ele queima — bata com tudo (10 s)", 1800);
            }
            if (!sol) {
                if (t % 4 == 0) for (Block b : vitrais) if (l.clicaveis.contains(b) || l.clicaveis.contains(b.getRelative(BlockFace.UP)))
                    fx(w, Particle.DAMAGE_INDICATOR, b.getLocation().add(0.5, 2.2, 0.5), 1, 0.2, 0.2, 0.2, 0);
                if (t >= 250) {
                    for (Player p : ps) p.sendActionBar(Masmorras.txt("§4A escuridão continua... §7(ele segue quase sem sentir dano fora do sol)"));
                    return false;
                }
                return true;
            }
            return t - solDesde < 100;
        }

        @Override
        void fim() {
            l.imuneAte = 0;
        }
    }

    // ================================================================== MURALHAS (Minotauro): labirinto que muda

    private final class Muralhas extends Cena {
        final List<Block> paredes = new ArrayList<>();
        Investida inv;
        int pausa;

        Muralhas(Luta l) {
            super(l);
            erguer();
            if (paredes.isEmpty()) throw new IllegalStateException("sem lugar para o labirinto");
            for (Player p : c.naArena()) titulo(p, "§6§lLABIRINTO!", "§7Ele só se machuca quando bate numa parede", 2400);
        }

        void erguer() {
            for (Block b : paredes) tirar(l, b);
            paredes.clear();
            Caixa a = arena();
            Material m = l.tema.bloco() != null && l.tema.bloco().isBlock() ? l.tema.bloco() : Material.MOSSY_STONE_BRICKS;
            int q = 5 + Math.min(4, n);
            for (int k = 0; k < q; k++) {
                boolean emX = rnd.nextBoolean();
                int len = 4 + rnd.nextInt(4);
                int x = a.x1() + 2 + rnd.nextInt(Math.max(1, a.x2() - a.x1() - 3));
                int z = a.z1() + 2 + rnd.nextInt(Math.max(1, a.z2() - a.z1() - 3));
                for (int i = 0; i < len; i++) {
                    int bx = emX ? x + i : x, bz = emX ? z : z + i;
                    if (bx >= a.x2() || bz >= a.z2()) break;
                    Location meio = new Location(w, bx + 0.5, a.y1(), bz + 0.5);
                    boolean gente = horiz(meio, c.e.getLocation()) < 2 + c.e.getWidth();
                    for (Player p : w.getPlayers()) if (horiz(meio, p.getLocation()) < 1.6 && Math.abs(p.getY() - a.y1()) < 3) gente = true;
                    if (gente) continue;
                    for (int y = 0; y < 3; y++) {
                        Block b = w.getBlockAt(bx, a.y1() + y, bz);
                        if (!vazio(b) || l.blocos.containsKey(b)) continue;
                        colocar(l, b, m, false);
                        paredes.add(b);
                    }
                }
            }
            som(w, c.e.getLocation(), "minecraft:block.piston.extend", 2f, 0.5f);
            fx(w, Particle.CAMPFIRE_COSY_SMOKE, c.e.getLocation(), 10, 3, 0.2, 3, 0.01);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (t > 0 && t % 100 == 0 && t < 300) {
                erguer();
                for (Player p : ps) p.sendActionBar(Masmorras.txt("§6O labirinto mudou!"));
            }
            if (inv == null) {
                if (pausa-- <= 0) inv = new Investida(l);
            } else {
                boolean segue = inv.passo(ps);
                inv.t++;
                if (!segue) {
                    inv = null;
                    pausa = 12;
                }
            }
            return t < 300;
        }
    }

    // ================================================================== GRAVIDADE (Arquimago): todo mundo vai para o teto

    private final class Gravidade extends Cena {
        final double alvo;

        Gravidade(Luta l) {
            super(l);
            Caixa a = arena();
            if (a.y2() - a.y1() < 5) throw new IllegalStateException("arena baixa para inverter");
            alvo = a.y2() - 2.2;
            c.e.setGravity(false);
            for (Player p : c.naArena()) titulo(p, "§d§lGRAVIDADE INVERTIDA!", "§7A luta continua lá em cima", 2000);
            som(w, c.e.getLocation(), "minecraft:block.beacon.activate", 1.6f, 1.6f);
        }

        @Override
        boolean passo(List<Player> ps) {
            for (Player p : ps) {
                Vector v = p.getVelocity();
                if (p.getY() < alvo - 0.3) v.setY(Math.min(0.45, 0.15 + (alvo - p.getY()) * 0.1));
                else v.setY(Math.max(-0.02, Math.min(0.02, (alvo - p.getY()) * 0.3)));
                p.setVelocity(v);
                p.setFallDistance(0);
                if (t % 4 == 0) fx(w, Particle.REVERSE_PORTAL, p.getLocation(), 3, 0.2, 0.1, 0.2, 0.05);
            }
            Location b = c.e.getLocation();
            double y = Math.min(alvo - Math.max(0, c.e.getHeight() - 2), b.getY() + 0.35);
            if (Math.abs(b.getY() - y) > 0.05) c.e.teleport(new Location(w, b.getX(), y, b.getZ(), b.getYaw(), b.getPitch()));
            c.e.setVelocity(c.e.getVelocity().setY(0));
            return t < 140;
        }

        @Override
        void fim() {
            c.e.setGravity(true);
            for (Player p : c.naArena()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
                p.sendActionBar(Masmorras.txt("§7A gravidade voltou."));
            }
        }
    }

    // ================================================================== ILHAS (Senhor dos Ventos): o chao some

    private final class Ilhas extends Cena {
        final List<Location> ilhas = new ArrayList<>();
        int yChao;

        Ilhas(Luta l) {
            super(l);
            Caixa a = arena();
            if (a.x2() - a.x1() < 9 || a.z2() - a.z1() < 9) throw new IllegalStateException("arena pequena para ilhas");
            yChao = a.y1() - 1;
            ilhas.add(c.e.getLocation().clone());
            for (Player p : c.naArena()) ilhas.add(p.getLocation().clone());
            for (int k = 0; k < 6; k++) {
                Block b = chaoLivre(l, null, 0, 3);
                if (b != null) ilhas.add(b.getLocation().add(0.5, 0, 0.5));
            }
            int tirados = 0;
            for (int x = a.x1(); x <= a.x2(); x++)
                for (int z = a.z1(); z <= a.z2(); z++) {
                    boolean naIlha = false;
                    for (Location il : ilhas) if (Math.abs(il.getBlockX() - x) <= 1 && Math.abs(il.getBlockZ() - z) <= 1) naIlha = true;
                    if (naIlha) continue;
                    // so 2 camadas: quem cai fica dentro da area vigiada (arena + 2) e volta para uma ilha
                    for (int y = yChao; y >= yChao - 1; y--) {
                        Block b = w.getBlockAt(x, y, z);
                        if (b.getType().isAir() || l.blocos.containsKey(b) || b.getState() instanceof org.bukkit.block.TileState) continue;
                        l.blocos.put(b, b.getBlockData());
                        b.setType(Material.AIR, false);
                        tirados++;
                    }
                }
            if (tirados == 0) throw new IllegalStateException("chao nao saiu");
            c.e.setGravity(false);
            for (Player p : c.naArena()) {
                titulo(p, "§f§lO CHÃO SUMIU!", "§7Fique nas ilhas — os ventos empurram", 2200);
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 21, 1, false, false));
            }
            som(w, c.e.getLocation(), "minecraft:entity.breeze.wind_burst", 2f, 0.5f);
        }

        Location ilhaPerto(Location de) {
            Location melhor = null;
            double md = Double.MAX_VALUE;
            for (Location il : ilhas) {
                double d = horiz(il, de);
                if (d < md) {
                    md = d;
                    melhor = il;
                }
            }
            return melhor;
        }

        @Override
        boolean passo(List<Player> ps) {
            c.e.setVelocity(new Vector());
            for (Player p : ps) {
                if (p.getY() < yChao - 0.6) {
                    if (t % 5 == 0) {
                        p.damage(dano(1), c.e);
                        Location il = ilhaPerto(p.getLocation());
                        if (il != null) p.teleport(new Location(w, il.getBlockX() + 0.5, yChao + 1, il.getBlockZ() + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch()));
                        p.sendActionBar(Masmorras.txt("§f§lCaiu no vazio! §7Fique nas ilhas"));
                    }
                }
            }
            if (t > 20 && t % 25 == 0 && !ps.isEmpty()) {
                Player alvo = ps.get(rnd.nextInt(ps.size()));
                double ang = rnd.nextDouble() * Math.PI * 2;
                alvo.setVelocity(new Vector(Math.cos(ang) * 0.9, 0.55, Math.sin(ang) * 0.9));
                fx(w, Particle.GUST, alvo.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
                som(w, alvo.getLocation(), "minecraft:entity.wind_charge.wind_burst", 1.5f, 1f);
            }
            if (t % 6 == 0) for (Location il : ilhas) fx(w, Particle.CLOUD, il.clone().add(0, -0.5, 0), 2, 0.8, 0.1, 0.8, 0.01);
            return t < 210;
        }

        @Override
        void fim() {
            c.e.setGravity(true);
            Caixa a = arena();
            int y1 = a.y1();
            for (Player p : w.getPlayers()) {
                if (p.getX() < a.x1() - 1 || p.getX() > a.x2() + 2 || p.getZ() < a.z1() - 1 || p.getZ() > a.z2() + 2) continue;
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                // o chao volta no proximo instante: quem estiver no buraco sobe para cima do chao (nao fica preso dentro)
                if (p.getY() < y1 - 0.2 && p.getY() > y1 - 6) {
                    Location l2 = p.getLocation().clone();
                    l2.setY(y1);
                    p.teleport(l2);
                    p.setFallDistance(0);
                }
            }
        }
    }

    // ================================================================== TEMPO (Relojoeiro): so um se mexe

    private final class Tempo extends Cena {
        static final int AVISO = 10, PRAZO = 50;
        final Player livre;
        final Map<UUID, Location> parados = new HashMap<>();
        Block engrenagem;
        boolean quebrou;

        Tempo(Luta l, List<Player> ps) {
            super(l);
            livre = ps.get(rnd.nextInt(ps.size()));
            Block b = chaoLivre(l, null, 0, 4);
            if (b == null) throw new IllegalStateException("sem lugar para a engrenagem");
            for (int tent = 0; tent < 8 && b != null && b.getLocation().distance(livre.getLocation()) < 4; tent++) {
                Block o = chaoLivre(l, null, 0, 4);
                if (o != null) b = o;
            }
            engrenagem = b;
            colocar(l, engrenagem, l.tema.bloco() != null && l.tema.bloco().isBlock() ? l.tema.bloco() : Material.GOLD_BLOCK, true);
            for (Player p : ps) if (!p.equals(livre)) parados.put(p.getUniqueId(), p.getLocation().clone());
            congelar(l);
            som(w, c.e.getLocation(), "minecraft:block.bell.use", 2f, 0.5f);
            for (Player p : ps) {
                if (p.equals(livre)) titulo(p, "§e§lO TEMPO PAROU!", "§7Só você se mexe: quebre a §fengrenagem §7em 5 s", 2000);
                else titulo(p, "§e§lO TEMPO PAROU!", "§f" + livre.getName() + " §7tem 5 s para quebrar a engrenagem", 2000);
            }
        }

        @Override
        void clicou(Player p, Block b) {
            if (!b.equals(engrenagem) || quebrou) return;
            if (!p.equals(livre)) return;
            quebrou = true;
            BlockData d = b.getBlockData();
            tirar(l, b);
            w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, 0.1, d);
            som(w, b.getLocation(), "minecraft:block.chain.break", 2f, 0.6f);
            l.tontoAte = System.currentTimeMillis() + 3500;
            for (Player x : c.naArena()) titulo(x, "§a§lO TEMPO VOLTOU!", "§7" + p.getName() + " quebrou a engrenagem — ele está tonto!", 1600);
        }

        @Override
        boolean passo(List<Player> ps) {
            if (quebrou) return false;
            for (Player p : ps) {
                Location ali = parados.get(p.getUniqueId());
                if (ali == null) continue;
                if (p.getLocation().distanceSquared(ali) > 0.04) p.teleport(ali.clone().setDirection(p.getLocation().getDirection()));
                p.setVelocity(new Vector());
                if (t % 5 == 0) fx(w, Particle.WAX_OFF, p.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0);
            }
            if (t % 3 == 0) {
                fx(w, Particle.END_ROD, engrenagem.getLocation().add(0.5, 1.2, 0.5), 2, 0.2, 0.2, 0.2, 0.01);
                if (t % 10 == 0) som(w, livre.getLocation(), "minecraft:block.note_block.hat", 1.5f, 1.5f);
            }
            int falta = AVISO + PRAZO - t;
            if (falta > 0) livre.sendActionBar(Masmorras.txt("§e§lQuebre a engrenagem! §f" + (falta / 10 + 1) + "s"));
            if (t >= AVISO + PRAZO) {
                som(w, c.e.getLocation(), "minecraft:block.anvil.land", 2f, 0.5f);
                for (Player p : ps) {
                    p.damage(dano(3), c.e);
                    p.sendActionBar(Masmorras.txt("§c§lO tempo voltou de uma vez! §7Ninguém quebrou a engrenagem."));
                }
                return false;
            }
            return true;
        }
    }
}
