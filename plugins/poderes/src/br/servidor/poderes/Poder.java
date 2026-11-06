package br.servidor.poderes;

import org.bukkit.Material;

/** Todas as habilidades. Cada classe usa 5 delas (veja Classe). */
public enum Poder {
    // ---- Mago
    FOGO("fogo", "Bola de Fogo", "§6", Material.FIRE_CHARGE, 2,
            "Solta uma bola de fogo que queima", "mobs e jogadores."),
    GELO("gelo", "Rajada de Neve", "§b", Material.SNOWBALL, 4,
            "Atira gelo que da dano e deixa", "o alvo bem lento."),
    VENENO("veneno", "Nuvem de Veneno", "§2", Material.SPIDER_EYE, 20,
            "Cria uma nuvem toxica onde voce olha:", "dano por 5s e envenena quem estiver nela."),
    RAIO("raio", "Raio", "§e", Material.LIGHTNING_ROD, 15,
            "Chama um raio onde voce esta", "olhando (ate 40 blocos)."),
    METEOROS("meteoros", "Chuva de Meteoros", "§c", Material.MAGMA_BLOCK, 45,
            "Faz 5 meteoros cairem do ceu", "onde voce esta olhando."),

    // ---- Engenheiro
    TORRETA("torreta", "Torre Arqueira", "§a", Material.DISPENSER, 30,
            "Coloca uma torre que atira flechas", "nos seus inimigos por 20s."),
    TORRE_GELO("torregelo", "Torre de Gelo", "§b", Material.BLUE_ICE, 35,
            "Torre que congela os inimigos", "com um raio de gelo (lentidao)."),
    TORRE_FOGO("torrefogo", "Torre Lanca-Chamas", "§6", Material.MAGMA_BLOCK, 35,
            "Torre de curto alcance que", "queima tudo que chegar perto."),
    TORRE_TESLA("torretesla", "Torre Tesla", "§e", Material.COPPER_BLOCK, 45,
            "Choque eletrico que pula", "de um inimigo para outro."),
    TORRE_CANHAO("torrecanhao", "Torre Canhao", "§8", Material.ANVIL, 60,
            "Artilharia pesada: bala que explode", "em area, de bem longe."),

    // ---- Saiyajin
    KI_BLAST("kiblast", "Rajada de Ki", "§e", Material.GLOWSTONE_DUST, 3,
            "Dispara 4 esferas de energia seguidas", "que seguem a mira (6 transformado)."),
    TELEPORTE_INSTANTANEO("teleinstantaneo", "Teletransporte Instantaneo", "§f", Material.ENDER_EYE, 8,
            "Aparece nas costas do inimigo que", "voce esta olhando (ate 30 blocos)."),
    VOO("voo", "Voo (Bukujutsu)", "§3", Material.ELYTRA, 15,
            "Voa por 4 segundos.", "Cai devagar quando acaba."),
    KAMEHAMEHA("kamehameha", "Kamehameha", "§b", Material.HEART_OF_THE_SEA, 30,
            "Carrega 1s e solta um raio de energia", "por 3s que segue sua mira (32 blocos)."),
    SUPER_SAIYAJIN("supersaiyajin", "Super Saiyajin", "§6", Material.BLAZE_POWDER, 90,
            "Transformacao por 15s: armadura de ki dourada,", "cabelo de energia, mais forca e poderes 30% mais fortes."),

    // ---- Guerreiro
    DASH("dash", "Investida", "§f", Material.RABBIT_FOOT, 5,
            "Te lanca para frente.", "Sem dano de queda por 4s."),
    GOLEM("golem", "Forca do Golem", "§7", Material.IRON_BLOCK, 10,
            "Soco que joga o alvo pro alto", "e explode como um creeper."),
    GRITO("grito", "Grito de Guerra", "§c", Material.GOAT_HORN, 25,
            "Empurra e enfraquece os inimigos", "em volta e te deixa mais resistente."),
    GUARDIAO("guardiao", "Guardiao de Ferro", "§f", Material.CARVED_PUMPKIN, 45,
            "Invoca um golem que so ataca", "seus inimigos (20s a 40s)."),
    TERREMOTO("terremoto", "Terremoto", "§6", Material.COARSE_DIRT, 40,
            "Pula e esmaga o chao: onda que", "da dano e joga os inimigos pro alto."),

    // ---- Ninja
    SHURIKEN("shuriken", "Shuriken", "§7", Material.IRON_NUGGET, 3,
            "Lanca 3 estrelas ninja", "em leque."),
    FUMACA("fumaca", "Bomba de Fumaca", "§8", Material.GUNPOWDER, 20,
            "Fica invisivel e rapido por 5s e", "cega os inimigos em volta."),
    TELEPORTE("teleporte", "Passo das Sombras", "§5", Material.ENDER_PEARL, 8,
            "Aparece onde voce esta olhando", "(ate 30 blocos)."),
    LAMINAS("laminas", "Laminas Giratorias", "§f", Material.IRON_SWORD, 20,
            "Gira por 3s cortando todos", "os inimigos em volta."),
    ATAQUE_RELAMPAGO("relampago", "Ataque Relampago", "§d", Material.NETHERITE_SWORD, 45,
            "Pula de inimigo em inimigo cortando", "ate 5 deles e volta para o lugar."),

