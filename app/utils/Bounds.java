package utils;

import java.io.Serializable;

import org.geotools.geometry.Envelope2D;

import com.vividsolutions.jts.geom.Envelope;

public class Bounds implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public Double west, east, south, north;
	
	public Bounds() {
		
	}
	
	public Bounds(Envelope2D envelope) {
		west = envelope.getMinX();
    	east = envelope.getMaxX();
    	south = envelope.getMinY();
    	north = envelope.getMaxY();
	}
	
	public Bounds(Envelope envelope) {
		west = envelope.getMinX();
    	east = envelope.getMaxX();
    	south = envelope.getMinY();
    	north = envelope.getMaxY();
	}

}
