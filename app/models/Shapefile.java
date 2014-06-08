package models;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.analyst.core.Sample;

import play.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.strtree.STRtree;

import controllers.Api;
import controllers.Application;
import utils.DataStore;
import utils.HaltonPoints;
import utils.HashUtils;

public class Shapefile implements Serializable {
	
	private static final long serialVersionUID = 1L;

	@JsonIgnore
	static private DataStore<Shapefile> shapefilesData = new DataStore<Shapefile>("shapes");
	
	public String id;
	public String name;
	public String description;
	
	public ArrayList<String> fieldnames = new ArrayList<String>();
	
	
	@JsonIgnore
	public File file;

	@JsonIgnore
	transient private DataStore<ShapeFeature> shapeFeatures;
	
	@JsonIgnore 
	transient private STRtree spatialIndex;
	
	static public class ShapeFeature  implements Serializable {

		private static final long serialVersionUID = 1L;
		public String id;
		public Geometry geom;
		
		@JsonIgnore 
		transient private Map<String,HaltonPoints> haltonPointMap;
		
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
		transient private Map<String,Sample> graphSampleMap;
		
		@JsonIgnore
		public Sample getSampe(String graphId) {
			
			if(graphSampleMap == null)
				graphSampleMap = new ConcurrentHashMap<String,Sample>();
			
			if(!graphSampleMap.containsKey(graphId)) {
				Point p = geom.getCentroid();
				Sample s = Api.analyst.getSample(graphId, p.getX(), p.getY());
				
				if(s != null)
					graphSampleMap.put(graphId, s);
			}
			
			return graphSampleMap.get(graphId);
		}
		
		Map<String,Object> attributes = new HashMap<String,Object>();	
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
		if(spatialIndex == null)
			buildIndex();
		
		return spatialIndex;
	}
	
	@JsonIgnore
	public DataStore<ShapeFeature> getShapeFeatureStore() {
		
		if(shapeFeatures == null)
			shapeFeatures = new DataStore<ShapeFeature>(getShapeDataPath(), id);
		
		return shapeFeatures;
		
	}
	
	@JsonIgnore
	private static File getShapeDataPath() {
		File shapeDataPath = new File(Application.dataPath, "shape_data");
		
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
		return getShapeFeatureStore().size();
	}
	
	private void buildIndex() {
		Logger.info("building index for shapefile " + this.id);
	
		spatialIndex = new STRtree(getShapeFeatureStore().size());
		
		for(ShapeFeature feature : getShapeFeatureStore().getAll()) {
			spatialIndex.insert(feature.geom.getEnvelopeInternal(), feature);
		}
	}
	
	public List<ShapeFeature> query(Envelope env) {
		
		return getSpatialIndex().query(env);
		
	}
	
	public static Shapefile create(File originalShapefileZip) throws ZipException, IOException {
		
		String shapefileHash = HashUtils.hashFile(originalShapefileZip);
		
		if(shapefilesData.getById(shapefileHash) != null) {
			
			Logger.info("loading shapefile " + shapefileHash);
			
			originalShapefileZip.delete();
			return shapefilesData.getById(shapefileHash);
		}
		
		Logger.info("creating shapefile " + shapefileHash);
		
		Shapefile shapefile = new Shapefile();
		
		shapefile.id = shapefileHash;
		
		ZipFile zipFile = new ZipFile(originalShapefileZip);

	    Enumeration<? extends ZipEntry> entries = zipFile.entries();

	    Boolean hasShp = false;
	    Boolean hasDbf = false;
	    
	    while(entries.hasMoreElements()) {
	    	
	        ZipEntry entry = entries.nextElement();
	        
	        if(entry.getName().toLowerCase().endsWith("shp"))
	        	hasShp = true;
	        if(entry.getName().toLowerCase().endsWith("dbf"))
	        	hasDbf = true;
	    }
	    
	    zipFile.close();
	    
	    if(hasShp && hasDbf) {
	    	// move shape to perm location
	    	
	    	shapefile.file = new File(Shapefile.getShapeDataPath(),  shapefileHash + ".zip");
	    	FileUtils.copyFile(originalShapefileZip, shapefile.file);
	    
	    	shapefile.populateShapeData();
	    	

	    	shapefile.save();
	    }
	    else 
	    	shapefile = null;
	    
	    originalShapefileZip.delete();
	    
		return shapefile;
	}

