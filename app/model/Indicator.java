package model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import java.util.List;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opentripplanner.analyst.core.Sample;

import otp.Blocks;
import otp.SampleFactory;

import java.util.Collections;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.index.strtree.STRtree;


public class Indicator {

	public String id;
	public String name;
	
	public List<AttributeGroup> attributes;
	
	public Collection<IndicatorItem> data;
	
	public HashMap<String, IndicatorItem> idIndex = new HashMap<String, IndicatorItem>();
	

	public STRtree spatialIndex = new STRtree();
	
	public Indicator() {
	 
	}
	
	public void index(SampleFactory sampleSource, Blocks blockData) {

	
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		
		spatialIndex = new STRtree(data.size());

		Collections.sort(attributes);

		
		for(IndicatorItem item : data) {
		
			Coordinate coord = new Coordinate(item.lon, item.lat);
			item.sample =  sampleSource.getSample(item.lon, item.lat);
			item.point = geometryFactory.createPoint(coord);

		
			item.block = blockData.blocks.get(item.geoId);
			
			idIndex.put(item.geoId, item);
			
			if(item.block == null)
				spatialIndex.insert(item.point.getEnvelopeInternal(), item);
			else
				spatialIndex.insert(item.block.getEnvelopeInternal(), item);

			
		}
	}
	
	public List<IndicatorItem> query(Envelope env) {
		
		return spatialIndex.query(env);
		
	}
		
	public List<IndicatorItem> queryAll() {
		
		return new ArrayList<IndicatorItem>(data);
		
	}
}
