package com.prodigalgal.xaigateway.gateway.core.response;

import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.List;

public record GatewayResponse(
        String requestId,
        RouteSelectionResult routeSelection,
        String outputText,
        GatewayUsageView usage,
        List<GatewayToolCall> toolCalls,
        String reasoning,
        GatewayFinishReason finishReason,
        GatewayErrorView error
) {
}
