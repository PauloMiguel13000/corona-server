package br.servidor.masmorras;

import br.servidor.masmorras.Tipo.Esp;
import br.servidor.masmorras.Tipo.Estilo;
import br.servidor.masmorras.Tipo.Hab;
import br.servidor.masmorras.Tipo.Onde;

/**
 * As 40 masmorras novas (versao 3, 04/11/2026): 30 no mundo normal, 5 no Nether e 5 no End. Cada uma tem chefe,
 * habilidades, particularidade, cara (paleta de blocos) e reliquia proprios. No mapa fica no maximo 2 de cada tipo.
 */
final class TiposNovos {

    private static Tipo n(String id, String nome, String chefe, Onde o, int nivel, Estilo e, String paleta, String placa, String curto) {
        Tipo t = Tipo.novo(id, nome, chefe, o, nivel, e);
        paleta(t, paleta);
        t.placa = placa;
        t.curto = curto;
        return t;
    }

    private static String[] m(String... s) {
        return s;
    }

    private static Hab[] h(Hab... s) {
        return s;
    }

    private static void chefe(Tipo t, String mob, double vida, double escala, double dano, String... equip) {
        t.chefe = mob;
        t.vida = vida;
        t.escala = escala;
        t.dano = dano;
        t.equipamento = equip;
    }

    private static void rel(Tipo t, String de, String cor, int rgb, String part, String golpe, String gn, String gd,
                            String attr, double valor, boolean pct, String pn, String pd) {
        t.rel = new Tipo.Rel(de, cor, rgb, part, golpe, gn, gd, attr, valor, pct, pn, pd);
    }

    private static String[] bs(String... s) {
        String[] r = new String[s.length];
        for (int i = 0; i < s.length; i++) r[i] = Tipo.b(s[i]);
        return r;
    }

