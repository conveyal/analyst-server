package com.conveyal.analyst.server.controllers;

import models.User;
import spark.Request;
import spark.Response;

import static spark.Spark.*;
import static com.conveyal.analyst.server.controllers.Status.*;

/** handle authentication */
public class Authentication {
    public static void authenticated (Request request, Response response) {
        String username = (String) request.session().attribute("username");
        if (username == null || User.getUserByUsername(username) == null) {
            halt(UNAUTHORIZED, "you must log in to access this page");
        }
    }
}