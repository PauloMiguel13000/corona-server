package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Spawn principal v2 (05/11): ilha redonda maior (raio 27) nas cores do dono (vermelho, preto e branco).
 * Piso de quartzo com rejunte preto, cruz de "tapete vermelho" ate os portais, coroa vermelha em volta do farol
 * (o feixe sai vermelho). Norte: portal do Spawn das Masmorras. Sul: portal do Lobby dos Minigames.
 * Leste: Galeria VIP (4 totens; clicar compra por Pix no jogo, plugin CoronaLoja: /loja comprar). Oeste: totens de
 * todos os minigames (clicar entra no jogo) e sempre 2 vagas "Em breve" para os proximos. Diagonais: Classes, Kits,
 * Explorar e Pets (pisar no tapete ou clicar). Leve: tudo solido, luz invisivel, poucos enfeites.
 * Trocar a VERSAO reconstroi (tira o antigo primeiro; marca spawn-principal-vN.txt).
 */
final class SpawnPrincipal implements Listener {

    static final int CX = 6, CZ = -6, PISO = 122, R = 27, VERSAO = 2, PORTAL = 23, VAGAS_MINIGAMES = 9;

    record Pedestal(String nome, String sub, double angulo, Material icone, String tapete, String comando) {}

    record Vip(String produto, String nome, String cor, Material icone) {}

    static final Pedestal[] PEDESTAIS = {
            new Pedestal("§b§lCLASSES", "§7escolha a sua classe", -135, Material.NETHER_STAR, "minecraft:red_glazed_terracotta", "classe"),
            new Pedestal("§2§lKITS", "§7pegue seus kits", -45, Material.CHEST, "minecraft:red_glazed_terracotta", "kit"),
            new Pedestal("§a§lEXPLORAR", "§7lugar aleatório do mundo", 45, Material.COMPASS, "minecraft:red_glazed_terracotta", "rtp"),
            new Pedestal("§d§lPETS", "§7chame e guarde seus pets", 135, Material.BONE, "minecraft:red_glazed_terracotta", "pets")};

    /** Galeria VIP (leste). O texto (preco e beneficios) quem escreve e o CoronaLoja, pela chave coronaloja:totem. */
    static final Vip[] VIPS = {
            new Vip("vip", "§a§lVIP", "§a", Material.EMERALD),
            new Vip("vipmais", "§b§lVIP+", "§b", Material.DIAMOND),
            new Vip("mvp", "§6§lMVP", "§6", Material.GOLDEN_HELMET),
            new Vip("lenda", "§d§lLENDA", "§d", Material.DRAGON_HEAD)};

    private final Extras pl;
    private final Map<UUID, Long> espera = new HashMap<>();

    SpawnPrincipal(Extras pl) {
        this.pl = pl;
    }

    World mundo() {
        return Bukkit.getWorld(pl.getConfig().getString("spawn.mundo", "world"));
    }

    private org.bukkit.NamespacedKey chave() {
        return new org.bukkit.NamespacedKey(pl, "spawn_novo");
    }

    /** Versao do spawn gravada em cada enfeite: enfeite de versao antiga que aparecer depois e apagado. */
    private org.bukkit.NamespacedKey chaveVersao() {
        return new org.bukkit.NamespacedKey(pl, "spawn_v");
    }

    private boolean antigo(Entity e) {
        if (!e.getPersistentDataContainer().has(chave(), PersistentDataType.STRING)) return false;
        Integer v = e.getPersistentDataContainer().get(chaveVersao(), PersistentDataType.INTEGER);
        return (v == null || v != VERSAO) && perto(e.getLocation());
    }

    @EventHandler
    public void enfeitesCarregados(org.bukkit.event.world.EntitiesLoadEvent e) {
        if (e.getWorld() != mundo() || !new File(pl.getDataFolder(), "spawn-principal-v" + VERSAO + ".txt").exists()) return;
        boolean parteFeita = new File(pl.getDataFolder(), "spawn-principal-sul-oeste-v" + PARTE + ".txt").exists();
        int n = 0;
        for (Entity en : e.getEntities()) if (antigo(en) || (parteFeita && antigoDaParte(en))) {
            en.remove();
            n++;
        }
        if (n > 0) pl.getLogger().info("Spawn: " + n + " enfeite(s) do spawn antigo apagado(s).");
    }

    static boolean perto(Location l) {
        double dx = l.getX() - (CX + 0.5), dz = l.getZ() - (CZ + 0.5);
        return dx * dx + dz * dz < (R + 3) * (R + 3) && l.getY() > PISO - 10 && l.getY() < PISO + 20;
    }

