package br.servidor.mineracao;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** 13/11 (Paulo): 3 missoes diarias da Prisao por jogador, com premio em $ e bonus por completar as 3. */
final class Missoes {
    enum Tipo {
        QUEBRAR("Quebre %d blocos nas minas", Material.IRON_PICKAXE),
        MINERIO("Minere %d minérios (sem contar pedra)", Material.DIAMOND_ORE),
        VENDER("Venda %s em minério", Material.EMERALD),
        ENCANTAR("Encante %d item(ns) na Sala de Encantamentos", Material.ENCHANTING_TABLE),
        PVP("Derrote %d jogador(es) na Mina PvP", Material.NETHERITE_SWORD);
        final String texto; final Material icone;
        Tipo(String texto, Material icone) { this.texto = texto; this.icone = icone; }
    }

    private final Mineracao pl;
    private final NamespacedKey kDia, kTipos, kMetas, kProg, kFeitas;
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    Missoes(Mineracao pl) {
        this.pl = pl;
        kDia = new NamespacedKey(pl, "missao_dia");
        kTipos = new NamespacedKey(pl, "missao_tipos");
        kMetas = new NamespacedKey(pl, "missao_metas");
        kProg = new NamespacedKey(pl, "missao_prog");
        kFeitas = new NamespacedKey(pl, "missao_feitas");
    }

    static int hoje() { LocalDate d = LocalDate.now(FUSO); return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth(); }