    /** Paletas de blocos (parede, piso, teto, pilar, escada, luz, enfeites e tabela dos baus). */
    static void paleta(Tipo t, String p) {
        switch (p) {
            case "ossos" -> {
                t.parede = bs("smooth_stone", "smooth_stone", "calcite", "calcite", "bone_block[axis=y]", "cobblestone", "mossy_cobblestone");
                t.piso = bs("coarse_dirt", "podzol", "cobblestone", "gravel", "bone_block[axis=y]");
                t.teto = bs("smooth_stone", "cobblestone", "calcite");
                t.pilar = Tipo.b("bone_block[axis=y]");
                t.escada = "cobblestone_stairs";
                t.luz = Tipo.b("soul_lantern[hanging=true]");
                t.decoPiso = bs("skeleton_skull[rotation=4]", "skeleton_skull[rotation=10]", "candle[candles=3,lit=true]", "cobweb", "bone_block[axis=x]", "dead_bush");
                t.decoTeto = bs("cobweb", "cobweb", "iron_chain[axis=y]");
                t.chancePiso = 0.16;
                t.chanceTeto = 0.10;
                t.tabela = "chests/simple_dungeon";
            }
            case "musgo" -> {
                t.parede = bs("mossy_stone_bricks", "mossy_stone_bricks", "mossy_cobblestone", "mossy_cobblestone", "stone_bricks", "moss_block");
                t.piso = bs("moss_block", "moss_block", "rooted_dirt", "mossy_cobblestone", "mud");
                t.teto = bs("mossy_stone_bricks", "mossy_cobblestone", "moss_block");
                t.pilar = Tipo.b("oak_log[axis=y]");
                t.escada = "mossy_stone_brick_stairs";
                t.luz = Tipo.b("ochre_froglight");
                t.luzNoTeto = true;
                t.decoPiso = bs("moss_carpet", "moss_carpet", "flowering_azalea", "azalea", "slime_block", "cobweb");
                t.decoTeto = bs("hanging_roots", "hanging_roots", "glow_lichen[down=true]", "cobweb");
                t.chancePiso = 0.18;
                t.chanceTeto = 0.14;
                t.tabela = "chests/jungle_temple";
            }
            case "madeira" -> {
                t.parede = bs("spruce_planks", "spruce_planks", "stripped_spruce_log[axis=y]", "dark_oak_planks", "dark_oak_planks", "cobblestone");
                t.piso = bs("spruce_planks", "spruce_planks", "dark_oak_planks", "oak_planks", "coarse_dirt");
                t.teto = bs("spruce_planks", "dark_oak_planks", "stripped_dark_oak_log[axis=x]");
                t.pilar = Tipo.b("spruce_log[axis=y]");
                t.escada = "spruce_stairs";
                t.luz = Tipo.b("lantern[hanging=true]");
                t.decoPiso = bs("barrel[facing=up]", "barrel[facing=up]", "hay_block[axis=y]", "crafting_table", "cobweb", "candle[candles=2,lit=true]", "fletching_table");
                t.decoTeto = bs("cobweb", "iron_chain[axis=y]");
                t.chancePiso = 0.15;
                t.chanceTeto = 0.07;
                t.tabela = "chests/pillager_outpost";
            }
            case "micelio" -> {
                t.parede = bs("mushroom_stem", "mushroom_stem", "brown_mushroom_block", "red_mushroom_block", "mycelium", "dirt");
                t.piso = bs("mycelium", "mycelium", "podzol", "coarse_dirt", "rooted_dirt");
                t.teto = bs("brown_mushroom_block", "red_mushroom_block", "mushroom_stem");
                t.pilar = Tipo.b("mushroom_stem");
                t.escada = "mud_brick_stairs";
                t.luz = Tipo.b("shroomlight");
                t.luzNoTeto = true;
                t.decoPiso = bs("red_mushroom", "brown_mushroom", "red_mushroom", "brown_mushroom", "cobweb", "potted_red_mushroom");
                t.decoTeto = bs("hanging_roots", "cobweb");
                t.chancePiso = 0.22;
                t.chanceTeto = 0.08;
                t.tabela = "chests/simple_dungeon";
            }
            case "gelo_azul" -> {
                t.parede = bs("packed_ice", "packed_ice", "blue_ice", "snow_block", "snow_block", "polished_diorite");
                t.piso = bs("snow_block", "snow_block", "packed_ice", "polished_diorite");
                t.teto = bs("packed_ice", "snow_block", "blue_ice");
                t.pilar = Tipo.b("blue_ice");
                t.escada = "polished_diorite_stairs";
                t.luz = Tipo.b("sea_lantern");
                t.luzNoTeto = true;
                t.decoPiso = bs("powder_snow_cauldron[level=3]", "snow[layers=2]", "snow[layers=1]", "bone_block[axis=y]", "skeleton_skull[rotation=6]");
                t.decoTeto = bs("cobweb", "iron_chain[axis=y]");
                t.chancePiso = 0.14;
                t.chanceTeto = 0.05;
                t.tabela = "chests/igloo_chest";
            }
            case "palido" -> {
                t.parede = bs("pale_oak_planks", "pale_oak_planks", "stripped_pale_oak_log[axis=y]", "resin_bricks", "pale_moss_block");
                t.piso = bs("pale_moss_block", "pale_moss_block", "pale_oak_planks", "coarse_dirt");
                t.teto = bs("pale_oak_planks", "pale_oak_planks", "pale_moss_block");
                t.pilar = Tipo.b("pale_oak_log[axis=y]");
                t.escada = "resin_brick_stairs";
                t.luz = Tipo.b("lantern[hanging=true]");
                t.decoPiso = bs("pale_moss_carpet", "pale_moss_carpet", "resin_block", "cobweb", "candle[candles=1,lit=true]");
                t.decoTeto = bs("pale_hanging_moss", "pale_hanging_moss", "cobweb");
                t.chancePiso = 0.18;
                t.chanceTeto = 0.16;
                t.tabela = "chests/woodland_mansion";
            }
            case "prismarinho" -> {
                t.parede = bs("prismarine", "prismarine", "prismarine_bricks", "prismarine_bricks", "dark_prismarine", "mossy_stone_bricks");
                t.piso = bs("prismarine_bricks", "prismarine_bricks", "dark_prismarine", "sand", "gravel");
                t.teto = bs("prismarine", "prismarine_bricks", "dark_prismarine");
                t.pilar = Tipo.b("dark_prismarine");
                t.escada = "prismarine_brick_stairs";
                t.luz = Tipo.b("sea_lantern");
                t.luzNoTeto = true;
                t.decoPiso = bs("dead_brain_coral_block", "dead_tube_coral_block", "wet_sponge", "sea_pickle[pickles=4,waterlogged=false]", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]", "cobweb");
                t.chancePiso = 0.14;
                t.chanceTeto = 0.05;
                t.tabela = "chests/underwater_ruin_big";
            }
            case "cobre" -> {
                t.parede = bs("waxed_oxidized_copper", "waxed_weathered_copper", "waxed_oxidized_cut_copper", "waxed_exposed_cut_copper", "tuff_bricks");
                t.piso = bs("waxed_oxidized_cut_copper", "waxed_weathered_cut_copper", "polished_tuff", "waxed_oxidized_copper_grate");
                t.teto = bs("waxed_weathered_copper", "waxed_oxidized_copper", "tuff_bricks");
                t.pilar = Tipo.b("waxed_oxidized_cut_copper");
                t.escada = "waxed_oxidized_cut_copper_stairs";
                t.luz = Tipo.b("waxed_copper_bulb[lit=true]");
                t.luzNoTeto = true;
                t.decoPiso = bs("lightning_rod[facing=up]", "waxed_copper_bulb[lit=true]", "brewing_stand", "cauldron", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]", "lightning_rod[facing=down]");
                t.chancePiso = 0.13;
                t.chanceTeto = 0.06;
                t.tabela = "chests/trial_chambers/corridor";
            }
            case "tufo" -> {
                t.parede = bs("tuff_bricks", "tuff_bricks", "polished_tuff", "chiseled_tuff", "waxed_oxidized_cut_copper");
                t.piso = bs("polished_tuff", "polished_tuff", "tuff_bricks", "waxed_oxidized_copper_grate");
                t.teto = bs("tuff_bricks", "polished_tuff");
                t.pilar = Tipo.b("chiseled_tuff_bricks");
                t.escada = "tuff_brick_stairs";
                t.luz = Tipo.b("waxed_copper_bulb[lit=true]");
                t.luzNoTeto = true;
                t.decoPiso = bs("decorated_pot", "decorated_pot", "waxed_copper_bulb[lit=true]", "cobweb", "iron_bars");
                t.decoTeto = bs("iron_chain[axis=y]");
                t.chancePiso = 0.12;
                t.chanceTeto = 0.05;
                t.tabela = "chests/trial_chambers/reward";
            }
            case "feno" -> {
                t.parede = bs("oak_planks", "oak_planks", "stripped_oak_log[axis=y]", "cobblestone", "mud_bricks");
                t.piso = bs("coarse_dirt", "dirt_path", "oak_planks", "rooted_dirt", "farmland");
                t.teto = bs("oak_planks", "spruce_planks", "hay_block[axis=x]");
                t.pilar = Tipo.b("oak_log[axis=y]");
                t.escada = "oak_stairs";
                t.luz = Tipo.b("lantern[hanging=true]");
                t.decoPiso = bs("hay_block[axis=y]", "hay_block[axis=y]", "carved_pumpkin[facing=north]", "composter", "cobweb", "jack_o_lantern[facing=south]");
                t.decoTeto = bs("cobweb", "cobweb", "iron_chain[axis=y]");
                t.chancePiso = 0.18;
                t.chanceTeto = 0.10;
                t.tabela = "chests/village/village_plains_house";
            }
            case "basalto" -> {
                t.parede = bs("basalt[axis=y]", "basalt[axis=y]", "polished_basalt[axis=y]", "smooth_basalt", "blackstone", "magma_block");
                t.piso = bs("smooth_basalt", "smooth_basalt", "polished_blackstone", "magma_block", "blackstone");
                t.teto = bs("basalt[axis=y]", "smooth_basalt", "blackstone");
                t.pilar = Tipo.b("polished_basalt[axis=y]");
                t.escada = "polished_blackstone_stairs";
                t.luz = Tipo.b("shroomlight");
                t.luzNoTeto = true;
                t.decoPiso = bs("magma_block", "campfire[lit=true]", "iron_chain[axis=y]", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]");
                t.chancePiso = 0.11;
                t.chanceTeto = 0.05;
                t.tabela = "chests/ruined_portal";
            }
            case "cerejeira" -> {
                t.parede = bs("cherry_planks", "cherry_planks", "stripped_cherry_log[axis=y]", "pink_terracotta", "white_terracotta");
                t.piso = bs("cherry_planks", "cherry_planks", "moss_block", "pink_terracotta");
                t.teto = bs("cherry_planks", "stripped_cherry_wood[axis=y]");
                t.pilar = Tipo.b("cherry_log[axis=y]");
                t.escada = "cherry_stairs";
                t.luz = Tipo.b("lantern[hanging=true]");
                t.decoPiso = bs("pink_petals[flower_amount=4,facing=north]", "pink_petals[flower_amount=2,facing=east]", "potted_cherry_sapling", "candle[candles=2,lit=true]", "bamboo_block[axis=y]");
                t.decoTeto = bs("cherry_leaves[persistent=true]", "iron_chain[axis=y]");
                t.chancePiso = 0.20;
                t.chanceTeto = 0.08;
                t.tabela = "chests/simple_dungeon";
            }
            case "quartzo" -> {
                t.parede = bs("quartz_bricks", "quartz_bricks", "smooth_quartz", "chiseled_quartz_block", "white_terracotta");
                t.piso = bs("smooth_quartz", "quartz_bricks", "polished_diorite", "white_concrete");
                t.teto = bs("quartz_block", "smooth_quartz");
                t.pilar = Tipo.b("quartz_pillar[axis=y]");
                t.escada = "quartz_stairs";
                t.luz = Tipo.b("sea_lantern");
                t.luzNoTeto = true;
                t.decoPiso = bs("white_candle[candles=4,lit=true]", "white_candle[candles=2,lit=true]", "end_rod[facing=up]", "lectern[facing=north]", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]", "end_rod[facing=down]");
                t.chancePiso = 0.13;
                t.chanceTeto = 0.05;
                t.tabela = "chests/stronghold_corridor";
            }
            case "ouro" -> {
                t.parede = bs("smooth_sandstone", "smooth_sandstone", "cut_sandstone", "chiseled_sandstone", "raw_gold_block", "gold_block");
                t.piso = bs("smooth_sandstone", "cut_sandstone", "yellow_terracotta", "raw_gold_block");
                t.teto = bs("smooth_sandstone", "cut_sandstone");
                t.pilar = Tipo.b("chiseled_sandstone");
                t.escada = "smooth_sandstone_stairs";
                t.luz = Tipo.b("lantern[hanging=true]");
                t.decoPiso = bs("gold_block", "raw_gold_block", "decorated_pot", "candle[candles=4,lit=true]", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]");
                t.chancePiso = 0.12;
                t.chanceTeto = 0.05;
                t.tabela = "chests/buried_treasure";
            }
            case "tijolo" -> {
                t.parede = bs("bricks", "bricks", "bricks", "granite", "polished_granite", "cracked_stone_bricks");
                t.piso = bs("polished_granite", "polished_granite", "bricks", "sand", "coarse_dirt");
                t.teto = bs("bricks", "polished_granite");
                t.pilar = Tipo.b("chiseled_stone_bricks");
                t.escada = "brick_stairs";
                t.luz = Tipo.b("lantern[hanging=true]");
                t.decoPiso = bs("flower_pot", "decorated_pot", "anvil[facing=north]", "cobweb", "iron_bars", "candle[candles=3,lit=true]");
                t.decoTeto = bs("iron_chain[axis=y]", "cobweb");
                t.chancePiso = 0.12;
                t.chanceTeto = 0.06;
                t.tabela = "chests/village/village_weaponsmith";
            }
            case "ametista" -> {
                t.parede = bs("calcite", "calcite", "amethyst_block", "smooth_basalt", "deepslate_tiles");
                t.piso = bs("calcite", "amethyst_block", "smooth_basalt", "polished_deepslate");
                t.teto = bs("smooth_basalt", "deepslate_tiles", "amethyst_block");
                t.pilar = Tipo.b("amethyst_block");
                t.escada = "deepslate_tile_stairs";
                t.luz = Tipo.b("pearlescent_froglight");
                t.luzNoTeto = true;
                t.decoPiso = bs("amethyst_cluster[facing=up]", "large_amethyst_bud[facing=up]", "medium_amethyst_bud[facing=up]", "end_rod[facing=up]", "cobweb");
                t.decoTeto = bs("amethyst_cluster[facing=down]", "large_amethyst_bud[facing=down]");
                t.chancePiso = 0.16;
                t.chanceTeto = 0.10;
                t.tabela = "chests/stronghold_library";
            }
            case "sculk" -> {
                t.parede = bs("deepslate_bricks", "deepslate_bricks", "deepslate_tiles", "sculk", "cracked_deepslate_bricks", "polished_deepslate");
                t.piso = bs("sculk", "sculk", "deepslate_tiles", "polished_deepslate");
                t.teto = bs("deepslate_tiles", "sculk", "deepslate_bricks");
                t.pilar = Tipo.b("chiseled_deepslate");
                t.escada = "deepslate_brick_stairs";
                t.luz = Tipo.b("soul_lantern[hanging=true]");
                t.decoPiso = bs("sculk_vein[down=true]", "candle[candles=3,lit=true]", "skeleton_skull[rotation=2]", "sculk_catalyst", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]", "cobweb", "sculk_vein[up=true]");
                t.chancePiso = 0.14;
                t.chanceTeto = 0.08;
                t.tabela = "chests/ancient_city";
            }
            case "sangue" -> {
                t.parede = bs("red_nether_bricks", "red_nether_bricks", "nether_bricks", "polished_blackstone_bricks", "crimson_planks");
                t.piso = bs("red_nether_bricks", "polished_blackstone", "crimson_planks", "nether_wart_block");
                t.teto = bs("red_nether_bricks", "nether_bricks");
                t.pilar = Tipo.b("crimson_stem[axis=y]");
                t.escada = "red_nether_brick_stairs";
                t.luz = Tipo.b("red_candle[candles=4,lit=true]");
                t.decoPiso = bs("red_candle[candles=3,lit=true]", "red_candle[candles=1,lit=true]", "skeleton_skull[rotation=0]", "cobweb", "redstone_wire");
                t.decoTeto = bs("iron_chain[axis=y]", "cobweb", "weeping_vines");
                t.chancePiso = 0.15;
                t.chanceTeto = 0.10;
                t.tabela = "chests/woodland_mansion";
            }
            case "carmesim" -> {
                t.parede = bs("crimson_planks", "crimson_planks", "stripped_crimson_stem[axis=y]", "nether_wart_block", "netherrack");
                t.piso = bs("crimson_planks", "crimson_nylium", "netherrack", "nether_wart_block");
                t.teto = bs("nether_wart_block", "crimson_planks");
                t.pilar = Tipo.b("crimson_stem[axis=y]");
                t.escada = "crimson_stairs";
                t.luz = Tipo.b("shroomlight");
                t.luzNoTeto = true;
                t.decoPiso = bs("crimson_fungus", "crimson_roots", "crimson_roots", "nether_sprouts", "bone_block[axis=y]");
                t.decoTeto = bs("weeping_vines", "weeping_vines", "iron_chain[axis=y]");
                t.chancePiso = 0.18;
                t.chanceTeto = 0.14;
                t.tabela = "chests/bastion_hoglin_stable";
            }
            case "distorcido" -> {
                t.parede = bs("warped_planks", "warped_planks", "stripped_warped_stem[axis=y]", "warped_wart_block", "netherrack");
                t.piso = bs("warped_planks", "warped_nylium", "netherrack", "warped_wart_block");
                t.teto = bs("warped_wart_block", "warped_planks");
                t.pilar = Tipo.b("warped_stem[axis=y]");
                t.escada = "warped_stairs";
                t.luz = Tipo.b("shroomlight");
                t.luzNoTeto = true;
                t.decoPiso = bs("warped_fungus", "warped_roots", "warped_roots", "nether_sprouts", "twisting_vines");
                t.decoTeto = bs("iron_chain[axis=y]", "iron_chain[axis=y]");
                t.chancePiso = 0.18;
                t.chanceTeto = 0.06;
                t.tabela = "chests/bastion_other";
            }
            case "almas" -> {
                t.parede = bs("soul_soil", "basalt[axis=y]", "basalt[axis=y]", "polished_blackstone_bricks", "blackstone");
                t.piso = bs("soul_soil", "soul_soil", "soul_sand", "polished_blackstone");
                t.teto = bs("basalt[axis=y]", "blackstone", "polished_blackstone_bricks");
                t.pilar = Tipo.b("polished_basalt[axis=y]");
                t.escada = "blackstone_stairs";
                t.luz = Tipo.b("soul_lantern[hanging=true]");
                t.decoPiso = bs("soul_campfire[lit=true]", "soul_torch", "bone_block[axis=y]", "skeleton_skull[rotation=12]", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]", "iron_chain[axis=y]");
                t.chancePiso = 0.14;
                t.chanceTeto = 0.06;
                t.tabela = "chests/nether_bridge";
            }
            case "obsidiana" -> {
                t.parede = bs("obsidian", "obsidian", "crying_obsidian", "blackstone", "polished_blackstone_bricks");
                t.piso = bs("polished_blackstone", "polished_blackstone", "obsidian", "blackstone");
                t.teto = bs("obsidian", "polished_blackstone_bricks");
                t.pilar = Tipo.b("crying_obsidian");
                t.escada = "polished_blackstone_stairs";
                t.luz = Tipo.b("soul_lantern[hanging=true]");
                t.decoPiso = bs("respawn_anchor[charges=4]", "soul_campfire[lit=true]", "iron_chain[axis=y]", "cobweb");
                t.decoTeto = bs("iron_chain[axis=y]");
                t.chancePiso = 0.10;
                t.chanceTeto = 0.05;
                t.tabela = "chests/ruined_portal";
            }
            case "fimpedra" -> {
                t.parede = bs("end_stone_bricks", "end_stone_bricks", "end_stone", "purpur_block", "obsidian");
                t.piso = bs("end_stone_bricks", "end_stone", "purpur_block", "obsidian");
                t.teto = bs("end_stone", "end_stone_bricks");
                t.pilar = Tipo.b("purpur_pillar[axis=y]");
                t.janela = Tipo.b("magenta_stained_glass");
                t.escada = "end_stone_brick_stairs";
                t.luz = Tipo.b("end_rod[facing=down]");
                t.decoPiso = bs("end_rod[facing=up]", "dragon_head[rotation=0]", "purpur_slab[type=bottom]", "obsidian");
                t.decoTeto = bs("end_rod[facing=down]");
                t.chancePiso = 0.10;
                t.chanceTeto = 0.05;
                t.tabela = "chests/end_city_treasure";
            }
            default -> throw new IllegalArgumentException("paleta " + p);
        }
    }

