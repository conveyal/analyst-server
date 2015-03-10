package utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.PatternHop;
import org.opentripplanner.routing.edgetype.TripPattern;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.index.strtree.STRtree;

import controllers.Api;

public class TransportIndex {
	
	private Map<String, STRtree> indexMap = new ConcurrentHashMap<String,STRtree>();
	
	public class TransitSegment {
		final public String edgeId;
		public LineString geom;
		public TraverseMode mode;
		
		public TransitSegment(PatternHop ph) {
			GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory( null );
			
			this.edgeId = ph.getBeginStop().getId().toString() + "_" + ph.getEndStop().getId().toString();
			this.geom = geometryFactory.createLineString(ph.getGeometry().getCoordinates());
			this.mode = ph.getMode();
		}
		
		/*public CarSegment(PatternHop ph) {
			GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory( null );
			
			this.geom = geometryFactory.createLineString(ph.getGeometry().getCoordinates());
			this.mode = ph.getMode();
		}*/
	}
	
	public STRtree getIndexForGraph(String graphId) {
		return null;
		/*
		if(!indexMap.containsKey(graphId)) {
		
			List<TransitSegment> segments = new ArrayList<TransitSegment>();
			for(TripPattern tp : Api.analyst.getGraph(graphId).index.patternsForRoute.values()) {
				for(PatternHop ph : tp.getPatternHops()) {
					if(ph.getGeometry() != null && ph.getBeginStop() != null && ph.getEndStop() != null) {
						TransitSegment ts = new TransitSegment(ph);
						segments.add(ts);
					}
				}
			}
			
			// R-trees have to have a minimum of two nodes
			STRtree spatialIndex = new STRtree(Math.max(segments.size(), 2));
			
			for(TransitSegment ts : segments) {
				spatialIndex.insert(ts.geom.getEnvelopeInternal(), ts);
			}
			
			indexMap.put(graphId, spatialIndex);
		}
		
		return indexMap.get(graphId);*/
	}

}
