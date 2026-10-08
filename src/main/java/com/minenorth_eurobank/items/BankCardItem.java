package com.minenorth_eurobank.items;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public class BankCardItem extends Item {
    public BankCardItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack create(ServerPlayer p) {
        ItemStack s = new ItemStack(ModItems.CARD.get());
        CompoundTag t = s.getOrCreateTag();
        t.putUUID("Owner", p.getUUID());
        t.putString("OwnerName", fr.minenorth.api.MineNorth.displayName(p));
        return s;
    }

    @Nullable
    public static UUID owner(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.hasUUID("Owner") ? t.getUUID("Owner") : null;
    }

    public static boolean hasOwnCard(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof BankCardItem && p.getUUID().equals(owner(s))) return true;
        }
        return false;
    }

    public static boolean hasAnyCard(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof BankCardItem) return true;
        }
        return false;
    }

    /** Clic droit dans le vide avec sa carte : solde du compte (et prêt en cours) dans le chat. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            player.getCooldowns().addCooldown(this, 10);
            UUID owner = owner(stack);
            BankData d = BankData.get(sp.server);
            if (owner == null || !owner.equals(sp.getUUID())) {
                sp.sendSystemMessage(Component.literal("§c[Banque] Cette carte n'est pas la vôtre."));
            } else if (!d.has(owner)) {
                sp.sendSystemMessage(Component.literal("§c[Banque] Vous n'avez pas de compte bancaire."));
            } else {
                sp.sendSystemMessage(Component.literal("§6[Banque] §7Solde de votre compte : §a" + Money.format(d.balance(owner))));
                Loan loan = d.openLoanOf(owner);
                if (loan != null && loan.status != Loan.PENDING)
                    sp.sendSystemMessage(Component.literal("§6[Banque] §7Prêt en cours : §c" + Money.format(loan.remaining())
                            + " §7à rembourser avant le " + LoanService.date(loan.dueMs)
                            + (loan.status == Loan.OVERDUE ? " §c(en retard)" : "")));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("OwnerName")) {
            tip.add(Component.literal("Titulaire : " + t.getString("OwnerName")).withStyle(ChatFormatting.GRAY));
        }
    }
}
