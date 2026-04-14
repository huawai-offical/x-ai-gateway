package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.JsonNode;
import java.util.List;

public record CanonicalRequest(
        String distributedKeyPrefix,
        CanonicalIngressProtocol ingressProtocol,
        String requestPath,
        String requestedModel,
        List<CanonicalMessage> messages,
        List<CanonicalToolDefinition> tools,
        JsonNode toolChoice,
        Double temperature,
        Integer maxTokens,
        CanonicalReasoningConfig reasoning,
        JsonNode providerExtensions
) {
}
