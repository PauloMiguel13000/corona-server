package br.servidor.slender;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Uma partida do Slenderman. O ciclo roda a cada 5 ticks (4 vezes por segundo). */
final class Partida {

    enum Estado { ESPERANDO, CONTAGEM, JOGANDO, FIM }

    static final class Jogador {
        final UUID id;
        final String nome;
        boolean jogando;
        boolean vivo;
        double estatica;
        long proximoEscuro;
        long reviverAte = -1;

        Jogador(Player p) {
            this.id = p.getUniqueId();
            this.nome = p.getName();
        }
    }

    record Folha(UUID quadro, int idx, Floresta.Ancora ancora, String ponto) {}

    private final Slender pl;
    final World mundo;
    final Floresta mapa;
    final Map<UUID, Jogador> jogadores = new LinkedHashMap<>();
    final List<Folha> folhasNoMapa = new ArrayList<>();
    private final Random rnd = new Random();

    Estado estado = Estado.ESPERANDO;
    int folhas;
    private int contagem;
    private long tick;
    private long inicioTick;
    private long fimPendente = -1;
    private Mannequin slender;
    /** Modelo 3D do pacote (ItemDisplay) que acompanha o boneco invisivel. */
    private ItemDisplay modelo;
    private UUID alvo;
    private long visivelAte;
    private long proximaAparicao;
    private long proximoPasso;
    private boolean assustou;

    Partida(Slender pl, World mundo, Floresta mapa) {
        this.pl = pl;
        this.mundo = mundo;
        this.mapa = mapa;
    }

    private static long seg(double s) {
        return Math.round(s * 4);
    }

    int totalFolhas() {
        return Math.max(1, Math.min(8, pl.getConfig().getInt("folhas-para-vencer", 8)));
    }

    // ================================================================== entrada e saida

    Jogador adicionar(Player p) {
        Jogador j = new Jogador(p);
        jogadores.put(p.getUniqueId(), j);
        return j;
    }

    /** Tira o jogador da partida (comando, desconexao ou fim). Devolve as coisas dele. */
    boolean remover(Player p) {
        Jogador j = jogadores.remove(p.getUniqueId());
        if (j == null) return false;
        p.setWalkSpeed(0.2f);
        pl.devolver(p);
        if (estado == Estado.JOGANDO && j.jogando && j.vivo) avisar("§7" + j.nome + " §7fugiu da partida.");
        return true;
    }

    boolean participa(Player p) {
        return jogadores.containsKey(p.getUniqueId());
    }

    boolean vivo(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        return j != null && j.jogando && j.vivo && estado == Estado.JOGANDO;
    }

    /** Medo do jogador (0 a 100): sobe olhando para o Slenderman ou com ele perto. */
    double estaticaDe(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        return j == null ? 0 : j.estatica;
    }

