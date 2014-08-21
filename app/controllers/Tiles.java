package controllers;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import models.Query;
import models.Shapefile.ShapeFeature;
import models.SpatialLayer;
import models.Attribute;

import org.opentripplanner.analyst.ResultFeatureDelta;
import org.opentripplanner.analyst.ResultFeatureWithTimes;
import org.opentripplanner.analyst.TimeSurface;
import org.opentripplanner.api.model.TimeSurfaceShort;
import org.opentripplanner.common.model.GenericLocation;

import otp.AnalystRequest;

import com.vividsolutions.jts.index.strtree.STRtree;

import play.libs.Akka;
import play.libs.Json;
import play.libs.F.Function;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.*;
import scala.concurrent.ExecutionContext;
import tiles.Tile;
import tiles.AnalystTileRequest;
import tiles.AnalystTileRequest.TransitTile;
import tiles.AnalystTileRequest.SpatialTile;
import tiles.AnalystTileRequest.SurfaceTile;
import tiles.AnalystTileRequest.SurfaceCompareTile;
import tiles.AnalystTileRequest.QueryTile;
import tiles.TileCache;
import utils.HaltonPoints;
import utils.QueryResults;
import utils.QueryResults.QueryResultItem;
import utils.TransportIndex;
import utils.TransportIndex.TransitSegment;

public class Tiles extends Controller {
	
	private static TileCache tileCache = new TileCache();

	public static void resetTileCache() {
		tileCache.clear();
	}

	public static void resetQueryCache(String queryId) {
		QueryResults.queryResultsCache.remove(queryId);
		tileCache.clear();
	}

	public static Promise<Result> tileBuilder(final AnalystTileRequest tileRequest) {
		
		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");
		response().setContentType("image/png");

		ExecutionContext tileContext = Akka.system().dispatchers().lookup("contexts.tile-analyst-context");
		
		Promise<byte[]> promise = Promise.promise(
		    new Function0<byte[]>() {
		      public byte[] apply() {	
		    	  return tileCache.get(tileRequest);
		      }
		    }, tileContext
		  );
		Promise<Result> promiseOfResult = promise.map(
		    new Function<byte[], Result>() {
		      public Result apply(byte[] response) {
		    	
		    	if(response == null)
		    	  return notFound();
		    	
		    	ByteArrayInputStream bais = new ByteArrayInputStream(response);
			    
				return ok(bais);
		      }
		    }, tileContext
		  );
		
		return promiseOfResult;
	}
	
	
	public static Promise<Result> spatial(String pointSetId, Integer x, Integer y, Integer z, String selectedAttributes) {


		AnalystTileRequest tileRequest = new SpatialTile(pointSetId, x, y, z, selectedAttributes);
		return tileBuilder(tileRequest);
    }


	public static Promise<Result> surface(Integer surfaceId, String pointSetId, Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {

		AnalystTileRequest tileRequest = new SurfaceTile( surfaceId, pointSetId, x, y, z, showIso, showPoints, timeLimit, minTime);
		return tileBuilder(tileRequest);
		
    }
	
	public static Promise<Result> compare(Integer surfaceId1, Integer surfaceId2, String spatialId, Integer x, Integer y, Integer z, Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime) {

		AnalystTileRequest tileRequest = new SurfaceCompareTile(surfaceId1, surfaceId2, spatialId, x, y, z, showIso, showPoints, timeLimit, minTime);
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> query(String queryId, Integer x, Integer y, Integer z, Integer timeLimit, String normalizeBy, String groupBy) {

		AnalystTileRequest tileRequest = new QueryTile(queryId, x, y, z, timeLimit, normalizeBy, groupBy);
		return tileBuilder(tileRequest);
    }


	public static Promise<Result> transit(final String scenarioId, final Integer x, final Integer y, final Integer z) {
	
		AnalystTileRequest tileRequest = new TransitTile(scenarioId, x, y, z);
		return tileBuilder(tileRequest);
		
	}

	public static Result traffic(String scenarioId, Integer x, Integer y, Integer z) {

		/*response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");

		String tileIdPrefix = "transit_" + scenarioId;

    	Tile tile = new Tile(tileIdPrefix, x, y, z);

    	try {
	    	if(!tileCache.containsKey(tile.tileId)) {
	    		//Api.analyst.getGraph(scenarioId).getGeomIndex().

	    		//for(TransitSegment ts : segments) {
	    		//	Color color;

	    		//	color = new Color(0.6f,0.6f,1.0f,0.25f);

	    		//	tile.renderLineString(ts.geom, color);
	    		//}
	    		//tileCache.put(tile.tileId, tile.generateImage());
	    	}

			ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId));

		    response().setContentType("image/png");
			return ok(bais);
		

		} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    } */
		
		return ok();

	}


}
