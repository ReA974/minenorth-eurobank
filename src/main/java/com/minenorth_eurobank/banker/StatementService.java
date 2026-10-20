package com.minenorth_eurobank.banker;

import com.minenorth_eurobank.BankData;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.packet.StatementPacket;
import fr.minenorth.api.BankTx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/** Relevé de compte : synthèse (entrées, sorties, dépenses des 7 derniers jours) et dernières opérations, affiché en interface. */
public final class StatementService {
    public static final int MAX_TX = 60;
    private static final long DAY_MS = 86_400_000L;

    private StatementService() {}

    /** Envoie à {@code viewer} le relevé du compte {@code account} (le sien à l'ATM, n'importe lequel pour un banquier). */
    public static void send(ServerPlayer viewer, UUID account) {
        BankData d = BankData.get(viewer.server);
        if (!d.has(account)) return;
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new StatementPacket(build(d, account, System.currentTimeMillis())));
    }

    static CompoundTag build(BankData d, UUID account, long now) {
        CompoundTag t = new CompoundTag();
        t.putString("name", d.name(account));
        t.putBoolean("business", d.isBusiness(account));
        t.putLong("balance", d.balance(account));
        t.putBoolean("frozen", d.isFrozen(account));
        t.putLong("now", now);
        Loan loan = d.openLoanOf(account);
        if (loan != null && loan.status != Loan.PENDING) {
            t.putLong("loanLeft", loan.remaining());
            t.putLong("loanDue", loan.dueMs);
            t.putBoolean("loanLate", loan.status == Loan.OVERDUE);
        }
        List<BankTx> txs = d.history(account, 200);
        long in30 = 0, out30 = 0, biggest = 0;
        long[] days = new long[7];
        for (BankTx x : txs) {
            long age = now - x.time();
            if (age <= 30 * DAY_MS) {
                if (x.cents() > 0) in30 += x.cents();
                else out30 += -x.cents();
            }
            if (x.cents() < 0) {
                int idx = (int) (age / DAY_MS);
                if (idx >= 0 && idx < 7) days[6 - idx] += -x.cents();
                if (age <= 30 * DAY_MS) biggest = Math.max(biggest, -x.cents());
            }
        }
        t.putLong("in30", in30);
        t.putLong("out30", out30);
        t.putLong("biggest", biggest);
        t.putLongArray("days", days);
        t.putInt("count", txs.size());
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(MAX_TX, txs.size()); i++) {
            BankTx x = txs.get(i);
            CompoundTag c = new CompoundTag();
            c.putLong("t", x.time());
            c.putLong("c", x.cents());
            c.putLong("b", x.balanceAfter());
            String label = x.label() == null ? "" : x.label();
            c.putString("l", label.length() > 48 ? label.substring(0, 48) : label);
            c.putString("k", x.category() == null ? "" : x.category());
            list.add(c);
        }
        t.put("tx", list);
        return t;
    }
}
