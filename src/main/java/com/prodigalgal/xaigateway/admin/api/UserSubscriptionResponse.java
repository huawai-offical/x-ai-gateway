package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record UserSubscriptionResponse(
        Long id,
        Long userId,
        String userEmail,
        Long planId,
        String planName,
        String status,
        Instant startsAt,
        Instant expiresAt,
        boolean autoRenew,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
