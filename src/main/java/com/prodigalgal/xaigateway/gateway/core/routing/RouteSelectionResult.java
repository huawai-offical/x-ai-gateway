package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.auth.GatewayClientFamily;
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
        GatewayClientFamily clientFamily,
        List<String> governanceNotes,
        String governanceReservationKey,
        RouteSelectionSource selectionSource,
        RouteCandidateView selectedCandidate,
        List<RouteCandidateView> candidates
) {
    public RouteSelectionResult(
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
        this(
                distributedKeyId,
                distributedKeyPrefix,
                requestedModel,
                publicModel,
                resolvedModelKey,
                protocol,
                prefixHash,
                fingerprint,
                modelGroup,
                GatewayClientFamily.GENERIC_OPENAI,
                List.of(),
                null,
                selectionSource,
                selectedCandidate,
                candidates
        );
    }
}
