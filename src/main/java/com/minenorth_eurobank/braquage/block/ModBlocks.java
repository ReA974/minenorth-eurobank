package com.minenorth_eurobank.braquage.block;

import com.minenorth_eurobank.EuroBank;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EuroBank.MODID);

    public static final RegistryObject<Block> BANK_VAULT = BLOCKS.register("bank_vault", () -> new BankVaultBlock(
            BlockBehaviour.Properties.of()
                    // Incassable (comme la bedrock) : on ne l'ouvre qu'à la perceuse. Un OP en créatif peut le retirer.
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
    ));
}
