package br.servidor.backrooms;

import java.util.List;
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
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Uma criatura que caca os jogadores. Por dentro sao duas entidades: um zumbi invisivel que anda
 * (o "motor", com o buscador de caminho do Minecraft) e o corpo que aparece, sem IA, que segue o motor a cada tick.
 * Bacteria (Nivel 0 e 1): alta e preta, enxerga longe. O Uivador (Nivel 2): quase cego, mas ouve tudo.
 * O Cao (Nivel 3): anda de quatro. Encarar ele (sem correr) faz ele parar e rosnar; virar as costas ou correr faz
 * ele atacar mais rapido. Se for encarado por muito tempo, desiste e vai embora.
 * O corpo e um manequim com skin (funciona no Java e no Bedrock).
 */
final class Cacador {

    enum Tipo {
        BACTERIA("bacteria", "a Bactéria"), COISA("coisa", "o Uivador"), CAO("cao", "o Cão"), FESTEIRO("festeiro", "um Festeiro"),
        /** Nivel 6: so se mexe quando ninguem esta olhando para ela (com luz). */
        SOMBRA("sombra", "a Sombra");

        final String chave, nome;

        Tipo(String chave, String nome) {
            this.chave = chave;
            this.nome = nome;
        }
    }

    enum Ia { RONDA, INVESTIGANDO, CACANDO, PAUSA }

    private final Backrooms pl;
    private final Partida jogo;
    private final World mundo;
    private final Nivel nivel;
    final Tipo tipo;
    private final Random rnd = new Random();

    private Zombie motor;
    private Mannequin corpo;
    Ia ia = Ia.RONDA;
    private UUID alvo;
    private long tick, vistoEm, esperaAte, proximoSom, proximaVisita, proximoGrito;
    private int checagens;
    private Location ultimoLocal, destino, ultimaPos, localPreso;
    private int parado, tempoPreso;
    /** Depois de pegar alguem ela some e reaparece longe (senao ficava esperando no ponto de volta). */
    private boolean sumirDepois;
    /** Cao: ha quantos ciclos esta sendo encarado e ate quando fica longe depois de desistir. */
    private int encarado;
    private long desistiuAte;
    /** Tremida da cabeca (a criatura vira a cabeca de repente, como um tique). */
    private float tique;
    /** Sombra: esta sendo observada agora (fica parada como estatua) e ha quanto tempo seguido. */
    boolean congelada;
    private int observadaHa;

    Cacador(Backrooms pl, Partida jogo, World mundo, Nivel nivel, Tipo tipo) {
        this.pl = pl;
        this.jogo = jogo;
        this.mundo = mundo;
        this.nivel = nivel;
        this.tipo = tipo;
    }

    private static long seg(double s) {
        return Math.round(s * 4);
    }

    private String skin() {
        return switch (tipo) {
            case BACTERIA -> "bacteria";
            case CAO -> "cao";
            case FESTEIRO -> "festeiro";
            case SOMBRA -> "sombra";
            default -> nivel != null && nivel.id == 5 ? "afogado" : "uivador";   // Nivel 37 (piscinas): os Afogados
        };
    }

    double escala() {
        return switch (tipo) {
            case BACTERIA -> 1.6;
            case CAO, FESTEIRO -> 1.05;
            case SOMBRA -> 1.12;
            default -> 1.25;
        };
    }

    private double cfg(String k, double padrao) {
        return jogo.dif(tipo.chave + "-" + k, padrao);
    }

    boolean vivo() {
        return motor != null && motor.isValid() && corpo != null && corpo.isValid();
    }

    Location local() {
        return vivo() ? motor.getLocation() : null;
    }

    boolean eh(Entity e) {
        if (e == null) return false;
        return motor != null && e.getUniqueId().equals(motor.getUniqueId()) || corpo != null && e.getUniqueId().equals(corpo.getUniqueId());
    }

    boolean cacando(Player p) {
        return ia == Ia.CACANDO && p.getUniqueId().equals(alvo);
    }