    /** Blocos onde as folhas estao penduradas (a luz da lanterna nao vai para la). */
    Set<Lanterna.Pos> posicoesFolhas() {
        Set<Lanterna.Pos> s = new HashSet<>();
        for (Folha f : folhasNoMapa) {
            Location l = f.ancora().frente(mundo);
            s.add(new Lanterna.Pos(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        }
        return s;
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
        for (Player p : online(false)) p.sendMessage(Slender.PREFIXO + msg);
    }

    // ================================================================== ciclo

    void ciclo() {
        tick++;
        for (Player p : online(false)) {
            if (p.getWorld().equals(mundo) && p.getLocation().getY() < Floresta.BASE - 20) {
                Jogador j = jogadores.get(p.getUniqueId());
                pl.mover(p, j != null && j.jogando && estado == Estado.JOGANDO ? mapa.inicio(mundo, 0) : Floresta.lobby(mundo));
            }
        }
        switch (estado) {
            case ESPERANDO -> {
                int n = naFila().size();
                if (n > 0 && n >= pl.getConfig().getInt("min-jogadores", 1)) {
                    estado = Estado.CONTAGEM;
                    contagem = (int) seg(pl.getConfig().getInt("contagem-segundos", 30));
                    avisar("§eA partida começa em §f" + (contagem / 4) + "s§e. Chame os amigos: §f/slender entrar");
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
        if (fila.size() >= pl.getConfig().getInt("max-jogadores", 8)) contagem = (int) Math.min(contagem, seg(10));
        contagem--;
        if (contagem % 4 == 0) {
            int s = contagem / 4;
            for (Player p : fila) {
                p.sendActionBar(Slender.txt("§7A partida começa em §e" + s + "s §8| §7jogadores: §f" + fila.size()));
                if (s <= 5 && s > 0) {
                    p.showTitle(Title.title(Slender.txt("§4" + s), Slender.txt("§7prepare-se..."),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f);
                }
            }
        }
        if (contagem <= 0) iniciar();
    }

    private void hudFila() {
        if (tick % 4 != 0) return;
        for (Player p : naFila()) {
            String s = estado == Estado.JOGANDO
                    ? "§7Partida em andamento: §e" + folhas + "/" + totalFolhas() + " folhas §8| §7você joga a próxima"
                    : "§7Esperando jogadores... §8| §7na fila: §f" + naFila().size();
            p.sendActionBar(Slender.txt(s));
        }
    }

    // ================================================================== inicio

    void iniciar() {
        List<Player> fila = naFila();
        if (fila.isEmpty()) {
            estado = Estado.ESPERANDO;
            return;
        }
        estado = Estado.JOGANDO;
        folhas = 0;
        inicioTick = tick;
        fimPendente = -1;
        removerSlender();
        limparFolhas();
        colocarFolhas();
        int i = 0;
        for (Player p : fila) {
            Jogador j = jogadores.get(p.getUniqueId());
            j.jogando = true;
            j.vivo = true;
            j.estatica = 0;
            Guarda.limpar(p);
            p.setGameMode(GameMode.ADVENTURE);
            pl.lanterna().dar(p);
            escuridao(p, j);
            pl.mover(p, mapa.inicio(mundo, i++));
            p.showTitle(Title.title(Slender.txt("§4§lO PORCELANA"), Slender.txt("§7Encontre as §f" + totalFolhas() + " folhas§7. Não encare a máscara."),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.6f);
            p.sendMessage(Slender.PREFIXO + "§fAs folhas estão presas nas §eparedes§f dos lugares da floresta. Clique nelas para pegar.");
            p.sendMessage(Slender.PREFIXO + "§fSe ele aparecer, §cnão fique olhando§f... e corra.");
        }
        proximaAparicao = tick + seg(150);
        avisar("§cA partida começou com §f" + fila.size() + " §cjogador(es). Boa sorte.");
    }

    /** Quem chega com a partida rolando entra direto na floresta. */
    void entrarNoMeio(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || estado != Estado.JOGANDO) return;
        j.jogando = true;
        j.vivo = true;
        j.estatica = 0;
        fimPendente = -1;
        Guarda.limpar(p);
        p.setGameMode(GameMode.ADVENTURE);
        pl.lanterna().dar(p);
        escuridao(p, j);
        pl.mover(p, mapa.inicio(mundo, rnd.nextInt(8)));
        p.showTitle(Title.title(Slender.txt("§4§lO PORCELANA"), Slender.txt("§7Faltam §f" + (totalFolhas() - folhas) + " folhas§7. Não encare a máscara."),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.6f);
        p.sendMessage(Slender.PREFIXO + "§fVocê entrou na partida em andamento: §e" + folhas + "/" + totalFolhas() + " folhas§f já pegas.");
        p.sendMessage(Slender.PREFIXO + "§fAs folhas estão presas nas §eparedes§f dos lugares da floresta. Clique nelas para pegar.");
        avisar("§f" + p.getName() + " §7entrou na floresta.");
    }

    /**
     * A visao escura o tempo todo vem do teto escuro (Teto.java), sem efeito. Aqui ficam so as piscadas: o efeito
     * Escuridao do Minecraft, que pulsa sozinho a cada 4 s se ficar ligado direto, vem em ondas curtas a cada
     * escuridao-a-cada segundos (mais vezes com o Slenderman perto).
     */
    private void escuridao(Player p, Jogador j) {
        p.removePotionEffect(PotionEffectType.DARKNESS);
        j.proximoEscuro = tick + seg(10 + rnd.nextInt(10));
    }

    private void ondaDeEscuridao(Player p, Jogador j, boolean perigo) {
        PotionEffect atual = p.getPotionEffect(PotionEffectType.DARKNESS);
        // tira a escuridao infinita de versoes antigas
        if (atual != null && (atual.isInfinite() || atual.getDuration() > 400)) p.removePotionEffect(PotionEffectType.DARKNESS);
        if (tick < j.proximoEscuro) return;
        // media entre ondas e duracao de cada uma (config); com o Slenderman perto vem mais vezes e dura mais
        double cada = Math.max(8, pl.getConfig().getDouble("escuridao-a-cada", 10));
        double dura = Math.max(2, Math.min(15, pl.getConfig().getDouble("escuridao-duracao", 6)));
        if (perigo) {
            cada *= 0.52;
            dura *= 1.3;
        }
        // duracao do efeito e em ticks do servidor (20 por segundo); seg() e em voltas da partida (4 por segundo)
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, (int) Math.round(dura * 20), 0, false, false, false));
        j.proximoEscuro = tick + seg(cada * (0.9 + rnd.nextDouble() * 0.2));
    }

    private void colocarFolhas() {
        List<Floresta.Ponto> ps = new ArrayList<>(mapa.pontos);
        Collections.shuffle(ps, rnd);
        List<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < Folhas.TEXTOS.length; i++) idxs.add(i);
        Collections.shuffle(idxs, rnd);
        int n = Math.min(totalFolhas(), ps.size());
        for (int k = 0; k < n; k++) {
            Floresta.Ponto p = ps.get(k);
            Floresta.Ancora a = p.ancoras.get(rnd.nextInt(p.ancoras.size()));
            int idx = idxs.get(k % idxs.size());
            ItemFrame f = pl.colocarFolha(a, idx);
            if (f != null) folhasNoMapa.add(new Folha(f.getUniqueId(), idx, a, p.nome));
        }
        pl.getLogger().info("Folhas da partida: " + folhasNoMapa.stream()
                .map(f -> f.ponto() + " (" + f.ancora().x() + " " + f.ancora().y() + " " + f.ancora().z() + " " + f.ancora().face() + ")").toList());
    }

    void limparFolhas() {
        for (Folha f : folhasNoMapa) {
            Entity e = Bukkit.getEntity(f.quadro());
            if (e != null) e.remove();
        }
        folhasNoMapa.clear();
        for (Floresta.Ponto p : mapa.pontos)
            for (Floresta.Ancora a : p.ancoras) {
                Location l = a.frente(mundo);
                mundo.getChunkAt(l).load();
                for (Entity e : mundo.getNearbyEntities(l, 0.8, 0.8, 0.8))
                    if (e instanceof ItemFrame && e.getPersistentDataContainer().has(pl.kFolha, PersistentDataType.INTEGER)) e.remove();
            }
    }

    // ================================================================== jogo

    private int nivel() {
        long s = (tick - inicioTick) / 4;
        int bonus = folhas == 0 ? (s >= 150 ? 1 : 0) : (int) Math.min(2, s / 300);
        return Math.min(8, folhas + bonus);
    }

    private void jogar() {
        List<Player> vivos = vivos();
        if (vivos.isEmpty()) {
            if (alguemPodeVoltar()) {
                fimPendente = -1;
                return;
            }
            if (fimPendente < 0) fimPendente = tick + seg(2);
            if (tick >= fimPendente) derrota();
            return;
        }
        int nivel = nivel();
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null) continue;
            boolean perigo = j.estatica >= 35 || slender != null && slender.getWorld().equals(p.getWorld())
                    && slender.getLocation().distanceSquared(p.getLocation()) < 18 * 18;
            ondaDeEscuridao(p, j, perigo);
            // visao noturna (pocao, poder) acabaria com o escuro da floresta
            if (p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            if (tick % 40 == 0 && p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
        }
        iaSlender(vivos, nivel);
        if (estado != Estado.JOGANDO) return;
        estatica(vivos(), nivel);
        if (estado != Estado.JOGANDO) return;
        ambiente(vivos(), nivel);
        hud();
        hudFila();
    }

    private void iaSlender(List<Player> vivos, int nivel) {
        if (slender != null && !slender.isValid()) slender = null;
        if (slender != null) Pacote.repartir(pl, slender, modelo);
        if (slender == null) {
            if (nivel >= 1 && tick >= proximaAparicao) aparecer(vivos, nivel);
            return;
        }
        Player a = alvo == null ? null : Bukkit.getPlayer(alvo);
        if (a == null || !vivo(a)) {
            if (tick >= visivelAte) sumir(nivel);
            return;
        }
        Location sl = slender.getLocation();
        for (Player p : vivos) {
            if (p.getLocation().distanceSquared(sl) < 1.7 * 1.7) {
                pegar(p);
                return;
            }
        }
        boolean olhado = false;
        for (Player p : vivos) if (olhando(p)) olhado = true;
        virarPara(a);
        if (!assustou && olhando(a)) {
            assustou = true;
            a.playSound(a.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.7f, 0.5f);
            a.showTitle(Title.title(Component.empty(), Slender.txt("§4§k|||||||||"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(300), Duration.ofMillis(200))));
        }
        if (!olhado && nivel >= 3 && tick >= proximoPasso) {
            aproximar(a, Math.min(4.5, 1.2 + nivel * 0.4));
            proximoPasso = tick + Math.max(2, 6 - nivel / 2);
            if (slender != null && a.getLocation().distanceSquared(slender.getLocation()) < 1.7 * 1.7) {
                pegar(a);
                return;
            }
        }
        if (tick >= visivelAte) sumir(nivel);
    }

    private Player escolherAlvo(List<Player> vivos) {
        // quem esta sozinho corre mais perigo
        double total = 0;
        double[] peso = new double[vivos.size()];
        for (int i = 0; i < vivos.size(); i++) {
            double perto = 0;
            for (Player o : vivos) if (o != vivos.get(i) && o.getLocation().distanceSquared(vivos.get(i).getLocation()) < 15 * 15) perto++;
            peso[i] = perto == 0 ? 2 : 1;
            total += peso[i];
        }
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < vivos.size(); i++) {
            r -= peso[i];
            if (r <= 0) return vivos.get(i);
        }
        return vivos.get(0);
    }

    private void aparecer(List<Player> vivos, int nivel) {
        Player a = escolherAlvo(vivos);
        double d = Math.max(5, 18 - nivel * 1.8) + rnd.nextDouble() * 5;
        double r = rnd.nextDouble(), ang;
        if (nivel <= 2) ang = r < 0.7 ? 180 + rnd.nextGaussian() * 30 : (rnd.nextBoolean() ? 90 : -90) + rnd.nextGaussian() * 20;
        else if (r < 0.5) ang = 180 + rnd.nextGaussian() * 35;
        else if (r < 0.8) ang = (rnd.nextBoolean() ? 90 : -90) + rnd.nextGaussian() * 20;
        else {
            ang = rnd.nextGaussian() * 15;
            d += 4;
        }
        Location achou = null;
        for (int t = 0; t < 40 && achou == null; t++) {
            // primeiro tenta o lado escolhido; se nao der (cerca, arvores), qualquer lado e mais perto
            double desvio = t == 0 ? 0 : t < 16 ? rnd.nextGaussian() * 25 : rnd.nextDouble() * 360;
            double ar = Math.toRadians(a.getLocation().getYaw() + ang + desvio);
            double dd = (t < 16 ? d : Math.max(5, d - (t - 16) * 0.6)) + (t > 0 ? rnd.nextDouble() * 4 - 2 : 0);
            achou = chao(a.getX() - Math.sin(ar) * dd, a.getZ() + Math.cos(ar) * dd);
        }
        if (achou == null) {
            proximaAparicao = tick + seg(2);
            return;
        }
        criarSlender(achou);
        alvo = a.getUniqueId();
        assustou = false;
        virarPara(a);
        visivelAte = tick + seg(3 + rnd.nextDouble() * 3 + nivel * 0.5);
        proximoPasso = tick + seg(1.5);
        mundo.playSound(achou, Sound.BLOCK_GRASS_STEP, 0.6f, 0.6f);
    }

    private void criarSlender(Location l) {
        removerSlender();
        slender = mundo.spawn(l, Mannequin.class, m -> {
            m.setProfile(pl.perfilSlender());
            m.setDescription(null);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setPersistent(false);
            m.setGravity(false);
            AttributeInstance sc = m.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(pl.escala());
            m.getPersistentDataContainer().set(pl.kSlender, PersistentDataType.BYTE, (byte) 1);
        });
        criarModelo(0);
    }

    @SuppressWarnings("UnstableApiUsage")
    private static ItemStack itemModelo(int quadro) {
        // leather_helmet: no Bedrock o Geyser (corona-porcelana.json) troca pelo item 3D que a extensao GeyserDisplayEntity mostra
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                net.kyori.adventure.key.Key.key("corona", "props/porcelana_f" + quadro));
        return it;
    }

    /** Modelo 3D (quadro 0 parado, 1 susto) em cima do boneco. Recria do zero (sem deslizar). */
    private void criarModelo(int quadro) {
        if (modelo != null) modelo.remove();
        modelo = null;
        if (slender == null || !pl.modelo3d()) return;
        Location l = posModelo();
        float esc = (float) (pl.escala() / 1.3);
        modelo = mundo.spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(itemModelo(quadro));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, 1.5f * esc, 0), new Quaternionf(), new Vector3f(esc, esc, esc), new Quaternionf()));
            d.setViewRange(1.0f);
            d.setShadowRadius(0f);
            d.setTeleportDuration(3);
            d.setPersistent(false);
            d.setVisibleByDefault(false);     // so quem tem o pacote ve o modelo; os outros veem o boneco com skin
            d.getPersistentDataContainer().set(pl.kSlender, PersistentDataType.BYTE, (byte) 1);
        });
        Pacote.repartir(pl, slender, modelo);
    }

    private Location posModelo() {
        Location l = slender.getLocation();
        l.setYaw(l.getYaw() + pl.modeloGiro());
        l.setPitch(0);
        return l;
    }

    /** Leva o modelo junto quando o boneco anda (desliza em 3 ticks no Java). */
    private void moverModelo() {
        if (modelo == null || slender == null) return;
        if (!modelo.isValid()) {
            criarModelo(0);
            return;
        }
        modelo.teleport(posModelo());
    }

    private void removerSlender() {
        if (modelo != null) modelo.remove();
        modelo = null;
        if (slender != null) slender.remove();
        slender = null;
        alvo = null;
    }

    private void sumir(int nivel) {
        removerSlender();
        double base = Math.max(4, 22 - nivel * 2.3);
        proximaAparicao = tick + seg(base + rnd.nextDouble() * 5 - 2);
    }

    /** Lugar no chao onde ele cabe (sem tronco de arvore no meio), dentro do mapa. */
    private Location chao(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (bx < 3 || bz < 3 || bx >= Floresta.TAM - 3 || bz >= Floresta.TAM - 3) return null;
        int h = mapa.alturaEm(bx, bz);
        for (int y = h + 1; y <= h + 3; y++) {
            Block pe = mundo.getBlockAt(bx, y, bz);
            Block baixo = pe.getRelative(0, -1, 0);
            if (!baixo.getType().isSolid() || baixo.getType().name().contains("LEAVES") || baixo.getType().name().endsWith("_LOG")) continue;
            if (!pe.isPassable() || !pe.getRelative(0, 1, 0).isPassable()) continue;
            Block topo = pe.getRelative(0, 2, 0);
            if (!topo.isPassable() && !topo.getType().name().contains("LEAVES")) continue;
            return new Location(mundo, bx + 0.5, y, bz + 0.5);
        }
        return null;
    }

    private void aproximar(Player a, double passo) {
        Location s = slender.getLocation();
        Vector dir = a.getLocation().toVector().subtract(s.toVector()).setY(0);
        double dist = dir.length();
        if (dist < 0.1) return;
        double mover = Math.min(passo, dist - 0.6);
        if (mover <= 0) return;
        dir.normalize();
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        for (double lat : new double[] {0, 1.2, -1.2, 2.2, -2.2}) {
            double x = s.getX() + dir.getX() * mover + perp.getX() * lat, z = s.getZ() + dir.getZ() * mover + perp.getZ() * lat;
            Location c = chao(x, z);
            if (c != null && c.distanceSquared(a.getLocation()) < s.distanceSquared(a.getLocation())) {
                c.setX(x);
                c.setZ(z);
                slender.teleport(c);
                virarPara(a);
                moverModelo();
                return;
            }
        }
    }

    private void virarPara(Player p) {
        if (slender == null) return;
        Location s = slender.getLocation();
        Vector v = p.getEyeLocation().toVector().subtract(s.toVector().add(new Vector(0, 1.5 * pl.escala(), 0)));
        Location l = s.clone();
        l.setDirection(v);
        slender.setRotation(l.getYaw(), Math.max(-30, Math.min(30, l.getPitch())));
        if (modelo != null && modelo.isValid()) modelo.setRotation(l.getYaw() + pl.modeloGiro(), 0);
    }

    boolean olhando(Player p) {
        if (slender == null || !p.getWorld().equals(mundo)) return false;
        Location eye = p.getEyeLocation();
        Vector para = slender.getLocation().add(0, 1.5 * pl.escala(), 0).toVector().subtract(eye.toVector());
        double dist = para.length();
        if (dist > 40) return false;
        if (dist < 0.3) return true;
        double ang = Math.toDegrees(eye.getDirection().angle(para));
        double lim = dist < 6 ? 50 : dist < 14 ? 35 : 25;
        return ang <= lim && p.hasLineOfSight(slender);
    }

    private void estatica(List<Player> vivos, int nivel) {
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (j == null) continue;
            double delta;
            if (slender != null) {
                double dist = p.getLocation().distance(slender.getLocation());
                if (olhando(p)) delta = (1.4 + nivel * 0.35) * (dist < 8 ? 1.6 : 1.0);
                else if (dist < 7) delta = 0.8;
                else delta = -0.9;
            } else delta = -1.2;
            j.estatica = Math.max(0, Math.min(100, j.estatica + delta));
            if (j.estatica >= 100) {
                pegar(p);
                if (estado != Estado.JOGANDO) return;
                continue;
            }
            if (j.estatica >= 30 && rnd.nextDouble() < j.estatica / 100) {
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, (float) (j.estatica / 160), 0.5f + rnd.nextFloat());
            }
            if (j.estatica >= 55) p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, false, false, false));
            if (j.estatica >= 85 && tick % 16 == 0) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
            if (j.estatica >= 40 && delta > 0 && tick % 3 == 0) {
                p.showTitle(Title.title(Component.empty(), Slender.txt("§8§k" + "|".repeat(4 + (int) (j.estatica / 12))),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(150), Duration.ofMillis(100))));
            }
        }
    }

    private void pegar(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.vivo || estado != Estado.JOGANDO) return;
        j.vivo = false;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        dir.normalize();
        double esc = pl.escala();
        Location s = eye.clone().add(dir.clone().multiply(1.1));
        s.setY(eye.getY() - 1.5 * esc);
        Location ol = s.clone();
        ol.setDirection(eye.toVector().subtract(s.toVector().add(new Vector(0, 1.5 * esc, 0))));
        s.setYaw(ol.getYaw());
        s.setPitch(0);
        if (slender == null) criarSlender(s);
        else slender.teleport(s);
        criarModelo(1);
        alvo = p.getUniqueId();
        visivelAte = tick + seg(1.5);
        proximoPasso = Long.MAX_VALUE;
        p.setWalkSpeed(0f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 1, false, false, false));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 0.8f, 0.5f);
        p.showTitle(Title.title(Slender.txt("§4§k||||||||||"), Slender.txt("§cO Porcelana te pegou..."),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
        for (Player o : online(true)) {
            if (o == p) continue;
            o.playSound(o.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, 0.5f);
        }
        int restam = vivos().size();
        avisar("§c☠ §f" + j.nome + " §cfoi pego pelo Porcelana! §7(" + restam + " vivo" + (restam == 1 ? "" : "s") + ")");
        int espera = Reviver.segundos(pl);
        if (Reviver.moedas(p) > 0) j.reviverAte = tick + seg(espera + 4);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !participa(p) || estado == Estado.ESPERANDO) return;
            if (alvo != null && alvo.equals(p.getUniqueId())) sumir(nivel());
            if (j.vivo) return; // ja voltou
            espectador(p);
            if (estado == Estado.JOGANDO) Reviver.oferecer(pl, p, "o Porcelana", espera, () -> reviver(p));
        }, 30L);
    }

    /** Teste (admin): o Slenderman pega o jogador agora. */
    boolean pegarTeste(Player p) {
        if (!vivo(p)) return false;
        pegar(p);
        return true;
    }

    /** Alguem eliminado ainda pode gastar a Moeda de Reviver (a partida espera). */
    private boolean alguemPodeVoltar() {
        for (Jogador j : jogadores.values()) {
            if (!j.jogando || j.vivo || j.reviverAte < tick) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    /** Moeda de Reviver: o jogador volta para a floresta, perto de quem ainda esta vivo. */
    boolean reviver(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.jogando || j.vivo || estado != Estado.JOGANDO || !p.isOnline()) return false;
        List<Player> outros = vivos();
        j.vivo = true;
        j.estatica = 0;
        j.reviverAte = -1;
        fimPendente = -1;
        if (alvo != null && alvo.equals(p.getUniqueId())) alvo = null;
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setWalkSpeed(0.2f);
        p.setGameMode(GameMode.ADVENTURE);
        pl.lanterna().dar(p);
        escuridao(p, j);
        pl.mover(p, outros.isEmpty() ? mapa.inicio(mundo, rnd.nextInt(8)) : outros.get(rnd.nextInt(outros.size())).getLocation());
        p.showTitle(Title.title(Slender.txt("§a§lVOCÊ VOLTOU!"), Slender.txt("§7Ainda faltam §f" + (totalFolhas() - folhas) + " folhas§7. Não encare a máscara."),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(700))));
        avisar("§e✦ §f" + j.nome + " §eusou uma Moeda de Reviver e voltou para a floresta!");
        return true;
    }

    private void espectador(Player p) {
        p.setWalkSpeed(0.2f);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.SPECTATOR);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        p.sendMessage(Slender.PREFIXO + "§7Agora você assiste como espectador. §e/slender sair §7para voltar.");
    }

    void coletar(Player p, ItemFrame f) {
        if (!vivo(p)) return;
        Folha fl = null;
        for (Folha x : folhasNoMapa) if (x.quadro().equals(f.getUniqueId())) fl = x;
        if (fl == null) return;
        f.remove();
        folhasNoMapa.remove(fl);
        folhas++;
        int total = totalFolhas();
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        for (Player o : online(true)) {
            o.showTitle(Title.title(Component.empty(), Slender.txt("§fFolhas: §e" + folhas + "§7/" + total),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(700))));
        }
        avisar("§f" + p.getName() + " §7pegou uma folha §8(" + fl.ponto() + ")§7: §o\"" + Folhas.FRASES[fl.idx()] + "\"");
        if (folhas >= total) {
            vitoria();
            return;
        }
        if (folhas == 1) {
            proximaAparicao = tick + seg(8);
            avisar("§8Algo começou a seguir vocês...");
            for (Player o : vivos()) o.playSound(o.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.4f, 0.4f);
        } else if (slender == null && rnd.nextDouble() < 0.6) {
            proximaAparicao = Math.min(proximaAparicao, tick + seg(3 + rnd.nextInt(4)));
        }
    }

    private void ambiente(List<Player> vivos, int nivel) {
        if (vivos.isEmpty()) return;
        if (folhas >= 2) {
            int intervalo = Math.max(3, 9 - folhas);
            if (tick % intervalo == 0)
                for (Player p : vivos) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.4f + folhas * 0.06f, 1f);
        }
        if (tick % 20 == 0 && rnd.nextDouble() < 0.35) {
            Player p = vivos.get(rnd.nextInt(vivos.size()));
            Vector atras = p.getLocation().getDirection().setY(0);
            if (atras.lengthSquared() > 0.01) {
                Location l = p.getLocation().subtract(atras.normalize().multiply(4));
                p.playSound(l, Sound.BLOCK_GRASS_STEP, 0.8f, 0.7f);
            }
        }
        if (tick % 120 == 0 && rnd.nextDouble() < 0.5) for (Player p : vivos) p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 0.6f, 0.8f);
        if (folhas >= 5 && tick % 24 == 0) for (Player p : vivos) p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.25f, 0.5f);
    }

    private void hud() {
        if (tick % 2 != 0) return;
        int total = totalFolhas(), nv = vivos().size();
        for (Jogador j : jogadores.values()) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p == null) continue;
            if (j.vivo) {
                int cheio = (int) Math.round(j.estatica / 10);
                String cor = j.estatica < 40 ? "§7" : j.estatica < 70 ? "§6" : "§c";
                p.sendActionBar(Slender.txt("§fFolhas §e" + folhas + "§7/" + total + "   §8[" + cor + "|".repeat(cheio) + "§8" + ".".repeat(10 - cheio) + "§8]"));
            } else {
                p.sendActionBar(Slender.txt("§7Espectador §8| §fFolhas §e" + folhas + "§7/" + total + " §8| §fvivos §e" + nv));
            }
        }
    }

    // ================================================================== fim

    private void vitoria() {
        estado = Estado.FIM;
        removerSlender();
        long s = (tick - inicioTick) / 4;
        String tempo = String.format("%d:%02d", s / 60, s % 60);
        List<String> nomes = new ArrayList<>();
        for (Player p : online(true)) {
            Jogador j = jogadores.get(p.getUniqueId());
            p.showTitle(Title.title(Slender.txt("§a§lVOCÊS ESCAPARAM!"), Slender.txt("§7Todas as folhas em §f" + tempo),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            if (j != null && j.vivo) {
                nomes.add(p.getName());
                fogos(p.getLocation());
            }
        }
        pl.registrarVitoria(nomes, s, online(true));
        Bukkit.getScheduler().runTaskLater(pl, this::finalizar, 160L);
    }

    private void fogos(Location l) {
        Firework fw = mundo.spawn(l.clone().add(0, 1, 0), Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.WHITE, Color.RED).withFade(Color.BLACK).build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
        fw.setPersistent(false);
    }

    private void derrota() {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        removerSlender();
        for (Player p : online(true)) {
            p.showTitle(Title.title(Slender.txt("§4§lO PORCELANA VENCEU"), Slender.txt("§7Folhas coletadas: §f" + folhas + "/" + totalFolhas()),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 0.5f);
        }
        avisar("§cNinguém escapou. Folhas coletadas: §f" + folhas + "/" + totalFolhas());
        Bukkit.getScheduler().runTaskLater(pl, this::finalizar, 120L);
    }

    /** Fim da partida: devolve todo mundo que jogou; quem esperava no lobby fica para a proxima. */
    void finalizar() {
        removerSlender();
        limparFolhas();
        for (Jogador j : new ArrayList<>(jogadores.values())) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            jogadores.remove(j.id);
            if (p != null && p.isOnline()) {
                p.setWalkSpeed(0.2f);
                pl.devolver(p);
                p.sendMessage(Slender.PREFIXO + "§7Partida encerrada. Jogar de novo: §e/slender entrar");
            }
        }
        folhas = 0;
        fimPendente = -1;
        estado = Estado.ESPERANDO;
    }

    /** Admin: encerra agora, sem vencedor. */
    void parar() {
        if (estado == Estado.JOGANDO || estado == Estado.FIM) finalizar();
        else estado = Estado.ESPERANDO;
    }

    // ================================================================== admin / testes

    /** Admin: pega a proxima folha como se o jogador tivesse clicado. */
    boolean folhaTeste(Player p) {
        if (folhasNoMapa.isEmpty() || !vivo(p)) return false;
        Entity e = Bukkit.getEntity(folhasNoMapa.get(0).quadro());
        if (!(e instanceof ItemFrame f)) return false;
        coletar(p, f);
        return true;
    }

    /** Admin: faz ele aparecer agora perto de alguem. */
    void aparecerTeste(double distancia) {
        List<Player> v = vivos();
        if (v.isEmpty()) return;
        removerSlender();
        if (distancia <= 0) {
            aparecer(v, Math.max(1, nivel()));
            return;
        }
        Player a = v.get(0);
        Vector dir = a.getLocation().getDirection().setY(0).normalize();
        Location c = chao(a.getX() + dir.getX() * distancia, a.getZ() + dir.getZ() * distancia);
        if (c == null) return;
        criarSlender(c);
        alvo = a.getUniqueId();
        assustou = false;
        virarPara(a);
        visivelAte = tick + seg(30);
        proximoPasso = tick + seg(1.5);
    }

    String status() {
        StringBuilder est = new StringBuilder();
        for (Jogador j : jogadores.values()) if (j.jogando) est.append(j.nome).append('=').append((int) j.estatica).append(j.vivo ? "" : "(pego)").append(' ');
        return "estado=" + estado + " folhas=" + folhas + "/" + totalFolhas() + " jogadores=" + jogadores.size() + " vivos=" + vivos().size()
                + " slender=" + (slender != null ? String.format("%.1f %.1f %.1f", slender.getX(), slender.getY(), slender.getZ()) : "escondido")
                + " nivel=" + (estado == Estado.JOGANDO ? nivel() : 0) + " folhasNoMapa=" + folhasNoMapa.size() + " estatica=[" + est.toString().trim() + "]";
    }

    Mannequin slenderAtual() {
        return slender;
    }
}
