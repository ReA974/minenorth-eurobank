package com.minenorth_eurobank.items;

import com.minenorth_eurobank.BankData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Carte d'un compte entreprise. Utilisée en main principale, elle fait payer le compte de l'entreprise. */
public class BusinessCardItem extends Item {
    public BusinessCardItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack create(ServerPlayer p, UUID account, String companyName) {
        ItemStack s = new ItemStack(ModItems.BUSINESS_CARD.get());
        CompoundTag t = s.getOrCreateTag();
        t.putUUID("Account", account);
        t.putUUID("Holder", p.getUUID());
        t.putString("CompanyName", companyName == null ? "" : companyName);
        t.putString("HolderName", fr.minenorth.api.MineNorth.displayName(p));
        return s;
    }

    @Nullable
    public static UUID account(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.hasUUID("Account") ? t.getUUID("Account") : null;
    }

    /** Compte de la carte entreprise tenue en main principale, ou null. */
    @Nullable
    public static UUID activeAccount(ServerPlayer p) {
        ItemStack s = p.getItemInHand(InteractionHand.MAIN_HAND);
        return s.getItem() instanceof BusinessCardItem ? account(s) : null;
    }

    public static boolean holdsCard(ServerPlayer p) {
        return p.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BusinessCardItem;
    }

    /** Titulaire = ce joueur, compte entreprise existant, joueur toujours signataire. */
    public static boolean validFor(ServerPlayer p, ItemStack s, BankData d) {
        CompoundTag t = s.getTag();
        if (t == null || !t.hasUUID("Holder") || !t.hasUUID("Account")) return false;
        UUID account = t.getUUID("Account");
        return p.getUUID().equals(t.getUUID("Holder")) && d.isBusiness(account) && d.signers(account).contains(p.getUUID());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t == null) return;
        if (t.contains("CompanyName")) tip.add(Component.literal("Entreprise : " + t.getString("CompanyName")).withStyle(ChatFormatting.GRAY));
        if (t.contains("HolderName")) tip.add(Component.literal("Titulaire : " + t.getString("HolderName")).withStyle(ChatFormatting.GRAY));
    }
}
