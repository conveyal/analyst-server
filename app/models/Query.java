package models;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.conveyal.otpac.actors.JobItemActor;
import com.conveyal.otpac.message.JobSpec;
import com.conveyal.otpac.message.WorkResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import controllers.Api;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.analyst.scenario.RemoveTrip;
import org.opentripplanner.analyst.scenario.Scenario;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import play.Logger;
import play.Play;
import utils.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query implements Serializable {

	private static HashMap<String, List<ResultEnvelope>> resultsQueue = new HashMap<String, List<ResultEnvelope>>();
	
	private static final long serialVersionUID = 1L;

	static DataStore<Query> queryData = new DataStore<Query>("queries", true);

	public String id;
	public String projectId;
	public String name;

	/** The mode. Can be left null if both graphId and profileRequest or routingRequest are set */
	public String mode;
	
	public String shapefileId;

	/** The scenario. Can be left null if both graphId and either profileRequest or routingRequest are set */
	public String scenarioId;
	public String status;
	
	public Integer totalPoints;
	public Integer completePoints;
	
	/** the from time of this query. Can be left unset if both graphId and profileRequest or routingRequest are set */
	public int fromTime;

	/** the to time of this query. Can be left unset if both graphId and profileRequest or routingRequest are set */
	public int toTime;

	/** the date of this query. Can be left unset if both graphId and profileRequest or routingRequest are set */
	public LocalDate date;

	/** The graph to use. If profileRequest and routingRequest are both null this will be ignored */
	public String graphId;

	/**
	 * Profile request to use for this query. If set, graphId must not be null. If set, mode, fromTime, toTime,
	 * scenarioId, and date will be ignored.
	 *
	 * Takes precedence over routingRequest; if both are set, a profile request will be performed.
	 */
	public ProfileRequest profileRequest;

	/**
	 * Routing request to use for this query. If set, graphId must not be null. If set, mode, fromTime, toTime,
	 * scenarioId, and date will be ignored.
	 */
	public RoutingRequest routingRequest;
	
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
		
		ActorRef queryActor = Cluster.getActorSystem().actorOf(Props.create(QueryActor.class));
		System.out.println(queryActor.path());
		
		queryActor.tell(this, null);

		save();
		
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
				
				ActorSystem system = Cluster.getActorSystem();
				ActorRef executive = Cluster.getExecutive();

				q.totalPoints = sl.getFeatureCount();
				q.completePoints = 0;

				// build the request(s) if they are null
				ProfileRequest pr = q.profileRequest;
				RoutingRequest rr = q.routingRequest;

				String graphId = q.graphId;

				// users can define params either through scenario IDs, etc., or by specifying a profile request
				// directly.
				if (pr == null && rr == null) {
					// build the requests
					TransportScenario scenario = TransportScenario.getScenario(q.scenarioId);
					graphId = scenario.bundleId;

					if (q.isTransit()) {
						// create a profile request
						pr = Api.analyst.buildProfileRequest(q.mode, q.date, q.fromTime, q.toTime, 0, 0);

						pr.scenario = new Scenario(0);

						if (scenario.bannedRoutes != null) {
							pr.scenario.modifications = scenario.bannedRoutes.stream().map(rs -> {
								RemoveTrip ret = new RemoveTrip();
								ret.agencyId = rs.agencyId;
								ret.routeId = Arrays.asList(rs.id);
								return ret;
							}).collect(Collectors.toList());
						}
						else {
							pr.scenario.modifications = Collections.emptyList();
						}
					}
					else {
						// this is not a transit request, no need for computationally-intensive profile routing
						graphId = q.scenarioId;
						Bundle s = Bundle.getBundle(graphId);
						rr = Api.analyst.buildRequest(q.scenarioId, q.date, q.fromTime, null, q.mode, 120, DateTimeZone.forID(s.timeZone));
					}
				}

				JobSpec js;

				if (pr != null)
					js =  new JobSpec(graphId, sl.id, sl.id, pr);
				else
					js =  new JobSpec(graphId, sl.id, sl.id, rr);

				// plus a callback that registers how many work items have returned
				ActorRef callback = system.actorOf(Props.create(SaveQueryCallback.class, q.id, q.totalPoints));
				js.setCallback(callback);

				// start the job
				executive.tell(js, ActorRef.noSender());							
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
	
	/**
	 * Save the queries as they come back.
	 */
	public static class SaveQueryCallback extends JobItemActor {
		
		/** the query ID */
		public final String id;
		
		/** the number of points completed so far */
		public int complete; 
		
		/** the number of points we expect to complete */
		public final int totalPoints;
		
		/**
		 * Create a new save query callback.
		 * @param id the query ID.
		 */
		public SaveQueryCallback(String id, int totalPoints) {
			this.id = id;
			this.totalPoints = totalPoints;
			complete = 0;
		}
		
		@Override
		public synchronized void onWorkResult(WorkResult res) {			
			if (res.success) {
				Query.saveQueryResult(id, new ResultEnvelope(res));
			}
			
			// update complete after query has been saved
			complete++;
			
			// only update client every 200 points or when the query is done
			if (complete % 200 == 0 || complete == totalPoints) {
				Query query = Query.getQuery(id);				
				query.completePoints = complete;
				query.save();
				
				// flush to disk before saying the query is done
				// transactional support is off, so this is important
				if (complete == totalPoints) {
					query.closeResults();					
				}
			}
		}
	}
}
