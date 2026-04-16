package com.prodigalgal.xaigateway.protocol.ingress.google;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiBatchesRequestMapper {

    private final ObjectMapper objectMapper;

    public GeminiBatchesRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extractInputFileName(JsonNode requestBody) {
        String fileName = requestBody == null ? null : requestBody.path("src").path("fileName").asText(null);
        if (fileName == null || fileName.isBlank()) {
            fileName = requestBody == null ? null : requestBody.path("fileName").asText(null);
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Google batch create 需要 src.fileName。");
        }
        return fileName;
    }

    public ObjectNode toBatchCreatePayload(String model, JsonNode requestBody, String gatewayFileKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("input_file_id", gatewayFileKey);
        payload.put("endpoint", "/v1beta/models/" + model + ":generateContent");
        String completionWindow = firstText(requestBody, "completionWindow");
        if (completionWindow != null) {
            payload.put("completion_window", completionWindow);
        }
        String displayName = nestedText(requestBody, "config", "displayName");
        if (displayName != null) {
            payload.putObject("metadata").put("display_name", displayName);
        }
        return payload;
    }

    private String firstText(JsonNode node, String fieldName) {
        if (node == null || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String nestedText(JsonNode node, String objectField, String fieldName) {
        if (node == null || node.path(objectField).isMissingNode() || node.path(objectField).isNull()) {
            return null;
        }
        String value = node.path(objectField).path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
