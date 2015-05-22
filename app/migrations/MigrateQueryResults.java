package migrations;

import java.io.File;

import utils.DataStore;
import utils.QueryResultStore;
import utils.ResultEnvelope;

/** Migrate query results from the old, slow storage format to the new, slightly faster, storage format */
public class MigrateQueryResults {
	/** usage: MigrateQueryResults directory */ 
	public static void main (String... args) {
		File directory = new File(args[0]);
		
		if (!directory.exists() || !directory.isDirectory()) {
			System.err.println("usage: MigrateQueryResults data_directory");
			return;
		}
		
		File newResultsDir = new File(directory, "query_results");
		newResultsDir.mkdirs();
		
		for (File file : new File(directory, "results").listFiles()) {
			if (!file.getName().endsWith(".db"))
				continue;
			
			System.err.println("Processing file " + file.getName());
			
			String newDbName = file.getName().replaceFirst("^r_", "");
			
			File newDb = new File(newResultsDir, newDbName);
			
			/*QueryResultStore store = new QueryResultStore(newDb, false);
			
			DataStore<ResultEnvelope> oldResults = new DataStore<ResultEnvelope>(file.getParentFile(), file.getName().replaceFirst(".db$", ""), false, true, false);
			
			for (ResultEnvelope env : oldResults.getAll()) {
				store.store(env);
			}
			
			store.close();*/
		}
	}
}
