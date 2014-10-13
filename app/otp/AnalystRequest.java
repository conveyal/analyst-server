package otp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.core.Response;

import models.SpatialLayer;

import org.opentripplanner.analyst.ResultFeature;
import org.opentripplanner.analyst.ResultFeatureWithTimes;
import org.opentripplanner.analyst.SurfaceCache;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.spt.ShortestPathTree;

import com.google.common.collect.Maps;

import controllers.Api;

public class AnalystRequest extends RoutingRequest{
	
	private static final long serialVersionUID = 1L;

	private static SurfaceCache surfaceCache = new SurfaceCache(100);
	private static  Map<String, ResultFeature> resultCache = new ConcurrentHashMap<String, ResultFeature>();
	
	private static PrototypeAnalystRequest prototypeRequest = new PrototypeAnalystRequest();

	public int cutoffMinutes;
	
	static public AnalystRequest create(String graphId, GenericLocation latLon, int cutoffMinutes) throws IOException, NoSuchAlgorithmException {
		
		AnalystRequest request = new PrototypeAnalystRequest();
		
		request.cutoffMinutes = cutoffMinutes;
		request.routerId = graphId;
	
        if (request.arriveBy)
            request.to = latLon;
        else
            request.from = latLon;
        
		return request;
	}
	
	public TimeSurfaceShort createSurface() {
		
		EarliestArrivalSPTService sptService = new EarliestArrivalSPTService();
        sptService.maxDuration = 60 * cutoffMinutes;
        
        ShortestPathTree spt = sptService.getShortestPathTree(this);
       
        this.cleanup();
        
        if (spt != null) {
   
            TimeSurface surface = new TimeSurface(spt);
            
            surface.cutoffMinutes = cutoffMinutes;
            surfaceCache.add(surface);
            return new TimeSurfaceShort(surface);
        }
        
        return null;
		
	}
	
	public static ResultFeature getResult(Integer surfaceId, String pointSetId) {
		
		String resultId = "resultId_" + surfaceId + "_" + pointSetId;
    	
		ResultFeature result;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			result = resultCache.get(resultId);
        	else {
        		TimeSurface surf =getSurface(surfaceId);
        		result = new ResultFeature(SpatialLayer.getPointSetCategory(pointSetId).getPointSet().getSampleSet(surf.routerId), surf);;
        		resultCache.put(resultId, result);
        	}
    	}
    	
    	return result;
	}
	
	public static ResultFeatureWithTimes getResultWithTimes(Integer surfaceId, String pointSetId) {
		
		String resultId = "resultWIthTimesId_" + surfaceId + "_" + pointSetId;
    	
		ResultFeatureWithTimes resultWithTimes;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			resultWithTimes = (ResultFeatureWithTimes)resultCache.get(resultId);
        	else {
        		TimeSurface surf =getSurface(surfaceId);
        		resultWithTimes = new ResultFeatureWithTimes(SpatialLayer.getPointSetCategory(pointSetId).getPointSet().getSampleSet(surf.routerId), surf);
        		resultCache.put(resultId, resultWithTimes);
        	}
    	}
    	
    	return resultWithTimes;
	}
	
	public static TimeSurface getSurface(Integer surfaceId) {
		return surfaceCache.get(surfaceId);
	}
}
