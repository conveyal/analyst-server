package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;

/**
 * Store and update quotas atomically.
 */
public class QuotaStore {
    private DB db;

    public QuotaStore(String dataFile) {
        db = DBMaker
                .newFileDB(new File(AnalystMain.config.getProperty("application.data"), dataFile))
                .make();
    }

    public long getQuotaUsage(String username) {
        return db.getAtomicLong(username).get();
    }

    public long incrementQuotaUsage(String username, long increment) {
        long val = db.getAtomicLong(username).addAndGet(increment);
        db.commit();
        return val;
    }
}
