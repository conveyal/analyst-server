package com.conveyal.analyst.server.tiles;

import com.conveyal.analyst.server.controllers.SinglePoint;
import com.conveyal.analyst.server.utils.HaltonPoints;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.strtree.STRtree;
import models.Attribute;
import models.Shapefile;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.cluster.ResultEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
* Compare single-point results.
*/
public class SurfaceComparisonTile extends AnalystTileRequest implements UTFIntGridRequest {
    private static final Logger LOG = LoggerFactory.getLogger(SurfaceComparisonTile.class);

	final String resultKey1;
	final String resultKey2;
	final ResultEnvelope.Which which;
    final Boolean showIso;
    final Boolean showPoints;
    final Integer timeLimit;
    private static final GeometryFactory geometryFactory = new GeometryFactory();

    public SurfaceComparisonTile(String key1, String key2, ResultEnvelope.Which which, Integer x, Integer y, Integer z,
			   Boolean showIso, Boolean showPoints, Integer timeLimit, String format) {
        super(x, y, z, "surface_comparison", format);
        
		resultKey1 = key1;
		
		resultKey2 = key2;
		
        this.showIso = showIso;
        this.showPoints = showPoints;
        this.timeLimit = timeLimit;
        this.which = which;
    }

    public String getId() {
    	// includes result keys, which contain graph ID and profile information
        return super.getId() + "_" +  resultKey1 + "_" + resultKey2 + "_" + which + "_" + showIso + "_" + showPoints + "_" + timeLimit;
    }

    public byte[] render(){
        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
    	ResultEnvelope env1 = SinglePoint.getResultSet(resultKey1); 
        ResultSet result1 = env1.get(which);
        
        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
        ResultEnvelope env2 = SinglePoint.getResultSet(resultKey2);
        ResultSet result2 = env2.get(which);
        
        if (result1 == null || result2 == null || !env1.destinationPointsetId.equals(env2.destinationPointsetId)) {
        	return null;
        }
    	
        Tile tile = new Tile(this);

        Shapefile shp = Shapefile.getShapefile(env1.destinationPointsetId);
        PointSet ps = shp.getPointSet();
        
        List<Shapefile.ShapeFeature> features = shp.query(tile.envelope);

        for(Shapefile.ShapeFeature feature : features) {

            int featIdx = ps.getIndexForFeature(feature.id);

            int time1 = result1.times[featIdx];
            int time2 = result2.times[featIdx];

            if (time1 == 0 && time2 == 0)
                continue;

            if(time1 == Integer.MAX_VALUE && time2 == Integer.MAX_VALUE)
                continue;

            Color color = null;

            if(showIso) {

            	// no change
                 if((Math.abs(time1 - time2) < 60 || time2 > time1) && time1 < timeLimit){
                     float opacity = 1.0f - (float)((float)time1 / (float)timeLimit);
                     color = new Color(0.9f,0.7f,0.2f,opacity);
                 }

                // new service
                else if((time1 == Integer.MAX_VALUE || time1 > timeLimit) && time2 < timeLimit) {
                    float opacity = 1.0f - (float)((float)time2 / (float)timeLimit);
                    color = new Color(0.8f,0.0f,0.8f,opacity);
                }
                 
                // faster service 
                else if(time1 > time2 && time2 < timeLimit) {
                    float opacity = 1.0f - (float)((float)time2 / (float)time1);
                    color = new Color(0.0f,0.0f,0.8f,opacity);
                }
                else {
                    color = new Color(0.0f,0.0f,0.0f,0.2f);
                }

                 if(color != null)
                    try {
                        tile.renderPolygon(feature.geom, color, null);
                    } catch (MismatchedDimensionException | TransformException e) {
                        LOG.error("Unable to render polygon", e);
                    }
            }

             if(showPoints && (time1 < timeLimit || time2 < timeLimit)) {

                for(Attribute a : shp.attributes.values()) {

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
            LOG.error("unable to generate tile image", e);
            return null;
        }
    }

	@Override
	public int[][] getGrid() {
        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
    	ResultEnvelope env1 = SinglePoint.getResultSet(resultKey1);
        ResultSet result1 = env1.get(which);
        
        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
        ResultEnvelope env2 = SinglePoint.getResultSet(resultKey2);
        ResultSet result2 = env2.get(which);
        
        if (result1 == null || result2 == null || !env1.destinationPointsetId.equals(env2.destinationPointsetId)) {
        	return null;
        }

        Shapefile shp = Shapefile.getShapefile(env1.destinationPointsetId);
        PointSet ps = shp.getPointSet();
		
		int[][] grid = new int[64][64];
		
		List<Shapefile.ShapeFeature> fsub = shp.query(new Envelope(tile2lon(x, z), tile2lon(x + 1, z), tile2lat(y, z), tile2lat(y + 1, z)));
		
		if (fsub.isEmpty())
			return null;
		
		// build a spatial index for just this tile, to speed up querying
		STRtree subIdx = new STRtree(Math.max(fsub.size(), 2));
		
		for (Shapefile.ShapeFeature ft : fsub) {
			subIdx.insert(ft.geom.getEnvelopeInternal(), ft);
		}
		
		ROW: for (int row = 0; row < 64; row++) {
			COL: for (int col = 0; col < 64; col++) {
				// find the point
				Coordinate c = new Coordinate(tile2lon(x + ((double) col / 64.0), z), tile2lat(y + ((double) row / 64.0), z));
				Point pt = geometryFactory.createPoint(c);
				
				// find relevant features
				List<Shapefile.ShapeFeature> features = subIdx.query(pt.getEnvelopeInternal());
				
				grid[row][col] = Integer.MIN_VALUE;
				
				if (features.isEmpty())
					continue COL;
				
				for (Shapefile.ShapeFeature ft : features) {
					if (!ft.geom.contains(pt))
						continue;
					
		            int featIdx = ps.getIndexForFeature(ft.id);

		            int time1 = result1.times[featIdx];
		            int time2 = result2.times[featIdx];

		            if (time1 == 0 && time2 == 0)
		                continue;

		            if(time1 == Integer.MAX_VALUE && time2 == Integer.MAX_VALUE)
		                continue;
		            
		            // found one
		            grid[row][col] = time1 - time2;
		            break;
				}
			}
		}
		
		return grid;
	}
	
	// copied from OpenTripPlanner but revised to take doubles
    public static double tile2lon(double x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }
    
    public static double tile2lat(double y, int z) {
        double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}
