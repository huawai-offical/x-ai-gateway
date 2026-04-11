package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;

public record ChatExecutionStreamChunk(
        String textDelta,
        String finishReason,
        GatewayUsage usage,
        boolean terminal,
        List<GatewayToolCall> toolCalls
) {

    public ChatExecutionStreamChunk(String textDelta, String finishReason, GatewayUsage usage, boolean terminal) {
        this(textDelta, finishReason, usage, terminal, List.of());
    }
}
