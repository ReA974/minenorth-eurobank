package com.minenorth_eurobank.safe;

import com.minenorth_eurobank.EuroBank;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** Enregistrements des coffres-forts : un bloc et un objet par niveau, un seul type de bloc-entité et de menu. */
public final class ModSafe {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EuroBank.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EuroBank.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EuroBank.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, EuroBank.MODID);

    public static final Map<SafeTier, RegistryObject<Block>> SAFE_BLOCKS = new EnumMap<>(SafeTier.class);
    public static final Map<SafeTier, RegistryObject<Item>> SAFE_ITEMS = new EnumMap<>(SafeTier.class);

    static {
        for (SafeTier t : SafeTier.values()) {
            RegistryObject<Block> block = BLOCKS.register("safe_" + t.id, () -> new SafeBlock(t));
            SAFE_BLOCKS.put(t, block);
            SAFE_ITEMS.put(t, ITEMS.register("safe_" + t.id, () -> new SafeItem(block.get(), t)));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SafeBlockEntity>> SAFE_BE = BLOCK_ENTITIES.register("safe",
            () -> BlockEntityType.Builder.of(SafeBlockEntity::new,
                    SAFE_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static final RegistryObject<MenuType<SafeMenu>> SAFE_MENU = MENUS.register("safe",
            () -> IForgeMenuType.create((id, inv, buf) -> {
                SafeTier tier = SafeTier.byIndex(buf.readVarInt());
                if (tier == null) tier = SafeTier.BASIC;
                return new SafeMenu(id, inv, new SimpleContainer(tier.slots), tier, ContainerLevelAccess.NULL);
            }));

    private ModSafe() {}
}
