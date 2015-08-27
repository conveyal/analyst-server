package com.conveyal.analyst.server.utils;

import com.conveyal.geojson.GeoJsonModule;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleKeyDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.api.model.JodaLocalDateSerializer;
import org.opentripplanner.api.parameter.QualifiedModeSet;
import org.opentripplanner.routing.core.TraverseModeSet;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Maintains a singleton ObjectMapper, with appropriate serialization and deserialization config.
 */
public class JsonUtil {
    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new CustomSerializerModule());
        objectMapper.registerModule(JodaLocalDateSerializer.makeModule());
        objectMapper.registerModule(new GeoJsonModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /** Serialize a traverse mode set as a string. No need for a deserializer as TraverseModeSet has a single-arg string constructor */
    public static class TraverseModeSetSerializer extends JsonSerializer<TraverseModeSet> {
        @Override
        public void serialize(TraverseModeSet traverseModeSet, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
            jsonGenerator.writeString(traverseModeSet.getAsStr());
        }
    }

    /** Serialize a traverse mode set as a string. No need for a deserializer as TraverseModeSet has a single-arg string constructor */
    public static class QualifiedModeSetSerializer extends JsonSerializer<QualifiedModeSet> {
        @Override
        public void serialize(QualifiedModeSet qualifiedModeSet, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
            jsonGenerator.writeString(qualifiedModeSet.toString());
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

    /** Serializer for java.time.LocalDate */
    public static class JavaLocalDateIsoSerializer extends JsonSerializer<LocalDate> {
        @Override public void serialize(LocalDate localDate, JsonGenerator jsonGenerator,
                SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
            jsonGenerator.writeString(localDate.format(DateTimeFormatter.ISO_DATE));
        }
    }

    /** Deserializer for java.time.LocalDate */
    public static class JavaLocalDateIsoDeserializer extends JsonDeserializer<LocalDate> {

        @Override public LocalDate deserialize(JsonParser jsonParser,
                DeserializationContext deserializationContext)
                throws IOException, JsonProcessingException {
            return LocalDate.from(DateTimeFormatter.ISO_DATE.parse(jsonParser.getValueAsString()));
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
            s.addSerializer(QualifiedModeSet.class, new QualifiedModeSetSerializer());
            s.addSerializer(LocalDate.class, new JavaLocalDateIsoSerializer());
            ctx.addSerializers(s);

            SimpleDeserializers d = new SimpleDeserializers();
            d.addDeserializer(LocalDate.class, new JavaLocalDateIsoDeserializer());
            ctx.addDeserializers(d);
        }
    }
}
