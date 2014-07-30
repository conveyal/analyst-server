package otp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Graph.LoadLevel;
import org.opentripplanner.routing.services.GraphService;

import controllers.Application;


public class AnalystGraphService implements GraphService { 

	private ConcurrentHashMap<String,Graph> graphMap = new ConcurrentHashMap<String,Graph>();

	public synchronized Graph getGraph(String graphId) {
		
		if(!graphMap.containsKey(graphId)) {
			
			GraphBuilderTask gbt = AnalystGraphBuilder.createBuilder(new File(new File(Application.dataPath,"graphs"), graphId));
			
			gbt.run();
			
			Graph g = gbt.getGraph();
			
			g.setRouterId(graphId);
			
			graphMap.put(graphId,g);
					
		}
		
		return graphMap.get(graphId);
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
		graphMap.clear();
		return 0;
	}

	@Override
	public boolean evictGraph(String graphId) {
		graphMap.remove(graphId);
		return false;
	}

	@Override
	public Graph getGraph() {
		if(graphMap.values().size() > 0)
			return graphMap.values().iterator().next();
		return null;
	}

	@Override
	public Collection<String> getRouterIds() {
		return graphMap.keySet();
	}

	@Override
	public boolean registerGraph(String graphId, boolean arg1) {
		// TODO Auto-generated method stub
		return graphMap.get(graphId) != null;
	}

	@Override
	public boolean registerGraph(String arg0, Graph arg1) {
		graphMap.put(arg0,  arg1);
		return  true;
	}

	@Override
	public boolean reloadGraphs(boolean arg0) {
		return false;
	}

	@Override
	public boolean save(String arg0, InputStream arg1) {
		return false;
	}

	@Override
	public void setLoadLevel(LoadLevel arg0) {
	}
	
	
}