    static void registrar() {
        Tipo t;
        // =========================================================== mundo normal, nivel 1
        t = n("CEMITERIO", "Cemitério Esquecido", "O Coveiro", Onde.NORMAL, 1, Estilo.SUBTERRANEA, "ossos", "CEMITÉRIO", "Cemitério");
        t.lacaios = m("ZOMBIE", "ZOMBIE", "ZOMBIE_VILLAGER", "SKELETON");
        chefe(t, "ZOMBIE", 150, 1.7, 6, "HAND:IRON_SHOVEL", "HEAD:LEATHER_HELMET");
        t.habs = h(Hab.INVOCAR, Hab.CORRENTES, Hab.SANGRAR, Hab.PUXAR);
        t.ajudantes = m("ZOMBIE_VILLAGER", "ZOMBIE");
        t.esp = Esp.REVIVER;
        t.aura = "SOUL";
        rel(t, "do Coveiro", "§7", 0x9A9A9A, "SOUL", "OSSOS", "Pá do Coveiro", "deixa o alvo lento e fraco", "armor", 0.5, false, "Terra Batida", "+0,5 de armadura");

        t = n("ESGOTO", "Esgoto Gosmento", "Gosma Primordial", Onde.NORMAL, 1, Estilo.SUBTERRANEA, "musgo", "ESGOTO", "Esgoto");
        t.lacaios = m("SLIME", "SLIME", "ZOMBIE", "CAVE_SPIDER");
        chefe(t, "SLIME", 160, 1.0, 6);
        t.habs = h(Hab.GOSMA, Hab.PISAO, Hab.NUVEM_VENENO, Hab.INVOCAR);
        t.ajudantes = m("SLIME");
        t.esp = Esp.ONDAS;
        t.aura = "ITEM_SLIME";
        rel(t, "da Gosma Primordial", "§a", 0x7CCB5B, "ITEM_SLIME", "VENENO", "Gosma Ácida", "envenena o alvo", "jump_strength", 0.04, false, "Pulo de Gosma", "pula mais alto");

        t = n("ACAMPAMENTO", "Acampamento Saqueador", "Capitão Saqueador", Onde.NORMAL, 1, Estilo.SUBTERRANEA, "madeira", "ACAMPAMENTO", "Acampamento");
        t.lacaios = m("PILLAGER", "PILLAGER", "VINDICATOR", "ZOMBIE");
        chefe(t, "PILLAGER", 150, 1.6, 6, "HAND:CROSSBOW", "HEAD:WHITE_BANNER");
        t.habs = h(Hab.FLECHAS, Hab.INVOCAR, Hab.CORRENTES, Hab.SANGRAR);
        t.ajudantes = m("PILLAGER", "VINDICATOR");
        t.esp = Esp.FRENESI;
        t.aura = "CRIT";
        rel(t, "do Capitão Saqueador", "§8", 0x6B6B6B, "CRIT", "INVESTIDA", "Saque", "joga o alvo longe", "attack_damage", 0.3, false, "Olho de Capitão", "+0,3 de dano");

        t = n("MOINHO", "Moinho Assombrado", "O Espantalho", Onde.NORMAL, 1, Estilo.SUBTERRANEA, "feno", "MOINHO", "Moinho");
        t.lacaios = m("SKELETON", "ZOMBIE", "SPIDER", "SKELETON");
        chefe(t, "SKELETON", 140, 1.8, 6, "HEAD:CARVED_PUMPKIN", "HAND:BOW");
        t.habs = h(Hab.FLECHAS, Hab.TROCA, Hab.NUVEM_VENENO, Hab.ENXAME);
        t.ajudantes = m("SKELETON");
        t.esp = Esp.ESPINHOS_PELE;
        t.aura = "WHITE_ASH";
        t.biomas = new String[] {"PLAINS", "MEADOW", "SUNFLOWER"};
        rel(t, "do Espantalho", "§6", 0xE0A030, "WHITE_ASH", "ILUSAO", "Susto do Espantalho", "cega o alvo", "movement_speed", 0.02, true, "Pernas de Palha", "anda 2% mais rápido");

        t = n("FUNGOS", "Gruta dos Cogumelos", "Rei Fungo", Onde.NORMAL, 1, Estilo.SUBTERRANEA, "micelio", "GRUTA FUNGOS", "Fungos");
        t.lacaios = m("BOGGED", "BOGGED", "SPIDER", "ZOMBIE");
        chefe(t, "BOGGED", 150, 1.8, 6, "HAND:BOW", "HEAD:RED_MUSHROOM_BLOCK");
        t.habs = h(Hab.NUVEM_VENENO, Hab.FLECHAS, Hab.GOSMA, Hab.INVOCAR);
        t.ajudantes = m("BOGGED");
        t.esp = Esp.CRISTAIS;
        t.aura = "SPORE_BLOSSOM_AIR";
        t.veneno = true;
        rel(t, "do Rei Fungo", "§c", 0xC04040, "SPORE_BLOSSOM_AIR", "VENENO", "Esporos", "envenena o alvo", "max_health", 1.0, false, "Pele de Cogumelo", "+½ coração de vida");

        t = n("COVIL_LOBOS", "Covil dos Lobos", "Lobo Alfa", Onde.NORMAL, 1, Estilo.SUBTERRANEA, "musgo", "COVIL LOBOS", "Lobos");
        t.lacaios = m("ZOMBIE", "SKELETON", "SPIDER", "ZOMBIE");
        chefe(t, "WOLF", 150, 2.2, 7);
        t.habs = h(Hab.INVESTIDA, Hab.SANGRAR, Hab.RUGIDO, Hab.INVOCAR);
        t.ajudantes = m("ZOMBIE", "SKELETON");
        t.esp = Esp.FRENESI;
        t.aura = "CLOUD";
        t.biomas = new String[] {"TAIGA", "FOREST", "GROVE"};
        rel(t, "do Lobo Alfa", "§f", 0xDDDDDD, "CLOUD", "SANGRAR", "Mordida", "o alvo sangra", "movement_speed", 0.025, true, "Faro do Alfa", "anda 2,5% mais rápido");

        // =========================================================== mundo normal, nivel 2
        t = n("URSO", "Caverna do Urso Polar", "Urso Ancestral", Onde.NORMAL, 2, Estilo.SUBTERRANEA, "gelo_azul", "CAVERNA URSO", "Urso");
        t.lacaios = m("STRAY", "STRAY", "ZOMBIE", "SKELETON");
        chefe(t, "POLAR_BEAR", 260, 2.0, 9);
        t.habs = h(Hab.PISAO, Hab.INVESTIDA, Hab.GELO_PRISAO, Hab.RUGIDO);
        t.ajudantes = m("STRAY");
        t.esp = Esp.FRENESI;
        t.aura = "SNOWFLAKE";
        t.biomas = new String[] {"SNOW", "ICE", "FROZEN"};
        rel(t, "do Urso Ancestral", "§b", 0xBFE6FF, "SNOWFLAKE", "CONGELAR", "Garra Gelada", "congela o alvo", "knockback_resistance", 0.1, false, "Pelagem Grossa", "é menos empurrado");

        t = n("BOSQUE_PALIDO", "Bosque Pálido", "O Rangedor", Onde.NORMAL, 2, Estilo.SUBTERRANEA, "palido", "BOSQUE PÁLIDO", "Pálido");
        t.lacaios = m("ZOMBIE", "SKELETON", "SPIDER", "STRAY");
        chefe(t, "CREAKING", 280, 1.4, 9);
        t.habs = h(Hab.CORRENTES, Hab.ESPINHOS, Hab.TROCA, Hab.NUVEM_VENENO);
        t.ajudantes = m("SKELETON", "ZOMBIE");
        t.esp = Esp.ESCURIDAO;
        t.aura = "WHITE_ASH";
        t.biomas = new String[] {"PALE", "DARK_FOREST"};
        rel(t, "do Rangedor", "§7", 0xBBBBAA, "WHITE_ASH", "MALDICAO", "Rangido", "um efeito ruim no alvo", "safe_fall_distance", 1.0, false, "Galhos Leves", "cai 1 bloco a mais sem dano");

        t = n("FAROL", "Farol Naufragado", "Capitã Fantasma", Onde.NORMAL, 2, Estilo.TORRE, "prismarinho", "FAROL", "Farol");
        t.lacaios = m("DROWNED", "DROWNED", "BOGGED", "ZOMBIE");
        chefe(t, "DROWNED", 260, 1.8, 8, "HAND:TRIDENT", "HEAD:CHAINMAIL_HELMET");
        t.habs = h(Hab.TRIDENTES, Hab.PUXAR, Hab.ONDA_CHOQUE, Hab.INVOCAR);
        t.ajudantes = m("DROWNED");
        t.esp = Esp.CRISTAIS;
        t.aura = "DRIPPING_WATER";
        t.biomas = new String[] {"BEACH", "OCEAN", "SHORE"};
        rel(t, "da Capitã Fantasma", "§3", 0x2FA6B5, "DRIPPING_WATER", "CORRENTEZA", "Maré", "puxa o alvo para você", "oxygen_bonus", 1.0, false, "Fôlego Fantasma", "fica mais tempo debaixo d'água");

        t = n("LABORATORIO", "Laboratório Alquímico", "Alquimista Louca", Onde.NORMAL, 2, Estilo.SUBTERRANEA, "cobre", "LABORATÓRIO", "Laboratório");
        t.lacaios = m("WITCH", "ZOMBIE_VILLAGER", "ZOMBIE", "SPIDER");
        chefe(t, "WITCH", 240, 1.7, 7);
        t.habs = h(Hab.POCOES, Hab.NUVEM_VENENO, Hab.GOSMA, Hab.EXPLOSAO_ARCANA);
        t.ajudantes = m("WITCH", "ZOMBIE_VILLAGER");
        t.esp = Esp.ELEMENTOS;
        t.aura = "WITCH";
        rel(t, "da Alquimista Louca", "§5", 0x9A4FD0, "WITCH", "MALDICAO", "Frasco Instável", "um efeito ruim no alvo", "luck", 1.0, false, "Fórmula da Sorte", "+1 de sorte");

        t = n("CAMARA_TESTES", "Câmara dos Testes", "Sentinela de Cobre", Onde.NORMAL, 2, Estilo.SUBTERRANEA, "tufo", "CÂMARA TESTES", "Testes");
        t.lacaios = m("BREEZE", "ZOMBIE", "SKELETON", "SPIDER");
        chefe(t, "IRON_GOLEM", 300, 1.2, 9);
        t.habs = h(Hab.MARTELO, Hab.ONDA_CHOQUE, Hab.VENTANIA, Hab.ARREMESSO);
        t.ajudantes = m("BREEZE");
        t.esp = Esp.ESPINHOS_PELE;
        t.aura = "ELECTRIC_SPARK";
        rel(t, "da Sentinela de Cobre", "§6", 0xC87533, "ELECTRIC_SPARK", "ESMAGAR", "Punho de Cobre", "dano extra e empurra", "armor_toughness", 0.5, false, "Chapa de Cobre", "+0,5 de resistência da armadura");

        t = n("FAZENDA", "Fazenda Maldita", "Fazendeiro Zumbi", Onde.NORMAL, 2, Estilo.SUBTERRANEA, "feno", "FAZENDA", "Fazenda");
        t.lacaios = m("ZOMBIE_VILLAGER", "ZOMBIE", "HUSK", "SPIDER");
        chefe(t, "ZOMBIE_VILLAGER", 260, 1.9, 8, "HAND:IRON_HOE", "HEAD:LEATHER_HELMET");
        t.habs = h(Hab.SANGRAR, Hab.ENXAME, Hab.INVOCAR, Hab.GOSMA);
        t.ajudantes = m("ZOMBIE_VILLAGER", "ZOMBIE");
        t.esp = Esp.ONDAS;
        t.aura = "COMPOSTER";
        t.biomas = new String[] {"PLAINS", "SAVANNA"};
        rel(t, "do Fazendeiro Zumbi", "§2", 0x6E8B3D, "COMPOSTER", "SANGRAR", "Foice Enferrujada", "o alvo sangra", "block_break_speed", 0.08, true, "Mãos de Lavrador", "quebra blocos 8% mais rápido");

        t = n("CRATERA", "Cratera do Meteoro", "O Caído", Onde.NORMAL, 2, Estilo.SUBTERRANEA, "basalto", "CRATERA", "Cratera");
        t.lacaios = m("MAGMA_CUBE", "MAGMA_CUBE", "HUSK", "SKELETON");
        chefe(t, "MAGMA_CUBE", 260, 1.0, 8);
        t.habs = h(Hab.METEORO, Hab.ANEL_FOGO, Hab.PISAO, Hab.BOLAS_FOGO);
        t.ajudantes = m("MAGMA_CUBE");
        t.esp = Esp.REVIVER;
        t.aura = "FLAME";
        rel(t, "do Caído", "§c", 0xFF6A2A, "FLAME", "CHAMAS", "Fragmento de Meteoro", "põe fogo no alvo", "burning_time", -0.15, false, "Casca de Meteoro", "queima por menos tempo");

        t = n("JARDIM", "Jardim das Cerejeiras", "Ronin Espectral", Onde.NORMAL, 2, Estilo.SUBTERRANEA, "cerejeira", "JARDIM", "Jardim");
        t.lacaios = m("SKELETON", "ZOMBIE", "STRAY", "SPIDER");
        chefe(t, "VINDICATOR", 260, 1.6, 9, "HAND:IRON_SWORD");
        t.habs = h(Hab.LAMINAS, Hab.SANGRAR, Hab.TROCA, Hab.TELEPORTE);
        t.ajudantes = m("SKELETON", "STRAY");
        t.esp = Esp.FRENESI;
        t.aura = "CHERRY_LEAVES";
        t.biomas = new String[] {"CHERRY"};
        rel(t, "do Ronin Espectral", "§d", 0xF4A7C6, "CHERRY_LEAVES", "SANGRAR", "Corte da Pétala", "o alvo sangra", "attack_speed", 0.05, true, "Postura do Ronin", "ataca 5% mais rápido");

        // =========================================================== mundo normal, nivel 3
        t = n("CATEDRAL", "Catedral Profana", "Bispo Corrompido", Onde.NORMAL, 3, Estilo.TORRE, "quartzo", "CATEDRAL", "Catedral");
        t.lacaios = m("ZOMBIE_VILLAGER", "SKELETON", "VINDICATOR", "WITCH");
        chefe(t, "ZOMBIE_VILLAGER", 340, 1.9, 9, "HAND:GOLDEN_SWORD", "HEAD:GOLDEN_HELMET");
        t.habs = h(Hab.EXPLOSAO_ARCANA, Hab.DRENAR, Hab.CURA, Hab.CORRENTE_RAIO);
        t.ajudantes = m("ZOMBIE_VILLAGER", "VINDICATOR");
        t.esp = Esp.CRISTAIS;
        t.aura = "END_ROD";
        rel(t, "do Bispo Corrompido", "§f", 0xFFF7D6, "END_ROD", "ARCANO", "Luz Profana", "descarga com dano extra", "max_health", 1.0, false, "Bênção Falsa", "+½ coração de vida");

        t = n("TESOURO", "Câmara do Tesouro", "Rei Dourado", Onde.NORMAL, 3, Estilo.SUBTERRANEA, "ouro", "TESOURO", "Tesouro");
        t.lacaios = m("PIGLIN", "HUSK", "ZOMBIE", "SKELETON");
        chefe(t, "PIGLIN", 340, 1.9, 10, "HAND:GOLDEN_SWORD", "HEAD:GOLDEN_HELMET", "CHEST:GOLDEN_CHESTPLATE");
        t.habs = h(Hab.ROUBAR, Hab.ARREMESSO, Hab.GRITO, Hab.TRIDENTES);
        t.ajudantes = m("PIGLIN", "HUSK");
        t.esp = Esp.ESPINHOS_PELE;
        t.aura = "WAX_ON";
        rel(t, "do Rei Dourado", "§e", 0xFFD34A, "WAX_ON", "GANANCIA", "Ganância Real", "você ganha Força e dano extra", "luck", 1.5, false, "Coroa da Sorte", "+1,5 de sorte");

        t = n("COLISEU", "Coliseu Perdido", "Gladiador Imortal", Onde.NORMAL, 3, Estilo.SUBTERRANEA, "tijolo", "COLISEU", "Coliseu");
        t.lacaios = m("VINDICATOR", "ZOMBIE", "SKELETON", "HUSK");
        chefe(t, "ZOMBIE", 360, 2.0, 10, "HAND:IRON_SWORD", "OFF_HAND:SHIELD", "HEAD:IRON_HELMET", "CHEST:IRON_CHESTPLATE");
        t.habs = h(Hab.MARTELO, Hab.LAMINAS, Hab.INVESTIDA, Hab.GRITO);
        t.ajudantes = m("VINDICATOR", "ZOMBIE");
        t.esp = Esp.REVIVER;
        t.aura = "CRIT";
        rel(t, "do Gladiador Imortal", "§c", 0xB83A2B, "CRIT", "ESTOURO", "Fúria da Arena", "acerta os monstros em volta", "max_absorption", 2.0, false, "Glória", "+1 coração de absorção");

        t = n("TROVAO", "Templo do Trovão", "Arauto do Trovão", Onde.NORMAL, 3, Estilo.TORRE, "cobre", "TEMPLO TROVÃO", "Trovão");
        t.lacaios = m("SKELETON", "STRAY", "WITCH", "VINDICATOR");
        chefe(t, "SKELETON", 330, 1.9, 9, "HAND:BOW", "HEAD:CHAINMAIL_HELMET");
        t.habs = h(Hab.CORRENTE_RAIO, Hab.RAIO, Hab.VENTANIA, Hab.TROCA);
        t.ajudantes = m("STRAY", "SKELETON");
        t.esp = Esp.ELEMENTOS;
        t.aura = "ELECTRIC_SPARK";
        rel(t, "do Arauto do Trovão", "§e", 0xF5F05A, "ELECTRIC_SPARK", "ARCANO", "Descarga", "choque com dano extra", "entity_interaction_range", 0.25, false, "Braço de Raio", "acerta um pouco mais longe");

        t = n("OBSERVATORIO", "Observatório Estelar", "O Astrólogo", Onde.NORMAL, 3, Estilo.TORRE, "ametista", "OBSERVATÓRIO", "Estrelas");
        t.lacaios = m("ENDERMAN", "VINDICATOR", "WITCH", "SKELETON");
        chefe(t, "ILLUSIONER", 320, 1.7, 9, "HAND:BOW");
        t.habs = h(Hab.METEORO, Hab.BURACO_NEGRO, Hab.LEVITAR, Hab.TELEPORTE);
        t.ajudantes = m("VINDICATOR", "ENDERMAN");
        t.esp = Esp.GRAVIDADE;
        t.aura = "ENCHANT";
        rel(t, "do Astrólogo", "§d", 0xB98CFF, "ENCHANT", "VAZIO", "Toque Estelar", "o alvo flutua", "gravity", -0.004, false, "Passo Lunar", "cai mais devagar");

        t = n("VIUVA", "Ninho Profundo", "Viúva Negra", Onde.NORMAL, 3, Estilo.SUBTERRANEA, "ossos", "NINHO FUNDO", "Viúva");
        t.lacaios = m("CAVE_SPIDER", "CAVE_SPIDER", "SPIDER", "SKELETON");
        chefe(t, "CAVE_SPIDER", 320, 4.0, 9);
        t.habs = h(Hab.TEIA, Hab.NUVEM_VENENO, Hab.CORRENTES, Hab.ENXAME);
        t.ajudantes = m("CAVE_SPIDER");
        t.esp = Esp.ONDAS;
        t.aura = "SMOKE";
        t.veneno = true;
        rel(t, "da Viúva Negra", "§8", 0x3A1F1F, "SMOKE", "VENENO", "Picada da Viúva", "envenena o alvo", "step_height", 0.25, false, "Oito Patas", "sobe degraus mais altos");

        t = n("NAUFRAGIO", "Navio Fantasma", "Capitão Caveira", Onde.NORMAL, 3, Estilo.SUBTERRANEA, "madeira", "NAVIO FANTASMA", "Navio");
        t.lacaios = m("DROWNED", "SKELETON", "BOGGED", "ZOMBIE");
        chefe(t, "SKELETON", 340, 2.0, 10, "HAND:IRON_SWORD", "HEAD:GOLDEN_HELMET");
        t.habs = h(Hab.SANGRAR, Hab.ARREMESSO, Hab.FLECHAS, Hab.ONDA_CHOQUE);
        t.ajudantes = m("DROWNED", "SKELETON");
        t.esp = Esp.FRENESI;
        t.aura = "BUBBLE_POP";
        t.biomas = new String[] {"OCEAN", "BEACH"};
        rel(t, "do Capitão Caveira", "§3", 0x3D6E7A, "BUBBLE_POP", "CORRENTEZA", "Âncora", "puxa o alvo e deixa lento", "oxygen_bonus", 1.0, false, "Pulmão de Pirata", "fica mais tempo debaixo d'água");

        t = n("MAUSOLEU", "Mausoléu Real", "Rainha Múmia", Onde.NORMAL, 3, Estilo.SUBTERRANEA, "ouro", "MAUSOLÉU", "Mausoléu");
        t.lacaios = m("HUSK", "HUSK", "SKELETON", "SPIDER");
        chefe(t, "HUSK", 340, 2.0, 10, "HEAD:GOLDEN_HELMET");
        t.habs = h(Hab.NUVEM_VENENO, Hab.CORRENTES, Hab.SUMIR, Hab.ROUBAR);
        t.ajudantes = m("HUSK");
        t.esp = Esp.REVIVER;
        t.aura = "WHITE_ASH";
        t.biomas = new String[] {"DESERT", "BADLANDS"};
        rel(t, "da Rainha Múmia", "§e", 0xE8D49A, "WHITE_ASH", "AREIA", "Bandagem Amaldiçoada", "cega o alvo e dá fome", "max_health", 1.0, false, "Eterna", "+½ coração de vida");

        t = n("BIBLIOTECA", "Biblioteca Proibida", "O Bibliotecário", Onde.NORMAL, 3, Estilo.TORRE, "madeira", "BIBLIOTECA", "Biblioteca");
        t.lacaios = m("VINDICATOR", "WITCH", "SKELETON", "ZOMBIE");
        chefe(t, "VINDICATOR", 330, 1.7, 9, "HAND:BOOK");
        t.habs = h(Hab.EXPLOSAO_ARCANA, Hab.LEVITAR, Hab.TELEPORTE, Hab.ENXAME);
        t.ajudantes = m("VINDICATOR", "WITCH");
        t.esp = Esp.CRISTAIS;
        t.aura = "ENCHANT";
        rel(t, "do Bibliotecário", "§5", 0x7A4FB0, "ENCHANT", "ARCANO", "Página Proibida", "descarga mágica", "entity_interaction_range", 0.25, false, "Leitura Rápida", "acerta um pouco mais longe");

        // =========================================================== mundo normal, nivel 4
        t = n("VULCAO", "Coração do Vulcão", "Titã de Magma", Onde.NORMAL, 4, Estilo.SUBTERRANEA, "basalto", "VULCÃO", "Vulcão");
        t.lacaios = m("MAGMA_CUBE", "BLAZE", "WITHER_SKELETON", "HUSK");
        chefe(t, "IRON_GOLEM", 470, 1.8, 13);
        t.habs = h(Hab.METEORO, Hab.ANEL_FOGO, Hab.MARTELO, Hab.ONDA_CHOQUE);
        t.ajudantes = m("MAGMA_CUBE", "BLAZE");
        t.esp = Esp.ZONAS;
        t.aura = "LAVA";
        rel(t, "do Titã de Magma", "§6", 0xFF7A00, "LAVA", "CHAMAS", "Punho de Lava", "põe fogo e dá dano extra", "burning_time", -0.25, false, "Coração de Lava", "queima por bem menos tempo");

        t = n("CIDADE_ANTIGA", "Cidade Ancestral", "Eco Sombrio", Onde.NORMAL, 4, Estilo.SUBTERRANEA, "sculk", "CIDADE ANTIGA", "Ancestral");
        t.lacaios = m("SKELETON", "ZOMBIE", "SPIDER", "STRAY");
        chefe(t, "ENDERMAN", 460, 2.0, 13);
        t.habs = h(Hab.DRENAR, Hab.RUGIDO, Hab.BURACO_NEGRO, Hab.CORRENTES);
        t.ajudantes = m("SKELETON", "STRAY");
        t.esp = Esp.ESCURIDAO;
        t.aura = "SCULK_SOUL";
        rel(t, "do Eco Sombrio", "§3", 0x1E5A66, "SCULK_SOUL", "SONICO", "Eco", "dano alto e escuridão", "sneaking_speed", 0.15, false, "Passos Silenciosos", "anda agachado mais rápido");

        t = n("GELEIRA", "Geleira Eterna", "Gigante de Gelo", Onde.NORMAL, 4, Estilo.SUBTERRANEA, "gelo_azul", "GELEIRA", "Geleira");
        t.lacaios = m("STRAY", "STRAY", "ZOMBIE", "SKELETON");
        chefe(t, "ZOMBIE", 480, 3.0, 13, "HAND:DIAMOND_AXE", "HEAD:DIAMOND_HELMET");
        t.habs = h(Hab.GELO_PRISAO, Hab.CONGELAR, Hab.PISAO, Hab.ONDA_CHOQUE);
        t.ajudantes = m("STRAY");
        t.esp = Esp.ESPINHOS_PELE;
        t.aura = "SNOWFLAKE";
        t.biomas = new String[] {"SNOW", "ICE", "FROZEN", "PEAKS"};
        rel(t, "do Gigante de Gelo", "§b", 0x8FD8FF, "SNOWFLAKE", "CONGELAR", "Machado Glacial", "congela o alvo", "armor", 1.0, false, "Pele de Geleira", "+1 de armadura");

        t = n("SANGUE", "Santuário de Sangue", "Lorde Vampiro", Onde.NORMAL, 4, Estilo.SUBTERRANEA, "sangue", "SANTUÁRIO", "Sangue");
        t.lacaios = m("ZOMBIE", "SKELETON", "WITCH", "VINDICATOR");
        chefe(t, "VINDICATOR", 460, 1.8, 13, "HAND:NETHERITE_SWORD");
        t.habs = h(Hab.DRENAR, Hab.SANGRAR, Hab.ENXAME, Hab.TELEPORTE);
        t.ajudantes = m("VINDICATOR", "WITCH");
        t.esp = Esp.VAMPIRO;
        t.aura = "DAMAGE_INDICATOR";
        rel(t, "do Lorde Vampiro", "§4", 0x8B0000, "DAMAGE_INDICATOR", "SANGRAR", "Sede de Sangue", "o alvo sangra", "max_health", 2.0, false, "Sangue Nobre", "+1 coração de vida");

        t = n("RELOGIO", "Torre do Relógio", "O Relojoeiro", Onde.NORMAL, 4, Estilo.TORRE, "cobre", "TORRE RELÓGIO", "Relógio");
        t.lacaios = m("VINDICATOR", "PILLAGER", "WITCH", "SKELETON");
        chefe(t, "ILLUSIONER", 450, 1.8, 12, "HAND:BOW");
        t.habs = h(Hab.TROCA, Hab.CORRENTES, Hab.EXPLOSAO_ARCANA, Hab.LAMINAS);
        t.ajudantes = m("VINDICATOR", "PILLAGER");
        t.esp = Esp.FURIA_TEMPO;
        t.aura = "WAX_OFF";
        rel(t, "do Relojoeiro", "§6", 0xD9A441, "WAX_OFF", "ILUSAO", "Tempo Parado", "cega e marca o alvo", "attack_speed", 0.06, true, "Ponteiro", "ataca 6% mais rápido");

        t = n("LABIRINTO", "Labirinto do Minotauro", "Minotauro", Onde.NORMAL, 4, Estilo.SUBTERRANEA, "tijolo", "LABIRINTO", "Labirinto");
        t.lacaios = m("ZOMBIE", "SKELETON", "VINDICATOR", "HUSK");
        chefe(t, "HOGLIN", 480, 2.2, 14);
        t.habs = h(Hab.INVESTIDA, Hab.MARTELO, Hab.PISAO, Hab.RUGIDO);
        t.ajudantes = m("VINDICATOR", "ZOMBIE");
        t.esp = Esp.FRENESI;
        t.aura = "ANGRY_VILLAGER";
        rel(t, "do Minotauro", "§c", 0xA0522D, "ANGRY_VILLAGER", "INVESTIDA", "Chifrada", "joga o alvo longe", "knockback_resistance", 0.15, false, "Cascos", "é bem menos empurrado");

        t = n("CRIPTA_REAL", "Cripta dos Reis", "Rei Lich", Onde.NORMAL, 4, Estilo.SUBTERRANEA, "sculk", "CRIPTA REAL", "Lich");
        t.lacaios = m("SKELETON", "STRAY", "ZOMBIE", "WITHER_SKELETON");
        chefe(t, "WITHER_SKELETON", 480, 1.8, 13, "HAND:DIAMOND_HOE", "HEAD:GOLDEN_HELMET");
        t.habs = h(Hab.CRANIOS, Hab.DRENAR, Hab.CORRENTES, Hab.INVOCAR);
        t.ajudantes = m("SKELETON", "STRAY");
        t.esp = Esp.CRISTAIS;
        t.aura = "SOUL_FIRE_FLAME";
        rel(t, "do Rei Lich", "§9", 0x4060C0, "SOUL_FIRE_FLAME", "MURCHAR", "Toque do Lich", "aplica Wither no alvo", "max_health", 2.0, false, "Filactério", "+1 coração de vida");

        // =========================================================== Nether
        t = n("DELTA", "Delta de Basalto", "Centurião Zumbi", Onde.NETHER, 3, Estilo.SUBTERRANEA, "basalto", "DELTA", "Delta");
        t.lacaios = m("MAGMA_CUBE", "ZOMBIFIED_PIGLIN", "BLAZE", "MAGMA_CUBE");
        chefe(t, "ZOMBIFIED_PIGLIN", 330, 2.0, 10, "HAND:GOLDEN_AXE", "HEAD:IRON_HELMET");
        t.habs = h(Hab.METEORO, Hab.MARTELO, Hab.ONDA_CHOQUE, Hab.INVOCAR);
        t.ajudantes = m("MAGMA_CUBE");
        t.esp = Esp.ESPINHOS_PELE;
        t.aura = "LAVA";
        rel(t, "do Centurião Zumbi", "§6", 0xB0703C, "LAVA", "CHAMAS", "Lâmina de Brasa", "põe fogo no alvo", "explosion_knockback_resistance", 0.25, false, "Escudo de Basalto", "explosões te empurram menos");

        t = n("CARMESIM", "Floresta Carmesim", "Rei dos Hoglins", Onde.NETHER, 4, Estilo.SUBTERRANEA, "carmesim", "CARMESIM", "Carmesim");
        t.lacaios = m("HOGLIN", "PIGLIN", "PIGLIN_BRUTE", "PIGLIN");
        chefe(t, "HOGLIN", 460, 2.4, 14);
        t.habs = h(Hab.INVESTIDA, Hab.PISAO, Hab.RUGIDO, Hab.SANGRAR);
        t.ajudantes = m("HOGLIN");
        t.esp = Esp.FRENESI;
        t.aura = "CRIMSON_SPORE";
        rel(t, "do Rei dos Hoglins", "§c", 0xC0303A, "CRIMSON_SPORE", "ESTOURO", "Estouro Carmesim", "acerta os monstros em volta", "jump_strength", 0.04, false, "Patas Fortes", "pula mais alto");

        t = n("DISTORCIDO", "Floresta Distorcida", "Senhor dos Esporos", Onde.NETHER, 4, Estilo.SUBTERRANEA, "distorcido", "DISTORCIDA", "Distorcida");
        t.lacaios = m("ENDERMAN", "ZOMBIFIED_PIGLIN", "ENDERMAN", "PIGLIN");
        chefe(t, "ENDERMAN", 450, 2.0, 13);
        t.habs = h(Hab.TELEPORTE, Hab.NUVEM_VENENO, Hab.TROCA, Hab.BURACO_NEGRO);
        t.ajudantes = m("ENDERMAN");
        t.esp = Esp.ESCURIDAO;
        t.aura = "WARPED_SPORE";
        rel(t, "do Senhor dos Esporos", "§3", 0x1FA59A, "WARPED_SPORE", "VAZIO", "Esporo Flutuante", "o alvo flutua", "safe_fall_distance", 1.5, false, "Leveza dos Esporos", "cai 1,5 bloco a mais sem dano");

        t = n("ALMAS", "Vale das Almas", "Ceifador de Almas", Onde.NETHER, 4, Estilo.SUBTERRANEA, "almas", "VALE ALMAS", "Almas");
        t.lacaios = m("SKELETON", "SKELETON", "WITHER_SKELETON", "BLAZE");
        chefe(t, "SKELETON", 460, 2.1, 13, "HAND:NETHERITE_HOE", "HEAD:NETHERITE_HELMET");
        t.habs = h(Hab.DRENAR, Hab.CRANIOS, Hab.SUMIR, Hab.CORRENTES);
        t.ajudantes = m("SKELETON", "WITHER_SKELETON");
        t.esp = Esp.REVIVER;
        t.aura = "SOUL";
        rel(t, "do Ceifador de Almas", "§b", 0x4FD6E0, "SOUL", "MURCHAR", "Ceifa", "aplica Wither no alvo", "movement_efficiency", 0.25, false, "Pés de Alma", "anda melhor na areia de almas");

        t = n("OBSIDIANA", "Fortaleza de Obsidiana", "Arquidemônio", Onde.NETHER, 4, Estilo.SUBTERRANEA, "obsidiana", "FORTE OBSIDIANA", "Obsidiana");
        t.lacaios = m("WITHER_SKELETON", "BLAZE", "PIGLIN_BRUTE", "MAGMA_CUBE");
        chefe(t, "EVOKER", 470, 1.9, 13);
        t.habs = h(Hab.METEORO, Hab.ANEL_FOGO, Hab.EXPLOSAO_ARCANA, Hab.BOLAS_FOGO);
        t.ajudantes = m("BLAZE", "WITHER_SKELETON");
        t.esp = Esp.ELEMENTOS;
        t.aura = "DRIPPING_OBSIDIAN_TEAR";
        rel(t, "do Arquidemônio", "§5", 0x5A1E8C, "DRIPPING_OBSIDIAN_TEAR", "CHAMAS", "Chama Roxa", "põe fogo e dá dano extra", "armor_toughness", 1.0, false, "Pele de Obsidiana", "+1 de resistência da armadura");

        // =========================================================== End
        t = n("ILHAS", "Ilhas Quebradas", "Arauto do Fim", Onde.END, 5, Estilo.TORRE, "fimpedra", "ILHAS", "Ilhas");
        t.lacaios = m("ENDERMAN", "ENDERMITE", "SHULKER", "ENDERMAN");
        chefe(t, "ILLUSIONER", 500, 2.0, 16, "HAND:BOW");
        t.habs = h(Hab.TROCA, Hab.LEVITAR, Hab.BURACO_NEGRO, Hab.CORRENTE_RAIO);
        t.ajudantes = m("ENDERMAN", "ENDERMITE");
        t.esp = Esp.GRAVIDADE;
        t.aura = "REVERSE_PORTAL";
        rel(t, "do Arauto do Fim", "§d", 0xD07CFF, "REVERSE_PORTAL", "VAZIO", "Rasgo do Fim", "o alvo flutua e leva dano", "gravity", -0.006, false, "Asas do Fim", "cai bem mais devagar");

        t = n("NAVE", "Nave do Fim", "Capitão Shulker", Onde.END, 5, Estilo.TORRE, "fimpedra", "NAVE DO FIM", "Nave");
        t.lacaios = m("SHULKER", "ENDERMAN", "ENDERMITE", "ENDERMAN");
        chefe(t, "SHULKER", 480, 2.4, 14);
        t.habs = h(Hab.LEVITAR, Hab.METEORO, Hab.INVOCAR, Hab.CORRENTES);
        t.ajudantes = m("ENDERMITE", "ENDERMAN");
        t.esp = Esp.CRISTAIS;
        t.aura = "PORTAL";
        rel(t, "do Capitão Shulker", "§5", 0x9B6BB0, "PORTAL", "VAZIO", "Projétil Teleguiado", "o alvo flutua", "armor", 1.5, false, "Casco", "+1,5 de armadura");

        t = n("TRONO", "Trono de Obsidiana", "Fera do Vazio", Onde.END, 5, Estilo.SUBTERRANEA, "obsidiana", "TRONO", "Trono");
        t.lacaios = m("ENDERMAN", "ENDERMITE", "VINDICATOR", "ENDERMAN");
        chefe(t, "RAVAGER", 520, 1.6, 18);
        t.habs = h(Hab.INVESTIDA, Hab.RUGIDO, Hab.BURACO_NEGRO, Hab.PISAO);
        t.ajudantes = m("ENDERMAN", "VINDICATOR");
        t.esp = Esp.FRENESI;
        t.aura = "REVERSE_PORTAL";
        rel(t, "da Fera do Vazio", "§5", 0x6A1FA0, "REVERSE_PORTAL", "SONICO", "Rugido do Vazio", "dano alto e escuridão", "attack_damage", 1.0, false, "Fúria do Vazio", "+1 de dano");

        t = n("CORO", "Jardim do Coro", "Jardineira do Vazio", Onde.END, 4, Estilo.SUBTERRANEA, "fimpedra", "JARDIM CORO", "Coro");
        t.lacaios = m("ENDERMITE", "WITCH", "ENDERMAN", "ENDERMITE");
        chefe(t, "WITCH", 440, 1.8, 12);
        t.habs = h(Hab.POCOES, Hab.NUVEM_VENENO, Hab.TROCA, Hab.GOSMA);
        t.ajudantes = m("ENDERMITE", "WITCH");
        t.esp = Esp.ONDAS;
        t.aura = "PORTAL";
        rel(t, "da Jardineira do Vazio", "§d", 0xC9A0DC, "PORTAL", "MALDICAO", "Fruto do Coro", "um efeito ruim no alvo", "luck", 1.0, false, "Colheita", "+1 de sorte");

        t = n("ABISMO", "Abismo do Vazio", "Arqueiro do Vazio", Onde.END, 4, Estilo.SUBTERRANEA, "ametista", "ABISMO", "Abismo");
        t.lacaios = m("ENDERMAN", "SKELETON", "SHULKER", "STRAY");
        chefe(t, "STRAY", 440, 2.0, 12, "HAND:BOW", "HEAD:NETHERITE_HELMET");
        t.habs = h(Hab.FLECHAS, Hab.TRIDENTES, Hab.CORRENTE_RAIO, Hab.TELEPORTE);
        t.ajudantes = m("STRAY", "ENDERMAN");
        t.esp = Esp.ESCURIDAO;
        t.aura = "REVERSE_PORTAL";
        rel(t, "do Arqueiro do Vazio", "§8", 0x2B2B40, "REVERSE_PORTAL", "VAZIO", "Flecha do Vazio", "o alvo flutua", "entity_interaction_range", 0.3, false, "Mira do Vazio", "acerta mais longe");
    }

    private TiposNovos() {}
}
