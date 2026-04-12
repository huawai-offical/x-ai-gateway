package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RestoreJobResponse(
        Long id,
        Long backupJobId,
        String status,
        boolean dryRun,
        String summaryJson,
        Instant createdAt,
        Instant updatedAt
) {
}
