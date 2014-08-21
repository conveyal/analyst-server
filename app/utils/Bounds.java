package utils;

import java.io.Serializable;

import org.geotools.geometry.Envelope2D;

public class Bounds implements Serializable {

	public Double west, east, south, north;

	public Bounds(Envelope2D envelope) {
		west = envelope.getMinX();
    	east = envelope.getMaxX();
    	south = envelope.getMinY();
    	north = envelope.getMaxY();
	}

}
