package br.servidor.extras;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Entrada e saida dos jogadores: mensagem de entrar/sair bonita no chat, boas-vindas na tela e no chat para quem
 * entra, e anuncio de VIP com um efeito diferente por nivel (cada nivel ganha mais que o anterior):
 *   VIP   - brilho verde e som de level
 *   VIP+  - espiral azul subindo em volta e sininho para todo mundo
 *   MVP   - fogos dourados + anel de ouro + aviso na barra de todo mundo
 *   LENDA - raio (sem dano), fogos roxos com rastro, coroa de particulas e titulo na tela de todo mundo
 * Tudo acontece DEPOIS do /login (senao o jogador nem veria, ele esta na ilha do login), no lugar onde ele
 * aparece. Funciona no Java e no Bedrock (particulas, fogos, raio, titulo e sons passam pelo Geyser).
 */
final class Entradas implements Listener {
    private final Extras pl;
    /** Quem entrou e ainda esta esperando logar (sai do mapa quando o efeito roda ou ele sai). */
    private final Map<UUID, Long> esperando = new ConcurrentHashMap<>();
    private final NamespacedKey kFogo;
    private BukkitTask tarefa;

    Entradas(Extras pl) {
        this.pl = pl;
        this.kFogo = new NamespacedKey(pl, "fogo_entrada");
    }

    void iniciar() {
        if (!ligado()) return;
        tarefa = Bukkit.getScheduler().runTaskTimer(pl, this::conferir, 20L, 10L);
        pl.getLogger().info("Entradas ligadas: boas-vindas na tela e efeito por nivel de VIP.");
    }

    void parar() {
        if (tarefa != null) tarefa.cancel();
        tarefa = null;
        esperando.clear();
    }

    private boolean ligado() {
        return pl.getConfig().getBoolean("entradas.ligado", true);
    }

    // ------------------------------------------------------------------ mensagens do chat

    @EventHandler(priority = EventPriority.HIGH)
    public void entrou(PlayerJoinEvent e) {
        if (!ligado()) return;
        Player p = e.getPlayer();
        int n = pl.vip == null ? 0 : pl.vip.nivel(p);
        boolean primeira = !p.hasPlayedBefore();
        if (primeira) e.joinMessage(Extras.txt("§6§l★ §e" + p.getName() + " §fentrou pela §eprimeira vez§f! §7Deem as boas-vindas!"));
        else if (n == 0) e.joinMessage(Extras.txt("§8[§a+§8] §7" + p.getName() + " §8entrou"));
        else e.joinMessage(null);   // o anuncio do VIP sai depois do login, com efeito
        esperando.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void saiu(PlayerQuitEvent e) {
        esperando.remove(e.getPlayer().getUniqueId());
        if (!ligado()) return;
        Player p = e.getPlayer();
        int n = pl.vip == null ? 0 : pl.vip.nivel(p);
        String tag = n > 0 ? Vip.NIVEIS[n].tag() + " " : "";
        e.quitMessage(Extras.txt("§8[§c-§8] " + tag + "§7" + p.getName() + " §8saiu"));
    }

    /** Quem estava perto do raio da LENDA nos ultimos segundos (para nao ganhar a conquista do raio por isso). */
    private final Map<UUID, Long> semSurto = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void conquistaDoRaio(org.bukkit.event.player.PlayerAdvancementDoneEvent e) {
        Long t = semSurto.get(e.getPlayer().getUniqueId());
        if (t == null) return;
        if (System.currentTimeMillis() - t > 5000) {
            semSurto.remove(e.getPlayer().getUniqueId());
            return;
        }
        org.bukkit.advancement.Advancement adv = e.getAdvancement();
        if (!adv.getKey().getKey().equals("adventure/lightning_rod_with_villager_no_fire")) return;
        e.message(null);
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> {
            org.bukkit.advancement.AdvancementProgress pr = p.getAdvancementProgress(adv);
            for (String c : pr.getAwardedCriteria()) pr.revokeCriteria(c);
        });
    }

