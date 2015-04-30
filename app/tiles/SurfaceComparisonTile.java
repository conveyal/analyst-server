package tiles;

import controllers.Api;
import controllers.SinglePoint;
import models.Attribute;
import models.Shapefile;

import org.joda.time.LocalDate;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.SampleSet;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.analyst.core.SlippyTile;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.strtree.STRtree;

import utils.HaltonPoints;
import utils.ResultEnvelope;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
* Created by matthewc on 3/4/15.
*/
public class SurfaceComparisonTile extends AnalystTileRequest implements UTFIntGridRequest {

	final String resultKey1;
	final String resultKey2;
	final String shapefileId;
	final ResultEnvelope.Which which;
    final Boolean showIso;
    final Boolean showPoints;
    final boolean profile;
    final Integer timeLimit;
    final Integer minTime;
    private static final GeometryFactory geometryFactory = new GeometryFactory();

    public SurfaceComparisonTile(String graphId, String graphId2, Double lat, Double lon, String mode, String shapefile,
			   Double bikeSpeed, Double walkSpeed, String which, String date, int fromTime, int toTime, Integer x, Integer y, Integer z,
			   Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime, boolean profile, String format) {
        super(x, y, z, "surface_comparison", format);

        LocalDate jodaDate = LocalDate.parse(date);
        
		resultKey1 = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s%s", graphId, lat, lon, mode,
				bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
				fromTime, toTime, shapefile, (profile ? "_profile" : ""));
		
		resultKey2 = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s%s", graphId2, lat, lon, mode,
				bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
				fromTime, toTime, shapefile, (profile ? "_profile" : ""));
		
		this.shapefileId = shapefile;
        this.showIso = showIso;
        this.showPoints = showPoints;
        this.timeLimit = timeLimit;
        this.minTime = minTime;
        this.which = ResultEnvelope.Which.valueOf(which);
        this.profile = profile;
    }

    public String getId() {
    	// includes result keys, which contain graph ID and profile information
        return super.getId() + "_" + shapefileId + "_" + resultKey1 + "_" + resultKey2 + "_" + showIso + "_" + showPoints + "_" + timeLimit + "_" + minTime;
    }

    public byte[] render(){

        Tile tile = new Tile(this);

        Shapefile shp = Shapefile.getShapefile(shapefileId);
        PointSet ps = shp.getPointSet();

        if(shp == null)
            return null;

        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
        ResultSet result1 = SinglePoint.getResultSet(resultKey1).get(which);
        
        if (result1 == null) {
        	return null;
        }
        
        
        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
        ResultSet result2 = SinglePoint.getResultSet(resultKey2).get(which);
        
        if (result2 == null) {
        	return null;
        }
        
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
                 if((Math.abs(time1 - time2) < 60 || time2 > time1) && time1 > minTime && time1 < timeLimit){
                     float opacity = 1.0f - (float)((float)time1 / (float)timeLimit);
                     color = new Color(0.9f,0.7f,0.2f,opacity);
                 }

                // new service
                else if((time1 == Integer.MAX_VALUE || time1 > timeLimit) && time2 < timeLimit && time2 > minTime) {
                    float opacity = 1.0f - (float)((float)time2 / (float)timeLimit);
                    color = new Color(0.8f,0.0f,0.8f,opacity);
                }
                 
                // faster service 
                else if(time1 > time2 && time2 < timeLimit && time2 > minTime) {
                    float opacity = 1.0f - (float)((float)time2 / (float)time1);
                    color = new Color(0.0f,0.0f,0.8f,opacity);
                }
                else {
                    color = new Color(0.0f,0.0f,0.0f,0.2f);
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
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

	@Override
	public int[][] getGrid() {
        Shapefile shp = Shapefile.getShapefile(shapefileId);
        PointSet ps = shp.getPointSet();

        if(shp == null)
            return null;

        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
        ResultSet result1 = SinglePoint.getResultSet(resultKey1).get(which);
        
        if (result1 == null) {
        	return null;
        }
        
        
        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
        ResultSet result2 = SinglePoint.getResultSet(resultKey2).get(which);
        
        if (result2 == null) {
        	return null;
        }
		
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
