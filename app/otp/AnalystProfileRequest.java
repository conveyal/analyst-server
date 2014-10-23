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

	private static ProfileResultCache profileResultCache = new ProfileResultCache(100);

	private static  Map<String, ResultFeature> resultCache = new ConcurrentHashMap<String, ResultFeature>();

	public int cutoffMinutes;
	public String graphId;
	
	public TimeSurfaceShort createSurfaces() {
		
		ProfileRouter router = new ProfileRouter(Api.analyst.getGraph(graphId), this);
		
        try {
        	ProfileResponse response = router.route();
    
            router.minSurface.cutoffMinutes = cutoffMinutes;
            router.maxSurface.cutoffMinutes = cutoffMinutes;
            
            ProfileResult result = new ProfileResult(router.minSurface.id, router.minSurface, router.maxSurface);
            
            profileResultCache.add(result);

        }
        catch (Exception e) {
        	e.printStackTrace();
        }
        finally {
            router.cleanup(); // destroy routing contexts even when an exception happens
        }
        
        return new TimeSurfaceShort(router.minSurface);
  	}
	
	public static ResultFeature getResult(Integer surfaceId, String pointSetId, String show) {
		
		String resultId = "resultId_" + surfaceId + "_" + pointSetId + "_" + show;
    	
		ResultFeature result;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			result = resultCache.get(resultId);
        	else {
        		ProfileResult profileResult =getSurface(surfaceId);
        		
        		TimeSurface surf = null;
        		if(show.equals("min"))
        			surf = profileResult.min;
        		if(show.equals("max"))
        			surf = profileResult.max;
        		
        		result = new ResultFeature(SpatialLayer.getPointSetCategory(pointSetId).getPointSet().getSampleSet(surf.routerId), surf);;
        		resultCache.put(resultId, result);
        	}
    	}
    	
    	return result;
	}
	
	public static ResultFeatureWithTimes getResultWithTimes(Integer surfaceId, String pointSetId, String show) {
		
		String resultId = "resultWithTimesId_" + surfaceId + "_" + pointSetId + "_" + show;
    	
		ResultFeatureWithTimes resultWithTimes;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			resultWithTimes = (ResultFeatureWithTimes)resultCache.get(resultId);
        	else {
        		ProfileResult profileResult =getSurface(surfaceId);
        		
        		TimeSurface surf = null;
        		if(show.equals("min"))
        			surf = profileResult.min;
        		if(show.equals("max"))
        			surf = profileResult.max;
        			
        		resultWithTimes = new ResultFeatureWithTimes(SpatialLayer.getPointSetCategory(pointSetId).getPointSet().getSampleSet(surf.routerId), surf);
        		resultCache.put(resultId, resultWithTimes);
        	}
    	}
    	
    	return resultWithTimes;
	}
	
	public static ProfileResult getSurface(Integer surfaceId) {
		return profileResultCache.get(surfaceId);
	}

}
