package br.servidor.naochore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
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
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Uma partida do Nao Chore. Ciclo 4 vezes por segundo. Dia 1 a 4: os Vigias aparecem escondidos olhando, cada dia
 * mais perto, mais vezes e com sustos maiores (grito, escuridao, cara na tela, batidas na porta, rosto na janela,
 * tochas apagando). Dia 5: os Famintos cacam todo mundo. Sobreviveu ate o amanhecer do 6o dia = venceu.
 */
final class Partida {

    enum Estado { ESPERANDO, PREPARANDO, CONTAGEM, JOGANDO, FIM }

    static final class Jogador {
        final UUID id;
        final String nome;
        boolean jogando, vivo;
        int vidas;
        long proximoVigia, proximoNoturno, proximoChoro;
        int passos;
        double passoDist;
        long proximoPasso, proximoMaos, proximoDuplo, proximoNina;
        Medo medo;
        long reviverAte = -1;

        Jogador(Player p) {
            id = p.getUniqueId();
            nome = p.getName();
        }
    }

    /** Um Vigia: manequim parado olhando para uma pessoa. tipo 0 = longe/escondido, 1 = atras, 2 = na janela, 3 = na cara. */
    static final class Vigia {
        final UUID dono;
        final Corpo m;
        final int tipo;
        long ate, olhadoDesde = -1, naoVistoDesde = -1, ultimoPasso = -1;
        /** tipo 1 dentro de casa: de costas (virada para a parede) ate ser vista. */
        boolean deCostas;

        Vigia(UUID dono, Corpo m, int tipo, long ate) {
            this.dono = dono;
            this.m = m;
            this.tipo = tipo;
            this.ate = ate;
        }
    }

    private final NaoChore pl;
    final Map<UUID, Jogador> jogadores = new LinkedHashMap<>();
    final List<Vigia> vigias = new ArrayList<>();
    final List<Monstro> monstros = new ArrayList<>();
    private final Random rnd = new Random();

    Estado estado = Estado.ESPERANDO;
    private long tick, inicioTick, fimEm = -1, proximoMonstro;
    private int contagem, diaAnunciado;
    /** Hora do mundo acumulada desde o comeco (24000 = um dia). */
    double hora;

    Partida(NaoChore pl) {
        this.pl = pl;
    }

    static long seg(double s) {
        return Math.round(s * 4);
    }

    World mundo() {
        return pl.mundo;
    }

    int dias() {
        return Math.max(1, pl.getConfig().getInt("dias", 5));
    }

    int dia() {
        return 1 + (int) (hora / 24000);
    }

    boolean noite() {
        double h = hora % 24000;
        return h >= 12800 && h < 23200;
    }

    // ================================================================== jogadores

    Jogador adicionar(Player p) {
        Jogador j = new Jogador(p);
        jogadores.put(p.getUniqueId(), j);
        return j;
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
        World w = mundo();
        for (Jogador j : jogadores.values()) {
            if (!j.jogando || !j.vivo) continue;
            Player p = Bukkit.getPlayer(j.id);
            if (p != null && p.isOnline() && w != null && p.getWorld().equals(w)) l.add(p);
        }
        return l;
    }

    void avisar(String msg) {
        for (Player p : online(false)) p.sendMessage(NaoChore.PREFIXO + msg);
    }

