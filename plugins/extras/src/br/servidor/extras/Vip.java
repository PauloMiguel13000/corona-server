package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * VIPs do servidor (4 niveis). Cada nivel da coronas na hora, um kit por dia com coronas, bonus de coronas nas
 * masmorras, tag no chat e no TAB, e coisas so dele (particulas, chapeu, anuncio ao entrar, voar no spawn).
 * Para vender por um site: o site manda pelo console (RCON) "vip dar <jogador> <nivel> <dias>". Funciona com a
 * pessoa offline (fica guardado e as coronas entram quando ela entrar). Tudo fica em vips.yml e vips-log.txt.
 */
final class Vip implements Listener {

    record Nivel(int n, String id, String nome, String cor, long coronasNaHora, long coronasPorDia, int bonus, Material icone) {
        String tag() {
            return cor + "§l" + nome;
        }
    }

    static final Nivel[] NIVEIS = {
            null,
            new Nivel(1, "vip", "VIP", "§a", 3000, 150, 10, Material.EMERALD),
            new Nivel(2, "vipmais", "VIP+", "§b", 8000, 400, 25, Material.DIAMOND),
            new Nivel(3, "mvp", "MVP", "§6", 12000, 600, 35, Material.GOLD_BLOCK),
            new Nivel(4, "lenda", "LENDA", "§d", 25000, 1200, 50, Material.NETHER_STAR)};

    private final Extras pl;
    private final File arquivo, log;
    private YamlConfiguration dados;
    private final NamespacedKey kNivel, kAte;
    private final Set<UUID> voandoVip = new HashSet<>();
    /** Traje do Rei (MVP e LENDA). */
    TrajeRei traje;

    Vip(Extras pl) {
        this.pl = pl;
        this.arquivo = new File(pl.getDataFolder(), "vips.yml");
        this.log = new File(pl.getDataFolder(), "vips-log.txt");
        this.dados = YamlConfiguration.loadConfiguration(arquivo);
        this.kNivel = new NamespacedKey(pl, "vip_nivel");
        this.kAte = new NamespacedKey(pl, "vip_ate");
    }

