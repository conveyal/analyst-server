package otp;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opentripplanner.analyst.core.GeometryIndex;
import org.opentripplanner.analyst.core.SampleSource;
import org.opentripplanner.analyst.request.Renderer;
import org.opentripplanner.analyst.request.SPTCache;
import org.opentripplanner.analyst.request.SampleFactory;
import org.opentripplanner.analyst.request.TileCache;
import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.graph_builder.impl.EmbeddedConfigGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.GtfsGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.PruneFloatingIslands;
import org.opentripplanner.graph_builder.impl.StreetlessStopLinker;
import org.opentripplanner.graph_builder.impl.TransitToStreetNetworkGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.ned.NEDGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.ned.NEDGridCoverageFactoryImpl;
import org.opentripplanner.graph_builder.impl.osm.DefaultWayPropertySetSource;
import org.opentripplanner.graph_builder.impl.osm.OpenStreetMapGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.transit_index.TransitIndexBuilder;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.graph_builder.services.GraphBuilderWithGtfsDao;
import org.opentripplanner.graph_builder.services.ned.NEDGridCoverageFactory;
import org.opentripplanner.openstreetmap.impl.AnyFileBasedOpenStreetMapProviderImpl;
import org.opentripplanner.openstreetmap.services.OpenStreetMapProvider;
import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;
import org.opentripplanner.routing.algorithm.GenericAStar;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.impl.DefaultFareServiceFactory;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultRemainingWeightHeuristicFactoryImpl;
import org.opentripplanner.routing.impl.GraphServiceBeanImpl;
import org.opentripplanner.routing.impl.GraphServiceImpl;
import org.opentripplanner.routing.impl.RetryingPathServiceImpl;
import org.opentripplanner.routing.impl.LongDistancePathService;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.PathService;
import org.opentripplanner.routing.services.RemainingWeightHeuristicFactory;
import org.opentripplanner.routing.services.SPTService;
import org.opentripplanner.visualizer.GraphVisualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class AnalystConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(AnalystConfigurator.class);
     
    private GraphService graphService = null;
    private SPTService sptService = null;
    
    private AnalystProviderFactory componentProviderFactory = null;
    
    public AnalystConfigurator () {
      
    }

  /*
     * We could even do this at Configurator construct time (rather than lazy initializing), using 
     * the inMemory param to create the right kind of graphservice ahead of time. However that 
     * would create indexes even when only a build was going to happen. 
     */
    public AnalystProviderFactory getComponentProviderFactory() {
        
        if (componentProviderFactory != null)
            return componentProviderFactory;
        
        LOG.info("Wiring up and configuring server task.");
        
        
        // Core OTP modules
        AnalystProviderFactory cpf = new AnalystProviderFactory(); 
        cpf.bind(GraphService.class, getGraphService());
        cpf.bind(RoutingRequest.class);
        cpf.bind(SPTService.class, getSptService());
        
        cpf.bind(GeometryIndex.class);
        cpf.bind(SampleFactory.class);
        
        RetryingPathServiceImpl pathService = new RetryingPathServiceImpl();
        pathService.setFirstPathTimeout(10.0);
        pathService.setMultiPathTimeout(1.0);
        cpf.bind(PathService.class, pathService);
        cpf.bind(RemainingWeightHeuristicFactory.class, 
                new DefaultRemainingWeightHeuristicFactoryImpl()); 
        
        // Perform field injection on bound instances and call post-construct methods
        cpf.doneBinding();   
        
        this.componentProviderFactory = cpf;
        return cpf;         
        
    }

    /** Create a cached GraphService that will be shared between all OTP components. */
    public void makeGraphService() {
    	 GraphServiceImpl graphService = new GraphServiceImpl();
         
         graphService.setPath("data/graphs/");
         
         List<String> graphIds = new ArrayList<String>();
         
         File graphsPath = new File("data/graphs/");
         
         for(File graph : graphsPath.listFiles()) {
        	 if(graph.isDirectory()){
        		 graphIds.add(graph.getName());
        	 }
         }
         
         if(graphIds.size() == 0)
        	 System.out.println("No graphs found in " + "data/graphs/");
        	 
         if (graphIds.size() > 0) {
             graphService.setDefaultRouterId(graphIds.get(0));
             
         }
         
         graphService.setAutoRegister(graphIds);
         
         this.graphService = graphService;
    }

    /** Return the cached, shared GraphService, making one as needed. */
    public GraphService getGraphService () {
        if (graphService == null)
            makeGraphService();
        return graphService;
    }
    
    public SPTService getSptService() {
        if (sptService == null)
            sptService =  new EarliestArrivalSPTService();
        return sptService;
    }
    
  

}
