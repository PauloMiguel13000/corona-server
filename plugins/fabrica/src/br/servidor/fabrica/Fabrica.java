package br.servidor.fabrica;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Fabrica Alegria: minigame original de fabrica de brinquedos abandonada (1 a 4 jogadores, co-op). Luva de Gancho com
 * mao azul e vermelha, puzzles de energia e caixas, fitas com a historia e a perseguicao do Tamandua Tutu nos dutos.
 * Java e Bedrock.
 */
public final class Fabrica extends JavaPlugin implements Listener {

    static final String MUNDO = "fabrica";
    static final LegacyComponentSerializer LEGADO = LegacyComponentSerializer.legacySection();

    static Component txt(String s) {
        return LEGADO.deserialize(s);
    }

    static boolean ehBedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || p.getName().startsWith(".");
    }

    World mundo;
    Skins skins;
    Guarda guarda;
    NamespacedKey kMao, kSair;
    private final Map<Integer, Planta> plantas = new HashMap<>();
    private final Map<UUID, Partida> porJogador = new HashMap<>();
    private final List<Partida> partidas = new ArrayList<>();
    private File progressoArq;
    private YamlConfiguration progresso;

    // fila (sala de espera de 20 s para juntar ate 4 jogadores)
    private final List<UUID> fila = new ArrayList<>();
    private int filaSeg;
    private BukkitTask filaTarefa;
    private BossBar filaBarra;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kMao = new NamespacedKey(this, "mao");
        kSair = new NamespacedKey(this, "sair");
        progressoArq = new File(getDataFolder(), "progresso.yml");
        progresso = YamlConfiguration.loadConfiguration(progressoArq);
        guarda = new Guarda(new File(getDataFolder(), "guardados"));
        skins = new Skins(this);
        mundo = criarMundo();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Boneco3d.Ouvinte(), this);
        getLogger().info("Fabrica Alegria ligada: /fabrica (mundo " + MUNDO + ", " + getConfig().getInt("instancias", 3) + " instancias).");
    }

    @Override
    public void onDisable() {
        for (Partida p : new ArrayList<>(partidas)) acabou(p, false);
        if (filaBarra != null) filaBarra.removeAll();
    }

    private World criarMundo() {
        World w = Bukkit.getWorld(MUNDO);
        if (w == null) w = new WorldCreator(MUNDO).generator(new ChunkGenerator() {
        }).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
        if (w != null) {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.KEEP_INVENTORY, true);
            w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            w.setGameRule(GameRule.DO_FIRE_TICK, false);
            w.setGameRule(GameRule.MOB_GRIEFING, false);
            w.setGameRule(GameRule.FALL_DAMAGE, false);
            w.setTime(18000);
            w.setStorm(false);
            w.setSpawnLocation(0, Planta.Y + 1, 2);
        }
        return w;
    }

    Planta planta(int i) {
        return plantas.computeIfAbsent(i, k -> {
            Planta pt = new Planta(mundo, k * Planta.ESPACO);
            File flag = new File(getDataFolder(), "planta-" + k + "-v3.txt");
            if (!flag.exists()) {
                long t0 = System.currentTimeMillis();
                pt.construir();
                getLogger().info("Fabrica " + k + " construida em " + (System.currentTimeMillis() - t0) + " ms.");
                try {
                    getDataFolder().mkdirs();
                    java.nio.file.Files.writeString(flag.toPath(), "construida");
                } catch (IOException ignored) {
                }
            }
            return pt;
        });
    }

    Partida partidaDe(Player p) {
        return porJogador.get(p.getUniqueId());
    }

    // ------------------------------------------------------------------ fila e inicio

    void entrarFila(Player p) {
        if (partidaDe(p) != null) {
            p.sendMessage(txt("§7Você já está na fábrica. §f/fabrica sair §7para desistir."));
            return;
        }
        if (fila.contains(p.getUniqueId())) {
            p.sendMessage(txt("§7Você já está na fila. §f/fabrica comecar §7para começar agora."));
            return;
        }
        if (p.getWorld().getName().equals(MUNDO)) return;
        fila.add(p.getUniqueId());
        int max = Math.max(1, Math.min(4, getConfig().getInt("jogadores-max", 4)));
        if (filaTarefa == null) {
            filaSeg = Math.max(5, getConfig().getInt("espera-segundos", 20));
            filaBarra = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
            filaTarefa = Bukkit.getScheduler().runTaskTimer(this, this::tickFila, 0L, 20L);
            Bukkit.broadcast(txt("§6§l» §e" + p.getName() + " §7vai entrar na §6Fábrica Alegria§7! Quer ir junto? §f/fabrica §7(" + filaSeg + " s, até " + max + ")"));
        }
        filaBarra.addPlayer(p);
        p.sendMessage(txt("§6Fábrica Alegria: §7você entrou na fila (" + fila.size() + "/" + max + "). §f/fabrica comecar §7começa agora, §f/fabrica sair §7sai da fila."));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        if (fila.size() >= max) filaSeg = 1;
    }

    private void tickFila() {
        fila.removeIf(u -> Bukkit.getPlayer(u) == null);
        if (fila.isEmpty()) {
            pararFila();
            return;
        }
        filaSeg--;
        if (filaBarra != null) {
            filaBarra.setTitle("§6Fábrica Alegria §7— começa em §f" + Math.max(0, filaSeg) + " s §7(" + fila.size() + " na fila)");
            filaBarra.setProgress(Math.max(0, Math.min(1, filaSeg / 20.0)));
        }
        if (filaSeg <= 0) iniciarFila();
    }

    private void pararFila() {
        if (filaTarefa != null) filaTarefa.cancel();
        filaTarefa = null;
        if (filaBarra != null) filaBarra.removeAll();
        filaBarra = null;
        fila.clear();
    }

    private int instanciaLivre() {
        int n = Math.max(1, getConfig().getInt("instancias", 3));
        for (int i = 0; i < n; i++) {
            final int fi = i;
            if (partidas.stream().noneMatch(x -> x.instancia == fi)) return i;
        }
        return -1;
    }

    void iniciarFila() {
        int inst = instanciaLivre();
        List<Player> ps = new ArrayList<>();
        for (UUID u : fila) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && partidaDe(p) == null) ps.add(p);
        }
        if (ps.isEmpty()) {
            pararFila();
            return;
        }
        if (inst < 0) {
            filaSeg = 10;
            for (Player p : ps) p.sendActionBar(txt("§7Todas as fábricas estão ocupadas... esperando uma vaga."));
            return;
        }
        if (mundo == null) {
            for (Player p : ps) p.sendMessage(txt("§cO mundo da fábrica não carregou."));
            pararFila();
            return;
        }
        pararFila();
        List<Player> dentro = new ArrayList<>();
        for (Player p : ps) {
            try {
                if (!guarda.tem(p)) guarda.guardar(p);
                dentro.add(p);
            } catch (IOException e) {
                p.sendMessage(txt("§cNão deu para guardar seu inventário. Tente de novo."));
                getLogger().warning("Guardar " + p.getName() + ": " + e);
            }
        }
        if (dentro.isEmpty()) return;
        Planta pt = planta(inst);
        Partida pa = new Partida(this, pt, inst, dentro);
        partidas.add(pa);
        for (Player p : dentro) porJogador.put(p.getUniqueId(), pa);
        pa.comecar();
    }

    /** Fim da partida (venceu ou acabou): devolve todo mundo. */
    void acabou(Partida pa, boolean venceu) {
        if (!partidas.remove(pa)) return;
        long seg = pa.segundos();
        for (UUID u : new ArrayList<>(pa.jogadores)) {
            porJogador.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            if (venceu) premiar(p, seg);
            devolver(p);
        }
        pa.limpar();
    }

    private void premiar(Player p, long seg) {
        int moedas = getConfig().getInt("recompensa.vitoria", 150);
        if (moedas > 0) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), getConfig().getString("recompensa.comando", "coronas dar %jogador% %valor%")
                    .replace("%jogador%", p.getName()).replace("%valor%", String.valueOf(moedas)));
        }
        String k = p.getUniqueId().toString();
        long antes = progresso.getLong(k + ".melhor", 0);
        boolean recorde = antes == 0 || seg < antes;
        if (recorde) progresso.set(k + ".melhor", seg);
        progresso.set(k + ".nome", p.getName());
        progresso.set(k + ".vitorias", progresso.getInt(k + ".vitorias", 0) + 1);
        try {
            progresso.save(progressoArq);
        } catch (IOException e) {
            getLogger().warning("Progresso: " + e);
        }
        p.sendMessage(txt("§6§l✔ Fábrica Alegria — Capítulo 1 concluído! §e+" + moedas + " coronas §7| tempo §f" + seg / 60 + ":" + String.format("%02d", seg % 60)
                + (recorde ? " §a(recorde seu!)" : " §7(seu recorde: " + antes / 60 + ":" + String.format("%02d", antes % 60) + ")")));
        Bukkit.broadcast(txt("§6✦ §f" + p.getName() + " §7escapou da §6Fábrica Alegria §7em §f" + seg / 60 + ":" + String.format("%02d", seg % 60) + "§7!"));
    }

    private void devolver(Player p) {
        Guarda.limpar(p);
        if (!guarda.devolver(p, true)) {
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    void sair(Player p, boolean avisar) {
        if (fila.remove(p.getUniqueId())) {
            if (filaBarra != null) filaBarra.removePlayer(p);
            if (avisar) p.sendMessage(txt("§7Você saiu da fila da fábrica."));
            return;
        }
        Partida pa = porJogador.remove(p.getUniqueId());
        if (pa != null) {
            pa.saiu(p);
            devolver(p);
            if (avisar) p.sendMessage(txt("§7Você saiu da Fábrica Alegria."));
            if (pa.online().isEmpty()) acabou(pa, false);
        } else if (p.getWorld().getName().equals(MUNDO)) {
            devolver(p);
        }
    }

    void skinPronta(String id) {
        for (Partida pa : partidas) pa.skinPronta(id);
    }

    // ------------------------------------------------------------------ comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("So no jogo.");
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase();
        switch (sub) {
            case "", "entrar", "jogar" -> entrarFila(p);
            case "sair" -> sair(p, true);
            case "comecar", "começar", "iniciar" -> {
                if (!fila.contains(p.getUniqueId())) {
                    entrarFila(p);
                    if (!fila.contains(p.getUniqueId())) return true;
                }
                filaSeg = 1;
            }
            case "recorde", "recordes" -> {
                String k = p.getUniqueId().toString();
                long m = progresso.getLong(k + ".melhor", 0);
                p.sendMessage(txt(m == 0 ? "§7Você ainda não terminou a fábrica." : "§6Seu recorde: §f" + m / 60 + ":" + String.format("%02d", m % 60)
                        + " §7(" + progresso.getInt(k + ".vitorias", 0) + " vitórias)"));
            }
            case "admin" -> admin(p, a);
            default -> p.sendMessage(txt("§6Fábrica Alegria: §f/fabrica §7(entrar na fila), §f/fabrica comecar§7, §f/fabrica sair§7, §f/fabrica recorde"));
        }
        return true;
    }

    private void admin(Player p, String[] a) {
        if (!p.hasPermission("fabrica.admin")) {
            p.sendMessage(txt("§cSem permissão."));
            return;
        }
        String o = a.length > 1 ? a[1].toLowerCase() : "";
        Partida pa = partidaDe(p);
        switch (o) {
            case "skins" -> {
                p.sendMessage(txt(skins.resumo()));
                skins.gerarFaltando();
            }
            case "conferir" -> {
                // confere a fabrica 0 a pe (busca de caminho) — so com ela livre
                if (partidas.stream().anyMatch(x -> x.instancia == 0)) {
                    p.sendMessage(txt("§cA fábrica 0 está em uso agora."));
                    return;
                }
                for (String l : new Verificador(planta(0)).rodar()) {
                    getLogger().info("[conferir] " + l);
                    p.sendMessage(txt((l.startsWith("OK") ? "§a" : "§c") + l));
                }
            }
            case "codigo" -> {
                if (pa != null) getLogger().info("[admin] senha da portaria (" + p.getName() + "): " + pa.codigo);
                p.sendMessage(txt(pa == null ? "§cEntre numa partida." : "§eSenha: §f" + pa.codigo));
            }
            case "reconstruir" -> {
                for (int i = 0; i < Math.max(1, getConfig().getInt("instancias", 3)); i++) {
                    new File(getDataFolder(), "planta-" + i + "-v3.txt").delete();
                    plantas.remove(i);
                }
                p.sendMessage(txt("§aAs fábricas serão reconstruídas na próxima partida."));
            }
            case "pular" -> {
                // pular para uma parte (teste): portaria, luva, energia, caixas, controle, ponte
                if (pa == null) {
                    p.sendMessage(txt("§cEntre numa partida primeiro."));
                    return;
                }
                String parte = a.length > 2 ? a[2].toLowerCase() : "";
                pa.pularPara(p, parte);
            }
            default -> p.sendMessage(txt("§e/fabrica admin conferir | codigo | skins | reconstruir | pular <portaria|luva|energia|caixas|controle|ponte|final>"));
        }
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.LOW)
    public void interagiu(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Partida pa = partidaDe(p);
        if (pa == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kSair, PersistentDataType.BYTE)) {
            sair(p, true);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getLocation().add(0.5, 0.5, 0.5).distance(p.getEyeLocation()) < 5.5 && pa.clicouBloco(p, e.getClickedBlock())) return;
        Integer mao = it.hasItemMeta() ? it.getItemMeta().getPersistentDataContainer().get(kMao, PersistentDataType.INTEGER) : null;
        if (mao != null) pa.disparou(p, mao);
    }

    @EventHandler
    public void entidade(PlayerInteractEntityEvent e) {
        Partida pa = partidaDe(e.getPlayer());
        if (pa == null) return;
        e.setCancelled(true);
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        Integer mao = it.hasItemMeta() ? it.getItemMeta().getPersistentDataContainer().get(kMao, PersistentDataType.INTEGER) : null;
        if (mao != null && e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) pa.disparou(e.getPlayer(), mao);
    }

    @EventHandler
    public void trocouMao(PlayerSwapHandItemsEvent e) {
        if (partidaDe(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void soltou(PlayerDropItemEvent e) {
        if (partidaDe(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void inventario(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && partidaDe(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void quebrou(BlockBreakEvent e) {
        if (e.getPlayer().getWorld().getName().equals(MUNDO) && !e.getPlayer().hasPermission("fabrica.admin")) e.setCancelled(true);
    }

    @EventHandler
    public void colocou(BlockPlaceEvent e) {
        if (e.getPlayer().getWorld().getName().equals(MUNDO) && !e.getPlayer().hasPermission("fabrica.admin")) e.setCancelled(true);
    }

    @EventHandler
    public void dano(EntityDamageEvent e) {
        if (e.getEntity().getWorld().getName().equals(MUNDO)) e.setCancelled(true);
    }

    @EventHandler
    public void fome(FoodLevelChangeEvent e) {
        if (e.getEntity().getWorld().getName().equals(MUNDO)) e.setCancelled(true);
    }

    @EventHandler
    public void saiuDoServidor(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        fila.remove(p.getUniqueId());
        Partida pa = porJogador.remove(p.getUniqueId());
        if (pa != null) {
            pa.saiu(p);
            devolver(p);
            if (pa.online().isEmpty() || pa.jogadores.isEmpty()) acabou(pa, false);
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (guarda.tem(p) && partidaDe(p) == null) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || partidaDe(p) != null) return;
                devolver(p);
                p.sendMessage(txt("§7Seu inventário da Fábrica Alegria foi devolvido."));
            }, 10L);
        } else if (p.getWorld().getName().equals(MUNDO)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && partidaDe(p) == null && p.getWorld().getName().equals(MUNDO)) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }, 10L);
        }
    }

    @EventHandler
    public void mudouMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (partidaDe(p) != null && !p.getWorld().getName().equals(MUNDO)) sair(p, false);
    }
}
