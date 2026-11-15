package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Divulgacao feita pelos proprios jogadores (sem conta em site nenhum):
 *  - /ip mostra o endereco do Java e do Bedrock para mandar aos amigos;
 *  - de tempos em tempos, uma dica no chat (votar, chamar amigos, /ip);
 *  - indicacao: quem e novo usa /indicar <nick de quem chamou>; os dois ganham premio quando o novo
 *    completa X minutos jogando (evita conta fake so para o premio).
 */
final class Divulgacao implements Listener {

    private final Extras pl;
    private final File arquivo;
    private final YamlConfiguration dados;
    private int proxima;

    Divulgacao(Extras pl) {
        this.pl = pl;
        this.arquivo = new File(pl.getDataFolder(), "indicacoes.yml");
        this.dados = YamlConfiguration.loadConfiguration(arquivo);
    }

    private void salvar() {
        try {
            dados.save(arquivo);
        } catch (IOException e) {
            pl.getLogger().warning("Nao consegui salvar indicacoes.yml: " + e.getMessage());
        }
    }

    void iniciar() {
        long min = pl.getConfig().getLong("divulgacao.dica-a-cada-minutos", 20);
        if (pl.getConfig().getBoolean("divulgacao.ativo", true) && min > 0)
            Bukkit.getScheduler().runTaskTimer(pl, this::dica, 20L * 60 * min, 20L * 60 * min);
        // Discord do servidor: convite no chat de tempos em tempos (so se tiver alguem online)
        long dc = pl.getConfig().getLong("divulgacao.discord.a-cada-minutos", 50);
        if (pl.getConfig().getBoolean("divulgacao.discord.ativo", true) && dc > 0 && !linkDiscord().isBlank())
            Bukkit.getScheduler().runTaskTimer(pl, this::avisoDiscord, 20L * 60 * dc, 20L * 60 * dc);
        // indicacoes: confere o tempo de jogo de quem foi indicado
        Bukkit.getScheduler().runTaskTimer(pl, this::conferirIndicados, 20L * 60, 20L * 60);
    }

