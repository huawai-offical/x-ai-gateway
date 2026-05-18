package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.response.GatewayFinishReason;
import java.util.List;

public record CanonicalStreamEvent(
        CanonicalStreamEventType type,
        String textDelta,
        String reasoningDelta,
        List<CanonicalToolCall> toolCalls,
        CanonicalUsage usage,
        boolean terminal,
        GatewayFinishReason finishReason,
        String outputText,
        String reasoning,
        String rawSsePayload
) {
    public CanonicalStreamEvent(
            CanonicalStreamEventType type,
            String textDelta,
            String reasoningDelta,
            List<CanonicalToolCall> toolCalls,
            CanonicalUsage usage,
            boolean terminal,
            GatewayFinishReason finishReason,
            String outputText,
            String reasoning) {
        this(type, textDelta, reasoningDelta, toolCalls, usage, terminal, finishReason, outputText, reasoning, null);
    }

    public static CanonicalStreamEvent rawSse(String payload) {
        return new CanonicalStreamEvent(
                CanonicalStreamEventType.RAW_SSE,
                null,
                null,
                List.of(),
                CanonicalUsage.empty(),
                false,
                null,
                null,
                null,
                payload
        );
    }
}
