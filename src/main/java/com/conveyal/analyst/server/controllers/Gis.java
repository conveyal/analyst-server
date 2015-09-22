package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.DirectoryZip;
import com.conveyal.analyst.server.utils.HashUtils;
import com.conveyal.analyst.server.utils.QueryResults;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.sun.javafx.geom.Shape;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import models.Attribute;
import models.Query;
import models.Shapefile;
import models.Shapefile.ShapeFeature;
import models.User;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opentripplanner.analyst.PointSet;
import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.analyst.cluster.ResultEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static spark.Spark.halt;
public class Gis extends Controller {
	private static final Logger LOG = LoggerFactory.getLogger(Gis.class);

	public static Object query(Request req, Response res) throws Exception {
    	String queryId = req.queryParams("queryId");
		int timeLimit = Integer.parseInt(req.queryParams("timeLimit"));
		String weightByShapefile = req.queryParams("weightByShapefile");
		String weightByAttribute = req.queryParams("weightByAttribute");
		String groupBy = req.queryParams("groupBy");
		String compareTo = req.queryParams("compareTo");

		Query query = Query.getQuery(queryId);

		Query query2 = compareTo != null ? Query.getQuery(compareTo) : null;

		User u = currentUser(req);

		if(query == null || (query2 == null && compareTo != null) ||
				!u.hasReadPermission(query.projectId) ||
				(query2 != null && !u.hasReadPermission(query2.projectId)))
			halt(NOT_FOUND, "Could not find query or you do not have access to it");

		if (compareTo != null &&
				(!query.originShapefileId.equals(query2.originShapefileId) || !query.destinationShapefileId.equals(query2.destinationShapefileId)))
			halt(BAD_REQUEST, "Shapefiles must match for comparison");

		Shapefile shpNorm = weightByShapefile != null ? Shapefile.getShapefile(weightByShapefile) : null;
		// these are not the origins of the search but the output geographies of the query
		Shapefile outputFeatures = groupBy != null ? Shapefile.getShapefile(groupBy) : Shapefile.getShapefile(query.originShapefileId);
		Shapefile destinations = Shapefile.getShapefile(query.destinationShapefileId);

		// get query results for every attribute and envelope param
		List<QueryResults> results = new ArrayList<>();

		ResultEnvelope.Which[] params;
		if (query.isTransit())
			params = new ResultEnvelope.Which[] { ResultEnvelope.Which.WORST_CASE, ResultEnvelope.Which.AVERAGE, ResultEnvelope.Which.BEST_CASE };
		else
			params = new ResultEnvelope.Which[] { ResultEnvelope.Which.AVERAGE };

		List<Attribute> destinationAttributes = destinations.getShapeAttributes().stream()
				.filter(a -> a.hide == null || !a.hide)
				.collect(Collectors.toList());

		// NB loop over attributes is outside loop. Must retain this pattern when creating field names.
		for (Attribute a : destinationAttributes) {
			for (ResultEnvelope.Which which : params) {
				String queryKey = queryId + "_" + timeLimit + "_" + which + "_" + a.fieldName;

				synchronized (QueryResults.queryResultsCache) {
					QueryResults qr;

					if (!QueryResults.queryResultsCache.containsKey(queryKey)) {
						qr = new QueryResults(query, timeLimit, which, a.fieldName);
						QueryResults.queryResultsCache.put(queryKey, qr);
					} else
						qr = QueryResults.queryResultsCache.get(queryKey);

					if (compareTo != null) {
						String q2key = compareTo + "_" + timeLimit + "_" + which + "_" + a.fieldName;
						QueryResults qr2;

						if (!QueryResults.queryResultsCache.containsKey(q2key)) {
							qr2 = new QueryResults(query2, timeLimit, which, a.fieldName);
							QueryResults.queryResultsCache.put(q2key, qr2);
						} else {
							qr2 = QueryResults.queryResultsCache.get(q2key);
						}

						qr = qr.subtract(qr2);
					}

					if (weightByShapefile != null) {
						if(groupBy == null)
							halt(BAD_REQUEST, "Must specify a weight by clause when specifying a normalize by clause!");

						qr = qr.aggregate(outputFeatures, shpNorm, weightByAttribute);
					}

					results.add(qr);
				}
			}
		}

		List<Attribute> outputAttributes = outputFeatures.getShapeAttributes().stream()
				.filter(a -> a.hide == null || !a.hide)
				.collect(Collectors.toList());

		Collection<ShapeFeature> features = outputFeatures.getShapeFeatureStore().getAll();

		List<String> fields = new ArrayList<String>();
		List<String> fieldTypes = new ArrayList<>();

		// add fields from the origins as well
		for (Attribute a : outputAttributes) {
			fields.add("o_" + a.fieldName);
			fieldTypes.add(a.numeric ? "Double" : "String");
		}

		// NB loop over attributes is outside loop, matches order that query results are in.
		for (Attribute a : destinationAttributes) {
			for (ResultEnvelope.Which which : params) {
				fields.add(which.toString().substring(0, 1) + "_" + a.fieldName);
				fieldTypes.add(a.numeric ? "Double" : "String");
			}
		}

		// shapefiles can only have 255 fields
		if (fields.size() > 255)
			halt("Too many fields to export shapefile. Hide some fields and try again.");

		ArrayList<GisShapeFeature> gisFeatures = new ArrayList<>();

		for (ShapeFeature feature : features) {
			GisShapeFeature gf = new GisShapeFeature();
			gf.geom = feature.geom;
			gf.id = feature.id;

			for (Attribute a : outputAttributes) {
				gf.fields.add(feature.getAttribute(a.fieldName));
			}

			// NB these come from a loop with attributes on the outside so they match field order
			for (QueryResults qr : results) {
				if (qr.items.containsKey(feature.id)) {
					gf.fields.add(qr.items.get(feature.id).value);
				}
				else
					// preserve positional information
					gf.fields.add(null);
			}

			gisFeatures.add(gf);
		}

		String shapeName = query.name + (query2 != null ? "_" + query2.name : "");

		res.header("Content-Disposition", "attachment; filename=" + shapeName + ".zip");

		generateZippedShapefile(shapeName, fields, gisFeatures, false, false, fieldTypes, res);
		return "";
    }
	

