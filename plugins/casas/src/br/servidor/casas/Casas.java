package br.servidor.casas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Casas (pedido do Paulo, 08/11): cada plot do PlotMe ganha uma casa pronta no meio. Dentro, logo depois da porta, tem
 * uma placa de pressao: o dono pisa nela com nivel de poder suficiente (10, 20, ... 60) e a casa e trocada por uma
 * maior e mais bonita (7 casas: cabana, casa de madeira, sobrado, casa de pedra, mansao, castelo, palacio). Os itens
 * que estavam nos baus da casa antiga vao para os baus da nova. Funciona igual no Java e no Bedrock (so blocos).
 */
public final class Casas extends JavaPlugin implements Listener {

    /** Estado de uma casa construida. */
    static final class Estado {
        int nivel;
        UUID dono;
        int cx, cz, chao;
        int px, py, pz;      // placa
        int x1, z1, x2, z2, yTopo; // caixa da casa (para limpar e recolher itens)
        int plx1, plz1, plx2, plz2; // cantos do plot
    }

    private final Map<String, Estado> casas = new HashMap<>();
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();
    private final Set<String> semEspaco = new HashSet<>();
    private Plots plots;
    private File arquivo;
    private NamespacedKey kLetreiro;
    private boolean conferindo;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kLetreiro = new NamespacedKey(this, "letreiro");
        arquivo = new File(getDataFolder(), "casas.yml");
        plots = new Plots(new File(getConfig().getString("banco", "plugins/PlotMe/plots.db")));
        carregar();
        Bukkit.getPluginManager().registerEvents(this, this);
        long seg = Math.max(5, getConfig().getInt("conferir-segundos", 30));
        Bukkit.getScheduler().runTaskTimer(this, this::conferir, 20L * 10, 20L * seg);
        getLogger().info("Casas ativo: " + casas.size() + " casa(s) conhecidas; niveis " + niveis() + ".");
    }

    @Override
    public void onDisable() {
        salvar();
    }

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    List<Integer> niveis() {
        List<Integer> l = getConfig().getIntegerList("niveis");
        return l.isEmpty() ? List.of(0, 10, 20, 30, 40, 50, 60) : l;
    }

    int maxNivel() {
        return Math.min(Construtor.CASAS.length - 1, niveis().size() - 1);
    }

    /** Nivel de poder exigido para a casa de nivel n. */
    int poderPara(int n) {
        List<Integer> l = niveis();
        return n < l.size() ? l.get(n) : l.get(l.size() - 1);
    }

    World mundo() {
        return Bukkit.getWorld(getConfig().getString("mundo", "plots"));
    }

    // ------------------------------------------------------------------ estado

    private void carregar() {
        casas.clear();
        if (!arquivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        for (String k : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s == null) continue;
            Estado e = new Estado();
            e.nivel = s.getInt("nivel");
            try {
                e.dono = UUID.fromString(s.getString("dono", ""));
            } catch (RuntimeException ex) {
                continue;
            }
            e.cx = s.getInt("cx");
            e.cz = s.getInt("cz");
            e.chao = s.getInt("chao", 64);
            e.px = s.getInt("px");
            e.py = s.getInt("py");
            e.pz = s.getInt("pz");
            e.x1 = s.getInt("x1");
            e.z1 = s.getInt("z1");
            e.x2 = s.getInt("x2");
            e.z2 = s.getInt("z2");
            e.yTopo = s.getInt("ytopo");
            e.plx1 = s.getInt("plx1", e.cx - 16);
            e.plz1 = s.getInt("plz1", e.cz - 16);
            e.plx2 = s.getInt("plx2", e.cx + 15);
            e.plz2 = s.getInt("plz2", e.cz + 15);
            casas.put(k, e);
        }
    }

    private void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Estado> en : casas.entrySet()) {
            Estado e = en.getValue();
            String k = en.getKey();
            y.set(k + ".nivel", e.nivel);
            y.set(k + ".dono", e.dono.toString());
            y.set(k + ".cx", e.cx);
            y.set(k + ".cz", e.cz);
            y.set(k + ".chao", e.chao);
            y.set(k + ".px", e.px);
            y.set(k + ".py", e.py);
            y.set(k + ".pz", e.pz);
            y.set(k + ".x1", e.x1);
            y.set(k + ".z1", e.z1);
            y.set(k + ".x2", e.x2);
            y.set(k + ".z2", e.z2);
            y.set(k + ".ytopo", e.yTopo);
            y.set(k + ".plx1", e.plx1);
            y.set(k + ".plz1", e.plz1);
            y.set(k + ".plx2", e.plx2);
            y.set(k + ".plz2", e.plz2);
        }
        try {
            y.save(arquivo);
        } catch (IOException ex) {
            getLogger().warning("Nao consegui salvar casas.yml: " + ex);
        }
    }

    // ------------------------------------------------------------------ conferencia dos plots

    /** Le o plots.db fora da thread principal e, na principal, constroi o que falta. */
    void conferir() {
        if (conferindo) return;
        World w = mundo();
        if (w == null) return;
        conferindo = true;
        String nomeMundo = w.getName();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            List<Plots.Plot> lista = plots.ler(nomeMundo);
            String erro = plots.ultimoErro();
            Bukkit.getScheduler().runTask(this, () -> {
                conferindo = false;
                if (erro != null) {
                    getLogger().warning("plots.db: " + erro);
                    return;
                }
                aplicar(w, lista);
            });
        });
    }

    private void aplicar(World w, List<Plots.Plot> lista) {
        Set<String> vistos = new HashSet<>();
        boolean mudou = false;
        for (Plots.Plot p : lista) {
            vistos.add(p.chave());
            Estado e = casas.get(p.chave());
            if (e != null && e.dono.equals(p.dono())) continue;
            // plot novo (ou dono novo depois de um reset): casa inicial
            try {
                if (e != null) removerLetreiro(w, p.chave());
                int[] c0 = centro(w, p, 0, null);
                if (c0 == null) {
                    if (semEspaco.add(p.chave())) {
                        getLogger().info("Plot " + p.chave() + " de " + p.nomeDono() + ": sem espaco livre para a casa inicial (plot ja construido). Nada feito.");
                        Player dono = Bukkit.getPlayer(p.dono());
                        if (dono != null) dono.sendMessage(txt("§eSeu plot já tem construções: não achei um espaço livre de 9x9 para a sua casa. §7Libere um canto e use §f/casa conferir§7."));
                    }
                    continue;
                }
                e = construir(w, p, 0, null, c0[0], c0[1]);
                casas.put(p.chave(), e);
                mudou = true;
                Player dono = Bukkit.getPlayer(p.dono());
                if (dono != null) {
                    dono.sendMessage(txt("§6§l>> §eSua casa foi construída no seu plot! §7Uma §f" + Construtor.casa(0).nome()
                            + "§7 para começar. Dentro dela, pise na §fplaca§7 quando tiver nível de poder §e" + poderPara(1) + "§7 e ela evolui."));
                    dono.playSound(dono.getLocation(), Sound.ENTITY_VILLAGER_WORK_MASON, 1f, 1f);
                }
                getLogger().info("Casa inicial no plot " + p.chave() + " de " + p.nomeDono() + " em " + e.cx + "," + e.cz + ".");
            } catch (Throwable t) {
                getLogger().warning("Casa no plot " + p.chave() + ": " + t);
                t.printStackTrace();
            }
        }
        // plots que sumiram do PlotMe (reset/expirou): esquece (o PlotMe limpa o terreno)
        for (String k : new ArrayList<>(casas.keySet())) {
            if (!vistos.contains(k)) {
                removerLetreiro(w, k);
                casas.remove(k);
                mudou = true;
            }
        }
        // placa que o dono tirou volta; casa que sumiu (plot limpo pelo PlotMe) e construida de novo
        for (Map.Entry<String, Estado> en : new ArrayList<>(casas.entrySet())) {
            Estado e = en.getValue();
            if (!w.isChunkLoaded(e.px >> 4, e.pz >> 4)) continue;
            Block b = w.getBlockAt(e.px, e.py, e.pz);
            if (b.getType() == Material.POLISHED_BLACKSTONE_PRESSURE_PLATE) continue;
            if (blocosNaCaixa(w, e) < 12) {
                try {
                    Estado novo = construir(w, plotDe(e, "?"), e.nivel, null, e.cx, e.cz);
                    casas.put(en.getKey(), novo);
                    mudou = true;
                    getLogger().info("Casa do plot " + en.getKey() + " tinha sumido: construida de novo (nivel " + e.nivel + ").");
                } catch (Throwable t) {
                    getLogger().warning("Refazer casa " + en.getKey() + ": " + t);
                }
            } else {
                b.setType(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, false);
            }
        }
        if (mudou) salvar();
    }

    private int blocosNaCaixa(World w, Estado e) {
        int n = 0;
        for (int x = e.x1; x <= e.x2; x++)
            for (int z = e.z1; z <= e.z2; z++)
                for (int yy = e.chao + 1; yy <= e.yTopo; yy++) if (!w.getBlockAt(x, yy, z).getType().isAir() && ++n >= 12) return n;
        return n;
    }

    private Plots.Plot plotDe(Estado e, String nome) {
        return new Plots.Plot(0, 0, mundo().getName(), e.dono, nome, e.plx1, e.plz1, e.plx2, e.plz2);
    }

    /** A area da casa (lado x lado, mais 1 de folga) esta livre? Blocos da casa anterior nao contam. */
    private boolean livre(World w, int cx, int cz, int lado, int alt, int chao, Estado antes) {
        int r = lado / 2 + 1;
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++) {
                if (antes != null && x >= antes.x1 && x <= antes.x2 && z >= antes.z1 && z <= antes.z2) continue;
                for (int yy = chao + 1; yy <= chao + alt; yy++) {
                    Material m = w.getBlockAt(x, yy, z).getType();
                    if (!m.isAir() && m != Material.SHORT_GRASS && m != Material.TALL_GRASS && m != Material.SNOW) return false;
                }
            }
        return true;
    }

    /** Onde por a casa do nivel: o centro do plot, senao o centro da casa antiga, senao o lugar livre mais perto do centro. */
    private int[] centro(World w, Plots.Plot p, int nivel, Estado antes) {
        Construtor.Casa c = Construtor.casa(nivel);
        int chao = getConfig().getInt("chao", 64);
        int r = c.lado() / 2 + 1;
        int minX = p.x1() + r + 1, maxX = p.x2() - r - 1, minZ = p.z1() + r + 1, maxZ = p.z2() - r - 1;
        if (minX > maxX || minZ > maxZ) return null; // casa maior que o plot
        for (int x = p.x1() >> 4; x <= p.x2() >> 4; x++) for (int z = p.z1() >> 4; z <= p.z2() >> 4; z++) w.getChunkAt(x, z).load(true);
        List<int[]> candidatos = new ArrayList<>();
        candidatos.add(new int[]{Math.max(minX, Math.min(maxX, p.cx())), Math.max(minZ, Math.min(maxZ, p.cz()))});
        if (antes != null) candidatos.add(new int[]{Math.max(minX, Math.min(maxX, antes.cx)), Math.max(minZ, Math.min(maxZ, antes.cz))});
        for (int[] k : candidatos) if (livre(w, k[0], k[1], c.lado(), c.altura(), chao, antes)) return k;
        // varre o plot do centro para fora, de 2 em 2 blocos
        int cx = candidatos.get(0)[0], cz = candidatos.get(0)[1];
        for (int raio = 2; raio <= 32; raio += 2)
            for (int dx = -raio; dx <= raio; dx += 2)
                for (int dz = -raio; dz <= raio; dz += 2) {
                    if (Math.abs(dx) != raio && Math.abs(dz) != raio) continue;
                    int x = cx + dx, z = cz + dz;
                    if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                    if (livre(w, x, z, c.lado(), c.altura(), chao, antes)) return new int[]{x, z};
                }
        return null;
    }

    /** Constroi a casa do nivel no plot; 'antes' (pode ser null) e a casa anterior, cujos itens dos baus sao guardados. */
    private Estado construir(World w, Plots.Plot p, int nivel, Estado antes, int cx, int cz) {
        int chao = getConfig().getInt("chao", 64);
        for (int x = p.x1() >> 4; x <= p.x2() >> 4; x++) for (int z = p.z1() >> 4; z <= p.z2() >> 4; z++) w.getChunkAt(x, z).load(true);
        List<ItemStack> itens = new ArrayList<>();
        if (antes != null) {
            if (getConfig().getBoolean("guardar-itens", true)) itens.addAll(recolher(w, antes));
            Construtor limpador = new Construtor(w, antes.chao);
            // limpa a caixa antiga inteira (a nova pode ser menor ou deslocada)
            for (int x = antes.x1; x <= antes.x2; x++)
                for (int z = antes.z1; z <= antes.z2; z++) {
                    for (int yy = antes.chao + 1; yy <= antes.yTopo; yy++) {
                        Block b = w.getBlockAt(x, yy, z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
                    w.getBlockAt(x, antes.chao, z).setType(Material.GRASS_BLOCK, false);
                }
            removerLetreiro(w, p.chave());
        }
        Construtor c = new Construtor(w, chao);
        Construtor.Resultado r = c.construir(cx, cz, nivel);
        Estado e = new Estado();
        e.nivel = nivel;
        e.dono = p.dono();
        e.cx = cx;
        e.cz = cz;
        e.chao = chao;
        e.px = r.placa().getBlockX();
        e.py = r.placa().getBlockY();
        e.pz = r.placa().getBlockZ();
        e.x1 = r.x1();
        e.z1 = r.z1();
        e.x2 = r.x2();
        e.z2 = r.z2();
        e.yTopo = r.yTopo();
        e.plx1 = p.x1();
        e.plz1 = p.z1();
        e.plx2 = p.x2();
        e.plz2 = p.z2();
        // devolve os itens nos baus novos
        if (!itens.isEmpty()) {
            List<ItemStack> sobra = new ArrayList<>(itens);
            for (Block b : r.baus()) {
                if (sobra.isEmpty()) break;
                if (b.getState() instanceof Container cont) {
                    for (ItemStack it : new ArrayList<>(sobra)) {
                        if (cont.getInventory().addItem(it).isEmpty()) sobra.remove(it);
                        else break;
                    }
                }
            }
            for (ItemStack it : sobra) w.dropItemNaturally(r.placa().clone().add(0.5, 1, 0.5), it);
        }
        letreiro(w, p.chave(), e);
        // quem estava dentro da area nao fica preso nos blocos: vai para a placa (dentro da casa nova)
        for (Player j : w.getPlayers()) {
            Location l = j.getLocation();
            if (l.getBlockX() >= e.x1 && l.getBlockX() <= e.x2 && l.getBlockZ() >= e.z1 && l.getBlockZ() <= e.z2 && l.getBlockY() <= e.yTopo) {
                j.teleport(new Location(w, e.px + 0.5, e.py, e.pz + 0.5, l.getYaw(), l.getPitch()));
            }
        }
        return e;
    }

    /** Tira tudo o que estava nos baus/barris/fornalhas/shulkers (e quadros/armaduras) da casa antiga. */
    private List<ItemStack> recolher(World w, Estado a) {
        List<ItemStack> itens = new ArrayList<>();
        for (int x = a.x1; x <= a.x2; x++)
            for (int z = a.z1; z <= a.z2; z++)
                for (int yy = a.chao; yy <= a.yTopo; yy++) {
                    Block b = w.getBlockAt(x, yy, z);
                    BlockState st = b.getState();
                    if (st instanceof ShulkerBox sb) {
                        for (ItemStack it : sb.getInventory().getContents()) if (it != null && !it.getType().isAir()) itens.add(it.clone());
                        sb.getInventory().clear();
                        itens.add(new ItemStack(b.getType()));
                        b.setType(Material.AIR, false);
                    } else if (st instanceof Container c) {
                        for (ItemStack it : c.getInventory().getContents()) if (it != null && !it.getType().isAir()) itens.add(it.clone());
                        c.getInventory().clear();
                    }
                }
        for (Entity en : w.getNearbyEntities(new Location(w, (a.x1 + a.x2) / 2.0, (a.chao + a.yTopo) / 2.0, (a.z1 + a.z2) / 2.0),
                (a.x2 - a.x1) / 2.0 + 1, (a.yTopo - a.chao) / 2.0 + 1, (a.z2 - a.z1) / 2.0 + 1)) {
            if (en instanceof ItemFrame f) {
                if (!f.getItem().getType().isAir()) itens.add(f.getItem().clone());
                itens.add(new ItemStack(en.getType() == org.bukkit.entity.EntityType.GLOW_ITEM_FRAME ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME));
                en.remove();
            } else if (en instanceof ArmorStand as) {
                for (ItemStack it : as.getEquipment().getArmorContents()) if (it != null && !it.getType().isAir()) itens.add(it.clone());
                if (!as.getEquipment().getItemInMainHand().getType().isAir()) itens.add(as.getEquipment().getItemInMainHand().clone());
                itens.add(new ItemStack(Material.ARMOR_STAND));
                en.remove();
            }
        }
        return itens;
    }

    private void removerLetreiro(World w, String chave) {
        for (Entity en : w.getEntities()) {
            if (en instanceof TextDisplay t && chave.equals(t.getPersistentDataContainer().get(kLetreiro, PersistentDataType.STRING))) en.remove();
        }
    }

    private void letreiro(World w, String chave, Estado e) {
        Construtor.Casa c = Construtor.casa(e.nivel);
        String t;
        if (e.nivel >= maxNivel()) t = "§6§l" + c.nome().toUpperCase(Locale.ROOT) + "\n§7Nível máximo da casa. §dParabéns!";
        else t = "§6§lCASA NÍVEL " + e.nivel + " §7- §f" + c.nome() + "\n§7Pise na placa com nível de poder §e" + poderPara(e.nivel + 1)
                + "\n§7para virar §f" + Construtor.casa(e.nivel + 1).nome() + "§7.";
        w.spawn(new Location(w, e.px + 0.5, e.py + 1.9, e.pz + 0.5), TextDisplay.class, d -> {
            d.text(txt(t));
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(110, 0, 0, 0));
            d.setPersistent(true);
            d.getPersistentDataContainer().set(kLetreiro, PersistentDataType.STRING, chave);
            org.bukkit.util.Transformation tr = d.getTransformation();
            tr.getScale().set(0.7f);
            d.setTransformation(tr);
        });
    }

    // ------------------------------------------------------------------ placa

    private Estado casaDaPlaca(Block b) {
        for (Estado e : casas.values()) if (e.px == b.getX() && e.py == b.getY() && e.pz == b.getZ()) return e;
        return null;
    }

    private String chaveDe(Estado e) {
        for (Map.Entry<String, Estado> en : casas.entrySet()) if (en.getValue() == e) return en.getKey();
        return null;
    }

    int nivelDePoder(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return -1;
        try {
            String ph = getConfig().getString("placeholder-nivel", "%auraskills_power%");
            String s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((OfflinePlayer) p, ph);
            String d = s == null ? "" : s.replaceAll("[^0-9]", "");
            return d.isEmpty() ? -1 : Integer.parseInt(d);
        } catch (Throwable t) {
            return -1;
        }
    }

    @EventHandler
    public void pisou(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null) return;
        World w = mundo();
        if (w == null || !e.getClickedBlock().getWorld().equals(w)) return;
        Estado c = casaDaPlaca(e.getClickedBlock());
        if (c == null) return;
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        Long ultimo = ultimoAviso.get(p.getUniqueId());
        if (ultimo != null && agora - ultimo < 3000) return;
        ultimoAviso.put(p.getUniqueId(), agora);
        if (!p.getUniqueId().equals(c.dono)) {
            p.sendActionBar(txt("§7Essa placa só evolui a casa do dono do plot."));
            return;
        }
        tentarEvoluir(p, c, false);
    }

    private void tentarEvoluir(Player p, Estado c, boolean forcar) {
        if (c.nivel >= maxNivel()) {
            p.sendActionBar(txt("§6Sua casa já é um " + Construtor.casa(c.nivel).nome() + ": o nível máximo!"));
            return;
        }
        int exigido = poderPara(c.nivel + 1);
        int nivel = nivelDePoder(p);
        if (!forcar && nivel >= 0 && nivel < exigido) {
            p.sendActionBar(txt("§eSua casa evolui para §f" + Construtor.casa(c.nivel + 1).nome() + " §eno nível de poder §f" + exigido
                    + " §7(você tem " + nivel + "; suba suas skills: /skills)"));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        if (!forcar && nivel < 0) {
            p.sendActionBar(txt("§cNão consegui ler o seu nível de poder agora. Tente de novo."));
            return;
        }
        String chave = chaveDe(c);
        World w = mundo();
        Plots.Plot plot = plotDe(c, p.getName());
        int novo = c.nivel + 1;
        int[] onde = centro(w, plot, novo, c);
        if (onde == null) {
            int lado = Construtor.casa(novo).lado() + 2;
            p.sendMessage(txt("§eSua casa pode virar §f" + Construtor.casa(novo).nome() + "§e, mas ela precisa de um espaço livre de §f" + lado + "x" + lado
                    + " §eem volta. §7Tire suas construções perto da casa (a casa atual pode ficar) e pise na placa de novo."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        try {
            Estado e = construir(w, plot, novo, c, onde[0], onde[1]);
            casas.put(chave, e);
            salvar();
        } catch (Throwable t) {
            getLogger().warning("Evoluir casa de " + p.getName() + ": " + t);
            t.printStackTrace();
            p.sendMessage(txt("§cDeu erro ao evoluir a casa. Avise o Paulo."));
            return;
        }
        Construtor.Casa nova = Construtor.casa(novo);
        p.showTitle(Title.title(txt("§6§l" + nova.nome().toUpperCase(Locale.ROOT)), txt("§eSua casa evoluiu! §7(nível " + novo + ")")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);
        Location l = new Location(w, c.cx + 0.5, c.chao + 3, c.cz + 0.5);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, l, 120, 4, 3, 4, 0.2);
        w.spawnParticle(Particle.CLOUD, l, 80, 5, 2, 5, 0.05);
        p.sendMessage(txt("§6§l>> §eCasa nova: §f" + nova.nome() + " §7(" + nova.descricao() + ")."));
        if (novo < maxNivel()) p.sendMessage(txt("§7Próxima: §f" + Construtor.casa(novo + 1).nome() + " §7no nível de poder §e" + poderPara(novo + 1) + "§7."));
        else p.sendMessage(txt("§dEssa é a casa máxima. Parabéns!"));
        if (getConfig().getBoolean("guardar-itens", true)) p.sendMessage(txt("§8Os itens dos baús antigos foram para os baús da casa nova (o que não coube caiu na placa)."));
        for (Player o : w.getPlayers()) if (!o.equals(p)) o.sendMessage(txt("§6" + p.getName() + " §eevoluiu a casa para §f" + nova.nome() + "§e!"));
    }

    // ------------------------------------------------------------------ comandos

    private Estado casaDe(UUID dono) {
        for (Estado e : casas.values()) if (e.dono.equals(dono)) return e;
        return null;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length >= 1 && s.hasPermission("casas.admin")) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("conferir")) {
                conferir();
                s.sendMessage("§aConferindo os plots agora. Casas conhecidas: " + casas.size());
                return true;
            }
            if ((sub.equals("nivel") || sub.equals("refazer")) && a.length >= 2) {
                Player alvo = Bukkit.getPlayerExact(a[1]);
                if (alvo == null) {
                    s.sendMessage("§cJogador precisa estar online: " + a[1]);
                    return true;
                }
                Estado c = casaDe(alvo.getUniqueId());
                if (c == null) {
                    s.sendMessage("§c" + alvo.getName() + " nao tem casa (nao tem plot ou ainda nao conferi). Use /casa conferir.");
                    return true;
                }
                int n = sub.equals("refazer") ? c.nivel : Math.max(0, Math.min(maxNivel(), Integer.parseInt(a[2 < a.length ? 2 : 1].replaceAll("[^0-9]", "0"))));
                World w = mundo();
                Plots.Plot plot = plotDe(c, alvo.getName());
                String chave = chaveDe(c);
                int[] onde = centro(w, plot, n, c);
                if (onde == null) onde = new int[]{c.cx, c.cz};
                Estado e = construir(w, plot, n, c, onde[0], onde[1]);
                casas.put(chave, e);
                salvar();
                s.sendMessage("§aCasa de " + alvo.getName() + " agora e nivel " + n + " (" + Construtor.casa(n).nome() + ").");
                return true;
            }
            if (sub.equals("evoluir") && a.length >= 2) {
                Player alvo = Bukkit.getPlayerExact(a[1]);
                Estado c = alvo == null ? null : casaDe(alvo.getUniqueId());
                if (c == null) {
                    s.sendMessage("§cSem casa para esse jogador.");
                    return true;
                }
                tentarEvoluir(alvo, c, true);
                return true;
            }
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Uso: casa conferir | casa nivel <jogador> <0-6> | casa refazer <jogador> | casa evoluir <jogador>");
            return true;
        }
        Estado c = casaDe(p.getUniqueId());
        if (c == null) {
            p.sendMessage(txt("§eVocê ainda não tem casa. §7Pegue um plot no mundo dos plots (§f/plotme auto§7) e a casa aparece em até 30 s."));
            return true;
        }
        Construtor.Casa casa = Construtor.casa(c.nivel);
        p.sendMessage(txt("§6§lSua casa: §f" + casa.nome() + " §7(nível " + c.nivel + ") em " + c.cx + ", " + c.cz + " do mundo dos plots."));
        if (c.nivel < maxNivel()) p.sendMessage(txt("§7Próxima: §f" + Construtor.casa(c.nivel + 1).nome() + " §7pisando na placa com nível de poder §e"
                + poderPara(c.nivel + 1) + " §7(você tem §e" + Math.max(0, nivelDePoder(p)) + "§7)."));
        else p.sendMessage(txt("§dNível máximo!"));
        return true;
    }
}