    // ---- Druida
    CURA("cura", "Cura", "§d", Material.GLISTERING_MELON_SLICE, 25,
            "Recupera 4 coracoes, apaga o fogo", "e da regeneracao."),
    RAIZES("raizes", "Raizes", "§2", Material.HANGING_ROOTS, 18,
            "Raizes prendem os inimigos onde", "voce olha por 3 segundos."),
    MATILHA("matilha", "Matilha", "§6", Material.BONE, 50,
            "Invoca 3 lobos espirituais que", "lutam com voce por 20s."),
    BENCAO("bencao", "Bencao da Natureza", "§a", Material.GOLDEN_APPLE, 35,
            "Cura voce e os jogadores perto", "e da regeneracao forte."),
    ESPINHOS("espinhos", "Furia dos Espinhos", "§2", Material.SWEET_BERRIES, 40,
            "Espinhos brotam em volta 3 vezes:", "dano e veneno nos inimigos."),

    // ---- Tridente
    ARREMESSO("arremesso", "Arremesso do Tridente", "§3", Material.TRIDENT, 3,
            "Atira um tridente de agua", "que acerta forte de longe."),
    ONDA("onda", "Onda", "§b", Material.WATER_BUCKET, 10,
            "Uma onda na sua frente: dano,", "empurra e deixa lento."),
    TRIDENTE_RAIO("tridenteraio", "Tridente Trovejante", "§e", Material.END_ROD, 20,
            "Raios caem em ate 3 inimigos", "perto de voce (15 blocos)."),
    REDEMOINHO("redemoinho", "Redemoinho", "§9", Material.NAUTILUS_SHELL, 35,
            "Um redemoinho onde voce olha", "puxa e machuca por 4s."),
    POSEIDON("poseidon", "Furia de Poseidon", "§1", Material.CONDUIT, 60,
            "Tsunami em volta de voce e", "forca do mar por 10 segundos."),

    // ---- Arqueiro
    TIRO_CERTEIRO("tirocerteiro", "Tiro Certeiro", "§f", Material.ARROW, 2,
            "Uma flecha rapida e precisa", "que vai bem longe."),
    FLECHA_EXPLOSIVA("flechaexplosiva", "Flecha Explosiva", "§c", Material.FIREWORK_STAR, 10,
            "Explode onde acerta: dano em", "area (nao quebra blocos)."),
    MULTIPLO("multiplo", "Disparo Multiplo", "§e", Material.SPECTRAL_ARROW, 12,
            "Atira 5 flechas em leque", "de uma vez so."),
    CHUVA_FLECHAS("chuvaflechas", "Chuva de Flechas", "§7", Material.CROSSBOW, 30,
            "Chove flecha onde voce olha", "por 3 segundos."),
    TEMPESTADE("tempestade", "Tempestade de Flechas", "§6", Material.BOW, 60,
            "Flechas que perseguem ate 8", "inimigos, em 3 rajadas."),

    // ---- Lutador (passiva: soco de mao vazia mais forte, sobe com o nivel)
    RAJADA_SOCOS("rajada", "Rajada de Socos", "§6", Material.RABBIT_HIDE, 4,
            "6 socos de energia seguidos na sua", "frente; o ultimo empurra longe."),
    GANCHO("gancho", "Gancho do Dragao", "§c", Material.MAGMA_CREAM, 10,
            "Sobe num gancho de fogo e joga pro alto", "todos em volta (5 blocos)."),
    SOCO_SISMICO("sismico", "Soco Sismico", "§6", Material.CRACKED_STONE_BRICKS, 20,
            "Soco no chao: uma rachadura corre", "pra frente jogando todos pro alto."),
    FURIA("furia", "Punhos em Chamas", "§4", Material.REDSTONE, 45,
            "10s: soco 50% mais forte, mais rapido", "e cada soco explode em fogo."),
    PUNHO_METEORO("punhometeoro", "Punho Meteoro", "§e", Material.HEAVY_CORE, 60,
            "Carrega e solta um punho gigante", "de energia que atravessa tudo."),

    // ---- Cipo
    CHICOTE("chicote", "Chicote de Cipó", "§a", Material.TWISTING_VINES, 3,
            "Chicoteia na sua frente (10 blocos):", "dano e puxa o inimigo para você."),
    BALANCO("balanco", "Balanço do Tarzan", "§2", Material.LEAD, 7,
            "Prende um cipó onde você mira e", "se balança até lá (sem dano de queda)."),
    LACO("laco", "Laço de Espinhos", "§2", Material.WEEPING_VINES, 16,
            "Laça os inimigos onde você olha,", "junta todos no meio e eles sangram."),
    FLOR_CARNIVORA("florcarnivora", "Flor Carnívora", "§c", Material.PITCHER_PLANT, 35,
            "Planta uma flor gigante por 10s que", "puxa e morde quem chegar perto."),
    SELVA_VIVA("selvaviva", "Selva Viva", "§a", Material.JUNGLE_SAPLING, 60,
            "Cipós brotam em volta: os inimigos ficam", "pendurados e estrangulados; você se cura.");

    public final String id;
    public final String nome;
    public final String cor;
    public final Material icone;
    public final int recargaSeg;
    public final String desc1;
    public final String desc2;

    Poder(String id, String nome, String cor, Material icone, int recargaSeg, String desc1, String desc2) {
        this.id = id;
        this.nome = nome;
        this.cor = cor;
        this.icone = icone;
        this.recargaSeg = recargaSeg;
        this.desc1 = desc1;
        this.desc2 = desc2;
    }

    public static Poder porId(String s) {
        if (s == null) return null;
        String t = s.toLowerCase(java.util.Locale.ROOT);
        for (Poder p : values()) {
            if (p.id.equals(t)) return p;
        }
        return null;
    }
}
