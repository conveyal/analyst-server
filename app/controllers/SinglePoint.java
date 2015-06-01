package controllers;

import com.csvreader.CsvWriter;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import models.Shapefile;
import org.opentripplanner.analyst.Histogram;
import org.opentripplanner.analyst.ResultSet;
import play.Play;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;
import utils.*;
import utils.ResultEnvelope.Which;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Controllers for getting result sets used in single point mode.
 */
public class SinglePoint extends Controller {
    // cache the result envelopes. 250MB in-memory cache.
    // this doesn't need to be very large; it needs only to store as many result envelopes as there are expected to be
    // active users. Once the user has moved the pin, the probability they will put it back on exactly the same spot
    // is for all intents and purposes zero, so the cache miss rate is very big regardless of cache size.
    //private static ConcurrentMap<String, ResultEnvelope> envelopeCache = DBMaker.newCache(.25);
	private static Map<String, ResultEnvelope> envelopeCache = Maps.newHashMap();
    
    /** re-use object mapper */
    private static final ObjectMapper objectMapper = JsonUtil.getObjectMapper();
    
    /** Create a result from a JSON-ified OneToMany[Profile]Request. */
    public static F.Promise<Result> result () throws JsonProcessingException {
    	if (session().get("username") == null &&
    			Play.application().configuration().getBoolean("api.allow-unauthenticated-access") != true)
    		return F.Promise.pure((Result) unauthorized());
    	
    	// allow cross-origin access if we don't need auth
    	if (Play.application().configuration().getBoolean("api.allow-unauthenticated-access") == true)
    		response().setHeader("Access-Control-Allow-Origin", "*");
    		
    	
    	// deserialize a result
    	// figure out if it's profile or not
    	
    	JsonNode params = request().body().asJson();
    	
    	boolean profile = params.get("profile").asBoolean();

		AnalystClusterRequest req;

		if (profile) {
			req = objectMapper.treeToValue(params, OneToManyProfileRequest.class);
		}
		else {
			req = objectMapper.treeToValue(params, OneToManyRequest.class);
		}

		// state tracking
		req.jobId = null;
		// put the ID first for better performance with S3 writes
		// S3 uses some sort of tree/sequential index, by varying prefixes you write to different parts of that index.
		req.id = IdUtils.getId() + "_single";

		req.includeTimes = true;

		F.RedeemablePromise<Result> result = F.RedeemablePromise.empty();

		QueueManager.getManager().enqueue(req, env -> {
            envelopeCache.put(env.id, env);
            result.success(ok(resultSetToJson(env)).as("application/json"));
        });

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
    	ResultEnvelope env = envelopeCache.get(key);
    	
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
    	return envelopeCache.get(key);
    }
}
