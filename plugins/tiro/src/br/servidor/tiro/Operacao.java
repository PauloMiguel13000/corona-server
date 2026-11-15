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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * OPERACAO COROA (15/11): co-op de tiro de 1 a 4 jogadores, no estilo das missoes PvE do Warface. O esquadrao
 * escolhe as classes (Fuzileiro reabastece municao, Medico cura, Engenheiro repara colete, Atirador de elite marca
 * os inimigos), desce num mapa (Deposito, Rua em Ruinas ou Laboratorio, em rodizio) e cumpre a missao em 3 fases:
 * limpar as ondas, defender o gerador e derrubar o Chefe de Horda. Quem cai volta depois de alguns segundos enquanto
 * houver fichas de retorno e alguem de pe. No fim: XP, patente e coronas. Uma missao por vez; o inventario e do jogo
 * (o jogador entra pelo Lobby de Tiro, de mao vazia, e sai de mao vazia).
 */
final class Operacao implements Listener {

    static final String PREFIXO = "§8[§6§lHorda§8] §7";
    static final int Y = 64, M = 28, YSALA = 140, VERSAO = 1;
    static final String[] MAPAS = {"Depósito 7", "Rua em Ruínas", "Laboratório Coroa"};
    static final String[] DIFS = {"§aFácil", "§eNormal", "§cDifícil"};
    static final double[] FORCA = {0.75, 1.0, 1.4};
    static final int[] PREMIO = {60, 90, 130};
    static final String[] PATENTES = {"Recruta", "Soldado", "Cabo", "Sargento", "Tenente", "Capitão", "Major", "Coronel"};
    static final int[] PATENTE_XP = {0, 100, 300, 700, 1500, 3000, 6000, 12000};

    enum Fase { ESPERA, CONTAGEM, ONDAS, DEFESA, CHEFE, FIM }

    record Classe(String id, String nome, Material icone, int cor, String arma, String habilidade, String descricao) {}

    static final Classe[] CLASSES = {
            new Classe("fuzileiro", "§aFuzileiro", Material.IRON_HOE, 0x4A6B3A, "fuzil", "§aCaixa de Munição", "Reabastece a munição de todo mundo por perto."),
            new Classe("medico", "§cMédico", Material.STONE_HOE, 0xC8C8C8, "escopeta", "§cKit Médico", "Cura todo mundo por perto."),
            new Classe("engenheiro", "§eEngenheiro", Material.GOLDEN_HOE, 0xC9A227, "smg", "§eReparo de Colete", "Dá colete (corações extras) a todos por perto."),
            new Classe("atirador", "§bAtirador de Elite", Material.DIAMOND_HOE, 0x2A3A5A, "sniper", "§bMarcar Alvos", "Faz todos os inimigos brilharem através das paredes.")};

    static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    final Tiro pl;
    World mundo;
    private final Random rnd = new Random();
    private Fase fase = Fase.ESPERA;
    private int t, mapa, proximoMapa, dif = 1, onda, fichas, vivosNoInicio;
    private final Map<UUID, String> classe = new HashMap<>();
    private final Set<UUID> noJogo = new HashSet<>();
    private final Map<UUID, Integer> caidoAte = new HashMap<>(), pontos = new HashMap<>();
    private final Map<UUID, Long> habilidadeAte = new HashMap<>();
    private BossBar barra;
    private Zombie chefe;
    private double chefeVida;

