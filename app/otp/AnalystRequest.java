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

import models.PointSetCategory;

import org.opentripplanner.analyst.Indicator;
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
	private static  Map<String, Indicator> indicatorCache = new ConcurrentHashMap<String, Indicator>();
	
	private static PrototypeAnalystRequest prototypeRequest = new PrototypeAnalystRequest();

	public int cutoffMinutes;
	
	static public AnalystRequest create(String graphId, GenericLocation latLon, int cutoffMinutes) throws IOException, NoSuchAlgorithmException {
		
		AnalystRequest request = (AnalystRequest)prototypeRequest.clone();
		
		request.cutoffMinutes = cutoffMinutes;
		request.routerId = graphId;
	
        if (request.arriveBy)
            request.setTo(latLon);
        else
            request.setFrom(latLon);
        
		return request;
	}
	
	public TimeSurfaceShort createSurface() {
		
		EarliestArrivalSPTService sptService = new EarliestArrivalSPTService();
        sptService.setMaxDuration(60 * cutoffMinutes);
        
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
	
	public static Indicator getIndicator(Integer surfaceId, String pointSetId) {
		
		String indicatorId = "indicatorId_" + surfaceId + "_" + pointSetId;
    	
    	Indicator indicator;
    	
    	synchronized(indicatorCache) {
    		if(indicatorCache.containsKey(indicatorId))
        		indicator = indicatorCache.get(indicatorId);
        	else {
        		indicator = new Indicator(PointSetCategory.getPointSetCategory(pointSetId).getPointSet(), getSurface(surfaceId), true);;
        		indicatorCache.put(indicatorId, indicator);
        	}
    	}
    	
    	return indicator;
	}
	
	public static TimeSurface getSurface(Integer surfaceId) {
		return surfaceCache.get(surfaceId);
	}
}
