package otp;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.routing.graph.Graph;

import controllers.Application;


public class AnalystGraphService  { 

	private ConcurrentHashMap<String,Graph> graphMap = new ConcurrentHashMap<String,Graph>();
	private ConcurrentHashMap<String,SampleFactory> sampleMap = new ConcurrentHashMap<String,SampleFactory>();
	
	public synchronized Graph getGraph(String graphId) {
		
		if(!graphMap.containsKey(graphId)) {
			
			GraphBuilderTask gbt = AnalystGraphBuilder.createBuilder(new File(new File(Application.dataPath,"graphs"), graphId));
			
			gbt.run();
			
			Graph g = gbt.getGraph();
			
			GeometryIndex geomIndex = new GeometryIndex(g);
			
			SampleFactory sampleSource = new SampleFactory(geomIndex);
			
			graphMap.put(graphId,g);
			sampleMap.put(graphId,sampleSource);
			
		}
		
		return graphMap.get(graphId);
	}
	
	public SampleFactory getSampleFactory(String graphId) {
		
		if(!graphMap.containsKey(graphId)) {
			getGraph(graphId);
		}
		return sampleMap.get(graphId);
		
	}
	
	
}
