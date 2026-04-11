package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;

public record AdminChatExecuteResponse(
        String requestId,
        RouteSelectionResult routeSelection,
        String text,
        GatewayUsage usage,
        List<GatewayToolCall> toolCalls
) {
}
