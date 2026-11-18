package br.servidor.masmorras;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 18/11 (pedido do Paulo): o lobby das masmorras com vida.
 * <ul>
 * <li>Os NPCs do lobby andam devagar perto do lugar deles (pra la e pra ca), param e olham para quem chega perto.</li>
 * <li>Falam frases simples com quem passa perto (no chat so daquele jogador, sem spam).</li>
 * <li>Tres moradores novos andando pela praca (guarda, dona das flores e um menino).</li>
 * <li>A trilha da masmorra ???: um velho escondido atras da torre nordeste (fora da muralha) conta a lenda e manda para
 * o Vigia das Ruinas, longe do spawn; o Vigia manda para a Andarilha, que da as coordenadas da masmorra secreta. A etapa
 * de cada jogador fica guardada nele (quem nao passou pela pista anterior so ouve conversa vaga).</li>
 * </ul>
 */
final class NpcsLobby implements Listener {

    private final Masmorras pl;
    private final NamespacedKey kMorador, kPista, kEtapa;
    private final NamespacedKey kMercador, kGuia, kClasses, kVendedor, kMissao;
    private World mundo;
    private int y;

    /** Estado de quem anda: casa, raio, alvo e pausa. */
    private static final class Andante {
        double cx, cz, raio, tx, tz, vel;
        int pausa;
        String nome;
        String[] falas;
    }

    private final Map<UUID, Andante> andantes = new HashMap<>();
    private final Map<String, Long> falou = new HashMap<>();
    private final Map<UUID, Long> ultimaFala = new HashMap<>();

    // moradores: nome, profissao, x, z, raio, bebe, falas
    private static final Object[][] MORADORES = {
            {"§7§lGuarda Bento", "nitwit", 6.5, -30.5, 3.5, false, new String[] {
                    "Fique de olho: à noite os monstros rondam os portões.",
                    "Dizem que quem passa do nível 4 nunca volta igual.",
                    "Tem um velho que se esconde atrás de uma das torres... fala umas coisas estranhas.",
                    "Portão verde ao norte: é por ali que começa o nível 1."}},
            {"§d§lDona Cida", "farmer", -21.5, -8.5, 2.0, false, new String[] {
                    "Essas flores eu plantei com muito carinho!",
                    "Não pisa no canteiro, viu?",
                    "A coroa da fonte brilha mais quando chove... ou é impressão minha?",
                    "Meu avô contava da Era de Ouro, quando o mundo inteiro tinha um rei só."}},
            {"§e§lTéo", "none", -5.5, 28.5, 5.0, true, new String[] {
                    "Quando eu crescer vou derrotar o Rei Esqueleto!",
                    "Já viu a coroa gigante? É de ouro de verdade!",
                    "Duvido você bater mais forte que eu nos bonecos!",
                    "Minha mãe falou que tem um monstro de olho branco... mas eu não tenho medo. Quase."}},
    };

    // trilha da masmorra secreta
    static final int VELHO_X = 36, VELHO_Z = -37;
    private Location vigia, andarilha;

    NpcsLobby(Masmorras pl) {
        this.pl = pl;
        kMorador = new NamespacedKey(pl, "npc_morador");
        kPista = new NamespacedKey(pl, "npc_pista");
        kEtapa = new NamespacedKey(pl, "pista_secreta");
        kMercador = new NamespacedKey(pl, "mercador");
        kGuia = new NamespacedKey(pl, "lobby_guia");
        kClasses = new NamespacedKey(pl, "lobby_classes");
        kVendedor = new NamespacedKey(pl, "vendedor_classes");
        kMissao = new NamespacedKey(pl, "npc_missao");
    }

    void iniciar(World w, int yChao) {
        mundo = w;
        y = yChao;
        if (mundo == null) return;
        Bukkit.getScheduler().runTaskTimer(pl, this::conferir, 160L, 100L);
        Bukkit.getScheduler().runTaskTimer(pl, this::andar, 200L, 2L);
        Bukkit.getScheduler().runTaskTimer(pl, this::conversar, 220L, 20L);
        Bukkit.getScheduler().runTaskLater(pl, this::calcularTrilha, 400L);
    }

