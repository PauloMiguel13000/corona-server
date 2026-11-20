package br.servidor.masmorras;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 18/11 (Paulo): os chefes das masmorras 1-10 com corpo 3D proprio (desenho original), em pecas articuladas que se
 * mexem de verdade: respiram parados, andam com o passo casado com a velocidade (joelho e cotovelo dobram, a capa
 * vai para tras com o vento), olham para o alvo, armam o golpe e batem, puxam o arco, lancam magia, levam tranco
 * quando apanham e caem quando morrem. Herobrine nao muda.
 *
 * Pecas: ItemDisplay com item corona:masmorras/<modelo>_<peca> (gerados por ferramentas/modelos/chefes/partes.py,
 * junto com o res/corpos.json que diz os pivos e quem e pai de quem). O pacote e SO das masmorras: no Java entra ao
 * chegar num mundo "masmorras*" e sai ao voltar; no Bedrock vai pelo CoronaPack (pacote separado + Geyser).
 * O mob de verdade fica invisivel (a caixa de acerto continua: espada, flecha e toque no celular funcionam igual) e
 * quem tem o pacote ve o corpo 3D no lugar dele.
 */
final class CorposChefes implements Listener {

    static final String URL_PADRAO = "https://corona-server.duckdns.org/pacotes/corona_masmorras3d_java-2ad7be2bca.zip";
    static final String SHA1_PADRAO = "2ad7be2bca5e1aaebdd9472016d19e198f9809d6";
    private static final UUID ID = UUID.nameUUIDFromBytes("corona-masmorras-3d".getBytes(StandardCharsets.UTF_8));
    private static final int PASSO = 2;   // ticks entre uma pose e outra (o cliente desliza entre elas)

    record PecaDef(String nome, float px, float py, float pz, int pai) {}

    record CorpoDef(String modelo, String tipo, float esc, PecaDef[] pecas, Map<String, Integer> indice) {
        int i(String n) {
            Integer k = indice.get(n);
            return k == null ? -1 : k;
        }
    }

    private final Masmorras pl;
    private final Map<String, CorpoDef> porMasmorra = new HashMap<>();
    private final Map<UUID, Corpo> corpos = new HashMap<>();
    private final Set<UUID> comPacote = ConcurrentHashMap.newKeySet();
    private int ticks;

