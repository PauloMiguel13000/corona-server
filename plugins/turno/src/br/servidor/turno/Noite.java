package br.servidor.turno;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Uma noite de turno para um vigia numa instancia do mapa. Regras (as mesmas do classico do genero): o vigia fica na
 * cadeira, com duas portas (esquerda/oeste e direita/leste), duas luzes de canto e o tablet das cameras. Tudo gasta
 * energia; acabou a energia, acabou a defesa. Os animatronicos andam pelo restaurante no ritmo do nivel de IA deles e
 * tentam entrar pelas portas; porta fechada na hora certa manda o bicho de volta. Sobreviva da meia-noite as 6 da manha.
 */
final class Noite {

    static final String[] HORAS = {"12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM"};

    final Turno pl;
    final Mapa mapa;
    final int instancia;
    final UUID jogador;     // quem abriu o turno (o anfitriao no coop)
    /** Um vigia na sala: no coop cada um tem o seu tablet, a sua camera e o seu lugar na mesa. */
    static final class Vigia {
        final UUID id;
        int camera = -1;        // -1 = fora do tablet
        int cameraDesde;        // tick em que abriu as cameras
        Location ancora;        // onde tem de ficar (lugar na mesa ou camera); null = solto (susto, fim)
        Location lugar;         // o lugar dele na mesa
        BossBar barra;

        Vigia(UUID id) {
            this.id = id;
        }

        Player p() {
            return Bukkit.getPlayer(id);
        }
    }

    final List<Vigia> vigias = new ArrayList<>();
    final int noite;        // 1..6 (6 = noite personalizada)
    final int[] ia;         // tuca, jaca, capi, boto
    private final Random rnd = new Random();
    final List<Animatronico> bichos = new ArrayList<>();
    private final Animatronico tuca, jaca, capi, boto;

    double energia = 100;
    int ticks;              // desde o inicio
    int duracaoTicks;
    boolean portaOeste, portaLeste, luzOeste, luzLeste;
    int luzOesteAte, luzLesteAte;
    int botoFase;           // 0..3
    int botoCorridas;
    boolean botoCorrendo;
    boolean semEnergia;
    int sustoEm = -1;       // tick do susto (apos acabar a energia)
    Animatronico sustoDe;
    boolean acabou;
    BukkitTask tarefa;
    final List<TextDisplay> textos = new ArrayList<>();
    int ultimoAvisoBloqueio;

    Noite(Turno pl, Mapa mapa, int instancia, List<Player> gente, int noite, int[] ia) {
        this.pl = pl;
        this.mapa = mapa;
        this.instancia = instancia;
        this.jogador = gente.get(0).getUniqueId();
        for (Player x : gente) vigias.add(new Vigia(x.getUniqueId()));
        // lugares na mesa: sozinho fica na cadeira; em grupo, lado a lado (todos alcancam os 4 botoes)
        for (int i = 0; i < vigias.size(); i++) {
            double[] c = Mapa.CADEIRA.clone();
            c[0] += (i - (vigias.size() - 1) / 2.0) * 1.6;
            vigias.get(i).lugar = mapa.loc(c);
        }
        this.noite = noite;
        this.ia = ia;
        tuca = new Animatronico("tuca", "Tuca", "§e", List.of("palco_tuca", "salao_a", "bastidores", "deposito", "corr_oeste", "canto_oeste"), true, 97);
        jaca = new Animatronico("jaca", "Jaca", "§6", List.of("palco_jaca", "salao_b", "banheiros", "cozinha", "corr_leste", "canto_leste"), false, 99);
        capi = new Animatronico("capi", "Capi", "§c", List.of("palco_capi", "salao_a", "banheiros", "cozinha", "corr_leste", "canto_leste"), false, 61);
        boto = new Animatronico("boto", "Boto", "§d", List.of("cortina_0", "cortina_1", "cortina_2", "cortina_3", "corr_oeste", "canto_oeste"), true, 101);
        tuca.ia = ia[0];
        jaca.ia = ia[1];
        capi.ia = ia[2];
        boto.ia = ia[3];
        bichos.add(tuca);
        bichos.add(jaca);
        bichos.add(capi);
        bichos.add(boto);
    }

