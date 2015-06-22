package models;

import com.conveyal.analyst.server.otp.Analyst;
import com.conveyal.analyst.server.utils.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.cluster.AnalystClusterRequest;
import org.opentripplanner.analyst.cluster.ResultEnvelope;
import org.opentripplanner.analyst.scenario.RemoveTrip;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query implements Serializable {
	private static final Logger LOG = LoggerFactory.getLogger(Query.class);

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
			
			LOG.info("created query q " + id);
		}
		
		queryData.save(id, this);
		
		LOG.info("saved query q " +id);
	}
	
	public void run() {
		QueueManager qm = ClusterQueueManager.getManager();

		// enqueue all the requests
		Shapefile shp = Shapefile.getShapefile(this.shapefileId);
		PointSet ps = shp.getPointSet();

		totalPoints = ps.capacity;
		completePoints = 0;
		this.save();

		List<AnalystClusterRequest> requests = Lists.newArrayList();

		// TODO batch?
		long now = System.currentTimeMillis();
		for (int i = 0; i < ps.capacity; i++) {
			PointFeature pf = ps.getFeature(i);

			AnalystClusterRequest req;

			ProfileRequest pr = this.profileRequest;
			RoutingRequest rr = this.routingRequest;

			String graphId = this.graphId;

			// users can define params either through scenario IDs, etc., or by specifying a profile request
			// directly.
			if (pr == null && rr == null) {
				// build the requests
				// TODO not necessary to do this for every feature
				TransportScenario scenario = TransportScenario.getScenario(this.scenarioId);
				graphId = scenario.bundleId;

				if (this.isTransit()) {
					// create a profile request
					pr = Analyst.buildProfileRequest(this.mode, this.date, this.fromTime, this.toTime, 0, 0);

					pr.scenario = new org.opentripplanner.analyst.scenario.Scenario(0);

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
					graphId = this.scenarioId;
					Bundle s = Bundle.getBundle(graphId);
					rr = Analyst.buildRequest(this.scenarioId, this.date, this.fromTime, null, this.mode, 120, DateTimeZone.forID(s.timeZone));
				}
			}

			if (pr != null) {
				pr.fromLat = pr.toLat = pf.getLat();
				pr.fromLon = pr.toLon = pf.getLon();
				req = new AnalystClusterRequest(this.shapefileId, graphId, pr);
			}
			else {
				rr.from = rr.to = new GenericLocation(pf.getLat(), pf.getLon());
				req = new AnalystClusterRequest(this.shapefileId, graphId, rr);
			}

			req.jobId = this.id;
			req.id = pf.getId();
			req.includeTimes = false;

			requests.add(req);
		}

		qm.addCallback(id, re -> {
			getResults().store(re);

			boolean done;

			synchronized (this) {
				this.completePoints++;

				done = this.completePoints.equals(this.totalPoints);

				if (done || this.completePoints % 200 == 0)
					this.save();
			}

			if (done)
				this.closeResults();

			// when the job is complete return false to remove this callback from the rotation
			return !done;
		});

		// enqueue the requests
		qm.enqueue(requests);

		LOG.info("Enqueued {} items in {}ms", ps.capacity, System.currentTimeMillis() - now);
	}

	public String getGraphId () {
		if (this.graphId != null)
			return this.graphId;

		else if (this.scenarioId != null && TransportScenario.getScenario(this.scenarioId) != null)
			return TransportScenario.getScenario(this.scenarioId).bundleId;

		else return null;
	}

	public void delete() throws IOException {
		queryData.delete(id);
		
		LOG.info("delete query q" +id);
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
	
	static public Collection<Query> getQueriesByProject(String projectId) {
		return queryData.getAll().stream().filter(q -> projectId.equals(q.projectId))
				.collect(Collectors.toList());
	}

	public static Collection<Query> getQueries () {
		return queryData.getAll();
	}
}
