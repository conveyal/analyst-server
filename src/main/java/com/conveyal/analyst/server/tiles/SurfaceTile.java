package com.conveyal.analyst.server.tiles;

import com.conveyal.analyst.server.controllers.SinglePoint;
import com.conveyal.analyst.server.utils.HaltonPoints;
import models.Attribute;
import models.Shapefile;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.cluster.ResultEnvelope;

import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
* Created by matthewc on 3/4/15.
*/
public class SurfaceTile extends AnalystTileRequest {
		final String resultKey;
		final ResultEnvelope.Which which;
        final Boolean showIso;
        final Boolean showPoints;
        final Integer timeLimit;

        public SurfaceTile(String key, ResultEnvelope.Which which, Integer x, Integer y, Integer z,
						   Boolean showIso, Boolean showPoints, Integer timeLimit) {
            super(x, y, z, "surface");
            
			resultKey = key;
			this.which = which;
            this.showIso = showIso;
            this.showPoints = showPoints;
            this.timeLimit = timeLimit;
        }

        public String getId() {
            return super.getId() + "_" + resultKey + "_" + which + "_" + showIso + "_" + showPoints + "_" + timeLimit;
        }

        public byte[] render(){
            // note that this may occasionally return null if someone's had the site open for a very long
            // time because the result will have fallen out of the cache.
            ResultEnvelope env = SinglePoint.getResultSet(resultKey);
            ResultSet result = env.get(which);
            
            if (result == null) {
            	return null;
            }
        	
            Tile tile = new Tile(this);

            Shapefile shp = Shapefile.getShapefile(env.destinationPointsetId);

            if(shp == null)
                return null;

            List<Shapefile.ShapeFeature> features = shp.query(tile.envelope);

            PointSet ps = shp.getPointSet();

            for(Shapefile.ShapeFeature feature : features) {

                Integer sampleTime = result.times[ps.getIndexForFeature(feature.id)];
                if(sampleTime == null)
                    continue;

                if(sampleTime == Integer.MAX_VALUE)
                    continue;

                if(showIso) {

                    Color color = null;

                     if(sampleTime < timeLimit){
                         float opacity = 1.0f - (float)((float)sampleTime / (float)timeLimit);
                         color = new Color(0.9f,0.7f,0.2f,opacity);
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

                // draw halton points for indicator

                if(showPoints && sampleTime < timeLimit) {

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
