package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import org.mapdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A ledger of quotas.
 */
public class QuotaLedger {
    private static final Logger LOG = LoggerFactory.getLogger(QuotaLedger.class);

    private DB db;

    private BTreeMap<Fun.Tuple3<String, Long, String>, LedgerEntry> entries;

    private BTreeMap<String, Long> credits;

    public QuotaLedger(String dataFile) {
        db = DBMaker
                .newFileDB(new File(AnalystMain.config.getProperty("application.data"), dataFile))
                .make();

        entries = db.createTreeMap("entries")
                .valueSerializer(Serializer.JAVA)
                .makeOrGet();

        credits = db.createTreeMap("credits")
                .valueSerializer(Serializer.LONG)
                .makeOrGet();

        entries.modificationListenerAdd((key, oldVal, newVal) -> {
            // make sure we don't get into concurrency situations
            synchronized (credits) {
                if (oldVal != null) {
                    Long oldTotal = credits.get(oldVal.groupId);

                    if (oldTotal == null)
                        LOG.error("Group ID {} not found in credits database, not crediting account", oldVal.groupId);
                    else
                        credits.put(oldVal.groupId, oldTotal - oldVal.delta);
                }

                if (newVal != null) {
                    Long total = credits.get(newVal.groupId);

                    if (total == null)
                        total = 0l;

                    credits.put(newVal.groupId, total + newVal.delta);
                }
            }
        });
    }

    /** Get all of the ledger entries for a given group, in chronological order */
    public List<LedgerEntry> getLedgerEntries (String groupId, long fromTime, long toTime) {
        // Loop over all entries up to the toTime so that we can compute running totals
        Map<?, LedgerEntry> values = entries.subMap(new Fun.Tuple3(groupId, null, null), new Fun.Tuple3(groupId, toTime, Fun.HI));

        List<LedgerEntry> ret = new ArrayList<>();

        int balance = 0;
        for (LedgerEntry e : values.values()) {
            balance += e.delta;

            // no need to check toTime as it is taken care of by the subMap
            if (e.time > fromTime) {
                e.balance = balance;
                ret.add(e);
            }
        }

        ret.sort((l1, l2) -> Long.compare(l1.time, l2.time));
        return ret;
    }

    /** Get the ledger value (running total) for the given group ID */
    public long getValue (String groupId) {
        Long val = credits.get(groupId);
        return val != null ? val : 0;
    }

    /**
     * Add a ledger entry. There is no corresponding delete function;
     * instead, to refund a ledger entry, simply create a new one with the amount inverted.
     * @param entry
     */
    public void add(LedgerEntry entry) {
        if (entry.groupId == null)
            throw new NullPointerException("Group ID must not be null");

        this.entries.put(new Fun.Tuple3<>(entry.groupId, entry.time, entry.id), entry);
        db.commit();
    }

    public static final class LedgerEntry implements Serializable {
        private static final long serialVersionUID = 1;

        public final String id = UUID.randomUUID().toString();

        /** The user who initiated this action */
        public String userId;

        /** The group this action affects */
        public String groupId;

        /** The amount of quota used (positive) or gained (negative) as a result of this action. */
        public int delta;

        /** The query this is associated with, if any */
        public String query;

        /** The epoch time this action was taken */
        public long time = System.currentTimeMillis();

        /** Format the time in ISO format */
        public String getTime () {
            // Users are all over the world, so charge in UTC
            ZonedDateTime ztd = Instant.ofEpochMilli(time).atZone(ZoneId.of("Etc/UTC"));
            return ztd.format(DateTimeFormatter.ISO_DATE_TIME);
        }

        /** The ID of the parent ledger entry (group ID assumed to be same) */
        public String parentId;

        /** why was this action taken */
        public LedgerReason reason;

        /** comments on this action */
        public String note;

        /** Balance after this action took place */
        public transient int balance;

        /** getter so Jackson will include balance */
        public int getBalance () {
            return balance;
        }
    }

    public static enum LedgerReason {
        /** user-initiated single point job */
        SINGLE_POINT,

        /** Query created */
        QUERY_CREATED,

        /** Query deleted, partial refund for points not completed */
        QUERY_PARTIAL_REFUND,

        /** Query failed, full refund */
        QUERY_FAILED_REFUND,

        /** user purchased more credits (future use) */
        PURCHASE,

        /** A refund for another reason */
        OTHER_REFUND
    }
}
