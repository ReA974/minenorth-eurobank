package com.minenorth_eurobank.blocks;

import com.minenorth_eurobank.EuroBank;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EuroBank.MODID);
    public static final RegistryObject<Block> ATM = BLOCKS.register("atm", AtmBlock::new);
    public static final RegistryObject<Block> ATM_BASE = BLOCKS.register("atm_base", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f).sound(SoundType.STONE).noOcclusion()));

    private ModBlocks() {}
}
