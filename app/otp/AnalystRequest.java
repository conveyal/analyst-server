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

import models.Shapefile;
import models.SpatialLayer;

import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.ResultSetWithTimes;
import org.opentripplanner.analyst.SampleSet;
import org.opentripplanner.analyst.SurfaceCache;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.spt.ShortestPathTree;

import com.amazonaws.services.elasticmapreduce.model.Application;
import com.google.common.collect.Maps;

import controllers.Api;

public class AnalystRequest extends RoutingRequest{
	
	private static final long serialVersionUID = 1L;

	private static SurfaceCache surfaceCache = new SurfaceCache(100);
	private static  Map<String, ResultSet> resultCache = new ConcurrentHashMap<String, ResultSet>();
	
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
	
	public static TimeSurfaceShort createSurface(RoutingRequest req, int cutoffMinutes) {
		
		EarliestArrivalSPTService sptService = new EarliestArrivalSPTService();
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
	
	public static ResultSet getResult(Integer surfaceId, String shapefileId, String attributeName) {
		
		String resultId = "resultId_" + surfaceId + "_" + shapefileId + "_" + attributeName;
    	
		ResultSet result;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			result = resultCache.get(resultId);
        	else {
        		TimeSurface surf =getSurface(surfaceId);
        		PointSet ps = Shapefile.getShapefile(shapefileId).getPointSet(attributeName);
        		SampleSet ss = ps.getSampleSet(Api.analyst.getGraph(surf.routerId));
        		result = new ResultSet(ss, surf);
        		resultCache.put(resultId, result);
        	}
    	}
    	
    	return result;
	}
	
	public static ResultSetWithTimes getResultWithTimes(Integer surfaceId, String shapefileId, String attributeName) {
		
		String resultId = "resultWIthTimesId_" + surfaceId + "_" + shapefileId + "_" + attributeName;
    	
		ResultSetWithTimes resultWithTimes;
    	
    	synchronized(resultCache) {
    		if(resultCache.containsKey(resultId))
    			resultWithTimes = (ResultSetWithTimes)resultCache.get(resultId);
        	else {
        		TimeSurface surf =getSurface(surfaceId);
        		PointSet ps = Shapefile.getShapefile(shapefileId).getPointSet(attributeName);
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
