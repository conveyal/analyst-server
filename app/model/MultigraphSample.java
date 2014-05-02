package model;

import java.util.HashMap;

import org.opentripplanner.analyst.core.Sample;

public class MultigraphSample {
	
	private HashMap<String, Sample> samples = new HashMap<String, Sample>();
	
	public void addSample(String id, Sample s) {
		samples.put(id, s);
	}
	
	public Sample getSample(String id) {
		return samples.get(id);
	}
	
	public int getSampleCount() {
		return samples.values().size();
	}

}
