package otp;


import java.util.HashMap;
import java.util.Map;

import models.Shapefile;
import models.Shapefile.ShapeFeature;
import models.SpatialDataSet;

import org.opentripplanner.analyst.core.Sample;
import org.opentripplanner.common.geometry.AccumulativeGridSampler;
import org.opentripplanner.common.geometry.AccumulativeGridSampler.AccumulativeMetric;
import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.common.geometry.ZSampleGrid;
import org.opentripplanner.common.geometry.DistanceLibrary;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.spt.SPTWalker;
import org.opentripplanner.routing.spt.SPTWalker.SPTVisitor;
import org.opentripplanner.routing.spt.ShortestPathTree;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Coordinate;

import controllers.Api;

public class SptResponse {

	static private EarliestArrivalSPTService sptService = new EarliestArrivalSPTService();

	// TODO develop a cache eviction strategy -- this will run out of memory as it is used
	static private  Map<String, Map<String,SptResponse>> sptCache = new HashMap<String, Map<String,SptResponse>>();

	public String sptId;
	public String featureFileId;
	
	private AnalystRequest req;
	
	@JsonIgnore
	public HashMap<String,Long> destinationTimes = new HashMap<String,Long>();
	
	public static SptResponse getResponse(String sptId, String spatialId) {
		
		SpatialDataSet sd = SpatialDataSet.getSpatialDataSet(spatialId);
		
		String featureFileId = sd.shapefileid;
		
		if(sptCache.containsKey(sptId)) {
			if(sptCache.get(sptId).containsKey(featureFileId)) {
				return sptCache.get(sptId).get(featureFileId);
			}
		}
		
		return null;
		
	}
	
	public static SptResponse create(AnalystRequest r, String spatialId) {

		SpatialDataSet sd = SpatialDataSet.getSpatialDataSet(spatialId);
		
		String featureFileId = sd.shapefileid;
		
		if(sptCache.containsKey(r.sptId)) {
			if(sptCache.get(r.sptId).containsKey(featureFileId)) {
				return sptCache.get(r.sptId).get(featureFileId);
			}
		}
		else 
			sptCache.put(r.sptId, new HashMap<String,SptResponse>());
		
		
		SptResponse sptResponse = new SptResponse();
		
		sptResponse.sptId = r.sptId;
		sptResponse.req = r;
		sptResponse.featureFileId = featureFileId;
		
		final ShortestPathTree spt = sptService.getShortestPathTree(sptResponse.req);
		sptResponse.req.cleanup();
		
		sptResponse.calcSamples(spt);
		
		sptCache.get(sptResponse.req.sptId).put(featureFileId, sptResponse);
		
		return sptResponse;
	}
	
	public long evaluateSample(ShortestPathTree spt, Sample s) {
		return s.eval(spt);
	}
	
	
	public void calcSamples(ShortestPathTree spt) {
		
		Shapefile shapefile = Shapefile.getShapefile(featureFileId);
		
		for(ShapeFeature sf: shapefile.getShapeFeatureStore().getAll()) {
			Sample s = sf.getSampe(req.graphId);
			if(s != null) {
				long time = evaluateSample(spt, s);
				if(time <= Api.maxTimeLimit) {
					destinationTimes.put(sf.id, time);
				}
			}
		}
	}
}