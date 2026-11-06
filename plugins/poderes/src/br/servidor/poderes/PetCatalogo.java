package br.servidor.poderes;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.potion.PotionEffectType;

/**
 * Poderes de cada pet: cada familia de bicho (aves, abelha, fazenda, cavalos, lhamas, aquaticos, coelho, tatu, felino,
 * raposa, urso polar, panda, cabra) tem os seus 5 poderes (niveis 1, 3, 5, 7 e 9 do pet). Bicho que nao esta na lista
 * usa os 5 poderes de antes (Mordida Feroz, Investida, Uivo Gelado, Cura do Pet, Furia Corona).
 */
final class PetCatalogo {

    private PetCatalogo() {}

    /** O que o poder faz. */
    enum Acao {
        /** golpe no alvo (pode sangrar, envenenar, pegar fogo, empurrar) */
        GOLPE,
        /** acerta todos em volta do alvo */
        AREA,
        /** o pet pula no alvo e acerta em volta */
        INVESTIDA,
        /** acerta todos em volta do pet (empurra para longe dele) */
        ONDA,
        /** cura o dono (vida cheia: escudo) + efeito bom no dono */
        CURA,
        /** efeito bom no dono + golpe */
        BUFF,
        /** 8 s de furia (+50% de dano do pet) + golpe em volta */
        FURIA
    }

    record Poder(String nome, Material icone, String d1, String d2, Acao acao, double mult, double raio,
                 PotionEffectType efeito, int segundos, int forca, Particle part, Sound som, float tom,
                 boolean fogo, boolean gelo, double empurrao, int sangra) {}

    record Familia(String id, String nome, List<Poder> poderes) {}

    private static Poder p(String nome, Material icone, String d1, String d2, Acao acao, double mult, double raio,
                           PotionEffectType efeito, int segundos, int forca, Particle part, Sound som, float tom,
                           boolean fogo, boolean gelo, double empurrao, int sangra) {
        return new Poder(nome, icone, d1, d2, acao, mult, raio, efeito, segundos, forca, part, som, tom, fogo, gelo, empurrao, sangra);
    }

    static final Familia PADRAO = new Familia("padrao", "Pet", List.of(
            p("§c§lMordida Feroz", Material.BONE, "§7Morde forte e faz o monstro sangrar:", "§7golpe extra + 3 de sangramento.",
                    Acao.GOLPE, 1.0, 0, null, 0, 0, Particle.DAMAGE_INDICATOR, Sound.ENTITY_WOLF_GROWL, 0.8f, false, false, 0, 3),
            p("§6§lInvestida", Material.RABBIT_FOOT, "§7Pula no monstro e acerta todos", "§7em volta, jogando eles longe.",
                    Acao.INVESTIDA, 0.8, 3.5, null, 0, 0, Particle.EXPLOSION, Sound.ENTITY_RAVAGER_ATTACK, 1.2f, false, false, 0.9, 0),
            p("§b§lUivo Gelado", Material.SNOWBALL, "§7Uiva e congela os monstros perto:", "§7ficam bem lentos e levam dano.",
                    Acao.ONDA, 0.5, 7, PotionEffectType.SLOWNESS, 4, 2, Particle.SNOWFLAKE, Sound.ENTITY_WOLF_GROWL, 0.7f, false, true, 0, 0),
            p("§a§lCura do Pet", Material.GLISTERING_MELON_SLICE, "§7Cura o dono (3 corações) e dá", "§7regeneração. Vida cheia: escudo.",
                    Acao.CURA, 0.5, 0, null, 0, 0, Particle.HEART, Sound.ENTITY_PLAYER_LEVELUP, 1.8f, false, false, 0, 0),
            p("§e§lFúria Corona", Material.BLAZE_POWDER, "§7Fica furioso por 8 segundos:", "§7bate mais rápido, +50% de dano e fogo.",
                    Acao.FURIA, 0.7, 4, null, 0, 0, Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT, 0.8f, true, false, 0, 0)));

    static final Map<String, Familia> FAMILIAS = new HashMap<>();
    static final Map<String, Familia> POR_BICHO = new HashMap<>();

