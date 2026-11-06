package br.servidor.masmorras;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Os tipos de masmorra: onde ficam, nivel, desenho (blocos), monstros e o chefe do fim.
 * Blocos ficam em texto ("minecraft:..."), monstros pelo nome do EntityType: assim da para testar sem servidor.
 */
final class Tipo {

    enum Onde { NORMAL, NETHER, END }

    enum Estilo { SUBTERRANEA, TORRE }

    /** Habilidades dos chefes. */
    enum Hab {
        FLECHAS, INVOCAR, PISAO, INVESTIDA, TELEPORTE, TEIA, BOLAS_FOGO, ANEL_FOGO, CRANIOS, POCOES,
        LEVITAR, PUXAR, CONGELAR, RAIO, TNT, ESCUDO, GRITO, ARREMESSO, RUGIDO, AURA_WITHER, CURA, SUMIR, CEGAR,
        // novas (masmorras da versao 3)
        METEORO, LAMINAS, GELO_PRISAO, ESPINHOS, CORRENTE_RAIO, BURACO_NEGRO, NUVEM_VENENO, CORRENTES, TROCA, ONDA_CHOQUE,
        DRENAR, ENXAME, MARTELO, EXPLOSAO_ARCANA, ROUBAR, TRIDENTES, SANGRAR, VENTANIA, GOSMA
    }

    /** Particularidade do chefe (mecanica propria, alem das habilidades). */
    enum Esp {
        NENHUMA(""),
        ESCUDO_LACAIOS("Ergue um escudo com 70% e 35% da vida: mate os guardas para quebrar."),
        CLONES("Com metade da vida, cria 2 clones de si mesmo."),
        ZONAS("Marca círculos vermelhos no chão: saia deles antes de explodirem!"),
        VAMPIRO("Rouba vida: cura um pouco a cada golpe que acerta."),
        FURIA_TEMPO("Fica mais forte a cada 40 segundos de luta: seja rápido!"),
        REFLEXO("Rebate flechas e projéteis às vezes: lute de perto!"),
        CRISTAIS("Cristais de cura aparecem na arena: quebre os cristais ou ele não para de se curar!"),
        REVIVER("Quando você acha que acabou, ele volta uma vez com 40% da vida."),
        ELEMENTOS("Muda de elemento (fogo, gelo, raio) a cada quarto da vida: cada forma ataca diferente."),
        ESCURIDAO("A arena fica no escuro: ele só aparece de verdade quando os relâmpagos caem."),
        GRAVIDADE("De tempos em tempos a gravidade some: todo mundo flutua e cai devagar."),
        FRENESI("Quanto menos vida ele tem, mais rápido usa as habilidades."),
        ESPINHOS_PELE("Pele de espinhos: quem bate de perto leva parte do dano de volta."),
        ONDAS("Chama ondas de monstros a cada 20 segundos: não deixe acumular!");

        final String desc;

        Esp(String desc) {
            this.desc = desc;
        }
    }

    Esp esp = Esp.NENHUMA;

    final String id, nome, chefeNome;
    final Onde onde;
    final int nivel;
    final Estilo estilo;
    int salas = 4, alturaSala = 5, alturaArena = 9;
    /**
     * Tamanho (versao 2, definido pelo nivel em tamanhos()): grade de celulas (lado), tamanho da celula, meia-largura das
     * salas (minimo e maximo), meia-largura da arena, salas extras do lado do caminho (com tesouro) e meia-largura dos
     * corredores (1 = 3 blocos, 2 = 5 blocos). Torre: meia-largura de cada andar.
     */
    int grade = 4, celula = 20, meiaMin = 4, meiaMax = 6, arenaMeia = 8, ramos = 0, corredor = 1, torreMeia = 8;
    String[] parede, piso, teto;
    String pilar, escada, luz, janela;
    /** Luz pendurada no teto (lanterna) ou no lugar de um bloco do teto (luminaria). */
    boolean luzNoTeto;
    String[] decoPiso = {}, decoTeto = {};
    double chancePiso = 0.15, chanceTeto = 0.08;
    String tabela = "chests/simple_dungeon";
    String[] lacaios;
    String chefe;
    double vida, escala = 1, dano;
    String[] equipamento = {};
    Hab[] habs;
    String[] ajudantes;
    /** Biomas preferidos (parte do nome, ex.: "SNOW", "SWAMP"); vazio = qualquer terra. */
    String[] biomas = {};
    /** Monstro que envenena ao bater. */
    boolean veneno;
    /** Masmorra secreta: nao aparece no mapa, no menu nem na lista. */
    boolean secreta;
    /** Masmorras novas (versao 3): nome curto da placa, nome no mapa e particula do chefe (as antigas usam os switch). */
    String placa, curto, aura;
    /** Tema da reliquia das masmorras novas (as antigas estao no Reliquias). */
    Rel rel;

    record Rel(String de, String cor, int rgb, String particula, String golpe, String golpeNome, String golpeDesc,
               String attr, double valor, boolean pct, String passivoNome, String passivoDesc) {}

    /** Para o TiposNovos registrar as masmorras novas. */
    static Tipo novo(String id, String nome, String chefeNome, Onde onde, int nivel, Estilo estilo) {
        return add(new Tipo(id, nome, chefeNome, onde, nivel, estilo));
    }

