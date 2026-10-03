package com.eurobank;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public class BankCardItem extends Item {
    public BankCardItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack create(ServerPlayer p) {
        ItemStack s = new ItemStack(ModItems.CARD.get());
        CompoundTag t = s.getOrCreateTag();
        t.putUUID("Owner", p.getUUID());
        t.putString("OwnerName", p.getGameProfile().getName());
        return s;
    }

    @Nullable
    public static UUID owner(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.hasUUID("Owner") ? t.getUUID("Owner") : null;
    }

    public static boolean hasOwnCard(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof BankCardItem && p.getUUID().equals(owner(s))) return true;
        }
        return false;
    }

    public static boolean hasAnyCard(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof BankCardItem) return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("OwnerName")) {
            tip.add(Component.literal("Titulaire : " + t.getString("OwnerName")).withStyle(ChatFormatting.GRAY));
        }
    }
}
