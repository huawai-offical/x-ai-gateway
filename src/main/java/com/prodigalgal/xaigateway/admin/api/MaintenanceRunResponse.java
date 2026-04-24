package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record MaintenanceRunResponse(
        Long id,
        String runType,
        String status,
        boolean dryRun,
        boolean confirmRequired,
        boolean confirmed,
        String artifactPath,
        String artifactChecksum,
        String actor,
        String sourceRef,
        long durationMs,
        String detailJson,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