    private static void familia(Familia f, String... bichos) {
        FAMILIAS.put(f.id(), f);
        for (String b : bichos) POR_BICHO.put(b, f);
    }

    static {
        familia(new Familia("ave", "Ave", List.of(
                p("§c§lBicada", Material.FEATHER, "§7Bica sem parar:", "§7golpe + 3 bicadas seguidas.",
                        Acao.GOLPE, 1.0, 0, null, 0, 0, Particle.CRIT, Sound.ENTITY_CHICKEN_HURT, 1.4f, false, false, 0, 3),
                p("§6§lRasante", Material.ARROW, "§7Voa baixo e passa por cima", "§7de todos, derrubando para longe.",
                        Acao.INVESTIDA, 0.85, 3.5, null, 0, 0, Particle.CLOUD, Sound.ENTITY_PHANTOM_SWOOP, 1.4f, false, false, 1.0, 0),
                p("§f§lChuva de Ovos", Material.EGG, "§7Ovos caem em volta do monstro:", "§7dano em área e todos ficam lentos.",
                        Acao.AREA, 0.6, 4, PotionEffectType.SLOWNESS, 3, 1, Particle.POOF, Sound.ENTITY_CHICKEN_EGG, 0.8f, false, false, 0.3, 0),
                p("§a§lNinho Quentinho", Material.HAY_BLOCK, "§7Cura o dono e dá queda lenta", "§7(nada de dano de queda).",
                        Acao.CURA, 0.4, 0, PotionEffectType.SLOW_FALLING, 10, 0, Particle.HEART, Sound.ENTITY_CHICKEN_AMBIENT, 1.2f, false, false, 0, 0),
                p("§e§lGalo de Briga", Material.BLAZE_POWDER, "§7Vira um galo furioso por 8 s:", "§7+50% de dano e bicadas de fogo.",
                        Acao.FURIA, 0.7, 4, null, 0, 0, Particle.FLAME, Sound.ENTITY_CHICKEN_HURT, 0.6f, true, false, 0, 0))), "CHICKEN", "PARROT");

        familia(new Familia("abelha", "Abelha", List.of(
                p("§6§lFerroada", Material.HONEYCOMB, "§7Pica o monstro e deixa", "§7ele envenenado.",
                        Acao.GOLPE, 1.0, 0, PotionEffectType.POISON, 4, 1, Particle.CRIT, Sound.ENTITY_BEE_STING, 1f, false, false, 0, 0),
                p("§e§lEnxame", Material.BEEHIVE, "§7Chama o enxame: todos em volta", "§7do monstro levam picadas e veneno.",
                        Acao.AREA, 0.55, 4.5, PotionEffectType.POISON, 3, 0, Particle.WAX_ON, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.2f, false, false, 0, 0),
                p("§7§lZumbido", Material.STRING, "§7Zumbe tão alto que os monstros", "§7perto ficam fracos e lentos.",
                        Acao.ONDA, 0.4, 6, PotionEffectType.WEAKNESS, 5, 1, Particle.NOTE, Sound.ENTITY_BEE_POLLINATE, 0.6f, false, false, 0.2, 0),
                p("§a§lMel Curativo", Material.HONEY_BOTTLE, "§7Mel para o dono: cura, regeneração", "§7e mata a fome.",
                        Acao.CURA, 0.4, 0, PotionEffectType.SATURATION, 1, 1, Particle.DRIPPING_HONEY, Sound.ITEM_HONEY_BOTTLE_DRINK, 1.2f, false, false, 0, 0),
                p("§6§lRainha Furiosa", Material.BLAZE_POWDER, "§7Vira a rainha por 8 s: +50%", "§7de dano e ferroadas que queimam.",
                        Acao.FURIA, 0.7, 4, PotionEffectType.POISON, 3, 1, Particle.WAX_OFF, Sound.ENTITY_BEE_HURT, 0.6f, true, false, 0, 0))), "BEE");

        familia(new Familia("fazenda", "Bicho da fazenda", List.of(
                p("§c§lCabeçada", Material.LEATHER, "§7Cabeçada forte que", "§7joga o monstro para trás.",
                        Acao.GOLPE, 1.1, 0, null, 0, 0, Particle.CRIT, Sound.ENTITY_GOAT_RAM_IMPACT, 1.2f, false, false, 1.0, 0),
                p("§6§lEstouro", Material.WHEAT, "§7Sai correndo e atropela", "§7todos em volta do monstro.",
                        Acao.INVESTIDA, 0.85, 3.5, null, 0, 0, Particle.CAMPFIRE_COSY_SMOKE, Sound.ENTITY_RAVAGER_STEP, 1.3f, false, false, 1.0, 0),
                p("§2§lLamaçal", Material.MUD, "§7Espalha lama: os monstros perto", "§7ficam muito lentos.",
                        Acao.ONDA, 0.4, 6, PotionEffectType.SLOWNESS, 5, 2, Particle.SQUID_INK, Sound.BLOCK_MUD_STEP, 0.7f, false, false, 0, 0),
                p("§f§lLeite Fresco", Material.MILK_BUCKET, "§7Cura o dono, tira os efeitos ruins", "§7e dá resistência.",
                        Acao.CURA, 0.4, 0, PotionEffectType.RESISTANCE, 6, 0, Particle.HAPPY_VILLAGER, Sound.ENTITY_COW_MILK, 1f, false, false, 0, 0),
                p("§e§lFúria do Campo", Material.BLAZE_POWDER, "§7Fica bravo por 8 s: +50% de dano", "§7e chifradas de fogo.",
                        Acao.FURIA, 0.7, 4, null, 0, 0, Particle.FLAME, Sound.ENTITY_PIG_HURT, 0.5f, true, false, 0.4, 0))), "PIG", "COW", "SHEEP", "MOOSHROOM");

        familia(new Familia("equino", "Cavalo", List.of(
                p("§c§lCoice", Material.SADDLE, "§7Coice fortíssimo: dano alto", "§7e o monstro voa longe.",
                        Acao.GOLPE, 1.25, 0, null, 0, 0, Particle.SWEEP_ATTACK, Sound.ENTITY_HORSE_ANGRY, 1f, false, false, 1.4, 0),
                p("§6§lGalope", Material.GOLDEN_CARROT, "§7Galopa por cima dos monstros", "§7e derruba todos em volta.",
                        Acao.INVESTIDA, 0.9, 4, null, 0, 0, Particle.CLOUD, Sound.ENTITY_HORSE_GALLOP, 1f, false, false, 1.1, 0),
                p("§7§lRelincho", Material.NOTE_BLOCK, "§7Relincha: os monstros perto", "§7ficam fracos e com medo.",
                        Acao.ONDA, 0.4, 6, PotionEffectType.WEAKNESS, 5, 1, Particle.NOTE, Sound.ENTITY_HORSE_AMBIENT, 0.7f, false, false, 0.6, 0),
                p("§a§lFôlego", Material.APPLE, "§7Cura o dono e dá velocidade", "§7para correr junto.",
                        Acao.CURA, 0.4, 0, PotionEffectType.SPEED, 8, 1, Particle.HAPPY_VILLAGER, Sound.ENTITY_HORSE_BREATHE, 1f, false, false, 0, 0),
                p("§e§lEstouro da Manada", Material.BLAZE_POWDER, "§7Fúria por 8 s: +50% de dano", "§7e coices em chamas.",
                        Acao.FURIA, 0.75, 4.5, null, 0, 0, Particle.FLAME, Sound.ENTITY_HORSE_ANGRY, 0.6f, true, false, 0.6, 0))), "HORSE", "DONKEY", "MULE", "CAMEL", "SKELETON_HORSE", "ZOMBIE_HORSE");

        familia(new Familia("lhama", "Lhama", List.of(
                p("§f§lCuspida", Material.WHITE_WOOL, "§7Cospe no monstro:", "§7dano e ele fica lento.",
                        Acao.GOLPE, 1.0, 0, PotionEffectType.SLOWNESS, 3, 1, Particle.SPIT, Sound.ENTITY_LLAMA_SPIT, 1f, false, false, 0.3, 0),
                p("§6§lInvestida da Lhama", Material.LEAD, "§7Corre e derruba todos", "§7em volta do monstro.",
                        Acao.INVESTIDA, 0.85, 3.5, null, 0, 0, Particle.CLOUD, Sound.ENTITY_LLAMA_ANGRY, 1f, false, false, 1.0, 0),
                p("§b§lChuva de Cuspe", Material.SLIME_BALL, "§7Cospe para todo lado: dano", "§7em área e todos ficam lentos.",
                        Acao.AREA, 0.55, 4.5, PotionEffectType.SLOWNESS, 4, 2, Particle.SPIT, Sound.ENTITY_LLAMA_SPIT, 0.7f, false, false, 0.2, 0),
                p("§a§lTapete Macio", Material.RED_CARPET, "§7Cura o dono e dá escudo", "§7(absorção).",
                        Acao.CURA, 0.4, 0, PotionEffectType.ABSORPTION, 10, 1, Particle.HEART, Sound.ENTITY_LLAMA_CHEST, 1.2f, false, false, 0, 0),
                p("§e§lLhama Furiosa", Material.BLAZE_POWDER, "§7Fúria por 8 s: +50% de dano", "§7e cuspe que queima.",
                        Acao.FURIA, 0.7, 4, null, 0, 0, Particle.FLAME, Sound.ENTITY_LLAMA_ANGRY, 0.6f, true, false, 0, 0))), "LLAMA", "TRADER_LLAMA");

        familia(new Familia("aquatico", "Bicho do mar", List.of(
                p("§9§lJato d'Água", Material.WATER_BUCKET, "§7Jato forte de água:", "§7dano e empurra o monstro.",
                        Acao.GOLPE, 1.0, 0, null, 0, 0, Particle.SPLASH, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, false, false, 1.0, 0),
                p("§8§lTinta Cegante", Material.INK_SAC, "§7Solta tinta: os monstros em volta", "§7ficam lentos e fracos.",
                        Acao.AREA, 0.5, 4.5, PotionEffectType.WEAKNESS, 5, 1, Particle.SQUID_INK, Sound.ENTITY_SQUID_SQUIRT, 1f, false, false, 0, 0),
                p("§3§lRedemoinho", Material.HEART_OF_THE_SEA, "§7Um redemoinho de água em volta", "§7do pet: dano e todos ficam lentos.",
                        Acao.ONDA, 0.55, 6, PotionEffectType.SLOWNESS, 4, 2, Particle.BUBBLE_POP, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, false, false, 0.4, 0),
                p("§b§lBolha Protetora", Material.PRISMARINE_CRYSTALS, "§7Cura o dono e dá respiração", "§7na água e nado rápido.",
                        Acao.CURA, 0.4, 0, PotionEffectType.DOLPHINS_GRACE, 10, 0, Particle.BUBBLE_POP, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2f, false, false, 0, 0),
                p("§1§lTsunami", Material.TRIDENT, "§7Uma onda gigante: dano alto", "§7e joga todos para longe.",
                        Acao.ONDA, 0.9, 7, PotionEffectType.SLOWNESS, 3, 1, Particle.SPLASH, Sound.ENTITY_GENERIC_SPLASH, 0.6f, false, false, 1.4, 0))), "SQUID", "GLOW_SQUID", "DOLPHIN", "TURTLE", "AXOLOTL", "FROG");

        familia(new Familia("coelho", "Coelho", List.of(
                p("§c§lMordidinha", Material.CARROT, "§7Morde rapidinho várias vezes:", "§7golpe + 2 mordidas seguidas.",
                        Acao.GOLPE, 0.9, 0, null, 0, 0, Particle.CRIT, Sound.ENTITY_RABBIT_ATTACK, 1.3f, false, false, 0, 2),
                p("§6§lSalto Duplo", Material.RABBIT_FOOT, "§7Pula em cima dos monstros", "§7e acerta todos em volta.",
                        Acao.INVESTIDA, 0.85, 3.5, null, 0, 0, Particle.POOF, Sound.ENTITY_RABBIT_JUMP, 1f, false, false, 0.8, 0),
                p("§a§lPé de Coelho", Material.RABBIT_HIDE, "§7Sorte: o dono ganha velocidade", "§7e pulo alto.",
                        Acao.BUFF, 0.5, 0, PotionEffectType.JUMP_BOOST, 10, 1, Particle.HAPPY_VILLAGER, Sound.ENTITY_RABBIT_AMBIENT, 1.4f, false, false, 0, 0),
                p("§d§lToca Segura", Material.MOSS_BLOCK, "§7Cura o dono e dá", "§7resistência.",
                        Acao.CURA, 0.4, 0, PotionEffectType.RESISTANCE, 6, 0, Particle.HEART, Sound.ENTITY_RABBIT_AMBIENT, 1f, false, false, 0, 0),
                p("§e§lTurbo", Material.SUGAR, "§7Fúria por 8 s: +50% de dano", "§7e o dono fica super rápido.",
                        Acao.FURIA, 0.7, 4, PotionEffectType.SPEED, 8, 2, Particle.ELECTRIC_SPARK, Sound.ENTITY_RABBIT_ATTACK, 0.7f, false, false, 0, 0))), "RABBIT");

        familia(new Familia("tatu", "Tatu", List.of(
                p("§c§lGarra", Material.ARMADILLO_SCUTE, "§7Arranha forte e faz", "§7o monstro sangrar.",
                        Acao.GOLPE, 1.0, 0, null, 0, 0, Particle.CRIT, Sound.ENTITY_ARMADILLO_ROLL, 1.2f, false, false, 0, 3),
                p("§6§lRolamento", Material.TURTLE_SCUTE, "§7Vira uma bola e rola por cima", "§7de todos em volta.",
                        Acao.INVESTIDA, 0.9, 3.5, null, 0, 0, Particle.CRIT, Sound.ENTITY_ARMADILLO_ROLL, 0.8f, false, false, 1.1, 0),
                p("§7§lEspinhos", Material.CACTUS, "§7Espinhos para todo lado:", "§7dano em todos perto do pet.",
                        Acao.ONDA, 0.6, 5, null, 0, 0, Particle.CRIT, Sound.ENCHANT_THORNS_HIT, 1f, false, false, 0.5, 0),
                p("§8§lCarapaça", Material.ARMADILLO_SCUTE, "§7Cura o dono e dá", "§7resistência forte.",
                        Acao.CURA, 0.4, 0, PotionEffectType.RESISTANCE, 8, 1, Particle.WAX_ON, Sound.ENTITY_ARMADILLO_SCUTE_DROP, 1f, false, false, 0, 0),
                p("§e§lBola de Ferro", Material.BLAZE_POWDER, "§7Fúria por 8 s: +50% de dano", "§7e golpes que jogam longe.",
                        Acao.FURIA, 0.75, 4, null, 0, 0, Particle.EXPLOSION, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, false, false, 0.9, 0))), "ARMADILLO");

        familia(new Familia("felino", "Felino", List.of(
                p("§c§lArranhão", Material.STRING, "§7Arranha e faz o monstro", "§7sangrar.",
                        Acao.GOLPE, 1.05, 0, null, 0, 0, Particle.SWEEP_ATTACK, Sound.ENTITY_CAT_HISS, 1.2f, false, false, 0, 3),
                p("§6§lBote", Material.TROPICAL_FISH, "§7Dá o bote no monstro e", "§7acerta todos em volta.",
                        Acao.INVESTIDA, 0.9, 3.5, null, 0, 0, Particle.SWEEP_ATTACK, Sound.ENTITY_OCELOT_HURT, 1f, false, false, 0.8, 0),
                p("§5§lOlhar Hipnótico", Material.ENDER_EYE, "§7Hipnotiza os monstros perto:", "§7ficam lentos e fracos.",
                        Acao.ONDA, 0.4, 6, PotionEffectType.SLOWNESS, 5, 2, Particle.WITCH, Sound.ENTITY_CAT_PURR, 0.6f, false, false, 0, 0),
                p("§a§lSete Vidas", Material.COD, "§7Cura o dono e dá", "§7regeneração forte.",
                        Acao.CURA, 0.4, 0, PotionEffectType.REGENERATION, 6, 1, Particle.HEART, Sound.ENTITY_CAT_PURREOW, 1f, false, false, 0, 0),
                p("§e§lFúria da Selva", Material.BLAZE_POWDER, "§7Fúria por 8 s: +50% de dano", "§7e unhas em chamas.",
                        Acao.FURIA, 0.75, 4, null, 0, 0, Particle.FLAME, Sound.ENTITY_CAT_HISS, 0.6f, true, false, 0, 0))), "OCELOT", "CAT");

        familia(new Familia("raposa", "Raposa", List.of(
                p("§c§lMordida Ágil", Material.SWEET_BERRIES, "§7Morde rápido duas vezes", "§7(golpe + 2 mordidas).",
                        Acao.GOLPE, 1.0, 0, null, 0, 0, Particle.CRIT, Sound.ENTITY_FOX_BITE, 1.1f, false, false, 0, 2),
                p("§6§lBote Noturno", Material.FOX_SPAWN_EGG, "§7Some na sombra e ataca todos", "§7em volta do monstro.",
                        Acao.INVESTIDA, 0.9, 3.5, null, 0, 0, Particle.LARGE_SMOKE, Sound.ENTITY_FOX_SCREECH, 1f, false, false, 0.8, 0),
                p("§b§lFogo-Fátuo", Material.SOUL_LANTERN, "§7Chamas azuis em volta do monstro:", "§7dano em área e queima.",
                        Acao.AREA, 0.6, 4, null, 0, 0, Particle.SOUL_FIRE_FLAME, Sound.ITEM_FIRECHARGE_USE, 1.3f, true, false, 0, 0),
                p("§a§lEsperteza", Material.GLOW_BERRIES, "§7Cura o dono e dá velocidade", "§7e visão no escuro.",
                        Acao.CURA, 0.4, 0, PotionEffectType.NIGHT_VISION, 20, 0, Particle.HAPPY_VILLAGER, Sound.ENTITY_FOX_SNIFF, 1.2f, false, false, 0, 0),
                p("§6§lNove Caudas", Material.BLAZE_POWDER, "§7Fúria por 8 s: +50% de dano", "§7e chamas azuis em todos perto.",
                        Acao.FURIA, 0.8, 4.5, null, 0, 0, Particle.SOUL_FIRE_FLAME, Sound.ENTITY_FOX_AGGRO, 0.8f, true, false, 0, 0))), "FOX");

        familia(new Familia("urso_polar", "Urso polar", List.of(
                p("§c§lPatada", Material.COD, "§7Patada pesada: dano alto", "§7e joga o monstro longe.",
                        Acao.GOLPE, 1.25, 0, null, 0, 0, Particle.SWEEP_ATTACK, Sound.ENTITY_POLAR_BEAR_WARNING, 1f, false, false, 1.2, 0),
                p("§6§lAbraço de Urso", Material.WHITE_WOOL, "§7Pula e esmaga todos em volta:", "§7ficam tontos (lentos e fracos).",
                        Acao.INVESTIDA, 0.9, 3.5, PotionEffectType.WEAKNESS, 3, 1, Particle.SNOWFLAKE, Sound.ENTITY_POLAR_BEAR_HURT, 0.8f, false, false, 0.6, 0),
                p("§b§lSopro Polar", Material.SNOWBALL, "§7Sopro gelado: congela os", "§7monstros perto.",
                        Acao.ONDA, 0.55, 7, PotionEffectType.SLOWNESS, 5, 3, Particle.SNOWFLAKE, Sound.ENTITY_POLAR_BEAR_WARNING, 0.6f, false, true, 0, 0),
                p("§f§lPelagem Grossa", Material.POWDER_SNOW_BUCKET, "§7Cura o dono e dá", "§7resistência e escudo.",
                        Acao.CURA, 0.4, 0, PotionEffectType.RESISTANCE, 8, 1, Particle.SNOWFLAKE, Sound.ENTITY_POLAR_BEAR_AMBIENT, 1f, false, false, 0, 0),
                p("§b§lAvalanche", Material.PACKED_ICE, "§7Uma avalanche em volta do pet:", "§7dano alto, gelo e empurra.",
                        Acao.ONDA, 0.95, 7, PotionEffectType.SLOWNESS, 4, 2, Particle.SNOWFLAKE, Sound.BLOCK_POWDER_SNOW_BREAK, 0.5f, false, true, 1.2, 0))), "POLAR_BEAR");

        familia(new Familia("panda", "Panda", List.of(
                p("§c§lPatada de Panda", Material.BAMBOO, "§7Patada forte que joga", "§7o monstro para trás.",
                        Acao.GOLPE, 1.2, 0, null, 0, 0, Particle.SWEEP_ATTACK, Sound.ENTITY_PANDA_BITE, 1f, false, false, 1.0, 0),
                p("§6§lCambalhota", Material.BAMBOO_BLOCK, "§7Rola por cima dos monstros", "§7e derruba todos em volta.",
                        Acao.INVESTIDA, 0.9, 3.5, null, 0, 0, Particle.CLOUD, Sound.ENTITY_PANDA_PRE_SNEEZE, 1f, false, false, 1.0, 0),
                p("§a§lEspirro", Material.SLIME_BALL, "§7Um espirro gigante: empurra", "§7e deixa todos perto lentos.",
                        Acao.ONDA, 0.45, 6, PotionEffectType.SLOWNESS, 4, 1, Particle.SNEEZE, Sound.ENTITY_PANDA_SNEEZE, 0.8f, false, false, 1.2, 0),
                p("§2§lLanche de Bambu", Material.CAKE, "§7Cura o dono, mata a fome", "§7e dá regeneração.",
                        Acao.CURA, 0.4, 0, PotionEffectType.SATURATION, 1, 2, Particle.HAPPY_VILLAGER, Sound.ENTITY_PANDA_EAT, 1f, false, false, 0, 0),
                p("§e§lPanda Furioso", Material.BLAZE_POWDER, "§7Fúria por 8 s: +50% de dano", "§7e patadas que jogam longe.",
                        Acao.FURIA, 0.8, 4, null, 0, 0, Particle.ANGRY_VILLAGER, Sound.ENTITY_PANDA_AGGRESSIVE_AMBIENT, 0.8f, false, false, 0.8, 0))), "PANDA");

        familia(new Familia("cabra", "Cabra", List.of(
                p("§c§lChifrada", Material.GOAT_HORN, "§7Chifrada que joga o monstro", "§7muito longe.",
                        Acao.GOLPE, 1.2, 0, null, 0, 0, Particle.CRIT, Sound.ENTITY_GOAT_RAM_IMPACT, 1f, false, false, 1.6, 0),
                p("§6§lEscalada", Material.STONE, "§7Salta das alturas e cai", "§7em cima de todos.",
                        Acao.INVESTIDA, 0.95, 4, null, 0, 0, Particle.POOF, Sound.ENTITY_GOAT_LONG_JUMP, 1f, false, false, 1.0, 0),
                p("§7§lBerro", Material.NOTE_BLOCK, "§7Berra: os monstros perto", "§7ficam tontos (lentos e fracos).",
                        Acao.ONDA, 0.45, 6, PotionEffectType.WEAKNESS, 5, 1, Particle.NOTE, Sound.ENTITY_GOAT_SCREAMING_AMBIENT, 1f, false, false, 0.5, 0),
                p("§f§lLeite de Cabra", Material.MILK_BUCKET, "§7Cura o dono e dá", "§7pulo alto e resistência.",
                        Acao.CURA, 0.4, 0, PotionEffectType.JUMP_BOOST, 10, 1, Particle.HAPPY_VILLAGER, Sound.ENTITY_GOAT_MILK, 1f, false, false, 0, 0),
                p("§b§lDeslizamento", Material.COBBLESTONE, "§7Derruba a montanha: dano alto", "§7e joga todos perto para longe.",
                        Acao.ONDA, 0.95, 7, PotionEffectType.SLOWNESS, 3, 1, Particle.EXPLOSION, Sound.ENTITY_GOAT_SCREAMING_RAM_IMPACT, 0.7f, false, false, 1.5, 0))), "GOAT");
    }

    /** Familia do pet pelo tipo do bicho. */
    static Familia de(Entity pet) {
        if (pet == null) return PADRAO;
        Familia f = POR_BICHO.get(pet.getType().name().toUpperCase(Locale.ROOT));
        return f == null ? PADRAO : f;
    }
}
