package models;

import java.io.Serializable;

public class Attribute implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;
	public String description;
	public String color;
	
	public String fieldName;
	
	public Attribute() {
		
	}
	
	static String convertNameToId(String name) {
		return name.toLowerCase().trim().replaceAll(" ", "_").replaceAll("\\W","");
	}
	
}
