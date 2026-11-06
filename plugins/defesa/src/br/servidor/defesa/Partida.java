package br.servidor.defesa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Mannequin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

/** Uma partida (uma por mapa): ondas, torres, Nucleo, Essencia e dificuldade. */
final class Partida {

    enum Estado { ESPERANDO, CONTAGEM, ONDA, INTERVALO, FIM }

    static final class Torre {
        final UUID stand;
        final TipoTorre tipo;
        final Location local;
        int nivel = 1;
        int investido;
        int recarga;

        Torre(UUID stand, TipoTorre tipo, Location local, int investido) {
            this.stand = stand;
            this.tipo = tipo;
            this.local = local;
            this.investido = investido;
        }
    }

    static final class Monstro {
        final UUID id;
        final String nome;
        final int danoNucleo;
        final int essencia;
        final double velocidade;
        final boolean chefe;
        int indice = 1;
        Location ultimaPos;
        int parado;
        int venenoAte;
        double venenoDps;

        Monstro(UUID id, String nome, int danoNucleo, int essencia, double velocidade, boolean chefe) {
            this.id = id;
            this.nome = nome;
            this.danoNucleo = danoNucleo;
            this.essencia = essencia;
            this.velocidade = velocidade;
            this.chefe = chefe;
        }
    }

    private static final class Surgimento {
        final TipoMonstro tipo;
        final boolean chefe;

        Surgimento(TipoMonstro tipo, boolean chefe) {
            this.tipo = tipo;
            this.chefe = chefe;
        }
    }

    final DefesaTorre plugin;
    final Mapa mapa;
    final World mundo;

    Estado estado = Estado.ESPERANDO;
    Dificuldade dif = Dificuldade.NORMAL;
    int onda = 0;
    int vidaNucleo;
    int essencia;
    int contagem;
    boolean forcado = false;
    boolean venceu = false;
    private int tick = 0;
    /** Aumenta a cada reset para cancelar bombas do morteiro ainda no ar. */
    private int geracao = 0;

    final Map<UUID, Monstro> monstros = new HashMap<>();
    final Map<UUID, Torre> torres = new HashMap<>();
    private final Deque<Surgimento> fila = new ArrayDeque<>();
    private final Map<UUID, Integer> itensEssencia = new HashMap<>();
    final Map<UUID, TipoTorre> colocando = new HashMap<>();
    final Map<UUID, Long> colocandoAte = new HashMap<>();
    private UUID chefeId;
    private double chefeVidaMax = 1;

    private final Scoreboard placar;
    private final Objective objetivo;
    private final List<String> linhasAntigas = new ArrayList<>();
    private final BossBar barraNucleo;
    private final BossBar barraChefe;

