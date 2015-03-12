package models;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import jobs.ProcessTransitScenarioJob;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.geometry.Envelope2D;
import org.joda.time.DateTimeZone;
import org.mapdb.Atomic;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.Fun.Tuple2;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Agency;
import com.conveyal.gtfs.model.Shape;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;
import com.conveyal.gtfs.model.Trip;
import com.conveyal.otpac.ClusterGraphService;
import com.conveyal.otpac.PointSetDatastore;
import com.vividsolutions.jts.index.strtree.STRtree;

import org.opentripplanner.routing.graph.Graph;

import play.Logger;
import play.Play;
import play.libs.Akka;
import play.libs.F.Function0;
import play.libs.F.Promise;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import utils.Bounds;
import utils.ClassLoaderSerializer;
import utils.DataStore;
import utils.HashUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.io.ByteStreams;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

import controllers.Api;
import controllers.Application;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Scenario implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static ClusterGraphService clusterGraphService;
	
	static {
		String s3credentials = Play.application().configuration().getString("cluster.s3credentials");
		String bucket = Play.application().configuration().getString("cluster.graphs-bucket");
		boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline");
		clusterGraphService = new ClusterGraphService(s3credentials, workOffline, bucket);
	}
	

	static DataStore<Scenario> scenarioData = new DataStore<Scenario>("scenario", true);
	
	// a db to hold the shapes. uses tuple indexing, so we can't use datastore
	static DB segmentsDb = DBMaker.newFileDB(new File(Application.dataPath, "scenario_shapes.db"))
				.cacheSize(2000)
				.make();
	
	// the long is just to differentiate entries . . . this should really be a multimap
	static BTreeMap<Tuple2<String, Long>, TransitSegment> segments = segmentsDb.createTreeMap("segments").valueSerializer(new ClassLoaderSerializer()).makeOrGet();
	static Atomic.Long nextSegmentId = segmentsDb.getAtomicLong("segmentId");

	public String id;
	public String projectId;
	public String name;
	public String description;
	
	public String timeZone;
	
	public Boolean processingGtfs = false;
	public Boolean processingOsm = false;
	public Boolean failed = false;
	
	public Bounds bounds;
	
	/** spatial index of transit layer. */
	// TODO: this can be large, and large numbers of scenarios can be cached . . . perhaps use a SoftReference?
	private transient STRtree spIdx;
	
	@JsonIgnore
	public Collection<TransitSegment> getSegments () {
		return segments.subMap(new Tuple2(this.id, null), new Tuple2(this.id, Fun.HI)).values();
	}
	
	@JsonIgnore
	public STRtree getSpatialIndex () {
		if (spIdx == null) {
			buildSpatialIndexIfNeeded();
		}
		
		return spIdx;
	}
	
	/** build the spatial index */
	private synchronized void buildSpatialIndexIfNeeded () {
		if (spIdx != null)
			return;
		
		Collection<TransitSegment> shapes = getSegments();
		
		spIdx = new STRtree(Math.max(shapes.size(), 2));
		
		for (TransitSegment seg : shapes) {
			spIdx.insert(seg.geom.getEnvelopeInternal(), seg);
		}
	}
	
	public Scenario() {}
		
	public String getStatus() {
		
		if(processingGtfs)
			return "PROCESSSING_GTFS";
		else if(processingOsm) 
			return "PROCESSSING_OSM";
		else 
			return "BUILT";
		
	}
	
	static public Scenario create(final File gtfsFile, final String scenarioType, final String augmentScenarioId) throws IOException {
		
		final Scenario scenario = new Scenario();
		scenario.save();
		
		scenario.processGtfs(gtfsFile, scenarioType, augmentScenarioId);
		
		return scenario;
	}
	
	public List<String> getFiles() {
		
		ArrayList<String> files = new ArrayList<String>();
		
		for(File f : this.getScenarioDataPath().listFiles()) {
			files.add(f.getName());
		}
		
		return files;	
	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("sc_" + d.toString());
			
			Logger.info("created scenario s " + id);
		}
		
		scenarioData.save(id, this);
		
		Logger.info("saved scenario s " +id);
	}
	
	@JsonIgnore
	private static File getScenarioDir() {
		File scenarioPath = new File(Application.dataPath, "graphs");
		
		scenarioPath.mkdirs();
		
		return scenarioPath;
	}
	
	@JsonIgnore
	public File getScenarioDataPath() {
		
		File scenarioDataPath = new File(getScenarioDir(), id);
		
		scenarioDataPath.mkdirs();
		
		return scenarioDataPath;
	}
	
	public void delete() throws IOException {
		scenarioData.delete(id);
		
		FileUtils.deleteDirectory(getScenarioDataPath());
		
		Logger.info("delete scenario s" +id);
	}
	
	public void processGtfs(final File gtfsFile, final String scenarioType, final String augmentScenarioId) {
		ExecutionContext graphBuilderContext = Akka.system().dispatchers().lookup("contexts.graph-builder-analyst-context");
		
		final Scenario scenario = this;
		
		Akka.system().scheduler().scheduleOnce(
			        Duration.create(10, TimeUnit.MILLISECONDS),
			        new ProcessTransitScenarioJob(this, gtfsFile, scenarioType, augmentScenarioId),
			        graphBuilderContext
			);
	}
	
	public void writeToClusterCache () throws IOException {
		clusterGraphService.addGraphFile(getScenarioDataPath());
		
		// if the shapes are null, compute them.
		// They are built on upload, but older databases may not have them.
		if (this.getSegments().isEmpty() || this.timeZone == null) {
			processGtfs();
		}
	}
	
	public void processGtfs () {		
		Envelope2D envelope = new Envelope2D();

		for(File f : getScenarioDataPath().listFiles()) {			
			if(f.getName().toLowerCase().endsWith(".zip")) {
				final GTFSFeed feed = GTFSFeed.fromFile(f.getAbsolutePath());
				
				// this is not a gtfs feed
				if (feed.agency.isEmpty())
					continue;

				for(Stop s : feed.stops.values()) {
					envelope.include(s.stop_lon, s.stop_lat);
				}
				
				Agency a = feed.agency.values().iterator().next();
				this.timeZone = a.agency_timezone;
				
				// build the spatial index for the map view
				Collection<Trip> exemplarTrips =
						Collections2.transform(feed.findPatterns().values(), new Function<List<String>, Trip> () {
							public Trip apply(List<String> tripIds) {
								return feed.trips.get(tripIds.get(0));
							}
						});
				
				GeometryFactory gf = new GeometryFactory();
				
				for (Trip trip : exemplarTrips) {
					// if it has a shape, use that
					Coordinate[] coords;
					if (trip.shape_id != null) {
						Map<Tuple2<String, Integer>, Shape> shape = feed.shapePoints.subMap(new Tuple2(trip.shape_id, null), new Tuple2(trip.shape_id, Fun.HI));
						
						coords = new Coordinate[shape.size()];
						
						int i = 0;
						
						int lastKey = Integer.MIN_VALUE;
						for (Entry<Tuple2<String, Integer>, Shape> e : shape.entrySet()) {
							if (e.getKey().b < lastKey)
								throw new IllegalStateException("Non-sequential shape keys.");
							
							lastKey = e.getKey().b;
							
							coords[i++] = new Coordinate(e.getValue().shape_pt_lon, e.getValue().shape_pt_lat);
						}
					}
					else {
						Collection<StopTime> stopTimes = feed.stop_times.subMap(new Tuple2(trip.trip_id, null), new Tuple2(trip.trip_id, Fun.HI)).values();
						coords = new Coordinate[stopTimes.size()];
						int i = 0;
						for (StopTime st : stopTimes) {
							Stop stop = feed.stops.get(st.stop_id);
							coords[i++] = new Coordinate(stop.stop_lon, stop.stop_lat);
						}
					}
					
					if (coords.length < 2)
						continue;
					
					LineString geom = gf.createLineString(coords);
					TransitSegment seg = new TransitSegment(geom);
					
					this.segments.put(new Tuple2(this.id, this.nextSegmentId.getAndIncrement()), seg);
				}
			}
		}
		
		this.segmentsDb.commit();
		
		this.bounds = new Bounds(envelope);
		
		this.save();
	}
	
	static public void writeAllToClusterCache () throws IOException {
		for (Scenario s : scenarioData.getAll()) {
			s.writeToClusterCache();
		}
	}

	static public Scenario getScenario(String id) {
		
		return scenarioData.getById(id);	
	}
	
	static public Collection<Scenario> getScenarios(String projectId) throws IOException {
		
		if(projectId == null)
			return scenarioData.getAll();
		
		else {
			
			Collection<Scenario> data = new ArrayList<Scenario>();
			
			for(Scenario sd : scenarioData.getAll()) {
				if(sd.projectId == null )
					sd.delete();
				else if(sd.projectId.equals(projectId))
					data.add(sd);
			}
		
			return data;
		}
		
	}
	
	@JsonIgnore
	public File getTempShapeDirPath() {
		
		File shapeDirPath = new File(getScenarioDataPath(), "tmp_" + id);
		
		shapeDirPath.mkdirs();
		
		return shapeDirPath;
	}
	
	/** represents a transit line on the map */
	public static class TransitSegment implements Serializable {
		private static final long serialVersionUID = 1L;
		public LineString geom;
		
		public TransitSegment(LineString geom) {
			this.geom = geom;
		}
	}
	
}
