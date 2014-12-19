package controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;

import javax.imageio.ImageIO;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.analyst.core.IsochroneData;
import org.opentripplanner.analyst.core.SlippyTile;
import org.opentripplanner.analyst.request.TileRequest;
import org.opentripplanner.analyst.request.SampleGridRenderer.WTWD;
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.api.param.LatLon;
import org.opentripplanner.api.resource.LIsochrone;
import org.opentripplanner.common.geometry.DelaunayIsolineBuilder;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;

import otp.Analyst;
import otp.AnalystProfileRequest;
import otp.AnalystRequest;
import otp.ProfileResult;
import models.Attribute;
import models.Project;
import models.Query;
import models.Scenario;
import models.Shapefile;
import models.SpatialLayer;
import models.User;
import models.Shapefile.ShapeFeature;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

import play.libs.Akka;
import play.libs.Json;
import play.libs.F.Function;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.*;
import play.mvc.Http.MultipartFormData.FilePart;
import scala.concurrent.ExecutionContext;
import tiles.Tile;
import utils.HaltonPoints;
import utils.QueryResults;
import utils.ResultEnvelope;
import utils.QueryResults.QueryResultItem;

@Security.Authenticated(Secured.class)
public class Api extends Controller {

	public static int maxTimeLimit = 120; // in minutes

	public static Analyst analyst = new Analyst();

	private static ObjectMapper mapper = new ObjectMapper();
	
	static {
		mapper.registerModule(new JodaModule());
	}
	
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

    public static Promise<Result> surface(final String graphId, final Double lat, final Double lon, final String mode,
    		final Double bikeSpeed, final Double walkSpeed, String which, String date, final int fromTime, final int toTime) {
    	Promise<TimeSurfaceShort> promise;

		ResultEnvelope.Which whichEnum_tmp;
		
		LocalDate jodaDate_tmp;
		
		try {
			whichEnum_tmp = ResultEnvelope.Which.valueOf(which);
			jodaDate_tmp = LocalDate.parse(date);
		} catch (Exception e) {
			// no need to pollute the console with a stack trace
			return Promise.promise(new Function0<Result> () {
				@Override
				public Result apply() throws Throwable {
				    return badRequest("Invalid value for which or date parameter");
				}
			});
		}

		final LocalDate jodaDate = jodaDate_tmp;
		final ResultEnvelope.Which whichEnum = whichEnum_tmp;

		// temporarily disabling profile routing due to problems with large/dense graphs
     	if (false && new TraverseModeSet(mode).isTransit()) {
    		// transit search: use profile routing
    		promise = Promise.promise(
    				new Function0<TimeSurfaceShort>() {
    					public TimeSurfaceShort apply() {
    						LatLon latLon = new LatLon(String.format("%s,%s", lat, lon));

    						ProfileRequest request = analyst.buildProfileRequest(mode, jodaDate, fromTime, toTime, latLon);

    						if(request == null)
    							return null;

    						return AnalystProfileRequest.createSurfaces(request, graphId, 120, whichEnum);
    					}
    				}
    				);
    	}
    	else {
    		promise = Promise.promise(
    				new Function0<TimeSurfaceShort>() {
						public TimeSurfaceShort apply() throws Throwable {
							GenericLocation latLon = new GenericLocation(lat, lon);
							RoutingRequest req = analyst.buildRequest(graphId, jodaDate, fromTime, latLon, mode, 120);

							if (req == null)
								return null;

							req.setRoutingContext(analyst.getGraph(graphId));

							return AnalystRequest.createSurface(req, 120);
						}
    				});
    	}

		return promise.map(
				new Function<TimeSurfaceShort, Result>() {
					public Result apply(TimeSurfaceShort response) {

						if(response == null)
							return notFound();


						return ok(Json.toJson(response));

					}
				}
		);
    }
    
    /**
     * Get a day that has ostensibly normal service, one would guess. Uses the next Tuesday that
     * is covered by all transit feeds in the project.
     */
    public static Result getExemplarDay(String projectId) throws Exception {
    	Collection<Scenario> scenarios = Scenario.getScenarios(projectId);
    	
    	LocalDate originalDate = new LocalDate().dayOfWeek().setCopy("Tuesday");
    	LocalDate date = originalDate;
    	
    	// date is now a nearby Tuesday
    	
    	// don't loop an excessive amount 
    	LocalDate dateBound = date.plusYears(2);
    	
    	// search forward first
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
    	return ok(originalDate.toString());
    }

    public static Result isochrone(Integer surfaceId, List<Integer> cutoffs) throws IOException {

    	 final TimeSurface surf = AnalystRequest.getSurface(surfaceId);
         if (surf == null) return badRequest("Invalid TimeSurface ID.");
         if (cutoffs == null || cutoffs.isEmpty()) {
        	 cutoffs = new ArrayList<Integer>();
             cutoffs.add(surf.cutoffMinutes);
             cutoffs.add(surf.cutoffMinutes / 2);
         }

         List<IsochroneData> isochrones = getIsochronesAccumulative(surf, cutoffs);
         final SimpleFeatureCollection fc = LIsochrone.makeContourFeatures(isochrones);

         FeatureJSON fj = new FeatureJSON();
         ByteArrayOutputStream os = new ByteArrayOutputStream();
         fj.writeFeatureCollection(fc, os);
         String fcString = new String(os.toByteArray(),"UTF-8");

         response().setContentType("application/json");
         return ok(fcString);
    }