    static int px(double angulo, double r) {
        return CX + (int) Math.round(Math.cos(Math.toRadians(angulo)) * r);
    }

    static int pz(double angulo, double r) {
        return CZ + (int) Math.round(Math.sin(Math.toRadians(angulo)) * r);
    }

    /** Tapete do pedestal: um bloco mais perto do centro. */
    static int[] tapete(Pedestal p) {
        return new int[] {px(p.angulo(), 8.6), pz(p.angulo(), 8.6)};
    }

    static final String TEXTO_PORTAL_MASMORRAS = "§4§lMUNDO DAS MASMORRAS\n§7Lobby • níveis 1 a 4 • NPCs e rankings";
    static final String DICA_MASMORRAS = "§fMasmorras: §eportal ao norte §7(nível 1 perto do lobby; mais longe = mais forte)";

    static final String TEXTO_CENTRO = "§c§lCORONA SERVER\n§4Terror §fe Minigames §7ao sul  §8|  §fMasmorras §7ao norte\n§fVIP §7a leste  §8|  §fJogos §7a oeste";

    /**
     * Parte nova do spawn (09/11): no sul, dois portais lado a lado (Lobby do Terror a oeste, Lobby dos Minigames a
     * leste) no lugar do portal unico; no oeste, um totem por minigame (11 lugares em dois aneis, textos em duas
     * alturas para nao encavalar). Tudo o que esta parte poe leva a marca spawn_parte = PARTE; enfeite antigo
     * nessas areas sem a marca e apagado quando carrega.
     */
    static final int PARTE = 2, SUL_TERROR = -6, SUL_MINIGAMES = 6, LUGARES_OESTE = 11;

    private org.bukkit.NamespacedKey chaveParte() {
        return new org.bukkit.NamespacedKey(pl, "spawn_parte");
    }

    private void marcar(Entity e) {
        e.getPersistentDataContainer().set(chaveVersao(), PersistentDataType.INTEGER, VERSAO);
        e.getPersistentDataContainer().set(chaveParte(), PersistentDataType.INTEGER, PARTE);
    }

