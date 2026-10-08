package com.minenorth_eurobank.items;

import com.minenorth_eurobank.Denomination;
import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.blocks.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EuroBank.MODID);

    public static final Map<Denomination, RegistryObject<Item>> MONEY = new EnumMap<>(Denomination.class);

    static {
        for (Denomination d : Denomination.values()) {
            MONEY.put(d, ITEMS.register(d.id, () -> new MoneyItem(new Item.Properties(), d.cents)));
        }
    }

    public static final RegistryObject<Item> CARD = ITEMS.register("bank_card", BankCardItem::new);
    public static final RegistryObject<Item> BUSINESS_CARD = ITEMS.register("business_card", BusinessCardItem::new);
    public static final RegistryObject<Item> ATM_ITEM = ITEMS.register("atm",
            () -> new BlockItem(ModBlocks.ATM.get(), new Item.Properties()));
    public static final RegistryObject<Item> ATM_BASE_ITEM = ITEMS.register("atm_base",
            () -> new BlockItem(ModBlocks.ATM_BASE.get(), new Item.Properties()));

    private ModItems() {}
}
