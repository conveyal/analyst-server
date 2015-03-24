package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import com.conveyal.otpac.PrototypeAnalystProfileRequest;
import com.conveyal.otpac.actors.JobItemActor;
import com.conveyal.otpac.message.SinglePointJobSpec;
import com.conveyal.otpac.message.WorkResult;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import models.Scenario;
import models.Shapefile;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mapdb.DBMaker;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;

import play.libs.F;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Cluster;
import utils.ResultEnvelope;
import utils.ResultEnvelope.Which;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
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

    /**
     * Get a ResultSet.
     */
    public static F.Promise<Result> result(String shapefile, String graphId, Double lat, Double lon, String mode,
                                           Double bikeSpeed, Double walkSpeed, String date, int fromTime, int toTime) {

        ResultEnvelope.Which whichEnum_tmp;
        LocalDate jodaDate;

        try {
            jodaDate = LocalDate.parse(date);
        } catch (Exception e) {
            // no need to pollute the console with a stack trace
            return resolveNow((Result) badRequest("Invalid value for date parameter"));
        }

        final String key = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s", graphId, lat, lon, mode,
                bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
                fromTime, toTime, shapefile);
        
        final Shapefile shp = Shapefile.getShapefile(shapefile);

        // is it cached?
        if (envelopeCache.containsKey(key)) {
        	String json = resultSetToJson(envelopeCache.get(key), shp);
        	
        	if (json != null)
        		return resolveNow((Result) ok().as("application/json"));
        	else
        		return resolveNow((Result) badRequest());
        }
        else {
            // build it, add it to the cache, and return it when we're ready
            SinglePointJobSpec spec;
            if (new TraverseModeSet(mode).isTransit()) {
                ProfileRequest req = Api.analyst.buildProfileRequest(mode, jodaDate, fromTime, toTime, lat, lon);
                spec = new SinglePointJobSpec(graphId, shapefile + ".json", req);
            }
            else {
            	Scenario s = Scenario.getScenario(graphId);
                RoutingRequest req = Api.analyst.buildRequest(graphId,jodaDate, fromTime, new GenericLocation(lat, lon), mode, 120, DateTimeZone.forID(s.timeZone));
                spec = new SinglePointJobSpec(graphId, shapefile + ".json", req);
            }

            spec.includeTimes = true;
            
            ActorSystem sys = Cluster.getActorSystem();

            F.RedeemablePromise<WorkResult> result = F.RedeemablePromise.empty();
            ActorRef callback = sys.actorOf(Props.create(SinglePointListener.class, result));
            spec.callback = callback;

            ActorRef exec = Cluster.getExecutive();

            exec.tell(spec, ActorRef.noSender());

            return result.map(new F.Function<WorkResult, Result> () {
                @Override
                public Result apply(WorkResult workResult) throws Throwable {
                    ResultEnvelope res = new ResultEnvelope(workResult);
                    envelopeCache.put(key, res);
                    String json = resultSetToJson(res, shp);
                    
                    if (json != null)
                    	return ok(json).as("application/json");
                    else
                    	return badRequest();
                }
            });
        }
    }
    
    private static String resultSetToJson (ResultEnvelope rs, Shapefile shp) {
    	try {
    		ResultSet worst = rs.get(Which.WORST_CASE);
    		ResultSet point = rs.get(Which.POINT_ESTIMATE);
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
		    	shp.getPointSet().writeJsonProperties(jgen);
		    	
		    	jgen.writeObjectFieldStart("data");
		    	{
			    	for (String propertyId : (point != null ? point : worst).histograms.keySet()) {
			    		jgen.writeObjectFieldStart(propertyId);
			    		{
				    		if (worst != null) {
				    			jgen.writeObjectFieldStart("worstCase");
				    			worst.histograms.get(propertyId).writeJson(jgen);
				    			jgen.writeEndObject();
				    		}
				    		
				    		if (point != null) {
				    			jgen.writeObjectFieldStart("pointEstimate");
				    			point.histograms.get(propertyId).writeJson(jgen);
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
}
