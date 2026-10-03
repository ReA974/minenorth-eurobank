package com.minenorth.eurobank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Un seul compte par joueur (clé = UUID). Solde en centimes. Sauvegardé avec le monde. */
public class BankData extends SavedData {
    private static final String NAME = "eurobank_accounts";
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public static BankData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BankData::load, BankData::new, NAME);
    }

    public static BankData load(CompoundTag tag) {
        BankData d = new BankData();
        ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UUID id = t.getUUID("id");
            d.balances.put(id, t.getLong("cents"));
            if (t.contains("name")) d.names.put(id, t.getString("name"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        balances.forEach((id, c) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putLong("cents", c);
            String n = names.get(id);
            if (n != null) t.putString("name", n);
            list.add(t);
        });
        tag.put("accounts", list);
        return tag;
    }

    public boolean has(UUID id) { return balances.containsKey(id); }

    public void open(UUID id) {
        if (balances.putIfAbsent(id, 0L) == null) setDirty();
    }

    public long balance(UUID id) { return balances.getOrDefault(id, 0L); }

    public void add(UUID id, long delta) {
        balances.merge(id, delta, Long::sum);
        setDirty();
    }

    public void set(UUID id, long v) {
        balances.put(id, v);
        setDirty();
    }

    /** Mémorise le pseudo (pour la liste admin et les virements par nom). */
    public void rename(UUID id, String name) {
        if (name != null && !name.equals(names.get(id))) {
            names.put(id, name);
            setDirty();
        }
    }

    public String name(UUID id) {
        String n = names.get(id);
        return n != null ? n : id.toString().substring(0, 8);
    }

    public UUID findByName(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name) && balances.containsKey(e.getKey())) return e.getKey();
        }
        return null;
    }

    public Map<UUID, Long> all() { return Collections.unmodifiableMap(balances); }

    public long total() { return balances.values().stream().mapToLong(Long::longValue).sum(); }
}
