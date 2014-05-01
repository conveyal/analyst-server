package model;

import java.util.HashMap;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opentripplanner.analyst.core.Sample;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.Point;

public class IndicatorItem implements Comparable<IndicatorItem> {
	
	static GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

	public String geoId;
	
	public double lat;
	public double lon;
	
	public MultigraphSample samples;
	public Point point;
	
	public HashMap<String, Long> attributes  = new HashMap<String, Long>();
	
	public Geometry geom;

	public Geometry centroid;
	
	Double percentLand;
	
	
	public IndicatorItem() {
		 
	}
	
	public HaltonPoints getHaltonPoints(String attribute) {
		
		if(geom == null || !attributes.containsKey(attribute))
			return new HaltonPoints(geom, 0);
			
		// !!! need to handle large values more gracefully
		return new HaltonPoints(geom, (int)(long)attributes.get(attribute));
	}
	

	@Override
	public int compareTo(IndicatorItem o) {
		// TODO Auto-generated method stub
		return this.geoId.compareTo(o.geoId);
	}

}
