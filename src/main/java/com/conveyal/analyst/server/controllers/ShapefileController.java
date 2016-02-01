package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import models.Project;
import models.Shapefile;
import models.User;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static spark.Spark.halt;

/**
 * Shapefile controllers.
 */
public class ShapefileController extends Controller {
    private static final Logger LOG = LoggerFactory.getLogger(ShapefileController.class);

    public static Object getShapefile(Request req, Response res) {
        if (req.params("id") != null) {
            Shapefile s = Shapefile.getShapefile(req.params().get("id"));
            if(s != null || !currentUser(req).hasReadPermission(Project.getProject(s.projectId)))
                return s;
            else
                halt(NOT_FOUND);
        }
        else if (req.queryParams("projectId") != null) {
            Project p = Project.getProject(req.queryParams("projectId"));
            if (p != null && currentUser(req).hasReadPermission(p))
                return Shapefile.getShapefilesByProject(p.id);
            else
                halt(NOT_FOUND);
        }
        else {
            // get all the shapefile for this users' projects
            Set<String> userProjects = currentUser(req).projectPermissions.stream()
                    .filter(pp -> pp.read)
                    .map(pp -> pp.projectId)
                    .collect(Collectors.toSet());

            return Shapefile.getShapefiles().stream()
                    .filter(s -> userProjects.contains(s.projectId))
                    .collect(Collectors.toList());
        }

        return null;
    }


    public static Shapefile createShapefile(Request req, Response res) throws Exception {
        // see https://github.com/perwendel/spark/issues/26
        // TODO should we be initializing this every time?

        ServletFileUpload sfu = new ServletFileUpload(fileItemFactory);
        Map<String, List<FileItem>> files = sfu.parseParameterMap(req.raw());

        FileItem file = files.get("file").get(0);

        if (file != null) {

            String projectId = files.get("projectId").get(0).getString();

            // make sure the project exists
            if (Project.getProject(projectId) == null || !currentUser(req).hasWritePermission(projectId))
                halt(NOT_FOUND, "Project not found or you do not have access to it");

            // grab as utf8, http://stackoverflow.com/questions/546365
            String name = files.get("name").get(0).getString("UTF-8");

            // copy it to a temporary file
            File tempFile = File.createTempFile("shape", ".zip");
            file.write(tempFile);

            Shapefile s;
            if (file.getName().endsWith(".pbf"))
                s = Shapefile.createFromGeobuf(tempFile, projectId, name);
            else
                s = Shapefile.create(tempFile, projectId, name);

            tempFile.delete();

            s.description = files.get("description").get(0).getString("UTF-8");

            s.save();

            s.writeToClusterCache();

            // redirect back from whence we came.
            // NB this is not so CRUD, but we need to send users back to the app.
            // We pass the location hash in so we can redirect to the right part of the app.
            res.redirect(String.format("/%s", files.get("location").get(0).getString()));
        }
        else {
            halt(BAD_REQUEST, "Please upload a file");
        }

        return null;
    }

    public static Shapefile updateShapefile (Request req, Response res) {

        Shapefile s;

        try {
            s = JsonUtil.getObjectMapper().readValue(req.body(), Shapefile.class);

            if(s.id == null)
                halt(BAD_REQUEST, "Specify a shapefile ID in the body");

            Shapefile ex = Shapefile.getShapefile(s.id);

            User u = currentUser(req);

            if (ex == null || !u.hasWritePermission(ex.projectId) || !u.hasWritePermission(s.projectId))
                halt(NOT_FOUND, "not found");

            s.save();

            return s;
        } catch (Exception e) {
            LOG.error("Error updating shapefile", e);
            halt(BAD_REQUEST, e.getMessage());
        }

        return null;
    }

    public static Shapefile deleteShapefile(Request req, Response res) {
        String id = req.params("id");
        if(id == null)
            halt(BAD_REQUEST, "specify an ID");

        Shapefile s = Shapefile.getShapefile(id);

        if (s == null || !currentUser(req).hasWritePermission(s.projectId))
            halt(NOT_FOUND, "not found");

        s.delete();

        return s;
    }
}