    static final List<String> DICAS_EXTRAS = List.of(
            "&6&l>> &eMissões do dia: 3 missões novas todo dia valendo coronas. &f/missoes",
            "&6&l>> &eNão sabe por onde começar? &f/ajuda &7abre o guia com tudo o que tem no servidor",
            "&6&l>> &eTurno da Noite agora em grupo: &f/turno coop <nick> &7(até 3 vigias na mesma sala)",
            "&6&l>> &eJogos de terror: Hotel, Backrooms, Casa da Vovó, Não Chore, Turno, Fábrica e Floresta. &f/terror",
            "&6&l>> &eNOVO: a &6&lPRISÃO &e(Rank Up)! &f/minas &7— 12 minas, dinheiro próprio, kits e encantamentos. Portal no spawn!",
            "&6&l>> &eCoragem? A &cMina PvP &e(rank D) tem o melhor minério e paga por abate, mas lá quem morre perde os itens. &f/minas",
            "&6&l>> &eNa Prisão: pise no &atapete verde &epara vender, pegue a &a/recompensa &ediária e fique de olho na &bChuva de Minério&e!",
            "&6&l>> &eDefesa da Torre tem fases novas: &bGeleira &ee &5Ninho do Dragão&e, e 3 torres novas! &f/defesa",
            "&6&l>> &eNOVO: &f/kits &e— kits do &aSurvival &e(sobrevivente, construtor, fazendeiro, minerador) e das &cMasmorras &e(aventureiro, alquimista, explorador)!",
            "&6&l>> &eCAÇA AO TESOURO: de tempos em tempos um &6Baú do Tesouro &eaparece no Survival com um feixe de luz. &f/tesouro",
            "&6&l>> &eTodo dia uma &cmasmorra em destaque &ecom coronas e XP em DOBRO — veja no painel do spawn!",
            "&6&l>> &eDefesa da Torre: &6poderes &e(meteoros, nevasca, trovão) e um &5desafio diferente &etodo dia. &f/defesa",
            "&6&l>> &eAlgumas noites no Survival viram &4&lLUA DE SANGUE&e: monstros mais fortes que pagam coronas e soltam loot raro!",
            "&6&l>> &eQuer saber o que tem de especial hoje? &f/eventos &e(destaques, desafio, tesouro, lua de sangue...)",
            "&6&l>> &eNo Survival podem aparecer &6&lMONSTROS LENDÁRIOS &e(dourados): derrote e ganhe 50 coronas + diamantes!",
            "&6&l>> &eGire a &6&lROLETA DO DIA &egrátis: coronas, XP e até &c&lJACKPOT de 1000 coronas&e! &f/roleta",
            "&6&l>> &ePesque no Survival: tem &6Peixe Dourado &e(coronas), livros encantados raros e até &bTRIDENTE&e!",
            "&6&l>> &eEntre todo dia: o &6login diário &edá coronas que crescem até &6600 &eno 7º dia seguido!",
            "&6&l>> &eNOVO: &6&l/conquistas &e— 21 metas (horas jogadas, monstros, diamantes, dragão...) que pagam até &61500 coronas&e!",
            "&6&l>> &eRANKING SEMANAL das Masmorras: quem mais derrotar chefes na semana ganha até &61500 coronas&e! Placar no lobby das masmorras.",
            "&6&l>> &eAgora os jogos de terror pagam: escapou do &4Hotel&e, &eBackrooms&e, &5Vovó&e, &8Porcelana &eou &9Não Chore&e? Coronas na hora!",
            "&6&l>> &eArena PvP agora paga &6coronas por abate &ee tem &c&lSEQUÊNCIA DE ABATES &ecom bônus! &f/minigames",
            "&6&l>> &eFique de olho: de tempos em tempos cai uma &6&lCHUVA DE CORONAS &ena praça do spawn! Pegue as moedas douradas.",
            "&6&l>> &eFerramenta ou armadura gasta? O &6Ferreiro &econserta por coronas: segure o item e use &f/reparar",
            "&6&l>> &eRANKING DA SEMANA: quem mais jogar (sem AFK) ganha até &61000 coronas &etoda segunda! &f/tempo",
            "&6&l>> &eNa Prisão tem &6ranking semanal de blocos&e: top 3 ganha até &2$60.000&e! &f/topminas",
            "&6&l>> &eNo Survival caem &c&lMETEOROS &ede vez em quando, cheios de diamante e ouro! Fique de olho no chat.",
            "&6&l>> &eVeja sua posição nos 3 rankings da semana (tempo, chefes e Prisão): &f/semana",
            "&6&l>> &eDe hora em hora tem &e&lSORTEIO&e: um jogador ativo ganha &6100 coronas&e. Fique ativo para concorrer!",
            "&6&l>> &eFique de olho: aparece &d&lPIÑATA &eno spawn e &b&lTORNEIO DE PESCA &eno Survival — prêmios em coronas!",
            "&c&l▶ &eTem vídeo novo do servidor no YouTube! Masmorras, chefes e tour: &f/youtube &7(se inscreva!)",
            "&6&l>> &eTodo dia tem &e&lCONTRATO &enas masmorras: derrote 30 monstros da masmorra do dia e ganhe coronas! &f/contrato",
            "&6&l>> &eFique ligado no chat: &e&lPERGUNTA RELÂMPAGO &e— quem responder primeiro ganha coronas!",
            "&6&l>> &eA primeira vitória contra um chefe de masmorra no dia vale &6coronas em DOBRO&e!",
            "&6&l>> &eNa Prisão tem &6missões do dia &ecom prêmio em $ — dentro dela use &f/missoes");

    private void dica() {
        List<String> l = new java.util.ArrayList<>(pl.getConfig().getStringList("divulgacao.dicas"));
        // dicas das novidades (10/11): entram na roda junto com as do config; "dicas-extras: []" no config desliga
        List<String> extras = pl.getConfig().isSet("divulgacao.dicas-extras") ? pl.getConfig().getStringList("divulgacao.dicas-extras") : DICAS_EXTRAS;
        for (String x : extras) if (!l.contains(x)) l.add(x);
        if (l.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        String m = l.get(proxima++ % l.size());
        Bukkit.broadcast(Extras.txt(Placas.cor(m)));
    }

    String linkDiscord() {
        return pl.getConfig().getString("divulgacao.discord.link", "https://discord.gg/uhEaWb9sJA").trim();
    }

    /** Linha com o link clicavel (Java abre o navegador; no Bedrock o link aparece escrito). */
    private Component linhaLink() {
        String link = linkDiscord();
        return Extras.txt("§9§l>> §e§n" + link)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(Extras.txt("§aClique para abrir o Discord")))
                .append(Extras.txt(" §7(clique ou digite §f/discord§7)"));
    }