    void iniciar() {
        Bukkit.getScheduler().runTaskTimer(pl, this::conferirTodos, 200L, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(pl, this::efeitos, 40L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) sincronizar(p);
    }

    // ------------------------------------------------------------------ dados

    private static String chave(String nome) {
        return nome.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    private ConfigurationSection secao(String nome, boolean criar) {
        String k = "jogadores." + chave(nome);
        ConfigurationSection c = dados.getConfigurationSection(k);
        if (c == null && criar) c = dados.createSection(k);
        return c;
    }

    private void salvar() {
        try {
            dados.save(arquivo);
        } catch (IOException e) {
            pl.getLogger().warning("Nao consegui salvar vips.yml: " + e.getMessage());
        }
    }

    private void registrar(String linha) {
        try {
            String t = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()) + " " + linha + System.lineSeparator();
            Files.writeString(log.toPath(), t, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    /** Nivel ativo (0 = nao e VIP). */
    int nivel(String nome) {
        ConfigurationSection c = secao(nome, false);
        if (c == null) return 0;
        if (c.getLong("ate", 0) < System.currentTimeMillis()) return 0;
        return Math.max(0, Math.min(4, c.getInt("nivel", 0)));
    }

    int nivel(Player p) {
        return nivel(p.getName());
    }

    /** Traje do Rei de cortesia (dado pelo admin, mesmo sem VIP). */
    boolean cortesia(String nome) {
        return dados.getStringList("traje-cortesia").contains(chave(nome));
    }

    Nivel info(Player p) {
        int n = nivel(p);
        return n == 0 ? null : NIVEIS[n];
    }

    /** Copia nivel/validade para os dados do jogador (o Masmorras le dai o bonus de coronas) e atualiza o TAB. */
    void sincronizar(Player p) {
        ConfigurationSection c = secao(p.getName(), false);
        int n = nivel(p);
        if (n > 0) {
            p.getPersistentDataContainer().set(kNivel, PersistentDataType.INTEGER, n);
            p.getPersistentDataContainer().set(kAte, PersistentDataType.LONG, c.getLong("ate"));
        } else {
            p.getPersistentDataContainer().remove(kNivel);
            p.getPersistentDataContainer().remove(kAte);
        }
        atualizarTab(p);
    }

    private static void atualizarTab(Player p) {
        org.bukkit.plugin.Plugin po = Bukkit.getPluginManager().getPlugin("Poderes");
        if (po == null || !po.isEnabled()) return;
        try {
            po.getClass().getMethod("atualizarTab", Player.class).invoke(po, p);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ dar / tirar

    /** Da (ou estende) o VIP. Nivel maior troca o nivel; os dias somam com o que ainda faltava. Devolve a mensagem. */
    String dar(String nome, int n, int dias, String quem) {
        if (n < 1 || n > 4 || dias < 1 || dias > 3650) return "nivel 1 a 4 e dias 1 a 3650";
        ConfigurationSection c = secao(nome, true);
        long agora = System.currentTimeMillis();
        int antes = nivel(nome);
        long base = Math.max(agora, c.getLong("ate", 0));
        if (antes == 0) base = agora;
        c.set("nome", nome);
        c.set("nivel", Math.max(antes, n));
        c.set("ate", base + dias * 86_400_000L);
        long coronas = NIVEIS[n].coronasNaHora();
        Player p = Bukkit.getPlayerExact(nome);
        if (p != null) darCoronas(p, coronas);
        else c.set("coronas-pendentes", c.getLong("coronas-pendentes", 0) + coronas);
        if (p != null) Bukkit.getScheduler().runTaskLater(pl, () -> presenteDoMes(p, nivel(nome)), 40L);
        else c.set("presente-pendente", c.getInt("presente-pendente", 0) + 1);
        salvar();
        registrar("DAR " + nome + " nivel " + n + " dias " + dias + " (por " + quem + ") -> nivel " + c.getInt("nivel") + " ate " + data(c.getLong("ate")));
        Nivel nv = NIVEIS[c.getInt("nivel")];
        if (p != null) {
            sincronizar(p);
            p.showTitle(net.kyori.adventure.title.Title.title(Extras.txt(nv.tag()), Extras.txt("§fObrigado por apoiar o Corona Server!")));
            p.sendMessage(Extras.txt("§6§l✦ §fVocê agora é " + nv.tag() + " §faté §e" + data(c.getLong("ate")) + "§f! §7Benefícios: §e/vip"));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            fogos(p.getLocation());
            if (nv.n() >= 3) trajePrimeiraVez(p, c);
        }
        Bukkit.broadcast(Extras.txt("§6§l✦ §f" + nome + " §7virou " + nv.tag() + "§7! §8(/vip)"));
        return nome + " agora é " + nv.nome() + " até " + data(c.getLong("ate")) + (p == null ? " (offline: as coronas entram quando ele entrar)" : "");
    }

    String tirar(String nome, String quem) {
        ConfigurationSection c = secao(nome, false);
        if (c == null) return nome + " não é VIP.";
        dados.set("jogadores." + chave(nome), null);
        salvar();
        registrar("TIRAR " + nome + " (por " + quem + ")");
        Player p = Bukkit.getPlayerExact(nome);
        if (p != null) {
            pararVoo(p);
            sincronizar(p);
        }
        return "VIP de " + nome + " tirado.";
    }

    private void darCoronas(Player p, long v) {
        if (v <= 0) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coronas dar " + p.getName() + " " + v);
    }

    static String data(long ms) {
        return new SimpleDateFormat("dd/MM/yyyy").format(new Date(ms));
    }

    // ------------------------------------------------------------------ entrar, vencer

    @EventHandler(priority = EventPriority.MONITOR)
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            ConfigurationSection c = secao(p.getName(), false);
            if (c != null && c.getLong("ate", 0) < System.currentTimeMillis() && c.getInt("nivel", 0) > 0) {
                p.sendMessage(Extras.txt("§7Seu §fVIP §7acabou. Obrigado pelo apoio! §e/vip §7mostra como renovar."));
                registrar("VENCEU " + p.getName());
                dados.set("jogadores." + chave(p.getName()), null);
                salvar();
            }
            sincronizar(p);
            Nivel nv = info(p);
            if (nv == null) {
                if (traje != null && cortesia(p.getName())) traje.entregar(p, false);
                return;
            }
            int pres = c == null ? 0 : c.getInt("presente-pendente", 0);
            if (pres > 0) {
                c.set("presente-pendente", null);
                salvar();
                for (int k = 0; k < Math.min(pres, 6); k++) presenteDoMes(p, nv.n());
            }
            long pend = c == null ? 0 : c.getLong("coronas-pendentes", 0);
            if (pend > 0) {
                darCoronas(p, pend);
                c.set("coronas-pendentes", null);
                salvar();
                p.sendMessage(Extras.txt("§6§l✦ §fSuas §6" + pend + " coronas §fdo " + nv.tag() + " §fchegaram!"));
            }
            if (nv.n() >= 3) {
                Bukkit.broadcast(Extras.txt(nv.tag() + " §f" + p.getName() + " §7entrou no servidor!"));
                fogos(p.getLocation());
                trajePrimeiraVez(p, c);
            }
            p.sendMessage(Extras.txt(nv.tag() + " §7ativo até §f" + data(c.getLong("ate")) + "§7. Kit do dia: §e/vip kit"));
        }, 60L);
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        voandoVip.remove(e.getPlayer().getUniqueId());
    }

    private void conferirTodos() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Integer n = p.getPersistentDataContainer().get(kNivel, PersistentDataType.INTEGER);
            if (n != null && nivel(p) == 0) {
                p.sendMessage(Extras.txt("§7Seu §fVIP §7acabou agora. Obrigado pelo apoio! §e/vip"));
                registrar("VENCEU " + p.getName());
                pararVoo(p);
                sincronizar(p);
            }
        }
    }

    // ------------------------------------------------------------------ tag no chat

    @EventHandler(priority = EventPriority.HIGH)
    public void chat(io.papermc.paper.event.player.AsyncChatEvent e) {
        Integer n = e.getPlayer().getPersistentDataContainer().get(kNivel, PersistentDataType.INTEGER);
        Long ate = e.getPlayer().getPersistentDataContainer().get(kAte, PersistentDataType.LONG);
        if (n == null || n < 1 || n > 4 || ate == null || ate < System.currentTimeMillis()) return;
        net.kyori.adventure.text.Component tag = Extras.txt("§8[" + NIVEIS[n].tag() + "§8] ");
        io.papermc.paper.chat.ChatRenderer antes = e.renderer();
        e.renderer((src, nome, msg, quem) -> tag.append(antes.render(src, nome, msg, quem)));
    }

    // ------------------------------------------------------------------ particulas e voo

    private void efeitos() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Integer n = p.getPersistentDataContainer().get(kNivel, PersistentDataType.INTEGER);
            if (n == null) continue;
            ConfigurationSection c = secao(p.getName(), false);
            if (c == null) continue;
            // voo do LENDA: so no spawn e nos lobbies
            if (voandoVip.contains(p.getUniqueId()) && !podeVoarAqui(p)) {
                pararVoo(p);
                p.sendActionBar(Extras.txt("§7Voo do LENDA só no spawn e nos lobbies."));
            }
            if (n < 2 || !c.getBoolean("particulas", false) || p.isInvisible() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location l = p.getLocation().add(0, 0.1, 0);
            switch (n) {
                case 2 -> p.getWorld().spawnParticle(Particle.END_ROD, l, 2, 0.25, 0.05, 0.25, 0.01);
                case 3 -> p.getWorld().spawnParticle(Particle.WAX_ON, l.add(0, 1, 0), 3, 0.35, 0.6, 0.35, 0.5);
                default -> {
                    p.getWorld().spawnParticle(Particle.WITCH, l.clone().add(0, 1, 0), 3, 0.35, 0.6, 0.35, 0.02);
                    p.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0.25, 0.05, 0.25, 0.01);
                }
            }
        }
    }

    private boolean podeVoarAqui(Player p) {
        String w = p.getWorld().getName();
        return pl.naAreaDoSpawn(p.getLocation()) || w.equals("masmorras_spawn") || w.equals("minigames");
    }

    private void pararVoo(Player p) {
        if (!voandoVip.remove(p.getUniqueId())) return;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        p.setFlying(false);
        p.setAllowFlight(false);
        pl.semQuedaPor(p, 10000L);
    }

    private static void fogos(Location l) {
        l.getWorld().spawnParticle(Particle.FIREWORK, l.clone().add(0, 1.5, 0), 40, 0.6, 0.8, 0.6, 0.15);
        l.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.3);
        l.getWorld().playSound(l, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
    }

    /** O Traje do Rei entra sozinho na primeira vez; depois e so /vip traje (se perder). */
    private void trajePrimeiraVez(Player p, ConfigurationSection c) {
        if (traje == null || c == null || c.getBoolean("traje-recebido", false)) return;
        if (traje.entregar(p, false) >= 0) {
            c.set("traje-recebido", true);
            salvar();
        }
    }

    // ------------------------------------------------------------------ kit do dia

    /** Kit do dia (o mais simples: comida, XP e perolas; o forte fica no kit semanal e no presente do mes). */
    private static List<ItemStack> kit(int n) {
        List<ItemStack> l = new ArrayList<>();
        int[][] q = {{}, {16, 8, 8, 0, 0, 0}, {24, 16, 16, 4, 0, 0}, {32, 24, 24, 8, 1, 0}, {48, 32, 32, 12, 2, 2}};
        Material[] m = {Material.COOKED_BEEF, Material.GOLDEN_CARROT, Material.EXPERIENCE_BOTTLE, Material.ENDER_PEARL, Material.GOLDEN_APPLE, Material.DIAMOND};
        for (int i = 0; i < m.length; i++) if (q[n][i] > 0) l.add(new ItemStack(m[i], q[n][i]));
        return l;
    }

    // ------------------------------------------------------------------ presente do mes (ao comprar ou renovar)

    private static final java.util.Map<String, String> OVO = java.util.Map.of(
            "LEGENDARY", "give %player% minecraft:player_head[minecraft:custom_name={text:\"Ovo de Pet Lendario\",color:\"gold\",italic:false},minecraft:lore=[{text:\"Um ovo misterioso...\",color:\"gray\",italic:false},{text:\"Coloque numa Incubadora de Pets pra chocar!\",color:\"yellow\",italic:false}],minecraft:profile={properties:[{name:\"textures\",value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmM4YjE1ZTg1ZWQyMjVjMjFjMzIzNjdkYTdiZTI0NDc3OWUzNmFmZTdmNzU2M2E2YTU3MGE3MTdjYjViZTFiMSJ9fX0=\"}]},minecraft:custom_data={PublicBukkitValues:{\"babypets:pet_egg\":1b,\"babypets:egg_rarity\":\"LEGENDARY\"}}] 1",
            "EPIC", "give %player% minecraft:player_head[minecraft:custom_name={text:\"Ovo de Pet Epico\",color:\"dark_purple\",italic:false},minecraft:lore=[{text:\"Um ovo misterioso...\",color:\"gray\",italic:false},{text:\"Coloque numa Incubadora de Pets pra chocar!\",color:\"yellow\",italic:false}],minecraft:profile={properties:[{name:\"textures\",value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmM4YjE1ZTg1ZWQyMjVjMjFjMzIzNjdkYTdiZTI0NDc3OWUzNmFmZTdmNzU2M2E2YTU3MGE3MTdjYjViZTFiMSJ9fX0=\"}]},minecraft:custom_data={PublicBukkitValues:{\"babypets:pet_egg\":1b,\"babypets:egg_rarity\":\"EPIC\"}}] 1",
            "RARE", "give %player% minecraft:player_head[minecraft:custom_name={text:\"Ovo de Pet Raro\",color:\"blue\",italic:false},minecraft:lore=[{text:\"Um ovo misterioso...\",color:\"gray\",italic:false},{text:\"Coloque numa Incubadora de Pets pra chocar!\",color:\"yellow\",italic:false}],minecraft:profile={properties:[{name:\"textures\",value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmM4YjE1ZTg1ZWQyMjVjMjFjMzIzNjdkYTdiZTI0NDc3OWUzNmFmZTdmNzU2M2E2YTU3MGE3MTdjYjViZTFiMSJ9fX0=\"}]},minecraft:custom_data={PublicBukkitValues:{\"babypets:pet_egg\":1b,\"babypets:egg_rarity\":\"RARE\"}}] 1",
            "UNCOMMON", "give %player% minecraft:player_head[minecraft:custom_name={text:\"Ovo de Pet Incomum\",color:\"green\",italic:false},minecraft:lore=[{text:\"Um ovo misterioso...\",color:\"gray\",italic:false},{text:\"Coloque numa Incubadora de Pets pra chocar!\",color:\"yellow\",italic:false}],minecraft:profile={properties:[{name:\"textures\",value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmM4YjE1ZTg1ZWQyMjVjMjFjMzIzNjdkYTdiZTI0NDc3OWUzNmFmZTdmNzU2M2E2YTU3MGE3MTdjYjViZTFiMSJ9fX0=\"}]},minecraft:custom_data={PublicBukkitValues:{\"babypets:pet_egg\":1b,\"babypets:egg_rarity\":\"UNCOMMON\"}}] 1");

    /** Ovos de pet do presente do mes, por nivel. */
    private static final String[][] OVOS_MES = {{}, {"UNCOMMON"}, {"RARE"}, {"EPIC", "RARE"}, {"LEGENDARY", "EPIC", "RARE"}};
    private static final String[] NOME_OVO = {"Incomum", "Raro", "Épico", "Lendário"};

    /** Entrega o presente do mes (incubadora + ovos do nivel). Chamado a cada compra/renovacao. */
    void presenteDoMes(Player p, int n) {
        if (n < 1 || n > 4) return;
        org.bukkit.command.CommandSender c = Bukkit.getConsoleSender();
        Bukkit.dispatchCommand(c, "pets giveincubator " + p.getName());
        StringBuilder nomes = new StringBuilder();
        for (String r : OVOS_MES[n]) {
            String cmd = OVO.get(r);
            if (cmd == null) continue;
            Bukkit.dispatchCommand(c, cmd.replace("%player%", p.getName()));
            String nm = switch (r) {
                case "LEGENDARY" -> "§6Lendário";
                case "EPIC" -> "§5Épico";
                case "RARE" -> "§9Raro";
                default -> "§aIncomum";
            };
            if (nomes.length() > 0) nomes.append("§7, ");
            nomes.append(nm);
        }
        p.sendMessage(Extras.txt("§6§l✦ Presente do mês " + NIVEIS[n].tag() + "§6§l: §7Incubadora + Ovo " + nomes + "§7!"));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
        registrar("PRESENTE DO MES " + p.getName() + " nivel " + n);
    }

    private void pegarKit(Player p) {
        Nivel nv = info(p);
        if (nv == null) {
            p.sendMessage(Extras.txt("§cO kit do dia é só para VIP. §7Veja: §e/vip"));
            return;
        }
        ConfigurationSection c = secao(p.getName(), false);
        long agora = System.currentTimeMillis(), ultimo = c.getLong("kit-ultimo", 0);
        if (agora - ultimo < 86_400_000L) {
            long falta = 86_400_000L - (agora - ultimo);
            p.sendMessage(Extras.txt("§7Kit do dia já pego. Volta em §f" + (falta / 3_600_000L) + "h " + (falta / 60_000L % 60) + "min§7."));
            return;
        }
        c.set("kit-ultimo", agora);
        salvar();
        for (ItemStack it : kit(nv.n()))
            for (ItemStack sobra : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), sobra);
        darCoronas(p, nv.coronasPorDia());
        p.sendMessage(Extras.txt("§a✔ Kit do dia " + nv.tag() + " §a+ §6" + nv.coronasPorDia() + " coronas§a!"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ /vip

    boolean comando(CommandSender s, String[] a) {
        boolean admin = s.hasPermission("extras.vip.admin");
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "dar", "add", "ativar" -> {
                if (!admin) return semPermissao(s);
                if (a.length < 4) {
                    s.sendMessage("Use: vip dar <jogador> <nivel 1-4 ou vip|vipmais|mvp|lenda> <dias>");
                    return true;
                }
                int n = nivelDe(a[2]);
                int dias;
                try {
                    dias = Integer.parseInt(a[3]);
                } catch (NumberFormatException ex) {
                    dias = -1;
                }
                s.sendMessage(Extras.txt("§e" + dar(a[1], n, dias, s.getName())));
                return true;
            }
            case "tirar", "remover" -> {
                if (!admin) return semPermissao(s);
                if (a.length < 2) {
                    s.sendMessage("Use: vip tirar <jogador>");
                    return true;
                }
                s.sendMessage(Extras.txt("§e" + tirar(a[1], s.getName())));
                return true;
            }
            case "ver" -> {
                if (!admin) return semPermissao(s);
                if (a.length < 2) return false;
                ConfigurationSection c = secao(a[1], false);
                int n = nivel(a[1]);
                s.sendMessage(Extras.txt(n == 0 ? "§7" + a[1] + " não é VIP." : "§e" + a[1] + ": " + NIVEIS[n].tag() + " §7até §f" + data(c.getLong("ate"))
                        + (c.getLong("coronas-pendentes", 0) > 0 ? " §7(coronas pendentes: " + c.getLong("coronas-pendentes") + ")" : "")));
                return true;
            }
            case "traje" -> {
                // admin: traje de cortesia (funciona mesmo sem MVP/LENDA). vip traje dar|tirar <jogador>
                if (a.length < 3 || !admin) break;
                String nome = a[2], k = chave(nome);
                List<String> l = new ArrayList<>(dados.getStringList("traje-cortesia"));
                boolean dar = a[1].equalsIgnoreCase("dar");
                l.remove(k);
                if (dar) l.add(k);
                dados.set("traje-cortesia", l);
                salvar();
                registrar("TRAJE CORTESIA " + (dar ? "DAR " : "TIRAR ") + nome + " (por " + s.getName() + ")");
                Player alvo = Bukkit.getPlayerExact(nome);
                if (alvo != null && traje != null) {
                    if (dar) traje.entregar(alvo, true);
                    else traje.conferir(alvo);
                }
                s.sendMessage(Extras.txt("§eTraje do Rei de cortesia " + (dar ? "dado a " : "tirado de ") + nome + (alvo == null && dar ? " §7(entra quando ele entrar)" : "") + "§e."));
                return true;
            }
            case "lista" -> {
                if (!admin) return semPermissao(s);
                ConfigurationSection j = dados.getConfigurationSection("jogadores");
                int k = 0;
                if (j != null)
                    for (String id : j.getKeys(false)) {
                        String nome = j.getString(id + ".nome", id);
                        int n = nivel(nome);
                        if (n == 0) continue;
                        k++;
                        s.sendMessage(Extras.txt("§7- §f" + nome + " " + NIVEIS[n].tag() + " §7até " + data(j.getLong(id + ".ate"))));
                    }
                s.sendMessage(Extras.txt("§e" + k + " VIP(s) ativo(s)."));
                return true;
            }
            default -> { }
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Console: vip dar <jogador> <nivel> <dias> | vip tirar <jogador> | vip ver <jogador> | vip lista");
            return true;
        }
        Nivel nv = info(p);
        switch (sub) {
            case "kit" -> pegarKit(p);
            case "traje", "armadura", "rei" -> {
                if (traje != null) traje.entregar(p, true);
            }
            case "particulas", "particula", "rastro" -> {
                if (nv == null || nv.n() < 2) {
                    p.sendMessage(Extras.txt("§cPartículas: a partir do " + NIVEIS[2].tag() + "§c."));
                    return true;
                }
                ConfigurationSection c = secao(p.getName(), true);
                boolean liga = !c.getBoolean("particulas", false);
                c.set("particulas", liga);
                salvar();
                p.sendMessage(Extras.txt(liga ? "§aPartículas ligadas." : "§7Partículas desligadas."));
            }
            case "chapeu", "chapéu", "hat" -> {
                if (nv == null || nv.n() < 3) {
                    p.sendMessage(Extras.txt("§cChapéu: a partir do " + NIVEIS[3].tag() + "§c."));
                    return true;
                }
                ItemStack mao = p.getInventory().getItemInMainHand();
                if (mao.getType().isAir()) {
                    p.sendMessage(Extras.txt("§7Segure um bloco ou item e use §e/vip chapeu§7."));
                    return true;
                }
                ItemStack cabeca = p.getInventory().getHelmet();
                p.getInventory().setHelmet(mao);
                p.getInventory().setItemInMainHand(cabeca);
                p.sendMessage(Extras.txt("§aChapéu novo!"));
            }
            case "voar", "fly" -> {
                if (nv == null || nv.n() < 4) {
                    p.sendMessage(Extras.txt("§cVoar no spawn: só " + NIVEIS[4].tag() + "§c."));
                    return true;
                }
                if (voandoVip.contains(p.getUniqueId())) {
                    pararVoo(p);
                    p.sendMessage(Extras.txt("§7Voo desligado."));
                } else if (!podeVoarAqui(p)) {
                    p.sendMessage(Extras.txt("§7O voo do LENDA funciona no spawn e nos lobbies."));
                } else {
                    voandoVip.add(p.getUniqueId());
                    p.setAllowFlight(true);
                    p.sendMessage(Extras.txt("§dVoo ligado §7(aperte espaço duas vezes). §e/vip voar §7desliga."));
                }
            }
            default -> mostrar(p, nv);
        }
        return true;
    }

    private static int nivelDe(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace("+", "mais");
        for (int i = 1; i < NIVEIS.length; i++) if (NIVEIS[i].id().equals(t) || NIVEIS[i].nome().equalsIgnoreCase(s)) return i;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean semPermissao(CommandSender s) {
        s.sendMessage(Extras.txt("§cSó admin."));
        return true;
    }

    private void mostrar(Player p, Nivel nv) {
        ConfigurationSection c = secao(p.getName(), false);
        p.sendMessage(Extras.txt("§6§l✦ VIPs do Corona Server ✦"));
        if (nv != null) p.sendMessage(Extras.txt("§fVocê é " + nv.tag() + " §faté §e" + data(c.getLong("ate")) + "§f."));
        String[] extra = {"", "§7tag, kit do dia e +10% de coronas", "§7+ partículas (§e/vip particulas§7)",
                "§7+ chapéu (§e/vip chapeu§7), anúncio quando entra e §6§lTraje do Rei §7(§e/vip traje§7)", "§7+ voar no spawn e nos lobbies (§e/vip voar§7)"};
        for (int i = 1; i < NIVEIS.length; i++) {
            Nivel n = NIVEIS[i];
            p.sendMessage(Extras.txt(n.tag() + "§7: §6" + n.coronasNaHora() + " coronas §7+ §dovo " + NOME_OVO[i - 1] + " §7ao comprar, §6" + n.coronasPorDia()
                    + "/dia §7no kit do dia, kit semanal (§e/kits§7), §a+" + n.bonus() + "% §7nas masmorras"));
            p.sendMessage(Extras.txt("   " + extra[i] + (i > 1 ? " §8(e tudo do anterior)" : "")));
        }
        String site = pl.getConfig().getString("vip.site", "");
        p.sendMessage(Extras.txt(site.isBlank() ? "§7Como comprar: §fem breve! Fale com o dono do servidor." : "§7Compre em: §b" + site));
        if (nv != null) p.sendMessage(Extras.txt("§7Comandos: §e/vip kit§7, §e/vip particulas§7, §e/vip chapeu§7, §e/vip traje§7, §e/vip voar"));
    }
}
