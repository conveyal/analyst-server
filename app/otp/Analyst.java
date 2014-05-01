package otp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import model.AnalystRequest;
import model.Location;
import model.SptResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.pool.impl.GenericObjectPool.Config;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.core.Sample;	
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.error.VertexNotFoundException;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.SPTService;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.TransitStop;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.RoundRobinRouter;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.Point;

import controllers.Application;
import play.Logger;
import play.libs.Akka;

public class Analyst { 
	
	private AnalystConfigurator ac;
	
	private GraphService graphService;
	private SPTService sptService;
	private SampleFactory sampleSource;

	private ActorRef master;
	
	private MutligraphSampleFactory sampleFactory = new MutligraphSampleFactory();
	
	private ArrayList<Location> destinations = new ArrayList<Location>();
	
	IndicatorManager indicatorManager;

	public Analyst() {
		
		// Create an Akka system
	    ActorSystem system = ActorSystem.create("AnalystSystem");

	    // create the result listener, which will print the result and shutdown the system
	    final ActorRef listener = system.actorOf(Props.create(Listener.class), "listener");

	    // create the master
	    final Analyst a = this;
	   
	    Props p = Props.create(BatchAnalystMaster.class, a, listener);
	    p.withDispatcher("akka.actor.batch-dispatcher");
	    master = system.actorOf(p, "master");
		
		ac = new AnalystConfigurator();
		 
		ac.getComponentProviderFactory();
	
		graphService = ac.getGraphService();
		sptService = ac.getSptService();
		
		for(String routerId : graphService.getRouterIds()) {
			
			GeometryIndex graphIndex = new GeometryIndex(graphService.getGraph(routerId));
			
			sampleFactory.addSampleFactory(routerId, new SampleFactory(graphIndex));
			
		}
	
		indicatorManager = new IndicatorManager(sampleFactory);
		
		System.out.println("loaded " + destinations.size() + " destionations");
	}
	 
	public Envelope getMetadata() {
		return this.graphService.getGraph().getExtent();
	}
	
	public Sample getSample(String graphId, Point p) {
		return sampleFactory.getSample(graphId, p.getX(), p.getY());
	}
	
	public AnalystRequest buildRequest(GenericLocation latLon, String mode, String graphId) {
		
		// use center of graph extent if no location is specified
		if(latLon == null)
			latLon = new GenericLocation(this.graphService.getGraph().getExtent().centre().y, this.graphService.getGraph().getExtent().centre().x);
		 
		AnalystRequest req;
		
		try {
			req = AnalystRequest.create(latLon, graphId);
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
            req.setRoutingContext(this.graphService.getGraph());
            return req;
        } catch (VertexNotFoundException vnfe) {
            //Logger.info("no vertex could be created near the origin point");
            return null;
        }
    }
	
	public Graph getGraph () {
		return this.graphService.getGraph();
	}

	public SptResponse getSpt(AnalystRequest req, HashMap<String, Sample> destinations) {
		
		final ShortestPathTree spt = sptService.getShortestPathTree(req);
		req.cleanup();
		
		SptResponse response = new SptResponse(req, spt);
		
		if(destinations != null)
			response.calcDestinations(destinations);
		
		return response;
	}
	

	
	public void batch(String graphId, Integer page, Integer pageCount, String mode, Integer timeLimit) {
		
	    master.tell(new AnalystBatchRequest(graphId, page, pageCount, mode, timeLimit), master);

	}
	 
	public int testGraph() {
		return graphService.getGraph().getAgencyIds().size();
	
	}
	 
	public boolean testSpt() {
		if(sptService != null)
			return true;
		else
			return false;	
	}
	
	static class BatchStatus {
	    private final long processed;

	    public BatchStatus(long processed) {
	      this.processed = processed;
	 	}

	    public double getProcessed() {
	      return processed;
	    }
	  }
	
	public static class BatchAnalystMaster extends UntypedActor {
		
	    private final long start = System.currentTimeMillis();

	    private final ActorRef listener;
	    private final ActorRef workerRouter;
	    
	    private Analyst analyst;

	    private int totalItems = 0;
	    private int processedlRequests;
	    private int processedItems = 0;
	    private int failedItems = 0;
	    private int pageSize = 0;
	    private Long startTime;
	    
	    PrintWriter f0; 
	    PrintWriter f1; 
	    
