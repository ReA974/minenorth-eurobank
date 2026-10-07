package com.minenorth_eurobank.api;

import com.minenorth_eurobank.BankData;
import fr.minenorth.api.MineNorth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Fournit les comptes aux autres mods via MineNorth API. Tout prélèvement part au trésor public avec sa source,
 * tout remboursement en revient : la banque ne crée ni ne détruit d'argent pour les autres mods.
 */
public final class BankProvider implements fr.minenorth.api.BankService {
    private static fr.minenorth.api.PayResult map(PayResult r) {
        return fr.minenorth.api.PayResult.valueOf(r.name());
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
}
