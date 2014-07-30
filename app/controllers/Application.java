package controllers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import models.Project;
import models.Shapefile;
import models.SpatialLayer;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.mapdb.DBMaker;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opentripplanner.routing.graph.Graph;

import com.conveyal.otpac.ClusterGraphService;
import com.conveyal.otpac.JobItemCallback;
import com.conveyal.otpac.message.JobSpec;
import com.conveyal.otpac.message.WorkResult;
import com.conveyal.otpac.standalone.StandaloneCluster;
import com.conveyal.otpac.standalone.StandaloneExecutive;
import com.conveyal.otpac.standalone.StandaloneWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.geom.Geometry;

import play.*;
import play.mvc.*;
import views.html.*;

public class Application extends Controller {

	public static final String dataPath = Play.application().configuration().getString("application.data");
		
	public static Result cluster(String pointSetId, String graphId) throws Exception  {
		
		SpatialLayer sl = SpatialLayer.getPointSetCategory(pointSetId);
		sl.writeToClusterCache(true);
		
		StandaloneCluster cluster = new StandaloneCluster("s3credentials", true, Api.analyst.getGraphService());

		StandaloneExecutive exec = cluster.createExecutive();
		StandaloneWorker worker = cluster.createWorker();
		
		cluster.registerWorker(exec, worker);
		
		JobSpec js = new JobSpec(graphId, pointSetId + ".json",  pointSetId + ".json", "2014-06-09", "8:05 AM", "America/New York");
		
		// plus a callback that registers how many work items have returned
		class CounterCallback implements JobItemCallback {
			int jobsBack = 0;
			String jsonBack = null;

			@Override
			public synchronized void onWorkResult(WorkResult res) {
				try {
					Logger.info(res.toJsonString());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				jobsBack += 1;
			}
		}
		
		CounterCallback callback = new CounterCallback();
		js.setCallback(callback);

		// start the job
		exec.find(js);

		// stall until a work item returns
		while (callback.jobsBack < 1000) {
			Thread.sleep(100);
		}
		
		cluster.stop(worker);

		
		return ok();
    }
	
	final static jsmessages.JsMessages messages = jsmessages.JsMessages.create(play.Play.application());

	public static Result index() throws IOException  {
		return ok(index.render());	
    }
	
	public static Result jsMessages() {
	    return ok(messages.generate("window.Messages"));
	}
}
