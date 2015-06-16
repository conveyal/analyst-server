package controllers;


import com.csvreader.CsvWriter;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import models.Bundle;
import models.Project;
import models.Shapefile;
import models.User;
import org.opentripplanner.analyst.Histogram;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.cluster.AnalystClusterRequest;
import play.Play;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import utils.IdUtils;
import utils.JsonUtil;
import utils.QueueManager;
import utils.ResultEnvelope;
import utils.ResultEnvelope.Which;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map.Entry;

/**
 * Controllers for getting result sets used in single point mode.
 */
public class SinglePoint extends Controller {
    // cache the result envelopes, but don't run out of memory
	// if we use a direct cache from MapDB, the system comes apart at the seams because MapDB has a lot of trouble serializing
	// the isochrones
	private static Cache<String, ResultEnvelope> envelopeCache = CacheBuilder.newBuilder().maximumSize(500).build();

    /** re-use object mapper */
    private static final ObjectMapper objectMapper = JsonUtil.getObjectMapper();
    
    /** Create a result from a JSON-ified OneToMany[Profile]Request. */
    public static F.Promise<Result> result () throws JsonProcessingException {
		String username = session().get("username");
    	if (username == null &&
    			Play.application().configuration().getBoolean("api.allow-unauthenticated-access") != true)
    		return F.Promise.pure((Result) unauthorized());
    	
    	// allow cross-origin access if we don't need auth
    	if (Play.application().configuration().getBoolean("api.allow-unauthenticated-access") == true)
    		response().setHeader("Access-Control-Allow-Origin", "*");
    		
    	
    	// deserialize a result
    	// figure out if it's profile or not
    	
    	JsonNode params = request().body().asJson();
		AnalystClusterRequest req = objectMapper.treeToValue(params, AnalystClusterRequest.class);

		req.includeTimes = true;
		req.jobId = IdUtils.getId();

		Bundle bundle = Bundle.getBundle(req.graphId);
		Shapefile ps = Shapefile.getShapefile(req.destinationPointsetId);

		// provide some security; if we don't have this bundle and shapefile here, don't let them make a request
		// against it (they may have the UUID from somewhere else and be trying to use our server as a back door
		// to the cluster)
		if (bundle == null || ps == null)
			return F.Promise.pure((Result) notFound());

		if (!bundle.projectId.equals(ps.projectId))
			return F.Promise.pure((Result) badRequest());

		// permissions check
		Project p = Project.getProject(bundle.projectId);
		User u = username != null ? User.getUser(username) : null;
		if (u != null && !u.hasPermission(p))
			return F.Promise.pure((Result) unauthorized());

		F.RedeemablePromise < Result > result = F.RedeemablePromise.empty();

		// for safety, add the callback before enqueing the job.
		QueueManager.getManager().addCallback(req.jobId, re -> {
			result.success(ok(resultSetToJson(re)).as("application/json"));
			// remove callback
			return false;
		});

		QueueManager.getManager().enqueue(p.id, bundle.id, req.id, req);

		return result;
    }
    
    /** Options request for unauthenticated CORS if enabled */
    public static Result options () {
    	if (session().get("username") == null &&
    			Play.application().configuration().getBoolean("api.allow-unauthenticated-access") != true)
    		return unauthorized();
    	
    	// allow cross-origin access if we don't need auth
    	if (Play.application().configuration().getBoolean("api.allow-unauthenticated-access") == true) {
    		response().setHeader("Access-Control-Allow-Origin", "*");
    		response().setHeader("Access-Control-Request-Method", "POST");
    		response().setHeader("Access-Control-Allow-Headers", "Content-Type");
    	}
    	
    	return ok();
    }
    
    /** Create a CSV result 
     * @throws IOException */
    public static Result csv(String key, String which) throws IOException {
    	if (session().get("username") == null &&
    			Play.application().configuration().getBoolean("api.allow-unauthenticated-access") != true)
    		return unauthorized();
    	
    	if (Play.application().configuration().getBoolean("api.allow-unauthenticated-access") == true)
    		response().setHeader("Access-Control-Allow-Origin", "*");
    	
    	Which whichEnum = Which.valueOf(which);
    	
    	// get the resultset
    	ResultEnvelope env = envelopeCache.getIfPresent(key);
    	
    	if (env == null)
    		return notFound();
    	
    	ResultSet rs = env.get(whichEnum);
    	
    	// create a CSV
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	CsvWriter writer = new CsvWriter(baos, ',', Charset.forName("UTF-8"));
    	
    	// write the header column
    	// size 121: 1 column for attribute names, 120 columns for minutes
    	String[] columns = new String[121];
    	columns[0] = "minute";
    	
    	for (int i = 1; i < 121; i++) {
    		columns[i] = "" + i;
    	}
    	
    	writer.writeRecord(columns);
    	
    	for (Entry<String, Histogram> e : rs.histograms.entrySet()) {
    		// write sums only here
    		writer.write(e.getKey());
    		
    		int[] sums = e.getValue().sums;
    		
    		for (int i = 0; i < Math.min(120, sums.length); i++) {
    			writer.write("" + sums[i]);
    		}
    		
    		writer.endRecord();
    	}
    	
    	// write the sums, which are the same for the entire resultset
    	for (Histogram h : rs.histograms.values()) {
    		writer.write("feature count");
    		
    		for (int i = 0; i < Math.min(120, h.counts.length); i++) {
    			writer.write("" + h.counts[i]);
    		}
    		
    		writer.endRecord();
    		break;
    	}
    	
    	writer.close();
    	
    	// make a file name
    	String filename = key.substring(0, 5) + "_" + whichEnum.toString().toLowerCase() + ".csv";
    	
    	response().setHeader("Content-Disposition", "attachment; filename=" + filename);
    	
    	return ok(baos.toString())
    			.as("text/csv");
    }
    
