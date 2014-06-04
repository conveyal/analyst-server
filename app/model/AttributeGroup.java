package model;

import java.util.ArrayList;

public class AttributeGroup implements Comparable<AttributeGroup> {
	
	public String name;
	public String id;
	public String color;

	
	public ArrayList<String> attributes = new ArrayList<String>();
	
	public AttributeGroup(String [] data) {
		
		name = data[0];
		
		id = data[1];
		
		for(int i = 2; i < data.length; i++) {
			attributes.add(data[i]);
		}
	}	
	
	public AttributeGroup() {
		
	}

		@Override
	public int compareTo(AttributeGroup o) {
		// TODO Auto-generated method stub
		return this.name.compareTo(o.name);
	}

	
}
