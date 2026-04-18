package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record MaintenanceWindowResponse(
        Long id,
        String windowName,
        String scopeType,
        String scopeRef,
        Instant startsAt,
        Instant endsAt,
        boolean enabled,
        String description,
        boolean activeNow,
        Instant createdAt,
        Instant updatedAt
) {
}