	private void populateShapeData() throws ZipException, IOException {
		
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
			e1.printStackTrace();
			return;
		}

		SimpleFeatureCollection collection = featureSource.getFeatures();
		SimpleFeatureIterator iterator = collection.features();
		
		int skippedFeatures = 0;
		
		Set<String> fieldnamesFound = new HashSet<String>();
		
		try {
			while( iterator.hasNext() ) {
		         
				try {
					
					ShapeFeature feature = new ShapeFeature();
					
					SimpleFeature sFeature = iterator.next();
					
					feature.id = (String)sFeature.getID();	  
			    	feature.geom = JTS.transform((Geometry)sFeature.getDefaultGeometry(),  transform);  
			    	
			        for(Object attr : sFeature.getProperties()) {
			        	if(attr instanceof Property) {
			        		Property p = ((Property)attr);
			        		String name = p.getName().toString();
			        		PropertyType pt = p.getType();
			        		Object value = p.getValue();
			        		if(value != null && (value instanceof Long)) {
			        			feature.attributes.put(p.getName().toString(), (int)(long)p.getValue());
			        			
			        			fieldnamesFound.add(p.getName().toString());
			        			//Logger.info(name + " : " + value + " : " + value.getClass().getName());
			        		} else if( value instanceof Integer) {
			        			feature.attributes.put(p.getName().toString(), (int)p.getValue());

			        			fieldnamesFound.add(p.getName().toString());
			        		}
			        		else if(value != null && (value instanceof Double )) {
			        			feature.attributes.put(p.getName().toString(), (int)(long)Math.round((Double)p.getValue()));
			        		
			        			fieldnamesFound.add(p.getName().toString());
			        			//Logger.info(name + " : " + value + " : " + value.getClass().getName());
			        		}
			        	}
			        }
			        
			        Logger.info(feature.id);
			        
			        this.getShapeFeatureStore().save(feature.id, feature);
				}
				catch(Exception e) {
					skippedFeatures++;
					System.out.println(e.toString());
					continue;
				}
				
				
		     }
		}
		finally {
		     iterator.close();
		} 
		
		dataStore.dispose();
		
		fieldnames = new ArrayList<String>(fieldnamesFound);
		Collections.sort(fieldnames);
		
		cleanupUnzippedShapefile();
		
	}
	
	@JsonIgnore
	public File getUnzippedShapefile() throws ZipException, IOException {
		// unpack zip into temporary directory and return handle to *.shp file
		
		File outputDirectory = getTempShapeDirPath();
		
		ZipFile zipFile = new ZipFile(this.file);
    
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
    	
        while (entries.hasMoreElements()) {
        	
            ZipEntry entry = entries.nextElement();
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
		
		// assign id at save
		if(id == null || id.isEmpty()) {		
			Logger.info("created shapefile  " + id);
		}
		
		shapefilesData.save(id, this);
		
		Logger.info("saved shapefile " +id);
	}
	
	public void delete() {
		shapefilesData.delete(id);
		
		if(file != null && file.exists())
			file.delete();
		
		try {
			cleanupUnzippedShapefile();
		} catch (IOException e) {
			Logger.error("unable delete shapefile p " +id);
			e.printStackTrace();
		}
		
		Logger.info("delete shapefile p " +id);
	}

	static public Shapefile getShapefile(String id) {
		
		return shapefilesData.getById(id);	
	}
	
	static public Collection<Shapefile> getShapfiles() {
		
		return shapefilesData.getAll();
		
	}

}

