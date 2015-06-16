package models;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.conveyal.otpac.ClusterGraphService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Collections2;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.index.strtree.STRtree;
import controllers.Application;
import jersey.repackaged.com.google.common.collect.Lists;
import jobs.ProcessTransitBundleJob;
import org.apache.commons.io.FileUtils;
import org.geotools.geometry.Envelope2D;
import org.mapdb.*;
import org.mapdb.Fun.Tuple2;
import play.Logger;
import play.Play;
import play.libs.Akka;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import utils.Bounds;
import utils.ClassLoaderSerializer;
import utils.DataStore;
import utils.HashUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;


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
		String s3credentials = Play.application().configuration().getString("cluster.aws-credentials");
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
	
	/** What is the earliest date all feeds cover in this bundle? */
	public LocalDate startDate;
	
	/** What is the latest date all feeds cover in this bundle? */
	public LocalDate endDate;
	
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
		if ((this.getSegments().isEmpty() || this.timeZone == null || this.startDate == null || this.endDate == null) && this.failed != null && !this.failed) {
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


				// figure out the service range
				LocalDate start = null, end = null;

				for (Service c : feed.services.values()) {
					if (c.calendar != null) {
						LocalDate cs = fromInt(c.calendar.start_date);
						LocalDate ce = fromInt(c.calendar.end_date);

						if (start == null || cs.isBefore(start))
							start = cs;

						if (end == null || ce.isAfter(end))
							end = ce;
					}
					
					for (CalendarDate cd : c.calendar_dates.values()) {
						if (cd.exception_type == 2)
							// removed service, does not count
							continue;

						LocalDate ld = LocalDate.of(cd.date.getYear(), cd.date.getMonthOfYear(), cd.date.getMonthOfYear());

						if (start == null || ld.isBefore(start))
							start = ld;

						if (end == null || ld.isAfter(end))
							end = ld;
					}
				}

				if (start != null && end != null) {
					// we want the dates when *all* feeds are active
					if (this.startDate == null || start.isAfter(this.startDate))
						this.startDate = start;

					if (this.endDate == null || end.isBefore(this.endDate))
						this.endDate = end;
				}

				Agency a = feed.agency.values().iterator().next();
				this.timeZone = a.agency_timezone;
				
				// cache the routes
				for (Route route : feed.routes.values()) {
					this.routes.add(new RouteSummary(route, f.getName()));					
				}
				
				// build the spatial index for the map view
				Collection<Trip> exemplarTrips =
						Collections2.transform(feed.findPatterns().values(), tripIds -> feed.trips.get(tripIds.get(0)));
				
				GeometryFactory gf = new GeometryFactory();
				
				for (Trip trip : exemplarTrips) {
					// if it has a shape, use that
					Coordinate[] coords;
					if (trip.shape_id != null) {
						Map<Tuple2<String, Integer>, Shape> shape = feed.shapePoints.subMap(new Tuple2(trip.shape_id, null), new Tuple2(trip.shape_id, Fun.HI));
						
						coords = new Coordinate[shape.size()];
						
						int i = 0;
						
						int lastKey = Integer.MIN_VALUE;
						for (Map.Entry<Tuple2<String, Integer>, Shape> e : shape.entrySet()) {
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
		if (this.timeZone == null || this.startDate == null || this.endDate == null)
			this.failed = true;
		else
			this.bounds = new Bounds(envelope);
		
		this.segmentsDb.commit();
		
		this.save();
	}

	static public LocalDate fromInt (int date) {
		int year = date / 10000;
		int month = (date % 10000) / 100;
		int day = date % 100;
		return LocalDate.of(year, month, day);
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
		
		/** Constructor for JSON deserialization, etc. */
		public RouteSummary() { }
		
		public String shortName;
		public String longName;
		public String id;
		public String agencyId;
		public String feed;
	}

	/** If there are no bundles, try to import bundles from the legacy scenario data store */
	@SuppressWarnings("deprecation")
	public static void importBundlesAsNeeded() {
		if (!bundleData.isEmpty())
			return;
		
		DataStore<Scenario> scenarioStore = new DataStore<Scenario>("scenario", true);
		
		if (scenarioStore.isEmpty()) {
			scenarioStore.close();
			return;
		}
		
		Logger.info("Importing legacy scenarios . . .");
		
		int count = 0;
		
		for (Scenario s : scenarioStore.getAll()) {
			Bundle b = new Bundle();
			b.id = s.id;
			b.description = s.description;
			b.name = s.name;
			b.projectId = s.projectId;
			b.processGtfs();
			b.save();
			
			// create a same-named scenario so that things are transparent (more or less) from the user perspective
			TransportScenario ts = TransportScenario.create(b);
			ts.name = b.name;
			ts.description = b.description;
			ts.save();
			count++;
		}
	
		Logger.info("Imported {} scenarios", count);
	}
}
