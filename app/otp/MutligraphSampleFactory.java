package otp;

import java.util.HashMap;

import model.MultigraphSample;

import org.opentripplanner.analyst.core.Sample;

public class MutligraphSampleFactory {
	
	private HashMap<String, SampleFactory> graphSampleFactories = new HashMap<String, SampleFactory>();

	
	public void addSampleFactory(String id, SampleFactory sf){
		graphSampleFactories.put(id, sf);	
	}
	
	public Sample getSample(String id, double lon, double lat) {
		return graphSampleFactories.get(id).getSample(lon, lat);
	}
	
	public MultigraphSample getMultigraphSample(double lon, double lat) {
	
		MultigraphSample multiSample = new MultigraphSample();
		
		for(String id : graphSampleFactories.keySet()) {
			Sample s = graphSampleFactories.get(id).getSample(lon, lat);
			multiSample.addSample(id, s);
		}
		
		return multiSample;
	}
}


