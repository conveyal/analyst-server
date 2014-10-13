import java.io.IOException;

import models.Scenario;
import models.User;
import controllers.Api;
import play.Application;
import play.GlobalSettings;
import play.api.mvc.EssentialFilter;

public class Global extends GlobalSettings {
  
	@Override
	public void onStart(Application app) {
		try {
			Scenario.buildAll();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
	}  

}
