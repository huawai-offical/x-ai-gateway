package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AlertSilenceRequest(
        String silenceName,
        String eventType,
        String severity,
        String entityType,
        String entityRef,
        Instant startsAt,
        Instant endsAt,
        Boolean enabled,
        String reason
) {
}
