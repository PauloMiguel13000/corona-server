package br.servidor.clas;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Grupos (temporarios, para jogar junto) e clas (permanentes, com tag). Quem e do mesmo grupo, ou do mesmo cla
 * (com fogo amigo desligado), nao se machuca: espada, flecha, pocao, fogo, TNT, lobo. Os poderes das classes
 * (plugin Poderes) tambem perguntam aqui antes de acertar alguem (aliados()).
 */
public final class Clas extends JavaPlugin implements Listener {

    static final String PG = "§3§lGrupo §8» §7", PC = "§5§lClã §8» §7";

    final Registro r = new Registro();
    private File arquivo;
    /** Quem saiu do jogo e quando (some do grupo depois de alguns minutos). */
    private final Map<UUID, Long> saiu = new HashMap<>();
    private final Map<UUID, Long> recargaBase = new HashMap<>();
    private final Map<UUID, Integer> esperandoBase = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        r.maxGrupo = getConfig().getInt("grupo.maximo", 8);
        r.conviteGrupoMs = getConfig().getLong("grupo.convite-segundos", 120) * 1000L;
        r.maxCla = getConfig().getInt("cla.maximo", 20);
        r.tagMin = getConfig().getInt("cla.tag-min", 2);
        r.tagMax = getConfig().getInt("cla.tag-max", 5);
        r.nomeMax = getConfig().getInt("cla.nome-max", 24);
        r.conviteClaMs = getConfig().getLong("cla.convite-segundos", 300) * 1000L;
        arquivo = new File(getDataFolder(), "clas.yml");
        r.ler(YamlConfiguration.loadConfiguration(arquivo));
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) entrou(p);
        // quem esta offline ha muito tempo sai do grupo
        Bukkit.getScheduler().runTaskTimer(this, this::limparOffline, 1200L, 1200L);
        getLogger().info(r.clas.size() + " clas carregados.");
    }

    @Override
    public void onDisable() {
        salvar();
    }

    void salvar() {
        YamlConfiguration y = new YamlConfiguration();
        r.salvar(y);
        try {
            y.save(arquivo);
        } catch (IOException e) {
            getLogger().warning("Nao consegui salvar clas.yml: " + e.getMessage());
        }
    }

    static Component txt(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    // ================================================================== para os outros plugins

    /** Os dois sao do mesmo grupo ou cla (nao se machucam)? Na Arena PvP (mundos em pvp-livre) todo mundo pode lutar. */
    public boolean aliados(Player a, Player b) {
        if (a == null || b == null) return false;
        if (pvpLivre(a.getWorld()) && pvpLivre(b.getWorld())) return false;
        return r.aliados(a.getUniqueId(), b.getUniqueId());
    }

    /** Mundo onde o cla/grupo nao protege (Arena PvP). */
    public boolean pvpLivre(org.bukkit.World w) {
        java.util.List<String> l = getConfig().getStringList("pvp-livre");
        if (l.isEmpty()) l = java.util.List.of("arena");
        return w != null && l.contains(w.getName());
    }

    /** Tag do cla do jogador (ex.: "ABC"), ou null. */
    /** Membros online do grupo do jogador (sem ele); vazio se nao tem grupo. Usado pelo Masmorras (bussola do grupo). */
    public java.util.List<Player> membrosDoGrupo(Player p) {
        Registro.Grupo g = r.grupoDe.get(p.getUniqueId());
        java.util.List<Player> l = new java.util.ArrayList<>();
        if (g == null) return l;
        for (java.util.UUID u : g.membros) {
            if (u.equals(p.getUniqueId())) continue;
            Player o = Bukkit.getPlayer(u);
            if (o != null && o.isOnline()) l.add(o);
        }
        return l;
    }

    public String tag(Player p) {
        Registro.Cla c = r.claDe.get(p.getUniqueId());
        return c == null ? null : c.tag;
    }

    // ================================================================== entrar e sair

    @EventHandler
    public void aoEntrar(PlayerJoinEvent e) {
        entrou(e.getPlayer());
    }

    private void entrou(Player p) {
        r.nomes.put(p.getUniqueId(), p.getName());
        saiu.remove(p.getUniqueId());
        atualizarTag(p);
        Registro.Grupo g = r.grupoDe.get(p.getUniqueId());
        if (g != null) avisarGrupo(g, PG + "§f" + p.getName() + " §7voltou.", p);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent e) {
        saiu.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        esperandoBase.remove(e.getPlayer().getUniqueId());
    }

    private void limparOffline() {
        long lim = getConfig().getLong("grupo.offline-minutos", 5) * 60_000L, agora = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : new ArrayList<>(saiu.entrySet())) {
            if (agora - e.getValue() < lim) continue;
            saiu.remove(e.getKey());
            Registro.Grupo g = r.sairGrupo(e.getKey());
            if (g != null) {
                avisarGrupo(g, PG + "§f" + r.nome(e.getKey()) + " §7saiu do grupo (ficou fora do jogo).", null);
                if (g.membros.size() > 1) avisarGrupo(g, PG + "Líder agora: §f" + r.nome(g.lider), null);
                else if (g.membros.size() == 1) msgUuid(g.membros.iterator().next(), PG + "O grupo acabou (ficou só você).");
            }
        }
    }

    // ================================================================== sem dano entre aliados

    static Player jogadorDe(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (d instanceof AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        if (d instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void dano(EntityDamageByEntityEvent e) {
        Player a = jogadorDe(e.getDamager());
        if (a == null) return;
        Player v = null;
        if (e.getEntity() instanceof Player p) v = p;
        // o lobo/gato de um aliado tambem nao apanha
        else if (e.getEntity() instanceof Tameable t && t.isTamed() && t.getOwner() instanceof Player dono && !dono.equals(a)) v = dono;
        if (v == null || !aliados(a, v)) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Projectile pr && !(pr instanceof org.bukkit.entity.Trident)) pr.remove();
        boolean grupo = r.grupoDe.get(a.getUniqueId()) != null && r.grupoDe.get(a.getUniqueId()) == r.grupoDe.get(v.getUniqueId());
        a.sendActionBar(txt("§7" + v.getName() + " é do seu " + (grupo ? "grupo" : "clã") + ": sem dano."));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void fogo(EntityCombustByEntityEvent e) {
        if (e.getEntity() instanceof Player v && aliados(jogadorDe(e.getCombuster()), v)) e.setCancelled(true);
    }

    static boolean nocivo(java.util.Collection<PotionEffect> efeitos) {
        for (PotionEffect pe : efeitos) {
            try {
                if (pe.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) return true;
            } catch (Throwable t) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void pocao(PotionSplashEvent e) {
        ThrownPotion tp = e.getPotion();
        if (!(tp.getShooter() instanceof Player a) || !nocivo(tp.getEffects())) return;
        for (LivingEntity le : e.getAffectedEntities()) if (le instanceof Player v && aliados(a, v)) e.setIntensity(le, 0);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void nuvem(AreaEffectCloudApplyEvent e) {
        AreaEffectCloud c = e.getEntity();
        if (!(c.getSource() instanceof Player a)) return;
        List<PotionEffect> ef = new ArrayList<>(c.getCustomEffects());
        if (c.getBasePotionType() != null) ef.addAll(c.getBasePotionType().getPotionEffects());
        if (!nocivo(ef)) return;
        e.getAffectedEntities().removeIf(le -> le instanceof Player v && aliados(a, v));
    }

    // ================================================================== tag no chat e na cabeca

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        if (!getConfig().getBoolean("cla.tag-no-chat", true)) return;
        Registro.Cla c = r.claDe.get(e.getPlayer().getUniqueId());
        if (c == null) return;
        Component tag = txt("§8[§d" + c.tag + "§8] ");
        io.papermc.paper.chat.ChatRenderer antes = e.renderer();
        e.renderer((src, nome, msg, quem) -> tag.append(antes.render(src, nome, msg, quem)));
    }

    /** Tag em cima da cabeca (time do placar "cla_TAG") e no TAB (pelo Poderes, que monta o nome do TAB). */
    void atualizarTag(Player p) {
        Registro.Cla c = r.claDe.get(p.getUniqueId());
        if (getConfig().getBoolean("cla.tag-na-cabeca", true)) {
            try {
                Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
                Team atual = sb.getEntryTeam(p.getName());
                String quer = c == null ? null : "cla_" + c.tag;
                if (atual != null && atual.getName().startsWith("cla_") && !atual.getName().equals(quer)) {
                    atual.removeEntry(p.getName());
                    if (atual.getEntries().isEmpty()) atual.unregister();
                }
                if (quer != null && (atual == null || !atual.getName().equals(quer))) {
                    Team t = sb.getTeam(quer);
                    if (t == null) {
                        t = sb.registerNewTeam(quer);
                        t.prefix(txt("§8[§d" + c.tag + "§8] "));
                        t.setAllowFriendlyFire(true);
                        t.setCanSeeFriendlyInvisibles(false);
                    }
                    if (atual == null || atual.getName().startsWith("cla_")) t.addEntry(p.getName());
                }
            } catch (Exception ex) {
                getLogger().warning("Nao consegui pôr a tag em cima da cabeca: " + ex.getMessage());
            }
        }
        // o Poderes monta o nome do TAB ([Classe] Nome); pede para ele refazer com a tag
        try {
            org.bukkit.plugin.Plugin pod = Bukkit.getPluginManager().getPlugin("Poderes");
            if (pod != null && pod.isEnabled()) pod.getClass().getMethod("atualizarTab", Player.class).invoke(pod, p);
        } catch (Exception ignored) {
            // Poderes antigo: sem tag no TAB
        }
    }

    private void atualizarTag(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) atualizarTag(p);
    }

    // ================================================================== mensagens

    private void msgUuid(UUID u, String m) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) p.sendMessage(txt(m));
    }

    private void avisarGrupo(Registro.Grupo g, String m, Player menos) {
        for (UUID u : g.membros) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && !p.equals(menos)) p.sendMessage(txt(m));
        }
    }

    private void avisarCla(Registro.Cla c, String m) {
        for (UUID u : c.membros) msgUuid(u, m);
    }

    /** Texto com um botao para clicar (no Bedrock aparece so o texto, por isso o comando vai escrito). */
    private static Component botao(String texto, String comando) {
        return txt(texto).clickEvent(ClickEvent.runCommand(comando));
    }

    private Player online(CommandSender s, String nome, String prefixo) {
        Player p = Bukkit.getPlayerExact(nome);
        if (p == null) for (Player o : Bukkit.getOnlinePlayers()) if (o.getName().equalsIgnoreCase(nome) || o.getName().equalsIgnoreCase("." + nome)) p = o;
        if (p == null) s.sendMessage(txt(prefixo + "§c" + nome + " não está online."));
        return p;
    }

    private UUID membroPorNome(String nome) {
        for (Map.Entry<UUID, String> e : r.nomes.entrySet())
            if (e.getValue().equalsIgnoreCase(nome) || e.getValue().equalsIgnoreCase("." + nome)) return e.getKey();
        @SuppressWarnings("deprecation") OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(nome);
        return op == null ? null : op.getUniqueId();
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (c.equals("clan") && a.length >= 2 && a[0].equalsIgnoreCase("admin")) return admin(s, a);
        if (!(s instanceof Player p)) {
            s.sendMessage("Use no jogo.");
            return true;
        }
        r.nomes.put(p.getUniqueId(), p.getName());
        switch (c) {
            case "grupo" -> grupo(p, a);
            case "clan" -> cla(p, a);
            case "gc" -> chatGrupo(p, String.join(" ", a));
            case "cc" -> chatCla(p, String.join(" ", a));
            default -> { }
        }
        return true;
    }

    private void chatGrupo(Player p, String msg) {
        Registro.Grupo g = r.grupoDe.get(p.getUniqueId());
        if (g == null) {
            p.sendMessage(txt(PG + "Você não está em um grupo. §e/grupo convidar <nick>"));
            return;
        }
        if (msg.isBlank()) {
            p.sendMessage(txt(PG + "Use: §e/gc <mensagem>"));
            return;
        }
        avisarGrupo(g, "§3[Grupo] §f" + p.getName() + "§7: §b" + msg.replace("§", ""), null);
    }

    private void chatCla(Player p, String msg) {
        Registro.Cla c = r.claDe.get(p.getUniqueId());
        if (c == null) {
            p.sendMessage(txt(PC + "Você não está em um clã. §e/clan criar <TAG> <nome>"));
            return;
        }
        if (msg.isBlank()) {
            p.sendMessage(txt(PC + "Use: §e/cc <mensagem>"));
            return;
        }
        avisarCla(c, "§5[" + c.tag + "] §f" + p.getName() + "§7: §d" + msg.replace("§", ""));
    }

    private void ajudaGrupo(Player p) {
        p.sendMessage(txt("§3§l--- GRUPO §7(jogar junto; ninguém do grupo se machuca) §3§l---"));
        p.sendMessage(txt("§e/grupo convidar <nick> §7- chama alguém (máx. " + r.maxGrupo + ")"));
        p.sendMessage(txt("§e/grupo aceitar §7- entra no grupo que te chamou"));
        p.sendMessage(txt("§e/grupo sair §7| §e/grupo lista §7| §e/gc <msg> §7(chat do grupo)"));
        p.sendMessage(txt("§7Líder: §e/grupo expulsar <nick> §7| §e/grupo lider <nick>"));
    }

    private void grupo(Player p, String[] a) {
        UUID u = p.getUniqueId();
        long agora = System.currentTimeMillis();
        String op = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (op) {
            case "convidar", "add", "invite", "chamar" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PG + "Use: §e/grupo convidar <nick>"));
                    return;
                }
                Player alvo = online(p, a[1], PG);
                if (alvo == null) return;
                r.nomes.put(alvo.getUniqueId(), alvo.getName());
                String erro = r.convidarGrupo(u, alvo.getUniqueId(), agora);
                if (erro != null) {
                    p.sendMessage(txt(PG + "§c" + erro));
                    return;
                }
                p.sendMessage(txt(PG + "Convite enviado para §f" + alvo.getName() + "§7."));
                alvo.sendMessage(txt(PG + "§f" + p.getName() + " §7te chamou para o grupo dele."));
                alvo.sendMessage(botao("§a§l[ACEITAR] §7digite §e/grupo aceitar " + p.getName(), "/grupo aceitar " + p.getName()));
            }
            case "aceitar", "entrar", "accept" -> {
                UUID de = null;
                if (a.length >= 2) {
                    de = membroPorNome(a[1]);
                    if (de == null) {
                        p.sendMessage(txt(PG + "§cNão achei " + a[1] + "."));
                        return;
                    }
                }
                String erro = r.aceitarGrupo(u, de, agora);
                if (erro != null) {
                    p.sendMessage(txt(PG + "§c" + erro));
                    return;
                }
                Registro.Grupo g = r.grupoDe.get(u);
                avisarGrupo(g, PG + "§f" + p.getName() + " §aentrou no grupo! §7(" + g.membros.size() + "/" + r.maxGrupo + ")", null);
                p.sendMessage(txt(PG + "Agora vocês não se machucam. Chat do grupo: §e/gc <msg>"));
            }
            case "recusar", "negar" -> {
                r.convitesGrupo.remove(u);
                p.sendMessage(txt(PG + "Convites de grupo recusados."));
            }
            case "sair", "leave" -> {
                Registro.Grupo g = r.sairGrupo(u);
                if (g == null) {
                    p.sendMessage(txt(PG + "Você não está em um grupo."));
                    return;
                }
                p.sendMessage(txt(PG + "Você saiu do grupo."));
                avisarGrupo(g, PG + "§f" + p.getName() + " §7saiu do grupo.", null);
                if (g.membros.size() == 1) msgUuid(g.membros.iterator().next(), PG + "O grupo acabou (ficou só você).");
                else if (!g.membros.isEmpty()) avisarGrupo(g, PG + "Líder: §f" + r.nome(g.lider), null);
            }
            case "expulsar", "kick" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PG + "Use: §e/grupo expulsar <nick>"));
                    return;
                }
                UUID alvo = membroPorNome(a[1]);
                String erro = alvo == null ? "Não achei " + a[1] + "." : r.expulsarGrupo(u, alvo);
                if (erro != null) {
                    p.sendMessage(txt(PG + "§c" + erro));
                    return;
                }
                msgUuid(alvo, PG + "§cVocê foi tirado do grupo.");
                Registro.Grupo g = r.grupoDe.get(u);
                if (g != null) avisarGrupo(g, PG + "§f" + r.nome(alvo) + " §7foi tirado do grupo.", null);
                else p.sendMessage(txt(PG + "O grupo acabou (ficou só você)."));
            }
            case "lider", "líder", "leader" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PG + "Use: §e/grupo lider <nick>"));
                    return;
                }
                UUID alvo = membroPorNome(a[1]);
                String erro = alvo == null ? "Não achei " + a[1] + "." : r.liderGrupo(u, alvo);
                if (erro != null) {
                    p.sendMessage(txt(PG + "§c" + erro));
                    return;
                }
                avisarGrupo(r.grupoDe.get(u), PG + "Líder agora: §f" + r.nome(alvo), null);
            }
            case "lista", "info", "membros" -> {
                Registro.Grupo g = r.grupoDe.get(u);
                if (g == null) {
                    p.sendMessage(txt(PG + "Você não está em um grupo. §e/grupo convidar <nick>"));
                    return;
                }
                p.sendMessage(txt("§3§l--- SEU GRUPO §7(" + g.membros.size() + "/" + r.maxGrupo + ") §3§l---"));
                for (UUID m : g.membros) {
                    Player o = Bukkit.getPlayer(m);
                    String onde = o == null ? "§8fora do jogo" : "§7" + (int) Math.ceil(o.getHealth()) + "❤ §8| §7" + mundo(o.getWorld());
                    p.sendMessage(txt((m.equals(g.lider) ? "§6★ " : "§7- ") + "§f" + r.nome(m) + " §8| " + onde));
                }
            }
            case "chat" -> chatGrupo(p, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
            default -> ajudaGrupo(p);
        }
    }

    static String mundo(World w) {
        return switch (w.getName()) {
            case "world" -> "mundo normal";
            case "world_nether" -> "Nether";
            case "world_the_end" -> "End";
            case "plots" -> "plots";
            default -> "minigame";
        };
    }

    private void ajudaCla(Player p) {
        p.sendMessage(txt("§5§l--- CLÃ §7(tag no chat; ninguém do clã se machuca) §5§l---"));
        p.sendMessage(txt("§e/clan criar <TAG> <nome> §7- tag de " + r.tagMin + " a " + r.tagMax + " letras"));
        p.sendMessage(txt("§e/clan convidar <nick> §7| §e/clan aceitar §7| §e/clan sair"));
        p.sendMessage(txt("§e/clan info [TAG] §7| §e/clan lista §7| §e/cc <msg> §7(chat do clã)"));
        p.sendMessage(txt("§e/clan base §7- vai para a base | §e/clan setbase §7- marca a base"));
        p.sendMessage(txt("§7Dono/oficial: §e/clan expulsar <nick>§7. Dono: §e/clan promover|rebaixar|dono <nick>§7,"));
        p.sendMessage(txt("§e/clan fogoamigo §7(liga/desliga dano entre o clã), §e/clan desfazer"));
    }

    private void cla(Player p, String[] a) {
        UUID u = p.getUniqueId();
        long agora = System.currentTimeMillis();
        String op = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        Registro.Cla meu = r.claDe.get(u);
        switch (op) {
            case "criar", "create" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PC + "Use: §e/clan criar <TAG> <nome do clã>"));
                    return;
                }
                String nome = a.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : a[1];
                String erro = r.criarCla(u, a[1], nome, agora);
                if (erro != null) {
                    p.sendMessage(txt(PC + "§c" + erro));
                    return;
                }
                Registro.Cla c = r.claDe.get(u);
                salvar();
                atualizarTag(p);
                Bukkit.broadcast(txt(PC + "§f" + p.getName() + " §7criou o clã §d[" + c.tag + "] §f" + c.nome + "§7!"));
                p.sendMessage(txt(PC + "Chame a galera: §e/clan convidar <nick>§7. Marque a base: §e/clan setbase"));
            }
            case "convidar", "add", "invite", "chamar" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PC + "Use: §e/clan convidar <nick>"));
                    return;
                }
                Player alvo = online(p, a[1], PC);
                if (alvo == null) return;
                r.nomes.put(alvo.getUniqueId(), alvo.getName());
                String erro = r.convidarCla(u, alvo.getUniqueId(), agora);
                if (erro != null) {
                    p.sendMessage(txt(PC + "§c" + erro));
                    return;
                }
                p.sendMessage(txt(PC + "Convite enviado para §f" + alvo.getName() + "§7."));
                alvo.sendMessage(txt(PC + "§f" + p.getName() + " §7te convidou para o clã §d[" + meu.tag + "] §f" + meu.nome + "§7."));
                alvo.sendMessage(botao("§a§l[ACEITAR] §7digite §e/clan aceitar " + meu.tag, "/clan aceitar " + meu.tag));
            }
            case "aceitar", "entrar", "accept" -> {
                String erro = r.aceitarCla(u, a.length >= 2 ? a[1] : null, agora);
                if (erro != null) {
                    p.sendMessage(txt(PC + "§c" + erro));
                    return;
                }
                Registro.Cla c = r.claDe.get(u);
                salvar();
                atualizarTag(p);
                avisarCla(c, PC + "§f" + p.getName() + " §aentrou no clã §d[" + c.tag + "]§a! §7(" + c.membros.size() + "/" + r.maxCla + ")");
            }
            case "recusar", "negar" -> {
                r.convitesCla.remove(u);
                p.sendMessage(txt(PC + "Convites de clã recusados."));
            }
            case "sair", "leave" -> {
                String erro = r.sairCla(u);
                if (erro != null) {
                    p.sendMessage(txt(PC + "§c" + erro));
                    return;
                }
                salvar();
                atualizarTag(p);
                p.sendMessage(txt(PC + "Você saiu do clã §d[" + meu.tag + "]§7."));
                if (r.clas.containsKey(meu.tag)) avisarCla(meu, PC + "§f" + p.getName() + " §7saiu do clã.");
                else p.sendMessage(txt(PC + "O clã acabou (era só você)."));
            }
            case "expulsar", "kick" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PC + "Use: §e/clan expulsar <nick>"));
                    return;
                }
                UUID alvo = membroPorNome(a[1]);
                String erro = alvo == null ? "Não achei " + a[1] + "." : r.expulsarCla(u, alvo);
                if (erro != null) {
                    p.sendMessage(txt(PC + "§c" + erro));
                    return;
                }
                salvar();
                atualizarTag(alvo);
                msgUuid(alvo, PC + "§cVocê foi tirado do clã [" + meu.tag + "].");
                avisarCla(meu, PC + "§f" + r.nome(alvo) + " §7foi tirado do clã.");
            }
            case "promover", "rebaixar" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PC + "Use: §e/clan " + op + " <nick>"));
                    return;
                }
                UUID alvo = membroPorNome(a[1]);
                boolean sobe = op.equals("promover");
                String erro = alvo == null ? "Não achei " + a[1] + "." : r.promover(u, alvo, sobe);
                if (erro != null) {
                    p.sendMessage(txt(PC + "§c" + erro));
                    return;
                }
                salvar();
                avisarCla(meu, PC + "§f" + r.nome(alvo) + (sobe ? " §7agora é §6oficial§7 (convida, expulsa e marca a base)." : " §7não é mais oficial."));
            }
            case "dono" -> {
                if (a.length < 2) {
                    p.sendMessage(txt(PC + "Use: §e/clan dono <nick> §7(passa o clã)"));
                    return;
                }
                UUID alvo = membroPorNome(a[1]);
                String erro = alvo == null ? "Não achei " + a[1] + "." : r.passarDono(u, alvo);
                if (erro != null) {
                    p.sendMessage(txt(PC + "§c" + erro));
                    return;
                }
                salvar();
                avisarCla(meu, PC + "§f" + r.nome(alvo) + " §7agora é o §6dono§7 do clã.");
            }
            case "desfazer", "apagar", "disband" -> {
                if (meu == null || !u.equals(meu.dono)) {
                    p.sendMessage(txt(PC + "§cSó o dono desfaz o clã."));
                    return;
                }
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirmar")) {
                    p.sendMessage(txt(PC + "§cIsso apaga o clã para todos. Para confirmar: §e/clan desfazer confirmar"));
                    return;
                }
                List<UUID> membros = new ArrayList<>(meu.membros);
                avisarCla(meu, PC + "§cO clã [" + meu.tag + "] foi desfeito pelo dono.");
                r.desfazerCla(meu);
                salvar();
                for (UUID m : membros) atualizarTag(m);
            }
            case "fogoamigo", "pvp" -> {
                if (meu == null || !u.equals(meu.dono)) {
                    p.sendMessage(txt(PC + "§cSó o dono muda isso."));
                    return;
                }
                meu.fogoAmigo = !meu.fogoAmigo;
                salvar();
                avisarCla(meu, PC + "Dano entre membros do clã: " + (meu.fogoAmigo ? "§cLIGADO" : "§aDESLIGADO") + " §7(grupo nunca se machuca).");
            }
            case "setbase", "definirbase", "marcarbase" -> {
                if (meu == null || !meu.manda(u)) {
                    p.sendMessage(txt(PC + "§cSó o dono e os oficiais marcam a base."));
                    return;
                }
                if (!mundoDeBase(p.getWorld())) {
                    p.sendMessage(txt(PC + "§cA base só pode ser no mundo normal, Nether, End ou plots."));
                    return;
                }
                Location l = p.getLocation();
                meu.base = l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
                salvar();
                avisarCla(meu, PC + "§f" + p.getName() + " §7marcou a base do clã aqui (" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "). §e/clan base §7leva para lá.");
            }
            case "base", "home" -> irBase(p, meu);
            case "info" -> {
                Registro.Cla c = a.length >= 2 ? r.clas.get(Registro.limparTag(a[1])) : meu;
                if (c == null) {
                    p.sendMessage(txt(PC + (a.length >= 2 ? "§cNão existe o clã [" + a[1] + "]." : "Você não está em um clã. §e/clan criar <TAG> <nome>")));
                    return;
                }
                p.sendMessage(txt("§5§l--- §d[" + c.tag + "] §f" + c.nome + " §5§l---"));
                p.sendMessage(txt("§7Dono: §6" + r.nome(c.dono) + " §8| §7Membros: §f" + c.membros.size() + "/" + r.maxCla
                        + " §8| §7Fogo amigo: " + (c.fogoAmigo ? "§cligado" : "§adesligado")));
                StringBuilder sb = new StringBuilder("§7");
                for (UUID m : c.membros) {
                    if (sb.length() > 2) sb.append("§8, ");
                    boolean on = Bukkit.getPlayer(m) != null;
                    sb.append(m.equals(c.dono) ? "§6★" : c.oficiais.contains(m) ? "§e☆" : "").append(on ? "§a" : "§7").append(r.nome(m));
                }
                p.sendMessage(txt(sb.toString()));
            }
            case "lista", "list" -> {
                if (r.clas.isEmpty()) {
                    p.sendMessage(txt(PC + "Ainda não tem clãs. Crie o primeiro: §e/clan criar <TAG> <nome>"));
                    return;
                }
                p.sendMessage(txt("§5§l--- CLÃS (" + r.clas.size() + ") ---"));
                List<Registro.Cla> l = new ArrayList<>(r.clas.values());
                l.sort((x, y) -> Integer.compare(y.membros.size(), x.membros.size()));
                for (Registro.Cla c : l) p.sendMessage(txt("§d[" + c.tag + "] §f" + c.nome + " §8| §7" + c.membros.size() + " membro(s), dono " + r.nome(c.dono)));
            }
            case "chat" -> chatCla(p, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
            default -> ajudaCla(p);
        }
    }

    private boolean mundoDeBase(World w) {
        return getConfig().getStringList("base.mundos").contains(w.getName());
    }

    private void irBase(Player p, Registro.Cla c) {
        if (c == null) {
            p.sendMessage(txt(PC + "Você não está em um clã."));
            return;
        }
        if (c.base == null) {
            p.sendMessage(txt(PC + "O clã ainda não tem base. O dono ou um oficial marca com §e/clan setbase§7."));
            return;
        }
        if (!mundoDeBase(p.getWorld())) {
            p.sendMessage(txt(PC + "§cDaqui não dá (saia do minigame primeiro)."));
            return;
        }
        String[] s = c.base.split(",");
        World w = Bukkit.getWorld(s[0]);
        if (w == null || s.length < 6) {
            p.sendMessage(txt(PC + "§cA base do clã está num mundo que não existe mais."));
            return;
        }
        long agora = System.currentTimeMillis();
        long rec = getConfig().getLong("base.recarga-segundos", 120) * 1000L;
        Long ultima = recargaBase.get(p.getUniqueId());
        if (ultima != null && agora - ultima < rec && !p.hasPermission("clas.admin")) {
            p.sendMessage(txt(PC + "§cEspere " + ((rec - (agora - ultima)) / 1000 + 1) + " s para ir de novo."));
            return;
        }
        Location destino = new Location(w, Double.parseDouble(s[1]), Double.parseDouble(s[2]), Double.parseDouble(s[3]), Float.parseFloat(s[4]), Float.parseFloat(s[5]));
        int espera = getConfig().getInt("base.espera-segundos", 5);
        Location de = p.getLocation().clone();
        p.sendMessage(txt(PC + "Indo para a base em §f" + espera + " s§7. Não se mexa."));
        int id = Bukkit.getScheduler().runTaskLater(this, () -> {
            esperandoBase.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            if (!mundoDeBase(p.getWorld())) return;
            recargaBase.put(p.getUniqueId(), System.currentTimeMillis());
            p.teleportAsync(destino).thenAccept(ok -> {
                if (ok) p.sendMessage(txt(PC + "Base do clã §d[" + c.tag + "]§7."));
            });
        }, espera * 20L).getTaskId();
        Integer antes = esperandoBase.put(p.getUniqueId(), id);
        if (antes != null) Bukkit.getScheduler().cancelTask(antes);
        posicaoEspera.put(p.getUniqueId(), de);
    }

    private final Map<UUID, Location> posicaoEspera = new HashMap<>();

    private void cancelarBase(Player p, String motivo) {
        Integer id = esperandoBase.remove(p.getUniqueId());
        posicaoEspera.remove(p.getUniqueId());
        if (id == null) return;
        Bukkit.getScheduler().cancelTask(id);
        p.sendActionBar(txt("§cIda para a base cancelada: " + motivo + "."));
    }

    @EventHandler(ignoreCancelled = true)
    public void mexeu(PlayerMoveEvent e) {
        if (!esperandoBase.containsKey(e.getPlayer().getUniqueId())) return;
        Location de = posicaoEspera.get(e.getPlayer().getUniqueId());
        if (de != null && (de.getWorld() != e.getTo().getWorld() || de.distanceSquared(e.getTo()) > 0.5)) cancelarBase(e.getPlayer(), "você se mexeu");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void levouDano(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && esperandoBase.containsKey(p.getUniqueId())) cancelarBase(p, "você levou dano");
    }

    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("clas.admin")) {
            s.sendMessage(txt(PC + "§cSem permissão."));
            return true;
        }
        if (a[1].equalsIgnoreCase("desfazer") && a.length >= 3) {
            Registro.Cla c = r.clas.get(Registro.limparTag(a[2]));
            if (c == null) {
                s.sendMessage(PC + "Não existe o clã [" + a[2] + "].");
                return true;
            }
            List<UUID> membros = new ArrayList<>(c.membros);
            avisarCla(c, PC + "§cO clã [" + c.tag + "] foi desfeito pela administração.");
            r.desfazerCla(c);
            salvar();
            for (UUID m : membros) atualizarTag(m);
            s.sendMessage(PC + "Clã [" + c.tag + "] desfeito.");
        } else s.sendMessage(PC + "Use: clan admin desfazer <TAG>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> l = new ArrayList<>();
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && c.equals("grupo")) l.addAll(List.of("convidar", "aceitar", "recusar", "sair", "lista", "expulsar", "lider", "chat"));
        else if (a.length == 1 && c.equals("clan"))
            l.addAll(List.of("criar", "convidar", "aceitar", "recusar", "sair", "info", "lista", "base", "setbase", "expulsar", "promover", "rebaixar", "dono", "fogoamigo", "desfazer", "chat"));
        else if (a.length == 2 && (c.equals("grupo") || c.equals("clan"))) {
            String op = a[0].toLowerCase(Locale.ROOT);
            if (List.of("convidar", "expulsar", "lider", "promover", "rebaixar", "dono", "aceitar").contains(op))
                for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
            if (c.equals("clan") && (op.equals("info") || op.equals("aceitar"))) l.addAll(r.clas.keySet());
        } else return l;
        String u = a[a.length - 1].toLowerCase(Locale.ROOT);
        l.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(u));
        return l;
    }
}
