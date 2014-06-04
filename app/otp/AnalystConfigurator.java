package otp;



import org.opentripplanner.routing.algorithm.EarliestArrivalSPTService;

import org.opentripplanner.analyst.core.GeometryIndex;
import org.opentripplanner.analyst.core.SampleSource;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.impl.DefaultRemainingWeightHeuristicFactoryImpl;
import org.opentripplanner.routing.impl.GraphServiceImpl;
import org.opentripplanner.routing.impl.RetryingPathServiceImpl;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.PathService;
import org.opentripplanner.routing.services.RemainingWeightHeuristicFactory;
import org.opentripplanner.routing.services.SPTService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        

        RetryingPathServiceImpl pathService = new RetryingPathServiceImpl(getGraphService(),  getSptService());
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
         
         graphService.setPath("data/graph/ba/");
         
         graphService.startup();
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