    Operacao(Tiro pl) {
        this.pl = pl;
        mundo = new WorldCreator(Tiro.OPERACAO).generator(new Tiro.Vazio()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (mundo == null) return;
        Tiro.regrasDeLobby(mundo);
        mundo.setDifficulty(org.bukkit.Difficulty.NORMAL);
        mundo.setTime(14000);
        mundo.setSpawnLocation(sala());
        if (pl.getConfig().getInt("operacao-mapas-feitos", 0) != VERSAO) {
            for (int i = 0; i < MAPAS.length; i++) construir(i);
            sala(true);
            pl.getConfig().set("operacao-mapas-feitos", VERSAO);
            pl.saveConfig();
        }
        for (Entity e : mundo.getEntities()) if (e instanceof Zombie) e.remove();
        barra = BossBar.bossBar(Tiro.txt("§6§lMODO HORDA"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        Bukkit.getScheduler().runTaskTimer(pl, this::segundo, 60L, 20L);
    }

    void desligar() {
        if (mundo == null) return;
        for (Player p : new ArrayList<>(mundo.getPlayers())) tirar(p, false);
    }

    // ================================================================== mapas

    int ox(int i) {
        return i * 200;
    }

    Location sala() {
        return new Location(mundo, 0.5, YSALA + 1, 0.5, 0f, 0f);
    }

    Location inicio(int i, int k) {
        return new Location(mundo, ox(i) + 0.5 + (k % 2 == 0 ? 1.5 : -1.5) * (1 + k / 2), Y, 22.5, 180f, 0f);
    }

    private void b(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    private void caixa(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, m);
    }

    private void sala(boolean fazer) {
        caixa(-7, YSALA, -7, 7, YSALA, 7, Material.POLISHED_DEEPSLATE);
        caixa(-7, YSALA + 1, -7, 7, YSALA + 5, 7, Material.AIR);
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                if (Math.abs(x) == 7 || Math.abs(z) == 7) caixa(x, YSALA + 1, z, x, YSALA + 5, z, (x + z) % 3 == 0 ? Material.CUT_COPPER : Material.DEEPSLATE_BRICKS);
                b(x, YSALA + 6, z, Material.DEEPSLATE_TILES);
            }
        for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) b(c[0], YSALA + 1, c[1], Material.LANTERN);
        for (int x = -3; x <= 3; x += 2) b(x, YSALA + 1, -6, Material.BARREL);
        b(0, YSALA, 0, Material.ORANGE_CONCRETE);
    }

    private void construir(int i) {
        int o = ox(i);
        for (int cx = (o - M - 4) >> 4; cx <= (o + M + 4) >> 4; cx++) for (int cz = (-M - 4) >> 4; cz <= (M + 4) >> 4; cz++) {
            mundo.getChunkAt(cx, cz).load(true);
            mundo.addPluginChunkTicket(cx, cz, pl);
        }
        Random r = new Random(1000 + i);
        Material[] piso = i == 0 ? new Material[] {Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.STONE}
                : i == 1 ? new Material[] {Material.GRAY_CONCRETE_POWDER, Material.COARSE_DIRT, Material.GRAVEL, Material.BLACK_CONCRETE_POWDER}
                : new Material[] {Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.WHITE_CONCRETE, Material.SMOOTH_QUARTZ};
        Material muro = i == 0 ? Material.IRON_BLOCK : i == 1 ? Material.CRACKED_STONE_BRICKS : Material.WHITE_CONCRETE;
        caixa(o - M - 1, Y - 2, -M - 1, o + M + 1, Y + 14, M + 1, Material.AIR);
        caixa(o - M - 1, Y - 2, -M - 1, o + M + 1, Y - 2, M + 1, Material.BEDROCK);
        for (int x = -M; x <= M; x++)
            for (int z = -M; z <= M; z++) {
                b(o + x, Y - 1, z, piso[r.nextInt(piso.length)]);
                if (Math.abs(x) == M || Math.abs(z) == M) {
                    caixa(o + x, Y, z, o + x, Y + 5, z, (x + z) % 7 == 0 ? Material.DEEPSLATE_BRICKS : muro);
                    caixa(o + x, Y + 6, z, o + x, Y + 13, z, Material.BARRIER);
                }
            }
        if (i == 2) caixa(o - M, Y + 6, -M, o + M, Y + 6, M, Material.SMOOTH_QUARTZ);     // laboratorio e fechado
        // gerador no meio
        caixa(o - 1, Y - 1, -1, o + 1, Y - 1, 1, Material.YELLOW_CONCRETE);
        b(o, Y, 0, Material.LODESTONE);
        b(o, Y + 1, 0, Material.REDSTONE_LAMP);
        b(o, Y + 2, 0, Material.LIGHTNING_ROD);
        // coberturas
        Material[] cont = {Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.GREEN_CONCRETE, Material.ORANGE_CONCRETE, Material.CYAN_TERRACOTTA};
        for (int k = 0; k < 46; k++) {
            int x = r.nextInt(2 * M - 8) - M + 4, z = r.nextInt(2 * M - 8) - M + 4;
            if (Math.abs(x) < 5 && Math.abs(z) < 5) continue;                 // gerador
            if (Math.abs(x) < 5 && z > 16) continue;                          // entrada do esquadrao
            if ((Math.abs(x) < 4 && z < -M + 9) || (Math.abs(z) < 4 && Math.abs(x) > M - 9)) continue;   // portoes inimigos
            int tipo = r.nextInt(6);
            boolean ex = r.nextBoolean();
            if (i == 0 && tipo <= 1) {                                         // conteiner 2 x 5 x 3
                Material m = cont[r.nextInt(cont.length)];
                caixa(o + x, Y, z, o + x + (ex ? 4 : 1), Y + 2, z + (ex ? 1 : 4), m);
            } else if (i == 1 && tipo <= 1) {                                  // parede de predio em ruina
                for (int s = 0; s < 6; s++) {
                    int h = 1 + r.nextInt(4);
                    caixa(o + x + (ex ? s : 0), Y, z + (ex ? 0 : s), o + x + (ex ? s : 0), Y + h, z + (ex ? 0 : s), r.nextInt(3) == 0 ? Material.MOSSY_STONE_BRICKS : Material.BRICKS);
                }
            } else if (i == 2 && tipo <= 1) {                                  // bancada / tanque do laboratorio
                caixa(o + x, Y, z, o + x + (ex ? 3 : 0), Y, z + (ex ? 0 : 3), Material.QUARTZ_BLOCK);
                b(o + x, Y + 1, z, r.nextBoolean() ? Material.LIME_STAINED_GLASS : Material.SEA_LANTERN);
                b(o + x, Y + 2, z, Material.LIME_STAINED_GLASS);
            } else if (tipo == 2) {                                            // sacos de areia
                for (int s = 0; s < 4; s++) b(o + x + (ex ? s : 0), Y, z + (ex ? 0 : s), Material.SMOOTH_SANDSTONE);
                b(o + x, Y + 1, z, Material.SMOOTH_SANDSTONE_SLAB);
            } else if (tipo == 3) {                                            // caixotes
                b(o + x, Y, z, Material.BARREL);
                b(o + x + 1, Y, z, Material.BARREL);
                if (r.nextBoolean()) b(o + x, Y + 1, z, Material.BARREL);
            } else if (tipo == 4) {                                            // pilar
                caixa(o + x, Y, z, o + x, Y + 4, z, i == 2 ? Material.QUARTZ_PILLAR : Material.DEEPSLATE_BRICKS);
            } else {                                                           // carro batido / maquina
                caixa(o + x, Y, z, o + x + (ex ? 2 : 1), Y, z + (ex ? 1 : 2), i == 2 ? Material.IRON_BLOCK : Material.BLACK_CONCRETE);
                b(o + x, Y + 1, z, i == 2 ? Material.OBSERVER : Material.GRAY_STAINED_GLASS);
            }
        }
        // limpa a entrada do esquadrao e os portoes
        caixa(o - 4, Y, 18, o + 4, Y + 4, M - 1, Material.AIR);
        caixa(o - 3, Y, -M + 1, o + 3, Y + 4, -M + 5, Material.AIR);
        caixa(o - M + 1, Y, -3, o - M + 5, Y + 4, 3, Material.AIR);
        caixa(o + M - 5, Y, -3, o + M - 1, Y + 4, 3, Material.AIR);
        for (int[] g : new int[][] {{0, -M}, {-M, 0}, {M, 0}}) {
            for (int s = -2; s <= 2; s++) caixa(o + g[0] + (g[0] == 0 ? s : 0), Y, g[1] + (g[1] == 0 ? s : 0), o + g[0] + (g[0] == 0 ? s : 0), Y + 3, g[1] + (g[1] == 0 ? s : 0), Material.RED_NETHER_BRICKS);
        }
        org.bukkit.block.data.type.Light luz = (org.bukkit.block.data.type.Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(i == 1 ? 9 : 12);
        for (int x = -M + 2; x < M; x += 5) for (int z = -M + 2; z < M; z += 5)
            if (mundo.getBlockAt(o + x, Y + 4, z).getType().isAir()) mundo.getBlockAt(o + x, Y + 4, z).setBlockData(luz, false);
        pl.getLogger().info("Operacao: mapa " + MAPAS[i] + " construido.");
    }

    private Location portao(int i) {
        int o = ox(i);
        return switch (rnd.nextInt(3)) {
            case 0 -> new Location(mundo, o + rnd.nextInt(5) - 2 + 0.5, Y, -M + 3.5);
            case 1 -> new Location(mundo, o - M + 3.5, Y, rnd.nextInt(5) - 2 + 0.5);
            default -> new Location(mundo, o + M - 2.5, Y, rnd.nextInt(5) - 2 + 0.5);
        };
    }

    // ================================================================== entrar e sair

    boolean aqui(Player p) {
        return mundo != null && p.getWorld() == mundo;
    }

    List<Player> todos() {
        List<Player> l = new ArrayList<>();
        if (mundo != null) for (Player p : mundo.getPlayers()) if (p.getGameMode() != GameMode.SPECTATOR) l.add(p);
        return l;
    }

    List<Player> vivos() {
        List<Player> l = new ArrayList<>();
        for (Player p : todos()) if (noJogo.contains(p.getUniqueId()) && !caidoAte.containsKey(p.getUniqueId()) && !p.isDead()) l.add(p);
        return l;
    }

    int xp(UUID u) {
        return pl.dados.getInt("operacao.xp." + u, 0);
    }

    static int patente(int xp) {
        int k = 0;
        for (int i = 0; i < PATENTE_XP.length; i++) if (xp >= PATENTE_XP[i]) k = i;
        return k;
    }

    void entrar(Player p) {
        if (mundo == null) {
            p.sendMessage(PREFIXO + "§cO Modo Horda está indisponível agora.");
            return;
        }
        if (aqui(p)) {
            abrirMenu(p);
            return;
        }
        if (p.getWorld() != pl.lobby) {
            // so entra pelo Lobby de Tiro (de mao vazia: os itens do jogador ficam guardados la)
            String w = p.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
            if (Set.of("vovo", "slender", "backrooms", "hotel", "defesa", "login", "naochore", "turno_noite", "fabrica", "arena", "esconde", "porteiro").contains(w)) {
                p.sendMessage(PREFIXO + "§cSaia do outro jogo primeiro.");
                return;
            }
            if (w.equals(Tiro.ZUMBI)) {
                p.sendMessage(PREFIXO + "§7Saia do Apocalipse primeiro (§f/zumbi sair§7) e entre pelo portão do Modo Horda.");
                return;
            }
            pl.irLobby(p);
            p.sendMessage(PREFIXO + "§7Você está no Lobby de Tiro. Entre no §6portão da Modo Horda§7 (leste) ou use §f/operacao §7de novo.");
            return;
        }
        p.getInventory().clear();
        p.teleport(sala());
        p.setFallDistance(0);
        classe.putIfAbsent(p.getUniqueId(), "fuzileiro");
        int x = xp(p.getUniqueId());
        p.showTitle(Title.title(Tiro.txt("§6§lMODO HORDA"), Tiro.txt("§7Patente: §f" + PATENTES[patente(x)] + " §8(" + x + " XP)")));
        p.sendMessage(PREFIXO + "§fMissão co-op de 1 a 4 jogadores: §7limpe as ondas, defenda o gerador e derrube o Chefe de Horda.");
        p.sendMessage(PREFIXO + "§7Escolha a §fclasse§7 e a §fdificuldade§7 com a §eestrela§7 na sua mão. Sair: §f/operacao sair");
        if (fase != Fase.ESPERA && fase != Fase.CONTAGEM) p.sendMessage(PREFIXO + "§eTem uma missão em andamento: você entra na próxima.");
        itensDaSala(p);
        for (Player o : mundo.getPlayers()) if (o != p) o.sendMessage(PREFIXO + "§f" + p.getName() + " §7(" + PATENTES[patente(x)] + ") entrou no esquadrão.");
    }

    private void itensDaSala(Player p) {
        p.getInventory().clear();
        p.getInventory().setItem(4, pl.armas.item("menu", Material.NETHER_STAR, "§e§lClasse e dificuldade §7(clique)", 1, "§7Escolha a sua classe e a dificuldade da missão."));
    }

    void tirar(Player p, boolean avisar) {
        UUID u = p.getUniqueId();
        noJogo.remove(u);
        caidoAte.remove(u);
        p.hideBossBar(barra);
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setGlowing(false);
        p.setAbsorptionAmount(0);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        if (aqui(p)) pl.irLobby(p);
        if (avisar) p.sendMessage(PREFIXO + "Você saiu do Modo Horda.");
    }

    boolean comando(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "entrar" : a[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("top")) {
            List<String[]> l = new ArrayList<>();
            var sec = pl.dados.getConfigurationSection("operacao.nome");
            if (sec != null) for (String u : sec.getKeys(false)) l.add(new String[] {sec.getString(u), String.valueOf(pl.dados.getInt("operacao.xp." + u, 0)), String.valueOf(pl.dados.getInt("operacao.vitorias." + u, 0))});
            l.sort((x, y) -> Integer.compare(Integer.parseInt(y[1]), Integer.parseInt(x[1])));
            s.sendMessage(PREFIXO + "§e§lMelhores da Modo Horda");
            if (l.isEmpty()) s.sendMessage("§7Ninguém ainda. Seja o primeiro!");
            for (int i = 0; i < Math.min(10, l.size()); i++)
                s.sendMessage("§e" + (i + 1) + ". §f" + l.get(i)[0] + " §7— §6" + PATENTES[patente(Integer.parseInt(l.get(i)[1]))] + " §7(" + l.get(i)[1] + " XP, " + l.get(i)[2] + " missões)");
            return true;
        }
        if (sub.equals("admin") && s.hasPermission("tiro.admin")) {
            String o = a.length > 1 ? a[1] : "";
            if (o.equals("iniciar") && (fase == Fase.ESPERA || fase == Fase.CONTAGEM) && !todos().isEmpty()) comecar(todos());
            else if (o.equals("tempo") && a.length > 2) t = Integer.parseInt(a[2]);
            else if (o.equals("matar")) for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) if (pl.zumbis.tipo(z) != null && !vivos().isEmpty()) z.damage(9999, vivos().get(0));
            else if (o.equals("mapa") && a.length > 2) proximoMapa = Integer.parseInt(a[2]) % MAPAS.length;
            else if (o.equals("refazer")) {
                for (int i = 0; i < MAPAS.length; i++) construir(i);
                sala(true);
            } else s.sendMessage(PREFIXO + "fase=" + fase + " t=" + t + " mapa=" + mapa + " dif=" + dif + " onda=" + onda + " fichas=" + fichas + " vivos=" + vivos().size() + " inimigos=" + inimigos()
                        + " | admin <iniciar|tempo N|matar|mapa N|refazer>");
            return true;
        }
        if (!(s instanceof Player p)) return true;
        if (sub.equals("sair")) {
            if (aqui(p)) tirar(p, true);
            else p.sendMessage(PREFIXO + "Você não está no Modo Horda.");
        } else entrar(p);
        return true;
    }

    // ------------------------------------------------------------------ menu de classe e dificuldade

    private void abrirMenu(Player p) {
        Menu h = new Menu();
        h.inv = Bukkit.createInventory(h, 27, Tiro.txt("§8Modo Horda: classe e dificuldade"));
        String minha = classe.getOrDefault(p.getUniqueId(), "fuzileiro");
        for (int i = 0; i < CLASSES.length; i++) {
            Classe c = CLASSES[i];
            h.inv.setItem(10 + i * 2, pl.armas.item("classe", c.icone(), c.nome() + (c.id().equals(minha) ? " §a§l✔" : ""), 1,
                    "§7Arma: §f" + Armas.ARMAS.get(c.arma()).nome() + " §7+ pistola", "§7Habilidade: " + c.habilidade(), "§8" + c.descricao(), "", c.id().equals(minha) ? "§aSua classe" : "§eClique para escolher"));
        }
        h.inv.setItem(22, pl.armas.item("dif", Material.COMPARATOR, "§fDificuldade: " + DIFS[dif], 1, "§7Mais difícil = inimigos mais fortes,", "§7mais XP e mais coronas (" + PREMIO[dif] + ").", "",
                fase == Fase.ESPERA || fase == Fase.CONTAGEM ? "§eClique para trocar" : "§8(missão em andamento)"));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void cliqueMenu(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player q && aqui(q) && !(e.getInventory().getHolder() instanceof Menu) && (fase == Fase.ESPERA || fase == Fase.CONTAGEM || !noJogo.contains(q.getUniqueId()))) {
            e.setCancelled(true);     // na sala de espera ninguem mexe no inventario
            return;
        }
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        int s = e.getSlot();
        if (s == 22 && (fase == Fase.ESPERA || fase == Fase.CONTAGEM)) {
            dif = (dif + 1) % DIFS.length;
            for (Player o : todos()) o.sendMessage(PREFIXO + "§f" + p.getName() + " §7mudou a dificuldade para " + DIFS[dif] + "§7.");
        } else if (s >= 10 && s <= 16 && (s - 10) % 2 == 0 && !noJogo.contains(p.getUniqueId())) {
            classe.put(p.getUniqueId(), CLASSES[(s - 10) / 2].id());
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
        } else return;
        abrirMenu(p);
    }

    private Classe classeDe(Player p) {
        String id = classe.getOrDefault(p.getUniqueId(), "fuzileiro");
        for (Classe c : CLASSES) if (c.id().equals(id)) return c;
        return CLASSES[0];
    }

    private void equipar(Player p) {
        Classe c = classeDe(p);
        var inv = p.getInventory();
        inv.clear();
        inv.setItem(0, pl.armas.arma(c.arma()));
        inv.setItem(1, pl.armas.arma("pistola"));
        inv.setItem(2, pl.armas.item("habilidade", Material.NETHER_STAR, c.habilidade() + " §7(botão direito)", 1, "§7" + c.descricao()));
        inv.setItem(3, pl.apocalipse.coisa("bandagem", 3));
        Armas.Arma a = Armas.ARMAS.get(c.arma());
        int extra = patente(xp(p.getUniqueId())) * 10;
        inv.setItem(7, pl.armas.municao(a.municao(), 64));
        inv.addItem(pl.armas.municao(a.municao(), Math.min(64, a.pente() * 3 + extra)));
        if (!a.municao().equals("leve")) inv.setItem(8, pl.armas.municao("leve", 48));
        Material[] pecas = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        ItemStack[] arm = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            ItemStack it = new ItemStack(pecas[i]);
            LeatherArmorMeta m = (LeatherArmorMeta) it.getItemMeta();
            m.setColor(Color.fromRGB(c.cor()));
            m.setUnbreakable(true);
            it.setItemMeta(m);
            arm[3 - i] = it;
        }
        inv.setArmorContents(arm);
        pl.armas.vestir(p, "sobrevivente");   // 15/11: no Modo Horda todo mundo e sobrevivente (a cor da classe fica no nome e na habilidade)
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    // ================================================================== a missao

    private void titulo(Player p, String a, String b2) {
        p.showTitle(Title.title(Tiro.txt(a), Tiro.txt(b2), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2600), Duration.ofMillis(500))));
    }

    private int inimigos() {
        int n = 0;
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) if (pl.zumbis.tipo(z) != null && !z.isDead()) n++;
        return n;
    }

    private double forca() {
        return FORCA[dif] * (1 + 0.3 * (Math.max(1, vivosNoInicio) - 1));
    }

    private void soltar(Zumbis.Tipo tp, int n) {
        List<Player> v = vivos();
        for (int i = 0; i < n; i++) {
            Zombie z = pl.zumbis.nascer(portao(mapa), tp, tp == Zumbis.Tipo.CHEFE ? forca() : Math.sqrt(forca()));
            if (!v.isEmpty()) z.setTarget(v.get(rnd.nextInt(v.size())));
            if (tp == Zumbis.Tipo.CHEFE) {
                chefe = z;
                chefeVida = z.getHealth();
            }
        }
    }

    private void comecar(List<Player> gente) {
        int max = Math.max(1, pl.getConfig().getInt("operacao.maximo-jogadores", 4));
        List<Player> l = new ArrayList<>(gente.subList(0, Math.min(max, gente.size())));
        mapa = proximoMapa;
        proximoMapa = (proximoMapa + 1) % MAPAS.length;
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) z.remove();
        noJogo.clear();
        caidoAte.clear();
        pontos.clear();
        chefe = null;
        vivosNoInicio = l.size();
        fichas = l.size() == 1 ? 2 : 3;
        int k = 0;
        for (Player p : l) {
            noJogo.add(p.getUniqueId());
            p.closeInventory();
            equipar(p);
            p.teleport(inicio(mapa, k++));
            p.showBossBar(barra);
            titulo(p, "§6§l" + MAPAS[mapa].toUpperCase(), "§7Dificuldade " + DIFS[dif] + " §8| §7Classe " + classeDe(p).nome());
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 1f);
            p.sendMessage(PREFIXO + "§fFase 1: §7limpe as 3 ondas de infectados. §fBotão direito §7atira, §fQ §7recarrega, §festrela §7usa a habilidade.");
        }
        for (Player p : gente) if (!noJogo.contains(p.getUniqueId())) p.sendMessage(PREFIXO + "§eO esquadrão lotou (" + max + "). Você entra na próxima missão.");
        fase = Fase.ONDAS;
        onda = 0;
        t = 9;
        pl.getLogger().info("Operacao: missao em " + MAPAS[mapa] + " com " + l.size() + " jogador(es), dificuldade " + dif);
    }

    private void proximaOnda() {
        onda++;
        int n = vivosNoInicio;
        int base = 3 + onda * 2 + n + mapa;   // 15/11: teste no cliente - a primeira onda matava quem estava sozinho em segundos
        soltar(Zumbis.Tipo.COMUM, base);
        soltar(Zumbis.Tipo.CORREDOR, Math.max(0, onda + n - 1));
        if (onda >= 2) soltar(Zumbis.Tipo.CUSPIDOR, onda - 1 + mapa);
        if (onda >= 3) {
            soltar(Zumbis.Tipo.TANQUE, 1 + dif);
            soltar(Zumbis.Tipo.EXPLOSIVO, 2 + mapa);
        }
        for (Player p : todos()) if (noJogo.contains(p.getUniqueId())) {
            titulo(p, "§c§lONDA " + onda + "/3", "§7Eles estão vindo pelos portões vermelhos!");
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6f, 0.6f);
        }
    }

    /** Musica do momento: calma na sala e entre ondas, de luta com infectado vivo, a do chefe na fase final. */
    private void musica() {
        if (pl.musica == null) return;
        boolean vivos = false;
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) if (pl.zumbis.tipo(z) != null && !z.isDead()) vivos = true;
        String f = switch (fase) {
            case CHEFE -> "horda_chefe";
            case ONDAS, DEFESA -> vivos ? "horda_luta" : "horda";
            case FIM -> null;
            default -> "horda";
        };
        for (Player p : mundo.getPlayers()) pl.musica.tocar(p, f);
    }

    private void segundo() {
        musica();
        if (mundo == null) return;
        List<Player> todos = todos();
        for (Player p : todos) {
            if (p.getY() < Y - 10) p.teleport(noJogo.contains(p.getUniqueId()) && fase != Fase.ESPERA && fase != Fase.CONTAGEM ? inicio(mapa, 0) : sala());
            if (!noJogo.contains(p.getUniqueId()) && p.getFoodLevel() < 20) p.setFoodLevel(20);
        }
        if (fase == Fase.ESPERA) {
            if (!todos.isEmpty()) {
                fase = Fase.CONTAGEM;
                t = pl.getConfig().getInt("operacao.contagem-segundos", 20);
            }
            return;
        }
        if (fase == Fase.CONTAGEM) {
            if (todos.isEmpty()) {
                fase = Fase.ESPERA;
                return;
            }
            for (Player p : todos) {
                p.sendActionBar(Tiro.txt("§6Decolagem em §f" + t + "s §8| §7próximo mapa: §f" + MAPAS[proximoMapa] + " §8| " + DIFS[dif] + " §8| §7classe: " + classeDe(p).nome()));
                if (t <= 5 && t > 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            if (t-- <= 0) comecar(todos);
            return;
        }
        if (fase == Fase.FIM) {
            if (t-- <= 0) {
                for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) z.remove();
                for (Player p : todos) {
                    boolean jogou = noJogo.contains(p.getUniqueId());
                    p.hideBossBar(barra);
                    if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
                    for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                    p.setAbsorptionAmount(0);
                    p.setGlowing(false);
                    if (jogou || p.getLocation().getY() < YSALA - 5) p.teleport(sala());
                    itensDaSala(p);
                }
                noJogo.clear();
                caidoAte.clear();
                fase = Fase.ESPERA;
            }
            return;
        }
        // ---- missao rolando
        noJogo.removeIf(u -> {
            Player p = Bukkit.getPlayer(u);
            return p == null || !aqui(p);
        });
        // quem caiu volta enquanto houver ficha e alguem de pe
        for (UUID u : new ArrayList<>(caidoAte.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !noJogo.contains(u)) {
                caidoAte.remove(u);
                continue;
            }
            int falta = caidoAte.merge(u, -1, Integer::sum);
            if (falta > 0) {
                p.sendActionBar(Tiro.txt("§cVocê caiu. §7Volta em §f" + falta + "s §8| §7fichas do esquadrão: §f" + fichas));
                continue;
            }
            caidoAte.remove(u);
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(inicio(mapa, 0));
            equipar(p);
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 4, false, false, true));
            titulo(p, "§a§lDE VOLTA À LUTA", "§7Fichas restantes: " + fichas);
        }
        List<Player> vivos = vivos();
        if (noJogo.isEmpty()) {
            acabar(false, "§7O esquadrão abandonou a missão.");
            return;
        }
        if (vivos.isEmpty() && caidoAte.isEmpty()) {
            acabar(false, "§7Todo o esquadrão caiu.");
            return;
        }
        // os infectados sempre tem um alvo
        if (!vivos.isEmpty()) for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) {
            if (pl.zumbis.tipo(z) == null) continue;
            if (!(z.getTarget() instanceof Player tp) || tp.isDead() || tp.getGameMode() == GameMode.SPECTATOR || !aqui(tp)) {
                Player perto = vivos.get(0);
                for (Player p : vivos) if (p.getLocation().distanceSquared(z.getLocation()) < perto.getLocation().distanceSquared(z.getLocation())) perto = p;
                z.setTarget(perto);
            }
            if (z.getLocation().getY() < Y - 6) z.teleport(portao(mapa));
        }
        int ini = inimigos();
        switch (fase) {
            case ONDAS -> {
                barra.name(Tiro.txt("§6Fase 1 §8| §fOnda " + Math.max(1, onda) + "/3 §8| §cInimigos: " + ini + " §8| §7fichas " + fichas));
                barra.progress(Math.max(0f, Math.min(1f, (onda - (ini > 0 ? 1 : 0) + 0.0f) / 3f)));
                if (ini == 0 && t-- <= 0) {
                    if (onda >= 3) {
                        fase = Fase.DEFESA;
                        t = 75;
                        for (Player p : todos) if (noJogo.contains(p.getUniqueId())) {
                            titulo(p, "§e§lFASE 2: DEFENDA O GERADOR", "§7Fique perto do gerador (no meio) por 75 segundos");
                            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
                        }
                    } else {
                        proximaOnda();
                        t = 6;
                    }
                }
            }
            case DEFESA -> {
                boolean alguem = false;
                Location g = new Location(mundo, ox(mapa) + 0.5, Y, 0.5);
                for (Player p : vivos) if (p.getLocation().distanceSquared(g) < 9 * 9) alguem = true;
                if (alguem) t--;
                barra.name(Tiro.txt("§eFase 2 §8| §fDefenda o gerador: " + t + "s " + (alguem ? "" : "§c(ninguém no ponto!) ") + "§8| §cInimigos: " + ini));
                barra.progress(Math.max(0f, Math.min(1f, 1f - t / 75f)));
                mundo.spawnParticle(Particle.END_ROD, g.clone().add(0, 3.2, 0), 6, 0.2, 0.6, 0.2, 0.01);
                if (ini < 5 + vivosNoInicio * 3 && t % 4 == 0) {
                    soltar(Zumbis.Tipo.COMUM, 1 + vivosNoInicio);
                    soltar(rnd.nextBoolean() ? Zumbis.Tipo.CORREDOR : Zumbis.Tipo.CUSPIDOR, 1 + dif);
                    if (t % 20 == 0) soltar(rnd.nextBoolean() ? Zumbis.Tipo.TANQUE : Zumbis.Tipo.EXPLOSIVO, 1 + mapa / 2);
                }
                if (t <= 0) {
                    fase = Fase.CHEFE;
                    soltar(Zumbis.Tipo.CHEFE, 1);
                    soltar(Zumbis.Tipo.CORREDOR, 2 + vivosNoInicio);
                    for (Player p : todos) if (noJogo.contains(p.getUniqueId())) {
                        titulo(p, "§4§lFASE 3: CHEFE DE HORDA", "§7Derrube o gigante para concluir a missão");
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.7f);
                    }
                }
            }
            case CHEFE -> {
                if (chefe == null || chefe.isDead() || !chefe.isValid()) {
                    acabar(true, "§7O Chefe de Horda caiu.");
                    return;
                }
                barra.name(Tiro.txt("§4§lCHEFE DE HORDA §8| §c" + (int) chefe.getHealth() + " ❤ §8| §7fichas " + fichas));
                barra.progress((float) Math.max(0, Math.min(1, chefe.getHealth() / Math.max(1, chefeVida))));
            }
            default -> { }
        }
    }

    private void acabar(boolean vitoria, String sub) {
        fase = Fase.FIM;
        t = 10;
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) z.remove();
        for (Player p : todos()) {
            UUID u = p.getUniqueId();
            if (!noJogo.contains(u)) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) {
                p.setGameMode(GameMode.SURVIVAL);
                p.teleport(inicio(mapa, 0));
            }
            int pts = pontos.getOrDefault(u, 0);
            int ganho = (int) Math.round((pts + (vitoria ? 60 : 10)) * (0.8 + dif * 0.35));
            int antes = xp(u), depois = antes + ganho;
            pl.dados.set("operacao.xp." + u, depois);
            pl.dados.set("operacao.nome." + u, p.getName());
            if (vitoria) pl.dados.set("operacao.vitorias." + u, pl.dados.getInt("operacao.vitorias." + u, 0) + 1);
            titulo(p, vitoria ? "§a§lMISSÃO CUMPRIDA" : "§c§lMISSÃO FRACASSADA", sub);
            p.playSound(p.getLocation(), vitoria ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 0.7f, 1f);
            p.sendMessage(PREFIXO + (vitoria ? "§aMissão cumprida em " + MAPAS[mapa] + "! " : "§cMissão fracassada. ") + "§7Pontos: §f" + pts + " §8| §7XP: §f+" + ganho + " §8(" + depois + ")");
            if (patente(depois) > patente(antes)) {
                p.sendMessage(PREFIXO + "§e§lPROMOÇÃO! §7Você agora é §6" + PATENTES[patente(depois)] + "§7 (mais munição no começo de cada missão).");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
            }
            pl.coronas(p, vitoria ? PREMIO[dif] : 10, vitoria ? "pela missão cumprida" : "pela tentativa");
        }
        pl.salvar();
        pl.getLogger().info("Operacao: fim da missao (" + (vitoria ? "vitoria" : "derrota") + ") em " + MAPAS[mapa]);
    }

    // ------------------------------------------------------------------ avisos das armas e dos infectados

    void acertou(Player a, LivingEntity alvo, double dano, boolean cabeca) {
        if (cabeca && noJogo.contains(a.getUniqueId())) pontos.merge(a.getUniqueId(), 1, Integer::sum);
    }

    void morreu(Zombie z, Zumbis.Tipo t, Player matador) {
        if (matador == null || !noJogo.contains(matador.getUniqueId())) return;
        pontos.merge(matador.getUniqueId(), t.pontos * 2, Integer::sum);
        if (rnd.nextInt(100) < 30) {
            Armas.Arma a = pl.armas.armaDe(matador.getInventory().getItem(0));
            if (a != null) {
                matador.getInventory().addItem(pl.armas.municao(a.municao(), 6 + rnd.nextInt(8)));
                matador.playSound(matador.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
            }
        }
    }

    // ================================================================== eventos

    @EventHandler(priority = EventPriority.HIGH)
    public void habilidade(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            if (e.getClickedBlock() != null) e.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        ItemStack it = p.getInventory().getItemInMainHand();
        String id = pl.armas.idItem(it);
        if (e.getClickedBlock() != null && pl.armas.armaDe(it) == null) e.setUseInteractedBlock(Event.Result.DENY);
        if ("menu".equals(id)) {
            e.setCancelled(true);
            abrirMenu(p);
            return;
        }
        if ("bandagem".equals(id)) {
            e.setCancelled(true);
            if (p.getHealth() >= p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) return;
            it.setAmount(it.getAmount() - 1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true, true));
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
            return;
        }
        if (!"habilidade".equals(id) || !noJogo.contains(p.getUniqueId())) return;
        e.setCancelled(true);
        long agora = System.currentTimeMillis(), ate = habilidadeAte.getOrDefault(p.getUniqueId(), 0L);
        if (agora < ate) {
            p.sendActionBar(Tiro.txt("§7Habilidade recarregando: §f" + (ate - agora) / 1000 + "s"));
            return;
        }
        Classe c = classeDe(p);
        habilidadeAte.put(p.getUniqueId(), agora + (c.id().equals("fuzileiro") ? 45000 : c.id().equals("engenheiro") ? 40000 : 30000));
        List<Player> perto = new ArrayList<>();
        for (Player o : vivos()) if (o.getLocation().distanceSquared(p.getLocation()) < 10 * 10) perto.add(o);
        switch (c.id()) {
            case "fuzileiro" -> {
                for (Player o : perto) {
                    Armas.Arma a = pl.armas.armaDe(o.getInventory().getItem(0));
                    if (a != null) o.getInventory().addItem(pl.armas.municao(a.municao(), Math.min(64, a.pente() * 2)));
                    o.getInventory().addItem(pl.armas.municao("leve", 24));
                }
                mundo.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 0.8f);
            }
            case "medico" -> {
                for (Player o : perto) {
                    o.setHealth(Math.min(o.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), o.getHealth() + 10));
                    o.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true, true));
                    mundo.spawnParticle(Particle.HEART, o.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3);
                }
                mundo.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
            }
            case "engenheiro" -> {
                for (Player o : perto) o.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 1, false, true, true));
                mundo.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
            }
            default -> {
                for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) if (pl.zumbis.tipo(z) != null) z.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
                mundo.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
            }
        }
        for (Player o : perto) o.sendActionBar(Tiro.txt(c.habilidade() + " §7de §f" + p.getName()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!aqui(p)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        if (!noJogo.contains(p.getUniqueId()) || fase == Fase.FIM) return;
        if (fichas > 0) {
            fichas--;
            caidoAte.put(p.getUniqueId(), 12);
            for (Player o : todos()) o.sendMessage(PREFIXO + "§c" + p.getName() + " §7caiu! Volta em 12s. Fichas do esquadrão: §f" + fichas);
        } else {
            noJogo.remove(p.getUniqueId());
            for (Player o : todos()) o.sendMessage(PREFIXO + "§c" + p.getName() + " §7caiu e não há mais fichas.");
        }
    }

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p)) return;
        boolean volta = caidoAte.containsKey(p.getUniqueId());
        e.setRespawnLocation(volta || noJogo.contains(p.getUniqueId()) ? inicio(mapa, 0).add(0, 6, 0) : sala());
        if (volta) Bukkit.getScheduler().runTask(pl, () -> p.setGameMode(GameMode.SPECTATOR));
        else Bukkit.getScheduler().runTask(pl, () -> {
            if (aqui(p)) itensDaSala(p);
        });
    }

    /** Sem fogo amigo e sem dano na sala de espera. */
    @EventHandler(priority = EventPriority.LOW)
    public void dano(org.bukkit.event.entity.EntityDamageEvent e) {
        if (mundo == null || e.getEntity().getWorld() != mundo || !(e.getEntity() instanceof Player p)) return;
        if (!noJogo.contains(p.getUniqueId()) || fase == Fase.FIM || fase == Fase.ESPERA || fase == Fase.CONTAGEM) e.setCancelled(true);
        else if (e instanceof EntityDamageByEntityEvent ev && (ev.getDamager() instanceof Player || (ev.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player))) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (mundo != null && e.getEntity().getWorld() == mundo) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quebrar(BlockBreakEvent e) {
        if (mundo != null && e.getBlock().getWorld() == mundo && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void colocar(BlockPlaceEvent e) {
        if (mundo != null && e.getBlock().getWorld() == mundo && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void largar(PlayerDropItemEvent e) {
        if (aqui(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void nascer(CreatureSpawnEvent e) {
        if (mundo != null && e.getLocation().getWorld() == mundo && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler
    public void mudou(PlayerChangedWorldEvent e) {
        if (mundo == null || e.getFrom() != mundo) return;
        Player p = e.getPlayer();
        noJogo.remove(p.getUniqueId());
        caidoAte.remove(p.getUniqueId());
        p.hideBossBar(barra);
        p.getInventory().clear();
        p.removePotionEffect(PotionEffectType.RESISTANCE);
        p.removePotionEffect(PotionEffectType.ABSORPTION);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        if (aqui(e.getPlayer())) tirar(e.getPlayer(), false);
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p)) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline() && aqui(p)) tirar(p, false);
        }, 20L);
    }
}
