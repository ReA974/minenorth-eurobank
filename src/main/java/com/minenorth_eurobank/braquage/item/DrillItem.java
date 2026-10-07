package com.minenorth_eurobank.braquage.item;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.braquage.block.BankVaultBlock;
import com.minenorth_eurobank.braquage.block.entity.BankVaultBlockEntity;
import com.minenorth_eurobank.braquage.tags.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;

import java.util.function.Consumer;

public class DrillItem extends Item implements GeoItem {
    private static final String TARGET_X = "DrillTargetX";
    private static final String TARGET_Y = "DrillTargetY";
    private static final String TARGET_Z = "DrillTargetZ";
    private static final String START_X = "DrillStartX";
    private static final String START_Y = "DrillStartY";
    private static final String START_Z = "DrillStartZ";

    public enum DrillLevel {
        I(1, 120, 300, "I", "textures/item/drill.png", "animation.drill.spin"),
        II(2, 80, 500, "II", "textures/item/drill_mk2.png", "animation.drill.spin_fast"),
        III(3, 50, 800, "III", "textures/item/drill_mk3.png", "animation.drill.spin_ultra");

        public final int number;
        public final int drillTicks;
        public final int durability;
        public final String label;
        public final String texture;
        public final String animation;

        DrillLevel(int number, int drillTicks, int durability, String label, String texture, String animation) {
            this.number = number;
            this.drillTicks = drillTicks;
            this.durability = durability;
            this.label = label;
            this.texture = texture;
            this.animation = animation;
        }
    }

