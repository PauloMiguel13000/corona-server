package br.servidor.loja;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Pix pelo Mercado Pago (API de pagamentos). O token fica so no config do servidor. */
final class MercadoPago {

    record Pix(String id, String status, String qr, String qrBase64) {
    }

    record Consulta(String status, double valor, String referencia) {
    }

    private static final String API = "https://api.mercadopago.com/v1/payments";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String token;

    MercadoPago(String token) {
        this.token = token == null ? "" : token.trim();
    }

    boolean ativo() {
        return !token.isEmpty();
    }

    Pix criar(Pedidos.Pedido p, String descricao, int minutos) throws IOException, InterruptedException {
        JsonObject corpo = new JsonObject();
        corpo.addProperty("transaction_amount", Math.round(p.valor * 100) / 100.0);
        corpo.addProperty("description", descricao);
        corpo.addProperty("payment_method_id", "pix");
        corpo.addProperty("external_reference", p.codigo);
        corpo.addProperty("date_of_expiration", OffsetDateTime.now(ZoneOffset.ofHours(-3)).plusMinutes(minutos)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)));
        JsonObject payer = new JsonObject();
        payer.addProperty("email", p.email);
        corpo.add("payer", payer);
        HttpRequest req = HttpRequest.newBuilder(URI.create(API))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", p.codigo)
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject j = objeto(r.body());
        if (r.statusCode() / 100 != 2 || j == null || !j.has("id")) {
            throw new IOException("Mercado Pago respondeu " + r.statusCode() + ": " + resumo(r.body()));
        }
        String qr = null, img = null;
        JsonObject poi = j.has("point_of_interaction") && j.get("point_of_interaction").isJsonObject() ? j.getAsJsonObject("point_of_interaction") : null;
        if (poi != null && poi.has("transaction_data") && poi.get("transaction_data").isJsonObject()) {
            JsonObject td = poi.getAsJsonObject("transaction_data");
            qr = texto(td, "qr_code");
            img = texto(td, "qr_code_base64");
        }
        if (qr == null) throw new IOException("Mercado Pago nao devolveu o codigo Pix: " + resumo(r.body()));
        return new Pix(j.get("id").getAsString(), texto(j, "status"), qr, img);
    }

    Consulta consultar(String id) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/" + id))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject j = objeto(r.body());
        if (r.statusCode() / 100 != 2 || j == null) throw new IOException("Mercado Pago respondeu " + r.statusCode() + ": " + resumo(r.body()));
        double valor = j.has("transaction_amount") && !j.get("transaction_amount").isJsonNull() ? j.get("transaction_amount").getAsDouble() : 0;
        return new Consulta(texto(j, "status"), valor, texto(j, "external_reference"));
    }

    private static JsonObject objeto(String s) {
        try {
            JsonElement e = JsonParser.parseString(s);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String texto(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static String resumo(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ");
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
