package otp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Graph.LoadLevel;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.GraphSource;
import org.opentripplanner.routing.services.GraphSource.Factory;

import controllers.Application;


public class AnalystGraphService implements GraphService { 
	AnalystGraphCache graphCache = new AnalystGraphCache();
	
	public Graph getGraph(String graphId) {
		return graphCache.get(graphId);
	}
	
	public String getGraphStatus(String graphId) {
		return graphCache.getStatus(graphId);
	}
	
	public synchronized File getZippedGraph(String graphId) throws IOException {
		
		File graphDataDir = new File(new File(Application.dataPath,"graphs"), graphId);
		
		File graphZipFile = new File(new File(Application.dataPath,"graphs"), graphId + ".zip");
		
		if(graphDataDir.exists() && graphDataDir.isDirectory()) {
			
			FileOutputStream fileOutputStream = new FileOutputStream(graphZipFile);
			ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
			
			byte[] buffer = new byte[1024];
			
			for(File f : graphDataDir.listFiles()) {
				ZipEntry zipEntry = new ZipEntry(f.getName());
				zipOutputStream.putNextEntry(zipEntry);
	    		FileInputStream fileInput = new FileInputStream(f);

	    		int len;
	    		while ((len = fileInput.read(buffer)) > 0) {
	    			zipOutputStream.write(buffer, 0, len);
	    		}
	 
	    		fileInput.close();
	    		zipOutputStream.closeEntry();
			}
			
			zipOutputStream.close();
			return graphZipFile;		
		}	
		return null;
	}

	@Override
	public int evictAll() {
		graphCache.clear();
		return 0;
	}

	@Override
	public boolean evictGraph(String graphId) {
		graphCache.remove(graphId);
		return false;
	}

	@Override
	public Graph getGraph() {
		if(graphCache.values().size() > 0)
			return graphCache.values().iterator().next();
		return null;
	}

	@Override
	public Collection<String> getRouterIds() {
		return graphCache.keySet();
	}


	public boolean registerGraph(String graphId, GraphSource gs) {
		graphCache.put(graphId, gs.getGraph());
		return true;
	}


	public boolean registerGraph(String arg0, Graph arg1) {
		graphCache.put(arg0, arg1);
		return  true;
	}
	
    public void setDefaultRouterId (String routerId) {
    	// do nothing
    }

	@Override
	public Factory getGraphSourceFactory() {
		return null;
	}

	@Override
	public boolean reloadGraphs(boolean arg0) {
		return false;
	}
}
