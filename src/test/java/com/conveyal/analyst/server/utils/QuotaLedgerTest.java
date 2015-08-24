package com.conveyal.analyst.server.utils;

import com.google.common.io.Files;
import junit.framework.TestCase;
import models.Query;
import models.User;
import org.junit.Before;
import org.junit.Test;

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
        DataStore.dataPath = Files.createTempDir().getAbsolutePath();
        
        // stored in temporary directory created above
        ledger = new QuotaLedger("ledger");

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

    @Test
    public void testMultiPointPartialRefund () {
        Query q = Query.create();
        q.totalPoints = 100;
        q.completePoints = 30;

        // create a multipoint query
        QuotaLedger.LedgerEntry e = new QuotaLedger.LedgerEntry();
        e.delta = -100;
        e.groupId = "TEST";
        e.userId = "TEST";
        e.query = q.id;
        e.reason = QuotaLedger.LedgerReason.QUERY_CREATED;

        ledger.add(e);

        QuotaLedger.LedgerEntry partialRefund = ledger.refundQueryPartial(q, user);
        assertEquals(e.id, partialRefund.parentId);
        assertEquals(q.totalPoints - q.completePoints, partialRefund.delta);
        assertEquals(q.id, e.query);
        assertEquals(2, ledger.getEntriesForQuery(q.id).size());
        assertEquals(-30, ledger.getValue("TEST"));
        assertEquals(-30, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(1).balance);

        // try to partially refund again
        assertNull(ledger.refundQueryPartial(q, user));

        // fully refund
        QuotaLedger.LedgerEntry refund = ledger.refundQuery(q.id, user);
        assertEquals(e.id, refund.parentId);
        assertEquals((int) q.completePoints, refund.delta);
        assertEquals(q.id, e.query);
        assertEquals(3, ledger.getEntriesForQuery(q.id).size());
        assertEquals(0, ledger.getValue("TEST"));
        assertEquals(0, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(2).balance);
    }

    @Test
    public void testMultipointFullRefund () {
        Query q = Query.create();
        q.totalPoints = 100;
        q.completePoints = 30;

        // create a multipoint query
        QuotaLedger.LedgerEntry e = new QuotaLedger.LedgerEntry();
        e.delta = -100;
        e.groupId = "TEST";
        e.userId = "TEST";
        e.query = q.id;
        e.reason = QuotaLedger.LedgerReason.QUERY_CREATED;

        ledger.add(e);

        // fully refund
        QuotaLedger.LedgerEntry refund = ledger.refundQuery(q.id, user);
        assertEquals(e.id, refund.parentId);
        assertEquals((int) q.totalPoints, refund.delta);
        assertEquals(q.id, e.query);
        assertEquals(2, ledger.getEntriesForQuery(q.id).size());
        assertEquals(0, ledger.getValue("TEST"));
        assertEquals(0, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(1).balance);

        // try to partially refund again
        assertNull(ledger.refundQueryPartial(q, user));

        // try to fully refund again
        assertNull(ledger.refundQuery(q.id, user));

        assertEquals(2, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).size());
    }

    @Test
    public void testMultipleUsers () {
        User u2 = new User("test2", "test2");

        QuotaLedger.LedgerEntry e1 = new QuotaLedger.LedgerEntry();
        e1.delta = -1;
        e1.groupId = "TEST";
        e1.userId = "TEST";
        e1.reason = QuotaLedger.LedgerReason.SINGLE_POINT;
        ledger.add(e1);

        QuotaLedger.LedgerEntry e2 = new QuotaLedger.LedgerEntry();
        e2.delta = 200;
        e2.groupId = "test2";
        e2.userId = "test2";
        e2.reason = QuotaLedger.LedgerReason.PURCHASE;
        ledger.add(e2);

        QuotaLedger.LedgerEntry e3 = new QuotaLedger.LedgerEntry();
        e3.delta = -1;
        e3.groupId = "test2";
        e3.userId = "test2";
        e3.reason = QuotaLedger.LedgerReason.SINGLE_POINT;
        ledger.add(e3);

        assertEquals(-1, ledger.getValue("TEST"));
        assertEquals(1, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).size());
        assertEquals(-1, ledger.getLedgerEntries("TEST", 0, Long.MAX_VALUE).get(0).balance);

        assertEquals(199, ledger.getValue("test2"));
        assertEquals(2, ledger.getLedgerEntries("test2", 0, Long.MAX_VALUE).size());
        assertEquals(199, ledger.getLedgerEntries("test2", 0, Long.MAX_VALUE).get(1).balance);
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