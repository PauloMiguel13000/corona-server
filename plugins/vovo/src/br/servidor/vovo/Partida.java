package br.servidor.vovo;

import io.papermc.paper.entity.TeleportFlag;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Uma partida na casa da Vovo. O ciclo roda a cada 5 ticks (4 vezes por segundo). */
final class Partida {

    enum Estado { ESPERANDO, CONTAGEM, JOGANDO, FIM }

    enum Ia { DORMINDO, RONDA, INVESTIGANDO, CACANDO, PEGANDO, ATORDOADA }

    static final class Jogador {
        final UUID id;
        final String nome;
        boolean jogando;
        int dias;
        boolean fora;
        long graca;
        boolean escondido;
        boolean vistoAoEsconder;
        long ultimoRangido;
        long ultimoCorrer;
        long reviverAte = -1;
        long barulhoAte;

        Jogador(Player p) {
            this.id = p.getUniqueId();
            this.nome = p.getName();
        }
    }

    private final Vovo pl;
    final World mundo;
    final Casa casa;
    final Map<UUID, Jogador> jogadores = new LinkedHashMap<>();
    private final Random rnd = new Random();

    Estado estado = Estado.ESPERANDO;
    private long tick;
    private long inicioTick;
    private int contagem;
    private long fimPendente = -1;

    // ---------------------------------------------------------------- estado da casa
    boolean cadeado, corrente, tabuas, energia, porao, garagem, bateria, gasolina, chaveCarro, carroLigado, portaAberta, tampa, grade, tampaRua;
    private final Map<Long, Material> rangidos = new HashMap<>();
    private final Set<Long> armadilhas = new HashSet<>();
    private final Map<UUID, long[]> despertadores = new HashMap<>();
    private final Map<String, UUID> textos = new HashMap<>();
    private final Map<Missao, UUID> quadrosTranca = new EnumMap<>(Missao.class);
    private long bestaVoltaEm = -1;
    private boolean ticketsAtivos;

    // ---------------------------------------------------------------- a vovo
    private Zombie motor;
    private Mannequin corpo;
    private TextDisplay zzz;
    private Ia ia = Ia.DORMINDO;
    private UUID alvo;
    private long vistoEm;
    private Location ultimoLocal;
    private Location destino;
    private long esperaAte;
    private long ateQuando;
    private long proximaVoz;
    private long proximaVisita;
    private double bonus;
    private Location ultimaPos;
    private int parado;
    private Player olhando;
    private final Deque<Location> rota = new ArrayDeque<>();
    private Location alvoMov;
    /** Fim da passagem (escada/porta) que ela esta atravessando agora: nao recalcula o caminho ate chegar la. */
    private Location transito;
    private double ritmo = 1;
    private long evitarQuartoAte;
    private Location localPreso;
    private int tempoPreso;

    final Aranha aranha;
    /** Dificuldade escolhida: "normal" ou "dificil". So muda fora da partida. */
    String modo;

    Partida(Vovo pl, World mundo, Casa casa) {
        this.pl = pl;
        this.mundo = mundo;
        this.casa = casa;
        this.aranha = new Aranha(pl, this, mundo);
        this.modo = modoValido(pl.getConfig().getString("modo-padrao", "normal"));
    }

    static String modoValido(String m) {
        if (m == null) return "normal";
        m = m.toLowerCase(java.util.Locale.ROOT).replace("í", "i");
        return m.startsWith("dif") || m.equals("hard") ? "dificil" : "normal";
    }

    static String nomeModo(String m) {
        return "dificil".equals(m) ? "§c§lDifícil" : "§a§lNormal";
    }

    String nomeModo() {
        return nomeModo(modo);
    }

    /** Valor da dificuldade atual (modos.<modo>.<chave> na config). */
    double dif(String chave, double padrao) {
        // get() sem valor padrao tambem olha a config de dentro do jar (vale para configs antigas sem a chave)
        Object v = pl.getConfig().get("modos." + modo + "." + chave);
        return v instanceof Number n ? n.doubleValue() : padrao;
    }

    /** Troca a dificuldade. Retorna o motivo se nao puder. */
    String trocarModo(Player quem, String novo) {
        novo = modoValido(novo);
        if (estado == Estado.JOGANDO || estado == Estado.FIM) return "§cA partida já começou. Escolha a dificuldade antes da próxima.";
        if (quem != null && !participa(quem) && !naFila().isEmpty()) return "§cEntre na fila (§f/vovo entrar§c) para escolher a dificuldade.";
        if (novo.equals(modo)) return null;
        modo = novo;
        avisar("§f" + (quem == null ? "Um admin" : quem.getName()) + " §7mudou a dificuldade para " + nomeModo() + "§7.");
        return null;
    }

    private static long seg(double s) {
        return Math.round(s * 4);
    }

    int diasTotal() {
        return (int) Math.max(1, Math.min(10, dif("dias", pl.getConfig().getInt("dias", 5))));
    }

    // ================================================================== entrada e saida

    Jogador adicionar(Player p) {
        Jogador j = new Jogador(p);
        jogadores.put(p.getUniqueId(), j);
        return j;
    }

