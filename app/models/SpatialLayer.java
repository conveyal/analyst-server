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
	
	private static final long serialVersionUID = 1L;

	static DataStore<SpatialLayer> spatialDataSets = new DataStore<SpatialLayer>("layer");

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
		
		if(getShapefile() == null)
			return 0;
		
		if(featureCount == null)
			featureCount = getShapefile().getShapeFeatureStore().size();
		
		return featureCount;
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
	
	@JsonIgnore
	public PointSet getPointSet() {
		
		synchronized (pointSetCache) {
			if(pointSetCache.containsKey(this.id))
				return pointSetCache.get(this.id);
				
			PointSet ps = new PointSet(featureCount);
			ps.setGraphService(Api.analyst.getGraphService());
			
			String categoryId = Attribute.convertNameToId(this.name);
			
			ps.id = categoryId;
			ps.label = this.name;
			ps.description = this.description;
			
			int index = 0;
			for(ShapeFeature sf :  this.getShapefile().getShapeFeatureStore().getAll()) {
				
				HashMap<String,Integer> propertyData = new HashMap<String,Integer>();
				
				for(Attribute a : this.attributes) {
					String propertyId = categoryId + "." + Attribute.convertNameToId(a.name);	
					propertyData.put(propertyId, sf.getAttribute(a.fieldName));
				}
				
				PointFeature pf;
				try {
					pf = new PointFeature(sf.id.toString(), sf.geom, propertyData);
					ps.addFeature(pf, index);
				} catch (EmptyPolygonException | UnsupportedGeometryException e) {
					e.printStackTrace();
				}
				
				
				index++;
			}
			
			ps.setLabel(categoryId, this.name);
			
			for(Attribute attr : this.attributes) {
				String propertyId = categoryId + "." + Attribute.convertNameToId(attr.name);
				ps.setLabel(propertyId, attr.name);
				ps.setStyle(propertyId, "color", attr.color);
			}
			
			pointSetCache.put(this.id, ps);
			
			return ps;
		}
	}
	
	public String writeToClusterCache(Boolean workOffline) throws IOException {
		
		
		
		PointSet ps = this.getPointSet();	
		String cachePointSetId = id + ".json";
		
		File f = new File(cachePointSetId);
		
		FileOutputStream fos = new FileOutputStream(f);
		ps.writeJson(fos, true);
		fos.close();
		
		PointSetDatastore datastore = new PointSetDatastore(10, "s3Credentials", workOffline);
	
		datastore.addPointSet(f, cachePointSetId);
		
		f.delete();
		
		return cachePointSetId;
			
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
	}
	
	
}
