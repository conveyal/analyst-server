package controllers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import models.Bundle;
import models.Project;
import models.Shapefile;
import models.User;
import org.joda.time.LocalDate;
import otp.Analyst;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.zip.ZipException;

@Security.Authenticated(Secured.class)
public class Api extends Controller {

	public static int maxTimeLimit = 120; // in minutes

	public static Analyst analyst = new Analyst();

	static ObjectMapper mapper = new ObjectMapper();
	
	static {
		mapper.registerModule(new JodaModule());
        mapper.registerModule(new SinglePoint.RoutingRequestModule());
	}

    private static JsonFactory jf = new JsonFactory();


    static String toJson(Object pojo, boolean prettyPrint)
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
    
    /**
     * Get a day that has ostensibly normal service, one would guess. Uses the next Tuesday that
     * is covered by all transit feeds in the project.
     */
    public static Result getExemplarDay(String projectId) throws Exception {
    	Collection<Bundle> scenarios = Bundle.getBundles(projectId);
    	
    	LocalDate originalDate = new LocalDate().dayOfWeek().setCopy("Tuesday");
    	LocalDate date = originalDate;
    	
    	// date is now a nearby Tuesday
    	
    	// don't loop an excessive amount 
    	LocalDate dateBound = date.plusYears(2);
    	
    	// TODO: actually infer a date
    	return ok(originalDate.toString());
    	
    	/*// search forward first
    	DATES: while (date.isBefore(dateBound)) {
    		for (Scenario s : scenarios) {
    			Graph graph = analyst.getGraph(s.id);
    			
    			long dateInLocalTime = date.toDateTimeAtStartOfDay(DateTimeZone.forTimeZone(graph.getTimeZone()))
    					.toDate().getTime();
    			if (!graph.transitFeedCovers(dateInLocalTime)) {
    				date = date.plusWeeks(1);
    				continue DATES;
    			}
    		}
    		
    		// if we got here, this date is within the transit service range for all graphs in the project
    		return ok(date.toString());
    	}
    	
    	// if we got here, there is no future date where all the graphs have service
    	date = originalDate;
    	dateBound = date.minusYears(2);
    	
    	// search forward first
    	BDATES: while (date.isAfter(dateBound)) {
    		for (Scenario s : scenarios) {
    			Graph graph = analyst.getGraph(s.id);
    			
    			long dateInLocalTime = date.toDateTimeAtStartOfDay(DateTimeZone.forTimeZone(graph.getTimeZone()))
    					.toDate().getTime();
    			if (!graph.transitFeedCovers(dateInLocalTime)) {
    				date = date.minusWeeks(1);
    				continue BDATES;
    			}
    		}
    		
    		// if we got here, this date is within the transit service range for all graphs in the project
    		return ok(date.toString());
    	}
    	
    	// we don't have a date to return that is valid in all feeds
    	return ok(originalDate.toString());*/
    }


// **** user controllers ****

