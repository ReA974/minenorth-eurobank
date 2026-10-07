package com.minenorth_eurobank.braquage.item;

import com.minenorth_eurobank.braquage.bank.MainBankData;
import com.minenorth_eurobank.braquage.bank.MainBankManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;

public class HackerItem extends Item {
    public HackerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.PASS;

        MainBankData data = MainBankData.get(level);
        MainBankManager.HackType type = null;
        if (data.isGrilleConfigured() && data.getGrilleHackDimension().equals(level.dimension()) && data.getGrilleHackBlock().equals(context.getClickedPos())) {
            type = MainBankManager.HackType.GRILLE;
        } else if (data.isConfigured() && data.getHackDimension().equals(level.dimension()) && data.getHackBlock().equals(context.getClickedPos())) {
            type = MainBankManager.HackType.COFFRE;
        }

        if (type == null) return InteractionResult.PASS;

        boolean alreadyHacked = type == MainBankManager.HackType.GRILLE ? data.isGrilleHacked() : data.isHacked();
        if (alreadyHacked) {
            player.displayClientMessage(Component.translatable("message.minenorth_eurobank.hack_already_done"), true);
            return InteractionResult.CONSUME;
        }
        if (MainBankManager.start(player, level, type)) {
            level.playSound(null, context.getClickedPos(), ModSounds.HACK_START.get(), player.getSoundSource(), 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.minenorth_eurobank.hack_started"), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.minenorth_eurobank.hack_unavailable"), true);
        return InteractionResult.CONSUME;
    }
}
