package br.servidor.vovo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Os itens que ficam espalhados pela casa. Cada jogador carrega um por vez. */
enum Missao {
    CHAVE_PORAO("§eChave do Porão", Material.TRIPWIRE_HOOK, EnumSet.of(Casa.Zona.CASA),
            "Abre a porta do porão,", "no corredor de baixo."),
    CHAVE_ENFERRUJADA("§6Chave Enferrujada", Material.TRIAL_KEY, EnumSet.allOf(Casa.Zona.class),
            "Abre o cadeado da", "porta da frente."),
    ALICATE("§7Alicate", Material.SHEARS, EnumSet.allOf(Casa.Zona.class),
            "Corta a corrente da", "porta da frente."),
    PE_DE_CABRA("§cPé de Cabra", Material.IRON_HOE, EnumSet.allOf(Casa.Zona.class),
            "Arranca as tábuas pregadas", "na porta da frente."),
    FUSIVEL("§bFusível", Material.LIGHTNING_ROD, EnumSet.allOf(Casa.Zona.class),
            "Vai na caixa de força do porão.", "Sem energia a fechadura", "elétrica da porta não abre."),
    CHAVE_GARAGEM("§3Chave da Garagem", Material.OMINOUS_TRIAL_KEY, EnumSet.of(Casa.Zona.CASA, Casa.Zona.PORAO),
            "Abre a porta da garagem,", "na sala de jantar."),
    CHAVE_CARRO("§aChave do Carro", Material.NAME_TAG, EnumSet.allOf(Casa.Zona.class),
            "Para ligar o carro", "da garagem."),
    BATERIA("§4Bateria", Material.REDSTONE_BLOCK, EnumSet.allOf(Casa.Zona.class),
            "O carro está sem bateria."),
    GASOLINA("§6Galão de Gasolina", Material.LAVA_BUCKET, EnumSet.allOf(Casa.Zona.class),
            "O tanque do carro está vazio."),
    CHAVE_INGLESA("§9Chave Inglesa", Material.BRUSH, EnumSet.of(Casa.Zona.CASA, Casa.Zona.PORAO, Casa.Zona.GARAGEM),
            "Solta os parafusos da tampa", "do bueiro, no chão da", "caldeira (porão)."),
    SERRA("§8Serra", Material.IRON_AXE, EnumSet.allOf(Casa.Zona.class),
            "Corta a grade de ferro", "no fim do esgoto."),
    BESTA("§dBesta com Dardo", Material.CROSSBOW, EnumSet.allOf(Casa.Zona.class),
            "Mire na vovó e clique com o", "botão direito: ela dorme por", "alguns segundos. Um tiro só."),
    DESPERTADOR("§fDespertador", Material.CLOCK, EnumSet.allOf(Casa.Zona.class),
            "Largue (tecla Q) e se afaste:", "ele toca e a vovó vai ver", "o que é. Dá para usar de novo.");

    final String nome;
    final Material material;
    final EnumSet<Casa.Zona> zonas;
    final String[] dica;

    Missao(String nome, Material material, EnumSet<Casa.Zona> zonas, String... dica) {
        this.nome = nome;
        this.material = material;
        this.zonas = zonas;
        this.dica = dica;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    String nomeLimpo() {
        return nome.replaceAll("§.", "");
    }

    static Missao porId(String s) {
        if (s == null) return null;
        for (Missao m : values()) if (m.id().equals(s)) return m;
        return null;
    }

    ItemStack item(NamespacedKey chave) {
        ItemStack it = new ItemStack(material);
        ItemMeta m = it.getItemMeta();
        m.displayName(Vovo.txt(nome));
        List<Component> lore = new ArrayList<>();
        for (String d : dica) lore.add(Vovo.txt("§7" + d));
        lore.add(Vovo.txt(""));
        lore.add(Vovo.txt("§8Tecla Q: largar no chão (faz barulho)"));
        m.lore(lore);
        m.addItemFlags(ItemFlag.values());
        m.getPersistentDataContainer().set(chave, PersistentDataType.STRING, id());
        it.setItemMeta(m);
        return it;
    }

    static Missao de(ItemStack it, NamespacedKey chave) {
        if (it == null || !it.hasItemMeta()) return null;
        return porId(it.getItemMeta().getPersistentDataContainer().get(chave, PersistentDataType.STRING));
    }
}