	    public BatchAnalystMaster(
	      Analyst analyst,
	      ActorRef listener) {
	      this.listener = listener;
	      this.analyst = analyst;
	      
	      Date d = new Date();
	      try {
			f0 = new PrintWriter(new FileWriter("data/output/" + d.getTime() + "_"  + "_blocks_pairs.csv"));
			f1 = new PrintWriter(new FileWriter("data/output/" + d.getTime() + "_failed_block_pairs.csv"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	      
	      System.out.println("starting worker with " +  Runtime.getRuntime().availableProcessors() + " threads.");
	      workerRouter = this.getContext().actorOf(Props.create(BatchAnalystWorker.class).withRouter(new RoundRobinRouter(Runtime.getRuntime().availableProcessors())), "workerRouter");
	    }

	    public void onReceive(Object message) {
	      if (message instanceof AnalystBatchRequest) {
	    	  
	    	AnalystBatchRequest request = (AnalystBatchRequest)message;
	    		
			
			pageSize = (Application.analyst.destinations.size() / request.pageCount) -1;
			
			totalItems = pageSize  * totalItems; 
			startTime = System.currentTimeMillis();
			
			System.out.println("processing items for page " + request.page);
			
			// need to interleave pages as not all items are equal -- sorting unfairly distributed the load
			int cur = 1;
			for(Location d : Application.analyst.destinations) {
				
				if(cur == request.page) {
					AnalystWorkerRequest ar = new AnalystWorkerRequest();
					ar.location = d;
					ar.graphId = request.graphId;
					ar.mode = request.mode;
					ar.timeLimit = request.timeLimit;
					workerRouter.tell(ar, getSelf());
				}
				cur++;
				if(cur > request.pageCount)
					cur =1;
			}
			
					
					
	    	  
	      } else if (message instanceof Result) {
	        Result result = (Result) message;
	        
	        synchronized(startTime) {
	        	 long elapsedTime = System.currentTimeMillis() - startTime;
	        	 
	        	 processedItems++; 
	        	 
	        	 double itemsPerSec = (double)processedItems / elapsedTime * 1000;
	        	 
	        	 for(int i = 0; i <= result.i; i++)	 
	        		 f0.println(result.originId[i] + "," + result.destId[i] + "," + result.time[i]);
	        	 
	        	 
	        	 System.out.print(processedItems + ":" +  failedItems + "\t / \t" + totalItems + " \t -- \t " + itemsPerSec + "\r");
	        	
	        }
	     
	      } else if (message instanceof Done)  {
	    	  if(processedlRequests == pageSize) {
	    		  f0.flush();
	     		  f1.flush();
	    	  }
	      }
	      else {
	        unhandled(message);
	      }
	    }
	  }
	
	public static class BatchAnalystWorker extends UntypedActor {
		  LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		 
		 
		  public void onReceive(Object message) throws Exception {
		    
				if(message instanceof AnalystWorkerRequest) {
					AnalystWorkerRequest ar = (AnalystWorkerRequest)message;
					
					try {
						
				    	AnalystRequest req = Application.analyst.buildRequest(new GenericLocation(ar.location.point.getY(), ar.location.point.getX()), ar.mode, ar.graphId);
				    	
						if(req != null) {
							final ShortestPathTree spt = Application.analyst.sptService.getShortestPathTree(req);
							req.cleanup();
							
							SptResponse response = new SptResponse(req, spt);
							
							Result r = new Result();
							
							for(Location d : Application.analyst.destinations) {
								if(d.getSample(ar.graphId) != null) {
									long time = response.evaluateSample(d.getSample(ar.graphId));
									r.add(ar.location.id, d.id, time);
								}
							}
							
							getSender().tell(r, getSelf());
							
						}
						
						getSender().tell(new Done(), getSelf());
					}
					catch(Exception e) {
						getSender().tell(new Done(), getSelf());
					}
				}
		  }
	}
	
	static class Result {
		String[] originId = new String[Application.analyst.destinations.size()];
		String[] destId = new String[Application.analyst.destinations.size()];
	    Long[] time = new Long[Application.analyst.destinations.size()];
	    
	    int i = 0;
	   
	    public Result() {
	    	
	    }
	    
	    public void add(String oid, String did, long time) {
	    	this.originId[i] = oid;
	    	this.destId[i] = did;	
	    	this.time[i] = time;
	    	i++;
	    }
	  }
	
	static class Done {
		
	}
	
	public static class Listener extends UntypedActor {
	    public void onReceive(Object message) {
	      if (message instanceof BatchStatus) {
	    	  BatchStatus status = (BatchStatus) message;
	          System.out.println(String.format("\n\tBatch status: " + 
	          "\t\t%s\n\tCalculation time: \t%s",
	          status.getProcessed()));
	        getContext().system().shutdown();
	      } else {
	        unhandled(message);
	      }
	    }
	  }
	
	public class AnalystBatchRequest {
		
		Integer page;
		Integer pageCount;
		String mode;
		Integer timeLimit;
		String graphId;
		
		AnalystBatchRequest(String gId, Integer p, Integer c, String m, Integer tl) {
			page = p;
			pageCount = c;
			mode = m;
			timeLimit = tl;
			graphId = gId;
			
		}
	}
		
}
