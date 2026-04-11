package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record RouteDecisionLogResponse(
        Long id,
        String requestId,
        Long distributedKeyId,
        String distributedKeyPrefix,
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        String protocol,
        String modelGroup,
        String selectionSource,
        Long selectedCredentialId,
        ProviderType selectedProviderType,
        String selectedBaseUrl,
        String prefixHash,
        String fingerprint,
        int candidateCount,
        String candidateSummaryJson,
        Instant createdAt
) {
}