    private static void atributo(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    /**
     * O motor e um zumbi: LevelledMobs/EliteMobs podiam dar nivel a ele depois de nascer e a velocidade
     * subia do nada (a criatura passava o jogador). Tira qualquer bonus de velocidade vindo de outro plugin.
     */
    static void travarVelocidade(LivingEntity e) {
        if (e == null || !e.isValid()) return;
        limparBonus(e.getAttribute(Attribute.MOVEMENT_SPEED));
        if (e.hasPotionEffect(PotionEffectType.SPEED)) e.removePotionEffect(PotionEffectType.SPEED);
    }

    /** Tira os modificadores de outros plugins e os que aumentam a velocidade; base volta a 0,25. */
    static void limparBonus(AttributeInstance i) {
        if (i == null) return;
        for (org.bukkit.attribute.AttributeModifier m : new java.util.ArrayList<>(i.getModifiers())) {
            if (!"minecraft".equals(m.getKey().getNamespace()) || m.getAmount() > 0) i.removeModifier(m);
        }
        if (i.getBaseValue() != 0.25) i.setBaseValue(0.25);
    }

    void criar(Location l) {
        remover();
        motor = mundo.spawn(l, Zombie.class, z -> {
            z.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
            // LevelledMobs/EliteMobs nao mexem no motor (nada de nivel nem armadura de ferro flutuando no monstro)
            org.bukkit.NamespacedKey sn = org.bukkit.NamespacedKey.fromString("levelledmobs:nolevel"), sm = org.bukkit.NamespacedKey.fromString("levelledmobs:denylm_nametag");
            if (sn != null) z.getPersistentDataContainer().set(sn, PersistentDataType.STRING, "backrooms");
            if (sm != null) z.getPersistentDataContainer().set(sm, PersistentDataType.STRING, "backrooms");
            z.addScoreboardTag("clearlag_exclude");
            z.setAdult();
            z.setInvisible(true);
            z.setSilent(true);
            z.setCollidable(false);
            z.setInvulnerable(true);
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            try {
                z.setShouldBurnInDay(false);
            } catch (Throwable ignored) {
            }
            z.getEquipment().clear();
            // com um efeito ativo o Paper nunca deixa o motor "inativo" longe dos jogadores
            z.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            atributo(z, Attribute.FOLLOW_RANGE, 48);
            atributo(z, Attribute.KNOCKBACK_RESISTANCE, 1);
            atributo(z, Attribute.MOVEMENT_SPEED, 0.25);
        });
        try {
            Bukkit.getMobGoals().removeAllGoals(motor);
        } catch (Throwable t) {
            pl.getLogger().warning("Nao consegui tirar a IA padrao do zumbi: " + t);
        }
        motor.getPathfinder().setCanOpenDoors(false);
        motor.getPathfinder().setCanPassDoors(false);
        motor.getPathfinder().setCanFloat(false);
        travarVelocidade(motor);
        corpo = mundo.spawn(l, Mannequin.class, m -> {
            m.getPersistentDataContainer().set(pl.kEntidade, PersistentDataType.BYTE, (byte) 1);
            m.setProfile(Skins.perfil(skin()));
            m.setDescription(null);
            m.setImmovable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setInvulnerable(true);
            m.setCollidable(false);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
            // tamanhos dos originais: Bacteria ~2,5 m, Cao do tamanho de uma pessoa (de quatro), Uivador ~2,2 m
            // Bacteria 1,6 = ~2,9 m (alta e magra como a do jogo original)
            atributo(m, Attribute.SCALE, escala());
        });
        if (tipo == Tipo.CAO) {
            // de quatro, rente ao chao
            try {
                corpo.setPose(org.bukkit.entity.Pose.SWIMMING, true);
            } catch (Throwable t) {
                pl.getLogger().warning("Cao: nao deu para deixar de quatro: " + t);
            }
        }
        Visual.gemeo(corpo, skin());
        encarado = 0;
        congelada = false;
        observadaHa = 0;
        ia = Ia.RONDA;
        alvo = null;
        destino = null;
        esperaAte = 0;
        parado = 0;
        ultimaPos = l.clone();
        proximaVisita = tick + seg(20 + rnd.nextInt(20));
        // cada criatura faz o primeiro barulho numa hora diferente (senao todas estalavam juntas no inicio)
        proximoSom = tick + seg(4 + rnd.nextInt(10));
    }

    void remover() {
        if (corpo != null) corpo.remove();
        if (motor != null) motor.remove();
        corpo = null;
        motor = null;
        alvo = null;
    }

    /** Tira qualquer equipamento que outro plugin (LevelledMobs, EliteMobs) tenha posto no motor invisivel: armadura
     * num zumbi invisivel aparece flutuando no meio do monstro. */
    static void semEquipamento(org.bukkit.entity.LivingEntity z) {
        org.bukkit.inventory.EntityEquipment eq = z.getEquipment();
        if (eq == null) return;
        for (org.bukkit.inventory.EquipmentSlot s : org.bukkit.inventory.EquipmentSlot.values()) {
            try {
                org.bukkit.inventory.ItemStack it = eq.getItem(s);
                if (it != null && !it.getType().isAir()) eq.setItem(s, null);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Todo tick: o corpo acompanha o motor. */
    void sincronizar() {
        if (!vivo()) return;
        if (++checagens % 5 == 0) travarVelocidade(motor);
        if (checagens % 10 == 1) semEquipamento(motor);
        Location l = motor.getLocation();
        if (tipo == Tipo.SOMBRA && congelada) {
            // estatua: nao vira nem treme enquanto alguem olha
            Location c = corpo.getLocation();
            l.setYaw(c.getYaw());
            l.setPitch(0);
            corpo.teleport(l);
            olhos(c.getYaw());
            return;
        }
        float yaw = motor.getBodyYaw();
        Player a = alvo == null ? null : Bukkit.getPlayer(alvo);
        if (ia == Ia.CACANDO && a != null && a.getWorld().equals(mundo) && tick - vistoEm <= 2) {
            Location t = l.clone();
            t.setDirection(a.getLocation().toVector().subtract(l.toVector()));
            yaw = t.getYaw();
        }
        // tique: de vez em quando a cabeca vira de repente (mais quando esta cacando)
        if (rnd.nextInt(ia == Ia.CACANDO ? 14 : 40) == 0) tique = (rnd.nextFloat() * 2 - 1) * (ia == Ia.CACANDO ? 55 : 35);
        else tique *= 0.8f;
        l.setYaw(yaw);
        l.setPitch(0);
        corpo.teleport(l);
        corpo.setRotation(yaw + tique, tipo == Tipo.CAO ? 0 : (ia == Ia.CACANDO ? 12 : 0));
        corpo.setBodyYaw(yaw);
        olhos(yaw + tique);
    }

    /** Olhos vermelhos que brilham no escuro (Bacteria e Sombra). */
    private void olhos(float yaw) {
        if ((tipo != Tipo.BACTERIA && tipo != Tipo.SOMBRA) || checagens % 3 != 0) return;
        Location l = corpo.getLocation();
        double s = escala(), r = Math.toRadians(yaw);
        Vector f = new Vector(-Math.sin(r), 0, Math.cos(r)), lado = new Vector(-f.getZ(), 0, f.getX());
        Location olho = l.clone().add(0, 1.56 * s, 0).add(f.multiply(0.27 * s));
        org.bukkit.Particle.DustOptions verm = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 20, 20), tipo == Tipo.SOMBRA ? 0.7f : 0.55f);
        mundo.spawnParticle(Particle.DUST, olho.clone().add(lado.clone().multiply(0.12 * s)), 1, 0, 0, 0, 0, verm);
        mundo.spawnParticle(Particle.DUST, olho.clone().subtract(lado.clone().multiply(0.12 * s)), 1, 0, 0, 0, 0, verm);
    }

    private void parar() {
        if (motor != null && motor.isValid()) motor.getPathfinder().stopPathfinding();
    }

    private void mover(Location para, double r) {
        if (motor == null || para == null) return;
        motor.getPathfinder().moveTo(para, r);
    }

    private boolean chegou(Location d) {
        if (motor == null || d == null) return true;
        Location l = motor.getLocation();
        double dx = l.getX() - d.getX(), dz = l.getZ() - d.getZ();
        return dx * dx + dz * dz < 2.5 && Math.abs(l.getY() - d.getY()) < 2;
    }

    private double ritmo(String t) {
        return switch (t) {
            case "ronda" -> cfg("ritmo-ronda", 0.75);
            case "investigar" -> cfg("ritmo-investigar", 0.95);
            default -> cfg("ritmo-caca", 1.1);
        };
    }

    // ---------------------------------------------------------------- sentidos

    private Player procurar(List<Player> vivos) {
        if (tipo == Tipo.SOMBRA) {
            // a Sombra nao precisa ver: sente quem esta perto (e vai atras de quem estiver mais perto)
            Player melhor = null;
            double md = cfg("alcance", 40);
            md *= md;
            for (Player p : vivos) {
                if (!jogo.alvoValido(p)) continue;
                double d = p.getLocation().distanceSquared(motor.getLocation());
                if (d < md) {
                    md = d;
                    melhor = p;
                }
            }
            return melhor;
        }
        Location olho = motor.getEyeLocation();
        Vector frente = olho.getDirection().setY(0);
        if (frente.lengthSquared() < 1e-6) frente = new Vector(0, 0, 1);
        frente.normalize();
        Player melhor = null;
        double melhorD = 999;
        for (Player p : vivos) {
            if (!jogo.alvoValido(p)) continue;
            Vector para = p.getEyeLocation().toVector().subtract(olho.toVector());
            double d = para.length();
            double lim = p.isSneaking() ? cfg("visao-agachado", tipo == Tipo.COISA ? 3 : tipo == Tipo.CAO ? 8 : 10)
                    : cfg("visao", tipo == Tipo.COISA ? 6 : tipo == Tipo.CAO ? 16 : 20);
            if (cacando(p)) lim += 6;
            if (d > lim) continue;
            if (d > 2.5) {
                Vector hz = para.clone().setY(0);
                double ang = cacando(p) ? 110 : cfg("angulo", tipo == Tipo.COISA ? 140 : tipo == Tipo.CAO ? 90 : 70);
                if (hz.lengthSquared() > 1e-6 && Math.toDegrees(frente.angle(hz.normalize())) > ang) continue;
            }
            if (!motor.hasLineOfSight(p)) continue;
            if (d < melhorD) {
                melhorD = d;
                melhor = p;
            }
        }
        return melhor;
    }

    /** Um barulho em "l" que se ouve ate "raio" blocos. */
    void ouvir(Location l, double raio, Player quem) {
        if (!vivo() || ia == Ia.PAUSA || !l.getWorld().equals(mundo)) return;
        raio *= cfg("audicao", tipo == Tipo.COISA ? 1.0 : 0.5);
        if (tipo == Tipo.SOMBRA && congelada) return;
        Location v = motor.getLocation();
        if (v.distanceSquared(l) > raio * raio) return;
        if (ia == Ia.CACANDO && tick - vistoEm < seg(2)) return;
        boolean antes = ia == Ia.INVESTIGANDO;
        ia = Ia.INVESTIGANDO;
        alvo = null;
        destino = l.clone();
        destino.setY(Nivel.Y);
        esperaAte = 0;
        parado = 0;
        mover(destino, ritmo("investigar"));
        if (!antes) som(true);
    }

    private void som(boolean forte) {
        Location v = motor.getLocation();
        if (tipo == Tipo.CAO) {
            Sons.localComPausa(mundo, v, forte ? 40 : 24, "cao_rosnado", forte ? 1.2f : 0.8f, 0.9f + rnd.nextFloat() * 0.2f,
                    forte ? Sound.ENTITY_WOLF_BIG_GROWL : Sound.ENTITY_WOLF_GRUMPY_GROWL, forte ? 1.6f : 1.0f, 0.55f, 1800);
            return;
        }
        if (tipo == Tipo.SOMBRA) {
            // quase nao faz barulho: so um sussurro baixo de vez em quando
            Sons.localComPausa(mundo, v, forte ? 24 : 14, "sussurros", forte ? 0.9f : 0.6f, 0.6f + rnd.nextFloat() * 0.15f,
                    Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, 0.6f, 3500);
            return;
        }
        if (tipo == Tipo.FESTEIRO) {
            Sons.localComPausa(mundo, v, forte ? 40 : 24, "festeiro_risada", forte ? 1f : 0.6f, 0.9f + rnd.nextFloat() * 0.25f,
                    Sound.ENTITY_WITCH_CELEBRATE, forte ? 1.2f : 0.7f, 0.6f, 3000);
            return;
        }
        if (tipo == Tipo.BACTERIA) {
            Sons.localComPausa(mundo, v, 48, "bacteria_estalo", forte ? 1f : 0.75f, 0.9f + rnd.nextFloat() * 0.2f,
                    Sound.ENTITY_ENDERMAN_AMBIENT, forte ? 1.6f : 1.0f, 0.5f, 2500);
        } else if (forte) {
            Sons.local(mundo, v, 64, "uivador_uivo", 1f, 0.95f + rnd.nextFloat() * 0.1f, Sound.ENTITY_WARDEN_LISTENING, 2f, 0.8f);
        } else {
            Sons.localComPausa(mundo, v, 32, "uivador_estalo", 0.9f, 0.9f + rnd.nextFloat() * 0.2f, Sound.ENTITY_WARDEN_AMBIENT, 1.3f, 0.8f, 2000);
        }
    }

    private void comecar(Player p) {
        boolean novo = ia != Ia.CACANDO;
        ia = Ia.CACANDO;
        alvo = p.getUniqueId();
        esperaAte = 0;
        parado = 0;
        if (novo && tipo == Tipo.SOMBRA) {
            p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.35f, 0.5f);
            jogo.titulo(p, "", "§8algo se mexeu no escuro... §7não tire a luz de cima dela", 1.6);
            return;
        }
        if (novo) {
            gritar(p, true);
            proximoGrito = tick + seg(3 + rnd.nextDouble() * 2);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 1.6f);
            Visual.gritar(corpo, 24);   // o modelo 3D abre a boca / levanta os bracos
            jogo.titulo(p, "", tipo == Tipo.CAO ? "§4§lO CÃO TE VIU! §fENCARE ELE, NÃO CORRA!"
                    : "§4§l" + tipo.nome.toUpperCase(java.util.Locale.ROOT) + " TE VIU! CORRA!", 1.2);
        }
    }

