package com.minenorth_eurobank.api;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.items.BankCardItem;
import com.minenorth_eurobank.items.BusinessCardItem;
import fr.minenorth.api.BankTx;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * API publique pour les autres mods (garage, boutiques...). Montants en CENTIMES.
 * À appeler depuis le thread serveur uniquement.
 *
 * Un paiement par carte exige : un compte, SA carte dans l'inventaire (une carte volée est refusée) et un solde suffisant.
 */
public final class BankApi {
    /** Plafond par opération (10 M€ en centimes). */
    private static final long MAX_CENTS = 1_000_000_000L;

    private BankApi() {}

    public static boolean hasAccount(ServerPlayer p) {
        return BankData.get(p.server).has(p.getUUID());
    }

    /** Solde du compte en centimes (0 si pas de compte). */
    public static long balance(ServerPlayer p) {
        return BankData.get(p.server).balance(p.getUUID());
    }

    /** Vérifie qu'un paiement serait possible, sans rien débiter. */
    public static PayResult check(ServerPlayer p, long cents) {
        if (BusinessCardItem.holdsCard(p)) return checkBusiness(p, cents);
        if (cents <= 0) return PayResult.INVALID_AMOUNT;
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        if (!d.has(id)) return PayResult.NO_ACCOUNT;
        if (!BankCardItem.hasOwnCard(p)) return BankCardItem.hasAnyCard(p) ? PayResult.FOREIGN_CARD : PayResult.NO_CARD;
        if (d.balance(id) < cents) return PayResult.INSUFFICIENT_FUNDS;
        return PayResult.OK;
    }

    /** Débite le compte ; la somme part au trésor public (MineNorth API). Préférer MineNorth.bank().charge(p, cents, source). */
    public static PayResult charge(ServerPlayer p, long cents) {
        return charge(p, cents, "carte");
    }

    /** Paiement par carte entreprise (main principale) : vise le compte de l'entreprise. */
    private static PayResult checkBusiness(ServerPlayer p, long cents) {
        BankData d = BankData.get(p.server);
        ItemStack card = p.getItemInHand(InteractionHand.MAIN_HAND);
        if (!BusinessCardItem.validFor(p, card, d)) return PayResult.FOREIGN_CARD;
        if (cents <= 0 || cents > MAX_CENTS) return PayResult.INVALID_AMOUNT;
        if (d.balance(BusinessCardItem.account(card)) < cents) return PayResult.INSUFFICIENT_FUNDS;
        return PayResult.OK;
    }

    public static PayResult charge(ServerPlayer p, long cents, String source) {
        if (BusinessCardItem.holdsCard(p)) {
            PayResult r = checkBusiness(p, cents);
            if (r == PayResult.OK) {
                BankData d = BankData.get(p.server);
                UUID account = BusinessCardItem.activeAccount(p);
                d.add(account, -cents);
                fr.minenorth.api.MineNorth.treasury().collect(p.server, cents, source);
                d.record(account, new BankTx(System.currentTimeMillis(), BankTx.PAYMENT, -cents, d.balance(account), source, d.name(p.getUUID())));
            }
            return r;
        }
        PayResult r = check(p, cents);
        if (r == PayResult.OK) {
            BankData.get(p.server).add(p.getUUID(), -cents);
            fr.minenorth.api.MineNorth.treasury().collect(p.server, cents, source);
        }
        return r;
    }

    /**
     * Ancien « vers le capital de la banque » (amendes, frais d'hôpital). Règle MineNorth : tout prélèvement va au
     * trésor public, comme les autres. Le capital des prêts se gère avec /bank reserve.
     */
    public static PayResult chargeToReserve(ServerPlayer p, long cents) {
        return charge(p, cents, "frais");
    }

    /** Recrédite le compte (ex. achat annulé après le débit). */
    public static void refund(ServerPlayer p, long cents) {
        BankData d = BankData.get(p.server);
        if (cents > 0 && d.has(p.getUUID())) {
            d.add(p.getUUID(), cents);
            fr.minenorth.api.MineNorth.treasury().collect(p.server, -cents, "remboursement");
        }
    }
}
