package com.conveyal.analyst.server.controllers;

import com.google.common.io.Files;
import models.User;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import spark.Request;

import java.io.File;

/**
 * Convenience methods for controllers.
 */
public class Controller {
    protected static DiskFileItemFactory fileItemFactory = new DiskFileItemFactory();
    static {
        File repo = Files.createTempDir();
        fileItemFactory.setRepository(repo);
        repo.deleteOnExit();
    }

    // HTTP status codes

    /** 200 OK */
    public static final int OK = 200;

    /** 301 Moved Permanently */
    public static final int MOVED_PERMANENTLY = 301;

    /** 302 Moved Temporarily */
    public static final int MOVED_TEMPORARILY = 302;

    /** 400 Bad Request */
    public static final int BAD_REQUEST = 400;

    /** 401 Unauthorized */
    public static final int UNAUTHORIZED = 401;

    /** 404 Not Found */
    public static final int NOT_FOUND = 404;

    /** 418 I am a teapot (https://tools.ietf.org/html/rfc2324) */
    public static final int I_AM_A_TEAPOT = 418;

    /** 500 Internal server error */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /** Get the current user */
    protected static User currentUser(Request request) {
        return User.getUserByUsername(request.session().attribute("username"));
    }
}
