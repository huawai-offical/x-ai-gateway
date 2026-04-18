package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AlertSilenceResponse(
        Long id,
        String silenceName,
        String eventType,
        String severity,
        String entityType,
        String entityRef,
        Instant startsAt,
        Instant endsAt,
        boolean enabled,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
}