    private Tipo(String id, String nome, String chefeNome, Onde onde, int nivel, Estilo estilo) {
        this.id = id;
        this.nome = nome;
        this.chefeNome = chefeNome;
        this.onde = onde;
        this.nivel = nivel;
        this.estilo = estilo;
    }

    static String b(String s) {
        return s.startsWith("minecraft:") ? s : "minecraft:" + s;
    }

    private static String[] bs(String... s) {
        String[] r = new String[s.length];
        for (int i = 0; i < s.length; i++) r[i] = b(s[i]);
        return r;
    }

    /** Sorteio fixo por posicao (a masmorra fica igual se for construida de novo). */
    static String escolher(String[] l, int x, int y, int z, long semente) {
        long h = semente ^ (x * 73428767L) ^ (y * 9122717L) ^ (z * 1500450271L);
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return l[(int) Math.floorMod(h, (long) l.length)];
    }

    static double sorte(int x, int y, int z, long semente) {
        return new Random(semente ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (y * 2654435761L)).nextDouble();
    }

    static final Map<String, Tipo> TODOS = new LinkedHashMap<>();

    static Tipo pegar(String id) {
        return TODOS.get(id);
    }

    static List<Tipo> de(Onde o) {
        List<Tipo> l = new ArrayList<>();
        for (Tipo t : TODOS.values()) if (t.onde == o) l.add(t);
        return l;
    }

    private static Tipo add(Tipo t) {
        TODOS.put(t.id, t);
        return t;
    }