    boolean coop() {
        return vigias.size() > 1;
    }

    Vigia vigia(Player p) {
        if (p != null) for (Vigia v : vigias) if (v.id.equals(p.getUniqueId())) return v;
        return null;
    }

    /** Camera que o jogador esta olhando (-1 = fora do tablet ou nao e desta noite). */
    int cam(Player p) {
        Vigia v = vigia(p);
        return v == null ? -1 : v.camera;
    }

    int desde(Player p) {
        Vigia v = vigia(p);
        return v == null ? 0 : v.cameraDesde;
    }

    /** Os vigias que estao online. */
    List<Player> gente() {
        List<Player> l = new ArrayList<>();
        for (Vigia v : vigias) {
            Player x = v.p();
            if (x != null) l.add(x);
        }
        return l;
    }

    private boolean alguemNasCameras() {
        for (Vigia v : vigias) if (v.camera >= 0) return true;
        return false;
    }

    private boolean todosNasCameras() {
        for (Vigia v : vigias) if (v.camera < 0) return false;
        return !vigias.isEmpty();
    }

    /** Alguem esta com a camera dessa sala aberta. */
    private boolean alguemVe(String sala) {
        for (Vigia v : vigias) if (v.camera >= 0 && Mapa.CAMERAS[v.camera].equals(sala)) return true;
        return false;
    }

    private void som(Sound s, float vol, float tom) {
        for (Player x : gente()) x.playSound(x.getLocation(), s, vol, tom);
    }

    private void barraDeAcao(String t) {
        for (Player x : gente()) x.sendActionBar(Turno.txt(t));
    }

    private void aviso(String t) {
        for (Player x : gente()) x.sendMessage(Turno.txt(t));
    }

    // ------------------------------------------------------------------ inicio

