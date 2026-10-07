package com.minenorth_eurobank.braquage.block.entity;

import com.minenorth_eurobank.braquage.block.BankVaultBlock;
import com.minenorth_eurobank.braquage.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BankVaultBlockEntity extends RandomizableContainerBlockEntity implements MenuProvider {
    private static final String LOOT_GENERATED_TAG = "MineNorthLootGenerated";
    private static final Set<BankVaultBlockEntity> LOADED_VAULTS = ConcurrentHashMap.newKeySet();
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private boolean lootGenerated = false;
    /** Numéro du dernier /banquereset appliqué à ce coffre (voir MainBankData.vaultGeneration). */
    private int generation;

    public BankVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANK_VAULT.get(), pos, state);
        LOADED_VAULTS.add(this);
    }

    /**
     * Remet à zéro les coffres chargés. Les coffres des chunks non chargés se remettront à zéro
     * d'eux-mêmes à leur prochain chargement (numéro de génération).
     */
    public static int resetLoadedVaults(int generation) {
        int count = 0;
        for (BankVaultBlockEntity vault : List.copyOf(LOADED_VAULTS)) {
            if (vault.level != null && !vault.isRemoved()) {
                vault.generation = generation;
                vault.resetVault();
                count++;
            } else {
                LOADED_VAULTS.remove(vault);
            }
        }
        return count;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        int current = com.minenorth_eurobank.braquage.bank.MainBankData.get(server).vaultGeneration();
        if (generation < current) {
            // Pas de setBlock pendant le chargement du chunk : on attend le tick suivant.
            server.getServer().execute(() -> {
                if (isRemoved() || level == null) return;
                generation = current;
                resetVault();
            });
        }
    }

    @Override
    public void setRemoved() {
        LOADED_VAULTS.remove(this);
        super.setRemoved();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.minenorth_eurobank.bank_vault");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        generateLootIfNeeded();
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
        lootGenerated = tag.getBoolean(LOOT_GENERATED_TAG);
        generation = tag.getInt("VaultGeneration");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
        tag.putBoolean(LOOT_GENERATED_TAG, lootGenerated);
        tag.putInt("VaultGeneration", generation);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (level != null) {
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.minenorth_eurobank.bank_vault");
    }

    public boolean isOpened() {
        return level != null && getBlockState().hasProperty(BankVaultBlock.OPEN) && getBlockState().getValue(BankVaultBlock.OPEN);
    }

    public boolean isBlocked() {
        return level != null && getBlockState().hasProperty(BankVaultBlock.BLOCKED) && getBlockState().getValue(BankVaultBlock.BLOCKED);
    }

    public void openVault() {
        if (level == null || level.isClientSide || isOpened() || isBlocked()) {
            return;
        }
        generateLootIfNeeded();
        level.setBlock(worldPosition, getBlockState().setValue(BankVaultBlock.OPEN, true).setValue(BankVaultBlock.BLOCKED, false), 3);
        setChanged();
    }

    public void resetVault() {
        if (level == null || level.isClientSide) {
            return;
        }
        clearContent();
        lootGenerated = false;
        level.setBlock(worldPosition, getBlockState().setValue(BankVaultBlock.OPEN, false).setValue(BankVaultBlock.BLOCKED, false), 3);
        setChanged();
    }

    public void blockVault() {
        if (level == null || level.isClientSide || isOpened()) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(BankVaultBlock.OPEN, false).setValue(BankVaultBlock.BLOCKED, true), 3);
        setChanged();
    }

    private void generateLootIfNeeded() {
        if (lootGenerated || level == null || level.isClientSide) {
            return;
        }

        lootGenerated = true;
        RandomSource random = level.getRandom();

        for (String entry : ModConfig.BANK_VAULT_LOOT.get()) {
            LootEntry lootEntry = LootEntry.parse(entry);
            if (lootEntry == null) {
                continue;
            }

            if (random.nextDouble() * 100.0D >= lootEntry.chance) {
                continue;
            }

            Item item = ForgeRegistries.ITEMS.getValue(lootEntry.itemId);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }

            int amount = lootEntry.minAmount;
            if (lootEntry.maxAmount > lootEntry.minAmount) {
                amount += random.nextInt(lootEntry.maxAmount - lootEntry.minAmount + 1);
            }

            addItemToRandomSlots(new ItemStack(item, amount), random);
        }

        setChanged();
    }

    private void addItemToRandomSlots(ItemStack stack, RandomSource random) {
        while (!stack.isEmpty()) {
            List<Integer> emptySlots = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).isEmpty()) {
                    emptySlots.add(i);
                }
            }
            if (emptySlots.isEmpty()) {
                return;
            }

            int slot = emptySlots.get(random.nextInt(emptySlots.size()));
            int amount = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack placed = stack.copy();
            placed.setCount(amount);
            items.set(slot, placed);
            stack.shrink(amount);
        }
    }

    private static class LootEntry {
        private final ResourceLocation itemId;
        private final double chance;
        private final int minAmount;
        private final int maxAmount;

        private LootEntry(ResourceLocation itemId, double chance, int minAmount, int maxAmount) {
            this.itemId = itemId;
            this.chance = chance;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        private static LootEntry parse(String value) {
            try {
                String[] parts = value.split("\\|", -1);
                if (parts.length != 3) return null;

                ResourceLocation itemId = ResourceLocation.tryParse(parts[0].trim());
                if (itemId == null) return null;

                double chance = Double.parseDouble(parts[1].trim());
                String[] quantity = parts[2].trim().split("-", -1);
                if (quantity.length != 2) return null;

                int min = Integer.parseInt(quantity[0].trim());
                int max = Integer.parseInt(quantity[1].trim());
                if (chance < 0.0D || chance > 100.0D || min < 1 || max < min) return null;

                return new LootEntry(itemId, chance, min, max);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
