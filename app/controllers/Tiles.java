package controllers;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import play.libs.Akka;
import play.libs.F.Function;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.*;
import scala.concurrent.ExecutionContext;
import tiles.AnalystTileRequest;
import tiles.AnalystTileRequest.TransitTile;
import tiles.AnalystTileRequest.TransitComparisonTile;
import tiles.AnalystTileRequest.SpatialTile;
import tiles.SurfaceTile;
import tiles.SurfaceComparisonTile;
import tiles.AnalystTileRequest.QueryTile;
import tiles.AnalystTileRequest.QueryComparisonTile;
import tiles.AnalystTileRequest.ShapefileTile;
import tiles.TileCache;
import tiles.UTFIntGridRequest;
import utils.PromiseUtils;
import utils.QueryResults;
import utils.ResultEnvelope;

@Security.Authenticated(Secured.class)
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
		
		if (tileRequest.format.equals("png")) {
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
		else if (tileRequest.format.equals("json") && tileRequest instanceof UTFIntGridRequest) {
			response().setContentType("application/json");

			final UTFIntGridRequest req = (UTFIntGridRequest) tileRequest;
			
			return Promise.promise(
					new Function0<Result> () {
						@Override
						public Result apply() throws Throwable {
							int[][] grid = req.getGrid();
							
							// map from grid value to ID
							TIntIntMap values = new TIntIntHashMap();
							
							// TODO: count ints and use low values for high numbers
							// codepoint represents the UTF-8 codepoint we are using to encode this value.
							for (int row = 0, id = 1; row < 128; row++) {
								for (int col = 0; col < 128; col++) {
									if (!values.containsKey(grid[row][col]))
										values.put(grid[row][col], id++);
								}
							}
							
							// create the JSON
							JsonFactory jf = new JsonFactory(); // TODO: put in static final field?
							ByteArrayOutputStream bais = new ByteArrayOutputStream();
							JsonGenerator jgen = jf.createGenerator(bais);
							
							jgen.writeStartObject();
							
							// write the grid itself
							jgen.writeArrayFieldStart("grid");
							
							for (int row = 0; row < 128; row++) {
								StringBuilder sb = new StringBuilder(128);
								
								for (int col = 0; col < 128; col++) {
									sb.append(charForId(values.get(grid[row][col])));
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
							
							return ok(bais.toString());
						}
					}
				);

		}
		else {
			return PromiseUtils.<Result>resolveNow((Result) badRequest());
		}
	}

	/** convert a UTFGrid ID into a char sequence, per spec: https://github.com/mapbox/mbtiles-spec/blob/master/1.1/utfgrid.md.
	 * may return more than one char for 3+ byte characters */
	public static char[] charForId (int id) {
		int codepoint = id + 32;
		if (codepoint >= 34)
			codepoint++;
		
		if (codepoint >= 92)
			codepoint++;
		
		return Character.toChars(codepoint);
	}

	public static Promise<Result> spatial(String shapefileId, Integer x, Integer y, Integer z, String selectedAttributes) {


		AnalystTileRequest tileRequest = new SpatialTile(shapefileId, x, y, z, selectedAttributes);
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> shape(String shapefileId, Integer x, Integer y, Integer z, String attributeName) {

		AnalystTileRequest tileRequest = new ShapefileTile(shapefileId, x, y, z, attributeName);
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> surface(String shapefile, String graphId, Double lat, Double lon, String mode,
			   Double bikeSpeed, Double walkSpeed, String which, String date, int fromTime, int toTime, Integer x, Integer y, Integer z,
			   Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime, boolean profile) {

		AnalystTileRequest tileRequest = new SurfaceTile(graphId, lat, lon, mode, shapefile,
				   bikeSpeed, walkSpeed, which, date, fromTime, toTime, x, y, z,
				   showIso, showPoints, timeLimit, minTime, profile);
		return tileBuilder(tileRequest);

    }

	public static Promise<Result> surfaceComparison(String shapefile, String graphId, String graphId2, Double lat, Double lon, String mode,
			   Double bikeSpeed, Double walkSpeed, String which, String date, int fromTime, int toTime, Integer x, Integer y, Integer z,
			   Boolean showIso, Boolean showPoints, Integer timeLimit, Integer minTime, boolean profile, String format) {

		AnalystTileRequest tileRequest = new SurfaceComparisonTile(graphId, graphId2, lat, lon, mode, shapefile,
				   			bikeSpeed, walkSpeed, which, date, fromTime, toTime, x, y, z,
				   			showIso, showPoints, timeLimit, minTime, profile, format);
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> query(String queryId, Integer x, Integer y, Integer z,
			Integer timeLimit, String weightByShapefile, String weightByAttribute, String groupBy,
			String which, String attributeName, String compareTo) {

		ResultEnvelope.Which whichEnum;
		try {
			whichEnum = ResultEnvelope.Which.valueOf(which);
		} catch (Exception e) {
			// no need to pollute the console with a stack trace
			return Promise.promise(new Function0<Result> () {
				@Override
				public Result apply() throws Throwable {
				    return badRequest("Invalid value for which parameter");
				}
			});
		}

		AnalystTileRequest tileRequest;
		
		if (compareTo == null)
			tileRequest = new QueryTile(queryId, x, y, z, timeLimit, weightByShapefile, weightByAttribute, groupBy, whichEnum, attributeName);
		else
			tileRequest = new QueryComparisonTile(queryId, compareTo, x, y, z, timeLimit, weightByShapefile, weightByAttribute, groupBy, whichEnum, attributeName);
		
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> transit(final String scenarioId, final Integer x, final Integer y, final Integer z) {

		AnalystTileRequest tileRequest = new TransitTile(scenarioId, x, y, z);
		return tileBuilder(tileRequest);

	}

	public static Promise<Result> transitComparison(String scenarioId1, String scenarioId2, Integer x, Integer y, Integer z) {

		AnalystTileRequest tileRequest = new TransitComparisonTile(scenarioId1, scenarioId2, x, y, z);
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
