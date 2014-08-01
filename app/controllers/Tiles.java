package controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
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
import com.vividsolutions.jts.index.strtree.STRtree;

import play.mvc.*;
import utils.HaltonPoints;
import utils.QueryResults;
import utils.QueryResults.QueryResultItem;
import utils.Tile;
import utils.TransportIndex;
import utils.TransportIndex.TransitSegment;

public class Tiles extends Controller {

	private static  Map<String, byte[]> tileCache = new ConcurrentHashMap<String, byte[]>();
	
	private static  Map<String, QueryResults> queryResultsCache = new ConcurrentHashMap<String, QueryResults>();
	
	private static TransportIndex transitIndex = new TransportIndex();
	
	public static void resetTileCache() {
		tileCache.clear();
	}
	
	public static void resetQueryCache(String queryId) {
		queryResultsCache.remove(queryId);
		tileCache.clear();
	}
	
	
	public static Result spatial(String pointSetId, Integer x, Integer y, Integer z, String selectedAttributes) {
    	
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		SpatialLayer sd = SpatialLayer.getPointSetCategory(pointSetId);
		
		HashSet<String> attributes = new HashSet<String>();
		
		if(selectedAttributes != null) {
			for(String attribute : selectedAttributes.split(",")) {
				attributes.add(attribute);
			}
		}
		
		if(sd == null)
			return badRequest();
		
    	String tileIdPrefix = "spatialTile_" + pointSetId + "_" + selectedAttributes;
    	
    	Tile tile = new Tile(tileIdPrefix, x, y, z);
    	
    	try {
    		if(!tileCache.containsKey(tile.tileId)) {
    
	    	    List<ShapeFeature> features = sd.getShapefile().query(tile.envelope);
	         	
	            for(ShapeFeature feature : features) {
	            	
	            	if(sd.attributes.size() > 0 || attributes.isEmpty()) {
	            		for(Attribute a : sd.attributes) {
	                    	
	            			if(!attributes.contains(a.fieldName))
	            				continue;
	            			
	            			// draw halton points for indicator
	            			 
	            			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);
	                    	
	            			if(hp.getNumPoints() > 0) {
	        	         
	        	            	Color color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));
	        	            	
	        	            	tile.renderHaltonPoints(hp, color);
	            			}
	            		}        	
	            	}
	            	else {
	            		
	            		Color color = new Color(0.0f,0.0f,0.0f,0.1f);
	            		Color stroke = new Color(0.0f,0.0f,0.0f,0.5f);
	            		
	            		tile.renderPolygon(feature.geom, color, stroke); 		  		
	            	}
	            }
	            
	            response().setContentType("image/png");
	           
