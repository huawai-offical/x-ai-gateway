package com.prodigalgal.xaigateway.gateway.core.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ChatExecutionRequest(
        String distributedKeyPrefix,
        String protocol,
        String requestPath,
        String requestedModel,
        List<MessageInput> messages,
        List<GatewayToolDefinition> tools,
        JsonNode toolChoice,
        Double temperature,
        Integer maxTokens,
        JsonNode executionMetadata
) {

    public ChatExecutionRequest(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            List<MessageInput> messages,
            List<GatewayToolDefinition> tools,
            JsonNode toolChoice,
            Double temperature,
            Integer maxTokens
    ) {
        this(
                distributedKeyPrefix,
                protocol,
                requestPath,
                requestedModel,
                messages,
                tools,
                toolChoice,
                temperature,
                maxTokens,
                null
        );
    }

    public record MessageInput(
            String role,
            String content,
            String toolCallId,
            String toolName,
            List<MediaInput> media
    ) {
    }

    public record MediaInput(
            String kind,
            String mimeType,
            String url,
            String name
    ) {
    }
}
