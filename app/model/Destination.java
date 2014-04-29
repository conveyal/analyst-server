package model;

import java.util.HashMap;

import org.opentripplanner.analyst.core.Sample;

import com.vividsolutions.jts.geom.Point;

public class Destination {

	final public String id;
	final public Point point;
	HashMap<String, Sample> samples = new HashMap<String, Sample>();
	
	public Destination(String i, Point p) {
		id = i;
		point = p;
	}
	
	public void addSample(String graphId, Sample sample) {
	
		samples.put(graphId, sample);
	}
	
	public Sample getSample(String graphId) {
		return samples.get(graphId);
	}
}
