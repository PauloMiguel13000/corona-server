package br.servidor.loja;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente ACME (RFC 8555) minimo, so com o Java: pede o certificado gratis do Let's Encrypt para o dominio do site,
 * com o desafio HTTP (o Let's Encrypt abre http://dominio/.well-known/acme-challenge/... e o proprio site responde).
 * Nada de programa de fora. Conta e chaves ficam em plugins/CoronaLoja/https/.
 */
final class Acme {
    static final String PRODUCAO = "https://acme-v02.api.letsencrypt.org/directory";
    static final String TESTE = "https://acme-staging-v02.api.letsencrypt.org/directory";

    /** Desafios em aberto: token -> resposta (lido pelo site). Compartilhado entre emissoes. */
    static final Map<String, String> DESAFIOS = new ConcurrentHashMap<>();

    interface Log {
        void info(String s);
    }

    private final String diretorio;
    private final File pasta;
    private final String prefixo;
    private final Log log;
    private final HttpClient http;
    private KeyPair conta;
    private String kid;
    private String nonce;
    private JsonObject dir;

    /** teste = ambiente de teste do Let's Encrypt (nao vale no navegador, so para conferir que tudo funciona). */
    Acme(File pasta, boolean teste, Log log) {
        this(teste ? TESTE : PRODUCAO, pasta, teste ? "teste-" : "", log);
    }

    Acme(String diretorio, File pasta, String prefixo, Log log) {
        this.diretorio = diretorio;
        this.pasta = pasta;
        this.prefixo = prefixo;
        this.log = log;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    File arquivoChave() {
        return new File(pasta, prefixo + "dominio.key");
    }

    File arquivoCertificado() {
        return new File(pasta, prefixo + "certificado.pem");
    }

    /** Pede o certificado (demora uns segundos; rodar fora do thread principal). Salva chave e certificado. */
    void emitir(String dominio) throws Exception {
        pasta.mkdirs();
        conta = carregarOuCriarChave(new File(pasta, prefixo + "conta.key"));
        dir = json(get(diretorio).body());
        nonce = null;
        novoNonce();

        // conta (com a mesma chave, o Let's Encrypt devolve a conta que ja existe)
        HttpResponse<String> r = post(dir.get("newAccount").getAsString(), "{\"termsOfServiceAgreed\":true}", true);
        kid = r.headers().firstValue("Location").orElseThrow(() -> new IllegalStateException("conta sem Location"));
        log.info("conta ACME ok (" + (r.statusCode() == 201 ? "nova" : "ja existia") + ")");

        // pedido
        r = post(dir.get("newOrder").getAsString(), "{\"identifiers\":[{\"type\":\"dns\",\"value\":\"" + dominio + "\"}]}", false);
        String urlPedido = r.headers().firstValue("Location").orElseThrow(() -> new IllegalStateException("pedido sem Location"));
        JsonObject pedido = json(r.body());
        String thumb = thumbprint((ECPublicKey) conta.getPublic());
        List<String> tokens = new ArrayList<>();
        try {
            for (JsonElement a : pedido.getAsJsonArray("authorizations")) {
                String urlAut = a.getAsString();
                JsonObject aut = json(post(urlAut, null, false).body());
                if ("valid".equals(str(aut, "status"))) continue;
                JsonObject desafio = null;
                for (JsonElement c : aut.getAsJsonArray("challenges"))
                    if ("http-01".equals(str(c.getAsJsonObject(), "type"))) desafio = c.getAsJsonObject();
                if (desafio == null) throw new IllegalStateException("o Let's Encrypt nao ofereceu o desafio http-01");
                String token = str(desafio, "token");
                DESAFIOS.put(token, token + "." + thumb);
                tokens.add(token);
                post(str(desafio, "url"), "{}", false);
                log.info("desafio http-01 enviado, esperando o Let's Encrypt abrir o site...");
                esperar(urlAut, "autorizacao", 90);
            }

            // chave do dominio + CSR, finaliza
            File fChave = arquivoChave();
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair chave = g.generateKeyPair();
            byte[] csr = csr(dominio, chave);
            pedido = json(post(str(pedido, "finalize"), "{\"csr\":\"" + b64(csr) + "\"}", false).body());
            if (!"valid".equals(str(pedido, "status"))) pedido = esperar(urlPedido, "pedido", 90);
            String urlCert = str(pedido, "certificate");
            if (urlCert == null) throw new IllegalStateException("pedido valido sem certificado: " + pedido);
            String pem = post(urlCert, null, false).body();
            List<X509Certificate> cadeia = lerCadeia(pem);
            if (cadeia.isEmpty()) throw new IllegalStateException("certificado vazio");
            Files.writeString(fChave.toPath(), pem("PRIVATE KEY", chave.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
            Files.writeString(arquivoCertificado().toPath(), pem, StandardCharsets.US_ASCII);
            log.info("certificado emitido: valido ate " + cadeia.get(0).getNotAfter());
        } finally {
            for (String t : tokens) DESAFIOS.remove(t);
        }
    }

    /** Espera o objeto (autorizacao ou pedido) ficar "valid". Erro se ficar "invalid". */
    private JsonObject esperar(String url, String oque, int segundos) throws Exception {
        long fim = System.currentTimeMillis() + segundos * 1000L;
        while (true) {
            JsonObject o = json(post(url, null, false).body());
            String st = str(o, "status");
            if ("valid".equals(st)) return o;
            if ("invalid".equals(st)) throw new IllegalStateException(oque + " recusada: " + motivo(o));
            if (System.currentTimeMillis() > fim) throw new IllegalStateException(oque + " demorou demais (status " + st + ")");
            Thread.sleep(2500);
        }
    }

    private static String motivo(JsonObject o) {
        if (o.has("error")) return o.get("error").toString();
        if (o.has("challenges"))
            for (JsonElement c : o.getAsJsonArray("challenges"))
                if (c.getAsJsonObject().has("error")) return c.getAsJsonObject().get("error").toString();
        return o.toString();
    }

    // ------------------------------------------------------------------ HTTP + JWS

    private HttpResponse<String> get(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "CoronaLoja-ACME/1.0").GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() >= 400) throw new IllegalStateException("GET " + url + " -> " + r.statusCode() + " " + r.body());
        return r;
    }

    private void novoNonce() throws Exception {
        HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(dir.get("newNonce").getAsString())).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "CoronaLoja-ACME/1.0").method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        nonce = r.headers().firstValue("Replay-Nonce").orElseThrow(() -> new IllegalStateException("sem Replay-Nonce"));
    }

    /** payload null = POST-as-GET (corpo vazio). jwk = manda a chave publica (so na criacao da conta). */
    private HttpResponse<String> post(String url, String payload, boolean jwk) throws Exception {
        for (int tentativa = 0; ; tentativa++) {
            if (nonce == null) novoNonce();
            String prot = "{\"alg\":\"ES256\"," + (jwk ? "\"jwk\":" + jwk((ECPublicKey) conta.getPublic()) : "\"kid\":\"" + kid + "\"")
                    + ",\"nonce\":\"" + nonce + "\",\"url\":\"" + url + "\"}";
            String p64 = b64(prot.getBytes(StandardCharsets.UTF_8));
            String c64 = payload == null ? "" : b64(payload.getBytes(StandardCharsets.UTF_8));
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(conta.getPrivate());
            s.update((p64 + "." + c64).getBytes(StandardCharsets.US_ASCII));
            String corpo = "{\"protected\":\"" + p64 + "\",\"payload\":\"" + c64 + "\",\"signature\":\"" + b64(s.sign()) + "\"}";
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "CoronaLoja-ACME/1.0").header("Content-Type", "application/jose+json")
                    .header("Accept", "application/pem-certificate-chain, application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo)).build(), HttpResponse.BodyHandlers.ofString());
            nonce = r.headers().firstValue("Replay-Nonce").orElse(null);
            if (r.statusCode() < 400) return r;
            if (tentativa < 2 && r.body() != null && r.body().contains("badNonce")) continue;
            throw new IllegalStateException("POST " + url + " -> " + r.statusCode() + " " + r.body());
        }
    }

    private static String jwk(ECPublicKey k) {
        return "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + b64(coord(k.getW().getAffineX())) + "\",\"y\":\"" + b64(coord(k.getW().getAffineY())) + "\"}";
    }

    /** RFC 7638: SHA-256 do JWK com os campos em ordem (jwk() ja escreve assim). */
    static String thumbprint(ECPublicKey k) throws Exception {
        return b64(MessageDigest.getInstance("SHA-256").digest(jwk(k).getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] coord(BigInteger v) {
        byte[] b = v.toByteArray();
        byte[] r = new byte[32];
        int n = Math.min(32, b.length);
        System.arraycopy(b, b.length - n, r, 32 - n, n);
        return r;
    }

    static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    // ------------------------------------------------------------------ chaves e certificados

    static KeyPair carregarOuCriarChave(File f) throws Exception {
        if (f.exists()) {
            PrivateKey priv = lerChave(f);
            // a publica sai da privada guardada junto (formato: linha 1 privada, linha 2 publica, em base64)
            String[] l = Files.readString(f.toPath(), StandardCharsets.US_ASCII).trim().split("\\s+");
            if (l.length >= 2 && !l[0].startsWith("-----")) {
                byte[] pub = Base64.getDecoder().decode(l[1]);
                return new KeyPair(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(pub)), priv);
            }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair k = g.generateKeyPair();
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), Base64.getEncoder().encodeToString(k.getPrivate().getEncoded()) + "\n"
                + Base64.getEncoder().encodeToString(k.getPublic().getEncoded()) + "\n", StandardCharsets.US_ASCII);
        return k;
    }

    /** Le uma chave privada EC: PEM "PRIVATE KEY" ou o formato da conta (base64 na primeira linha). */
    static PrivateKey lerChave(File f) throws Exception {
        String s = Files.readString(f.toPath(), StandardCharsets.US_ASCII).trim();
        byte[] der;
        if (s.startsWith("-----")) {
            der = Base64.getMimeDecoder().decode(s.replaceAll("-----[A-Z ]+-----", ""));
        } else {
            der = Base64.getDecoder().decode(s.split("\\s+")[0]);
        }
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    static List<X509Certificate> lerCadeia(String pem) throws Exception {
        Collection<? extends Certificate> cs = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        List<X509Certificate> l = new ArrayList<>();
        for (Certificate c : cs) l.add((X509Certificate) c);
        return l;
    }

    private static String pem(String tipo, byte[] der) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + tipo + "-----\n";
    }

    // ------------------------------------------------------------------ CSR (PKCS#10) em DER, na mao

    static byte[] csr(String dominio, KeyPair k) throws Exception {
        byte[] nome = seq(set(seq(oid("2.5.4.3"), tlv(0x0C, dominio.getBytes(StandardCharsets.UTF_8)))));
        byte[] san = seq(tlv(0x82, dominio.getBytes(StandardCharsets.US_ASCII)));
        byte[] ext = seq(seq(oid("2.5.29.17"), tlv(0x04, san)));
        byte[] atributos = tlv(0xA0, seq(oid("1.2.840.113549.1.9.14"), set(ext)));
        byte[] info = seq(new byte[] {0x02, 0x01, 0x00}, nome, k.getPublic().getEncoded(), atributos);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(k.getPrivate());
        s.update(info);
        byte[] assinatura = s.sign();
        byte[] bits = new byte[assinatura.length + 1];
        System.arraycopy(assinatura, 0, bits, 1, assinatura.length);
        return seq(info, seq(oid("1.2.840.10045.4.3.2")), tlv(0x03, bits));
    }

    private static byte[] seq(byte[]... partes) {
        return tlv(0x30, partes);
    }

    private static byte[] set(byte[]... partes) {
        return tlv(0x31, partes);
    }

    private static byte[] tlv(int tag, byte[]... partes) {
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        for (byte[] p : partes) corpo.writeBytes(p);
        byte[] c = corpo.toByteArray();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int n = c.length;
        if (n < 128) o.write(n);
        else if (n < 256) {
            o.write(0x81);
            o.write(n);
        } else {
            o.write(0x82);
            o.write(n >> 8);
            o.write(n & 0xFF);
        }
        o.writeBytes(c);
        return o.toByteArray();
    }

    private static byte[] oid(String s) {
        String[] p = s.split("\\.");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            byte[] tmp = new byte[10];
            int n = 0;
            do {
                tmp[n++] = (byte) (v & 0x7F);
                v >>= 7;
            } while (v > 0);
            for (int j = n - 1; j >= 0; j--) o.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(0x06, o.toByteArray());
    }
}
