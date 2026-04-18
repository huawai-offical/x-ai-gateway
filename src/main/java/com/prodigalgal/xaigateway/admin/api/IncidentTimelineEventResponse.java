package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record IncidentTimelineEventResponse(
        String eventType,
        String title,
        String description,
        String severity,
        String entityType,
        String entityRef,
        String source,
        Instant occurredAt
) {
}
