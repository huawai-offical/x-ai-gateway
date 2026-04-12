package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record UpgradeJobResponse(
        Long id,
        Long targetReleaseArtifactId,
        Long preBackupJobId,
        String status,
        String message,
        boolean autoRollbackTriggered,
        Instant createdAt,
        Instant updatedAt
) {
}
