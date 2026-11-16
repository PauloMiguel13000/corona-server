package br.servidor.tiro;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Missao (15/11, pedido do Paulo: "o Warface era pra ser missoes PvE contra bots que sao pessoas; voce vai seguindo a
 * fase ate o final"). Co-op de 1 a 4 numa base inimiga em LINHA: 5 setores separados por portoes. Em cada setor os
 * soldados inimigos (gente, que ATIRA em voce e usa cobertura) seguram a passagem; limpou, o portao abre e vira ponto
 * de controle (cura, municao e quem caiu volta). No fim, o Juggernaut. 4 classes: fuzileiro, medico, engenheiro e
 * atirador. Inimigos: soldado, escopeteiro (avanca), atirador de elite (mira com laser), escudeiro (so toma tiro pelas
 * costas ou de lado) e o pesado da metralhadora (fraco nas costas).
 */
final class Missao implements Listener {

    static final String PREFIXO = "§8[§c§lMissão§8] §7";
    static final int Y = 64, SETORES = 5, COMP = 40, LARG = 12, MAPA = 2;
    static final String[] NOMES = {"Pátio de Carga", "Armazém", "Rua dos Contêineres", "Laboratório", "Heliporto"};
    static final String[] CLASSES = {"fuzileiro", "medico", "engenheiro", "atirador"};
    static final String[] CLASSE_NOME = {"§aFuzileiro", "§dMédico", "§eEngenheiro", "§bAtirador"};

    enum Fase { ESPERA, CONTAGEM, JOGO, FIM }

    /** O que cada inimigo faz: distancia que gosta, intervalo e dano do tiro, rajada, pontaria. */
    record Perfil(double perto, double longe, int intervalo, double dano, int rajada, double pontaria) {}

    static final Map<Zumbis.Tipo, Perfil> PERFIS = Map.of(
            Zumbis.Tipo.SOLDADO, new Perfil(8, 15, 50, 1.5, 3, 0.45),
            Zumbis.Tipo.ESCOPETA, new Perfil(3, 6, 40, 4.0, 1, 0.7),
            Zumbis.Tipo.ATIRADOR, new Perfil(14, 60, 100, 8.0, 1, 0.85),
            Zumbis.Tipo.ESCUDO, new Perfil(2.5, 5, 26, 2.0, 1, 0.6),
            Zumbis.Tipo.PESADO, new Perfil(6, 16, 80, 1.2, 20, 0.38),
            Zumbis.Tipo.JUGGERNAUT, new Perfil(5, 18, 70, 1.5, 24, 0.42));

    final Tiro pl;
    World mundo;
    private Fase fase = Fase.ESPERA;
    private int contagem, setor, tempo, reforcos;
    private boolean setorAtivo, reforcoVeio;
    private final Set<UUID> noJogo = new HashSet<>(), caidos = new HashSet<>();
    private final Map<UUID, Integer> classe = new HashMap<>(), usos = new HashMap<>();
    private final Map<UUID, Long> proximoTiro = new HashMap<>(), miraAte = new HashMap<>(), ultimoKit = new HashMap<>();
    private final Map<UUID, Integer> rajada = new HashMap<>();
    /** Quando cada jogador levou o ultimo tiro (a vida volta sozinha depois de 5 s em cobertura) e quando alguem
     *  comecou a atirar nele (os soldados se revezam: nao abrem fogo todos no mesmo instante). */
    private final Map<UUID, Long> feridoEm = new HashMap<>(), fogoEm = new HashMap<>();
    private final Random rnd = new Random();
    private final BossBar barra = BossBar.bossBar(Tiro.txt("§c§lMISSÃO"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private int tick;
    /** true enquanto o dano que esta saindo e de um TIRO de soldado (o soco deles e cancelado). */
    private boolean bala;

    Missao(Tiro pl) {
        this.pl = pl;
        mundo = new WorldCreator(Tiro.MISSAO).generator(new Tiro.Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        Tiro.regrasDeLobby(mundo);
        mundo.setDifficulty(org.bukkit.Difficulty.NORMAL);
        mundo.setTime(13500);
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= (SETORES * COMP + 30) / 16; cz++) mundo.addPluginChunkTicket(cx, cz, pl);
        if (pl.getConfig().getInt("missao-mapa", 0) != MAPA) {
            construir();
            pl.getConfig().set("missao-mapa", MAPA);
            pl.saveConfig();
        }
        limpar();
        Bukkit.getScheduler().runTaskTimer(pl, this::segundo, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(pl, this::ia, 5L, 4L);
    }

    void desligar() {
        if (mundo == null) return;
        limpar();
        for (Player p : new ArrayList<>(mundo.getPlayers())) tirar(p, false);
    }

    // ================================================================== mapa

    private void b(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    private void caixa(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, m);
    }

    private int larg(int s) {
        return s == SETORES - 1 ? 18 : LARG;
    }

    private int z0(int s) {
        return s * COMP + 2;
    }

    private int z1(int s) {
        return (s + 1) * COMP - 1;
    }

    Location sala() {
        return new Location(mundo, 0.5, Y + 1, -30.5, 0f, 0f);
    }

    private Location partida(int s) {
        return new Location(mundo, 0.5 + rnd.nextInt(5) - 2, Y + 1, (s <= 0 ? -6 : z0(s) + 1) + 0.5, 0f, 0f);
    }

    private void portao(int s, boolean aberto) {
        int z = s * COMP;                                                        // portao de ENTRADA do setor s
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) for (int dz = 0; dz <= 1; dz++) b(x, Y + y, z + dz, aberto ? Material.AIR : Material.IRON_BARS);
    }

    private void construir() {
        Random r = new Random(15);
        // sala de espera
        caixa(-6, Y - 1, -36, 6, Y + 8, -24, Material.AIR);
        caixa(-6, Y, -36, 6, Y, -24, Material.POLISHED_DEEPSLATE);
        for (int x = -6; x <= 6; x++) for (int z = -36; z <= -24; z++) if (Math.abs(x) == 6 || z == -36 || z == -24) caixa(x, Y + 1, z, x, Y + 5, z, Material.DEEPSLATE_BRICKS);
        caixa(-6, Y + 6, -36, 6, Y + 6, -24, Material.DEEPSLATE_TILES);
        for (int[] c : new int[][] {{-4, -34}, {4, -34}, {-4, -26}, {4, -26}, {0, -30}}) b(c[0], Y + 6, c[1], Material.SEA_LANTERN);
        // corredor inteiro: chao, muros e os portoes
        int fim = SETORES * COMP + 4;
        for (int z = -14; z <= fim; z++) {
            int s = Math.max(0, Math.min(SETORES - 1, Math.floorDiv(z, COMP))), L = z < 0 ? 6 : larg(s);
            for (int x = -L - 1; x <= L + 1; x++) {
                for (int y = Y + 1; y <= Y + 14; y++) b(x, y, z, Material.AIR);
                b(x, Y - 1, z, Material.DEEPSLATE);
                boolean muro = Math.abs(x) == L + 1 || z == -14 || z == fim;
                Material chao = switch (s) {
                    case 0 -> r.nextInt(7) == 0 ? Material.GRAVEL : Material.GRAY_CONCRETE;
                    case 1 -> (x + z) % 4 == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
                    case 2 -> r.nextInt(9) == 0 ? Material.COARSE_DIRT : Material.GRAY_CONCRETE_POWDER;
                    case 3 -> (x % 3 == 0 || z % 3 == 0) ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE;
                    default -> Math.hypot(x, z - (z0(s) + 20)) < 7 ? (Math.hypot(x, z - (z0(s) + 20)) > 5.6 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE) : Material.GRAY_CONCRETE;
                };
                b(x, Y, z, chao);
                if (muro) for (int y = 1; y <= 9; y++) b(x, Y + y, z, y == 9 ? Material.IRON_BARS : (y + z + x) % 7 == 0 ? Material.CRACKED_DEEPSLATE_BRICKS : Material.DEEPSLATE_BRICKS);
            }
            // parede com portao no comeco de cada setor
            if (z >= 0 && Math.floorMod(z, COMP) <= 1 && z < SETORES * COMP) {
                for (int x = -L; x <= L; x++) for (int y = 1; y <= 8; y++) b(x, Y + y, z, y == 5 && Math.abs(x) <= 3 ? Material.RED_CONCRETE : Material.POLISHED_DEEPSLATE);
            }
        }
        for (int s = 0; s < SETORES; s++) portao(s, false);
        // cobertura e cara de cada setor
        for (int s = 0; s < SETORES; s++) {
            int L = larg(s), za = z0(s) + 5, zb = z1(s) - 4;
            if (s == 1 || s == 3) {                                              // setores cobertos, com luz no teto
                caixa(-L, Y + 9, z0(s), L, Y + 9, z1(s), s == 1 ? Material.SPRUCE_PLANKS : Material.SMOOTH_QUARTZ);
                for (int z = za; z <= zb; z += 7) for (int x = -L + 3; x <= L - 3; x += 6) b(x, Y + 9, z, Material.SEA_LANTERN);
            } else {
                for (int z = za; z <= zb; z += 9) for (int sx = -1; sx <= 1; sx += 2) {
                    caixa(sx * L, Y + 1, z, sx * L, Y + 6, z, Material.POLISHED_DEEPSLATE_WALL);
                    b(sx * (L - 1), Y + 6, z, Material.SEA_LANTERN);
                }
            }
            for (int k = 0; k < (s == SETORES - 1 ? 16 : 13); k++) {
                int x = r.nextInt(2 * L - 3) - L + 2, z = za + r.nextInt(Math.max(1, zb - za));
                switch (r.nextInt(5)) {
                    case 0 -> {                                                  // caixotes
                        caixa(x, Y + 1, z, x + 1, Y + 1, z + 1, Material.BARREL);
                        b(x, Y + 2, z, r.nextBoolean() ? Material.BARREL : Material.SPRUCE_PLANKS);
                    }
                    case 1 -> {                                                  // mureta
                        if (Math.abs(x) < 2) x += 3;
                        caixa(x - 2, Y + 1, z, x + 2, Y + 1, z, Material.STONE_BRICKS);
                        b(x - 2, Y + 2, z, Material.STONE_BRICK_SLAB);
                        b(x + 1, Y + 2, z, Material.STONE_BRICK_SLAB);
                    }
                    case 2 -> {                                                  // conteiner
                        if (Math.abs(x) < 4) x = x < 0 ? -L + 2 : L - 4;
                        Material c = new Material[] {Material.RED_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.ORANGE_TERRACOTTA}[r.nextInt(4)];
                        caixa(x, Y + 1, z, x + 2, Y + 3, Math.min(zb, z + 5), c);
                    }
                    case 3 -> {                                                  // sacos de areia
                        caixa(x - 1, Y + 1, z, x + 1, Y + 1, z, Material.MUD_BRICKS);
                        b(x, Y + 2, z, Material.MUD_BRICK_SLAB);
                    }
                    default -> caixa(x, Y + 1, z, x, Y + 2, z, s == 3 ? Material.IRON_BLOCK : Material.HAY_BLOCK);
                }
            }
            // corredor do meio livre junto do portao e duas torres de vigia no fundo (para os atiradores de elite)
            caixa(-1, Y + 1, z0(s), 1, Y + 3, z0(s) + 3, Material.AIR);
            if (s >= 2) for (int sx = -1; sx <= 1; sx += 2) {
                int tx = sx * (L - 2), tz = z1(s) - 5;
                caixa(tx - 1, Y + 1, tz - 1, tx + 1, Y + 4, tz + 1, Material.AIR);
                caixa(tx - 1, Y + 4, tz - 1, tx + 1, Y + 4, tz + 1, Material.SPRUCE_PLANKS);
                for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) caixa(tx + c[0], Y + 1, tz + c[1], tx + c[0], Y + 3, tz + c[1], Material.SPRUCE_FENCE);
                caixa(tx - 1, Y + 5, tz - 1, tx + 1, Y + 5, tz - 1, Material.MUD_BRICK_SLAB);
            }
        }
        // luz invisivel pela base inteira (15/11: no teste estava escuro demais para ver os soldados)
        org.bukkit.block.data.type.Light luz = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(14);
        for (int z = -12; z <= fim - 2; z += 4) {
            int s = Math.max(0, Math.min(SETORES - 1, Math.floorDiv(z, COMP))), L = z < 0 ? 6 : larg(s);
            for (int x = -L + 1; x <= L - 1; x += 4)
                for (int y = Y + 5; y >= Y + 2; y--) {
                    var bl = mundo.getBlockAt(x, y, z);
                    if (bl.getType().isAir()) {
                        bl.setBlockData(luz, false);
                        break;
                    }
                }
        }
        for (int x = -4; x <= 4; x += 4) for (int z = -34; z <= -26; z += 4) mundo.getBlockAt(x, Y + 3, z).setBlockData(luz, false);
        pl.getLogger().info("Missao: base inimiga construida (" + SETORES + " setores).");
    }

    // ================================================================== entrar e sair

    boolean aqui(Player p) {
        return mundo != null && p.getWorld() == mundo;
    }

    private List<Player> time() {
        List<Player> l = new ArrayList<>();
        for (UUID u : noJogo) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && aqui(p)) l.add(p);
        }
        return l;
    }

    private List<Player> vivos() {
        List<Player> l = time();
        l.removeIf(p -> caidos.contains(p.getUniqueId()) || p.isDead());
        return l;
    }

    void entrar(Player p) {
        if (mundo == null) {
            p.sendMessage(PREFIXO + "§cA Missão está indisponível agora.");
            return;
        }
        if (aqui(p)) {
            p.sendMessage(PREFIXO + "§7Você já está na base. §f/missao sair §7para voltar ao lobby.");
            return;
        }
        p.teleport(sala());
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !aqui(p)) return;
            preparar(p);
            p.showTitle(Title.title(Tiro.txt("§c§lMISSÃO"), Tiro.txt("§7Invada a base e chegue ao Heliporto")));
            p.sendMessage(PREFIXO + "§fCo-op de 1 a 4§7 contra soldados inimigos. Avance §e5 setores§7; cada portão é um ponto de controle.");
            p.sendMessage(PREFIXO + "§7Escolha a classe com os itens da mão. §eUse cobertura§7: eles atiram de verdade, e a vida volta depois de 5 s sem levar tiro.");
            if (fase == Fase.JOGO) p.sendMessage(PREFIXO + "§eTem uma missão em andamento: você entra na próxima.");
        }, 10L);
    }

    private void preparar(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.setHealth(Math.min(20, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        p.setFoodLevel(20);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        Material[] ic = {Material.IRON_HOE, Material.RED_DYE, Material.IRON_INGOT, Material.SPYGLASS};
        String[] d = {"§7Fuzil de assalto. Bom em tudo.", "§7Escopeta + kit que cura o time.", "§7Submetralhadora + placas de blindagem para o time.", "§7Rifle de precisão. Fique atrás."};
        // 15/11 (Paulo: "os itens ainda aparecem como enxada"): quem tem o pacote ve a ARMA de cada classe no icone
        String[] armaDaClasse = {"fuzil", "escopeta", "smg", "sniper"};
        boolean desenho = pl.visual != null && pl.visual.tem(p);
        for (int i = 0; i < 4; i++) {
            ItemStack it = pl.armas.item("classe" + i, desenho ? Armas.ARMAS.get(armaDaClasse[i]).item() : ic[i], CLASSE_NOME[i] + " §7(clique)", 1, d[i]);
            if (desenho) {
                it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "armas/" + armaDaClasse[i]));
                var m = it.getItemMeta();
                m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                it.setItemMeta(m);
            }
            p.getInventory().setItem(i + 2, it);
        }
        p.showBossBar(barra);
    }

    void tirar(Player p, boolean avisar) {
        noJogo.remove(p.getUniqueId());
        caidos.remove(p.getUniqueId());
        p.hideBossBar(barra);
        if (pl.musica != null) pl.musica.tocar(p, null);
        if (aqui(p)) {
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            pl.irLobby(p);
        }
        if (avisar) p.sendMessage(PREFIXO + "Você saiu da Missão.");
    }

    boolean comando(CommandSender s, String[] a) {
        String sub = a.length > 0 ? a[0].toLowerCase(java.util.Locale.ROOT) : "";
        if (sub.equals("admin") && s.hasPermission("tiro.admin")) {
            String o = a.length > 1 ? a[1] : "";
            if (o.equals("iniciar") && fase != Fase.JOGO && !mundo.getPlayers().isEmpty()) comecar();
            else if (o.equals("refazer")) {
                construir();
                s.sendMessage(PREFIXO + "base refeita");
            } else if (o.equals("setor") && fase == Fase.JOGO) limparSetor();
            else if (o.equals("ver")) {
                for (Zombie z : inimigos()) for (Player q : vivos())
                    s.sendMessage(pl.zumbis.tipo(z) + " d=" + (int) z.getLocation().distance(q.getLocation()) + " ve=" + z.hasLineOfSight(q) + " z=" + (int) z.getLocation().getZ()
                            + " tiro=" + (proximoTiro.getOrDefault(z.getUniqueId(), 0L) - System.currentTimeMillis()) + " raj=" + rajada.get(z.getUniqueId()) + " tick=" + tick + " ai=" + z.hasAI());
            }
            else s.sendMessage(PREFIXO + "fase=" + fase + " setor=" + setor + " inimigos=" + inimigos().size() + " time=" + noJogo.size() + " | admin <iniciar|refazer|setor>");
            return true;
        }
        if (!(s instanceof Player p)) return true;
        if (sub.equals("sair")) {
            if (aqui(p)) tirar(p, true);
            else p.sendMessage(PREFIXO + "Você não está na Missão.");
        } else entrar(p);
        return true;
    }

    // ================================================================== partida

    private void equipar(Player p) {
        int c = classe.getOrDefault(p.getUniqueId(), 0);
        var inv = p.getInventory();
        inv.clear();
        String[] arma = {"fuzil", "escopeta", "smg", "sniper"};
        inv.setItem(0, pl.armas.arma(arma[c]));
        inv.setItem(1, pl.armas.arma("pistola"));
        municao(p);
        if (c == 1) inv.setItem(2, pl.armas.item("kitmed", Material.RED_DYE, "§dKit médico §7(botão direito)", 1, "§7Cura você e quem estiver a 6 blocos.", "§7Recarrega em 18 s."));
        if (c == 2) inv.setItem(2, pl.armas.item("placas", Material.IRON_INGOT, "§ePlacas de blindagem §7(botão direito)", 1, "§7Escudo extra para você e quem estiver a 6 blocos.", "§7Recarrega em 25 s."));
        pl.armas.vestir(p, c == 3 ? "tatico" : "exercito");   // atirador de elite vai de preto; o resto, farda camuflada
        inv.setHeldItemSlot(0);
    }

    private void municao(Player p) {
        int c = classe.getOrDefault(p.getUniqueId(), 0);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (pl.armas.municaoDe(inv.getItem(i)) != null) inv.setItem(i, null);
        String[] tipo = {"pesada", "cartucho", "leve", "pesada"};
        int[] qtd = {150, 36, 210, 30};
        for (int q = qtd[c]; q > 0; q -= 64) inv.addItem(pl.armas.municao(tipo[c], Math.min(64, q)));
        if (c != 2) inv.addItem(pl.armas.municao("leve", 48));
    }

    private void comecar() {
        noJogo.clear();
        caidos.clear();
        int max = pl.getConfig().getInt("missao.maximo-jogadores", 4);
        for (Player p : mundo.getPlayers()) {
            if (noJogo.size() >= max) {
                p.sendMessage(PREFIXO + "§eO esquadrão fechou com " + max + ". Você entra na próxima.");
                continue;
            }
            noJogo.add(p.getUniqueId());
            equipar(p);
            p.teleport(partida(0));
            p.sendMessage(PREFIXO + "§fClasse: " + CLASSE_NOME[classe.getOrDefault(p.getUniqueId(), 0)] + "§7. Botão direito atira; esquerdo no ar ou Q recarrega.");
        }
        limpar();
        for (int s = 0; s < SETORES; s++) portao(s, false);
        fase = Fase.JOGO;
        setor = 0;
        tempo = 0;
        setorAtivo = false;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (fase != Fase.JOGO) return;
            portao(0, true);
            aviso("§c§lSETOR 1", "§f" + NOMES[0] + " §7— limpe a área para o portão abrir", Sound.BLOCK_IRON_DOOR_OPEN);
        }, 60L);
    }

    private void aviso(String a, String b2, Sound s) {
        for (Player p : time()) {
            p.showTitle(Title.title(Tiro.txt(a), Tiro.txt(b2), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2600), Duration.ofMillis(500))));
            if (s != null) p.playSound(p.getLocation(), s, 1f, 1f);
        }
    }

    private List<Zombie> inimigos() {
        List<Zombie> l = new ArrayList<>();
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) if (pl.zumbis.tipo(z) != null && !z.isDead()) l.add(z);
        return l;
    }

    private void limpar() {
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) z.remove();
        proximoTiro.clear();
        miraAte.clear();
        rajada.clear();
    }

    private void limparSetor() {
        for (Zombie z : inimigos()) z.remove();
        reforcoVeio = true;
    }

    private Zombie soltar(Zumbis.Tipo t, int s, boolean fundo) {
        int L = larg(s) - 2;
        Location l = null;
        for (int i = 0; i < 12 && l == null; i++) {
            int x = rnd.nextInt(2 * L + 1) - L, z = fundo ? z1(s) - 2 - rnd.nextInt(4) : z0(s) + COMP / 2 - 4 + rnd.nextInt(COMP / 2);
            if (t == Zumbis.Tipo.ATIRADOR && s >= 2) {
                l = new Location(mundo, (rnd.nextBoolean() ? 1 : -1) * (larg(s) - 2) + 0.5, Y + 5, z1(s) - 5 + 0.5);
                break;
            }
            if (mundo.getBlockAt(x, Y + 1, z).getType().isAir() && mundo.getBlockAt(x, Y + 2, z).getType().isAir()) l = new Location(mundo, x + 0.5, Y + 1, z + 0.5);
        }
        if (l == null) l = new Location(mundo, 0.5, Y + 1, z1(s) - 3.5);
        l.setYaw(180f);
        int n = Math.max(1, time().size());
        Zombie z = pl.zumbis.nascer(l, t, (t == Zumbis.Tipo.JUGGERNAUT ? 0.6 + 0.4 * n : 0.8 + 0.2 * n));
        z.setSilent(true);
        try {
            Bukkit.getMobGoals().removeAllGoals(z);
        } catch (Throwable ignorado) {
            z.setTarget(null);
        }
        proximoTiro.put(z.getUniqueId(), System.currentTimeMillis() + 1500 + rnd.nextInt(1500));
        return z;
    }

    /** Quem guarda cada setor (o numero cresce com o tamanho do esquadrao). */
    private void povoar(int s, boolean reforco) {
        int n = Math.max(1, time().size());
        int[][] grupos = {       // soldado, escopeta, atirador, escudo, pesado
                {4, 0, 0, 0, 0}, {4, 2, 0, 1, 0}, {3, 1, 2, 2, 0}, {3, 3, 1, 1, 1}, {4, 2, 0, 1, 0}};
        int[][] ref = {{3, 0, 0, 0, 0}, {2, 2, 0, 0, 0}, {2, 1, 0, 1, 0}, {2, 1, 0, 1, 1}, {3, 1, 0, 1, 0}};
        Zumbis.Tipo[] tipos = {Zumbis.Tipo.SOLDADO, Zumbis.Tipo.ESCOPETA, Zumbis.Tipo.ATIRADOR, Zumbis.Tipo.ESCUDO, Zumbis.Tipo.PESADO};
        int[] g = reforco ? ref[s] : grupos[s];
        for (int i = 0; i < g.length; i++) {
            int q = g[i] == 0 ? 0 : (i >= 2 ? g[i] + (n >= 3 ? 1 : 0) : (int) Math.ceil(g[i] * (0.7 + 0.3 * n)));
            for (int k = 0; k < q; k++) soltar(tipos[i], s, reforco);
        }
        if (s == SETORES - 1 && !reforco) {
            soltar(Zumbis.Tipo.JUGGERNAUT, s, true).setGlowing(true);
            aviso("§4§lJUGGERNAUT", "§7Blindado pela frente. §fAtire nas costas!", Sound.ENTITY_RAVAGER_ROAR);
        }
    }

    private void segundo() {
        if (mundo == null) return;
        for (Player p : mundo.getPlayers()) {
            if (p.getY() < Y - 10) p.teleport(noJogo.contains(p.getUniqueId()) && fase == Fase.JOGO ? partida(setor) : sala());
            if (pl.musica != null) pl.musica.tocar(p, fase == Fase.JOGO ? (setorAtivo ? "missao_luta" : "missao") : fase == Fase.FIM ? null : "missao");
        }
        int na = mundo.getPlayers().size();
        if (fase == Fase.ESPERA) {
            barra.name(Tiro.txt("§c§lMISSÃO §7— escolha a classe" + (na > 0 ? " §8| §f" + na + " no esquadrão" : "")));
            barra.progress(1f);
            if (na > 0) {
                fase = Fase.CONTAGEM;
                contagem = pl.getConfig().getInt("missao.contagem-segundos", 20);
            }
            return;
        }
        if (fase == Fase.CONTAGEM) {
            if (na == 0) {
                fase = Fase.ESPERA;
                return;
            }
            barra.name(Tiro.txt("§c§lMISSÃO §7começa em §f" + contagem + "s §8| §f" + na + " no esquadrão"));
            barra.progress(Math.max(0f, Math.min(1f, contagem / 20f)));
            if (contagem <= 5 && contagem > 0) for (Player p : mundo.getPlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            if (contagem-- <= 0) comecar();
            return;
        }
        if (fase == Fase.FIM) {
            if (contagem-- <= 0) {
                limpar();
                for (Player p : new ArrayList<>(mundo.getPlayers())) {
                    noJogo.remove(p.getUniqueId());
                    caidos.remove(p.getUniqueId());
                    p.teleport(sala());
                    preparar(p);
                }
                fase = Fase.ESPERA;
            }
            return;
        }
        // ---- em jogo
        tempo++;
        for (Player p : vivos()) {
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            if (p.getHealth() < max && System.currentTimeMillis() - feridoEm.getOrDefault(p.getUniqueId(), 0L) > 5000) p.setHealth(Math.min(max, p.getHealth() + 2));
        }
        List<Player> vivos = vivos();
        if (time().isEmpty()) {
            acabar(false, "");
            return;
        }
        if (vivos.isEmpty()) {
            acabar(false, "§7O esquadrão inteiro caiu no setor " + (setor + 1) + ".");
            return;
        }
        List<Zombie> ini = inimigos();
        if (!setorAtivo) {
            boolean entrou = false;
            for (Player p : vivos) if (p.getLocation().getZ() > z0(setor) + 2) entrou = true;
            if (entrou || tempo % 30 == 29) {
                setorAtivo = true;
                reforcoVeio = false;
                povoar(setor, false);
            }
        } else if (ini.size() <= 2 && !reforcoVeio) {
            reforcoVeio = true;
            povoar(setor, true);
            aviso("§e§lREFORÇOS!", "§7Mais soldados chegando pelo fundo", Sound.BLOCK_BELL_USE);
        } else if (ini.isEmpty()) {
            setorAtivo = false;
            if (setor == SETORES - 1) {
                acabar(true, "");
                return;
            }
            setor++;
            portao(setor, true);
            // ponto de controle: cura, municao e quem caiu volta
            for (Player p : time()) {
                if (caidos.remove(p.getUniqueId())) {
                    p.setGameMode(GameMode.ADVENTURE);
                    p.teleport(partida(setor));
                    equipar(p);
                }
                p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                municao(p);
                pl.coronas(p, 10, "pelo setor " + setor + " da Missão");
            }
            aviso("§a§lPONTO DE CONTROLE " + setor + "/" + (SETORES - 1), "§fPróximo: §e" + NOMES[setor] + " §7— vida e munição repostas", Sound.ENTITY_PLAYER_LEVELUP);
        }
        barra.name(Tiro.txt("§c§lSETOR " + (setor + 1) + "/" + SETORES + " §f" + NOMES[setor] + " §8| §7inimigos §f" + ini.size() + " §8| §7" + String.format("%d:%02d", tempo / 60, tempo % 60)));
        barra.progress(setorAtivo ? Math.max(0.05f, Math.min(1f, ini.size() / 10f)) : 1f);
    }

    private void acabar(boolean vitoria, String sub) {
        fase = Fase.FIM;
        contagem = 12;
        for (Player p : time()) {
            if (vitoria) {
                int premio = 120 + Math.max(0, 600 - tempo) / 10;
                p.showTitle(Title.title(Tiro.txt("§a§lMISSÃO CUMPRIDA"), Tiro.txt("§7Tempo §f" + String.format("%d:%02d", tempo / 60, tempo % 60))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                pl.coronas(p, premio, "por completar a Missão");
                int n = pl.dados.getInt("missao.vitorias." + p.getUniqueId(), 0) + 1;
                pl.dados.set("missao.vitorias." + p.getUniqueId(), n);
                int melhor = pl.dados.getInt("missao.tempo." + p.getUniqueId(), 99999);
                if (tempo < melhor) pl.dados.set("missao.tempo." + p.getUniqueId(), tempo);
            } else {
                p.showTitle(Title.title(Tiro.txt("§c§lMISSÃO FRACASSADA"), Tiro.txt(sub)));
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f);
            }
            if (caidos.remove(p.getUniqueId())) p.setGameMode(GameMode.ADVENTURE);
        }
        limpar();
    }

    // ================================================================== inimigos: andam, miram e atiram

    private void ia() {
        if (mundo == null || fase != Fase.JOGO) return;
        tick++;
        List<Player> alvos = vivos();
        if (alvos.isEmpty()) return;
        long agora = System.currentTimeMillis();
        for (Zombie z : inimigos()) {
            Zumbis.Tipo t = pl.zumbis.tipo(z);
            Perfil pf = PERFIS.get(t);
            if (pf == null) continue;
            Player alvo = null;
            double dm = 1e9;
            for (Player p : alvos) {
                double d = p.getLocation().distanceSquared(z.getLocation());
                if (d < dm) {
                    dm = d;
                    alvo = p;
                }
            }
            if (alvo == null) continue;
            double d = Math.sqrt(dm);
            boolean ve = z.hasLineOfSight(alvo);
            UUID u = z.getUniqueId();
            // olha para o alvo
            Vector dir = alvo.getEyeLocation().toVector().subtract(z.getEyeLocation().toVector());
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            z.setRotation(yaw, 0f);
            try {
                z.setBodyYaw(yaw);
            } catch (Throwable ignorado) {
                // versao sem setBodyYaw
            }
            // anda: chega ate a distancia que gosta; sem visao, procura; perto demais (menos o escudo e a escopeta), recua
            if (t != Zumbis.Tipo.ATIRADOR && rajada.getOrDefault(u, 0) <= 0) {
                if (!ve || d > pf.longe()) z.getPathfinder().moveTo(alvo.getLocation(), t == Zumbis.Tipo.ESCOPETA ? 1.25 : 1.0);
                else if (d < pf.perto() && t != Zumbis.Tipo.ESCUDO && t != Zumbis.Tipo.ESCOPETA) {
                    Location tras = z.getLocation().add(dir.clone().setY(0).normalize().multiply(-4));
                    z.getPathfinder().moveTo(tras, 1.0);
                } else if (tick % 12 == (u.getLeastSignificantBits() & 7)) {
                    // troca de posicao de lado, para nao ficar parado feito poste
                    Vector lado = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(rnd.nextBoolean() ? 3 : -3);
                    z.getPathfinder().moveTo(z.getLocation().add(lado), 1.0);
                } else if (tick % 12 == ((u.getLeastSignificantBits() & 7) + 5) % 12) z.getPathfinder().stopPathfinding();
            }
            // tiro
            int rj = rajada.getOrDefault(u, 0);
            if (rj > 0) {
                rajada.put(u, rj - 1);
                if (ve) disparo(z, t, pf, alvo, d);
                continue;
            }
            Long mira = miraAte.get(u);
            if (mira != null) {                                                  // atirador de elite e pesado: avisam antes
                if (!ve) {
                    miraAte.remove(u);
                    continue;
                }
                if (t == Zumbis.Tipo.ATIRADOR) laser(z, alvo);
                if (agora >= mira) {
                    miraAte.remove(u);
                    proximoTiro.put(u, agora + pf.intervalo() * 50L + rnd.nextInt(600));
                    if (pf.rajada() > 1) rajada.put(u, pf.rajada());
                    else disparo(z, t, pf, alvo, d);
                }
                continue;
            }
            if (!ve || d > pf.longe() + 6 || agora < proximoTiro.getOrDefault(u, 0L)) continue;
            if (agora - fogoEm.getOrDefault(alvo.getUniqueId(), 0L) < 650) continue;
            fogoEm.put(alvo.getUniqueId(), agora);
            if (t == Zumbis.Tipo.ATIRADOR || t == Zumbis.Tipo.PESADO || t == Zumbis.Tipo.JUGGERNAUT) {
                miraAte.put(u, agora + (t == Zumbis.Tipo.ATIRADOR ? 1500 : 900));
                mundo.playSound(z.getLocation(), t == Zumbis.Tipo.ATIRADOR ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_PISTON_EXTEND, 1f, t == Zumbis.Tipo.ATIRADOR ? 2f : 0.6f);
                z.getPathfinder().stopPathfinding();
                continue;
            }
            proximoTiro.put(u, agora + pf.intervalo() * 50L + rnd.nextInt(500));
            if (pf.rajada() > 1) rajada.put(u, pf.rajada());
            else disparo(z, t, pf, alvo, d);
        }
    }

    private void laser(Zombie z, Player alvo) {
        Location a = z.getEyeLocation(), b2 = alvo.getEyeLocation().subtract(0, 0.3, 0);
        Vector v = b2.toVector().subtract(a.toVector());
        double len = v.length();
        v.normalize();
        Particle.DustOptions po = new Particle.DustOptions(Color.RED, 0.5f);
        for (double k = 1; k < len; k += 0.8) mundo.spawnParticle(Particle.DUST, a.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0, po);
    }

    private void disparo(Zombie z, Zumbis.Tipo t, Perfil pf, Player alvo, double d) {
        if (pl.visual != null) pl.visual.atirando(z);
        Location boca = z.getEyeLocation().add(z.getLocation().getDirection().multiply(0.8)).subtract(0, 0.3, 0);
        boolean grosso = t == Zumbis.Tipo.ESCOPETA || t == Zumbis.Tipo.ATIRADOR;
        mundo.playSound(boca, grosso ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_FIREWORK_ROCKET_BLAST, grosso ? 0.7f : 0.45f, t == Zumbis.Tipo.ATIRADOR ? 2f : t == Zumbis.Tipo.ESCOPETA ? 1.7f : 1.5f + rnd.nextFloat() * 0.3f);
        // pontaria: cai com a distancia; agachado ou correndo voce e mais dificil de acertar
        double chance = pf.pontaria() * Math.max(0.25, 1 - d / (pf.longe() * 2.2));
        if (alvo.isSneaking()) chance *= 0.6;
        if (alvo.isSprinting()) chance *= 0.75;
        boolean acertou = rnd.nextDouble() < chance;
        Location fim = alvo.getEyeLocation().subtract(0, 0.4, 0);
        if (!acertou) fim.add(rnd.nextGaussian() * 0.9, rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.9);
        Vector v = fim.toVector().subtract(boca.toVector());
        double len = v.length();
        v.normalize();
        // rastro fino (no Bedrock a poeira colorida sai enorme e tapa a visao)
        for (double k = 0.8; k < len; k += 1.6) mundo.spawnParticle(Particle.ELECTRIC_SPARK, boca.clone().add(v.clone().multiply(k)), 1, 0, 0, 0, 0);
        if (!acertou) {
            alvo.playSound(fim, Sound.ENTITY_ARROW_SHOOT, 0.35f, 1.9f);       // zunido da bala passando
            return;
        }
        double dano = pf.dano() * (t == Zumbis.Tipo.ESCOPETA ? Math.max(0.3, 1 - d / 9) : 1) * (1 + 0.12 * setor);
        alvo.setNoDamageTicks(0);
        feridoEm.put(alvo.getUniqueId(), System.currentTimeMillis());
        alvo.damage(dano);   // sem "quem bateu": dano de mob muda com a dificuldade do mundo (zero no pacifico)
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void semAlvoProprio(EntityTargetLivingEntityEvent e) {
        if (mundo != null && e.getEntity().getWorld() == mundo && PERFIS.containsKey(pl.zumbis.tipo(e.getEntity()))) e.setCancelled(true);
    }

    /** Soldado nao da soco. Escudo e blindados so tomam dano de verdade de lado ou pelas costas. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void golpes(EntityDamageByEntityEvent e) {
        if (mundo == null || e.getEntity().getWorld() != mundo) return;
        Zumbis.Tipo tb = pl.zumbis.tipo(e.getDamager());
        if (tb != null && PERFIS.containsKey(tb) && e.getEntity() instanceof Player) {
            if (!bala) e.setCancelled(true);
            return;
        }
        Zumbis.Tipo t = pl.zumbis.tipo(e.getEntity());
        if (t == null || !(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity alvo)) return;
        if (t != Zumbis.Tipo.ESCUDO && t != Zumbis.Tipo.PESADO && t != Zumbis.Tipo.JUGGERNAUT) return;
        Vector olha = alvo.getLocation().getDirection().setY(0).normalize();
        Vector para = p.getLocation().toVector().subtract(alvo.getLocation().toVector()).setY(0).normalize();
        double frente = olha.dot(para);                                          // 1 = de frente, -1 = pelas costas
        if (t == Zumbis.Tipo.ESCUDO && frente > 0.35) {
            e.setDamage(e.getDamage() * 0.08);
            mundo.playSound(alvo.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.8f);
            p.sendActionBar(Tiro.txt("§7O escudo segurou! §eDê a volta§7 ou espere ele virar."));
        } else if (t != Zumbis.Tipo.ESCUDO) {
            if (frente < -0.3) {
                e.setDamage(e.getDamage() * 2.2);
                p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 0.8f);
            } else if (t == Zumbis.Tipo.JUGGERNAUT) e.setDamage(e.getDamage() * 0.45);
        }
    }

    void morreu(Zombie z, Zumbis.Tipo t, Player matador) {
        proximoTiro.remove(z.getUniqueId());
        miraAte.remove(z.getUniqueId());
        rajada.remove(z.getUniqueId());
        if (matador != null) matador.sendActionBar(Tiro.txt("§a+ " + t.nome + " §7abatido"));
    }

    // ================================================================== jogador: classe, habilidade, queda

    @EventHandler(priority = EventPriority.HIGH)
    public void usar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_AIR) return;
        String id = pl.armas.idItem(p.getInventory().getItemInMainHand());
        if (id == null) return;
        if (id.startsWith("classe") && fase != Fase.JOGO) {
            int c = id.charAt(6) - '0';
            classe.put(p.getUniqueId(), c);
            p.sendMessage(PREFIXO + "Classe: " + CLASSE_NOME[c] + "§7.");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            e.setCancelled(true);
            return;
        }
        if (!id.equals("kitmed") && !id.equals("placas")) return;
        e.setCancelled(true);
        long agora = System.currentTimeMillis(), espera = id.equals("kitmed") ? 18000 : 25000;
        long falta = espera - (agora - ultimoKit.getOrDefault(p.getUniqueId(), 0L));
        if (falta > 0) {
            p.sendActionBar(Tiro.txt("§7Recarregando: §f" + (falta / 1000 + 1) + "s"));
            return;
        }
        ultimoKit.put(p.getUniqueId(), agora);
        for (Player o : vivos()) {
            if (o.getLocation().distanceSquared(p.getLocation()) > 36) continue;
            if (id.equals("kitmed")) {
                o.setHealth(Math.min(o.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), o.getHealth() + 10));
                o.getWorld().spawnParticle(Particle.HEART, o.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3, 0);
            } else {
                o.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 40, 2, false, false, true));
                o.getWorld().spawnParticle(Particle.CRIT, o.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.1);
            }
            o.playSound(o.getLocation(), id.equals("kitmed") ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.4f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!aqui(p)) return;
        e.getDrops().clear();
        e.setKeepInventory(true);
        e.setDroppedExp(0);
        e.deathMessage(null);
        if (noJogo.contains(p.getUniqueId()) && fase == Fase.JOGO) {
            caidos.add(p.getUniqueId());
            for (Player o : time()) o.sendMessage(PREFIXO + "§c" + p.getName() + " §7caiu. Volta no próximo ponto de controle.");
        }
    }

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!noJogo.contains(p.getUniqueId()) || mundo == null) return;
        boolean caido = caidos.contains(p.getUniqueId()) && fase == Fase.JOGO;
        e.setRespawnLocation(caido ? partida(setor) : sala());
        if (caido) Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && aqui(p) && caidos.contains(p.getUniqueId())) {
                p.setGameMode(GameMode.SPECTATOR);
                p.showTitle(Title.title(Tiro.txt("§c§lVOCÊ CAIU"), Tiro.txt("§7Você volta quando o time limpar o setor")));
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void dano(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !aqui(p)) return;
        if (fase != Fase.JOGO || !noJogo.contains(p.getUniqueId()) || e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && aqui(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quebrar(BlockBreakEvent e) {
        if (aqui(e.getPlayer()) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void colocar(BlockPlaceEvent e) {
        if (aqui(e.getPlayer()) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void largar(PlayerDropItemEvent e) {
        if (aqui(e.getPlayer()) && pl.armas.armaDe(e.getItemDrop().getItemStack()) == null) e.setCancelled(true);
    }

    @EventHandler
    public void nascer(CreatureSpawnEvent e) {
        if (mundo != null && e.getLocation().getWorld() == mundo && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    /** 15/11 (Paulo: "os itens sairam do modo de tiro e foram para o spawn"): nada da Missao sai da Missao. */
    private void varrer(Player p) {
        if (pl.mundoDeTiro(p.getWorld())) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            String id = pl.armas.idItem(it);
            boolean daMissao = id != null && (id.startsWith("classe") || id.startsWith("uniforme_") || id.equals("kitmed") || id.equals("placas"));
            if (daMissao || pl.armas.armaDe(it) != null || pl.armas.municaoDe(it) != null) inv.setItem(i, null);
        }
    }

    @EventHandler
    public void entrouNoServidor(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) varrer(p); }, 50L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void mudou(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        // depois das mochilas do Extras trocarem (elas guardam a do Apocalipse com as armas dentro)
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) varrer(p); }, 8L);
        if (e.getFrom() == mundo) {
            noJogo.remove(p.getUniqueId());
            caidos.remove(p.getUniqueId());
            p.hideBossBar(barra);
            p.getInventory().clear();
            for (PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p)) return;
        noJogo.remove(p.getUniqueId());
        caidos.remove(p.getUniqueId());
        p.getInventory().clear();
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(pl.spawnLobby());
    }
}
