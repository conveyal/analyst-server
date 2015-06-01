package utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.opentripplanner.routing.core.TraverseModeSet;

import java.io.IOException;

/**
 * Maintains a singleton ObjectMapper, with appropriate serialization and deserialization config.
 */
public class JsonUtil {
    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new TraverseModeSetModule());
        objectMapper.registerModule(new JodaModule());
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /** Serialize a traverse mode set as a string. No need for a deserializer as TraverseModeSet has a single-arg string constructor */
    public static class TraverseModeSetSerializer extends JsonSerializer<TraverseModeSet> {
        @Override
        public void serialize(TraverseModeSet traverseModeSet, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
            jsonGenerator.writeString(traverseModeSet.toString());
        }
    }

    public static class TraverseModeSetModule extends SimpleModule {
        public TraverseModeSetModule () {
            super("TraverseModeSetModule", new Version(0, 0, 1, null, null, null));
        }

        @Override
        public void setupModule(SetupContext ctx) {
            SimpleSerializers s = new SimpleSerializers();
            s.addSerializer(TraverseModeSet.class, new TraverseModeSetSerializer());
            ctx.addSerializers(s);
        }
    }
}
