package com.minenorth_eurobank;

import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import fr.minenorth.api.BankTx;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BankerFeaturesTest {
    private static BankTx tx(long cents, long balance) {
        return new BankTx(System.currentTimeMillis(), BankTx.DEPOSIT, cents, balance, "test", null);
    }

    @Test
    void largeMovementRaisesAnAlertAndSmallOneDoesNot() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID();
        d.open(p);
        d.record(p, tx(100_00L, 100_00L));
        assertEquals(0, d.openAlerts());
        d.record(p, tx(d.alertThreshold(), d.alertThreshold() + 100_00L));
        assertEquals(1, d.openAlerts());
        d.handleAllAlerts();
        assertEquals(0, d.openAlerts());
    }

    @Test
    void depositAfterHeistIsSuspect() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID();
        d.open(p);
        d.record(p, tx(BankData.HEIST_SUSPECT, BankData.HEIST_SUSPECT));
        assertEquals(0, d.openAlerts());
        d.markHeist();
        d.record(p, tx(BankData.HEIST_SUSPECT, 2 * BankData.HEIST_SUSPECT));
        assertEquals(1, d.openAlerts());
    }

    @Test
    void frozenNotesAndAlertsSurviveSaveAndLoad() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID();
        d.open(p);
        d.setFrozen(p, true);
        d.setNote(p, "client à surveiller");
        d.setAlertThreshold(123_00L);
        d.addAlert(p, "alerte", 5);
        BankData r = BankData.load(d.save(new CompoundTag()));
        assertTrue(r.isFrozen(p));
        assertEquals("client à surveiller", r.note(p));
        assertEquals(123_00L, r.alertThreshold());
        assertEquals(1, r.alerts().size());
        r.setFrozen(p, false);
        assertFalse(r.isFrozen(p));
    }

    @Test
    void loanApplicationFieldsSurviveSaveAndLoad() {
        Loan l = new Loan(UUID.randomUUID(), UUID.randomUUID(), 1000_00L, 7, 1L);
        l.type = 1;
        l.housing = 2;
        l.income = 3;
        l.downPayment = 50_00L;
        l.phone = "0042";
        l.reason = "maison";
        l.wasLate = true;
        Loan r = Loan.load(l.save());
        assertEquals(1, r.type);
        assertEquals(2, r.housing);
        assertEquals(3, r.income);
        assertEquals(50_00L, r.downPayment);
        assertEquals("0042", r.phone);
        assertEquals("maison", r.reason);
        assertTrue(r.wasLate);
    }

    @Test
    void trustScoreRewardsRepaymentAndPunishesLateness() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID();
        assertEquals(50, LoanService.trust(d, p));
        Loan ok = new Loan(UUID.randomUUID(), p, 100_00L, 7, 1L);
        ok.status = Loan.REPAID;
        d.addLoan(ok);
        assertEquals(60, LoanService.trust(d, p));
        Loan bad = new Loan(UUID.randomUUID(), p, 100_00L, 7, 2L);
        bad.status = Loan.OVERDUE;
        bad.wasLate = true;
        d.addLoan(bad);
        assertTrue(LoanService.trust(d, p) < 60);
    }

    @Test
    void contactRequestsSurviveSaveAndBlockDuplicates() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID();
        assertFalse(d.hasOpenContact(p));
        d.addContact(p, "Jean", "Dupont", "1234", "Je veux un rappel");
        assertTrue(d.hasOpenContact(p));
        assertEquals(1, d.openContacts());
        BankData r = BankData.load(d.save(new CompoundTag()));
        assertEquals(1, r.contacts().size());
        assertEquals("Dupont", r.contacts().get(0).last);
        r.handleContact(r.contacts().get(0).id);
        assertFalse(r.hasOpenContact(p));
        assertEquals(0, r.openContacts());
    }

    @Test
    void recentMergesAccountsNewestFirst() {
        BankData d = new BankData();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        d.open(a);
        d.open(b);
        d.record(a, new BankTx(1, BankTx.DEPOSIT, 10, 10, "x", null));
        d.record(b, new BankTx(3, BankTx.DEPOSIT, 10, 10, "y", null));
        d.record(a, new BankTx(2, BankTx.DEPOSIT, 10, 20, "z", null));
        var all = d.recent(null, 10);
        assertEquals(3, all.size());
        assertEquals(3, all.get(0).tx().time());
        assertEquals(1, d.recent(b, 10).size());
    }
}
