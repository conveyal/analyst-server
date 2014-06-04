package model;

import java.util.ArrayList;
import java.util.List;

import controllers.Api;

public class IndicatorResponse {

	public String sptId;
	public Integer timeLimit;
	
	public List<IndicatorSummary> indicators = new ArrayList<IndicatorSummary>();
	
	public IndicatorResponse(String sptId, Integer timeLimit, String indicatorId) {

		String[] indicatorIds = indicatorId.split(",");
		
		this.sptId = sptId;
		this.timeLimit = timeLimit;
		
		for(String id : indicatorIds) {

			List<IndicatorItem> items = Api.analyst.queryIndicators(sptId, id, timeLimit);

			IndicatorSummary summary = new IndicatorSummary(id, items);
			indicators.add(summary);
		}
		
	}
	
}