    private final DrillLevel drillLevel;
    private final RawAnimation spinAnimation;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DrillItem(DrillLevel drillLevel, Item.Properties properties) {
        super(properties.stacksTo(1).durability(drillLevel.durability));
        this.drillLevel = drillLevel;
        this.spinAnimation = RawAnimation.begin().thenLoop(drillLevel.animation);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public DrillLevel getDrillLevel() {
        return drillLevel;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return drillLevel.drillTicks;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null) return InteractionResult.PASS;

        BlockState state = level.getBlockState(pos);
        if (!state.is(ModTags.DRILLABLE_BLOCKS)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide && state.getBlock() instanceof BankVaultBlock
                && level.getBlockEntity(pos) instanceof BankVaultBlockEntity vault) {
            if (vault.isBlocked()) {
                player.displayClientMessage(Component.translatable("message.minenorth_eurobank.vault_blocked"), true);
                return InteractionResult.FAIL;
            }
            if (vault.isOpened()) {
                player.displayClientMessage(Component.translatable("message.minenorth_eurobank.vault_already_open"), true);
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            if (stack.isDamaged() && stack.getDamageValue() >= stack.getMaxDamage()) {
                player.displayClientMessage(Component.translatable("message.minenorth_eurobank.drill_empty"), true);
                return InteractionResult.FAIL;
            }
            stack.getOrCreateTag().putInt(TARGET_X, pos.getX());
            stack.getOrCreateTag().putInt(TARGET_Y, pos.getY());
            stack.getOrCreateTag().putInt(TARGET_Z, pos.getZ());
            stack.getOrCreateTag().putDouble(START_X, player.getX());
            stack.getOrCreateTag().putDouble(START_Y, player.getY());
            stack.getOrCreateTag().putDouble(START_Z, player.getZ());
            if (player instanceof ServerPlayer serverPlayer) {
                triggerAnim(serverPlayer, GeoItem.getOrAssignId(stack, (ServerLevel) level), "Drill", "spin");
            }
            level.playSound(null, pos, ModSounds.DRILL_START.get(), player.getSoundSource(), 0.8F, 0.92F + drillLevel.number * 0.06F);
        }

        player.startUsingItem(context.getHand());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;

        if (!level.isClientSide) {
            BlockPos target = getTarget(stack);
            if (target == null || !level.getBlockState(target).is(ModTags.DRILLABLE_BLOCKS)) {
                cancelDrilling(player, stack, level, "message.minenorth_eurobank.drill_cancelled");
                return;
            }

            if (hasPlayerMoved(stack, player)) {
                cancelDrilling(player, stack, level, "message.minenorth_eurobank.drill_cancelled");
                return;
            }

            if (level.getBlockEntity(target) instanceof BankVaultBlockEntity vault) {
                if (vault.isBlocked()) {
                    cancelDrilling(player, stack, level, "message.minenorth_eurobank.vault_blocked");
                    return;
                }
                if (vault.isOpened()) {
                    cancelDrilling(player, stack, level, "message.minenorth_eurobank.vault_already_open");
                    return;
                }
            }

            if (remainingUseDuration % 6 == 0 && level instanceof ServerLevel serverLevel) {
                double x = target.getX() + 0.5D;
                double y = target.getY() + 0.5D;
                double z = target.getZ() + 0.5D;
                serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, 2, 0.18, 0.18, 0.18, 0.04);
                serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.10, 0.10, 0.10, 0.01);
            }

            if (remainingUseDuration % 10 == 0) {
                int elapsed = drillLevel.drillTicks - remainingUseDuration;
                int percent = Math.max(0, Math.min(100, (int) ((elapsed / (double) drillLevel.drillTicks) * 100.0D)));
                player.displayClientMessage(Component.translatable("message.minenorth_eurobank.drilling", percent), true);
            }

            if (remainingUseDuration <= 1) {
                finishDrilling(player, stack, target, level);
                player.stopUsingItem();
            }
        } else if (remainingUseDuration % 12 == 0) {
            // Le son de boucle dure ~0,65 s : on le relance juste avant sa fin pour un perçage continu.
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), ModSounds.DRILL_LOOP.get(), player.getSoundSource(), 0.62F, 0.95F + drillLevel.number * 0.05F, false);
        }
    }

    private void finishDrilling(Player player, ItemStack stack, BlockPos target, Level level) {
        BlockState state = level.getBlockState(target);
        if (!state.is(ModTags.DRILLABLE_BLOCKS)) {
            clearTarget(stack);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (state.getBlock() instanceof BankVaultBlock && level.getBlockEntity(target) instanceof BankVaultBlockEntity vault) {
                if (vault.isOpened()) {
                    player.displayClientMessage(Component.translatable("message.minenorth_eurobank.vault_already_open"), true);
                } else {
                    vault.openVault();
                    var provider = level.getBlockState(target).getMenuProvider(level, target);
                    if (provider != null) {
                        NetworkHooks.openScreen(serverPlayer, provider, target);
                        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                        player.displayClientMessage(Component.translatable("message.minenorth_eurobank.vault_opened", drillLevel.label), true);
                    }
                }
            } else {
                var provider = state.getMenuProvider(level, target);
                if (provider != null) {
                    NetworkHooks.openScreen(serverPlayer, provider, target);
                    stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                    player.displayClientMessage(Component.translatable("message.minenorth_eurobank.vault_opened", drillLevel.label), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.minenorth_eurobank.incompatible"), true);
                }
            }
            stopTriggeredAnim(serverPlayer, GeoItem.getOrAssignId(stack, (ServerLevel) level), "Drill", "spin");
            level.playSound(null, target, ModSounds.DRILL_STOP.get(), player.getSoundSource(), 0.7F, 1.0F);
        }
        clearTarget(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (entity instanceof ServerPlayer player && !level.isClientSide) {
            BlockPos target = getTarget(stack);
            if (target != null) {
                failDrilling(player, stack, level, target, "message.minenorth_eurobank.drill_bit_broken");
                return;
            }
        }
        clearTarget(stack);
        super.releaseUsing(stack, level, entity, timeCharged);
    }

    private static BlockPos getTarget(ItemStack stack) {
        if (!stack.hasTag()) return null;
        var tag = stack.getTag();
        if (!tag.contains(TARGET_X) || !tag.contains(TARGET_Y) || !tag.contains(TARGET_Z)) return null;
        return new BlockPos(tag.getInt(TARGET_X), tag.getInt(TARGET_Y), tag.getInt(TARGET_Z));
    }

    private static boolean hasPlayerMoved(ItemStack stack, Player player) {
        if (!stack.hasTag()) return true;
        var tag = stack.getTag();
        if (!tag.contains(START_X) || !tag.contains(START_Y) || !tag.contains(START_Z)) return true;
        double dx = player.getX() - tag.getDouble(START_X);
        double dy = player.getY() - tag.getDouble(START_Y);
        double dz = player.getZ() - tag.getDouble(START_Z);
        return (dx * dx + dy * dy + dz * dz) > 0.04D;
    }

    private void cancelDrilling(Player player, ItemStack stack, Level level, String messageKey) {
        BlockPos target = getTarget(stack);
        failDrilling(player, stack, level, target, messageKey);
        player.stopUsingItem();
        clearTarget(stack);
    }

    private void failDrilling(Player player, ItemStack stack, Level level, BlockPos target, String messageKey) {
        if (player instanceof ServerPlayer serverPlayer) {
            stopTriggeredAnim(serverPlayer, GeoItem.getOrAssignId(stack, (ServerLevel) level), "Drill", "spin");
            level.playSound(null, player.blockPosition(), ModSounds.DRILL_STOP.get(), player.getSoundSource(), 0.55F, 1.0F);
            if (target != null && level.getBlockEntity(target) instanceof BankVaultBlockEntity vault && !vault.isOpened()) {
                vault.blockVault();
            }
            breakDrillBit(player, stack);
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    private void breakDrillBit(Player player, ItemStack stack) {
        if (!stack.isEmpty() && stack.getMaxDamage() > 0) {
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if (remaining > 0) {
                stack.hurtAndBreak(remaining, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            }
        }
    }

    private static void clearTarget(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(TARGET_X);
            stack.getTag().remove(TARGET_Y);
            stack.getTag().remove(TARGET_Z);
            stack.getTag().remove(START_X);
            stack.getTag().remove(START_Y);
            stack.getTag().remove(START_Z);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("item.minenorth_eurobank.drill_tooltip", drillLevel.label));
        tooltip.add(Component.translatable("item.minenorth_eurobank.robbery_tool"));
        tooltip.add(Component.translatable("item.minenorth_eurobank.drill_time", String.format(java.util.Locale.ROOT, "%.1f", drillLevel.drillTicks / 20.0D)));
        tooltip.add(Component.translatable("item.minenorth_eurobank.drill_durability", (stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage()));
        tooltip.add(Component.translatable("item.minenorth_eurobank.drill_usage"));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "Drill", 0, state -> PlayState.STOP)
                .triggerableAnim("spin", spinAnimation));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        com.minenorth_eurobank.braquage.client.DrillItemClient.initialize(consumer);
    }

}
