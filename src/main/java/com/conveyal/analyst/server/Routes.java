package com.conveyal.analyst.server;

import com.conveyal.analyst.server.controllers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import static spark.Spark.*;

/**
 * Routes for Analyst Server.
 */
public class Routes {
    private static final Logger LOG = LoggerFactory.getLogger(Routes.class);

    private static final JsonTransformer json = new JsonTransformer();

    public static void routes () {
        // serve assets
        staticFileLocation("/public");

        // send people to the login page if necessary
        before("/", Authentication::uiAuthenticated);

        // messages are dynamically generated
        get("/messages", MessagesController::messages);

        // set the language for message strings
        post("/setLang", MessagesController::setLang);

        // login/logout
        // if Stormpath ID site is enabled, redirect to it. Otherwise render login chrome.
        get("/login", Authentication::redirectToLoginSite);
        // handle login through ID site
        get("/handleLogin", Authentication::handleLogin);
        // do login through chrome
        post("/doLogin", Authentication::doLogin);
        get("/logout", Authentication::logout);
        get("/api/user/self", Authentication::getCurrentUser, json);
        post("/oauth/token", Authentication::getBearerToken);

        // authentication for API excludes two resources
        before("/api/*", (req, res) -> {
            // single and query allow CORS access and auth is handled in controllers
            String[] whitelist = new String[] {
                    "/api/single",
                    "/api/query"
            };

            for (String root : whitelist) {
                if (req.pathInfo().startsWith(root))
                    return;
            }

            Authentication.authenticated(req, res);
        });

        // project routes
        // return value is plain text
        get("/api/project/:id/exemplarDay", ProjectController::getExemplarDay);
        get("/api/project", ProjectController::getProject, json);
        get("/api/project/:id", ProjectController::getProject, json);
        post("/api/project", ProjectController::createProject, json);
        put("/api/project/:id", ProjectController::updateProject, json);
        delete("/api/project/:id", ProjectController::deleteProject, json);
        after("/api/project*", json::type);

        // shapefile routes
        get("/api/shapefile", ShapefileController::getShapefile, json);
        get("/api/shapefile/:id", ShapefileController::getShapefile, json);
        post("/api/shapefile", ShapefileController::createShapefile, json);
        put("/api/shapefile/:id", ShapefileController::updateShapefile, json);
        delete("/api/shapefile/:id", ShapefileController::deleteShapefile, json);
        after("/api/shapefile*", json::type);

        // bundle routes
        get("/api/bundle", BundleController::getBundle, json);
        get("/api/bundle/:id", BundleController::getBundle, json);
        post("/api/bundle", BundleController::createBundle, json);
        put("/api/bundle/:id", BundleController::updateBundle, json);
        delete("/api/bundle/:id", BundleController::deleteBundle, json);
        after("/api/bundle*", json::type);

        // scenario routes
        get("/api/scenario", ScenarioController::get, json);
        get("/api/scenario/:id", ScenarioController::getById, json);
        post("/api/scenario", ScenarioController::create, json);
        put("/api/scenario/:id", ScenarioController::update, json);
        delete("/api/scenario/:id", ScenarioController::delete, json);
        after("/api/scenario*", json::type);

        // query routes
        // note: auth is handled by each individual controller as some allow unauthenticated access
        get("/api/query/:id", QueryController::getQuery, json);
        get("/api/query", QueryController::getQuery, json);
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
        after("/api/single*", json::type);
        get("/csv/single", SinglePoint::csv);

        // GIS routes
        before("/gis/*", Authentication::authenticated);
        get("/gis/query", Gis::query);
        get("/gis/single", Gis::single);
        get("/gis/singleComparison", Gis::singleComparison);

        before("/tile/spatial", Authentication::authenticated);
        get("/tile/spatial", Tiles::spatial);

        before("/tile/shapefile", Authentication::authenticated);
        get("/tile/shapefile", Tiles::shape);

        before("/tile/transit", Authentication::authenticated);
        get("/tile/transit", Tiles::transit);

        before("/tile/transitComparison", Authentication::authenticated);
        get("/tile/transitComparison", Tiles::transitComparison);

        // single point and query tiles don't require authentication, so that they can be used
        // with analyst.js apps. Of course they do nothing if you don't have the query IDs/keys,
        // which are pretty secret
        get("/tile/query/:queryId/:compareTo/:z/:x/:yformat", Tiles::query);
        get("/tile/query/:queryId/:z/:x/:yformat", Tiles::query);
        get("/tile/single/:key/:z/:x/:yformat", SinglePointTiles::single);
        get("/tile/single/:key1/:key2/:z/:x/:yformat", SinglePointTiles::compare);

        // handle exceptions by reporting the proper HTTP status code, printing a stack trace to the logs
        // and sending it to the browser in dev mode.
        exception(Exception.class, (e, req, res) -> {
            LOG.info("unhandled exception, caught by server thread", e);
            if (Boolean.TRUE.equals(
                    Boolean.parseBoolean(AnalystMain.config.getProperty("application.prod")))) {
                res.status(Controller.INTERNAL_SERVER_ERROR);
                res.type("text/plain");
                res.body("Internal Server Error");
            }
            else {
                res.status(Controller.INTERNAL_SERVER_ERROR);
                res.type("text/plain");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintWriter pw = new PrintWriter(baos);
                pw.write("500 Internal Server Error\n\n");

                e.printStackTrace(pw);
                pw.close();
                res.body(baos.toString());
            }
        });
    }
}
