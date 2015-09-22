package models;

import com.conveyal.analyst.server.AnalystMain;
import com.conveyal.analyst.server.utils.DataStore;
import com.conveyal.analyst.server.utils.GeoUtils;
import com.conveyal.analyst.server.utils.HaltonPoints;
import com.conveyal.analyst.server.utils.PointSetDatastore;
import com.conveyal.data.geobuf.GeobufDecoder;
import com.conveyal.data.geobuf.GeobufFeature;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.prep.PreparedPolygon;
import com.vividsolutions.jts.index.strtree.STRtree;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.mapdb.Fun;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.EmptyPolygonException;
import org.opentripplanner.analyst.PointFeature;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.UnsupportedGeometryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Bounds;

import java.io.*;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * A Shapefile corresponds to an OTP PointSet. All numeric Shapefile columns are converted to pointset columns and accessibility values are calculated for each.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class Shapefile implements Serializable {
	private static final Logger LOG = LoggerFactory.getLogger(Shapefile.class);

	// this should remain constant unless we make a change where we explicitly want to break deserialization
	// so that users have to start fresh.
	private static final long serialVersionUID = 2L;
	
	private static PointSetDatastore datastore;
	
	static {
		String s3credentials = AnalystMain.config.getProperty("cluster.aws-credentials");
		String bucket = AnalystMain.config.getProperty("cluster.pointsets-bucket");
		boolean workOffline = Boolean.parseBoolean(
				AnalystMain.config.getProperty("cluster.work-offline"));
		
		datastore = new PointSetDatastore(10, s3credentials, workOffline, bucket);
	}

	@JsonIgnore
	static private DataStore<Shapefile> shapefilesData = new DataStore<Shapefile>("shapes", true);

	public String id;
	public String name;
	
	/**
	 * The name of this shapefile in the pointset. Don't change.
	 */
	public String categoryId;
	
	public String description;
	
	public String filename;
	
	public String type;

	public String projectId;
	
	public Bounds bounds;

	public Integer featureCount;

	@JsonIgnore
	public HashMap<String,Attribute> attributes = new HashMap<String,Attribute>();

	/** the pointset for this shapefile */
	@VisibleForTesting
	transient SoftReference<PointSet> pointSet;

	@JsonIgnore
	public File file;

	@JsonIgnore
	transient private DataStore<ShapeFeature> shapeFeatures;

	@JsonIgnore
	transient private SoftReference<STRtree> spatialIndex;

	public Shapefile() {
		
	}
	static public class ShapeFeature implements Serializable, Comparable<ShapeFeature> {

		private static final long serialVersionUID = 1L;
		public String id;
		public Geometry geom;

		@JsonIgnore
		transient private List<PreparedPolygon> preparedPolygons;

		@JsonIgnore
		transient private Map<String,HaltonPoints> haltonPointMap;

		@JsonIgnore
		public List<PreparedPolygon> getPreparedPolygons() {

			if(preparedPolygons == null) {
				preparedPolygons = new ArrayList<PreparedPolygon>();

				if(geom instanceof Polygon) {

					PreparedPolygon pp = new PreparedPolygon((Polygon)geom);
					preparedPolygons.add(pp);

				}
				else if(geom instanceof MultiPolygon) {

					for(int i = 0; i < ((MultiPolygon)geom).getNumGeometries(); i++) {
						Polygon p = (Polygon)((MultiPolygon)geom).getGeometryN(i);

						PreparedPolygon pp = new PreparedPolygon(p);
						preparedPolygons.add(pp);
					}

				}
			}

			return preparedPolygons;
		}

		@JsonIgnore
		public HaltonPoints getHaltonPoints(String attributeId) {
			if(haltonPointMap == null)
				haltonPointMap = new ConcurrentHashMap<String,HaltonPoints>();

			if(!haltonPointMap.containsKey(attributeId)) {
				HaltonPoints hp;
				if(attributes.containsKey(attributeId))
					hp = new HaltonPoints(geom, (Integer)attributes.get(attributeId));
				else
					hp = new HaltonPoints(geom, 0);

				haltonPointMap.put(attributeId, hp);
			}

			return haltonPointMap.get(attributeId);

		}

		@JsonIgnore
		public Integer getAttribute(String attributeId) {
			// TODO: don't assume everything is numeric, and don't cast everything to int (bad for e.g. synthetic populations with fractional people)
			if(attributes.containsKey(attributeId) && attributes.get(attributeId) instanceof Number)
				return ((Number) attributes.get(attributeId)).intValue();
			else
				return 0;
		}

		@JsonIgnore
		public Long getAttributeSum(List<String> attributeIds) {

			Long sum = 0l;

			for(String attributeId : attributeIds) {
				if(attributes.containsKey(attributeId))
					sum += (Integer)attributes.get(attributeId);
			}

			return sum;

		}

		public Map<String,Object> attributes = new HashMap<String,Object>();

		@Override
		public int compareTo(ShapeFeature o) {
			return this.id.compareTo(o.id);
		}
	}

	public static class FeatureTime {

		public ShapeFeature feature;
		public Long time;

		public FeatureTime(ShapeFeature sf, Long t) {
			feature = sf;
			time = t;
		}
	}
	
	@JsonIgnore
	public synchronized STRtree getSpatialIndex() {
		STRtree spatialIndex = this.spatialIndex != null ? this.spatialIndex.get() : null;

		if (spatialIndex == null) {
			spatialIndex = buildIndex ();
			this.spatialIndex = new SoftReference<STRtree>(spatialIndex);
		}

		return spatialIndex;
	}

	/**
	 * Get the pointset.
	 */
	@JsonIgnore
	public synchronized PointSet getPointSet() {
		PointSet pointSet = this.pointSet != null ? this.pointSet.get() : null;

		if (pointSet != null)
			return pointSet;

		pointSet = pointSetFromIterator(this.getShapeFeatureStore().getAll().iterator());
		this.pointSet = new SoftReference<>(pointSet);
		return pointSet;
	}

	/**
	 * Create a pointset from an iterator. This uses MapDB when the shapefile has already been created, but
	 * is also used on already in-memory features when loading a new shapefile. This avoids the relatively slow
	 * deserialization of every MapDB shape feature.
	 */
	private PointSet pointSetFromIterator (Iterator<ShapeFeature> iterator) {
		PointSet pointSet = new PointSet(getFeatureCount());

		pointSet.id = categoryId;
		pointSet.label = this.name;
		pointSet.description = this.description;

		int index = 0;
		while (iterator.hasNext()) {
			ShapeFeature sf = iterator.next();

			HashMap<String,Integer> propertyData = new HashMap<String,Integer>();

			for (Attribute a : this.attributes.values()) {
				String propertyId = categoryId + "." + a.fieldName;
				propertyData.put(propertyId, sf.getAttribute(a.fieldName));
				// TODO: update names when attribute name is edited.
				pointSet.setLabel(propertyId, a.name);
			}


			PointFeature pf;
			try {
				pf = new PointFeature(sf.id.toString(), sf.geom, propertyData);
				pointSet.addFeature(pf, index);
			} catch (EmptyPolygonException | UnsupportedGeometryException e) {
				LOG.warn("Invalid/unsupported geometry", e);
			}


			index++;
		}

		pointSet.setLabel(categoryId, this.name);

		return pointSet;
	}

	/**
	 * Write the shapefile to the cluster cache and to S3.
	 */
	public String writeToClusterCache() throws IOException {
		if (datastore.isCached(id))
			return id;

		PointSet ps = this.getPointSet();

		File f = File.createTempFile(id, ".json");

		FileOutputStream fos = new FileOutputStream(f);
		ps.writeJson(fos, true);
		fos.close();

		datastore.addPointSet(f, id);

		f.delete();

		return id;

	}



	@JsonIgnore
	public void setShapeFeatureStore(List<Fun.Tuple2<String,ShapeFeature>> features) {

		shapeFeatures = new DataStore<ShapeFeature>(getShapeDataPath(), id, features);

	}

	@JsonIgnore
	public DataStore<ShapeFeature> getShapeFeatureStore() {

		if(shapeFeatures == null){
			shapeFeatures = new DataStore<ShapeFeature>(getShapeDataPath(), id, true, true, true);
		}

		return shapeFeatures;

	}

	@JsonIgnore
	private static File getShapeDataPath() {
		File shapeDataPath = new File(AnalystMain.config.getProperty("application.data"), "shape_data");

		shapeDataPath.mkdirs();

		return shapeDataPath;
	}

	@JsonIgnore
	private File getTempShapeDirPath() {

		File shapeDirPath = new File(getShapeDataPath(), "tmp_" + id);

		shapeDirPath.mkdirs();

		return shapeDirPath;
	}

	public Integer getFeatureCount() {
		if (featureCount == null || featureCount.equals(0)) {
			featureCount = getShapeFeatureStore().size();
			save();
		}

		return featureCount;
	}

	private STRtree buildIndex() {
		LOG.info("building index for shapefile " + this.id);

		// it's not possible to make an R-tree with only one node, so we make an r-tree with two
		// nodes and leave one empty.
		STRtree spatialIndex = new STRtree(Math.max(getShapeFeatureStore().size(), 2));

		for(ShapeFeature feature : getShapeFeatureStore().getAll()) {
			spatialIndex.insert(feature.geom.getEnvelopeInternal(), feature);
		}

		return spatialIndex;
	}

	public List<ShapeFeature> query(Envelope env) {

		return getSpatialIndex().query(env);

	}
	
	public List<Attribute> getShapeAttributes() {
		return new ArrayList(attributes.values());
	}

	public void setShapeAttributes(List<Attribute> shapeAttributes) {
		for(Attribute a : shapeAttributes) {
			this.attributes.put(a.fieldName, a);
		}
	}
	
	public Collection<ShapeFeature> queryAll() {

		return shapeFeatures.getAll();

	}

	/**
	 * Create a new shapefile with the given name.
	 */
	public static Shapefile create(File originalShapefileZip, String projectId, String name) throws ZipException, IOException {

		String shapefileId = UUID.randomUUID().toString();

		LOG.info("creating shapefile " + shapefileId);

		Shapefile shapefile = new Shapefile();

		shapefile.id = shapefileId;
		shapefile.projectId = projectId;
		
		shapefile.name = name;
		shapefile.categoryId = Attribute.convertNameToId(name);


		ZipFile zipFile = new ZipFile(originalShapefileZip);

	    Enumeration<? extends ZipEntry> entries = zipFile.entries();

	    Boolean hasShp = false;
	    Boolean hasDbf = false;

	    while(entries.hasMoreElements()) {

	        ZipEntry entry = entries.nextElement();

	        if (entry.getName().startsWith("__MACOSX"))
	        	continue;

	        if(entry.getName().toLowerCase().endsWith("shp")){
	   	        hasShp = true;
	   	        shapefile.filename = entry.getName();
	        }
	   	    if(entry.getName().toLowerCase().endsWith("dbf"))
	        	hasDbf = true;
	    }

	    zipFile.close();

	    if(hasShp && hasDbf) {
	    	// move shape to perm location

	    	shapefile.file = new File(Shapefile.getShapeDataPath(),  shapefileId + ".zip");
	    	FileUtils.copyFile(originalShapefileZip, shapefile.file);

	    	LOG.info("loading shapefile " + shapefileId);
	    	List<Fun.Tuple2<String,ShapeFeature>> features = shapefile.getShapeFeatures();
	    	LOG.info("saving " + features.size() + " features...");

			// we have a sorted list but it needs to be reverse-sorted for MapDB.
	    	shapefile.setShapeFeatureStore(Lists.reverse(features));

	    	shapefile.save();

			// NB using forward-sorted iterator here not the reverse-sorted iterator that is used in the MapDB
			// so that the pointset created from this iterator has features in the same order as a pointset created
			// from MapDB.
			Iterator<ShapeFeature> featureIterator = features.stream()
					.map(f -> f.b)
					.iterator();

			shapefile.pointSet = new SoftReference<>(shapefile.pointSetFromIterator(featureIterator));
	    		    	
	    	LOG.info("done loading shapefile " + shapefileId);
	    }
	    else
	    	shapefile = null;

	    originalShapefileZip.delete();

		return shapefile;
	}

	/** Create shapefile from a Geobuf file */
	public static Shapefile createFromGeobuf (File geobuf, String projectId, String name) throws IOException {
		Shapefile shapefile = new Shapefile();
		shapefile.id = UUID.randomUUID().toString().replaceFirst("-", "");
		shapefile.projectId = projectId;
		shapefile.name = name;

		DataStore<ShapeFeature> featureStore = shapefile.getShapeFeatureStore();
		// load the features
		shapefile.name = name;
		shapefile.categoryId = Attribute.convertNameToId(name);

		// save file
		shapefile.file = new File(Shapefile.getShapeDataPath(),  shapefile.id + ".pbf");
		FileUtils.copyFile(geobuf, shapefile.file);

		FileInputStream fis = new FileInputStream(geobuf);
		GeobufDecoder decoder = new GeobufDecoder(fis);

		while (decoder.hasNext()) {
			GeobufFeature feature = decoder.next();
			ShapeFeature sf = new ShapeFeature();
			sf.attributes = feature.properties;
			sf.id = "" + feature.numericId;
			// GeoBuf files generally come from the Census, so one would hope they are already valid. But it won't hurt to
			// double-check.
			sf.geom = GeoUtils.makeValid(feature.geometry);
			featureStore.saveWithoutCommit(sf.id, sf);

			for (Map.Entry<String, Object> prop : feature.properties.entrySet()) {
				shapefile.updateAttributeStats(prop.getKey(), prop.getValue());
			}
		}

 		featureStore.commit();
		shapefile.save();
		return shapefile;
	}
	
	
	public void updateAttributeStats(String name, Object value) {
		
		Attribute attribute; 
		
		if(!attributes.containsKey(name)){
			attribute = new Attribute();
			attribute.name = name;
			attribute.fieldName = name;
			
			attributes.put(name, attribute);
		}
		else
			attribute = attributes.get(name);

		if (value != null && value instanceof Number)
			attribute.numeric = true;
		
		attribute.updateStats(value);
		 
	}

	private List<Fun.Tuple2<String,ShapeFeature>> getShapeFeatures() throws ZipException, IOException {

		List<Fun.Tuple2<String,ShapeFeature>> features = new ArrayList<Fun.Tuple2<String,ShapeFeature>>();

		File unzippedShapefile = getUnzippedShapefile();

		Map map = new HashMap();
		map.put( "url", unzippedShapefile.toURI().toURL() );

		org.geotools.data.DataStore dataStore = DataStoreFinder.getDataStore(map);

		SimpleFeatureSource featureSource = dataStore.getFeatureSource(dataStore.getTypeNames()[0]);

		SimpleFeatureType schema = featureSource.getSchema();

		CoordinateReferenceSystem shpCRS = schema.getCoordinateReferenceSystem();
		MathTransform transform;

		try {
			transform = CRS.findMathTransform(shpCRS, DefaultGeographicCRS.WGS84, true);
		} catch (FactoryException e1) {
			LOG.warn("Could not find projection for shapefile, returning unprojected file", e1);
			return features;
		}

		SimpleFeatureCollection collection = featureSource.getFeatures();
		SimpleFeatureIterator iterator = collection.features();

		int skippedFeatures = 0;

		Set<String> fieldnamesFound = new HashSet<String>();

		int featureId = 0;

		try {
			Envelope envelope = new Envelope();
			while( iterator.hasNext() ) {

				try {
					ShapeFeature feature = new ShapeFeature();

					SimpleFeature sFeature = iterator.next();

					// zero-pad feature ID so it is guaranteed to be ascending, so MapDB doesn't have to shuffle the
					// features around to store them.
					feature.id = String.format("%s_%9d", unzippedShapefile.getName().replace(".shp", ""), featureId++);
			    	feature.geom = JTS
							.transform((Geometry) sFeature.getDefaultGeometry(), transform);

					// make sure the geometry is valid
					// This correctly handles figure-8 polygons, overlapping holes, etc.
					feature.geom = GeoUtils.makeValid(feature.geom);

			    	envelope.expandToInclude(feature.geom.getEnvelopeInternal());
			    	
			    	this.type = feature.geom.getGeometryType().toLowerCase();
			    	
			        for(Object attr : sFeature.getProperties()) {
			        	if(attr instanceof Property) {
			        		Property p = ((Property)attr);
			        		String name = Attribute.convertNameToId(p.getName().toString());
			        		PropertyType pt = p.getType();
			        		Object value = p.getValue();
			        		
			        		updateAttributeStats(name, value);
			        		
			        		if(value != null && (value instanceof Long)) {
			        			feature.attributes.put(name, (int)(long)p.getValue());

			        			fieldnamesFound.add(name);

			        		} else if( value instanceof Integer) {
			        			feature.attributes.put(name, (int)p.getValue());

			        			fieldnamesFound.add(name);
			        		}
			        		else if(value != null && (value instanceof Double )) {
			        			feature.attributes.put(name, (int)(long)Math.round((Double)p.getValue()));

			        			fieldnamesFound.add(name);

			        		}
							else {
								feature.attributes.put(name, value != null ? value.toString() : null);
							}
			        	}
			        }
			    	features.add(new Fun.Tuple2<String,ShapeFeature>(feature.id, feature));
				}
				catch(Exception e) {
					skippedFeatures++;
					LOG.warn("Skipping invalid feature", e);
					continue;
				}
		     }
			
			this.bounds = new Bounds(envelope);
		}
		finally {
		     iterator.close();
		}

		dataStore.dispose();

		cleanupUnzippedShapefile();

		return features;
	}

	@JsonIgnore
	public File getUnzippedShapefile() throws ZipException, IOException {
		// unpack zip into temporary directory and return handle to *.shp file

		File outputDirectory = getTempShapeDirPath();

		ZipFile zipFile = new ZipFile(this.file);

		Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {

            ZipEntry entry = entries.nextElement();
            
            // ignore the funny Apple files
            if (entry.getName().toString().startsWith("__MACOSX"))
            	continue;
            
            File entryDestination = new File(outputDirectory,  entry.getName());

            entryDestination.getParentFile().mkdirs();

            if (entry.isDirectory())
                entryDestination.mkdirs();
            else {
                InputStream in = zipFile.getInputStream(entry);
                OutputStream out = new FileOutputStream(entryDestination);
                IOUtils.copy(in, out);
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }
        }

        for(File f : outputDirectory.listFiles()) {
        	if(f.getName().toLowerCase().endsWith(".shp"))
        		return f;
        }

        return null;
	}

	public void cleanupUnzippedShapefile() throws IOException {

		FileUtils.deleteDirectory(getTempShapeDirPath());

	}

	public void save() {
		shapefilesData.save(id, this);
		LOG.info("saved shapefile " +id);
	}

	public void delete() {
		shapefilesData.delete(id);

		if(file != null && file.exists())
			file.delete();

		try {
			cleanupUnzippedShapefile();
		} catch (IOException e) {
			LOG.error("unable delete shapefile p " +id, e);
		}

		LOG.info("delete shapefile p " +id);
	}

	static public Shapefile getShapefile(String id) {

		return shapefilesData.getById(id);
	}

	static public Collection<Shapefile> getShapefilesByProject(String projectId) {
		return shapefilesData.getAll().stream().filter(s -> projectId.equals(s.projectId))
				.collect(Collectors.toList());
	}

	public static Collection<Shapefile> getShapefiles () {
		return shapefilesData.getAll();
	}

	public static void writeAllToClusterCache() {
		for (Shapefile shapefile : shapefilesData.getAll()) {
			try {
				shapefile.writeToClusterCache();
			} catch (IOException e) {
				continue;
			}
		}
	}
}
