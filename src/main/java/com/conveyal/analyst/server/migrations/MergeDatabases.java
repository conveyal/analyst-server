package com.conveyal.analyst.server.migrations;

import com.conveyal.analyst.server.utils.DataStore;
import com.conveyal.analyst.server.utils.QueryResultStore;
import com.google.common.io.Files;
import models.*;
import org.apache.commons.io.FileUtils;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Merge the database specified as the first argument into the database specified as the second.
 */
public class MergeDatabases implements Runnable {
    public static void main (String... args) {
        if (args.length != 2) {
            System.err.println("Usage: merge-databases database into-database");
        }

        new MergeDatabases(new File(args[0]), new File(args[1])).run();
    }

    private File from, into;

    public MergeDatabases(File from, File into) {
        if (!from.exists() || !from.isDirectory())
            throw new RuntimeException("From database does not exist or is not directory!");

        if (!into.exists() || !into.isDirectory())
            throw new RuntimeException("Into database does not exist or is not directory!");

        this.from = from;
        this.into = into;

        // most classes when loaded create their own datastores. point them off into space
        File tempDir = Files.createTempDir();
        tempDir.deleteOnExit();
        DataStore.dataPath = tempDir.getAbsolutePath();
    }

    public void run () {
        try {
            System.err.println("Merging bundles");
            mergeBundles();
            System.err.println("done");

            System.err.println("Merging projects");
            this.<Project>merge("projects");
            System.err.println("done");

            System.err.println("Merging queries");
            this.mergeQueries();
            System.err.println("done");

            System.err.println("Merging shapefiles");
            this.mergeShapefiles();
            System.err.println("done");

            System.err.println("Merging scenarios");
            this.<TransportScenario>merge("transport_scenario_data");
            System.err.println("done");

            System.err.println("Merge complete");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Merge the bundles, bundle data, and bundle shapes */
    public void mergeBundles () throws IOException {
        // bundles are a normal datastore
        this.<Bundle>merge("bundle");

        // merge bundle shapes
        DB shpSrc = DBMaker.newFileDB(new File(from, "bundle_shapes.db"))
                .readOnly()
                .make();

        DB shpDest = DBMaker.newFileDB(new File(into, "bundle_shapes.db"))
                .asyncWriteEnable()
                .asyncWriteFlushDelay(10000)
                .make();

        // note not using classloaderserializer; it's no longer needed as we no longer use play
        Map<Fun.Tuple2<String, Long>, Bundle.TransitSegment> mSrc  = shpSrc. getTreeMap("segments");
        // note not using classloaderserializer; it's no longer needed as we no longer use play
        Map<Fun.Tuple2<String, Long>, Bundle.TransitSegment> mDest = shpDest.getTreeMap("segments");

        // NB: the segment number is usually globally unique, but that's for convenience.
        // there's no requirement that it be unique except within a particular shape.
        mDest.putAll(mSrc);

        shpSrc.close();
        shpDest.commit();
        shpDest.close();

        // merge bundle data
        this.mergeFiles("graphs");
    }

    /** merge queries, taking care of migrating results to new format if needed */
    public void mergeQueries () throws IOException {
        this.<Query>merge("queries");

        File srcFlatResults = new File(from, "flat_results");
        File srcMapdbResults = new File(from, "query_results");
        File destResults = new File(into, "flat_results");

        // if flat results already exist, just copy them
        if (srcFlatResults.exists() && srcFlatResults.listFiles().length > 0) {
            this.mergeFiles("flat_results");
        }
        else if (srcMapdbResults.exists()) {
            DataStore<Query> queryDataStore = new DataStore<Query>(from, "queries");


            for (Query q : queryDataStore.getAll()) {
                // if there are no results we're not going to find them
                if (q.completePoints == null || q.completePoints == 0)
                    continue;

                String id = q.id;

                DB db = DBMaker.newFileDB(new File(srcMapdbResults, id + ".db"))
                        .mmapFileEnable()
                        .transactionDisable()
                        .readOnly()
                        .make();

                QueryResultStore dest = new QueryResultStore(id, false, destResults);

                for (Object o : db.getAll().values()) {
                    if (o instanceof BTreeMap) {
                        Map<String, utils.ResultEnvelope> map = (Map<String, utils.ResultEnvelope>) o;

                        map.values().stream()
                                // convert to OTP result envelopes
                                .map(re -> {
                                    org.opentripplanner.analyst.cluster.ResultEnvelope out = new org.opentripplanner.analyst.cluster.ResultEnvelope();
                                    out.id = re.id;
                                    out.avgCase = re.avgCase;
                                    out.bestCase = re.bestCase;
                                    out.destinationPointsetId = re.shapefile;
                                    out.jobId = re.id;
                                    out.pointEstimate = re.pointEstimate;
                                    out.profile = re.profile;
                                    out.spread = re.spread;
                                    out.worstCase = re.worstCase;
                                    return out;
                                    // NB: there is a result envelope per variable in the original store, so we're saving results multiple times
                                    // for each feature. This is OK though because the queryresultstore is just splitting them up again.
                                }).forEach(dest::store);
                    }
                }
                db.close();
                dest.close();
            }
            queryDataStore.close();
        }
    }

    public void mergeShapefiles () throws IOException {
        this.<Shapefile>merge("shapes");
        this.mergeFiles("shape_data");
    }

    /** generic merge function */
    public <T> void merge (String name) {
        // simple. since they're using UUIDs, just add them directly
        DataStore<T> src = new DataStore<>(from, name);
        DataStore<T> dest = new DataStore<>(into, name, true, false, true);
        dest.addAll(src);
        dest.close();
        src.close();
    }

    /** copy every file and directory in the given directory */
    public void mergeFiles (String dirName) throws IOException {
        File src = new File(from, dirName);
        File dest = new File(into, dirName);

        for (File f : src.listFiles()) {
            if (f.getName().startsWith(".")) continue;

            if (f.isDirectory()) {
                FileUtils.copyDirectory(f, new File(dest, f.getName()));
            } else {
                FileUtils.copyFile(f, new File(dest, f.getName()));
            }
        }
    }
}
