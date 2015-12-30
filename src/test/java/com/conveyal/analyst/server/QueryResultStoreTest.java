package com.conveyal.analyst.server;

import com.conveyal.analyst.server.utils.QueryResultStore;
import com.conveyal.r5.analyst.Histogram;
import com.conveyal.r5.analyst.ResultSet;
import com.conveyal.r5.analyst.cluster.ResultEnvelope;
import com.google.common.io.Files;
import junit.framework.TestCase;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Test that query result stores work.
 */
public class QueryResultStoreTest extends TestCase{
    @Test
    public void testQueryResultStore () {
        // make some fake query results
        Histogram h1 = new Histogram();

        h1.counts = new int[120];
        h1.sums = new int[120];

        for (int i = 0; i < 120; i++) {
            h1.counts[i] = i * i - (i -1) * (i - 1);
            h1.sums[i] = h1.counts[i] * 5;
        }

        // and a shorter histogram
        Histogram h2 = new Histogram();

        h2.sums = new int[100];
        h2.counts = new int[100];

        // make a variety of result sets
        ResultSet single = new ResultSet();
        single.id = "single";
        single.histograms.put("test", h1);

        ResultSet dbl = new ResultSet();
        dbl.histograms.put("one", h1);
        dbl.histograms.put("two", h2);
        dbl.id = "double";

        // mash them up into result envelopes
        ResultEnvelope sre = new ResultEnvelope();
        sre.worstCase = single;
        sre.bestCase = single;
        sre.avgCase = single;
        sre.id = single.id;

        ResultEnvelope dre = new ResultEnvelope();
        dre.worstCase = dbl;
        dre.bestCase = dbl;
        dre.avgCase = dbl;
        dre.id = dbl.id;

        ResultSet mdbl = new ResultSet();
        mdbl.histograms.put("one", h2);
        mdbl.histograms.put("two", h1);
        mdbl.id = "mixed";

        ResultSet msingle = new ResultSet();
        msingle.histograms.put("one", h2);
        msingle.id = "mixed";

        ResultEnvelope mixed = new ResultEnvelope();
        mixed.worstCase = msingle;
        mixed.avgCase = new ResultSet();
        mixed.avgCase.id = "mixed";
        mixed.bestCase = mdbl;
        mixed.id = "mixed";

        // get a temporary directory
        File tempDir = Files.createTempDir();

        QueryResultStore qrs = new QueryResultStore("test", false, tempDir);

        qrs.store(sre);
        qrs.store(dre);
        qrs.store(mixed);

        qrs.close();

        qrs = new QueryResultStore("test", true, tempDir);

        ResultSet res;

        // now test that we got back what we expected to
        // for worst case there should be two results for the variable one, one for dre and one for mixed
        Iterator<ResultSet> oneWorst = qrs.getAll("one", ResultEnvelope.Which.WORST_CASE);

        res = oneWorst.next();
        assertEquals("double", res.id);
        histogramEquals(h1, res.histograms.get("one"));

        res = oneWorst.next();
        assertEquals("mixed", res.id);
        histogramEquals(h2, res.histograms.get("one"));

        assertFalse(oneWorst.hasNext());

        // should be only one from single
        Iterator<ResultSet> testAvg = qrs.getAll("test", ResultEnvelope.Which.AVERAGE);

        res = testAvg.next();
        assertEquals("single", res.id);
        histogramEquals(h1, res.histograms.get("test"));

        assertFalse(testAvg.hasNext());

        // Should be two: one from double and one from mixed
        Iterator<ResultSet> twoBest = qrs.getAll("two", ResultEnvelope.Which.BEST_CASE);

        res = twoBest.next();
        assertEquals("double", res.id);
        histogramEquals(h2, res.histograms.get("two"));

        res = twoBest.next();
        assertEquals("mixed", res.id);
        histogramEquals(h1, res.histograms.get("two"));

        assertFalse(twoBest.hasNext());
    }

    public static void histogramEquals (Histogram expected, Histogram actual) {
            assertNotNull(expected);
            assertNotNull(actual);

            assertTrue(Arrays.equals(expected.counts, expected.counts));
            assertTrue(Arrays.equals(actual.sums, actual.sums));

    }
}
