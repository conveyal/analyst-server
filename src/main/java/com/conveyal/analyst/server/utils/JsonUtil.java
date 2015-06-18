package com.conveyal.analyst.server.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleKeyDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.routing.core.TraverseModeSet;

import java.io.IOException;

/**
 * Maintains a singleton ObjectMapper, with appropriate serialization and deserialization config.
 */
public class JsonUtil {
    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new CustomSerializerModule());
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

    /** Deserializer for AgencyAndId, for agencyid_id format in bannedTrips */
    public static class AgencyAndIdDeserializer extends KeyDeserializer {

        @Override
        public AgencyAndId deserializeKey(String arg0, DeserializationContext arg1)
                throws IOException, JsonProcessingException {
            String[] sp = arg0.split("_");
            return new AgencyAndId(sp[0], sp[1]);
        }
    }

    public static class CustomSerializerModule extends SimpleModule {
        public CustomSerializerModule () {
            super("CustomSerializerModule", new Version(0, 0, 1, null, null, null));
        }

        @Override
        public void setupModule(SetupContext ctx) {
            SimpleKeyDeserializers kd = new SimpleKeyDeserializers();
            kd.addDeserializer(AgencyAndId.class, new AgencyAndIdDeserializer());
            ctx.addKeyDeserializers(kd);

            SimpleSerializers s = new SimpleSerializers();
            s.addSerializer(TraverseModeSet.class, new TraverseModeSetSerializer());
            ctx.addSerializers(s);
        }
    }
}
