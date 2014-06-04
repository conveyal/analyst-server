package model;

import com.vividsolutions.jts.geom.Envelope;

public class Metadata {
	
	public Double lat;
	public Double lon;
	
	public Long maxTimeLimit;

	
	public Metadata(Envelope e, Long maxTimeLimit) {
		lat = e.centre().y;
		lon = e.centre().x;
		
		this.maxTimeLimit = maxTimeLimit;
	}

}
