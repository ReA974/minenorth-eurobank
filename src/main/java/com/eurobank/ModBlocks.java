package com.eurobank;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EuroBank.MODID);
    public static final RegistryObject<Block> ATM = BLOCKS.register("atm", AtmBlock::new);

    private ModBlocks() {}
}
