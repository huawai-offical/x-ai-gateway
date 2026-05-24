package com.prodigalgal.xaigateway.protocol.ingress.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OpenAiResponsesLocalLifecycleService {

    private final ObjectMapper objectMapper;

    public OpenAiResponsesLocalLifecycleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode inputTokens(JsonNode requestBody) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("object", "response.input_tokens");
        response.put("input_tokens", estimateInputTokens(requestBody));
        return response;
    }

    private int estimateInputTokens(JsonNode requestBody) {
        if (requestBody == null || requestBody.isMissingNode() || requestBody.isNull()) {
            return 0;
        }
        List<String> segments = new ArrayList<>();
        collectText(requestBody.path("instructions"), segments);
        collectText(requestBody.path("input"), segments);
        int codePoints = segments.stream().mapToInt(value -> value.codePointCount(0, value.length())).sum();
        if (codePoints == 0) {
            return 0;
        }
        int structuralTokens = Math.max(segments.size() * 2, countInputItems(requestBody.path("input")) * 4);
        return Math.max(1, (int) Math.ceil(codePoints / 4.0) + structuralTokens);
    }

    private void collectText(JsonNode node, List<String> segments) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            if (!node.asText().isBlank()) {
                segments.add(node.asText());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectText(item, segments);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.properties().forEach(entry -> {
            if (shouldCountField(entry.getKey())) {
                collectText(entry.getValue(), segments);
            }
        });
    }

    private boolean shouldCountField(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return !List.of(
                "id",
                "type",
                "status",
                "role",
                "model",
                "name",
                "call_id",
                "file_id",
                "image_url",
                "file_url",
                "filename",
                "detail",
                "metadata",
                "stream",
                "stream_options",
                "store",
                "background"
        ).contains(normalized);
    }

    private int countInputItems(JsonNode input) {
        if (input == null || input.isMissingNode() || input.isNull()) {
            return 0;
        }
        if (input.isArray()) {
            return input.size();
        }
        return 1;
    }

}
