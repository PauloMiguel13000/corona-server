package br.servidor.poderes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Ponte (por reflexao) com o plugin BabyPets: acha o pet ativo do jogador, nivel e raridade. */
final class PetsPonte {

    static final class PetInfo {
        final LivingEntity entidade;
        final int nivel;
        final String raridade;
        final String nome;
        /** Id do pet no banco do BabyPets (-1 = nao sabe). */
        int id = -1;

        PetInfo(LivingEntity entidade, int nivel, String raridade, String nome) {
            this.entidade = entidade;
            this.nivel = Math.max(1, nivel);
            this.raridade = raridade == null ? "COMMON" : raridade;
            this.nome = nome;
        }

        /** Dano do pet: sobe com o nivel e a raridade. No nivel 10: 7 (comum), 11 (epico) e 21 (lendario, quase o dobro). */
        double dano() {
            double base, passo;
            switch (raridade) {
                case "UNCOMMON" -> { base = 3.2; passo = 0.55; }
                case "RARE" -> { base = 3.4; passo = 0.7; }
                case "EPIC" -> { base = 3.6; passo = 0.85; }
                case "LEGENDARY" -> { base = 6.0; passo = 1.7; }
                default -> { base = 3.0; passo = 0.45; }
            }
            return Math.min(lendario() ? 22.0 : 15.0, base + (nivel - 1) * passo);
        }

        boolean lendario() {
            return "LEGENDARY".equals(raridade);
        }

        double multiplicador() {
            switch (raridade) {
                case "UNCOMMON": return 1.15;
                case "RARE": return 1.3;
                case "EPIC": return 1.5;
                case "LEGENDARY": return 2.6;
                default: return 1.0;
            }
        }

        String corRaridade() {
            switch (raridade) {
                case "UNCOMMON": return "§a";
                case "RARE": return "§9";
                case "EPIC": return "§5";
                case "LEGENDARY": return "§6";
                default: return "§f";
            }
        }
    }

    @SuppressWarnings("deprecation")
    private final NamespacedKey chaveEntidade = new NamespacedKey("babypets", "pet_entity");
    @SuppressWarnings("deprecation")
    private final NamespacedKey chaveDono = new NamespacedKey("babypets", "pet_owner");

    boolean ehPet(Entity e) {
        return e != null && e.getPersistentDataContainer().has(chaveEntidade, PersistentDataType.BYTE);
    }