    void titulo(Player p, String t, String s, double fica) {
        p.showTitle(Title.title(NaoChore.txt(t), NaoChore.txt(s),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis((long) (fica * 1000)), Duration.ofMillis(800))));
    }

    // ================================================================== ciclo

    void ciclo() {
        tick++;
        switch (estado) {
            case ESPERANDO -> {
                int n = naFila().size();
                if (n > 0 && n >= pl.getConfig().getInt("min-jogadores", 1)) {
                    if (!pl.mundoPronto()) {
                        estado = Estado.PREPARANDO;
                        pl.prepararMundo();
                    } else comecarContagem();
                }
                hudFila();
            }
            case PREPARANDO -> {
                if (naFila().isEmpty()) estado = Estado.ESPERANDO;
                else if (pl.mundoPronto()) comecarContagem();
                if (tick % 8 == 0) for (Player p : naFila()) p.sendActionBar(NaoChore.txt("§7Preparando um mundo novo... §8(alguns segundos)"));
            }
            case CONTAGEM -> contar();
            case JOGANDO -> jogar();
            case FIM -> {
                if (fimEm > 0 && tick >= fimEm) finalizar();
            }
        }
    }

    private void comecarContagem() {
        estado = Estado.CONTAGEM;
        contagem = (int) seg(pl.getConfig().getInt("contagem-segundos", 25));
        avisar("§4Uma partida de §lNÃO CHORE §4começa em §f" + contagem / 4 + "s§4. Chame os amigos: §f/naochore entrar");
    }

    private void contar() {
        List<Player> fila = naFila();
        if (fila.isEmpty()) {
            estado = Estado.ESPERANDO;
            return;
        }
        if (fila.size() >= pl.getConfig().getInt("max-jogadores", 8)) contagem = (int) Math.min(contagem, seg(8));
        contagem--;
        if (contagem % 4 == 0) {
            int s = contagem / 4;
            for (Player p : fila) {
                p.sendActionBar(NaoChore.txt("§4Não Chore §8| §7começa em §f" + s + "s §8| §7jogadores: §f" + fila.size()));
                if (s <= 5 && s > 0) {
                    titulo(p, "§4" + s, "§8eles já sabem que você vem...", 0.9);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.5f);
                }
            }
        }
        if (contagem <= 0) iniciar();
    }

    private void hudFila() {
        if (tick % 4 != 0) return;
        for (Player p : naFila())
            p.sendActionBar(NaoChore.txt(estado == Estado.JOGANDO ? "§7Partida em andamento: §fdia " + dia() + "§7. Você entra na próxima."
                    : "§7Esperando jogadores... §8| §7na fila: §f" + naFila().size()));
    }

    // ================================================================== inicio e fim

    void iniciar() {
        List<Player> fila = naFila();
        World w = mundo();
        if (fila.isEmpty() || w == null) {
            estado = Estado.ESPERANDO;
            return;
        }
        estado = Estado.JOGANDO;
        pl.mundoUsado = true;
        inicioTick = tick;
        hora = 0;
        diaAnunciado = 0;
        proximoMonstro = 0;
        w.setTime(0);
        Location spawn = pl.spawn();
        int i = 0;
        for (Player p : fila) {
            Jogador j = jogadores.get(p.getUniqueId());
            try {
                pl.guarda.guardar(p);
            } catch (Exception e) {
                p.sendMessage(NaoChore.PREFIXO + "§cNão consegui guardar suas coisas. Você não entrou (nada foi perdido).");
                jogadores.remove(p.getUniqueId());
                continue;
            }
            pl.guardarPet(p);
            j.jogando = true;
            j.vivo = true;
            j.vidas = Math.max(1, pl.getConfig().getInt("vidas", 1));
            j.proximoVigia = tick + seg(35 + rnd.nextInt(25));
            j.proximoNoturno = tick + seg(30);
            j.proximoChoro = tick + seg(60);
            Guarda.limpar(p);
            p.setGameMode(GameMode.SURVIVAL);
            p.setLevel(0);
            p.setExp(0);
            p.getInventory().addItem(new ItemStack(Material.BREAD, 4));
            double a = i++ * 0.9;
            pl.mover(p, spawn.clone().add(Math.cos(a) * 2, 0, Math.sin(a) * 2));
            titulo(p, "§4§lNÃO CHORE", "§7Sobreviva §f" + dias() + " dias§7. Eles estão olhando.", 5);
            p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.5f);
            p.sendMessage(NaoChore.PREFIXO + "§fUm mundo pequeno, só seu. Junte recursos, construa um abrigo e §csobreviva " + dias() + " dias§f.");
            p.sendMessage(NaoChore.PREFIXO + "§7Algo te observa de longe. Se chegar perto... §8não chore.");
            p.sendMessage(NaoChore.PREFIXO + "§7No §4último dia§7 eles param de se esconder. §cPortas e janelas não vão segurar.");
        }
        if (vivos().isEmpty()) {
            estado = Estado.ESPERANDO;
            return;
        }
        avisar("§4A partida começou com §f" + vivos().size() + " §4jogador(es). Boa sorte...");
        pl.getLogger().info("Nao Chore: partida comecou com " + vivos().size() + " jogador(es).");
    }

    void entrarNoMeio(Player p) {
        // so entra quem estava na fila antes de comecar (evita entrar no dia 5 descansado)
        p.sendMessage(NaoChore.PREFIXO + "§7A partida já começou (dia " + dia() + "). Você entra na próxima.");
    }

    void pego(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.vivo) return;
        j.vidas--;
        if (j.vidas > 0) return;
        j.vivo = false;
        if (Reviver.moedas(p) > 0) j.reviverAte = tick + seg(Reviver.segundos(pl) + 4);
        avisar("§c" + p.getName() + " §7não sobreviveu ao §fdia " + dia() + "§7. §8(" + vivos().size() + " ainda vivos)");
        pl.getLogger().info("Nao Chore: " + p.getName() + " morreu no dia " + dia());
    }

    /** Depois de morrer: vira espectador no mesmo mundo, com o rosto do Faminto e o coro. */
    void virarEspectador(Player p) {
        p.setGameMode(GameMode.SPECTATOR);
        Sons.para(p, p.getLocation(), "naochore.voce_chorou", 1.2f, 1f, Sound.ENTITY_WARDEN_DEATH, 0.8f, 0.5f);
        naCara(p, "faminto");
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline()) titulo(p, "§4§lVOCÊ CHOROU", "§7Agora assiste os outros. §f/naochore sair §7para voltar.", 4);
        }, 24L);
    }

    /** Teste (admin): o mesmo que morrer com a ultima vida (o bot de teste nao morre de verdade). */
    void eliminarTeste(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null) return;
        j.vidas = 1;
        pego(p);
        if (!vivo(p)) {
            virarEspectador(p);
            oferecerReviver(p);
        }
    }

    /** Depois de virar espectador: oferece a Moeda de Reviver. */
    void oferecerReviver(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.jogando || j.vivo || estado != Estado.JOGANDO) return;
        int espera = Reviver.segundos(pl);
        if (Reviver.oferecer(pl, p, "o Não Chore", espera, () -> reviver(p))) j.reviverAte = tick + seg(espera + 2);
        else j.reviverAte = -1;
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

    /** Moeda de Reviver: volta vivo (1 vida, comida cheia, 4 paes) perto de quem ainda esta vivo. */
    boolean reviver(Player p) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null || !j.jogando || j.vivo || estado != Estado.JOGANDO || !p.isOnline() || mundo() == null) return false;
        List<Player> outros = vivos();
        j.vivo = true;
        j.vidas = 1;
        j.reviverAte = -1;
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.SURVIVAL);
        p.setFireTicks(0);
        p.setFallDistance(0);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(10);
        p.getInventory().addItem(new ItemStack(Material.BREAD, 4));
        pl.mover(p, outros.isEmpty() ? pl.spawn() : outros.get(rnd.nextInt(outros.size())).getLocation());
        titulo(p, "§a§lVOCÊ VOLTOU!", "§7Suas coisas ficaram onde você morreu. Sobreviva!", 4);
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1f);
        avisar("§e✦ §f" + j.nome + " §eusou uma Moeda de Reviver e voltou!");
        return true;
    }

    private void vitoria() {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        List<String> nomes = new ArrayList<>();
        for (Player p : online(true)) {
            Jogador j = jogadores.get(p.getUniqueId());
            boolean viveu = j != null && j.vivo;
            if (viveu) nomes.add(p.getName());
            titulo(p, viveu ? "§a§lVOCÊ SOBREVIVEU" : "§e§lELES SOBREVIVERAM", "§7aos " + dias() + " dias. O sol nasceu.", 6);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        }
        if (!nomes.isEmpty()) Bukkit.broadcast(NaoChore.txt(NaoChore.PREFIXO + "§a" + String.join(", ", nomes) + " §fsobreviveu aos §c" + dias() + " dias §fdo Não Chore!"));
        for (String n : nomes)
            for (String c : pl.getConfig().getStringList("recompensa-comandos"))
                Bukkit.getScheduler().runTaskLater(pl, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{jogador}", n)), 220L);
        fimEm = tick + seg(10);
    }

    private void derrota() {
        if (estado != Estado.JOGANDO) return;
        estado = Estado.FIM;
        for (Player p : online(true)) {
            titulo(p, "§4§lTODOS CHORARAM", "§7Ninguém sobreviveu (dia " + dia() + ").", 5);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_DEATH, 0.8f, 0.5f);
        }
        avisar("§cNinguém sobreviveu. Chegaram até o §fdia " + dia() + "§c.");
        fimEm = tick + seg(8);
    }

    void finalizar() {
        limparMonstros();
        for (Jogador j : new ArrayList<>(jogadores.values())) {
            if (!j.jogando) continue;
            Player p = Bukkit.getPlayer(j.id);
            jogadores.remove(j.id);
            if (p != null && p.isOnline()) pl.devolver(p);
        }
        fimEm = -1;
        estado = Estado.ESPERANDO;
        // o mundo foi usado: apaga e faz outro (com calma, sem ninguem dentro)
        Bukkit.getScheduler().runTaskLater(pl, pl::renovarMundo, 100L);
    }

    void parar() {
        if (estado == Estado.JOGANDO || estado == Estado.FIM) finalizar();
        else estado = Estado.ESPERANDO;
    }

    void limparMonstros() {
        for (Vigia v : vigias) v.m.remove();
        vigias.clear();
        for (Monstro m : monstros) m.remover();
        monstros.clear();
    }

    // ================================================================== durante a partida

    private void jogar() {
        World w = mundo();
        List<Player> vivos = vivos();
        if (vivos.isEmpty()) {
            if (alguemPodeVoltar()) return;
            derrota();
            return;
        }
        // o tempo anda mais rapido: um dia inteiro em dia-minutos
        double passo = 24000.0 / (Math.max(2, pl.getConfig().getDouble("dia-minutos", 8)) * 60 * 4);
        hora += passo;
        w.setTime((long) (hora % 24000));
        int d = dia();
        if (d > dias()) {
            vitoria();
            return;
        }
        if (d != diaAnunciado) anunciarDia(d, vivos);
        for (Player p : vivos) {
            Jogador j = jogadores.get(p.getUniqueId());
            if (d < dias()) {
                vigias(p, j, d);
                noturnos(p, j, d);
                maos(p, j, d);
                duplo(p, j, d);
            }
            passos(p, j);
            if (j.medo == null) j.medo = new Medo(pl);
            double distV = 999;
            boolean olhado = false;
            for (Vigia v : vigias) {
                if (!v.dono.equals(p.getUniqueId()) || !v.m.valido()) continue;
                double dd = v.m.getLocation().distance(p.getLocation());
                if (dd < distV) distV = dd;
                if (v.tipo == 0 && dd < 30) olhado = true;
            }
            for (Monstro m : monstros) if (m.vivo()) distV = Math.min(distV, m.corpo.getLocation().distance(p.getLocation()));
            j.medo.atualizar(p, d, noite(), distV, olhado, tick);
        }
        cuidarVigias(vivos, d);
        if (d >= dias()) famintos(vivos);
        if (tick % 4 == 0) hud(vivos);
    }

    private void anunciarDia(int d, List<Player> vivos) {
        diaAnunciado = d;
        boolean ultimo = d >= dias();
        for (Player p : online(true)) {
            titulo(p, (ultimo ? "§4§l" : "§c§l") + "DIA " + d, ultimo ? "§4§lELES PARARAM DE SE ESCONDER" : frase(d), ultimo ? 5 : 3.5);
            p.playSound(p.getLocation(), ultimo ? Sound.ENTITY_WARDEN_EMERGE : Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            if (ultimo) {
                Sons.para(p, p.getLocation(), "corrida_rugido", 2f, 0.7f, Sound.ENTITY_WARDEN_ROAR, 2f, 0.5f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, false));
            }
        }
        if (ultimo) {
            for (Vigia v : vigias) v.m.remove();
            vigias.clear();
            proximoMonstro = tick + seg(20);
        }
    }

    private static String frase(int d) {
        return switch (d) {
            case 1 -> "§7junte o que puder antes da noite";
            case 2 -> "§7eles estão mais perto...";
            case 3 -> "§7eles sabem onde você dorme";
            default -> "§7eles estão logo ali. sempre.";
        };
    }

    private void hud(List<Player> vivos) {
        double h = hora % 24000;
        String quando;
        if (h < 12800) quando = "§enoite em §f" + mmss((12800 - h) / (24000.0 / (pl.getConfig().getDouble("dia-minutos", 8) * 60)));
        else if (h < 23200) quando = "§9amanhece em §f" + mmss((23200 - h) / (24000.0 / (pl.getConfig().getDouble("dia-minutos", 8) * 60)));
        else quando = "§eamanhecendo";
        String txt = "§4Dia " + dia() + "§8/§4" + dias() + " §8| " + quando + " §8| §7vivos: §f" + vivos.size();
        for (Player p : online(true)) p.sendActionBar(NaoChore.txt(vivo(p) ? txt : "§7Espectador §8| " + txt));
    }

    private static String mmss(double s) {
        int t = (int) Math.max(0, s);
        return String.format("%d:%02d", t / 60, t % 60);
    }

    // ================================================================== os Vigias (dias 1 a 4)

    /** De quanto em quanto tempo um Vigia aparece para cada pessoa (segundos), por dia. */
    private static double intervalo(int d) {
        return switch (d) {
            case 1 -> 80;
            case 2 -> 65;
            case 3 -> 50;
            default -> 35;
        };
    }

    private static double[] distancia(int d) {
        return switch (d) {
            case 1 -> new double[] {34, 48};
            case 2 -> new double[] {24, 38};
            case 3 -> new double[] {16, 30};
            default -> new double[] {10, 22};
        };
    }

    /** Chegou a essa distancia (qualquer jogador vivo), ele pula na cara: susto forte desde o dia 1. */
    private static double gatilho(int d) {
        return switch (d) {
            case 1, 2 -> 8;
            case 3 -> 9;
            default -> 10;
        };
    }

    private boolean temVigia(Player p) {
        for (Vigia v : vigias) if (v.dono.equals(p.getUniqueId())) return true;
        return false;
    }

    private void vigias(Player p, Jogador j, int d) {
        if (tick < j.proximoVigia || temVigia(p)) return;
        double cada = intervalo(d) * (noite() ? 0.7 : 1.0);
        j.proximoVigia = tick + seg(cada * (0.7 + rnd.nextDouble() * 0.6));
        double[] dist = distancia(d);
        Location l = escondido(p, dist[0], dist[1]);
        if (l == null) return;
        String skin = d >= 3 && rnd.nextBoolean() ? "chorona" : "vigia";
        Corpo m = boneco(p, l, skin, 1.0 + (d - 1) * 0.07, false);
        vigias.add(new Vigia(p.getUniqueId(), m, 0, tick + seg(d == 1 ? 60 : 45)));
        if (d >= 2 && rnd.nextInt(3) == 0) p.playSound(l, Sound.AMBIENT_CAVE, 0.6f, 0.5f);
        if (skin.equals("chorona")) Sons.para(p, l, "naochore.choro", 1f, 1f, Sound.ENTITY_GHAST_AMBIENT, 0.8f, 1.4f);
    }

    /** Um lugar entre min e max blocos que tenha cobertura (arvore, um bloco no meio) para ele ficar meio escondido. */
    Location escondido(Player p, double min, double max) {
        World w = p.getWorld();
        Location c = p.getLocation();
        Location melhor = null;
        int pontos = -1;
        for (int k = 0; k < 28; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = min + rnd.nextDouble() * (max - min);
            int x = (int) Math.floor(c.getX() + Math.cos(a) * r), z = (int) Math.floor(c.getZ() + Math.sin(a) * r);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z)) || !w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Location l = chao(w, x, z, c.getBlockY());
            if (l == null) continue;
            int s = 0;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int dy = 0; dy <= 4; dy++) {
                        Material m = w.getBlockAt(x + dx, l.getBlockY() + dy, z + dz).getType();
                        if (m.name().endsWith("_LEAVES") || m.name().endsWith("_LOG")) {
                            s++;
                            break;
                        }
                    }
            Vector para = c.toVector().subtract(l.toVector()).setY(0).normalize();
            Block meio = l.clone().add(para.multiply(1.5)).getBlock();
            if (meio.getType().isSolid()) s += 4;
            if (noite() && l.getBlock().getLightLevel() < 7) s += 2;
            if (s > pontos) {
                pontos = s;
                melhor = l;
            }
        }
        return melhor;
    }

    /** Chao andavel na coluna (perto da altura do jogador, para nao aparecer em cima de montanha ou no fundo de caverna). */
    static Location chao(World w, int x, int z, int yRef) {
        int top = w.getHighestBlockYAt(x, z);
        int[] tentativas = {top, yRef, yRef + 1, yRef - 1, yRef + 2, yRef - 2};
        for (int y0 : tentativas) {
            Block pe = w.getBlockAt(x, y0 + (y0 == top ? 1 : 0), z);
            Block chao = pe.getRelative(BlockFace.DOWN);
            if (!chao.getType().isSolid() || chao.isLiquid() || chao.getType() == Material.LAVA) continue;
            if (!pe.isPassable() || pe.isLiquid() || !pe.getRelative(BlockFace.UP).isPassable()) continue;
            if (Math.abs(pe.getY() - yRef) > 12) continue;
            return new Location(w, x + 0.5, pe.getY(), z + 0.5);
        }
        return null;
    }

    Corpo boneco(Player quem, Location l, String skin, double escala, boolean soDele) {
        Location o = l.clone();
        o.setDirection(quem.getEyeLocation().toVector().subtract(l.clone().add(0, 1.6, 0).toVector()));
        o.setPitch(0);
        return new Corpo(o, skin, escala, soDele ? quem.getUniqueId() : null);
    }

    private boolean olhando(Player p, Location l, double ang) {
        Location olho = p.getEyeLocation();
        Vector para = l.toVector().subtract(olho.toVector());
        if (para.lengthSquared() < 0.01) return true;
        return Math.toDegrees(olho.getDirection().angle(para.normalize())) < ang;
    }

    private void cuidarVigias(List<Player> vivos, int d) {
        for (Iterator<Vigia> it = vigias.iterator(); it.hasNext(); ) {
            Vigia v = it.next();
            Player p = Bukkit.getPlayer(v.dono);
            if (p == null || !vivo(p) || !v.m.valido()) {
                v.m.remove();
                it.remove();
                continue;
            }
            Location vl = v.m.getLocation();
            // o corpo acompanha a pessoa (devagar, sem piscar) e respira
            if (!v.deCostas) v.m.olharPara(p.getEyeLocation());
            v.m.respirar();
            v.m.visibilidade(false);
            double dist = p.getLocation().distance(vl);
            // quem chegar mais perto (o dono ou qualquer outro vivo na partida) leva o susto
            Player alvo = p;
            double distAlvo = dist;
            for (Player outro : vivos) {
                if (outro.getWorld() != vl.getWorld()) continue;
                double dd = outro.getLocation().distance(vl);
                if (dd < distAlvo) {
                    distAlvo = dd;
                    alvo = outro;
                }
            }
            boolean viu = olhando(p, vl.clone().add(0, 1.5, 0), 25) && p.hasLineOfSight(v.m.entidade());
            if (viu && v.olhadoDesde < 0) v.olhadoDesde = tick;
            if (!viu) v.olhadoDesde = -1;
            boolean acabou = tick >= v.ate;
            switch (v.tipo) {
                case 0 -> {
                    if (distAlvo < gatilho(d)) {
                        gritar(alvo, v.m, d);
                        acabou = true;
                    } else if (viu) {
                        v.naoVistoDesde = -1;
                        // encarou muito tempo (dia 1-2): ele some num sussurro. Dia 3+: encarar nao adianta
                        if (d <= 2 && tick - v.olhadoDesde > seg(5)) {
                            Sons.para(p, vl, "naochore.sussurro_nome", 0.8f, 1f, Sound.ENTITY_ENDERMAN_AMBIENT, 0.6f, 0.4f);
                            sumir(p, v.m, false);
                            acabou = true;
                        }
                    } else {
                        // nao esta olhando: a cada 1,5-2,5 s sem ser visto, chega mais perto (quanto mais dias, mais rapido)
                        if (v.naoVistoDesde < 0) v.naoVistoDesde = tick;
                        long espera = seg(d >= 4 ? 1.2 : d == 3 ? 1.6 : 2.2);
                        if (tick - v.naoVistoDesde >= seg(0.6) && (v.ultimoPasso < 0 || tick - v.ultimoPasso >= espera) && dist > gatilho(d) - 1) {
                            double frac = d >= 3 ? 0.4 : 0.3;
                            Location perto = chao(p.getWorld(), (int) Math.floor(vl.getX() + (p.getX() - vl.getX()) * frac),
                                    (int) Math.floor(vl.getZ() + (p.getZ() - vl.getZ()) * frac), p.getLocation().getBlockY());
                            if (perto != null) {
                                v.m.teleport(perto);
                                v.ultimoPasso = tick;
                                // um passo seco na folhagem, atras de voce
                                p.playSound(perto, Sound.BLOCK_GRASS_STEP, 0.8f, 0.6f);
                                if (dist < 16) Sons.para(p, perto, "naochore.respiracao", 0.9f, 1f, Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.5f);
                            }
                        }
                    }
                }
                case 1 -> {
                    // atras: quando a pessoa vira, ele grita (dentro de casa a Chorona esta de costas e vira na hora)
                    if (viu) {
                        if (v.deCostas) {
                            v.deCostas = false;
                            v.m.olharPara(p.getEyeLocation());
                            Sons.para(p, vl, "naochore.estatica", 0.7f, 1f, Sound.BLOCK_CONDUIT_AMBIENT, 0.5f, 0.4f);
                            v.ate = Math.min(v.ate, tick + 12);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 16, 3, false, false, false));
                        } else {
                            gritar(p, v.m, Math.max(3, d));
                            acabou = true;
                        }
                    }
                }
                case 2 -> {
                    // na janela
                    if (viu && tick - v.olhadoDesde > seg(0.7)) {
                        gritar(p, v.m, Math.max(3, d));
                        acabou = true;
                    }
                }
                default -> { }
            }
            if (acabou) {
                if (v.m.valido()) {
                    if (v.tipo == 1 && !v.deCostas && tick >= v.ate && dist < 6) gritar(p, v.m, Math.max(3, d));
                    else if (v.tipo != 0 || distAlvo > gatilho(d)) sumir(p, v.m, false);
                    v.m.remove();
                }
                it.remove();
            }
        }
    }

    private void sumir(Player p, Corpo m, boolean alto) {
        Location l = m.getLocation().add(0, 1, 0);
        m.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 18, 0.3, 0.7, 0.3, 0.02);
        p.playSound(l, alto ? Sound.ENTITY_ENDERMAN_TELEPORT : Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 0.5f);
        m.remove();
    }

    /** O susto: o rosto pula na tela com um grito alto desde o dia 1. Cada dia pior: dia 2 + "NÃO CHORE"; dia 3 + tontura; dia 4 + um segundo rosto. */
    void gritar(Player p, Corpo m, int d) {
        Location l = m.getLocation().add(0, 1.6, 0);
        float vol = d >= 4 ? 2f : d == 3 ? 1.8f : 1.6f;
        String tipo = m.tipo;
        Jogador jj = jogadores.get(p.getUniqueId());
        if (jj != null && jj.medo != null) jj.medo.susto();
        // ele some de onde estava e aparece colado na cara
        m.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 12, 0.3, 0.6, 0.3, 0.02);
        naCara(p, tipo);
        String som = somGrito(tipo);
        Sons.para(p, p.getEyeLocation(), som, vol, 0.95f + rnd.nextFloat() * 0.1f, Sound.ENTITY_ENDERMAN_SCREAM, vol, 0.45f);
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, vol * 0.8f, 0.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.9f, 1.4f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50 + d * 15, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false, false));
        if (d >= 2) Bukkit.getScheduler().runTaskLater(pl, () -> titulo(p, "", "§4§lNÃO CHORE", 1.2), 14L);
        if (d >= 3) {
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.5f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false, false));
        }
        if (d >= 4 && rnd.nextBoolean()) Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && vivo(p)) naCara(p, "chorona");
        }, 30L);
        // quem estiver perto escuta o grito
        for (Player o : p.getWorld().getPlayers())
            if (o != p && o.getLocation().distanceSquared(p.getLocation()) < 24 * 24) Sons.para(o, p.getLocation(), som, 1.2f, 1f, Sound.ENTITY_ENDERMAN_SCREAM, 1.2f, 0.45f);
        // tranco na camera
        p.setVelocity(p.getVelocity().add(new Vector((rnd.nextDouble() - 0.5) * 0.5, 0.25, (rnd.nextDouble() - 0.5) * 0.5)));
    }

    /** Caractere da fonte do pacote com o rosto gigante (so Java com o pacote). */
    static String glifo(String skin) {
        return switch (skin) {
            case "chorona" -> "\uE001";
            case "faminto" -> "\uE002";
            case "duplo" -> "\uE003";
            default -> "\uE000";
        };
    }

    /** Quadrado preto da fonte (\uE0F0) e recuo de 1 px (\uF801): tapa a tela toda em volta do rosto. */
    static final String PRETO = "\uE0F0", RECUO = "\uF801";

    static String somGrito(String tipo) {
        return switch (tipo) {
            case "chorona" -> "naochore.grito_chorona";
            case "faminto" -> "naochore.rosnado";
            case "duplo" -> "naochore.grito_duplo";
            default -> "naochore.grito_vigia";
        };
    }

    /** O rosto colado na tela (modelo 3D na frente da camera + rosto gigante na tela para quem tem o pacote). */
    void naCara(Player p, String skin) {
        Location olho = p.getLocation();
        Vector f = olho.getDirection().setY(0);
        if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
        f.normalize();
        Location l = olho.clone().add(f.multiply(1.1));
        if (!l.getBlock().isPassable()) l = olho.clone().add(f.multiply(-0.4));
        Corpo m = boneco(p, l, skin, skin.equals("faminto") ? 1.0 : 1.05, true);
        m.quadro("grito");
        if (Sons.temPacote(p) && !Sons.bedrock(p)) {
            // rosto em 3 tempos: longe, perto, na cara
            String[] fontes = {"susto_p", "susto_m", "susto"};
            for (int k = 0; k < 3; k++) {
                final int kk = k;
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (!p.isOnline()) return;
                    // o ultimo quadro: rosto no meio e quadrados pretos dos dois lados (cobre a tela inteira)
                    String texto = kk == 2 ? PRETO + RECUO + PRETO + RECUO + glifo(skin) + RECUO + PRETO + RECUO + PRETO : glifo(skin);
                    p.showTitle(net.kyori.adventure.title.Title.title(
                            net.kyori.adventure.text.Component.text(texto).font(net.kyori.adventure.key.Key.key("corona", fontes[kk])),
                            net.kyori.adventure.text.Component.empty(),
                            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(kk == 2 ? 700 : 200), java.time.Duration.ofMillis(kk == 2 ? 250 : 0))));
                }, kk * 3L);
            }
            Sons.para(p, p.getEyeLocation(), "naochore.estatica", 0.9f, 1.2f, null, 0f, 0f);
        }
        Sons.para(p, l, somGrito(skin), 2f, 1f, Sound.ENTITY_ENDERMAN_SCREAM, 2f, 0.4f);
        p.playSound(l, Sound.ENTITY_GHAST_HURT, 1.4f, 0.5f);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            m.remove();
            if (p.isOnline()) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
        }, 16L);
    }

    // ================================================================== sustos da noite (dias 2 a 4)

    private void noturnos(Player p, Jogador j, int d) {
        // choro de longe a noite (cada dia mais perto)
        if (noite() && tick >= j.proximoChoro) {
            j.proximoChoro = tick + seg(40 + rnd.nextInt(40) - d * 6);
            double r = Math.max(8, 40 - d * 8);
            Location l = p.getLocation().add((rnd.nextDouble() - 0.5) * 2 * r, 0, (rnd.nextDouble() - 0.5) * 2 * r);
            if (Sons.temPacote(p) && !Sons.bedrock(p)) {
                Sons.para(p, l, rnd.nextInt(4) == 0 ? "naochore.risada" : "naochore.choro", 0.5f + d * 0.15f, 1f, null, 0f, 0f);
            } else for (int k = 0; k < 3 + d; k++) {
                final int kk = k;
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    if (p.isOnline()) p.playSound(l, Sound.ENTITY_GHAST_AMBIENT, 0.35f + d * 0.12f, 1.35f + rnd.nextFloat() * 0.15f);
                }, kk * 14L);
            }
        }
        // caixinha de musica desafinada dentro de casa (so de noite, de vez em quando)
        if (noite() && d >= 2 && tick >= j.proximoNina && temTeto(p) && rnd.nextInt(10) == 0) {
            j.proximoNina = tick + seg(120 + rnd.nextInt(120));
            Location canto = p.getLocation().add((rnd.nextDouble() - 0.5) * 8, 0.5, (rnd.nextDouble() - 0.5) * 8);
            Sons.para(p, canto, "naochore.nina", 0.9f, 1f, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.6f);
            if (j.medo != null) j.medo.nivel = Math.max(j.medo.nivel, 0.6);
        }
        if (!noite() || tick < j.proximoNoturno || temVigia(p)) return;
        j.proximoNoturno = tick + seg((d == 1 ? 75 : d == 2 ? 55 : d == 3 ? 40 : 30) * (0.7 + rnd.nextDouble() * 0.6));
        int susto = rnd.nextInt(d >= 4 ? 5 : d == 3 ? 4 : 2);
        switch (susto) {
            case 0 -> {
                // passos atras chegando perto e parando
                j.passos = 5 + rnd.nextInt(3);
                j.passoDist = 10;
                j.proximoPasso = tick;
            }
            case 1 -> {
                // aparece atras; se virar, grita
                Vector f = p.getLocation().getDirection().setY(0);
                if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
                Location atras = chao(p.getWorld(), (int) Math.floor(p.getX() - f.normalize().getX() * 3), (int) Math.floor(p.getZ() - f.getZ() * 3), p.getLocation().getBlockY());
                if (atras == null) return;
                boolean dentro = temTeto(p);
                Corpo m = boneco(p, atras, dentro ? "chorona" : "vigia", dentro ? 1.0 : 1.1, true);
                Vigia vg = new Vigia(p.getUniqueId(), m, 1, tick + seg(dentro ? 14 : 9));
                if (dentro) {
                    // num canto, de costas para voce, chorando baixinho
                    vg.deCostas = true;
                    Location o = atras.clone();
                    o.setDirection(atras.toVector().subtract(p.getLocation().toVector()).setY(0));
                    m.rotacao(o.getYaw(), 20);
                    Sons.para(p, atras, "naochore.choro", 0.9f, 1.1f, Sound.ENTITY_GHAST_AMBIENT, 0.6f, 1.4f);
                    abrirPorta(p);
                } else Sons.para(p, atras, "naochore.respiracao", 1.2f, 1f, Sound.ENTITY_PLAYER_BREATH, 1f, 0.5f);
                vigias.add(vg);
            }
            case 2 -> {
                // batidas na porta mais perto
                Block porta = perto(p, 10, true);
                if (porta == null) return;
                Sons.para(p, porta.getLocation(), "naochore.batida_porta", 1.4f, 1f, null, 0f, 0f);
                for (int k = 0; k < 3; k++) {
                    final int kk = k;
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        if (p.isOnline() && (!Sons.temPacote(p) || Sons.bedrock(p))) p.playSound(porta.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.2f, 0.6f + kk * 0.05f);
                    }, kk * 9L);
                }
            }
            case 3 -> {
                // rosto na janela (vidro perto de quem esta dentro de casa)
                Block vidro = perto(p, 8, false);
                if (vidro == null) return;
                Vector fora = vidro.getLocation().add(0.5, 0, 0.5).toVector().subtract(p.getLocation().toVector()).setY(0);
                if (fora.lengthSquared() < 0.01) return;
                Location l = vidro.getLocation().add(0.5, 0, 0.5).add(fora.normalize().multiply(1.2));
                Location pe = chao(p.getWorld(), l.getBlockX(), l.getBlockZ(), vidro.getY() - 1);
                if (pe == null) return;
                Corpo m = boneco(p, pe, "chorona", 1.0, true);
                vigias.add(new Vigia(p.getUniqueId(), m, 2, tick + seg(12)));
                p.playSound(vidro.getLocation(), Sound.BLOCK_GLASS_HIT, 1f, 0.5f);
                Sons.para(p, vidro.getLocation(), "naochore.arranhao", 1f, 1f, Sound.BLOCK_GLASS_HIT, 0.8f, 0.4f);
            }
            default -> {
                // as tochas perto apagam (caem no chao)
                int n = 0;
                Location c = p.getLocation();
                for (int dx = -7; dx <= 7 && n < 8; dx++)
                    for (int dy = -3; dy <= 4 && n < 8; dy++)
                        for (int dz = -7; dz <= 7 && n < 8; dz++) {
                            Block b = c.getWorld().getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz);
                            Material m = b.getType();
                            if (m == Material.TORCH || m == Material.WALL_TORCH || m == Material.LANTERN || m == Material.SOUL_TORCH || m == Material.SOUL_WALL_TORCH) {
                                b.breakNaturally();
                                c.getWorld().spawnParticle(Particle.SMOKE, b.getLocation().add(0.5, 0.5, 0.5), 8, 0.1, 0.1, 0.1, 0.01);
                                n++;
                            }
                        }
                if (n > 0) {
                    p.playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
                    p.sendActionBar(NaoChore.txt("§8...algo apagou suas luzes"));
                } else naCara(p, "vigia");
            }
        }
    }

    /** Tem teto em cima (ate 7 blocos): esta dentro de alguma construcao/caverna. */
    static boolean temTeto(Player p) {
        Block b = p.getLocation().getBlock();
        for (int k = 1; k <= 7; k++) {
            Block c = b.getRelative(0, k, 0);
            if (c.getType().isSolid() || c.getType().name().contains("GLASS") || c.getType().name().contains("SLAB") || c.getType().name().contains("STAIRS")) return true;
        }
        return false;
    }

    /** A porta mais perta range e abre sozinha. */
    private void abrirPorta(Player p) {
        Block porta = perto(p, 8, true);
        if (porta == null) return;
        Sons.para(p, porta.getLocation(), "naochore.porta_rangendo", 1.2f, 1f, Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 0.5f);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (porta.getBlockData() instanceof org.bukkit.block.data.Openable o && !o.isOpen()) {
                o.setOpen(true);
                porta.setBlockData(o, true);
            }
        }, 30L);
    }

    /** Maos saem do chao e seguram o pe (de noite, na mata, dia 2+). */
    private void maos(Player p, Jogador j, int d) {
        if (d < 2 || !noite() || tick < j.proximoMaos || temTeto(p) || !p.isOnGround()) return;
        if (rnd.nextInt(d >= 4 ? 160 : 260) != 0) return;
        j.proximoMaos = tick + seg(90 + rnd.nextInt(120));
        Block chao = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
        String n = chao.getType().name();
        if (!(n.contains("GRASS") || n.contains("DIRT") || n.contains("PODZOL") || n.contains("MOSS") || n.contains("SAND") || n.contains("MUD"))) return;
        Location l = p.getLocation().clone();
        l.setYaw(p.getLocation().getYaw());
        Corpo m = new Corpo(l, "maos", 1.3, null);
        Sons.para(p, l, "naochore.maos", 1.3f, 1f, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.5f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45, 5, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
        p.setVelocity(new Vector(0, -0.2, 0));
        p.sendActionBar(NaoChore.txt("§4algo segurou o seu pé"));
        if (j.medo != null) j.medo.nivel = Math.max(j.medo.nivel, 0.85);
        Bukkit.getScheduler().runTaskLater(pl, () -> m.quadro("grito"), 8L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            m.remove();
            p.getWorld().spawnParticle(Particle.BLOCK, l.clone().add(0, 0.2, 0), 25, 0.4, 0.1, 0.4, 0, chao.getBlockData());
        }, 45L);
    }

    /** O Duplo (dia 3+): uma figura parecida com gente, parada longe, te olhando. Chegou perto: a mandibula cai e ele grita. */
    private void duplo(Player p, Jogador j, int d) {
        if (d < 3 || tick < j.proximoDuplo || temVigia(p) || rnd.nextInt(d >= 4 ? 70 : 110) != 0) return;
        j.proximoDuplo = tick + seg(150 + rnd.nextInt(150));
        Location l = escondido(p, 18, 30);
        if (l == null) return;
        Location o = l.clone();
        o.setDirection(p.getEyeLocation().toVector().subtract(l.clone().add(0, 1.6, 0).toVector()));
        o.setPitch(0);
        Corpo m = new Corpo(o, "duplo", 1.08, p.getUniqueId());
        vigias.add(new Vigia(p.getUniqueId(), m, 0, tick + seg(40)));
        Sons.para(p, l, "naochore.estatica", 0.5f, 0.8f, Sound.BLOCK_CONDUIT_AMBIENT, 0.4f, 0.5f);
    }

    /** Porta (true) ou vidro (false) mais perto num raio. */
    private Block perto(Player p, int r, boolean porta) {
        Location c = p.getLocation();
        Block melhor = null;
        double md = 1e9;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -2; dy <= 3; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    Block b = c.getWorld().getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz);
                    String n = b.getType().name();
                    boolean ok = porta ? n.endsWith("_DOOR") && !n.startsWith("IRON") : n.contains("GLASS");
                    if (!ok) continue;
                    double d = b.getLocation().distanceSquared(c);
                    if (d < md) {
                        md = d;
                        melhor = b;
                    }
                }
        return melhor;
    }

    private void passos(Player p, Jogador j) {
        if (j.passos <= 0 || tick < j.proximoPasso) return;
        Vector atras = p.getLocation().getDirection().setY(0);
        if (atras.lengthSquared() < 1e-4) atras = new Vector(1, 0, 0);
        Location onde = p.getLocation().add(atras.normalize().multiply(-j.passoDist));
        Block chao = onde.getBlock().getRelative(BlockFace.DOWN);
        Sound s = chao.getType().isSolid() ? chao.getBlockData().getSoundGroup().getStepSound() : Sound.BLOCK_GRASS_STEP;
        p.playSound(onde, s, 1.2f, 0.7f);
        j.passoDist = Math.max(2, j.passoDist - 1.4);
        j.passos--;
        j.proximoPasso = tick + 2;
        if (j.passos == 0 && rnd.nextBoolean()) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1f, 0.5f);
    }

    // ================================================================== dia 5: os Famintos

    private void famintos(List<Player> vivos) {
        int quer = Math.min(8, pl.getConfig().getInt("cacadores-base", 2) + vivos.size() + (noite() ? 2 : 0));
        monstros.removeIf(m -> {
            if (m.vivo()) return false;
            m.remover();
            return true;
        });
        if (monstros.size() < quer && tick >= proximoMonstro) {
            proximoMonstro = tick + seg(noite() ? 15 : 30);
            Player alvo = vivos.get(rnd.nextInt(vivos.size()));
            Location l = lugarLonge(alvo, 35, 55);
            if (l != null) {
                Monstro m = new Monstro(pl, mundo());
                m.criar(l, pl.getConfig().getDouble("cacador-vida", 80));
                monstros.add(m);
                for (Player p : vivos) if (p.getLocation().distanceSquared(l) < 80 * 80)
                    Sons.para(p, l, "distante", 1.4f, 0.6f, Sound.ENTITY_WARDEN_ROAR, 1f, 0.6f);
            }
        }
        double dano = pl.getConfig().getDouble("cacador-dano", 6);
        for (Monstro m : monstros) m.ciclo(vivos, noite(), dano);
    }

    /** Todo tick. */
    void sincronizar() {
        if (estado != Estado.JOGANDO || monstros.isEmpty()) return;
        List<Player> vivos = vivos();
        for (Monstro m : monstros) {
            Player alvo = null;
            double md = 1e18;
            if (m.vivo()) for (Player p : vivos) {
                double d = p.getLocation().distanceSquared(m.motor.getLocation());
                if (d < md) {
                    md = d;
                    alvo = p;
                }
            }
            m.sincronizar(alvo);
        }
    }

    Location lugarLonge(Player p, double min, double max) {
        World w = p.getWorld();
        for (int k = 0; k < 30; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = min + rnd.nextDouble() * (max - min);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) continue;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Location l = chao(w, x, z, p.getLocation().getBlockY());
            if (l != null) return l;
        }
        return null;
    }

    // ================================================================== admin

    String status() {
        StringBuilder sb = new StringBuilder("estado=" + estado + " dia=" + dia() + " hora=" + (long) (hora % 24000) + " noite=" + noite()
                + " vigias=" + vigias.size() + " famintos=" + monstros.size() + " jogadores=[");
        for (Jogador j : jogadores.values()) sb.append(j.nome).append(j.jogando ? (j.vivo ? "(vivo)" : "(morto)") : "(fila)").append(' ');
        return sb.toString().trim() + "]";
    }

    void pularDia(int d) {
        if (estado != Estado.JOGANDO) return;
        hora = (Math.max(1, Math.min(dias(), d)) - 1) * 24000.0 + 1;
    }

    void sustoTeste(Player p, int tipo) {
        Jogador j = jogadores.get(p.getUniqueId());
        if (j == null) return;
        switch (tipo) {
            case 0 -> {
                Location l = escondido(p, 12, 20);
                if (l != null) vigias.add(new Vigia(p.getUniqueId(), boneco(p, l, rnd.nextBoolean() ? "vigia" : "chorona", 1.1, false), 0, tick + seg(30)));
            }
            case 1 -> {
                j.proximoNoturno = 0;
                hora = Math.max(hora, (dia() - 1) * 24000.0 + 14000);
            }
            case 3 -> {
                j.proximoMaos = 0;
                Location l = p.getLocation();
                Corpo m = new Corpo(l, "maos", 1.3, null);
                Sons.para(p, l, "naochore.maos", 1.3f, 1f, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.5f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45, 5, false, false, false));
                Bukkit.getScheduler().runTaskLater(pl, () -> m.quadro("grito"), 8L);
                Bukkit.getScheduler().runTaskLater(pl, m::remove, 45L);
            }
            case 4 -> {
                Location l = escondido(p, 10, 16);
                if (l == null) l = p.getLocation().add(6, 0, 0);
                Location o = l.clone();
                o.setDirection(p.getEyeLocation().toVector().subtract(l.clone().add(0, 1.6, 0).toVector()));
                o.setPitch(0);
                Corpo m = new Corpo(o, "duplo", 1.08, p.getUniqueId());
                vigias.add(new Vigia(p.getUniqueId(), m, 0, tick + seg(40)));
            }
            case 5 -> {
                Vector f = p.getLocation().getDirection().setY(0).normalize();
                Location atras = p.getLocation().add(f.multiply(-3));
                Corpo m = boneco(p, atras, "chorona", 1.0, true);
                Vigia vg = new Vigia(p.getUniqueId(), m, 1, tick + seg(14));
                vg.deCostas = true;
                Location o = atras.clone();
                o.setDirection(atras.toVector().subtract(p.getLocation().toVector()).setY(0));
                m.rotacao(o.getYaw(), 20);
                Sons.para(p, atras, "naochore.choro", 0.9f, 1.1f, Sound.ENTITY_GHAST_AMBIENT, 0.6f, 1.4f);
                abrirPorta(p);
                vigias.add(vg);
            }
            case 6 -> virarEspectador(p);
            default -> naCara(p, "chorona");
        }
    }
}