    public static Result getUser(String id) {

    	try {

            if(id != null) {

            	User u = null;

            	if(id.toLowerCase().equals("self")) {
            		u = User.getUserByUsername(session().get("username"));
            	}
            	else {
            		 u = User.getUser(id);
            	}

                if(u != null)
                    return ok(Api.toJson(u, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(User.getUsers(), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }

    public static Result createUser() {
        User u;

        try {

        	u = mapper.readValue(request().body().asJson().traverse(), User.class);
            u.save();

            return ok(Api.toJson(u, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }

    public static Result updateUser(String id) {

    	User u;

        try {

        	u = mapper.readValue(request().body().asJson().traverse(), User.class);

        	if(u.id == null || User.getUser(u.id) == null)
                return badRequest();

        	u.save();

            return ok(Api.toJson(u, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }


    public static Result deleteUser(String id) {
        if(id == null)
            return badRequest();

        User u = User.getUser(id);

        if(u == null)
        	return badRequest();

        u.delete();

        return ok();
    }


	// **** project controllers ****

    public static Result getAllProject() {

    	try {

    		return ok(Api.toJson(Project.getProjects(), false));

    	} catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }
    
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

            	User u = User.getUserByUsername(session().get("username"));

                return ok(Api.toJson(Project.getProjectsByUser(u), false));
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

            // add newly created project to user permission
            User u = User.getUserByUsername(session().get("username"));
            u.addProjectPermission(p.id);
            u.save();

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


    public static Result getShapefileById(String id) {
    	return getShapefile(id, null);
    }

    public static Result getShapefile(String id, String projectId) {

    	try {

            if(id != null) {
            	Shapefile s = Shapefile.getShapefile(id);
                if(s != null)
                    return ok(Api.toJson(s, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(Shapefile.getShapfiles(projectId), false));
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

        	String projectId = body.asFormUrlEncoded().get("projectId")[0];
        	String name = body.asFormUrlEncoded().get("name")[0];
        	Shapefile s = Shapefile.create(file.getFile(), projectId, name);

        	s.description = body.asFormUrlEncoded().get("description")[0];

        	s.save();

        	s.writeToClusterCache();
        	
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

        	if(s.id == null || Shapefile.getShapefile(s.id) == null)
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

  /*  public static Result getPointsetById(String id) {
    	return getPointset(id, null);
    }

    public static Result getPointset(String id, String projectId) {

    	try {

            if(id != null) {
            	Shapefile shp = Shapefile.getShapefile(id);
                if(shp != null)
                    return ok(Api.toJson(shp, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(Shapefile.getShapfiles(projectId), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }

    public static Result getPointsetsByProjectId(String projectId) {

    	try {

    		return ok(Api.toJson(Shapefile.getShapfiles(projectId), false));

        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }


    public static Result createPointset() {
    	Shapefile shp;
        try {

        	shp = mapper.readValue(request().body().asJson().traverse(), Shapefile.class);
        	shp.save();

        	Tiles.resetTileCache();
        	Shapefile.pointSetCache.clear();

            return ok(Api.toJson(shp, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }

    public static Result updatePointset(String id) {

    	Shapefile shp;

        try {

        	shp = mapper.readValue(request().body().asJson().traverse(), Shapefile.class);

        	if(shp.id == null || Shapefile.getShapefile(shp.id) == null)
                return badRequest();

        	shp.save();

        	Tiles.resetTileCache();
        	Shapefile.pointSetCache.clear();

            return ok(Api.toJson(shp, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }

    public static Result deletePointset(String id) {
        if(id == null)
            return badRequest();

        Shapefile shp = Shapefile.getShapefile(id);

        if(shp == null)
        	return badRequest();

        shp.delete();

        Tiles.resetTileCache();
        Shapefile.pointSetCache.clear();

        return ok();
    } */


    // **** scenario controllers ****


    public static Result getBundleById(String id) {
    	return getBundle(id, null);
    }

    public static Result getBundle(String id, String projectId) {

    	try {

            if(id != null) {
            	Bundle s = Bundle.getBundle(id);
                if(s != null)
                    return ok(Api.toJson(s, false));
                else
                    return notFound();
            }
            else {
                return ok(Api.toJson(Bundle.getBundles(projectId), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }

    public static Result createBundle() throws ZipException, IOException {

    	Http.MultipartFormData body = request().body().asMultipartFormData();

        Http.MultipartFormData.FilePart file = body.getFile("file");

        if (file != null && file.getFile() != null) {

        	String bundleType = body.asFormUrlEncoded().get("bundleType")[0];

        	String augmentBundleId = null;

        	if(body.asFormUrlEncoded().get("augmentBundleId") != null)
        		augmentBundleId = body.asFormUrlEncoded().get("augmentBundleId")[0];

        	Bundle s = Bundle.create(file.getFile(), bundleType, augmentBundleId);

        	s.name = body.asFormUrlEncoded().get("name")[0];
        	s.description = body.asFormUrlEncoded().get("description")[0];
        	s.projectId = body.asFormUrlEncoded().get("projectId")[0];

        	s.save();

            return ok(Api.toJson(s, false));
        }
        else {
            return forbidden();
        }
    }

    public static Result updateBundle(String id) {

    	Bundle s;

        try {

        	s = mapper.readValue(request().body().asJson().traverse(), Bundle.class);

        	if(s.id == null || Bundle.getBundle(s.id) == null)
                return badRequest();

        	s.save();

            return ok(Api.toJson(s, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }

    public static Result deleteBundle(String id) throws IOException {
        if(id == null)
            return badRequest();

        Bundle s = Bundle.getBundle(id);

        if(s == null)
        	return badRequest();

        s.delete();

        return ok();
    }
}
