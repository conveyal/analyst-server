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

import jersey.repackaged.com.google.common.collect.Lists;
import jobs.ProcessTransitBundleJob;

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
import com.conveyal.gtfs.model.Route;
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
/**
 * This represents a bundle of transit data. It used to be known as a "scenario," but
 * scenarios now include modifications on top of bundles. The bundle simply keeps transit data
 * together; it is a quite heavy object and is expensive to create and store. Scenarios, on the
 * other hand, are extremely cheap and should be used with reckless abandon.
 */
public class Bundle implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static ClusterGraphService clusterGraphService;
	
	static {
		String s3credentials = Play.application().configuration().getString("cluster.s3credentials");
		String bucket = Play.application().configuration().getString("cluster.graphs-bucket");
		boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline");
		clusterGraphService = new ClusterGraphService(s3credentials, workOffline, bucket);
	}
	

	static DataStore<Bundle> bundleData = new DataStore<Bundle>("bundle", true);
	
	// a db to hold the shapes. uses tuple indexing, so we can't use datastore
	static DB segmentsDb = DBMaker.newFileDB(new File(Application.dataPath, "bundle_shapes.db"))
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
	
	public List<RouteSummary> routes = Lists.newArrayList();
	
	/** spatial index of transit layer. */
	// TODO: this can be large, and large numbers of bundles can be cached . . . perhaps use a SoftReference?
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
	
	public Bundle() {}
		
	public String getStatus() {
		
		if (failed)
			return "ERROR";
		else if(processingGtfs)
			return "PROCESSSING_GTFS";
		else if(processingOsm) 
			return "PROCESSSING_OSM";
		else 
			return "BUILT";
		
	}
	
	static public Bundle create(final File gtfsFile, final String bundleType, final String augmentBundleId) throws IOException {
		
		final Bundle bundle = new Bundle();
		bundle.save();
		
		bundle.processGtfs(gtfsFile, bundleType, augmentBundleId);
		
		return bundle;
	}
	
	public List<String> getFiles() {
		
		ArrayList<String> files = new ArrayList<String>();
		
		for(File f : this.getBundleDataPath().listFiles()) {
			files.add(f.getName());
		}
		
		return files;	
	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("b_" + d.toString());
			
			Logger.info("created bundle " + id);
		}
		
		bundleData.save(id, this);
		
		Logger.info("saved bundle " +id);
	}
	
	@JsonIgnore
	private static File getBundleDir() {
		File bundlePath = new File(Application.dataPath, "graphs");
		
		bundlePath.mkdirs();
		
		return bundlePath;
	}
	
	@JsonIgnore
	public File getBundleDataPath() {
		
		File bundleDataPath = new File(getBundleDir(), id);
		
		bundleDataPath.mkdirs();
		
		return bundleDataPath;
	}
	
	public void delete() throws IOException {
		bundleData.delete(id);
		
		FileUtils.deleteDirectory(getBundleDataPath());
		
		Logger.info("delete bundle s" +id);
	}
	
	public void processGtfs(final File gtfsFile, final String bundleType, final String augmentBundleId) {
		ExecutionContext graphBuilderContext = Akka.system().dispatchers().lookup("contexts.graph-builder-analyst-context");
		
		Akka.system().scheduler().scheduleOnce(
			        Duration.create(10, TimeUnit.MILLISECONDS),
			        new ProcessTransitBundleJob(this, gtfsFile, bundleType, augmentBundleId),
			        graphBuilderContext
			);
	}
	
	public void writeToClusterCache () throws IOException {
		clusterGraphService.addGraphFile(getBundleDataPath());
		
		// if the shapes are null, compute them.
		// They are built on upload, but older databases may not have them.
		// but don't rebuild failed uploads every time the server is started
		if ((this.getSegments().isEmpty() || this.timeZone == null) && this.failed != null && !this.failed) {
			processGtfs();
		}
	}
	
	public void processGtfs () {		
		Envelope2D envelope = new Envelope2D();

		for(File f : getBundleDataPath().listFiles()) {			
			if(f.getName().toLowerCase().endsWith(".zip")) {
				final GTFSFeed feed;
				
				Logger.info("Processing file " + f.getName());
				
				try {
					feed = GTFSFeed.fromFile(f.getAbsolutePath());
				} catch (RuntimeException e) {
					if (e.getCause() instanceof ZipException) {
						Logger.error("Unable to process GTFS file for bundle %s, project %s", this.name, Project.getProject(this.projectId).name);
						continue;
					}
					throw e;
				}
				
				// this is not a gtfs feed
				if (feed.agency.isEmpty())
					continue;

				for(Stop s : feed.stops.values()) {
					envelope.include(s.stop_lon, s.stop_lat);
				}
				
				Agency a = feed.agency.values().iterator().next();
				this.timeZone = a.agency_timezone;
				
				// cache the routes
				for (Route route : feed.routes.values()) {
					this.routes.add(new RouteSummary(route, f.getName()));					
				}
				
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
		
		// if we've done all that and we still don't have a time zone, then this was a failure
		if (this.timeZone == null)
			this.failed = true;
		else
			this.bounds = new Bounds(envelope);
		
		this.segmentsDb.commit();
		
		this.save();
	}
	
	static public void writeAllToClusterCache () throws IOException {
		for (Bundle s : bundleData.getAll()) {
			s.writeToClusterCache();
		}
	}

	static public Bundle getBundle(String id) {
		
		return bundleData.getById(id);	
	}
	
	static public Collection<Bundle> getBundles(String projectId) throws IOException {
		
		if(projectId == null)
			return bundleData.getAll();
		
		else {
			
			Collection<Bundle> data = new ArrayList<Bundle>();
			
			for(Bundle sd : bundleData.getAll()) {
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
		
		File shapeDirPath = new File(getBundleDataPath(), "tmp_" + id);
		
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
	
	/** Represents a single route (for use in banning) */
	public static class RouteSummary implements Serializable {
		private static final long serialVersionUID = 1L;
		
		/** Construct a route summary from a GTFS route and the name of the feed file from whence it came */
		public RouteSummary(Route route, String feedName) {
			this.shortName = route.route_short_name;
			this.longName = route.route_long_name;
			this.id = route.route_id;
			this.agencyId = route.agency.agency_id;
			this.feed = feedName;
		}
		
		public final String shortName;
		public final String longName;
		public final String id;
		public final String agencyId;
		public final String feed;
	}
	
}
