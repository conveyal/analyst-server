package models;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Attribute implements Serializable {

	private static final long serialVersionUID = 2L;

	/** cache attribute name -> attribute ID; doing the string manipulations is a hot spot */
	private static Map<String, String> attributeIdCache = new HashMap<>();

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
	public static String convertNameToId(String name) {
		if (attributeIdCache.containsKey(name))
			return attributeIdCache.get(name);

		else {
			synchronized (attributeIdCache) {
				String id = name.toLowerCase().trim().replaceAll("[^0-9a-zA-Z]", "_");
				attributeIdCache.put(name, id);
				return id;
			}
		}
	}
}
