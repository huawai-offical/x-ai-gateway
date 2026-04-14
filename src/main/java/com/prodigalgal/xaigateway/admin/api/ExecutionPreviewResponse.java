package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.Map;

public record ExecutionPreviewResponse(
        RouteSelectionResult selection,
        CanonicalRequest canonicalRequest,
        CanonicalExecutionPlan plan,
        RouteCandidateView providerBinding,
        Map<String, Object> providerOptions
) {
}
