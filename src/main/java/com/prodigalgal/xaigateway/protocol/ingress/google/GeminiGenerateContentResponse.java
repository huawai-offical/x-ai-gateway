package com.prodigalgal.xaigateway.protocol.ingress.google;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResponse;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalToolCall;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalUsage;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    public static GeminiGenerateContentResponse fromCanonical(CanonicalResponse response) {
        return new GeminiGenerateContentResponse(
                List.of(new Candidate(
                        new Content(toPartsCanonical(response.outputText(), response.toolCalls()), "model"),
                        "STOP"
                )),
                toUsageMetadata(response.usage())
        );
    }

    private static List<Part> toPartsCanonical(String text, List<CanonicalToolCall> toolCalls) {
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

    private static UsageMetadata toUsageMetadata(CanonicalUsage usage) {
        if (usage == null || !usage.present()) {
            return new UsageMetadata(0, 0, 0, 0, 0);
        }
        return new UsageMetadata(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.cacheHitTokens(),
                usage.reasoningTokens()
        );
    }
}
