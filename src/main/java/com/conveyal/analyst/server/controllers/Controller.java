package com.conveyal.analyst.server.controllers;

import models.User;
import spark.Request;

/**
 * Convenience methods for controllers.
 */
public class Controller {
    // HTTP status codes
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
        return User.getUserByUsername((String) request.attribute("username"));
    }
}
