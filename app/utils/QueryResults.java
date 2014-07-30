package utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.opentripplanner.analyst.ResultFeature;

import models.Query;
import models.SpatialLayer;
import models.Shapefile.ShapeFeature;

public class QueryResults {
	
	public Long minValue = -1l;
	public Long maxValue = 0l;

	public HashMap<String, QueryResultItem> items = new HashMap<String, QueryResultItem>();
	
	public QueryResults(Query q) {
		SpatialLayer sd = SpatialLayer.getPointSetCategory(q.pointSetId);
	       
       for(ResultFeature feature : q.getResults().getAll()) {
        	
        	Long value = 0l;
        	
        	for(String k : feature.histograms.keySet()) {
        		for(int v : feature.histograms.get(k).sums) {
        			value += v;
        		}
        	}
        	
        	if(value > maxValue)
        		maxValue = value;
        	if(minValue == -1 || minValue > value)
        		minValue = value;
        	
        	QueryResultItem item = new QueryResultItem();
        	
        	item.value = value;
        	item.feature = sd.getShapefile().getShapeFeatureStore().getById(feature.id);
        	
        	items.put(feature.id, item);
        }
	}
       
	public class QueryResultItem {
		public ShapeFeature feature;
		public Long value;
	}
}
