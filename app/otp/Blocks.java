package otp;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygonal;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedPolygon;

public class Blocks {

	public ConcurrentHashMap<String, Geometry> blocks = new ConcurrentHashMap<String, Geometry>();

	CoordinateReferenceSystem wgsCRS = DefaultGeographicCRS.WGS84;
	
	public Blocks() {
	
	}
	
	public void load(File blockShapefile)  {
		
		if(blockShapefile == null || !blockShapefile.exists())
			return;

		System.out.println("loading " + blockShapefile.getName());
		
		try {
			String current = new java.io.File( "." ).getCanonicalPath();
			System.out.println("block path: " + blockShapefile.toURI().toURL() );
			
			Map map = new HashMap();
			map.put( "url", blockShapefile.toURI().toURL() );
			
			DataStore dataStore = DataStoreFinder.getDataStore(map);
			
			SimpleFeatureSource featureSource = dataStore.getFeatureSource(dataStore.getTypeNames()[0]); 
			
			SimpleFeatureType schema = featureSource.getSchema();

			CoordinateReferenceSystem shpCRS = schema.getCoordinateReferenceSystem();
			MathTransform transform = CRS.findMathTransform(shpCRS, wgsCRS, true);

			SimpleFeatureCollection collection = featureSource.getFeatures();
			SimpleFeatureIterator iterator = collection.features();
			
			Integer skippedFeatures = 0;
			
			try {
				while( iterator.hasNext() ) {
			         
					try {
						
						SimpleFeature feature = iterator.next();
				    	String geoId = (String)feature.getAttribute("id");
				        	  
				        Geometry geom = JTS.transform((Geometry)feature.getDefaultGeometry(),  transform);  
				        
				        blocks.put(geoId, geom);
				 
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
		}
		catch(Exception e) {
			e.printStackTrace();
			System.out.println("failed to load " + blockShapefile.getName());
		}
		
		

	}
}
