package tiles;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.vividsolutions.jts.index.strtree.STRtree;
import models.Attribute;
import models.Bundle;
import models.Bundle.TransitSegment;
import models.Query;
import models.Shapefile;
import models.Shapefile.ShapeFeature;
import org.apache.commons.imaging.ImageWriteException;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import utils.HaltonPoints;
import utils.NaturalBreaksClassifier;
import utils.QueryResults;
import utils.QueryResults.QueryResultItem;
import utils.ResultEnvelope;

import java.awt.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AnalystTileRequest {
		public static  Map<String, NaturalBreaksClassifier> naturalBreaksClassifierCache = new ConcurrentHashMap<String, NaturalBreaksClassifier>();
	
	final public String format;
	final public String type;
	final public Integer x, y, z;
	
	public AnalystTileRequest(Integer x, Integer y, Integer z, String type) {
		this(x, y, z, type, "png");
	}
	
	public AnalystTileRequest(Integer x, Integer y, Integer z, String type, String format) {
		this.x = x;
		this.y = y;
		this.z = z;
		
		this.type = type;
		
		// default format is png
		this.format = format;
	}
	
	

	public String getId() {
		return type + "_" + x + "_" + y + "_" + z;
	}
	
	public boolean equals(AnalystTileRequest tr) {
		return this.getId().equals(tr.getId());
	}
	
	public int hashCode() {
		HashFunction hashFunction = Hashing.md5();
        HashCode hashCode = hashFunction.newHasher().putString(this.getId(), Charsets.UTF_8).hash();
        
        return hashCode.asInt();
	}
	
	abstract byte[] render();
	
	
	public static class TransitTile extends AnalystTileRequest {
		
		final String scenarioId;
		
		public TransitTile(String scenarioId, Integer x, Integer y, Integer z) {
			super(x, y, z, "transit");
			
			this.scenarioId = scenarioId;
		}
		
		public String getId() {
			return super.getId() + "_" + scenarioId;
		}
		
		public byte[] render(){
			
			Tile tile = new Tile(this);
			
            STRtree index = Bundle.getBundle(scenarioId).getSpatialIndex();
    		List<TransitSegment> segments = index.query(tile.envelope);

    		for(TransitSegment seg : segments) {
    			Color color;

    			color = new Color(0.6f,0.6f,1.0f,0.25f);

    			try {
					tile.renderLineString(seg.geom, color, null);
				} catch (MismatchedDimensionException | TransformException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    		
    		try {
				return tile.generateImage();
			} catch (IOException | ImageWriteException e) {
				e.printStackTrace();
				return null;
			}

		}
	}
	
	public static class TransitComparisonTile extends AnalystTileRequest {
		
		final String scenario1Id;
		final String scenario2Id;
		
		public TransitComparisonTile(String scenario1Id, String scenario2Id, Integer x, Integer y, Integer z) {
			super(x, y, z, "transitComparison");
			
			this.scenario1Id = scenario1Id;
			this.scenario2Id = scenario2Id;
		}
		
		public String getId() {
			return super.getId() + "_" + scenario1Id  + "_" + scenario2Id;
		}
		
		public byte[] render(){
			
			Tile tile = new Tile(this);
			/*
			HashSet<String> defaultEdges = new HashSet<String>();

			STRtree index1 = transitIndex.getIndexForGraph(scenario1Id);
			List<LineString> segments1 = null;//index1.query(tile.envelope);

			for(LineString ts : segments1) {
				defaultEdges.add(ts.edgeId);
			}
    		
    		STRtree index2 = transitIndex.getIndexForGraph(scenario2Id);
    		List<TransitSegment> segments2 = index2.query(tile.envelope);

    		for(TransitSegment ts : segments2) {
    			Color color;

				if(!defaultEdges.contains(ts.edgeId)) {
					color = new Color(0.6f,0.8f,0.6f,0.75f);

	    			try {
						tile.renderLineString(ts.geom, color, 5);
					} catch (MismatchedDimensionException
							| TransformException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
    		}
    		
    		try {
				return tile.generateImage();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
                        */
                        return null;	
		}
                       
	}
	
	public static class SpatialTile extends AnalystTileRequest {
		
		final String shapefileId;
		final String selectedAttributes;
		
		public SpatialTile(String shapefileId, Integer x, Integer y, Integer z, String selectedAttributes) {
			super(x, y, z, "spatial");
			
			this.shapefileId = shapefileId;
			this.selectedAttributes = selectedAttributes;
		}
		
		public String getId() {
			return super.getId() + "_" + shapefileId + "_" + selectedAttributes;
		}
		
		public byte[] render(){
			
			Tile tile = new Tile(this);

			Shapefile shp = Shapefile.getShapefile(shapefileId);

			HashSet<String> attributes = new HashSet<String>();

			if(selectedAttributes != null) {
				for(String attribute : selectedAttributes.split(",")) {
					attributes.add(attribute);
				}
			}

			if(shp == null)
				return null;

    	    List<ShapeFeature> features = shp.query(tile.envelope);

            for(ShapeFeature feature : features) {

            	if(shp.attributes.size() > 0 || attributes.isEmpty()) {
            		for(Attribute a : shp.attributes.values()) {

            			if(!attributes.contains(a.fieldName))
            				continue;

            			// draw halton points for indicator

            			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);

            			if(hp.getNumPoints() > 0) {

        	            	Color color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));

        	            	tile.renderHaltonPoints(hp, color);
            			}
            		}
            	}
            	else {

            		Color color = new Color(0.0f,0.0f,0.0f,0.1f);
            		Color stroke = new Color(0.0f,0.0f,0.0f,0.5f);

            		try {
						tile.renderPolygon(feature.geom, color, stroke);
					} catch (MismatchedDimensionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (TransformException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            	}
            }
		           
    		try {
				return tile.generateImage();
			} catch (IOException | ImageWriteException e) {
				e.printStackTrace();
				return null;
			}

		}
	}
	
	public static class ShapefileTile extends AnalystTileRequest {
		
		final String shapefileId;
		final String attributeName;
		
		public ShapefileTile(String shapefileId, Integer x, Integer y, Integer z, String attributeName) {
			super(x, y, z, "spatial");
			
			this.shapefileId = shapefileId;
			this.attributeName = attributeName;
		}
		
		public String getId() {
			return super.getId() + "_" + shapefileId;
		}
		
		public byte[] render(){
			
			Tile tile = new Tile(this);
				
			Shapefile shp = Shapefile.getShapefile(shapefileId);
			
    	    List<ShapeFeature> features = shp.getShapefile(shapefileId).query(tile.envelope);

    	    if(shp == null)
				return null;
    	    
    	    Attribute attr = null;
			
			for(Attribute a : shp.getShapeAttributes()) {
				if(a.fieldName.equals(attributeName)) {
					attr = a;
					break;
				}
			}
			
			if(attr == null)
				return null;
			
			Color colorTemplate;
			
			if(attr.color != null ) {
				Integer colorInt = Integer.parseInt(attr.color.replace("#", ""), 16);
				colorTemplate = new Color(colorInt);
			}
			else
				colorTemplate = new Color(0.0f,0.0f,0.0f,0.6f);
			
    	    
            for(ShapeFeature feature : features) {
            	
            	Color color = new Color(colorTemplate.getRed() / 255.0f, colorTemplate.getGreen() / 255.0f, colorTemplate.getBlue() / 255.0f, (float) (feature.getAttribute(attributeName) / attr.max));
        		Color stroke = new Color(0.0f,0.0f,0.0f,0.5f);

        		try {
					tile.renderPolygon(feature.geom, color, stroke);
				} catch (MismatchedDimensionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (TransformException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
		           
    		try {
				return tile.generateImage();
			} catch (IOException | ImageWriteException e) {
				e.printStackTrace();
				return null;
			}
			
		}
	}


	public static class QueryTile extends AnalystTileRequest {
		
		final String queryId;
		final Integer timeLimit;
		final String weightByShapefile;
		final String weightByAttribute;
		final String attributeName;
		final String groupBy;
		final ResultEnvelope.Which which;
		
		public QueryTile(String queryId, Integer x, Integer y, Integer z, Integer timeLimit,
				String weightByShapefile, String weightByAttribute, String groupBy,
				ResultEnvelope.Which which, String attributeName) {
			super(x, y, z, "transit");
			
			this.queryId = queryId;
			this.timeLimit = timeLimit;
			this.weightByShapefile = weightByShapefile;
			this.weightByAttribute = weightByAttribute;
			this.groupBy = groupBy;
			this.which = which;
			this.attributeName = attributeName;
		}
		
		public String getId() {
			return super.getId() + "_" + queryId + "_" + timeLimit +  "_" +  which + "_" + weightByShapefile + "_" + groupBy + "_" + weightByAttribute + "_" + attributeName;
		}
		
		public byte[] render(){
			
			Query query = Query.getQuery(queryId);
			
			if(query == null)
				return null;


    		String queryKey = queryId + "_" + timeLimit + "_" + which + "_" + attributeName;
			
			QueryResults qr = null;

			synchronized(QueryResults.queryResultsCache) {
				if(!QueryResults.queryResultsCache.containsKey(queryKey)) {
					qr = new QueryResults(query, timeLimit, which, attributeName);
					QueryResults.queryResultsCache.put(queryKey, qr);
				}
				else
					qr = QueryResults.queryResultsCache.get(queryKey);
			}
			
			return doRender(qr, query.shapefileId);
		}
		
		/**
		 * Actually perform the rendering. This is abstracted into a separate function so it can also be used in
		 * QueryComparisonTile.
		 */
		protected byte[] doRender(QueryResults qr, String shapefileId) {
			Tile tile = new Tile(this);
			
			Shapefile shp = Shapefile.getShapefile(shapefileId);


		    List<ShapeFeature> features = shp.query(tile.envelope);

		    if(weightByShapefile == null) {
		        for(ShapeFeature feature : features) {

		        	Color color = null;

		        	if(qr.items.containsKey(feature.id)) {
		         		color = qr.classifier.getColorValue(qr.items.get(feature.id).value);
		         	}

					if(color == null) {
						color = new Color(0.0f,0.0f,0.0f,0.1f);
					}

		         	if(color != null)
						try {
							tile.renderPolygon(feature.geom, color, null);
						} catch (MismatchedDimensionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (TransformException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

		        }
		    }
		    else {
		    	if(groupBy == null) {
		    		// It doesn't make sense to weight/normalize without also aggregating.
		    		// Suppose you are weighting job access by population. Then the weighted
		    		// unaggregated figures are in person-jobs, which is not a meaningful unit.
		    		throw new UnsupportedOperationException("Cannot specify normalization/weighting without specifying grouping/aggregation");
		    	}
		    	else {
		    		Shapefile aggregateTo = Shapefile.getShapefile(groupBy);
		    		Shapefile weightBy = Shapefile.getShapefile(weightByShapefile); 
		    		QueryResults groupedQr = qr.aggregate(aggregateTo, weightBy, weightByAttribute);

		    		// TODO: don't loop over everything here, only the items in this tile
		    		for(QueryResultItem item : groupedQr.items.values()) {

		            	Color color = null;

		            	color = groupedQr.classifier.getColorValue(item.value);

		            	if(color == null){
							color = new Color(0.0f,0.0f,0.0f,0.1f);
						}

		            	try {
							tile.renderPolygon(item.feature.geom, color, null);
						} catch (MismatchedDimensionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (TransformException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

		            }
		    	}
		    }
    		
    		try {
				return tile.generateImage();
			} catch (IOException | ImageWriteException e) {
				e.printStackTrace();
				return null;
			}
			
		}
	}

	/**
	 * A tile comparing two queries
	 */
	public static class QueryComparisonTile extends QueryTile {
		public final String compareTo;
		
		public QueryComparisonTile(String queryId, String compareTo, Integer x, Integer y, Integer z, Integer timeLimit,
				String weightByShapefile, String weightByAttribute, String groupBy, ResultEnvelope.Which which,
				String attributeName) {
			super(queryId, x, y, z, timeLimit, weightByShapefile, weightByAttribute, groupBy, which, attributeName);

			this.compareTo = compareTo;
		}
		
		@Override
		public String getId () {
			return super.getId() + "_" + compareTo;
		}
		
		@Override
		public byte[] render () {
			Query q1 = Query.getQuery(queryId);
			Query q2 = Query.getQuery(compareTo);
			
			if (q1 == null || q2 == null || !q1.shapefileId.equals(q2.shapefileId))
				return null;
			
			String q1key = queryId + "_" + timeLimit + "_" + which + "_" + attributeName;
			String q2key = compareTo + "_" + timeLimit + "_" + which + "_" + attributeName;
			
			QueryResults qr1, qr2;
			
			if (!QueryResults.queryResultsCache.containsKey(q1key)) {
				qr1 = new QueryResults(q1, timeLimit, which, attributeName);
				QueryResults.queryResultsCache.put(q1key, qr1);
			}
			else {
				qr1 = QueryResults.queryResultsCache.get(q1key);
			}
			
			if (!QueryResults.queryResultsCache.containsKey(q2key)) {
				qr2 = new QueryResults(q2, timeLimit, which, attributeName);
				QueryResults.queryResultsCache.put(q2key, qr2);
			}
			else {
				qr2 = QueryResults.queryResultsCache.get(q2key);
			}
			
			
			return doRender(qr1.subtract(qr2), q1.shapefileId);
		}
	}
 
}
