package models;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.geometry.Envelope2D;
import org.mapdb.Bind;
import org.mapdb.Fun;
import org.opentripplanner.gtfs.model.GTFSFeed;
import org.opentripplanner.gtfs.model.Stop;
import org.opentripplanner.routing.graph.Graph;

import play.Logger;
import play.libs.Akka;
import play.libs.F.Function0;
import play.libs.F.Promise;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import utils.Bounds;
import utils.DataStore;
import utils.HashUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Geometry;

import controllers.Api;
import controllers.Application;

public class Scenario implements Serializable {

	private static final long serialVersionUID = 1L;

	static DataStore<Scenario> scenarioData = new DataStore<Scenario>("scenario");

	public String id;
	public String projectId;
	public String name;
	public String description;
	
	public Boolean processingGtfs = false;
	public Boolean processingOsm = false;
	public Boolean failed = false;
	
	public Bounds bounds;
	
	public Scenario() {
		
	}
	
	public String getStatus() {
		
		if(processingGtfs)
			return "PROCESSSING_GTFS";
		else if(processingOsm) 
			return "PROCESSSING_OSM";
		else 
			return Api.analyst.getGraphStatus(id);
		
	}
	
	static public Scenario create(final File gtfsFile, final String scenarioType, final String augmentScenarioId) throws IOException {
		
		final Scenario scenario = new Scenario();
		scenario.save();
		
		scenario.processGtfs(gtfsFile, scenarioType, augmentScenarioId);
		
		return scenario;
	}
	
	public List<String> getFiles() {
		
		ArrayList<String> files = new ArrayList<String>();
		
		for(File f : this.getScenarioDataPath().listFiles()) {
			files.add(f.getName());
		}
		
		return files;	
	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("sc_" + d.toString());
			
			Logger.info("created scenario s " + id);
		}
		
		scenarioData.save(id, this);
		
		Logger.info("saved scenario s " +id);
	}
	
	@JsonIgnore
	private static File getScenarioDir() {
		File scenarioPath = new File(Application.dataPath, "graphs");
		
		scenarioPath.mkdirs();
		
		return scenarioPath;
	}
	
	@JsonIgnore
	private File getScenarioDataPath() {
		
		File scenarioDataPath = new File(getScenarioDir(), id);
		
		scenarioDataPath.mkdirs();
		
		return scenarioDataPath;
	}
	
	public void delete() throws IOException {
		scenarioData.delete(id);
		
		FileUtils.deleteDirectory(getScenarioDataPath());
		
		Logger.info("delete scenario s" +id);
	}
	
	public void processGtfs(final File gtfsFile, final String scenarioType, final String augmentScenarioId) {
		ExecutionContext graphBuilderContext = Akka.system().dispatchers().lookup("contexts.graph-builder-analyst-context");
		
		final Scenario scenario = this;
		
		Akka.system().scheduler().scheduleOnce(
			        Duration.create(10, TimeUnit.MILLISECONDS),
			        new Runnable() {
			            public void run() {
			            	
			            	scenario.processingGtfs = true;
			            	scenario.save();
		
			            	try {
			            		
								ZipFile zipFile = new ZipFile(gtfsFile);
						
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
							    	zipFile = new ZipFile(gtfsFile);
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
							        newFile = new File(getScenarioDataPath(), HashUtils.hashFile(gtfsFile) + ".zip");
							        
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
							        gtfsFile.delete();
							    }
								else  {
									newFile = new File(scenario.getScenarioDataPath(), scenario.id + ".zip");
									FileUtils.copyFile(gtfsFile, newFile);
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
				            					            	
				            	File osmXmlFile = new File(getScenarioDataPath(), scenario.id + ".osm.xml");
				            	File osmPbfFile = new File(getScenarioDataPath(), scenario.id + ".osm.pbf");
				            	
				            
				            	
				            	String bbox = "n=\"" + bounds.north + "\" w=\"" + bounds.west + "\" s=\"" + bounds.south + "\" e=\"" + bounds.east + "\"";
				            	
				            	String overpassQuery = "'data=<osm-script><bbox-query " + bbox + "/><print/><query type=\"way\"> <bbox-query " + bbox + "/> </query><print/> <query type=\"relation\"> <bbox-query " + bbox + "/></query><print/></osm-script>'";
				            	
				            	System.out.println("downloading xml for " + bbox);
				            	
						        ProcessBuilder pb = new ProcessBuilder("wget", "--post-data", overpassQuery, "http://overpass-api.de/api/interpreter/", "-O", osmXmlFile.getAbsolutePath());
						        	 
						        Process p = pb.start();
					            
					            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
						        String line;
						        
					            while((line = bufferedReader.readLine()) != null){
					                System.out.println("downloading...");
					            }
							       
						        System.out.println("osm xml downloaded");
						        
						        pb = new ProcessBuilder(new File(Application.binPath, "osmconvert").getAbsolutePath(), "--out-pbf", "-o=" + osmPbfFile.getAbsolutePath(), osmXmlFile.getAbsolutePath());
					        	 
						        p = pb.start();
					            
					            bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
						        
					            while((line = bufferedReader.readLine()) != null){
					                System.out.println("processing...");
					            }
							    
					            osmXmlFile.delete();
					            
						        System.out.println("osm xml converted to pbf");
				            	
									
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
			        },
			        graphBuilderContext
			);
	}
	
	public void build() {
		
		this.processingGtfs = false;
    	this.processingOsm = false;
    	this.failed = false;
    	this.save();
	
		ExecutionContext graphBuilderContext = Akka.system().dispatchers().lookup("contexts.graph-builder-analyst-context");

		final String graphId = id;
		
		Akka.system().scheduler().scheduleOnce(
			        Duration.create(10, TimeUnit.MILLISECONDS),
			        new Runnable() {
			            public void run() {
			            	
			            	Api.analyst.getGraph(graphId);
			            }
			        },
			        graphBuilderContext
			);
		
	}

	static public Scenario getScenario(String id) {
		
		return scenarioData.getById(id);	
	}
	
	static public void buildAll() throws IOException {
		
		for(Scenario s : getScenarios(null)) {
			s.build();
		}
	}
	
	static public Collection<Scenario> getScenarios(String projectId) throws IOException {
		
		if(projectId == null)
			return scenarioData.getAll();
		
		else {
			
			Collection<Scenario> data = new ArrayList<Scenario>();
			
			for(Scenario sd : scenarioData.getAll()) {
				if(sd.projectId == null )
					sd.delete();
				else if(sd.projectId.equals(projectId))
					data.add(sd);
			}
		
			return data;
		}
		
	}
	
	@JsonIgnore
	private File getTempShapeDirPath() {
		
		File shapeDirPath = new File(getScenarioDataPath(), "tmp_" + id);
		
		shapeDirPath.mkdirs();
		
		return shapeDirPath;
	}
	
}
