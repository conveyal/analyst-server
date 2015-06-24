package com.conveyal.analyst.server.jobs;

import com.conveyal.geom2gtfs.Main;
import models.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Run geom2gtfs with the given shapefile and config file for the given scenario.
 * @author mattwigway
 *
 */
public class Geom2GtfsJob implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger(Geom2GtfsJob.class);

	private File configFile;
	private File shapeFile;
	private File newFile;
	private Bundle bundle;
	
	public Geom2GtfsJob(Bundle bundle, File configFile, File shapeFile, File newFile) {
		this.bundle = bundle;
		this.configFile = configFile;
		this.shapeFile = shapeFile;
		this.newFile = newFile;
	}
	
	@Override
	public void run() {
		// overwrite
		if (newFile.exists())
			newFile.delete();

		// this is cloogy - we're calling a Java main function from inside a Java program
		try {
			Main.main(new String[] { shapeFile.getAbsolutePath(), configFile.getAbsolutePath(),
					newFile.getAbsolutePath() });
		} catch (Exception e) {
			LOG.error("geom2gtfs error", e);
			return;
		}

		LOG.info("shapefile " + shapeFile.getName() + " processed.");
	}
}
