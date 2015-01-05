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
import org.joda.time.LocalDate;
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
import org.opentripplanner.routing.services.GraphSource;
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
	
	public RoutingRequest buildRequest(String graphId, LocalDate date, int time, GenericLocation latLon, String mode, int cutoffMinutes) {
		Graph graph = getGraph(graphId);
		
		// use center of graph extent if no location is specified
		if(latLon == null)
			latLon = new GenericLocation(graph.getExtent().centre().y, graph.getExtent().centre().x);
		
		// use graph time zone to build request
		TimeZone tz = graph.getTimeZone();
		
		PrototypeAnalystRequest req = new PrototypeAnalystRequest();
		
		req.dateTime = date.toDateTimeAtStartOfDay(DateTimeZone.forTimeZone(tz)).toDate().getTime() / 1000;
		req.dateTime += time;
		req.modes = new TraverseModeSet(mode);
		req.routerId = graphId;
		req.from = latLon;
		req.worstTime = req.dateTime + cutoffMinutes * 60;
		
		if (req.modes.isTransit()) {
			Logger.warn("Building a non-profile transit routing request, this probably shouldn't be happening.");
			req.walkReluctance = 1.0;
		}

		return req;
	}
	
	public ProfileRequest buildProfileRequest(String mode, LocalDate date, int fromTime, int toTime, LatLon latLon) {
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
        req.fromTime   = fromTime;
        req.toTime     = toTime;
        req.walkSpeed  = 1.4f;
        req.bikeSpeed  = 4.1f;
        req.carSpeed   = 20f;
        req.streetTime = 10;
        req.date       = date;
		
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
		
	public Graph getGraph (String graphId) {
		return graphService.getGraph(graphId);
	}
	
	public String getGraphStatus (String graphId) {
		return graphService.getGraphStatus(graphId);
	}
	
	public File getZippedGraph (String graphId) throws IOException {
		return graphService.getZippedGraph(graphId);
	}
	
	public Sample getSample (String graphId, double lon, double lat) {
		return graphService.getGraph(graphId).getSampleFactory().getSample(lon, lat);
	}
	
}
