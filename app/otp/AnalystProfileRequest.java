package otp;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import models.SpatialLayer;

import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.ResultSetWithTimes;
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

	private static  Map<String, ResultSet> resultCache = new ConcurrentHashMap<String, ResultSet>();

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
	
	public static ResultSet getResult(Integer surfaceId, String pointSetId, String show) {
		
		String resultId = "resultId_" + surfaceId + "_" + pointSetId + "_" + show;
    	
		ResultSet result;
    	
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
        		
        		result = new ResultSet(SpatialLayer.getPointSetCategory(pointSetId).getPointSet().getSampleSet(surf.routerId), surf);;
        		resultCache.put(resultId, result);
        	}
    	}
    	
    	return result;
	}
	
	public static ResultSetWithTimes getResultWithTimes(Integer surfaceId, String pointSetId, String show) {
		
		String resultId = "resultWithTimesId_" + surfaceId + "_" + pointSetId + "_" + show;
    	
		ResultSetWithTimes resultWithTimes;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			resultWithTimes = (ResultSetWithTimes)resultCache.get(resultId);
        	else {
        		ProfileResult profileResult =getSurface(surfaceId);
        		
        		TimeSurface surf = null;
        		if(show.equals("min"))
        			surf = profileResult.min;
        		if(show.equals("max"))
        			surf = profileResult.max;
        			
        		resultWithTimes = new ResultSetWithTimes(SpatialLayer.getPointSetCategory(pointSetId).getPointSet().getSampleSet(surf.routerId), surf);
        		resultCache.put(resultId, resultWithTimes);
        	}
    	}
    	
    	return resultWithTimes;
	}
	
	public static ProfileResult getSurface(Integer surfaceId) {
		return profileResultCache.get(surfaceId);
	}

}
