package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import java.time.Instant;
import java.util.List;

public record OfficialAccountQuotaResponse(
        Long accountId,
        Long groupId,
        String accountName,
        OfficialAccountType accountType,
        UpstreamAccountProviderType providerType,
        List<String> supportedModels,
        String externalAccountId,
        String planTier,
        String subscriptionTier,
        String quotaStatus,
        Instant quotaWindowStartedAt,
        Integer quotaWindowSeconds,
        Instant quotaResetAt,
        Long quotaRemainingTokens,
        Long quotaRemainingRequests,
        Instant lastRefreshAt,
        Instant nextRefreshAfter,
        String refreshStatus,
        int refreshFailureCount,
        boolean active,
        boolean frozen,
        boolean healthy,
        boolean routeEligible,
        String routeBlockReason,
        String quotaError,
        String lastRefreshResultJson
) {
}
