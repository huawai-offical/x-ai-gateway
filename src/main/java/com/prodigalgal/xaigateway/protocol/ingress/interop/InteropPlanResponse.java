package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.Map;

public record InteropPlanResponse(
        CanonicalExecutionPlan plan,
        RouteSelectionResult selection,
        Map<String, Object> summary,
        Map<String, Object> debug
) {
    public static InteropPlanResponse from(
            CanonicalExecutionPlan plan,
            RouteSelectionResult selectionResult,
            Map<String, Object> summary,
            Map<String, Object> debug
    ) {
        return new InteropPlanResponse(
                plan,
                selectionResult,
                summary == null ? Map.of() : Map.copyOf(summary),
                debug == null ? Map.of() : Map.copyOf(debug)
        );
    }
}
