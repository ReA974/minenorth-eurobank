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
    void routineBankMovementsAndRepeatsDoNotSpamAlerts() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID();
        d.open(p);
        long big = d.alertThreshold() * 2;
        // prêt accordé, ajustement admin : jamais d'alerte
        d.record(p, new BankTx(System.currentTimeMillis(), BankTx.INCOME, big, big, "Prêt accordé", null));
        d.record(p, new BankTx(System.currentTimeMillis(), BankTx.ADMIN, big, big, "Ajout", null));
        assertEquals(0, d.openAlerts());
        // gros mouvements répétés : une seule alerte dans la fenêtre de 15 minutes
        for (int i = 0; i < 5; i++) d.record(p, tx(big, big));
        assertEquals(1, d.openAlerts());
    }

    @Test
    void safeTiersMatchTheShopPrices() {
        assertEquals(10, com.minenorth_eurobank.safe.SafeTier.BASIC.slots);
        assertEquals(23, com.minenorth_eurobank.safe.SafeTier.ADVANCED.slots);
        assertEquals(30, com.minenorth_eurobank.safe.SafeTier.EXPERT.slots);
        assertEquals(4_000_00L, com.minenorth_eurobank.safe.SafeTier.BASIC.price);
        assertEquals(11_000_00L, com.minenorth_eurobank.safe.SafeTier.ADVANCED.price);
        assertEquals(25_000_00L, com.minenorth_eurobank.safe.SafeTier.EXPERT.price);
        for (com.minenorth_eurobank.safe.SafeTier t : com.minenorth_eurobank.safe.SafeTier.values()) {
            assertTrue(t.rows() * t.cols >= t.slots, "la grille doit contenir tous les emplacements");
        }
        assertTrue(LoanService.MAX >= 1_000_000_00L, "pas de plafond métier sur les prêts (700 000 € doit passer)");
    }

    @Test
    void contactsCanBeDeleted() {
        BankData d = new BankData();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        d.addContact(a, "A", "A", "1111", "x");
        d.addContact(b, "B", "B", "2222", "y");
        d.handleContact(d.contacts().get(0).id);
        d.clearHandledContacts();
        assertEquals(1, d.contacts().size());
        d.deleteContact(d.contacts().get(0).id);
        assertTrue(d.contacts().isEmpty());
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

    private static Loan activeLoan(long principal, int rateBp, int days, long now, long startOffsetMs) {
        Loan l = new Loan(UUID.randomUUID(), UUID.randomUUID(), principal, days, now + startOffsetMs);
        l.status = Loan.ACTIVE;
        l.rateBp = rateBp;
        l.totalDue = principal + LoanService.interest(principal, rateBp);
        l.dueMs = now + startOffsetMs + days * 86_400_000L;
        return l;
    }

    @Test
    void earlyPayoffProratesInterestAndNeverExceedsRemaining() {
        long now = 1_000_000_000_000L;
        long day = 86_400_000L;
        // 1000 € sur 10 jours à 10 % = 100 € d'intérêts ; payé le 1er jour entamé -> 10 €
        Loan l = new Loan(UUID.randomUUID(), UUID.randomUUID(), 1000_00L, 10, now);
        l.status = Loan.ACTIVE;
        l.rateBp = 1000;
        l.totalDue = 1100_00L;
        l.dueMs = now + 10 * day;
        assertEquals(1010_00L, LoanService.payoff(l, now + 1));
        assertEquals(1050_00L, LoanService.payoff(l, now + 4 * day + 1));   // 5e jour entamé
        assertEquals(1100_00L, LoanService.payoff(l, now + 9 * day + day / 2 + 1));
        // échéance dépassée : reste dû normal
        assertEquals(1100_00L, LoanService.payoff(l, now + 11 * day));
        // jamais plus que le reste dû après paiement partiel
        l.repaid = 1050_00L;
        assertEquals(40_00L, LoanService.payoff(l, now + 9 * day));
        l.repaid = 1095_00L;   // déjà plus que le prorata dû : rien à payer
        assertEquals(0L, LoanService.payoff(l, now + 9 * day));
    }

    @Test
    void offerIsOnlyVisibleToItsBorrowerWhileValid() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID(), other = UUID.randomUUID();
        Loan o = new Loan(UUID.randomUUID(), p, 500_00L, 7, System.currentTimeMillis());
        o.status = Loan.OFFERED;
        d.addLoan(o);
        assertNotNull(LoanService.offerOf(d, p));
        assertNull(LoanService.offerOf(d, other));
        assertNull(d.openLoanOf(p), "une offre n'est pas un prêt ouvert");
        Loan old = new Loan(UUID.randomUUID(), other, 500_00L, 7, System.currentTimeMillis() - LoanService.OFFER_VALID_MS - 1000);
        old.status = Loan.OFFERED;
        d.addLoan(old);
        assertNull(LoanService.offerOf(d, other), "offre expirée");
    }

    @Test
    void weeklyStatsAndReportsSurviveSaveAndLoad() {
        BankData d = new BankData();
        d.setWeekStart(1000L);
        d.addStat("granted", 500_00L);
        d.addStat("granted", 250_00L);
        assertEquals(750_00L, d.stat("granted"));
        d.closeWeek(2000L, java.util.List.of("ligne 1", "ligne 2"));
        assertEquals(0, d.stat("granted"), "compteurs remis à zéro");
        assertEquals(2000L, d.weekStartMs());
        BankData r = BankData.load(d.save(new CompoundTag()));
        assertEquals(1, r.reports().size());
        assertEquals(java.util.List.of("ligne 1", "ligne 2"), r.reports().get(0).lines);
        assertEquals(2000L, r.weekStartMs());
    }

    @Test
    void remindersSurviveSaveAndLoad() {
        Loan l = new Loan(UUID.randomUUID(), UUID.randomUUID(), 100_00L, 7, 1L);
        l.remindMs = 12345L;
        assertEquals(12345L, Loan.load(l.save()).remindMs);
        BankData d = new BankData();
        d.addContact(UUID.randomUUID(), "A", "B", "1234", "msg");
        d.contacts().get(0).remindMs = 999L;
        assertEquals(999L, BankData.load(d.save(new CompoundTag())).contacts().get(0).remindMs);
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
