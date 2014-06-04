package model;

import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

public class HaltonPoints {
		
	private Coordinate[] boundaryPoints;
	
	private int numPoints; 
	
	private double[] coords;

	public HaltonPoints(Geometry geom, Integer numberPoints) {
		
		numPoints = numberPoints;
		
		coords = new double[numberPoints*2];
		
		boundaryPoints = geom.getCoordinates();
		
		if(numPoints > 0) {
				
			Envelope env = geom.getEnvelopeInternal();
			
			int basei = 2;
			int basej = 3;
			
			double baseX = env.getMinX();
			double baseY = env.getMinY();
		    
			int i = 0;
			int j = (int) (Integer.MAX_VALUE * Math.random());
			
			if(j + coords.length > Integer.MAX_VALUE) 
				j = j - coords.length;
			
			while (i < coords.length) {
				
				double x = baseX + env.getWidth() * haltonNumber(j + 1, basei);
				double y = baseY + env.getHeight() * haltonNumber(j + 1, basej);
			
				j++;
				
				if (!contains(x, y))
					continue;
				
				//packed coords
				coords[i] = x;
				coords[i+1] = y;
				
				i += 2;
			}	
		}
	}
	  
	public int getNumPoints() {
		return numPoints;
	}
	
	
	public double[] transformPoints(MathTransform transform) {
	
		double[] transformedCoords = new double[coords.length];
		
		try {
			transform.transform(coords, 0, transformedCoords, 0, coords.length/2);
		} catch (TransformException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return transformedCoords;
	}
	
	private double haltonNumber(int index, int base) {
		double result = 0;
		double fraction = 1.0 / base;
		int i = index;
		while (i > 0) {
			result = result + fraction * (i % base);
			i = (int) Math.floor(i / (double) base);
			fraction = fraction / base;
		}
		return result;
	}
	
	public boolean contains(double x, double y) {
		int i;
		int j;
		boolean result = false;
		for (i = 0, j = boundaryPoints.length - 1; i < boundaryPoints.length; j = i++) {
			if ((boundaryPoints[i].y > y) != (boundaryPoints[j].y > y) &&
				(x < (boundaryPoints[j].x - boundaryPoints[i].x) * (y - boundaryPoints[i].y) / (boundaryPoints[j].y-boundaryPoints[i].y) + boundaryPoints[i].x)) {
				result = !result;
				}
		}
		return result;
    }

	
}