    /**
     * Grito do monstro. Para quem ele viu, o som toca a poucos blocos do jogador, na direcao do monstro: assim chega
     * alto mesmo quando ele te ve de longe (antes tocava no lugar dele e de 20 blocos quase nao se ouvia). Os outros
     * ouvem do lugar do monstro. forte = o grito de quando ve (com camadas e tremida da tela no celular).
     */
    private void gritar(Player p, boolean forte) {
        if (motor == null || !motor.isValid() || p == null || !p.getWorld().equals(mundo)) return;
        Location m = motor.getLocation().add(0, 1.6, 0);
        Location olho = p.getEyeLocation();
        org.bukkit.util.Vector para = m.toVector().subtract(olho.toVector());
        double dist = para.length();
        // forte: praticamente no ouvido (1,5 bloco, vindo da direcao dele) = o mais alto que o jogo deixa
        double raio = forte ? 1.5 : 3.0;
        Location perto = dist < raio + 0.5 ? m : olho.clone().add(para.multiply(raio / dist));
        String nome;
        Sound reserva, camada;
        float tom, tomR, tomC;
        switch (tipo) {
            case FESTEIRO -> { nome = "festeiro_risada"; reserva = Sound.ENTITY_WITCH_CELEBRATE; camada = Sound.ENTITY_EVOKER_PREPARE_ATTACK; tom = 1.1f; tomR = 0.5f; tomC = 0.6f; }
            case CAO -> { nome = forte ? "cao_ataque" : "cao_rosnado"; reserva = Sound.ENTITY_ZOGLIN_ANGRY; camada = Sound.ENTITY_RAVAGER_ROAR; tom = 1f; tomR = 0.6f; tomC = 0.8f; }
            case COISA -> { nome = "uivador_uivo"; reserva = Sound.ENTITY_WARDEN_ROAR; camada = Sound.ENTITY_RAVAGER_ROAR; tom = 1.1f; tomR = 0.9f; tomC = 0.55f; }
            default -> { nome = forte ? "bacteria_grito" : "bacteria_urro_curto"; reserva = Sound.ENTITY_ENDERMAN_SCREAM; camada = Sound.ENTITY_RAVAGER_ROAR; tom = 1f; tomR = 0.5f; tomC = 0.6f; }
        }
        if (!forte && tipo != Tipo.BACTERIA) tom *= 1.12f;
        float vol = forte ? 2f : 1.4f;
        Sons.para(p, perto, nome, vol, tom, reserva, 2f, forte ? tomR : tomR + 0.1f);
        if (forte) {
            // susto: o mesmo grito dobrado + rugido grave + grito do enderman, tudo junto e colado no jogador
            Sons.para(p, olho, nome, vol, tom * 0.98f, null, 0f, 0f);
            p.playSound(perto, camada, org.bukkit.SoundCategory.MASTER, 2f, tomC);
            p.playSound(olho, camada, org.bukkit.SoundCategory.MASTER, 2f, tomC * 0.9f);
            if (tipo != Tipo.FESTEIRO) {
                p.playSound(perto, Sound.ENTITY_ENDERMAN_SCREAM, org.bukkit.SoundCategory.MASTER, 2f, 0.55f);
                p.playSound(olho, Sound.ENTITY_GHAST_HURT, org.bukkit.SoundCategory.MASTER, 1.5f, 0.5f);
            }
            Tela.tremer(p, 1.3f, 1.0f);
        }
        double r2 = 56 * 56;
        for (Player o : mundo.getPlayers()) {
            if (o == p || o.getLocation().distanceSquared(m) > r2) continue;
            Sons.para(o, m, nome, forte ? 3f : 2f, tom, reserva, 3f, tomR);
        }
        if (forte || rnd.nextBoolean()) Visual.gritar(corpo, forte ? 24 : 12);
    }

