package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record MaintenanceWindowRequest(
        String windowName,
        String scopeType,
        String scopeRef,
        Instant startsAt,
        Instant endsAt,
        Boolean enabled,
        String description
) {
}
