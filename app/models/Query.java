package models;

import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.vividsolutions.jts.geom.Geometry;
import controllers.Api;
import models.Bundle.RouteSummary;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import otp.Analyst;
import play.Logger;
import play.Play;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.*;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
	
	public Integer jobId;
	public String akkaId;

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

	private static AmazonS3Client s3 = null;

	private static final String resultsBucket = Play.application().configuration().getString("cluster.results-bucket");

	public Query() {
		
	}

	// pick up queries that are already running
	static {
		for (Query q : queryData.getAll()) {
			if (!q.complete) {
				q.watch();
			}
		}
	}
	
	static public Query create() {
		
		Query query = new Query();
		query.save();
		
		return query;
	}

	private static void initializeS3 () {
		if (s3 == null) {
			synchronized (QueryWatcher.class) {
				if (s3 == null) {
					String s3CredentialsFilename = Play.application().configuration().getString("cluster.aws-credentials");

					if (s3CredentialsFilename != null) {
						AWSCredentials creds = new ProfileCredentialsProvider(s3CredentialsFilename, "default").getCredentials();
						s3 = new AmazonS3Client(creds);
					}
					else {
						// S3 credentials propagated to EC2 instances via IAM roles
						s3 = new AmazonS3Client();
					}
				}
			}
		}
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

		// start watching for results now.
		this.watch();

		// TODO batch?
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
			req.disposition = AnalystClusterRequest.RequestDisposition.STORE;
			req.outputLocation = Play.application().configuration().getString("cluster.results-bucket");
			req.jobId = this.id;
			req.id = pf.getId() != null ? pf.getId() : "" + i;
			req.includeTimes = false;

			// TODO parallelize?
			qm.enqueue(req);
		}
	}

	/** watch this query's progress and update completePoints */
	private void watch() {
		QueryWatcher qw = new QueryWatcher(this);

		// watch for results
		Cancellable c = Akka.system().scheduler().schedule(
				Duration.create(10, TimeUnit.SECONDS),
				// check for updates every ten seconds
				Duration.create(10, TimeUnit.SECONDS),
				qw,
				Akka.system().dispatcher()
		);

		qw.setCancellable(c);
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

	/** Process the results once a query is complete and copy them from S3 to MapDB */
	protected void processResults () {
		if (completePoints != totalPoints)
			throw new UnsupportedOperationException("Tried to process incomplete query results!");

		// S3 should already be initialized, but why not?
		initializeS3();

		Logger.info("Processing {} results", this.totalPoints);

		QueryResultStore qrs = new QueryResultStore(this);

		// get the results
		ListObjectsRequest req = new ListObjectsRequest();
		req.setBucketName(resultsBucket);
		req.setPrefix(this.jobId + "/");
		// TODO: this may be too slow with large queries
		int count = 0;

		ObjectListing ls;
		do {
			ls = s3.listObjects(req);

			List<S3ObjectSummary> oss = ls.getObjectSummaries();

			count += oss.size();

			// process in parallel; we've had this be the bottleneck in the past
			// TODO could this cause too many simultaneous S3 requests? What will happen then?
			oss.parallelStream().forEach(os -> {
				S3Object obj = s3.getObject(resultsBucket, os.getKey());
				InputStream is = obj.getObjectContent();
				ResultEnvelope re;
				try {
					re = objectMapper.readValue(is, ResultEnvelope.class);
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				} finally {
					try {
						is.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				qrs.store(re);
			});

			req = req.withMarker(ls.getNextMarker());
			ls = s3.listObjects(req);
			count += ls.getObjectSummaries().size();
		} while (ls.isTruncated());

		qrs.close();

		complete = true;
		save();
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

	/** Watch this query for completion and update its status */
	public static class QueryWatcher implements Runnable {
		/**
		 * This is the cancellable that refers to this Runnable, so we can cancel polling when we're done
		 */
		private Cancellable cancellable = null;

		private final Query q;

		public QueryWatcher(Query q) {
			this.q = q;

			// ensure the s3 client is available
			initializeS3();
		}

		public void setCancellable(Cancellable c) {
			if (cancellable != null)
				throw new UnsupportedOperationException("Tried to set cancellable on querywatcher with already-set cancellable");

			this.cancellable = c;
		}


		@Override
		public void run() {
			ListObjectsRequest req = new ListObjectsRequest();
			req.setBucketName(resultsBucket);
			req.setPrefix(q.id + "/");
			// TODO: this may be too slow with large queries
			ObjectListing ls = s3.listObjects(req);

			int count = ls.getObjectSummaries().size();

			while (ls.isTruncated()) {
				req = req.withMarker(ls.getNextMarker());
				ls = s3.listObjects(req);
				count += ls.getObjectSummaries().size();
			}

			if (q.completePoints == null || q.completePoints != count) {
				q.completePoints = count;
				q.save();
			}
			;

			// query is done!
			if (q.completePoints == q.totalPoints && cancellable != null) {
				Logger.info("Completed query {}", q.id);
				cancellable.cancel();
				q.processResults();
			}
		}
	}
}
