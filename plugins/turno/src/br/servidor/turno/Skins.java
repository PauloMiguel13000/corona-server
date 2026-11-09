package br.servidor.turno;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Skins dos animatronicos (mesmo esquema das skins dos NPCs das Masmorras): os NPCs deixam de ser aldeoes e viram bonecos (Mannequin) com skin propria.
 * As skins (PNG 64x64 desenhadas por codigo, res/skins/) sao assinadas pelo MineSkin (api.mineskin.org, aprovado pelo
 * Paulo) UMA vez e guardadas em plugins/Masmorras/skins.yml; o Java e o Bedrock (Geyser) veem a skin pela assinatura,
 * sem pacote de recursos. Enquanto a skin nao esta pronta, o NPC continua aldeao; quando fica, e trocado no lugar
 * (mesma posicao, mesmo nome, mesmos dados). Os bonecos olham para o jogador mais perto.
 */
final class Skins {

    static final String[] IDS = {"capi", "tuca", "jaca", "boto"};

    private final Turno pl;
    private final File arquivo;
    private final Map<String, String[]> prontas = new ConcurrentHashMap<>(); // id -> {value, signature, url}
    private final Map<String, String> erros = new ConcurrentHashMap<>();
    private final AtomicBoolean gerando = new AtomicBoolean(false);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    Skins(Turno pl) {
        this.pl = pl;
        arquivo = new File(pl.getDataFolder(), "skins.yml");
        carregar();
        if (!ligado()) return;
        Bukkit.getScheduler().runTaskLater(pl, this::gerarFaltando, 60L);
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("skins.ligado", true);
    }

