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

import model.AnalystRequest;
import model.AttributeGroup;
import model.HaltonPoints;
import model.IndicatorItem;
import model.IndicatorQueryItem;
import model.IndicatorResponse;
import model.IndicatorSummary;
import model.SptResponse;


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
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.routing.RoundRobinRouter;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;

import controllers.Api;
import play.Logger;
import play.libs.Akka;
import util.GeoUtils;

public class Analyst { 
	
	private AnalystConfigurator ac;
	
	private GraphService graphService;
	private SPTService sptService;
	private SampleFactory sampleSource;
	 
	private IndicatorManager indicators;
	
	private ActorRef master;
	
	// TODO develop a cache eviction strategy -- this will run out of memory as it is used
	private  Map<String, SptResponse> sptCache = new HashMap<String, SptResponse>();
	
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
		
		GeometryIndex graphIndex = new GeometryIndex(graphService.getGraph());
		
		sampleSource = new SampleFactory(graphIndex);
		
		indicators = new IndicatorManager(sampleSource);
		
		try {
			
			Blocks blocks = new Blocks();
			blocks.load(new File("data/ba/blocks.shp"));
			
			indicators.loadJson(new File("data/ba/jobs_type.json"), blocks);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	 
	public Envelope getMetadata() {
		return this.graphService.getGraph().getExtent();
	}
	public  ConcurrentHashMap<String, Collection<AttributeGroup>> getIndicatorMetadata() {
		return indicators.indicatorMetadata();
	}
	
	public HaltonPoints getHaltonPoints(String blockId, String indicatorId, String attribute) {
		return indicators.getHaltonPoints(blockId, indicatorId, attribute);
	}
	
	public List<IndicatorItem> queryIndicators(String sptId, String indicatorIds, Integer seconds) {

		List<IndicatorItem> items = new ArrayList<IndicatorItem>();
		
		String [] ids = indicatorIds.split(",");
		
		if(sptId.equals("all")) {
			for(String id : ids) {
				for(IndicatorItem item : indicators.queryAll(id)) {
					items.add(item);
				}
			}
		}
		else {
			if(!sptCache.containsKey(sptId))
				return null;
		
			SptResponse spt = (SptResponse)sptCache.get(sptId);
			
			for(String id : ids) {
				for(IndicatorItem item : indicators.queryAll(id)) {
					if(spt.destinationTimes.containsKey(item.geoId)) {
						if(spt.destinationTimes.get(item.geoId) <= seconds) {
							items.add(item);
						}
					}
				}
			}
		}
		
		return items;
	}
	
	public  ConcurrentHashMap<String, IndicatorQueryItem> queryBlocks(String sptId, String indicatorId, Integer seconds) {
		
		ConcurrentHashMap<String, IndicatorQueryItem> items = new  ConcurrentHashMap<String, IndicatorQueryItem>();
		
		if(sptId.equals("all")) {
			
			for(IndicatorItem item : indicators.queryAll(indicatorId)) {
				items.put(item.geoId, new IndicatorQueryItem(0l, item));
			}
		}
		else {
			
			if(!sptCache.containsKey(sptId))
				return null;
		
			SptResponse spt = (SptResponse)sptCache.get(sptId);
		
			for(IndicatorItem item : indicators.queryAll(indicatorId)) {
				if(spt.destinationTimes.containsKey(item.geoId) && spt.destinationTimes.get(item.geoId) <= seconds) {
					items.put(item.geoId, new IndicatorQueryItem(spt.destinationTimes.get(item.geoId), item));
				}
			}
			
		}
				
		return items;
	}
	
	public List<IndicatorItem> getIndicatorsForEnv(String indicatorId, Envelope env) {
		
		return indicators.query(indicatorId, env);
	}
	
	public List<IndicatorItem> getIndicatorsById(String indicatorId) {
		
		return indicators.queryAll(indicatorId);
	}
	
	public AnalystRequest buildRequest(GenericLocation latLon, String mode) {
		
		// use center of graph extent if no location is specified
		if(latLon == null)
			latLon = new GenericLocation(this.graphService.getGraph().getExtent().centre().y, this.graphService.getGraph().getExtent().centre().x);
		 
		AnalystRequest req;
		
		try {
			req = AnalystRequest.create(latLon);
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
	
	public Sample getSample (double lon, double lat) {
		return sampleSource.getSample(lon, lat);
	}
	 
	public SptResponse getSpt(AnalystRequest req, HashMap<String, Sample> destinations) {
		
		if(sptCache.containsKey(req.sptId))
			return (SptResponse)sptCache.get(req.sptId);
	
		final ShortestPathTree spt = sptService.getShortestPathTree(req);
		req.cleanup();
		
		SptResponse response = new SptResponse(req, spt);
		
		if(destinations != null)
			response.calcDestinations(destinations);
		
		sptCache.put(req.sptId, response);
		
		return response;
	}
	
	public SptResponse getSptById(String sptId) {
		
		if(sptCache.containsKey(sptId))
			return (SptResponse)sptCache.get(sptId);
		else 
			return null;
	}
	
	public Object testBatch(Integer page, Integer pageCount, String mode, Integer timeLimit) {
		
	    master.tell(new AnalystBatchRequest(page, pageCount, mode, timeLimit), master);
		
		return new IndicatorResponse("", 0, "workforce_edu");
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
	    private int processedItems = 0;
	    private int failedItems = 0;
	    private Long startTime;
	    
	    PrintWriter f0; 
	    PrintWriter f1; 
	    
	    public BatchAnalystMaster(
	      Analyst analyst,
	      ActorRef listener) {
	      this.listener = listener;
	      this.analyst = analyst;
	      
	      try {
			f0 = new PrintWriter(new FileWriter("batch_blocks.csv"));
			f1 = new PrintWriter(new FileWriter("failed_blocks.csv"));
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
	    			
			List<IndicatorItem> items = analyst.indicators.queryAll("workforce_edu");
		
			Collections.sort(items);
			
			int pageSize = (items.size() / request.pageCount) -1;
			
			totalItems = pageSize;
			startTime = System.currentTimeMillis();
			
			System.out.println("processing items for page " + request.page);
			
			// need to interleave pages as not all items are equal -- sorting unfairly distributed the load
			int cur = 1;
			for(IndicatorItem i : items) {
				
				if(cur == request.page) {
					AnalystWorkerRequest ar = new AnalystWorkerRequest();
					ar.item = i;
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
	        	 
	        	 if(result.isSuccessful())
	        		 f0.println(result.getId() + "," + result.getJobs() + "," + result.getJobsCollege() + "," + result.getJobsLessThanCollege() + "," + result.getWorkforce() + "," + result.getWorkforceCollege() + "," + result.getWorkforceLessThanCollege());
	        	 else {
	        		 f1.println(result.getId());
	        		 failedItems++;
	        	 }
	        	 
	        	 System.out.print(processedItems + ":" +  failedItems + "\t / \t" + totalItems + " \t -- \t " + itemsPerSec + "\r");
	        	 
	        	 if(processedItems == totalItems) {
	        		 f0.flush();
	        		 f1.flush();
	        	 }
	        }
	     
	      } else {
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
						
				    	AnalystRequest req = Api.analyst.buildRequest(new GenericLocation(ar.item.point.getY(), ar.item.point.getX()), ar.mode);
						
						if(req != null) {
							final ShortestPathTree spt = Api.analyst.sptService.getShortestPathTree(req);
							req.cleanup();
							
							SptResponse response = new SptResponse(req, spt);
							
							ArrayList<IndicatorItem> jobsStatsList = new ArrayList<IndicatorItem>();
							
							for(IndicatorItem item : Api.analyst.indicators.queryAll("jobs_edu")) {
								if(item.sample != null) {
									long time = response.evaluateSample(item.sample);
									if(time <= ar.timeLimit) {
										jobsStatsList.add(item);
									}
								}
							}
							
							ArrayList<IndicatorItem> workforceStatsList = new ArrayList<IndicatorItem>();
							
							for(IndicatorItem item : Api.analyst.indicators.queryAll("workforce_edu")) {
								if(item.sample != null) {
									long time = response.evaluateSample(item.sample);
									if(time <= ar.timeLimit) {
										workforceStatsList.add(item);
									}
								}
							}
							
							IndicatorSummary isJobs = new IndicatorSummary("jobs_edu", jobsStatsList);
							IndicatorSummary isWorkforce = new IndicatorSummary("workforce_edu", workforceStatsList);
							
							Long jobsCollege =  0l;
							if(isJobs.attributes.containsKey("college"))
								jobsCollege += isJobs.attributes.get("college").total;
							
							Long jobsLessThanCollege = 0l;
							
							if(isJobs.attributes.containsKey("hs_less"))
								jobsLessThanCollege += isJobs.attributes.get("hs_less").total;
							
							if(isJobs.attributes.containsKey("hs"))
								jobsLessThanCollege += isJobs.attributes.get("hs").total;
							
							if(isJobs.attributes.containsKey("some_college"))
								jobsLessThanCollege += isJobs.attributes.get("some_college").total;
							
							
							Long workforceCollege =  0l;
							if(isWorkforce.attributes.containsKey("college"))
								workforceCollege += isWorkforce.attributes.get("college").total;
							
							Long workforceLessThanCollege = 0l;
							
							if(isWorkforce.attributes.containsKey("hs_less"))
								workforceLessThanCollege += isWorkforce.attributes.get("hs_less").total;
							
							if(isWorkforce.attributes.containsKey("hs"))
								workforceLessThanCollege += isWorkforce.attributes.get("hs").total;
							
							if(isWorkforce.attributes.containsKey("some_college"))
								workforceLessThanCollege += isWorkforce.attributes.get("some_college").total;
							
							getSender().tell(new Result(ar.item.geoId, isJobs.total,jobsCollege, jobsLessThanCollege, isWorkforce.total, workforceCollege, workforceLessThanCollege, true), getSelf());
						}
						else {
							getSender().tell(new Result(ar.item.geoId, 0l, 0l, 0l, 0l, 0l, 0l, false), getSelf());
						}
					}
					catch(Exception e) {
						getSender().tell(new Result(ar.item.geoId, 0l, 0l, 0l, 0l, 0l, 0l, false), getSelf());
					}
				}
		  }
	}
	
	static class Result {
		private final String id;
	    private final long jobs;
	    private final long workforce;
	    private final long jobsCollege;
	    private final long jobsLessThanCollege;
	    private final long workforceCollege;
	    private final long workforceLessThanCollege;
	    
	    private final boolean success;

	    public Result(String id, long jobs,long jobsCollege, long jobsLessThanCollege, long workforce, long workforceCollege, long workforceLessThanCollege, boolean success) {
	    	this.id = id;
	    	this.jobs = jobs;
	    	this.workforce = workforce;
	    	this.success = success;
	    	this.workforceCollege = workforceCollege;
	    	this.workforceLessThanCollege = workforceLessThanCollege;
	    	this.jobsCollege = jobsCollege;
	    	this.jobsLessThanCollege = workforceLessThanCollege;
	    }
	    
	    public String getId() {
		      return id;
		    }
	    
	    public long getJobs() {
	      return jobs;
	    }
	    
	    public Long getJobsCollege() {
		      return jobsCollege;
		    }
	    
	    public long getJobsLessThanCollege() {
	      return jobsLessThanCollege;
	    }
	    
	    public long getWorkforce() {
		      return workforce;
	    }
	    
	    public Long getWorkforceCollege() {
		      return workforceCollege;
		    }
	    
	    public long getWorkforceLessThanCollege() {
	      return workforceLessThanCollege;
	    }
	    public boolean isSuccessful() {
	    	return success;
	    }
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
		
		AnalystBatchRequest(Integer p, Integer c, String m, Integer tl) {
			page = p;
			pageCount = c;
			mode = m;
			timeLimit = tl;
			
		}
	}
		
}
