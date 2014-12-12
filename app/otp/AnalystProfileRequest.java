package otp;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import models.Shapefile;
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

import utils.ResultEnvelope;

import com.google.common.collect.Lists;

import controllers.Api;

public class AnalystProfileRequest extends ProfileRequest{
	
	private static final long serialVersionUID = 1L;

	private static SurfaceCache profileResultCache = new SurfaceCache(100);

	private static  Map<String, ResultSet> resultCache = new ConcurrentHashMap<String, ResultSet>();

	public int cutoffMinutes;
	public String graphId;
	
	public TimeSurfaceShort createSurfaces(ResultEnvelope.Which which) {
		
		ProfileRouter router = new ProfileRouter(Api.analyst.getGraph(graphId), this);
		
        try {
        	ProfileResponse response = router.route();
    
            router.minSurface.cutoffMinutes = cutoffMinutes;
            router.maxSurface.cutoffMinutes = cutoffMinutes;
            
            // add both the min surface and the max surface to the cache; they will be retrieved later on by ID
            profileResultCache.add(router.minSurface);
            profileResultCache.add(router.maxSurface);
        }
        catch (Exception e) {
        	e.printStackTrace();
        }
        finally {
            router.cleanup(); // destroy routing contexts even when an exception happens
        }
        
        switch(which) {
        case BEST_CASE:
        	return new TimeSurfaceShort(router.minSurface);
        case WORST_CASE:
        	return new TimeSurfaceShort(router.maxSurface);
        default:
        	return null;
        }
  	}
	
	/**
	 * Get the ResultSet for the given ID. Note that no ResultEnvelope.Which need be specified as each surface ID is unique to a particular
	 * statistic.
	 */
	public static ResultSet getResult(Integer surfaceId, String shapefileId, String attributeName) {
		
		String resultId = "resultId_" + surfaceId + "_" + shapefileId + "_" + attributeName;
    	
		ResultSet result;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			result = resultCache.get(resultId);
        	else {
        		TimeSurface surf =getSurface(surfaceId);
        		
        		result = new ResultSet(Shapefile.getShapefile(shapefileId).getPointSet(attributeName).getSampleSet(surf.routerId), surf);
        		resultCache.put(resultId, result);
        	}
    	}
    	
    	return result;
	}
	
	/**
	 * Get the ResultSet for the given ID. Note that no min/max need be specified as each surface ID is unique to a particular
	 * statistic.
	 */
	public static ResultSetWithTimes getResultWithTimes(Integer surfaceId, String shapefileId, String attributeName) {
		
		String resultId = "resultWithTimesId_" + surfaceId + "_" + shapefileId + "_" + attributeName;
    	
		ResultSetWithTimes resultWithTimes;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			resultWithTimes = (ResultSetWithTimes)resultCache.get(resultId);
        	else {
        		TimeSurface surf = getSurface(surfaceId);
        			
        		resultWithTimes = new ResultSetWithTimes(Shapefile.getShapefile(shapefileId).getPointSet(attributeName).getSampleSet(surf.routerId), surf);
        		resultCache.put(resultId, resultWithTimes);
        	}
    	}
    	
    	return resultWithTimes;
	}
	
	public static TimeSurface getSurface(Integer surfaceId) {
		return profileResultCache.get(surfaceId);
	}

}
