package com.conveyal.analyst.server.jobs;

import com.conveyal.analyst.server.AnalystMain;
import models.Bundle;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Run geom2gtfs with the given shapefile and config file for the given scenario.
 * @author mattwigway
 *
 */
public class Geom2GtfsJob implements Runnable {
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
		try {			
			// overwrite
			if (newFile.exists())
				newFile.delete();
	
			ProcessBuilder pb = new ProcessBuilder("java"
					,"-jar",new File(AnalystMain.config.getProperty("application.bin"), "geom2gtfs.jar").getAbsolutePath(),  shapeFile.getAbsolutePath(), configFile.getAbsolutePath(), newFile.getAbsolutePath());
			// show messages from stderr as well
			pb.redirectErrorStream(true);
			Process p;
			p = pb.start();
	
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
	
			while((line = bufferedReader.readLine()) != null){
				System.out.println("geom2gtfs: " + line); 
			}
	
			System.out.println("shapefile " + shapeFile.getName() + " processed.");
		} catch (IOException e) {
			// rethrow as unchecked
			throw new RuntimeException(e);
		}
	}

}
