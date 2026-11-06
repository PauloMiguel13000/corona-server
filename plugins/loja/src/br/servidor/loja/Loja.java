package br.servidor.loja;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Produtos e funcoes pequenas usadas pelo site e pelo plugin. */
final class Loja {

    record Produto(String id, String nome, String categoria, double preco, String cor, String destaque, String descricao,
                   List<String> beneficios, List<String> comandos) {
    }

    static final Pattern NICK = Pattern.compile("^\\.?[A-Za-z0-9_ ]{3,20}$");
    static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private Loja() {
    }

    /** Mesmo jogador com ou sem o ponto do Bedrock, maiusculas ou espacos (o Floodgate troca espaco por _). */
    static String normalizar(String nick) {
        if (nick == null) return "";
        String n = nick.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        while (n.startsWith(".")) n = n.substring(1);
        return n;
    }

    static String limparNick(String nick) {
        return nick == null ? "" : nick.trim().replaceAll("\\s+", " ");
    }

    static String reais(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }

    static String json(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '<' -> b.append("\\u003c");
                case '>' -> b.append("\\u003e");
                case '&' -> b.append("\\u0026");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    static String jsonLista(List<String> l) {
        List<String> out = new ArrayList<>();
        for (String s : l) out.add(json(s));
        return "[" + String.join(",", out) + "]";
    }

    /** Tira os codigos de cor do Minecraft (§a etc.) de um texto. */
    static String semCor(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
