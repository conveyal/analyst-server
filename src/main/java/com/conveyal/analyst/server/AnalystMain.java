package com.conveyal.analyst.server;

import models.Bundle;
import models.Query;
import models.Shapefile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static spark.Spark.*;

public class AnalystMain {
	private static final Logger LOG = LoggerFactory.getLogger(AnalystMain.class);

	public static void main (String... args) {
		LOG.info("initializing datastore . . .")
		initialize();

		// parse out the port number
		int portNo = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
		LOG.info("Starting up server on port {}", portNo);

		port(portNo);

		// set routes
		Routes.routes();


	}

	/** initialize the database */
	public static void initialize () {
		Bundle.importBundlesAsNeeded();

		// upload to S3
		try {
			Bundle.writeAllToClusterCache();
			Shapefile.writeAllToClusterCache();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		for (Query q : Query.getAll()) {
			if (q.completePoints == null || !q.completePoints.equals(q.totalPoints)) {
				// TODO accumulate results
			}
		}
	}
}
