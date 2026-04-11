package com.prodigalgal.xaigateway.gateway.core.routing;

import java.util.List;

public record RouteSelectionResult(
        Long distributedKeyId,
        String distributedKeyPrefix,
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        String protocol,
        String prefixHash,
        String fingerprint,
        String modelGroup,
        RouteSelectionSource selectionSource,
        RouteCandidateView selectedCandidate,
        List<RouteCandidateView> candidates
) {
}
