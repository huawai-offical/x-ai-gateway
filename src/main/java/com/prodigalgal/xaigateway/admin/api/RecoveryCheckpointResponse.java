package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RecoveryCheckpointResponse(
        Long id,
        String checkpointName,
        Long changePlanId,
        String status,
        String metadataSnapshotPath,
        String runtimeSnapshotPath,
        String dataSnapshotPath,
        String manifestJson,
        String verificationStatus,
        String verificationMessage,
        Instant verifiedAt,
        String verifiedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
