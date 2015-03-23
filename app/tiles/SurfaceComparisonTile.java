package tiles;

import controllers.Api;
import controllers.SinglePoint;
import models.Attribute;
import models.Shapefile;

import org.joda.time.LocalDate;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSetDelta;
import org.opentripplanner.analyst.ResultSetWithTimes;
import org.opentripplanner.analyst.SampleSet;
import org.opentripplanner.analyst.TimeSurface;

import utils.HaltonPoints;
import utils.ResultEnvelope;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
* Created by matthewc on 3/4/15.
*/
public class SurfaceComparisonTile extends AnalystTileRequest {

	final String resultKey1;
	final String resultKey2;
	final String shapefileId;
	final ResultEnvelope.Which which;
    final Boolean showIso;
    final Boolean showPoints;
    final Integer timeLimit;
    final Integer minTime;

    public SurfaceComparisonTile(String graphId, String graphId2, Double lat, Double lon, String mode, String shapefile,
			   Double bikeSpeed, Double walkSpeed, String which, String date, int fromTime, int toTime, Integer x, Integer y, Integer z,
			   Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {
        super(x, y, z, "surface");

        LocalDate jodaDate = LocalDate.parse(date);
        
		resultKey1 = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s", graphId, lat, lon, mode,
				bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
				fromTime, toTime, shapefile);
		
		resultKey2 = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s", graphId2, lat, lon, mode,
				bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
				fromTime, toTime, shapefile);
		
		this.shapefileId = shapefile;
        this.showIso = showIso;
        this.showPoints = showPoints;
        this.timeLimit = timeLimit;
        this.minTime = minTime;
        this.which = ResultEnvelope.Which.valueOf(which);
    }

    public String getId() {
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
        ResultSetWithTimes result1 = (ResultSetWithTimes) SinglePoint.getResultSet(resultKey1).get(which);
        
        if (result1 == null) {
        	return null;
        }
        
        
        // note that this may occasionally return null if someone's had the site open for a very long
        // time because the result will have fallen out of the cache.
        ResultSetWithTimes result2 = (ResultSetWithTimes) SinglePoint.getResultSet(resultKey2).get(which);
        
        if (result2 == null) {
        	return null;
        }
        
        // TODO: cache deltas between requests
        // we want result2 - result1, because result2 is presumed to be the better one
        ResultSetDelta resultDelta = new ResultSetDelta(result2, result1);
        
        List<Shapefile.ShapeFeature> features = shp.query(tile.envelope);

for(Shapefile.ShapeFeature feature : features) {

            int featIdx = ps.getIndexForFeature(feature.id);

            int time1 = resultDelta.times[featIdx];
            int time2 = resultDelta.times2[featIdx];

            if (time1 == 0 && time2 == 0)
                continue;

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
}
