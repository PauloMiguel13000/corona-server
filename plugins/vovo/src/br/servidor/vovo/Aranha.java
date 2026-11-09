package br.servidor.vovo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Spider;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * A aranha gigante do esgoto. O padrao dela:
 * - dorme no ninho ate alguem passar da entrada do esgoto;
 * - anda entre os pontos do esgoto sem repetir uma ordem fixa;
 * - so enxerga em linha reta para a frente (nao ve atras de esquinas) e persegue quem estiver na frente dela;
 * - perdeu de vista, vai ate onde viu a pessoa por ultimo e depois volta a rondar;
 * - nao liga para barulho nem para itens largados, e o dardo da besta nao faz efeito nela;
 * - e grande demais para as passagens estreitas: as salinhas do esgoto e a entrada sao seguras;
 * - se pegar alguem, a pessoa perde um dia, igual a vovo.
 *
 * Por dentro sao duas entidades: um "motor" invisivel (urso polar, que anda so pelo chao e nao sobe parede)
 * e o corpo (uma aranha sem IA) que segue o motor a cada tick. A aranha de verdade escalava as paredes
 * do esgoto quando nao achava caminho e ficava presa no teto.
 */
final class Aranha {

    enum Estado { DORMINDO, RONDA, CACANDO, PROCURANDO }

    private final Vovo pl;
    private final Partida jogo;
    private final World mundo;
    private final Random rnd = new Random();
    private final List<Location> caminho;

    /** Quem anda (invisivel). */
    private PolarBear ent;
    /** O que aparece (aranha sem IA). */
    private Spider corpo;
    Estado estado = Estado.DORMINDO;
    UUID alvo;
    private long tick;
    private long vistoEm;
    private Location ultimoLocal;
    private Location destino;
    private long esperaAte;
    private int parado;
    private Location ultimaPos;
    private int ultimoPonto = -1;
    private long proximoChiado;
    /** Desde quando ela esta parada na frente de alguem que entrou numa passagem estreita (-1 = nao esta). */
    private long cercoDesde = -1;
    private final Map<UUID, Long> ignorarAte = new HashMap<>();
    /** Onde cada jogador estava no ciclo anterior e quanto andou desde entao (a velocidade do jogador no servidor nao serve). */
    private final Map<UUID, Location> posAnterior = new HashMap<>();
    private final Map<UUID, Double> andouAgora = new HashMap<>();

    Aranha(Vovo pl, Partida jogo, World mundo) {
        this.pl = pl;
        this.jogo = jogo;
        this.mundo = mundo;
        this.caminho = Casa.caminhoAranha(mundo);
    }

    private static long seg(double s) {
        return Math.round(s * 4);
    }

    private double ritmo(String tipo) {
        return switch (tipo) {
            case "ronda" -> jogo.dif("aranha-ritmo-ronda", pl.getConfig().getDouble("aranha.ritmo-ronda", 0.95));
            case "procura" -> jogo.dif("aranha-ritmo-procura", pl.getConfig().getDouble("aranha.ritmo-procura", 1.15));
            default -> jogo.dif("aranha-ritmo-caca", pl.getConfig().getDouble("aranha.ritmo-caca", 1.3));
        };
    }

    private double tamanho() {
        return Math.max(1.0, Math.min(3.0, pl.getConfig().getDouble("aranha.tamanho", 1.8)));
    }

    boolean ativa() {
        return pl.getConfig().getBoolean("aranha.ativa", true);
    }

    boolean viva() {
        return ent != null && ent.isValid() && corpo != null && corpo.isValid();
    }

    Location local() {
        return viva() ? ent.getLocation() : null;
    }

    LivingEntity entidade() {
        return corpo;
    }

    boolean eh(Entity e) {
        if (e == null) return false;
        return ent != null && e.getUniqueId().equals(ent.getUniqueId()) || corpo != null && e.getUniqueId().equals(corpo.getUniqueId());
    }

    /** Roda todo tick: o corpo acompanha o motor. */
    void sincronizar() {
        if (ent == null || corpo == null || !ent.isValid() || !corpo.isValid()) return;
        Location l = ent.getLocation();
        float yaw = ent.getBodyYaw();
        l.setYaw(yaw);
        l.setPitch(0);
        corpo.teleport(l);
        corpo.setRotation(yaw, 0);
    }

    boolean perseguindo(Player p) {
        return estado == Estado.CACANDO && p.getUniqueId().equals(alvo);
    }

