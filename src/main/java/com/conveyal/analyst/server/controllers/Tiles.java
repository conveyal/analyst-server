package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.tiles.AnalystTileRequest;
import com.conveyal.analyst.server.tiles.TileCache;
import com.conveyal.analyst.server.tiles.UTFIntGridRequest;
import com.conveyal.r5.analyst.cluster.ResultEnvelope;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import models.TransportScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.ByteArrayOutputStream;

import static spark.Spark.halt;

public class Tiles extends Controller {
    private static final Logger LOG = LoggerFactory.getLogger(Tiles.class);

    protected static TileCache tileCache = new TileCache();

    public static Object tileBuilder(Request request, Response res,
            final AnalystTileRequest tileRequest) {
        if (tileRequest.format.equals("png")) {
            res.type("image/png");
            return tileCache.get(tileRequest);
        } else if (tileRequest.format.equals("json") && tileRequest instanceof UTFIntGridRequest) {
            res.type("application/json");

            final UTFIntGridRequest req = (UTFIntGridRequest) tileRequest;

            int[][] grid = req.getGrid();

            // map from grid value to ID
            TIntIntMap values = new TIntIntHashMap();

            // TODO: count ints and use low values for high numbers
            // codepoint represents the UTF-8 codepoint we are using to encode this value.
            for (int row = 0, id = 1; row < 64; row++) {
                for (int col = 0; col < 64; col++) {
                    if (!values.containsKey(grid[row][col]) && grid[row][col] != Integer.MIN_VALUE)
                        values.put(grid[row][col], id++);
                }
            }

            // create the JSON
            JsonFactory jf = new JsonFactory(); // TODO: put in static final field?
            ByteArrayOutputStream bais = new ByteArrayOutputStream();
            try {
                JsonGenerator jgen = jf.createGenerator(bais);

                jgen.writeStartObject();

                // write the grid itself
                jgen.writeArrayFieldStart("grid");

                for (int row = 0; row < 64; row++) {
                    StringBuilder sb = new StringBuilder(128);

                    for (int col = 0; col < 64; col++) {
                        int id = grid[row][col] == Integer.MIN_VALUE ?
                                0 :
                                values.get(grid[row][col]);
                        sb.append(charForId(id));
                    }

                    jgen.writeString(sb.toString());
                }

                jgen.writeEndArray();

                // now write the IDs. This is pretty straightforward.
                // note that ID 0 is reserved for no data.
                jgen.writeArrayFieldStart("keys");
                jgen.writeString("");
                for (int i = 1; i <= values.size(); i++) {
                    jgen.writeString(i + "");
                }

                jgen.writeEndArray();

                // write the data
                jgen.writeObjectFieldStart("data");
                // The keys of the values map are actually the values of the pixels
                for (int val : values.keys()) {
                    int key = values.get(val);
                    jgen.writeNumberField(key + "", val);
                }
                jgen.writeEndObject();
                jgen.writeEndObject();

                jgen.close();

                return bais.toString();

            } catch (Exception e) {
                LOG.error("error creating tile", e);
                halt(INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } else {
            halt(BAD_REQUEST, "Invalid tile format");
        }

        return null;
    }

    // TODO: what does this do? Is it ever used?
    public static Object spatial(Request req, Response res) {
        String shapefileId = req.queryParams("shapefileId");
        int x = Integer.parseInt(req.queryParams("x"));
        int y = Integer.parseInt(req.queryParams("y"));
        int z = Integer.parseInt(req.queryParams("z"));
        String selectedAttributes = req.queryParams("selectedAttributes");

        AnalystTileRequest tileRequest = new AnalystTileRequest.SpatialTile(shapefileId, x, y, z,
                selectedAttributes);
        return tileBuilder(req, res, tileRequest);
    }

    public static Object shape(Request req, Response res) {
        String shapefileId = req.queryParams("shapefileId");
        int x = Integer.parseInt(req.queryParams("x"));
        int y = Integer.parseInt(req.queryParams("y"));
        int z = Integer.parseInt(req.queryParams("z"));
        String attributeName = req.queryParams("attributeName");

        AnalystTileRequest tileRequest = new AnalystTileRequest.ShapefileTile(shapefileId, x, y, z, attributeName);
        return tileBuilder(req, res, tileRequest);
    }

    public static Object query(Request req, Response res) {
        ResultEnvelope.Which which = ResultEnvelope.Which.valueOf(req.queryParams("which"));
        String queryId = req.params("queryId");
        int x = Integer.parseInt(req.params("x"));
        String[] yformat = req.params("yformat").split("\\.");
        int y = Integer.parseInt(yformat[0]);
        int z = Integer.parseInt(req.params("z"));
        String attributeName = req.queryParams("attributeName");
        String compareTo = req.params("compareTo");
        int timeLimit = Integer.parseInt(req.queryParams("timeLimit"));
        String weightByShapefile = req.queryParams("weightByShapefile");
        String weightByAttribute = req.queryParams("weightByAttribute");
        String groupBy = req.queryParams("groupBy");

        AnalystTileRequest tileRequest;
        if (compareTo == null)
            tileRequest = new AnalystTileRequest.QueryTile(queryId, x, y, z, timeLimit, weightByShapefile,
                    weightByAttribute, groupBy, which, attributeName);
        else
            tileRequest = new AnalystTileRequest.QueryComparisonTile(queryId, compareTo, x, y, z, timeLimit,
                    weightByShapefile, weightByAttribute, groupBy, which, attributeName);

        return tileBuilder(req, res, tileRequest);
    }

    public static Object transit (Request req, Response res) {
        String bundleId = req.queryParams("bundleId");

        if (bundleId == null) {
            String scenarioId = req.queryParams("scenarioId");
            TransportScenario s = TransportScenario.getScenario(scenarioId);
            bundleId = s.bundleId;
        }


        int x = Integer.parseInt(req.queryParams("x"));
        int y = Integer.parseInt(req.queryParams("y"));
        int z = Integer.parseInt(req.queryParams("z"));

        AnalystTileRequest tileRequest = new AnalystTileRequest.TransitTile(bundleId, x, y, z);
        return tileBuilder(req, res, tileRequest);

    }

    public static Object transitComparison (Request req, Response res) {
        String bundleId1 = req.queryParams("bundleId1");
        String bundleId2 = req.queryParams("bundleId2");

        if (bundleId1 == null || bundleId2 == null) {
            bundleId1 = TransportScenario.getScenario(req.queryParams("scenarioId1")).bundleId;
            bundleId2 = TransportScenario.getScenario(req.queryParams("scenarioId2")).bundleId;
        }

        int x = Integer.parseInt(req.queryParams("x"));
        int y = Integer.parseInt(req.queryParams("y"));
        int z = Integer.parseInt(req.queryParams("z"));

        AnalystTileRequest tileRequest = new AnalystTileRequest.TransitComparisonTile(bundleId1, bundleId2, x, y,
                z);
        return tileBuilder(req, res, tileRequest);
    }

    /**
     * convert a UTFGrid ID into a char sequence, per spec: https://github.com/mapbox/mbtiles-spec/blob/master/1.1/utfgrid.md.
     * may return more than one char for 3+ byte characters
     */
    public static char[] charForId(int id) {
        int codepoint = id + 32;
        if (codepoint >= 34)
            codepoint++;

        if (codepoint >= 92)
            codepoint++;

        return Character.toChars(codepoint);
    }
}