    // ---------------------------------------------------------------- ciclo (4x por segundo)

    void tick(long t, List<Player> vivos) {
        tick = t;
        if (!vivo()) {
            Location l = jogo.lugarLonge(nivel, vivos, 30, 50);
            if (l != null) criar(l);
            return;
        }
        Location v = motor.getLocation();
        if (!nivel.dentro(v)) {
            reaparecer(vivos);
            return;
        }
        // longe de todo mundo: aparece de novo perto de alguem (mas fora de vista)
        double perto = 1e9;
        for (Player p : vivos) perto = Math.min(perto, p.getLocation().distanceSquared(v));
        if (!vivos.isEmpty() && perto > 80 * 80 && ia != Ia.CACANDO) {
            reaparecer(vivos);
            return;
        }
        if (ia == Ia.PAUSA) {
            parar();
            if (t >= esperaAte) {
                ia = Ia.RONDA;
                destino = null;
                esperaAte = 0;
                if (sumirDepois) {
                    sumirDepois = false;
                    reaparecer(vivos);
                }
            }
            return;
        }

        // Sombra: com alguem olhando (e ela iluminada) fica parada como estatua
        if (tipo == Tipo.SOMBRA) {
            boolean antes = congelada;
            congelada = observada(vivos);
            if (congelada) {
                parar();
                observadaHa++;
                estatua(vivos, antes);
                return;
            }
            observadaHa = 0;
        }

        // pega quem estiver colado
        for (Player p : vivos) {
            if (!jogo.alvoValido(p)) continue;
            Location l = p.getLocation();
            double dx = l.getX() - v.getX(), dz = l.getZ() - v.getZ();
            if (dx * dx + dz * dz < 1.7 * 1.7 && Math.abs(l.getY() - v.getY()) < 1.8 && motor.hasLineOfSight(p)) {
                parar();
                corpo.swingMainHand();
                jogo.pego(p, tipo.nome, skin());
                ia = Ia.PAUSA;
                esperaAte = t + seg(1.5);
                sumirDepois = true;
                alvo = null;
                return;
            }
        }

        Player visto = tipo == Tipo.CAO && t < desistiuAte ? null : procurar(vivos);
        if (visto != null) {
            if (ia != Ia.CACANDO || !visto.getUniqueId().equals(alvo)) comecar(visto);
            vistoEm = t;
            ultimoLocal = visto.getLocation();
        }

        switch (ia) {
            case CACANDO -> {
                Player a = alvo == null ? null : Bukkit.getPlayer(alvo);
                if (a == null || !jogo.alvoValido(a)) {
                    pararCaca();
                    break;
                }
                if (t - vistoEm > seg(cfg("memoria", 4))) {
                    ia = Ia.INVESTIGANDO;
                    destino = ultimoLocal;
                    alvo = null;
                    parado = 0;
                    mover(destino, ritmo("investigar"));
                    break;
                }
                destino = t - vistoEm <= 1 ? a.getLocation() : ultimoLocal;
                // grita de novo de tempos em tempos enquanto corre atras (a Sombra nao: ela e silenciosa)
                if (tipo != Tipo.SOMBRA && t >= proximoGrito && t - vistoEm <= seg(2)) {
                    gritar(a, false);
                    proximoGrito = t + seg(cfg("grito-a-cada", 4) + rnd.nextDouble() * 3);
                }
                if (tipo == Tipo.CAO && caoEncarado(a, t, vivos)) break;
                double bote = 1.0;
                if (tipo == Tipo.CAO && (a.isSprinting() || !olhando(a))) bote = cfg("investida", 1.25);
                else if (tipo != Tipo.CAO && tipo != Tipo.SOMBRA && t - vistoEm <= 1 && a.getLocation().distanceSquared(v) < 5 * 5)
                    bote = cfg("bote", 1.2); // perto e vendo: da o bote
                mover(destino, ritmo("caca") * bote);
                if (parado >= seg(5)) pararCaca();
            }
            case INVESTIGANDO -> {
                if (chegou(destino) || parado >= seg(4)) {
                    if (esperaAte == 0) {
                        esperaAte = t + seg(2 + rnd.nextDouble() * 2);
                        parar();
                    } else if (t >= esperaAte) {
                        ia = Ia.RONDA;
                        destino = null;
                        esperaAte = 0;
                    }
                } else if (t % 4 == 0) mover(destino, ritmo("investigar"));
            }
            default -> ronda(vivos);
        }

        boolean querAndar = destino != null && !chegou(destino) && esperaAte == 0;
        if (querAndar && ultimaPos != null && v.distanceSquared(ultimaPos) < 0.02) parado++;
        else parado = 0;
        ultimaPos = v.clone();
        if (ia == Ia.RONDA && parado >= seg(4)) {
            destino = null;
            parado = 0;
        }
        if (localPreso == null || v.distanceSquared(localPreso) > 1.0) {
            localPreso = v.clone();
            tempoPreso = 0;
        } else if (querAndar) tempoPreso++;
        if (tempoPreso >= seg(20)) {
            tempoPreso = 0;
            reaparecer(vivos);
            return;
        }

        // sons e particulas
        boolean andando = motor.getVelocity().setY(0).lengthSquared() > 0.0004;
        if (andando && t % (ia == Ia.CACANDO ? 1 : 2) == 0)
            mundo.playSound(v, tipo == Tipo.BACTERIA ? Sound.BLOCK_SCULK_STEP : tipo == Tipo.CAO ? Sound.ENTITY_WOLF_STEP : Sound.ENTITY_WARDEN_STEP,
                    ia == Ia.CACANDO ? 1.3f : 0.9f, tipo == Tipo.CAO ? 0.5f : 0.6f);
        if (t >= proximoSom) {
            proximoSom = t + seg(ia == Ia.CACANDO ? 2.5 : 7 + rnd.nextInt(8));
            som(false);
        }
        if (tipo == Tipo.COISA && t % 4 == 0) mundo.spawnParticle(Particle.SCULK_SOUL, v.clone().add(0, 2.2, 0), 1, 0.2, 0.2, 0.2, 0.01);
    }

