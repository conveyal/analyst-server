package controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import lombok.Synchronized;
import models.Query;
import models.Shapefile.ShapeFeature;
import models.SpatialLayer;
import models.Attribute;

import org.apache.commons.io.FileUtils;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollections;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.ResultFeature;
import org.opentripplanner.analyst.ResultFeatureDelta;
import org.opentripplanner.analyst.ResultFeatureWithTimes;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.analyst.core.SlippyTile;
import org.opentripplanner.analyst.request.TileRequest;

import otp.AnalystRequest;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.index.strtree.STRtree;

import play.Logger;
import play.Play;
import play.mvc.*;
import tiles.Tile;
import utils.DirectoryZip;
import utils.HaltonPoints;
import utils.HashUtils;
import utils.QueryResults;
import utils.QueryResults.QueryResultItem;
import utils.TransportIndex;
import utils.TransportIndex.TransitSegment;

public class Gis extends Controller {
	
	static File TMP_PATH = new File("tmp/");
	
	public static Result query(String queryId, Integer timeLimit, String normalizeBy, String groupBy) {
    	
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		Query query = Query.getQuery(queryId);
		
		if(query == null)
			return badRequest();
		
		String shapeName = (timeLimit / 60) + "_mins_";
    	
    	try {
	    
    		String queryKey = queryId + "_" + timeLimit;
    		
			QueryResults qr = null;

			synchronized(QueryResults.queryResultsCache) {
				if(!QueryResults.queryResultsCache.containsKey(queryKey)) {
					qr = new QueryResults(query, timeLimit);
					QueryResults.queryResultsCache.put(queryKey, qr);
				}
				else
					qr = QueryResults.queryResultsCache.get(queryKey);
			}
    		
    		
    		SpatialLayer sd = SpatialLayer.getPointSetCategory(query.pointSetId);
    		       
            Collection<ShapeFeature> features = sd.getShapefile().getShapeFeatureStore().getAll();
         
            if(normalizeBy == null) {
	            
            	ArrayList<String> fields = new ArrayList<String>();
            	
            	fields.add(sd.name.replaceAll("\\W+", ""));
            	
            	ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();
            	
            	for(ShapeFeature feature : features) {
	            	
            		if(qr.items.containsKey(feature.id)) {
	            		GisShapeFeature gf = new GisShapeFeature();
	            		gf.geom = feature.geom;
	            		gf.id = feature.id;
	            		
	            		gf.fields.add(qr.items.get(feature.id).value);
	            		
	            		gisFeatures.add(gf);
            		}
	            }
            	
            	shapeName += "access_" + sd.name.replaceAll("\\W+", "").toLowerCase();
            	
            	return ok(generateZippedShapefile(shapeName, fields, gisFeatures));
            	
            }
            else {
            
            	QueryResults normalizeQr = qr.normalizeBy(normalizeBy);
            	
            	SpatialLayer sdNorm = SpatialLayer.getPointSetCategory(normalizeBy);
        	
            	if(groupBy == null) {
            		
            		ArrayList<String> fields = new ArrayList<String>();
                	
            		fields.add("normval");
                	fields.add(sd.name.replaceAll("\\W+", ""));
                	fields.add(sdNorm.name.replaceAll("\\W+", ""));
                	
                	ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();
                	
                	for(ShapeFeature feature : features) {
    	            	
                		if(normalizeQr.items.containsKey(feature.id)) {
	                		GisShapeFeature gf = new GisShapeFeature();
	                		gf.geom = feature.geom;
	                		gf.id = feature.id;
	                		
	                		gf.fields.add(normalizeQr.items.get(feature.id).value);
	                		gf.fields.add(normalizeQr.items.get(feature.id).original);
	                		gf.fields.add(normalizeQr.items.get(feature.id).nomalizedTotal);
	                		
	                		gisFeatures.add(gf);
    	            	}
                	}
                	shapeName += "_" + sd.name.replaceAll("\\W+", "") + "_norm_" + sdNorm.name.replaceAll("\\W+", "").toLowerCase();
                	
                	return ok(generateZippedShapefile(shapeName, fields, gisFeatures));
            
            	}
            	else {
            		
            		QueryResults gruopedQr = normalizeQr.groupBy(groupBy);
            		
            		SpatialLayer sdGroup = SpatialLayer.getPointSetCategory(groupBy);
            		
            		ArrayList<String> fields = new ArrayList<String>();

                	fields.add("groupval");
                	
                	ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();
                	
                	
                	for(ShapeFeature feature : sdGroup.getShapefile().getShapeFeatureStore().getAll()) {
    	            	
                		if(gruopedQr.items.containsKey(feature.id)) {
	                		GisShapeFeature gf = new GisShapeFeature();
	                		gf.geom = feature.geom;
	                		gf.id = feature.id;
	                		
	                		gf.fields.add(gruopedQr.items.get(feature.id).value);
	                		
	                		gisFeatures.add(gf);
    	            	}
                	}
                	
                	shapeName += "_" + sd.name.replaceAll("\\W+", "") + "_norm_" + sdNorm.name.replaceAll("\\W+", "") + "_group_" + sdGroup.name.replaceAll("\\W+", "").toLowerCase();
                	
                	return ok(generateZippedShapefile(shapeName, fields, gisFeatures));
            	}
            }     	
    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
    }
	
	public static class GisShapeFeature {
		
		Geometry geom;
		String id;
		ArrayList<Object> fields = new ArrayList<Object>();
		
	}
	
	static File generateZippedShapefile(String fileName, ArrayList<String> fieldNames, List<GisShapeFeature> features) {
			
		String shapeFileId = HashUtils.hashString("shapefile_" + (new Date()).toString()).substring(0, 6) + "_" + fileName;
		
		if(!TMP_PATH.exists())
			TMP_PATH.mkdirs();
		
		File outputZipFile = new File(TMP_PATH, shapeFileId + ".zip");
		
		File outputDirectory = new File(TMP_PATH, shapeFileId);
		
		Logger.info("outfile path:" + outputDirectory.getAbsolutePath());
		
		File outputShapefile = new File(outputDirectory, shapeFileId + ".shp");
       
        try
        {        	
        	if(!outputDirectory.exists())
        	{
        		outputDirectory.mkdirs();
        	}
        	
			ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
			
			Map<String, Serializable> params = new HashMap<String, Serializable>();
			params.put("url", outputShapefile.toURI().toURL());
			params.put("create spatial index", Boolean.TRUE);
			
			ShapefileDataStore dataStore = (ShapefileDataStore)dataStoreFactory.createNewDataStore(params);
			dataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);

			String featureDefinition = "the_geom:MultiPolygon:srid=4326,id:String";
			
			int fieldPosition = 0;
			for(String fieldName : fieldNames) {
				featureDefinition += "," + fieldName + ":";
				if(features.get(0).fields.get(fieldPosition) instanceof String)
					featureDefinition += "String";
				if(features.get(0).fields.get(fieldPosition) instanceof Number)
					featureDefinition += "Double";
					fieldPosition++;
			}
			
        	SimpleFeatureType featureType = DataUtilities.createType("Analyst", featureDefinition);

            SimpleFeatureBuilder featureBuilder = null;
            
            List<SimpleFeature> featureList = new ArrayList<SimpleFeature>();
            
        	dataStore.createSchema(featureType);
        	featureBuilder = new SimpleFeatureBuilder(featureType);
        	
        	for(GisShapeFeature feature : features)
        	{
        		featureBuilder.add((MultiPolygon)feature.geom);
                featureBuilder.add(feature.id);
               
                for(Object o : feature.fields)
                	featureBuilder.add(o);
                
                SimpleFeature f = featureBuilder.buildFeature(null);
                featureList.add(f);
                
        	}
        	
        	ListFeatureCollection featureCollection = new ListFeatureCollection(featureType, featureList);
       
            Transaction transaction = new DefaultTransaction("create");

            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);

            if (featureSource instanceof SimpleFeatureStore) 
            {
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

                featureStore.setTransaction(transaction);
               
                featureStore.addFeatures(featureCollection);
                transaction.commit();

                transaction.close();
            } 
            else 
            {
            	throw new Exception(typeName + " does not support read/write access");
            }
            
            DirectoryZip.zip(outputDirectory, outputZipFile);
            FileUtils.deleteDirectory(outputDirectory);    
        }
        catch(Exception e)
        {	
        	Logger.error("Unable to process GIS export: ", e.toString());
        	e.printStackTrace();
        } 
        
        return outputZipFile;
	}
	
	  
	
}
