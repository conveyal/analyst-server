package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.QuotaLedger;
import models.User;
import spark.Request;
import spark.Response;

import java.util.List;

import static spark.Spark.halt;

public class LedgerController extends Controller {
    public static List<QuotaLedger.LedgerEntry> getLedger(Request req, Response res) {
        User u = currentUser(req);

        if (!u.admin)
            halt(UNAUTHORIZED, "Must be an admin to view ledgers");

        String groupId = req.queryParams("groupId");

        return User.getLedgerEntries(groupId);
    }
}
