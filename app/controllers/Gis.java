package controllers;

import static utils.PromiseUtils.resolveNow;

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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import models.Query;
import models.Shapefile.ShapeFeature;
import models.Shapefile;
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
import org.joda.time.LocalDate;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.ResultSetDelta;
import org.opentripplanner.analyst.ResultSetWithTimes;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.analyst.core.SlippyTile;
import org.opentripplanner.analyst.request.TileRequest;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.index.strtree.STRtree;

import play.Logger;
import play.Play;
import play.libs.F;
import play.mvc.*;
import tiles.Tile;
import utils.DirectoryZip;
import utils.HaltonPoints;
import utils.HashUtils;
import utils.QueryResults;
import utils.ResultEnvelope;
import utils.QueryResults.QueryResultItem;
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
	    
    		String queryKey = queryId + "_" + timeLimit + "_" + which;
    		
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
    public static Result result(String shapefile, String graphId, Double lat, Double lon, String mode,
                                           Double bikeSpeed, Double walkSpeed, String which, String date, int fromTime, int toTime,
                                           boolean profile) {

        ResultEnvelope.Which whichEnum;
        LocalDate jodaDate;

        try {
            whichEnum = ResultEnvelope.Which.valueOf(which);
            jodaDate = LocalDate.parse(date);
        } catch (Exception e) {
            // no need to pollute the console with a stack trace
            return badRequest("Invalid value for which or date parameter");
        }

        // note that this does not include which, as it is a key to ResultEnvelopes, not ResultSets
        final String key = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s%s", graphId, lat, lon, mode,
                bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
                fromTime, toTime, shapefile, (profile ? "_profile" : ""));
        
        try {
        	ResultSetWithTimes result = (ResultSetWithTimes) SinglePoint.getResultSet(key).get(whichEnum);
        	
        	final Shapefile shp = Shapefile.getShapefile(shapefile);
			       
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
    public static Result resultComparison(String shapefile, String graphId, String graphId2, Double lat, Double lon, String mode,
                                           Double bikeSpeed, Double walkSpeed, String which, String date, int fromTime, int toTime,
                                           boolean profile) {

        ResultEnvelope.Which whichEnum;
        LocalDate jodaDate;

        try {
            whichEnum = ResultEnvelope.Which.valueOf(which);
            jodaDate = LocalDate.parse(date);
        } catch (Exception e) {
            // no need to pollute the console with a stack trace
            return badRequest("Invalid value for which or date parameter");
        }

        // note that this does not include which, as it is a key to ResultEnvelopes, not ResultSets
        String key = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s%s", graphId, lat, lon, mode,
                bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
                fromTime, toTime, shapefile, (profile ? "_profile" : ""));
        
        String key2 = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s%s", graphId2, lat, lon, mode,
                bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
                fromTime, toTime, shapefile, (profile ? "_profile" : ""));
        
        try {
        	ResultSetWithTimes result = (ResultSetWithTimes) SinglePoint.getResultSet(key).get(whichEnum);
        	ResultSetWithTimes result2 = (ResultSetWithTimes) SinglePoint.getResultSet(key2).get(whichEnum);
        	
        	final Shapefile shp = Shapefile.getShapefile(shapefile);
			       
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
