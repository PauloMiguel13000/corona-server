package br.servidor.extras;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Extras do servidor:
 *  - /rtp: teleporte aleatorio para um lugar seguro do mapa (com espera e recarga);
 *  - queda segura: quem estava na area do spawn nao toma dano de queda por alguns segundos depois de sair
 *    (para poder pular do spawn alto). O resto da protecao do spawn fica na regiao do WorldGuard.
 */
public final class Extras extends JavaPlugin implements Listener {

    private final Map<UUID, Long> recargaRtp = new HashMap<>();
    private final Map<UUID, BukkitTask> esperando = new HashMap<>();
    private final Map<UUID, Long> saiuDoSpawn = new HashMap<>();
    private final Map<UUID, Long> rtpSemQueda = new HashMap<>();
    private org.bukkit.NamespacedKey chaveMenu;
    private org.bukkit.NamespacedKey chaveRecebeu;
    Placas placas;
    Vazio vazio;
    Vip vip;
    LobbyMinigames lobby;
    LobbyTerror terror;
    SpawnPrincipal spawnNovo;
    Votos votos;
    Divulgacao divulgacao;
    MissoesDiarias missoesDiarias;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chaveMenu = new org.bukkit.NamespacedKey(this, "item_menu");
        chaveRecebeu = new org.bukkit.NamespacedKey(this, "itens_recebidos");
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) darItensNaPrimeiraVez(p);
        placas = new Placas(this);
        vazio = new Vazio(this);
        Bukkit.getPluginManager().registerEvents(vazio, this);
        votos = new Votos(this);
        Bukkit.getPluginManager().registerEvents(votos, this);
        votos.iniciar();
        divulgacao = new Divulgacao(this);
        Bukkit.getPluginManager().registerEvents(divulgacao, this);
        divulgacao.iniciar();
        missoesDiarias = new MissoesDiarias(this);
        missoesDiarias.iniciar();
        // depois que os mundos carregaram: placas do spawn (se a versao mudou) e limpeza embaixo do spawn (uma vez)
        Bukkit.getScheduler().runTaskTimer(this, this::visaoNoSpawn, 60L, 20L);
        lobby = new LobbyMinigames(this);
        Bukkit.getPluginManager().registerEvents(lobby, this);
        try {
            lobby.iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Lobby dos Minigames nao iniciou: " + ex);
        }
        terror = new LobbyTerror(this);
        Bukkit.getPluginManager().registerEvents(terror, this);
        try {
            terror.iniciar();
            new Estatuas(this, terror).iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Lobby do Terror nao iniciou: " + ex);
        }
        spawnNovo = new SpawnPrincipal(this);
        Bukkit.getPluginManager().registerEvents(spawnNovo, this);
        spawnNovo.iniciar();
        vip = new Vip(this);
        Bukkit.getPluginManager().registerEvents(vip, this);
        vip.traje = new TrajeRei(this, vip);
        Bukkit.getPluginManager().registerEvents(vip.traje, this);
        try {
            asas = new AsasRei(this, vip.traje);
            Bukkit.getPluginManager().registerEvents(asas, this);
        } catch (Throwable ex) {
            getLogger().warning("Asas do Traje do Rei (Java) nao ligaram: " + ex);
        }
        vip.iniciar();
        try {
            mochilas = new Mochilas(this);
            Bukkit.getPluginManager().registerEvents(mochilas, this);
            for (Player pj : Bukkit.getOnlinePlayers()) mochilas.conferir(pj);
        } catch (Throwable ex) {
            getLogger().warning("Mochilas separadas nao ligaram: " + ex);
        }
        try {
            entradas = new Entradas(this);
            Bukkit.getPluginManager().registerEvents(entradas, this);
            entradas.iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Entradas (boas-vindas e efeito de VIP) nao ligaram: " + ex);
        }
        try {
            rede = new RedeBedrock(this);
            Bukkit.getPluginManager().registerEvents(rede, this);
        } catch (Throwable ex) {
            getLogger().warning("Rede do Bedrock (distancia e medidor) nao ligou: " + ex);
        }
        try {
            avisoLogin = new AvisoLogin(this);
            Bukkit.getPluginManager().registerEvents(avisoLogin, this);
            avisoLogin.iniciar();
        } catch (Throwable ex) {
            getLogger().warning("Aviso de login na tela nao ligou: " + ex);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                placas.atualizarArquivo();
                placas.conferir();
            } catch (Exception ex) {
                getLogger().warning("Nao consegui colocar as placas do spawn: " + ex);
            }
            vazio.conferir();
        }, 100L);
        if (getConfig().getBoolean("log.silenciar-stun", true)) {
            try {
                filtroStun = FiltroStun.instalar(Math.max(0, getConfig().getLong("log.stun-a-cada-minutos", 30)) * 60_000L);
                // o Geyser liga depois e o log4j pode criar contexto novo ou recarregar a configuracao:
                // confere de novo a cada 30 s e conta no log como ficou (uma vez no inicio e outra depois de 10 min)
                Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                    private int vezes;

                    @Override
                    public void run() {
                        vezes++;
                        try {
                            FiltroStun.reaplicar(filtroStun);
                            if (vezes == 4 || vezes == 22) getLogger().info("Mensagens STUN do NetherNet: " + FiltroStun.resumo(filtroStun) + ".");
                        } catch (Throwable t) {
                            if (vezes == 1) getLogger().warning("Nao consegui conferir o filtro das mensagens STUN: " + t);
                        }
                    }
                }, 200L, 600L);
            } catch (Throwable t) {
                getLogger().warning("Nao consegui diminuir as mensagens STUN do log: " + t);
            }
        }
        getLogger().info("Extras ativo: /rtp e queda segura no spawn (area " + areaTexto() + ").");
    }

    /** Filtro das mensagens "STUN discovery" do NetherNet (fica como Object para nao depender do log4j aqui). */
    private Object filtroStun;
    AsasRei asas;
    RedeBedrock rede;
    AvisoLogin avisoLogin;
    Entradas entradas;
    Mochilas mochilas;

    @Override
    public void onDisable() {
        if (missoesDiarias != null) missoesDiarias.parar();
        if (entradas != null) entradas.parar();
        if (filtroStun != null) {
            try {
                FiltroStun.remover(filtroStun);
            } catch (Throwable ignored) {
            }
            filtroStun = null;
        }
        for (BukkitTask t : esperando.values()) t.cancel();
        esperando.clear();
        if (vazio != null) vazio.parar();
        if (asas != null) asas.parar();
        if (rede != null) rede.parar();
        if (avisoLogin != null) avisoLogin.parar();
    }

    /** Sem dano de queda por um tempo (resgate de quem pula da ilha do spawn). */
    void semQuedaPor(Player p, long ms) {
        rtpSemQueda.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    // ================================================================== plots: monstros nao atacam

    private boolean semAtaqueDeMonstro(World w) {
        return w != null && getConfig().getStringList("plots.mundos-sem-ataque-de-monstro").contains(w.getName());
    }

    /** Quem esta por tras do dano: jogador (ele, a flecha, a TNT, o lobo dele) ou null (monstro, natureza). */
    static Player jogadorDe(org.bukkit.entity.Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof org.bukkit.entity.TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (d instanceof org.bukkit.entity.AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        if (d instanceof org.bukkit.entity.Tameable t && t.isTamed() && t.getOwner() instanceof Player p) return p;
        return null;
    }

    /** Nas plots, monstro nenhum mira em jogador. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void mobNaoMiraNasPlots(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && !(e.getEntity() instanceof Player) && semAtaqueDeMonstro(p.getWorld())
                && !(e.getEntity() instanceof org.bukkit.entity.Tameable t && t.isTamed()) && !ehPet(e.getEntity())) e.setCancelled(true);
    }

    private static final org.bukkit.NamespacedKey PET = new org.bukkit.NamespacedKey("babypets", "pet_entity");

    static boolean ehPet(org.bukkit.entity.Entity e) {
        return e != null && e.getPersistentDataContainer().has(PET);
    }

    /** O pedido de nascer veio do BabyPets? (o pet nasce com spawnEntity e so depois ganha a marca de pet) */
    static boolean vemDoBabyPets() {
        return StackWalker.getInstance().walk(s -> s.limit(120).anyMatch(f -> f.getClassName().startsWith("com.petsplugin.")));
    }

    /**
     * O PlotMe cancela qualquer criatura que nasce fora de um terreno com dono (ruas, terrenos livres) e tambem as
     * "CUSTOM" sem a bandeira de mobs: o pet do BabyPets nao nascia e ficava sumindo/voltando nas plots. Aqui o pet
     * sempre pode nascer (so ele: monstros e animais continuam bloqueados como o PlotMe quer).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void petNasceNasPlots(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (!e.isCancelled() || e.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (!getConfig().getStringList("plots.mundos-pet-livre").contains(e.getLocation().getWorld().getName())) return;
        if (ehPet(e.getEntity()) || vemDoBabyPets()) e.setCancelled(false);
    }

    /** Nas plots, jogador nao toma dano de monstro (mordida, flecha, explosao de creeper...). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void mobNaoBateNasPlots(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !semAtaqueDeMonstro(p.getWorld())) return;
        if (jogadorDe(e.getDamager()) != null) return;
        e.setCancelled(true);
    }

    static Component txt(String legado) {
        return LegacyComponentSerializer.legacySection().deserialize(legado).decoration(TextDecoration.ITALIC, false);
    }

    // ================================================================== area do spawn

    private String areaTexto() {
        List<Integer> a = getConfig().getIntegerList("spawn.min");
        List<Integer> b = getConfig().getIntegerList("spawn.max");
        return getConfig().getString("spawn.mundo", "world") + " " + a + " ate " + b;
    }

    boolean naAreaDoSpawn(Location l) {
        if (l.getWorld() == null || !l.getWorld().getName().equals(getConfig().getString("spawn.mundo", "world"))) return false;
        List<Integer> a = getConfig().getIntegerList("spawn.min");
        List<Integer> b = getConfig().getIntegerList("spawn.max");
        if (a.size() < 3 || b.size() < 3) return false;
        double x = l.getX(), y = l.getY(), z = l.getZ();
        if (PortaisSpawn.naPraca(l)) return true; // a Praca dos Portais (ao norte) tambem e spawn
        return x >= Math.min(a.get(0), b.get(0)) && x < Math.max(a.get(0), b.get(0)) + 1
                && y >= Math.min(a.get(1), b.get(1)) && y < Math.max(a.get(1), b.get(1)) + 1
                && z >= Math.min(a.get(2), b.get(2)) && z < Math.max(a.get(2), b.get(2)) + 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void marcarArea(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        if (naAreaDoSpawn(e.getTo())) saiuDoSpawn.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /** Sem dano de queda dentro da area, e por alguns segundos depois de sair dela (pular do alto) ou depois do /rtp. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void quedaSegura(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        long agora = System.currentTimeMillis();
        if (naAreaDoSpawn(p.getLocation())) {
            e.setCancelled(true);
            return;
        }
        long graca = getConfig().getLong("spawn.sem-queda-segundos", 15) * 1000L;
        Long saiu = saiuDoSpawn.get(p.getUniqueId());
        if (saiu != null && agora - saiu <= graca) {
            e.setCancelled(true);
            return;
        }
        Long rtp = rtpSemQueda.get(p.getUniqueId());
        if (rtp != null && agora < rtp) e.setCancelled(true);
    }

    /** Protecao extra: dentro da area do spawn, jogador nao toma dano nenhum (alem do que o WorldGuard ja faz). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void semDanoNoSpawn(EntityDamageEvent e) {
        if (!getConfig().getBoolean("spawn.sem-dano", true)) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.KILL) return;
        if (naAreaDoSpawn(p.getLocation())) {
            e.setCancelled(true);
            if (p.getFireTicks() > 0) p.setFireTicks(0);
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BukkitTask t = esperando.remove(id);
        if (t != null) t.cancel();
        saiuDoSpawn.remove(id);
    }

    // ================================================================== /rtp

    private boolean inseguro(Material m) {
        return switch (m) {
            case WATER, LAVA, MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW, SWEET_BERRY_BUSH,
                 POINTED_DRIPSTONE, WITHER_ROSE, BUBBLE_COLUMN, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS -> true;
            default -> m.name().endsWith("_LEAVES");
        };
    }

    private final java.util.Map<UUID, Integer> visaoAplicada = new java.util.HashMap<>();

    /**
     * Quanto o servidor manda de mapa para cada jogador. Na ilha do spawn tudo em volta e aberto e o jogo desenha
     * todos os chunks em volta: ali manda menos (spawn.distancia-visao). Quem joga pelo celular/Bedrock (Geyser)
     * recebe sempre menos mapa (celular.distancia-visao), porque o celular trava desenhando muitos chunks.
     * -1 = o normal do servidor.
     */
    private void visaoNoSpawn() {
        int vd = getConfig().getInt("spawn.distancia-visao", 5);
        int cel = getConfig().getInt("celular.distancia-visao", 4);
        World w = vazio.mundo();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            boolean dentro = vd >= 2 && w != null && l.getWorld() == w && l.getY() >= 90
                    && Math.hypot(l.getX() - vazio.cx(), l.getZ() - vazio.cz()) <= vazio.raio() + 20;
            int alvo = -1;
            if (dentro) alvo = vd;
            if (cel >= 2 && ehBedrock(p)) alvo = alvo < 0 ? cel : Math.min(alvo, cel);
            int normal = p.getWorld().getSendViewDistance();
            if (alvo >= normal) alvo = -1;
            UUID u = p.getUniqueId();
            Integer antes = visaoAplicada.get(u);
            if (antes != null && antes == alvo) continue;
            if (antes == null && alvo == -1) continue;
            try {
                p.setSendViewDistance(alvo);
                if (alvo == -1) visaoAplicada.remove(u);
                else visaoAplicada.put(u, alvo);
            } catch (RuntimeException ex) {
                visaoAplicada.remove(u);
            }
        }
        visaoAplicada.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }

    /** Jogador do Bedrock (celular, console, Windows) entrando pelo Geyser/Floodgate: UUID com a parte alta zerada. */
    static boolean ehBedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L || p.getName().startsWith(".");
    }

    /** Tem terreno de alguem (GriefPrevention) neste lugar? */
    boolean temTerreno(Location l) {
        if (!Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) return false;
        try {
            return TerrenoGP.temTerreno(l);
        } catch (Throwable t) {
            return false;
        }
    }

    private void procurarLugar(Player p, World w, int tentativa) {
        int min = getConfig().getInt("rtp.raio-minimo", 500);
        int max = Math.max(min + 50, getConfig().getInt("rtp.raio-maximo", 5000));
        int tentativas = getConfig().getInt("rtp.tentativas", 12);
        if (tentativa > tentativas) {
            p.sendMessage("§cNão achei um lugar seguro agora. Tente de novo daqui a pouco.");
            recargaRtp.remove(p.getUniqueId());
            return;
        }
        Location centro = w.getSpawnLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble(Math.PI * 2);
        double dist = min + r.nextDouble() * (max - min);
        int x = (int) Math.floor(centro.getX() + Math.cos(ang) * dist);
        int z = (int) Math.floor(centro.getZ() + Math.sin(ang) * dist);
        WorldBorder borda = w.getWorldBorder();
        if (!borda.isInside(new Location(w, x + 0.5, 64, z + 0.5))) {
            procurarLugar(p, w, tentativa + 1);
            return;
        }
        w.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk -> {
            if (!p.isOnline()) return;
            Block topo = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block pe = topo.getRelative(BlockFace.UP);
            boolean ok = topo.getType().isSolid() && !inseguro(topo.getType())
                    && pe.isPassable() && !pe.isLiquid() && !inseguro(pe.getType())
                    && pe.getRelative(BlockFace.UP).isPassable()
                    && topo.getY() > w.getMinHeight() + 1
                    && (w.getEnvironment() != World.Environment.NETHER || topo.getY() < 120);
            Location destino = new Location(w, x + 0.5, pe.getY(), z + 0.5, p.getLocation().getYaw(), 0f);
            if (!ok || temTerreno(destino) || naAreaDoSpawn(destino)) {
                procurarLugar(p, w, tentativa + 1);
                return;
            }
            Location origem = p.getLocation();
            p.teleportAsync(destino, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(foi -> {
                if (!foi) {
                    p.sendMessage("§cNão deu para te teleportar (área protegida?). Tente de novo.");
                    recargaRtp.remove(p.getUniqueId());
                    return;
                }
                rtpSemQueda.put(p.getUniqueId(), System.currentTimeMillis() + 6000L);
                p.setFallDistance(0);
                origem.getWorld().spawnParticle(Particle.PORTAL, origem.add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.4);
                w.spawnParticle(Particle.PORTAL, destino.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.4);
                p.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                int distSpawn = (int) Math.round(Math.hypot(x - centro.getX(), z - centro.getZ()));
                p.showTitle(net.kyori.adventure.title.Title.title(txt("§a§lTELEPORTADO!"), txt("§7X §f" + x + " §7Z §f" + z + " §8(" + distSpawn + " blocos do spawn)")));
                p.sendMessage("§aVocê foi para §fX " + x + ", Y " + pe.getY() + ", Z " + z + "§a. Use §e/spawn §apara voltar.");
            });
        });
    }

    private void rtp(Player p) {
        if (!p.hasPermission("extras.rtp")) {
            p.sendMessage("§cSem permissão.");
            return;
        }
        List<String> mundos = getConfig().getStringList("rtp.mundos");
        World w = p.getWorld();
        if (!mundos.isEmpty() && !mundos.contains(w.getName())) {
            String destinoNome = getConfig().getString("rtp.mundo-padrao", "world");
            World padrao = Bukkit.getWorld(destinoNome);
            if (padrao == null || !mundos.contains(padrao.getName())) {
                p.sendMessage("§cO /rtp não funciona neste mundo.");
                return;
            }
            w = padrao;
        }
        UUID id = p.getUniqueId();
        if (esperando.containsKey(id)) {
            p.sendMessage("§eVocê já está esperando o teleporte.");
            return;
        }
        long agora = System.currentTimeMillis();
        long recarga = getConfig().getLong("rtp.recarga-segundos", 300) * 1000L;
        Long ultimo = recargaRtp.get(id);
        if (ultimo != null && agora - ultimo < recarga && !p.hasPermission("extras.rtp.semrecarga")) {
            long falta = (recarga - (agora - ultimo) + 999) / 1000;
            p.sendMessage("§cAguarde §f" + (falta >= 60 ? (falta / 60) + "min " + (falta % 60) + "s" : falta + "s") + " §cpara usar o /rtp de novo.");
            return;
        }
        int espera = Math.max(0, getConfig().getInt("rtp.espera-segundos", 3));
        final World mundo = w;
        if (espera == 0 || p.hasPermission("extras.rtp.semespera") || p.getGameMode() == GameMode.CREATIVE) {
            recargaRtp.put(id, agora);
            p.sendMessage("§aProcurando um lugar seguro...");
            procurarLugar(p, mundo, 1);
            return;
        }
        Location inicio = p.getLocation().clone();
        p.sendMessage("§aTeleportando em §f" + espera + "s§a. Não se mexa!");
        final int[] resta = {espera};
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!p.isOnline()) {
                BukkitTask x = esperando.remove(id);
                if (x != null) x.cancel();
                return;
            }
            Location aqui = p.getLocation();
            if (!aqui.getWorld().equals(inicio.getWorld()) || aqui.distanceSquared(inicio) > 0.6) {
                BukkitTask x = esperando.remove(id);
                if (x != null) x.cancel();
                p.sendActionBar(txt("§cTeleporte cancelado: você se mexeu."));
                return;
            }
            if (resta[0] <= 0) {
                BukkitTask x = esperando.remove(id);
                if (x != null) x.cancel();
                recargaRtp.put(id, System.currentTimeMillis());
                p.sendActionBar(txt("§aProcurando um lugar seguro..."));
                procurarLugar(p, mundo, 1);
                return;
            }
            p.sendActionBar(txt("§eTeleporte aleatório em §f" + resta[0] + "s §7(não se mexa)"));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
            resta[0]--;
        }, 0L, 20L);
        esperando.put(id, t);
    }

    @EventHandler(ignoreCancelled = true)
    public void cancelarSeLevarDano(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        BukkitTask t = esperando.remove(p.getUniqueId());
        if (t != null) {
            t.cancel();
            p.sendActionBar(txt("§cTeleporte cancelado: você levou dano."));
        }
    }

    // ================================================================== itens de menu

    /** Os 4 itens: id, material, nome, descricao, comando que o item executa. */
    private enum ItemMenu {
        MINIGAMES("minigames", Material.COMPASS, "§6§lMinigames", "§7Defesa, Porcelana, Vovó, Backrooms, Hotel, Não Chore, Arena PvP, Turno e Fábrica", "minigames"),
        CLASSES("classes", Material.NETHER_STAR, "§d§lClasses", "§7Escolha ou troque a sua classe", "classe"),
        PETS("pets", Material.BONE, "§a§lPets", "§7Veja e chame os seus pets", "pets"),
        HABILIDADES("habilidades", Material.BOOK, "§b§lHabilidades", "§7Liga/desliga a barra de habilidades (slots 1 a 5)", "poderes"),
        MASMORRAS("masmorras", Material.RECOVERY_COMPASS, "§4§lMasmorras", "§7Veja as masmorras e ganhe uma bússola até ela", "masmorras menu");

        final String id;
        final Material material;
        final String nome;
        final String desc;
        final String comando;

        ItemMenu(String id, Material material, String nome, String desc, String comando) {
            this.id = id;
            this.material = material;
            this.nome = nome;
            this.desc = desc;
            this.comando = comando;
        }

        static ItemMenu porId(String s) {
            for (ItemMenu m : values()) {
                if (m.id.equals(s)) return m;
            }
            return null;
        }
    }

    private org.bukkit.inventory.ItemStack criarItem(ItemMenu m) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(m.material);
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        meta.displayName(txt(m.nome + " §7(clique direito)"));
        meta.lore(List.of(txt(m.desc), txt(""), txt("§8Perdeu? Pegue de novo no §f/kit§8.")));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(chaveMenu, org.bukkit.persistence.PersistentDataType.STRING, m.id);
        it.setItemMeta(meta);
        return it;
    }

    private ItemMenu itemMenu(org.bukkit.inventory.ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(chaveMenu, org.bukkit.persistence.PersistentDataType.STRING);
        return id == null ? null : ItemMenu.porId(id);
    }

    /** Versao dos itens de menu (2 = entrou o item Masmorras): quem tinha a 1 ganha so o item novo. */
    static final byte VERSAO_ITENS = 2;

    /** Da um item de menu se o jogador ainda nao tem. */
    void darItem(Player p, ItemMenu m, int slot) {
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) if (itemMenu(it) == m) return;
        org.bukkit.inventory.ItemStack it = criarItem(m);
        org.bukkit.inventory.ItemStack noSlot = p.getInventory().getItem(slot);
        if (noSlot == null || noSlot.getType().isAir()) p.getInventory().setItem(slot, it);
        else for (org.bukkit.inventory.ItemStack resto : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), resto);
    }

    /** Da os itens que o jogador ainda nao tem. Retorna quantos deu. */
    int darItens(Player p) {
        java.util.Set<ItemMenu> tem = java.util.EnumSet.noneOf(ItemMenu.class);
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
            ItemMenu m = itemMenu(it);
            if (m != null) tem.add(m);
        }
        int dados = 0;
        int[] slots = {5, 6, 7, 8, 4};
        ItemMenu[] todos = ItemMenu.values();
        for (int i = 0; i < todos.length; i++) {
            if (tem.contains(todos[i])) continue;
            org.bukkit.inventory.ItemStack it = criarItem(todos[i]);
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            org.bukkit.inventory.ItemStack noSlot = inv.getItem(slots[i]);
            if (noSlot == null || noSlot.getType().isAir()) {
                inv.setItem(slots[i], it);
            } else {
                for (org.bukkit.inventory.ItemStack resto : inv.addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), resto);
            }
            dados++;
        }
        return dados;
    }

    /** Quem ainda nao tinha recebido os itens ganha uma vez (depois de logar no AuthMe). */
    private void darItensNaPrimeiraVez(Player p) {
        if (!getConfig().getBoolean("itens-menu.dar-na-primeira-vez", true)) return;
        Byte v = p.getPersistentDataContainer().get(chaveRecebeu, org.bukkit.persistence.PersistentDataType.BYTE);
        if (v != null && v >= VERSAO_ITENS) return;
        final boolean novo = v == null;
        final int[] tentativas = {0};
        Bukkit.getScheduler().runTaskTimer(this, tarefa -> {
            tentativas[0]++;
            if (!p.isOnline() || tentativas[0] > 600) {
                tarefa.cancel();
                return;
            }
            if (!logado(p)) return;
            tarefa.cancel();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Byte w = p.getPersistentDataContainer().get(chaveRecebeu, org.bukkit.persistence.PersistentDataType.BYTE);
                if (!p.isOnline() || (w != null && w >= VERSAO_ITENS)) return;
                if (novo) {
                    darItens(p);
                    if (getConfig().getBoolean("kit-inicial", true)) {
                        KitBasico.dar(p);
                        p.sendMessage("§6§l>> §eBem-vindo! Você ganhou o §6§lKit Corona Básico§e: armadura, ferramentas, §lPá de Terreno §ee comida.");
                    }
                } else darItem(p, ItemMenu.MASMORRAS, 4);
                p.getPersistentDataContainer().set(chaveRecebeu, org.bukkit.persistence.PersistentDataType.BYTE, VERSAO_ITENS);
                if (!novo) {
                    p.sendMessage("§6§l>> §eItem novo na barra: §4§lMasmorras §7(bússola escura): mostra as masmorras e dá uma bússola até a que você escolher.");
                    return;
                }
                p.sendMessage("§6§l>> §eVocê ganhou os itens do servidor §7(barra de atalhos): §6Minigames§7, §dClasses§7, §aPets§7, §bHabilidades §7e §4Masmorras§7.");
                p.sendMessage("§7Clique com o botão direito segurando o item. Perdeu? Pegue de novo no §f/kit§7 (a cada 1 hora).");
            }, 60L);
        }, 20L, 20L);
    }

    /** Quem ainda nao fez /login no AuthMe. */
    static boolean logado(Player p) {
        if (!Bukkit.getPluginManager().isPluginEnabled("AuthMe")) return true;
        try {
            Class<?> c = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
            Object api = c.getMethod("getInstance").invoke(null);
            if (api == null) return true;
            return (Boolean) c.getMethod("isAuthenticated", Player.class).invoke(api, p);
        } catch (Throwable t) {
            return true;
        }
    }

    @EventHandler
    public void aoEntrar(org.bukkit.event.player.PlayerJoinEvent e) {
        darItensNaPrimeiraVez(e.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, () -> atualizarItensMenu(e.getPlayer()), 30L);
    }

    /** Troca os itens de menu que o jogador ja tem por copias novas (nome/descricao atualizados), no mesmo slot. */
    void atualizarItensMenu(Player p) {
        if (!p.isOnline()) return;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack it = inv.getItem(i);
            ItemMenu m = itemMenu(it);
            if (m == null) continue;
            org.bukkit.inventory.ItemStack novo = criarItem(m);
            if (!novo.isSimilar(it)) inv.setItem(i, novo);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void usarItemMenu(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        org.bukkit.event.block.Action a = e.getAction();
        if (a != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && a != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemMenu m = itemMenu(e.getItem());
        if (m == null) return;
        e.setCancelled(true);
        abrir(e.getPlayer(), m);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void usarItemMenuEmEntidade(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        ItemMenu m = itemMenu(e.getPlayer().getInventory().getItem(e.getHand()));
        if (m == null) return;
        e.setCancelled(true);
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) abrir(e.getPlayer(), m);
    }

    private final Map<UUID, Long> ultimoMenu = new HashMap<>();

    private void abrir(Player p, ItemMenu m) {
        long agora = System.currentTimeMillis();
        Long antes = ultimoMenu.put(p.getUniqueId(), agora);
        if (antes != null && agora - antes < 400) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        if (!p.performCommand(m.comando)) p.sendMessage("§cNão consegui abrir agora (" + m.comando + ").");
    }

    /** Os itens de menu nao servem para craftar, bigorna, rebolo ou ferraria. */
    @EventHandler
    public void naoCraftar(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        for (org.bukkit.inventory.ItemStack it : e.getInventory().getMatrix()) {
            if (itemMenu(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    private boolean temItemMenu(org.bukkit.inventory.Inventory inv) {
        for (org.bukkit.inventory.ItemStack it : inv.getContents()) {
            if (itemMenu(it) != null) return true;
        }
        return false;
    }

    @EventHandler
    public void naoUsarNaBigorna(org.bukkit.event.inventory.PrepareAnvilEvent e) {
        if (temItemMenu(e.getInventory())) e.setResult(null);
    }

    @EventHandler
    public void naoUsarNoRebolo(org.bukkit.event.inventory.PrepareGrindstoneEvent e) {
        if (temItemMenu(e.getInventory())) e.setResult(null);
    }

    @EventHandler
    public void naoUsarNaFerraria(org.bukkit.event.inventory.PrepareSmithingEvent e) {
        if (temItemMenu(e.getInventory())) e.setResult(null);
    }

    // ================================================================== menu de minigames

    private static final class MenuMinigames implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inv;

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inv;
        }
    }

    private static org.bukkit.inventory.ItemStack icone(Material m, String nome, String... lore) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(m);
        org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
        im.displayName(txt(nome));
        List<Component> l = new java.util.ArrayList<>();
        for (String s : lore) l.add(txt(s));
        im.lore(l);
        it.setItemMeta(im);
        return it;
    }

    private void abrirMinigames(Player p) {
        MenuMinigames h = new MenuMinigames();
        h.inv = Bukkit.createInventory(h, 27, txt("§6§lMinigames"));
        org.bukkit.inventory.ItemStack vidro = icone(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) h.inv.setItem(i, vidro);
        boolean defesa = Bukkit.getPluginManager().isPluginEnabled("DefesaTorre");
        boolean slender = Bukkit.getPluginManager().isPluginEnabled("Slender");
        boolean vovo = Bukkit.getPluginManager().isPluginEnabled("Vovo");
        boolean backrooms = Bukkit.getPluginManager().isPluginEnabled("Backrooms");
        boolean hotel = Bukkit.getPluginManager().isPluginEnabled("Hotel");
        boolean naochore = Bukkit.getPluginManager().isPluginEnabled("NaoChore");
        boolean arena = Bukkit.getPluginManager().isPluginEnabled("CoronaArena");
        boolean turno = Bukkit.getPluginManager().isPluginEnabled("TurnoNoite");
        boolean fabrica = Bukkit.getPluginManager().isPluginEnabled("FabricaAlegria");
        h.inv.setItem(26, icone(Material.CARROT_ON_A_STICK, "§6§lFábrica Alegria",
                "§7Uma fábrica de brinquedos abandonada.", "§7Use a §9Luva de Gancho §7para puxar,", "§7ligar a energia e abrir caminho...",
                "§7e fuja do §cTamanduá Tutu §7nos dutos.", "§aCo-op: 1 a 4 jogadores.", "",
                fabrica ? "§eClique para entrar na fila" : "§cIndisponível agora"));
        h.inv.setItem(18, icone(Material.CLOCK, "§e§lTurno da Noite",
                "§7Você é o vigia de um zoo de bichos", "§7mecânicos. Portas, luzes e câmeras", "§7gastam energia. Sobreviva até as 6.", "§75 noites + noite personalizada.", "",
                turno ? "§eClique para escolher a noite" : "§cIndisponível agora"));
        h.inv.setItem(10, icone(Material.BOW, "§6§lDefesa da Torre",
                "§7Construa torres e defenda o Núcleo", "§7das ondas de monstros.", "§7Vários mapas e dificuldades.", "",
                defesa ? "§eClique para escolher o mapa" : "§cIndisponível agora"));
        h.inv.setItem(12, icone(Material.WITHER_SKELETON_SKULL, "§4§lO Porcelana",
                "§7Terror na floresta escura:", "§7colete as §f8 folhas §7antes que", "§7o Porcelana te pegue.", "",
                slender ? "§eClique para jogar" : "§cIndisponível agora"));
        h.inv.setItem(14, icone(Material.IRON_DOOR, "§5§lCasa da Vovó",
                "§7Você acordou trancado na casa", "§7da vovó. Ache chaves e ferramentas", "§7e fuja sem fazer barulho.", "§7Modos §aNormal §7e §cDifícil§7.", "",
                vovo ? "§eClique para jogar" : "§cIndisponível agora"));
        h.inv.setItem(16, icone(Material.YELLOW_GLAZED_TERRACOTTA, "§e§lBackrooms",
                "§7Você saiu da realidade.", "§7Atravesse §f8 níveis§7 que mudam de ordem", "§7a cada partida. Fuja das criaturas!", "§aFácil§7, §eNormal §7e §cDifícil§7.", "",
                backrooms ? "§eClique para jogar" : "§cIndisponível agora"));
        h.inv.setItem(22, icone(Material.DARK_OAK_DOOR, "§4§lO Hotel",
                "§7Abra §f100 portas§7 de um hotel que", "§7se monta enquanto vocês andam.", "§7Luzes piscando? §fEsconda-se no armário!§7", "§7A campainha tocou? §fNão se mexa!", "§7Ache os livros sem acordar a Figura.", "",
                hotel ? "§eClique para jogar" : "§cIndisponível agora"));
        h.inv.setItem(20, icone(Material.SKELETON_SKULL, "§4§lNão Chore",
                "§7Sobreviva §f5 dias§7 num mundo novo.", "§7Eles ficam escondidos te olhando...", "§7e no último dia vêm te buscar.", "§cTerror de verdade.", "",
                naochore ? "§eClique para entrar na fila" : "§cIndisponível agora"));
        h.inv.setItem(24, icone(Material.NETHERITE_SWORD, "§c§lArena PvP",
                "§7Lute contra outros jogadores", "§7usando os §fpoderes das classes§7.", "§aNinguém perde itens nem XP.", "",
                arena ? "§eClique para entrar" : "§cIndisponível agora"));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void cliqueMinigames(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuMinigames)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;
        String comando = switch (e.getSlot()) {
            case 10 -> "defesa mapas";
            case 12 -> "slender";
            case 14 -> "vovo";
            case 16 -> "backrooms";
            case 22 -> "hotel entrar";
            case 20 -> "naochore entrar";
            case 24 -> "arena entrar";
            case 18 -> "turno";
            case 26 -> "fabrica";
            default -> null;
        };
        if (comando == null) return;
        p.closeInventory();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        if (!p.performCommand(comando)) p.sendMessage("§cEsse minigame não está disponível agora.");
    }

    @EventHandler
    public void arrastarMinigames(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuMinigames) e.setCancelled(true);
    }

    // ================================================================== comandos

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("vip")) return vip.comando(s, args);
        if (cmd.getName().equalsIgnoreCase("rtp")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Somente jogadores.");
                return true;
            }
            rtp(p);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("ec")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Somente jogadores.");
                return true;
            }
            if (!p.hasPermission("extras.ec")) {
                p.sendMessage("§cSem permissão.");
                return true;
            }
            if (args.length > 0 && p.hasPermission("extras.ec.outros")) {
                Player alvo = Bukkit.getPlayerExact(args[0]);
                if (alvo == null) {
                    p.sendMessage("§cJogador não encontrado (precisa estar online).");
                    return true;
                }
                p.openInventory(alvo.getEnderChest());
                p.sendMessage("§7Abrindo o baú do Ender de §f" + alvo.getName() + "§7.");
                return true;
            }
            p.openInventory(p.getEnderChest());
            p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1f);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("votar")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Somente jogadores.");
                return true;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("pegar") || args[0].equalsIgnoreCase("premio") || args[0].equalsIgnoreCase("prêmio"))) {
                p.sendMessage(txt("§7Procurando os seus votos..."));
                votos.conferir(p, true);
            } else votos.mostrar(p);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("ip")) {
            if (s instanceof Player p) divulgacao.ip(p);
            else s.sendMessage("Java: " + getConfig().getString("divulgacao.ip-java") + " | Bedrock: " + getConfig().getString("divulgacao.ip-bedrock") + " porta " + getConfig().getString("divulgacao.porta-bedrock"));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("missoes")) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("completar") && s.hasPermission("extras.admin")) {
                // dono testando: /missoes completar <nick>
                Player alvo = Bukkit.getPlayerExact(args[1]);
                if (alvo == null) s.sendMessage("Jogador offline.");
                else {
                    missoesDiarias.completar(alvo);
                    s.sendMessage("Missoes de hoje de " + alvo.getName() + " terminadas (o premio ele pega no /missoes).");
                }
                return true;
            }
            if (s instanceof Player p) missoesDiarias.abrir(p);
            else s.sendMessage("Somente jogadores.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("discord")) {
            divulgacao.discord(s);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("indicar")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Somente jogadores.");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(txt("§eUse: §f/indicar <nick de quem te chamou> §7(os dois ganham prêmio)"));
                return true;
            }
            divulgacao.indicar(p, args[0]);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("terror")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Somente jogadores.");
                return true;
            }
            if (terror == null || terror.mundo == null) p.sendMessage(txt("§cO Lobby do Terror não está ligado."));
            else if (terror.ehTerror(p.getWorld())) abrirMinigames(p);
            else terror.teleportar(p);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("minigames")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Somente jogadores.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("menu") || lobby == null || lobby.mundo == null) abrirMinigames(p);
            else if (lobby.ehLobby(p.getWorld())) abrirMinigames(p);
            else lobby.teleportar(p);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("extras")) {
            if (!s.hasPermission("extras.admin")) {
                s.sendMessage("§cSem permissão.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("recarregar")) {
                reloadConfig();
                s.sendMessage("§aConfig recarregada. Área do spawn: " + areaTexto());
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("asas")) {
                s.sendMessage("§6Asas do Traje do Rei (Java): §f" + (asas == null ? "desligadas" : asas.resumo()));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("rede")) {
                if (rede == null) s.sendMessage("§cMedidor de rede desligado.");
                else rede.comando(s);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("refazerterror")) {
                if (terror == null) s.sendMessage("§cLobby do Terror desligado.");
                else terror.refazer(s);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("conferirterror")) {
                if (terror == null) s.sendMessage("§cLobby do Terror desligado.");
                else terror.conferir(s);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("refazerspawn")) {
                if (spawnNovo == null) s.sendMessage("§cSpawn novo desligado.");
                else spawnNovo.refazer(s);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("daritens")) {
                Player alvo = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (s instanceof Player pp ? pp : null);
                if (alvo == null) {
                    s.sendMessage("§cJogador não encontrado.");
                    return true;
                }
                int n = darItens(alvo);
                alvo.getPersistentDataContainer().set(chaveRecebeu, org.bukkit.persistence.PersistentDataType.BYTE, VERSAO_ITENS);
                alvo.sendMessage(n > 0 ? "§aVocê recebeu " + n + " item(ns) do servidor de volta!" : "§eVocê já tem todos os itens do servidor.");
                if (!s.equals(alvo)) s.sendMessage("Itens dados para " + alvo.getName() + ": " + n);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("kitbasico")) {
                Player alvo = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (s instanceof Player pp ? pp : null);
                if (alvo == null) {
                    s.sendMessage("§cJogador não encontrado.");
                    return true;
                }
                KitBasico.dar(alvo);
                alvo.sendMessage("§6§l>> §eVocê recebeu o §6§lKit Corona Básico§e!");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("aqui") && s instanceof Player p) {
                s.sendMessage("§eVocê está " + (naAreaDoSpawn(p.getLocation()) ? "§aDENTRO" : "§cFORA") + " §eda área do spawn (" + areaTexto() + ").");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("placas")) {
                placas.atualizarArquivo();
                s.sendMessage("§aPlacas do spawn: " + placas.colocar(s) + ".");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("vazio")) {
                if (args.length > 1 && args[1].equalsIgnoreCase("selar")) {
                    vazio.selado.delete();
                    vazio.selar();
                    s.sendMessage("§aFechando as cavernas da parede do buraco (aos poucos). §7/extras vazio mostra como está.");
                } else if (args.length > 1 && args[1].equalsIgnoreCase("fazer")) {
                    vazio.limpar();
                    s.sendMessage("§aLimpeza embaixo do spawn começou (aos poucos). §7/extras vazio mostra como está.");
                } else s.sendMessage("§eLimpeza embaixo do spawn: §f" + (vazio.feito() ? "feita" : vazio.situacao) + " §7(raio " + vazio.raio() + ") §eParede: §f" + (vazio.selado.exists() ? "fechada" : vazio.situacao));
                return true;
            }
            s.sendMessage("§e/extras recarregar §7| §e/extras aqui §7| §e/extras daritens <jogador> §7| §e/extras placas §7| §e/extras vazio [fazer|selar]");
            return true;
        }
        return false;
    }
}
