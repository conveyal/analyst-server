import models.Bundle;
import models.Query;
import models.Shapefile;
import play.Application;
import play.GlobalSettings;

import java.io.IOException;

public class Global extends GlobalSettings {
  
	@Override
	public void onStart(Application app) {
		Bundle.importBundlesAsNeeded();			
		
		// upload to S3
		try {
			Bundle.writeAllToClusterCache();
			Shapefile.writeAllToClusterCache();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		for (Query q : Query.getAll()) {
			if (q.completePoints == null || !q.completePoints.equals(q.totalPoints)) {
				// TODO accumulate results
			}
		}
	}
}
