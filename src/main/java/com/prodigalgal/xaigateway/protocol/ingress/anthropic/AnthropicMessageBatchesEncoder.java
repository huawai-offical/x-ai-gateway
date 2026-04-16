package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AnthropicMessageBatchesEncoder {

    private final ObjectMapper objectMapper;

    public AnthropicMessageBatchesEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode encode(JsonNode response) {
        return response == null ? objectMapper.createObjectNode() : response.deepCopy();
    }
}
