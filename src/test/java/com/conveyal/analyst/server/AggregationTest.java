package com.conveyal.analyst.server;

import com.conveyal.analyst.server.utils.DataStore;
import com.conveyal.analyst.server.utils.QueryResults;
import com.google.common.io.Files;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTWriter;
import junit.framework.TestCase;
import junit.framework.TestResult;
import models.Attribute;
import models.Shapefile;
import org.junit.Test;
import org.mapdb.Fun;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Test that aggregation works as expected.
 */
public class AggregationTest extends TestCase {
    static {
        AnalystMain.config.setProperty("application.data", Files.createTempDir().getAbsolutePath());
    }

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    private static Shapefile getGrid (double originX, double originY, double resolutionX, double resolutionY, int countX, int countY, IntFunction<Map<String, Object>> attributeProducer) {
        List<Polygon> ret = new ArrayList<>();

        for (int y = 0; y < countY; y++) {
            for (int x = 0; x < countX; x++) {
                Coordinate[] coords = new Coordinate[] {
                    new Coordinate(originX + x * resolutionX, originY + y * resolutionY),
                    new Coordinate(originX + (x + 1) * resolutionX, originY + y * resolutionY),
                    new Coordinate(originX + (x + 1) * resolutionX, originY + (y + 1) * resolutionY),
                    new Coordinate(originX + x * resolutionX, originY + (y + 1) * resolutionY),
                    new Coordinate(originX + x * resolutionX, originY + y * resolutionY)
                };
                ret.add(geometryFactory.createPolygon(coords));

            }
        }

        Iterator<Integer> idStream = IntStream.range(0, ret.size()).iterator();

        // convert everything to shapefeatures
        List<Fun.Tuple2<String, Shapefile.ShapeFeature>> features = ret.stream()
                .map(p -> {
                    int id = idStream.next();
                    Shapefile.ShapeFeature sf = new Shapefile.ShapeFeature();
                    sf.geom = p;
                    // zero pad so they sort in order.
                    sf.id = "feat" + String.format("%10d", id);
                    sf.attributes = attributeProducer.apply(id);

                    return Fun.t2(sf.id, sf);
                })
                .collect(Collectors.toList());

        Shapefile shp = new Shapefile();
        shp.id = UUID.randomUUID().toString();
        shp.setShapeFeatureStore(features);

        // make sure the attribute stats are up to date.
        for (Fun.Tuple2<String, Shapefile.ShapeFeature> feature : features) {
            for (Map.Entry<String, Object> attr : feature.b.attributes.entrySet()) {
                shp.updateAttributeStats(attr.getKey(), attr.getValue());
            }
        }

        return shp;
    }

    public QueryResults getQueryResultsForShapefile (Shapefile shp, Function<Shapefile.ShapeFeature, Double> getValue) {
        QueryResults qr = new QueryResults();
        qr.shapeFileId = shp.id;
        qr.minValue = qr.maxPossible = Double.MAX_VALUE;
        qr.maxValue = Double.MIN_VALUE;

        for (Shapefile.ShapeFeature sf : shp.getShapeFeatureStore().getAll()) {
            QueryResults.QueryResultItem queryResultItem = new QueryResults.QueryResultItem();
            queryResultItem.feature = sf;
            queryResultItem.value = getValue.apply(sf);
            qr.items.put(sf.id, queryResultItem);
        }

        return qr;
    }

    /** Test that aggregation works correctly when weighting by the same shapefile */
    @Test
    public void testSameShapefile () {
        // 45 degrees latitude; everything is squished in the x dimension by 50%
        Shapefile shp = getGrid(0, 45, 2e-6, 1e-6, 100, 100, i -> {
           Map<String, Object> map = new HashMap<>();
           map.put("value", i % 100);
           map.put("weight", 1);
           return map;
        });

        Shapefile contour = getGrid(0, 45, 2e-6 * 50.5, 1e-6 * 50, 1, 1, HashMap::new);

        QueryResults qr = getQueryResultsForShapefile(shp, sf -> sf.getAttribute("value").doubleValue());

        QueryResults aggregated = qr.aggregate(contour, shp, "weight");
        QueryResults.QueryResultItem aggItem = aggregated.items.values().stream().findFirst().orElse(null);

        // Independently verify the calculated values.
        // The aggregate shapefile overlaps the first 50.5 features of the first fifty rows.

        double expected = 0;
        double wsum = 0;

        int i = 0;
        for (Shapefile.ShapeFeature feature: shp.getShapeFeatureStore().getAll()) {
            int x = i++ % 100;

            // NB i has already been incremented
            if (i > 50 * 100)
                break;

            if (x < 50) {
                expected += feature.getAttribute("weight") * feature.getAttribute("value");
                wsum += feature.getAttribute("weight");
            }

            if (x == 50) {
                expected += feature.getAttribute("weight") * feature.getAttribute("value") / 2D;
                wsum += feature.getAttribute("weight") / 2D;
            }
        }

        assertEquals(expected / wsum, aggItem.value, 1e-6);
    }
}
