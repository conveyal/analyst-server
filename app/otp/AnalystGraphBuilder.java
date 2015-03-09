package otp;

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.standalone.CommandLineParameters;

public class AnalystGraphBuilder {

	public static GraphBuilder createBuilder(File dir) {

        CommandLineParameters params = new CommandLineParameters();
        params.build = dir;
        params.inMemory = true;

        return GraphBuilder.forDirectory(params, dir);
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
