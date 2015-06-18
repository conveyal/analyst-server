package com.conveyal.analyst.server;

import com.conveyal.analyst.server.controllers.*;

import static spark.Spark.*;

/**
 * Routes for Analyst Server.
 */
public class Routes {
    private static final JsonTransformer jsonTransformer = new JsonTransformer();

    public static void routes () {
        // login/logout/admin
        post("/doLogin", Application::doLogin);
        post("/createUser", Application::createUser);

        before("/setPassword", Authentication::authenticated);
        post("/setPassword", Application::setPassword);

        // TODO this shouldn't be a GET as it's not idempotent
        before("/logout", Authentication::authenticated);
        get("/logout", Application::logout);

        before("/createDemoProject", Authentication::authenticated);
        get("/createDemoProject", Application::createDemoProject);

        // user controllers
        // TODO ensure / matches /api/user
        before("/api/user*", Authentication::authenticated);
        get("/api/user", UserController::getUser, jsonTransformer);
        get("/api/user/:id", UserController::getUser, jsonTransformer);
        delete("/api/user", UserController::deleteUser, jsonTransformer);

        // TODO edit user: slightly tricky due to the hashed password

        // project routes
        before("/api/project*", Authentication::authenticated);
        get("/api/project/:projectId/exemplarDay", Api::getExemplarDay);

        // finally, serve assets from the public folder at /
        staticFileLocation("/public");
    }
}
