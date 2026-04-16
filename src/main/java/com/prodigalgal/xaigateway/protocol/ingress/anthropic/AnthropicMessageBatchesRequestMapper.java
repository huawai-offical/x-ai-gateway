package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AnthropicMessageBatchesRequestMapper {

    private final ObjectMapper objectMapper;

    public AnthropicMessageBatchesRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode toCreatePayload(JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new IllegalArgumentException("Anthropic message batches 请求体必须是 JSON object。");
        }
        return ((ObjectNode) requestBody).deepCopy();
    }

    public String extractModel(JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            return null;
        }
        JsonNode requests = requestBody.path("requests");
        if (!requests.isArray() || requests.isEmpty()) {
            return null;
        }
        String model = requests.get(0).path("params").path("model").asText(null);
        return model == null || model.isBlank() ? null : model;
    }

    public ObjectNode emptyPayload() {
        return objectMapper.createObjectNode();
    }
}
