package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class IndicatorSummary {

	public String id;
	public Long count = 0l;
	public Long total = 0l;
	
	public HashMap<String, AttributeStats> attributes = new HashMap<String, AttributeStats>();
	
	public IndicatorSummary(String indicatorId, List<IndicatorItem> items) {

		id = indicatorId;
		
		HashMap<String, ArrayList<Long>> attributeValues = new HashMap<String, ArrayList<Long>>();
		
		if(items == null)
			return;
		
		for(IndicatorItem item : items) {
	
			count++;
			
			for(String attribute :item.attributes.keySet()) {
				
				if(!attributeValues.containsKey(attribute)) {
					attributeValues.put(attribute, new ArrayList<Long>());
				}
				
				attributeValues.get(attribute).add(item.attributes.get(attribute));	
			}
		}
		
		for(String attributeId : attributeValues.keySet()) {
			
			AttributeStats stats = new AttributeStats(attributeId, attributeValues.get(attributeId));
			
			total += stats.total;
			
			attributes.put(attributeId, stats);		
		}
	}	
}
