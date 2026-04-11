package com.prodigalgal.xaigateway.protocol.ingress.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;

public record GeminiGenerateContentResponse(
        List<Candidate> candidates,
        @JsonProperty("usageMetadata")
        UsageMetadata usageMetadata
) {

    public record Candidate(
            Content content,
            @JsonProperty("finishReason")
            String finishReason
    ) {
    }

    public record Content(
            List<Part> parts,
            String role
    ) {
    }

    public record Part(
            String text,
            @JsonProperty("functionCall")
            FunctionCall functionCall
    ) {
    }

    public record FunctionCall(
            String name,
            JsonNode args
    ) {
    }

    public record UsageMetadata(
            @JsonProperty("promptTokenCount")
            int promptTokenCount,
            @JsonProperty("candidatesTokenCount")
            int candidatesTokenCount,
            @JsonProperty("totalTokenCount")
            int totalTokenCount,
            @JsonProperty("cachedContentTokenCount")
            int cachedContentTokenCount,
            @JsonProperty("thoughtsTokenCount")
            int thoughtsTokenCount
    ) {
    }

    public static GeminiGenerateContentResponse from(
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls) {
        return new GeminiGenerateContentResponse(
                List.of(new Candidate(
                        new Content(toParts(text, toolCalls), "model"),
                        "STOP"
                )),
                new UsageMetadata(
                        usage.promptTokens(),
                        usage.completionTokens(),
                        usage.totalTokens(),
                        usage.cacheHitTokens(),
                        usage.reasoningTokens()
                )
        );
    }

    private static List<Part> toParts(String text, List<GatewayToolCall> toolCalls) {
        if (toolCalls != null && !toolCalls.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();
            return toolCalls.stream()
                    .map(toolCall -> new Part(
                            null,
                            new FunctionCall(
                                    toolCall.name(),
                                    parseArguments(mapper, toolCall.arguments())
                            )
                    ))
                    .toList();
        }

        return List.of(new Part(text, null));
    }

    private static JsonNode parseArguments(ObjectMapper mapper, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return mapper.createObjectNode();
        }

        try {
            return mapper.readTree(arguments);
        } catch (Exception ignored) {
            return mapper.createObjectNode().put("raw", arguments);
        }
    }
}
