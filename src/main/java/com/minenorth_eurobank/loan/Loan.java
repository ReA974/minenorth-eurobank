package com.minenorth_eurobank.loan;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Un prêt (ou une demande de prêt). Montants en centimes, durées en jours réels. */
public class Loan {
    public static final byte PENDING = 0, ACTIVE = 1, OVERDUE = 2, REPAID = 3, REJECTED = 4, FORGIVEN = 5, CANCELLED = 6;

    public final UUID id;
    public final UUID borrower;
    public final long principal;
    public final int termDays;
    public final long createdMs;
    public int rateBp;      // taux fixe en points de base (500 = 5 %)
    public long totalDue;   // capital + intérêts
    public long repaid;
    public long dueMs;
    public byte status;
    // --- dossier de demande (rempli au guichet auprès du banquier PNJ)
    public byte type;          // index dans LoanService.TYPES
    public byte housing;       // index dans LoanService.HOUSING
    public byte income;        // index dans LoanService.INCOME
    public long downPayment;   // apport personnel en centimes
    public String phone = "";  // numéro à 4 chiffres pour rappeler le client
    public String reason = "";
    /** Vrai si le prêt est déjà passé en retard au moins une fois (sert au score de fiabilité). */
    public boolean wasLate;

    public Loan(UUID id, UUID borrower, long principal, int termDays, long createdMs) {
        this.id = id;
        this.borrower = borrower;
        this.principal = principal;
        this.termDays = termDays;
        this.createdMs = createdMs;
        this.totalDue = principal;
        this.status = PENDING;
    }

    public boolean open() { return status <= OVERDUE; }

    public long remaining() { return Math.max(0, totalDue - repaid); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("borrower", borrower);
        t.putLong("principal", principal);
        t.putInt("term", termDays);
        t.putLong("created", createdMs);
        t.putInt("rate", rateBp);
        t.putLong("total", totalDue);
        t.putLong("repaid", repaid);
        t.putLong("due", dueMs);
        t.putByte("status", status);
        t.putByte("type", type);
        t.putByte("housing", housing);
        t.putByte("income", income);
        t.putLong("down", downPayment);
        t.putString("phone", phone);
        t.putString("reason", reason);
        t.putBoolean("late", wasLate);
        return t;
    }

    public static Loan load(CompoundTag t) {
        Loan l = new Loan(t.getUUID("id"), t.getUUID("borrower"), t.getLong("principal"), t.getInt("term"), t.getLong("created"));
        l.rateBp = t.getInt("rate");
        l.totalDue = t.getLong("total");
        l.repaid = t.getLong("repaid");
        l.dueMs = t.getLong("due");
        l.status = t.getByte("status");
        l.type = t.getByte("type");
        l.housing = t.getByte("housing");
        l.income = t.getByte("income");
        l.downPayment = t.getLong("down");
        l.phone = t.getString("phone");
        l.reason = t.getString("reason");
        l.wasLate = t.getBoolean("late");
        return l;
    }
}
