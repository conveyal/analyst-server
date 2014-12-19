package otp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import models.SpatialLayer;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.opentripplanner.analyst.core.Sample;	
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.api.param.LatLon;
import org.opentripplanner.api.param.YearMonthDay;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.Option;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.profile.ProfileResponse;
import org.opentripplanner.profile.ProfileRouter;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.error.VertexNotFoundException;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.spt.ShortestPathTree;

import com.conveyal.otpac.PrototypeAnalystProfileRequest;
import com.conveyal.otpac.PrototypeAnalystRequest;
import com.conveyal.otpac.message.JobSpec;
import com.conveyal.otpac.message.WorkResult;
import com.conveyal.otpac.standalone.StandaloneCluster;
import com.conveyal.otpac.standalone.StandaloneExecutive;
import com.conveyal.otpac.standalone.StandaloneWorker;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;

import play.Logger;


public class Analyst { 
	
	private AnalystGraphService graphService = new AnalystGraphService();
	
	public Analyst() {
		
	}
	 
	/**
	 * Build a routing request. Note that this does not set the routing context, you'll have to do that
	 * manually; this is because, in cluster mode, we don't want to serialize the routing context and send
	 * it over the wire.
	 */
	public AnalystRequest buildRequest(String graphId, GenericLocation latLon, String mode, int cutoffMinutes) {
		
		// use center of graph extent if no location is specified
		if(latLon == null)
			latLon = new GenericLocation(this.graphService.getGraph(graphId).getExtent().centre().y, this.graphService.getGraph(graphId).getExtent().centre().x);
		 
		AnalystRequest req;
		
		try {
			req = AnalystRequest.create(graphId, latLon, cutoffMinutes);
		} catch (NoSuchAlgorithmException | IOException e) {
			Logger.error("unable to create request id");
			return null;
		}
		
		req.modes = new TraverseModeSet(mode);
		
		if (req.modes.isTransit()) {
			Logger.warn("Building a non-profile transit routing request, this probably shouldn't be happening.");
			req.walkReluctance = 1.0;
		}

		return req;
    }
	
	public RoutingRequest buildClusterRequest (String graphId, GenericLocation latLon, String mode, int cutoffMinutes) {
		// use center of graph extent if no location is specified
		if(latLon == null)
			latLon = new GenericLocation(this.graphService.getGraph(graphId).getExtent().centre().y, this.graphService.getGraph(graphId).getExtent().centre().x);
		 
		PrototypeAnalystRequest req = new PrototypeAnalystRequest();
		
		// TODO hardwired time zone
		req.dateTime = new LocalDateTime(2014, 12, 11, 8, 0, 0).toDate(TimeZone.getTimeZone("America/Argentina/Buenos_Aires")).getTime();
		req.modes = new TraverseModeSet(mode);
		req.routerId = graphId;
		req.from = latLon;
		req.worstTime = req.dateTime + cutoffMinutes * 60 * 1000;
		
		if (req.modes.isTransit()) {
			Logger.warn("Building a non-profile transit routing request, this probably shouldn't be happening.");
			req.walkReluctance = 1.0;
		}

		return req;
	}
	
	public ProfileRequest buildClusterProfileRequest(String graphId, String mode, LatLon latLon) {
		PrototypeAnalystProfileRequest req = new PrototypeAnalystProfileRequest();
		
		// split the modeset into two modes
		TraverseModeSet modes = new TraverseModeSet(mode);
		modes.setTransit(false);

		TraverseModeSet transitModes = new TraverseModeSet(mode);
		transitModes.setBicycle(false);
		transitModes.setDriving(false);
		transitModes.setWalk(false);

		req.accessModes = req.egressModes = req.directModes = modes;
		req.transitModes = transitModes;

        req.from       = latLon;
        req.to		   = latLon; // not used but required
        req.analyst	   = true;
        req.fromTime   = 7 * 60 * 60;
        req.toTime     = 9 * 60 * 60;
        req.walkSpeed  = 1.4f;
        req.bikeSpeed  = 4.1f;
        req.carSpeed   = 20f;
        req.streetTime = 10;
        req.date       = new YearMonthDay("2014-12-04").toJoda();
		
        req.maxWalkTime = 20;
		req.maxBikeTime = 20;
		req.maxCarTime  = 20;
		req.minBikeTime = 5;
		req.minCarTime  = 5;
		
		req.limit       = 10;
		req.suboptimalMinutes = 5;
		
		// doesn't matter for analyst requests
		req.orderBy = Option.SortOrder.AVG;
		
		return req;
	}
	
	public AnalystProfileRequest buildProfileRequest(String graphId, String mode, LatLon latLon) {
		
		// use center of graph extent if no location is specified
		if(latLon == null) {
			Coordinate center = this.graphService.getGraph(graphId).getExtent().centre();
			latLon = new LatLon(String.format("%f,%f", center.y, center.x));
		}
		
		AnalystProfileRequest req = new AnalystProfileRequest();
		
		// split the modeset into two modes
		TraverseModeSet modes = new TraverseModeSet(mode);
		modes.setTransit(false);

		TraverseModeSet transitModes = new TraverseModeSet(mode);
		transitModes.setBicycle(false);
		transitModes.setDriving(false);
		transitModes.setWalk(false);

		req.accessModes = req.egressModes = req.directModes = modes;
		req.transitModes = transitModes;

		req.graphId = graphId;
        req.from       = latLon;
        req.to		   = latLon; // not used but required
        req.analyst	   = true;
        req.fromTime   = 7 * 60 * 60;
        req.toTime     = 9 * 60 * 60;
        req.walkSpeed  = 1.4f;
        req.bikeSpeed  = 4.1f;
        req.carSpeed   = 20f;
        req.streetTime = 10;
        req.date       = new YearMonthDay("2014-12-04").toJoda();
		
        req.maxWalkTime = 20;
		req.maxBikeTime = 20;
		req.maxCarTime  = 20;
		req.minBikeTime = 5;
		req.minCarTime  = 5;
		
		req.limit       = 10;
		req.suboptimalMinutes = 5;
		
		// doesn't matter for analyst requests
		req.orderBy = Option.SortOrder.AVG;
		
		req.cutoffMinutes = 120;
		
        return req;
        
	}
		
	public Graph getGraph (String graphId) {
		return graphService.getGraph(graphId);
	}
	
	public String getGraphStatus (String graphId) {
		return graphService.getGraphStatus(graphId);
	}
	
	public File getZippedGraph (String graphId) throws IOException {
		return graphService.getZippedGraph(graphId);
	}
	
	public GraphService getGraphService() {
		return graphService;
	}
	
	public Sample getSample (String graphId, double lon, double lat) {
		return graphService.getGraph(graphId).getSampleFactory().getSample(lon, lat);
	}
	
}
