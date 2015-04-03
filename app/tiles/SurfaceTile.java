package tiles;

import models.Attribute;
import models.Shapefile;

import org.joda.time.LocalDate;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSetWithTimes;

import controllers.SinglePoint;
import utils.HaltonPoints;
import utils.ResultEnvelope;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
* Created by matthewc on 3/4/15.
*/
public class SurfaceTile extends AnalystTileRequest {
		final String resultKey;
		final String shapefileId;
		final ResultEnvelope.Which which;
        final Boolean showIso;
        final Boolean showPoints;
        final Integer timeLimit;
        final Integer minTime;
        final boolean profile;

        public SurfaceTile(String graphId, Double lat, Double lon, String mode, String shapefile,
						   Double bikeSpeed, Double walkSpeed, String which, String date, int fromTime, int toTime, Integer x, Integer y, Integer z,
						   Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime, boolean profile) {
            super(x, y, z, "surface");
            
            LocalDate jodaDate = LocalDate.parse(date);
            
			resultKey = String.format(Locale.US, "%s_%.6f_%.6f_%s_%.2f_%.2f_%d_%d_%d_%d_%d_%s%s", graphId, lat, lon, mode,
					bikeSpeed, walkSpeed, jodaDate.getYear(), jodaDate.getMonthOfYear(), jodaDate.getDayOfMonth(),
					fromTime, toTime, shapefile, (profile ? "_profile" : ""));
			shapefileId = shapefile;
			this.which = ResultEnvelope.Which.valueOf(which);
            this.showIso = showIso;
            this.showPoints = showPoints;
            this.timeLimit = timeLimit;
            this.minTime = minTime;
            this.profile = profile;
        }

        public String getId() {
            return super.getId() + "_" + resultKey + "_" + showIso + "_" + showPoints + "_" + timeLimit + "_" + minTime + (profile ? "_profile" : "");
        }

        public byte[] render(){

            Tile tile = new Tile(this);

            Shapefile shp = Shapefile.getShapefile(shapefileId);

            if(shp == null)
                return null;

            // note that this may occasionally return null if someone's had the site open for a very long
            // time because the result will have fallen out of the cache.
            ResultSetWithTimes result = (ResultSetWithTimes) SinglePoint.getResultSet(resultKey).get(which);
            
            if (result == null) {
            	return null;
            }

            List<Shapefile.ShapeFeature> features = shp.query(tile.envelope);

            PointSet ps = Shapefile.getShapefile(shapefileId).getPointSet();

            for(Shapefile.ShapeFeature feature : features) {

                Integer sampleTime = result.times[ps.getIndexForFeature(feature.id)];
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

                    for(Attribute a : shp.attributes.values()) {

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
