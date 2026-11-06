package br.servidor.hotel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Moeda de Reviver (comprada no site ou com coronas no Mercador Lendario, plugin Masmorras): fica no jogador, na
 * chave "masmorras:moedas_reviver". Quem e eliminado ganha uns segundos para gastar uma e voltar para a partida:
 * no Java clicando na mensagem, no Bedrock numa janelinha Sim/Nao (pelo Geyser). Mesma ideia da Backrooms.
 */
final class Reviver {
    static final NamespacedKey CHAVE = NamespacedKey.fromString("masmorras:moedas_reviver");
    private static final String CMD = "/hotel reviver";

    static int moedas(Player p) {
        Integer v = p.getPersistentDataContainer().get(CHAVE, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    static int segundos(Plugin pl) {
        return Math.max(8, pl.getConfig().getInt("reviver-segundos", 20));
    }

    private static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    private static boolean gastar(Player p) {
        int n = moedas(p);
        if (n <= 0) return false;
        p.getPersistentDataContainer().set(CHAVE, PersistentDataType.INTEGER, n - 1);
        try {
            p.saveData();
        } catch (Throwable ignored) {
        }
        return true;
    }

    /**
     * Oferece a moeda por "segundos". "voltar" faz o jogador voltar e devolve true se deu certo (so entao a moeda e
     * gasta). Sem moeda, so mostra a dica de onde comprar. Devolve true se ofereceu (o jogador tem moeda).
     */
    static boolean oferecer(Plugin pl, Player p, String onde, int segundos, BooleanSupplier voltar) {
        int n = moedas(p);
        if (n <= 0) {
            p.sendMessage(Hotel.txt("§8Dica: com uma §eMoeda de Reviver §8você voltaria para a partida. Tem no site (§f/loja§8) e no §5Mercador Lendário§8."));
            p.sendActionBar(Hotel.txt("§7Sem Moeda de Reviver. §eConsiga na /loja §7ou no §5Mercador Lendário"));
            return false;
        }
        long ate = System.currentTimeMillis() + segundos * 1000L;
        boolean[] feito = {false};
        Runnable usar = () -> {
            if (feito[0] || !p.isOnline()) return;
            if (System.currentTimeMillis() > ate) {
                p.sendMessage(Hotel.txt("§cO tempo para usar a Moeda de Reviver acabou."));
                return;
            }
            if (moedas(p) <= 0) {
                p.sendMessage(Hotel.txt("§cVocê não tem mais Moedas de Reviver."));
                return;
            }
            if (!voltar.getAsBoolean()) {
                p.sendMessage(Hotel.txt("§cNão dá para voltar agora (a partida já acabou). A moeda não foi gasta."));
                return;
            }
            feito[0] = true;
            Pendente.tirar(p.getUniqueId());
            gastar(p);
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.1f);
            p.sendMessage(Hotel.txt("§eMoeda de Reviver usada. §7Restam §e" + moedas(p) + "§7."));
            pl.getLogger().info(p.getName() + " usou uma Moeda de Reviver (" + onde + "), restam " + moedas(p));
        };
        p.sendMessage(Hotel.txt("§eVocê tem §f" + n + " §eMoeda" + (n > 1 ? "s" : "") + " de Reviver. §7Voltar para " + onde + "? §8(" + segundos + "s)"));
        p.sendMessage(Hotel.txt("§a§l[ USAR MOEDA E VOLTAR ]")
                .hoverEvent(HoverEvent.showText(Hotel.txt("§7Gasta 1 moeda e você volta para a partida")))
                .clickEvent(ClickEvent.callback(a -> Bukkit.getScheduler().runTask(pl, usar),
                        ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(segundos + 2)).build())));
        p.sendMessage(Hotel.txt("§8(ou digite §7/reviver§8)"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        // bem visivel: titulo (depois do titulo do proprio jogo) e contagem regressiva na barra de acao
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (feito[0] || !p.isOnline() || System.currentTimeMillis() > ate) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Hotel.txt("§e§lMOEDA DE REVIVER"),
                    Hotel.txt("§fClique no chat ou digite §e/reviver §7(você tem " + n + ")"),
                    net.kyori.adventure.title.Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
        }, 25L);
        final org.bukkit.scheduler.BukkitTask[] conta = {null};
        conta[0] = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            long falta = (ate - System.currentTimeMillis() + 999) / 1000;
            if (feito[0] || !p.isOnline() || falta <= 0) {
                if (conta[0] != null) conta[0].cancel();
                return;
            }
            p.sendActionBar(Hotel.txt("§eMoeda de Reviver §7- digite §f/reviver §7ou clique no chat §8| §c" + falta + "s"));
        }, 20L, 20L);
        if (bedrock(p)) {
            // celular nao clica no chat: abre uma janelinha (um pouco depois, quando o susto ja passou)
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (feito[0] || !p.isOnline() || System.currentTimeMillis() > ate) return;
                try {
                    Janela.perguntar(id, n, segundos, () -> Bukkit.getScheduler().runTask(pl, usar));
                } catch (Throwable t) {
                    p.sendMessage(Hotel.txt("§7(Para usar, digite §f" + CMD + "§7)"));
                }
            }, 30L);
        }
        Pendente.colocar(p.getUniqueId(), usar, ate);
        return true;
    }

    /** Pelo comando "/... reviver" (para quem nao consegue clicar). */
    static boolean usarPendente(Player p) {
        return Pendente.usar(p.getUniqueId());
    }

    /** Esquece a oferta (fim da partida, saiu). */
    static void cancelar(UUID id) {
        Pendente.tirar(id);
    }

    /** Oferta em aberto por jogador (para o comando). */
    private static final class Pendente {
        private static final Map<UUID, Runnable> ACAO = new HashMap<>();
        private static final Map<UUID, Long> ATE = new HashMap<>();

        static void colocar(UUID id, Runnable r, long ate) {
            ACAO.put(id, r);
            ATE.put(id, ate);
        }

        static void tirar(UUID id) {
            ACAO.remove(id);
            ATE.remove(id);
        }

        static boolean usar(UUID id) {
            Runnable r = ACAO.get(id);
            Long ate = ATE.get(id);
            if (r == null || ate == null || System.currentTimeMillis() > ate) {
                tirar(id);
                return false;
            }
            r.run();
            return true;
        }
    }

    /** So e carregada quando o Geyser existe. */
    private static final class Janela {
        static void perguntar(UUID id, int moedas, int segundos, Runnable sim) {
            org.geysermc.geyser.api.connection.GeyserConnection c = org.geysermc.geyser.api.GeyserApi.api().connectionByUuid(id);
            if (c == null) throw new IllegalStateException("sem conexao do Geyser");
            c.sendForm(org.geysermc.cumulus.form.ModalForm.builder()
                    .title("Moeda de Reviver")
                    .content("Você foi eliminado!\n\nVocê tem " + moedas + " Moeda" + (moedas > 1 ? "s" : "") + " de Reviver.\n"
                            + "Quer gastar uma e voltar para a partida?\n\n(" + segundos + " segundos para decidir)")
                    .button1("Sim, voltar!")
                    .button2("Não")
                    .validResultHandler(r -> {
                        if (r.clickedFirst()) sim.run();
                    }));
        }
    }

    private Reviver() {}
}
