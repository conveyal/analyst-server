package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.DirectoryZip;
import com.conveyal.analyst.server.utils.HashUtils;
import com.conveyal.analyst.server.utils.QueryResults;
import com.google.common.io.Files;
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

import java.io.File;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.*;

import static spark.Spark.halt;
public class Gis extends Controller {
	private static final Logger LOG = LoggerFactory.getLogger(Gis.class);

	public static Object query(Request req, Response res) throws Exception {
    	String queryId = req.queryParams("queryId");
		int timeLimit = Integer.parseInt(req.queryParams("timeLimit"));
		String weightByShapefile = req.queryParams("weightByShapefile");
		String weightByAttribute = req.queryParams("weightByAttribute");
		String attributeName = req.queryParams("attributeName");
		String groupBy = req.queryParams("groupBy");
		String compareTo = req.queryParams("compareTo");
		ResultEnvelope.Which which = ResultEnvelope.Which.valueOf(req.queryParams("which"));
		
		Query query = Query.getQuery(queryId);
		
		Query query2 = compareTo != null ? Query.getQuery(compareTo) : null;

		User u = currentUser(req);

		if(query == null || (query2 == null && compareTo != null) ||
				!u.hasReadPermission(query.projectId) ||
				(query2 != null && !u.hasReadPermission(query2.projectId)));
			halt(NOT_FOUND, "Could not find query or you do not have access to it");

		String shapeName = (timeLimit / 60) + "_mins_" + which.toString().toLowerCase() + "_";

		String queryKey = queryId + "_" + timeLimit + "_" + which + "_" + attributeName;

		QueryResults qr, qr2;

		synchronized(QueryResults.queryResultsCache) {
			if(!QueryResults.queryResultsCache.containsKey(queryKey)) {
				qr = new QueryResults(query, timeLimit, which, attributeName);
				QueryResults.queryResultsCache.put(queryKey, qr);
			}
			else
				qr = QueryResults.queryResultsCache.get(queryKey);

			if (compareTo != null) {
				String q2key = compareTo + "_" + timeLimit + "_" + which;

				if(!QueryResults.queryResultsCache.containsKey(q2key)) {
					qr2 = new QueryResults(query2, timeLimit, which, attributeName);
					QueryResults.queryResultsCache.put(q2key, qr2);
				}
				else {
					qr2 = QueryResults.queryResultsCache.get(q2key);
				}

				qr = qr.subtract(qr2);
			}
		}

		Shapefile shp = Shapefile.getShapefile(query.shapefileId);


		Collection<ShapeFeature> features = shp.getShapeFeatureStore().getAll();

		if(weightByShapefile == null) {

			ArrayList<String> fields = new ArrayList<String>();

			fields.add(shp.name.replaceAll("\\W+", ""));

			ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();

			for(ShapeFeature feature : features) {

				if(qr.items.containsKey(feature.id)) {
					GisShapeFeature gf = new GisShapeFeature();
					gf.geom = feature.geom;
					gf.id = feature.id;

					gf.fields.add(qr.items.get(feature.id).value);

					gisFeatures.add(gf);
				}
			}

			shapeName += "access_" + shp.name.replaceAll("\\W+", "").toLowerCase() +
					(query2 != null ? "_compare_" + query2.name : "");

			generateZippedShapefile(shapeName, fields, gisFeatures, false, res);
			return "";
		}
		else {



			if(groupBy == null) {

				halt(BAD_REQUEST, "Must specify a weight by clause when specifying a normalize by clause!");

			}
			else {


				Shapefile shpNorm = Shapefile.getShapefile(weightByShapefile);
				Shapefile aggregateToSf = Shapefile.getShapefile(groupBy);

				QueryResults groupedQr = qr.aggregate(aggregateToSf, shpNorm, weightByAttribute);

				ArrayList<String> fields = new ArrayList<String>();

				fields.add("groupval");

				ArrayList<GisShapeFeature> gisFeatures = new ArrayList<GisShapeFeature>();


				for(ShapeFeature feature : aggregateToSf.getShapeFeatureStore().getAll()) {

					if(groupedQr.items.containsKey(feature.id)) {
						GisShapeFeature gf = new GisShapeFeature();
						gf.geom = feature.geom;
						gf.id = feature.id;

						gf.fields.add(groupedQr.items.get(feature.id).value);

						gisFeatures.add(gf);
					}
				}


				shapeName += "_" + shp.name.replaceAll("\\W+", "") + "_norm_" + shpNorm.name.replaceAll("\\W+", "") + "_group_" +
						aggregateToSf.name.replaceAll("\\W+", "").toLowerCase() + (query2 != null ? "_compare_" + query2.name : "");

				generateZippedShapefile(shapeName, fields, gisFeatures, false, res);
				return "";
			}
		}

		return null;
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

		generateZippedShapefile(shapeName, fields, gisFeatures, false, res);
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

		generateZippedShapefile(shapeName, fields, gisFeatures, true, res);
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
	
	static void generateZippedShapefile(String fileName, ArrayList<String> fieldNames, List<GisShapeFeature> features, boolean difference, Response res)
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
			featureDefinition = "the_geom:MultiPolygon:srid=4326,id:String,time:Integer";

		if (difference)
			featureDefinition += ",time2:Integer,difference:Integer";

		int fieldPosition = 0;
		for(String fieldName : fieldNames) {

			String shortFieldName = fieldName;

			if(fieldName.length() > 10)
				shortFieldName = fieldName.substring(0, 10);

			featureDefinition += "," + shortFieldName + ":";
			if(features.get(0).fields.get(fieldPosition) instanceof String)
				featureDefinition += "String";
			if(features.get(0).fields.get(fieldPosition) instanceof Number)
				featureDefinition += "Double";
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

			featureBuilder.add(feature.time);

			if (difference) {
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

		OutputStream os = res.raw().getOutputStream();
		DirectoryZip.zip(outputDirectory, os);
		os.close();
		outputDirectory.delete();
	}
	
	  
	
}
