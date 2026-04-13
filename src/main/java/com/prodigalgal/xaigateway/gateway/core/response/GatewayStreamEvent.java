package com.prodigalgal.xaigateway.gateway.core.response;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import java.util.List;

public record GatewayStreamEvent(
        GatewayStreamEventType type,
        String textDelta,
        String reasoningDelta,
        List<GatewayToolCall> toolCalls,
        GatewayUsageView usage,
        boolean terminal,
        GatewayFinishReason finishReason,
        String outputText,
        String reasoning,
        GatewayErrorView error
) {
}
