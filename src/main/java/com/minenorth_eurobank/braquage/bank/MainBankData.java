package com.minenorth_eurobank.braquage.bank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;

public class MainBankData extends SavedData {
    private static final String DATA_NAME = "minenorthbanque_braque_main_bank";
    private final List<SavedBlock> blocks = new ArrayList<>();
    private final List<SavedBlock> grilleBlocks = new ArrayList<>();
    private BlockPos hackBlock;
    private ResourceKey<Level> hackDimension;
    private boolean hacked;
    private BlockPos grilleHackBlock;
    private ResourceKey<Level> grilleHackDimension;
    private boolean grilleHacked;
    /** Incrémenté à chaque /banquereset : un coffre en retard (chunk non chargé) se remet à zéro à son chargement. */
    private int vaultGeneration;

    public int vaultGeneration() { return vaultGeneration; }

    public int nextVaultGeneration() {
        vaultGeneration++;
        setDirty();
        return vaultGeneration;
    }

    public static MainBankData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                tag -> MainBankData.load(overworld, tag), MainBankData::new, DATA_NAME);
    }

    private static MainBankData load(ServerLevel level, CompoundTag tag) {
        MainBankData data = new MainBankData();
        ListTag list = tag.getList("Blocks", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            CompoundTag stateTag = entry.getCompound("State");
            data.blocks.add(new SavedBlock(key, pos, stateTag.copy(), entry.contains("BlockEntity") ? entry.getCompound("BlockEntity").copy() : null));
        }
        if (tag.contains("HackBlock")) data.hackBlock = BlockPos.of(tag.getLong("HackBlock"));
        if (tag.contains("HackDimension")) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("HackDimension"));
            if (dim != null) data.hackDimension = ResourceKey.create(Registries.DIMENSION, dim);
        }
        data.hacked = tag.getBoolean("Hacked");
        ListTag grilleList = tag.getList("GrilleBlocks", 10);
        for (int i = 0; i < grilleList.size(); i++) {
            CompoundTag entry = grilleList.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            CompoundTag stateTag = entry.getCompound("State");
            data.grilleBlocks.add(new SavedBlock(key, pos, stateTag.copy(), entry.contains("BlockEntity") ? entry.getCompound("BlockEntity").copy() : null));
        }
        if (tag.contains("GrilleHackBlock")) data.grilleHackBlock = BlockPos.of(tag.getLong("GrilleHackBlock"));
        if (tag.contains("GrilleHackDimension")) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("GrilleHackDimension"));
            if (dim != null) data.grilleHackDimension = ResourceKey.create(Registries.DIMENSION, dim);
        }
        data.grilleHacked = tag.getBoolean("GrilleHacked");
        data.vaultGeneration = tag.getInt("VaultGeneration");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SavedBlock block : blocks) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", block.dimension.location().toString());
            entry.putLong("Pos", block.pos.asLong());
            entry.put("State", block.stateTag.copy());
            if (block.blockEntityTag != null) entry.put("BlockEntity", block.blockEntityTag.copy());
            list.add(entry);
        }
        tag.put("Blocks", list);
        if (hackBlock != null) tag.putLong("HackBlock", hackBlock.asLong());
        if (hackDimension != null) tag.putString("HackDimension", hackDimension.location().toString());
        tag.putBoolean("Hacked", hacked);
        ListTag grilleList = new ListTag();
        for (SavedBlock block : grilleBlocks) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", block.dimension.location().toString());
            entry.putLong("Pos", block.pos.asLong());
            entry.put("State", block.stateTag.copy());
            if (block.blockEntityTag != null) entry.put("BlockEntity", block.blockEntityTag.copy());
            grilleList.add(entry);
        }
        tag.put("GrilleBlocks", grilleList);
        if (grilleHackBlock != null) tag.putLong("GrilleHackBlock", grilleHackBlock.asLong());
        if (grilleHackDimension != null) tag.putString("GrilleHackDimension", grilleHackDimension.location().toString());
        tag.putBoolean("GrilleHacked", grilleHacked);
        tag.putInt("VaultGeneration", vaultGeneration);
        return tag;
    }

    public boolean addBlock(ServerLevel level, BlockPos pos) {
        // Le bloc déclencheur du piratage doit rester séparé des blocs du coffre.
        if (hackBlock != null && hackDimension != null
                && hackDimension.equals(level.dimension()) && hackBlock.equals(pos)) {
            return false;
        }
        for (SavedBlock b : blocks) {
            if (b.dimension.equals(level.dimension()) && b.pos.equals(pos)) return false;
        }
        for (SavedBlock b : grilleBlocks) {
            if (b.dimension.equals(level.dimension()) && b.pos.equals(pos)) return false;
        }
        if (grilleHackBlock != null && grilleHackDimension != null && grilleHackDimension.equals(level.dimension()) && grilleHackBlock.equals(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        CompoundTag stateTag = NbtUtils.writeBlockState(state);
        CompoundTag beTag = null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) beTag = be.saveWithFullMetadata();
        blocks.add(new SavedBlock(level.dimension(), pos.immutable(), stateTag, beTag));
        setDirty();
        return true;
    }

    public boolean removeBlock(BlockPos pos, ServerLevel level) {
        boolean removed = blocks.removeIf(b -> b.dimension.equals(level.dimension()) && b.pos.equals(pos));
        if (removed) setDirty();
        return removed;
    }

    public void clearBlocks() {
        blocks.clear();
        hackBlock = null;
        hackDimension = null;
        hacked = false;
        setDirty();
    }

    public void clearGrilleBlocks() {
        grilleBlocks.clear();
        grilleHackBlock = null;
        grilleHackDimension = null;
        grilleHacked = false;
        setDirty();
    }

    public boolean contains(BlockPos pos, ServerLevel level) {
        return blocks.stream().anyMatch(b -> b.dimension.equals(level.dimension()) && b.pos.equals(pos));
    }

    public boolean containsGrille(BlockPos pos, ServerLevel level) {
        return grilleBlocks.stream().anyMatch(b -> b.dimension.equals(level.dimension()) && b.pos.equals(pos));
    }

    public List<SavedBlock> getBlocks() { return List.copyOf(blocks); }
    public List<SavedBlock> getGrilleBlocks() { return List.copyOf(grilleBlocks); }
    public BlockPos getHackBlock() { return hackBlock; }
    public BlockPos getGrilleHackBlock() { return grilleHackBlock; }
    public ResourceKey<Level> getHackDimension() { return hackDimension; }
    public ResourceKey<Level> getGrilleHackDimension() { return grilleHackDimension; }
    public boolean isHacked() { return hacked; }
    public boolean isGrilleHacked() { return grilleHacked; }

    public void setHackBlock(ServerLevel level, BlockPos pos) {
        hackBlock = pos.immutable();
        hackDimension = level.dimension();
        setDirty();
    }

    public void clearHackBlock() { hackBlock = null; hackDimension = null; setDirty(); }

    public boolean addGrilleBlock(ServerLevel level, BlockPos pos) {
        if (hackBlock != null && hackDimension != null && hackDimension.equals(level.dimension()) && hackBlock.equals(pos)) return false;
        if (grilleHackBlock != null && grilleHackDimension != null && grilleHackDimension.equals(level.dimension()) && grilleHackBlock.equals(pos)) return false;
        for (SavedBlock b : grilleBlocks) if (b.dimension.equals(level.dimension()) && b.pos.equals(pos)) return false;
        for (SavedBlock b : blocks) if (b.dimension.equals(level.dimension()) && b.pos.equals(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        CompoundTag stateTag = NbtUtils.writeBlockState(state);
        CompoundTag beTag = null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) beTag = be.saveWithFullMetadata();
        grilleBlocks.add(new SavedBlock(level.dimension(), pos.immutable(), stateTag, beTag));
        setDirty();
        return true;
    }

    public boolean removeGrilleBlock(BlockPos pos, ServerLevel level) {
        boolean removed = grilleBlocks.removeIf(b -> b.dimension.equals(level.dimension()) && b.pos.equals(pos));
        if (removed) setDirty();
        return removed;
    }

    public void setGrilleHackBlock(ServerLevel level, BlockPos pos) {
        grilleHackBlock = pos.immutable();
        grilleHackDimension = level.dimension();
        setDirty();
    }

    public void clearGrilleHackBlock() { grilleHackBlock = null; grilleHackDimension = null; setDirty(); }

    public boolean isConfigured() { return !blocks.isEmpty() && hackBlock != null && hackDimension != null; }
    public boolean isGrilleConfigured() { return !grilleBlocks.isEmpty() && grilleHackBlock != null && grilleHackDimension != null; }

    public void hideBank(ServerLevel level) {
        if (!isConfigured()) return;
        for (SavedBlock b : blocks) {
            ServerLevel target = level.getServer().getLevel(b.dimension);
            if (target != null && !target.getBlockState(b.pos).isAir()) {
                target.removeBlock(b.pos, false);
            }
        }
        hacked = true;
        setDirty();
    }

    public int restoreBank(ServerLevel level) {
        int restored = 0;
        for (SavedBlock b : blocks) {
            ServerLevel target = level.getServer().getLevel(b.dimension);
            if (target == null) continue;
            BlockState state;
            try {
                state = NbtUtils.readBlockState(target.registryAccess().lookupOrThrow(Registries.BLOCK), b.stateTag);
            } catch (Exception ex) {
                continue;
            }
            target.setBlock(b.pos, state, 3);
            if (b.blockEntityTag != null) {
                BlockEntity be = target.getBlockEntity(b.pos);
                if (be != null) {
                    be.load(b.blockEntityTag.copy());
                    be.setChanged();
                }
            }
            restored++;
        }
        hacked = false;
        setDirty();
        return restored;
    }


    public void hideGrille(ServerLevel level) {
        if (!isGrilleConfigured()) return;
        for (SavedBlock b : grilleBlocks) {
            ServerLevel target = level.getServer().getLevel(b.dimension);
            if (target != null && !target.getBlockState(b.pos).isAir()) target.removeBlock(b.pos, false);
        }
        grilleHacked = true;
        setDirty();
    }

    public int restoreGrille(ServerLevel level) {
        int restored = 0;
        for (SavedBlock b : grilleBlocks) {
            ServerLevel target = level.getServer().getLevel(b.dimension);
            if (target == null) continue;
            BlockState state;
            try {
                state = NbtUtils.readBlockState(target.registryAccess().lookupOrThrow(Registries.BLOCK), b.stateTag);
            } catch (Exception ex) {
                continue;
            }
            target.setBlock(b.pos, state, 3);
            if (b.blockEntityTag != null) {
                BlockEntity be = target.getBlockEntity(b.pos);
                if (be != null) { be.load(b.blockEntityTag.copy()); be.setChanged(); }
            }
            restored++;
        }
        grilleHacked = false;
        setDirty();
        return restored;
    }

    public record SavedBlock(ResourceKey<Level> dimension, BlockPos pos, CompoundTag stateTag, CompoundTag blockEntityTag) {}
}
