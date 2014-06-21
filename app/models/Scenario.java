package models;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
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
		
		//gtfsFile.renameTo(new File());
		
		File newFile = new File(scenario.getScenarioDataPath(), scenario.id + ".zip");
		FileUtils.copyFile(gtfsFile, newFile);
			
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
	
	static public Collection<Scenario> getScenarios(String projectId) {
		
		if(projectId == null)
			return scenarioData.getAll();
		
		else {
			
			Collection<Scenario> data = new ArrayList<Scenario>();
			
			for(Scenario sd : scenarioData.getAll()) {
				if(sd.projectId.equals(projectId))
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


}
