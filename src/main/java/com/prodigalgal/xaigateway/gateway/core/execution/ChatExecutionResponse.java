package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;

public record ChatExecutionResponse(
        String requestId,
        RouteSelectionResult routeSelection,
        String text,
        GatewayUsage usage,
        List<GatewayToolCall> toolCalls
) {
}
