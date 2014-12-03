package jobs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import models.Scenario;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.geometry.Envelope2D;
import org.opentripplanner.gtfs.model.GTFSFeed;
import org.opentripplanner.gtfs.model.Stop;

import play.Play;
import utils.Bounds;
import utils.HashUtils;

import com.google.common.io.ByteStreams;

import controllers.Application;

/**
 * Process an uploaded GTFS file or shapefile.
 */
public class ProcessTransitScenarioJob implements Runnable {
	private Scenario scenario;
	private File uploadFile;
	private String scenarioType;
	private String augmentScenarioId;
	
	public ProcessTransitScenarioJob(Scenario scenario, File uploadFile,
			String scenarioType, String augmentScenarioId) {
		this.scenario = scenario;
		this.uploadFile = uploadFile;
		this.scenarioType = scenarioType;
		this.augmentScenarioId = augmentScenarioId;
	}
	
	public void run() {

		scenario.processingGtfs = true;
		scenario.save();

		try {

			ZipFile zipFile = new ZipFile(uploadFile);

			Enumeration<? extends ZipEntry> entries = zipFile.entries();

			String shpFile = null;
			String confFile = null;

			while(entries.hasMoreElements()) {

				ZipEntry entry = entries.nextElement();

				if(entry.getName().toLowerCase().endsWith("shp"))
					shpFile = entry.getName();
				if(entry.getName().toLowerCase().endsWith("json"))
					confFile = entry.getName();
			}

			zipFile.close();
			File newFile;

			if(confFile != null && shpFile != null) {

				File outputDirectory = scenario.getTempShapeDirPath();
				zipFile = new ZipFile(uploadFile);
				entries = zipFile.entries();

				while (entries.hasMoreElements()) {

					ZipEntry entry = entries.nextElement();
					File entryDestination = new File(outputDirectory,  entry.getName());

					entryDestination.getParentFile().mkdirs();

					if (entry.isDirectory())
						entryDestination.mkdirs();
					else {
						InputStream in = zipFile.getInputStream(entry);
						OutputStream out = new FileOutputStream(entryDestination);
						IOUtils.copy(in, out);
						IOUtils.closeQuietly(in);
						IOUtils.closeQuietly(out);
					}
				}

				File shapeFile = new File(outputDirectory, shpFile);
				File configFile = new File(outputDirectory, confFile);
				newFile = new File(scenario.getScenarioDataPath(), HashUtils.hashFile(uploadFile) + ".zip");

				ProcessBuilder pb = new ProcessBuilder("java"
						,"-jar",new File(Application.binPath, "geom2gtfs.jar").getAbsolutePath(),  shapeFile.getAbsolutePath(), configFile.getAbsolutePath(), newFile.getAbsolutePath());
				Process p;

				p = pb.start();

				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String line;

				while((line = bufferedReader.readLine()) != null){
					System.out.println("reading...");
					System.out.println(line);
				}

				System.out.println("gtfs " + newFile.getName() + " processed.");

				FileUtils.deleteDirectory(outputDirectory);
				zipFile.close();
				uploadFile.delete();
			}
			else  {
				newFile = new File(scenario.getScenarioDataPath(), scenario.id + ".zip");
				FileUtils.copyFile(uploadFile, newFile);
			}

			if((scenarioType != null && augmentScenarioId != null && scenarioType.equals("augment"))) 
			{	
				for(File f : Scenario.getScenario(augmentScenarioId).getScenarioDataPath().listFiles()) {
					if(f.getName().toLowerCase().endsWith(".zip")) {
						FileUtils.copyFileToDirectory(f, scenario.getScenarioDataPath());
					}
				}
			}

			Envelope2D envelope = new Envelope2D();
			for(File f : scenario.getScenarioDataPath().listFiles()) {
				if(f.getName().toLowerCase().endsWith(".zip")) {
					GTFSFeed feed = GTFSFeed.fromFile(f.getAbsolutePath());

					for(Stop s : feed.stops.values()) {
						Double lat = Double.parseDouble(s.stop_lat);
						Double lon = Double.parseDouble(s.stop_lon);

						envelope.include(lon, lat);
					}
				}
			}

			scenario.bounds = new Bounds(envelope);
			scenario.processingGtfs = false;
			scenario.processingOsm = true;
			scenario.save();

			File osmPbfFile = new File(scenario.getScenarioDataPath(), scenario.id + ".osm.pbf");

			// hard-coding vex osm integration (for now)

			Double south = scenario.bounds.north < scenario.bounds.south ? scenario.bounds.north : scenario.bounds.south;
			Double west = scenario.bounds.east < scenario.bounds.west ? scenario.bounds.east : scenario.bounds.west;
			Double north = scenario.bounds.north > scenario.bounds.south ? scenario.bounds.north : scenario.bounds.south;
			Double east = scenario.bounds.east > scenario.bounds.west ? scenario.bounds.east : scenario.bounds.west;

			String vexUrl = Play.application().configuration().getString("application.vex");

			if (!vexUrl.endsWith("/"))
				vexUrl += "/";

			vexUrl += String.format("?n=%s&s=%s&e=%s&w=%s", north, south, east, west);

			HttpURLConnection conn = (HttpURLConnection) new URL(vexUrl).openConnection();

			conn.connect();

			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				System.err.println("Received response code " +
						conn.getResponseCode() + " from vex server");
				scenario.failed = true;
				scenario.save();
				return;
			}

			// download the file
			ByteStreams.copy(conn.getInputStream(), new FileOutputStream(osmPbfFile));

			System.out.println("osm pbf retrieved");


		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to process gtfs");

			scenario.failed = true;
			scenario.save();

			return;
		}

		scenario.processingGtfs = false;
		scenario.processingOsm = false;
		scenario.save();

		scenario.build();
	}
}
