package com.conveyal.analyst.server.migrations;

import com.conveyal.analyst.server.utils.DataStore;
import com.google.common.io.Files;
import models.Query;

import java.io.File;

/**
 * Update the query complete flag based on whether completePoints equals totalPoints.
 */
public class SetQueryCompletion {
    public static void main (String... args) {
        DataStore.dataPath = Files.createTempDir().getAbsolutePath();

        File input = new File(args[0]);

        if (!input.exists() || !input.isDirectory()) {
            System.err.println("Input is not a directory or does not exist.");
            return;
        }

        DataStore<Query> queryStore = new DataStore<Query>(input, "queries");

        queryStore.getAll().stream().filter(q -> q.totalPoints != null && q.totalPoints.equals(q.completePoints))
                .forEach(q -> {
                    q.complete = true;
                    q.save();
                });
    }
}
