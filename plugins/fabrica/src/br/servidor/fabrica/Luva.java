package br.servidor.fabrica;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Luva de Gancho: a mao (azul = 0, vermelha = 1) sai voando presa a um cabo ate o alvo e volta. A mira e assistida
 * (no celular e dificil acertar um botao pequeno a 15 blocos): vale o alvo mais perto da linha da mira, se nada
 * estiver no caminho.
 */
final class Luva {

    static final double ALCANCE = 20;
    static final Color[] COR = {Color.fromRGB(40, 110, 240), Color.fromRGB(230, 40, 40)};
    static final String[] NOME = {"azul", "vermelha"};

    private Luva() {
    }

    static ItemStack modeloMao(int mao) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        try {
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                    net.kyori.adventure.key.Key.key("corona", "props/fab_mao_" + (mao == 0 ? "azul" : "vermelha") + "_f0"));
        } catch (Throwable ignored) {
        }
        return it;
    }

    static ItemStack modeloPlugue() {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        try {
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/fab_plugue_f0"));
        } catch (Throwable ignored) {
        }
        return it;
    }

    static ItemStack modeloFita() {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        try {
            it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("corona", "props/fab_fita_f0"));
        } catch (Throwable ignored) {
        }
        return it;
    }

    /** Alvo da mira (com assistencia) ou null. Ponto de impacto em 'ponto' (centro do alvo, ou onde bateu). */
    static Planta.Alvo mirar(Planta pt, Player p, Location[] ponto) {
        Location olho = p.getEyeLocation();
        Vector dir = olho.getDirection().normalize();
        World w = p.getWorld();
        // 1) acerto direto (o bloco do alvo, ou o bloco preso na face que levou o tiro)
        RayTraceResult r = w.rayTraceBlocks(olho, dir, ALCANCE, FluidCollisionMode.NEVER, false);
        if (r != null && r.getHitBlock() != null) {
            Block b = r.getHitBlock();
            Planta.Alvo a = pt.alvoEm(b);
            if (a == null && r.getHitBlockFace() != null) a = pt.alvoEm(b.getRelative(r.getHitBlockFace()));
            if (a == null) a = pt.alvoEm(b.getRelative(org.bukkit.block.BlockFace.UP));   // acertou a prateleira logo abaixo
            if (a != null && usaLuva(a.tipo())) {
                ponto[0] = pt.centro(a);
                return a;
            }
            ponto[0] = r.getHitPosition().toLocation(w);
        } else {
            ponto[0] = olho.clone().add(dir.clone().multiply(ALCANCE));
        }
        // 2) mira assistida
        Planta.Alvo melhor = null;
        double melhorD = 9;
        for (Planta.Alvo a : Planta.ALVOS) {
            if (!usaLuva(a.tipo())) continue;
            Location c = pt.centro(a);
            Vector v = c.toVector().subtract(olho.toVector());
            double t = v.dot(dir);
            if (t <= 0.5 || t > ALCANCE + 1) continue;
            double perp = v.clone().subtract(dir.clone().multiply(t)).length();
            double tol = 0.9 + t * 0.045;
            if (perp > tol || perp >= melhorD) continue;
            // nada no caminho
            Vector ate = c.toVector().subtract(olho.toVector());
            double dist = ate.length();
            RayTraceResult livre = w.rayTraceBlocks(olho, ate.normalize(), Math.max(0.1, dist - 0.2), FluidCollisionMode.NEVER, false);
            if (livre != null && livre.getHitBlock() != null) {
                Block hb = livre.getHitBlock();
                Planta.Alvo ha = pt.alvoEm(hb);
                // bloqueado so pelo apoio do proprio alvo (prateleira embaixo da caixa, parede do botao): vale
                boolean apoio = hb.getLocation().add(0.5, 0.5, 0.5).distance(c) < 1.5;
                if (ha != a && !apoio) continue;
            }
            melhor = a;
            melhorD = perp;
        }
        if (melhor != null) ponto[0] = pt.centro(melhor);
        return melhor;
    }

    static boolean usaLuva(Planta.Tipo t) {
        return t == Planta.Tipo.BOTAO || t == Planta.Tipo.PLUGUE || t == Planta.Tipo.TOMADA || t == Planta.Tipo.CAIXA
                || t == Planta.Tipo.RECEPTOR || t == Planta.Tipo.PUXADOR;
    }

    /** Animacao: a mao vai ate 'alvo' e volta; 'chegou' roda quando a mao encosta no alvo. */
    static void disparar(Fabrica pl, Player p, int mao, Location alvo, Runnable chegou) {
        World w = p.getWorld();
        Location saida = maoDe(p, mao);
        Vector caminho = alvo.toVector().subtract(saida.toVector());
        double dist = Math.max(0.5, caminho.length());
        Vector dir = caminho.clone().normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, dir.getY()))));
        Location ini = saida.clone();
        ini.setYaw(yaw + 180);
        ini.setPitch(-pitch);
        ItemDisplay voo = w.spawn(ini, ItemDisplay.class, d -> {
            d.setItemStack(modeloMao(mao));
            d.setPersistent(false);
            d.setTeleportDuration(2);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.55f), new AxisAngle4f()));
        });
        w.playSound(saida, Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 0.6f);
        w.playSound(saida, Sound.BLOCK_PISTON_EXTEND, 0.5f, 1.6f);
        int idaTicks = Math.max(2, (int) Math.ceil(dist / 2.2));
        int total = idaTicks * 2 + 1;
        final int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(pl, tarefa -> {
            t[0]++;
            if (!p.isOnline() || !voo.isValid()) {
                voo.remove();
                tarefa.cancel();
                return;
            }
            Location base = maoDe(p, mao);
            double f;
            if (t[0] <= idaTicks) f = (double) t[0] / idaTicks;
            else f = Math.max(0, 1 - (double) (t[0] - idaTicks) / idaTicks);
            Location agora = base.clone().add(alvo.toVector().subtract(base.toVector()).multiply(f));
            agora.setYaw(ini.getYaw());
            agora.setPitch(ini.getPitch());
            if (t[0] % 2 == 0 || t[0] == idaTicks) voo.teleport(agora);
            cabo(p, base, agora, COR[mao], 0.7);
            if (t[0] == idaTicks) {
                w.playSound(alvo, Sound.BLOCK_CHAIN_PLACE, 1f, 1.4f);
                w.playSound(alvo, Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 0.8f, 0.8f);
                try {
                    chegou.run();
                } catch (Throwable ex) {
                    pl.getLogger().warning("Luva: " + ex);
                }
            }
            if (t[0] >= total) {
                voo.remove();
                w.playSound(base, Sound.ITEM_ARMOR_EQUIP_IRON, 0.6f, 1.3f);
                tarefa.cancel();
            }
        }, 1L, 1L);
    }

    /** Ponto da mao (um pouco a direita/esquerda e abaixo do olho). */
    static Location maoDe(Player p, int mao) {
        Location o = p.getEyeLocation();
        Vector frente = o.getDirection().setY(0);
        if (frente.lengthSquared() < 1e-4) frente = new Vector(0, 0, 1);
        frente.normalize();
        Vector lado = new Vector(-frente.getZ(), 0, frente.getX()).multiply(mao == 0 ? -0.35 : 0.35);
        return o.clone().add(frente.multiply(0.4)).add(lado).add(0, -0.45, 0);
    }

    /** Cabo de particulas entre dois pontos (todos que estao perto veem; no celular, menos pontos). */
    static void cabo(Player dono, Location a, Location b, Color cor, double passo) {
        World w = a.getWorld();
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.3) return;
        Particle.DustOptions dust = new Particle.DustOptions(cor, 0.6f);
        for (Player v : w.getPlayers()) {
            if (v.getLocation().distanceSquared(a) > 48 * 48) continue;
            double p = Fabrica.ehBedrock(v) ? Math.max(passo, 1.4) : passo;
            int n = (int) Math.min(40, Math.ceil(len / p));
            for (int i = 0; i <= n; i++) {
                Location l = a.clone().add(d.clone().multiply((double) i / n));
                v.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dust);
            }
        }
    }
}
