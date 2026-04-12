package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RollbackJobResponse(
        Long id,
        Long upgradeJobId,
        Long releaseArtifactId,
        Long backupJobId,
        String status,
        String message,
        Instant createdAt,
        Instant updatedAt
) {
}
