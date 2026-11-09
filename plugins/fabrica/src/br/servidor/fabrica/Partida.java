package br.servidor.fabrica;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Uma partida da Fabrica Alegria (1 a 4 jogadores, co-op) numa instancia. */
final class Partida {

    static final double CABO_MAX = 34;
    static final double TUTU_VEL = 0.225;       // blocos por tick nos dutos (jogador correndo = 0.28; andando = 0.215)
    static final double TUTU_VEL_SALA = 0.17;

    final Fabrica pl;
    final Planta pt;
    final int instancia;
    final List<UUID> jogadores = new ArrayList<>();
    private final Map<UUID, Estado> estados = new HashMap<>();
    private final Random rnd = new Random();

    final Set<String> feito = new HashSet<>();
    int checkpoint;
    final String codigo;
    private final StringBuilder digitado = new StringBuilder();
    private final Map<String, UUID> plugueCom = new HashMap<>();
    private final Map<String, Integer> plugueMao = new HashMap<>();
    private final Set<String> tomadasLigadas = new HashSet<>();
    private final Map<String, UUID> caixaCom = new HashMap<>();
    private final Set<String> entregues = new HashSet<>();

    private final List<Entity> entidades = new ArrayList<>();
    private TextDisplay tela;
    private final Map<String, ItemDisplay> plugues = new HashMap<>();
    private Mannequin tutu, bolota;
    BossBar barra;
    BukkitTask tarefa;
    long inicioMs;
    int ticks;
    boolean acabou;

    // perseguicao
    boolean cacando, tutuNoDuto, tutuNaSalaFinal, finalFeito;
    double tutuDist;
    int cacadaEspera;
    private final double[] acumulado;

    static final class Estado {
        boolean azul, vermelha, pego, salvo;
        final String[] mao = new String[2];
        final long[] ultimo = new long[2];
        Display carregando;
        int caiuEm = -1000;
    }

    Partida(Fabrica pl, Planta pt, int instancia, List<Player> ps) {
        this.pl = pl;
        this.pt = pt;
        this.instancia = instancia;
        for (Player p : ps) {
            jogadores.add(p.getUniqueId());
            estados.put(p.getUniqueId(), new Estado());
        }
        codigo = String.format("%04d", 1000 + rnd.nextInt(9000));
        acumulado = new double[Planta.CAMINHO.length];
        for (int i = 1; i < Planta.CAMINHO.length; i++) {
            double dx = Planta.CAMINHO[i][0] - Planta.CAMINHO[i - 1][0], dz = Planta.CAMINHO[i][1] - Planta.CAMINHO[i - 1][1];
            acumulado[i] = acumulado[i - 1] + Math.sqrt(dx * dx + dz * dz);
        }
    }

