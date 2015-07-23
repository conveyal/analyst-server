package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.tiles.AnalystTileRequest;
import com.conveyal.analyst.server.tiles.SurfaceComparisonTile;
import com.conveyal.analyst.server.tiles.SurfaceTile;
import org.opentripplanner.analyst.cluster.ResultEnvelope;
import spark.Request;
import spark.Response;

/**
 * Tiles for single point.
 */
public class SinglePointTiles extends Tiles {
	public static Object single(Request req, Response res) {
		String key = req.params("key");
		ResultEnvelope.Which which = ResultEnvelope.Which.valueOf(req.queryParams("which"));
		int x = Integer.parseInt(req.params("x"));
		// this is a bit of a hack: writing :y.png yields a parameter named "y.png" not one named "y" with suffix .png
		String[] yformat = req.params("yformat").split("\\.");
		int y = Integer.parseInt(yformat[0]);
		int z = Integer.parseInt(req.params("z"));
		// defaults: iso on, points off, time limit 1h.
		boolean showIso = !Boolean.FALSE.equals(Boolean.parseBoolean(req.queryParams("showIso")));
		boolean showPoints = Boolean.TRUE.equals(Boolean.parseBoolean(req.queryParams("showPoints")));
		int timeLimit = req.queryParams("timeLimit") != null ? Integer.parseInt(req.queryParams("timeLimit")) : 3600;

		AnalystTileRequest tileRequest =
				new SurfaceTile(key, which, x, y, z, showIso, showPoints, timeLimit);
		return tileBuilder(req, res, tileRequest);
	}

	public static Object compare (Request req, Response res) {
		String key1 = req.params("key1");
		String key2 = req.params("key2");
		ResultEnvelope.Which which = ResultEnvelope.Which.valueOf(req.queryParams("which"));
		int x = Integer.parseInt(req.params("x"));
		String[] yformat = req.params("yformat").split("\\.");
		int y = Integer.parseInt(yformat[0]);
		int z = Integer.parseInt(req.params("z"));
		// defaults: iso on, points off, time limit 1h.
		boolean showIso = !Boolean.FALSE.equals(Boolean.parseBoolean(req.queryParams("showIso")));
		boolean showPoints = Boolean.TRUE.equals(Boolean.parseBoolean(req.queryParams("showPoints")));
		int timeLimit = req.queryParams("timeLimit") != null ? Integer.parseInt(req.queryParams("timeLimit")) : 3600;
		String format = yformat[1];

		AnalystTileRequest tileRequest = new SurfaceComparisonTile(key1, key2, which, x, y, z,
				   			showIso, showPoints, timeLimit, format);
		return tileBuilder(req, res, tileRequest);
 }
}
