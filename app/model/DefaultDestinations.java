package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opentripplanner.analyst.core.Sample;

import controllers.Api;

public class DefaultDestinations {
	
	public HashMap<String, Sample> destinations = new HashMap<String, Sample>();
	
	public DefaultDestinations() {
	
		for(IndicatorItem item : Api.analyst.getIndicatorsById("jobs_type")) {
			if(!destinations.containsKey(item.geoId))
				destinations.put(item.geoId, item.sample);
		}

	}
 
}