    private void avisoDiscord() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        String m = pl.getConfig().getString("divulgacao.discord.mensagem",
                "&9&l>> &bEntre no nosso Discord! &fNovidades, call com música, grupos para masmorra e suporte.");
        Bukkit.broadcast(Extras.txt(Placas.cor(m)));
        Bukkit.broadcast(linhaLink());
    }

    void discord(CommandSender s) {
        s.sendMessage(Extras.txt("§9§l--- DISCORD DO CORONA SERVER ---"));
        s.sendMessage(Extras.txt("§fNovidades todo dia, call com música, grupos para masmorra e suporte."));
        s.sendMessage(linhaLink());
    }

    void ip(Player p) {
        p.sendMessage(Extras.txt("§6§l--- CHAME OS AMIGOS ---"));
        p.sendMessage(Extras.txt("§fJava: §e" + pl.getConfig().getString("divulgacao.ip-java", "")));
        p.sendMessage(Extras.txt("§fBedrock (celular, Xbox, Windows): §e" + pl.getConfig().getString("divulgacao.ip-bedrock", "")
                + " §fporta §e" + pl.getConfig().getString("divulgacao.porta-bedrock", "")));
        p.sendMessage(Extras.txt("§7Quando o amigo entrar, ele digita §e/indicar " + p.getName() + " §7e vocês dois ganham prêmio"
                + " §7(quando ele jogar " + pl.getConfig().getInt("divulgacao.indicacao.minutos-jogando", 30) + " min)."));
        if (!linkDiscord().isBlank()) p.sendMessage(linhaLink());
    }

    static int minutosJogados(Player p) {
        return p.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60);
    }

    void indicar(Player p, String nome) {
        if (!pl.getConfig().getBoolean("divulgacao.indicacao.ativo", true)) {
            p.sendMessage(Extras.txt("§cIndicação desligada."));
            return;
        }
        String eu = p.getUniqueId().toString();
        if (dados.contains("indicados." + eu)) {
            p.sendMessage(Extras.txt("§eVocê já disse quem te chamou (" + dados.getString("indicados." + eu + ".nome") + ")."));
            return;
        }
        int limite = pl.getConfig().getInt("divulgacao.indicacao.so-ate-minutos-jogados", 180);
        if (minutosJogados(p) > limite) {
            p.sendMessage(Extras.txt("§cA indicação é só para quem chegou agora (até " + limite / 60 + " horas de jogo)."));
            return;
        }
        Player online = Bukkit.getPlayerExact(nome);
        @SuppressWarnings("deprecation") OfflinePlayer quem = online != null ? online : Bukkit.getOfflinePlayerIfCached(nome);
        if (quem == null || (!quem.hasPlayedBefore() && !quem.isOnline())) {
            p.sendMessage(Extras.txt("§cNão achei o jogador " + nome + " (ele precisa já ter jogado aqui)."));
            return;
        }
        if (quem.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Extras.txt("§cVocê não pode indicar você mesmo."));
            return;
        }
        String q = quem.getUniqueId().toString();
        // 14/11: contra conta falsa para ganhar o premio - quem indicou precisa ter jogado de verdade e nao pode ser da mesma rede
        int minQuem = pl.getConfig().getInt("divulgacao.indicacao.quem-chamou-minutos", 60);
        if (quem.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60) < minQuem) {
            p.sendMessage(Extras.txt("§c" + quem.getName() + " ainda jogou pouco aqui para indicar alguém (precisa de " + minQuem + " min de jogo)."));
            return;
        }
        String meuIp = ipDe(p), ipQuem = dados.getString("redes." + q);
        if (pl.getConfig().getBoolean("divulgacao.indicacao.bloquear-mesma-rede", true) && meuIp != null && meuIp.equals(ipQuem)) {
            p.sendMessage(Extras.txt("§cA indicação não vale para contas da mesma casa/rede."));
            pl.getLogger().info("Indicacao recusada (mesma rede): " + p.getName() + " -> " + quem.getName());
            return;
        }
        // limite por dia para quem indica
        long hoje = System.currentTimeMillis() / 86_400_000L;
        int hojeQtd = dados.getLong("quem-chamou." + q + ".dia", -1) == hoje ? dados.getInt("quem-chamou." + q + ".qtd", 0) : 0;
        int max = pl.getConfig().getInt("divulgacao.indicacao.maximo-por-dia", 5);
        if (hojeQtd >= max) {
            p.sendMessage(Extras.txt("§c" + quem.getName() + " já indicou " + max + " pessoas hoje. Tente amanhã."));
            return;
        }
        dados.set("quem-chamou." + q + ".dia", hoje);
        dados.set("quem-chamou." + q + ".qtd", hojeQtd + 1);
        dados.set("indicados." + eu + ".nome", quem.getName());
        dados.set("indicados." + eu + ".uuid", q);
        dados.set("indicados." + eu + ".pago", false);
        salvar();
        int min = pl.getConfig().getInt("divulgacao.indicacao.minutos-jogando", 30);
        p.sendMessage(Extras.txt("§aAnotado: §f" + quem.getName() + " §ate chamou! Jogue §f" + min + " min §ae vocês dois ganham prêmio."));
        if (online != null) online.sendMessage(Extras.txt("§a§l" + p.getName() + " §adisse que você chamou ele! Quando ele jogar " + min + " min, vocês ganham prêmio."));
    }

    private void conferirIndicados() {
        var sec = dados.getConfigurationSection("indicados");
        if (sec == null) return;
        int min = pl.getConfig().getInt("divulgacao.indicacao.minutos-jogando", 30);
        boolean mudou = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String eu = p.getUniqueId().toString();
            if (!sec.contains(eu) || sec.getBoolean(eu + ".pago")) continue;
            if (minutosJogados(p) < min) continue;
            sec.set(eu + ".pago", true);
            mudou = true;
            darPremio(p, "§aVocê completou " + min + " min! Prêmio de indicação entregue.");
            UUID quem = UUID.fromString(sec.getString(eu + ".uuid"));
            Player q = Bukkit.getPlayer(quem);
            if (q != null) darPremio(q, "§a" + p.getName() + " (que você chamou) jogou " + min + " min! Prêmio de indicação entregue.");
            else dados.set("pendentes." + quem, dados.getInt("pendentes." + quem, 0) + 1);
            Bukkit.broadcast(Extras.txt("§6§l>> §e" + sec.getString(eu + ".nome") + " §7trouxe §e" + p.getName() + " §7para o servidor! Chame os seus amigos também: §e/ip"));
        }
        if (mudou) salvar();
    }

    /** A rede do jogador embaralhada (nao guarda o IP em si). */
    private static String ipDe(Player p) {
        try {
            if (p.getAddress() == null) return null;
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(("corona:" + p.getAddress().getAddress().getHostAddress()).getBytes());
            return java.util.HexFormat.of().formatHex(h, 0, 8);
        } catch (Exception ex) {
            return null;
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String rede = ipDe(p);
        if (rede != null && !rede.equals(dados.getString("redes." + p.getUniqueId()))) {
            dados.set("redes." + p.getUniqueId(), rede);
            salvar();
        }
        int n = dados.getInt("pendentes." + p.getUniqueId(), 0);
        if (n <= 0) return;
        dados.set("pendentes." + p.getUniqueId(), null);
        salvar();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (int i = 0; i < n; i++) darPremio(p, "§aPrêmio de indicação: alguém que você chamou já jogou bastante!");
        }, 100L);
    }

    private void darPremio(Player p, String msg) {
        for (String linha : pl.getConfig().getStringList("divulgacao.indicacao.premio")) {
            String[] a = linha.trim().split("\\s+");
            Material m = Material.matchMaterial(a[0]);
            if (m == null || !m.isItem()) continue;
            int q = a.length > 1 ? Integer.parseInt(a[1]) : 1;
            for (ItemStack sobra : p.getInventory().addItem(new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), q)))).values())
                p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        }
        p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.8f, 1.2f);
        p.sendMessage(Extras.txt(msg));
    }
}