    private static void atributo(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    void criar() {
        remover();
        Location l = Casa.ninhoAranha(mundo);
        double t = tamanho();
        ent = mundo.spawn(l, PolarBear.class, b -> {
            b.getPersistentDataContainer().set(pl.kVovo, PersistentDataType.BYTE, (byte) 1);
            b.setAdult();
            b.setAgeLock(true);
            b.setPersistent(false);
            b.setRemoveWhenFarAway(false);
            b.setInvulnerable(true);
            b.setInvisible(true);
            b.setSilent(true);
            b.setCanPickupItems(false);
            b.setCollidable(false);
            // o motor e mais estreito que a aranha que aparece: com a largura toda ela raspava nas quinas dos tuneis e
            // quase parava em toda curva. Ainda e largo demais para as portas e passagens de 1 bloco.
            atributo(b, Attribute.SCALE, Math.max(1.0, t * 0.75));
            b.addScoreboardTag("clearlag_exclude");
            marcarSemNivel(b);
            atributo(b, Attribute.MOVEMENT_SPEED, 0.25);
            atributo(b, Attribute.FOLLOW_RANGE, 64);
            atributo(b, Attribute.KNOCKBACK_RESISTANCE, 1);
            atributo(b, Attribute.STEP_HEIGHT, 1.0);
            // com um efeito ativo o Paper nunca deixa o motor "inativo" quando os jogadores estao longe
            b.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        });
        try {
            Bukkit.getMobGoals().removeAllGoals(ent);
        } catch (Throwable e) {
            pl.getLogger().warning("Nao consegui tirar a IA padrao da aranha: " + e);
        }
        ent.getPathfinder().setCanFloat(false);
        ent.getPathfinder().setCanOpenDoors(false);
        ent.getPathfinder().setCanPassDoors(false);
        corpo = mundo.spawn(l, Spider.class, s -> {
            s.getPersistentDataContainer().set(pl.kVovo, PersistentDataType.BYTE, (byte) 1);
            s.setAI(false);
            s.setGravity(false);
            s.setSilent(true);
            s.setPersistent(false);
            s.setRemoveWhenFarAway(false);
            s.setInvulnerable(true);
            s.setCanPickupItems(false);
            s.setCollidable(false);
            s.addScoreboardTag("clearlag_exclude");
            marcarSemNivel(s);
            atributo(s, Attribute.SCALE, t);
        });
        cercoDesde = -1;
        estado = Estado.DORMINDO;
        alvo = null;
        destino = null;
        esperaAte = 0;
        parado = 0;
        ultimaPos = l.clone();
        ignorarAte.clear();
    }

    /** Pede ao LevelledMobs para nao dar nivel (ele mexe na velocidade e na vida). */
    private static void marcarSemNivel(LivingEntity e) {
        org.bukkit.NamespacedKey sn = org.bukkit.NamespacedKey.fromString("levelledmobs:nolevel");
        if (sn != null) e.getPersistentDataContainer().set(sn, PersistentDataType.STRING, "vovo");
    }

    void remover() {
        posAnterior.clear();
        andouAgora.clear();
        if (ent != null) ent.remove();
        if (corpo != null) corpo.remove();
        ent = null;
        corpo = null;
        alvo = null;
        destino = null;
        estado = Estado.DORMINDO;
    }

    private void parar() {
        if (viva()) ent.getPathfinder().stopPathfinding();
    }

    private void mover(Location para, double r) {
        if (!viva() || para == null) return;
        ent.getPathfinder().moveTo(para, r);
    }

    private boolean chegou(Location d) {
        if (!viva() || d == null) return true;
        Location l = ent.getLocation();
        double dx = l.getX() - d.getX(), dz = l.getZ() - d.getZ();
        return dx * dx + dz * dz < 4.0 && Math.abs(l.getY() - d.getY()) < 2;
    }

    private Player procurar(List<Player> vivos) {
        Location olho = ent.getEyeLocation();
        Vector frente = olho.getDirection().setY(0);
        if (frente.lengthSquared() < 1e-6) frente = new Vector(0, 0, 1);
        frente.normalize();
        Player melhor = null;
        double melhorD = 999;
        for (Player p : vivos) {
            Partida.Jogador j = jogo.jogadores.get(p.getUniqueId());
            if (j == null || tick < j.graca || !Casa.noEsgoto(p.getLocation())) continue;
            Long ig = ignorarAte.get(p.getUniqueId());
            if (ig != null && tick < ig) continue;
            boolean seguindo = perseguindo(p);
            Vector para = p.getEyeLocation().toVector().subtract(olho.toVector());
            double d = para.length();
            if (d > (seguindo ? jogo.dif("aranha-visao-caca", 32) : jogo.dif("aranha-visao", 26))) continue;
            if (d > 3.5) {
                Vector h = para.clone().setY(0);
                if (h.lengthSquared() > 1e-6 && Math.toDegrees(frente.angle(h.normalize())) > (seguindo ? 75 : jogo.dif("aranha-angulo", 70))) continue;
            }
            if (!ent.hasLineOfSight(p)) continue;
            if (d < melhorD) {
                melhorD = d;
                melhor = p;
            }
        }
        return melhor;
    }

    /** Quem ela escuta agora (o mais perto): correndo ate "aranha-ouve-correndo" blocos, andando ate "aranha-ouve-andando". */
    private Player ouvir(List<Player> vivos) {
        double correndo = jogo.dif("aranha-ouve-correndo", 20), andando = jogo.dif("aranha-ouve-andando", 7);
        Location v = ent.getLocation();
        Player melhor = null;
        double melhorD = 1e9;
        for (Player p : vivos) {
            Partida.Jogador j = jogo.jogadores.get(p.getUniqueId());
            if (j == null || tick < j.graca || p.isSneaking() || !Casa.noEsgoto(p.getLocation())) continue;
            Long ig = ignorarAte.get(p.getUniqueId());
            if (ig != null && tick < ig) continue;
            Double andou = andouAgora.get(p.getUniqueId());
            if ((andou == null || andou < 0.35) && !p.isSprinting()) continue;      // parado nao faz barulho
            double d = p.getLocation().distance(v);
            if (d <= (p.isSprinting() ? correndo : andando) && d < melhorD) {
                melhorD = d;
                melhor = p;
            }
        }
        return melhor;
    }

    /** Vai conferir um lugar (ouviu um barulho). */
    void investigar(Location onde, boolean chiar) {
        if (!viva() || estado == Estado.DORMINDO || estado == Estado.CACANDO || onde == null) return;
        estado = Estado.PROCURANDO;
        destino = onde.clone();
        parado = 0;
        esperaAte = 0;
        mover(destino, ritmo("procura"));
        if (chiar) mundo.playSound(ent.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 2.2f, 0.7f);
    }

    private void comecar(Player p) {
        boolean novo = estado != Estado.CACANDO;
        estado = Estado.CACANDO;
        alvo = p.getUniqueId();
        esperaAte = 0;
        parado = 0;
        if (novo) {
            mundo.playSound(ent.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 2.5f, 0.45f);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 1.8f);
            jogo.titulo(p, "", "§4§lA ARANHA TE VIU! SAI DA FRENTE DELA!", 1.4);
        }
    }

