package br.servidor.extras;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Placas informativas da ilha do spawn (minigames, masmorras, kits, pets, classes, grupos e clas, comandos).
 * O texto fica em plugins/Extras/placas.yml. Coloca sozinho quando a "versao" do arquivo muda (ou com /extras placas):
 * placa em pe de madeira, texto brilhando e encerada (ninguem edita). So poe onde tem ar com chao firme embaixo.
 */
final class Placas {

    record Placa(int x, int y, int z, BlockFace olha, String[] linhas) {}

    private final Extras pl;
    final File arquivo, feito;

    Placas(Extras pl) {
        this.pl = pl;
        this.arquivo = new File(pl.getDataFolder(), "placas.yml");
        this.feito = new File(pl.getDataFolder(), "placas-colocadas.yml");
    }

    /** O placas.yml do jar e mais novo que o da pasta? Entao troca (o Paulo pode editar o da pasta). */
    void atualizarArquivo() {
        YamlConfiguration doJar = doJar();
        if (!arquivo.exists() || (doJar != null && doJar.getInt("versao", 0) > YamlConfiguration.loadConfiguration(arquivo).getInt("versao", 0)))
            pl.saveResource("placas.yml", true);
    }

    private YamlConfiguration doJar() {
        try (var in = pl.getResource("placas.yml")) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    static String cor(String s) {
        return s.replace('&', '§');
    }

    static List<Placa> ler(YamlConfiguration y) {
        List<Placa> l = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("placas")) {
            try {
                String[] p = String.valueOf(m.get("pos")).trim().split("\\s+");
                BlockFace f = BlockFace.valueOf(String.valueOf(m.get("olha")).trim().toUpperCase(Locale.ROOT));
                Object t = m.get("texto");
                String[] linhas = new String[4];
                java.util.Arrays.fill(linhas, "");
                if (t instanceof List<?> tl) for (int i = 0; i < 4 && i < tl.size(); i++) linhas[i] = cor(String.valueOf(tl.get(i)));
                l.add(new Placa(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), f, linhas));
            } catch (Exception ignored) {
                // placa com erro de digitacao: pula
            }
        }
        return l;
    }

    /** Ao ligar: se a versao do placas.yml ainda nao foi colocada, coloca. */
    void conferir() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        int v = y.getInt("versao", 0);
        YamlConfiguration f = YamlConfiguration.loadConfiguration(feito);
        if (v > 0 && f.getInt("versao", -1) != v) colocar(null);
    }

    /** Coloca todas (tira as da versao anterior que sairam da lista). Devolve um resumo. */
    String colocar(org.bukkit.command.CommandSender quem) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(arquivo);
        World w = Bukkit.getWorld(y.getString("mundo", "world"));
        if (w == null) return "mundo " + y.getString("mundo") + " nao existe";
        Material madeira = Material.matchMaterial(y.getString("madeira", "CRIMSON").toUpperCase(Locale.ROOT) + "_SIGN");
        if (madeira == null) madeira = Material.CRIMSON_SIGN;
        List<Placa> l = ler(y);
        Set<String> agora = new HashSet<>();
        for (Placa p : l) agora.add(p.x() + " " + p.y() + " " + p.z());
        // as que estavam na versao anterior e sairam da lista
        YamlConfiguration f = YamlConfiguration.loadConfiguration(feito);
        int tiradas = 0;
        for (String s : f.getStringList("posicoes")) {
            if (agora.contains(s)) continue;
            String[] p = s.split(" ");
            Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            if (org.bukkit.Tag.ALL_SIGNS.isTagged(b.getType())) {
                b.setType(Material.AIR, false);
                tiradas++;
            }
        }
        int postas = 0, puladas = 0;
        List<String> feitas = new ArrayList<>();
        for (Placa p : l) {
            if (por(w, p, madeira)) {
                postas++;
                feitas.add(p.x() + " " + p.y() + " " + p.z());
            } else {
                puladas++;
                pl.getLogger().warning("Placa em " + p.x() + " " + p.y() + " " + p.z() + " nao foi colocada (lugar ocupado ou sem chao).");
            }
        }
        f.set("versao", y.getInt("versao", 0));
        f.set("posicoes", feitas);
        try {
            f.save(feito);
        } catch (IOException e) {
            pl.getLogger().warning("Nao consegui salvar placas-colocadas.yml: " + e.getMessage());
        }
        String r = postas + " placas colocadas" + (puladas > 0 ? ", " + puladas + " puladas (lugar ocupado)" : "") + (tiradas > 0 ? ", " + tiradas + " antigas tiradas" : "");
        pl.getLogger().info("Placas do spawn: " + r + ".");
        return r;
    }

    static boolean podeOcupar(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || org.bukkit.Tag.ALL_SIGNS.isTagged(m);
    }

    private boolean por(World w, Placa p, Material madeira) {
        Block b = w.getBlockAt(p.x(), p.y(), p.z());
        if (!podeOcupar(b.getType()) || !b.getRelative(BlockFace.DOWN).getType().isSolid()) return false;
        BlockData bd = Bukkit.createBlockData(madeira);
        if (bd instanceof org.bukkit.block.data.type.Sign sd) sd.setRotation(p.olha());
        b.setBlockData(bd, false);
        if (!(b.getState() instanceof org.bukkit.block.Sign sg)) return false;
        for (Side lado : Side.values()) {
            SignSide s = sg.getSide(lado);
            for (int i = 0; i < 4; i++) s.line(i, Extras.txt(lado == Side.FRONT ? p.linhas()[i] : ""));
            s.setColor(DyeColor.WHITE);
            s.setGlowingText(lado == Side.FRONT);
        }
        sg.setWaxed(true);
        sg.update(true, false);
        return true;
    }
}
