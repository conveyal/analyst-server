package controllers;

import play.Play;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Result;
import tiles.AnalystTileRequest;
import tiles.SurfaceComparisonTile;
import tiles.SurfaceTile;
import org.opentripplanner.analyst.cluster.ResultEnvelope;

/**
 * Tiles for single point. Intentionally not annotated with @Security.Authenticated;
 * as it is possible to turn off auth for this controller and thus auth is handled
 * individually.
 */
public class SinglePointTiles extends Tiles {
	public static Promise<Result> surface(String key, String which, Integer x, Integer y, Integer z,
			   Boolean showIso, Boolean showPoints, Integer timeLimit) {
    	if (session().get("username") == null &&
    			Play.application().configuration().getBoolean("api.allow-unauthenticated-access") != true)
    		return F.Promise.pure((Result) unauthorized());
		
		ResultEnvelope.Which whichEnv = ResultEnvelope.Which.valueOf(which);
		
		AnalystTileRequest tileRequest =
				new SurfaceTile(key, whichEnv, x, y, z, showIso, showPoints, timeLimit);
		return tileBuilder(tileRequest);
	}

	public static Promise<Result> surfaceComparison(String key1, String key2, String which, Integer x, Integer y, Integer z,
			   Boolean showIso, Boolean showPoints, Integer timeLimit, String format) {
    	if (session().get("username") == null &&
    			Play.application().configuration().getBoolean("api.allow-unauthenticated-access") != true)
    		return F.Promise.pure((Result) unauthorized());

		ResultEnvelope.Which whichEnv = ResultEnvelope.Which.valueOf(which);
		
		AnalystTileRequest tileRequest = new SurfaceComparisonTile(key1, key2, whichEnv, x, y, z,
				   			showIso, showPoints, timeLimit, format);
		return tileBuilder(tileRequest);
 }
}