    /** Alguem atirou nela: ela vira para quem atirou, se estiver vendo. */
    void irritar(Player p) {
        if (!viva() || estado == Estado.DORMINDO) return;
        if (ent.hasLineOfSight(p) && Casa.noEsgoto(p.getLocation())) {
            comecar(p);
            vistoEm = tick;
            ultimoLocal = p.getLocation();
        }
        mundo.playSound(ent.getLocation(), Sound.ENTITY_SPIDER_HURT, 2f, 0.5f);
    }

    void acordar() {
        if (!viva() || estado != Estado.DORMINDO) return;
        estado = Estado.RONDA;
        destino = null;
        esperaAte = 0;
        mundo.playSound(ent.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 3f, 0.4f);
        jogo.avisar("§8Algo enorme se mexeu no esgoto... §7Ela ouve quem §fcorre§7: ande §fagachado§7 e saia da frente dela.");
    }

    void aqui(Location l) {
        if (!viva()) criar();
        parar();
        ent.teleport(l);
        sincronizar();
        if (estado == Estado.DORMINDO) acordar();
    }

    void cacar(Player p) {
        if (!viva()) criar();
        if (estado == Estado.DORMINDO) acordar();
        comecar(p);
        vistoEm = tick;
        ultimoLocal = p.getLocation();
    }

