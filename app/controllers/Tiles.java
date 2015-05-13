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

public abstract class Tiles extends Controller {

	protected static TileCache tileCache = new TileCache();

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
							for (int row = 0, id = 1; row < 64; row++) {
								for (int col = 0; col < 64; col++) {
									if (!values.containsKey(grid[row][col]) && grid[row][col] != Integer.MIN_VALUE)
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
							
							for (int row = 0; row < 64; row++) {
								StringBuilder sb = new StringBuilder(128);
								
								for (int col = 0; col < 64; col++) {
									int id = grid[row][col] == Integer.MIN_VALUE ? 0 : values.get(grid[row][col]);
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
}
