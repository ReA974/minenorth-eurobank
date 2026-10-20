package com.minenorth_eurobank.items;

import com.minenorth_eurobank.banker.TabletService;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Tablette du banquier : prêts, comptes, transactions, alertes et gel de comptes. Réservée aux banquiers nommés. */
public class BankerTabletItem extends Item {
    public BankerTabletItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (LoanService.isBanker(sp)) {
                TabletService.open(sp);
            } else {
                sp.sendSystemMessage(Component.literal("§c[Banque] Cette tablette est réservée aux banquiers."));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Réservée aux banquiers.").withStyle(ChatFormatting.GRAY));
    }
}
