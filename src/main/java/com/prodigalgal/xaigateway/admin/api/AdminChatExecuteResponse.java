package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolCall;
import com.prodigalgal.xaigateway.gateway.core.shared.ExecutionBackend;
import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;

public record AdminChatExecuteResponse(
        String requestId,
        RouteSelectionResult routeSelection,
        CanonicalExecutionPlan plan,
        ExecutionBackend executionBackend,
        String text,
        GatewayUsage usage,
        List<GatewayToolCall> toolCalls
) {

    public AdminChatExecuteResponse(
            String requestId,
            RouteSelectionResult routeSelection,
            ExecutionBackend executionBackend,
            String text,
            GatewayUsage usage,
            List<GatewayToolCall> toolCalls) {
        this(requestId, routeSelection, null, executionBackend, text, usage, toolCalls);
    }
}
