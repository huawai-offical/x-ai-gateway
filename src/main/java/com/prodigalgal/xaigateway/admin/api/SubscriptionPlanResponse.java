package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record SubscriptionPlanResponse(
        Long id,
        String planName,
        String description,
        boolean active,
        int defaultDurationDays,
        int maxActiveKeys,
        int rpmLimit,
        int tpmLimit,
        int concurrencyLimit,
        long dailyTokenLimit,
        long activeSubscriptionCount,
        Instant createdAt,
        Instant updatedAt
) {
}