	/**
     * Get a ResultSet.
     */
    public static String single(Request req, Response res) throws Exception {
    	ResultEnvelope.Which whichEnum = ResultEnvelope.Which.valueOf(req.queryParams("which"));
		String key = req.queryParams("key");

		ResultEnvelope env = SinglePoint.getResultSet(key);
		ResultSet result = env.get(whichEnum);

		final Shapefile shp = Shapefile.getShapefile(env.destinationPointsetId);

		res.header("Content-Disposition", "attachment; filename=" + shp.name.replaceAll("[^a-zA-Z0-9]", "") + "_time.zip");

		Collection<ShapeFeature> features = shp.getShapeFeatureStore().getAll();

		ArrayList<String> fields = new ArrayList<String>();

		for (Attribute a : shp.attributes.values()) {
			if (a.numeric) {
				fields.add(a.name);
			}
		}

		ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();

		PointSet ps = shp.getPointSet();

		for (ShapeFeature feature : features) {

			int sampleTime = result.times[ps.getIndexForFeature(feature.id)];
			GisShapeFeature gf = new GisShapeFeature();
			gf.geom = feature.geom;
			gf.id = feature.id;
			gf.time = sampleTime != Integer.MAX_VALUE ? sampleTime : null;

			// TODO: handle non-integer attributes
			for (Attribute a : shp.attributes.values()) {
				if (a.numeric) {
					gf.fields.add(feature.getAttribute(a.name));
				}
			}

			gisFeatures.add(gf);

		}

		String shapeName = shp.name.toLowerCase().replaceAll("\\W", "") + "_time";

		generateZippedShapefile(shapeName, fields, gisFeatures, false, true, res);
		return "";
    }
    
	/**
     * Get a comparison.
     */
    public static String singleComparison(Request req, Response res) throws Exception {
		ResultEnvelope.Which whichEnum = ResultEnvelope.Which.valueOf(req.queryParams("which"));
		String key1 = req.queryParams("key1");
		String key2 = req.queryParams("key2");

		ResultEnvelope env1 = SinglePoint.getResultSet(key1);
		ResultSet result = env1.get(whichEnum);
		ResultEnvelope env2 = SinglePoint.getResultSet(key2);
		ResultSet result2 = env2.get(whichEnum);

		if (env1 == null || env2 == null)
			halt(NOT_FOUND, "no such key");

		if (!env1.destinationPointsetId.equals(env2.destinationPointsetId))
			halt(BAD_REQUEST, "pointsets must match");

		final Shapefile shp = Shapefile.getShapefile(env1.destinationPointsetId);

		res.header("Content-Disposition", "attachment; filename=" + shp.name.replaceAll("[^a-zA-Z0-9]", "") + "_compare.zip");

		Collection<ShapeFeature> features = shp.getShapeFeatureStore().getAll();

		ArrayList<String> fields = new ArrayList<String>();

		for (Attribute a : shp.attributes.values()) {
			if (a.numeric) {
				fields.add(a.name);
			}
		}

		ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();

		PointSet ps = shp.getPointSet();

		for (ShapeFeature feature : features) {

			int fidx = ps.getIndexForFeature(feature.id);
			GisShapeFeature gf = new GisShapeFeature();
			gf.geom = feature.geom;
			gf.id = feature.id;

			int time1 = result.times[fidx];
			gf.time = time1 != Integer.MAX_VALUE ? time1 : null;
			int time2 = result2.times[fidx];
			gf.time2 = time2 != Integer.MAX_VALUE ? time2 : null;

			if (gf.time != null && gf.time2 != null)
				gf.difference = gf.time2 - gf.time;
			else
				gf.difference = null;

			// TODO: handle non-integer attributes
			for (Attribute a : shp.attributes.values()) {
				if (a.numeric) {
					gf.fields.add(feature.getAttribute(a.name));
				}
			}

			gisFeatures.add(gf);

		}

		String shapeName = shp.name.toLowerCase().replaceAll("\\W", "") + "_time_diff";

		generateZippedShapefile(shapeName, fields, gisFeatures, true, true, res);
		return "";
	}
	
