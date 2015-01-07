package otp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.graph_builder.impl.EmbeddedConfigGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.GtfsGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.DirectTransferGenerator;
import org.opentripplanner.graph_builder.impl.PruneFloatingIslands;
import org.opentripplanner.graph_builder.impl.TransitToStreetNetworkGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.ned.ElevationGraphBuilderImpl;
import org.opentripplanner.graph_builder.impl.ned.NEDGridCoverageFactoryImpl;
import org.opentripplanner.graph_builder.impl.osm.DefaultWayPropertySetSource;
import org.opentripplanner.graph_builder.impl.osm.OpenStreetMapGraphBuilderImpl;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.graph_builder.services.ned.ElevationGridCoverageFactory;
import org.opentripplanner.openstreetmap.impl.AnyFileBasedOpenStreetMapProviderImpl;
import org.opentripplanner.openstreetmap.services.OpenStreetMapProvider;
import org.opentripplanner.routing.impl.DefaultFareServiceFactory;

import org.opentripplanner.standalone.CommandLineParameters;
import org.opentripplanner.standalone.OTPConfigurator;
import play.Logger;

import com.google.common.collect.Lists;

public class AnalystGraphBuilder {

	public static GraphBuilderTask createBuilder(File dir) {
       
        GraphBuilderTask graphBuilder = new GraphBuilderTask();
        List<File> gtfsFiles = Lists.newArrayList();
        List<File> osmFiles =  Lists.newArrayList();
        File configFile = null;
        /* For now this is adding files from all directories listed, rather than building multiple graphs. */
       
        if ( !dir.isDirectory() && dir.canRead()) {
            return null;
        }
        
        graphBuilder.setPath(dir);
        for (File file : dir.listFiles()) {
            switch (InputFileType.forFile(file)) {
            case GTFS:
                Logger.info("Found GTFS file {}", file);
                gtfsFiles.add(file);
                break;
            case OSM:
            	Logger.info("Found OSM file {}", file);
                osmFiles.add(file);
                break;
            case OTHER:
            	Logger.debug("Skipping file '{}'", file);
            }
        }
        
        boolean hasOSM  = ! (osmFiles.isEmpty());
        boolean hasGTFS = ! (gtfsFiles.isEmpty());
        
        if ( ! (hasOSM || hasGTFS )) {
            Logger.error("Found no input files from which to build a graph in {}", dir.toString());
            return null;
        }
        
        if ( hasOSM ) {
            List<OpenStreetMapProvider> osmProviders = Lists.newArrayList();
            for (File osmFile : osmFiles) {
                OpenStreetMapProvider osmProvider = new AnyFileBasedOpenStreetMapProviderImpl(osmFile);
                osmProviders.add(osmProvider);
            }
            OpenStreetMapGraphBuilderImpl osmBuilder = new OpenStreetMapGraphBuilderImpl(osmProviders); 
            DefaultWayPropertySetSource defaultWayPropertySetSource = new DefaultWayPropertySetSource();
            osmBuilder.setDefaultWayPropertySetSource(defaultWayPropertySetSource);
            osmBuilder.skipVisibility = false;
            graphBuilder.addGraphBuilder(osmBuilder);
            graphBuilder.addGraphBuilder(new PruneFloatingIslands());
            graphBuilder.addGraphBuilder(new DirectTransferGenerator());

        }
        if ( hasGTFS ) {
            List<GtfsBundle> gtfsBundles = Lists.newArrayList();
            for (File gtfsFile : gtfsFiles) {
                GtfsBundle gtfsBundle = new GtfsBundle(gtfsFile);
                gtfsBundle.setTransfersTxtDefinesStationPaths(false);
                gtfsBundle.linkStopsToParentStations = false;
                gtfsBundle.parentStationTransfers =false;
                gtfsBundles.add(gtfsBundle);
            }
            GtfsGraphBuilderImpl gtfsBuilder = new GtfsGraphBuilderImpl(gtfsBundles);
            graphBuilder.addGraphBuilder(gtfsBuilder);
            
            graphBuilder.addGraphBuilder(new TransitToStreetNetworkGraphBuilderImpl());

            

        }
        graphBuilder.serializeGraph = false;

        CommandLineParameters params = new CommandLineParameters();
        params.build = new ArrayList<File>(1);
        params.build.add(dir);
        params.inMemory = true;
        params.longDistance = true;
        graphBuilder = new OTPConfigurator(params).builderFromParameters();


        return graphBuilder;
    }
	
	 private static enum InputFileType {
	        GTFS, OSM, CONFIG, OTHER;
	        public static InputFileType forFile(File file) {
	            String name = file.getName();
	            if (name.endsWith(".zip")) {
	                try {
	                    ZipFile zip = new ZipFile(file);
	                    ZipEntry stopTimesEntry = zip.getEntry("stop_times.txt");
	                    zip.close();
	                    if (stopTimesEntry != null) return GTFS;
	                } catch (Exception e) { /* fall through */ }
	            }
	            if (name.endsWith(".pbf")) return OSM;
	            if (name.endsWith(".osm")) return OSM;
	            if (name.endsWith(".osm.xml")) return OSM;
	            if (name.equals("Embed.properties")) return CONFIG;
	            return OTHER;
	        }
	    }
}
