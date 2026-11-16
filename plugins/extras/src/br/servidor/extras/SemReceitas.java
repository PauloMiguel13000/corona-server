package br.servidor.extras;

import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

/**
 * 16/11: nos minigames e lobbies o jogo ficava mostrando "Nova receita desbloqueada" (no Bedrock aparece no canto da
 * tela) toda vez que o kit dava um item novo (corante da dificuldade, estrela, armas...). Ali ninguem fabrica nada,
 * entao a receita nao e liberada e o aviso nao aparece. No survival, masmorras, Prisao, plots e Apocalipse continua igual.
 */
final class SemReceitas implements Listener {

    private static final Set<String> MINIGAMES = Set.of("login", "minigames", "terror", "tiro", "missao", "operacao", "hotel",
            "esconde", "naochore", "naochore_reserva", "porteiro", "slender", "turno_noite", "vovo", "backrooms", "arena", "defesa", "galeria");

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void descobriu(PlayerRecipeDiscoverEvent e) {
        if (MINIGAMES.contains(e.getPlayer().getWorld().getName())) e.setCancelled(true);
    }
}
