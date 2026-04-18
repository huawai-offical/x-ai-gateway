package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;

public record CredentialHealthScoreResponse(
        Long credentialId,
        String credentialName,
        ProviderType providerType,
        Long siteProfileId,
        Long proxyId,
        boolean active,
        int score,
        String healthState,
        String reason,
        Instant effectiveUntil,
        Instant lastUsedAt,
        List<Long> matchedPolicyIds,
        List<Long> matchedQuarantineIds
) {
}
