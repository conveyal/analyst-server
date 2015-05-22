package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.QueueManager;
import utils.ResultEnvelope;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Receives messages from the cluster workers and handles them
 */
public class QueueReceiver extends Controller {
    private static ObjectMapper objectMapper = new ObjectMapper();

    // 10 MB ought to be enough for anybody
    @BodyParser.Of(value = BodyParser.Raw.class, maxLength = 1024 * 1024 * 10)
    public static Result receive (String key) throws IOException {
        // the key provides a rudimentary level of security, and also prevents responses from coming in that aren't intended
        // for this server.

        QueueManager qm = QueueManager.getManager();

        if (!qm.key.equals(key))
            return unauthorized();

        Http.RawBuffer rb = request().body().asRaw();

        // if it's over 100k it's cached on disk
        // TODO increase threshold
        InputStream is;
        if (rb.asFile() != null)
            is = new FileInputStream(rb.asFile());
        else
            is = new ByteArrayInputStream(rb.asBytes());

        GZIPInputStream gis = new GZIPInputStream(is);

        ResultEnvelope env = objectMapper.readValue(gis, ResultEnvelope.class);

        gis.close();

        qm.handle(env);

        return ok();
    }
}