    /** Roda no ciclo da partida (a cada 5 ticks). */
    void tick(long t, List<Player> vivos) {
        tick = t;
        if (!ativa()) {
            if (ent != null) remover();
            return;
        }
        if (!viva()) {
            Estado antes = estado;
            criar();
            if (antes != Estado.DORMINDO) estado = Estado.RONDA;
        }
        andouAgora.clear();
        for (Player p : vivos) {
            Location agora = p.getLocation(), antes = posAnterior.put(p.getUniqueId(), agora);
            if (antes != null && antes.getWorld() == agora.getWorld()) andouAgora.put(p.getUniqueId(), antes.distance(agora));
        }
        Location v = ent.getLocation();
        if (!Casa.areaDaAranha(v)) {
            // saiu da area dela de algum jeito: volta para um ponto longe de todo mundo
            parar();
            ent.teleport(pontoLonge(vivos));
            v = ent.getLocation();
            destino = null;
            if (estado == Estado.CACANDO) estado = Estado.RONDA;
        }

        if (estado == Estado.DORMINDO) {
            parar();
            for (Player p : vivos)
                if (Casa.alemDaEntrada(p.getLocation())) {
                    acordar();
                    break;
                }
            return;
        }

        // pega quem estiver colado nela
        double raio = 0.75 * tamanho() + 0.6;
        for (Player p : vivos) {
            Partida.Jogador j = jogo.jogadores.get(p.getUniqueId());
            if (j == null || tick < j.graca) continue;
            Location pl0 = p.getLocation();
            double dx = pl0.getX() - v.getX(), dz = pl0.getZ() - v.getZ();
            if (dx * dx + dz * dz < raio * raio && Math.abs(pl0.getY() - v.getY()) < 1.8 && ent.hasLineOfSight(p)) {
                parar();
                corpo.swingMainHand();
                jogo.pegarPelaAranha(p);
                estado = Estado.RONDA;
                alvo = null;
                destino = null;
                esperaAte = t + seg(4);
                return;
            }
        }

        Player visto = procurar(vivos);
        if (visto != null) {
            if (estado != Estado.CACANDO || !visto.getUniqueId().equals(alvo)) comecar(visto);
            vistoEm = t;
            ultimoLocal = visto.getLocation();
        } else if (estado == Estado.RONDA || estado == Estado.PROCURANDO) {
            // nao viu ninguem, mas OUVE: quem corre faz barulho de longe, quem anda so de perto; agachado nao faz nenhum
            Player ouvido = ouvir(vivos);
            if (ouvido != null && (estado == Estado.RONDA || t % 8 == 0)) investigar(ouvido.getLocation(), estado == Estado.RONDA);
        }

        switch (estado) {
            case CACANDO -> {
                Player a = alvo == null ? null : Bukkit.getPlayer(alvo);
                if (a == null || !jogo.vivo(a) || !Casa.noEsgoto(a.getLocation())) {
                    estado = Estado.RONDA;
                    alvo = null;
                    destino = null;
                    break;
                }
                if (!Casa.areaDaAranha(a.getLocation())) {
                    // a pessoa entrou numa salinha ou passagem estreita: ela nao cabe. Antes ela ficava refazendo o
                    // caminho a cada passo da pessoa e andava de um lado para o outro na frente da porta, sem parar.
                    // Agora chega perto UMA vez, fica ameacando alguns segundos e vai embora.
                    if (cercoDesde < 0) {
                        cercoDesde = t;
                        destino = a.getLocation();
                        mover(destino, ritmo("caca"));
                    } else if (t - cercoDesde >= seg(6)) {
                        ignorarAte.put(a.getUniqueId(), t + seg(12));
                        estado = Estado.RONDA;
                        alvo = null;
                        destino = null;
                        esperaAte = 0;
                        cercoDesde = -1;
                        parado = 0;
                        mundo.playSound(v, Sound.ENTITY_SPIDER_AMBIENT, 2f, 0.6f);
                    } else if (parado >= seg(1)) {
                        // chegou o mais perto que da: para e encara
                        parar();
                        destino = null;
                        Vector para = a.getLocation().toVector().subtract(v.toVector()).setY(0);
                        if (para.lengthSquared() > 1e-4) {
                            Location olha = v.clone();
                            olha.setDirection(para);
                            ent.setRotation(olha.getYaw(), 0);
                            ent.setBodyYaw(olha.getYaw());
                        }
                    }
                    break;
                }
                cercoDesde = -1;
                if (t - vistoEm > seg(3)) {
                    estado = Estado.PROCURANDO;
                    destino = ultimoLocal;
                    alvo = null;
                    parado = 0;
                    mover(destino, ritmo("procura"));
                    break;
                }
                destino = t - vistoEm <= 1 ? a.getLocation() : ultimoLocal;
                mover(destino, ritmo("caca"));
                // ve a pessoa mas nao consegue chegar (ela esta numa passagem estreita): desiste por um tempo
                if (parado >= seg(4)) {
                    ignorarAte.put(a.getUniqueId(), t + seg(10));
                    estado = Estado.RONDA;
                    alvo = null;
                    destino = null;
                    parado = 0;
                    mundo.playSound(v, Sound.ENTITY_SPIDER_AMBIENT, 2f, 0.6f);
                }
            }
            case PROCURANDO -> {
                if (chegou(destino) || parado >= seg(3)) {
                    estado = Estado.RONDA;
                    destino = null;
                    esperaAte = t + seg(1 + rnd.nextDouble() * 2);
                    parar();
                } else if (t % 4 == 0) mover(destino, ritmo("procura"));
            }
            default -> ronda();
        }

        // travada no mesmo lugar
        boolean querAndar = destino != null && !chegou(destino) && esperaAte <= t;
        if (querAndar && ultimaPos != null && v.distanceSquared(ultimaPos) < 0.02) parado++;
        else parado = 0;
        ultimaPos = v.clone();
        if (estado == Estado.RONDA && parado >= seg(5)) {
            destino = null;
            parado = 0;
        }
        if (parado >= seg(15)) {
            parado = 0;
            parar();
            ent.teleport(pontoLonge(vivos));
            sincronizar();
            destino = null;
        }

        // sons
        boolean andando = ent.getVelocity().setY(0).lengthSquared() > 0.0004;
        if (andando && t % (estado == Estado.CACANDO ? 1 : 2) == 0) mundo.playSound(v, Sound.ENTITY_SPIDER_STEP, 1.2f, 0.55f);
        if (t >= proximoChiado) {
            proximoChiado = t + seg(estado == Estado.CACANDO ? 1.5 : 6 + rnd.nextInt(6));
            mundo.playSound(v, Sound.ENTITY_SPIDER_AMBIENT, estado == Estado.CACANDO ? 2f : 1.4f, 0.5f + rnd.nextFloat() * 0.1f);
        }
        if (estado == Estado.CACANDO && t % 4 == 0) mundo.spawnParticle(Particle.SMOKE, v.clone().add(0, 0.6, 0), 3, 0.6, 0.2, 0.6, 0.01);
    }