    UUID donoDoPet(Entity e) {
        if (e == null) return null;
        String s = e.getPersistentDataContainer().get(chaveDono, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    boolean ehPetDe(Entity e, Player p) {
        UUID d = donoDoPet(e);
        return d != null && d.equals(p.getUniqueId());
    }

    /** Pet ativo (spawnado) do jogador, ou null. */
    PetInfo petAtivo(Player p) {
        Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
        if (bp == null || !bp.isEnabled()) return null;
        try {
            Object gerente = bp.getClass().getMethod("getPetManager").invoke(bp);
            Object inst = gerente.getClass().getMethod("getActivePet", UUID.class).invoke(gerente, p.getUniqueId());
            if (inst == null) return null;
            Class<?> ic = inst.getClass();
            UUID entUuid = (UUID) ic.getMethod("getEntityUuid").invoke(inst);
            if (entUuid == null) return null;
            Entity ent = Bukkit.getEntity(entUuid);
            if (!(ent instanceof LivingEntity) || !ent.isValid()) return null;
            int nivel = (Integer) ic.getMethod("getLevel").invoke(inst);
            String tipoId = (String) ic.getMethod("getPetTypeId").invoke(inst);
            String raridade = "COMMON";
            String nome = null;
            Object tipos = bp.getClass().getMethod("getPetTypes").invoke(bp);
            if (tipos instanceof Map<?, ?> mapa) {
                Object tipo = mapa.get(tipoId);
                if (tipo != null) {
                    Object r = tipo.getClass().getMethod("getRarity").invoke(tipo);
                    if (r instanceof Enum<?> en) raridade = en.name();
                    Method dn = ic.getMethod("getDisplayName", tipo.getClass());
                    Object n = dn.invoke(inst, tipo);
                    if (n != null) nome = cores(n.toString());
                }
            }
            if (nome == null || nome.isBlank()) nome = tipoId == null ? "Pet" : tipoId;
            PetInfo pi = new PetInfo((LivingEntity) ent, nivel, raridade, nome);
            try {
                pi.id = (Integer) ic.getMethod("getDatabaseId").invoke(inst);
            } catch (Throwable ignored) {
            }
            return pi;
        } catch (Throwable t) {
            return petPorPerto(p);
        }
    }

    /**
     * Da XP ao pet ativo do jogador (sobe de nivel na hora, ate o maximo do BabyPets). Devolve o nivel novo
     * (ou -1 se nao deu; o mesmo nivel se so somou XP).
     */
    int darXp(Player p, double xp) {
        Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
        if (bp == null || !bp.isEnabled() || xp <= 0) return -1;
        try {
            Object gerente = bp.getClass().getMethod("getPetManager").invoke(bp);
            Object inst = gerente.getClass().getMethod("getActivePet", UUID.class).invoke(gerente, p.getUniqueId());
            if (inst == null) return -1;
            Class<?> ic = inst.getClass();
            int nivel = ((Number) ic.getMethod("getLevel").invoke(inst)).intValue();
            double atual = ((Number) ic.getMethod("getXp").invoke(inst)).doubleValue();
            int max = (Integer) bp.getClass().getMethod("getMaxLevel").invoke(bp);
            Method precisa = gerente.getClass().getMethod("getXpForLevel", int.class);
            double x = atual + xp;
            int novo = nivel;
            while (novo < max) {
                double falta = ((Number) precisa.invoke(gerente, novo + 1)).doubleValue();
                if (falta <= 0) break;
                if (x < falta) break;
                x -= falta;
                novo++;
            }
            if (novo >= max) x = 0;
            if (novo != nivel) gerente.getClass().getMethod("setLevel", Player.class, ic, int.class).invoke(gerente, p, inst, novo);
            ic.getMethod("setXp", double.class).invoke(inst, x);
            Object db = bp.getClass().getMethod("getDatabaseManager").invoke(bp);
            db.getClass().getMethod("updatePetAsync", ic).invoke(db, inst);
            return novo;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Plano B se a reflexao falhar: acha um pet do jogador por perto. */
    private PetInfo petPorPerto(Player p) {
        for (Entity e : p.getNearbyEntities(24, 12, 24)) {
            if (e instanceof LivingEntity le && ehPetDe(e, p)) {
                return new PetInfo(le, 1, "COMMON", "Pet");
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ colecao (estrelas e fusao, 16/11)

    /** Um pet da colecao do jogador (do BabyPets). inst = o PetInstance dele. */
    record PetDado(Object inst, int id, String tipo, String nome, int nivel, long obtido, boolean selecionado, Material icone, String raridade) {}

    private static Plugin bp() {
        Plugin bp = Bukkit.getPluginManager().getPlugin("BabyPets");
        return bp != null && bp.isEnabled() ? bp : null;
    }

    private static Object gerente(Plugin bp) throws Exception {
        return bp.getClass().getMethod("getPetManager").invoke(bp);
    }

    /** Todos os pets do jogador (vazia se o BabyPets nao responder). */
    List<PetDado> colecao(Player p) {
        List<PetDado> l = new ArrayList<>();
        Plugin bp = bp();
        if (bp == null) return l;
        try {
            Object g = gerente(bp);
            Object lista = g.getClass().getMethod("getPlayerPets", UUID.class).invoke(g, p.getUniqueId());
            Object tipos = bp.getClass().getMethod("getPetTypes").invoke(bp);
            if (!(lista instanceof List<?> ls)) return l;
            for (Object inst : new ArrayList<>(ls)) {
                if (inst == null) continue;
                Class<?> ic = inst.getClass();
                int id = ((Number) ic.getMethod("getDatabaseId").invoke(inst)).intValue();
                if (id <= 0) continue;
                String tipoId = (String) ic.getMethod("getPetTypeId").invoke(inst);
                int nivel = ((Number) ic.getMethod("getLevel").invoke(inst)).intValue();
                long obtido = ((Number) ic.getMethod("getObtainedAt").invoke(inst)).longValue();
                boolean sel = (Boolean) ic.getMethod("isSelected").invoke(inst);
                Material icone = Material.EGG;
                String rar = "COMMON";
                String nome = null;
                Object tipo = tipos instanceof Map<?, ?> m ? m.get(tipoId) : null;
                if (tipo != null) {
                    Object i2 = tipo.getClass().getMethod("getIcon").invoke(tipo);
                    if (i2 instanceof Material mm && mm.isItem()) icone = mm;
                    Object r = tipo.getClass().getMethod("getRarity").invoke(tipo);
                    if (r instanceof Enum<?> en) rar = en.name();
                    Object n = ic.getMethod("getDisplayName", tipo.getClass()).invoke(inst, tipo);
                    if (n != null) nome = cores(n.toString());
                }
                if (nome == null || nome.isBlank()) nome = tipoId == null ? "Pet" : tipoId;
                l.add(new PetDado(inst, id, tipoId == null ? "" : tipoId, nome, nivel, obtido, sel, icone, rar));
            }
        } catch (Throwable ignored) {
        }
        return l;
    }

    /** Id do pet ativo (chamado) do jogador, mesmo sem achar a entidade; -1 = nenhum. */
    int idAtivo(Player p) {
        Plugin bp = bp();
        if (bp == null) return -1;
        try {
            Object g = gerente(bp);
            Object inst = g.getClass().getMethod("getActivePet", UUID.class).invoke(g, p.getUniqueId());
            if (inst == null) return -1;
            return ((Number) inst.getClass().getMethod("getDatabaseId").invoke(inst)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Apaga um pet da colecao (o mesmo que o "Apagar pet" do BabyPets). */
    boolean apagar(Player p, Object inst) {
        Plugin bp = bp();
        if (bp == null) return false;
        try {
            Object g = gerente(bp);
            g.getClass().getMethod("deletePet", UUID.class, inst.getClass()).invoke(g, p.getUniqueId(), inst);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Muda o nivel de um pet (o BabyPets salva, atualiza o bonus e o tamanho). */
    boolean mudarNivel(Player p, Object inst, int nivel) {
        Plugin bp = bp();
        if (bp == null) return false;
        try {
            Object g = gerente(bp);
            g.getClass().getMethod("setLevel", Player.class, inst.getClass(), int.class).invoke(g, p, inst, nivel);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Chave do bonus de atributo que o BabyPets coloca no jogador (ex.: vida extra do pet). */
    org.bukkit.NamespacedKey chaveAtributo() {
        Plugin bp = bp();
        if (bp == null) return null;
        try {
            Object g = gerente(bp);
            return (org.bukkit.NamespacedKey) g.getClass().getMethod("getPetAttributeKey").invoke(g);
        } catch (Throwable t) {
            return null;
        }
    }

    private Set<Attribute> atributos;

    /** Atributos que algum pet da (vida, velocidade...). */
    Set<Attribute> atributosDosPets() {
        if (atributos != null && !atributos.isEmpty()) return atributos;
        Set<Attribute> s = new HashSet<>();
        Plugin bp = bp();
        if (bp == null) return s;
        try {
            Object tipos = bp.getClass().getMethod("getPetTypes").invoke(bp);
            if (tipos instanceof Map<?, ?> m)
                for (Object tipo : m.values()) {
                    Object l = tipo.getClass().getMethod("getAttributeBonuses").invoke(tipo);
                    if (l instanceof List<?> bs)
                        for (Object b : bs) {
                            Object a = b.getClass().getMethod("getAttribute").invoke(b);
                            if (a instanceof Attribute at) s.add(at);
                        }
                }
        } catch (Throwable ignored) {
        }
        atributos = s;
        return s;
    }

    static String cores(String s) {
        return s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }
}
