package com.minenorth_eurobank.api;

import com.minenorth_eurobank.BankData;
import fr.minenorth.api.BankTx;
import fr.minenorth.api.MineNorth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fournit les comptes aux autres mods via MineNorth API. Tout prélèvement part au trésor public avec sa source,
 * tout remboursement en revient : la banque ne crée ni ne détruit d'argent pour les autres mods.
 */
public final class BankProvider implements fr.minenorth.api.BankService {
    /** Plafond par opération (10 M€ en centimes). */
    private static final long MAX_CENTS = 1_000_000_000L;

    private static fr.minenorth.api.PayResult map(PayResult r) {
        return fr.minenorth.api.PayResult.valueOf(r.name());
    }

    /** Enregistre une ligne d'historique sur un compte entreprise ; le solde après opération est lu ici. */
    private static void record(MinecraftServer s, UUID account, long signedCents, String cat, String label, String actor) {
        BankData d = BankData.get(s);
        if (!d.isBusiness(account)) return;
        d.record(account, new BankTx(System.currentTimeMillis(), cat, signedCents, d.balance(account), label, actor));
    }

    @Override
    public boolean hasAccount(MinecraftServer s, UUID player) { return BankData.get(s).has(player); }

    @Override
    public long balance(MinecraftServer s, UUID player) { return BankData.get(s).balance(player); }

    @Override
    public fr.minenorth.api.PayResult check(ServerPlayer p, long cents) { return map(BankApi.check(p, cents)); }

    @Override
    public fr.minenorth.api.PayResult charge(ServerPlayer p, long cents, String source) {
        return map(BankApi.charge(p, cents, source));   // BankApi verse déjà au trésor
    }

    @Override
    public boolean refund(MinecraftServer s, UUID player, long cents, String source) {
        BankData d = BankData.get(s);
        if (cents <= 0 || !d.has(player)) return false;
        d.add(player, cents);
        MineNorth.treasury().collect(s, -cents, source);
        record(s, player, cents, BankTx.ADMIN, source, null);
        return true;
    }

    @Override
    public fr.minenorth.api.PayResult debit(MinecraftServer s, UUID player, long cents, String source, boolean allowNegative) {
        if (cents <= 0) return fr.minenorth.api.PayResult.INVALID_AMOUNT;
        BankData d = BankData.get(s);
        if (!d.has(player)) return fr.minenorth.api.PayResult.NO_ACCOUNT;
        if (!allowNegative && d.balance(player) < cents) return fr.minenorth.api.PayResult.INSUFFICIENT_FUNDS;
        d.add(player, -cents);
        MineNorth.treasury().collect(s, cents, source);
        record(s, player, -cents, BankTx.ADMIN, source, null);
        return fr.minenorth.api.PayResult.OK;
    }

    @Override
    public fr.minenorth.api.PayResult transfer(MinecraftServer s, UUID from, UUID to, long cents) {
        if (cents <= 0 || from.equals(to)) return fr.minenorth.api.PayResult.INVALID_AMOUNT;
        BankData d = BankData.get(s);
        if (!d.has(from) || !d.has(to)) return fr.minenorth.api.PayResult.NO_ACCOUNT;
        if (d.balance(from) < cents) return fr.minenorth.api.PayResult.INSUFFICIENT_FUNDS;
        d.add(from, -cents);
        d.add(to, cents);
        return fr.minenorth.api.PayResult.OK;
    }

    @Override
    public boolean openBusinessAccount(MinecraftServer s, UUID accountId, String label) {
        BankData d = BankData.get(s);
        if (d.has(accountId) && !d.isBusiness(accountId)) return false;   // jamais écraser un compte joueur
        d.open(accountId);
        d.openBusiness(accountId, label);
        return true;
    }

    @Override
    public boolean renameAccount(MinecraftServer s, UUID accountId, String label) {
        BankData d = BankData.get(s);
        if (!d.isBusiness(accountId)) return false;
        d.rename(accountId, label);
        return true;
    }

    @Override
    public boolean closeAccount(MinecraftServer s, UUID accountId) {
        BankData d = BankData.get(s);
        if (!d.isBusiness(accountId) || d.balance(accountId) != 0) return false;
        d.closeBusiness(accountId);
        return true;
    }

    @Override
    public void setSigners(MinecraftServer s, UUID accountId, Set<UUID> signers) {
        BankData d = BankData.get(s);
        if (d.isBusiness(accountId)) d.setSigners(accountId, signers);
    }

    @Override
    public fr.minenorth.api.PayResult transfer(MinecraftServer s, UUID from, UUID to, long cents, String category, String label, String actor) {
        if (cents > MAX_CENTS) return fr.minenorth.api.PayResult.INVALID_AMOUNT;
        fr.minenorth.api.PayResult r = transfer(s, from, to, cents);
        if (r != fr.minenorth.api.PayResult.OK) return r;
        BankData d = BankData.get(s);
        boolean noLabel = label == null || label.isEmpty();
        if (d.isBusiness(from)) record(s, from, -cents, category, noLabel ? d.name(to) : label, actor);
        if (d.isBusiness(to)) record(s, to, cents, category, noLabel ? d.name(from) : label, actor);
        return r;
    }

    @Override
    public fr.minenorth.api.PayResult payFromAccount(MinecraftServer s, UUID accountId, long cents, String source, UUID actor) {
        if (cents <= 0 || cents > MAX_CENTS) return fr.minenorth.api.PayResult.INVALID_AMOUNT;
        BankData d = BankData.get(s);
        if (!d.isBusiness(accountId)) return fr.minenorth.api.PayResult.NO_ACCOUNT;
        if (actor != null && !d.signers(accountId).contains(actor)) return fr.minenorth.api.PayResult.FOREIGN_CARD;
        if (d.balance(accountId) < cents) return fr.minenorth.api.PayResult.INSUFFICIENT_FUNDS;
        d.add(accountId, -cents);
        MineNorth.treasury().collect(s, cents, source);
        record(s, accountId, -cents, BankTx.PAYMENT, source, actor == null ? null : d.name(actor));
        return fr.minenorth.api.PayResult.OK;
    }

    @Override
    public boolean giveBusinessCard(ServerPlayer p, UUID accountId, String companyName) {
        if (!BankData.get(p.server).isBusiness(accountId)) return false;
        net.minecraft.world.item.ItemStack stack = com.minenorth_eurobank.items.BusinessCardItem.create(p, accountId, companyName);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        return true;
    }

    @Override
    public List<BankTx> history(MinecraftServer s, UUID accountId, int limit) {
        return BankData.get(s).history(accountId, limit);
    }
}
