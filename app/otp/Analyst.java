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
import java.util.concurrent.ConcurrentHashMap;

import models.SpatialLayer;

import org.opentripplanner.analyst.core.Sample;	
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.api.param.LatLon;
import org.opentripplanner.api.param.YearMonthDay;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.profile.ProfileResponse;
import org.opentripplanner.profile.ProfileRouter;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.error.VertexNotFoundException;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.spt.ShortestPathTree;

import com.conveyal.otpac.JobItemCallback;
import com.conveyal.otpac.message.JobSpec;
import com.conveyal.otpac.message.WorkResult;
import com.conveyal.otpac.standalone.StandaloneCluster;
import com.conveyal.otpac.standalone.StandaloneExecutive;
import com.conveyal.otpac.standalone.StandaloneWorker;
import com.google.common.collect.Lists;

import play.Logger;


public class Analyst { 
	
	private AnalystGraphService graphService = new AnalystGraphService();
	
	public Analyst() {
		
	}
	 
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
		req.modes.clear();
		switch(mode) {
			case "TRANSIT":
				req.modes.setWalk(true);
				req.modes.setTransit(true);
				break;
			case "CAR,TRANSIT,WALK":
				req.modes.setCar(true);
				req.modes.setTransit(true);
				req.modes.setWalk(true);
				req.kissAndRide = true;
				req.walkReluctance = 1.0;
				break;	
			case "BIKE,TRANSIT":
				req.modes.setBicycle(true);
				req.modes.setTransit(true);
				break;
			case "CAR":
				req.modes.setCar(true);
				break;
			case "BIKE":
				req.modes.setBicycle(true);
				break;
			case "WALK":
				req.modes.setWalk(true);
				break;
		}
	
		
        try {
            req.setRoutingContext(this.graphService.getGraph(graphId));
            return req;
        } catch (VertexNotFoundException vnfe) {
            //Logger.info("no vertex could be created near the origin point");
            return null;
        }
    }
	
	public AnalystProfileRequest buildProfileRequest(String graphId, String mode, LatLon latLon) {
		
		// use center of graph extent if no location is specified
		if(latLon == null) {
			latLon = new LatLon(null);
			latLon.lat = this.graphService.getGraph(graphId).getExtent().centre().y;
			latLon.lon = this.graphService.getGraph(graphId).getExtent().centre().x;
		}
		
		AnalystProfileRequest req = new AnalystProfileRequest();
		
		TraverseModeSet modes = new TraverseModeSet();
		
		switch(mode) {
			case "TRANSIT":
				modes.setWalk(true);
				modes.setTransit(true);
				break;
			case "CAR,TRANSIT,WALK":
				modes.setCar(true);
				modes.setTransit(true);
				modes.setWalk(true);
				break;	
			case "BIKE,TRANSIT":
				modes.setBicycle(true);
				modes.setTransit(true);
				break;
			case "CAR":
				modes.setCar(true);
				break;
			case "BIKE":
				modes.setBicycle(true);
				break;
			case "WALK":
				modes.setWalk(true);
				break;
		}
	
		req.modes = modes;
		req.graphId = graphId;
        req.from       = latLon;
        req.to		   = latLon; // not used but required?
        req.analyst	   = true;
        req.fromTime   = 7 * 60 * 60;
        req.toTime     = 9 * 60 * 60;
        req.walkSpeed  = 1.4f;
        req.bikeSpeed  = 4.1f;
        req.streetTime = 90;
        req.accessTime = 90;
        req.date       = new YearMonthDay("2014-06-04").toJoda();
		
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