    Partida(DefesaTorre plugin, Mapa mapa, World mundo) {
        this.plugin = plugin;
        this.mapa = mapa;
        this.mundo = mundo;
        this.vidaNucleo = dif.vidaNucleo;
        this.essencia = dif.essenciaInicial;
        placar = Bukkit.getScoreboardManager().getNewScoreboard();
        objetivo = placar.registerNewObjective("defesa", Criteria.DUMMY, DefesaTorre.txt("§6§lDEFESA DA TORRE"));
        objetivo.setDisplaySlot(DisplaySlot.SIDEBAR);
        barraNucleo = BossBar.bossBar(DefesaTorre.txt("§cNúcleo"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        barraChefe = BossBar.bossBar(DefesaTorre.txt("§5Boss"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    }

    // ================================================================== montagem

    void prepararArena() {
        int n = 0;
        for (Entity e : mundo.getEntities()) {
            if (e instanceof Player || !mapa.contem(e.getLocation())) continue;
            e.remove();
            n++;
        }
        if (n > 0) plugin.getLogger().info("[" + mapa.id + "] Limpei " + n + " entidades antigas.");
        mundo.spawn(mapa.nucleo(mundo), EnderCrystal.class, c -> {
            c.setShowingBottom(true);
            c.setInvulnerable(true);
            c.customName(DefesaTorre.txt("§c§lNÚCLEO §7(proteja!)"));
            c.setCustomNameVisible(true);
            c.getPersistentDataContainer().set(plugin.kNucleo, PersistentDataType.BYTE, (byte) 1);
        });
        mundo.spawn(mapa.loja(mundo), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setRemoveWhenFarAway(false);
            v.setProfession(mapa.tema == Mapa.Tema.VULCAO ? Villager.Profession.WEAPONSMITH : Villager.Profession.LIBRARIAN);
            v.customName(DefesaTorre.txt("§6§lLOJA DE TORRES §7(clique)"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(plugin.kLoja, PersistentDataType.STRING, mapa.id);
        });
    }

    // ================================================================== utilidades

    List<Player> jogadores() {
        List<Player> r = new ArrayList<>();
        for (Player p : mundo.getPlayers()) {
            if (mapa.contem(p.getLocation())) r.add(p);
        }
        return r;
    }

    void avisar(String msg) {
        for (Player p : jogadores()) p.sendMessage(msg);
    }

    private void barraTodos(String msg) {
        for (Player p : jogadores()) p.sendActionBar(DefesaTorre.txt(msg));
    }

    private void tituloTodos(String titulo, String sub) {
        Title t = Title.title(DefesaTorre.txt(titulo), DefesaTorre.txt(sub));
        for (Player p : jogadores()) p.showTitle(t);
    }

    private void somTodos(Sound s, float vol, float pitch) {
        for (Player p : jogadores()) p.playSound(p.getLocation(), s, vol, pitch);
    }

    private String chaveRecorde(Dificuldade d) {
        return "recordes." + mapa.id + "." + d.id;
    }

    int recorde(Dificuldade d) {
        return plugin.getConfig().getInt(chaveRecorde(d), 0);
    }

    String recordistas(Dificuldade d) {
        return plugin.getConfig().getString("recordes-jogadores." + mapa.id + "." + d.id, "");
    }

    int vitorias(Dificuldade d) {
        return plugin.getConfig().getInt("vitorias." + mapa.id + "." + d.id, 0);
    }

    private void linha(Location a, Location b, Particle p) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) return;
        d.normalize();
        for (double s = 0; s <= len; s += 0.5) mundo.spawnParticle(p, a.clone().add(d.clone().multiply(s)), 1, 0, 0, 0, 0);
    }

    private void linhaCor(Location a, Location b, Color cor, float tamanho) {
        Particle.DustOptions op = new Particle.DustOptions(cor, tamanho);
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) return;
        d.normalize();
        for (double s = 0; s <= len; s += 0.35) mundo.spawnParticle(Particle.DUST, a.clone().add(d.clone().multiply(s)), 1, 0, 0, 0, 0, op);
    }

    String ondaTexto() {
        return dif.infinito() ? String.valueOf(onda) : onda + "/" + dif.ondas;
    }

    // ================================================================== placar e barras

    String estadoTexto() {
        return switch (estado) {
            case ESPERANDO -> "§7esperando";
            case CONTAGEM -> "§ecomeçando em " + contagem + "s";
            case ONDA -> "§conda em andamento";
            case INTERVALO -> "§apróxima em " + contagem + "s";
            default -> venceu ? "§a§lVITÓRIA!" : "§4núcleo destruído";
        };
    }

    void atualizarPlacar() {
        List<String> linhas = new ArrayList<>();
        linhas.add("§8§m          ");
        linhas.add(mapa.cor + "§l" + mapa.nome);
        linhas.add("§fDificuldade: " + dif.cor + dif.nome);
        linhas.add("§f" + estadoTexto());
        linhas.add("§fOnda: §e" + ondaTexto() + (onda > 0 && onda % 5 == 0 && estado == Estado.ONDA ? " §5§lBOSS" : ""));
        linhas.add("§fNúcleo: §c" + Math.max(0, vidaNucleo) + "§7/" + dif.vidaNucleo);
        linhas.add("§fEssência: §d" + essencia);
        linhas.add("§fMonstros: §e" + monstros.size() + (fila.isEmpty() ? "" : " §7(+" + fila.size() + ")"));
        linhas.add("§fTorres: §a" + torres.size() + "§7/" + dif.limiteTorres);
        linhas.add("§fRecorde: §6onda " + recorde(dif));
        linhas.add("§7§m          ");
        linhas.add("§e/defesa loja");
        for (String antiga : linhasAntigas) {
            if (!linhas.contains(antiga)) placar.resetScores(antiga);
        }
        int score = linhas.size();
        for (String l : linhas) objetivo.getScore(l).setScore(score--);
        linhasAntigas.clear();
        linhasAntigas.addAll(linhas);
    }

    private void atualizarBarras() {
        float prog = Math.max(0f, Math.min(1f, vidaNucleo / (float) dif.vidaNucleo));
        barraNucleo.progress(prog);
        barraNucleo.name(DefesaTorre.txt("§c§lNÚCLEO §f" + Math.max(0, vidaNucleo) + "/" + dif.vidaNucleo
                + (onda > 0 ? "  §7|  §eOnda " + ondaTexto() : "") + "  §7|  " + dif.cor + dif.nome));
        barraNucleo.color(prog > 0.5f ? BossBar.Color.GREEN : prog > 0.25f ? BossBar.Color.YELLOW : BossBar.Color.RED);
        if (chefeId != null) {
            Entity e = Bukkit.getEntity(chefeId);
            if (e instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                barraChefe.progress((float) Math.max(0.0, Math.min(1.0, le.getHealth() / chefeVidaMax)));
                for (Player p : jogadores()) p.showBossBar(barraChefe);
            } else {
                esconderChefe();
            }
        }
    }

    private void esconderChefe() {
        chefeId = null;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barraChefe);
    }

    void entrou(Player p) {
        p.setScoreboard(placar);
        p.showBossBar(barraNucleo);
        if (chefeId != null) p.showBossBar(barraChefe);
        atualizarPlacar();
    }

    void saiu(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.hideBossBar(barraNucleo);
        p.hideBossBar(barraChefe);
        colocando.remove(p.getUniqueId());
        colocandoAte.remove(p.getUniqueId());
        plugin.tirarItemColocar(p);
    }

    // ================================================================== loop do jogo

    /** Velocidade 2x ligada por algum jogador (/defesa 2x ou botao na loja): o relogio da partida anda dobrado. */
    boolean rapido;

    void tique() {
        tick += 5;
        if (!torres.isEmpty() && !monstros.isEmpty()) torresAtacam();
        if (tick % 10 == 0) moverMonstros();
        if (tick % 20 == 0) porSegundo();
        if (tick % 40 == 0 && !torres.isEmpty()) efeitosSuporte();
    }

    private void porSegundo() {
        long agora = System.currentTimeMillis();
        monstros.keySet().removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid() || e.isDead();
        });
        aplicarVeneno();
        recolherEssencias();
        if (simulando) executarPlano();
        colocandoAte.entrySet().removeIf(en -> {
            if (en.getValue() < agora) {
                colocando.remove(en.getKey());
                Player p = Bukkit.getPlayer(en.getKey());
                if (p != null) {
                    plugin.tirarItemColocar(p);
                    p.sendActionBar(DefesaTorre.txt("§7Compra cancelada (demorou demais para colocar)."));
                }
                return true;
            }
            return false;
        });
        for (UUID id : colocando.keySet()) {
            Player p = Bukkit.getPlayer(id);
            TipoTorre t = colocando.get(id);
            if (p != null && t != null) {
                long resta = Math.max(0, (colocandoAte.getOrDefault(id, agora) - agora) / 1000);
                p.sendActionBar(DefesaTorre.txt("§aOlhe para o chão e clique para colocar: " + t.cor + t.nome + " §7(" + resta + "s)"));
            }
        }
        List<Player> js = jogadores();
        if (mapa.tema == Mapa.Tema.VULCAO && !js.isEmpty()) {
            mundo.spawnParticle(Particle.ASH, mapa.centro(mundo).add(0, 3, 0), 60, 16, 4, 18, 0);
            mundo.spawnParticle(Particle.LAVA, mapa.centro(mundo), 3, 18, 0.5, 20, 0);
        }

        if (estado != Estado.ESPERANDO && !forcado && js.isEmpty()) {
            plugin.getLogger().info("[" + mapa.id + "] Todos sairam: reiniciando a partida.");
            resetar(false);
        }

