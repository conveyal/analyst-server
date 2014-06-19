package otp;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

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
			
			GeometryIndex geomIndex = new GeometryIndex(g);
			
			SampleFactory sampleSource = new SampleFactory(geomIndex);
			
			graphMap.put(graphId,g);
					
		}
		
		return graphMap.get(graphId);
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
