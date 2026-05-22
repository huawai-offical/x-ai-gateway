package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;

public record CredentialHealthScoreResponse(
        String sourceType,
        Long sourceId,
        Long credentialId,
        Long accountId,
        String credentialName,
        String displayName,
        ProviderType providerType,
        Long siteProfileId,
        Long proxyId,
        boolean active,
        Boolean frozen,
        int score,
        String healthState,
        String reason,
        Instant effectiveUntil,
        Instant lastUsedAt,
        List<Long> matchedPolicyIds,
        List<Long> matchedQuarantineIds
) {
}
