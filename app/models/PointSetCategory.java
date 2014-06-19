package models;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import models.Shapefile.ShapeFeature;

import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.PointSet.AttributeData;
import org.opentripplanner.routing.services.GraphService;

import play.Logger;
import utils.DataStore;
import utils.HashUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import controllers.Api;

public class PointSetCategory implements Serializable {
	
	private static final long serialVersionUID = 1L;

	static DataStore<PointSetCategory> spatialDataSets = new DataStore<PointSetCategory>("pointset");

	public String id;
	public String projectId;
	public String name;
	public String description;
	
	public String shapeFileId;
	
	public List<Attribute> attributes = new ArrayList<Attribute>();
	
	public Integer featureCount;
	
	public static Map<String,PointSet> pointSetCache = new ConcurrentHashMap<String,PointSet>(); 
	
	
	@JsonIgnore
	public Shapefile getShapefile() {
		return Shapefile.getShapefile(shapeFileId);
	}

	public Integer getFeatureCount() {
		if(featureCount == null)
			featureCount = getShapefile().getShapeFeatureStore().size();
		
		return featureCount;
	}
	
	public PointSetCategory() {
	
	}
	
	public void addAttribute(Attribute attribute) {
	
    	attributes.add(attribute);
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
	
	@JsonIgnore
	public PointSet getPointSet() {
		
		synchronized (pointSetCache) {
			if(pointSetCache.containsKey(this.id))
				return pointSetCache.get(this.id);
				
			PointSet ps = new PointSet(featureCount);
			ps.setGraphService(Api.analyst.getGraphService());
			
			int index = 0;
			for(ShapeFeature sf :  this.getShapefile().getShapeFeatureStore().getAll()) {
				
				List<AttributeData> attributeData = new ArrayList<AttributeData>();
				
				for(Attribute a : this.attributes) {
					AttributeData ad = new AttributeData(Attribute.convertNameToId(this.name), Attribute.convertNameToId(a.name), sf.getAttribute(a.fieldName));
					attributeData.add(ad);
				}
				
				ps.addFeature(sf.id, sf.geom, attributeData, index);
				index++;
			}
			
			pointSetCache.put(this.id, ps);
			
			return ps;
		}
	}

	static public PointSetCategory getPointSetCategory(String id) {
		
		return spatialDataSets.getById(id);	
	}
	
	static public Collection<PointSetCategory> getPointSetCategories(String projectId) {
		
		if(projectId == null)
			return spatialDataSets.getAll();
		
		else {
			Collection<PointSetCategory> data = new ArrayList<PointSetCategory>();
			
			for(PointSetCategory sd : spatialDataSets.getAll()) {
				if(sd.projectId.equals(projectId))
					data.add(sd);
			}
			
			return data;
		}
		
	}


}
