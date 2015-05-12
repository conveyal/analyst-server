package models;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;

import otp.Analyst;
import play.Logger;
import play.Play;
import play.libs.Akka;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import utils.Cluster;
import utils.DataStore;
import utils.HashUtils;
import utils.QueryResultStore;
import utils.ResultEnvelope;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;

import com.conveyal.otpac.actors.JobItemActor;
import com.conveyal.otpac.message.JobId;
import com.conveyal.otpac.message.JobSpec;
import com.conveyal.otpac.message.JobStatus;
import com.conveyal.otpac.message.WorkResult;
import com.conveyal.otpac.standalone.StandaloneCluster;
import com.conveyal.otpac.standalone.StandaloneExecutive;
import com.conveyal.otpac.standalone.StandaloneWorker;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vividsolutions.jts.geom.Geometry;

import controllers.Api;
import controllers.Application;
import controllers.Tiles;

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
		
		ActorRef queryActor = Cluster.getActorSystem().actorOf(Props.create(QueryActor.class));
		System.out.println(queryActor.path());
		
		queryActor.tell(this, null);
		
		akkaId = queryActor.path().name();
		
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
				
				JobSpec js;
				
				String pointSetId = sl.id + ".json";
				
				q.totalPoints = sl.getFeatureCount();
				q.completePoints = 0;
				
				if (q.isTransit()) {
					// create a profile request
					ProfileRequest pr = Api.analyst.buildProfileRequest(q.mode, q.date, q.fromTime, q.toTime, 0, 0);
					// the pointset is already in the cluster cache, from when it was uploaded.
					// every pointset has all shapefile attributes.
					js = new JobSpec(q.scenarioId, pointSetId, pointSetId, pr);
				}
				else {
					// this is not a transit request, no need for computationally-intensive profile routing 
					Bundle s = Bundle.getBundle(q.scenarioId);
					RoutingRequest rr = Api.analyst.buildRequest(q.scenarioId, q.date, q.fromTime, null, q.mode, 120, DateTimeZone.forID(s.timeZone));
					js = new JobSpec(q.scenarioId, pointSetId, pointSetId, rr);
				}

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
