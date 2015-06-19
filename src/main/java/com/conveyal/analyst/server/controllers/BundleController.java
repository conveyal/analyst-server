package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import models.Bundle;
import models.Project;
import models.User;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import spark.Request;
import spark.Response;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static spark.Spark.halt;

/**
 * Bundle create/delete logic.
 */
public class BundleController extends Controller {
    public static Object getBundle(Request req, Response res) {
        if (req.params().containsKey("id")) {
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

    public static Bundle createBundle(Request req, Response res) throws FileUploadException, IOException {
        // see https://github.com/perwendel/spark/issues/26
        // TODO should we be initializing this every time?
        DiskFileItemFactory fileItemFactory = new DiskFileItemFactory();
        File repo = Files.createTempDir();
        fileItemFactory.setRepository(repo);

        ServletFileUpload sfu = new ServletFileUpload(fileItemFactory);
        List<FileItem> files = sfu.parseRequest(req.raw());

        FileItem file = files.stream().filter(f -> "file".equals(f.getFieldName())).findFirst().get();

        if (file != null) {

            String projectId = req.queryParams("projectId");

            // make sure the project exists
            if (Project.getProject(projectId) == null || !currentUser(req).hasWritePermission(projectId))
                halt(NOT_FOUND, "Project not found or you do not have access to it");

            // copy it to a temporary file
            // this seems silly because it's probably on the file system somewhere
            File tempFile = File.createTempFile("shape", ".zip");
            InputStream is = file.getInputStream();
            OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile));
            ByteStreams.copy(is, os);
            is.close();
            os.close();

            String bundleType = req.queryParams("bundleType");
            String augmentBundleId = req.queryParams("augmentBundleId");

            Bundle s = Bundle.create(tempFile, bundleType, augmentBundleId);

            s.name = req.queryParams("name");
            s.description = req.queryParams("description");
            s.projectId = projectId;

            s.save();
            repo.delete();

            return s;
        }
        else {
            repo.delete();
            halt(BAD_REQUEST, "Please upload a file");
        }

        return null;
    }

    public static Bundle updateBundle (Request req, Response res) {

        Bundle s;

        try {

            s = JsonUtil.getObjectMapper().readValue(req.body(), Bundle.class);

            if (s.id == null)
                halt(BAD_REQUEST, "Please specify an ID");

            Bundle ex = Bundle.getBundle(s.id);
            User u = currentUser(req);

            if (ex == null || !u.hasWritePermission(ex.projectId) || !u.hasWritePermission(s.projectId))
                halt(NOT_FOUND, "not found");

            s.save();

            return s;
        } catch (Exception e) {
            e.printStackTrace();
            halt(BAD_REQUEST, e.getMessage());
        }

        return null;
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
