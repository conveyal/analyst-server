package models;

import com.conveyal.analyst.server.AnalystMain;
import com.conveyal.analyst.server.jobs.ProcessTransitBundleJob;
import com.conveyal.analyst.server.utils.DataStore;
import com.conveyal.analyst.server.utils.HashUtils;
import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.index.strtree.STRtree;
import jersey.repackaged.com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.geotools.geometry.Envelope2D;
import org.mapdb.*;
import org.mapdb.Fun.Tuple2;
import org.opentripplanner.analyst.cluster.ClusterGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Bounds;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
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

	private static final Logger LOG = LoggerFactory.getLogger(Bundle.class);
	
	private static ClusterGraphService clusterGraphService;
	
	static {
		String s3credentials = AnalystMain.config.getProperty("cluster.aws-credentials");
		String bucket = AnalystMain.config.getProperty("cluster.graphs-bucket");
		boolean workOffline = Boolean.parseBoolean(
				AnalystMain.config.getProperty("cluster.work-offline"));
		clusterGraphService = new ClusterGraphService(s3credentials, workOffline, bucket);
	}
	

	static DataStore<Bundle> bundleData = new DataStore<Bundle>("bundle", true);
	
	// a db to hold the shapes. uses tuple indexing, so we can't use datastore
	static DB segmentsDb = DBMaker.newFileDB(new File(AnalystMain.config.getProperty("application.data"), "bundle_shapes.db"))
				.cacheSize(2000)
				.asyncWriteEnable()
				.asyncWriteFlushDelay(1000)
				.make();
	
	// the long is just to differentiate entries . . . this should really be a multimap
	static BTreeMap<Tuple2<String, Long>, TransitSegment> segments = segmentsDb.createTreeMap("segments").valueSerializer(Serializer.JAVA).makeOrGet();
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
	public Boolean tooBig = false;
	
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
		if (this.spIdx != null)
			return;
		
		Collection<TransitSegment> shapes = getSegments();
		
		STRtree spIdx = new STRtree(Math.max(shapes.size(), 2));
		
		for (TransitSegment seg : shapes) {
			spIdx.insert(seg.geom.getEnvelopeInternal(), seg);
		}

		this.spIdx = spIdx;
	}
	
	public Bundle() {}
		
	public String getStatus() {

		if (tooBig != null && tooBig)
			return "EXTENT_TOO_LARGE";
		else if (failed)
			return "ERROR";
		else if(processingGtfs)
			return "PROCESSING_GTFS";
		else if(processingOsm) 
			return "PROCESSING_OSM";
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
			
			LOG.info("created bundle " + id);
		}
		
		bundleData.save(id, this);
		
		LOG.info("saved bundle " +id);
	}
	
	@JsonIgnore
	private static File getBundleDir() {
		File bundlePath = new File(AnalystMain.config.getProperty("application.data"), "graphs");
		
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
		
		LOG.info("delete bundle s" +id);
	}
	
	public void processGtfs(final File gtfsFile, final String bundleType, final String augmentBundleId) {
		new Thread(new ProcessTransitBundleJob(this, gtfsFile, bundleType, augmentBundleId)).start();
	}
	
	public void writeToClusterCache () throws IOException {
		clusterGraphService.addGraphFile(getBundleDataPath());
	}
	
	public void processGtfs () {		
		Envelope2D envelope = new Envelope2D();

		for(File f : getBundleDataPath().listFiles()) {			
			if(f.getName().toLowerCase().endsWith(".zip")) {
				GTFSFeed feed = null;

				try {
					LOG.info("Processing file " + f.getName());

					try {
						feed = GTFSFeed.fromFile(f.getAbsolutePath());
					} catch (RuntimeException e) {
						if (e.getCause() instanceof ZipException) {
							LOG.error("Unable to process GTFS file for bundle %s, project %s", this.name, Project.getProject(this.projectId).name);
							continue;
						}
						throw e;
					}

					// this is not a gtfs feed
					if (feed.agency.isEmpty())
						continue;

					// loop over stop times not stops to build the bounds, so that unused stops are ignored.
					for (StopTime st : feed.stop_times.values()) {
						Stop s = feed.stops.get(st.stop_id);

						if (s == null)
							// GTFS reader has already printed an error
							continue;

						// few agencies provide submarine service in the gulf of guinea, so these stops are almost
						// certainly errors.
						if (Math.abs(s.stop_lon) < 1 && Math.abs(s.stop_lat) < 1) {
							LOG.warn("Ignoring stop {}, it is in the Gulf of Guinea", s.stop_name);
							continue;
						}

						envelope.include(s.stop_lon, s.stop_lat);
					}

					if (envelope.getWidth() > 5 || envelope.getHeight() > 5) {
						LOG.warn("Envelope size for bundle {} is excessive, refusing to build. Check your GTFS?");
						this.failed = true;
						this.tooBig = true;
						return;
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
					final GTFSFeed feedFinal = feed;
					Collection<Trip> exemplarTrips = feed.findPatterns().values().stream()
							.map(tripIds -> feedFinal.trips.get(tripIds.get(0)))
							.collect(Collectors.toList());

					GeometryFactory gf = new GeometryFactory();

					for (Trip trip : exemplarTrips) {
						// if it has a shape, use that
						Coordinate[] coords;
						if (trip.shape_id != null) {
							Map<?, Shape> shape = feed.shapePoints.subMap(new Tuple2(trip.shape_id, null), new Tuple2(trip.shape_id, Fun.HI));

							coords = shape.values().stream()
									.map(s -> new Coordinate(s.shape_pt_lon, s.shape_pt_lon))
									.toArray(size -> new Coordinate[size]);
						} else {
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
				finally {
					if (feed != null) feed.close();
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
		// two pass loop to avoid concurrent modification
		List<String> bundlesToReprocess = new ArrayList<>();
		for (Map.Entry<String, Bundle> e : bundleData.getEntries()) {
			Bundle s = e.getValue();
			// if the shapes are null, compute them.
			// They are built on upload, but older databases may not have them.
			// but don't rebuild failed uploads every time the server is started
			if ((s.getSegments().isEmpty() || s.timeZone == null || s.startDate == null || s.endDate == null) &&
					s.failed != null && !s.failed) {
				LOG.warn("Marking bundle {} (map key: {}) for reprocessing", s.id, e.getKey());
				// this bundle needs to be reprocessed, but we can't do it here because it will cause issues with
				// concurrent modification.
				bundlesToReprocess.add(s.id);
			}

			try {
				// writing to the cluster cache just uploads the GTFS file, so even if the bundle is being reprocessed, it's fine
				s.writeToClusterCache();
			} catch (Exception ex) {
				LOG.error("Failed to write bundle {} to cluster cache", s, ex);
			}
		}

		for (String bundleId : bundlesToReprocess) {
			Bundle bundle = Bundle.getBundle(bundleId);
			bundle.processGtfs();
		}
	}

	static public Bundle getBundle(String id) {
		
		return bundleData.getById(id);	
	}
	
	static public Collection<Bundle> getBundlesByProject(String projectId) {
		return bundleData.getAll().stream().filter(b -> projectId.equals(b.projectId))
				.collect(Collectors.toList());
	}

	public static Collection<Bundle> getBundles () {
		return bundleData.getAll();
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

			// workaround for #118
			// OBA replaces agency ID with agency name, evidently, when there is no agency ID
			// There is a comment in the OTP source (GtfsModule.java):
			//  "TODO figure out how and why this is happening"
			if (this.agencyId == null) {
				this.agencyId = route.agency.agency_name;
			}


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
		
		LOG.info("Importing legacy scenarios . . .");
		
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
	
		LOG.info("Imported {} scenarios", count);
	}
}
