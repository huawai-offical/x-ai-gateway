package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record BackupJobResponse(
        Long id,
        String status,
        boolean dryRun,
        String snapshotPath,
        String summaryJson,
        Instant createdAt,
        Instant updatedAt
) {
}
