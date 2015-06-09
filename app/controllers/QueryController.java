package controllers;

import models.Query;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

import java.io.IOException;

/**
 * Multipoint query controller
 */
@Security.Authenticated(Secured.class)
public class QueryController extends Controller {

    // **** query controllers ****

    public static Result getQueryById(String id) {
        return getQuery(id, null, null);
    }

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

    public static Result createQuery() throws IOException {

        Query  q = Query.create();

        q = Api.mapper.readValue(request().body().asJson().traverse(), Query.class);

        q.save();

        q.run();

        return ok(Api.toJson(q, false));

    }

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

    public static Result deleteQuery(String id) throws IOException {
        if(id == null)
            return badRequest();

        Query q = Query.getQuery(id);

        if(q == null)
            return badRequest();

        q.delete();

        return ok();
    }
}
