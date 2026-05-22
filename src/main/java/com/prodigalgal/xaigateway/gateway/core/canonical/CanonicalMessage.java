package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.JsonNode;
import java.util.List;

public record CanonicalMessage(
        CanonicalMessageRole role,
        List<CanonicalContentPart> parts,
        String reasoningContent,
        List<CanonicalToolCall> toolCalls,
        JsonNode providerExtensions
) {
    public CanonicalMessage(CanonicalMessageRole role, List<CanonicalContentPart> parts) {
        this(role, parts, null, List.of(), null);
    }

    public CanonicalMessage {
        parts = parts == null ? List.of() : List.copyOf(parts);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