    /**
     * Get a ResultSet. ResultEnvelope.Which is embedded in the surface ID.
     * @param surfaceId
     * @param shapefileId
     * @return
     */
    public static Result result(Integer surfaceId, String shapefileId, String attributeName) {
    	final Shapefile shp = Shapefile.getShapefile(shapefileId);
    	ResultSet result;

    	// it could be a profile request, or not
    	// The IDs are unique; they come from inside OTP.
    	try {
    		result = AnalystProfileRequest.getResult(surfaceId, shapefileId, attributeName);
    	} catch (NullPointerException e) {
    		result = AnalystRequest.getResult(surfaceId, shapefileId, attributeName);
    	}

    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	result.writeJson(baos, shp.getPointSet(attributeName));
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        response().setContentType("application/json");
    	return ok(bais);
    }

    /**
     * Use Laurent's accumulative grid sampler. Cutoffs in minutes.
     * The grid and delaunay triangulation are cached, so subsequent requests are very fast.
     */
    public static List<IsochroneData> getIsochronesAccumulative(TimeSurface surf, List<Integer> cutoffs) {

        long t0 = System.currentTimeMillis();
        DelaunayIsolineBuilder<WTWD> isolineBuilder = new DelaunayIsolineBuilder<WTWD>(
                surf.sampleGrid.delaunayTriangulate(), new WTWD.IsolineMetric());

        double D0 = 400.0; // TODO ? Set properly
        List<IsochroneData> isochrones = new ArrayList<IsochroneData>();
        for (int cutoffSec : cutoffs) {

            WTWD z0 = new WTWD();
            z0.w = 1.0;
            z0.wTime = cutoffSec;
            z0.d = D0;
            IsochroneData isochrone = new IsochroneData(cutoffSec, isolineBuilder.computeIsoline(z0));
            isochrones.add(isochrone);
        }

        long t1 = System.currentTimeMillis();
        return isochrones;
    }

    public static Result queryBins(String queryId, Integer timeLimit, String weightByShapefile, String weightByAttribute, String groupBy,
    		String which, String compareTo) {
    	
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

		if(query == null)
			return badRequest();
		Query otherQuery = null;
		
		if (compareTo != null) {
			otherQuery = Query.getQuery(compareTo);
			
			if (otherQuery == null) {
				return badRequest("Non-existent comparison query.");
			}
		}
	   	
    	try {

    		String queryKey = queryId + "_" + timeLimit + "_" + which;

    		QueryResults qr = null;

    		synchronized(QueryResults.queryResultsCache) {
    			if(!QueryResults.queryResultsCache.containsKey(queryKey)) {
	    			qr = new QueryResults(query, timeLimit, whichEnum);
	    			QueryResults.queryResultsCache.put(queryKey, qr);
	    		}
	    		else
	    			qr = QueryResults.queryResultsCache.get(queryKey);
    		}
    		
    		if (otherQuery != null) {
        		QueryResults otherQr = null;
        		
    			queryKey = compareTo + "_" + timeLimit + "_" + which;
    			if (!QueryResults.queryResultsCache.containsKey(queryKey)) {
    				otherQr = new QueryResults(otherQuery, timeLimit, whichEnum);
    				QueryResults.queryResultsCache.put(queryKey, otherQr);
    			}
    			else {
    				otherQr = QueryResults.queryResultsCache.get(queryKey);
    			}
    			
    			qr = qr.subtract(otherQr);
    		}

            if(weightByShapefile == null) {
            	return ok(Json.toJson(qr.classifier.getBins()));
            }
            else {
            	Shapefile aggregateTo = Shapefile.getShapefile(groupBy);

				Shapefile weightBy = Shapefile.getShapefile(weightByShapefile);
				return ok(Json.toJson(qr.aggregate(aggregateTo, weightBy, weightByAttribute).classifier.getBins()));
	        
            }


    	} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }
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
            else if(id == "ALL") {
            	 return ok(Api.toJson(Project.getProjects(), false));
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

        	Shapefile s = Shapefile.create(file.getFile(), body.asFormUrlEncoded().get("projectId")[0]);

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


    public static Result getScenarioById(String id) {
    	return getScenario(id, null);
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

        	String augmentScenarioId = null;

        	if(body.asFormUrlEncoded().get("augmentScenarioId") != null)
        		augmentScenarioId = body.asFormUrlEncoded().get("augmentScenarioId")[0];

        	Scenario s = Scenario.create(file.getFile(), scenarioType, augmentScenarioId);

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

    public static Result updateScenario(String id) {

    	Scenario s;

        try {

        	s = mapper.readValue(request().body().asJson().traverse(), Scenario.class);

        	if(s.id == null || Scenario.getScenario(s.id) == null)
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

    	Query  q = Query.create();

    	q = mapper.readValue(request().body().asJson().traverse(), Query.class);

    	q.save();

    	q.run();

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

        q.delete();

        return ok();
    }


}