    /** Um ponto da ronda longe de todo mundo (para ela "reaparecer" sem ninguem ver). */
    private Location pontoLonge(List<Player> vivos) {
        List<Location> ok = new java.util.ArrayList<>();
        for (Location c : caminho) {
            boolean longe = true;
            for (Player p : vivos) if (p.getWorld().equals(mundo) && p.getLocation().distanceSquared(c) < 22 * 22) longe = false;
            if (longe) ok.add(c);
        }
        return ok.isEmpty() ? Casa.ninhoAranha(mundo) : ok.get(rnd.nextInt(ok.size())).clone();
    }

    private void ronda() {
        if (destino == null || chegou(destino)) {
            if (esperaAte == 0) {
                esperaAte = tick + seg(0.4 + rnd.nextDouble() * 1.2);
                parar();
                return;
            }
            if (tick < esperaAte) return;
            esperaAte = 0;
            int i = proximoPonto();
            ultimoPonto = i;
            destino = caminho.get(i);
            mover(destino, ritmo("ronda"));
        } else if (tick % 8 == 0) mover(destino, ritmo("ronda"));
    }

    /**
     * Proximo ponto da ronda. Antes era sorteado entre todos: ela ia ate um ponto, dava meia-volta, voltava pelo mesmo
     * tunel, virava de novo... parecia perdida. Agora, entre alguns pontos sorteados, escolhe o que fica mais "para a
     * frente" de onde ela esta olhando e nao muito perto, entao a ronda da voltas pelo esgoto.
     */
    private int proximoPonto() {
        Location l = ent.getLocation();
        Vector frente = l.getDirection().setY(0);
        if (frente.lengthSquared() < 1e-6) frente = new Vector(0, 0, 1);
        frente.normalize();
        int melhor = -1;
        double melhorNota = -1e9;
        for (int k = 0; k < 6; k++) {
            int i = rnd.nextInt(caminho.size());
            if (i == ultimoPonto && caminho.size() > 1) continue;
            Vector para = caminho.get(i).toVector().subtract(l.toVector()).setY(0);
            double d = para.length();
            if (d < 1e-3) continue;
            double nota = frente.dot(para.clone().multiply(1 / d)) * 2.0 + (d < 14 ? -1.5 : 0) + rnd.nextDouble() * 0.6;
            if (nota > melhorNota) {
                melhorNota = nota;
                melhor = i;
            }
        }
        if (melhor < 0) {
            do {
                melhor = rnd.nextInt(caminho.size());
            } while (caminho.size() > 1 && melhor == ultimoPonto);
        }
        return melhor;
    }

    String status() {
        if (!viva()) return "aranha=-";
        Location l = ent.getLocation();
        return String.format("aranha=%s %.1f %.1f %.1f destino=%s alvo=%s", estado, l.getX(), l.getY(), l.getZ(),
                destino == null ? "-" : String.format("%.1f %.1f %.1f", destino.getX(), destino.getY(), destino.getZ()),
                alvo == null ? "-" : Bukkit.getOfflinePlayer(alvo).getName());
    }
}
