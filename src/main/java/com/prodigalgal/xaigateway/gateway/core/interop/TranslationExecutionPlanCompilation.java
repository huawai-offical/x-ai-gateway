package com.prodigalgal.xaigateway.gateway.core.interop;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;

public record TranslationExecutionPlanCompilation(
        TranslationExecutionPlan plan,
        RouteSelectionResult selectionResult,
        GatewayRequestSemantics semantics
) {
}
