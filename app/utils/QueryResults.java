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
import org.mapdb.Fun.Tuple3;
import org.opentripplanner.analyst.ResultSet;

import utils.Bin;
import utils.ResultEnvelope.Which;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.prep.PreparedPolygon;
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.strtree.STRtree;

import models.Attribute;
import models.Query;
import models.Shapefile;
import models.SpatialLayer;
import models.Shapefile.ShapeFeature;

public class QueryResults {

	public static  Map<String, QueryResults> queryResultsCache = new ConcurrentHashMap<String, QueryResults>();
	
	/**
	 * Keep track of IDs
	 * This is an Integer not an int so we can synchronize on it.
	 */
	
	private static Integer nextId = 0;
	
	/*
	 * Min and max values for this result. Set to null initially so that there is no confusion
	 * as to whether they've been set.
	 */
	public Double minValue = null;
	public Double maxValue = null;
	
	/** The maximum possible accessibility value an item could take, if everything was accessible. */
	public double maxPossible;
	
	/**
	 * Number of classes to display on the map. 
	 */
	public static final int nClasses = 6;
	
	/**
	 * From whence do the geometries of this QueryResults come?
	 */
	public String shapeFileId;
	
	/**
	 * What attribute of that shapefile are we using?
	 */
	public String attributeId;

	public ConcurrentHashMap<String, QueryResultItem> items = new ConcurrentHashMap<String, QueryResultItem>();
	
	public ConcurrentHashMap<Integer, QueryResults> subtracted = new ConcurrentHashMap<Integer, QueryResults>();
	
	public ConcurrentHashMap<Tuple3<String, String, String>, QueryResults> aggregated =
			new ConcurrentHashMap<Tuple3<String, String, String>, QueryResults>();
	
	public Classifier classifier;
	
	/** Cache the spatial index */
	private transient SpatialIndex spIdx = null;
	
	/** We want the IDs to be weak so that they never get serialized, as they are reset every time the server is restarted */
	private transient int id;

	/** Is this the point estimate, lower bound, etc.? */
	private ResultEnvelope.Which which;
	
	public QueryResults() {
		
	}
	
