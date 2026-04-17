package org.owl.flux.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class JsonMetadata {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private JsonMetadata() {
    }

    public static String write(Map<String, String> metadata) {
        Map<String, String> value = metadata == null ? Collections.emptyMap() : metadata;
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to encode punishment metadata.", exception);
        }
    }

    public static Map<String, String> read(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return new HashMap<>(OBJECT_MAPPER.readValue(metadataJson, MAP_TYPE));
        } catch (JsonProcessingException exception) {
            return Collections.emptyMap();
        }
    }
}
