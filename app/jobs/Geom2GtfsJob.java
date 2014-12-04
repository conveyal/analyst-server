package jobs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.io.FileUtils;

import utils.HashUtils;
import controllers.Application;
import models.Scenario;

/**
 * Run geom2gtfs with the given shapefile and config file for the given scenario.
 * @author mattwigway
 *
 */
public class Geom2GtfsJob implements Runnable {
	private File configFile;
	private File shapeFile;
	private File newFile;
	private Scenario scenario;
	
	public Geom2GtfsJob(Scenario scenario, File configFile, File shapeFile, File newFile) {
		this.scenario = scenario;
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
					,"-jar",new File(Application.binPath, "geom2gtfs.jar").getAbsolutePath(),  shapeFile.getAbsolutePath(), configFile.getAbsolutePath(), newFile.getAbsolutePath());
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
