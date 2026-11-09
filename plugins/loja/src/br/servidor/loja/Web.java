package br.servidor.loja;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** O site da loja: paginas (web/) e a API usada pela pagina. */
final class Web {

    private final CoronaLoja pl;
    private HttpServer server;
    private ExecutorService exec;
    private final Map<String, byte[]> arquivos = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> pedidosPorIp = new HashMap<>();

    Web(CoronaLoja pl) {
        this.pl = pl;
    }

    void iniciar(String host, int porta) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, porta), 64);
        exec = Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "CoronaLoja-site");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(exec);
        server.createContext("/", ex -> tratar(ex, false));
        server.start();
    }

    void parar() {
        if (server != null) server.stop(0);
        if (exec != null) exec.shutdownNow();
    }

    /** seguro = chegou pelo https. */
    void tratar(HttpExchange ex, boolean seguro) throws IOException {
        try {
            String caminho = ex.getRequestURI().getPath();
            String metodo = ex.getRequestMethod().toUpperCase(Locale.ROOT);
            // desafio do Let's Encrypt (certificado do https)
            if (caminho.startsWith("/.well-known/acme-challenge/")) {
                String r = Acme.DESAFIOS.get(caminho.substring("/.well-known/acme-challenge/".length()));
                if (r == null) responder(ex, 404, "text/plain; charset=utf-8", "Nao encontrado".getBytes(StandardCharsets.UTF_8));
                else responder(ex, 200, "text/plain; charset=utf-8", r.getBytes(StandardCharsets.US_ASCII));
                return;
            }
            Https h = pl.https();
            if (!seguro && h != null && h.redirecionar() && (metodo.equals("GET") || metodo.equals("HEAD"))) {
                String hostPedido = ex.getRequestHeaders().getFirst("Host");
                if (hostPedido != null && hostPedido.split(":")[0].equalsIgnoreCase(h.dominio())) {
                    String q = ex.getRequestURI().getRawQuery();
                    ex.getResponseHeaders().set("Location", "https://" + h.dominio() + ex.getRequestURI().getRawPath() + (q == null ? "" : "?" + q));
                    ex.getResponseHeaders().set("Cache-Control", "no-store");
                    ex.sendResponseHeaders(301, -1);
                    return;
                }
            }
            if (caminho.startsWith("/pacotes/")) {
                pacoteBedrock(ex, caminho.substring("/pacotes/".length()), metodo);
                return;
            }
            switch (caminho) {
                case "/api/loja" -> responderJson(ex, 200, apiLoja());
                case "/api/comprar" -> {
                    if (!metodo.equals("POST")) responderJson(ex, 405, erro("Use POST."));
                    else responderJson(ex, 200, apiComprar(ex));
                }
                case "/api/pedido" -> responderJson(ex, 200, apiPedido(consulta(ex).get("codigo")));
                case "/api/historico" -> responderJson(ex, 200, apiHistorico(consulta(ex).get("jogador")));
                default -> estatico(ex, caminho);
            }
        } catch (Exception e) {
            pl.log("ERRO no site: " + e);
            try {
                responderJson(ex, 500, erro("Erro no servidor da loja. Tente de novo."));
            } catch (Exception ignored) {
            }
        } finally {
            ex.close();
        }
    }

    // ------------------------------------------------------------------ pacotes do Bedrock (o celular baixa daqui)

    /**
     * Pacotes de recursos do Bedrock que o CoronaPack poe em plugins/CoronaPack/pacotes-site/. O Bedrock exige link
     * direto, Content-Length exato e Content-Type application/zip. Aceita HEAD e Range (download em pedacos).
     * Cada pedido vai para o log (ajuda a ver se o celular chegou a pedir).
     */
    private void pacoteBedrock(HttpExchange ex, String nome, String metodo) throws IOException {
        String ua = String.valueOf(ex.getRequestHeaders().getFirst("User-Agent"));
        String range = ex.getRequestHeaders().getFirst("Range");
        java.net.InetSocketAddress de = ex.getRemoteAddress();
        pl.getLogger().info("Pacote Bedrock pedido: " + metodo + " " + nome + (range == null ? "" : " " + range) + " (" + (ua.length() > 80 ? ua.substring(0, 80) : ua) + ")"
                + (de == null ? "" : " de " + de.getAddress().getHostAddress()));
        java.io.File f = new java.io.File(new java.io.File(pl.getDataFolder().getParentFile(), "CoronaPack/pacotes-site"), nome);
        if (!nome.matches("^[A-Za-z0-9_-]+\\.zip$") || !f.isFile()) {
            responder(ex, 404, "text/plain; charset=utf-8", "Nao encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!metodo.equals("GET") && !metodo.equals("HEAD")) {
            responder(ex, 405, "text/plain; charset=utf-8", "Use GET".getBytes(StandardCharsets.UTF_8));
            return;
        }
        long tam = f.length(), ini = 0, fim = tam - 1;
        int codigo = 200;
        if (range != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^bytes=(\\d*)-(\\d*)$").matcher(range.trim());
            if (m.matches() && !(m.group(1).isEmpty() && m.group(2).isEmpty())) {
                if (m.group(1).isEmpty()) {
                    ini = Math.max(0, tam - Long.parseLong(m.group(2)));
                } else {
                    ini = Long.parseLong(m.group(1));
                    if (!m.group(2).isEmpty()) fim = Math.min(tam - 1, Long.parseLong(m.group(2)));
                }
                if (ini > fim || ini >= tam) {
                    ex.getResponseHeaders().set("Content-Range", "bytes */" + tam);
                    ex.sendResponseHeaders(416, -1);
                    return;
                }
                codigo = 206;
                ex.getResponseHeaders().set("Content-Range", "bytes " + ini + "-" + fim + "/" + tam);
            }
        }
        long n = fim - ini + 1;
        ex.getResponseHeaders().set("Content-Type", "application/zip");
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        ex.getResponseHeaders().set("ETag", "\"" + nome.replace(".zip", "") + "-" + tam + "\"");
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        if (metodo.equals("HEAD")) {
            ex.getResponseHeaders().set("Content-Length", String.valueOf(n));
            ex.sendResponseHeaders(codigo, -1);
            return;
        }
        ex.sendResponseHeaders(codigo, n);
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r"); java.io.OutputStream out = ex.getResponseBody()) {
            raf.seek(ini);
            byte[] buf = new byte[64 * 1024];
            long falta = n;
            while (falta > 0) {
                int r = raf.read(buf, 0, (int) Math.min(buf.length, falta));
                if (r < 0) break;
                out.write(buf, 0, r);
                falta -= r;
            }
        }
    }

    // ------------------------------------------------------------------ paginas

    private void estatico(HttpExchange ex, String caminho) throws IOException {
        if (caminho.equals("/") || caminho.isEmpty()) caminho = "/index.html";
        if (!caminho.matches("^/[a-z0-9._-]+$")) {
            responder(ex, 404, "text/plain; charset=utf-8", "Nao encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        // plugins/CoronaLoja/web/ (se existir o arquivo) tem prioridade: muda o visual do site sem reiniciar o servidor
        java.io.File noDisco = new java.io.File(pl.getDataFolder(), "web" + caminho);
        byte[] b;
        boolean doDisco = noDisco.isFile();
        if (doDisco) {
            b = java.nio.file.Files.readAllBytes(noDisco.toPath());
        } else {
            b = arquivos.computeIfAbsent(caminho, k -> {
                try (InputStream in = pl.getResource("web" + k)) {
                    return in == null ? new byte[0] : in.readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });
        }
        if (b.length == 0) {
            arquivos.remove(caminho);
            responder(ex, 404, "text/plain; charset=utf-8", "Nao encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String tipo = caminho.endsWith(".html") ? "text/html; charset=utf-8"
                : caminho.endsWith(".js") ? "application/javascript; charset=utf-8"
                : caminho.endsWith(".css") ? "text/css; charset=utf-8"
                : caminho.endsWith(".png") ? "image/png"
                : caminho.endsWith(".jpg") ? "image/jpeg"
                : caminho.endsWith(".svg") ? "image/svg+xml"
                : caminho.endsWith(".ico") ? "image/x-icon"
                : caminho.endsWith(".woff2") ? "font/woff2" : "application/octet-stream";
        ex.getResponseHeaders().set("Cache-Control", doDisco || caminho.endsWith(".html") ? "no-cache" : "public, max-age=300");
        responder(ex, 200, tipo, b);
    }

    // ------------------------------------------------------------------ API

    private String apiLoja() {
        StringBuilder b = new StringBuilder("{\"ok\":true,\"servidor\":{");
        b.append("\"nome\":").append(Loja.json(pl.cfg("servidor.nome", "Corona Server"))).append(',');
        b.append("\"online\":").append(pl.online()).append(',');
        b.append("\"max\":").append(pl.maxJogadores()).append(',');
        b.append("\"ipJava\":").append(Loja.json(pl.cfg("servidor.ip-java", ""))).append(',');
        b.append("\"ipBedrock\":").append(Loja.json(pl.cfg("servidor.ip-bedrock", ""))).append(',');
        b.append("\"portaBedrock\":").append(Loja.json(pl.cfg("servidor.porta-bedrock", ""))).append(',');
        b.append("\"discord\":").append(Loja.json(pl.cfg("servidor.discord", "https://discord.gg/uhEaWb9sJA")));
        b.append("},\"modo\":").append(Loja.json(pl.mp().ativo() ? "auto" : "manual"));
        b.append(",\"pedeEmail\":").append(pl.mp().ativo());
        b.append(",\"aberta\":").append(pl.aberta());
        b.append(",\"produtos\":[");
        List<String> ps = new ArrayList<>();
        for (Loja.Produto p : pl.produtos().values()) {
            ps.add("{\"id\":" + Loja.json(p.id()) + ",\"nome\":" + Loja.json(p.nome()) + ",\"categoria\":" + Loja.json(p.categoria())
                    + ",\"preco\":" + String.format(Locale.ROOT, "%.2f", p.preco()) + ",\"cor\":" + Loja.json(p.cor())
                    + ",\"destaque\":" + Loja.json(p.destaque()) + ",\"descricao\":" + Loja.json(p.descricao())
                    + ",\"beneficios\":" + Loja.jsonLista(p.beneficios()) + "}");
        }
        b.append(String.join(",", ps)).append("]}");
        return b.toString();
    }

    private String apiComprar(HttpExchange ex) throws IOException {
        Map<String, String> f = formulario(ex);
        String jogador = Loja.limparNick(f.get("jogador"));
        String email = f.getOrDefault("email", "").trim();
        String prodId = f.getOrDefault("produto", "").trim();
        Loja.Produto prod = pl.produtos().get(prodId);
        if (prod == null) return erro("Produto não encontrado.");
        if (!pl.aberta()) return erro("As compras abrem em breve. Volte daqui a pouco!");
        if (!Loja.NICK.matcher(jogador).matches()) return erro("Nick inválido. Use o nome exato do jogo (3 a 16 letras, números ou _).");
        boolean auto = pl.mp().ativo();
        if (auto && (email.length() > 100 || !Loja.EMAIL.matcher(email).matches())) return erro("Digite um e-mail válido (o Mercado Pago pede para o comprovante).");
        if (!auto) email = email.length() <= 100 && Loja.EMAIL.matcher(email).matches() ? email : "";
        String ip = ip(ex);
        if (!liberarIp(ip)) return erro("Muitos pedidos seguidos. Espere alguns minutos e tente de novo.");

        Pedidos.Pedido p = pl.pedidos().novo(jogador, email, prod.id(), prod.nome(), prod.preco(), prod.comandos(), ip);
        pl.log("pedido " + p.codigo + " criado: " + jogador + " - " + prod.nome() + " R$ " + Loja.reais(prod.preco()) + " (" + (auto ? "Pix automatico" : "Pix manual") + ", ip " + ip + ")");
        if (auto) {
            try {
                MercadoPago.Pix pix = pl.mp().criar(p, pl.cfg("servidor.nome", "Corona Server") + " - " + prod.nome() + " (" + jogador + ")", pl.expiraMinutos());
                synchronized (pl.pedidos()) {
                    p.mpId = pix.id();
                    p.qr = pix.qr();
                    p.qrBase64 = pix.qrBase64();
                }
                pl.pedidos().salvar();
                pl.log("pedido " + p.codigo + ": Pix gerado no Mercado Pago (id " + pix.id() + ")");
            } catch (Exception e) {
                pl.pedidos().trocar(p, Pedidos.AGUARDANDO, Pedidos.CANCELADO);
                pl.log("pedido " + p.codigo + ": ERRO ao gerar Pix: " + e.getMessage());
                return erro("Não consegui gerar o Pix agora. Tente de novo em alguns minutos.");
            }
        }
        synchronized (pl.pedidos()) {
            return pedidoJson(p);
        }
    }

    private String apiPedido(String codigo) {
        Pedidos.Pedido p = pl.pedidos().pegar(codigo);
        if (p == null) return erro("Pedido não encontrado.");
        synchronized (pl.pedidos()) {
            return pedidoJson(p);
        }
    }

    private String apiHistorico(String jogador) {
        jogador = Loja.limparNick(jogador);
        if (!Loja.NICK.matcher(jogador).matches()) return erro("Nick inválido.");
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.of("pt", "BR"));
        fmt.setTimeZone(TimeZone.getTimeZone("America/Bahia"));
        List<String> l = new ArrayList<>();
        for (Pedidos.Pedido p : pl.pedidos().doJogador(jogador)) {
            l.add("{\"codigo\":" + Loja.json(p.codigo) + ",\"produto\":" + Loja.json(p.nomeProduto) + ",\"valor\":"
                    + String.format(Locale.ROOT, "%.2f", p.valor) + ",\"status\":" + Loja.json(p.status) + ",\"data\":" + Loja.json(fmt.format(new Date(p.criado))) + "}");
        }
        return "{\"ok\":true,\"pedidos\":[" + String.join(",", l) + "]}";
    }

    private String pedidoJson(Pedidos.Pedido p) {
        boolean auto = p.mpId != null;
        StringBuilder b = new StringBuilder("{\"ok\":true");
        b.append(",\"codigo\":").append(Loja.json(p.codigo));
        b.append(",\"jogador\":").append(Loja.json(p.jogador));
        b.append(",\"produto\":").append(Loja.json(p.nomeProduto));
        b.append(",\"valor\":").append(String.format(Locale.ROOT, "%.2f", p.valor));
        b.append(",\"status\":").append(Loja.json(p.status));
        b.append(",\"modo\":").append(Loja.json(auto ? "auto" : "manual"));
        b.append(",\"expiraEm\":").append(p.criado + (long) (auto ? pl.expiraMinutos() : pl.expiraManualHoras() * 60) * 60_000L);
        if (p.status.equals(Pedidos.AGUARDANDO)) {
            if (auto) {
                b.append(",\"qr\":").append(Loja.json(p.qr));
                b.append(",\"qrImg\":").append(Loja.json(p.qrBase64));
            } else {
                b.append(",\"chave\":").append(Loja.json(pl.cfg("pix-manual.chave", "")));
                b.append(",\"recebedor\":").append(Loja.json(pl.cfg("pix-manual.nome-recebedor", "")));
            }
        }
        return b.append('}').toString();
    }

    private static String erro(String msg) {
        return "{\"ok\":false,\"erro\":" + Loja.json(msg) + "}";
    }

    // ------------------------------------------------------------------ utilidades

    private synchronized boolean liberarIp(String ip) {
        long agora = System.currentTimeMillis();
        Deque<Long> d = pedidosPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        while (!d.isEmpty() && agora - d.peekFirst() > 10 * 60_000L) d.pollFirst();
        if (d.size() >= 6) return false;
        d.addLast(agora);
        if (pedidosPorIp.size() > 5000) pedidosPorIp.clear();
        return true;
    }

    private static String ip(HttpExchange ex) {
        String xf = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        String cf = ex.getRequestHeaders().getFirst("X-Real-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private static Map<String, String> consulta(HttpExchange ex) {
        return decodificar(ex.getRequestURI().getRawQuery());
    }

    private static Map<String, String> formulario(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 8192) throw new IOException("formulario grande demais");
                out.write(buf, 0, n);
            }
            return decodificar(out.toString(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> decodificar(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String par : s.split("&")) {
            int i = par.indexOf('=');
            String k = i < 0 ? par : par.substring(0, i);
            String v = i < 0 ? "" : par.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void responderJson(HttpExchange ex, int codigo, String json) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        responder(ex, codigo, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void responder(HttpExchange ex, int codigo, String tipo, byte[] corpo) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.sendResponseHeaders(codigo, corpo.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(corpo);
        }
    }
}
