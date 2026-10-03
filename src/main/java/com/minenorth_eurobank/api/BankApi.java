package com.minenorth_eurobank.api;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.items.BankCardItem;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * API publique pour les autres mods (garage, boutiques...). Montants en CENTIMES.
 * À appeler depuis le thread serveur uniquement.
 *
 * Un paiement par carte exige : un compte, SA carte dans l'inventaire (une carte volée est refusée) et un solde suffisant.
 */
public final class BankApi {
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
        if (cents <= 0) return PayResult.INVALID_AMOUNT;
        BankData d = BankData.get(p.server);
        UUID id = p.getUUID();
        if (!d.has(id)) return PayResult.NO_ACCOUNT;
        if (!BankCardItem.hasOwnCard(p)) return BankCardItem.hasAnyCard(p) ? PayResult.FOREIGN_CARD : PayResult.NO_CARD;
        if (d.balance(id) < cents) return PayResult.INSUFFICIENT_FUNDS;
        return PayResult.OK;
    }

    /** Débite le compte (l'argent disparaît de l'économie). */
    public static PayResult charge(ServerPlayer p, long cents) {
        PayResult r = check(p, cents);
        if (r == PayResult.OK) BankData.get(p.server).add(p.getUUID(), -cents);
        return r;
    }

    /** Débite le compte et verse la somme au capital de la banque (réutilisable pour les prêts). */
    public static PayResult chargeToReserve(ServerPlayer p, long cents) {
        PayResult r = check(p, cents);
        if (r == PayResult.OK) {
            BankData d = BankData.get(p.server);
            d.add(p.getUUID(), -cents);
            d.addReserve(cents);
        }
        return r;
    }

    /** Recrédite le compte (ex. achat annulé après le débit). */
    public static void refund(ServerPlayer p, long cents) {
        BankData d = BankData.get(p.server);
        if (cents > 0 && d.has(p.getUUID())) d.add(p.getUUID(), cents);
    }
}
