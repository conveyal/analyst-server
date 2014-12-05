package utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mapdb.Fun.Tuple2;
import org.opentripplanner.analyst.ResultFeature;

import utils.NaturalBreaksClassifier.Bin;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.prep.PreparedPolygon;
import com.vividsolutions.jts.index.strtree.STRtree;

import models.Query;
import models.Shapefile;
import models.SpatialLayer;
import models.Shapefile.ShapeFeature;

public class QueryResults {

	public static  Map<String, QueryResults> queryResultsCache = new ConcurrentHashMap<String, QueryResults>();
	
	/*
	 * Min and max values for this result. Set to null initially so that there is no confusion
	 * as to whether they've been set.
	 */
	public Double minValue = null;
	public Double maxValue = null;
	
	/**
	 * From whence do the geometries of this QueryResults come?
	 */
	public String shapeFileId;

	public ConcurrentHashMap<String, QueryResultItem> items = new ConcurrentHashMap<String, QueryResultItem>();
	public ConcurrentHashMap<Tuple2<String, String>, QueryResults> aggregated =
			new ConcurrentHashMap<Tuple2<String, String>, QueryResults>();
	
	public LinearClassifier linearClassifier;
	public NaturalBreaksClassifier jenksClassifier;
	
	public QueryResults() {
		
	}
	
	public QueryResults(Query q, Integer timeLimit) {
	   SpatialLayer sd = SpatialLayer.getPointSetCategory(q.pointSetId);

       for(ResultFeature feature : q.getResults().getAll()) {
        	
        	Double value = feature.sum(timeLimit).doubleValue();
        	
        	if(maxValue == null || value > maxValue)
        		maxValue = value;
        	if(minValue == null || minValue > value)
        		minValue = value;
        	
        	QueryResultItem item = new QueryResultItem();
        	
        	item.value = value;
        	        	
        	item.feature = sd.getShapefile().getShapeFeatureStore().getById(feature.id);
        	
        	items.put(feature.id, item);
       }
       
       shapeFileId = sd.shapeFileId;
       
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
	
	/**
	 * Aggregate these QueryResults into the units specified by the shapefile aggregateTo.
	 * Weight by the point set weightBy. For example, suppose you've calculated block-level
	 * statistics for a large state, and now you want to aggregate them up to the county level.
	 * It doesn't make sense to simply average the accessibility of every block in that county;
	 * that would commit the Modifiable Areal Unit Problem. Suppose that there is one block where
	 * 300 people live that is highly accessible, and another where only 10 people live that is
	 * highly inaccessible. You want to weight by the people to get an accurate picture of the
	 * accessibility for the average resident in that county.
	 * 
	 * This used to be performed by the functions normalizeBy and groupBy. The output of this function
	 * is similar, but not identical, to the combination of those functions. The difference is because,
	 * when weighting, normalizeBy always found the weight of a polygon by whether it contained the centroid
	 * of another. In the case where both the original query and the weights run against the same shapefile,
	 * this means that areas which don't contain their own centroids (not terribly uncommon) didn't get mapped
	 * to themselves. In this implementation, if the two come from the same shapefile, the matching for
	 * the weighting is done by feature ID.
	 */
	public QueryResults aggregate (Shapefile aggregateTo, SpatialLayer weightBy) {
		// see if we've already performed this aggregation; if so, return it from the cache
		synchronized (aggregated) {
			Tuple2<String, String> key = new Tuple2<String, String> (aggregateTo.id, weightBy.id);
			if (aggregated.containsKey(key))
				return aggregated.get(key);
			
			
			DataStore<ShapeFeature> weightStore = weightBy.getShapefile().getShapeFeatureStore();
			
			// TODO: should we allow weighting by information from another shapefile? It could
			// be useful, but also has the potential to introduce error.
			if (!shapeFileId.equals(weightBy.shapeFileId)) {
				System.err.println("Cannot weight by attribute of other shapefile!");
				return null;
			}
			
			// build a spatial index for the features of this queryresult
			// TODO: cache spatial index
			STRtree spIdx = new STRtree(items.values().size());
			
			for (QueryResultItem i : this.items.values()) {
				// TODO: we should be indexing the envelopes not the centroids
				spIdx.insert(i.feature.geom.getCentroid().getEnvelopeInternal(), i);
			}
			
			QueryResults out = new QueryResults();
			
			// this does not actually load all the features into memory; this is a MapDB, and
			// DataStore is delegating to MapDB's map values() function, which returns a disk-backed
			// collection
			for (final ShapeFeature f : aggregateTo.getShapeFeatureStore().getAll()) {
				// TODO: this should be moved into Akka actors and parallelized
				// TODO: ensure STRtree is threadsafe. There is some debate on this point.
				Envelope env = f.geom.getEnvelopeInternal();
				
				// find all of the items that could overlap this geometry
				List<QueryResultItem> potentialMatches = spIdx.query(env);
				
				// TODO: this is mapping each original geography to exactly one aggregate geography
				// we should split original geographies and aggregate pycnoplactically.
				Collection<QueryResultItem> actualMatches =
						Collections2.filter(potentialMatches, new Predicate<QueryResultItem> () {
					@Override
					public boolean apply(QueryResultItem potentialMatch) {
						Point centroid = potentialMatch.feature.geom.getCentroid();
						for (PreparedPolygon p : f.getPreparedPolygons()) {
							if (p.contains(centroid))
								return true;
						}
						return false;
					}
				});
				
				// this is the weighted value of all of the original geographies within this
				// aggregate geography
				double weightedVal = 0.0;
				
				// This is the sum of the weights of all of the original geographies within this
				// aggregate geography
				double sumOfWeights = 0.0;
				
				for (QueryResultItem i : actualMatches) {
					// calculate the weight of this geography
					double weight = weightStore.getById(i.feature.id).getAttributeSum(weightBy.getAttributeIds());
					weightedVal += i.value * weight;
					sumOfWeights += weight;
				}
				
				// add this feature to the new query result
				QueryResultItem item = new QueryResultItem();
				// don't divide by zero
				item.value = sumOfWeights > 0.0000001 ? weightedVal / sumOfWeights : 0;
				item.feature = f;
				out.items.put(f.id, item);
				out.shapeFileId = aggregateTo.id;
				
				if (out.maxValue == null || item.value > out.maxValue)
					out.maxValue = item.value;
	        	
				if (out.minValue == null || out.minValue > item.value)
	        		out.minValue = item.value;
        	}
			
			out.jenksClassifier = new NaturalBreaksClassifier(out, 10, new Color(1.0f, 1.0f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			out.shapeFileId = aggregateTo.id;
			
			aggregated.put(key, out);
			
			return out;
		}
	}	
       
	public class QueryResultItem {
		public ShapeFeature feature;
		public Double value = 0.0;
		public Double normalizedTotal = 0.0;
		public Double original = 0.0;
	}
}
