package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import com.conveyal.analyst.server.utils.QuotaLedger;
import com.stormpath.sdk.group.Group;
import models.Query;
import models.User;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static spark.Spark.halt;

public class LedgerController extends Controller {
    /** Get the ledger for a particular user */
    public static List<QuotaLedger.LedgerEntry> getLedger(Request req, Response res) {
        User u = currentUser(req);

        if (!u.admin)
            halt(UNAUTHORIZED, "Must be an admin to view ledgers");

        String groupId = req.queryParams("group");

        // TODO: if you're looking at this at midnight UTC on Dec 31, this may get the wrong year depending on where the server is.
        int year = req.queryParams("year") != null ? Integer.parseInt(req.queryParams("year")) : LocalDateTime.now().getYear();
        Month month = req.queryParams("month") != null ? Month.valueOf(req.queryParams("month").toUpperCase()) : LocalDateTime.now().getMonth();

        // figure out the times
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1);

        long from = start.toInstant(ZoneOffset.UTC).toEpochMilli();
        long to = end.toInstant(ZoneOffset.UTC).toEpochMilli();

        return User.getLedgerEntries(groupId, from, to);
    }

    /** Create a ledger entry */
    public static QuotaLedger.LedgerEntry createLedgerEntry (Request req, Response res) throws IOException {
        User u = currentUser(req);

        if (!u.admin)
            halt(UNAUTHORIZED, "Must be an admin to edit ledgers");

        QuotaLedger.LedgerEntry entry = JsonUtil.getObjectMapper().readValue(req.body(),
                QuotaLedger.LedgerEntry.class);

        // enforce
        entry.userId = u.username;
        entry.time = System.currentTimeMillis();

        User.addLedgerEntry(entry);

        return entry;
    }

    /** Get a list of all group names */
    public static List<String> getGroups (Request req, Response res) {
        User u = currentUser(req);

        if (!u.admin)
            halt(UNAUTHORIZED, "Must be an admin to view groups");

        List<String> ret = new ArrayList<>();
        for (Group g : Authentication.getAllGroups()) {
            ret.add(g.getName());
        }

        return ret;
    }

    /** Refund a ledger entry (POST the JSON of the ledger entry that you wish to refund to this method) */
    public static QuotaLedger.LedgerEntry refund (Request req, Response res) throws IOException {
        User u = currentUser(req);

        if (!u.admin)
            halt(UNAUTHORIZED, "Must be an admin to view groups");

        // parse out the ledger entry
        QuotaLedger.LedgerEntry entry = JsonUtil.getObjectMapper().readValue(req.body(),
                QuotaLedger.LedgerEntry.class);

        // get our own, local copy of this ledger entry
        entry = User.ledger.getEntry(entry.id);

        if (entry == null)
            halt(BAD_REQUEST);

        entry.refunded = true;
        // will overwrite
        User.ledger.add(entry);

        // is this associated with a query?
        if (entry.query != null)
            return Query.refundFull(entry.query, u);
        else {
            QuotaLedger.LedgerEntry refund = new QuotaLedger.LedgerEntry();
            refund.parentId = entry.id;
            refund.delta = -1 * entry.delta;
            refund.groupId = entry.groupId;
            refund.reason = QuotaLedger.LedgerReason.OTHER_REFUND;
            refund.userId = u.username;
            User.ledger.add(refund);
            return refund;
        }
    }
}
