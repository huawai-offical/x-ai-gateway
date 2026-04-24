package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record GatewayUserResponse(
        Long id,
        String email,
        String displayName,
        boolean active,
        long subscriptionCount,
        Instant lastLoginAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
