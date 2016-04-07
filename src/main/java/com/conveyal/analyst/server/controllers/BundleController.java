package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import models.Bundle;
import models.Project;
import models.TransportScenario;
import models.User;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static spark.Spark.halt;

/**
 * Bundle create/delete logic.
 */
public class BundleController extends Controller {
    private static Logger LOG = LoggerFactory.getLogger(BundleController.class);

    public static Object getBundle(Request req, Response res) {
        if (req.params("id") != null) {
            Bundle s = Bundle.getBundle(req.params("id"));
            if(s != null && currentUser(req).hasReadPermission(s.projectId))
                return s;
            else
                halt(NOT_FOUND, "Bundle not found");
        }
        else if (req.queryParams().contains("projectId")) {
            String projectId = req.queryParams("projectId");
            if (currentUser(req).hasReadPermission(projectId))
                return Bundle.getBundlesByProject(projectId);
        }
        else {
            Set<String> userProjects = currentUser(req).projectPermissions.stream()
                    .filter(pp -> pp.read)
                    .map(pp -> pp.projectId)
                    .collect(Collectors.toSet());

            return Bundle.getBundles().stream()
                    .filter(b -> userProjects.contains(b.projectId))
                    .collect(Collectors.toList());
        }

        return null;
    }

    public static Bundle createBundle(Request req, Response res) throws Exception {
        // see https://github.com/perwendel/spark/issues/26
        ServletFileUpload sfu = new ServletFileUpload(fileItemFactory);
        Map<String, List<FileItem>> files = sfu.parseParameterMap(req.raw());

        FileItem file = files.get("file").get(0);

        if (file != null) {

            // http://stackoverflow.com/questions/546365
            String projectId = files.get("projectId").get(0).getString("UTF-8");
            String bundleType = files.get("bundleType").get(0).getString("UTF-8");
            String augmentBundleId = files.containsKey("augmentBundleId") ? files.get("augmentBundleId").get(0).getString("UTF-8") : null;

            // make sure the project exists
            if (Project.getProject(projectId) == null || !currentUser(req).hasWritePermission(projectId))
                halt(NOT_FOUND, "Project not found or you do not have access to it");

            // copy it to a temporary file
            // this seems silly because it's probably on the file system somewhere
            File tempFile = File.createTempFile("shape", ".zip");
            file.write(tempFile);

            // true indicates to delete file when upload succeeds
            Bundle b = Bundle.create(tempFile, bundleType, augmentBundleId, true);

            b.name = files.get("name").get(0).getString("UTF-8");
            b.description = files.get("description").get(0).getString("UTF-8");
            b.projectId = projectId;

            b.save();

            // Create a baseline transport scenario
            TransportScenario s = new TransportScenario();
            s.bundleId = b.id;
            s.projectId = b.projectId;
            s.name = b.name;
            s.generateId();

            s.save();

            // redirect back from whence we came.
            // NB this is not so CRUD, but we need to send users back to the app.
            // We pass the location hash in so we can redirect to the right part of the app.
            res.redirect(String.format("/%s", files.get("location").get(0).getString()));
            return null;
        }
        else {
            halt(BAD_REQUEST, "Please upload a file");
        }

        return null;
    }

    public static Bundle updateBundle (Request req, Response res) {

        Bundle s;

        try {

            s = JsonUtil.getObjectMapper().readValue(req.body(), Bundle.class);

        } catch (Exception e) {
            LOG.warn("error updating bundle", e);
            halt(BAD_REQUEST, e.getMessage());
            return null;
        }

        if (s.id == null)
            halt(BAD_REQUEST, "Please specify an ID");

        Bundle ex = Bundle.getBundle(s.id);
        User u = currentUser(req);

        if (ex == null || !u.hasWritePermission(ex.projectId) || !u.hasWritePermission(s.projectId))
            halt(NOT_FOUND, "not found");

        s.save();

        return s;
    }

    public static Bundle deleteBundle (Request req, Response res) throws IOException {
        String id = req.params("id");

        if (id == null)
            halt(BAD_REQUEST, "Specify an ID to delete!");

        Bundle s = Bundle.getBundle(id);

        if (s == null || !currentUser(req).hasWritePermission(s.projectId))
            halt(NOT_FOUND, "Cannot delete project that does not exist.");

        s.delete();

        return s;
    }
}
