package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import com.conveyal.otpac.actors.JobItemActor;
import com.conveyal.otpac.message.OneToManyProfileRequest;
import com.conveyal.otpac.message.OneToManyRequest;
import com.conveyal.otpac.message.SinglePointJobSpec;
import com.conveyal.otpac.message.WorkResult;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.KeyDeserializers;
import com.fasterxml.jackson.databind.module.SimpleKeyDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;

import models.Bundle;
import models.Shapefile;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mapdb.DBMaker;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.analyst.Histogram;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.request.BannedStopSet;

import play.Play;
import play.libs.F;
import play.libs.F.Function;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import utils.Cluster;
import utils.ResultEnvelope;
import utils.ResultEnvelope.Which;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static utils.PromiseUtils.resolveNow;

/**
 * Controllers for getting result sets used in single point mode.
 */
public class SinglePoint extends Controller {
    // cache the result envelopes. 250MB in-memory cache.
    // this doesn't need to be very large; it needs only to store as many result envelopes as there are expected to be
    // active users. Once the user has moved the pin, the probability they will put it back on exactly the same spot
    // is for all intents and purposes zero, so the cache miss rate is very big regardless of cache size.
    private static ConcurrentMap<String, ResultEnvelope> envelopeCache = DBMaker.newCache(.25);
    
    /** re-use object mapper */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
    	objectMapper.registerModule(new JodaModule());
    	objectMapper.registerModule(new RoutingRequestModule());
    }
    
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
    	
    	SinglePointJobSpec spec;
    	
    	Shapefile shpTemp;
    	
    	if (profile) {
    		OneToManyProfileRequest req = objectMapper.treeToValue(params, OneToManyProfileRequest.class);
    		shpTemp = req.destinationPointsetId == null ? null : Shapefile.getShapefile(req.destinationPointsetId);
    		// for now not requesting a vector isochrone
    		spec = new SinglePointJobSpec(req.graphId, req.destinationPointsetId, req.options, true);
    	}
    	else {
    		OneToManyRequest req = objectMapper.treeToValue(params, OneToManyRequest.class);
    		shpTemp = req.destinationPointsetId == null ? null : Shapefile.getShapefile(req.destinationPointsetId);
    		spec = new SinglePointJobSpec(req.graphId, req.destinationPointsetId, req.options, true);
    	}
    	
        final Shapefile shp = shpTemp;
    	
    	// needed to render tiles
    	spec.includeTimes = true;
    	
    	// make up a "surface ID" - this isn't actually an OTP surface ID, because . . .
    	// 1) Profile requests no longer use surfaces at all
    	// 2) These may be computed by different machines
    	// Above we use keys in the cache; there's no need to do that here as the client
    	// is tracking the surface ID. There is no chance for a collision between these keys
    	// and the key format used above, so this is completely.
    	final String key = UUID.randomUUID().toString();
    	
    	// caching is a pain and the cache miss rate is enormous. Don't check if this request is cached,
    	// just run it again. The cache is used for rendering tiles only.
    	
    	// TODO duplicated code here is ugly
        ActorSystem sys = Cluster.getActorSystem();

        F.RedeemablePromise<WorkResult> result = F.RedeemablePromise.empty();
        ActorRef callback = sys.actorOf(Props.create(SinglePointListener.class, result));
        spec.callback = callback;

        ActorRef exec = Cluster.getExecutive();

        exec.tell(spec, ActorRef.noSender());
        
        return result.map(new Function<WorkResult, Result>() {

			@Override
			public Result apply(WorkResult result) throws Throwable {
                ResultEnvelope res = new ResultEnvelope(result);
                res.shapefile = shp != null ? shp.id : null;
                envelopeCache.put(key, res);
                String json = resultSetToJson(res, shp, key);
                
                if (json != null)
                	return ok(json).as("application/json");
                else
                	return badRequest();
			}
        	
		});
    	
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
    
    private static String resultSetToJson (ResultEnvelope rs, Shapefile shp, String key) {
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
	    		if (shp != null)
	    			shp.getPointSet().writeJsonProperties(jgen);
		    	
		    	jgen.writeStringField("key", key);
		    	
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
    

    /** Listen for a result to be done, and resolve the redeemable promise when it is. */
    public static class SinglePointListener extends JobItemActor {
        private F.RedeemablePromise<WorkResult> promise;

        public SinglePointListener (F.RedeemablePromise<WorkResult> promise) {
            this.promise = promise;
        }

        @Override
        public void onWorkResult(WorkResult workResult) {
            promise.success(workResult);
        }
    }
    
    /** Deserializer for AgencyAndId, for agencyid_id format in bannedTrips */
    public static class AgencyAndIdDeserializer extends KeyDeserializer {

		@Override
		public AgencyAndId deserializeKey(String arg0, DeserializationContext arg1)
				throws IOException, JsonProcessingException {
			String[] sp = arg0.split("_");
			return new AgencyAndId(sp[0], sp[1]);
		}
    }
    
    /** module with jackson config to deserialize routing requests */
    public static class RoutingRequestModule extends SimpleModule {
    	public RoutingRequestModule () {
    		super("RoutingRequestModule", new Version(0, 0, 1, null, null, null));
    	}
    	
    	@Override
    	public void setupModule (SetupContext ctx) {
    		SimpleKeyDeserializers kd = new SimpleKeyDeserializers();
    		kd.addDeserializer(AgencyAndId.class, new AgencyAndIdDeserializer());
    		ctx.addKeyDeserializers(kd);
    	}
    }
}
