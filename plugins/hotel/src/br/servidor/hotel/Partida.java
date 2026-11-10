package br.servidor.hotel;

import java.time.Duration;
import java.util.ArrayList;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Uma partida do Hotel: fila, comodos sendo montados, portas, armarios, gavetas e as criaturas. */
final class Partida {

    enum Estado { ESPERANDO, CONTAGEM, JOGANDO, FIM }

    /** Tamanhos parecidos com os dos monstros originais (cabeca de jogador = 0,5 bloco x escala). */
    static final float ESC_ESTRONDO = 4.4f, ESC_RETORNO = 5.0f, ESC_OLHOS = 2.0f, ESC_SUSSURRO = 1.4f, ESC_OLHO_PAREDE = 0.55f;
    static final double ESC_FIGURA = 2.1, ESC_PERSEGUIDOR = 1.4;

    static final class Jogador {
        final UUID id;
        final String nome;
        boolean jogando, vivo;
        int vidas, moedas;
        Planta.Armario armario;
        long escondidoDesde, graca, proximoSussurro;
        boolean avisouArmario;
        /** O Retrato: ha quantos ciclos esta encarando. A Campainha: ate quando nao leva outro castigo. */
        int encarando;
        long campCastigo;
        ItemDisplay sussurro;
        long sussurroAte;
        Location ultima;
        long reviverAte = -1;

        Jogador(Player p) {
            this.id = p.getUniqueId();
            this.nome = p.getName();
        }
    }

    private final Hotel pl;
    final World mundo;
    final Map<UUID, Jogador> jogadores = new LinkedHashMap<>();
    private final Random rnd = new Random();

    Estado estado = Estado.ESPERANDO;
    private int contagem;
    /** ciclo: 4 por segundo. rtick: 20 por segundo. */
    private long tick, rtick, inicioTick;
    private int numPartida;
    Planta planta;
    /** Quantas portas ja foram abertas (= indice do comodo mais novo aberto). */
    int abertas;
    private int construidos = -1;
    private int ultimoEvento;
    private int livros;
    private final Set<Long> gavetasVistas = new HashSet<>();
    private final Set<Long> luzesQuebradas = new HashSet<>();
    private final Set<Integer> destrancadas = new HashSet<>();
    private final List<Entity> criadas = new ArrayList<>();
    private final Map<Integer, ItemDisplay> olhos = new HashMap<>();
    private final List<ItemDisplay> olhosParede = new ArrayList<>();
    private final List<Item> livrosNoChao = new ArrayList<>();
    private Investida investida;
    private Figura figura;
    private Perseguidor perseguidor;
    private boolean corridaFeita;
    /** O Retrato (quadros com rosto) e a Campainha (o Gerente): monstros nossos, que nao existem no Doors. */
    private final Map<Integer, ItemDisplay> retratos = new HashMap<>();
    private final Set<Integer> retratosAcordados = new HashSet<>();
    private ItemDisplay retratoVoando;
    private UUID retratoAlvo;
    private long retratoAte;
    private long campComeca = -1, campAte = -1;
    private Elevador elevador;
    private final Map<UUID, Location> campPos = new HashMap<>();

    Partida(Hotel pl, World mundo) {
        this.pl = pl;
        this.mundo = mundo;
    }

    private static long seg(double s) {
        return Math.round(s * 4);
    }

    int portas() {
        return Math.max(10, Math.min(120, pl.getConfig().getInt("portas", 50)));
    }

    int livrosPrecisa() {
        return Math.max(1, pl.getConfig().getInt("livros", 5));
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
        tirarSussurro(j);
        p.setWalkSpeed(0.2f);
        pl.devolver(p);
        if (estado == Estado.JOGANDO && j.jogando && j.vivo) avisar("§7" + j.nome + " §7fugiu do hotel.");
        return true;
    }

    boolean participa(Player p) {
        return jogadores.containsKey(p.getUniqueId());
    }

