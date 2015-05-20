package models;

import akka.actor.UntypedActor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import controllers.Api;
import models.Bundle.RouteSummary;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import play.Logger;
import play.Play;
import utils.DataStore;
import utils.HashUtils;
import utils.QueryResultStore;
import utils.ResultEnvelope;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query implements Serializable {

	private static HashMap<String, List<ResultEnvelope>> resultsQueue = new HashMap<String, List<ResultEnvelope>>();
	
	private static final long serialVersionUID = 1L;

	static DataStore<Query> queryData = new DataStore<Query>("queries", true);

	public String id;
	public String projectId;
	public String name;
	
	public Integer jobId;
	public String akkaId;

	public String mode;
	
	public String shapefileId;
	
	public String scenarioId;
	public String status;
	
	public Integer totalPoints;
	public Integer completePoints;
	
	// the from time of this query
	public int fromTime;
	
	// the to time of this query
	public int toTime;
	
	public LocalDate date;
	
	@JsonIgnore 
	transient private QueryResultStore results; 
	
	public Query() {
		
	}
	
	static public Query create() {
		
		Query query = new Query();
		query.save();
		
		return query;
	}
	
	/**
	 * Get the shapefile name. This is used in the UI so that we can display the name of the shapefile.
	 */
	public String getShapefileName () {
		Shapefile l = Shapefile.getShapefile(shapefileId);
		
		if (l == null)
			return null;
		
		return l.name;
	}
	
	/**
	 * Does this query use transit?
	 */
	public Boolean isTransit () {
		if (this.mode == null)
			return null;
		
		return new TraverseModeSet(this.mode).isTransit();
	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("q_" + d.toString());
			
			Logger.info("created query q " + id);
		}
		
		queryData.save(id, this);
		
		Logger.info("saved query q " +id);
	}
	
	public void run() {

		// FIXME do something.
		
	}
	
	public void delete() throws IOException {
		queryData.delete(id);
		
		Logger.info("delete query q" +id);
	}

	private synchronized void makeResultDb() {
		if (results == null) {
			results = new QueryResultStore(this);
		}
	}
	
	@JsonIgnore
	public QueryResultStore getResults() {
		
		if (results == null) {
			makeResultDb();
		}
		
		return results;
	}
	
	/** close the results database, ensuring it is written to disk */
	public synchronized void closeResults () {
		if (results != null) {
			results.close();
			results = null;
		}
	}
	
	public Integer getPercent() {
		if(this.totalPoints != null && this.completePoints != null && this.totalPoints > 0)
			return Math.round((float)((float)this.completePoints / (float)this.totalPoints) * 100); 
		else 
			return 0;
	}
	
	static public Query getQuery(String id) {
		
		return queryData.getById(id);	
	}
	
	static public Collection<Query> getQueries(String projectId) {
		
		if(projectId == null)
			return queryData.getAll();
		
		else {
			
			Collection<Query> data = new ArrayList<Query>();
			
			for(Query sd : queryData.getAll()) {
				if(sd.projectId != null && sd.projectId.equals(projectId))
					data.add(sd);
				
			}
				
			return data;
		}	
	}
	
	static void saveQueryResult(String id, ResultEnvelope resultEnvelope) {
		
		Query q = getQuery(id);

		if(q == null)
			return;
		
		q.getResults().store(resultEnvelope);
	}
	
	public static class QueryActor extends UntypedActor {
		
		public void onReceive(Object message) throws Exception {
			if (message instanceof Query) {
				
				final Query q = (Query)message;
	
				Shapefile sl = Shapefile.getShapefile(q.shapefileId);
				
				Boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline");
				
				if (workOffline == null)
					workOffline = true;
				
				q.totalPoints = sl.getFeatureCount();
				q.completePoints = 0;
				
				TransportScenario scenario = TransportScenario.getScenario(q.scenarioId);
				String graphId = scenario.bundleId;
				
				if (q.isTransit()) {
					// create a profile request
					ProfileRequest pr = Api.analyst.buildProfileRequest(q.mode, q.date, q.fromTime, q.toTime, 0, 0);
					
					Collection<String> bannedRoutes =
							Collections2.transform(scenario.bannedRoutes, new Function<RouteSummary, String> () {

								@Override
								public String apply(RouteSummary route) {
									return String.format(Locale.US, "%s_%s", route.agencyId, route.id);
								}
							});
					
					pr.bannedRoutes = new ArrayList<String>(bannedRoutes);
					
					// the pointset is already in the cluster cache, from when it was uploaded.
					// every pointset has all shapefile attributes.
				}
				else {
					// this is not a transit request, no need for computationally-intensive profile routing 
					Bundle s = Bundle.getBundle(q.scenarioId);
					RoutingRequest rr = Api.analyst.buildRequest(q.scenarioId, q.date, q.fromTime, null, q.mode, 120, DateTimeZone.forID(s.timeZone));
				}
			}
		} 
	}

	
	/**
	 * Get all the queries for a point set.
	 */
	public static Collection<Query> getQueriesByPointSet(String shapefileId) {
		Collection<Query> ret = new ArrayList<Query>();

		for (Query q : queryData.getAll()) {
			if (q.shapefileId != null && q.shapefileId.equals(shapefileId)) {
				ret.add(q);
			}
		}

		return ret;
	}
}
