package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import models.Project;
import models.Shapefile;
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
import java.util.zip.ZipException;

import static spark.Spark.halt;

/**
 * Shapefile controllers.
 */
public class ShapefileController extends Controller {
    public static Object getShapefile(Request req, Response res) {
        if (req.params().containsKey("id")) {
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


    public static Shapefile createShapefile(Request req, Response res) throws ZipException,
            FileUploadException, IOException {
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

            String name = req.queryParams("name");

            // copy it to a temporary file
            // this seems silly because it's probably on the file system somewhere
            File tempFile = File.createTempFile("shape", ".zip");
            InputStream is = file.getInputStream();
            OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile));
            ByteStreams.copy(is, os);
            is.close();
            os.close();

            Shapefile s = Shapefile.create(tempFile, projectId, name);
            tempFile.delete();

            s.description = req.queryParams("description");

            s.save();

            s.writeToClusterCache();

            repo.delete();

            return s;
        }
        else {
            repo.delete();
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
            e.printStackTrace();
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
