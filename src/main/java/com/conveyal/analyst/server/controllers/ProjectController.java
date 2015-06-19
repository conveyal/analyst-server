package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import models.Bundle;
import models.Project;
import models.User;
import spark.Request;
import spark.Response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.Collection;

import static spark.Spark.halt;

/**
 * Project controllers.
 */
public class ProjectController extends Controller {
    /**
     * Get a day that has ostensibly normal service, one would guess. Uses the next Tuesday that
     * is covered by all transit feeds in the project.
     */
    public static String getExemplarDay(Request req, Response res) throws Exception {
        Collection<Bundle> bundles = Bundle.getBundles(req.params("id"));
        if (bundles == null)
            halt(NOT_FOUND);

        LocalDate defaultDate = LocalDate.now().with(ChronoField.DAY_OF_WEEK, DayOfWeek.TUESDAY.getValue());

        if (defaultDate.isAfter(LocalDate.now()))
            defaultDate = defaultDate.minusDays(7);

        LocalDate startDate = null, endDate = null;

        for (Bundle b : bundles) {
            if (!b.failed && b.startDate != null && b.endDate != null && !b.startDate.isAfter(b.endDate)) {
                if (startDate == null || startDate.isBefore(b.startDate))
                    startDate = b.startDate;

                if (endDate == null || endDate.isAfter(b.endDate))
                    endDate = b.endDate;
            }
        }

        if (startDate == null || endDate == null || startDate.isAfter(endDate))
            return defaultDate.toString();

        // find a tuesday between the start and end date
        LocalDate ret = endDate;

        while (!ret.isBefore(startDate)) {
            if (ret.getDayOfWeek().equals(DayOfWeek.TUESDAY))
                return ret.toString();
            ret = ret.minusDays(1);
        }

        return defaultDate.toString();
    }

    public static Object getProject(Request req, Response res) {
        User u = currentUser(req);
        if (req.params().containsKey("id")) {
            Project p = Project.getProject(req.params("id"));
            if (p != null && u.hasReadPermission(p))
                return p;
            else
                // return not found even if it was found but they're unauthorized, to avoid
                // giving the impression that it exists.
                halt(NOT_FOUND);
        }
        else {
            return Project.getProjectsByUser(u);
        }

        return null;
    }

    public static Project createProject(Request req, Response res) {
        Project p;

        try {

            p = JsonUtil.getObjectMapper().readValue(req.body(), Project.class);
            p.save();

            // add newly created project to user permission
            User u = currentUser(req);
            u.addProjectPermission(p.id);
            u.save();

            return p;
        } catch (Exception e) {
            e.printStackTrace();
            halt(BAD_REQUEST, e.getMessage());
        }

        return null;
    }

    public static Object updateProject(Request req, Response res) {

        Project p;
        User u = currentUser(req);

        try {

            p = JsonUtil.getObjectMapper().readValue(req.body(), Project.class);

            if (p.id == null)
                halt(BAD_REQUEST, "Must specify a project ID");

            Project ex = Project.getProject(p.id);

            if (ex == null || !u.hasWritePermission(p))
                halt(NOT_FOUND, "Cannot update project that does not exist");

            p.save();

            return p;
        } catch (Exception e) {
            e.printStackTrace();
            halt(BAD_REQUEST, e.getMessage());
        }

        return null;
    }


    public static Object deleteProject(Request req, Response res) {
        String id = req.params("id");

        if(id == null)
            halt(BAD_REQUEST, "must specify an ID");

        Project p = Project.getProject(id);

        if (p == null || !currentUser(req).hasWritePermission(p))
            halt(NOT_FOUND);

        p.delete();

        return p;
    }
}
