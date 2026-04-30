package com.prodigalgal.xaigateway.admin.application;

import java.time.Instant;

public record OAuthSessionRefreshOutcome(
        Long accountId,
        String providerType,
        String status,
        String reason,
        Instant refreshedAt,
        Instant nextRefreshAfter,
        Instant cooldownUntil
) {
}
