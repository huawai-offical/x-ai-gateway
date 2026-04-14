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
        String reasoning
) {
}
