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

import org.opentripplanner.analyst.core.Sample;	
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
import org.opentripplanner.routing.error.VertexNotFoundException;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.spt.ShortestPathTree;

import play.Logger;


public class Analyst { 
	
	private AnalystGraphService graphService = new AnalystGraphService();
	
	public Analyst() {
		
	}
	 
	public AnalystRequest buildRequest(String graphId, GenericLocation latLon, String mode) {
		
		// use center of graph extent if no location is specified
		if(latLon == null)
			latLon = new GenericLocation(this.graphService.getGraph(graphId).getExtent().centre().y, this.graphService.getGraph(graphId).getExtent().centre().x);
		 
		AnalystRequest req;
		
		try {
			req = AnalystRequest.create(graphId, latLon);
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
			req.calcHash();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        try {
            req.setRoutingContext(this.graphService.getGraph(graphId));
            return req;
        } catch (VertexNotFoundException vnfe) {
            //Logger.info("no vertex could be created near the origin point");
            return null;
        }
    }

	
	public Graph getGraph (String graphId) {
		return graphService.getGraph(graphId);
	}
	
	public Sample getSample (String graphId, double lon, double lat) {
		return graphService.getSampleFactory(graphId).getSample(lon, lat);
	}
	 
	public SptResponse getSptResponse(AnalystRequest req, String spatialId) {
		
		SptResponse response = SptResponse.create(req, spatialId);
		return response;
	}
}
