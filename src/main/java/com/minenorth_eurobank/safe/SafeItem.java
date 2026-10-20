package com.minenorth_eurobank.safe;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** Objet « coffre-fort » : l'infobulle montre le niveau, la capacité et le propriétaire. */
public class SafeItem extends BlockItem {
    private final SafeTier tier;

    public SafeItem(Block block, SafeTier tier) {
        super(block, new Item.Properties().stacksTo(1));
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(tier.slots + " emplacements").withStyle(ChatFormatting.GRAY));
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        if (be != null && !be.getString("OwnerName").isEmpty()) {
            tip.add(Component.literal("Propriétaire : " + be.getString("OwnerName")).withStyle(ChatFormatting.AQUA));
        }
        tip.add(Component.literal("Seul son propriétaire peut l'ouvrir.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
