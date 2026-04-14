package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalExecutionPlan;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestSemantics;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionResult;
import java.util.List;

public record RouteSelectionPreviewResponse(
        RouteSelectionResult selection,
        GatewayRequestSemantics requestedSemantics,
        CanonicalRequest canonicalRequest,
        CanonicalExecutionPlan plan,
        List<RouteCandidateEvaluation> candidateEvaluations
) {
}
