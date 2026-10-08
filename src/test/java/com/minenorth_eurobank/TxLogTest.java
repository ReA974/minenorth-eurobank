package com.minenorth_eurobank;

import fr.minenorth.api.BankTx;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TxLogTest {
    private static BankTx tx(long n) {
        return new BankTx(n, BankTx.DEPOSIT, n, n, "t" + n, "a");
    }

    @Test
    void keepsOnlyLast200() {
        TxLog log = new TxLog();
        for (long i = 1; i <= 250; i++) log.add(tx(i));
        List<BankTx> l = log.latest(500);
        assertEquals(200, l.size());
        assertEquals(250, l.get(0).time());
        assertEquals(51, l.get(199).time());
    }

    @Test
    void latestIsNewestFirst() {
        TxLog log = new TxLog();
        for (long i = 1; i <= 3; i++) log.add(tx(i));
        List<BankTx> l = log.latest(2);
        assertEquals(3, l.get(0).time());
        assertEquals(2, l.get(1).time());
    }

    @Test
    void latestHonoursLimit() {
        TxLog log = new TxLog();
        for (long i = 1; i <= 10; i++) log.add(tx(i));
        assertEquals(4, log.latest(4).size());
        assertEquals(0, log.latest(0).size());
    }
}