    /** Fogos de entrada nao machucam ninguem. */
    @EventHandler
    public void fogoSemDano(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework f && f.getPersistentDataContainer().has(kFogo, PersistentDataType.BYTE)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ depois do login

    private void conferir() {
        if (esperando.isEmpty()) return;
        long agora = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : esperando.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || agora - en.getValue() > 600_000L) {
                esperando.remove(en.getKey());
                continue;
            }
            if (!Extras.logado(p)) continue;
            esperando.remove(en.getKey());
            // 2 s depois de logar: o LoginLobby ja devolveu o jogador para o lugar dele e o aviso de login ja sumiu
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!p.isOnline()) return;
                try {
                    chegou(p);
                } catch (Throwable t) {
                    pl.getLogger().warning("Efeito de entrada de " + p.getName() + " falhou: " + t);
                }
            }, 40L);
        }
    }

    private void chegou(Player p) {
        int n = pl.vip == null ? 0 : pl.vip.nivel(p);
        boasVindas(p, n);
        if (n <= 0) return;
        Vip.Nivel nv = Vip.NIVEIS[n];
        Location l = p.getLocation();
        switch (n) {
            case 1 -> {
                Bukkit.broadcast(Extras.txt("§a§l✦ " + nv.tag() + " §f" + p.getName() + " §7entrou!"));
                brilhoVerde(p, l);
            }
            case 2 -> {
                Bukkit.broadcast(Extras.txt("§b§l✦ " + nv.tag() + " §f" + p.getName() + " §7chegou!"));
                somParaTodos(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
                espiralAzul(p, l);
            }
            case 3 -> {
                Bukkit.broadcast(Extras.txt("§6§l✦✦ " + nv.tag() + " §f" + p.getName() + " §7entrou no servidor! §6§l✦✦"));
                for (Player o : Bukkit.getOnlinePlayers()) o.sendActionBar(Extras.txt("§6§l✦ " + nv.tag() + " §f" + p.getName() + " §7entrou"));
                somParaTodos(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
                fogosDourados(p, l);
            }
            default -> {
                Bukkit.broadcast(Extras.txt("§d§l━━━━━━━━━━━━━━━━━━━━━━━━"));
                Bukkit.broadcast(Extras.txt("§d§l  ★ " + nv.tag() + " §f" + p.getName() + " §dchegou! §d§l★"));
                Bukkit.broadcast(Extras.txt("§d§l━━━━━━━━━━━━━━━━━━━━━━━━"));
                Title t = Title.title(Extras.txt("§d§l★ LENDA ★"), Extras.txt("§f" + p.getName() + " §dchegou!"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(600)));
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (o.getUniqueId().equals(p.getUniqueId())) continue;   // a boas-vindas dele ja esta na tela
                    o.showTitle(t);
                    o.playSound(o.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.4f);
                }
                raioLenda(p, l);
            }
        }
    }

    // ------------------------------------------------------------------ boas-vindas (todo mundo)

    private void boasVindas(Player p, int n) {
        boolean novato = !p.hasPlayedBefore() || p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) < 20 * 60 * 10;
        String nome = p.getName();
        String sub = novato ? "§fBem-vindo, §e" + nome + "§f!" : "§fBom te ver de novo, §e" + nome + "§f!";
        p.showTitle(Title.title(Extras.txt("§6§lCORONA SERVER"), Extras.txt(sub),
                Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
        int online = Bukkit.getOnlinePlayers().size();
        boolean cel = Extras.ehBedrock(p);
        // no celular o chat e estreito: linhas curtas
        p.sendMessage(Extras.txt("§6§l━━━━━━ §e§lCORONA SERVER §6§l━━━━━━"));
        p.sendMessage(Extras.txt((novato ? "§fBem-vindo, §e" : "§fBom te ver de novo, §e") + nome + "§f! " + (online <= 1 ? "§7Você é o primeiro por aqui agora." : "§7Tem §f" + online + " §7jogadores online.")));
        if (novato) {
            p.sendMessage(Extras.txt("§7Masmorras, poderes, pets e minigames."));
            p.sendMessage(Extras.txt("§7Comece pelos itens da barra" + (cel ? "" : " §8(botão direito)") + "§7."));
        } else {
            p.sendMessage(Extras.txt("§7Masmorras: §e/masmorras §8• §7Minigames: §e/minigames"));
        }
        p.sendMessage(Extras.txt("§7Vote todo dia e ganhe prêmios: §e/vote"));
        if (n == 0) p.sendMessage(Extras.txt("§7Quer apoiar o servidor? §e/vip"));
        else p.sendMessage(Extras.txt("§7Seu kit do dia: §e/vip kit"));
        p.sendMessage(Extras.txt("§6§l━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    // ------------------------------------------------------------------ efeitos por nivel

    private void brilhoVerde(Player p, Location l) {
        World w = l.getWorld();
        p.playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        w.spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0, 1, 0), 40, 0.6, 0.9, 0.6, 0.0);
        w.spawnParticle(Particle.COMPOSTER, l.clone().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.0);
    }

    private void espiralAzul(Player p, Location base) {
        p.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        new Animacao(60) {
            @Override
            void passo(int t) {
                Location l = p.getLocation();
                World w = l.getWorld();
                for (int k = 0; k < 2; k++) {
                    double ang = (t * 0.35) + k * Math.PI;
                    double r = 1.1 - t * 0.008;
                    double y = (t / 60.0) * 2.6;
                    w.spawnParticle(Particle.END_ROD, l.clone().add(Math.cos(ang) * r, y, Math.sin(ang) * r), 1, 0, 0, 0, 0.0);
                    w.spawnParticle(Particle.DUST, l.clone().add(Math.cos(ang) * r, y, Math.sin(ang) * r), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.1f));
                }
                if (t == 59) {
                    w.spawnParticle(Particle.FIREWORK, l.clone().add(0, 2.6, 0), 30, 0.3, 0.3, 0.3, 0.08);
                    w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.4f);
                }
            }
        }.rodar();
    }

    private void fogosDourados(Player p, Location base) {
        p.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        new Animacao(70) {
            @Override
            void passo(int t) {
                Location l = p.getLocation();
                World w = l.getWorld();
                // anel de ouro girando no chao
                for (int k = 0; k < 3; k++) {
                    double ang = t * 0.3 + k * (Math.PI * 2 / 3);
                    w.spawnParticle(Particle.DUST, l.clone().add(Math.cos(ang) * 1.3, 0.15, Math.sin(ang) * 1.3), 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.3f));
                }
                if (t % 10 == 0) w.spawnParticle(Particle.WAX_ON, l.clone().add(0, 1.2, 0), 8, 0.4, 0.6, 0.4, 0.6);
                if (t == 5 || t == 25 || t == 45) {
                    fogo(l.clone().add((t - 25) / 25.0 * 1.5, 1.0, 0), FireworkEffect.Type.BALL_LARGE, Color.fromRGB(255, 200, 40), Color.YELLOW, Color.WHITE, false);
                }
            }
        }.rodar();
    }

    private void raioLenda(Player p, Location base) {
        World w = base.getWorld();
        // o raio (so visual) conta para a conquista "Protetor contra surtos" de quem esta perto: segura essa conquista
        for (Player o : w.getPlayers()) if (o.getLocation().distanceSquared(base) < 260 * 260) semSurto.put(o.getUniqueId(), System.currentTimeMillis());
        w.strikeLightningEffect(base);
        w.spawnParticle(Particle.FLASH, base.clone().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(230, 150, 255));
        p.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        new Animacao(100) {
            @Override
            void passo(int t) {
                Location l = p.getLocation();
                World w2 = l.getWorld();
                // coroa de particulas roxas girando na altura da cabeca
                for (int k = 0; k < 4; k++) {
                    double ang = t * 0.25 + k * (Math.PI / 2);
                    double r = 0.9;
                    Location c = l.clone().add(Math.cos(ang) * r, 2.2 + Math.sin(t * 0.3) * 0.15, Math.sin(ang) * r);
                    w2.spawnParticle(Particle.DUST, c, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(230, 90, 255), 1.4f));
                    if (k % 2 == 0) w2.spawnParticle(Particle.WITCH, c, 1, 0, 0, 0, 0.0);
                }
                // espiral dupla subindo, cor roxa e branca
                double ang = t * 0.4;
                double y = (t % 50) / 50.0 * 2.5;
                w2.spawnParticle(Particle.END_ROD, l.clone().add(Math.cos(ang) * 1.3, y, Math.sin(ang) * 1.3), 1, 0, 0, 0, 0.0);
                w2.spawnParticle(Particle.DUST, l.clone().add(-Math.cos(ang) * 1.3, y, -Math.sin(ang) * 1.3), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(190, 60, 255), 1.2f));
                if (t == 10 || t == 30 || t == 50 || t == 70 || t == 90) {
                    double dx = Math.cos(t * 0.9) * 2, dz = Math.sin(t * 0.9) * 2;
                    fogo(l.clone().add(dx, 1.5, dz), t == 50 ? FireworkEffect.Type.STAR : FireworkEffect.Type.BALL_LARGE,
                            Color.fromRGB(200, 60, 255), Color.FUCHSIA, Color.WHITE, true);
                }
            }
        }.rodar();
    }

    private void fogo(Location l, FireworkEffect.Type tipo, Color cor1, Color cor2, Color fade, boolean rastro) {
        Firework fw = l.getWorld().spawn(l, Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(tipo).withColor(cor1, cor2).withFade(fade).trail(rastro).flicker(rastro).build());
            m.setPower(0);
            f.setFireworkMeta(m);
            f.getPersistentDataContainer().set(kFogo, PersistentDataType.BYTE, (byte) 1);
        });
        fw.setPersistent(false);
        // estoura no proximo tick, onde foi criado (nao sobe para fora do campo de visao)
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (fw.isValid()) fw.detonate();
        }, 2L);
    }

    private void somParaTodos(Sound s, float vol, float tom) {
        for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), s, vol, tom);
    }

    /** Animacao de particulas que roda 1 passo por tick e para sozinha. */
    private abstract class Animacao implements Runnable {
        private final int passos;
        private int t;
        private BukkitTask tr;

        Animacao(int passos) {
            this.passos = passos;
        }

        abstract void passo(int t);

        void rodar() {
            tr = Bukkit.getScheduler().runTaskTimer(pl, this, 0L, 1L);
        }

        @Override
        public void run() {
            try {
                passo(t);
            } catch (Throwable ignored) {
            }
            if (++t >= passos && tr != null) tr.cancel();
        }
    }
}
