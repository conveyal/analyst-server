package com.conveyal.analyst.server.controllers;

import models.Query;
import models.Shapefile;
import play.Play;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import utils.QueryResults;
import utils.ResultEnvelope;

import java.io.IOException;

/**
 * Multipoint query controller
 */
public class QueryController extends Controller {

    // **** query controllers ****

    @Security.Authenticated(Secured.class)
    public static Result getQueryById(String id) {
        return getQuery(id, null, null);
    }

    @Security.Authenticated(Secured.class)
    public static Result getQuery(String id, String projectId, String pointSetId) {

        try {

            if(id != null) {
                Query q = Query.getQuery(id);
                if(q != null)
                    return ok(Api.toJson(q, false));
                else
                    return notFound();
            }
            else if (projectId != null){
                return ok(Api.toJson(Query.getQueries(projectId), false));
            }
            else {
                return ok(Api.toJson(Query.getQueriesByPointSet(pointSetId), false));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }

    }

    @Security.Authenticated(Secured.class)
    public static Result createQuery() throws IOException {

        Query  q = Query.create();

        q = Api.mapper.readValue(request().body().asJson().traverse(), Query.class);

        q.save();

        q.run();

        return ok(Api.toJson(q, false));

    }

    @Security.Authenticated(Secured.class)
    public static Result updateQuery(String id) {

        Query q;

        try {

            q = Api.mapper.readValue(request().body().asJson().traverse(), Query.class);

            if(q.id == null || Query.getQuery(q.id) == null)
                return badRequest();

            q.save();

            return ok(Api.toJson(q, false));
        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }

    @Security.Authenticated(Secured.class)
    public static Result deleteQuery(String id) throws IOException {
        if(id == null)
            return badRequest();

        Query q = Query.getQuery(id);

        if(q == null)
            return badRequest();

        q.delete();

        return ok();
    }

    /** Get the classes/bins for a particular query */
    public static Result queryBins(String queryId, Integer timeLimit, String weightByShapefile, String weightByAttribute, String groupBy,
                                   String which, String attributeName, String compareTo) {

        if (session().get("username") == null &&
                Play.application().configuration().getBoolean("api.allow-unauthenticated-access") != true)
            return unauthorized();

        // allow cross-origin access if we don't need auth
        if (Play.application().configuration().getBoolean("api.allow-unauthenticated-access") == true)
            response().setHeader("Access-Control-Allow-Origin", "*");

        response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        response().setHeader(PRAGMA, "no-cache");
        response().setHeader(EXPIRES, "0");

        ResultEnvelope.Which whichEnum;
        try {
            whichEnum = ResultEnvelope.Which.valueOf(which);
        } catch (Exception e) {
            // no need to pollute the console with a stack trace
            return badRequest("Invalid value for which parameter");
        }

        Query query = Query.getQuery(queryId);

        if (query == null)
            return badRequest();
        Query otherQuery = null;

        if (compareTo != null) {
            otherQuery = Query.getQuery(compareTo);

            if (otherQuery == null) {
                return badRequest("Non-existent comparison query.");
            }
        }

        try {

            String queryKey = queryId + "_" + timeLimit + "_" + which + "_" + attributeName;

            QueryResults qr = null;

            synchronized (QueryResults.queryResultsCache) {
                if (!QueryResults.queryResultsCache.containsKey(queryKey)) {
                    qr = new QueryResults(query, timeLimit, whichEnum, attributeName);
                    QueryResults.queryResultsCache.put(queryKey, qr);
                } else
                    qr = QueryResults.queryResultsCache.get(queryKey);
            }

            if (otherQuery != null) {
                QueryResults otherQr = null;

                queryKey = compareTo + "_" + timeLimit + "_" + which + "_" + attributeName;
                if (!QueryResults.queryResultsCache.containsKey(queryKey)) {
                    otherQr = new QueryResults(otherQuery, timeLimit, whichEnum, attributeName);
                    QueryResults.queryResultsCache.put(queryKey, otherQr);
                } else {
                    otherQr = QueryResults.queryResultsCache.get(queryKey);
                }

                qr = qr.subtract(otherQr);
            }

            if (weightByShapefile == null) {
                return ok(Json.toJson(qr.classifier.getBins()));
            } else {
                Shapefile aggregateTo = Shapefile.getShapefile(groupBy);

                Shapefile weightBy = Shapefile.getShapefile(weightByShapefile);
                return ok(Json.toJson(qr.aggregate(aggregateTo, weightBy, weightByAttribute).classifier.getBins()));

            }


        } catch (Exception e) {
            e.printStackTrace();
            return badRequest();
        }
    }
}
