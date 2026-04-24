package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OpsSystemEventResponse(
        Long id,
        String eventType,
        String severity,
        String source,
        String entityType,
        String entityRef,
        String title,
        String detailJson,
        Instant occurredAt,
        Instant createdAt
) {
}
