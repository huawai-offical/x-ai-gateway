package com.prodigalgal.xaigateway.gateway.core.routing;

import java.time.Instant;
import java.util.List;

public record RouteCandidateEvaluation(
        RouteCandidateView candidate,
        boolean eligible,
        String healthState,
        Instant cooldownUntil,
        boolean affinityMatched,
        RouteSelectionSource selectionSource,
        double totalScore,
        List<String> scoreBreakdown,
        List<String> exclusionReasons
) {
}
