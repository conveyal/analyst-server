package models;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

import org.mapdb.Bind;
import org.mapdb.Fun;

import play.Logger;
import utils.DataStore;
import utils.HashUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Geometry;

public class SpatialDataSet implements Serializable {
	
	static DataStore<SpatialDataSet> spatialDataSets = new DataStore<SpatialDataSet>("spatial");

	public String id;
	public String projectid;
	public String name;
	public String description;
	public String color;
	
	public String shapefileid;
	public String shapefieldname;
	
	public Integer featureCount;
	
	@JsonIgnore
	public Shapefile getShapefile() {
		return Shapefile.getShapefile(shapefileid);
	}

	public Integer getFeatureCount() {
		if(featureCount == null)
			featureCount = getShapefile().getShapeFeatureStore().size();
		
		return featureCount;
	}
	
	public SpatialDataSet() {

	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("sd_" + d.toString());
			
			Logger.info("created spatial data set sd " + id);
		}
		
		spatialDataSets.save(id, this);
		
		Logger.info("saved spatial data set sd " +id);
	}
	
	public void delete() {
		spatialDataSets.delete(id);
		
		Logger.info("delete spatial data set sd" +id);
	}

	static public SpatialDataSet getSpatialDataSet(String id) {
		
		return spatialDataSets.getById(id);	
	}
	
	static public Collection<SpatialDataSet> getSpatialDataSets(String projectId) {
		
		if(projectId == null)
			return spatialDataSets.getAll();
		
		else {
			Collection<SpatialDataSet> data = new ArrayList<SpatialDataSet>();
			
			for(SpatialDataSet sd : spatialDataSets.getAll()) {
				if(sd.projectid.equals(projectId))
					data.add(sd);
			}
			
			return data;
		}
		
	}


}
