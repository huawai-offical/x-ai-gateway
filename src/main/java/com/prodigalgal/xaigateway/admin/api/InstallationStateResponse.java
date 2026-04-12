package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record InstallationStateResponse(
        Long id,
        String status,
        Long activeReleaseArtifactId,
        boolean bootstrapCompleted,
        Instant lastHealthCheckAt,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt
) {
}
