package com.minenorth.eurobank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

import java.util.List;

public class MoneyItem extends Item {
    public final long cents;

    public MoneyItem(Properties props, long cents) {
        super(props);
        this.cents = cents;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(Money.format(cents)).withStyle(ChatFormatting.GRAY));
    }
}