	public QueryResults(Query q, Integer timeLimit, ResultEnvelope.Which which, String attributeId) {
		Shapefile sd = Shapefile.getShapefile(q.shapefileId);
		
		this.which = which;

       double value;

       for(ResultEnvelope env : q.getResults().getAll(sd.categoryId + "." + attributeId)) {
    	   
    	   ResultSet feature;
    	   
    	   switch(which) {
    	   case WORST_CASE:
    		   feature = env.worstCase;
    		   break;
    	   case BEST_CASE:
    		   feature = env.bestCase;
    		   break;
    	   case POINT_ESTIMATE:
    		   feature = env.pointEstimate;
    		   break;
    	   case SPREAD:
    		   feature = env.spread;
    		   break;
    	   case AVERAGE:
    		   feature = env.avgCase;
    		   break;
    	   default:
    	    	throw new RuntimeException("Unhandled envelope type"); 
    	   }
    	   
           value = (double) feature.sum(timeLimit, sd.categoryId + "." + attributeId);
        	
        	if(maxValue == null || value > maxValue)
        		maxValue = value;
        	if(minValue == null || minValue > value)
        		minValue = value;
        	
        	QueryResultItem item = new QueryResultItem();
        	
        	item.value = value;
        	        	
        	item.feature = sd.getShapeFeatureStore().getById(feature.id);
        	
        	items.put(feature.id, item);
       }
       
       shapeFileId = sd.id;
       
       this.attributeId = attributeId;
       
       this.maxPossible = sd.attributes.get(attributeId).sum;
       
       // assign a unique ID
       synchronized (nextId) {
    	   id = nextId++;
       }
       
       //linearClassifier = new LinearClassifier(values, new Color(0.5f, 0.5f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
       classifier = new NaturalBreaksClassifier(this, nClasses, new Color(1.0f, 1.0f, 1.0f, 0.25f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
		
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
	public QueryResults aggregate (Shapefile aggregateTo, Shapefile weightBy, String weightByAttribute) {
		// see if we've already performed this aggregation; if so, return it from the cache
		synchronized (aggregated) {
			Tuple3<String, String, String> key = new Tuple3<String, String, String> (aggregateTo.id, weightBy.id, weightByAttribute);
			if (aggregated.containsKey(key))
				return aggregated.get(key);
						
			// Do the features come from the same shapefile?
			boolean sameShapefile = shapeFileId.equals(weightBy.id);
			
			// if they don't come from the same shapefile, create the weights pycnoplactically
			// we'll need a spatial index for that
			SpatialIndex weightIdx = null;
			DataStore<ShapeFeature> weightStore = null;
			
			if (!sameShapefile) {
				// get the spatial index
				weightIdx = weightBy.getSpatialIndex();
			}
			else {
				// just use the id -> feature mapping directly
				weightStore = weightBy.getShapeFeatureStore();
			}
			
			// build a spatial index for the features of this queryresult
			SpatialIndex spIdx = getSpatialIndex();
			
			QueryResults out = new QueryResults();
			
			// this does not actually load all the features into memory; this is a MapDB, and
			// DataStore is delegating to MapDB's map values() function, which returns a disk-backed
			// collection
			for (final ShapeFeature aggregateFeature : aggregateTo.getShapeFeatureStore().getAll()) {
				// TODO: this should be moved into Akka actors and parallelized
				// TODO: ensure STRtree is threadsafe. There is some debate on this point.
				Envelope env = aggregateFeature.geom.getEnvelopeInternal();
				
				// find all of the items that could overlap this geometry
				List<QueryResultItem> potentialMatches = spIdx.query(env);
				
				// this is the weighted value of all of the original geographies within this
				// aggregate geography
				double weightedVal = 0.0;
				
				// This is the sum of the weights of all of the original geographies within this
				// aggregate geography
				double sumOfWeights = 0.0;
				
				for (QueryResultItem match : potentialMatches) {
					// calculate the weight of this geography in the aggregate geography
					double weight;
					
					if (sameShapefile) {
						// FIXME hard-wiring first attribute sum while rebuilding UI -- this won't work 
						weight = weightStore.getById(match.feature.id).getAttribute(weightByAttribute);
					}
					else {
						weight = 0;
						
						// query the spatial index
						List<ShapeFeature> potentialWeights =
								weightIdx.query(match.feature.geom.getEnvelopeInternal());
						
						for (ShapeFeature weightFeature : potentialWeights) {
							// calculate the weight of the entire item geometry that we are weighting by
							
							// FIXME hard-wiring first attribute sum while rebuilding UI -- this won't work 
							double totalWeight = weightFeature.getAttribute(weightByAttribute); 
						
							// figure out how much of this weight should be assigned to the original geometry
							double weightArea = GeoUtils.getArea(weightFeature.geom);
							
							// don't divide by zeroish
							if (weightArea < 0.0000000001)
								continue;
							
							Geometry overlap = weightFeature.geom.intersection(match.feature.geom);
							if (overlap.isEmpty())
								continue;
							
							double overlapArea = GeoUtils.getArea(overlap);
							
							weight += totalWeight * (overlapArea / weightArea);
								
						}
					}
					
					// this aggregate geography may not completely contain the original geography.
					// discount weight to account for that.
					
					double matchArea = GeoUtils.getArea(match.feature.geom);
					
					if (matchArea < 0.0000000001)
						continue;
					
					Geometry overlap = match.feature.geom.intersection(aggregateFeature.geom);
					
					if (overlap.isEmpty())
						continue;
					
					weight *= GeoUtils.getArea(overlap) / matchArea;
					
					weightedVal += match.value * weight;
					sumOfWeights += weight;
				}
				
				// add this feature to the new query result
				QueryResultItem item = new QueryResultItem();
				// don't divide by zero
				item.value = sumOfWeights > 0.0000001 ? weightedVal / sumOfWeights : 0;
				item.feature = aggregateFeature;
				out.items.put(aggregateFeature.id, item);
				out.shapeFileId = aggregateTo.id;
				
				if (out.maxValue == null || item.value > out.maxValue)
					out.maxValue = item.value;
	        	
				if (out.minValue == null || out.minValue > item.value)
	        		out.minValue = item.value;
        	}
			
			// we preserve the maxPossible from the original. It does not change under aggregation.
			out.maxPossible = this.maxPossible;
			
			if (this.classifier instanceof BimodalNaturalBreaksClassifier && this.minValue < 0 && this.maxValue > 0)
				out.classifier = new BimodalNaturalBreaksClassifier(out, nClasses, 0d,
						new Color(.9f, .9f, .1f, .5f), new Color(.5f, .5f, .5f, .5f), new Color(0f, 0f, 1f, .5f));
			else
				out.classifier = new NaturalBreaksClassifier(out, nClasses, new Color(1.0f, 1.0f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			
			out.shapeFileId = aggregateTo.id;
						
			aggregated.put(key, out);
			
			// TODO: set attribute ID.
			
			return out;
		}
	}
	
	/**
	 * Subtract the other queryresults from this one, and return the query results.
	 * The other queryresults must have come from or been aggregated to the same shapefile.
	 */
	public QueryResults subtract(QueryResults otherQr) {
		synchronized (subtracted) {
			if (subtracted.containsKey(otherQr.id))
				return subtracted.get(otherQr.id);
			
			// TODO: check that indicator is same also
			if (!shapeFileId.equals(otherQr.shapeFileId) || !attributeId.equals(otherQr.attributeId)) {
				throw new IllegalArgumentException("Query results in difference operation do not come from same attribute of same shapefile!");
			}
			
			QueryResults ret = new QueryResults();
			
			ret.shapeFileId = this.shapeFileId;
			
			for (String id : items.keySet()) {
				QueryResultItem item1 = this.items.get(id);
				QueryResultItem item2 = otherQr.items.get(id);
				
				if (item2 == null)
					// if it's unreachable in either leave it out of the difference
					continue;
				
				QueryResultItem newItem = new QueryResultItem();
				newItem.feature = item1.feature;
				newItem.value = item1.value - item2.value;
				
				if (ret.maxValue == null || newItem.value > ret.maxValue)
					ret.maxValue = newItem.value;
	        	
				if (ret.minValue == null || ret.minValue > newItem.value)
	        		ret.minValue = newItem.value;
				
				ret.items.put(id, newItem);
			}
			
			// we preserve the maxPossible from the original. This is because we want to represent percentages as
			// a percentage of total possible still, not a percent change.
			ret.maxPossible = this.maxPossible;
			
			if (this.minValue < 0 && this.maxValue > 0)
				ret.classifier = new BimodalNaturalBreaksClassifier(ret, nClasses, 0d,
						new Color(.9f, .9f, .1f, .5f), new Color(.5f, .5f, .5f, .5f), new Color(0f, 0f, 1f, .5f));
			else 
				ret.classifier = new NaturalBreaksClassifier(ret, nClasses, new Color(1.0f, 1.0f, 1.0f, 0.5f), new Color(0.0f, 0.0f, 1.0f, 0.5f));
			
			subtracted.put(otherQr.id, ret);
			
			return ret;
		}
	}
    
	/** Get a spatial index for the items of this queryresults */
	public SpatialIndex getSpatialIndex () {
		return getSpatialIndex(false);
	}
	
	/**
	 * Get a spatial index for the items of this queryresults.
	 * @param forceRebuild force the spatial index to be rebuilt.
	 * Should be set to true if the items of the result have changed in number or
	 * geography (if they've changed in value there is no need to rebuild).
	 */
	public SpatialIndex getSpatialIndex (boolean forceRebuild) {
		if (forceRebuild || spIdx == null) {
			// we can't build an STRtree with only one node, so we make sure we make a minimum of
			// two nodes even if we leave one empty
			spIdx = new STRtree(Math.max(items.size(), 2));
			
			for (QueryResultItem i : this.items.values()) {
				spIdx.insert(i.feature.geom.getEnvelopeInternal(), i);
			}
		}
		
		return spIdx;
	}

	public class QueryResultItem {
		public ShapeFeature feature;
		public Double value = 0.0;
		public Double normalizedTotal = 0.0;
		public Double original = 0.0;
	}
}
