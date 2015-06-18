package com.conveyal.analyst.server.controllers;

/**
 * Hold web server status codes.
 */
public class Status {
    /** 400 Bad Request */
    public static final int BAD_REQUEST = 400;

    /** 401 Unauthorized */
    public static final int UNAUTHORIZED = 401;

    /** 404 Not Found */
    public static final int NOT_FOUND = 404;

    /** 418 I am a teapot (https://tools.ietf.org/html/rfc2324) */
    public static final int I_AM_A_TEAPOT = 418;
}