    private static String resultSetToJson (ResultEnvelope rs) {
    	try {
    		ResultSet worst = rs.get(Which.WORST_CASE);
    		ResultSet point = rs.get(Which.POINT_ESTIMATE);
    		ResultSet avg   = rs.get(Which.AVERAGE);
    		ResultSet best  = rs.get(Which.BEST_CASE);
    		ResultSet spread= rs.get(Which.SPREAD); 
    	
	    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    	// TODO: are the features in the same order here (!)
	    	// I think they are if the caches are cleaned regularly, because we cache the pointset on startup
	    	// However, I'm not positive that every time we start the server we get a pointset in the same order
	    	// I think we do, because MapDB iteration order is defined in a TreeMap, and getAll returns map.values . . .
	    	// However, nothing is enforcing this . . .
	    	JsonFactory jf = new JsonFactory();
	    	JsonGenerator jgen = jf.createGenerator(baos);
	    	jgen.setCodec(objectMapper);
	    	
	    	jgen.writeStartObject();
	    	{
	    		if (rs.destinationPointsetId != null) {
					Shapefile shp = Shapefile.getShapefile(rs.destinationPointsetId);
					shp.getPointSet().writeJsonProperties(jgen);
				}
		    	
		    	jgen.writeStringField("key", rs.id);
		    	
		    	ResultSet exemplar = point != null ? point : worst;
		    	
		    	jgen.writeObjectFieldStart("data");
		    	{
			    	for (String propertyId : exemplar.histograms.keySet()) {
			    		jgen.writeObjectFieldStart(propertyId);
			    		{
				    		if (worst != null) {
				    			jgen.writeObjectFieldStart("worstCase");
				    			worst.histograms.get(propertyId).writeJson(jgen);
				    			jgen.writeEndObject();
				    		}
				    		
				    		// both pointEstimate and average are point estimates, pick whichever one is not null
				    		if (point != null || avg != null) {
				    			jgen.writeObjectFieldStart("pointEstimate");
				    			(point != null ? point : avg).histograms.get(propertyId).writeJson(jgen);
				    			jgen.writeEndObject();
				    		}
				    		
				    		if (best != null) {
				    			jgen.writeObjectFieldStart("bestCase");
				    			best.histograms.get(propertyId).writeJson(jgen);
				    			jgen.writeEndObject();
				    		}
				    		
				    		if (spread != null) {
				    			jgen.writeObjectFieldStart("spread");
				    			spread.histograms.get(propertyId).writeJson(jgen);
				    			jgen.writeEndObject();
				    		}
			    		}
			    		jgen.writeEndObject();
			    	}
		    	
		    	}
		    	jgen.writeEndObject();
		    	
		    	if (exemplar.isochrones != null) {
		    		jgen.writeObjectFieldStart("isochrones");
		    		if (worst != null) {
		    			jgen.writeObjectFieldStart("worstCase");
		    			worst.writeIsochrones(jgen);
		    			jgen.writeEndObject();
		    		}
		    		
		    		// both pointEstimate and average are point estimates, pick whichever one is not null
		    		if (point != null || avg != null) {
		    			jgen.writeObjectFieldStart("pointEstimate");
		    			(point != null ? point : avg).writeIsochrones(jgen);
		    			jgen.writeEndObject();
		    		}
		    		
		    		if (best != null) {
		    			jgen.writeObjectFieldStart("bestCase");
		    			best.writeIsochrones(jgen);
		    			jgen.writeEndObject();
		    		}
		    		
		    		if (spread != null) {
		    			jgen.writeObjectFieldStart("spread");
		    			spread.writeIsochrones(jgen);
		    			jgen.writeEndObject();
		    		}
		    	}
	    	}
	    	jgen.writeEndObject();
	    	jgen.close();
	    	return baos.toString();
    	} catch (Exception e) {
    		return null;
    	}
    }

    /** Get a result set with times from the cache, or null if the key doesn't exist/has fallen out of the cache */
    public static ResultEnvelope getResultSet (String key) {
    	return envelopeCache.getIfPresent(key);
    }
}
