package br.servidor.loja;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Pedidos da loja (guardados em pedidos.yml). Todos os metodos sao sincronizados: o site e o servidor usam juntos. */
final class Pedidos {

    static final String AGUARDANDO = "aguardando", PAGO = "pago", ENTREGUE = "entregue", EXPIRADO = "expirado", CANCELADO = "cancelado";

    static final class Pedido {
        String codigo, jogador, email, produto, nomeProduto, status, mpId, qr, ip;
        double valor;
        long criado, pago, entregue;
        List<String> comandos = new ArrayList<>();
        /** Imagem do QR (base64). So na memoria: depois de reiniciar o site mostra o copia e cola. */
        transient String qrBase64;
    }

    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom rnd = new SecureRandom();
    private final File arquivo;
    private final Map<String, Pedido> pedidos = new LinkedHashMap<>();

    Pedidos(File arquivo) {
        this.arquivo = arquivo;
        carregar();
    }

    private void carregar() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection s = y.getConfigurationSection("pedidos");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            Pedido p = new Pedido();
            p.codigo = k;
            p.jogador = c.getString("jogador", "");
            p.email = c.getString("email", "");
            p.produto = c.getString("produto", "");
            p.nomeProduto = c.getString("nome-produto", p.produto);
            p.status = c.getString("status", AGUARDANDO);
            p.mpId = c.getString("mp-id", null);
            p.qr = c.getString("qr", null);
            p.ip = c.getString("ip", "");
            p.valor = c.getDouble("valor");
            p.criado = c.getLong("criado");
            p.pago = c.getLong("pago");
            p.entregue = c.getLong("entregue");
            p.comandos = new ArrayList<>(c.getStringList("comandos"));
            pedidos.put(k, p);
        }
    }

    synchronized void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Pedido p : pedidos.values()) {
            String b = "pedidos." + p.codigo + ".";
            y.set(b + "jogador", p.jogador);
            y.set(b + "email", p.email);
            y.set(b + "produto", p.produto);
            y.set(b + "nome-produto", p.nomeProduto);
            y.set(b + "valor", p.valor);
            y.set(b + "status", p.status);
            y.set(b + "mp-id", p.mpId);
            y.set(b + "qr", p.qr);
            y.set(b + "ip", p.ip);
            y.set(b + "criado", p.criado);
            y.set(b + "pago", p.pago);
            y.set(b + "entregue", p.entregue);
            y.set(b + "comandos", p.comandos);
        }
        try {
            File tmp = new File(arquivo.getParentFile(), arquivo.getName() + ".tmp");
            y.save(tmp);
            if (arquivo.exists() && !arquivo.delete()) throw new IOException("nao consegui trocar " + arquivo);
            if (!tmp.renameTo(arquivo)) throw new IOException("nao consegui renomear " + tmp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized Pedido novo(String jogador, String email, String produto, String nomeProduto, double valor, List<String> comandos, String ip) {
        String c;
        do {
            StringBuilder sb = new StringBuilder("CS-");
            for (int i = 0; i < 6; i++) sb.append(LETRAS.charAt(rnd.nextInt(LETRAS.length())));
            c = sb.toString();
        } while (pedidos.containsKey(c));
        Pedido p = new Pedido();
        p.codigo = c;
        p.jogador = jogador;
        p.email = email;
        p.produto = produto;
        p.nomeProduto = nomeProduto;
        p.valor = valor;
        p.comandos = new ArrayList<>(comandos);
        p.status = AGUARDANDO;
        p.criado = System.currentTimeMillis();
        p.ip = ip;
        pedidos.put(c, p);
        salvar();
        return p;
    }

    synchronized Pedido pegar(String codigo) {
        if (codigo == null) return null;
        return pedidos.get(codigo.trim().toUpperCase(Locale.ROOT));
    }

    synchronized List<Pedido> comStatus(String status) {
        List<Pedido> l = new ArrayList<>();
        for (Pedido p : pedidos.values()) if (p.status.equals(status)) l.add(p);
        return l;
    }

    /** Historico do jogador (mais novos primeiro), sem os que expiraram sem pagar. */
    synchronized List<Pedido> doJogador(String jogador) {
        String j = Loja.normalizar(jogador);
        List<Pedido> l = new ArrayList<>();
        for (Pedido p : pedidos.values()) {
            if (Loja.normalizar(p.jogador).equals(j) && !p.status.equals(EXPIRADO)) l.add(p);
        }
        l.sort(Comparator.comparingLong((Pedido p) -> p.criado).reversed());
        return l;
    }

    synchronized List<Pedido> ultimos(int n) {
        List<Pedido> l = new ArrayList<>(pedidos.values());
        l.sort(Comparator.comparingLong((Pedido p) -> p.criado).reversed());
        return l.size() > n ? l.subList(0, n) : l;
    }

    /** Muda o status so se ele ainda for o esperado (evita entregar duas vezes). */
    synchronized boolean trocar(Pedido p, String de, String para) {
        if (!p.status.equals(de)) return false;
        p.status = para;
        long agora = System.currentTimeMillis();
        if (para.equals(PAGO)) p.pago = agora;
        if (para.equals(ENTREGUE)) p.entregue = agora;
        salvar();
        return true;
    }

    synchronized double totalRecebido() {
        double t = 0;
        for (Pedido p : pedidos.values()) if (p.status.equals(PAGO) || p.status.equals(ENTREGUE)) t += p.valor;
        return t;
    }
}
