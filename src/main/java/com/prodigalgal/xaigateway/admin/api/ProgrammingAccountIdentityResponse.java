package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import java.time.Instant;

public record ProgrammingAccountIdentityResponse(
        Long accountId,
        UpstreamAccountProviderType providerType,
        String accountName,
        String externalAccountId,
        String identitySubject,
        String identityEmail,
        String clientFamily,
        String adoptionDecision,
        String authorizationStatus,
        Long quotaRemainingTokens,
        Long quotaRemainingRequests,
        Integer quotaWindowSeconds,
        Instant quotaWindowStartedAt,
        boolean routeEligible,
        String routeBlockReason,
        String lastRefreshResultJson
) {
}
