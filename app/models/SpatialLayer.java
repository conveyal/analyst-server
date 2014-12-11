package models;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import models.Shapefile.ShapeFeature;

import org.opentripplanner.analyst.EmptyPolygonException;
import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.UnsupportedGeometryException;

import play.Logger;
import utils.DataStore;
import utils.HashUtils;

import com.conveyal.otpac.PointSetDatastore;
import com.fasterxml.jackson.annotation.JsonIgnore;

import controllers.Api;


public class SpatialLayer implements Serializable {
	
/*	private static final long serialVersionUID = 1L;

	static DataStore<SpatialLayer> spatialDataSets = new DataStore<SpatialLayer>("layer");

	public String id;
	public String projectId;
	public String name;
	public String description;
	
	public String shapeFileId;
	
	
	
	@JsonIgnore
	public Shapefile getShapefile() {
		return Shapefile.getShapefile(shapeFileId);
	}

	public SpatialLayer() {
	
	}
	
	public void addAttribute(Attribute attribute) {
	
    	attributes.add(attribute);
	}
	
	public List<String> getAttributeIds() {
		List<String> attributeIds = new ArrayList<String>();
	
		for(Attribute attr : attributes) {
			attributeIds.add(attr.fieldName);
		}
		
		return attributeIds;
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
	
	
	static public SpatialLayer getPointSetCategory(String id) {
		
		return spatialDataSets.getById(id);	
	}
	
	static public Collection<SpatialLayer> getPointSetCategories(String projectId) {
		
		if(projectId == null)
			return spatialDataSets.getAll();
		
		else {
			Collection<SpatialLayer> data = new ArrayList<SpatialLayer>();
			
			for(SpatialLayer sd : spatialDataSets.getAll()) {
				if(sd.projectId.equals(projectId))
					data.add(sd);
			}
			
			return data;
		}
	} */
	
	
}