    boolean remover(Player p) {
        Jogador j = jogadores.remove(p.getUniqueId());
        if (j == null) return false;
        if (estado == Estado.JOGANDO && j.jogando && !j.fora) {
            soltarNoChao(p, false);
            avisar("§7" + j.nome + " §7desistiu e saiu da casa.");
        }
        p.setWalkSpeed(0.2f);
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

    List<Player> online(boolean soQuemJoga) {
        List<Player> l = new ArrayList<>();
        for (Jogador j : jogadores.values()) {
            if (soQuemJoga && !j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) l.add(p);
        }
        return l;
    }

    private List<Player> naFila() {
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
        for (Player p : online(false)) p.sendMessage(Vovo.PREFIXO + msg);
    }

    void titulo(Player p, String t, String s, double fica) {
        p.showTitle(Title.title(Vovo.txt(t), Vovo.txt(s),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis((long) (fica * 1000)), Duration.ofMillis(600))));
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
                    avisar("§eA partida começa em §f" + (contagem / 4) + "s§e. Chame os amigos: §f/vovo entrar");
                }
                hudFila();
            }
            case CONTAGEM -> contar();
            case JOGANDO -> jogar();
            case FIM -> { }
        }
    }

    private void contar() {
        List<Player> fila = naFila();
        if (fila.isEmpty() || fila.size() < pl.getConfig().getInt("min-jogadores", 1)) {
            estado = Estado.ESPERANDO;
            avisar("§7Contagem cancelada: faltam jogadores.");
            return;
        }
        if (fila.size() >= pl.getConfig().getInt("max-jogadores", 6)) contagem = (int) Math.min(contagem, seg(8));
        contagem--;
        if (contagem % 4 == 0) {
            int s = contagem / 4;
            for (Player p : fila) {
                p.sendActionBar(Vovo.txt("§7A partida começa em §e" + s + "s §8| §7jogadores: §f" + fila.size()));
                if (s <= 5 && s > 0) {
                    titulo(p, "§5" + s, "§7prepare-se...", 0.9);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f);
                }
            }
        }
        if (contagem <= 0) iniciar();
    }

    private void hudFila() {
        if (tick % 4 != 0) return;
        for (Player p : naFila()) {
            String s = estado == Estado.JOGANDO ? "§7Partida em andamento §8| §7você entra na próxima"
                    : "§7Esperando jogadores... §8| §7na fila: §f" + naFila().size();
            p.sendActionBar(Vovo.txt(s));
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
        fimPendente = -1;
        bonus = 0;
        prepararCasa();
        criarVovo(Casa.quartoVovo(mundo));
        ia = Ia.DORMINDO;
        if (aranha.ativa()) aranha.criar();
        ateQuando = tick + seg(dif("dorme-segundos", pl.getConfig().getInt("vovo.dorme-segundos", 20)));
        proximaVisita = tick + seg(dif("primeira-visita", 70));
        int i = 0;
        for (Player p : fila) {
            Jogador j = jogadores.get(p.getUniqueId());
            j.jogando = true;
            j.dias = diasTotal();
            j.fora = false;
            j.graca = tick + seg(4);
            j.escondido = false;
            Guarda.limpar(p);
            p.setGameMode(GameMode.ADVENTURE);
            pl.mover(p, Casa.inicio(mundo, i++));
            titulo(p, "§5§lCASA DA VOVÓ", "§7Modo " + nomeModo() + " §8| §fDia 1 §7— fuja em " + diasTotal() + " dias", 4);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.6f, 0.5f);
            explicar(p);
        }
        avisar("§dA partida começou com §f" + fila.size() + " §djogador(es) no modo " + nomeModo() + "§d. A vovó está dormindo... por enquanto.");
    }

    private void explicar(Player p) {
        p.sendMessage(Vovo.PREFIXO + "§fVocê acordou trancado na casa da vovó. §7Fuja pela §fporta da frente§7, de §fcarro §7ou pelo §fesgoto§7.");
        p.sendMessage(Vovo.PREFIXO + "§7Procure §fitens§7 em cima de mesas, barris e prateleiras. Clique para pegar §8(um por vez)§7.");
        p.sendMessage(Vovo.PREFIXO + "§7Clique na porta, no carro, na caixa de força, na tampa do bueiro ou na grade com o item certo na mão.");
        p.sendMessage(Vovo.PREFIXO + "§7Ela §fouve tudo§7: tábuas soltas rangem, correr e largar coisas faz barulho. §fAgache §7para andar quieto.");
        p.sendMessage(Vovo.PREFIXO + "§7Entre num §farmário§7 para se esconder. Se ela te pegar, você acorda no quarto no dia seguinte.");
    }

    /** Quem chega com a partida rolando acorda direto no quarto. */
    void entrarNoMeio(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || estado != Estado.JOGANDO) return;
        j.jogando = true;
        j.dias = diasTotal();
        j.fora = false;
        j.graca = tick + seg(8);
        fimPendente = -1;
        Guarda.limpar(p);
        p.setGameMode(GameMode.ADVENTURE);
        pl.mover(p, Casa.inicio(mundo, rnd.nextInt(8)));
        titulo(p, "§5§lCASA DA VOVÓ", progresso(), 4);
        explicar(p);
        avisar("§f" + p.getName() + " §7acordou no quarto de cima.");
    }

    private void vitoria(String como, Player quem) {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        long s = (tick - inicioTick) / 4;
        String tempo = String.format("%d:%02d", s / 60, s % 60);
        removerVovo();
        aranha.remover();
        List<String> nomes = new ArrayList<>();
        for (Player p : online(true)) {
            Jogador j = jogadores.get(p.getUniqueId());
            titulo(p, "§a§lVOCÊS FUGIRAM!", "§7" + como + " em §f" + tempo, 5);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            if (j != null) nomes.add(p.getName());
        }
        fogos(como.contains("carro") ? Casa.fogosCarro(mundo) : como.contains("esgoto") ? Casa.fogosEsgoto(mundo) : Casa.fogosPorta(mundo));
        pl.registrarVitoria(nomes, s, como, quem == null ? "" : quem.getName(), online(true), modo);
        Bukkit.getScheduler().runTaskLater(pl, this::finalizar, 160L);
    }

    private void derrota(String motivo) {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        if (corpo != null && motor != null) {
            mundo.playSound(motor.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 2f, 0.6f);
        }
        for (Player p : online(true)) {
            titulo(p, "§4§lA VOVÓ VENCEU", "§7" + motivo, 4);
            p.playSound(p.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.5f);
        }
        avisar("§c" + motivo + " " + progresso());
        Bukkit.getScheduler().runTaskLater(pl, this::finalizar, 120L);
    }

    private void fogos(Location l) {
        for (int i = 0; i < 3; i++) {
            Firework fw = mundo.spawn(l.clone().add(rnd.nextDouble() * 4 - 2, 1, rnd.nextDouble() * 2), Firework.class, f -> {
                FireworkMeta m = f.getFireworkMeta();
                m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.PURPLE, Color.WHITE).withFade(Color.BLACK).build());
                m.setPower(1);
                f.setFireworkMeta(m);
            });
            fw.setPersistent(false);
        }
    }

    /** Fim da partida: devolve quem jogou; quem esperava no lobby fica para a proxima. */
    void finalizar() {
        removerVovo();
        aranha.remover();
        limparEntidades();
        restaurarCasa();
        tickets(false);
        for (Jogador j : new ArrayList<>(jogadores.values())) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            jogadores.remove(j.id);
            if (p != null && p.isOnline()) {
                p.setWalkSpeed(0.2f);
                pl.devolver(p);
                p.sendMessage(Vovo.PREFIXO + "§7Partida encerrada. Jogar de novo: §e/vovo entrar");
            }
        }
        fimPendente = -1;
        estado = Estado.ESPERANDO;
    }

    void parar() {
        if (estado == Estado.JOGANDO || estado == Estado.FIM) finalizar();
        else estado = Estado.ESPERANDO;
    }

    // ================================================================== preparar a casa

    private static long chave(int x, int y, int z) {
        return ((long) (x + 1000) << 40) | ((long) (y + 1000) << 20) | (z + 1000);
    }

    private static long chave(Block b) {
        return chave(b.getX(), b.getY(), b.getZ());
    }

    private void tickets(boolean ligar) {
        if (ligar == ticketsAtivos) return;
        ticketsAtivos = ligar;
        for (int cx = -1; cx <= 5; cx++)
            for (int cz = -1; cz <= 6; cz++) {
                if (ligar) mundo.addPluginChunkTicket(cx, cz, pl);
                else mundo.removePluginChunkTicket(cx, cz, pl);
            }
    }

    /** Tudo de volta como a casa foi construida (portas fechadas, sem itens, pisos e lampadas normais). */
    void restaurarCasa() {
        Casa.Obra o = new Casa.Obra(mundo);
        for (Map.Entry<Long, Material> e : rangidos.entrySet()) {
            long k = e.getKey();
            int x = (int) ((k >> 40) & 0xFFFFF) - 1000, y = (int) ((k >> 20) & 0xFFFFF) - 1000, z = (int) (k & 0xFFFFF) - 1000;
            o.b(x, y, z, e.getValue());
        }
        rangidos.clear();
        armadilhas.clear();
        // sobras de uma partida interrompida (servidor caiu no meio)
        for (int x = Casa.IX1; x <= Casa.IX2; x++)
            for (int z = Casa.IZ1; z <= Casa.IZ2; z++) {
                if (mundo.getBlockAt(x, Casa.PISO_TERREO, z).getType() == Material.STRIPPED_SPRUCE_WOOD) o.b(x, Casa.PISO_TERREO, z, Material.SPRUCE_PLANKS);
                if (mundo.getBlockAt(x, Casa.PISO_ANDAR, z).getType() == Material.STRIPPED_DARK_OAK_WOOD) o.b(x, Casa.PISO_ANDAR, z, Material.DARK_OAK_PLANKS);
                for (int y : new int[] {Casa.PISO_TERREO + 1, Casa.PISO_ANDAR + 1})
                    if (mundo.getBlockAt(x, y, z).getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) o.b(x, y, z, Material.AIR);
            }
        porta(Casa.PORTA_FRENTE, false);
        porta(Casa.PORTA_FRENTE2, false);
        porta(Casa.PORTA_PORAO, false);
        porta(Casa.PORTA_PORAO2, false);
        porta(Casa.PORTA_GARAGEM, false);
        porta(Casa.PORTA_GARAGEM2, false);
        casa.apagarLampadas(o);
        casa.portaoFechado(o);
        casa.carro(o, false);
        casa.tampaFechada(o);
        casa.tampaRuaFechada(o);
        casa.gradeFechada(o);
        casa.luzesInvisiveis(o, pl.getConfig().getInt("claridade", 9), pl.getConfig().getInt("claridade-esgoto", 7));
        cadeado = corrente = tabuas = energia = porao = garagem = bateria = gasolina = chaveCarro = carroLigado = portaAberta = false;
        tampa = grade = tampaRua = false;
        despertadores.clear();
        bestaVoltaEm = -1;
    }

    private void prepararCasa() {
        removerVovo();
        aranha.remover();
        limparEntidades();
        restaurarCasa();
        tickets(true);
        // tabuas que rangem e armadilhas
        List<int[]> pisos = casa.pisosLivres(mundo);
        Collections.shuffle(pisos, rnd);
        List<int[]> vaos = Casa.vaos();
        int nRang = (int) dif("tabuas-que-rangem", pl.getConfig().getInt("tabuas-que-rangem", 30)), nArm = (int) dif("armadilhas", pl.getConfig().getInt("armadilhas", 4));
        for (int[] c : pisos) {
            boolean quarto = Casa.noQuartoInicio(c[0], c[1], c[2]);
            if (quarto) continue;
            if (armadilhas.size() < nArm) {
                boolean pertoDeVao = false;
                for (int[] v : vaos) if (v[1] == c[1] && Math.abs(v[0] - c[0]) <= 1 && Math.abs(v[2] - c[2]) <= 1) pertoDeVao = true;
                if (!pertoDeVao) {
                    armadilhas.add(chave(c[0], c[1] + 1, c[2]));
                    mundo.getBlockAt(c[0], c[1] + 1, c[2]).setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, false);
                    continue;
                }
            }
            if (rangidos.size() < nRang) {
                Block b = mundo.getBlockAt(c[0], c[1], c[2]);
                rangidos.put(chave(b), b.getType());
                b.setType(c[1] == Casa.PISO_TERREO ? Material.STRIPPED_SPRUCE_WOOD : Material.STRIPPED_DARK_OAK_WOOD, false);
            }
        }
        espalharItens();
        quadrosTranca();
        atualizarTextos();
    }

    /** Espalha os itens: cada um num comodo diferente, revezando entre andares, porao, garagem e esgoto. */
    private void espalharItens() {
        List<Casa.Ponto> livres = new ArrayList<>(casa.pontos);
        Collections.shuffle(livres, rnd);
        List<Missao> ordem = new ArrayList<>(List.of(Missao.values()));
        Collections.shuffle(ordem, rnd);
        // os mais restritos primeiro
        ordem.sort((a, b) -> Integer.compare(a.zonas.size(), b.zonas.size()));
        Map<String, Integer> porSala = new HashMap<>();
        Map<String, Integer> porRegiao = new HashMap<>();
        for (Missao m : ordem) {
            Casa.Ponto escolhido = null;
            int melhor = Integer.MAX_VALUE;
            for (Casa.Ponto pt : livres) {
                if (!m.zonas.contains(pt.zona())) continue;
                String regiao = pt.zona() + ":" + Casa.andarDe(pt.y());
                int nota = porSala.getOrDefault(pt.sala(), 0) * 100 + porRegiao.getOrDefault(regiao, 0);
                if (nota < melhor) {
                    melhor = nota;
                    escolhido = pt;
                }
            }
            if (escolhido == null) continue;
            livres.remove(escolhido);
            porSala.merge(escolhido.sala(), 1, Integer::sum);
            porRegiao.merge(escolhido.zona() + ":" + Casa.andarDe(escolhido.y()), 1, Integer::sum);
            colocarItem(m, escolhido.x(), escolhido.y(), escolhido.z());
        }
        pl.getLogger().info("Itens da partida espalhados (" + Missao.values().length + ").");
    }

    private ItemFrame colocarItem(Missao m, int x, int y, int z) {
        Location l = new Location(mundo, x, y + 1, z);
        try {
            return mundo.spawn(l, GlowItemFrame.class, f -> {
                f.setFacingDirection(BlockFace.UP, true);
                f.setItem(m.item(pl.kMissao), false);
                f.setVisible(false);
                f.setFixed(true);
                f.setInvulnerable(true);
                f.setPersistent(true);
                f.getPersistentDataContainer().set(pl.kItem, PersistentDataType.STRING, m.id());
            });
        } catch (Throwable t) {
            pl.getLogger().warning("Nao consegui colocar " + m + " em " + x + " " + y + " " + z + ": " + t.getMessage());
            return null;
        }
    }

    private void quadrosTranca() {
        Missao[] ordem = {Missao.CHAVE_ENFERRUJADA, Missao.ALICATE, Missao.PE_DE_CABRA, Missao.FUSIVEL};
        for (int i = 0; i < ordem.length; i++) {
            int[] b = Casa.QUADROS_TRANCA[i];
            Missao m = ordem[i];
            Location l = new Location(mundo, b[0], b[1], b[2] - 1);
            try {
                ItemFrame fr = mundo.spawn(l, ItemFrame.class, f -> {
                    f.setFacingDirection(BlockFace.NORTH, true);
                    ItemStack it = new ItemStack(m.material);
                    ItemMeta meta = it.getItemMeta();
                    meta.displayName(Vovo.txt(m.nome));
                    it.setItemMeta(meta);
                    f.setItem(it, false);
                    f.setVisible(false);
                    f.setFixed(true);
                    f.setInvulnerable(true);
                    f.setPersistent(true);
                    f.getPersistentDataContainer().set(pl.kTranca, PersistentDataType.STRING, m.id());
                });
                quadrosTranca.put(m, fr.getUniqueId());
            } catch (Throwable t) {
                pl.getLogger().warning("Nao consegui por o quadro da tranca " + m + ": " + t.getMessage());
            }
        }
    }

    private void tirarQuadroTranca(Missao m) {
        UUID id = quadrosTranca.remove(m);
        Entity e = id == null ? null : Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    void limparEntidades() {
        for (Entity e : mundo.getEntities()) {
            var pdc = e.getPersistentDataContainer();
            if (pdc.has(pl.kItem, PersistentDataType.STRING) || pdc.has(pl.kTranca, PersistentDataType.STRING)
                    || pdc.has(pl.kTexto, PersistentDataType.BYTE) || pdc.has(pl.kVovo, PersistentDataType.BYTE)) e.remove();
        }
        textos.clear();
        quadrosTranca.clear();
    }

    // ---------------------------------------------------------------- placas de texto

    private void texto(String id, double x, double y, double z, String conteudo) {
        UUID antigo = textos.get(id);
        Entity e = antigo == null ? null : Bukkit.getEntity(antigo);
        if (e instanceof TextDisplay t && t.isValid()) {
            t.text(Vovo.txt(conteudo));
            return;
        }
        TextDisplay t = mundo.spawn(new Location(mundo, x, y, z), TextDisplay.class, d -> {
            d.text(Vovo.txt(conteudo));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setPersistent(false);
            d.setViewRange(0.25f);
            d.getPersistentDataContainer().set(pl.kTexto, PersistentDataType.BYTE, (byte) 1);
        });
        textos.put(id, t.getUniqueId());
    }

    private static String ok(boolean feito) {
        return feito ? "§a✔ §7" : "§c✘ §f";
    }

    private void atualizarTextos() {
        texto("porta", Casa.TXT_PORTA[0], Casa.TXT_PORTA[1], Casa.TXT_PORTA[2], "§c§lPORTA DA FRENTE\n" + ok(cadeado) + "Cadeado §8(Chave Enferrujada)\n" + ok(corrente) + "Corrente §8(Alicate)\n"
                + ok(tabuas) + "Tábuas §8(Pé de Cabra)\n" + ok(energia) + "Fechadura elétrica §8(" + (energia ? "com energia" : "sem energia") + ")");
        texto("porao", Casa.TXT_PORAO[0], Casa.TXT_PORAO[1], Casa.TXT_PORAO[2], porao ? "§aPorão aberto" : "§ePorta do Porão\n§7precisa da §eChave do Porão");
        texto("garagem", Casa.TXT_GARAGEM[0], Casa.TXT_GARAGEM[1], Casa.TXT_GARAGEM[2], garagem ? "§aGaragem aberta" : "§3Porta da Garagem\n§7precisa da §3Chave da Garagem");
        texto("caixa", Casa.TXT_CAIXA[0], Casa.TXT_CAIXA[1], Casa.TXT_CAIXA[2], energia ? "§a⚡ Energia ligada" : "§bCaixa de Força\n§7coloque o §bFusível");
        texto("carro", Casa.TXT_CARRO[0], Casa.TXT_CARRO[1], Casa.TXT_CARRO[2], carroLigado ? "§a§lVRUUUM!" : "§c§lCARRO\n" + ok(bateria) + "Bateria\n" + ok(gasolina) + "Gasolina\n" + ok(chaveCarro)
                + "Chave do Carro" + (pecasCarro() == 3 ? "\n§eClique com a mão vazia para ligar" : ""));
        texto("tampa", Casa.TXT_TAMPA[0], Casa.TXT_TAMPA[1], Casa.TXT_TAMPA[2], tampa ? "§aBueiro aberto\n§7desça para o esgoto" : "§9Tampa do Bueiro\n§7parafusada: precisa da §9Chave Inglesa");
        texto("grade", Casa.TXT_GRADE[0], Casa.TXT_GRADE[1], Casa.TXT_GRADE[2], grade ? "§aGrade cortada\n§fSAÍDA →" : "§8§lGRADE DE FERRO\n§7precisa da §8Serra §7para cortar");
    }

    int etapasEsgoto() {
        return (tampa ? 1 : 0) + (grade ? 1 : 0);
    }

    String progresso() {
        return "§fPorta §e" + trancasAbertas() + "§7/4 §8| §fCarro §e" + pecasCarro() + "§7/3 §8| §fEsgoto §e" + etapasEsgoto() + "§7/2";
    }

    int trancasAbertas() {
        return (cadeado ? 1 : 0) + (corrente ? 1 : 0) + (tabuas ? 1 : 0) + (energia ? 1 : 0);
    }

    int pecasCarro() {
        return (bateria ? 1 : 0) + (gasolina ? 1 : 0) + (chaveCarro ? 1 : 0);
    }

    private void porta(int[] p, boolean aberta) {
        for (int dy = 0; dy <= 1; dy++) {
            Block b = mundo.getBlockAt(p[0], p[1] + dy, p[2]);
            BlockData d = b.getBlockData();
            if (d instanceof Openable op) {
                op.setOpen(aberta);
                b.setBlockData(op, false);
            }
        }
        if (aberta) mundo.playSound(Casa.centro(mundo, p), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.8f);
    }

    // ================================================================== o jogo

    private void jogar() {
        List<Player> vivos = vivos();
        if (vivos.isEmpty()) {
            if (alguemPodeVoltar()) {
                fimPendente = -1;
                return;
            }
            if (fimPendente < 0) fimPendente = tick + seg(2);
            if (tick >= fimPendente) derrota("Ninguém conseguiu fugir.");
            return;
        }
        fimPendente = -1;
        int limite = pl.getConfig().getInt("tempo-limite-minutos", 30);
        if (limite > 0 && tick - inicioTick > seg(limite * 60.0)) {
            derrota("O tempo acabou (" + limite + " minutos).");
            return;
        }
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
            Location l = p.getLocation();
            if (portaAberta && Casa.naVaranda(l)) {
                avisar("§a§l" + p.getName() + " abriu a porta da frente e todo mundo fugiu!");
                vitoria("Fugiram pela porta da frente", p);
                return;
            }
            if (grade && !tampaRua && Casa.naEscadaSaida(l) && l.getY() >= 57) {
                tampaRua = true;
                casa.tampaRuaAberta(new Casa.Obra(mundo));
                mundo.playSound(l, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.2f, 0.7f);
            }
            if (grade && Casa.naSaidaEsgoto(l)) {
                avisar("§a§l" + p.getName() + " saiu pelo esgoto e todo mundo fugiu!");
                vitoria("Fugiram pelo esgoto", p);
                return;
            }
            if (!Casa.dentro(l) && !Casa.naVaranda(l)) {
                pl.mover(p, Casa.inicio(mundo, rnd.nextInt(8)));
                continue;
            }
            boolean esc = escondidoAgora(p);
            if (esc && !j.escondido) {
                j.vistoAoEsconder = ia == Ia.CACANDO && p.getUniqueId().equals(alvo) && tick - vistoEm <= seg(dif("visto-ao-esconder", 1.2));
                p.playSound(l, Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.5f, 0.8f);
            }
            j.escondido = esc;
            if (p.isSprinting() && tick - j.ultimoCorrer >= seg(1)) {
                j.ultimoCorrer = tick;
                barulho(l, 9, p);
            }
        }
        tocarDespertadores();
        if (bestaVoltaEm > 0 && tick >= bestaVoltaEm) {
            bestaVoltaEm = -1;
            devolverAoMapa(Missao.BESTA);
        }
        iaVovo(vivos);
        if (estado != Estado.JOGANDO) return;
        aranha.tick(tick, vivos);
        if (estado != Estado.JOGANDO) return;
        ambiente(vivos);
        hud();
        hudFila();
    }

    private boolean escondidoAgora(Player p) {
        Block b = p.getLocation().getBlock();
        for (Casa.Esconderijo e : casa.esconderijos)
            if (e.x() == b.getX() && e.y() == b.getY() && e.z() == b.getZ()) return true;
        return false;
    }

    private void hud() {
        if (tick % 2 != 0) return;
        int total = diasTotal();
        for (Jogador j : jogadores.values()) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p == null) continue;
            String base = progresso();
            if (j.fora) {
                p.sendActionBar(Vovo.txt("§7Espectador §8| " + base + " §8| §fna casa §e" + vivos().size()));
                continue;
            }
            Missao m = carregado(p);
            StringBuilder s = new StringBuilder("§fDia §e" + (total - j.dias + 1) + "§7/" + total + " §8| " + base + " §8| §fNa mão: "
                    + (m == null ? "§7nada" : m.nome));
            if (j.escondido) s.append(" §a[escondido]");
            if (aranha.perseguindo(p)) s.append(" §4§lA ARANHA!");
            else if (ia == Ia.CACANDO && p.getUniqueId().equals(alvo)) s.append(" §c§lELA TE VIU!");
            else if (tick < j.barulhoAte) s.append(" §6*barulho*");
            p.sendActionBar(Vovo.txt(s.toString()));
        }
    }

    private void ambiente(List<Player> vivos) {
        if (motor != null && motor.isValid()) {
            Location v = motor.getLocation();
            for (Player p : vivos) {
                double d = p.getLocation().distance(v);
                if (d < 10 && ia != Ia.DORMINDO && ia != Ia.ATORDOADA && tick % (d < 5 ? 3 : 5) == 0)
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, (float) Math.max(0.2, 1.0 - d / 12), 1f);
            }
        }
        Location a = aranha.local();
        if (a != null && aranha.estado != Aranha.Estado.DORMINDO) {
            for (Player p : vivos) {
                if (!p.getWorld().equals(a.getWorld())) continue;
                double d = p.getLocation().distance(a);
                if (d < 12 && tick % (d < 6 ? 3 : 5) == 0)
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, (float) Math.max(0.2, 1.0 - d / 14), 0.8f);
            }
        }
        if (tick % 60 == 0 && rnd.nextDouble() < 0.5) {
            Player p = vivos.get(rnd.nextInt(vivos.size()));
            Location l = p.getLocation().add(rnd.nextDouble() * 10 - 5, 0, rnd.nextDouble() * 10 - 5);
            p.playSound(l, rnd.nextBoolean() ? Sound.BLOCK_WOODEN_DOOR_CLOSE : Sound.BLOCK_WOOD_STEP, 0.4f, 0.5f);
        }
        if (tick % 160 == 0) for (Player p : vivos) p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 0.4f, 0.7f);
    }

    // ================================================================== itens

    Missao carregado(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            Missao m = Missao.de(it, pl.kMissao);
            if (m != null) return m;
        }
        return null;
    }

    private void tirarItem(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++)
            if (Missao.de(p.getInventory().getItem(i), pl.kMissao) != null) p.getInventory().setItem(i, null);
    }

    private void darItem(Player p, Missao m) {
        tirarItem(p);
        p.getInventory().setItem(0, m.item(pl.kMissao));
        p.getInventory().setHeldItemSlot(0);
    }

    /** Clique num item em cima de um movel (ou no chao). Se ja carrega um, troca. */
    void pegarItem(Player p, ItemFrame f) {
        if (!vivo(p)) return;
        Missao m = Missao.porId(f.getPersistentDataContainer().get(pl.kItem, PersistentDataType.STRING));
        if (m == null) return;
        Missao atual = carregado(p);
        despertadores.remove(f.getUniqueId());
        if (atual != null) {
            f.setItem(atual.item(pl.kMissao), false);
            f.getPersistentDataContainer().set(pl.kItem, PersistentDataType.STRING, atual.id());
            p.sendMessage(Vovo.PREFIXO + "§7Você deixou §f" + atual.nomeLimpo() + " §7e pegou §f" + m.nomeLimpo() + "§7.");
        } else {
            f.remove();
        }
        darItem(p, m);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.7f, 1.2f);
        titulo(p, "", m.nome, 1.2);
        barulho(p.getLocation(), 3, p);
    }

    /** Tecla Q com o item: larga no chao (faz barulho). */
    void largar(Player p) {
        if (!vivo(p)) return;
        Missao m = carregado(p);
        if (m == null) return;
        ItemFrame f = soltarNoChao(p, true);
        if (f == null) {
            p.sendMessage(Vovo.PREFIXO + "§7Não dá para largar aqui.");
            return;
        }
        Jogador j = jogadores.get(p.getUniqueId());
        if (m == Missao.DESPERTADOR) {
            despertadores.put(f.getUniqueId(), new long[] {tick + seg(4), 0});
            p.sendMessage(Vovo.PREFIXO + "§fO despertador vai tocar em 4 segundos. §7Saia de perto!");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.6f);
        } else {
            mundo.playSound(p.getLocation(), Sound.BLOCK_CHAIN_FALL, 1f, 0.8f);
            barulho(p.getLocation(), p.isSneaking() ? 7 : 14, p);
        }
        if (j != null) j.barulhoAte = tick + 6;
    }

    /** Tira o item do jogador e poe no chao perto dele. */
    private ItemFrame soltarNoChao(Player p, boolean avisarSeFalhar) {
        Missao m = carregado(p);
        if (m == null) return null;
        Location l = p.getLocation();
        Block achou = lugarNoChao(l.getBlock());
        tirarItem(p);
        ItemFrame f;
        if (achou == null) {
            devolverAoMapa(m);
            return null;
        }
        f = colocarItem(m, achou.getX(), achou.getY() - 1, achou.getZ());
        if (f == null) devolverAoMapa(m);
        return f;
    }

    private Block lugarNoChao(Block pe) {
        for (int r = 0; r <= 2; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : new int[] {0, -1, 1}) {
                        Block b = pe.getRelative(dx, dy, dz);
                        if (!b.getType().isAir() || !b.getRelative(BlockFace.DOWN).getType().isOccluding()) continue;
                        if (!Casa.dentro(b.getLocation().add(0.5, 0, 0.5))) continue;
                        boolean ocupado = false;
                        for (Entity e : mundo.getNearbyEntities(b.getLocation().add(0.5, 0.2, 0.5), 0.45, 0.45, 0.45))
                            if (e instanceof ItemFrame) ocupado = true;
                        if (!ocupado) return b;
                    }
                }
        return null;
    }

    /** Poe o item de volta num lugar livre qualquer da casa (quando sumiria). */
    private void devolverAoMapa(Missao m) {
        List<Casa.Ponto> l = new ArrayList<>(casa.pontos);
        Collections.shuffle(l, rnd);
        for (Casa.Ponto pt : l) {
            if (!m.zonas.contains(pt.zona())) continue;
            boolean ocupado = false;
            for (Entity e : mundo.getNearbyEntities(new Location(mundo, pt.x() + 0.5, pt.y() + 1.1, pt.z() + 0.5), 0.45, 0.45, 0.45))
                if (e instanceof ItemFrame) ocupado = true;
            if (ocupado) continue;
            colocarItem(m, pt.x(), pt.y(), pt.z());
            return;
        }
    }

    private void tocarDespertadores() {
        Iterator<Map.Entry<UUID, long[]>> it = despertadores.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, long[]> e = it.next();
            long[] v = e.getValue();
            if (tick < v[0]) continue;
            Entity f = Bukkit.getEntity(e.getKey());
            if (f == null || !f.isValid()) {
                it.remove();
                continue;
            }
            Location l = f.getLocation();
            mundo.playSound(l, Sound.BLOCK_BELL_USE, 2f, 1.4f);
            mundo.playSound(l, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 1.8f);
            mundo.spawnParticle(Particle.NOTE, l.clone().add(0, 0.5, 0), 3, 0.3, 0.2, 0.3);
            barulho(l, 40, null);
            v[1]++;
            if (v[1] >= 5) it.remove();
            else v[0] = tick + 2;
        }
    }

    // ================================================================== trancas

    /** Clique numa porta, na caixa de forca ou no carro. */
    void usar(Player p, Block b) {
        if (!vivo(p)) return;
        if (Casa.ehPortaFrente(b)) portaFrente(p);
        else if (Casa.ehPortaPorao(b)) portaSimples(p, "porao");
        else if (Casa.ehPortaGaragem(b)) portaSimples(p, "garagem");
        else if (Casa.ehCaixa(b)) caixaForca(p);
        else if (Casa.noCarro(b)) carro(p);
        else if (Casa.ehTampa(b)) tampaBueiro(p);
        else if (Casa.naGrade(b)) gradeEsgoto(p);
        else if (Casa.noPortao(b)) p.sendMessage(Vovo.PREFIXO + "§7O portão da garagem só abre quando o carro ligar.");
    }

    private void tampaBueiro(Player p) {
        if (tampa) return;
        if (carregado(p) != Missao.CHAVE_INGLESA) {
            p.sendMessage(Vovo.PREFIXO + "§fA tampa do bueiro está parafusada. Precisa da " + Missao.CHAVE_INGLESA.nome + "§f.");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.4f);
            return;
        }
        tampa = true;
        casa.tampaAberta(new Casa.Obra(mundo));
        Location c = Casa.centro(mundo, Casa.TAMPA);
        mundo.playSound(c, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.2f, 0.6f);
        mundo.playSound(c, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
        consumir(p, "soltou a §9tampa do bueiro§7 da caldeira: dá para descer ao esgoto!", 20);
    }

    private void gradeEsgoto(Player p) {
        if (grade) return;
        if (carregado(p) != Missao.SERRA) {
            p.sendMessage(Vovo.PREFIXO + "§fUma grade de ferro bloqueia a saída do esgoto. Precisa da " + Missao.SERRA.nome + "§f.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_HIT, 0.8f, 0.8f);
            return;
        }
        grade = true;
        casa.gradeAberta(new Casa.Obra(mundo));
        Location c = new Location(mundo, Casa.CENTRO_GRADE[0], Casa.CENTRO_GRADE[1], Casa.CENTRO_GRADE[2]);
        mundo.playSound(c, Sound.BLOCK_GRINDSTONE_USE, 1.5f, 0.6f);
        mundo.playSound(c, Sound.BLOCK_CHAIN_BREAK, 1.5f, 0.5f);
        mundo.spawnParticle(Particle.CRIT, c, 40, 0.3, 1.2, 1.5, 0.1);
        consumir(p, "serrou a §8grade do esgoto§7: a saída está livre!", 22);
        for (Player o : online(true)) titulo(o, "§a§lA GRADE CAIU!", "§fFujam pelo esgoto!", 3);
    }

    boolean ehTranca(Block b) {
        return Casa.ehPortaFrente(b) || Casa.ehPortaPorao(b) || Casa.ehPortaGaragem(b)
                || Casa.ehCaixa(b) || Casa.noCarro(b) || Casa.noPortao(b) || Casa.ehTampa(b) || Casa.naGrade(b);
    }

    private void consumir(Player p, String msg, double raio) {
        tirarItem(p);
        Jogador j = jogadores.get(p.getUniqueId());
        if (j != null) j.barulhoAte = tick + 8;
        barulho(p.getLocation(), raio, p);
        avisar("§f" + p.getName() + " §7" + msg);
        atualizarTextos();
    }

    void portaFrente(Player p) {
        if (!vivo(p)) return;
        if (portaAberta) return;
        Missao m = carregado(p);
        Location l = p.getLocation();
        if (m == Missao.CHAVE_ENFERRUJADA && !cadeado) {
            cadeado = true;
            tirarQuadroTranca(Missao.CHAVE_ENFERRUJADA);
            mundo.playSound(l, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 0.6f);
            consumir(p, "abriu o §6cadeado§7 da porta da frente.", 18);
        } else if (m == Missao.ALICATE && !corrente) {
            corrente = true;
            tirarQuadroTranca(Missao.ALICATE);
            mundo.playSound(l, Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
            mundo.playSound(l, Sound.ENTITY_SHEEP_SHEAR, 1f, 0.6f);
            consumir(p, "cortou a §7corrente§7 da porta da frente.", 18);
        } else if (m == Missao.PE_DE_CABRA && !tabuas) {
            tabuas = true;
            tirarQuadroTranca(Missao.PE_DE_CABRA);
            mundo.playSound(l, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.8f);
            consumir(p, "arrancou as §ctábuas§7 da porta da frente.", 26);
        } else {
            List<String> falta = new ArrayList<>();
            if (!cadeado) falta.add("§6Cadeado §8(Chave Enferrujada)");
            if (!corrente) falta.add("§7Corrente §8(Alicate)");
            if (!tabuas) falta.add("§cTábuas §8(Pé de Cabra)");
            if (!energia) falta.add("§bFechadura elétrica §8(Fusível na caixa de força do porão)");
            p.sendMessage(Vovo.PREFIXO + "§fA porta da frente está trancada. Falta: " + String.join("§7, ", falta));
            p.playSound(l, Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 1.4f);
            return;
        }
        verificarPorta();
    }

    private void verificarPorta() {
        if (portaAberta || trancasAbertas() < 4) return;
        portaAberta = true;
        porta(Casa.PORTA_FRENTE, true);
        porta(Casa.PORTA_FRENTE2, true);
        for (Player o : online(true)) {
            titulo(o, "§a§lA PORTA ESTÁ ABERTA!", "§fCorra para a porta da frente!", 3);
            o.playSound(o.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.6f);
        }
        // ela ouve e corre para a porta
        if (motor != null) {
            mundo.playSound(motor.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 2f, 0.5f);
            ia = Ia.INVESTIGANDO;
            destino = Casa.frentePorta(mundo);
            mover(destino, velocidade("caca"));
        }
    }

    private void portaSimples(Player p, String qual) {
        boolean ehPorao = qual.equals("porao");
        if (ehPorao ? porao : garagem) return;
        Missao precisa = ehPorao ? Missao.CHAVE_PORAO : Missao.CHAVE_GARAGEM;
        if (carregado(p) != precisa) {
            p.sendMessage(Vovo.PREFIXO + "§fTrancada. Precisa da " + precisa.nome + "§f.");
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 1.4f);
            return;
        }
        if (ehPorao) porao = true;
        else garagem = true;
        porta(ehPorao ? Casa.PORTA_PORAO : Casa.PORTA_GARAGEM, true);
        porta(ehPorao ? Casa.PORTA_PORAO2 : Casa.PORTA_GARAGEM2, true);
        consumir(p, "destrancou a " + (ehPorao ? "§eporta do porão" : "§3porta da garagem") + "§7.", 16);
    }

    private void caixaForca(Player p) {
        if (energia) {
            p.sendMessage(Vovo.PREFIXO + "§aA energia já está ligada.");
            return;
        }
        if (carregado(p) != Missao.FUSIVEL) {
            p.sendMessage(Vovo.PREFIXO + "§fA caixa de força está sem o §bFusível§f.");
            return;
        }
        energia = true;
        casa.acenderLampadas(new Casa.Obra(mundo));
        tirarQuadroTranca(Missao.FUSIVEL);
        Location c = Casa.centro(mundo, Casa.CAIXA_FORCA);
        mundo.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        mundo.spawnParticle(Particle.ELECTRIC_SPARK, c, 30, 0.4, 0.4, 0.4, 0.2);
        consumir(p, "colocou o §bfusível§7: a energia voltou!", 24);
        verificarPorta();
    }

    private void carro(Player p) {
        if (carroLigado) return;
        if (!garagem && p.getLocation().getX() < Casa.PAREDE_LESTE + 0.5) return;
        Missao m = carregado(p);
        Location l = p.getLocation();
        if (m == Missao.BATERIA && !bateria) {
            bateria = true;
            mundo.playSound(l, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.7f);
            consumir(p, "colocou a §4bateria§7 no carro.", 14);
        } else if (m == Missao.GASOLINA && !gasolina) {
            gasolina = true;
            mundo.playSound(l, Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 0.8f);
            consumir(p, "encheu o tanque com §6gasolina§7.", 14);
        } else if (m == Missao.CHAVE_CARRO && !chaveCarro) {
            chaveCarro = true;
            mundo.playSound(l, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 0.8f);
            consumir(p, "colocou a §achave§7 no carro.", 10);
        } else if (pecasCarro() == 3) {
            ligarCarro(p);
        } else {
            List<String> falta = new ArrayList<>();
            if (!bateria) falta.add("§4Bateria");
            if (!gasolina) falta.add("§6Gasolina");
            if (!chaveCarro) falta.add("§aChave do Carro");
            p.sendMessage(Vovo.PREFIXO + "§fO carro não liga. Falta: " + String.join("§7, ", falta));
        }
    }

    private void ligarCarro(Player p) {
        carroLigado = true;
        Casa.Obra o = new Casa.Obra(mundo);
        casa.carro(o, true);
        casa.portaoAberto(o);
        Location c = Casa.carroCentro(mundo);
        mundo.playSound(c, Sound.ENTITY_MINECART_RIDING, 2f, 0.6f);
        mundo.playSound(c, Sound.BLOCK_PISTON_EXTEND, 2f, 0.5f);
        mundo.playSound(c, Sound.ENTITY_IRON_GOLEM_DEATH, 1f, 0.4f);
        mundo.spawnParticle(Particle.LARGE_SMOKE, new Location(mundo, Casa.FUMACA[0], Casa.FUMACA[1], Casa.FUMACA[2]), 40, 0.4, 0.2, 0.4, 0.02);
        atualizarTextos();
        avisar("§a§l" + p.getName() + " ligou o carro! VÃO, VÃO, VÃO!");
        barulho(c, 60, p);
        Bukkit.getScheduler().runTaskLater(pl, () -> vitoria("Fugiram de carro", p), 50L);
    }

    // ================================================================== tabuas, armadilhas e corrida

    /** Chamado quando o jogador muda de bloco. */
    void passo(Player p, Location para) {
        if (!vivo(p)) return;
        Jogador j = jogadores.get(p.getUniqueId());
        Block pe = para.getBlock();
        if (armadilhas.remove(chave(pe))) {
            pe.setType(Material.AIR, false);
            p.setWalkSpeed(0f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 6, false, false, false));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
            mundo.playSound(pe.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.2f, 1.5f);
            titulo(p, "§c§lARMADILHA!", "§7Você ficou preso e fez muito barulho...", 1.5);
            j.barulhoAte = tick + 12;
            barulho(pe.getLocation(), 32, p);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline() && vivo(p) && p.getWalkSpeed() == 0f && !(ia == Ia.PEGANDO && p.getUniqueId().equals(alvo))) p.setWalkSpeed(0.2f);
            }, 60L);
            return;
        }
        Block piso = pe.getRelative(BlockFace.DOWN);
        if (rangidos.containsKey(chave(piso)) && !p.isSneaking() && tick - j.ultimoRangido >= 3) {
            j.ultimoRangido = tick;
            j.barulhoAte = tick + 6;
            mundo.playSound(piso.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 0.9f, 0.5f);
            barulho(piso.getLocation(), 13, p);
        }
    }

    // ================================================================== a vovo

    private double velocidade(String tipo) {
        return switch (tipo) {
            case "ronda" -> dif("ritmo-ronda", pl.getConfig().getDouble("vovo.ritmo-ronda", 0.65));
            case "investigar" -> dif("ritmo-investigar", pl.getConfig().getDouble("vovo.ritmo-investigar", 0.9));
            default -> dif("ritmo-caca", pl.getConfig().getDouble("vovo.ritmo-caca", 1.0));
        };
    }

    private void ajustarVelocidade() {
        if (motor == null) return;
        AttributeInstance v = motor.getAttribute(Attribute.MOVEMENT_SPEED);
        if (v != null) v.setBaseValue(pl.getConfig().getDouble("vovo.velocidade", 0.25) * (1 + bonus));
    }

    private void criarVovo(Location l) {
        removerVovo();
        motor = mundo.spawn(l, Zombie.class, z -> {
            z.getPersistentDataContainer().set(pl.kVovo, PersistentDataType.BYTE, (byte) 1);
            z.setAdult();
            z.setInvisible(true);
            z.setSilent(true);
            z.setCollidable(false);
            z.setInvulnerable(true);
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            try {
                z.setShouldBurnInDay(false);
            } catch (Throwable ignored) {
            }
            z.getEquipment().clear();
            // com um efeito ativo o Paper nunca deixa o zumbi "inativo" quando os jogadores estao longe
            // (a casa e grande: sem isso ela congelava parada a mais de 32 blocos de todo mundo)
            z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            AttributeInstance a = z.getAttribute(Attribute.FOLLOW_RANGE);
            if (a != null) a.setBaseValue(64);
            a = z.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (a != null) a.setBaseValue(1);
        });
        try {
            Bukkit.getMobGoals().removeAllGoals(motor);
        } catch (Throwable t) {
            pl.getLogger().warning("Nao consegui tirar a IA padrao do zumbi: " + t);
        }
        motor.getPathfinder().setCanOpenDoors(false);
        motor.getPathfinder().setCanPassDoors(true);
        motor.getPathfinder().setCanFloat(false);
        ajustarVelocidade();
        corpo = mundo.spawn(l, Mannequin.class, m -> {
            m.getPersistentDataContainer().set(pl.kVovo, PersistentDataType.BYTE, (byte) 1);
            m.setProfile(pl.perfilVovo());
            m.setDescription(null);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setPersistent(false);
            m.setGravity(false);
            m.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SHOVEL));
        });
        ia = Ia.RONDA;
        alvo = null;
        destino = null;
        esperaAte = 0;
        parado = 0;
        ultimaPos = l.clone();
        proximaVoz = tick + seg(10);
    }

    void removerVovo() {
        if (zzz != null) zzz.remove();
        zzz = null;
        if (corpo != null) corpo.remove();
        if (motor != null) motor.remove();
        corpo = null;
        motor = null;
        alvo = null;
        olhando = null;
    }

    boolean ehVovo(Entity e) {
        return e != null && e.getPersistentDataContainer().has(pl.kVovo, PersistentDataType.BYTE);
    }

    /** Roda todo tick: o corpo (manequim com a skin) acompanha o motor invisivel. */
    void sincronizar() {
        aranha.sincronizar();
        if (motor == null || corpo == null) return;
        if (!motor.isValid() || !corpo.isValid()) return;
        Location l = motor.getLocation();
        float yaw;
        if (olhando != null && olhando.isOnline() && olhando.getWorld().equals(mundo)) {
            Vector v = olhando.getLocation().toVector().subtract(l.toVector());
            Location t = l.clone();
            t.setDirection(v);
            yaw = t.getYaw();
        } else yaw = motor.getBodyYaw();
        l.setYaw(yaw);
        l.setPitch(0);
        corpo.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        corpo.setRotation(yaw, 0);
    }

    /** Anda ate "para" passando pelas portas e escadas do caminho (o buscador do Minecraft so faz trechos curtos). */
    /** A vovo nao desce no esgoto: barulho ou jogador la embaixo vira "ir ate a tampa do bueiro". */
    private Location alcance(Location l) {
        if (l == null) return null;
        return Casa.zonaDe(l) == Casa.Zona.ESGOTO ? Casa.sobreOBueiro(mundo) : l;
    }

    private boolean mover(Location para, double r) {
        if (motor == null || para == null) return false;
        para = alcance(para);
        boolean trocou = alvoMov == null || alvoMov.distanceSquared(para) > 0.25 || ritmo != r;
        alvoMov = para.clone();
        ritmo = r;
        // no meio de uma escada nao recalcula (senao ela sobe e desce sem parar): termina o trecho primeiro
        if (!rota.isEmpty() && (naEscada(motor.getLocation()) || (transito != null && rota.peek() == transito))) return seguir(false);
        rota.clear();
        transito = null;
        rota.addAll(casa.rota(mundo, motor.getLocation(), para, porao, garagem));
        return seguir(true);
    }

    private static boolean naEscada(Location l) {
        return Casa.naEscada(l);
    }

    private boolean seguir(boolean forcar) {
        if (motor == null || alvoMov == null) return false;
        Location l = motor.getLocation();
        boolean avancou = false;
        while (!rota.isEmpty() && pertoDe(l, rota.peek())) {
            Location feito = rota.poll();
            avancou = true;
            transito = !rota.isEmpty() && parDePassagem(feito, rota.peek()) ? rota.peek() : null;
        }
        if (transito != null && rota.peek() != transito) transito = null;
        Location prox = rota.isEmpty() ? alvoMov : rota.peek();
        if (forcar || avancou || !motor.getPathfinder().hasPath()) return motor.getPathfinder().moveTo(prox, ritmo);
        return true;
    }

    private boolean parDePassagem(Location a, Location b) {
        for (Casa.Passagem pg : casa.passagens) {
            if (pg.pa() == pg.pb()) continue;
            if ((igual(a, pg.pa()) && igual(b, pg.pb())) || (igual(a, pg.pb()) && igual(b, pg.pa()))) return true;
        }
        return false;
    }

    private static boolean igual(Location l, double[] c) {
        return Math.abs(l.getX() - c[0]) < 0.01 && Math.abs(l.getY() - c[1]) < 0.01 && Math.abs(l.getZ() - c[2]) < 0.01;
    }

    private static boolean pertoDe(Location l, Location p) {
        double dx = l.getX() - p.getX(), dz = l.getZ() - p.getZ();
        return dx * dx + dz * dz < 0.8 && Math.abs(l.getY() - p.getY()) < 1.6;
    }

    private void pararVovo() {
        alvoMov = null;
        transito = null;
        rota.clear();
        if (motor != null) motor.getPathfinder().stopPathfinding();
    }

    private boolean chegou(Location d) {
        if (motor == null || d == null) return true;
        Location l = motor.getLocation();
        double dx = l.getX() - d.getX(), dz = l.getZ() - d.getZ();
        return dx * dx + dz * dz < 1.4 && Math.abs(l.getY() - d.getY()) < 1.6;
    }

    /** Ela ouve um barulho. */
    void barulho(Location l, double raio, Player quem) {
        if (estado != Estado.JOGANDO || motor == null || !motor.isValid() || !l.getWorld().equals(mundo)) return;
        if (quem != null) {
            Jogador j = jogadores.get(quem.getUniqueId());
            if (j != null) j.barulhoAte = Math.max(j.barulhoAte, tick + 5);
        }
        Location v = motor.getLocation();
        raio *= dif("audicao", 1.0);
        double d = v.distance(l);
        if (Math.abs(v.getY() - l.getY()) > 3) d *= 1.35;
        if (d > raio) return;
        switch (ia) {
            case PEGANDO, ATORDOADA -> {
                return;
            }
            case DORMINDO -> {
                if (raio < 12) return;
                acordar("§cUm barulho acordou a vovó!");
            }
            case CACANDO -> {
                if (tick - vistoEm < seg(2)) return;
            }
            default -> { }
        }
        ia = Ia.INVESTIGANDO;
        alvo = null;
        olhando = null;
        destino = alcance(l.clone());
        esperaAte = 0;
        parado = 0;
        mover(destino, velocidade("investigar"));
        if (rnd.nextDouble() < 0.5) mundo.playSound(v, Sound.ENTITY_WITCH_AMBIENT, 1.4f, 0.7f);
    }

    private void acordar(String msg) {
        if (ia != Ia.DORMINDO) return;
        ia = Ia.RONDA;
        destino = null;
        if (motor != null) mundo.playSound(motor.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 2f, 0.6f);
        avisar(msg);
    }

    private Player procurar(List<Player> vivos) {
        if (motor == null) return null;
        Location olho = motor.getEyeLocation();
        Vector frente = olho.getDirection();
        Player melhor = null;
        double melhorD = 999;
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null || tick < j.graca) continue;
            if (j.escondido && !j.vistoAoEsconder) continue;
            Location pe = p.getEyeLocation();
            Vector para = pe.toVector().subtract(olho.toVector());
            double d = para.length();
            double lim = p.isSneaking() ? dif("visao-agachado", 8) : dif("visao", 16);
            if (ia == Ia.CACANDO && p.getUniqueId().equals(alvo)) lim += 6;
            if (d > lim) continue;
            if (d > 2.6) {
                double ang = Math.toDegrees(frente.angle(para));
                if (ang > (ia == Ia.CACANDO ? 100 : dif("angulo", 65))) continue;
            }
            if (!motor.hasLineOfSight(p)) continue;
            if (d < melhorD) {
                melhorD = d;
                melhor = p;
            }
        }
        return melhor;
    }

    private void iaVovo(List<Player> vivos) {
        if (motor == null || !motor.isValid() || corpo == null || !corpo.isValid()) {
            Location onde = motor != null && motor.isValid() ? motor.getLocation() : Casa.quartoVovo(mundo);
            Ia antes = ia;
            criarVovo(onde);
            if (antes == Ia.DORMINDO) ia = Ia.DORMINDO;
        }
        Location v = motor.getLocation();
        if (!Casa.dentro(v) || Casa.zonaDe(v) == Casa.Zona.ESGOTO) {
            // saiu da casa de algum jeito (empurrada, bug): volta para dentro longe dos jogadores
            reaparecerLonge(vivos);
            v = motor.getLocation();
        }
        switch (ia) {
            case DORMINDO -> {
                pararVovo();
                if (tick >= ateQuando) acordar("§cA vovó acordou... e está andando pela casa.");
                return;
            }
            case ATORDOADA -> {
                pararVovo();
                if (tick % 8 == 0) mundo.spawnParticle(Particle.CRIT, v.clone().add(0, 2.1, 0), 4, 0.2, 0.1, 0.2, 0.01);
                if (tick >= ateQuando) {
                    if (zzz != null) zzz.remove();
                    zzz = null;
                    ia = Ia.RONDA;
                    destino = null;
                    mundo.playSound(v, Sound.ENTITY_WITCH_AMBIENT, 2f, 0.5f);
                    avisar("§cA vovó acordou do dardo... e está brava.");
                }
                return;
            }
            case PEGANDO -> {
                pararVovo();
                if (tick >= ateQuando) {
                    ia = Ia.RONDA;
                    olhando = null;
                    alvo = null;
                    destino = null;
                    esperaAte = tick + seg(2);
                }
                return;
            }
            default -> { }
        }

        // pegar quem estiver colado nela
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null || tick < j.graca) continue;
            if (j.escondido && !j.vistoAoEsconder) continue;
            Location pl0 = p.getLocation();
            double dx = pl0.getX() - v.getX(), dz = pl0.getZ() - v.getZ();
            if (dx * dx + dz * dz < 1.35 * 1.35 && Math.abs(pl0.getY() - v.getY()) < 1.3 && motor.hasLineOfSight(p)) {
                pegar(p);
                return;
            }
        }

        Player visto = procurar(vivos);
        if (visto != null) {
            if (ia != Ia.CACANDO || !visto.getUniqueId().equals(alvo)) comecarCaca(visto);
            vistoEm = tick;
            ultimoLocal = visto.getLocation();
        }

        switch (ia) {
            case CACANDO -> {
                Player a = alvo == null ? null : Bukkit.getPlayer(alvo);
                if (a == null || !vivo(a)) {
                    ia = Ia.RONDA;
                    destino = null;
                    olhando = null;
                    break;
                }
                if (tick - vistoEm > seg(dif("memoria", 4))) {
                    ia = Ia.INVESTIGANDO;
                    destino = alcance(ultimoLocal);
                    olhando = null;
                    alvo = null;
                    mover(destino, velocidade("investigar"));
                    break;
                }
                destino = alcance(tick - vistoEm <= 1 ? a.getLocation() : ultimoLocal);
                mover(destino, velocidade("caca"));
                olhando = tick - vistoEm <= 1 ? a : null;
            }
            case INVESTIGANDO -> {
                if (chegou(destino)) {
                    if (esperaAte == 0) {
                        esperaAte = tick + seg(2 + rnd.nextDouble() * 2);
                        pararVovo();
                        if (rnd.nextDouble() < 0.4) mundo.playSound(v, Sound.ENTITY_WITCH_AMBIENT, 1.2f, 0.8f);
                        if (abrirArmario(vivos, v)) return;
                    } else if (tick >= esperaAte) {
                        ia = Ia.RONDA;
                        destino = null;
                        esperaAte = 0;
                    }
                } else if (tick % 4 == 0) mover(destino, velocidade("investigar"));
            }
            default -> ronda(vivos);
        }

        if (alvoMov != null) seguir(false);

        // travou? tenta de novo, depois outro lugar, depois reaparece longe de todo mundo
        boolean querAndar = destino != null && !chegou(destino) && esperaAte == 0;
        if (querAndar && ultimaPos != null && v.distanceSquared(ultimaPos) < 0.03) parado++;
        else parado = 0;
        ultimaPos = v.clone();
        if (parado == seg(3)) mover(destino, velocidade(ia == Ia.CACANDO ? "caca" : "investigar"));
        if (parado == seg(6) && ia != Ia.CACANDO) {
            ia = Ia.RONDA;
            destino = null;
        }
        // parada no mesmo lugar (mesmo trocando de destino) por muito tempo: reaparece longe
        if (localPreso == null || v.distanceSquared(localPreso) > 1.0) {
            localPreso = v.clone();
            tempoPreso = 0;
        } else if (querAndar) tempoPreso++;
        if (parado >= seg(12) || tempoPreso >= seg(20)) {
            parado = 0;
            tempoPreso = 0;
            reaparecerLonge(vivos);
        }

        // passos e voz
        boolean andando = motor.getVelocity().setY(0).lengthSquared() > 0.0004 || parado == 0 && querAndar;
        if (andando && tick % (ia == Ia.CACANDO ? 1 : 2) == 0) mundo.playSound(v, Sound.BLOCK_WOOD_STEP, 0.9f, 0.6f);
        if (tick >= proximaVoz) {
            proximaVoz = tick + seg(12 + rnd.nextInt(14));
            mundo.playSound(v, Sound.ENTITY_WITCH_AMBIENT, 1.3f, 0.75f + rnd.nextFloat() * 0.1f);
        }
    }

    /** Chegou onde ouviu o barulho: as vezes ela abre o armario mais perto. Quem estiver la dentro e pego. */
    private boolean abrirArmario(List<Player> vivos, Location v) {
        double chance = dif("chance-armario", 0);
        if (chance <= 0) return false;
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null || !j.escondido || tick < j.graca) continue;
            Location l = p.getLocation();
            double dx = l.getX() - v.getX(), dz = l.getZ() - v.getZ();
            if (dx * dx + dz * dz > 3.5 * 3.5 || Math.abs(l.getY() - v.getY()) > 1.5) continue;
            if (rnd.nextDouble() >= chance) return false;
            mundo.playSound(l, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 1.2f, 0.7f);
            mundo.playSound(v, Sound.ENTITY_WITCH_CELEBRATE, 1.6f, 0.6f);
            titulo(p, "", "§c§lELA ABRIU O ARMÁRIO!", 1.2);
            olhando = p;
            pegar(p);
            return true;
        }
        return false;
    }

    private void comecarCaca(Player p) {
        boolean novo = ia != Ia.CACANDO;
        ia = Ia.CACANDO;
        alvo = p.getUniqueId();
        esperaAte = 0;
        parado = 0;
        if (novo) {
            mundo.playSound(motor.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1.6f, 0.7f);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.35f, 1.5f);
            titulo(p, "", "§c§lELA TE VIU! CORRA!", 1.2);
        }
    }

    private void ronda(List<Player> vivos) {
        if (tick >= proximaVisita && !vivos.isEmpty()) {
            proximaVisita = tick + seg(dif("visita-segundos", 50) + rnd.nextInt(30));
            Player p = vivos.get(rnd.nextInt(vivos.size()));
            Casa.Ronda r = rondaPerto(p.getLocation());
            if (r != null && !(tick < evitarQuartoAte && r.sala().equals("quarto"))) {
                destino = new Location(mundo, r.x(), r.y(), r.z());
                esperaAte = 0;
                mover(destino, velocidade("ronda"));
                return;
            }
        }
        if (destino == null || chegou(destino)) {
            if (esperaAte == 0) {
                esperaAte = tick + seg(1.5 + rnd.nextDouble() * 3);
                pararVovo();
                return;
            }
            if (tick < esperaAte) return;
            esperaAte = 0;
            List<Casa.Ronda> ok = rondasPossiveis();
            if (ok.isEmpty()) return;
            if (tick < evitarQuartoAte) ok.removeIf(x -> x.sala().equals("quarto"));
            if (ok.isEmpty()) return;
            Casa.Ronda r;
            if (!vivos.isEmpty() && rnd.nextDouble() < dif("chance-perto", 0.35)) r = rondaPerto(vivos.get(rnd.nextInt(vivos.size())).getLocation());
            else r = ok.get(rnd.nextInt(ok.size()));
            if (r == null || (tick < evitarQuartoAte && r.sala().equals("quarto"))) r = ok.get(rnd.nextInt(ok.size()));
            destino = new Location(mundo, r.x(), r.y(), r.z());
            mover(destino, velocidade("ronda"));
        } else if (tick % 8 == 0) mover(destino, velocidade("ronda"));
    }

    private List<Casa.Ronda> rondasPossiveis() {
        List<Casa.Ronda> l = new ArrayList<>();
        for (Casa.Ronda r : casa.ronda) {
            if (r.zona() == Casa.Zona.PORAO && !porao) continue;
            if (r.zona() == Casa.Zona.GARAGEM && !garagem) continue;
            l.add(r);
        }
        return l;
    }

    private Casa.Ronda rondaPerto(Location l) {
        Casa.Ronda melhor = null;
        double md = 1e9;
        for (Casa.Ronda r : rondasPossiveis()) {
            double d = Math.pow(r.x() - l.getX(), 2) + Math.pow((r.y() - l.getY()) * 2, 2) + Math.pow(r.z() - l.getZ(), 2);
            if (d < md) {
                md = d;
                melhor = r;
            }
        }
        return melhor;
    }

    private void reaparecerLonge(List<Player> vivos) {
        List<Casa.Ronda> ok = rondasPossiveis();
        Collections.shuffle(ok, rnd);
        for (Casa.Ronda r : ok) {
            Location l = new Location(mundo, r.x(), r.y(), r.z());
            boolean longe = true;
            for (Player p : vivos) if (p.getLocation().distanceSquared(l) < 14 * 14) longe = false;
            if (!longe) continue;
            pararVovo();
            motor.teleport(l);
            ia = Ia.RONDA;
            destino = null;
            esperaAte = 0;
            pl.getLogger().info("A vovo travou e reapareceu em " + r.sala());
            return;
        }
        if (!ok.isEmpty()) {
            Casa.Ronda r = ok.get(0);
            pararVovo();
            motor.teleport(new Location(mundo, r.x(), r.y(), r.z()));
            ia = Ia.RONDA;
            destino = null;
            esperaAte = 0;
        }
    }

    private void pegar(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || j.fora || estado != Estado.JOGANDO) return;
        ia = Ia.PEGANDO;
        ateQuando = tick + seg(2.2);
        alvo = p.getUniqueId();
        olhando = p;
        pararVovo();
        j.graca = tick + seg(12);
        corpo.swingMainHand();
        soltarNoChao(p, false);
        p.setWalkSpeed(0f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
        p.playSound(p.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1.4f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.6f);
        titulo(p, "§4§lPEGO!", "§7A vovó te pegou...", 1.6);
        for (Player o : online(true)) if (o != p) o.playSound(o.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 0.5f, 0.6f);
        evitarQuartoAte = tick + seg(40);
        proximaVisita = Math.max(proximaVisita, tick + seg(40));
        bonus = Math.min(dif("bonus-maximo", pl.getConfig().getDouble("vovo.bonus-maximo", 0.10)), bonus + dif("bonus-por-captura", pl.getConfig().getDouble("vovo.bonus-por-captura", 0.02)));
        ajustarVelocidade();
        Bukkit.getScheduler().runTaskLater(pl, () -> depoisDePego(p, "pela vovó"), 36L);
    }

    /** A aranha do esgoto pegou alguem: perde um dia, igual a vovo. */
    void pegarPelaAranha(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || j.fora || estado != Estado.JOGANDO || tick < j.graca) return;
        j.graca = tick + seg(12);
        soltarNoChao(p, false);
        p.setWalkSpeed(0f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
        p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_DEATH, 1.6f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.6f);
        titulo(p, "§4§lPEGO!", "§7A aranha te pegou...", 1.6);
        for (Player o : online(true)) if (o != p) o.playSound(o.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.5f, 0.5f);
        Bukkit.getScheduler().runTaskLater(pl, () -> depoisDePego(p, "pela aranha do esgoto"), 36L);
    }

    private void depoisDePego(Player p, String quem) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !p.isOnline() || estado != Estado.JOGANDO) return;
        j.dias--;
        j.escondido = false;
        p.setWalkSpeed(0.2f);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        if (j.dias <= 0) {
            j.fora = true;
            p.setGameMode(GameMode.SPECTATOR);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            titulo(p, "§4§lFIM PARA VOCÊ", "§7Seus dias acabaram. Agora você assiste.", 3);
            int restam = vivos().size();
            avisar("§c☠ §f" + j.nome + " §cnão tem mais dias. §7(" + restam + " na casa)");
            p.sendMessage(Vovo.PREFIXO + "§7Você virou espectador. §e/vovo sair §7para voltar.");
            int espera = Reviver.segundos(pl);
            if (Reviver.oferecer(pl, p, "a casa da vovó", espera, () -> reviver(p))) j.reviverAte = tick + seg(espera + 2);
            return;
        }
        int dia = diasTotal() - j.dias + 1;
        pl.mover(p, Casa.inicio(mundo, rnd.nextInt(8)));
        j.graca = tick + seg(6);
        titulo(p, "§fDIA §c" + dia, "§7Você acordou no quarto. Restam §f" + j.dias + " §7dia" + (j.dias == 1 ? "" : "s") + ".", 3);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 0.6f);
        avisar("§f" + j.nome + " §7foi pego " + quem + ". §8(dia " + dia + "/" + diasTotal() + ")");
    }

    /** Teste (admin): o jogador perde o ultimo dia agora. */
    boolean eliminarTeste(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (!vivo(p) || j == null) return false;
        j.dias = 1;
        depoisDePego(p, "pelo teste");
        return true;
    }

    /** Alguem sem dias ainda pode gastar a Moeda de Reviver (a partida espera). */
    private boolean alguemPodeVoltar() {
        for (Jogador j : jogadores.values()) {
            if (!j.jogando || !j.fora || j.reviverAte < tick) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    /** Moeda de Reviver: o jogador acorda no quarto com mais 1 dia. */
    boolean reviver(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.jogando || !j.fora || estado != Estado.JOGANDO || !p.isOnline()) return false;
        j.fora = false;
        j.dias = 1;
        j.escondido = false;
        j.reviverAte = -1;
        j.graca = tick + seg(8);
        fimPendente = -1;
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setWalkSpeed(0.2f);
        p.setGameMode(GameMode.ADVENTURE);
        pl.mover(p, Casa.inicio(mundo, rnd.nextInt(8)));
        titulo(p, "§a§lVOCÊ VOLTOU!", "§7Você acordou no quarto com §fmais 1 dia§7.", 3);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 0.9f);
        avisar("§e✦ §f" + j.nome + " §eusou uma Moeda de Reviver e voltou para a casa!");
        return true;
    }

    /** Besta com dardo: se acertar, ela dorme por um tempo. */
    void atirar(Player p) {
        if (!vivo(p) || carregado(p) != Missao.BESTA) return;
        tirarItem(p);
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection();
        mundo.playSound(olho, Sound.ITEM_CROSSBOW_SHOOT, 1f, 1f);
        for (double d = 1; d < 20; d += 0.7) mundo.spawnParticle(Particle.CRIT, olho.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        bestaVoltaEm = tick + seg(pl.getConfig().getInt("besta-volta-segundos", 90));
        boolean acertou = false;
        if (motor != null && motor.isValid() && ia != Ia.ATORDOADA) {
            Vector para = motor.getLocation().add(0, 1.2, 0).toVector().subtract(olho.toVector());
            double dist = para.length();
            double ang = Math.toDegrees(dir.angle(para));
            acertou = dist <= 26 && ang <= (dist < 6 ? 25 : 12) && p.hasLineOfSight(motor);
        }
        if (!acertou && aranha.viva()) {
            Vector para = aranha.local().add(0, 0.8, 0).toVector().subtract(olho.toVector());
            double dist = para.length();
            if (dist <= 26 && Math.toDegrees(dir.angle(para)) <= (dist < 6 ? 30 : 15) && p.hasLineOfSight(aranha.entidade())) {
                p.sendMessage(Vovo.PREFIXO + "§7O dardo acertou a aranha... §cmas não fez nada nela! §8(dardo só funciona na vovó)");
                aranha.irritar(p);
                return;
            }
        }
        if (!acertou) {
            p.sendMessage(Vovo.PREFIXO + "§7O dardo errou... §8(a besta volta para algum lugar da casa daqui a pouco)");
            barulho(p.getLocation(), 10, p);
            return;
        }
        int dur = (int) dif("dardo-segundos", pl.getConfig().getInt("vovo.dardo-segundos", 25));
        ia = Ia.ATORDOADA;
        ateQuando = tick + seg(dur);
        alvo = null;
        olhando = null;
        pararVovo();
        mundo.playSound(motor.getLocation(), Sound.ENTITY_WITCH_HURT, 1.5f, 0.6f);
        if (zzz != null) zzz.remove();
        zzz = mundo.spawn(corpo.getLocation().add(0, 2.2, 0), TextDisplay.class, d -> {
            d.text(Vovo.txt("§b§lzZz"));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(pl.kVovo, PersistentDataType.BYTE, (byte) 1);
        });
        corpo.addPassenger(zzz);
        avisar("§d" + p.getName() + " §7acertou o dardo: a vovó dormiu por §f" + dur + "s§7!");
    }

    // ================================================================== admin / testes

    String darTeste(Player p, Missao m) {
        if (!vivo(p)) return "nao esta jogando";
        darItem(p, m);
        return "na mao: " + p.getInventory().getItemInMainHand().getType() + " / carregado=" + carregado(p);
    }

    void abrirTudo() {
        cadeado = corrente = tabuas = true;
        energia = true;
        casa.acenderLampadas(new Casa.Obra(mundo));
        for (Missao m : new ArrayList<>(quadrosTranca.keySet())) tirarQuadroTranca(m);
        porao = garagem = true;
        porta(Casa.PORTA_PORAO, true);
        porta(Casa.PORTA_PORAO2, true);
        porta(Casa.PORTA_GARAGEM, true);
        porta(Casa.PORTA_GARAGEM2, true);
        tampa = true;
        casa.tampaAberta(new Casa.Obra(mundo));
        atualizarTextos();
        verificarPorta();
    }

    String pisosTeste() {
        StringBuilder sb = new StringBuilder("rangem: ");
        int n = 0;
        for (long k : rangidos.keySet()) {
            if (n++ >= 4) break;
            sb.append(((k >> 40) & 0xFFFFF) - 1000).append(',').append(((k >> 20) & 0xFFFFF) - 1000).append(',').append((k & 0xFFFFF) - 1000).append(' ');
        }
        sb.append("| armadilhas: ");
        for (long k : armadilhas) sb.append(((k >> 40) & 0xFFFFF) - 1000).append(',').append(((k >> 20) & 0xFFFFF) - 1000).append(',').append((k & 0xFFFFF) - 1000).append(' ');
        return sb.toString();
    }

    void cacarTeste(Player p) {
        if (motor == null || !vivo(p)) return;
        if (ia == Ia.DORMINDO || ia == Ia.ATORDOADA) ia = Ia.RONDA;
        comecarCaca(p);
        vistoEm = tick;
        ultimoLocal = p.getLocation();
    }

    String aranhaTeste(Player p, String acao) {
        switch (acao) {
            case "acordar" -> aranha.acordar();
            case "cacar" -> {
                if (p != null) aranha.cacar(p);
            }
            case "aqui" -> {
                if (p != null) aranha.aqui(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(5)));
            }
            case "ninho" -> aranha.aqui(Casa.ninhoAranha(mundo));
            case "criar" -> aranha.criar();
            default -> { }
        }
        return aranha.status();
    }

    void dormirTeste(int segundos) {
        if (motor == null) return;
        ia = Ia.DORMINDO;
        ateQuando = tick + seg(segundos);
        pararVovo();
    }

    void vovoAqui(Player p) {
        if (motor == null) return;
        Location l = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4));
        if (!Casa.dentro(l) || !l.getBlock().isPassable()) {
            Casa.Ronda r = rondaPerto(p.getLocation());
            if (r != null) l = new Location(mundo, r.x(), r.y(), r.z());
        }
        pararVovo();
        motor.teleport(l);
        if (ia == Ia.DORMINDO) ia = Ia.RONDA;
    }

    String status() {
        StringBuilder js = new StringBuilder();
        for (Jogador j : jogadores.values())
            if (j.jogando) js.append(j.nome).append("(dias=").append(j.dias).append(j.fora ? ",fora" : "").append(j.escondido ? ",esc" : "").append(") ");
        String v = motor != null && motor.isValid() ? String.format("%.1f %.1f %.1f", motor.getX(), motor.getY(), motor.getZ()) : "-";
        String c = corpo != null && corpo.isValid() ? String.format("%.1f %.1f %.1f", corpo.getX(), corpo.getY(), corpo.getZ()) : "-";
        return "modo=" + modo + " estado=" + estado + " ia=" + ia + " vovo=" + v + " corpo=" + c + " destino="
                + (destino == null ? "-" : String.format("%.1f %.1f %.1f", destino.getX(), destino.getY(), destino.getZ()))
                + " rota=" + rota.size() + (rota.isEmpty() ? "" : String.format("(%.1f %.1f %.1f)", rota.peek().getX(), rota.peek().getY(), rota.peek().getZ()))
                + (transito != null ? "[T]" : "") + (motor != null && motor.getPathfinder().hasPath() ? "[P" + caminho() + "]" : "[-]") + " porta=" + trancasAbertas() + "/4 carro=" + pecasCarro() + "/3 esgoto=" + etapasEsgoto() + "/2 porao=" + porao + " garagem=" + garagem
                + " rangidos=" + rangidos.size() + " armadilhas=" + armadilhas.size() + " bonus=" + String.format("%.2f", bonus)
                + " velAttr=" + (motor == null || motor.getAttribute(Attribute.MOVEMENT_SPEED) == null ? "-" : String.format("%.3f", motor.getAttribute(Attribute.MOVEMENT_SPEED).getValue()))
                + " " + aranha.status() + " jogadores=[" + js.toString().trim() + "]";
    }

    private String caminho() {
        var c = motor.getPathfinder().getCurrentPath();
        if (c == null) return "";
        Location n = c.getNextPoint(), f = c.getFinalPoint();
        return (n == null ? "" : String.format(" prox=%.1f,%.1f,%.1f", n.getX(), n.getY(), n.getZ()))
                + (f == null ? "" : String.format(" fim=%.1f,%.1f,%.1f", f.getX(), f.getY(), f.getZ())) + " pts=" + c.getPoints().size() + " i=" + c.getNextPointIndex();
    }

    String itensNoMapa() {
        StringBuilder sb = new StringBuilder();
        for (Entity e : mundo.getEntities()) {
            String id = e.getPersistentDataContainer().get(pl.kItem, PersistentDataType.STRING);
            if (id == null) continue;
            Location l = e.getLocation();
            sb.append(id).append('@').append(l.getBlockX()).append(',').append(l.getBlockY()).append(',').append(l.getBlockZ()).append(' ');
        }
        return sb.toString().trim();
    }

    Component nada() {
        return Component.empty();
    }
}
