package com.conveyal.analyst.server;

import com.conveyal.analyst.server.controllers.*;

import static spark.Spark.*;

/**
 * Routes for Analyst Server.
 */
public class Routes {
    private static final JsonTransformer json = new JsonTransformer();

    public static void routes () {
        // serve assets
        staticFileLocation("/public");

        // messages are dynamically generated
        get("/messages", MessagesController::messages);

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
        get("/api/user", UserController::getUser, json);
        get("/api/user/:id", UserController::getUser, json);
        delete("/api/user", UserController::deleteUser, json);
        after("/api/user", json::type);

        // TODO edit user: slightly tricky due to the hashed password

        // project routes
        before("/api/project*", Authentication::authenticated);
        // return value is plain text
        get("/api/project/:id/exemplarDay", ProjectController::getExemplarDay);
        get("/api/project", ProjectController::getProject, json);
        get("/api/project/:id", ProjectController::getProject, json);
        post("/api/project", ProjectController::createProject, json);
        put("/api/project/:id", ProjectController::updateProject, json);
        delete("/api/project/:id", ProjectController::deleteProject, json);
        after("/api/project*", json::type);

        // shapefile routes
        before("/api/shapefile*", Authentication::authenticated);
        get("/api/shapefile", ShapefileController::getShapefile, json);
        get("/api/shapefile/:id", ShapefileController::getShapefile, json);
        post("/api/shapefile", ShapefileController::createShapefile, json);
        put("/api/shapefile/:id", ShapefileController::updateShapefile, json);
        delete("/api/shapefile/:id", ShapefileController::deleteShapefile, json);
        after("/api/shapefile*", json::type);

        // bundle routes
        before("/api/bundle*", Authentication::authenticated);
        get("/api/bundle", BundleController::getBundle, json);
        get("/api/bundle/:id", BundleController::getBundle, json);
        post("/api/bundle", BundleController::createBundle, json);
        put("/api/bundle/:id", BundleController::updateBundle, json);
        delete("/api/bundle/:id", BundleController::deleteBundle, json);
        after("/api/bundle*", json::type);

        // scenario routes
        before("/api/scenario*", Authentication::authenticated);
        get("/api/scenario", ScenarioController::get, json);
        get("/api/scenario/:id", ScenarioController::getById, json);
        post("/api/scenario", ScenarioController::create, json);
        put("/api/scenario/:id", ScenarioController::update, json);
        delete("/api/scenario/:id", ScenarioController::delete, json);
        after("/api/scenario*", json::type);

        // query routes
        // note: auth is handled by each individual controller as some allow unauthenticated access
        get("/api/query", QueryController::getQuery, json);
        get("/api/query/:id", QueryController::getQuery, json);
        post("/api/query", QueryController::createQuery, json);
        put("/api/query/:id", QueryController::updateQuery, json);
        delete("/api/query/:id", QueryController::deleteQuery, json);
        get("/api/query/:id/bins", QueryController::queryBins, json);
        get("/api/query/:id/:compareTo/bins", QueryController::queryBins, json);
        after("/api/query*", json::type);

        // single point analysis results
        // note: auth is handled in the controller
        // JSON rendered by controller, no need for a result filter
        post("/api/single", SinglePoint::result);
        options("/api/single", SinglePoint::options);
        after("/api/single", json::type);

        get("/csv/single", SinglePoint::csv);

        // GIS routes
        before("/gis*", Authentication::authenticated);
        get("/gis/query", Gis::query);

        // all tiles are accessible by the world if unauthenticated API access is enabled.
        before("/tile*", Authentication::authenticatedOrCors);
        get("/tile/spatial", Tiles::spatial);
        get("/tile/shapefile", Tiles::shape);
        get("/tile/query/:queryId/:compareTo/:z/:x/:y.png", Tiles::query);
        get("/tile/query/:queryId/:z/:x/:y.png", Tiles::query);
        get("/tile/transit", Tiles::transit);
        get("/tile/transitComparison", Tiles::transitComparison);
        get("/tile/single/:key/:z/:x/:y.png", SinglePointTiles::single);
        get("/tile/single/:key1/:key2/:z/:x/:y.:format", SinglePointTiles::compare);
    }
}