	            tileCache.put(tile.tileId, tile.generateImage());
	    	}
	    	
	    	ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId)); 
	        response().setContentType("image/png");
			return ok(bais);
			
    	} catch (Exception e) {
	           
            e.printStackTrace();
            return badRequest();
        }

    } 
	
	public static Result surface(Integer surfaceId, String pointSetId, Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {
    	
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		SpatialLayer sd = SpatialLayer.getPointSetCategory(pointSetId);
		
		if(sd == null)
			return badRequest();
		
		final TimeSurface surface = AnalystRequest.getSurface(surfaceId);
		
		if(surface == null) 
			return notFound();
		
		String tileIdPrefix = "surfaceTile_" + surfaceId + "_" + pointSetId + "_" +  "_" + showIso + "_" + showPoints +"_" + timeLimit;
		
		if(minTime != null)
			tileIdPrefix += "_" + minTime;
	
    	Tile tile = new Tile(tileIdPrefix, x, y, z);
    	try {
	    	if(!tileCache.containsKey(tile.tileId)) {
	    
	    		ResultFeatureWithTimes result = AnalystRequest.getResultWithTimes(surfaceId, pointSetId);
	    	           
	            List<ShapeFeature> features = sd.getShapefile().query(tile.envelope);
	         	
	            for(ShapeFeature feature : features) {
	            	
	            	Integer sampleTime = result.getTime(feature.id);
	            	if(sampleTime == null) 
	            		continue;
	            	
	            	if(sampleTime == Integer.MAX_VALUE)
                		continue;
	            	
	            	if(showIso) {
	            		
	            		Color color = null;
	            		
                     	if(sampleTime < timeLimit && (minTime != null && sampleTime > minTime)){
                     		float opacity = 1.0f - (float)((float)sampleTime / (float)timeLimit);
                     		color = new Color(0.9f,0.7f,0.2f,opacity);
                     	}
        				else {
        					color = new Color(0.0f,0.0f,0.0f,0.1f);
        				}
        		
                     	if(color != null)
                     		tile.renderPolygon(feature.geom, color, null);
                	}
	            
	            	// draw halton points for indicator
	 
	        		if(showPoints && sampleTime < timeLimit && (minTime != null && sampleTime > minTime)) {
	        				
	            		for(Attribute a : sd.attributes) {
	    		
			    			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);
			            	
	            			if(hp.getNumPoints() > 0) {
	        	         
	        	            	Color color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));
	        	            	
	        	            	tile.renderHaltonPoints(hp, color);
	            			}
	            		}
	    			}         	
	            }
	               
	            tileCache.put(tile.tileId, tile.generateImage());
			}
			
			ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId)); 
		    response().setContentType("image/png");
			return ok(bais);
		
    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
    }
		
	public static Result query(String queryId, Integer x, Integer y, Integer z, Integer timeLimit, String normalizeBy, String groupBy) {
    	
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		Query query = Query.getQuery(queryId);
		
		if(query == null)
			return badRequest();
		
		String tileIdPrefix = "query_" + queryId + "_" + timeLimit;
		
		if(normalizeBy != null)
			tileIdPrefix += "_" + normalizeBy;
		
		if(groupBy != null)
			tileIdPrefix += "_" + groupBy;

		
    	Tile tile = new Tile(tileIdPrefix, x, y, z);
    	
    	try {
	    	if(!tileCache.containsKey(tile.tileId)) {
	    		
	    		String queryKey = queryId + "_" + timeLimit;
	    		
	    		QueryResults qr = null;
	    		
	    		synchronized(queryResultsCache) {
	    			if(!queryResultsCache.containsKey(queryKey)) {
		    			qr = new QueryResults(query, timeLimit);
		    			queryResultsCache.put(queryKey, qr);
		    		}
		    		else
		    			qr = queryResultsCache.get(queryKey);
	    		}
	    		
	    		SpatialLayer sd = SpatialLayer.getPointSetCategory(query.pointSetId);
	    		       
	            List<ShapeFeature> features = sd.getShapefile().query(tile.envelope);
	         
	            if(normalizeBy == null) {
		            for(ShapeFeature feature : features) {
		            	
		            	Color color = null;
		            
		            	if(qr.items.containsKey(feature.id)) {
	                 		color = qr.jenksClassifier.getColorValue(qr.items.get(feature.id).value);
	                 	}
		            	
	    				if(color == null) {
	    					color = new Color(0.0f,0.0f,0.0f,0.1f);
	    				}
	    		
	                 	if(color != null)
	                 		tile.renderPolygon(feature.geom, color, null);
		            	
		            }
	            }
	            else {
	            
	            	QueryResults normalizeQr = qr.normalizeBy(normalizeBy);
            	
	            	if(groupBy == null) {
	            		for(ShapeFeature feature : features) {
			            	
			            	Color color = null;
			            
			            	if(normalizeQr.items.containsKey(feature.id)) {
		                 		color = normalizeQr.jenksClassifier.getColorValue(normalizeQr.items.get(feature.id).value);
		                 	}
		    				
			            	if(color == null){
		    					color = new Color(0.0f,0.0f,0.0f,0.1f);
		    				}
		    		
		                 	if(color != null)
		                 		tile.renderPolygon(feature.geom, color, null);
			            	
			            }
	            	}
	            	else {
	            		QueryResults gruopedQr = normalizeQr.groupBy(groupBy);
	            		
	            		for(QueryResultItem item : gruopedQr.items.values()) {
			            	
			            	Color color = null;
			            
			            	color = gruopedQr.jenksClassifier.getColorValue(item.value);
	                 	
			            	if(color == null){
		    					color = new Color(0.0f,0.0f,0.0f,0.1f);
		    				}
			            		
			            	tile.renderPolygon(item.feature.geom, color, null);
			            	
			            }
	            	}
		            
	            }
		               
	            tileCache.put(tile.tileId, tile.generateImage());
			}
			
			ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId)); 
		    response().setContentType("image/png");
			return ok(bais);
		
    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
    }
	
	
	public static Result transit(String scenarioId, Integer x, Integer y, Integer z) {
		
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		String tileIdPrefix = "transit_" + scenarioId;

    	Tile tile = new Tile(tileIdPrefix, x, y, z);
		
    	try {
    		
    		HashSet<String> defaultEdges = new HashSet<String>();
    		
	    	if(!tileCache.containsKey(tile.tileId)) {
	    		
	    		if(!scenarioId.equals("default")) {
	    			STRtree index = transitIndex.getIndexForGraph("default");
	    			List<TransitSegment> segments = index.query(tile.envelope);
	    			
	    			for(TransitSegment ts : segments) {
	    				defaultEdges.add(ts.edgeId);
	    			}
	    		}
	    		STRtree index = transitIndex.getIndexForGraph(scenarioId);
	    		List<TransitSegment> segments = index.query(tile.envelope);
	    		
	    		for(TransitSegment ts : segments) {
	    			Color color;
	    			
	    			if(!scenarioId.equals("default")) {
	    				if(!defaultEdges.contains(ts.edgeId)) {
	    					color = new Color(0.6f,0.8f,0.6f,0.75f);
		    				
			    			tile.renderLineString(ts.geom, color, 5);
	    				}
	    			}
	    			else {
	    				color = new Color(0.6f,0.6f,1.0f,0.25f);
	    				
		    			tile.renderLineString(ts.geom, color, null);
	    			}	
	    		}
	    		tileCache.put(tile.tileId, tile.generateImage());	
	    	}
			
			ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId)); 
			
		    response().setContentType("image/png");
			return ok(bais);
			
		} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
		
	}
	
	public static Result traffic(String scenarioId, Integer x, Integer y, Integer z) {
		
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		String tileIdPrefix = "transit_" + scenarioId;

    	Tile tile = new Tile(tileIdPrefix, x, y, z);
		
    	try {
	    	if(!tileCache.containsKey(tile.tileId)) {
	    		//Api.analyst.getGraph(scenarioId).getGeomIndex().
	    		
	    		//for(TransitSegment ts : segments) {
	    		//	Color color;
	    			
	    		//	color = new Color(0.6f,0.6f,1.0f,0.25f);
	    				
	    		//	tile.renderLineString(ts.geom, color);
	    		//}
	    		//tileCache.put(tile.tileId, tile.generateImage());	
	    	}
			
			ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId)); 
			
		    response().setContentType("image/png");
			return ok(bais);
			
		} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
		
	}
	
	
	public static Result compare(Integer surfaceId1, Integer surfaceId2, String spatialId, Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {
    	
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		
		SpatialLayer sd = SpatialLayer.getPointSetCategory(spatialId);
		
		if(sd == null)
			return badRequest();
		
		String tileIdPrefix = "compare_" + surfaceId1 + "_" + surfaceId2 + "_" + spatialId + "_" + showIso + "_" + showPoints + "_" + timeLimit + "_" + minTime;

    	Tile tile = new Tile(tileIdPrefix, x, y, z);
    	
    	try {
    		
    		if(!tileCache.containsKey(tile.tileId)) {

    			final TimeSurface surf1 = AnalystRequest.getSurface(surfaceId1);
    			final TimeSurface surf2 = AnalystRequest.getSurface(surfaceId2);
    			
    			if(surf1 == null || surf2 == null) 
    				return notFound();
    			
    			ResultFeatureDelta resultDelta = new ResultFeatureDelta(sd.getPointSet().getSampleSet(surf1.routerId), sd.getPointSet().getSampleSet(surf2.routerId),  surf1, surf2);
    			
    			List<ShapeFeature> features = sd.getShapefile().query(tile.envelope);
             	
                for(ShapeFeature feature : features) {
                	
                	if(resultDelta.timeIdMap.get(feature.id) == 0 &&  resultDelta.times2IdMap.get(feature.id) == 0)
                		continue;
                	
                	int time1 = resultDelta.timeIdMap.get(feature.id);
                	int time2 = resultDelta.times2IdMap.get(feature.id);
                	
                	if(time1 == Integer.MAX_VALUE && time2 == Integer.MAX_VALUE)
                		continue;
                	
                	Color color = null;
                	
                	if(showIso) {
                		
                     	if((time2 == time1 || time2 > time1) && time1 > minTime && time1 < timeLimit){
                     		float opacity = 1.0f - (float)((float)time1 / (float)timeLimit);
                     		color = new Color(0.9f,0.7f,0.2f,opacity);
                     	}
                     		
        				else if((time1 == Integer.MAX_VALUE || time1 > timeLimit) && time2 < timeLimit && time2 > minTime) {
        					float opacity = 1.0f - (float)((float)time2 / (float)timeLimit);
        					color = new Color(0.8f,0.0f,0.8f,opacity);
        				}
        				else if(time1 > time2 && time2 < timeLimit && time2 > minTime) {
        					float opacity = 1.0f - (float)((float)time2 / (float)time1);
        					color = new Color(0.0f,0.0f,0.8f,opacity);
        				}
        				else {
        					color = new Color(0.0f,0.0f,0.0f,0.1f);
        				}
        		
                     	if(color != null)
                     		tile.renderPolygon(feature.geom, color, null);
                	}
                		
                 	if(showPoints && (time1 < timeLimit || time2 < timeLimit)) {
        				
	            		for(Attribute a : sd.attributes) {
	    		
			    			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);
			            	
	            			if(hp.getNumPoints() > 0) {
	        	         
	        	            	color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));
	        	            	
	        	            	tile.renderHaltonPoints(hp, color);
	            			}
	            		}
	    			}       
                }
                
                tileCache.put(tile.tileId, tile.generateImage());	
    		}
                 
            ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId)); 
			
		    response().setContentType("image/png");
			return ok(bais);
            
    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
    }    
	
}
