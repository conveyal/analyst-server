import java.io.IOException;

import models.Scenario;
import models.User;
import controllers.Api;
import play.Application;
import play.GlobalSettings;
import play.api.mvc.EssentialFilter;
import utils.Cluster;

public class Global extends GlobalSettings {
  
	@Override
	public void onStart(Application app) {
		// start up the Akka server
		Cluster.getExecutive();
		
		try {
			Scenario.buildAll();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}  

}
