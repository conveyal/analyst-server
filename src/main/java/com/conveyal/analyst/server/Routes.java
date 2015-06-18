package com.conveyal.analyst.server;

import com.conveyal.analyst.server.controllers.Application;

import static spark.Spark.*;

/**
 * Routes for Analyst Server.
 */
public class Routes {
    public static void routes () {
        get("/", Application::index);
    }
}
