package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.cluster.AnalystClusterRequest;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import otp.Analyst;
import play.Logger;
import utils.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query implements Serializable {

	private static final ObjectMapper objectMapper = JsonUtil.getObjectMapper();

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

	/** Has this query finished computing _and_ processing? */
	public boolean complete = false;
	
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

	public static Collection<Query> getAll() {
		return queryData.getAll();
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
	public boolean isTransit () {
		if (this.routingRequest == null && this.profileRequest == null)
			return new TraverseModeSet(this.mode).isTransit();

		else if (this.profileRequest != null)
			return true;

		else
			return this.routingRequest.modes.isTransit();
	}

	/**
	 * Is this a profile request?
	 */
	public boolean isProfile () {
		if (this.routingRequest == null && this.profileRequest == null)
			return isTransit();
		else
			return this.profileRequest != null;
	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			id = IdUtils.getId();
			
			Logger.info("created query q " + id);
		}
		
		queryData.save(id, this);
		
		Logger.info("saved query q " +id);
	}
	
	public void run() {
		QueueManager qm = QueueManager.getManager();

		// enqueue all the requests
		Shapefile shp = Shapefile.getShapefile(this.shapefileId);
		PointSet ps = shp.getPointSet();
		TransportScenario scenario = TransportScenario.getScenario(this.scenarioId);
		Bundle bundle = Bundle.getBundle(scenario.bundleId);

		totalPoints = ps.capacity;
		completePoints = 0;
		this.save();

		List<AnalystClusterRequest> requests = Lists.newArrayList();

		// TODO batch?
		long now = System.currentTimeMillis();
		for (int i = 0; i < ps.capacity; i++) {
			PointFeature pf = ps.getFeature(i);

			AnalystClusterRequest req;

			if (this.isTransit()) {
				ProfileRequest pr = Analyst.buildProfileRequest(this.mode, this.date, this.fromTime, this.toTime, pf.getLat(), pf.getLon());
				req = new AnalystClusterRequest(this.shapefileId, scenario.bundleId, pr);
			} else {
				GenericLocation from = new GenericLocation(pf.getLat(), pf.getLon());
				RoutingRequest rr = Analyst.buildRequest(scenario.bundleId, this.date, this.fromTime, from, this.mode, 120, DateTimeZone.forID(bundle.timeZone));
				req = new AnalystClusterRequest(this.shapefileId, scenario.bundleId, rr);
			}

			req.jobId = this.id;
			req.includeTimes = false;

			requests.add(req);
		}

		qm.addCallback(id, re -> {
			getResults().store(re);

			synchronized (this) {
				this.completePoints++;

				if (this.completePoints == this.totalPoints || this.completePoints % 200 == 0)
					this.save();
			}

			// when the job is complete return false to remove this callback from the rotation
			return this.completePoints < this.totalPoints;
		});

		// enqueue the requests
		qm.enqueue(this.projectId, this.graphId, this.id, requests);

		Logger.info("Enqueued {} items in {}ms", ps.capacity, System.currentTimeMillis() - now);
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