    /** O jogador esta olhando para o Cao (ate 20 blocos, com visao livre)? */
    private boolean olhando(Player p) {
        Location olho = p.getEyeLocation();
        Vector para = motor.getLocation().add(0, 0.4, 0).toVector().subtract(olho.toVector());
        double d = para.length();
        if (d > 20 || d < 0.01) return false;
        double ang = Math.toDegrees(olho.getDirection().angle(para.normalize()));
        return ang < Math.max(12, 30 - d) && p.hasLineOfSight(motor);
    }

    /**
     * Sombra: alguem esta com ela na tela (ate ~40 graus do centro da visao, sem parede no meio) E ela esta
     * iluminada (luz do lugar 7+ ou a lanterna de quem olha alcanca ela).
     */
    private boolean observada(List<Player> vivos) {
        Location c = motor.getLocation();
        int luz = c.clone().add(0, 1, 0).getBlock().getLightLevel();
        for (Player p : vivos) {
            if (!jogo.vivo(p)) continue;
            Location olho = p.getEyeLocation();
            Vector para = c.clone().add(0, 1.0, 0).toVector().subtract(olho.toVector());
            double d = para.length();
            if (d > 40 || d < 0.01) continue;
            boolean iluminada = luz >= 7 || jogo.lanternaLigada(p) && d <= cfg("lanterna-alcance", 18);
            if (!iluminada) continue;
            if (Math.toDegrees(olho.getDirection().angle(para.normalize())) > 40) continue;
            if (!p.hasLineOfSight(motor)) continue;
            return true;
        }
        return false;
    }

