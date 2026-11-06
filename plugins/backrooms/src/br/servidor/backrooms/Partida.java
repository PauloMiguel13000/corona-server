package br.servidor.backrooms;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Uma partida nas Backrooms. O ciclo roda a cada 5 ticks (4 vezes por segundo). */
final class Partida {

    enum Estado { ESPERANDO, CONTAGEM, JOGANDO, FIM }

    static final class Jogador {
        final UUID id;
        final String nome;
        boolean jogando, fora;
        int vidas;
        double sanidade = 100;
        long graca, proximoEfeito, proximoBatimento;
        /** Passos atras do jogador (Nivel 0): quantos faltam, quando e a que distancia. */
        long proximosPassos, proximoPasso;
        int passos;
        double passoDist;
        Location ultimaPos;
        /** Nivel 6: pilha da lanterna (0 a 100) e ate quando ela falha. */
        double bateria = 100;
        long falhaAte;
        /** Sustos (jumpscares) e a musica de perseguicao. */
        long proximoSusto;
        boolean cacado;
        int batida;
        /** Eliminado com Moeda de Reviver: ate quando pode voltar (a derrota espera). */
        long reviverAte;

        Jogador(Player p) {
            id = p.getUniqueId();
            nome = p.getName();
        }
    }

    static final class Coletavel {
        final Location l;
        final boolean chave;
        Item ent;
        boolean pego;

        Coletavel(Location l, boolean chave) {
            this.l = l;
            this.chave = chave;
        }
    }

    /** Lugares de uma fase separados em quadrados de 16x16 para achar rapido o que esta perto. */
    static final class Indice {
        final List<int[]> todos;
        private final Map<Long, List<int[]>> b = new HashMap<>();

        Indice(List<int[]> l) {
            todos = l;
            for (int[] p : l) b.computeIfAbsent(k(p[0] >> 4, p[1] >> 4), x -> new ArrayList<>()).add(p);
        }

        private static long k(int a, int c) {
            return ((long) a << 32) ^ (c & 0xffffffffL);
        }

        List<int[]> perto(double x, double z, double min, double max) {
            List<int[]> r = new ArrayList<>();
            int raio = (int) Math.ceil(max / 16) + 1, bx = (int) Math.floor(x) >> 4, bz = (int) Math.floor(z) >> 4;
            for (int a = bx - raio; a <= bx + raio; a++)
                for (int c = bz - raio; c <= bz + raio; c++) {
                    List<int[]> l = b.get(k(a, c));
                    if (l == null) continue;
                    for (int[] p : l) {
                        double dx = p[0] + 0.5 - x, dz = p[1] + 0.5 - z, d = dx * dx + dz * dz;
                        if (d >= min * min && d <= max * max) r.add(p);
                    }
                }
            return r;
        }
    }

    /** Indice da fase da corrida (Nivel !), sempre a ultima. */
    static final int CORRIDA = 7;
    /** Nivel 6 (Luzes Apagadas): escuro, com lanterna. */
    static final int ESCURO = 6;
    private static final String[] ITEM_NOME = {"§eChave Amarela", "§bFusível", "§6Válvula", "§cDisjuntor", "§dPresente", "§bBoia", "§7Pilha", ""};
    private static final String[] ITEM_PLURAL = {"chaves", "fusíveis", "válvulas", "disjuntores", "presentes", "boias", "pilhas", ""};
    private static final String[] ITEM_HUD = {"§eChaves", "§bFusíveis", "§6Válvulas", "§cDisjuntores", "§dPresentes", "§bBoias", "§7Pilhas", ""};
    private static final Material[] ITEM_MAT = {Material.TRIAL_KEY, Material.LIGHTNING_ROD, Material.IRON_TRAPDOOR, Material.LEVER, Material.CAKE, Material.HEART_OF_THE_SEA, Material.COPPER_INGOT, Material.AIR};
    private static final String[] SAIDA_NOME = {"§a§lSAÍDA", "§a§lELEVADOR", "§a§lESCOTILHA", "§a§lPORTÃO", "§a§lPORTA DA FESTA", "§a§lESCADA", "§a§lPORTA DE EMERGÊNCIA", "§a§lSAÍDA"};

    private final Backrooms pl;
    final World mundo;
    final Mapa mapa;
    final Map<UUID, Jogador> jogadores = new LinkedHashMap<>();
    private final Random rnd = new Random();
    private final Map<Integer, Indice> lugares = new HashMap<>(), escuros = new HashMap<>();

    Estado estado = Estado.ESPERANDO;
    String modo;
    private long tick, inicioTick, trocaEm = -1, fimEm = -1, proximaBussola;
    private long proximaInvestida, investidaComeca = -1, investidaAte = -1, proximoEscuro;
    /** Apagao do Nivel 0: quando vem o proximo, quando as luzes voltam e onde foi. */
    private long proximoApagao = -1, fimApagao = -1;
    private Location ondeApagou;
    /** Escuridao rapida no apagao (trocavel nos testes, que rodam sem servidor). */
    java.util.function.Consumer<Player> escurecer = p -> p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
    private int contagem, proximo;
    int nivel;
    int pegos, necessarios;
    boolean saidaAberta;
    final List<Coletavel> coletaveis = new ArrayList<>();
    final List<Cacador> cacadores = new ArrayList<>();
    final List<Sorridente> sorridentes = new ArrayList<>();
    private final Map<Long, int[]> apagadas = new HashMap<>();
    private final List<TextDisplay> placas = new ArrayList<>();
    // nivel ! (a corrida)
    private Mannequin coisaCorrida;
    /** O Festeiro (Niveis 1 a 3): aparece parado ao longe e some quando chegam perto. */
    private Mannequin festeiro;
    private long festeiroAte, proximoFesteiro;
    private double corridaX;
    private long corridaComeca;
    private boolean corridaLigada;
    /** Ordem das fases desta partida: o Nivel 0 primeiro, as do meio embaralhadas e a corrida no fim. */
    final List<Integer> ordem = new ArrayList<>();
    int etapa;
    /** Lanterna do Nivel 6. */
    private final Lanterna lanterna = new Lanterna();
    private long ticks;
    /** Sustos que so uma pessoa ve (aparicoes, vultos, olhos no escuro). */
    private static final class Susto {
        final UUID quem;
        final int tipo;
        final Mannequin m;
        final Location l;
        long ate;
        Vector passo;

        Susto(UUID quem, int tipo, Mannequin m, Location l, long ate) {
            this.quem = quem;
            this.tipo = tipo;
            this.m = m;
            this.l = l;
            this.ate = ate;
        }
    }
    private final List<Susto> sustos = new ArrayList<>();

    Partida(Backrooms pl, World mundo, Mapa mapa) {
        this.pl = pl;
        this.mundo = mundo;
        this.mapa = mapa;
        this.modo = modoValido(pl.getConfig().getString("modo-padrao", "normal"));
        for (Nivel n : mapa.niveis) {
            lugares.put(n.id, new Indice(n.lugares));
            escuros.put(n.id, new Indice(n.escuros));
        }
        montarOrdem();
    }

    /** Sorteia a ordem das fases (config embaralhar-niveis: false = sempre na ordem 0, 1, 2...). */
    void montarOrdem() {
        ordem.clear();
        List<Integer> meio = new ArrayList<>();
        for (int i = 1; i < CORRIDA; i++) meio.add(i);
        if (pl.getConfig().getBoolean("embaralhar-niveis", true)) Collections.shuffle(meio, rnd);
        ordem.add(0);
        ordem.addAll(meio);
        ordem.add(CORRIDA);
        etapa = 0;
    }

    /** "fase 3/8" */
    String fase() {
        return "fase " + (etapa + 1) + "/" + ordem.size();
    }

    static long seg(double s) {
        return Math.round(s * 4);
    }

    // ================================================================== dificuldade

    static String modoValido(String m) {
        if (m == null) return "normal";
        m = m.toLowerCase(Locale.ROOT).replace("í", "i").replace("á", "a");
        if (m.startsWith("fac") || m.equals("easy")) return "facil";
        if (m.startsWith("dif") || m.equals("hard")) return "dificil";
        return "normal";
    }

    static String nomeModo(String m) {
        return switch (m) {
            case "facil" -> "§a§lFácil";
            case "dificil" -> "§c§lDifícil";
            default -> "§e§lNormal";
        };
    }

    String nomeModo() {
        return nomeModo(modo);
    }

    /** Valor da dificuldade atual (modos.<modo>.<chave>), olhando tambem a config de dentro do jar. */
    double dif(String chave, double padrao) {
        Object v = pl.getConfig().get("modos." + modo + "." + chave);
        return v instanceof Number n ? n.doubleValue() : padrao;
    }

    String difTexto(String chave, String padrao) {
        Object v = pl.getConfig().get("modos." + modo + "." + chave);
        return v == null ? padrao : v.toString();
    }

    String trocarModo(Player quem, String novo) {
        novo = modoValido(novo);
        if (estado == Estado.JOGANDO || estado == Estado.FIM) return "§cA partida já começou. Escolha a dificuldade antes da próxima.";
        if (quem != null && !participa(quem) && !naFila().isEmpty()) return "§cEntre na fila (§f/backrooms entrar§c) para escolher a dificuldade.";
        if (novo.equals(modo)) return null;
        modo = novo;
        avisar("§f" + (quem == null ? "Um admin" : quem.getName()) + " §7mudou a dificuldade para " + nomeModo() + "§7.");
        return null;
    }

    int vidasIniciais() {
        return (int) Math.max(1, dif("vidas", 3));
    }

    // ================================================================== jogadores

    Jogador adicionar(Player p) {
        Jogador j = new Jogador(p);
        jogadores.put(p.getUniqueId(), j);
        return j;
    }

    boolean remover(Player p) {
        Jogador j = jogadores.remove(p.getUniqueId());
        if (j == null) return false;
        if (estado == Estado.JOGANDO && j.jogando && !j.fora) avisar("§7" + j.nome + " desistiu e saiu das Backrooms.");
        pl.devolver(p);
        return true;
    }

    boolean participa(Player p) {
        return jogadores.containsKey(p.getUniqueId());
    }

    boolean vivo(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        return j != null && j.jogando && !j.fora && estado == Estado.JOGANDO;
    }

    /** Pode ser pego agora? */
    boolean alvoValido(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        return j != null && j.jogando && !j.fora && estado == Estado.JOGANDO && trocaEm < 0 && tick >= j.graca
                && p.isOnline() && p.getWorld().equals(mundo) && p.getGameMode() != GameMode.SPECTATOR;
    }

    List<Player> online(boolean soQuemJoga) {
        List<Player> l = new ArrayList<>();
        for (Jogador j : jogadores.values()) {
            if (soQuemJoga && !j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) l.add(p);
        }
        return l;
    }

    List<Player> naFila() {
        List<Player> l = new ArrayList<>();
        for (Jogador j : jogadores.values()) {
            if (j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) l.add(p);
        }
        return l;
    }

