package utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opentripplanner.analyst.ResultFeature;

import utils.NaturalBreaksClassifier.Bin;

import com.vividsolutions.jts.index.strtree.STRtree;

import models.Query;
import models.SpatialLayer;
import models.Shapefile.ShapeFeature;

public class QueryResults {
	
	public Double minValue = -1.0;
	public Double maxValue = 0.0;

	public ConcurrentHashMap<String, QueryResultItem> items = new ConcurrentHashMap<String, QueryResultItem>();
	public ConcurrentHashMap<String, QueryResults> normalized = new ConcurrentHashMap<String, QueryResults>();
	public ConcurrentHashMap<String, QueryResults> gruoped = new ConcurrentHashMap<String, QueryResults>();
	
	public LinearClassifier linearClassifier;
	public NaturalBreaksClassifier jenksClassifier;
	
	public QueryResults() {
		
	}
	
	public QueryResults(Query q, Integer timeLimit) {
	   SpatialLayer sd = SpatialLayer.getPointSetCategory(q.pointSetId);
	       
	   ArrayList<Double> values = new ArrayList<Double>();
	   
       for(ResultFeature feature : q.getResults().getAll()) {
        	
        	Double value = feature.sum(timeLimit).doubleValue();
        	
        	if(value > maxValue)
        		maxValue = value;
        	if(minValue == -1.0 || minValue > value)
        		minValue = value;
        	
        	QueryResultItem item = new QueryResultItem();
        	
        	item.value = value;
        	
        	values.add(value);
        	
        	item.feature = sd.getShapefile().getShapeFeatureStore().getById(feature.id);
        	
        	items.put(feature.id, item);
       }
       
       //linearClassifier = new LinearClassifier(values, new Color(0.5f, 0.5f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
       jenksClassifier = new NaturalBreaksClassifier(values, 10, new Color(1.0f, 1.0f, 1.0f, 0.25f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
		
	}
	
	/*public Color getColorById(String id) {
		Color c = linearClassifier.getColorValue(items.get(id).value);
		if(c == null) 
			return new Color(0.8f, 0.8f, 0.8f, 0.5f);
		else
			return c;
	}*/
	
	public QueryResults normalizeBy(String pointSetId) {
		synchronized(normalized) {
			
			if(normalized.containsKey(pointSetId))
				return normalized.get(pointSetId);

		
			SpatialLayer sd = SpatialLayer.getPointSetCategory(pointSetId);
			
			STRtree normalizerItemIndex = new STRtree(items.values().size());
			
			for(ShapeFeature feature : sd.getShapefile().getShapeFeatureStore().getAll()) {
				normalizerItemIndex.insert(feature.geom.getCentroid().getEnvelopeInternal(), feature);
			}
		
			QueryResults normalizedQr = new QueryResults();
			
			ArrayList<Double> values = new ArrayList<Double>();
			
			for(QueryResultItem item : items.values()) {
				
				List<ShapeFeature> normalizerMatches = new ArrayList<ShapeFeature>();
				
				for(Object o : normalizerItemIndex.query(item.feature.geom.getEnvelopeInternal())) {
					if(o instanceof ShapeFeature && item.feature.geom.contains(((ShapeFeature)o).geom.getCentroid())){
						normalizerMatches.add((ShapeFeature)o);
					}
				}
				
				QueryResultItem i = new QueryResultItem();
				
				i.feature = item.feature;
	
				for(ShapeFeature sf : normalizerMatches) {
					i.nomalizedTotal += sf.getAttributeSum(sd.getAttributeIds());
				}
				
				if(item.value > 0 && i.nomalizedTotal > 0)
					i.value = item.value / i.nomalizedTotal;
				
				values.add(i.value);
				
				i.original = item.value;
				
				if(i.value > normalizedQr.maxValue)
					normalizedQr.maxValue = i.value;
	        	if(normalizedQr.minValue == -1 || normalizedQr.minValue > i.value)
	        		normalizedQr.minValue = i.value;
				
				normalizedQr.items.put(i.feature.id, i);
		
				
			}
			
			normalizedQr.jenksClassifier = new NaturalBreaksClassifier(values, 10, new Color(1.0f, 1.0f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			
			normalized.put(pointSetId, normalizedQr);
		
			return normalizedQr;
		}
		
	}
	
	public QueryResults groupBy(String pointSetId) {
		synchronized(gruoped) {
			
			if(gruoped.containsKey(pointSetId))
				return gruoped.get(pointSetId);
			
			SpatialLayer sd = SpatialLayer.getPointSetCategory(pointSetId);
			
			STRtree groupItemIndex = new STRtree(items.values().size());
			
			for(QueryResultItem item : this.items.values()) {
				groupItemIndex.insert(item.feature.geom.getCentroid().getEnvelopeInternal(), item);
			}
			
			ArrayList<Double> values = new ArrayList<Double>();
			
			QueryResults groupedQr = new QueryResults();
			
			for(ShapeFeature item : sd.getShapefile().getShapeFeatureStore().getAll()) {
			
				List<QueryResultItem> groupedMatches = new ArrayList<QueryResultItem>();
				
				for(Object o : groupItemIndex.query(item.geom.getEnvelopeInternal())) {
					if(o instanceof QueryResultItem && item.geom.contains(((QueryResultItem)o).feature.geom.getCentroid())) {
						groupedMatches.add((QueryResultItem)o);
					}
				}
				
				QueryResultItem groupItem = new QueryResultItem();
				groupItem.feature = item;
				
				Double total = 0.0;
				Double normalizedTotal = 0.0;
				for(QueryResultItem i : groupedMatches) {
					total += i.original * i.nomalizedTotal;
					normalizedTotal += i.nomalizedTotal;
				}
				
				groupItem.value = (total / normalizedTotal) / 5838654;
				
				values.add(groupItem.value);
				
				if(groupItem.value > groupedQr.maxValue)
					groupedQr.maxValue = groupItem.value;
	        	if(groupedQr.minValue == -1 || groupedQr.minValue > groupItem.value)
	        		groupedQr.minValue = groupItem.value;
	        	
	        	groupedQr.items.put(item.id, groupItem);
				
			}
			
			groupedQr.jenksClassifier = new NaturalBreaksClassifier(values, 10, new Color(1.0f, 1.0f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			
			
			gruoped.put(pointSetId, groupedQr);
			
			return groupedQr;
		}
	}
	
       
	public class QueryResultItem {
		public ShapeFeature feature;
		public Double value = 0.0;
		public Double nomalizedTotal = 0.0;
		public Double original = 0.0;
	}
}
