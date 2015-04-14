package jobs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import models.Scenario;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.geotools.geometry.Envelope2D;
import org.joda.time.DateTimeZone;
import org.mapdb.Fun;
import org.mapdb.Fun.Tuple2;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Agency;
import com.conveyal.gtfs.model.Shape;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.conveyal.otpac.ClusterGraphService;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.index.strtree.STRtree;

import play.Logger;
import play.Play;
import utils.Bounds;
import utils.HashUtils;
import utils.ZipUtils;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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
			
			// this allows one to upload a ZIP file full of GTFS files and have them all added.
			List<ZipEntry> zips = Lists.newArrayList(); 
			
			while(entries.hasMoreElements()) {

				ZipEntry entry = entries.nextElement();
				
				// don't try to use the weird Apple files
				if (entry.getName().startsWith("__MACOSX"))
					continue;

				if (entry.getName().toLowerCase().endsWith(".shp") && !entry.isDirectory())
					shpFile = entry.getName();
				
				if (entry.getName().toLowerCase().endsWith(".json") && !entry.isDirectory())
					confFile = entry.getName();
				
				if (entry.getName().toLowerCase().endsWith(".zip") && !entry.isDirectory())
					zips.add(entry);
			}

			File newFile;

			File outputDirectory = scenario.getTempShapeDirPath();
			
			// the files that are needed for this graph build
			List<File> graphFiles = new ArrayList<File>(2);
			
			if (confFile != null && shpFile != null) {				
				zipFile = new ZipFile(uploadFile);

				ZipUtils.unzip(zipFile, outputDirectory);

				File shapeFile = new File(outputDirectory, shpFile);
				File configFile = new File(outputDirectory, confFile);
				
				newFile = new File(scenario.getScenarioDataPath(), HashUtils.hashFile(uploadFile) + ".zip");
				new Geom2GtfsJob(scenario, configFile, shapeFile, newFile).run();

				FileUtils.deleteDirectory(outputDirectory);
				zipFile.close();
				uploadFile.delete();
				graphFiles.add(newFile);
			}
			else if (!zips.isEmpty()) {
				int i = 0;
				for (ZipEntry ze : zips) {
					File file = new File(scenario.getScenarioDataPath(), scenario.id + "_gtfs_" + (i++) + ".zip");
					ZipUtils.unzip(zipFile, ze, file);
					graphFiles.add(file);
				}
			}
			else  {
				newFile = new File(scenario.getScenarioDataPath(), scenario.id + "_gtfs.zip");
				FileUtils.copyFile(uploadFile, newFile);
				graphFiles.add(newFile);
			}
			
			zipFile.close();
			
			if((scenarioType != null && augmentScenarioId != null && scenarioType.equals("augment"))) 
			{	
				for(File f : Scenario.getScenario(augmentScenarioId).getScenarioDataPath().listFiles()) {
					if(f.getName().toLowerCase().endsWith(".zip")) {
						FileUtils.copyFileToDirectory(f, scenario.getScenarioDataPath());
						graphFiles.add(new File(scenario.getScenarioDataPath(), f.getName()));
					}
				}
			}

			scenario.processGtfs();
			scenario.processingGtfs = false;
			scenario.processingOsm = true;
			scenario.save();

			File osmPbfFile = new File(scenario.getScenarioDataPath(), scenario.id + ".osm.pbf");

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
			InputStream is = conn.getInputStream();
			OutputStream os = new FileOutputStream(osmPbfFile);
			ByteStreams.copy(is, os);
			is.close();
			os.close();
			
			graphFiles.add(osmPbfFile);

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
		
		try {
			scenario.writeToClusterCache();
		} catch (IOException e) {
			e.printStackTrace();
			Logger.error("Failed to write graph to cluster cache");
		}
	}
}
