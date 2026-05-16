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
        String sessionAffinityKey,
        List<String> governanceNotes,
        String governanceReservationKey,
        RouteSelectionSource selectionSource,
        RouteCandidateView selectedCandidate,
        List<RouteCandidateView> candidates,
        List<RouteCandidateEvaluation> candidateEvaluations,
        List<RouteExecutionAttempt> attempts
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
            GatewayClientFamily clientFamily,
            List<String> governanceNotes,
            String governanceReservationKey,
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
                clientFamily,
                null,
                governanceNotes,
                governanceReservationKey,
                selectionSource,
                selectedCandidate,
                candidates,
                List.of(),
                List.of()
        );
    }

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
                null,
                List.of(),
                null,
                selectionSource,
                selectedCandidate,
                candidates,
                List.of(),
                List.of()
        );
    }

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
            GatewayClientFamily clientFamily,
            List<String> governanceNotes,
            String governanceReservationKey,
            RouteSelectionSource selectionSource,
            RouteCandidateView selectedCandidate,
            List<RouteCandidateView> candidates,
            List<RouteCandidateEvaluation> candidateEvaluations,
            List<RouteExecutionAttempt> attempts
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
                clientFamily,
                null,
                governanceNotes,
                governanceReservationKey,
                selectionSource,
                selectedCandidate,
                candidates,
                candidateEvaluations,
                attempts
        );
    }

    public RouteSelectionResult withSelectedCandidate(RouteCandidateView candidate, RouteSelectionSource source) {
        return new RouteSelectionResult(
                distributedKeyId,
                distributedKeyPrefix,
                requestedModel,
                publicModel,
                resolvedModelKey,
                protocol,
                prefixHash,
                fingerprint,
                modelGroup,
                clientFamily,
                sessionAffinityKey,
                governanceNotes,
                governanceReservationKey,
                source,
                candidate,
                candidates,
                candidateEvaluations,
                attempts
        );
    }

    public RouteSelectionResult withAttempts(List<RouteExecutionAttempt> updatedAttempts) {
        return new RouteSelectionResult(
                distributedKeyId,
                distributedKeyPrefix,
                requestedModel,
                publicModel,
                resolvedModelKey,
                protocol,
                prefixHash,
                fingerprint,
                modelGroup,
                clientFamily,
                sessionAffinityKey,
                governanceNotes,
                governanceReservationKey,
                selectionSource,
                selectedCandidate,
                candidates,
                candidateEvaluations,
                updatedAttempts
        );
    }
}