    static {
        Tipo t;
        // ----------------------------------------------------------------- mundo normal
        t = add(new Tipo("CRIPTA", "Cripta dos Mortos", "Rei Esqueleto", Onde.NORMAL, 1, Estilo.SUBTERRANEA));
        t.salas = 4;
        t.parede = bs("stone_bricks", "stone_bricks", "stone_bricks", "mossy_stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks");
        t.piso = bs("polished_andesite", "stone_bricks", "stone_bricks", "mossy_cobblestone");
        t.teto = bs("stone_bricks", "stone_bricks", "mossy_stone_bricks");
        t.pilar = b("chiseled_stone_bricks");
        t.escada = "stone_brick_stairs";
        t.luz = b("soul_lantern[hanging=true]");
        t.decoPiso = bs("cobweb", "skeleton_skull[rotation=4]", "bone_block[axis=y]", "candle[candles=3,lit=true]", "spruce_slab[type=bottom]");
        t.decoTeto = bs("cobweb", "cobweb", "cobweb", "iron_chain[axis=y]");
        t.chancePiso = 0.18;
        t.chanceTeto = 0.10;
        t.tabela = "chests/simple_dungeon";
        t.lacaios = new String[] {"ZOMBIE", "ZOMBIE", "SKELETON", "SKELETON"};
        t.chefe = "SKELETON";
        t.vida = 160;
        t.escala = 1.6;
        t.dano = 6;
        t.equipamento = new String[] {"HEAD:GOLDEN_HELMET", "HAND:BOW"};
        t.habs = new Hab[] {Hab.FLECHAS, Hab.INVOCAR, Hab.TELEPORTE, Hab.ESCUDO};
        t.ajudantes = new String[] {"SKELETON", "ZOMBIE"};

        t = add(new Tipo("NINHO", "Ninho da Aranha", "Rainha Aranha", Onde.NORMAL, 1, Estilo.SUBTERRANEA));
        t.salas = 4;
        t.parede = bs("mossy_cobblestone", "mossy_cobblestone", "mossy_cobblestone", "cobblestone", "cobblestone", "tuff", "coarse_dirt");
        t.piso = bs("coarse_dirt", "coarse_dirt", "rooted_dirt", "moss_block", "dirt");
        t.teto = bs("cobblestone", "mossy_cobblestone", "mossy_cobblestone", "dirt");
        t.pilar = b("mossy_cobblestone");
        t.escada = "mossy_cobblestone_stairs";
        t.luz = b("glow_lichen[up=true]");
        t.decoPiso = bs("cobweb", "cobweb", "cobweb", "cobweb", "bone_block[axis=y]", "skeleton_skull[rotation=0]");
        t.decoTeto = bs("cobweb", "cobweb", "cobweb", "cobweb", "hanging_roots");
        t.chancePiso = 0.25;
        t.chanceTeto = 0.22;
        t.tabela = "chests/simple_dungeon";
        t.lacaios = new String[] {"SPIDER", "SPIDER", "CAVE_SPIDER", "CAVE_SPIDER"};
        t.chefe = "SPIDER";
        t.vida = 140;
        t.escala = 3.0;
        t.dano = 6;
        t.habs = new Hab[] {Hab.TEIA, Hab.INVOCAR, Hab.INVESTIDA};
        t.ajudantes = new String[] {"CAVE_SPIDER"};
        t.veneno = true;

        t = add(new Tipo("MINA", "Mina Abandonada", "Mineiro Gigante", Onde.NORMAL, 2, Estilo.SUBTERRANEA));
        t.salas = 5;
        t.parede = bs("stone", "stone", "stone", "cobbled_deepslate", "cobbled_deepslate", "deepslate", "andesite", "coal_ore", "iron_ore");
        t.piso = bs("cobblestone", "cobblestone", "stone", "stone", "coarse_dirt", "oak_planks");
        t.teto = bs("oak_planks", "oak_planks", "stone", "stone", "spruce_planks");
        t.pilar = b("oak_log[axis=y]");
        t.escada = "cobblestone_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("rail[shape=north_south]", "rail[shape=east_west]", "barrel[facing=up]", "cobweb", "oak_fence");
        t.decoTeto = bs("cobweb", "cobweb", "iron_chain[axis=y]");
        t.chancePiso = 0.15;
        t.tabela = "chests/abandoned_mineshaft";
        t.lacaios = new String[] {"ZOMBIE", "ZOMBIE", "SKELETON", "HUSK"};
        t.chefe = "ZOMBIE";
        t.vida = 260;
        t.escala = 2.2;
        t.dano = 9;
        t.equipamento = new String[] {"HAND:DIAMOND_PICKAXE", "HEAD:IRON_HELMET", "CHEST:IRON_CHESTPLATE"};
        t.habs = new Hab[] {Hab.PISAO, Hab.TNT, Hab.INVOCAR, Hab.INVESTIDA};
        t.ajudantes = new String[] {"ZOMBIE"};

        t = add(new Tipo("PANTANO", "Covil da Bruxa", "Bruxa Anciã", Onde.NORMAL, 2, Estilo.SUBTERRANEA));
        t.salas = 4;
        t.parede = bs("mud_bricks", "mud_bricks", "mud_bricks", "packed_mud", "packed_mud", "dark_oak_planks", "mossy_cobblestone");
        t.piso = bs("moss_block", "moss_block", "dark_oak_planks", "dark_oak_planks", "packed_mud", "mud");
        t.teto = bs("dark_oak_planks", "dark_oak_planks", "mud_bricks", "mud_bricks");
        t.pilar = b("mangrove_log[axis=y]");
        t.escada = "mud_brick_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("cauldron", "brewing_stand", "potted_red_mushroom", "potted_brown_mushroom", "cobweb", "candle[candles=2,lit=true]");
        t.decoTeto = bs("hanging_roots", "hanging_roots", "cobweb", "mangrove_roots");
        t.chancePiso = 0.18;
        t.chanceTeto = 0.15;
        t.tabela = "chests/jungle_temple";
        t.lacaios = new String[] {"ZOMBIE", "SLIME", "SLIME", "WITCH"};
        t.chefe = "WITCH";
        t.vida = 220;
        t.escala = 1.4;
        t.dano = 0;
        t.habs = new Hab[] {Hab.POCOES, Hab.TELEPORTE, Hab.INVOCAR, Hab.CURA};
        t.ajudantes = new String[] {"SLIME"};
        t.biomas = new String[] {"SWAMP"};

        t = add(new Tipo("GELO", "Fortaleza de Gelo", "Rei do Gelo", Onde.NORMAL, 2, Estilo.SUBTERRANEA));
        t.salas = 5;
        t.parede = bs("packed_ice", "packed_ice", "packed_ice", "snow_block", "snow_block", "blue_ice");
        t.piso = bs("packed_ice", "packed_ice", "snow_block", "snow_block", "blue_ice");
        t.teto = bs("packed_ice", "packed_ice", "snow_block");
        t.pilar = b("blue_ice");
        t.escada = "spruce_stairs";
        t.luz = b("soul_lantern[hanging=true]");
        t.decoPiso = bs("snow[layers=2]", "snow[layers=2]", "powder_snow", "blue_ice");
        t.decoTeto = bs("packed_ice");
        t.chancePiso = 0.15;
        t.chanceTeto = 0.05;
        t.tabela = "chests/igloo_chest";
        t.lacaios = new String[] {"STRAY", "STRAY", "STRAY", "ZOMBIE"};
        t.chefe = "STRAY";
        t.vida = 240;
        t.escala = 1.8;
        t.dano = 7;
        t.equipamento = new String[] {"HEAD:GOLDEN_HELMET", "HAND:BOW"};
        t.habs = new Hab[] {Hab.FLECHAS, Hab.CONGELAR, Hab.INVOCAR, Hab.PISAO};
        t.ajudantes = new String[] {"STRAY"};
        t.biomas = new String[] {"SNOW", "FROZEN", "ICE", "GROVE", "JAGGED"};

        t = add(new Tipo("TORRE", "Torre do Arquimago", "Arquimago", Onde.NORMAL, 3, Estilo.TORRE));
        t.salas = 5;
        t.alturaSala = 6;
        t.parede = bs("deepslate_tiles", "deepslate_tiles", "deepslate_tiles", "polished_blackstone_bricks", "polished_blackstone_bricks", "cracked_deepslate_tiles");
        t.piso = bs("dark_oak_planks", "dark_oak_planks", "dark_oak_planks", "spruce_planks");
        t.teto = bs("dark_oak_planks");
        t.pilar = b("dark_oak_log[axis=y]");
        t.janela = b("purple_stained_glass");
        t.escada = "deepslate_tile_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("bookshelf", "bookshelf", "bookshelf", "candle[candles=4,lit=true]", "candle[candles=4,lit=true]", "amethyst_cluster[facing=up]", "cobweb", "enchanting_table");
        t.decoTeto = bs("cobweb", "iron_chain[axis=y]");
        t.chancePiso = 0.22;
        t.chanceTeto = 0.06;
        t.tabela = "chests/woodland_mansion";
        t.lacaios = new String[] {"VINDICATOR", "VINDICATOR", "PILLAGER", "PILLAGER", "WITCH"};
        t.chefe = "EVOKER";
        t.vida = 320;
        t.escala = 1.5;
        t.dano = 8;
        t.habs = new Hab[] {Hab.RAIO, Hab.TELEPORTE, Hab.INVOCAR, Hab.ESCUDO};
        t.ajudantes = new String[] {"VINDICATOR"};

        // ----------------------------------------------------------------- nether
        t = add(new Tipo("FORJA", "Forja Infernal", "Senhor das Chamas", Onde.NETHER, 3, Estilo.SUBTERRANEA));
        t.salas = 4;
        t.parede = bs("polished_blackstone_bricks", "polished_blackstone_bricks", "polished_blackstone_bricks", "blackstone", "blackstone", "basalt[axis=y]", "cracked_polished_blackstone_bricks");
        t.piso = bs("polished_blackstone", "polished_blackstone", "polished_blackstone", "blackstone", "magma_block");
        t.teto = bs("polished_blackstone_bricks", "polished_blackstone_bricks", "basalt[axis=y]");
        t.pilar = b("polished_basalt[axis=y]");
        t.escada = "polished_blackstone_brick_stairs";
        t.luz = b("shroomlight");
        t.luzNoTeto = true;
        t.decoPiso = bs("anvil[facing=north]", "blast_furnace[facing=south]", "smithing_table", "iron_chain[axis=y]");
        t.decoTeto = bs("iron_chain[axis=y]");
        t.chancePiso = 0.12;
        t.chanceTeto = 0.06;
        t.tabela = "chests/nether_bridge";
        t.lacaios = new String[] {"MAGMA_CUBE", "MAGMA_CUBE", "BLAZE"};
        t.chefe = "BLAZE";
        t.vida = 300;
        t.escala = 1.8;
        t.dano = 9;
        t.habs = new Hab[] {Hab.BOLAS_FOGO, Hab.ANEL_FOGO, Hab.INVOCAR, Hab.PISAO};
        t.ajudantes = new String[] {"BLAZE"};

        t = add(new Tipo("BASTIAO", "Bastião Perdido", "Chefe Piglin", Onde.NETHER, 4, Estilo.SUBTERRANEA));
        t.salas = 5;
        t.parede = bs("polished_blackstone_bricks", "polished_blackstone_bricks", "polished_blackstone_bricks", "blackstone", "blackstone", "gilded_blackstone", "cracked_polished_blackstone_bricks");
        t.piso = bs("polished_blackstone", "polished_blackstone", "blackstone", "polished_basalt[axis=y]");
        t.teto = bs("blackstone", "blackstone", "polished_blackstone_bricks");
        t.pilar = b("gilded_blackstone");
        t.escada = "blackstone_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("gold_block", "iron_chain[axis=y]", "magma_block", "basalt[axis=y]");
        t.decoTeto = bs("iron_chain[axis=y]", "iron_chain[axis=y]");
        t.chancePiso = 0.10;
        t.tabela = "chests/bastion_treasure";
        t.lacaios = new String[] {"PIGLIN_BRUTE", "PIGLIN_BRUTE", "PIGLIN", "PIGLIN", "HOGLIN"};
        t.chefe = "PIGLIN_BRUTE";
        t.vida = 420;
        t.escala = 2.0;
        t.dano = 14;
        t.equipamento = new String[] {"HAND:NETHERITE_AXE", "HEAD:GOLDEN_HELMET"};
        t.habs = new Hab[] {Hab.GRITO, Hab.PISAO, Hab.INVESTIDA, Hab.ARREMESSO};
        t.ajudantes = new String[] {"PIGLIN_BRUTE"};

        t = add(new Tipo("WITHER", "Fortaleza Wither", "General Wither", Onde.NETHER, 4, Estilo.SUBTERRANEA));
        t.salas = 5;
        t.parede = bs("nether_bricks", "nether_bricks", "nether_bricks", "nether_bricks", "cracked_nether_bricks", "cracked_nether_bricks", "red_nether_bricks", "chiseled_nether_bricks");
        t.piso = bs("nether_bricks", "nether_bricks", "soul_soil", "soul_soil");
        t.teto = bs("nether_bricks", "nether_bricks", "nether_bricks", "red_nether_bricks");
        t.pilar = b("red_nether_bricks");
        t.escada = "nether_brick_stairs";
        t.luz = b("soul_lantern[hanging=true]");
        t.decoPiso = bs("soul_campfire[lit=true]", "wither_skeleton_skull[rotation=0]", "bone_block[axis=y]", "iron_chain[axis=y]");
        t.decoTeto = bs("iron_chain[axis=y]", "iron_chain[axis=y]", "cobweb");
        t.chancePiso = 0.12;
        t.tabela = "chests/nether_bridge";
        t.lacaios = new String[] {"WITHER_SKELETON", "WITHER_SKELETON", "WITHER_SKELETON", "BLAZE"};
        t.chefe = "WITHER_SKELETON";
        t.vida = 450;
        t.escala = 1.7;
        t.dano = 14;
        t.equipamento = new String[] {"HAND:NETHERITE_SWORD", "HEAD:NETHERITE_HELMET"};
        t.habs = new Hab[] {Hab.CRANIOS, Hab.AURA_WITHER, Hab.INVOCAR, Hab.INVESTIDA};
        t.ajudantes = new String[] {"WITHER_SKELETON"};

        // ----------------------------------------------------------------- end
        t = add(new Tipo("CIDADELA", "Cidadela do Vazio", "Senhor do Vazio", Onde.END, 5, Estilo.TORRE));
        t.salas = 5;
        t.alturaSala = 6;
        t.parede = bs("purpur_block", "purpur_block", "purpur_block", "purpur_pillar[axis=y]", "end_stone_bricks", "end_stone_bricks", "obsidian");
        t.piso = bs("purpur_block", "purpur_block", "end_stone_bricks", "end_stone_bricks");
        t.teto = bs("purpur_block", "purpur_block", "end_stone_bricks");
        t.pilar = b("purpur_pillar[axis=y]");
        t.janela = b("magenta_stained_glass");
        t.escada = "purpur_stairs";
        t.luz = b("end_rod[facing=down]");
        t.decoPiso = bs("purpur_slab[type=bottom]", "end_rod[facing=up]", "obsidian");
        t.decoTeto = bs("end_rod[facing=down]");
        t.chancePiso = 0.10;
        t.chanceTeto = 0.04;
        t.tabela = "chests/end_city_treasure";
        t.lacaios = new String[] {"ENDERMAN", "ENDERMAN", "ENDERMITE", "ENDERMITE", "SHULKER"};
        t.chefe = "ENDERMAN";
        t.vida = 500;
        t.escala = 2.2;
        t.dano = 16;
        t.habs = new Hab[] {Hab.TELEPORTE, Hab.LEVITAR, Hab.PUXAR, Hab.INVOCAR};
        t.ajudantes = new String[] {"ENDERMITE", "ENDERMITE", "ENDERMAN"};

        t = add(new Tipo("PRISAO", "Prisão Abissal", "O Carcereiro", Onde.END, 5, Estilo.SUBTERRANEA));
        t.salas = 5;
        t.parede = bs("deepslate_tiles", "deepslate_tiles", "deepslate_tiles", "deepslate_bricks", "deepslate_bricks", "sculk", "polished_deepslate");
        t.piso = bs("deepslate_tiles", "deepslate_tiles", "sculk", "sculk", "polished_deepslate");
        t.teto = bs("deepslate_tiles", "deepslate_tiles", "sculk");
        t.pilar = b("reinforced_deepslate");
        t.escada = "deepslate_tile_stairs";
        t.luz = b("soul_lantern[hanging=true]");
        t.decoPiso = bs("candle[candles=2,lit=true]", "bone_block[axis=y]", "skeleton_skull[rotation=8]", "sculk_catalyst", "iron_chain[axis=y]");
        t.decoTeto = bs("iron_chain[axis=y]", "iron_chain[axis=y]", "cobweb");
        t.chancePiso = 0.12;
        t.tabela = "chests/ancient_city";
        t.lacaios = new String[] {"VINDICATOR", "ZOMBIE", "SKELETON", "ENDERMAN"};
        t.chefe = "WARDEN";
        t.vida = 500;
        t.escala = 1.0;
        t.dano = 20;
        t.habs = new Hab[] {Hab.RUGIDO, Hab.PUXAR, Hab.PISAO, Hab.INVOCAR};
        t.ajudantes = new String[] {"VINDICATOR"};

        // ----------------------------------------------------------------- novas (versao 2)
        t = add(new Tipo("DESERTO", "Tumba do Faraó", "Faraó", Onde.NORMAL, 2, Estilo.SUBTERRANEA));
        t.parede = bs("cut_sandstone", "cut_sandstone", "sandstone", "sandstone", "smooth_sandstone", "chiseled_sandstone");
        t.piso = bs("smooth_sandstone", "smooth_sandstone", "cut_sandstone", "orange_terracotta");
        t.teto = bs("cut_sandstone", "sandstone", "smooth_sandstone");
        t.pilar = b("chiseled_sandstone");
        t.escada = "sandstone_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("decorated_pot", "decorated_pot", "cobweb", "bone_block[axis=y]", "candle[candles=3,lit=true]", "skeleton_skull[rotation=8]");
        t.decoTeto = bs("cobweb", "cobweb", "iron_chain[axis=y]");
        t.chancePiso = 0.16;
        t.chanceTeto = 0.08;
        t.tabela = "chests/desert_pyramid";
        t.lacaios = new String[] {"HUSK", "HUSK", "HUSK", "SKELETON", "SPIDER"};
        t.chefe = "HUSK";
        t.vida = 260;
        t.escala = 2.0;
        t.dano = 8;
        t.equipamento = new String[] {"HEAD:GOLDEN_HELMET", "HAND:GOLDEN_SWORD"};
        t.habs = new Hab[] {Hab.INVOCAR, Hab.PISAO, Hab.INVESTIDA, Hab.ESCUDO, Hab.TELEPORTE};
        t.ajudantes = new String[] {"HUSK"};
        t.biomas = new String[] {"DESERT", "BADLANDS"};

        t = add(new Tipo("QUARTEL", "Quartel dos Saqueadores", "Devastador", Onde.NORMAL, 3, Estilo.SUBTERRANEA));
        t.parede = bs("dark_oak_planks", "dark_oak_planks", "cobblestone", "cobblestone", "mossy_cobblestone", "spruce_planks");
        t.piso = bs("spruce_planks", "spruce_planks", "dark_oak_planks", "cobblestone", "coarse_dirt");
        t.teto = bs("dark_oak_planks", "dark_oak_planks", "spruce_planks");
        t.pilar = b("dark_oak_log[axis=y]");
        t.escada = "cobblestone_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("barrel[facing=up]", "crafting_table", "fletching_table", "hay_block[axis=y]", "target", "cobweb");
        t.decoTeto = bs("cobweb", "iron_chain[axis=y]");
        t.chancePiso = 0.16;
        t.chanceTeto = 0.06;
        t.tabela = "chests/pillager_outpost";
        t.lacaios = new String[] {"PILLAGER", "PILLAGER", "VINDICATOR", "VINDICATOR", "WITCH"};
        t.chefe = "RAVAGER";
        t.vida = 380;
        t.escala = 1.3;
        t.dano = 12;
        t.habs = new Hab[] {Hab.INVESTIDA, Hab.RUGIDO, Hab.PISAO, Hab.GRITO, Hab.INVOCAR};
        t.ajudantes = new String[] {"VINDICATOR", "PILLAGER"};

        t = add(new Tipo("VENTOS", "Santuário dos Ventos", "Senhor dos Ventos", Onde.NORMAL, 4, Estilo.SUBTERRANEA));
        t.parede = bs("tuff_bricks", "tuff_bricks", "tuff_bricks", "polished_tuff", "chiseled_tuff", "waxed_oxidized_cut_copper");
        t.piso = bs("polished_tuff", "polished_tuff", "tuff_bricks", "waxed_cut_copper");
        t.teto = bs("tuff_bricks", "tuff_bricks", "waxed_oxidized_cut_copper");
        t.pilar = b("chiseled_tuff_bricks");
        t.escada = "tuff_brick_stairs";
        t.luz = b("waxed_copper_bulb[lit=true]");
        t.luzNoTeto = true;
        t.decoPiso = bs("decorated_pot", "waxed_copper_grate", "cobweb", "candle[candles=2,lit=true]");
        t.decoTeto = bs("iron_chain[axis=y]", "cobweb");
        t.chancePiso = 0.12;
        t.chanceTeto = 0.06;
        t.tabela = "chests/trial_chambers/reward";
        t.lacaios = new String[] {"BREEZE", "BOGGED", "BOGGED", "SKELETON"};
        t.chefe = "BREEZE";
        t.vida = 420;
        t.escala = 2.0;
        t.dano = 0;
        t.habs = new Hab[] {Hab.LEVITAR, Hab.PUXAR, Hab.ARREMESSO, Hab.INVOCAR, Hab.TELEPORTE};
        t.ajudantes = new String[] {"BOGGED", "BREEZE"};

        // ----------------------------------------------------------------- secreta
        t = add(new Tipo("SECRETA", "???", "Herobrine", Onde.NORMAL, 5, Estilo.SUBTERRANEA));
        t.secreta = true;
        t.parede = bs("mossy_cobblestone", "mossy_cobblestone", "cobblestone", "netherrack", "netherrack", "obsidian", "cracked_stone_bricks");
        t.piso = bs("netherrack", "netherrack", "cobblestone", "soul_soil", "gold_block");
        t.teto = bs("obsidian", "cobblestone", "netherrack", "crying_obsidian");
        t.pilar = b("obsidian");
        t.escada = "cobblestone_stairs";
        t.luz = b("soul_lantern[hanging=true]");
        t.decoPiso = bs("redstone_torch", "redstone_torch", "netherrack", "gold_block", "skeleton_skull[rotation=8]", "cobweb", "soul_campfire[lit=true]");
        t.decoTeto = bs("cobweb", "iron_chain[axis=y]", "crying_obsidian");
        t.chancePiso = 0.14;
        t.chanceTeto = 0.08;
        t.tabela = "chests/ancient_city";
        t.lacaios = new String[] {"ZOMBIE", "SKELETON", "WITHER_SKELETON", "ENDERMAN", "STRAY"};
        t.chefe = "ZOMBIE";
        t.vida = 700;
        t.escala = 1.0;
        t.dano = 16;
        t.habs = new Hab[] {Hab.SUMIR, Hab.RAIO, Hab.ANEL_FOGO, Hab.CEGAR, Hab.TELEPORTE, Hab.INVOCAR, Hab.LEVITAR};
        t.ajudantes = new String[] {"ZOMBIE", "SKELETON"};

        // ----------------------------------------------------------------- tipos novos (com particularidades)
        t = add(new Tipo("AFOGADA", "Catacumba Afogada", "Rei Afogado", Onde.NORMAL, 2, Estilo.SUBTERRANEA));
        t.parede = bs("prismarine_bricks", "prismarine_bricks", "prismarine", "dark_prismarine", "mossy_stone_bricks");
        t.piso = bs("prismarine_bricks", "dark_prismarine", "sand", "gravel");
        t.teto = bs("prismarine_bricks", "dark_prismarine");
        t.pilar = b("dark_prismarine");
        t.escada = "prismarine_brick_stairs";
        t.luz = b("sea_lantern");
        t.luzNoTeto = true;
        t.decoPiso = bs("seagrass", "kelp_plant", "tube_coral_block", "brain_coral_block", "sea_pickle[pickles=3,waterlogged=false]", "skeleton_skull[rotation=4]");
        t.decoTeto = bs("iron_chain[axis=y]", "hanging_roots");
        t.chancePiso = 0.14;
        t.chanceTeto = 0.06;
        t.tabela = "chests/underwater_ruin_big";
        t.lacaios = new String[] {"DROWNED", "DROWNED", "DROWNED", "ZOMBIE", "BOGGED"};
        t.chefe = "DROWNED";
        t.vida = 280;
        t.escala = 2.0;
        t.dano = 8;
        t.equipamento = new String[] {"HEAD:GOLDEN_HELMET", "HAND:TRIDENT"};
        t.habs = new Hab[] {Hab.PUXAR, Hab.INVOCAR, Hab.INVESTIDA, Hab.PISAO};
        t.ajudantes = new String[] {"DROWNED"};
        t.biomas = new String[] {"BEACH", "SWAMP", "MANGROVE"};
        t.esp = Esp.ESCUDO_LACAIOS;

        t = add(new Tipo("SELVA", "Templo da Selva", "Xamã da Selva", Onde.NORMAL, 3, Estilo.SUBTERRANEA));
        t.parede = bs("mossy_cobblestone", "mossy_cobblestone", "mossy_stone_bricks", "cobblestone", "jungle_planks");
        t.piso = bs("mossy_cobblestone", "moss_block", "coarse_dirt", "mossy_stone_bricks");
        t.teto = bs("mossy_cobblestone", "jungle_planks", "mossy_stone_bricks");
        t.pilar = b("jungle_log[axis=y]");
        t.escada = "mossy_cobblestone_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("fern", "moss_carpet", "cobweb", "bamboo_block[axis=y]", "skeleton_skull[rotation=8]");
        t.decoTeto = bs("vine[south=true]", "hanging_roots", "cobweb");
        t.chancePiso = 0.18;
        t.chanceTeto = 0.12;
        t.tabela = "chests/jungle_temple";
        t.lacaios = new String[] {"SPIDER", "CAVE_SPIDER", "ZOMBIE", "SKELETON", "WITCH"};
        t.chefe = "ILLUSIONER";
        t.vida = 340;
        t.escala = 1.6;
        t.dano = 8;
        t.equipamento = new String[] {"HAND:BOW"};
        t.habs = new Hab[] {Hab.FLECHAS, Hab.TELEPORTE, Hab.CEGAR, Hab.INVOCAR, Hab.TEIA};
        t.ajudantes = new String[] {"SPIDER", "WITCH"};
        t.biomas = new String[] {"JUNGLE", "BAMBOO"};
        t.esp = Esp.CLONES;

        t = add(new Tipo("CASTELO", "Castelo do Carrasco", "Carrasco", Onde.NORMAL, 4, Estilo.SUBTERRANEA));
        t.parede = bs("deepslate_bricks", "deepslate_bricks", "cracked_deepslate_bricks", "polished_blackstone_bricks", "deepslate_tiles");
        t.piso = bs("polished_deepslate", "deepslate_tiles", "polished_blackstone", "deepslate_bricks");
        t.teto = bs("deepslate_bricks", "deepslate_tiles");
        t.pilar = b("chiseled_deepslate");
        t.escada = "deepslate_brick_stairs";
        t.luz = b("lantern[hanging=true]");
        t.decoPiso = bs("iron_bars", "skeleton_skull[rotation=0]", "cobweb", "candle[candles=4,lit=true]", "anvil[facing=north]", "red_carpet");
        t.decoTeto = bs("iron_chain[axis=y]", "iron_chain[axis=y]", "cobweb");
        t.chancePiso = 0.14;
        t.chanceTeto = 0.10;
        t.tabela = "chests/stronghold_corridor";
        t.lacaios = new String[] {"VINDICATOR", "VINDICATOR", "PILLAGER", "SKELETON", "WITHER_SKELETON"};
        t.chefe = "VINDICATOR";
        t.vida = 460;
        t.escala = 1.9;
        t.dano = 12;
        t.equipamento = new String[] {"HEAD:NETHERITE_HELMET", "HAND:NETHERITE_AXE"};
        t.habs = new Hab[] {Hab.INVESTIDA, Hab.GRITO, Hab.PISAO, Hab.INVOCAR, Hab.ARREMESSO};
        t.ajudantes = new String[] {"VINDICATOR", "PILLAGER"};
        t.esp = Esp.FURIA_TEMPO;

        t = add(new Tipo("COVIL", "Covil da Besta", "Besta Zoglin", Onde.NETHER, 4, Estilo.SUBTERRANEA));
        t.parede = bs("blackstone", "blackstone", "polished_blackstone_bricks", "basalt[axis=y]", "nether_bricks");
        t.piso = bs("crimson_nylium", "blackstone", "netherrack", "soul_soil");
        t.teto = bs("blackstone", "nether_bricks", "basalt[axis=y]");
        t.pilar = b("polished_basalt[axis=y]");
        t.escada = "blackstone_stairs";
        t.luz = b("shroomlight");
        t.luzNoTeto = true;
        t.decoPiso = bs("crimson_roots", "bone_block[axis=y]", "nether_wart_block", "skeleton_skull[rotation=4]", "magma_block");
        t.decoTeto = bs("weeping_vines", "iron_chain[axis=y]");
        t.chancePiso = 0.15;
        t.chanceTeto = 0.10;
        t.tabela = "chests/bastion_hoglin_stable";
        t.lacaios = new String[] {"HOGLIN", "PIGLIN_BRUTE", "MAGMA_CUBE", "ZOMBIFIED_PIGLIN"};
        t.chefe = "ZOGLIN";
        t.vida = 480;
        t.escala = 1.8;
        t.dano = 12;
        t.habs = new Hab[] {Hab.INVESTIDA, Hab.RUGIDO, Hab.PISAO, Hab.INVOCAR};
        t.ajudantes = new String[] {"HOGLIN"};
        t.esp = Esp.ZONAS;

        // particularidades dos chefes antigos
        TODOS.get("CRIPTA").esp = Esp.ESCUDO_LACAIOS;
        TODOS.get("MINA").esp = Esp.ZONAS;
        TODOS.get("PANTANO").esp = Esp.VAMPIRO;
        TODOS.get("GELO").esp = Esp.REFLEXO;
        TODOS.get("DESERTO").esp = Esp.ESCUDO_LACAIOS;
        TODOS.get("TORRE").esp = Esp.CLONES;
        TODOS.get("QUARTEL").esp = Esp.FURIA_TEMPO;
        TODOS.get("VENTOS").esp = Esp.REFLEXO;
        TODOS.get("FORJA").esp = Esp.ZONAS;
        TODOS.get("WITHER").esp = Esp.VAMPIRO;
        TODOS.get("CIDADELA").esp = Esp.CLONES;
        TODOS.get("PRISAO").esp = Esp.FURIA_TEMPO;

        // as 40 novas (versao 3): chefes, habilidades e particularidades diferentes
        TiposNovos.registrar();

        for (Tipo x : TODOS.values()) tamanhos(x);
        Tipo sec = TODOS.get("SECRETA");
        sec.grade = 7; sec.celula = 30; sec.salas = 14; sec.ramos = 5; sec.meiaMin = 6; sec.meiaMax = 10;
        sec.arenaMeia = 17; sec.alturaSala = 7; sec.alturaArena = 18; sec.corredor = 2;
    }

