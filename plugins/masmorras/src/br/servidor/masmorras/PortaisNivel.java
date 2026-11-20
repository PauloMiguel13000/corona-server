package br.servidor.masmorras;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Portais dos niveis no lobby do Mundo das Masmorras (10/11, pedido do Paulo): quatro portais no lado oeste que levam
 * direto para a area de cada nivel do mapa (2, 3, 4 e 5). O nivel 1 fica em volta do lobby, entao nao precisa de portal.
 * Cada portal so deixa passar quem esta com equipamento a altura — a conta esta em {@link #nivelDosItens(Player)}:
 * media do que a pessoa veste (4 pecas) mais a arma da mao, de couro/madeira (1) ate netherite (5).
 */
final class PortaisNivel implements Listener {

    /**
     * 18/11 (lobby v5): os portais ficam nos portoes do lobby novo (nivel 2 a leste, 3 ao sul, 4 a oeste) e o 5 ao lado
     * do 4. {x, z, eixoX (1 = o vao se estende no x), nivel}. Quem constroi agora e o LobbyMundo.construirV5.
     */
    // 20/11: + nivel 6 na faixa oeste, no lugar da esmeralda antiga (a volta ao spawn continua na esmeralda do sul)
    static final int[][] PORTAIS = {{38, 0, 0, 2}, {0, 38, 1, 3}, {-38, 0, 0, 4}, {-37, -13, 0, 5}, {-37, 13, 0, 6}};
    private static final int[] NIVEIS = {2, 3, 4, 5, 6};

    private final Masmorras pl;
    private final MundoMasmorras mm;
    private final NamespacedKey kEnfeite;
    private final Map<UUID, Long> espera = new HashMap<>();

    PortaisNivel(Masmorras pl, MundoMasmorras mm) {
        this.pl = pl;
        this.mm = mm;
        this.kEnfeite = new NamespacedKey(pl, "portal_nivel_enfeite");
    }

    boolean ativo() {
        return pl.getConfig().getBoolean("portais-nivel.ativo", true);
    }

    void iniciar() {
        if (mm.normal == null || !ativo()) return;
        File feito = new File(pl.getDataFolder(), "portais-nivel-v2.txt");
        if (feito.exists() || new File(pl.getDataFolder(), "lobby-mundo-v5.txt").exists() || pl.getResource("lobby5.txt.gz") != null) return;
        try {
            construir();
            Files.writeString(feito.toPath(), "construido em " + new java.util.Date() + "\n");
        } catch (Throwable ex) {
            pl.getLogger().warning("Portais dos niveis nao terminaram: " + ex);
        }
    }

    // ------------------------------------------------------------------ construcao

    private void por(World w, int x, int y, int z, String b) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(b), false);
    }

    /** Moldura e cor de cada nivel (quanto mais alto, mais "perigoso"). */
    static String[] visual(int nivel) {
        return switch (nivel) {
            case 2 -> new String[] {"minecraft:mossy_stone_bricks", "minecraft:oxidized_copper", "minecraft:moss_block", "§e"};
            case 3 -> new String[] {"minecraft:deepslate_bricks", "minecraft:gold_block", "minecraft:polished_deepslate", "§6"};
            case 4 -> new String[] {"minecraft:blackstone", "minecraft:diamond_block", "minecraft:polished_blackstone", "§c"};
            case 6 -> new String[] {"minecraft:red_nether_bricks", "minecraft:redstone_block", "minecraft:crimson_nylium", "§4"};
            default -> new String[] {"minecraft:obsidian", "minecraft:netherite_block", "minecraft:crying_obsidian", "§5"};
        };
    }

    private void construir() {
        World w = mm.normal;
        int y = GeradorMundo.LOBBY_Y;
        for (int[] q : PORTAIS) {
            if (q[2] == 1) continue;   // so o caminho antigo (sem o lobby v5): portais que atravessam no x
            int nivel = q[3], z = q[1];
            w.getChunkAt(q[0] >> 4, z >> 4).load(true);
            String[] v = visual(nivel);
            portal(w, q[0], y, z, v[0], v[1], v[2]);
            texto(w, new Location(w, q[0] + 0.5, y + 6.8, z + 0.5),
                    v[3] + "§lÁREA DE NÍVEL " + nivel + "\n§7Masmorras de nível " + nivel + " do mapa\n§8equipamento nível " + nivel + " ou melhor", 1.1f);
        }
        pl.getLogger().info("Portais dos niveis 2 a 5 construidos no lobby (oeste).");
    }

    /** Portal de 3 de vao x 4 de altura, eixo z (atravessa no x), com degrau na frente (lado leste, virado para o lobby). */
    private void portal(World w, int x, int y, int z, String moldura, String detalhe, String degrau) {
        for (int dz = -3; dz <= 3; dz++) for (int dy = 1; dy <= 8; dy++) por(w, x, y + dy, z + dz, "minecraft:air");
        for (Entity e : w.getNearbyEntities(new Location(w, x + 0.5, y + 7, z + 0.5), 1.5, 3, 1.5))
            if (e.getPersistentDataContainer().has(kEnfeite)) e.remove();
        for (int dz = -2; dz <= 2; dz++)
            for (int dy = 0; dy <= 5; dy++) {
                boolean borda = Math.abs(dz) == 2 || dy == 0 || dy == 5;
                String b;
                if (!borda) b = "minecraft:nether_portal[axis=z]";
                else if ((dy == 0 || dy == 5) && Math.abs(dz) == 2) b = detalhe;
                else if (dy % 2 == 1 && Math.abs(dz) == 2) b = detalhe;
                else b = moldura;
                por(w, x, y + dy, z + dz, b);
            }
        // abre o caminho na frente (lado do lobby): sem isso o portal pode nascer atras de uma parede
        for (int dx = 1; dx <= 6; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 5; dy++) por(w, x + dx, y + dy, z + dz, "minecraft:air");
                por(w, x + dx, y, z + dz, degrau);
            }
        por(w, x, y + 6, z, "minecraft:soul_lantern");
    }

    private void texto(World w, Location l, String t, float escala) {
        w.spawn(l, TextDisplay.class, x -> {
            x.text(Masmorras.txt(t));
            x.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            x.setShadowed(true);
            x.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
            x.setPersistent(true);
            x.setLineWidth(240);
            x.getPersistentDataContainer().set(kEnfeite, PersistentDataType.INTEGER, 1);
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(), new org.joml.Vector3f(escala), new org.joml.AxisAngle4f()));
        });
    }

    // ------------------------------------------------------------------ nivel do equipamento

    /** Quanto vale a peca: couro/madeira 1 ... netherite 5. Nada na mao/no corpo vale 0. */
    private static int pontos(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0;
        String m = it.getType().name();
        if (m.startsWith("NETHERITE")) return 5;
        if (m.startsWith("DIAMOND")) return 4;
        if (m.startsWith("IRON") || m.startsWith("CHAINMAIL")) return 3;
        if (m.startsWith("GOLDEN") || m.startsWith("STONE")) return 2;
        if (m.startsWith("LEATHER") || m.startsWith("WOODEN") || m.startsWith("TURTLE")) return 1;
        if (m.equals("BOW") || m.equals("CROSSBOW") || m.equals("TRIDENT")) return 3;
        return 0;
    }

    /**
     * Nivel do equipamento: media das 4 pecas de armadura com a arma da mao (arredondando para baixo).
     * Quem esta de netherite inteiro da 5; quem esta de ferro da 3; quem esta pelado da 0.
     */
    static int nivelDosItens(Player p) {
        int soma = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) soma += pontos(it);
        soma += pontos(p.getInventory().getItemInMainHand());
        return soma / 5;
    }

    // ------------------------------------------------------------------ atravessar

    /**
     * 17/11 (Paulo: "os portais teleportam para os portais de lvl diretamente, faca o teleporte ser na area do seu lvl, mas
     * distante ... 400 blocos de distancia das dungeons"): o portal leva para um ponto do anel do nivel que fique a pelo
     * menos portais.distancia-das-masmorras (400) de qualquer masmorra. Sorteia varios pontos e fica com o mais longe.
     */
    private Location longeDasMasmorras(int nivel) {
        World w = mm.normal;
        int[] z = mm.zonas();
        int borda = Math.max(z[2] + 200, pl.borda(Tipo.Onde.NORMAL) / 2 - 150);
        double r1, r2;
        switch (nivel) {
            case 2 -> { r1 = z[0]; r2 = z[1]; }
            case 3 -> { r1 = z[1]; r2 = z[2]; }
            case 4 -> { r1 = z[2]; r2 = z.length > 3 ? z[3] - 80 : z[2] + (borda - z[2]) * 0.5; }
            case 6 -> { r1 = z.length > 3 ? z[3] + 120 : borda - 300; r2 = borda; }
            default -> { r1 = z[2] + (borda - z[2]) * 0.5; r2 = borda; }
        }
        r1 += 40;
        r2 = Math.max(r1 + 10, r2 - 40);
        // 20/11 (Sui/Paulo: "os portais teleportam mais longe da dungeon do que ir andando do spawn"): agora cai a uns
        // 150 blocos de uma masmorra DAQUELE nivel (nem colado, nem longe); conta so as masmorras do nivel
        double quer = pl.getConfig().getDouble("portais.distancia-das-masmorras-v2", 150);
        List<double[]> portas = new ArrayList<>();
        for (Masmorra m : pl.todas) if (m.mundo.equals(w.getName()) && !m.demolindo && !m.tipo.secreta && m.tipo.nivel == nivel) portas.add(new double[] {m.saidaX, m.saidaZ});
        if (!portas.isEmpty()) {
            ThreadLocalRandom rr = ThreadLocalRandom.current();
            double[] alvo = portas.get(rr.nextInt(portas.size()));
            for (int t = 0; t < 40; t++) {
                double a = rr.nextDouble() * Math.PI * 2, d = quer * (0.8 + rr.nextDouble() * 0.4);
                int x = (int) (alvo[0] + Math.cos(a) * d), zz = (int) (alvo[1] + Math.sin(a) * d);
                if (!w.getWorldBorder().isInside(new Location(w, x, 64, zz)) || pertoDaSecreta(w, x, zz)) continue;
                boolean colado = false;
                for (Masmorra m : pl.todas) if (m.mundo.equals(w.getName()) && !m.demolindo && Math.hypot(m.saidaX - x, m.saidaZ - zz) < 70) colado = true;
                if (colado) continue;
                Location d0 = Loja.lugarPerto(w, x, w.getHighestBlockYAt(x, zz) + 1, zz);
                if (d0 != null) return d0;
            }
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int bx = 0, bz = 0;
        double melhor = -1;
        for (int t = 0; t < 80; t++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = r1 + rnd.nextDouble() * (r2 - r1);
            int x = (int) (Math.cos(a) * r), zz = (int) (Math.sin(a) * r);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, zz)) || pertoDaSecreta(w, x, zz)) continue;
            double perto = Double.MAX_VALUE;
            for (double[] d : portas) perto = Math.min(perto, (d[0] - x) * (d[0] - x) + (d[1] - zz) * (d[1] - zz));
            perto = Math.sqrt(perto);
            if (perto > melhor) {
                melhor = perto;
                bx = x;
                bz = zz;
            }
            if (perto >= quer) break;
        }
        if (melhor < 0) return noAnel(nivel);
        Location d = Loja.lugarPerto(w, bx, w.getHighestBlockYAt(bx, bz) + 1, bz);
        return d != null ? d : new Location(w, bx + 0.5, w.getHighestBlockYAt(bx, bz) + 1, bz + 0.5);
    }

    /** Para onde o portal leva: perto de uma masmorra daquele nivel; se nao houver, no anel daquele nivel. */
    private Location destino(int nivel) {
        // 15/11: as de nivel 5 ficam no End (e algumas de nivel 4 no Nether): procura nos tres mundos das masmorras,
        // primeiro no normal. Nunca fora da borda (Paulo morreu na hora: o anel do nivel 5 caia fora do mundo).
        for (World w : new World[] {mm.normal, mm.nether, mm.end}) {
            if (w == null) continue;
            List<Masmorra> l = new ArrayList<>();
            for (Masmorra m : pl.todas)
                if (m.mundo.equals(w.getName()) && m.tipo.nivel == nivel && !m.demolindo && !m.tipo.secreta
                        && !pertoDaSecreta(w, m.saidaX, m.saidaZ)
                        && w.getWorldBorder().isInside(new Location(w, m.saidaX, m.saidaY, m.saidaZ))) l.add(m);
            if (l.isEmpty()) continue;
            Masmorra m = l.get(ThreadLocalRandom.current().nextInt(l.size()));
            Location d = Loja.lugarPerto(w, m.saidaX, m.saidaY, m.saidaZ);
            return d != null ? d : new Location(w, m.saidaX + 0.5, m.saidaY + 1, m.saidaZ + 0.5);
        }
        return noAnel(nivel);
    }

    /** 17/11 (Paulo: "em hipotese alguma o portal leva ao ???, deixe que achem"): nunca deixa ninguem perto da secreta. */
    private boolean pertoDaSecreta(World w, double x, double z) {
        for (Masmorra s : pl.todas) {
            if (s.tipo == null || !s.tipo.secreta || !s.mundo.equals(w.getName())) continue;
            double dx = s.saidaX - x, dz = s.saidaZ - z;
            if (dx * dx + dz * dz < 128 * 128) return true;
        }
        return false;
    }

    /** Um lugar seguro no anel daquele nivel (quando ainda nao ha masmorra construida la). */
    private Location noAnel(int nivel) {
        World w = mm.normal;
        int[] z = mm.zonas();
        // a borda da config e o LADO do quadrado: o raio e a metade, com folga para nao nascer colado nela
        int borda = Math.max(z[2] + 200, pl.borda(Tipo.Onde.NORMAL) / 2 - 150);
        double r = switch (nivel) {
            case 2 -> (z[0] + z[1]) / 2.0;
            case 3 -> (z[1] + z[2]) / 2.0;
            case 4 -> z[2] + ((z.length > 3 ? z[3] : borda) - z[2]) * 0.4;
            case 6 -> z.length > 3 ? (z[3] + borda) / 2.0 : borda - 200;
            default -> z[2] + (borda - z[2]) * 0.7;
        };
        for (int t = 0; t < 12; t++) {
            double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            int x = (int) (Math.cos(a) * r), zz = (int) (Math.sin(a) * r);
            Location d = Loja.lugarPerto(w, x, w.getHighestBlockYAt(x, zz) + 1, zz);
            if (d != null && !pertoDaSecreta(w, d.getX(), d.getZ())) return d;
        }
        double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        int x = (int) (Math.cos(a) * r), zz = (int) (Math.sin(a) * r);
        return new Location(w, x + 0.5, w.getHighestBlockYAt(x, zz) + 1, zz + 0.5);
    }

    /** Qual portal e este bloco (ou -1). */
    private int qual(Block b) {
        for (int i = 0; i < PORTAIS.length; i++) {
            int[] q = PORTAIS[i];
            boolean ok = q[2] == 1 ? b.getZ() == q[1] && Math.abs(b.getX() - q[0]) <= 1 : b.getX() == q[0] && Math.abs(b.getZ() - q[1]) <= 1;
            if (ok) return i;
        }
        return -1;
    }

    /** Lugar logo na frente do portal, do lado do centro do lobby. */
    static Location frente(World w, int[] q, Block b, Location de) {
        double x = q[2] == 1 ? b.getX() + 0.5 : q[0] - Integer.signum(q[0]) + 0.5;
        double z = q[2] == 1 ? q[1] - Integer.signum(q[1]) + 0.5 : b.getZ() + 0.5;
        return new Location(w, x, de.getY(), z, de.getYaw(), de.getPitch());
    }

    @EventHandler(ignoreCancelled = true)
    public void andar(PlayerMoveEvent e) {
        if (!ativo()) return;
        Location to = e.getTo();
        World w = to.getWorld();
        if (mm.normal == null || !mm.normal.equals(w)) return;
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockZ() == e.getFrom().getBlockZ() && to.getBlockY() == e.getFrom().getBlockY()) return;
        Block b = to.getBlock();
        if (b.getType() != Material.NETHER_PORTAL) return;
        int i = qual(b);
        if (i < 0) return;
        Player p = e.getPlayer();
        long agora = System.currentTimeMillis();
        Long ate = espera.get(p.getUniqueId());
        if (ate != null && agora < ate) return;
        espera.put(p.getUniqueId(), agora + 4000L);
        int nivel = PORTAIS[i][3];
        // 16/11: o portal agora pede o Nivel de Masmorra (vencer todas as do nivel anterior), nao mais o equipamento
        if (pl.progresso != null && !pl.progresso.pode(p, nivel)) {
            pl.progresso.avisarTrancado(p, "A área de nível " + nivel, nivel);
            Location fora = frente(w, PORTAIS[i], b, to);
            p.teleport(fora);
            return;
        }
        // 17/11 (Sui: "os portais sao aleatorios... recomendo teleportar pras masmorras que faltam"): se ainda falta
        // vencer alguma masmorra deste nivel, o portal leva direto para a porta de uma delas (sorteada); senao, aleatorio
        if (pl.progresso != null && pl.progresso.ativo() && pl.getConfig().getBoolean("portais.levar-ao-que-falta", false)) {
            java.util.List<Tipo> falta = pl.progresso.faltam(p, nivel);
            java.util.List<Masmorra> opcoes = new java.util.ArrayList<>();
            for (Masmorra m : pl.todas)
                if (falta.contains(m.tipo) && !m.demolindo && !m.tipo.secreta && m.mundo.equals(w.getName())
                        && !pertoDaSecreta(w, m.saidaX, m.saidaZ)) opcoes.add(m);
            if (!opcoes.isEmpty()) {
                Masmorra alvo = opcoes.get(new java.util.Random().nextInt(opcoes.size()));
                Location porta = new Location(w, alvo.saidaX + 0.5, alvo.saidaY, alvo.saidaZ + 0.5, to.getYaw(), 0f);
                p.setFallDistance(0);
                p.teleportAsync(porta, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
                    if (!ok) return;
                    p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt(visual(nivel)[3] + "§l" + alvo.tipo.nome.toUpperCase(java.util.Locale.ROOT)),
                            Masmorras.txt("§7Falta na sua trilha §8(" + falta.size() + " do nível " + nivel + ")")));
                    p.playSound(p.getLocation(), "minecraft:block.portal.travel", 0.5f, 1.1f - nivel * 0.1f);
                    p.sendMessage(Masmorras.txt(Masmorras.PREFIXO + "§7O portal te trouxe para a porta da §f" + alvo.tipo.nome
                            + "§7, que falta para o seu Nível de Masmorra. §8(Quando terminar todas do nível, o portal volta a ser aleatório.)"));
                    pl.darBussola(p, alvo);
                });
                return;
            }
        }
        // 17/11: longe das masmorras (pedido do Paulo); portais.longe-das-masmorras: false volta ao jeito antigo
        Location d = pl.getConfig().getBoolean("portais.longe-das-masmorras", true) ? longeDasMasmorras(nivel) : destino(nivel);
        java.util.List<Tipo> faltam = pl.progresso != null && pl.progresso.ativo() ? pl.progresso.faltam(p, nivel) : java.util.List.of();
        p.setFallDistance(0);
        p.teleportAsync(d, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt(visual(nivel)[3] + "§lÁREA DE NÍVEL " + nivel),
                    Masmorras.txt("§7Tem uma masmorra perto: siga a bússola")));
            p.playSound(p.getLocation(), "minecraft:block.portal.travel", 0.5f, 1.1f - nivel * 0.1f);
            Masmorra perto = null;
            double md = Double.MAX_VALUE;
            for (Masmorra m : pl.todas) {
                if (!m.mundo.equals(p.getWorld().getName()) || m.tipo.nivel != nivel || m.demolindo || m.tipo.secreta) continue;
                if (!faltam.isEmpty() && !faltam.contains(m.tipo)) continue;   // a bussola aponta para uma que falta na trilha
                if (pertoDaSecreta(p.getWorld(), m.saidaX, m.saidaZ)) continue;
                double dd = pl.dist2(m, p.getLocation());
                if (dd < md) {
                    md = dd;
                    perto = m;
                }
            }
            if (perto != null) pl.darBussola(p, perto);
        });
    }

    // ------------------------------------------------------------------ /nivel

    /** Nome curto do material, em portugues de gente ("Peitoral de ferro"). */
    private static String nomeMat(ItemStack it) {
        if (it == null || it.getType().isAir()) return "§8(vazio)";
        String m = it.getType().name();
        String mat = m.startsWith("NETHERITE") ? "netherita"
                : m.startsWith("DIAMOND") ? "diamante"
                : m.startsWith("IRON") ? "ferro"
                : m.startsWith("CHAINMAIL") ? "malha"
                : m.startsWith("GOLDEN") ? "ouro"
                : m.startsWith("STONE") ? "pedra"
                : m.startsWith("LEATHER") ? "couro"
                : m.startsWith("WOODEN") ? "madeira"
                : m.startsWith("TURTLE") ? "casco"
                : null;
        String base = m.endsWith("_HELMET") || m.equals("TURTLE_HELMET") ? "Capacete"
                : m.endsWith("_CHESTPLATE") ? "Peitoral"
                : m.endsWith("_LEGGINGS") ? "Calça"
                : m.endsWith("_BOOTS") ? "Bota"
                : m.endsWith("_SWORD") ? "Espada"
                : m.endsWith("_AXE") ? "Machado"
                : m.equals("BOW") ? "Arco"
                : m.equals("CROSSBOW") ? "Besta"
                : m.equals("TRIDENT") ? "Tridente"
                : null;
        if (base == null) return "§7" + m.charAt(0) + m.substring(1).toLowerCase().replace('_', ' ');
        return "§f" + base + (mat == null ? "" : " de " + mat);
    }

    /** Cor conforme os pontos da peca (vermelho fraco, verde forte). */
    private static String corPontos(int n) {
        return switch (n) {
            case 5 -> "§d";
            case 4 -> "§b";
            case 3 -> "§a";
            case 2 -> "§e";
            case 1 -> "§6";
            default -> "§c";
        };
    }

    private static void linha(Player p, String rotulo, ItemStack it) {
        int n = pontos(it);
        p.sendMessage(Masmorras.txt("§8| §7" + rotulo + " §8- " + nomeMat(it) + " " + corPontos(n) + n));
    }

    /**
     * /nivel — mostra ao jogador o nivel do equipamento dele, quais areas ja pode entrar
     * e quanto falta para a proxima. So texto, para funcionar igual no Java e no Bedrock.
     */
    static void comando(Player p) {
        ItemStack[] arm = p.getInventory().getArmorContents(); // bota, calca, peitoral, capacete
        ItemStack mao = p.getInventory().getItemInMainHand();
        int soma = 0;
        for (ItemStack it : arm) soma += pontos(it);
        soma += pontos(mao);
        int nivel = soma / 5;

        p.sendMessage(Masmorras.txt("§8+--------------------------------+"));
        p.sendMessage(Masmorras.txt(" " + visual(Math.max(2, Math.min(5, nivel)))[3] + "§lSEU NÍVEL DE EQUIPAMENTO"));
        linha(p, "Capacete", arm.length > 3 ? arm[3] : null);
        linha(p, "Peitoral", arm.length > 2 ? arm[2] : null);
        linha(p, "Calça   ", arm.length > 1 ? arm[1] : null);
        linha(p, "Botas   ", arm.length > 0 ? arm[0] : null);
        linha(p, "Na mão  ", mao);
        p.sendMessage(Masmorras.txt("§8|"));
        p.sendMessage(Masmorras.txt("§8| §fNível §l" + nivel + " §7(" + soma + " de 25 pontos)"));

        StringBuilder abertas = new StringBuilder("§a1");
        StringBuilder fechadas = new StringBuilder();
        for (int n : NIVEIS) {
            if (nivel >= n) abertas.append("§7, §a").append(n);
            else fechadas.append(fechadas.length() == 0 ? "" : "§7, ").append("§c").append(n);
        }
        p.sendMessage(Masmorras.txt("§8| §7Áreas liberadas: " + abertas));
        if (fechadas.length() > 0) p.sendMessage(Masmorras.txt("§8| §7Ainda trancadas: " + fechadas));

        if (nivel >= 5) {
            p.sendMessage(Masmorras.txt("§8| §dEquipamento no máximo. Todos os portais são seus."));
        } else {
            int falta = (nivel + 1) * 5 - soma;
            p.sendMessage(Masmorras.txt("§8| §eFaltam §f" + falta + " ponto" + (falta == 1 ? "" : "s")
                    + " §epara o nível §f" + (nivel + 1) + "§e."));
            int pior = 0, piorN = 99;
            String[] rot = {"as botas", "a calça", "o peitoral", "o capacete"};
            for (int i = 0; i < arm.length && i < 4; i++) {
                int n = pontos(arm[i]);
                if (n < piorN) { piorN = n; pior = i; }
            }
            if (pontos(mao) < piorN) p.sendMessage(Masmorras.txt("§8| §7Comece melhorando §fa arma da mão§7."));
            else p.sendMessage(Masmorras.txt("§8| §7Comece melhorando §f" + rot[pior] + "§7."));
        }
        p.sendMessage(Masmorras.txt("§8| §7Vale: §fcouro/madeira 1§7, §fpedra/ouro 2§7, §fferro/malha 3§7, §fdiamante 4§7, §fnetherita 5§7."));
        p.sendMessage(Masmorras.txt("§8+--------------------------------+"));
    }

}
