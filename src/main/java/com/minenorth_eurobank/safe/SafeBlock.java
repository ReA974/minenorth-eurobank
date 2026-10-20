package com.minenorth_eurobank.safe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/** Coffre-fort personnel : seul son propriétaire peut l'ouvrir, le casser ou le déplacer avec son contenu. */
public class SafeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

    private final SafeTier tier;

    public SafeBlock(SafeTier tier) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 3_600_000f).sound(SoundType.NETHERITE_BLOCK).noOcclusion());
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public SafeTier tier() { return tier; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SafeBlockEntity(pos, state);
    }

    /** Un coffre posé sans propriétaire (créatif, commande) appartient à celui qui le pose. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SafeBlockEntity be && be.owner() == null
                && placer instanceof ServerPlayer sp) {
            be.setOwner(sp.getUUID(), fr.minenorth.api.MineNorth.displayName(sp));
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof SafeBlockEntity be)) return InteractionResult.PASS;
        if (!be.isOwner(sp.getUUID())) {
            sp.displayClientMessage(Component.literal("§cCe coffre-fort ne vous appartient pas."), true);
            return InteractionResult.CONSUME;
        }
        // contenu rattaché au joueur (comme un ender chest), pas au bloc
        SimpleContainer storage = SafeStorage.get(sp.server).container(sp.getUUID(), tier);
        ListTag legacy = be.takeLegacyItems();
        if (legacy != null) {
            SimpleContainer old = new SimpleContainer(64);
            old.fromTag(legacy);
            for (int i = 0; i < old.getContainerSize(); i++) {
                ItemStack s = old.getItem(i);
                if (s.isEmpty()) continue;
                ItemStack rest = storage.addItem(s);
                if (!rest.isEmpty()) popResource(level, pos, rest);
            }
        }
        NetworkHooks.openScreen(sp, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Coffre-fort " + tier.label + " (" + tier.slots + ")");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new SafeMenu(id, inv, storage, tier, ContainerLevelAccess.create(level, pos));
            }
        }, buf -> buf.writeVarInt(tier.ordinal()));
        return InteractionResult.CONSUME;
    }

    /** Les autres joueurs ne peuvent pas le casser (les opérateurs, si). */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SafeBlockEntity be && !be.isOwner(player.getUUID())
                && !player.hasPermissions(2) && !player.isCreative()) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

}