    /** Garante as missoes de hoje (sorteia de novo quando vira o dia). */
    private void garantir(Player p) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        Integer d = c.get(kDia, PersistentDataType.INTEGER);
        if (d != null && d == hoje()) return;
        int r = pl.rank(p);
        java.util.Random rnd = new java.util.Random(p.getUniqueId().getMostSignificantBits() ^ hoje() * 31L);
        List<Tipo> pool = new ArrayList<>(List.of(Tipo.QUEBRAR, Tipo.MINERIO, Tipo.VENDER, Tipo.ENCANTAR));
        if (r >= pl.minas.get(12).rank) pool.add(Tipo.PVP);
        java.util.Collections.shuffle(pool, rnd);
        int[] tipos = new int[3], metas = new int[3];
        for (int i = 0; i < 3; i++) {
            Tipo t = pool.get(i);
            tipos[i] = t.ordinal();
            metas[i] = switch (t) {
                case QUEBRAR -> 400 + 100 * rnd.nextInt(5);
                case MINERIO -> 120 + 20 * rnd.nextInt(5);
                case VENDER -> (int) Math.min(2_000_000, 1500L * (r + 1) * (r + 1));
                case ENCANTAR -> 1 + rnd.nextInt(2);
                case PVP -> 1 + rnd.nextInt(2);
            };
        }
        c.set(kDia, PersistentDataType.INTEGER, hoje());
        c.set(kTipos, PersistentDataType.INTEGER_ARRAY, tipos);
        c.set(kMetas, PersistentDataType.INTEGER_ARRAY, metas);
        c.set(kProg, PersistentDataType.INTEGER_ARRAY, new int[3]);
        c.set(kFeitas, PersistentDataType.INTEGER, 0);
    }

    long premio(Player p) { return Math.round(400L * (pl.rank(p) + 1) * Mineracao.multPrest(pl.prestigio(p))); }

    /** Soma progresso no tipo dado (chamado pelos eventos da Prisao). */
    void somar(Player p, Tipo tipo, long qtd) {
        if (qtd <= 0) return;
        garantir(p);
        PersistentDataContainer c = p.getPersistentDataContainer();
        int[] tipos = c.get(kTipos, PersistentDataType.INTEGER_ARRAY), metas = c.get(kMetas, PersistentDataType.INTEGER_ARRAY), prog = c.get(kProg, PersistentDataType.INTEGER_ARRAY);
        if (tipos == null || metas == null || prog == null) return;
        boolean mudou = false;
        for (int i = 0; i < 3; i++) {
            if (tipos[i] != tipo.ordinal() || prog[i] >= metas[i]) continue;
            prog[i] = (int) Math.min(metas[i], prog[i] + qtd);
            mudou = true;
            if (prog[i] >= metas[i]) concluir(p, tipo, metas[i]);
        }
        if (mudou) c.set(kProg, PersistentDataType.INTEGER_ARRAY, prog);
    }

    private void concluir(Player p, Tipo t, int meta) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        int feitas = c.getOrDefault(kFeitas, PersistentDataType.INTEGER, 0) + 1;
        c.set(kFeitas, PersistentDataType.INTEGER, feitas);
        long v = premio(p);
        pl.setDinheiro(p, pl.dinheiro(p) + v);
        p.sendMessage(Mineracao.txt("§6§l✔ MISSÃO CONCLUÍDA! §f" + descricao(t, meta) + " §8» " + Mineracao.din(v) + " §8(" + feitas + "/3)"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        if (feitas == 3) {
            long b = v * 3;
            pl.setDinheiro(p, pl.dinheiro(p) + b);
            p.giveExpLevels(5);
            p.showTitle(net.kyori.adventure.title.Title.title(Mineracao.txt("§6§l★ MISSÕES DO DIA ★"), Mineracao.txt("§fbônus de " + Mineracao.din(b) + " §f+ 5 níveis de XP")));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            for (Player o : p.getWorld().getPlayers())
                if (o != p) o.sendMessage(Mineracao.txt("§6★ §e" + p.getName() + " §7completou as §6missões do dia §7da Prisão! §8(/missoes)"));
        }
    }

    static String descricao(Tipo t, int meta) {
        return t == Tipo.VENDER ? String.format(t.texto, "$" + Mineracao.fmt(meta)) : String.format(t.texto, meta);
    }

    void abrir(Player p) {
        garantir(p);
        PersistentDataContainer c = p.getPersistentDataContainer();
        int[] tipos = c.get(kTipos, PersistentDataType.INTEGER_ARRAY), metas = c.get(kMetas, PersistentDataType.INTEGER_ARRAY), prog = c.get(kProg, PersistentDataType.INTEGER_ARRAY);
        Mineracao.Tela h = new Mineracao.Tela("missoes");
        Inventory inv = Bukkit.createInventory(h, 27, Mineracao.txt("§8📜 Missões do dia • Prisão"));
        h.inv = inv;
        var vidro = Mineracao.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, vidro);
        long v = premio(p);
        for (int i = 0; i < 3; i++) {
            Tipo t = Tipo.values()[tipos[i]];
            boolean ok = prog[i] >= metas[i];
            int pct = (int) (prog[i] * 100L / Math.max(1, metas[i]));
            String barra = "§a" + "■".repeat(pct / 10) + "§8" + "■".repeat(10 - pct / 10);
            inv.setItem(11 + 2 * i, Mineracao.item(ok ? Material.LIME_DYE : t.icone, (ok ? "§a§l✔ " : "§e§l") + "Missão " + (i + 1),
                    "§f" + descricao(t, metas[i]), "", barra + " §f" + (t == Tipo.VENDER ? "$" + Mineracao.fmt(prog[i]) : prog[i]) + "§7/" + (t == Tipo.VENDER ? "$" + Mineracao.fmt(metas[i]) : metas[i]),
                    "", ok ? "§aConcluída!" : "§7Prêmio: " + Mineracao.din(v)));
        }
        int feitas = c.getOrDefault(kFeitas, PersistentDataType.INTEGER, 0);
        inv.setItem(22, Mineracao.item(feitas >= 3 ? Material.NETHER_STAR : Material.CHEST, "§6§lBônus das 3 missões",
                "§7Complete as três hoje e ganhe", Mineracao.din(v * 3) + " §7+ §a5 níveis de XP§7.", "", "§7Feitas: §f" + feitas + "/3", "§8Novas missões todo dia à meia-noite."));
        p.openInventory(inv);
    }
}
