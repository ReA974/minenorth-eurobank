package com.minenorth_eurobank.safe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contenu des coffres-forts, rattaché au propriétaire et au niveau (comme un ender chest) : tous les coffres
 * d'un même niveau appartenant au même joueur ouvrent le même contenu, qu'on les casse, déplace ou repose.
 */
public class SafeStorage extends SavedData {
    private static final String NAME = "eurobank_safes";

    private final Map<UUID, SimpleContainer[]> safes = new HashMap<>();

    public static SafeStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SafeStorage::load, SafeStorage::new, NAME);
    }

    public SimpleContainer container(UUID owner, SafeTier tier) {
        SimpleContainer[] arr = safes.computeIfAbsent(owner, k -> new SimpleContainer[SafeTier.values().length]);
        int i = tier.ordinal();
        if (arr[i] == null) {
            arr[i] = new SimpleContainer(tier.slots);
            arr[i].addListener(c -> setDirty());
            setDirty();
        }
        return arr[i];
    }

    public static SafeStorage load(CompoundTag tag) {
        SafeStorage s = new SafeStorage();
        ListTag owners = tag.getList("owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag o = owners.getCompound(i);
            SimpleContainer[] arr = new SimpleContainer[SafeTier.values().length];
            for (SafeTier t : SafeTier.values()) {
                String key = "t" + t.ordinal();
                if (!o.contains(key)) continue;
                SimpleContainer c = new SimpleContainer(t.slots);
                c.fromTag(o.getList(key, Tag.TAG_COMPOUND));
                c.addListener(x -> s.setDirty());
                arr[t.ordinal()] = c;
            }
            s.safes.put(o.getUUID("id"), arr);
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag owners = new ListTag();
        safes.forEach((id, arr) -> {
            CompoundTag o = new CompoundTag();
            o.putUUID("id", id);
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] != null) o.put("t" + i, arr[i].createTag());
            }
            owners.add(o);
        });
        tag.put("owners", owners);
        return tag;
    }
}