    boolean vivo(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        return j != null && j.jogando && j.vivo && estado == Estado.JOGANDO;
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
            if (!j.jogando || !j.vivo) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) l.add(p);
        }
        return l;
    }

    void avisar(String msg) {
        for (Player p : online(false)) p.sendMessage(Hotel.PREFIXO + msg);
    }

    void titulo(Player p, String t, String s, double segundos) {
        p.showTitle(Title.title(Hotel.txt(t), Hotel.txt(s),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis((long) (segundos * 1000)), Duration.ofMillis(400))));
    }

    String estadoTexto() {
        return switch (estado) {
            case ESPERANDO -> "esperando jogadores";
            case CONTAGEM -> "começando";
            case JOGANDO -> "em andamento (porta " + String.format("%04d", abertas) + ", " + vivos().size() + " vivos)";
            case FIM -> "terminando";
        };
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
                    avisar("§eO elevador desce em §f" + (contagem / 4) + "s§e. Chame os amigos: §f/hotel entrar");
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
        if (fila.size() >= pl.getConfig().getInt("max-jogadores", 8)) contagem = (int) Math.min(contagem, seg(8));
        contagem--;
        if (contagem % 4 == 0) {
            int s = contagem / 4;
            for (Player p : fila) {
                p.sendActionBar(Hotel.txt("§7O elevador desce em §e" + s + "s §8| §7hóspedes: §f" + fila.size()));
                if (s <= 5 && s > 0) {
                    titulo(p, "§4" + s, "§7o elevador vai descer...", 0.9);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.5f);
                }
            }
        }
        if (contagem <= 0) iniciar();
    }

    private void hudFila() {
        if (tick % 4 != 0) return;
        for (Player p : naFila()) {
            String s = estado == Estado.JOGANDO ? "§7Partida em andamento: §eporta " + String.format("%04d", abertas) + " §8| §7você joga a próxima"
                    : "§7Esperando hóspedes... §8| §7na fila: §f" + naFila().size();
            p.sendActionBar(Hotel.txt(s));
        }
    }

    // ================================================================== inicio

    void iniciar() {
        List<Player> fila = naFila();
        if (fila.isEmpty()) {
            estado = Estado.ESPERANDO;
            return;
        }
        limparEntidades();
        numPartida++;
        int portas = portas();
        int corrida = pl.getConfig().getBoolean("perseguicao", true) && portas >= 30 ? Math.max(12, portas / 2 + 2 + rnd.nextInt(5)) : 0;
        planta = new Planta(100 + numPartida * 64, rnd.nextLong(), portas, corrida);
        abertas = 0;
        construidos = -1;
        ultimoEvento = 0;
        livros = 0;
        corridaFeita = false;
        gavetasVistas.clear();
        luzesQuebradas.clear();
        destrancadas.clear();
        construirAte(3);
        estado = Estado.JOGANDO;
        inicioTick = tick;
        int i = 0;
        for (Player p : fila) {
            Jogador j = jogadores.get(p.getUniqueId());
            j.jogando = true;
            j.vivo = true;
            j.vidas = Math.max(1, pl.getConfig().getInt("vidas", 3));
            j.moedas = 0;
            j.armario = null;
            j.graca = rtick + 100;
            j.proximoSussurro = 0;
            Guarda.limpar(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().addItem(vitamina());
            j.graca = rtick + 100 + Elevador.DURA;
            pl.mover(p, noElevador(planta.comodo(0), i++));
        }
        if (elevador != null) elevador.acabar(false);
        elevador = new Elevador(planta.comodo(0));
        avisar("§cO elevador está descendo com §f" + fila.size() + " §chóspede(s)...");
    }

    void entrarNoMeio(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || estado != Estado.JOGANDO) return;
        j.jogando = true;
        j.vivo = true;
        j.vidas = Math.max(1, pl.getConfig().getInt("vidas", 3));
        j.graca = rtick + 100;
        Guarda.limpar(p);
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().addItem(vitamina());
        pl.mover(p, lugarSeguro(p));
        titulo(p, "§4§lO HOTEL", "§7Vocês estão na porta §f" + String.format("%04d", abertas), 3);
        avisar("§f" + p.getName() + " §7entrou no hotel.");
    }

    Location entrada(Planta.Comodo c, int i) {
        double z = c.zE + 0.5 + (i % 3 - 1) * 0.9;
        double x = c.x0 + 1.6 + (i / 3) * 0.9;
        if (c.idx == 0) {
            x = c.x0 + 2.5 + (i / 3) * 0.9;
        }
        return new Location(mundo, x, Planta.Y, z, -90f, 0f);
    }

    /** Onde quem morreu volta: no comeco do comodo mais novo aberto (ou depois da perseguicao). */
    Location lugarSeguro(Player p) {
        if (planta == null) return Recepcao.spawn(mundo);
        int idx = abertas;
        if (perseguidor != null && planta.corrida > 0) idx = Math.min(construidos, planta.corrida + Planta.CORRIDA_SALAS);
        Planta.Comodo c = planta.comodo(Math.max(0, idx));
        return entrada(c, rnd.nextInt(3));
    }

    ItemStack vitamina() {
        return pl.item(Material.HONEY_BOTTLE, "vitamina", "§a§lVitamina", "§7Clique com o botão direito:", "§7corre mais rápido por 8s e cura um pouco.");
    }

    // ================================================================== montar os comodos no mundo

    private void construirAte(int idx) {
        planta.gerarAte(idx);
        idx = Math.min(idx, planta.comodos.size() - 1);
        while (construidos < idx) {
            construidos++;
            Planta.Comodo c = planta.comodo(construidos);
            aplicar(c);
            prepararComodo(c);
        }
    }

    private void aplicar(Planta.Comodo c) {
        for (int cx = c.x0 >> 4; cx <= c.x1 >> 4; cx++)
            for (int cz = c.z0 >> 4; cz <= c.z1 >> 4; cz++) mundo.getChunkAt(cx, cz).load(true);
        Map<String, BlockData> cache = new HashMap<>();
        for (Planta.Bloco b : c.blocos) {
            String k = b.m().name() + "|" + b.estado();
            BlockData d = cache.computeIfAbsent(k, x -> b.estado().isEmpty() ? b.m().createBlockData()
                    : Bukkit.createBlockData(b.m(), "[" + b.estado() + "]"));
            mundo.getBlockAt(b.x(), b.y(), b.z()).setBlockData(d, false);
        }
        // numero da porta
        if (c.tipo != Planta.Tipo.SAIDA) {
            Location l = new Location(mundo, c.x1 - 0.06, Planta.Y + 2.55, c.zS + 0.5);
            TextDisplay t = mundo.spawn(l, TextDisplay.class, x -> {
                x.text(Hotel.txt((c.tipo == Planta.Tipo.BIBLIOTECA ? "§4§l" : "§6§l") + String.format("%04d", c.porta())));
                x.setBillboard(Display.Billboard.VERTICAL);
                x.setBrightness(new Display.Brightness(12, 12));
                x.setShadowed(true);
                x.setPersistent(false);
                x.setBackgroundColor(Color.fromARGB(160, 20, 10, 5));
                x.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
            });
            criadas.add(t);
        }
        if (c.tipo == Planta.Tipo.SAIDA) {
            Location l = new Location(mundo, c.meioX(), Planta.Y + 2.8, c.meioZ());
            criadas.add(mundo.spawn(l, TextDisplay.class, x -> {
                x.text(Hotel.txt("§a§lSAÍDA\n§fvocê sobreviveu ao hotel"));
                x.setBillboard(Display.Billboard.CENTER);
                x.setBrightness(new Display.Brightness(15, 15));
                x.setPersistent(false);
            }));
        }
    }

    /** Decide o que tem no comodo: porta trancada (chave numa gaveta), os Olhos. */
    private void prepararComodo(Planta.Comodo c) {
        boolean especial = c.tipo == Planta.Tipo.INICIO || c.tipo == Planta.Tipo.BIBLIOTECA || c.tipo == Planta.Tipo.SAIDA
                || c.tipo == Planta.Tipo.CORRIDA || planta.naCorrida(c.idx + 1) || c.idx + 1 == planta.corrida;
        if (!especial && c.idx >= 4 && !c.gavetas.isEmpty() && rnd.nextDouble() < pl.getConfig().getDouble("chance-trancada", 0.2)) {
            c.trancado = true;
            c.chave = c.gavetas.get(rnd.nextInt(c.gavetas.size()));
        }
        if (c.tipo == Planta.Tipo.BIBLIOTECA) c.trancado = true;
        if (!especial && c.idx >= 7 && !c.escuro && rnd.nextDouble() < pl.getConfig().getDouble("chance-olhos", 0.08)) criarOlhos(c);
        if (c.retrato != null && !olhos.containsKey(c.idx)) criarRetrato(c);
    }

    // ================================================================== durante a partida (4x por segundo)

    private void jogar() {
        List<Player> vivos = vivos();
        if (vivos.isEmpty()) {
            if (alguemPodeVoltar()) return;
            derrota();
            return;
        }
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            Location l = p.getLocation();
            if (l.getY() < Planta.Y - 6 || !p.getWorld().equals(mundo)) {
                pl.mover(p, lugarSeguro(p));
                continue;
            }
            Planta.Comodo c = planta.onde(l.getX(), l.getZ());
            if (c != null && c.tipo == Planta.Tipo.SAIDA) {
                vitoria();
                return;
            }
            armarioDemais(p, j);
            olhando(p, j, c);
            sussurro(p, j, c);
            retrato(p, j, c);
            if (c != null && c.tipo == Planta.Tipo.BIBLIOTECA) pegarLivro(p);
            if (figura != null && c != null && c.tipo == Planta.Tipo.BIBLIOTECA) figura.ouvir(p, j);
            if (perseguidor == null && !corridaFeita && planta.corrida > 0 && c != null && c.idx == planta.corrida && l.getX() > c.x0 + 2.5)
                comecarCorrida();
            j.ultima = l.clone();
        }
        if (figura != null) figura.ciclo(vivos);
        for (ItemDisplay d : olhosParede) virarPara(d, vivos);
        ambiente(vivos);
        if (tick % 2 == 0) hud();
    }

    // ================================================================== a cada tick: o que anda rapido

    void rapido() {
        rtick++;
        if (estado != Estado.JOGANDO) return;
        if (elevador != null && !elevador.tick()) elevador = null;
        if (investida != null && !investida.tick()) investida = null;
        if (perseguidor != null && !perseguidor.tick()) {
            perseguidor = null;
            corridaFeita = true;
        }
        if (figura != null) figura.sincronizar();
        for (ItemDisplay d : olhos.values()) if (d.isValid()) girar(d, rtick * 0.04f, ESC_OLHOS);
        voarRetrato();
        campainha();
    }

    // ================================================================== cliques

    void clicouBloco(Player p, Block b) {
        if (planta == null) return;
        Material m = b.getType();
        if (m == Material.DARK_OAK_DOOR) {
            Planta.Comodo c = portaDe(b);
            if (c != null) porta(p, c);
            else {
                p.playSound(b.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.8f, 0.6f);
                p.sendActionBar(Hotel.txt("§7Trancada. §8(não é o caminho)"));
            }
        } else if (m == Material.SPRUCE_DOOR) {
            armarioClique(p, b);
        } else if (m == Material.BARREL) {
            gaveta(p, b);
        }
    }

    private Planta.Comodo portaDe(Block b) {
        int y = b.getY();
        if (y != Planta.Y && y != Planta.Y + 1) return null;
        for (Planta.Comodo c : planta.comodos) if (b.getX() == c.x1 && b.getZ() == c.zS && c.tipo != Planta.Tipo.SAIDA) return c;
        return null;
    }

    private void porta(Player p, Planta.Comodo c) {
        if (c.idx < abertas) return; // ja aberta
        if (c.idx > abertas) return;
        if (c.tipo == Planta.Tipo.BIBLIOTECA && livros < livrosPrecisa()) {
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 0.6f);
            p.sendActionBar(Hotel.txt("§cA porta não abre. §fAche os §e" + livrosPrecisa() + " livros§f da biblioteca §7(" + livros + "/" + livrosPrecisa() + ")"));
            if (figura != null) figura.barulho(p.getLocation(), 14);
            return;
        }
        if (c.trancado && !destrancadas.contains(c.idx)) {
            String chave = "chave:" + c.porta();
            ItemStack achada = null;
            for (ItemStack it : p.getInventory().getContents()) if (chave.equals(pl.tipoItem(it))) achada = it;
            if (achada == null) {
                p.playSound(c.idx == 0 ? p.getLocation() : new Location(mundo, c.x1, Planta.Y, c.zS), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1f, 0.5f);
                p.sendActionBar(Hotel.txt("§cTrancada. §fA chave da porta §e" + String.format("%04d", c.porta()) + " §festá numa §egaveta §f(barril) deste cômodo."));
                return;
            }
            achada.setAmount(achada.getAmount() - 1);
            destrancadas.add(c.idx);
            mundo.playSound(new Location(mundo, c.x1, Planta.Y, c.zS), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 1.4f);
        }
        abrirPorta(c);
        abertas = c.idx + 1;
        construirAte(abertas + 3);
        eventoAoAbrir(abertas, p);
    }

    private void abrirPorta(Planta.Comodo c) {
        for (int y = Planta.Y; y <= Planta.Y + 1; y++) {
            Block b = mundo.getBlockAt(c.x1, y, c.zS);
            if (b.getBlockData() instanceof Openable o) {
                o.setOpen(true);
                b.setBlockData(o, false);
            }
        }
        Location l = new Location(mundo, c.x1 + 0.5, Planta.Y + 1, c.zS + 0.5);
        mundo.playSound(l, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 0.7f);
        mundo.playSound(l, Sound.BLOCK_WOOD_BREAK, 0.4f, 0.5f);
    }

    private void fecharPorta(Planta.Comodo c) {
        for (int y = Planta.Y; y <= Planta.Y + 1; y++) {
            Block b = mundo.getBlockAt(c.x1, y, c.zS);
            if (b.getBlockData() instanceof Openable o) {
                o.setOpen(false);
                b.setBlockData(o, false);
            }
        }
        mundo.playSound(new Location(mundo, c.x1 + 0.5, Planta.Y + 1, c.zS + 0.5), Sound.BLOCK_WOODEN_DOOR_CLOSE, 2f, 0.5f);
    }

    // ---------------------------------------------------------------- armarios

    private Planta.Comodo comodoDoBloco(Block b) {
        return planta.onde(b.getX() + 0.5, b.getZ() + 0.5);
    }

    private void armarioClique(Player p, Block b) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null) return;
        if (j.armario != null) {
            sairDoArmario(p, j, false);
            return;
        }
        Planta.Comodo c = comodoDoBloco(b);
        if (c == null) return;
        for (Planta.Armario a : c.armarios) {
            if (a.x() != b.getX() || a.portaZ() != b.getZ()) continue;
            for (Jogador o : jogadores.values()) {
                if (o.armario != null && o.armario.x() == a.x() && o.armario.z() == a.z()) {
                    p.sendActionBar(Hotel.txt("§7Já tem alguém aí dentro."));
                    return;
                }
            }
            j.armario = a;
            j.escondidoDesde = rtick;
            j.avisouArmario = false;
            Location dentro = new Location(mundo, a.x() + 0.5, Planta.Y, a.z() + 0.5, a.norte() ? 0f : 180f, 0f);
            pl.mover(p, dentro);
            p.playSound(dentro, Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.8f, 1.1f);
            p.sendActionBar(Hotel.txt("§7Escondido. §fAgache §7(ou clique na porta) para sair."));
            return;
        }
    }

    private void sairDoArmario(Player p, Jogador j, boolean empurrado) {
        Planta.Armario a = j.armario;
        if (a == null) return;
        j.armario = null;
        Location fora = new Location(mundo, a.x() + 0.5, Planta.Y, a.foraZ() + 0.5, a.norte() ? 0f : 180f, 0f);
        pl.mover(p, fora);
        p.playSound(fora, Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8f, empurrado ? 0.6f : 1.1f);
    }

    void agachou(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j != null && j.armario != null && vivo(p)) sairDoArmario(p, j, false);
    }

    boolean escondido(Jogador j) {
        return j.armario != null;
    }

    /** Ninguem fica no armario para sempre: depois de um tempo algo la dentro te empurra para fora. */
    private void armarioDemais(Player p, Jogador j) {
        if (j.armario == null) return;
        if (investida != null) {
            j.escondidoDesde = rtick;
            return;
        }
        long dentro = rtick - j.escondidoDesde;
        int limite = pl.getConfig().getInt("armario-segundos", 22) * 20;
        if (dentro > limite - 100 && !j.avisouArmario) {
            j.avisouArmario = true;
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.2f);
            p.sendActionBar(Hotel.txt("§cTem algo aqui dentro com você... SAIA!"));
        }
        if (dentro > limite) {
            sairDoArmario(p, j, true);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.6f, 0.5f);
            titulo(p, "", "§c§lSAI DAQUI!", 1);
            machucar(p, 4, "algo no armário");
        }
    }

    // ---------------------------------------------------------------- gavetas

    private void gaveta(Player p, Block b) {
        long k = Planta.chave2(b.getX(), b.getZ()) ^ ((long) b.getY() << 50);
        Planta.Comodo c = comodoDoBloco(b);
        if (c == null) return;
        if (!gavetasVistas.add(k)) {
            p.sendActionBar(Hotel.txt("§7Vazia."));
            return;
        }
        BlockData d = b.getBlockData();
        if (d instanceof org.bukkit.block.data.type.Barrel br) {
            br.setOpen(true);
            b.setBlockData(br, false);
        }
        p.playSound(b.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 0.9f);
        if (c.trancado && c.chave != null && c.chave[0] == b.getX() && c.chave[2] == b.getZ()) {
            p.getInventory().addItem(pl.item(Material.TRIPWIRE_HOOK, "chave:" + c.porta(), "§6§lChave da porta " + String.format("%04d", c.porta()),
                    "§7Clique na porta trancada."));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
            avisar("§f" + p.getName() + " §7achou a §6chave da porta " + String.format("%04d", c.porta()) + "§7.");
            return;
        }
        double q = rnd.nextDouble();
        if (q < 0.18) {
            p.getInventory().addItem(vitamina());
            p.sendActionBar(Hotel.txt("§aAchou uma vitamina!"));
        } else if (q < 0.50) {
            Jogador j = jogadores.get(p.getUniqueId());
            int n = 1 + rnd.nextInt(5);
            if (j != null) j.moedas += n;
            p.sendActionBar(Hotel.txt("§6+" + n + " moedas §7(total " + (j == null ? n : j.moedas) + ")"));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
        } else if (q < 0.53) {
            // susto: algo pula da gaveta
            p.playSound(p.getLocation(), Sound.ENTITY_SILVERFISH_HURT, 1f, 0.5f);
            mundo.spawnParticle(Particle.SQUID_INK, b.getLocation().add(0.5, 1, 0.5), 15, 0.2, 0.3, 0.2, 0.05);
            p.sendActionBar(Hotel.txt("§8...algo saiu correndo da gaveta."));
        } else p.sendActionBar(Hotel.txt("§7Nada aqui."));
    }

    void usarItem(Player p, ItemStack it) {
        String t = pl.tipoItem(it);
        if (!"vitamina".equals(t)) return;
        it.setAmount(it.getAmount() - 1);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, false, false, true));
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + 6));
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1.2f);
        p.sendActionBar(Hotel.txt("§aVitamina! §7Mais rápido por 8s."));
    }

    // ================================================================== morte e vidas

    /** Dano do jogo (pode tirar uma vida). */
    void machucar(Player p, double dano, String quem) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.vivo || rtick < j.graca) return;
        if (p.getHealth() - dano <= 0.5) {
            morrer(p, quem);
            return;
        }
        p.damage(dano);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
    }

    void morrer(Player p, String quem) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.vivo || rtick < j.graca) return;
        if (j.armario != null) j.armario = null;
        tirarSussurro(j);
        j.vidas--;
        j.graca = rtick + 100;
        pl.getLogger().info("Hotel: " + p.getName() + " foi pego por " + quem + " na porta " + abertas + " (vidas " + j.vidas + ")");
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.4f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, false));
        for (Player o : online(true)) if (o != p) o.playSound(o.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.4f, 0.6f);
        if (j.vidas > 0) {
            titulo(p, "§4☠", "§c" + quem + " te pegou §7(" + j.vidas + " vida" + (j.vidas == 1 ? "" : "s") + ")", 2);
            avisar("§c☠ §f" + j.nome + " §cfoi pego por " + quem + "§c. §7(" + j.vidas + " vida" + (j.vidas == 1 ? "" : "s") + ")");
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (p.isOnline() && vivo(p)) pl.mover(p, lugarSeguro(p));
            }, 30L);
            return;
        }
        j.vivo = false;
        int espera = Reviver.segundos(pl);
        if (Reviver.moedas(p) > 0) j.reviverAte = tick + seg(espera + 4);
        titulo(p, "§4§lVOCÊ MORREU", "§c" + quem + " te pegou", 3);
        int restam = vivos().size();
        avisar("§4☠ §f" + j.nome + " §4morreu (" + quem + "). §7(" + restam + " vivo" + (restam == 1 ? "" : "s") + ")");
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !participa(p) || j.vivo) return;
            p.setGameMode(GameMode.SPECTATOR);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            p.sendMessage(Hotel.PREFIXO + "§7Agora você assiste. §e/hotel sair §7para voltar.");
            if (estado != Estado.JOGANDO) return;
            if (Reviver.oferecer(pl, p, "o Hotel", espera, () -> reviver(p))) j.reviverAte = tick + seg(espera + 2);
            else j.reviverAte = -1;
        }, 40L);
    }

    /** Teste (admin): o jogador morre de vez agora. */
    boolean eliminarTeste(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.vivo || estado != Estado.JOGANDO) return false;
        j.vidas = 1;
        j.graca = 0;
        morrer(p, "o teste");
        return true;
    }

    /** Alguem que morreu ainda pode gastar a Moeda de Reviver (a partida espera). */
    private boolean alguemPodeVoltar() {
        for (Jogador j : jogadores.values()) {
            if (!j.jogando || j.vivo || j.reviverAte < tick) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    /** Moeda de Reviver: volta com 1 vida no comodo mais novo aberto. */
    boolean reviver(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.jogando || j.vivo || estado != Estado.JOGANDO || !p.isOnline()) return false;
        j.vivo = true;
        j.vidas = 1;
        j.armario = null;
        j.reviverAte = -1;
        j.graca = rtick + 100;
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setGameMode(GameMode.ADVENTURE);
        pl.mover(p, lugarSeguro(p));
        titulo(p, "§a§lVOCÊ VOLTOU!", "§7Porta §f" + String.format("%04d", abertas) + " §7— você tem §f1 vida§7.", 3);
        avisar("§e✦ §f" + j.nome + " §eusou uma Moeda de Reviver e voltou para o hotel!");
        return true;
    }

    // ================================================================== eventos ao abrir porta

    private void eventoAoAbrir(int idx, Player quem) {
        Planta.Comodo c = planta.comodo(idx);
        if (c == null) return;
        if (c.tipo == Planta.Tipo.BIBLIOTECA) {
            iniciarBiblioteca(c);
            return;
        }
        if (c.tipo == Planta.Tipo.SAIDA) return;
        if (planta.corrida > 0 && idx == planta.corrida - 2) olhosNasParedes();
        if (planta.naCorrida(idx) || planta.corrida > 0 && idx >= planta.corrida - 2 && idx < planta.corrida) return;
        if (c.escuro) for (Player p : vivos()) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j != null && j.proximoSussurro == 0) j.proximoSussurro = rtick + 160 + rnd.nextInt(200);
        }
        if (investida != null || campComeca >= 0 || idx < 5 || idx - ultimoEvento < 3) return;
        double q = rnd.nextDouble();
        double pr = pl.getConfig().getDouble("chance-retorno", 0.08), pe = pl.getConfig().getDouble("chance-estrondo", 0.17);
        double pc = pl.getConfig().getDouble("chance-campainha", 0.07);
        if (idx >= 15 && q < pr) {
            ultimoEvento = idx;
            investida = new Investida(true, 2 + rnd.nextInt(3));
        } else if (q < pr + pe) {
            ultimoEvento = idx;
            investida = new Investida(false, 1);
        } else if (idx >= 20 && q < pr + pe + pc) {
            ultimoEvento = idx;
            iniciarCampainha();
        }
    }

    String testeEvento(String nome) {
        if (estado != Estado.JOGANDO || planta == null) return "Sem partida.";
        switch (nome) {
            case "estrondo" -> investida = new Investida(false, 1);
            case "retorno" -> investida = new Investida(true, 3);
            case "olhos" -> criarOlhos(planta.comodo(Math.min(construidos, abertas)));
            case "campainha" -> iniciarCampainha();
            case "sussurro" -> {
                for (Player p : vivos()) {
                    Jogador j = jogadores.get(p.getUniqueId());
                    criarSussurro(p, j);
                }
            }
            default -> {
                return "?";
            }
        }
        return "Evento " + nome + " ligado.";
    }

    String pularPara(int n) {
        if (estado != Estado.JOGANDO || planta == null) return "Sem partida.";
        n = Math.max(1, Math.min(portas(), n));
        construirAte(n + 2);
        while (abertas < n) {
            Planta.Comodo c = planta.comodo(abertas);
            if (c.tipo == Planta.Tipo.BIBLIOTECA) livros = livrosPrecisa();
            abrirPorta(c);
            abertas++;
        }
        construirAte(abertas + 3);
        for (Player p : vivos()) pl.mover(p, entrada(planta.comodo(abertas), 0));
        eventoAoAbrir(abertas, null);
        return "Pulado para a porta " + n + ".";
    }

    // ---------------------------------------------------------------- luzes

    private long chaveLuz(int[] l) {
        return Planta.chave2(l[0], l[2]) ^ ((long) l[1] << 50);
    }

    private void luz(int[] l, boolean acesa) {
        if (luzesQuebradas.contains(chaveLuz(l))) return;
        Block b = mundo.getBlockAt(l[0], l[1], l[2]);
        if (acesa) {
            if (b.getType() != Material.LANTERN) b.setBlockData(Bukkit.createBlockData("minecraft:lantern[hanging=true]"), false);
        } else if (b.getType() == Material.LANTERN) b.setType(Material.AIR, false);
    }

    private void quebrarLuzes(Planta.Comodo c) {
        for (int[] l : c.luzes) {
            if (rnd.nextDouble() < 0.25) continue;
            luz(l, false);
            luzesQuebradas.add(chaveLuz(l));
            mundo.spawnParticle(Particle.BLOCK, l[0] + 0.5, l[1] + 0.5, l[2] + 0.5, 8, 0.2, 0.2, 0.2, 0, Material.GLASS.createBlockData());
        }
    }

    // ================================================================== Estrondo e Retorno

    /** Varre os comodos do fundo ate a porta mais nova. Quem nao estiver num armario e pego. */
    final class Investida {
        final boolean retorno;
        int passes;
        double x, xIni, xFim;
        int dir = 1;
        long avisoAte, pausaAte = -1;
        ItemDisplay cabeca;
        final List<BlockDisplay> nevoa = new ArrayList<>();
        int de;

        Investida(boolean retorno, int passes) {
            this.retorno = retorno;
            this.passes = passes;
            this.avisoAte = rtick + (retorno ? 90 : 70) + rnd.nextInt(40);
            this.de = Math.max(0, abertas - 4);
        }

        /** Retorna false quando acabou. */
        boolean tick() {
            if (planta == null) return false;
            long falta = avisoAte - rtick;
            if (falta > 0) {
                avisoLuzes(falta);
                return true;
            }
            if (cabeca == null) comecar();
            if (pausaAte > 0) {
                if (rtick < pausaAte) return true;
                pausaAte = -1;
                Sons.local(mundo, posicao(), 90, "retorno", 3f, 1f, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.6f);
            }
            double vel = retorno ? 1.7 : 1.3;
            x += dir * vel;
            Location pos = posicao();
            mover(pos);
            pegar(pos);
            if (rtick % 3 == 0) {
                mundo.playSound(pos, retorno ? Sound.ENTITY_ZOGLIN_ANGRY : Sound.ENTITY_RAVAGER_ROAR, 3.5f, retorno ? 0.5f : 0.45f);
                if (rtick % 9 == 0) Sons.local(mundo, pos, 80, retorno ? "retorno" : "estrondo", 2.5f, 1f, Sound.ENTITY_GHAST_SCREAM, 2.5f, 0.45f);
            }
            if (dir > 0 && x >= xFim || dir < 0 && x <= xIni) {
                passes--;
                if (passes > 0) {
                    dir = -dir;
                    pausaAte = rtick + 40 + rnd.nextInt(40);
                    return true;
                }
                if (!retorno) for (int i = de; i <= Math.min(construidos, abertas); i++) quebrarLuzes(planta.comodo(i));
                acabar();
                return false;
            }
            return true;
        }

        private void avisoLuzes(long falta) {
            if (rtick % 3 == 0) {
                for (int i = Math.max(0, abertas - 2); i <= Math.min(construidos, abertas + 1); i++)
                    for (int[] l : planta.comodo(i).luzes) luz(l, rnd.nextDouble() < 0.45);
            }
            if (falta % 20 == 0) {
                for (Player p : vivos()) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_TENDRIL_CLICKS, 0.6f, 0.5f);
                    p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.15f, 0.5f);
                }
            }
            if (falta == 30) for (Player p : vivos()) {
                Sons.para(p, p.getLocation(), retorno ? "retorno" : "estrondo", 0.6f, 1f, Sound.ENTITY_GHAST_SCREAM, 0.4f, 0.4f);
                if (pl.getConfig().getBoolean("dica-armario", true)) p.sendActionBar(Hotel.txt("§8(as luzes piscam... §7esconda-se num armário§8)"));
            }
        }

        private void comecar() {
            for (int i = Math.max(0, abertas - 2); i <= Math.min(construidos, abertas + 1); i++)
                for (int[] l : planta.comodo(i).luzes) luz(l, true);
            Planta.Comodo ini = planta.comodo(de), fim = planta.comodo(Math.min(construidos, abertas));
            xIni = ini.x0 + 1;
            xFim = fim.x1 - 1;
            x = xIni;
            Location pos = posicao();
            cabeca = mundo.spawn(pos, ItemDisplay.class, d -> {
                d.setItemStack(Skins.cabeca(retorno ? "retorno" : "estrondo"));
                d.setBrightness(new Display.Brightness(10, 10));
                d.setTeleportDuration(1);
                d.setPersistent(false);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY((float) Math.toRadians(-90)), new Vector3f(escala()), new Quaternionf()));
                d.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
            });
            criadas.add(cabeca);
            Visual.gemeo(cabeca, retorno ? "retorno" : "estrondo");
            for (int i = 0; i < 4; i++) {
                BlockDisplay b = mundo.spawn(pos, BlockDisplay.class, d -> {
                    d.setBlock(Material.BLACK_STAINED_GLASS.createBlockData());
                    d.setTeleportDuration(1);
                    d.setPersistent(false);
                    d.setBrightness(new Display.Brightness(0, 0));
                    d.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
                });
                nevoa.add(b);
                criadas.add(b);
            }
            Sons.local(mundo, pos, 120, retorno ? "retorno" : "estrondo", 3f, 1f, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);
        }

        float escala() {
            return retorno ? ESC_RETORNO : ESC_ESTRONDO;
        }

        /** Centro do monstro (a cabeca fica na metade de cima do corredor). */
        Location posicao() {
            Planta.Comodo c = comodoPorX(x);
            double z = c == null ? planta.laneZ : c.zNoCaminho(x);
            return new Location(mundo, x, Planta.Y + 0.6 + escala() * 0.25, z);
        }

        private void mover(Location pos) {
            if (cabeca.isValid()) {
                cabeca.teleport(pos.clone().add(0, escala() * 0.25 + Math.sin(rtick * 0.5) * 0.15, 0));
                float yaw = dir > 0 ? -90 : 90;
                cabeca.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY((float) Math.toRadians(yaw + Math.sin(rtick * 0.9) * 8)),
                        new Vector3f(escala()), new Quaternionf()));
            }
            for (int i = 0; i < nevoa.size(); i++) {
                BlockDisplay b = nevoa.get(i);
                if (!b.isValid()) continue;
                b.teleport(pos);
                float s = 2.2f + (float) Math.sin(rtick * 0.3 + i) * 0.4f;
                Quaternionf q = new Quaternionf().rotationXYZ(rtick * 0.07f * (i + 1), rtick * 0.05f * (i + 2), i);
                Vector3f t = new Vector3f(-s / 2, -s / 2, -s / 2).rotate(q);
                b.setInterpolationDelay(0);
                b.setInterpolationDuration(1);
                b.setTransformation(new Transformation(t, q, new Vector3f(s), new Quaternionf()));
            }
            mundo.spawnParticle(Particle.SQUID_INK, pos, 10, 0.9, 0.9, 0.9, 0.05);
            mundo.spawnParticle(Particle.LARGE_SMOKE, pos, 5, 1.0, 1.0, 1.0, 0.02);
            if (retorno) mundo.spawnParticle(Particle.DUST, pos, 8, 1.0, 1.0, 1.0, 0, new Particle.DustOptions(Color.fromRGB(40, 200, 60), 2f));
            else mundo.spawnParticle(Particle.DUST, pos, 6, 1.0, 1.0, 1.0, 0, new Particle.DustOptions(Color.fromRGB(10, 10, 10), 2.5f));
        }

        private void pegar(Location pos) {
            for (Player p : vivos()) {
                Jogador j = jogadores.get(p.getUniqueId());
                if (j == null || escondido(j)) continue;
                Location l = p.getLocation();
                if (Math.abs(l.getX() - pos.getX()) < 2.4 && Math.abs(l.getZ() - pos.getZ()) < 14 && Math.abs(l.getY() - Planta.Y) < 4)
                    morrer(p, retorno ? "o Retorno" : "o Estrondo");
            }
        }

        void acabar() {
            if (cabeca != null) cabeca.remove();
            for (BlockDisplay b : nevoa) b.remove();
            for (Player p : vivos()) p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.4f, 0.5f);
        }
    }

    Planta.Comodo comodoPorX(double x) {
        if (planta == null) return null;
        for (Planta.Comodo c : planta.comodos) if (x >= c.x0 && x < c.x1) return c;
        return planta.comodos.isEmpty() ? null : planta.comodos.get(planta.comodos.size() - 1);
    }

    // ================================================================== Os Olhos

    private void criarOlhos(Planta.Comodo c) {
        if (c == null || olhos.containsKey(c.idx)) return;
        Location l = new Location(mundo, c.meioX(), Planta.Y + 2.1 + ESC_OLHOS * 0.25, c.meioZ());
        ItemDisplay d = mundo.spawn(l, ItemDisplay.class, x -> {
            x.setItemStack(Skins.cabeca("olhos"));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setGlowing(true);
            x.setGlowColorOverride(Color.fromRGB(170, 40, 255));
            x.setPersistent(false);
            x.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
        });
        girar(d, 0, ESC_OLHOS);
        olhos.put(c.idx, d);
        criadas.add(d);
        Visual.gemeo(d, "olhos");
        // o comodo fica numa luz roxa fraca
        for (int[] lz : c.luzes) {
            luzesQuebradas.add(chaveLuz(lz));
            mundo.getBlockAt(lz[0], lz[1], lz[2]).setBlockData(Bukkit.createBlockData("minecraft:soul_lantern[hanging=true]"), false);
        }
    }

    private void girar(ItemDisplay d, float ang, float s) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(1);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(ang), new Vector3f(s), new Quaternionf()));
    }

    /** Quem olha para os Olhos se machuca. */
    private void olhando(Player p, Jogador j, Planta.Comodo c) {
        if (c == null || escondido(j)) return;
        ItemDisplay d = olhos.get(c.idx);
        if (d == null || !d.isValid()) return;
        mundo.spawnParticle(Particle.DUST, d.getLocation(), 3, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(Color.fromRGB(170, 40, 255), 1.4f));
        if (tick % 8 == 0) p.playSound(d.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.7f, 0.5f);
        Location olho = p.getEyeLocation();
        Vector para = d.getLocation().toVector().subtract(olho.toVector());
        if (para.lengthSquared() < 0.01) return;
        double ang = Math.toDegrees(olho.getDirection().angle(para.normalize()));
        if (ang < 24 && p.hasLineOfSight(d)) {
            p.playSound(p.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.8f, 0.5f);
            p.sendActionBar(Hotel.txt("§5§lNÃO OLHE PARA OS OLHOS!"));
            machucar(p, 1.5, "os Olhos");
        }
    }

    // ================================================================== O Sussurro (no escuro)

    private void sussurro(Player p, Jogador j, Planta.Comodo c) {
        if (j.sussurro != null) {
            if (!j.sussurro.isValid()) {
                j.sussurro = null;
                return;
            }
            Location olho = p.getEyeLocation();
            Vector para = j.sussurro.getLocation().toVector().subtract(olho.toVector());
            double ang = Math.toDegrees(olho.getDirection().angle(para.normalize()));
            if (ang < 32) {
                p.playSound(j.sussurro.getLocation(), Sound.ENTITY_BAT_HURT, 1f, 0.5f);
                mundo.spawnParticle(Particle.SQUID_INK, j.sussurro.getLocation(), 12, 0.2, 0.2, 0.2, 0.05);
                tirarSussurro(j);
                p.sendActionBar(Hotel.txt("§7Ufa... ele foi embora."));
            } else if (rtick >= j.sussurroAte) {
                Location l = j.sussurro.getLocation();
                tirarSussurro(j);
                p.playSound(l, Sound.ENTITY_PHANTOM_BITE, 1.2f, 0.6f);
                Sons.para(p, l, "sussurro_ataque", 1.2f, 1f, Sound.ENTITY_FOX_SCREECH, 1f, 0.6f);
                titulo(p, "", "§4§lMORDIDO!", 0.8);
                machucar(p, pl.getConfig().getDouble("sussurro-dano", 7), "o Sussurro");
            } else {
                // vai virando para o jogador e treme
                j.sussurro.setRotation(0, 0);
            }
            return;
        }
        if (c == null || !c.escuro || escondido(j)) return;
        if (j.proximoSussurro == 0) j.proximoSussurro = rtick + 120 + rnd.nextInt(160);
        if (rtick < j.proximoSussurro) return;
        j.proximoSussurro = rtick + 400 + rnd.nextInt(300);
        criarSussurro(p, j);
    }

    private void criarSussurro(Player p, Jogador j) {
        if (j == null || j.sussurro != null) return;
        Location olho = p.getEyeLocation();
        Vector f = olho.getDirection().setY(0);
        if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
        f.normalize().rotateAroundY(Math.toRadians(100 + rnd.nextInt(160)));
        Location l = olho.clone().add(f.multiply(1.8)).add(0, rnd.nextDouble() * 0.7 - 0.3, 0);
        if (!l.getBlock().isPassable()) l = olho.clone().add(0, 0.9, 0);
        Location fl = l.clone();
        fl.setDirection(olho.toVector().subtract(l.toVector()));
        j.sussurro = mundo.spawn(fl, ItemDisplay.class, d -> {
            d.setItemStack(Skins.cabeca("grito"));
            d.setVisibleByDefault(false);
            d.setBrightness(new Display.Brightness(6, 6));
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY((float) Math.PI),
                    new Vector3f(ESC_SUSSURRO), new Quaternionf()));
            d.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
        });
        Visual.gemeo(j.sussurro, "grito", p);
        criadas.add(j.sussurro);
        j.sussurroAte = rtick + (long) (pl.getConfig().getDouble("sussurro-segundos", 2.6) * 20);
        Sons.para(p, l, "psst", 1.2f, 1f, Sound.ENTITY_SILVERFISH_AMBIENT, 1.2f, 1.7f);
        p.playSound(l, Sound.ENTITY_PHANTOM_AMBIENT, 0.6f, 1.8f);
    }

    private void tirarSussurro(Jogador j) {
        if (j.sussurro != null) j.sussurro.remove();
        j.sussurro = null;
    }

    // ================================================================== o elevador do comeco

    /** Lugar de cada um dentro da cabine (olhando para a porta). */
    Location noElevador(Planta.Comodo c, int i) {
        double[][] off = {{0, 0}, {-0.9, -0.9}, {-0.9, 0.9}, {0.6, -0.9}, {0.6, 0.9}, {-0.9, 0}, {0.6, 0}, {0, -0.9}, {0, 0.9}};
        double[] o = off[Math.floorMod(i, off.length)];
        return new Location(mundo, c.x0 - 2 + 0.5 + o[0], Planta.Y, c.zE + 0.5 + o[1], -90f, 0f);
    }

    /**
     * A partida comeca com todo mundo descendo de elevador: a parede do poco passa pela grade, os andares contam,
     * no meio ele da um tranco e a luz apaga, depois chega devagar, o sino toca e a porta abre para a recepcao.
     */
    final class Elevador {
        static final int DURA = 200;
        final Planta.Comodo c;
        final long ini;
        final List<BlockDisplay> parede = new ArrayList<>();
        final List<Integer> linha = new ArrayList<>();
        final boolean[] pulou;
        double desl;
        static final int LINHAS = 16;

        Elevador(Planta.Comodo c) {
            this.c = c;
            this.ini = rtick;
            // o poco fica num chunk que pode ainda nao estar carregado: segura ele carregado durante a descida
            for (int dz = -2; dz <= 2; dz += 4) {
                org.bukkit.Chunk ch = mundo.getChunkAt((c.x0 - 6) >> 4, (c.zE + dz) >> 4);
                ch.load(true);
                ch.addPluginChunkTicket(pl);
            }
            // a parede do poco (do outro lado da grade): blocos que sobem enquanto a cabine "desce"
            for (int dz = -2; dz <= 2; dz++)
                for (int k = 0; k < LINHAS; k++) {
                    final int kk = k, zz = dz;
                    Material m = k % 8 == 3 && dz == 0 ? Material.REDSTONE_LAMP : rnd.nextDouble() < 0.18 ? Material.CRACKED_STONE_BRICKS
                            : rnd.nextDouble() < 0.1 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
                    BlockData bd = m == Material.REDSTONE_LAMP ? Bukkit.createBlockData("minecraft:redstone_lamp[lit=true]") : m.createBlockData();
                    BlockDisplay d = mundo.spawn(posParede(zz, kk), BlockDisplay.class, x -> {
                        x.setBlock(bd);
                        x.setPersistent(false);
                        x.setTeleportDuration(1);
                        x.setBrightness(new Display.Brightness(m == Material.REDSTONE_LAMP ? 15 : 6, 0));
                        x.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
                    });
                    parede.add(d);
                    linha.add(k);
                    criadas.add(d);
                }
            pulou = new boolean[parede.size()];
            for (Player p : vivos()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.6f);
                titulo(p, "", "§8o elevador está descendo...", 2.5);
            }
        }

        private Location posParede(int dz, double k) {
            double y = Planta.Y - 7 + k;
            return new Location(mundo, c.x0 - 6, y, c.zE + dz);
        }

        /** Velocidade (blocos por tick) em cada momento. */
        private double vel(long t) {
            if (t < 100) return 0.32;
            if (t < 135) return 0;
            if (t < 170) return 0.12;
            return 0;
        }

        boolean tick() {
            long t = rtick - ini;
            List<Player> vivos = vivos();
            double v = vel(t);
            desl += v;
            if (v > 0) for (int i = 0; i < parede.size(); i++) {
                BlockDisplay d = parede.get(i);
                if (!d.isValid()) continue;
                double k = Math.floorMod((int) Math.floor(linha.get(i) + desl), LINHAS) + (desl - Math.floor(desl));
                Location nova = posParede(i / LINHAS - 2, k);
                boolean volta = nova.getY() < d.getLocation().getY() - 1;
                if (volta != pulou[i]) {
                    d.setTeleportDuration(volta ? 0 : 1);
                    pulou[i] = volta;
                }
                d.teleport(nova);
            }
            Location lamp = new Location(mundo, c.x0 - 2, Planta.Y + 3, c.zE);
            // barulho do motor e das correntes
            if (v > 0 && t % 20 == 0) for (Player p : vivos) {
                p.playSound(p.getLocation(), Sound.ENTITY_MINECART_INSIDE, 0.5f, v > 0.2 ? 0.5f : 0.4f);
                p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_STEP, 0.6f, 0.5f);
            }
            // andar (no Java e no Bedrock: barra de acao)
            if (t % 5 == 0 && t < 185) {
                String andar = t < 100 ? "§f" + (5 - (int) (t / 25)) : t < 135 ? "§c§k?" : t < 170 ? "§c-1" : "§4§lB2";
                for (Player p : vivos) p.sendActionBar(Hotel.txt("§7Andar " + andar));
            }
            // o tranco: para de repente, luz apaga, algo bate no teto
            if (t == 100) {
                mundo.getBlockAt(lamp).setType(Material.IRON_BLOCK, false);
                for (Player p : vivos) {
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
                    p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.5f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, false, false, false));
                    p.setVelocity(new Vector(0, 0.32, 0));
                    titulo(p, "", "§4o elevador parou...", 1.5);
                }
            }
            if (t == 112) for (Player p : vivos) p.playSound(lamp, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1f, 0.5f);
            if (t == 120) for (Player p : vivos) Sons.para(p, lamp.clone().add(0, 2, 0), "passos", 1f, 0.7f, Sound.BLOCK_WOOD_STEP, 1f, 0.5f);
            if (t > 100 && t < 135 && t % 3 == 0) mundo.getBlockAt(lamp).setType(rnd.nextInt(3) == 0 ? Material.SEA_LANTERN : Material.IRON_BLOCK, false);
            if (t == 135) {
                mundo.getBlockAt(lamp).setType(Material.SEA_LANTERN, false);
                for (Player p : vivos) p.playSound(p.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.8f, 0.5f);
            }
            if (t == 170) for (Player p : vivos) {
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.2f, 1.2f);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 0.7f);
            }
            if (t >= 185) {
                acabar(true);
                return false;
            }
            return true;
        }

        void acabar(boolean abrir) {
            for (BlockDisplay d : parede) d.remove();
            parede.clear();
            for (int dz = -2; dz <= 2; dz += 4) mundo.getChunkAt((c.x0 - 6) >> 4, (c.zE + dz) >> 4).removePluginChunkTicket(pl);
            mundo.getBlockAt(c.x0 - 2, Planta.Y + 3, c.zE).setType(Material.SEA_LANTERN, false);
            if (!abrir) return;
            for (int y = Planta.Y; y <= Planta.Y + 1; y++) {
                Block b = mundo.getBlockAt(c.x0, y, c.zE);
                if (b.getBlockData() instanceof Openable o) {
                    o.setOpen(true);
                    b.setBlockData(o, false);
                }
            }
            Location porta = new Location(mundo, c.x0 + 0.5, Planta.Y + 1, c.zE + 0.5);
            mundo.playSound(porta, Sound.BLOCK_IRON_DOOR_OPEN, 1.2f, 0.6f);
            for (Player p : vivos()) {
                titulo(p, "§4§lO HOTEL", "§7Abra §f" + portas() + " portas§7. Não deixe ELES te acharem.", 4);
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
                p.sendMessage(Hotel.PREFIXO + "§fClique na porta com o número para abrir. §7Trancada? A chave está numa §egaveta§7 (barril).");
                p.sendMessage(Hotel.PREFIXO + "§cLuzes piscando = esconda-se num armário §7(clique na porta do armário; agache para sair).");
                p.sendMessage(Hotel.PREFIXO + "§7Quadro com rosto: §fnão encare§7. A campainha tocou: §fNÃO SE MEXA§7.");
            }
        }
    }

    // ================================================================== O Retrato (nosso)

    private void criarRetrato(Planta.Comodo c) {
        int[] r = c.retrato;
        float esc = 1.4f;
        Location l = new Location(mundo, r[0] + 0.5, r[1] + 0.5, r[2] + 1 + esc * 0.25 + 0.02);
        ItemDisplay d = mundo.spawn(l, ItemDisplay.class, x -> {
            x.setItemStack(Skins.cabeca("retrato"));
            x.setBrightness(new Display.Brightness(7, 7));
            x.setPersistent(false);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY((float) Math.PI), new Vector3f(esc), new Quaternionf()));
            x.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
        });
        retratos.put(c.idx, d);
        criadas.add(d);
        Visual.gemeo(d, "retrato");
    }

    /** Quem encara o quadro por mais de ~2 segundos acorda ele: o rosto pula em cima, as luzes do comodo quebram. */
    private void retrato(Player p, Jogador j, Planta.Comodo c) {
        if (c == null || c.retrato == null || retratosAcordados.contains(c.idx) || escondido(j)) {
            j.encarando = 0;
            return;
        }
        ItemDisplay d = retratos.get(c.idx);
        if (d == null || !d.isValid()) return;
        Location olho = p.getEyeLocation();
        Vector para = d.getLocation().toVector().subtract(olho.toVector());
        double dist = para.length();
        boolean olhando = dist < 11 && dist > 0.1 && Math.toDegrees(olho.getDirection().angle(para.normalize())) < 16 && p.hasLineOfSight(d);
        if (!olhando) {
            j.encarando = Math.max(0, j.encarando - 1);
            return;
        }
        j.encarando++;
        if (j.encarando == 3) p.playSound(d.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f);
        if (j.encarando == 5) {
            p.playSound(d.getLocation(), Sound.ENTITY_CREAKING_TWITCH, 1f, 0.5f);
            p.sendActionBar(Hotel.txt("§8...o quadro está te olhando de volta"));
        }
        if (j.encarando >= 8) acordarRetrato(c, d, p);
    }

    private void acordarRetrato(Planta.Comodo c, ItemDisplay d, Player p) {
        retratosAcordados.add(c.idx);
        retratos.remove(c.idx);
        Location l = d.getLocation();
        Sons.local(mundo, l, 24, "sussurro_ataque", 1.6f, 0.6f, Sound.ENTITY_GHAST_SCREAM, 1.5f, 0.5f);
        mundo.playSound(l, Sound.ENTITY_PAINTING_BREAK, 1.5f, 0.5f);
        // o comodo fica no escuro (e no escuro mora o Sussurro)
        for (int[] lz : c.luzes) {
            luz(lz, false);
            luzesQuebradas.add(chaveLuz(lz));
            mundo.spawnParticle(Particle.BLOCK, lz[0] + 0.5, lz[1] + 0.5, lz[2] + 0.5, 8, 0.2, 0.2, 0.2, 0, Material.GLASS.createBlockData());
        }
        c.escuro = true;
        for (Player o : vivos()) {
            Planta.Comodo oc = planta.onde(o.getX(), o.getZ());
            Jogador oj = jogadores.get(o.getUniqueId());
            if (oc == c && oj != null) oj.proximoSussurro = rtick + 50 + rnd.nextInt(60);
        }
        retratoVoando = d;
        retratoAlvo = p.getUniqueId();
        retratoAte = rtick + 8;
        titulo(p, "", "§4§lNÃO ERA PARA ENCARAR", 1);
        machucar(p, 4, "o Retrato");
    }

    /** A cada tick: o rosto do retrato voa para a cara de quem encarou e some. */
    private void voarRetrato() {
        if (retratoVoando == null) return;
        Player p = retratoAlvo == null ? null : Bukkit.getPlayer(retratoAlvo);
        if (p == null || !retratoVoando.isValid() || rtick >= retratoAte) {
            if (retratoVoando.isValid()) {
                mundo.spawnParticle(Particle.SQUID_INK, retratoVoando.getLocation(), 20, 0.3, 0.3, 0.3, 0.05);
                retratoVoando.remove();
            }
            retratoVoando = null;
            return;
        }
        Location de = retratoVoando.getLocation(), para = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.7));
        Vector passo = para.toVector().subtract(de.toVector()).multiply(0.45);
        Location nova = de.add(passo);
        retratoVoando.teleport(nova);
        Vector v = p.getEyeLocation().toVector().subtract(nova.toVector());
        float yaw = (float) Math.atan2(-v.getX(), v.getZ());
        float esc = 1.4f + (8 - (retratoAte - rtick)) * 0.35f;
        retratoVoando.setInterpolationDelay(0);
        retratoVoando.setInterpolationDuration(1);
        retratoVoando.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(-yaw + (float) Math.PI), new Vector3f(esc), new Quaternionf()));
    }

    // ================================================================== A Campainha (nosso: o Gerente)

    /** Toca a campainha da recepcao 3 vezes. Quando para, quem se mexer leva um susto (e dano) do Gerente. */
    void iniciarCampainha() {
        if (campComeca >= 0) return;
        campComeca = rtick + 60;
        campAte = campComeca + 80;
        campPos.clear();
    }

    private void campainha() {
        if (campComeca < 0) return;
        long falta = campComeca - rtick;
        List<Player> vivos = vivos();
        if (falta == 55 || falta == 38 || falta == 21) for (Player p : vivos) {
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.4f, 1.3f);
            if (falta == 55) titulo(p, "§6§lDING...", "§fquando a campainha parar: §c§lNÃO SE MEXA", 2.6);
        }
        if (falta == 0) for (Player p : vivos) {
            campPos.put(p.getUniqueId(), p.getLocation().clone());
            titulo(p, "§c§lNÃO SE MEXA", "§7o Gerente está olhando...", 3.6);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
        }
        if (falta < 0 && rtick < campAte) {
            if (rtick % 10 == 0) for (Player p : vivos) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
            if (rtick % 2 == 0) for (Player p : vivos) {
                Jogador j = jogadores.get(p.getUniqueId());
                if (j == null || escondido(j)) continue;
                Location a = campPos.get(p.getUniqueId()), l = p.getLocation();
                if (a == null || !a.getWorld().equals(l.getWorld())) {
                    campPos.put(p.getUniqueId(), l.clone());
                    continue;
                }
                double dx = l.getX() - a.getX(), dz = l.getZ() - a.getZ();
                if (dx * dx + dz * dz > 0.3 * 0.3 && rtick >= j.campCastigo) {
                    j.campCastigo = rtick + 25;
                    campPos.put(p.getUniqueId(), l.clone());
                    castigoGerente(p);
                }
            }
        }
        if (falta < 0 && rtick >= campAte) {
            campComeca = -1;
            campAte = -1;
            for (Player p : vivos) {
                titulo(p, "", "§7...pode andar.", 1.5);
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.9f, 0.5f);
            }
        }
    }

    /** O rosto do Gerente aparece colado na cara de quem se mexeu (so essa pessoa ve). */
    private void castigoGerente(Player p) {
        Location olho = p.getEyeLocation();
        Vector f = olho.getDirection();
        Location l = olho.clone().add(f.clone().multiply(0.9));
        Vector v = olho.toVector().subtract(l.toVector());
        float yaw = (float) Math.atan2(-v.getX(), v.getZ());
        ItemDisplay d = mundo.spawn(l, ItemDisplay.class, x -> {
            x.setItemStack(Skins.cabeca("retrato"));
            x.setVisibleByDefault(false);
            x.setBrightness(new Display.Brightness(12, 12));
            x.setPersistent(false);
            x.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(-yaw + (float) Math.PI), new Vector3f(2.2f), new Quaternionf()));
            x.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
        });
        p.showEntity(pl, d);
        criadas.add(d);
        Bukkit.getScheduler().runTaskLater(pl, d::remove, 10L);
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.2f, 0.5f);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.5f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
        titulo(p, "§4§lEU DISSE PARA NÃO SE MEXER", "", 1.2);
        machucar(p, pl.getConfig().getDouble("campainha-dano", 6), "o Gerente");
    }

    // ================================================================== a Biblioteca e a Figura

    private void iniciarBiblioteca(Planta.Comodo c) {
        livros = 0;
        for (Item i : livrosNoChao) i.remove();
        livrosNoChao.clear();
        List<int[]> lugares = new ArrayList<>(c.livros);
        java.util.Collections.shuffle(lugares, rnd);
        List<int[]> escolhidos = new ArrayList<>();
        for (int[] l : lugares) {
            if (escolhidos.size() >= livrosPrecisa()) break;
            boolean longe = true;
            for (int[] o : escolhidos) if (Math.abs(o[0] - l[0]) + Math.abs(o[1] - l[1]) < 7) longe = false;
            if (longe && l[0] > c.x0 + 3) escolhidos.add(l);
        }
        for (int[] l : escolhidos) {
            Item it = mundo.dropItem(new Location(mundo, l[0] + 0.5, Planta.Y + 0.3, l[1] + 0.5), new ItemStack(Material.ENCHANTED_BOOK), x -> {
                x.setGravity(false);
                x.setVelocity(new Vector());
                x.setPickupDelay(32767);
                x.setCanPlayerPickup(false);
                x.setCanMobPickup(false);
                x.setUnlimitedLifetime(true);
                x.setGlowing(true);
                x.setPersistent(false);
                x.setInvulnerable(true);
                x.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
            });
            livrosNoChao.add(it);
            criadas.add(it);
        }
        for (Player p : vivos()) {
            titulo(p, "§4§lA BIBLIOTECA", "§7Ache §f" + livrosPrecisa() + " livros§7. §cAnde AGACHADO.", 4);
            p.sendMessage(Hotel.PREFIXO + "§fA §4Figura §fé cega, mas ouve tudo. §eAnde agachado§f e não corra. Ache os §e" + livrosPrecisa()
                    + " livros brilhantes§f no chão para abrir a porta " + String.format("%04d", c.porta()) + ".");
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (estado != Estado.JOGANDO || planta == null) return;
            if (figura != null) figura.remover();
            figura = new Figura(c);
        }, 80L);
    }

    private void pegarLivro(Player p) {
        Location l = p.getLocation();
        for (Iterator<Item> it = livrosNoChao.iterator(); it.hasNext(); ) {
            Item i = it.next();
            if (!i.isValid() || i.getLocation().distanceSquared(l) > 1.6 * 1.6) continue;
            i.remove();
            it.remove();
            livros++;
            mundo.playSound(l, Sound.ITEM_BOOK_PAGE_TURN, 1.2f, 0.7f);
            for (Player o : online(true)) titulo(o, "", "§fLivros: §e" + livros + "§7/" + livrosPrecisa(), 1.5);
            if (figura != null) figura.barulho(l, 12);
            if (livros >= livrosPrecisa()) {
                Planta.Comodo c = planta.comodo(planta.biblioteca - 1);
                destrancadas.add(c.idx);
                avisar("§a§lTodos os livros! §fA porta §e" + String.format("%04d", c.porta()) + " §fdestrancou. §7Saiam sem fazer barulho...");
                mundo.playSound(new Location(mundo, c.x1, Planta.Y, c.zS), Sound.BLOCK_IRON_DOOR_OPEN, 2f, 0.8f);
            }
        }
    }

    /** A Figura: cega e enorme. Um motor invisivel anda (caminhos do Minecraft) e o corpo segue. */
    final class Figura {
        final Planta.Comodo sala;
        Zombie motor;
        private int limpezaEq;
        Mannequin corpo;
        Location destino;
        int modo; // 0 ronda, 1 investigando, 2 cacando
        long ateModo, proximoSom;
        UUID alvo;

        Figura(Planta.Comodo sala) {
            this.sala = sala;
            Location l = new Location(mundo, sala.meioX(), Planta.Y, sala.meioZ() + 0.5);
            motor = mundo.spawn(l, Zombie.class, z -> {
                z.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
                z.setAdult();
                z.setInvisible(true);
                z.setSilent(true);
                z.setInvulnerable(true);
                z.setPersistent(false);
                z.setCanPickupItems(false);
                z.setShouldBurnInDay(false);
                z.setCollidable(false);
                z.getEquipment().clear();
                z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
                AttributeInstance v = z.getAttribute(Attribute.MOVEMENT_SPEED);
                if (v != null) v.setBaseValue(0.25);
                AttributeInstance f = z.getAttribute(Attribute.FOLLOW_RANGE);
                if (f != null) f.setBaseValue(48);
                // LevelledMobs/EliteMobs nao mexem no motor (nada de nivel, nome em cima ou velocidade extra)
                NamespacedKey sn = NamespacedKey.fromString("levelledmobs:nolevel"), sm = NamespacedKey.fromString("levelledmobs:denylm_nametag");
                if (sn != null) z.getPersistentDataContainer().set(sn, PersistentDataType.STRING, "hotel");
                if (sm != null) z.getPersistentDataContainer().set(sm, PersistentDataType.STRING, "hotel");
                z.addScoreboardTag("clearlag_exclude");
            });
            try {
                Bukkit.getMobGoals().removeAllGoals(motor);
            } catch (Throwable ignored) {
            }
            motor.getPathfinder().setCanOpenDoors(false);
            motor.getPathfinder().setCanPassDoors(false);
            corpo = mundo.spawn(l, Mannequin.class, m -> {
                m.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
                m.setProfile(Skins.perfil("figura"));
                m.setDescription(null);
                m.setImmovable(true);
                m.setGravity(false);
                m.setSilent(true);
                m.setInvulnerable(true);
                m.setCollidable(false);
                m.setPersistent(false);
                AttributeInstance sc = m.getAttribute(Attribute.SCALE);
                if (sc != null) sc.setBaseValue(ESC_FIGURA);
            });
            criadas.add(motor);
            criadas.add(corpo);
            Visual.gemeo(corpo, "figura");
            Sons.local(mundo, l, 64, "figura", 2f, 1f, Sound.ENTITY_WARDEN_EMERGE, 2f, 0.6f);
            mundo.spawnParticle(Particle.SQUID_INK, l.clone().add(0, 1, 0), 40, 0.6, 1.2, 0.6, 0.05);
            for (Player p : vivos()) titulo(p, "", "§4A Figura acordou...", 2);
        }

        void sincronizar() {
            if (motor != null && motor.isValid() && (++limpezaEq % 10 == 1)) {
                org.bukkit.inventory.EntityEquipment eq = motor.getEquipment();
                if (eq != null) for (org.bukkit.inventory.EquipmentSlot sl : org.bukkit.inventory.EquipmentSlot.values()) {
                    try {
                        org.bukkit.inventory.ItemStack it = eq.getItem(sl);
                        if (it != null && !it.getType().isAir()) eq.setItem(sl, null);   // armadura posta por outro plugin num zumbi invisivel
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (motor == null || !motor.isValid() || corpo == null || !corpo.isValid()) return;
            if (rtick % 20 == 0) travarVelocidade();
            Location l = motor.getLocation();
            float yaw = motor.getBodyYaw();
            l.setYaw(yaw);
            l.setPitch(0);
            corpo.teleport(l);
            corpo.setRotation(yaw + (float) Math.sin(rtick * 0.3) * (modo == 0 ? 6 : 18), modo == 2 ? 15 : 0);
            corpo.setBodyYaw(yaw);
            Visual.quadro(corpo, modo == 2 ? 1 : 0);     // cacando: bracos para a frente e a boca aberta
        }

        void barulho(Location l, double raio) {
            if (motor == null || !motor.isValid() || l.distanceSquared(motor.getLocation()) > raio * raio) return;
            if (modo < 1 || modo == 1) {
                modo = 1;
                destino = l.clone();
                ateModo = tick + seg(6);
                motor.getPathfinder().moveTo(destino, 1.05);
                if (tick >= proximoSom) {
                    proximoSom = tick + seg(3);
                    mundo.playSound(motor.getLocation(), Sound.ENTITY_WARDEN_SNIFF, 2f, 0.6f);
                }
            }
        }

        void ouvir(Player p, Jogador j) {
            if (motor == null || !motor.isValid() || escondido(j)) return;
            Location l = p.getLocation(), v = motor.getLocation();
            double d = l.distance(v);
            boolean andou = j.ultima != null && j.ultima.distanceSquared(l) > 0.05;
            // pegou quem estava colado
            if (d < 1.9 && Math.abs(l.getY() - v.getY()) < 2) {
                corpo.swingMainHand();
                mundo.playSound(v, Sound.ENTITY_WARDEN_ROAR, 2f, 0.8f);
                morrer(p, "a Figura");
                modo = 0;
                destino = null;
                return;
            }
            double raio = p.isSprinting() ? 22 : p.isSneaking() ? 2.5 : 11;
            if (!andou && !p.isSprinting()) raio = 1.8;
            if (d > raio) return;
            if (d < 6 && (andou || p.isSprinting())) {
                if (modo != 2) mundo.playSound(v, Sound.ENTITY_WARDEN_ROAR, 2.5f, 0.7f);
                modo = 2;
                alvo = p.getUniqueId();
                ateModo = tick + seg(4);
                motor.getPathfinder().moveTo(p, 1.55);
            } else {
                barulho(l, raio + 1);
            }
            if (d < 9) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
        }

        void ciclo(List<Player> vivos) {
            if (motor == null || !motor.isValid()) return;
            Location v = motor.getLocation();
            if (!sala.contem(v.getX(), v.getZ())) motor.teleport(new Location(mundo, sala.meioX(), Planta.Y, sala.meioZ() + 0.5));
            if (modo == 2) {
                Player a = alvo == null ? null : Bukkit.getPlayer(alvo);
                if (a == null || tick > ateModo || !vivo(a)) modo = 0;
                else motor.getPathfinder().moveTo(a, 1.55);
            } else if (modo == 1 && (tick > ateModo || destino != null && destino.distanceSquared(v) < 3)) {
                modo = 0;
            }
            if (modo == 0 && (destino == null || destino.distanceSquared(v) < 4 || tick % 40 == 0)) {
                if (!sala.livros.isEmpty()) {
                    int[] l = sala.livros.get(rnd.nextInt(sala.livros.size()));
                    destino = new Location(mundo, l[0] + 0.5, Planta.Y, l[1] + 0.5);
                    motor.getPathfinder().moveTo(destino, 0.7);
                }
            }
            if (tick % 3 == 0 && motor.getVelocity().setY(0).lengthSquared() > 0.0002)
                mundo.playSound(v, Sound.ENTITY_WARDEN_STEP, 1.6f, 0.55f);
            if (tick >= proximoSom && modo == 0) {
                proximoSom = tick + seg(5 + rnd.nextInt(6));
                Sons.local(mundo, v, 40, "figura", 1.2f, 0.9f + rnd.nextFloat() * 0.2f, Sound.ENTITY_WARDEN_AMBIENT, 1.5f, 0.6f);
            }
        }

        /** Outro plugin pode tentar acelerar o zumbi: volta a velocidade para 0,25. */
        void travarVelocidade() {
            AttributeInstance v = motor.getAttribute(Attribute.MOVEMENT_SPEED);
            if (v == null) return;
            for (org.bukkit.attribute.AttributeModifier m : new ArrayList<>(v.getModifiers()))
                if (!"minecraft".equals(m.getKey().getNamespace()) || m.getAmount() > 0) v.removeModifier(m);
            if (v.getBaseValue() != 0.25) v.setBaseValue(0.25);
            if (motor.hasPotionEffect(PotionEffectType.SPEED)) motor.removePotionEffect(PotionEffectType.SPEED);
        }

        String status() {
            if (motor == null || !motor.isValid()) return "motor-morto";
            Location v = motor.getLocation();
            return String.format("modo%d %.1f %.1f path=%s", modo, v.getX(), v.getZ(), motor.getPathfinder().hasPath());
        }

        void remover() {
            if (corpo != null) corpo.remove();
            if (motor != null) motor.remove();
        }
    }

    // ================================================================== O Perseguidor

    private void olhosNasParedes() {
        for (int i = planta.corrida - 2; i < planta.corrida; i++) {
            Planta.Comodo c = planta.comodo(i);
            if (c == null) continue;
            for (int k = 0; k < 7; k++) {
                int x = c.x0 + 2 + rnd.nextInt(Math.max(1, c.x1 - c.x0 - 3));
                boolean norte = rnd.nextBoolean();
                double z = norte ? c.z0 + 1.05 : c.z1 - 0.05;
                Location l = new Location(mundo, x + 0.5, Planta.Y + 1 + rnd.nextDouble() * (c.alt - 2.2), z);
                ItemDisplay d = mundo.spawn(l, ItemDisplay.class, e -> {
                    e.setItemStack(Skins.cabeca("perseguidor"));
                    e.setBrightness(new Display.Brightness(10, 10));
                    e.setPersistent(false);
                    e.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
                    e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(ESC_OLHO_PAREDE), new Quaternionf()));
                });
                olhosParede.add(d);
                criadas.add(d);
                Visual.gemeo(d, "perseguidor");
            }
        }
    }

    private void virarPara(ItemDisplay d, List<Player> vivos) {
        if (!d.isValid() || vivos.isEmpty()) return;
        Player perto = null;
        double md = 1e9;
        for (Player p : vivos) {
            double x = p.getLocation().distanceSquared(d.getLocation());
            if (x < md) {
                md = x;
                perto = p;
            }
        }
        if (perto == null || md > 30 * 30) return;
        Vector para = perto.getEyeLocation().toVector().subtract(d.getLocation().toVector());
        float yaw = (float) Math.atan2(-para.getX(), para.getZ());
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(3);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(-yaw + (float) Math.PI), new Vector3f(ESC_OLHO_PAREDE), new Quaternionf()));
    }

    private void comecarCorrida() {
        Planta.Comodo ini = planta.comodo(planta.corrida);
        int fimIdx = planta.corrida + Planta.CORRIDA_SALAS - 1;
        construirAte(fimIdx + 3);
        abertas = Math.max(abertas, fimIdx + 1);
        construirAte(abertas + 3);
        perseguidor = new Perseguidor(ini.x0 + 0.5, planta.comodo(fimIdx).x1 - 0.5);
        for (Player p : vivos()) {
            titulo(p, "§0§l§kXX§4§l CORRA §0§l§kXX", "§7não pare, não olhe para trás", 2.5);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1.2f, 0.8f);
        }
    }

    final class Perseguidor {
        double x;
        final double xFim;
        Mannequin corpo;
        long comecaEm;
        double vel = 0.0;

        Perseguidor(double xIni, double xFim) {
            this.x = xIni;
            this.xFim = xFim;
            this.comecaEm = rtick + 40;
            Location l = posicao();
            corpo = mundo.spawn(l, Mannequin.class, m -> {
                m.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
                m.setProfile(Skins.perfil("perseguidor"));
                m.setDescription(null);
                m.setImmovable(true);
                m.setGravity(false);
                m.setSilent(true);
                m.setInvulnerable(true);
                m.setCollidable(false);
                m.setPersistent(false);
                AttributeInstance sc = m.getAttribute(Attribute.SCALE);
                if (sc != null) sc.setBaseValue(ESC_PERSEGUIDOR);
            });
            criadas.add(corpo);
            Visual.gemeo(corpo, "perseguidor");
            Visual.quadro(corpo, 1);     // ele ja nasce correndo atras: bracos para a frente
            mundo.spawnParticle(Particle.SQUID_INK, l.clone().add(0, 0.2, 0), 60, 0.7, 0.1, 0.7, 0.02);
            Sons.local(mundo, l, 90, "perseguidor", 2.5f, 1f, Sound.ENTITY_WARDEN_ROAR, 2.5f, 0.8f);
        }

        Location posicao() {
            Planta.Comodo c = comodoPorX(x);
            double z = c == null ? planta.laneZ : c.zNoCaminho(x);
            double y = Planta.Y;
            if (c != null) for (int m : c.muretas) if (Math.abs(x - (m + 0.5)) < 1.3) y += 1.0 - Math.abs(x - (m + 0.5)) / 1.3 * 0.6;
            Location l = new Location(mundo, x, y, z, -90f, 0f);
            return l;
        }

        boolean tick() {
            if (corpo == null || !corpo.isValid()) return false;
            if (rtick < comecaEm) {
                Location l = posicao();
                mundo.spawnParticle(Particle.SQUID_INK, l.clone().add(0, 0.3, 0), 6, 0.5, 0.2, 0.5, 0.02);
                corpo.teleport(l.clone().subtract(0, (comecaEm - rtick) * 0.06, 0));
                return true;
            }
            vel = Math.min(pl.getConfig().getDouble("perseguidor-velocidade", 0.29), vel + 0.02);
            x += vel;
            Location l = posicao();
            corpo.teleport(l);
            corpo.setRotation(-90f + (float) Math.sin(rtick * 0.8) * 10, 10);
            if (rtick % 2 == 0) mundo.spawnParticle(Particle.SQUID_INK, l.clone().add(0, 1, 0), 5, 0.3, 0.6, 0.3, 0.02);
            if (rtick % 5 == 0) mundo.playSound(l, Sound.ENTITY_WARDEN_STEP, 2f, 1.2f);
            if (rtick % 30 == 0) Sons.local(mundo, l, 70, "perseguidor", 1.5f, 1.05f, Sound.ENTITY_WARDEN_ANGRY, 1.5f, 0.9f);
            for (Player p : vivos()) {
                Jogador j = jogadores.get(p.getUniqueId());
                if (j == null || escondido(j)) continue;
                Location pl2 = p.getLocation();
                if (pl2.getX() < x + 0.6 && pl2.getX() > x - 6 && Math.abs(pl2.getZ() - l.getZ()) < 4) {
                    corpo.swingMainHand();
                    morrer(p, "o Perseguidor");
                }
            }
            if (x >= xFim) {
                mundo.spawnParticle(Particle.SQUID_INK, l.clone().add(0, 1, 0), 60, 0.6, 1, 0.6, 0.05);
                mundo.playSound(l, Sound.ENTITY_WARDEN_DIG, 2f, 1f);
                corpo.remove();
                Planta.Comodo fim = comodoPorX(xFim - 1);
                // quem ficou para tras (longe do Perseguidor) passa para depois da porta antes dela bater
                for (Player p : vivos()) if (p.getLocation().getX() < xFim + 0.5) pl.mover(p, lugarSeguro(p));
                if (fim != null) fecharPorta(fim);
                for (Player p : vivos()) p.sendActionBar(Hotel.txt("§7A porta bateu atrás de vocês. §fSobreviveram."));
                return false;
            }
            return true;
        }
    }

    // ================================================================== ambiente e HUD

    private void ambiente(List<Player> vivos) {
        for (Player p : vivos) {
            Location l = p.getLocation();
            if (rnd.nextInt(260) == 0) p.playSound(l.clone().add(rnd.nextInt(16) - 8, 0, rnd.nextInt(16) - 8), Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.5f, 0.5f);
            if (rnd.nextInt(400) == 0) p.playSound(l, Sound.AMBIENT_CAVE, 0.6f, 0.7f);
            if (rnd.nextInt(500) == 0) Sons.para(p, l.clone().add(rnd.nextInt(20) - 10, 0, rnd.nextInt(20) - 10), "passos", 0.8f, 1f, Sound.BLOCK_WOOD_STEP, 0.6f, 0.6f);
        }
    }

    private void hud() {
        int nv = vivos().size();
        for (Jogador j : jogadores.values()) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p == null) continue;
            if (!j.vivo) {
                p.sendActionBar(Hotel.txt("§7Espectador §8| §6Porta §f" + String.format("%04d", abertas) + " §8| §fvivos §e" + nv));
                continue;
            }
            String extra = j.armario != null ? " §8| §7escondido §8(agache para sair)" : "";
            Planta.Comodo c = planta.onde(p.getX(), p.getZ());
            if (c != null && c.tipo == Planta.Tipo.BIBLIOTECA) extra = " §8| §fLivros §e" + livros + "§7/" + livrosPrecisa() + extra;
            p.sendActionBar(Hotel.txt("§6Porta §f" + String.format("%04d", abertas) + " §8| §c" + "❤".repeat(Math.max(0, j.vidas)) + " §8| §6" + j.moedas + " moedas" + extra));
        }
    }

    // ================================================================== fim

    private void vitoria() {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        long s = (tick - inicioTick) / 4;
        String tempo = String.format("%d:%02d", s / 60, s % 60);
        List<String> nomes = new ArrayList<>();
        for (Player p : online(true)) {
            Jogador j = jogadores.get(p.getUniqueId());
            titulo(p, "§a§lVOCÊS SAÍRAM DO HOTEL!", "§7" + portas() + " portas em §f" + tempo, 5);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            if (j != null && j.vivo) {
                nomes.add(p.getName());
                fogos(p.getLocation());
                p.sendMessage(Hotel.PREFIXO + "§fVocê saiu com §6" + j.moedas + " moedas§f.");
            }
        }
        pl.registrarVitoria(nomes, s, online(true));
        Bukkit.getScheduler().runTaskLater(pl, this::finalizar, 160L);
    }

    private void fogos(Location l) {
        Firework fw = mundo.spawn(l.clone().add(0, 1, 0), Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.ORANGE, Color.RED).withFade(Color.BLACK).build());
            m.setPower(0);
            f.setFireworkMeta(m);
            f.getPersistentDataContainer().set(pl.kHotel, PersistentDataType.BYTE, (byte) 1);
        });
        fw.setPersistent(false);
    }

    private void derrota() {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        for (Player p : online(true)) {
            titulo(p, "§4§lO HOTEL VENCEU", "§7Vocês chegaram na porta §f" + String.format("%04d", abertas), 4);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 0.5f);
        }
        avisar("§cNinguém saiu do hotel. Chegaram até a porta §f" + String.format("%04d", abertas) + "§c.");
        Bukkit.getScheduler().runTaskLater(pl, this::finalizar, 120L);
    }

    void finalizar() {
        limparEntidades();
        for (Jogador j : new ArrayList<>(jogadores.values())) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            jogadores.remove(j.id);
            if (p != null && p.isOnline()) {
                p.setWalkSpeed(0.2f);
                pl.devolver(p);
                p.sendMessage(Hotel.PREFIXO + "§7Partida encerrada. Jogar de novo: §e/hotel entrar");
            }
        }
        planta = null;
        estado = Estado.ESPERANDO;
    }

    void parar() {
        if (estado == Estado.JOGANDO || estado == Estado.FIM) finalizar();
        else estado = Estado.ESPERANDO;
    }

    void limparEntidades() {
        if (elevador != null) elevador.acabar(false);
        elevador = null;
        retratos.clear();
        retratosAcordados.clear();
        retratoVoando = null;
        campComeca = -1;
        campAte = -1;
        campPos.clear();
        if (investida != null) investida.acabar();
        investida = null;
        if (figura != null) figura.remover();
        figura = null;
        if (perseguidor != null && perseguidor.corpo != null) perseguidor.corpo.remove();
        perseguidor = null;
        for (Jogador j : jogadores.values()) tirarSussurro(j);
        for (Entity e : criadas) if (e != null && e.isValid()) e.remove();
        criadas.clear();
        olhos.clear();
        olhosParede.clear();
        livrosNoChao.clear();
        if (mundo != null)
            for (Entity e : mundo.getEntities())
                if (!(e instanceof Player) && e.getPersistentDataContainer().has(pl.kHotel, PersistentDataType.BYTE)) e.remove();
    }

    String status() {
        return "estado=" + estado + " porta=" + abertas + " construidos=" + construidos + " vivos=" + vivos().size()
                + (planta == null ? "" : " corrida=" + planta.corrida + " biblioteca=" + planta.biblioteca)
                + " estrondo=" + (investida != null) + " figura=" + (figura == null ? "nao" : figura.status()) + " campainha=" + (campComeca >= 0) + " retratos=" + retratos.size() + " perseguidor=" + (perseguidor != null) + " livros=" + livros;
    }
}
