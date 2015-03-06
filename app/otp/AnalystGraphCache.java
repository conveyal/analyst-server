package otp;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;

import play.Logger;
import play.Play;
import play.libs.Akka;
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
	 /** Graphs that are currently being uploaded to S3 */
	 private HashSet<String> graphsUploading = new HashSet<String>();

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
			 else if (graphsUploading.contains(graphId))
				 return "UPLOADING_GRAPH";
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

		 Boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline");

		 graphsUploading.add(graphId);

		 // put the graph into the cluster cache and s3 if necessary
		 String s3cred = Play.application().configuration().getString("cluster.s3credentials");
		 String bucket = Play.application().configuration().getString("cluster.graphs-bucket");

		 ClusterGraphService cgs = new ClusterGraphService(s3cred, workOffline, bucket);

		 Logger.info("preparing to upload graph " + graphId);
		 File zippedGraph = Api.analyst.getZippedGraph(graphId);

		 Logger.info("uploading graph " + graphId);
		 cgs.addGraphFile(zippedGraph);

		 graphsUploading.remove(graphId);
		

 		 Logger.info("building graph " + graphId);
 		 
    	 graphsBuilding.add(graphId);
    	  
    	 Graph g;
    	 try {
	    	 GraphBuilder gb = AnalystGraphBuilder.createBuilder(new File(new File(Application.dataPath,"graphs"), graphId));
	 		 gb.run();
	 		 
	 		 g = gb.getGraph();
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
		 return g;
	 }	 
}
