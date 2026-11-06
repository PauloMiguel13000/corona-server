package br.servidor.coronapack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Link publico do pacote do Java, sem conta: sobe o mesmo zip do jar num servico gratis (tenta varios) e confere
 * baixando de volta. Guarda o link em link-publico.txt e confere de tempos em tempos: se sumiu, sobe de novo.
 */
final class LinkPublico {
    private record Servico(String nome, String url, String campo, String[][] extras) {}

    private static final List<Servico> SERVICOS = List.of(
            new Servico("catbox", "https://catbox.moe/user/api.php", "fileToUpload", new String[][] {{"reqtype", "fileupload"}}),
            new Servico("x0.at", "https://x0.at/", "file", new String[0][]),
            new Servico("envs.sh", "https://envs.sh/", "file", new String[0][]),
            new Servico("0x0.st", "https://0x0.st/", "file", new String[0][]),
            new Servico("pomf2", "https://pomf2.lain.la/upload.php", "files[]", new String[0][]),
            new Servico("litterbox", "https://litterbox.catbox.moe/resources/internals/api.php", "fileToUpload",
                    new String[][] {{"reqtype", "fileupload"}, {"time", "72h"}}));
    private static final Pattern LINK = Pattern.compile("https?:[\\\\/A-Za-z0-9._~%-]+");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final byte[] zip, sha1;
    private final File arquivo;
    private final Logger log;
    private final String nomeArquivo, rotulo;
    /** Bedrock: o celular so aceita link https que responda "application/zip". */
    boolean exigirZip;
    volatile String link;

    LinkPublico(byte[] zip, byte[] sha1, File pasta, Logger log) {
        this(zip, sha1, pasta, log, "link-publico.txt", "corona-pack.zip", "do Java");
    }

    /** nomeLink: arquivo onde guarda o link; nomeArquivo: nome do arquivo enviado (o link termina com a mesma extensao). */
    LinkPublico(byte[] zip, byte[] sha1, File pasta, Logger log, String nomeLink, String nomeArquivo, String rotulo) {
        this.zip = zip;
        this.sha1 = sha1;
        this.arquivo = new File(pasta, nomeLink);
        this.log = log;
        this.nomeArquivo = nomeArquivo;
        this.rotulo = rotulo;
        try {
            if (arquivo.isFile()) {
                String[] l = Files.readString(arquivo.toPath(), StandardCharsets.UTF_8).trim().split("\\s+");
                // a segunda coluna e o sha1: link de outro pacote nao vale
                if (l.length >= 2 && l[1].equalsIgnoreCase(java.util.HexFormat.of().formatHex(sha1))) link = l[0];
            }
        } catch (Exception ignored) {
        }
    }

    /** Confere o link atual e, se nao estiver no ar, sobe de novo. Roda fora da thread do servidor. */
    void garantir() {
        String atual = link;
        if (atual != null && confere(atual)) return;
        if (atual != null) log.info("Link do pacote saiu do ar: " + atual);
        link = null;
        for (Servico s : SERVICOS) {
            try {
                String u = subir(s);
                if (u == null) continue;
                if (!confere(u)) {
                    log.info("Pacote: " + s.nome() + " devolveu " + u + " mas o arquivo nao confere");
                    continue;
                }
                link = u;
                Files.writeString(arquivo.toPath(), u + " " + java.util.HexFormat.of().formatHex(sha1) + " " + s.nome() + "\n", StandardCharsets.UTF_8);
                log.info("Pacote " + rotulo + " no ar (" + s.nome() + "): " + u);
                return;
            } catch (Exception e) {
                log.info("Pacote: " + s.nome() + " falhou: " + e.getMessage());
            }
        }
        log.warning("Nao consegui deixar o pacote " + rotulo + " num link publico agora (tento de novo mais tarde).");
    }

    private String subir(Servico s) throws Exception {
        String fronteira = "----corona" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream b = new ByteArrayOutputStream(zip.length + 1024);
        for (String[] e : s.extras()) {
            b.write(("--" + fronteira + "\r\nContent-Disposition: form-data; name=\"" + e[0] + "\"\r\n\r\n" + e[1] + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        b.write(("--" + fronteira + "\r\nContent-Disposition: form-data; name=\"" + s.campo() + "\"; filename=\"" + nomeArquivo + "\"\r\n"
                + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        b.write(zip);
        b.write(("\r\n--" + fronteira + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest r = HttpRequest.newBuilder(URI.create(s.url())).timeout(Duration.ofSeconds(180))
                .header("Content-Type", "multipart/form-data; boundary=" + fronteira).header("User-Agent", "CoronaServer/1.0 (servidor de Minecraft)")
                .POST(HttpRequest.BodyPublishers.ofByteArray(b.toByteArray())).build();
        HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
        String corpo = resp.body() == null ? "" : resp.body().trim();
        if (resp.statusCode() / 100 != 2) {
            log.info("Pacote: " + s.nome() + " respondeu " + resp.statusCode() + ": " + corpo.substring(0, Math.min(140, corpo.length())));
            return null;
        }
        Matcher m = LINK.matcher(corpo);
        while (m.find()) {
            String u = m.group().replace("\\/", "/");
            if (u.contains(".zip") || !u.endsWith("/")) return u.replace("http://", "https://");
        }
        log.info("Pacote: " + s.nome() + " nao devolveu link: " + corpo.substring(0, Math.min(140, corpo.length())));
        return null;
    }

    /** Baixa o link e compara com o zip do jar. */
    boolean confere(String u) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(120)).header("User-Agent", "CoronaServer/1.0").GET().build();
            HttpResponse<byte[]> resp = http.send(r, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200 || !Arrays.equals(MessageDigest.getInstance("SHA-1").digest(resp.body()), sha1)) return false;
            if (exigirZip) {
                String tipo = resp.headers().firstValue("content-type").orElse("").toLowerCase(java.util.Locale.ROOT);
                if (!u.startsWith("https://") || !tipo.startsWith("application/zip")) {
                    log.info("Pacote " + rotulo + ": " + u + " responde '" + tipo + "' (o Bedrock exige https e application/zip)");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
