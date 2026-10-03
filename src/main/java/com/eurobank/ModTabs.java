package com.eurobank;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EuroBank.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eurobank"))
                    .icon(() -> new ItemStack(ModItems.MONEY.get(Denomination.B50).get()))
                    .displayItems((params, out) -> {
                        ModItems.MONEY.values().forEach(r -> out.accept(r.get()));
                        out.accept(ModItems.ATM_ITEM.get());
                    })
                    .build());

    private ModTabs() {}
}
