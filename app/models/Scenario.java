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
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.mapdb.Bind;
import org.mapdb.Fun;

import play.Logger;
import utils.DataStore;
import utils.HashUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Geometry;

import controllers.Application;

public class Scenario implements Serializable {
	
	static DataStore<Scenario> scenarioData = new DataStore<Scenario>("scenario");

	public String id;
	public String projectId;
	public String name;
	public String description;
	
	public Scenario() {
		
	}
	
	static public Scenario create(File gtfsFile, String scenarioType) throws IOException {
		
		Scenario scenario = new Scenario();
		scenario.save();
		
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
	        newFile = new File(scenario.getScenarioDataPath(), HashUtils.hashFile(gtfsFile) + ".zip");
	        
	        ProcessBuilder pb = new ProcessBuilder(new File(Application.binPath, "java").getAbsolutePath()
	        		,"-jar","lib/geom2gtfs.jar",  shapeFile.getAbsolutePath(), configFile.getAbsolutePath(), newFile.getAbsolutePath());
	        Process p;
	        try {
	            p = pb.start();
	            
	            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		        String line;
		        
		        try {
		            while((line = bufferedReader.readLine()) != null){
		                System.out.println("reading...");
		                System.out.println(line);
		            }
		        } catch (IOException e) {
		            System.out.println("Failed to read line");
		        }
		        
	        } catch (IOException e) {
	            System.out.println("Failed to start geom2gtfs");
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
			
			
		for(File f : Scenario.getScenario("default").getScenarioDataPath().listFiles()) {
			if((scenarioType != null && scenarioType.equals("augment")) || f.getName().toLowerCase().endsWith(".pbf")) {
				FileUtils.copyFileToDirectory(f, scenario.getScenarioDataPath());
			}
		}
		
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

	static public Scenario getScenario(String id) {
		
		return scenarioData.getById(id);	
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
			
			if(data.isEmpty()) {
				Scenario defaultScenario = new Scenario();
				
				defaultScenario.id = "default";
				defaultScenario.name = "Default Scenario";
				defaultScenario.projectId = projectId;
				defaultScenario.save();
				
				data.add(defaultScenario);
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