    // ------------------------------------------------------------------ quem anda

    private boolean temGente() {
        for (Player p : mundo.getPlayers()) if (p.getLocation().getX() * p.getLocation().getX() + p.getLocation().getZ() * p.getLocation().getZ() < 80 * 80) return true;
        return false;
    }

    /** A cada 5 s: acha os NPCs do lobby (e da trilha), cria os moradores que faltam. */
    private void conferir() {
        if (mundo == null || !mundo.isChunkLoaded(0, 0)) return;
        // moradores
        for (int i = 0; i < MORADORES.length; i++) {
            Object[] m = MORADORES[i];
            double x = (double) m[2], z = (double) m[3];
            if (!LobbyMundo.pronto(mundo, (int) Math.floor(x), (int) Math.floor(z))) continue;
            if (existe(kMorador, i)) continue;
            Location l = new Location(mundo, x, y + 1, z);
            Villager v = pl.loja.npc(l, (String) m[0], null, Loja.profissao(0), kMorador, i);
            String prof = (String) m[1];
            Villager.Profession pr = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(prof));
            if (pr != null) v.setProfession(pr);
            if ((boolean) m[5]) v.setBaby();
            v.setAgeLock(true);
        }
        // velho escondido atras da torre nordeste
        if (LobbyMundo.pronto(mundo, VELHO_X, VELHO_Z) && !existe(kPista, 1)) {
            Location l = Loja.lugarPerto(mundo, VELHO_X, y + 2, VELHO_Z);
            if (l == null) l = new Location(mundo, VELHO_X + 0.5, y + 1, VELHO_Z + 0.5);
            l.setYaw(135f);
            pista(l, 1, "§8§lVelho Encapuzado", "nitwit", Villager.Type.SWAMP);
            Block b = mundo.getBlockAt(l.getBlockX() + 1, l.getBlockY(), l.getBlockZ() + 1);
            if (b.getType().isAir()) b.setType(Material.SOUL_LANTERN, false);
        }
        if (vigia != null && garantirLonge(vigia, 2, "§6§lVigia das Ruínas", "cartographer", Villager.Type.DESERT)) acampamento(vigia, false);
        if (andarilha != null && garantirLonge(andarilha, 3, "§5§lAndarilha Cega", "cleric", Villager.Type.TAIGA)) acampamento(andarilha, true);
        tirarRepetidos();
        // registra quem anda
        andantes.keySet().removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || !e.isValid();
        });
        for (Entity e : mundo.getNearbyEntities(new Location(mundo, 0, y, 0), 52, 20, 52)) {
            if (!(e instanceof Villager || e instanceof org.bukkit.entity.Mannequin) || andantes.containsKey(e.getUniqueId())) continue;
            Andante a = casa(e);
            if (a == null) continue;
            a.tx = a.cx;
            a.tz = a.cz;
            a.pausa = ThreadLocalRandom.current().nextInt(20, 80);
            andantes.put(e.getUniqueId(), a);
        }
    }

    /** NPC repetido no lobby (criado de novo antes das entidades do chunk carregarem): fica so um de cada. */
    private void tirarRepetidos() {
        java.util.Set<String> vistos = new java.util.HashSet<>();
        NamespacedKey kBoneco = new NamespacedKey(pl, "lobby_boneco");
        int tirados = 0;
        for (Entity e : mundo.getNearbyEntities(new Location(mundo, 0, y, 0), 60, 30, 60)) {
            PersistentDataContainer d = e.getPersistentDataContainer();
            String id = null;
            if ((e instanceof Villager || e instanceof org.bukkit.entity.Mannequin) && !d.has(kBoneco)) {
                if (d.has(kMercador)) id = "merc";
                else if (d.has(kGuia)) id = "guia";
                else if (d.has(kClasses)) id = "classes";
                else if (d.has(kVendedor)) id = "vend";
                else if (d.has(kMissao)) id = "m" + d.get(kMissao, PersistentDataType.STRING);
                else if (d.has(kMorador)) id = "mor" + d.get(kMorador, PersistentDataType.INTEGER);
                else if (d.has(kPista)) id = "p" + d.get(kPista, PersistentDataType.INTEGER);
            } else if (d.has(kBoneco)) {
                int bx = e.getLocation().getBlockX(), bz = e.getLocation().getBlockZ();
                boolean certo = false;
                for (int[] c : LobbyMundo.BONECOS) if (c[0] == bx && c[1] == bz) certo = true;
                id = certo ? "b" + bx + "," + bz : "fora";
                if (!certo) vistos.add(id);
            }
            if (id == null) continue;
            if (!vistos.add(id)) {
                e.remove();
                tirados++;
            }
        }
        if (tirados > 0) pl.getLogger().info("Lobby: " + tirados + " NPC(s) repetido(s) tirado(s).");
    }

    private boolean existe(NamespacedKey k, int valor) {
        for (Entity e : mundo.getEntitiesByClass(Villager.class)) {
            Integer v = e.getPersistentDataContainer().get(k, PersistentDataType.INTEGER);
            if (v != null && v == valor) return true;
        }
        return false;
    }

    private Andante casa(Entity e) {
        PersistentDataContainer d = e.getPersistentDataContainer();
        Andante a = new Andante();
        a.vel = 0.075;
        if (d.has(kMercador)) {
            set(a, LobbyMundo.MERCADOR[0], LobbyMundo.MERCADOR[1], 1.6, "Mercador", "Relíquias fresquinhas! Moeda de Reviver pra quem tem coronas.", "Psiu... hoje eu tenho coisa rara. Dá uma olhada.", "Ouro, relíquia, item lendário... é só escolher.");
        } else if (d.has(kGuia)) {
            set(a, LobbyMundo.GUIA[0], LobbyMundo.GUIA[1], 1.6, "Guia", "Perdido? Fala comigo que eu te dou a bússola.", "Nível 1 fica aqui em volta. Quanto mais longe, mais perigo.", "Os portões coloridos levam direto pra área de cada nível.");
        } else if (d.has(kClasses)) {
            set(a, LobbyMundo.CLASSES[0], LobbyMundo.CLASSES[1], 0.9, "Mestre das Classes", "Já escolheu sua classe? Cada uma tem 5 habilidades.", "A habilidade nova libera conforme o seu nível de poder.");
        } else if (d.has(kVendedor)) {
            set(a, LobbyMundo.VENDEDOR_X + 0.5, LobbyMundo.VENDEDOR_Z + 0.5, 0.9, "Vendedor de Classes", "Sem VIP? Eu vendo classe por coronas!", "Lutador, Saiyajin, Cipó e Tridente... qual vai ser?");
        } else if (d.has(kMissao)) {
            String id = d.get(kMissao, PersistentDataType.STRING);
            if (pl.missoes == null) return null;
            for (Missoes.Npc n : pl.missoes.npcs())
                if (n.id().equals(id)) {
                    String[] f = switch (id) {
                        case "ferreiro" -> new String[] {"Traz minério que eu forjo coisa boa.", "Espada cega não mata chefe. Vem cá."};
                        case "armeira" -> new String[] {"Armadura boa salva vida lá embaixo.", "Tenho missão pra você, se tiver coragem."};
                        case "cacadora" -> new String[] {"Meu arco nunca erra... quase nunca.", "Tem bicho grande lá fora. Quer caçar comigo?"};
                        default -> new String[] {"Poção fresquinha, ainda borbulhando!", "Cuidado com a fumaça roxa... ela morde."};
                    };
                    set(a, n.x() + 0.5, n.z() + 0.5, 1.1, n.nome(), f);
                    return a;
                }
            return null;
        } else if (d.has(kMorador)) {
            Integer i = d.get(kMorador, PersistentDataType.INTEGER);
            if (i == null || i < 0 || i >= MORADORES.length) return null;
            Object[] m = MORADORES[i];
            set(a, (double) m[2], (double) m[3], (double) m[4], ((String) m[0]).replaceAll("§.", ""), (String[]) m[6]);
            if ((boolean) m[5]) a.vel = 0.12;
        } else return null;
        return a;
    }

    private static void set(Andante a, double x, double z, double r, String nome, String... falas) {
        a.cx = x;
        a.cz = z;
        a.raio = r;
        a.nome = nome;
        a.falas = falas;
    }

    /** A cada 2 ticks: anda um passinho ate o alvo; chegou, descansa; tem jogador perto, para e olha para ele. */
    private void andar() {
        if (mundo == null || andantes.isEmpty() || !temGente()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<UUID, Andante> en : andantes.entrySet()) {
            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof org.bukkit.entity.LivingEntity v) || !v.isValid()) continue;
            Andante a = en.getValue();
            Location l = v.getLocation();
            Player perto = null;
            double melhor = 5.5 * 5.5;   // como o Skins.olhar (6): perto de alguem, para e olha
            for (Player p : mundo.getPlayers()) {
                double d = p.getLocation().distanceSquared(l);
                if (d < melhor) {
                    melhor = d;
                    perto = p;
                }
            }
            if (perto != null) {
                Location o = perto.getEyeLocation();
                double dx = o.getX() - l.getX(), dz = o.getZ() - l.getZ(), dy = o.getY() - (l.getY() + 1.5);
                v.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
                continue;
            }
            if (a.pausa > 0) {
                a.pausa--;
                continue;
            }
            double dx = a.tx - l.getX(), dz = a.tz - l.getZ(), dist = Math.hypot(dx, dz);
            if (dist < 0.12) {
                a.pausa = r.nextInt(30, 110);   // 3 a 11 s parado
                double ang = r.nextDouble() * Math.PI * 2, rr = Math.sqrt(r.nextDouble()) * a.raio;
                a.tx = a.cx + Math.cos(ang) * rr;
                a.tz = a.cz + Math.sin(ang) * rr;
                continue;
            }
            double passo = Math.min(a.vel * 2, dist);
            Location nova = l.clone().add(dx / dist * passo, 0, dz / dist * passo);
            nova.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            nova.setPitch(0f);
            Block pe = nova.getBlock();
            if (!pe.isPassable() || !pe.getRelative(0, 1, 0).isPassable() || pe.getRelative(0, -1, 0).isPassable()) {
                a.tx = a.cx;   // bateu em algo: volta para casa
                a.tz = a.cz;
                a.pausa = 20;
                continue;
            }
            v.teleport(nova);
        }
    }

    /** A cada 1 s: quem chega perto de um NPC ouve uma frase dele (no maximo uma a cada 10 s, a mesma pessoa a cada 90 s). */
    private void conversar() {
        if (mundo == null || andantes.isEmpty()) return;
        long agora = System.currentTimeMillis();
        for (Player p : mundo.getPlayers()) {
            Location pl0 = p.getLocation();
            if (pl0.getX() * pl0.getX() + pl0.getZ() * pl0.getZ() > 60 * 60) continue;
            Long ult = ultimaFala.get(p.getUniqueId());
            if (ult != null && agora - ult < 10_000) continue;
            for (Map.Entry<UUID, Andante> en : andantes.entrySet()) {
                Entity e = Bukkit.getEntity(en.getKey());
                if (e == null || e.getLocation().distanceSquared(pl0) > 4.5 * 4.5) continue;
                String chave = p.getUniqueId() + "|" + en.getKey();
                Long f = falou.get(chave);
                if (f != null && agora - f < 90_000) continue;
                falar(p, e, en.getValue());
                falou.put(chave, agora);
                ultimaFala.put(p.getUniqueId(), agora);
                break;
            }
        }
        if (falou.size() > 4000) falou.entrySet().removeIf(x -> agora - x.getValue() > 90_000);
    }

    private void falar(Player p, Entity e, Andante a) {
        String frase = a.falas[ThreadLocalRandom.current().nextInt(a.falas.length)];
        p.sendMessage(Masmorras.txt("§e" + a.nome + "§7: §f" + frase));
        p.playSound(e.getLocation(), "minecraft:entity.villager.ambient", 0.8f, e instanceof Villager v && !v.isAdult() ? 1.5f : 1f);
    }

    /** Clique nos moradores: uma frase (os outros NPCs ja abrem os menus deles). */
    @EventHandler(priority = EventPriority.HIGH)
    public void clicou(PlayerInteractEntityEvent e) {
        Entity x = e.getRightClicked();
        PersistentDataContainer d = x.getPersistentDataContainer();
        boolean morador = d.has(kMorador), pista = d.has(kPista);
        if (!morador && !pista) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (morador) {
            Andante a = andantes.get(x.getUniqueId());
            if (a == null) a = casa(x);
            if (a != null) falar(p, x, a);
            return;
        }
        Integer qual = d.get(kPista, PersistentDataType.INTEGER);
        if (qual != null) pista(p, qual, x);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoApanha(org.bukkit.event.entity.EntityDamageEvent e) {
        PersistentDataContainer d = e.getEntity().getPersistentDataContainer();
        if (d.has(kMorador) || d.has(kPista)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naoVira(org.bukkit.event.entity.EntityTransformEvent e) {
        PersistentDataContainer d = e.getEntity().getPersistentDataContainer();
        if (d.has(kMorador) || d.has(kPista)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ trilha da masmorra ???

    private Masmorra secreta() {
        if (mundo == null) return null;
        for (Masmorra m : pl.todas) if (m.tipo.secreta && !m.demolindo && m.mundo.equals(mundo.getName())) return m;
        return null;
    }

    /** Vigia e Andarilha: lugares fixos (a partir da masmorra secreta), guardados em pistas.txt. */
    private void calcularTrilha() {
        Masmorra s = secreta();
        if (s == null) {
            pl.getLogger().info("Trilha da ???: sem masmorra secreta no " + (mundo == null ? "?" : mundo.getName()) + ".");
            return;
        }
        File f = new File(pl.getDataFolder(), "pistas.txt");
        try {
            if (f.exists()) {
                String[] p = Files.readString(f.toPath()).trim().split("\\s+");
                if (p.length >= 7 && Integer.parseInt(p[6]) == s.saidaX + s.saidaZ) {
                    vigia = new Location(mundo, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                    andarilha = new Location(mundo, Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]));
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        double sx = s.saidaX, sz = s.saidaZ, ds = Math.hypot(sx, sz), ang = Math.atan2(sz, sx);
        Random rnd = new Random(s.saidaX * 31L + s.saidaZ);
        // Vigia: longe do spawn, meio caminho, desviado ~35 graus; Andarilha: uns 180 blocos antes da secreta, desviada
        double d2 = Math.max(350, Math.min(900, ds * 0.45)), a2 = ang + Math.toRadians(rnd.nextBoolean() ? 35 : -35);
        double d3 = Math.max(120, Math.min(220, ds * 0.15)), a3 = ang + Math.PI + Math.toRadians(rnd.nextBoolean() ? 50 : -50);
        vigia = new Location(mundo, Math.round(Math.cos(a2) * d2) + 0.5, -999, Math.round(Math.sin(a2) * d2) + 0.5);
        andarilha = new Location(mundo, Math.round(sx + Math.cos(a3) * d3) + 0.5, -999, Math.round(sz + Math.sin(a3) * d3) + 0.5);
        for (Location l : new Location[] {vigia, andarilha})
            if (!mundo.getWorldBorder().isInside(l)) l.multiply(0.8);
        salvarTrilha(s);
        pl.getLogger().info("Trilha da ???: Vigia em " + vigia.getBlockX() + "," + vigia.getBlockZ() + " e Andarilha em " + andarilha.getBlockX() + "," + andarilha.getBlockZ() + ".");
    }

    private void salvarTrilha(Masmorra s) {
        try {
            Files.writeString(new File(pl.getDataFolder(), "pistas.txt").toPath(), vigia.getX() + " " + vigia.getY() + " " + vigia.getZ() + " "
                    + andarilha.getX() + " " + andarilha.getY() + " " + andarilha.getZ() + " " + (s.saidaX + s.saidaZ) + "\n");
        } catch (java.io.IOException ignored) {
        }
    }

    /** Cria o NPC longe quando o chunk dele carrega. Devolve true quando acabou de criar (para montar o acampamento). */
    private boolean garantirLonge(Location l, int qual, String nome, String prof, Villager.Type tipo) {
        if (!LobbyMundo.pronto(mundo, l.getBlockX(), l.getBlockZ())) return false;
        if (existe(kPista, qual)) return false;
        if (l.getY() < -900) {
            Location chao = chaoLivre(l.getBlockX(), l.getBlockZ());
            if (chao == null) return false;
            l.setX(chao.getX());
            l.setY(chao.getY());
            l.setZ(chao.getZ());
            Masmorra s = secreta();
            if (s != null) salvarTrilha(s);
        }
        pista(l, qual, nome, prof, tipo);
        return true;
    }

    /** Chao seco e aberto perto de (x, z): espiral de ate 40 blocos. */
    private Location chaoLivre(int x0, int z0) {
        for (int r = 0; r <= 40; r += 4)
            for (int k = 0; k < Math.max(1, r * 2); k++) {
                double a = Math.PI * 2 * k / Math.max(1, r * 2);
                int x = x0 + (int) Math.round(Math.cos(a) * r), z = z0 + (int) Math.round(Math.sin(a) * r);
                if (!mundo.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block b = mundo.getHighestBlockAt(x, z);
                Material m = b.getType();
                if (!m.isSolid() || m.name().contains("LEAVES") || m.name().contains("LOG") || b.isLiquid()) continue;
                if (b.getRelative(0, 1, 0).isLiquid() || !b.getRelative(0, 1, 0).isPassable() || !b.getRelative(0, 2, 0).isPassable()) continue;
                return new Location(mundo, x + 0.5, b.getY() + 1, z + 0.5);
            }
        return null;
    }

    private void pista(Location l, int qual, String nome, String prof, Villager.Type tipo) {
        Villager v = pl.loja.npc(l, nome, null, Loja.profissao(0), kPista, qual);
        Villager.Profession pr = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(prof));
        if (pr != null) v.setProfession(pr);
        v.setVillagerType(tipo);
    }

    /** Fogueira, lanterna num poste e um bau quebrado (so enfeite) do lado do NPC. */
    private void acampamento(Location l, boolean almas) {
        int x = l.getBlockX(), yy = l.getBlockY(), z = l.getBlockZ();
        Block f = mundo.getBlockAt(x + 2, yy, z);
        if (f.getType().isAir() && f.getRelative(0, -1, 0).getType().isSolid())
            f.setBlockData(Bukkit.createBlockData(almas ? "minecraft:soul_campfire[lit=true]" : "minecraft:campfire[lit=true]"), false);
        Block poste = mundo.getBlockAt(x - 1, yy, z + 1);
        if (poste.getType().isAir() && poste.getRelative(0, 1, 0).getType().isAir() && poste.getRelative(0, -1, 0).getType().isSolid()) {
            poste.setType(Material.SPRUCE_FENCE, false);
            poste.getRelative(0, 1, 0).setType(almas ? Material.SOUL_LANTERN : Material.LANTERN, false);
        }
    }

    private static String direcao(double dx, double dz) {
        String[] n = {"leste", "sudeste", "sul", "sudoeste", "oeste", "noroeste", "norte", "nordeste"};
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return n[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    private static int arredonda(double d) {
        return (int) (Math.round(d / 50.0) * 50);
    }

    private int etapa(Player p) {
        Integer i = p.getPersistentDataContainer().get(kEtapa, PersistentDataType.INTEGER);
        return i == null ? 0 : i;
    }

    /** As falas da trilha, em partes (2 s entre elas), com som de caverna. */
    private void pista(Player p, int qual, Entity npc) {
        int et = etapa(p);
        List<String> f = new ArrayList<>();
        String quem;
        if (qual == 1) {
            quem = "§8Velho Encapuzado";
            f.add("Psiu... chega mais. Não deixa os outros ouvirem.");
            f.add("Na §6Era de Ouro§f, antes das masmorras, este mundo tinha um rei só, de coroa dourada.");
            f.add("Um dia chegou alguém de §lolhos brancos§f. Sem nome, sem sombra. O rei sumiu e a coroa se partiu.");
            f.add("De cada pedaço da coroa nasceu uma masmorra. Mas ele... ele ficou. Chamam a casa dele de §4§l???§f.");
            if (vigia != null) {
                double dx = vigia.getX() - npc.getLocation().getX(), dz = vigia.getZ() - npc.getLocation().getZ();
                f.add("Eu só sei o começo. Procure o §6Vigia das Ruínas§f, uns §e" + arredonda(Math.hypot(dx, dz)) + " blocos§f para o §e" + direcao(dx, dz) + "§f daqui. Fogueira acesa, não tem erro.");
                if (et < 1) p.getPersistentDataContainer().set(kEtapa, PersistentDataType.INTEGER, 1);
            } else f.add("Mas hoje a terra está quieta demais... volte outro dia.");
        } else if (qual == 2) {
            quem = "§6Vigia das Ruínas";
            if (et < 1) {
                f.add("Hm? Não sei do que você está falando, viajante. Siga seu caminho.");
            } else {
                f.add("Então o velho mandou você... faz anos que ninguém pergunta disso.");
                f.add("Eu vigio estas ruínas porque já vi §lele§f passar. As tochas apagam sozinhas e os bichos fogem.");
                f.add("Quem chega na casa dele não encontra porta: o chão é de §cnetherrack§f e o ar cheira a enxofre.");
                if (andarilha != null) {
                    double dx = andarilha.getX() - npc.getLocation().getX(), dz = andarilha.getZ() - npc.getLocation().getZ();
                    f.add("A §5Andarilha Cega§f sabe o caminho exato. Ela está uns §e" + arredonda(Math.hypot(dx, dz)) + " blocos§f para o §e" + direcao(dx, dz) + "§f. Fogo azul do lado dela.");
                    if (et < 2) p.getPersistentDataContainer().set(kEtapa, PersistentDataType.INTEGER, 2);
                }
            }
        } else {
            quem = "§5Andarilha Cega";
            Masmorra s = secreta();
            if (et < 2 || s == null) {
                f.add("Não enxergo você... mas sinto que ainda não está pronto. Fale com quem vigia as ruínas.");
            } else {
                f.add("Eu perdi os olhos quando olhei para ele. Valeu a pena: agora eu só vejo o caminho.");
                f.add("Ele espera desde a Era de Ouro. Vá forte, vá acompanhado... e não olhe nos olhos dele.");
                f.add("A casa dele fica em §e§lX " + s.saidaX + " §7/ §e§lY " + s.saidaY + " §7/ §e§lZ " + s.saidaZ + "§f. Boa sorte, coroado.");
                if (et < 3) p.getPersistentDataContainer().set(kEtapa, PersistentDataType.INTEGER, 3);
            }
        }
        p.playSound(npc.getLocation(), qual == 3 ? "minecraft:ambient.soul_sand_valley.mood" : "minecraft:ambient.cave", 0.7f, 1f);
        for (int i = 0; i < f.size(); i++) {
            String linha = f.get(i);
            boolean ultima = i == f.size() - 1;
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                p.sendMessage(Masmorras.txt(quem + "§7: §f" + linha));
                if (ultima && qual == 3 && linha.contains("X ")) {
                    p.showTitle(net.kyori.adventure.title.Title.title(Masmorras.txt("§4§l???"), Masmorras.txt("§7as coordenadas estão no chat")));
                    p.playSound(p.getLocation(), "minecraft:entity.wither.ambient", 0.5f, 0.6f);
                }
            }, i * 40L);
        }
    }
}
