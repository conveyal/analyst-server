package controllers;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import models.Attribute;
import models.Query;
import models.Shapefile;
import models.Shapefile.ShapeFeature;
import org.apache.commons.io.FileUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.cluster.ResultEnvelope;
import org.opentripplanner.analyst.cluster.ResultEnvelope.Which;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import utils.DirectoryZip;
import utils.HashUtils;
import utils.QueryResults;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Security.Authenticated(Secured.class)
public class Gis extends Controller {
	
	static File TMP_PATH = new File(Application.tmpPath);
	
	public static Result query(String queryId, Integer timeLimit, String weightByShapefile, String weightByAttribute,
			String groupBy, String which, String attributeName, String compareTo) {
    	
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		ResultEnvelope.Which whichEnum;
		try {
			whichEnum = ResultEnvelope.Which.valueOf(which);
		} catch (Exception e) {
			// no need to pollute the console with a stack trace
			return badRequest("Invalid value for which parameter");
		}
		
		Query query = Query.getQuery(queryId);
		
		Query query2 = compareTo != null ? Query.getQuery(compareTo) : null;
		
		if(query == null)
			return badRequest();
		
		String shapeName = (timeLimit / 60) + "_mins_" + whichEnum.toString().toLowerCase() + "_";
    	
    	try {
	    
    		String queryKey = queryId + "_" + timeLimit + "_" + which + "_" + attributeName;
    		
			QueryResults qr, qr2;

			synchronized(QueryResults.queryResultsCache) {
				if(!QueryResults.queryResultsCache.containsKey(queryKey)) {
					qr = new QueryResults(query, timeLimit, whichEnum, attributeName);
					QueryResults.queryResultsCache.put(queryKey, qr);
				}
				else
					qr = QueryResults.queryResultsCache.get(queryKey);
				
	    		if (compareTo != null) {
	    			String q2key = compareTo + "_" + timeLimit + "_" + which;
	    			
					if(!QueryResults.queryResultsCache.containsKey(q2key)) {
						qr2 = new QueryResults(query2, timeLimit, whichEnum, attributeName);
						QueryResults.queryResultsCache.put(q2key, qr2);
					}
					else {
						qr2 = QueryResults.queryResultsCache.get(q2key);
					}
					
		    		qr = qr.subtract(qr2);
	    		}
			}

			Shapefile shp = Shapefile.getShapefile(query.shapefileId);

    		       
            Collection<ShapeFeature> features = shp.getShapeFeatureStore().getAll();
         
            if(weightByShapefile == null) {
	            
            	ArrayList<String> fields = new ArrayList<String>();
            	
            	fields.add(shp.name.replaceAll("\\W+", ""));
            	
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
       
            	shapeName += "access_" + shp.name.replaceAll("\\W+", "").toLowerCase() +
            			(query2 != null ? "_compare_" + query2.name : "");
            	
            	return ok(generateZippedShapefile(shapeName, fields, gisFeatures, false));
            	
            }
            else {
            

        	
            	if(groupBy == null) {
            		
            		return badRequest("Must specify a weight by clause when specifying a normalize by clause!");
            
            	}
            	else {
            		
                	
            		Shapefile shpNorm = Shapefile.getShapefile(weightByShapefile);
                	Shapefile aggregateToSf = Shapefile.getShapefile(groupBy);
                	
            		QueryResults groupedQr = qr.aggregate(aggregateToSf, shpNorm, weightByAttribute);
            		            		
            		ArrayList<String> fields = new ArrayList<String>();

                	fields.add("groupval");
                	
                	ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();
                	
                	
                	for(ShapeFeature feature : aggregateToSf.getShapeFeatureStore().getAll()) {
    	            	
                		if(groupedQr.items.containsKey(feature.id)) {
	                		GisShapeFeature gf = new GisShapeFeature();
	                		gf.geom = feature.geom;
	                		gf.id = feature.id;
	                		
	                		gf.fields.add(groupedQr.items.get(feature.id).value);
	                		
	                		gisFeatures.add(gf);
    	            	}
                	}
                	

                	shapeName += "_" + shp.name.replaceAll("\\W+", "") + "_norm_" + shpNorm.name.replaceAll("\\W+", "") + "_group_" +
                			aggregateToSf.name.replaceAll("\\W+", "").toLowerCase() + (query2 != null ? "_compare_" + query2.name : "");
                	
                	return ok(generateZippedShapefile(shapeName, fields, gisFeatures, false));
            	}
            }     	
    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
    }
	
	
	/**
     * Get a ResultSet.
     */
    public static Result result(String key, String which) {     
    	Which whichEnum = Which.valueOf(which);
    	
        try {
        	ResultEnvelope env = SinglePoint.getResultSet(key);
        	ResultSet result = env.get(whichEnum);
        	
        	final Shapefile shp = Shapefile.getShapefile(env.destinationPointsetId);
			       
	        Collection<ShapeFeature> features = shp.getShapeFeatureStore().getAll();
	     
	        ArrayList<String> fields = new ArrayList<String>();
	        
	        for (Attribute a : shp.attributes.values()) {
	        	if (a.numeric) {
            		fields.add(a.name);
	        	}
	        }
        	        	
        	ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();

        	PointSet ps = shp.getPointSet();

        	for (ShapeFeature feature : features) {
            	
            	int sampleTime = result.times[ps.getIndexForFeature(feature.id)];
        		GisShapeFeature gf = new GisShapeFeature();
        		gf.geom = feature.geom;
        		gf.id = feature.id;
        		gf.time = sampleTime != Integer.MAX_VALUE ? sampleTime : null; 

        		// TODO: handle non-integer attributes
        		for (Attribute a : shp.attributes.values()) {
        			if (a.numeric) {
                		gf.fields.add(feature.getAttribute(a.name));
        			}
        		}
        		
        		gisFeatures.add(gf);
        	
            }
   
        	String shapeName = shp.name.toLowerCase().replaceAll("\\W", "") + "_time";
   
        	return ok(generateZippedShapefile(shapeName, fields, gisFeatures, false));
       
        	
    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
    }
    
	/**
     * Get a comparison.
     */
    public static Result resultComparison(String key1, String key2, String which) {

    	Which whichEnum = Which.valueOf(which);
        
        try {
        	ResultEnvelope env1 = SinglePoint.getResultSet(key1);
        	ResultSet result = env1.get(whichEnum);
        	ResultEnvelope env2 = SinglePoint.getResultSet(key2);
        	ResultSet result2 = env2.get(whichEnum);
        	
        	if (env1 == null || env2 == null)
        		notFound();
        	
        	if (!env1.destinationPointsetId.equals(env2))
        		badRequest();
        	
        	final Shapefile shp = Shapefile.getShapefile(env1.destinationPointsetId);
			       
	        Collection<ShapeFeature> features = shp.getShapeFeatureStore().getAll();
	     
	        ArrayList<String> fields = new ArrayList<String>();
	        
	        for (Attribute a : shp.attributes.values()) {
	        	if (a.numeric) {
            		fields.add(a.name);
	        	}
	        }
        	        	
        	ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();

        	PointSet ps = shp.getPointSet();

        	for (ShapeFeature feature : features) {
            	
        		int fidx = ps.getIndexForFeature(feature.id);
        		GisShapeFeature gf = new GisShapeFeature();
        		gf.geom = feature.geom;
        		gf.id = feature.id;
        		
        		int time1 = result.times[fidx];
        		gf.time = time1 != Integer.MAX_VALUE ? time1 : null;
        		int time2 = result2.times[fidx];
        		gf.time2 = time2 != Integer.MAX_VALUE ? time2 : null;
        		
        		if (gf.time != null && gf.time2 != null)
        			gf.difference = gf.time2 - gf.time;
        		else
        			gf.difference = null;

        		// TODO: handle non-integer attributes
        		for (Attribute a : shp.attributes.values()) {
        			if (a.numeric) {
                		gf.fields.add(feature.getAttribute(a.name));
        			}
        		}
        		
        		gisFeatures.add(gf);
        	
            }
   
        	String shapeName = shp.name.toLowerCase().replaceAll("\\W", "") + "_time_diff";
   
        	return ok(generateZippedShapefile(shapeName, fields, gisFeatures, true));
       
        	
    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
    }
	
	public static class GisShapeFeature {
		
		Geometry geom;
		String id;
		Integer time;
		Integer time2;
		Integer difference;
		ArrayList<Object> fields = new ArrayList<Object>();
		
	}
	
	static File generateZippedShapefile(String fileName, ArrayList<String> fieldNames, List<GisShapeFeature> features, boolean difference) {
			
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


			String featureDefinition = null;

			if(features.size() > 0 && features.get(0).geom instanceof Point)
				featureDefinition = "the_geom:Point:srid=4326,id:String,time:Integer";
			else
				featureDefinition = "the_geom:MultiPolygon:srid=4326,id:String,time:Integer";
			
			if (difference)
				featureDefinition += ",time2:Integer,difference:Integer";
			
			int fieldPosition = 0;
			for(String fieldName : fieldNames) {

				String shortFieldName = fieldName;
				
				if(fieldName.length() > 10)
					shortFieldName = fieldName.substring(0, 10);
				
				featureDefinition += "," + shortFieldName + ":";
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
				if(feature.geom instanceof Point)
					featureBuilder.add((Point)feature.geom);
				else
					featureBuilder.add((MultiPolygon)feature.geom);
                featureBuilder.add(feature.id);
                
                featureBuilder.add(feature.time);
                
                if (difference) {
                	featureBuilder.add(feature.time2);
                	featureBuilder.add(feature.difference);
                }
                	
               
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
