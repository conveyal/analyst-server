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
import models.Shapefile.ShapeFeature;
import models.PointSetCategory;
import models.Attribute;

import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.Indicator;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.analyst.core.SlippyTile;
import org.opentripplanner.analyst.request.TileRequest;

import otp.AnalystRequest;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

import play.mvc.*;
import utils.HaltonPoints;

public class Tiles extends Controller {

	private static  Map<String, byte[]> tileCache = new ConcurrentHashMap<String, byte[]>();
	
	public static void resetCache() {
		tileCache.clear();
	}
	
	public static Result spatial(String pointSetId, Integer x, Integer y, Integer z, String selectedAttributes) {
    	
		PointSetCategory sd = PointSetCategory.getPointSetCategory(pointSetId);
		
		HashSet<String> attributes = new HashSet<String>();
		
		if(selectedAttributes != null) {
			for(String attribute : selectedAttributes.split(",")) {
				attributes.add(attribute);
			}
		}
		
		if(sd == null)
			return badRequest();
		
    	String tileId = "spatialTile_" + pointSetId + "_" + x + "_" + "_" + y + "_" + "_" + z + "_" + selectedAttributes;
    	
    	if(tileCache.containsKey(tileId)) {
    		
    		ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tileId)); 
            response().setContentType("image/png");
    		return ok(bais);
    		
    	}
    	
    	double maxLat = SlippyTile.tile2lat(y, z);
        double minLat = SlippyTile.tile2lat(y + 1, z);
        double minLon = SlippyTile.tile2lon(x, z);
        double maxLon = SlippyTile.tile2lon(x + 1, z);
    	
        // annoyingly need both jts and opengis envelopes -- there's probably a smarter way to get them
        Envelope jtsEnvelope = new Envelope(maxLon, minLon, maxLat, minLat);
         
    	Envelope2D env = JTS.getEnvelope2D(jtsEnvelope, DefaultGeographicCRS.WGS84);
    	
    	TileRequest tileRequest = new TileRequest("", env, 256, 256);
    	GridEnvelope2D gridEnv = new GridEnvelope2D(0, 0, tileRequest.width, tileRequest.height);
    	GridGeometry2D gg = new GridGeometry2D(gridEnv, (org.opengis.geometry.Envelope)(tileRequest.bbox));
    	
      	MathTransform tr = gg.getCRSToGrid2D();
    	
    
    	try {
    	           
            BufferedImage before = new BufferedImage(256, 256, BufferedImage.TYPE_4BYTE_ABGR);
        	
            Graphics2D gr = before.createGraphics();
       
            List<ShapeFeature> features = sd.getShapefile().query(jtsEnvelope);
         	
            for(ShapeFeature feature : features) {
            	
            	if(sd.attributes.size() > 0 || attributes.isEmpty()) {
            		for(Attribute a : sd.attributes) {
                    	
            			if(!attributes.contains(a.fieldName))
            				continue;
            			
            			// draw halton points for indicator
            			 
            			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);
                    	
            			if(hp.getNumPoints() > 0) {
        	         
        	            	double[] coords = hp.transformPoints(tr);
        	            	
        	            	Color color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));
        	            	
        	            	int i = 0;
        	            	for(i = 0; i < hp.getNumPoints() * 2; i += 2){
        	            		
        	            		if(coords[i] > 0 && coords[i] < before.getWidth() &&  coords[i+1] > 0 && coords[i+1] < before.getHeight())
        	            			before.setRGB((int)coords[i], (int)coords[i+1], color.getRGB());
                    			
        	            		if(z > 14) {
                    				
                    				if(x+1 < before.getWidth() && y+1 < before.getHeight())
                    					before.setRGB((int)coords[i]+1, (int)coords[i+1]+1, color.getRGB());
                    				
                    				if(y+1 < before.getHeight())
                    					before.setRGB((int)coords[i], (int)coords[i+1]+1, color.getRGB());
                    				
                    				if(x+1 < before.getWidth())
                    					before.setRGB((int)coords[i]+1, (int)coords[i+1], color.getRGB());
        	            			
                    			}	
        	            	}	
            			}
            		}        	
            	}
            	else {
            		Geometry g  = JTS.transform(feature.geom, tr);
                   
            		gr.setColor(new Color(0.0f,0.0f,0.0f,0.1f));
            		
	            	Polygon p = new Polygon();
	            	for(Coordinate c : g.getCoordinates())
	            		p.addPoint((int)c.x, (int)c.y);
	            	
	            	gr.fillPolygon(p);         		
            	}
            }
            
            gr.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(before, "png", baos);
            
            tileCache.put(tileId, baos.toByteArray());
            
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray()); 
            response().setContentType("image/png");
      
           
            return ok(bais);
            
            
        } catch (Exception e) {
           
            e.printStackTrace();
        }
    	
    	return badRequest();
    } 
	
	public static Result surface(Integer surfaceId, String pointSetId, Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit) {
    	
		PointSetCategory sd = PointSetCategory.getPointSetCategory(pointSetId);
		
		if(sd == null)
			return badRequest();
		
		final TimeSurface surface = AnalystRequest.getSurface(surfaceId);
		
		if(surface == null) 
			return notFound();
		
    	String tileId = "surfaceTile_" + surfaceId + "_" + pointSetId + "_" + x + "_" + "_" + y + "_" + "_" + z + "_" + showIso + "_" + showPoints + "_" + timeLimit;
    	
    	if(tileCache.containsKey(tileId)) {
    		
    		ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tileId)); 
            response().setContentType("image/png");
    		return ok(bais);
    		
    	}
    	
    	Indicator indicator = AnalystRequest.getIndicator(surfaceId, pointSetId);
    	double maxLat = SlippyTile.tile2lat(y, z);
        double minLat = SlippyTile.tile2lat(y + 1, z);
        double minLon = SlippyTile.tile2lon(x, z);
        double maxLon = SlippyTile.tile2lon(x + 1, z);
    	
        // annoyingly need both jts and opengis envelopes -- there's probably a smarter way to get them
        Envelope jtsEnvelope = new Envelope(maxLon, minLon, maxLat, minLat);
         
    	Envelope2D env = JTS.getEnvelope2D(jtsEnvelope, DefaultGeographicCRS.WGS84);
    	
    	TileRequest tileRequest = new TileRequest("", env, 256, 256);
    	GridEnvelope2D gridEnv = new GridEnvelope2D(0, 0, tileRequest.width, tileRequest.height);
    	GridGeometry2D gg = new GridGeometry2D(gridEnv, (org.opengis.geometry.Envelope)(tileRequest.bbox));
    	
      	MathTransform tr = gg.getCRSToGrid2D();
    	
    	try {
    	           
            BufferedImage before = new BufferedImage(256, 256, BufferedImage.TYPE_4BYTE_ABGR);
        	
            Graphics2D gr = before.createGraphics();
       
            List<ShapeFeature> features = sd.getShapefile().query(jtsEnvelope);
         	
            for(ShapeFeature feature : features) {
            	
            	Integer sampleTime = indicator.getTime(feature.id);
            	if(sampleTime == null || sampleTime > timeLimit)
            		continue;
            
               	
    			// draw isotiles for block time
        		float opacity = 0.5f;
        		
        		
        		if(showIso) {
        			
        			Geometry g  = JTS.transform(feature.geom, tr);
        			
        			gr.setColor(new Color(0.0f,0.0f,1.0f,opacity));
        			
        			
        			Polygon p = new Polygon();
                	for(Coordinate c : g.getCoordinates())
                		p.addPoint((int)c.x, (int)c.y);
                	
                	gr.fillPolygon(p);
            			
        		}
            	
        		
            	// draw halton points for indicator
 
        		if(showPoints) {
        				
            		for(Attribute a : sd.attributes) {
    		
		    			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);
		            	
		    			if(hp.getNumPoints() > 0) {
		    				
			            	double[] coords = hp.transformPoints(tr);
			            	
			            	Color color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));
			            
			            	int i = 0;
			            	for(i = 0; i < hp.getNumPoints() * 2; i += 2){
			            		
			            		if(coords[i] > 0 && coords[i] < before.getWidth() &&  coords[i+1] > 0 && coords[i+1] < before.getHeight())
			            			before.setRGB((int)coords[i], (int)coords[i+1], color.getRGB());
		            			
			            		if(z > 14) {
		            				
		            				if(x+1 < before.getWidth() && y+1 < before.getHeight())
		            					before.setRGB((int)coords[i]+1, (int)coords[i+1]+1, color.getRGB());
		            				
		            				if(y+1 < before.getHeight())
		            					before.setRGB((int)coords[i], (int)coords[i+1]+1, color.getRGB());
		            				
		            				if(x+1 < before.getWidth())
		            					before.setRGB((int)coords[i]+1, (int)coords[i+1], color.getRGB());
			            			
		            			}     		
			            	}	
		    			}
            		}
    			}         	
            }
            
            gr.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(before, "png", baos);
            
            tileCache.put(tileId, baos.toByteArray());
            
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray()); 
            response().setContentType("image/png");
      
            return ok(bais);
            
            
        } catch (Exception e) {
           
            e.printStackTrace();
        }
    	
    	return badRequest();
    } 
	
	
	public static Result compareSpt(Integer surfaceId1, Integer surfaceId2, String spatialId, Integer x, Integer y, Integer z, Integer timeLimit) {
    	
		PointSetCategory sd = PointSetCategory.getPointSetCategory(spatialId);
		
		if(sd == null)
			return badRequest();
		
		final TimeSurface surf1 = AnalystRequest.getSurface(surfaceId1);
		final TimeSurface surf2 = AnalystRequest.getSurface(surfaceId2);
		
		if(surf1 == null || surf2 == null) 
			return notFound();
		
    	String tileId = "compareSpt_" + surfaceId1 + "_" + surfaceId2 + "_" + spatialId + "_" + x + "_" + "_" + y + "_" + "_" + z + "_" +  "_" + timeLimit;
    	
    	if(tileCache.containsKey(tileId)) {
    		
    		ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tileId)); 
            response().setContentType("image/png");
    		return ok(bais);
    		
    	}
    	
    	double maxLat = SlippyTile.tile2lat(y, z);
        double minLat = SlippyTile.tile2lat(y + 1, z);
        double minLon = SlippyTile.tile2lon(x, z);
        double maxLon = SlippyTile.tile2lon(x + 1, z);
    	
        // annoyingly need both jts and opengis envelopes -- there's probably a smarter way to get them
        Envelope jtsEnvelope = new Envelope(maxLon, minLon, maxLat, minLat);
         
    	Envelope2D env = JTS.getEnvelope2D(jtsEnvelope, DefaultGeographicCRS.WGS84);
    	
    	TileRequest tileRequest = new TileRequest("", env, 256, 256);
    	GridEnvelope2D gridEnv = new GridEnvelope2D(0, 0, tileRequest.width, tileRequest.height);
    	GridGeometry2D gg = new GridGeometry2D(gridEnv, (org.opengis.geometry.Envelope)(tileRequest.bbox));
    	
      	MathTransform tr = gg.getCRSToGrid2D();
    	
    	try {
    	           
            BufferedImage before = new BufferedImage(256, 256, BufferedImage.TYPE_4BYTE_ABGR);
        	
            Graphics2D gr = before.createGraphics();
       
            List<ShapeFeature> features = sd.getShapefile().query(jtsEnvelope);
         	
            for(ShapeFeature feature : features) {
            	
            	Long time1 = 1l;//sptResponse1.destinationTimes.get(feature.id);
            	Long time2 = 1l;//sptResponse2.destinationTimes.get(feature.id);
            	
             	if((time1 == null || time1 > timeLimit) && 
             			(time2 == null || time2 > timeLimit)) 
             		continue;
            	
            	
            	// draw polygon 
               	
    			// draw isotiles for block time
        		float opacity; 
    			Geometry g  = JTS.transform(feature.geom, tr);
    			
    			if(time2 != null) {
    				opacity = 0.5f - ((((float)time2 / (float)Api.maxTimeLimit)) / 2);
    			}
    			else {
    				opacity = 0.5f - ((((float)time1 / (float)Api.maxTimeLimit)) / 2);
    			}

    			if(time1 == null || time1 > timeLimit)
    				time1 = -1l;
    			if(time2 == null || time2 > timeLimit)
    				time2 = -1l;
    			
    			if(time1 < 0)
    				gr.setColor(new Color(0.0f,0.0f,0.8f,opacity));
    			else if(time1 > time2)
					gr.setColor(new Color(0.8f,0.0f,0.8f,opacity));
				else
					gr.setColor(new Color(0.8f,0.7f,0.2f,opacity));
    			
    			
    			Polygon p = new Polygon();
            	for(Coordinate c : g.getCoordinates())
            		p.addPoint((int)c.x, (int)c.y);
            	
            	gr.fillPolygon(p);
            			
            }
            gr.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(before, "png", baos);
            
            tileCache.put(tileId, baos.toByteArray());
            
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray()); 
            response().setContentType("image/png");
      
            return ok(bais);
            
            
        } catch (Exception e) {
           
            e.printStackTrace();
        }
    	
    	return badRequest();
    }    
	
}
