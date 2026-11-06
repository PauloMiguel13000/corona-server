package br.servidor.loja;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

/**
 * Compra dentro do jogo (totens de VIP do spawn): "/loja comprar <produto>" cria o pedido no nick do jogador, gera o
 * Pix no Mercado Pago e entrega um MAPA com o QR code (da para ler com o celular olhando para a tela do PC/console).
 * Java: mensagem para copiar o codigo Pix e link para o site. Bedrock (celular): janelinha com o codigo curto do
 * pedido para abrir no site e copiar o Pix la. Quando o pedido e pago (ou expira), o mapa some sozinho.
 */
final class CompraNoJogo {
    private final CoronaLoja pl;
    private final NamespacedKey chaveMapa;
    private final Map<UUID, Long> espera = new HashMap<>();

    CompraNoJogo(CoronaLoja pl) {
        this.pl = pl;
        this.chaveMapa = new NamespacedKey(pl, "pix_mapa");
        Bukkit.getScheduler().runTaskTimer(pl, this::limparMapas, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(pl, this::atualizarTotens, 200L, 20L * 60);
    }

    /** Totens de VIP do spawn (feitos pelo Extras com a chave coronaloja:totem): escreve preco e beneficios atuais. */
    void atualizarTotens() {
        NamespacedKey k = new NamespacedKey(pl, "totem");
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.TextDisplay t : w.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)) {
                String id = t.getPersistentDataContainer().get(k, PersistentDataType.STRING);
                if (id == null) continue;
                Loja.Produto p = pl.produtos().get(id);
                if (p == null) continue;
                net.kyori.adventure.text.format.TextColor cor = net.kyori.adventure.text.format.TextColor.fromHexString(p.cor());
                net.kyori.adventure.text.Component c = net.kyori.adventure.text.Component.text(p.nome())
                        .color(cor == null ? net.kyori.adventure.text.format.NamedTextColor.WHITE : cor)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                StringBuilder sb = new StringBuilder("\n§f§lR$ " + Loja.reais(p.preco()) + ("vip".equals(p.categoria()) ? " §7por 30 dias" : ""));
                if (p.destaque() != null && !p.destaque().isBlank()) sb.append("\n§c§l★ ").append(p.destaque());
                int n = 0;
                for (String b : p.beneficios()) {
                    if (n++ >= 4) break;
                    sb.append("\n§7").append(b);
                }
                sb.append("\n§e▶ clique para comprar com Pix");
                t.text(c.append(CoronaLoja.txt(sb.toString())));
            }
        }
    }

    private static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    void comprar(Player p, String id) {
        Loja.Produto prod = pl.produtos().get(id);
        if (prod == null) {
            p.sendMessage(CoronaLoja.txt(CoronaLoja.PREFIXO + "§cProduto não encontrado."));
            return;
        }
        if (!pl.aberta()) {
            p.sendMessage(CoronaLoja.txt(CoronaLoja.PREFIXO + "§eAs compras abrem em breve!"));
            return;
        }
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        espera.put(p.getUniqueId(), agora + 8000L);

        // ja tem um Pix desse produto esperando? mostra o mesmo (nao cria outro)
        for (Pedidos.Pedido x : pl.pedidos().doJogador(p.getName())) {
            if (!Pedidos.AGUARDANDO.equals(x.status) || !id.equals(x.produto) || x.mpId == null) continue;
            if (x.qrBase64 == null || agora - x.criado > (pl.expiraMinutos() - 3) * 60_000L) continue;
            mostrar(p, x);
            return;
        }
        if (!pl.mp().ativo()) {
            p.sendMessage(CoronaLoja.txt(CoronaLoja.PREFIXO + "§eCompre pelo site: ").append(CoronaLoja.txt("§b§n" + pl.enderecoPublico())
                    .clickEvent(ClickEvent.openUrl(pl.enderecoPublico()))));
            return;
        }
        String nick = p.getName();
        String email = emailDe(nick);
        p.sendMessage(CoronaLoja.txt(CoronaLoja.PREFIXO + "§7Gerando o Pix de §f" + prod.nome() + "§7..."));
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            Pedidos.Pedido ped = pl.pedidos().novo(nick, email, prod.id(), prod.nome(), prod.preco(), prod.comandos(), "jogo");
            pl.log("pedido " + ped.codigo + " criado no jogo (totem): " + nick + " - " + prod.nome() + " R$ " + Loja.reais(prod.preco()));
            try {
                MercadoPago.Pix pix = pl.mp().criar(ped, pl.cfg("servidor.nome", "Corona Server") + " - " + prod.nome() + " (" + nick + ")", pl.expiraMinutos());
                synchronized (pl.pedidos()) {
                    ped.mpId = pix.id();
                    ped.qr = pix.qr();
                    ped.qrBase64 = pix.qrBase64();
                }
                pl.pedidos().salvar();
                pl.log("pedido " + ped.codigo + ": Pix gerado no Mercado Pago (id " + pix.id() + ")");
                Bukkit.getScheduler().runTask(pl, () -> {
                    if (p.isOnline()) mostrar(p, ped);
                });
            } catch (Exception e) {
                pl.pedidos().trocar(ped, Pedidos.AGUARDANDO, Pedidos.CANCELADO);
                pl.log("pedido " + ped.codigo + ": ERRO ao gerar Pix (jogo): " + e.getMessage());
                Bukkit.getScheduler().runTask(pl, () -> p.sendMessage(CoronaLoja.txt(CoronaLoja.PREFIXO
                        + "§cNão consegui gerar o Pix agora. Tente de novo em alguns minutos ou compre pelo site.")));
            }
        });
    }

    /** E-mail para o comprovante do Mercado Pago: o ultimo que o jogador usou no site, senao um generico da loja. */
    private String emailDe(String nick) {
        for (Pedidos.Pedido x : pl.pedidos().doJogador(nick))
            if (x.email != null && !x.email.isBlank() && !x.email.startsWith("jogo@")) return x.email;
        String dom = pl.https() == null ? "" : pl.https().dominio();
        return "jogo@" + (dom.isEmpty() ? "coronaserver.playit.plus" : dom);
    }

    private void mostrar(Player p, Pedidos.Pedido ped) {
        String site = pl.enderecoPublico();
        String linkPedido = site + (site.endsWith("/") ? "" : "/") + "?pedido=" + ped.codigo;
        boolean deuMapa = darMapa(p, ped);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        p.sendMessage(CoronaLoja.txt(" "));
        p.sendMessage(CoronaLoja.txt(CoronaLoja.PREFIXO + "§f" + ped.nomeProduto + " §7por §a§lR$ " + Loja.reais(ped.valor) + " §7para §f" + ped.jogador));
        if (deuMapa) p.sendMessage(CoronaLoja.txt("§7Segure o §fmapa§7 e leia o QR code com o app do banco (Pix)."));
        p.sendMessage(CoronaLoja.txt("§a§l[ COPIAR CÓDIGO PIX ]").hoverEvent(HoverEvent.showText(CoronaLoja.txt("§7Copia o Pix copia e cola")))
                .clickEvent(ClickEvent.copyToClipboard(ped.qr == null ? "" : ped.qr))
                .append(CoronaLoja.txt("  "))
                .append(CoronaLoja.txt("§b§l[ ABRIR NO SITE ]").hoverEvent(HoverEvent.showText(CoronaLoja.txt("§7" + linkPedido)))
                        .clickEvent(ClickEvent.openUrl(linkPedido))));
        p.sendMessage(CoronaLoja.txt("§7Pelo celular: abra §f" + site.replaceFirst("^https?://", "") + " §7e use o código §e§l" + ped.codigo + "§7."));
        p.sendMessage(CoronaLoja.txt("§8Vale por " + pl.expiraMinutos() + " minutos. Pagou? Chega sozinho, em segundos."));
        if (bedrock(p)) {
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                try {
                    Janela.mostrar(p.getUniqueId(), ped, site, deuMapa, pl.expiraMinutos());
                } catch (Throwable ignored) {
                }
            }, 10L);
        }
    }

    // ------------------------------------------------------------------ mapa com o QR

    private boolean darMapa(Player p, Pedidos.Pedido ped) {
        boolean[][] m = matriz(ped.qrBase64);
        if (m == null) return false;
        tirarMapas(p, null);
        MapView view = Bukkit.createMap(p.getWorld());
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new Desenho(m));
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) it.getItemMeta();
        meta.setMapView(view);
        meta.customName(CoronaLoja.txt("§a§lPix: §f" + ped.nomeProduto + " §7(R$ " + Loja.reais(ped.valor) + ")"));
        meta.lore(List.of(CoronaLoja.txt("§7Leia com o app do banco"), CoronaLoja.txt("§8Pedido " + ped.codigo)));
        meta.getPersistentDataContainer().set(chaveMapa, PersistentDataType.STRING, ped.codigo);
        it.setItemMeta(meta);
        // vai para a mao (o que estava la vai para outro lugar do inventario)
        ItemStack naMao = p.getInventory().getItemInMainHand();
        p.getInventory().setItemInMainHand(it);
        if (naMao != null && !naMao.getType().isAir()) {
            for (ItemStack sobra : p.getInventory().addItem(naMao).values()) p.getWorld().dropItem(p.getLocation(), sobra);
        }
        return true;
    }

    /** Some com os mapas de Pix (todos, ou so os de pedidos que ja nao estao esperando quando codigo == null). */
    private void tirarMapas(Player p, String soCodigo) {
        ItemStack[] c = p.getInventory().getContents();
        boolean mudou = false;
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null || c[i].getType() != Material.FILLED_MAP || !c[i].hasItemMeta()) continue;
            String cod = c[i].getItemMeta().getPersistentDataContainer().get(chaveMapa, PersistentDataType.STRING);
            if (cod == null || (soCodigo != null && !soCodigo.equals(cod))) continue;
            c[i] = null;
            mudou = true;
        }
        if (mudou) p.getInventory().setContents(c);
    }

    /** A cada 5 s: tira o mapa de quem ja pagou (ou deixou expirar). */
    private void limparMapas() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (it == null || it.getType() != Material.FILLED_MAP || !it.hasItemMeta()) continue;
                String cod = it.getItemMeta().getPersistentDataContainer().get(chaveMapa, PersistentDataType.STRING);
                if (cod == null) continue;
                Pedidos.Pedido ped = pl.pedidos().pegar(cod);
                if (ped == null || !Pedidos.AGUARDANDO.equals(ped.status)) tirarMapas(p, cod);
            }
        }
    }

    /** Le o PNG do QR do Mercado Pago e devolve os modulos (true = preto), sem a margem. */
    static boolean[][] matriz(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            int x0 = w, y0 = h, x1 = -1, y1 = -1;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (escuro(img.getRGB(x, y))) {
                        x0 = Math.min(x0, x);
                        y0 = Math.min(y0, y);
                        x1 = Math.max(x1, x);
                        y1 = Math.max(y1, y);
                    }
            if (x1 < 0) return null;
            // o quadrado do canto (localizador) tem 7 modulos: mede o tamanho de um modulo pela primeira linha
            // (varias linhas do topo: a borda de cima do localizador e a corrida mais longa; as de dentro sao curtas)
            int run = 0;
            for (int y = y0; y <= Math.min(y1, y0 + (x1 - x0) / 20); y++) {
                int r = 0;
                while (x0 + r <= x1 && escuro(img.getRGB(x0 + r, y))) r++;
                run = Math.max(run, r);
            }
            double mod = run / 7.0;
            int n = (int) Math.round((x1 - x0 + 1) / mod);
            if (n < 21 || n > 177 || (n - 17) % 4 != 0) {
                // arredonda para um tamanho de QR valido (21, 25, 29, ...)
                n = 21 + Math.max(0, Math.round((n - 21) / 4f)) * 4;
            }
            double passo = (x1 - x0 + 1) / (double) n;
            boolean[][] m = new boolean[n][n];
            for (int my = 0; my < n; my++)
                for (int mx = 0; mx < n; mx++) {
                    int px = (int) (x0 + (mx + 0.5) * passo), py = (int) (y0 + (my + 0.5) * passo);
                    m[my][mx] = escuro(img.getRGB(Math.min(px, w - 1), Math.min(py, h - 1)));
                }
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean escuro(int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return a > 128 && (r + g + b) / 3 < 128;
    }

    /** Desenha o QR no mapa (128x128) com modulos de tamanho inteiro e margem branca. */
    static final class Desenho extends MapRenderer {
        private final boolean[][] m;
        private boolean feito;

        Desenho(boolean[][] m) {
            super(false);
            this.m = m;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void render(MapView view, MapCanvas canvas, Player player) {
            if (feito) return;
            feito = true;
            int n = m.length;
            int esc = Math.max(1, (128 - 8) / n);
            int lado = esc * n, off = (128 - lado) / 2;
            byte branco = MapPalette.matchColor(255, 255, 255), preto = MapPalette.matchColor(0, 0, 0);
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) canvas.setPixel(x, y, branco);
            for (int my = 0; my < n; my++)
                for (int mx = 0; mx < n; mx++) {
                    if (!m[my][mx]) continue;
                    for (int dy = 0; dy < esc; dy++) for (int dx = 0; dx < esc; dx++) canvas.setPixel(off + mx * esc + dx, off + my * esc + dy, preto);
                }
        }
    }

    /** Janela do Bedrock (so carrega com o Geyser). */
    private static final class Janela {
        static void mostrar(UUID id, Pedidos.Pedido ped, String site, boolean mapa, int minutos) {
            org.geysermc.geyser.api.connection.GeyserConnection c = org.geysermc.geyser.api.GeyserApi.api().connectionByUuid(id);
            if (c == null) return;
            String s = site.replaceFirst("^https?://", "");
            c.sendForm(org.geysermc.cumulus.form.ModalForm.builder()
                    .title("Pix: " + ped.nomeProduto)
                    .content(ped.nomeProduto + " por R$ " + Loja.reais(ped.valor) + " para " + ped.jogador + ".\n\n"
                            + (mapa ? "Jogando no PC ou console? Leia o QR code do mapa com o app do banco.\n\n" : "")
                            + "Jogando no celular? Abra no navegador:\n" + s + "\ne use o código do pedido:\n\n" + ped.codigo + "\n\n"
                            + "Lá aparece o Pix copia e cola. Vale por " + minutos + " minutos e chega sozinho quando pagar.")
                    .button1("Entendi")
                    .button2("Fechar"));
        }
    }
}
