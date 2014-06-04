package controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.core.SlippyTile;
import org.opentripplanner.analyst.request.TileRequest;
import org.opentripplanner.common.model.GenericLocation;

import otp.Analyst;
import model.AnalystRequest;
import model.AttributeGroup;
import model.DefaultDestinations;
import model.HaltonPoints;
import model.IndicatorItem;
import model.IndicatorQueryItem;
import model.IndicatorResponse;
import model.Metadata;
import model.SptResponse;
import models.Project;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

import play.libs.Json;
import play.libs.F.Function;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.*;

public class Api extends Controller {

	public static Long maxTimeLimit = 3600l;
	
	public static Analyst analyst = new Analyst();
	
	private static ObjectMapper mapper = new ObjectMapper();
    private static JsonFactory jf = new JsonFactory();

    public static DefaultDestinations defaultDestinations = new DefaultDestinations();
	
	private static  Map<String, byte[]> tileCache = new HashMap<String, byte[]>();
    
    private static String toJson(Object pojo, boolean prettyPrint)
        throws JsonMappingException, JsonGenerationException, IOException {
        
    	StringWriter sw = new StringWriter();
        JsonGenerator jg = jf.createJsonGenerator(sw);
        if (prettyPrint) {
            jg.useDefaultPrettyPrinter();
        }
        mapper.writeValue(jg, pojo);
        return sw.toString();
    }
	
	// **** project controllers ****
    
