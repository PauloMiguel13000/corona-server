package br.servidor.poderes;

import org.bukkit.Material;

/** As classes: cada uma tem 5 habilidades, liberadas por nivel de poder (config classes.niveis). */
public enum Classe {
    MAGO("mago", "Mago", "§d", Material.BLAZE_ROD,
            "Magias de longe: fogo, neve,", "veneno, raios e meteoros.",
            Poder.FOGO, Poder.GELO, Poder.VENENO, Poder.RAIO, Poder.METEOROS),
    ENGENHEIRO("engenheiro", "Engenheiro", "§6", Material.DISPENSER,
            "Constroi torres de varios tipos", "que lutam por ele.",
            Poder.TORRETA, Poder.TORRE_GELO, Poder.TORRE_FOGO, Poder.TORRE_TESLA, Poder.TORRE_CANHAO),
    SAIYAJIN("saiyajin", "Saiyajin", "§e", Material.BLAZE_POWDER,
            "Guerreiro do Dragon Ball: ki,", "teletransporte e Kamehameha.",
            Poder.KI_BLAST, Poder.TELEPORTE_INSTANTANEO, Poder.VOO, Poder.KAMEHAMEHA, Poder.SUPER_SAIYAJIN),
    GUERREIRO("guerreiro", "Guerreiro", "§c", Material.IRON_SWORD,
            "Forca bruta no corpo a corpo", "e muita resistencia.",
            Poder.DASH, Poder.GOLEM, Poder.GRITO, Poder.GUARDIAO, Poder.TERREMOTO),
    NINJA("ninja", "Ninja", "§8", Material.NETHERITE_SWORD,
            "Rapido e furtivo: some, aparece", "e ataca varios de uma vez.",
            Poder.SHURIKEN, Poder.FUMACA, Poder.TELEPORTE, Poder.LAMINAS, Poder.ATAQUE_RELAMPAGO),
    DRUIDA("druida", "Druida", "§a", Material.OAK_SAPLING,
            "Poder da natureza: cura, prende", "os inimigos e chama lobos.",
            Poder.CURA, Poder.RAIZES, Poder.MATILHA, Poder.BENCAO, Poder.ESPINHOS),
    TRIDENTE("tridente", "Tridente", "§3", Material.TRIDENT,
            "Senhor dos mares: tridente,", "ondas, raios e redemoinho.",
            Poder.ARREMESSO, Poder.ONDA, Poder.TRIDENTE_RAIO, Poder.REDEMOINHO, Poder.POSEIDON),
    ARQUEIRO("arqueiro", "Arqueiro", "§2", Material.BOW,
            "Mestre do arco: tiros certeiros", "e chuva de flechas.",
            Poder.TIRO_CERTEIRO, Poder.FLECHA_EXPLOSIVA, Poder.MULTIPLO, Poder.CHUVA_FLECHAS, Poder.TEMPESTADE),
    LUTADOR("lutador", "Lutador", "§4", Material.LEATHER,
            "Mestre do soco: bate muito mais forte", "de mao vazia (sobe com o nivel).",
            Poder.RAJADA_SOCOS, Poder.GANCHO, Poder.SOCO_SISMICO, Poder.FURIA, Poder.PUNHO_METEORO),
    CIPO("cipo", "Cipó", "§2", Material.VINE,
            "Mestre das trepadeiras: chicoteia,", "se balança, laça e estrangula com cipós.",
            Poder.CHICOTE, Poder.BALANCO, Poder.LACO, Poder.FLOR_CARNIVORA, Poder.SELVA_VIVA);

    public final String id;
    public final String nome;
    public final String cor;
    public final Material icone;
    public final String desc1;
    public final String desc2;
    public final Poder[] poderes;

    Classe(String id, String nome, String cor, Material icone, String desc1, String desc2, Poder... poderes) {
        this.id = id;
        this.nome = nome;
        this.cor = cor;
        this.icone = icone;
        this.desc1 = desc1;
        this.desc2 = desc2;
        this.poderes = poderes;
    }

    /** Posicao da habilidade na classe (0 a 4), ou -1 se nao for desta classe. */
    public int posicao(Poder p) {
        for (int i = 0; i < poderes.length; i++) {
            if (poderes[i] == p) return i;
        }
        return -1;
    }

    public static Classe porId(String s) {
        if (s == null) return null;
        String t = s.toLowerCase(java.util.Locale.ROOT);
        for (Classe c : values()) {
            if (c.id.equals(t)) return c;
        }
        return null;
    }
}
