package model;

import java.util.ArrayList;

import java.util.Collections;
import java.util.List;

import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class AttributeStats {
	
	public String id;
	
	public Long count = 0l;
	public Long total = 0l;
	
	public List<String> timeBreaks = new ArrayList<String>();
	public List<Long> timeBreakValues = new ArrayList<Long>();
	
	public List<String> valueBreaks = new ArrayList<String>();
	public List<Double> valueBreakValues = new ArrayList<Double>();

	public AttributeStats(String attributeId, List<Long> attributeValues) {
	
		id = attributeId;

		ArrayList<Long> values = new ArrayList<Long>();

		for(Long value : attributeValues) {
			
			if(value != null) {
				
					values.add(value);

				count++;
				total += value;
			}
		}

		Collections.sort(values);
			
		if(values.size() >= 4) {
			valueBreaks.add("25%");
			values.add(values.get((int) (values.size()*0.25)));
			
			valueBreaks.add("75%");
			values.add(values.get((int) (values.size()*0.75)));
		}
		
		if(values.size() >= 2) {
			valueBreaks.add("50%");
			values.add(values.get((int) (values.size()*0.5)));
			
			valueBreaks.add("100%");
			values.add(values.get((int) (values.size()*1.0)-1));
		}

	}
}
