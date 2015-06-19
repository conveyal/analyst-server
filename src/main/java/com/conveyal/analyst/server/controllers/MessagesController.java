package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.AnalystMain;
import com.conveyal.analyst.server.utils.JsonUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import spark.Request;
import spark.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static spark.Spark.halt;

/**
 * Controller to reformat messages as JSON objects and send them to the client.
 */
public class MessagesController extends Controller {
    // map from language to messages for language
    private static Map<String, Properties> langs = new HashMap<>();

    public static String messages (Request req, Response res) throws IOException {
        String lang = req.queryParams("lang");
        if (lang == null)
            lang = AnalystMain.config.getProperty("application.lang");

        lang = lang.toLowerCase();

        if (!lang.matches("[a-z\\-]+"))
            halt(BAD_REQUEST, "invalid language");

        if (!langs.containsKey(lang)) {
            synchronized (langs) {
                if (!langs.containsKey(lang)) {
                    Properties tr = new Properties();
                    InputStream is = ClassLoader.getSystemClassLoader()
                            .getResourceAsStream("messages/messages." + lang);
                    tr.load(is);
                    is.close();
                    langs.put(lang, tr);
                }
            }
        }

        Properties tr = langs.get(lang);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonFactory jf = new JsonFactory();
        JsonGenerator jgen = jf.createGenerator(baos);
        jgen.setCodec(JsonUtil.getObjectMapper());

        jgen.writeStartObject();
        for (String pname : tr.stringPropertyNames()) {
            jgen.writeStringField(pname, tr.getProperty(pname));
        }
        jgen.writeEndObject();
        jgen.flush();
        jgen.close();

        res.type("application/json");
        return baos.toString();
    }
}