    /** Tamanho pelo nivel (versao 2: bem maiores que a primeira, principalmente do nivel 3 para cima). */
    static void tamanhos(Tipo t) {
        switch (t.nivel) {
            case 1 -> { t.grade = 5; t.celula = 22; t.salas = 6; t.meiaMin = 4; t.meiaMax = 7; t.arenaMeia = 10; t.alturaSala = 5; t.alturaArena = 10; t.ramos = 1; t.corredor = 1; }
            case 2 -> { t.grade = 5; t.celula = 24; t.salas = 7; t.meiaMin = 5; t.meiaMax = 8; t.arenaMeia = 11; t.alturaSala = 6; t.alturaArena = 11; t.ramos = 2; t.corredor = 1; }
            case 3 -> { t.grade = 6; t.celula = 26; t.salas = 9; t.meiaMin = 5; t.meiaMax = 9; t.arenaMeia = 13; t.alturaSala = 6; t.alturaArena = 13; t.ramos = 3; t.corredor = 2; }
            case 4 -> { t.grade = 6; t.celula = 28; t.salas = 10; t.meiaMin = 6; t.meiaMax = 10; t.arenaMeia = 14; t.alturaSala = 7; t.alturaArena = 14; t.ramos = 3; t.corredor = 2; }
            default -> { t.grade = 7; t.celula = 28; t.salas = 12; t.meiaMin = 6; t.meiaMax = 10; t.arenaMeia = 15; t.alturaSala = 7; t.alturaArena = 15; t.ramos = 4; t.corredor = 2; }
        }
        // o Nether e pequeno (borda de 1250): grade 5x5
        if (t.onde == Onde.NETHER) {
            t.grade = 5;
            t.salas = Math.min(t.salas, 9);
            t.ramos = Math.min(t.ramos, 2);
        }
        if (t.estilo == Estilo.TORRE) {
            t.torreMeia = t.nivel >= 5 ? 12 : 11;
            t.salas = t.nivel >= 5 ? 8 : 7;
            t.alturaSala = 6;
            t.alturaArena = 12;
            t.ramos = 0;
        }
    }

    /** Metade do lado da area da masmorra (do centro ate a beira da grade ou da torre). */
    int meia() {
        return estilo == Estilo.TORRE ? torreMeia + 2 : grade * celula / 2;
    }

    /** Todos os blocos usados (para conferir no inicio se o Minecraft conhece). */
    List<String> blocos() {
        List<String> l = new ArrayList<>();
        for (String[] a : new String[][] {parede, piso, teto, decoPiso, decoTeto}) if (a != null) for (String s : a) l.add(s);
        for (String s : new String[] {pilar, luz, janela}) if (s != null) l.add(s);
        l.add(b(escada + "[facing=north]"));
        return l;
    }
}
