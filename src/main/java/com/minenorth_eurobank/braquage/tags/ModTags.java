package com.minenorth_eurobank.braquage.tags;

import com.minenorth_eurobank.EuroBank;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModTags {
    public static final TagKey<Block> DRILLABLE_BLOCKS = TagKey.create(
            Registries.BLOCK,
            new ResourceLocation(EuroBank.MODID, "drillable")
    );
}
