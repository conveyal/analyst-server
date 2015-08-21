package com.conveyal.analyst.server.utils;

import junit.framework.TestCase;
import models.User;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Test to ensure that the quota ledger works as advertised */
public class QuotaLedgerTest extends TestCase {
    private QuotaLedger ledger;

    private User user;

    private List<QuotaLedger.LedgerEntry> singlePointQueries;

    @Before
    public void setUp () throws Exception {
        ledger = new QuotaLedger(File.createTempFile("ledger", ".db").getAbsolutePath());

        user = new User("TEST", "TEST");
    }

    /** Make sure that single point jobs are recorded and charged correctly */
    @Test
    public void testSingleCharge () {
        createSinglePointQueries();

        // make sure that the counter works
        assertEquals(-100, ledger.getValue("TEST"));
        // make sure they all made it in
        assertEquals(100, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).size());
        // make sure the balance is correct
        assertEquals(-100, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(99).balance);
    }

    /** Make sure that refunding single point queries works */
    @Test
    public void testSingleRefund () {
        createSinglePointQueries();

        // refund a few of them
        QuotaLedger.LedgerEntry e1 = singlePointQueries.get(10);
        QuotaLedger.LedgerEntry r1 = ledger.refund(e1, user);

        assertEquals(e1.id, r1.parentId);
        assertEquals(-e1.delta, r1.delta);
        assertEquals(QuotaLedger.LedgerReason.OTHER_REFUND, r1.reason);

        assertEquals(-99, ledger.getValue("TEST"));

        assertEquals(101, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).size());
        // make sure the balance is correct
        assertEquals(-99, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(100).balance);

        // should not be able to refund same query twice
        assertNull(ledger.refund(e1, user));

        // nothing should have changed
        assertEquals(-99, ledger.getValue("TEST"));

        assertEquals(101, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).size());
        // make sure the balance is correct
        assertEquals(-99, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(100).balance);

        // do it again
        createSinglePointQueries();

        QuotaLedger.LedgerEntry e2 = singlePointQueries.get(10);
        QuotaLedger.LedgerEntry r2 = ledger.refund(e2, user);

        assertEquals(e2.id, r2.parentId);
        assertEquals(-e2.delta, r2.delta);
        assertEquals(QuotaLedger.LedgerReason.OTHER_REFUND, r2.reason);

        assertEquals(-198, ledger.getValue("TEST"));

        assertEquals(202, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).size());
        // make sure the balance is correct
        assertEquals(-198, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(201).balance);
    }

    /** Create single point queries */
    private void createSinglePointQueries () {
        singlePointQueries = IntStream.range(0, 100).mapToObj(i -> {
                QuotaLedger.LedgerEntry e = new QuotaLedger.LedgerEntry();
                e.reason = QuotaLedger.LedgerReason.SINGLE_POINT;
                e.delta = -1;
                e.userId = "TEST";
                e.groupId = "TEST";
                return e;
            })
            .collect(Collectors.toList());

        singlePointQueries.forEach(ledger::add);
    }
}