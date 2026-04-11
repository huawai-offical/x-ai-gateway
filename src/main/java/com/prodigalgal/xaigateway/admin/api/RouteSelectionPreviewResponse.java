package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import com.prodigalgal.xaigateway.gateway.core.routing.RouteSelectionSource;
import java.util.List;

public record RouteSelectionPreviewResponse(
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
        int candidateCount,
        List<RouteCandidateView> candidates
) {
}