    List<Player> online() {
        List<Player> l = new ArrayList<>();
        for (UUID u : jogadores) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) l.add(p);
        }
        return l;
    }

    Estado estado(Player p) {
        return estados.get(p.getUniqueId());
    }

    void todos(String msg) {
        for (Player p : online()) p.sendMessage(Fabrica.txt(msg));
    }

    void somTodos(Sound s, float vol, float tom) {
        for (Player p : online()) p.playSound(p.getLocation(), s, vol, tom);
    }

    void titulo(String t, String sub, int fica) {
        for (Player p : online()) p.sendTitle(t, sub, 5, fica, 15);
    }

    // ------------------------------------------------------------------ inicio / reinicio do mapa

    void comecar() {
        zerarMapa();
        inicioMs = System.currentTimeMillis();
        barra = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
        for (Player p : online()) {
            Guarda.limpar(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(pt.loc(Planta.SPAWN));
            p.setFoodLevel(20);
            p.setSaturation(20);
            barra.addPlayer(p);
            p.getInventory().setItem(8, itemSair());
        }
        objetivo("Entre na fábrica: ache a senha da portaria");
        titulo("§6§lFÁBRICA ALEGRIA", "§7Capítulo 1 — A Luva de Gancho", 70);
        somTodos(Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.7f);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            todos("§8§o(Uma fábrica de brinquedos fechada há anos. As luzes ainda funcionam... mais ou menos.)");
            todos("§7Dica: a §fsala de segurança §7(à direita) tem um aparelho de vídeo com a senha.");
            todos("§7As duas mãos da §9Luva de Gancho §7você acha na fábrica: a §9azul §7logo depois do saguão, a §cvermelha §7mais adiante.");
        }, 60L);
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
    }

    private ItemStack itemSair() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Fabrica.txt("§cSair da Fábrica"));
        m.lore(List.of(Fabrica.txt("§7Clique para desistir e voltar")));
        m.getPersistentDataContainer().set(pl.kSair, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    ItemStack itemMao(int mao) {
        ItemStack it = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Fabrica.txt(mao == 0 ? "§9§lMão Azul" : "§c§lMão Vermelha"));
        m.lore(List.of(Fabrica.txt("§7Clique (direito ou esquerdo) mirando"), Fabrica.txt("§7botões, plugues, tomadas, caixas e puxadores."),
                Fabrica.txt("§7A mira ajuda: não precisa acertar em cheio."), Fabrica.txt("§8Para devolver o que segura: atire de onde tirou.")));
        m.getPersistentDataContainer().set(pl.kMao, PersistentDataType.INTEGER, mao);
        m.setUnbreakable(true);
        it.setItemMeta(m);
        // depois do setItemMeta: o meta antigo apagava o item_model e a mao virava uma vara de cenoura (Java e Bedrock)
        try {
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                    net.kyori.adventure.key.Key.key("corona", "props/fab_mao_" + (mao == 0 ? "azul" : "vermelha") + "_f0"));
        } catch (Throwable ignored) {
        }
        return it;
    }

    void zerarMapa() {
        for (Entity e : entidades) if (e != null && e.isValid()) e.remove();
        entidades.clear();
        plugues.clear();
        World w = pt.mundo();
        for (Entity e : w.getNearbyEntities(pt.loc(-25, Planta.Y, 47, 0, 0), 70, 40, 70)) if (!(e instanceof Player)) e.remove();
        pt.portaPortaria(false);
        pt.portaLuva(true);
        pt.portao(false);
        pt.portaVestiario(false);
        pt.luzSaguao(false);
        pt.lampada(4, Planta.Y + 3, 44, false);
        pt.lampada(-22, Planta.Y + 6, 15, false);
        pt.lampada(34, Planta.Y + 3, 89, false);
        pt.lampada(14, Planta.Y + 3, 89, false);
        for (String c : new String[]{"vermelha", "amarela", "azul"}) {
            pt.caixaNaPrateleira(c, true);
            pt.caixaNaPrensa(c, false);
        }
        pt.ponte(0);
        pt.piso(true);
        pt.caixa(-79, Planta.Y - 10, 75, -65, Planta.Y - 1, 93, "air");
        pt.puxador(false);
        pt.por(-79, Planta.Y + 8, 84, "lever[face=wall,facing=east,powered=false]");
        // tela do teclado
        pt.por(4, Planta.Y + 5, 9, "light[level=14]");                // luz em cima do teclado
        pt.por(4, Planta.Y + 2, 9, "light[level=10]");
        tela = texto(pt.loc(4.5, Planta.Y + 5.3, 11.35, 180, 0), "§8[ §f_ _ _ _ §8]", 0.9f, Display.Billboard.FIXED);
        texto(pt.loc(4.5, Planta.Y + 5.75, 11.35, 180, 0), "§7PORTARIA §8— digite a senha", 0.55f, Display.Billboard.FIXED);
        for (Planta.Alvo a : Planta.ALVOS) {
            if (a.tipo() == Planta.Tipo.TECLA)
                texto(pt.loc(a.x() + 0.5, a.y() + 0.55, a.z() + 0.86, 180, 0), "§e§l" + a.cor(), 0.7f, Display.Billboard.FIXED);
        }
        // fitas e vitrines
        objeto(pt.loc(11.5, Planta.Y + 2.15, 7.5, 0, 0), Luva.modeloFita(), 0.6f);
        texto(pt.loc(11.5, Planta.Y + 2.9, 7.5, 0, 0), "§e▶ FITA 1\n§7clique no aparelho", 0.6f, Display.Billboard.CENTER);
        objeto(pt.loc(21.5, Planta.Y + 2.15, 73.5, 0, 0), Luva.modeloFita(), 0.6f);
        texto(pt.loc(21.5, Planta.Y + 2.9, 73.5, 0, 0), "§e▶ FITA 2", 0.6f, Display.Billboard.CENTER);
        objeto(pt.loc(-27.5, Planta.Y + 2.4, 21.5, 90, 0), Luva.modeloMao(0), 0.7f);
        texto(pt.loc(-27.5, Planta.Y + 3.4, 21.5, 0, 0), "§9§lLUVA DE GANCHO\n§7clique no pedestal", 0.7f, Display.Billboard.CENTER);
        objeto(pt.loc(28.5, Planta.Y + 2.4, 72.5, -90, 0), Luva.modeloMao(1), 0.7f);
        texto(pt.loc(28.5, Planta.Y + 3.4, 72.5, 0, 0), "§c§lMÃO VERMELHA\n§7clique no pedestal", 0.7f, Display.Billboard.CENTER);
        // plugues
        for (String id : new String[]{"plugue_saguao", "plugue_azul", "plugue_vermelho"}) plugue(id, casaPlugue(id));
        texto(pt.loc(4.5, Planta.Y + 4.3, 43.6, 180, 0), "§e⚡ TOMADA", 0.6f, Display.Billboard.CENTER);
        texto(pt.loc(34.5, Planta.Y + 4.3, 88.6, 0, 0), "§9⚡ TOMADA AZUL", 0.6f, Display.Billboard.CENTER);
        texto(pt.loc(14.5, Planta.Y + 4.3, 88.6, 0, 0), "§c⚡ TOMADA VERMELHA", 0.6f, Display.Billboard.CENTER);
        texto(pt.loc(-21.5, Planta.Y + 4.4, 15.6, 0, 0), "§7Botão da porta\n§8(longe demais para a mão)", 0.55f, Display.Billboard.CENTER);
        // placas com setas nas portas (o caminho fica claro no celular)
        texto(pt.loc(-14.6, Planta.Y + 4.6, 20.5, -90, 0), "§9§l◄ SALA DA LUVA\n§7pegue a Mão Azul", 0.9f, Display.Billboard.CENTER);
        texto(pt.loc(0.5, Planta.Y + 5.6, 43.4, 180, 0), "§e§lLINHA DE PRODUÇÃO\n§7precisa de energia (plugue ► tomada)", 0.8f, Display.Billboard.CENTER);
        texto(pt.loc(17.6, Planta.Y + 4.6, 67.5, 90, 0), "§c§lVESTIÁRIO ►\n§7Mão Vermelha", 0.9f, Display.Billboard.CENTER);
        texto(pt.loc(24.5, Planta.Y + 4.4, 73.6, 0, 0), "§7▼ §fSALA DE CONTROLE", 0.8f, Display.Billboard.CENTER);
        texto(pt.loc(15.4, Planta.Y + 4.4, 83.5, 90, 0), "§e◄ §lGALERIA (ponte)", 0.8f, Display.Billboard.CENTER);
        texto(pt.loc(0.5, Planta.Y + 9.2, 44.4, 180, 0), "§6§lFÁBRICA ALEGRIA\n§e\"Todo brinquedo tem um abraço guardado\"", 1.6f, Display.Billboard.FIXED);
        // Tutu no pedestal (gigante, parado)
        tutu = boneco("tutu", pt.loc(Planta.TUTU_PEDESTAL), 3.2);
        cacando = false;
        tutuNaSalaFinal = false;
        tutuNoDuto = false;
        finalFeito = false;
        tutuDist = 0;
    }

    private TextDisplay texto(Location l, String t, float esc, Display.Billboard bb) {
        TextDisplay d = l.getWorld().spawn(l, TextDisplay.class, x -> {
            x.text(Fabrica.txt(t));
            x.setBillboard(bb);
            x.setPersistent(false);
            x.setShadowed(true);
            x.setBackgroundColor(org.bukkit.Color.fromARGB(80, 0, 0, 0));
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(esc), new AxisAngle4f()));
        });
        entidades.add(d);
        return d;
    }

    private ItemDisplay objeto(Location l, ItemStack it, float esc) {
        ItemDisplay d = l.getWorld().spawn(l, ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setPersistent(false);
            x.setTeleportDuration(1);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(esc), new AxisAngle4f()));
        });
        entidades.add(d);
        return d;
    }

    /** Onde o plugue fica descansando: na frente do carretel, do lado de dentro da sala. */
    private Location casaPlugue(String id) {
        Planta.Alvo a = Planta.alvo(id);
        double dx = switch (id) {
            case "plugue_azul" -> 0.95;       // carretel na parede oeste da sala de controle
            default -> -0.95;                 // saguao (parede leste) e vermelho (parede leste da sala de controle)
        };
        Location l = pt.centro(a).add(dx, 0, 0);
        l.setYaw(dx > 0 ? -90 : 90);
        return l;
    }

    private void plugue(String id, Location l) {
        plugues.put(id, objeto(l, Luva.modeloPlugue(), 0.5f));
    }

    private Mannequin boneco(String id, Location l, double escala) {
        Mannequin m = l.getWorld().spawn(l, Mannequin.class, CreatureSpawnEvent.SpawnReason.CUSTOM, x -> {
            var perfil = pl.skins.perfil(id);
            if (perfil != null) x.setProfile(perfil);
            x.setImmovable(true);
            x.setGravity(false);
            x.setCollidable(false);
            x.setSilent(true);
            x.setInvulnerable(true);
            x.setPersistent(false);
            x.setRemoveWhenFarAway(false);
            x.setCustomNameVisible(false);
            x.setDescription(net.kyori.adventure.text.Component.empty());
            var esc = x.getAttribute(Attribute.SCALE);
            if (esc != null) esc.setBaseValue(escala);
        });
        entidades.add(m);
        return m;
    }

    void skinPronta(String id) {
        var perfil = pl.skins.perfil(id);
        if (perfil == null) return;
        if (id.equals("tutu") && tutu != null && tutu.isValid()) tutu.setProfile(perfil);
        if (id.equals("bolota") && bolota != null && bolota.isValid()) bolota.setProfile(perfil);
    }

    private void escala(Mannequin m, double e) {
        if (m == null) return;
        var a = m.getAttribute(Attribute.SCALE);
        if (a != null && Math.abs(a.getBaseValue() - e) > 0.01) a.setBaseValue(e);
    }

    void objetivo(String o) {
        if (barra != null) barra.setTitle("§e" + o);
    }

    // ------------------------------------------------------------------ cliques

    /** Clique com a mao (sem luva) num bloco perto: teclado, fita, pedestal. true = tratado. */
    boolean clicouBloco(Player p, Block b) {
        Planta.Alvo a = pt.alvoEm(b);
        if (a == null) return false;
        switch (a.tipo()) {
            case TECLA -> tecla(p, a.cor());
            case FITA -> fita(a.id());
            case PEDESTAL_AZUL -> pegarMao(p, 0);
            case PEDESTAL_VERMELHO -> pegarMao(p, 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void tecla(Player p, String t) {
        if (feito.contains("portaria")) return;
        p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1.6f);
        switch (t) {
            case "C" -> digitado.setLength(0);
            case "OK" -> {
                if (digitado.toString().equals(codigo)) {
                    feito.add("portaria");
                    tela.text(Fabrica.txt("§a[ " + String.join(" ", codigo.split("")) + " ]"));
                    somTodos(Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        pt.portaPortaria(true);
                        somTodos(Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.6f);
                        somTodos(Sound.BLOCK_PISTON_CONTRACT, 1f, 0.5f);
                        objetivo("Pegue a Luva de Gancho: sala à ESQUERDA (oeste) do saguão");
                        todos("§aA porta abriu. §7Do outro lado, o saguão. A §9Luva de Gancho §7fica na sala à §fesquerda§7 (oeste).");
                    }, 15L);
                    return;
                }
                digitado.setLength(0);
                tela.text(Fabrica.txt("§c[ ERRO ]"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
                Bukkit.getScheduler().runTaskLater(pl, () -> atualizarTela(), 25L);
                return;
            }
            default -> {
                if (digitado.length() < 4) digitado.append(t);
            }
        }
        atualizarTela();
    }

    private void atualizarTela() {
        if (tela == null || feito.contains("portaria")) return;
        StringBuilder sb = new StringBuilder("§8[ §f");
        for (int i = 0; i < 4; i++) sb.append(i < digitado.length() ? digitado.charAt(i) : '_').append(i < 3 ? " " : "");
        tela.text(Fabrica.txt(sb + " §8]"));
    }

    private void fita(String id) {
        List<String> falas = switch (id) {
            case "fita1" -> List.of("§8§o*chiado de fita* ... *clique*",
                    "§6[Seu Arlindo, dono da fábrica] §fSe alguém está vendo isto, a Fábrica Alegria fechou as portas.",
                    "§6[Seu Arlindo] §fTrocaram a senha da portaria de novo. Anota aí: §e§l" + codigo + "§f.",
                    "§6[Seu Arlindo] §fO Tutu fica no saguão, no pedestal. Ele é só um boneco grande... §7*pausa longa*",
                    "§6[Seu Arlindo] §f...não deixe a energia do saguão desligada. Ele não gosta do escuro.",
                    "§8§o*a fita acaba num chiado*");
            case "fita2" -> List.of("§8§o*chiado* ... *clique*",
                    "§d[Marta, turno da noite] §fHoje o Tutu virou a cabeça sozinho na linha de produção. Eu vi.",
                    "§d[Marta] §fO Seu Arlindo diz que é a \"alegria\" que colocam no recheio. Que alegria é essa que olha pra gente?",
                    "§d[Marta] §fPeguei a §cmão vermelha§f no armário. Com as duas mãos dá pra ligar a ponte na sala de controle.",
                    "§d[Marta] §fSe der errado... corre pelos dutos. Ele é grande demais pra caber lá. §7*barulho de metal*",
                    "§d[Marta] §f...não é?",
                    "§8§o*a fita acaba*");
            default -> List.of();
        };
        if (falas.isEmpty() || feito.contains("tocando_" + id)) return;
        feito.add("tocando_" + id);
        somTodos(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.5f);
        for (int i = 0; i < falas.size(); i++) {
            final String f = falas.get(i);
            final int k = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (acabou) return;
                todos(f);
                somTodos(Sound.BLOCK_NOTE_BLOCK_BASS, 0.25f, 0.5f + (k % 3) * 0.1f);
                if (k == falas.size() - 1) {
                    feito.remove("tocando_" + id);
                    feito.add("ouviu_" + id);
                    if (id.equals("fita1") && !feito.contains("portaria")) objetivo("Digite a senha " + codigo + " no teclado da portaria");
                }
            }, 10L + i * 70L);
        }
    }

    private void pegarMao(Player p, int mao) {
        Estado e = estado(p);
        if (e == null) return;
        if (mao == 0 ? e.azul : e.vermelha) {
            p.sendActionBar(Fabrica.txt("§7Você já tem essa mão."));
            return;
        }
        if (mao == 0) e.azul = true;
        else e.vermelha = true;
        p.getInventory().setItem(mao, itemMao(mao));
        p.getInventory().setHeldItemSlot(mao);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.2f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.sendTitle(mao == 0 ? "§9§lMÃO AZUL" : "§c§lMÃO VERMELHA", "§7Clique mirando para disparar", 5, 50, 10);
        if (mao == 0 && !feito.contains("luva")) {
            feito.add("luva");
            checkpoint = Math.max(checkpoint, 1);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                pt.portaLuva(false);
                somTodos(Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.5f);
                somTodos(Sound.BLOCK_ANVIL_LAND, 0.6f, 0.6f);
                todos("§c§lA porta bateu! §7Tem um botão lá em cima, na parede... longe demais para a mão. §fUse a luva.");
                objetivo("Atire a Mão Azul no botão alto da parede");
            }, 30L);
        }
        if (mao == 1 && !feito.contains("vermelha")) {
            feito.add("vermelha");
            checkpoint = Math.max(checkpoint, 3);
            todos("§c" + p.getName() + " §7pegou a §cMão Vermelha§7. Todos podem pegar a sua no pedestal.");
            objetivo("Sala de controle: ligue os dois plugues (azul com azul, vermelho com vermelho)");
        }
    }

    /** Clique com a luva na mao (qualquer botao). */
    void disparou(Player p, int mao) {
        Estado e = estado(p);
        if (e == null || e.pego || acabou) return;
        long agora = System.currentTimeMillis();
        if (agora - e.ultimo[mao] < 450) return;
        e.ultimo[mao] = agora;
        Location[] ponto = new Location[1];
        Planta.Alvo a = Luva.mirar(pt, p, ponto);
        final String segurando = e.mao[mao];
        Luva.disparar(pl, p, mao, ponto[0], () -> acertou(p, mao, a, segurando));
    }

    private void acertou(Player p, int mao, Planta.Alvo a, String segurava) {
        Estado e = estado(p);
        if (e == null || acabou) return;
        if (a == null) {
            if (segurava != null) p.sendActionBar(Fabrica.txt("§7Errou o alvo. §8(para devolver, atire no lugar de onde tirou)"));
            return;
        }
        switch (a.tipo()) {
            case BOTAO -> {
                if (a.id().equals("botao_luva") && !feito.contains("saida_luva")) {
                    feito.add("saida_luva");
                    pt.lampada(-22, Planta.Y + 6, 15, true);
                    somTodos(Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1f);
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        pt.portaLuva(true);
                        somTodos(Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.7f);
                        todos("§aBoa! §7A porta abriu. Agora... aquele portão do saguão precisa de energia.");
                        objetivo("Leve o plugue (parede leste do saguão) até a tomada do portão");
                    }, 10L);
                }
            }
            case PLUGUE -> {
                if (a.id().equals(segurava)) {
                    soltar(p, mao, true);
                    return;
                }
                if (segurava != null) {
                    p.sendActionBar(Fabrica.txt("§7Essa mão já está segurando algo."));
                    return;
                }
                if (tomadasLigadas.contains(a.id()) || plugueCom.containsKey(a.id())) {
                    p.sendActionBar(Fabrica.txt("§7Esse plugue já está em uso."));
                    return;
                }
                if (a.cor() != null && !a.cor().equals(Luva.NOME[mao])) {
                    p.sendActionBar(Fabrica.txt("§7Esse plugue só encaixa na mão §f" + a.cor() + "§7."));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.6f);
                    return;
                }
                e.mao[mao] = a.id();
                plugueCom.put(a.id(), p.getUniqueId());
                plugueMao.put(a.id(), mao);
                p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_HIT, 1f, 0.8f);
                p.sendActionBar(Fabrica.txt("§eSegurando o plugue. §7Atire na tomada certa (cabo de até " + (int) CABO_MAX + " blocos)."));
            }
            case TOMADA -> {
                if (segurava == null || !segurava.equals(a.cor())) {
                    p.sendActionBar(Fabrica.txt(segurava == null ? "§7Precisa trazer o plugue até aqui." : "§7Esse plugue não é desta tomada."));
                    return;
                }
                ligarTomada(p, mao, a);
            }
            case CAIXA -> {
                if (("caixa_" + a.cor()).equals(segurava)) {
                    soltar(p, mao, true);
                    return;
                }
                if (segurava != null) {
                    p.sendActionBar(Fabrica.txt("§7Essa mão já está segurando algo."));
                    return;
                }
                if (!feito.contains("energia")) return;
                String cor = a.cor();
                if (entregues.contains(cor) || caixaCom.containsKey(cor)) return;
                caixaCom.put(cor, p.getUniqueId());
                e.mao[mao] = "caixa_" + cor;
                pt.caixaNaPrateleira(cor, false);
                e.carregando = carregarBloco(p, Material.valueOf(Planta.cor(cor).toUpperCase() + "_SHULKER_BOX"));
                p.playSound(p.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 1f, 0.8f);
                p.sendActionBar(Fabrica.txt("§ePegou a caixa " + cor + ". §7Atire na prensa da mesma cor."));
            }
            case RECEPTOR -> {
                if (segurava == null || !segurava.startsWith("caixa_")) {
                    p.sendActionBar(Fabrica.txt("§7A prensa precisa de uma caixa de brinquedo."));
                    return;
                }
                String cor = segurava.substring(6);
                if (!cor.equals(a.cor())) {
                    p.sendActionBar(Fabrica.txt("§cNão encaixa! §7Essa prensa é da cor " + a.cor() + "."));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.6f);
                    return;
                }
                e.mao[mao] = null;
                caixaCom.remove(cor);
                if (e.carregando != null) e.carregando.remove();
                e.carregando = null;
                entregues.add(cor);
                pt.caixaNaPrensa(cor, true);
                somTodos(Sound.BLOCK_PISTON_EXTEND, 1f, 0.6f);
                somTodos(Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                todos("§aCaixa " + cor + " na prensa! §7(" + entregues.size() + "/3)");
                if (entregues.size() == 2) espiarTutu();
                if (entregues.size() == 3) {
                    feito.add("caixas");
                    Bukkit.getScheduler().runTaskLater(pl, () -> {
                        pt.portaVestiario(true);
                        somTodos(Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.7f);
                        todos("§aA linha voltou a funcionar. §7A porta do §fvestiário §7(leste) abriu.");
                        objetivo("Vestiário: pegue a Mão Vermelha e ouça a fita 2");
                    }, 20L);
                }
            }
            case PUXADOR -> puxar(p);
            default -> {
            }
        }
    }

    private void ligarTomada(Player p, int mao, Planta.Alvo tomada) {
        Estado e = estado(p);
        String plugue = tomada.cor();
        e.mao[mao] = null;
        plugueCom.remove(plugue);
        plugueMao.remove(plugue);
        tomadasLigadas.add(plugue);
        ItemDisplay d = plugues.get(plugue);
        if (d != null && d.isValid()) d.teleport(pt.centro(tomada).add(0, 0, -0.45));
        pt.lampada(tomada.x(), tomada.y() + 1, tomada.z(), true);
        somTodos(Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        somTodos(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 1.4f);
        switch (plugue) {
            case "plugue_saguao" -> energiaSaguao();
            case "plugue_azul", "plugue_vermelho" -> {
                todos("§a⚡ Tomada " + (plugue.equals("plugue_azul") ? "§9azul" : "§cvermelha") + " §aligada! §7(" + contarControle() + "/2)");
                if (contarControle() == 2) estenderPonte();
            }
            default -> {
            }
        }
    }

    private int contarControle() {
        return (tomadasLigadas.contains("plugue_azul") ? 1 : 0) + (tomadasLigadas.contains("plugue_vermelho") ? 1 : 0);
    }

    void soltar(Player p, int mao, boolean avisar) {
        Estado e = estado(p);
        if (e == null || e.mao[mao] == null) return;
        String s = e.mao[mao];
        e.mao[mao] = null;
        if (s.startsWith("caixa_")) {
            String cor = s.substring(6);
            caixaCom.remove(cor);
            pt.caixaNaPrateleira(cor, true);
            if (e.carregando != null) e.carregando.remove();
            e.carregando = null;
        } else {
            plugueCom.remove(s);
            plugueMao.remove(s);
        }
        if (avisar) {
            p.sendActionBar(Fabrica.txt("§7Você soltou. " + (s.startsWith("caixa_") ? "A caixa voltou para a prateleira." : "O plugue voltou para o lugar.")));
            p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.8f, 1f);
        }
    }

    private BlockDisplay carregarBloco(Player p, Material m) {
        BlockDisplay d = p.getWorld().spawn(p.getLocation().add(0, 1, 0), BlockDisplay.class, x -> {
            x.setBlock(m.createBlockData());
            x.setPersistent(false);
            x.setTeleportDuration(2);
            x.setTransformation(new Transformation(new Vector3f(-0.25f, 0, -0.25f), new AxisAngle4f(), new Vector3f(0.5f), new AxisAngle4f()));
        });
        entidades.add(d);
        return d;
    }

    // ------------------------------------------------------------------ eventos do roteiro

    private void energiaSaguao() {
        feito.add("energia");
        objetivo("A energia voltou...");
        for (int i = 0; i < 8; i++) {
            final boolean acesa = i % 2 == 0;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                pt.luzSaguao(acesa);
                somTodos(Sound.BLOCK_LEVER_CLICK, 0.6f, acesa ? 1.2f : 0.7f);
            }, 4L * i);
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            pt.luzSaguao(false);
            somTodos(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
            for (Player p : online()) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
        }, 34L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (tutu != null && tutu.isValid()) tutu.teleport(pt.loc(0.5, Planta.Y - 30, 32.5, 0, 0));
            pt.luzSaguao(true);
            somTodos(Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.6f);
            somTodos(Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
            titulo("", "§7...o pedestal está §cvazio§7.", 50);
        }, 60L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            pt.portao(true);
            somTodos(Sound.BLOCK_PISTON_CONTRACT, 1f, 0.5f);
            somTodos(Sound.ENTITY_IRON_GOLEM_STEP, 1f, 0.4f);
            checkpoint = Math.max(checkpoint, 2);
            todos("§7O portão da §flinha de produção §7abriu. Onde foi parar o Tutu?");
            objetivo("Linha de produção: leve as 3 caixas (alto das prateleiras) às prensas da mesma cor");
        }, 90L);
    }

    private void espiarTutu() {
        if (tutu == null || !tutu.isValid()) return;
        escala(tutu, 2.3);
        tutu.teleport(pt.loc(Planta.TUTU_ESPIA));
        Bukkit.getScheduler().runTaskLater(pl, () -> somTodos(Sound.ENTITY_WARDEN_AMBIENT, 0.7f, 1.6f), 5L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (tutu != null && tutu.isValid() && !cacando) tutu.teleport(pt.loc(0.5, Planta.Y - 30, 32.5, 0, 0));
        }, 40L);
    }

    private void estenderPonte() {
        feito.add("ponte");
        todos("§aAs duas tomadas ligadas! §7A ponte da galeria está estendendo...");
        int n = Planta.tamanhoPonte();
        for (int i = 1; i <= n; i++) {
            final int c = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                pt.ponte(c);
                somTodos(Sound.BLOCK_PISTON_EXTEND, 0.5f, 0.8f + c * 0.02f);
                if (c == n) {
                    checkpoint = Math.max(checkpoint, 4);
                    objetivo("Atravesse a ponte (porta oeste da sala de controle)");
                    somTodos(Sound.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
                }
            }, 3L * i);
        }
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        if (acabou) return;
        ticks++;
        List<Player> ps = online();
        if (ps.isEmpty()) {
            pl.acabou(this, false);
            return;
        }
        for (Player p : ps) {
            Estado e = estado(p);
            if (e == null) continue;
            if (ticks % 10 == 0) {
                p.setFoodLevel(20);
                p.setSaturation(20);
            }
            // caiu no vao
            if (p.getLocation().getY() < Planta.Y - 5 && !finalFeito && ticks - e.caiuEm > 20) {
                e.caiuEm = ticks;
                for (int m = 0; m < 2; m++) soltar(p, m, false);
                p.teleport(pt.loc(Planta.CHECKPOINT[checkpoint]));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
                p.sendActionBar(Fabrica.txt("§7Você caiu... de volta ao último ponto."));
            }
            // carregando: plugue com cabo, caixa flutuando
            for (int m = 0; m < 2; m++) {
                String s = e.mao[m];
                if (s == null) continue;
                Location mao = Luva.maoDe(p, m);
                if (s.startsWith("caixa_")) {
                    if (e.carregando != null && e.carregando.isValid() && ticks % 2 == 0) e.carregando.teleport(mao.clone().add(0, -0.1, 0));
                } else {
                    ItemDisplay d = plugues.get(s);
                    Planta.Alvo origem = Planta.alvo(s);
                    if (d != null && d.isValid() && ticks % 2 == 0) {
                        Location l = mao.clone();
                        l.setYaw(p.getLocation().getYaw() + 180);
                        d.teleport(l);
                    }
                    if (origem != null) {
                        Location o = pt.centro(origem);
                        if (o.distance(p.getLocation()) > CABO_MAX) {
                            soltar(p, m, false);
                            voltarPlugue(s);
                            p.sendActionBar(Fabrica.txt("§cO cabo esticou demais e soltou!"));
                            p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.6f);
                        } else if (ticks % 4 == 0) Luva.cabo(p, o, mao, org.bukkit.Color.fromRGB(30, 30, 30), 0.8);
                    }
                }
            }
        }
        // plugues soltos voltam
        if (ticks % 5 == 0) for (String id : plugues.keySet()) if (!plugueCom.containsKey(id) && !tomadasLigadas.contains(id)) voltarPlugue(id);
        perseguicao(ps);
        if (ticks % 20 == 0 && barra != null) {
            barra.setProgress(Math.min(1, checkpoint / 5.0 + (feito.contains("ponte") ? 0.1 : 0)));
        }
    }

    private void voltarPlugue(String id) {
        ItemDisplay d = plugues.get(id);
        Planta.Alvo a = Planta.alvo(id);
        if (d == null || a == null || !d.isValid()) return;
        Location casa = casaPlugue(id);
        if (d.getLocation().distanceSquared(casa) > 0.04) d.teleport(casa);
    }

    // ------------------------------------------------------------------ perseguicao

    private Location pontoNoCaminho(double dist) {
        double[][] c = Planta.CAMINHO;
        int fim = Planta.DUTO_FIM;
        if (dist <= 0) return pt.loc(c[0][0], Planta.Y + 1, c[0][1], 0, 0);
        for (int i = 1; i <= fim; i++) {
            if (dist <= acumulado[i]) {
                double f = (dist - acumulado[i - 1]) / (acumulado[i] - acumulado[i - 1]);
                double x = c[i - 1][0] + (c[i][0] - c[i - 1][0]) * f, z = c[i - 1][1] + (c[i][1] - c[i - 1][1]) * f;
                float yaw = (float) Math.toDegrees(Math.atan2(-(c[i][0] - c[i - 1][0]), c[i][1] - c[i - 1][1]));
                return pt.loc(x, Planta.Y + 1, z, yaw, 0);
            }
        }
        return pt.loc(c[fim][0], Planta.Y + 1, c[fim][1], 90, 0);
    }

    private boolean naSalaGigante(Location l) {
        double x = l.getX() - pt.ox, z = l.getZ();
        return x < -10.3 && x > -23 && z > 75 && z < 91 && l.getY() > Planta.Y;
    }

    private boolean naSalaFinal(Location l) {
        double x = l.getX() - pt.ox;
        return x < -64.3 && l.getZ() > 74 && l.getZ() < 94 && l.getY() > Planta.Y - 2;
    }

    private void perseguicao(List<Player> ps) {
        if (!feito.contains("ponte") || finalFeito) return;
        if (!cacando) {
            for (Player p : ps) {
                Estado e = estado(p);
                if (e != null && !e.pego && naSalaGigante(p.getLocation())) {
                    comecarCacada();
                    break;
                }
            }
            return;
        }
        if (cacadaEspera > 0) {
            cacadaEspera--;
            if (cacadaEspera == 0) somTodos(Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);
            return;
        }
        if (tutu == null || !tutu.isValid()) return;
        // anda
        if (!tutuNaSalaFinal && !tutuNoDuto) {
            // sala dos brinquedos gigantes: vai atras de quem esta na sala; sem ninguem, entra no duto
            Player alvo = maisPerto(ps, this::naSalaGigante);
            Location t = tutu.getLocation();
            org.bukkit.util.Vector v;
            double vel;
            if (alvo != null) {
                v = alvo.getLocation().toVector().subtract(t.toVector()).setY(0);
                vel = TUTU_VEL_SALA + 0.04;
            } else {
                Location boca = pt.loc(Planta.CAMINHO[Planta.DUTO_INICIO][0], Planta.Y + 1, Planta.CAMINHO[Planta.DUTO_INICIO][1], 90, 0);
                v = boca.toVector().subtract(t.toVector()).setY(0);
                vel = 0.26;
                if (v.lengthSquared() < 0.36) {
                    tutuNoDuto = true;
                    tutuDist = acumulado[Planta.DUTO_INICIO];
                    v = new org.bukkit.util.Vector();
                }
            }
            if (v.lengthSquared() > 0.0001) {
                double len = v.length();
                v.normalize().multiply(Math.min(vel, len));
                Location n = t.clone().add(v);
                double rx = n.getX() - pt.ox;
                n.setX(pt.ox + Math.max(-22.6, Math.min(-10.5, rx)));
                n.setZ(Math.max(75.5, Math.min(90.5, n.getZ())));
                n.setY(Planta.Y + 1);
                n.setYaw((float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())));
                escala(tutu, 2.2);
                tutu.teleport(n);
                if (ticks % 7 == 0) tutu.getWorld().playSound(n, Sound.ENTITY_IRON_GOLEM_STEP, 1f, 0.6f);
            }
            if (ticks % 50 == 0) tutu.getWorld().playSound(t, Sound.ENTITY_WARDEN_ANGRY, 1f, 1.4f);
        } else if (!tutuNaSalaFinal) {
            double fimDuto = acumulado[Planta.DUTO_FIM];
            tutuDist += TUTU_VEL;
            escala(tutu, 0.95);
            Location l = pontoNoCaminho(tutuDist);
            tutu.teleport(l);
            if (ticks % 6 == 0) tutu.getWorld().playSound(l, Sound.ENTITY_IRON_GOLEM_STEP, 1f, 1.3f);
            if (ticks % 50 == 0) tutu.getWorld().playSound(l, Sound.ENTITY_WARDEN_ANGRY, 1f, 1.4f);
            if (tutuDist >= fimDuto) {
                tutuNaSalaFinal = true;
                escala(tutu, 2.4);
                pt.puxador(true);
                somTodos(Sound.ENTITY_RAVAGER_ROAR, 1f, 0.5f);
                for (Player p : ps) if (naSalaFinal(p.getLocation())) p.sendTitle("§c§lO PUXADOR!", "§7Atire a luva no puxador da parede oeste", 0, 40, 10);
            }
        } else {
            // na sala final: vai atras do jogador mais perto que esta na sala
            Player alvo = maisPerto(ps, this::naSalaFinal);
            if (alvo != null) {
                Location t = tutu.getLocation();
                org.bukkit.util.Vector v = alvo.getLocation().toVector().subtract(t.toVector()).setY(0);
                if (v.lengthSquared() > 0.01) {
                    v.normalize().multiply(TUTU_VEL_SALA);
                    Location n = t.clone().add(v);
                    double rx = n.getX() - pt.ox;
                    n.setX(pt.ox + Math.max(-78.5, Math.min(-65.5, rx)));
                    n.setZ(Math.max(75.5, Math.min(92.5, n.getZ())));
                    n.setY(Planta.Y + 1);
                    n.setYaw((float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())));
                    tutu.teleport(n);
                    if (ticks % 7 == 0) tutu.getWorld().playSound(n, Sound.ENTITY_IRON_GOLEM_STEP, 1f, 0.5f);
                }
            }
        }
        // pegou alguem?
        Location tl = tutu.getLocation();
        double raio = tutuNaSalaFinal ? 1.9 : 1.25;
        for (Player p : ps) {
            Estado e = estado(p);
            if (e == null || e.pego) continue;
            if (!p.getWorld().equals(tl.getWorld())) continue;
            Location pl0 = p.getLocation();
            double dx = pl0.getX() - tl.getX(), dz = pl0.getZ() - tl.getZ(), dy = Math.abs(pl0.getY() - tl.getY());
            if (dx * dx + dz * dz < raio * raio && dy < 2.5) pegou(p);
        }
        // pegou todo mundo: recomeca
        boolean algum = false;
        for (Player p : ps) {
            Estado e = estado(p);
            if (e != null && !e.pego) algum = true;
        }
        if (!algum) reiniciarCacada();
    }

    private Player maisPerto(List<Player> ps, java.util.function.Predicate<Location> onde) {
        Player alvo = null;
        double md = Double.MAX_VALUE;
        for (Player p : ps) {
            Estado e = estado(p);
            if (e == null || e.pego || !onde.test(p.getLocation())) continue;
            double d = p.getLocation().distanceSquared(tutu.getLocation());
            if (d < md) {
                md = d;
                alvo = p;
            }
        }
        return alvo;
    }

    private void comecarCacada() {
        cacando = true;
        tutuNoDuto = false;
        tutuDist = 0;
        cacadaEspera = 50;
        escala(tutu, 2.2);
        tutu.teleport(pt.loc(Planta.TUTU_CACADA));
        objetivo("CORRA PARA OS DUTOS! (parede oeste)");
        titulo("§4§lCORRA!", "§7Para os dutos, na parede oeste", 40);
        somTodos(Sound.ENTITY_WARDEN_ROAR, 1f, 1.2f);
        somTodos(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
    }

    private void reiniciarCacada() {
        cacando = false;
        tutuNoDuto = false;
        tutuNaSalaFinal = false;
        tutuDist = 0;
        pt.puxador(false);
        if (tutu != null && tutu.isValid()) {
            escala(tutu, 2.2);
            tutu.teleport(pt.loc(0.5, Planta.Y - 30, 32.5, 0, 0));
        }
        objetivo("Tente de novo: atravesse a sala e corra pelos dutos");
    }

    private void pegou(Player p) {
        Estado e = estado(p);
        e.pego = true;
        for (int m = 0; m < 2; m++) soltar(p, m, false);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1f, 1.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1f, 0.6f);
        p.sendTitle("§4§lTUTU", "§cte deu um abraço...", 0, 40, 10);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 6, false, false, false));
        todos("§c" + p.getName() + " §7foi pego pelo Tutu!");
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || acabou) return;
            p.teleport(pt.loc(Planta.CHECKPOINT[4]));
            e.pego = false;
            e.salvo = false;
        }, 40L);
    }

    // ------------------------------------------------------------------ final

    private void puxar(Player p) {
        if (finalFeito) return;
        if (!tutuNaSalaFinal || tutu == null || !tutu.isValid()) {
            p.sendActionBar(Fabrica.txt("§7O puxador está travado... §8(a luz está apagada)"));
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 0.6f);
            return;
        }
        finalFeito = true;
        pt.por(-79, Planta.Y + 8, 84, "lever[face=wall,facing=east,powered=true]");
        somTodos(Sound.BLOCK_LEVER_CLICK, 1f, 0.6f);
        somTodos(Sound.BLOCK_CHAIN_BREAK, 1f, 0.5f);
        Location t = tutu.getLocation();
        int cx = t.getBlockX() - pt.ox, cz = t.getBlockZ();
        // caixote gigante caindo do teto
        BlockDisplay caixote = t.getWorld().spawn(pt.loc(cx - 1, Planta.Y + 10, cz - 1, 0, 0), BlockDisplay.class, x -> {
            x.setBlock(Material.SPRUCE_PLANKS.createBlockData());
            x.setPersistent(false);
            x.setTeleportDuration(1);
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(3f), new AxisAngle4f()));
        });
        entidades.add(caixote);
        for (int i = 1; i <= 8; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> caixote.teleport(pt.loc(cx - 1, Planta.Y + 10 - k * 1.1, cz - 1, 0, 0)), i);
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            somTodos(Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
            somTodos(Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
            somTodos(Sound.ENTITY_WARDEN_DEATH, 1f, 1.3f);
            pt.buraco(cx, cz, 2);
            for (Player j : online()) j.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
        }, 9L);
        for (int i = 1; i <= 14; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (tutu != null && tutu.isValid()) tutu.teleport(t.clone().add(0, -0.8 * k, 0));
                caixote.teleport(pt.loc(cx - 1, Planta.Y + 1.2 - 0.8 * k, cz - 1, 0, 0));
            }, 9L + i);
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (tutu != null) tutu.remove();
            caixote.remove();
            somTodos(Sound.BLOCK_WOOD_BREAK, 1f, 0.5f);
        }, 26L);
        Bukkit.getScheduler().runTaskLater(pl, this::epilogo, 60L);
    }

    private void epilogo() {
        objetivo("Fim do Capítulo 1");
        for (Player p : online()) {
            Estado e = estado(p);
            if (e != null) e.pego = false;
            if (!naSalaFinal(p.getLocation())) p.teleport(pt.loc(-76.5, Planta.Y + 1, 78.5, -45, 10));
        }
        List<String> falas = List.of("§8§o*uma fita cai do caixote quebrado e começa a tocar sozinha*",
                "§6[Seu Arlindo] §fDeu certo demais. Eles lembram de tudo... de cada criança que abraçaram.",
                "§6[Seu Arlindo] §f...e de quem trancou eles aqui.",
                "§6[Seu Arlindo] §fSe você chegou até aqui, não desça. A §dDona Bolota §fainda está lá embaixo,",
                "§6[Seu Arlindo] §fdando corda em si mesma. §7*tic... tic... tic...*");
        for (int i = 0; i < falas.size(); i++) {
            final String f = falas.get(i);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!acabou) todos(f);
            }, 10L + i * 60L);
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (acabou) return;
            bolota = boneco("bolota", pt.loc(Planta.BOLOTA_FIM), 0.7);
            int[] notas = {7, 4, 0, 4, 7, 12, 11, 7};
            for (int i = 0; i < notas.length; i++) {
                final float tom = (float) Math.pow(2, (notas[i] - 12) / 12.0);
                Bukkit.getScheduler().runTaskLater(pl, () -> somTodos(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, tom), i * 8L);
            }
        }, 300L);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (acabou) return;
            titulo("§6§lFIM DO CAPÍTULO 1", "§7continua...", 80);
            somTodos(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }, 380L);
        Bukkit.getScheduler().runTaskLater(pl, () -> pl.acabou(this, true), 480L);
    }

    // ------------------------------------------------------------------ saida

    /** O jogador saiu (desistiu, caiu do servidor): solta o que segurava. */
    void saiu(Player p) {
        Estado e = estado(p);
        if (e != null) for (int m = 0; m < 2; m++) soltar(p, m, false);
        jogadores.remove(p.getUniqueId());
        if (barra != null) barra.removePlayer(p);
    }

    void limpar() {
        acabou = true;
        if (tarefa != null) tarefa.cancel();
        if (barra != null) barra.removeAll();
        for (Entity e : entidades) if (e != null && e.isValid()) e.remove();
        entidades.clear();
        for (Estado e : estados.values()) if (e.carregando != null) e.carregando.remove();
    }

    // ------------------------------------------------------------------ admin: pular partes (teste)

    void pularPara(Player p, String parte) {
        java.util.List<String> ordem = List.of("portaria", "luva", "energia", "caixas", "controle", "ponte", "final");
        int ate = ordem.indexOf(parte);
        if (ate < 0) {
            p.sendMessage(Fabrica.txt("§cPartes: " + String.join(", ", ordem)));
            return;
        }
        Estado e = estado(p);
        if (ate >= 0) {
            feito.add("portaria");
            pt.portaPortaria(true);
        }
        if (ate >= 1) {
            feito.add("luva");
            feito.add("saida_luva");
            if (!e.azul) pegarMao(p, 0);
            pt.portaLuva(true);
            checkpoint = Math.max(checkpoint, 1);
        }
        if (ate >= 2 && !feito.contains("energia")) {
            tomadasLigadas.add("plugue_saguao");
            feito.add("energia");
            pt.luzSaguao(true);
            pt.portao(true);
            if (tutu != null) tutu.teleport(pt.loc(0.5, Planta.Y - 30, 32.5, 0, 0));
            checkpoint = Math.max(checkpoint, 2);
        }
        if (ate >= 3) {
            for (String c : new String[]{"vermelha", "amarela", "azul"}) {
                entregues.add(c);
                pt.caixaNaPrateleira(c, false);
                pt.caixaNaPrensa(c, true);
            }
            feito.add("caixas");
            pt.portaVestiario(true);
        }
        if (ate >= 4) {
            if (!e.vermelha) pegarMao(p, 1);
            checkpoint = Math.max(checkpoint, 3);
        }
        if (ate >= 5) {
            tomadasLigadas.add("plugue_azul");
            tomadasLigadas.add("plugue_vermelho");
            feito.add("ponte");
            pt.ponte(Planta.tamanhoPonte());
            checkpoint = Math.max(checkpoint, 4);
        }
        if (ate >= 6) {
            cacando = true;
            cacadaEspera = 0;
            tutuNoDuto = true;
            tutuDist = acumulado[Planta.DUTO_FIM] - 0.3;
            p.teleport(pt.loc(-72.5, Planta.Y + 1, 80.5, 0, 0));
        } else {
            double[][] onde = {Planta.CHECKPOINT[0], Planta.CHECKPOINT[1], Planta.CHECKPOINT[2], {24.5, Planta.Y + 1, 65.5, 90, 0}, Planta.CHECKPOINT[3], Planta.CHECKPOINT[4]};
            p.teleport(pt.loc(onde[ate]));
        }
        p.sendMessage(Fabrica.txt("§eAdmin: pulou para " + parte + "."));
    }

    long segundos() {
        return (System.currentTimeMillis() - inicioMs) / 1000;
    }
}
