package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.otp.Analyst;
import models.*;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

import static com.conveyal.analyst.server.controllers.Status.NOT_FOUND;
import static spark.Spark.halt;

public class Api {

	public static int maxTimeLimit = 120; // in minutes

	public static Analyst analyst = new Analyst();

    /**
     * Get a day that has ostensibly normal service, one would guess. Uses the next Tuesday that
     * is covered by all transit feeds in the project.
     */
    public static String getExemplarDay(Request req, Response res) throws Exception {
        Collection<Bundle> bundles = Bundle.getBundles(req.params("projectId"));
        if (bundles == null)
            halt(NOT_FOUND);

        LocalDate defaultDate = LocalDate.now().with(ChronoField.DAY_OF_WEEK, DayOfWeek.TUESDAY.getValue());

        if (defaultDate.isAfter(LocalDate.now()))
            defaultDate = defaultDate.minusDays(7);

        LocalDate startDate = null, endDate = null;

        for (Bundle b : bundles) {
            if (!b.failed && b.startDate != null && b.endDate != null && !b.startDate.isAfter(b.endDate)) {
                if (startDate == null || startDate.isBefore(b.startDate))
                    startDate = b.startDate;

                if (endDate == null || endDate.isAfter(b.endDate))
                    endDate = b.endDate;
            }
        }

        if (startDate == null || endDate == null || startDate.isAfter(endDate))
            return defaultDate.toString();

        // find a tuesday between the start and end date
        LocalDate ret = endDate;

        while (!ret.isBefore(startDate)) {
            if (ret.getDayOfWeek().equals(DayOfWeek.TUESDAY))
                return ret.toString();
            ret = ret.minusDays(1);
        }

        return defaultDate.toString();
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

    // **** query controllers ****

    public static Result getQueryById(String id) {
    	return getQuery(id, null, null);
    }
    
    public static Result getQuery(String id, String projectId, String pointSetId) {
        
    	try {

            if(id != null) {
            	Query q = Query.getQuery(id);
                if(q != null)
                    return ok(Api.toJson(q, false));
                else
                    return notFound();
            }
            else if (projectId != null){
                return ok(Api.toJson(Query.getQueries(projectId), false));
            }
            else {
            	return ok(Api.toJson(Query.getQueriesByPointSet(pointSetId), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }

    public static Result createQuery() throws IOException {
    	final Query q = mapper.readValue(request().body().asJson().traverse(), Query.class);

    	q.save();

        Akka.system().scheduler().scheduleOnce(
                Duration.create(10, TimeUnit.MILLISECONDS),
                new Runnable() {
                    @Override
                    public void run() {
                        q.run();
                    }
                },
                Akka.system().dispatcher()
        );

        return ok(Api.toJson(q, false));

    }

    public static Result updateQuery(String id) {

    	Query q;

        try {

        	q = mapper.readValue(request().body().asJson().traverse(), Query.class);

        	if(q.id == null || Query.getQuery(q.id) == null)
                return badRequest();

        	q.save();

            return ok(Api.toJson(q, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }

    public static Result deleteQuery(String id) throws IOException {
        if(id == null)
            return badRequest();

        Query q = Query.getQuery(id);

        if(q == null)
        	return badRequest();

        // cancel the job if it is running
        //QueueManager.getManager().cancelJob(q);

        q.delete();

        return ok();
    }
}
