package com.conveyal.analyst.server.controllers;

import models.User;
import spark.Request;
import spark.Response;

import static spark.Spark.halt;

/**
 * User controllers.
 */
public class UserController extends Controller {
    public static Object getUser (Request request, Response response) {

        String id = request.params("id");

        if(id != null) {

            User u = null;

            if(id.toLowerCase().equals("self")) {
                u = currentUser(request);
            }
            else {
                u = User.getUser(id);
            }

            if(u != null)
                return u;
            else
                halt(NOT_FOUND);
        }
        else {
            return User.getUsers();
        }

        return null;
    }

    public static User deleteUser(Request req, Response res)  {
        String id = req.params("id");

        if(id == null)
            halt(NOT_FOUND);

        User u = User.getUser(id);

        if(u == null)
            halt(UNAUTHORIZED);

        if (!u.username.equals(req.session().attribute("username")))
            halt(UNAUTHORIZED);

        u.delete();

        return u;
    }
}