	public static class GisShapeFeature {
		
		Geometry geom;
		String id;
		Integer time;
		Integer time2;
		Integer difference;
		ArrayList<Object> fields = new ArrayList<Object>();
		
	}

	/** generate a zipped shapefile with field types inferred automatically. Won't work with null values */
	static void generateZippedShapefile (String fileName, List<String> fieldNames, List<GisShapeFeature> features,
										 boolean difference, boolean includeTimeFields, Response res) throws Exception {
		generateZippedShapefile(fileName, fieldNames, features, difference, includeTimeFields, null, res);
	}

	/**
	 * Generate a zipped shapefile and send it to the response res. Specify field names and optionally field types
	 * (field types are non optional if there are null values, as dynamic type inference won't work on nulls).
	 */
	static void generateZippedShapefile(String fileName, List<String> fieldNames, List<GisShapeFeature> features,
										boolean difference, boolean includeTimeFields, List<String> fieldTypes, Response res)
			throws Exception {
			
		String shapeFileId = HashUtils.hashString("shapefile_" + (new Date()).toString()).substring(0, 6) + "_" + fileName;

		File outputDirectory = Files.createTempDir();
		File outputShapefile = new File(outputDirectory, shapeFileId + ".shp");

		if(!outputDirectory.exists())
		{
			outputDirectory.mkdirs();
		}

		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<String, Serializable>();
		params.put("url", outputShapefile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore dataStore = (ShapefileDataStore)dataStoreFactory.createNewDataStore(params);
		dataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);


		String featureDefinition = null;

		if(features.size() > 0 && features.get(0).geom instanceof Point)
			featureDefinition = "the_geom:Point:srid=4326,id:String,time:Integer";
		else
			featureDefinition = "the_geom:MultiPolygon:srid=4326,id:String";

		if (includeTimeFields)
			featureDefinition += ",time:Integer";

		if (difference && includeTimeFields)
			featureDefinition += ",time2:Integer,difference:Integer";

		int fieldPosition = 0;

		Set<String> usedFieldNames = new HashSet<>();

		for(String fieldName : fieldNames) {

			String shortFieldName = fieldName;

			if(fieldName.length() > 10)
				shortFieldName = fieldName.substring(0, 10);

			int i = 0;
			while (usedFieldNames.contains(shortFieldName)) {
				shortFieldName = shortFieldName.substring(0, 8) + i++;
			}

			usedFieldNames.add(shortFieldName);

			featureDefinition += "," + shortFieldName + ":";


			if (fieldTypes == null) {
				if (features.get(0).fields.get(fieldPosition) instanceof String)
					featureDefinition += "String";
				else if (features.get(0).fields.get(fieldPosition) instanceof Number)
					featureDefinition += "Double";
				else {
					LOG.error("Cannot process field of type {}", features.get(0).fields.get(fieldPosition).getClass());
				}
			} else {
				featureDefinition += fieldTypes.get(fieldPosition);
			}
			fieldPosition++;
		}

		SimpleFeatureType featureType = DataUtilities.createType("Analyst", featureDefinition);

		SimpleFeatureBuilder featureBuilder = null;

		List<SimpleFeature> featureList = new ArrayList<SimpleFeature>();

		dataStore.createSchema(featureType);
		featureBuilder = new SimpleFeatureBuilder(featureType);

		for(GisShapeFeature feature : features)
		{
			if(feature.geom instanceof Point	)
				featureBuilder.add((Point)feature.geom);
			else
				featureBuilder.add((MultiPolygon)feature.geom);
			featureBuilder.add(feature.id);

			if (includeTimeFields)
			featureBuilder.add(feature.time);

			if (difference && includeTimeFields) {
				featureBuilder.add(feature.time2);
				featureBuilder.add(feature.difference);
			}


			for(Object o : feature.fields)
				featureBuilder.add(o);

			SimpleFeature f = featureBuilder.buildFeature(null);
			featureList.add(f);

		}

		ListFeatureCollection featureCollection = new ListFeatureCollection(featureType, featureList);

		Transaction transaction = new DefaultTransaction("create");

		String typeName = dataStore.getTypeNames()[0];
		SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);

		if (featureSource instanceof SimpleFeatureStore)
		{
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

			featureStore.setTransaction(transaction);

			featureStore.addFeatures(featureCollection);
			transaction.commit();

			transaction.close();
		}
		else
		{
			throw new Exception(typeName + " does not support read/write access");
		}

		res.header("Content-Type", "application/x-zip");

		// buffer to a file
		File temp = File.createTempFile("shapefile", "zip");
		FileOutputStream fos = new FileOutputStream(temp);
		DirectoryZip.zip(outputDirectory, fos);
		fos.close();

		FileInputStream fis = new FileInputStream(temp);
		OutputStream os = res.raw().getOutputStream();
		ByteStreams.copy(fis, os);
		os.close();
		fis.close();
		temp.delete();
		outputDirectory.delete();
	}
}
