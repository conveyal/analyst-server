package otp;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import models.SpatialLayer;

import org.opentripplanner.analyst.ResultFeature;
import org.opentripplanner.analyst.ResultFeatureWithTimes;
import org.opentripplanner.analyst.SurfaceCache;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.api.param.LatLon;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.profile.ProfileResponse;
import org.opentripplanner.profile.ProfileRouter;

import com.google.common.collect.Lists;

import controllers.Api;

public class AnalystProfileRequest extends ProfileRequest{
	
	private static final long serialVersionUID = 1L;

	private static SurfaceCache surfaceCache = new SurfaceCache(100);
	private static  Map<String, ResultFeature> resultCache = new ConcurrentHashMap<String, ResultFeature>();

	public int cutoffMinutes;
	public String graphId;
	
	static public AnalystProfileRequest create(String graphId, LatLon latLon, int cutoffMinutes) throws IOException, NoSuchAlgorithmException {
		
		AnalystProfileRequest request = new AnalystProfileRequest();
		
		request.analyst = true;
		request.cutoffMinutes = cutoffMinutes;
		request.graphId = graphId;
	
		request.to = latLon;
        
		return request;
	}
	
	public List<TimeSurfaceShort> createSurfaces() {
		
		ProfileRouter router = new ProfileRouter(Api.analyst.getGraph(graphId), this);
		
		List<TimeSurfaceShort> surfaceShorts = null;
		
        try {
        	ProfileResponse response = router.route();
        	surfaceShorts = Lists.newArrayList();
            surfaceShorts.add(new TimeSurfaceShort(router.minSurface));
            surfaceShorts.add(new TimeSurfaceShort(router.maxSurface));
  
        }
        catch (Exception e) {
        	e.printStackTrace();
        }
        finally {
            router.cleanup(); // destroy routing contexts even when an exception happens
        }
        
        return surfaceShorts;
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
