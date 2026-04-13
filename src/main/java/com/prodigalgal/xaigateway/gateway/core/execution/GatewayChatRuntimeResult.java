package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;

public record GatewayChatRuntimeResult(
        String text,
        GatewayUsage usage,
        List<GatewayToolCall> toolCalls,
        String finishReason,
        String reasoning
) {
    public GatewayChatRuntimeResult(
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls
    ) {
        this(text, usage, toolCalls, null, null);
    }

    public GatewayChatRuntimeResult(
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls,
            String finishReason
    ) {
        this(text, usage, toolCalls, finishReason, null);
    }
}
