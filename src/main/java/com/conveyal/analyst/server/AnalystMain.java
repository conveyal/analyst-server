package com.conveyal.analyst.server;

import com.conveyal.analyst.server.utils.QueueManager;
import models.Bundle;
import models.Query;
import models.Shapefile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static spark.SparkBase.ipAddress;
import static spark.SparkBase.port;

public class AnalystMain {
	private static final Logger LOG = LoggerFactory.getLogger(AnalystMain.class);

	public static final Properties config = new Properties();

	public static final Properties gitVersion = new Properties();

	static {
		try {
			InputStream is = AnalystMain.class.getClassLoader().getResourceAsStream("git.properties");
			gitVersion.load(is);
			is.close();
		} catch (Exception e) {
			LOG.error("Error while loading git commit information", e);
			LOG.info("If you are working within an IDE, run a command line Maven build to ensure a git.properties file is present.");
		}
	}

	public static void main (String... args) throws Exception {
		LOG.info("Welcome to Transport Analyst by conveyal");
		LOG.info("Reading properties . . .");
		// TODO don't hardwire
		FileInputStream in;

		if (args.length == 0)
			in = new FileInputStream(new File("application.conf"));
		else
			in = new FileInputStream(new File(args[0]));

		config.load(in);
		in.close();

		LOG.info("Initializing datastore . . .");
		initialize();

		// figure out host and port
		int portNo = Integer.parseInt(config.getProperty("application.port", "9090"));
		String ip = config.getProperty("application.ip");
		if (ip != null) ipAddress(ip);

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

		// accumulate results from running queries
		for (Query q : Query.getAll()) {
			// migration: queries used to have the same origin and destination shapefile, by definition.
			// if a query doesn't have an explicit origin shapefile, set the origin shapefile to the same as the
			// destination shapefile.

			boolean modified = (q.originShapefileId == null || q.destinationShapefileId == null) && q.shapefileId != null;
			if (q.originShapefileId == null && q.shapefileId != null)
				q.originShapefileId = q.shapefileId;

			if (q.destinationShapefileId == null && q.shapefileId != null)
				q.destinationShapefileId = q.shapefileId;

			if (modified)
				q.save();

			if (!q.complete) {
				QueueManager.getManager().addCallback(q.id, q::updateStatus);
			}
		}
	}
}
