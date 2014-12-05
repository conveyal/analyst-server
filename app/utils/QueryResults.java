package utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opentripplanner.analyst.ResultFeature;

import utils.NaturalBreaksClassifier.Bin;

import com.vividsolutions.jts.geom.prep.PreparedPolygon;
import com.vividsolutions.jts.index.strtree.STRtree;

import models.Query;
import models.Shapefile;
import models.SpatialLayer;
import models.Shapefile.ShapeFeature;

public class QueryResults {

	public static  Map<String, QueryResults> queryResultsCache = new ConcurrentHashMap<String, QueryResults>();

	
	public Double minValue = -1.0;
	public Double maxValue = 0.0;

	public ConcurrentHashMap<String, QueryResultItem> items = new ConcurrentHashMap<String, QueryResultItem>();
	public ConcurrentHashMap<String, QueryResults> normalized = new ConcurrentHashMap<String, QueryResults>();
	public ConcurrentHashMap<String, QueryResults> grouped = new ConcurrentHashMap<String, QueryResults>();
	
	public LinearClassifier linearClassifier;
	public NaturalBreaksClassifier jenksClassifier;
	
	public QueryResults() {
		
	}
	
	public QueryResults(Query q, Integer timeLimit) {
	   SpatialLayer sd = SpatialLayer.getPointSetCategory(q.pointSetId);
	       
	   HashSet<Double> values = new HashSet<Double>();
	   
       for(ResultFeature feature : q.getResults().getAll()) {
        	
        	Double value = feature.sum(timeLimit).doubleValue();
        	
        	if(value > maxValue)
        		maxValue = value;
        	if(minValue == -1.0 || minValue > value)
        		minValue = value;
        	
        	QueryResultItem item = new QueryResultItem();
        	
        	item.value = value;
        	
        	values.add(((Math.round(value.floatValue()) / 100)) * 100.0);
        	
        	item.feature = sd.getShapefile().getShapeFeatureStore().getById(feature.id);
        	
        	items.put(feature.id, item);
       }
       
       ArrayList<Double> valuesArray = new ArrayList<Double>();
       
       valuesArray.addAll(values);
       
       //linearClassifier = new LinearClassifier(values, new Color(0.5f, 0.5f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
       jenksClassifier = new NaturalBreaksClassifier(this, 10, new Color(1.0f, 1.0f, 1.0f, 0.25f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
		
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
			
			 HashSet<Double> values = new HashSet<Double>();
			
			for(QueryResultItem item : items.values()) {
				
				List<ShapeFeature> normalizerMatches = new ArrayList<ShapeFeature>();
				
				
				// We probably don't need to be looping over the shapefile, as the query results contain
				// a reference to the feature.
				for(Object o : normalizerItemIndex.query(item.feature.geom.getEnvelopeInternal())) {
		
					for(PreparedPolygon pp : item.feature.getPreparedPolygons()) {
						if(o instanceof ShapeFeature && ((ShapeFeature)o).geom != null && pp.contains(((ShapeFeature)o).geom.getCentroid())) {

							normalizerMatches.add((ShapeFeature)o);
						}
					}	
				}
				
				QueryResultItem i = new QueryResultItem();
				
				i.feature = item.feature;
	
				for(ShapeFeature sf : normalizerMatches) {
					i.normalizedTotal += sf.getAttributeSum(sd.getAttributeIds());
				}
				
				// The value of the normalized item is value / weight. This isn't really a 
				// meaningful number, unless I misunderstand something, as changing the size of the original
				// areal unit changes the weight (normalizedTotal) a lot but does not change the value
				// substantially. This number is never exposed to the UI and should probably be removed,
				// unless there is something I am misunderstanding.
				if(item.value > 0 && i.normalizedTotal > 0)
					i.value = item.value / i.normalizedTotal;
				
				values.add(((Math.round(i.value.floatValue()) / 100)) * 100.0);
				
				i.original = item.value;
				
				if(i.value > normalizedQr.maxValue)
					normalizedQr.maxValue = i.value;
	        	if(normalizedQr.minValue == -1 || normalizedQr.minValue > i.value)
	        		normalizedQr.minValue = i.value;
				
				normalizedQr.items.put(i.feature.id, i);
		
				
			}
			
			ArrayList<Double> valuesArray = new ArrayList<Double>();
		       
		    valuesArray.addAll(values);
			
			normalizedQr.jenksClassifier = new NaturalBreaksClassifier(normalizedQr, 10, new Color(1.0f, 1.0f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			//normalizedQr.linearClassifier = new LinearClassifier(values, new Color(0.5f, 0.5f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			  
			
			normalized.put(pointSetId, normalizedQr);
		
			return normalizedQr;
		}
		
	}
	
	public QueryResults groupBy(String shapefileId) {
		synchronized(grouped) {
			
			if(grouped.containsKey(shapefileId))
				return grouped.get(shapefileId);
			
			Shapefile shp = Shapefile.getShapefile(shapefileId);
			
			STRtree groupItemIndex = new STRtree(items.values().size());
			
			for(QueryResultItem item : this.items.values()) {
				groupItemIndex.insert(item.feature.geom.getCentroid().getEnvelopeInternal(), item);
			}
			
			HashSet<Double> values = new HashSet<Double>();
			
			QueryResults groupedQr = new QueryResults();
			
			for(ShapeFeature item : shp.getShapeFeatureStore().getAll()) {
			
				List<QueryResultItem> groupedMatches = new ArrayList<QueryResultItem>();
				
				for(Object o : groupItemIndex.query(item.geom.getEnvelopeInternal())) {
					for(PreparedPolygon pp : item.getPreparedPolygons()) {
						if(o instanceof QueryResultItem && ((QueryResultItem)o).feature.geom != null && pp.contains(((QueryResultItem)o).feature.geom.getCentroid())) {
							groupedMatches.add((QueryResultItem)o);
						}
					}	
				}
				
				QueryResultItem groupItem = new QueryResultItem();
				groupItem.feature = item;
				
				Double total = 0.0;
				Double normalizedTotal = 0.0;
				for(QueryResultItem i : groupedMatches) {
					total += i.original * i.normalizedTotal;
					normalizedTotal += i.normalizedTotal;
				}
				
				groupItem.value = (total / normalizedTotal);
				
				if(groupItem.value.isNaN())
					groupItem.value = 0.0;

				// These are the values for the classifier
				// TODO: move rounding into classifier
	        	values.add(((Math.round(groupItem.value.floatValue()) / 100)) * 100.0);
				
				if(groupItem.value > groupedQr.maxValue)
					groupedQr.maxValue = groupItem.value;
	        	if(groupedQr.minValue == -1 || groupedQr.minValue > groupItem.value)
	        		groupedQr.minValue = groupItem.value;
	        	
	        	groupedQr.items.put(item.id, groupItem);
				
			}
			
			ArrayList<Double> valuesArray = new ArrayList<Double>();
		       
		    valuesArray.addAll(values);
			
			groupedQr.jenksClassifier = new NaturalBreaksClassifier(groupedQr, 10, new Color(1.0f, 1.0f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			//groupedQr.linearClassifier = new LinearClassifier(values, new Color(0.5f, 0.5f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			
			grouped.put(shapefileId, groupedQr);
			
			return groupedQr;
		}
	}
	
       
	public class QueryResultItem {
		public ShapeFeature feature;
		public Double value = 0.0;
		public Double normalizedTotal = 0.0;
		public Double original = 0.0;
	}
}
