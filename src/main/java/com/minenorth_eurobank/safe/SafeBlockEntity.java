package com.minenorth_eurobank.safe;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Propriétaire d'un coffre-fort. Le contenu n'est pas ici mais dans {@link SafeStorage} (par propriétaire et par niveau) :
 * le bloc n'est pas un {@code Container}, donc les entonnoirs ne peuvent ni le vider ni le remplir.
 */
public class SafeBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName = "";
    /** Contenu enregistré dans le bloc par une ancienne version : versé dans le stockage du propriétaire à la première ouverture. */
    private ListTag legacyItems;

    public SafeBlockEntity(BlockPos pos, BlockState state) {
        super(ModSafe.SAFE_BE.get(), pos, state);
    }

    @Nullable
    public UUID owner() { return owner; }

    public String ownerName() { return ownerName; }

    public void setOwner(UUID id, String name) {
        this.owner = id;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    public boolean isOwner(UUID id) {
        return owner != null && owner.equals(id);
    }

    @Nullable
    public ListTag takeLegacyItems() {
        ListTag l = legacyItems;
        if (l != null) {
            legacyItems = null;
            setChanged();
        }
        return l;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        legacyItems = items.isEmpty() ? null : items;
    }
}
