package otp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import models.Shapefile;

import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.ResultSetWithTimes;
import org.opentripplanner.analyst.SampleSet;
import org.opentripplanner.analyst.SurfaceCache;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.EarliestArrivalSearch;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.spt.ShortestPathTree;

import controllers.Api;

public class AnalystRequest extends RoutingRequest{
	
	private static final long serialVersionUID = 1L;

	private static SurfaceCache surfaceCache = new SurfaceCache(100);
	private static  Map<String, ResultSet> resultCache = new ConcurrentHashMap<String, ResultSet>();
	
	private static PrototypeAnalystRequest prototypeRequest = new PrototypeAnalystRequest();

	public int cutoffMinutes;
	
	static public AnalystRequest create(String graphId, GenericLocation latLon, int cutoffMinutes)  {
		
		AnalystRequest request = new PrototypeAnalystRequest();
		
		request.cutoffMinutes = cutoffMinutes;
		request.routerId = graphId;
	
        if (request.arriveBy)
            request.to = latLon;
        else
            request.from = latLon;
        
		return request;
	}
	
	public static TimeSurfaceShort createSurface(RoutingRequest req, int cutoffMinutes) {
		
		EarliestArrivalSearch sptService = new EarliestArrivalSearch();
        sptService.maxDuration = 60 * cutoffMinutes;
        
        ShortestPathTree spt = sptService.getShortestPathTree(req);
       
        req.cleanup();
        
        if (spt != null) {
   
            TimeSurface surface = new TimeSurface(spt);
            
            surface.cutoffMinutes = cutoffMinutes;
            surfaceCache.add(surface);
            return new TimeSurfaceShort(surface);
        }
        
        return null;
		
	}
	
	public static ResultSet getResult(Integer surfaceId, String shapefileId) {
		
		String resultId = "resultId_" + surfaceId + "_" + shapefileId;
    	
		ResultSet result;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			result = resultCache.get(resultId);
        	else {
        		TimeSurface surf =getSurface(surfaceId);
        		PointSet ps = Shapefile.getShapefile(shapefileId).getPointSet();
        		SampleSet ss = ps.getSampleSet(Api.analyst.getGraph(surf.routerId));
        		result = new ResultSet(ss, surf);
        		resultCache.put(resultId, result);
        	}
    	}
    	
    	return result;
	}
	
	public static ResultSetWithTimes getResultWithTimes(Integer surfaceId, String shapefileId) {
		
		String resultId = "resultWithTimesId_" + surfaceId + "_" + shapefileId;
    	
		ResultSetWithTimes resultWithTimes;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			resultWithTimes = (ResultSetWithTimes)resultCache.get(resultId);
        	else {
        		TimeSurface surf =getSurface(surfaceId);
        		PointSet ps = Shapefile.getShapefile(shapefileId).getPointSet();
        		SampleSet ss = ps.getSampleSet(Api.analyst.getGraph(surf.routerId));
        		resultWithTimes = new ResultSetWithTimes(ss, surf);
        		resultCache.put(resultId, resultWithTimes);
        	}
    	}
    	
    	return resultWithTimes;
	}
	
	public static TimeSurface getSurface(Integer surfaceId) {
		return surfaceCache.get(surfaceId);
	}
}
