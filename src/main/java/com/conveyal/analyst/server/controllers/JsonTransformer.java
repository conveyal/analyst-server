package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import spark.Request;
import spark.Response;
import spark.ResponseTransformer;

/**
 * Serve output as json.
 */
public class JsonTransformer implements ResponseTransformer {
    private static final ObjectMapper objectMapper = JsonUtil.getObjectMapper();

    @Override
    public String render(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    /** set the content type */
    public void type (Request request, Response response) {
        response.type("application/json");
    }
}
