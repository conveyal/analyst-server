package models;

import com.beust.jcommander.internal.Lists;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.TraverseModeSet;
import otp.Analyst;
import play.Logger;
import play.Play;
import utils.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query implements Serializable {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	static {
		objectMapper.registerModule(new JodaModule());
	}

	private static HashMap<String, List<ResultEnvelope>> resultsQueue = new HashMap<String, List<ResultEnvelope>>();
	
	private static final long serialVersionUID = 1L;

	static DataStore<Query> queryData = new DataStore<Query>("queries", true);

	public String id;
	public String projectId;
	public String name;

	public String mode;
	
	public String shapefileId;
	
	public String scenarioId;
	public String status;
	
	public Integer totalPoints;
	public Integer completePoints;

	/** Has this query finished computing _and_ processing? */
	public boolean complete = false;
	
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
	public Boolean isTransit () {
		if (this.mode == null)
			return null;
		
		return new TraverseModeSet(this.mode).isTransit();
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

		QueryResultStore.accumulate(this);

		List<AnalystClusterRequest> requests = Lists.newArrayList();

		// TODO batch?
		long now = System.currentTimeMillis();
		for (int i = 0; i < ps.capacity; i++) {
			PointFeature pf = ps.getFeature(i);

			AnalystClusterRequest req;

			if (this.isTransit()) {
				OneToManyProfileRequest pr = new OneToManyProfileRequest();
				pr.options = Analyst.buildProfileRequest(this.mode, this.date, this.fromTime, this.toTime, pf.getLat(), pf.getLon());
				pr.options.bannedRoutes = scenario.bannedRoutes.stream().map(rs -> rs.agencyId + "_" + rs.id).collect(Collectors.toList());
				req = pr;
			} else {
				OneToManyRequest rr = new OneToManyRequest();
				GenericLocation from = new GenericLocation(pf.getLat(), pf.getLon());
				rr.options = Analyst.buildRequest(rr.graphId, this.date, this.fromTime, from, this.mode, 120, DateTimeZone.forID(bundle.timeZone));
				req = rr;
			}

			req.destinationPointsetId = this.shapefileId;
			req.graphId = scenario.bundleId;
			req.outputQueue = Play.application().configuration().getString("cluster.results-bucket");
			req.jobId = this.id;
			req.id = pf.getId() != null ? pf.getId() : "" + i;
			req.includeTimes = false;

			requests.add(req);
		}

		qm.enqueueBatch(requests);

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
