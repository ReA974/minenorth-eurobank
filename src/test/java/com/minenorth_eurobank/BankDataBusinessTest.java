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

    @Test
    void listedBusinessesIsSortedAndOnlyListed() {
        BankData d = new BankData();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), p = UUID.randomUUID();
        d.openBusiness(a, "zeta");
        d.openBusiness(b, "Alpha");
        d.openBusiness(c, "Hidden");
        d.open(p);
        d.setListed(a, true);
        d.setListed(b, true);
        d.setListed(p, true);   // compte joueur : sans effet
        assertTrue(d.isListed(a));
        assertFalse(d.isListed(c));
        assertFalse(d.isListed(p));
        var l = d.listedBusinesses();
        assertEquals(2, l.size());
        assertEquals(b, l.get(0).getKey());
        assertEquals("Alpha", l.get(0).getValue());
        assertEquals(a, l.get(1).getKey());
        assertEquals("zeta", l.get(1).getValue());
    }

    @Test
    void listingSurvivesSaveLoad() {
        BankData d = new BankData();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        d.openBusiness(a, "Acme");
        d.openBusiness(b, "Bolt");
        d.setListed(a, true);
        BankData r = BankData.load(d.save(new CompoundTag()));
        assertTrue(r.isListed(a));
        assertFalse(r.isListed(b));
        assertEquals(1, r.listedBusinesses().size());
        assertTrue(BankData.load(new CompoundTag()).listedBusinesses().isEmpty());
    }

    @Test
    void closeUnlists() {
        BankData d = new BankData();
        UUID a = UUID.randomUUID();
        d.openBusiness(a, "Acme");
        d.setListed(a, true);
        d.closeBusiness(a);
        assertFalse(d.isListed(a));
        assertTrue(d.listedBusinesses().isEmpty());
    }
}
