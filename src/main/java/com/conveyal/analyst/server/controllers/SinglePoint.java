package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.otp.Analyst;
import com.conveyal.analyst.server.utils.IdUtils;
import com.conveyal.analyst.server.utils.JsonUtil;
import com.conveyal.analyst.server.utils.QueueManager;
import com.conveyal.analyst.server.utils.QuotaLedger;
import com.conveyal.r5.analyst.Histogram;
import com.conveyal.r5.analyst.ResultSet;
import com.conveyal.r5.analyst.cluster.AnalystClusterRequest;
import com.conveyal.r5.analyst.cluster.GenericClusterRequest;
import com.conveyal.r5.analyst.cluster.ResultEnvelope;
import com.conveyal.r5.common.R5Version;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import models.Bundle;
import models.Project;
import models.Shapefile;
import models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map.Entry;

import static spark.Spark.halt;

/**
 * Controllers for getting result sets used in single point mode.
 */
public class SinglePoint extends Controller {
	private static final Logger LOG = LoggerFactory.getLogger(SinglePoint.class);

    // cache the result envelopes, but don't run out of memory
	// if we use a direct cache from MapDB, the system comes apart at the seams because MapDB has a lot of trouble serializing
	// the isochrones
	private static Cache<String, ResultEnvelope> envelopeCache = CacheBuilder.newBuilder().maximumSize(500).build();

    /** re-use object mapper */
    private static final ObjectMapper objectMapper = JsonUtil.getObjectMapper();
    
    /**
	 * Create a result from a JSON-ified OneToMany[Profile]Request.
	 *
	 * TODO: this should be made asynchronous, but that's not an easy thing to do within the confines of Spark.
	 */
    public static Object result (Request request, Response res) throws Exception {
		Authentication.authenticatedOrCors(request, res);
    	
    	// deserialize a result
    	// figure out if it's profile or not
		GenericClusterRequest genericReq = null;
		try {
			genericReq = objectMapper.readValue(request.body(), GenericClusterRequest.class);
		} catch (IOException e) {
			halt(BAD_REQUEST, e.getMessage());
		}

		if (genericReq instanceof AnalystClusterRequest) {
			AnalystClusterRequest req = (AnalystClusterRequest) genericReq;

			// don't include times on isochrone requests, they're enormous
			req.includeTimes = req.destinationPointsetId != null;
			req.jobId = IdUtils.getId();

			Bundle bundle = Bundle.getBundle(req.graphId);

			// provide some security; if we don't have this bundle and shapefile here, don't let them make a request
			// against it (they may have the UUID from somewhere else and be trying to use our server as a back door
			// to the cluster)
			if (bundle == null)
				halt(NOT_FOUND, "No such bundle or pointset, or you do not have permission to access them");

			if (req.destinationPointsetId != null) {
				Shapefile shp = Shapefile.getShapefile(req.destinationPointsetId);

				if (shp == null)
					halt(NOT_FOUND, "No such bundle or pointset, or you do not have permission to access them");

				if (!bundle.projectId.equals(shp.projectId))
					halt(BAD_REQUEST, "bundle and pointset do not match");
			}

			// permissions check
			Project p = Project.getProject(bundle.projectId);
			User u = currentUser(request);
			if (u != null && !u.hasReadPermission(p))
				// NB this message is exactly the same as the one above, so as to not reveal any information
				halt(NOT_FOUND, "No such bundle or pointset, or you do not have permission to access them");

			// specify r5 version
			req.workerVersion = p.r5version != null && !p.r5version.isEmpty() ? p.r5version : R5Version.version;

			// TODO enforce quota for users who are not logged on, or remove unauthenticated access
			if (u.getQuota() < 1)
				halt(FORBIDDEN, INSUFFICIENT_QUOTA);

			try {
				ResultEnvelope re = QueueManager.getManager().getSinglePoint(req);

				if (re == null) {
					// graph is still building/workers are starting
					// Client should retry later
					halt(SERVICE_UNAVAILABLE, "Workers starting up");
				}

				re.id = req.jobId;

				envelopeCache.put(re.id, re);

				res.type("application/json");

				String json = resultSetToJson(re);

				// increment the quota at the last possible moment so that we don't charge them if something went wrong
				QuotaLedger.LedgerEntry entry = new QuotaLedger.LedgerEntry();
				entry.delta = -1;
				entry.userId = u.username;
				entry.groupId = u.groupName;
				entry.reason = QuotaLedger.LedgerReason.SINGLE_POINT;
				u.addLedgerEntry(entry);
				return json;
			} catch (Exception e) {
				// don't halt if we've already halted
				if (e instanceof HaltException)
					throw e;

				LOG.error("Error creating single-point result", e);
				halt(INTERNAL_SERVER_ERROR, e.getMessage());
			}
		} else {
			// hand it off to the broker directly
			// no need to check permissions, UUIDs are stronger than passwords
			byte[] result = QueueManager.getManager().getGenericRequest(genericReq);
			if (result == null) halt(202); // 202 Accepted, indicating workers are starting
			else return result;
		}

		return null;
    }

	public static String options (Request req, Response res) {
		Authentication.authenticatedOrCors(req, res);
		return "";
	}
    
    /** Create a CSV result 
     * @throws IOException */
    public static String csv(Request req, Response res) throws IOException {
		Authentication.authenticatedOrCors(req, res);

		ResultEnvelope.Which which = ResultEnvelope.Which.valueOf(req.queryParams("which"));
		String key = req.queryParams("key");
    	
    	// get the resultset
    	ResultEnvelope env = envelopeCache.getIfPresent(key);
    	
    	if (env == null)
    		halt(NOT_FOUND, "no such key");
    	
    	ResultSet rs = env.get(which);
    	
    	// create a CSV
		// TODO: stream directly into request
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
    	String filename = key.substring(0, 5) + "_" + which.toString().toLowerCase() + ".csv";
    	
    	res.header("Content-Disposition", "attachment; filename=" + filename);
    	
    	return baos.toString();
    }
    
    private static String resultSetToJson (ResultEnvelope rs) throws Exception {
		ResultSet worst = rs.get(ResultEnvelope.Which.WORST_CASE);
		ResultSet point = rs.get(ResultEnvelope.Which.POINT_ESTIMATE);
		ResultSet avg   = rs.get(ResultEnvelope.Which.AVERAGE);
		ResultSet best  = rs.get(ResultEnvelope.Which.BEST_CASE);
		ResultSet spread= rs.get(ResultEnvelope.Which.SPREAD);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
    }

    /** Get a result set with times from the cache, or null if the key doesn't exist/has fallen out of the cache */
    public static ResultEnvelope getResultSet (String key) {
    	return envelopeCache.getIfPresent(key);
    }
}
