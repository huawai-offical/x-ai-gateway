package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;

public record CanonicalExecutionPlanCompilation(
        CanonicalExecutionPlan canonicalPlan,
        RouteSelectionResult selectionResult,
        GatewayRequestSemantics semantics,
        CanonicalRequest canonicalRequest
) {
}
