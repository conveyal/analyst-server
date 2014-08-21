package tiles;

import java.awt.Color;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import models.Attribute;
import models.Shapefile.ShapeFeature;
import models.SpatialLayer;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.ResultFeatureDelta;
import org.opentripplanner.analyst.ResultFeatureWithTimes;
import org.opentripplanner.analyst.TimeSurface;

import otp.AnalystRequest;
import utils.HaltonPoints;
import utils.TransportIndex;
import utils.TransportIndex.TransitSegment;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.vividsolutions.jts.index.strtree.STRtree;

public abstract class AnalystTileRequest {
	
	private static TransportIndex transitIndex = new TransportIndex();
	
	final public String type;
	final public Integer x, y, z;
	
	public AnalystTileRequest(Integer x, Integer y, Integer z, String type) {
		this.x = x;
		this.y = y;
		this.z = z;
		
		this.type = type;
	}

	public String getId() {
		return type + "_" + x + "_" + y + "_" + z;
	}
	
	public boolean equals(AnalystTileRequest tr) {
		return this.getId().equals(tr.getId());
	}
	
	public int hashCode() {
		HashFunction hashFunction = Hashing.md5();
        HashCode hashCode = hashFunction.newHasher().putString(this.getId()).hash();
        
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
			
			HashSet<String> defaultEdges = new HashSet<String>();

    		STRtree index = transitIndex.getIndexForGraph(scenarioId);
    		List<TransitSegment> segments = index.query(tile.envelope);

    		for(TransitSegment ts : segments) {
    			Color color;

    			color = new Color(0.6f,0.6f,1.0f,0.25f);

    			try {
					tile.renderLineString(ts.geom, color, null);
				} catch (MismatchedDimensionException | TransformException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    		
    		try {
				return tile.generateImage();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			
		}
	}
	
	public static class SpatialTile extends AnalystTileRequest {
		
		final String pointSetId;
		final String selectedAttributes;
		
		public SpatialTile(String pointSetId, Integer x, Integer y, Integer z, String selectedAttributes) {
			super(x, y, z, "spatial");
			
			this.pointSetId = pointSetId;
			this.selectedAttributes = selectedAttributes;
		}
		
		public String getId() {
			return super.getId() + "_" + pointSetId + "_" + selectedAttributes;
		}
		
		public byte[] render(){
			
			Tile tile = new Tile(this);

			SpatialLayer sd = SpatialLayer.getPointSetCategory(pointSetId);

			HashSet<String> attributes = new HashSet<String>();

			if(selectedAttributes != null) {
				for(String attribute : selectedAttributes.split(",")) {
					attributes.add(attribute);
				}
			}

			if(sd == null)
				return null;

    	    List<ShapeFeature> features = sd.getShapefile().query(tile.envelope);

            for(ShapeFeature feature : features) {

            	if(sd.attributes.size() > 0 || attributes.isEmpty()) {
            		for(Attribute a : sd.attributes) {

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
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			
		}
	}
	
	
	public static class SurfaceCompareTile extends AnalystTileRequest {
		
		final String spatialId;
		final Integer surfaceId1;
		final Integer surfaceId2;
		final Boolean showIso;
		final Boolean showPoints;
		final Integer timeLimit;
		final Integer minTime;
		
		public SurfaceCompareTile(Integer surfaceId1, Integer surfaceId2, String spatialId, Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {
			super(x, y, z, "surface");
			
			this.spatialId = spatialId;
			this.surfaceId1 = surfaceId1;
			this.surfaceId2 = surfaceId2;
			this.showIso = showIso;
			this.showPoints = showPoints;
			this.timeLimit = timeLimit;
			this.minTime = minTime;
		}
		
		public String getId() {
			return super.getId() + "_" + spatialId + "_" + surfaceId1 + "_" + surfaceId2 + "_" + showIso + "_" + showPoints + "_" + timeLimit + "_" + minTime;
		}
		
		public byte[] render(){
			
			Tile tile = new Tile(this);

			SpatialLayer sd = SpatialLayer.getPointSetCategory(spatialId);

			if(sd == null)
				return null;
			
			final TimeSurface surf1 = AnalystRequest.getSurface(surfaceId1);
			final TimeSurface surf2 = AnalystRequest.getSurface(surfaceId2);

			if(surf1 == null || surf2 == null)
				return null;

			ResultFeatureDelta resultDelta = new ResultFeatureDelta(sd.getPointSet().getSampleSet(surf1.routerId), sd.getPointSet().getSampleSet(surf2.routerId),  surf1, surf2);

			List<ShapeFeature> features = sd.getShapefile().query(tile.envelope);

            for(ShapeFeature feature : features) {

            	if(resultDelta.timeIdMap.get(feature.id) == 0 &&  resultDelta.times2IdMap.get(feature.id) == 0)
            		continue;

            	int time1 = resultDelta.timeIdMap.get(feature.id);
            	int time2 = resultDelta.times2IdMap.get(feature.id);

            	if(time1 == Integer.MAX_VALUE && time2 == Integer.MAX_VALUE)
            		continue;

            	Color color = null;

            	if(showIso) {

                 	if((time2 == time1 || time2 > time1) && time1 > minTime && time1 < timeLimit){
                 		float opacity = 1.0f - (float)((float)time1 / (float)timeLimit);
                 		color = new Color(0.9f,0.7f,0.2f,opacity);
                 	}

    				else if((time1 == Integer.MAX_VALUE || time1 > timeLimit) && time2 < timeLimit && time2 > minTime) {
    					float opacity = 1.0f - (float)((float)time2 / (float)timeLimit);
    					color = new Color(0.8f,0.0f,0.8f,opacity);
    				}
    				else if(time1 > time2 && time2 < timeLimit && time2 > minTime) {
    					float opacity = 1.0f - (float)((float)time2 / (float)time1);
    					color = new Color(0.0f,0.0f,0.8f,opacity);
    				}
    				else {
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

             	if(showPoints && (time1 < timeLimit || time2 < timeLimit)) {

            		for(Attribute a : sd.attributes) {

		    			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);

            			if(hp.getNumPoints() > 0) {

        	            	color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));

        	            	tile.renderHaltonPoints(hp, color);
            			}
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
			
		}
	}
	
	
    public static class SurfaceTile extends AnalystTileRequest {
    		
    		final String pointSetId;
    		final Integer surfaceId;
    		final Boolean showIso;
    		final Boolean showPoints;
    		final Integer timeLimit;
    		final Integer minTime;
    		
    		public SurfaceTile(Integer surfaceId, String pointSetId, Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {
    			super(x, y, z, "surface");
    			
    			this.pointSetId = pointSetId;
    			this.surfaceId = surfaceId;
    			this.showIso = showIso;
    			this.showPoints = showPoints;
    			this.timeLimit = timeLimit;
    			this.minTime = minTime;
    		}
    		
    		public String getId() {
    			return super.getId() + "_" + pointSetId + "_" + surfaceId + "_" + showIso + "_" + showPoints + "_" + timeLimit + "_" + minTime;
    		}
    		
    		public byte[] render(){
    			
    			Tile tile = new Tile(this);

    			SpatialLayer sd = SpatialLayer.getPointSetCategory(pointSetId);

    			if(sd == null)
    				return null;

    			final TimeSurface surface = AnalystRequest.getSurface(surfaceId);

    			if(surface == null)
    				return null;

	    		ResultFeatureWithTimes result = AnalystRequest.getResultWithTimes(surfaceId, pointSetId);

	            List<ShapeFeature> features = sd.getShapefile().query(tile.envelope);

	            for(ShapeFeature feature : features) {

	            	Integer sampleTime = result.getTime(feature.id);
	            	if(sampleTime == null)
	            		continue;

	            	if(sampleTime == Integer.MAX_VALUE)
	            		continue;

	            	if(showIso) {

	            		Color color = null;

	                 	if(sampleTime < timeLimit && (minTime != null && sampleTime > minTime)){
	                 		float opacity = 1.0f - (float)((float)sampleTime / (float)timeLimit);
	                 		color = new Color(0.9f,0.7f,0.2f,opacity);
	                 	}
	    				else {
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

	            	// draw halton points for indicator

	        		if(showPoints && sampleTime < timeLimit && (minTime != null && sampleTime > minTime)) {

	            		for(Attribute a : sd.attributes) {

			    			HaltonPoints hp = feature.getHaltonPoints(a.fieldName);

	            			if(hp.getNumPoints() > 0) {

	        	            	Color color = new Color(Integer.parseInt(a.color.replace("#", ""), 16));

	        	            	tile.renderHaltonPoints(hp, color);
	            			}
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
			
			HashSet<String> defaultEdges = new HashSet<String>();

			STRtree index1 = transitIndex.getIndexForGraph(scenario1Id);
			List<TransitSegment> segments1 = index1.query(tile.envelope);

			for(TransitSegment ts : segments1) {
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
			
		}
	}
}
