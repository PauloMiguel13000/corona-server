package br.servidor.loja;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * HTTPS do site: certificado gratis do Let's Encrypt (Acme.java), renovado sozinho, servido na porta do https.yml
 * (o tunel do playit manda a porta 443 para ca). Configuracao em plugins/CoronaLoja/https.yml.
 * Antes do primeiro certificado de verdade, faz um ensaio no ambiente de teste do Let's Encrypt (nao gasta limite).
 */
final class Https {
    private final CoronaLoja pl;
    private final Web web;
    private final String host;
    private final File pasta;
    private YamlConfiguration cfg;
    private HttpsServer servidor;
    private ExecutorService exec;
    private volatile boolean publicoOk;
    private volatile String situacao = "desligado";
    private volatile X509Certificate atual;

    Https(CoronaLoja pl, Web web, String host) {
        this.pl = pl;
        this.web = web;
        this.host = host;
        this.pasta = new File(pl.getDataFolder(), "https");
    }

    void carregar() {
        File f = new File(pl.getDataFolder(), "https.yml");
        if (!f.exists() && pl.getResource("https.yml") != null) pl.saveResource("https.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    boolean ativo() {
        return cfg != null && cfg.getBoolean("ativo", true) && !dominio().isEmpty();
    }

    String dominio() {
        return cfg == null ? "" : cfg.getString("dominio", "").trim().toLowerCase(Locale.ROOT);
    }

    private int porta() {
        return cfg.getInt("porta", 8443);
    }

    /** Ja tem certificado servindo e o https publico respondeu. */
    boolean funcionando() {
        return servidor != null && publicoOk;
    }

    /** Mandar quem abre http:// (pelo dominio) para https:// ? */
    boolean redirecionar() {
        String m = cfg == null ? "nao" : String.valueOf(cfg.get("redirecionar", "auto")).toLowerCase(Locale.ROOT);
        if (servidor == null) return false;
        return switch (m) {
            case "sim", "true" -> true;
            case "auto" -> publicoOk;
            default -> false;
        };
    }

    String situacao() {
        X509Certificate c = atual;
        return situacao + (c == null ? "" : " | certificado ate " + c.getNotAfter()) + " | https publico: " + (publicoOk ? "ok" : "nao conferido/falhou")
                + " | redireciona http: " + (redirecionar() ? "sim" : "nao");
    }

    /** Na hora de ligar o plugin: se ja tem certificado bom, ja abre o https. O resto (pedir/renovar) e no verificar(). */
    void iniciar() {
        carregar();
        if (!ativo()) {
            situacao = "desligado no https.yml";
            return;
        }
        try {
            if (certificadoBom(0)) abrir();
        } catch (Exception e) {
            pl.log("https: nao abriu com o certificado salvo: " + e);
        }
    }

    /** Roda fora do thread principal: pede/renova o certificado se precisar, abre o https e confere de fora. */
    synchronized void verificar(boolean forcar) {
        if (!ativo()) return;
        String dom = dominio();
        try {
            int renovar = Math.max(7, cfg.getInt("renovar-dias", 30));
            if (forcar || !certificadoBom(renovar)) {
                File ensaio = new File(pasta, "teste-ok");
                if (!ensaio.exists() && cfg.getBoolean("ensaio-antes", true)) {
                    situacao = "ensaiando no ambiente de teste do Let's Encrypt...";
                    pl.log("https: ensaio no ambiente de teste do Let's Encrypt para " + dom);
                    acme(true, s -> pl.log("https (teste): " + s)).emitir(dom);
                    pasta.mkdirs();
                    Files.writeString(ensaio.toPath(), "ok " + new java.util.Date() + "\n", StandardCharsets.US_ASCII);
                    pl.log("https: ensaio ok, pedindo o certificado de verdade");
                }
                situacao = "pedindo certificado ao Let's Encrypt...";
                acme(false, s -> pl.log("https: " + s)).emitir(dom);
                abrir();
            } else if (servidor == null) {
                abrir();
            }
            situacao = "https ligado na porta " + porta();
        } catch (Exception e) {
            situacao = "erro: " + e.getMessage();
            pl.log("https: ERRO " + e);
            pl.getLogger().warning("HTTPS da loja: " + e.getMessage());
        }
        testarPublico();
    }

    /** acme-diretorio (escondido) so serve para testar com um servidor ACME de mentira. */
    private Acme acme(boolean teste, Acme.Log log) {
        String d = cfg.getString("acme-diretorio", "");
        if (d == null || d.isBlank()) return new Acme(pasta, teste, log);
        return new Acme(d, pasta, teste ? "teste-" : "", log);
    }

    private boolean certificadoBom(int diasMinimos) {
        try {
            Acme a = new Acme(pasta, false, s -> {});
            File fc = a.arquivoCertificado(), fk = a.arquivoChave();
            if (!fc.exists() || !fk.exists()) return false;
            List<X509Certificate> c = Acme.lerCadeia(Files.readString(fc.toPath(), StandardCharsets.US_ASCII));
            if (c.isEmpty()) return false;
            X509Certificate x = c.get(0);
            boolean cobre = false;
            if (x.getSubjectAlternativeNames() != null)
                for (List<?> n : x.getSubjectAlternativeNames())
                    if (n.size() > 1 && dominio().equalsIgnoreCase(String.valueOf(n.get(1)))) cobre = true;
            long resta = x.getNotAfter().getTime() - System.currentTimeMillis();
            return cobre && resta > diasMinimos * 86_400_000L;
        } catch (Exception e) {
            return false;
        }
    }

    /** (Re)abre o servidor https com o certificado salvo. */
    private synchronized void abrir() throws Exception {
        Acme a = new Acme(pasta, false, s -> {});
        PrivateKey chave = Acme.lerChave(a.arquivoChave());
        List<X509Certificate> cadeia = Acme.lerCadeia(Files.readString(a.arquivoCertificado().toPath(), StandardCharsets.US_ASCII));
        char[] senha = "loja".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("loja", chave, senha, cadeia.toArray(new X509Certificate[0]));
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, senha);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        parar();
        HttpsServer s = HttpsServer.create(new InetSocketAddress(host, porta()), 64);
        s.setHttpsConfigurator(new HttpsConfigurator(ctx));
        exec = Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "CoronaLoja-https");
            t.setDaemon(true);
            return t;
        });
        s.setExecutor(exec);
        s.createContext("/", ex -> web.tratar(ex, true));
        s.start();
        servidor = s;
        atual = cadeia.get(0);
        pl.log("https ligado em " + host + ":" + porta() + " (certificado ate " + atual.getNotAfter() + ")");
        pl.getLogger().info("HTTPS da loja ligado na porta " + porta() + " (certificado ate " + atual.getNotAfter() + ")");
    }

    /** Abre https://dominio/ de fora (pela internet, pelo playit) para saber se o https chega mesmo. */
    void testarPublico() {
        if (servidor == null) {
            publicoOk = false;
            return;
        }
        boolean antes = publicoOk;
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create("https://" + dominio() + "/api/loja")).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "CoronaLoja-teste").GET().build(), HttpResponse.BodyHandlers.ofString());
            publicoOk = r.statusCode() == 200 && r.body().contains("\"ok\":true");
        } catch (Exception e) {
            publicoOk = false;
            if (antes) pl.log("https publico parou de responder: " + e);
        }
        if (publicoOk != antes) pl.log("https publico: " + (publicoOk ? "OK (http:// passa a ir para https://)" : "nao respondeu"));
    }

    void parar() {
        if (servidor != null) servidor.stop(0);
        servidor = null;
        if (exec != null) exec.shutdownNow();
        exec = null;
    }
}
