package com.prodigalgal.xaigateway.gateway.core.routing;

import java.util.List;

public record RoutePlanSnapshot(
        String publicModel,
        String resolvedModelKey,
        String modelGroup,
        List<RouteCandidateEvaluation> candidateEvaluations
) {
}
