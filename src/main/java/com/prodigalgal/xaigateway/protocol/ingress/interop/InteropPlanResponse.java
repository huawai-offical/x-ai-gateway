package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.List;
import java.util.Map;

public record InteropPlanResponse(
        boolean executable,
        String protocol,
        String requestPath,
        String requestedModel,
        String degradationPolicy,
        List<String> requiredFeatures,
        List<String> blockers,
        List<String> degradations,
        RouteSelectionResult selectionResult,
        Map<String, Object> summary,
        Map<String, Object> debug
) {
}
