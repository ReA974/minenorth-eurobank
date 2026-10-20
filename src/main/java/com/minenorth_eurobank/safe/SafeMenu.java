package com.minenorth_eurobank.safe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu du coffre-fort : grille adaptée au niveau (10, 23 ou 30 emplacements) puis inventaire du joueur. */
public class SafeMenu extends AbstractContainerMenu {
    public static final int WIDTH = 194;
    private static final int TOP = 28;

    private final Container container;
    private final SafeTier tier;
    private final ContainerLevelAccess access;

    public SafeMenu(int id, Inventory inv, Container container, SafeTier tier, ContainerLevelAccess access) {
        super(ModSafe.SAFE_MENU.get(), id);
        this.container = container;
        this.tier = tier;
        this.access = access;
        checkContainerSize(container, tier.slots);
        container.startOpen(inv.player);

        int gridX = (WIDTH - tier.cols * 18) / 2;
        for (int i = 0; i < tier.slots; i++) {
            addSlot(new Slot(container, i, gridX + (i % tier.cols) * 18, TOP + (i / tier.cols) * 18));
        }
        int invY = TOP + tier.rows() * 18 + 14;
        int invX = (WIDTH - 162) / 2;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, invX + c * 18, invY + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, invX + c * 18, invY + 58));
    }

    public SafeTier tier() { return tier; }

    public int guiHeight() {
        return TOP + tier.rows() * 18 + 14 + 58 + 18 + 8;
    }

    public int invLabelY() {
        return TOP + tier.rows() * 18 + 4;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < tier.slots) {
                if (!moveItemStackTo(stack, tier.slots, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, tier.slots, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof SafeBlock
                && level.getBlockEntity(pos) instanceof SafeBlockEntity be && be.isOwner(player.getUUID())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
