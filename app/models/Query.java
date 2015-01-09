package models;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.joda.time.LocalDate;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.ResultSetWithTimes;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;

import otp.Analyst;
import otp.AnalystProfileRequest;
import play.Logger;
import play.Play;
import play.libs.Akka;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import utils.Cluster;
import utils.DataStore;
import utils.HashUtils;
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

	static DataStore<Query> queryData = new DataStore<Query>("queries");

	public String id;
	public String projectId;
	public String name;
	
	public Integer jobId;
	public String akkaId;

	public String mode;
	
	public String shapefileId;

	public String attributeName;
	
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
	transient private DataStore<ResultEnvelope> results; 
	
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
	
	/**
	 * What attribute is this associated with?
	 */
	public Attribute getAttribute () {
		Shapefile l = Shapefile.getShapefile(shapefileId);
		
		if (l == null)
			return null;
		
		return l.attributes.get(attributeName);
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
		
		ActorRef queryActor = Akka.system().actorOf(Props.create(QueryActor.class));
		System.out.println(queryActor.path());
		
		queryActor.tell(this, null);
		
		akkaId = queryActor.path().name();
		
		save();
		
	}
	
	public void delete() throws IOException {
		queryData.delete(id);
		
		Logger.info("delete query q" +id);
	}

	@JsonIgnore
	public synchronized DataStore<ResultEnvelope> getResults() {
		
		if(results == null) {
			results = new DataStore<ResultEnvelope>(new File(Application.dataPath, "results"), "r_" + id);
		}
		
		return results;
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

		ArrayList<ResultEnvelope> writeList = null;
		
		synchronized(resultsQueue) {
			if(!resultsQueue.containsKey(id))
				resultsQueue.put(id, new ArrayList<ResultEnvelope>());
			resultsQueue.get(id).add(resultEnvelope);
			
			if(resultsQueue.get(id).size() > 10) {
				writeList = new ArrayList<ResultEnvelope>(resultsQueue.get(id));
				resultsQueue.get(id).clear();
				Logger.info("flushing queue...");
			}
			
		}
		
		if(writeList != null){
				for(ResultEnvelope rf1 : writeList)
					q.getResults().saveWithoutCommit(rf1.id, rf1);
			
				q.getResults().commit();
				
				Tiles.resetQueryCache(id);
		}
					
		
	}
	
	static void updateStatus(String id, JobStatus js) {
		
		Query q = getQuery(id);
		
		if(q == null)
			return;
		
		synchronized(q) {
			q.totalPoints = (int)js.total;
			q.completePoints = (int)js.complete;
			q.jobId = js.curJobId;
			q.save();
		}
		
		Logger.info("status update: " + js.complete + "/" + js.total);
	}
	
	public static class QueryActor extends UntypedActor {
		
		public void onReceive(Object message) throws Exception {
			if (message instanceof Query) {
				
				final Query q = (Query)message;
	
				Shapefile sl = Shapefile.getShapefile(q.shapefileId);
				
				Boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline");
				
				if (workOffline == null)
					workOffline = true;
				
				String pointSetCachedName = sl.writeToClusterCache(workOffline, q.attributeName);
				
				ActorSystem system = Cluster.getActorSystem();
				ActorRef executive = Cluster.getExecutive();
				
				JobSpec js;
				
				if (q.isTransit()) {
					// create a profile request
					ProfileRequest pr = Api.analyst.buildProfileRequest(q.mode, q.date, q.fromTime, q.toTime, null);
					js = new JobSpec(q.scenarioId, pointSetCachedName, pointSetCachedName, pr);
				}
				else {
					// this is not a transit request, no need for computationally-expensive profile routing 
					RoutingRequest rr = Api.analyst.buildRequest(q.scenarioId, q.date, q.fromTime, null, q.mode, 120);
					js = new JobSpec(q.scenarioId, pointSetCachedName, pointSetCachedName, rr);
				}

				// plus a callback that registers how many work items have returned
				ActorRef callback = system.actorOf(Props.create(SaveQueryCallback.class, q.id));
				js.setCallback(callback);

				// start the job
				Timeout timeout = new Timeout(Duration.create(60, "seconds"));
				Future<Object> future = Patterns.ask(executive, js, timeout);
				
				int jobId = ((JobId) Await.result(future, timeout.duration())).jobId;
				
				JobStatus status = null;
				
				// wait for job to complete
				do {
					Thread.sleep(500);
					try {
						status = Cluster.getStatus(jobId);
						
						if(status != null)
							Query.updateStatus(q.id, status);
						else
							Logger.debug("waiting for job status messages, incomplete");
					}
					catch (Exception e) {
						Logger.debug("waiting for job status messages");
					}
					
					
				} while(status == null || !status.isComplete());				
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
		
		/**
		 * Create a new save query callback.
		 * @param id the query ID.
		 */
		public SaveQueryCallback(String id) {
			this.id = id;
		}
		
		@Override
		public synchronized void onWorkResult(WorkResult res) {
			if (res.getAvgCase() instanceof ResultSetWithTimes || res.getResult() instanceof ResultSetWithTimes)
				Logger.info("received result set including times");
//			Query.saveQueryResult(id, new ResultEnvelope(res));
		}
	}
}
