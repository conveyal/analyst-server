package controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;

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
import otp.AnalystRequest;
import otp.SptResponse;
import models.Project;
import models.Scenario;
import models.Shapefile;
import models.SpatialDataSet;
import models.Shapefile.ShapeFeature;

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
import play.mvc.Http.MultipartFormData.FilePart;
import utils.HaltonPoints;

public class Api extends Controller {

	public static Long maxTimeLimit = 3600l;
	
	public static Analyst analyst = new Analyst();
	
	private static ObjectMapper mapper = new ObjectMapper();
    private static JsonFactory jf = new JsonFactory();

    
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
    
    private static class AccesibilitySummary {
    	public Long total = 0l;
    	public Long accessible = 0l;
    }
    
    public static Promise<Result> spt(final String graphId, final String spatialId, final Double lat, final Double lon, final String mode) {
    	
    	Promise<SptResponse> promise = Promise.promise(
		    new Function0<SptResponse>() {
		      public SptResponse apply() {
		    	  GenericLocation latLon = new GenericLocation(lat, lon);
	          	
	              	AnalystRequest request = analyst.buildRequest(graphId, latLon, mode);
	              	
	              	if(request == null)
	              		return null;
	              		
	              	SptResponse sptResponse = analyst.getSptResponse(request, spatialId);
	              	
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
    
    public static Result sptSummary(String sptId, String spatialId, Integer timeLimit) {
    	
		SpatialDataSet sd = SpatialDataSet.getSpatialDataSet(spatialId);
		
		if(sd == null)
			return badRequest();
		
		SptResponse sptResponse = SptResponse.getResponse(sptId, spatialId);
		
		if(sptResponse == null) 
			return notFound();
		
    	
    	try {
    	           
            BufferedImage before = new BufferedImage(256, 256, BufferedImage.TYPE_4BYTE_ABGR);
        	
            Graphics2D gr = before.createGraphics();
       
            Collection<ShapeFeature> features = sd.getShapefile().queryAll();
         	
            AccesibilitySummary summary = new AccesibilitySummary();
            
            for(ShapeFeature feature : features) {
            	
            	Integer value = feature.getAttribute(sd.shapefieldname);
            	
            	summary.total += value;
            	
            	if(!sptResponse.destinationTimes.containsKey(feature.id) || sptResponse.destinationTimes.get(feature.id) > timeLimit)
            		continue;
            	
            	summary.accessible += value;
            	
	            	            	
            }
           
            return ok(Json.toJson(summary));
            
            
        } catch (Exception e) {
           
            e.printStackTrace();
        }
    	
    	return badRequest();
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
    
    
 // **** shapefile controllers ****
    
    
    public static Result getShapefile(String id) {
        
    	try {
    		
            if(id != null) {
            	Shapefile s = Shapefile.getShapefile(id);
                if(s != null)
                    return ok(Api.toJson(s, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(Shapefile.getShapfiles(), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }
    
    public static Result createShapefile() throws ZipException, IOException {
    	
    	Http.MultipartFormData body = request().body().asMultipartFormData();
        
        Http.MultipartFormData.FilePart file = body.getFile("file");
		          
        if (file != null && file.getFile() != null) {

        	Shapefile s = Shapefile.create(file.getFile());
        	
        	s.name = body.asFormUrlEncoded().get("name")[0];
        	s.description = body.asFormUrlEncoded().get("description")[0];
        	
        	s.save();
        	
            return ok(Api.toJson(s, false));  
        } 
        else {
            return forbidden(); 
        }
    }
    
    public static Result updateShapefile(String id) {
        
    	Shapefile s;

        try {
        	
        	s = mapper.readValue(request().body().asJson().traverse(), Shapefile.class);
        	
        	if(s.id == null || Project.getProject(s.id) == null)
                return badRequest();
        	
        	s.save();

            return ok(Api.toJson(s, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }
    
    public static Result deleteShapefile(String id) {
        if(id == null)
            return badRequest();

        Shapefile s = Shapefile.getShapefile(id);

        if(s == null)
        	return badRequest();

        s.delete();

        return ok();
    }
    
    
   // *** pointset controllers ***
    
    public static Result getPointsetById(String id) {
    	return getPointset(id, null);
    }
    
    public static Result getPointset(String id, String projectId) {
        
    	try {
    		
            if(id != null) {
            	SpatialDataSet s = SpatialDataSet.getSpatialDataSet(id);
                if(s != null)
                    return ok(Api.toJson(s, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(SpatialDataSet.getSpatialDataSets(projectId), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }
    
    public static Result getPointsetsByProjectId(String projectId) {
        
    	try {
    		
    		return ok(Api.toJson(SpatialDataSet.getSpatialDataSets(projectId), false));
    		
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }
    
    
    
    public static Result createPointset() {
    	SpatialDataSet sd;
        try {
        
        	sd = mapper.readValue(request().body().asJson().traverse(), SpatialDataSet.class);
            sd.save();

            return ok(Api.toJson(sd, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    	
    }
    
    public static Result updatePointset(String id) {
        
    	SpatialDataSet sd;

        try {
        	
        	sd = mapper.readValue(request().body().asJson().traverse(), SpatialDataSet.class);
        	
        	if(sd.id == null || Project.getProject(sd.id) == null)
                return badRequest();
        	
        	sd.save();

            return ok(Api.toJson(sd, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }
    
    public static Result deletePointset(String id) {
        if(id == null)
            return badRequest();

        SpatialDataSet sd = SpatialDataSet.getSpatialDataSet(id);

        if(sd == null)
        	return badRequest();

        sd.delete();

        return ok();
    }
    
    
    
// **** shapefile controllers ****
    
    
    public static Result getScenarioById(String id) {
    	return getPointset(id, null);
    }
    
    public static Result getScenario(String id, String projectId) {
        
    	try {
    		
            if(id != null) {
            	Scenario s = Scenario.getScenario(id);
                if(s != null)
                    return ok(Api.toJson(s, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(Scenario.getScenarios(projectId), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }
    
    public static Result createScenario() throws ZipException, IOException {
    	
    	Http.MultipartFormData body = request().body().asMultipartFormData();
        
        Http.MultipartFormData.FilePart file = body.getFile("file");
		          
        if (file != null && file.getFile() != null) {

        	String scenarioType = body.asFormUrlEncoded().get("scenarioType")[0];
        	
        	Scenario s = Scenario.create(file.getFile(), scenarioType);
        	
        	s.name = body.asFormUrlEncoded().get("name")[0];
        	s.description = body.asFormUrlEncoded().get("description")[0];
        	s.projectid = body.asFormUrlEncoded().get("projectid")[0];
        	
        	s.save();
        	
            return ok(Api.toJson(s, false));  
        } 
        else {
            return forbidden(); 
        }
    }
    
    public static Result updateScenario(String id) {
        
    	Scenario s;

        try {
        	
        	s = mapper.readValue(request().body().asJson().traverse(), Scenario.class);
        	
        	if(s.id == null || Project.getProject(s.id) == null)
                return badRequest();
        	
        	s.save();

            return ok(Api.toJson(s, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }
    
    public static Result deleteScenario(String id) throws IOException {
        if(id == null)
            return badRequest();

        Scenario s = Scenario.getScenario(id);

        if(s == null)
        	return badRequest();

        s.delete();

        return ok();
    }
    
  
    
}
