package com.minenorth_eurobank;

import fr.minenorth.api.BankTx;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BankDataBusinessTest {
    @Test
    void businessIsHiddenFromPlayersButCountedInTotal() {
        BankData d = new BankData();
        UUID p = UUID.randomUUID(), b = UUID.randomUUID(), s = UUID.randomUUID();
        d.open(p);
        d.rename(p, "Jean");
        d.add(p, 100);
        d.openBusiness(b, "Acme");
        d.add(b, 500);
        d.setSigners(b, Set.of(s));
        d.record(b, new BankTx(1, BankTx.DEPOSIT, 500, 500, "x", "Jean"));

        assertNull(d.findByName("Acme"));
        assertEquals(p, d.findByName("Jean"));
        assertFalse(d.all().containsKey(b));
        assertEquals(600, d.total());

        BankData r = BankData.load(d.save(new CompoundTag()));
        assertTrue(r.isBusiness(b));
        assertEquals(Set.of(s), r.signers(b));
        assertEquals(1, r.history(b, 10).size());
        assertEquals("Jean", r.history(b, 10).get(0).actor());
        assertNull(r.findByName("Acme"));

        r.closeBusiness(b);
        assertFalse(r.has(b));
        assertTrue(r.signers(b).isEmpty());
        assertTrue(r.history(b, 10).isEmpty());
    }
}
