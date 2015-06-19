package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.AnalystMain;
import models.User;
import spark.Request;
import spark.Response;

import static spark.Spark.*;

/** handle authentication */
public class Authentication extends Controller {
    public static void authenticated (Request request, Response response) {
        String username = (String) request.session().attribute("username");
        if (username == null || User.getUserByUsername(username) == null) {
            halt(UNAUTHORIZED, "you must log in to access this page");
        }
    }

    /** same as above but generates a redirect instead of a 401 Unauthorized */
    public static void uiAuthenticated (Request req, Response res) {
        String username = (String) req.session().attribute("username");
        if (username == null || User.getUserByUsername(username) == null) {
            res.redirect("/login.html", MOVED_TEMPORARILY);
        }
    }

    public static void authenticatedOrCors (Request req, Response res) {
        if (!Boolean.parseBoolean(AnalystMain.config.getProperty("api.allow-unauthenticated-access", "false")))
            halt(UNAUTHORIZED, "you must log in to access this page");
        else
            res.header("Access-Control-Allow-Origin", "*");
    }
}