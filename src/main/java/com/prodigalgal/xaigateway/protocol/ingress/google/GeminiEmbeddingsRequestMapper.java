package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class GeminiEmbeddingsRequestMapper {

    private final ObjectMapper objectMapper;

    public GeminiEmbeddingsRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode toEmbedRequest(String model, JsonNode requestBody) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("input", extractContentText(requestBody == null ? null : requestBody.path("content")));
        return payload;
    }

    public ObjectNode toBatchEmbedRequest(String model, JsonNode requestBody) {
        ArrayNode requests = requestBody != null && requestBody.path("requests").isArray()
                ? (ArrayNode) requestBody.path("requests")
                : null;
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("batchEmbedContents 至少需要一条 requests。");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        ArrayNode input = payload.putArray("input");
        for (JsonNode item : requests) {
            input.add(extractContentText(item.path("content")));
        }
        return payload;
    }

    private String extractContentText(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            throw new IllegalArgumentException("embeddings 请求缺少 content。");
        }
        JsonNode parts = content.path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalArgumentException("embeddings content 至少需要一条 text part。");
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText(null);
            if (text == null || text.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(text);
        }
        if (builder.isEmpty()) {
            throw new IllegalArgumentException("embeddings content 至少需要一条 text part。");
        }
        return builder.toString();
    }
}
