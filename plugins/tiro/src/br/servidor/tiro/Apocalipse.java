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
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * APOCALIPSE ZUMBI (15/11): mundo survival aberto so dele (mochila propria, separada do resto do servidor).
 * O jogador nasce no Abrigo (area segura com o Mercador), sai para saquear caixas de suprimento espalhadas pelo mapa
 * (voltam a encher depois de um tempo), mata infectados com as armas, junta sucata para comprar municao e armas,
 * constroi a base dele e protege o terreno com a pa de ouro (GriefPrevention). Quanto mais longe do Abrigo, pior:
 * Suburbio -> Cidade Morta -> Zona Toxica -> Marco Zero, cada area com os seus infectados. Cair de alto pode quebrar
 * a perna (cura com tala); bandagem recupera vida.
 */
final class Apocalipse implements Listener {

    static final String PREFIXO = "§8[§2§lApocalipse§8] §7";
    static final String[] ZONAS = {"§aSubúrbio", "§eCidade Morta", "§6Zona Tóxica", "§4§lMarco Zero"};
    static final String[] ZONA_SUB = {"§7Poucos infectados. Bom para começar.", "§7Corredores e cuspidores. Saque melhor.", "§7Tanques e explosivos. Venha armado.", "§7Tudo junto. Chefes de horda rondam aqui."};
    static final int[] ZONA_ATE = {250, 600, 1000};

    final Tiro pl;
    World mundo;
    Location abrigo;
    final NamespacedKey kSaque, kPerna, kMercador;
    private final Random rnd = new Random();
    private final Map<UUID, Integer> zonaDe = new HashMap<>();
    private final Map<UUID, Long> ultimaBandagem = new HashMap<>();
    private final Set<String> saques = new HashSet<>();

    static final class Loja implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    record Oferta(String id, int qtd, int preco) {}

    static final Oferta[] OFERTAS = {
            new Oferta("m:leve", 16, 4), new Oferta("m:pesada", 16, 6), new Oferta("m:cartucho", 8, 6), new Oferta("i:bandagem", 1, 3),
            new Oferta("i:tala", 1, 4), new Oferta("i:pao", 4, 2), new Oferta("i:faca", 1, 10), new Oferta("a:pistola", 1, 15),
            new Oferta("a:smg", 1, 45), new Oferta("a:escopeta", 1, 50), new Oferta("a:fuzil", 1, 80), new Oferta("a:sniper", 1, 120),
            new Oferta("a:metralhadora", 1, 160)};

    Apocalipse(Tiro pl) {
        this.pl = pl;
        kSaque = new NamespacedKey(pl, "saque");
        kPerna = new NamespacedKey(pl, "perna_quebrada");
        kMercador = new NamespacedKey(pl, "mercador");
        criarMundo();
        saques.addAll(pl.dados.getStringList("zumbi.saques"));
        Bukkit.getScheduler().runTaskTimer(pl, this::ciclo, 100L, 60L);
    }

    void desligar() {
        pl.dados.set("zumbi.saques", new ArrayList<>(saques));
    }

    // ================================================================== mundo e Abrigo

    private void criarMundo() {
        mundo = new WorldCreator(Tiro.ZUMBI).seed(pl.getConfig().getLong("zumbi.semente", 20261115L)).environment(World.Environment.NORMAL).createWorld();
        if (mundo == null) {
            pl.getLogger().severe("Nao consegui criar o mundo do Apocalipse Zumbi!");
            return;
        }
        mundo.setDifficulty(org.bukkit.Difficulty.NORMAL);
        mundo.setGameRule(GameRules.KEEP_INVENTORY, true);
        mundo.setGameRule(GameRules.SPAWN_MONSTERS, false);       // os infectados sao nossos
        mundo.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        mundo.setGameRule(GameRules.SPAWN_PATROLS, false);
        mundo.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        mundo.setGameRule(GameRules.SPAWN_WARDENS, false);
        mundo.setGameRule(GameRules.MOB_GRIEFING, false);
        mundo.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        mundo.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        mundo.setGameRule(GameRules.LOCATOR_BAR, false);
        mundo.setGameRule(GameRules.PVP, pl.getConfig().getBoolean("zumbi.pvp", false));
        String s = pl.getConfig().getString("zumbi.abrigo");
        if (s != null) {
            String[] a = s.split(",");
            abrigo = new Location(mundo, Integer.parseInt(a[0]) + 0.5, Integer.parseInt(a[1]), Integer.parseInt(a[2]) + 0.5);
        } else {
            abrigo = acharTerra();
            construirAbrigo();
            pl.getConfig().set("zumbi.abrigo", abrigo.getBlockX() + "," + abrigo.getBlockY() + "," + abrigo.getBlockZ());
            pl.saveConfig();
        }
        mundo.setSpawnLocation(abrigo);
        mundo.getWorldBorder().setCenter(abrigo);
        mundo.getWorldBorder().setSize(2.0 * pl.getConfig().getInt("zumbi.borda", 1500));
        mercador();
    }