    /** Sombra parada: perto de quem olha ela "estala"; olhada muito tempo seguido, a lanterna de todo mundo falha. */
    private void estatua(List<Player> vivos, boolean jaEstava) {
        Location c = motor.getLocation();
        if (!jaEstava) mundo.playSound(c, Sound.ENTITY_CREAKING_FREEZE, 1f, 0.6f);
        if (observadaHa % 6 == 0) for (Player p : vivos) {
            double d = p.getLocation().distanceSquared(c);
            if (d < 8 * 8) {
                p.playSound(c, Sound.ENTITY_CREAKING_TWITCH, 0.8f, 0.5f);
                p.sendActionBar(Backrooms.txt("§8...não pisque..."));
            }
        }
        // a cada ~8 s seguidos de olhar, as luzes falham por 1 segundo (e ela se mexe no escuro)
        if (observadaHa >= seg(cfg("piscar-segundos", 8))) {
            observadaHa = 0;
            for (Player p : vivos) if (p.getLocation().distanceSquared(c) < 30 * 30) {
                jogo.falharLanterna(p, seg(cfg("piscar-duracao", 1.2)));
                p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_OFF, 1f, 0.5f);
            }
            jogo.apagarPerto(c, 10, seg(cfg("piscar-duracao", 1.2)) + 2);
        }
    }

    /**
     * Cao: quem caca e encarado (olhando para ele, sem correr) faz ele parar e rosnar. Encarado por
     * "encarar-segundos" seguidos, ele desiste e vai embora por um tempo. Retorna true se ficou parado.
     */
    private boolean caoEncarado(Player a, long t, List<Player> vivos) {
        boolean encarando = olhando(a) && !a.isSprinting();
        if (!encarando) {
            encarado = Math.max(0, encarado - 2);
            return false;
        }
        encarado++;
        parar();
        if (encarado % 4 == 1) {
            mundo.playSound(motor.getLocation(), Sound.ENTITY_WOLF_BIG_GROWL, 1.3f, 0.5f + rnd.nextFloat() * 0.1f);
            a.sendActionBar(Backrooms.txt("§7O Cão rosna... §fcontinue encarando, sem correr."));
        }
        if (encarado >= seg(cfg("encarar-segundos", 4))) {
            encarado = 0;
            desistiuAte = t + seg(cfg("desiste-segundos", 20));
            mundo.playSound(motor.getLocation(), Sound.ENTITY_WOLF_BIG_WHINE, 1.2f, 0.6f);
            a.sendActionBar(Backrooms.txt("§aO Cão desistiu e foi embora."));
            pararCaca();
            reaparecer(vivos);
        }
        return true;
    }

    private void pararCaca() {
        ia = Ia.RONDA;
        alvo = null;
        destino = null;
        parado = 0;
    }

    private void ronda(List<Player> vivos) {
        if (tick >= proximaVisita && !vivos.isEmpty()) {
            // de tempos em tempos vai na direcao de alguem
            proximaVisita = tick + seg(cfg("visita-segundos", 30) + rnd.nextInt(20));
            Player p = vivos.get(rnd.nextInt(vivos.size()));
            Location l = jogo.passoNaDirecao(nivel, motor.getLocation(), p.getLocation());
            if (l != null) {
                destino = l;
                esperaAte = 0;
                mover(destino, ritmo("ronda"));
                return;
            }
        }
        if (destino == null || chegou(destino)) {
            if (esperaAte == 0) {
                esperaAte = tick + seg(1 + rnd.nextDouble() * 3);
                parar();
                return;
            }
            if (tick < esperaAte) return;
            esperaAte = 0;
            Location l = jogo.lugarPerto(nivel, motor.getLocation(), 8, 26);
            if (l == null) return;
            destino = l;
            mover(destino, ritmo("ronda"));
        } else if (tick % 8 == 0) mover(destino, ritmo("ronda"));
    }

    private void reaparecer(List<Player> vivos) {
        Location l = jogo.lugarLonge(nivel, vivos, 45, 72);
        if (l == null) return;
        parar();
        motor.teleport(l);
        sincronizar();
        ia = Ia.RONDA;
        destino = null;
        esperaAte = 0;
        alvo = null;
        parado = 0;
    }

    String status() {
        if (!vivo()) return tipo.chave + "=-";
        Location l = motor.getLocation();
        return String.format("%s=%s%s %.0f %.0f %.0f", tipo.chave, ia, congelada ? "(parada)" : "", l.getX(), l.getY(), l.getZ());
    }
}
