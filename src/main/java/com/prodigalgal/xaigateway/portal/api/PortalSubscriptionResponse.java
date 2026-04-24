package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalSubscriptionResponse(
        Long id,
        Long planId,
        String planName,
        String status,
        Instant startsAt,
        Instant expiresAt,
        boolean autoRenew,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Long dailyTokenLimit,
        Instant createdAt,
        Instant updatedAt
) {
}
