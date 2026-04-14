package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;

public record CanonicalExecutionResult(
        String requestId,
        RouteSelectionResult routeSelection,
        CanonicalExecutionPlan plan,
        CanonicalResponse response
) {
}
