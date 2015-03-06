package tiles;

import controllers.Api;
import models.Attribute;
import models.Shapefile;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSetDelta;
import org.opentripplanner.analyst.SampleSet;
import org.opentripplanner.analyst.TimeSurface;
import utils.HaltonPoints;

import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
* Created by matthewc on 3/4/15.
*/
public class SurfaceComparisonTile extends AnalystTileRequest {

    final String shapefileId;
    final Integer surfaceId1;
    final Integer surfaceId2;
    final Boolean showIso;
    final Boolean showPoints;
    final Integer timeLimit;
    final Integer minTime;

    public SurfaceComparisonTile(Integer surfaceId1, Integer surfaceId2, String shapefileId,
								 Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {
        super(x, y, z, "surface");

        this.shapefileId = shapefileId;
        this.surfaceId1 = surfaceId1;
        this.surfaceId2 = surfaceId2;
        this.showIso = showIso;
        this.showPoints = showPoints;
        this.timeLimit = timeLimit;
        this.minTime = minTime;
    }

    public String getId() {
        return super.getId() + "_" + shapefileId + "_" + surfaceId1 + "_" + surfaceId2 + "_" + showIso + "_" + showPoints + "_" + timeLimit + "_" + minTime;
    }

    public byte[] render(){

        /*Tile tile = new Tile(this);

        Shapefile shp = Shapefile.getShapefile(shapefileId);

        if(shp == null)
            return null;

        TimeSurface surf1 = AnalystProfileRequest.getSurface(surfaceId1);
        if (surf1 == null)
            surf1 = AnalystRequest.getSurface(surfaceId1);

        TimeSurface surf2 = AnalystProfileRequest.getSurface(surfaceId2);
        if (surf2 == null)
            surf2 = AnalystRequest.getSurface(surfaceId2);

        PointSet ps = shp.getPointSet();

        // TODO: cache samples on multiple tile requests (should be a performance win)
        SampleSet ss1 = ps.getSampleSet(Api.analyst.getGraph(surf1.routerId));
        SampleSet ss2 = ps.getSampleSet(Api.analyst.getGraph(surf2.routerId));
        ResultSetDelta resultDelta = new ResultSetDelta(ss1, ss2,  surf1, surf2);


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
        }*/
    	return null;

    }
}
