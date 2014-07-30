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
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
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
	
	public void createJob() throws Exception {
		
		// start up cluster
		StandaloneCluster cluster = new StandaloneCluster("s3credentials", true, null);
	
		StandaloneExecutive exec = cluster.createExecutive();
		StandaloneWorker worker = cluster.createWorker();
	
		cluster.registerWorker(exec, worker);
	
		// build the request
		JobSpec js = new JobSpec("austin", "austin.csv", "austin.csv", "2014-06-09", "8:05 AM", "America/Chicago");
	
		// plus a callback that registers how many work items have returned
		class CounterCallback implements JobItemCallback {
			int jobsBack = 0;
	
			@Override
			public void onWorkResult(WorkResult res) {
				System.out.println("got callback: ");
				jobsBack += 1;
			}
		}
		;
		CounterCallback callback = new CounterCallback();
		js.setCallback(callback);
	
		// start the job
		exec.find(js);
	
		// stall until a work item returns
		while (callback.jobsBack == 0) {
			Thread.sleep(100);
		}
		
		cluster.stop(worker);
	}

	
	public Graph getGraph (String graphId) {
		return graphService.getGraph(graphId);
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