    private void carregar() {
        if (!arquivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        for (String id : y.getKeys(false)) {
            String v = y.getString(id + ".value"), s = y.getString(id + ".signature");
            if (v != null && s != null) prontas.put(id, new String[]{v, s, y.getString(id + ".url", "")});
        }
        if (!prontas.isEmpty()) pl.getLogger().info("Skins dos NPCs: " + prontas.size() + " prontas (skins.yml).");
    }

    private synchronized void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, String[]> e : prontas.entrySet()) {
            y.set(e.getKey() + ".value", e.getValue()[0]);
            y.set(e.getKey() + ".signature", e.getValue()[1]);
            y.set(e.getKey() + ".url", e.getValue()[2]);
        }
        try {
            y.save(arquivo);
        } catch (IOException ex) {
            pl.getLogger().warning("Nao consegui salvar skins.yml: " + ex);
        }
    }

    boolean pronta(String id) {
        return id != null && prontas.containsKey(id);
    }

    /** Perfil com a textura assinada (null se a skin ainda nao esta pronta). */
    ResolvableProfile perfil(String id) {
        String[] tex = prontas.get(id);
        if (tex == null) return null;
        return ResolvableProfile.resolvableProfile()
                .name(("tn_" + id).length() > 16 ? ("tn_" + id).substring(0, 16) : "tn_" + id)
                .uuid(UUID.nameUUIDFromBytes(("corona-turno-" + id).getBytes(StandardCharsets.UTF_8)))
                .addProperty(new ProfileProperty("textures", tex[0], tex[1]))
                .build();
    }

    // ------------------------------------------------------------------ MineSkin

    String resumo() {
        StringBuilder sb = new StringBuilder("§eSkins dos NPCs: ");
        for (String id : IDS) {
            sb.append(pronta(id) ? "§a" : erros.containsKey(id) ? "§c" : "§7").append(id).append(' ');
        }
        if (gerando.get()) sb.append("§8(gerando no MineSkin...)");
        return sb.toString();
    }

    /** Sobe para o MineSkin as skins que ainda nao tem assinatura (uma por vez, em outra thread). */
    void gerarFaltando() {
        if (!ligado()) return;
        List<String> faltam = new ArrayList<>();
        for (String id : IDS) if (!pronta(id)) faltam.add(id);
        if (faltam.isEmpty() || !gerando.compareAndSet(false, true)) return;
        pl.getLogger().info("Skins dos NPCs: gerando " + faltam.size() + " no MineSkin (" + String.join(", ", faltam) + ")...");
        CompletableFuture.runAsync(() -> {
            try {
                for (String id : faltam) {
                    for (int tent = 1; tent <= 4; tent++) {
                        try {
                            String[] tex = gerar(id);
                            if (tex != null) {
                                prontas.put(id, tex);
                                erros.remove(id);
                                salvar();
                                pl.getLogger().info("Skin pronta: " + id);
                                Bukkit.getScheduler().runTask(pl, () -> pl.skinPronta(id));
                                break;
                            }
                            erros.put(id, "resposta sem textura");
                        } catch (RateLimit r) {
                            pl.getLogger().info("MineSkin pediu para esperar " + r.segundos + "s (" + id + ").");
                            Thread.sleep(Math.min(120, Math.max(5, r.segundos)) * 1000L);
                            tent--; // esperar nao conta como tentativa
                            if (r.vezes++ > 6) break;
                        } catch (Exception ex) {
                            erros.put(id, ex.toString());
                            pl.getLogger().warning("MineSkin (" + id + ", tentativa " + tent + "): " + ex);
                            Thread.sleep(8000L);
                        }
                    }
                    Thread.sleep(Math.max(1, pl.getConfig().getInt("skins.intervalo-segundos", 6)) * 1000L);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                gerando.set(false);
            }
        });
    }

    private static final class RateLimit extends Exception {
        final long segundos;
        int vezes;

        RateLimit(long s) {
            super("rate limit " + s + "s");
            segundos = s;
        }
    }

    private String agente() {
        return pl.getConfig().getString("skins.user-agent", "CoronaServer-Masmorras/1.0 (Paper plugin; NPC skins)");
    }

    private HttpRequest.Builder pedido(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .header("User-Agent", agente())
                .header("Accept", "application/json");
        String chave = pl.getConfig().getString("skins.api-key", "");
        if (chave != null && !chave.isBlank()) b.header("Authorization", "Bearer " + chave.trim());
        return b;
    }

    /** Sobe o PNG, espera o job e devolve {value, signature, url}; null se nao achou textura. */
    private String[] gerar(String id) throws Exception {
        byte[] png;
        try (InputStream in = pl.getResource("skins/" + id + ".png")) {
            if (in == null) throw new IOException("skins/" + id + ".png nao esta no jar");
            png = in.readAllBytes();
        }
        String limite = "----CoronaSkin" + System.nanoTime();
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        campo(corpo, limite, "name", "corona_" + id);
        campo(corpo, limite, "variant", "classic");
        campo(corpo, limite, "visibility", "unlisted");
        corpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + id + ".png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        corpo.write(png);
        corpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        String base = pl.getConfig().getString("skins.api", "https://api.mineskin.org");
        HttpRequest req = pedido(base + "/v2/queue")
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() == 429) throw new RateLimit(esperaDe(r));
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + ": " + resumoCorpo(r.body()));
        JsonObject json = JsonParser.parseString(r.body()).getAsJsonObject();
        String[] tex = textura(json);
        if (tex != null) return tex;
        String job = achar(json, "job", "id");
        if (job == null) job = achar(json, "id");
        if (job == null) throw new IOException("resposta sem job: " + resumoCorpo(r.body()));
        // espera o job (ate ~90 s), 1 consulta a cada 2 s
        for (int i = 0; i < 45; i++) {
            Thread.sleep(2000L);
            HttpResponse<String> s = http.send(pedido(base + "/v2/queue/" + job).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (s.statusCode() == 429) {
                Thread.sleep(3000L);
                continue;
            }
            if (s.statusCode() / 100 != 2) throw new IOException("HTTP " + s.statusCode() + " ao consultar o job: " + resumoCorpo(s.body()));
            JsonObject js = JsonParser.parseString(s.body()).getAsJsonObject();
            tex = textura(js);
            if (tex != null) return tex;
            String status = achar(js, "job", "status");
            if (status == null) status = achar(js, "status");
            if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) throw new IOException("job falhou: " + resumoCorpo(s.body()));
            if ("completed".equalsIgnoreCase(status)) {
                String uuid = achar(js, "job", "result");
                if (uuid == null) uuid = achar(js, "result");
                if (uuid != null) {
                    HttpResponse<String> k = http.send(pedido(base + "/v2/skins/" + uuid).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (k.statusCode() / 100 == 2) {
                        tex = textura(JsonParser.parseString(k.body()).getAsJsonObject());
                        if (tex != null) return tex;
                    }
                }
                throw new IOException("job completo sem textura: " + resumoCorpo(s.body()));
            }
        }
        throw new IOException("job " + job + " nao terminou em 90 s");
    }

    private static void campo(ByteArrayOutputStream o, String limite, String nome, String valor) throws IOException {
        o.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + nome + "\"\r\n\r\n" + valor + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static long esperaDe(HttpResponse<String> r) {
        try {
            String h = r.headers().firstValue("Retry-After").orElse(null);
            if (h != null) return Long.parseLong(h.trim());
            JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
            String s = achar(j, "rateLimit", "next", "relative");
            if (s == null) s = achar(j, "delay");
            if (s == null) s = achar(j, "nextRequest");
            if (s != null) {
                double v = Double.parseDouble(s);
                return v > 1000 ? Math.round(v / 1000) : Math.round(v);
            }
        } catch (RuntimeException ignored) {
        }
        return 30;
    }

    private static String resumoCorpo(String b) {
        if (b == null) return "";
        b = b.replaceAll("\\s+", " ");
        return b.length() > 300 ? b.substring(0, 300) + "..." : b;
    }

    /** Procura em qualquer lugar do JSON um objeto com "value" e "signature" (texture.data) e, perto dele, a "url". */
    private static String[] textura(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("value") && o.has("signature") && o.get("value").isJsonPrimitive() && o.get("signature").isJsonPrimitive()) {
                String v = o.get("value").getAsString(), s = o.get("signature").getAsString();
                if (v.length() > 50 && s.length() > 50) return new String[]{v, s, ""};
            }
            String url = o.has("url") && o.get("url").isJsonPrimitive() ? o.get("url").getAsString() : null;
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                String[] t = textura(en.getValue());
                if (t != null) {
                    if (t[2].isEmpty() && url != null) t[2] = url;
                    return t;
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                String[] t = textura(x);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static String achar(JsonObject o, String... caminho) {
        JsonElement e = o;
        for (String c : caminho) {
            if (e == null || !e.isJsonObject() || !e.getAsJsonObject().has(c)) return null;
            e = e.getAsJsonObject().get(c);
        }
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
