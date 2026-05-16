package com.prodigalgal.xaigateway.admin.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class SensitiveJsonSanitizer {

    private final ObjectMapper objectMapper;

    SensitiveJsonSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (JacksonException exception) {
            return new LinkedHashMap<>();
        }
    }

    String sanitizeJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return writeJson(sanitizeValue("", readMap(json)));
    }

    String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(sanitizeValue("", value));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法序列化脱敏 JSON。", exception);
        }
    }

    @SuppressWarnings("unchecked")
    Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key)) {
            return "***";
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            mapValue.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null) {
                    String childKey = String.valueOf(nestedKey);
                    nested.put(childKey, sanitizeValue(childKey, nestedValue));
                }
            });
            return nested;
        }
        if (value instanceof List<?> listValue) {
            List<Object> sanitized = new ArrayList<>();
            for (Object child : listValue) {
                sanitized.add(child instanceof Map<?, ?> ? sanitizeValue(key, child) : sanitizeScalar(key, child));
            }
            return sanitized;
        }
        return sanitizeScalar(key, value);
    }

    private Object sanitizeScalar(String key, Object value) {
        if (value instanceof String text) {
            return redactSecretText(text);
        }
        return value;
    }

    private String redactSecretText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer ***")
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "sk-***")
                .replaceAll("AIza[A-Za-z0-9_-]{20,}", "AIza***");
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("password")
                || normalized.contains("session_key")
                || normalized.contains("sessionkey");
    }
}