    /** Angulo (0-360, 0 = leste, 90 = sul) do ponto visto do centro. */
    private static double anguloDe(double x, double z) {
        return Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z - (CZ + 0.5), x - (CX + 0.5)))), 360);
    }

    /** Areas refeitas: anel dos totens no oeste e a faixa dos portais no sul. */
    static boolean areaNova(Location l) {
        double dx = l.getX() - (CX + 0.5), dz = l.getZ() - (CZ + 0.5);
        if (l.getY() < PISO - 1 || l.getY() > PISO + 13) return false;
        double d = Math.sqrt(dx * dx + dz * dz), a = anguloDe(l.getX(), l.getZ());
        boolean oeste = d >= 15.5 && d <= 25.5 && a >= 100 && a <= 260;
        boolean sul = Math.abs(dx) <= 13 && dz >= 15 && dz <= 26;
        return oeste || sul;
    }

    private boolean antigoDaParte(Entity e) {
        if (!e.getPersistentDataContainer().has(chave(), PersistentDataType.STRING)) return false;
        Integer v = e.getPersistentDataContainer().get(chaveParte(), PersistentDataType.INTEGER);
        return (v == null || v != PARTE) && areaNova(e.getLocation());
    }

    /** Dois portais no sul: Lobby do Terror (oeste) e Lobby dos Minigames (leste), com uma placa no meio. */
    private void portaisSul(World w) {
        int z = CZ + PORTAL;
        portalSul(w, CX + SUL_TERROR, z, new String[] {"minecraft:crying_obsidian", "minecraft:polished_blackstone_bricks", "minecraft:blackstone", "minecraft:obsidian"},
                "§4§lLOBBY DO TERROR\n§7Hotel • Porcelana • Vovó • Backrooms\n§7Não Chore • Turno • Fábrica", true);
        portalSul(w, CX + SUL_MINIGAMES, z, new String[] {"minecraft:quartz_bricks", "minecraft:polished_blackstone_bricks", "minecraft:red_nether_bricks", "minecraft:red_concrete"},
                "§c§lLOBBY DOS MINIGAMES\n§7Defesa da Torre • Arena PvP", false);
        // placa no meio, no fim do tapete vermelho
        por(w, CX, PISO + 1, z, "minecraft:polished_blackstone_bricks");
        por(w, CX, PISO + 2, z, "minecraft:quartz_pillar");
        por(w, CX, PISO + 3, z, "minecraft:lantern[hanging=false]");
        texto(w, new Location(w, CX + 0.5, PISO + 4.6, z - 0.4), "§c◀ §fMinigames   §8|   §4Terror §c▶", 0.9f);
    }

    private void portalSul(World w, int cx, int z, String[] m, String nome, boolean terror) {
        for (int s = -2; s <= 2; s++)
            for (int y = 0; y <= 6; y++) {
                int x = cx + s;
                boolean borda = Math.abs(s) == 2 || y == 0 || y == 6;
                String b = !borda ? "minecraft:nether_portal[axis=x]" : y == 6 && s == 0 ? m[3]
                        : y == 0 ? m[2] : (y == 6 || (Math.abs(s) == 2 && y % 2 == 0)) ? m[0] : m[1];
                por(w, x, PISO + y, z, b);
            }
        if (terror) {
            por(w, cx, PISO + 7, z, "minecraft:wither_skeleton_skull[rotation=8]");
            por(w, cx - 2, PISO + 7, z, "minecraft:soul_lantern");
            por(w, cx + 2, PISO + 7, z, "minecraft:soul_lantern");
            por(w, cx - 3, PISO + 1, z - 1, "minecraft:jack_o_lantern[facing=north]");
            por(w, cx + 3, PISO + 1, z - 1, "minecraft:jack_o_lantern[facing=north]");
        } else {
            por(w, cx - 2, PISO + 7, z, "minecraft:lantern[hanging=false]");
            por(w, cx + 2, PISO + 7, z, "minecraft:lantern[hanging=false]");
        }
        texto(w, new Location(w, cx + 0.5, PISO + 8.4, z - 0.6), nome, 1.0f);
    }

    /** Totens no oeste: um por minigame (os normais primeiro, perto do sul) e as vagas "Em breve" no fim. */
    private void totensOeste(World w) {
        List<LobbyMinigames.Portal> jogos = new ArrayList<>();
        for (LobbyMinigames.Portal p : LobbyMinigames.PORTAIS) if (!p.terror()) jogos.add(p);
        for (LobbyMinigames.Portal p : LobbyMinigames.PORTAIS) if (p.terror()) jogos.add(p);
        int n = Math.max(LUGARES_OESTE, jogos.size() + 1);
        double passo = 108.0 / (n - 1);
        for (int i = 0; i < n; i++) {
            double ang = 120 + i * passo;
            double r = i % 2 == 0 ? 18 : 21.5, alt = i % 2 == 0 ? 4.6 : 6.2;
            if (i < jogos.size()) totemMinigame(w, jogos.get(i), ang, r, alt);
            else vaga(w, ang, r);
        }
        texto(w, new Location(w, px(180, 24.8) + 0.5, PISO + 8.0, pz(180, 24.8) + 0.5), "§f§lMINIGAMES\n§7clique num totem para jogar", 1.1f);
    }

    /** Spawn ja feito: troca so o sul e o oeste (uma vez; marca spawn-principal-sul-oeste-vN.txt). */
    private void atualizarSulOeste(World w) {
        File feito = new File(pl.getDataFolder(), "spawn-principal-sul-oeste-v" + PARTE + ".txt");
        if (feito.exists()) return;
        for (int cx = (CX - R) >> 4; cx <= (CX + R) >> 4; cx++) for (int cz = (CZ - R) >> 4; cz <= (CZ + R) >> 4; cz++) w.getChunkAt(cx, cz).load(true);
        // espera os enfeites (entidades) dos chunks carregarem antes de tirar os antigos
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            try {
                int ents = 0;
                for (Entity en : w.getEntities()) if (antigoDaParte(en)) {
                    en.remove();
                    ents++;
                }
                int blocos = 0;
                for (int x = CX - R; x <= CX + R; x++)
                    for (int z = CZ - R; z <= CZ + R; z++) {
                        Location l = new Location(w, x + 0.5, PISO + 1, z + 0.5);
                        if (!areaNova(l)) continue;
                        String b = piso(x, z);
                        if (b == null) continue;
                        double d = dist(x, z);
                        if (d > R - 1.5) continue;   // mureta e pilares da borda ficam
                        if (!w.getBlockAt(x, PISO, z).getBlockData().getAsString().equals(Bukkit.createBlockData(b).getAsString())) {
                            por(w, x, PISO, z, b);
                            blocos++;
                        }
                        for (int y = 1; y <= 8; y++) {
                            Block bl = w.getBlockAt(x, PISO + y, z);
                            if (bl.getType().isAir() || bl.getType() == Material.LIGHT) continue;
                            bl.setType(Material.AIR, false);
                            blocos++;
                        }
                    }
                portaisSul(w);
                totensOeste(w);
                int centro = 0;
                for (Entity en : w.getEntities()) {
                    if (!(en instanceof TextDisplay t) || !perto(en.getLocation()) || !t.getPersistentDataContainer().has(chave(), PersistentDataType.STRING)) continue;
                    String plano = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(t.text());
                    if (plano.startsWith("CORONA SERVER")) {
                        t.text(Extras.txt(TEXTO_CENTRO));
                        centro++;
                    }
                }
                Files.writeString(feito.toPath(), "feito em " + new java.util.Date() + ": " + ents + " enfeites e " + blocos + " blocos trocados, letreiro do centro " + centro + "\n");
                pl.getLogger().info("Spawn: sul (Terror + Minigames) e oeste (" + LobbyMinigames.PORTAIS.length + " totens) refeitos; " + ents + " enfeites antigos tirados, "
                        + blocos + " blocos, letreiro do centro " + (centro > 0 ? "ok" : "nao achado"));
            } catch (Throwable t) {
                pl.getLogger().warning("Spawn (sul e oeste) nao terminou: " + t);
            }
        }, 100L);
    }

    /** 08/11: o portal norte leva ao Mundo das Masmorras novo; troca os letreiros que falavam do Spawn das Masmorras. */
    private void atualizarTextosMasmorras(World w) {
        File feito = new File(pl.getDataFolder(), "spawn-principal-textos-masmorras.txt");
        if (feito.exists()) return;
        int n = 0;
        for (Entity e : w.getEntities()) {
            if (!(e instanceof TextDisplay t) || !perto(e.getLocation()) || !t.getPersistentDataContainer().has(chave(), PersistentDataType.STRING)) continue;
            String plano = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(t.text());
            if (plano.contains("SPAWN DAS MASMORRAS")) {
                t.text(Extras.txt(TEXTO_PORTAL_MASMORRAS));
                n++;
            } else if (plano.contains("procure pelo mundo")) {
                String legado = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(t.text());
                t.text(Extras.txt(legado.replaceAll("§fMasmorras: §eprocure pelo mundo! §7\\(mais longe = mais forte\\)", java.util.regex.Matcher.quoteReplacement(DICA_MASMORRAS))));
                n++;
            }
        }
        if (n == 0) return;   // os enfeites ainda nao carregaram: tenta de novo na proxima vez que ligar
        try {
            Files.writeString(feito.toPath(), n + " letreiro(s) trocado(s) em " + new java.util.Date() + "\n");
        } catch (IOException ignored) {
        }
        pl.getLogger().info("Spawn: " + n + " letreiro(s) atualizado(s) para o Mundo das Masmorras.");
    }

    void iniciar() {
        World w = mundo();
        if (w == null || !pl.getConfig().getBoolean("spawn-novo.ativo", true)) return;
        File feito = new File(pl.getDataFolder(), "spawn-principal-v" + VERSAO + ".txt");
        if (feito.exists()) {
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                try {
                    atualizarTextosMasmorras(w);
                } catch (Throwable t) {
                    pl.getLogger().warning("Letreiros do spawn: " + t);
                }
            }, 220L);
            Bukkit.getScheduler().runTaskLater(pl, () -> atualizarSulOeste(w), 160L);
            Bukkit.getScheduler().runTaskLater(pl, () -> Ceu.fazer(pl, "spawn-principal", w, CX, CZ, PISO, R, 46, Ceu.VERMELHO, false), 260L);
            // limpa enfeite antigo que tenha sobrado (ja carregado)
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                int n = 0;
                for (Entity en : w.getEntities()) if (antigo(en)) {
                    en.remove();
                    n++;
                }
                if (n > 0) pl.getLogger().info("Spawn: " + n + " enfeite(s) do spawn antigo apagado(s).");
            }, 200L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> limparAntigo(w, () -> {
            try {
                construir(w);
                Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
                Ceu.fazer(pl, "spawn-principal", w, CX, CZ, PISO, R, 46, Ceu.VERMELHO, true);
            } catch (Throwable t) {
                pl.getLogger().warning("Spawn novo nao terminou: " + t);
            }
        }), 80L);
    }

    /** Refaz o spawn agora (admin: /extras refazerspawn), por exemplo depois de quebrarem um bloco sem querer. */
    void refazer(org.bukkit.command.CommandSender quem) {
        World w = mundo();
        if (w == null) {
            quem.sendMessage("§cMundo do spawn nao encontrado.");
            return;
        }
        quem.sendMessage("§eRefazendo o spawn principal (uns segundos)...");
        limparAntigo(w, () -> {
            try {
                construir(w);
                Ceu.fazer(pl, "spawn-principal", w, CX, CZ, PISO, R, 46, Ceu.VERMELHO, true);
                quem.sendMessage("§aSpawn principal refeito.");
                pl.getLogger().info("Spawn principal refeito por " + quem.getName() + ".");
            } catch (Throwable t) {
                quem.sendMessage("§cNao deu para refazer: " + t);
                pl.getLogger().warning("Refazer o spawn: " + t);
            }
        });
    }

    // ------------------------------------------------------------------ tirar o antigo

    /** Tira tudo da plataforma antiga e da Praca dos Portais (blocos e enfeites), aos poucos. */
    private void limparAntigo(World w, Runnable depois) {
        int x1 = -32, x2 = 54, z1 = -70, z2 = 38, y1 = 96, y2 = 175;
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) w.getChunkAt(cx, cz).load(true);
        int[] x = {x1};
        long[] tirados = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            long t0 = System.nanoTime();
            while (x[0] <= x2 && System.nanoTime() - t0 < 15_000_000L) {
                for (int z = z1; z <= z2; z++)
                    for (int y = y1; y <= y2; y++) {
                        Block b = w.getBlockAt(x[0], y, z);
                        if (b.getType().isAir()) continue;
                        b.setType(Material.AIR, false);
                        tirados[0]++;
                    }
                x[0]++;
            }
            if (x[0] <= x2) return;
            tarefa.cancel();
            int ents = 0;
            for (Entity e : w.getEntities()) {
                Location l = e.getLocation();
                if (l.getX() < x1 || l.getX() > x2 + 1 || l.getZ() < z1 || l.getZ() > z2 + 1 || l.getY() < y1 || l.getY() > y2) continue;
                boolean enfeite = e instanceof Display || e instanceof Interaction || e instanceof org.bukkit.entity.ArmorStand
                        || e instanceof org.bukkit.entity.Hanging || e instanceof org.bukkit.entity.Item || e instanceof org.bukkit.entity.ExperienceOrb
                        || (e instanceof org.bukkit.entity.Villager && !e.getPersistentDataContainer().getKeys().isEmpty());
                if (!enfeite) continue;
                e.remove();
                ents++;
            }
            pl.getLogger().info("Spawn antigo tirado: " + tirados[0] + " blocos e " + ents + " enfeites.");
            depois.run();
        }, 1L, 1L);
    }

    // ------------------------------------------------------------------ construir o novo

    private static void por(World w, int x, int y, int z, String b) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false);
    }

    private static double dist(int x, int z) {
        return Math.sqrt((x - CX) * (x - CX) + (z - CZ) * (z - CZ));
    }

    /** Bloco do piso original naquele ponto (null = fora da ilha). */
    static String piso(int x, int z) {
        double d = dist(x, z);
        if (d > R + 0.5) return null;
        boolean cruz = Math.abs(x - CX) <= 1 || Math.abs(z - CZ) <= 1;
        if (d < 0.5) return "minecraft:red_stained_glass";
        if (d < 2.5) return "minecraft:quartz_bricks";
        if (d < 4.5) return "minecraft:polished_blackstone_bricks";
        if (d > R - 0.5) return "minecraft:polished_blackstone_bricks";
        if (d > R - 1.5) return "minecraft:red_nether_bricks";
        if (d >= 12.5 && d < 13.5) return "minecraft:red_nether_bricks";
        if (cruz) return Math.abs(x - CX) + Math.abs(z - CZ) - 1 == (int) d && (int) d % 5 == 0 ? "minecraft:chiseled_quartz_block" : "minecraft:red_concrete";
        if (Math.floorMod(x - CX, 4) == 0 || Math.floorMod(z - CZ, 4) == 0) return "minecraft:polished_blackstone";
        return "minecraft:smooth_quartz";
    }

    private void construir(World w) {
        // piso: quartzo com rejunte preto a cada 4, anel e borda vermelhos, cruz de tapete vermelho
        for (int x = CX - R; x <= CX + R; x++)
            for (int z = CZ - R; z <= CZ + R; z++) {
                String b = piso(x, z);
                if (b == null) continue;
                por(w, x, PISO, z, b);
                por(w, x, PISO - 1, z, "minecraft:obsidian");
            }
        // farol no meio: o vidro vermelho deixa o feixe vermelho
        for (int x = CX - 1; x <= CX + 1; x++) for (int z = CZ - 1; z <= CZ + 1; z++) por(w, x, PISO - 2, z, "minecraft:iron_block");
        por(w, CX, PISO - 1, CZ, "minecraft:beacon");
        // fundo da ilha afinando (solido)
        for (int k = 2; k <= 8; k++)
            for (int x = CX - R; x <= CX + R; x++)
                for (int z = CZ - R; z <= CZ + R; z++) {
                    if (dist(x, z) > R - k * 3.0) continue;
                    if (Math.abs(x - CX) <= 1 && Math.abs(z - CZ) <= 1 && k == 2) continue;
                    por(w, x, PISO - k, z, k % 3 == 0 ? "minecraft:red_nether_bricks" : k >= 6 ? "minecraft:blackstone" : "minecraft:polished_blackstone");
                }
        // coroa em volta do farol: 8 pontas vermelhas (altas e baixas) com uma "joia" branca em cima
        for (int k = 0; k < 8; k++) {
            double ang = k * 45;
            int x = px(ang, 3.6), z = pz(ang, 3.6);
            int alt = k % 2 == 0 ? 4 : 2;
            por(w, x, PISO + 1, z, "minecraft:polished_blackstone_bricks");
            for (int y = 2; y <= alt; y++) por(w, x, PISO + y, z, "minecraft:red_concrete");
            por(w, x, PISO + alt + 1, z, "minecraft:quartz_block");
        }
        // mureta preta na borda e 12 pilares brancos com topo vermelho e lanterna
        for (int x = CX - R; x <= CX + R; x++)
            for (int z = CZ - R; z <= CZ + R; z++) {
                double d = dist(x, z);
                if (d > R - 0.5 && d <= R + 0.5) w.getBlockAt(x, PISO + 1, z).setBlockData(Bukkit.createBlockData("minecraft:polished_blackstone_wall"), true);
            }
        for (int k = 0; k < 12; k++) {
            double ang = 15 + k * 30;
            int x = px(ang, R), z = pz(ang, R);
            if (dist(x, z) > R + 0.5) continue;
            por(w, x, PISO + 1, z, "minecraft:quartz_pillar");
            por(w, x, PISO + 2, z, "minecraft:quartz_pillar");
            por(w, x, PISO + 3, z, "minecraft:red_concrete");
            por(w, x, PISO + 4, z, "minecraft:lantern[hanging=false]");
        }
        // portais: sul = Lobby do Terror e Lobby dos Minigames (lado a lado), norte = Mundo das Masmorras
        portaisSul(w);
        portal(w, CZ - PORTAL, TEXTO_PORTAL_MASMORRAS);
        // Galeria VIP (leste)
        double[] angVip = {-36, -12, 12, 36};
        for (int i = 0; i < VIPS.length; i++) totemVip(w, VIPS[i], angVip[i]);
        texto(w, new Location(w, px(0, 23.2) + 0.5, PISO + 5.2, pz(0, 23.2) + 0.5), "§c§lGALERIA VIP\n§fcompre aqui com Pix, sem sair do jogo", 1.1f);
        // minigames (oeste): um totem por jogo + vagas livres
        totensOeste(w);
        // pedestais nas diagonais
        for (Pedestal p : PEDESTAIS) pedestal(w, p);
        // quadros de texto
        texto(w, new Location(w, px(-64, 15) + 0.5, PISO + 3.4, pz(-64, 15) + 0.5), "§c§lCOMANDOS\n"
                + "§f/spawn §7volta aqui  §f/rtp §7lugar aleatório\n"
                + "§f/sethome §7e §f/home §7suas casas  §f/ec §7baú do Ender\n"
                + "§f/kit §7kits  §f/classe §7classes  §f/poderes §7habilidades\n"
                + "§f/pets §7e §f/petpoder §7pets  §f/skills §7seu nível\n"
                + "§f/masmorras §7e §f/coronas §7masmorras e dinheiro\n"
                + "§f/grupo §7e §f/clan §7jogar junto  §f/vip §7VIPs  §f/loja §7site\n"
                + "§f/plot auto §7seu terreno  §f/votar §7e §f/ip", 0.75f);
        texto(w, new Location(w, px(-116, 15) + 0.5, PISO + 3.4, pz(-116, 15) + 0.5), "§c§lDICAS\n"
                + "§fUsar poder: §eagache e soque o ar §7(mão vazia)\n"
                + "§fTrocar poder: §eagache + F\n"
                + "§fProteger a casa: §epá de ouro §7(2 cantos)\n"
                + DICA_MASMORRAS + "\n"
                + "§fSem PvP no spawn e nas masmorras\n"
                + "§fPule da ilha para descer ao chão §7(sem dano)\n"
                + "§7Java e Bedrock jogam juntos", 0.75f);
        texto(w, new Location(w, CX + 0.5, PISO + 7.4, CZ + 0.5), TEXTO_CENTRO, 1.5f);
        // luz invisivel
        Light luz = (Light) Bukkit.createBlockData(Material.LIGHT);
        luz.setLevel(15);
        for (int x = CX - R + 2; x <= CX + R - 2; x += 5)
            for (int z = CZ - R + 2; z <= CZ + R - 2; z += 5) {
                if (dist(x, z) > R - 2) continue;
                Block b = w.getBlockAt(x, PISO + 3, z);
                if (b.getType().isAir()) b.setBlockData(luz, false);
            }
        // marca a versao em tudo o que acabou de ser colocado; o que sobrar sem marca e do antigo
        int velhos = 0;
        for (Entity en : w.getEntities()) {
            if (!en.getPersistentDataContainer().has(chave(), PersistentDataType.STRING) || !perto(en.getLocation())) continue;
            if (en.getTicksLived() > 200) {
                en.remove();
                velhos++;
            } else en.getPersistentDataContainer().set(chaveVersao(), PersistentDataType.INTEGER, VERSAO);
        }
        if (velhos > 0) pl.getLogger().info("Spawn: " + velhos + " enfeite(s) antigo(s) apagado(s).");
        try {
            Files.writeString(new File(pl.getDataFolder(), "spawn-principal-sul-oeste-v" + PARTE + ".txt").toPath(), "junto com o spawn em " + new java.util.Date() + "\n");
        } catch (IOException ignored) {
        }
        pl.getLogger().info("Spawn principal v2 construido (raio " + R + ", galeria VIP, " + LobbyMinigames.PORTAIS.length + " minigames + vagas, 4 pedestais, terror e minigames ao sul).");
    }

    private void portal(World w, int z, String nome) {
        for (int s = -2; s <= 2; s++)
            for (int y = 0; y <= 6; y++) {
                int x = CX + s;
                boolean borda = Math.abs(s) == 2 || y == 0 || y == 6;
                String b = !borda ? "minecraft:nether_portal[axis=x]" : y == 6 && s == 0 ? "minecraft:red_concrete"
                        : y == 0 ? "minecraft:red_nether_bricks" : (y == 6 || (Math.abs(s) == 2 && y % 2 == 0)) ? "minecraft:quartz_bricks" : "minecraft:polished_blackstone_bricks";
                por(w, x, PISO + y, z, b);
            }
        double tz = z > CZ ? z - 0.6 : z + 1.6;
        texto(w, new Location(w, CX + 0.5, PISO + 7.6, tz), nome, 1.0f);
    }

    /** Base de totem: plataforma 3x3, coluna e o icone flutuando. */
    private void base(World w, int x, int z, String topo, Material icone, String comando) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) por(w, x + dx, PISO, z + dz, dx == 0 && dz == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:red_nether_bricks");
        por(w, x, PISO + 1, z, "minecraft:polished_blackstone_bricks");
        por(w, x, PISO + 2, z, topo);
        if (icone != null) w.spawn(new Location(w, x + 0.5, PISO + 3.6, z + 0.5), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(icone));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(true);
            d.getPersistentDataContainer().set(chave(), PersistentDataType.STRING, comando == null ? "texto" : comando);
            marcar(d);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.3f), new AxisAngle4f()));
        });
        if (comando != null) w.spawn(new Location(w, x + 0.5, PISO + 1, z + 0.5), Interaction.class, i -> {
            i.setInteractionWidth(1.8f);
            i.setInteractionHeight(3.6f);
            i.setResponsive(true);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(chave(), PersistentDataType.STRING, comando);
            marcar(i);
        });
    }

    private void totemVip(World w, Vip v, double ang) {
        int x = px(ang, 19), z = pz(ang, 19);
        base(w, x, z, "minecraft:chiseled_quartz_block", v.icone(), "loja comprar " + v.produto());
        // texto provisorio; o CoronaLoja troca pelo preco e beneficios de verdade
        TextDisplay t = texto(w, new Location(w, x + 0.5, PISO + 5.4, z + 0.5), v.nome() + "\n§7clique para comprar com Pix", 0.8f);
        org.bukkit.NamespacedKey k = org.bukkit.NamespacedKey.fromString("coronaloja:totem");
        if (k != null) t.getPersistentDataContainer().set(k, PersistentDataType.STRING, v.produto());
    }

    private void totemMinigame(World w, LobbyMinigames.Portal p, double ang, double r, double alturaTexto) {
        int x = px(ang, r), z = pz(ang, r);
        base(w, x, z, p.terror() ? "minecraft:crying_obsidian" : "minecraft:red_concrete", p.icone(), p.comando());
        texto(w, new Location(w, x + 0.5, PISO + alturaTexto, z + 0.5), p.nome() + "\n" + p.sub() + (p.terror() ? "\n§4☠ terror" : "") + "\n§f▶ clique para jogar", 0.7f);
    }

    private void vaga(World w, double ang, double r) {
        int x = px(ang, r), z = pz(ang, r);
        base(w, x, z, "minecraft:polished_blackstone", null, null);
        texto(w, new Location(w, x + 0.5, PISO + 3.4, z + 0.5), "§8§lEM BREVE\n§7novo minigame", 0.7f);
    }

    private void pedestal(World w, Pedestal p) {
        int x = px(p.angulo(), 10), z = pz(p.angulo(), 10);
        int[] t = tapete(p);
        por(w, t[0], PISO, t[1], p.tapete());
        base(w, x, z, "minecraft:quartz_block", p.icone(), p.comando());
        texto(w, new Location(w, x + 0.5, PISO + 4.8, z + 0.5), p.nome() + "\n" + p.sub() + "\n§8clique ou pise no tapete", 0.8f);
    }

    private TextDisplay texto(World w, Location l, String t, float escala) {
        return w.spawn(l, TextDisplay.class, x -> {
            x.text(Extras.txt(t));
            x.setBillboard(Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(Color.fromARGB(130, 0, 0, 0));
            x.setPersistent(true);
            x.setViewRange(0.6f);
            x.getPersistentDataContainer().set(chave(), PersistentDataType.STRING, "texto");
            marcar(x);
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(escala), new AxisAngle4f()));
        });
    }

    // ------------------------------------------------------------------ usar

    private boolean esperando(Player p) {
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return true;
        espera.put(p.getUniqueId(), agora + 3000L);
        return false;
    }

    private void usar(Player p, String comando) {
        if (esperando(p)) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        if (!p.performCommand(comando)) p.sendMessage(Extras.txt("§cIndisponível agora."));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clicou(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction i)) return;
        String c = i.getPersistentDataContainer().get(chave(), PersistentDataType.STRING);
        if (c == null) return;
        e.setCancelled(true);
        usar(e.getPlayer(), c);
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        if (!perto(to) || to.getWorld() != mundo()) return;
        Player p = e.getPlayer();
        int bx = to.getBlockX(), by = to.getBlockY() - PISO, bz = to.getBlockZ();
        // tapetes dos pedestais
        if (by == 1)
            for (Pedestal pd : PEDESTAIS) {
                int[] t = tapete(pd);
                if (bx == t[0] && bz == t[1]) {
                    usar(p, pd.comando());
                    return;
                }
            }
        // portais
        if (by < 1 || by > 5) return;
        boolean norte = Math.abs(bz - (CZ - PORTAL)) <= 1 && Math.abs(bx - CX) <= 1;
        boolean sul = Math.abs(bz - (CZ + PORTAL)) <= 1;
        boolean terror = sul && Math.abs(bx - (CX + SUL_TERROR)) <= 1, minigames = sul && Math.abs(bx - (CX + SUL_MINIGAMES)) <= 1;
        if (!norte && !terror && !minigames) return;
        if (esperando(p)) return;
        int vx = terror ? CX + SUL_TERROR : minigames ? CX + SUL_MINIGAMES : CX;
        p.teleport(new Location(to.getWorld(), vx + 0.5, PISO + 1, (norte ? CZ - PORTAL + 6 : CZ + PORTAL - 6) + 0.5, to.getYaw(), to.getPitch()));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        if (terror) {
            if (pl.terror != null) pl.terror.teleportar(p);
        } else if (minigames) {
            if (pl.lobby != null) pl.lobby.teleportar(p);
        } else p.performCommand("masmorras spawn");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether(PlayerPortalEvent e) {
        if (e.getFrom().getWorld() == mundo() && perto(e.getFrom())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void semNether2(EntityPortalEvent e) {
        if (e.getFrom().getWorld() == mundo() && perto(e.getFrom())) e.setCancelled(true);
    }
}