    public static Result getProject(String id) {
        
    	try {
    		
            if(id != null) {
            	Project p = Project.getProject(id);
                if(p != null)
                    return ok(Api.toJson(p, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(Project.getProjects(), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }
    
    public static Result createProject() {
        Project p;

        try {
        
        	p = mapper.readValue(request().body().asJson().traverse(), Project.class);
            p.save();

            return ok(Api.toJson(p, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }
    
    public static Result updateProject(String id) {
        
    	Project p;

        try {
        	
        	p = mapper.readValue(request().body().asJson().traverse(), Project.class);
        	
        	if(p.id == null || Project.getProject(p.id) == null)
                return badRequest();
        	
        	p.save();

            return ok(Api.toJson(p, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }


    public static Result deleteProject(String id) {
        if(id == null)
            return badRequest();

        Project p = Project.getProject(id);

        if(p == null)
        	return badRequest();

        p.delete();

        return ok();
    }
    
    
    
public static Promise<Result> spt(final Double lat, final Double lon, final String mode) {
    	
    	Promise<SptResponse> promise = Promise.promise(
		    new Function0<SptResponse>() {
		      public SptResponse apply() {
		    	  GenericLocation latLon = new GenericLocation(lat, lon);
	          	
	              	AnalystRequest request = analyst.buildRequest(latLon, mode);
	              	
	              	if(request == null)
	              		return null;
	              		
	              	SptResponse sptResponse = analyst.getSpt(request, defaultDestinations.destinations);
	              	
	              	return sptResponse;
		      }
		    }
		  );
    	return promise.map(
		    new Function<SptResponse, Result>() {
		      public Result apply(SptResponse response) {
		    	
		    	if(response == null)
		    	  return notFound();
		    	
		        return ok(Json.toJson(response));
		      }
		    }
		  );
    	
    }
      
    public static Result indicator(String sptId, String indicatorId, Integer timeLimit) {
    	
    	IndicatorResponse indicatorResponse = new IndicatorResponse( sptId,  timeLimit,  indicatorId);

    	if(indicatorResponse == null)
    		return badRequest();	
    	else
    		return ok(Json.toJson(indicatorResponse)); 
    }
    
    public static Result tile(String sptId, String indicatorId, Integer timeLimit, Integer x, Integer y, Integer z, String hiddenAttributes) {
    	
    	String tileId = sptId + "_" + indicatorId + "_" + timeLimit + "_" + x + "_" + "_" + y + "_" + "_" + z + "_" + "_" + hiddenAttributes;
    	
    	if(tileCache.containsKey(tileId)) {
    		
    		ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tileId)); 
            response().setContentType("image/png");
    		return ok(bais);
    		
    	}
    	
    	HashSet<String> hiddenAtrs = new HashSet<String>();
    	
    	if(hiddenAttributes != null&& !hiddenAttributes.isEmpty()) {
    		for(String atr :hiddenAttributes.split(",")) {
    			hiddenAtrs.add(atr);
    		}
    	}
    	
    	
    	long startTime = System.currentTimeMillis();
    	
    	long haltonPoints =0;
    	
    	double maxLat = SlippyTile.tile2lat(y, z);
        double minLat = SlippyTile.tile2lat(y + 1, z);
        double minLon = SlippyTile.tile2lon(x, z);
        double maxLon = SlippyTile.tile2lon(x + 1, z);
    	
        // annoyingly need both jts and opengis envelopes -- there's probably a smarter way to get them
        Envelope jtsEnvelope = new Envelope(maxLon, minLon, maxLat, minLat);
         
    	Envelope2D env = JTS.getEnvelope2D(jtsEnvelope, DefaultGeographicCRS.WGS84);
    	
    	TileRequest tileRequest = new TileRequest(env, 256, 256);
    	GridEnvelope2D gridEnv = new GridEnvelope2D(0, 0, tileRequest.width, tileRequest.height);
    	GridGeometry2D gg = new GridGeometry2D(gridEnv, (org.opengis.geometry.Envelope)(tileRequest.bbox));
    	
    	List<IndicatorItem> items = analyst.getIndicatorsForEnv(indicatorId, jtsEnvelope);
    	
    	 ConcurrentHashMap<String, IndicatorQueryItem> sptBlocks = analyst.queryBlocks(sptId, indicatorId, timeLimit);
    	
    	MathTransform tr = gg.getCRSToGrid2D();
    	
    	long transformSetupTime = System.currentTimeMillis();
    
    	try {
    	           
            BufferedImage before = new BufferedImage(256, 256, BufferedImage.TYPE_4BYTE_ABGR);
        	
            Graphics2D gr = before.createGraphics();
        	
        	long preBuildTime = System.currentTimeMillis();
        	
        	long totalTransformTime = 0;
        	int totalItems = 0;
        	
        	ConcurrentHashMap<String, Collection<AttributeGroup>> indicators = Api.analyst.getIndicatorMetadata();
        	
            for(IndicatorItem item : items) {
            	
            	if(sptBlocks != null &&  item.block != null && sptBlocks.containsKey(item.geoId)) {
            		
            		totalItems++;
            		
            		long preTransformTime = System.currentTimeMillis();
	            	Geometry g  = JTS.transform(item.block, tr);
	            	long postTransformTime = System.currentTimeMillis();
	            	
	            	totalTransformTime += postTransformTime - preTransformTime;
	            	
	            	double area = g.getArea();
	            	
            		if(!sptId.equals("all")) {
            			
            			// draw isotiles for block time
                		//float opacity = 1 - sptBlocks.get(item.geoId).time / (float)maxTimeLimit;
                		// using consistent shading for all blocks in area
                		gr.setColor(new Color(0.0f,0.0f,0.0f,0.1f));
                		
                	
    	            	Polygon p = new Polygon();
    	            	for(Coordinate c : g.getCoordinates())
    	            		p.addPoint((int)c.x, (int)c.y);
    	            	
    	            	gr.fillPolygon(p);
            			
            		}
            		
	            	// draw halton points for indicator
	            	
	            	long totalPoints = 0l;
					if(indicators.keySet().contains(indicatorId)) {
						            		
	            		for(AttributeGroup group : indicators.get(indicatorId)) {
	            			HaltonPoints hp = Api.analyst.getHaltonPoints(item.geoId, indicatorId, group.id);
	            			totalPoints += hp.getNumPoints();
	            		}
					}
	            	
					long skipModulus = (long) (totalPoints / (area * 5));
	            	
	            	if(indicators.keySet().contains(indicatorId)) {
	            		
	            		for(AttributeGroup group : indicators.get(indicatorId)) {
	            			
	            			//skip hidden attributes
	            			if(hiddenAtrs.contains(group.id))
	            				continue;
	            			
	            			HaltonPoints hp = Api.analyst.getHaltonPoints(item.geoId, indicatorId, group.id);
			            	
	            			if(hp.getNumPoints() > 0) {
	            				haltonPoints += hp.getNumPoints();
				         
				            	double[] coords = hp.transformPoints(tr);
				            	
				            	Color color = new Color(Integer.parseInt(group.color.replace("#", ""), 16));
				            	
				            	int i = 0;
				            	for(i = 0; i < hp.getNumPoints() * 2; i += 2){
				            		
				            		if(skipModulus > 0 && i % skipModulus != 0)
				            			continue;
				            		
				            		
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
            	
            }
            
            gr.dispose();
            
            long buildTime = System.currentTimeMillis();
            
            // skipping retina for now for performance reasons (2-3x savings)
            // revisit with GPU acceleration...
            
           /* int w = before.getWidth();

            int h = before.getHeight();
            BufferedImage after = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            AffineTransform at = new AffineTransform();
            at.scale(2.0, 2.0);
            AffineTransformOp scaleOp = 
               new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
            after = scaleOp.filter(before, after); */

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(before, "png", baos);
            
            tileCache.put(tileId, baos.toByteArray());
            
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray()); 
            response().setContentType("image/png");
            
            
            long finalTime = System.currentTimeMillis();
            
            long totalTime = finalTime - startTime;
            long totalPreBuildTime = preBuildTime - startTime;
            long totalPostBuildTime = finalTime - buildTime;
            long totalBuildTime = buildTime - preBuildTime;
            
            long totalTimeMinusBuild = totalTime - totalBuildTime;
            
           
            System.out.println("tile: " + z + "/" + x + "/" + y);
            System.out.println("haltonPoints: " + haltonPoints);
            
            System.out.println("totalTime: " + totalTime + " totalPreBuildTime: " + totalPreBuildTime + " totalPostBuildTime: " + totalPostBuildTime + " totalBuildTime: " + totalBuildTime + " totalTransformTime: " + totalTransformTime + " totalTimeMinusBuild: " + totalTimeMinusBuild);
            System.out.println("totalItems: " + totalItems + " time/item: " + ((float)totalBuildTime / totalItems));
            System.out.println("");
       
            return ok(bais);
            
            
        } catch (Exception e) {
           
            e.printStackTrace();
        }

    	return ok(items.size() + "");
    }
    
    public static Result metadata() {
        
    	Envelope env = analyst.getMetadata();
    	
    	return ok(Json.toJson(new Metadata(env, maxTimeLimit)));
    }
    
    public static Result indicatorMetadata() {
    	
    	return ok(Json.toJson(analyst.getIndicatorMetadata()));
    }

    
}