    List<Player> vivos() {
        List<Player> l = new ArrayList<>();
        for (Jogador j : jogadores.values()) {
            if (!j.jogando || j.fora) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline() && p.getWorld().equals(mundo)) l.add(p);
        }
        return l;
    }

    void avisar(String msg) {
        for (Player p : online(false)) p.sendMessage(Backrooms.PREFIXO + msg);
    }

    void titulo(Player p, String t, String s, double fica) {
        p.showTitle(Title.title(Backrooms.txt(t), Backrooms.txt(s),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis((long) (fica * 1000)), Duration.ofMillis(600))));
    }

    Nivel atual() {
        return mapa.niveis.get(nivel);
    }

    // ================================================================== ciclo

    void ciclo() {
        tick++;
        switch (estado) {
            case ESPERANDO -> {
                int n = naFila().size();
                if (n > 0 && n >= pl.getConfig().getInt("min-jogadores", 1)) {
                    estado = Estado.CONTAGEM;
                    contagem = (int) seg(pl.getConfig().getInt("contagem-segundos", 20));
                    avisar("§eA partida começa em §f" + (contagem / 4) + "s §e(modo " + nomeModo() + "§e). Chame os amigos: §f/backrooms entrar");
                }
                hudFila();
            }
            case CONTAGEM -> contar();
            case JOGANDO -> jogar();
            case FIM -> {
                if (fimEm > 0 && tick >= fimEm) finalizar();
            }
        }
    }

    private void contar() {
        List<Player> fila = naFila();
        if (fila.isEmpty() || fila.size() < pl.getConfig().getInt("min-jogadores", 1)) {
            estado = Estado.ESPERANDO;
            avisar("§7Contagem cancelada: faltam jogadores.");
            return;
        }
        if (fila.size() >= pl.getConfig().getInt("max-jogadores", 8)) contagem = (int) Math.min(contagem, seg(8));
        contagem--;
        if (contagem % 4 == 0) {
            int s = contagem / 4;
            for (Player p : fila) {
                p.sendActionBar(Backrooms.txt("§7Começa em §e" + s + "s §8| §7modo " + nomeModo() + " §8| §7jogadores: §f" + fila.size()));
                if (s <= 5 && s > 0) {
                    titulo(p, "§e" + s, "§7as luzes estão piscando...", 0.9);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f);
                }
            }
        }
        if (contagem <= 0) iniciar();
    }

    private void hudFila() {
        if (tick % 4 != 0) return;
        for (Player p : naFila()) {
            String s = estado == Estado.JOGANDO ? "§7Partida em andamento §8| §7você entra já já" : "§7Esperando jogadores... §8| §7modo " + nomeModo() + " §8| §7na fila: §f" + naFila().size();
            p.sendActionBar(Backrooms.txt(s));
        }
    }

    // ================================================================== inicio e fim

    void iniciar() {
        List<Player> fila = naFila();
        if (fila.isEmpty()) {
            estado = Estado.ESPERANDO;
            return;
        }
        estado = Estado.JOGANDO;
        inicioTick = tick;
        fimEm = -1;
        for (Player p : fila) {
            Jogador j = jogadores.get(p.getUniqueId());
            j.jogando = true;
            j.fora = false;
            j.vidas = vidasIniciais();
            j.sanidade = 100;
            j.bateria = 100;
            j.cacado = false;
            Guarda.limpar(p);
            p.setGameMode(GameMode.ADVENTURE);
            kit(p);
        }
        avisar("§eA partida começou com §f" + fila.size() + " §ejogador(es) no modo " + nomeModo() + "§e. Boa sorte...");
        montarOrdem();
        irParaNivel(ordem.get(0));
    }

    /** Quem entra com a partida rolando cai na fase atual. */
    void entrarNoMeio(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || estado != Estado.JOGANDO) return;
        j.jogando = true;
        j.fora = false;
        j.vidas = vidasIniciais();
        j.sanidade = 100;
        j.graca = tick + seg(8);
        Guarda.limpar(p);
        p.setGameMode(GameMode.ADVENTURE);
        kit(p);
        j.bateria = 100;
        if (nivel == ESCURO) darLanterna(p);
        pl.mover(p, nivel == CORRIDA ? voltaCorrida() : atual().inicio(mundo, rnd.nextInt(9)));
        titulo(p, "§e§l" + atual().nome, "§7" + atual().apelido + " §8(" + fase() + ")", 3);
        explicar(p);
        avisar("§f" + p.getName() + " §7caiu nas Backrooms.");
    }

    private void kit(Player p) {
        ItemStack b = new ItemStack(Material.COMPASS);
        ItemMeta m = b.getItemMeta();
        m.displayName(Backrooms.txt("§6Bússola"));
        String tipo = difTexto("bussola", "as-vezes");
        m.lore(List.of(Backrooms.txt(switch (tipo) {
            case "sempre" -> "§7Aponta para o item mais perto.";
            case "lenta", "nunca" -> "§7Aponta um item, mas só se mexe de " + (int) dif("bussola-segundos", 15) + " em " + (int) dif("bussola-segundos", 15) + " s.";
            default -> "§7Às vezes aponta para um item...";
        }), Backrooms.txt("§7Com a saída aberta, aponta para ela.")));
        m.getPersistentDataContainer().set(pl.kItem, PersistentDataType.STRING, "bussola");
        b.setItemMeta(m);
        p.getInventory().setItem(0, b);
        p.setLevel(vidasIniciais());
        p.setExp(0.99f);
    }

    ItemStack agua() {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.setColor(Color.fromRGB(0xF2E6C9));
        m.displayName(Backrooms.txt("§fÁgua de Amêndoa"));
        m.lore(List.of(Backrooms.txt("§7Beba (segure o botão direito)"), Backrooms.txt("§7para recuperar a §bsanidade§7.")));
        m.addItemFlags(ItemFlag.values());
        m.getPersistentDataContainer().set(pl.kItem, PersistentDataType.STRING, "agua");
        it.setItemMeta(m);
        return it;
    }

    boolean ehAgua(ItemStack it) {
        return it != null && it.hasItemMeta() && "agua".equals(it.getItemMeta().getPersistentDataContainer().get(pl.kItem, PersistentDataType.STRING));
    }

    private int aguas(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (ehAgua(it)) n += it.getAmount();
        return n;
    }

    void bebeu(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !vivo(p)) return;
        double antes = j.sanidade;
        j.sanidade = Math.min(100, j.sanidade + dif("agua-sanidade", 40));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.2f);
        p.sendActionBar(Backrooms.txt("§bSanidade §f" + (int) antes + "% §7→ §f" + (int) j.sanidade + "%"));
        p.removePotionEffect(PotionEffectType.NAUSEA);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        barulho(p.getLocation(), 5, p);
    }

    private void explicar(Player p) {
        int k = necessarios;
        switch (nivel) {
            case 0 -> {
                p.sendMessage(Backrooms.PREFIXO + "§fVocê saiu da realidade e caiu nas §eBackrooms§f. Ache §e" + k + " chaves amarelas §fpara destrancar a §aporta de saída§f.");
                p.sendMessage(Backrooms.PREFIXO + "§7Cuidado com a §fBactéria§7 (alta e preta). Se ela te ver, corra e vire esquinas para ela te perder.");
            }
            case 1 -> {
                p.sendMessage(Backrooms.PREFIXO + "§fAche §b" + k + " fusíveis §fpara ligar o §aelevador§f.");
                p.sendMessage(Backrooms.PREFIXO + "§7Nos cantos escuros moram os §fSorridentes§7. §cNão olhe para eles§7 e não chegue perto. Se um vier, vire uma esquina.");
            }
            case 2 -> {
                p.sendMessage(Backrooms.PREFIXO + "§fAche §6" + k + " válvulas §fpara abrir a §aescotilha§f.");
                p.sendMessage(Backrooms.PREFIXO + "§7O §fUivador §7é quase cego, mas §couve tudo§7: ande §fagachado§7 e não corra perto dele.");
            }
            case 3 -> {
                p.sendMessage(Backrooms.PREFIXO + "§fAche §c" + k + " disjuntores §fpara religar o §aportão§f da Estação Elétrica.");
                p.sendMessage(Backrooms.PREFIXO + "§7Os §fCães §7andam de quatro e enxergam no escuro. Se um vier, §cENCARE ELE§7 e §cnão corra§7:");
                p.sendMessage(Backrooms.PREFIXO + "§7ele para e rosna, e se você aguentar o olhar ele desiste. Virar as costas ou correr = ele ataca.");
            }
            case 4 -> {
                p.sendMessage(Backrooms.PREFIXO + "§fNível Fun §d=)§f. Ache §d" + k + " presentes §fpara abrir a §aporta da festa§f.");
                p.sendMessage(Backrooms.PREFIXO + "§7Os §eFesteiros §7querem brincar. §cNão deixe eles te verem§7: se um rir para você, CORRA e vire esquinas.");
            }
            case 5 -> {
                p.sendMessage(Backrooms.PREFIXO + "§fNível 37, as §bPiscinas§f. Ache §b" + k + " boias §fpara achar a §aescada§f.");
                p.sendMessage(Backrooms.PREFIXO + "§7Os §fAfogados §7não enxergam, mas §couvem a água§7: atravessar piscina faz barulho. Ande §fagachado§7.");
            }
            case ESCURO -> {
                p.sendMessage(Backrooms.PREFIXO + "§fNível 6, §8Luzes Apagadas§f. Ache §7" + k + " pilhas §fpara abrir a §aporta de emergência§f.");
                p.sendMessage(Backrooms.PREFIXO + "§7Sua §elanterna §7(mão esquerda) ilumina para onde você olha. Cada pilha achada recarrega ela.");
                p.sendMessage(Backrooms.PREFIXO + "§7As §8Sombras §7só se mexem quando §cninguém está olhando§7 para elas com luz. §fNão tire a luz de cima delas.");
            }
            default -> p.sendMessage(Backrooms.PREFIXO + "§c§lCORRA §fpelo corredor até a luz amarela no fim. Pule as muretas e desvie das paredes. §7Não olhe para trás.");
        }
        p.sendMessage(Backrooms.PREFIXO + "§7A §bsanidade §7(barra verde) cai com o tempo, mais rápido no escuro. Beba §fÁgua de Amêndoa§7. Vidas: §c" + vidasIniciais());
    }

    // ================================================================== fases

    void irParaNivel(int n) {
        limparNivel();
        nivel = n;
        int e = ordem.indexOf(n);
        if (e >= 0) etapa = e;
        trocaEm = -1;
        Nivel nv = atual();
        pegos = 0;
        saidaAberta = false;
        necessarios = n == CORRIDA ? 0 : (int) Math.max(1, dif("itens", 4));
        fecharPorta(nv);
        if (n < CORRIDA) {
            espalharItens(nv);
            int nCac = (int) (n == 0 ? dif("bacterias", 3) : n == 1 ? dif("n1-bacterias", 1) : n == 2 ? dif("coisas", 2) : n == 3 ? dif("caes", 3)
                    : n == 4 ? dif("festeiros", 3) : n == ESCURO ? dif("sombras", 3) : dif("afogados", 3));
            Cacador.Tipo tipoCac = n == 2 || n == 5 ? Cacador.Tipo.COISA : n == 3 ? Cacador.Tipo.CAO : n == 4 ? Cacador.Tipo.FESTEIRO
                    : n == ESCURO ? Cacador.Tipo.SOMBRA : Cacador.Tipo.BACTERIA;
            for (int i = 0; i < nCac; i++) {
                Cacador c = new Cacador(pl, this, mundo, nv, tipoCac);
                Location l = lugarDistante(nv, nv.inicioX, nv.inicioZ, 45 + i * 6);
                if (l != null) c.criar(l);
                cacadores.add(c);
            }
            if (n == 1) {
                int ns = (int) dif("sorridentes", 10);
                for (int i = 0; i < ns; i++) {
                    Sorridente s = new Sorridente(pl, this, mundo, nv);
                    s.comecar(tick, 6 + i * 2);
                    sorridentes.add(s);
                }
            }
        } else prepararCorrida(nv);
        int i = 0;
        for (Jogador j : jogadores.values()) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p == null || !p.isOnline()) continue;
            j.graca = tick + seg(n == CORRIDA ? 5 : 8);
            j.ultimaPos = null;
            j.cacado = false;
            j.proximoSusto = tick + seg(35 + rnd.nextInt(30));
            pl.mover(p, nv.inicio(mundo, i++));
            if (j.fora) continue;
            // na corrida ninguem sai antes da hora
            p.setWalkSpeed(n == CORRIDA ? 0f : 0.2f);
            if (etapa > 0) j.sanidade = Math.min(100, j.sanidade + 20);
            if (n == ESCURO) {
                j.bateria = 100;
                darLanterna(p);
            } else recolherLanterna(p);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
            titulo(p, (n == CORRIDA ? "§c§l" : "§e§l") + nv.nome, "§7" + nv.apelido + " §8(" + fase() + ")", 3.5);
            p.playSound(p.getLocation(), n == CORRIDA ? Sound.ENTITY_WARDEN_EMERGE : Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
            explicar(p);
        }
        proximaBussola = 0;
        bussolas(vivos(), true);
        pl.getLogger().info("Backrooms: " + nv.nome + " (" + fase() + ", " + modo + ", itens " + necessarios + ", criaturas " + cacadores.size() + "+" + sorridentes.size() + ")");
    }

    private void espalharItens(Nivel nv) {
        int extra = (int) dif("itens-extras", 3);
        int total = necessarios + extra;
        int s = nv instanceof Labirinto lb ? lb.S : 7;
        List<int[]> cand = Mapa.embaralhado(nv.lugares, rnd);
        List<int[]> chaves = new ArrayList<>();
        for (double espaco = 55; espaco >= 10 && chaves.size() < total; espaco -= 10) {
            for (int[] c : cand) {
                if (chaves.size() >= total) break;
                if (c[2] * s < 30 || chaves.contains(c)) continue;
                boolean ok = true;
                for (int[] o : chaves) if (Math.hypot(o[0] - c[0], o[1] - c[1]) < espaco) ok = false;
                if (ok) chaves.add(c);
            }
        }
        for (int[] c : chaves) coletaveis.add(new Coletavel(new Location(mundo, c[0] + 0.5, Nivel.Y + 0.25, c[1] + 0.5), true));
        int na = (int) dif("agua", 20);
        for (int[] c : cand) {
            if (na <= 0) break;
            if (chaves.contains(c) || c[2] < 2) continue;
            coletaveis.add(new Coletavel(new Location(mundo, c[0] + 0.5, Nivel.Y + 0.25, c[1] + 0.5), false));
            na--;
        }
    }

    private void fecharPorta(Nivel nv) {
        for (int i = 0; i < nv.porta.size(); i++) {
            int[] b = nv.porta.get(i);
            mundo.getBlockAt(b[0], b[1], b[2]).setBlockData(nv.portaFechada.get(i), false);
        }
    }

    private void abrirSaida() {
        if (saidaAberta) return;
        saidaAberta = true;
        Nivel nv = atual();
        for (int i = 0; i < nv.porta.size(); i++) {
            int[] b = nv.porta.get(i);
            mundo.getBlockAt(b[0], b[1], b[2]).setBlockData(nv.portaAberta.get(i), false);
        }
        Location s = nv.saida(mundo);
        mundo.playSound(s, Sound.BLOCK_IRON_DOOR_OPEN, 2f, 0.6f);
        Sons.local(mundo, s, 48, "saida", 1f, 1f, null, 0f, 0f);
        placas.add(mundo.spawn(s.clone().add(0, 2.6, 0), TextDisplay.class, t -> {
            t.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
            t.text(Backrooms.txt(SAIDA_NOME[nivel] + "\n§7(entre na porta)"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setPersistent(false);
        }));
        for (Player p : vivos()) {
            titulo(p, "§a§lA SAÍDA ABRIU!", "§7A bússola aponta para ela.", 2.5);
            Sons.para(p, p.getLocation(), "saida", 0.6f, 1f, Sound.BLOCK_BELL_RESONATE, 0.8f, 0.7f);
        }
        avisar("§a§lTodas as " + ITEM_PLURAL[nivel] + " foram achadas! §fA " + SAIDA_NOME[nivel].replace("§a§l", "").toLowerCase(Locale.ROOT) + " §festá aberta. Sigam a bússola.");
        // a saida faz barulho: as criaturas vao para la
        for (Cacador c : cacadores) c.ouvir(s, 80, null);
    }

    private void passouPelaSaida(Player p) {
        if (trocaEm > 0) return;
        avisar("§a" + p.getName() + " §fpassou pela porta...");
        if (nivel >= CORRIDA) {
            vitoria(p);
            return;
        }
        proximo = etapa + 1 < ordem.size() ? ordem.get(etapa + 1) : CORRIDA;
        trocaEm = tick + seg(1.5);
        for (Player o : online(true)) {
            o.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, false));
            o.playSound(o.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
            titulo(o, "", "§7você sente o chão sumir...", 1.5);
        }
    }

    void pularNivel() {
        if (estado != Estado.JOGANDO) return;
        if (nivel >= CORRIDA) vitoria(null);
        else irParaNivel(etapa + 1 < ordem.size() ? ordem.get(etapa + 1) : CORRIDA);
    }

    void abrirTeste() {
        pegos = necessarios;
        abrirSaida();
    }

    // ================================================================== durante a partida

    private void jogar() {
        if (trocaEm > 0) {
            if (tick >= trocaEm) irParaNivel(proximo);
            return;
        }
        List<Player> vivos = vivos();
        if (vivos.isEmpty()) {
            if (alguemPodeVoltar()) return;   // alguem com Moeda de Reviver ainda decidindo
            derrota();
            return;
        }
        int limite = pl.getConfig().getInt("tempo-limite-minutos", 60);
        if (limite > 0 && tick - inicioTick > seg(limite * 60)) {
            avisar("§cO tempo acabou. As luzes se apagaram de vez...");
            derrota();
            return;
        }
        Nivel nv = atual();
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            Location l = p.getLocation();
            if (!nv.dentro(l)) {
                pl.mover(p, nivel == CORRIDA ? voltaCorrida() : nv.inicio(mundo, rnd.nextInt(9)));
                continue;
            }
            // barulho de andar e correr
            if (j.ultimaPos != null && tick % 2 == 0) {
                double dx = l.getX() - j.ultimaPos.getX(), dz = l.getZ() - j.ultimaPos.getZ();
                double d = dx * dx + dz * dz;
                if (p.isSprinting() && d > 0.3) barulho(l, 18, p);
                else if (!p.isSneaking() && d > 0.15) barulho(l, 7, p);
            }
            if (tick % 2 == 0) j.ultimaPos = l.clone();
            if (nivel < CORRIDA && (nivel != ESCURO || !lanternaLigada(p))) escuroDeVerdade(p);
            if (nivel == ESCURO) pilha(p, j);
            if (nivel < CORRIDA) {
                sanidade(p, j);
                if (estado != Estado.JOGANDO) return;
                pegar(p, j);
                if (saidaAberta && l.distanceSquared(nv.saida(mundo)) < 1.6 * 1.6) {
                    passouPelaSaida(p);
                    return;
                }
            }
        }
        for (Cacador c : cacadores) {
            c.tick(tick, vivos);
            if (estado != Estado.JOGANDO || trocaEm > 0) return;
        }
        for (Sorridente s : sorridentes) s.tick(tick, vivos);
        if (nivel == CORRIDA) corrida(vivos);
        if (estado != Estado.JOGANDO) return;
        materializar(vivos);
        ambiente(vivos);
        festeiro(vivos);
        apagoes(vivos);
        passos(vivos);
        luzesPerto(vivos);
        piscar(vivos);
        musica(vivos);
        sustos(vivos);
        if (tick % 4 == 0) bussolas(vivos, false);
        hud();
        hudFila();
    }

    /** Todo tick. */
    void sincronizar() {
        ticks++;
        if (estado != Estado.JOGANDO) return;
        if (nivel == ESCURO && ticks % 2 == 0) lanterna.tick(mundo, vivos(), this::lanternaLigada, dif("lanterna-alcance", 18));
        moverSustos();
        for (Cacador c : cacadores) c.sincronizar();
        for (Sorridente s : sorridentes) s.mover();
        if (nivel == CORRIDA) moverCorrida();
    }

    void barulho(Location l, double raio, Player quem) {
        for (Cacador c : cacadores) c.ouvir(l, raio, quem);
    }

    private void sanidade(Player p, Jogador j) {
        double base = dif("sanidade-por-segundo", 0.09) * 0.25;
        int luz = p.getEyeLocation().getBlock().getLightLevel();
        if (luz <= 5) base *= 2;
        for (Cacador c : cacadores) {
            Location cl = c.local();
            if (cl != null && cl.distanceSquared(p.getLocation()) < 10 * 10) base += 0.15 * 0.25;
        }
        j.sanidade -= base;
        double s = j.sanidade;
        if (s < 35 && tick >= j.proximoEfeito) {
            j.proximoEfeito = tick + seg(8 + rnd.nextInt(10));
            Location atras = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-3));
            switch (rnd.nextInt(4)) {
                case 0 -> p.playSound(atras, Sound.AMBIENT_CAVE, 0.8f, 0.6f + rnd.nextFloat() * 0.5f);
                case 1 -> p.playSound(atras, Sound.BLOCK_SCULK_STEP, 1f, 0.6f);
                case 2 -> Sons.para(p, atras, "sussurros", 0.9f, 0.9f + rnd.nextFloat() * 0.2f, Sound.ENTITY_ENDERMAN_AMBIENT, 0.6f, 0.5f);
                default -> {
                    if (s < 25) p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, false));
                    else p.playSound(atras, Sound.ENTITY_WARDEN_NEARBY_CLOSE, 0.5f, 1.2f);
                }
            }
        }
        if (s < 15 && tick >= j.proximoBatimento) {
            j.proximoBatimento = tick + seg(2.5);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
            if (rnd.nextInt(4) == 0) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        }
        if (s <= 0) {
            j.sanidade = 0;
            pego(p, "a loucura");
        }
    }

    private void pegar(Player p, Jogador j) {
        Location l = p.getLocation();
        for (Coletavel c : coletaveis) {
            if (c.pego) continue;
            double dx = c.l.getX() - l.getX(), dz = c.l.getZ() - l.getZ();
            if (dx * dx + dz * dz > 1.6 * 1.6 || Math.abs(c.l.getY() - l.getY()) > 1.6) continue;
            if (c.chave) {
                if (saidaAberta) continue;
                c.pego = true;
                if (c.ent != null) c.ent.remove();
                c.ent = null;
                pegos++;
                mundo.playSound(c.l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.4f);
                p.playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                if (nivel == ESCURO) {
                    j.bateria = 100;
                    p.sendMessage(Backrooms.PREFIXO + "§7A pilha também recarregou a sua §elanterna§7 (100%).");
                }
                barulho(c.l, 8, p);
                if (pegos < necessarios) {
                    avisar("§f" + p.getName() + " §7achou " + ITEM_NOME[nivel] + " §7(§f" + pegos + "/" + necessarios + "§7)");
                    titulo(p, "", ITEM_NOME[nivel] + " §f" + pegos + "/" + necessarios, 1.5);
                } else abrirSaida();
            } else {
                if (aguas(p) >= 3) {
                    if (tick % 8 == 0) p.sendActionBar(Backrooms.txt("§7Você já carrega 3 águas."));
                    continue;
                }
                c.pego = true;
                if (c.ent != null) c.ent.remove();
                c.ent = null;
                p.getInventory().addItem(agua());
                p.playSound(l, Sound.ITEM_BOTTLE_FILL, 1f, 1f);
                if (nivel == ESCURO) j.bateria = Math.min(100, j.bateria + 25);
                p.sendActionBar(Backrooms.txt("§fVocê pegou uma §bÁgua de Amêndoa§f. §7(beba quando a sanidade cair)"));
            }
        }
    }

    /** Os itens so existem de verdade perto de alguem (as fases sao enormes). */
    private void materializar(List<Player> vivos) {
        if (tick % 4 != 0) return;
        for (Coletavel c : coletaveis) {
            if (c.pego) continue;
            double perto = 1e9;
            for (Player p : vivos) perto = Math.min(perto, p.getLocation().distanceSquared(c.l));
            boolean existe = c.ent != null && c.ent.isValid();
            if (!existe && perto < 40 * 40) {
                ItemStack it = c.chave ? new ItemStack(ITEM_MAT[nivel]) : agua();
                ItemMeta m = it.getItemMeta();
                m.getPersistentDataContainer().set(pl.kColetavel, PersistentDataType.STRING, UUID.randomUUID().toString());
                it.setItemMeta(m);
                c.ent = mundo.dropItem(c.l, it, i -> {
                    i.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
                    i.setPickupDelay(Integer.MAX_VALUE);
                    i.setCanPlayerPickup(false);
                    i.setCanMobPickup(false);
                    i.setUnlimitedLifetime(true);
                    i.setInvulnerable(true);
                    i.setPersistent(false);
                    i.setGravity(false);
                    i.setVelocity(new org.bukkit.util.Vector());
                    i.setGlowing(c.chave && "facil".equals(modo));
                });
            } else if (existe && perto > 56 * 56) {
                c.ent.remove();
                c.ent = null;
            }
            if (c.chave && perto < 24 * 24) {
                mundo.spawnParticle(Particle.END_ROD, c.l.clone().add(0, 0.4, 0), 2, 0.25, 0.3, 0.25, 0.01);
                if (tick % 12 == 0) mundo.playSound(c.l, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.2f);
            }
        }
    }

    /**
     * A bussola aponta para o item mais perto (e para a saida quando ela abre).
     * sempre = atualiza toda hora; as-vezes = so 8 segundos a cada 30; lenta = atualiza a cada X segundos e fica parada no meio.
     */
    private void bussolas(List<Player> vivos, boolean forcar) {
        String tipo = difTexto("bussola", "as-vezes");
        Location saida = atual().saida(mundo);
        boolean atualizar = forcar || nivel == CORRIDA || saidaAberta || switch (tipo) {
            case "sempre" -> true;
            case "as-vezes" -> (tick / 4) % 30 < 8;
            default -> tick >= proximaBussola;
        };
        if (!atualizar) return;
        if (!tipo.equals("sempre") && !tipo.equals("as-vezes")) proximaBussola = tick + seg(dif("bussola-segundos", 15));
        for (Player p : vivos) {
            Location alvoB;
            if (nivel == CORRIDA || saidaAberta) alvoB = saida;
            else {
                alvoB = null;
                double md = 1e18;
                for (Coletavel c : coletaveis) {
                    if (c.pego || !c.chave) continue;
                    double d = c.l.distanceSquared(p.getLocation());
                    if (d < md) {
                        md = d;
                        alvoB = c.l;
                    }
                }
                if (alvoB == null) alvoB = p.getLocation();
            }
            p.setCompassTarget(alvoB);
        }
    }

    private void hud() {
        if (tick % 2 != 0) return;
        Nivel nv = atual();
        for (Jogador j : jogadores.values()) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p == null) continue;
            String obj;
            if (nivel == CORRIDA) {
                double falta = Math.max(0, nv.saidaX - p.getX());
                obj = "§c§lCORRA! §7faltam §f" + (int) falta + "m";
            } else obj = saidaAberta ? "§a§lSAÍDA ABERTA" : ITEM_HUD[nivel] + " §f" + pegos + "/" + necessarios;
            if (j.fora) {
                p.sendActionBar(Backrooms.txt("§7Espectador §8| §e" + nv.nome + " §8| " + obj + " §8| §fainda presos: §e" + vivos().size()));
                continue;
            }
            int s = (int) Math.ceil(j.sanidade);
            String cor = s > 60 ? "§a" : s > 30 ? "§e" : "§c";
            String pilha = "";
            if (nivel == ESCURO) {
                int b = (int) Math.ceil(j.bateria);
                pilha = " §8| §6Lanterna " + (b > 40 ? "§f" : b > 15 ? "§e" : "§c") + b + "%";
            }
            p.sendActionBar(Backrooms.txt("§e" + nv.nome + " §7(" + (etapa + 1) + "/" + ordem.size() + ") §8| " + obj + pilha + " §8| §bSanidade " + cor + s + "% §8| §c" + "❤".repeat(Math.max(0, j.vidas))));
            p.setLevel(j.vidas);
            p.setExp((float) Math.max(0, Math.min(0.999, j.sanidade / 100.0)));
        }
    }

    private void ambiente(List<Player> vivos) {
        for (Player p : vivos) {
            Location l = p.getLocation();
            switch (nivel) {
                case 0 -> {
                    // o zumbido das lampadas fluorescentes do Nivel 0 (3s, em loop)
                    if (Sons.temPacote(p)) {
                        if (tick % 12 == 0) Sons.para(p, l, "zumbido", 0.45f, 1f, null, 0f, 0f);
                    } else if (tick % 16 == 0) p.playSound(l, Sound.BLOCK_BEACON_AMBIENT, 0.35f, 0.55f);
                    if (rnd.nextInt(220) == 0) p.playSound(perto(l, 12), Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.6f, 0.5f);
                    if (rnd.nextInt(520) == 0) Sons.para(p, perto(l, 30), "distante", 0.8f, 0.9f + rnd.nextFloat() * 0.2f, Sound.AMBIENT_CAVE, 0.5f, 0.5f);
                }
                case 1 -> {
                    if (rnd.nextInt(360) == 0) Sons.para(p, perto(l, 14), "batidas", 0.8f, 0.9f + rnd.nextFloat() * 0.2f, Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 0.5f);
                    if (rnd.nextInt(600) == 0) Sons.para(p, perto(l, 30), "distante", 0.8f, 0.85f + rnd.nextFloat() * 0.2f, Sound.AMBIENT_CAVE, 0.5f, 0.5f);
                    if (rnd.nextInt(10) == 0) p.playSound(perto(l, 8), Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, 0.8f, 0.9f + rnd.nextFloat() * 0.2f);
                    if (rnd.nextInt(16) == 0) mundo.spawnParticle(Particle.DRIPPING_WATER, perto(l, 5).add(0, 3.8, 0), 1);
                    if (rnd.nextInt(260) == 0) p.playSound(perto(l, 14), Sound.BLOCK_IRON_DOOR_CLOSE, 0.7f, 0.5f);
                }
                case 2 -> {
                    if (rnd.nextInt(24) == 0) {
                        Location v = perto(l, 6).add(0, 2.5, 0);
                        p.playSound(v, Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 0.5f);
                        mundo.spawnParticle(Particle.CLOUD, v, 6, 0.3, 0.1, 0.3, 0.02);
                    }
                    if (rnd.nextInt(140) == 0) p.playSound(perto(l, 10), Sound.BLOCK_ANVIL_LAND, 0.15f, 0.5f);
                    if (tick % 24 == 0) p.playSound(l, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.3f, 0.5f);
                    if (rnd.nextInt(320) == 0) Sons.para(p, perto(l, 12), "batidas", 1f, 0.7f + rnd.nextFloat() * 0.15f, Sound.BLOCK_ANVIL_LAND, 0.2f, 0.5f);
                    if (rnd.nextInt(560) == 0) Sons.para(p, perto(l, 30), "distante", 0.8f, 0.8f + rnd.nextFloat() * 0.2f, Sound.AMBIENT_CAVE, 0.5f, 0.5f);
                }
                case 3 -> {
                    // zumbido eletrico, faiscas e estalos
                    if (tick % 14 == 0) p.playSound(l, Sound.BLOCK_BEACON_AMBIENT, 0.3f, 1.6f);
                    if (rnd.nextInt(6) == 0) {
                        Location f = perto(l, 7).add(0, 1 + rnd.nextDouble() * 2.5, 0);
                        mundo.spawnParticle(Particle.ELECTRIC_SPARK, f, 6, 0.15, 0.15, 0.15, 0.2);
                        if (rnd.nextInt(3) == 0) p.playSound(f, Sound.BLOCK_COPPER_BULB_TURN_ON, 0.4f, 0.5f + rnd.nextFloat());
                    }
                    if (rnd.nextInt(90) == 0) {
                        Location f = perto(l, 10).add(0, 2, 0);
                        p.playSound(f, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.25f, 1.8f);
                        mundo.spawnParticle(Particle.ELECTRIC_SPARK, f, 30, 0.4, 0.6, 0.4, 0.5);
                    }
                    if (rnd.nextInt(300) == 0) p.playSound(perto(l, 14), Sound.ENTITY_WOLF_BIG_GROWL, 0.4f, 0.5f);
                    if (rnd.nextInt(520) == 0) Sons.para(p, perto(l, 30), "distante", 0.8f, 0.8f + rnd.nextFloat() * 0.2f, Sound.AMBIENT_CAVE, 0.5f, 0.5f);
                }
                case 4 -> {
                    // musica de festa abafada, baloes estourando, risadinhas
                    if (tick % 10 == 0 && rnd.nextInt(3) == 0)
                        p.playSound(perto(l, 25), Sound.BLOCK_NOTE_BLOCK_BELL, 0.25f, new float[] {0.5f, 0.6f, 0.67f, 0.75f, 0.84f}[rnd.nextInt(5)]);
                    if (rnd.nextInt(240) == 0) p.playSound(perto(l, 12), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.4f);
                    if (rnd.nextInt(500) == 0) Sons.para(p, perto(l, 18), "festeiro_risada", 0.45f, 0.8f + rnd.nextFloat() * 0.3f, Sound.ENTITY_WITCH_CELEBRATE, 0.3f, 0.6f);
                    if (rnd.nextInt(40) == 0) mundo.spawnParticle(Particle.DUST, perto(l, 6).add(0, 2.5, 0), 4, 0.5, 0.3, 0.5, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)), 1.2f));
                }
                case 5 -> {
                    // agua pingando, eco de piscina vazia, algo nadando
                    if (rnd.nextInt(8) == 0) p.playSound(perto(l, 10), Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, 0.8f, 0.7f + rnd.nextFloat() * 0.4f);
                    if (tick % 20 == 0) p.playSound(l, Sound.AMBIENT_UNDERWATER_LOOP, 0.15f, 0.6f);
                    if (rnd.nextInt(200) == 0) p.playSound(perto(l, 14), Sound.ENTITY_PLAYER_SPLASH, 0.6f, 0.6f);
                    if (rnd.nextInt(520) == 0) Sons.para(p, perto(l, 30), "distante", 0.8f, 0.7f + rnd.nextFloat() * 0.2f, Sound.AMBIENT_CAVE, 0.5f, 0.5f);
                    if (p.isInWater() && !p.isSneaking() && tick % 4 == 0) barulho(l, 14, p);
                }
                case ESCURO -> {
                    // silencio quase total: respiracao, portas, sussurros e algo arranhando a parede
                    if (tick % 28 == 0) p.playSound(l, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.25f, 0.5f);
                    if (rnd.nextInt(260) == 0) p.playSound(perto(l, 14), Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.7f, 0.5f);
                    if (rnd.nextInt(340) == 0) Sons.para(p, perto(l, 10), "sussurros", 0.8f, 0.7f + rnd.nextFloat() * 0.2f, Sound.ENTITY_ENDERMAN_AMBIENT, 0.5f, 0.4f);
                    if (rnd.nextInt(300) == 0) p.playSound(perto(l, 6), Sound.ENTITY_CREAKING_AMBIENT, 0.5f, 0.6f);
                    if (rnd.nextInt(420) == 0) p.playSound(perto(l, 4), Sound.ENTITY_PLAYER_BREATH, 0.9f, 0.6f);
                    if (rnd.nextInt(520) == 0) Sons.para(p, perto(l, 30), "distante", 0.8f, 0.6f + rnd.nextFloat() * 0.2f, Sound.AMBIENT_CAVE, 0.5f, 0.5f);
                }
                default -> { }
            }
        }
    }

    /**
     * O Festeiro (Niveis 1, 2 e 3): de tempos em tempos aparece parado ao longe, na frente de alguem, olhando.
     * Quando chegam perto (ou depois de uns segundos) ele some com uma risada e a sanidade de quem viu cai.
     */
    private void festeiro(List<Player> vivos) {
        if (nivel >= CORRIDA || vivos.isEmpty()) return;
        if (festeiro != null) {
            if (!festeiro.isValid()) {
                festeiro = null;
                return;
            }
            Location fl = festeiro.getLocation();
            Player maisPerto = null;
            double md = 1e9;
            for (Player p : vivos) {
                double d = p.getLocation().distanceSquared(fl);
                if (d < md) {
                    md = d;
                    maisPerto = p;
                }
            }
            if (md < 7 * 7 || tick >= festeiroAte) {
                sumirFesteiro(vivos, md < 7 * 7);
                return;
            }
            if (maisPerto != null) {
                Location olhar = fl.clone();
                olhar.setDirection(maisPerto.getLocation().toVector().subtract(fl.toVector()));
                festeiro.setRotation(olhar.getYaw(), 0);
                festeiro.setBodyYaw(olhar.getYaw());
            }
            return;
        }
        if (proximoFesteiro == 0) proximoFesteiro = tick + seg(25 + rnd.nextInt(25));
        if (tick < proximoFesteiro) return;
        proximoFesteiro = tick + seg(dif("festeiro-segundos", 55) * (0.7 + rnd.nextDouble() * 0.6));
        Player p = vivos.get(rnd.nextInt(vivos.size()));
        Location l = lugarVisivel(p, 11, 26);
        if (l == null) return;
        Location olhar = l.clone();
        olhar.setDirection(p.getLocation().toVector().subtract(l.toVector()));
        olhar.setPitch(0);
        String sk = skinNivel();
        festeiro = mundo.spawn(olhar, Mannequin.class, w -> {
            w.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
            w.setProfile(Skins.perfil(sk));
            w.setDescription(null);
            w.setImmovable(true);
            w.setCanPickupItems(false);
            w.setGravity(false);
            w.setSilent(true);
            w.setInvulnerable(true);
            w.setCollidable(false);
            w.setPersistent(false);
            w.setRemoveWhenFarAway(false);
            AttributeInstance sc = w.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(sk.equals("bacteria") ? 1.6 : 1.05);
        });
        Visual.gemeo(festeiro, sk);
        festeiroAte = tick + seg(14);
        if (nivel == 4) Sons.para(p, l, "festeiro_risada", 0.9f, 1f, Sound.ENTITY_WITCH_CELEBRATE, 0.35f, 0.55f);
        else p.playSound(l, Sound.AMBIENT_CAVE, 0.7f, 0.5f);
    }

    private void sumirFesteiro(List<Player> vivos, boolean chegaram) {
        Location fl = festeiro.getLocation();
        if (chegaram) {
            for (Player p : vivos) {
                if (p.getLocation().distanceSquared(fl) > 14 * 14) continue;
                Jogador j = jogadores.get(p.getUniqueId());
                if (j != null && !j.fora) j.sanidade = Math.max(0, j.sanidade - 8);
                if (nivel == 4) {
                    Sons.para(p, fl, "sorridente_risada", 1f, 0.7f, Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.5f);
                    p.playSound(fl, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 0.6f, 0.6f);
                    titulo(p, "", "§e§lVOCÊ FOI CONVIDADO PARA A FESTA :)", 1.2);
                } else if (rnd.nextInt(5) < 2 && j != null && !j.fora && tick >= j.graca) {
                    // as vezes, em vez de sumir, ela aparece na sua cara
                    caraACara(p, skinNivel(), 1.2);
                } else {
                    p.playSound(fl, Sound.ENTITY_CREAKING_DEACTIVATE, 1f, 0.5f);
                    titulo(p, "", "§8ela estava te olhando...", 1.2);
                }
            }
            mundo.spawnParticle(Particle.LARGE_SMOKE, fl.clone().add(0, 1, 0), 25, 0.3, 0.7, 0.3, 0.02);
            mundo.spawnParticle(Particle.DUST, fl.clone().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 230, 40), 1.4f));
        } else mundo.spawnParticle(Particle.SMOKE, fl.clone().add(0, 1, 0), 12, 0.3, 0.7, 0.3, 0.01);
        festeiro.remove();
        festeiro = null;
    }

    /** Um lugar andavel na frente do jogador (entre min e max blocos) que ele consegue ver. */
    private Location lugarVisivel(Player p, double min, double max) {
        Nivel nv = atual();
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) return null;
        dir.normalize();
        List<int[]> cand = Mapa.embaralhado(nv.lugares, rnd);
        int testados = 0;
        for (int[] c : cand) {
            double dx = c[0] + 0.5 - olho.getX(), dz = c[1] + 0.5 - olho.getZ(), d = Math.hypot(dx, dz);
            if (d < min || d > max) continue;
            if ((dx * dir.getX() + dz * dir.getZ()) / d < 0.85) continue;
            if (++testados > 40) break;
            Location l = new Location(mundo, c[0] + 0.5, Nivel.Y, c[1] + 0.5);
            Vector para = l.clone().add(0, 1.5, 0).toVector().subtract(olho.toVector());
            if (mundo.rayTraceBlocks(olho, para.clone().normalize(), para.length(), org.bukkit.FluidCollisionMode.NEVER, true) != null) continue;
            return l;
        }
        return null;
    }

    private Location perto(Location l, double r) {
        return l.clone().add((rnd.nextDouble() * 2 - 1) * r, 0, (rnd.nextDouble() * 2 - 1) * r);
    }

    /**
     * Nivel 0: de vez em quando as lampadas em volta de alguem apagam todas de uma vez (som de energia caindo),
     * ficam assim uns 6 a 9 s e voltam aos poucos. No escuro as Bacterias ouvem e vem para o lugar.
     */
    private void apagoes(List<Player> vivos) {
        if (nivel >= CORRIDA || vivos.isEmpty()) {
            proximoApagao = -1;
            return;
        }
        if (fimApagao > 0 && tick >= fimApagao) {
            fimApagao = -1;
            if (ondeApagou != null) for (Player p : vivos)
                if (p.getLocation().distanceSquared(ondeApagou) < 30 * 30) p.playSound(ondeApagou, "minecraft:block.beacon.activate", 0.7f, 0.6f);
        }
        // segundos entre apagoes (media): facil 80, normal 55, dificil 40 (da para mudar no config: modos.<modo>.apagao-a-cada)
        double cada = Math.max(20, dif("apagao-a-cada", "facil".equals(modo) ? 80 : "dificil".equals(modo) ? 40 : 55));
        if (proximoApagao < 0) {
            proximoApagao = tick + seg(cada * (0.5 + rnd.nextDouble() * 0.4));
            return;
        }
        if (tick < proximoApagao) return;
        proximoApagao = tick + seg(cada * (0.7 + rnd.nextDouble() * 0.6));
        apagao(vivos.get(rnd.nextInt(vivos.size())), vivos);
    }

    void apagao(Player alvo, List<Player> vivos) {
        Nivel nv = atual();
        Location l = alvo.getLocation();
        int r = 14, ly = nv.yLampada();
        Material acesa = nv.acesa.getMaterial();
        long dura = seg(6 + rnd.nextInt(4));
        int n = 0;
        for (int x = l.getBlockX() - r; x <= l.getBlockX() + r; x++)
            for (int z = l.getBlockZ() - r; z <= l.getBlockZ() + r; z++) {
                int dx = x - l.getBlockX(), dz = z - l.getBlockZ();
                if (dx * dx + dz * dz > r * r || !nv.contem(x, z)) continue;
                Block b = mundo.getBlockAt(x, ly, z);
                if (b.getType() != acesa) continue;
                b.setBlockData(nv.apagada, false);
                // voltam aos poucos, piscando
                apagadas.put(((long) x << 32) ^ (z & 0xffffffffL), new int[] {x, ly, z, (int) (dura + rnd.nextInt((int) seg(2)))});
                n++;
            }
        if (n == 0) return;
        ondeApagou = l.clone();
        fimApagao = tick + dura;
        for (Player p : vivos) {
            if (p.getLocation().distanceSquared(l) > 30 * 30) continue;
            p.playSound(l, "minecraft:block.beacon.deactivate", 1.4f, 0.5f);
            p.playSound(l, "minecraft:block.tripwire.click_off", 1f, 0.5f);
            if (p.getLocation().distanceSquared(l) < r * r) escurecer.accept(p);
        }
        // no escuro as Bacterias ouvem
        barulho(l, 40, alvo);
    }

    /**
     * Nivel 0: nas partes sem luz (luz 4 ou menos onde a pessoa esta) a visao escurece de verdade com o efeito
     * Escuridao, que tambem vence o brilho alto do jogo. Ao voltar para a luz, some em 1 a 2 s.
     */
    private void escuroDeVerdade(Player p) {
        int luz = p.getEyeLocation().getBlock().getLightLevel();
        if (luz > dif("escuro-luz-maxima", 4)) return;
        if (escuroRestante.applyAsInt(p) > 25) return;
        darEscuro.accept(p, 60);
    }

    /** Efeito Escuridao (trocaveis nos testes, que rodam sem servidor). */
    java.util.function.BiConsumer<Player, Integer> darEscuro = (p, ticks) -> p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
    java.util.function.ToIntFunction<Player> escuroRestante = p -> {
        PotionEffect e = p.getPotionEffect(PotionEffectType.DARKNESS);
        return e == null ? 0 : e.isInfinite() ? Integer.MAX_VALUE : e.getDuration();
    };

    /**
     * Nivel 0: de vez em quando a pessoa ouve passos atras dela, chegando mais perto, e de repente param.
     * Nao tem ninguem la (so o som, so para ela).
     */
    private void passos(List<Player> vivos) {
        if (nivel >= CORRIDA) return;
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null) continue;
            if (j.passos > 0) {
                if (tick < j.proximoPasso) continue;
                Vector atras = p.getLocation().getDirection().setY(0);
                if (atras.lengthSquared() < 1e-4) atras = new Vector(1, 0, 0);
                Location onde = p.getLocation().add(atras.normalize().multiply(-j.passoDist));
                p.playSound(onde, "minecraft:block.stone.step", 1f, 0.75f + rnd.nextFloat() * 0.1f);
                j.passoDist = Math.max(2.5, j.passoDist - 1.1);
                j.passos--;
                j.proximoPasso = tick + 2;
                continue;
            }
            double cada = Math.max(15, dif("passos-a-cada", 45));
            if (j.proximosPassos <= 0 || j.proximosPassos > tick + seg(cada * 2)) {
                j.proximosPassos = tick + seg(cada * (0.4 + rnd.nextDouble() * 0.6));
                continue;
            }
            if (tick < j.proximosPassos) continue;
            j.proximosPassos = tick + seg(cada * (0.7 + rnd.nextDouble() * 0.6));
            j.passos = 4 + rnd.nextInt(3);
            j.passoDist = 9;
            j.proximoPasso = tick;
        }
    }

    /** Lampadas que piscam (so as que estao perto de alguem). */
    private void piscar(List<Player> vivos) {
        Nivel nv = atual();
        Iterator<Map.Entry<Long, int[]>> it = apagadas.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, int[]> e = it.next();
            int[] b = e.getValue();
            if (b[3]-- <= 0) {
                mundo.getBlockAt(b[0], b[1], b[2]).setBlockData(nv.acesa, false);
                it.remove();
            }
        }
        if (nv.piscando.isEmpty() || tick % 2 != 0) return;
        double chance = nivel == CORRIDA ? 0.10 : 0.06;
        for (int[] b : nv.piscando) {
            long k = ((long) b[0] << 32) ^ (b[2] & 0xffffffffL);
            if (apagadas.containsKey(k) || rnd.nextDouble() >= chance) continue;
            boolean perto = false;
            for (Player p : vivos) {
                double dx = p.getX() - b[0], dz = p.getZ() - b[2];
                if (dx * dx + dz * dz < 26 * 26) {
                    perto = true;
                    break;
                }
            }
            if (!perto) continue;
            Block bl = mundo.getBlockAt(b[0], b[1], b[2]);
            bl.setBlockData(nv.apagada, false);
            apagadas.put(k, new int[] {b[0], b[1], b[2], 1 + rnd.nextInt(nivel == CORRIDA ? 3 : 8)});
            if (rnd.nextInt(3) == 0) Sons.local(mundo, bl.getLocation(), 24, "lampada", 0.8f, 0.85f + rnd.nextFloat() * 0.3f, Sound.BLOCK_TRIPWIRE_CLICK_OFF, 0.6f, 0.5f);
        }
    }

    // ================================================================== sustos de verdade

    /** Skin do monstro da fase atual (para aparicoes e sustos). */
    String skinNivel() {
        return switch (nivel) {
            case 2 -> "uivador";
            case 5 -> "afogado";
            case 3 -> "cao";
            case 4 -> "festeiro";
            case ESCURO -> "sombra";
            case CORRIDA -> "corredor";
            default -> "bacteria";
        };
    }

    private boolean cacadoPorAlguem(Player p) {
        for (Cacador c : cacadores) if (c.cacando(p)) return true;
        return false;
    }

    private boolean temSusto(Player p) {
        for (Susto x : sustos) if (x.quem.equals(p.getUniqueId())) return true;
        return false;
    }

    /** O jogador esta olhando para "l" (ate "ang" graus do centro da visao)? */
    private static boolean olhandoPara(Player p, Location l, double ang) {
        Location olho = p.getEyeLocation();
        Vector para = l.toVector().subtract(olho.toVector());
        if (para.lengthSquared() < 1e-4) return true;
        return Math.toDegrees(olho.getDirection().angle(para.normalize())) < ang;
    }

    private static boolean livre(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
    }

    /** Manequim que so "quem" ve, olhando para ele. */
    private Mannequin aparicao(Player quem, Location l, String skin, double escala) {
        Mannequin m = mundo.spawn(l, Mannequin.class, w -> {
            w.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
            w.setVisibleByDefault(false);
            w.setProfile(Skins.perfil(skin));
            w.setDescription(null);
            w.setImmovable(true);
            w.setCanPickupItems(false);
            w.setGravity(false);
            w.setSilent(true);
            w.setInvulnerable(true);
            w.setCollidable(false);
            w.setPersistent(false);
            w.setRemoveWhenFarAway(false);
            AttributeInstance sc = w.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(escala);
        });
        Visual.gemeo(m, skin, quem);
        return m;
    }

    private Location olhandoPara(Location de, Location alvo) {
        Location o = de.clone();
        o.setDirection(alvo.toVector().subtract(de.toVector()));
        o.setPitch(0);
        return o;
    }

    /** Grito na cara (o susto em si). */
    private void grito(Player p, Location l, String skin) {
        String g = skin.equals("festeiro") ? "sorridente_grito" : skin.equals("cao") ? "cao_ataque" : skin.equals("uivador") ? "uivador_uivo" : "bacteria_grito";
        Sons.para(p, l, g, 1.6f, 0.85f + rnd.nextFloat() * 0.1f, Sound.ENTITY_ENDERMAN_SCREAM, 1.6f, 0.5f);
        p.playSound(l, Sound.ENTITY_GHAST_SCREAM, 0.7f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.6f);
        if (Sons.bedrock(p)) {
            // celular: sem o rosto na tela, entao o susto vem no som (grito agudo colado no ouvido) e na camera tremendo
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.MASTER, 1f, 0.75f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.MASTER, 1f, 0.9f);
            p.playSound(p.getLocation(), Sound.ENTITY_GHAST_HURT, SoundCategory.MASTER, 1f, 0.6f);
            Tela.tremer(p, 1.4f, 1.1f);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 70, 0, false, false, false));
        Jogador j = jogadores.get(p.getUniqueId());
        if (j != null && !j.fora) j.sanidade = Math.max(1, j.sanidade - 10);
    }

    /** O monstro aparece colado na cara da pessoa por meio segundo, com um grito. */
    void caraACara(Player p, String skin, double dist) {
        Location olho = p.getLocation();
        Vector f = olho.getDirection().setY(0);
        if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
        f.normalize();
        Location l = null;
        for (double d = dist; d >= 0.6; d -= 0.3) {
            Location c = olho.clone().add(f.clone().multiply(d));
            if (livre(c)) {
                l = c;
                break;
            }
        }
        if (l == null) l = olho.clone().add(f.clone().multiply(0.6));
        boolean celular = Sons.bedrock(p) && Modelo3D.emprestada(skin) != null && pl.getConfig().getBoolean("modelo-bedrock", true);
        if (celular) {
            // No Bedrock o monstro e o modelo 3D do pacote (bem maior que o manequim): colado na cara a camera fica
            // dentro dele e nao se ve nada. Fica um pouco mais longe e mais baixo, com o rosto na altura dos olhos.
            double longe = skin.equals("bacteria") ? 1.9 : 2.1, baixo = skin.equals("bacteria") ? 2.0 : 0.75;
            Location c = olho.clone().add(f.clone().multiply(longe));
            if (!livre(c)) c = olho.clone().add(f.clone().multiply(Math.max(1.2, longe - 0.7)));
            l = c.subtract(0, baixo, 0);
        }
        l = olhandoPara(l, p.getLocation());
        Mannequin m = aparicao(p, l, skin, skin.equals("bacteria") ? 1.25 : 1.0);
        sustos.add(new Susto(p.getUniqueId(), 0, m, l, tick + (celular ? 4 : 3)));
        Visual.gritar(m, 20);
        grito(p, l, skin);
        rostoNaTela(p, skin);
        mundo.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 10, 0.2, 0.5, 0.2, 0.01);
    }

    /** Caractere da fonte do pacote com o rosto gigante de cada monstro (null = nao tem). Bate com fontes/modelos/gerar.py. */
    static String glifo(String skin) {
        return switch (skin) {
            case "bacteria" -> "\uE010";
            case "uivador" -> "\uE011";
            case "cao" -> "\uE012";
            case "festeiro" -> "\uE013";
            case "sombra" -> "\uE014";
            case "corredor" -> "\uE015";
            case "afogado" -> "\uE016";
            default -> null;
        };
    }

    /** Quadrado preto da fonte (\uE0F0) e recuo de 1 px (\uF801): tapa a tela toda em volta do rosto. */
    private static final String PRETO = "\uE0F0", RECUO = "\uF801";

    /** O rosto do monstro pula na tela em 3 tempos (longe, perto, na cara) - so Java com o pacote. */
    void rostoNaTela(Player p, String skin) {
        String g = glifo(skin);
        if (g == null || !Sons.temPacote(p) || Sons.bedrock(p) || !pl.getConfig().getBoolean("rosto-na-tela", true)) return;
        String[] fontes = {"susto_p", "susto_m", "susto"};
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                String texto = kk == 2 ? PRETO + RECUO + PRETO + RECUO + g + RECUO + PRETO + RECUO + PRETO : g;
                p.showTitle(Title.title(
                        net.kyori.adventure.text.Component.text(texto).font(net.kyori.adventure.key.Key.key("corona", fontes[kk])),
                        net.kyori.adventure.text.Component.empty(),
                        Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(kk == 2 ? 650 : 200), java.time.Duration.ofMillis(kk == 2 ? 250 : 0))));
            }, kk * 3L);
        }
    }

    /**
     * De tempos em tempos (mais vezes com a sanidade baixa) cada pessoa leva um susto que so ela ve:
     * 0 o monstro na cara, 1 alguem respirando atras (se virar, ele grita), 2 um vulto cruzando o corredor,
     * 3 as luzes apagam e dois olhos vermelhos ficam te olhando no escuro.
     */
    private void sustos(List<Player> vivos) {
        for (Iterator<Susto> it = sustos.iterator(); it.hasNext(); ) {
            Susto x = it.next();
            Player p = Bukkit.getPlayer(x.quem);
            boolean acabou = p == null || !p.isOnline() || x.m != null && !x.m.isValid() || tick >= x.ate;
            if (!acabou && x.tipo == 1 && x.m != null && olhandoPara(p, x.m.getLocation().add(0, 1.5, 0), 50) && p.hasLineOfSight(x.m)) {
                grito(p, x.m.getLocation(), skinNivel());
                // chega mais perto antes de sumir
                Location l = p.getLocation().add(x.m.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(1.0));
                if (livre(l)) x.m.teleport(olhandoPara(l, p.getLocation()));
                x.ate = tick + 2;
                x.passo = null;
                continue;
            }
            if (!acabou && x.tipo == 3 && p != null && tick % 4 == 0) p.playSound(x.l, Sound.ENTITY_WARDEN_NEARBY_CLOSER, 0.7f, 0.5f);
            if (acabou) {
                if (x.m != null && x.m.isValid()) {
                    mundo.spawnParticle(Particle.SMOKE, x.m.getLocation().add(0, 1, 0), 10, 0.2, 0.6, 0.2, 0.01);
                    x.m.remove();
                }
                it.remove();
            }
        }
        if (nivel >= CORRIDA || dif("sustos", 1) <= 0) return;
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null || j.fora) continue;
            double cada = Math.max(30, dif("susto-a-cada", 100));
            if (j.sanidade < 40) cada *= 0.6;
            if (j.proximoSusto <= 0) {
                j.proximoSusto = tick + seg(cada * (0.5 + rnd.nextDouble() * 0.5));
                continue;
            }
            if (tick < j.proximoSusto) continue;
            if (tick < j.graca || cacadoPorAlguem(p) || temSusto(p) || saidaPerto(p)) {
                j.proximoSusto = tick + seg(6);
                continue;
            }
            j.proximoSusto = tick + seg(cada * (0.7 + rnd.nextDouble() * 0.6));
            assustar(p, rnd.nextInt(4));
        }
    }

    private boolean saidaPerto(Player p) {
        return saidaAberta && p.getLocation().distanceSquared(atual().saida(mundo)) < 10 * 10;
    }

    void assustar(Player p, int tipo) {
        String sk = skinNivel();
        Location pl0 = p.getLocation();
        Vector f = pl0.getDirection().setY(0);
        if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
        f.normalize();
        switch (tipo) {
            case 1 -> {
                Location l = pl0.clone().subtract(f.clone().multiply(2.2));
                if (!livre(l)) l = pl0.clone().subtract(f.clone().multiply(1.4));
                if (!livre(l)) {
                    assustar(p, 2);
                    return;
                }
                l = olhandoPara(l, pl0);
                Mannequin m = aparicao(p, l, sk, sk.equals("bacteria") ? 1.25 : 1.0);
                sustos.add(new Susto(p.getUniqueId(), 1, m, l, tick + seg(8)));
                p.playSound(l, Sound.ENTITY_PLAYER_BREATH, 1f, 0.5f);
                Sons.para(p, l, "sussurros", 1f, 0.8f, Sound.ENTITY_ENDERMAN_AMBIENT, 0.7f, 0.4f);
                p.sendActionBar(Backrooms.txt("§8...tem alguém respirando atrás de você"));
            }
            case 2 -> {
                Location l = lugarVisivel(p, 9, 18);
                if (l == null) {
                    caraACara(p, sk, 1.4);
                    return;
                }
                Vector lado = new Vector(-f.getZ(), 0, f.getX()).multiply(rnd.nextBoolean() ? 0.6 : -0.6);
                Location ini = l.clone().subtract(lado.clone().multiply(7));
                ini.setDirection(lado);
                ini.setPitch(0);
                Mannequin m = aparicao(p, ini, sk, sk.equals("bacteria") ? 1.5 : 1.05);
                Susto x = new Susto(p.getUniqueId(), 2, m, ini, tick + seg(1));
                x.passo = lado;
                sustos.add(x);
                p.playSound(l, Sound.ENTITY_PHANTOM_SWOOP, 1f, 0.5f);
                p.playSound(l, Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 0.5f);
                Jogador j = jogadores.get(p.getUniqueId());
                if (j != null) j.sanidade = Math.max(1, j.sanidade - 5);
            }
            case 3 -> {
                apagao(p, vivos());
                Location l = lugarVisivel(p, 5, 11);
                if (l == null) l = pl0.clone().add(f.clone().multiply(5));
                sustos.add(new Susto(p.getUniqueId(), 3, null, l.clone().add(0, 1.65, 0), tick + seg(4)));
                p.playSound(l, Sound.ENTITY_WOLF_GROWL, 0.8f, 0.4f);
            }
            default -> caraACara(p, sk, 1.4);
        }
    }

    /** Todo tick: o vulto atravessa o corredor e os olhos vermelhos brilham no escuro. */
    private void moverSustos() {
        for (Susto x : sustos) {
            Player p = Bukkit.getPlayer(x.quem);
            if (p == null) continue;
            if (x.tipo == 2 && x.m != null && x.m.isValid() && x.passo != null) {
                Location l = x.m.getLocation().add(x.passo);
                l.setDirection(x.passo);
                l.setPitch(0);
                x.m.teleport(l);
                x.m.setBodyYaw(l.getYaw());
            } else if (x.tipo == 3 && ticks % 3 == 0) {
                Vector para = p.getLocation().toVector().subtract(x.l.toVector()).setY(0);
                if (para.lengthSquared() < 1e-4) continue;
                Vector lado = new Vector(-para.getZ(), 0, para.getX()).normalize().multiply(0.16);
                Particle.DustOptions verm = new Particle.DustOptions(Color.fromRGB(255, 10, 10), 0.8f);
                p.spawnParticle(Particle.DUST, x.l.clone().add(lado), 1, 0, 0, 0, 0, verm);
                p.spawnParticle(Particle.DUST, x.l.clone().subtract(lado), 1, 0, 0, 0, 0, verm);
            }
        }
    }

    /**
     * Musica de perseguicao (so para quem esta sendo cacado): baixo pesado repetindo, bumbo e o coracao,
     * que bate mais rapido quanto mais perto o monstro esta. Quando ele perde a pessoa, a musica para.
     */
    private void musica(List<Player> vivos) {
        float[] baixo = {0.5f, 0.5f, 0.53f, 0.5f, 0.6f, 0.5f, 0.53f, 0.47f};
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null || j.fora) continue;
            double d = 1e9;
            if (nivel < CORRIDA) for (Cacador c : cacadores) {
                if (!c.cacando(p)) continue;
                Location cl = c.local();
                if (cl != null) d = Math.min(d, cl.distance(p.getLocation()));
            }
            if (d > 1e8) {
                if (j.cacado) {
                    j.cacado = false;
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f);
                    p.sendActionBar(Backrooms.txt("§7Você despistou... §8por enquanto."));
                }
                continue;
            }
            Location l = p.getLocation();
            if (!j.cacado) {
                j.cacado = true;
                j.batida = 0;
                p.playSound(l, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                p.playSound(l, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.35f, 0.7f);
            }
            int b = j.batida++;
            p.playSound(l, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, baixo[b % baixo.length]);
            if (b % 2 == 0) p.playSound(l, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.9f, 0.6f);
            if (b % 16 == 8) p.playSound(l, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, d < 8 ? 0.53f : 0.5f);
            int cada = d < 6 ? 2 : d < 12 ? 3 : d < 20 ? 4 : 6;
            if (b % cada == 0) p.playSound(l, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 1f);
        }
    }

    /** As lampadas em volta de um monstro que esta andando atras de alguem falham (sinal de que ele esta perto). */
    private void luzesPerto(List<Player> vivos) {
        if (nivel >= CORRIDA || tick % 2 != 0) return;
        for (Cacador c : cacadores) {
            if (c.ia != Cacador.Ia.CACANDO && c.ia != Cacador.Ia.INVESTIGANDO) continue;
            Location cl = c.local();
            if (cl == null) continue;
            boolean alguem = false;
            for (Player p : vivos) if (p.getLocation().distanceSquared(cl) < 28 * 28) alguem = true;
            if (alguem) apagarPerto(cl, 7, 1 + rnd.nextInt(3), 2);
        }
    }

    /** Apaga (por "ticks" ciclos) ate "quantas" lampadas perto de "l" (todas se quantas <= 0). */
    void apagarPerto(Location l, int r, long ciclos) {
        apagarPerto(l, r, ciclos, 0);
    }

    private void apagarPerto(Location l, int r, long ciclos, int quantas) {
        Nivel nv = atual();
        int ly = nv.yLampada();
        Material acesa = nv.acesa.getMaterial();
        int feitas = 0;
        int tentativas = quantas > 0 ? quantas * 6 : (2 * r + 1) * (2 * r + 1);
        for (int k = 0; k < tentativas; k++) {
            int x, z;
            if (quantas > 0) {
                x = l.getBlockX() + rnd.nextInt(2 * r + 1) - r;
                z = l.getBlockZ() + rnd.nextInt(2 * r + 1) - r;
            } else {
                x = l.getBlockX() - r + k % (2 * r + 1);
                z = l.getBlockZ() - r + k / (2 * r + 1);
            }
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            if (apagadas.containsKey(key) || !nv.contem(x, z)) continue;
            Block b = mundo.getBlockAt(x, ly, z);
            if (b.getType() != acesa) continue;
            b.setBlockData(nv.apagada, false);
            apagadas.put(key, new int[] {x, ly, z, (int) ciclos});
            if (rnd.nextInt(3) == 0) Sons.local(mundo, b.getLocation(), 20, "lampada", 0.7f, 0.8f + rnd.nextFloat() * 0.3f, Sound.BLOCK_TRIPWIRE_CLICK_OFF, 0.5f, 0.5f);
            if (quantas > 0 && ++feitas >= quantas) return;
        }
    }

    // ================================================================== lanterna (Nivel 6)

    ItemStack lanternaItem() {
        ItemStack it = new ItemStack(Material.LANTERN);
        ItemMeta m = it.getItemMeta();
        m.displayName(Backrooms.txt("§e§lLanterna"));
        m.lore(List.of(Backrooms.txt("§7Ilumina para onde você olha."), Backrooms.txt("§7A pilha acaba: ache §fpilhas §7para recarregar.")));
        m.getPersistentDataContainer().set(pl.kItem, PersistentDataType.STRING, "lanterna");
        it.setItemMeta(m);
        return it;
    }

    boolean ehLanterna(ItemStack it) {
        return it != null && it.hasItemMeta() && "lanterna".equals(it.getItemMeta().getPersistentDataContainer().get(pl.kItem, PersistentDataType.STRING));
    }

    private void darLanterna(Player p) {
        if (ehLanterna(p.getInventory().getItemInOffHand())) return;
        p.getInventory().setItemInOffHand(lanternaItem());
    }

    private void recolherLanterna(Player p) {
        if (ehLanterna(p.getInventory().getItemInOffHand())) p.getInventory().setItemInOffHand(null);
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length; i++) if (ehLanterna(c[i])) p.getInventory().setItem(i, null);
    }

    /** A lanterna desta pessoa esta acesa agora? */
    boolean lanternaLigada(Player p) {
        if (nivel != ESCURO) return false;
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || j.fora || !j.jogando || j.bateria <= 0 || tick < j.falhaAte) return false;
        // com a sanidade baixa (ou a pilha no fim) ela pisca sozinha
        if ((j.sanidade < 25 || j.bateria < 10) && (ticks / 3) % 9 == 0) return false;
        return p.getGameMode() != GameMode.SPECTATOR;
    }

    void falharLanterna(Player p, long ciclos) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j != null) j.falhaAte = Math.max(j.falhaAte, tick + ciclos);
    }

    /** Gasta a pilha (4x por segundo). */
    private void pilha(Player p, Jogador j) {
        if (j.bateria <= 0) return;
        double antes = j.bateria;
        j.bateria = Math.max(0, j.bateria - dif("pilha-por-segundo", 0.45) * 0.25);
        if (antes > 20 && j.bateria <= 20) {
            p.sendActionBar(Backrooms.txt("§cA pilha da lanterna está acabando! §7Ache uma pilha."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 0.5f);
        }
        if (j.bateria <= 0) {
            titulo(p, "", "§c§lSUA LANTERNA APAGOU", 2);
            p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_OFF, 1f, 0.4f);
        }
    }

    // ================================================================== ser pego

    /** "a Coisa" -> "pela Coisa", "o Uivador" -> "pelo Uivador". */
    static String porQuem(String quem) {
        if (quem.startsWith("a ")) return "pela " + quem.substring(2);
        if (quem.startsWith("o ")) return "pelo " + quem.substring(2);
        return "por " + quem;
    }

    static String maiuscula(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    void pego(Player p, String quem) {
        pego(p, quem, null);
    }

    /** skin = quem pegou (aparece na cara da pessoa por meio segundo antes de tudo ficar preto). */
    void pego(Player p, String quem, String skin) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !alvoValido(p)) return;
        j.vidas--;
        j.graca = tick + seg(6);
        if (skin != null) {
            caraACara(p, skin, 0.95);
            // celular: o rosto fica um pouco mais na tela e depois tudo apaga de verdade (a cegueira no Bedrock e fraca);
            // a pessoa volta para o inicio com a tela ainda preta
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, false, false, false));
                Tela.escurecer(p, 0, 0, 0, 0.15f, 0.9f, 0.7f);
            }, Sons.bedrock(p) ? 12L : 9L);
        } else {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, false));
            if (!quem.equals("a loucura")) grito(p, p.getLocation(), "festeiro");   // o Sorridente: o grito dele colado no ouvido
            Tela.escurecer(p, quem.equals("a loucura") ? 45 : 0, 0, 0, 0.1f, 1.0f, 0.7f);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, false, false, false));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        Sons.para(p, p.getLocation(), "pego", 1f, 1f, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.4f);
        boolean loucura = quem.equals("a loucura");
        pl.getLogger().info("Backrooms: " + p.getName() + " foi pego " + porQuem(quem) + " no " + atual().nome + " (vidas " + j.vidas + ")");
        if (j.vidas <= 0) {
            j.fora = true;
            p.setGameMode(GameMode.SPECTATOR);
            titulo(p, "§4§lVOCÊ SE PERDEU", loucura ? "§7Sua mente não aguentou..." : "§7" + maiuscula(quem) + " te pegou.", 4);
            avisar("§c" + p.getName() + " §7se perdeu nas Backrooms (" + porQuem(quem) + "). §8Agora assiste os outros.");
            // Moeda de Reviver: uns segundos para voltar (se era o ultimo, a derrota espera)
            int espera = Math.max(8, pl.getConfig().getInt("reviver-segundos", 20));
            if (Reviver.moedas(p) > 0) j.reviverAte = tick + seg(espera + 2);
            Reviver.oferecer(pl, p, "as Backrooms", espera, () -> reviver(p));
            return;
        }
        titulo(p, "§4§lPEGO!", loucura ? "§7Você enlouqueceu... §c" + "❤".repeat(j.vidas) + " §7restante(s)" : "§7" + maiuscula(quem) + " te pegou. §c" + "❤".repeat(j.vidas) + " §7restante(s)", 2.5);
        avisar("§f" + p.getName() + " §7foi pego " + porQuem(quem) + " §8(" + j.vidas + " vida(s))");
        j.sanidade = Math.max(j.sanidade, loucura ? 60 : 50);
        Location volta = nivel == CORRIDA ? voltaCorrida() : atual().inicio(mundo, rnd.nextInt(9));
        if (nivel == CORRIDA) {
            // depois de pegar alguem na corrida ela nao da outro bote logo em seguida (o jogador volta cego e tonto)
            investidaComeca = -1;
            investidaAte = -1;
            proximaInvestida = tick + seg(dif("corrida-investida-segundos", 10));
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && vivo(p)) pl.mover(p, volta);
        }, 25L);
    }

    private boolean alguemPodeVoltar() {
        for (Jogador j : jogadores.values()) {
            if (!j.jogando || !j.fora || j.reviverAte <= tick) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    /** Moeda de Reviver: volta para a fase atual com 1 vida. false = nao da mais (partida acabou, saiu...). */
    boolean reviver(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || estado != Estado.JOGANDO || !j.jogando || !j.fora || !p.isOnline()) return false;
        j.fora = false;
        j.reviverAte = 0;
        j.vidas = 1;
        j.sanidade = Math.max(j.sanidade, 60);
        j.graca = tick + seg(8);
        j.cacado = false;
        p.setGameMode(GameMode.ADVENTURE);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.NAUSEA);
        if (nivel == ESCURO) darLanterna(p);
        pl.mover(p, nivel == CORRIDA ? voltaCorrida() : atual().inicio(mundo, rnd.nextInt(9)));
        titulo(p, "§a§lVOCÊ VOLTOU!", "§7A Moeda de Reviver te trouxe de volta. §c❤ §71 vida", 3);
        avisar("§e" + p.getName() + " §7usou uma §eMoeda de Reviver §7e voltou para as Backrooms!");
        pl.getLogger().info("Backrooms: " + p.getName() + " voltou com Moeda de Reviver no " + atual().nome);
        return true;
    }

    // ================================================================== nivel ! (corrida)

    private void prepararCorrida(Nivel nv) {
        corridaLigada = false;
        corridaComeca = tick + seg(5);
        investidaComeca = -1;
        investidaAte = -1;
        corridaX = nv.x0 + 2.5;
        coisaCorrida = mundo.spawn(new Location(mundo, corridaX, Nivel.Y, nv.z0 + 3.5, -90, 0), Mannequin.class, w -> {
            w.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
            w.setProfile(Skins.perfil("corredor"));
            w.setDescription(null);
            w.setImmovable(true);
            w.setCanPickupItems(false);
            AttributeInstance sc = w.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(1.6);
            w.setGravity(false);
            w.setSilent(true);
            w.setInvulnerable(true);
            w.setCollidable(false);
            w.setPersistent(false);
            w.setRemoveWhenFarAway(false);
        });
        Visual.gemeo(coisaCorrida, "corredor");
    }

    private void corrida(List<Player> vivos) {
        Nivel nv = atual();
        if (!corridaLigada) {
            long falta = corridaComeca - tick;
            if (falta % 4 == 0 && falta > 0)
                for (Player p : vivos) {
                    titulo(p, "§c§l" + (falta / 4), "§7algo acordou atrás de você...", 0.8);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.5f);
                }
            if (tick >= corridaComeca) {
                corridaLigada = true;
                for (Player p : online(true)) p.setWalkSpeed(0.2f);
                proximaInvestida = tick + seg(dif("corrida-investida-segundos", 10));
                proximoEscuro = tick + seg(dif("corrida-escuridao", 0));
                Sons.local(mundo, new Location(mundo, corridaX, Nivel.Y, nv.z0 + 3.5), 300, "corrida_rugido", 3f, 0.9f,
                        Sound.ENTITY_WARDEN_ROAR, 3f, 0.8f);
                for (Player p : vivos) {
                    titulo(p, "§4§lCORRA!", "", 1.5);
                    Sons.para(p, p.getLocation(), "corrida_alarme", 0.8f, 1f, Sound.ENTITY_WARDEN_ROAR, 1f, 0.8f);
                }
            }
            return;
        }
        for (Player p : vivos) {
            if (p.getX() >= nv.saidaX - 2) {
                avisar("§a§l" + p.getName() + " chegou ao fim do corredor!");
                vitoria(p);
                return;
            }
            if (alvoValido(p) && p.getX() < corridaX + 1.0) pego(p, "a Coisa", "corredor");
            if (estado != Estado.JOGANDO) return;
        }
        // de tempos em tempos ela da um bote: ruge e acelera por 2 segundos
        if (investidaComeca < 0 && tick >= proximaInvestida) {
            investidaComeca = tick + seg(1.5);
            investidaAte = investidaComeca + seg(2);
            Location c = new Location(mundo, corridaX, Nivel.Y, nv.z0 + 3.5);
            Sons.local(mundo, c, 300, "corrida_rugido", 3f, 1.1f, Sound.ENTITY_WARDEN_ROAR, 3f, 1.1f);
            for (Player p : vivos) titulo(p, "", "§4§lELA ESTÁ ACELERANDO!", 1.2);
        }
        if (investidaComeca >= 0 && tick >= investidaAte) {
            investidaComeca = -1;
            double base = dif("corrida-investida-segundos", 10);
            proximaInvestida = tick + seg(base * (0.7 + rnd.nextDouble() * 0.6));
        }
        // apagao: a escuridao fecha a visao por alguns segundos
        double esc = dif("corrida-escuridao", 0);
        if (esc > 0 && tick >= proximoEscuro) {
            proximoEscuro = tick + seg(esc * (0.8 + rnd.nextDouble() * 0.4));
            for (Player p : vivos) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, 0, false, false, false));
        }
        if (tick % 6 == 0) mundo.playSound(new Location(mundo, corridaX, Nivel.Y, nv.z0 + 3.5), Sound.ENTITY_WARDEN_STEP, 2.5f, 0.6f);
        if (tick % 20 == 0) {
            Location c = new Location(mundo, corridaX, Nivel.Y, nv.z0 + 3.5);
            for (Player p : mundo.getPlayers()) {
                if (p.getLocation().distanceSquared(c) > 300 * 300) continue;
                if (Sons.temPacote(p)) {
                    if (tick % 40 == 0) Sons.para(p, c, "corrida_rugido", 2f, 0.8f + rnd.nextFloat() * 0.2f, null, 0f, 0f);
                }
                else p.playSound(c, Sound.ENTITY_WARDEN_ANGRY, 2.5f, 0.7f);
            }
        }
        // o alarme vermelho do corredor toca sem parar (2s, em loop)
        if (tick % 8 == 0) for (Player p : vivos) if (Sons.temPacote(p)) Sons.para(p, p.getLocation(), "corrida_alarme", 0.3f, 1f, null, 0f, 0f);
    }

    private void moverCorrida() {
        if (!corridaLigada || coisaCorrida == null || !coisaCorrida.isValid()) return;
        Nivel nv = atual();
        boolean bote = investidaComeca >= 0 && tick >= investidaComeca && tick < investidaAte;
        corridaX += dif("corrida-velocidade", 5.6) * (bote ? dif("corrida-investida-forca", 1.4) : 1.0) / 20.0;
        // nunca fica longe demais de quem esta mais atras
        double atras = 1e9;
        for (Player p : vivos()) if (p.getGameMode() != GameMode.SPECTATOR) atras = Math.min(atras, p.getX());
        double dist = dif("corrida-distancia", 20);
        if (atras < 1e8 && atras - corridaX > dist) corridaX = atras - dist;
        corridaX = Math.min(corridaX, nv.saidaX - 4);
        coisaCorrida.teleport(new Location(mundo, corridaX, Nivel.Y, nv.z0 + 3.5, -90, 0));
        coisaCorrida.setBodyYaw(-90f);
    }

    private Location voltaCorrida() {
        Nivel nv = atual();
        if (!(nv instanceof Corredor c)) return nv.inicio(mundo, 0);
        double x = c.pontoDepois(corridaX + 22);
        if (x < 0) x = corridaX + 26;
        x = Math.max(x, nv.inicioX);
        int rx = (int) Math.floor(x - nv.x0);
        while (rx < Corredor.L - 12 && c.obst[rx] != 0) rx++;
        return new Location(mundo, nv.x0 + rx + 0.5, Nivel.Y, nv.z0 + 3.5, -90, 0);
    }

    // ================================================================== fim

    private void vitoria(Player quem) {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        long s = (tick - inicioTick) / 4;
        String tempo = String.format("%d:%02d", s / 60, s % 60);
        List<String> nomes = new ArrayList<>();
        for (Jogador j : jogadores.values()) if (j.jogando && !j.fora) nomes.add(j.nome);
        for (Player p : online(true)) {
            Jogador j = jogadores.get(p.getUniqueId());
            boolean saiu = j != null && !j.fora;
            titulo(p, saiu ? "§a§lVOCÊ ESCAPOU!" : "§e§lELES ESCAPARAM", "§7das Backrooms em §f" + tempo + " §7(" + nomeModo() + "§7)", 5);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        pl.registrarVitoria(nomes, s, modo, online(true));
        fimEm = tick + seg(8);
    }

    private void derrota() {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        for (Player p : online(true)) {
            titulo(p, "§4§lFIM", "§7Ninguém escapou das Backrooms...", 4);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_DEATH, 0.8f, 0.6f);
        }
        avisar("§cNinguém escapou desta vez. §7(chegaram até o §f" + atual().nome + "§7, " + fase() + ")");
        pl.getLogger().info("Backrooms: derrota no " + atual().nome + " (" + modo + ")");
        fimEm = tick + seg(6);
    }

    void finalizar() {
        limparNivel();
        for (Jogador j : new ArrayList<>(jogadores.values())) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            jogadores.remove(j.id);
            if (p != null && p.isOnline()) {
                pl.devolver(p);
                p.sendMessage(Backrooms.PREFIXO + "§7Partida encerrada. Jogar de novo: §e/backrooms entrar");
            }
        }
        fimEm = -1;
        trocaEm = -1;
        estado = Estado.ESPERANDO;
    }

    void parar() {
        if (estado == Estado.JOGANDO || estado == Estado.FIM) finalizar();
        else estado = Estado.ESPERANDO;
    }

    void limparNivel() {
        lanterna.apagar(mundo);
        for (Susto x : sustos) if (x.m != null) x.m.remove();
        sustos.clear();
        proximoApagao = -1;
        fimApagao = -1;
        ondeApagou = null;
        for (Coletavel c : coletaveis) if (c.ent != null) c.ent.remove();
        coletaveis.clear();
        for (Cacador c : cacadores) c.remover();
        cacadores.clear();
        for (Sorridente s : sorridentes) s.remover();
        sorridentes.clear();
        for (TextDisplay t : placas) t.remove();
        placas.clear();
        if (coisaCorrida != null) coisaCorrida.remove();
        coisaCorrida = null;
        corridaLigada = false;
        if (festeiro != null) festeiro.remove();
        festeiro = null;
        proximoFesteiro = 0;
        if (nivel >= 0 && nivel < mapa.niveis.size()) {
            Nivel nv = atual();
            for (int[] b : apagadas.values()) mundo.getBlockAt(b[0], b[1], b[2]).setBlockData(nv.acesa, false);
            if (nivel == ESCURO) for (Player p : online(true)) recolherLanterna(p);
            fecharPorta(nv);
        }
        apagadas.clear();
    }

    /** Tira entidades que sobraram (servidor caiu no meio de uma partida etc.). */
    void limparEntidades() {
        for (Entity e : mundo.getEntities()) {
            if (e instanceof Player) continue;
            if (e.getPersistentDataContainer().has(pl.kEntidade, PersistentDataType.BYTE)) e.remove();
        }
    }

    // ================================================================== lugares

    private Location loc(int[] c) {
        return new Location(mundo, c[0] + 0.5, Nivel.Y, c[1] + 0.5);
    }

    private Location lugarDistante(Nivel nv, double x, double z, double min) {
        List<int[]> l = Mapa.embaralhado(nv.lugares, rnd);
        for (int[] c : l) if (Math.hypot(c[0] - x, c[1] - z) >= min && Math.hypot(c[0] - x, c[1] - z) < min + 40) return loc(c);
        return l.isEmpty() ? null : loc(l.get(0));
    }

    /** Lugar entre "min" e "max" blocos de alguem, e longe de todos. */
    Location lugarLonge(Nivel nv, List<Player> vivos, double min, double max) {
        return longeDe(lugares.get(nv.id), nv, vivos, min, max);
    }

    Location escuroLonge(Nivel nv, List<Player> vivos, double min, double max) {
        return longeDe(escuros.get(nv.id), nv, vivos, min, max);
    }

    private Location longeDe(Indice idx, Nivel nv, List<Player> vivos, double min, double max) {
        if (idx == null || idx.todos.isEmpty()) return null;
        List<Player> ordem = new ArrayList<>(vivos);
        Collections.shuffle(ordem, rnd);
        for (Player p : ordem) {
            if (!nv.contem(p.getX(), p.getZ())) continue;
            List<int[]> c = idx.perto(p.getX(), p.getZ(), min, max);
            Collections.shuffle(c, rnd);
            for (int[] x : c) {
                boolean ok = true;
                for (Player o : vivos) if (Math.hypot(o.getX() - x[0], o.getZ() - x[1]) < min) ok = false;
                if (ok) return loc(x);
            }
        }
        for (int k = 0; k < 40; k++) {
            int[] x = idx.todos.get(rnd.nextInt(idx.todos.size()));
            boolean ok = true;
            for (Player o : vivos) if (Math.hypot(o.getX() - x[0], o.getZ() - x[1]) < min) ok = false;
            if (ok) return loc(x);
        }
        return null;
    }

    Location lugarPerto(Nivel nv, Location de, double min, double max) {
        List<int[]> c = lugares.get(nv.id).perto(de.getX(), de.getZ(), min, max);
        return c.isEmpty() ? null : loc(c.get(rnd.nextInt(c.size())));
    }

    /** Um lugar a uns 10-26 blocos de "de" que fique mais perto de "para". */
    Location passoNaDirecao(Nivel nv, Location de, Location para) {
        List<int[]> c = lugares.get(nv.id).perto(de.getX(), de.getZ(), 10, 26);
        if (c.isEmpty()) return null;
        int[] melhor = null;
        double md = 1e18;
        for (int k = 0; k < 8; k++) {
            int[] x = c.get(rnd.nextInt(c.size()));
            double d = Math.pow(x[0] - para.getX(), 2) + Math.pow(x[1] - para.getZ(), 2);
            if (d < md) {
                md = d;
                melhor = x;
            }
        }
        return melhor == null ? null : loc(melhor);
    }

    // ================================================================== admin

    String status() {
        StringBuilder js = new StringBuilder();
        for (Jogador j : jogadores.values())
            if (j.jogando) js.append(j.nome).append("(v=").append(j.vidas).append(",s=").append((int) j.sanidade).append(j.fora ? ",fora" : "").append(") ");
        StringBuilder cs = new StringBuilder();
        for (Cacador c : cacadores) cs.append(c.status()).append(' ');
        for (Sorridente s : sorridentes) cs.append(s.status()).append(' ');
        int naoPegos = 0;
        for (Coletavel c : coletaveis) if (!c.pego && c.chave) naoPegos++;
        String bus = "";
        for (Player p : vivos()) {
            Location t = p.getCompassTarget();
            bus = String.format(" bussola=%.0f,%.0f", t.getX(), t.getZ());
            break;
        }
        return "modo=" + modo + bus + " estado=" + estado + " ordem=" + ordem + " etapa=" + etapa + " nivel=" + nivel + " itens=" + pegos + "/" + necessarios + " (no mapa " + naoPegos + ") saida=" + saidaAberta
                + (nivel == CORRIDA ? String.format(" corridaX=%.1f ligada=%s", corridaX, corridaLigada) : "")
                + " criaturas=[" + cs.toString().trim() + "] jogadores=[" + js.toString().trim() + "]";
    }

    String itensTeste(Player p) {
        StringBuilder s = new StringBuilder();
        for (Coletavel c : coletaveis)
            if (!c.pego && c.chave) s.append(String.format("(%.0f %.0f d=%.0f) ", c.l.getX(), c.l.getZ(), p == null ? 0 : c.l.distance(p.getLocation())));
        return s.toString();
    }

    Location itemMaisPerto(Player p) {
        Location m = null;
        double md = 1e18;
        for (Coletavel c : coletaveis) {
            if (c.pego || !c.chave) continue;
            double d = c.l.distanceSquared(p.getLocation());
            if (d < md) {
                md = d;
                m = c.l;
            }
        }
        return m;
    }

    void sanidadeTeste(Player p, double v) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j != null) j.sanidade = v;
    }
}
