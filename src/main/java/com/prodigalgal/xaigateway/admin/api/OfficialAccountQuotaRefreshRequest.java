package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OfficialAccountQuotaRefreshRequest(
        String planTier,
        String subscriptionTier,
        Integer quotaWindowSeconds,
        Long quotaRemainingTokens,
        Long quotaRemainingRequests,
        Instant quotaResetAt,
        String quotaError,
        Boolean forceFailure
) {
}
