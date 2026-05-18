package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record CanonicalResponse(
        String requestId,
        String publicModel,
        String outputText,
        String reasoning,
        List<CanonicalToolCall> toolCalls,
        CanonicalUsage usage,
        GatewayFinishReason finishReason,
        JsonNode rawResponse
) {
    public CanonicalResponse(
            String requestId,
            String publicModel,
            String outputText,
            String reasoning,
            List<CanonicalToolCall> toolCalls,
            CanonicalUsage usage,
            GatewayFinishReason finishReason) {
        this(requestId, publicModel, outputText, reasoning, toolCalls, usage, finishReason, null);
    }
}
