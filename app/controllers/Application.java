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
import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.routing.graph.Graph;

import otp.AnalystGraphBuilder;

import com.vividsolutions.jts.geom.Geometry;

import play.*;
import play.mvc.*;
import views.html.*;

public class Application extends Controller {

	public static final String dataPath = Play.application().configuration().getString("application.data");
	
	public static Result index() throws IOException  {

		
		/*try {
			Api.analyst.createJob();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		return ok(index.render());
		
    }
	
	final static jsmessages.JsMessages messages = jsmessages.JsMessages.create(play.Play.application());

	public static Result jsMessages() {
	    return ok(messages.generate("window.Messages"));
	}
}
