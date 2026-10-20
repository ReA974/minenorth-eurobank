package com.minenorth_eurobank.safe;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Contenu et propriétaire d'un coffre-fort. Volontairement pas un {@code Container} : les entonnoirs et autres
 * automatismes ne peuvent ni le vider ni le remplir, seul le propriétaire l'ouvre.
 */
public class SafeBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName = "";
    private final SimpleContainer items;

    public SafeBlockEntity(BlockPos pos, BlockState state) {
        super(ModSafe.SAFE_BE.get(), pos, state);
        SafeTier tier = state.getBlock() instanceof SafeBlock b ? b.tier() : SafeTier.BASIC;
        this.items = new SimpleContainer(tier.slots);
        this.items.addListener(c -> setChanged());
    }

    @Nullable
    public UUID owner() { return owner; }

    public String ownerName() { return ownerName; }

    public SimpleContainer items() { return items; }

    public void setOwner(UUID id, String name) {
        this.owner = id;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    public boolean isOwner(UUID id) {
        return owner != null && owner.equals(id);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.put("Items", items.createTag());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        items.fromTag(tag.getList("Items", Tag.TAG_COMPOUND));
    }
}