        switch (estado) {
            case CONTAGEM -> {
                if (contagem <= 3 && contagem > 0) somTodos(Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
                if (contagem > 0) tituloTodos("§e" + contagem, "§7Prepare as torres! " + dif.cor + "(" + dif.nome + ")");
                if (--contagem < 0) comecarOnda();
            }
            case ONDA -> {
                int porSeg = onda >= 10 ? 2 : 1;
                for (int i = 0; i < porSeg && !fila.isEmpty(); i++) surgir(fila.poll());
                if (fila.isEmpty() && monstros.isEmpty()) fimDaOnda();
            }
            case INTERVALO -> {
                if (contagem > 0 && contagem <= 5) somTodos(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
                barraTodos("§aPróxima onda em §f" + contagem + "s §7- torres: §a" + torres.size() + "/" + dif.limiteTorres + " §7(/defesa loja)");
                if (--contagem < 0) comecarOnda();
            }
            case FIM -> {
                if (--contagem < 0) resetar(true);
            }
            default -> { }
        }
        if (!js.isEmpty()) {
            atualizarPlacar();
            atualizarBarras();
        }
        atualizarNomes();
    }

    private void atualizarNomes() {
        for (Monstro mo : monstros.values()) {
            if (mo.chefe) continue;
            Entity e = Bukkit.getEntity(mo.id);
            if (e instanceof LivingEntity le) {
                String veneno = mo.venenoAte > tick ? " §2☠" : "";
                le.customName(DefesaTorre.txt("§c" + mo.nome + " §f" + (int) Math.ceil(le.getHealth()) + "§c❤" + veneno));
            }
        }
    }

    boolean iniciar(CommandSender quem, Dificuldade d) {
        if (estado != Estado.ESPERANDO) {
            quem.sendMessage("§eA partida já está rolando (onda " + ondaTexto() + ", " + dif.nome + ").");
            return false;
        }
        if (d != null) definirDificuldade(d);
        estado = Estado.CONTAGEM;
        contagem = 10;
        String fim = dif.infinito() ? "§5sem fim: até o Núcleo cair" : "§fvençam §e" + dif.ondas + " ondas";
        avisar("§6[Defesa] §f" + quem.getName() + " iniciou a partida no " + mapa.cor + mapa.nome + " §f- dificuldade "
                + dif.cor + "§l" + dif.nome + "§f!");
        avisar("§7Objetivo: " + fim + "§7. Limite de §a" + dif.limiteTorres + " torres§7. Primeira onda em 10s.");
        avisar("§7Dica: compre torres na §6Loja §7(o aldeão) ou com §e/defesa loja§7.");
        plugin.getLogger().info("[" + mapa.id + "] Partida iniciada por " + quem.getName() + " (" + dif.id + ")" + (forcado ? " (forcada)" : ""));
        return true;
    }

    /** So muda antes de iniciar: ajusta Nucleo e Essencia inicial. */
    void definirDificuldade(Dificuldade d) {
        if (estado != Estado.ESPERANDO) return;
        dif = d;
        vidaNucleo = d.vidaNucleo;
        int gasto = 0;
        for (Torre t : torres.values()) gasto += t.investido;
        essencia = Math.max(0, d.essenciaInicial - gasto);
    }

    private void comecarOnda() {
        onda++;
        estado = Estado.ONDA;
        montarFila();
        boolean chefe = onda % 5 == 0;
        boolean ultima = !dif.infinito() && onda == dif.ondas;
        if (ultima) {
            tituloTodos("§6§lONDA FINAL!", "§eAguentem essa e vocês vencem!");
            somTodos(Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.8f);
        } else if (chefe) {
            tituloTodos("§5§lONDA " + ondaTexto(), "§dUm BOSS está chegando!");
            somTodos(Sound.ENTITY_WITHER_SPAWN, 0.6f, 1f);
        } else {
            tituloTodos("§c§lONDA " + ondaTexto(), "§7" + fila.size() + " monstros a caminho");
            somTodos(Sound.EVENT_RAID_HORN, 0.8f, 1f);
        }
        plugin.getLogger().info("[" + mapa.id + "] Onda " + onda + " comecou: " + fila.size() + " monstros" + (chefe ? " + BOSS" : ""));
    }

    private void montarFila() {
        fila.clear();
        boolean chefe = onda % 5 == 0;
        int n = (int) Math.round(Math.min(6 + 2 * onda, 40) * dif.quantidade);
        if (chefe) n = n / 2;
        List<TipoMonstro> liberados = new ArrayList<>();
        int pesoTotal = 0;
        for (TipoMonstro t : TipoMonstro.values()) {
            if (onda >= t.ondaMinima) {
                liberados.add(t);
                pesoTotal += t.peso;
            }
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            int sorte = r.nextInt(pesoTotal);
            TipoMonstro escolhido = liberados.get(0);
            for (TipoMonstro t : liberados) {
                sorte -= t.peso;
                if (sorte < 0) {
                    escolhido = t;
                    break;
                }
            }
            fila.add(new Surgimento(escolhido, false));
        }
        if (chefe) fila.add(new Surgimento(null, true));
    }

    double multVida() {
        double m = (1.0 + 0.2 * (onda - 1)) * dif.vidaMonstros * mapa.vidaMult * dif.rampaVida(onda);
        // no Infinito, depois da onda 30 fica 3% mais dificil a cada onda
        if (dif.infinito() && onda > 30) m *= Math.pow(1.03, onda - 30);
        return m;
    }

    @SuppressWarnings("unchecked")
    private void surgir(Surgimento s) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location loc = mapa.ponto(mundo, 0).add(r.nextDouble(-1, 1), 0, r.nextDouble(-1, 1));
        if (s.chefe) {
            int numChefe = onda / 5;
            TipoChefe c = TipoChefe.values()[(numChefe - 1) % TipoChefe.values().length];
            double vida = Math.min(5000.0, c.vida * (1.0 + 0.35 * (numChefe - 1)) * dif.vidaMonstros * mapa.vidaMult);
            EntityType tipo = mapa.trocas.getOrDefault(c.tipo, c.tipo);
            String nome = mapa.trocas.containsKey(c.tipo) ? c.nome.replace("Zumbi", "Piglin") : c.nome;
            Class<Mob> cls = (Class<Mob>) tipo.getEntityClass();
            Mob m = mundo.spawn(loc, cls, false, mob -> configurar(mob, "§5§l" + nome, vida, c.escala));
            m.setGlowing(true);
            if (m instanceof IronGolem g) g.setPlayerCreated(false);
            Monstro mo = new Monstro(m.getUniqueId(), nome, 40, 60, 0.9, true);
            monstros.put(m.getUniqueId(), mo);
            chefeId = m.getUniqueId();
            chefeVidaMax = Math.min(VIDA_MAX, vida);
            barraChefe.name(DefesaTorre.txt("§5§lBOSS: §d" + nome));
            barraChefe.progress(1f);
            for (Player p : jogadores()) p.showBossBar(barraChefe);
            avisar("§5§l[BOSS] §d" + nome + " §7entrou na arena com §c" + DefesaTorre.num(vida) + " de vida§7!");
            plugin.getLogger().info("[" + mapa.id + "] Boss " + nome + " surgiu (vida " + DefesaTorre.num(vida) + ")");
            limparNivel(m, vida, "§5§l" + nome);
            return;
        }
        TipoMonstro t = s.tipo;
        double vida = Math.min(3000.0, t.vida * multVida());
        EntityType tipo = mapa.trocas.getOrDefault(t.tipo, t.tipo);
        String nome = mapa.nomesTroca.getOrDefault(t.tipo, t.nome);
        Class<Mob> cls = (Class<Mob>) tipo.getEntityClass();
        Mob m = mundo.spawn(loc, cls, false, mob -> configurar(mob, "§c" + nome, vida, 1.0));
        int ess = Math.max(1, (int) Math.round(t.essencia * dif.essenciaDrop));
        monstros.put(m.getUniqueId(), new Monstro(m.getUniqueId(), nome, t.danoNucleo, ess, t.velocidade, false));
        mundo.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.3);
        limparNivel(m, vida, "§c" + nome);
    }

    /** O Minecraft limita a vida em 1024: acima disso o monstro ganha resistencia para compensar. */
    static final double VIDA_MAX = 1024.0;

    private void configurar(Mob m, String nome, double vidaDesejada, double escala) {
        double vida = Math.min(VIDA_MAX, vidaDesejada);
        if (vidaDesejada > VIDA_MAX) {
            int nivel = (int) Math.min(4, Math.round((1.0 - VIDA_MAX / vidaDesejada) / 0.2));
            if (nivel > 0) m.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, nivel - 1, false, false));
        }
        m.customName(DefesaTorre.txt(nome));
        m.setCustomNameVisible(true);
        m.setRemoveWhenFarAway(false);
        m.setCanPickupItems(false);
        m.getPersistentDataContainer().set(plugin.kMob, PersistentDataType.STRING, mapa.id);
        AttributeInstance hp = m.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(vida);
        m.setHealth(vida);
        if (escala != 1.0) {
            AttributeInstance sc = m.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(escala);
        }
        if (m instanceof Zombie z) {
            z.setAdult();
            z.setShouldBurnInDay(false);
        }
        if (m instanceof AbstractSkeleton sk) sk.setShouldBurnInDay(false);
    }

    /** Se outro plugin (ex.: LevelledMobs) mexer na vida/velocidade do monstro, desfaz. */
    private void limparNivel(Mob m, double vidaDesejada, String nome) {
        double vida = Math.min(VIDA_MAX, vidaDesejada);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.isValid()) return;
            boolean mexeu = false;
            for (Attribute a : new Attribute[] {Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED, Attribute.ATTACK_DAMAGE, Attribute.ARMOR}) {
                AttributeInstance ai = m.getAttribute(a);
                if (ai == null) continue;
                for (AttributeModifier mod : new ArrayList<>(ai.getModifiers())) {
                    ai.removeModifier(mod);
                    mexeu = true;
                }
            }
            AttributeInstance hp = m.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null && hp.getBaseValue() != vida) {
                hp.setBaseValue(vida);
                mexeu = true;
            }
            if (mexeu) {
                m.setHealth(Math.min(vida, hp != null ? hp.getValue() : vida));
                m.customName(DefesaTorre.txt(nome));
            }
        }, 3L);
    }

    private double progresso(Mob m, Monstro mo) {
        Location alvo = mapa.ponto(mundo, Math.min(mo.indice, mapa.caminho.length - 1));
        return mo.indice * 1000.0 - m.getLocation().distance(alvo);
    }

    private void moverMonstros() {
        for (Monstro mo : new ArrayList<>(monstros.values())) {
            Entity e = Bukkit.getEntity(mo.id);
            if (!(e instanceof Mob m) || !m.isValid() || m.isDead()) {
                monstros.remove(mo.id);
                continue;
            }
            Location aqui = m.getLocation();
            if (aqui.getY() < Mapa.Y - 15) {
                m.remove();
                monstros.remove(mo.id);
                continue;
            }
            Location alvo = mapa.ponto(mundo, mo.indice);
            double dx = aqui.getX() - alvo.getX();
            double dz = aqui.getZ() - alvo.getZ();
            if (dx * dx + dz * dz < 2.25) {
                mo.indice++;
                if (mo.indice >= mapa.caminho.length) {
                    chegouNoNucleo(m, mo);
                    continue;
                }
                alvo = mapa.ponto(mundo, mo.indice);
            }
            m.setTarget(null);
            m.getPathfinder().moveTo(alvo, rapido ? mo.velocidade * 2 : mo.velocidade);
            if (mo.ultimaPos != null && mo.ultimaPos.getWorld().equals(aqui.getWorld()) && mo.ultimaPos.distanceSquared(aqui) < 0.04) {
                mo.parado += 10;
                if (mo.parado >= 100) {
                    m.teleport(alvo);
                    mo.parado = 0;
                }
            } else {
                mo.parado = 0;
            }
            mo.ultimaPos = aqui;
        }
    }

    private void chegouNoNucleo(Mob m, Monstro mo) {
        monstros.remove(mo.id);
        Location l = m.getLocation();
        m.remove();
        vidaNucleo -= mo.danoNucleo;
        mundo.spawnParticle(Particle.EXPLOSION, l.clone().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
        mundo.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        mundo.spawnParticle(Particle.DAMAGE_INDICATOR, mapa.nucleo(mundo).add(0, 1.5, 0), 10, 0.4, 0.4, 0.4, 0.1);
        barraTodos("§c" + mo.nome + " atingiu o Núcleo! §4-" + mo.danoNucleo);
        somTodos(Sound.ENTITY_PLAYER_HURT, 0.6f, 0.6f);
        if (mo.chefe) esconderChefe();
        plugin.getLogger().info("[" + mapa.id + "] " + mo.nome + " chegou no Nucleo (-" + mo.danoNucleo + ", restam " + Math.max(0, vidaNucleo) + ")");
        if (vidaNucleo <= 0 && estado != Estado.FIM) nucleoDestruido();
    }

    private void fimDaOnda() {
        int bonus = 5 + 2 * onda;
        int minas = 0;
        for (Torre t : torres.values()) {
            if (t.tipo != TipoTorre.MINA) continue;
            minas += TipoTorre.essenciaMina(t.nivel);
            mundo.spawnParticle(Particle.WITCH, t.local.clone().add(0, 1.5, 0), 25, 0.3, 0.5, 0.3, 0.1);
        }
        essencia += bonus + minas;
        boolean chefe = onda % 5 == 0;
        tituloTodos("§a§lONDA " + ondaTexto() + " CONCLUÍDA!", "§d+" + bonus + " Essência de bônus" + (minas > 0 ? " §7+ §d" + minas + " das minas" : ""));
        somTodos(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
        for (Player p : jogadores()) p.giveExp(10 * onda + (chefe ? 100 : 0));
        List<String> nomes = new ArrayList<>();
        for (Player p : jogadores()) nomes.add(p.getName());
        if (onda > recorde(dif)) {
            plugin.getConfig().set(chaveRecorde(dif), onda);
            plugin.getConfig().set("recordes-jogadores." + mapa.id + "." + dif.id, String.join(", ", nomes));
            plugin.saveConfig();
            if (!nomes.isEmpty() && (dif.infinito() || onda % 5 == 0)) {
                Bukkit.broadcast(DefesaTorre.txt("§6[Defesa da Torre] §e" + String.join(", ", nomes) + " §fbateram o recorde do "
                        + mapa.cor + mapa.nome + " §f(" + dif.cor + dif.nome + "§f): §6onda " + onda + "§f!"));
            }
        }
        if (chefe && !nomes.isEmpty()) {
            Bukkit.broadcast(DefesaTorre.txt("§5[Defesa da Torre] §d" + String.join(", ", nomes) + " §fderrotaram o Boss da onda §5"
                    + onda + " §fno " + mapa.cor + mapa.nome + "§f!"));
        }
        plugin.getLogger().info("[" + mapa.id + "] Onda " + onda + " concluida (+" + bonus + "+" + minas + " essencia, total " + essencia + ")");
        if (!dif.infinito() && onda >= dif.ondas) {
            vitoria(nomes);
            return;
        }
        estado = Estado.INTERVALO;
        contagem = dif.intervalo;
    }

    private void vitoria(List<String> nomes) {
        venceu = true;
        estado = Estado.FIM;
        contagem = 12;
        int v = vitorias(dif) + 1;
        plugin.getConfig().set("vitorias." + mapa.id + "." + dif.id, v);
        plugin.saveConfig();
        tituloTodos("§6§lVITÓRIA!", "§eVocês venceram o " + mapa.nome + " no " + dif.nome + "!");
        somTodos(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        somTodos(Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        for (Player p : jogadores()) p.giveExp(250 + 150 * dif.ordinal());
        Location n = mapa.nucleo(mundo).add(0, 1, 0);
        for (int i = 0; i < 6; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                mundo.spawnParticle(Particle.TOTEM_OF_UNDYING, n.clone().add(0, 2 + k * 0.5, 0), 60, 1.5, 1.5, 1.5, 0.5);
                mundo.spawnParticle(Particle.FIREWORK, n.clone().add(0, 3, 0), 40, 2, 2, 2, 0.15);
                mundo.playSound(n, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
            }, i * 15L);
        }
        if (!nomes.isEmpty()) {
            Bukkit.broadcast(DefesaTorre.txt("§6§l[Defesa da Torre] §e" + String.join(", ", nomes) + " §fVENCERAM o " + mapa.cor + mapa.nome
                    + " §fno " + dif.cor + "§l" + dif.nome + "§f! §7(" + dif.ondas + " ondas)"));
        }
        avisar("§a[Defesa] Vitória! A arena vai reiniciar em instantes. Que tal tentar uma dificuldade maior?");
        plugin.getLogger().info("[" + mapa.id + "] VITORIA no " + dif.id + " por " + String.join(", ", nomes));
    }

    private void nucleoDestruido() {
        estado = Estado.FIM;
        venceu = false;
        contagem = 6;
        fila.clear();
        for (Monstro mo : new ArrayList<>(monstros.values())) {
            Entity e = Bukkit.getEntity(mo.id);
            if (e != null) {
                mundo.spawnParticle(Particle.POOF, e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
                e.remove();
            }
        }
        monstros.clear();
        esconderChefe();
        Location n = mapa.nucleo(mundo);
        mundo.spawnParticle(Particle.EXPLOSION_EMITTER, n, 2);
        mundo.playSound(n, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        tituloTodos("§4§lNÚCLEO DESTRUÍDO", "§fVocês chegaram na onda §e" + ondaTexto() + " §7(recorde: " + recorde(dif) + ")");
        somTodos(Sound.ENTITY_WITHER_DEATH, 0.6f, 1f);
        avisar("§c[Defesa] O Núcleo caiu na onda " + onda + ". A arena vai reiniciar em instantes...");
        plugin.getLogger().info("[" + mapa.id + "] Nucleo destruido na onda " + onda + " (" + dif.id + ")");
    }

    void resetar(boolean avisarJogadores) {
        geracao++;
        rapido = false;
        if (simulando) plugin.getLogger().info("[sim " + mapa.id + "] fim: onda " + onda + ", nucleo " + vidaNucleo + "/" + dif.vidaNucleo + ", venceu=" + venceu);
        simulando = false;
        fila.clear();
        for (Monstro mo : monstros.values()) {
            Entity e = Bukkit.getEntity(mo.id);
            if (e != null) e.remove();
        }
        monstros.clear();
        for (Torre t : torres.values()) {
            Entity e = Bukkit.getEntity(t.stand);
            if (e != null) e.remove();
        }
        torres.clear();
        for (UUID id : itensEssencia.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        itensEssencia.clear();
        for (UUID id : colocando.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.tirarItemColocar(p);
        }
        colocando.clear();
        colocandoAte.clear();
        esconderChefe();
        onda = 0;
        estado = Estado.ESPERANDO;
        venceu = false;
        forcado = false;
        vidaNucleo = dif.vidaNucleo;
        essencia = dif.essenciaInicial;
        if (avisarJogadores) {
            avisar("§e[Defesa] Arena reiniciada! §fUse §a/defesa iniciar §fpara jogar de novo (escolha a dificuldade).");
            atualizarPlacar();
            atualizarBarras();
        }
    }

    // ================================================================== torres

    private List<Mob> alvosNoAlcance(Location origem, double alcance) {
        List<Mob> lista = new ArrayList<>();
        double a2 = alcance * alcance;
        for (Monstro mo : monstros.values()) {
            Entity e = Bukkit.getEntity(mo.id);
            if (!(e instanceof Mob m) || !m.isValid() || m.isDead()) continue;
            if (m.getLocation().distanceSquared(origem) <= a2) lista.add(m);
        }
        lista.sort(Comparator.comparingDouble((Mob m) -> -progresso(m, monstros.get(m.getUniqueId()))));
        return lista;
    }

    private static void ferir(Mob m, double dano) {
        if (!m.isValid() || m.isDead()) return;
        m.setNoDamageTicks(0);
        m.damage(dano);
    }

    /** Bonus de dano dos Farois por perto (1.0 = sem bonus). */
    private double bonusFarois(Torre t, List<Torre> farois) {
        double melhor = 0;
        for (Torre f : farois) {
            double r = f.tipo.alcanceNivel(f.nivel);
            if (f.local.distanceSquared(t.local) <= r * r) melhor = Math.max(melhor, TipoTorre.bonusFarol(f.nivel));
        }
        return 1.0 + melhor;
    }

    private void torresAtacam() {
        List<Torre> farois = new ArrayList<>();
        for (Torre t : torres.values()) {
            if (t.tipo == TipoTorre.FAROL) farois.add(t);
        }
        for (Torre t : new ArrayList<>(torres.values())) {
            Entity se = Bukkit.getEntity(t.stand);
            if (!(se instanceof Mannequin st) || !st.isValid()) {
                torres.remove(t.stand);
                continue;
            }
            if (t.tipo.suporte()) continue;
            t.recarga -= 5;
            if (t.recarga > 0) continue;
            Location origem = st.getLocation().add(0, 1.45 * escalaTorre(t.nivel), 0);
            double dano = t.tipo.danoNivel(t.nivel) * (farois.isEmpty() ? 1.0 : bonusFarois(t, farois));
            List<Mob> alvos = alvosNoAlcance(st.getLocation(), t.tipo.alcanceNivel(t.nivel));
            if (t.tipo == TipoTorre.MORTEIRO) alvos.removeIf(m -> m.getLocation().distanceSquared(st.getLocation()) < 16);
            if (alvos.isEmpty()) continue;
            Mob alvo = alvos.get(0);
            if (t.tipo == TipoTorre.BALISTA) {
                alvo = alvos.stream().max(Comparator.comparingDouble(LivingEntity::getHealth)).orElse(alvo);
            } else if (t.tipo == TipoTorre.MORTEIRO) {
                alvo = maisAgrupado(alvos);
            }
            Location la = alvo.getLocation().add(0, alvo.getHeight() * 0.6, 0);
            Vector olhar = la.toVector().subtract(origem.toVector());
            if (olhar.lengthSquared() > 0.01) {
                float yaw = origem.clone().setDirection(olhar).getYaw();
                st.setRotation(yaw, 0f);
                st.setBodyYaw(yaw);
            }
            st.swingMainHand();
            switch (t.tipo) {
                case ARQUEIRA -> {
                    linha(origem, la, Particle.CRIT);
                    mundo.playSound(origem, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.2f);
                    ferir(alvo, dano);
                }
                case CANHAO -> {
                    linha(origem, la, Particle.FLAME);
                    mundo.spawnParticle(Particle.EXPLOSION, la, 1, 0, 0, 0, 0);
                    mundo.playSound(la, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);
                    for (Mob m : alvos) {
                        if (m.getLocation().distanceSquared(alvo.getLocation()) <= 2.5 * 2.5) ferir(m, dano);
                    }
                }
                case GELO -> {
                    double r = t.tipo.alcanceNivel(t.nivel) * 0.4;
                    mundo.spawnParticle(Particle.SNOWFLAKE, st.getLocation().add(0, 1, 0), 30, r, 0.3, r, 0.02);
                    for (Mob m : alvos) {
                        ferir(m, dano);
                        m.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, t.nivel >= 3 ? 2 : 1));
                        mundo.spawnParticle(Particle.SNOWFLAKE, m.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.01);
                    }
                    mundo.playSound(origem, Sound.BLOCK_GLASS_BREAK, 0.3f, 1.8f);
                }
                case VENENO -> {
                    double r = t.tipo.alcanceNivel(t.nivel) * 0.45;
                    mundo.spawnParticle(Particle.ITEM_SLIME, st.getLocation().add(0, 1, 0), 25, r, 0.3, r, 0.02);
                    for (Mob m : alvos) {
                        Monstro mo = monstros.get(m.getUniqueId());
                        if (mo == null) continue;
                        mo.venenoDps = mo.venenoAte > tick ? Math.max(mo.venenoDps, dano) : dano;
                        mo.venenoAte = tick + 100;
                        mundo.spawnParticle(Particle.DUST, m.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0,
                                new Particle.DustOptions(Color.fromRGB(60, 200, 40), 1.2f));
                    }
                    mundo.playSound(origem, Sound.BLOCK_BREWING_STAND_BREW, 0.4f, 1.4f);
                }
                case MORTEIRO -> morteiro(origem, alvo, dano);
                case TESLA -> {
                    int saltos = t.nivel >= 3 ? 3 : 2;
                    Set<UUID> atingidos = new HashSet<>();
                    Location de = origem;
                    Mob atual = alvo;
                    double d = dano;
                    for (int i = 0; i <= saltos && atual != null; i++) {
                        Location para = atual.getLocation().add(0, atual.getHeight() * 0.6, 0);
                        linha(de, para, Particle.ELECTRIC_SPARK);
                        ferir(atual, d);
                        atingidos.add(atual.getUniqueId());
                        de = para;
                        d = dano * 0.6;
                        Mob proximo = null;
                        double melhor = 25.0;
                        for (Mob m : alvos) {
                            if (atingidos.contains(m.getUniqueId())) continue;
                            double dist = m.getLocation().distanceSquared(atual.getLocation());
                            if (dist < melhor) {
                                melhor = dist;
                                proximo = m;
                            }
                        }
                        atual = proximo;
                    }
                    mundo.playSound(origem, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.35f, 1.8f);
                }
                case BALISTA -> {
                    linhaCor(origem, la, Color.fromRGB(120, 30, 30), 1.4f);
                    linha(origem, la, Particle.CRIT);
                    mundo.spawnParticle(Particle.CRIT, la, 20, 0.3, 0.4, 0.3, 0.3);
                    mundo.playSound(origem, Sound.ITEM_CROSSBOW_SHOOT, 0.9f, 0.6f);
                    ferir(alvo, dano);
                }
                case LASER -> {
                    linhaCor(origem, la, Color.RED, 1.1f);
                    ferir(alvo, dano);
                    if (tick % 20 == 0) mundo.playSound(origem, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 2f);
                }
                default -> { }
            }
            t.recarga = t.tipo.intervaloNivel(t.nivel);
        }
    }

    /** Monstro com mais vizinhos (onde a bomba do morteiro acerta mais gente). */
    private Mob maisAgrupado(List<Mob> alvos) {
        Mob melhor = alvos.get(0);
        int max = -1;
        for (Mob a : alvos) {
            int n = 0;
            for (Mob b : alvos) {
                if (a.getLocation().distanceSquared(b.getLocation()) <= 3.5 * 3.5) n++;
            }
            if (n > max) {
                max = n;
                melhor = a;
            }
        }
        return melhor;
    }

    private void morteiro(Location origem, Mob alvo, double dano) {
        Location destino = alvo.getLocation().add(alvo.getVelocity().clone().setY(0).multiply(10));
        Vector delta = destino.toVector().subtract(origem.toVector());
        double altura = 4 + delta.length() * 0.3;
        int ger = geracao;
        mundo.playSound(origem, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
        mundo.spawnParticle(Particle.LARGE_SMOKE, origem, 10, 0.2, 0.2, 0.2, 0.05);
        final int[] tk = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, tarefa -> {
            if (ger != geracao) {
                tarefa.cancel();
                return;
            }
            tk[0]++;
            double s = tk[0] / 20.0;
            Location pt = origem.clone().add(delta.clone().multiply(s)).add(0, 4 * altura * s * (1 - s), 0);
            mundo.spawnParticle(Particle.LARGE_SMOKE, pt, 2, 0.05, 0.05, 0.05, 0);
            mundo.spawnParticle(Particle.FLAME, pt, 2, 0.05, 0.05, 0.05, 0);
            if (tk[0] < 20) return;
            tarefa.cancel();
            mundo.spawnParticle(Particle.EXPLOSION, destino.clone().add(0, 0.5, 0), 4, 1.2, 0.3, 1.2, 0);
            mundo.spawnParticle(Particle.FLAME, destino.clone().add(0, 0.3, 0), 25, 1.5, 0.2, 1.5, 0.05);
            mundo.playSound(destino, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
            for (Monstro mo : new ArrayList<>(monstros.values())) {
                Entity e = Bukkit.getEntity(mo.id);
                if (e instanceof Mob m && m.getLocation().distanceSquared(destino) <= 3.5 * 3.5) ferir(m, dano);
            }
        }, 1L, 1L);
    }

    private void aplicarVeneno() {
        Particle.DustOptions verde = new Particle.DustOptions(Color.fromRGB(60, 200, 40), 1.0f);
        for (Monstro mo : new ArrayList<>(monstros.values())) {
            if (mo.venenoAte <= tick || mo.venenoDps <= 0) continue;
            Entity e = Bukkit.getEntity(mo.id);
            if (!(e instanceof Mob m)) continue;
            ferir(m, mo.venenoDps);
            mundo.spawnParticle(Particle.DUST, m.getLocation().add(0, m.getHeight() * 0.7, 0), 4, 0.25, 0.3, 0.25, 0, verde);
        }
    }

    /** Particulas das torres de suporte (farol ligando nas torres, mina brilhando). */
    private void efeitosSuporte() {
        for (Torre f : torres.values()) {
            if (f.tipo == TipoTorre.MINA) {
                mundo.spawnParticle(Particle.WITCH, f.local.clone().add(0, 1.8, 0), 4, 0.25, 0.25, 0.25, 0.02);
                continue;
            }
            if (f.tipo != TipoTorre.FAROL) continue;
            double r = f.tipo.alcanceNivel(f.nivel);
            Location topo = f.local.clone().add(0, 1.9, 0);
            mundo.spawnParticle(Particle.END_ROD, topo, 3, 0.1, 0.3, 0.1, 0.01);
            for (Torre t : torres.values()) {
                if (t == f || t.tipo.suporte() || t.local.distanceSquared(f.local) > r * r) continue;
                linha(topo, t.local.clone().add(0, 1.9, 0), Particle.END_ROD);
            }
        }
    }

    Torre torreDoStand(Entity e) {
        if (!(e instanceof Mannequin)) return null;
        return torres.get(e.getUniqueId());
    }

    static String nomeTorre(Torre t) {
        return t.tipo.cor + "§l" + t.tipo.nome + " §7[Nv " + t.nivel + "]";
    }

    boolean colocarTorre(Player p, Block clicado, TipoTorre tipo, boolean gratis) {
        Block chao = clicado;
        if (chao.getType() != mapa.chao && chao.getType() != mapa.trilha && chao.isPassable()) chao = chao.getRelative(BlockFace.DOWN);
        int rx = chao.getX() - mapa.ox;
        int rz = chao.getZ() - mapa.oz;
        if (!chao.getWorld().equals(mundo) || chao.getY() != Mapa.Y - 1 || !mapa.dentro(rx, rz)) {
            if (p != null) p.sendMessage("§cColoque a torre no chão da arena.");
            return false;
        }
        if (chao.getType() == mapa.trilha || mapa.noCaminho(rx, rz)) {
            if (p != null) p.sendMessage("§cNão pode colocar no caminho dos monstros! Coloque do lado.");
            return false;
        }
        if (chao.getType() != mapa.chao) {
            if (p != null) p.sendMessage("§cAqui não dá. Coloque a torre no chão de " + (mapa.chao == Material.GRASS_BLOCK ? "grama" : "nylium vermelho") + ".");
            return false;
        }
        if (torres.size() >= dif.limiteTorres) {
            if (p != null) {
                p.sendMessage("§cLimite de torres do " + dif.cor + dif.nome + " §catingido (§f" + dif.limiteTorres
                        + "§c). Evolua as torres que já tem, ou venda uma.");
            }
            return false;
        }
        Location l = chao.getLocation().add(0.5, 1, 0.5);
        if (l.distanceSquared(mapa.nucleo(mundo)) < 9 || l.distanceSquared(mapa.loja(mundo)) < 4 || l.distanceSquared(mapa.lobby(mundo)) < 4) {
            if (p != null) p.sendMessage("§cMuito perto do Núcleo, da loja ou do lobby.");
            return false;
        }
        for (Torre t : torres.values()) {
            if (t.local.distanceSquared(l) < 2.0) {
                if (p != null) p.sendMessage("§cJá tem uma torre aí do lado. Deixe 1 bloco de espaço.");
                return false;
            }
        }
        if (!gratis && essencia < tipo.custo) {
            if (p != null) p.sendMessage("§cFalta Essência: precisa de §d" + tipo.custo + "§c, vocês têm §d" + essencia + "§c.");
            return false;
        }
        l.setYaw(p != null ? p.getLocation().getYaw() + 180f : 0f);
        Mannequin st = mundo.spawn(l, Mannequin.class, a -> montarGuardiao(a, tipo));
        if (!st.isValid()) {
            if (p != null) p.sendMessage("§cNão consegui colocar a torre aqui.");
            return false;
        }
        int pago = gratis ? 0 : tipo.custo;
        essencia -= pago;
        torres.put(st.getUniqueId(), new Torre(st.getUniqueId(), tipo, l, Math.max(pago, tipo.custo / 2)));
        mundo.playSound(l, Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.3f);
        mundo.spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
        if (p != null) {
            avisar("§a" + p.getName() + " §7colocou " + tipo.cor + tipo.nome + " §7(-" + pago + " Essência, restam §d" + essencia
                    + "§7) §8[" + torres.size() + "/" + dif.limiteTorres + " torres]");
        }
        plugin.getLogger().info("[" + mapa.id + "] " + tipo.nome + " colocada em " + rx + "," + rz + (p != null ? " por " + p.getName() : ""));
        atualizarPlacar();
        return true;
    }

    /** Tamanho do guardiao da torre: cresce a cada nivel (Nv 1 menorzinho, Nv 3 tamanho de jogador). */
    static double escalaTorre(int nivel) {
        return nivel >= 3 ? 1.0 : nivel == 2 ? 0.88 : 0.76;
    }

    /** A torre e um "guardiao": um manequim com skin de jogador, item tematico na mao e o nome em cima. */
    private void montarGuardiao(Mannequin a, TipoTorre tipo) {
        a.getPersistentDataContainer().set(plugin.kTorre, PersistentDataType.STRING, tipo.id);
        a.setProfile(Skins.perfil(tipo.id));
        a.setDescription(null);
        a.setImmovable(true);
        a.setInvulnerable(true);
        a.setSilent(true);
        a.setCollidable(false);
        a.setGravity(false);
        a.setPersistent(true);
        a.setRemoveWhenFarAway(false);
        a.setCanPickupItems(false);
        AttributeInstance sc = a.getAttribute(Attribute.SCALE);
        if (sc != null) sc.setBaseValue(escalaTorre(1));
        var eq = a.getEquipment();
        eq.setItemInMainHand(itemMao(tipo));
        ItemStack outra = itemOutraMao(tipo);
        if (outra != null) eq.setItemInOffHand(outra);
        for (EquipmentSlot s : EquipmentSlot.values()) {
            try {
                eq.setDropChance(s, 0f);
            } catch (IllegalArgumentException ignored) {
                // slot sem suporte
            }
        }
        a.customName(DefesaTorre.txt(tipo.cor + "§l" + tipo.nome + " §7[Nv 1]"));
        a.setCustomNameVisible(true);
    }

    private static ItemStack itemMao(TipoTorre tipo) {
        return switch (tipo) {
            case ARQUEIRA -> new ItemStack(Material.BOW);
            case CANHAO -> new ItemStack(Material.FIRE_CHARGE);
            case GELO -> new ItemStack(Material.BLUE_ICE);
            case VENENO -> {
                ItemStack it = new ItemStack(Material.SPLASH_POTION);
                if (it.getItemMeta() instanceof PotionMeta pm) {
                    pm.setColor(Color.fromRGB(60, 200, 40));
                    it.setItemMeta(pm);
                }
                yield it;
            }
            case MINA -> new ItemStack(Material.GOLDEN_PICKAXE);
            case MORTEIRO -> new ItemStack(Material.TNT);
            case TESLA -> new ItemStack(Material.LIGHTNING_ROD);
            case FAROL -> new ItemStack(Material.END_ROD);
            case BALISTA -> {
                ItemStack it = new ItemStack(Material.CROSSBOW);
                if (it.getItemMeta() instanceof CrossbowMeta cm) {
                    cm.addChargedProjectile(new ItemStack(Material.ARROW));
                    it.setItemMeta(cm);
                }
                yield it;
            }
            case LASER -> new ItemStack(Material.BLAZE_ROD);
        };
    }

    private static ItemStack itemOutraMao(TipoTorre tipo) {
        return switch (tipo) {
            case MINA -> new ItemStack(Material.AMETHYST_SHARD);
            case FAROL, BALISTA -> new ItemStack(Material.SHIELD);
            case GELO -> new ItemStack(Material.SNOWBALL);
            default -> null;
        };
    }

    /** Nome e tamanho do guardiao conforme o nivel da torre. */
    void atualizarGuardiao(Torre t) {
        Entity e = Bukkit.getEntity(t.stand);
        if (e == null) return;
        e.customName(DefesaTorre.txt(nomeTorre(t)));
        if (e instanceof Mannequin m) {
            AttributeInstance sc = m.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(escalaTorre(t.nivel));
        }
    }

    boolean evoluir(Player p, Torre t) {
        if (t.nivel >= 3) {
            if (p != null) p.sendMessage("§eEssa torre já está no nível máximo.");
            return false;
        }
        int custo = t.tipo.custoEvolucao(t.nivel);
        if (essencia < custo) {
            if (p != null) p.sendMessage("§cFalta Essência: precisa de §d" + custo + "§c, vocês têm §d" + essencia + "§c.");
            return false;
        }
        essencia -= custo;
        t.investido += custo;
        t.nivel++;
        Entity e = Bukkit.getEntity(t.stand);
        atualizarGuardiao(t);
        if (e != null) {
            mundo.spawnParticle(Particle.TOTEM_OF_UNDYING, e.getLocation().add(0, 1.2, 0), 30, 0.4, 0.6, 0.4, 0.2);
            mundo.playSound(e.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        }
        avisar("§a" + (p != null ? p.getName() : "Alguém") + " §7evoluiu " + t.tipo.cor + t.tipo.nome + " §7para o §enível " + t.nivel + " §7(-" + custo + ")");
        return true;
    }

    void vender(Player p, Torre t) {
        int volta = t.investido / 2;
        essencia += volta;
        torres.remove(t.stand);
        Entity e = Bukkit.getEntity(t.stand);
        if (e != null) {
            mundo.spawnParticle(Particle.POOF, e.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            e.remove();
        }
        mundo.playSound(t.local, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
        avisar("§e" + p.getName() + " §7vendeu " + t.tipo.cor + t.tipo.nome + " §7(+" + volta + " Essência) §8[" + torres.size() + "/" + dif.limiteTorres + "]");
    }

    // ================================================================== essencia

    void soltarEssencia(Location l, int valor) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, Math.min(64, valor)));
        ItemMeta m = it.getItemMeta();
        m.displayName(DefesaTorre.txt("§dEssência"));
        m.getPersistentDataContainer().set(plugin.kEssencia, PersistentDataType.STRING, mapa.id);
        it.setItemMeta(m);
        Item item = mundo.dropItemNaturally(l, it);
        item.setPickupDelay(5);
        item.customName(DefesaTorre.txt("§d+" + valor + " Essência"));
        item.setCustomNameVisible(true);
        itensEssencia.put(item.getUniqueId(), tick);
    }

    void pegouEssencia(UUID item, int v) {
        essencia += v;
        itensEssencia.remove(item);
    }

    private void recolherEssencias() {
        itensEssencia.entrySet().removeIf(en -> {
            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof Item item) || !item.isValid()) return true;
            if (tick - en.getValue() < 240) return false;
            essencia += item.getItemStack().getAmount();
            mundo.spawnParticle(Particle.WITCH, item.getLocation(), 8, 0.2, 0.2, 0.2, 0.05);
            item.remove();
            return true;
        });
    }

    /** Um monstro desta partida morreu. */
    void monstroMorreu(LivingEntity ent, Monstro mo) {
        soltarEssencia(ent.getLocation().add(0, 0.5, 0), mo.essencia);
        if (mo.chefe) {
            esconderChefe();
            tituloTodos("§5§lBOSS DERROTADO!", "§d+" + mo.essencia + " Essência");
            somTodos(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
            mundo.spawnParticle(Particle.TOTEM_OF_UNDYING, ent.getLocation().add(0, 1, 0), 80, 0.8, 1.2, 0.8, 0.4);
            plugin.getLogger().info("[" + mapa.id + "] Boss " + mo.nome + " derrotado na onda " + onda);
        }
    }

    String status() {
        return "[" + mapa.id + "] estado=" + estado + " dif=" + dif.id + " onda=" + onda + " nucleo=" + vidaNucleo + " essencia=" + essencia
                + " monstros=" + monstros.size() + " fila=" + fila.size() + " torres=" + torres.size() + "/" + dif.limiteTorres
                + " jogadores=" + jogadores().size() + " recorde=" + recorde(dif);
    }

    // ================================================================== simulacao (admin, para equilibrar)

    boolean simulando = false;
    private List<String[]> plano = new ArrayList<>();
    private int passo = 0;

    void simular(List<String> linhas) {
        plano = new ArrayList<>();
        for (String l : linhas) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("#")) plano.add(t.split("\\s+"));
        }
        passo = 0;
        simulando = true;
        forcado = true;
    }

    /** Passos: "<tipo> x z" coloca; "up <tipo>" evolui todas desse tipo ate o 3; "up todas". */
    private void executarPlano() {
        if (estado == Estado.ESPERANDO || estado == Estado.FIM) return;
        while (passo < plano.size()) {
            String[] a = plano.get(passo);
            if (a[0].equals("up")) {
                Torre alvo = null;
                for (Torre t : torres.values()) {
                    if (t.nivel >= 3 || (!a[1].equals("todas") && !t.tipo.id.equals(a[1]))) continue;
                    if (alvo == null || t.nivel < alvo.nivel) alvo = t;
                }
                if (alvo == null) {
                    passo++;
                    continue;
                }
                if (!evoluir(null, alvo)) return;
                plugin.getLogger().info("[sim " + mapa.id + "] onda " + onda + ": evoluiu " + alvo.tipo.id + " p/ " + alvo.nivel + " (ess " + essencia + ")");
                continue;
            }
            TipoTorre tipo = TipoTorre.porId(a[0]);
            if (tipo == null || a.length < 3) {
                passo++;
                continue;
            }
            if (essencia < tipo.custo) return;
            Block b = mundo.getBlockAt(mapa.ox + Integer.parseInt(a[1]), Mapa.Y - 1, mapa.oz + Integer.parseInt(a[2]));
            if (colocarTorre(null, b, tipo, false)) {
                plugin.getLogger().info("[sim " + mapa.id + "] onda " + onda + ": colocou " + tipo.id + " em " + a[1] + "," + a[2] + " (ess " + essencia + ")");
            } else {
                plugin.getLogger().info("[sim " + mapa.id + "] passo invalido: " + String.join(" ", a));
            }
            passo++;
        }
    }

    /** Admin: muda a onda inicial (antes de comecar). */
    boolean pularParaOnda(int n) {
        if (estado != Estado.ESPERANDO) return false;
        onda = Math.max(0, n - 1);
        return true;
    }
}
