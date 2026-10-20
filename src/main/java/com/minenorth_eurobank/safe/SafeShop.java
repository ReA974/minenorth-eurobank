package com.minenorth_eurobank.safe;

import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.api.BankApi;
import com.minenorth_eurobank.api.PayResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Achat d'un coffre-fort au guichet : paiement par carte, l'objet va dans l'inventaire et est lié à l'acheteur. */
public final class SafeShop {
    private SafeShop() {}

    public static String buy(ServerPlayer p, int tierIndex) {
        SafeTier tier = SafeTier.byIndex(tierIndex);
        if (tier == null) return "Coffre-fort inconnu.";
        PayResult r = BankApi.charge(p, tier.price, "Coffre-fort " + tier.label);
        if (r != PayResult.OK) return r.message();
        ItemStack stack = new ItemStack(ModSafe.SAFE_ITEMS.get(tier).get());
        CompoundTag be = new CompoundTag();
        be.putUUID("Owner", p.getUUID());
        be.putString("OwnerName", fr.minenorth.api.MineNorth.displayName(p));
        stack.getOrCreateTag().put("BlockEntityTag", be);
        Money.giveStack(p, stack);
        return "Coffre-fort " + tier.label + " acheté (" + Money.format(tier.price) + "). Il est dans votre inventaire.";
    }
}
