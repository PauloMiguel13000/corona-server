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
    final UUID jogador;
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
    int camera = -1;        // -1 = fora do tablet
    int botoFase;           // 0..3
    int botoCorridas;
    boolean botoCorrendo;
    boolean semEnergia;
    int sustoEm = -1;       // tick do susto (apos acabar a energia)
    Animatronico sustoDe;
    boolean acabou;
    BossBar barra;
    BukkitTask tarefa;
    final List<TextDisplay> textos = new ArrayList<>();
    int ultimoAvisoBloqueio;

    Noite(Turno pl, Mapa mapa, int instancia, Player p, int noite, int[] ia) {
        this.pl = pl;
        this.mapa = mapa;
        this.instancia = instancia;
        this.jogador = p.getUniqueId();
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

    Player p() {
        return Bukkit.getPlayer(jogador);
    }

    // ------------------------------------------------------------------ inicio

    void comecar() {
        Player p = p();
        if (p == null) return;
        duracaoTicks = Math.max(60 * 20, pl.getConfig().getInt("minutos-por-noite", 6) * 60 * 20);
        mapa.porta(true, false);
        mapa.porta(false, false);
        mapa.luz(true, false);
        mapa.luz(false, false);
        mapa.cortina(true);
        mapa.luzEscritorio(true);
        for (Animatronico a : bichos) a.nascer(pl, mapa);
        Guarda.limpar(p);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(mapa.loc(Mapa.CADEIRA));
        ancora = mapa.loc(Mapa.CADEIRA);
        p.setWalkSpeed(0f);
        // o vigia fica sentado: sem pulo (pular tirava a pessoa da cadeira)
        var pulo = p.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH);
        if (pulo != null) pulo.setBaseValue(0);
        p.setFoodLevel(20);
        p.getInventory().setItem(0, tablet());
        p.getInventory().setHeldItemSlot(0);
        // kits/menus de outros plugins chegam com atraso: garante a mesa limpa (so o tablet)
        for (long d : new long[]{10L, 45L, 100L}) Bukkit.getScheduler().runTaskLater(pl, () -> {
            Player j = p();
            if (j == null || acabou) return;
            ItemStack[] c = j.getInventory().getContents();
            for (int i = 1; i < c.length; i++) if (c[i] != null) j.getInventory().setItem(i, null);
            j.getInventory().setItem(0, tablet());
        }, d);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
        barra = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
        barra.addPlayer(p);
        letreiros();
        p.showTitle(net.kyori.adventure.title.Title.title(Turno.txt("§f§lNOITE " + (noite == 6 ? "PERSONALIZADA" : noite)), Turno.txt("§712:00 AM — Zoo Mecânico do Seu Zé")));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
        telefonema(p);
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
    private void telefonema(Player p) {
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
                Player j = p();
                if (j != null && !acabou) j.sendMessage(Turno.txt(f));
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
        if (camera >= 0) u++;
        return u;
    }

    private void tick() {
        Player p = p();
        if (p == null || acabou) {
            if (!acabou) terminar(false, null);
            return;
        }
        ticks++;
        if (ticks >= duracaoTicks) {
            terminar(true, null);
            return;
        }
        if (ticks % 10 == 0) for (Animatronico a : bichos) a.rever();
        amarrar(p);
        // nas cameras: lembra o tempo todo como sair (era so no texto do tablet e muita gente ficava presa)
        if (camera >= 0 && (ticks - cameraDesde) % 40 == 20) {
            boolean aviso = false;
            for (Animatronico a : bichos) if (a.noEscritorio) aviso = true;
            if (!aviso) dicaDeSaida(p);
            if (p.getInventory().getItem(8) == null) p.getInventory().setItem(8, botaoSair());
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
            if (ticks % 200 == 0 && rnd.nextInt(3) == 0) ambiente(p);
        }
        if (!semEnergia) {
            for (Animatronico a : bichos) andar(a, p);
        } else if (sustoEm > 0 && ticks >= sustoEm) {
            susto(capi);
        }
        // bicho dentro do escritorio: o susto vem quando o tablet abaixa (ou logo, se ja esta abaixado)
        for (Animatronico a : bichos) {
            if (!a.noEscritorio) continue;
            if (camera < 0 && ticks - a.ticks > 30 + rnd.nextInt(30)) {
                susto(a);
                return;
            }
            if (camera >= 0 && ticks - a.ticks > 100) {
                // segurar o tablet nao salva: ele arranca o tablet da sua mao
                fecharCameras(p);
                susto(a);
                return;
            }
        }
        if (ticks % 10 == 0) p.setFoodLevel(20);
    }

    private void barra() {
        if (barra == null) return;
        int h = hora();
        String txt = "§e⚡ " + Math.max(0, (int) Math.ceil(energia)) + "%  §7|  §f" + (h >= 6 ? "6 AM" : HORAS[h]) + "  §7|  uso " + "▮".repeat(uso())
                + (camera >= 0 ? "  §7|  §bCAM " + Mapa.CAMERAS_NOME[camera] + "  §7|  §eSHIFT, ESPAÇO ou Q = sair" : "");
        barra.setTitle(txt);
        barra.setProgress(Math.max(0, Math.min(1, energia / 100.0)));
        barra.setColor(energia > 50 ? BarColor.GREEN : energia > 20 ? BarColor.YELLOW : BarColor.RED);
    }

    private void ambiente(Player p) {
        Location l = p.getLocation();
        switch (rnd.nextInt(5)) {
            case 0 -> p.playSound(l, Sound.BLOCK_IRON_DOOR_CLOSE, 0.3f, 0.5f);
            case 1 -> p.playSound(l, Sound.ENTITY_VILLAGER_AMBIENT, 0.25f, 0.4f);
            case 2 -> p.playSound(l, Sound.BLOCK_ANVIL_LAND, 0.15f, 0.6f);
            case 3 -> p.playSound(l, Sound.AMBIENT_CAVE, 0.6f, 1f);
            default -> p.playSound(l, Sound.BLOCK_CHEST_OPEN, 0.3f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ animatronicos

    private void andar(Animatronico a, Player p) {
        if (a.noEscritorio || a.ia <= 0) return;
        if (ticks % a.intervaloTicks != 0) return;
        if (a == capi && camera >= 0) return;                 // o Capi so anda quando ninguem olha as cameras
        if (a == boto && camera >= 0 && Mapa.CAMERAS[camera].equals("cortina")) return;  // olhar a Cortina segura o Boto
        if (rnd.nextInt(20) + 1 > a.ia) return;
        if (a == boto) {
            andarBoto(p);
            return;
        }
        if (a.noCanto()) {
            boolean fechada = a.portaOeste ? portaOeste : portaLeste;
            if (fechada) {
                a.posicao = rnd.nextInt(2);
                a.mover(mapa, a.lugarAtual());
                p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.5f, 0.3f);
            } else entrar(a, p);
            return;
        }
        a.posicao++;
        a.mover(mapa, a.lugarAtual());
        if (a.noCanto()) p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_STEP, 0.6f, 0.4f);
        else p.playSound(p.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.25f, 0.5f);
        if (camera >= 0 && Mapa.CAMERAS[camera].equals(salaDe(a))) {
            // ele acabou de aparecer na camera que o vigia olha: um "pulo" na imagem
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
        }
    }

    private String salaDe(Animatronico a) {
        String l = a.lugarAtual();
        if (l.startsWith("palco")) return "palco";
        if (l.startsWith("salao")) return "salao";
        if (l.startsWith("cortina")) return "cortina";
        return l;
    }

    private void andarBoto(Player p) {
        if (botoCorrendo) return;
        if (botoFase < 3) {
            botoFase++;
            boto.posicao = botoFase;
            boto.mover(mapa, boto.lugarAtual());
            if (botoFase == 2) mapa.cortina(false);
            if (botoFase >= 2) p.playSound(p.getLocation(), Sound.BLOCK_WOOL_BREAK, 0.6f, 0.4f);
            return;
        }
        // corre pelo corredor oeste
        botoCorrendo = true;
        boto.posicao = 4;
        boto.mover(mapa, "corr_oeste");
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_STEP, 1f, 1.4f);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (acabou || boto.boneco == null) return;
            Player j = p();
            if (j == null) return;
            j.playSound(j.getLocation(), Sound.ENTITY_ZOMBIE_STEP, 1f, 1.6f);
            boto.posicao = 5;
            boto.mover(mapa, "canto_oeste");
        }, 15L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (acabou || boto.boneco == null) return;
            Player j = p();
            if (j == null) return;
            if (portaOeste) {
                // bate na porta e volta: cada corrida tira mais energia
                int perda = 1 + 5 * botoCorridas;
                botoCorridas++;
                energia = Math.max(0, energia - perda);
                j.playSound(j.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.7f);
                j.playSound(j.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1f, 0.6f);
                j.sendActionBar(Turno.txt("§d§lBAM! §fO Boto bateu na porta: §c-" + perda + "% de energia"));
                botoFase = 0;
                boto.posicao = 0;
                boto.mover(mapa, "cortina_0");
                mapa.cortina(true);
                botoCorrendo = false;
                if (energia <= 0) acabouEnergia();
            } else {
                entrar(boto, j);
                botoCorrendo = false;
            }
        }, 30L);
    }

    private void entrar(Animatronico a, Player p) {
        a.noEscritorio = true;
        a.ticks = ticks;
        a.mover(mapa, a.portaOeste ? "canto_oeste" : "canto_leste");
        if (a.boneco != null) {
            a.boneco.teleport(mapa.loc(a.portaOeste ? -3.5 : 3.5, Mapa.Y + 1, 0.5, a.portaOeste ? -90 : 90, 0));
            a.sincronizar();
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 0.6f);
        if (camera >= 0) p.sendActionBar(Turno.txt("§4Algo entrou no escritório..."));
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
        Player p = p();
        if (camera >= 0) fecharCameras(p);
        if (p == null) return;
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false, false));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.4f);
        p.sendMessage(Turno.txt("§4§lA ENERGIA ACABOU. §7Está tudo escuro..."));
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
                Player j = p();
                if (j != null && !acabou) j.playSound(j.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, tom);
            }, 20L + i * 9L);
        }
        sustoEm = ticks + 20 * (12 + rnd.nextInt(18));
    }

    // ------------------------------------------------------------------ acoes do vigia

    void botao(Player p, int x) {
        if (acabou) return;
        if (camera >= 0) return;
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
            p.playSound(p.getLocation(), nova ? Sound.BLOCK_IRON_TRAPDOOR_CLOSE : Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 0.5f);
            p.playSound(p.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.8f, 0.6f);
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
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.7f, 1.4f);
            if (ligar) {
                for (Animatronico a : bichos) {
                    if (!a.noEscritorio && a.noCanto() && a.portaOeste == oeste) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.7f, 0.6f);
                        p.playSound(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1f, 0.5f);
                        if (a.boneco != null) a.olharPara(p.getLocation());
                    }
                }
            }
        }
        barra();
    }

    /** Onde o vigia tem de ficar (cadeira ou camera atual); null = solto (susto, fim). */
    private Location ancora;

    /** Se o cliente saiu do lugar mesmo assim (pulo no celular, empurrao), volta para a cadeira/camera olhando para o mesmo lado. */
    private void amarrar(Player p) {
        if (ancora == null || !p.getWorld().equals(ancora.getWorld())) return;
        Location l = p.getLocation();
        double dx = l.getX() - ancora.getX(), dz = l.getZ() - ancora.getZ(), dy = l.getY() - ancora.getY();
        if (dx * dx + dz * dz <= 0.09 && dy <= 0.6 && dy >= -1.2) return;
        Location v = ancora.clone();
        v.setYaw(l.getYaw());
        v.setPitch(l.getPitch());
        p.teleport(v);
    }

    /** Tick em que as cameras abriram (as teclas de sair so valem um instante depois, para nao fechar sem querer). */
    int cameraDesde;

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
        if (acabou || semEnergia || camera >= 0) return;
        cameraDesde = ticks;
        mostrarCamera(p, 0);
        p.getInventory().setItem(8, botaoSair());
        dicaDeSaida(p);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.8f);
    }

    void proximaCamera(Player p, int passo) {
        if (camera < 0 || acabou) return;
        int n = Mapa.CAMERAS.length;
        mostrarCamera(p, ((camera + passo) % n + n) % n);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.7f);
    }

    private void mostrarCamera(Player p, int i) {
        camera = i;
        Location c = mapa.camera(i);
        if (c == null) {
            // cozinha: so audio
            p.teleport(mapa.cameraEscura());
            ancora = mapa.cameraEscura();
            p.sendTitle("§8§lSEM SINAL", "§7Cozinha — somente áudio", 0, 40, 10);
            boolean alguem = false;
            for (Animatronico a : bichos) if (!a.noEscritorio && a.lugarAtual().equals("cozinha")) alguem = true;
            if (alguem) {
                for (int k = 0; k < 6; k++) {
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        Player j = p();
                        if (j != null && camera >= 0 && Mapa.CAMERAS[camera].equals("cozinha")) j.playSound(j.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                    }, 8L * k);
                }
            }
        } else {
            p.teleport(c);
            ancora = c.clone();
            p.sendTitle("", "§7CAM " + Mapa.CAMERAS_NOME[i], 0, 30, 10);
        }
        for (Animatronico a : bichos) if (a.boneco != null && salaDe(a).equals(Mapa.CAMERAS[i])) a.olharPara(c == null ? p.getLocation() : c);
        barra();
    }

    void fecharCameras(Player p) {
        if (camera < 0) return;
        camera = -1;
        if (p != null) {
            p.teleport(mapa.loc(Mapa.CADEIRA));
            ancora = mapa.loc(Mapa.CADEIRA);
            p.getInventory().setItem(8, null);
            p.getInventory().setHeldItemSlot(0);
            p.sendActionBar(Turno.txt(""));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.4f);
        }
        barra();
    }

    // ------------------------------------------------------------------ susto e fim

    private void susto(Animatronico a) {
        if (acabou) return;
        acabou = true;
        Player p = p();
        if (p == null) {
            terminarSemJogador();
            return;
        }
        sustoDe = a;
        camera = -1;
        Location cad = mapa.loc(Mapa.CADEIRA);
        p.teleport(cad);
        ancora = null; // susto: a camera do jogador e levada para a frente do bicho, sem amarra
        if (a.boneco != null) {
            Location frente = cad.clone().add(0, 0, -1.1);
            frente.setYaw(0);
            a.boneco.teleport(frente);
            var esc = a.boneco.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (esc != null) esc.setBaseValue(1.5);
            a.susto(1.5);
            a.brilhar(true);
        }
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0, false, false, false));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.7f);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1f, 1.9f);
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        p.sendTitle("§4§l" + a.nome.toUpperCase(), "§cte pegou", 0, 50, 10);
        // tremedeira: o olhar e sacudido por 1 segundo
        for (int i = 1; i <= 12; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                Player j = p();
                if (j == null) return;
                Location l = cad.clone();
                l.setYaw(180 + (k % 2 == 0 ? 12 : -12) * (1 - k / 14f));
                l.setPitch((k % 3 == 0 ? -8 : 6) * (1 - k / 14f));
                j.teleport(l);
            }, i * 2L);
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> terminar(false, a), 50L);
    }

    void terminar(boolean venceu, Animatronico por) {
        acabou = true;
        if (tarefa != null) tarefa.cancel();
        Player p = p();
        for (Animatronico a : bichos) a.sumir();
        for (TextDisplay t : textos) if (t.isValid()) t.remove();
        textos.clear();
        if (barra != null) barra.removeAll();
        mapa.porta(true, false);
        mapa.porta(false, false);
        mapa.luz(true, false);
        mapa.luz(false, false);
        mapa.cortina(true);
        mapa.luzEscritorio(true);
        if (p != null) {
            p.setWalkSpeed(0.2f);
            var pulo = p.getAttribute(org.bukkit.attribute.Attribute.JUMP_STRENGTH);
            if (pulo != null) pulo.setBaseValue(pulo.getDefaultValue());
            p.removePotionEffect(PotionEffectType.DARKNESS);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            if (venceu) {
                p.sendTitle("§a§l6:00 AM", "§eVocê sobreviveu à noite " + (noite == 6 ? "personalizada" : noite) + "!", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1.2f);
                for (int k = 0; k < 6; k++) {
                    final float tom = (float) Math.pow(2, ((k % 2 == 0 ? 0 : 7) - 12) / 12.0 + 1);
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        Player j = p();
                        if (j != null) j.playSound(j.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, tom);
                    }, 10L * k);
                }
                pl.venceu(p, noite);
            } else {
                p.sendMessage(Turno.txt("§4§lGAME OVER §7— " + (por == null ? "a noite acabou mal" : por.cor + por.nome + " §7te pegou às " + HORAS[Math.min(5, hora())])
                        + ". §fTente de novo: /turno"));
            }
            Bukkit.getScheduler().runTaskLater(pl, () -> pl.sair(p, false), venceu ? 70L : 30L);
        }
        pl.noiteAcabou(this);
    }

    private void terminarSemJogador() {
        terminar(false, null);
    }
}
