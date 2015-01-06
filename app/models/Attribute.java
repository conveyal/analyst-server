package models;

import java.io.Serializable;

public class Attribute implements Serializable {

	private static final long serialVersionUID = 2L;

	public String name;
	public String description;
	public String color;
	
	public Boolean hide = false;
	
	public Boolean numeric = false;
	
	public Double min = 0.0;
	public Double max = 0.0;
	public Double sum = 0.0;      // sum of numeric values
	public Integer count = 0;    // total count of featuers
	public Integer nanCount = 0; // count of features with null/NaN values
	
	public String fieldName;
	
	public Attribute() {
		
	}
	
	public void updateStats(Object f) {
		
		if(f instanceof Number && f != null) {
			Double fCast;
			
			if(f instanceof Double)
				fCast = (double)f; // cast numeric values to double;
			else if(f instanceof Float)
				fCast = (double)(float)f;
			else if(f instanceof Long)
				fCast = (double)((long)f);
			else
				fCast = (double)((int)f);
				
			if(fCast < min)
				min = fCast;
			if(fCast > max)
				max = fCast;
		
			sum += fCast;
		}
		else
			nanCount++;
		
		count++;
	}
	
	/**
	 * Sanitize a name for use as a category ID. Also implemented in the client:
	 * A.models.Shapefile.getCategoryName()
	 */
	static String convertNameToId(String name) {
		return name.toLowerCase().trim().replaceAll(" ", "_").replaceAll("\\W","");
	}
	
}
