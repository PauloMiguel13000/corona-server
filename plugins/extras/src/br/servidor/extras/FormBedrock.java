package br.servidor.extras;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 14/11 (Paulo: "melhore muito"): no Bedrock (celular/console) o /ajuda abre um MENU NATIVO de botoes (formulario do
 * Geyser), bem mais facil de tocar que o bau. So carrega se o Floodgate estiver instalado; senao cai no bau.
 */
final class FormBedrock {
    private FormBedrock() {}

    static boolean ehBedrock(Player p) {
        try {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    static String limpar(String s) { return s.replaceAll("§.", ""); }

    /** Devolve true se mandou o formulario. */
    static boolean ajuda(Extras pl, Player p, List<Ajuda.Item> itens) {
        try {
            var b = org.geysermc.cumulus.form.SimpleForm.builder()
                    .title("Guia do Corona Server")
                    .content("Toque em uma opção para ir direto. Abra este guia quando quiser: /ajuda");
            for (Ajuda.Item i : itens) b.button(limpar(i.nome()));
            b.validResultHandler(r -> {
                int id = r.clickedButtonId();
                if (id < 0 || id >= itens.size()) return;
                Bukkit.getScheduler().runTask(pl, () -> { if (p.isOnline()) p.performCommand(itens.get(id).comando()); });
            });
            org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(p.getUniqueId(), b.build());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
