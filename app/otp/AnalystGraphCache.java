package otp;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;

import play.Logger;
import play.Play;
import play.libs.Akka;
import play.libs.Json;
import play.libs.F.Function;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Result;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;

import com.conveyal.otpac.ClusterGraphService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import controllers.Api;
import controllers.Application;

public class AnalystGraphCache extends CacheLoader<String, Graph> {
	 private LoadingCache<String, Graph> graphCache;
	 
	 private HashSet<String> graphsBuilding = new HashSet<String>();
	 /** Graphs that failed to build */
	 private HashSet<String> graphsInError = new HashSet<String>();

	 private int size = 200;
	 private int concurrency = 4;
	 
	 public AnalystGraphCache() {
		 
		 this.graphCache = CacheBuilder.newBuilder()
				 .concurrencyLevel(concurrency)
				 .maximumSize(size)
				 .build(this);
		 
	 }
	 
	 public Graph get(String graphId) {
		try {
			return graphCache.get(graphId);
		} catch (ExecutionException e) {
			return null;
		}
	 }
	 
	 public String getStatus(String graphId) {
		 if(this.graphCache.asMap().containsKey(graphId)) {
			 return "BUILT";
		 }
		 else {
			 if (graphsInError.contains(graphId))
			 	return "ERROR";
			 else if (graphsBuilding.contains(graphId))
				 return "BUILDING_GRAPH";
			 else
				 return "UNBUILT";
		 }
			 
	 }
	 
	 public Graph getIfPresent(final String graphId) {
			try {
				Akka.system().scheduler().scheduleOnce(
				        Duration.create(0, TimeUnit.MILLISECONDS),
				        new Runnable() {
				            public void run() {
				            	try {
									graphCache.get(graphId);
								} catch (ExecutionException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
				            }
				        },
				        Akka.system().dispatcher()
				);
				return graphCache.get(graphId);
				
			} catch (ExecutionException e) {
				return null;
			}
		 }
	 
	 public void put(String graphId, Graph graph) {
		 graphCache.put(graphId, graph);
	 }
	 
	 public Set<String> keySet() {
		 return graphCache.asMap().keySet();
	 }
	 
	 public void clear() {
		 graphCache.invalidateAll();
	 }
	 
	 public Collection<Graph> values() {
		 return graphCache.asMap().values();
	 }
	 
	 public void remove(String graphId) {
		 graphCache.invalidate(graphId);
	 }
	 
	 @Override
	 public Graph load(final String graphId) throws Exception {
		 
    	 graphsBuilding.add(graphId);
    	  
    	 Graph g;
    	 try {
	    	 GraphBuilderTask gbt = AnalystGraphBuilder.createBuilder(new File(new File(Application.dataPath,"graphs"), graphId));
	 		 gbt.run();
	 		 
	 		 g = gbt.getGraph();
	 		 g.routerId = graphId;
	 		 
	 		 g.index(new DefaultStreetVertexIndexFactory());
    	 } catch (Exception e) {
    		 // catch, clean up state, and rethrow to let the caller worry about it.
    		 
    		 Logger.error("Exception building graph " + graphId);

    		 e.printStackTrace();
    		 graphsInError.add(graphId);
    		 graphsBuilding.remove(graphId);
    		 
    		 // rethrow
    		 throw e;
    	 }
 		 
 		 graphsBuilding.remove(graphId);
 		 
 		 // put the graph into the cluster cache and s3 if necessary
         String s3cred = Play.application().configuration().getString("cluster.s3credentials");
 		 Boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline");
 		 String bucket = Play.application().configuration().getString("cluster.graphs-bucket");
 		
 		 ClusterGraphService cgs = new ClusterGraphService(s3cred, workOffline, bucket);
 		 
 		 cgs.addGraphFile(Api.analyst.getZippedGraph(graphId));
 	
		 return g;
	 }	 
}