    /** Procura terra firme perto do 0,0 (em espiral de 48 em 48 blocos). */
    private Location acharTerra() {
        for (int raio = 0; raio <= 480; raio += 48)
            for (int x = -raio; x <= raio; x += 48)
                for (int z = -raio; z <= raio; z += 48) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != raio) continue;
                    Block b = mundo.getHighestBlockAt(x, z);
                    Material m = b.getType();
                    if (m == Material.WATER || m == Material.LAVA || m == Material.ICE || m.name().contains("LEAVES") || b.getY() < 62 || b.getY() > 110) continue;
                    return new Location(mundo, x + 0.5, b.getY() + 1, z + 0.5);
                }
        Block b = mundo.getHighestBlockAt(0, 0);
        return new Location(mundo, 0.5, Math.max(64, b.getY() + 1), 0.5);
    }

    private void b(int x, int y, int z, Material m) {
        mundo.getBlockAt(x, y, z).setType(m, false);
    }

    /** Forte de pedra 25 x 25 com 4 portoes, torres nos cantos, fogueira no meio e a banca do Mercador. */
    private void construirAbrigo() {
        int cx = abrigo.getBlockX(), cy = abrigo.getBlockY(), cz = abrigo.getBlockZ(), R = 12;
        for (int x = -R - 2; x <= R + 2; x++)
            for (int z = -R - 2; z <= R + 2; z++) {
                for (int y = cy; y <= cy + 14; y++) b(cx + x, y, cz + z, Material.AIR);                 // limpa em cima
                for (int y = cy - 1; y >= cy - 9; y--) {                                                // fundacao
                    Block bl = mundo.getBlockAt(cx + x, y, cz + z);
                    if (y < cy - 1 && bl.getType().isSolid() && !bl.getType().name().contains("LEAVES") && !bl.getType().name().contains("LOG")) break;
                    bl.setType(y == cy - 1 ? ((x + z) % 5 == 0 ? Material.GRAVEL : Material.COARSE_DIRT) : Material.STONE, false);
                }
                boolean borda = Math.abs(x) == R || Math.abs(z) == R, dentro = Math.abs(x) <= R && Math.abs(z) <= R;
                if (!dentro) continue;
                boolean portao = (Math.abs(x) <= 1 && Math.abs(z) == R) || (Math.abs(z) <= 1 && Math.abs(x) == R);
                if (borda && !portao) for (int y = 0; y < 4; y++) b(cx + x, cy + y, cz + z, y == 3 && (x + z) % 2 == 0 ? Material.STONE_BRICK_SLAB : (x * 7 + z * 3 + y) % 6 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                if (borda && portao) b(cx + x, cy + 3, cz + z, Material.SPRUCE_PLANKS);
                if (Math.abs(x) >= R - 1 && Math.abs(z) >= R - 1) {                                     // torres
                    for (int y = 0; y < 6; y++) b(cx + x, cy + y, cz + z, Material.DEEPSLATE_BRICKS);
                    if (Math.abs(x) == R - 1 && Math.abs(z) == R - 1) b(cx + x, cy + 6, cz + z, Material.LANTERN);
                }
            }
        b(cx, cy, cz, Material.CAMPFIRE);
        for (int[] c : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) b(cx + c[0], cy, cz + c[1], Material.SPRUCE_STAIRS);
        // banca do mercador (noroeste) e caixotes
        for (int x = -9; x <= -6; x++) {
            b(cx + x, cy, cz - 8, Material.BARREL);
            b(cx + x, cy + 3, cz - 8, Material.GREEN_WOOL);
            b(cx + x, cy + 3, cz - 9, Material.WHITE_WOOL);
        }
        for (int[] c : new int[][] {{-10, -8}, {-5, -8}, {-10, -10}, {-5, -10}}) for (int y = 0; y < 3; y++) b(cx + c[0], cy + y, cz + c[1], Material.SPRUCE_FENCE);
        for (int[] c : new int[][] {{8, 8}, {9, 8}, {8, 9}, {7, -9}, {8, -9}, {-8, 8}}) b(cx + c[0], cy, cz + c[1], Material.BARREL);
        b(cx + 8, cy + 1, cz + 8, Material.BARREL);
        b(cx + 6, cy, cz + 9, Material.CRAFTING_TABLE);
        b(cx + 5, cy, cz + 9, Material.FURNACE);
        pl.getLogger().info("Abrigo do Apocalipse construido em " + cx + "," + cy + "," + cz + ".");
    }

    private void mercador() {
        Location l = abrigo.clone().add(-7.5 + 0.5, 0, -9.5 + 0.5);
        l.getChunk().load(true);
        for (Entity e : mundo.getNearbyEntities(abrigo, 16, 8, 16)) if (e.getPersistentDataContainer().has(kMercador)) e.remove();
        l.setYaw(0f);
        mundo.spawn(l, Villager.class, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setProfession(Villager.Profession.WEAPONSMITH);
            v.customName(Tiro.txt("§e§lMercador do Abrigo §7(clique)"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(kMercador, PersistentDataType.BYTE, (byte) 1);
        });
    }

    boolean aqui(Player p) {
        return mundo != null && p.getWorld() == mundo;
    }

    boolean noAbrigo(Location l) {
        int r = pl.getConfig().getInt("zumbi.abrigo-raio", 34);
        return l.getWorld() == mundo && Math.abs(l.getX() - abrigo.getX()) <= r && Math.abs(l.getZ() - abrigo.getZ()) <= r;
    }

    int zona(Location l) {
        double d = Math.hypot(l.getX() - abrigo.getX(), l.getZ() - abrigo.getZ());
        for (int i = 0; i < ZONA_ATE.length; i++) if (d < ZONA_ATE[i]) return i;
        return 3;
    }

    // ================================================================== itens

    ItemStack sucata(int n) {
        return pl.armas.item("sucata", Material.PRISMARINE_SHARD, "§bSucata", n, "§7Moeda do Apocalipse.", "§7Troque com o Mercador do Abrigo.");
    }

    ItemStack coisa(String id, int n) {
        return switch (id) {
            case "bandagem" -> pl.armas.item("bandagem", Material.PAPER, "§fBandagem", n, "§7Botão direito: recupera vida aos poucos.");
            case "tala" -> pl.armas.item("tala", Material.STICK, "§eTala", n, "§7Botão direito: conserta a perna quebrada.");
            case "pao" -> new ItemStack(Material.BREAD, n);
            case "faca" -> {
                ItemStack it = pl.armas.item("faca", Material.IRON_SWORD, "§7Faca de Combate", 1, "§7Silenciosa. Não gasta munição.");
                ItemMeta m = it.getItemMeta();
                m.setUnbreakable(true);
                it.setItemMeta(m);
                yield it;
            }
            default -> sucata(n);
        };
    }

    private ItemStack daOferta(Oferta o) {
        String[] p = o.id().split(":");
        return switch (p[0]) {
            case "m" -> pl.armas.municao(p[1], o.qtd());
            case "a" -> pl.armas.arma(p[1]);
            default -> coisa(p[1], o.qtd());
        };
    }

    private void dar(Player p, ItemStack it) {
        for (ItemStack sobra : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
    }

    private int contar(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (id.equals(pl.armas.idItem(it))) n += it.getAmount();
        return n;
    }

    private void tirar(Player p, String id, int quer) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length && quer > 0; i++) {
            if (!id.equals(pl.armas.idItem(c[i]))) continue;
            int k = Math.min(quer, c[i].getAmount());
            quer -= k;
            if (k == c[i].getAmount()) p.getInventory().setItem(i, null);
            else c[i].setAmount(c[i].getAmount() - k);
        }
    }

    // ================================================================== entrar e sair

    void entrar(Player p) {
        if (mundo == null) {
            p.sendMessage(PREFIXO + "§cO Apocalipse está indisponível agora.");
            return;
        }
        if (aqui(p)) {
            p.sendMessage(PREFIXO + "Você já está no Apocalipse. §f/zumbi abrigo §7volta ao Abrigo, §f/zumbi sair §7sai.");
            return;
        }
        String w = p.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        if (Set.of("vovo", "slender", "backrooms", "hotel", "defesa", "login", "naochore", "turno_noite", "fabrica", "arena", "esconde", "porteiro", Tiro.OPERACAO).contains(w)) {
            p.sendMessage(PREFIXO + "§cSaia do outro jogo primeiro.");
            return;
        }
        Location l = abrigo.clone().add(0, 0, 4);
        String s = pl.dados.getString("zumbi.loc." + p.getUniqueId());
        if (s != null) {
            try {
                String[] a = s.split(",");
                l = new Location(mundo, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Float.parseFloat(a[3]), 0f);
            } catch (Exception ignorado) {
                // fica no Abrigo
            }
        }
        p.teleport(l);
        p.setFallDistance(0);
        p.showTitle(Title.title(Tiro.txt("§2§lAPOCALIPSE ZUMBI"), Tiro.txt("§7Saqueie, construa, sobreviva. O Abrigo é seguro.")));
        // o kit vem depois que a mochila deste mundo abrir
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline() || !aqui(p)) return;
            if (!pl.dados.getBoolean("zumbi.kit." + p.getUniqueId(), false)) {
                pl.dados.set("zumbi.kit." + p.getUniqueId(), true);
                dar(p, pl.armas.arma("pistola"));
                dar(p, pl.armas.municao("leve", 36));
                dar(p, coisa("faca", 1));
                dar(p, coisa("bandagem", 3));
                dar(p, coisa("tala", 1));
                dar(p, new ItemStack(Material.BREAD, 6));
                dar(p, new ItemStack(Material.GOLDEN_SHOVEL));
                p.sendMessage(PREFIXO + "§aKit de sobrevivente entregue: §fpistola, munição, faca, bandagens, tala, pão e a pá de ouro §7(para proteger o seu terreno).");
            }
            p.sendMessage(PREFIXO + "§fBotão direito §7atira, §fesquerdo no ar ou Q §7recarrega. Mate infectados e abra §fbarris de suprimento §7para juntar §bsucata§7.");
            p.sendMessage(PREFIXO + "§7Quanto mais longe do Abrigo, mais perigoso e melhor o saque. §f/zumbi abrigo §8| §f/zumbi loja §8| §f/zumbi sair");
        }, 12L);
    }

    void sair(Player p) {
        if (!aqui(p)) {
            pl.irLobby(p);
            return;
        }
        if (infectadoPerto(p, 12)) {
            p.sendMessage(PREFIXO + "§cTem infectado perto demais para sair agora!");
            return;
        }
        Location l = p.getLocation();
        pl.dados.set("zumbi.loc." + p.getUniqueId(), l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw());
        pl.irLobby(p);
    }

    private boolean infectadoPerto(Player p, double raio) {
        for (Entity e : p.getNearbyEntities(raio, raio, raio)) if (pl.zumbis.tipo(e) != null) return true;
        return false;
    }

    boolean comando(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "entrar" : a[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("top")) {
            List<String[]> l = new ArrayList<>();
            var sec = pl.dados.getConfigurationSection("zumbi.nome");
            if (sec != null) for (String u : sec.getKeys(false)) l.add(new String[] {sec.getString(u), String.valueOf(pl.dados.getInt("zumbi.abates." + u, 0))});
            l.sort((x, y) -> Integer.compare(Integer.parseInt(y[1]), Integer.parseInt(x[1])));
            s.sendMessage(PREFIXO + "§e§lMaiores caçadores de infectados");
            if (l.isEmpty()) s.sendMessage("§7Ninguém ainda. Seja o primeiro!");
            for (int i = 0; i < Math.min(10, l.size()); i++) s.sendMessage("§e" + (i + 1) + ". §f" + l.get(i)[0] + " §7— §c" + l.get(i)[1] + " abates");
            return true;
        }
        if (sub.equals("admin") && s.hasPermission("tiro.admin")) {
            String o = a.length > 1 ? a[1] : "";
            Player alvo = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player sp ? sp : null);
            if (o.equals("nascer") && alvo != null && aqui(alvo)) {
                Zumbis.Tipo t = a.length > 3 ? Zumbis.Tipo.valueOf(a[3].toUpperCase()) : Zumbis.Tipo.COMUM;
                Zombie z = pl.zumbis.nascer(alvo.getLocation().add(4, 0, 0), t, 1.0);
                z.setTarget(alvo);
                s.sendMessage(PREFIXO + "nasceu " + t);
            } else if (o.equals("dar") && alvo != null) {
                for (String id : Armas.ARMAS.keySet()) dar(alvo, pl.armas.arma(id));
                for (String id : Armas.MUNICOES.keySet()) dar(alvo, pl.armas.municao(id, 64));
                dar(alvo, sucata(64));
            } else if (o.equals("atirar") && alvo != null) {   // teste: simula o botao direito com o item da mao
                int n = a.length > 3 ? Integer.parseInt(a[3]) : 1;
                for (int i = 0; i < n; i++) Bukkit.getScheduler().runTaskLater(pl, () -> Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(alvo, Action.RIGHT_CLICK_AIR,
                        alvo.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND)), i * 8L);
            } else if (o.equals("saque") && alvo != null && aqui(alvo)) {
                ruina(alvo.getLocation().getBlockX() + 3, alvo.getLocation().getBlockZ() + 3);
            } else s.sendMessage(PREFIXO + "abrigo=" + abrigo.getBlockX() + "," + abrigo.getBlockY() + "," + abrigo.getBlockZ() + " jogadores=" + mundo.getPlayers().size()
                        + " infectados=" + contarInfectados() + " saques=" + saques.size() + " | admin <nascer|dar|saque> [jogador] [tipo]");
            return true;
        }
        if (!(s instanceof Player p)) return true;
        switch (sub) {
            case "sair" -> sair(p);
            case "abrigo" -> {
                if (!aqui(p)) entrar(p);
                else if (infectadoPerto(p, 16)) p.sendMessage(PREFIXO + "§cTem infectado perto demais! Afaste-se ou acabe com eles primeiro.");
                else {
                    p.teleport(abrigo.clone().add(0, 0, 4));
                    p.sendMessage(PREFIXO + "§aVocê voltou ao Abrigo.");
                }
            }
            case "loja" -> {
                if (aqui(p) && noAbrigo(p.getLocation())) abrirLoja(p);
                else p.sendMessage(PREFIXO + "§7A loja é com o §eMercador§7, dentro do Abrigo (§f/zumbi abrigo§7).");
            }
            default -> entrar(p);
        }
        return true;
    }

    private int contarInfectados() {
        int n = 0;
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) if (pl.zumbis.tipo(z) != null) n++;
        return n;
    }

    // ================================================================== ciclo: infectados, saques, areas, perna

    private Zumbis.Tipo sortear(int zona) {
        int[][] pesos = {{80, 20, 0, 0, 0}, {50, 30, 0, 20, 0}, {30, 22, 14, 18, 16}, {20, 22, 20, 18, 20}};
        int[] p = pesos[zona];
        int k = rnd.nextInt(100), s = 0;
        Zumbis.Tipo[] t = {Zumbis.Tipo.COMUM, Zumbis.Tipo.CORREDOR, Zumbis.Tipo.TANQUE, Zumbis.Tipo.CUSPIDOR, Zumbis.Tipo.EXPLOSIVO};
        for (int i = 0; i < p.length; i++) {
            s += p[i];
            if (k < s) return t[i];
        }
        return Zumbis.Tipo.COMUM;
    }

    private void ciclo() {
        if (mundo == null) return;
        List<Player> ps = new ArrayList<>();
        for (Player p : mundo.getPlayers()) if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR) ps.add(p);
        // limpa infectado longe de todo mundo
        for (Zombie z : mundo.getEntitiesByClass(Zombie.class)) {
            if (pl.zumbis.tipo(z) == null) continue;
            boolean perto = false;
            for (Player p : ps) if (p.getLocation().distanceSquared(z.getLocation()) < 72 * 72) perto = true;
            if (!perto || noAbrigo(z.getLocation())) z.remove();
        }
        if (ps.isEmpty()) return;
        boolean noite = mundo.getTime() > 12800 && mundo.getTime() < 23200;
        for (Player p : ps) {
            Location l = p.getLocation();
            int z = zona(l);
            Integer antes = zonaDe.put(p.getUniqueId(), z);
            if (antes != null && antes != z)
                p.showTitle(Title.title(Tiro.txt(ZONAS[z]), Tiro.txt(ZONA_SUB[z]), Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(600))));
            // cinzas no ar; na Zona Toxica em diante o ceu fecha
            p.spawnParticle(Particle.WHITE_ASH, l.clone().add(0, 3, 0), 60, 9, 4, 9, 0);
            if (z >= 2) p.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
            else p.resetPlayerWeather();
            // perna quebrada
            if (p.getPersistentDataContainer().has(kPerna)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, false, true));
                p.sendActionBar(Tiro.txt("§c§lPERNA QUEBRADA §7— use uma §eTala§7 (o Mercador vende)"));
            }
            saquePerto(l);
            if (p.getGameMode() == GameMode.CREATIVE || noAbrigo(l)) continue;
            // infectados em volta
            int tem = 0;
            boolean chefePerto = false;
            for (Entity e : p.getNearbyEntities(46, 30, 46)) {
                Zumbis.Tipo t = pl.zumbis.tipo(e);
                if (t == null) continue;
                tem++;
                if (t == Zumbis.Tipo.CHEFE) chefePerto = true;
            }
            int max = (noite ? pl.getConfig().getInt("zumbi.zumbis-de-noite", 11) : pl.getConfig().getInt("zumbi.zumbis-de-dia", 5)) + z;
            for (int i = 0; i < 2 && tem < max; i++) {
                Location onde = lugar(l);
                if (onde == null) continue;
                Zumbis.Tipo t = sortear(z);
                if (!chefePerto && ((z == 3 && rnd.nextInt(100) < 3) || (z == 2 && noite && rnd.nextInt(200) < 3))) {
                    t = Zumbis.Tipo.CHEFE;
                    chefePerto = true;
                    for (Player o : ps) if (o.getLocation().distanceSquared(onde) < 120 * 120) {
                        o.showTitle(Title.title(Tiro.txt("§4§lCHEFE DE HORDA"), Tiro.txt("§7Ele sentiu o seu cheiro...")));
                        o.playSound(o.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.5f);
                    }
                }
                Zombie zu = pl.zumbis.nascer(onde, t, 1.0 + 0.15 * z);
                zu.setTarget(p);
                tem++;
            }
        }
    }

    /** Um lugar na superficie entre 20 e 34 blocos do jogador, fora do Abrigo e fora da agua. */
    private Location lugar(Location l) {
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 20 + rnd.nextDouble() * 14;
            int x = (int) Math.floor(l.getX() + Math.cos(a) * d), z = (int) Math.floor(l.getZ() + Math.sin(a) * d);
            if (!mundo.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = mundo.getHighestBlockAt(x, z);
            Material m = b.getType();
            if (m == Material.WATER || m == Material.LAVA || !m.isSolid()) continue;
            Location onde = new Location(mundo, x + 0.5, b.getY() + 1, z + 0.5);
            if (noAbrigo(onde) || Math.abs(onde.getY() - l.getY()) > 22) continue;
            return onde;
        }
        return null;
    }

    // ------------------------------------------------------------------ saques (ruinas com barril de suprimento)

    /** Cada quadrado de 96 x 96 tem um ponto de saque; a ruina nasce quando alguem chega perto pela primeira vez. */
    private void saquePerto(Location l) {
        int rx = Math.floorDiv(l.getBlockX(), 96), rz = Math.floorDiv(l.getBlockZ(), 96);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int qx = rx + dx, qz = rz + dz;
                String k = qx + "," + qz;
                if (saques.contains(k)) continue;
                Random r = new Random(qx * 73856093L ^ qz * 19349663L ^ 20261115L);
                int x = qx * 96 + 12 + r.nextInt(72), z = qz * 96 + 12 + r.nextInt(72);
                if (Math.hypot(x - l.getX(), z - l.getZ()) > 56 || !mundo.isChunkLoaded(x >> 4, z >> 4)) continue;
                saques.add(k);
                if (noAbrigo(new Location(mundo, x, 0, z))) continue;
                ruina(x, z);
            }
    }

    private void ruina(int x, int z) {
        Block topo = mundo.getHighestBlockAt(x, z);
        Material mt = topo.getType();
        if (mt == Material.WATER || mt == Material.LAVA) return;
        int y = topo.getY() + 1;
        while (y > 50 && (mundo.getBlockAt(x, y - 1, z).getType().name().contains("LEAVES") || mundo.getBlockAt(x, y - 1, z).getType().name().contains("LOG"))) y--;
        Random r = new Random(x * 31L + z);
        Material[] par = {Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS};
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean borda = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                Block chao = mundo.getBlockAt(x + dx, y - 1, z + dz);
                if (!chao.getType().isSolid()) chao.setType(Material.COARSE_DIRT, false);
                else if (r.nextInt(3) == 0) chao.setType(r.nextBoolean() ? Material.GRAVEL : Material.COARSE_DIRT, false);
                if (!borda || (dx == 0 && dz == 3)) continue;
                int h = r.nextInt(4);                                  // parede quebrada: 0 a 3 de altura
                for (int k = 0; k < h; k++) mundo.getBlockAt(x + dx, y + k, z + dz).setType(par[r.nextInt(par.length)], false);
            }
        mundo.getBlockAt(x + 1, y, z - 1).setType(Material.COBWEB, false);
        mundo.getBlockAt(x - 2, y, z + 1).setType(Material.CAMPFIRE, false);
        if (mundo.getBlockAt(x - 2, y, z + 1).getBlockData() instanceof org.bukkit.block.data.Lightable li) {
            li.setLit(false);
            mundo.getBlockAt(x - 2, y, z + 1).setBlockData(li, false);
        }
        Block bb = mundo.getBlockAt(x, y, z);
        bb.setType(Material.BARREL, false);
        if (bb.getState() instanceof Barrel ba) {
            ba.getPersistentDataContainer().set(kSaque, PersistentDataType.LONG, 0L);
            ba.customName(Tiro.txt("§eSuprimentos"));
            ba.update(true, false);
        }
    }

    private void saquear(Player p, Barrel ba) {
        long agora = System.currentTimeMillis(), ultimo = ba.getPersistentDataContainer().getOrDefault(kSaque, PersistentDataType.LONG, 0L);
        long volta = pl.getConfig().getLong("zumbi.saque-volta-minutos", 20) * 60000L;
        if (agora - ultimo < volta) {
            p.sendActionBar(Tiro.txt("§7Vazio. Os suprimentos voltam em §f" + Math.max(1, (volta - (agora - ultimo)) / 60000) + " min§7."));
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 0.8f);
            return;
        }
        ba.getPersistentDataContainer().set(kSaque, PersistentDataType.LONG, agora);
        ba.update(true, false);
        int z = zona(ba.getLocation());
        List<ItemStack> l = new ArrayList<>();
        l.add(sucata(2 + rnd.nextInt(3) + z * 2));
        l.add(pl.armas.municao("leve", 8 + rnd.nextInt(10) + z * 3));
        if (rnd.nextInt(100) < 45 + z * 10) l.add(pl.armas.municao("pesada", 6 + rnd.nextInt(8) + z * 3));
        if (rnd.nextInt(100) < 35 + z * 8) l.add(pl.armas.municao("cartucho", 3 + rnd.nextInt(5) + z));
        if (rnd.nextInt(100) < 55) l.add(coisa("bandagem", 1 + rnd.nextInt(2)));
        if (rnd.nextInt(100) < 25) l.add(coisa("tala", 1));
        if (rnd.nextInt(100) < 60) l.add(new ItemStack(rnd.nextBoolean() ? Material.BREAD : Material.COOKED_BEEF, 2 + rnd.nextInt(3)));
        if (rnd.nextInt(100) < 30) l.add(new ItemStack(rnd.nextBoolean() ? Material.IRON_INGOT : Material.OAK_PLANKS, 3 + rnd.nextInt(8)));
        String[][] armasZona = {{"smg"}, {"smg", "escopeta"}, {"escopeta", "fuzil"}, {"fuzil", "sniper", "metralhadora"}};
        if (rnd.nextInt(100) < 6 + z * 6) l.add(pl.armas.arma(armasZona[z][rnd.nextInt(armasZona[z].length)]));
        for (ItemStack it : l) dar(p, it);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 1f, 1f);
        p.sendMessage(PREFIXO + "§aVocê pegou os suprimentos! §7(" + l.size() + " tipos de item; o barril enche de novo em " + volta / 60000 + " min)");
    }

    // ------------------------------------------------------------------ loja do Mercador

    private void abrirLoja(Player p) {
        Loja h = new Loja();
        h.inv = Bukkit.createInventory(h, 27, Tiro.txt("§8Mercador do Abrigo §7(paga com sucata)"));
        for (int i = 0; i < OFERTAS.length; i++) {
            ItemStack it = daOferta(OFERTAS[i]);
            ItemMeta m = it.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = m.hasLore() ? new ArrayList<>(m.lore()) : new ArrayList<>();
            lore.add(Tiro.txt(""));
            lore.add(Tiro.txt("§bPreço: " + OFERTAS[i].preco() + " sucata"));
            lore.add(Tiro.txt("§eClique para comprar"));
            m.lore(lore);
            it.setItemMeta(m);
            h.inv.setItem(i < 7 ? i + 1 : i + 4, it);
        }
        h.inv.setItem(22, pl.armas.item("saldo", Material.PRISMARINE_SHARD, "§bSua sucata: §f" + contar(p, "sucata"), 1, "§7Mate infectados e abra barris", "§7de suprimento para juntar mais."));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void cliqueLoja(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Loja)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        int s = e.getSlot(), i = s >= 1 && s <= 7 ? s - 1 : s >= 11 && s <= 16 ? s - 4 : -1;
        if (i < 0 || i >= OFERTAS.length) return;
        Oferta o = OFERTAS[i];
        if (contar(p, "sucata") < o.preco()) {
            p.sendMessage(PREFIXO + "§cFalta sucata: custa §b" + o.preco() + "§c e você tem §b" + contar(p, "sucata") + "§c.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        tirar(p, "sucata", o.preco());
        dar(p, daOferta(o));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        abrirLoja(p);
    }

    @EventHandler
    public void cliqueMercador(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(kMercador)) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) abrirLoja(e.getPlayer());
    }

    // ================================================================== eventos do mundo

    void morreu(Zombie z, Zumbis.Tipo t, Player matador) {
        Location l = z.getLocation();
        int[] suc = {1, 1, 4, 2, 2, 20};
        if (rnd.nextInt(100) < 70 || t == Zumbis.Tipo.CHEFE) mundo.dropItemNaturally(l, sucata(suc[t.ordinal()] + rnd.nextInt(2)));
        if (rnd.nextInt(100) < 25) mundo.dropItemNaturally(l, pl.armas.municao(rnd.nextBoolean() ? "leve" : "pesada", 3 + rnd.nextInt(5)));
        if (rnd.nextInt(100) < 5) mundo.dropItemNaturally(l, coisa("bandagem", 1));
        if (matador == null) return;
        String u = matador.getUniqueId().toString();
        int n = pl.dados.getInt("zumbi.abates." + u, 0) + 1;
        pl.dados.set("zumbi.abates." + u, n);
        pl.dados.set("zumbi.nome." + u, matador.getName());
        if (n % 10 == 0) pl.coronas(matador, 5, "por " + n + " infectados abatidos");
        if (t == Zumbis.Tipo.CHEFE) {
            String[] premio = {"fuzil", "sniper", "metralhadora"};
            mundo.dropItemNaturally(l, pl.armas.arma(premio[rnd.nextInt(premio.length)]));
            mundo.dropItemNaturally(l, pl.armas.municao("pesada", 48));
            pl.coronas(matador, 150, "por derrubar um Chefe de Horda!");
            for (Player o : mundo.getPlayers()) o.sendMessage(PREFIXO + "§e" + matador.getName() + " §7derrubou um §4§lChefe de Horda§7!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void usar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!aqui(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getState() instanceof Barrel ba
                && ba.getPersistentDataContainer().has(kSaque)) {
            e.setCancelled(true);
            saquear(p, ba);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        String id = pl.armas.idItem(it);
        if ("bandagem".equals(id)) {
            e.setCancelled(true);
            long agora = System.currentTimeMillis();
            if (agora - ultimaBandagem.getOrDefault(p.getUniqueId(), 0L) < 3000) return;
            if (p.getHealth() >= p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) {
                p.sendActionBar(Tiro.txt("§7Você está com a vida cheia."));
                return;
            }
            ultimaBandagem.put(p.getUniqueId(), agora);
            it.setAmount(it.getAmount() - 1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true, true));
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
            p.sendActionBar(Tiro.txt("§aBandagem aplicada."));
        } else if ("tala".equals(id)) {
            e.setCancelled(true);
            if (!p.getPersistentDataContainer().has(kPerna)) {
                p.sendActionBar(Tiro.txt("§7Sua perna está inteira."));
                return;
            }
            it.setAmount(it.getAmount() - 1);
            p.getPersistentDataContainer().remove(kPerna);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 0.8f);
            p.sendMessage(PREFIXO + "§aPerna imobilizada com a tala. Você voltou a andar normal.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void dano(EntityDamageEvent e) {
        if (mundo == null || e.getEntity().getWorld() != mundo) return;
        if (e.getEntity().getPersistentDataContainer().has(kMercador)) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getEntity() instanceof Player p)) return;
        if (noAbrigo(p.getLocation()) && e.getCause() != EntityDamageEvent.DamageCause.VOID && e.getCause() != EntityDamageEvent.DamageCause.KILL) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getFinalDamage() >= 4 && !p.getPersistentDataContainer().has(kPerna)
                && rnd.nextDouble() < pl.getConfig().getDouble("zumbi.perna-quebrada-chance", 0.35)) {
            p.getPersistentDataContainer().set(kPerna, PersistentDataType.BYTE, (byte) 1);
            p.playSound(p.getLocation(), Sound.ENTITY_SKELETON_HURT, 1f, 0.6f);
            p.showTitle(Title.title(Tiro.txt("§c§lPERNA QUEBRADA"), Tiro.txt("§7Você anda devagar até usar uma §eTala")));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quebrar(BlockBreakEvent e) {
        if (mundo == null || e.getBlock().getWorld() != mundo || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (noAbrigo(e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Tiro.txt("§7O Abrigo é protegido. Construa a sua base lá fora."));
        } else if (e.getBlock().getState() instanceof Barrel ba && ba.getPersistentDataContainer().has(kSaque)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void colocar(BlockPlaceEvent e) {
        if (mundo == null || e.getBlock().getWorld() != mundo || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (noAbrigo(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void morte(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!aqui(p)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        double f = pl.getConfig().getDouble("zumbi.ao-morrer-perde-municao", 0.5);
        if (f > 0) pl.armas.perderMunicao(p, f);
        p.getPersistentDataContainer().remove(kPerna);
        p.sendMessage(PREFIXO + "§cVocê morreu. §7Voltou ao Abrigo com os seus itens" + (f > 0 ? ", mas perdeu parte da munição." : "."));
    }

    @EventHandler
    public void renascer(PlayerRespawnEvent e) {
        if (aqui(e.getPlayer())) e.setRespawnLocation(abrigo.clone().add(0, 0, 4));
    }

    @EventHandler
    public void mudou(PlayerChangedWorldEvent e) {
        if (mundo != null && e.getFrom() == mundo) {
            e.getPlayer().resetPlayerWeather();
            e.getPlayer().removePotionEffect(PotionEffectType.SLOWNESS);
            zonaDe.remove(e.getPlayer().getUniqueId());
        }
    }
}
