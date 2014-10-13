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
import models.User;

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
	public static final String binPath = Play.application().configuration().getString("application.bin");

	final static jsmessages.JsMessages messages = jsmessages.JsMessages.create(play.Play.application());

	@Security.Authenticated(Secured.class)
	public static Result index() throws IOException  {
		return ok(index.render());	
    }

	public static Result login() throws IOException  {
		return ok(login.render());	
    }
	
	public static Result doLogin() throws IOException  {
	
		String username = request().body().asFormUrlEncoded().get("username")[0];
		String password = request().body().asFormUrlEncoded().get("password")[0];
		
		User user = User.getUserByUsername(username);
		
		if(user == null)
			return notFound();
		
		if(user.checkPassword(password)) {
			session().put("username", user.username);
			return ok();
		}
		else {
			return unauthorized();
		}

	}
	
	public static Result logout() throws IOException  {
		session().clear();
		return redirect(routes.Application.login());	
    }
	
	public static Result jsMessages() {
	    return ok(messages.generate("window.Messages"));
	}
}