    void comecar() {
        if (gente().isEmpty()) return;
        duracaoTicks = Math.max(60 * 20, pl.getConfig().getInt("minutos-por-noite", 6) * 60 * 20);
        mapa.porta(true, false);
        mapa.porta(false, false);
        mapa.luz(true, false);
        mapa.luz(false, false);
        mapa.cortina(true);
        mapa.luzEscritorio(true);
        for (Animatronico a : bichos) a.nascer(pl, mapa);
        for (Vigia v : vigias) {
            Player p = v.p();
            if (p == null) continue;
            Guarda.limpar(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(v.lugar);
            v.ancora = v.lugar.clone();
            p.setWalkSpeed(0f);
            // o vigia fica sentado: sem pulo (pular tirava a pessoa da cadeira)
            var pulo = p.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH);
            if (pulo != null) pulo.setBaseValue(0);
            p.setFoodLevel(20);
            p.getInventory().setItem(0, tablet());
            p.getInventory().setHeldItemSlot(0);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
            v.barra = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
            v.barra.addPlayer(p);
            p.showTitle(net.kyori.adventure.title.Title.title(Turno.txt("§f§lNOITE " + (noite == 6 ? "PERSONALIZADA" : noite) + (coop() ? " §7(coop)" : "")),
                    Turno.txt("§712:00 AM — Zoo Mecânico do Seu Zé")));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
        }
        // kits/menus de outros plugins chegam com atraso: garante a mesa limpa (so o tablet)
        for (long d : new long[]{10L, 45L, 100L}) Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (acabou) return;
            for (Vigia v : vigias) {
                Player j = v.p();
                if (j == null) continue;
                ItemStack[] c = j.getInventory().getContents();
                for (int i = 1; i < c.length; i++) if (c[i] != null && !(i == 8 && v.camera >= 0)) j.getInventory().setItem(i, null);
                j.getInventory().setItem(0, tablet());
            }
        }, d);
        letreiros();
        telefonema();
        if (coop()) Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!acabou) aviso("§b§lCOOP §7— a energia é uma só para todos e cada tablet aberto gasta. Combinem: um nas câmeras, outro nas portas!");
        }, 30L);
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 20L, 1L);
    }

    private ItemStack tablet() {
        ItemStack it = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta m = it.getItemMeta();
        m.displayName(Turno.txt("§b§lTablet das Câmeras"));
        m.lore(List.of(Turno.txt("§7Clique direito: abrir as câmeras"), Turno.txt("§7Nas câmeras: direito = próxima, esquerdo = anterior"),
                Turno.txt("§7Sair: agache (Shift), pule (Espaço) ou Q"), Turno.txt("§7No celular: toque em SAIR na barra"), Turno.txt("§8Olhar as câmeras gasta energia")));
        m.getPersistentDataContainer().set(pl.kTablet, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private void letreiros() {
        texto(mapa.loc(Mapa.BOTAO_PORTA_OESTE_X + 0.5, Mapa.Y + 2.6, Mapa.BOTAO_Z + 0.5, 0, 0), "§c◄ PORTA");
        texto(mapa.loc(Mapa.BOTAO_LUZ_OESTE_X + 0.5, Mapa.Y + 2.6, Mapa.BOTAO_Z + 0.5, 0, 0), "§f◄ LUZ");
        texto(mapa.loc(Mapa.BOTAO_LUZ_LESTE_X + 0.5, Mapa.Y + 2.6, Mapa.BOTAO_Z + 0.5, 0, 0), "§fLUZ ►");
        texto(mapa.loc(Mapa.BOTAO_PORTA_LESTE_X + 0.5, Mapa.Y + 2.6, Mapa.BOTAO_Z + 0.5, 0, 0), "§cPORTA ►");
    }

    private void texto(Location l, String t) {
        TextDisplay d = l.getWorld().spawn(l, TextDisplay.class, x -> {
            x.text(Turno.txt(t));
            x.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            x.setPersistent(false);
            x.setShadowed(true);
            org.bukkit.util.Transformation tr = x.getTransformation();
            tr.getScale().set(0.45f);
            x.setTransformation(tr);
        });
        textos.add(d);
    }

    /** O recado do vigia anterior (texto, com pausa entre as falas). */
    private void telefonema() {
        List<String> falas = switch (Math.min(noite, 5)) {
            case 1 -> List.of("§7§o*trim trim*", "§e[Seu Zé] §fAlô? Alô... ah, é o novato! Bem-vindo ao Zoo Mecânico. Eu sou o Zé, o vigia antigo.",
                    "§e[Seu Zé] §fOs bichos do palco ficam no modo passeio livre de noite... as juntas travam se ficam parados. É normal eles andarem por aí.",
                    "§e[Seu Zé] §fSe algum chegar perto do escritório, §cfecha a porta§f. Mas cuidado: a energia é pouca. Porta, luz e câmera gastam.",
                    "§e[Seu Zé] §fAcabou a energia... bom, aí é torcer para dar 6 da manhã. Boa sorte, menino.");
            case 2 -> List.of("§7§o*trim trim*", "§e[Seu Zé] §fOi, de novo! Sobreviveu, hein. Hoje eles se mexem mais.",
                    "§e[Seu Zé] §fOlha a §dCortina§f de vez em quando. O Boto não gosta de ficar sem plateia... mas se você olhar demais, pior.",
                    "§e[Seu Zé] §fUsa a luz antes de fechar a porta: fechar à toa é jogar energia fora.");
            case 3 -> List.of("§7§o*trim trim*", "§e[Seu Zé] §fTerceira noite. Eles aprenderam a sua rotina. Fica esperto com o §cCapi§f, ele só anda quando ninguém olha.",
                    "§e[Seu Zé] §fSe ele entrar no escritório... não desce o tablet. Fica nele até dar 6.");
            case 4 -> List.of("§7§o*trim trim*", "§e[Seu Zé] §fEu... hoje eu tô com um barulho estranho aqui na minha sala. Deve ser a cozinha.",
                    "§e[Seu Zé] §fQualquer coisa, a saída de emergência no corredor leste está... §7*estática*", "§7§o*batida de metal* ... *silêncio*");
            default -> List.of("§7§o*trim trim*", "§4§o*uma voz mecânica, sem palavras, repete o seu nome*");
        };
        for (int i = 0; i < falas.size(); i++) {
            final String f = falas.get(i);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!acabou) aviso(f);
            }, 40L + i * 130L);
        }
    }

    // ------------------------------------------------------------------ o relogio

    private int hora() {
        return Math.min(6, (int) (ticks * 6L / duracaoTicks));
    }

    private int uso() {
        int u = 1;
        if (portaOeste) u++;
        if (portaLeste) u++;
        if (luzOeste) u++;
        if (luzLeste) u++;
        for (Vigia v : vigias) if (v.camera >= 0) u++;
        return u;
    }

    private void tick() {
        List<Player> gente = gente();
        if (gente.isEmpty() || acabou) {
            if (!acabou) terminar(false, null);
            return;
        }
        ticks++;
        if (ticks >= duracaoTicks) {
            terminar(true, null);
            return;
        }
        if (ticks % 10 == 0) for (Animatronico a : bichos) a.rever();
        boolean dentro = false;
        for (Animatronico a : bichos) if (a.noEscritorio) dentro = true;
        for (Vigia v : vigias) {
            Player p = v.p();
            if (p == null) continue;
            amarrar(v, p);
            // nas cameras: lembra o tempo todo como sair (era so no texto do tablet e muita gente ficava presa)
            if (v.camera >= 0 && (ticks - v.cameraDesde) % 40 == 20) {
                if (!dentro) dicaDeSaida(p);
                if (p.getInventory().getItem(8) == null) p.getInventory().setItem(8, botaoSair());
            }
            if (ticks % 10 == 0) p.setFoodLevel(20);
        }
        // luzes apagam sozinhas
        if (luzOeste && ticks > luzOesteAte) {
            luzOeste = false;
            mapa.luz(true, false);
        }
        if (luzLeste && ticks > luzLesteAte) {
            luzLeste = false;
            mapa.luz(false, false);
        }
        if (ticks % 20 == 0) {
            if (!semEnergia) {
                double fator = noite >= 5 ? 1.35 : noite == 4 ? 1.25 : noite == 3 ? 1.15 : 1.0;
                energia -= 0.1 * uso() * fator * pl.getConfig().getDouble("gasto-energia", 1.0);
                if (energia <= 0) acabouEnergia();
            }
            barra();
            if (ticks % 200 == 0 && rnd.nextInt(3) == 0) ambiente();
        }
        if (!semEnergia) {
            for (Animatronico a : bichos) andar(a);
        } else if (sustoEm > 0 && ticks >= sustoEm) {
            susto(capi);
        }
        // bicho dentro do escritorio: o susto vem quando um tablet abaixa (ou logo, se ja tem alguem com ele abaixado)
        for (Animatronico a : bichos) {
            if (!a.noEscritorio) continue;
            boolean todos = todosNasCameras();
            if (!todos && ticks - a.ticks > 30 + rnd.nextInt(30)) {
                susto(a);
                return;
            }
            if (todos && ticks - a.ticks > 100) {
                // segurar o tablet nao salva: ele arranca o tablet da sua mao
                for (Vigia v : vigias) fecharCameras(v.p());
                susto(a);
                return;
            }
        }
    }

    private void barra() {
        int h = hora();
        String base = "§e⚡ " + Math.max(0, (int) Math.ceil(energia)) + "%  §7|  §f" + (h >= 6 ? "6 AM" : HORAS[h]) + "  §7|  uso " + "▮".repeat(uso());
        for (Vigia v : vigias) {
            if (v.barra == null) continue;
            v.barra.setTitle(base + (v.camera >= 0 ? "  §7|  §bCAM " + Mapa.CAMERAS_NOME[v.camera] + "  §7|  §eSHIFT, ESPAÇO ou Q = sair" : ""));
            v.barra.setProgress(Math.max(0, Math.min(1, energia / 100.0)));
            v.barra.setColor(energia > 50 ? BarColor.GREEN : energia > 20 ? BarColor.YELLOW : BarColor.RED);
        }
    }

    private void ambiente() {
        switch (rnd.nextInt(5)) {
            case 0 -> som(Sound.BLOCK_IRON_DOOR_CLOSE, 0.3f, 0.5f);
            case 1 -> som(Sound.ENTITY_VILLAGER_AMBIENT, 0.25f, 0.4f);
            case 2 -> som(Sound.BLOCK_ANVIL_LAND, 0.15f, 0.6f);
            case 3 -> som(Sound.AMBIENT_CAVE, 0.6f, 1f);
            default -> som(Sound.BLOCK_CHEST_OPEN, 0.3f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ animatronicos

    private void andar(Animatronico a) {
        if (a.noEscritorio || a.ia <= 0) return;
        if (ticks % a.intervaloTicks != 0) return;
        if (a == capi && alguemNasCameras()) return;          // o Capi so anda quando ninguem olha as cameras
        if (a == boto && alguemVe("cortina")) return;         // olhar a Cortina segura o Boto
        if (rnd.nextInt(20) + 1 > a.ia) return;
        if (a == boto) {
            andarBoto();
            return;
        }
        if (a.noCanto()) {
            boolean fechada = a.portaOeste ? portaOeste : portaLeste;
            if (fechada) {
                a.posicao = rnd.nextInt(2);
                a.mover(mapa, a.lugarAtual());
                som(Sound.BLOCK_IRON_DOOR_OPEN, 0.5f, 0.3f);
            } else entrar(a);
            return;
        }
        a.posicao++;
        a.mover(mapa, a.lugarAtual());
        if (a.noCanto()) som(Sound.ENTITY_ZOMBIE_STEP, 0.6f, 0.4f);
        else som(Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.25f, 0.5f);
        // ele acabou de aparecer na camera que um vigia olha: um "pulo" na imagem
        String sala = salaDe(a);
        for (Vigia v : vigias) {
            Player x = v.p();
            if (x != null && v.camera >= 0 && Mapa.CAMERAS[v.camera].equals(sala)) x.playSound(x.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
        }
    }

    private String salaDe(Animatronico a) {
        String l = a.lugarAtual();
        if (l.startsWith("palco")) return "palco";
        if (l.startsWith("salao")) return "salao";
        if (l.startsWith("cortina")) return "cortina";
        return l;
    }

    private void andarBoto() {
        if (botoCorrendo) return;
        if (botoFase < 3) {
            botoFase++;
            boto.posicao = botoFase;
            boto.mover(mapa, boto.lugarAtual());
            if (botoFase == 2) mapa.cortina(false);
            if (botoFase >= 2) som(Sound.BLOCK_WOOL_BREAK, 0.6f, 0.4f);
            return;
        }
        // corre pelo corredor oeste
        botoCorrendo = true;
        boto.posicao = 4;
        boto.mover(mapa, "corr_oeste");
        som(Sound.ENTITY_ZOMBIE_STEP, 1f, 1.4f);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (acabou || boto.boneco == null) return;
            som(Sound.ENTITY_ZOMBIE_STEP, 1f, 1.6f);
            boto.posicao = 5;
            boto.mover(mapa, "canto_oeste");
        }, 15L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (acabou || boto.boneco == null) return;
            if (portaOeste) {
                // bate na porta e volta: cada corrida tira mais energia
                int perda = 1 + 5 * botoCorridas;
                botoCorridas++;
                energia = Math.max(0, energia - perda);
                som(Sound.BLOCK_ANVIL_LAND, 1f, 0.7f);
                som(Sound.ENTITY_IRON_GOLEM_HURT, 1f, 0.6f);
                barraDeAcao("§d§lBAM! §fO Boto bateu na porta: §c-" + perda + "% de energia");
                botoFase = 0;
                boto.posicao = 0;
                boto.mover(mapa, "cortina_0");
                mapa.cortina(true);
                botoCorrendo = false;
                if (energia <= 0) acabouEnergia();
            } else {
                entrar(boto);
                botoCorrendo = false;
            }
        }, 30L);
    }

    private void entrar(Animatronico a) {
        a.noEscritorio = true;
        a.ticks = ticks;
        a.mover(mapa, a.portaOeste ? "canto_oeste" : "canto_leste");
        if (a.boneco != null) {
            a.boneco.teleport(mapa.loc(a.portaOeste ? -3.5 : 3.5, Mapa.Y + 1, 0.5, a.portaOeste ? -90 : 90, 0));
            a.sincronizar();
        }
        som(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.6f);
        for (Vigia v : vigias) {
            Player x = v.p();
            if (x != null && v.camera >= 0) x.sendActionBar(Turno.txt("§4Algo entrou no escritório..."));
        }
    }

    private void acabouEnergia() {
        if (semEnergia) return;
        semEnergia = true;
        energia = 0;
        portaOeste = portaLeste = luzOeste = luzLeste = false;
        mapa.porta(true, false);
        mapa.porta(false, false);
        mapa.luz(true, false);
        mapa.luz(false, false);
        mapa.luzEscritorio(false);
        for (Vigia v : vigias) {
            Player p = v.p();
            if (p == null) continue;
            if (v.camera >= 0) fecharCameras(p);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false, false));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.4f);
            p.sendMessage(Turno.txt("§4§lA ENERGIA ACABOU. §7Está tudo escuro..."));
        }
        // o Capi aparece na porta esquerda com os olhos brilhando e toca a caixinha de musica
        capi.noEscritorio = false;
        if (capi.boneco != null) {
            capi.boneco.teleport(mapa.loc(-4.5, Mapa.Y + 1, -0.5, -90, 0));
            capi.sincronizar();
            capi.brilhar(true);
        }
        int[] notas = {12, 16, 19, 24, 19, 16, 12, 7, 12, 16, 19, 24};
        for (int i = 0; i < notas.length * 2; i++) {
            final float tom = (float) Math.pow(2, (notas[i % notas.length] - 12) / 12.0);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!acabou) som(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, tom);
            }, 20L + i * 9L);
        }
        sustoEm = ticks + 20 * (12 + rnd.nextInt(18));
    }

    // ------------------------------------------------------------------ acoes do vigia

    void botao(Player p, int x) {
        if (acabou) return;
        if (cam(p) >= 0) return;
        if (semEnergia) {
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 0.5f);
            return;
        }
        if (x == Mapa.BOTAO_PORTA_OESTE_X || x == Mapa.BOTAO_PORTA_LESTE_X) {
            boolean oeste = x == Mapa.BOTAO_PORTA_OESTE_X;
            for (Animatronico a : bichos) {
                if (a.noEscritorio && a.portaOeste == oeste) {
                    p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.6f, 0.4f);
                    p.sendActionBar(Turno.txt("§cA porta emperrou!"));
                    return;
                }
            }
            boolean nova = !(oeste ? portaOeste : portaLeste);
            if (oeste) portaOeste = nova;
            else portaLeste = nova;
            mapa.porta(oeste, nova);
            som(nova ? Sound.BLOCK_IRON_TRAPDOOR_CLOSE : Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 0.5f);
            som(Sound.BLOCK_PISTON_EXTEND, 0.8f, 0.6f);
        } else {
            boolean oeste = x == Mapa.BOTAO_LUZ_OESTE_X;
            boolean ligar = !(oeste ? luzOeste : luzLeste);
            if (oeste) {
                luzOeste = ligar;
                luzOesteAte = ticks + 40;
            } else {
                luzLeste = ligar;
                luzLesteAte = ticks + 40;
            }
            mapa.luz(oeste, ligar);
            som(Sound.BLOCK_LEVER_CLICK, 0.7f, 1.4f);
            if (ligar) {
                for (Animatronico a : bichos) {
                    if (!a.noEscritorio && a.noCanto() && a.portaOeste == oeste) {
                        som(Sound.ENTITY_ENDERMAN_STARE, 0.7f, 0.6f);
                        som(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1f, 0.5f);
                        if (a.boneco != null) a.olharPara(p.getLocation());
                    }
                }
            }
        }
        barra();
    }

    /** Se o cliente saiu do lugar mesmo assim (pulo no celular, empurrao), volta para a cadeira/camera olhando para o mesmo lado. */
    private void amarrar(Vigia vg, Player p) {
        Location ancora = vg.ancora;
        if (ancora == null || !p.getWorld().equals(ancora.getWorld())) return;
        Location l = p.getLocation();
        double dx = l.getX() - ancora.getX(), dz = l.getZ() - ancora.getZ(), dy = l.getY() - ancora.getY();
        if (dx * dx + dz * dz <= 0.09 && dy <= 0.6 && dy >= -1.2) return;
        Location v = ancora.clone();
        v.setYaw(l.getYaw());
        v.setPitch(l.getPitch());
        p.teleport(v);
    }

    private ItemStack botaoSair() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Turno.txt("§c§lSAIR DAS CÂMERAS"));
        m.lore(List.of(Turno.txt("§7Toque aqui (ou agache, pule ou ande para trás)"), Turno.txt("§7para largar o tablet e voltar para a cadeira")));
        it.setItemMeta(m);
        return it;
    }

    private void dicaDeSaida(Player p) {
        p.sendActionBar(Turno.txt("§e§lSHIFT§7, §e§lESPAÇO §7ou §e§lQ §f= sair das câmeras  §8|  §7clique = trocar de câmera"));
    }

    void abrirCameras(Player p) {
        Vigia v = vigia(p);
        if (v == null || acabou || semEnergia || v.camera >= 0) return;
        v.cameraDesde = ticks;
        mostrarCamera(v, p, 0);
        p.getInventory().setItem(8, botaoSair());
        dicaDeSaida(p);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.8f);
    }

    void proximaCamera(Player p, int passo) {
        Vigia v = vigia(p);
        if (v == null || v.camera < 0 || acabou) return;
        int n = Mapa.CAMERAS.length;
        mostrarCamera(v, p, ((v.camera + passo) % n + n) % n);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.7f);
    }

    private void mostrarCamera(Vigia v, Player p, int i) {
        v.camera = i;
        Location c = mapa.camera(i);
        if (c == null) {
            // cozinha: so audio
            p.teleport(mapa.cameraEscura());
            v.ancora = mapa.cameraEscura();
            p.sendTitle("§8§lSEM SINAL", "§7Cozinha — somente áudio", 0, 40, 10);
            boolean alguem = false;
            for (Animatronico a : bichos) if (!a.noEscritorio && a.lugarAtual().equals("cozinha")) alguem = true;
            if (alguem) {
                for (int k = 0; k < 6; k++) {
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        Player j = v.p();
                        if (j != null && v.camera >= 0 && Mapa.CAMERAS[v.camera].equals("cozinha")) j.playSound(j.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                    }, 8L * k);
                }
            }
        } else {
            p.teleport(c);
            v.ancora = c.clone();
            p.sendTitle("", "§7CAM " + Mapa.CAMERAS_NOME[i], 0, 30, 10);
        }
        for (Animatronico a : bichos) if (a.boneco != null && salaDe(a).equals(Mapa.CAMERAS[i])) a.olharPara(c == null ? p.getLocation() : c);
        barra();
    }

    void fecharCameras(Player p) {
        Vigia v = vigia(p);
        if (v == null || v.camera < 0) return;
        v.camera = -1;
        p.teleport(v.lugar);
        v.ancora = v.lugar.clone();
        p.getInventory().setItem(8, null);
        p.getInventory().setHeldItemSlot(0);
        p.sendActionBar(Turno.txt(""));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.4f);
        barra();
    }

    /** Um vigia saiu no meio (desistiu ou caiu): os outros continuam; sem ninguem, a noite acaba. */
    void remover(Player p) {
        Vigia v = vigia(p);
        if (v == null) return;
        vigias.remove(v);
        if (v.barra != null) v.barra.removeAll();
        if (acabou) return;
        if (vigias.isEmpty()) terminar(false, null);
        else {
            aviso("§7" + p.getName() + " saiu do turno. §fVocês continuam!");
            barra();
        }
    }

    // ------------------------------------------------------------------ susto e fim

    private void susto(Animatronico a) {
        if (acabou) return;
        acabou = true;
        if (gente().isEmpty()) {
            terminar(false, null);
            return;
        }
        sustoDe = a;
        Location cad = mapa.loc(Mapa.CADEIRA);
        if (a.boneco != null) {
            Location frente = cad.clone().add(0, 0, -1.1);
            frente.setYaw(0);
            a.boneco.teleport(frente);
            var esc = a.boneco.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (esc != null) esc.setBaseValue(1.5);
            a.susto(1.5);
            a.brilhar(true);
        }
        for (Vigia v : vigias) {
            Player p = v.p();
            if (p == null) continue;
            v.camera = -1;
            v.ancora = null; // susto: o olhar do jogador e levado para o bicho, sem amarra
            // sozinho: na cadeira; em grupo: cada um no seu lugar, virado para o bicho
            final Location base = cad.clone();
            if (coop()) {
                base.setX(v.lugar.getX());
                double dx = cad.getX() - base.getX(), dz = -1.1;
                base.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            }
            base.setPitch(0);
            p.teleport(base);
            p.getInventory().setItem(8, null);
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0, false, false, false));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.7f);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1f, 1.9f);
            p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1f, 0.6f);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
            p.sendTitle("§4§l" + a.nome.toUpperCase(), coop() ? "§cpegou vocês" : "§cte pegou", 0, 50, 10);
            // tremedeira: o olhar e sacudido por 1 segundo
            for (int i = 1; i <= 12; i++) {
                final int k = i;
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    Player j = v.p();
                    if (j == null) return;
                    Location l = base.clone();
                    l.setYaw(base.getYaw() + (k % 2 == 0 ? 12 : -12) * (1 - k / 14f));
                    l.setPitch((k % 3 == 0 ? -8 : 6) * (1 - k / 14f));
                    j.teleport(l);
                }, i * 2L);
            }
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> terminar(false, a), 50L);
    }

    void terminar(boolean venceu, Animatronico por) {
        acabou = true;
        if (tarefa != null) tarefa.cancel();
        for (Animatronico a : bichos) a.sumir();
        for (TextDisplay t : textos) if (t.isValid()) t.remove();
        textos.clear();
        mapa.porta(true, false);
        mapa.porta(false, false);
        mapa.luz(true, false);
        mapa.luz(false, false);
        mapa.cortina(true);
        mapa.luzEscritorio(true);
        int quantos = vigias.size();
        for (Vigia v : new ArrayList<>(vigias)) {
            if (v.barra != null) v.barra.removeAll();
            Player p = v.p();
            if (p == null) continue;
            p.setWalkSpeed(0.2f);
            var pulo = p.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH);
            if (pulo != null) pulo.setBaseValue(pulo.getDefaultValue());
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            if (venceu) {
                p.sendTitle("§a§l6:00 AM", (quantos > 1 ? "§eVocês sobreviveram à noite " : "§eVocê sobreviveu à noite ") + (noite == 6 ? "personalizada" : noite) + "!", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1.2f);
                for (int k = 0; k < 6; k++) {
                    final float tom = (float) Math.pow(2, ((k % 2 == 0 ? 0 : 7) - 12) / 12.0 + 1);
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        Player j = v.p();
                        if (j != null) j.playSound(j.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, tom);
                    }, 10L * k);
                }
                pl.venceu(p, noite);
            } else {
                p.sendMessage(Turno.txt("§4§lGAME OVER §7— " + (por == null ? "a noite acabou mal" : por.cor + por.nome + (quantos > 1 ? " §7pegou vocês às " : " §7te pegou às ") + HORAS[Math.min(5, hora())])
                        + ". §fTente de novo: /turno"));
            }
            Bukkit.getScheduler().runTaskLater(pl, () -> pl.sair(p, false), venceu ? 70L : 30L);
        }
        pl.noiteAcabou(this);
    }
}