    CorposChefes(Masmorras pl) {
        this.pl = pl;
        try (var in = pl.getResource("corpos.json")) {
            if (in == null) throw new IllegalStateException("corpos.json nao esta no jar");
            JsonObject raiz = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject cs = raiz.getAsJsonObject("corpos");
            Map<String, CorpoDef> porModelo = new HashMap<>();
            for (var en : cs.entrySet()) {
                JsonObject o = en.getValue().getAsJsonObject();
                JsonArray ps = o.getAsJsonArray("pecas");
                PecaDef[] pecas = new PecaDef[ps.size()];
                Map<String, Integer> idx = new HashMap<>();
                for (int i = 0; i < ps.size(); i++) idx.put(ps.get(i).getAsJsonObject().get("nome").getAsString(), i);
                for (int i = 0; i < ps.size(); i++) {
                    JsonObject p = ps.get(i).getAsJsonObject();
                    JsonArray v = p.getAsJsonArray("pivo");
                    JsonElement pai = p.get("pai");
                    pecas[i] = new PecaDef(p.get("nome").getAsString(), v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat(),
                            pai == null || pai.isJsonNull() ? -1 : idx.get(pai.getAsString()));
                }
                porModelo.put(en.getKey(), new CorpoDef(en.getKey(), o.get("tipo").getAsString(), o.get("esc").getAsFloat(), pecas, idx));
            }
            for (var en : raiz.getAsJsonObject("masmorras").entrySet()) {
                CorpoDef d = porModelo.get(en.getValue().getAsString());
                if (d != null) porMasmorra.put(en.getKey(), d);
            }
        } catch (Exception ex) {
            pl.getLogger().warning("Corpos 3D dos chefes: nao consegui ler o corpos.json (" + ex + "). Os chefes ficam normais.");
        }
        Bukkit.getScheduler().runTaskTimer(pl, this::tique, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(pl, this::visibilidade, 10L, 10L);
        pl.getLogger().info("Corpos 3D dos chefes: " + porMasmorra.size() + " chefes com corpo proprio.");
    }

    boolean ligado() {
        return pl.getConfig().getBoolean("chefes3d.ligado", true) && !porMasmorra.isEmpty();
    }

    boolean temCorpo(Tipo t) {
        return ligado() && porMasmorra.containsKey(t.id);
    }

    // ------------------------------------------------------------------ pacote so das masmorras

    static boolean masmorra(World w) {
        // 18/11: o mundo de Herobrine (luta da ???) conta como masmorra: sem isso o pacote saia e voltava (tela de carregar)
        return w != null && (w.getName().toLowerCase(java.util.Locale.ROOT).startsWith("masmorras") || w.getName().equals(InfernoHerobrine.MUNDO));
    }

    static boolean bedrock(Player p) {
        if (p.getName().startsWith(".") || p.getUniqueId().getMostSignificantBits() == 0L) return true;
        try {
            return Bukkit.getPluginManager().getPlugin("floodgate") != null
                    && org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ve os corpos 3D? Bedrock sempre (o pacote dele entra junto com os outros); Java depois de carregar o pacote. */
    boolean ve(Player p) {
        return ligado() && (bedrock(p) || comPacote.contains(p.getUniqueId()));
    }

    private void mandarPacote(Player p) {
        if (!p.isOnline() || bedrock(p) || !ligado() || !masmorra(p.getWorld()) || comPacote.contains(p.getUniqueId())) return;
        String sha = pl.getConfig().getString("chefes3d.pacote-sha1", SHA1_PADRAO);
        if (sha.length() != 40) return;
        try {
            p.addResourcePack(ID, pl.getConfig().getString("chefes3d.pacote-url", URL_PADRAO), HexFormat.of().parseHex(sha),
                    "§6§lMasmorras§r\n§fChefes 3D das masmorras (só aqui dentro)", false);
        } catch (Throwable t) {
            pl.getLogger().warning("Pacote 3D das masmorras para " + p.getName() + ": " + t);
        }
    }

    private void tirarPacote(Player p) {
        if (bedrock(p) || !comPacote.remove(p.getUniqueId())) return;
        try {
            p.removeResourcePacks(ID);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void entrou(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> mandarPacote(p), 20L * 20);
    }

    @EventHandler
    public void trocouMundo(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (masmorra(p.getWorld())) Bukkit.getScheduler().runTaskLater(pl, () -> mandarPacote(p), 10L);
        else if (!masmorra(p.getWorld())) tirarPacote(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void status(PlayerResourcePackStatusEvent e) {
        if (!ID.equals(e.getID())) return;
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                comPacote.add(p.getUniqueId());
                if (!masmorra(p.getWorld())) tirarPacote(p);   // saiu antes de terminar de baixar
            }
            case DECLINED -> {
                comPacote.remove(p.getUniqueId());
                p.sendMessage(Masmorras.txt("§7Sem o pacote das masmorras você vê os chefes no visual simples."));
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> comPacote.remove(p.getUniqueId());
            default -> { }
        }
    }

    @EventHandler
    public void saiu(PlayerQuitEvent e) {
        comPacote.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ vestir o chefe

    /** Chamado quando o chefe nasce. Devolve true se ele ganhou corpo 3D (entao nao usa a coroa flutuante). */
    boolean vestir(LivingEntity e, Tipo t) {
        if (!temCorpo(t)) return false;
        CorpoDef d = porMasmorra.get(t.id);
        // a armadura do mob nao aparece mais (o corpo 3D veste a dele): passa a protecao para o atributo
        EntityEquipment eq = e.getEquipment();
        if (eq != null) {
            AttributeInstance arm = e.getAttribute(Attribute.ARMOR), tou = e.getAttribute(Attribute.ARMOR_TOUGHNESS);
            double a0 = arm == null ? 0 : arm.getValue(), t0 = tou == null ? 0 : tou.getValue();
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) eq.setItem(s, null);
            if (arm != null) arm.setBaseValue(arm.getBaseValue() + Math.max(0, a0 - arm.getValue()));
            if (tou != null) tou.setBaseValue(tou.getBaseValue() + Math.max(0, t0 - tou.getValue()));
            // a arma continua na mao (dano e arco funcionam), so que invisivel: o modelo 3D segura a dele
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                ItemStack it = eq.getItem(s);
                if (it == null || it.getType().isAir()) continue;
                vazio(it);
                eq.setItem(s, it);
            }
        }
        // o mob de verdade fica invisivel para todos (continua com a caixa de acerto: espada, flecha e toque no celular
        // funcionam igual); quem tem o pacote ve o corpo 3D no lugar dele
        e.setInvisible(true);
        Corpo c = new Corpo(e, d);
        c.nascer();
        corpos.put(e.getUniqueId(), c);
        return true;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void vazio(ItemStack it) {
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "masmorras/vazio"));
    }

    /** Animacao de puxar e soltar o arco (rajada de flechas do chefe arqueiro). */
    void arco(LivingEntity e) {
        Corpo c = corpos.get(e.getUniqueId());
        if (c != null) {
            c.soltou = 6;
            c.acao(Acao.NADA, 1);
        }
    }

    /** Animacao de magia (habilidade especial do chefe). */
    void magia(LivingEntity e) {
        Corpo c = corpos.get(e.getUniqueId());
        if (c != null) c.acao(Acao.MAGIA, 26);
    }

    void parar() {
        for (Corpo c : corpos.values()) c.sumir();
        corpos.clear();
        for (Player p : Bukkit.getOnlinePlayers()) tirarPacote(p);
    }

    // ------------------------------------------------------------------ tique e visibilidade

    private void tique() {
        ticks++;
        if (!andandoTeste.isEmpty()) andarTeste();
        Iterator<Corpo> it = corpos.values().iterator();
        while (it.hasNext()) {
            Corpo c = it.next();
            if (!c.tick(ticks)) {
                c.sumir();
                it.remove();
            }
        }
    }

    private void visibilidade() {
        for (Corpo c : corpos.values()) {
            World w = (c.morto && c.lugarMorte != null ? c.lugarMorte : c.mob.getLocation()).getWorld();
            for (Player p : w.getPlayers()) c.ver(p, ve(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bateu(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        Corpo c = corpos.get(d.getUniqueId());
        if (c != null && c.acao != Acao.GOLPE) c.acao(Acao.GOLPE_RAPIDO, 12);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void apanhou(EntityDamageEvent e) {
        Corpo c = corpos.get(e.getEntity().getUniqueId());
        if (c != null) c.dano = Math.min(1.4f, c.dano + 0.6f + (float) Math.min(0.6, e.getFinalDamage() / 20.0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void atirou(EntityShootBowEvent e) {
        Corpo c = corpos.get(e.getEntity().getUniqueId());
        if (c != null) c.soltou = 6;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void lancou(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof LivingEntity le)) return;
        if (e.getEntity() instanceof org.bukkit.entity.AbstractArrow && !(e.getEntity() instanceof org.bukkit.entity.Trident)) return;
        Corpo c = corpos.get(le.getUniqueId());
        if (c != null && c.acao == Acao.NADA) c.acao(Acao.LANCA, 16);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void feitico(org.bukkit.event.entity.EntitySpellCastEvent e) {
        Corpo c = corpos.get(e.getEntity().getUniqueId());
        if (c != null) c.acao(Acao.MAGIA, 26);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morreu(EntityDeathEvent e) {
        Corpo c = corpos.get(e.getEntity().getUniqueId());
        if (c != null) c.morrer(ticks);
    }

    // ------------------------------------------------------------------ o corpo

    enum Acao { NADA, GOLPE, GOLPE_RAPIDO, LANCA, MAGIA }

    private static float rad(double g) {
        return (float) Math.toRadians(g);
    }

    /** suave 0..1 (ease in-out) */
    private static float ss(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** curva do golpe: arma (0..a), bate (a..b, rapido), volta (b..1). Devolve {armar, bater} 0..1. */
    private static float[] curva(float t, float a, float b) {
        if (t < a) return new float[] {ss(t / a), 0};
        if (t < b) {
            float k = (t - a) / (b - a);
            return new float[] {1 - k, k * k * (3 - 2 * k)};
        }
        float k = ss((t - b) / (1 - b));
        return new float[] {0, 1 - k};
    }

    private static float wrap(float g) {
        g %= 360;
        if (g > 180) g -= 360;
        if (g < -180) g += 360;
        return g;
    }

    final class Corpo {
        final LivingEntity mob;
        final CorpoDef d;
        final ItemDisplay[] pecas;
        float esc;
        Location ultimo, lugarMorte;
        double fase, andado;
        float vel, olharY, olharP, capa, capaRoll, saia, dano;
        Acao acao = Acao.NADA;
        int acaoDesde, acaoDura, soltou, tk, morteDesde = -1;
        boolean morto;
        private final java.util.Random rnd = new java.util.Random();
        private final Quaternionf[] q;
        private final Vector3f[] off;
        private final Vector3f[] sc;

        Corpo(LivingEntity mob, CorpoDef d) {
            this.mob = mob;
            this.d = d;
            this.pecas = new ItemDisplay[d.pecas().length];
            this.q = new Quaternionf[pecas.length];
            this.off = new Vector3f[pecas.length];
            this.sc = new Vector3f[pecas.length];
            AttributeInstance s = mob.getAttribute(Attribute.SCALE);
            this.esc = d.esc() * (float) (s == null ? 1.0 : s.getValue());
            // slime (Rainha da Gosma): o tamanho vem da caixa (slime grande), nao do atributo de escala
            if (mob instanceof org.bukkit.entity.Slime) this.esc = d.esc() * (float) Math.max(1.0, mob.getBoundingBox().getHeight() / 2.2);
        }

        Vector3f lugar(PecaDef p) {
            return new Vector3f(-(p.px() - 8f) / 16f * esc, (p.py() + 16f) / 16f * esc, -(p.pz() - 8f) / 16f * esc);
        }

        @SuppressWarnings("UnstableApiUsage")
        void nascer() {
            sumir();
            Location l = base();
            for (int i = 0; i < pecas.length; i++) {
                PecaDef p = d.pecas()[i];
                ItemStack it = new ItemStack(Material.LEATHER_HELMET);
                it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "masmorras/" + d.modelo() + "_" + p.nome()));
                pecas[i] = l.getWorld().spawn(l, ItemDisplay.class, x -> {
                    x.setItemStack(it);
                    x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    x.setBillboard(Display.Billboard.FIXED);
                    x.setTransformation(new Transformation(lugar(p), new Quaternionf(), new Vector3f(esc, esc, esc), new Quaternionf()));
                    x.setBrightness(new Display.Brightness(13, 15));
                    x.setViewRange(2.0f);
                    x.setShadowRadius(0f);
                    x.setTeleportDuration(1);
                    x.setInterpolationDuration(PASSO);
                    x.setPersistent(false);
                    x.setVisibleByDefault(false);
                });
            }
            ultimo = l.clone();
        }

        Location base() {
            Location l = (morto && lugarMorte != null ? lugarMorte : mob.getLocation()).clone();
            if (!morto) l.setYaw(mob.getBodyYaw());
            l.setPitch(0);
            return l;
        }

        void sumir() {
            for (int i = 0; i < pecas.length; i++) {
                if (pecas[i] != null && pecas[i].isValid()) pecas[i].remove();
                pecas[i] = null;
            }
        }

        boolean existe() {
            for (ItemDisplay x : pecas) if (x == null || !x.isValid()) return false;
            return true;
        }

        void ver(Player p, boolean sim) {
            for (ItemDisplay x : pecas) {
                if (x == null || !x.isValid() || sim == p.canSee(x)) continue;
                if (sim) p.showEntity(pl, x);
                else p.hideEntity(pl, x);
            }
        }

        void acao(Acao a, int dura) {
            if (morto) return;
            acao = a;
            acaoDesde = tk;
            acaoDura = dura;
        }

        void morrer(int agora) {
            if (morto) return;
            morto = true;
            lugarMorte = mob.getLocation().clone();
            lugarMorte.setYaw(mob.getBodyYaw());
            morteDesde = tk;
        }

        /** Devolve false quando o corpo deve sumir. */
        boolean tick(int agora) {
            tk++;
            if (!morto && (!mob.isValid() || mob.isDead())) {
                if (mob.isDead()) morrer(agora);
                else return false;   // tirado da masmorra (sem morrer): some junto
            }
            if (morto && tk - morteDesde > 60) return false;
            Location l = base();
            if (!existe() || pecas[0].getWorld() != l.getWorld()) {
                if (morto) return false;
                nascer();
            }
            for (ItemDisplay x : pecas) x.teleport(l);
            if (tk % PASSO != 0) return true;
            // velocidade no chao (blocos por tique) e passo casado com a distancia andada
            double dx = ultimo == null || ultimo.getWorld() != l.getWorld() ? 0 : Math.hypot(l.getX() - ultimo.getX(), l.getZ() - ultimo.getZ());
            ultimo = l.clone();
            float v = (float) Math.min(1.4, dx / PASSO / 0.13);
            vel += (v - vel) * 0.35f;
            fase += dx * (d.tipo().equals("aranha") ? 5.2 : 3.1) / Math.max(0.6, esc);
            // olhar: para o alvo (cabeca vira ate 60 graus, depois o corpo acompanha)
            float qy = 0, qp = 0;
            if (!morto && mob instanceof Mob m && m.getTarget() != null && m.getTarget().getWorld() == l.getWorld()) {
                Location t = m.getTarget().getEyeLocation();
                Location o = mob.getEyeLocation();
                double ax = t.getX() - o.getX(), az = t.getZ() - o.getZ(), ay = t.getY() - o.getY();
                float yawAlvo = (float) Math.toDegrees(Math.atan2(-ax, az));
                // yaw do Minecraft gira no sentido do relogio; no espaco da entidade a cabeca gira ao contrario
                qy = -Math.max(-60, Math.min(60, wrap(yawAlvo - l.getYaw())));
                qp = (float) Math.max(-35, Math.min(35, Math.toDegrees(Math.atan2(ay, Math.hypot(ax, az)))));
                // golpe previsto: chegou perto do alvo, arma antes de bater
                if (acao == Acao.NADA && o.distanceSquared(t) < Math.pow(2.6 + mob.getWidth(), 2) && rnd.nextInt(4) == 0) acao(Acao.GOLPE, 20);
            } else if (!morto) {
                qy = 25f * (float) Math.sin(tk * 0.021) * (float) Math.sin(tk * 0.007 + 1);
            }
            olharY += (qy - olharY) * 0.3f;
            olharP += (qp - olharP) * 0.3f;
            if (acao != Acao.NADA && tk - acaoDesde >= acaoDura) acao = Acao.NADA;
            dano *= 0.72f;
            if (soltou > 0) soltou -= PASSO;
            for (int i = 0; i < pecas.length; i++) {
                q[i] = new Quaternionf();
                off[i] = new Vector3f();
                sc[i] = new Vector3f(1f, 1f, 1f);
            }
            Quaternionf global = new Quaternionf();
            Vector3f globalOff = new Vector3f();
            switch (d.tipo()) {
                case "aranha" -> poseAranha(global, globalOff);
                case "quadrupede" -> poseQuadrupede(global, globalOff);
                case "gosma" -> poseGosma(global, globalOff);
                default -> poseHumano(global, globalOff);
            }
            aplicar(global, globalOff);
            return true;
        }

        private void set(String n, float pitch, float yaw, float roll) {
            int i = d.i(n);
            if (i < 0) return;
            // yaw (Y) * roll (Z) * pitch (X, positivo = membro pendurado vai para a frente / cabeca olha para cima)
            q[i] = new Quaternionf().rotateY(rad(yaw)).mul(new Quaternionf().rotateZ(rad(roll))).mul(new Quaternionf().rotateX(rad(-pitch)));
        }

        private void mover(String n, float x, float y, float z) {
            int i = d.i(n);
            if (i >= 0) off[i].add(x / 16f * esc, y / 16f * esc, z / 16f * esc);   // em pixels do modelo
        }

        private void escala(String n, float s) {
            int i = d.i(n);
            if (i >= 0) sc[i].set(s, s, s);
        }

        /** Lado: +1 direita do chefe (peca _d), -1 esquerda. roll positivo = para fora. */
        private float fora(int lado, float g) {
            return lado * g;
        }

        // ----------------------------------------------------------- humanoides (e os que flutuam)
        private void poseHumano(Quaternionf global, Vector3f gOff) {
            boolean flutua = d.i("fogo") >= 0;   // Senhor das Chamas: sem pernas, flutua na coluna de fogo
            float s = (float) Math.sin(fase), c = (float) Math.cos(fase), v = vel;
            float resp = (float) Math.sin(tk * 0.075), resp2 = (float) Math.sin(tk * 0.075 - 0.9);
            float t = tk - acaoDesde, k = acaoDura <= 0 ? 1 : t / acaoDura;

            float tP = 3f * v + 1.4f * resp, tY = -7f * s * v, tR = 2.5f * s * v;   // tronco
            float cP = olharP * 0.75f - tP * 0.6f, cY = olharY * 0.85f - tY * 0.5f, cR = -tR * 0.5f;   // cabeca
            float mand = 1.5f + 1.5f * Math.max(0, resp);
            float[] bP = {-24f * s * v + 3f * resp2, 24f * s * v - 3f * resp2};   // braco d, e
            float[] bR = {5f + 1.5f * resp, 5f + 1.5f * resp};
            float[] bY = {0, 0};
            float[] aP = {14f + 14f * v * Math.max(0, s), 14f + 14f * v * Math.max(0, -s)};   // antebraco (dobra o cotovelo)
            float[] pP = {30f * s * v, -30f * s * v};   // coxa
            float[] jP = {-46f * v * Math.max(0, c), -46f * v * Math.max(0, -c)};   // canela (dobra o joelho para tras)
            float sobe = flutua ? 0.12f * (float) Math.sin(tk * 0.07) : 0.06f * Math.abs(c) * v + 0.012f * resp;

            switch (acao) {
                case GOLPE -> {   // arma por cima (0,35), desce de uma vez (0,35-0,5), volta
                    float[] g = curva(k, 0.38f, 0.52f);
                    bP[0] += 150 * g[0] - 15 * g[1];
                    aP[0] += 30 * g[0] - 10 * g[1];
                    bR[0] += 18 * g[0];
                    bP[1] += 40 * g[0] + 25 * g[1];
                    tY += -22 * g[0] + 26 * g[1];
                    tP += -10 * g[0] + 20 * g[1];
                    cP += 14 * g[0] - 12 * g[1];
                    mand += 22 * (g[0] + g[1]);
                    pP[0] += 14 * g[1];
                    pP[1] -= 10 * g[1];
                    sobe -= 0.05f * g[1];
                }
                case GOLPE_RAPIDO -> {
                    float[] g = curva(k, 0.2f, 0.45f);
                    bP[0] += 70 * g[0] + 20 * g[1];
                    tY += -12 * g[0] + 16 * g[1];
                    tP += 10 * g[1];
                    mand += 15 * (g[0] + g[1]);
                }
                case LANCA -> {   // pocao, bola de fogo: puxa para tras e arremessa
                    float[] g = curva(k, 0.45f, 0.62f);
                    bP[0] += 125 * g[0] + 70 * g[1];
                    aP[0] += 40 * g[0] - 10 * g[1];
                    bR[0] += 25 * g[0];
                    tY += -18 * g[0] + 22 * g[1];
                    tP += -6 * g[0] + 12 * g[1];
                    mand += 12 * g[1];
                }
                case MAGIA -> {   // ergue os dois bracos, treme, e solta para a frente
                    float[] g = curva(k, 0.6f, 0.75f);
                    float treme = (float) Math.sin(tk * 1.7) * 4 * g[0];
                    for (int i = 0; i < 2; i++) {
                        bP[i] += 155 * g[0] + 95 * g[1] + treme;
                        bR[i] += 28 * g[0] - 10 * g[1];
                        aP[i] += -8 * g[0];
                    }
                    tP += -14 * g[0] + 16 * g[1];
                    cP += 26 * g[0] - 10 * g[1];
                    mand += 26 * (g[0] + g[1]);
                    sobe += 0.08f * g[0];
                }
                default -> { }
            }
            // arqueiro: segura o arco esticado enquanto mira (mao esquerda) e a direita puxa a corda
            Material naMao = mob.getEquipment() == null ? Material.AIR : mob.getEquipment().getItemInMainHand().getType();
            boolean arco = naMao == Material.BOW || naMao == Material.CROSSBOW;
            if (mob.isHandRaised() && naMao == Material.TRIDENT && acao == Acao.NADA) acao(Acao.LANCA, 16);
            if (arco && (mob.isHandRaised() || soltou > 0)) {
                float puxa = soltou > 0 ? soltou / 6f : 1f;
                bP[1] = 85 + olharP;
                bY[1] = 10;
                aP[1] = 0;
                bP[0] = 80 + olharP;
                bY[0] = -35 * puxa;
                aP[0] = 70 * puxa;
                tY = -18 + (soltou > 0 ? 8 * (1 - puxa) : 0);
                cY = olharY + 18;
            }
            // tranco quando apanha: joga o tronco e a cabeca para tras e abre os bracos
            tP -= 14 * dano;
            cP += 12 * dano;
            bR[0] += 14 * dano;
            bR[1] += 14 * dano;
            mand += 18 * dano;
            sobe += 0.03f * dano * (float) Math.sin(tk * 2.3);

            // capa e saia: vao para tras com a velocidade, balancam com atraso (segue o tronco)
            // o modelo ja tem a capa um pouco inclinada; parado ela quase cai reta, andando vai para tras
            float capaAlvo = (9 - 30 * v) - tP * 0.7f + 3 * (float) Math.sin(tk * 0.23) * (0.4f + v);
            capa += (capaAlvo - capa) * 0.3f;
            capaRoll += (-tR * 1.4f + 2 * (float) Math.sin(tk * 0.13) - capaRoll) * 0.3f;
            float saiaAlvo = -tP * 0.6f + 6 * s * v;
            saia += (saiaAlvo - saia) * 0.35f;

            if (morto) {
                float m = ss((tk - morteDesde) / 26f);
                bP[0] = 60 * m;
                bP[1] = 50 * m;
                bR[0] = 30 * m;
                bR[1] = 30 * m;
                cP = -25 * m;
                mand = 30 * m;
                pP[0] = 10 * m;
                pP[1] = -8 * m;
                jP[0] = -10 * m;
                jP[1] = -25 * m;
                if (flutua) {   // desaba no chao
                    global.rotateX(rad(-25 * m));
                    gOff.set(0, -0.6f * esc * m, 0);
                } else {   // cai de costas
                    global.rotateX(rad(88 * m));
                    gOff.set(0, -0.1f * esc * m - 0.4f * Math.max(0, (tk - morteDesde - 34) / 26f), 0);
                }
            }

            set("tronco", -tP, tY, tR);
            mover("tronco", 0, sobe * 16, 0);
            set("cabeca", cP, cY, cR);
            set("mandibula", -mand, 0, 0);
            set("braco_d", bP[0], bY[0], fora(1, bR[0]));
            set("braco_e", bP[1], bY[1], fora(-1, bR[1]));
            set("antebraco_d", aP[0], 0, 0);
            set("antebraco_e", aP[1], 0, 0);
            set("coxa_d", pP[0], 0, 0);
            set("coxa_e", pP[1], 0, 0);
            set("canela_d", jP[0], 0, 0);
            set("canela_e", jP[1], 0, 0);
            set("capa", capa, 0, capaRoll);
            set("saia", saia, 0, tR * 1.2f);
            // pecas soltas: orbita de pocoes/estilhacos, livro flutuante, corvo, aneis e fogo do Senhor das Chamas
            set("orbita", 0, tk * 3.2f, 0);
            mover("orbita", 0, 1.6f * (float) Math.sin(tk * 0.09), 0);
            set("livro", 8 + 6 * (float) Math.sin(tk * 0.11), 20 * (float) Math.sin(tk * 0.05), 4 * (float) Math.sin(tk * 0.08));
            mover("livro", 0, 1.4f * (float) Math.sin(tk * 0.12) + (acao == Acao.MAGIA ? 3f : 0), 0);
            set("corvo", 10 * (float) Math.max(0, Math.sin(tk * 0.35)) * ((tk / 40) % 3 == 0 ? 1 : 0), 25 * (float) Math.sin(tk * 0.04), 0);
            set("anel1", 0, tk * 7f, 0);
            set("anel2", 0, -tk * 5f, 0);
            mover("anel1", 0, 0.8f * (float) Math.sin(tk * 0.15), 0);
            mover("anel2", 0, 0.8f * (float) Math.sin(tk * 0.15 + 2), 0);
            escala("fogo", 1f + 0.10f * (float) Math.sin(tk * 0.9) + 0.06f * (float) Math.sin(tk * 2.3));
            set("fogo", 4 * (float) Math.sin(tk * 0.4), tk * 2f, 0);
            if (flutua) {
                mover("fogo", 0, sobe * 16, 0);
                mover("anel1", 0, sobe * 16, 0);
                mover("anel2", 0, sobe * 16, 0);
            }
        }

        // ----------------------------------------------------------- aranha
        private void poseAranha(Quaternionf global, Vector3f gOff) {
            float v = vel, t = tk - acaoDesde, k = acaoDura <= 0 ? 1 : t / acaoDura;
            float resp = (float) Math.sin(tk * 0.08);
            float corpoP = 1.5f * resp, corpoY = olharY * 0.4f, sobe = 0.02f * resp;
            float presa = 4 + 4 * (float) Math.max(0, Math.sin(tk * 0.6)) * (olharY != 0 ? 1 : 0.3f);
            float abP = 3 * resp, abY = 0;
            float[] frenteErgue = {0, 0};
            switch (acao) {
                case GOLPE, GOLPE_RAPIDO -> {   // empina, ergue as patas da frente e da o bote
                    float[] g = curva(k, 0.45f, 0.6f);
                    corpoP += 16 * g[0] - 12 * g[1];
                    frenteErgue[0] = frenteErgue[1] = 55 * g[0] + 20 * g[1];
                    presa += 30 * (g[0] + g[1]);
                    sobe += 0.12f * g[0] - 0.05f * g[1];
                    gOff.add(0, 0, 0.35f * esc * g[1]);
                }
                case LANCA, MAGIA -> {   // cospe teia: abdomen vira para a frente por baixo
                    float[] g = curva(k, 0.5f, 0.65f);
                    abP += -40 * g[0] - 25 * g[1];
                    corpoP += -8 * g[0];
                    presa += 20 * g[1];
                }
                default -> { }
            }
            corpoP -= 10 * dano;
            presa += 20 * dano;
            sobe += 0.03f * dano * (float) Math.sin(tk * 2.5);
            abY = 8 * (float) Math.sin(fase * 0.5) * v + 3 * (float) Math.sin(tk * 0.05);
            for (int lado = 0; lado < 2; lado++) {
                String ld = lado == 0 ? "d" : "e";
                int sgn = lado == 0 ? 1 : -1;
                for (int i = 0; i < 4; i++) {
                    // marcha em dois grupos alternados (d0 e1 d2 e3 / e0 d1 e2 d3)
                    double ph = fase + (((i + lado) % 2) == 0 ? 0 : Math.PI);
                    float sw = (float) Math.sin(ph), lift = (float) Math.max(0, Math.cos(ph));
                    float tw = 2.5f * (float) Math.sin(tk * 0.09 + i * 1.3 + lado);   // patas inquietas paradas
                    float yaw = sgn * (22 * sw * v) + tw;
                    // joelho alto (pata sobe e desce em arco), como aranha de verdade
                    float ergue = 20 + (24 * lift * v) + 3 * Math.abs(tw) * (1 - v);
                    if (i == 0) ergue += frenteErgue[lado];
                    float canela = -38 - (14 * lift * v) - (i == 0 ? frenteErgue[lado] * 0.6f : 0) + 2 * resp;
                    if (morto) {   // encolhe as patas
                        float m = ss((tk - morteDesde) / 22f);
                        ergue = -35 * m + 20 * m * (float) Math.sin(tk * 1.5) * (1 - m);
                        canela = -70 * m;
                        yaw = sgn * (i - 1.5f) * 8 * m;
                    }
                    set("coxa_" + ld + i, 0, yaw, fora(sgn, ergue));
                    set("canela_" + ld + i, 0, 0, fora(sgn, canela));
                }
            }
            if (morto) {
                float m = ss((tk - morteDesde - 14) / 24f);
                global.rotateZ(rad(180 * m));
                gOff.set(0, 0.9f * esc * m, 0);
                presa = 35;
            }
            set("corpo", -corpoP, corpoY, 3 * (float) Math.sin(fase) * v);
            mover("corpo", 0, sobe * 16 + 0.6f * Math.abs((float) Math.cos(fase)) * v, 0);
            set("abdomen", abP, abY, 0);
            set("presas", -presa, 0, 0);
        }

        private void escala3(String n, float x, float y, float z) {
            int i = d.i(n);
            if (i >= 0) sc[i].set(x, y, z);
        }

        // ----------------------------------------------------------- 4 patas (Devastador, Besta Zoglin)
        private void poseQuadrupede(Quaternionf global, Vector3f gOff) {
            float v = vel, t = tk - acaoDesde, k = acaoDura <= 0 ? 1 : t / acaoDura;
            float resp = (float) Math.sin(tk * 0.07), s2 = (float) Math.sin(fase * 2);
            float cP = 1.2f * resp, cR = 2.5f * (float) Math.sin(fase) * v, sobe = 0.5f * Math.abs((float) Math.cos(fase)) * v + 0.15f * resp;
            float hP = olharP * 0.6f + 4 * (float) Math.sin(tk * 0.045) * (1 - v) + 5 * s2 * v, hY = olharY * 0.8f, mand = 3 + 3 * Math.max(0, resp);
            float caudaY = 14 * (float) Math.sin(tk * 0.15) + 18 * (float) Math.sin(fase) * v, caudaP = 8 + 6 * v;
            float avanco = 0;
            float[] perna = new float[4], joelho = new float[4];   // fd, fe, td, te
            String[] ks = {"fd", "fe", "td", "te"};
            for (int i = 0; i < 4; i++) {
                // trote: patas em diagonal juntas (fd+te / fe+td)
                double ph = fase + ((i == 0 || i == 3) ? 0 : Math.PI);
                float sw = (float) Math.sin(ph), lift = (float) Math.max(0, Math.cos(ph));
                perna[i] = 28 * sw * v;
                joelho[i] = (i < 2 ? -38 : 34) * lift * v;
            }
            switch (acao) {
                case GOLPE, GOLPE_RAPIDO -> {   // abaixa a cabeca, avanca e da a chifrada para cima
                    float[] g = curva(k, 0.45f, 0.58f);
                    hP += -26 * g[0] + 38 * g[1];
                    cP += 8 * g[0] - 10 * g[1];
                    mand += 26 * (g[0] + g[1]);
                    avanco = 6 * g[1] - 2 * g[0];
                    perna[0] += -14 * g[0] + 22 * g[1];
                    perna[1] += -14 * g[0] + 22 * g[1];
                    perna[2] += 12 * g[0];
                    perna[3] += 12 * g[0];
                }
                case LANCA, MAGIA -> {   // urra: ergue a cabeca e treme
                    float[] g = curva(k, 0.55f, 0.7f);
                    float treme = (float) Math.sin(tk * 1.9) * 3 * g[0];
                    hP += 30 * g[0] + 10 * g[1] + treme;
                    mand += 40 * g[0] + 20 * g[1];
                    cP += -6 * g[0];
                    cR += treme * 0.6f;
                }
                default -> { }
            }
            cR += 8 * dano * (float) Math.sin(tk * 2.1);
            hP += 14 * dano;
            mand += 20 * dano;
            if (morto) {   // tomba de lado, patas duras
                float m = ss((tk - morteDesde) / 24f);
                global.rotateZ(rad(88 * m));
                gOff.set(0, -0.15f * esc * m, 0);
                for (int i = 0; i < 4; i++) {
                    perna[i] = (i < 2 ? 20 : -20) * m;
                    joelho[i] = 0;
                }
                hP = -20 * m;
                mand = 30 * m;
                caudaY = 0;
            }
            set("corpo", -cP, 0, cR);
            mover("corpo", 0, sobe, -avanco);
            set("cabeca", hP, hY, -cR * 0.5f);
            set("mandibula", -mand, 0, 0);
            set("cauda", caudaP, caudaY, 0);
            for (int i = 0; i < 4; i++) {
                set("coxa_" + ks[i], perna[i], 0, 0);
                set("canela_" + ks[i], joelho[i], 0, 0);
            }
        }

        // ----------------------------------------------------------- gosma: estica, achata e quica
        private void poseGosma(Quaternionf global, Vector3f gOff) {
            float v = vel, t = tk - acaoDesde, k = acaoDura <= 0 ? 1 : t / acaoDura;
            float resp = (float) Math.sin(tk * 0.09);
            double hp = fase * 0.55;
            float pulo = (float) Math.max(0, Math.sin(hp)), pouso = (float) Math.max(0, -Math.sin(hp));
            float sy = 1 + 0.04f * resp + 0.16f * pulo * v - 0.22f * pouso * v, sxz = 1 - 0.03f * resp - 0.08f * pulo * v + 0.14f * pouso * v;
            float alto = 7 * pulo * v, abre = 0.4f + 0.4f * Math.max(0, resp), inclina = 6 * (float) Math.cos(hp) * v;
            switch (acao) {
                case GOLPE, GOLPE_RAPIDO -> {   // estica para cima e se joga em cima do alvo
                    float[] g = curva(k, 0.45f, 0.6f);
                    sy += 0.3f * g[0] - 0.35f * g[1];
                    sxz += -0.14f * g[0] + 0.28f * g[1];
                    alto += 5 * g[0];
                    abre += 3 * (g[0] + g[1]);
                    inclina += 18 * g[1];
                }
                case LANCA, MAGIA -> {   // treme inteira e cospe
                    float[] g = curva(k, 0.55f, 0.7f);
                    float treme = (float) Math.sin(tk * 2.2) * 0.08f * g[0];
                    sy += treme;
                    sxz -= treme;
                    abre += 3.5f * g[1] + 1.5f * g[0];
                }
                default -> { }
            }
            // apanhou: balanca como gelatina
            float jig = dano * (float) Math.sin(tk * 1.7) * 0.18f;
            sy += jig;
            sxz -= jig * 0.8f;
            if (morto) {   // derrete no chao
                float m = ss((tk - morteDesde) / 30f);
                sy = 1 - 0.85f * m;
                sxz = 1 + 0.6f * m;
                alto = 0;
                abre = 2 * m;
            }
            set("corpo", -inclina, 0, 0);
            escala3("corpo", sxz, sy, sxz);
            mover("corpo", 0, alto, 0);
            // a parte de cima vira para o alvo e balanca com atraso
            set("cima", 3 * (float) Math.sin(tk * 0.11) - inclina * 0.4f, olharY * 0.5f, 2 * (float) Math.sin(tk * 0.07));
            escala3("cima", 1 / Math.max(0.6f, sxz) * 0.98f + 0.02f, 1, 1 / Math.max(0.6f, sxz) * 0.98f + 0.02f);
            mover("mandibula", 0, -abre, 0);
        }

        /** Junta as pecas em cadeia (filho gira em volta do pivo do pai) e manda as transformacoes. */
        private void aplicar(Quaternionf global, Vector3f gOff) {
            PecaDef[] ps = d.pecas();
            Quaternionf[] rw = new Quaternionf[ps.length];
            Vector3f[] pw = new Vector3f[ps.length];
            for (int i = 0; i < ps.length; i++) {
                PecaDef p = ps[i];
                Vector3f rest = lugar(p);
                if (p.pai() < 0) {
                    pw[i] = rest.add(off[i]);
                    rw[i] = new Quaternionf(q[i]);
                } else {
                    PecaDef pai = ps[p.pai()];
                    // o pai esticado/achatado (gosma) leva os filhos junto
                    Vector3f rel = rest.sub(lugar(pai)).mul(sc[p.pai()]).add(off[i]);
                    pw[i] = rw[p.pai()].transform(rel, new Vector3f()).add(pw[p.pai()]);
                    rw[i] = new Quaternionf(rw[p.pai()]).mul(q[i]);
                }
            }
            for (int i = 0; i < ps.length; i++) {
                Vector3f pos = global.transform(pw[i], new Vector3f()).add(gOff);
                Quaternionf r = new Quaternionf(global).mul(rw[i]);
                Vector3f e = new Vector3f(sc[i]).mul(esc);
                ItemDisplay x = pecas[i];
                x.setInterpolationDelay(0);
                x.setInterpolationDuration(PASSO);
                x.setTransformation(new Transformation(pos, r, e, new Quaternionf()));
            }
        }
    }

    // ------------------------------------------------------------------ teste do admin (pelo console)

    private final List<LivingEntity> testes = new ArrayList<>();
    private final Set<UUID> andandoTeste = ConcurrentHashMap.newKeySet();

    /**
     * masmorras admin corpo <TEMA> <mundo> <x> <y> <z>  -> chefe parado (sem IA) com o corpo 3D
     * masmorras admin corpo-anim <golpe|rapido|lanca|magia|dano|morte|andar|parar>
     * masmorras admin corpo-dump  -> grava as transformacoes de cada peca em corpos-dump.json (para conferir a pose)
     * masmorras admin corpo-limpar
     */
    void comando(org.bukkit.command.CommandSender s, String[] a) {
        String sub = a[1].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "corpo" -> {
                Tipo t = a.length > 2 ? Tipo.TODOS.get(a[2].toUpperCase(java.util.Locale.ROOT)) : null;
                World w = a.length > 3 ? Bukkit.getWorld(a[3]) : null;
                if (t == null || w == null || a.length < 7) {
                    s.sendMessage("uso: masmorras admin corpo <TEMA> <mundo> <x> <y> <z>");
                    return;
                }
                Location l = new Location(w, Double.parseDouble(a[4]), Double.parseDouble(a[5]), Double.parseDouble(a[6]), 0, 0);
                l.getChunk().load();
                LivingEntity e = (LivingEntity) w.spawnEntity(l, org.bukkit.entity.EntityType.valueOf(pl.semBruto(t.chefe)), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
                if (e instanceof Mob m) m.setAI(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setPersistent(false);
                if (t.escala != 1) Chefe.atributo(e, Attribute.SCALE, t.escala);
                if (e.getEquipment() != null) for (String x : t.equipamento) {
                    String[] p = x.split(":");
                    Material mat = Material.matchMaterial(p[1]);
                    if (mat != null) e.getEquipment().setItem(EquipmentSlot.valueOf(p[0]), new ItemStack(mat));
                }
                boolean ok = vestir(e, t);
                testes.add(e);
                s.sendMessage("corpo de " + t.chefeNome + ": " + (ok ? "ok (" + corpos.get(e.getUniqueId()).pecas.length + " pecas)" : "SEM corpo 3D"));
            }
            case "corpo-anim" -> {
                String an = a.length > 2 ? a[2].toLowerCase(java.util.Locale.ROOT) : "golpe";
                for (LivingEntity e : testes) {
                    Corpo c = corpos.get(e.getUniqueId());
                    if (c == null) continue;
                    switch (an) {
                        case "golpe" -> c.acao(Acao.GOLPE, 20);
                        case "rapido" -> c.acao(Acao.GOLPE_RAPIDO, 12);
                        case "lanca" -> c.acao(Acao.LANCA, 16);
                        case "magia" -> c.acao(Acao.MAGIA, 26);
                        case "dano" -> c.dano = 1.2f;
                        case "morte" -> c.morrer(ticks);
                        case "andar" -> andandoTeste.add(e.getUniqueId());
                        default -> andandoTeste.remove(e.getUniqueId());
                    }
                }
                s.sendMessage("anim " + an + " em " + testes.size());
            }
            case "corpo-dump" -> {
                JsonArray tudo = new JsonArray();
                for (Corpo c : corpos.values()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("modelo", c.d.modelo());
                    o.addProperty("yaw", c.base().getYaw());
                    o.addProperty("tk", c.tk);
                    o.addProperty("acao", c.acao.name() + (c.morto ? " morto" : "") + " vel=" + c.vel);
                    JsonArray ps = new JsonArray();
                    for (int i = 0; i < c.pecas.length; i++) {
                        if (c.pecas[i] == null) continue;
                        Transformation tr = c.pecas[i].getTransformation();
                        JsonObject p = new JsonObject();
                        p.addProperty("nome", c.d.pecas()[i].nome());
                        JsonArray tv = new JsonArray();
                        tv.add(tr.getTranslation().x);
                        tv.add(tr.getTranslation().y);
                        tv.add(tr.getTranslation().z);
                        JsonArray rq = new JsonArray();
                        rq.add(tr.getLeftRotation().x);
                        rq.add(tr.getLeftRotation().y);
                        rq.add(tr.getLeftRotation().z);
                        rq.add(tr.getLeftRotation().w);
                        p.add("t", tv);
                        p.add("q", rq);
                        p.addProperty("s", tr.getScale().x);
                        ps.add(p);
                    }
                    o.add("pecas", ps);
                    tudo.add(o);
                }
                try {
                    java.nio.file.Files.writeString(new java.io.File(pl.getDataFolder(), "corpos-dump.json").toPath(), tudo.toString());
                    s.sendMessage("dump de " + tudo.size() + " corpos");
                } catch (Exception ex) {
                    s.sendMessage("falhou: " + ex);
                }
            }
            default -> {
                for (LivingEntity e : testes) e.remove();
                testes.clear();
                andandoTeste.clear();
                s.sendMessage("testes limpos");
            }
        }
    }

    /** No teste, "andar" leva o chefe em circulo (raio 3, devagar) para ver o passo. */
    private void andarTeste() {
        for (LivingEntity e : testes) {
            if (!andandoTeste.contains(e.getUniqueId()) || !e.isValid()) continue;
            Location l = e.getLocation();
            double ang = (ticks * 0.035) % (Math.PI * 2);
            Location c = l.clone().subtract(Math.cos(ang - 0.035) * 3, 0, Math.sin(ang - 0.035) * 3);
            Location n = c.clone().add(Math.cos(ang) * 3, 0, Math.sin(ang) * 3);
            n.setYaw((float) Math.toDegrees(Math.atan2(-(n.getX() - l.getX()), n.getZ() - l.getZ())));
            e.teleport(n);
            e.setBodyYaw(n.getYaw());
        }
    }

    List<LivingEntity> comCorpo() {
        List<LivingEntity> l = new ArrayList<>();
        for (Corpo c : corpos.values()) l.add(c.mob);
        return l;
    }
}
