package com.conveyal.analyst.server;

import models.Bundle;
import models.Query;
import models.Shapefile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static spark.Spark.port;

public class AnalystMain {
	private static final Logger LOG = LoggerFactory.getLogger(AnalystMain.class);

	public static final Properties config = new Properties();

	public static void main (String... args) throws Exception {
		LOG.info("Welcome to Transport Analyst by conveyal");
		LOG.info("Reading properties . . .");
		// TODO don't hardwire
		FileInputStream in = new FileInputStream(new File("application.conf"));
		config.load(in);
		in.close();

		LOG.info("Initializing datastore . . .");
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
			LOG.error("error accessing S3", e);
			throw new RuntimeException(e);
		}

		for (Query q : Query.getAll()) {
			if (q.completePoints == null || !q.completePoints.equals(q.totalPoints)) {
				// TODO accumulate results
			}
		}
	}
}
